(ns lipas.ui.admin.views
  (:require
   [clojure.spec.alpha :as s]
   [lipas.data.styles :as styles]
   [lipas.ui.admin.events :as events]
   [lipas.ui.admin.subs :as subs]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [re-frame.core :as rf]))

(defn magic-link-dialog [{:keys [tr]}]
  (let [open?    (<== [::subs/magic-link-dialog-open?])
        variants (<== [::subs/magic-link-variants])
        variant  (<== [::subs/selected-magic-link-variant])
        user     (<== [::subs/editing-user])]

    [mui/dialog {:open open?}
     [mui/dialog-title
      (tr :lipas.admin/send-magic-link (:email user))]
     [mui/dialog-content
      [mui/form-group
       [lui/select
        {:label     (tr :lipas.admin/select-magic-link-template)
         :items     variants
         :value     variant
         :on-change #(==> [::events/select-magic-link-variant %])}]
       [mui/button
        {:style    {:margin-top "1em"}
         :on-click #(==> [::events/send-magic-link user variant])}
        (tr :actions/submit)]
       [mui/button
        {:style    {:margin-top "1em"}
         :on-click #(==> [::events/close-magic-link-dialog])}
        (tr :actions/cancel)]]]]))

(defn user-dialog [tr]
  (let [locale     (tr)
        cities     (<== [::subs/cities-list locale])
        types      (<== [::subs/types-list locale])
        sites      (<== [::subs/sites-list])
        activities (<== [::subs/activities-list locale])
        user       (<== [::subs/editing-user])
        history    (<== [::subs/user-history])
        existing?  (some? (:id user))]

    [lui/full-screen-dialog
     {:open?       (boolean (seq user))
      :title       (or (:username user) (:email user))
      :close-label (tr :actions/close)
      :on-close    #(==> [::events/set-user-to-edit nil])
      :bottom-actions
      [;; GDPR remove button
       [mui/button
        {:variant  "contained"
         :color    "secondary"
         :on-click (fn []
                     (==> [:lipas.ui.events/confirm
                           "Haluatko varmasti GDPR-poistaa tämän käyttäjän?"
                           (fn []
                             (==> [::events/gdpr-remove-user user]))]))}
        [mui/icon {:sx {:mr 1}} "gpp_bad"]
        "GDPR-poista"]
       ;; Archive button
       (when (= "active" (:status user))
         [mui/button
          {:variant  "contained"
           :color    "secondary"
           :on-click #(==> [::events/update-user-status user "archived"])}
          [mui/icon {:sx {:mr 1}} "archive"]
          "Arkistoi"])

       ;; Restore button
       (when (= "archived" (:status user))
         [mui/button
          {:variant  "contained"
           :color    "secondary"
           :on-click #(==> [::events/update-user-status user "active"])}
          [mui/icon {:sx {:mr 1}} "restore"]
          "Palauta"])

       ;; Send magic link button
       [lui/email-button
        {:label    (tr :lipas.admin/magic-link)
         :disabled (not (s/valid? :lipas/new-user user))
         :on-click #(==> [::events/open-magic-link-dialog])}]

       ;; Save button
       (when existing?
         [mui/button
          {:variant  "contained"
           :color    "secondary"
           :on-click #(==> [::events/save-user user])}
          [mui/icon {:sx {:mr 1}} "save"]
          (tr :actions/save)])]}

     [mui/grid {:container true :spacing 1}

      [magic-link-dialog {:tr tr}]

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
      ;; TODO: Replace this with roles management
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
          :multi?    true
          :on-change #(==> [::events/edit-user [:permissions :sports-sites] %])}]

        ;; Permission to individual types
        [lui/autocomplete
         {:items     types
          :label     (tr :lipas.user.permissions/types)
          :value     (-> user :permissions :types)
          :multi?    true
          :on-change #(==> [::events/edit-user [:permissions :types] %])}]

        ;; Permission to individual cities
        [lui/autocomplete
         {:items     cities
          :label     (tr :lipas.user.permissions/cities)
          :value     (-> user :permissions :cities)
          :multi?    true
          :on-change #(==> [::events/edit-user [:permissions :cities] %])}]

        ;; Permission to activities
        [lui/autocomplete
         {:items     activities
          :label     (tr :lipas.user.permissions/activities)
          :value     (-> user :permissions :activities)
          :multi?    true
          :on-change #(==> [::events/edit-user [:permissions :activities] %])}]

        [mui/button
         {:on-click #(==> [::events/grant-access-to-activity-types
                           (-> user :permissions :activities)])}
         "Anna oikeus aktiviteettien tyyppeihin"]]]

      ;;; History
      [lui/form-card {:title (tr :lipas.user/history)}
       [lui/table-v2
        {:items history
         :headers
         {:event      {:label (tr :general/event)}
          :event-date {:label (tr :time/time)}}}]]]]))

(defn color-picker [{:keys [value on-change]}]
  [:input
   {:type      "color"
    :value     value
    :on-change #(on-change (-> % .-target .-value))}])

(defn color-selector []
  (let [new-colors (<== [::subs/selected-colors])
        pick-color (fn [k1 k2 v] (==> [::events/select-color k1 k2 v]))
        types      (<== [:lipas.ui.sports-sites.subs/active-types])]
    [mui/table
     [mui/table-head
      [mui/table-row
       [mui/table-cell "Type-code"]
       [mui/table-cell "Type-name"]
       [mui/table-cell "Geometry"]
       [mui/table-cell "Old symbol"]
       [mui/table-cell "New symbol"]
       [mui/table-cell "Old-fill"]
       [mui/table-cell "New-fill"]
       [mui/table-cell "Old-stroke"]
       [mui/table-cell "New-stroke"]]]

     (into
      [mui/table-body]
      (for [[type-code type] (sort-by first types)
            :let             [shape (-> type-code types :geometry-type)
                              fill (-> type-code styles/symbols :fill :color)
                              stroke (-> type-code styles/symbols :stroke :color)]]
        [mui/table-row
         [mui/table-cell type-code]
         [mui/table-cell (-> type :name :fi)]
         [mui/table-cell shape]

         ;; Old symbol
         [mui/table-cell (condp = shape
                           "Point" "Circle"
                           shape)]

         ;; New symbol
         [mui/table-cell (condp = shape
                           "Point" [lui/select
                                    {:items     [{:label "Circle" :value "circle"}
                                                 {:label "Square" :value "square"}]
                                     :value     (or (-> type-code new-colors :symbol)
                                                    "circle")
                                     :on-change (partial pick-color type-code :symbol)}]
                           shape)]

         ;; Old fill
         [mui/table-cell
          [color-picker {:value fill :on-change #()}]]

         ;; New fill
         [mui/table-cell
          [mui/grid {:container true :wrap "nowrap"}
           [mui/grid {:item true}
            [color-picker
             {:value     (-> (new-colors type-code) :fill)
              :on-change (partial pick-color type-code :fill)}]]
           [mui/grid {:item true}
            [mui/button
             {:size :small :on-click #(pick-color type-code :fill fill)}
             "reset"]]]]

         ;; Old stroke
         [mui/table-cell
          [color-picker {:value stroke :on-change #()}]]

         ;; New stroke
         [mui/table-cell
          [mui/grid {:container true :wrap "nowrap"}
           [mui/grid {:item true}
            [color-picker
             {:value     (-> (new-colors type-code) :stroke)
              :on-change (partial pick-color type-code :stroke)}]]
           [mui/grid {:item true}
            [mui/button
             {:size :small :on-click #(pick-color type-code :stroke stroke)}
             "reset"]]]]]))]))

(defn type-codes-view []
  (let [types (<== [:lipas.ui.sports-sites.subs/type-table])]
    [mui/card {:square true}
     [mui/card-content
      [mui/typography {:variant "h5"}
       "Tyyppikoodit"]
      [lui/table
       {:hide-action-btn? true
        :headers
        [[:type-code "Tyyppikoodi"]
         [:name "Nimi"]
         [:main-category "Pääluokka"]
         [:sub-category "Alaluokka"]
         [:description "Kuvaus"]
         [:geometry-type "Geometria"]]
        :sort-fn   :type-code
        :items     types
        :on-select #(js/alert "Ei tee mitään vielä...")}]]]))

(defn admin-panel []
  (let [tr           (<== [:lipas.ui.subs/translator])
        status       (<== [::subs/users-status])
        users        (<== [::subs/users-list])
        users-filter (<== [::subs/users-filter])
        selected-tab (<== [::subs/selected-tab])]
    [mui/paper
     [mui/grid {:container true}
      [mui/grid {:item true :xs 12}
       [mui/tool-bar
        [mui/tabs
         {:value     selected-tab
          :on-change #(==> [::events/select-tab %2])
          :indicator-color "secondary"
          :text-color "inherit"}
         [mui/tab {:label (tr :lipas.admin/users)}]
         [mui/tab {:label "Symbolityökalu"}]
         [mui/tab {:label "Tyyppikoodit"}]]]

       (when (= 1 selected-tab)
         [:<>
          [color-selector]
          [mui/fab
           {:style    {:position "sticky" :bottom "1em" :left "1em"}
            :variant  "extended"
            :color    "secondary"
            :on-click #(==> [::events/download-new-colors-excel])}
           [mui/icon "save"]
           "Lataa"]])

       (when (= 0 selected-tab)
         [mui/card {:square true}
          [mui/card-content
           [mui/typography {:variant "h5"}
            (tr :lipas.admin/users)]

           ;; Full-screen user dialog
           [user-dialog tr]

           [mui/grid {:container true :spacing 4}

            ;; Add user button
            [mui/grid {:item true :style {:flex-grow 1}}
             [mui/fab
              {:color    "secondary"
               :size     "small"
               :style    {:margin-top "1em"}
               :on-click #(==> [::events/edit-user [:email] "fix@me.com"])}
              [mui/icon "add"]]]

            ;; Status selector
            [mui/grid {:item true}
             [lui/select
              {:style     {:width "150px"}
               :label     "Status"
               :value     status
               :items     ["active" "archived"]
               :value-fn  identity
               :label-fn  identity
               :on-change #(==> [::events/select-status %])}]]

            ;; Users filter
            [mui/grid {:item true}
             [lui/text-field
              {:label     (tr :search/search)
               :on-change #(==> [::events/filter-users %])
               :value     users-filter}]]]

           ;; Users table
           [lui/table
            {:headers
             [[:email (tr :lipas.user/email)]
              [:firstname (tr :lipas.user/firstname)]
              [:lastname (tr :lipas.user/lastname)]
              [:sports-sites (tr :lipas.user.permissions/sports-sites)]
              [:cities (tr :lipas.user.permissions/cities)]
              [:types (tr :lipas.user.permissions/types)]
              [:activities (tr :lipas.user.permissions/activities)]]
             :sort-fn   :email
             :items     users
             :on-select #(==> [::events/set-user-to-edit %])}]]])

       (when (= 2 selected-tab)
         [type-codes-view])]]]))

(defn main []
  (let [admin? @(rf/subscribe [:lipas.ui.user.subs/check-privilege nil :user-management])]
    (if admin?
      [admin-panel]
      (==> [:lipas.ui.events/navigate "/"]))))
