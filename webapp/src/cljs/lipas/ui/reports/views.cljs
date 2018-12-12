(ns lipas.ui.reports.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.reports.events :as events]
   [lipas.ui.reports.subs :as subs]
   [lipas.ui.search.events :as search-events]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn fields-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [::subs/fields])]
    ^{:key value}
    [lui/autocomplete
     {:value       value
      :label       (tr :search/search)
      :items       items
      :label-fn    (comp locale second)
      :value-fn    first
      :spacing     8
      :items-label (tr :reports/selected-fields)
      :on-change   on-change}]))

(defn dialog [{:keys [tr]}]
  (r/with-let [open?  (r/atom false)
               toggle #(swap! open? not)]
    (let [selected-fields (<== [::subs/selected-fields])
          downloading?    (<== [::subs/downloading?])
          results-count   (<== [:lipas.ui.search.subs/search-results-total-count])]
      [:<>
       ;; Open Dialog button
       (when (< 0 results-count )
         [mui/button {:style    {:margin-top "1em"}
                      :on-click toggle}
          [mui/icon "arrow_right"]
          (tr :reports/download-as-excel)])

       ;; Dialog
       [mui/dialog {:open       @open?
                    :full-width true
                    :on-close   toggle}
        [mui/dialog-title (tr :reports/select-fields)]
        [mui/dialog-content
         [mui/grid {:container true :spacing 8}

          ;; Quick selects
          [mui/grid {:item true :xs 12}
           [mui/typography
            {:variant "body2"
             :style   {:margin-top "1em"}}
            (tr :reports/shortcuts)]]

          ;;; Basic data
          [mui/grid {:item true}
           [mui/button
            {:variant  "outlined"
             :on-click #(==> [::events/select-basic-fields :append])}
            (tr :lipas.sports-site/basic-data)]]

          ;;; Measures
          [mui/grid {:item true}
           [mui/button
            {:variant  "outlined"
             :on-click #(==> [::events/select-measure-fields :append])}
            (tr :general/measures)]]

          ;;; Surface materials
          [mui/grid {:item true}
           [mui/button
            {:variant  "outlined"
             :on-click #(==> [::events/select-surface-material-fields :append])}
            (tr :lipas.sports-site/surface-materials)]]

          ;; Fields autocomplete selector
          [mui/grid {:item true :xs 12}
           [fields-selector {:tr        tr
                             :on-change #(==> [::events/set-selected-fields %])
                             :value     selected-fields}]]

          ;; Clear selections
          [mui/grid {:item true :xs 12}
           [mui/button {:style    {:margin-top "1em"}
                        :variant  "outlined"
                        :size     "small"
                        :on-click #(==> [::events/set-selected-fields []])}
            [mui/icon "clear"]
            (tr :actions/clear-selections)]]]]

        ;; Cancel / download buttons
        [mui/dialog-actions
         (when downloading?
           [mui/circular-progress])
         [mui/button {:on-click toggle
                      :disabled downloading?}
          "Peruuta"]
         [mui/button
          {:disabled downloading?
           ;;:variant  ""
           :color "secondary"
           :on-click #(==> [::search-events/create-report-from-current-search])}
          (tr :reports/download-excel)]]]])))
