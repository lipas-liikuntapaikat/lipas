(ns lipas.backend.org-takeover
  "Bulk ownership take-over: an org claims the sites matching its ownership rule
  (city-code ∈ rule.city-codes ∧ owner ∈ rule.owners). Modeled as a small
  requested -> approved/denied state machine (org admins request, LIPAS admins
  approve). Applying ownership is append-only site revisions setting
  :owner-org-id (and the locked :owner enum), authored by the approver and
  marked acting on behalf of the org."
  (:require [clojure.java.jdbc :as jdbc-old]
            [honey.sql :as hsql]
            [lipas.backend.core :as core]
            [lipas.backend.org :as org]
            [lipas.backend.search :as search]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn- ->uuid [x] (if (string? x) (parse-uuid x) x))

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
  the currently-matching sites."
  [db org-id requested-by]
  (let [rule      (:ownership (org/get-org db org-id))
        lipas-ids (matching-lipas-ids db rule org-id)]
    (jdbc/execute-one! db
                       ["INSERT INTO org_takeover_request
                         (org_id, status, ownership_rule, lipas_ids, requested_by)
                         VALUES (?,?,?,?,?) RETURNING *"
                        (->uuid org-id) "requested" rule (or lipas-ids []) (some-> requested-by ->uuid)]
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
  (jdbc/execute-one! db ["SELECT * FROM org_takeover_request WHERE id = ?" (->uuid request-id)]
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn approve!
  "Apply ownership to the currently-matching sites (recomputed at approval, not
  the request-time snapshot) and mark the request approved. Each site gets a new
  revision with :owner-org-id, the locked :owner enum, and acting_org_id = org.
  ES is updated in a SINGLE bulk request rather than one round-trip per site —
  the per-site index call was the dominant cost (~350ms/site). `actor` is the
  deciding user (becomes the site author)."
  [db search request-id actor]
  (let [req        (get-request db request-id)
        _          (when-not (= "requested" (:status req))
                     ;; only a pending request can be approved — never re-run a
                     ;; claim for an already-approved or denied request
                     (throw (ex-info "Takeover request is not awaiting a decision"
                                     {:type :invalid-takeover-state :status (:status req)})))
        org-id     (:org-id req)
        owner-enum (core/org-type->owner (:type (org/get-org db org-id)))
        ;; recompute at approval time, excluding sites already owned by this org
        ;; (idempotent re-claim: no redundant revisions for already-claimed sites)
        lipas-ids  (matching-lipas-ids db (:ownership-rule req) org-id)]
    ;; DB revisions + ES bulk index + request status in one transaction (mirrors
    ;; bulk-ops mass-update): either every matching site is claimed and the
    ;; request marked approved, or the whole thing rolls back.
    ;; clojure.java.jdbc tx (re-entrant: the nested with-db-transaction inside
    ;; db/upsert-sports-site! reuses it) — same mechanism as bulk-ops mass-update.
    (jdbc-old/with-db-transaction [tx db]
      ;; Flip the status FIRST, guarded on the current state. 0 rows ⇒ a
      ;; concurrent approval already won the row — throw to roll back so the
      ;; claim runs exactly once.
      (when (zero? (first (jdbc-old/execute! tx
                                             ["UPDATE org_takeover_request
                                               SET status='approved', decided_by=?, decided_at=now()
                                               WHERE id=? AND status='requested'"
                                              (some-> actor :id ->uuid) (->uuid request-id)])))
        (throw (ex-info "Takeover request already decided"
                        {:type :invalid-takeover-state})))
      (let [updated (doall
                     (for [lid lipas-ids]
                       (->> (cond-> (assoc (core/get-sports-site tx lid)
                                           :owner-org-id  (str org-id)
                                           :acting-org-id (str org-id))
                              owner-enum (assoc :owner owner-enum))
                            (core/upsert-sports-site!* tx actor))))]
        ;; one bulk index for all of them (refresh=wait_for, so the immediate
        ;; "our sites" refresh after a reclaim sees the freshly-claimed sites)
        (when (seq updated)
          (let [idx (get-in search [:indices :sports-site :search])]
            (search/bulk-index-sync! (:client search)
                                     (search/->bulk idx :lipas-id (map core/enrich updated)))))))
    {:status "approved" :org-id (str org-id) :sites-claimed (count lipas-ids)}))

(defn reclaim-now!
  "LIPAS-admin direct reclaim: create a request and immediately approve it, so a
  one-time setup doesn't bounce through the approval queue (the queue is for
  org-admin-initiated requests). Reuses create-request! + approve! verbatim."
  [db search org-id actor]
  ;; create-request! wants the requester's id; approve! wants the actor map
  (let [req (create-request! db org-id (:id actor))]
    (approve! db search (:id req) actor)))

(defn deny!
  [db request-id actor]
  ;; Guarded like approve! — only a pending request can be denied.
  (let [res (jdbc/execute-one! db
                               ["UPDATE org_takeover_request
                                 SET status='denied', decided_by=?, decided_at=now()
                                 WHERE id=? AND status='requested'"
                                (some-> actor :id ->uuid) (->uuid request-id)])]
    (when (zero? (:next.jdbc/update-count res))
      (throw (ex-info "Takeover request is not awaiting a decision"
                      {:type :invalid-takeover-state})))
    {:status "denied"}))
