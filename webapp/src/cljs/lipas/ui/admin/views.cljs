(ns lipas.ui.admin.views
  (:require
   [clojure.spec.alpha :as s]
   [lipas.data.styles :as styles]
   [lipas.ui.admin.events :as events]
   [lipas.ui.admin.subs :as subs]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

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
  (let [locale    (tr)
        cities    (<== [::subs/cities-list locale])
        types     (<== [::subs/types-list locale])
        sites     (<== [::subs/sites-list])
        user      (<== [::subs/editing-user])
        history   (<== [::subs/user-history])
        existing? (some? (:id user))]

    [lui/full-screen-dialog
     {:open?       (boolean (seq user))
      :title       (or (:username user) (:email user))
      :close-label (tr :actions/close)
      :on-close    #(==> [::events/set-user-to-edit nil])
      :bottom-actions
      [[lui/email-button
        {:label    (tr :lipas.admin/magic-link)
         :disabled (not (s/valid? :lipas/new-user user))
         :on-click #(==> [::events/open-magic-link-dialog])}]
       (when existing?
         [mui/button
          {:variant  "contained"
           :color    "secondary"
           :on-click #(==> [::events/save-user user])}
          (tr :actions/save)])]}

     [mui/grid {:container true :spacing 8}

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
          :on-change #(==> [::events/edit-user [:permissions :cities] %])}]]]

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
        types      (<== [:lipas.ui.sports-sites.subs/all-types])]
    [mui/table
     [mui/table-head
      [mui/table-row
       [mui/table-cell "Type-code"]
       [mui/table-cell "Type-name"]
       [mui/table-cell "Geometry"]
       [mui/table-cell "Old-fill"]
       [mui/table-cell "New-fill"]
       [mui/table-cell "Old-stroke"]
       [mui/table-cell "New-stroke"]]]

     (into
      [mui/table-body]
      (for [[type-code type] (sort-by first types)
            :let             [shape (-> type-code styles/all :shape)
                              fill (-> type-code styles/all :fill :color)
                              stroke (-> type-code styles/all :stroke :color)]]
        [mui/table-row
         [mui/table-cell type-code]
         [mui/table-cell (-> type :name :fi)]
         [mui/table-cell shape]
         [mui/table-cell
          [color-picker {:value fill :on-change #()}]]
         [mui/table-cell
          [color-picker
           {:value     (-> (new-colors type-code) :fill)
            :on-change (partial pick-color type-code :fill)}]
          [mui/button
           {:size :small :on-click #(pick-color type-code :fill fill)}
           "reset"]]
         [mui/table-cell
          [color-picker {:value stroke :on-change #()}]]
         [mui/table-cell
          [color-picker
           {:value     (-> (new-colors type-code) :stroke)
            :on-change (partial pick-color type-code :stroke)}]
          [mui/button
           {:size :small :on-click #(pick-color type-code :stroke stroke)}
           "reset"]]]))]))

(defn admin-panel []
  (let [tr           (<== [:lipas.ui.subs/translator])
        users        (<== [::subs/users-list])
        users-filter (<== [::subs/users-filter])
        selected-tab (<== [::subs/selected-tab])]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/tool-bar
       [mui/tabs
        {:value     selected-tab
         :on-change #(==> [::events/select-tab %2])}
        [mui/tab {:label (tr :lipas.admin/users)}]
        [mui/tab {:label "Symbolityökalu"}]]]

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

          [mui/grid {:container true :spacing 32}

           ;; Add user button
           [mui/grid {:item true :style {:flex-grow 1}}
            [mui/fab
             {:color    "secondary"
              :size     "small"
              :style    {:margin-top "1em"}
              :on-click #(==> [::events/edit-user [:email] "fix@me.com"])}
             [mui/icon "add"]]]

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
             [:types (tr :lipas.user.permissions/types)]]
            :sort-fn   :email
            :items     users
            :on-select #(==> [::events/set-user-to-edit %])}]]])]]))

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
