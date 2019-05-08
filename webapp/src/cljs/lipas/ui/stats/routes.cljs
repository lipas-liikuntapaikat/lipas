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
         (==> [::events/select-tab "sport"])
         (==> [:lipas.ui.stats.sport.events/create-report]))}]}]

   ["/liikuntapaikat"
    {:name :lipas.ui.routes.stats/sport
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "sport"])
         (==> [:lipas.ui.stats.sport.events/create-report]))}]}]

   ["/rakennusvuodet"
    {:name :lipas.ui.routes.stats/age-structure
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "age-structure"])
         (==> [:lipas.ui.stats.age-structure.events/create-report]))}]}]

   ["/kunta"
    {:name :lipas.ui.routes.stats/city
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "city"])
         (==> [:lipas.ui.stats.city.events/create-report]))}]}]

   ["/talous"
    {:name :lipas.ui.routes.stats/finance
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/select-tab "finance"])
         (==> [:lipas.ui.stats.finance.events/create-report]))}]}]])
