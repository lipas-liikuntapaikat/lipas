(ns lipas.ui.components.layouts
  (:require
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn floating-container
  [{:keys [ref top right bottom left background-color]
    :or   {background-color mui/gray2}} & children]
  (into
   [:div.no-print
    {:ref ref
     :style
     {:position         "fixed"
      :z-index          999
      :background-color background-color
      :top              top
      :right            right
      :bottom           bottom
      :left             left}}]
   children))

(defn card
  [{:keys [title xs md lg] :or {xs 12 md 6}} & content]
  [mui/grid {:item true :xs xs :md md :lg lg}
   [mui/card {:square true :style {:height "100%"}}
    [mui/card-header {:title title}]
    (into [mui/card-content] content)]])

(defn expansion-panel
  [{:keys [label label-color default-expanded style]
    :or   {label-color "inherit" style {:margin-top "1em"}}} & children]
  [mui/expansion-panel
   {:default-expanded default-expanded :style style}
   [mui/expansion-panel-summary
    {:expand-icon (r/as-element [mui/icon "expand_more"])}

    [mui/typography {:color label-color :variant "button"} label]]
   (into [mui/expansion-panel-details] children)])
