(ns lipas.ui.admin.views
  (:require ["@mui/material/Button$default" :as Button]
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
            [clojure.string :as str]
            [lipas.data.styles :as styles]
            [lipas.roles :as roles]
            [lipas.schema.users :as users-schema]
            [malli.core :as m]
            [lipas.ui.admin.events :as events]
            [lipas.ui.admin.jobs.views :as jobs-views]
            [lipas.ui.admin.subs :as subs]
            [lipas.ui.roles.editor :as role-editor]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.admin.ai-workbench.views :as ai-workbench-views]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.autocompletes :as ac]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Dialog$default" :as Dialog]
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
            ["@mui/material/Toolbar$default" :as Toolbar]
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

;; Assign a role instance to a user. Thin adapter over the shared
;; role-spec editor (lipas.ui.roles.editor); admin keeps its own
;; [:admin …] state + events.
(r/defc role-form [{:keys [tr]}]
  (let [data (<== [::subs/edit-role])
        editing? (:editing? data)
        assignable-roles (->> roles/roles
                              (filter (comp :assignable val))
                              (sort-by (comp :sort val))
                              (map key))]
    [:> Stack
     {:direction "column"
      :sx #js {:gap 1}}
     [:> Typography
      {:variant "h6"}
      (if editing?
        (tr :lipas.user.permissions.roles.edit-role/edit-header)
        (tr :lipas.user.permissions.roles.edit-role/new-header))]

     [role-editor/role-spec-editor
      {:spec data
       :roles assignable-roles
       :role-read-only? editing?
       :on-role-change (fn [role] (==> [::events/set-new-role role]))
       :on-context-change (fn [k vals] (==> [::events/set-role-context-value k vals]))
       :tr tr}]

     [:> Stack
      {:direction "row"
       :sx #js {:gap 1}}
      (if editing?
        [:> Button
         {:onClick (fn [_e] (==> [::events/stop-edit]))}
         (tr :lipas.user.permissions.roles.edit-role/stop-editing)]
        [:> Button
         {:onClick (fn [_e] (==> [::events/add-new-role]))}
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
       {:title "Suorat käyttöoikeudet"
        :subheader (str "LIPAS-ylläpitäjän käyttäjälle suoraan myöntämät roolit. "
                        "Organisaation kautta saadut oikeudet (jäsenyydet) "
                        "hallitaan Organisaatiot-näkymässä.")}]
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
        cities (<== [::role-editor/cities-list locale])
        types (<== [::role-editor/types-list locale])
        sites (<== [::role-editor/sites-list])
        activities (<== [::role-editor/activities-list locale])
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

       ;; Impersonate button
       (when (and existing? (= "active" (:status user)))
         [:> Button
          {:variant "contained"
           :color "secondary"
           :on-click #(==> [:lipas.ui.login.events/impersonate (:id user)])}
          [:> Icon {:sx #js{:mr 1}} "supervised_user_circle"]
          (tr :lipas.admin/impersonate)])

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
                  :value "jobs"}]
         [:> Tab {:label "PTV AI Workbench"
                  :value "ai-workbench"}]]]

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
         [jobs-views/jobs-view]

         :ai-workbench
         [ai-workbench-views/ai-workbench-tab]

         [:div "Missing view"])]]]))

(defn main []
  (let [admin? @(rf/subscribe [:lipas.ui.user.subs/check-privilege nil :users/manage])]
    (if admin?
      [admin-panel]
      (==> [:lipas.ui.events/navigate "/"]))))
