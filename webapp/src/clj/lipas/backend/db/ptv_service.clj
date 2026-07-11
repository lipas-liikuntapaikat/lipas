(ns lipas.backend.db.ptv-service
  "Accessors for the append-only ptv_service table holding LIPAS-managed
   PTV Service content revisions (names, descriptions, audits). Logical
   identity is (org_id, source_id); ptv_service_current shows the latest
   revision per lineage regardless of status."
  (:require [honey.sql :as hsql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]))

(defn- ->uuid [x]
  (if (string? x) (parse-uuid x) x))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn insert-service-rev!
  "Appends a new ptv_service revision. `event-date` is an ISO-8601 string
   (lipas.utils/timestamp); parsed to OffsetDateTime here because PGJDBC
   doesn't coerce varchar->timestamptz on insert."
  [db {:keys [org-id source-id service-id status author-id event-date document]}]
  (sql/insert! db :ptv_service
               {:org-id (->uuid org-id)
                :source-id source-id
                :service-id (some-> service-id ->uuid)
                :status (or status "active")
                :author-id (some-> author-id ->uuid)
                :event-date (java.time.OffsetDateTime/parse event-date)
                :document document}
               (assoc jdbc/unqualified-snake-kebab-opts :return-keys true)))

(defn get-current-by-org
  "Latest revision of every service lineage the org has."
  [db org-id]
  (sql/query db
             (hsql/format {:select [:*]
                           :from [:ptv_service_current]
                           :where [:= :org_id (->uuid org-id)]})
             query-opts))

(defn get-current
  "Latest revision of the (org-id, source-id) lineage, or nil."
  [db org-id source-id]
  (first
   (sql/query db
              (hsql/format {:select [:*]
                            :from [:ptv_service_current]
                            :where [:and
                                    [:= :org_id (->uuid org-id)]
                                    [:= :source_id source-id]]})
              query-opts)))

(defn get-current-by-service-id
  "Latest revision of the org's lineage carrying the given PTV Service UUID, or nil."
  [db org-id service-id]
  (first
   (sql/query db
              (hsql/format {:select [:*]
                            :from [:ptv_service_current]
                            :where [:and
                                    [:= :org_id (->uuid org-id)]
                                    [:= :service_id (->uuid service-id)]]})
              query-opts)))

(defn get-history
  "All revisions of the (org-id, source-id) lineage, newest first."
  [db org-id source-id]
  (sql/query db
             (hsql/format {:select [:*]
                           :from [:ptv_service]
                           :where [:and
                                   [:= :org_id (->uuid org-id)]
                                   [:= :source_id source-id]]
                           :order-by [[:event_date :desc]]})
             query-opts))
