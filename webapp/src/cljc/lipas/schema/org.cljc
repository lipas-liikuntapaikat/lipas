(ns lipas.schema.org
  (:require [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sites]
            [malli.util :as mu]))

;; TODO: How to ensure values are set when needed?
(def ptv-data
  [:map
   [:ptv-org-id {:optional true} [:maybe common/uuid]]
   [:city-codes {:optional true} [:vector number?]]
   [:owners {:optional true} [:vector [:enum "city" "city-main-owner"]]]
   [:supported-languages {:optional true} [:vector [:enum "fi" "se" "en"]]]])

(def org-id :uuid)

(def org
  [:map
   [:id org-id]
   [:name [:string {:min 1 :max 128}]]
   [:data [:map
           [:primary-contact [:map
                              [:phone [:maybe sites/phone-number]]
                              [:email [:maybe sites/email]]
                              [:website [:maybe sites/www]]
                              [:reservations-link [:maybe sites/reservations-link]]]]]]
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
