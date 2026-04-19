(ns lipas.ui.ptv.site-view
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [lipas.ui.ptv.components :as ptv-components]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.utils :refer [prod?]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

(r/defc audit-summary-notification
  "Displays a summary notification if there are audit issues"
  [{:keys [tr lipas-id]}]
  (let [summary-feedback @(rf/subscribe [::subs/site-audit-field-feedback lipas-id :summary])
        summary-status @(rf/subscribe [::subs/site-audit-field-status lipas-id :summary])
        desc-feedback @(rf/subscribe [::subs/site-audit-field-feedback lipas-id :description])
        desc-status @(rf/subscribe [::subs/site-audit-field-status lipas-id :description])

        has-summary-issues? (and summary-feedback
                                 (not (str/blank? summary-feedback))
                                 (= summary-status "changes-requested"))
        has-desc-issues? (and desc-feedback
                              (not (str/blank? desc-feedback))
                              (= desc-status "changes-requested"))

        issues-count (+ (if has-summary-issues? 1 0)
                        (if has-desc-issues? 1 0))]

    (when (> issues-count 0)
      [:> Alert
       {:severity "warning"
        :variant "outlined"
        :sx #js {:mb 2}}
       [:> AlertTitle (tr :ptv.audit/issues-found)]
       (cond
         (and has-summary-issues? has-desc-issues?)
         (tr :ptv.audit/both-fields-need-changes)

         has-summary-issues?
         (tr :ptv.audit/summary-needs-changes)

         has-desc-issues?
         (tr :ptv.audit/description-needs-changes))])))

(r/defc audit-feedback-component
  "Displays audit feedback for a specific field (summary or description)"
  [{:keys [tr lipas-id field-name]}]
  (let [feedback @(rf/subscribe [::subs/site-audit-field-feedback lipas-id field-name])
        status @(rf/subscribe [::subs/site-audit-field-status lipas-id field-name])]
    (when (and feedback (not (str/blank? feedback)))
      [:> Alert
       {:severity (case status
                    "changes-requested" "error"
                    "approved" "success"
                    "warning")
        :variant "outlined"
        :sx #js {:mt 1 :mb 1}}
       [:> AlertTitle
        (case status
          "changes-requested" (tr :ptv.audit/auditor-feedback-changes-requested)
          "approved" (tr :ptv.audit/auditor-feedback-approved)
          (tr :ptv.audit/auditor-feedback))]
       feedback])))

(r/defc site-view [{:keys [tr lipas-id can-edit? edit-data]}]
  (let [[selected-tab set-selected-tab] (hooks/use-state :fi)
        [editing-org? set-editing-org?] (hooks/use-state false)
        [editing-services? set-editing-services?] (hooks/use-state false)
        [creating-service? set-creating-service?] (hooks/use-state false)
        locale (tr)
        ptv-base (if (prod?)
                   "https://palvelutietovaranto.suomi.fi"
                   "https://palvelutietovaranto.trn.suomi.fi")

        editing* (boolean @(rf/subscribe [:lipas.ui.sports-sites.subs/editing? lipas-id]))
        editing? (and can-edit? editing*)
        sports-site @(rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])

        ;; NOTE: Edit-data and sports-site schema can be a bit different.
        ;; Shouldn't matter for the :ptv fields we mostly need here.
        site (if editing?
               edit-data
               sports-site)

        {:keys [sync-enabled delete-existing last-sync publishing-status]} (:ptv site)

        orgs @(rf/subscribe [::subs/all-orgs])
        ;; Single source of truth for "which org is this site's PTV integration
        ;; scoped to": persisted value → single org → city-code match → nil.
        org-id @(rf/subscribe [::subs/resolved-org-id lipas-id])
        org-ptv-config @(rf/subscribe [::subs/ptv-config-by-ptv-org-id org-id])
        org-languages @(rf/subscribe [::subs/org-languages org-id])

        loading? @(rf/subscribe [::subs/generating-descriptions?])

        type-code (-> site :type :type-code)

        type-code-changed? (not= type-code (:previous-type-code (:ptv site)))
        previous-sent? (ptv-data/is-sent-to-ptv? site)
        ready? (ptv-data/ptv-ready? site)
        candidate-now? (ptv-data/ptv-candidate? site)

        read-only? (or (not editing?)
                       (not candidate-now?))

        types @(rf/subscribe [:lipas.ui.sports-sites.subs/all-types])
        loading-ptv? @(rf/subscribe [::subs/loading-from-ptv?])

        services @(rf/subscribe [::subs/services-by-id org-id])
        services* @(rf/subscribe [::subs/services org-id])
        missing-services-input [{:service-ids #{}
                                 :sub-category-id (-> site :type :type-code types :sub-category)
                                 :sub-category (-> site :search-meta :type :sub-category :name :fi)}]
        missing-services (when (and org-id candidate-now? (not loading-ptv?))
                           (ptv-data/resolve-missing-services org-id services missing-services-input))

        org-options (hooks/use-memo (fn []
                                      (->> orgs
                                           (map (fn [{:keys [name _id ptv-data]}]
                                                  {:label name
                                                   :value (:org-id ptv-data)}))))
                                    [orgs])
        single-org? (= 1 (count org-options))
        service-channel-modified? (= "Modified" (-> site :ptv :service-channel-publishing-status))]

    ;; Load user orgs on mount if not already loaded
    ;; Note: orgs is nil when not loaded, empty vector [] when loaded but user has no orgs
    (hooks/use-effect (fn []
                        (when (nil? orgs)
                          (rf/dispatch [:lipas.ui.org.events/get-user-orgs])))
                      [orgs])

    ;; Fetch PTV org data, services, and integration candidates when org-id is
    ;; selected. Integration candidates are the org's peer sites — needed so
    ;; the style-reference picker has something to pull from.
    ;; The events expect a lipas-org-like structure with [:ptv-data :org-id].
    (hooks/use-effect (fn []
                        (when org-id
                          (let [lipas-org-stub {:ptv-data {:org-id org-id
                                                           :city-codes (:city-codes org-ptv-config)}}]
                            (rf/dispatch [::events/fetch-ptv-org lipas-org-stub])
                            (rf/dispatch [::events/fetch-ptv-services lipas-org-stub])
                            (when (seq (:city-codes org-ptv-config))
                              (rf/dispatch [::events/fetch-integration-candidates lipas-org-stub])))))
                      [org-id org-ptv-config])

    [:> Stack
     {:direction "column"
      :sx #js {:gap 2
               :position "relative"}}

     (when loading-ptv?
       [:> Stack
        {:severity "info"
         :sx #js {:position "absolute"
                  :background "rgba(255, 255, 255, 0.7)"
                  :zIndex 2000
                  :top "0"
                  :left "0"
                  :width "100%"
                  :height "100%"}}
        [:> Stack
         {:direction "column"
          :alignItems "center"
          :sx #js {:position "absolute"
                   :top "50%"
                   :left "50%"
                   :transform "translateX(-50%) translateY(-50%)"
                   :gap 2}}
         [:> CircularProgress]
         (tr :ptv/loading-from-ptv)]])

     ;; 1. Status alert — always visible when relevant. PTV links inline.
     (let [channel-id (first (:service-channel-ids (:ptv site)))
           ptv-links (when channel-id
                       [:> Stack {:direction "row" :spacing 2
                                  :sx #js {:mt 0.5 :flexWrap "wrap"}}
                        [:> Link {:target "new"
                                  :href (str ptv-base "/channels/serviceLocation/" channel-id)}
                         (tr :ptv/open-in-ptv)]
                        (when (prod?)
                          [:> Link {:target "new"
                                    :href (str "https://www.suomi.fi/palvelut/palvelupiste/x/" channel-id)}
                           (tr :ptv/open-in-suomi-fi)])])
           alert (fn [severity body]
                   [:> Alert {:severity severity}
                    body
                    ptv-links])]
       (cond
         (:error (:ptv site))
         (alert "error" (str (tr :ptv/integration-error) " " (:message (:error (:ptv site)))))

         (and previous-sent? candidate-now? ready?)
         (cond
           sync-enabled (alert "success" (tr :ptv/integration-enabled))
           delete-existing (alert "success" (tr :ptv/will-be-archived-on-save))
           :else (alert "success" (tr :ptv/integration-enabled-sync-off)))

         (and previous-sent? (not candidate-now?))
         (alert "warning" (tr :ptv/will-be-archived-type-changed))

         (and candidate-now? ready? sync-enabled)
         (alert "info" (tr :ptv/new-service-location-will-be-created))

         (and candidate-now? (not ready?) sync-enabled)
         (alert "info" (tr :ptv/data-incomplete))

         (and (not candidate-now?) sync-enabled)
         (alert "warning" (tr :ptv/not-suitable-for-export))

         :else nil))

     ;; 2. Sync + archive switches.
     [:> Stack
      {:direction "row"}
      [:> FormControlLabel
       {:label (tr :ptv.actions/integration-enabled)
        :control (r/as-element
                  [:> Switch
                   {:disabled read-only?
                    :checked sync-enabled
                    :on-change (fn [_e v]
                                 (rf/dispatch [::events/toggle-site-sync-enabled lipas-id v]))}])}]
      (when (and (not sync-enabled)
                 previous-sent?)
        [:> FormControlLabel
         {:label (tr :ptv.actions/archive-in-ptv)
          :control (r/as-element
                    [:> Switch
                     {:disabled read-only?
                      :checked (or delete-existing false)
                      :on-change (fn [_e v]
                                   (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :delete-existing] v]))}])}])]

     ;; 3. Organization selector — only relevant once the user opts in to sync.
     ;; Pre-filled from ::resolved-org-id. Shown as a caption + edit affordance
     ;; when the org is already known; falls back to a raw selector when it isn't.
     (when sync-enabled
       (let [org-name (some #(when (= org-id (get-in % [:ptv-data :org-id])) (:name %)) orgs)]
         [ptv-components/ptv-link-field
          {:tr tr
           :label (tr :ptv.actions/select-org)
           :items (when org-id [{:id org-id :name org-name}])
           :can-edit? (and (not single-org?) (not read-only?))
           :editing? editing-org?
           :on-edit #(set-editing-org? true)
           :on-cancel #(set-editing-org? false)
           :tooltip (tr :ptv.actions/select-org)
           :selector-component
           [autocomplete2
            {:options org-options
             :disabled (or loading? read-only?)
             :label (tr :ptv.actions/select-org)
             :value org-id
             :onChange (fn [_e v]
                         (rf/dispatch [:lipas.ui.sports-sites.events/edit-field
                                       lipas-id [:ptv :org-id] (:value v)])
                         (set-editing-org? false))}]}]))

     ;; 4. Service selector (only meaningful once sync is on + org is known + site is a candidate).
     (when (and sync-enabled org-id candidate-now?)
       (let [service-ids (set (:service-ids (:ptv site)))
             linked-services (filter #(contains? service-ids (:service-id %)) services*)
             sub-cat-id (-> site :type :type-code types :sub-category)
             needs-service? (and (empty? service-ids) (seq missing-services))]
         [:> FormControl
          [ptv-components/ptv-link-field
           {:tr tr
            :label (tr :ptv/services)
            :items (for [s linked-services]
                     {:id (:service-id s)
                      :name (:label s)
                      :url (str ptv-base "/service/" (:service-id s))})
            :can-edit? (not read-only?)
            :editing? editing-services?
            :on-edit #(set-editing-services? true)
            :on-cancel #(set-editing-services? false)
            :tooltip (tr :ptv.actions/change-service)
            :selector-component
            [controls/services-selector
             {:disabled (or loading? read-only?)
              :value (:service-ids (:ptv site))
              :options services*
              :on-change (fn [ids]
                           (rf/dispatch [:lipas.ui.sports-sites.events/edit-field
                                         lipas-id [:ptv :service-ids] ids])
                           (set-editing-services? false))
              :value-fn :service-id
              :label (tr :ptv.actions/select-service)}]}]

          ;; Alerts about the service state.
          (cond
            ;; Previously-synced site whose type changed and no matching PTV
            ;; service exists for the new type.
            (and (seq missing-services) previous-sent? type-code-changed?)
            [:> Alert {:severity "warning"} (tr :ptv/service-missing-for-type)]

            ;; Type changed but a matching service exists — heads-up that the
            ;; site may be re-linked on next sync.
            (and previous-sent? type-code-changed?)
            [:> Alert {:severity "info"} (tr :ptv/type-changed-may-rebind)])

          ;; When the site isn't yet attached to any service and no lipas-managed
          ;; service exists for its sub-category, offer a "create new" shortcut.
          ;; Linking an existing PTV service isn't surfaced as a separate flow
          ;; here — the main service selector above already lists every service
          ;; in the org. Auto-attaches on success.
          (when needs-service?
            (if creating-service?
              [ptv-components/add-service-create-form
               {:org-id org-id
                :locked-sub-category-id sub-cat-id
                :lipas-id lipas-id
                :on-cancel #(set-creating-service? false)}]
              [:> Stack {:spacing 1 :sx #js {:mt 1}}
               [:> Typography {:variant "body2"}
                (tr :ptv.service/needs-service-helper)]
               [:> Button
                {:variant "outlined" :size "small"
                 :disabled read-only?
                 :sx #js {:textTransform "none" :alignSelf "flex-start"}
                 :on-click #(set-creating-service? true)}
                (tr :ptv.service/create-new)]]))]))

     ;; 5. Descriptions and everything below — gated on sync-enabled + org-id.
     (when (and sync-enabled org-id)
       [:<>
        ;; Audit summary notification - show if there are audit issues
        [audit-summary-notification {:tr tr :lipas-id lipas-id}]

        ;; Warn when PTV has unpublished edits that would block sync.
        (when service-channel-modified?
          [:> Alert {:severity "warning" :variant "outlined"}
           (tr :ptv/modified-in-ptv)])

        ;; Generate + Translate buttons grouped in one row, matching the wizard.
        ;; Use org-languages (real PTV org config) rather than :languages on the
        ;; site — calc-derived-fields writes stale "fi"-only defaults there from
        ;; the static org table, which doesn't reflect the live PTV config.
        (let [effective-languages org-languages
              other-langs (disj (set (map name effective-languages))
                                (name selected-tab))
              summary-filled? (seq (get-in edit-data [:ptv :summary selected-tab]))
              description-filled? (seq (get-in edit-data [:ptv :description selected-tab]))
              has-text? (and summary-filled? description-filled?)]
          [:> Stack
           {:direction "row" :spacing 1 :sx #js {:flexWrap "wrap" :position "relative"}}
           [:> Button
            {:disabled (or loading?
                           read-only?)
             :variant "outlined"
             :size "small"
             :sx #js {:textTransform "none"}
             :on-click (fn [_e]
                         (rf/dispatch [::events/generate-descriptions-from-data lipas-id]))}
            (tr :ptv.actions/generate-with-ai)]
           (when (> (count effective-languages) 1)
             [:> Tooltip {:title (tr :ptv.wizard/translate-to-other-langs-tooltip)}
              [:> Button
               {:disabled (or loading?
                              read-only?
                              (not has-text?))
                :variant "outlined"
                :size "small"
                :sx #js {:textTransform "none"}
                :startIcon (when loading?
                             (r/as-element [:> CircularProgress {:size 16 :color "inherit"}]))
                :on-click (fn [_e]
                            (rf/dispatch [::events/translate-to-other-langs lipas-id {:from (name selected-tab)
                                                                                      :to other-langs}]))}
               (str (tr :ptv.wizard/translate-to-other-langs) " ("
                    (str/join ", " (map str/upper-case (sort other-langs)))
                    ")")]])
           (when loading?
             [:> CircularProgress
              {:size 24
               :sx #js {:position "absolute"
                        :top "50%"
                        :left "50%"
                        :mt "-12px"}}])])

        [controls/lang-selector
         {:value selected-tab
          :on-change set-selected-tab
          :enabled-languages (set org-languages)}]

        ;; Summary
        (let [v (or (get-in edit-data [:ptv :summary selected-tab])
                    (get-in sports-site [:ptv :summary selected-tab])
                    "")]
          [text-fields/text-field
           {:disabled (or loading?
                          read-only?)
            :multiline true
            :variant "outlined"
            :on-change (fn [v]
                         (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :summary selected-tab] v]))
            :label (tr :ptv/summary)
            :value v
            :helperText (str (count v) "/150")
            :error (> (count v) 150)}])

        ;; Summary audit feedback
        [audit-feedback-component
         {:tr tr
          :lipas-id lipas-id
          :field-name :summary}]

        ;; Description
        (let [v (or (get-in edit-data [:ptv :description selected-tab])
                    (get-in sports-site [:ptv :description selected-tab])
                    "")]
          [text-fields/text-field
           {:disabled (or loading?
                          read-only?)
            :variant "outlined"
            :rows 5
            :multiline true
            :on-change (fn [v]
                         (rf/dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:ptv :description selected-tab] v]))
            :label (tr :ptv/description)
            :value v
            :helperText (str (count v) "/2500")
            :error (> (count v) 2500)}])

        ;; Description audit feedback
        [audit-feedback-component
         {:tr tr
          :lipas-id lipas-id
          :field-name :description}]])]))
