(ns lipas-ui.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.History)
  (:require [secretary.core :as secretary]
            [goog.events :as gevents]
            [goog.history.EventType :as EventType]
            [re-frame.core :as re-frame]
            [lipas-ui.events :as events]))

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
    (re-frame/dispatch [::events/set-active-panel :home-panel]))

  (defroute "/liikuntapaikat" []
    (re-frame/dispatch [::events/set-active-panel :sports-panel]))

  (defroute "/jaahalliportaali" []
    (re-frame/dispatch [::events/set-active-panel :ice-panel]))

  (defroute "/uimahalliportaali" []
    (re-frame/dispatch [::events/set-active-panel :swim-panel]))

  (defroute "/avoin-data" []
    (re-frame/dispatch [::events/set-active-panel :open-data-panel]))

  (defroute "/ohjeet" []
    (re-frame/dispatch [::events/set-active-panel :help-panel]))

  (defroute "/kirjaudu" []
    (re-frame/dispatch [::events/set-active-panel :login-panel]))

  (defroute "/rekisteroidy" []
    (re-frame/dispatch [::events/set-active-panel :register-panel]))

  (defroute "/profiili" []
    (re-frame/dispatch [::events/set-active-panel :user-panel]))

  ;; --------------------
  (hook-browser-navigation!))
