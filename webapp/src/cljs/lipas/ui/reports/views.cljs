(ns lipas.ui.reports.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.events :as events]
   [lipas.ui.reports.subs :as subs]
   [lipas.ui.search.events :as search-events]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

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
    :fields ["type.type-name"]}
   {:label  (tr :lipas.location/city)
    :fields ["location.city.city-name"]}])

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

(defn dialog [{:keys [tr]}]
  (let [open?           (<== [::subs/dialog-open?])
        toggle          #(==> [::events/toggle-dialog])
        selected-fields (<== [::subs/selected-fields])
        downloading?    (<== [::subs/downloading?])
        results-count   (<== [:lipas.ui.search.subs/search-results-total-count])

        quick-selects (make-quick-selects tr)]
    [:<>
     ;; Open Dialog button
     (when (< 0 results-count)
       [mui/button {:style    {:margin-top "1em"}
                    :variant  "extendedFab"
                    :color    "secondary"
                    :on-click toggle}
        [mui/icon "arrow_right"]
        (tr :reports/download-as-excel)])

     ;; Dialog
     [mui/dialog {:open       open?
                  :full-width true
                  :on-close   toggle}
      [mui/dialog-title (tr :reports/select-fields)]
      [mui/dialog-content
       [mui/grid {:container true :spacing 8}

        ;; Quick selects
        [mui/grid {:item true :xs 12}
         [mui/typography {:variant "body2" :style {:margin-top "1em"}}
          (tr :reports/shortcuts)]]

        (into [:<>]
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
         [mui/button {:style    {:margin-top "1em"}
                      :variant  "outlined"
                      :size     "small"
                      :disabled (empty? selected-fields)
                      :on-click #(==> [::events/set-selected-fields []])}
          [mui/icon "clear"]
          (tr :actions/clear-selections)]]]]

      ;; Cancel / download buttons
      [mui/dialog-actions
       (when downloading?
         [mui/circular-progress])
       [mui/button {:on-click toggle
                    :disabled downloading?}
        (tr :actions/cancel)]
       [mui/button
        {:disabled (or downloading? (empty? selected-fields))
         ;;:variant  ""
         :color    "secondary"
         :on-click #(==> [::search-events/create-report-from-current-search])}
        (tr :reports/download-excel)]]]]))
