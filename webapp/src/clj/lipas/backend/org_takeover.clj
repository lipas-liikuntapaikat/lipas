(ns lipas.backend.org-takeover
  "Bulk ownership take-over: an org claims the sites matching its ownership rule
  (city-code ∈ rule.city-codes ∧ owner ∈ rule.owners). Modeled as a small
  requested -> approved/denied state machine (org admins request, LIPAS admins
  approve). Applying ownership is append-only site revisions setting
  :owner-org-id (and the locked :owner enum), authored by the approver and
  marked acting on behalf of the org.

  A request is a one-shot snapshot, not a standing rule: `create-request!`
  stores the lipas-ids being claimed (the requester's curated picker subset
  when given — validated ⊆ the rule's matches — otherwise everything matching
  at request time) and `approve!` applies ownership to EXACTLY that stored
  set. The rule is never re-run at approval, so what the approver saw is
  precisely what gets claimed (no drift window for sites that started
  matching after the request). Sites that got claimed or deleted between
  request and approval are skipped and reported, never re-revisioned."
  (:require [clojure.java.jdbc :as jdbc-old]
            [honey.sql :as hsql]
            ;; Registers :-> / :->> etc. as infix ops in HoneySQL's runtime
            ;; registry. Without this require the ownership-rule queries render
            ;; as `->>(document, 'x')` → PG "operator does not exist: ->> record"
            ;; whenever lipas.wfs.core (the only other requirer) isn't loaded.
            [honey.sql.pg-ops]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.org :as org]
            [lipas.backend.search :as search]
            [lipas.utils :as utils]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn- ownership-clauses
  "HoneySQL where-clauses for the ownership rule's up-to-four AND-combined axes
  (empty when the rule matches nothing). `city_code` is text so its codes compare
  as strings; `type_code` is an integer column; activities are jsonb keys under
  `document->'activities'`, matched via `jsonb_exists_any` (the function form of
  `?|`, avoiding the JDBC `?` placeholder clash)."
  [{:keys [city-codes owners type-codes activities]}]
  (cond-> []
    (seq city-codes) (conj [:in :city_code (mapv str city-codes)])
    (seq owners)     (conj [:in [:->> :document [:inline "owner"]] (vec owners)])
    (seq type-codes) (conj [:in :type_code (vec type-codes)])
    (seq activities) (conj [:jsonb_exists_any
                            [:-> :document [:inline "activities"]]
                            [:array (vec activities)]])))

(defn- not-owned-by-clause
  "Exclude sites this org already owns, so claiming is idempotent — a re-run only
  picks up matching sites the org doesn't yet own (newly added since last time),
  never re-revisioning the ones already claimed. (Sites owned by *another* org
  are still in scope — that's a cross-org take-over, not a no-op re-claim.)"
  [org-id]
  [:or
   [:is [:->> :document [:inline "owner-org-id"]] nil]
   [:<> [:->> :document [:inline "owner-org-id"]] (str org-id)]])

(defn matching-lipas-ids
  "lipas-ids of current sites matching the ownership rule; nil for an empty rule.
  With `exclude-org-id`, sites already owned by that org are left out."
  ([db rule] (matching-lipas-ids db rule nil))
  ([db rule exclude-org-id]
   (let [clauses (ownership-clauses rule)]
     (when (seq clauses)
       (->> (jdbc/execute! db
                           (hsql/format
                            {:select [:lipas_id]
                             :from   [:sports_site_current]
                             :where  (into [:and] (cond-> clauses
                                                    exclude-org-id (conj (not-owned-by-clause exclude-org-id))))})
                           {:builder-fn rs/as-unqualified-kebab-maps})
            (mapv :lipas-id))))))

(defn matching-sites
  "Lightweight rows for matching sites (lipas-id + name + current owner enum),
  name-sorted, in a single query — no full-document loads, so it scales to the
  thousands of sites a big municipality's rule can match. nil for an empty rule.
  With `exclude-org-id`, sites already owned by that org are left out."
  ([db rule] (matching-sites db rule nil))
  ([db rule exclude-org-id]
   (let [clauses (ownership-clauses rule)]
     (when (seq clauses)
       (jdbc/execute! db
                      (hsql/format
                       {:select   [:lipas_id
                                   [[:->> :document [:inline "name"]] :name]
                                   [[:->> :document [:inline "owner"]] :current-owner]]
                        :from     [:sports_site_current]
                        :where    (into [:and] (cond-> clauses
                                                 exclude-org-id (conj (not-owned-by-clause exclude-org-id))))
                        :order-by [[[:->> :document [:inline "name"]] :asc]]})
                      {:builder-fn rs/as-unqualified-kebab-maps})))))

(defn preview
  "Impact preview for a take-over claim: the org the sites will be assigned to,
  the `:owner` enum they'd be relabeled+locked to (from the org type), and the
  full lightweight list of matching sites (the FE paginates it). Drives the
  explicit confirmation the admin sees before claiming."
  [db org-id]
  (let [org        (org/get-org db org-id)
        rule       (:ownership org)
        ;; only sites that would actually be (re)claimed — excludes ones this org
        ;; already owns, so after a full claim the preview correctly shows 0
        sites      (vec (matching-sites db rule org-id))]
    {:count          (count sites)
     :owner-enum     (core/org-type->owner (:type org))
     :owner-org-id   (str org-id)
     :owner-org-name (:name org)
     :rule           rule
     :sites          sites}))

(defn create-request!
  "Create a pending take-over request snapshotting the org's ownership rule and
  the lipas-ids to claim — approval applies exactly this stored set.

  Optional `:lipas-ids` is the requester's curated subset (picker): every id
  must be among the rule's current matches (sites the org already owns are not
  matches, mirroring the preview) or the whole request is rejected with
  :invalid-selection. Without it the full set of currently-matching sites is
  snapshotted (full-rule claim)."
  [db org-id requested-by & {:keys [lipas-ids]}]
  (let [rule     (:ownership (org/get-org db org-id))
        matching (matching-lipas-ids db rule org-id)
        selected (when (seq lipas-ids)
                   (let [matching? (set matching)
                         invalid   (vec (remove matching? lipas-ids))]
                     (when (seq invalid)
                       (throw (ex-info "Selection contains sites that don't match the org's ownership rule"
                                       {:type :invalid-selection :invalid-lipas-ids invalid})))
                     (vec (distinct lipas-ids))))]
    (jdbc/execute-one! db
                       ["INSERT INTO org_takeover_request
                         (org_id, status, ownership_rule, lipas_ids, requested_by)
                         VALUES (?,?,?,?,?) RETURNING *"
                        (utils/->uuid-safe org-id) "requested" rule (or selected matching []) (utils/->uuid-safe requested-by)]
                       {:builder-fn rs/as-unqualified-kebab-maps})))

