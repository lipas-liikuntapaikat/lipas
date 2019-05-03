(ns lipas.ui.stats.city.views
  (:require
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.stats.common :as common]
   [lipas.ui.stats.city.events :as events]
   [lipas.ui.stats.city.subs :as subs]
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
    :style        common/select-style
    :years        (range 2000 utils/this-year)
    :on-change    on-change}])

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

    [mui/grid {:container true :spacing 16}

     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h3"}
       (tr :stats/city-stats)]]

     ;; City selector
     [mui/grid {:item true :xs 12}
      [lui/city-selector-single
       {:tr        tr
        :value     (first cities)
        :on-change #(==> [::events/select-cities [%]])}]]

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
      [common/view-tabs
       {:value     view
        :on-change #(==> [::events/select-finance-view %2])}]]

     ;; Download Excel button
     [common/download-excel-button
      {:tr       tr
       :on-click #(==> [::events/download-finance-excel finance-data headers])}]]))
