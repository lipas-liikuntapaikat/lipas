(ns lipas.ui.ptv.site-view
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.components :as lui]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

(defui site-view [{:keys [tr lipas-id can-edit? edit-data]}]
  (let [[selected-tab set-selected-tab] (uix/use-state :fi)

        editing*   (boolean (use-subscribe [:lipas.ui.sports-sites.subs/editing? lipas-id]))
        editing?   (and can-edit? editing*)
        read-only? (not editing?)
        site       (use-subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])

        ;; default-settings {}
        enabled    (boolean (:ptv site))
        descriptions-enabled (not= "manual" (:descriptions-integration (:ptv site)))

        loading?   (use-subscribe [::subs/generating-descriptions?])]
    ($ Stack
       {:direction "column"
        :sx #js {:gap 2}}

       (when (not enabled)
         ($ Alert
            {:severity "warning"}
            "PTV integraatio ei käytössä tälle paikalle, käytä PTV käyttöönotto wizardia PTV palvelun alustamiseen."))

       ($ FormControl
          ($ FormLabel
             "Lipas tila")
          ($ Typography
             (:status site)))

       ($ FormControl
          ($ FormLabel
             "PTV Tila")
          ($ Typography
             (:publishing-status (:ptv site)))
          ($ Typography
             (:last-sync (:ptv site))))

       #_
       ($ controls/description-integration
          {:value (:descriptions-integration (:ptv site))
           :on-change identity
           :tr tr})

       (when (= "lipas-managed-ptv-fields" (:descriptions-integration (:ptv site)))
          ($ Stack
             {:sx #js {:position "relative"}}
             ($ Button
                {:disabled (or loading?
                               read-only?)
                 :variant "outlined"
                 ;; NOTE: Could use the lipas-id version when not editing? But then we don't have
                 ;; place to store the results.
                 :on-click (fn [_e]
                              (rf/dispatch [::events/generate-descriptions-from-data lipas-id]))}
                (tr :ptv.actions/generate-with-ai))
             (when loading?
                ($ CircularProgress
                   {:size 24
                    :sx #js {:position "absolute"
                             :top "50%"
                             :left "50%"
                             :mt "-12px"}}))))

       ($ controls/lang-selector
          {:value selected-tab
           :on-change set-selected-tab})

       ;; Summary
       (r/as-element
         [lui/text-field
          {:disabled   (or loading?
                           (not descriptions-enabled)
                           read-only?)
           :multiline  true
           :variant    "outlined"
           :on-change  (fn [v]
                         (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :summary selected-tab] v]))
           :label      "Tiivistelmä"
           :value      (or (get-in edit-data [:ptv :summary selected-tab])
                           (get-in site [:ptv :summary selected-tab]))}])

       ;; Description
       (r/as-element
         [lui/text-field
          {:disabled   (or loading?
                           (not descriptions-enabled)
                           read-only?)
           :variant    "outlined"
           :rows       5
           :multiline  true
           :on-change  (fn [v]
                         (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :description selected-tab] v]))
           :label      "Kuvaus"
           :value      (or (get-in edit-data [:ptv :description selected-tab])
                           (get-in site [:ptv :description selected-tab]))}]))))
