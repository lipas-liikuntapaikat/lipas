(ns lipas.ui.login.views
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.spec.alpha :as s]
            [lipas.ui.components :as lui]
            [lipas.ui.login.events :as events]
            [lipas.ui.login.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.utils :refer [==>] :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

(defn set-field [& args]
  (==> [::events/set-field (butlast args) (last args)]))

(defn clear-errors []
  (==> [::events/clear-errors]))

(defui error-msg [{:keys [tr error]}]
  (let [error (or (-> error :response :error)
                 (-> error :response :type))]
    ($ Grid {:container true
             :spacing   1
             :style     {:margin-top "0.5em"}}

       ;; Error message
       ($ Grid {:item true :xs 12}
          ($ Typography {:color "error"}
             (case error
               "Not authorized"  (tr :login/bad-credentials)
               "user-not-found"  (tr :error/email-not-found)
               "email-not-found" (tr :error/email-not-found)
               (tr :error/unknown))))

       ;; Register button
       (when (#{"user-not-found" "email-not-found"} error)
         ($ Grid {:item true :xs 12}
            ($ Button {:full-width true
                       :href       "/rekisteroidy"}
               (tr :register/headline)))))))

(defui magic-link-form [{:keys [tr]}]
  (let [form-data     (use-subscribe [::subs/login-form])
        error         (use-subscribe [::subs/login-error])
        link-ordered? (use-subscribe [::subs/magic-link-ordered?])]

    ($ Grid {:container true :spacing 2}

       ;; Helper text
       ($ Grid
          {:item true :xs 12 :style {:padding-top "1em" :padding-bottom "1em"}}
          ($ Paper {:style {:background-color mui/gray3 :padding "1em"}}
             ($ Typography {:variant "body2"}
                (tr :login/magic-link-help))))

       ;; Form
       ($ Grid {:item true :xs 12}
          ($ FormGroup
             {}

             ;; Email
             (r/as-element
              [lui/text-field
               {:id          "magic-link-login-email-input"
                :label       (tr :lipas.user/email)
                :spec        :lipas.user/email
                :auto-focus  true
                :value       (:email form-data)
                :on-change   (comp clear-errors #(set-field :email %))
                :required    true
                :placeholder (tr :lipas.user/email-example)}])

             ;; Login button
             ($ Button
                {:id         "magic-link-submit-btn"
                 :color      "secondary"
                 :disabled   (or link-ordered?
                                 (some? error)
                                 (not (s/valid? :lipas.user/email (:email form-data))))
                 :style      {:margin-top "1em"}
                 :full-width true
                 :size       "large"
                 :variant    "contained"
                 :on-click   #(==> [::events/order-magic-link form-data])}
                (tr :login/order-magic-link))

             ;; Error messages
             (when error
               ($ error-msg {:tr tr :error error}))

             ;; Success messages
             (when link-ordered?
               ($ Typography {:style {:margin-top "1em"} :variant "body2"}
                  (tr :login/magic-link-ordered))))))))

(defui login-form [{:keys [tr]}]
  (let [form-data (use-subscribe [::subs/login-form])
        error     (use-subscribe [::subs/login-error])]

    ($ Grid {:container true :spacing 2}

       ;; Helper text
       ($ Grid {:item true :xs 12 :style {:padding-top "1em" :padding-bottom "1em"}}
          ($ Paper {:style
                    {:background-color mui/gray3
                     :padding          "0.5em 1em 0.5em 1em"}}

             ($ Typography {:variant "body2" :style {:display "inline"}}
                (tr :login/login-help))
             ($ Button {:style    {:padding 0 :margin-bottom "0.25em"}
                        :color    "secondary"
                        :on-click #(==> [::events/select-login-mode :magic-link])}
                (tr :login/login-here))))

       ;; Form
       ($ Grid {:item true :xs 12}
          ($ FormGroup
             {}

             ;; Username
             (r/as-element
              [lui/text-field
               {:id          "login-username-input"
                :label       (tr :login/username)
                :auto-focus  true
                :value       (:username form-data)
                :on-change   (comp clear-errors #(set-field :username %))
                :required    true
                :placeholder (tr :login/username-example)}])

             ;; Password
             (r/as-element
              [lui/text-field
               {:id           "login-password-input"
                :label        (tr :login/password)
                :type         "password"
                :value        (:password form-data)
                ;; Enter press might occur 'immediately' so we can't afford
                ;; waiting default 200ms for text-field to update
                ;; asynchronously.
                :defer-ms     0
                :on-change    (comp clear-errors #(set-field :password %))
                :on-key-press (fn [e]
                                (when (= 13 (.-charCode e)) ; Enter
                                  (==> [::events/submit-login-form form-data])))
                :required     true}])

             ;; Login button
             ($ Button
                {:id         "login-submit-btn"
                 :color      "secondary"
                 :style      {:margin-top "1em"}
                 :full-width true
                 :size       "large"
                 :variant    "contained"
                 :on-click   #(==> [::events/submit-login-form form-data])}
                (tr :login/login))

             ;; Error messages
             (when error
               ($ error-msg {:tr tr :error error}))

             ;; Forgot password
             ($ Button {:style {:margin-top "2em"}
                        :href  "/passu-hukassa"}
                (tr :login/forgot-password?))

             ;; Register
             ($ Button {:href "/rekisteroidy"}
                (tr :register/headline)))))))

(defui register-btn [{:keys [tooltip]}]
  ($ Tooltip
     {:title tooltip}
     ($ IconButton {:href "/rekisteroidy"}
        ($ Icon {} "group_add"))))

(defui login-panel [{:keys [tr]}]
  (let [login-mode (use-subscribe [::subs/login-mode])
        card-props {:square true :style {:height "100%"}}]
    ($ Grid
       {:container       true
        :justify-content "center"
        :style           {:padding "1em"}}
       ($ Grid {:item true :xs 12 :md 8 :lg 6}
          ($ Card card-props
             ($ CardHeader
                {:title  (tr :login/headline)
                 :action (r/as-element
                          [register-btn
                           {:tooltip (tr :register/headline)}])})
             ($ CardContent
                {}
                ($ Tabs {:value      login-mode
                         :on-change  #(==> [::events/select-login-mode (keyword %2)])
                         :style      {:margin-bottom "1em"}
                         :indicator-color "secondary"
                         :text-color "secondary"}
                   ($ Tab {:label (tr :login/login-with-password) :value :password})
                   ($ Tab {:label (tr :login/login-with-magic-link) :value :magic-link}))
                (if (= :magic-link login-mode)
                  ($ magic-link-form {:tr tr})
                  ($ login-form {:tr tr}))))))))

(defui main []
  (let [tr            (use-subscribe [:lipas.ui.subs/translator])
        logged-in?    (use-subscribe [::subs/logged-in?])
        comeback-path (use-subscribe [::subs/comeback-path])
        token         (utils/parse-token (-> js/window .-location .-href))]
    (cond
      token      (do (rf/dispatch [:lipas.ui.login.events/login-with-magic-link token]) nil)
      logged-in? (do (utils/navigate! (or comeback-path "/profiili")) nil)
      :else      ($ login-panel {:tr tr}))))
