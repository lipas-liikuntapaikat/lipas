(ns lipas.ui.components.buttons
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Tooltip$default" :as Tooltip]))

(defn email-button [{:keys [on-click label] :as props}]
  [:> Button
   (merge
     {:color    "secondary"
      :variant  "contained"
      :on-click on-click}
     props)
   [:> Icon {:style {:margin-right "0.25em"}}
    "email"]
   label])

(defn download-button [{:keys [on-click label] :as props}]
  [:> Button (merge {:color    "secondary"
                      :variant  "outlined"
                      :on-click on-click}
                     props)
   label])

(defn login-button [{:keys [on-click label]}]
  [:> Button {:variant "contained" :color "secondary" :on-click on-click}
   [:> Icon {:style {:margin-right "0.25em"}}
    "lock"]
   label])

(defn register-button [{:keys [href on-click label]}]
  [:> Button
   {:variant  "contained"
    :color    "secondary"
    :href     href
    :on-click on-click}
   [:> Icon {:style {:margin-right "0.25em"}}
    "group_add"]
   label])

(defn edit-button [{:keys [on-click tooltip] :as props}]
  (let [btn-props (-> props
                      (dissoc :active?)
                      (merge {:on-click on-click}))]
    [:> Tooltip {:title (or tooltip "") :placement "top"}
     [:span
      [:> Fab btn-props
       [:> Icon "edit_icon"]]]]))

(defn save-button [{:keys [on-click tooltip disabled disabled-tooltip color]
                    :or   {color "secondary"} :as props}]
  [:> Tooltip {:title (if disabled disabled-tooltip "") :placement "top"}
   ;; Mui will complain if tooltip is bound to disabled button unless
   ;; there's a wrapper component. Therefore :span is here.
   [:span
    [:> Fab
     (merge
       (dissoc props :disabled-tooltip :color)
       {:disabled disabled :on-click on-click :variant "extended" :color color})
     tooltip
     [:> Icon {:style {:margin-left "0.25em"}}
      "save_icon"]]]])

(defn discard-button [{:keys [on-click tooltip] :as props}]
  [:> Tooltip {:title (or tooltip "") :placement "top"}
   [:span
    [:> Fab (merge props {:on-click on-click :size "small"})
     [:> Icon {:class "material-icons-outlined"} "close"]]]])

(defn locator-button [{:keys [on-click tooltip] :as props}]
  [:> Tooltip {:title (or tooltip "") :placement "top"}
   [:span
    [:> Fab (merge props {:on-click on-click :size "small"})
     [:> Icon {:class "material-icons-outlined"} "location_searching"]]]])

(defn delete-button [{:keys [on-click tooltip] :as props}]
  [:> Tooltip {:title (or tooltip "") :placement "top"}
   [:span
    [:> Fab (merge props {:on-click on-click :size "small"})
     [:> Icon "delete"]]]])

(defn confirming-delete-button [{:keys [on-delete tooltip confirm-tooltip]}]
  [:<>
   [:> Tooltip
    {:title     (or tooltip "")
     :placement "top"}
    [:span
     [:> IconButton
      {:on-click (fn [e]
                   (when (js/confirm confirm-tooltip)
                     (on-delete e)))}
      [:> Icon "delete"]]]]])
