(ns lipas-ui.login.views
  (:require [lipas-ui.mui :as mui]
            [lipas-ui.login.events :as events]
            [lipas-ui.login.subs :as subs]
            [lipas-ui.routes :refer [navigate!]]
            [re-frame.core :as re-frame]))

(defn set-form-field [path event]
  (let [path (into [] (flatten [path]))
        value (-> event
                  .-target
                  .-value)]
    (re-frame/dispatch [::events/set-login-form-field path value])))

(defn create-login-form [tr form-data]
  [mui/form-group
   [mui/text-field {:label (tr :login/username)
                    :auto-focus true
                    :type "text"
                    :value (:username form-data)
                    :on-change #(set-form-field :username %)
                    :required true
                    :placeholder (tr :login/username-example)}]
   [mui/text-field {:label (tr :login/password)
                    :type "password"
                    :value (:password form-data)
                    :on-change #(set-form-field :password %)
                    :required true}]
   [mui/button {:color "primary"
                :size "large"
                :on-click #(re-frame/dispatch
                            [::events/submit-login-form form-data])}
    (tr :login/login)]])

(defn create-login-panel [tr form-data]
  (let [card-props {:square true
                    :style {:height "100%"}}]
    [mui/grid {:container true
               :justify "center"
               :style {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 8 :lg 6}
      [mui/card card-props
       [mui/card-header {:title (tr :login/headline)}]
       [mui/card-content
        (create-login-form tr form-data)]]]]))

(defn main [tr]
  (let [logged-in? (re-frame/subscribe [::subs/logged-in?])
        form-data (re-frame/subscribe [::subs/login-form])]
    (if @logged-in?
      (navigate! "/#/profiili")
      (create-login-panel tr @form-data))))
