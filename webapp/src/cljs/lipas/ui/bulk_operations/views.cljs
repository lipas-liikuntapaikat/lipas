(ns lipas.ui.bulk-operations.views
  (:require ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/Alert$default" :as Alert]
            ;; clj-kondo false positive: `Box`/`List` bare symbols resolve
            ;; against a builtin during analysis, so the alias is reported
            ;; unused even though `[:> Box ...]`/`[:> List ...]` are used
            ;; below. See lipas.ui.assistant.views for the same pattern.
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Step$default" :as Step]
            ["@mui/material/StepLabel$default" :as StepLabel]
            ["@mui/material/Stepper$default" :as Stepper]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableContainer$default" :as TableContainer]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.bulk-operations :as bulk-fields]
            [lipas.data.prop-types :as prop-types]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.ui.bulk-operations.events :as events]
            [lipas.ui.bulk-operations.subs :as subs]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [malli.core :as m]
            [re-frame.core :as rf]
            [reagent.core :as r]))

;; ---------------------------------------------------------------------------
;; Data-driven field editor: one card per bulk-editable field, the input widget
;; chosen by the field's :type. The registry (lipas.data.bulk-operations) is the
;; single source of which fields exist and where they write.
;; ---------------------------------------------------------------------------

(def ^:private field-specs
  "malli schema per static field-id, for inline input validation. These are the
  SAME schemas the backend enforces (route `updates-schema`), so client feedback
  matches server acceptance. Selects (status/admin) need none — they can't
  produce invalid values."
  {:email             sites-schema/email
   :phone-number      sites-schema/phone-number
   :www               sites-schema/www
   :reservations-link sites-schema/reservations-link
   :construction-year sites-schema/construction-year
   :postal-code       location-schema/postal-code
   :postal-office     location-schema/postal-office
   :neighborhood      location-schema/neighborhood})

(defn- field-id->spec
  "Validation schema for a field-id: a static field's schema, else the property's
  prop-type schema (static ids and prop keys never collide). nil ⇒ no validation
  (selects). Numeric props resolve to an unbounded number schema by design."
  [field-id]
  (or (get field-specs field-id)
      (get prop-types/schemas field-id)))

(defn- field-value-invalid?
  "True when an armed field holds a non-empty value that fails its schema. Empty
  (a clear) is always valid; fields without a schema (selects) never fail."
  [field-id value]
  (let [spec (field-id->spec field-id)]
    (boolean (and spec (some? value)
                  (not (and (string? value) (str/blank? value)))
                  (not (m/validate spec value))))))

(defn- blank-value?
  "A value that means \"cleared\": nil, blank string, or empty collection."
  [v]
  (or (nil? v)
      (and (string? v) (str/blank? v))
      (and (coll? v) (empty? v))))

