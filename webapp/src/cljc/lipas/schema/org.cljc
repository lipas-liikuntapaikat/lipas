(ns lipas.schema.org
  (:require [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sites]
            [malli.util :as mu]))

;; TODO: How to ensure values are set when needed?
(def ptv-data
  "Schema for PTV integration configuration"
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
    [:maybe :boolean]]])

(def org-id :uuid)

(def org
  [:map
   [:id org-id]
   [:name [:string {:min 1 :max 128}]]
   [:data [:map {:optional true}
           [:primary-contact {:optional true}
            [:map
             [:phone {:optional true} [:maybe sites/phone-number]]
             [:email {:optional true} [:maybe sites/email]]
             [:website {:optional true} [:maybe sites/www]]
             [:reservations-link {:optional true} [:maybe sites/reservations-link]]]]]]
   [:ptv-data ptv-data]])

(def new-org
  (mu/dissoc org :id))

(def user-updates
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
       [:role [:enum "org-admin" "org-user"]]]]]]])

 ;; Schema for form validation (includes ID)
(def org-form-validation
  org)

;; Schema for API updates (no ID required since it's in URL)
(def org-update
  (mu/dissoc org :id))

 ;; Schema for PTV config updates (stricter validation for API endpoint)
(def ptv-config-update
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
   [:sync-enabled :boolean]])
