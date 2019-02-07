(ns lipas.ui.routes
  (:require
   [clojure.string :as string]
   [lipas.ui.admin.routes :as admin]
   [lipas.ui.forgot-password.routes :as forgot-password]
   [lipas.ui.front-page.routes :as front-page]
   [lipas.ui.ice-stadiums.routes :as ice-stadiums]
   [lipas.ui.login.routes :as login]
   [lipas.ui.map.routes :as lmap]
   [lipas.ui.register.routes :as register]
   [lipas.ui.stats.routes :as stats]
   [lipas.ui.swimming-pools.routes :as swimming-pools]
   [lipas.ui.user.routes :as user]
   [lipas.ui.utils :refer [==>] :as utils]
   [reitit.coercion :as rc]
   [reitit.coercion.spec :as rss]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]))

(defn navigate-async! [url]
  (==> [:lipas.ui.events/navigate url]))

(def root-route
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
         navigate-async!))}]}])

(def routes
  (rf/router
   ["/"
    root-route
    front-page/routes
    login/routes
    user/routes
    forgot-password/routes
    register/routes
    lmap/routes
    admin/routes
    stats/routes
    ice-stadiums/routes
    swimming-pools/routes]
   {:data {:coercion rss/coercion}}))

(defn match-by-path [path]
  (let [path (string/replace path #"/#" "")]
    (rf/match-by-path routes path)))

(defn navigate!
  ([path]
   (navigate! path nil))
  ([path & args]
   (if (and (string? path) (or (string/starts-with? path "http")
                               (string/starts-with? path "tel:")
                               (string/starts-with? path "mailto:")))
     ;; External link
     (set! (.-location js/window) path)

     ;; Internal link
     (let [match (when (string? path) (-> path match-by-path))
           kw    (cond
                   (keyword? path) path
                   (string? path)  (-> match :data :name))
           args  (conj args (-> match :parameters :path))]
       (apply rfe/push-state (into [kw] (remove nil?) args))))))

(defonce match (atom nil))

(defn init! []
  (rfe/start!
   routes
   (fn [new-match]
     (==> [:lipas.ui.events/navigated (:path new-match)])
     (swap! match (fn [old-match]
                    (if new-match
                      (assoc new-match :controllers
                             (rfc/apply-controllers
                              (:controllers old-match) new-match))))))
   {:use-fragment true}))
