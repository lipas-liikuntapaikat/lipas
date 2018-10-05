(ns lipas.ui.charts
  (:require ["recharts" :as rc]
            [lipas.ui.mui :as mui]
            [clojure.set :refer [rename-keys map-invert]]))

(def colors
  {:energy-mwh      "orange"
   :electricity-mwh "#ffd400"
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

(defn energy-history-chart [{:keys [data labels]}]
  (let [data-keys (vals (select-keys labels [:electricity-mwh :heat-mwh :cold-mwh :water-m3]))
        lookup    (map-invert labels)
        data      (->> data
                   (map #(rename-keys % labels))
                   (map #(update % :month labels)))]
    [:> rc/ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> rc/BarChart {:data data}
       [:> rc/Legend]
       [:> rc/Tooltip]
       [:> rc/YAxis]
       [:> rc/XAxis {:dataKey :month :tick true}]]
      (for [k data-keys]
        (when (some #(get % k) data)
          [:> rc/Bar {:dataKey k :label false :fill (get colors (get lookup k))}])))]))
