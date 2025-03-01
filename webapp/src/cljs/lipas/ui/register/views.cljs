(ns lipas.ui.register.views
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.spec.alpha :as s]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.register.events :as events]
            [lipas.ui.register.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.utils :refer [==> navigate!]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

(defn set-field [& args]
  (==> [::events/set-registration-form-field (butlast args) (last args)]))

(defui registration-form [{:keys [tr]}]
  (let [form-data (use-subscribe [::subs/registration-form])
        error     (use-subscribe [::subs/registration-error])]

    ($ FormGroup
       {}

       ;; Email
       (r/as-element
        [lui/text-field
         {:required    true
          :label       (tr :lipas.user/email)
          :type        "email"
          :spec        :lipas.user/email
          :value       (:email form-data)
          :on-change   #(==> [::events/set-registration-form-email %])
          :placeholder (tr :lipas.user/email-example)}])

       ;; Username
       (r/as-element
        [lui/text-field
         {:required          true
          :Input-label-props (when-not (-> form-data :username empty?)
                               {:shrink true})
          :label             (tr :lipas.user/username)
          :type              "text"
          :spec              :lipas.user/username
          :value             (:username form-data)
          :on-change         #(set-field :username %)
          :placeholder       (tr :lipas.user/username-example)}])

       ;; Password
       (r/as-element
        [lui/text-field
         {:required  true
          :label     (tr :lipas.user/password)
          :type      "password"
          :spec      :lipas.user/password
          :value     (:password form-data)
          :on-change #(set-field :password %)}])

       ;; Firstname
       (r/as-element
        [lui/text-field
         {:required  true
          :label     (tr :lipas.user/firstname)
          :spec      :lipas.user/firstname
          :value     (-> form-data :user-data :firstname)
          :on-change #(set-field :user-data :firstname %)}])

       ;; Lastname
       (r/as-element
        [lui/text-field
         {:required  true
          :label     (tr :lipas.user/lastname)
          :spec      :lipas.user/lastname
          :value     (-> form-data :user-data :lastname)
          :on-change #(set-field :user-data :lastname %)}])

       ;; Permissions request
       (r/as-element
        [lui/text-field
         {:label       (tr :lipas.user/permissions)
          :multiline   true
          :spec        :lipas.user/permissions-request
          :value       (-> form-data :user-data :permissions-request)
          :on-change   #(set-field :user-data :permissions-request %)
          :min-rows    3
          :placeholder (tr :lipas.user/permissions-example)
          :helper-text (tr :lipas.user/permissions-help)}])

       ;; Register button
       ($ Button
          {:style    {:margin-top "1em"}
           :color    "secondary"
           :variant  "contained"
           :disabled (not (s/valid? :lipas/new-user form-data))
           :size     "large"
           :on-click #(==> [::events/submit-registration-form form-data])}
          (tr :register/headline))

       ;; Privacy policy
       ($ Link
          {:style  {:margin-top "0.5em"}
           :href   "https://lipas.fi/pdf/tietosuojailmoitus_lipas.pdf"
           :target "_blank"}
          (tr :help/privacy-policy))

       ;; Terms
       ($ Typography {:variant "body1" :sx {:mt 1 :mb 1}}
          (tr :user/data-ownership))

       ($ Typography {:variant "body1" :style {:font-size "0.9em"}}
          (tr :disclaimer/data-ownership))

       ;; Error messages
       (when error
         ($ Typography {:color "error"}
            (case (-> error :response :type)
              "email-conflict"    (tr :error/email-conflict)
              "username-conflict" (tr :error/username-conflict)
              (tr :error/unknown)))))))

(defui thank-you-for-registering-box [{:keys [tr]}]
  ($ Grid
     {:item true :xs 12 :style {:padding-top "1em" :padding-bottom "1em"}}
     ($ Paper {:style {:background-color mui/gray3 :padding "1em"}}
        ($ Typography {:variant "body2"}
           (tr :register/thank-you-for-registering)))))

(defui create-panel [{:keys [tr]}]
  (let [registered? (use-subscribe [::subs/registration-success?])]
    ($ Grid {:container true :justify-content "center" :style {:padding "1em"}}
       ($ Grid {:item true :xs 12 :md 8 :lg 6}
          ($ Card {:square true :style {:height "100%"}}
             ($ CardHeader {:title (tr :register/headline)})
             ($ CardContent
                {}
                (if registered?
                  ($ thank-you-for-registering-box {:tr tr})
                  ($ registration-form {:tr tr}))))))))

(defui main []
  (let [tr         (use-subscribe [:lipas.ui.subs/translator])
        logged-in? (use-subscribe [::subs/logged-in?])]
    (if logged-in?
      (do (navigate! "/profiili") nil)
      ($ create-panel {:tr tr}))))