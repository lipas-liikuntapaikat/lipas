(ns lipas.ui.ptv.site-view
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Typography$default" :as Typography]
            [goog.string.format]
            [lipas.ui.components :as lui]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.utils :refer [<== ==>]]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

(defui site-view [{:keys [_tr lipas-id can-edit? edit-data]}]
  (let [[selected-tab set-selected-tab] (uix/use-state "fi")

        editing? (and can-edit? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id]))
        read-only? (not editing?)
        site (use-subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])

        loading? false]
    (js/console.log site)
    (js/console.log edit-data)
    ($ Stack
       {:direction "column"
        :sx #js {:gap 2}}

       ($ FormControl
          ($ FormLabel
             "Lipas tila")
          ($ Typography
             (:status site)))

       ($ FormControl
          ($ FormLabel
             "PTV Tila")
          ($ Typography
             (:publishing-status (:ptv site))
             (:last-sync (:ptv site))))

       ($ Tabs
          {:value     selected-tab
           :on-change (fn [_e v] (set-selected-tab v))}
          ($ Tab {:value "fi" :label "FI"})
          ($ Tab {:value "se" :label "SE"})
          ($ Tab {:value "en" :label "EN"}))

       ;; Summary
       (r/as-element
         [lui/text-field
          {:disabled   loading?
           :multiline  true
           :read-only? (or (not= "manual" (:descriptions-integration site))
                           read-only?)
           :variant    "outlined"
           :on-change  #(==> [::events/set-summary site selected-tab %])
           :label      "Tiivistelmä"
           :value      (get-in site [:ptv :summary selected-tab])}])

       ;; Description
       (r/as-element
         [lui/text-field
          {:disabled   loading?
           :variant    "outlined"
           :read-only? (or (not= "manual" (:descriptions-integration site))
                           read-only?)
           :rows       5
           :multiline  true
           :on-change  #(==> [::events/set-description site selected-tab %])
           :label      "Kuvaus"
           :value      (get-in site [:ptv :description selected-tab])}])

       ($ Button
          {:variant "contained"
           :color "primary"
           :disabled (or (not can-edit?)
                         loading?)
           :on-click #(==> [::events/create-ptv-service-location lipas-id [] []])}
          "Vie PTV"))))
