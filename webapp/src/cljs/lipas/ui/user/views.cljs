(ns lipas.ui.user.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.components :as lui]
            [lipas.ui.user.subs :as subs]
            [lipas.ui.user.events :as events]
            [lipas.ui.routes :refer [navigate!]]
            [lipas.ui.utils :refer [<== ==>]]))

(defn user-form [tr data]
  [mui/form-group

   ;; Email
   [lui/text-field
    {:label    (tr :lipas.user/email)
     :value    (:email data)
     :disabled true}]

   ;; Username
   [lui/text-field
    {:label    (tr :lipas.user/username)
     :value    (:username data)
     :disabled true}]

   ;; Firstname
   [lui/text-field
    {:label    (tr :lipas.user/firstname)
     :value    (-> data :user-data :firstname)
     :disabled true}]

   ;; Lastname
   [lui/text-field
    {:label    (tr :lipas.user/lastname)
     :value    (-> data :user-data :lastname)
     :disabled true}]

   ;; TODO figure out how to represent permissions in a clear way

   ;; Permissions

   ;; ;;; Admin?
   ;; [lui/checkbox
   ;;  {:label    (tr :lipas.user.permissions/admin?)
   ;;   :value    (-> data :permissions :admin?)
   ;;   :disabled true}]

   ;; ;;; Draft?
   ;; [lui/checkbox
   ;;  {:label    (tr :lipas.user.permissions/draft?)
   ;;   :value    (-> data :permissions :draft?)
   ;;   :disabled true}]

   ;; ;;; Sports-sites
   ;; [lui/text-field
   ;;  {:label    (tr :lipas.user.permissions/sports-sites)
   ;;   :value    (pr-str (-> data :permissions :sports-sites))
   ;;   :disabled true}]

   ;; ;;; Types
   ;; [lui/text-field
   ;;  {:label    (tr :lipas.user.permissions/types)
   ;;   :value    (get-in data [:permissions :types] "-")
   ;;   :disabled true}]

   ;; ;;; Cities
   ;; [lui/text-field
   ;;  {:label    (tr :lipas.user.permissions/cities)
   ;;   :value    (pr-str (-> data :permissions :cities))
   ;;   :disabled true}]

   ;; [lui/text-field
   ;;  {:label    (tr :lipas.user/permissions)
   ;;   :value    (pr-str (:permissions data))
   ;;   :disabled true}]

   ;; Permissions request
   [lui/text-field
    {:label    (tr :lipas.user/requested-permissions)
     :value    (-> data :user-data :permissions-request)
     :disabled true}]])

(defn actions-dialog [tr]
  (let [site     (<== [::subs/selected-sports-site])
        close    #(==> [::events/select-sports-site nil])
        lipas-id (:lipas-id site)]
    [mui/dialog {:open     (some? (seq site))
                 :on-close close}

     [mui/dialog-title
      (:name site)]

     [mui/dialog-content
      [mui/list

       ;; View basic info button
       [mui/list-item
        {:button   true
         :on-click (comp
                    close
                    #(==> [:lipas.ui.events/display lipas-id]))}
        [mui/list-item-icon
         [mui/icon "arrow_forward"]]
        (tr :lipas.user/view-basic-info)]

       ;; Report energy consumption button
       [mui/list-item
        {:button   true
         :on-click (comp
                    close
                    #(==> [:lipas.ui.events/report-energy-consumption lipas-id]))}
        [mui/list-item-icon
         [mui/icon "arrow_forward"]]
        (tr :lipas.user/report-energy-consumption)]]]

     [mui/dialog-actions
      [mui/button {:on-click close}
       (tr :actions/cancel)]]]))

(defn user-panel [tr user]
  (let [admin? (<== [::subs/admin?])
        sites  (<== [::subs/sports-sites (tr)])

        card-props {:square true
                    :style  {:height "100%"}}

        firstname (-> user :user-data :firstname)
        lastname  (-> user :user-data :lastname)]

    [mui/grid {:container true
               :spacing   8
               :style     {:padding 8}}
     [mui/grid {:item true :xs 12 :md 6}

      ;; Profile card
      [mui/card card-props
       [mui/card-header {:title (tr :user/greeting firstname lastname)}]
       [mui/card-content
        [user-form tr user]]
       [mui/card-actions
        [mui/button {:href  "/#/"
                     :color :secondary}
         (str "> " (tr :user/front-page-link))]
        (when admin?
          [mui/button {:href  "/#/admin"
                       :color :primary}
           (str "> " (tr :user/admin-page-link))])]]]

     ;; Sports sites that user can access
     [mui/grid {:item true :xs 12 :md 6}

      [actions-dialog tr]

      [mui/card card-props
       [mui/card-header {:title (tr :lipas.user/sports-sites)}]
       [mui/card-content
        (cond

          admin? [lui/icon-text
                  {:icon "lock_open"
                   :text (tr :lipas.admin/access-all-sites)}]

          (empty? sites) [mui/grid {:container true}
                          [lui/icon-text
                           {:icon "lock"
                            :text (tr :lipas.user/no-permissions)}]
                          [lui/icon-text
                           {:icon       "info"
                            :icon-color :secondary
                            :text       (tr :lipas.user/draft-encouragement)}]]

          :else [lui/table
                 {:headers   [[:name (tr :lipas.sports-site/name-short)]
                              [:type (tr :lipas.sports-site/type)]
                              [:city (tr :lipas.location/city)]]
                  :items     sites
                  :on-select #(==> [::events/select-sports-site %])}])]]]]))

(defn main [tr]
  (let [logged-in? (<== [::subs/logged-in?])
        user       (<== [::subs/user-data])]
    (if logged-in?
      (do
        (==> [::events/get-users-sports-sites])
        [user-panel tr user])
      (navigate! "/#/kirjaudu"))))
