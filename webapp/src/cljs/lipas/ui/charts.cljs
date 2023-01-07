(ns lipas.ui.charts
  (:require
   ["recharts/es6/cartesian/Area" :refer [Area]]
   ["recharts/es6/cartesian/Bar" :refer [Bar]]
   ["recharts/es6/cartesian/CartesianGrid" :refer [CartesianGrid]]
   ["recharts/es6/cartesian/Line" :refer [Line]]
   ["recharts/es6/cartesian/XAxis" :refer [XAxis]]
   ["recharts/es6/cartesian/YAxis" :refer [YAxis]]
   ["recharts/es6/chart/AreaChart" :refer [AreaChart]]
   ["recharts/es6/chart/BarChart" :refer [BarChart]]
   ["recharts/es6/chart/ComposedChart" :refer [ComposedChart]]
   ["recharts/es6/chart/PieChart" :refer [PieChart]]
   ["recharts/es6/component/Cell" :refer [Cell]]
   ["recharts/es6/component/LabelList" :refer [LabelList]]
   ["recharts/es6/component/Legend" :refer [Legend]]
   ["recharts/es6/component/ResponsiveContainer" :refer [ResponsiveContainer]]
   ["recharts/es6/component/Tooltip" :refer [Tooltip]]
   ["recharts/es6/polar/Pie" :refer [Pie]]
   [clojure.set :refer [rename-keys map-invert]]
   [clojure.string :as string]
   [goog.object :as gobj]
   [goog.string :as gstring]
   [lipas.ui.components.misc :as misc]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :as utils]
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
   :operating-expenses "#ff6f0f"
   :operating-incomes  "#3ba12f"
   :subsidies          "#8ddd21"
   :net-costs          "#d51a0d"

   ;;; Age structure

   ;; Owners
   "other"                  "#7c5235"
   "registered-association" "#3ba12f"
   "state"                  "#8ddd21"
   "city"                   "orange"
   "city-main-owner"        "#efc700"
   "municipal-consortium"   "#bf682b"
   "company-ltd"            "#0a9bff"
   "foundation"             "#8E44AD"
   "unknown"                mui/gray1

   ;; Admins
   "city-sports"             "orange"
   "city-education"          "#efc700"
   "city-technical-services" "#ff503c"
   "city-other"              "#d51a0d"
   "private-association"     "#3ba12f"
   "private-company"         "#0a9bff"
   "private-foundation"      "#8E44AD"})

(def font-styles
  {:font-family "lato"})

(def font-styles-bold
  (merge font-styles
         {:font-weight "bold"}))

(def tooltip-styles
  {:itemStyle  font-styles-bold
   :labelStyle font-styles-bold})

