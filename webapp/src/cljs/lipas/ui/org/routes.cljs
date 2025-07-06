(ns lipas.ui.org.routes
  (:require [lipas.ui.org.events :as events]
            [lipas.ui.org.views :as views]
            [re-frame.core :as rf]))

(def org-detail-routes
  ["organisaatio/:org-id"
   {:name :lipas.ui.routes/org
    :tr-key :lipas.org/headline
    :no-navbar-link? true
    :view #'views/org-view
    :parameters {:path [:map
                        [:org-id :string]]}
    :controllers
    [{:identity (fn [match]
                  (-> match :parameters :path :org-id))
      :start
      (fn [org-id]
        (rf/dispatch [::events/init-view org-id]))}]}])

(def org-list-routes
  ["organisaatiot"
   {:name :lipas.ui.routes/orgs
    :tr-key :lipas.admin/organizations
    :view #'views/orgs-list-view
    :controllers
    [{:start
      (fn [_]
        (rf/dispatch [::events/get-user-orgs]))}]}])

(def bulk-operations-routes
  ["organisaatio/:org-id/massa-paivitys"
   {:name :lipas.ui.routes/org-bulk-operations
    :tr-key :lipas.org/bulk-operations
    :no-navbar-link? true
    :view #'views/bulk-operations-view
    :parameters {:path [:map
                        [:org-id :string]]}
    :controllers
    [{:identity (fn [match]
                  (-> match :parameters :path :org-id))
      :start
      (fn [org-id]
        (rf/dispatch [::events/init-bulk-operations org-id]))}]}])

(def routes
  [""
   org-list-routes
   org-detail-routes
   bulk-operations-routes])
