(ns lipas.ui.user.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.components :as lui]
            [lipas.ui.user.subs :as subs]
            [lipas.ui.routes :refer [navigate!]]
            [re-frame.core :as re-frame]))

(defn user-panel [tr data]
  (let [card-props {:square true
                    :style  {:height "100%"}}
        firstname  (-> data :user-data :firstname)
        lastname   (-> data :user-data :lastname)]
    [mui/grid {:container true
               :justify   "center"
               :style     {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 6}
      [mui/card card-props
       [mui/card-header {:title (tr :user/greeting firstname lastname)}]
       [mui/card-content
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
           :value    firstname
           :disabled true}]

         ;; Lastname
         [lui/text-field
          {:label    (tr :lipas.user/lastname)
           :value    lastname
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
           :disabled true}]]]

       [mui/card-actions
        [mui/button {:href  "/#/"
                     :color :secondary}
         (str "> " (tr :user/front-page-link))]]]]]))

(defn main [tr]
  (let [logged-in? (re-frame/subscribe [::subs/logged-in?])
        user-data  (re-frame/subscribe [::subs/user-data])]
    (if @logged-in?
      [user-panel tr @user-data]
      (navigate! "/#/kirjaudu"))))
