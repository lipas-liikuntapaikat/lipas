(ns lipas.backend.org-takeover
  "Bulk ownership take-over: an org claims the sites matching its ownership rule
  (city-code ∈ rule.city-codes ∧ owner ∈ rule.owners). Modeled as a small
  requested -> approved/denied state machine (org admins request, LIPAS admins
  approve). Applying ownership is append-only site revisions setting
  :owner-org-id (and the locked :owner enum), authored by the approver and
  marked acting on behalf of the org."
  (:require [honey.sql :as hsql]
            [lipas.backend.core :as core]
            [lipas.backend.org :as org]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn- ->uuid [x] (if (string? x) (parse-uuid x) x))

(defn matching-lipas-ids
  "Current sites matching the ownership rule. city_code is stored as text, so
  the rule's integer city-codes are compared as strings."
  [db {:keys [city-codes owners]}]
  (when (and (seq city-codes) (seq owners))
    (->> (jdbc/execute! db
                        (hsql/format
                         {:select [:lipas_id]
                          :from   [:sports_site_current]
                          :where  [:and
                                   [:in :city_code (mapv str city-codes)]
                                   [:in [:->> :document [:inline "owner"]] (vec owners)]]})
                        {:builder-fn rs/as-unqualified-kebab-maps})
         (mapv :lipas-id))))

(defn create-request!
  "Create a pending take-over request snapshotting the org's ownership rule and
  the currently-matching sites."
  [db org-id requested-by]
  (let [rule      (:ownership (org/get-org db org-id))
        lipas-ids (matching-lipas-ids db rule)]
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
  `actor` is the deciding user (becomes the site author)."
  [db search request-id actor]
  (let [req        (get-request db request-id)
        org-id     (:org-id req)
        owner-enum (core/org-type->owner (:type (org/get-org db org-id)))
        lipas-ids  (matching-lipas-ids db (:ownership-rule req))]
    (doseq [lid lipas-ids]
      (let [site    (core/get-sports-site db lid)
            updated (cond-> (assoc site
                                   :owner-org-id  (str org-id)
                                   :acting-org-id (str org-id))
                      owner-enum (assoc :owner owner-enum))
            resp    (core/upsert-sports-site!* db actor updated)]
        (core/index! search resp)))
    (jdbc/execute-one! db
                       ["UPDATE org_takeover_request
                         SET status='approved', decided_by=?, decided_at=now() WHERE id=?"
                        (some-> actor :id ->uuid) (->uuid request-id)])
    {:status "approved" :org-id (str org-id) :sites-claimed (count lipas-ids)}))

(defn deny!
  [db request-id actor]
  (jdbc/execute-one! db
                     ["UPDATE org_takeover_request
                       SET status='denied', decided_by=?, decided_at=now() WHERE id=?"
                      (some-> actor :id ->uuid) (->uuid request-id)])
  {:status "denied"})
