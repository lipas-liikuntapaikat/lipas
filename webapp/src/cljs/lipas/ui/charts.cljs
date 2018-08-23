(ns lipas.ui.charts
  (:require ["recharts" :as rc]))

(def colors {:electricity-mwh "#FFBD00"
             :heat-mwh        "#ff503c"
             :water-m3        "#0a9bff"})

(defn energy-activity-chart [{:keys [electricity-count heat-count water-count n]}]
  (let [data [{:electricity electricity-count
               :heat        heat-count
               :water       water-count}]]
    [:> rc/ResponsiveContainer {:width "100%" :height 300}
     [:> rc/BarChart {:height 300 :data data}
      [:> rc/Legend]
      ;; [:> rc/Tooltip]
      [:> rc/YAxis {:domain [0 n]
                    :label {:value "Hallia" :position "insideTopLeft" :offset 20}
                    :mirror true}]
      [:> rc/Bar {:dataKey "electricity" :label true :fill (:electricity-mwh colors)}]
      [:> rc/Bar {:dataKey "heat" :label true :fill (:heat-mwh colors)}]
      [:> rc/Bar {:dataKey "water" :label true :fill (:water-m3 colors)}]]]))

(defn energy-activity-chart-2 [{:keys [data]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   [:> rc/BarChart {:data data}
    [:> rc/Legend]
    [:> rc/Tooltip]
    [:> rc/YAxis]
    [:> rc/XAxis {:dataKey :name :label false :name nil}]
    [:> rc/Bar {:dataKey "electricity-mwh" :label false :fill (:electricity-mwh colors)}]
    [:> rc/Bar {:dataKey "heat-mwh" :label false :fill (:heat-mwh colors)}]
    [:> rc/Bar {:dataKey "water-m3" :label false :fill (:water-m3 colors)}]]])

(defn energy-activity-chart-3 [{:keys [data energy]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   [:> rc/BarChart {:data data}
    [:> rc/Legend]
    [:> rc/Tooltip]
    [:> rc/YAxis]
    [:> rc/XAxis {:dataKey :name :label false :tick false}]
    [:> rc/Bar {:dataKey (name energy) :label false :fill (get colors energy)}]]])
