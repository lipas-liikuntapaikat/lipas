(ns lipas.ui.stats.routes
  "Route definitions only — the stats views and event handlers live in
   the lazy :stats module. The :view loadable is loaded by
   lipas.ui.routes/on-navigate before controllers run, so the literal
   event keywords below resolve to registered handlers."
  (:require [lipas.ui.lazy :as lazy]
            [lipas.ui.utils :refer [==>]]))

(def routes
  ["tilastot"
   {:name   :lipas.ui.routes/stats
    :tr-key :stats/headline
    :view   lazy/stats-view}

   [""
    {:name :lipas.ui.routes.stats/front-page
     :controllers
     [{:start
       (fn [& _params]
         (==> [:lipas.ui.stats.events/select-tab "sport"])
         (==> [:lipas.ui.stats.sport.events/create-report]))}]}]

   ["/liikuntapaikat"
    {:name :lipas.ui.routes.stats/sport
     :controllers
     [{:start
       (fn [& _params]
         (==> [:lipas.ui.stats.events/select-tab "sport"])
         (==> [:lipas.ui.stats.sport.events/create-report]))}]}]

   ["/rakennusvuodet"
    {:name :lipas.ui.routes.stats/age-structure
     :controllers
     [{:start
       (fn [& _params]
         (==> [:lipas.ui.stats.events/select-tab "age-structure"])
         (==> [:lipas.ui.stats.age-structure.events/create-report]))}]}]

   ["/kunta"
    {:name :lipas.ui.routes.stats/city
     :controllers
     [{:start
       (fn [& _params]
         (==> [:lipas.ui.stats.events/select-tab "city"])
         (==> [:lipas.ui.stats.city.events/create-report]))}]}]

   ["/talous"
    {:name :lipas.ui.routes.stats/finance
     :controllers
     [{:start
       (fn [& _params]
         (==> [:lipas.ui.stats.events/select-tab "finance"])
         (==> [:lipas.ui.stats.finance.events/create-report]))}]}]

   ["/avustukset"
    {:name :lipas.ui.routes.stats/subsidies
     :controllers
     [{:start
       (fn [& _params]
         (==> [:lipas.ui.stats.events/select-tab "subsidies"])
         (==> [:lipas.ui.stats.subsidies.events/create-report]))}]}]])
