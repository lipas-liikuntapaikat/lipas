(ns lipas.ui.core
  (:require [cljsjs.babel-polyfill]
            [cljsjs.google-analytics]
            [district0x.re-frame.google-analytics-fx]
            [day8.re-frame.http-fx]
            [lipas.ui.local-storage]
            [lipas.ui.config :as config]
            [lipas.ui.events :as events]
            [lipas.ui.routes :as routes]
            [lipas.ui.utils :as utils]
            [lipas.ui.views :as views]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))

(def dev-backend-url "http://localhost:8091/api")

(def tracking-code (if (utils/prod?)
                     "UA-123952697-1"
                     "UA-123820613-1"))

(defn track! []
  (js/ga
   (fn []
     (js/ga "create" tracking-code "auto")
     (js/ga "send" "pageview"))))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (re-frame/dispatch [::events/set-backend-url dev-backend-url])
    (println "dev mode, backend-url:" dev-backend-url)))

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