(defn parse-number [s]
  (when (string? s)
    (js/Number.parseFloat (string/replace s #"[^\d\.]" ""))))

(defn energy-chart
  [{:keys [data energy energy-label]}]
  (let [data (map #(rename-keys % {energy energy-label}) data)]
   [:> ResponsiveContainer {:width "100%" :height 300}
    [:> BarChart {:data data}
     [:> CartesianGrid]
     [:> Legend {:wrapperStyle font-styles}]
     [:> Tooltip tooltip-styles]
     [:> YAxis {:tick font-styles}]
     [:> XAxis {:dataKey :name :label false :tick false}]
     [:> Bar {:dataKey energy-label :label false :fill (get colors energy)}]]]))

(defn monthly-chart
  [{:keys [data labels]}]
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
    [:> ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> BarChart {:data data}
       [:> Legend {:wrapperStyle font-styles}]
       [:> Tooltip tooltip-styles]
       [:> YAxis {:tick font-styles}]
       [:> XAxis {:dataKey :month :tick font-styles}]]
      (for [k data-keys]
        (when (some #(get % k) data)
          [:> Bar {:dataKey k :label false :fill (get colors (get lookup k))}])))]))

(defn yearly-chart
  [{:keys [data labels on-click]}]
  (let [data-keys (vals (select-keys labels [:electricity-mwh
                                             :heat-mwh
                                             :cold-mwh
                                             :water-m3
                                             :total-count
                                             :spectators-count]))
        lookup    (map-invert labels)
        data      (->> data (map #(rename-keys % labels)))]
    [:> ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> BarChart {:data data :on-click on-click}
       [:> Legend {:wrapperStyle font-styles}]
       [:> Tooltip tooltip-styles]
       [:> YAxis {:tick font-styles}]
       [:> XAxis {:dataKey :year :tick font-styles}]]
      (for [k data-keys]
        (when (some #(get % k) data)
          [:> Bar {:dataKey k :label false :fill (get colors (get lookup k))}])))]))

(defn energy-totals-gauge
  [{:keys [data energy-type]}]
  [:> ResponsiveContainer {:width "100%" :height 150}
   [:> PieChart {:data data}
    [:> Pie
     {:startAngle  180
      :endAngle    0
      :data        data
      :cy          110
      :outerRadius 100
      :dataKey     :value}
     [:> Cell {:fill (colors energy-type)}]
     [:> Cell] {:fill mui/gray1}]
    [:> Legend
     {:wrapperStyle font-styles}]]])

(def legend-icons
  {"rect" "label"
   "line" "show_chart"})

(defn legend
  ([labels props]
   (legend labels :label props))
  ([labels sort-fn props]
   (let [payload (gobj/get props "payload")]
     (r/as-element
      (->> payload
           (map
            (fn [obj]
              {:label (or (labels (gobj/get obj "value"))
                          (labels (keyword (gobj/get obj "value"))))
               :color (gobj/get obj "color")
               :type  (gobj/get obj "type")}))
           (sort-by sort-fn)
           (map
            (fn [{:keys [label color type]}]
              [mui/grid {:item true}
               [misc/icon-text2
                {:icon (legend-icons type) :icon-color color :text label}]]))
           (into
            [mui/grid {:container true :justify-content "center"}]))))))

(defn tooltip
  "`payload-fn` should return a map with
  keys :label :value (:icon :color)."
  ([payload-fn labels ^js props]
   (tooltip payload-fn labels :label false props))
  ([payload-fn labels sort-fn ^js props]
   (tooltip payload-fn labels sort-fn false props))
  ([payload-fn labels sort-fn hide-header? ^js props]
   (let [label   (gobj/get props "label")
         payload (gobj/get props "payload")]
     (r/as-element
      [mui/paper {:style {:padding "1em"}}

       ;; Tooltip header
       (when-not hide-header?
         [mui/typography
          {:variant "body2" :align "center" :style {:margin-bottom "0.25em"}}
          label])

       ;; Content table
       [mui/table {:style {:width "350"} :padding "normal" :size "small"}
        (->> payload
             payload-fn
             (sort-by sort-fn)
             (map
              (fn [{:keys [label value icon color]}]
                [mui/table-row #_{:style {:height "24px"}}
                 (when icon
                   [mui/table-cell {:padding "none"}
                    [mui/icon {:style {:color color}}
                     icon]])
                 [mui/table-cell
                  [mui/typography {:variant "caption"}
                   label]]
                 [mui/table-cell
                  [mui/typography {:variant "caption"}
                   value]]]))
             (into [mui/table-body]))]]))))

(defn- get-population [payload]
  (when (> (count payload) 0)
    (gobj/getValueByKeys payload 0 "payload" "population")))

(defn finance-tooltip [labels props]
  (let [payload-fn (fn [payload]
                     (let [population (get-population payload)]
                       (conj
                        (->> payload
                             (map
                              (fn [^js obj]
                                {:color (gobj/get obj "color")
                                 :value (utils/round-safe (gobj/get obj "value"))
                                 :icon  (if (gobj/get obj "stroke")
                                          (legend-icons "line")
                                          (legend-icons "rect"))
                                 :label (labels (keyword (gobj/get obj "name")))})))
                        {:color "white"
                         :icon  "__dummy__"
                         :value population
                         :label (:population labels)})))]
    (tooltip payload-fn labels props)))

(defn city-finance-chart
  [{:keys [data metrics labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 500}
   (-> [:> ComposedChart {:data data :on-click on-click}
        [:> Legend {:content (fn [^js props] (legend labels props))}]
        [:> Tooltip {:content (fn [^js props] (finance-tooltip labels props))}]
        [:> YAxis {:tick font-styles}]
        [:> XAxis {:dataKey :year :tick font-styles}]]
       (into
        (for [m    metrics
              :let [k (keyword m)]]
          [:> Bar {:dataKey k :label false :fill (get colors k)}]))
       (into
        (for [m    metrics
              :let [k1 (keyword m)
                    k2 (keyword (str m "-avg"))]]
          [:> Line
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

(defn age-structure-chart
  [{:keys [data labels]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> BarChart {:data data}
     [:> Legend {:content (fn [^js props] (legend labels props))}]
     [:> Tooltip {:content (fn [^js props] (age-structure-tooltip labels props))}]
     [:> YAxis
      {:tick font-styles
       :label
       {:value    (:y-axis labels)
        :angle    -90
        :offset   10
        :position "insideBottomLeft"}}]
     [:> XAxis {:dataKey :construction-year :tick font-styles}]]
    (for [k (->> data (mapcat keys) set (remove #(= :construction-year %)) sort)]
      [:> Bar
       {:stackId "a"
        :dataKey k
        :label   false
        :fill    (get colors k)}]))])

(def ugly-keys
  #{:length-km-pc :length-km-avg :sites-count-p1000c :area-m2-pc :area-m2-avg})

(defn sports-stats-tooltip [labels props]
  (let [->formatter (fn [k] (if (ugly-keys k) utils/round-safe identity))
        payload-fn  (fn [payload]
                      (let [entry (-> payload
                                      first
                                      (gobj/get "payload")
                                      (js->clj :keywordize-keys true))]
                        (->> entry
                             (reduce
                              (fn [res [k v]]
                                (if-let [label (labels k)]
                                  (conj res {:label label
                                             :value ((->formatter k) v)})
                                  res))
                              []))))]
    (tooltip payload-fn labels props)))

(defn sports-stats-chart
  [{:keys [data labels metric grouping on-click]}]
  (let [margin     {:top 5 :right 100 :bottom 5 :left 100}
        y-axis-key (if (= "location.city.city-code" grouping)
                     :city-name
                     :type-name)
        precision (if (= metric "length-km-pc") 8 2)
        formatter (when (ugly-keys (keyword metric)) #(utils/round-safe % precision))]
    [:> ResponsiveContainer {:width "100%" :height (+ 60 (* 30 (count data)))}
     [:> BarChart
      {:data     data
       :layout   "vertical"
       :margin   margin
       :on-click on-click}
      [:> Legend {:content (fn [^js props] (legend labels props))}]
      [:> Tooltip {:content (fn [^js props] (sports-stats-tooltip labels props))}]
      [:> XAxis {:tick font-styles :type "number"}]
      [:> YAxis {:dataKey y-axis-key :type "category" :tick font-styles}]
      [:> Bar {:dataKey (keyword metric) :fill "orange"}
       [:> LabelList {:position "right" :formatter formatter}]]]]))

(defn angled-tick [props]
  (let [x       (gobj/get props "x")
        y       (gobj/get props "y")
        payload (gobj/get props "payload")]
    (r/as-element
     [:g {:transform (gstring/format "translate(%d,%d)" x y)}
      [:text
       {:x 0 :y 0 :dy 16 :textAnchor "end" :transform "rotate(-45)"
        :font-family "Lato"}
       (gobj/get payload "value")]])))

(defn finance-chart
  [{:keys [data metrics labels on-click]}]
  (let [x-interval (if (> (count data) 50) 5 0)]
    [:> ResponsiveContainer {:width "100%" :height 500}
     (->
      [:> ComposedChart {:data data :on-click on-click :margin {:bottom 135}}
       [:> Legend {:content (fn [^js props] (legend labels props)) :verticalAlign "top"}]
       [:> Tooltip {:content (fn [^js props] (finance-tooltip labels props))}]
       [:> YAxis {:tick font-styles}]
       [:> XAxis {:dataKey :region :tick angled-tick :interval x-interval}]]
      (into
       (for [metric metrics]
         [:> Bar
          {:dataKey metric :label false :fill (get colors (keyword metric))}])))]))

(defn finance-ranking-chart
  [{:keys [data labels metric]}]
  (let [margin     {:top 5 :right 100 :bottom 5 :left 100}
        y-axis-key :region]
    [:> ResponsiveContainer {:width "100%" :height (+ 60 (* 48 (count data)))}
     [:> BarChart {:data data :layout "vertical" :margin margin}
      [:> Legend {:content (fn [^js props] (legend labels props))}]
      [:> Tooltip {:content (fn [^js props] (finance-tooltip labels props))}]
      [:> XAxis {:tick font-styles :type "number"}]
      [:> YAxis {:dataKey y-axis-key :type "category" :tick font-styles}]
      [:> Bar {:dataKey (keyword metric) :fill (get colors (keyword metric))}
       [:> LabelList {:position "right"}]]]]))

(defn labeled-tooltip
  ([labels ^js props]
   (labeled-tooltip labels :label false props))
  ([labels sort-fn ^js props]
   (labeled-tooltip labels sort-fn false props))
  ([labels sort-fn hide-header? ^js props]
   (let [payload-fn (fn [^js payload]
                      (let [entry (-> payload
                                      first
                                      (gobj/get "payload")
                                      (js->clj :keywordize-keys true))]
                        (->> entry
                             (reduce
                              (fn [res [k v]]
                                (if-let [label (labels k)]
                                  (conj res {:label label :value (if (nil? v) "<10" v)})
                                  res))
                              []))))]
     (tooltip payload-fn labels sort-fn hide-header? props))))

(def subsidies-tooltip labeled-tooltip)

(defn subsidies-chart
  [{:keys [data labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 500}
   [:> BarChart {:data data :on-click on-click}
    [:> Legend {:content (fn [^js props] (legend labels props))}]
    [:> Tooltip {:content (fn [^js props] (labeled-tooltip labels props))}]
    [:> YAxis {:tick font-styles :data-key :amount}]
    [:> XAxis {:dataKey :group :tick font-styles}]
    [:> Bar {:dataKey :amount :label false :fill "#0a9bff"}]]])

(defn subsidies-ranking-chart
  [{:keys [data labels on-click]}]
  (let [margin     {:top 5 :right 100 :bottom 5 :left 100}
        y-axis-key :group]
    [:> ResponsiveContainer {:width "100%" :height (+ 60 (* 48 (count data)))}
     [:> BarChart {:data data :layout "vertical" :margin margin :on-click on-click}
      [:> Legend {:content (fn [^js props] (legend labels props))}]
      [:> Tooltip {:content (fn [^js props] (labeled-tooltip labels props))}]
      [:> XAxis {:tick font-styles :type "number"}]
      [:> YAxis {:dataKey y-axis-key :type "category" :tick font-styles}]
      [:> Bar {:dataKey :amount :fill "#0a9bff"}
       [:> LabelList {:position "right"}]]]]))

(defn ->payload [evt]
  (when-let [arr (gobj/get evt "activePayload")]
    (let [obj (gobj/getValueByKeys arr 0 "payload")]
      (js->clj obj :keywordize-keys true))))

(def zone-colors
  {:zone1 "#008000"
   :zone2 "#2db92d"
   :zone3 "#73e600"
   :zone4 "#aaaaaa"})

(def age-groups
  {:age-0-14  "#006190"
   :age-15-64 "#5b8caa"
   :age-65-   "#9cb7c5"})

(def school-types
  {:vaka        "#006190"
   :peruskoulut "#457da1"
   :lukiot      "#9cb7c5"
   :perus+lukio "#709ab3"
   :erityis     "#c8d4d9"})

(defn population-bar-chart
  [{:keys [data labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> BarChart {:data data :layout "horizontal" :on-click on-click}
     [:> Legend {:content (fn [^js props] (legend labels props))}]
     [:> Tooltip {:content (fn [^js props] (subsidies-tooltip labels props))}]
     [:> XAxis {:dataKey "group" :tick font-styles :type "category"}]
     [:> YAxis {:tick font-styles}]]
    (for [zone [:zone1 :zone2 :zone3 :zone4]]
      [:> Bar {:dataKey zone :stackId "a" :fill (zone-colors zone)}]))])

(defn fixed-tick [props]
  (let [x       (gobj/get props "x")
        y       (gobj/get props "y")
        payload (gobj/get props "payload")
        v       (gobj/get payload "value")]
    (r/as-element
     [:g {:transform (gstring/format "translate(%d,%d)" x y)}
      [:text
       {:x 0 :y 0 :dy 20 :textAnchor "middle" :font-family "Lato"
        :style {::font-weight "normal" :fill "rgb(102, 102, 102)"}}
       (subs v 2)]])))

(defn population-area-chart
  [{:keys [data labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> Legend {:content (fn [^js props] (legend labels props))}]
     [:> Tooltip {:content (fn [^js props] (subsidies-tooltip labels props))}]
     [:> XAxis {:dataKey "zone" :tick fixed-tick :type "category"}]
     [:> YAxis {:tick font-styles}]]
    (for [k [:age-0-14 :age-15-64 :age-65-]]
      [:> Area
       {:dataKey k :stackId "a" :fill (age-groups k) :stroke (age-groups k)}]))])

(defn population-area-chart-v2
  [{:keys [data labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> Legend {:content (fn [^js props] (legend labels props))}]
     [:> Tooltip {:content (fn [^js props] (subsidies-tooltip labels props))}]
     [:> XAxis {:dataKey "zone" :tick true :type "category"}]
     [:> YAxis {:tick font-styles}]]
    (for [k [:age-0-14 :age-15-64 :age-65-]]
      [:> Area
       {:dataKey k :stackId "a" :fill (age-groups k) :stroke (age-groups k)}]))])

(defn sports-sites-bar-chart
  [{:keys [data labels on-click zones zone-colors]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> BarChart {:data data :layout "horizontal" :on-click on-click}
     [:> Legend {:content (fn [^js props] (legend labels (comp parse-number :label) props))}]
     [:> Tooltip {:content (fn [^js props] (subsidies-tooltip labels parse-number props))}]
     [:> XAxis {:dataKey "type" :tick font-styles :type "category"}]
     [:> YAxis {:tick font-styles}]]
    (for [zone (->> zones (map :id) sort)]
      [:> Bar {:dataKey zone :stackId "a" :fill (zone-colors zone)}]))])

#_(defn sports-sites-area-chart
  [{:keys [data labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> Legend {:content (fn [^js props] (legend labels props))}]
     [:> Tooltip {:content (fn [^js props] (partial subsidies-tooltip labels props))}]
     [:> XAxis {:dataKey "zone" :tick true :type "category"}]
     [:> YAxis {:dataKey "count" :tick font-styles}]]
    (for [type (->> data (map :type))]
      [:> Bar {:dataKey type :stackId "a"}]))])

#_(defn schools-bar-chart
  [{:keys [data labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> BarChart {:data data :layout "horizontal" :on-click on-click}
     [:> Legend {:content (fn [^js props] (legend labels props))}]
     [:> Tooltip {:content (fn [^js props] (subsidies-tooltip labels props))}]
     [:> XAxis {:dataKey "type" :tick font-styles :type "category"}]
     [:> YAxis {:tick font-styles}]]
    (for [zone [:zone1 :zone2 :zone3 :zone4]]
      [:> Bar {:dataKey zone :stackId "a" :fill (zone-colors zone)}]))])

(defn schools-area-chart
  [{:keys [data labels on-click]}]
  [:> ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> Legend {:content (fn [^js props] (legend labels props))}]
     [:> Tooltip {:content (fn [^js props] (subsidies-tooltip labels props))}]
     [:> XAxis {:dataKey "zone" :tick true :type "category"}]
     [:> YAxis {:tick font-styles}]]
    (for [k [:vaka :lukiot :perus+lukio :peruskoulut :erityis]]
      [:> Area
       {:dataKey k :stackId "a" :fill (school-types k) :stroke (school-types k)}]))])
