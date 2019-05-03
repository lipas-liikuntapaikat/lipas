(ns lipas.ui.stats.common
  (:require
   [lipas.ui.mui :as mui]
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
