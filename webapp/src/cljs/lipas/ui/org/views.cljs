(ns lipas.ui.org.views
  (:require ["@mui/icons-material/Delete$default" :as DeleteIcon]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/InputLabel$default" :as InputLabel]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.ui.bulk-operations.views :as bulk-ops-views]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.mui :as mui]
            [lipas.ui.org.events :as events]
            [lipas.ui.org.subs :as subs]
            [lipas.ui.subs :as ui-subs]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]
            [uix.core :refer [$]]))

(defn ptv-tab []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [mui/box {:sx {:p 2}}
     [mui/typography {:variant "h5" :sx {:mb 2}}
      "PTV " (tr :lipas.org/ptv-integration)]
     [mui/typography {:variant "body1" :color "text.secondary"}
      "TODO: PTV integration functionality will be implemented here."]
     [mui/typography {:variant "body2" :sx {:mt 2}}
      "This will include:"
      [:ul
       [:li "Organization's PTV integration settings that are currently hardcoded"]]]]))

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
     ($ ac/autocomplete2
        {:sx #js {:minWidth 250}
         :label (tr :lipas.user/email)
         :options all-users
         :value (:user-id add-form)
         :onChange (fn [_e v] (rf/dispatch [::events/set-add-user-form [:user-id] (ac/safe-value v)]))})
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
        current-tab @(rf/subscribe [::subs/current-tab])]

    [mui/paper {:sx {:p 3 :m 2}}
     ;; Organization name as headline
     [mui/typography {:variant "h4" :sx {:mb 3}}
      (:name org)]

     ;; Tabs
     [mui/tabs {:value current-tab
                :on-change (fn [_ value] (rf/dispatch [::events/set-current-tab value]))
                :sx {:mb 3 :border-bottom 1 :border-color "divider"}}
      [mui/tab {:label (tr :lipas.org/contact-info-tab) :value "contact"}]
      [mui/tab {:label (tr :lipas.org/members-tab) :value "members"}]
      [mui/tab {:label (tr :lipas.org/bulk-operations-tab) :value "bulk-operations"}]
      [mui/tab {:label (tr :lipas.org/ptv-tab) :value "ptv"}]]

     ;; Tab panels
     (case current-tab
       "contact"
       [mui/box {:sx {:p 2}}
        [mui/form-group {:sx {:gap 2 :max-width 600}}
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/name)
           :value (:name org)
           :spec [:string {:min 1 :max 128}]
           :required true
           :disabled (not (or is-lipas-admin? is-org-admin?))
           :on-change #(rf/dispatch [::events/edit-org [:name] %])}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/phone)
           :value (get-in org [:data :primary-contact :phone])
           :spec sites-schema/phone-number
           :disabled (not (or is-lipas-admin? is-org-admin?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :phone] x]))}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/email)
           :value (get-in org [:data :primary-contact :email])
           :spec sites-schema/email
           :disabled (not (or is-lipas-admin? is-org-admin?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :email] x]))}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/website)
           :value (get-in org [:data :primary-contact :website])
           :spec sites-schema/www
           :disabled (not (or is-lipas-admin? is-org-admin?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :website] x]))}]
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/reservations-link)
           :value (get-in org [:data :primary-contact :reservations-link])
           :spec sites-schema/reservations-link
           :disabled (not (or is-lipas-admin? is-org-admin?))
           :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :primary-contact :reservations-link] x]))}]

         (when (or is-lipas-admin? is-org-admin?)
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
        user-orgs @(rf/subscribe [::subs/user-orgs])]
    [mui/paper {:sx {:p 3 :m 2}}
     [mui/typography {:variant "h4" :sx {:mb 3}}
      (tr :lipas.admin/organizations)]

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
