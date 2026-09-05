(ns lipas.ui.ptv.components
  "Shared PTV UI components to avoid circular dependencies"
  (:require ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
            ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.ptv.controls :as controls]
            [lipas.ui.ptv.diff :as ptv-diff]
            [lipas.ui.ptv.events :as events]
            [lipas.ui.ptv.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

(defn writing-guidance
  "Collapsed accordion showing authoring guidance for one PTV text field.
   Shared by the Service panels (per sub-category, from
   lipas.data.ptv-service-guidance) and the Service Location site view
   (per type-code group, from lipas.data.ptv-site-guidance).

   Props:
     :title       - accordion header, e.g. \"Mitä kuvaukseen kannattaa kirjoittaa?\"
     :text        - guidance body; newlines are preserved
     :avoid       - optional short \"what not to write\" line under the body
     :avoid-label - label for that line, e.g. \"Vältä:\"

   Renders nothing when :text is blank, so callers can pass a lookup that
   may miss (a type or sub-category with no guidance)."
  [{:keys [title text avoid avoid-label]}]
  (when-not (str/blank? text)
    [:> Accordion {:disableGutters true :elevation 0 :variant "outlined"}
     [:> AccordionSummary {:expandIcon (r/as-element [:> Icon "expand_more"])}
      [:> Stack {:direction "row" :spacing 1 :align-items "center"}
       [:> Icon {:fontSize "small" :color "action"} "help_outline"]
       [:> Typography {:variant "body2"} title]]]
     [:> AccordionDetails
      [:> Stack {:spacing 1}
       [:> Typography {:variant "body2" :sx #js {:whiteSpace "pre-line"}}
        text]
       (when-not (str/blank? avoid)
         [:> Typography {:variant "body2" :color "text.secondary"}
          [:strong (str avoid-label " ")]
          avoid])]]]))

(defn audit-feedback-alert
  "Auditor feedback for one field in the municipality-facing views.
   Severity follows the whose-move state so a red changes-requested alert
   resolves once the municipality has edited the text (:fixed), and an
   approval whose content changed afterwards shows as a warning (:stale).
   Props: :tr, :field-audit (full audit field map incl. :audited-content
   and optionally a precomputed :state), :current-content (localized map
   to compare the verdict snapshot against; omit when :state is given)."
  [{:keys [tr field-audit current-content]}]
  (let [{:keys [status feedback]} field-audit
        state (or (:state field-audit)
                  (when field-audit
                    (ptv-data/audit-field-state field-audit current-content)))
        [severity title] (case state
                           :changes-requested
                           ["error" (tr :ptv.audit/auditor-feedback-changes-requested)]

                           :approved
                           ["success" (tr :ptv.audit/auditor-feedback-approved)]

                           :fixed
                           ["info" (tr :ptv.audit/auditor-feedback-fixed)]

                           :stale
                           ["warning" (tr :ptv.audit/auditor-feedback-stale)]

                           ;; no state (no verdict/content info): raw status
                           (case status
                             "changes-requested" ["error" (tr :ptv.audit/auditor-feedback-changes-requested)]
                             "approved" ["success" (tr :ptv.audit/auditor-feedback-approved)]
                             ["warning" (tr :ptv.audit/auditor-feedback)]))]
    (when (and feedback (not (str/blank? feedback)))
      [:> Alert
       {:severity severity
        :variant "outlined"
        :sx #js {:mt 1 :mb 1}}
       [:> AlertTitle title]
       feedback])))

(defn ptv-link-field
  "Shows PTV items as links with an edit button to switch to selector mode.
   When an item has no :url, renders as plain text instead of a link.
   Set :can-edit? false to hide the edit affordance (e.g. single-org case).
   Props: :label, :items [{:id :name :url}], :editing?, :on-edit, :on-cancel,
          :selector-component, :tooltip, :tr, :can-edit? (default true)"
  [{:keys [label items editing? on-edit on-cancel selector-component tooltip tr can-edit?]
    :or {can-edit? true}}]
  (if (and (seq items) (not editing?))
    [:> Stack {:spacing 0.5}
     [:> Stack {:direction "row" :align-items "center" :spacing 0.5}
      [:> Typography {:variant "caption" :color "text.secondary"} label]
      (when can-edit?
        [:> Tooltip {:title (or tooltip "")}
         [:> IconButton {:size "small" :on-click on-edit
                         :sx #js {:p 0}}
          [:> Icon {:sx #js {:fontSize "1rem"}} "edit"]]])]
     (for [{:keys [id name url]} items]
       (if url
         ^{:key id} [:> Link {:href url :target "_blank" :variant "body2"} (or name id)]
         ^{:key id} [:> Typography {:variant "body2"} (or name id)]))]
    [:> Stack {:spacing 0.5}
     selector-component
     (when (and editing? (seq items))
       [:> Button {:size "small" :variant "text"
                   :sx #js {:textTransform "none" :alignSelf "flex-start" :p 0}
                   :on-click on-cancel}
        (tr :actions/cancel)])]))

(defn- drift-field-label
  [tr {:keys [field type language]}]
  (let [field-label (case field
                      :name (tr :ptv.drift/field-name)
                      :marketing-name (tr :ptv.drift/field-marketing-name)
                      :summary (tr :ptv.drift/field-summary)
                      :description (tr :ptv.drift/field-description)
                      :services (tr :ptv.drift/field-services)
                      (str field))]
    (cond
      (= field :services) field-label
      (= type "AlternativeName") field-label
      language (str field-label " (" (str/upper-case language) ")")
      :else field-label)))

(defn- drift-cell
  [tr value]
  (if (or (nil? value) (and (string? value) (str/blank? value)))
    [:> Typography {:variant "body2" :sx #js {:color "text.disabled" :fontStyle "italic"}}
     (tr :ptv.drift/empty)]
    [:> Typography {:variant "body2" :sx #js {:whiteSpace "pre-wrap"}} value]))

(def ^:private diff-styles
  {;; LIPAS column: highlight tokens present only in LIPAS — these are
   ;; what's about to be pushed into PTV. Green underline.
   :lipas {:background "#e6f4ea"
           :font-weight 500
           :text-decoration "underline"
           :text-decoration-color "#137333"}
   ;; PTV column: highlight tokens present only in PTV — these are what
   ;; will be erased on the next sync. Red strikethrough.
   :ptv {:background "#fce8e6"
         :text-decoration "line-through"
         :text-decoration-color "#c5221f"}})

(defn- diffed-cell
  "Render one column of a side-by-side word diff. `ops` is the result
   of `(ptv-diff/diff lipas-text ptv-text)` already coalesced. `side` is
   :lipas or :ptv and determines which ops are skipped (the other side's
   additions) and which are highlighted (this side's additions)."
  [tr ops side]
  (let [skip-op (case side :lipas :added :ptv :removed)
        highlight-op (case side :lipas :removed :ptv :added)
        highlight-style (clj->js (get diff-styles side))
        any-content? (some (fn [[op _]] (or (= op :equal) (= op highlight-op))) ops)]
    (if any-content?
      [:> Typography {:variant "body2" :sx #js {:whiteSpace "pre-wrap"}}
       (into [:<>]
             (for [[i [op v]] (map-indexed vector ops)
                   :when (not= op skip-op)]
               (if (= op highlight-op)
                 ^{:key i} [:span {:style highlight-style} v]
                 ^{:key i} [:span v])))]
      [:> Typography {:variant "body2" :sx #js {:color "text.disabled" :fontStyle "italic"}}
       (tr :ptv.drift/empty)])))

(def ^:private text-fields
  "Drift fields where word-level diff highlighting helps readability."
  #{:name :marketing-name :summary :description})

(defn- drift-services-cell
  [tr entries]
  (if (seq entries)
    [:> Typography {:variant "body2"}
     (str/join ", " (map (fn [{:keys [id name]}] (or name id)) entries))]
    [:> Typography {:variant "body2" :sx #js {:color "text.disabled" :fontStyle "italic"}}
     (tr :ptv.drift/empty)]))

(r/defc drift-panel
  "Renders a per-field diff between the LIPAS-side value (what will be
   pushed on the next sync) and the PTV-side value (what the kunta is
   currently seeing in PTV). Only shown when the site has drift."
  [{:keys [drift-fields tr]}]
  (when (seq drift-fields)
    [:> Alert {:severity "warning"
               :icon false
               :sx #js {:my 2}}
     [:> AlertTitle (tr :ptv.drift/title)]
     [:> Typography {:variant "body2" :sx #js {:mb 1}}
      (tr :ptv.drift/warning)]
     [:> Typography {:variant "body2" :sx #js {:mb 2}}
      (tr :ptv.drift/instruction)]
     [:> Table {:size "small" :sx #js {:bgcolor "background.paper"}}
      [:> TableHead
       [:> TableRow
        [:> TableCell {:sx #js {:fontWeight 600}} (tr :ptv.drift/field-header)]
        [:> TableCell {:sx #js {:fontWeight 600}} (tr :ptv.drift/lipas-header)]
        [:> TableCell {:sx #js {:fontWeight 600}} (tr :ptv.drift/ptv-header)]]]
      [:> TableBody
       (for [{:keys [field lipas ptv added removed] :as entry}
             (sort-by (juxt :field :language) drift-fields)]
         ^{:key (str field "-" (:language entry) "-" (:type entry))}
         [:> TableRow
          [:> TableCell {:sx #js {:verticalAlign "top"}}
           [:> Typography {:variant "body2" :sx #js {:fontWeight 500}}
            (drift-field-label tr entry)]]
          (cond
            (= field :services)
            [:<>
             [:> TableCell {:sx #js {:verticalAlign "top"}}
              [:> Typography {:variant "caption" :sx #js {:color "text.secondary" :display "block"}}
               (tr :ptv.drift/services-lipas-has)]
              [drift-services-cell tr lipas]
              (when (seq removed)
                [:> Typography {:variant "caption" :sx #js {:color "warning.dark" :display "block" :mt 1}}
                 (tr :ptv.drift/services-will-remain)])]
             [:> TableCell {:sx #js {:verticalAlign "top"}}
              [:> Typography {:variant "caption" :sx #js {:color "text.secondary" :display "block"}}
               (tr :ptv.drift/services-ptv-has)]
              [drift-services-cell tr ptv]
              (when (seq added)
                [:> Typography {:variant "caption" :sx #js {:color "warning.dark" :display "block" :mt 1}}
                 (tr :ptv.drift/services-will-be-removed)])]]

            (contains? text-fields field)
            (let [ops (ptv-diff/coalesce (ptv-diff/diff lipas ptv))]
              [:<>
               [:> TableCell {:sx #js {:verticalAlign "top" :width "40%"}}
                [diffed-cell tr ops :lipas]]
               [:> TableCell {:sx #js {:verticalAlign "top" :width "40%"}}
                [diffed-cell tr ops :ptv]]])

            :else
            [:<>
             [:> TableCell {:sx #js {:verticalAlign "top" :width "40%"}}
              [drift-cell tr lipas]]
             [:> TableCell {:sx #js {:verticalAlign "top" :width "40%"}}
              [drift-cell tr ptv]]])])]]]))

(r/defc service-location-preview
  "Preview component showing how a sports site will appear in PTV as a service location"
  [{:keys [org-id lipas-id]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        preview @(rf/subscribe [::subs/service-location-preview org-id lipas-id])
        sports-sites @(rf/subscribe [::subs/sports-sites org-id])
        site-data (some #(when (= lipas-id (:lipas-id %)) %) sports-sites)
        synced? (= :ok (:sync-status site-data))
        [expanded? set-expanded] (hooks/use-state false)

        row (fn [{:keys [label value tooltip]}]
              [:> Tooltip {:title (or tooltip (tr :ptv.preview/tooltip-missing))}
               [:> TableRow
                [:> TableCell [:> Typography {:variant "caption"} label]]
                [:> TableCell [:> Typography value]]]])
        join (fn [coll] (when (seq coll) (str/join ", " coll)))
        get-desc (fn [type lang] (or (->> preview
                                          :serviceChannelDescriptions
                                          (filter (fn [m] (and (= (:type m) type)
                                                               (= (:language m) lang))))
                                          (map :value)
                                          join)
                                     "-"))
        get-name (fn [type lang] (or (->> preview :serviceChannelNames
                                          (filter (fn [m] (and
                                                            (= (:language m) lang)
                                                            (= (:type m) type))))
                                          (map :value)
                                          join)
                                     "-"))

        tt-summary (tr :ptv.preview/tt-summary)
        tt-description (tr :ptv.preview/tt-service-location-description)
        lang-disclaimer (tr :ptv.preview/lang-disclaimer)]

    [:> Stack {:spacing 2}
     ;; Collapsible header with expand/collapse button
     [:> Paper
      {:sx #js{:p 2 :bgcolor mui/gray3 :cursor "pointer"}
       :onClick #(set-expanded (not expanded?))}
      [:> Stack
       {:direction "row"
        :alignItems "center"
        :justifyContent "space-between"}
       [:> Typography
        {:variant "subtitle2" :fontWeight "medium"}
        (tr :ptv.preview/integration-content)]
       [:> IconButton
        {:size "small"
         :sx #js{:transform (if expanded? "rotate(180deg)" "rotate(0deg)")
                 :transition "transform 0.2s"}}
        [:> ExpandMoreIcon]]]]

     ;; Collapsible content
     [:> Collapse {:in expanded? :timeout "auto" :unmountOnExit true}
      [:> Paper {:sx #js{:p 2 :bgcolor mui/gray3}}
       [:> Typography
        {:variant "body2" :mb 2}
        (tr :ptv.preview/service-location-intro)]]

      [:> Table {:variant "dense"}
       [:> TableHead
        [:> TableRow
         [:> TableCell (tr :ptv.preview/record)]
         [:> TableCell (tr :ptv.preview/value)]]]

       [:> TableBody
        (row {:label (tr :ptv.preview/status)
              :value (if synced?
                       (:publishingStatus preview)
                       (str (:publishingStatus preview) (tr :ptv.preview/not-yet-exported)))
              :tooltip (tr :ptv.preview/tt-status)})

        (row {:label (tr :ptv.preview/languages)
              :value (join (:languages preview))
              :tooltip (tr :ptv.preview/tt-languages)})

        (row {:label (tr :ptv.preview/name-fi)
              :value (get-name "Name" "fi")
              :tooltip (tr :ptv.preview/tt-site-name-fi)})

        (row {:label (tr :ptv.preview/name-se)
              :value (get-name "Name" "sv")
              :tooltip (str (tr :ptv.preview/tt-site-name-se) " " lang-disclaimer)})

        (row {:label (tr :ptv.preview/name-en)
              :value (get-name "Name" "en")
              :tooltip (str (tr :ptv.preview/tt-site-name-en) " " lang-disclaimer)})

        (row {:label (tr :ptv.preview/alternative-name)
              :value (get-name "AlternativeName" "fi")
              :tooltip (tr :ptv.preview/tt-alternative-name)})

        (row {:label (tr :ptv.preview/primary-name-type)
              :value (->> preview :displayNameType first :type)
              :tooltip (tr :ptv.preview/tt-primary-name-type)})

        (row {:label (tr :ptv.preview/country)
              :value (->> preview :addresses first :country)
              :tooltip (tr :ptv.preview/tt-country)})

        (row {:label (tr :ptv.preview/street-address)
              :value (-> preview :addresses first :streetAddress :street first :value)
              :tooltip (tr :ptv.preview/tt-street-address)})

        (row {:label (tr :ptv.preview/postal-code)
              :value (-> preview :addresses first :streetAddress :postalCode)
              :tooltip (tr :ptv.preview/tt-postal-code)})

        (row {:label (tr :ptv.preview/coordinates)
              :value (str "(E) "
                          (-> preview :addresses first :streetAddress :longitude)
                          " (N) "
                          (-> preview :addresses first :streetAddress :latitude))
              :tooltip (tr :ptv.preview/tt-coordinates)})

        (row {:label (tr :ptv.preview/emails)
              :value (->> preview :emails (map :value) join)
              :tooltip (tr :ptv.preview/tt-emails)})

        (row {:label (tr :ptv.preview/web-pages)
              :value (->> preview :webPages (map :url) join)
              :tooltip (tr :ptv.preview/tt-web-pages)})

        (row {:label (tr :ptv.preview/phone-numbers)
              :value (->> preview
                          :phoneNumbers
                          (map (fn [{:keys [number prefixNumber isFinnishServiceNumber]}]
                                 (str prefixNumber " " number
                                      (when isFinnishServiceNumber
                                        (tr :ptv.preview/finnish-service-number)))))
                          join)
              :tooltip (tr :ptv.preview/tt-phone-numbers)})

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
              :tooltip (str tt-description " " lang-disclaimer)})

        (row {:label (tr :ptv.preview/organization-id)
              :value (:organizationId preview)
              :tooltip (tr :ptv.preview/tt-organization-id)})

        (row {:label (tr :ptv.preview/service-ids)
              :value (join (:services preview))
              :tooltip (tr :ptv.preview/tt-service-ids)})]]]]))

(defn add-service-create-form
  "Form for creating a new PTV service for a sub-category.
   Props:
   - :org-id (required) — PTV org id
   - :on-cancel (required) — close handler
   - :locked-sub-category-id (optional) — if set, pre-select this sub-category
     and hide the selector. Used from site context where the site type dictates
     which service is needed.
   - :lipas-id (optional) — when set, the created service-id is auto-added to
     that site's :ptv :service-ids on success."
  [{:keys [org-id on-cancel locked-sub-category-id lipas-id]}]
  (r/with-let [source-id (r/atom (when locked-sub-category-id
                                   (ptv-data/->service-source-id org-id locked-sub-category-id)))
               selected-tab (r/atom :fi)
               editing-sub-category? (r/atom false)
               _seed-locked
               (when locked-sub-category-id
                 (let [sid (ptv-data/->service-source-id org-id locked-sub-category-id)
                       sub-cat (get types/sub-categories locked-sub-category-id)]
                   (rf/dispatch [::events/set-manual-services org-id [sid]
                                 [{:source-id sid
                                   :sub-category-id locked-sub-category-id
                                   :sub-category (-> sub-cat :name :fi)}]])))]
    (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
          missing-subcategories @(rf/subscribe [::subs/missing-subcategories org-id])
          org-languages @(rf/subscribe [::subs/org-languages org-id])
          descriptions @(rf/subscribe [::subs/service-candidate-descriptions org-id])
          desc (when @source-id (get descriptions @source-id))
          generating? @(rf/subscribe [::subs/generating-service-descriptions? @source-id])
          syncing? (when @source-id @(rf/subscribe [::subs/syncing-service? @source-id]))
          valid? (and @source-id
                      (some-> desc :summary :fi count (> 5))
                      (some-> desc :description :fi count (> 5))
                      (some-> desc :user-instruction :fi count (> 5)))]
      [:> Stack {:spacing 2}
       [:> Typography {:variant "subtitle1" :fontWeight "medium"}
        (tr :ptv.service/create-new)]
       [:> Grid {:container true :spacing 3}

        ;; Left column: selector and action buttons
        [:> Grid {:size #js {:xs 12 :md 5}}
         [:> Stack {:spacing 2}
          (let [selector [controls/services-selector
                          {:options missing-subcategories
                           :multiple false
                           :value @source-id
                           :value-fn :source-id
                           :sx #js {:minWidth 320}
                           :on-change (fn [v]
                                        (reset! source-id v)
                                        (when v
                                          (rf/dispatch [::events/set-manual-services org-id [v] missing-subcategories]))
                                        (reset! editing-sub-category? false))
                           :label (tr :ptv.service/select-sub-category)}]]
            (if locked-sub-category-id
              ;; Site context: pre-filled from the site's type. Edit button lets
              ;; the user swap in a different sub-category if needed.
              [ptv-link-field
               {:tr tr
                :label (tr :ptv.service/select-sub-category)
                :items [{:id (or @source-id locked-sub-category-id)
                         :name (get-in types/sub-categories
                                       [(ptv-data/parse-service-source-id (or @source-id ""))
                                        :name (tr)]
                                       (get-in types/sub-categories
                                               [locked-sub-category-id :name (tr)]
                                               (get-in types/sub-categories
                                                       [locked-sub-category-id :name :fi])))}]
                :editing? @editing-sub-category?
                :on-edit #(reset! editing-sub-category? true)
                :on-cancel #(reset! editing-sub-category? false)
                :selector-component selector}]
              selector))

          (when @source-id
            [:<>
             ;; AI generate + translate buttons
             (let [from-lang @selected-tab
                   other-langs (disj (set (map keyword org-languages)) from-lang)
                   has-text? (and (seq (get-in desc [:summary from-lang]))
                                  (seq (get-in desc [:description from-lang])))]
               [:> Stack {:direction "row" :spacing 1 :flex-wrap "wrap" :align-items "flex-start"}
                [:> Button
                 {:variant "outlined" :size "small" :disabled generating?
                  :sx #js {:textTransform "none"}
                  :startIcon (r/as-element
                               (if generating?
                                 [:> CircularProgress {:size 16 :color "inherit"}]
                                 [:> Icon "auto_fix_high"]))
                  :on-click #(rf/dispatch [::events/generate-service-descriptions
                                           org-id @source-id nil [] []])}
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
                      :on-click #(rf/dispatch [::events/translate-service-candidate-with-texts
                                               @source-id from-lang other-langs
                                               {:summary (get-in desc [:summary from-lang])
                                                :description (get-in desc [:description from-lang])
                                                :user-instruction (get-in desc [:user-instruction from-lang])}])}
                     (str (tr :ptv.wizard/translate-to-other-langs) " ("
                          (str/join ", " (map (comp str/upper-case name) (sort other-langs))) ")")]]])])

             [:> Button
              {:variant "contained" :color "secondary" :size "small" :full-width true
               :disabled (or syncing? (not valid?))
               :sx #js {:textTransform "none"}
               :startIcon (r/as-element
                            (if syncing?
                              [:> CircularProgress {:size 16 :color "inherit"}]
                              [:> Icon "ios_share"]))
               :on-click #(let [data (merge {:org-id org-id
                                             :source-id @source-id
                                             :sub-category-id (ptv-data/parse-service-source-id @source-id)
                                             :languages org-languages}
                                            desc)]
                            (rf/dispatch [::events/create-ptv-service
                                          org-id @source-id data [] [] lipas-id])
                            (on-cancel))}
              (tr :ptv.wizard/export-services-to-ptv)]])

          [:> Button
           {:size "small" :variant "text"
            :sx #js {:textTransform "none" :alignSelf "flex-start"}
            :on-click on-cancel}
           (tr :actions/cancel)]]]

        ;; Right column: language tabs + text fields
        (when @source-id
          [:> Grid {:size #js {:xs 12 :md 7}}
           [:> Stack {:spacing 2}
            [controls/lang-selector
             {:value @selected-tab
              :on-change #(reset! selected-tab %)
              :enabled-languages (set org-languages)}]

            (let [v (or (get-in desc [:summary @selected-tab]) "")]
              [text-fields/text-field
               {:on-change #(rf/dispatch [::events/set-service-candidate-summary @source-id @selected-tab %])
                :fullWidth true
                :multiline true :variant "outlined" :label (tr :ptv/summary) :value v
                :inputProps #js {:maxLength ptv-data/max-summary-length}
                :helperText (str (count v) "/" ptv-data/max-summary-length)
                :error (> (count v) ptv-data/max-summary-length)}])

            (let [v (or (get-in desc [:description @selected-tab]) "")]
              [text-fields/text-field
               {:on-change #(rf/dispatch [::events/set-service-candidate-description @source-id @selected-tab %])
                :fullWidth true
                :variant "outlined" :rows 7 :multiline true :label (tr :ptv/description) :value v
                :inputProps #js {:maxLength ptv-data/max-description-length}
                :helperText (str (count v) "/" ptv-data/max-description-length)
                :error (> (count v) ptv-data/max-description-length)}])

            (let [v (or (get-in desc [:user-instruction @selected-tab]) "")]
              [text-fields/text-field
               {:on-change #(rf/dispatch [::events/set-service-candidate-user-instruction @source-id @selected-tab %])
                :fullWidth true
                :variant "outlined" :rows 5 :multiline true :label (tr :ptv/user-instruction) :value v
                :inputProps #js {:maxLength ptv-data/max-user-instruction-length}
                :helperText (str (count v) "/" ptv-data/max-user-instruction-length)
                :error (> (count v) ptv-data/max-user-instruction-length)}])]])]])))

(defn add-service-link-form
  "Form for linking (adopting) an existing PTV service into LIPAS management.
   Props:
   - :org-id (required)
   - :on-cancel (required)
   - :lipas-id (optional) — when set, the linked service-id is auto-added to
     that site's :ptv :service-ids on success."
  [{:keys [org-id on-cancel lipas-id]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        all-services @(rf/subscribe [::subs/services org-id])
        non-lipas-services (remove #(some-> (:source-id %) (str/starts-with? "lipas-")) all-services)
        org-languages @(rf/subscribe [::subs/org-languages org-id])]
    [:> Stack {:spacing 2}
     [:> Typography {:variant "subtitle1" :fontWeight "medium"}
      (tr :ptv.service/link-existing)]
     [controls/services-selector
      {:options non-lipas-services
       :multiple false
       :value nil
       :value-fn :service-id
       :on-change (fn [service-id]
                    (when service-id
                      (let [service (some #(when (= (:service-id %) service-id) %) all-services)
                            data (merge {:org-id org-id
                                         :service-id service-id
                                         :languages org-languages}
                                        (select-keys service [:summary :description :user-instruction]))]
                        (rf/dispatch [::events/create-ptv-service org-id nil data [] [] lipas-id])
                        (on-cancel))))
       :label (tr :ptv.service/select-service)}]
     [:> Button
      {:size "small" :variant "text" :sx #js {:textTransform "none"}
       :on-click on-cancel}
      (tr :actions/cancel)]]))

(defn add-service-panel
  "Two-button entry point for adding a PTV service. Default state shows
   \"Create new\" / \"Link existing\"; click switches to the corresponding form.
   Props:
   - :org-id (required)
   - :locked-sub-category-id (optional) — passed to add-service-create-form
   - :lipas-id (optional) — passed to both forms for auto-attach"
  [{:keys [org-id locked-sub-category-id lipas-id]}]
  (r/with-let [mode (r/atom nil)]
    (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
      [:> Stack {:spacing 2 :sx #js {:p 2}}
       (case @mode
         :create [add-service-create-form {:org-id org-id
                                           :locked-sub-category-id locked-sub-category-id
                                           :lipas-id lipas-id
                                           :on-cancel #(reset! mode nil)}]
         :link   [add-service-link-form {:org-id org-id
                                         :lipas-id lipas-id
                                         :on-cancel #(reset! mode nil)}]
         [:> Stack {:direction "row" :spacing 1}
          [:> Button
           {:variant "outlined" :size "small" :sx #js {:textTransform "none"}
            :startIcon (r/as-element [:> Icon "add"])
            :on-click #(reset! mode :create)}
           (tr :ptv.service/create-new)]
          ;; HIDDEN: "Liitä olemassa oleva PTV-palvelu" entry point is
          ;; reader-discarded (`#_`) until the adoption flow is verified
          ;; on fix/ptv-adopt-preserve-target-groups. Restore by removing
          ;; the `#_` below. The :link case in the case-form above is
          ;; unreachable while this is hidden but is intentionally kept
          ;; so add-service-link-form remains wired up for restoration.
          #_[:> Button
             {:variant "outlined" :size "small" :sx #js {:textTransform "none"}
              :startIcon (r/as-element [:> Icon "link"])
              :on-click #(reset! mode :link)}
             (tr :ptv.service/link-existing)]])])))
