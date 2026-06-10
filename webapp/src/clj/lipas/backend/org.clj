(ns lipas.backend.org
  "Organizations as an append-only revision log.

  The `org` table holds one row per change (mirroring `sports_site`); the
  `org_current` view exposes the latest active revision per `org_id`. The org
  state — name, type, contact, PTV config, role-template catalog, ownership rule
  and membership — lives in a single jsonb `document`. `org_id` is the stable
  identity (== the old single-row `org.id`) preserved across revisions.

  Reads go through `org_current`; writes append a new revision carrying the
  previous document forward. Nothing here mutates `account` rows: membership is
  the document's concern, and the roles a membership confers are projected at
  login (see `derive-org-roles` / `derive-user-org-roles`)."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [honey.sql :as hsql]
            [lipas.backend.db.utils :as db-utils]
            [lipas.roles :as roles]
            [lipas.schema.org :as org-schema]
            [lipas.utils :as utils]
            [malli.core :as m]
            [malli.error :as me]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]))

;;; marshalling between the external org map and the revision document ;;;

(defn marshall
  "External org map -> revision document. Accepts both the new top-level
  `:primary-contact` and the legacy `:data {:primary-contact ..}` shape."
  [org]
  {:name            (:name org)
   :type            (or (:type org) "city")
   :primary-contact (or (:primary-contact org)
                        (get-in org [:data :primary-contact]))
   :instructions    (:instructions org)
   :ptv-data        (:ptv-data org)
   :role-templates  (or (:role-templates org) {})
   :ownership       (or (:ownership org) {:city-codes [] :owners []})
   :members         (or (:members org) [])})

(defn unmarshall
  "org_current row -> external org map. `:id` is the stable `org_id`. Keeps the
  legacy `:data`/`:ptv-data` keys so existing consumers (PTV, system, FE) work,
  and exposes the new org-management keys."
  [{:keys [org-id document]}]
  (when document
    {:id              org-id
     :name            (:name document)
     :data            {:primary-contact (:primary-contact document)}
     :ptv-data        (:ptv-data document)
     :type            (:type document)
     :primary-contact (:primary-contact document)
     :instructions    (:instructions document)
     :role-templates  (:role-templates document)
     :ownership       (:ownership document)
     :members         (:members document)}))

;;; revision plumbing ;;;

