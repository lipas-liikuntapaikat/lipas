(ns lipas.ui.admin.views
  (:require [lipas.ui.admin.events :as events]
            [lipas.ui.admin.subs :as subs]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn user-dialog [tr]
  (let [locale    (tr)
        cities    (<== [::subs/cities-list locale])
        types     (<== [::subs/types-list locale])
        sites     (<== [::subs/sites-list])
        user      (<== [::subs/editing-user])
        existing? (some? (:id user))]

    [lui/full-screen-dialog
     {:open?          (boolean (seq user))
      :title          (or (:username user)
                          (:email user))
      :close-label    (tr :actions/close)
      :on-close       #(==> [::events/set-user-to-edit nil])
      :bottom-actions
      [[lui/email-button
        {:label    (tr :lipas.admin/magic-link)
         :on-click #(==> [:lipas.ui.events/confirm
                          (tr :lipas.admin/confirm-magic-link (:email user))
                          (fn [] (==> [::events/send-magic-link user]))])}]
       (when existing?
         [lui/save-button
          {:tooltip  (tr :actions/save)
           :on-click #(==> [::events/save-user user])}])]}

     [mui/grid {:container true :spacing 8}

      ;;; Contact info
      [lui/form-card {:title (tr :lipas.user/contact-info)}
       [mui/form-group

        ;; Email
        [lui/text-field
         {:label     (tr :lipas.user/email)
          :value     (:email user)
          :on-change #(==> [::events/edit-user [:email] %])
          :disabled  existing?}]

        ;; Username
        [lui/text-field
         {:label     (tr :lipas.user/username)
          :value     (:username user)
          :on-change #(==> [::events/edit-user [:username] %])
          :disabled  existing?}]

        ;; Firstname
        [lui/text-field
         {:label     (tr :lipas.user/firstname)
          :value     (-> user :user-data :firstname)
          :on-change #(==> [::events/edit-user [:user-data :firstname] %])
          :disabled  existing?}]

        ;; Lastname
        [lui/text-field
         {:label     (tr :lipas.user/lastname)
          :value     (-> user :user-data :lastname)
          :on-change #(==> [::events/edit-user [:user-data :lastname] %])
          :disabled  existing?}]]]

      ;;; Permissions
      [lui/form-card {:title (tr :lipas.user/permissions)}
       [mui/form-group

        [mui/card {:style {:background-color mui/gray3
                           :margin-bottom    "1em"}}
         [mui/card-header {:subheader (tr :lipas.user/requested-permissions)}]
         [mui/card-content
          [mui/typography
           [:i (or (-> user :user-data :permissions-request)
                   "-")]]]]

        ;; Admin?
        [lui/checkbox
         {:label     (tr :lipas.user.permissions/admin?)
          :value     (-> user :permissions :admin?)
          :on-change #(==> [::events/edit-user [:permissions :admin?] %])}]

        ;; Permission to all types?
        [lui/checkbox
         {:label     (tr :lipas.user.permissions/all-types?)
          :value     (-> user :permissions :all-types?)
          :on-change #(==> [::events/edit-user [:permissions :all-types?] %])}]

        ;; Permission to all cities?
        [lui/checkbox
         {:label     (tr :lipas.user.permissions/all-cities?)
          :value     (-> user :permissions :all-cities?)
          :on-change #(==> [::events/edit-user [:permissions :all-cities?] %])}]

        ;; Permission to individual spoorts-sites
        [lui/autocomplete
         {:items     sites
          :label     (tr :lipas.user.permissions/sports-sites)
          :value     (-> user :permissions :sports-sites)
          :on-change #(==> [::events/edit-user [:permissions :sports-sites] %])}]

        ;; Permission to individual types
        [lui/autocomplete
         {:items     types
          :label     (tr :lipas.user.permissions/types)
          :value     (-> user :permissions :types)
          :on-change #(==> [::events/edit-user [:permissions :types] %])}]

        ;; Permission to individual cities
        [lui/autocomplete
         {:items     cities
          :label     (tr :lipas.user.permissions/cities)
          :value     (-> user :permissions :cities)
          :on-change #(==> [::events/edit-user [:permissions :cities] %])}]]
       ]]]))


(defn admin-panel []
  (let [tr    (<== [:lipas.ui.subs/translator])
        users (<== [::subs/users-list])]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/card {:square true}
       [mui/card-content
        [mui/typography {:variant :headline}
         (tr :lipas.admin/users)]

        ;; Full-screen user dialog
        [user-dialog tr]

        ;; Magic link button
        [mui/button {:variant  :fab
                     :color    :secondary
                     :mini     true
                     :style {:margin-top "1em"}
                     :on-click #(==> [::events/edit-user [:email] "fix@me.com"])}
         [mui/icon "add"]]

        ;; Users table
        [lui/table
         {:headers
          [[:email (tr :lipas.user/email)]
           [:firstname (tr :lipas.user/firstname)]
           [:lastname (tr :lipas.user/lastname)]
           [:sports-sites (tr :lipas.user.permissions/sports-sites)]
           [:cities (tr :lipas.user.permissions/cities)]
           [:types (tr :lipas.user.permissions/types)]]
          :sort-fn   :email
          :items     users
          :on-select #(==> [::events/set-user-to-edit %])}]]]]]))

(defn main []
  (let [admin? (<== [:lipas.ui.user.subs/admin?])]
    (if admin?
      (do
        (==> [::events/get-users])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 3110])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 3130])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 2510])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 2520])
        [admin-panel])
      (==> [:lipas.ui.events/navigate "/#/"]))))
