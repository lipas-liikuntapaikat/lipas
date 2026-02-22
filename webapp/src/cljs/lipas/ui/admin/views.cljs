(ns lipas.ui.admin.views
  (:require ["@mui/material/Autocomplete" :refer [createFilterOptions]]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemSecondaryAction$default" :as ListItemSecondaryAction]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            ["react" :as react]
            [clojure.string :as str]
            [lipas.data.styles :as styles]
            [lipas.roles :as roles]
            [lipas.schema.users :as users-schema]
            [malli.core :as m]
            [lipas.ui.admin.events :as events]
            [lipas.ui.admin.subs :as subs]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.autocompletes :as ac]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/Grid$default" :as Grid2]
            ["@mui/material/LinearProgress$default" :as LinearProgress]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/ToggleButton$default" :as ToggleButton]
            ["@mui/material/ToggleButtonGroup$default" :as ToggleButtonGroup]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Tooltip$default" :as Tooltip]
            [lipas.ui.mui :as mui]
            [lipas.ui.subs :as ui-subs]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]
            [reitit.frontend.easy :as rfe]))

(defn magic-link-dialog [{:keys [tr]}]
  (let [open? (<== [::subs/magic-link-dialog-open?])
        variants (<== [::subs/magic-link-variants])
        variant (<== [::subs/selected-magic-link-variant])
        user (<== [::subs/editing-user])]

    [:> Dialog {:open open?}
     [:> DialogTitle
      (tr :lipas.admin/send-magic-link (:email user))]
     [:> DialogContent
      [:> FormGroup
       [selects/select
        {:label (tr :lipas.admin/select-magic-link-template)
         :items variants
         :value variant
         :on-change #(==> [::events/select-magic-link-variant %])}]
       [:> Button
        {:style {:margin-top "1em"}
         :on-click #(==> [::events/send-magic-link user variant])}
        (tr :actions/submit)]
       [:> Button
        {:style {:margin-top "1em"}
         :on-click #(==> [::events/close-magic-link-dialog])}
        (tr :actions/cancel)]]]]))

(r/defc permissions-request-card [{:keys [permissions-request tr]}]
  [:> Card
     ;; TODO: Add the color to the theme
   {:sx #js {:backgroundColor mui/gray3
             :mb 2}}
   [:> CardHeader
    {:subheader (tr :lipas.user/requested-permissions)}]
   [:> CardContent
    [:> Typography
     {:sx #js {:fontStyle "italic"}}
     (or permissions-request
         "-")]]])

(def filter-ac (createFilterOptions))

(r/defc site-select [{:keys [tr required data]}]
  (let [sites @(rf/subscribe [::subs/sites-list])]
    [ac/autocomplete2
     {:options sites
      :label (str (tr :lipas.user.permissions.roles.context-keys/lipas-id)
                  (when required
                    " *"))
      :value (to-array (or (:lipas-id data) []))
      :onChange (fn [_e v]
                  (rf/dispatch [::events/set-role-context-value :lipas-id (mapv ac/safe-value v)]))
      :multiple true
      :selectOnFocus true
      :clearOnBlue true
      :handleHomeEndKeys true
      :freeSolo true
      :filterOptions (fn [options params]
                           ;; The options only contains some x first sites in the system,
                           ;; so the autocomplete doesn't work that well.
                           ;; Allow inputting paikka-id numbers directly, show "Add x" option when
                           ;; the input value doesn't match any options.
                       (let [filtered (filter-ac options params)
                             input-value (js/parseInt (.-inputValue params))
                             input-value (when (pos? input-value)
                                           input-value)
                             is-existing (.some options (fn [x] (= input-value (:value x))))]
                         (when (and input-value (not is-existing))
                           (.push filtered {:value input-value
                                            :label (str "Paikka-id \"" input-value "\"")}))
                         filtered))}]))

(r/defc type-code-select [{:keys [tr required data]}]
  (let [types @(rf/subscribe [::subs/types-list (tr)])]
    [ac/autocomplete2
     {:options types
      :label (str (tr :lipas.user.permissions/types)
                  (when required
                    " *"))
      :value (to-array (or (:type-code data) []))
      :onChange (fn [_e v] (rf/dispatch [::events/set-role-context-value :type-code (mapv ac/safe-value v)]))
      :multiple true}]))

(r/defc city-code-select [{:keys [tr required data]}]
  (let [cities @(rf/subscribe [::subs/cities-list (tr)])]
    [ac/autocomplete2
     {:options cities
      :label (str (tr :lipas.user.permissions/cities)
                  (when required
                    " *"))
      :value (to-array (or (:city-code data) []))
      :onChange (fn [_e v] (rf/dispatch [::events/set-role-context-value :city-code (mapv ac/safe-value v)]))
      :multiple true}]))

(r/defc activity-select [{:keys [tr required data]}]
  (let [activities @(rf/subscribe [::subs/activities-list (tr)])]
    [ac/autocomplete2
     {:options activities
      :label (str (tr :lipas.user.permissions/activities)
                  (when required
                    " *"))
      :value (to-array (or (:activity data) []))
      :onChange (fn [_e v] (rf/dispatch [::events/set-role-context-value :activity (mapv ac/safe-value v)]))
      :multiple true}]))

(r/defc org-select [{:keys [tr required data]}]
  (let [orgs @(rf/subscribe [::subs/orgs-options])]
    [ac/autocomplete2
     {:options orgs
      :label (str (tr :lipas.user.permissions/orgs)
                  (when required
                    " *"))
      :value (to-array (or (:org-id data) []))
      :onChange (fn [_e v] (rf/dispatch [::events/set-role-context-value :org-id (mapv ac/safe-value v)]))
      :multiple true}]))

(r/defc context-key-edit [{:keys [k] :as props}]
  (case k
    :lipas-id
    [site-select props]

    :type-code
    [type-code-select props]

    :city-code
    [city-code-select props]

    :activity
    [activity-select props]

    :org-id
    [org-select props]))

(r/defc role-form [{:keys [tr]}]
  (let [data @(rf/subscribe [::subs/edit-role])
        editing? (:editing? data)

        role (:role data)
        required-context-keys (:required-context-keys (get roles/roles role))
        optional-context-keys (:optional-context-keys (get roles/roles role))]
    [:> Stack
     {:direction "column"
      :sx #js {:gap 1}}
     [:> Typography
      {:variant "h6"}
      (if editing?
        (tr :lipas.user.permissions.roles.edit-role/edit-header)
        (tr :lipas.user.permissions.roles.edit-role/new-header))]

     [ac/autocomplete2
      {:options (for [[k {:keys [assignable]}] roles/roles
                      :when assignable]
                  {:value k
                   :label (tr (keyword :lipas.user.permissions.roles.role-names k))})
       :readOnly editing?
       :label (tr :lipas.user.permissions.roles/role)
       :value (:role data)
       :onChange (fn [_e v] (rf/dispatch [::events/set-new-role (ac/safe-value v)]))}]

     (when-not (:role data)
       [:> Typography
        (tr :lipas.user.permissions.roles.edit-role/choose-role)])

     (for [k required-context-keys]
       [context-key-edit
        {:key k
         :k k
         :required data
         :tr tr
         :data data}])

     (for [k optional-context-keys]
       [context-key-edit
        {:key k
         :k k
         :tr tr
         :data data}])

     [:> Stack
      {:direction "row"
       :sx #js {:gap 1}}
      (if editing?
        [:> Button
         {:onClick (fn [_e] (rf/dispatch [::events/stop-edit]))}
         (tr :lipas.user.permissions.roles.edit-role/stop-editing)]
        [:> Button
         {:onClick (fn [_e] (rf/dispatch [::events/add-new-role]))}
         (tr :lipas.user.permissions.roles.edit-role/add)])]]))

(r/defc role-context [{:keys [tr k v]}]
  (let [locale (tr)
        localized @(rf/subscribe [::user-subs/context-value-name k v locale])]
    [:> Typography
     {:key (str k "-" v)
      :component "span"
      :sx #js {:mr 1}}
       ;; Role context key name
     (tr (keyword :lipas.user.permissions.roles.context-keys k))
     ": "
       ;; Value localized name
     localized
       ;; Value code
     " " v]))

(r/defc roles-card [{:keys [tr]}]
  (let [user @(rf/subscribe [::subs/editing-user])
        data @(rf/subscribe [::subs/edit-role])
        editing? (:editing? data)
        permissions-request (-> user :user-data :permissions-request)]
    ;; TODO: replace the container grid
    [:> Grid
     {:item true
      :xs 12
      :md 6}
     [:> Card
      {:square true}
      [:> CardHeader
       {:title "Roolit"}]
      [:> CardContent
       [:> FormGroup

        ;; Only show permissions request when there's an actual request
        (when (not-empty permissions-request)
          [permissions-request-card
           {:permissions-request permissions-request
            :tr tr}])

        [:> List
         (for [[i {:keys [role] :as x}]
               (->> user
                    :permissions
                    :roles
                              ;; Edit uses the roles vector index, so add idx before sort
                    (map-indexed vector)
                    (sort-by (comp roles/role-sort-fn second)))]
           [:> ListItem
            {:key i}
            [:> ListItemText
             [:> Typography
              {:component "span"
               :sx #js {:mr 2
                        :fontWeight "bold"}}
              (tr (keyword :lipas.user.permissions.roles.role-names role))]
             (for [[context-key vs] (dissoc x :role)]
               [:<>
                {:key context-key}
                (for [v vs]
                  [role-context
                   {:key v
                    :k context-key
                    :v v
                    :tr tr}])])]
            [:> ListItemSecondaryAction
             [:> IconButton
              {:onClick (fn [_e] (rf/dispatch [::events/edit-role i]))}
              [:> Icon "edit"]]
             [:> IconButton
              {:onClick (fn [_e] (rf/dispatch [::events/remove-role x]))
                               ;; Deleting item while editing would break the editing :roles idx numbers
               :disabled editing?}
              [:> Icon "delete"]]]])]

        [:> Paper
         {:variant "outlined"
          :sx #js {:p 2 :mt 1}}
         [role-form
          {:tr tr}]]]]]]))

(defn user-dialog [tr]
  (let [locale (tr)
        cities (<== [::subs/cities-list locale])
        types (<== [::subs/types-list locale])
        sites (<== [::subs/sites-list])
        activities (<== [::subs/activities-list locale])
        user (<== [::subs/editing-user])
        history (<== [::subs/user-history])
        existing? (some? (:id user))]

    [dialogs/full-screen-dialog
     {:open? (boolean (seq user))
      :title (or (:username user) (:email user))
      :close-label (tr :actions/close)
      :on-close #(==> [::events/set-user-to-edit nil])
      :bottom-actions
      [;; GDPR remove button
       [:> Button
        {:variant "contained"
         :color "secondary"
         :on-click (fn []
                     (==> [:lipas.ui.events/confirm
                           "Haluatko varmasti GDPR-poistaa tämän käyttäjän?"
                           (fn []
                             (==> [::events/gdpr-remove-user user]))]))}
        [:> Icon {:sx #js{:mr 1}} "gpp_bad"]
        "GDPR-poista"]
       ;; Archive button
       (when (= "active" (:status user))
         [:> Button
          {:variant "contained"
           :color "secondary"
           :on-click #(==> [::events/update-user-status user "archived"])}
          [:> Icon {:sx #js{:mr 1}} "archive"]
          "Arkistoi"])

       ;; Restore button
       (when (= "archived" (:status user))
         [:> Button
          {:variant "contained"
           :color "secondary"
           :on-click #(==> [::events/update-user-status user "active"])}
          [:> Icon {:sx #js{:mr 1}} "restore"]
          "Palauta"])

       ;; Send magic link button
       [buttons/email-button
        {:label (tr :lipas.admin/magic-link)
         :disabled (not (m/validate users-schema/new-user-schema user))
         :on-click #(==> [::events/open-magic-link-dialog])}]

       ;; Save button
       (when existing?
         [:> Button
          {:variant "contained"
           :color "secondary"
           :on-click #(==> [::events/save-user user])}
          [:> Icon {:sx #js{:mr 1}} "save"]
          (tr :actions/save)])]}

     [:> Grid {:container true :spacing 1}

      [magic-link-dialog {:tr tr}]

      ;;; Contact info
      [layouts/card {:title (tr :lipas.user/contact-info)}
       [:> FormGroup

        ;; Email
        [text-fields/text-field
         {:label (tr :lipas.user/email)
          :value (:email user)
          :on-change #(==> [::events/edit-user [:email] %])
          :disabled existing?}]

        ;; Username
        [text-fields/text-field
         {:label (tr :lipas.user/username)
          :value (:username user)
          :on-change #(==> [::events/edit-user [:username] %])
          :disabled existing?}]

        ;; Firstname
        [text-fields/text-field
         {:label (tr :lipas.user/firstname)
          :value (-> user :user-data :firstname)
          :on-change #(==> [::events/edit-user [:user-data :firstname] %])
          :disabled existing?}]

        ;; Lastname
        [text-fields/text-field
         {:label (tr :lipas.user/lastname)
          :value (-> user :user-data :lastname)
          :on-change #(==> [::events/edit-user [:user-data :lastname] %])
          :disabled existing?}]]]

      [roles-card
       {:tr tr}]

      ;;; Permissions
      ;; TODO: Replace this with roles management
      [layouts/card {:title (str (tr :lipas.user/permissions)
                                  " " (tr :lipas.user.permissions.roles/permissions-old))}
       [:> FormGroup

        [permissions-request-card
         {:permissions-request (-> user :user-data :permissions-request)
          :tr tr}]

        ;; Admin?
        [checkboxes/checkbox
         {:disabled true
          :label (tr :lipas.user.permissions/admin?)
          :value (-> user :permissions :admin?)
          :on-change #(==> [::events/edit-user [:permissions :admin?] %])}]

        ;; Permission to all types?
        [checkboxes/checkbox
         {:disabled true
          :label (tr :lipas.user.permissions/all-types?)
          :value (-> user :permissions :all-types?)
          :on-change #(==> [::events/edit-user [:permissions :all-types?] %])}]

        ;; Permission to all cities?
        [checkboxes/checkbox
         {:disabled true
          :label (tr :lipas.user.permissions/all-cities?)
          :value (-> user :permissions :all-cities?)
          :on-change #(==> [::events/edit-user [:permissions :all-cities?] %])}]

        ;; Permission to individual spoorts-sites
        [ac/autocomplete
         {:disabled true
          :items sites
          :label (tr :lipas.user.permissions/sports-sites)
          :value (-> user :permissions :sports-sites)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :sports-sites] %])}]

        ;; Permission to individual types
        [ac/autocomplete
         {:disabled true
          :items types
          :label (tr :lipas.user.permissions/types)
          :value (-> user :permissions :types)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :types] %])}]

        ;; Permission to individual cities
        [ac/autocomplete
         {:disabled true
          :items cities
          :label (tr :lipas.user.permissions/cities)
          :value (-> user :permissions :cities)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :cities] %])}]

        ;; Permission to activities
        [ac/autocomplete
         {:disabled true
          :items activities
          :label (tr :lipas.user.permissions/activities)
          :value (-> user :permissions :activities)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :activities] %])}]

        [:> Button
         {:disabled true
          :on-click #(==> [::events/grant-access-to-activity-types
                           (-> user :permissions :activities)])}
         "Anna oikeus aktiviteettien tyyppeihin"]]]

      ;;; History
      [layouts/card {:title (tr :lipas.user/history)}
       [tables/table-v2
        {:items history
         :headers
         {:event {:label (tr :general/event)}
          :event-date {:label (tr :time/time)}}}]]]]))

(defn color-picker [{:keys [value on-change]}]
  [:input
   {:type "color"
    :value value
    :on-change #(on-change (-> % .-target .-value))}])

(defn color-selector []
  (let [new-colors (<== [::subs/selected-colors])
        pick-color (fn [k1 k2 v] (==> [::events/select-color k1 k2 v]))
        types (<== [:lipas.ui.sports-sites.subs/active-types])]
    [:<>
     [:> Table
      [:> TableHead
       [:> TableRow
        [:> TableCell "Type-code"]
        [:> TableCell "Type-name"]
        [:> TableCell "Geometry"]
        [:> TableCell "Old symbol"]
        [:> TableCell "New symbol"]
        [:> TableCell "Old-fill"]
        [:> TableCell "New-fill"]
        [:> TableCell "Old-stroke"]
        [:> TableCell "New-stroke"]]]

      (into
       [:> TableBody]
       (for [[type-code type] (sort-by first types)
             :let [shape (-> type-code types :geometry-type)
                   fill (-> type-code styles/symbols :fill :color)
                   stroke (-> type-code styles/symbols :stroke :color)]]
         [:> TableRow
          [:> TableCell type-code]
          [:> TableCell (-> type :name :fi)]
          [:> TableCell shape]

           ;; Old symbol
          [:> TableCell (condp = shape
                            "Point" "Circle"
                            shape)]

           ;; New symbol
          [:> TableCell (condp = shape
                            "Point" [selects/select
                                     {:items [{:label "Circle" :value "circle"}
                                              {:label "Square" :value "square"}]
                                      :value (or (-> type-code new-colors :symbol)
                                                 "circle")
                                      :on-change (partial pick-color type-code :symbol)}]
                            shape)]

           ;; Old fill
          [:> TableCell
           [color-picker {:value fill :on-change #()}]]

           ;; New fill
          [:> TableCell
           [:> Grid {:container true :wrap "nowrap"}
            [:> Grid {:item true}
             [color-picker
              {:value (-> (new-colors type-code) :fill)
               :on-change (partial pick-color type-code :fill)}]]
            [:> Grid {:item true}
             [:> Button
              {:size :small :on-click #(pick-color type-code :fill fill)}
              "reset"]]]]

           ;; Old stroke
          [:> TableCell
           [color-picker {:value stroke :on-change #()}]]

           ;; New stroke
          [:> TableCell
           [:> Grid {:container true :wrap "nowrap"}
            [:> Grid {:item true}
             [color-picker
              {:value (-> (new-colors type-code) :stroke)
               :on-change (partial pick-color type-code :stroke)}]]
            [:> Grid {:item true}
             [:> Button
              {:size :small :on-click #(pick-color type-code :stroke stroke)}
              "reset"]]]]]))]
     [:> Fab
      {:style {:position "sticky" :bottom "1em" :left "1em"}
       :variant "extended"
       :color "secondary"
       :on-click #(==> [::events/download-new-colors-excel])}
      [:> Icon "save"]
      "Lataa"]]))

(defn type-codes-view []
  (let [types (<== [:lipas.ui.sports-sites.subs/type-table])]
    [:> Card {:square true}
     [:> CardContent
      [:> Typography {:variant "h5"}
       "Tyyppikoodit"]
      [tables/table
       {:hide-action-btn? true
        :headers
        [[:type-code "Tyyppikoodi"]
         [:name "Nimi"]
         [:main-category "Pääluokka"]
         [:sub-category "Alaluokka"]
         [:description "Kuvaus"]
         [:geometry-type "Geometria"]]
        :sort-fn :type-code
        :items types
        :on-select #(js/alert "Ei tee mitään vielä...")}]]]))

(defn users-view []
  (let [tr (<== [:lipas.ui.subs/translator])
        status (<== [::subs/users-status])
        users (<== [::subs/users-list])
        users-filter (<== [::subs/users-filter])]
    [:> Card {:square true}
     [:> CardContent
      [:> Typography {:variant "h5"}
       (tr :lipas.admin/users)]

      ;; Full-screen user dialog
      [user-dialog tr]

      [:> Grid {:container true :spacing 4}

       ;; Add user button
       [:> Grid {:item true :style {:flex-grow 1}}
        [:> Fab
         {:color "secondary"
          :size "small"
          :style {:margin-top "1em"}
          :on-click #(==> [::events/edit-user [:email] "fix@me.com"])}
         [:> Icon "add"]]]

       ;; Status selector
       [:> Grid {:item true}
        [selects/select
         {:style {:width "150px"}
          :label "Status"
          :value status
          :items ["active" "archived"]
          :value-fn identity
          :label-fn identity
          :on-change #(==> [::events/select-status %])}]]

       ;; Users filter
       [:> Grid {:item true}
        [text-fields/text-field
         {:label (tr :search/search)
          :on-change #(==> [::events/filter-users %])
          :value users-filter}]]]

      ;; Users table
      [tables/table
       {:headers
        [[:email (tr :lipas.user/email)]
         [:firstname (tr :lipas.user/firstname)]
         [:lastname (tr :lipas.user/lastname)]
         [:roles (tr :lipas.user.permissions.roles/roles)]]
        :sort-fn :email
        :items users
        :on-select #(==> [::events/set-user-to-edit %])}]]]))

(defn add-user-to-org-dialog [tr]
  (let [open? @(rf/subscribe [::subs/add-user-to-org-dialog-open?])
        org-id @(rf/subscribe [::subs/add-user-to-org-dialog-org-id])
        email @(rf/subscribe [::subs/add-user-to-org-email])
        role @(rf/subscribe [::subs/add-user-to-org-role])]
    [dialogs/dialog
     {:open? open?
      :title (tr :org.form/add-user)
      :on-close #(rf/dispatch [::events/close-add-user-to-org-dialog])
      :save-enabled? (and (seq email) role)
      :save-label (tr :actions/add)
      :cancel-label (tr :actions/cancel)
      :on-save #(rf/dispatch [::events/add-user-to-org email role org-id])}

     [:> FormGroup
      [text-fields/text-field
       {:label (tr :lipas.user/email)
        :value email
        :required true
        :on-change #(rf/dispatch [::events/set-add-user-to-org-email %])}]
      [selects/select
       {:label (tr :lipas.org/org-role)
        :value role
        :required true
        :items [{:value "org-user" :label (tr :lipas.user.permissions.roles.role-names/org-user)}
                {:value "org-admin" :label (tr :lipas.user.permissions.roles.role-names/org-admin)}]
        :on-change #(rf/dispatch [::events/set-add-user-to-org-role %])}]]]))

(defn org-dialog [tr]
  (let [edit-id @(rf/subscribe [::ui-subs/query-param :edit-id])
        org @(rf/subscribe [::subs/editing-org])
        org-users @(rf/subscribe [::subs/org-users-table-data edit-id])]
    (react/useEffect (fn []
                       (rf/dispatch [::events/set-org-to-edit edit-id])
                       (when (and edit-id (not= "new" edit-id))
                         (rf/dispatch [::events/get-org-users edit-id]))
                       (fn []
                         (rf/dispatch [::events/set-org-to-edit nil])))
                     #js [edit-id])
    [dialogs/full-screen-dialog
     {:open? (boolean edit-id)
      :title (or (:name org)
                 "-")
      :close-label (tr :actions/close)
      :on-close (fn [] (rfe/set-query #(dissoc % :edit-id)))
      :bottom-actions
      [[:> Button
        {:variant "contained"
         :color "secondary"
         :on-click #(rf/dispatch [::events/save-org org])}
        [:> Icon {:sx {:mr 1}} "save"]
        (tr :actions/save)]]}

     [add-user-to-org-dialog tr]

     ;; Reuse lipas.ui.org.views
     [:> Grid {:container true :spacing 1}
      [layouts/card {:title (tr :org.form/details)
                      :xs 12
                      :md 12
                      :lg 12}
       [:> FormGroup
        [text-fields/text-field
         {:label (tr :lipas.org/name)
          :value (:name org)
          :on-change #(rf/dispatch [::events/edit-org [:name] %])}]
        [text-fields/text-field
         {:label (tr :lipas.org/phone)
          :value (:phone (:data org))
          :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :phone] x]))}]]]

       ;; TODO: Ptv data fields

      [layouts/card {:title (tr :org.form/users)
                      :xs 12
                      :md 12
                      :lg 12}
       [:> Grid {:container true :spacing 2 :align-items "flex-end"}
        [:> Grid {:item true :xs true}
         [tables/table
          {:headers
           [[:email (tr :lipas.user/email)]
            [:username (tr :lipas.user/username)]
            [:role (tr :lipas.org/org-role)]]
           :sort-fn :email
           :items org-users
           :hide-action-btn? true}]]
        [:> Grid {:item true}
         [:> Fab
          {:color "secondary"
           :size "small"
           :on-click #(rf/dispatch [::events/open-add-user-to-org-dialog edit-id])}
          [:> Icon "person_add"]]]]]]]))

(defn job-details-dialog [tr]
  (let [open? (<== [::subs/job-details-dialog-open?])
        job-id (<== [::subs/selected-job-id])
        job (<== [::subs/selected-job-details job-id])
        reprocessing? (<== [::subs/reprocessing?])]
    [:> Dialog
     {:open open?
      :on-close #(==> [::events/close-job-details-dialog])
      :max-width "md"
      :full-width true}
     [:> DialogTitle "Job Details"
      [:> IconButton
       {:on-click #(==> [::events/close-job-details-dialog])
        :sx #js{:position "absolute" :right 8 :top 8}}
       [:> Icon "close"]]]

     (when job
       [:> DialogContent
        ;; Basic info
        [:> Typography {:variant "h6" :gutterBottom true} "Basic Information"]
        [:> Grid2 {:container true :spacing 2 :sx #js{:mb 3}}
         [:> Grid2 {:size 6}
          [:> Typography {:color "textSecondary"} "ID"]
          [:> Typography (str (:id job))]]
         [:> Grid2 {:size 6}
          [:> Typography {:color "textSecondary"} "Job Type"]
          [:> Typography (get-in job [:original-job :type] "Unknown")]]
         [:> Grid2 {:size 6}
          [:> Typography {:color "textSecondary"} "Failed At"]
          [:> Typography (let [died-at (:died-at job)]
                            (cond
                              (inst? died-at) (.toLocaleString died-at)
                              (string? died-at) died-at
                              :else (str died-at)))]]
         [:> Grid2 {:size 6}
          [:> Typography {:color "textSecondary"} "Status"]
          [:> Chip {:label (if (:acknowledged job) "Acknowledged" "Unacknowledged")
                     :color (if (:acknowledged job) "default" "warning")
                     :size "small"}]]]

        ;; Error details
        [:> Typography {:variant "h6" :gutterBottom true} "Error Details"]
        [:> Paper {:sx #js{:p 2 :mb 3 :bgcolor "#f5f5f5"}}
         [:> Typography {:variant "body2" :component "pre" :sx #js{:whiteSpace "pre-wrap" :fontFamily "monospace"}}
          (:error-message job)]]

        ;; Job payload
        [:> Typography {:variant "h6" :gutterBottom true} "Job Payload"]
        [:> Paper {:sx #js{:p 2 :bgcolor "#f5f5f5" :overflow "auto"}}
         [:> Typography {:variant "body2" :component "pre" :sx #js{:fontFamily "monospace"}}
          (js/JSON.stringify (clj->js (:original-job job)) nil 2)]]])

     [:> DialogActions
      [:> Button
       {:on-click #(==> [::events/close-job-details-dialog])}
       "Close"]
      (when (not (:acknowledged job))
        [:> Button
         {:variant "outlined"
          :on-click #(when (js/confirm "Mark this job as acknowledged?")
                       (==> [::events/acknowledge-single-job (:id job)]))}
         "Acknowledge"])
      [:> Button
       {:variant "contained"
        :color "primary"
        :disabled reprocessing?
        :start-icon (when reprocessing? (r/as-element [:> CircularProgress {:size 16}]))
        :on-click #(when (js/confirm "Are you sure you want to reprocess this job?")
                     (==> [::events/reprocess-single-job (:id job)]))}
       (if reprocessing? "Reprocessing..." "Reprocess")]]]))

;; Dead Letter Queue section
 ;; Jobs Monitoring tab content
(defn jobs-monitoring-tab []
  (let [health-data (<== [::subs/jobs-health])
        metrics-data (<== [::subs/jobs-metrics])
        loading? (<== [::subs/jobs-loading?])
        error (<== [::subs/jobs-error])]
    [:<>
     ;; Error display
     (when error
       [:> Alert {:severity "error" :sx #js{:mb 2}}
        error])

     ;; Loading indicator
     (when loading?
       [:> LinearProgress {:sx #js{:mb 2}}])

     ;; Health Overview Card
     (when health-data
       [:> Card {:sx #js{:mb 2}}
        [:> CardHeader {:title "Queue Health"}]
        [:> CardContent
         [:> Grid2 {:container true :spacing 2}
          ;; Pending jobs
          [:> Grid2 {:size 12 :size/sm 6 :size/md 3}
           [:> Paper {:sx #js{:p 2 :bgcolor (if (> (or (:pending_count health-data) 0) 100) "#ffebee" "#f5f5f5")}}
            [:> Typography {:variant "h4"} (str (or (:pending_count health-data) 0))]
            [:> Typography {:color "textSecondary"} "Pending Jobs"]
            (when-let [oldest (:oldest_pending_minutes health-data)]
              [:> Typography {:variant "caption" :color "textSecondary"}
               (str "Oldest: " oldest " min")])]]

          ;; Processing jobs
          [:> Grid2 {:size 12 :size/sm 6 :size/md 3}
           [:> Paper {:sx #js{:p 2 :bgcolor "#f5f5f5"}}
            [:> Typography {:variant "h4"} (str (or (:processing_count health-data) 0))]
            [:> Typography {:color "textSecondary"} "Processing"]
            (when-let [longest (:longest_processing_minutes health-data)]
              [:> Typography {:variant "caption" :color "textSecondary"}
               (str "Longest: " longest " min")])]]

          ;; Failed jobs
          [:> Grid2 {:size 12 :size/sm 6 :size/md 3}
           [:> Paper {:sx #js{:p 2 :bgcolor (if (> (or (:failed_count health-data) 0) 0) "#fff3e0" "#f5f5f5")}}
            [:> Typography {:variant "h4"} (str (or (:failed_count health-data) 0))]
            [:> Typography {:color "textSecondary"} "Failed"]]]

          ;; Dead letter jobs
          [:> Grid2 {:size 12 :size/sm 6 :size/md 3}
           (let [dlq-stats (<== [::subs/dead-letter-stats])
                 unacknowledged (:unacknowledged dlq-stats 0)]
             [:> Paper {:sx #js{:p 2 :bgcolor (if (> unacknowledged 0) "#ffebee" "#f5f5f5")}}
              [:> Typography {:variant "h4"} (str unacknowledged)]
              [:> Typography {:color "textSecondary"} "Unacknowledged DLQ"]
              [:> Button
               {:size "small"
                :sx #js{:mt 1}
                :on-click #(==> [::events/select-jobs-sub-tab 1])}
               "View DLQ"]])]]]])

     ;; Performance Metrics
     (when-let [metrics-table-data (<== [::subs/jobs-metrics-table-data])]
       [:> Card {:sx #js{:mb 2}}
        [:> CardHeader {:title "Performance Metrics"}]
        [:> CardContent
         [tables/table
          {:headers [[:type "Job Type"]
                     [:status "Status"]
                     [:job_count "Count"]
                     [:avg_duration_seconds "Avg Duration (s)"]
                     [:p95_duration_seconds "P95 Duration (s)"]
                     [:avg_attempts "Avg Attempts"]]
           :items metrics-table-data
           :sort-fn :type}]]])

     ;; Current Stats by Status
     (when-let [current-stats (:current-stats metrics-data)]
       [:> Card {:sx #js{:mb 2}}
        [:> CardHeader {:title "Current Queue Status"}]
        [:> CardContent
         [:> Grid2 {:container true :spacing 2}
          (for [[status data] current-stats
                :when data]
            [:> Grid2 {:key status :size 12 :size/sm 6 :size/md 4}
             [:> Paper {:sx #js{:p 2}}
              [:> Typography {:variant "h6"} (if (keyword? status) (name status) (str status))]
              [:> Typography (str "Count: " (:count data))]
              (when-let [oldest (:oldest_created_at data)]
                [:> Typography {:variant "caption" :display "block"}
                 (str "Oldest: " oldest)])
              (when-let [oldest-min (:oldest_minutes data)]
                [:> Typography {:variant "caption"}
                 (str oldest-min " minutes ago")])]])]]])

     ;; Job Types Configuration
     (when metrics-data
       [:> Card
        [:> CardHeader {:title "Job Types Configuration"}]
        [:> CardContent
         [:> Grid2 {:container true :spacing 2}
          [:> Grid2 {:size 12 :size/md 6}
           [:> Typography {:variant "subtitle1"} "Fast Lane Jobs"]
           [:> List {:dense true}
            (for [job-type (:fast-job-types metrics-data)]
              [:> ListItem {:key job-type}
               [:> ListItemText job-type]])]]
          [:> Grid2 {:size 12 :size/md 6}
           [:> Typography {:variant "subtitle1"} "Slow Lane Jobs"]
           [:> List {:dense true}
            (for [job-type (:slow-job-types metrics-data)]
              [:> ListItem {:key job-type}
               [:> ListItemText job-type]])]]]]])]))

;; Dead Letter Queue tab content
(defn dead-letter-queue-tab []
  (let [dlq-jobs (<== [::subs/filtered-dead-letter-jobs])
        loading? (<== [::subs/dead-letter-loading?])
        error (<== [::subs/dead-letter-error])
        filter-value (<== [::subs/dead-letter-filter])
        selected-ids (<== [::subs/selected-job-ids])
        bulk-reprocessing? (<== [::subs/bulk-reprocessing?])
        bulk-acknowledging? (<== [::subs/bulk-acknowledging?])
        tr (<== [:lipas.ui.subs/translator])
        all-job-ids (set (map :id dlq-jobs))
        some-selected? (seq selected-ids)
        all-selected? (and some-selected?
                           (= (count selected-ids) (count dlq-jobs)))]
    [:<>
     ;; Job details dialog
     [job-details-dialog tr]

     ;; Filter buttons
     [:> ToggleButtonGroup
      {:value filter-value
       :exclusive true
       :on-change (fn [_ new-value]
                    (when-let [new-value (and new-value (keyword new-value))]
                      (==> [::events/toggle-dead-letter-filter new-value])
                      (==> [::events/clear-job-selection])
                      (==> [::events/fetch-dead-letter-jobs
                            {:acknowledged (case new-value
                                             :all nil
                                             :unacknowledged false
                                             :acknowledged true)}])))
       :sx #js{:mb 2}}
      [:> ToggleButton {:value :unacknowledged} "Unacknowledged"]
      [:> ToggleButton {:value :acknowledged} "Acknowledged"]
      [:> ToggleButton {:value :all} "All"]]

     ;; Error display
     (when error
       [:> Alert {:severity "error" :sx #js{:mb 2}} error])

     ;; Bulk actions toolbar
     (when some-selected?
       [:> Paper {:sx #js{:p 2 :mb 2 :bgcolor "action.hover"}}
        [:> Stack {:direction "row" :spacing 2 :alignItems "center"}
         [:> Typography (str (count selected-ids) " selected")]
         [:> Button
          {:variant "contained"
           :size "small"
           :disabled bulk-reprocessing?
           :start-icon (when bulk-reprocessing?
                         (r/as-element [:> CircularProgress {:size 16}]))
           :on-click #(when (js/confirm (str "Reprocess " (count selected-ids) " selected job(s)?"))
                        (==> [::events/reprocess-selected-jobs]))}
          (if bulk-reprocessing? "Reprocessing..." "Reprocess Selected")]
         [:> Button
          {:variant "outlined"
           :size "small"
           :disabled bulk-acknowledging?
           :start-icon (when bulk-acknowledging?
                         (r/as-element [:> CircularProgress {:size 16}]))
           :on-click #(when (js/confirm (str "Acknowledge " (count selected-ids) " selected job(s)?"))
                        (==> [::events/acknowledge-selected-jobs]))}
          (if bulk-acknowledging? "Acknowledging..." "Acknowledge Selected")]
         [:> Button
          {:variant "outlined"
           :size "small"
           :on-click #(==> [::events/clear-job-selection])}
          "Clear Selection"]]])

     ;; Loading indicator
     (when loading?
       [:> LinearProgress {:sx #js{:mb 2}}])

     ;; Jobs table
     (if (empty? dlq-jobs)
       [:> Typography {:color "textSecondary"} "No jobs in the selected filter"]
       [:> Table {:sx #js{:minWidth 650}}
        [:> TableHead
         [:> TableRow
          [:> TableCell {:padding "checkbox"}
           [:> Checkbox
            {:checked all-selected?
             :indeterminate (and some-selected? (not all-selected?))
             :on-change #(if all-selected?
                           (==> [::events/clear-job-selection])
                           (==> [::events/select-all-jobs all-job-ids]))}]]
          [:> TableCell "ID"]
          [:> TableCell "Job Type"]
          [:> TableCell "Error Message"]
          [:> TableCell "Failed At"]
          [:> TableCell "Status"]
          [:> TableCell {:align "right"} "Actions"]]]

        [:> TableBody
         (for [job (sort-by :died-at #(compare %2 %1) dlq-jobs)]
           [:> TableRow {:key (:id job)
                           :sx #js{"&:last-child td, &:last-child th" #js{:border 0}}
                           :selected (contains? selected-ids (:id job))}
            [:> TableCell {:padding "checkbox"}
             [:> Checkbox
              {:checked (contains? selected-ids (:id job))
               :on-change #(==> [::events/toggle-job-selection (:id job)])}]]
            [:> TableCell (:id job)]
            [:> TableCell (get-in job [:original-job :type] "Unknown")]
            [:> TableCell
             (let [msg (:error-message job)]
               [:> Tooltip {:title msg}
                [:> Typography {:variant "body2"
                                 :sx #js{:cursor "help"
                                         :maxWidth 300
                                         :overflow "hidden"
                                         :textOverflow "ellipsis"
                                         :whiteSpace "nowrap"}}
                 (if (> (count msg) 50)
                   (str (subs msg 0 47) "...")
                   msg)]])]
            [:> TableCell (let [died-at (:died-at job)]
                              (cond
                                (inst? died-at) (.toLocaleString died-at)
                                (string? died-at) (-> died-at
                                                      (str/replace "T" " ")
                                                      (str/split ".")
                                                      first)
                                :else (str died-at)))]
            [:> TableCell
             (if (:acknowledged job)
               [:> Chip {:label "Acknowledged"
                          :size "small"
                          :color "default"}]
               [:> Chip {:label "Unacknowledged"
                          :size "small"
                          :color "warning"}])]
            [:> TableCell {:align "right"}
             [:> Stack {:direction "row" :spacing 1 :justifyContent "flex-end"}
              [:> Button
               {:size "small"
                :on-click #(==> [::events/open-job-details-dialog (:id job)])}
               "View"]
              [:> Button
               {:size "small"
                :color "primary"
                :on-click #(when (js/confirm "Reprocess this job?")
                             (==> [::events/reprocess-single-job (:id job)]))}
               "Reprocess"]
              (when (not (:acknowledged job))
                [:> Button
                 {:size "small"
                  :on-click #(when (js/confirm "Acknowledge this job?")
                               (==> [::events/acknowledge-single-job (:id job)]))}
                 "Ack"])]]])]])]))

(defn jobs-monitor-view []
  (let [selected-sub-tab (<== [::subs/jobs-selected-sub-tab])]
    [:> Card {:square true}
     [:> CardContent
      [:> Typography {:variant "h5"} "Jobs Queue Monitor"]

      ;; Refresh button
      [:> Button
       {:variant "contained"
        :color "primary"
        :on-click #(do
                     (==> [::events/fetch-jobs-health])
                     (==> [::events/fetch-jobs-metrics])
                     (==> [::events/fetch-dead-letter-jobs {:acknowledged false}]))
        :style {:margin-bottom "1em"}}
       [:> Icon {:sx #js{:mr 1}} "refresh"]
       "Refresh"]

      ;; Sub-tabs
      [:> Tabs
       {:value selected-sub-tab
        :on-change #(==> [::events/select-jobs-sub-tab %2])
        :sx #js{:borderBottom 1 :borderColor "divider" :mb 2}}
       [:> Tab {:label "Monitoring"}]
       [:> Tab {:label "Dead Letter Queue"}]]

      ;; Tab content
      (case selected-sub-tab
        0 [jobs-monitoring-tab]
        1 [dead-letter-queue-tab]
        [jobs-monitoring-tab])]]))

(defn format-timestamp [timestamp]
  (when timestamp
    (try
      (let [date (if (string? timestamp)
                   (js/Date. timestamp)
                   timestamp)]
        (.toLocaleDateString date "fi-FI"
                             #js {:year "numeric"
                                  :month "2-digit"
                                  :day "2-digit"
                                  :hour "2-digit"
                                  :minute "2-digit"}))
      (catch js/Error _
        (str timestamp)))))

(defn get-user-display-name [users author-id]
  (let [user (get users author-id)]
    (or (:email user)
        (:username user)
        (str "User ID: " author-id))))

(defn site-history-search []
  (let [search-id (<== [::subs/site-history-search-id])
        loading? (<== [::subs/site-history-loading?])
        search-id-str (str (or search-id ""))
        valid-id? (and (not-empty search-id-str)
                       (re-matches #"^\d+$" search-id-str))]
    [:> Card {:sx #js{:mb 2}}
     [:> CardContent
      [:> Typography {:variant "h6" :gutterBottom true}
       "Hae historia Lipas ID:llä"]
      [:> Grid2 {:container true :spacing 2 :alignItems "flex-end"}
       [:> Grid2 {:size 8}
        [text-fields/text-field
         {:label "LIPAS ID"
          :value search-id-str
          :type "number"
          :disabled loading?
          :on-change #(==> [::events/set-site-history-search-id %])
          :on-key-down (fn [e]
                         (when (= "Enter" (.-key e))
                           (when valid-id?
                             (==> [::events/search-site-history (js/parseInt search-id-str)]))))}]]
       [:> Grid2 {:size 4}
        [:> Button
         {:variant "contained"
          :disabled (or loading? (not valid-id?))
          :on-click #(when valid-id?
                       (==> [::events/search-site-history (js/parseInt search-id-str)]))}
         (if loading? "Haetaan..." "Hae")]]]]]))

(defn site-history-results []
  (let [results (<== [::subs/site-history-results])
        error (<== [::subs/site-history-error])
        loading? (<== [::subs/site-history-loading?])
        users (<== [::subs/users])
        tr (<== [:lipas.ui.subs/translator])]
    [:<>
     ;; Error display
     (when error
       [:> Alert {:severity "error" :sx #js{:mb 2}}
        error])

     ;; Loading indicator
     (when loading?
       [:> LinearProgress {:sx #js{:mb 2}}])

     ;; Results
     (when (and results (seq results))
       [:> Card
        [:> CardHeader {:title (str "Hukutulokset (" (count results) " versiota)")}]
        [:> CardContent
         [tables/table-v2
          {:items (map-indexed (fn [idx revision]
                                 (-> revision
                                     (assoc :index (+ idx 1))
                                     (assoc :formatted-date (format-timestamp (:event-date revision)))
                                     (assoc :user-display (get-user-display-name users (:author revision)))
                                     (assoc :type-code (get-in revision [:type :type-code]))))
                               (sort-by :event-date #(compare %2 %1) results))
           :headers
           {:index {:label "#"}
            :formatted-date {:label (tr :time/time)}
            :user-display {:label (tr :lipas.user/user)}
            :status {:label (tr :lipas.sports-site/status)}
            :name {:label (tr :lipas.sports-site/name)}
            :type-code {:label (tr :type/type-code)}}}]]])

     ;; No results message
     (when (and results (empty? results))
       [:> Alert {:severity "info"}
        "No history found for this LIPAS ID"])]))

(defn site-history-tab []
  [:> Card {:square true}
   [:> CardContent
    [:> Typography {:variant "h5"}
     "Liikuntapaikan historia"]
    [site-history-search]
    [site-history-results]]])

(defn admin-panel []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        selected-tab @(rf/subscribe [::ui-subs/query-param :tab :users])]
    [:> Paper
     [:> Grid {:container true}
      [:> Grid {:item true :xs 12}
       [:> Toolbar
        [:> Tabs
         {:value selected-tab
          :on-change (fn [_e x]
                       (rfe/set-query {:tab x}))
          :indicator-color "secondary"
          :text-color "inherit"}
         [:> Tab {:label (tr :lipas.admin/users)
                   :value "users"}]
         [:> Tab {:label "Historia"
                   :value "site-history"}]
         [:> Tab {:label "Symbolityökalu"
                   :value "symbol"}]
         [:> Tab {:label "Tyyppikoodit"
                   :value "types"}]
         [:> Tab {:label "Jobs Monitoring"
                   :value "jobs"}]]]

       (case selected-tab
         :symbol
         [color-selector]

         :users
         [users-view]

         :site-history
         [site-history-tab]

         :types
         [type-codes-view]

         :jobs
         [jobs-monitor-view]

         [:div "Missing view"])]]]))

(defn main []
  (let [admin? @(rf/subscribe [:lipas.ui.user.subs/check-privilege nil :users/manage])]
    (if admin?
      [admin-panel]
      (==> [:lipas.ui.events/navigate "/"]))))
