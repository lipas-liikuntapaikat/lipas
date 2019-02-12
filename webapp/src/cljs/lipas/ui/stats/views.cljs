(ns lipas.ui.stats.views
  (:require
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.stats.events :as events]
   [lipas.ui.stats.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(def select-style {:min-width "170px"})

(defn view-tabs [{:keys [value on-change]}]
  [mui/tabs {:value value :on-change on-change}
   [mui/tab {:value "chart" :icon (r/as-element [mui/icon "bar_chart"])}]
   [mui/tab {:value "table" :icon (r/as-element [mui/icon "table_chart"])}]])

(defn download-excel-button [{:keys [tr on-click]}]
  [mui/button
   {:style    {:margin "1em"}
    :variant  "outlined"
    :color    "secondary"
    :on-click on-click}
   (tr :actions/download-excel)])

(defn service-selector [{:keys [tr value on-change]}]
  (let [locale   (tr)
        services (<== [::subs/finance-city-services])]
    ^{:key value}
    [lui/select
     {:items     services
      :value     value
      :style     select-style
      :label     (tr :stats/select-city-service)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn finance-metrics-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        metrics (<== [::subs/finance-metrics])]
    ^{:key value}
    [lui/multi-select
     {:items        metrics
      :value        value
      :style        select-style
      :label        (tr :stats/select-metrics)
      :value-fn     first
      :label-fn     (comp locale second)
      :on-change    on-change}]))

(defn unit-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        units  (<== [::subs/finance-units])]
    ^{:key value}
    [lui/select
     {:items     units
      :value     value
      :style     select-style
      :label     (tr :stats/select-unit)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn years-selector [{:keys [tr value on-change]}]
  [lui/year-selector
   {:label        (tr :stats/select-years)
    :multi?       true
    :render-value (fn [vs]
                    (let [vs (sort vs)]
                      (condp = (count vs)
                        0 "-"
                        1 (first vs)
                        2 (str (first vs) ", " (second vs))
                        (str (first vs) " ... " (last vs)))))
    :value        value
    :style        select-style
    :years        (range 2000 utils/this-year)
    :on-change    on-change}])

(defn grouping-selector [{:keys [tr value on-change]}]
  (let [locale    (tr)
        groupings (<== [::subs/age-structure-groupings])]
    ^{:key value}
    [lui/select
     {:items     groupings
      :value     value
      :style     select-style
      :label     (tr :stats/select-grouping)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn interval-selector [{:keys [tr value on-change]}]
  ^{:key value}
  [lui/number-selector
   {:items     [1 5 10]
    :value     value
    :style     select-style
    :unit      (tr :duration/years-short)
    :label     (tr :stats/select-interval)
    :on-change on-change}])

(defn sports-stats-metrics-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        metrics (<== [::subs/sports-stats-metrics])]
    ^{:key value}
    [lui/select
     {:items        metrics
      :value        value
      :style        select-style
      :label        (tr :stats/select-metric)
      :value-fn     first
      :label-fn     (comp locale second)
      :on-change    on-change}]))

(defn sports-stats []
  (let [tr      (<== [:lipas.ui.subs/translator])
        cities  (<== [::subs/selected-sports-stats-cities])
        types   (<== [::subs/selected-sports-stats-types])
        metric  (<== [::subs/selected-sports-stats-metric])
        view    (<== [::subs/selected-sports-stats-view])
        data    (<== [::subs/sports-stats-data])
        labels  (<== [::subs/sports-stats-labels])
        headers (<== [::subs/sports-stats-headers])]

    [mui/grid {:container true :spacing 16}

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/sports-stats)]]

     [mui/grid {:item true :xs 12}

      [mui/grid {:container true :spacing 16 :style {:margin-bottom "1em"}}

       ;; Region selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-cities)]
        [lui/region-selector
         {:value     cities
          :on-change #(==> [::events/select-sports-stats-cities %])}]]

       ;; Type selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-types)]
        [lui/type-category-selector
         {:tr        tr
          :value     types
          :on-change #(==> [::events/select-sports-stats-types %])}]]

       ;; Clear filters button
       (when (or (not-empty types) (not-empty cities))
         [mui/grid {:item true :xs 12}
          [mui/button
           {:color    "secondary"
            :on-click #(==> [::events/clear-sports-stats-filters])}
           (tr :search/clear-filters)]])]

      ;; Metric selector
      [mui/grid {:item true}
       [sports-stats-metrics-selector
        {:tr        tr
         :value     metric
         :on-change #(==> [::events/select-sports-stats-metric %])}]]]

     ;; Table
     (when (= view "table")
       [mui/grid {:item true :xs 12}
        [lui/table
         {:headers headers :items data}]])

     ;; Chart
     (when (= view "chart")
       [mui/grid {:item true :xs 12}
        [charts/sports-stats-chart
         {:data data :labels labels :metric metric}]])

     ;; Tabs for choosing between chart/table views
     [mui/grid {:item true}
      [view-tabs
       {:value     view
        :on-change #(==> [::events/select-sports-stats-view %2])}]]

     ;; Download Excel button
     [download-excel-button
      {:tr       tr
       :on-click #(==> [::events/download-sports-stats-excel data headers])}]]))

(defn age-structure-stats []
  (let [tr       (<== [:lipas.ui.subs/translator])
        regions  (<== [::subs/selected-age-structure-cities])
        types    (<== [::subs/selected-age-structure-types])
        grouping (<== [::subs/selected-age-structure-grouping])
        interval (<== [::subs/selected-age-structure-interval])
        view     (<== [::subs/selected-age-structure-view])
        data     (<== [::subs/age-structure-data])
        labels   (<== [::subs/age-structure-labels])
        headers  (<== [::subs/age-structure-headers])]

    [mui/grid {:container true :spacing 16}

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/age-structure)]]

     [mui/grid {:item true}
      [mui/grid {:container true :spacing 16}

       ;; Region selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :actions/select-cities)]
        [lui/region-selector
         {:value     regions
          :on-change #(==> [::events/select-age-structure-cities %])}]]

       ;; Type selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :actions/select-types)]
        [lui/type-category-selector
         {:tr        tr
          :value     types
          :on-change #(==> [::events/select-age-structure-types %])}]]

       ;; Clear filters button
       (when (or (not-empty types) (not-empty regions))
         [mui/grid {:item true :xs 12}
          [mui/button
           {:color    "secondary"
            :on-click #(==> [::events/clear-age-structure-filters])}
           (tr :search/clear-filters)]])

       ;; Interval selector
       [mui/grid {:item true}
        [interval-selector
         {:tr        tr
          :value     interval
          :on-change #(==> [::events/select-age-structure-interval %])}]]

       ;; Grouping selector
       [mui/grid {:item true}
        [grouping-selector
         {:tr        tr
          :value     grouping
          :on-change #(==> [::events/select-age-structure-grouping %])}]]]]

     ;; Table
     (when (= view "table")
       [mui/grid {:item true :xs 12}
        [lui/table
         {:headers headers :items data}]])

     ;; Chart
     (when (= view "chart")
       [mui/grid {:item true :xs 12}
        [charts/age-structure-chart {:data data :labels labels}]])

     ;; Tabs for choosing between chart/table views
     [mui/grid {:item true}
      [view-tabs
       {:value     view
        :on-change #(==> [::events/select-age-structure-view %2])}]]

     ;; Download Excel button
     [download-excel-button
      {:tr       tr
       :on-click #(==> [::events/download-age-structure-excel data headers])}]]))

(defn city-stats []
  (let [tr           (<== [:lipas.ui.subs/translator])
        cities       (<== [::subs/selected-cities])
        unit         (<== [::subs/selected-finance-unit])
        metrics      (<== [::subs/selected-finance-metrics])
        service      (<== [::subs/selected-finance-city-service])
        years        (<== [::subs/selected-finance-years])
        view         (<== [::subs/selected-finance-view])
        finance-data (<== [::subs/finance-data])
        labels       (<== [::subs/finance-labels])
        headers      (<== [::subs/finance-headers])]

    [mui/grid {:container true :spacing 16}

     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h3"}
       (tr :stats/city-stats)]]

     ;; City selector
     [mui/grid {:item true :xs 12}
      [lui/city-selector-single
       {:tr        tr
        :value     (first cities)
        :on-change #(==> [::events/select-finance-cities [%]])}]]

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/finance-stats)]]

     ;; Selectors
     [mui/grid {:item true}
      [mui/grid {:container true :spacing 16}

       ;; Unit selector
       [mui/grid {:item true}
        [unit-selector
         {:tr tr :value unit :on-change #(==> [::events/select-finance-unit %])}]]

       ;; Metrics selector
       [mui/grid {:item true}
        [finance-metrics-selector
         {:tr        tr
          :value     metrics
          :on-change #(==> [::events/select-finance-metrics %])}]]

       ;; City service selector
       [mui/grid {:item true}
        [service-selector
         {:tr        tr
          :value     service
          :on-change #(==> [::events/select-finance-city-service %])}]]

       ;; Years selector
       [mui/grid {:item true}
        [years-selector
         {:tr        tr
          :value     years
          :on-change #(==> [::events/select-finance-years %])}]]]]

     ;; Chart
     (when (= view "chart")
       [mui/grid {:item true :xs 12}
        [charts/finance-chart
         {:metrics metrics :data finance-data :labels labels}]])

     ;; Table
     (when (= view "table")
       [mui/grid {:item true :xs 12}
        [lui/table
         {:headers headers :items finance-data}]])

     ;; Tabs for choosing between chart/table views
     [mui/grid {:item true}
      [view-tabs
       {:value     view
        :on-change #(==> [::events/select-finance-view %2])}]]

     ;; Download Excel button
     [download-excel-button
      {:tr       tr
       :on-click #(==> [::events/download-finance-excel finance-data headers])}]]))

(defn create-panel []
  (let [tr  (<== [:lipas.ui.subs/translator])
        tab (<== [::subs/selected-tab])]

    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/paper {:square true :style {:padding "1em"}}

       ;; Tabs for choosing between different stats pages
       [mui/grid {:item true}
        [mui/tabs {:value tab :on-change #(==> [::events/navigate %2])}
         [mui/tab {:value "sports-stats" :label (tr :stats/sports-stats)}]
         [mui/tab {:value "age-structure-stats" :label (tr :stats/age-structure-stats)}]
         [mui/tab {:value "city-stats" :label (tr :stats/city-stats)}]]]

       [mui/grid {:item true}
        (condp = tab
          "sports-stats"        [sports-stats]
          "age-structure-stats" [age-structure-stats]
          "city-stats"          [city-stats])]]]]))

(defn main [tr]
  [create-panel])
