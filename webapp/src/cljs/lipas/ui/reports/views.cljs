(ns lipas.ui.reports.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.events :as events]
   [lipas.ui.reports.subs :as subs]
   [lipas.ui.search.events :as search-events]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn fields-selector
  [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [::subs/fields])]
    ^{:key value}
    [lui/autocomplete
     {:value       value
      :label       (tr :search/search-more)
      :multi?      true
      :items       items
      :label-fn    (comp locale second)
      :value-fn    first
      :spacing     1
      :items-label (tr :reports/selected-fields)
      :on-change   on-change}]))

(defn format-selector
  [{:keys [tr value on-change]}]
  (let [items [{:label "Excel" :value "xlsx"}
               {:label "CSV" :value "csv"}
               {:label "GeoJSON" :value "geojson"}]]
    ^{:key value}
    [lui/select
     {:value     value
      #_#_:label (tr :reports/file-format)
      :items     items
      :style     {:width "100px"}
      :on-change on-change}]))

(defn save-dialog []
  (r/with-let [name' (r/atom nil)]
    (let [tr        (<== [:lipas.ui.subs/translator])
          open?     (<== [::subs/save-dialog-open?])]
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
          :on-click #(==> [::events/save-current-report @name'])}
         (tr :actions/save)]]])))

(defn dialog
  [{:keys [tr btn-variant]}]
  (let [open?            (<== [::subs/dialog-open?])
        toggle           #(==> [::events/toggle-dialog])
        selected-fields  (<== [::subs/selected-fields])
        selected-format  (<== [::subs/selected-format])
        downloading?     (<== [::subs/downloading?])
        results-count    (<== [:lipas.ui.search.subs/search-results-total-count])
        logged-in?       (<== [:lipas.ui.subs/logged-in?])
        saved-reports    (<== [:lipas.ui.user.subs/saved-reports])
        quick-selects    (<== [::subs/quick-selects])
        limits-exceeded? (<== [::subs/limits-exceeded?])]
    [:<>
     ;; Open Dialog button
     (when (< 0 results-count)
       [mui/tooltip {:title (tr :reports/tooltip)}
        (if (= btn-variant :fab)
          [mui/fab
           {:variant "circular"
            :on-click toggle
            :size "small"}
           [mui/icon "list_alt"]]
          [mui/button {:variant "contained" :color "secondary" :on-click toggle}
           (tr :reports/download-as-excel)])])

     ;; Save for later use dialog
     [save-dialog]

     ;; Dialog
     [mui/dialog {:open open? :full-width true :on-close toggle :max-width "md"}
      [mui/dialog-title
       [mui/grid
        {:container       true
         :justify-content "space-between"
         :align-items     "baseline"}
        [mui/grid {:item true}
         (tr :reports/select-fields)]
        [mui/grid {:item true}
         [mui/icon-button {:on-click toggle}
          [mui/icon "close"]]]]]

      [mui/dialog-content
       [mui/grid {:container true :spacing 1 :align-items "center"}

        ;; Saved reports
        (when saved-reports
          [mui/grid {:item true}
           [lui/select
            {:label     (tr :lipas.user/saved-reports)
             :style     {:width "210px"}
             :items     saved-reports
             :label-fn  :name
             :value-fn  :fields
             :on-change #(==> [::events/open-saved-report %])}]])

        ;; Save template for later use btn
        (when logged-in?
          [mui/tooltip {:title (tr :lipas.user/save-report)}
           [mui/grid {:item true}
            [mui/icon-button
             {:style {:margin-bottom "-0.5em"}
              :on-click #(==> [::events/toggle-save-dialog])}
             [mui/icon "save"]]]])

        ;; Quick selects
        [mui/grid {:item true :xs 12}
         [mui/typography {:variant "body2" :style {:margin-top "1em"}}
          (tr :reports/shortcuts)]]

        (into
         [:<>]
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
         [mui/button
          {:style    {:margin-top "1em"}
           :variant  "outlined"
           :size     "small"
           :disabled (empty? selected-fields)
           :on-click #(==> [::events/set-selected-fields []])}
          [mui/icon "clear"]
          (tr :actions/clear-selections)]]]]

      ;; Cancel / download buttons
      [mui/dialog-actions
       [mui/grid {:container true :spacing 2 :align-items "center" :justify-content "flex-end"}
        [mui/grid {:item true}
         (when limits-exceeded?
           [mui/typography
            {:variant "caption"
             :color   "error"}
            (tr :reports/excel-limit-exceeded)])]

        ;; Result count
        [mui/grid {:item true}
         [mui/typography
          {:variant "caption"
           :color   (if limits-exceeded? "error" "initial")}
          (tr :search/results-count results-count)]]

        [:span {:style {:width "12px"}}]

        ;; Format selector
        [mui/grid {:item true}
         [format-selector
          {:tr        tr
           :value     selected-format
           :on-change #(==> [::events/set-selected-format %])}]]

        [mui/grid {:item true}
         (when downloading?
           [mui/circular-progress])]

        ;; Cancel button
        [mui/grid {:item true}
         [mui/button {:on-click toggle :disabled downloading?}
          (tr :actions/cancel)]]

        ;; Download button
        [mui/grid {:item true}
         [mui/button
          {:disabled (or downloading? (empty? selected-fields) limits-exceeded?)
           :color    "secondary"
           :on-click #(==> [::search-events/create-report-from-current-search selected-format])}
          (tr :actions/download-excel)]]]]]]))
