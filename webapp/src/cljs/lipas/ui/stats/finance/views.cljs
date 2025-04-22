(ns lipas.ui.stats.finance.views
  (:require [lipas.ui.charts :as charts]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.stats.common :as common]
            [lipas.ui.stats.finance.events :as events]
            [lipas.ui.stats.finance.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn year-selector
  [{:keys [tr] :as props}]
  [lui/year-selector
   (merge
     props
     {:label (tr :actions/select-year)
      :style common/select-style})])

(defn unit-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        units  (<== [::subs/units])]
    [lui/select
     {:items     units
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-unit)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn service-selector [{:keys [tr value on-change]}]
  (let [locale   (tr)
        services (<== [::subs/city-services])]
    [lui/select
     {:items     services
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-city-service)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn metrics-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        metrics (<== [::subs/metrics])]
    [lui/multi-select
     {:items     metrics
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-metrics)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn metric-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        metrics (<== [::subs/metrics])]
    [lui/select
     {:items     metrics
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-metric)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn grouping-selector [{:keys [tr value on-change]}]
  (let [locale    (tr)
        groupings (<== [::subs/groupings])]
    [lui/select
     {:items     groupings
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-grouping)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn region-selector [props]
  (let [regions (<== [:lipas.ui.stats.subs/regions])]
    [lui/region-selector (assoc props :regions regions)]))

(defn view []
  (let [tr             (<== [:lipas.ui.subs/translator])
        cities         (<== [::subs/selected-cities])
        year           (<== [::subs/selected-year])
        service        (<== [::subs/selected-city-service])
        unit           (<== [::subs/selected-unit])
        metrics        (<== [::subs/selected-metrics])
        grouping       (<== [::subs/selected-grouping])
        ranking-metric (<== [::subs/selected-ranking-metric])
        ranking-data   (<== [::subs/ranking-data])
        chart-type     (<== [::subs/chart-type])
        data           (<== [::subs/data])
        labels         (<== [::subs/labels])
        headers        (<== [::subs/headers])
        view           (<== [::subs/selected-view])]

    [mui/grid {:container true :spacing 4}

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/finance-stats)]]

     ;; Disclaimers
     [mui/grid {:item true :xs 12}
      [common/disclaimer
       {:label (tr :stats/disclaimer-headline)
        :texts [(tr :stats/finance-disclaimer)]}]]

     [mui/grid {:item true :xs 12}

      [mui/grid {:container true :spacing 4}

       ;; Region selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-cities)]
        [region-selector
         {:value     cities
          :on-change #(==> [::events/select-cities %])}]]

       ;; Unit selector
       [mui/grid {:item true}
        [unit-selector
         {:tr tr :value unit :on-change #(==> [::events/select-unit %])}]]

       ;; City service selector
       [mui/grid {:item true}
        [service-selector
         {:tr        tr
          :value     service
          :on-change #(==> [::events/select-city-service %])}]]

       ;; Years selector
       [mui/grid {:item true}
        [year-selector
         {:tr        tr
          :years     (range 2000 (inc 2023))
          :value     year
          :on-change #(==> [::events/select-year %])}]]

       ;; Metrics selector
       (when (= "comparison" chart-type)
         [mui/grid {:item true}
          [metrics-selector
           {:tr        tr
            :value     metrics
            :on-change #(==> [::events/select-metrics %])}]])

       ;; Metric selector
       (when (= "ranking" chart-type)
         [mui/grid {:item true}
          [metric-selector
           {:tr        tr
            :value     ranking-metric
            :on-change #(==> [::events/select-ranking-metric %])}]])

       ;; Grouping selector
       [mui/grid {:item true}
        [grouping-selector
         {:tr        tr
          :value     grouping
          :on-change #(==> [::events/select-grouping %])}]]

       ;; Chart type selector
       [mui/grid {:item true}
        [mui/tooltip
         {:title
          (if (= "ranking" chart-type)
            (tr :stats/show-comparison)
            (tr :stats/show-ranking))}
         [mui/icon-button {:on-click #(==> [::events/toggle-chart-type])}
          [mui/icon {:font-size "large" :color (if (= "ranking" chart-type)
                                                 "secondary"
                                                 "inherit")}
           "sort"]]]]

       ;; Clear filters button
       (when (not-empty cities)
         [mui/grid {:item true :xs 12}
          [mui/button
           {:color    "secondary"
            :on-click #(==> [::events/clear-filters])}
           (tr :search/clear-filters)]])]]

     ;; Tabs for choosing between chart/table views
     [mui/grid {:container true :item true :xs 12}

      [mui/grid {:item true}
       [common/view-tabs
        {:value     view
         :on-change #(==> [::events/select-view %2])}]]

      ;; Download Excel button
      [common/download-excel-button
       {:tr       tr
        :on-click #(==> [::events/download-excel data headers])}]]

     ;; Chart
     (when (= "chart" view)
       (let [on-click (fn [evt]
                        (when-let [m (charts/->payload evt)]
                          (==> [::events/select-filters m grouping])))]

         ;; Comparison chart
         (if (= "comparison" chart-type)
           [mui/grid {:item true
                      :xs 12
                      :sx {:width 0}}
            [charts/finance-chart
             {:data     data
              :metrics  metrics
              :on-click on-click
              :labels   labels}]]

           ;; Ranking chart
           [mui/grid {:container true :item true :direction "column" :spacing 4}
            [mui/grid {:item true
                       :xs 12
                       :md 6
                       :sx {:width 0}}
             [charts/finance-ranking-chart
              {:data     ranking-data
               :metric   ranking-metric
               :on-click on-click
               :labels   labels}]]])))

     ;; Table
     (when (= "table" view)
       [mui/grid {:item true :xs 12}
        [lui/table
         {:headers headers :items data}]])]))
