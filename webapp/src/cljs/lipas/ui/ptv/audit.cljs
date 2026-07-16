(ns lipas.ui.ptv.audit
  (:require [clojure.string :as str]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
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
            [lipas.data.ptv :as ptv-data]
            [lipas.ui.components.text-fields :as tf]
            [lipas.ui.ptv.components :as ptv-components]
            [lipas.ui.ptv.diff :as ptv-diff]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

(defn- audit-content-diff
  "Inline word diff between the audited snapshot and the current text:
   removed = struck-through red, added = green."
  [{:keys [old new]}]
  (let [ops (ptv-diff/coalesce (ptv-diff/diff old new))]
    [:> Typography {:variant "body1" :whiteSpace "pre-wrap"}
     (for [[i [op v]] (map-indexed vector ops)]
       (case op
         :equal ^{:key i} [:span v]
         :removed ^{:key i} [:span {:style #js {:background "#fce8e6"
                                                :textDecoration "line-through"
                                                :textDecorationColor "#c5221f"}} v]
         :added ^{:key i} [:span {:style #js {:background "#e6f4ea"}} v]))]))

;; Display a single field's content to audit
(r/defc content-panel
  [{:keys [tr field content content-localized audit-data]}]
  (let [;; Get existing audit data for this field
        field-audit (get audit-data field)

        state (when field-audit
                (ptv-data/audit-field-state field-audit content-localized))
        ;; Content changed since the verdict? :stale = approval no longer
        ;; covers the text (auditor's move), :fixed = the municipality
        ;; responded to a changes request (reviewable in Valmiit).
        changed? (contains? #{:stale :fixed} state)

        ;; Format last audit information if available
        last-audit-info (when field-audit
                          (str (tr :ptv.audit/last-audit) " "
                               (some-> field-audit :timestamp (subs 0 10))
                               (when-let [status (:status field-audit)]
                                 (str ", " (tr (keyword (str "ptv.audit.status/" status)))))
                               (when-let [feedback (:feedback field-audit)]
                                 (str ": " feedback ""))))]

    [:> Box {:key field}
     [:> Stack {:direction "row" :spacing 1 :alignItems "center" :sx #js{:mt 3 :mb 1}}
      [:> Typography {:variant "h6"}
       (tr (case field
             :summary :ptv/summary
             :description :ptv/description
             :user-instruction :ptv/user-instruction))]
      (case state
        :stale [:> Chip {:label (tr :ptv.audit/changed-since-audit)
                         :size "small"
                         :color "warning"
                         :variant "outlined"}]
        :fixed [:> Chip {:label (tr :ptv.audit/fixed-after-audit)
                         :size "small"
                         :color "info"
                         :variant "outlined"}]
        nil)]

     ;; Content display; when the text changed after the verdict, show
     ;; what changed as a word diff against the audited snapshot.
     [:> Box {:sx #js {:mb 2 :border "1px solid #eee" :p 2}}
      (if changed?
        [audit-content-diff {:old (get-in field-audit [:audited-content :fi])
                             :new content}]
        [:> Typography {:variant "body1" :whiteSpace "pre-wrap"}
         content])]

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
                :description :ptv/description
                :user-instruction :ptv/user-instruction)))]
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

;; Per-field tabs: each tab shows one field's current content (with the
;; changed-since-audit diff when relevant) and its verdict controls right
;; below it. The tab label dot reflects the field's audit state so tabs
;; don't hide which fields still need the auditor's attention.
(r/defc audit-fields-view
  [{:keys [tr fields audit-data has-privilege? field-form-fn]}]
  (let [field-keys (mapv :field fields)
        [selected-raw set-selected] (hooks/use-state (first field-keys))
        selected (if (some #{selected-raw} field-keys) selected-raw (first field-keys))
        current (some #(when (= selected (:field %)) %) fields)]
    [:<>
     [:> Tabs
      {:value (name selected)
       :onChange (fn [_ v] (set-selected (keyword v)))
       :textColor "primary"
       :indicatorColor "primary"
       :sx #js {:mt 2 :minHeight 40 :borderBottom "1px solid #eee"}}
      (for [{:keys [field content-localized]} fields]
        (let [state (ptv-data/audit-field-state (get audit-data field) content-localized)
              dot-color (case state
                          :approved "success.main"
                          :changes-requested "error.main"
                          :stale "warning.main"
                          :fixed "info.main"
                          "text.disabled")]
          ^{:key (name field)}
          [:> Tab
           {:value (name field)
            :sx #js {:minHeight 40}
            :label (r/as-element
                    [:> Stack {:direction "row" :spacing 1 :alignItems "center"}
                     [:> Box {:sx #js {:width 8
                                       :height 8
                                       :borderRadius "50%"
                                       :bgcolor dot-color}}]
                     [:span (tr (case field
                                  :summary :ptv/summary
                                  :description :ptv/description
                                  :user-instruction :ptv/user-instruction))]])}]))]

     ;; Selected field's content...
     [content-panel
      {:tr tr
       :field selected
       :content (get-in current [:content-localized :fi])
       :content-localized (:content-localized current)
       :audit-data audit-data}]

     ;; ...and its verdict controls right below
     (when has-privilege?
       [:> Box {:sx #js {:mt 2}}
        (field-form-fn selected)])]))

;; Complete audit form for a site with single save button. Items in the
;; done bucket open read-only — DVV reviews fixes from Valmiit and only
;; re-opens the verdict controls via an explicit re-audit action
;; (tester finding #7).
(r/defc site-form
  [{:keys [tr lipas-id site]}]
  (let [has-privilege? @(rf/subscribe [:lipas.ui.ptv.subs/has-audit-privilege?])
        saving? @(rf/subscribe [:lipas.ui.ptv.subs/saving-audit?])
        site-audit-data @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data lipas-id])
        audit-valid? @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-data-valid? lipas-id])
        dirty? @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-dirty? lipas-id])
        org-id @(rf/subscribe [:lipas.ui.ptv.subs/selected-ptv-org-id])

        fields (ptv-data/site-audit-fields site)
        field-states (vals (ptv-data/audit-field-states site-audit-data fields))
        bucket (ptv-data/audit-bucket site-audit-data fields)
        ;; Every save appends a revision and re-anchors verdict snapshots,
        ;; so it must change something: new input since the last save, or
        ;; a stale verdict being re-confirmed for the edited content.
        save-meaningful? (or dirty? (boolean (some #{:stale} field-states)))
        [reauditing? set-reauditing] (hooks/use-state false)
        _ (hooks/use-effect (fn [] (set-reauditing false)) [lipas-id])
        ;; dirty? keeps the form editable while there is unsaved input:
        ;; the bucket is computed from the live draft, so without it,
        ;; giving the last pending field a verdict would flip the form
        ;; read-only and hide the save button BEFORE the audit is saved.
        editable? (and has-privilege? (or reauditing? dirty? (not= :done bucket)))]

    [:> Paper {:sx #js{:p 3}}
     [:> Typography {:variant "h6"} (:name site)]

     ;; Service Location Preview Section
     [:> Box {:sx #js{:mt 3 :mb 3}}
      [:> Typography {:variant "h6" :sx #js{:mb 2}} "PTV-palvelupaikan esikatselu"]
      [ptv-components/service-location-preview
       {:org-id org-id
        :lipas-id lipas-id}]]

     ;; Per-field tabs: content + verdict controls together
     [audit-fields-view
      {:tr tr
       :audit-data site-audit-data
       :has-privilege? editable?
       :fields [{:field :summary
                 :content-localized (get-in site [:ptv :summary])}
                {:field :description
                 :content-localized (get-in site [:ptv :description])}]
       :field-form-fn (fn [field]
                        [site-field-form {:tr tr :field field :lipas-id lipas-id}])}]

     (when has-privilege?
       (if editable?
         ;; Single save button for all fields - passing site-audit-data explicitly
         [:> Button
          {:variant "contained"
           :color "primary"
           :fullWidth true
           :sx #js{:mt 3}
           :disabled (or saving? (not audit-valid?) (not save-meaningful?))
           :onClick (fn []
                      (rf/dispatch [:lipas.ui.ptv.events/save-ptv-audit
                                    lipas-id
                                    site-audit-data
                                    ;; snapshots anchoring the verdicts to this revision
                                    {:summary (get-in site [:ptv :summary])
                                     :description (get-in site [:ptv :description])}]))}
          (tr :actions/save)]

         [:> Stack {:spacing 2 :sx #js{:mt 3}}
          [:> Alert {:severity "success" :variant "outlined"}
           (tr :ptv.audit/done-item-info)]
          [:> Button
           {:variant "outlined"
            :color "primary"
            :fullWidth true
            :onClick #(set-reauditing true)}
           (tr :ptv.audit/reaudit)]]))]))

;; Complete audit form for a PTV Service with single save button. Done
;; items open read-only with an explicit re-audit action, like site-form.
(r/defc service-form
  [{:keys [tr service]}]
  (let [service-id (:service-id service)
        has-privilege? @(rf/subscribe [:lipas.ui.ptv.subs/has-audit-privilege?])
        saving? @(rf/subscribe [:lipas.ui.ptv.subs/saving-audit?])
        audit-data @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-data service-id])
        audit-valid? @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-data-valid? service-id])
        dirty? @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-dirty? service-id])

        fields (ptv-data/service-audit-fields service)
        field-states (vals (ptv-data/audit-field-states audit-data fields))
        bucket (ptv-data/audit-bucket audit-data fields)
        ;; See site-form: a save must carry new input or re-confirm a
        ;; stale verdict.
        save-meaningful? (or dirty? (boolean (some #{:stale} field-states)))
        [reauditing? set-reauditing] (hooks/use-state false)
        _ (hooks/use-effect (fn [] (set-reauditing false)) [service-id])
        ;; dirty? keeps the form editable while there is unsaved input —
        ;; see site-form for why (draft-derived bucket would otherwise
        ;; hide the save button on the last verdict click).
        editable? (and has-privilege? (or reauditing? dirty? (not= :done bucket)))]

    [:> Paper {:sx #js{:p 3}}
     [:> Typography {:variant "h6"} (:label service)]

     ;; Per-field tabs: content + verdict controls together. Toimintaohje
     ;; is shown even when empty so the auditor can request one to be added.
     [audit-fields-view
      {:tr tr
       :audit-data audit-data
       :has-privilege? editable?
       :fields [{:field :summary
                 :content-localized (:summary service)}
                {:field :description
                 :content-localized (:description service)}
                {:field :user-instruction
                 :content-localized (:user-instruction service)}]
       :field-form-fn (fn [field]
                        [service-field-form {:tr tr :field field :service-id service-id}])}]

     (when has-privilege?
       (if editable?
         ;; Single save button for all fields
         [:> Button
          {:variant "contained"
           :color "primary"
           :fullWidth true
           :sx #js{:mt 3}
           :disabled (or saving? (not audit-valid?) (not save-meaningful?))
           :onClick (fn []
                      (rf/dispatch [:lipas.ui.ptv.events/save-ptv-service-audit
                                    {:service-id service-id
                                     :source-id (:source-id service)
                                     :audit-data audit-data
                                     ;; snapshots anchoring the verdicts to this revision
                                     :contents {:summary (:summary service)
                                                :description (:description service)
                                                :user-instruction (:user-instruction service)}}]))}
          (tr :actions/save)]

         [:> Stack {:spacing 2 :sx #js{:mt 3}}
          [:> Alert {:severity "success" :variant "outlined"}
           (tr :ptv.audit/done-item-info)]
          [:> Button
           {:variant "outlined"
            :color "primary"
            :fullWidth true
            :onClick #(set-reauditing true)}
           (tr :ptv.audit/reaudit)]]))]))

;; Site list item component for the list of sites to audit
(r/defc site-list-item
  [{:keys [tr site selected? on-select]}]
  (let [audit-data (get-in site [:ptv :audit])
        summary-status (get-in audit-data [:summary :status])
        desc-status (get-in audit-data [:description :status])
        field-states (ptv-data/audit-field-states
                      audit-data (ptv-data/site-audit-fields site))
        states (vals field-states)
        changed? (boolean (some #{:stale} states))
        fixed? (boolean (some #{:fixed} states))

        ;; Calculate completion status (stale verdicts count as incomplete,
        ;; fixed ones as complete — the municipality has responded)
        status-indicator (cond
                           (and (seq states)
                                (every? #{:approved :changes-requested :fixed} states)) "completed"
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
       [:> Stack {:direction "row" :spacing 1 :alignItems "center"}
        [:> Typography
         {:variant "subtitle1"
          :component "div"
          :sx #js {:fontWeight (when selected? "bold")}}
         (:name site)]
        (when changed?
          [:> Chip {:label (tr :ptv.audit/changed-since-audit)
                    :size "small"
                    :color "warning"
                    :variant "outlined"}])
        (when fixed?
          [:> Chip {:label (tr :ptv.audit/fixed-after-audit)
                    :size "small"
                    :color "info"
                    :variant "outlined"}])]

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
  [{:keys [tr service selected? on-select]}]
  (let [audit-data (:audit service)
        summary-status (get-in audit-data [:summary :status])
        desc-status (get-in audit-data [:description :status])
        ui-status (get-in audit-data [:user-instruction :status])
        field-states (ptv-data/audit-field-states
                      audit-data (ptv-data/service-audit-fields service))
        states (vals field-states)
        changed? (boolean (some #{:stale} states))
        fixed? (boolean (some #{:fixed} states))

        ;; Calculate completion status (stale verdicts count as incomplete,
        ;; fixed ones as complete — the municipality has responded)
        status-indicator (cond
                           (and (seq states)
                                (every? #{:approved :changes-requested :fixed} states)) "completed"
                           (some some? [summary-status desc-status ui-status]) "partial"
                           :else "todo")

        ;; Style based on status
        status-color (case status-indicator
                       "completed" "success.main"
                       "partial" "warning.main"
                       "todo" "info.main")

        ;; Last audit date or empty string
        last-audit-date (when (or summary-status desc-status ui-status)
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
       [:> Stack {:direction "row" :spacing 1 :alignItems "center"}
        [:> Typography
         {:variant "subtitle1"
          :component "div"
          :sx #js {:fontWeight (when selected? "bold")}}
         (:label service)]
        (when changed?
          [:> Chip {:label (tr :ptv.audit/changed-since-audit)
                    :size "small"
                    :color "warning"
                    :variant "outlined"}])
        (when fixed?
          [:> Chip {:label (tr :ptv.audit/fixed-after-audit)
                    :size "small"
                    :color "info"
                    :variant "outlined"}])]

       ;; Show audit status if available
       (when (or summary-status desc-status ui-status)
         [:> Typography
          {:variant "caption"
           :color "text.secondary"}
          (str "Last audit: " last-audit-date)
          (when summary-status
            (str ", Summary: " summary-status))
          (when desc-status
            (str ", Description: " desc-status))
          (when ui-status
            (str ", UserInstruction: " ui-status))])]]]))

;; Confirmation dialog for the audit notification: shows who receives the
;; email (the org's PTV managers) and the derived contents — which items
;; await municipality fixes — before anything is sent (tester finding #2).
;; Everything comes from the backend preview endpoint, which runs the same
;; derivation as the send endpoint.
(r/defc notification-dialog
  [{:keys [tr services? lipas-org-id]}]
  (let [dialog @(rf/subscribe [:lipas.ui.ptv.subs/notification-dialog])
        {:keys [open? loading? recipients action-items approved-count]} dialog
        close #(rf/dispatch [:lipas.ui.ptv.events/close-notification-dialog])
        field-label {:summary (tr :ptv/summary)
                     :description (tr :ptv/description)
                     :user-instruction (tr :ptv/user-instruction)}]
    [:> Dialog {:open (boolean open?)
                :onClose close
                :maxWidth "sm"
                :fullWidth true}
     [:> DialogTitle (tr :ptv.audit/notification-dialog-title)]
     [:> DialogContent
      [:> Stack {:spacing 2 :sx #js {:mt 1}}
       [:> Typography {:variant "body2"}
        (tr :ptv.audit/notification-dialog-info)]

       [:> Box
        [:> Typography {:variant "subtitle2"}
         (tr :ptv.audit/notification-recipients)]
        (cond
          loading?
          [:> CircularProgress {:size 20 :sx #js {:mt 1}}]

          (empty? recipients)
          [:> Alert {:severity "warning" :variant "outlined" :sx #js {:mt 1}}
           (tr :ptv.audit/no-recipients-found)]

          :else
          [:> Stack {:spacing 0.5 :sx #js {:mt 1}}
           (for [email recipients]
             ^{:key email}
             [:> Typography {:variant "body2"} email])])]

       [:> Box
        [:> Typography {:variant "subtitle2"}
         (tr :ptv.audit/notification-contents)]
        (when-not loading?
          (if (seq action-items)
            [:> Stack {:spacing 0.5 :sx #js {:mt 1}}
             [:> Typography {:variant "body2" :sx #js {:fontWeight 500}}
              (str (tr :ptv.audit/notification-action-items)
                   " (" (count action-items) "):")]
             (for [{:keys [name fields] :as item} action-items]
               ^{:key (str (or (:lipas-id item) (:service-id item)))}
               [:> Typography {:variant "body2"}
                (str "• " name " — "
                     (str/join ", " (keep field-label fields)))])
             (when (pos? approved-count)
               [:> Typography {:variant "body2" :sx #js {:mt 1}}
                (str/replace (tr :ptv.audit/notification-approved-line)
                             "%1$s" (str approved-count))])]
            [:> Typography {:variant "body2" :sx #js {:mt 1}}
             (str/replace (tr :ptv.audit/notification-no-fixes)
                          "%1$s" (str approved-count))]))]]]
     [:> DialogActions
      [:> Button {:onClick close}
       (tr :actions/cancel)]
      [:> Button
       {:variant "contained"
        :color "primary"
        :disabled (boolean (or loading? (empty? recipients)))
        :onClick (fn []
                   (close)
                   (rf/dispatch (if services?
                                  [:lipas.ui.ptv.events/send-service-audit-notification lipas-org-id]
                                  [:lipas.ui.ptv.events/send-audit-notification lipas-org-id])))}
       (tr :ptv.audit/send-notification)]]]))

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
        sites-waiting-audit @(rf/subscribe [:lipas.ui.ptv.subs/auditable-sites org-id :waiting-audit])
        sites-waiting-fixes @(rf/subscribe [:lipas.ui.ptv.subs/auditable-sites org-id :waiting-fixes])
        sites-done @(rf/subscribe [:lipas.ui.ptv.subs/auditable-sites org-id :done])
        site-notify-counts @(rf/subscribe [:lipas.ui.ptv.subs/site-audit-notification-counts org-id])
        site-notification-sent? @(rf/subscribe [:lipas.ui.ptv.subs/notification-sent?])

        ;; Services
        services-waiting-audit @(rf/subscribe [:lipas.ui.ptv.subs/auditable-services org-id :waiting-audit])
        services-waiting-fixes @(rf/subscribe [:lipas.ui.ptv.subs/auditable-services org-id :waiting-fixes])
        services-done @(rf/subscribe [:lipas.ui.ptv.subs/auditable-services org-id :done])
        service-notify-counts @(rf/subscribe [:lipas.ui.ptv.subs/service-audit-notification-counts org-id])
        service-notification-sent? @(rf/subscribe [:lipas.ui.ptv.subs/service-notification-sent?])

        sending? @(rf/subscribe [:lipas.ui.ptv.subs/sending-notification?])

        services? (= "services" selected-section)
        waiting-audit-items (if services? services-waiting-audit sites-waiting-audit)
        waiting-fixes-items (if services? services-waiting-fixes sites-waiting-fixes)
        done-items (if services? services-done sites-done)
        {:keys [action-count approved-count]} (if services? service-notify-counts site-notify-counts)
        notification-sent? (if services? service-notification-sent? site-notification-sent?)

        display-items (case selected-tab
                        "waiting-audit" waiting-audit-items
                        "waiting-fixes" waiting-fixes-items
                        "done" done-items
                        waiting-audit-items)]

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

     ;; Whose-move buckets over the audit sample + the picker for growing
     ;; the sample. Deliberately lighter than the section Tabs above.
     ;; Notification action shares the row.
     [:> Box {:sx #js {:display "flex"
                       :justifyContent "space-between"
                       :alignItems "center"
                       :flexWrap "wrap"
                       :gap 1
                       :mb 2}}
      [:> Tabs
       {:value selected-tab
        :onChange #(rf/dispatch [:lipas.ui.ptv.events/select-audit-tab %2])
        :textColor "primary"
        :indicatorColor "primary"
        :sx #js {:minHeight 36}}
       [:> Tab
        {:value "waiting-audit"
         :sx #js {:minHeight 36}
         :label (str (tr :ptv.audit/waiting-audit-tab) " (" (count waiting-audit-items) ")")}]
       [:> Tab
        {:value "waiting-fixes"
         :sx #js {:minHeight 36}
         :label (str (tr :ptv.audit/waiting-fixes-tab) " (" (count waiting-fixes-items) ")")}]
       [:> Tab
        {:value "done"
         :sx #js {:minHeight 36}
         :label (str (tr :ptv.audit/done-tab) " (" (count done-items) ")")}]]
      [:> Button
       {:variant "contained"
        :color "primary"
        ;; Enabled when something needs the municipality's attention
        ;; (action-count) or a "nothing to fix" note can be sent
        ;; (approved-count) — items mid-audit or fixed-awaiting-re-review
        ;; alone don't justify a notification.
        :disabled (or sending?
                      notification-sent?
                      (and (zero? action-count) (zero? approved-count)))
        ;; Opens a confirmation dialog (recipients + contents) — the
        ;; actual send is dispatched from the dialog.
        :onClick #(rf/dispatch [:lipas.ui.ptv.events/open-notification-dialog
                                lipas-org-id
                                (if services? "services" "sites")])}
       (cond
         sending? (tr :ptv.audit/sending-notification)
         notification-sent? (tr :ptv.audit/notification-sent)
         (pos? action-count) (str (tr :ptv.audit/send-notification)
                                  " (" action-count " " (tr :ptv.audit/needing-fixes) ")")
         :else (tr :ptv.audit/send-notification))]

      [notification-dialog
       {:tr tr
        :services? services?
        :lipas-org-id lipas-org-id}]]

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
          (tr (case selected-tab
                "waiting-audit" :ptv.audit/waiting-audit-tab
                "waiting-fixes" :ptv.audit/waiting-fixes-tab
                "done" :ptv.audit/done-tab
                :ptv.audit/waiting-audit-tab))]

         ;; Item count or empty message
         (if (empty? display-items)
           [:> Typography
            {:color "text.secondary"
             :variant "body2"}
            (if services?
              (tr :ptv.audit/no-services)
              (tr :ptv.audit/no-sites))]

           ;; List of items
           [:> Box
            {:sx #js{:maxHeight "60vh"
                     :overflow "auto"}}
            (if services?
              (for [service display-items]
                ^{:key (:service-id service)}
                [service-list-item
                 {:tr tr
                  :service service
                  :selected? (= (:service-id service) (:service-id selected-service))
                  :on-select #(rf/dispatch [:lipas.ui.ptv.events/select-audit-service %])}])
              (for [site display-items]
                ^{:key (:lipas-id site)}
                [site-list-item
                 {:tr tr
                  :site site
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
