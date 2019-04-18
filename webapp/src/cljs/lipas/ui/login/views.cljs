(ns lipas.ui.login.views
  (:require
   [clojure.spec.alpha :as s]
   [lipas.ui.components :as lui]
   [lipas.ui.login.events :as events]
   [lipas.ui.login.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==> navigate!] :as utils]
   [reagent.core :as r]))

(defn set-field [& args]
  (==> [::events/set-field (butlast args) (last args)]))

(defn clear-errors []
  (==> [::events/clear-errors]))

(defn error-msg [{:keys [tr error]}]
  (let [error (or (-> error :response :error)
                  (-> error :response :type))]
    [mui/grid {:container true
               :spacing   8
               :style     {:margin-top "0.5em"}}

     ;; Error message
     [mui/grid {:item true :xs 12}
      [mui/typography {:color "error"}
       (case error
         "Not authorized"  (tr :login/bad-credentials)
         "user-not-found"  (tr :error/email-not-found)
         "email-not-found" (tr :error/email-not-found)
         (tr :error/unknown))]]

     ;; Register button
     (when (#{"user-not-found" "email-not-found"} error)
       [mui/grid {:item true :xs 12}
        [mui/button {:full-width true
                     :href       "/rekisteroidy"}
         (tr :register/headline)]])]))

(defn magic-link-form [{:keys [tr]}]
  (let [form-data     (<== [::subs/login-form])
        error         (<== [::subs/login-error])
        link-ordered? (<== [::subs/magic-link-ordered?])]

    [mui/grid {:container true :spacing 16}

     ;; Helper text
     [mui/grid
      {:item true :xs 12 :style {:padding-top "1em" :padding-bottom "1em"}}
      [mui/paper {:style {:background-color mui/gray3 :padding "1em"}}
       [mui/typography {:variant "body2"}
        (tr :login/magic-link-help)]]]

     ;; Form
     [mui/grid {:item true :xs 12}
      [mui/form-group

       ;; Email
       [lui/text-field
        {:id          "magic-link-login-email-input"
         :label       (tr :lipas.user/email)
         :spec        :lipas.user/email
         :auto-focus  true
         :value       (:email form-data)
         :on-change   (comp clear-errors #(set-field :email %))
         :required    true
         :placeholder (tr :lipas.user/email-example)}]

       ;; Login button
       [mui/button
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
        (tr :login/order-magic-link)]

       ;; Error messages
       (when error
         [error-msg {:tr tr :error error}])

       ;; Success messages
       (when link-ordered?
         [mui/typography {:style {:margin-top "1em"} :variant "body2"}
          (tr :login/magic-link-ordered)])]]]))

(defn login-form [{:keys [tr]}]
  (let [form-data (<== [::subs/login-form])
        error     (<== [::subs/login-error])]

    [mui/grid {:container true :spacing 16}

     ;; Helper text
     [mui/grid {:item true :xs 12 :style {:padding-top "1em" :padding-bottom "1em"}}
      [mui/paper {:style
                  {:background-color mui/gray3
                   :padding          "0.5em 1em 0.5em 1em"}}

       [mui/typography {:variant "body2" :style {:display "inline"}}
        (tr :login/login-help)]
       [mui/button {:style    {:padding 0 :margin-bottom "0.25em"}
                    :color    "secondary"
                    :on-click #(==> [::events/select-login-mode :magic-link])}
        (tr :login/login-here)]]]

     ;; Form
     [mui/grid {:item true :xs 12}
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
         :required     true}]

       ;; Login button
       [mui/button
        {:id         "login-submit-btn"
         :color      "secondary"
         :style      {:margin-top "1em"}
         :full-width true
         :size       "large"
         :variant    "contained"
         :on-click   #(==> [::events/submit-login-form form-data])}
        (tr :login/login)]

       ;; Error messages
       (when error
         [error-msg {:tr tr :error error}])

       ;; Forgot password
       [mui/button {:style {:margin-top "2em"}
                    :href  "/passu-hukassa"}
        (tr :login/forgot-password?)]

       ;; Register
       [mui/button {:href "/rekisteroidy"}
        (tr :register/headline)]]]]))

(defn register-btn [{:keys [tooltip]}]
  [mui/tooltip
   {:title tooltip}
   [mui/icon-button {:href "/rekisteroidy"}
    [mui/icon "group_add"]]])

(defn login-panel [{:keys [tr]}]
  (let [login-mode (<== [::subs/login-mode])
        card-props {:square true :style {:height "100%"}}]
    [mui/grid
     {:container true
      :justify   "center"
      :style     {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 8 :lg 6}
      [mui/card card-props
       [mui/card-header
        {:title  (tr :login/headline)
         :action (r/as-element
                  [register-btn
                   {:tooltip (tr :register/headline)}])}]
       [mui/card-content
        [mui/tabs {:value      login-mode
                   :on-change  #(==> [::events/select-login-mode (keyword %2)])
                   :style      {:margin-bottom "1em"}
                   :text-color "secondary"}
         [mui/tab {:label (tr :login/login-with-password) :value :password}]
         [mui/tab {:label (tr :login/login-with-magic-link) :value :magic-link}]]
        (if (= :magic-link login-mode)
          [magic-link-form {:tr tr}]
          [login-form {:tr tr}])]]]]))

(defn main [tr]
  (let [logged-in?    (<== [::subs/logged-in?])
        comeback-path (<== [::subs/comeback-path])
        token         (utils/parse-token (-> js/window .-location .-href))]
    (cond
      token      (==> [:lipas.ui.login.events/login-with-magic-link token])
      logged-in? (navigate! (or comeback-path "/profiili"))
      :else      [login-panel {:tr tr}])))
