(ns lipas.ui.components.email-change
  "Dialog for requesting an email change. Shared by the profile page
  (self-service) and admin user management; only the events differ. The
  change itself happens when the link mailed to the new address is opened
  (lipas.ui.email-change.*)."
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.schema.users :as users-schema]
            [lipas.ui.components.text-fields :as text-fields]
            [malli.core :as m]
            [reagent.core :as r]))

(r/defc email-change-dialog
  "`state` is {:open? :new-email :in-progress? :sent-to :error}."
  [{:keys [tr current-email state help-text on-change on-submit on-close]}]
  (let [{:keys [open? new-email in-progress? sent-to error]} state
        valid? (and (m/validate users-schema/email-schema (or new-email ""))
                    (not= (str/lower-case (or new-email ""))
                          (str/lower-case (or current-email ""))))]
    [:> Dialog {:open (boolean open?) :on-close on-close :max-width "sm" :full-width true}
     [:> DialogTitle (tr :lipas.user/change-email)]
     [:> DialogContent
      (if sent-to
        [:> Typography {:variant "body2"} (tr :lipas.user/email-change-link-sent sent-to)]
        [:> Stack {:spacing 2}
         [:> Typography {:variant "body2"} help-text]
         [text-fields/text-field
          {:label (tr :lipas.user/email)
           :value current-email
           :disabled true}]
         [text-fields/text-field
          {:label (tr :lipas.user/new-email)
           :type "email"
           :spec users-schema/email-schema
           :value new-email
           :on-change on-change}]
         (when error
           [:> Typography {:color "error" :variant "body2"}
            (case error
              "same-email" (tr :lipas.user/email-change-same)
              "email-conflict" (tr :error/email-conflict)
              (tr :error/unknown))])])]
     [:> DialogActions
      [:> Button {:on-click on-close}
       (tr (if sent-to :actions/close :actions/cancel))]
      (when-not sent-to
        [:> Button {:variant "contained"
                    :color "secondary"
                    :disabled (or (not valid?) in-progress?)
                    :on-click #(on-submit new-email)}
         (tr :lipas.user/send-confirmation-link)])]]))
