(ns lipas.ui.site-notes.components
  "Small pieces shared by the site-panel notes drawer (lazy :map) and the org
  page's notes tab (lazy :org). Being required by both, this namespace lands in
  :app — keep it tiny."
  (:require ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Tooltip$default" :as Tooltip]
            [lipas.ui.site-notes.events :as events]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(defn when-str [note]
  (utils/->human-date-time-at-user-tz (:created-at note)))

(defn status-actions
  "Archive an active note; restore or delete an archived one. Delete is only
  offered once a note is archived, and asks first."
  [tr note]
  (if (= "archived" (:status note))
    [:<>
     [:> Tooltip {:title (tr :lipas.org/notes-restore)}
      [:> IconButton {:size "small"
                      :on-click #(rf/dispatch [::events/set-status note "active"])}
       [:> Icon {:fontSize "small"} "unarchive"]]]
     [:> Tooltip {:title (tr :lipas.org/notes-delete)}
      [:> IconButton {:size "small"
                      :on-click #(rf/dispatch [:lipas.ui.events/confirm
                                               (tr :lipas.org/notes-delete-confirm)
                                               (fn [] (rf/dispatch [::events/set-status note "deleted"]))])}
       [:> Icon {:fontSize "small"} "delete"]]]]
    [:> Tooltip {:title (tr :lipas.org/notes-archive)}
     [:> IconButton {:size "small"
                     :on-click #(rf/dispatch [::events/set-status note "archived"])}
      [:> Icon {:fontSize "small"} "archive"]]]))
