(ns lipas.ui.stats.common
  (:require
   [lipas.ui.mui :as mui]
   [lipas.ui.components :as components]
   [reagent.core :as r]))

(def select-style {:min-width "170px"})

(defn view-tabs [{:keys [value on-change]}]
  [mui/tabs {:value value :on-change on-change}
   [mui/tab {:value "chart" :icon (r/as-element [mui/icon "bar_chart"])}]
   [mui/tab {:value "table" :icon (r/as-element [mui/icon "table_chart"])}]])

(defn download-excel-button [{:keys [tr on-click]}]
  [mui/button
   {:style    {:margin "1em"}
    :variant  "outlined"
    :color    "secondary"
    :on-click on-click}
   (tr :actions/download-excel)])

(defn disclaimer [{:keys [texts label]}]
  [components/expansion-panel
   {:label            label
    :default-expanded true
    :style            {:margin-bottom "1em" :background-color mui/gray3}}
   (into
    [mui/card-content]
    (for [text texts]
      [mui/typography {:variant "body1" :paragraph true}
       text]))])
