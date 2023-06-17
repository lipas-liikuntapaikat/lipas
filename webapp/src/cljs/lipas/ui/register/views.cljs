(ns lipas.ui.register.views
  (:require
   [clojure.spec.alpha :as s]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.register.events :as events]
   [lipas.ui.register.subs :as subs]
   [lipas.ui.utils :refer [<== ==> navigate!]]))

(defn set-field [& args]
  (==> [::events/set-registration-form-field (butlast args) (last args)]))

(defn registration-form [{:keys [tr]}]
  (let [form-data (<== [::subs/registration-form])
        error     (<== [::subs/registration-error])]

    [mui/form-group

     ;; Email
     [lui/text-field
      {:required    true
       :label       (tr :lipas.user/email)
       :type        "email"
       :spec        :lipas.user/email
       :value       (:email form-data)
       :on-change   #(==> [::events/set-registration-form-email %])
       :placeholder (tr :lipas.user/email-example)}]

     ;; Username
     [lui/text-field
      {:required          true
       :Input-label-props (when-not (-> form-data :username empty?)
                            {:shrink true})
       :label             (tr :lipas.user/username)
       :type              "text"
       :spec              :lipas.user/username
       :value             (:username form-data)
       :on-change         #(set-field :username %)
       :placeholder       (tr :lipas.user/username-example)}]

     ;; Password
     [lui/text-field
      {:required  true
       :label     (tr :lipas.user/password)
       :type      "password"
       :spec      :lipas.user/password
       :value     (:password form-data)
       :on-change #(set-field :password %)}]

     ;; Firstname
     [lui/text-field
      {:required  true
       :label     (tr :lipas.user/firstname)
       :spec      :lipas.user/firstname
       :value     (-> form-data :user-data :firstname)
       :on-change #(set-field :user-data :firstname %)}]

     ;; Lastname
     [lui/text-field
      {:required  true
       :label     (tr :lipas.user/lastname)
       :spec      :lipas.user/lastname
       :value     (-> form-data :user-data :lastname)
       :on-change #(set-field :user-data :lastname %)}]

     ;; Permissions request
     [lui/text-field
      {:label       (tr :lipas.user/permissions)
       :multiline   true
       :spec        :lipas.user/permissions-request
       :value       (-> form-data :user-data :permissions-request)
       :on-change   #(set-field :user-data :permissions-request %)
       :min-rows    3
       :placeholder (tr :lipas.user/permissions-example)
       :helper-text (tr :lipas.user/permissions-help)}]

     ;; Register button
     [mui/button
      {:style    {:margin-top "1em"}
       :color    "secondary"
       :variant  "contained"
       :disabled (not (s/valid? :lipas/new-user form-data))
       :size     "large"
       :on-click #(==> [::events/submit-registration-form form-data])}
      (tr :register/headline)]

     ;; Error messages
     (when error
       [mui/typography {:color "error"}
        (case (-> error :response :type)
          "email-conflict"    (tr :error/email-conflict)
          "username-conflict" (tr :error/username-conflict)
          (tr :error/unknown))])]))

(defn thank-you-for-registering-box [{:keys [tr]}]
  [mui/grid
   {:item true :xs 12 :style {:padding-top "1em" :padding-bottom "1em"}}
   [mui/paper {:style {:background-color mui/gray3 :padding "1em"}}
    [mui/typography {:variant "body2"}
     (tr :register/thank-you-for-registering)]]])

(defn create-panel [tr]
  (let [registered? (<== [::subs/registration-success?])]
    [mui/grid {:container true :justify-content "center" :style {:padding "1em"}}
     [mui/grid {:item true :xs 12 :md 8 :lg 6}
      [mui/card {:square true :style {:height "100%"}}
       [mui/card-header {:title (tr :register/headline)}]
       [mui/card-content
        (if registered?
          [thank-you-for-registering-box {:tr tr}]
          [registration-form {:tr tr}])]]]]))

(defn main []
  (let [tr         (<== [:lipas.ui.subs/translator])
        logged-in? (<== [::subs/logged-in?])]
    (if logged-in?
      (navigate! "/profiili")
      [create-panel tr])))
