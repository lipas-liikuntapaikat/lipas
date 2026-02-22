(ns lipas.ui.stats.subsidies.views
  (:require [lipas.ui.charts :as charts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.tables :as tables]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.stats.common :as common]
            [lipas.ui.stats.subsidies.events :as events]
            [lipas.ui.stats.subsidies.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn years-selector
  [{:keys [tr] :as props}]
  [selects/years-selector
   (merge
     props
     {:label (tr :actions/select-years)
      :style common/select-style})])

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

(defn issuer-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        issuers (<== [::subs/issuers])]
    [selects/multi-select
     {:items     issuers
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-issuer)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn region-selector [props]
  (let [regions (<== [:lipas.ui.stats.subs/regions])]
    [selects/region-selector (assoc props :regions regions)]))

(defn view []
  (let [tr           (<== [:lipas.ui.subs/translator])
        cities       (<== [::subs/selected-cities])
        types        (<== [::subs/selected-types])
        years        (<== [::subs/selected-years])
        grouping     (<== [::subs/selected-grouping])
        chart-type   (<== [::subs/chart-type])
        data         (<== [::subs/data])
        ranking-data (<== [::subs/ranking-data])
        labels       (<== [::subs/labels])
        headers      (<== [::subs/headers])
        issuers      (<== [::subs/selected-issuers])
        view         (<== [::subs/selected-view])]

    [:> Grid {:container true :spacing 4}

     ;; Headline
     [:> Grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [:> Typography {:variant "h4"}
       (tr :stats/subsidies)]]

     ;; Disclaimers
     [:> Grid {:item true}
      [common/disclaimer
       {:label (tr :stats/disclaimer-headline)
        :texts [(tr :stats/general-disclaimer-1)
                (tr :stats/general-disclaimer-2)
                (tr :stats/general-disclaimer-3)]}]]

     [:> Grid {:item true :xs 12}

      [:> Grid {:container true :spacing 4}

       ;; Region filter
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "body2"} (tr :stats/filter-cities)]
        [region-selector
         {:value     cities
          :on-change #(==> [::events/select-cities %])}]]

       ;; Type filter
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "body2"} (tr :stats/filter-types)]
        [selects/type-category-selector
         {:tr        tr
          :value     types
          :on-change #(==> [::events/select-types %])}]]

       ;; Years selector
       [:> Grid {:item true}
        [years-selector
         {:tr        tr
          :years     (range 2002 (inc 2024))
          :value     years
          :on-change #(==> [::events/select-years %])}]]

       ;; Issuer selector
       [:> Grid {:item true}
        [issuer-selector
         {:tr        tr
          :value     issuers
          :on-change #(==> [::events/select-issuers %])}]]

       ;; Grouping selector
       [:> Grid {:item true}
        [grouping-selector
         {:tr        tr
          :value     grouping
          :on-change #(==> [::events/select-grouping %])}]]

       ;; Chart type selector
       [:> Grid {:item true}
        [:> Tooltip
         {:title
          (if (= "ranking" chart-type)
            (tr :stats/show-comparison)
            (tr :stats/show-ranking))}
         [:> IconButton {:on-click #(==> [::events/toggle-chart-type])}
          [:> Icon {:font-size "large" :color (if (= "ranking" chart-type)
                                                 "secondary"
                                                 "inherit")}
           "sort"]]]]

       ;; Clear filters button
       (when (or (not-empty cities) (not-empty types))
         [:> Grid {:item true :xs 12}
          [:> Button
           {:color    "secondary"
            :on-click #(==> [::events/clear-filters])}
           (tr :search/clear-filters)]])]]

     ;; Tabs for choosing between chart/table views
     [:> Grid {:container true :item true :xs 12}

      [:> Grid {:item true}
       [common/view-tabs
        {:value     view
         :on-change #(==> [::events/select-view %2])}]]

      ;; Download Excel button
      [:> Grid {:item true}
       [common/download-excel-button
        {:tr       tr
         :on-click #(==> [::events/download-excel data headers])}]]]

     ;; Chart
     (when (= "chart" view)
       (let [on-click (fn [evt]
                        (when-let [m (charts/->payload evt)]
                          (==> [::events/select-filters m grouping])))]
         (if (= "ranking" chart-type)
           [charts/subsidies-ranking-chart
            {:data     ranking-data
             :on-click on-click
             :labels   labels}]
           [charts/subsidies-chart
            {:data     data
             :on-click on-click
             :labels   labels}])))

     ;; Table
     (when (= "table" view)
       [:> Grid {:item true :xs 12}
        [tables/table
         {:headers headers :items data}]])]))
