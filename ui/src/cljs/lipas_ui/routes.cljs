(ns lipas-ui.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.History)
  (:require [secretary.core :as secretary]
            [goog.events :as gevents]
            [goog.history.EventType :as EventType]
            [re-frame.core :as re-frame]
            [lipas-ui.events :as events]
            ))

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

  (defroute "/rajapinnat" []
    (re-frame/dispatch [::events/set-active-panel :interfaces-panel]))

  (defroute "/ohjeet" []
    (re-frame/dispatch [::events/set-active-panel :help-panel]))

  ;; --------------------
  (hook-browser-navigation!))
