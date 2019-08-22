(ns lipas.ui.components.buttons
  (:require
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn email-button [{:keys [on-click label] :as props}]
  [mui/button
   (merge
    {:color    "secondary"
     :variant  "contained"
     :on-click on-click}
    props)
   [mui/icon {:style {:margin-right "0.25em"}}
    "email"]
   label])

(defn download-button [{:keys [on-click label] :as props}]
  [mui/button (merge {:color    "secondary"
                      :variant  "outlined"
                      :on-click on-click}
                     props)
   label])

(defn login-button [{:keys [on-click label]}]
  [mui/button {:variant "contained" :color "secondary" :on-click on-click}
   [mui/icon {:style {:margin-right "0.25em"}}
    "lock"]
   label])

(defn register-button [{:keys [href on-click label]}]
  [mui/button
   {:variant  "contained"
    :color    "secondary"
    :href     href
    :on-click on-click}
   [mui/icon {:style {:margin-right "0.25em"}}
    "group_add"]
   label])

(defn edit-button [{:keys [on-click tooltip] :as props}]
  (let [btn-props (-> props
                      (dissoc :active?)
                      (merge {:on-click on-click}))]
    [mui/tooltip {:title (or tooltip "") :placement "top"}
     [mui/fab btn-props
      [mui/icon "edit_icon"]]]))

(defn save-button [{:keys [on-click tooltip disabled disabled-tooltip color]
                    :or   {color "secondary"} :as props}]
  [mui/tooltip {:title (if disabled disabled-tooltip "") :placement "top"}
   ;; Mui will complain if tooltip is bound to disabled button unless
   ;; there's a wrapper component. Therefore :span is here.
   [:span
    [mui/fab
     (merge
      (dissoc props :disabled-tooltip :color)
      {:disabled disabled :on-click on-click :variant "extended" :color color})
     tooltip
     [mui/icon {:style {:margin-left "0.25em"}}
      "save_icon"]]]])

(defn publish-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title "" :placement "top"}
   [mui/button
    (merge props {:variant "contained" :on-click on-click :color "secondary"})
    tooltip
    [mui/icon {:style {:margin-left "0.25em"}}
     "cloud_upload"]]])

(defn discard-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title (or tooltip "") :placement "top"}
   [mui/fab (merge props {:on-click on-click :size "small"})
    [mui/icon {:class "material-icons-outlined"} "close"]]])

(defn delete-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title (or tooltip "") :placement "top"}
   [mui/fab (merge props {:on-click on-click :size "small"})
    [mui/icon "delete"]]])

(defn confirming-delete-button [{:keys [on-delete tooltip confirm-tooltip]}]
  (r/with-let [timeout  10000
               clicked? (r/atom false)
               timeout* (r/atom nil)]

    [:span
     [mui/tooltip
      {:title     (or tooltip "")
       :placement "top"}
      [mui/icon-button
       {:on-click #(if @clicked?
                     (do
                       (js/clearTimeout @timeout*)
                       (reset! clicked? false)
                       (on-delete %))
                     (do
                       (reset! timeout*
                               (js/setTimeout
                                (fn []
                                  (reset! clicked? false)) timeout))
                       (reset! clicked? true)))}
       (if @clicked?
         [mui/icon "delete_forever"]
         [mui/icon "delete"])]]
     (when @clicked?
       [mui/typography {:style {:display "inline"} :color :error}
        confirm-tooltip])]))
