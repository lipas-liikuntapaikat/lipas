(ns lipas.ui.org.views
  (:require ["@mui/icons-material/Delete$default" :as DeleteIcon]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/InputLabel$default" :as InputLabel]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            [clojure.string :as str]
            [lipas.schema.org :as org-schema]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.ui.bulk-operations.views :as bulk-ops-views]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.org.events :as events]
            [lipas.ui.org.subs :as subs]
            [lipas.ui.subs :as ui-subs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn ptv-tab []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        org @(rf/subscribe [::subs/editing-org])
        is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])
        ptv-config (or (:ptv-data org) {})
        ptv-enabled? (and (:sync-enabled ptv-config)
                          (not (str/blank? (:org-id ptv-config))))]

    [mui/box {:sx {:p 2}}
     [mui/typography {:variant "h5" :sx {:mb 2}}
      (tr :lipas.org.ptv/prefix) (tr :lipas.org/ptv-integration)]

     ;; PTV Integration Status Banner
     [mui/alert {:severity (if ptv-enabled? "success" "info")
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
     [mui/form-group {:sx {:gap 2 :max-width 800}}

      ;; PTV Organization ID
      [text-fields/text-field-controlled
       {:label (tr :lipas.org.ptv/org-id-label)
        :value (:org-id ptv-config)
        :placeholder (tr :lipas.org.ptv/org-id-placeholder)
        :helper-text (tr :lipas.org.ptv/org-id-helper)
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

      ;; Test environment credentials
      [mui/typography {:variant "h6" :sx {:mt 2 :mb 1}}
       (tr :lipas.org.ptv/test-credentials-title)]

      [mui/box {:sx {:pl 2}}
       [text-fields/text-field-controlled
        {:label (tr :lipas.org.ptv/test-username-label)
         :value (get-in ptv-config [:test-credentials :username])
         :placeholder (tr :lipas.org.ptv/test-username-placeholder)
         :disabled (not is-lipas-admin?)
         :on-change #(rf/dispatch [::events/edit-org [:ptv-data :test-credentials :username] %])}]

       [text-fields/text-field-controlled
        {:label (tr :lipas.org.ptv/test-password-label)
         :type "password"
         :value (get-in ptv-config [:test-credentials :password])
         :placeholder (tr :lipas.org.ptv/test-password-placeholder)
         :disabled (not is-lipas-admin?)
         :on-change #(rf/dispatch [::events/edit-org [:ptv-data :test-credentials :password] %])}]]

      ;; City codes
      [mui/typography {:variant "h6" :sx {:mt 2 :mb 1}}
       (tr :lipas.org.ptv/municipality-codes-title)]

      [selects/city-selector
       {:label (tr :lipas.org.ptv/cities-label)
        :value (:city-codes ptv-config [])
        :disabled (not is-lipas-admin?)
        :on-change (fn [value]
                     (rf/dispatch [::events/edit-org [:ptv-data :city-codes] value]))}]

      ;; Owners
      [mui/typography {:variant "h6" :sx {:mt 2 :mb 1}}
       (tr :lipas.org.ptv/ownership-types-title)]

      [selects/owner-selector
       {:label (tr :lipas.org.ptv/owners-label)
        :value (:owners ptv-config [])
        :disabled (not is-lipas-admin?)
        :on-change #(rf/dispatch [::events/edit-org [:ptv-data :owners] %])}]

      ;; Supported languages
      [mui/typography {:variant "h6" :sx {:mt 2 :mb 1}}
       (tr :lipas.org.ptv/supported-languages-title)]

      [selects/multi-select
       {:label (tr :lipas.org.ptv/languages-label)
        :value (:supported-languages ptv-config [])
        :items [{:value "fi" :label (tr :lipas.org.ptv/finnish-label)}
                {:value "se" :label (tr :lipas.org.ptv/swedish-label)}
                {:value "en" :label (tr :lipas.org.ptv/english-label)}]
        :disabled (not is-lipas-admin?)
        :on-change #(rf/dispatch [::events/edit-org [:ptv-data :supported-languages] %])}]

      ;; Sync enabled
      [mui/form-control-label
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
        [mui/button
         {:variant "contained"
          :color "primary"
          :on-click #(rf/dispatch [::events/save-ptv-config])
          :sx {:mt 3 :align-self "flex-start"}}
         [mui/icon {:sx {:mr 1}} "save"]
         (tr :lipas.org.ptv/save-configuration)])

      ;; Info message for non-admins
      (when (not is-lipas-admin?)
        [mui/alert {:severity "info" :sx {:mt 2}}
         (tr :lipas.org.ptv/admin-only-message)])]]))

;; Component for LIPAS admins who can see all users
(defn admin-user-management [tr org-id]
  (let [add-form @(rf/subscribe [::subs/add-user-form])
        all-users @(rf/subscribe [::subs/all-users-options])]
    [:> Box
     {:sx {:display "flex"
           :flex-direction "row"
           :gap 1
           :align-items "center"}}
     ;; User autocomplete dropdown
     (r/as-element
      [ac/autocomplete2
       {:sx #js {:minWidth 250}
        :label (tr :lipas.user/email)
        :options all-users
        :value (:user-id add-form)
        :onChange (fn [_e v] (rf/dispatch [::events/set-add-user-form [:user-id] (ac/safe-value v)]))}])
     ;; Role selector
     [:> FormControl
      {:sx {:min-width 120}}
      [:> InputLabel
       {:id "add-org-user-admin"}
       (tr :lipas.org/org-role)]
      [:> Select
       {:value (or (:role add-form) "org-user")
        :labelId "add-org-user-admin"
        :onChange (fn [e] (rf/dispatch [::events/set-add-user-form [:role] (.-value (.-target e))]))}
       [:> MenuItem {:value "org-user"} (tr (keyword :lipas.user.permissions.roles.role-names :org-user))]
       [:> MenuItem {:value "org-admin"} (tr (keyword :lipas.user.permissions.roles.role-names :org-admin))]]]
     ;; Add button
     [:> Button
      {:variant "contained"
       :color "primary"
       :on-click #(rf/dispatch [::events/add-user-to-org org-id])}
      (tr :lipas.org/add-user)]]))

;; Component for org admins who add users by email
(defn org-admin-user-management [tr org-id]
  (let [add-form @(rf/subscribe [::subs/add-user-email-form])]
    [:> Box
     {:sx {:display "flex"
           :flex-direction "row"
           :gap 1
           :align-items "center"}}
     ;; Email input field
     [text-fields/text-field-controlled
      {:label (tr :lipas.user/email)
       :value (:email add-form)
       :type "email"
       :spec sites-schema/email
       :on-change #(rf/dispatch [::events/set-add-user-email-form [:email] %])
       :sx {:min-width 200}}]
     ;; Role selector
     [:> FormControl
      {:sx {:min-width 120}}
      [:> InputLabel
       {:id "add-org-user-email"}
       (tr :lipas.org/org-role)]
      [:> Select
       {:value (or (:role add-form) "org-user")
        :labelId "add-org-user-email"
        :onChange (fn [e] (rf/dispatch [::events/set-add-user-email-form [:role] (.-value (.-target e))]))}
       [:> MenuItem {:value "org-user"} (tr (keyword :lipas.user.permissions.roles.role-names :org-user))]
       [:> MenuItem {:value "org-admin"} (tr (keyword :lipas.user.permissions.roles.role-names :org-admin))]]]
     ;; Add button
     [:> Button
      {:variant "contained"
       :color "primary"
       :on-click #(rf/dispatch [::events/add-user-by-email org-id])}
      (tr :lipas.org/add-user-by-email)]]))

(defn org-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        {:keys [org-id]} (:path @(rf/subscribe [::ui-subs/parameters]))
        org @(rf/subscribe [::subs/editing-org])
        org-users @(rf/subscribe [::subs/org-users])
        org-valid? @(rf/subscribe [::subs/org-valid?])
        is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])
        is-org-admin? @(rf/subscribe [::subs/is-org-admin? org-id])
        is-org-member? @(rf/subscribe [::subs/is-org-member? org-id])
        current-tab @(rf/subscribe [::subs/current-tab])
        is-new? (= "new" org-id)]

    [mui/paper {:sx {:p 3 :m 2}}
     ;; Organization name as headline
     [mui/typography {:variant "h4" :sx {:mb 3}}
      (if is-new?
        (tr :lipas.org/new-organization)
        (:name org))]

     ;; Tabs - only show if not new
     (when-not is-new?
       [mui/tabs {:value current-tab
                  :on-change (fn [_ value] (rf/dispatch [::events/set-current-tab value]))
                  :sx {:mb 3 :border-bottom 1 :border-color "divider"}}
        [mui/tab {:label (tr :lipas.org/contact-info-tab) :value "contact"}]
        [mui/tab {:label (tr :lipas.org/members-tab) :value "members"}]
        [mui/tab {:label (tr :lipas.org/bulk-operations-tab) :value "bulk-operations"}]
        [mui/tab {:label (tr :lipas.org/ptv-tab) :value "ptv"}]])

     ;; Tab panels
     (case (if is-new? "contact" current-tab)
       "contact"
       [mui/box {:sx {:p 2}}
        [mui/form-group {:sx {:gap 2 :max-width 600}}
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/name)
           :value (:name org)
           :spec org-schema/org-name
           :required true
           :disabled (not (or is-lipas-admin? is-org-admin? is-new?))
           :on-change #(rf/dispatch [::events/edit-org [:name] %])}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/phone)
           :value (get-in org [:data :primary-contact :phone])
           :spec sites-schema/phone-number
           :disabled (not (or is-lipas-admin? is-org-admin? is-new?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :phone] x]))}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/email)
           :value (get-in org [:data :primary-contact :email])
           :spec sites-schema/email
           :disabled (not (or is-lipas-admin? is-org-admin? is-new?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :email] x]))}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/website)
           :value (get-in org [:data :primary-contact :website])
           :spec sites-schema/www
           :disabled (not (or is-lipas-admin? is-org-admin? is-new?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :website] x]))}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/reservations-link)
           :value (get-in org [:data :primary-contact :reservations-link])
           :spec sites-schema/reservations-link
           :disabled (not (or is-lipas-admin? is-org-admin? is-new?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :reservations-link] x]))}]

         (when (or is-lipas-admin? is-org-admin? is-new?)
           [mui/button
            {:variant "contained"
             :color "secondary"
             :disabled (not org-valid?)
             :on-click #(rf/dispatch [::events/save-org org])
             :sx {:mt 2 :align-self "flex-start"}}
            [mui/icon {:sx {:mr 1}} "save"]
            (tr :actions/save)])]]

       "members"
       [mui/box {:sx {:p 2}}
        ;; Only show user management form for admins
        (when (or is-lipas-admin? is-org-admin?)
          [:> Box {:sx {:mb 3}}
           (if is-lipas-admin?
             [admin-user-management tr org-id]
             [org-admin-user-management tr org-id])])

        ;; Members table
        [:> Table
         [:> TableHead
          [:> TableRow
           [:> TableCell (tr :lipas.user/username)]
           [:> TableCell (tr :lipas.org/org-role)]
           (when (or is-lipas-admin? is-org-admin?)
             [:> TableCell {:align "right"} (tr :actions/actions)])]]
         [:> TableBody
          (for [item org-users]
            [:> TableRow {:key (:id item)}
             [:> TableCell (:username item)]
             [:> TableCell
              (for [{:keys [role] :as x} (-> item :permissions :roles)
                    :when (contains? (set (:org-id x)) org-id)]
                [:span {:key (str (:id item) "-" role)}
                 (tr (keyword :lipas.user.permissions.roles.role-names role))])]
             (when (or is-lipas-admin? is-org-admin?)
               [:> TableCell {:align "right"}
                (for [{:keys [role] :as x} (-> item :permissions :roles)
                      :when (contains? (set (:org-id x)) org-id)]
                  [:> Button
                   {:key (str (:id item) "-" role "-delete")
                    :size "small"
                    :color "error"
                    :on-click (fn [_e]
                                (rf/dispatch [::events/org-user-update org-id (:id item) role "remove"]))}
                   [:> DeleteIcon]])])])]]]

       "bulk-operations"
       [mui/box
        [bulk-ops-views/main
         {:title nil
          :description nil
          :on-cancel #(rf/dispatch [::events/set-current-tab "contact"])}]]

       "ptv"
       [ptv-tab]

       ;; Default case
       nil)]))

