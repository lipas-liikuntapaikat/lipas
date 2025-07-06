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
        is-org-member? @(rf/subscribe [::subs/is-org-member? org-id])]

    (println "Is org member? " is-org-member?)
    (println "Is org admin? " is-org-admin?)
    (println "Is LIPAS admin? " is-lipas-admin?)

    [mui/grid {:container true
               :spacing 2
               :sx {:p 1}}
     [mui/grid {:item true :container true :xs 12 :spacing 1}
      [lui/form-card
       {:title "Organisaatio"
        :md 12}
       [mui/form-group
        {:sx {:gap 1}}
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

        [mui/grid {:item true}
         [mui/box {:sx {:display "flex" :gap 2}}
          (when (or is-lipas-admin? is-org-admin?)
            [mui/button
             {:variant "contained"
              :color "secondary"
              :disabled (not org-valid?)
              :on-click #(rf/dispatch [::events/save-org org])}
             [mui/icon {:sx {:mr 1}} "save"]
             (tr :actions/save)])
          (when (or is-lipas-admin? is-org-admin? is-org-member?)
            [mui/button
             {:variant "outlined"
              :on-click #(rfe/navigate :lipas.ui.routes/org-bulk-operations {:path-params {:org-id org-id}})}
             [mui/icon {:sx {:mr 1}} "update"]
             (tr :lipas.org/bulk-operations)])]]]]

      [lui/form-card
       {:title (tr :lipas.org/users-section)
        :md 12}
       ;; Only show user management form for admins
       (when (or is-lipas-admin? is-org-admin?)
         (if is-lipas-admin?
           [admin-user-management tr org-id] ; LIPAS admin form
           [org-admin-user-management tr org-id])) ; Org admin form
       ;; Show members list to all org members
       [:> Table
        [:> TableHead
         [:> TableRow
          [:> TableCell (tr :lipas.user/username)]
          [:> TableCell (tr :lipas.org/org-role)]]]
        [:> TableBody
         (for [item org-users]
           [:> TableRow
            {:key (:id item)}
            [:> TableCell (:username item)]
            [:> TableCell
             (for [{:keys [role] :as x} (-> item :permissions :roles)
                   ;; These users HAVE role for this org, but this value contains all the user roles so filter to find the
                   ;; current org roles:
                   :when (contains? (set (:org-id x)) org-id)]
               [:span {:key (str (:id item) "-" role)}
                (tr (keyword :lipas.user.permissions.roles.role-names role))
                ;; Only show delete button for admins
                (when (or is-lipas-admin? is-org-admin?)
                  [:> Button
                   {:size "small"
                    :color "error"
                    :on-click (fn [_e]
                                (rf/dispatch [::events/org-user-update org-id (:id item) role "remove"]))}
                   [:> DeleteIcon]])])]])]]]]]))

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
             (:name org)]
            (when-let [phone (get-in org [:data :primary-contact :phone])]
              [mui/typography {:variant "body2" :color "text.secondary"}
               phone])]])]

       [mui/paper {:sx {:p 3 :text-align "center"}}
        [mui/typography {:variant "h6" :color "text.secondary"}
         (tr :lipas.org/no-organizations)]
        [mui/typography {:variant "body2" :color "text.secondary" :sx {:mt 1}}
         (tr :lipas.org/contact-admin)]])]))

(defn bulk-operations-view []
  (let [{:keys [org-id]} (:path @(rf/subscribe [::ui-subs/parameters]))]
    [bulk-ops-views/main
     {:on-cancel #(rfe/navigate :lipas.ui.routes/org {:path-params {:org-id org-id}})}]))
