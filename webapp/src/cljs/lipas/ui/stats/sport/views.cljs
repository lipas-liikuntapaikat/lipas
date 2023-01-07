(ns lipas.ui.stats.sport.views
  (:require
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.stats.common :as common]
   [lipas.ui.stats.sport.events :as events]
   [lipas.ui.stats.sport.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn metrics-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        metrics (<== [::subs/metrics])]
    ^{:key value}
    [lui/select
     {:items        metrics
      :value        value
      :style        common/select-style
      :label        (tr :stats/select-metric)
      :value-fn     first
      :label-fn     (comp locale second)
      :on-change    on-change}]))

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

(defn view []
  (let [tr       (<== [:lipas.ui.subs/translator])
        cities   (<== [::subs/selected-cities])
        types    (<== [::subs/selected-types])
        metric   (<== [::subs/selected-metric])
        grouping (<== [::subs/selected-grouping])
        view     (<== [::subs/selected-view])
        data     (<== [::subs/data])
        labels   (<== [::subs/labels])
        headers  (<== [::subs/headers])]

    [mui/grid {:container true :spacing 4}

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/sports-stats)]]

     ;; Disclaimers
     [mui/grid {:item true :xs 12}
      [common/disclaimer
       {:label (tr :stats/disclaimer-headline)
        :texts [(tr :stats/general-disclaimer-1)
                (tr :stats/general-disclaimer-2)
                (tr :stats/general-disclaimer-3)]}]]

     [mui/grid {:item true :xs 12}

      [mui/grid {:container true :spacing 4}

       ;; Region selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-cities)]
        [lui/region-selector
         {:value     cities
          :on-change #(==> [::events/select-cities %])}]]

       ;; Type selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-types)]
        [lui/type-category-selector
         {:tr        tr
          :value     types
          :on-change #(==> [::events/select-types %])}]]

       ;; Clear filters button
       (when (or (not-empty types) (not-empty cities))
         [mui/grid {:item true :xs 12}
          [mui/button
           {:color    "secondary"
            :on-click #(==> [::events/clear-filters])}
           (tr :search/clear-filters)]])

       ;; Grouping selector
       [mui/grid {:item true}
        [grouping-selector
         {:tr        tr
          :value     grouping
          :on-change #(==> [::events/select-grouping %])}]]

       ;; Metric selector
       [mui/grid {:item true}
        [metrics-selector
         {:tr        tr
          :value     metric
          :on-change #(==> [::events/select-metric %])}]]]]

     [mui/grid {:container true :item true :xs 12 :spacing 4}

      ;; Tabs for choosing between chart/table views
      [mui/grid {:item true}
       [common/view-tabs
        {:value     view
         :on-change #(==> [::events/select-view %2])}]]

      ;; Download Excel button
      [mui/grid {:item true}
       [common/download-excel-button
        {:tr       tr
         :on-click #(==> [::events/download-excel data headers])}]]]

     ;; Table
     (when (= view "table")
       [mui/grid {:item true :xs 12}
        [lui/table
         {:headers headers :items data}]])

     ;; Chart
     (when (= view "chart")
       (let [on-click (fn [evt]
                        (when-let [m (charts/->payload evt)]
                          (==> [::events/select-filters m grouping])))]
         [mui/grid {:item true :xs 12}
          [charts/sports-stats-chart
           {:data     data
            :labels   labels
            :metric   metric
            :grouping grouping
            :on-click on-click}]]))]))
