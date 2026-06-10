(ns lipas.ui.org.views
  (:require ["@mui/icons-material/Delete$default" :as DeleteIcon]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/InputLabel$default" :as InputLabel]
            ["@mui/material/Menu$default" :as Menu]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Step$default" :as Step]
            ["@mui/material/StepButton$default" :as StepButton]
            ["@mui/material/StepLabel$default" :as StepLabel]
            ["@mui/material/Stepper$default" :as Stepper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/TableSortLabel$default" :as TableSortLabel]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.data.owners :as owners]
            [lipas.roles :as roles]
            [lipas.schema.org :as org-schema]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.ui.bulk-operations.events :as bulk-ops-events]
            [lipas.ui.bulk-operations.subs :as bulk-ops-subs]
            [lipas.ui.bulk-operations.views :as bulk-ops-views]
            [lipas.ui.roles.editor :as role-editor]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.org.events :as events]
            [lipas.ui.org.subs :as subs]
            [lipas.ui.subs :as ui-subs]
            [lipas.utils :as utils]
            [malli.core :as m]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

;; ---------------------------------------------------------------------------
;; Shared helpers
;; ---------------------------------------------------------------------------

(def org-type-options
  [["city" :lipas.org/type-city]
   ["municipal-consortium" :lipas.org/type-municipal-consortium]
   ["state" :lipas.org/type-state]
   ["private" :lipas.org/type-private]
   ["association" :lipas.org/type-association]
   ["other" :lipas.org/type-other]])

(defn org-type-label [tr type]
  (some (fn [[k tr-key]] (when (= k type) (tr tr-key))) org-type-options))

(defn role-grant-text
  "Plain-language description of a single catalog role-spec. Data-driven from the
  role's i18n grant description (defined alongside the role catalog), falling back
  to the role's display name so a role without a curated description never renders
  as \"unknown\". Appends the role's scoped activities when present."
  [tr {:keys [role activity]}]
  (when role
    (let [desc (tr (keyword "lipas.user.permissions.roles.role-descriptions" role))
          ;; tongue returns "{Missing key …}" for an undescribed role → fall back
          ;; to its translated name.
          desc (if (str/starts-with? desc "{Missing")
                 (tr (keyword "lipas.user.permissions.roles.role-names" role))
                 desc)]
      (str desc (when (seq activity) (str ": " (str/join ", " activity)))))))

(defn template-grants-text
  "Plain-language description of everything a catalog template grants."
  [tr template]
  (->> (:roles template)
       (map #(role-grant-text tr %))
       (remove str/blank?)
       (str/join "; ")))

;; A member holds a single `:roles` list drawn from the reserved engine role
;; "admin" plus the org's catalog keys. These helpers build the assignment
;; vocabulary (shared by the invite form and the members table) and label one
;; role key for display.

(defn role-options
  "Multi-select options for a member's roles: the reserved \"admin\" role first,
  then the org's catalog templates."
  [tr template-opts]
  (into [{:value "admin" :label (tr :lipas.org/role-admin)}]
        template-opts))

(defn role-label
  "Human label for one role key — the reserved admin label or the catalog
  template's label, falling back to the raw key."
  [tr catalog rkey]
  (if (= "admin" rkey)
    (tr :lipas.org/role-admin)
    (or (:label (get catalog (keyword rkey))) rkey)))

;; ---------------------------------------------------------------------------
;; PTV tab (unchanged)
;; ---------------------------------------------------------------------------

(defn ptv-tab []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        org @(rf/subscribe [::subs/editing-org])
        is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])
        ptv-config (or (:ptv-data org) {})
        ptv-enabled? (and (:sync-enabled ptv-config)
                          (not (str/blank? (:org-id ptv-config))))
        ptv-config-valid? (m/validate org-schema/ptv-config-update (utils/clean ptv-config))]

    [:> Box {:sx {:p 2}}
     [:> Typography {:variant "h5" :sx {:mb 2}}
      (tr :lipas.org.ptv/prefix) (tr :lipas.org/ptv-integration)]

     ;; PTV Integration Status Banner
     [:> Alert {:severity (if ptv-enabled? "success" "info")
                :sx {:mb 3}}
      (if ptv-enabled?
        (tr :lipas.org.ptv/integration-enabled)
        [:span
         (tr :lipas.org.ptv/integration-not-enabled-1)
         (tr :lipas.org.ptv/please-contact)
         [:a {:href "mailto:lipasinfo@jyu.fi"
              :style {:color "inherit" :text-decoration "underline"}}
          "lipasinfo@jyu.fi"]
         (tr :lipas.org.ptv/integration-not-enabled-2)])]

     ;; Show configuration to everyone, but only LIPAS admins can edit
     [:> FormGroup {:sx {:gap 2 :max-width 800}}

      ;; PTV Organization ID
      [text-fields/text-field-controlled
       {:label (tr :lipas.org.ptv/org-id-label)
        :value (:org-id ptv-config)
        :placeholder (tr :lipas.org.ptv/org-id-placeholder)
        :helper-text (tr :lipas.org.ptv/org-id-helper)
        :required true
        :disabled (not is-lipas-admin?)
        :on-change #(rf/dispatch [::events/edit-org [:ptv-data :org-id] %])}]

      ;; Production Organization ID
      [text-fields/text-field-controlled
       {:label (tr :lipas.org.ptv/prod-org-id-label)
        :value (:prod-org-id ptv-config)
        :placeholder (tr :lipas.org.ptv/prod-org-id-placeholder)
        :helper-text (tr :lipas.org.ptv/prod-org-id-helper)
        :disabled (not is-lipas-admin?)
        :on-change #(rf/dispatch [::events/edit-org [:ptv-data :prod-org-id] %])}]

      ;; City codes
      [:> Typography {:variant "h6" :sx {:mt 2 :mb 1}}
       (tr :lipas.org.ptv/municipality-codes-title)]

      [selects/city-selector
       {:label (tr :lipas.org.ptv/cities-label)
        :value (:city-codes ptv-config [])
        :required true
        :disabled (not is-lipas-admin?)
        :on-change (fn [value]
                     (rf/dispatch [::events/edit-org [:ptv-data :city-codes] value]))}]

      ;; Owners
      [:> Typography {:variant "h6" :sx {:mt 2 :mb 1}}
       (tr :lipas.org.ptv/ownership-types-title)]

      [selects/owner-selector
       {:label (tr :lipas.org.ptv/owners-label)
        :value (:owners ptv-config ["city" "city-main-owner"])
        :required true
        :disabled (not is-lipas-admin?)
        :on-change #(rf/dispatch [::events/edit-org [:ptv-data :owners] %])}]

      ;; Supported languages
      [:> Typography {:variant "h6" :sx {:mt 2 :mb 1}}
       (tr :lipas.org.ptv/supported-languages-title)]

      [selects/multi-select
       {:label (tr :lipas.org.ptv/languages-label)
        :value (:supported-languages ptv-config [])
        :items [{:value "fi" :label (tr :lipas.org.ptv/finnish-label)}
                {:value "se" :label (tr :lipas.org.ptv/swedish-label)}
                {:value "en" :label (tr :lipas.org.ptv/english-label)}]
        :required true
        :disabled (not is-lipas-admin?)
        :on-change #(rf/dispatch [::events/edit-org [:ptv-data :supported-languages] %])}]

      ;; Sync enabled
      [:> FormControlLabel
       {:control (r/as-element
                   [:> Checkbox
                    {:checked (boolean (:sync-enabled ptv-config))
                     :disabled (not is-lipas-admin?)
                     :onChange (fn [e]
                                 (rf/dispatch [::events/edit-org
                                               [:ptv-data :sync-enabled]
                                               (.-checked (.-target e))]))}])
        :label (tr :lipas.org.ptv/sync-enabled-label)
        :sx {:mt 2}}]

      ;; Save button - only visible to LIPAS admins
      (when is-lipas-admin?
        [:> Button
         {:variant "contained"
          :color "primary"
          :disabled (not ptv-config-valid?)
          :on-click #(rf/dispatch [::events/save-ptv-config])
          :sx {:mt 3 :align-self "flex-start"}}
         [:> Icon {:sx {:mr 1}} "save"]
         (tr :lipas.org.ptv/save-configuration)])

      ;; Info message for non-admins
      (when (not is-lipas-admin?)
        [:> Alert {:severity "info" :sx {:mt 2}}
         (tr :lipas.org.ptv/admin-only-message)])]]))

;; ---------------------------------------------------------------------------
;; Overview tab (was Contact)
;; ---------------------------------------------------------------------------

(def ^:private instruction-langs [[:fi "Suomi"] [:se "Svenska"] [:en "English"]])

(defn instructions-tab
  "Org admin (and lipas-admin) write localized instructions for members; members
  see them read-only. Stored in the org payload under :instructions {fi se en}.
  The language variants are shown as sub-tabs, one editable text area at a time."
  [tr org-id]
  (r/with-let [lang (r/atom :fi)]
    (let [org          @(rf/subscribe [::subs/editing-org])
          is-new?      (= "new" org-id)
          can-edit?    (or is-new? @(rf/subscribe [::subs/can? :org/edit-instructions org-id]))
          instructions (:instructions org)
          empty?       (every? str/blank? (vals (select-keys instructions [:fi :se :en])))
          selected     @lang]
      [:> Box {:sx {:p 2}}
       [:> Typography {:variant "body2" :color "text.secondary" :sx {:mb 2 :max-width 800}}
        (tr :lipas.org/instructions-helper)]
       (if (and (not can-edit?) empty?)
         [:> Typography {:color "text.secondary"} (tr :lipas.org/no-instructions)]
         [:> Box {:sx {:max-width 800}}
          (into [:> Tabs {:value     selected
                          :on-change (fn [_ v] (reset! lang v))
                          :sx        {:mb 2}}]
                (for [[loc label] instruction-langs]
                  [:> Tab {:key loc :label label :value loc}]))
          ^{:key selected}
          [text-fields/text-field-controlled
           {:label     (tr (keyword "lipas.org" (str "instructions-" (name selected))))
            :value     (get instructions selected)
            :multiline true
            :rows      10
            :disabled  (not can-edit?)
            :on-change #(rf/dispatch [::events/edit-org [:instructions selected] %])}]
          (when can-edit?
            [:> Button
             {:variant  "contained"
              :color    "secondary"
              :on-click #(rf/dispatch [::events/save-org org])
              :sx       {:mt 2 :display "block"}}
             [:> Icon {:sx {:mr 1}} "save"]
             (tr :actions/save)])])])))

(defn overview-tab [tr org-id]
  (let [org @(rf/subscribe [::subs/editing-org])
        org-valid? @(rf/subscribe [::subs/org-valid?])
        is-new? (= "new" org-id)
        can-contact? (or is-new? @(rf/subscribe [::subs/can? :org/edit-contact org-id]))
        can-type? (or is-new? @(rf/subscribe [::subs/can? :org/edit-type+ownership org-id]))]
    [:> Box {:sx {:p 2}}
     [:> FormGroup {:sx {:gap 2 :max-width 600}}
      [text-fields/text-field-controlled
       {:label (tr :lipas.org/name)
        :value (:name org)
        :spec org-schema/org-name
        :required true
        :disabled (not can-contact?)
        :on-change #(rf/dispatch [::events/edit-org [:name] %])}]

      ;; Organization type (lipas-admin only)
      [:> FormControl {:disabled (not can-type?)}
       [:> InputLabel {:id "org-type"} (tr :lipas.org/org-type)]
       [:> Select
        {:labelId "org-type"
         :value (or (:type org) "")
         :label (tr :lipas.org/org-type)
         :onChange (fn [e] (rf/dispatch [::events/edit-org [:type] (.. e -target -value)]))}
        (for [[k tr-key] org-type-options]
          [:> MenuItem {:key k :value k} (tr tr-key)])]]

      [text-fields/text-field-controlled
       {:label (tr :lipas.org/phone)
        :value (get-in org [:data :primary-contact :phone])
        :spec sites-schema/phone-number
        :disabled (not can-contact?)
        :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :phone] x]))}]
      [text-fields/text-field-controlled
       {:label (tr :lipas.org/email)
        :value (get-in org [:data :primary-contact :email])
        :spec sites-schema/email
        :disabled (not can-contact?)
        :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :email] x]))}]
      [text-fields/text-field-controlled
       {:label (tr :lipas.org/website)
        :value (get-in org [:data :primary-contact :website])
        :spec sites-schema/www
        :disabled (not can-contact?)
        :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :website] x]))}]
      [text-fields/text-field-controlled
       {:label (tr :lipas.org/reservations-link)
        :value (get-in org [:data :primary-contact :reservations-link])
        :spec sites-schema/reservations-link
        :disabled (not can-contact?)
        :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :reservations-link] x]))}]

      ;; Ownership rule moved to the "Oikeudet ja omistus" tab (ownership-editor).

      (when (or can-contact? can-type?)
        [:> Button
         {:variant "contained"
          :color "secondary"
          :disabled (not org-valid?)
          :on-click #(rf/dispatch [::events/save-org org])
          :sx {:mt 2 :align-self "flex-start"}}
         [:> Icon {:sx {:mr 1}} "save"]
         (tr :actions/save)])]]))

