(ns lipas.ui.charts
  (:require ["recharts" :as rc]
            [clojure.set :refer [rename-keys map-invert]]))

(def colors
  {:energy-mwh       "orange"
   ;; :electricity-mwh  "#ffd400"
   :electricity-mwh  "#efc700"
   :heat-mwh         "#ff503c"
   :water-m3         "#0a9bff"
   ;; :total-count      "#8ce614"
   :total-count      "#3367d6"
   ;; :spectators-count "#05cdaa"
   :spectators-count "#72a4f7"
   })

(def font-styles
  {:font-family "lato"})

(def font-styles-bold
  (merge font-styles
         {:font-weight "bold"}))

(def tooltip-styles
  {:itemStyle  font-styles-bold
   :labelStyle font-styles-bold})

(defn energy-chart [{:keys [data energy energy-label]}]
  (let [data (map #(rename-keys % {energy energy-label}) data)]
   [:> rc/ResponsiveContainer {:width "100%" :height 300}
    [:> rc/BarChart {:data data}
     [:> rc/CartesianGrid]
     [:> rc/Legend {:wrapperStyle font-styles}]
     [:> rc/Tooltip tooltip-styles]
     [:> rc/YAxis {:tick font-styles}]
     [:> rc/XAxis {:dataKey :name :label false :tick false}]
     [:> rc/Bar {:dataKey energy-label :label false :fill (get colors energy)}]]]))

(defn monthly-chart [{:keys [data labels]}]
  (let [data-keys (vals (select-keys labels [:electricity-mwh
                                             :heat-mwh
                                             :cold-mwh
                                             :water-m3
                                             :total-count
                                             :spectators-count]))
        lookup    (map-invert labels)
        data      (->> data
                   (map #(rename-keys % labels))
                   (map #(update % :month labels)))]
    [:> rc/ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> rc/BarChart {:data data}
       [:> rc/Legend {:wrapperStyle font-styles}]
       [:> rc/Tooltip tooltip-styles]
       [:> rc/YAxis {:tick font-styles}]
       [:> rc/XAxis {:dataKey :month :tick font-styles}]]
      (for [k data-keys]
        (when (some #(get % k) data)
          [:> rc/Bar {:dataKey k :label false :fill (get colors (get lookup k))}])))]))

(defn yearly-chart [{:keys [data labels on-click]}]
  (let [data-keys (vals (select-keys labels [:electricity-mwh
                                             :heat-mwh
                                             :cold-mwh
                                             :water-m3
                                             :total-count
                                             :spectators-count]))
        lookup    (map-invert labels)
        data      (->> data
                       (map #(rename-keys % labels)))]
    [:> rc/ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> rc/BarChart {:data     data
                       :on-click on-click}
       [:> rc/Legend {:wrapperStyle font-styles}]
       [:> rc/Tooltip tooltip-styles]
       [:> rc/YAxis {:tick font-styles}]
       [:> rc/XAxis {:dataKey :year :tick font-styles}]]
      (for [k data-keys]
        (when (some #(get % k) data)
          [:> rc/Bar {:dataKey k :label false :fill (get colors (get lookup k))}])))]))
