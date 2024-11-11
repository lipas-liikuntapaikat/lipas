(ns lipas.ui.ptv.controls
  (:require ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Radio$default" :as Radio]
            ["@mui/material/RadioGroup$default" :as RadioGroup]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [uix.core :as uix :refer [$ defui]]))

(defui info-text [{:keys [children]}]
  ($ Typography
     {:variant "body1"}
     children))

(defui services-selector
  [{:keys [options value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [options* (uix/use-memo (fn []
                                 (map (fn [x]
                                        {:value (value-fn x)
                                         :label (:label x)})
                                      options))
                               [options value-fn])]
    ($ autocomplete2
       {:options   options*
        :multiple  true
        :label     label
        :value     (to-array value)
        :on-change (fn [_e v]
                     (on-change (:value v)))})))

(defui service-integration [{:keys [tr value on-change service-ids set-service-ids services]}]
  ($ :<>
     ($ Typography {:variant "h6"}
        (tr :ptv/services))

     ;; Integration type
     ($ FormControl
       ($ FormLabel (tr :ptv.actions/select-integration))
       ($ RadioGroup
         {:on-change (fn [_e v] (on-change v))
          :value     value}
         ($ FormControlLabel
           {:value   "lipas-managed"
            :label   (tr :ptv.integration.service/lipas-managed)
            :control ($ Radio)})

         ($ FormControlLabel
           {:value   "manual"
            :label   (tr :ptv.integration/manual)
            :control ($ Radio)})))

     (when (= "lipas-managed" value)
       (tr :ptv.integration.service/lipas-managed-helper))

     (when (= "manual" value)
       ($ services-selector
          {:options   services
           :value     service-ids
           :on-change set-service-ids
           :value-fn  :service-id
           :label     (tr :ptv.actions/select-service)}))))

(defui service-channel-integration [{:keys [tr value on-change]}]
  ($ :<>
     ($ Typography {:variant "h6"}
        (tr :ptv/service-channels))

     ;; Integration type
     ($ FormControl
        ($ FormLabel (tr :ptv.actions/select-integration))
        ($ RadioGroup
           {:on-change (fn [_e v] (on-change v))
            :value     value}
           ($ FormControlLabel
              {:value   "lipas-managed"
               :label   (tr :ptv.integration.service-channel/lipas-managed)
               :control ($ Radio)})

           ($ FormControlLabel
              {:value   "manual"
               :label   (tr :ptv.integration/manual)
               :control ($ Radio)})))

     (case value
       "lipas-managed"
       ($ info-text (tr :ptv.integration.service-channel/lipas-managed-helper))

       "manual"
       ($ info-text (tr :ptv.integration.service-channel/manual-helper))

       nil)))

(defui description-integration [{:keys [tr value on-change]}]
  ($ :<>
     ($ Typography
        {:variant "h6"}
        (tr :ptv/descriptions))

     ;; Integration type
     ($ FormControl
        ($ FormLabel (tr :ptv.actions/select-integration))
        ($ RadioGroup
          {:on-change (fn [_e v]
                        (on-change v))
           :value     value}
          ($ FormControlLabel
            {:value   "lipas-managed-ptv-fields"
             :label   (tr :ptv.integration.description/lipas-managed-ptv-fields)
             :control ($ Radio)})

          ($ FormControlLabel
            {:value   "lipas-managed-comment-field"
             :label   (tr :ptv.integration.description/lipas-managed-comment-field)
             :control ($ Radio)})

          ($ FormControlLabel
            {:value   "ptv-managed"
             :label   (tr :ptv.integration.description/ptv-managed)
             :control ($ Radio)})))

     (case value
       "lipas-managed-ptv-fields"
       ($ Typography (tr :ptv.integration.description/lipas-managed-ptv-fields-helper))

       "lipas-managed-comment-field"
       ($ Typography (tr :ptv.integration.description/lipas-managed-comment-field-helper))

       "ptv-managed"
       ($ Typography (tr :ptv.integration.description/ptv-managed-helper))

       nil)))

(defui lang-selector [{:keys [value on-change]}]
  ($ Tabs
     {:value     value
      :on-change (fn [_e v] (on-change (keyword v)))}
     ($ Tab {:value "fi" :label "FI"})
     ($ Tab {:value "se" :label "SE"})
     ($ Tab {:value "en" :label "EN"})))
