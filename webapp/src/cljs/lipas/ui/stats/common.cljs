(ns lipas.ui.stats.common
  (:require [lipas.ui.components.layouts :as layouts]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.mui :as mui]
            [reagent.core :as r]))

(def select-style {:min-width "170px"})

(defn view-tabs [{:keys [value on-change]}]
  [:> Tabs
   {:value value
    :on-change on-change
    :indicator-color "secondary"
    :text-color "inherit"}
   [:> Tab {:value "chart" :icon (r/as-element [:> Icon "bar_chart"])}]
   [:> Tab {:value "table" :icon (r/as-element [:> Icon "table_chart"])}]])

(defn download-excel-button [{:keys [tr on-click]}]
  [:> Button
   {:style    {:margin "1em"}
    :variant  "outlined"
    :color    "secondary"
    :on-click on-click}
   (tr :actions/download-excel)])

(defn disclaimer [{:keys [texts label]}]
  [layouts/expansion-panel
   {:label            label
    :default-expanded true
    :style            {:margin-bottom "1em" :background-color mui/gray3}}
   (into
     [:> CardContent]
     (for [text texts]
       [:> Typography {:variant "body1" :paragraph true}
        text]))])
