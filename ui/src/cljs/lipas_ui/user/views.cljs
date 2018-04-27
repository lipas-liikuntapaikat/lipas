(ns lipas-ui.user.views
  (:require [lipas-ui.i18n :as i18n]
            [lipas-ui.mui :as mui]
            ;; [lipas-ui.user.events :as events]
            [lipas-ui.user.subs :as subs]
            [lipas-ui.subs :as global-subs]
            [lipas-ui.routes :refer [navigate!]]
            [re-frame.core :as re-frame]))

(defn create-user-panel [tr]
  (let [card-props {:square true
                    :style {:height "100%"}}]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} (tr :user/headline)]
        [mui/typography (tr :help/description)]]]]]))

(defn main []
  (let [tr (i18n/->tr-fn @(re-frame/subscribe [::global-subs/locale]))
        logged-in? (re-frame/subscribe [::subs/logged-in?])]
    (if @logged-in?
      (create-user-panel tr)
      (navigate! "/#/login"))))
