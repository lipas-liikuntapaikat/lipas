(ns lipas.ui.org.routes
  "Route definitions — org views live in the lazy :org module, loaded by
   lipas.ui.routes/on-navigate before controllers run. lipas.ui.org.events
   stays in the base module (login and the user profile need it)."
  (:require [lipas.ui.lazy :as lazy]
            [lipas.ui.org.events :as events]
            [re-frame.core :as rf]))

(def org-detail-routes
  ["organisaatio/:org-id"
   {:name :lipas.ui.routes/org
    :tr-key :lipas.org/headline
    :navbar-link :lipas.ui.routes/orgs
    :view lazy/org-detail-page
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
    :view lazy/orgs-list-page
    :controllers
    [{:start
      (fn [_]
        (rf/dispatch [::events/get-user-orgs]))}]}])

(def bulk-operations-routes
  ["organisaatiot/:org-id/massa-paivitys"
   {:name :lipas.ui.routes/org-bulk-operations
    :tr-key :lipas.org/bulk-operations
    :navbar-link :lipas.ui.routes/orgs
    :view lazy/org-bulk-operations-page
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