;; ---------------------------------------------------------------------------
;; Members tab
;; ---------------------------------------------------------------------------

(defn invite-member
  "One email-based invite path for both lipas-admins and org-admins. As soon as a
  valid email loses focus we ask the (org-scoped) backend whether it's already a
  LIPAS account, and reflect that inline + in the button — so the admin knows
  whether they're adding an existing user or sending a fresh invitation. The
  backend does the right thing either way; the user is always notified by email."
  [tr org-id]
  (let [form @(rf/subscribe [::subs/invite-member-form])
        template-opts @(rf/subscribe [::subs/member-template-options])
        role-opts (role-options tr template-opts)
        email (:email form)
        existing? (:existing? form)
        checking? (:checking? form)
        valid-email? (boolean (and email (re-matches #".+@.+\..+" email)))]
    [:> Box {:sx {:display "flex" :flex-direction "row" :flex-wrap "wrap"
                  :gap 1 :align-items "flex-start" :mb 2}}
     ;; Email — the single, canonical path for everyone
     [:> Box {:sx {:display "flex" :flex-direction "column" :min-width 280}}
      [text-fields/text-field-controlled
       {:label (tr :lipas.org/email)
        :value email
        :type "email"
        :spec sites-schema/email
        :on-change #(rf/dispatch [::events/set-invite-email %])
        :on-blur (fn [_] (rf/dispatch [::events/check-existing-user org-id]))}]
      ;; live existence status (only meaningful for a well-formed email)
      (cond
        checking?
        [:> Typography {:variant "caption" :color "text.secondary" :sx {:mt 0.5}}
         (tr :lipas.org/invite-checking)]

        (and valid-email? (true? existing?))
        [:> Typography {:variant "caption" :color "success.main" :sx {:mt 0.5}}
         (tr :lipas.org/invite-status-existing)]

        (and valid-email? (false? existing?))
        [:> Typography {:variant "caption" :color "info.main" :sx {:mt 0.5}}
         (tr :lipas.org/invite-status-new)])]

     ;; Roles (reserved "admin" + catalog templates). Defaults to empty = a
     ;; plain member (membership confers the :org/member baseline).
     [:> Box {:sx {:min-width 220}}
      [selects/multi-select
       {:label (tr :lipas.org/roles)
        :value (vec (:roles form))
        :items role-opts
        :on-change #(rf/dispatch [::events/set-invite-member-form [:roles] (vec %)])}]]

     [:> Button
      {:variant "contained" :color "primary" :sx {:mt 1}
       :disabled (not valid-email?)
       :on-click #(rf/dispatch [::events/invite-member org-id])}
      (cond
        (true? existing?)  (tr :lipas.org/add-member-action)
        (false? existing?) (tr :lipas.org/send-invitation-action)
        :else              (tr :lipas.org/invite-member))]]))

(defn member-roles-cell
  "Role chips for one member with an add-menu, all gated by can-manage?. The role
  vocabulary is the reserved \"admin\" role plus the org's catalog keys; edits
  replace the member's whole `:roles` list via ::set-member-roles."
  [_props]
  (let [anchor (r/atom nil)]
    (fn [{:keys [tr org-id member catalog can-manage?]}]
      (let [assigned   (vec (:roles member))
            user-id    (:id member)
            all-keys   (cons "admin" (map name (keys catalog)))
            unassigned (remove (set assigned) all-keys)]
        [:> Box {:sx {:display "flex" :flex-wrap "wrap" :gap 0.5 :align-items "center"}}
         (for [rkey assigned
               :let [entry (get catalog (keyword rkey))]]
           [:> Tooltip {:key rkey :title (if (= "admin" rkey)
                                           (tr :lipas.org/grants-admin)
                                           (template-grants-text tr entry))}
            [:> Chip
             (cond-> {:label (role-label tr catalog rkey)
                      :size "small"}
               can-manage?
               (assoc :on-delete
                      (fn [] (rf/dispatch [::events/set-member-roles org-id user-id
                                           (vec (remove #(= % rkey) assigned))]))))]])
         (when (and can-manage? (seq unassigned))
           [:<>
            [:> IconButton {:size "small"
                            :on-click (fn [e] (reset! anchor (.-currentTarget e)))}
             [:> Icon {:fontSize "small"} "add"]]
            [:> Menu {:open (boolean @anchor)
                      :anchorEl @anchor
                      :onClose (fn [] (reset! anchor nil))}
             (for [rkey unassigned]
               [:> MenuItem
                {:key rkey
                 :onClick (fn []
                            (rf/dispatch [::events/set-member-roles org-id user-id
                                          (conj assigned rkey)])
                            (reset! anchor nil))}
                (role-label tr catalog rkey)])]])]))))

(defn members-tab [tr org-id]
  (let [org-users @(rf/subscribe [::subs/org-users])
        catalog @(rf/subscribe [::subs/org-templates])
        can-manage? @(rf/subscribe [::subs/can? :org/manage-members org-id])]
    [:> Box {:sx {:p 2}}
     [:> Typography {:variant "body2" :color "text.secondary" :sx {:mb 2}}
      (tr :lipas.org/members-plane-note)]
     (when can-manage?
       [:> Box {:sx {:mb 1}}
        [invite-member tr org-id]
        [:> Alert {:severity "info" :sx {:mt 1}}
         (tr :lipas.org/permissions-refresh-note)]])

     (if (seq org-users)
       [:> Table
        [:> TableHead
         [:> TableRow
          [:> TableCell (tr :lipas.org/member-col)]
          [:> TableCell (tr :lipas.org/roles)]
          (when can-manage?
            [:> TableCell {:align "right"} (tr :actions/actions)])]]
        [:> TableBody
         (for [member org-users]
           [:> TableRow {:key (:id member)}
            [:> TableCell (or (:email member) (:username member))]
            [:> TableCell
             [member-roles-cell {:tr tr :org-id org-id :member member
                                 :catalog catalog :can-manage? can-manage?}]]
            (when can-manage?
              [:> TableCell {:align "right"}
               [:> IconButton
                {:size "small" :color "error"
                 :on-click (fn [] (rf/dispatch [::events/remove-member org-id (:id member)]))}
                [:> DeleteIcon]]])])]]
       [:> Typography {:color "text.secondary"} (tr :lipas.org/no-members)])]))

;; ---------------------------------------------------------------------------
;; Roles & templates tab
;; ---------------------------------------------------------------------------

(defn template-member-counts
  "Map role-key (string) -> number of members currently assigned it. Includes the
  reserved \"admin\" key; catalog-usage views only ever look up catalog keys, so
  it's harmless there."
  [org-users]
  (->> org-users
       (mapcat :roles)
       (frequencies)))

(def catalog-roles
  "Role vocabulary a LIPAS admin may put in an org's template catalog (the
  ceiling). Differs from admin's `:assignable` set: includes `:org-editor`
  (assignable false, but projected) and excludes org-admin/org-user/admin."
  (->> roles/roles
       (filter (comp :catalog-assignable val))
       (sort-by (comp :sort val))
       (mapv key)))

(def catalog-presets
  "Common templates, one click to drop in and then tweak. Labels are editable."
  [{:key :editor   :label "Muokkaaja"     :roles [{:role "org-editor"}]}
   {:key :ptv      :label "PTV"           :roles [{:role "ptv-manager" :city-code []}]}
   {:key :activity :label "Aktiviteetti"  :roles [{:role "activities-manager" :activity []}]}
   {:key :type     :label "Tyyppi"        :roles [{:role "type-manager" :type-code []}]}])

(defn catalog-template-editor
  "One template = a human-readable label + 1..N role-specs built with the shared
  role editor (org-id hidden — injected at projection). `member-count` is how
  many of the org's members currently have this template assigned — surfaced so
  the admin sees that editing/removing it affects real users."
  [tr tkey entry member-count]
  [:> Paper {:variant "outlined" :sx {:p 2 :mb 2}}
   [:> Box {:sx {:display "flex" :align-items "center" :gap 1}}
    [text-fields/text-field-controlled
     {:label (tr :lipas.org/template-label)
      :value (:label entry)
      :on-change #(rf/dispatch [::events/set-template-label tkey %])}]
    [:> Typography {:variant "caption" :color "text.secondary" :sx {:flex 1}}
     (str (tr :lipas.org/template-key) ": " (name tkey))]
    (when (pos? member-count)
      [:> Tooltip {:title (tr :lipas.org/template-in-use member-count)}
       [:> Chip {:size "small" :color "warning" :variant "outlined"
                 :icon (r/as-element [:> Icon {:fontSize "small"} "group"])
                 :label member-count}]])
    [:> IconButton {:color "error"
                    :on-click #(rf/dispatch [::events/remove-template tkey])}
     [:> DeleteIcon]]]
   (when (pos? member-count)
     [:> Alert {:severity "warning" :sx {:mt 1}}
      (tr :lipas.org/template-in-use-warning member-count)])
   (doall
     (map-indexed
       (fn [idx spec]
         [:> Box {:key idx :sx {:display "flex" :gap 1 :align-items "flex-start" :mt 1}}
          [:> Box {:sx {:flex 1}}
           [role-editor/role-spec-editor
            {:spec spec
             :roles catalog-roles
             :hide-context-keys #{:org-id}
             :on-role-change (fn [role] (rf/dispatch [::events/set-template-role tkey idx role]))
             :on-context-change (fn [k vals] (rf/dispatch [::events/set-template-role-context tkey idx k vals]))
             :tr tr}]]
          [:> IconButton {:size "small" :color "error"
                          :on-click #(rf/dispatch [::events/remove-template-role tkey idx])}
           [:> DeleteIcon]]])
       (:roles entry)))
   [:> Button {:size "small" :sx {:mt 1}
               :on-click #(rf/dispatch [::events/add-template-role tkey])}
    [:> Icon {:sx {:mr 0.5} :fontSize "small"} "add"]
    (tr :lipas.org/add-role)]])

(defn ownership-editor
  "The ownership/claim rule (the ceiling for what an org may take over). Four
  optional, AND-combined axes; lipas-admin only. Saved with the org details."
  [tr org-id]
  (let [org @(rf/subscribe [::subs/editing-org])
        can? @(rf/subscribe [::subs/can? :org/edit-type+ownership org-id])
        ownership (:ownership org)]
    (when can?
      [:> Box {:sx {:mt 4}}
       [:> Divider {:sx {:mb 2}}]
       [:> Typography {:variant "h6"} (tr :lipas.org/ownership-rule)]
       [:> Typography {:variant "body2" :color "text.secondary" :sx {:mb 1}}
        (tr :lipas.org/ownership-rule-help)]
       [:> Alert {:severity "warning" :sx {:mb 2}}
        (tr :lipas.org/ownership-lock-note)]
       [:> Stack {:spacing 2 :sx {:max-width 600}}
        [selects/city-selector
         {:label (tr :lipas.org.ptv/cities-label)
          :value (:city-codes ownership [])
          :on-change #(rf/dispatch [::events/edit-org [:ownership :city-codes] %])}]
        [selects/owner-selector
         {:label (tr :lipas.org.ptv/owners-label)
          :value (:owners ownership [])
          :on-change #(rf/dispatch [::events/edit-org [:ownership :owners] %])}]
        [role-editor/type-code-select
         {:tr tr
          :value (:type-codes ownership [])
          :on-change #(rf/dispatch [::events/edit-org [:ownership :type-codes] %])}]
        [role-editor/activity-select
         {:tr tr
          :value (:activities ownership [])
          :on-change #(rf/dispatch [::events/edit-org [:ownership :activities] %])}]
        [:> Box {:sx {:display "flex" :gap 1 :flex-wrap "wrap"}}
         [:> Button
          {:variant "contained" :color "secondary"
           :on-click #(rf/dispatch [::events/save-org org])}
          [:> Icon {:sx {:mr 1}} "save"]
          (tr :lipas.org/save-ownership)]
         ;; act on the rule right where it's defined (uses the saved rule)
         (when (some #(seq (get ownership %)) [:city-codes :owners :type-codes :activities])
           [:> Button
            {:variant "outlined" :color "secondary"
             :on-click #(rf/dispatch [::events/open-claim-dialog
                                      {:mode "request" :org-id org-id}])}
            [:> Icon {:sx {:mr 1}} "flag"]
            (tr :lipas.org/preview-and-reclaim)])]]])))

(defn roles-templates-tab [tr org-id]
  (let [catalog @(rf/subscribe [::subs/org-templates])
        org-users @(rf/subscribe [::subs/org-users])
        counts (template-member-counts org-users)
        can-edit? @(rf/subscribe [::subs/can? :org/edit-catalog org-id])
        editor @(rf/subscribe [::subs/catalog-editor])
        ;; saved templates that members still hold but the admin has removed from
        ;; the working editor copy → saving will strip those members' grants.
        removed-in-use (->> (keys catalog)
                            (map name)
                            (remove (set (map name (keys editor))))
                            (filter #(pos? (get counts % 0)))
                            (map (fn [k] {:key k
                                          :label (or (:label (get catalog (keyword k))) k)
                                          :count (get counts k 0)})))]
    [:> Box {:sx {:p 2}}
     (when-not can-edit?
       [:> Alert {:severity "info" :sx {:mb 2}}
        (tr :lipas.org/catalog-readonly-note)])

     (if can-edit?
       ;; --- editable view (lipas-admin); seeded on tab switch in ::set-current-tab ---
       [:> Box
        [:> Typography {:variant "h6" :sx {:mb 1}} (tr :lipas.org/roles-templates-tab)]

        ;; presets + add custom
        [:> Box {:sx {:display "flex" :gap 1 :flex-wrap "wrap" :mb 2}}
         (for [p catalog-presets]
           [:> Button {:key (name (:key p)) :size "small" :variant "outlined"
                       :on-click #(rf/dispatch [::events/add-template p])}
            (str "+ " (:label p))])
         [:> Button {:size "small" :variant "contained"
                     :on-click #(rf/dispatch [::events/add-template {:label ""}])}
          [:> Icon {:sx {:mr 0.5} :fontSize "small"} "add"]
          (tr :lipas.org/add-template)]]

        (doall
          (for [[tkey entry] (sort-by (comp name key) editor)]
            ^{:key (name tkey)}
            [catalog-template-editor tr tkey entry (get counts (name tkey) 0)]))

        (when (empty? editor)
          [:> Typography {:color "text.secondary" :sx {:mb 2}} (tr :lipas.org/no-templates)])

        ;; warn before saving away templates members still depend on
        (when (seq removed-in-use)
          [:> Alert {:severity "warning" :sx {:mt 1 :mb 1}}
           [:> Typography {:variant "body2" :sx {:font-weight "bold"}}
            (tr :lipas.org/catalog-removed-in-use-warning)]
           [:ul {:style {:margin "4px 0 0 0"}}
            (for [{:keys [key label count]} removed-in-use]
              [:li {:key key} (str label " — " (tr :lipas.org/template-in-use count))])]])

        [:> Button
         {:variant "contained" :color "secondary" :sx {:mt 1}
          :on-click #(rf/dispatch [::events/edit-template-catalog org-id editor])}
         [:> Icon {:sx {:mr 1}} "save"]
         (tr :actions/save)]

        [ownership-editor tr org-id]]

       ;; --- read-only view (org-admin / member) ---
       (if (seq catalog)
         [:> Table
          [:> TableHead
           [:> TableRow
            [:> TableCell (tr :lipas.org/template-label)]
            [:> TableCell (tr :lipas.org/template-grants)]
            [:> TableCell {:align "right"} (tr :lipas.org/members-assigned)]]]
          [:> TableBody
           (for [[k entry] catalog]
             [:> TableRow {:key (name k)}
              [:> TableCell (or (:label entry) (name k))]
              [:> TableCell (template-grants-text tr entry)]
              [:> TableCell {:align "right"} (get counts (name k) 0)]])]]
         [:> Typography {:color "text.secondary"} (tr :lipas.org/no-templates)]))]))

;; ---------------------------------------------------------------------------
;; Our sites tab
;; ---------------------------------------------------------------------------

(defn- editor-row
  "One line in the unified who-can-edit list: name on the left, a reason tag chip
  on the right (the chip carries the revoke action for shared grants; the tag
  explains in a tooltip how the access is granted)."
  [{:keys [label tag color tooltip on-delete]}]
  (let [chip [:> Chip {:label tag :size "small" :variant "outlined" :color (or color "default")
                       :on-delete on-delete}]]
    [:> Box {:sx {:display "flex" :align-items "center" :justify-content "space-between"
                  :py 0.5 :gap 1}}
     [:> Typography {:variant "body2"} label]
     (if tooltip
       [:> Tooltip {:title tooltip :arrow true} chip]
       chip)]))

(def ^:private edit-history-display-limit 50)

(defn site-edit-history-section
  "Per-revision edit history for the expanded site, lazily fetched on expand
  and cached per lipas-id. Branches on which key the backend sent (GDPR, F38):
  rows carry `:author` (email, lipas-admins with :users/manage only) OR
  `:author-role` (coarse role label — admin/municipality/organization/other —
  no person identifier, everyone else). Capped to the most recent revisions to
  keep the accordion DOM bounded on long-lived sites."
  [tr lipas-id]
  (let [history @(rf/subscribe [::subs/site-edit-history lipas-id])
        total   (count history)
        shown   (take edit-history-display-limit history)]
    [:<>
     [:> Divider {:sx {:my 2}}]
     [:> Typography {:variant "subtitle2" :sx {:mb 1}}
      (str (tr :lipas.org/edit-history) " (" total ")")]
     (if (seq history)
       [:<>
        [:> Table {:size "small"}
         [:> TableHead
          [:> TableRow
           [:> TableCell (tr :lipas.org/edit-history-when)]
           [:> TableCell (tr :lipas.org/edit-history-who)]]]
         [:> TableBody
          (for [[i row] (map-indexed vector shown)]
            [:> TableRow {:key i}
             [:> TableCell (some-> (:event-date row) (subs 0 16))]
             [:> TableCell
              (cond
                (:author row)      (:author row)
                (:author-role row) (tr (keyword "lipas.org"
                                                (str "history-role-" (:author-role row))))
                :else              "–")]])]]
        (when (> total edit-history-display-limit)
          [:> Typography {:variant "caption" :color "text.secondary"}
           (tr :lipas.org/edit-history-truncated)])]
       [:> Typography {:variant "body2" :color "text.secondary"}
        (tr :lipas.org/edit-history-empty)])]))

(defn site-editors-detail [_tr _org-id _site]
  (let [grant-target (r/atom nil)]
    (fn [tr org-id site]
      (let [lipas-id      (:lipas-id site)
            editors       @(rf/subscribe [::subs/site-editors lipas-id])
            can-grant?    @(rf/subscribe [::subs/can? :org/grant-site-edit org-id])
            user-orgs     @(rf/subscribe [::subs/user-orgs])
            grant-options (->> user-orgs
                               (remove #(= (str (:id %)) (str org-id)))
                               (map (fn [o] {:value (str (:id o)) :label (:name o)})))
            ;; One scannable list answering "who can edit", each entry tagged by
            ;; WHY it has access (owner / shared grant / activity / direct user).
            rows (concat
                   (when-let [o (:owner-org editors)]
                     [{:key (str "owner-" (:id o)) :label (:name o)
                       :tag (tr :lipas.org/role-owner) :color "primary"
                       :tooltip (tr :lipas.org/role-owner-tooltip)}])
                   (for [g (:grantee-orgs editors)]
                     {:key (str "grant-" (:id g)) :label (:name g)
                      :tag (tr :lipas.org/role-shared)
                      :tooltip (tr :lipas.org/role-shared-tooltip)
                      :on-delete (when can-grant?
                                   (fn [] (rf/dispatch [::events/revoke-site-edit
                                                        org-id lipas-id (str (:id g))])))})
                   ;; orgs whose role-template catalog grants full edit on this
                   ;; site (catalog city/type/site-manager templates, F16)
                   (for [c (:catalog-editor-orgs editors)]
                     {:key (str "cat-" (:id c)) :label (:name c)
                      :tag (tr :lipas.org/role-catalog)
                      :tooltip (tr :lipas.org/role-catalog-tooltip)})
                   (for [a (:activity-editor-orgs editors)]
                     {:key (str "act-" (:id a)) :label (:name a)
                      :tag (tr :lipas.org/role-activity)
                      :tooltip (tr :lipas.org/role-activity-tooltip)})
                   (for [u (:legacy-users editors)]
                     {:key (str "legacy-" (:email u)) :label (:email u)
                      :tag (tr :lipas.org/role-direct)
                      :tooltip (tr :lipas.org/role-direct-tooltip)})
                   ;; direct activity-only users: can edit the site's UTP data
                   ;; but not the site itself (same tag as activity orgs)
                   (for [u (:legacy-activity-users editors)]
                     {:key (str "legacy-act-" (:email u)) :label (:email u)
                      :tag (tr :lipas.org/role-activity)
                      :tooltip (tr :lipas.org/role-direct-activity-tooltip)}))]
        [:> Box {:sx {:p 2 :bgcolor "action.hover"}}
         [:> Typography {:variant "subtitle2" :sx {:mb 1}}
          (tr :lipas.org/who-can-edit-site)]

         (into [:> Box {:sx {:max-width 480}}]
               (for [row rows]
                 ^{:key (:key row)} [editor-row row]))

         ;; share edit access with another org
         (when (and can-grant? (seq grant-options))
           [:<>
            [:> Divider {:sx {:my 2}}]
            [:> Typography {:variant "caption" :color "text.secondary"}
             (tr :lipas.org/share-edit-access)]
            [:> Box {:sx {:display "flex" :gap 1 :align-items "flex-end" :mt 1}}
             [:> Box {:sx {:min-width 220}}
              [selects/select
               {:label (tr :lipas.org/grant-to-org)
                :value @grant-target
                :items grant-options
                :on-change #(reset! grant-target %)}]]
             [:> Button
              {:variant "outlined" :size "small"
               :disabled (nil? @grant-target)
               :on-click (fn []
                           (rf/dispatch [::events/grant-site-edit org-id lipas-id @grant-target])
                           (reset! grant-target nil))}
              (tr :lipas.org/grant-edit)]]])

         ;; edit history (timestamp + editor) for the members maintaining the data
         [site-edit-history-section tr lipas-id]]))))

(defn sortable-th
  "A clickable header cell. `sort*` is the current {:col :dir} state; clicking
  toggles direction on the active column or switches to this one (asc)."
  [{:keys [sort* on-sort col label align]}]
  (let [active? (= (:col sort*) col)]
    [:> TableCell (when align {:align align})
     [:> TableSortLabel
      {:active active?
       :direction (if active? (name (:dir sort*)) "asc")
       :onClick #(on-sort col)}
      label]]))

(defn our-sites-tab [_tr _org-id]
  (let [expanded (r/atom nil)
        ;; column sort state for the sites table (client-side)
        sort* (r/atom {:col :name :dir :asc})]
    (fn [tr org-id]
      ;; One dataset drives everything: the org's editable sites (owned ∪ granted)
      ;; from bulk-operations, each row flagged `:owned?`. Browsing, the owned vs
      ;; shared split, content filtering, and bulk-edit selection are all facets
      ;; of this single list — no separate "owned" vs "editable" fetches.
      (let [filters       @(rf/subscribe [::bulk-ops-subs/sites-filters])
            ownership-flt  (:ownership filters)
            editable       @(rf/subscribe [::bulk-ops-subs/editable-sites])
            sites          @(rf/subscribe [::bulk-ops-subs/filtered-editable-sites])
            selected       @(rf/subscribe [::bulk-ops-subs/selected-sites])
            current-step   @(rf/subscribe [::bulk-ops-subs/current-step])
            sites-error    @(rf/subscribe [::bulk-ops-subs/error])
            ;; members without :site/create-edit get a read-only list: no
            ;; selection checkboxes, no mass-update launcher
            can-bulk-edit? @(rf/subscribe [::subs/can-bulk-edit? org-id])
            types          @(rf/subscribe [:lipas.ui.sports-sites.subs/active-types])
            cities         @(rf/subscribe [:lipas.ui.sports-sites.subs/cities-by-city-code])
            locale         (tr)
            bulk-editing?  (pos? current-step)
            ;; counts label the owned/shared/all segments (the "is it working?" cue)
            all-count      (count editable)
            owned-count    (count (filter :owned? editable))
            shared-count   (- all-count owned-count)
            ;; client-side column sort; accessors resolve the same display values
            ;; the cells render (type/city names come from the registry subs)
            sort-state     @sort*
            accessor       {:name        :name
                            :type        #(get-in types [(get-in % [:type :type-code]) :name locale])
                            :city        #(get-in cities [(get-in % [:location :city :city-code]) :name locale])
                            :last-edited :event-date}
            sort-key-fn    (fn [s] (str/lower-case (str ((accessor (:col sort-state)) s))))
            sorted-sites   (cond->> (sort-by sort-key-fn sites)
                             (= (:dir sort-state) :desc) reverse)
            on-sort        (fn [col]
                             (swap! sort* (fn [{c :col d :dir}]
                                            (if (= c col)
                                              {:col col :dir (if (= d :asc) :desc :asc)}
                                              {:col col :dir :asc}))))
            site-ids       (map :lipas-id sites)
            all-selected?  (and (seq site-ids) (every? selected site-ids))]
        ;; min-width 0 + overflow-x auto keeps wide children (table, bulk-ops)
        ;; from widening the whole page
        [:> Box {:sx {:p 2 :min-width 0 :overflow-x "auto"}}
         (if bulk-editing?
           ;; Bulk-edit mode: selection happened in the list, so the wizard runs
           ;; in external-selection mode (Enter info → Summary). Done/Back/cancel
           ;; reset the bulk state, returning to the list below.
           [bulk-ops-views/main
            {:title (tr :lipas.org/bulk-operations)
             :external-selection? true
             :on-cancel #(rf/dispatch [::bulk-ops-events/reset])}]

           [:<>
            ;; ownership segment (with live counts) — the now-correct owned vs
            ;; granted-by-another-org split (shared = editable minus owned)
            [:> Box {:sx {:display "flex" :gap 1 :mb 2 :align-items "center" :flex-wrap "wrap"}}
             [:> Button {:variant (if (str/blank? (str ownership-flt)) "contained" "outlined")
                         :size "small"
                         :on-click #(rf/dispatch [::bulk-ops-events/set-sites-filter :ownership nil])}
              (str (tr :lipas.org/all-sites) " (" all-count ")")]
             [:> Button {:variant (if (= ownership-flt "owned") "contained" "outlined")
                         :size "small"
                         :on-click #(rf/dispatch [::bulk-ops-events/set-sites-filter :ownership "owned"])}
              (str (tr :lipas.org/owned) " (" owned-count ")")]
             [:> Button {:variant (if (= ownership-flt "granted") "contained" "outlined")
                         :size "small"
                         :on-click #(rf/dispatch [::bulk-ops-events/set-sites-filter :ownership "granted"])}
              (str (tr :lipas.org/shared-with-us) " (" shared-count ")")]]

            ;; content filters — find sites to view / bulk-edit
            [:> Box {:sx {:display "flex" :gap 1 :mb 2 :align-items "center" :flex-wrap "wrap"}}
             [:> Box {:sx {:min-width 200}}
              [text-fields/text-field-controlled
               {:label (tr :search/search)
                :value (:search-text filters)
                :on-change #(rf/dispatch [::bulk-ops-events/set-sites-filter :search-text %])}]]
             [:> Box {:sx {:min-width 200}}
              (r/as-element
                [ac/type-selector
                 {:value (:type-code filters)
                  :label (tr :type/name)
                  :onChange (fn [_ {:keys [value]}]
                              (rf/dispatch [::bulk-ops-events/set-sites-filter :type-code value]))}])]
             ;; no owner filter: take-over locks :owner to the org type's enum, so
             ;; all of an org's sites share one owner value — filtering by it is moot
             [:> Box {:sx {:min-width 200}}
              (r/as-element
                [ac/admin-selector
                 {:value (:admin filters)
                  :label (tr :lipas.sports-site/admin)
                  :onChange (fn [_ {:keys [value]}]
                              (rf/dispatch [::bulk-ops-events/set-sites-filter :admin value]))}])]]

            ;; selection action bar — launches the bulk-edit wizard for the
            ;; checked sites (the headline "edit contact info on many sites")
            (when (and can-bulk-edit? (seq selected))
              [:> Box {:sx {:display "flex" :gap 1 :mb 2 :align-items "center"
                            :p 1 :bgcolor "action.hover" :border-radius 1}}
               [:> Typography {:variant "body2"}
                (tr :lipas.bulk-operations/n-sites-selected (count selected))]
               [:> Button {:variant "contained" :size "small" :sx {:ml "auto"}
                           :on-click #(rf/dispatch [::bulk-ops-events/set-current-step 1])}
                [:> Icon {:sx {:mr 1}} "edit"]
                (tr :lipas.org/bulk-operations)]
               [:> Button {:variant "text" :size "small"
                           :on-click #(rf/dispatch [::bulk-ops-events/deselect-all-sites])}
                (tr :actions/deselect-all)]])

            (cond
              ;; list fetch failed — say so instead of showing an empty list
              ;; under a badge with a non-zero count
              sites-error
              [:> Alert {:severity "error"}
               (tr :lipas.org/sites-load-failed)]

              (seq sites)
              [:> Box {:sx {:overflow-x "auto" :width "100%"}}
               [:> Table {:size "small"}
                [:> TableHead
                 [:> TableRow
                  (when can-bulk-edit?
                    [:> TableCell {:padding "checkbox"}
                     [:> Checkbox {:checked all-selected?
                                   :indeterminate (boolean (and (not all-selected?)
                                                                (some selected site-ids)))
                                   :on-change #(if all-selected?
                                                 (rf/dispatch [::bulk-ops-events/deselect-all-sites])
                                                 (rf/dispatch [::bulk-ops-events/select-all-sites site-ids]))}]])
                  [sortable-th {:sort* sort-state :on-sort on-sort :col :name
                                :label (tr :lipas.org/name)}]
                  [sortable-th {:sort* sort-state :on-sort on-sort :col :type
                                :label (tr :lipas.org/type-col)}]
                  [sortable-th {:sort* sort-state :on-sort on-sort :col :city
                                :label (tr :lipas.org/city)}]
                  [sortable-th {:sort* sort-state :on-sort on-sort :col :last-edited
                                :label (tr :lipas.org/last-edited)}]
                  [:> TableCell {:align "right"} (tr :lipas.org/who-can-edit)]]]
                (into
                  [:> TableBody]
                  (mapcat
                    (fn [site]
                      (let [lipas-id (:lipas-id site)
                            open? (= @expanded lipas-id)]
                        (cond-> [[:> TableRow {:key lipas-id
                                               :selected (boolean (selected lipas-id))}
                                  (when can-bulk-edit?
                                    [:> TableCell {:padding "checkbox"}
                                     [:> Checkbox {:checked (boolean (selected lipas-id))
                                                   :on-change #(rf/dispatch [::bulk-ops-events/toggle-site-selection lipas-id])}]])
                                  [:> TableCell
                                   [:> Box {:sx {:display "flex" :align-items "center" :gap 1}}
                                    [:span (:name site)]
                                    ;; mark sites we can edit but don't own
                                    (when-not (:owned? site)
                                      [:> Chip {:size "small" :variant "outlined" :color "secondary"
                                                :label (tr :lipas.org/shared-with-us)}])]]
                                  [:> TableCell (get-in types [(get-in site [:type :type-code]) :name locale])]
                                  [:> TableCell (get-in cities [(get-in site [:location :city :city-code]) :name locale])]
                                  [:> TableCell (some-> (:event-date site) (.substring 0 10))]
                                  [:> TableCell {:align "right"}
                                   [:> IconButton
                                    {:size "small"
                                     :on-click (fn []
                                                 (if open?
                                                   (reset! expanded nil)
                                                   (do (reset! expanded lipas-id)
                                                       (rf/dispatch [::events/get-site-editors lipas-id])
                                                       (rf/dispatch [::events/get-site-edit-history lipas-id]))))}
                                    [:> Icon (if open? "expand_less" "expand_more")]]]]]
                          open?
                          (conj [:> TableRow {:key (str lipas-id "-detail")}
                                 [:> TableCell {:colSpan (if can-bulk-edit? 6 5) :sx {:p 0}}
                                  [site-editors-detail tr org-id site]]]))))
                    sorted-sites))]]

              :else
              [:> Typography {:color "text.secondary"} (tr :lipas.org/no-sites)])])]))))

;; ---------------------------------------------------------------------------
;; History tab
;; ---------------------------------------------------------------------------

(defn history-tab [tr _org-id]
  (let [history @(rf/subscribe [::subs/org-history])]
    [:> Box {:sx {:p 2}}
     (if (seq history)
       [:> Table {:size "small"}
        [:> TableHead
         [:> TableRow
          [:> TableCell (tr :lipas.org/event-date)]
          [:> TableCell (tr :lipas.org/changed-by)]
          [:> TableCell "Muutokset"]]]
        [:> TableBody
         (for [{:keys [event-date author-name changes] :as rev} history]
           [:> TableRow {:key (str event-date "-" (:id rev))}
            [:> TableCell (some-> event-date (.substring 0 19) (str/replace "T" " "))]
            [:> TableCell (or author-name "–")]
            [:> TableCell
             (if (seq changes)
               [:> Box {:sx {:display "flex" :flex-direction "column"}}
                (for [c changes]
                  [:> Typography {:key c :variant "body2"} c])]
               "–")]])]]
       [:> Typography {:color "text.secondary"} (tr :lipas.org/no-history)])]))

;; ---------------------------------------------------------------------------
;; Site-page integration: "Editing rights" panel (UX plan §5)
;; ---------------------------------------------------------------------------

(defn editing-rights-panel
  "Mounted as a tab on the sports-site page: owner org + derived editors, with
  the inline grant control for the owner-org admin. Reuses site-editors-detail."
  [{:keys [tr lipas-id owner-org-id]}]
  ;; site-editors-detail renders site-edit-history-section, which reads
  ;; ::site-edit-history — so this panel must fetch BOTH (the our-sites tab
  ;; fetches edit-history itself; this standalone mount has to as well).
  (r/with-let [_ (do (rf/dispatch [::events/get-site-editors lipas-id])
                     (rf/dispatch [::events/get-site-edit-history lipas-id]))]
    [:> Box {:sx {:p 2}}
     [:> Typography {:variant "h6" :sx {:mb 1}} (tr :lipas.org/editing-rights)]
     [site-editors-detail tr (str owner-org-id) {:lipas-id lipas-id}]]))

;; ---------------------------------------------------------------------------
;; Claim impact warning (shown before requesting OR approving a take-over)
;; ---------------------------------------------------------------------------

(def ^:private claim-page-size 10)

(defn claim-impact-dialog [_tr]
  (let [page (r/atom 0)
        name-filter (r/atom "")]
    (fn [tr]
      (let [dialog @(rf/subscribe [::subs/claim-dialog])
            preview @(rf/subscribe [::subs/takeover-preview])
            selection @(rf/subscribe [::subs/claim-selection])
            is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])
            reclaiming? @(rf/subscribe [::subs/reclaiming?])
            locale (tr)
            ;; localized owner-class label (fall back to the raw enum, then "–")
            owner-label (fn [enum] (or (get-in owners/all [enum locale]) enum "–"))
            ;; request/reclaim modes get the curated picker (checkboxes);
            ;; approve mode shows the request's STORED selection read-only —
            ;; the requester curates, the approver verifies
            picker? (not= "approve" (:mode dialog))
            sites (vec (:sites preview))
            total (count sites)
            n-selected (count selection)
            ;; client-side name filter — narrows the table AND what the header
            ;; select-all/none toggles; selection outside the filter is kept
            filter-text (str/trim @name-filter)
            visible (if (str/blank? filter-text)
                      sites
                      (filterv #(str/includes? (str/lower-case (str (:name %)))
                                               (str/lower-case filter-text))
                               sites))
            n-visible (count visible)
            visible-ids (mapv :lipas-id visible)
            all-visible? (and (pos? n-visible) (every? selection visible-ids))
            some-visible? (boolean (some selection visible-ids))
            pages (max 1 (js/Math.ceil (/ n-visible claim-page-size)))
            cur (min @page (dec pages))
            from (* cur claim-page-size)
            to (min (+ from claim-page-size) n-visible)
            ;; don't allow closing mid-reclaim (the op keeps running server-side)
            close! (fn [] (when-not reclaiming?
                            (reset! page 0)
                            (reset! name-filter "")
                            (rf/dispatch [::events/close-claim-dialog])))]
        [:> Dialog {:open (boolean dialog)
                    :maxWidth "sm" :fullWidth true
                    :onClose close!}
         [:> DialogTitle (tr :lipas.org/claim-impact-title)]
         [:> DialogContent
          (if (nil? preview)
            [:> Typography "…"]
            [:> Box
             [:> Typography {:variant "h6" :sx {:mb 1}}
              (str (tr (if picker?
                         :lipas.org/claim-impact-affects
                         :lipas.org/claim-impact-requested)) ": "
                   (if (and picker? (< n-selected total))
                     ;; a curated subset is active — make the boundary explicit
                     (str n-selected " / " total " " (tr :lipas.org/claim-selected))
                     (:count preview)))]
             ;; what the reclaim does: (1) assign ownership to this org, and
             ;; (2) lock the owner class to the org type's owner (shown as label)
             [:> Alert {:severity "warning" :sx {:my 1}}
              [:> Box {:component "ul" :sx {:m 0 :pl 2}}
               [:> Box {:component "li"}
                (str (tr :lipas.org/claim-impact-assign) ": "
                     (or (:owner-org-name preview) "—"))]
               [:> Box {:component "li"}
                (str (tr :lipas.org/claim-impact-lock) ": "
                     (owner-label (:owner-enum preview)))]]]
             (when (pos? total)
               [:> Box {:sx {:mt 2}}
                [:> Typography {:variant "subtitle2" :sx {:mb 1}}
                 (tr :lipas.org/claim-impact-sample)]
                [:> TextField {:size "small" :fullWidth true :sx {:mb 1}
                               :placeholder (tr :lipas.org/claim-filter-by-name)
                               :value @name-filter
                               :on-change (fn [e]
                                            (reset! name-filter (.. e -target -value))
                                            (reset! page 0))}]
                [:> Table {:size "small"}
                 [:> TableHead
                  [:> TableRow
                   (when picker?
                     [:> TableCell {:padding "checkbox"}
                      [:> Checkbox {:size "small"
                                    :checked all-visible?
                                    :indeterminate (and some-visible? (not all-visible?))
                                    :disabled (or reclaiming? (zero? n-visible))
                                    :on-change #(rf/dispatch [::events/select-claim-sites
                                                              visible-ids (not all-visible?)])}]])
                   [:> TableCell (tr :lipas.org/name)]
                   [:> TableCell (tr :lipas.org/current-owner)]]]
                 [:> TableBody
                  (for [s (subvec visible from to)]
                    [:> TableRow {:key (:lipas-id s)}
                     (when picker?
                       [:> TableCell {:padding "checkbox"}
                        [:> Checkbox {:size "small"
                                      :checked (contains? selection (:lipas-id s))
                                      :disabled reclaiming?
                                      :on-change #(rf/dispatch [::events/toggle-claim-site
                                                                (:lipas-id s)])}]])
                     [:> TableCell (:name s)]
                     [:> TableCell (owner-label (:current-owner s))]])]]
                (when (> pages 1)
                  [:> Box {:sx {:display "flex" :align-items "center"
                                :justify-content "flex-end" :gap 1 :mt 1}}
                   [:> Typography {:variant "caption" :color "text.secondary"}
                    (str (inc from) "–" to " / " n-visible)]
                   [:> IconButton {:size "small" :disabled (<= cur 0)
                                   :on-click #(swap! page dec)}
                    [:> Icon "chevron_left"]]
                   [:> IconButton {:size "small" :disabled (>= cur (dec pages))
                                   :on-click #(swap! page inc)}
                    [:> Icon "chevron_right"]]])])])]
         [:> DialogActions
          (when reclaiming?
            [:> Typography {:variant "caption" :color "text.secondary" :sx {:mr "auto" :ml 1}}
             (tr :lipas.org/reclaiming)])
          [:> Button {:on-click close! :disabled reclaiming?}
           (tr :lipas.org/cancel)]
          [:> Button {:variant "contained" :color "secondary"
                      :disabled (or (nil? preview)
                                    reclaiming?
                                    (if picker?
                                      (zero? n-selected)
                                      (zero? (:count preview 0))))
                      :startIcon (when reclaiming?
                                   (r/as-element [:> CircularProgress {:size 16 :color "inherit"}]))
                      :on-click #(rf/dispatch [::events/confirm-claim])}
           (tr (cond
                 (= "approve" (:mode dialog)) :lipas.org/approve-impact-confirm
                 ;; lipas-admin applies directly (no request→self-approve round-trip)
                 is-lipas-admin? :lipas.org/reclaim-now-confirm
                 :else :lipas.org/claim-impact-confirm))]]]))))

;; ---------------------------------------------------------------------------
;; Setup checklist (LIPAS-admin one-time org setup)
;; ---------------------------------------------------------------------------

(defn setup-checklist
  "Action-oriented orientation for the rare, one-time setup a LIPAS admin does:
  set type+ownership rule → create role models → reclaim the org's sites. Steps
  are derived from the loaded org doc + owned-site count; the banner auto-hides
  once all three are satisfied. Non-blocking — each step jumps to where the work
  actually happens (no separate wizard screens to maintain)."
  [tr org-id]
  (let [is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])
        org @(rf/subscribe [::subs/editing-org])
        owned-count @(rf/subscribe [::subs/owned-sites-count])
        claim-dialog @(rf/subscribe [::subs/claim-dialog])
        rule (:ownership org)
        step1-done? (and (boolean (:type org))
                         (boolean (some #(seq (get rule %)) [:city-codes :owners :type-codes :activities])))
        step2-done? (boolean (seq (:role-templates org)))
        step3-done? (pos? owned-count)]
    (when (and is-lipas-admin? (not (and step1-done? step2-done? step3-done?)))
      [:> Paper {:variant "outlined" :sx {:p 2 :mb 3 :bgcolor "action.hover"}}
       [:> Typography {:variant "subtitle1" :sx {:mb 1 :font-weight "bold"}}
        (tr :lipas.org/setup-title)]
       [:> Stepper {:nonLinear true :alternativeLabel true}
        [:> Step {:completed step1-done?}
         [:> StepButton {:onClick #(rf/dispatch [::events/set-current-tab "roles-templates"])}
          [:> StepLabel (tr :lipas.org/setup-step-type)]]]
        [:> Step {:completed step2-done?}
         [:> StepButton {:onClick #(rf/dispatch [::events/set-current-tab "roles-templates"])}
          [:> StepLabel (tr :lipas.org/setup-step-roles)]]]
        [:> Step {:completed step3-done?}
         ;; disabled while the dialog is open: a click that closes the dialog
         ;; must not be able to re-open it through this button underneath
         [:> StepButton {:disabled (some? claim-dialog)
                         :onClick #(rf/dispatch [::events/open-claim-dialog
                                                 {:mode "request" :org-id org-id}])}
          [:> StepLabel (tr :lipas.org/setup-step-reclaim)]]]]])))

;; ---------------------------------------------------------------------------
;; Org detail view (tabbed)
;; ---------------------------------------------------------------------------

(defn org-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        {:keys [org-id]} (:path @(rf/subscribe [::ui-subs/parameters]))
        org @(rf/subscribe [::subs/editing-org])
        current-tab @(rf/subscribe [::subs/current-tab])
        is-new? (= "new" org-id)]

    ;; min-width 0 lets the Paper shrink inside its flex parent; overflow-x
    ;; hidden clamps it to the column width so wide tab content (Our sites table,
    ;; bulk-ops) scrolls within its own container instead of widening the page.
    [:> Paper {:sx {:p 3 :m 2 :min-width 0 :overflow-x "hidden"}}
     [claim-impact-dialog tr]
     [:> Typography {:variant "h4" :sx {:mb 3}}
      (if is-new?
        (tr :lipas.org/new-organization)
        (:name org))]

     (when-not is-new?
       [setup-checklist tr org-id])

     (when-not is-new?
       [:> Tabs {:value current-tab
                 :variant "scrollable"
                 :scrollButtons "auto"
                 :on-change (fn [_ value] (rf/dispatch [::events/set-current-tab value]))
                 :sx {:mb 3 :border-bottom 1 :border-color "divider"}}
        [:> Tab {:label (tr :lipas.org/instructions-tab) :value "instructions"}]
        [:> Tab {:label (tr :lipas.org/our-sites-tab) :value "our-sites"}]
        [:> Tab {:label (tr :lipas.org/overview-tab) :value "overview"}]
        [:> Tab {:label (tr :lipas.org/members-tab) :value "members"}]
        [:> Tab {:label (tr :lipas.org/permissions-ownership-tab) :value "roles-templates"}]
        [:> Tab {:label (tr :lipas.org/ptv-tab) :value "ptv"}]
        (when @(rf/subscribe [::subs/can? :org/view-history org-id])
          [:> Tab {:label (tr :lipas.org/history-tab) :value "history"}])])

     (case (if is-new? "overview" current-tab)
       "instructions" [instructions-tab tr org-id]
       "overview" [overview-tab tr org-id]
       "members" [members-tab tr org-id]
       "roles-templates" [roles-templates-tab tr org-id]
       "our-sites" [our-sites-tab tr org-id]
       "ptv" [ptv-tab]
       "history" (when @(rf/subscribe [::subs/can? :org/view-history org-id])
                   [history-tab tr org-id])
       ;; legacy routes
       "contact" [overview-tab tr org-id]
       "bulk-operations" [our-sites-tab tr org-id]
       nil)]))

;; ---------------------------------------------------------------------------
;; Take-over approval queue (lipas-admin)
;; ---------------------------------------------------------------------------

(defn takeover-approvals [tr]
  (let [requests @(rf/subscribe [::subs/takeover-requests])]
    (when (seq requests)
      [:> Paper {:variant "outlined" :sx {:p 2 :mb 2}}
       [:> Typography {:variant "h6" :sx {:mb 1}} (tr :lipas.org/pending-approvals)]
       [:> Table {:size "small"}
        [:> TableHead
         [:> TableRow
          [:> TableCell (tr :lipas.org/owner-org)]
          [:> TableCell "Kohteita"]
          [:> TableCell {:align "right"} (tr :actions/actions)]]]
        [:> TableBody
         (for [req requests]
           [:> TableRow {:key (str (:id req))}
            [:> TableCell (str (:org-id req))]
            [:> TableCell (count (:lipas-ids req))]
            [:> TableCell {:align "right"}
             [:> Button {:size "small" :color "primary"
                         :on-click #(rf/dispatch [::events/open-claim-dialog
                                                  {:mode "approve"
                                                   :org-id (str (:org-id req))
                                                   :request-id (:id req)}])}
              (tr :lipas.org/approve)]
             [:> Button {:size "small" :color "error"
                         :on-click #(rf/dispatch [::events/decide-takeover (:id req) "deny"])}
              (tr :lipas.org/deny)]]])]]])))

;; ---------------------------------------------------------------------------
;; Organizations list
;; ---------------------------------------------------------------------------

(defn org-card [tr org]
  (let [is-org-admin? @(rf/subscribe [::subs/is-org-admin? (str (:id org))])]
    [:> Paper {:sx {:p 2 :cursor "pointer"}
               :on-click #(rfe/navigate :lipas.ui.routes/org
                                        {:path-params {:org-id (str (:id org))}})}
     [:> Box {:sx {:display "flex" :align-items "center" :gap 1}}
      [:> Typography {:variant "h6" :sx {:flex 1}} (:name org)]
      (when is-org-admin?
        [:> Chip {:size "small" :color "primary" :label (tr :lipas.org/admin)}])]
     [:> Stack {:direction "row" :spacing 1 :sx {:mt 1}}
      (when (:type org)
        [:> Chip {:size "small" :variant "outlined"
                  :label (org-type-label tr (:type org))}])
      [:> Chip {:size "small" :variant "outlined"
                :label (str (count (:members org)) " " (tr :lipas.org/members-assigned))}]
      [:> Chip {:size "small" :variant "outlined"
                :label (str (:site-count org 0) " " (tr :lipas.org/sites-owned))}]]]))

(defn orgs-list-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        user-orgs @(rf/subscribe [::subs/user-orgs])
        is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])]
    [:> Paper {:sx {:p 3 :m 2}}
     [claim-impact-dialog tr]
     [:> Typography {:variant "h4" :sx {:mb 3}}
      (tr :lipas.admin/organizations)]

     (when is-lipas-admin?
       [:> Fab
        {:color "secondary"
         :size "small"
         :sx {:mb 2}
         :on-click #(rfe/navigate :lipas.ui.routes/org {:path-params {:org-id "new"}})}
        [:> Icon "add"]])

     ;; lipas-admin take-over approval queue
     (when is-lipas-admin?
       [takeover-approvals tr])

     (if (seq user-orgs)
       [:> Grid {:container true :spacing 2}
        (for [org user-orgs]
          [:> Grid {:item true :xs 12 :md 6 :key (:id org)}
           [org-card tr org]])]

       [:> Paper {:sx {:p 3 :text-align "center"}}
        [:> Typography {:variant "h6" :color "text.secondary"}
         (tr :lipas.org/no-organizations)]
        [:> Typography {:variant "body2" :color "text.secondary" :sx {:mt 1}}
         (tr :lipas.org/contact-admin)]])]))

(defn bulk-operations-view []
  ;; Legacy route — bulk-ops now lives inside the Our sites tab.
  (let [{:keys [org-id]} (:path @(rf/subscribe [::ui-subs/parameters]))]
    (rf/dispatch [::events/set-current-tab "our-sites"])
    (rfe/replace-state :lipas.ui.routes/org {:path-params {:org-id org-id}})))
