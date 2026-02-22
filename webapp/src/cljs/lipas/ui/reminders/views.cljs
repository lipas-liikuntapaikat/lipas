(ns lipas.ui.reminders.views
  (:require [lipas.ui.components :as lui]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/FormHelperText$default" :as FormHelperText]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Tooltip$default" :as Tooltip]
            [lipas.ui.reminders.events :as events]
            [lipas.ui.reminders.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn add-button [{:keys [message]}]
  (let [tr (<== [:lipas.ui.subs/translator])]
    [:> Tooltip {:title (tr :reminders/title)}
     [:> Fab {:on-click #(==> [::events/add message]) :size "small"}
      [:> Icon "alarm_add"]]]))

(def preselect-opts
  [[:tomorrow         (utils/now+ (* 24 60 60 1000))]
   [:in-a-week        (utils/now+ (* 7 24 60 60 1000))]
   [:after-one-month  (utils/now+ (* 30 24 60 60 1000))]
   [:after-six-months (utils/now+ (* 180 24 60 60 1000))]
   [:in-a-year        (utils/now+ (* 365 24 60 60 1000))]])

(defn dialog []
  (let [tr     (<== [:lipas.ui.subs/translator])
        open?  (<== [::subs/dialog-open?])
        form   (<== [::subs/form])
        toggle (fn [] (==> [::events/toggle-dialog]))]

    [lui/dialog
     {:open?         open?
      :title         (tr :reminders/title)
      :on-close      toggle
      :save-enabled? (:valid? form)
      :on-save       #(==> [::events/save (:data form)])
      :save-label    (tr :actions/save)
      :cancel-label  (tr :actions/close)}

     [:> Grid {:container true :spacing 2}

      [:> Grid {:item true :xs 12}

       (into
         [:> Grid {:container true :spacing 1}]
         (for [[k v] preselect-opts]
           [:> Grid {:item true}
            [:> Button
             {:variant  "outlined"
              :on-click #(==> [::events/select-option v])}
             (tr (keyword :reminders k))]]))]

      [:> Grid {:item true :xs 12}

       [:> FormGroup

        [:> Grid {:container true :spacing 2}

         [:> Grid {:item true :xs 12}
          [lui/date-picker
           {:type      "date"
            :fullWidth true
            :label     (tr :reminders/select-date)
            :required  true
            :value     (-> form :data :date)
            :on-change #(==> [::events/set-date %])}]]

         [:> Grid {:item true :xs 12}
          [lui/text-field
           {:label     (tr :reminders/message)
            :multiline true
            :fullWidth true
            :required  true
            :value     (-> form :data :body :message)
            :on-change #(==> [::events/set-message %])}]]]]

       [:> FormHelperText (tr :reminders/description)]]]]))
