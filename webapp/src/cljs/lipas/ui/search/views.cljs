(ns lipas.ui.search.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.views :as reports]
   [lipas.ui.search.events :as events]
   [lipas.ui.search.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn type-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        types  (<== [:lipas.ui.sports-sites.subs/types-list locale])]
    ^{:key value}
    [lui/autocomplete
     {:items     types
      :value     value
      :label     (tr :search/search)
      :value-fn  :type-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn city-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        types  (<== [:lipas.ui.sports-sites.subs/cities-list])]
    ^{:key value}
    [lui/autocomplete
     {:items     types
      :value     value
      :label     (tr :search/search)
      :value-fn  :city-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn surface-material-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/surface-materials])]
    [lui/autocomplete
     {:value     value
      :label     ""
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn admin-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/admins])]
    [lui/multi-select
     {:style     {:min-width "150px"}
      :value     value
      :deselect? true
      :label     ""
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn owner-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/owners])]
    [lui/multi-select
     {:style     {:min-width "150px"}
      :value     value
      :deselect? true
      :label     ""
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn- filter-layout [props & children]
  [mui/grid {:item true :style {:min-width "350px"}}
   [mui/paper {:style {:padding "1em"}}
    (into [mui/grid {:container true :direction "column"}]
          (for [c children]
            [mui/grid {:item true}
             c]))]])

(defn filters [{:keys [tr]}]
  (let [type-codes        (<== [::subs/types-filter])
        city-codes        (<== [::subs/cities-filter])
        admins            (<== [::subs/admins-filter])
        owners            (<== [::subs/owners-filter])
        area-min          (<== [::subs/area-min-filter])
        area-max          (<== [::subs/area-max-filter])
        surface-materials (<== [::subs/surface-materials-filter])
        retkikartta?      (<== [::subs/retkikartta-filter])]

    [mui/grid {:container true
               :spacing   16}

     ;; Types filter
     [filter-layout {}
      [mui/typography {:variant :caption}
       (tr :actions/select-types)]

      [type-selector
       {:tr        tr
        :value     type-codes
        :on-change #(==> [::events/set-type-filter %])}]]

     ;; Cities filter
     [filter-layout {}
      [mui/typography {:variant :caption}
       (tr :actions/select-cities)]

      [city-selector
       {:tr        tr
        :value     city-codes
        :on-change #(==> [::events/set-city-filter %])}]]

     ;; Admins filter
     [filter-layout {}
      [mui/typography {:variant :caption}
       (tr :actions/select-admins)]

      [admin-selector
       {:tr        tr
        :value     admins
        :on-change #(==> [::events/set-admins-filter %])}]]

     ;; Owners filter
     [filter-layout {}
      [mui/typography {:variant :caption}
       (tr :actions/select-owners)]

      [owner-selector
       {:tr        tr
        :value     owners
        :on-change #(==> [::events/set-owners-filter %])}]]

     ;; Surface materials filter
     [filter-layout {}
      [mui/typography {:variant :caption}
       (tr :actions/filter-surface-materials)]

      [surface-material-selector
       {:tr        tr
        :value     surface-materials
        :on-change #(==> [::events/set-surface-materials-filter %])}]]

     ;; Retkikartta.fi filter
     [filter-layout {}
      [lui/checkbox
       {:value     retkikartta?
        :label     (tr :search/retkikartta-filter)
        :on-change #(==> [::events/set-retkikartta-filter %])}]]

     ;; Area filters
     [filter-layout {}
      [mui/typography {:variant :caption}
       (tr :actions/filter-area-m2)]

      [mui/grid {:container true :spacing 16}

       ;; Area min filter
       [mui/grid {:item true :xs 6}
        [lui/text-field {:label     "Min"
                         :defer-ms  500
                         :type      "number"
                         :value     area-min
                         :on-change #(==> [::events/set-area-min-filter %])}]]

       ;; Area max filter
       [mui/grid {:item true :xs 6}
        [lui/text-field {:label     "Max"
                         :defer-ms  500
                         :type      "number"
                         :value     area-max
                         :on-change #(==> [::events/set-area-max-filter %])}]]]]]))

(defn search-view [{:keys [tr on-result-click]}]
  (let [search-str      (<== [::subs/search-string])
        results         (<== [::subs/search-results-list])
        total           (<== [::subs/search-results-total-count])
        result-view     (<== [::subs/search-results-view])
        sort-opts       (<== [::subs/sort-opts])
        pagination      (<== [::subs/pagination])
        filters-active? (<== [::subs/filters-active?])]

    [mui/grid {:item true :xs 12 :style {:flex 1}}

     ;; First row: LIPAS-text and view selector
     [mui/grid {:container true}

      [mui/grid {:item true :style {:flex-grow 1}}
       [mui/typography {:variant :display1}
        "LIPAS"]]

      ;; Change result view (list | table)
      [mui/grid {:item true}

       [mui/icon-button {:on-click #(==> [::events/toggle-results-view])}
        [mui/icon {:color (if (= :list result-view)
                            "secondary"
                            "default")}
         "view_stream"]]

       [mui/icon-button {:on-click #(==> [::events/toggle-results-view])}
        [mui/icon {:color (if-not (= :list result-view)
                            "secondary"
                            "inherit")}
         "view_column"]]]]

     ;; Second row: Search input and button
     [mui/grid {:container true :style {:margin-top    "2em"
                                        :margin-bottom "1em"}}
      [mui/grid {:item true :style {:flex-grow 1}}
       [lui/text-field {:value       search-str
                        :placeholder (tr :search/placeholder)
                        :full-width  true
                        :on-change   #(==> [::events/update-search-string %])}]]
      [mui/grid {:item true}
       [mui/button {:on-click #(==> [::events/submit-search])}
        [mui/icon "search"]
        (tr :search/search)]]]

     ;; Third row: filters expansion panel
     [lui/expansion-panel {:label            (tr :search/filters)
                           :label-color      "default"
                           :default-expanded true}
      [filters {:tr tr}]]

     ;; 4th row: Results count and clear filters button
     [mui/grid {:container true}
      [mui/grid {:item true :style {:flex-grow 1}}
       [mui/typography {:variant "body2"
                        :style   {:margin-top  "1em"
                                  :margin-left "1em"}}
        (tr :search/results-count total)]]

      (when filters-active?
        [mui/grid {:item true}
         [mui/button {:style    {:margin "0.5em"}
                      :color    "secondary"
                      :size     "small"
                      :on-click #(==> [::events/clear-filters])}
          (tr :search/clear-filters)]])]

     [mui/divider]

     ;; 5th row: Excel export
     (when (not= :list result-view)
       [reports/dialog {:tr tr}])

     ;; Remaining rows: Results

     (if (= :list result-view)

       ;; Results list
       [into [mui/list]
        (for [result results]
          [mui/list-item
           {:button   true
            :divider  true
            :on-click #(on-result-click result)}
           [mui/list-item-text
            {:primary   (-> result :name)
             :secondary (str (-> result :type.name) ", "
                             (-> result :city.name))}]])]

       ;; Results table
       [mui/grid {:container true}

        ;; Pagination
        [mui/grid {:item true :style {:flex-grow 1}}
         [mui/table-pagination
          {:rows-per-page         200
           :rows-per-page-options #js[200]
           :count                 total
           :on-change-page        #(==> [::events/change-result-page %2])
           :page                  (:page pagination)}]]

        ;; Rank results close to map center higher
        [mui/grid {:item true}
         [lui/checkbox
          {:style     {:height "100%"}
           :label     (tr :search/display-closest-first)
           :value     (:score? sort-opts)
           :on-change #(==> [::events/toggle-sorting-by-distance])}]]

        ;; The table
        [mui/grid {:item true}
         [lui/table
          {:items            results
           :hide-action-btn? true
           :on-select        #(on-result-click %)
           :sort-fn          (or (:sort-fn sort-opts) :score)
           :sort-asc?        (:asc? sort-opts)
           :on-sort-change   #(==> [::events/change-sort-order %])
           :headers          [[:score "score" :hidden]
                              [:name (tr :lipas.sports-site/name)]
                              [:type.name (tr :type/name)]
                              [:admin (tr :lipas.sports-site/admin)]
                              [:owner (tr :lipas.sports-site/owner)]
                              [:location.city.name (tr :lipas.location/city)]
                              [:event-date (tr :lipas.sports-site/event-date)]]}]]])]))
