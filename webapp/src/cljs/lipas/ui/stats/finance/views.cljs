(ns lipas.ui.stats.finance.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.stats.common :as common]
   [lipas.ui.stats.finance.events :as events]
   [lipas.ui.stats.finance.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn years-selector [props]
  [lui/years-selector (merge props {:style common/select-style})])

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
        years    (<== [::subs/selected-years])
        service  (<== [::subs/selected-city-service])
        unit     (<== [::subs/selected-unit])
        grouping (<== [::subs/selected-grouping])
        data     (<== [::subs/data])
        labels   (<== [::subs/labels])
        headers  (<== [::subs/headers])]

    [mui/grid {:container true :spacing 16}

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/finance-stats)]]

     [mui/grid {:item true :xs 12}

      [mui/grid {:container true :spacing 16 :style {:margin-bottom "1em"}}

       ;; Region selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-cities)]
        [lui/region-selector
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
        [years-selector
         {:tr        tr
          :value     years
          :on-change #(==> [::events/select-years %])}]]

       ;; Grouping selector
       [mui/grid {:item true}
        [grouping-selector
         {:tr        tr
          :value     grouping
          :on-change #(==> [::events/select-grouping %])}]]

       ;; Clear filters button
       (when (not-empty cities)
         [mui/grid {:item true :xs 12}
          [mui/button
           {:color    "secondary"
            :on-click #(==> [::events/clear-filters])}
           (tr :search/clear-filters)]])]]

     ;; Table
     [mui/grid {:item true :xs 12}
      [lui/table
       {:headers headers :items data}]]

     ;; Download Excel button
     [common/download-excel-button
      {:tr       tr
       :on-click #(==> [::events/download-excel data headers])}]]))
