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
            ["@mui/material/ToggleButton$default" :as ToggleButton]
            ["@mui/material/ToggleButtonGroup$default" :as ToggleButtonGroup]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [lipas.data.styles :as styles]
            [lipas.roles :as roles]
            [lipas.ui.admin.events :as events]
            [lipas.ui.admin.subs :as subs]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.mui :as mui]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [re-frame.core :as rf]
            [uix.core :as uix :refer [$ defui]]
            [reagent.core :as r]))

(defn magic-link-dialog [{:keys [tr]}]
  (let [open? (<== [::subs/magic-link-dialog-open?])
        variants (<== [::subs/magic-link-variants])
        variant (<== [::subs/selected-magic-link-variant])
        user (<== [::subs/editing-user])]

    [mui/dialog {:open open?}
     [mui/dialog-title
      (tr :lipas.admin/send-magic-link (:email user))]
     [mui/dialog-content
      [mui/form-group
       [lui/select
        {:label (tr :lipas.admin/select-magic-link-template)
         :items variants
         :value variant
         :on-change #(==> [::events/select-magic-link-variant %])}]
       [mui/button
        {:style {:margin-top "1em"}
         :on-click #(==> [::events/send-magic-link user variant])}
        (tr :actions/submit)]
       [mui/button
        {:style {:margin-top "1em"}
         :on-click #(==> [::events/close-magic-link-dialog])}
        (tr :actions/cancel)]]]]))

(defui permissions-request-card [{:keys [permissions-request tr]}]
  ($ Card
     ;; TODO: Add the color to the theme
     {:sx #js {:backgroundColor mui/gray3
               :mb 2}}
     ($ CardHeader
        {:subheader (tr :lipas.user/requested-permissions)})
     ($ CardContent
        ($ Typography
           {:sx #js {:fontStyle "italic"}}
           (or permissions-request
               "-")))))

(def filter-ac (createFilterOptions))

(defui site-select [{:keys [tr required data]}]
  (let [sites (use-subscribe [::subs/sites-list])]
    ($ ac/autocomplete2
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
                           filtered))})))

(defui type-code-select [{:keys [tr required data]}]
  (let [types (use-subscribe [::subs/types-list (tr)])]
    ($ ac/autocomplete2
       {:options types
        :label (str (tr :lipas.user.permissions/types)
                    (when required
                      " *"))
        :value (to-array (or (:type-code data) []))
        :onChange (fn [_e v] (rf/dispatch [::events/set-role-context-value :type-code (mapv ac/safe-value v)]))
        :multiple true})))

(defui city-code-select [{:keys [tr required data]}]
  (let [cities (use-subscribe [::subs/cities-list (tr)])]
    ($ ac/autocomplete2
       {:options cities
        :label (str (tr :lipas.user.permissions/cities)
                    (when required
                      " *"))
        :value (to-array (or (:city-code data) []))
        :onChange (fn [_e v] (rf/dispatch [::events/set-role-context-value :city-code (mapv ac/safe-value v)]))
        :multiple true})))

(defui activity-select [{:keys [tr required data]}]
  (let [activities (<== [::subs/activities-list (tr)])]
    ($ ac/autocomplete2
       {:options activities
        :label (str (tr :lipas.user.permissions/activities)
                    (when required
                      " *"))
        :value (to-array (or (:activity data) []))
        :onChange (fn [_e v] (rf/dispatch [::events/set-role-context-value :activity (mapv ac/safe-value v)]))
        :multiple true})))

(defui context-key-edit [{:keys [k] :as props}]
  (case k
    :lipas-id
    ($ site-select props)

    :type-code
    ($ type-code-select props)

    :city-code
    ($ city-code-select props)

    :activity
    ($ activity-select props)))

(defui role-form [{:keys [tr]}]
  (let [data (use-subscribe [::subs/edit-role])
        editing? (:editing? data)

        role (:role data)
        required-context-keys (:required-context-keys (get roles/roles role))
        optional-context-keys (:optional-context-keys (get roles/roles role))]
    ($ Stack
       {:direction "column"
        :sx #js {:gap 1}}
       ($ Typography
          {:variant "h6"}
          (if editing?
            (tr :lipas.user.permissions.roles.edit-role/edit-header)
            (tr :lipas.user.permissions.roles.edit-role/new-header)))

       ($ ac/autocomplete2
          {:options (for [[k {:keys [assignable]}] roles/roles
                          :when assignable]
                      {:value k
                       :label (tr (keyword :lipas.user.permissions.roles.role-names k))})
           :readOnly editing?
           :label (tr :lipas.user.permissions.roles/role)
           :value (:role data)
           :onChange (fn [_e v] (rf/dispatch [::events/set-new-role (ac/safe-value v)]))})

       (when-not (:role data)
         ($ Typography
            (tr :lipas.user.permissions.roles.edit-role/choose-role)))

       (for [k required-context-keys]
         ($ context-key-edit
            {:key k
             :k k
             :required data
             :tr tr
             :data data}))

       (for [k optional-context-keys]
         ($ context-key-edit
            {:key k
             :k k
             :tr tr
             :data data}))

       ($ Stack
          {:direction "row"
           :sx #js {:gap 1}}
          (if editing?
            ($ Button
               {:onClick (fn [_e] (rf/dispatch [::events/stop-edit]))}
               (tr :lipas.user.permissions.roles.edit-role/stop-editing))
            ($ Button
               {:onClick (fn [_e] (rf/dispatch [::events/add-new-role]))}
               (tr :lipas.user.permissions.roles.edit-role/add)))))))

(defui role-context [{:keys [tr k v]}]
  (let [locale (tr)
        localized (use-subscribe [::user-subs/context-value-name k v locale])]
    ($ Typography
       {:key (str k "-" v)
        :component "span"
        :sx #js {:mr 1}}
       ;; Role context key name
       (tr (keyword :lipas.user.permissions.roles.context-keys k))
       ": "
       ;; Value localized name
       localized
       ;; Value code
       " " v)))

(defui roles-card [{:keys [tr]}]
  (let [user (use-subscribe [::subs/editing-user])
        data (use-subscribe [::subs/edit-role])
        editing? (:editing? data)]
    ;; TODO: replace the container grid
    ($ Grid
       {:item true
        :xs 12
        :md 6}
       ($ Card
          {:square true}
          ($ CardHeader
             {:title "Roolit"})
          ($ CardContent
             ($ FormGroup
                ($ permissions-request-card
                   {:permissions-request (-> user :user-data :permissions-request)
                    :tr tr})

                ($ List
                   (for [[i {:keys [role] :as x}]
                         (->> user
                              :permissions
                              :roles
                              ;; Edit uses the roles vector index, so add idx before sort
                              (map-indexed vector)
                              (sort-by (comp roles/role-sort-fn second)))]
                     ($ ListItem
                        {:key i}
                        ($ ListItemText
                           ($ Typography
                              {:component "span"
                               :sx #js {:mr 2
                                        :fontWeight "bold"}}
                              (tr (keyword :lipas.user.permissions.roles.role-names role)))
                           (for [[context-key vs] (dissoc x :role)]
                             ($ :<>
                                {:key context-key}
                                (for [v vs]
                                  ($ role-context
                                     {:key v
                                      :k context-key
                                      :v v
                                      :tr tr})))))
                        ($ ListItemSecondaryAction
                           ($ IconButton
                              {:onClick (fn [_e] (rf/dispatch [::events/edit-role i]))}
                              ($ Icon "edit"))
                           ($ IconButton
                              {:onClick (fn [_e] (rf/dispatch [::events/remove-role x]))
                               ;; Deleting item while editing would break the editing :roles idx numbers
                               :disabled editing?}
                              ($ Icon "delete"))))))

                ($ role-form
                   {:tr tr})))))))

(defn user-dialog [tr]
  (let [locale (tr)
        cities (<== [::subs/cities-list locale])
        types (<== [::subs/types-list locale])
        sites (<== [::subs/sites-list])
        activities (<== [::subs/activities-list locale])
        user (<== [::subs/editing-user])
        history (<== [::subs/user-history])
        existing? (some? (:id user))]

    [lui/full-screen-dialog
     {:open? (boolean (seq user))
      :title (or (:username user) (:email user))
      :close-label (tr :actions/close)
      :on-close #(==> [::events/set-user-to-edit nil])
      :bottom-actions
      [;; GDPR remove button
       [mui/button
        {:variant "contained"
         :color "secondary"
         :on-click (fn []
                     (==> [:lipas.ui.events/confirm
                           "Haluatko varmasti GDPR-poistaa tämän käyttäjän?"
                           (fn []
                             (==> [::events/gdpr-remove-user user]))]))}
        [mui/icon {:sx #js{:mr 1}} "gpp_bad"]
        "GDPR-poista"]
       ;; Archive button
       (when (= "active" (:status user))
         [mui/button
          {:variant "contained"
           :color "secondary"
           :on-click #(==> [::events/update-user-status user "archived"])}
          [mui/icon {:sx #js{:mr 1}} "archive"]
          "Arkistoi"])

       ;; Restore button
       (when (= "archived" (:status user))
         [mui/button
          {:variant "contained"
           :color "secondary"
           :on-click #(==> [::events/update-user-status user "active"])}
          [mui/icon {:sx #js{:mr 1}} "restore"]
          "Palauta"])

       ;; Send magic link button
       [lui/email-button
        {:label (tr :lipas.admin/magic-link)
         :disabled (not (s/valid? :lipas/new-user user))
         :on-click #(==> [::events/open-magic-link-dialog])}]

       ;; Save button
       (when existing?
         [mui/button
          {:variant "contained"
           :color "secondary"
           :on-click #(==> [::events/save-user user])}
          [mui/icon {:sx #js{:mr 1}} "save"]
          (tr :actions/save)])]}

     [mui/grid {:container true :spacing 1}

      [magic-link-dialog {:tr tr}]

      ;;; Contact info
      [lui/form-card {:title (tr :lipas.user/contact-info)}
       [mui/form-group

        ;; Email
        [lui/text-field
         {:label (tr :lipas.user/email)
          :value (:email user)
          :on-change #(==> [::events/edit-user [:email] %])
          :disabled existing?}]

        ;; Username
        [lui/text-field
         {:label (tr :lipas.user/username)
          :value (:username user)
          :on-change #(==> [::events/edit-user [:username] %])
          :disabled existing?}]

        ;; Firstname
        [lui/text-field
         {:label (tr :lipas.user/firstname)
          :value (-> user :user-data :firstname)
          :on-change #(==> [::events/edit-user [:user-data :firstname] %])
          :disabled existing?}]

        ;; Lastname
        [lui/text-field
         {:label (tr :lipas.user/lastname)
          :value (-> user :user-data :lastname)
          :on-change #(==> [::events/edit-user [:user-data :lastname] %])
          :disabled existing?}]]]

      ($ roles-card
         {:tr tr})

      ;;; Permissions
      ;; TODO: Replace this with roles management
      [lui/form-card {:title (str (tr :lipas.user/permissions)
                                  " " (tr :lipas.user.permissions.roles/permissions-old))}
       [mui/form-group

        ($ permissions-request-card
           {:permissions-request (-> user :user-data :permissions-request)
            :tr tr})

        ;; Admin?
        [lui/checkbox
         {:disabled true
          :label (tr :lipas.user.permissions/admin?)
          :value (-> user :permissions :admin?)
          :on-change #(==> [::events/edit-user [:permissions :admin?] %])}]

        ;; Permission to all types?
        [lui/checkbox
         {:disabled true
          :label (tr :lipas.user.permissions/all-types?)
          :value (-> user :permissions :all-types?)
          :on-change #(==> [::events/edit-user [:permissions :all-types?] %])}]

        ;; Permission to all cities?
        [lui/checkbox
         {:disabled true
          :label (tr :lipas.user.permissions/all-cities?)
          :value (-> user :permissions :all-cities?)
          :on-change #(==> [::events/edit-user [:permissions :all-cities?] %])}]

        ;; Permission to individual spoorts-sites
        [lui/autocomplete
         {:disabled true
          :items sites
          :label (tr :lipas.user.permissions/sports-sites)
          :value (-> user :permissions :sports-sites)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :sports-sites] %])}]

        ;; Permission to individual types
        [lui/autocomplete
         {:disabled true
          :items types
          :label (tr :lipas.user.permissions/types)
          :value (-> user :permissions :types)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :types] %])}]

        ;; Permission to individual cities
        [lui/autocomplete
         {:disabled true
          :items cities
          :label (tr :lipas.user.permissions/cities)
          :value (-> user :permissions :cities)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :cities] %])}]

        ;; Permission to activities
        [lui/autocomplete
         {:disabled true
          :items activities
          :label (tr :lipas.user.permissions/activities)
          :value (-> user :permissions :activities)
          :multi? true
          :on-change #(==> [::events/edit-user [:permissions :activities] %])}]

        [mui/button
         {:disabled true
          :on-click #(==> [::events/grant-access-to-activity-types
                           (-> user :permissions :activities)])}
         "Anna oikeus aktiviteettien tyyppeihin"]]]

      ;;; History
      [lui/form-card {:title (tr :lipas.user/history)}
       [lui/table-v2
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
            :let [shape (-> type-code types :geometry-type)
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
                                    {:items [{:label "Circle" :value "circle"}
                                             {:label "Square" :value "square"}]
                                     :value (or (-> type-code new-colors :symbol)
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
             {:value (-> (new-colors type-code) :fill)
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
             {:value (-> (new-colors type-code) :stroke)
              :on-change (partial pick-color type-code :stroke)}]]
           [mui/grid {:item true}
            [mui/button
             {:size :small :on-click #(pick-color type-code :stroke stroke)}
             "reset"]]]]]))]))

(defn job-details-dialog [tr]
  (let [open? (<== [::subs/job-details-dialog-open?])
        job-id (<== [::subs/selected-job-id])
        job (<== [::subs/selected-job-details job-id])
        reprocessing? (<== [::subs/reprocessing?])]
    [mui/dialog
     {:open open?
      :on-close #(==> [::events/close-job-details-dialog])
      :max-width "md"
      :full-width true}
     [mui/dialog-title "Job Details"
      [mui/icon-button
       {:on-click #(==> [::events/close-job-details-dialog])
        :sx #js{:position "absolute" :right 8 :top 8}}
       [mui/icon "close"]]]

     (when job
       [mui/dialog-content
        ;; Basic info
        [mui/typography {:variant "h6" :gutterBottom true} "Basic Information"]
        [mui/grid2 {:container true :spacing 2 :sx #js{:mb 3}}
         [mui/grid2 {:size 6}
          [mui/typography {:color "textSecondary"} "ID"]
          [mui/typography (str (:id job))]]
         [mui/grid2 {:size 6}
          [mui/typography {:color "textSecondary"} "Job Type"]
          [mui/typography (get-in job [:original-job :type] "Unknown")]]
         [mui/grid2 {:size 6}
          [mui/typography {:color "textSecondary"} "Failed At"]
          [mui/typography (let [died-at (:died-at job)]
                            (cond
                              (inst? died-at) (.toLocaleString died-at)
                              (string? died-at) died-at
                              :else (str died-at)))]]
         [mui/grid2 {:size 6}
          [mui/typography {:color "textSecondary"} "Status"]
          [mui/chip {:label (if (:acknowledged job) "Acknowledged" "Unacknowledged")
                     :color (if (:acknowledged job) "default" "warning")
                     :size "small"}]]]

        ;; Error details
        [mui/typography {:variant "h6" :gutterBottom true} "Error Details"]
        [mui/paper {:sx #js{:p 2 :mb 3 :bgcolor "#f5f5f5"}}
         [mui/typography {:variant "body2" :component "pre" :sx #js{:whiteSpace "pre-wrap" :fontFamily "monospace"}}
          (:error-message job)]]

        ;; Job payload
        [mui/typography {:variant "h6" :gutterBottom true} "Job Payload"]
        [mui/paper {:sx #js{:p 2 :bgcolor "#f5f5f5" :overflow "auto"}}
         [mui/typography {:variant "body2" :component "pre" :sx #js{:fontFamily "monospace"}}
          (js/JSON.stringify (clj->js (:original-job job)) nil 2)]]])

     [mui/dialog-actions
      [mui/button
       {:on-click #(==> [::events/close-job-details-dialog])}
       "Close"]
      [mui/button
       {:variant "contained"
        :color "primary"
        :disabled reprocessing?
        :start-icon (when reprocessing? (r/as-element [mui/circular-progress {:size 16}]))
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
       [mui/alert {:severity "error" :sx #js{:mb 2}}
        error])

     ;; Loading indicator
     (when loading?
       [mui/linear-progress {:sx #js{:mb 2}}])

     ;; Health Overview Card
     (when health-data
       [mui/card {:sx #js{:mb 2}}
        [mui/card-header {:title "Queue Health"}]
        [mui/card-content
         [mui/grid2 {:container true :spacing 2}
          ;; Pending jobs
          [mui/grid2 {:size 12 :size/sm 6 :size/md 3}
           [mui/paper {:sx #js{:p 2 :bgcolor (if (> (or (:pending_count health-data) 0) 100) "#ffebee" "#f5f5f5")}}
            [mui/typography {:variant "h4"} (str (or (:pending_count health-data) 0))]
            [mui/typography {:color "textSecondary"} "Pending Jobs"]
            (when-let [oldest (:oldest_pending_minutes health-data)]
              [mui/typography {:variant "caption" :color "textSecondary"}
               (str "Oldest: " oldest " min")])]]

          ;; Processing jobs
          [mui/grid2 {:size 12 :size/sm 6 :size/md 3}
           [mui/paper {:sx #js{:p 2 :bgcolor "#f5f5f5"}}
            [mui/typography {:variant "h4"} (str (or (:processing_count health-data) 0))]
            [mui/typography {:color "textSecondary"} "Processing"]
            (when-let [longest (:longest_processing_minutes health-data)]
              [mui/typography {:variant "caption" :color "textSecondary"}
               (str "Longest: " longest " min")])]]

          ;; Failed jobs
          [mui/grid2 {:size 12 :size/sm 6 :size/md 3}
           [mui/paper {:sx #js{:p 2 :bgcolor (if (> (or (:failed_count health-data) 0) 0) "#fff3e0" "#f5f5f5")}}
            [mui/typography {:variant "h4"} (str (or (:failed_count health-data) 0))]
            [mui/typography {:color "textSecondary"} "Failed"]]]

          ;; Dead letter jobs
          [mui/grid2 {:size 12 :size/sm 6 :size/md 3}
           (let [dlq-stats (<== [::subs/dead-letter-stats])
                 unacknowledged (:unacknowledged dlq-stats 0)]
             [mui/paper {:sx #js{:p 2 :bgcolor (if (> unacknowledged 0) "#ffebee" "#f5f5f5")}}
              [mui/typography {:variant "h4"} (str unacknowledged)]
              [mui/typography {:color "textSecondary"} "Unacknowledged DLQ"]
              [mui/button
               {:size "small"
                :sx #js{:mt 1}
                :on-click #(==> [::events/select-jobs-sub-tab 1])}
               "View DLQ"]])]]]])

     ;; Performance Metrics
     (when-let [metrics-table-data (<== [::subs/jobs-metrics-table-data])]
       [mui/card {:sx #js{:mb 2}}
        [mui/card-header {:title "Performance Metrics"}]
        [mui/card-content
         [lui/table
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
       [mui/card {:sx #js{:mb 2}}
        [mui/card-header {:title "Current Queue Status"}]
        [mui/card-content
         [mui/grid2 {:container true :spacing 2}
          (for [[status data] current-stats
                :when data]
            [mui/grid2 {:key status :size 12 :size/sm 6 :size/md 4}
             [mui/paper {:sx #js{:p 2}}
              [mui/typography {:variant "h6"} (if (keyword? status) (name status) (str status))]
              [mui/typography (str "Count: " (:count data))]
              (when-let [oldest (:oldest_created_at data)]
                [mui/typography {:variant "caption" :display "block"}
                 (str "Oldest: " oldest)])
              (when-let [oldest-min (:oldest_minutes data)]
                [mui/typography {:variant "caption"}
                 (str oldest-min " minutes ago")])]])]]])

     ;; Job Types Configuration
     (when metrics-data
       [mui/card
        [mui/card-header {:title "Job Types Configuration"}]
        [mui/card-content
         [mui/grid2 {:container true :spacing 2}
          [mui/grid2 {:size 12 :size/md 6}
           [mui/typography {:variant "subtitle1"} "Fast Lane Jobs"]
           [mui/list {:dense true}
            (for [job-type (:fast-job-types metrics-data)]
              [mui/list-item {:key job-type}
               [mui/list-item-text job-type]])]]
          [mui/grid2 {:size 12 :size/md 6}
           [mui/typography {:variant "subtitle1"} "Slow Lane Jobs"]
           [mui/list {:dense true}
            (for [job-type (:slow-job-types metrics-data)]
              [mui/list-item {:key job-type}
               [mui/list-item-text job-type]])]]]]])]))

;; Dead Letter Queue tab content
(defn dead-letter-queue-tab []
  (let [dlq-jobs (<== [::subs/filtered-dead-letter-jobs])
        loading? (<== [::subs/dead-letter-loading?])
        error (<== [::subs/dead-letter-error])
        filter-value (<== [::subs/dead-letter-filter])
        selected-ids (<== [::subs/selected-job-ids])
        bulk-reprocessing? (<== [::subs/bulk-reprocessing?])
        tr (<== [:lipas.ui.subs/translator])
        all-job-ids (set (map :id dlq-jobs))
        some-selected? (seq selected-ids)
        all-selected? (and some-selected?
                           (= (count selected-ids) (count dlq-jobs)))]
    [:<>
     ;; Job details dialog
     [job-details-dialog tr]

     ;; Filter buttons
     [mui/toggle-button-group
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
      [mui/toggle-button {:value :unacknowledged} "Unacknowledged"]
      [mui/toggle-button {:value :acknowledged} "Acknowledged"]
      [mui/toggle-button {:value :all} "All"]]

     ;; Error display
     (when error
       [mui/alert {:severity "error" :sx #js{:mb 2}} error])

     ;; Bulk actions toolbar
     (when some-selected?
       [mui/paper {:sx #js{:p 2 :mb 2 :bgcolor "action.hover"}}
        [mui/stack {:direction "row" :spacing 2 :alignItems "center"}
         [mui/typography (str (count selected-ids) " selected")]
         [mui/button
          {:variant "contained"
           :size "small"
           :disabled bulk-reprocessing?
           :start-icon (when bulk-reprocessing?
                         (r/as-element [mui/circular-progress {:size 16}]))
           :on-click #(when (js/confirm (str "Reprocess " (count selected-ids) " selected job(s)?"))
                        (==> [::events/reprocess-selected-jobs]))}
          (if bulk-reprocessing? "Reprocessing..." "Reprocess Selected")]
         [mui/button
          {:variant "outlined"
           :size "small"
           :on-click #(==> [::events/clear-job-selection])}
          "Clear Selection"]]])

     ;; Loading indicator
     (when loading?
       [mui/linear-progress {:sx #js{:mb 2}}])

     ;; Jobs table
     (if (empty? dlq-jobs)
       [mui/typography {:color "textSecondary"} "No jobs in the selected filter"]
       [mui/table {:sx #js{:minWidth 650}}
        [mui/table-head
         [mui/table-row
          [mui/table-cell {:padding "checkbox"}
           [mui/checkbox
            {:checked all-selected?
             :indeterminate (and some-selected? (not all-selected?))
             :on-change #(if all-selected?
                           (==> [::events/clear-job-selection])
                           (==> [::events/select-all-jobs all-job-ids]))}]]
          [mui/table-cell "ID"]
          [mui/table-cell "Job Type"]
          [mui/table-cell "Error Message"]
          [mui/table-cell "Failed At"]
          [mui/table-cell "Status"]
          [mui/table-cell {:align "right"} "Actions"]]]

        [mui/table-body
         (for [job (sort-by :died-at #(compare %2 %1) dlq-jobs)]
           [mui/table-row {:key (:id job)
                           :sx #js{"&:last-child td, &:last-child th" #js{:border 0}}
                           :selected (contains? selected-ids (:id job))}
            [mui/table-cell {:padding "checkbox"}
             [mui/checkbox
              {:checked (contains? selected-ids (:id job))
               :on-change #(==> [::events/toggle-job-selection (:id job)])}]]
            [mui/table-cell (:id job)]
            [mui/table-cell (get-in job [:original-job :type] "Unknown")]
            [mui/table-cell
             (let [msg (:error-message job)]
               [mui/tooltip {:title msg}
                [mui/typography {:variant "body2"
                                 :sx #js{:cursor "help"
                                         :maxWidth 300
                                         :overflow "hidden"
                                         :textOverflow "ellipsis"
                                         :whiteSpace "nowrap"}}
                 (if (> (count msg) 50)
                   (str (subs msg 0 47) "...")
                   msg)]])]
            [mui/table-cell (let [died-at (:died-at job)]
                              (cond
                                (inst? died-at) (.toLocaleString died-at)
                                (string? died-at) (-> died-at
                                                      (str/replace "T" " ")
                                                      (str/split ".")
                                                      first)
                                :else (str died-at)))]
            [mui/table-cell
             (if (:acknowledged job)
               [mui/chip {:label "Acknowledged"
                          :size "small"
                          :color "default"}]
               [mui/chip {:label "Unacknowledged"
                          :size "small"
                          :color "warning"}])]
            [mui/table-cell {:align "right"}
             [mui/stack {:direction "row" :spacing 1 :justifyContent "flex-end"}
              [mui/button
               {:size "small"
                :on-click #(==> [::events/open-job-details-dialog (:id job)])}
               "View"]
              [mui/button
               {:size "small"
                :color "primary"
                :on-click #(when (js/confirm "Reprocess this job?")
                             (==> [::events/reprocess-single-job (:id job)]))}
               "Reprocess"]]]])]])]))

(defn jobs-monitor-view []
  (let [selected-sub-tab (<== [::subs/jobs-selected-sub-tab])]
    [mui/card {:square true}
     [mui/card-content
      [mui/typography {:variant "h5"} "Jobs Queue Monitor"]

      ;; Refresh button
      [mui/button
       {:variant "contained"
        :color "primary"
        :on-click #(do
                     (==> [::events/fetch-jobs-health])
                     (==> [::events/fetch-jobs-metrics])
                     (==> [::events/fetch-dead-letter-jobs {:acknowledged false}]))
        :style {:margin-bottom "1em"}}
       [mui/icon {:sx #js{:mr 1}} "refresh"]
       "Refresh"]

      ;; Sub-tabs
      [mui/tabs
       {:value selected-sub-tab
        :on-change #(==> [::events/select-jobs-sub-tab %2])
        :sx #js{:borderBottom 1 :borderColor "divider" :mb 2}}
       [mui/tab {:label "Monitoring"}]
       [mui/tab {:label "Dead Letter Queue"}]]

      ;; Tab content
      (case selected-sub-tab
        0 [jobs-monitoring-tab]
        1 [dead-letter-queue-tab]
        [jobs-monitoring-tab])]]))

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
        :sort-fn :type-code
        :items types
        :on-select #(js/alert "Ei tee mitään vielä...")}]]]))

(defn admin-panel []
  (let [tr (<== [:lipas.ui.subs/translator])
        status (<== [::subs/users-status])
        users (<== [::subs/users-list])
        users-filter (<== [::subs/users-filter])
        selected-tab (<== [::subs/selected-tab])]
    [mui/paper
     [mui/grid {:container true}
      [mui/grid {:item true :xs 12}
       [mui/tool-bar
        [mui/tabs
         {:value selected-tab
          :on-change #(==> [::events/select-tab %2])
          :indicator-color "secondary"
          :text-color "inherit"}
         [mui/tab {:label (tr :lipas.admin/users)}]
         [mui/tab {:label "Symbolityökalu"}]
         [mui/tab {:label "Tyyppikoodit"}]
         [mui/tab {:label "Jobs Monitor"}]]]

       (when (= 1 selected-tab)
         [:<>
          [color-selector]
          [mui/fab
           {:style {:position "sticky" :bottom "1em" :left "1em"}
            :variant "extended"
            :color "secondary"
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
              {:color "secondary"
               :size "small"
               :style {:margin-top "1em"}
               :on-click #(==> [::events/edit-user [:email] "fix@me.com"])}
              [mui/icon "add"]]]

            ;; Status selector
            [mui/grid {:item true}
             [lui/select
              {:style {:width "150px"}
               :label "Status"
               :value status
               :items ["active" "archived"]
               :value-fn identity
               :label-fn identity
               :on-change #(==> [::events/select-status %])}]]

            ;; Users filter
            [mui/grid {:item true}
             [lui/text-field
              {:label (tr :search/search)
               :on-change #(==> [::events/filter-users %])
               :value users-filter}]]]

           ;; Users table
           [lui/table
            {:headers
             [[:email (tr :lipas.user/email)]
              [:firstname (tr :lipas.user/firstname)]
              [:lastname (tr :lipas.user/lastname)]
              [:roles (tr :lipas.user.permissions.roles/roles)]]
             :sort-fn :email
             :items users
             :on-select #(==> [::events/set-user-to-edit %])}]]])

       (when (= 2 selected-tab)
         [type-codes-view])

       (when (= 3 selected-tab)
         [jobs-monitor-view])]]]))

(defn main []
  (let [admin? @(rf/subscribe [:lipas.ui.user.subs/check-privilege nil :users/manage])]
    (if admin?
      [admin-panel]
      (==> [:lipas.ui.events/navigate "/"]))))
