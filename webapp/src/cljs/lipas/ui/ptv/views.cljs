(ns lipas.ui.ptv.views
  (:require ["@mui/icons-material/CheckCircle$default" :as CheckCircleIcon]
            ["@mui/icons-material/Close$default" :as CloseIcon]
            ["@mui/icons-material/HourglassTop$default" :as PartialIcon]
            ["@mui/icons-material/Sync$default" :as Sync]
            ["@mui/icons-material/SyncDisabled$default" :as SyncDisabled]
            ["@mui/icons-material/SyncProblem$default" :as SyncProblem]
            ["@mui/icons-material/Warning$default" :as WarningIcon]
            ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Radio$default" :as Radio]
            ["@mui/material/RadioGroup$default" :as RadioGroup]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/AppBar$default" :as AppBar]
            ["@mui/material/Avatar$default" :as Avatar]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Step$default" :as Step]
            ["@mui/material/StepButton$default" :as StepButton]
            ["@mui/material/Stepper$default" :as Stepper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [goog.string.format]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.misc :as misc]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableContainer$default" :as TableContainer]
            [lipas.ui.mui :as mui]
            [lipas.ui.ptv.audit :as audit]
            [lipas.ui.ptv.components :as ptv-components]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.events :as events]
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
                                  (map (fn [x]
                                         {:value (value-fn x)
                                          :label (:name x)})
                                       items))
                                [items value-fn])]
    [autocomplete2
     {:options options
      :multiple false
      :label label
      :value (first value)
      :onChange (fn [_e v]
                  (on-change [(:value v)]))}]))

(defn audit-feedback-component
  "Displays audit feedback for a specific field (summary or description)"
  [{:keys [lipas-id field-name]}]
  (let [feedback (<== [::subs/site-audit-field-feedback lipas-id field-name])
        status (<== [::subs/site-audit-field-status lipas-id field-name])]
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
          "changes-requested" "Auditoijan palaute - vaatii muutoksia"
          "approved" "Auditoijan palaute - hyväksytty"
          "Auditoijan palaute")]
       feedback])))

