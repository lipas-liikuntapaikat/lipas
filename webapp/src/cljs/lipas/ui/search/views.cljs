(ns lipas.ui.search.views
  (:require
   [clojure.spec.alpha :as s]
   [goog.object :as gobj]
   [lipas.ui.components :as lui]
   [lipas.ui.components.lists :as lists]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.views :as reports]
   [lipas.ui.search.events :as events]
   [lipas.ui.search.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn- filter-layout [props & children]
  [mui/grid {:item true :style {:min-width  "365px"}}
   [mui/paper {:style {:padding "1em" :height "100%"}}
    (into [mui/grid {:container true :direction "column"}]
          (for [c children]
            [mui/grid {:item true}
             c]))]])

;; Text-fields are nasty to reset and filters contain many of
;; them. Therefore we use this ugly hack to change react-key for the
;; filter container component when "clear filters" button is clicked.
(def ugly-forcer (r/atom 0))

(defn filters [{:keys [tr]}]
  (let [logged-in?        (<== [:lipas.ui.user.subs/logged-in?])
        statuses          (<== [::subs/statuses-filter])
        type-codes        (<== [::subs/types-filter])
        city-codes        (<== [::subs/cities-filter])
        admins            (<== [::subs/admins-filter])
        owners            (<== [::subs/owners-filter])
        area-min          (<== [::subs/area-min-filter])
        area-max          (<== [::subs/area-max-filter])
        year-min          (<== [::subs/construction-year-min-filter])
        year-max          (<== [::subs/construction-year-max-filter])
        surface-materials (<== [::subs/surface-materials-filter])
        retkikartta?      (<== [::subs/retkikartta-filter])
        harrastuspassi?   (<== [::subs/harrastuspassi-filter])
        school-use?       (<== [::subs/school-use-filter])]

    ^{:key @ugly-forcer}
    [mui/grid {:container true :spacing 2}

     ;; Permissions filter
     (when logged-in?
       [filter-layout ()
        [mui/button {:on-click #(==> [::events/set-filters-by-permissions])}
         (tr :search/permissions-filter)]])

     ;; Types filter
     [filter-layout {}
      [mui/typography {:variant "caption"}
       (tr :actions/select-types)]

      [lui/type-category-selector
       {:tr        tr
        :value     type-codes
        :on-change #(==> [::events/set-type-filter %])}]]

     ;; Regions filter (cities, avis, provinces)
     [filter-layout {}
      [mui/typography {:variant "caption"}
       (tr :actions/select-cities)]

      [lui/region-selector
       {:value     city-codes
        :on-change #(==> [::events/set-city-filter %])}]]

     ;; Admins filter
     [filter-layout {}
      [mui/typography {:variant "caption"}
       (tr :actions/select-admins)]

      [lui/admin-selector
       {:tr        tr
        :value     admins
        :on-change #(==> [::events/set-admins-filter %])}]]

     ;; Owners filter
     [filter-layout {}
      [mui/typography {:variant "caption"}
       (tr :actions/select-owners)]

      [lui/owner-selector
       {:tr        tr
        :value     owners
        :on-change #(==> [::events/set-owners-filter %])}]]

     ;; Surface materials filter
     [filter-layout {}
      [mui/typography {:variant "caption"}
       (tr :actions/filter-surface-materials)]

      [lui/surface-material-selector
       {:tr        tr
        :value     surface-materials
        :on-change #(==> [::events/set-surface-materials-filter %])}]]

     ;; Retkikartta.fi filter
     [filter-layout {}
      [lui/checkbox
       {:value     retkikartta?
        :label     (tr :search/retkikartta-filter)
        :on-change #(==> [::events/set-retkikartta-filter %])}]]

     ;; Harrastuspassi.fi filter
     [filter-layout {}
      [lui/checkbox
       {:value     harrastuspassi?
        :label     (tr :search/harrastuspassi-filter)
        :on-change #(==> [::events/set-harrastuspassi-filter %])}]]

     ;; School-use? filter
     [filter-layout {}
      [lui/checkbox
       {:value     school-use?
        :label     (tr :search/school-use-filter)
        :on-change #(==> [::events/set-school-use-filter %])}]]

     ;; Area filters
     [filter-layout {}
      [mui/typography {:variant "caption"}
       (tr :actions/filter-area-m2)]

      [mui/grid {:container true :spacing 2}

       ;; Area min filter
       [mui/grid {:item true :xs 6}
        [lui/text-field
         {:label     "Min"
          :defer-ms  500
          :type      "number"
          :value     area-min
          :on-change #(==> [::events/set-area-min-filter %])}]]

       ;; Area max filter
       [mui/grid {:item true :xs 6}
        [lui/text-field
         {:label     "Max"
          :defer-ms  500
          :type      "number"
          :value     area-max
          :on-change #(==> [::events/set-area-max-filter %])}]]]]

     ;; Construction year filters
     [filter-layout {}
      [mui/typography {:variant "caption"}
       (tr :actions/filter-construction-year)]

      [mui/grid {:container true :spacing 2}

       ;; Construction year min filter
       [mui/grid {:item true :xs 6}
        [lui/text-field
         {:label     "Min"
          :defer-ms  500
          :type      "number"
          :value     year-min
          :on-change #(==> [::events/set-construction-year-min-filter %])}]]

       ;; Construction year max
       [mui/grid {:item true :xs 6}
        [lui/text-field
         {:label     "Max"
          :defer-ms  500
          :type      "number"
          :value     year-max
          :on-change #(==> [::events/set-construction-year-max-filter %])}]]]]

     ;; Statuses filter
     [filter-layout {}
      [lui/status-selector
       {:multi?    true
        :value     statuses
        :on-change #(==> [::events/set-status-filter %])}]]]))

(defn pagination
  [{:keys [tr page page-size page-sizes total change-page-size?] :as props}]
  [mui/table-pagination
   (merge
    {:component             "div"
     :rows-per-page         page-size
     :rows-per-page-options #js[page-size]
     :label-displayed-rows
     (fn [props]
       (let [from  (gobj/get props "from")
             to    (gobj/get props "to")
             total (gobj/get props "count")
             page  (gobj/get props "page")]
         (tr :search/pagination from to total page)))
     :count                 (or total 0)
     :on-page-change        #(==> [::events/change-result-page %2])

     :page page}
    (when change-page-size?
      {:rows-per-page-options   (clj->js page-sizes)
       :on-rows-per-page-change #(==> [::events/change-result-page-size
                                       (-> %1 .-target .-value)])
       :label-rows-per-page     (tr :search/page-size)})
    (dissoc props :tr :change-page-size? :total :page-sizes :page-size))])

(defn results-table [{:keys [on-result-click]}]
  (let [tr               (<== [:lipas.ui.subs/translator])
        specs            (<== [::subs/results-table-specs])
        headers          (<== [::subs/results-table-headers])
        selected-columns (<== [::subs/selected-results-table-columns])
        sort-opts        (<== [::subs/sort-opts])
        in-progress?     (<== [::subs/in-progress?])
        results          (<== [::subs/search-results-table-data])
        total            (<== [::subs/search-results-total-count])
        pagination-opts  (<== [::subs/pagination])
        page-sizes       (-> pagination-opts :page-sizes)
        page-size        (-> pagination-opts :page-size)
        page             (-> pagination-opts :page)]

    [mui/grid
     {:container       true
      :align-items     "center"
      :justify-content "space-between"
      :style           {:padding "0.5em"}}

     ;; Pagination
     [mui/grid {:item true}
      [pagination
       {:tr                tr
        :total             total
        :page              page
        :page-size         page-size
        :page-sizes        page-sizes
        :change-page-size? true
        :style             {:margin-right "2em"}}]]

     [mui/grid {:item true}
      [reports/dialog {:tr tr}]]

     ;; Rank results close to map center higher
     [mui/grid {:item true}
      [lui/checkbox
       {:style     {:height "100%"}
        :label     (tr :search/display-closest-first)
        :value     (= :score (:sort-fn sort-opts))
        :on-change #(==> [::events/toggle-sorting-by-distance])}]]

     ;; Select table columns
     [mui/grid {:item true :style {:padding-right "0.5em"}}
      [lui/search-results-column-selector
       {:value     selected-columns
        :on-change #(==> [::events/select-results-table-columns %])}]]

     ;; The table
     [mui/grid {:item true :xs 12}
      [lui/table-v2
       {:key            (:sort-fn sort-opts)
        :in-progress?   in-progress?
        :items          results
        :action-icon    "location_on"
        :action-label   (tr :map/zoom-to-site)
        :edit-label     (tr :actions/edit)
        :save-label     (tr :actions/save)
        :discard-label  (tr :actions/discard)
        :on-select      #(on-result-click %)
        :sort-asc?      (:asc? sort-opts)
        :allow-editing? :permission?
        :allow-saving?  (fn [item]
                          (->> item
                               (reduce
                                (fn [res [k v]]
                                  (if-let [spec (and (or (-> k specs :required?)
                                                         (some? v))
                                                     (-> k specs :spec))]
                                    (conj res (s/valid? spec v))
                                    (conj res true)))
                                [])
                               (every? true?)))
        :on-item-save   #(==> [::events/save-edits %])
        :on-sort-change #(==> [::events/change-sort-order %])
        :on-edit-start  #(==> [:lipas.ui.sports-sites.events/get (:lipas-id %)])
        :headers        headers}]]

     ;; Pagination vol 2
     [mui/grid {:item true}
      [pagination
       {:tr                tr
        :total             total
        :page              page
        :page-size         page-size
        :page-sizes        page-sizes
        :change-page-size? true}]]]))

(defn results-list [{:keys [on-result-click]}]
  (let [tr           (<== [:lipas.ui.subs/translator])
        in-progress? (<== [::subs/in-progress?])
        results      (<== [::subs/search-results-list-data])
        total        (<== [::subs/search-results-total-count])

        pagination-opts (<== [::subs/pagination])

        page-sizes (-> pagination-opts :page-sizes)
        page-size  (-> pagination-opts :page-size)
        page       (-> pagination-opts :page)]

    ;; TODO: Juho later Move to Stack - fix results height
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [pagination
       {:tr                tr
        :total             total
        :page              page
        :page-size         page-size
        :page-sizes        page-sizes
        :change-page-size? true
        :style             {:padding-right 0 :padding-left 0}}]]

     (if in-progress?
       ;; Spinner
       [mui/grid {:item true :xs 12 :style {:text-align "center"}}
        [mui/circular-progress {:style {:margin-top "1em"}}]]

       ;; Results
       [mui/grid {:item true :xs 12 :flex 1}
        [:div {:style {:width "100%" :height "500px"}}
         [lists/virtualized-list
          {:items         results
           :label-fn      :name
           :label2-fn     #(when %
                             (str (-> % :type.name) ", "
                                  (-> % :location.city.name)
                                  ;; uncomment for search tuning
                                  ;;" " (-> % :score)
                                  ))
           :on-item-click on-result-click}]]])]))

(defn search-input []
  (let [tr         (<== [:lipas.ui.subs/translator])
        search-str (<== [::subs/search-string])]

    [mui/grid {:container true}
     [mui/grid {:item true :style {:flex-grow 1}}
      [lui/text-field
       {:value        search-str
        :placeholder  (tr :search/placeholder)
        :full-width   true
        :defer-ms     10
        :on-change    #(==> [::events/update-search-string %])
        :on-key-press (fn [e]
                        (when (= 13 (.-charCode e)) ; Enter
                          (==> [::events/search-with-keyword :fit-view])))}]]
     [mui/grid {:item true}
      [mui/button {:on-click #(==> [::events/search-with-keyword :fit-view])}
       [mui/icon "search"]
       (tr :search/search)]]]))

(defn save-dialog []
  (r/with-let [name' (r/atom nil)]
    (let [tr    (<== [:lipas.ui.subs/translator])
          open? (<== [::subs/save-dialog-open?])]
      [mui/dialog {:open open?}
       [mui/dialog-content
        [lui/text-field
         {:label     (tr :general/name)
          :value     @name'
          :on-change #(reset! name' %)}]]
       [mui/dialog-actions
        [mui/button {:on-click #(==> [::events/toggle-save-dialog])}
         (tr :actions/cancel)]
        [mui/button
         {:disabled (empty? @name')
          :on-click #(==> [::events/save-current-search @name'])}
         (tr :actions/save)]]])))

(defn search-view [{:keys [tr on-result-click]}]
  (let [total           (<== [::subs/search-results-total-count])
        result-view     (<== [::subs/search-results-view])
        filters-active? (<== [::subs/filters-active?])
        bbox-only?      (<== [::subs/bounding-box-filter])
        bbox-enabled?   (<== [::subs/allow-changing-bounding-box-filter?])
        saved-searches  (<== [:lipas.ui.user.subs/saved-searches])
        logged-in?      (<== [:lipas.ui.subs/logged-in?])]

    [:div {:style {:height "100%"}}

     (when (= result-view :table)
       [save-dialog])

     [mui/grid
      {:container       true
       :style           {:padding "0em 1em 0.5em 1em"}
       :align-items     "flex-start"
       :justify-content "flex-start"
       :direction       "row"}

      ;; Search input and button
      [mui/grid {:item true :xs 12}
       (if (= :list result-view)
         [search-input]
         [mui/grid {:container true :direction "row" :align-items "flex-end"}

          ;; LIPAS-text
          [mui/grid {:item true :xs 12 :md 6}
           [mui/typography {:variant "h2" :style {:opacity 0.7}}
            "LIPAS"]]

          [mui/grid {:item true :xs 12 :md 6}
           [search-input]]

          [mui/grid {:item true :xs 12 :style {:min-height "1em"}}]])]

      ;; Search only from area visible on map
      (when (= result-view :list)
        [mui/grid {:item true :xs 12}
         [lui/checkbox
          {:label     (tr :map/bounding-box-filter)
           :disabled  (not bbox-enabled?)
           :value     bbox-only?
           :on-change #(==> [::events/set-bounding-box-filter %])}]])

      ;; Filters expansion panel
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :search/filters)
         :style            {}
         :default-expanded false}
        [filters {:tr tr}]]]

      ;; Results count, clear filters button and result view selectors
      [mui/grid {:item true :xs 12}
       [mui/grid
        {:container       true
         :justify-content "space-between"
         :align-items     "center"
         :style           {:padding-top "0.5em" :padding-bottom "0.5em"}}
        [mui/grid {:item true}
         [mui/grid {:container true :align-items "center" :spacing 2}

          ;; Results ocunt
          [mui/grid {:item true}
           [mui/typography
            {:variant "body2" :style {:font-size "0.9rem" :margin-left "0.5em"}}
            (tr :search/results-count total)]]

          ;; Clear filters button
          (when (and filters-active? (= :table result-view))
            [mui/grid {:item true}
             [mui/button
              {:color    "secondary"
               :size     "small"
               :on-click (fn []
                           (==> [::events/clear-filters])
                           (swap! ugly-forcer inc))}
              (tr :search/clear-filters)]])

          ;; Save search btn
          (when (and filters-active? logged-in? (= :table result-view))
            [mui/tooltip {:title "Tallenna haku"}
             [mui/grid {:item true}
              [mui/button {:on-click #(==> [::events/toggle-save-dialog])}
               [mui/icon "save"]]]])

          ;; Saved searches select
          (when (and logged-in?  (= :table result-view) saved-searches)
            [mui/grid {:item true}
             [lui/select
              {:style        {:width "170px"}
               :items        saved-searches
               :value        "dummy"
               :render-value (constantly (str (tr :lipas.user/saved-searches) "..."))
               :label-fn     :name
               :value-fn     identity
               :on-change    #(==> [::events/select-saved-search %])}]])]]

        (when (and filters-active? (= :list result-view))
          [mui/grid {:item true}
           [mui/button
            {:color    "secondary"
             :size     "small"
             :on-click (fn []
                         (==> [::events/clear-filters])
                         (swap! ugly-forcer inc))}
            (tr :search/clear-filters)]])

        ;; Change result view (list | table)
        [mui/grid {:item true}

         [mui/tooltip {:title (tr :search/list-view)}
          [mui/icon-button {:on-click #(==> [::events/set-results-view :list])}
           [mui/icon {:color (if (= :list result-view)
                               "secondary"
                               "inherit")}
            "view_stream"]]]

         [mui/tooltip {:title (tr :search/table-view)}
          [mui/icon-button {:on-click #(==> [::events/set-results-view :table])}
           [mui/icon {:color (if-not (= :list result-view)
                               "secondary"
                               "inherit")}
            "view_column"]]]]]]

      [mui/grid {:item true :xs 12}
       [mui/divider]]]

     ;; Results
     (if (= :list result-view)
       [results-list {:on-result-click on-result-click}]
       [results-table {:on-result-click on-result-click}])]))
