(ns lipas-ui.core
  (:require [day8.re-frame.http-fx]
            [lipas-ui.local-storage]
            [lipas-ui.config :as config]
            [lipas-ui.events :as events]
            [lipas-ui.routes :as routes]
            [lipas-ui.views :as views]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))


(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (routes/app-routes)
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))
