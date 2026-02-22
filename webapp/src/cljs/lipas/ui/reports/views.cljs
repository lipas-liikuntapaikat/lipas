(ns lipas.ui.reports.views
  (:require [lipas.ui.components :as lui]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
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
      ;; NOTE: Label would be nice, but probably hidden because
      ;; this label doesn't fit that well into the reports dialog
      ;; toolbar. Consider adding label element before select element?
      #_#_:label (tr :reports/file-format)
      :items     items
      :style     {:width "100px"}
      :on-change on-change}]))

(defn save-dialog []
  (r/with-let [name' (r/atom nil)]
    (let [tr        (<== [:lipas.ui.subs/translator])
          open?     (<== [::subs/save-dialog-open?])]
      [:> Dialog {:open open?}
       [:> DialogContent
        [lui/text-field
         {:label     (tr :general/name)
          :value     @name'
          :on-change #(reset! name' %)}]]
       [:> DialogActions
        [:> Button {:on-click #(==> [::events/toggle-save-dialog])}
         (tr :actions/cancel)]
        [:> Button
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
       [:> Tooltip {:title (tr :reports/tooltip)}
        (if (= btn-variant :fab)
          [:> Fab
           {:variant "circular"
            :on-click toggle
            :size "small"}
           [:> Icon "list_alt"]]
          [:> Button {:variant "contained" :color "secondary" :on-click toggle}
           (tr :reports/download-as-excel)])])

     ;; Save for later use dialog
     [save-dialog]

     ;; Dialog
     [:> Dialog {:open open? :full-width true :on-close toggle :max-width "md"}
      [:> DialogTitle
       [:> Grid
        {:container       true
         :justify-content "space-between"
         :align-items     "baseline"}
        [:> Grid {:item true}
         (tr :reports/select-fields)]
        [:> Grid {:item true}
         [:> IconButton {:on-click toggle}
          [:> Icon "close"]]]]]

      [:> DialogContent
       [:> Grid {:container true :spacing 1 :align-items "center"}

        ;; Saved reports
        (when saved-reports
          [:> Grid {:item true}
           [lui/select
            {:label     (tr :lipas.user/saved-reports)
             :style     {:width "210px"}
             :items     saved-reports
             :label-fn  :name
             :value-fn  :fields
             :on-change #(==> [::events/open-saved-report %])}]])

        ;; Save template for later use btn
        (when logged-in?
          [:> Tooltip {:title (tr :lipas.user/save-report)}
           [:> Grid {:item true}
            [:> IconButton
             {:style {:margin-bottom "-0.5em"}
              :on-click #(==> [::events/toggle-save-dialog])}
             [:> Icon "save"]]]])

        ;; Quick selects
        [:> Grid {:item true :xs 12}
         [:> Typography {:variant "body2" :style {:margin-top "1em"}}
          (tr :reports/shortcuts)]]

        (into
          [:<>]
          (for [{:keys [fields label]} quick-selects]
            [:> Grid {:item true}
             [:> Button
              {:variant  "outlined"
               :on-click #(==> [::events/set-selected-fields fields :append])}
              label]]))

        ;; Fields autocomplete selector
        [:> Grid {:item true :xs 12}
         [fields-selector
          {:tr        tr
           :on-change #(==> [::events/set-selected-fields %])
           :value     selected-fields}]]

        ;; Clear selections
        [:> Grid {:item true :xs 12}
         [:> Button
          {:style    {:margin-top "1em"}
           :variant  "outlined"
           :size     "small"
           :disabled (empty? selected-fields)
           :on-click #(==> [::events/set-selected-fields []])}
          [:> Icon "clear"]
          (tr :actions/clear-selections)]]]]

      ;; Cancel / download buttons
      [:> DialogActions
       [:> Grid {:container true :spacing 2 :align-items "center" :justify-content "flex-end"}
        [:> Grid {:item true}
         (when limits-exceeded?
           [:> Typography
            {:variant "caption"
             :color   "error"}
            (tr :reports/excel-limit-exceeded)])]

        ;; Result count
        [:> Grid {:item true}
         [:> Typography
          {:color   (if limits-exceeded? "error" "initial")}
          (tr :search/results-count results-count)]]

        [:span {:style {:width "12px"}}]

        ;; Format selector
        [:> Grid {:item true}
         [format-selector
          {:tr        tr
           :value     selected-format
           :on-change #(==> [::events/set-selected-format %])}]]

        [:> Grid {:item true}
         (when downloading?
           [:> CircularProgress])]

        ;; Cancel button
        [:> Grid {:item true}
         [:> Button {:on-click toggle :disabled downloading?}
          (tr :actions/cancel)]]

        ;; Download button
        [:> Grid {:item true}
         [:> Button
          {:disabled (or downloading? (empty? selected-fields) limits-exceeded?)
           :color    "secondary"
           :on-click #(==> [::search-events/create-report-from-current-search selected-format])}
          (tr :actions/download-excel)]]]]]]))
