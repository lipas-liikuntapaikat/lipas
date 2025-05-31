(ns lipas.schema.sports-sites.ptv
  "Schema definitions for PTV (Palvelutietovaranto) integration in sports sites.")

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
  [:enum "lipas-managed" "manual"])

(def audit-status-enum
  "Enum for PTV audit status values."
  [:enum "approved" "changes-requested"])

(def audit-field
  "Schema for individual audit field feedback."
  [:map
   {:closed true}
   [:status audit-status-enum]
   [:feedback [:string {:min 0 :max 1000}]]])

(def ptv-audit
  "Schema for PTV audit information including timestamp, auditor, and field-specific feedback."
  [:map
   {:closed true}
   [:timestamp [:string {:min 24 :max 30}]] ;; ISO-8601 format timestamps
   [:auditor-id :string]
   [:summary {:optional true} audit-field]
   [:description {:optional true} audit-field]])

(def ptv-meta
  "Schema for PTV metadata associated with sports sites."
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
   ;; [:languages [:vector :string]]

   [:summary (localized-string-schema {:max 150})]
   [:description (localized-string-schema {})]

   [:audit {:optional true} ptv-audit]])

(def create-ptv-service-location
  "Schema for creating PTV service locations."
  [:map
   {:closed true}
   [:org-id :string]
   [:lipas-id :int]
   [:ptv ptv-meta]])
