(ns lipas.ui.charts
  (:require
   ["recharts" :as rc]
   [clojure.set :refer [rename-keys map-invert]]
   [goog.object :as gobj]
   [lipas.ui.mui :as mui]
   [lipas.ui.components.misc :as misc]
   [reagent.core :as r]))

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
   "municipal-consortium"   "#dc9b33"
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
   "private-foundation"      "#27AE60"})

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

(def legend-icons
  {"rect" "label"
   "line" "show_chart"})

(defn legend [labels props]
  (let [payload (gobj/get props "payload")]
    (r/as-element
     (->> payload
          (map
           (fn [obj]
             {:label (or (labels (gobj/get obj "value"))
                         (labels (keyword (gobj/get obj "value"))))
              :color (gobj/get obj "color")
              :type  (gobj/get obj "type")}))
          (sort-by :label)
          (map
           (fn [{:keys [label color type]}]
             [mui/grid {:item true}
              [misc/icon-text2
               {:icon (legend-icons type) :icon-color color :text label}]]))
          (into
           [mui/grid {:container true :justify "center"}])))))

(defn tooltip
  "`payload-fn` should return a map with
  keys :label :value (:icon :color)."
  [payload-fn labels props]
  (let [label   (gobj/get props "label")
        payload (gobj/get props "payload")]
    (r/as-element
     [mui/paper {:style {:padding "0.5em"}}

      ;; Tooltip header
      [mui/typography
       {:variant "body2" :align "center" :style {:margin-bottom "0.25em"}}
       label]

      ;; Content table
      [mui/table {:style {:width "350"} :padding "dense"}
       (->> payload
            payload-fn
            (sort-by :label)
            (map
             (fn [{:keys [label value icon color]}]
               [mui/table-row {:style {:height "24px"}}
                (when icon
                  [mui/table-cell {:padding "checkbox"}
                   [mui/icon {:style {:color color}}
                    icon]])
                [mui/table-cell
                 [mui/typography {:variant "caption"}
                  label]]
                [mui/table-cell
                 [mui/typography
                  value]]]))
            (into [mui/table-body]))]])))

(defn finance-tooltip [labels props]
  (let [payload-fn (fn [payload]
                     (->> payload
                          (map
                           (fn [obj]
                             {:color (gobj/get obj "color")
                              :value (gobj/get obj "value")
                              :icon  (if (gobj/get obj "stroke")
                                       (legend-icons "line")
                                       (legend-icons "rect"))
                              :label (labels (keyword (gobj/get obj "name")))}))))]
    (tooltip payload-fn labels props)))

(defn finance-chart [{:keys [data metrics labels on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 500}
   (-> [:> rc/ComposedChart {:data data :on-click on-click}
        [:> rc/Legend {:content (partial legend labels)}]
        [:> rc/Tooltip {:content (partial finance-tooltip labels)}]
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
          [:> rc/Line
           {:type    "monotone"
            :dataKey k2
            :stroke  (get colors k1)}])))])

(defn age-structure-tooltip [labels props]
  (let [payload-fn (fn [payload]
                     (->> payload
                          (map
                           (fn [obj]
                             {:label (labels (gobj/get obj "name"))
                              :color (gobj/get obj "fill")
                              :icon  "label"
                              :value (gobj/get obj "value")}))))]
    (tooltip payload-fn labels props)))

(defn age-structure-chart [{:keys [data labels]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/BarChart {:data data}
     [:> rc/Legend {:content (partial legend labels)}]
     [:> rc/Tooltip {:content (partial age-structure-tooltip labels)}]
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
        :fill    (get colors k)}]))])

(defn sports-stats-tooltip [labels props]
  (let [payload-fn (fn [payload]
                     (let [entry (-> payload
                                     first
                                     (gobj/get "payload")
                                     (js->clj :keywordize-keys true))]
                       (->> entry
                            (reduce
                             (fn [res [k v]]
                               (if-let [label (labels k)]
                                 (conj res {:label label :value v})
                                 res))
                             []))))]
    (tooltip payload-fn labels props)))

(defn sports-stats-chart [{:keys [data labels metric grouping]}]
  (let [;;data       (->> data (map #(rename-keys % labels)))
        margin     {:top 5 :right 100 :bottom 5 :left 100}
        y-axis-key (if (= "location.city.city-code" grouping)
                     :city-name
                     :type-name)]
    [:> rc/ResponsiveContainer {:width "100%" :height (+ 60 (* 30 (count data)))}
     [:> rc/BarChart {:data data :layout "vertical" :margin margin}
      [:> rc/Legend {:content (partial legend labels)}]
      [:> rc/Tooltip {:content (partial sports-stats-tooltip labels)}]
      [:> rc/XAxis {:tick font-styles :type "number"}]
      [:> rc/YAxis {:dataKey y-axis-key :type "category" :tick font-styles}]
      [:> rc/Bar {:dataKey (keyword metric) :fill "orange"}
       [:> rc/LabelList {:position "right"}]]]]))
