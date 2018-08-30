(ns lipas.ui.core
  (:require [cljsjs.babel-polyfill]
            [cljsjs.google-analytics]
            [day8.re-frame.http-fx]
            [district0x.re-frame.google-analytics-fx]
            [lipas.ui.local-storage]
            [lipas.ui.config :as config]
            [lipas.ui.events :as events]
            [lipas.ui.routes :as routes]
            [lipas.ui.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [lipas.ui.views :as views]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))

(def dev-backend-url "http://localhost:8091/api")

(def tracking-code (if (utils/prod?)
                     "UA-123952697-1"
                     "UA-123820613-1"))

(defn- resolve-cookie-domain []
  (if (utils/prod?)
    "lipas.fi"
    "auto"))

(defn track! []
  (let [domain (resolve-cookie-domain)]
    (js/ga
     (fn []
       (js/ga "create" tracking-code domain)
       (js/ga "send" "pageview")))))

(defn dev-setup []
  (when config/debug?
    (let [tr (<== [::subs/translator])]
      (enable-console-print!)
      (==> [::events/set-backend-url dev-backend-url])
      (==> [::events/set-active-disclaimer (tr :disclaimer/test-version)])
      (println "dev mode, backend-url:" dev-backend-url))))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (track!)
  (routes/app-routes)
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))
