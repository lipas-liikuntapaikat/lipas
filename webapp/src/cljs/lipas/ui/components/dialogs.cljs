(ns lipas.ui.components.dialogs
  (:require
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn dialog
  [{:keys [title on-save on-close save-label save-enabled?
           cancel-label open? max-width]
    :or   {open? true max-width "sm"}} content]
  [mui/dialog
   {:open open? :full-width true :on-close on-close :max-width max-width}
   [mui/dialog-title title]
   [mui/dialog-content content]
   [mui/dialog-actions
    [mui/button {:on-click on-close}
     cancel-label]
    [mui/button {:on-click on-save :disabled (not save-enabled?)}
     save-label]]])

(defn slide [props]
  [mui/slide props])

(defn full-screen-dialog [{:keys [open? title on-close close-label top-actions
                                  bottom-actions]}
                          & contents]
  [mui/dialog {:open                 open?
               :full-screen          true
               :Transition-component (r/reactify-component slide)
               :Transition-props     {:direction "up"}
               :on-close             on-close
               :Paper-Props          {:style {:background-color mui/gray1}}}

   ;; Top bar
   [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
    (into
     [mui/dialog-actions {:style
                          {:margin           0
                           :padding-right    "0.5em"
                           :background-color mui/primary}}
      [mui/dialog-title {:style {:flex-grow 1}}
       (or title "")]]
     top-actions)]

   ;; Content
   (into [mui/dialog-content {:style {:padding 8}}]
         contents)

   ;; Bottom bar
   [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
    (conj
     (into
      [mui/dialog-actions
       {:style {:margin           0
                :background-color mui/secondary2}}]
      bottom-actions)
     [mui/button {:on-click on-close}
      close-label])]])

(defn confirmation-dialog [{:keys [title message on-cancel on-decline
                                   decline-label cancel-label
                                   on-confirm confirm-label]}]
  [mui/dialog {:open                    true
               :disable-backdrop-click  true
               :disable-escape-key-down true}
   [mui/dialog-title title]
   [mui/dialog-content
    [mui/typography message]]
   [mui/dialog-actions
    [mui/button {:on-click on-cancel} cancel-label]
    (when on-decline
      [mui/button {:on-click on-decline} decline-label])
    [mui/button {:on-click on-confirm} confirm-label]]])
