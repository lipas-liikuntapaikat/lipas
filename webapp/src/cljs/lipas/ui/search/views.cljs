(ns lipas.ui.search.views
  (:require
   [goog.object :as gobj]
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
      :show-all? true
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
      :show-all? true
      :label     (tr :search/search)
      :value-fn  :city-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn surface-material-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/surface-materials])]
    ^{:key value}
    [lui/autocomplete
     {:value     value
      :label     (tr :search/search)
      :show-all? true
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn admin-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/admins])]
    ^{:key value}
    [lui/autocomplete
     {:style     {:min-width "150px"}
      :value     value
      :deselect? true
      :show-all? true
      :label     (tr :search/search)
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn owner-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/owners])]
    ^{:key value}
    [lui/autocomplete
     {:style     {:min-width "150px"}
      :value     value
      :show-all? true
      :deselect? true
      :label     (tr :search/search)
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

(defn pagination [{:keys [tr page page-size page-sizes total change-page-size?]}]
  [mui/table-pagination
   (merge
    {:rows-per-page         page-size
     :rows-per-page-options #js[page-size]
     :label-displayed-rows
     (fn [props]
       (let [from  (gobj/get props "from")
             to    (gobj/get props "to")
             total (gobj/get props "count")
             page  (gobj/get props "page")]
         (tr :search/pagination from to total page)))
     :count                 (or total 0)
     :on-change-page        #(==> [::events/change-result-page %2])

     :page page}
    (when change-page-size?
      {:rows-per-page-options   (clj->js page-sizes)
       :on-change-rows-per-page #(==> [::events/change-result-page-size
                                       (-> %1 .-target .-value)])
       :label-rows-per-page     (tr :search/page-size)}))])

(defn search-view [{:keys [tr on-result-click]}]
  (let [in-progress?    (<== [::subs/in-progress?])
        search-str      (<== [::subs/search-string])
        results         (<== [::subs/search-results-list])
        total           (<== [::subs/search-results-total-count])
        result-view     (<== [::subs/search-results-view])
        sort-opts       (<== [::subs/sort-opts])
        filters-active? (<== [::subs/filters-active?])
        pagination-opts (<== [::subs/pagination])
        page-sizes      (-> pagination-opts :page-sizes)
        page-size       (-> pagination-opts :page-size)
        page            (-> pagination-opts :page)]

    [mui/grid {:item true :xs 12 :style {:flex 1}}

     ;; First row: LIPAS-text and view selector
     [mui/grid {:container true}

      [mui/grid {:item true :style {:flex-grow 1}}
       [mui/typography {:variant :display3}
        "LIPAS"]]

      ;; Change result view (list | table)
      [mui/grid {:item true}

       [mui/icon-button {:on-click #(==> [::events/set-results-view :list])}
        [mui/icon {:color (if (= :list result-view)
                            "secondary"
                            "inherit")}
         "view_stream"]]

       [mui/icon-button {:on-click #(==> [::events/set-results-view :table])}
        [mui/icon {:color (if-not (= :list result-view)
                            "secondary"
                            "inherit")}
         "view_column"]]]]

     ;; Second row: Search input and button
     [mui/grid {:container true :style {:margin-top    "2em"
                                        :margin-bottom "1em"}}
      [mui/grid {:item true :style {:flex-grow 1}}
       [lui/text-field
        {:value        search-str
         :placeholder  (tr :search/placeholder)
         :full-width   true
         :defer-ms     10
         :on-change    #(==> [::events/update-search-string %])
         :on-key-press (fn [e]
                         (when (= 13 (.-charCode e)) ; Enter
                           (==> [::events/submit-search])))}]]
      [mui/grid {:item true}
       [mui/button {:on-click #(==> [::events/submit-search])}
        [mui/icon "search"]
        (tr :search/search)]]]

     ;; Third row: filters expansion panel
     [lui/expansion-panel {:label            (tr :search/filters)
                           :label-color      "default"
                           :default-expanded false}
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
       [mui/grid {:container true :direction "column" :align-items "center"}
        [mui/grid {:item true}
         [pagination
          {:tr                tr
           :total             total
           :page              page
           :page-size         page-size
           :page-sizes        page-sizes
           :change-page-size? true}]]

        [mui/grid {:item true}
         (if in-progress?
           [mui/circular-progress {:style {:margin-top "1em"}}]
           [into [mui/list]
            (for [result results]
              [mui/list-item
               {:button   true
                :divider  true
                :on-click #(on-result-click result)}
               [mui/list-item-text
                {:primary   (-> result :name)
                 :secondary (str (-> result :type.name) ", "
                                 (-> result :location.city.name))}]])])]]

       ;; Results table
       [mui/grid {:container true}

        ;; Pagination
        [mui/grid {:item true :style {:flex-grow 1}}
         [pagination
          {:tr                tr
           :total             total
           :page              page
           :page-size         page-size
           :page-sizes        page-sizes
           :change-page-size? true}]]

        ;; Rank results close to map center higher
        [mui/grid {:item true}
         [lui/checkbox
          {:style     {:height "100%"}
           :label     (tr :search/display-closest-first)
           :value     (= :score (:sort-fn sort-opts))
           :on-change #(==> [::events/toggle-sorting-by-distance])}]]

        ;; The table
        [mui/grid {:item true :xs 12}
         [lui/table
          {:key              (:sort-fn sort-opts)
           :in-progress?     in-progress?
           :items            results
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
