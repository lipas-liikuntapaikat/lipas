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
            [honey.sql :as hsql]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.utils :as db-utils]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]))

(defn- ->uuid [x]
  (if (string? x) (parse-uuid x) x))

;;; marshalling between the external org map and the revision document ;;;

(defn marshall
  "External org map -> revision document. Accepts both the new top-level
  `:primary-contact` and the legacy `:data {:primary-contact ..}` shape."
  [org]
  {:name            (:name org)
   :type            (or (:type org) "city")
   :primary-contact (or (:primary-contact org)
                        (get-in org [:data :primary-contact]))
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
     :role-templates  (:role-templates document)
     :ownership       (:ownership document)
     :members         (:members document)}))

;;; revision plumbing ;;;

(defn- current-row [db org-id]
  (jdbc/execute-one! db ["SELECT org_id, document FROM org_current WHERE org_id = ?"
                         (->uuid org-id)]
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn- current-document [db org-id]
  (:document (current-row db org-id)))

(defn- append-revision!
  "Insert a new revision for `org-id` with `document`."
  [db org-id document author-id status]
  (jdbc/execute-one! db
                     ["INSERT INTO org (org_id, author_id, status, document) VALUES (?,?,?,?)"
                      (->uuid org-id) (some-> author-id ->uuid) (or status "active") document]
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
  "Member accounts of the org, each augmented with their `:org-role` and
  `:templates` from the membership document. Returns full account maps (incl.
  `:permissions`) so callers that filter on direct roles (e.g. PTV managers)
  keep working."
  [db org-id]
  (let [members (:members (current-document db org-id))
        by-uid  (into {} (map (juxt :user-id identity)) members)
        ids     (keep (comp ->uuid :user-id) members)]
    (when (seq ids)
      (->> (sql/query db (hsql/format {:select [:id :email :username :permissions]
                                       :from   [:account]
                                       :where  [:in :id ids]})
                      {:builder-fn rs/as-unqualified-kebab-maps})
           (map db-utils/->kebab-case-keywords)
           (mapv (fn [user]
                   (let [m (get by-uid (str (:id user)))]
                     (assoc user :org-role (:org-role m) :templates (:templates m)))))))))

;;; writes ;;;

(defn create-org
  "Create an org as its first revision. Uses `(:id org)` as the stable org-id if
  supplied (preserves seeded/test ids), otherwise a fresh uuid."
  ([db org] (create-org db org nil))
  ([db org author-id]
   (let [org-id (or (:id org) (random-uuid))]
     (append-revision! db org-id (marshall org) author-id "active")
     (get-org db org-id))))

(defn update-org!
  "Update org details (name/type/contact/ptv/ownership) by appending a revision.
  Members and the role-template catalog are preserved from the current revision
  — they are managed through their own member/catalog operations, never wiped by
  a details edit."
  ([db org-id org] (update-org! db org-id org nil))
  ([db org-id org author-id]
   (update-document! db org-id author-id
                     (fn [doc]
                       (merge doc (select-keys (marshall org)
                                               [:name :type :primary-contact :ptv-data :ownership]))))))

(defn update-org-ptv-config!
  "Update only the org's PTV configuration."
  ([db org-id ptv-config] (update-org-ptv-config! db org-id ptv-config nil))
  ([db org-id ptv-config author-id]
   (update-document! db org-id author-id #(assoc % :ptv-data ptv-config))))

;;; membership ops ;;;

(defn- upsert-member [members user-id org-role]
  (if (some #(= (:user-id %) user-id) members)
    (mapv (fn [m] (if (= (:user-id m) user-id) (assoc m :org-role org-role) m)) members)
    (conj (vec members) {:user-id user-id :org-role org-role :templates []})))

(defn- drop-member [members user-id]
  (vec (remove #(= (:user-id %) user-id) members)))

(defn- resolve-user-id
  "Resolve a change spec to an existing account id (string). Throws if an email
  references no account (legacy endpoint behavior; the P3 invite endpoint creates
  accounts instead)."
  [db {:keys [user-id email]}]
  (cond
    user-id (str (->uuid user-id))
    email   (let [user (db/get-user-by-email db {:email email})]
              (when (nil? user)
                (throw (ex-info (str "No user found with email address: " email ". "
                                     "The user must first register an account with LIPAS "
                                     "before they can be added to an organization.")
                                {:type :user-not-found :email email})))
              (str (:id user)))))

(defn- apply-member-change [db doc {:keys [change role] :as spec}]
  (let [uid      (resolve-user-id db spec)
        org-role (case role "org-admin" "admin" "org-user" "member" "member")]
    (update doc :members
            (fn [members]
              (case change
                "add"    (upsert-member members uid org-role)
                "remove" (drop-member members uid)
                members)))))

(defn update-org-users!
  "Apply a batch of member add/remove changes by appending a single org revision.
  Membership now lives in the org document; account roles are not touched."
  ([db org-id changes] (update-org-users! db org-id changes nil))
  ([db org-id changes author-id]
   (update-document! db org-id author-id
                     (fn [doc]
                       (reduce (fn [doc change] (apply-member-change db doc change)) doc changes)))))

;;; template-aware member & catalog ops (self-service, §10) ;;;

(defn- upsert-member-full
  "Add or update a member with full assignment (org-role + templates)."
  [members user-id org-role templates]
  (if (some #(= (:user-id %) user-id) members)
    (mapv (fn [m] (if (= (:user-id m) user-id)
                    (assoc m :org-role org-role :templates templates)
                    m))
          members)
    (conj (vec members) {:user-id user-id :org-role org-role :templates templates})))

(defn catalog-template-keys
  "The set of template names (strings) defined in the org's role-template catalog
  — the ceiling an org-admin may assign from."
  [db org-id]
  (set (map name (keys (:role-templates (get-org db org-id))))))

(defn validate-assignment!
  "Throw unless `:org-role` ∈ {admin member} (when present) and `:templates` ⊆ the
  org's catalog. Belt-and-suspenders on top of the structural ceiling in
  derive-org-roles — the real guarantee is that projection only expands catalog
  templates, but rejecting early is better UX and defense in depth."
  [db org-id {:keys [org-role templates]}]
  (let [catalog (catalog-template-keys db org-id)
        tmpl    (set (map name (or templates [])))]
    (when (and org-role (not (#{"admin" "member"} (name org-role))))
      (throw (ex-info "Invalid org-role (must be admin or member)"
                      {:type :invalid-org-role :org-role org-role})))
    (when-not (set/subset? tmpl catalog)
      (throw (ex-info "Templates must be a subset of the org's role-template catalog"
                      {:type :templates-outside-catalog
                       :invalid (vec (set/difference tmpl catalog))
                       :catalog (vec catalog)})))
    true))

(defn update-catalog!
  "Replace the org's role-template catalog (lipas-admin only — the ceiling)."
  [db org-id role-templates author-id]
  (update-document! db org-id author-id #(assoc % :role-templates role-templates)))

(defn add-member!
  "Add/update a member with a validated assignment. `user-id` must reference an
  existing account (the invite endpoint creates the account first)."
  [db org-id user-id assignment author-id]
  (validate-assignment! db org-id assignment)
  (update-document! db org-id author-id
                    (fn [doc]
                      (update doc :members upsert-member-full
                              (str user-id)
                              (name (or (:org-role assignment) "member"))
                              (mapv name (or (:templates assignment) []))))))

(defn set-member-org-role!
  "Promote/demote a member (admin <-> member). Org-admins may promote (OQ6)."
  [db org-id user-id org-role author-id]
  (validate-assignment! db org-id {:org-role org-role})
  (update-document! db org-id author-id
                    (fn [doc]
                      (update doc :members
                              (fn [ms] (mapv (fn [m] (if (= (:user-id m) (str user-id))
                                                       (assoc m :org-role (name org-role)) m))
                                             ms))))))

(defn set-member-templates!
  "Assign/replace a member's templates (validated ⊆ catalog)."
  [db org-id user-id templates author-id]
  (validate-assignment! db org-id {:templates templates})
  (update-document! db org-id author-id
                    (fn [doc]
                      (update doc :members
                              (fn [ms] (mapv (fn [m] (if (= (:user-id m) (str user-id))
                                                       (assoc m :templates (mapv name (or templates []))) m))
                                             ms))))))

(defn remove-member!
  [db org-id user-id author-id]
  (update-document! db org-id author-id
                    (fn [doc] (update doc :members drop-member (str user-id)))))

;;; permission projection (member assignments × catalog -> role data) ;;;

(def org-scoped-roles
  "Roles whose context is the org itself (org-id injected at projection)."
  #{:org-editor})

(defn- member->roles
  "Project one org membership into raw role-data (string role, vector org-id —
  the stored wire shape, conformed downstream). The structural ceiling lives in
  the `(get catalog ...)` keep: a template the lipas-admin didn't define expands
  to nothing, so an org-admin can never project authority beyond the catalog."
  [org-id catalog member]
  (let [org-id-vec (vector (str org-id))
        org-role   (case (keyword (:org-role member))
                     :admin  {:role "org-admin" :org-id org-id-vec}
                     :member {:role "org-user" :org-id org-id-vec}
                     nil)
        assigned   (->> (:templates member)
                        (keep #(get catalog (keyword %))) ; <- ceiling enforced here
                        (mapcat :roles))]
    (->> (cons org-role
               (for [spec assigned]
                 (cond-> spec
                   (org-scoped-roles (keyword (:role spec))) (assoc :org-id org-id-vec))))
         (remove nil?))))

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

(defn add-org-user-by-email!
  "Add an existing user to an org by email. For org admins who can't see all
  users. Returns {:success? :message}."
  ([db org-id email role] (add-org-user-by-email! db org-id email role nil))
  ([db org-id email role author-id]
   (try
     (update-org-users! db org-id [{:email email :change "add" :role role}] author-id)
     {:success? true :message "User successfully added to organization"}
     (catch Exception e
       (if (= (:type (ex-data e)) :user-not-found)
         {:success? false
          :message (str "No user found with email address: " email ". "
                        "The user must first register an account with LIPAS "
                        "before they can be added to an organization.")}
         (throw e))))))

(comment
  (all-orgs (:lipas/db integrant.repl.state/system))
  (get-org (:lipas/db integrant.repl.state/system) #uuid "d068ec10-0928-4ed1-883a-f40f3c698f32")
  (user-orgs (:lipas/db integrant.repl.state/system) #uuid "47fd4126-d923-47fb-afe3-ce9e7c257d1f"))