(defn list-requests
  ([db] (list-requests db nil))
  ([db status]
   (jdbc/execute! db
                  (hsql/format (cond-> {:select   [:*]
                                        :from     [:org_takeover_request]
                                        :order-by [[:requested_at :desc]]}
                                 status (assoc :where [:= :status status])))
                  {:builder-fn rs/as-unqualified-kebab-maps})))

(defn get-request [db request-id]
  (jdbc/execute-one! db ["SELECT * FROM org_takeover_request WHERE id = ?" (utils/->uuid-safe request-id)]
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn- sites-by-lipas-ids
  "Same lightweight (lipas-id + name + current owner) rows as `matching-sites`,
  but for an explicit id set — resolves a request's stored snapshot for the
  approver's view. Ids whose site no longer exists simply don't come back.
  With `exclude-org-id`, sites already owned by that org are left out."
  [db lipas-ids exclude-org-id]
  (when (seq lipas-ids)
    (jdbc/execute! db
                   (hsql/format
                    {:select   [:lipas_id
                                [[:->> :document [:inline "name"]] :name]
                                [[:->> :document [:inline "owner"]] :current-owner]]
                     :from     [:sports_site_current]
                     :where    (cond-> [:and [:in :lipas_id (vec lipas-ids)]]
                                 exclude-org-id (conj (not-owned-by-clause exclude-org-id)))
                     :order-by [[[:->> :document [:inline "name"]] :asc]]})
                   {:builder-fn rs/as-unqualified-kebab-maps})))

(defn request-preview
  "Approver's impact preview for a stored request: same shape as `preview`, but
  the sites are resolved from the request's stored lipas-ids snapshot (what the
  requester selected) instead of re-running the rule — so the approver decides
  on exactly the set that approval will apply. Sites claimed-by-this-org or
  deleted since the request are filtered out (approval will skip them), which
  is why `:count` can be < `:requested-count`."
  [db request-id]
  (let [{:keys [org-id lipas-ids status ownership-rule]} (get-request db request-id)
        org   (org/get-org db org-id)
        sites (vec (sites-by-lipas-ids db lipas-ids org-id))]
    {:count           (count sites)
     :requested-count (count lipas-ids)
     :owner-enum      (core/org-type->owner (:type org))
     :owner-org-id    (str org-id)
     :owner-org-name  (:name org)
     :rule            ownership-rule
     :status          status
     :sites           sites}))

