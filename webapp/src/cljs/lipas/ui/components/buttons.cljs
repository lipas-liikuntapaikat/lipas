(ns lipas.ui.components.buttons
  (:require [lipas.ui.mui :as mui]))

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
     [:span
      [mui/fab btn-props
       [mui/icon "edit_icon"]]]]))

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

(defn discard-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title (or tooltip "") :placement "top"}
   [:span
    [mui/fab (merge props {:on-click on-click :size "small"})
     [mui/icon {:class "material-icons-outlined"} "close"]]]])

(defn locator-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title (or tooltip "") :placement "top"}
   [:span
    [mui/fab (merge props {:on-click on-click :size "small"})
     [mui/icon {:class "material-icons-outlined"} "location_searching"]]]])

(defn delete-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title (or tooltip "") :placement "top"}
   [:span
    [mui/fab (merge props {:on-click on-click :size "small"})
     [mui/icon "delete"]]]])

(defn confirming-delete-button [{:keys [on-delete tooltip confirm-tooltip]}]
  [:<>
   [mui/tooltip
    {:title     (or tooltip "")
     :placement "top"}
    [:span
     [mui/icon-button
      {:on-click (fn [e]
                   (when (js/confirm confirm-tooltip)
                     (on-delete e)))}
      [mui/icon "delete"]]]]])
