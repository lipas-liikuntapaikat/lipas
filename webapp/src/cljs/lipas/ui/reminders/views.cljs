(ns lipas.ui.reminders.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.reminders.events :as events]
   [lipas.ui.reminders.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn add-button [{:keys [message]}]
  (let [tr (<== [:lipas.ui.subs/translator])]
    [mui/tooltip {:title (tr :reminders/title)}
     [mui/fab {:on-click #(==> [::events/add message]) :size "small"}
      [mui/icon "alarm_add"]]]))

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

     [mui/grid {:container true :spacing 16}

      [mui/grid {:item true :xs 12}

       (into
        [mui/grid {:container true :spacing 8}]
        (for [[k v] preselect-opts]
          [mui/grid {:item true}
           [mui/button
            {:variant  "outlined"
             :on-click #(==> [::events/select-option v])}
            (tr (keyword :reminders k))]]))]

      [mui/grid {:item true :xs 12}

       [mui/form-group

        [lui/date-picker
         {:type      "date"
          :label     (tr :reminders/select-date)
          :required  true
          :value     (-> form :data :date)
          :on-change #(==> [::events/set-date %])}]

        [lui/text-field
         {:label     (tr :reminders/message)
          :multiline true
          :required  true
          :value     (-> form :data :body :message)
          :on-change #(==> [::events/set-message %])}]]

       [mui/form-helper-text (tr :reminders/description)]]]]))
