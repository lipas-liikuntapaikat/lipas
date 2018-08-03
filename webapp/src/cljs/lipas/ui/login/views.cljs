(ns lipas.ui.login.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.login.events :as events]
            [lipas.ui.login.subs :as subs]
            [lipas.ui.mui :as mui]
            [reagent.core :as r]
            [lipas.ui.routes :refer [navigate!]]
            [lipas.ui.utils :refer [<== ==>]]))

(defn set-field [& args]
  (==> [::events/set-field (butlast args) (last args)]))

(defn clear-errors []
  (==> [::events/clear-errors]))

(defn login-form [{:keys [tr]}]
  (let [form-data (<== [::subs/login-form])
        error     (<== [::subs/login-error])]

    [mui/form-group

     ;; Username
     [lui/text-field
      {:id          "login-username-input"
       :label       (tr :login/username)
       :auto-focus  true
       :value       (:username form-data)
       :on-change   (comp clear-errors #(set-field :username %))
       :required    true
       :placeholder (tr :login/username-example)}]

     ;; Password
     [lui/text-field
      {:id        "login-password-input"
       :label     (tr :login/password)
       :type      "password"
       :value     (:password form-data)
       :on-change (comp clear-errors #(set-field :password %))
       :required  true}]

     ;; Login button
     [mui/button
      {:id         "login-submit-btn"
       :color      "secondary"
       :style      {:margin-top "1em"}
       :full-width true
       :size       "large"
       :variant    "raised"
       :on-click   #(==> [::events/submit-login-form form-data])}
      (tr :login/login)]

     ;; Error messages
     (when error
       [mui/typography {:color "error"}
        (case (-> error :response :error)
          "Not authorized" (tr :login/bad-credentials)
          (tr :error/unknown))])

     [mui/button {:style    {:margin-top "2em"}
                  :href     "#/passu-hukassa"}
      (tr :login/forgot-password?)]]))

(defn register-btn [{:keys [tooltip]}]
  [mui/tooltip
   {:title tooltip}
   [mui/icon-button {:href "/#/rekisteroidy"}
    [mui/icon "group_add"]]])

(defn login-panel [{:keys [tr]}]
  (let [card-props {:square true
                    :style {:height "100%"}}]
    [mui/grid {:container true
               :justify "center"
               :style {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 8 :lg 6}
      [mui/card card-props
       [mui/card-header {:title (tr :login/headline)
                         :action (r/as-element
                                  [register-btn
                                   {:tooltip (tr :register/headline)}])}]
       [mui/card-content
        [login-form {:tr tr}]]]]]))

(defn main [tr]
  (let [logged-in? (<== [::subs/logged-in?])]
    (if logged-in?
      (navigate! "/#/profiili")
      [login-panel {:tr tr}])))
