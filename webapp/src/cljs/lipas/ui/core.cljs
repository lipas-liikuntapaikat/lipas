(ns lipas.ui.core
  (:require
   [day8.re-frame.http-fx]
   [district0x.re-frame.google-analytics-fx]
   [lipas.ui.config :as config]
   [lipas.ui.effects]
   [lipas.ui.events :as events]
   [lipas.ui.interceptors]
   [lipas.ui.local-storage]
   [lipas.ui.mui :as mui]
   [lipas.ui.routes :as routes]
   [lipas.ui.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [lipas.ui.views :as views]
   [re-frame.core :as re-frame]
   [reagent.dom :as reagent-dom]
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
    ;; Google Analytics
    (js/ga
     (fn []
       (js/ga "create" tracking-code domain)
       (js/ga "send" "pageview")))))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (==> [::events/set-backend-url dev-backend-url])
    (println "dev mode, backend-url:" dev-backend-url)
    ))

(defn qa-setup []
  (when-not (utils/prod?)
    (==> [::events/show-test-version-disclaimer])))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (routes/init!)
  (reagent-dom/render
   [:> ((mui/with-width*) (reagent/reactify-component views/main-panel))]
   (.getElementById js/document "app")))

(defn init []
  (track!)
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (qa-setup)
  (mount-root))
