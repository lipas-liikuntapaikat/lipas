(ns lipas.ui.charts
  (:require
   ["recharts" :as rc]
   [clojure.set :refer [rename-keys map-invert]]
   [lipas.ui.mui :as mui]))

(def colors
  {:energy-mwh       "orange"
   ;; :electricity-mwh  "#ffd400"
   :electricity-mwh  "#efc700"       ; yellow
   :heat-mwh         "#ff503c"       ; red-orange
   :water-m3         "#0a9bff"       ; blue
   ;; :total-count      "#8ce614"
   :total-count      "#3367d6"       ; darker blue
   ;; :spectators-count "#05cdaa"
   :spectators-count "#72a4f7"       ; lighter blue

   :investments        "#1e6ed6"     ; blue
   :operating-expenses "#ff503c"     ; orange
   :operating-incomes  "#3ba12f"     ; green
   :subsidies          "#8ce614"     ; light green
   :net-costs          "#d51a0d"     ; red
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
        data      (->> data (map #(rename-keys % labels)))]
    [:> rc/ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> rc/BarChart {:data data :on-click on-click}
       [:> rc/Legend {:wrapperStyle font-styles}]
       [:> rc/Tooltip tooltip-styles]
       [:> rc/YAxis {:tick font-styles}]
       [:> rc/XAxis {:dataKey :year :tick font-styles}]]
      (for [k data-keys]
        (when (some #(get % k) data)
          [:> rc/Bar {:dataKey k :label false :fill (get colors (get lookup k))}])))]))

(defn energy-totals-gauge [{:keys [data energy-type]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 150}
   [:> rc/PieChart {:data data}
    [:> rc/Pie
     {:startAngle  180
      :endAngle    0
      :data        data
      :cy          110
      :outerRadius 100
      :dataKey     :value}
     [:> rc/Cell {:fill (colors energy-type)}]
     [:> rc/Cell] {:fill mui/gray1}]
    [:> rc/Legend
     {:wrapperStyle font-styles}]]])

(defn city-stats-chart [{:keys [data metrics on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 500}
   (-> [:> rc/ComposedChart {:data data :on-click on-click}
        [:> rc/Legend {:wrapperStyle font-styles}]
        [:> rc/Tooltip tooltip-styles]
        [:> rc/YAxis {:tick font-styles}]
        [:> rc/XAxis {:dataKey :year :tick font-styles}]]
    (into
     (for [m    metrics
           :let [k (keyword m)]]
       [:> rc/Bar {:dataKey k :label false :fill (get colors k)}]))
    (into
     (for [m    metrics
           :let [k1 (keyword m)
                 k2 (keyword (str m "-avg"))]]
       [:> rc/Line {:type "monotone" :dataKey k2 :stroke (get colors k1)}])))])