(defn orgs-list-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        user-orgs @(rf/subscribe [::subs/user-orgs])
        is-lipas-admin? @(rf/subscribe [::subs/is-lipas-admin])]
    [mui/paper {:sx {:p 3 :m 2}}
     [mui/typography {:variant "h4" :sx {:mb 3}}
      (tr :lipas.admin/organizations)]

     ;; Add organization button for LIPAS admins
     (when is-lipas-admin?
       [:> Fab
        {:color "secondary"
         :size "small"
         :sx {:mb 2}
         :on-click #(rfe/navigate :lipas.ui.routes/org {:path-params {:org-id "new"}})}
        [:> Icon "add"]])

     (if (seq user-orgs)
       [mui/grid {:container true :spacing 2}
        (for [org user-orgs]
          [mui/grid {:item true :xs 12 :md 6 :key (:id org)}
           [mui/paper {:sx {:p 2 :cursor "pointer"}
                       :on-click #(rfe/navigate :lipas.ui.routes/org {:path-params {:org-id (str (:id org))}})}
            [mui/typography {:variant "h6"}
             (:name org)]]])]

       [mui/paper {:sx {:p 3 :text-align "center"}}
        [mui/typography {:variant "h6" :color "text.secondary"}
         (tr :lipas.org/no-organizations)]
        [mui/typography {:variant "body2" :color "text.secondary" :sx {:mt 1}}
         (tr :lipas.org/contact-admin)]])]))

(defn bulk-operations-view []
  ;; This view is now integrated into org-view as a tab
  ;; Redirect to the org view with the bulk-operations tab selected
  (let [{:keys [org-id]} (:path @(rf/subscribe [::ui-subs/parameters]))]
    (println "HIP HOP")
    (rf/dispatch [::events/set-current-tab "bulk-operations"])
    (rfe/replace-state :lipas.ui.routes/org-bulk-operations {:path-params {:org-id org-id}})))
