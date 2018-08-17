(ns lipas.ui.user.reset-password
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [cemerick.url :as url]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.user.events :as events]
            [lipas.ui.user.subs :as subs]
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
     [mui/button {:on-click #(==> [::events/send-reset-password-request @email])
                  :variant  "raised"
                  :color    "secondary"
                  :style    {:margin-top "1em"}
                  :disabled (not (s/valid? :lipas.user/email @email))}
      (tr :actions/submit)]]))

(defn panel [{:keys [tr title helper-text form form-props]}]
  (let [error   (<== [::subs/reset-password-request-error])
        success (<== [::subs/reset-password-request-success])]
    [mui/grid {:container true
               :justify   "center"
               :style     {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 8 :lg 6}
      [mui/card {:square true
                 :style  {:height "100%"}}
       [mui/card-header {:title title}]
       [mui/card-content
        [mui/typography helper-text]
        [form form-props]
        [:div {:style {:margin-top "1em"}}
         (when success
           [mui/typography {:style   {:margin-bottom "1em"
                                      :font-weight   :bold}
                            :variant :body1}
            (tr (keyword :reset-password success))])
         (when error
           [mui/typography {:color :error
                            :style {:margin-bottom "1em"}}
            (tr (keyword :error error))])
         (when (= error :email-not-found)
           [lui/register-button {:label (tr :register/headline)
                                 :href  "/#/rekisteroidy"}])
         (when (= error :reset-token-expired)
           [mui/button {:color :primary
                        :href  "/#/passu-hukassa"}
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
     [mui/button {:on-click #(==> [::events/reset-password @password token])
                  :variant  "raised"
                  :color    "secondary"
                  :style    {:margin-top "1em"}
                  :disabled (not (s/valid? :lipas.user/password @password))}
      (tr :actions/submit)]]))

(defn parse-token [s]
  (-> s
      url/url
      :anchor
      (string/split "?token=")
      second))

(defn main []
  (==> [::events/clear-feedback])
  (let [tr    (<== [:lipas.ui.subs/translator])
        token (parse-token (-> js/window .-location .-href))]
    (if token
      ;; Reset password
      [panel {:tr         tr
              :title       (tr :reset-password/enter-new-password)
              :helper-text (tr :reset-password/password-helper-text)
              :form reset-password-form
              :form-props {:tr tr :token token}}]
      ;; Request reset link
      [panel {:tr          tr
              :title       (tr :reset-password/headline)
              :helper-text (tr :reset-password/helper-text)
              :form request-reset-link-form
              :form-props  {:tr tr :token token}}]
)))
