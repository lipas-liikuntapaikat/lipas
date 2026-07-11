(ns lipas.ui.ptv.audit
  (:require ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Radio$default" :as Radio]
            ["@mui/material/RadioGroup$default" :as RadioGroup]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.components.text-fields :as tf]
            [lipas.ui.ptv.components :as ptv-components]
            [re-frame.core :as rf]
            [reagent.core :as r]))

;; Display a single field's content to audit
(r/defc content-panel
  [{:keys [tr field content audit-data]}]
  (let [;; Get existing audit data for this field
        field-audit (get audit-data field)

        ;; Format last audit information if available
        last-audit-info (when field-audit
                          (str (tr :ptv.audit/last-audit) " "
                               (some-> field-audit :timestamp (subs 0 10))
                               (when-let [status (:status field-audit)]
                                 (str ", " (tr (keyword (str "ptv.audit.status/" status)))))
                               (when-let [feedback (:feedback field-audit)]
                                 (str ": " feedback ""))))]

    [:> Box {:key field}
     [:> Typography {:variant "h6" :sx #js{:mt 3 :mb 1}}
      (tr (case field
            :summary :ptv/summary
            :description :ptv/description))]

     ;; Content display
     [:> Box {:sx #js {:mb 2 :border "1px solid #eee" :p 2}}
      [:> Typography {:variant "body1" :whiteSpace "pre-wrap"}
       content]]

     ;; Previous audit info display
     (when last-audit-info
       [:> Typography {:variant "caption" :color "text.secondary" :sx #js{:mb 2}}
        last-audit-info])]))

;; Presentational form controls for a single field. Entity-agnostic:
;; the wrappers below wire site/service specific subs and events.
(r/defc field-form
  [{:keys [tr field status feedback on-status-change on-feedback-change]}]
  [:> Box {:sx #js{:mb 4}}
   ;; Status selection
   [:> FormControl {:component "fieldset" :sx #js{:mb 2}}
    [:> FormLabel {:component "legend"}
     (str (tr :ptv.audit/status) " - "
          (tr (case field
                :summary :ptv/summary
                :description :ptv/description)))]
    [:> RadioGroup
     {:row true
      :value (or status "")
      :onChange (fn [e]
                  (on-status-change (.. e -target -value)))}
     [:> FormControlLabel
      {:value "approved"
       :control (r/as-element [:> Radio])
       :label (tr :ptv.audit.status/approved)}]
     [:> FormControlLabel
      {:value "changes-requested"
       :control (r/as-element [:> Radio])
       :label (tr :ptv.audit.status/changes-requested)}]]]

   ;; Feedback field
   (let [feedback-length (count (or feedback ""))
         max-length 1000
         is-over-limit (> feedback-length max-length)]
     [:> TextField
      {:fullWidth true
       :multiline true
       :InputProps #js{:inputComponent tf/patched-textarea}
       :inputProps #js{:maxLength max-length}
       :rows 3
       :label (tr :ptv.audit/feedback)
       :placeholder (tr :ptv.audit/feedback-placeholder)
       :value (or feedback "")
       :error is-over-limit
       :helperText (str feedback-length "/" max-length " "
                        (tr :ptv.audit/characters))
       :onChange (fn [e]
                   (on-feedback-change (.. e -target -value)))}])])

(r/defc site-field-form
  [{:keys [tr field lipas-id]}]
  (let [feedback @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-field-feedback lipas-id field])
        status @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-field-status lipas-id field])]
    [field-form
     {:tr tr
      :field field
      :status status
      :feedback feedback
      :on-status-change #(rf/dispatch [:lipas.ui.ptv.events/update-audit-status lipas-id field %])
      :on-feedback-change #(rf/dispatch [:lipas.ui.ptv.events/update-audit-feedback lipas-id field %])}]))

(r/defc service-field-form
  [{:keys [tr field service-id]}]
  (let [feedback @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-field-feedback service-id field])
        status @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-field-status service-id field])]
    [field-form
     {:tr tr
      :field field
      :status status
      :feedback feedback
      :on-status-change #(rf/dispatch [:lipas.ui.ptv.events/update-service-audit-status service-id field %])
      :on-feedback-change #(rf/dispatch [:lipas.ui.ptv.events/update-service-audit-feedback service-id field %])}]))

;; Complete audit form for a site with single save button
(r/defc site-form
  [{:keys [tr lipas-id site]}]
  (let [has-privilege? @(rf/subscribe [:lipas.ui.ptv.subs/has-audit-privilege?])
        saving? @(rf/subscribe [:lipas.ui.ptv.subs/saving-audit?])
        site-audit-data @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data lipas-id])
        audit-valid? @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data-valid? lipas-id])
        org-id @(rf/subscribe [:lipas.ui.ptv.subs/selected-ptv-org-id])]

    [:> Paper {:sx #js{:p 3}}
     [:> Typography {:variant "h6"} (:name site)]

     ;; Service Location Preview Section
     [:> Box {:sx #js{:mt 3 :mb 3}}
      [:> Typography {:variant "h6" :sx #js{:mb 2}} "PTV-palvelupaikan esikatselu"]
      [ptv-components/service-location-preview
       {:org-id org-id
        :lipas-id lipas-id}]]

     ;; Summary content
     [content-panel
      {:tr tr
       :field :summary
       :content (get-in site [:ptv :summary :fi])
       :audit-data site-audit-data}]

     ;; Description content
     [content-panel
      {:tr tr
       :field :description
       :content (get-in site [:ptv :description :fi])
       :audit-data site-audit-data}]

     ;; Audit controls (only for users with audit privilege)
     (when has-privilege?
       [:> Box
        {:sx #js{:mt 4 :pt 3 :borderTop "1px solid #eee"}}
        [:> Typography
         {:variant "h6" :mb 2}
         (tr :ptv.audit/feedback)]

        ;; Summary feedback form - now with lipas-id
        [site-field-form {:tr tr :field :summary :lipas-id lipas-id}]

        ;; Description feedback form - now with lipas-id
        [site-field-form {:tr tr :field :description :lipas-id lipas-id}]

        ;; Single save button for both fields - passing site-audit-data explicitly
        [:> Button
         {:variant "contained"
          :color "primary"
          :fullWidth true
          :sx #js{:mt 3}
          :disabled (or saving? (not audit-valid?))
          :onClick (fn []
                     (rf/dispatch [:lipas.ui.ptv.events/save-ptv-audit
                                   lipas-id
                                   site-audit-data]))}
         (tr :actions/save)]])]))

;; Complete audit form for a PTV Service with single save button
(r/defc service-form
  [{:keys [tr service]}]
  (let [service-id (:service-id service)
        has-privilege? @(rf/subscribe [:lipas.ui.ptv.subs/has-audit-privilege?])
        saving? @(rf/subscribe [:lipas.ui.ptv.subs/saving-audit?])
        audit-data @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-data service-id])
        audit-valid? @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-data-valid? service-id])]

    [:> Paper {:sx #js{:p 3}}
     [:> Typography {:variant "h6"} (:label service)]

     ;; Summary content (live from PTV)
     [content-panel
      {:tr tr
       :field :summary
       :content (get-in service [:summary :fi])
       :audit-data audit-data}]

     ;; Description content (live from PTV)
     [content-panel
      {:tr tr
       :field :description
       :content (get-in service [:description :fi])
       :audit-data audit-data}]

     ;; Audit controls (only for users with audit privilege)
     (when has-privilege?
       [:> Box
        {:sx #js{:mt 4 :pt 3 :borderTop "1px solid #eee"}}
        [:> Typography
         {:variant "h6" :mb 2}
         (tr :ptv.audit/feedback)]

        [service-field-form {:tr tr :field :summary :service-id service-id}]

        [service-field-form {:tr tr :field :description :service-id service-id}]

        [:> Button
         {:variant "contained"
          :color "primary"
          :fullWidth true
          :sx #js{:mt 3}
          :disabled (or saving? (not audit-valid?))
          :onClick (fn []
                     (rf/dispatch [:lipas.ui.ptv.events/save-ptv-service-audit
                                   {:service-id service-id
                                    :source-id (:source-id service)
                                    :audit-data audit-data}]))}
         (tr :actions/save)]])]))

;; Site list item component for the list of sites to audit
(r/defc site-list-item
  [{:keys [site selected? on-select]}]
  (let [audit-data (get-in site [:ptv :audit])
        summary-status (get-in audit-data [:summary :status])
        desc-status (get-in audit-data [:description :status])

        ;; Calculate completion status
        status-indicator (cond
                           (and summary-status desc-status) "completed"
                           (or summary-status desc-status) "partial"
                           :else "todo")

        ;; Style based on status
        status-color (case status-indicator
                       "completed" "success.main"
                       "partial" "warning.main"
                       "todo" "info.main")

        ;; Last audit date or empty string
        last-audit-date (when (or summary-status desc-status)
                          (some-> audit-data :timestamp (subs 0 10)))]

    [:> Paper
     {:sx #js{:p 2
              :mb 2
              :border (when selected? "2px solid")
              :borderColor (when selected? "primary.main")
              :cursor "pointer"}
      :elevation (if selected? 3 1)
      :onClick #(on-select site)}

     [:> Stack
      {:direction "row"
       :spacing 2
       :alignItems "center"}

      ;; Status indicator
      [:> Box
       {:sx #js{:width 10
                :height 10
                :borderRadius "50%"
                :bgcolor status-color}}]

      ;; Site name and details
      [:> Stack {:sx #js{:flex 1}}
       [:> Typography
        {:variant "subtitle1"
         :component "div"
         :sx #js {:fontWeight (when selected? "bold")}}
        (:name site)]

       ;; Show audit status if available
       (when (or summary-status desc-status)
         [:> Typography
          {:variant "caption"
           :color "text.secondary"}
          (str "Last audit: " last-audit-date)
          (when summary-status
            (str ", Summary: " summary-status))
          (when desc-status
            (str ", Description: " desc-status))])]]]))

;; Service list item component for the list of services to audit
(r/defc service-list-item
  [{:keys [service selected? on-select]}]
  (let [audit-data (:audit service)
        summary-status (get-in audit-data [:summary :status])
        desc-status (get-in audit-data [:description :status])

        ;; Calculate completion status
        status-indicator (cond
                           (and summary-status desc-status) "completed"
                           (or summary-status desc-status) "partial"
                           :else "todo")

        ;; Style based on status
        status-color (case status-indicator
                       "completed" "success.main"
                       "partial" "warning.main"
                       "todo" "info.main")

        ;; Last audit date or empty string
        last-audit-date (when (or summary-status desc-status)
                          (some-> audit-data :timestamp (subs 0 10)))]

    [:> Paper
     {:sx #js{:p 2
              :mb 2
              :border (when selected? "2px solid")
              :borderColor (when selected? "primary.main")
              :cursor "pointer"}
      :elevation (if selected? 3 1)
      :onClick #(on-select service)}

     [:> Stack
      {:direction "row"
       :spacing 2
       :alignItems "center"}

      ;; Status indicator
      [:> Box
       {:sx #js{:width 10
                :height 10
                :borderRadius "50%"
                :bgcolor status-color}}]

      ;; Service name and details
      [:> Stack {:sx #js{:flex 1}}
       [:> Typography
        {:variant "subtitle1"
         :component "div"
         :sx #js {:fontWeight (when selected? "bold")}}
        (:label service)]

       ;; Show audit status if available
       (when (or summary-status desc-status)
         [:> Typography
          {:variant "caption"
           :color "text.secondary"}
          (str "Last audit: " last-audit-date)
          (when summary-status
            (str ", Summary: " summary-status))
          (when desc-status
            (str ", Description: " desc-status))])]]]))

;; Main audit view
(r/defc main-view
  [{:keys [tr]}]
  (let [org-id @(rf/subscribe [:lipas.ui.ptv.subs/selected-ptv-org-id])
        lipas-org-id @(rf/subscribe [:lipas.ui.ptv.subs/selected-org-id])
        selected-section @(rf/subscribe [:lipas.ui.ptv.subs/selected-audit-section])
        selected-tab @(rf/subscribe [:lipas.ui.ptv.subs/selected-audit-tab])
        selected-site @(rf/subscribe [:lipas.ui.ptv.subs/selected-audit-site])
        selected-service @(rf/subscribe [:lipas.ui.ptv.subs/selected-audit-service])

        ;; Sites
        todo-sites @(rf/subscribe [:lipas.ui.ptv.subs/auditable-sites org-id :todo])
        completed-sites @(rf/subscribe [:lipas.ui.ptv.subs/auditable-sites org-id :completed])
        site-stats @(rf/subscribe [:lipas.ui.ptv.subs/audit-stats org-id])
        site-notification-sent? @(rf/subscribe [:lipas.ui.ptv.subs/notification-sent?])

        ;; Services
        todo-services @(rf/subscribe [:lipas.ui.ptv.subs/auditable-services org-id :todo])
        completed-services @(rf/subscribe [:lipas.ui.ptv.subs/auditable-services org-id :completed])
        service-stats @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-stats org-id])
        service-notification-sent? @(rf/subscribe [:lipas.ui.ptv.subs/service-notification-sent?])

        sending? @(rf/subscribe [:lipas.ui.ptv.subs/sending-notification?])

        services? (= "services" selected-section)
        todo-items (if services? todo-services todo-sites)
        completed-items (if services? completed-services completed-sites)
        stats (if services? service-stats site-stats)
        completed-count (if services? (:total-services stats) (:total-sites stats))
        notification-sent? (if services? service-notification-sent? site-notification-sent?)

        ;; Display items based on selected tab
        display-items (case selected-tab
                        "todo" todo-items
                        "completed" completed-items
                        todo-items)]

    [:> Stack {:spacing 2}
     ;; Header
     [:> Typography {:variant "h5"} (tr :ptv.audit/headline)]
     [:> Typography {:variant "body1"} (tr :ptv.audit/description)]

     ;; Section selector: service locations (sites) vs services
     [:> Paper {:sx #js {:mb 1}}
      [:> Tabs
       {:value selected-section
        :onChange #(rf/dispatch [:lipas.ui.ptv.events/select-audit-section %2])
        :textColor "primary"
        :indicatorColor "secondary"
        :variant "fullWidth"}
       [:> Tab
        {:value "sites"
         :label (tr :ptv.audit/sites-section)}]
       [:> Tab
        {:value "services"
         :label (tr :ptv.audit/services-section)}]]]

     ;; Tabs for Todo/Completed
     [:> Paper {:sx #js {:mb 2}}
      [:> Tabs
       {:value selected-tab
        :onChange #(rf/dispatch [:lipas.ui.ptv.events/select-audit-tab %2])
        :textColor "primary"
        :indicatorColor "secondary"
        :variant "fullWidth"}
       [:> Tab
        {:value "todo"
         :label (str (tr :ptv.audit/todo-tab) " (" (count todo-items) ")")}]
       [:> Tab
        {:value "completed"
         :label (str (tr :ptv.audit/completed-tab) " (" (count completed-items) ")")}]]]

     ;; Send notification button
     [:> Box {:sx #js {:display "flex" :justifyContent "flex-end" :mb 2}}
      [:> Button
       {:variant "contained"
        :color "primary"
        :disabled (or sending? (zero? completed-count) notification-sent?)
        :onClick #(rf/dispatch (if services?
                                 [:lipas.ui.ptv.events/send-service-audit-notification lipas-org-id stats]
                                 [:lipas.ui.ptv.events/send-audit-notification lipas-org-id stats]))}
       (cond
         sending? (tr :ptv.audit/sending-notification)
         notification-sent? (tr :ptv.audit/notification-sent)
         :else (str (tr :ptv.audit/send-notification)
                    " (" completed-count " " (tr :ptv.audit/audited) ")"))]]

     ;; Split view: item list and audit panel
     [:> Box
      {:sx #js{:display "flex"
               :flexDirection "row"
               :gap 2}}

      ;; Left side: items list
      [:> Box
       {:sx #js{:width "30%"
                :minWidth 250}}
       [:> Paper
        {:sx #js{:p 2 :height "100%"}}
        [:> Stack {:spacing 1}
         [:> Typography
          {:variant "h6"}
          (if services?
            (case selected-tab
              "todo" (tr :ptv.audit/services-to-audit)
              "completed" (tr :ptv.audit/audited-services)
              (tr :ptv.audit/select-service))
            (case selected-tab
              "todo" (tr :ptv.audit/sites-to-audit)
              "completed" (tr :ptv.audit/audited-sites)
              (tr :ptv.audit/select-site)))]

         ;; Item count or empty message
         (if (empty? display-items)
           [:> Typography
            {:color "text.secondary"
             :variant "body2"}
            (if services?
              (case selected-tab
                "todo" (tr :ptv.audit/no-services-to-audit)
                "completed" (tr :ptv.audit/no-audited-services)
                (tr :ptv.audit/no-services))
              (case selected-tab
                "todo" (tr :ptv.audit/no-sites-to-audit)
                "completed" (tr :ptv.audit/no-audited-sites)
                (tr :ptv.audit/no-sites)))]

           ;; List of items
           [:> Box
            {:sx #js{:maxHeight "60vh"
                     :overflow "auto"}}
            (if services?
              (for [service display-items]
                ^{:key (:service-id service)}
                [service-list-item
                 {:service service
                  :selected? (= (:service-id service) (:service-id selected-service))
                  :on-select #(rf/dispatch [:lipas.ui.ptv.events/select-audit-service %])}])
              (for [site display-items]
                ^{:key (:lipas-id site)}
                [site-list-item
                 {:site site
                  :selected? (= (:lipas-id site) (:lipas-id selected-site))
                  :on-select #(rf/dispatch [:lipas.ui.ptv.events/select-audit-site %])}]))])]]]

      ;; Right side: audit form for selected item
      [:> Box
       {:sx #js{:flex 1}}
       (cond
         (and services? selected-service)
         [service-form
          {:tr tr
           :service selected-service}]

         (and (not services?) selected-site)
         [site-form
          {:tr tr
           :lipas-id (:lipas-id selected-site)
           :site selected-site}]

         :else
         ;; Placeholder when nothing is selected
         [:> Paper
          {:sx #js{:p 3
                   :display "flex"
                   :alignItems "center"
                   :justifyContent "center"
                   :height "100%"
                   :bgcolor "action.hover"}}
          [:> Typography
           {:color "text.secondary"}
           (if services?
             (tr :ptv.audit/select-service-prompt)
             (tr :ptv.audit/select-site-prompt))]])]]]))
