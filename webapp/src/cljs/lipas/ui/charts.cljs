(ns lipas.ui.charts
  (:require ["recharts" :as rc]
            [clojure.set :refer [rename-keys]]))

(def colors
  {:electricity-mwh "#FFBD00"
   :heat-mwh        "#ff503c"
   :water-m3        "#0a9bff"})

(defn energy-chart [{:keys [data energy energy-label]}]
  (let [data (map #(rename-keys % {energy energy-label}) data)]
   [:> rc/ResponsiveContainer {:width "100%" :height 300}
    [:> rc/BarChart {:data data}
     [:> rc/CartesianGrid]
     [:> rc/Legend]
     [:> rc/Tooltip]
     [:> rc/YAxis]
     [:> rc/XAxis {:dataKey :name :label false :tick false}]
     [:> rc/Bar {:dataKey energy-label :label false :fill (get colors energy)}]]]))
