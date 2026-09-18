(ns lipas.backend.db.ptv-service-audit
  "Accessors for the append-only ptv_service_audit table holding DVV's
   katselmointi verdicts on a LIPAS-managed PTV Service's texts. Logical
   identity is (org_id, source_id), like ptv_service;
   ptv_service_audit_current shows the latest audit per lineage.
   `document` is the audit map the UI and lipas.data.ptv work with
   (per-field :status/:feedback/:audited-content + :timestamp/:auditor-id);
   `service_revision_id` records which ptv_service revision the verdicts
   were given on."
  (:require [honey.sql :as hsql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]))

(defn- ->uuid [x]
  (if (string? x) (parse-uuid x) x))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn insert-audit!
  "Appends an audit for the (org-id, source-id) lineage. `event-date` is an
   ISO-8601 string (lipas.utils/timestamp), parsed to OffsetDateTime because
   PGJDBC doesn't coerce varchar->timestamptz on insert."
  [db {:keys [org-id source-id service-id service-revision-id status auditor-id event-date document]}]
  (sql/insert! db :ptv_service_audit
               {:org-id (->uuid org-id)
                :source-id source-id
                :service-id (some-> service-id ->uuid)
                :service-revision-id (some-> service-revision-id ->uuid)
                :status (or status "active")
                :auditor-id (some-> auditor-id ->uuid)
                :event-date (java.time.OffsetDateTime/parse event-date)
                :document document}
               (assoc jdbc/unqualified-snake-kebab-opts :return-keys true)))

(defn get-current-by-org
  "Latest audit row of every audited service lineage the org has."
  [db org-id]
  (sql/query db
             (hsql/format {:select [:*]
                           :from [:ptv_service_audit_current]
                           :where [:= :org_id (->uuid org-id)]})
             query-opts))

(defn get-current
  "Latest audit map of the (org-id, source-id) lineage, or nil."
  [db org-id source-id]
  (:document
    (first
      (sql/query db
                 (hsql/format {:select [:document]
                               :from [:ptv_service_audit_current]
                               :where [:and
                                       [:= :org_id (->uuid org-id)]
                                       [:= :source_id source-id]]})
                 query-opts))))

(defn get-history
  "All audits of the (org-id, source-id) lineage, newest first."
  [db org-id source-id]
  (sql/query db
             (hsql/format {:select [:*]
                           :from [:ptv_service_audit]
                           :where [:and
                                   [:= :org_id (->uuid org-id)]
                                   [:= :source_id source-id]]
                           :order-by [[:event_date :desc] [:created_at :desc]]})
             query-opts))
