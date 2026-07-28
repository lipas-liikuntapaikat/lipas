(ns lipas.ui.admin.views
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/Grid$default" :as Grid2]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/LinearProgress$default" :as LinearProgress]
            ;; clj-kondo false positive: `List` collides with the cljs.core/List
            ;; deftype, so the [:> List ...] hiccup use below isn't recognized
            ;; as a use of this alias.
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemSecondaryAction$default" :as ListItemSecondaryAction]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Typography$default" :as Typography]
            ["@turf/area$default" :as turf-area]
            ["@turf/length$default" :as turf-length]
            [clojure.reader :refer [read-string]]
            [lipas.data.styles :as styles]
            [lipas.roles :as roles]
            [lipas.schema.users :as users-schema]
            [lipas.ui.admin.ai-workbench.views :as ai-workbench-views]
            [lipas.ui.admin.events :as events]
            [lipas.ui.admin.jobs.views :as jobs-views]
            [lipas.ui.admin.subs :as subs]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.roles.editor :as role-editor]
            [lipas.ui.subs :as ui-subs]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [malli.core :as m]
            [re-frame.core :as rf]
            [reagent.core :as r]
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

;; Calls turf directly instead of lipas.ui.map.utils, to avoid pulling the
;; OpenLayers-heavy map bundle into the admin module for two small calcs.
(defn- geom-length-km [fcoll]
  (when (seq (:features fcoll))
    (-> fcoll clj->js turf-length (utils/round-safe 2) read-string)))

(defn- geom-area-m2 [fcoll]
  (when (seq (:features fcoll))
    (-> fcoll clj->js turf-area (utils/round-safe 2) read-string)))

(defn- format-area [area-m2]
  (if (>= area-m2 10000)
    (str (utils/round-safe (/ area-m2 10000) 2) " ha")
    (str area-m2 " m²")))

(defn- count-vertices [coords]
  (if (number? (first coords))
    1
    (reduce + 0 (map count-vertices coords))))

(defn- line-segment-count [feature]
  (case (get-in feature [:geometry :type])
    "LineString"      (max 0 (dec (count (get-in feature [:geometry :coordinates]))))
    "MultiLineString" (reduce + 0 (map #(max 0 (dec (count %)))
                                       (get-in feature [:geometry :coordinates])))
    0))

