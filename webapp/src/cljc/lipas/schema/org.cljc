(ns lipas.schema.org
  (:require [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sites]
            [malli.core :as m]
            [malli.util :as mu]))

;; TODO: How to ensure values are set when needed?
(def ptv-data
  "Schema for PTV integration configuration"
  (m/schema
    [:map
     [:org-id {:optional true
               :description "PTV organization UUID"}
      [:maybe common/uuid]]
     [:prod-org-id {:optional true
                    :description "Special UUID used in production for apiUserOrganisation"}
      [:maybe common/uuid]]
     [:test-credentials {:optional true
                         :description "API credentials for test environment"}
      [:maybe [:map
               [:username [:string {:min 1}]]
               [:password [:string {:min 1}]]]]]
     [:city-codes {:optional true
                   :description "Municipality codes for filtering eligible sites"}
      [:vector [:int {:min 1 :max 999}]]]
     [:owners {:optional true
               :description "Ownership types for filtering"}
      [:vector [:enum "city" "city-main-owner" "municipal-consortium"
                "state" "private" "organization" "other"]]]
     [:supported-languages {:optional true
                            :description "Languages supported by the org in PTV"}
      [:vector [:enum "fi" "se" "en"]]]
     [:sync-enabled {:optional true
                     :description "Global flag to enable/disable PTV sync for the organization"}
      [:maybe :boolean]]]))

(def org-id (m/schema :uuid))

;; Standalone schema for org name form validation
(def org-name (m/schema [:string {:min 1 :max 128}]))

(def org-type
  [:enum "city" "municipal-consortium" "state" "private" "sports-federation" "other"])

(def role-templates
  "The per-org role-template catalog (the ceiling). Each entry maps a name to a
  label + a vector of role-specs (the existing role vocabulary)."
  [:map-of :keyword
   [:map
    [:label {:optional true} :string]
    [:roles [:vector [:map [:role :string]]]]]])

(def ownership
  [:map {:optional true}
   [:city-codes {:optional true} [:vector [:int {:min 1 :max 999}]]]
   [:owners {:optional true} [:vector :string]]])

(def members
  [:vector
   [:map
    [:user-id [:or :uuid :string]]
    [:org-role [:enum "admin" "member"]]
    [:templates {:optional true} [:vector :string]]]])

(def org
  (m/schema
    [:map
     [:id org-id]
     [:name [:string {:min 1 :max 128}]]
     [:data {:optional true}
      [:map
       [:primary-contact {:optional true}
        [:map
         [:phone {:optional true} [:maybe sites/phone-number]]
         [:email {:optional true} [:maybe sites/email]]
         [:website {:optional true} [:maybe sites/www]]
         [:reservations-link {:optional true} [:maybe sites/reservations-link]]]]]]
     [:ptv-data {:optional true} [:maybe ptv-data]]
     ;; --- org-management (opt-in; absent for legacy orgs) ---
     [:type {:optional true} org-type]
     [:role-templates {:optional true} role-templates]
     [:ownership {:optional true} ownership]
     [:members {:optional true} members]]))

(def new-org
  (m/schema (mu/dissoc org :id)))

(def user-updates
  (m/schema
    [:map
     [:changes
      [:vector
       [:or
       ;; Case 1: Has user-id but not email (existing admin workflow)
        [:map {:closed true}
         [:user-id :uuid]
         [:change [:enum "add" "remove"]]
         [:role [:enum "org-admin" "org-user"]]]
       ;; Case 2: Has email but not user-id (new email-based workflow)
        [:map {:closed true}
         [:email :string]
         [:change [:enum "add" "remove"]]
         [:role [:enum "org-admin" "org-user"]]]]]]]))

;; Schema for form validation (includes ID)
(def org-form-validation
  (m/schema org))

;; Schema for API updates (no ID required since it's in URL)
(def org-update
  (m/schema (mu/dissoc org :id)))

 ;; Schema for PTV config updates (stricter validation for API endpoint)
(def ptv-config-update
  (m/schema
    [:map
     [:org-id common/uuid]
     [:prod-org-id {:optional true} common/uuid]
     [:test-credentials {:optional true}
      [:map
       [:username [:string {:min 1}]]
       [:password [:string {:min 1}]]]]
     [:city-codes [:sequential [:int {:min 1 :max 999}]]]
     [:owners [:sequential [:enum "city" "city-main-owner" "municipal-consortium"
                            "state" "private" "organization" "other"]]]
     [:supported-languages [:sequential [:enum "fi" "se" "en"]]]
     [:sync-enabled :boolean]]))
