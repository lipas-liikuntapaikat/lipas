(ns lipas.ui.search.views
  (:require
   [clojure.spec.alpha :as s]
   [lipas.roles :as roles]
   [lipas.ui.components :as lui]
   [lipas.ui.components.lists :as lists]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.views :as reports]
   [lipas.ui.search.events :as events]
   [lipas.ui.search.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]
   [uix.core :refer [$]]))

(defn- filter-layout
  [{:keys [size] :as _props} & children]
  [mui/grid (merge {:item true} (when (= "large" size) {:xs 12 :md 6 :lg 4}))
   (into [mui/grid {:container true :direction "column"}]
         (for [c children]
           [mui/grid {:item true}
            c]))])

;; Text-fields are nasty to reset and filters contain many of
;; them. Therefore we use this ugly hack to change react-key for the
;; filter container component when "clear filters" button is clicked.
(def ugly-forcer (r/atom 0))

(defn filters
  [{:keys [tr size]}]
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
        has-edit-permission? (<== [::subs/edit-permission-filter])
        retkikartta?      (<== [::subs/retkikartta-filter])
        harrastuspassi?   (<== [::subs/harrastuspassi-filter])
        school-use?       (<== [::subs/school-use-filter])]

    ^{:key @ugly-forcer}
    [mui/grid
     {:container true
      :spacing   3
      :direction (if (= "large" size) "row" "column")}

     ;; Permissions filter
     (when (<== [:lipas.ui.user.subs/check-privilege
                 {:city-code ::roles/any
                  :type-code ::roles/any
                  :lipas-id ::roles/any}
                 :site/create-edit])
       [filter-layout {:size size}
        [lui/checkbox
         {:value     has-edit-permission?
          :label     (tr :search/permissions-filter)
          :on-change #(==> [::events/set-filters-by-permissions %])}]])

     ;; Types filter
     [filter-layout {:size size}
      [mui/typography {:variant "caption"}
       (tr :actions/select-types)]

      [lui/type-category-selector
       {:tr        tr
        :value     type-codes
        :on-change #(==> [::events/set-type-filter %])}]]

     ;; Regions filter (cities, avis, provinces)
     [filter-layout {:size size}
      [mui/typography {:variant "caption"}
       (tr :actions/select-cities)]

      [lui/region-selector
       {:value     city-codes
        :on-change #(==> [::events/set-city-filter %])}]]

     ;; Admins filter
     [filter-layout {:size size}
      [mui/typography {:variant "caption"}
       (tr :actions/select-admins)]

      [lui/admin-selector
       {:tr        tr
        :value     admins
        :on-change #(==> [::events/set-admins-filter %])}]]

     ;; Owners filter
     [filter-layout {:size size}
      [mui/typography {:variant "caption"}
       (tr :actions/select-owners)]

      [lui/owner-selector
       {:tr        tr
        :value     owners
        :on-change #(==> [::events/set-owners-filter %])}]]

     ;; Surface materials filter
     [filter-layout {:size size}
      [mui/typography {:variant "caption"}
       (tr :actions/filter-surface-materials)]

      [lui/surface-material-selector
       {:tr        tr
        :value     surface-materials
        :on-change #(==> [::events/set-surface-materials-filter %])}]]

     ;; Retkikartta.fi filter
     [filter-layout {:size size}
      [lui/checkbox
       {:value     retkikartta?
        :label     (tr :search/retkikartta-filter)
        :on-change #(==> [::events/set-retkikartta-filter %])}]]

     ;; Harrastuspassi.fi filter
     [filter-layout {:size size}
      [lui/checkbox
       {:value     harrastuspassi?
        :label     (tr :search/harrastuspassi-filter)
        :on-change #(==> [::events/set-harrastuspassi-filter %])}]]

     ;; School-use? filter
     [filter-layout {:size size}
      [lui/checkbox
       {:value     school-use?
        :label     (tr :search/school-use-filter)
        :on-change #(==> [::events/set-school-use-filter %])}]]

     ;; Area filters
     [filter-layout {:size size}
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
     [filter-layout {:size size}
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
     [filter-layout {:size size}
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
     :rows-per-page-options #js [page-size]
     :label-displayed-rows
     (fn [^js props]
       (tr :search/pagination (.-from props) (.-to props) (.-count props) (.-page props)))
     :count                 (or total 0)
     :on-page-change        #(==> [::events/change-result-page %2])

     :page page
     :sx (fn [^js theme]
           #js {;; Adjust flexbox shrink so that "Näytä kerralla" and "Tuloset x-y"
                ;; shrink on narrow (mobile screens).
                ;; Enable text-overflow ellipsis for them.
                ;; Try responsive reduce margins on small screens to allow text to fit better.
                ".MuiTablePagination-spacer" #js {:flex "1 1 0%"}
                ".MuiTablePagination-selectLabel" #js {:flexBasis "auto"
                                                       :flexShrink 1
                                                       :white-space "nowrap"
                                                       :overflow "hidden"
                                                       :textOverflow "ellipsis"}
                ".MuiTablePagination-input" (clj->js {:mr 1
                                                      :ml 0.5
                                                      (.. theme -breakpoints (up "sm")) #js {:ml 4
                                                                                            :mr 1}})
                ".MuiTablePagination-displayedRows" #js {:flexBasis "auto"
                                                     :flexShrink 1
                                                     :white-space "nowrap"
                                                     :overflow "hidden"
                                                     ;; Change right-to-left text to get the ellipsis in the beginning...
                                                     ;; does this have other undesired side-effects?
                                                     :direction "rtl"
                                                     :textOverflow "ellipsis"}
                ".MuiTablePagination-actions" (clj->js {:ml 0.5
                                                        (.. theme -breakpoints (up "sm")) #js {:ml 2.5}})})}
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

    [mui/stack
     {:flexGrow 1
      :direction "column"}
     [pagination
      {:tr                tr
       :total             total
       :page              page
       :page-size         page-size
       :page-sizes        page-sizes
       :change-page-size? true
       :style             {:padding-right 0 :padding-left 0}}]

     (if in-progress?
       ;; Spinner
       [mui/circular-progress {:style {:margin-top "1em"}}]

       ;; Results
       [mui/stack
        {:flexGrow 1
         :direction "column"}
        ($ lists/virtualized-list
           {:items         results
            :key-fn        :lipas-id
            :label-fn      :name
            :label2-fn     (fn [search-doc]
                             (when search-doc
                               (str (-> search-doc :type.name) ", "
                                    (-> search-doc :location.city.name)
                                    ;; uncomment for search tuning
                                    ;;" " (-> % :score)
                                    )))
            :on-item-click on-result-click})])]))

(defn search-input
  [{:keys [max-width]}]
  (let [tr         (<== [:lipas.ui.subs/translator])
        search-str (<== [::subs/search-string])]

    [:div {:style (merge {:width "100%"} (when max-width {:max-width max-width}))}
     [mui/stack {:direction "row"}
      [lui/text-field
       {:value        search-str
        :placeholder  (tr :search/placeholder)
        :fullWidth    true
        :defer-ms     10
        :on-change    #(==> [::events/update-search-string %])
        :on-key-press (fn [e]
                        (when (= 13 (.-charCode e)) ; Enter
                          (==> [::events/search-with-keyword :fit-view])))}]

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

    [mui/stack
     {:flexGrow 1
      :direction "column"}

     (when (= result-view :table)
       [save-dialog])

     [mui/stack
      {:sx {:px 2
            :pb 1}
       :spacing     1
       :align-items "flex-start"
       :direction   "column"}

      ;; Search input and button
      (if (= :list result-view)
        [search-input]

        [mui/stack
         {:spacing 2
          :direction "row"
          :align-items "flex-end"
          :justify-content "space-between"}
         ;; LIPAS-text
         [mui/typography {:variant "h2" :style {:opacity 0.7}}
          "LIPAS"]

         [search-input {:max-width "300px"}]])

      ;; Search only from area visible on map
      (when (= result-view :list)
        [lui/checkbox
         {:label     (tr :map/bounding-box-filter)
          :disabled  (not bbox-enabled?)
          :value     bbox-only?
          :on-change #(==> [::events/set-bounding-box-filter %])}])

      ;; Filters expansion panel
      [lui/expansion-panel
       {:label            (tr :search/filters)
        :style            {:width "100%"}
        :default-expanded false}
       [filters {:tr tr :size (if (= :list result-view) "small" "large")}]]

      ;; Results count, clear filters button and result view selectors
      [:div {:style {:width "100%"}}
       [mui/stack
        {:align-items     "center"
         :spacing         1
         :justify-content "space-between"
         :direction       "row"
         :style           {:padding-top "0.5em" :padding-bottom "0.5em"}}

        ;; Results ocunt
        [mui/typography
         {:variant "body2" :style {:font-size "0.9rem" :margin-left "0.5em"}}
         (tr :search/results-count total)]

        ;; Clear filters button
        (when (and filters-active? (= :table result-view))
          [mui/button
           {:color    "secondary"
            :size     "small"
            :on-click (fn []
                        (==> [::events/clear-filters])
                        (swap! ugly-forcer inc))}
           (tr :search/clear-filters)])

        ;; Save search btn
        (when (and filters-active? logged-in? (= :table result-view))
          [mui/tooltip {:title "Tallenna haku"}
           [mui/button {:on-click #(==> [::events/toggle-save-dialog])}
            [mui/icon "save"]]])

        ;; Saved searches select
        (when (and logged-in?  (= :table result-view) saved-searches)
          [lui/select
           {:style        {:width "170px"}
            :items        saved-searches
            :value        "dummy"
            :render-value (constantly (str (tr :lipas.user/saved-searches) "..."))
            :label-fn     :name
            :value-fn     identity
            :on-change    #(==> [::events/select-saved-search %])}])

        (when (and filters-active? (= :list result-view))
          [mui/button
           {:color    "secondary"
            :size     "small"
            :on-click (fn []
                        (==> [::events/clear-filters])
                        (swap! ugly-forcer inc))}
           (tr :search/clear-filters)])

        ;; Change result view (list | table)

        [mui/stack {:direction "row"}
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
            "view_column"]]]]]]]

     ;; Results
     (if (= :list result-view)
       [results-list {:on-result-click on-result-click}]
       [results-table {:on-result-click on-result-click}])]))
