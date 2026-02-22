(ns lipas.ui.stats.age-structure.views
  (:require [lipas.ui.charts :as charts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.tables :as tables]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.stats.age-structure.events :as events]
            [lipas.ui.stats.age-structure.subs :as subs]
            [lipas.ui.stats.common :as common]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn grouping-selector [{:keys [tr value on-change]}]
  (let [locale    (tr)
        groupings (<== [::subs/groupings])]
    [selects/select
     {:items     groupings
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-grouping)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn interval-selector [{:keys [tr value on-change]}]
  ^{:key value}
  [selects/number-selector
   {:items     [1 5 10]
    :value     value
    :style     common/select-style
    :unit      (tr :duration/years-short)
    :label     (tr :stats/select-interval)
    :on-change on-change}])

(defn view []
  (let [tr       (<== [:lipas.ui.subs/translator])
        regions  (<== [::subs/selected-cities])
        types    (<== [::subs/selected-types])
        grouping (<== [::subs/selected-grouping])
        interval (<== [::subs/selected-interval])
        view     (<== [::subs/selected-view])
        data     (<== [::subs/data])
        labels   (<== [::subs/labels])
        headers  (<== [::subs/headers])]

    [:> Grid {:container true :spacing 4}

     ;; Headline
     [:> Grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [:> Typography {:variant "h4"}
       (tr :stats/age-structure)]]

     ;; Disclaimers
     [:> Grid {:item true :xs 12}
      [common/disclaimer
       {:label (tr :stats/disclaimer-headline)
        :texts [(tr :stats/general-disclaimer-1)
                (tr :stats/general-disclaimer-2)
                (tr :stats/general-disclaimer-3)]}]]

     [:> Grid {:item true}
      [:> Grid {:container true :spacing 4}

       ;; Region selector
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "body2"} (tr :actions/select-cities)]
        [selects/region-selector
         {:value     regions
          :on-change #(==> [::events/select-cities %])}]]

       ;; Type selector
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "body2"} (tr :actions/select-types)]
        [selects/type-category-selector
         {:tr        tr
          :value     types
          :on-change #(==> [::events/select-types %])}]]

       ;; Clear filters button
       (when (or (not-empty types) (not-empty regions))
         [:> Grid {:item true :xs 12}
          [:> Button
           {:color    "secondary"
            :on-click #(==> [::events/clear-filters])}
           (tr :search/clear-filters)]])

       ;; Interval selector
       [:> Grid {:item true}
        [interval-selector
         {:tr        tr
          :value     interval
          :on-change #(==> [::events/select-interval %])}]]

       ;; Grouping selector
       [:> Grid {:item true}
        [grouping-selector
         {:tr        tr
          :value     grouping
          :on-change #(==> [::events/select-grouping %])}]]]]

     [:> Grid {:container true :item true :xs 12 :spacing 4}

      ;; Tabs for choosing between chart/table views
      [:> Grid {:item true}
       [common/view-tabs
        {:value     view
         :on-change #(==> [::events/select-view %2])}]]

      ;; Download Excel button
      [:> Grid {:item true}
       [common/download-excel-button
        {:tr       tr
         :on-click #(==> [::events/download-excel data headers])}]]]

     ;; Table
     (when (= view "table")
       [:> Grid {:item true :xs 12}
        [tables/table
         {:headers headers :items data}]])

     ;; Chart
     (when (= view "chart")
       [:> Grid {:item true
                  :xs 12
                  :sx {:width 0}}
        [charts/age-structure-chart {:data data :labels labels}]])]))
