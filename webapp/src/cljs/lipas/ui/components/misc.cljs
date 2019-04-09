(ns lipas.ui.components.misc
  (:require
   [lipas.ui.mui :as mui]
   [lipas.ui.components.buttons :as buttons]))

;; Returns actually a list of components.
;; TODO think something more intuitive here.
(defn edit-actions-list
  [{:keys [editing? valid? logged-in?  user-can-publish? on-discard
           discard-tooltip edit-tooltip publish-tooltip on-edit-start
           invalid-message on-edit-end delete-tooltip on-delete
           on-publish editing-allowed?]}]

  [(when (and editing? user-can-publish?)
     [buttons/save-button
      {:on-click         on-publish
       :disabled         (not valid?)
       :disabled-tooltip invalid-message
       :tooltip          publish-tooltip}])

   (when (and logged-in? editing-allowed? user-can-publish? (not editing?))
     [buttons/edit-button
      {:color    "secondary"
       :disabled (and editing? (not valid?))
       :active?  editing?
       :on-click #(if editing?
                    (on-edit-end %)
                    (on-edit-start %))
       :tooltip  edit-tooltip}])

   (when (and on-delete logged-in? editing-allowed? user-can-publish? (not editing?))
     [buttons/delete-button
      {:on-click on-delete
       :tooltip  delete-tooltip}])

   (when editing?
     [buttons/discard-button
      {:on-click on-discard :tooltip discard-tooltip}])])

(defn icon-text [{:keys [icon text icon-color]}]
  [mui/grid {:container true :align-items "center" :style {:padding "0.5em"}}
   [mui/grid {:item true}
    [mui/icon {:color (or icon-color "inherit")}
     icon]]
   [mui/grid {:item true}
    [mui/typography
     {:variant "body2" :style {:margin-left "0.5em" :display "inline"}}
     text]]])

(defn icon-text2 [{:keys [icon text icon-color]}]
  [mui/grid {:container true :align-items "center" :style {:padding "0.5em"}}
   [mui/grid {:item true}
    [mui/icon {:style {:color icon-color}}
     icon]]
   [mui/grid {:item true}
    [mui/typography
     {:variant "body2" :style {:margin-left "0.5em" :display "inline"}}
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
    {:margin-top    "1.5em"
     :margin-bottom "0.5em"
     :font-weight   "bold"}}
   label])
