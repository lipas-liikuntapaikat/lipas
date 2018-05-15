(ns lipas-ui.user.views
  (:require [lipas-ui.mui :as mui]
            [cljs.pprint :as pp]
            ;; [lipas-ui.user.events :as events]
            [lipas-ui.user.subs :as subs]
            [lipas-ui.routes :refer [navigate!]]
            [re-frame.core :as re-frame]))

(defn create-user-panel [tr data]
  (let [card-props {:square true
                    :style {:height "100%"}}
        firstname (-> data :user-data :firstname)
        lastname (-> data :user-data :lastname)]
    [mui/grid {:container true
               :justify "center"
               :style {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 6}
      [mui/card card-props
       [mui/card-header {:title (str "Hei " firstname " " lastname "!")}]
       [mui/card-content
        [mui/form-group
         [mui/text-field {:label (tr :register/username)
                          :value (:username data)}]
         [mui/text-field {:label (tr :register/firstname)
                          :value firstname}]
         [mui/text-field {:label (tr :register/lastname)
                          :value lastname}]
         [mui/text-field {:label (tr :register/permissions)
                          :value (:permissions data)}]
         [mui/text-field {:label (tr :user/requested-permissions)
                          :value (-> data :user-data :permissions-request)}]]
        ;;[:pre (with-out-str (pp/pprint data))]
        ]]]]))

(defn main [tr]
  (let [logged-in? (re-frame/subscribe [::subs/logged-in?])
        user-data (re-frame/subscribe [::subs/user-data])]
    (if @logged-in?
      (create-user-panel tr @user-data)
      (navigate! "/#/kirjaudu"))))
