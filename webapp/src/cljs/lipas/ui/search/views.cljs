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
     [mui/typography {:variant :caption}
      (tr :actions/select-types)]

     [mui/grid {:item true :xs 12}
      [type-selector
       {:tr        tr
        :value     type-codes
        :on-change #(==> [::events/set-type-filter %])}]]

     ;; Cities filter
     [mui/typography {:variant :caption :xs 12
                      :style   {:margin-top "1em"}}
      (tr :actions/select-cities)]

     [mui/grid {:item true :xs 12}
      [city-selector
       {:tr        tr
        :value     city-codes
        :on-change #(==> [::events/set-city-filter %])}]]

     ;; Admins filter
     [mui/typography {:variant :caption :xs 12
                      :style   {:margin-top "1em"}}
      (tr :actions/select-admins)]
     [mui/grid {:item true :xs 12}
      [admin-selector
       {:tr        tr
        :value     admins
        :on-change #(==> [::events/set-admins-filter %])}]]

     ;; Owners filter
     [mui/typography {:variant :caption :xs 12
                      :style   {:margin-top "1em"}}
      (tr :actions/select-owners)]
     [mui/grid {:item true :xs 12}
      [owner-selector
       {:tr        tr
        :value     owners
        :on-change #(==> [::events/set-owners-filter %])}]]

     ;; Area filters
     [mui/typography {:variant :caption
                      :style   {:margin-top "1em"}}
      (tr :actions/filter-area-m2)]

     [mui/grid {:item true :xs 12}
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
                         :on-change #(==> [::events/set-area-max-filter %])}]]]]

     ;; Surface materials filter
     [mui/typography {:variant :caption
                      :style   {:margin-top "1em"}}
      (tr :actions/filter-surface-materials)]
     [mui/grid {:item true :xs 12}
      [surface-material-selector
       {:tr        tr
        :value     surface-materials
        :on-change #(==> [::events/set-surface-materials-filter %])}]]

     ;; Retkikartta.fi filter
     [lui/checkbox
      {:value     retkikartta?
       :label     (tr :search/retkikartta-filter)
       :on-change #(==> [::events/set-retkikartta-filter %])}]]))

(defn search-view [{:keys [tr on-result-click]}]
  (let [search-str  (<== [::subs/search-string])
        results     (<== [::subs/search-results-list])
        total       (<== [::subs/search-results-total-count])
        result-view (<== [::subs/search-results-view])
        sort-opts   (<== [::subs/sort-opts])
        pagination  (<== [::subs/pagination])]
    [mui/grid {:item true :xs 12 :style {:flex 1}}
     [mui/typography {:style   {:margin-bottom "0.5em"}
                      :variant :display1}
      "LIPAS"]
     [mui/grid {:container true}
      [mui/grid {:item true :xs 9}
       [lui/text-field {:value       search-str
                        :placeholder (tr :search/placeholder)
                        :full-width  true
                        :on-change   #(==> [::events/update-search-string %])}]]
      [mui/grid {:item true :xs 3}
       [mui/button {:on-click #(==> [::events/submit-search])}
        [mui/icon "search"]
        (tr :search/search)]]]

     [lui/expansion-panel {:label            (tr :search/filters)
                           :label-color      "default"
                           :default-expanded false}
      [filters {:tr tr}]]

     [mui/typography {:variant "body2"
                      :style   {:margin-top  "1em"
                                :margin-left "1em"}}
      (tr :search/results-count total)]

     [mui/divider]

     [reports/dialog {:tr tr}]

     ;; Results

     ;; Rank results close to map center higher
     [lui/checkbox
      {:label     "Hae kartan alueelta"
       :value     (:score? sort-opts)
       :on-change #(==> [::events/toggle-sorting-by-distance])}]

     ;; Change result view (list | table)
     [mui/button {:on-click #(==> [::events/toggle-results-view])}
      [mui/icon (if (= :list result-view)
                  "arrow_forward_ios"
                  "arrow_back_ios")]]

     (if (= :list result-view)

       ;; Results list
       [into [mui/list]
        (for [result results]
          [mui/list-item
           {:button   true
            :divider  true
            :on-click #(on-result-click result)}
           [mui/list-item-text {:primary   (-> result :name)
                                :secondary (str (-> result :type.name) ", "
                                                (-> result :city.name))}]])]

       ;; Results table
       [:<>
        [mui/table-pagination
         {:rows-per-page         200
          :rows-per-page-options #js[200]
          :count                 total
          :on-change-page        #(==> [::events/change-result-page %2])
          :page                  (:page pagination)}]

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
                             [:event-date (tr :lipas.sports-site/event-date)]]}]

        ])]))
