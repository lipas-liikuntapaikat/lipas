(ns lipas.ui.core
  (:require
   [day8.re-frame.http-fx]
   [lipas.ui.config :as config]
   [lipas.ui.effects]
   [lipas.ui.events :as events]
   [lipas.ui.interceptors]
   [lipas.ui.local-storage]
   [lipas.ui.project-devtools :as project-devtools]
   [lipas.ui.routes :as routes]
   [lipas.ui.utils :refer [==>] :as utils]
   [lipas.ui.views :as views]
   [re-frame.core :as rf]
   [reagent.dom :as reagent-dom]))

(def dev-backend-url "http://localhost:8091/api")

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (==> [::events/set-backend-url dev-backend-url])
    (println "dev mode, backend-url:" dev-backend-url)))

(defn qa-setup []
  (when-not (utils/prod?)
    (==> [::events/show-test-version-disclaimer])))

(defn ^:dev/after-load mount-root []
  (rf/clear-subscription-cache!)
  (project-devtools/start!)
  (routes/init!)
  (reagent-dom/render
   [:f> views/main-panel]
   (.getElementById js/document "app")))

(defn init []
  (rf/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (qa-setup)
  (mount-root)

  ;; TODO remove after platform migration
  #_(when (utils/prod?)
    (re-frame/dispatch [::show-service-break-disclaimer])))

;; TODO remove after platform migration
#_(re-frame/reg-event-db
 ::show-service-break-disclaimer
 (fn [db _]
   (assoc db :active-disclaimer "Lipas-järjestelmän alustan päivitystöistä johtuva käyttökatko maanantaina 26.8. klo 18.00 – 20.00. Lipas-sovellus ja rajapinnat eivät ole tällöin käytettävissä.")))
