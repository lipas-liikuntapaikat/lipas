(ns lipas.ui.components.layouts
  (:require
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn floating-container
  [{:keys [top right bottom left background-color]
    :or   [background-color mui/gray2]} & children]
  (into
   [:div.no-print
    {:style
     {:position         "fixed"
      :z-index          999
      :background-color background-color
      :top              top
      :right            right
      :bottom           bottom
      :left             left}}]
   children))

(defn card [{:keys [title xs md lg] :or {xs 12 md 6}} & content]
  [mui/grid {:item true :xs xs :md md :lg lg}
   [mui/card {:square true :style {:height "100%"}}
    [mui/card-header {:title title}]
    (into [mui/card-content] content)]])

(defn expansion-panel
  [{:keys [label label-color default-expanded]
    :or   {label-color "default"}} & children]
  [mui/expansion-panel
   {:default-expanded default-expanded :style {:margin-top "1em"}}
   [mui/expansion-panel-summary
    {:expand-icon (r/as-element [mui/icon "expand_more"])}

    [mui/typography {:color label-color :variant "button"} label]]
   (into [mui/expansion-panel-details] children)])
