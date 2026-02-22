(ns lipas.ui.stats.city.views
  (:require [lipas.ui.charts :as charts]
            [lipas.ui.components :as lui]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.stats.city.events :as events]
            [lipas.ui.stats.city.subs :as subs]
            [lipas.ui.stats.common :as common]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn finance-metrics-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        metrics (<== [::subs/finance-metrics])]
    [lui/multi-select
     {:items        metrics
      :value        value
      :style        common/select-style
      :label        (tr :stats/select-metrics)
      :value-fn     first
      :label-fn     (comp locale second)
      :on-change    on-change}]))

(defn unit-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        units  (<== [::subs/finance-units])]
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
        services (<== [::subs/finance-city-services])]
    [lui/select
     {:items     services
      :value     value
      :style     common/select-style
      :label     (tr :stats/select-city-service)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn years-selector [props]
  [lui/years-selector (merge props {:style common/select-style})])

(defn city-selector
  "Includes also abolished cities."
  [props]
  (let [cities (<== [:lipas.ui.stats.subs/cities])]
    [lui/city-selector-single (assoc props :cities cities)]))

(defn view []
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

    [:> Grid {:container true :spacing 4}

     [:> Grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [:> Typography {:variant "h4"}
       (tr :stats/city-stats)]]

     ;; City selector
     [:> Grid {:item true :xs 12}
      [city-selector
       {:tr        tr
        :value     (first cities)
        :on-change #(==> [::events/select-cities [%]])}]]

     ;; Headline
     [:> Grid {:item true :xs 12 :style {:margin-top "1em" :margin-bottom "1em"}}
      [:> Typography {:variant "h5"}
       (tr :stats/finance-stats)]]

     ;; Disclaimers
     [:> Grid {:item true :xs 12}
      [common/disclaimer
       {:label (tr :stats/disclaimer-headline)
        :texts [(tr :stats/finance-disclaimer)]}]]

     ;; Selectors
     [:> Grid {:item true}
      [:> Grid {:container true :spacing 4}

       ;; Unit selector
       [:> Grid {:item true}
        [unit-selector
         {:tr tr :value unit :on-change #(==> [::events/select-finance-unit %])}]]

       ;; Metrics selector
       [:> Grid {:item true}
        [finance-metrics-selector
         {:tr        tr
          :value     metrics
          :on-change #(==> [::events/select-finance-metrics %])}]]

       ;; City service selector
       [:> Grid {:item true}
        [service-selector
         {:tr        tr
          :value     service
          :on-change #(==> [::events/select-finance-city-service %])}]]

       ;; Years selector
       [:> Grid {:item true}
        [years-selector
         {:tr        tr
          :years     (range 2000 utils/this-year)
          :value     years
          :on-change #(==> [::events/select-finance-years %])}]]]]

     [:> Grid {:container true :item true :xs 12 :spacing 4}

      ;; Tabs for choosing between chart/table views
      [:> Grid {:item true}
       [common/view-tabs
        {:value     view
         :on-change #(==> [::events/select-finance-view %2])}]]

      ;; Download Excel button
      [:> Grid {:item true}
       [common/download-excel-button
        {:tr       tr
         :on-click #(==> [::events/download-finance-excel finance-data headers])}]]]

     ;; Chart
     (when (= view "chart")
       [:> Grid {:item true
                  :xs 12
                  :sx {:width 0}}
        [charts/city-finance-chart
         {:metrics metrics :data finance-data :labels labels}]])

     ;; Table
     (when (= view "table")
       [:> Grid {:item true :xs 12}
        [lui/table
         {:headers headers :items finance-data}]])]))
