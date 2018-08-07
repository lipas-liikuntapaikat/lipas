(ns lipas.ui.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.History)
  (:require [goog.events :as gevents]
            [goog.history.EventType :as EventType]
            [lipas.ui.utils :refer [==>]]
            [secretary.core :as secretary]))

(defn navigate! [path]
  (set! (.-location js/window) path))

(defn hook-browser-navigation! []
  (doto (History.)
    (gevents/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  ;; --------------------
  ;; define routes here
  (defroute "/" []
    (==> [:lipas.ui.events/set-active-panel :home-panel]))

  (defroute "/liikuntapaikat" []
    (==> [:lipas.ui.events/set-active-panel :sports-panel]))

  (defroute "/jaahalliportaali" []
    (==> [:lipas.ui.events/set-active-panel :ice-panel])
    (==> [:lipas.ui.ice-stadiums.events/display-site nil]))

  (defroute "/jaahalliportaali/:lipas-id" {lipas-id :lipas-id}
    (let [lipas-id (js/parseInt lipas-id)]
      (==> [:lipas.ui.events/set-active-panel :ice-panel])
      (==> [:lipas.ui.ice-stadiums.events/display-site {:lipas-id lipas-id}])))

  (defroute "/uimahalliportaali" []
    (==> [:lipas.ui.events/set-active-panel :swim-panel])
    (==> [:lipas.ui.swimming-pools.events/display-site nil]))

  (defroute "/uimahalliportaali/:lipas-id" {lipas-id :lipas-id}
    (let [lipas-id (js/parseInt lipas-id)]
      (==> [:lipas.ui.events/set-active-panel :swim-panel])
      (==> [:lipas.ui.swimming-pools.events/display-site {:lipas-id lipas-id}])))

  (defroute "/kirjaudu" []
    (==> [:lipas.ui.events/set-active-panel :login-panel]))

  (defroute "/passu-hukassa" []
    (==> [:lipas.ui.events/set-active-panel :reset-password-panel]))

  (defroute "/rekisteroidy" []
    (==> [:lipas.ui.events/set-active-panel :register-panel]))

  (defroute "/profiili" []
    (==> [:lipas.ui.events/set-active-panel :user-panel]))

  ;; --------------------
  (hook-browser-navigation!))
