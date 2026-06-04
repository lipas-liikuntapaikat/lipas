(ns lipas.schema.sports-sites.ptv
  "Schema definitions for PTV (Palvelutietovaranto) integration in sports sites."
  (:require [lipas.data.ptv :as ptv-data]
            [malli.core :as m]))

(defn localized-string-schema
  "Creates schema for localized strings with optional constraints."
  [string-props]
  [:map
   {:closed true}
   [:fi {:optional true} [:string string-props]]
   [:se {:optional true} [:string string-props]]
   [:en {:optional true} [:string string-props]]])

(def integration-enum
  "Enum for PTV integration management types."
  (m/schema [:enum "lipas-managed" "manual"]))

(def audit-status-enum
  "Enum for PTV audit status values."
  (m/schema [:enum "approved" "changes-requested"]))

(def audit-field
  "Schema for individual audit field feedback."
  (m/schema
   [:map
    {:closed true}
    [:status audit-status-enum]
    [:feedback [:string {:min 0 :max 1000}]]]))

(def ptv-audit
  "Schema for PTV audit information including timestamp, auditor, and field-specific feedback."
  (m/schema
   [:map
    {:closed true}
    [:timestamp [:string {:min 24 :max 30}]] ;; ISO-8601 format timestamps
    [:auditor-id :string]
    [:summary {:optional true} audit-field]
    [:description {:optional true} audit-field]]))

(def audit-data
  "Schema for audit data sent from frontend (before backend adds timestamp/auditor-id)."
  (m/schema
   [:map
    {:closed true}
    [:summary {:optional true} audit-field]
    [:description {:optional true} audit-field]]))

(def ptv-meta
  "Schema for PTV metadata associated with sports sites."
  (m/schema
   [:map
    {:closed true}
    [:org-id :string]
    [:sync-enabled :boolean]
    [:delete-existing {:optional true} :boolean]

    ;; These options aren't used now:
    ;; TODO: Remove
    [:service-channel-integration
     {:optional true}
     integration-enum]
    [:service-integration
     {:optional true}
     integration-enum]

    [:service-channel-ids [:vector :string]]
    [:service-ids [:vector :string]]
    [:languages {:optional true} [:vector :string]]

    ;; Per-language character limits enforced by PTV (see lipas.data.ptv).
    ;; The :error/message values are localization keys resolved via `tr`
    ;; in the UI (e.g. the sync-button why-disabled tooltip).
    [:summary (localized-string-schema {:max ptv-data/max-summary-length
                                        :error/message :ptv/error-summary-too-long})]
    [:description (localized-string-schema {:max ptv-data/max-description-length
                                            :error/message :ptv/error-description-too-long})]
    [:user-instruction {:optional true} (localized-string-schema {:max ptv-data/max-user-instruction-length
                                                                  :error/message :ptv/error-user-instruction-too-long})]

    [:audit {:optional true} ptv-audit]]))

(def create-ptv-service-location
  "Schema for creating PTV service locations.
   `:archive?` requests an explicit archive (publishingStatus \"Deleted\")
   instead of a publish/update — used by the explicit \"Archive in PTV\"
   actions in both the wizard and the sports-site PTV tab."
  (m/schema
   [:map
    {:closed true}
    [:org-id :string]
    [:lipas-id :int]
    [:archive? {:optional true} :boolean]
    [:ptv ptv-meta]]))