(defn- current-row [db org-id]
  (jdbc/execute-one! db ["SELECT org_id, document FROM org_current WHERE org_id = ?"
                         (utils/->uuid-safe org-id)]
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn- current-document [db org-id]
  (:document (current-row db org-id)))

(defn- append-revision!
  "Insert a new revision for `org-id` with `document`."
  [db org-id document author-id status]
  (jdbc/execute-one! db
                     ["INSERT INTO org (org_id, author_id, status, document) VALUES (?,?,?,?)"
                      (utils/->uuid-safe org-id) (utils/->uuid-safe author-id) (or status "active") document]
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn- update-document!
  "Carry the current document forward, apply (f document), append a new active
  revision authored by `author-id`. Returns the new document."
  [db org-id author-id f]
  (let [new-doc (f (current-document db org-id))]
    (append-revision! db org-id new-doc author-id "active")
    new-doc))

;;; reads ;;;

(defn all-orgs [db]
  (->> (jdbc/execute! db ["SELECT org_id, document FROM org_current ORDER BY document->>'name'"]
                      {:builder-fn rs/as-unqualified-kebab-maps})
       (mapv unmarshall)))

(defn get-org
  "Get organization by its stable org-id."
  [db org-id]
  (unmarshall (current-row db org-id)))

(defn get-org-by-ptv-org-id
  "Get organization by its PTV org ID."
  [db ptv-org-id]
  (->> (jdbc/execute-one! db
                          ["SELECT org_id, document FROM org_current
           WHERE document->'ptv-data'->>'org-id' = ?" (str ptv-org-id)]
                          {:builder-fn rs/as-unqualified-kebab-maps})
       unmarshall))

(defn user-orgs
  "Orgs the user is a member of (reverse jsonb-containment over org_current;
  GIN-indexed)."
  [db user-id]
  (->> (jdbc/execute! db
                      ["SELECT org_id, document FROM org_current WHERE document->'members' @> ?"
                       [{:user-id (str user-id)}]]
                      {:builder-fn rs/as-unqualified-kebab-maps})
       (mapv unmarshall)))

(defn get-org-users
  "Member accounts of the org, each augmented with their `:roles` from the
  membership document. Returns full account maps (incl. `:permissions`) so
  callers that filter on direct roles (e.g. PTV managers) keep working."
  [db org-id]
  (let [members (:members (current-document db org-id))
        by-uid  (into {} (map (juxt :user-id identity)) members)
        ids     (keep (comp utils/->uuid-safe :user-id) members)]
    (if (seq ids)
      (->> (sql/query db (hsql/format {:select [:id :email :username :permissions]
                                       :from   [:account]
                                       :where  [:in :id ids]})
                      {:builder-fn rs/as-unqualified-kebab-maps})
           (map db-utils/->kebab-case-keywords)
           (mapv (fn [user]
                   (let [m (get by-uid (str (:id user)))]
                     (assoc user :roles (:roles m))))))
      ;; Always a vector — a memberless org must serialize as [] (an empty
      ;; response body is not valid JSON and breaks the members view).
      [])))

;;; account-name resolution (shared by org history + site edit history) ;;;

(defn resolve-account-names
  "Batch-resolve account ids → display label in one query. Returns a map keyed
  by string id (nil when `ids` is empty); ids absent from `account` are simply
  missing. Emails are PII: they are included only when `emails?` is set —
  callers gate it on an admin-grade privilege (`:users/manage` for the open
  site-edit-history endpoint; org history is `:org/manage`-gated); everyone
  else gets the username. The ONE place the masking rule lives (F25)."
  [db ids emails?]
  (let [ids (->> ids (keep utils/->uuid-safe) distinct vec)]
    (when (seq ids)
      (->> (sql/query db (hsql/format {:select [:id :email :username]
                                       :from   [:account]
                                       :where  [:in :id ids]})
                      {:builder-fn rs/as-unqualified-kebab-maps})
           (map (fn [a] [(str (:id a)) (if emails?
                                         (or (:email a) (:username a))
                                         (:username a))]))
           (into {})))))

(defn org-member-ids
  "Subset of account `ids` that are a member of at least one org. ONE query for
  the whole batch (no N+1): unnests every org's members and intersects with the
  candidate ids. Returns a set of string ids. NB: catalog-projected org roles
  live in the org documents, not on the account — this is the authoritative
  membership source."
  [db ids]
  (let [ids (->> ids (keep utils/->uuid-safe) (map str) distinct vec)]
    (if (empty? ids)
      #{}
      (->> (jdbc/execute! db
                          (into [(str "SELECT DISTINCT m->>'user-id' AS user_id"
                                      " FROM org_current,"
                                      " jsonb_array_elements(document->'members') m"
                                      " WHERE m->>'user-id' IN ("
                                      (str/join "," (repeat (count ids) "?")) ")")]
                                ids)
                          {:builder-fn rs/as-unqualified-kebab-maps})
           (map :user-id)
           set))))

(defn resolve-author-role-labels
  "Batch-classify account ids → coarse role label for the site edit-history
  shown to viewers WITHOUT :users/manage (F38, GDPR): \"admin\" /
  \"municipality\" / \"organization\" / \"other\" — never a person identifier.

  The label is derived AT READ TIME from each author's CURRENT stored
  permissions and CURRENT org membership; role-at-edit-time is not recorded,
  which is acceptable for this transparency view. Two queries total for the
  whole batch (account permissions + org membership), no N+1. Returns a map
  keyed by string id (nil when `ids` is empty); ids absent from `account` are
  simply missing — callers fall back to \"other\"."
  [db ids]
  (let [ids (->> ids (keep utils/->uuid-safe) distinct vec)]
    (when (seq ids)
      (let [accounts (->> (sql/query db (hsql/format {:select [:id :permissions]
                                                      :from   [:account]
                                                      :where  [:in :id ids]})
                                     {:builder-fn rs/as-unqualified-kebab-maps})
                          ;; raw permissions carry both city-code/city_code key
                          ;; spellings — normalize + conform like user/unmarshall
                          (map db-utils/->kebab-case-keywords)
                          (map (fn [a] (update a :permissions
                                               #(cond-> % (:roles %) (update :roles roles/conform-roles))))))
            in-org?  (org-member-ids db ids)]
        (->> accounts
             (map (fn [{:keys [id permissions]}]
                    [(str id)
                     (cond
                       (roles/check-role {:permissions permissions} :admin) "admin"
                       (some :city-code (:roles permissions))               "municipality"
                       (contains? in-org? (str id))                         "organization"
                       :else                                                "other")]))
             (into {}))))))

;;; history (the append-only org log IS the audit trail) ;;;

(defn- member-by-uid [doc]
  (into {} (map (fn [m] [(str (:user-id m)) m])) (:members doc)))

(defn- diff-org-docs
  "Human-readable summary of what changed between two consecutive org documents.
  `prev` nil means the org's first revision. `name-of` resolves a member
  user-id (string) to a human-readable label (email/username), falling back to
  the raw id."
  [name-of prev doc]
  (if (nil? prev)
    ["Organisaatio luotu"]
    (let [changes (volatile! [])
          add! (fn [s] (vswap! changes conj s))]
      (when (not= (:name prev) (:name doc))
        (add! (str "Nimi: " (:name prev) " → " (:name doc))))
      (when (not= (:type prev) (:type doc))
        (add! (str "Tyyppi: " (:type prev) " → " (:type doc))))
      (when (not= (:primary-contact prev) (:primary-contact doc))
        (add! "Yhteystiedot päivitetty"))
      (when (not= (:instructions prev) (:instructions doc))
        (add! "Ohjeet päivitetty"))
      (when (not= (:role-templates prev) (:role-templates doc))
        (add! "Roolimallit päivitetty"))
      (when (not= (:ownership prev) (:ownership doc))
        (add! "Omistussääntö päivitetty"))
      (when (not= (:ptv-data prev) (:ptv-data doc))
        (add! "PTV-asetukset päivitetty"))
      (let [pm (member-by-uid prev)
            dm (member-by-uid doc)
            added (set/difference (set (keys dm)) (set (keys pm)))
            removed (set/difference (set (keys pm)) (set (keys dm)))
            common (set/intersection (set (keys pm)) (set (keys dm)))]
        (doseq [uid added] (add! (str "Jäsen lisätty: " (name-of uid))))
        (doseq [uid removed] (add! (str "Jäsen poistettu: " (name-of uid))))
        (doseq [uid common
                :let [a (get pm uid) b (get dm uid)]]
          (when (not= (set (:roles a)) (set (:roles b)))
            (add! (str "Roolit muuttui: " (name-of uid) " "
                       (vec (:roles a)) " → " (vec (:roles b)))))))
      (let [result @changes]
        (if (seq result) result ["Muutos"])))))

(defn get-history
  "All revisions of an org (newest first), each with a computed summary of the
  changes vs. the previous revision. Pure read over the append-only `org` table.

  Two author modes, branched on `emails?` (the route gates it on :users/manage):
  - emails? true  → rows carry :author-name (the author's email — lipas-admin
    person view)
  - otherwise     → rows carry :author-role (coarse current-role label,
    \"admin\"/\"municipality\"/\"organization\"/\"other\" — same classification
    and FE i18n keys as site-edit-history, F38): org admins see WHO-class +
    WHEN, never a person identifier. Member references inside the change
    summaries keep emails in both modes — they are this org's own members,
    already visible to every member on the Jäsenet tab."
  ([db org-id] (get-history db org-id false))
  ([db org-id emails?]
   (let [rows (jdbc/execute! db
                             ["SELECT id, org_id, event_date, author_id, status, document
                              FROM org WHERE org_id = ? ORDER BY event_date ASC, created_at ASC"
                              (utils/->uuid-safe org-id)]
                             {:builder-fn rs/as-unqualified-kebab-maps})
         member-ids   (->> rows (mapcat (comp :members :document)) (keep :user-id))
         author-ids   (->> rows (keep :author-id))
         accounts     (resolve-account-names db (concat author-ids member-ids) true)
         name-of      (fn [uid] (get accounts (str uid) (str uid)))
         author-roles (when-not emails?
                        (resolve-author-role-labels db author-ids))]
     (->> rows
          (map-indexed
            (fn [i row]
              (let [doc  (:document row)
                    prev (when (pos? i) (:document (nth rows (dec i))))]
                (cond-> {:id         (str (:id row))
                         :event-date (str (:event-date row))
                         :status     (:status row)
                         :changes    (diff-org-docs name-of prev doc)}
                  emails?       (assoc :author-name
                                       (when-let [aid (:author-id row)]
                                         (get accounts (str aid))))
                  (not emails?) (assoc :author-role
                                       (when-let [aid (:author-id row)]
                                         (get author-roles (str aid))))))))
          reverse
          vec))))

;;; writes ;;;

(defn create-org
  "Create an org as its first revision. Uses `(:id org)` as the stable org-id if
  supplied (preserves seeded/test ids), otherwise a fresh uuid."
  ([db org] (create-org db org nil))
  ([db org author-id]
   (let [org-id (or (:id org) (random-uuid))]
     (append-revision! db org-id (marshall org) author-id "active")
     (get-org db org-id))))

(defn- merge-org-details
  "Merge the marshalled details of `org` into the current document `doc`,
  touching only the keys of `ks` the payload actually carries. `marshall`
  always emits every key (defaulting absent ones to nil/empty), so merging
  blindly would wipe stored values on a partial payload — presence in the
  INPUT map decides what gets written. `:primary-contact` also counts as
  present via the legacy `:data {:primary-contact ..}` shape."
  [doc org ks]
  (let [present? (fn [k]
                   (if (= k :primary-contact)
                     (or (contains? org :primary-contact)
                         (contains? (:data org) :primary-contact))
                     (contains? org k)))]
    (merge doc (select-keys (marshall org) (filterv present? ks)))))

(def ^:private full-org-details-keys
  [:name :type :primary-contact :instructions :ptv-data :ownership])

(def ^:private admin-editable-org-details-keys
  "The org-admin self-service ceiling: details an org-admin may edit. Excludes
  `:type`/`:ownership` (the take-over ceiling) and `:ptv-data` (lipas-admin
  only — it has its own `:users/manage`-gated endpoint)."
  [:name :primary-contact :instructions])

(defn update-org!
  "Privileged FULL details update (name/type/contact/instructions/ptv/ownership)
  by appending a revision. For lipas-admin callers, seeds, migrations and tests
  — HTTP routes must dispatch on caller privilege and use `update-org-details!`
  for non-lipas-admins (see /actions/update-org). Only keys present in the
  payload are written; members and the role-template catalog are preserved from
  the current revision — they are managed through their own member/catalog
  operations, never wiped by a details edit."
  ([db org-id org] (update-org! db org-id org nil))
  ([db org-id org author-id]
   (update-document! db org-id author-id
                     #(merge-org-details % org full-org-details-keys))))

(defn update-org-details!
  "Org-admin self-service details update. Merges ONLY the admin-editable
  whitelist (name/contact/instructions); `:type`/`:ownership` (the take-over
  ceiling) and `:ptv-data` in the payload are ignored, so an org-admin can
  never widen their own ownership rule or PTV config — enforced here in the
  business layer so every caller shares the rule."
  [db org-id org author-id]
  (update-document! db org-id author-id
                    #(merge-org-details % org admin-editable-org-details-keys)))

(defn update-org-ptv-config!
  "Update only the org's PTV configuration."
  ([db org-id ptv-config] (update-org-ptv-config! db org-id ptv-config nil))
  ([db org-id ptv-config author-id]
   (update-document! db org-id author-id #(assoc % :ptv-data ptv-config))))

;;; membership & catalog ops (self-service, §10) ;;;

(defn- upsert-member
  "Add or update a member with a full role assignment (one `:roles` list)."
  [members user-id roles]
  (if (some #(= (:user-id %) user-id) members)
    (mapv (fn [m] (if (= (:user-id m) user-id) (assoc m :roles roles) m)) members)
    (conj (vec members) {:user-id user-id :roles roles})))

(defn- drop-member [members user-id]
  (vec (remove #(= (:user-id %) user-id) members)))

(defn catalog-template-keys
  "The set of template names (strings) defined in an org's role-template catalog
  — the ceiling an org-admin may assign from. Takes any map carrying the org's
  `:role-templates` (a revision document or an unmarshalled org)."
  [org-doc]
  (set (map name (keys (:role-templates org-doc)))))

(defn validate-assignment!
  "Throw unless `:roles` ⊆ `#{\"admin\"}` ∪ the org's catalog keys. Belt-and-
  suspenders on top of the structural ceiling in derive-org-roles — the real
  guarantee is that projection only expands reserved + catalog keys, but
  rejecting early is better UX and defense in depth.

  Validates against the given org document (any map carrying `:role-templates`)
  so callers that already hold the current document validate and write against
  the SAME revision; the db+org-id arity fetches it first."
  ([db org-id assignment]
   (validate-assignment! (current-document db org-id) assignment))
  ([org-doc {:keys [roles]}]
   (let [catalog  (catalog-template-keys org-doc)
         allowed  (conj catalog "admin")
         assigned (set (map name (or roles [])))]
     (when-not (set/subset? assigned allowed)
       (throw (ex-info "Roles must be a subset of {admin} ∪ the org's role-template catalog"
                       {:type :roles-outside-catalog
                        :invalid (vec (set/difference assigned allowed))
                        :catalog (vec catalog)})))
     true)))

(defn validate-catalog!
  "Throw unless `role-templates` conforms to the catalog schema — every spec must
  name a real, catalog-assignable role and carry well-typed context. This is the
  guard that makes a catalog payload always make sense: it rejects unknown roles
  and structurally malformed entries (e.g. a non-seq `:roles`) that would
  otherwise be silently dropped at projection — or, worse, throw at login and
  lock the org's members out. Belt-and-suspenders on top of the endpoint's
  request coercion; also protects REPL/internal callers."
  [role-templates]
  (when-not (m/validate org-schema/role-templates role-templates)
    (throw (ex-info "Invalid role-template catalog"
                    {:type :invalid-catalog
                     :errors (me/humanize (m/explain org-schema/role-templates role-templates))})))
  ;; Beyond structural validity: a context-scoped role whose required key is
  ;; *absent* would project an UNSCOPED, org-wide grant — `select-role` treats a
  ;; missing context key as "always active" (e.g. a `ptv-manager` spec with no
  ;; `:city-code` ⇒ PTV management everywhere). Require the key to be present
  ;; (an empty value is fine — it simply matches nothing). `:org-id` is excepted;
  ;; it is injected at projection, never stored in the catalog.
  (doseq [[tname {tmpl-roles :roles}] role-templates
          spec tmpl-roles
          :let [req (remove #{:org-id}
                            (:required-context-keys (get roles/roles (keyword (:role spec)))))]
          :when (seq req)]
    (when-not (every? #(contains? spec %) req)
      (throw (ex-info "Role-template spec is missing a required context key"
                      {:type :invalid-catalog
                       :template tname
                       :role (:role spec)
                       :required (vec req)}))))
  true)

(defn- normalize-catalog
  "Server-side mirror of the FE's sanitize-catalog (F31): per template, drop
  role-less specs (half-filled editor rows / sloppy direct-API payloads) and
  collapse exact-duplicate specs — a duplicate grants nothing extra, it only
  renders twice and would creep back in via API callers if only the FE deduped.
  Touches `:roles` only when it is actually a sequence; anything else falls
  through untouched for validate-catalog! to reject as before."
  [role-templates]
  (into {}
        (map (fn [[k entry]]
               [k (cond-> entry
                    (sequential? (:roles entry))
                    (update :roles #(vec (distinct (filter :role %)))))]))
        role-templates))

(defn update-catalog!
  "Replace the org's role-template catalog (lipas-admin only — the ceiling).
  The payload is normalized (deduped, role-less specs dropped) before
  validation and storage."
  [db org-id role-templates author-id]
  (let [role-templates (normalize-catalog role-templates)]
    (validate-catalog! role-templates)
    (update-document! db org-id author-id #(assoc % :role-templates role-templates))))

(defn add-member!
  "Add/update a member with a validated assignment. `user-id` must reference an
  existing account (the invite endpoint creates the account first). Fetches the
  current document once and both validates and writes against it."
  [db org-id user-id assignment author-id]
  (let [doc     (current-document db org-id)
        _       (validate-assignment! doc assignment)
        new-doc (update doc :members upsert-member
                        (str user-id)
                        (mapv name (or (:roles assignment) [])))]
    (append-revision! db org-id new-doc author-id "active")
    new-doc))

(defn set-member-roles!
  "Replace a member's role list (validated ⊆ `#{admin}` ∪ catalog). Collapses the
  old admin/member promotion and template-assignment ops into one. Org-admins may
  manage their org's members (OQ6). Fetches the current document once and both
  validates and writes against it."
  [db org-id user-id roles author-id]
  (let [doc     (current-document db org-id)
        _       (validate-assignment! doc {:roles roles})
        new-doc (update doc :members
                        (fn [ms] (mapv (fn [m] (if (= (:user-id m) (str user-id))
                                                 (assoc m :roles (mapv name (or roles []))) m))
                                       ms)))]
    (append-revision! db org-id new-doc author-id "active")
    new-doc))

(defn remove-member!
  [db org-id user-id author-id]
  (update-document! db org-id author-id
                    (fn [doc] (update doc :members drop-member (str user-id)))))

;;; permission projection (member assignments × catalog -> role data) ;;;

(def org-scoped-roles
  "Roles whose context is the org itself (org-id injected at projection)."
  #{:org-editor :org-admin})

(def reserved-roles
  "Engine-level roles a member may hold that are NOT part of the per-org catalog.
  Reserved here (in code) so a catalog edit can never strip them. `:admin` (the
  string \"admin\" on the wire) projects org-admin (`:org/manage`); its `:org-id`
  is injected at projection like any other org-scoped role."
  {:admin {:role "org-admin"}})

(defn- member->roles
  "Project one org membership into raw role-data (string role, vector org-id —
  the stored wire shape, conformed downstream). Every member gets the `org-user`
  baseline (membership ⟺ `:org/member`). Each key in the member's single `:roles`
  list expands via the reserved-role table (engine-level, un-strippable) or the
  org's catalog. The structural ceiling lives in that expansion: a key that is
  neither reserved nor in the catalog expands to nothing, so an org-admin can
  never project authority beyond `#{admin}` ∪ the catalog."
  [org-id catalog member]
  (let [org-id-vec (vector (str org-id))
        baseline   {:role "org-user" :org-id org-id-vec}
        assigned   (->> (:roles member)
                        (mapcat (fn [k]
                                  (or (some-> (reserved-roles (keyword k)) vector)
                                      (:roles (get catalog (keyword k)))))) ; <- ceiling here
                        (remove nil?))]
    (->> (cons baseline assigned)
         (map (fn [spec]
                (cond-> spec
                  (org-scoped-roles (keyword (:role spec))) (assoc :org-id org-id-vec)))))))

(defn derive-org-roles
  "Pure projection: for each org the user belongs to, expand that user's member
  assignments against the org's role-template catalog into role data. None of it
  is persisted — it lives only in the JWT (see derive-user-org-roles)."
  [user-id orgs]
  (let [uid (str user-id)]
    (->> orgs
         (mapcat (fn [{:keys [id role-templates members]}]
                   (when-let [member (first (filter #(= uid (str (:user-id %))) members))]
                     (member->roles id role-templates member))))
         (remove nil?)
         vec)))

(defn derive-user-org-roles
  "Read the orgs the user is a member of and project their org-derived roles.
  Called at token-creation time (login / refresh) to enrich the user's roles."
  [db user-id]
  (derive-org-roles user-id (user-orgs db user-id)))

(comment
  (all-orgs (:lipas/db integrant.repl.state/system))
  (get-org (:lipas/db integrant.repl.state/system) #uuid "d068ec10-0928-4ed1-883a-f40f3c698f32")
  (user-orgs (:lipas/db integrant.repl.state/system) #uuid "47fd4126-d923-47fb-afe3-ce9e7c257d1f"))
