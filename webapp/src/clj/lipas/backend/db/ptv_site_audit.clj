(ns lipas.backend.db.ptv-site-audit
  "Accessors for the append-only ptv_site_audit table holding DVV's
   katselmointi verdicts on a sports site's PTV texts. Logical identity is
   the lipas_id; ptv_site_audit_current shows the latest audit per site.
   `document` is the audit map the UI and lipas.data.ptv work with
   (per-field :status/:feedback/:audited-content + :timestamp/:auditor-id);
   `site_revision_id` records which sports_site revision the verdicts were
   given on."
  (:require [honey.sql :as hsql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]))

(defn- ->uuid [x]
  (if (string? x) (parse-uuid x) x))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn insert-audit!
  "Appends an audit for the site. `event-date` is an ISO-8601 string
   (lipas.utils/timestamp), parsed to OffsetDateTime because PGJDBC doesn't
   coerce varchar->timestamptz on insert."
  [db {:keys [lipas-id site-revision-id status auditor-id event-date document]}]
  (sql/insert! db :ptv_site_audit
               {:lipas-id lipas-id
                :site-revision-id (some-> site-revision-id ->uuid)
                :status (or status "active")
                :auditor-id (some-> auditor-id ->uuid)
                :event-date (java.time.OffsetDateTime/parse event-date)
                :document document}
               (assoc jdbc/unqualified-snake-kebab-opts :return-keys true)))

(defn get-current-by-lipas-ids
  "Map of lipas-id -> latest audit map for the given sites (sites without
   an audit are absent)."
  [db lipas-ids]
  (if (empty? lipas-ids)
    {}
    (into {}
          (map (juxt :lipas-id :document))
          (sql/query db
                     (hsql/format {:select [:lipas_id :document]
                                   :from [:ptv_site_audit_current]
                                   :where [:in :lipas_id (vec lipas-ids)]})
                     query-opts))))

(defn get-all-current
  "Map of lipas-id -> latest audit map for every audited site (one small
   query; audited sites are a sample, hundreds at most). Resolved once per
   index batch, see lipas.backend.core/index-context."
  [db]
  (into {}
        (map (juxt :lipas-id :document))
        (sql/query db
                   (hsql/format {:select [:lipas_id :document]
                                 :from [:ptv_site_audit_current]})
                   query-opts)))

(defn get-current
  "Latest audit map of the site, or nil."
  [db lipas-id]
  (get (get-current-by-lipas-ids db [lipas-id]) lipas-id))

(defn get-history
  "All audits of the site, newest first."
  [db lipas-id]
  (sql/query db
             (hsql/format {:select [:*]
                           :from [:ptv_site_audit]
                           :where [:= :lipas_id lipas-id]
                           :order-by [[:event_date :desc] [:created_at :desc]]})
             query-opts))
