(ns lipas.ui.charts
  (:require
   ["recharts" :as rc]
   [cljs.pprint :as pprint]
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
   [:> rc/ResponsiveContainer {:width "100%" :height 300}
    [:> rc/BarChart {:data data}
     [:> rc/CartesianGrid]
     [:> rc/Legend {:wrapperStyle font-styles}]
     [:> rc/Tooltip tooltip-styles]
     [:> rc/YAxis {:tick font-styles}]
     [:> rc/XAxis {:dataKey :name :label false :tick false}]
     [:> rc/Bar {:dataKey energy-label :label false :fill (get colors energy)}]]]))

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

(defn energy-totals-gauge
  [{:keys [data energy-type]}]
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
            [mui/grid {:container true :justify "center"}]))))))

(defn tooltip
  "`payload-fn` should return a map with
  keys :label :value (:icon :color)."
  ([payload-fn labels props] (tooltip payload-fn labels :label props))
  ([payload-fn labels sort-fn props]
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
             (sort-by sort-fn)
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
                              (fn [obj]
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

(defn age-structure-chart
  [{:keys [data labels]}]
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
    [:> rc/ResponsiveContainer {:width "100%" :height (+ 60 (* 30 (count data)))}
     [:> rc/BarChart
      {:data     data
       :layout   "vertical"
       :margin   margin
       :on-click on-click}
      [:> rc/Legend {:content (partial legend labels)}]
      [:> rc/Tooltip {:content (partial sports-stats-tooltip labels)}]
      [:> rc/XAxis {:tick font-styles :type "number"}]
      [:> rc/YAxis {:dataKey y-axis-key :type "category" :tick font-styles}]
      [:> rc/Bar {:dataKey (keyword metric) :fill "orange"}
       [:> rc/LabelList {:position "right" :formatter formatter}]]]]))

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
    [:> rc/ResponsiveContainer {:width "100%" :height 500}
     (->
      [:> rc/ComposedChart {:data data :on-click on-click :margin {:bottom 135}}
       [:> rc/Legend {:content (partial legend labels) :verticalAlign "top"}]
       [:> rc/Tooltip {:content (partial finance-tooltip labels)}]
       [:> rc/YAxis {:tick font-styles}]
       [:> rc/XAxis {:dataKey :region :tick angled-tick :interval x-interval}]]
      (into
       (for [metric metrics]
         [:> rc/Bar
          {:dataKey metric :label false :fill (get colors (keyword metric))}])))]))

(defn finance-ranking-chart
  [{:keys [data labels metric]}]
  (let [margin     {:top 5 :right 100 :bottom 5 :left 100}
        y-axis-key :region]
    [:> rc/ResponsiveContainer {:width "100%" :height (+ 60 (* 48 (count data)))}
     [:> rc/BarChart {:data data :layout "vertical" :margin margin}
      [:> rc/Legend {:content (partial legend labels)}]
      [:> rc/Tooltip {:content (partial finance-tooltip labels)}]
      [:> rc/XAxis {:tick font-styles :type "number"}]
      [:> rc/YAxis {:dataKey y-axis-key :type "category" :tick font-styles}]
      [:> rc/Bar {:dataKey (keyword metric) :fill (get colors (keyword metric))}
       [:> rc/LabelList {:position "right"}]]]]))

(defn subsidies-tooltip
  ([labels props] (subsidies-tooltip labels :label props))
  ([labels sort-fn props]
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
     (tooltip payload-fn labels sort-fn props))))

