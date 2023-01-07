(ns lipas.ui.forgot-password.views
  (:require
   [clojure.spec.alpha :as s]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.forgot-password.events :as events]
   [lipas.ui.forgot-password.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn request-reset-link-form [{:keys [tr]}]
  (r/with-let [email (r/atom nil)]
    [mui/form-group

     ;; Email
     [lui/text-field
      {:label     (tr :lipas.user/email)
       :value     @email
       :spec      :lipas.user/email
       :on-change #(reset! email %)}]

     ;; Submit
     [mui/button
      {:on-click #(==> [::events/request-password-reset @email])
       :variant  "contained"
       :color    "secondary"
       :style    {:margin-top "1em"}
       :disabled (not (s/valid? :lipas.user/email @email))}
      (tr :actions/submit)]]))

(defn panel [{:keys [tr title helper-text form form-props]}]
  (let [error   (<== [::subs/error])
        success (<== [::subs/success])]

    [mui/grid {:container true :justify "center" :style {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 8 :lg 6}
      [mui/card {:square true :style {:height "100%"}}

       ;; Header
       [mui/card-header {:title title}]

       ;; Form
       [mui/card-content
        [mui/typography helper-text]
        [form form-props]

        ;;; Successess & errors box
        [:div {:style {:margin-top "1em"}}
         (when success
           [mui/typography
            {:variant "body1"
             :style   {:margin-bottom "1em" :font-weight "bold"}}
            (tr (keyword :reset-password success))])

         ;; Error message
         (when error
           [mui/typography {:color :error :style {:margin-bottom "1em"}}
            (tr (keyword :error error))])

         ;; Register button
         (when (= error :email-not-found)
           [lui/register-button
            {:label (tr :register/headline)
             :href  "/rekisteroidy"}])

         ;; Forgot password button
         (when (= error :reset-token-expired)
           [mui/button
            {:color :primary
             :href  "/passu-hukassa"}
            (tr :reset-password/get-new-link)])]]]]]))

(defn reset-password-form [{:keys [tr token]}]
  (r/with-let [password (r/atom nil)]
    [mui/form-group

     ;; Password
     [lui/text-field
      {:label     (tr :lipas.user/password)
       :type      :password
       :value     @password
       :spec      :lipas.user/password
       :on-change #(reset! password %)}]

     ;; Submit
     [mui/button
      {:on-click #(==> [::events/reset-password @password token])
       :variant  "contained"
       :color    "secondary"
       :style    {:margin-top "1em"}
       :disabled (not (s/valid? :lipas.user/password @password))}
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
