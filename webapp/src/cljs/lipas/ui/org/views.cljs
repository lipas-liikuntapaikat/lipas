(ns lipas.ui.org.views
  (:require ["@mui/icons-material/Delete$default" :as DeleteIcon]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/Chip$default" :as Chip]
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
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.schema.org :as org-schema]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.ui.bulk-operations.views :as bulk-ops-views]
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
   ["sports-federation" :lipas.org/type-sports-federation]
   ["other" :lipas.org/type-other]])

(defn org-type-label [tr type]
  (some (fn [[k tr-key]] (when (= k type) (tr tr-key))) org-type-options))

(defn role-grant-text
  "Plain-language description of a single catalog role-spec."
  [tr {:keys [role activity]}]
  (case role
    "org-editor" (tr :lipas.org/grants-org-editor)
    "ptv-manager" (tr :lipas.org/grants-ptv)
    "activities-manager" (str (tr :lipas.org/grants-activity)
                              (when (seq activity) (str ": " (str/join ", " activity))))
    (tr :lipas.org/grants-unknown)))

(defn template-grants-text
  "Plain-language description of everything a catalog template grants."
  [tr template]
  (->> (:roles template)
       (map #(role-grant-text tr %))
       (remove str/blank?)
       (str/join "; ")))

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

      ;; Ownership rule (the ceiling for ownership claims) — lipas-admin only
      [:> Divider {:sx {:my 1}}]
      [:> Typography {:variant "h6"} (tr :lipas.org/ownership-rule)]
      [:> Typography {:variant "body2" :color "text.secondary"}
       (tr :lipas.org/ownership-rule-help)]
      (when-not can-type?
        [:> Typography {:variant "caption" :color "text.secondary"}
         (tr :lipas.org/type-locked-note)])
      [selects/city-selector
       {:label (tr :lipas.org.ptv/cities-label)
        :value (get-in org [:ownership :city-codes] [])
        :disabled (not can-type?)
        :on-change (fn [v] (rf/dispatch [::events/edit-org [:ownership :city-codes] v]))}]
      [selects/owner-selector
       {:label (tr :lipas.org.ptv/owners-label)
        :value (get-in org [:ownership :owners] [])
        :disabled (not can-type?)
        :on-change (fn [v] (rf/dispatch [::events/edit-org [:ownership :owners] v]))}]

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

(defn invite-member [tr org-id]
  (let [form @(rf/subscribe [::subs/invite-member-form])
        template-opts @(rf/subscribe [::subs/member-template-options])
        is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])
        all-users @(rf/subscribe [::subs/all-users-options])]
    [:> Box {:sx {:display "flex" :flex-direction "row" :flex-wrap "wrap"
                  :gap 1 :align-items "flex-start" :mb 2}}
     ;; Email (canonical path)
     [text-fields/text-field-controlled
      {:label (tr :lipas.org/email)
       :value (:email form)
       :type "email"
       :spec sites-schema/email
       :on-change #(rf/dispatch [::events/set-invite-member-form [:email] %])
       :sx {:min-width 220}}]

     ;; lipas-admin convenience: pick an existing account
     (when is-lipas-admin?
       (r/as-element
         [ac/autocomplete2
          {:sx #js {:minWidth 220}
           :label (tr :lipas.user/email)
           :options all-users
           :value (:user-id form)
           :onChange (fn [_e v] (rf/dispatch [::events/set-invite-member-form [:user-id] (ac/safe-value v)]))}]))

     ;; Org-role
     [:> FormControl {:sx {:min-width 140}}
      [:> InputLabel {:id "invite-org-role"} (tr :lipas.org/org-role)]
      [:> Select
       {:labelId "invite-org-role"
        :value (or (:org-role form) "member")
        :label (tr :lipas.org/org-role)
        :onChange (fn [e] (rf/dispatch [::events/set-invite-member-form [:org-role] (.. e -target -value)]))}
       [:> MenuItem {:value "member"} (tr :lipas.org/member)]
       [:> MenuItem {:value "admin"} (tr :lipas.org/admin)]]]

     ;; Templates (catalog only)
     (when (seq template-opts)
       [:> Box {:sx {:min-width 220}}
        [selects/multi-select
         {:label (tr :lipas.org/assigned-templates)
          :value (vec (:templates form))
          :items template-opts
          :on-change #(rf/dispatch [::events/set-invite-member-form [:templates] (vec %)])}]])

     [:> Button
      {:variant "contained" :color "primary" :sx {:mt 1}
       :on-click #(rf/dispatch [::events/invite-member org-id])}
      (tr :lipas.org/invite)]]))

(defn member-templates-cell
  "Template chips for one member with an add-menu, all gated by can-manage?."
  [_props]
  (let [anchor (r/atom nil)]
    (fn [{:keys [tr org-id member catalog can-manage?]}]
      (let [assigned (vec (:templates member))
            user-id (:id member)
            unassigned (remove (set assigned) (map name (keys catalog)))]
        [:> Box {:sx {:display "flex" :flex-wrap "wrap" :gap 0.5 :align-items "center"}}
         (for [tkey assigned
               :let [entry (get catalog (keyword tkey))]]
           [:> Tooltip {:key tkey :title (template-grants-text tr entry)}
            [:> Chip
             (cond-> {:label (or (:label entry) tkey)
                      :size "small"}
               can-manage?
               (assoc :on-delete
                      (fn [] (rf/dispatch [::events/set-member-templates org-id user-id
                                           (vec (remove #(= % tkey) assigned))]))))]])
         (when (and can-manage? (seq unassigned))
           [:<>
            [:> IconButton {:size "small"
                            :on-click (fn [e] (reset! anchor (.-currentTarget e)))}
             [:> Icon {:fontSize "small"} "add"]]
            [:> Menu {:open (boolean @anchor)
                      :anchorEl @anchor
                      :onClose (fn [] (reset! anchor nil))}
             (for [tkey unassigned
                   :let [entry (get catalog (keyword tkey))]]
               [:> MenuItem
                {:key tkey
                 :onClick (fn []
                            (rf/dispatch [::events/set-member-templates org-id user-id
                                          (conj assigned tkey)])
                            (reset! anchor nil))}
                (or (:label entry) tkey)])]])]))))

(defn members-tab [tr org-id]
  (let [org-users @(rf/subscribe [::subs/org-users])
        catalog @(rf/subscribe [::subs/org-templates])
        can-manage? @(rf/subscribe [::subs/can? :org/manage-members org-id])]
    [:> Box {:sx {:p 2}}
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
          [:> TableCell (tr :lipas.org/org-role)]
          [:> TableCell (tr :lipas.org/assigned-templates)]
          (when can-manage?
            [:> TableCell {:align "right"} (tr :actions/actions)])]]
        [:> TableBody
         (for [member org-users]
           [:> TableRow {:key (:id member)}
            [:> TableCell (or (:email member) (:username member))]
            [:> TableCell
             (if can-manage?
               [:> Select
                {:value (or (:org-role member) "member")
                 :size "small"
                 :variant "standard"
                 :onChange (fn [e] (rf/dispatch [::events/set-member-org-role
                                                 org-id (:id member) (.. e -target -value)]))}
                [:> MenuItem {:value "member"} (tr :lipas.org/member)]
                [:> MenuItem {:value "admin"} (tr :lipas.org/admin)]]
               (tr (if (= "admin" (:org-role member))
                     :lipas.org/admin :lipas.org/member)))]
            [:> TableCell
             [member-templates-cell {:tr tr :org-id org-id :member member
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
  "Map template-key (string) -> number of members currently assigned it."
  [org-users]
  (->> org-users
       (mapcat :templates)
       (frequencies)))

(defn catalog-editor
  "lipas-admin editor for the role-template catalog. Edits a local copy in
  app-db; the Save button (in roles-templates-tab) PUTs the whole catalog."
  [tr _org-id]
  (let [catalog @(rf/subscribe [::subs/catalog-editor])]
    [:> Box
     (for [[k entry] catalog]
       [:> Paper {:key (name k) :variant "outlined" :sx {:p 2 :mb 1}}
        [:> Box {:sx {:display "flex" :align-items "center" :gap 1}}
         [:> Typography {:variant "subtitle1" :sx {:flex 1}}
          (str (or (:label entry) (name k)) " (" (name k) ")")]
         [:> IconButton
          {:size "small" :color "error"
           :on-click #(rf/dispatch [::events/set-catalog-editor (dissoc catalog k)])}
          [:> DeleteIcon]]]
        [:> Typography {:variant "body2" :color "text.secondary"}
         (template-grants-text tr entry)]])]))

(defn add-template-form [tr]
  (let [draft (r/atom {:key "" :label "" :role "org-editor" :city-codes [] :activities ""})]
    (fn [_tr]
      (let [d @draft
            role (:role d)]
        [:> Paper {:variant "outlined" :sx {:p 2 :mt 2}}
         [:> Typography {:variant "subtitle1" :sx {:mb 1}} (tr :lipas.org/add-template)]
         [:> Stack {:spacing 1}
          [text-fields/text-field-controlled
           {:label "key" :value (:key d)
            :on-change #(swap! draft assoc :key %)}]
          [text-fields/text-field-controlled
           {:label (tr :lipas.org/template-label) :value (:label d)
            :on-change #(swap! draft assoc :label %)}]
          [:> FormControl
           [:> InputLabel {:id "tmpl-role"} (tr :lipas.org/template-grants)]
           [:> Select {:labelId "tmpl-role" :value role
                       :label (tr :lipas.org/template-grants)
                       :onChange (fn [e] (swap! draft assoc :role (.. e -target -value)))}
            [:> MenuItem {:value "org-editor"} (tr :lipas.org/grants-org-editor)]
            [:> MenuItem {:value "ptv-manager"} (tr :lipas.org/grants-ptv)]
            [:> MenuItem {:value "activities-manager"} (tr :lipas.org/grants-activity)]]]
          (when (= role "ptv-manager")
            [selects/city-selector
             {:label (tr :lipas.org.ptv/cities-label)
              :value (:city-codes d)
              :on-change #(swap! draft assoc :city-codes %)}])
          (when (= role "activities-manager")
            [text-fields/text-field-controlled
             {:label (tr :lipas.org/activity-editors)
              :value (:activities d)
              :helper-text "esim. melonta, retkeily"
              :on-change #(swap! draft assoc :activities %)}])
          [:> Button
           {:variant "contained" :color "primary"
            :disabled (str/blank? (:key d))
            :on-click
            (fn []
              (let [role-spec (cond-> {:role role}
                                (= role "ptv-manager")
                                (assoc :city-code (vec (:city-codes d)))
                                (= role "activities-manager")
                                (assoc :activity (->> (str/split (:activities d) #",")
                                                      (map str/trim)
                                                      (remove str/blank?)
                                                      vec)))
                    entry {:label (:label d) :roles [role-spec]}
                    catalog @(rf/subscribe [::subs/catalog-editor])]
                (rf/dispatch [::events/set-catalog-editor
                              (assoc catalog (keyword (:key d)) entry)])
                (reset! draft {:key "" :label "" :role "org-editor" :city-codes [] :activities ""})))}
           (tr :lipas.org/add-template)]]]))))

(defn roles-templates-tab [tr org-id]
  (let [catalog @(rf/subscribe [::subs/org-templates])
        org-users @(rf/subscribe [::subs/org-users])
        counts (template-member-counts org-users)
        can-edit? @(rf/subscribe [::subs/can? :org/edit-catalog org-id])
        editor @(rf/subscribe [::subs/catalog-editor])]
    [:> Box {:sx {:p 2}}
     (when-not can-edit?
       [:> Alert {:severity "info" :sx {:mb 2}}
        (tr :lipas.org/catalog-readonly-note)])

     (if can-edit?
       ;; --- editable view (lipas-admin); seeded on tab switch in ::set-current-tab ---
       [:> Box
        [catalog-editor tr org-id]
        [add-template-form tr]
        [:> Button
         {:variant "contained" :color "secondary" :sx {:mt 2}
          :on-click #(rf/dispatch [::events/edit-template-catalog org-id (or editor catalog)])}
         [:> Icon {:sx {:mr 1}} "save"]
         (tr :actions/save)]]

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

(defn site-editors-detail [_tr _org-id _site]
  (let [grant-target (r/atom nil)]
    (fn [tr org-id site]
      (let [lipas-id (:lipas-id site)
            editors @(rf/subscribe [::subs/site-editors lipas-id])
            can-grant? @(rf/subscribe [::subs/can? :org/grant-site-edit org-id])
            user-orgs @(rf/subscribe [::subs/user-orgs])
            grantees (:grantee-orgs editors)
            grant-options (->> user-orgs
                               (remove #(= (str (:id %)) (str org-id)))
                               (map (fn [o] {:value (str (:id o)) :label (:name o)})))]
        [:> Box {:sx {:p 2 :bgcolor "action.hover"}}
         [:> Typography {:variant "subtitle2"} (tr :lipas.org/who-can-edit)]
         [:> Typography {:variant "body2"}
          (str (tr :lipas.org/owner-org) ": "
               (or (:name (:owner-org editors)) "–"))]

         ;; grantee orgs (with revoke)
         [:> Box {:sx {:mt 1}}
          [:> Typography {:variant "body2"} (tr :lipas.org/grantee-orgs) ":"]
          (if (seq grantees)
            [:> Box {:sx {:display "flex" :flex-wrap "wrap" :gap 0.5 :mt 0.5}}
             (for [g grantees]
               [:> Chip {:key (str (:id g))
                         :label (:name g)
                         :size "small"
                         :on-delete (when can-grant?
                                      (fn [] (rf/dispatch [::events/revoke-site-edit
                                                           org-id lipas-id (str (:id g))])))}])]
            [:> Typography {:variant "caption" :color "text.secondary"} "–"])]

         ;; activity editors
         (when (seq (:activity-editor-orgs editors))
           [:> Typography {:variant "body2" :sx {:mt 1}}
            (str (tr :lipas.org/activity-editors) ": "
                 (str/join ", " (map :name (:activity-editor-orgs editors))))])

         ;; legacy users
         (when (seq (:legacy-users editors))
           [:> Typography {:variant "body2" :sx {:mt 1}}
            (str (tr :lipas.org/legacy-users) ": "
                 (str/join ", " (map :email (:legacy-users editors))))])

         ;; grant control
         (when (and can-grant? (seq grant-options))
           [:> Box {:sx {:display "flex" :gap 1 :align-items "flex-end" :mt 2}}
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
             (tr :lipas.org/grant-edit)]])]))))

(defn our-sites-tab [_tr _org-id]
  (let [expanded (r/atom nil)]
    (fn [tr org-id]
      (let [flt @(rf/subscribe [::subs/our-sites-filter])
            sites @(rf/subscribe [::subs/our-sites])
            org @(rf/subscribe [::subs/editing-org])
            can-grant? @(rf/subscribe [::subs/can? :org/grant-site-edit org-id])
            ownership-rule (:ownership org)]
        ;; min-width 0 + overflow-x auto keeps wide children (table, bulk-ops)
        ;; from widening the whole page
        [:> Box {:sx {:p 2 :min-width 0 :overflow-x "auto"}}
         ;; filter toggle
         [:> Box {:sx {:display "flex" :gap 1 :mb 2 :align-items "center"}}
          [:> Button {:variant (if (= flt "owned") "contained" "outlined")
                      :size "small"
                      :on-click #(rf/dispatch [::events/set-our-sites-filter "owned"])}
           (tr :lipas.org/owned)]
          [:> Button {:variant (if (= flt "editable") "contained" "outlined")
                      :size "small"
                      :on-click #(rf/dispatch [::events/set-our-sites-filter "editable"])}
           (tr :lipas.org/editable)]

          ;; claim ownership
          (when (and can-grant? (seq (:city-codes ownership-rule)) (seq (:owners ownership-rule)))
            [:> Button {:variant "outlined" :color "secondary" :size "small"
                        :sx {:ml "auto"}
                        :on-click #(rf/dispatch [::events/request-takeover org-id])}
             [:> Icon {:sx {:mr 1}} "flag"]
             (tr :lipas.org/claim-ownership)])]

         (if (seq sites)
           [:> Box {:sx {:overflow-x "auto" :width "100%"}}
            [:> Table {:size "small"}
             [:> TableHead
              [:> TableRow
               [:> TableCell (tr :lipas.org/name)]
               [:> TableCell (tr :lipas.org/type-col)]
               [:> TableCell (tr :lipas.org/city)]
               [:> TableCell (tr :lipas.org/last-edited)]
               [:> TableCell {:align "right"} (tr :lipas.org/who-can-edit)]]]
             (into
               [:> TableBody]
               (mapcat
                 (fn [site]
                   (let [lipas-id (:lipas-id site)
                         open? (= @expanded lipas-id)]
                     (cond-> [[:> TableRow {:key lipas-id}
                               [:> TableCell (:name site)]
                               [:> TableCell (:type-name site)]
                               [:> TableCell (:city-name site)]
                               [:> TableCell (some-> (:event-date site) (.substring 0 10))]
                               [:> TableCell {:align "right"}
                                [:> IconButton
                                 {:size "small"
                                  :on-click (fn []
                                              (if open?
                                                (reset! expanded nil)
                                                (do (reset! expanded lipas-id)
                                                    (rf/dispatch [::events/get-site-editors lipas-id]))))}
                                 [:> Icon (if open? "expand_less" "expand_more")]]]]]
                       open?
                       (conj [:> TableRow {:key (str lipas-id "-detail")}
                              [:> TableCell {:colSpan 5 :sx {:p 0}}
                               [site-editors-detail tr org-id site]]]))))
                 sites))]]
           [:> Typography {:color "text.secondary"} (tr :lipas.org/no-sites)])

         ;; Bulk operations (folded in)
         [:> Divider {:sx {:my 3}}]
         [:> Typography {:variant "h6" :sx {:mb 1}} (tr :lipas.org/bulk-operations)]
         [bulk-ops-views/main
          {:title nil
           :description nil
           :on-cancel #(rf/dispatch [::events/set-current-tab "overview"])}]]))))

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
  (r/with-let [_ (rf/dispatch [::events/get-site-editors lipas-id])]
    [:> Box {:sx {:p 2}}
     [:> Typography {:variant "h6" :sx {:mb 1}} (tr :lipas.org/editing-rights)]
     [site-editors-detail tr (str owner-org-id) {:lipas-id lipas-id}]]))

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
     [:> Typography {:variant "h4" :sx {:mb 3}}
      (if is-new?
        (tr :lipas.org/new-organization)
        (:name org))]

     (when-not is-new?
       [:> Tabs {:value current-tab
                 :variant "scrollable"
                 :scrollButtons "auto"
                 :on-change (fn [_ value] (rf/dispatch [::events/set-current-tab value]))
                 :sx {:mb 3 :border-bottom 1 :border-color "divider"}}
        [:> Tab {:label (tr :lipas.org/overview-tab) :value "overview"}]
        [:> Tab {:label (tr :lipas.org/members-tab) :value "members"}]
        [:> Tab {:label (tr :lipas.org/roles-templates-tab) :value "roles-templates"}]
        [:> Tab {:label (tr :lipas.org/our-sites-tab) :value "our-sites"}]
        [:> Tab {:label (tr :lipas.org/ptv-tab) :value "ptv"}]
        [:> Tab {:label (tr :lipas.org/history-tab) :value "history"}]])

     (case (if is-new? "overview" current-tab)
       "overview" [overview-tab tr org-id]
       "members" [members-tab tr org-id]
       "roles-templates" [roles-templates-tab tr org-id]
       "our-sites" [our-sites-tab tr org-id]
       "ptv" [ptv-tab]
       "history" [history-tab tr org-id]
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
                         :on-click #(rf/dispatch [::events/decide-takeover (:id req) "approve"])}
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
                :label (str (count (:members org)) " " (tr :lipas.org/members-assigned))}]]]))

(defn orgs-list-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        user-orgs @(rf/subscribe [::subs/user-orgs])
        is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])]
    [:> Paper {:sx {:p 3 :m 2}}
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
