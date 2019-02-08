(ns lipas.ui.components.notifications
  (:require
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn notification [{:keys [notification on-close]}]
  [mui/snackbar
   {:key                (gensym)
    :auto-hide-duration 5000
    :open               true
    :anchor-origin      {:vertical "top" :horizontal "right"}
    :on-close           on-close}
   [mui/snackbar-content
    {:message (:message notification)
     :action  (r/as-element
               [mui/icon-button
                {:key      "close"
                 :on-click on-close
                 :color    "secondary"}
                (if (:success? notification)
                  [mui/icon "done"]
                  [mui/icon "warning"])])}]])
