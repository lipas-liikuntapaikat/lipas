(ns lipas.ui.register.views
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.schema.users :as users]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.register.events :as events]
            [lipas.ui.register.subs :as subs]
            [lipas.ui.utils :refer [==> navigate!]]
            [malli.core :as m]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn set-field [& args]
  (==> [::events/set-registration-form-field (butlast args) (last args)]))

(defn- notice [text]
  [:> Paper {:style {:background-color mui/gray3 :padding "1em"}}
   [:> Typography {:variant "body2"} text]])

(defn- privacy-policy-link [tr]
  [:> Link
   {:style {:margin-top "0.5em"}
    :href "https://lipas.fi/pdf/tietosuojailmoitus_lipas.pdf"
    :target "_blank"}
   (tr :help/privacy-policy)])

;;; Step 1: ask for a link ;;;

(r/defc request-link-form [{:keys [tr]}]
  (let [{:keys [email in-progress?]} @(rf/subscribe [::subs/registration-request])
        error @(rf/subscribe [::subs/registration-error])
        valid? (m/validate users/email-schema (or email ""))]
    [:> FormGroup {}
     [:> Typography {:variant "body1" :sx {:mb 1}}
      (tr :register/email-step-intro)]

     [text-fields/text-field
      {:required true
       :label (tr :lipas.user/email)
       :type "email"
       :spec users/email-schema
       :value email
       :on-change #(==> [::events/set-request-email %])
       :placeholder (tr :lipas.user/email-example)}]

     [:> Button
      {:style {:margin-top "1em"}
       :color "secondary"
       :variant "contained"
       :disabled (or (not valid?) in-progress?)
       :size "large"
       :on-click #(==> [::events/submit-registration-request email])}
      (tr :register/send-link)]

     [:> Stack {:spacing 1}
      [privacy-policy-link tr]]

     (when error
       [:> Typography {:color "error"} (tr :error/unknown)])]))

(r/defc link-sent-box [{:keys [tr email]}]
  [:> Stack {:spacing 2}
   [notice (tr :register/link-sent email)]
   [:> Button {:variant "text"
               :sx {:align-self "flex-start"}
               :on-click #(==> [::events/edit-request-email])}
    (tr :register/send-again)]])

;;; Step 2: complete the registration ;;;

(r/defc registration-form [{:keys [tr email]}]
  (let [form-data @(rf/subscribe [::subs/registration-form])
        error @(rf/subscribe [::subs/registration-error])]

    [:> FormGroup
     {}

     [:> Typography {:variant "body1" :sx {:mb 1}}
      (tr :register/complete-intro)]

     ;; Email — from the verified link, not editable
     [text-fields/text-field
      {:label (tr :lipas.user/email)
       :type "email"
       :value email
       :disabled true}]

     ;; Username
     [text-fields/text-field
      {:required true
       :Input-label-props (when-not (-> form-data :username empty?)
                            {:shrink true})
       :label (tr :lipas.user/username)
       :type "text"
       :spec users/registration-username-schema
       :value (:username form-data)
       :on-change #(set-field :username %)
       :placeholder (tr :lipas.user/username-example)}]

     ;; Password
     [text-fields/text-field
      {:required true
       :label (tr :lipas.user/password)
       :type "password"
       :spec users/password-schema
       :value (:password form-data)
       :on-change #(set-field :password %)}]

     ;; Firstname
     [text-fields/text-field
      {:required true
       :label (tr :lipas.user/firstname)
       :spec users/firstname-schema
       :value (-> form-data :user-data :firstname)
       :on-change #(set-field :user-data :firstname %)}]

     ;; Lastname
     [text-fields/text-field
      {:required true
       :label (tr :lipas.user/lastname)
       :spec users/lastname-schema
       :value (-> form-data :user-data :lastname)
       :on-change #(set-field :user-data :lastname %)}]

     ;; Permissions request
     [text-fields/text-field
      {:label (tr :lipas.user/permissions)
       :multiline true
       :spec users/permissions-request-schema
       :value (-> form-data :user-data :permissions-request)
       :on-change #(set-field :user-data :permissions-request %)
       :rows 3
       :placeholder (tr :lipas.user/permissions-example)
       :helper-text (tr :lipas.user/permissions-help)}]

     ;; Register button
     [:> Button
      {:style {:margin-top "1em"}
       :color "secondary"
       :variant "contained"
       :disabled (not (m/validate users/registration-form-schema form-data))
       :size "large"
       :on-click #(==> [::events/submit-registration-form form-data])}
      (tr :register/headline)]

     [:> Stack {:spacing 1}

      [privacy-policy-link tr]

      ;; Terms
      [:> Typography {:variant "body1" :sx {:mt 1 :mb 1}}
       (tr :user/data-ownership)]

      [:> Typography {:variant "body1" :style {:font-size "0.9em"}}
       (tr :disclaimer/data-ownership)]]

     ;; Error messages
     (when error
       [:> Typography {:color "error"}
        (case (-> error :response :type)
          "email-conflict" (tr :error/email-conflict)
          "username-conflict" (tr :error/username-conflict)
          (tr :error/unknown))])]))

(r/defc link-invalid-box [{:keys [tr]}]
  [:> Stack {:spacing 2}
   [notice (tr :register/link-invalid)]
   [:> Button {:variant "contained"
               :color "secondary"
               :sx {:align-self "flex-start"}
               :on-click #(==> [::events/start-over])}
    (tr :register/request-new-link)]])

(r/defc thank-you-for-registering-box [{:keys [tr]}]
  [:> Stack {:spacing 2}
   [notice (tr :register/thank-you-for-registering)]
   [:> Button {:variant "contained"
               :color "secondary"
               :sx {:align-self "flex-start"}
               :on-click #(navigate! "/kirjaudu")}
    (tr :register/go-to-login)]])

(r/defc create-panel [{:keys [tr]}]
  (let [registered? @(rf/subscribe [::subs/registration-success?])
        token @(rf/subscribe [::subs/registration-token])
        request @(rf/subscribe [::subs/registration-request])]
    [:> Grid {:container true :justify-content "center" :style {:padding "1em"}}
     [:> Grid {:item true :xs 12 :md 8 :lg 6}
      [:> Card {:square true :style {:height "100%"}}
       [:> CardHeader {:title (if token
                                (tr :register/complete-headline)
                                (tr :register/headline))}]
       [:> CardContent
        {}
        (cond
          registered?
          [thank-you-for-registering-box {:tr tr}]

          (or (:invalid? token) (:expired? token))
          [link-invalid-box {:tr tr}]

          token
          [registration-form {:tr tr :email (:email token)}]

          (:sent-to request)
          [link-sent-box {:tr tr :email (:sent-to request)}]

          :else
          [request-link-form {:tr tr}])]]]]))

(r/defc main []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        logged-in? @(rf/subscribe [::subs/logged-in?])]
    (if logged-in?
      (do (navigate! "/profiili") nil)
      [create-panel {:tr tr}])))
