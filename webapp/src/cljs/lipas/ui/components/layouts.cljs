(ns lipas.ui.components.layouts
  (:require ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.mui :as mui]
            [reagent-dev-tools.state :as dev-state]
            [reagent.core :as r]))

(defn floating-container
  [{:keys [ref top right bottom left background-color]
    :or   {background-color mui/gray2}} & children]
  (let [{:keys [open? place width height]} @dev-state/dev-state]
    (into
      [:div.no-print
       {:ref ref
        :style
        {:position         "fixed"
         :z-index          999
         :background-color background-color
         :top              top
         :right            right
         :margin-right     (if (and right open? (= :right place))
                             width
                             0)
         :bottom           bottom
         :margin-bottom    (if (and bottom open? (= :bottom place))
                             height
                             0)
         :left             left}}]
      children)))

(defn card
  [{:keys [title xs md lg] :or {xs 12 md 6}} & content]
  [:> Grid {:item true :xs xs :md md :lg lg}
   [:> Card {:square true :style {:height "100%"}}
    [:> CardHeader {:title title}]
    (into [:> CardContent] content)]])

(defn expansion-panel
  [{:keys [label label-icon label-color default-expanded style disabled]
    :or   {label-color "inherit" style {:margin-top "1em"} disabled false}}
   & children]
  [:> Accordion
   {:default-expanded default-expanded :style style :disabled disabled}
   [:> AccordionSummary
    {:expand-icon (r/as-element [:> Icon "expand_more"])}
    (when label-icon
      [:span {:style {:margin-right "12px"}} label-icon])
    [:> Typography {:color label-color :variant "button"} label]]
   (into [:> AccordionDetails] children)])