(defn approve!
  "Apply ownership to EXACTLY the request's stored lipas-ids snapshot (what the
  requester selected / the rule matched at request time — never a rule re-run,
  so sites that started matching after the request are not silently claimed)
  and mark the request approved. Sites the org already owns by approval time
  are skipped (idempotency — no redundant revisions) and so are ids whose site
  no longer exists; both are reported via :sites-skipped. Each claimed site
  gets a new revision with :owner-org-id, the locked :owner enum, and
  acting_org_id = org. ES is updated in a SINGLE bulk request rather than one
  round-trip per site — the per-site index call was the dominant cost
  (~350ms/site). `actor` is the deciding user (becomes the site author)."
  [db search request-id actor]
  (let [req        (get-request db request-id)
        _          (when-not (= "requested" (:status req))
                     ;; only a pending request can be approved — never re-run a
                     ;; claim for an already-approved or denied request
                     (throw (ex-info "Takeover request is not awaiting a decision"
                                     {:type :invalid-takeover-state :status (:status req)})))
        org-id     (:org-id req)
        owner-enum (core/org-type->owner (:type (org/get-org db org-id)))
        ;; the request-time snapshot — :lipas-ids has been populated since the
        ;; table was born, so the rule fallback only guards hand-made rows
        lipas-ids  (or (:lipas-ids req)
                       (matching-lipas-ids db (:ownership-rule req) org-id))]
    ;; DB revisions + ES bulk index + request status in one transaction (mirrors
    ;; bulk-ops mass-update): either every selected site is claimed and the
    ;; request marked approved, or the whole thing rolls back.
    ;; clojure.java.jdbc tx (re-entrant: the nested with-db-transaction inside
    ;; db/upsert-sports-site! reuses it) — same mechanism as bulk-ops mass-update.
    (let [claimed-count
          (jdbc-old/with-db-transaction [tx db]
            ;; Flip the status FIRST, guarded on the current state. 0 rows ⇒ a
            ;; concurrent approval already won the row — throw to roll back so the
            ;; claim runs exactly once.
            (when (zero? (first (jdbc-old/execute! tx
                                                   ["UPDATE org_takeover_request
                                                     SET status='approved', decided_by=?, decided_at=now()
                                                     WHERE id=? AND status='requested'"
                                                    (utils/->uuid-safe (:id actor)) (utils/->uuid-safe request-id)])))
              (throw (ex-info "Takeover request already decided"
                              {:type :invalid-takeover-state})))
            (let [;; one IN-query for all current revisions instead of a SELECT per
                  ;; lipas-id (F17 — Helsinki scale ≈ 3700 sites ⇒ ~3700 round trips
                  ;; saved). Ids deleted since the request just don't come back —
                  ;; skipped. The rows come back unmarshalled; enrich-activities is
                  ;; reapplied per doc so each one is exactly what
                  ;; core/get-sports-site used to return here.
                  claimable (->> (db/get-sports-sites-by-lipas-ids tx lipas-ids)
                                 (map core/enrich-activities)
                                 ;; already owned by this org (claimed between
                                 ;; request and approval) → idempotent skip
                                 (remove #(= (str org-id) (some-> (:owner-org-id %) str))))
                  updated (doall
                           (for [site claimable]
                             ;; fresh :event-date — reusing the stored one would create
                             ;; a duplicate (lipas-id, event-date) revision pair (FE
                             ;; history keys by event-date) and misdate the claim
                             (->> (cond-> (assoc site
                                                 :event-date    (utils/timestamp)
                                                 :owner-org-id  (str org-id)
                                                 :acting-org-id (str org-id))
                                    owner-enum (assoc :owner owner-enum))
                                  (core/upsert-sports-site!* tx actor))))]
              ;; one bulk index for all of them (refresh=wait_for, so the immediate
              ;; "our sites" refresh after a reclaim sees the freshly-claimed sites)
              (when (seq updated)
                (let [idx (get-in search [:indices :sports-site :search])
                      ;; resolved once per batch — owner just changed, so the docs
                      ;; must carry the new owner org's name (F15)
                      org-names (core/org-names db)]
                  (search/bulk-index-sync! (:client search)
                                           (search/->bulk idx :lipas-id
                                                          (map #(core/enrich % org-names) updated)))))
              (count updated)))]
      {:status        "approved"
       :org-id        (str org-id)
       :sites-claimed claimed-count
       ;; already-owned + no-longer-existing — surfaced so the approver can see
       ;; when the applied set ended up smaller than the requested one
       :sites-skipped (- (count lipas-ids) claimed-count)})))

(defn reclaim-now!
  "LIPAS-admin direct reclaim: create a request and immediately approve it, so a
  one-time setup doesn't bounce through the approval queue (the queue is for
  org-admin-initiated requests). Reuses create-request! + approve! verbatim —
  including the optional curated `:lipas-ids` subset. Note the snapshot is
  taken right here, so a direct claim still applies the rule's live matches."
  [db search org-id actor & {:keys [lipas-ids]}]
  ;; create-request! wants the requester's id; approve! wants the actor map
  (let [req (create-request! db org-id (:id actor) :lipas-ids lipas-ids)]
    (approve! db search (:id req) actor)))

(defn deny!
  [db request-id actor]
  ;; Guarded like approve! — only a pending request can be denied.
  (let [res (jdbc/execute-one! db
                               ["UPDATE org_takeover_request
                                 SET status='denied', decided_by=?, decided_at=now()
                                 WHERE id=? AND status='requested'"
                                (utils/->uuid-safe (:id actor)) (utils/->uuid-safe request-id)])]
    (when (zero? (:next.jdbc/update-count res))
      (throw (ex-info "Takeover request is not awaiting a decision"
                      {:type :invalid-takeover-state})))
    {:status "denied"}))
