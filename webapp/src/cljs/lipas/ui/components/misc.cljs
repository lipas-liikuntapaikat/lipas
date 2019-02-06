(ns lipas.ui.components.misc
  (:require
   [lipas.ui.mui :as mui]
   [lipas.ui.components.buttons :as buttons]))

;; Returns actually a list of components.
;; TODO think something more intuitive here.
(defn edit-actions-list [{:keys [editing? valid? logged-in?
                                 user-can-publish? on-discard
                                 on-save-draft save-draft-tooltip
                                 discard-tooltip edit-tooltip
                                 publish-tooltip on-edit-start
                                 invalid-message on-edit-end
                                 delete-tooltip on-delete
                                 on-publish]}]

  [(when (and editing? user-can-publish?)
     [buttons/save-button
      {:variant          "extendedFab"
       :on-click         on-publish
       :disabled         (not valid?)
       :disabled-tooltip invalid-message
       :tooltip          publish-tooltip}])

   (when (and on-delete logged-in? (not editing?))
     [buttons/delete-button
      {:variant  "fab"
       :on-click on-delete
       :tooltip  delete-tooltip}])

   (when (and editing? (not user-can-publish?))
     [buttons/save-button
      {:variant          "extendedFab"
       :on-click         on-save-draft
       :disabled         (not valid?)
       :disabled-tooltip invalid-message
       :tooltip          save-draft-tooltip}])

   (when editing?
     [buttons/discard-button
      {:variant  :fab
       :on-click on-discard
       :tooltip  discard-tooltip}])

   (when (and logged-in? (not editing?))
     [buttons/edit-button
      {:variant  "fab"
       :color    "secondary"
       :disabled (and editing? (not valid?))
       :active?  editing?
       :on-click #(if editing?
                    (on-edit-end %)
                    (on-edit-start %))
       :tooltip  edit-tooltip}])])

(defn icon-text [{:keys [icon text icon-color]}]
  [mui/grid {:container true :align-items :center
             :style     {:padding "0.5em"}}
   [mui/grid {:item true}
    [mui/icon {:color (or icon-color "inherit")}
     icon]]
   [mui/grid {:item true}
    [mui/typography
     {:variant "body2"
      :style
      {:margin-left "0.5em" :display "inline"}}
     text]]])

(defn li [text & children]
  (into
   [:li
    [mui/typography {:variant "body2" :color :default}
     text]]
   children))

(defn sub-heading [{:keys [label]}]
  [mui/typography
   {:variant "subtitle1"
    :style
    {:margin-top    "2em"
     :margin-bottom "2em"
     :font-weight   "bold"}}
   label])
