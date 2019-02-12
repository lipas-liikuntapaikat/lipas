(ns lipas.ui.charts
  (:require
   ["recharts" :as rc]
   [clojure.set :refer [rename-keys map-invert]]
   [lipas.ui.mui :as mui]))

;; Tip: enable rainbow-mode in emacs.

(def colors
  {;; Energy

   :energy-mwh       "orange"
   :electricity-mwh  "#efc700"
   :heat-mwh         "#ff503c"
   :water-m3         "#0a9bff"
   :total-count      "#3367d6"
   :spectators-count "#72a4f7"

   ;; City finance
   :investments        "#1e6ed6"
   :operating-expenses "#ff503c"
   :operating-incomes  "#3ba12f"
   :subsidies          "#8ce614"
   :net-costs          "#d51a0d"

   ;;; Age structure

   ;; Owners
   "other"                  mui/gray1
   "city"                   "#D35400"
   "city-main-owner"        "#DC7633"
   "registered-association" "#2980B9"
   "company-ltd"            "#8E44AD"
   "state"                  "#1ABC9C"
   "foundation"             "#27AE60"
   "unknown"                "#283747"

   ;; Admins
   "city-sports"             "#D35400"
   "city-education"          "#BA4A00"
   "city-technical-services" "#A04000"
   "city-other"              "#873600"
   "private-association"     "#2980B9"
   "private-company"         "#8E44AD"
   "private-foundation"      "#27AE60"
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

(defn finance-chart [{:keys [data metrics labels on-click]}]
  (let [lookup (map-invert labels)
        data   (->> data (map #(rename-keys % labels)))]
    [:> rc/ResponsiveContainer {:width "100%" :height 500}
     (-> [:> rc/ComposedChart {:data data :on-click on-click}
          [:> rc/Legend {:wrapperStyle font-styles}]
          [:> rc/Tooltip tooltip-styles]
          [:> rc/YAxis {:tick font-styles}]
          [:> rc/XAxis {:dataKey :year :tick font-styles}]]
         (into
          (for [m    metrics
                :let [k ((keyword m) labels)]]
            [:> rc/Bar {:dataKey k :label false :fill (get colors (get lookup k))}]))
         (into
          (for [m    metrics
                :let [k1 ((keyword m) labels)
                      k2 ((keyword (str m "-avg")) labels)]]
            [:> rc/Line
             {:type    "monotone"
              :dataKey k2
              :stroke  (get colors (get lookup k1))}])))]))

(defn age-structure-chart [{:keys [data labels]}]
  (let [lookup (map-invert labels)
        data   (->> data (map #(rename-keys % labels)))]
    [:> rc/ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> rc/BarChart {:data data}
       [:> rc/Legend {:wrapperStyle font-styles}]
       [:> rc/Tooltip tooltip-styles]
       [:> rc/YAxis
        {:tick font-styles
         :label
         {:value    (:y-axis labels)
          :angle    -90
          :offset   10
          :position "insideBottomLeft"}}]
       [:> rc/XAxis {:dataKey :construction-year :tick font-styles}]]
      (for [k (->> data (mapcat keys) set (remove #(= :construction-year %)) sort)]
        [:> rc/Bar
         {:stackId "a"
          :dataKey k
          :label   false
          :fill    (get colors (get lookup k))}]))]))

(defn sports-stats-chart [{:keys [data labels metric]}]
  (let [lookup (map-invert labels)
        data   (->> data (map #(rename-keys % labels)))
        margin {:top 5 :right 5 :bottom 5 :left 50}]
    [:> rc/ResponsiveContainer {:width "100%" :height (+ 60 (* 30 (count data)))}
     [:> rc/BarChart {:data data :layout "vertical" :margin margin}
      [:> rc/Legend {:wrapperStyle font-styles}]
      [:> rc/Tooltip tooltip-styles]
      [:> rc/XAxis {:tick font-styles :type "number"}]
      [:> rc/YAxis {:dataKey :city-name :type "category" :tick font-styles}]
      [:> rc/Bar {:dataKey (labels (keyword metric)) :label false :fill "orange"}]]]))
