(ns lipas.ui.map.routes
  (:require [lipas.ui.map.views :as views]
            [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["liikuntapaikat"
   {:name      :lipas.ui.routes/map
    :tr-key    :sport/headline
    :view      views/main
    :hide-nav? true}

   [""
    {:name :lipas.ui.routes.map/map
     :controllers
     [{:start
       (fn [_]
         (==> [:lipas.ui.help.events/get-help-data])
         (==> [:lipas.ui.map.events/show-sports-site* nil]))}]}]

   ["/loi/:loi-id"
    {:name       :lipas.ui.routes.map/loi-view
     :parameters {:path [:map [:loi-id :string]]}
     :controllers
     [{:identity
       (fn [match]
         (-> match :parameters :path :loi-id))
       :start
       (fn [loi-id]
         (let [on-success [:lipas.ui.map.events/show-loi* loi-id]]
           (==> [:lipas.ui.loi.events/get loi-id on-success])))}]}]

   ["/:lipas-id"
    {:name       :lipas.ui.routes.map/details-view
     :parameters {:path [:map [:lipas-id :int]]}
     :controllers
     [{:identity
       (fn [match]
         (-> match :parameters :path :lipas-id))
       :start
       (fn [lipas-id]
         (let [fit-view?  false
               on-success
               [[:lipas.ui.map.events/show-sports-site* lipas-id]
                #_[:lipas.ui.accessibility.events/get-statements lipas-id]
                ;; Not sure if zooming is good UX-wise...
                [:lipas.ui.map.events/zoom-to-site lipas-id]
                ;; Trigger search to reveal also sites nearby, set
                ;; page size proper for map viewing
                [:lipas.ui.search.events/change-result-page-size 250 fit-view?]]]
           (==> [:lipas.ui.sports-sites.events/get lipas-id on-success])))}]}]])