(defn ptv-link-field
  "Shows PTV items as links with an edit button to switch to selector mode.
   Props: :label, :items [{:id :name :url}], :editing?, :on-edit, :on-cancel, :selector-component"
  [{:keys [label items editing? on-edit on-cancel selector-component tooltip tr]}]
  (if (and (seq items) (not editing?))
    [:> Stack {:spacing 0.5}
     [:> Stack {:direction "row" :align-items "center" :spacing 0.5}
      [:> Typography {:variant "caption" :color "text.secondary"} label]
      [:> Tooltip {:title (or tooltip "")}
       [:> IconButton {:size "small" :on-click on-edit
                       :sx #js {:p 0}}
        [:> Icon {:sx #js {:fontSize "1rem"}} "edit"]]]]
     (for [{:keys [id name url]} items]
       ^{:key id}
       [:> Link {:href url :target "_blank" :variant "body2"} (or name id)])]
    [:> Stack {:spacing 0.5}
     selector-component
     (when (and editing? (seq items))
       [:> Button {:size "small" :variant "text"
                   :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                   :on-click on-cancel}
        (tr :actions/cancel)])]))

(defn form-left-column
  [{:keys [org-id tr site services ptv-base]}]
  (r/with-let [editing-services? (r/atom false)
               editing-channel? (r/atom false)]
    (let [loading? (<== [::subs/generating-descriptions?])
          service-ids (set (:service-ids site))
          linked-services (filter #(contains? service-ids (:service-id %)) services)
          channel-id (first (:service-channel-ids site))
          channel-name (:service-channel-name site)]
      [:> Stack {:spacing 2}

       ;; Services
       [ptv-link-field
        {:tr tr
         :label (str (tr :ptv/services) " PTV:ssä")
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
           :label (str (tr :ptv/service-channel) " PTV:ssä")
           :items [{:id channel-id
                    :name channel-name
                    :url (str ptv-base "/channels/serviceLocation/" channel-id)}]
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
          (if @editing-channel?
            [:> Stack {:spacing 0.5}
             [service-channel-selector
              {:org-id org-id
               :value (:service-channel-ids site)
               :value-fn :service-channel-id
               :on-change (fn [v]
                            (==> [::events/select-service-channels site v])
                            (reset! editing-channel? false))
               :label (tr :ptv/service-channel)}]
             [:> Button {:size "small" :variant "text"
                         :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                         :on-click #(reset! editing-channel? false)}
              (tr :actions/cancel)]]
            [:> Button {:size "small" :variant "text"
                        :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                        :on-click #(reset! editing-channel? true)}
             (tr :ptv.actions/attach-existing-service-channel)])])

       ;; AI generate button
       [:> Button {:disabled loading?
                   :variant "outlined"
                   :size "small"
                   :sx #js {:textTransform "none"}
                   :startIcon (r/as-element [:> Icon "auto_fix_high"])
                   :on-click #(==> [::events/generate-descriptions (:lipas-id site) [] []])}
        (tr :ptv.actions/generate-with-ai)]

       (when loading?
         [:> CircularProgress {:size 20}])

       ;; Sync / Export button
       (let [valid? (and (:sync-enabled site)
                         (some-> site :summary :fi count (> 5))
                         (some-> site :description :fi count (> 5)))
             synced? (:last-sync site)]
         [:> Button
          {:variant "contained"
           :color "secondary"
           :size "small"
           :disabled (or loading? (not valid?))
           :sx #js {:textTransform "none"}
           :startIcon (r/as-element [:> Icon (if synced? "sync" "ios_share")])
           :on-click #(==> [::events/create-ptv-service-location (:lipas-id site) [] []])}
          (if synced?
            (tr :ptv.actions/sync-now)
            (tr :ptv.wizard/export-service-locations-to-ptv))])])))

(defn form-right-column
  [{:keys [tr site org-languages]}]
  (r/with-let [selected-tab (r/atom :fi)]
    (let [loading? (<== [::subs/generating-descriptions?])]
      [:> Stack {:spacing 2}

       [controls/lang-selector
        {:value @selected-tab
         :on-change #(reset! selected-tab %)
         :enabled-languages (set org-languages)}]

       ;; Summary
       (let [v (or (get-in site [:summary @selected-tab]) "")]
         [text-fields/text-field
          {:disabled loading?
           :multiline true
           :variant "outlined"
           :on-change #(==> [::events/set-summary site @selected-tab %])
           :label (tr :ptv/summary)
           :value v
           :helperText (str (count v) "/150")
           :error (> (count v) 150)}])

       [audit-feedback-component
        {:lipas-id (:lipas-id site)
         :field-name :summary}]

       ;; Description
       (let [v (or (get-in site [:description @selected-tab]) "")]
         [text-fields/text-field
          {:disabled loading?
           :variant "outlined"
           :rows 5
           :multiline true
           :on-change #(==> [::events/set-description site @selected-tab %])
           :label (tr :ptv/description)
           :value v
           :helperText (str (count v) "/2500")
           :error (> (count v) 2500)}])

       [audit-feedback-component
        {:lipas-id (:lipas-id site)
         :field-name :description}]

       ;; Translate button
       (when (> (count org-languages) 1)
         (let [from-lang @selected-tab
               other-langs (disj (set (map keyword org-languages)) from-lang)
               has-text? (and (seq (get-in site [:summary from-lang]))
                              (seq (get-in site [:description from-lang])))]
           [:> Button
            {:size "small"
             :variant "text"
             :disabled (or loading? (not has-text?))
             :startIcon (r/as-element [:> Icon "translate"])
             :sx #js {:alignSelf "flex-start" :textTransform "none"}
             :on-click #(==> [::events/translate-site-descriptions
                              (:lipas-id site) from-lang other-langs])}
            (str (tr :ptv.wizard/translate-to-other-langs) " ("
                 (str/join ", " (map (comp str/upper-case name) (sort other-langs)))
                 ")")]))])))

(defn form
  [{:keys [org-id tr site]}]
  (let [services @(rf/subscribe [::subs/services org-id])
        org-languages (<== [::subs/org-languages org-id])
        ptv-base (if (utils/prod?)
                   "https://palvelutietovaranto.suomi.fi"
                   "https://palvelutietovaranto.trn.suomi.fi")]
    [:> Stack {:spacing 2 :sx #js {:pt 2 :pb 2}}

     [checkboxes/switch
      {:label (tr :ptv.actions/export-disclaimer)
       :value (:sync-enabled site)
       :on-change #(==> [::events/toggle-sync-enabled site %])}]

     [:> Grid {:container true :spacing 3}
      [:> Grid {:item true :xs 12 :md 4}
       [form-left-column {:org-id org-id :tr tr :site site
                          :services services :ptv-base ptv-base}]]
      [:> Grid {:item true :xs 12 :md 8}
       [form-right-column {:tr tr :site site :org-languages org-languages}]]]]))

(defn table []
  (r/with-let [expanded-rows (r/atom {})
               show-only-changes-requested (r/atom false)]
    (let [tr (<== [:lipas.ui.subs/translator])
          org-id (<== [::subs/selected-ptv-org-id])
          sites (<== [::subs/sports-sites org-id])
          sync-all-enabled? (<== [::subs/sync-all-enabled? org-id])

          ;; Audit status component
          audit-status-cell (fn [site]
                              (let [{:keys [audit-status]} site

                                    audit-data (get-in site [:ptv :audit])
                                    last-audit (some-> audit-data :timestamp (subs 0 10))
                                    summary-status (get-in audit-data [:summary :status])
                                    desc-status (get-in audit-data [:description :status])

                                    tooltip-text (case audit-status
                                                   :changes-requested
                                                   (str "Muutoksia pyydetty "
                                                        (when last-audit (str last-audit " - "))
                                                        (when (= summary-status "changes-requested") "Tiivistelmä ")
                                                        (when (= desc-status "changes-requested") "Kuvaus"))

                                                   :approved
                                                   (str "Hyväksytty " (or last-audit ""))

                                                   :partial
                                                   (str "Osittain auditoitu " (or last-audit ""))

                                                   :none
                                                   "Ei auditoitu")]

                                [:> TableCell {:sx #js{:textAlign "center"}}
                                 (when (not= audit-status :none)
                                   [:> Tooltip {:title tooltip-text}
                                    (case audit-status
                                      :changes-requested
                                      [:> WarningIcon {:sx #js{:color "warning.main" :fontSize "large" :width "32px" :height "32px"}}]

                                      :approved
                                      [:> CheckCircleIcon {:sx #js{:color "success.main" :fontSize "large" :width "32px" :height "32px"}}]

                                      :partial
                                      [:> PartialIcon {:sx #js{:color "info.main" :fontSize "large" :width "32px" :height "32px"}}])])]))

          headers [{:key :expand :label "" :padding "checkbox"}
                   #_{:key :selected :label (tr :ptv.actions/export)
                      :padding "checkbox"
                      :action-component
                      [:> Switch
                       {:value sync-all-enabled?
                        :on-change #(==> [::events/toggle-sync-all %2])}]}
                   #_{:key :auto-sync :label "Vie automaattisesti"}
                   {:key :event-data :label "Integraatio" :sx {:textAlign "center"}}
                   {:key :audit :label "Audit" :sx {:textAlign "center"}}
                   #_{:key :last-sync :label "Viety viimeksi"}
                   {:key :name :label (tr :general/name)}
                   {:key :type :label (tr :general/type)}
                   ;;{:key :admin :label (tr :lipas.sports-site/admin)}
                   {:key :owner :label (tr :lipas.sports-site/owner)}
                   #_{:key :service :label "Palvelu"}
                   #_{:key :service-channel :label (tr :ptv/service-channel)}
                   #_{:key :service-channel-summary :label "Tiivistelmä"}
                   #_{:key :service-channel-description :label "Kuvaus"}]]

      (when (seq sites)
        [:> TableContainer {:component Paper}
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
             (for [{:keys [lipas-id sync-status] :as site}
                  ;; Sort by audit priority first, then by type
                   (sort-by (fn [site]
                              (let [audit-priority (case (:audit-status site)
                                                     :changes-requested 1 ; Most critical
                                                     :partial 2 ; Needs completion
                                                     :none 3 ; Not audited
                                                     :approved 4 ; All good
                                                     5)] ; Default/unknown
                                [audit-priority (:type site)]))
                            sites)]

               [:<> {:key lipas-id}

                [:> TableRow
                 {:sx [#js{}]}

                ;; Expand toggle
                 [:> TableCell
                  [:> IconButton
                   {:style {:zIndex 1}
                    :size "small"
                    :on-click (fn [] (swap! expanded-rows update lipas-id not))}
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
                    (case sync-status
                      :ok
                      [:> Tooltip {:title (str (tr :ptv/synced-to-ptv) " "
                                               (some-> site :last-sync utils/->human-date-time-at-user-tz))}
                       [:> Chip {:label "PTV"
                                 :size "small"
                                 :color "success"
                                 :variant "outlined"
                                 :icon (r/as-element [:> Sync {:fontSize "small"}])}]]
                      :out-of-date
                      [:> Tooltip {:title (tr :ptv/out-of-date)}
                       [:> Chip {:label "PTV"
                                 :size "small"
                                 :color "warning"
                                 :variant "outlined"
                                 :icon (r/as-element [:> SyncProblem {:fontSize "small"}])}]]
                      :content-drift
                      [:> Tooltip {:title (tr :ptv/content-drift)}
                       [:> Chip {:label "PTV"
                                 :size "small"
                                 :color "error"
                                 :variant "outlined"
                                 :icon (r/as-element [:> SyncProblem {:fontSize "small"}])}]]
                      ;; :not-synced
                      [:> Tooltip {:title (tr :ptv/not-yet-synced)}
                       [:> Chip {:label "PTV"
                                 :size "small"
                                 :variant "outlined"
                                 :icon (r/as-element [:> SyncDisabled {:fontSize "small"}])}]])
                    ;; sync not enabled - no chip
                    nil)]

                ;; Audit status
                 (audit-status-cell site)

                ;; Last-sync
                 #_[:> TableCell
                    (or (some-> site :last-sync utils/->human-date-time-at-user-tz)
                        "Ei koskaan")]

                ;; Name
                 [:> TableCell
                  (:name site)]

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
                   [form {:tr tr
                          :org-id org-id
                          :site site}]]]]]))]]]))))

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
      "Seuraava"
      [:> Icon "arrow_forward"]]]))

(r/defc service-preview
  [{:keys [source-id sub-category-id valid]}]
  (let [preview @(rf/subscribe [::subs/service-preview source-id sub-category-id])
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

        tt-name "Lipaksen luokittelu (pääryhmä → alaryhmä → liikuntapaikkatyyppi) määrittää palvelun nimen. PTV-palvelu luodaan alaryhmätason mukaan ja se saa nimekseen alaryhmän nimen."
        tt-summary "Tiivistelmä on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."
        tt-description "Palvelun kuvaus on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."
        lang-disclaimer "Tieto täytetään vain mikäli integraation käyttöönoton yhteydessä on ilmoitettu että palvelupaikat halutaan kuvata tällä kielellä. Ota yhteyttä lipasinfo@jyu.fi mikäli haluat muuttaa kielivalintoja."]

    [:> Stack {:spacing 2}
     [:> Paper {:sx #js{:p 2 :bgcolor mui/gray3}}
      [:> Typography "Esikatselu näyttää palvelun perustiedot ennen PTV-julkaisua. Vie hiiren osoitin rivin päälle nähdäksesi tiedon alkuperän."]]

     [:> Table {:variant "dense"}
      [:> TableHead
       [:> TableRow
        [:> TableCell "PTV-tietue"]
        [:> TableCell "Arvo"]]]

      (row {:label "Tila"
            :value (:publishingStatus preview)
            :tooltip "Integraation PTV:hen viemät kohteet julkaistaan automaattisesti. Vedokseksi vieminen ei ole tuettu."})

      (row {:label "Nimi suomeksi" :value (get-name "fi") :tooltip tt-name})

      (row {:label "Nimi ruotsiksi"
            :value (get-name "sv")
            :tooltip (str tt-name " " lang-disclaimer)})

      (row {:label "Nimi englanniksi"
            :value (get-name "en")
            :tooltip (str tt-name " " lang-disclaimer)})

      (row {:label "Tyyppi"
            :value (:type preview)
            :tooltip "Palvelun tyyppi on aina \"Service\"."})

      (row {:label "Palveluluokat"
            :value (join (:serviceClasses preview))
            :tooltip "PTV:n ohjeistuksen mukaiset palveluluokat on määritelty jokaiselle Lipaksen liikuntapaikkatyypin alaryhmälle ja ne tulevat palvelun tietoihin automaattisesti."})

      (row {:label "Kohderyhmät"
            :value (join (:targetGroups preview))
            :tooltip "Palvelun kohderyhmä on aina \"Kansalaiset\""})

      (row {:label "Ontologiatermit"
            :value (join (:ontologyTerms preview))
            :tooltip "Ontologiatermit, eli PTV:n ohjeistuksen mukaiset avainsanat, on määritetty jokaiselle Lipaksen liikuntapaikkaluokittelun pää- ja alaryhmälle, ja ne lisätään palvelun tietoihin automaattisesti."})

      (row {:label "Rahoitus"
            :value (:fundingType preview)
            :tooltip "Rahoitustyyppi on aina \"Julkisesti rahoitettu\"."})

      (row {:label "Palveluntuottajat"
            :value (join (:organizations (first (:serviceProducers preview))))
            :tooltip "Palveluntuottaja on se organisaatio (kunta), joka on ottanut integraation käyttöön."})

      (row {:label "Tuotantotapa"
            :value (:provisionType (first (:serviceProducers preview)))
            :tooltip "Palvelun tuotantotapa on aina \"Itse tuotettu\"."})

      (row {:label "Vastuuorganisaatio"
            :value (:mainResponsibleOrganization preview)
            :tooltip "Organisaatio (kunta) joka käyttää integraatiota."})

      (row {:label "Alueen tyyppi"
            :value (-> preview :areas first :type)
            :tooltip "Alueen tyyppi on aina \"Kunta\"."})

      (row {:label "Alueen koodit"
            :value (join (-> preview :areas first :areaCodes))
            :tooltip "Alueen koodi on integraation käyttöön ottaneen organisaation (kunnan) kuntanumero."})

      (row {:label "Tiivistelmä suomeksi"
            :value (get-desc "Summary" "fi")
            :tooltip tt-summary})

      (row {:label "Tiivistelmä ruotsiksi"
            :value (get-desc "Summary" "sv")
            :tooltip (str tt-summary " " lang-disclaimer)})

      (row {:label "Tiivistelmä englanniksi"
            :value (get-desc "Summary" "en")
            :tooltip (str tt-summary " " lang-disclaimer)})

      (row {:label "Kuvaus suomeksi"
            :value (get-desc "Description" "fi")
            :tooltip tt-description})

      (row {:label "Kuvaus ruotsiksi"
            :value (get-desc "Description" "sv")
            :tooltip (str tt-description " " lang-disclaimer)})

      (row {:label "Kuvaus ruotsiksi"
            :value (get-desc "Description" "en")
            :tooltip (str tt-description " " lang-disclaimer)})]]))

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

          manual-services @(rf/subscribe [::subs/manual-services-keys org-id])
          missing-subcategories @(rf/subscribe [::subs/missing-subcategories org-id])
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
           (tr :ptv.wizard/generate-descriptions)]

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
               :disabled (some false? (map :valid service-candidates))
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

          [:<>
           [:> Typography
            "Oletuksena Lipas luo PTV-palvelut liikuntapaikkojen tyyppien
                mukaan, mutta tarvittaessa voit myös luoda muita palveluita ja
                liittää nämä palvelupaikoille manuaalisesti."]
           [controls/services-selector
            {:label "Luo palvelut manuaalisesti"
             :options missing-subcategories
             :value manual-services
             :value-fn :source-id
             :on-change (fn [services]
                          (rf/dispatch [::events/set-manual-services org-id services missing-subcategories]))}]]

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
                "Seuraava"
                [:> Icon "arrow_forward"]]]))

          [:div
           (doall
             (for [{:keys [source-id valid sub-category sub-category-id languages linked?] :as m} service-candidates]

               ^{:key sub-category-id}
               [layouts/expansion-panel
                {:label (if linked?
                          (str sub-category " (linkitetty)")
                          sub-category)
                 :label-icon (cond
                               linked? [:> Icon {:color "info"} "link"]
                               valid [:> Icon {:color "success"} "done"]
                               :else [:> Icon {:color "disabled"} "done"])}

                [:> Stack {:spacing 2}
                 [:> Tabs {:value service-details-tab
                           :indicatorColor "secondary"
                           :on-change #(==> [::events/select-service-details-tab %2])}
                  [:> Tab {:value "descriptions" :label "Syötä kuvaukset"}]
                  [:> Tab {:value "preview" :label "Esikatselu"}]]

                 ;; Enter descriptions form
                 (when (= "descriptions" service-details-tab)
                   [:> Stack {:spacing 2}

                    ;; Link to existing PTV service (collapsible) or show linked status
                    (when (seq services)
                      (if linked?
                        ;; Already linked - show linked service with unlink option
                        (let [linked-service (some #(when (= (:service-id %) (:service-id m)) %) services)]
                          [:> Stack {:direction "row" :spacing 1 :align-items "center"
                                     :sx #js{:p 1 :bgcolor "#e3f2fd" :borderRadius 1}}
                           [:> Icon {:color "info" :sx #js{:fontSize "1.2rem"}} "link"]
                           [:> Typography {:variant "body2" :flex 1}
                            (str (tr :ptv.wizard/linked-to-service) ": " (:label linked-service))]
                           [:> Button
                            {:size "small" :color "warning"
                             :on-click #(==> [::events/unlink-candidate-from-existing-service source-id])}
                            (tr :ptv.wizard/unlink-service)]])
                        ;; Not linked - show collapsible link option
                        (let [expanded? (contains? @link-expanded source-id)]
                          [:> Stack {:spacing 1}
                           [:> Button
                            {:size "small"
                             :variant "text"
                             :sx #js{:alignSelf "flex-start" :textTransform "none"}
                             :startIcon (r/as-element
                                          [:> Icon {:sx #js{:fontSize "1rem"}}
                                           (if expanded? "expand_less" "expand_more")])
                             :on-click #(swap! link-expanded (if expanded? disj conj) source-id)}
                            (tr :ptv.wizard/link-to-existing-service)]
                           (when expanded?
                             [controls/services-selector
                              {:options services
                               :multiple false
                               :value nil
                               :on-change #(when %
                                             (==> [::events/link-candidate-to-existing-service source-id %]))
                               :value-fn :service-id
                               :label (tr :ptv.wizard/select-existing-service)}])])))

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

                    ;; Summary (max 150 chars)
                    (let [summary-val (or (get-in m [:summary @selected-tab]) "")
                          summary-len (count summary-val)]
                      [text-fields/text-field
                       {:multiline true
                        :variant "outlined"
                        :on-change #(==> [::events/set-service-candidate-summary source-id @selected-tab %])
                        :label (tr :ptv/summary)
                        :value summary-val
                        :helperText (str summary-len "/150")
                        :error (> summary-len 150)}])

                    ;; Description (max 2500 chars)
                    (let [desc-val (or (get-in m [:description @selected-tab]) "")
                          desc-len (count desc-val)]
                      [text-fields/text-field
                       {:variant "outlined"
                        :rows 5
                        :multiline true
                        :on-change #(==> [::events/set-service-candidate-description source-id @selected-tab %])
                        :label (tr :ptv/description)
                        :value desc-val
                        :helperText (str desc-len "/2500")
                        :error (> desc-len 2500)}])

                    ;; User instruction / Toimintaohje (max 2500 chars)
                    (let [ui-val (or (get-in m [:user-instruction @selected-tab]) "")
                          ui-len (count ui-val)]
                      [text-fields/text-field
                       {:variant "outlined"
                        :rows 3
                        :multiline true
                        :on-change #(==> [::events/set-service-candidate-user-instruction source-id @selected-tab %])
                        :label (tr :ptv/user-instruction)
                        :value ui-val
                        :helperText (str ui-len "/2500")
                        :error (> ui-len 2500)}])

                    ;; Translate button (only when multiple languages)
                    (when (> (count languages) 1)
                      (let [from-lang @selected-tab
                            other-langs (disj (set (map keyword languages)) from-lang)
                            generating? (<== [::subs/generating-descriptions?])
                            has-text? (and (seq (get-in m [:summary from-lang]))
                                           (seq (get-in m [:description from-lang])))]
                        [:> Button
                         {:size "small"
                          :variant "text"
                          :disabled (or generating? (not has-text?))
                          :startIcon (r/as-element [:> Icon "translate"])
                          :sx #js{:alignSelf "flex-start" :textTransform "none"}
                          :on-click #(==> [::events/translate-service-candidate
                                           source-id from-lang other-langs])}
                         (str (tr :ptv.wizard/translate-to-other-langs) " ("
                              (str/join ", " (map (comp str/upper-case name) (sort other-langs)))
                              ")")]))])

                 (when (= "preview" service-details-tab)
                   [service-preview
                    {:source-id source-id
                     :sub-category-id sub-category-id}])]]))]]]]])))

(r/defc service-location-details
  [{:keys [org-id tr site lipas-id sync-enabled name-conflict service-ids selected-tab set-selected-tab service-channel-ids]}]
  (let [services @(rf/subscribe [::subs/services org-id])
        org-languages (<== [::subs/org-languages org-id])
        [selected-tab2 set-selected-tab2] (hooks/use-state "descriptions")]
    [:> AccordionDetails
     {}
     (r/as-element
       [:> Stack {:spacing 2}

        [:> Tabs {:value selected-tab2
                  :indicatorColor "secondary"
                  :on-change #(set-selected-tab2 %2)}
         [:> Tab {:value "descriptions" :label "Syötä kuvaukset"}]
         [:> Tab {:value "preview" :label "Esikatselu"}]]

        (when (= selected-tab2 "preview")
          [ptv-components/service-location-preview
           {:org-id org-id
            :lipas-id lipas-id}])

        (when (= selected-tab2 "descriptions")
          [:> Stack {:spacing 2}

          ;; Services selector
           [controls/services-selector
            {:options services
             :value service-ids
             :value-fn :service-id
             :on-change (fn [v]
                          (rf/dispatch [::events/select-services site v]))
             :label (tr :ptv/services)}]

          ;; Service channel selector

           [:span (when name-conflict {:style
                                       {:border "1px solid rgb(237, 108, 2)"
                                        :padding "1em"}})

            (when name-conflict
              [:> Stack
               [misc/icon-text
                {:icon "warning"
                 :icon-color "warning"
                 :text (tr :ptv.wizard/service-channel-name-conflict (:name site))}]

               [:> Typography
                {:style {:padding-left "1em" :margin-bottom "0"}
                 :variant "body2"}
                (tr :ptv.name-conflict/do-one-of-these)]

               [:ul
                [:li (tr :ptv.name-conflict/opt1)]
                [:li (tr :ptv.name-conflict/opt2)]
                [:li (tr :ptv.name-conflict/opt3)]
                #_[:li (tr :ptv.name-conflict/opt4)]]])

            (when name-conflict
              [:> Button
               {:on-click #(==> [::events/select-service-channels {:lipas-id lipas-id}
                                 [(:service-channel-id name-conflict)]])}
               (tr :ptv.wizard/attach-to-conflicting-service-channel)])

            ;; Only show service-channel selector when there's a conflict or existing link
            (when (or name-conflict (seq service-channel-ids))
              [service-channel-selector
               {:org-id org-id
                :value service-channel-ids
                :value-fn :service-channel-id
                :on-change #(==> [::events/select-service-channels site %])
                :label (tr :ptv/service-channel)}])

            (when-let [id (first (seq service-channel-ids))]
              [:> Button
               {:type "button"
                :on-click (fn [_e] (rf/dispatch [::events/load-ptv-texts lipas-id org-id id]))}
               "Lataa tekstit PTV:stä"])]

          ;; Per-facility AI description generation
           (let [generating? @(rf/subscribe [::subs/generating-descriptions?])]
             [:> Button
              {:variant "text"
               :size "small"
               :disabled generating?
               :color "secondary"
               :startIcon (r/as-element [:> Icon "auto_fix_high"])
               :on-click #(==> [::events/generate-descriptions lipas-id [] []])}
              (tr :ptv.actions/generate-with-ai)])

           [lang-selector
            {:value selected-tab
             :on-change (fn [_e v] (set-selected-tab v))
             :opts org-languages}]

          ;; Summary
           [text-fields/text-field
            {:multiline true
             :variant "outlined"
             :on-change #(==> [::events/set-summary site selected-tab %])
             :label (tr :ptv/summary)
             :value (get-in site [:summary selected-tab])}]

          ;; Description
           [text-fields/text-field
            {:variant "outlined"
             :rows 5
             :multiline true
             :on-change #(==> [::events/set-description site selected-tab %])
             :label (tr :ptv/description)
             :value (get-in site [:description selected-tab])}]

          ;; Disclaimer and enable switch
           (let [has-descriptions? (and (some-> site :summary :fi count (> 5))
                                        (some-> site :description :fi count (> 5)))]
             [checkboxes/switch
              {:label (if (:last-sync site)
                        (tr :ptv.actions/update-disclaimer)
                        (tr :ptv.actions/export-disclaimer))
               :value sync-enabled
               :disabled (not has-descriptions?)
               :on-change #(==> [::events/toggle-sync-enabled site %])}])])])]))

(r/defc service-location
  [{:keys [site sync-enabled name-conflict valid]
    :as props}]
  (let [tr (<== [:lipas.ui.subs/translator])
        sync-status (:sync-status site)
        last-sync (:last-sync site)]
    [:> Accordion
     {:defaultExpanded false
      :disableGutters true
      :square true
      ;; Much faster this way, only render the accordion content for open sites
      :slotProps #js {:transition #js {:unmountOnExit true}}
      :sx #js {:mb 2
               :backgroundColor (when (false? sync-enabled)
                                  mui/gray3)}}
     [:> AccordionSummary
      {:expandIcon (r/as-element [:> Icon "expand_more"])}
      [:> Stack {:direction "row" :spacing 1 :align-items "center" :flex 1 :sx #js {:mr 1}}
       ;; Validity icon
       [:> Typography
        {:sx #js {:mr 0.5}}
        (cond
          name-conflict [:> Icon {:color "warning"} "warning"]
          valid [:> Icon {:color "success"} "done"]
          :else [:> Icon {:color "disabled"} "done"])]
       ;; Name
       [:> Typography
        {:sx #js {:color "inherit"
                  :variant "button"
                  :flex 1}}
        (:name site)]
       ;; PTV sync status indicator
       (case sync-status
         :ok
         [:> Tooltip {:title (str (tr :ptv/synced-to-ptv) " " (some-> last-sync utils/->human-date-time-at-user-tz))}
          [:> Chip {:label "PTV"
                    :size "small"
                    :color "success"
                    :variant "outlined"
                    :icon (r/as-element [:> Sync {:fontSize "small"}])}]]
         :out-of-date
         [:> Tooltip {:title (tr :ptv/out-of-date)}
          [:> Chip {:label "PTV"
                    :size "small"
                    :color "warning"
                    :variant "outlined"
                    :icon (r/as-element [:> SyncProblem {:fontSize "small"}])}]]
         :content-drift
         [:> Tooltip {:title (tr :ptv/content-drift)}
          [:> Chip {:label "PTV"
                    :size "small"
                    :color "error"
                    :variant "outlined"
                    :icon (r/as-element [:> SyncProblem {:fontSize "small"}])}]]
         ;; :not-synced or nil
         (when sync-enabled
           [:> Tooltip {:title (tr :ptv/not-yet-synced)}
            [:> Chip {:label "PTV"
                      :size "small"
                      :variant "outlined"
                      :icon (r/as-element [:> SyncDisabled {:fontSize "small"}])}]]))]]

     [service-location-details props]]))

(r/defc integrate-service-locations
  [_props]
  (let [tr (<== [:lipas.ui.subs/translator])
        org-id (<== [::subs/selected-ptv-org-id])
        sports-sites (<== [::subs/sports-sites-wizard org-id])
        setup-done? (<== [::subs/sports-site-setup-done org-id])
        sports-sites-count (count sports-sites)
        sports-sites-count-sync (count (filter :sync-enabled sports-sites))
        sports-sites-filter (<== [::subs/sports-sites-filter])

        [selected-tab set-selected-tab] (hooks/use-state :fi)

        ;; TODO: Rename this so service-location-generation progress can also be
        ;; added to this level
        {:keys [in-progress?
                processed-lipas-ids
                processed-count
                total-count
                processed-percent
                halt?] :as m}
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
        (let [has-any-descriptions? (some (fn [s] (some-> s :summary :fi count (> 5))) sports-sites)]
          [:> Button
           {:variant "outlined"
            :disabled in-progress?
            :color "secondary"
            :startIcon (r/as-element [:> Icon (if has-any-descriptions? "refresh" "play_arrow")])
            :on-click #(==> [::events/generate-all-descriptions sports-sites])}
           (if has-any-descriptions?
             (tr :ptv.wizard/regenerate-descriptions)
             (tr :ptv.wizard/generate-descriptions))])

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
         (str "Valittuna " sports-sites-count-sync "/" sports-sites-count " liikuntapaikkaa")]

        ;; Export to PTV button
        [:> Button
         {:variant "outlined"
          :disabled (not (every? true? (map :valid sports-sites)))
          :color "primary"
          :startIcon (r/as-element [:> Icon "ios_share"])
          :on-click #(==> [::events/create-all-ptv-service-locations sports-sites])}
         (tr :ptv.wizard/export-service-locations-to-ptv)]

        (let [{:keys [in-progress?
                      processed-count
                      total-count
                      processed-percent
                      halt?]}
              (<== [::subs/service-location-creation-progress])]

          [:<>
           ;; TODO: Cancel?

           (when in-progress?
             [:> Stack {:direction "row" :spacing 2 :align-items "center"}
              [:> CircularProgress {:variant "indeterminate" :value processed-percent}]
              [:> Typography (str processed-count "/" total-count)]])

           (when halt?
             [:> Alert {:severity "error" :sx #js{:mt 2}}
              [:> AlertTitle (tr :notifications/save-failed)]
              (tr :ptv.wizard/export-error-try-again)])])]]

      ;; Results

      [:> Grid {:item true :xs 12 :lg 8}
       [:> Stack {:spacing 4}

        [:> Stack {:spacing 1 :sx #js{:pb 2}}

         [:> Typography {:variant "h6"}
          (tr :ptv/sports-sites)]

         [:> Typography {:variant "body2"}
          "Kytke integraatio päälle valitsemalla liikuntapaikka listasta ja siirtämällä liukukytkin ON-asentoon – harmaa väri osoittaa, että integraatio on pois päältä."]

         [:> Typography {:variant "body2"}
          (str "Valittuna " sports-sites-count-sync "/" sports-sites-count " liikuntapaikkaa")]]

        [:> Stack
         (for [{:keys [lipas-id valid name-conflict sync-enabled service-ids service-channel-ids service-name] :as site} sports-sites]
           ^{:key lipas-id}
           [service-location
            {:tr tr
             :site site
             :org-id org-id
             :lipas-id lipas-id
             :name-conflict name-conflict
             :sync-enabled sync-enabled
             :valid valid
             :service-ids service-ids
             :selected-tab selected-tab
             :set-selected-tab set-selected-tab
             :service-channel-ids service-channel-ids}])]]]]]))

(def ^:private service-channels-threshold 5)

(defn service-channels-list
  [{:keys [tr service ptv-base]}]
  (r/with-let [show-all? (r/atom false)]
    (let [channels (sort-by :name (:service-channels service))
          total (count channels)
          visible (if @show-all? channels (take service-channels-threshold channels))
          truncated? (and (not @show-all?) (> total service-channels-threshold))]
      [:> Stack {:spacing 0.5}
       [:> Typography {:variant "caption" :color "text.secondary"}
        (str (tr :ptv/service-channels) " (" total ")")]
       (for [sc visible]
         ^{:key (:id sc)}
         [:> Link {:href (str ptv-base "/channels/serviceLocation/" (:id sc))
                   :target "_blank"
                   :variant "body2"}
          (:name sc)])
       (when truncated?
         [:> Button {:size "small" :variant "text"
                     :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                     :on-click #(reset! show-all? true)}
          (str (tr :actions/show-all) " (" total ")")])
       (when @show-all?
         [:> Button {:size "small" :variant "text"
                     :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                     :on-click #(reset! show-all? false)}
          (tr :actions/show-less)])])))

(defn service-panel-left
  [{:keys [tr org-id service data ptv-base has-local-edits?]}]
  (let [service-id (:service-id service)
        source-id (:source-id service)]
    [:> Stack {:spacing 2}

     ;; 1. Link to PTV
     [:> Stack {:spacing 0.5}
      [:> Typography {:variant "caption" :color "text.secondary"}
       (str (tr :ptv/service) " PTV:ssä")]
      [:> Link {:href (str ptv-base "/service/" service-id)
                :target "_blank"
                :variant "body2"}
       (:label service)]]

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

     ;; 4. Linked service locations
     (when (seq (:service-channels service))
       [service-channels-list {:tr tr :service service :ptv-base ptv-base}])

     ;; Sync button
     [:> Button
      {:variant "contained"
       :color "secondary"
       :disabled (not has-local-edits?)
       :size "small"
       :sx #js {:textTransform "none"}
       :startIcon (r/as-element [:> Icon "sync"])
       :on-click (fn [_e]
                   (rf/dispatch [::events/create-ptv-service org-id source-id data
                                 [[:dispatch [:lipas.ui.events/set-active-notification
                                              {:message (tr :notifications/save-success)
                                               :success? true}]]]
                                 []]))}
      (tr :ptv.actions/sync-now)]

     ;; Last modified
     [:> Typography {:variant "caption" :color "text.secondary"}
      (str (tr :general/last-modified) " " (:last-modified-human service))]]))

(defn service-panel-right
  [{:keys [tr source-id service descriptions org-languages]}]
  (r/with-let [selected-tab (r/atom :fi)]
    (let [summary-data (merge (:summary service) (:summary descriptions))
          description-data (merge (:description service) (:description descriptions))
          user-instruction-data (merge (:user-instruction service) (:user-instruction descriptions))]
      [:> Stack {:spacing 2}

       [controls/lang-selector
        {:value @selected-tab
         :on-change #(reset! selected-tab %)
         :enabled-languages (set org-languages)}]

       ;; Summary
       (let [v (or (get summary-data @selected-tab) "")]
         [text-fields/text-field
          {:on-change #(==> [::events/set-service-candidate-summary source-id @selected-tab %])
           :multiline true
           :variant "outlined"
           :label (tr :ptv/summary)
           :value v
           :helperText (str (count v) "/150")
           :error (> (count v) 150)}])

       ;; Description
       (let [v (or (get description-data @selected-tab) "")]
         [text-fields/text-field
          {:on-change #(==> [::events/set-service-candidate-description source-id @selected-tab %])
           :variant "outlined"
           :rows 5
           :multiline true
           :label (tr :ptv/description)
           :value v
           :helperText (str (count v) "/2500")
           :error (> (count v) 2500)}])

       ;; User instruction
       (let [v (or (get user-instruction-data @selected-tab) "")]
         [text-fields/text-field
          {:on-change #(==> [::events/set-service-candidate-user-instruction source-id @selected-tab %])
           :variant "outlined"
           :rows 3
           :multiline true
           :label (tr :ptv/user-instruction)
           :value v
           :helperText (str (count v) "/2500")
           :error (> (count v) 2500)}])

       ;; Translate button
       (when (> (count org-languages) 1)
         (let [from-lang @selected-tab
               other-langs (disj (set (map keyword org-languages)) from-lang)
               has-text? (and (seq (get summary-data from-lang))
                              (seq (get description-data from-lang)))]
           [:> Button
            {:size "small"
             :variant "text"
             :disabled (not has-text?)
             :startIcon (r/as-element [:> Icon "translate"])
             :sx #js {:alignSelf "flex-start" :textTransform "none"}
             :on-click #(==> [::events/translate-service-candidate-with-texts
                              source-id from-lang other-langs
                              {:summary (get summary-data from-lang)
                               :description (get description-data from-lang)
                               :user-instruction (get user-instruction-data from-lang)}])}
            (str (tr :ptv.wizard/translate-to-other-langs) " ("
                 (str/join ", " (map (comp str/upper-case name) (sort other-langs)))
                 ")")]))])))

(defn service-panel
  [{:keys [org-id service descriptions]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        source-id (:source-id service)
        org-languages (<== [::subs/org-languages org-id])
        ptv-base (if (utils/prod?)
                   "https://palvelutietovaranto.suomi.fi"
                   "https://palvelutietovaranto.trn.suomi.fi")
        current-texts {:summary (merge (:summary service) (:summary descriptions))
                       :description (merge (:description service) (:description descriptions))
                       :user-instruction (merge (:user-instruction service) (:user-instruction descriptions))}
        ptv-texts {:summary (:summary service)
                   :description (:description service)
                   :user-instruction (:user-instruction service)}
        has-local-edits? (not (ptv-data/texts-match? current-texts ptv-texts))
        data (merge {:org-id org-id
                     :source-id source-id
                     :city-codes (:city-codes service)
                     :sub-category-id (ptv-data/parse-service-source-id source-id)
                     :languages org-languages}
                    current-texts)
        lipas-managed? (some-> source-id (str/starts-with? "lipas-"))]
    [layouts/expansion-panel
     {:label (:label service)
      :label-icon (when lipas-managed?
                    (if has-local-edits?
                      [:> Tooltip {:title (tr :ptv/out-of-date)}
                       [:> Chip {:label "PTV"
                                 :size "small"
                                 :color "warning"
                                 :variant "outlined"
                                 :icon (r/as-element [:> SyncProblem {:fontSize "small"}])}]]
                      [:> Tooltip {:title (str (tr :ptv/synced-to-ptv) " " (:last-modified-human service))}
                       [:> Chip {:label "PTV"
                                 :size "small"
                                 :color "success"
                                 :variant "outlined"
                                 :icon (r/as-element [:> Sync {:fontSize "small"}])}]]))}
     [:> Grid {:container true :spacing 3}
      [:> Grid {:item true :xs 12 :md 4}
       [service-panel-left {:tr tr :org-id org-id :service service
                            :data data :ptv-base ptv-base
                            :has-local-edits? has-local-edits?}]]
      [:> Grid {:item true :xs 12 :md 8}
       [service-panel-right {:tr tr :source-id source-id :service service
                             :descriptions descriptions
                             :org-languages org-languages}]]]]))

(defn services
  []
  (let [tr (<== [:lipas.ui.subs/translator])
        services-filter (<== [::subs/services-filter])
        org-id (<== [::subs/selected-ptv-org-id])
        services (<== [::subs/services-filtered org-id])
        descriptions (<== [::subs/service-candidate-descriptions org-id])]
    [:> Paper

     [:> Typography {:variant "body2" :sx #js{:p 2 :pb 0}}
      (tr :ptv.service/services-explanation)]

     ;; Filter checkbox
     [checkboxes/checkbox
      {:label (tr :ptv.service/show-only-lipas-managed)
       :value (= "lipas-managed" services-filter)
       :on-change #(==> [::events/toggle-services-filter])}]

     ;; Services list
     (if (seq services)
       (doall
         (for [service services]
           ^{:key (:service-id service)}
           [service-panel
            {:org-id org-id
             :service service
             :descriptions (get descriptions (:source-id service))}]))

       ;; Empty state
       [:> Stack {:spacing 2 :sx #js {:p 2}}
        [:> Alert {:severity "info"}
         (if (= "lipas-managed" services-filter)
           (tr :ptv.service/no-lipas-services)
           (tr :ptv.service/no-services))]
        (when (= "lipas-managed" services-filter)
          [:> Typography {:variant "body2"}
           (tr :ptv.service/use-wizard-to-create)])])]))

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
        (str "1. Valitse liikuntapaikat")]]
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

(defn site-list-item
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

    [:div {:key (:lipas-id site)}
     [:> Paper
      {:sx #js{:p 2
               :mb 2
               :border (when selected? "2px solid")
               :borderColor (when selected? "primary.main")
               :cursor "pointer"}
       :elevation (if selected? 3 1)
       :onClick #(on-select site)}

      [:> Stack {:direction "row" :spacing 2 :alignItems "center"}

         ;; Status indicator
       [:> Avatar
        {:sx #js{:bgcolor status-color
                 :color "white"
                 :width 10
                 :height 10}}]

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
           {:variant "caption" :color "text.secondary"}
           (str "Last audit: " last-audit-date)
           (when summary-status
             (str ", Summary: " summary-status))
           (when desc-status
             (str ", Description: " desc-status))])]]]]))

(defn dialog
  [{:keys [tr]}]
  (let [open? (<== [::subs/dialog-open?])
        selected-tab (<== [::subs/selected-tab])
        loading? (<== [::subs/loading-from-ptv?])
        ptv-org-id (<== [::subs/selected-ptv-org-id])
        org-data (<== [::subs/selected-org-data ptv-org-id])
        sites (<== [::subs/sports-sites ptv-org-id])

        has-manage-privilege? (<== [::subs/has-manage-privilege?])
        has-audit-privilege? (<== [::subs/has-audit-privilege?])

        on-close #(==> [::events/close-dialog])]

    [:> Dialog
     {:open open?
      :fullScreen true
      :max-width "xl"}

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

      [org-selector {:label (tr :ptv.actions/select-org)}]

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
            [:> Tab {:value "audit" :label (tr :ptv.audit/tab-label)}])]

         (when (and (= selected-tab "wizard") has-manage-privilege?)
           [wizard])

         (when (and (= selected-tab "services") has-manage-privilege?)
           [services])

         (when (and (= selected-tab "sports-sites") has-manage-privilege?)
           [table])

         (when (and (= selected-tab "audit") has-audit-privilege?)
           [audit/main-view {:tr tr}])])]]))
