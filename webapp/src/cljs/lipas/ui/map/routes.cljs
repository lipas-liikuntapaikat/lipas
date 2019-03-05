(ns lipas.ui.map.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["liikuntapaikat"
   {:name :lipas.ui.routes/map
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :map-panel]))}]}

   [""
    {:name :lipas.ui.routes.map/map
     :controllers
     [{:start
       (fn [_]
         (==> [:lipas.ui.map.events/show-sports-site* nil]))}]}]

   ["/:lipas-id"
    {:name       :lipas.ui.routes.map/details-view
     :parameters {:path {:lipas-id int?}}
     :controllers
     [{:params (fn [match]
                 (-> match :parameters :path))
       :start
       (fn [{:keys [lipas-id]}]
         (let [on-success [[:lipas.ui.map.events/show-sports-site* lipas-id]
                           ;; Not sure if zooming is good UX-wise...
                           [:lipas.ui.map.events/zoom-to-site lipas-id]]]
           (==> [:lipas.ui.sports-sites.events/get lipas-id on-success])))}]}]])
