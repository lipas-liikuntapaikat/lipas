(ns lipas.ui.components.notifications
  (:require ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Snackbar$default" :as Snackbar]
            ["@mui/material/SnackbarContent$default" :as SnackbarContent]
            [reagent.core :as r]))

(defn notification [{:keys [notification on-close]}]
  [:> Snackbar
   {:key                (gensym)
    :auto-hide-duration 5000
    :open               true
    :anchor-origin      {:vertical "top" :horizontal "right"}
    :on-close           on-close}
   [:> SnackbarContent
    {:style   {:background-color
               (if (:success? notification)
                 "#43a047"
                 "#d32f2f")}
     :message (:message notification)
     :action  (r/as-element
                [:> IconButton
                 {:key      "close"
                  :on-click on-close
                  :style    {:color "white"}}
                 (if (:success? notification)
                   [:> Icon "done"]
                   [:> Icon "warning"])])}]])
