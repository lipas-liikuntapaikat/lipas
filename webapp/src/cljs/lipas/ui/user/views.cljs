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
         [lui/text-field
          {:label    (tr :register/username)
           :value    (:username data)
           :disabled true}]
         [lui/text-field
          {:label    (tr :register/firstname)
           :value    firstname
           :disabled true}]
         [lui/text-field
          {:label    (tr :register/lastname)
           :value    lastname
           :disabled true}]
         [lui/text-field
          {:label    (tr :register/permissions)
           :value    (pr-str (:permissions data))
           :disabled true}]
         [lui/text-field
          {:label    (tr :user/requested-permissions)
           :value    (-> data :user-data :permissions-request)
           :disabled true}]]]]]]))

(defn main [tr]
  (let [logged-in? (re-frame/subscribe [::subs/logged-in?])
        user-data  (re-frame/subscribe [::subs/user-data])]
    (if @logged-in?
      [user-panel tr @user-data]
      (navigate! "/#/kirjaudu"))))
