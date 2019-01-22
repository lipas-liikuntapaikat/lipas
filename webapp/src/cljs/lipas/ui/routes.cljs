(ns lipas.ui.routes
  (:require
   [clojure.string :as string]
   [lipas.ui.utils :refer [==>] :as utils]
   [reitit.coercion :as rc]
   [reitit.coercion.spec :as rss]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]))

(defn navigate-async! [url]
  (==> [:lipas.ui.events/navigate url]))

(def routes
  (rf/router
   ["/"

    [""

     {:name ::root
      :controllers
      [{:start
        (fn [& params]
          (->
           (case (utils/domain)
             "uimahallit.lipas.fi"     :lipas.ui.routes.swimming-pools/front-page
             "jaahallit.lipas.fi"      :lipas.ui.routes.ice-stadiums/front-page
             "liikuntapaikat.lipas.fi" :lipas.ui.routes/map
             :lipas.ui.routes/front-page)
           navigate-async!))}]}]

    ["etusivu"
     {:nam :lipas.ui.routes/front-page
      :controllers
      [{:start
        (fn []
          (==> [:lipas.ui.events/set-active-panel :front-page-panel]))}]}]

    ["admin"
     {:name :lipas.ui.routes/admin
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.events/set-active-panel :admin-panel]))}]}]

    ["liikuntapaikat"
     {:name :lipas.ui.routes/map
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.events/set-active-panel :map-panel]))}]}]

    ["kirjaudu"
     {:name :lipas.ui.routes/login
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.events/set-active-panel :login-panel]))}]}]

    ["passu-hukassa"
     {:name :lipas.ui.routes/reset-password
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.events/set-active-panel :reset-password-panel]))}]}]

    ["rekisteroidy"
     {:name :lipas.ui.routes/register
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.events/set-active-panel :register-panel]))}]}]

    ["profiili"
     {:name :lipas.ui.routes/user
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.events/set-active-panel :user-panel]))}]}]

    ["jaahalliportaali"
     {:name :lipas.ui.routes/ice-stadiums
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.ice-stadiums.events/init])
          (==> [:lipas.ui.events/set-active-panel :ice-stadiums-panel]))}]}

     [""
      {:name :lipas.ui.routes.ice-stadiums/front-page
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.ice-stadiums.events/set-active-tab 0]))}]}]

     ["/ilmoita-tiedot"
      {:name :lipas.ui.routes.ice-stadiums/report-consumption
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.ice-stadiums.events/set-active-tab 1]))}]}]

     ["/hallit"
      {:name :lipas.ui.routes.ice-stadiums/list
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.ice-stadiums.events/set-active-tab 2]))}]}

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
            (==> [:lipas.ui.ice-stadiums.events/display-site {:lipas-id lipas-id}]))}]}]]

     ["/hallien-vertailu"
      {:name :lipas.ui.routes.ice-stadiums/visualizatios
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.ice-stadiums.events/set-active-tab 3]))}]}]

     ["/energia-info"
      {:name :lipas.ui.routes.ice-stadiums/energy-info
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.ice-stadiums.events/set-active-tab 4]))}]}]

     ["/yhteystiedot"
      {:name :lipas.ui.routes.ice-stadiums/reports
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.ice-stadiums.events/set-active-tab 5]))}]}]]

    ["uimahalliportaali"
     {:name :lipas.ui.routes/swimming-pools
      :controllers
      [{:start
        (fn [& params]
          (==> [:lipas.ui.swimming-pools.events/init])
          (==> [:lipas.ui.events/set-active-panel :swimming-pools-panel]))}]}

     [""
      {:name :lipas.ui.routes.swimming-pools/front-page
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.swimming-pools.events/set-active-tab 0]))}]}]

     ["/ilmoita-tiedot"
      {:name :lipas.ui.routes.swimming-pools/report-consumption
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.swimming-pools.events/set-active-tab 1]))}]}]

     ["/hallit"
      {:name :lipas.ui.routes.swimming-pools/list
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.swimming-pools.events/set-active-tab 2]))}]}

      [""
       {:name :lipas.ui.routes.swimming-pools/list-view}]

      ["/:lipas-id"
       {:name       :lipas.ui.routes.swimming-pools/details-view
        :parameters {:path {:lipas-id int?}}
        :controllers
        [{:params (fn [match]
                    (-> match :parameters :path))
          :start
          (fn [{:keys [lipas-id]}]
            (==> [:lipas.ui.swimming-pools.events/display-site {:lipas-id lipas-id}]))}]}]]

     ["/hallien-vertailu"
      {:name :lipas.ui.routes.swimming-pools/visualizatios
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.swimming-pools.events/set-active-tab 3]))}]}]

     ["/energia-info"
      {:name :lipas.ui.routes.swimming-pools/energy-info
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.swimming-pools.events/set-active-tab 4]))}]}]

     ["/yhteystiedot"
      {:name :lipas.ui.routes.swimming-pools/reports
       :controllers
       [{:start
         (fn [& params]
           (==> [:lipas.ui.swimming-pools.events/set-active-tab 5]))}]}]]]

   {:data {:coercion rss/coercion}}))

(defn find-name [path]
  (let [path  (string/replace path #"/#" "")
        match (rf/match-by-path routes path)]
    (-> match :data :name)))

(defn navigate!
  ([path]
   (navigate! path []))
  ([path & args]
   (let [kw (cond
              (keyword? path) path
              (string? path) (find-name path))]
     (apply rfe/push-state (into [kw] (remove nil?) args)))))

(defonce match (atom nil))

(defn init! []
  (rfe/start!
   routes
   (fn [new-match]
     (swap! match (fn [old-match]
                    (if new-match
                      (assoc new-match :controllers
                             (rfc/apply-controllers
                              (:controllers old-match) new-match))))))
   {:use-fragment true}))
