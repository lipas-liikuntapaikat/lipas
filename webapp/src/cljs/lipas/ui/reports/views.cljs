(ns lipas.ui.reports.views
  (:require
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.events :as events]
   [lipas.ui.reports.subs :as subs]
   [lipas.ui.search.events :as search-events]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(def select-style {:min-width "170px"})

(defn fields-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [::subs/fields])]
    ^{:key value}
    [lui/autocomplete
     {:value       value
      :label       (tr :search/search-more)
      :items       items
      :style       select-style
      :label-fn    (comp locale second)
      :value-fn    first
      :spacing     8
      :items-label (tr :reports/selected-fields)
      :on-change   on-change}]))

(defn city-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        cities (<== [:lipas.ui.sports-sites.subs/cities-list])]
    ^{:key value}
    [lui/select
     {:items     cities
      :value     value
      :style     select-style
      :label     (tr :reports/select-city)
      :value-fn  :city-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn service-selector [{:keys [tr value on-change]}]
  (let [locale   (tr)
        services (<== [::subs/city-services])]
    ^{:key value}
    [lui/select
     {:items     services
      :value     value
      :style     select-style
      :label     (tr :reports/select-city-service)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn metrics-selector [{:keys [tr value on-change]}]
  (let [locale  (tr)
        metrics (<== [::subs/stats-metrics])]
    ^{:key value}
    [lui/multi-select
     {:items        metrics
      :value        value
      :style        select-style
      :label        (tr :reports/select-metrics)
      :value-fn     first
      :label-fn     (comp locale second)
      :on-change    on-change}]))

(defn unit-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        units  (<== [::subs/stats-units])]
    ^{:key value}
    [lui/select
     {:items     units
      :value     value
      :style     select-style
      :label     (tr :reports/select-unit)
      :value-fn  first
      :label-fn  (comp locale second)
      :on-change on-change}]))

(defn years-selector [{:keys [tr value on-change]}]
  [lui/year-selector
   {:label        (tr :reports/select-years)
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

(defn dialog [{:keys [tr]}]
  (let [open?           (<== [::subs/dialog-open?])
        toggle          #(==> [::events/toggle-dialog])
        selected-fields (<== [::subs/selected-fields])
        downloading?    (<== [::subs/downloading?])
        results-count   (<== [:lipas.ui.search.subs/search-results-total-count])

        quick-selects [{:label  (tr :lipas.sports-site/basic-data)
                        :fields ["lipas-id" "name" "marketing-name" "comment"
                                 "construction-year" "renovation-years"]}
                       {:label  (tr :lipas.sports-site/ownership)
                        :fields ["admin" "owner"]}
                       {:label  (tr :lipas.sports-site/contact)
                        :fields ["email" "phone-number" "www"]}
                       {:label  (tr :lipas.sports-site/address)
                        :fields ["location.address" "location.postal-code"
                                 "location.postal-office"
                                 "location.city.city-name"
                                 "location.city.neighborhood"]}
                       {:label  (tr :general/measures)
                        :fields ["properties.field-length-m"
                                 "properties.field-width-m"
                                 "properties.area-m2"]}
                       {:label  (tr :lipas.sports-site/surface-materials)
                        :fields ["properties.surface-material"
                                 "properties.surface-material-info"]}
                       {:label  (tr :type/name)
                        :fields ["type.type-name"]}
                       {:label  (tr :lipas.location/city)
                        :fields ["location.city.city-name"]}]]
    [:<>
     ;; Open Dialog button
     (when (< 0 results-count)
       [mui/button {:style    {:margin-top "1em"}
                    :variant  "extendedFab"
                    :color    "secondary"
                    :on-click toggle}
        [mui/icon "arrow_right"]
        (tr :reports/download-as-excel)])

     ;; Dialog
     [mui/dialog {:open       open?
                  :full-width true
                  :on-close   toggle}
      [mui/dialog-title (tr :reports/select-fields)]
      [mui/dialog-content
       [mui/grid {:container true :spacing 8}

        ;; Quick selects
        [mui/grid {:item true :xs 12}
         [mui/typography {:variant "body2" :style {:margin-top "1em"}}
          (tr :reports/shortcuts)]]

        (into [:<>]
              (for [{:keys [fields label]} quick-selects]
                [mui/grid {:item true}
                 [mui/button
                  {:variant  "outlined"
                   :on-click #(==> [::events/set-selected-fields fields :append])}
                  label]]))

        ;; Fields autocomplete selector
        [mui/grid {:item true :xs 12}
         [fields-selector
          {:tr        tr
           :on-change #(==> [::events/set-selected-fields %])
           :value     selected-fields}]]

        ;; Clear selections
        [mui/grid {:item true :xs 12}
         [mui/button {:style    {:margin-top "1em"}
                      :variant  "outlined"
                      :size     "small"
                      :disabled (empty? selected-fields)
                      :on-click #(==> [::events/set-selected-fields []])}
          [mui/icon "clear"]
          (tr :actions/clear-selections)]]]]

      ;; Cancel / download buttons
      [mui/dialog-actions
       (when downloading?
         [mui/circular-progress])
       [mui/button {:on-click toggle
                    :disabled downloading?}
        (tr :actions/cancel)]
       [mui/button
        {:disabled (or downloading? (empty? selected-fields))
         ;;:variant  ""
         :color    "secondary"
         :on-click #(==> [::search-events/create-report-from-current-search])}
        (tr :reports/download-excel)]]]]))

(defn- make-headers [tr]
  [[:year (tr :time/year)]
   [:city-code (tr :lipas.location/city-code)]
   [:investments (tr :stats-metrics/investments)]
   [:investments-avg (str (tr :stats-metrics/investments) " "
                          (tr :reports/country-avg))]
   [:net-costs (tr :stats-metrics/net-costs)]
   [:net-costs-avg (str (tr :stats-metrics/net-costs) " "
                        (tr :reports/country-avg))]
   [:operating-expenses (tr :stats-metrics/operating-expenses)]
   [:operating-expenses-avg (str (tr :stats-metrics/operating-expenses) " "
                                 (tr :reports/country-avg))]
   [:operating-incomes (tr :stats-metrics/operating-incomes)]
   [:operating-incomes-avg (str (tr :stats-metrics/operating-incomes) " "
                                (tr :reports/country-avg))]
   [:subsidies (tr :stats-metrics/subsidies)]
   [:subsidies-avg (str (tr :stats-metrics/subsidies) " "
                        (tr :reports/country-avg))]])

(defn stats-report []
  (let [tr      (<== [:lipas.ui.subs/translator])
        unit    (<== [::subs/selected-unit])
        cities  (<== [::subs/selected-cities])
        metrics (<== [::subs/selected-metrics])
        service (<== [::subs/selected-city-service])
        years   (<== [::subs/selected-years])
        data    (<== [::subs/cities-stats])
        labels  (<== [::subs/stats-labels])
        tab     (<== [::subs/stats-tab])

        headers (make-headers tr)]

    [mui/grid {:container true :spacing 16}

     ;; Headline
     [mui/grid {:item true}
      [mui/typography {:variant "h2"}
       (tr :reports/stats)]

      ;; Selectors
      [mui/grid {:container true :style {:margin-top "1em"}}

       [mui/form-group {:row true}

        ;; Unit selector
        [unit-selector
         {:tr tr :value unit :on-change #(==> [::events/select-unit %])}]

        [:span {:style {:margin "0.5em"}}]

        ;; City selector
        [city-selector
         {:tr        tr
          :value     (first cities)
          :on-change #(==> [::events/select-cities [%]])}]

        [:span {:style {:margin "0.5em"}}]

        ;; Metrics selector
        [metrics-selector
         {:tr tr :value metrics :on-change #(==> [::events/select-metrics %])}]

        [:span {:style {:margin "0.5em"}}]

        ;; City service selector
        [service-selector
         {:tr tr :value service :on-change #(==> [::events/select-city-service %])}]

        [:span {:style {:margin "0.5em"}}]

        ;; Years selector
        [years-selector
         {:tr tr :value years :on-change #(==> [::events/select-years %])}]]]]

     ;; Chart
     (when (= tab "chart")
       [mui/grid {:item true :xs 12}
        [charts/city-stats-chart
         {:metrics metrics :data data :labels labels}]])

     ;; Table
     (when (= tab "table")
       [mui/grid {:item true :xs 12}
        [lui/table
         {:headers headers :items data}]])

     [mui/grid {:item true}
      [mui/tabs {:value tab :on-change #(==> [::events/select-stats-tab %2])}
       [mui/tab {:value "chart" :icon (r/as-element [mui/icon "bar_chart"])}]
       [mui/tab {:value "table" :icon (r/as-element [mui/icon "table_chart"])}]]]

     ;; Download Excel button
     [mui/button
      {:style    {:margin "1em"}
       :variant  "outlined"
       :color    "secondary"
       :on-click #(==> [::events/download-stats-excel data headers])}
      (tr :reports/download-excel)]]))

(defn create-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/paper {:square true :style {:padding "1em"}}
     [stats-report]]]])

(defn main [tr]
  [create-panel])
