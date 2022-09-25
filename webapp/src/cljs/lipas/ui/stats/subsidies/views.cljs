(ns lipas.ui.stats.subsidies.views
  (:require
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.stats.common :as common]
   [lipas.ui.stats.subsidies.events :as events]
   [lipas.ui.stats.subsidies.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn years-selector
  [{:keys [tr] :as props}]
  [lui/years-selector
   (merge
    props
    {:label (tr :actions/select-years)
     :style common/select-style})])

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

(defn issuer-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        issuers (<== [::subs/issuers])]
    [lui/multi-select
     {:items     issuers
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-issuer)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn region-selector [props]
  (let [regions (<== [:lipas.ui.stats.subs/regions])]
    [lui/region-selector (assoc props :regions regions)]))

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

    [mui/grid {:container true :spacing 16}

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/subsidies)]]

     ;; Disclaimers
     [common/disclaimer
      {:label (tr :stats/disclaimer-headline)
       :texts [(tr :stats/general-disclaimer-1)
               (tr :stats/general-disclaimer-2)
               (tr :stats/general-disclaimer-3)]}]

     [mui/grid {:item true :xs 12}

      [mui/grid {:container true :spacing 16}

       ;; Region filter
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-cities)]
        [region-selector
         {:value     cities
          :on-change #(==> [::events/select-cities %])}]]

       ;; Type filter
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-types)]
        [lui/type-category-selector
         {:tr        tr
          :value     types
          :on-change #(==> [::events/select-types %])}]]

       ;; Years selector
       [mui/grid {:item true}
        [years-selector
         {:tr        tr
          :years     (range 2002 (inc 2022))
          :value     years
          :on-change #(==> [::events/select-years %])}]]

       ;; Issuer selector
       [mui/grid {:item true}
        [issuer-selector
         {:tr        tr
          :value     issuers
          :on-change #(==> [::events/select-issuers %])}]]

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
                                                 "default")}
           "sort"]]]]

       ;; Clear filters button
       (when (or (not-empty cities) (not-empty types))
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
      [mui/grid {:item true}
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
       [mui/grid {:item true :xs 12}
        [lui/table
         {:headers headers :items data}]])]))
