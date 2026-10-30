(ns lipas.ui.ptv.views
  (:require ["@mui/icons-material/CheckCircle$default" :as CheckCircleIcon]
            ["@mui/icons-material/Close$default" :as CloseIcon]
            ["@mui/icons-material/Message$default" :as MessageIcon]
            ["@mui/icons-material/Sync$default" :as Sync]
            ["@mui/icons-material/SyncDisabled$default" :as SyncDisabled]
            ["@mui/icons-material/SyncProblem$default" :as SyncProblem]
            ["@mui/icons-material/Warning$default" :as WarningIcon]
            ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/AppBar$default" :as AppBar]
            ;; clj-kondo false positive: `Box` collides with the cljs.core/Box
            ;; deftype, so the [:> Box ...] hiccup use below isn't recognized
            ;; as a use of this alias.
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Radio$default" :as Radio]
            ["@mui/material/RadioGroup$default" :as RadioGroup]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Step$default" :as Step]
            ["@mui/material/StepButton$default" :as StepButton]
            ["@mui/material/Stepper$default" :as Stepper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableContainer$default" :as TableContainer]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [goog.string.format]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.data.ptv-service-guidance :as service-guidance]
            [lipas.data.ptv-services :as ptv-services]
            [lipas.data.status :as status]
            [lipas.data.types :as types]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.ptv.audit :as audit]
            [lipas.ui.ptv.components :as ptv-components]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.review :as review]
            [lipas.ui.ptv.review-data :as review-data]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.utils :as utils :refer [<== ==>]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

;; Memo
;; - preset service structure with descriptions
;; - linking to existing service channels
;;   - maybe define what to overwrite?
;;   - ...or pre-fill fields from PTV via linking?
;;   - ...anyway, somehow re-using stuff that's already there
;; - auto-sync on save

(defn lang-selector
  [{:keys [value on-change opts]}]
  (let [opts (set opts)]
    [:> Tabs
     {:value (if (keyword? value) (name value) value)
      :on-change (fn [e v] (on-change e (keyword v)))}
     [:> Tab {:value "fi" :label "FI"}]
     (when (contains? opts "se")
       [:> Tab {:value "se" :label "SE"}])
     (when (contains? opts "en")
       [:> Tab {:value "en" :label "EN" :disabled (not (contains? opts "en"))}])]))

(defn org-selector
  [{:keys [label]}]
  (let [orgs (<== [::subs/all-orgs])
        selected-org (<== [::subs/selected-org])]
    [selects/select
     {:items orgs
      :label label
      :label-fn :name
      :value-fn identity
      :value selected-org
      :on-change #(==> [::events/select-org %])}]))

(r/defc service-channel-selector
  [{:keys [org-id value on-change label value-fn]
    :or {value-fn identity
         label ""}}]
  (let [items @(rf/subscribe [::subs/service-channels-list org-id])
        options (hooks/use-memo (fn []
                                  (->> items
                                       (map (fn [x]
                                              {:value (value-fn x)
                                               :label (:name x)}))
                                       (controls/sort-by-label)))
                                [items value-fn])]
    [autocomplete2
     {:options options
      :multiple false
      :label label
      :value (first value)
      :onChange (fn [_e v]
                  (on-change [(:value v)]))}]))

(defn audit-feedback-component
  "Displays audit feedback for a specific field (summary or description).
   Renders resolved (info) once the municipality has fixed the text."
  [{:keys [lipas-id field-name]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        org-id (<== [::subs/selected-ptv-org-id])
        field-audit (<== [::subs/site-audit-field-display org-id lipas-id field-name])]
    [ptv-components/audit-feedback-alert
     {:tr tr
      :field-audit field-audit}]))

(defn service-audit-feedback-component
  "Displays auditor feedback for a PTV Service field. `current-content`
   is the live PTV content of the field — the verdict snapshot is compared
   against it so a fix synced to PTV resolves the alert."
  [{:keys [service-id field-name current-content]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        field-audit (<== [::subs/service-audit-field service-id field-name])]
    [ptv-components/audit-feedback-alert
     {:tr tr
      :field-audit field-audit
      :current-content current-content}]))

(defn service-writing-guidance
  "Collapsed accordion showing the DVV per-sub-category content guidance
   for a Service field. `field` is :description or :user-instruction.
   Renders nothing when no guidance exists for the sub-category (e.g.
   adopted services without a sub-category mapping). Guidance body is
   Finnish-only by source."
  [{:keys [tr sub-category-id field]}]
  (when-let [text (get-in service-guidance/guidance [sub-category-id field])]
    [:> Accordion {:disableGutters true :elevation 0 :variant "outlined"}
     [:> AccordionSummary {:expandIcon (r/as-element [:> Icon "expand_more"])}
      [:> Stack {:direction "row" :spacing 1 :align-items "center"}
       [:> Icon {:fontSize "small" :color "action"} "help_outline"]
       [:> Typography {:variant "body2"}
        (case field
          :description      (tr :ptv/writing-guidance-description)
          :user-instruction (tr :ptv/writing-guidance-user-instruction))]]]
     [:> AccordionDetails
      [:> Typography {:variant "body2" :sx #js {:whiteSpace "pre-line"}}
       text]]]))

(def ptv-link-field ptv-components/ptv-link-field)

(defn editable-field
  "Generic read/edit toggle field with Valmis/Peruuta buttons.
   Props:
     :tr          - translator fn
     :label       - caption label text
     :tooltip     - optional tooltip for edit button
     :read-view   - hiccup to show in read mode (the display content)
     :edit-view   - hiccup to show in edit mode (the editor)
     :on-confirm  - called when user clicks Valmis
     :on-cancel   - called when user clicks Peruuta
     :on-edit     - called when user clicks the pencil icon"
  [{:keys [tr label tooltip read-view edit-view on-confirm on-cancel on-edit editing?]}]
  (if editing?
    [:> Stack {:spacing 0.5}
     [:> Typography {:variant "caption" :color "text.secondary"} label]
     edit-view
     [:> Stack {:direction "row" :spacing 1}
      [:> Button {:size "small" :variant "text"
                  :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                  :on-click on-confirm}
       (tr :actions/done)]
      [:> Button {:size "small" :variant "text"
                  :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                  :on-click on-cancel}
       (tr :actions/cancel)]]]
    [:> Stack {:spacing 0.5}
     [:> Stack {:direction "row" :align-items "center" :spacing 0.5}
      [:> Typography {:variant "caption" :color "text.secondary"} label]
      [:> Tooltip {:title (or tooltip "")}
       [:> IconButton {:size "small" :on-click on-edit
                       :sx #js {:p 0}}
        [:> Icon {:sx #js {:fontSize "1rem"}} "edit"]]]]
     read-view]))

(defn- service-name-field
  "Editable PTV service name with edit toggle. Confirm dispatches
   ::events/set-service-candidate-name for `locale` under `edit-key`; sync
   flushes the edited name to PTV. `label` defaults to the plain field name."
  [{:keys [tr edit-key locale label service-id ptv-base display-name]}]
  (r/with-let [editing? (r/atom false)
               draft-name (r/atom nil)]
    [editable-field
     {:tr tr
      :label (or label (tr :ptv/service-name))
      :editing? @editing?
      :on-edit (fn []
                 (reset! draft-name display-name)
                 (reset! editing? true))
      :on-confirm (fn []
                    (==> [::events/set-service-candidate-name edit-key locale @draft-name])
                    (reset! editing? false))
      :on-cancel (fn []
                   (reset! draft-name nil)
                   (reset! editing? false))
      :read-view (if (str/blank? display-name)
                   [:> Typography {:variant "body2" :color "text.secondary" :sx #js {:fontStyle "italic"}}
                    (tr :ptv.service/name-missing)]
                   [:> Link {:href (str ptv-base "/service/" service-id)
                             :target "_blank"
                             :variant "body2"}
                    display-name])
      :edit-view [text-fields/text-field
                  {:value (or @draft-name display-name)
                   :variant "outlined"
                   :size "small"
                   :on-change #(reset! draft-name %)}]}]))

(defn form-left-column
  [{:keys [org-id tr site services ptv-base org-languages selected-tab]}]
  (r/with-let [editing-services? (r/atom false)
               editing-channel? (r/atom false)
               confirm-archive? (r/atom false)]
    (let [generating? (<== [::subs/generating-descriptions?])
          syncing? (<== [::subs/syncing-service-location? (:lipas-id site)])
          service-ids (set (:service-ids site))
          linked-services (filter #(contains? service-ids (:service-id %)) services)
          channel-id (first (:service-channel-ids site))
          ;; An archived (PTV "Deleted") channel is gone from /list/organization,
          ;; so its name can't be resolved — label it instead of showing the
          ;; bare UUID. Its PTV link 404s, so drop it (rendered as plain text).
          archived? (= "Deleted" (:publishing-status site))
          channel-name (or (:service-channel-name site)
                           (when archived? (tr :ptv.actions/service-channel-archived)))
          name-conflict (:name-conflict site)
          sync-status (:sync-status site)
          ;; a failed sync (:blocked by its error) can be tried again; an
          ;; unpublished draft in PTV blocks it (modified? below)
          needs-sync? (contains? #{:ready :awaiting-choice :blocked} sync-status)]
      [:> Stack {:spacing 2}

       ;; Services
       [ptv-link-field
        {:tr tr
         :label (tr :ptv/in-ptv (tr :ptv/services))
         :items (for [s linked-services]
                  {:id (:service-id s)
                   :name (:label s)
                   :url (str ptv-base "/service/" (:service-id s))})
         :editing? @editing-services?
         :on-edit #(reset! editing-services? true)
         :on-cancel #(reset! editing-services? false)
         :tooltip (tr :ptv.actions/change-service)
         :selector-component
         [controls/services-selector
          {:options services
           :value (:service-ids site)
           :on-change (fn [ids]
                        (rf/dispatch [::events/select-services site ids])
                        (reset! editing-services? false))
           :value-fn :service-id
           :label (tr :ptv/services)}]}]

       ;; Service channel
       (if channel-id
         ;; Already linked - show link with edit option
         [ptv-link-field
          {:tr tr
           :label (tr :ptv/in-ptv (tr :ptv/service-channel))
           :items [{:id channel-id
                    :name channel-name
                    :url (when-not archived?
                           (str ptv-base "/channels/serviceLocation/" channel-id))}]
           :editing? @editing-channel?
           :on-edit #(reset! editing-channel? true)
           :on-cancel #(reset! editing-channel? false)
           :tooltip (tr :ptv.actions/change-service-channel)
           :selector-component
           [service-channel-selector
            {:org-id org-id
             :value (:service-channel-ids site)
             :value-fn :service-channel-id
             :on-change (fn [v]
                          (==> [::events/select-service-channels site v])
                          (reset! editing-channel? false))
             :label (tr :ptv/service-channel)}]}]
         ;; Not linked - show "new will be created" with option to link existing
         [:> Stack {:spacing 0.5}
          [:> Typography {:variant "caption" :color "text.secondary"}
           (tr :ptv/service-channel)]
          [:> Typography {:variant "body2" :color "text.secondary" :sx #js {:fontStyle "italic"}}
           (tr :ptv.actions/new-service-channel-will-be-created)]
          ;; During a name-conflict the selector stays open: the conflict
          ;; guidance directs the user to it.
          (if (or @editing-channel? name-conflict)
            [:> Stack {:spacing 0.5}
             [service-channel-selector
              {:org-id org-id
               :value (:service-channel-ids site)
               :value-fn :service-channel-id
               :on-change (fn [v]
                            (==> [::events/select-service-channels site v])
                            (reset! editing-channel? false))
               :label (tr :ptv/service-channel)}]
             (when-not name-conflict
               [:> Button {:size "small" :variant "text"
                           :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                           :on-click #(reset! editing-channel? false)}
                (tr :actions/cancel)])]
            [:> Button {:size "small" :variant "text"
                        :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                        :on-click #(reset! editing-channel? true)}
             (tr :ptv.actions/attach-existing-service-channel)])])

       ;; AI generate + translate buttons
       (let [from-lang @selected-tab
             other-langs (disj (set (map keyword org-languages)) from-lang)
             has-text? (and (seq (get-in site [:summary from-lang]))
                            (seq (get-in site [:description from-lang])))]
         [:> Stack {:direction "row" :spacing 1 :flex-wrap "wrap" :align-items "flex-start"}
          [:> Button
           {:variant "outlined" :size "small" :disabled (or generating? syncing?)
            :sx #js {:textTransform "none"}
            :startIcon (r/as-element
                         (if generating?
                           [:> CircularProgress {:size 16 :color "inherit"}]
                           [:> Icon "auto_fix_high"]))
            :on-click #(==> [::events/generate-descriptions (:lipas-id site) [] []])}
           (tr :ptv.actions/generate-with-ai)]
          (when (> (count org-languages) 1)
            [:> Tooltip {:title (tr :ptv.wizard/translate-to-other-langs-tooltip)}
             [:span
              [:> Button
               {:size "small" :variant "outlined"
                :disabled (or generating? (not has-text?))
                :startIcon (r/as-element
                             (if generating?
                               [:> CircularProgress {:size 16 :color "inherit"}]
                               [:> Icon "translate"]))
                :sx #js {:textTransform "none"}
                :on-click #(==> [::events/translate-site-descriptions
                                 (:lipas-id site) from-lang other-langs])}
               (str (tr :ptv.wizard/translate-to-other-langs) " ("
                    (str/join ", " (map (comp str/upper-case name) (sort other-langs)))
                    ")")]]])])

       ;; Sync / Export button with helper text
       (let [sync-enabled? (:sync-enabled site)
             has-service? (seq (:service-ids site))
             has-summary? (some-> site :summary :fi count (> 5))
             has-description? (some-> site :description :fi count (> 5))
             ;; Validate the would-be payload against the same malli schema the
             ;; backend coerces against, so an over-length text (or other schema
             ;; violation) is caught here instead of bouncing as a 400.
             schema-error-keys (<== [::subs/service-location-schema-errors (:lipas-id site)])
             schema-errors (map tr schema-error-keys)
             valid? (and sync-enabled? has-service? has-summary? has-description?
                         (empty? schema-error-keys))
             synced? (:last-sync site)
             modified? (= "Modified" (:service-channel-publishing-status site))
             disabled? (or syncing? modified? (not valid?) (and synced? (not needs-sync?)))
             ;; Show the first missing requirement as helper text
             hint (cond
                    (not sync-enabled?) (tr :ptv/hint-enable-sync)
                    (not has-service?) (tr :ptv/hint-select-service)
                    (not has-summary?) (tr :ptv/hint-add-summary)
                    (not has-description?) (tr :ptv/hint-add-description)
                    (and synced? (not needs-sync?)) (tr :ptv/hint-up-to-date))
             ;; Every reason the button is disabled, for the tooltip.
             reasons (->> (concat (when hint [hint]) schema-errors)
                          (remove nil?))
             ;; One per line: the hints are phrases, the errors sentences
             reason-lines (into [:<>] (map (fn [reason] [:div reason])) reasons)]
         [:<>
          [:> Tooltip {:title (if (and disabled? (seq reasons))
                                (r/as-element reason-lines)
                                "")}
           [:span
            [:> Button
             {:variant "contained"
              :color "secondary"
              :size "small"
              :full-width true
              :disabled disabled?
              :sx #js {:textTransform "none"}
              :startIcon (r/as-element
                           (if syncing?
                             [:> CircularProgress {:size 16 :color "inherit"}]
                             [:> Icon (if synced? "sync" "ios_share")]))
              ;; through the review: asks before overwriting changes made in PTV
              :on-click #(==> [::events/sync-with-review (:lipas-id site)
                               (-> site :review :rows)])}
             (if synced?
               (tr :ptv.actions/sync-now)
               (tr :ptv.wizard/export-service-locations-to-ptv))]]]
          (when (or hint (seq schema-errors))
            [:> Typography {:variant "caption" :color "text.secondary" :component "div"}
             reason-lines])

          ;; Explicit archive — only when sync is OFF and the site is currently
          ;; published in PTV (mirrors the sports-site PTV tab). Two-step confirm;
          ;; reversible by re-enabling sync (resurrects the same channel).
          (when (and (not sync-enabled?)
                     channel-id
                     (= "Published" (:service-channel-publishing-status site)))
            (if @confirm-archive?
              [:> Stack {:direction "row" :spacing 1 :sx #js {:mt 1 :alignItems "center"}}
               [:> Typography {:variant "caption" :color "text.secondary"}
                (tr :ptv.actions/archive-confirm)]
               [:> Button {:size "small" :variant "outlined" :color "error"
                           :disabled syncing?
                           :sx #js {:textTransform "none"}
                           :on-click (fn []
                                       (reset! confirm-archive? false)
                                       (==> [::events/archive-ptv-service-location (:lipas-id site)]))}
                (tr :ptv.actions/archive)]
               [:> Button {:size "small" :variant "text"
                           :sx #js {:textTransform "none"}
                           :on-click #(reset! confirm-archive? false)}
                (tr :actions/cancel)]]
              [:> Button {:size "small" :variant "text" :color "error"
                          :disabled syncing?
                          :sx #js {:textTransform "none" :alignSelf "flex-start"}
                          :startIcon (r/as-element [:> Icon "archive"])
                          :on-click #(reset! confirm-archive? true)}
               (tr :ptv.actions/archive-in-ptv)]))])])))

(defn form-right-column
  [{:keys [tr site org-languages selected-tab]}]
  (let [loading? (<== [::subs/generating-descriptions?])]
    [:> Stack {:spacing 2}

     [:> Stack {:direction "row" :align-items "center"
                :flex-wrap "wrap" :spacing 1
                :sx #js {:rowGap 1 :minWidth 0}}
      [controls/lang-selector
       {:value @selected-tab
        :on-change #(reset! selected-tab %)
        :enabled-languages (set org-languages)}]]

     ;; Summary
     (let [v (or (get-in site [:summary @selected-tab]) "")]
       [text-fields/text-field
        {:disabled loading?
         :multiline true
         :variant "outlined"
         :on-change #(==> [::events/set-summary site @selected-tab %])
         :label (tr :ptv/summary)
         :value v
         :inputProps #js {:maxLength ptv-data/max-summary-length}
         :helperText (controls/text-length-hint tr v ptv-data/max-summary-length)
         :error (controls/text-length-error? v ptv-data/max-summary-length)}])

     [audit-feedback-component
      {:lipas-id (:lipas-id site)
       :field-name :summary}]

     ;; Description
     (let [v (or (get-in site [:description @selected-tab]) "")]
       [text-fields/text-field
        {:disabled loading?
         :variant "outlined"
         :rows 7
         :multiline true
         :on-change #(==> [::events/set-description site @selected-tab %])
         :label (tr :ptv/description)
         :value v
         :inputProps #js {:maxLength ptv-data/max-description-length}
         :helperText (controls/text-length-hint tr v ptv-data/max-description-length)
         :error (controls/text-length-error? v ptv-data/max-description-length)}])

     [audit-feedback-component
      {:lipas-id (:lipas-id site)
       :field-name :description}]]))

(defn ptv-base-url
  []
  (if (utils/prod?)
    "https://palvelutietovaranto.suomi.fi"
    "https://palvelutietovaranto.trn.suomi.fi"))

(defn- site-notices
  "① The state of the site's link to its PTV location, with what can be
   done about it: the same notices wherever the site is reviewed."
  [tr site]
  (let [lipas-id (:lipas-id site)
        org-id (:org-id site)
        channel-id (first (:service-channel-ids site))
        name-conflict (:name-conflict site)
        double-link-block (<== [::subs/double-link-block lipas-id])
        org-sites (<== [::subs/org-sports-sites org-id])
        other-ids (disj (get (<== [::subs/channel-id->lipas-ids org-id]) channel-id #{}) lipas-id)
        unreachable? (contains? (<== [::subs/unreachable-channels]) channel-id)]
    (cond-> []
      (= "Deleted" (:publishing-status site))
      (conj {:key :archived
             :text (tr :ptv.review/notice-archived)
             :actions [{:label (tr :ptv.review/republish)
                        :primary? true
                        :on-click #(==> [::events/republish site])}
                       {:label (tr :ptv.review/unlink)
                        :on-click #(==> [::events/select-service-channels site []])}]})

      (= "Modified" (:service-channel-publishing-status site))
      (conj {:key :modified
             :text (tr :ptv.review/notice-modified)
             :actions [{:label (tr :ptv.review/open-in-ptv)
                        :href (str (ptv-base-url) "/channels/serviceLocation/" channel-id)}
                       {:label (tr :ptv.review/recheck)
                        :busy? (<== [::subs/rechecking? channel-id])
                        :on-click #(==> [::events/recheck-draft {:org-id org-id
                                                                 :channel-id channel-id}])}]})

      (seq other-ids)
      (conj {:key :double-link
             :text (tr :ptv.review/notice-double-link
                       (str/join ", " (map #(or (get-in org-sites [% :name]) %) other-ids)))})

      double-link-block
      (conj {:key :double-link-block
             :severity "error"
             :title (tr :ptv.double-link/warning-title)
             :text (str (tr :ptv.double-link/blocked (:name double-link-block)) " "
                        (tr :ptv.double-link/explanation))})

      name-conflict
      (conj {:key :name-conflict
             :text (tr :ptv.review/notice-name-conflict (:name site))
             :actions [{:label (tr :ptv.wizard/attach-to-conflicting-service-channel)
                        :on-click #(==> [::events/select-service-channels
                                         {:lipas-id lipas-id}
                                         [(:service-channel-id name-conflict)]])}]})

      (and channel-id
           (review-data/names-differ-wholesale? (:name site) (:service-channel-name site)))
      (conj {:key :names-differ
             :text (tr :ptv.review/notice-names-differ (:service-channel-name site))})

      unreachable?
      (conj {:key :unreachable
             :text (tr :ptv.review/notice-unreachable)
             :actions [{:label (tr :ptv.review/retry)
                        :on-click #(==> [::events/refresh-service-channel org-id channel-id])}]}))))

(defn- site-review
  "The site's sync review and the state of its PTV link. `footer` says
   what the choices go into; by default the next sync, named as its button
   is (see the form's sync button: \"Synkronoi nyt\", or the first
   export's label)."
  [{:keys [tr site footer]}]
  (let [lipas-id (:lipas-id site)
        org-id (:org-id site)
        open? (<== [::subs/review-open? lipas-id])]
    [review/review-panel
     {:id (review-data/panel-dom-id lipas-id)
      :tr tr
      :review (:review site)
      :choices (<== [::subs/review-choices lipas-id])
      :open? open?
      :on-toggle #(==> [::events/set-review-open lipas-id (not open?)])
      :on-take (fn [rows side] (==> [::events/review-take :ptv-tool lipas-id rows side]))
      :on-take-value (fn [row value] (==> [::events/review-take-value :ptv-tool lipas-id row value]))
      :on-reset #(==> [::events/review-reset :ptv-tool lipas-id])
      :written? (<== [::subs/review-written? lipas-id])
      ;; the tool is for PTV managers; taking a site's own data (its name,
      ;; contacts, address) changes the site, which needs the right to edit it
      :rights {:ptv-manager? true
               :site-editor? (contains? (<== [::subs/editable-site-ids org-id]) lipas-id)}
      :org-langs (<== [::subs/org-languages org-id])
      :notices (site-notices tr site)
      :draft? (= "Modified" (:service-channel-publishing-status site))
      :first-open-id (review-data/first-open-dom-id lipas-id)
      :footer (or footer
                  (if (:last-sync site)
                    (tr :ptv.review/sync-tab-footer (tr :ptv.actions/sync-now))
                    (tr :ptv.review/export-tab-footer
                        (tr :ptv.wizard/export-service-locations-to-ptv))))}]))

(defn form
  [{:keys [org-id tr site]}]
  (r/with-let [selected-tab (r/atom :fi)]
    (let [services @(rf/subscribe [::subs/services org-id])
          org-languages (<== [::subs/org-languages org-id])
          ptv-base (ptv-base-url)]
      [:> Stack {:spacing 2 :sx #js {:pt 2 :pb 2}}

       ;; What differs from PTV and who changed it; the choices made go
       ;; into the next sync.
       [site-review {:tr tr :site site}]

       [checkboxes/switch
        {:label (tr :ptv.actions/export-disclaimer)
         :value (:sync-enabled site)
         ;; Liikuntapaikat tab has no batch "save all" — persist the toggle now.
         :on-change #(==> [::events/toggle-sync-enabled site % :persist? true])}]

       ;; Flex rather than a Grid container: its negative margins pushed the
       ;; row 8px past the table, which then scrolled sideways.
       [:> Stack {:direction #js {:xs "column" :md "row"} :spacing 3}
        [:> Stack {:sx #js {:flex "5 1 0" :minWidth 0}}
         [form-left-column {:org-id org-id :tr tr :site site
                            :services services :ptv-base ptv-base
                            :org-languages org-languages :selected-tab selected-tab}]]
        [:> Stack {:sx #js {:flex "7 1 0" :minWidth 0}}
         [form-right-column {:tr tr :site site :org-languages org-languages
                             :selected-tab selected-tab}]]]])))

(defn- filter-bar
  "Collapsible filter bar of the PTV tool's tables: a search, and a row of
   status chips (`chips`, [value label] pairs, the first :all) of which one
   is selected."
  [{:keys [tr expanded? on-toggle-expanded
           search-text on-search-change chips status-filter on-status-change
           total-count filtered-count extra-chip]}]
  (let [filtering? (or (seq search-text) (not= :all status-filter))
        status-chip (fn [[value label]]
                      ^{:key value}
                      [:> Chip
                       {:label (str label (when (= value status-filter) (str " (" filtered-count ")")))
                        :size "small"
                        :variant (if (= value status-filter) "filled" "outlined")
                        :color (if (= value status-filter) "primary" "default")
                        :on-click #(on-status-change (if (= value status-filter) :all value))}])]
    [:> Stack {:sx #js {:px 2 :py 1}}
     ;; Toggle row: always visible
     [:> Stack {:direction "row" :align-items "center" :spacing 1}
      [:> Button
       {:size "small" :variant "text"
        :sx #js {:textTransform "none" :p 0 :minWidth 0}
        :startIcon (r/as-element
                     [:> Icon {:sx #js {:fontSize "1.2rem"}}
                      (if expanded? "filter_list_off" "filter_list")])
        :on-click on-toggle-expanded}
       (str filtered-count "/" total-count)]
      ;; a filter set from elsewhere, shown whether the bar is open or not
      extra-chip
      (when (and (not expanded?) filtering?)
        [:> Chip {:label (if (seq search-text)
                           (str "\"" search-text "\"")
                           (some (fn [[value label]] (when (= value status-filter) label)) chips))
                  :size "small" :variant "outlined" :color "primary"
                  :onDelete #(do (on-search-change "") (on-status-change :all))}])]

     ;; Expanded content
     [:> Collapse {:in expanded?}
      [:> Stack {:spacing 1.5 :sx #js {:pt 1}}
       [text-fields/text-field
        {:value search-text
         :on-change on-search-change
         :variant "outlined"
         :size "small"
         :placeholder (tr :ptv/search-placeholder)
         :InputProps {:startAdornment (r/as-element [:> Icon {:sx #js {:mr 1 :color "text.secondary"}} "search"])}}]
       [:> Stack {:direction "row" :spacing 0.5 :flex-wrap "wrap" :align-items "center" :useFlexGap true}
        (map status-chip chips)]]]]))

(defn- site-filter-chips
  [tr]
  (for [k [:all :sync-enabled :not-synced :awaiting-choice :ready :blocked :ok
           :missing-descriptions :sync-disabled]]
    [k (tr (keyword "ptv" (str "filter-" (name k))))]))

(defn- filter-sites
  "Apply search text and status filter to sites list."
  [sites search-text status-filter]
  (cond->> sites
    (seq search-text)
    (filter (fn [{:keys [name]}]
              (str/includes? (str/lower-case (or name ""))
                             (str/lower-case search-text))))
    (not= :all status-filter)
    (filter (fn [{:keys [sync-enabled sync-status valid]}]
              (case status-filter
                :sync-enabled sync-enabled
                :sync-disabled (not sync-enabled)
                (:not-synced :awaiting-choice :ready :blocked :ok)
                (and sync-enabled (= status-filter sync-status))
                :missing-descriptions (not valid)
                true)))))

(defn- shown-sites
  "The sites the table lists: those matching the filter, plus any row the
   user has open. Every edit writes straight into the cached site the
   statuses are derived from, so without the pin an open row would vanish
   mid-edit (or right after its sync) the moment it stops matching.
   Keeps the order of `sites`."
  [sites matching-ids open-ids]
  (filter #(or (matching-ids (:lipas-id %)) (open-ids (:lipas-id %))) sites))

(defn- audit-feedback-text
  "The auditor's comments on an item, one \"Tiivistelmä: …\" line per field
   that has one."
  [tr audit fields]
  (->> fields
       (keep (fn [field]
               (when-let [feedback (not-empty (str/trim (str (get-in audit [field :feedback]))))]
                 (str (tr (case field
                            :summary :ptv/summary
                            :description :ptv/description
                            :user-instruction :ptv/user-instruction))
                      ": " feedback))))
       (str/join "\n")))

(defn- audit-comment-marker
  "Marker shown beside an approval symbol when the auditor approved the text
   but left a comment. Deliberately grey: the colour vocabulary belongs to
   the status symbol next to it, this one only says there is something to
   read — which it carries in its own tooltip."
  [{:keys [tr audit fields size]}]
  (when-let [feedback (not-empty (audit-feedback-text tr audit fields))]
    [:> Tooltip
     {:title (r/as-element
               ;; :color inherit — the tooltip paints its own light text on
               ;; the dark surface, Typography's default would be near-black.
               [:> Typography {:variant "body2" :sx #js {:whiteSpace "pre-line" :color "inherit"}}
                (str (tr :ptv.audit/auditor-feedback) "\n" feedback)])}
     [:> MessageIcon {:sx #js {:color "text.secondary" :width size :height size}}]]))

(defn- status-chip
  "The site's sync status, as one of five chips (ptv-review/sync-statuses).
   `tooltip` replaces the status's own explanation."
  [tr {:keys [sync-status last-sync tooltip]}]
  (let [[color icon default-tooltip] (case sync-status
                                       :ok ["success" Sync (str (tr :ptv/synced-to-ptv) " "
                                                                (some-> last-sync utils/->human-date-time-at-user-tz))]
                                       :ready ["info" Sync (tr :ptv/status-ready)]
                                       :awaiting-choice ["warning" SyncProblem (tr :ptv/status-awaiting-choice)]
                                       :blocked ["error" SyncProblem (tr :ptv/status-blocked)]
                                       ["default" SyncDisabled (tr :ptv/not-yet-synced)])]
    [:> Tooltip {:title (or tooltip default-tooltip)}
     [:> Chip {:label (tr (keyword "ptv" (str "filter-" (name (or sync-status :not-synced)))))
               :size "small"
               :color color
               :variant "outlined"
               :icon (r/as-element [:> icon {:fontSize "small"}])}]]))

(defn- export-halted-alert
  "Where a halted export stopped, why, and how to go on
   (::events/resume-service-locations-creation)."
  [{:keys [tr sports-sites failed skipped-count]}]
  (let [{:keys [lipas-id error]} failed
        site-name (some #(when (= lipas-id (:lipas-id %)) (:name %)) sports-sites)]
    [:> Alert {:severity "error" :sx #js {:mt 2}}
     [:> AlertTitle (tr :ptv.wizard/export-halted)]
     [:> Stack {:spacing 1}
      [:> Typography {:variant "body2"}
       (tr :ptv.wizard/export-failed-site (or site-name lipas-id))]
      (when-let [body (events/ptv-error-body tr error)]
        [:> Typography {:variant "body2"} body])
      (when (pos? skipped-count)
        [:> Typography {:variant "body2"}
         (if (= 1 skipped-count)
           (tr :ptv.wizard/export-skipped-one)
           (tr :ptv.wizard/export-skipped skipped-count))])
      [:> Typography {:variant "body2"}
       (tr :ptv.wizard/export-resume-hint)]
      [:> Button {:size "small"
                  :variant "outlined"
                  :color "inherit"
                  :sx #js {:alignSelf "flex-start" :textTransform "none"}
                  :on-click #(==> [::events/resume-service-locations-creation])}
       (tr :ptv.wizard/export-resume)]]]))

(defn- awaiting-archive
  "Sites removed in Lipas, or no longer PTV's, whose location is still
   published in PTV: an editor without PTV rights can't archive it (D2), a
   PTV manager does it here. The ① notice, once per site."
  [{:keys [tr org-id]}]
  (let [sites (<== [::subs/awaiting-archive org-id])
        locale (tr)]
    (when (seq sites)
      [:> Stack {:spacing 1 :sx #js {:mx 2 :mt 1}}
       [:> Typography {:variant "subtitle2"}
        (tr :ptv/awaiting-archive-title (count sites))]
       (for [{:keys [lipas-id name status event-date]} sites
             :let [syncing? (<== [::subs/syncing-service-location? lipas-id])
                   removed? (#{"incorrect-data" "out-of-service-permanently"} status)]]
         ^{:key lipas-id}
         [:> Alert {:severity "warning" :variant "outlined"
                    :action (r/as-element
                              [:> Stack {:direction "row" :spacing 1}
                               [:> Button {:size "small" :color "inherit" :variant "outlined"
                                           :disabled syncing?
                                           :sx #js {:textTransform "none" :whiteSpace "nowrap"}
                                           :on-click #(==> [::events/archive-awaiting org-id lipas-id])}
                                (tr :ptv/archive-in-ptv-now)]
                               [:> Button {:size "small" :color "inherit"
                                           :sx #js {:textTransform "none" :whiteSpace "nowrap"}
                                           :on-click #(do (==> [::events/close-dialog])
                                                          (==> [:lipas.ui.map.events/show-sports-site lipas-id]))}
                                (tr :ptv/open-site)]])}
          [:> AlertTitle name]
          (if removed?
            (tr :ptv/awaiting-archive-removed
                (get-in status/statuses [status (keyword locale)] status)
                (some-> event-date utils/->human-date))
            (tr :ptv/awaiting-archive-not-candidate))])])))

(defn- sync-all-ready
  "\"Synkronoi kaikki valmiit (n)\", and the progress of the run: the sites
   ready to sync, without opening each (D2: editors' saves wait for a PTV
   manager). Sites still waiting for a choice are skipped."
  [{:keys [tr org-id sites]}]
  (let [schema-errors (<== [::subs/service-location-schema-errors-by-id org-id])
        ;; what the row's own sync button asks too: a site it would refuse
        ;; would stop the run halfway
        ready (filter #(and (:sync-enabled %) (:valid %) (= :ready (:sync-status %))
                            (seq (:service-ids %))
                            (empty? (get schema-errors (:lipas-id %))))
                      sites)
        awaiting (filter #(and (:sync-enabled %) (= :awaiting-choice (:sync-status %))) sites)
        {:keys [in-progress? processed-count total-count failed skipped-count]}
        (<== [::subs/service-location-creation-progress])]
    [:<>
     (when (or (seq ready) in-progress?)
       [:> Stack {:direction "row" :spacing 2 :alignItems "center" :sx #js {:mx 2 :mt 1}}
        [:> Button {:variant "outlined" :size "small" :color "secondary"
                    :disabled (or in-progress? (empty? ready))
                    :sx #js {:textTransform "none"}
                    :startIcon (r/as-element [:> Icon "sync"])
                    :on-click #(==> [::events/sync-all-ready ready awaiting])}
         (tr :ptv/sync-all-ready (count ready))]
        (when in-progress?
          [:> Stack {:direction "row" :spacing 1 :alignItems "center"}
           [:> CircularProgress {:size 16}]
           [:> Typography {:variant "body2"} (str processed-count "/" total-count)]])])
     (when failed
       [:> Stack {:sx #js {:mx 2}}
        [export-halted-alert {:tr tr :sports-sites sites :failed failed
                              :skipped-count skipped-count}]])]))

(defn- open-row-content
  "The content of a table's open row, as wide as the table shows (its
   TableContainer, the container of `cqw`, has containerType inline-size)
   and kept in view (sticky) while the table scrolls sideways. On a phone
   the table is wider than the screen, and the open row would otherwise be
   as wide as all of it, its fields half out of view. The 16px are the
   cell's padding."
  [& children]
  (into [:> Box {:sx #js {:position "sticky" :left "16px" :width "calc(100cqw - 32px)"}}]
        children))

(defn table []
  (r/with-let [expanded-rows (r/atom {})
               search-text (r/atom "")
               status-filter (r/atom :all)
               filter-expanded? (r/atom true)]
    (let [tr (<== [:lipas.ui.subs/translator])
          org-id (<== [::subs/selected-ptv-org-id])
          sites (<== [::subs/sports-sites org-id])
          ;; the sites linked to a service, when the Palvelut tab led here
          service-filter (<== [::subs/sites-service-filter org-id])
          scoped (if service-filter
                   (filter #(contains? (:site-ids service-filter) (:lipas-id %)) sites)
                   sites)
          filtered-sites (filter-sites scoped @search-text @status-filter)
          open-ids (into #{} (keep (fn [[id open?]] (when open? id))) @expanded-rows)
          matching-ids (into #{} (map :lipas-id) filtered-sites)
          shown (shown-sites sites matching-ids open-ids)
          ;; A filter change starts a new lookup: rows open from the previous
          ;; one stay open only if they match the new filter too.
          refilter! (fn [search status]
                      (let [ids (into #{} (map :lipas-id) (filter-sites scoped search status))]
                        (swap! expanded-rows #(into {} (filter (comp ids key)) %))))
          double-linked-ids (<== [::subs/double-linked-lipas-ids org-id])

          ;; Audit status component
          audit-status-cell (fn [site]
                              (let [{:keys [audit-status]} site

                                    audit-data (:audit site)
                                    last-audit (some-> audit-data :timestamp utils/->human-date)
                                    summary-status (get-in audit-data [:summary :status])
                                    desc-status (get-in audit-data [:description :status])

                                    tooltip-text (case audit-status
                                                   :changes-requested
                                                   (str (tr :ptv.audit.status/changes-requested) " "
                                                        (when last-audit (str last-audit " - "))
                                                        (when (= summary-status "changes-requested")
                                                          (str (tr :ptv/summary) " "))
                                                        (when (= desc-status "changes-requested")
                                                          (tr :ptv/description)))

                                                   (:approved :approved-with-feedback)
                                                   (str (tr :ptv.audit.status/approved) " " (or last-audit ""))

                                                   :none
                                                   (tr :ptv.audit/not-audited))]

                                [:> TableCell {:sx #js{:textAlign "center"}}
                                 (when (not= audit-status :none)
                                   [:> Stack {:direction "row" :spacing 0.5
                                              :alignItems "center" :justifyContent "center"}
                                    [:> Tooltip {:title tooltip-text}
                                     (case audit-status
                                       :changes-requested
                                       [:> WarningIcon {:sx #js{:color "warning.main" :fontSize "large" :width "32px" :height "32px"}}]

                                       (:approved :approved-with-feedback)
                                       [:> CheckCircleIcon {:sx #js{:color "success.main" :fontSize "large" :width "32px" :height "32px"}}])]
                                    (when (= audit-status :approved-with-feedback)
                                      [audit-comment-marker {:tr tr
                                                             :audit audit-data
                                                             :fields [:summary :description]
                                                             :size "20px"}])])]))

          headers [{:key :expand :label "" :padding "checkbox"}
                   #_{:key :selected :label (tr :ptv.actions/export)
                      :padding "checkbox"
                      :action-component
                      [:> Switch
                       {:value sync-all-enabled?
                        :on-change #(==> [::events/toggle-sync-all %2])}]}
                   #_{:key :auto-sync :label "Vie automaattisesti"}
                   {:key :event-data :label (tr :ptv/integration) :sx {:textAlign "center"}}
                   {:key :audit :label (tr :ptv.audit/tab-label) :sx {:textAlign "center"}}
                   #_{:key :last-sync :label "Viety viimeksi"}
                   {:key :name :label (tr :general/name)}
                   {:key :type :label (tr :general/type)}
                   ;;{:key :admin :label (tr :lipas.sports-site/admin)}
                   {:key :owner :label (tr :lipas.sports-site/owner)}
                   #_{:key :service :label "Palvelu"}
                   #_{:key :service-channel :label (tr :ptv/service-channel)}
                   #_{:key :service-channel-summary :label "Tiivistelmä"}
                   #_{:key :service-channel-description :label "Kuvaus"}]]

      [:<>
       [awaiting-archive {:tr tr :org-id org-id}]
       [sync-all-ready {:tr tr :org-id org-id :sites sites}]
       ;; changes made in PTV waiting for a choice: say how many, and lead to them
       (let [n (count (filter #(and (:sync-enabled %) (= :awaiting-choice (:sync-status %))) sites))]
         (when (pos? n)
           [:> Alert {:severity "warning" :variant "outlined" :sx #js {:mx 2 :mt 1}
                      :action (r/as-element
                                [:> Button {:size "small" :color "inherit"
                                            :sx #js {:textTransform "none"}
                                            ;; the banner counts every site, so it shows every one
                                            :on-click #(do (==> [::events/clear-sites-service-filter])
                                                           (refilter! @search-text :awaiting-choice)
                                                           (reset! status-filter :awaiting-choice)
                                                           (reset! filter-expanded? true))}
                                 (tr :ptv/show-awaiting-choice)])}
            (tr :ptv/awaiting-choice-banner n)]))
       [filter-bar
        {:tr tr
         :expanded? @filter-expanded?
         :on-toggle-expanded #(swap! filter-expanded? not)
         :search-text @search-text
         :on-search-change #(do (refilter! % @status-filter)
                                (reset! search-text %))
         :chips (site-filter-chips tr)
         :extra-chip (when service-filter
                       [:> Chip {:label (tr :ptv.service/sites-of-service
                                            (or (:name service-filter) (tr :ptv.service/unnamed)))
                                 :size "small"
                                 :color "primary"
                                 :onDelete #(==> [::events/clear-sites-service-filter])}])
         :status-filter @status-filter
         :on-status-change #(do (refilter! @search-text %)
                                (reset! status-filter %))
         :total-count (count scoped)
         :filtered-count (count filtered-sites)}]

       (when (seq shown)
         [:> TableContainer {:component Paper :sx #js {:containerType "inline-size"}}
          [:> Table

          ;; Headers
           [:> TableHead
            [:> TableRow
             (doall
               (for [{:keys [key label action-component padding sx]} headers]
                 [:> TableCell {:key (name key) :padding padding :sx (clj->js sx)}
                  action-component
                  label]))]]

          ;; Body
           [:> TableBody
            (doall
              (for [{:keys [lipas-id] :as site}
                  ;; Sort by audit priority first, then by type
                    (sort-by (fn [site]
                               (let [audit-priority (case (:audit-status site)
                                                      :changes-requested 1 ; Most critical
                                                      :none 3 ; No verdict on the current text
                                                      :approved 4 ; All good
                                                      :approved-with-feedback 4 ; All good, with a remark
                                                      5)] ; Default/unknown
                                 [audit-priority (:type site)]))
                             shown)]

                [:<> {:key lipas-id}

                 [:> TableRow
                  {:sx [#js{}]}

                ;; Expand toggle
                  [:> TableCell
                   [:> IconButton
                    {:style {:zIndex 1}
                     :size "small"
                     :aria-expanded (boolean (get @expanded-rows lipas-id))
                     :on-click (fn []
                                 ;; opening the row refetches its location, so
                                 ;; its review isn't as old as the dialog
                                 (when-not (get @expanded-rows lipas-id)
                                   (==> [::events/refresh-service-channel org-id
                                         (:service-channel-id site)]))
                                 (swap! expanded-rows update lipas-id not))}
                    [:> Icon
                     (if (get @expanded-rows lipas-id false)
                       "keyboard_arrow_up_icon"
                       "keyboard_arrow_down_icon")]]]

                ;; Enable sync
                  #_[:> TableCell
                     [checkboxes/switch
                      {:value (:sync-enabled site)
                       :on-change #(==> [::events/toggle-sync-enabled site %])}]]

                  [:> TableCell {:sx #js{:textAlign "center"}}
                   (if (:sync-enabled site)
                     [status-chip tr site]
                    ;; sync not enabled
                     [:> Tooltip {:title (tr :ptv/filter-sync-disabled)}
                      [:> Chip {:label "PTV"
                                :size "small"
                                :variant "outlined"
                                :sx #js {:opacity 0.35}
                                :icon (r/as-element [:> SyncDisabled {:fontSize "small"}])}]])]

                ;; Audit status
                  (audit-status-cell site)

                ;; Last-sync
                  #_[:> TableCell
                     (or (some-> site :last-sync utils/->human-date-time-at-user-tz)
                         "Ei koskaan")]

                ;; Name
                  [:> TableCell
                   [:> Stack {:direction "row" :spacing 0.5 :align-items "center"}
                    (when (contains? double-linked-ids (:lipas-id site))
                      [:> Tooltip {:title (tr :ptv.double-link/warning-title)}
                       [:> WarningIcon {:color "warning" :sx #js {:fontSize "1rem"}}]])
                    [:span (:name site)]
                    ;; a review choice took PTV's name; the sync renames the site
                    (when (and (:saved-name site) (not= (:name site) (:saved-name site)))
                      [:> Tooltip {:title (tr :ptv.review/renamed-on-sync-tooltip (:saved-name site))}
                       [:> Chip {:label (tr :ptv.review/renamed-on-sync)
                                 :size "small"
                                 :color "primary"
                                 :variant "outlined"}]])
                    ;; Kept on screen only because the row is open (see shown-sites)
                    (when-not (matching-ids lipas-id)
                      [:> Chip {:label (tr :ptv/filter-no-longer-matches)
                                :size "small"
                                :variant "outlined"}])]]

                ;; Type
                  [:> TableCell
                   (:type site)]

                ;; Admin
                ;;[:> TableCell]

                ;; Owner
                  [:> TableCell
                   (:owner site)]

                ;; Service
                  #_[:> TableCell
                     [services-selector]]

                ;; Service channell
                  #_[:> TableCell
                     #_[service-channel-selector]]

                ;; Description
                  #_[:> TableCell]]

               ;; Details row
                 [:> TableRow
                  [:> TableCell
                   {:style {:paddingTop 0 :paddingBottom 0}
                    :colSpan (count headers)}
                   [:> Collapse {:in (get @expanded-rows lipas-id false)
                                 :timeout "auto"
                                 :unmountOnExit true}
                    [open-row-content
                     [form {:tr tr
                            :org-id org-id
                            :site site}]]]]]]))]]])])))

(r/defc set-types [_props]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        locale (tr)
        org-id @(rf/subscribe [::subs/selected-ptv-org-id])
        sports-sites @(rf/subscribe [::subs/sports-sites org-id])
        total-count (count sports-sites)
        available-sub-cats (hooks/use-memo
                             (fn []
                               (into #{} (keep :sub-category-id) sports-sites))
                             [sports-sites])
        options* (hooks/use-memo (fn []
                                   (->> types/sub-categories
                                        vals
                                        (filter #(contains? available-sub-cats (:type-code %)))
                                        (map (fn [{:keys [type-code] :as x}]
                                               {:value type-code
                                                :sort-value (case type-code
                                                              (1 2) (* 100 type-code)
                                                              type-code)
                                                :label (str type-code " " (get-in x [:name locale]))}))
                                        (sort-by :sort-value)))
                                 [locale available-sub-cats])
        value (:sub-cats @(rf/subscribe [::subs/candidates-search]))
        [mode set-mode] (hooks/use-state (if (seq value) "select" "all"))
        on-change (fn [v]
                    (rf/dispatch [::events/set-candidates-search {:sub-cats v}]))
        filtered-count (if (seq value)
                         (count (filter (fn [s] (contains? (set value) (:sub-category-id s))) sports-sites))
                         total-count)
        count-label (fn [n] (str n " " (if (= 1 n)
                                         (tr :ptv/sports-site-count-singular)
                                         (tr :ptv/sports-sites-count))))]
    [:> Stack
     {:sx #js {:gap 2}}

     [:> Typography {:variant "body1"}
      (tr :ptv.wizard/step1-description)]

     [:> RadioGroup
      {:value mode
       :on-change (fn [e]
                    (let [v (.. e -target -value)]
                      (set-mode v)
                      (when (= "all" v)
                        (on-change []))))}

      [:> FormControlLabel
       {:value "all"
        :control (r/as-element [:> Radio])
        :label (str (tr :ptv.wizard/integrate-all) " (" (count-label total-count) ")")}]

      [:> FormControlLabel
       {:value "select"
        :control (r/as-element [:> Radio])
        :label (tr :ptv.wizard/integrate-selected)}]]

     (when (= mode "select")
       [:> Stack {:spacing 1}
        [autocomplete2
         {:options options*
          :multiple true
          :label (tr :ptv.wizard/select-sub-categories)
          :value (to-array value)
          :onChange (fn [_e v]
                      (on-change (vec (map (fn [x]
                                             (if (map? x)
                                               (:value x)
                                               x))
                                           v))))}]
        [:> Typography {:variant "body2" :color "text.secondary"}
         (count-label filtered-count)]])

     [:> Button
      {:onClick (fn [_e]
                  (rf/dispatch [::events/set-step 1]))}
      (tr :actions/next)
      [:> Icon "arrow_forward"]]]))

(r/defc service-preview
  [{:keys [source-id sub-category-id]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        preview @(rf/subscribe [::subs/service-preview source-id sub-category-id])
        row (fn [{:keys [label value tooltip]}]
              [:> Tooltip {:title tooltip}
               [:> TableRow
                [:> TableCell [:> Typography {:variant "caption"} label]]
                [:> TableCell [:> Typography value]]]])
        join (fn [coll] (when (seq coll) (str/join ", " coll)))
        get-desc (fn [type lang] (or (->> preview
                                          :serviceDescriptions
                                          (filter (fn [m] (and (= (:type m) type)
                                                               (= (:language m) lang))))
                                          (map :value)
                                          join)
                                     "-"))
        get-name (fn [lang] (or (->> preview :serviceNames
                                     (filter #(= (:language %) lang))
                                     (map :value)
                                     join)
                                "-"))

        tt-name (tr :ptv.preview/tt-service-name)
        tt-summary (tr :ptv.preview/tt-summary)
        tt-description (tr :ptv.preview/tt-service-description)
        lang-disclaimer (tr :ptv.preview/lang-disclaimer)]

    [:> Stack {:spacing 2}
     [:> Paper {:sx #js{:p 2 :bgcolor mui/gray3}}
      [:> Typography (tr :ptv.preview/service-intro)]]

     [:> Table {:variant "dense"}
      [:> TableHead
       [:> TableRow
        [:> TableCell (tr :ptv.preview/record)]
        [:> TableCell (tr :ptv.preview/value)]]]

      (row {:label (tr :ptv.preview/status)
            :value (:publishingStatus preview)
            :tooltip (tr :ptv.preview/tt-status)})

      (row {:label (tr :ptv.preview/name-fi) :value (get-name "fi") :tooltip tt-name})

      (row {:label (tr :ptv.preview/name-se)
            :value (get-name "sv")
            :tooltip (str tt-name " " lang-disclaimer)})

      (row {:label (tr :ptv.preview/name-en)
            :value (get-name "en")
            :tooltip (str tt-name " " lang-disclaimer)})

      (row {:label (tr :ptv.preview/type)
            :value (:type preview)
            :tooltip (tr :ptv.preview/tt-type)})

      (row {:label (tr :ptv.preview/service-classes)
            :value (join (:serviceClasses preview))
            :tooltip (tr :ptv.preview/tt-service-classes)})

      (row {:label (tr :ptv.preview/target-groups)
            :value (join (:targetGroups preview))
            :tooltip (tr :ptv.preview/tt-target-groups)})

      (row {:label (tr :ptv.preview/ontology-terms)
            :value (join (:ontologyTerms preview))
            :tooltip (tr :ptv.preview/tt-ontology-terms)})

      (row {:label (tr :ptv.preview/funding)
            :value (:fundingType preview)
            :tooltip (tr :ptv.preview/tt-funding)})

      (row {:label (tr :ptv.preview/service-producers)
            :value (join (:organizations (first (:serviceProducers preview))))
            :tooltip (tr :ptv.preview/tt-service-producers)})

      (row {:label (tr :ptv.preview/provision-type)
            :value (:provisionType (first (:serviceProducers preview)))
            :tooltip (tr :ptv.preview/tt-provision-type)})

      (row {:label (tr :ptv.preview/main-organization)
            :value (:mainResponsibleOrganization preview)
            :tooltip (tr :ptv.preview/tt-main-organization)})

      (row {:label (tr :ptv.preview/area-type)
            :value (-> preview :areas first :type)
            :tooltip (tr :ptv.preview/tt-area-type)})

      (row {:label (tr :ptv.preview/area-codes)
            :value (join (-> preview :areas first :areaCodes))
            :tooltip (tr :ptv.preview/tt-area-codes)})

      (row {:label (tr :ptv.preview/summary-fi)
            :value (get-desc "Summary" "fi")
            :tooltip tt-summary})

      (row {:label (tr :ptv.preview/summary-se)
            :value (get-desc "Summary" "sv")
            :tooltip (str tt-summary " " lang-disclaimer)})

      (row {:label (tr :ptv.preview/summary-en)
            :value (get-desc "Summary" "en")
            :tooltip (str tt-summary " " lang-disclaimer)})

      (row {:label (tr :ptv.preview/description-fi)
            :value (get-desc "Description" "fi")
            :tooltip tt-description})

      (row {:label (tr :ptv.preview/description-se)
            :value (get-desc "Description" "sv")
            :tooltip (str tt-description " " lang-disclaimer)})

      (row {:label (tr :ptv.preview/description-en)
            :value (get-desc "Description" "en")
            :tooltip (str tt-description " " lang-disclaimer)})]]))

(defn linked-services
  "Sub-categories the user linked to a service already in PTV in this wizard
  run. Their sites are covered by it, so they drop out of the services to
  create; this is where the link shows and can be undone."
  [{:keys [tr org-id services]}]
  (let [mappings (<== [::subs/service-mappings org-id])
        label-by-id (into {} (map (juxt :service-id :label)) services)]
    (when (seq mappings)
      [:> Stack {:spacing 1}
       [:> Typography {:variant "subtitle2"} (tr :ptv.wizard/linked-services)]
       (doall
         (for [[source-id {:keys [service-id]}] (sort-by key mappings)
               :let [sub-cat (get types/sub-categories (ptv-data/parse-service-source-id source-id))]]
           ^{:key source-id}
           [:> Stack {:direction "row" :spacing 1 :align-items "center"
                      :sx #js {:p 1 :bgcolor "#e3f2fd" :borderRadius 1}}
            [:> Icon {:color "info" :sx #js {:fontSize "1.2rem"}} "link"]
            [:> Typography {:variant "body2" :flex 1}
             (str (get-in sub-cat [:name :fi]) " → " (get label-by-id service-id service-id))]
            [:> Button {:size "small" :color "warning" :sx #js {:textTransform "none"}
                        :on-click #(==> [::events/unlink-candidate-from-existing-service source-id])}
             (tr :ptv.wizard/unlink-service)]]))])))

(defn create-services
  []
  (r/with-let [selected-tab (r/atom :fi)
               link-expanded (r/atom #{})]
    (let [tr (<== [:lipas.ui.subs/translator])
          org-id (<== [::subs/selected-ptv-org-id])
          service-candidates (<== [::subs/service-candidates org-id])
          {:keys [in-progress?
                  halt?
                  processed-percent
                  total-count
                  processed-count]} (<== [::subs/service-descriptions-generation-progress])

          services @(rf/subscribe [::subs/services org-id])
          service-details-tab (<== [::subs/service-details-tab])]

      [:> Stack
       [:> Grid {:container true :spacing 4}
        [:> Grid {:item true :xs 12 :lg 4}
         [:> Stack {:spacing 4}
          [:> Typography {:variant "h6"} (tr :ptv.wizard/generate-descriptions)]
          [:> Typography (tr :ptv.wizard/generate-descriptions-helper2)]
          [:> Typography (tr :ptv.tools.ai/start-helper)]

          ;; Start descriptions generation button
          [:> Button
           {:variant "outlined"
            :disabled in-progress?
            :color "secondary"
            :startIcon (r/as-element [:> Icon "auto_fix_high"])
            :on-click #(==> [::events/generate-all-service-descriptions service-candidates])}
           (tr :ptv.wizard/generate-descriptions-all)]

          ;; Cancel descriptions generation button
          (when in-progress?
            [:> Button
             {:variant "outlined"
              :disabled halt?
              :color "secondary"
              :startIcon (r/as-element [:> Icon "cancel"])
              :on-click #(==> [::events/halt-service-descriptions-generation])}
             (tr :actions/cancel)])

          (when (and halt? in-progress?)
            [:> Typography (tr :ptv.tools.ai/canceling)])

          (when in-progress?
            [:> Stack {:direction "row" :spacing 2 :align-items "center"}
             [:> CircularProgress {:variant "indeterminate" :value processed-percent}]
             [:> Typography (str processed-count "/" total-count)]])

          [:> Typography (tr :ptv.wizard/generate-descriptions-helper1)]

          ;; Sync to PTV button
          (let [{:keys [in-progress?
                        processed-count
                        total-count
                        processed-percent
                        halt?]}
                (<== [::subs/services-creation-progress])]
            [:<>
             [:> Button
              {:variant "outlined"
               :disabled (or in-progress? (some false? (map :valid service-candidates)))
               :color "primary"
               :startIcon (r/as-element [:> Icon "ios_share"])
               :on-click #(==> [::events/create-all-ptv-services service-candidates])}
              (tr :ptv.wizard/export-services-to-ptv)]

             ;; TODO: Cancel?

             (when in-progress?
               [:> Stack {:direction "row" :spacing 2 :align-items "center"}
                [:> CircularProgress {:variant "indeterminate" :value processed-percent}]
                [:> Typography (str processed-count "/" total-count)]])

             (when halt?
               [:> Alert {:severity "error" :sx #js{:mt 2}}
                [:> AlertTitle (tr :notifications/save-failed)]
                (tr :ptv.wizard/export-error-try-again)])])]]

        ;; Results panel
        [:> Grid {:item true :xs 12 :lg 8}
         [:> Stack {:spacing 4}

          [:> Typography {:variant "h6"}
           (tr :ptv.wizard/services-to-add)]

          [linked-services {:tr tr :org-id org-id :services services}]

          (when (empty? service-candidates)
            (let [sports-sites-count (<== [::subs/sports-sites-count org-id])]
              [:<>
               (if (pos? sports-sites-count)
                 [:> Alert {:severity "success" :sx #js{:mb 2}}
                  (tr :ptv.wizard/all-services-exist)]
                 [:> Alert {:severity "info" :sx #js{:mb 2}}
                  (tr :ptv.wizard/no-sports-sites-for-types)])
               [:> Button
                {:onClick (fn [_e]
                            (rf/dispatch [::events/set-step 2]))}
                (tr :actions/next)
                [:> Icon "arrow_forward"]]]))

          [:div
           (doall
             (for [{:keys [source-id valid sub-category sub-category-id languages linked?] :as m} service-candidates]

               ^{:key sub-category-id}
               [layouts/expansion-panel
                {:label (if linked?
                          (tr :ptv.wizard/linked-label sub-category)
                          sub-category)
                 :label-icon (cond
                               linked? [:> Icon {:color "info"} "link"]
                               valid [:> Icon {:color "success"} "done"]
                               :else [:> Icon {:color "disabled"} "done"])}

                [:> Stack {:spacing 2}
                 [:> Tabs {:value service-details-tab
                           :indicatorColor "secondary"
                           :on-change #(==> [::events/select-service-details-tab %2])}
                  [:> Tab {:value "descriptions" :label (tr :ptv.wizard/enter-descriptions)}]
                  [:> Tab {:value "preview" :label (tr :ptv.wizard/preview)}]]

                 ;; Enter descriptions form
                 (when (= "descriptions" service-details-tab)
                   [:> Stack {:spacing 2}

                    ;; Use a service already in PTV instead of creating one:
                    ;; its sites are linked to it and nothing is written to
                    ;; the service (its texts can be updated later on the
                    ;; Palvelut tab). The sub-category then moves to the
                    ;; linked-services list above.
                    (when (seq services)
                      (let [expanded? (contains? @link-expanded source-id)]
                        [:> Stack {:spacing 1}
                         [:> Button
                          {:size "small"
                           :variant "text"
                           :sx #js {:alignSelf "flex-start" :textTransform "none"}
                           :startIcon (r/as-element
                                        [:> Icon {:sx #js {:fontSize "1rem"}}
                                         (if expanded? "expand_less" "expand_more")])
                           :on-click #(swap! link-expanded (if expanded? disj conj) source-id)}
                          (tr :ptv.wizard/link-to-existing-service)]
                         (when expanded?
                           [:<>
                            [:> Typography {:variant "body2" :color "text.secondary"}
                             (tr :ptv.wizard/link-existing-helper)]
                            [controls/services-selector
                             {:options services
                              :multiple false
                              :value nil
                              :on-change #(when %
                                            (==> [::events/link-candidate-to-existing-service source-id %]))
                              :value-fn :service-id
                              :label (tr :ptv.wizard/select-existing-service)}]])]))

                    ;; AI generate + translate buttons
                    (let [generating? (<== [::subs/generating-service-descriptions? source-id])
                          languages* (set languages)
                          from-lang @selected-tab
                          other-langs (disj (set (map keyword languages)) from-lang)
                          has-text? (and (seq (get-in m [:summary from-lang]))
                                         (seq (get-in m [:description from-lang])))]
                      [:> Stack {:direction "row" :spacing 1 :flex-wrap "wrap" :align-items "flex-start"}
                       [:> Button
                        {:variant "outlined" :size "small" :disabled generating?
                         :sx #js {:textTransform "none"}
                         :startIcon (r/as-element
                                      (if generating?
                                        [:> CircularProgress {:size 16 :color "inherit"}]
                                        [:> Icon "auto_fix_high"]))
                         :on-click #(==> [::events/generate-service-descriptions org-id source-id nil [] []])}
                        (tr :ptv.actions/generate-with-ai)]
                       (when (> (count languages*) 1)
                         [:> Tooltip {:title (tr :ptv.wizard/translate-to-other-langs-tooltip)}
                          [:span
                           [:> Button
                            {:size "small" :variant "outlined"
                             :disabled (or generating? (not has-text?))
                             :startIcon (r/as-element
                                          (if generating?
                                            [:> CircularProgress {:size 16 :color "inherit"}]
                                            [:> Icon "translate"]))
                             :sx #js {:textTransform "none"}
                             :on-click #(==> [::events/translate-service-candidate
                                              source-id from-lang other-langs])}
                            (str (tr :ptv.wizard/translate-to-other-langs) " ("
                                 (str/join ", " (map (comp str/upper-case name) (sort other-langs)))
                                 ")")]]])])

                    (let [languages (set languages)]
                      [:> Tabs
                       {:value @selected-tab
                        :on-change #(reset! selected-tab (keyword %2))}
                       (when (contains? languages "fi")
                         [:> Tab {:value "fi" :label "FI"}])
                       (when (contains? languages "se")
                         [:> Tab {:value "se" :label "SE"}])
                       (when (contains? languages "en")
                         [:> Tab {:value "en" :label "EN"}])])

                    ;; Summary
                    (let [summary-val (or (get-in m [:summary @selected-tab]) "")]
                      [text-fields/text-field
                       {:multiline true
                        :variant "outlined"
                        :on-change #(==> [::events/set-service-candidate-summary source-id @selected-tab %])
                        :label (tr :ptv/summary)
                        :value summary-val
                        :inputProps #js {:maxLength ptv-data/max-summary-length}
                        :helperText (controls/text-length-hint tr summary-val ptv-data/max-summary-length)
                        :error (controls/text-length-error? summary-val ptv-data/max-summary-length)}])

                    ;; Description
                    [service-writing-guidance
                     {:tr tr :sub-category-id sub-category-id :field :description}]
                    (let [desc-val (or (get-in m [:description @selected-tab]) "")]
                      [text-fields/text-field
                       {:variant "outlined"
                        :rows 7
                        :multiline true
                        :on-change #(==> [::events/set-service-candidate-description source-id @selected-tab %])
                        :label (tr :ptv/description)
                        :value desc-val
                        :inputProps #js {:maxLength ptv-data/max-description-length}
                        :helperText (controls/text-length-hint tr desc-val ptv-data/max-description-length)
                        :error (controls/text-length-error? desc-val ptv-data/max-description-length)}])

                    ;; User instruction / Toimintaohje
                    [service-writing-guidance
                     {:tr tr :sub-category-id sub-category-id :field :user-instruction}]
                    (let [ui-val (or (get-in m [:user-instruction @selected-tab]) "")]
                      [text-fields/text-field
                       {:variant "outlined"
                        :rows 5
                        :multiline true
                        :on-change #(==> [::events/set-service-candidate-user-instruction source-id @selected-tab %])
                        :label (tr :ptv/user-instruction)
                        :value ui-val
                        :inputProps #js {:maxLength ptv-data/max-user-instruction-length}
                        :helperText (controls/text-length-hint tr ui-val ptv-data/max-user-instruction-length)
                        :error (controls/text-length-error? ui-val ptv-data/max-user-instruction-length)}])])

                 (when (= "preview" service-details-tab)
                   [service-preview
                    {:source-id source-id
                     :sub-category-id sub-category-id}])]]))]]]]])))

(r/defc service-location-details
  [{:keys [org-id tr site lipas-id sync-enabled name-conflict service-ids selected-tab set-selected-tab service-channel-ids valid]}]
  (let [services @(rf/subscribe [::subs/services org-id])
        org-languages (<== [::subs/org-languages org-id])
        [selected-tab2 set-selected-tab2] (hooks/use-state "descriptions")
        [link-expanded? set-link-expanded] (hooks/use-state false)]
    [:> AccordionDetails
     {}
     (r/as-element
       [:> Stack {:spacing 2}

        [:> Tabs {:value selected-tab2
                  :indicatorColor "secondary"
                  :on-change #(set-selected-tab2 %2)}
         [:> Tab {:value "descriptions" :label (tr :ptv.wizard/enter-descriptions)}]
         [:> Tab {:value "preview" :label (tr :ptv.wizard/preview)}]]

        (when (= selected-tab2 "preview")
          [ptv-components/service-location-preview
           {:org-id org-id
            :lipas-id lipas-id}])

        (when (= selected-tab2 "descriptions")
          [:> Stack {:spacing 2}

          ;; Service channel selector

           [:span (when name-conflict {:style
                                       {:border "1px solid rgb(237, 108, 2)"
                                        :padding "1em"}})

            ;; the conflict and what to do about it are told in the review's
            ;; ① notices below
            ;; Linked state OR collapsible "is it already in PTV?" expander.
            ;; During a name-conflict we keep the in-place selector visible so the
            ;; user can still pick an alternative existing channel.
            (cond
              (seq service-channel-ids)
              [:> Stack {:direction "row" :spacing 1 :align-items "center"
                         :sx #js {:p 1 :bgcolor "#e3f2fd" :borderRadius 1}}
               [:> Icon {:color "info" :sx #js {:fontSize "1.2rem"}} "link"]
               [:> Typography {:variant "body2" :flex 1}
                (str (tr :ptv.wizard/linked-to-service-channel) ": "
                     (:service-channel-name site))]
               [:> Button
                {:size "small" :color "warning"
                 :on-click #(==> [::events/select-service-channels site []])}
                (tr :ptv.wizard/unlink-service-channel)]]

              name-conflict
              [service-channel-selector
               {:org-id org-id
                :value service-channel-ids
                :value-fn :service-channel-id
                :on-change #(==> [::events/select-service-channels site %])
                :label (tr :ptv/service-channel)}]

              :else
              [:> Stack {:spacing 1}
               [:> Button
                {:size "small"
                 :variant "text"
                 :sx #js {:alignSelf "flex-start" :textTransform "none"}
                 :startIcon (r/as-element
                              [:> Icon {:sx #js {:fontSize "1rem"}}
                               (if link-expanded? "expand_less" "expand_more")])
                 :on-click #(set-link-expanded (not link-expanded?))}
                (tr :ptv.wizard/link-to-existing-service-channel)]
               (when link-expanded?
                 [service-channel-selector
                  {:org-id org-id
                   :value service-channel-ids
                   :value-fn :service-channel-id
                   :on-change (fn [v]
                                (==> [::events/select-service-channels site v])
                                (set-link-expanded false))
                   :label (tr :ptv.wizard/select-existing-service-channel)}])])]

           ;; What differs from the linked location (on a first link: the
           ;; location made in PTV) and the choice of which version stays;
           ;; the export carries the choices
           [site-review {:tr tr
                         :site site
                         :footer (tr :ptv.review/wizard-footer)}]

           ;; Services selector: after the review, which may change it
           [controls/services-selector
            {:options services
             :value service-ids
             :value-fn :service-id
             :on-change (fn [v]
                          (rf/dispatch [::events/select-services site v]))
             :label (tr :ptv/services)}]

;; AI generate + translate buttons
           (let [generating? @(rf/subscribe [::subs/generating-descriptions?])
                 from-lang (keyword selected-tab)
                 other-langs (disj (set (map keyword org-languages)) from-lang)
                 has-text? (and (seq (get-in site [:summary from-lang]))
                                (seq (get-in site [:description from-lang])))]
             [:> Stack {:direction "row" :spacing 1 :flex-wrap "wrap" :align-items "flex-start"}
              [:> Button
               {:variant "outlined" :size "small" :disabled generating?
                :sx #js {:textTransform "none"}
                :startIcon (r/as-element
                             (if generating?
                               [:> CircularProgress {:size 16 :color "inherit"}]
                               [:> Icon "auto_fix_high"]))
                :on-click #(==> [::events/generate-descriptions lipas-id [] []])}
               (tr :ptv.actions/generate-with-ai)]
              (when (> (count org-languages) 1)
                [:> Tooltip {:title (tr :ptv.wizard/translate-to-other-langs-tooltip)}
                 [:span
                  [:> Button
                   {:size "small" :variant "outlined"
                    :disabled (or generating? (not has-text?))
                    :startIcon (r/as-element
                                 (if generating?
                                   [:> CircularProgress {:size 16 :color "inherit"}]
                                   [:> Icon "translate"]))
                    :sx #js {:textTransform "none"}
                    :on-click #(==> [::events/translate-site-descriptions lipas-id from-lang other-langs])}
                   (str (tr :ptv.wizard/translate-to-other-langs) " ("
                        (str/join ", " (map (comp str/upper-case name) (sort other-langs))) ")")]]])])

           [:> Stack {:direction "row" :align-items "center"
                      :flex-wrap "wrap" :spacing 1
                      :sx #js {:rowGap 1 :minWidth 0}}
            [lang-selector
             {:value selected-tab
              :on-change (fn [_e v] (set-selected-tab v))
              :opts org-languages}]]

          ;; Summary
           (let [summary-val (or (get-in site [:summary selected-tab]) "")]
             [text-fields/text-field
              {:multiline true
               :variant "outlined"
               :on-change #(==> [::events/set-summary site selected-tab %])
               :label (tr :ptv/summary)
               :value summary-val
               :inputProps #js {:maxLength ptv-data/max-summary-length}
               :helperText (controls/text-length-hint tr summary-val ptv-data/max-summary-length)
               :error (controls/text-length-error? summary-val ptv-data/max-summary-length)}])

          ;; Description
           (let [desc-val (or (get-in site [:description selected-tab]) "")]
             [text-fields/text-field
              {:variant "outlined"
               :rows 7
               :multiline true
               :on-change #(==> [::events/set-description site selected-tab %])
               :label (tr :ptv/description)
               :value desc-val
               :inputProps #js {:maxLength ptv-data/max-description-length}
               :helperText (controls/text-length-hint tr desc-val ptv-data/max-description-length)
               :error (controls/text-length-error? desc-val ptv-data/max-description-length)}])

          ;; Disclaimer and enable switch. Sync can't be switched on for an
          ;; invalid site, but can always be switched off (e.g. to drop a site
          ;; that blocks the batch export).
           [checkboxes/switch
            {:label (if (:last-sync site)
                      (tr :ptv.actions/update-disclaimer)
                      (tr :ptv.actions/export-disclaimer))
             :value sync-enabled
             :disabled (and (not sync-enabled) (not valid))
             :on-change #(==> [::events/toggle-sync-enabled site %])}]])])]))

(r/defc service-location
  [{:keys [site sync-enabled name-conflict valid schema-errors just-synced?]
    :as props}]
  (let [tr (<== [:lipas.ui.subs/translator])
        sync-status (:sync-status site)
        open-review (ptv-review/open-review-rows (-> site :review :rows)
                                                 (<== [::subs/review-choices (:lipas-id site)]))
        [expanded set-expanded] (hooks/use-state false)]

    ;; Auto-collapse when site gets synced during batch
    (hooks/use-effect
      (fn [] (when just-synced? (set-expanded false)))
      [just-synced?])

    [:> Accordion
     {:expanded expanded
      :onChange (fn [_e v] (set-expanded v))
      :disableGutters true
      :square true
      ;; Much faster this way, only render the accordion content for open sites
      :slotProps #js {:transition #js {:unmountOnExit true}}
      :sx #js {:mb 2}}
     [:> AccordionSummary
      {:expandIcon (r/as-element [:> Icon "expand_more"])}
      [:> Stack {:direction "row" :spacing 1 :align-items "center" :flex 1 :sx #js {:mr 1}}
       ;; Validity icon
       [:> Typography
        {:sx #js {:mr 0.5}}
        (cond
          (seq schema-errors) [:> Tooltip {:title (str/join " " (map tr schema-errors))}
                               [:> Icon {:color "error"} "error"]]
          name-conflict [:> Icon {:color "warning"} "warning"]
          valid [:> Icon {:color "success"} "done"]
          :else [:> Icon {:color "disabled"} "done"])]
       ;; Name
       [:> Typography
        {:sx #js {:color "inherit"
                  :variant "button"
                  :flex 1}}
        (:name site)]
       ;; Differences from PTV waiting for a choice
       (when (seq open-review)
         [:> Tooltip {:title (tr :ptv.review/choose-before-sync)}
          [:> Chip {:label (tr :ptv.review/count-open (count open-review))
                    :size "small"
                    :color "warning"
                    :variant "outlined"}]])
       ;; PTV sync status
       (when (or sync-enabled (not= :not-synced sync-status))
         [status-chip tr site])]]

     [service-location-details props]]))

(r/defc integrate-service-locations
  [_props]
  (let [tr (<== [:lipas.ui.subs/translator])
        org-id (<== [::subs/selected-ptv-org-id])
        sports-sites (<== [::subs/sports-sites-wizard org-id])
        sports-sites-count (count sports-sites)
        sports-sites-count-sync (count (filter :sync-enabled sports-sites))
        ;; Every site selected for export must pass validation, otherwise the
        ;; export is blocked and the offending sites are listed.
        invalid-to-export (filter #(and (:sync-enabled %) (not (:valid %))) sports-sites)
        ;; ...and every change made in PTV in them must have a choice
        all-choices (<== [::subs/all-review-choices])
        editable (<== [::subs/editable-site-ids org-id])
        open-review (for [{:keys [lipas-id review sync-enabled] :as site} sports-sites
                          :when sync-enabled
                          :let [open (ptv-review/open-review-rows (:rows review)
                                                                  (get all-choices lipas-id))]
                          :when (seq open)]
                      {:lipas-id lipas-id :name (:name site) :rows open})

        [selected-tab set-selected-tab] (hooks/use-state :fi)

        ;; TODO: Rename this so service-location-generation progress can also be
        ;; added to this level
        {:keys [in-progress?
                processed-count
                total-count
                processed-percent
                halt?]}
        (<== [::subs/batch-descriptions-generation-progress])]

    [:> Stack
     #_[:> Typography
        {:variant "h4"}
        (str (tr :ptv.wizard/integrate-service-locations))]

     [:> Grid {:container true :spacing 4}
      [:> Grid {:item true :xs 12 :lg 4}

       ;; Settings
       [:> Stack {:spacing 4}
        [:> Typography {:variant "h6"} (tr :ptv.wizard/generate-descriptions)]
        [:> Typography (tr :ptv.wizard/generate-descriptions-helper2)]
        [:> Typography (tr :ptv.tools.ai/start-helper)]

        ;; Start button
        [:> Button
         {:variant "outlined"
          :disabled in-progress?
          :color "secondary"
          :startIcon (r/as-element [:> Icon "auto_fix_high"])
          :on-click #(==> [::events/generate-all-descriptions sports-sites])}
         (tr :ptv.wizard/generate-descriptions-all)]

        ;; Cancel button
        (when in-progress?
          [:> Button
           {:variant "outlined"
            :disabled halt?
            :color "secondary"
            :startIcon (r/as-element [:> Icon "cancel"])
            :on-click #(==> [::events/halt-descriptions-generation])}
           (tr :actions/cancel)])

        (when (and halt? in-progress?)
          [:> Typography (tr :ptv.tools.ai/canceling)])

        (when in-progress?
          [:> Stack {:direction "row" :spacing 2 :align-items "center"}
           [:> CircularProgress {:variant "indeterminate" :value processed-percent}]
           [:> Typography (str processed-count "/" total-count)]])

        [:> Typography (tr :ptv.wizard/generate-descriptions-helper3)]

        [:> Typography (tr :ptv.wizard/unselect-helper)]

        [:> Typography {:variant "body2" :sx #js{:mb 0 :mt 0}}
         (tr :ptv.wizard/selected-count sports-sites-count-sync sports-sites-count)]

        (let [{:keys [in-progress?
                      processed-count
                      total-count
                      processed-percent
                      halt?
                      failed
                      skipped-count]}
              (<== [::subs/service-location-creation-progress])]

          [:<>
           ;; Export to PTV button
           [:> Button
            {:variant "outlined"
             :disabled (or in-progress?
                           (zero? sports-sites-count-sync)
                           (seq invalid-to-export)
                           (seq open-review))
             :color "primary"
             :startIcon (r/as-element [:> Icon "ios_share"])
             :on-click #(==> [::events/create-all-ptv-service-locations sports-sites])}
            (tr :ptv.wizard/export-service-locations-to-ptv)]

           (when (seq open-review)
             [:> Alert {:severity "warning" :sx #js{:mt 2}}
              [:> AlertTitle (tr :ptv.review/export-blocked)]
              [:ul {:style {:margin 0 :padding-left "1.2em"}}
               (for [{:keys [lipas-id name rows]} open-review]
                 ^{:key lipas-id}
                 [:li (str name ": " (str/join ", " (map #(review-data/field-label tr %) rows)))])]
              [:> Stack {:direction "row" :spacing 1 :flexWrap "wrap" :useFlexGap true :sx #js {:mt 1}}
               ;; the site's own data only where the user may edit the site,
               ;; and only rows with one value to take
               (let [takeable (keep (fn [{:keys [lipas-id rows] :as site}]
                                      (let [rights {:ptv-manager? true
                                                    :site-editor? (contains? editable lipas-id)}]
                                        (when-let [rows (seq (filter #(and (review-data/takeable? rights %)
                                                                           (ptv-review/takeable-in-bulk? %))
                                                                     rows))]
                                          (assoc site :rows rows))))
                                    open-review)]
                 (when (seq takeable)
                   [:> Button {:size "small" :variant "outlined" :color "inherit"
                               :sx #js {:textTransform "none"}
                               :on-click #(==> [::events/review-take-sites takeable :ptv])}
                    (tr :ptv.review/take-all-ptv)]))
               [:> Button {:size "small" :variant "outlined" :color "inherit"
                           :sx #js {:textTransform "none"}
                           :on-click #(==> [::events/review-take-sites open-review :lipas])}
                (tr :ptv.review/keep-all-lipas)]]])

           (when (seq invalid-to-export)
             [:> Alert {:severity "warning" :sx #js{:mt 2}}
              [:> AlertTitle (tr :ptv.wizard/export-blocked-invalid)]
              [:ul {:style {:margin 0 :padding-left "1.2em"}}
               (for [{:keys [lipas-id name schema-errors summary description]} invalid-to-export]
                 (let [reasons (cond-> []
                                 (not (some-> summary :fi count (> 5))) (conj (tr :ptv/hint-add-summary))
                                 (not (some-> description :fi count (> 5))) (conj (tr :ptv/hint-add-description))
                                 :always (into (map tr schema-errors)))]
                   ^{:key lipas-id}
                   [:li (str name ": " (str/join "; " reasons))]))]])

           (when in-progress?
             [:> Stack {:direction "row" :spacing 2 :align-items "center"}
              [:> CircularProgress {:variant "indeterminate" :value processed-percent}]
              [:> Typography (str processed-count "/" total-count)]])

           (when halt?
             [export-halted-alert {:tr tr
                                   :sports-sites sports-sites
                                   :failed failed
                                   :skipped-count skipped-count}])])]]

      ;; Results

      [:> Grid {:item true :xs 12 :lg 8}
       [:> Stack {:spacing 4}

        [:> Stack {:spacing 1 :sx #js{:pb 2}}

         [:> Typography {:variant "h6"}
          (tr :ptv/sports-sites)]

         [:> Typography {:variant "body2"}
          (tr :ptv.wizard/step3-instruction)]

         [:> Typography {:variant "body2"}
          (tr :ptv.wizard/selected-count sports-sites-count-sync sports-sites-count)]]

        ;; Batch sync completion panel
        (let [{sync-in-progress? :in-progress?
               sync-processed :processed-lipas-ids
               sync-processed-count :processed-count
               sync-total :total-count
               sync-halt? :halt?}
              (<== [::subs/service-location-creation-progress])
              batch-done? (and (not sync-in-progress?)
                               (pos? (or sync-processed-count 0)))]
          [:<>
           (when batch-done?
             [:> Alert {:severity (if sync-halt? "warning" "success")
                        :sx #js {:mb 2}}
              [:> AlertTitle
               (if sync-halt?
                 (tr :ptv.wizard/export-halted)
                 (tr :ptv.wizard/export-complete))]
              ;; a halted export says where and why beside its button
              [:> Typography {:variant "body2"}
               (tr :ptv.wizard/export-result sync-processed-count sync-total)]
              [:> Stack {:direction "row" :spacing 1 :sx #js {:mt 2} :flex-wrap "wrap"}
               [:> Button
                {:size "small" :variant "outlined"
                 :sx #js {:textTransform "none"}
                 :on-click #(==> [::events/reset-wizard])}
                (tr :ptv.wizard/start-new-export)]
               [:> Button
                {:size "small" :variant "outlined"
                 :sx #js {:textTransform "none"}
                 :on-click #(==> [::events/select-tab "sports-sites"])}
                (tr :ptv.wizard/go-to-sports-sites-tab)]
               [:> Button
                {:size "small" :variant "outlined"
                 :sx #js {:textTransform "none"}
                 :on-click #(==> [::events/select-tab "services"])}
                (tr :ptv.wizard/go-to-services-tab)]]])

           [:> Stack
            (for [{:keys [lipas-id valid schema-errors name-conflict sync-enabled service-ids service-channel-ids] :as site} sports-sites]
              ^{:key lipas-id}
              [service-location
               {:tr tr
                :site site
                :org-id org-id
                :lipas-id lipas-id
                :name-conflict name-conflict
                :sync-enabled sync-enabled
                :valid valid
                :schema-errors schema-errors
                :service-ids service-ids
                :selected-tab selected-tab
                :set-selected-tab set-selected-tab
                :service-channel-ids service-channel-ids
                :just-synced? (contains? sync-processed lipas-id)}])]])]]]]))

(def ^:private list-threshold 5)

(defn- truncated-list
  "A titled group of `items` (hiccup), the first few and a button for the
   rest."
  [{:keys [tr title items]}]
  (r/with-let [show-all? (r/atom false)]
    (let [total (count items)]
      [:> Stack {:spacing 0.25 :sx #js {:pl 1.25 :borderLeft "2px solid" :borderColor "divider"}}
       [:> Typography {:variant "caption" :fontWeight 700} title]
       (doall (map-indexed (fn [i item] ^{:key i} [:div item])
                           (if @show-all? items (take list-threshold items))))
       (when (> total list-threshold)
         [:> Button {:size "small" :variant "text"
                     :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                     :on-click #(swap! show-all? not)}
          (if @show-all?
            (tr :actions/show-less)
            (str (tr :actions/show-all) " (" total ")"))])])))

(defn- channel-type-label
  [tr type]
  (tr (case type
        "EChannel" :ptv.service/channel-type-echannel
        "WebPage" :ptv.service/channel-type-webpage
        "Phone" :ptv.service/channel-type-phone
        "PrintableForm" :ptv.service/channel-type-printable-form
        "ServiceLocation" :ptv.service/channel-type-service-location
        :ptv.service/channel-type-unknown)))

(defn- service-locations-list
  "The service's service locations in PTV, those of Lipas sites apart from
   those added in PTV, and its other channels."
  [{:keys [tr service ptv-base]}]
  (let [{:keys [locations other-channels]} service
        {from-lipas true from-ptv false} (group-by (comp boolean seq :lipas-ids) locations)
        location-link (fn [{:keys [id name]}]
                        [:> Link {:href (str ptv-base "/channels/serviceLocation/" id)
                                  :target "_blank"
                                  :variant "body2"}
                         name])]
    [:> Stack {:spacing 1}
     [:> Typography {:variant "caption" :color "text.secondary"}
      (tr :ptv.service/locations-title (count locations))]
     (when (empty? locations)
       [:> Alert {:severity "warning" :variant "outlined"}
        (tr :ptv.service/no-locations-info)])
     (when (seq from-lipas)
       [truncated-list {:tr tr
                        :title (tr :ptv.service/locations-from-lipas (count from-lipas))
                        :items (map location-link from-lipas)}])
     (when (seq from-ptv)
       [truncated-list {:tr tr
                        :title (tr :ptv.service/locations-from-ptv (count from-ptv))
                        :items (map location-link from-ptv)}])
     (when (seq other-channels)
       [truncated-list {:tr tr
                        :title (tr :ptv.service/other-channels-title (count other-channels))
                        :items (for [{:keys [name type]} other-channels]
                                 [:> Typography {:variant "body2" :fontWeight 400}
                                  name
                                  [:> Typography {:component "span" :variant "caption" :color "text.secondary"}
                                   (str " · " (channel-type-label tr type))]])}])]))

(defn- linked-sites-list
  "The Lipas sites saved as linked to the service."
  [{:keys [tr service]}]
  (let [sites (:linked-sites service)]
    [:> Stack {:spacing 1}
     [:> Typography {:variant "caption" :color "text.secondary"}
      (tr :ptv.service/linked-sites-title (count sites))]
     (if (seq sites)
       [:<>
        [truncated-list {:tr tr
                         :title (tr :ptv/sports-sites)
                         :items (for [{:keys [name]} sites]
                                  [:> Typography {:variant "body2" :fontWeight 400} name])}]
        [:> Button {:size "small" :variant "text"
                    :sx #js {:textTransform "none" :alignSelf "flex-start"}
                    :endIcon (r/as-element [:> Icon "arrow_forward"])
                    :on-click #(==> [::events/show-service-sites (:service-id service)])}
         (tr :ptv.service/show-sites)]]
       [:> Typography {:variant "body2" :fontWeight 400 :color "text.secondary"}
        (tr :ptv.service/no-linked-sites)])]))

(def ^:private text-field->tr-key
  {:name :ptv/service-name
   :summary :ptv/summary
   :description :ptv/description
   :user-instruction :ptv/user-instruction})

(defn service-text-errors-alert
  "The service texts PTV would reject (lipas.data.ptv/service-text-errors)."
  [{:keys [tr errors native?]}]
  [:> Alert {:severity "warning" :variant "outlined"}
   [:> AlertTitle (tr :ptv.service/sync-blocked-title)]
   [:ul {:style {:margin 0 :padding-left "1.2em"}}
    ;; By position: two of PTV's own entries can give equal errors (e.g. two
    ;; short requirements in one language)
    (for [[i {:keys [field locale error max min ptv-type language]}] (map-indexed vector errors)
          :let [field-label (some-> field text-field->tr-key tr)
                lang (some-> locale name str/upper-case)]]
      ^{:key i}
      [:li [:> Typography {:variant "body2"}
            (case error
              :missing (tr :ptv.service/text-missing field-label lang)
              :too-short (tr :ptv.service/text-too-short field-label lang min)
              :too-short-in-ptv (tr :ptv.service/ptv-text-too-short
                                    (events/ptv-text-type-label tr ptv-type)
                                    (events/ptv-language-label language)
                                    min)
              :too-long (tr :ptv.service/text-too-long field-label lang max))]])]
   (when (and native? (some #(= :missing (:error %)) errors))
     [:> Typography {:variant "body2" :sx #js {:mt 1}}
      (tr :ptv.service/fix-or-remove-version)])])

(defn first-save-dialog
  "Confirmation before LIPAS first writes the texts of a service made in PTV."
  [{:keys [tr service open? on-close on-confirm]}]
  (let [site-count (count (:site-ids service))]
    [:> Dialog {:open (boolean open?) :max-width "sm" :full-width true :on-close on-close}
     [:> DialogTitle (tr :ptv.service/first-save-title)]
     [:> DialogContent
      [:> Stack {:spacing 1.5}
       [:> Typography {:variant "body2"}
        (tr :ptv.service/first-save-reach (count (:service-channels service)) site-count)]
       [:ul {:style {:margin 0 :padding-left "1.5em"}}
        (for [k [:ptv.service/first-save-texts
                 :ptv.service/first-save-kept
                 :ptv.service/first-save-audit]]
          ^{:key k} [:li [:> Typography {:variant "body2"} (tr k)]])]]]
     [:> DialogActions
      [:> Button {:on-click on-close :sx #js {:textTransform "none"}} (tr :actions/cancel)]
      [:> Button {:variant "contained" :color "secondary" :sx #js {:textTransform "none"}
                  :on-click on-confirm}
       (tr :ptv.service/first-save-confirm)]]]))

(defn service-panel-left
  [{:keys [tr org-id service data ptv-base has-local-edits? org-languages selected-tab edited-name
           edit-key native? other-system? text-errors versions-to-drop edit-languages name-data
           open-rows]}]
  (r/with-let [confirm-first-save? (r/atom false)]
    (let [service-id (:service-id service)
          source-id (:source-id service)
          display-name (or edited-name (:label service))
          has-lipas-data? (= :lipas (:management service))
          generating? (<== [::subs/generating-service-descriptions? edit-key])
          write! #(rf/dispatch [::events/create-ptv-service org-id edit-key data [] []])
          ;; through the review: an edit over a change made in PTV since
          ;; Lipas's last write is asked about first
          sync! #(if (seq open-rows)
                   (==> [:lipas.ui.events/confirm-with
                         (review-data/overwrite-confirmation
                           tr {:open open-rows
                               :on-overwrite write!
                               :on-review (fn [] (rf/dispatch [::events/show-review edit-key]))})])
                   (write!))]
      [:> Stack {:spacing 2}

     ;; 1. PTV service name. A native service has one per language: the
     ;; pencil edits the name of the selected language tab.
       (cond
         other-system?
         [:> Stack {:spacing 0.5}
          [:> Typography {:variant "caption" :color "text.secondary"}
           (tr :ptv/service-name)]
          [:> Link {:href (str ptv-base "/service/" service-id)
                    :target "_blank"
                    :variant "body2"}
           display-name]]

         native?
         (let [locale @selected-tab]
           ^{:key locale}
           [service-name-field {:tr tr :edit-key edit-key :locale locale
                                :label (str (tr :ptv/service-name)
                                            " (" (str/upper-case (name locale)) ")")
                                :service-id service-id :ptv-base ptv-base
                                :display-name (get name-data locale)}])

         :else
         [service-name-field {:tr tr :edit-key source-id :locale :fi
                              :service-id service-id
                              :ptv-base ptv-base :display-name display-name}])

     ;; 2. Service classes
       (when (seq (:service-classes service))
         [:> Stack {:spacing 0.5}
          [:> Typography {:variant "caption" :color "text.secondary"}
           (tr :ptv.service/classes)]
          [:> Stack {:direction "row" :spacing 0.5 :flex-wrap "wrap"}
           (doall
             (for [class (:service-classes service)]
               (let [label (or (get class :fi) (first (vals class)))]
                 ^{:key label}
                 [:> Chip {:label label :size "small" :variant "outlined"}])))]])

     ;; 3. Keywords
       (when (seq (:ontology-terms service))
         [:> Stack {:spacing 0.5}
          [:> Typography {:variant "caption" :color "text.secondary"}
           (tr :ptv/keywords)]
          [:> Stack {:direction "row" :spacing 0.5 :flex-wrap "wrap"}
           (doall
             (for [onto (:ontology-terms service)]
               (let [label (or (get onto :fi) (first (vals onto)))]
                 ^{:key label}
                 [:> Chip {:label label :size "small" :variant "outlined"}])))]])

     ;; 4. Its service locations in PTV, and the Lipas sites linked to it
       [service-locations-list {:tr tr :service service :ptv-base ptv-base}]
       [linked-sites-list {:tr tr :service service}]

     ;; Who maintains what
       [:> Alert {:severity "info" :variant "outlined"
                  :action (when other-system?
                            (r/as-element
                              [:> Button {:size "small"
                                          :href (str ptv-base "/service/" service-id)
                                          :target "_blank"
                                          :sx #js {:textTransform "none" :whiteSpace "nowrap"}}
                               (tr :ptv/open-in-ptv)]))}
        (tr (case (:management service)
              :lipas :ptv.service/lipas-info
              :native :ptv.service/native-info
              :ptv.service/other-system-read-only))]

     ;; AI generate + translate buttons
       (when-not other-system?
         (let [from-lang @selected-tab
               ;; A native service also translates into the language versions
               ;; it already has, even ones the org hasn't enabled in LIPAS.
               other-langs (disj (set (map keyword (or edit-languages org-languages))) from-lang)
               has-text? (and (seq (get-in data [:summary from-lang]))
                              (seq (get-in data [:description from-lang])))]
           [:> Stack {:direction "row" :spacing 1 :flex-wrap "wrap" :align-items "flex-start"}
            [:> Tooltip {:title (if has-lipas-data?
                                  ""
                                  (tr :ptv.tools.ai/no-lipas-data))}
             [:span
              [:> Button
               {:variant "outlined" :size "small"
                :disabled (or generating? (not has-lipas-data?))
                :sx #js {:textTransform "none"}
                :startIcon (r/as-element
                             (if generating?
                               [:> CircularProgress {:size 16 :color "inherit"}]
                               [:> Icon "auto_fix_high"]))
                :on-click #(==> [::events/generate-service-descriptions org-id edit-key nil [] []])}
               (tr :ptv.actions/generate-with-ai)]]]
            (when (seq other-langs)
              [:> Tooltip {:title (tr :ptv.wizard/translate-to-other-langs-tooltip)}
               [:span
                [:> Button
                 {:size "small" :variant "outlined"
                  :disabled (or generating? (not has-text?))
                  :startIcon (r/as-element
                               (if generating?
                                 [:> CircularProgress {:size 16 :color "inherit"}]
                                 [:> Icon "translate"]))
                  :sx #js {:textTransform "none"}
                  :on-click #(==> [::events/translate-service-candidate-with-texts
                                   edit-key from-lang other-langs
                                   {:summary (get-in data [:summary from-lang])
                                    :description (get-in data [:description from-lang])
                                    :user-instruction (get-in data [:user-instruction from-lang])}])}
                 (str (tr :ptv.wizard/translate-to-other-langs) " ("
                      (str/join ", " (map (comp str/upper-case name) (sort other-langs)))
                      ")")]]])]))

     ;; Texts PTV would reject: sync stays disabled until they're fixed
       (when (seq text-errors)
         [service-text-errors-alert {:tr tr :errors text-errors :native? native?}])

     ;; LIPAS is the master of a service it created: say before the sync
     ;; which language versions it will remove from PTV
       (when (seq versions-to-drop)
         [:> Alert {:severity "info" :variant "outlined"}
          (tr :ptv.service/versions-will-be-removed
              (str/join ", " (map (comp str/upper-case name) versions-to-drop)))])

     ;; Sync button
       (when-not other-system?
         (let [syncing? (<== [::subs/syncing-service? edit-key])
               modified? (= "Modified" (:publishing-status service))]
           [:> Button
            {:variant "contained"
             :color "secondary"
             :disabled (or syncing? modified? (not has-local-edits?) (seq text-errors))
             :size "small"
             :sx #js {:textTransform "none"}
             :startIcon (r/as-element
                          (if syncing?
                            [:> CircularProgress {:size 16 :color "inherit"}]
                            [:> Icon "sync"]))
           ;; The first time LIPAS writes to a service made in PTV, say what
           ;; that means before doing it.
             :on-click (fn [_e]
                         (if (and native? (str/blank? source-id))
                           (reset! confirm-first-save? true)
                           (sync!)))}
            (tr :ptv.actions/sync-now)]))

       (when (and has-local-edits? (not other-system?))
         [:> Button {:size "small" :variant "text"
                     :sx #js {:textTransform "none" :alignSelf "flex-start"}
                     :startIcon (r/as-element [:> Icon "undo"])
                     :on-click #(==> [::events/discard-service-edits edit-key])}
          (tr :ptv.service/discard-edits)])

       [first-save-dialog {:tr tr :service service
                           :open? @confirm-first-save?
                           :on-close #(reset! confirm-first-save? false)
                           :on-confirm (fn []
                                         (reset! confirm-first-save? false)
                                         (sync!))}]

     ;; Last modified
       [:> Typography {:variant "caption" :color "text.secondary"}
        (str (tr :general/last-modified) " " (:last-modified-human service))]])))

(defn native-language-controls
  "Language-version removal for the selected language of a service made in
  PTV (see lipas.data.ptv/patch-service-texts). Its name per language is
  edited with the pencil in the left column."
  [{:keys [tr edit-key service selected-tab removed?]}]
  (let [locale @selected-tab
        version? (some #{locale} (:language-versions service))
        lang-label (str/upper-case (name locale))
        toggle-removal #(==> [::events/toggle-service-language-removal edit-key locale])]
    [:> Stack {:spacing 1}
     (when (seq (:ptv-only-languages service))
       [:> Typography {:variant "caption" :color "text.secondary"}
        (tr :ptv.service/ptv-only-languages
            (str/join ", " (map str/upper-case (:ptv-only-languages service))))])
     (cond
       removed?
       [:> Alert {:severity "warning" :variant "outlined"
                  :action (r/as-element
                            [:> Button {:size "small" :sx #js {:textTransform "none"}
                                        :on-click toggle-removal}
                             (tr :actions/cancel)])}
        (tr :ptv.service/language-removal-pending lang-label)]

       (and version? (not= :fi locale))
       [:> Button {:size "small" :variant "text" :color "warning"
                   :sx #js {:textTransform "none" :alignSelf "flex-start"}
                   :startIcon (r/as-element [:> Icon "delete_outline"])
                   :on-click toggle-removal}
        (tr :ptv.service/remove-language-version lang-label)])]))

(defn service-panel-right
  [{:keys [tr source-id edit-key service descriptions org-languages selected-tab native? other-system?
           edit-languages removals]}]
  ;; A language version removed by the last sync takes its tab with it.
  (when-not (contains? (set (or edit-languages org-languages)) (name @selected-tab))
    (reset! selected-tab :fi))
  (let [removed? (contains? removals @selected-tab)
        ;; A service another system maintains is shown read-only, as is a
        ;; native service's language version marked for removal.
        ;; text-field also calls on-change on blur, so it gets a no-op.
        read-only-props (when (or other-system? removed?)
                          {:on-change (fn [_])
                           :InputProps {:readOnly true}})
        summary-data (merge (:summary service) (:summary descriptions))
        description-data (merge (:description service) (:description descriptions))
        user-instruction-data (merge (:user-instruction service) (:user-instruction descriptions))
        sub-category-id (ptv-data/parse-service-source-id source-id)]
    [:> Stack {:spacing 2}

     [controls/lang-selector
      {:value @selected-tab
       :on-change #(reset! selected-tab %)
       :enabled-languages (set (or edit-languages org-languages))}]

     (when native?
       [native-language-controls
        {:tr tr :edit-key edit-key :service service :selected-tab selected-tab
         :removed? removed?}])

     ;; Summary
     (let [v (or (get summary-data @selected-tab) "")]
       [text-fields/text-field
        (merge
          {:on-change #(==> [::events/set-service-candidate-summary edit-key @selected-tab %])
           :multiline true
           :variant "outlined"
           :label (tr :ptv/summary)
           :value v
           :inputProps #js {:maxLength ptv-data/max-summary-length}
           :helperText (controls/text-length-hint tr v ptv-data/max-summary-length)
           :error (controls/text-length-error? v ptv-data/max-summary-length)}
          read-only-props)])

     ;; Auditor feedback for summary
     [service-audit-feedback-component
      {:service-id (:service-id service)
       :field-name :summary
       :current-content (:summary service)}]

     ;; Description
     [service-writing-guidance
      {:tr tr :sub-category-id sub-category-id :field :description}]
     (let [v (or (get description-data @selected-tab) "")]
       [text-fields/text-field
        (merge
          {:on-change #(==> [::events/set-service-candidate-description edit-key @selected-tab %])
           :variant "outlined"
           :rows 7
           :multiline true
           :label (tr :ptv/description)
           :value v
           :inputProps #js {:maxLength ptv-data/max-description-length}
           :helperText (controls/text-length-hint tr v ptv-data/max-description-length)
           :error (controls/text-length-error? v ptv-data/max-description-length)}
          read-only-props)])

     ;; Auditor feedback for description
     [service-audit-feedback-component
      {:service-id (:service-id service)
       :field-name :description
       :current-content (:description service)}]

     ;; User instruction
     [service-writing-guidance
      {:tr tr :sub-category-id sub-category-id :field :user-instruction}]
     (let [v (or (get user-instruction-data @selected-tab) "")]
       [text-fields/text-field
        (merge
          {:on-change #(==> [::events/set-service-candidate-user-instruction edit-key @selected-tab %])
           :variant "outlined"
           :rows 5
           :multiline true
           :label (tr :ptv/user-instruction)
           :value v
           :inputProps #js {:maxLength ptv-data/max-user-instruction-length}
           :helperText (controls/text-length-hint tr v ptv-data/max-user-instruction-length)
           :error (controls/text-length-error? v ptv-data/max-user-instruction-length)}
          read-only-props)])

     ;; Auditor feedback for user instruction
     [service-audit-feedback-component
      {:service-id (:service-id service)
       :field-name :user-instruction
       :current-content (:user-instruction service)}]]))

(defn service-details
  "The open row of a service in the Palvelut tab: its sync review, and its
   texts to edit beside what it is linked to. `service` is its row
   (::subs/service-rows)."
  [{:keys [org-id service]}]
  (r/with-let [selected-tab (r/atom :fi)]
    (let [tr (<== [:lipas.ui.subs/translator])
          org-languages (<== [::subs/org-languages org-id])
          ptv-base (ptv-base-url)
          {:keys [edit-key review open-rows modified? native? other-system? edit-languages
                  name-data text-errors versions-to-drop data has-local-edits? edited-name
                  removals descriptions]} service
          choices (<== [::subs/review-choices edit-key])]
      [:> Stack {:spacing 2 :sx #js {:py 2}}
       (when review
         (let [open? (<== [::subs/review-open? edit-key])]
           [review/review-panel
            {:id (review-data/panel-dom-id edit-key)
             :tr tr
             :review review
             :choices choices
             :open? open?
             :on-toggle #(==> [::events/set-review-open edit-key (not open?)])
             :on-take (fn [rows side] (==> [::events/service-review-take org-id edit-key rows side]))
             :on-reset #(==> [::events/service-review-reset org-id edit-key])
             :written? (<== [::subs/review-written? edit-key])
             :rights {:ptv-manager? true}
             :org-langs org-languages
             :notices (when modified?
                        [{:key :modified
                          :text (tr :ptv.review/notice-modified)
                          :actions [{:label (tr :ptv.review/open-in-ptv)
                                     :href (str ptv-base "/service/" (:service-id service))}]}])
             :first-open-id (review-data/first-open-dom-id edit-key)
             :footer (tr :ptv.review/service-footer (tr :ptv.actions/sync-now))}]))
       ;; Not a Grid: its negative margins widen the table cell it is in
       [:> Stack {:direction #js {:xs "column" :md "row"} :spacing 3 :alignItems "flex-start"}
        [:> Box {:sx #js {:flex "5 1 0" :minWidth 0 :width "100%"}}
         [service-panel-left {:tr tr :org-id org-id :service service
                              :open-rows open-rows
                              :native? native?
                              :other-system? other-system?
                              :edit-key edit-key
                              :edit-languages edit-languages
                              :name-data name-data
                              :text-errors text-errors
                              :versions-to-drop versions-to-drop
                              :data data :ptv-base ptv-base
                              :has-local-edits? has-local-edits?
                              :edited-name edited-name
                              :org-languages org-languages :selected-tab selected-tab}]]
        [:> Box {:sx #js {:flex "7 1 0" :minWidth 0 :width "100%"}}
         [service-panel-right {:tr tr :source-id (:source-id service) :service service
                               :native? native?
                               :other-system? other-system?
                               :edit-key edit-key
                               :edit-languages edit-languages
                               :removals removals
                               :descriptions descriptions
                               :org-languages org-languages
                               :selected-tab selected-tab}]]]])))

;;; The Palvelut tab's table ;;;

(defn- service-sync-cell
  "The service's sync status, with the chips of the sites."
  [tr {:keys [sync-status service-modified modified? invalid-texts?]}]
  (case sync-status
    nil
    [:> Tooltip {:title (tr :ptv.service/not-written)}
     [:> Typography {:component "span" :color "text.disabled"} "–"]]

    :other-system
    [:> Tooltip {:title (tr :ptv.service/other-system-read-only)}
     [:> Icon {:sx #js {:color "text.secondary"}} "lock"]]

    [status-chip tr {:sync-status sync-status
                     :last-sync (some-> service-modified (str "Z"))
                     ;; the sites' text speaks of a draft or a failed sync
                     :tooltip (when (and (= :blocked sync-status) invalid-texts? (not modified?))
                                (tr :ptv.service/status-invalid-texts))}]))

(defn- service-audit-cell
  "The verdict of the service's audit on its current texts, as the sites
   show theirs."
  [tr {:keys [audit-status audit]}]
  (case audit-status
    :changes-requested
    [:> Tooltip {:title (tr :ptv.audit.status/changes-requested)}
     [:> WarningIcon {:sx #js {:color "warning.main" :width "32px" :height "32px"}}]]

    (:approved :approved-with-feedback)
    [:> Stack {:direction "row" :spacing 0.5 :alignItems "center" :justifyContent "center"}
     [:> Tooltip {:title (tr :ptv.audit.status/approved)}
      [:> CheckCircleIcon {:sx #js {:color "success.main" :width "32px" :height "32px"}}]]
     (when (= :approved-with-feedback audit-status)
       [audit-comment-marker {:tr tr
                              :audit audit
                              :fields [:summary :description :user-instruction]
                              :size "20px"}])]

    ;; :none — no verdict covers the current text
    nil))

(defn- service-origin-chip
  "Where the service was created, which decides what Lipas may edit of it."
  [tr {:keys [management]}]
  (let [[label tooltip props] (case management
                                :lipas [(tr :ptv.service/origin-lipas)
                                        (tr :ptv.service/origin-lipas-tooltip)
                                        {:color "primary"}]
                                :native [(tr :ptv.service/origin-ptv)
                                         (tr :ptv.service/origin-ptv-tooltip)
                                         {:color "primary" :variant "outlined"}]
                                [(tr :ptv.service/other-system-chip)
                                 (tr :ptv.service/other-system-read-only)
                                 {:variant "outlined"}])]
    [:> Tooltip {:title tooltip}
     [:> Chip (merge {:label label
                      :size "small"
                      :sx #js {:borderRadius "4px" :fontWeight 700}}
                     props)]]))

(defn- channels-tooltip
  "The names of the service's channels, in the groups of its panel."
  [tr {:keys [locations other-channels]}]
  (let [{from-lipas true from-ptv false} (group-by (comp boolean seq :lipas-ids) locations)
        group (fn [title items]
                (let [shown (take 7 items)
                      more (- (count items) (count shown))]
                  [:div {:style {:marginTop "6px"}}
                   [:div {:style {:fontWeight 700}} title]
                   (for [[i item] (map-indexed vector shown)]
                     ^{:key i} [:div item])
                   (when (pos? more)
                     [:div {:style {:opacity 0.8}} (tr :ptv.service/and-more more)])]))]
    [:div
     [:div {:style {:fontWeight 700}} (tr :ptv.service/locations-title (count locations))]
     (when (seq from-lipas)
       (group (tr :ptv.service/locations-from-lipas (count from-lipas)) (map :name from-lipas)))
     (when (seq from-ptv)
       (group (tr :ptv.service/locations-from-ptv (count from-ptv)) (map :name from-ptv)))
     (when (seq other-channels)
       (group (tr :ptv.service/other-channels-title (count other-channels))
              (for [{:keys [name type]} other-channels]
                (str name " · " (channel-type-label tr type)))))]))

(defn- service-locations-cell
  "How many service locations the service has in PTV, how many of them are
   Lipas sites, and how many other channels; a service with none stands
   out."
  [tr {:keys [locations other-channels concerns-lipas?] :as service}]
  (let [n (count locations)
        from-lipas (count (filter (comp seq :lipas-ids) locations))
        others (count other-channels)
        caption (->> [(when (pos? from-lipas) (tr :ptv.service/from-lipas from-lipas))
                      (when (pos? others)
                        (if (= 1 others)
                          (tr :ptv.service/other-channels-one)
                          (tr :ptv.service/other-channels others)))]
                     (remove nil?)
                     (str/join " · "))
        content [:> Stack {:alignItems "center" :spacing 0.25}
                 (if (zero? n)
                   [:> Chip {:label (tr :ptv.service/no-locations)
                             :size "small"
                             :variant "outlined"
                             :color (if concerns-lipas? "warning" "default")
                             :icon (r/as-element [:> Icon {:fontSize "small"} "location_off"])}]
                   [:> Typography {:fontWeight 700} n])
                 (when (seq caption)
                   [:> Typography {:variant "caption" :color "text.secondary" :sx #js {:whiteSpace "nowrap"}}
                    caption])]]
    (if (pos? (+ n others))
      ;; not interactive: a click in it would reach the row and toggle it
      [:> Tooltip {:title (r/as-element [channels-tooltip tr service])
                   :disableInteractive true}
       [:> Box {:sx #js {:display "inline-block" :cursor "help"}} content]]
      content)))

(defn- service-sites-cell
  "How many Lipas sites are saved as linked to the service, leading to
   them in the Liikuntapaikat tab."
  [tr {:keys [service-id site-ids]}]
  (let [n (count site-ids)]
    (if (pos? n)
      [:> Tooltip {:title (tr :ptv.service/show-sites-tooltip n)}
       [:> Button {:size "small"
                   :sx #js {:minWidth 0 :fontSize "1rem" :fontWeight 700
                            :textDecoration "underline" :textUnderlineOffset "3px"}
                   :on-click #(==> [::events/show-service-sites service-id])}
        n]]
      [:> Typography {:fontWeight 700 :color "text.disabled"} n])))

(defn- service-row
  [{:keys [tr org-id service open? matches? on-toggle]}]
  (let [service-name (ptv-services/display-name service)]
    [:<>
     [:> TableRow {:hover true
                   :sx #js {:cursor "pointer"}
                   ;; the whole row opens it, except what is a control of its own
                   :on-click (fn [e]
                               (when-not (.closest (.-target e) "button, a, input")
                                 (on-toggle service)))}
      [:> TableCell {:padding "checkbox"}
       [:> IconButton {:size "small"
                       :aria-expanded (boolean open?)
                       :aria-label (or service-name (tr :ptv.service/unnamed))
                       :on-click #(on-toggle service)}
        [:> Icon (if open? "keyboard_arrow_up_icon" "keyboard_arrow_down_icon")]]]
      [:> TableCell {:sx #js {:textAlign "center"}}
       [service-sync-cell tr service]]
      [:> TableCell {:sx #js {:textAlign "center"}}
       [service-audit-cell tr service]]
      [:> TableCell
       [:> Stack {:direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true}
        (if service-name
          [:span service-name]
          [:> Typography {:component "span" :fontStyle "italic" :color "text.secondary"}
           (tr :ptv.service/unnamed)])
        ;; Kept on screen only because the row is open
        (when-not matches?
          [:> Chip {:label (tr :ptv/filter-no-longer-matches)
                    :size "small"
                    :variant "outlined"}])]]
      [:> TableCell
       [service-origin-chip tr service]]
      [:> TableCell {:sx #js {:textAlign "center"}}
       [service-locations-cell tr service]]
      [:> TableCell {:sx #js {:textAlign "center"}}
       [service-sites-cell tr service]]]
     [:> TableRow
      [:> TableCell {:style {:paddingTop 0 :paddingBottom 0} :colSpan 7}
       [:> Collapse {:in (boolean open?) :timeout "auto" :unmountOnExit true}
        [open-row-content
         [service-details {:org-id org-id :service service}]]]]]]))

(defn- header-with-tooltip
  "A column heading explained in a tooltip, which opens on keyboard focus
   too: the explanations shown on hover in the cells below are here as
   well."
  [{:keys [label tooltip align]}]
  [:> TableCell {:sx #js {:textAlign (or align "center")}}
   [:> Tooltip {:title tooltip}
    [:span {:tabIndex 0
            :style {:cursor "help"
                    :textDecoration "underline dotted"
                    :textUnderlineOffset "4px"}}
     label]]])

(defn- origin-header-tooltip
  "What the Luotu column means, and each of its values, as the chips
   below explain theirs."
  [tr]
  [:div
   [:div (tr :ptv.service/col-origin-tooltip)]
   (for [[label explanation] [[(tr :ptv.service/origin-lipas) (tr :ptv.service/origin-lipas-tooltip)]
                              [(tr :ptv.service/origin-ptv) (tr :ptv.service/origin-ptv-tooltip)]
                              [(tr :ptv.service/other-system-chip) (tr :ptv.service/other-system-read-only)]]]
     ^{:key label}
     [:div {:style {:marginTop "6px"}}
      [:b label] ": " explanation])])

(defn- services-table
  "The services that concern Lipas, then (when shown) the org's others
   under a heading of their own."
  [{:keys [tr org-id main others open-ids matching-ids on-toggle]}]
  (let [row (fn [service]
              (let [id (:service-id service)]
                ^{:key id}
                [service-row {:tr tr
                              :org-id org-id
                              :service service
                              :open? (contains? open-ids id)
                              :matches? (contains? matching-ids id)
                              :on-toggle on-toggle}]))]
    ;; the dialog lays its content out as a flex column: without this the
    ;; footer below would squeeze the table into a scroll box of its own
    [:> TableContainer {:component Paper :sx #js {:flexShrink 0 :containerType "inline-size"}}
     [:> Table
      [:> TableHead
       [:> TableRow
        [:> TableCell {:padding "checkbox"}]
        [:> TableCell {:sx #js {:textAlign "center"}} (tr :ptv/integration)]
        [:> TableCell {:sx #js {:textAlign "center"}} (tr :ptv.audit/tab-label)]
        [:> TableCell (tr :general/name)]
        [header-with-tooltip {:label (tr :ptv.service/col-origin)
                              :tooltip (r/as-element [origin-header-tooltip tr])
                              :align "left"}]
        [header-with-tooltip {:label (tr :ptv.service/col-locations)
                              :tooltip (tr :ptv.service/col-locations-tooltip)}]
        [header-with-tooltip {:label (tr :ptv.service/col-sites)
                              :tooltip (tr :ptv.service/col-sites-tooltip)}]]]
      ;; One keyed seq: a row moving across the heading stays mounted
      [:> TableBody
       (doall
         (concat
           (map row main)
           (when (seq others)
             [^{:key "others-divider"}
              [:> TableRow
               [:> TableCell {:colSpan 7 :sx #js {:bgcolor "action.hover" :py 1.25}}
                [:> Typography {:component "span" :variant "subtitle2"}
                 (tr :ptv.service/others-divider (count others))]
                [:> Typography {:component "span" :variant "caption" :color "text.secondary" :sx #js {:ml 1.5}}
                 (tr :ptv.service/others-divider-caption)]]]])
           (map row others)))]]]))

(defn- other-services-footer
  "Below the table: how many of the org's PTV services don't concern
   Lipas, and a button to list them. A search looks into them too, so a
   service kept there never reads as missing."
  [{:keys [tr others show-others? search-hits on-toggle]}]
  (when (seq others)
    (let [n (count others)]
      [:> Stack {:direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true
                 :sx #js {:mx 2}}
       (when-not show-others?
         [:<>
          [:> Icon {:sx #js {:fontSize "1.2rem" :color (if (pos? search-hits) "info.main" "text.secondary")}}
           (if (pos? search-hits) "search" "info_outline")]
          [:> Tooltip {:title (tr :ptv.service/others-tooltip)}
           [:> Typography {:variant "body2" :fontWeight 400 :color "text.secondary" :tabIndex 0
                           :sx #js {:cursor "help" :textDecoration "underline dotted" :textUnderlineOffset "4px"}}
            (if (= 1 n) (tr :ptv.service/others-hidden-one) (tr :ptv.service/others-hidden n))]]
          (when (pos? search-hits)
            [:> Typography {:variant "body2"}
             (if (= 1 search-hits)
               (tr :ptv.service/others-search-hit-one)
               (tr :ptv.service/others-search-hits search-hits))])])
       [:> Button {:size "small" :variant "text"
                   :sx #js {:textTransform "none"}
                   :startIcon (r/as-element [:> Icon (if show-others? "expand_less" "expand_more")])
                   :on-click on-toggle}
        (tr (if show-others? :ptv.service/hide-others :ptv.service/show-others))]])))

(defn- service-filter-chips
  [tr]
  (for [k ptv-services/status-filters]
    [k (tr (if (= :no-locations k)
             :ptv.service/filter-no-locations
             (keyword "ptv" (str "filter-" (name k)))))]))

(defn services
  []
  (r/with-let [expanded-rows (r/atom {})
               ;; service-id -> the place an open row had when opened, its
               ;; :rank (ptv-services/sort-key) and its group (:concerns?):
               ;; an edit changes its status, a first save makes it Lipas's,
               ;; and a row that jumps away from under the cursor is hard to
               ;; follow
               ranks (r/atom {})
               search-text (r/atom "")
               status-filter (r/atom :all)
               filter-expanded? (r/atom true)
               creating? (r/atom false)
               ;; made once: a new fn on each render would make every row's
               ;; props differ, and each keystroke re-render all of them
               toggle-row! (fn [{:keys [service-id] :as row}]
                             (if (get @expanded-rows service-id)
                               (do (swap! expanded-rows dissoc service-id)
                                   (swap! ranks dissoc service-id))
                               (do (swap! expanded-rows assoc service-id true)
                                   (swap! ranks assoc service-id {:rank (first (ptv-services/sort-key row))
                                                                  :concerns? (:concerns-lipas? row)}))))]
    (let [tr (<== [:lipas.ui.subs/translator])
          org-id (<== [::subs/selected-ptv-org-id])
          show-others? (= "all" (<== [::subs/services-filter]))
          rows (<== [::subs/service-rows org-id])
          matching (fn [search status others?]
                     (into #{}
                           (comp (filter #(and (or others? (:concerns-lipas? %))
                                               (ptv-services/matches? % search status)))
                                 (map :service-id))
                           rows))
          matching-ids (matching @search-text @status-filter show-others?)
          open-ids (into #{} (keep (fn [[id open?]] (when open? id))) @expanded-rows)
          ;; Every edit writes into the edits the statuses are derived
          ;; from, so without the pin an open row would vanish mid-edit the
          ;; moment it stops matching (as the sites' table does)
          shown? #(or (matching-ids (:service-id %)) (open-ids (:service-id %)))
          compare-rows (fn [a b]
                         (let [na (ptv-services/display-name a)
                               nb (ptv-services/display-name b)
                               c (compare (conj (ptv-services/sort-key a (:rank (get @ranks (:service-id a)))) (nil? na))
                                          (conj (ptv-services/sort-key b (:rank (get @ranks (:service-id b)))) (nil? nb)))]
                           (if (zero? c)
                             (.compare controls/label-collator (or na "") (or nb ""))
                             c)))
          concerns? (fn [{:keys [service-id concerns-lipas?]}]
                      (boolean (if-let [pinned (get @ranks service-id)]
                                 (:concerns? pinned)
                                 concerns-lipas?)))
          {main true others false} (group-by concerns? (filter shown? rows))
          other-rows (remove :concerns-lipas? rows)
          ;; A filter change starts a new lookup: rows open from the
          ;; previous one stay open only if they match the new one too
          refilter! (fn [search status others?]
                      (let [ids (matching search status others?)]
                        (swap! expanded-rows #(into {} (filter (comp ids key)) %))
                        (swap! ranks select-keys ids)))
          awaiting (count (filter #(and (:concerns-lipas? %) (= :awaiting-choice (:sync-status %))) rows))]
      [:<>
       [:> Stack {:direction "row" :sx #js {:mx 2 :mt 1}}
        [:> Button {:variant "outlined" :size "small"
                    :sx #js {:textTransform "none"}
                    :startIcon (r/as-element [:> Icon (if @creating? "close" "add")])
                    :on-click #(swap! creating? not)}
         (tr :ptv.service/create-new)]]
       (when @creating?
         [:> Paper {:variant "outlined" :sx #js {:mx 2 :p 2}}
          [ptv-components/add-service-create-form {:org-id org-id
                                                   :on-cancel #(reset! creating? false)}]])
       ;; changes made in PTV waiting for a choice: say how many, and lead to them
       (when (pos? awaiting)
         [:> Alert {:severity "warning" :variant "outlined" :sx #js {:mx 2 :mt 1}
                    :action (r/as-element
                              [:> Button {:size "small" :color "inherit"
                                          :sx #js {:textTransform "none"}
                                          ;; the search goes: it might hide the very rows meant
                                          :on-click #(do (refilter! "" :awaiting-choice show-others?)
                                                         (reset! search-text "")
                                                         (reset! status-filter :awaiting-choice)
                                                         (reset! filter-expanded? true))}
                               (tr (if (= 1 awaiting) :ptv.service/show-it :ptv/show-awaiting-choice))])}
          (if (= 1 awaiting)
            (tr :ptv.service/awaiting-choice-banner-one)
            (tr :ptv.service/awaiting-choice-banner awaiting))])
       [filter-bar
        {:tr tr
         :expanded? @filter-expanded?
         :on-toggle-expanded #(swap! filter-expanded? not)
         :search-text @search-text
         :on-search-change #(do (refilter! % @status-filter show-others?)
                                (reset! search-text %))
         :chips (service-filter-chips tr)
         :status-filter @status-filter
         :on-status-change #(do (refilter! @search-text % show-others?)
                                (reset! status-filter %))
         :total-count (if show-others? (count rows) (count (filter :concerns-lipas? rows)))
         :filtered-count (count matching-ids)}]
       (if (or (seq main) (and show-others? (seq others)))
         [services-table {:tr tr
                          :org-id org-id
                          :main (sort compare-rows main)
                          :others (when show-others? (sort compare-rows others))
                          :open-ids open-ids
                          :matching-ids matching-ids
                          :on-toggle toggle-row!}]
         (let [filtering? (or (not (str/blank? @search-text)) (not= :all @status-filter))]
           [:> Paper {:sx #js {:mx 2 :p 3}}
            [:> Stack {:spacing 1.5 :alignItems "center"}
             [:> Typography {:variant "body2" :fontWeight 400 :color "text.secondary" :textAlign "center"}
              (tr (cond
                    (not filtering?) (if show-others? :ptv.service/no-services-all :ptv.service/no-services)
                    show-others? :ptv.service/no-matches-all
                    :else :ptv.service/no-matches))]
             (when filtering?
               [:> Button {:size "small" :variant "outlined"
                           :sx #js {:textTransform "none"}
                           :startIcon (r/as-element [:> Icon "filter_list_off"])
                           :on-click #(do (refilter! "" :all show-others?)
                                          (reset! search-text "")
                                          (reset! status-filter :all))}
                (tr :ptv.service/clear-filters)])]]))
       [other-services-footer
        {:tr tr
         :others other-rows
         :show-others? show-others?
         :search-hits (if (or show-others? (str/blank? @search-text))
                        0
                        (count (filter #(ptv-services/matches? % @search-text @status-filter) other-rows)))
         :on-toggle #(do (refilter! @search-text @status-filter (not show-others?))
                         (==> [::events/toggle-services-filter]))}]])))

(defn wizard
  []
  (let [tr (<== [:lipas.ui.subs/translator])
        ptv-step @(rf/subscribe [::subs/selected-step])
        set-step (fn [i _e]
                   (rf/dispatch [::events/set-step i]))

        org-id (<== [::subs/selected-ptv-org-id])
        services-done? (empty? (<== [::subs/service-candidates org-id]))
        site-setup-done? (<== [::subs/sports-site-setup-done org-id])]
    [:> Stack
     [:> Stepper
      {:nonLinear true
       :activeStep ptv-step
       :sx (fn [theme]
             #js {:mt 2
                  :mb 4
                  ".Mui-completed" #js {:fontWeight "500 !important"}
                  ".Mui-completed.Mui-active" #js {:fontWeight "700 !important"}
                  ".Mui-active" #js {:fontWeight "700 !important"
                                     :color "primary.main"}
                  ".MuiStepIcon-root.Mui-active" #js {:fill (.. theme -palette -secondary -main)}})}
      [:> Step
       {:key "1"
        :completed true}
       [:> StepButton
        {:color "inherit"
         :onClick (partial set-step 0)}
        (str "1. " (tr :ptv.wizard/select-sports-sites))]]
      [:> Step
       {:key "2"
        :completed services-done?}
       [:> StepButton
        {:color "inherit"
         :onClick (partial set-step 1)}
        (str "2. " (tr :ptv.tools.generate-services/headline))]]
      [:> Step
       {:key "3"
        :completed site-setup-done?}
       [:> StepButton
        {:color "inherit"
         :onClick (partial set-step 2)}
        (str "3. " (tr :ptv.wizard/integrate-service-locations))]]]
     (case ptv-step
       0 [set-types]
       1 [create-services]
       2 [integrate-service-locations])]))

(defn dialog
  [{:keys [tr]}]
  (let [open? (<== [::subs/dialog-open?])
        selected-tab (<== [::subs/selected-tab])
        loading? (<== [::subs/loading-from-ptv?])
        any-loading? (<== [::subs/loading?])
        selected-org (<== [::subs/selected-org])
        ptv-org-id (<== [::subs/selected-ptv-org-id])
        org-data (<== [::subs/selected-org-data ptv-org-id])

        has-manage-privilege? (<== [::subs/has-manage-privilege?])
        has-audit-privilege? (<== [::subs/has-audit-privilege?])
        admin? (<== [:lipas.ui.user.subs/check-privilege nil :users/manage])

        on-close #(==> [::events/close-dialog])]

    [:> Dialog
     {:open open?
      :fullScreen true
      :max-width "xl"
      ;; The assistant panel lives outside this dialog's portal — without
      ;; this the modal focus trap steals every keystroke from it.
      :disableEnforceFocus true}

     [:> AppBar
      {:sx #js {:position "relative"}}
      [:> Toolbar
       [:> IconButton
        {:edge "start"
         :onClick on-close
         :color "inherit"}
        [:> CloseIcon]]
       [:> Typography
        {:variant "h6"
         :component "div"
         :sx #js {:ml 2 :flex 1}}
        (tr :ptv/tooltip)]]]

     [:> DialogContent
      {:sx #js {:display "flex"
                :flexDirection "column"
                :gap 2}}

      [:> Stack {:direction "row" :align-items "center" :spacing 2}
       ;; The tabs slot takes the row's slack whether or not the tabs are
       ;; rendered yet, so the org selector stays in the top right corner
       ;; instead of jumping there once an org has been picked.
       [:> Box {:sx #js {:flex 1}}
        (when (and org-data (not loading?))
          [:> Tabs
           {:value selected-tab
            :on-change #(==> [::events/select-tab %2])
            :textColor "primary"
            :indicatorColor "secondary"}

           (when has-manage-privilege?
             [:> Tab {:value "wizard" :label (tr :ptv/wizard)}])
           (when has-manage-privilege?
             [:> Tab {:value "services" :label (tr :ptv/services)}])
           (when has-manage-privilege?
             [:> Tab {:value "sports-sites" :label (tr :ptv/sports-sites)}])
           (when has-audit-privilege?
             [:> Tab {:value "audit" :label (tr :ptv.audit/tab-label)}])])]
       (when admin?
         [:> Tooltip {:title (tr :ptv.actions/refresh-data)}
          [:span
           [:> IconButton
            {:disabled (or (nil? selected-org) any-loading?)
             ;; the tooltip sits on the span, which a disabled button needs
             :aria-label (tr :ptv.actions/refresh-data)
             :on-click #(==> [::events/fetch-ptv-org-data selected-org])}
            [:> Icon "refresh"]]]])
       [:> Box {:sx #js {:minWidth 250}}
        [org-selector {:label (tr :ptv.actions/select-org)}]]]

      (when loading?
        [:> Stack
         {:direction "row"
          :spacing 2
          :alignItems "center"
          :justifyContent "center"}
         [:> CircularProgress]
         [:> Typography (tr :ptv/loading-from-ptv)]])

      (when (and org-data (not loading?))
        [:<>

         (when (and (= selected-tab "wizard") has-manage-privilege?)
           [wizard])

         (when (and (= selected-tab "services") has-manage-privilege?)
           [services])

         (when (and (= selected-tab "sports-sites") has-manage-privilege?)
           [table])

         (when (and (= selected-tab "audit") has-audit-privilege?)
           [audit/main-view {:tr tr}])])]]))