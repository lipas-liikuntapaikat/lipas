(ns lipas.ui.components.checkboxes
  (:require ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [reagent.core :as r]))

(defn checkbox
  [{:keys [label value on-change disabled style icon checked-icon tooltip]
    :or   {tooltip ""}}]
  [:> Tooltip {:title tooltip}
   [:> FormControlLabel
    {:label   (r/as-element [:> Typography {:variant "body1"} label])
     :style   (merge {:width :fit-content} style)
     :control (r/as-element
                [:> Checkbox
                 (merge
                   {:value     (str (boolean value))
                    :checked   (boolean value)
                    :disabled  disabled
                    :color     "secondary"
                  ;; %2 = checked?
                    :on-change #(on-change %2)}
                   (when icon
                     {:icon (r/as-element icon)})
                   (when checked-icon
                     {:checked-icon (r/as-element checked-icon)}))])}]])

(defn switch
  [{:keys [label value on-change] :as props}]
  [:> FormGroup
   [:> FormControlLabel
    {:label label
     :control
     (r/as-element
       [:> Switch
        (merge
          {:value     value
           :checked   value
           :color     "secondary"
           :on-change #(on-change (-> % .-target .-checked))}
          (dissoc props :on-change))])}]])
