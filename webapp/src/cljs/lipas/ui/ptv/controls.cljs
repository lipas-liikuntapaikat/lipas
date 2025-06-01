(ns lipas.ui.ptv.controls
  (:require ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Radio$default" :as Radio]
            ["@mui/material/RadioGroup$default" :as RadioGroup]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [re-frame.core :as rf]
            [uix.core :as uix :refer [$ defui]]))

(defui info-text [{:keys [children]}]
  ($ Typography
     {:variant "body1"}
     children))

(defui services-selector
  [{:keys [disabled options value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [options* (uix/use-memo (fn []
                                 (->> options
                                      (map (fn [x]
                                             {:value (value-fn x)
                                              :label (:label x)}))
                                      (sort-by :label)))
                               [options value-fn])]
    ($ autocomplete2
       {:disabled  disabled
        :options   options*
        :multiple  true
        :label     label
        :value     (to-array value)
        :on-change (fn [_e v]
                     (on-change (vec (map (fn [x]
                                            (if (map? x)
                                              (:value x)
                                              x))
                                          v))))})))

(defui lang-selector [{:keys [value on-change enabled-languages]}]
  ($ Tabs
     {:value     value
      :on-change (fn [_e v] (on-change (keyword v)))}
     (when (or (nil? enabled-languages) (contains? enabled-languages "fi"))
       ($ Tab {:value "fi" :label "FI"}))
     (when (or (nil? enabled-languages) (contains? enabled-languages "se"))
       ($ Tab {:value "se" :label "SE"}))
     (when (or (nil? enabled-languages) (contains? enabled-languages "en"))
       ($ Tab {:value "en" :label "EN"}))))

(defui audit-panel
  [{:keys [tr field content lipas-id]}]
  (let [has-privilege? (use-subscribe [:lipas.ui.ptv.subs/has-audit-privilege?])
        audit-feedback (use-subscribe [:lipas.ui.ptv.subs/audit-feedback field])
        audit-status (use-subscribe [:lipas.ui.ptv.subs/audit-status field])
        saving? (use-subscribe [:lipas.ui.ptv.subs/saving-audit?])
        site-audit-data (use-subscribe [:lipas.ui.ptv.subs/site-audit-data lipas-id])

        ;; Get existing audit data for this field
        field-audit (get site-audit-data field)

        ;; Format last audit information if available
        last-audit-info (when field-audit
                          (str (tr :ptv.audit/last-audit) " "
                               (some-> field-audit :timestamp (subs 0 10))
                               (when-let [status (:status field-audit)]
                                 (str ", " (tr (keyword (str "ptv.audit.status/" status)))))
                               (when-let [feedback (:feedback field-audit)]
                                 (str ": " feedback ""))))

        ;; If the user doesn't have audit privilege, just show previous audit info
        show-controls? has-privilege?]

    ($ Box {:key field}
       ($ Typography {:variant "h6" :sx #js{:mt 3 :mb 1}}
          (tr (case field
                :summary :ptv/summary
                :description :ptv/description)))

       ;; Content display (existing text to audit)
       #_($ Paper {:sx #js{:p 2 :mt 1 :mb 1 }})
       ($ Box {:sx #js {:mb 2 :border "1px solid #eee" :p 2}}
          ($ Typography {:variant "body1" :whiteSpace "pre-wrap"}
             content))

       ;; Previous audit info display
       (when last-audit-info
         ($ Typography {:variant "caption" :color "text.secondary" :sx #js{:mb 2}}
            last-audit-info))

       ;; Audit controls (only for users with audit privilege)
       (when show-controls?
         ($ Box
            ;; Status selection
            ($ FormControl {:component "fieldset" :sx #js{:mb 2}}
               ($ FormLabel {:component "legend"} (tr :ptv.audit/status))
               ($ RadioGroup
                  {:row true
                   :value (or audit-status "")
                   :onChange (fn [e]
                               (rf/dispatch [:lipas.ui.ptv.events/update-audit-status
                                             field
                                             (.. e -target -value)]))}
                  ($ FormControlLabel
                     {:value "approved"
                      :control ($ Radio)
                      :label (tr :ptv.audit.status/approved)})
                  ($ FormControlLabel
                     {:value "changes-requested"
                      :control ($ Radio)
                      :label (tr :ptv.audit.status/changes-requested)})))

            ;; Feedback field
            ($ TextField
               {:fullWidth true
                :multiline true
                :rows 3
                :label (tr :ptv.audit/feedback)
                :placeholder (tr :ptv.audit/feedback-placeholder)
                :value (or audit-feedback "")
                :onChange (fn [e]
                            (rf/dispatch [:lipas.ui.ptv.events/update-audit-feedback
                                          field
                                          (.. e -target -value)]))})

            ;; Save button
            ($ Button
               {:variant "contained"
                :color "primary"
                :sx #js{:mt 2}
                :disabled (or saving? (not audit-status))
                :onClick (fn []
                           (rf/dispatch [:lipas.ui.ptv.events/save-ptv-audit
                                         lipas-id]))}
               (tr :actions/save)))))))
