(ns lipas.ui.routes
  (:require [clojure.string :as string]
            [lipas.ui.admin.routes :as admin]
            [lipas.ui.forgot-password.routes :as forgot-password]
            [lipas.ui.front-page.routes :as front-page]
            [lipas.ui.lazy :as lazy]
            [lipas.ui.login.routes :as login]
            [lipas.ui.map.routes :as lmap]
            [lipas.ui.register.routes :as register]
            [lipas.ui.stats.routes :as stats]
            [lipas.ui.user.routes :as user]
            [lipas.ui.org.routes :as org]
            [lipas.ui.utils :refer [==>] :as utils]
            [reitit.coercion.malli :as rcm]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(defn navigate-async! [url]
  (==> [:lipas.ui.events/navigate url]))

(def root-route
  [""
   {:name ::root
    :controllers
    [{:start
      (fn [& params]
        (navigate-async!
          (if (= "liikuntapaikat.lipas.fi" (utils/domain))
            :lipas.ui.routes.map/map
            :lipas.ui.routes/front-page)))}]}])

(def routes
  (rf/router
    ["/"
     root-route
     front-page/routes
     login/routes
     user/routes
     org/routes
     forgot-password/routes
     register/routes
     lmap/routes
     admin/routes
     stats/routes]
    {:data {:coercion rcm/coercion}}))

(defn match-by-path [path]
  (let [path (string/replace path #"/#" "")]
    (rf/match-by-path routes path)))

(defn navigate!
  ([path]
   (navigate! path nil))
  ([path & args]
   (cond (and (string? path) (or (string/starts-with? path "http")
                                 (string/starts-with? path "tel:")
                                 (string/starts-with? path "mailto:")))
         ;; External link
         (set! (.-location js/window) path)

         ;; Internal link
         :else
         (let [match (when (string? path) (-> path match-by-path))
               kw    (cond
                       (keyword? path) path
                       (string? path)  (-> match :data :name))
               args  (conj args (-> match :parameters :path))]
           (apply rfe/push-state (into [kw] (remove nil?) args))))))

;; Monotonic navigation token. A lazy-module load completing after the
;; user has already navigated elsewhere must not dispatch its (now stale)
;; ::navigated — that would overwrite current-route with an older match
;; and desync the rendered view from the URL. Every navigation bumps the
;; token; a load callback only fires if its token is still the latest.
(defonce nav-token* (atom 0))

(defn on-navigate [new-match]
  (let [current-path (utils/current-path)
        token (swap! nav-token* inc)]
    (cond
      ;; Fix deprecated url with hash
      (string/starts-with? current-path "/#")
      (set! js/window.location.href (-> (utils/current-path) (subs 2)))

      :else
      ;; Lazy routes carry a shadow.lazy loadable in :view. Load its
      ;; module before ::navigated so the route's controllers dispatch
      ;; into registered event handlers. While loading, the previous
      ;; view stays rendered.
      (let [view (-> new-match :data :view)]
        (if (and (lazy/loadable? view) (not (lazy/ready? view)))
          (lazy/load! view #(when (= token @nav-token*)
                              (==> [:lipas.ui.events/navigated new-match])))
          (==> [:lipas.ui.events/navigated new-match]))))))

(defn init! []
  (rfe/start!
    routes
    on-navigate
    {:use-fragment false}))

(comment
  (require '[reitit.core :as reitit])
  (reitit/route-names routes))
