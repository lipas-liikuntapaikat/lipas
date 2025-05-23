(ns lipas.ui.swimming-pools.routes
  (:require [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.views :as views]
            [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["uimahallit"
   {:name   :lipas.ui.routes/swimming-pools
    :tr-key :swim/headline
    :view   views/main
    :controllers
    [{:start
      (fn [& params]
        (==> [::events/init]))}]}

   [""
    {:name :lipas.ui.routes.swimming-pools/front-page
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 0]))}]}]

   ["/ilmoita-tiedot"
    {:name :lipas.ui.routes.swimming-pools/report-consumption
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 1]))}]}]

   ["/hallit"
    {:name :lipas.ui.routes.swimming-pools/list
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 2]))}]}

    [""
     {:name :lipas.ui.routes.swimming-pools/list-view}]

    ["/:lipas-id"
     {:name       :lipas.ui.routes.swimming-pools/details-view
      :parameters {:path {:lipas-id int?}}
      :controllers
      [{:identity
        (fn [match]
          (-> match :parameters :path))
        :start
        (fn [{:keys [lipas-id]}]
          (==> [::events/display-site {:lipas-id lipas-id}]))}]}]]

   ["/hallien-vertailu"
    {:name :lipas.ui.routes.swimming-pools/visualizations
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 3]))}]}]

   ["/energia-info"
    {:name :lipas.ui.routes.swimming-pools/energy-info
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 4]))}]}]

   ["/yhteystiedot"
    {:name :lipas.ui.routes.swimming-pools/reports
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 5]))}]}]])
