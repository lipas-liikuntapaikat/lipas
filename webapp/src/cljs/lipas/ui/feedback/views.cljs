(ns lipas.ui.feedback.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.feedback.events :as events]
            [lipas.ui.feedback.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>]]))

(defn feedback-btn []
  (let [tr          (<== [:lipas.ui.subs/translator])
        modal-open? (<== [::subs/modal-open?])
        types       (<== [::subs/types-select-items])
        form-state  (<== [::subs/form])
        form-valid? (<== [::subs/form-valid?])]
    [:<>
     [lui/dialog
      {:open?         modal-open?
       :on-close      #(==> [::events/close-modal])
       :save-enabled? form-valid?
       :title         "Anna palautetta"
       :save-label    "Lähetä"
       :cancel-label  (tr :actions/cancel)
       :on-save       #(==> [::events/send form-state])}

      [mui/grid {:container true :spacing 2}

       ;; Feedback type
       [mui/grid {:item true :xs 12}
        [lui/autocomplete
         {:label     "Palautteen aihe"
          :required  true
          :style     {:min-width "170px"}
          :value     (:lipas.feedback/type form-state)
          :on-change #(==> [::events/select-feedback-type %])
          :items     types}]]

       ;; Sender email
       [mui/grid {:item true :xs 12}
        [lui/text-field
         {:label     "Sähköpostiosoite (ei pakollinen)"
          :fullWidth true
          :spec      :lipas.feedback/sender
          :value     (:lipas.feedback/sender form-state)
          :on-change #(==> [::events/set-sender-email %])}]]

       ;; Feedback text
       [mui/grid {:item true :xs 12}
        [lui/text-field
         {:label           "Palaute"
          :fullWidth       true
          :multiline       true
          :required        true
          :variant         "outlined"
          :InputLabelProps {:style {:zIndex "initial"}}
          :value           (:lipas.feedback/text form-state)
          :on-change       #(==> [::events/set-text %])}]]]]

     ;; The button
     [mui/tooltip {:title "Anna palautetta"}
      [mui/icon-button
       {:size     "medium"
        :style {:margin-right "0.2em"}
        #_#_:color    "primary"
        :on-click #(==> [::events/open-modal])}
       [mui/icon "feedback"]]]]))
