(ns lipas.ui.reports.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.events :as events]
   [lipas.ui.reports.subs :as subs]
   [lipas.ui.search.events :as search-events]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn- make-quick-selects [tr]
  [{:label  (tr :lipas.sports-site/basic-data)
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
    :fields ["type.type-name"
             "search-meta.type.main-category.name.fi"
             "search-meta.type.sub-category.name.fi"]}
   {:label  (tr :lipas.location/city)
    :fields ["location.city.city-name"
             "search-meta.location.province.name.fi"
             "search-meta.location.avi-area.name.fi"]}
   {:label  (tr :general/last-modified)
    :fields ["event-date"]}])

(defn fields-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [::subs/fields])]
    ^{:key value}
    [lui/autocomplete
     {:value       value
      :label       (tr :search/search-more)
      :items       items
      :label-fn    (comp locale second)
      :value-fn    first
      :spacing     8
      :items-label (tr :reports/selected-fields)
      :on-change   on-change}]))

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

(defn dialog [{:keys [tr btn-variant]}]
  (let [open?           (<== [::subs/dialog-open?])
        toggle          #(==> [::events/toggle-dialog])
        selected-fields (<== [::subs/selected-fields])
        downloading?    (<== [::subs/downloading?])
        results-count   (<== [:lipas.ui.search.subs/search-results-total-count])
        logged-in?      (<== [:lipas.ui.subs/logged-in?])
        saved-reports   (<== [:lipas.ui.user.subs/saved-reports])

        quick-selects (make-quick-selects tr)]
    [:<>
     ;; Open Dialog button
     (when (< 0 results-count)
       [mui/tooltip {:title (tr :reports/tooltip)}
        (if (= btn-variant :fab)
          [mui/fab
           {:variant "round" :color "default" :on-click toggle :size "small"}
           [mui/icon "list_alt"]]
          [mui/button {:variant "contained" :color "secondary" :on-click toggle}
           (tr :reports/download-as-excel)])])

     ;; Save for later use dialog
     [save-dialog]

     ;; Dialog
     [mui/dialog {:open open? :full-width true :on-close toggle}
      [mui/dialog-title
       [mui/grid {:container true :justify "space-between" :align-items "baseline"}
        [mui/grid {:item true}
         (tr :reports/select-fields)]

        ;; Save template for later use btn
        (when logged-in?
          [mui/tooltip {:title (tr :lipas.user/save-report)}
           [mui/grid {:item true}
            [mui/icon-button
             {:on-click #(==> [::events/toggle-save-dialog])}
             [mui/icon "save"]]]])]]

      [mui/dialog-content
       [mui/grid {:container true :spacing 8}

        ;; Saved reports
        (when saved-reports
          [mui/grid {:item true :xs 12}
           [lui/select
            {:label     (tr :lipas.user/saved-reports)
             :style     {:width "210px"}
             :items     saved-reports
             :label-fn  :name
             :value-fn  :fields
             :on-change #(==> [::events/open-saved-report %])}]])

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
       [mui/typography {:variant "caption"}
        (tr :search/results-count results-count)]
       (when downloading?
         [mui/circular-progress])
       [mui/button {:on-click toggle :disabled downloading?}
        (tr :actions/cancel)]
       [mui/button
        {:disabled (or downloading? (empty? selected-fields))
         :color    "secondary"
         :on-click #(==> [::search-events/create-report-from-current-search])}
        (tr :actions/download-excel)]]]]))