(defn subsidies-chart
  [{:keys [data labels on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 500}
   [:> rc/BarChart {:data data :on-click on-click}
    [:> rc/Legend {:content (partial legend labels)}]
    [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
    [:> rc/YAxis {:tick font-styles :data-key :amount}]
    [:> rc/XAxis {:dataKey :group :tick font-styles}]
    [:> rc/Bar {:dataKey :amount :label false :fill "#0a9bff"}]]])

(defn subsidies-ranking-chart
  [{:keys [data labels on-click]}]
  (let [margin     {:top 5 :right 100 :bottom 5 :left 100}
        y-axis-key :group]
    [:> rc/ResponsiveContainer {:width "100%" :height (+ 60 (* 48 (count data)))}
     [:> rc/BarChart {:data data :layout "vertical" :margin margin :on-click on-click}
      [:> rc/Legend {:content (partial legend labels)}]
      [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
      [:> rc/XAxis {:tick font-styles :type "number"}]
      [:> rc/YAxis {:dataKey y-axis-key :type "category" :tick font-styles}]
      [:> rc/Bar {:dataKey :amount :fill "#0a9bff"}
       [:> rc/LabelList {:position "right"}]]]]))

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
  {:age-0-14  "#80bfff"
   :age-15-64 "#1e90ff"
   :age-65-   "#0073e6"})

(def school-types
  {:vaka        "#80bfff"
   :peruskoulut "#1e90ff"
   :lukiot      "#0073e6"
   :perus+lukio "#73e600"
   :erityis     "#2db92d"})

(defn population-bar-chart
  [{:keys [data labels on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/BarChart {:data data :layout "horizontal" :on-click on-click}
     [:> rc/Legend {:content (partial legend labels)}]
     [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
     [:> rc/XAxis {:dataKey "group" :tick font-styles :type "category"}]
     [:> rc/YAxis {:tick font-styles}]]
    (for [zone [:zone1 :zone2 :zone3 :zone4]]
      [:> rc/Bar {:dataKey zone :stackId "a" :fill (zone-colors zone)}]))])

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
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> rc/Legend {:content (partial legend labels)}]
     [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
     [:> rc/XAxis {:dataKey "zone" :tick fixed-tick :type "category"}]
     [:> rc/YAxis {:tick font-styles}]]
    (for [k [:age-0-14 :age-15-64 :age-65-]]
      [:> rc/Area
       {:dataKey k :stackId "a" :fill (age-groups k) :stroke (age-groups k)}]))])

(defn population-area-chart-v2
  [{:keys [data labels on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> rc/Legend {:content (partial legend labels)}]
     [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
     [:> rc/XAxis {:dataKey "zone" :tick true :type "category"}]
     [:> rc/YAxis {:tick font-styles}]]
    (for [k [:age-0-14 :age-15-64 :age-65-]]
      [:> rc/Area
       {:dataKey k :stackId "a" :fill (age-groups k) :stroke (age-groups k)}]))])

(defn sports-sites-bar-chart
  [{:keys [data labels on-click zones zone-colors]}]
  (prn zones)
  (prn zone-colors)
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/BarChart {:data data :layout "horizontal" :on-click on-click}
     [:> rc/Legend {:content (partial legend labels (comp parse-number :label))}]
     [:> rc/Tooltip {:content (partial subsidies-tooltip labels parse-number)}]
     [:> rc/XAxis {:dataKey "type" :tick font-styles :type "category"}]
     [:> rc/YAxis {:tick font-styles}]]
    (for [zone (->> zones (map :id) sort)]
      [:> rc/Bar {:dataKey zone :stackId "a" :fill (zone-colors zone)}]))])

(defn sports-sites-area-chart
  [{:keys [data labels on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> rc/Legend {:content (partial legend labels)}]
     [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
     [:> rc/XAxis {:dataKey "zone" :tick true :type "category"}]
     [:> rc/YAxis {:dataKey "count" :tick font-styles}]]
    (for [type (->> data (map :type))]
      [:> rc/Bar {:dataKey type :stackId "a"}]))])

(defn schools-bar-chart
  [{:keys [data labels on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/BarChart {:data data :layout "horizontal" :on-click on-click}
     [:> rc/Legend {:content (partial legend labels)}]
     [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
     [:> rc/XAxis {:dataKey "type" :tick font-styles :type "category"}]
     [:> rc/YAxis {:tick font-styles}]]
    (for [zone [:zone1 :zone2 :zone3 :zone4]]
      [:> rc/Bar {:dataKey zone :stackId "a" :fill (zone-colors zone)}]))])

(defn schools-area-chart
  [{:keys [data labels on-click]}]
  [:> rc/ResponsiveContainer {:width "100%" :height 300}
   (into
    [:> rc/AreaChart {:data data :layout "horizontal" :on-click on-click}
     [:> rc/Legend {:content (partial legend labels)}]
     [:> rc/Tooltip {:content (partial subsidies-tooltip labels)}]
     [:> rc/XAxis {:dataKey "zone" :tick true :type "category"}]
     [:> rc/YAxis {:tick font-styles}]]
    (for [k [:vaka :lukiot :perus+lukio :peruskoulut :erityis]]
      [:> rc/Area
       {:dataKey k :stackId "a" :fill (school-types k) :stroke (school-types k)}]))])
