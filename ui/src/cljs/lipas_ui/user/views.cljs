(ns lipas-ui.user.views
  (:require [lipas-ui.mui :as mui]
            ;; [lipas-ui.user.events :as events]
            [lipas-ui.user.subs :as subs]
            [lipas-ui.routes :refer [navigate!]]
            [re-frame.core :as re-frame]))

(defn create-user-panel [tr user-data]
  (let [card-props {:square true
                    :style {:height "100%"}}]
    [mui/grid {:container true
               :justify "center"
               :style {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 6}
      [mui/card card-props
       [mui/card-header {:title (tr :user/headline)}]
       [mui/card-content
        [mui/typography (tr :user/description)]
        [:pre (str user-data)]]]]]))

(defn main [tr]
  (let [logged-in? (re-frame/subscribe [::subs/logged-in?])
        user-data (re-frame/subscribe [::subs/user-data])]
    (if @logged-in?
      (create-user-panel tr @user-data)
      (navigate! "/#/kirjaudu"))))
