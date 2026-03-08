(ns lipas.ui.feedback.views
  (:require [lipas.ui.components.autocompletes :as autocompletes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.feedback.events :as events]
            [lipas.ui.feedback.subs :as subs]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Tooltip$default" :as Tooltip]
            [lipas.ui.utils :refer [<== ==>]]
            [lipas.schema.users :as users-schema]))

(defn feedback-btn []
  (let [tr          (<== [:lipas.ui.subs/translator])
        modal-open? (<== [::subs/modal-open?])
        types       (<== [::subs/types-select-items])
        form-state  (<== [::subs/form])
        form-valid? (<== [::subs/form-valid?])]
    [:<>
     [dialogs/dialog
      {:open?         modal-open?
       :on-close      #(==> [::events/close-modal])
       :save-enabled? form-valid?
       :title         "Anna palautetta"
       :save-label    "Lähetä"
       :cancel-label  (tr :actions/cancel)
       :on-save       #(==> [::events/send form-state])}

      [:> Grid {:container true :spacing 2}

       ;; Feedback type
       [:> Grid {:item true :xs 12}
        [autocompletes/autocomplete
         {:label     "Palautteen aihe"
          :required  true
          :style     {:min-width "170px"}
          :value     (:lipas.feedback/type form-state)
          :on-change #(==> [::events/select-feedback-type %])
          :items     types}]]

       ;; Sender email
       [:> Grid {:item true :xs 12}
        [text-fields/text-field
         {:label     "Sähköpostiosoite (ei pakollinen)"
          :fullWidth true
          :spec      users-schema/email-schema
          :value     (:lipas.feedback/sender form-state)
          :on-change #(==> [::events/set-sender-email %])}]]

       ;; Feedback text
       [:> Grid {:item true :xs 12}
        [text-fields/text-field
         {:label           "Palaute"
          :fullWidth       true
          :multiline       true
          :required        true
          :variant         "outlined"
          :InputLabelProps {:style {:zIndex "initial"}}
          :value           (:lipas.feedback/text form-state)
          :on-change       #(==> [::events/set-text %])}]]]]

     ;; The button
     [:> Tooltip {:title "Anna palautetta"}
      [:> IconButton
       {:size     "large"
        :on-click #(==> [::events/open-modal])}
       [:> Icon "feedback"]]]]))
