(ns lipas.ui.forgot-password.views
  (:require [lipas.schema.users :as users-schema]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.text-fields :as text-fields]
            [malli.core :as m]
            [lipas.ui.forgot-password.events :as events]
            [lipas.ui.forgot-password.subs :as subs]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn request-reset-link-form [{:keys [tr]}]
  (r/with-let [email (r/atom nil)]
    [:> FormGroup

     ;; Email
     [text-fields/text-field
      {:label     (tr :lipas.user/email)
       :value     @email
       :spec      users-schema/email-schema
       :on-change #(reset! email %)}]

     ;; Submit
     [:> Button
      {:on-click #(==> [::events/request-password-reset @email])
       :variant  "contained"
       :color    "secondary"
       :style    {:margin-top "1em"}
       :disabled (not (m/validate users-schema/email-schema @email))}
      (tr :actions/submit)]]))

(defn panel [{:keys [tr title helper-text form form-props]}]
  (let [error   (<== [::subs/error])
        success (<== [::subs/success])]

    [:> Grid {:container true :justify-content "center" :style {:padding "1em"}}
     [:> Grid {:item true :xs 12 :md 8 :lg 6}
      [:> Card {:square true :style {:height "100%"}}

       ;; Header
       [:> CardHeader {:title title}]

       ;; Form
       [:> CardContent
        [:> Typography helper-text]
        [form form-props]

        ;;; Successess & errors box
        [:div {:style {:margin-top "1em"}}
         (when success
           [:> Typography
            {:variant "body1"
             :style   {:margin-bottom "1em" :font-weight "bold"}}
            (tr (keyword :reset-password success))])

         ;; Error message
         (when error
           [:> Typography {:color :error :style {:margin-bottom "1em"}}
            (tr (keyword :error error))])

         ;; Register button
         (when (= error :email-not-found)
           [buttons/register-button
            {:label (tr :register/headline)
             :href  "/rekisteroidy"}])

         ;; Forgot password button
         (when (= error :reset-token-expired)
           [:> Button
            {:color :primary
             :href  "/passu-hukassa"}
            (tr :reset-password/get-new-link)])]]]]]))

(defn reset-password-form [{:keys [tr token]}]
  (r/with-let [password (r/atom nil)]
    [:> FormGroup

     ;; Password
     [text-fields/text-field
      {:label     (tr :lipas.user/password)
       :type      :password
       :value     @password
       :spec      users-schema/password-schema
       :on-change #(reset! password %)}]

     ;; Submit
     [:> Button
      {:on-click #(==> [::events/reset-password @password token])
       :variant  "contained"
       :color    "secondary"
       :style    {:margin-top "1em"}
       :disabled (not (m/validate users-schema/password-schema @password))}
      (tr :actions/submit)]]))

(defn main []
  (let [tr    (<== [:lipas.ui.subs/translator])
        token (or (utils/parse-token (-> js/window .-location .-href))
                  (:token (<== [:lipas.ui.user.subs/user-data])))]
    (if token
      ;; Reset password
      [panel {:tr          tr
              :title       (tr :reset-password/enter-new-password)
              :helper-text (tr :reset-password/password-helper-text)
              :form        reset-password-form
              :form-props  {:tr tr :token token}}]
      ;; Request reset link
      [panel {:tr          tr
              :title       (tr :reset-password/headline)
              :helper-text (tr :reset-password/helper-text)
              :form        request-reset-link-form
              :form-props  {:tr tr :token token}}])))
