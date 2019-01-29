(ns lipas.ui.ice-stadiums.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]
   [lipas.ui.ice-stadiums.events :as events]))

(def routes
  ["jaahalliportaali"
   {:name :lipas.ui.routes/ice-stadiums
    :controllers
    [{:start
      (fn [& params]
        (==> [::events/init])
        (==> [:lipas.ui.events/set-active-panel :ice-stadiums-panel]))}]}

   [""
    {:name :lipas.ui.routes.ice-stadiums/front-page
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 0]))}]}]

   ["/ilmoita-tiedot"
    {:name :lipas.ui.routes.ice-stadiums/report-consumption
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 1]))}]}]

   ["/hallit"
    {:name :lipas.ui.routes.ice-stadiums/list
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 2]))}]}

    [""
     {:name :lipas.ui.routes.ice-stadiums/list-view}]

    ["/:lipas-id"
     {:name       :lipas.ui.routes.ice-stadiums/details-view
      :parameters {:path {:lipas-id int?}}
      :controllers
      [{:params (fn [match]
                  (-> match :parameters :path))
        :start
        (fn [{:keys [lipas-id]}]
          (==> [::events/display-site {:lipas-id lipas-id}]))}]}]]

   ["/hallien-vertailu"
    {:name :lipas.ui.routes.ice-stadiums/visualizations
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 3]))}]}]

   ["/energia-info"
    {:name :lipas.ui.routes.ice-stadiums/energy-info
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 4]))}]}]

   ["/yhteystiedot"
    {:name :lipas.ui.routes.ice-stadiums/reports
     :controllers
     [{:start
       (fn [& params]
         (==> [::events/set-active-tab 5]))}]}]])
