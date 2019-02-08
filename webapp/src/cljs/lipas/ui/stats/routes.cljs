(ns lipas.ui.stats.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]
   [lipas.ui.stats.events :as events]))

(def routes
  ["tilastot"
   {:name :lipas.ui.routes/stats
    :controllers
    [{:start
      (fn []
        (==> [:lipas.ui.events/set-active-panel :stats-panel]))}]}

   [""
    {:name :lipas.ui.routes.stats/front-page
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "sports-stats"])
         (==> [:lipas.ui.stats.events/create-sports-stats-report]))}]}]

   ["/liikuntapaikat"
    {:name :lipas.ui.routes.stats/sports-stats
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "sports-stats"])
         (==> [:lipas.ui.stats.events/create-sports-stats-report]))}]}]

   ["/rakennusvuodet"
    {:name :lipas.ui.routes.stats/age-structure-stats
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "age-structure-stats"])
         (==> [:lipas.ui.stats.events/create-age-structure-report]))}]}]

   ["/kunta"
    {:name :lipas.ui.routes.stats/city-stats
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "city-stats"])
         (==> [:lipas.ui.stats.events/create-finance-report]))}]}]])
