(ns lipas.ui.core
  (:require [day8.re-frame.http-fx]
            [lipas.ui.local-storage]
            [lipas.ui.analytics :as analytics]
            [lipas.ui.config :as config]
            [lipas.ui.events :as events]
            [lipas.ui.routes :as routes]
            [lipas.ui.views :as views]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))

(def dev-backend-url "http://localhost:8091/api")

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
  (routes/app-routes)
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root)
  (analytics/track!))
