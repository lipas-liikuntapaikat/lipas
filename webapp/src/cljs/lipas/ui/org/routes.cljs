(ns lipas.ui.org.routes
  (:require [lipas.ui.org.events :as events]
            [lipas.ui.org.views :as views]
            [re-frame.core :as rf]))

(def routes
  ["organisaatio/:org-id"
   {:name   :lipas.ui.routes/org
    :tr-key :org/headline
    :no-navbar-link? true
    :view   #'views/org-view
    :parameters {:path [:map
                        [:org-id :string]]}
    :controllers
    [{:identity (fn [match]
                  (-> match :parameters :path :org-id))
      :start
      (fn [org-id]
        (rf/dispatch [::events/init-view org-id]))}]}])
