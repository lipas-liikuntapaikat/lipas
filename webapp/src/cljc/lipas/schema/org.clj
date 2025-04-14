(ns lipas.schema.org
  (:require [lipas.schema.common :as common]
            [malli.util :as mu]))

(def ptv-data
  [:map
   [:ptv-org-id common/uuid]
   [:city-codes [:vector number?]]
   [:owners [:vector [:enum "city" "city-main-owner"]]]
   [:supported-languages [:vector [:enum "fi" "se" "en"]]]])

(def org-id common/uuid)

(def org
  [:map
   [:id org-id]
   [:name [:string {:min 1 :max 128}]]
   [:data [:map
           [:phone number?]]]
   [:ptv-data ptv-data]])

(def new-org
  (mu/dissoc org :id))

(def user-updates
  [:map
   [:changes
    [:map
     [:user-id :uuid]
     [:change [:enum "add" "remove"]]
     [:role [:enum "org-admin" "org-user"]]]]])