(defn- resolve-field
  "Look up a field spec by id across the static registry and the current dynamic
  property fields (needed by the summary, which only has the field ids)."
  [field-id property-fields]
  (or (get bulk-fields/static-field-by-id field-id)
      (first (filter #(= field-id (:field-id %)) property-fields))))

(def ^:private boolean-labels
  {:fi {true "Kyllä" false "Ei"}
   :se {true "Ja"    false "Nej"}
   :en {true "Yes"   false "No"}})

(defn- display-value
  "Human-readable value for the summary: enum codes resolved to their locale
  label, booleans to localized yes/no, a cleared value shown as a dash."
  [field value locale]
  (cond
    (blank-value? value)   "–"
    (boolean? value)       (get-in boolean-labels [locale value] (str value))
    (:opts field)          (if (sequential? value)
                             (str/join ", " (map #(get-in field [:opts % locale] (str %)) value))
                             (get-in field [:opts value locale] (str value)))
    :else                  (str value)))

(defn field-input
  "Render the value widget for a field, dispatched on `:type`."
  [{:keys [tr locale field value disabled? on-change]}]
  (let [{:keys [type opts clearable? field-id]} field
        label (get-in field [:label locale])
        spec  (field-id->spec field-id)]
    (case type
      :enum
      [selects/select
       {:label label :value value :items (seq opts)
        :value-fn first :label-fn (comp locale second)
        :deselect? clearable? :disabled disabled?
        :on-change on-change}]

      :enum-coll
      [selects/multi-select
       {:label label :value value :items (seq opts)
        :value-fn first :label-fn (comp locale second)
        :disabled disabled?
        :on-change on-change}]

      :boolean
      [selects/select
       {:label label :value value
        :items [[true (tr :confirm/yes)] [false (tr :confirm/no)]]
        :value-fn first :label-fn second
        :deselect? clearable? :disabled disabled?
        :on-change on-change}]

      :number
      [text-fields/text-field-controlled
       {:label label :value value :type "number"
        :disabled disabled? :spec spec :fullWidth true
        :on-change on-change}]

      ;; :text and anything unforeseen
      [text-fields/text-field-controlled
       {:label label :value value
        :disabled disabled? :spec spec :fullWidth true
        :on-change on-change}])))

(defn- field-helper-text
  [tr field selected? value]
  (let [{:keys [type clearable?]} field]
    (cond
      (not selected?)               (tr :lipas.bulk-operations/field-will-not-change)
      (and clearable? (blank-value? value)) (tr :lipas.bulk-operations/will-clear-field)
      ;; text/number show the concrete target; the enum widgets already show it
      (and (#{:text :number} type) (not (blank-value? value)))
      (str (tr :lipas.bulk-operations/will-update-to) " " value)
      :else                         (tr :lipas.bulk-operations/will-update))))

(defn field-card
  "One armable field: a checkbox toggling whether the field is written, plus the
  value input (disabled until armed)."
  [{:keys [tr locale field selected? value on-toggle on-change]}]
  [:> Grid {:item true :xs 12 :md 6}
   [:> Paper {:sx {:p 2
                   :border (if selected? 2 1)
                   :border-color (if selected? "primary.main" "divider")
                   :background-color (when-not selected? "action.disabledBackground")}}
    [:> Box {:sx {:display "flex" :align-items "flex-start" :gap 1}}
     [:> Box {:sx {:pt 1}}
      [:> Tooltip {:title (tr :lipas.bulk-operations/check-to-update-field)}
       [:> Checkbox {:checked selected? :color "primary" :on-change on-toggle}]]]
     [:> Box {:sx {:flex 1}}
      [field-input {:tr tr :locale locale :field field :value value
                    :disabled? (not selected?) :on-change on-change}]
      [:> Typography {:variant "caption" :color "text.secondary"}
       (field-helper-text tr field selected? value)]]]]])

(defn field-section
  "A titled group of field cards. Renders nothing when `fields` is empty."
  [{:keys [tr locale title fields update-form selected-fields]}]
  (when (seq fields)
    [:> Box {:sx {:mb 3}}
     (when title
       [:> Typography {:variant "subtitle1" :sx {:mb 1 :font-weight "bold"}} title])
     (into [:> Grid {:container true :spacing 2}]
           (for [field fields
                 :let [fid (:field-id field)]]
             [field-card
              {:tr tr :locale locale :field field
               :selected? (contains? selected-fields fid)
               :value (get update-form fid)
               :on-toggle #(rf/dispatch [::events/toggle-field-selection fid])
               :on-change #(rf/dispatch [::events/set-bulk-update-field fid %])}]))]))

;; Advance from Enter-info to the Yhteenveto step. The update is NOT executed
;; here — the summary previews the exact changes and holds the execute button.
;; Gated like the old direct submit: at least one armed field, no invalid values.
(defn next-to-summary-button [tr]
  (let [selected-fields @(rf/subscribe [::subs/selected-fields])
        update-form     @(rf/subscribe [::subs/bulk-update-form])
        ;; block advancing while any armed field holds a schema-invalid value —
        ;; matches the backend and spares the user a 400 round-trip
        has-invalid?    (some #(field-value-invalid? % (get update-form %)) selected-fields)]
    [:<>
     [:> Button {:variant "contained"
                 :color "primary"
                 :disabled (or (empty? selected-fields) (boolean has-invalid?))
                 :on-click #(rf/dispatch [::events/set-current-step 2])}
      (tr :actions/next)]
     (when has-invalid?
       [:> Typography {:variant "caption" :color "error" :sx {:ml 2}}
        (tr :lipas.bulk-operations/fix-invalid-values)])]))

;; Navigation buttons component
;; `on-back` (optional): when supplied, overrides the step-1 Back action — used
;; in external-selection mode where step 0 (site selection) is the calling list,
;; so Back must return there rather than to the (hidden) in-wizard select step.
(defn navigation-buttons
  ([tr current-step selected-count selected-fields-count on-cancel]
   (navigation-buttons tr current-step selected-count selected-fields-count on-cancel nil))
  ([tr current-step selected-count _selected-fields-count on-cancel on-back]
   [:> Box {:sx {:display "flex" :justify-content "space-between"}}
    [:> Box
     (when (pos? current-step)
       [:> Button {:variant "outlined"
                   :on-click (if (and on-back (= current-step 1))
                               on-back
                               #(rf/dispatch [::events/set-current-step (dec current-step)]))}
        (tr :actions/back)])]

    [:> Box {:sx {:display "flex" :gap 2}}
     [:> Button {:variant "outlined"
                 :on-click on-cancel}
      (tr :actions/cancel)]

     (case current-step
       0 [:> Button {:variant "contained"
                     :color "primary"
                     :disabled (zero? selected-count)
                     :on-click #(rf/dispatch [::events/set-current-step 1])}
          (tr :actions/next)]

       1 [next-to-summary-button tr]

       nil)]]))

;; Step 1: Select sites
(defn step-select-sites [tr selected-count on-cancel]
  (let [editable-sites @(rf/subscribe [::subs/filtered-editable-sites])
        selected-sites @(rf/subscribe [::subs/selected-sites])
        all-selected? @(rf/subscribe [::subs/all-sites-selected?])
        filters @(rf/subscribe [::subs/sites-filters])
        types @(rf/subscribe [:lipas.ui.sports-sites.subs/active-types])
        admins @(rf/subscribe [:lipas.ui.sports-sites.subs/admins])
        owners @(rf/subscribe [:lipas.ui.sports-sites.subs/owners])
        locale (tr)]
    [:> Box
     [:> Box {:sx {:mb 3}}
      [navigation-buttons tr 0 selected-count 0 on-cancel]]

     [:> Box {:sx {:mb 2 :display "flex" :justify-content "space-between" :align-items "center"}}
      [:> Typography {:variant "h6"}
       (if (pos? selected-count)
         (str (tr :lipas.bulk-operations/n-sites-selected selected-count))
         (tr :lipas.bulk-operations/select-sites-to-update))]
      [:> Box
       [:> Button {:variant "outlined"
                   :sx {:mr 1}
                   :on-click #(rf/dispatch [::events/select-all-sites (map :lipas-id editable-sites)])}
        (tr :actions/select-all)]
       [:> Button {:variant "outlined"
                   :on-click #(rf/dispatch [::events/deselect-all-sites])}
        (tr :actions/deselect-all)]]]

     [:> Accordion {:sx {:mb 2}}
      [:> AccordionSummary
       {:expandIcon (r/as-element [:> Icon "expand_more"])}
       [:> Typography (tr :actions/filter)]]
      [:> AccordionDetails
       [:> Grid {:container true :spacing 2}
        [:> Grid {:item true :xs 12 :md 4}
         [text-fields/text-field-controlled
          {:label (tr :search/search)
           :value (:search-text filters)
           :on-change #(rf/dispatch [::events/set-sites-filter :search-text %])}]]

        [:> Grid {:item true :xs 12 :md 2}
         (r/as-element
           [ac/type-selector
            {:value (:type-code filters)
             :label (tr :type/name)
             :onChange (fn [_ {:keys [value]}]
                         (rf/dispatch [::events/set-sites-filter :type-code value]))}])]

        [:> Grid {:item true :xs 12 :md 3}
         (r/as-element
           [ac/admin-selector
            {:value (:admin filters)
             :label (tr :lipas.sports-site/admin)
             :onChange (fn [_ {:keys [value]}]
                         (rf/dispatch [::events/set-sites-filter :admin value]))}])]

        [:> Grid {:item true :xs 12 :md 3}
         (r/as-element
           [ac/owner-selector
            {:value (:owner filters)
             :label (tr :lipas.sports-site/owner)
             :onChange (fn [_ {:keys [value]}]
                         (rf/dispatch [::events/set-sites-filter :owner value]))}])]

        ;; org-specific: owned vs granted (cross-org edit grant)
        [:> Grid {:item true :xs 12 :md 3}
         [selects/select
          {:label (tr :lipas.org/ownership-filter)
           :value (:ownership filters)
           :deselect? true
           :items [{:value "owned" :label (tr :lipas.org/owned)}
                   {:value "granted" :label (tr :lipas.org/grantee-orgs)}]
           :on-change #(rf/dispatch [::events/set-sites-filter :ownership %])}]]]]]

     ;; Table container with its own horizontal scroll
     [:> TableContainer {:sx {:overflow-x "auto" :width "100%"}}
      [:> Table {:size "small"}
       [:> TableHead
        [:> TableRow
         [:> TableCell {:padding "checkbox"}
          [:> Checkbox {:checked all-selected?
                        :on-change #(if all-selected?
                                      (rf/dispatch [::events/deselect-all-sites])
                                      (rf/dispatch [::events/select-all-sites (map :lipas-id editable-sites)]))}]]
         [:> TableCell (tr :lipas.sports-site/name)]
         [:> TableCell (tr :type/name)]
         [:> TableCell (tr :lipas.sports-site/admin)]
         [:> TableCell (tr :lipas.sports-site/owner)]
         [:> TableCell (tr :lipas.sports-site/email-public)]
         [:> TableCell (tr :lipas.sports-site/phone-number)]
         [:> TableCell (tr :lipas.sports-site/www)]
         [:> TableCell (tr :lipas.sports-site/reservations-link)]]]

       [:> TableBody
        (for [site editable-sites]
          [:> TableRow {:key (:lipas-id site)
                        :selected (contains? selected-sites (:lipas-id site))}
           [:> TableCell {:padding "checkbox"}
            [:> Checkbox {:checked (contains? selected-sites (:lipas-id site))
                          :on-change #(rf/dispatch [::events/toggle-site-selection (:lipas-id site)])}]]
           [:> TableCell (:name site)]
           [:> TableCell (get-in types [(get-in site [:type :type-code]) :name locale])]
           [:> TableCell (get-in admins [(:admin site) locale])]
           [:> TableCell (get-in owners [(:owner site) locale])]
           [:> TableCell (:email site)]
           [:> TableCell (:phone-number site)]
           [:> TableCell (:www site)]
           [:> TableCell (:reservations-link site)]])]]]

     [:> Box {:sx {:mt 3}}
      [navigation-buttons tr 0 selected-count 0 on-cancel]]]))

;; Step 2: Choose fields and values to apply to the selected sites.
;; `org-contact` (optional): the calling org's contact info remapped to site
;; contact keys ({:email :phone-number :www :reservations-link}); when present
;; (and non-empty) it powers the "fill from organization contact info" button.
(defn step-enter-info [tr selected-count on-cancel on-back org-contact]
  (let [update-form      @(rf/subscribe [::subs/bulk-update-form])
        selected-fields  @(rf/subscribe [::subs/selected-fields])
        property-fields  @(rf/subscribe [::subs/common-property-fields])
        locale           (tr)
        org-has-contact? (boolean (some some? (vals org-contact)))
        section          (fn [title fields]
                           [field-section {:tr tr :locale locale :title title :fields fields
                                           :update-form update-form :selected-fields selected-fields}])]
    [:> Box
     [:> Box {:sx {:mb 3}}
      [navigation-buttons tr 1 selected-count (count selected-fields) on-cancel on-back]]

     [:> Alert {:severity "info" :sx {:mb 3}}
      (tr :lipas.bulk-operations/selective-update-info)]

     ;; Header with a deselect-all shortcut. No "select all": with address and
     ;; type-specific properties now included, arming everything at once would
     ;; risk clearing empty fields across many sites — fields are armed by hand
     ;; (or via "fill from organization contact info" for the contact block).
     [:> Box {:sx {:display "flex" :justify-content "space-between" :align-items "center" :mb 2 :flex-wrap "wrap" :gap 1}}
      [:> Typography {:variant "body1"}
       (tr :lipas.bulk-operations/select-fields-to-update)]
      [:> Button {:variant "text"
                  :size "small"
                  :disabled (empty? selected-fields)
                  :on-click #(rf/dispatch [::events/set-selected-fields #{}])}
       (tr :actions/deselect-all)]]

     ;; --- Contact info (+ one-click fill from the org's own contact info) ---
     (when org-has-contact?
       [:> Box {:sx {:mb 2 :display "flex" :align-items "center" :gap 2 :flex-wrap "wrap"}}
        [:> Button {:variant "contained"
                    :color "secondary"
                    :size "small"
                    :startIcon (r/as-element [:> Icon "business"])
                    :on-click #(rf/dispatch [::events/populate-from-org-contact org-contact])}
         (tr :lipas.bulk-operations/fill-from-org)]
        [:> Typography {:variant "caption" :color "text.secondary"}
         (tr :lipas.bulk-operations/fill-from-org-help)]])

     (section (tr :lipas.bulk-operations/section-contact) bulk-fields/contact-fields)
     (section (tr :lipas.bulk-operations/section-basic) bulk-fields/basic-fields)
     (section (tr :lipas.bulk-operations/section-location) bulk-fields/location-fields)

     ;; --- Type-specific properties: only those common to every selected site ---
     [:> Box {:sx {:mb 3}}
      [:> Typography {:variant "subtitle1" :sx {:mb 1 :font-weight "bold"}}
       (tr :lipas.bulk-operations/section-properties)]
      (if (seq property-fields)
        (section nil property-fields)
        [:> Typography {:variant "body2" :color "text.secondary"}
         (tr :lipas.bulk-operations/no-common-properties)])]

     [:> Box {:sx {:mt 3}}
      [:> Typography {:variant "body2" :color "text.secondary"}
       (str (tr :lipas.bulk-operations/will-update-n-sites selected-count) " "
            (tr :lipas.bulk-operations/selected-fields-count (count selected-fields)))]]

     [:> Box {:sx {:mt 3}}
      [navigation-buttons tr 1 selected-count (count selected-fields) on-cancel on-back]]]))

;; Step 3: Yhteenveto — a two-mode step. Before execution it PREVIEWS the exact
;; changes (armed fields with target values + the sites they'll be applied to)
;; and holds the execute button; after execution it shows the results. The
;; preview is the deliberate confirmation for the whole update — including
;; high-impact fields, which upgrade the alert to a warning.

(defn- summary-fields-list
  "The armed fields with their human-readable target values."
  [update-form selected-fields property-fields locale]
  (into [:> List]
        (for [fid selected-fields
              :let [field (resolve-field fid property-fields)]
              :when field]
          [:> ListItem {:key (str fid)}
           [:> ListItemText
            {:primary (get-in field [:label locale])
             :secondary (display-value field (get update-form fid) locale)}]])))

(defn- summary-sites-list
  [sites]
  [:> Box {:sx {:max-height 300 :overflow-y "auto" :border 1 :border-color "divider" :border-radius 1 :p 2}}
   [:> List {:dense true}
    (for [site sites]
      [:> ListItem {:key (:lipas-id site)}
       [:> ListItemText
        {:primary (:name site)
         :secondary (str "ID: " (:lipas-id site))}]])]])

(defn step-summary [tr on-cancel]
  (let [update-results  @(rf/subscribe [::subs/update-results])
        update-form     @(rf/subscribe [::subs/bulk-update-form])
        selected-fields @(rf/subscribe [::subs/selected-fields])
        property-fields @(rf/subscribe [::subs/common-property-fields])
        editable-sites  @(rf/subscribe [::subs/editable-sites])
        locale          (tr)]
    (if update-results
      ;; --- Results: the update has run ---
      (let [updated-site-ids (set (:updated-sites update-results))
            updated-sites    (filter #(contains? updated-site-ids (:lipas-id %)) editable-sites)]
        [:> Box
         [:> Alert {:severity "success" :sx {:mb 3}}
          (tr :lipas.bulk-operations/update-completed)]

         [:> Typography {:variant "h6" :sx {:mb 2}}
          (tr :lipas.bulk-operations/updated-fields)]
         [summary-fields-list update-form selected-fields property-fields locale]

         [:> Typography {:variant "h6" :sx {:mt 3 :mb 2}}
          (str (tr :lipas.bulk-operations/updated-sites-list) " (" (:total-updated update-results) ")")]
         [summary-sites-list updated-sites]

         [:> Box {:sx {:mt 3 :display "flex" :gap 2}}
          [:> Button {:variant "contained"
                      :color "primary"
                      :on-click on-cancel}
           (tr :actions/done)]
          [:> Button {:variant "outlined"
                      :on-click #(rf/dispatch [::events/reset])}
           (tr :lipas.bulk-operations/update-more-sites)]]])

      ;; --- Preview: confirm before executing ---
      (let [selected-sites @(rf/subscribe [::subs/selected-sites])
            selected-count @(rf/subscribe [::subs/selected-sites-count])
            sites          (filter #(contains? selected-sites (:lipas-id %)) editable-sites)
            high-impact?   (some #(and (:high-impact? %)
                                       (contains? selected-fields (:field-id %)))
                                 bulk-fields/static-fields)
            execute!       #(rf/dispatch [::events/execute-bulk-update
                                          {:on-success (fn [_] (rf/dispatch [::events/get-editable-sites]))
                                           :on-failure nil}])]
        [:> Box
         [:> Alert {:severity (if high-impact? "warning" "info") :sx {:mb 3}}
          (tr :lipas.bulk-operations/review-before-update selected-count)]

         [:> Typography {:variant "h6" :sx {:mb 2}}
          (tr :lipas.bulk-operations/fields-to-update)]
         [summary-fields-list update-form selected-fields property-fields locale]

         [:> Typography {:variant "h6" :sx {:mt 3 :mb 2}}
          (str (tr :lipas.bulk-operations/sites-to-update-list) " (" selected-count ")")]
         [summary-sites-list sites]

         [:> Box {:sx {:mt 3 :display "flex" :gap 2}}
          [:> Button {:variant "outlined"
                      :on-click #(rf/dispatch [::events/set-current-step 1])}
           (tr :actions/back)]
          [:> Button {:variant "outlined"
                      :on-click on-cancel}
           (tr :actions/cancel)]
          [:> Button {:variant "contained"
                      :color "primary"
                      :on-click execute!}
           (tr :lipas.bulk-operations/update-n-sites selected-count)]]]))))

;; Main component with stepper
;; `external-selection?`: site selection happens in the caller's list (the org
;; "Our sites" tab), so the in-wizard "Select sites" step (0) is omitted — the
;; wizard starts at "Enter info" (step 1) and Back from there exits via on-cancel.
(defn main
  [{:keys [title description on-cancel external-selection? org-contact]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        current-step @(rf/subscribe [::subs/current-step])
        selected-count @(rf/subscribe [::subs/selected-sites-count])
        loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])
        update-results @(rf/subscribe [::subs/update-results])]

    [:> Grid {:container true :spacing 2 :sx {:p 1}}
     ;; Header
     [:> Grid {:item true :xs 12}
      [:> Paper {:sx {:p 2}}
       [:> Typography {:variant "h5" :sx {:mb 2}}
        (or title (tr :lipas.org/bulk-operations))]
       [:> Typography {:variant "body1"}
        (or description (tr :lipas.org/bulk-operations-description))]]]

     ;; Error display
     (when error
       [:> Grid {:item true :xs 12}
        [:> Alert {:severity "error"}
         (str "Error: " (or (:status-text error) "Failed to load data"))]])

     ;; Stepper
     [:> Grid {:item true :xs 12}
      [:> Paper {:sx {:p 3}}
       ;; Always the same 3 steps. In external mode selection happened in the
       ;; caller's list and current-step starts at 1, so "Valitse liikuntapaikat"
       ;; renders as already completed. After execution every step is done.
       [:> Stepper {:active-step (if update-results 3 current-step) :sx {:mb 3}}
        [:> Step
         [:> StepLabel (tr :lipas.bulk-operations/step-select-sites)]]
        [:> Step
         [:> StepLabel (tr :lipas.bulk-operations/step-enter-info)]]
        [:> Step
         [:> StepLabel (tr :lipas.bulk-operations/step-summary)]]]

       ;; Step content
       (if loading?
         [:> Box {:sx {:display "flex" :justify-content "center" :p 4}}
          [:> CircularProgress]]

         (case current-step
           0 (when-not external-selection?
               [step-select-sites tr selected-count on-cancel])
           1 [step-enter-info tr selected-count on-cancel
              ;; in external mode Back returns to the caller's list
              (when external-selection? on-cancel)
              org-contact]
           2 [step-summary tr on-cancel]
           nil))]]]))
