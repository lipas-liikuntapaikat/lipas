(ns lipas.ui.search.views
  (:require [lipas.data.prop-types :as prop-types]
            [lipas.roles :as roles]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [lipas.ui.components.lists :as lists]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/TablePagination$default" :as TablePagination]
            ["@mui/material/ToggleButton$default" :as ToggleButton]
            ["@mui/material/ToggleButtonGroup$default" :as ToggleButtonGroup]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.reports.views :as reports]
            [lipas.ui.search.events :as events]
            [lipas.ui.search.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [malli.core :as m]
            [reagent.core :as r]))

(defn- filter-layout
  [{:keys [size] :as _props} & children]
  [:> Grid (merge {:item true} (when (= "large" size) {:xs 12 :md 6 :lg 4}))
   (into [:> Grid {:container true :direction "column"}]
         (for [c children]
           [:> Grid {:item true}
            c]))])

;; Text-fields are nasty to reset and filters contain many of
;; them. Therefore we use this ugly hack to change react-key for the
;; filter container component when "clear filters" button is clicked.
(def ugly-forcer (r/atom 0))

(defn property-filter-input
  "Renders an input component for a single property filter based on its data type"
  [{:keys [tr prop-key prop-def filter-value on-change]}]
  (let [data-type (:data-type prop-def)
        prop-name (get-in prop-def [:name :fi])]

    (case data-type
      "numeric"
      [:> Grid {:container true :spacing 2}
       [:> Grid {:item true :xs 6}
        [lui/text-field
         {:label "Min"
          :defer-ms 500
          :type "number"
          :value (:min filter-value)
          :on-change #(on-change (assoc filter-value :type :range :min %))}]]
       [:> Grid {:item true :xs 6}
        [lui/text-field
         {:label "Max"
          :defer-ms 500
          :type "number"
          :value (:max filter-value)
          :on-change #(on-change (assoc filter-value :type :range :max %))}]]]

      "boolean"
      [:> ToggleButtonGroup
       {:value (case (:value filter-value)
                 true "yes"
                 false "no"
                 "all")
        :exclusive true
        :size "small"
        :on-change (fn [_event new-value]
                     (when new-value
                       (on-change {:type :boolean
                                   :value (case new-value
                                            "yes" true
                                            "no" false
                                            "all" nil)})))}
       [:> ToggleButton {:value "all"}
        (tr :search/boolean-filter-all)]
       [:> ToggleButton {:value "yes"}
        (tr :search/boolean-filter-yes)]
       [:> ToggleButton {:value "no"}
        (tr :search/boolean-filter-no)]]

      "string"
      [lui/text-field
       {:label (tr :search/contains-text)
        :placeholder (str prop-name "...")
        :defer-ms 500
        :value (:text filter-value)
        :on-change #(on-change {:type :string :text %})}]

      "enum"
      (let [options (->> (:opts prop-def)
                         (map (fn [[k v]]
                                {:key k
                                 :label (get-in v [:label :fi] k)}))
                         (sort-by :label))]
        [lui/autocomplete
         {:label prop-name
          :value (first (:values filter-value))
          :items options
          :value-fn :key
          :label-fn :label
          :key-fn :key
          :on-change #(when % (on-change {:type :enum :values [%]}))}])

      "enum-coll"
      (let [options (->> (:opts prop-def)
                         (map (fn [[k v]]
                                {:value k
                                 :label (get-in v [:label :fi] k)}))
                         (sort-by :label))
            ;; Map stored keyword values back to full option objects
            selected-values (set (:values filter-value))
            selected-options (filter #(contains? selected-values (:value %)) options)]
        (r/as-element
         [autocomplete2
          {:label prop-name
           :multiple true
           :value (to-array selected-options)
           :options options
           :onChange (fn [_event selected-items]
                       (let [values (vec (map :value (js->clj selected-items :keywordize-keys true)))]
                         (on-change {:type :enum :values values})))}]))

      ;; Default fallback
      [:> Typography {:variant "caption"}
       (str "Unsupported type: " data-type)])))

(defn- get-available-properties
  "Returns a vector of all available properties with their metadata for autocomplete"
  []
  (->> prop-types/all
       (map (fn [[prop-key prop-def]]
              {:key prop-key
               :label (get-in prop-def [:name :fi] (name prop-key))
               :data-type (:data-type prop-def)
               :def prop-def}))
       (sort-by :label)
       vec))

(defn add-property-filter
  "Component for adding a new property filter via autocomplete"
  [{:keys [tr size]}]
  (let [prop-filters (<== [::subs/properties-filters])
        available-props (get-available-properties)

        ;; Filter out already added properties
        unselected-props (remove (fn [prop]
                                   (contains? prop-filters (:key prop)))
                                 available-props)]

    [filter-layout {:size size}
     [lui/autocomplete
      {:label (tr :search/add-property-filter)
       :value nil
       :items unselected-props ; Pass full property objects
       :value-fn :key ; Extract :key for values
       :label-fn :label ; Extract :label for display
       :key-fn :key ; Use :key for React keys (ensures uniqueness)
       :on-change (fn [prop-key]
                    (when prop-key
                      (let [prop-def (get prop-types/all prop-key)
                            data-type (:data-type prop-def)
                            initial-value (case data-type
                                            "numeric" {:type :range}
                                            "boolean" {:type :boolean :value nil}
                                            "string" {:type :string :text ""}
                                            "enum" {:type :enum :values []}
                                            "enum-coll" {:type :enum :values []}
                                            {})]
                        (==> [::events/set-property-filter prop-key initial-value]))))}]]))

(defn active-property-filter
  "Component for displaying and editing a single active property filter"
  [{:keys [tr size prop-key]}]
  (let [prop-filters (<== [::subs/properties-filters])
        filter-value (get prop-filters prop-key)
        prop-def (get prop-types/all prop-key)
        prop-name (get-in prop-def [:name :fi] (name prop-key))]

    (when (and filter-value prop-def)
      [filter-layout {:size size}
       [:> Stack {:spacing 1 :direction "column"}
        [:> Stack {:direction "row" :spacing 1 :align-items "center"}
         [:> Typography {:variant "caption" :sx {:flex-grow 1}}
          prop-name]
         [:> IconButton
          {:size "small"
           :on-click #(==> [::events/remove-property-filter prop-key])}
          [:> Icon "close"]]]

        [property-filter-input
         {:tr tr
          :prop-key prop-key
          :prop-def prop-def
          :filter-value filter-value
          ;; Always update the filter value, never auto-remove
          ;; Filters are only removed when user clicks the X button
          :on-change #(==> [::events/set-property-filter prop-key %])}]]])))

(defn property-filters
  "Inline property filters with Add Filter button"
  [{:keys [tr size]}]
  (let [prop-filters (<== [::subs/properties-filters])
        active-filter-keys (keys prop-filters)]

    [:<>
     ;; Render active property filters
     (for [prop-key active-filter-keys]
       ^{:key prop-key}
       [active-property-filter {:tr tr :size size :prop-key prop-key}])

     ;; Add Filter button
     [add-property-filter {:tr tr :size size}]

     ;; Clear all property filters button (only show when filters exist)
     (when (not-empty prop-filters)
       [filter-layout {:size size}
        [:> Button
         {:color "secondary"
          :variant "outlined"
          :size "small"
          :on-click #(==> [::events/clear-property-filters])}
         (tr :search/clear-property-filters)]])]))

(defn filters
  [{:keys [tr size]}]
  (let [logged-in? (<== [:lipas.ui.user.subs/logged-in?])
        statuses (<== [::subs/statuses-filter])
        type-codes (<== [::subs/types-filter])
        city-codes (<== [::subs/cities-filter])
        admins (<== [::subs/admins-filter])
        owners (<== [::subs/owners-filter])
        year-min (<== [::subs/construction-year-min-filter])
        year-max (<== [::subs/construction-year-max-filter])
        has-edit-permission? (<== [::subs/edit-permission-filter])]

    ^{:key @ugly-forcer}
    [:> Grid
     {:container true
      :spacing 3
      :direction (if (= "large" size) "row" "column")}

     ;; Permissions filter
     (when (<== [:lipas.ui.user.subs/check-privilege
                 {:city-code ::roles/any
                  :type-code ::roles/any
                  :lipas-id ::roles/any}
                 :site/create-edit])
       [filter-layout {:size size}
        [lui/checkbox
         {:value has-edit-permission?
          :label (tr :search/permissions-filter)
          :on-change #(==> [::events/set-filters-by-permissions %])}]])

     ;; Types filter
     [filter-layout {:size size}
      [:> Typography {:variant "caption"}
       (tr :actions/select-types)]

      [lui/type-category-selector
       {:tr tr
        :value type-codes
        :on-change #(==> [::events/set-type-filter %])}]]

     ;; Regions filter (cities, avis, provinces)
     [filter-layout {:size size}
      [:> Typography {:variant "caption"}
       (tr :actions/select-cities)]

      [lui/region-selector
       {:value city-codes
        :on-change #(==> [::events/set-city-filter %])}]]

     ;; Admins filter
     [filter-layout {:size size}
      [:> Typography {:variant "caption"}
       (tr :actions/select-admins)]

      [lui/admin-selector
       {:tr tr
        :value admins
        :on-change #(==> [::events/set-admins-filter %])}]]

     ;; Owners filter
     [filter-layout {:size size}
      [:> Typography {:variant "caption"}
       (tr :actions/select-owners)]

      [lui/owner-selector
       {:tr tr
        :value owners
        :on-change #(==> [::events/set-owners-filter %])}]]

     ;; Construction year filters
     [filter-layout {:size size}
      [:> Typography {:variant "caption"}
       (tr :actions/filter-construction-year)]

      [:> Grid {:container true :spacing 2}

       ;; Construction year min filter
       [:> Grid {:item true :xs 6}
        [lui/text-field
         {:label "Min"
          :defer-ms 500
          :type "number"
          :value year-min
          :on-change #(==> [::events/set-construction-year-min-filter %])}]]

       ;; Construction year max
       [:> Grid {:item true :xs 6}
        [lui/text-field
         {:label "Max"
          :defer-ms 500
          :type "number"
          :value year-max
          :on-change #(==> [::events/set-construction-year-max-filter %])}]]]]

     ;; Statuses filter
     [filter-layout {:size size}
      [lui/status-selector
       {:multi? true
        :value statuses
        :on-change #(==> [::events/set-status-filter %])}]]

     ;; Property filters (inline with "Add Filter" button)
     [property-filters {:tr tr :size size}]]))
(defn pagination
  [{:keys [tr page page-size page-sizes total change-page-size?] :as props}]
  [:> TablePagination
   (merge
    {:component "div"
     :rows-per-page page-size
     :rows-per-page-options #js [page-size]
     :label-displayed-rows
     (fn [^js props]
       (tr :search/pagination (.-from props) (.-to props) (.-count props) (.-page props)))
     :count (or total 0)
     :on-page-change #(==> [::events/change-result-page %2])

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
      {:rows-per-page-options (clj->js page-sizes)
       :on-rows-per-page-change #(==> [::events/change-result-page-size
                                       (-> %1 .-target .-value)])
       :label-rows-per-page (tr :search/page-size)})
    (dissoc props :tr :change-page-size? :total :page-sizes :page-size))])

(defn results-table [{:keys [on-result-click]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        specs (<== [::subs/results-table-specs])
        headers (<== [::subs/results-table-headers])
        selected-columns (<== [::subs/selected-results-table-columns])
        sort-opts (<== [::subs/sort-opts])
        in-progress? (<== [::subs/in-progress?])
        results (<== [::subs/search-results-table-data])
        total (<== [::subs/search-results-total-count])
        pagination-opts (<== [::subs/pagination])
        page-sizes (-> pagination-opts :page-sizes)
        page-size (-> pagination-opts :page-size)
        page (-> pagination-opts :page)]

    [:> Grid
     {:container true
      :align-items "center"
      :justify-content "space-between"
      :style {:padding "0.5em"}}

     ;; Pagination
     [:> Grid {:item true}
      [pagination
       {:tr tr
        :total total
        :page page
        :page-size page-size
        :page-sizes page-sizes
        :change-page-size? true
        :style {:margin-right "2em"}}]]

     [:> Grid {:item true}
      [reports/dialog {:tr tr}]]

     ;; Rank results close to map center higher
     [:> Grid {:item true}
      [lui/checkbox
       {:style {:height "100%"}
        :label (tr :search/display-closest-first)
        :value (= :score (:sort-fn sort-opts))
        :on-change #(==> [::events/toggle-sorting-by-distance])}]]

     ;; Select table columns
     [:> Grid {:item true :style {:padding-right "0.5em"}}
      [lui/search-results-column-selector
       {:value selected-columns
        :on-change #(==> [::events/select-results-table-columns %])}]]

     ;; The table
     [:> Grid {:item true :xs 12}
      [lui/table-v2
       {:key (:sort-fn sort-opts)
        :in-progress? in-progress?
        :items results
        :action-icon "location_on"
        :action-label (tr :map/zoom-to-site)
        :edit-label (tr :actions/edit)
        :save-label (tr :actions/save)
        :discard-label (tr :actions/discard)
        :on-select #(on-result-click %)
        :sort-asc? (:asc? sort-opts)
        :allow-editing? :permission?
        :allow-saving? (fn [item]
                         (->> item
                              (reduce
                               (fn [res [k v]]
                                 (if-let [spec (and (or (-> k specs :required?)
                                                        (some? v))
                                                    (-> k specs :spec))]
                                   (conj res (m/validate spec v))
                                   (conj res true)))
                               [])
                              (every? true?)))
        :on-item-save #(==> [::events/save-edits %])
        :on-sort-change #(==> [::events/change-sort-order %])
        :on-edit-start #(==> [:lipas.ui.sports-sites.events/get (:lipas-id %)])
        :headers headers}]]

     ;; Pagination vol 2
     [:> Grid {:item true}
      [pagination
       {:tr tr
        :total total
        :page page
        :page-size page-size
        :page-sizes page-sizes
        :change-page-size? true}]]]))

(defn results-list [{:keys [on-result-click]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        in-progress? (<== [::subs/in-progress?])
        results (<== [::subs/search-results-list-data])
        total (<== [::subs/search-results-total-count])

        pagination-opts (<== [::subs/pagination])

        page-sizes (-> pagination-opts :page-sizes)
        page-size (-> pagination-opts :page-size)
        page (-> pagination-opts :page)]

    [:> Stack
     {:flexGrow 1
      :direction "column"}
     [pagination
      {:tr tr
       :total total
       :page page
       :page-size page-size
       :page-sizes page-sizes
       :change-page-size? true
       :style {:padding-right 0 :padding-left 0}}]

     ;; Results
     [:> Stack
      {:flexGrow 1
       :direction "column"
       :sx {:position "relative"}}
      ;; Keep the results list in React tree even when loading new results.
      ;; Unmounting and mounting the virtualized-list would force e.g.
      ;; new measurement, which causes extra rendering flashing.
      ;; When loading new results, show a overlay and a spinner over the results
      ;; list.
      (when in-progress?
        [:> Stack
         {:sx {:position "absolute"
               :top 0
               :bottom 0
               :left 0
               :right 0
               :backgroundColor "rgba(255, 255, 255, 0.6)"
               :zIndex 1000
               :alignItems "center"}}
         ;; Spinner
         [:> CircularProgress {:style {:margin-top "1em"}}]])
      (r/as-element
       [lists/virtualized-list
        {:items results
         :key-fn :lipas-id
         :landing-bay? true
         :label-fn :name
         :label2-fn (fn [search-doc]
                      (when search-doc
                        (str (-> search-doc :type.name) ", "
                             (-> search-doc :location.city.name)
                                 ;; uncomment for search tuning
                                 ;;" " (-> % :score)
                             )))
         :on-item-click on-result-click}])]]))

(defn search-input
  [{:keys [max-width]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        search-str (<== [::subs/search-string])]

    [:div {:style (merge {:width "100%"} (when max-width {:max-width max-width}))}
     [:> Stack {:direction "row"}
      [lui/text-field
       {:value search-str
        :placeholder (tr :search/placeholder)
        :fullWidth true
        :defer-ms 10
        :on-change #(==> [::events/update-search-string %])
        :on-key-press (fn [e]
                        (when (= 13 (.-charCode e)) ; Enter
                          (==> [::events/search-with-keyword :fit-view])))}]

      [:> Button {:on-click #(==> [::events/search-with-keyword :fit-view])}
       [:> Icon "search"]
       (tr :search/search)]]]))

(defn save-dialog []
  (r/with-let [name' (r/atom nil)]
    (let [tr (<== [:lipas.ui.subs/translator])
          open? (<== [::subs/save-dialog-open?])]
      [:> Dialog {:open open?}
       [:> DialogContent
        [lui/text-field
         {:label (tr :general/name)
          :value @name'
          :on-change #(reset! name' %)}]]
       [:> DialogActions
        [:> Button {:on-click #(==> [::events/toggle-save-dialog])}
         (tr :actions/cancel)]
        [:> Button
         {:disabled (empty? @name')
          :on-click #(==> [::events/save-current-search @name'])}
         (tr :actions/save)]]])))

(defn search-view [{:keys [tr on-result-click]}]
  (let [total (<== [::subs/search-results-total-count])
        result-view (<== [::subs/search-results-view])
        filters-active? (<== [::subs/filters-active?])
        bbox-only? (<== [::subs/bounding-box-filter])
        bbox-enabled? (<== [::subs/allow-changing-bounding-box-filter?])
        saved-searches (<== [:lipas.ui.user.subs/saved-searches])
        logged-in? (<== [:lipas.ui.subs/logged-in?])]

    [:> Stack
     {:flexGrow 1
      :direction "column"}

     (when (= result-view :table)
       [save-dialog])

     [:> Stack
      {:sx {:px 2
            :pb 1}
       :spacing 1
       :align-items "flex-start"
       :direction "column"}

      ;; Search input and button
      (if (= :list result-view)
        [search-input]

        [:> Stack
         {:spacing 2
          :direction "row"
          :align-items "flex-end"
          :justify-content "space-between"}
         ;; LIPAS-text
         [:> Typography {:variant "h2" :style {:opacity 0.7}}
          "LIPAS"]

         [search-input {:max-width "300px"}]])

      ;; Search only from area visible on map
      (when (= result-view :list)
        [lui/checkbox
         {:label (tr :map/bounding-box-filter)
          :disabled (not bbox-enabled?)
          :value bbox-only?
          :on-change #(==> [::events/set-bounding-box-filter %])}])

;; Filters expansion panel
      [lui/expansion-panel
       {:label (tr :search/filters)
        :style {:width "100%"}
        :default-expanded false}
       [filters {:tr tr :size (if (= :list result-view) "small" "large")}]]

      ;; Results count, clear filters button and result view selectors
      [:div {:style {:width "100%"}}
       [:> Stack
        {:align-items "center"
         :spacing 1
         :justify-content "space-between"
         :direction "row"
         :style {:padding-top "0.5em" :padding-bottom "0.5em"}}

        ;; Results ocunt
        [:> Typography
         {:variant "body2" :style {:font-size "0.9rem" :margin-left "0.5em"}}
         (tr :search/results-count total)]

        ;; Clear filters button
        (when (and filters-active? (= :table result-view))
          [:> Button
           {:color "secondary"
            :size "small"
            :on-click (fn []
                        (==> [::events/clear-filters])
                        (swap! ugly-forcer inc))}
           (tr :search/clear-filters)])

        ;; Save search btn
        (when (and filters-active? logged-in? (= :table result-view))
          [:> Tooltip {:title "Tallenna haku"}
           [:> Button {:on-click #(==> [::events/toggle-save-dialog])}
            [:> Icon "save"]]])

        ;; Saved searches select
        (when (and logged-in? (= :table result-view) saved-searches)
          [lui/select
           {:style {:width "170px"}
            :items saved-searches
            :value "dummy"
            :render-value (constantly (str (tr :lipas.user/saved-searches) "..."))
            :label-fn :name
            :value-fn identity
            :on-change #(==> [::events/select-saved-search %])}])

        (when (and filters-active? (= :list result-view))
          [:> Button
           {:color "secondary"
            :size "small"
            :on-click (fn []
                        (==> [::events/clear-filters])
                        (swap! ugly-forcer inc))}
           (tr :search/clear-filters)])

        ;; Change result view (list | table)

        [:> Stack {:direction "row"}
         [:> Tooltip {:title (tr :search/list-view)}
          [:> IconButton {:on-click #(==> [::events/set-results-view :list])}
           [:> Icon {:color (if (= :list result-view)
                               "secondary"
                               "inherit")}
            "view_stream"]]]

         [:> Tooltip {:title (tr :search/table-view)}
          [:> IconButton {:on-click #(==> [::events/set-results-view :table])}
           [:> Icon {:color (if-not (= :list result-view)
                               "secondary"
                               "inherit")}
            "view_column"]]]]]]]

     ;; Results
     (if (= :list result-view)
       [results-list {:on-result-click on-result-click}]
       [results-table {:on-result-click on-result-click}])]))
