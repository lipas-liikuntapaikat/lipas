(ns lipas.schema.ptv
  "Schemas for PTV Service persistence (ptv_service table) and Service audits.
   Audit field schemas are shared with sports-site audits and live in
   lipas.schema.sports-sites.ptv."
  (:require [lipas.schema.sports-sites.ptv :as ss-ptv]
            [malli.core :as m]))

(def save-service-audit-body
  "Request body for /actions/save-ptv-service-audit. :org-id is the LIPAS
   org uuid (same convention as /actions/send-audit-notification); the
   backend adds :timestamp and :auditor-id to the audit."
  (m/schema
   [:map
    {:closed true}
    [:org-id :uuid]
    [:service-id :uuid]
    [:source-id {:optional true} [:maybe :string]]
    [:audit #'ss-ptv/audit-data]]))

(def fetch-service-audits-body
  "Request body for /actions/fetch-ptv-service-audits."
  (m/schema
   [:map
    {:closed true}
    [:org-id :uuid]]))

(def service-document
  "Persisted ptv_service document. Deliberately open — the document evolves
   (e.g. :last-sync, future draft fields) without schema migrations."
  (m/schema
   [:map
    [:source-id :string]
    [:service-id {:optional true} [:maybe :string]]
    [:ptv-org-id {:optional true} [:maybe :string]]
    [:name {:optional true} (ss-ptv/localized-string-schema nil)]
    [:summary {:optional true} (ss-ptv/localized-string-schema nil)]
    [:description {:optional true} (ss-ptv/localized-string-schema nil)]
    [:user-instruction {:optional true} (ss-ptv/localized-string-schema nil)]
    [:languages {:optional true} [:vector :string]]
    [:publishing-status {:optional true} [:maybe :string]]
    [:sub-category-id {:optional true} [:maybe :int]]
    [:audit {:optional true} #'ss-ptv/ptv-audit]]))
