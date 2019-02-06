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

(defn city-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        cities (<== [:lipas.ui.sports-sites.subs/cities-list])]
    ^{:key value}
    [lui/select
     {:items     cities
      :value     value
      :style     select-style
      :label     (tr :stats/select-city)
      :value-fn  :city-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

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

(defn metrics-selector [{:keys [tr value on-change]}]
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

(defn- make-finance-headers [tr]
  [[:year (tr :time/year)]
   [:city-code (tr :lipas.location/city-code)]
   [:investments (tr :stats-metrics/investments)]
   [:investments-avg (str (tr :stats-metrics/investments) " "
                          (tr :stats/country-avg))]
   [:net-costs (tr :stats-metrics/net-costs)]
   [:net-costs-avg (str (tr :stats-metrics/net-costs) " "
                        (tr :stats/country-avg))]
   [:operating-expenses (tr :stats-metrics/operating-expenses)]
   [:operating-expenses-avg (str (tr :stats-metrics/operating-expenses) " "
                                 (tr :stats/country-avg))]
   [:operating-incomes (tr :stats-metrics/operating-incomes)]
   [:operating-incomes-avg (str (tr :stats-metrics/operating-incomes) " "
                                (tr :stats/country-avg))]
   [:subsidies (tr :stats-metrics/subsidies)]
   [:subsidies-avg (str (tr :stats-metrics/subsidies) " "
                        (tr :stats/country-avg))]])

(defn city-stats []
  (let [tr           (<== [:lipas.ui.subs/translator])
        cities       (<== [::subs/selected-cities])
        unit         (<== [::subs/selected-finance-unit])
        metrics      (<== [::subs/selected-finance-metrics])
        service      (<== [::subs/selected-finance-city-service])
        years        (<== [::subs/selected-finance-years])
        finance-data (<== [::subs/finance-data])
        labels       (<== [::subs/finance-labels])
        view         (<== [::subs/finance-view-type])
        headers      (make-finance-headers tr)

        age-structure-data   (<== [::subs/age-structure-data])
        age-structure-labels (<== [::subs/age-structure-labels])
        grouping             (<== [::subs/selected-age-structure-grouping])
        interval             (<== [::subs/selected-age-structure-interval])]

    [mui/grid {:container true :spacing 16}


     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h3"}
       (tr :stats/city-stats)]]

     ;; City selector
     [mui/grid {:item true :xs 12}
      [city-selector
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
        [metrics-selector
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
      [mui/tabs {:value view :on-change #(==> [::events/select-view-type %2])}
       [mui/tab {:value "chart" :icon (r/as-element [mui/icon "bar_chart"])}]
       [mui/tab {:value "table" :icon (r/as-element [mui/icon "table_chart"])}]]]

     ;; Download Excel button
     [mui/button
      {:style    {:margin "1em"}
       :variant  "outlined"
       :color    "secondary"
       :on-click #(==> [::events/download-finance-excel finance-data headers])}
      (tr :actions/download-excel)]

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/age-structure)]]

     [mui/grid {:item true}
      [mui/grid {:container true :spacing 16}

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

     ;; Chart
     [mui/grid {:item true :xs 12}
      [charts/age-structure-chart
       {:data   age-structure-data
        :labels age-structure-labels}]]]))

(defn sports-stats []
  (let [tr (<== [:lipas.ui.subs/translator])]
    [mui/grid {:container true}
     [mui/grid {:item true :style {:margin-top "1em"}}
      [mui/typography {:variant "h3"}
       (tr :stats/sports-stats)]]]))

(defn create-panel []
  (let [tr  (<== [:lipas.ui.subs/translator])
        tab (<== [::subs/selected-tab])]

    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/paper {:square true :style {:padding "1em"}}

       ;; Tabs for choosing between different stats pages
       [mui/grid {:item true}
        [mui/tabs {:value tab :on-change #(==> [::events/select-tab %2])}
         [mui/tab {:value "city-stats" :label (tr :stats/city-stats)}]
         [mui/tab {:value "sports-stats" :label (tr :stats/sports-stats)}]]]

       [mui/grid {:item true}

        (when (= "sports-stats" tab)
          [sports-stats])

        (when (= "city-stats" tab)
          [city-stats])]]]]))

(defn main [tr]
  [create-panel])
