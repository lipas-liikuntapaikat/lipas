(ns lipas.ui.charts
  (:require ["recharts" :as rc]))

(def colors
  {:electricity-mwh "#FFBD00"
   :heat-mwh        "#ff503c"
   :water-m3        "#0a9bff"})

(defn energy-chart [{:keys [data energy energy-label]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   [:> rc/BarChart {:data data}
    [:> rc/CartesianGrid]
    [:> rc/Legend]
    [:> rc/Tooltip]
    [:> rc/YAxis]
    [:> rc/XAxis {:dataKey :name :label false :tick false}]
    [:> rc/Bar {:dataKey (name energy) :label false :fill (get colors energy)}]]])
