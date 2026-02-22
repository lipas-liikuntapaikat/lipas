(ns lipas.ui.components.dialogs
  (:require ["@mui/material/Slide$default" :as Slide]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.mui :as mui]
            [reagent-dev-tools.state :as dev-state]))

(defn dialog
  [{:keys [title on-save on-close save-label save-enabled?
           cancel-label open? max-width]
    :or   {open? true max-width "sm"}} content]
  [:> Dialog
   {:open open?
    :full-width true
    :on-close on-close
    :max-width max-width}
   [:> DialogTitle title]
   [:> DialogContent content]
   [:> DialogActions
    (when on-close
      [:> Button {:on-click on-close}
       cancel-label])
    (when on-save
      [:> Button {:on-click on-save :disabled (not save-enabled?)}
       save-label])]])

(defn full-screen-dialog
  [{:keys [open? title on-close close-label top-actions
           bottom-actions]} & contents]
  (let [{dev-open? :open? :keys [place width height]} @dev-state/dev-state]
    [:> Dialog {:open                 open?
                 :full-screen          true
                 :Transition-component Slide
                 :Transition-props     {:direction "up"}
                 :on-close             on-close
                 :Paper-Props          {:style {:background-color mui/gray1}}
                 :style {:margin-right (when (and dev-open? (= :right place)) (str width "px"))
                         :margin-bottom (when (and dev-open? (= :bottom place)) (str height "px"))}}

     ;; Top bar
     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
      (into
        [:> DialogActions {:style
                             {:margin           0
                              :padding-right    "0.5em"
                              :background-color mui/primary}}
         [:> DialogTitle {:sx {:flex-grow 1
                                 :color "white"}}
          (or title "")]]
        top-actions)]

     ;; Content
     (into [:> DialogContent {:style {:padding 8}}]
           contents)

     ;; Bottom bar
     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
      (conj
        (into
          [:> DialogActions
           {:style {:margin           0
                    :background-color mui/secondary2}}]
          bottom-actions)
        [:> Button {:on-click on-close}
         close-label])]]))

(defn confirmation-dialog
  [{:keys [title message on-cancel on-decline decline-label
           cancel-label on-confirm confirm-label]}]
  [:> Dialog
   {:open                    true
    :on-close                #()
    :disable-escape-key-down true}
   [:> DialogTitle title]
   [:> DialogContent
    [:> Typography message]]
   [:> DialogActions
    [:> Button {:on-click on-cancel} cancel-label]
    (when on-decline
      [:> Button {:on-click on-decline} decline-label])
    [:> Button {:on-click on-confirm} confirm-label]]])