(defn- geom-summary-for-features
  "Lightweight, human-comparable summary of a GeoJSON feature seq: vertex
   count plus route length or polygon area, so revisions can be eyeballed
   for meaningful geometry changes without diffing raw coordinates."
  [features]
  (let [by-type       (group-by #(get-in % [:geometry :type]) features)
        line-features (concat (get by-type "LineString") (get by-type "MultiLineString"))
        poly-features (concat (get by-type "Polygon") (get by-type "MultiPolygon"))
        point-features (concat (get by-type "Point") (get by-type "MultiPoint"))
        vertex-count  (reduce + 0 (map #(count-vertices (get-in % [:geometry :coordinates])) features))]
    (cond
      (seq line-features)
      (let [segments  (reduce + 0 (map line-segment-count line-features))
            length-km (geom-length-km {:type "FeatureCollection" :features line-features})]
        {:vertex-count vertex-count
         :geom-summary (str segments " segmenttiä, " vertex-count " solmua, " length-km " km")})

      (seq poly-features)
      (let [area-m2 (geom-area-m2 {:type "FeatureCollection" :features poly-features})]
        {:vertex-count vertex-count
         :geom-summary (str vertex-count " solmua, " (format-area area-m2))})

      (seq point-features)
      {:vertex-count vertex-count
       :geom-summary (str (count point-features) " piste" (when (> (count point-features) 1) "ttä"))}

      :else
      {:vertex-count 0 :geom-summary "-"})))

;; Shape-based, not path-based: a document can carry more than one
;; FeatureCollection (the site's own :location/:geometries, but also e.g.
;; each entry under :activities/:outdoor-recreation-routes/:routes has its
;; own :geometries) -- anything shaped like one gets the compact summary
;; treatment, wherever it occurs.
(defn- geo-feature-collection? [v]
  (and (map? v) (= "FeatureCollection" (:type v)) (sequential? (:features v))))

;; Fields we add onto each revision map for table display, plus admin
;; metadata pulled from the DB row (not part of the document itself) --
;; stripped before diffing/tree-rendering so only real document content
;; is compared.
(def ^:private synthetic-revision-keys
  #{:index :formatted-date :user-display
    :revision-id :revision-created-at :author :doc-status})

(defn- leaf-str [v]
  (cond
    (nil? v) "-"
    (boolean? v) (str v)
    (keyword? v) (name v)
    ;; Falls through here when only one side of a diff is a FeatureCollection
    ;; (e.g. a route gained/lost its :geometries entirely) -- render it the
    ;; same way the paired-comparison case in diff-paths does, for consistency.
    (geo-feature-collection? v) (:geom-summary (geom-summary-for-features (:features v)))
    (map? v) (str (count v) " kenttää")
    (sequential? v) (let [s (str v)]
                      (if (> (count s) 80) (str (count v) " kpl") s))
    :else (str v)))

(defn- diff-paths
  "Flat list of {:path [...] :old :new} for every leaf that differs between
   two documents. Recurses into maps and vectors alike (vectors index-wise)
   so nested rich structures -- e.g. a route entry under
   :activities/:outdoor-recreation-routes/:routes -- are diffed field by
   field. The one exception is GeoJSON geometry: when BOTH sides of a path
   are FeatureCollections (wherever they appear), they're compared via their
   geom-summary instead of diffing raw coordinates. If only one side is one
   -- the geometry was added or removed entirely -- that's a real structural
   change and falls through to the normal leaf comparison below, so it isn't
   silently swallowed just because e.g. an empty FeatureCollection's summary
   happens to look the same as 'no geometry'."
  [old new]
  (letfn [(walk [path old-v new-v acc]
            (cond
              (and (geo-feature-collection? old-v) (geo-feature-collection? new-v))
              (let [old-s (:geom-summary (geom-summary-for-features (:features old-v)))
                    new-s (:geom-summary (geom-summary-for-features (:features new-v)))]
                (if (= old-s new-s) acc (conj acc {:path path :old old-s :new new-s})))

              (and (map? old-v) (map? new-v))
              (reduce (fn [acc k] (walk (conj path k) (get old-v k) (get new-v k) acc))
                      acc
                      (distinct (concat (keys old-v) (keys new-v))))

              (and (vector? old-v) (vector? new-v))
              (reduce (fn [acc i] (walk (conj path i) (get old-v i) (get new-v i) acc))
                      acc
                      (range (max (count old-v) (count new-v))))

              (= old-v new-v) acc

              :else (conj acc {:path path :old old-v :new new-v})))]
    (walk [] old new [])))

(defn- toggle-path [s path]
  (if (contains? s path) (disj s path) (conj s path)))

(defn- tree-value-node [{:keys [expanded* path label value] :as props}]
  (let [indent (* (count path) 16)]
    (cond
      (geo-feature-collection? value)
      [:div {:style {:padding-left indent :font-family "monospace"}}
       [:> Typography {:variant "body2"}
        (str label ": " (:geom-summary (geom-summary-for-features (:features value))))]]

      (map? value)
      (let [expanded? (contains? @expanded* path)]
        [:div {:style {:padding-left indent}}
         [:div {:style {:cursor "pointer"} :on-click #(swap! expanded* toggle-path path)}
          [:> Typography {:variant "body2" :style {:font-family "monospace"}}
           (str (if expanded? "▼ " "▶ ") label " {" (count value) "}")]]
         (when expanded?
           (doall
             (for [[k v] (sort-by (comp str key) value)]
               ^{:key (str path k)}
               [tree-value-node (assoc props :path (conj path k) :label (name k) :value v)])))])

      (and (sequential? value) (seq value))
      (let [expanded? (contains? @expanded* path)
            show-all? (contains? @expanded* (conj path ::all))
            shown (if (or show-all? (<= (count value) 20)) value (take 20 value))]
        [:div {:style {:padding-left indent}}
         [:div {:style {:cursor "pointer"} :on-click #(swap! expanded* toggle-path path)}
          [:> Typography {:variant "body2" :style {:font-family "monospace"}}
           (str (if expanded? "▼ " "▶ ") label " [" (count value) "]")]]
         (when expanded?
           [:<>
            (doall
              (map-indexed
                (fn [i v]
                  ^{:key (str path i)}
                  [tree-value-node (assoc props :path (conj path i) :label (str "#" i) :value v)])
                shown))
            (when (and (not show-all?) (> (count value) 20))
              [:div {:style {:padding-left (+ indent 16) :cursor "pointer" :color "#757575"}
                     :on-click #(swap! expanded* conj (conj path ::all))}
               (str "… ja " (- (count value) 20) " lisää — näytä kaikki")])])])

      :else
      [:div {:style {:padding-left indent :font-family "monospace"}}
       [:> Typography {:variant "body2"} (str label ": " (leaf-str value))]])))

(defn doc-tree
  "Collapsible read-only view of a full document. Top-level keys start
   expanded/visible; nested branches start collapsed. Any GeoJSON
   FeatureCollection (the site's own geometry, or e.g. a route's nested
   geometry under :activities) is never walked -- shown as a compact
   summary instead -- and any other long array is capped at 20 items with
   a 'show all' toggle, so large routes/polygons stay readable."
  [document]
  (r/with-let [expanded* (r/atom #{})]
    [:div
     (doall
       (for [[k v] (sort-by (comp str key) document)]
         ^{:key (str k)}
         [tree-value-node {:expanded* expanded* :path [k] :label (name k) :value v}]))]))

;; Marker key distinguishing a diff leaf ({::old ::new}) from a branch
;; (a plain nested map) once diff-paths' flat list is renested into a tree.
(def ^:private diff-leaf-marker ::diff-leaf)

(defn- path-key-label
  "diff-paths keys can be keywords (map traversal) or ints (vector index)."
  [k]
  (if (keyword? k) (name k) (str "#" k)))

(defn- build-diff-tree [diffs]
  (reduce (fn [acc {:keys [path old new]}]
            (assoc-in acc path {diff-leaf-marker true :old old :new new}))
          {}
          diffs))

(defn- diff-tree-node [depth label node]
  (let [indent (* depth 16)]
    (if (get node diff-leaf-marker)
      [:div {:style {:padding-left indent :font-family "monospace"}}
       [:> Typography {:variant "body2"}
        (str label ": " (leaf-str (:old node)) "  →  " (leaf-str (:new node)))]]
      [:div {:style {:padding-left indent}}
       [:> Typography {:variant "body2" :style {:font-family "monospace" :font-weight 600}}
        label]
       (doall
         (for [[k v] (sort-by (comp str key) node)]
           ^{:key (str label k)}
           [diff-tree-node (inc depth) (path-key-label k) v]))])))

(defn changes-view
  "Same tree shape as doc-tree, but pruned to only the paths that changed
   since the previous revision -- recursing into nested maps/vectors alike
   (so e.g. a change to just one route's :fi/:se/:en description stays
   visible, instead of collapsing into an opaque 'N kpl -> N kpl'), with
   any nested GeoJSON geometry still summarized as one line."
  [prev-revision revision]
  (let [strip #(apply dissoc % synthetic-revision-keys)
        diffs (when prev-revision
                (diff-paths (strip prev-revision) (strip revision)))]
    (cond
      (nil? prev-revision)
      [:> Typography {:variant "body2" :style {:color "#757575"}}
       "Ensimmäinen tallennettu versio -- ei aiempaa tallennusta vertailtavaksi."]

      (empty? diffs)
      [:> Typography {:variant "body2" :style {:color "#757575"}}
       "Ei muutoksia edelliseen tallennukseen."]

      :else
      (let [tree (build-diff-tree diffs)]
        [:div
         (doall
           (for [[k v] (sort-by (comp str key) tree)]
             ^{:key (str k)}
             [diff-tree-node 0 (path-key-label k) v]))]))))

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

(def ^:private detail-columns 6)

(defn- revision-detail
  "Content shown inside a row's Collapse: a Muutokset/Koko dokumentti
   toggle plus the corresponding view, for one revision."
  [{:keys [revision prev mode on-mode-change]}]
  [:div {:style {:padding "8px 16px 16px"}}
   [:> Grid2 {:container true :spacing 1 :sx #js{:mb 1}}
    [:> Grid2
     [:> Button
      {:size "small"
       :variant (if (= :changes mode) "contained" "outlined")
       :on-click #(on-mode-change :changes)}
      "Muutokset"]]
    [:> Grid2
     [:> Button
      {:size "small"
       :variant (if (= :full mode) "contained" "outlined")
       :on-click #(on-mode-change :full)}
      "Koko dokumentti"]]]
   (if (= :changes mode)
     [changes-view prev revision]
     ^{:key (str "tree-" (:revision-id revision))}
     [doc-tree (apply dissoc revision synthetic-revision-keys)])])

(defn site-history-results []
  (r/with-let [expanded* (r/atom {})
               detail-mode* (r/atom {})]
    (let [results (<== [::subs/site-history-results])
          error (<== [::subs/site-history-error])
          loading? (<== [::subs/site-history-loading?])
          users (<== [::subs/users])
          tr (<== [:lipas.ui.subs/translator])
          ;; Newest first for iteration/diffing (each row's "previous" is
          ;; the next one down), but :index counts from the oldest revision
          ;; (#1) forward, matching how admins expect version numbers to read.
          sorted-raw (->> (or results [])
                          (sort-by :event-date #(compare %2 %1))
                          vec)
          total (count sorted-raw)
          sorted (->> sorted-raw
                      (map-indexed
                        (fn [idx revision]
                          (-> revision
                              (assoc :index (- total idx))
                              (assoc :formatted-date (format-timestamp (:event-date revision)))
                              (assoc :user-display (get-user-display-name users (:author revision))))))
                      vec)]
      [:<>
       ;; Error display
       (when error
         [:> Alert {:severity "error" :sx #js{:mb 2}}
          error])

       ;; Loading indicator
       (when loading?
         [:> LinearProgress {:sx #js{:mb 2}}])

       ;; Results: each row expands in place (accordion-style, like the PTV
       ;; site table) so the diff/full-doc view sits right next to the row
       ;; that was clicked, instead of scrolling to the bottom of a long list.
       (when (and results (seq results))
         [:> Card
          [:> CardHeader {:title (str "Hakutulokset (" (count results) " versiota)")}]
          [:> CardContent
           [:> Table
            [:> TableHead
             [:> TableRow
              [:> TableCell {:padding "checkbox"}]
              [:> TableCell "#"]
              [:> TableCell (tr :time/time)]
              [:> TableCell (tr :lipas.sports-site/status)]
              [:> TableCell (tr :lipas.sports-site/name)]
              [:> TableCell (tr :lipas.user/user)]]]
            [:> TableBody
             (doall
               (for [[idx revision] (map-indexed vector sorted)
                     :let [id (:revision-id revision)
                           expanded? (get @expanded* id false)
                           prev (get sorted (inc idx))
                           mode (get @detail-mode* id :changes)]]
                 ^{:key (str id)}
                 [:<>
                  [:> TableRow
                   {:hover true
                    :style {:cursor "pointer"}
                    :on-click #(swap! expanded* update id not)}
                   [:> TableCell {:padding "checkbox"}
                    [:> IconButton {:size "small"}
                     [:> Icon (if expanded? "keyboard_arrow_up" "keyboard_arrow_down")]]]
                   [:> TableCell (:index revision)]
                   [:> TableCell (:formatted-date revision)]
                   [:> TableCell (:status revision)]
                   [:> TableCell (:name revision)]
                   [:> TableCell (:user-display revision)]]
                  [:> TableRow
                   [:> TableCell {:style {:paddingTop 0 :paddingBottom 0} :colSpan detail-columns}
                    [:> Collapse {:in expanded? :timeout "auto" :unmountOnExit true}
                     [revision-detail {:revision revision
                                       :prev prev
                                       :mode mode
                                       :on-mode-change #(swap! detail-mode* assoc id %)}]]]]]))]]]])

       ;; No results message
       (when (and results (empty? results))
         [:> Alert {:severity "info"}
          "No history found for this LIPAS ID"])])))

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
