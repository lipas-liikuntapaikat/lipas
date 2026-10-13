(ns lipas.ui.site-notes.views
  "Org-private site notes: a toolbar button + drawer on the sports-site panel,
  and the org page's notes tab. Writing is for members of the org that owns
  the site; the backend enforces the same."
  (:require ["@mui/material/Badge$default" :as Badge]
            ;; clj-kondo false positive: the bare symbol `Box` resolves
            ;; against a builtin during analysis, so the alias is reported
            ;; unused even though `[:> Box ...]` is used below. See
            ;; lipas.ui.assistant.views for the same pattern.
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/Drawer$default" :as Drawer]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.ui.site-notes.events :as events]
            [lipas.ui.site-notes.subs :as subs]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn- when-str [note]
  (utils/->human-date-time-at-user-tz (:created-at note)))

(defn delete-confirm-dialog [tr]
  (let [note @(rf/subscribe [::subs/confirm-delete])]
    [:> Dialog {:open (some? note)
                :on-close #(rf/dispatch [::events/confirm-delete nil])}
     [:> DialogContent
      [:> Typography (tr :lipas.org/notes-delete-confirm)]]
     [:> DialogActions
      [:> Button {:on-click #(rf/dispatch [::events/confirm-delete nil])}
       (tr :actions/cancel)]
      [:> Button {:color "error"
                  :on-click #(rf/dispatch [::events/set-status note "deleted"])}
       (tr :lipas.org/notes-delete)]]]))

(defn- status-actions [tr note]
  [:> Stack {:direction "row" :spacing 0.5}
   (if (= "archived" (:status note))
     [:> Tooltip {:title (tr :lipas.org/notes-restore)}
      [:> IconButton {:size "small"
                      :on-click #(rf/dispatch [::events/set-status note "active"])}
       [:> Icon {:fontSize "small"} "unarchive"]]]
     [:> Tooltip {:title (tr :lipas.org/notes-archive)}
      [:> IconButton {:size "small"
                      :on-click #(rf/dispatch [::events/set-status note "archived"])}
       [:> Icon {:fontSize "small"} "archive"]]])
   [:> Tooltip {:title (tr :lipas.org/notes-delete)}
    [:> IconButton {:size "small"
                    :on-click #(rf/dispatch [::events/confirm-delete note])}
     [:> Icon {:fontSize "small"} "delete"]]]])

(defn- note-card [tr note]
  [:> Paper {:variant "outlined"
             :sx {:p 1.5 :opacity (if (= "archived" (:status note)) 0.7 1)}}
   [:> Typography {:variant "body2" :sx {:white-space "pre-wrap" :overflow-wrap "anywhere"}}
    (:body note)]
   [:> Stack {:direction "row" :align-items "center" :sx {:mt 1}}
    [:> Typography {:variant "caption" :color "text.secondary" :sx {:flex 1}}
     (str (:author-name note) " · " (when-str note))]
    [status-actions tr note]]])

(defn- new-note-form [tr {:keys [lipas-id org-id]}]
  (let [draft   @(rf/subscribe [::subs/draft lipas-id])
        saving? @(rf/subscribe [::subs/saving?])]
    [:> Stack {:spacing 1}
     [:> TextField {:multiline true
                    :min-rows 3
                    :max-rows 10
                    :full-width true
                    :value draft
                    :placeholder (tr :lipas.org/notes-placeholder)
                    :input-props {:max-length 4000}
                    :on-change #(rf/dispatch [::events/set-draft lipas-id (.. % -target -value)])}]
     [:> Button {:variant "contained"
                 :color "secondary"
                 :disabled (or saving? (str/blank? draft))
                 :sx {:align-self "flex-end"}
                 :on-click #(rf/dispatch [::events/create-note {:lipas-id lipas-id
                                                                :org-id org-id
                                                                :body draft}])}
      (tr :lipas.org/notes-add)]]))

(defn notes-drawer [tr]
  (let [{:keys [lipas-id org-name show-archived?] :as drawer} @(rf/subscribe [::subs/drawer])
        active   (when lipas-id @(rf/subscribe [::subs/active-notes lipas-id]))
        archived (when lipas-id @(rf/subscribe [::subs/archived-notes lipas-id]))]
    [:> Drawer {:anchor "right"
                :open (some? drawer)
                :on-close #(rf/dispatch [::events/close-drawer])}
     (when drawer
       [:> Box {:sx {:width {:xs "100vw" :sm 420} :p 2}}
        [:> Stack {:direction "row" :align-items "center" :sx {:mb 1}}
         [:> Typography {:variant "h6" :sx {:flex 1}} (tr :lipas.org/notes)]
         [:> IconButton {:on-click #(rf/dispatch [::events/close-drawer])}
          [:> Icon "close"]]]
        [:> Typography {:variant "body2" :color "text.secondary" :sx {:mb 2}}
         (tr :lipas.org/notes-visibility org-name)]

        [new-note-form tr drawer]

        [:> Divider {:sx {:my 2}}]

        [:> Stack {:spacing 1}
         (if (seq active)
           (for [note active]
             ^{:key (:id note)} [note-card tr note])
           [:> Typography {:variant "body2" :color "text.secondary"}
            (tr :lipas.org/notes-empty)])]

        (when (seq archived)
          [:<>
           [:> Button {:size "small" :sx {:mt 2}
                       :on-click #(rf/dispatch [::events/toggle-show-archived])}
            (if show-archived?
              (tr :lipas.org/notes-hide-archived)
              (tr :lipas.org/notes-show-archived (count archived)))]
           (when show-archived?
             [:> Stack {:spacing 1 :sx {:mt 1}}
              (for [note archived]
                ^{:key (:id note)} [note-card tr note])])])])
     [delete-confirm-dialog tr]]))

(defn notes-button
  "Toolbar button for the sports-site panel. The badge counts active notes
  only. `org` is the site's owner org (the one the notes belong to)."
  [{:keys [tr lipas-id org]}]
  (r/with-let [_ (rf/dispatch [::events/get-site-notes lipas-id])]
    (let [active @(rf/subscribe [::subs/active-notes lipas-id])]
      [:<>
       [:> Tooltip {:title (tr :lipas.org/notes)}
        [:> IconButton {:on-click #(rf/dispatch [::events/open-drawer
                                                 {:lipas-id lipas-id
                                                  :org-id (str (:id org))
                                                  :org-name (:name org)}])}
         [:> Badge {:badge-content (count active) :color "secondary"}
          [:> Icon "sticky_note_2"]]]]
       [notes-drawer tr]])))

;; --- Org page tab ------------------------------------------------------------

(defn org-notes-tab
  "All of the org's notes, newest first, filtered to active or archived."
  [tr org-id]
  (r/with-let [_ (rf/dispatch [::events/get-org-notes org-id "active"])]
    (let [{:keys [status notes]} @(rf/subscribe [::subs/org-notes])]
      [:> Box {:sx {:p 2}}
       [:> Stack {:direction "row" :spacing 1 :sx {:mb 2}}
        (for [[s tr-key] [["active" :lipas.org/notes-active]
                          ["archived" :lipas.org/notes-archived]]]
          [:> Chip {:key s
                    :label (tr tr-key)
                    :color (if (= s status) "secondary" "default")
                    :variant (if (= s status) "filled" "outlined")
                    :on-click #(rf/dispatch [::events/get-org-notes org-id s])}])]
       (if (seq notes)
         [:> Box {:sx {:overflow-x "auto"}}
          [:> Table {:size "small"}
           [:> TableHead
            [:> TableRow
             [:> TableCell (tr :lipas.org/notes-site)]
             [:> TableCell (tr :lipas.org/notes-note)]
             [:> TableCell (tr :lipas.org/notes-author)]
             [:> TableCell (tr :lipas.org/notes-date)]
             [:> TableCell]]]
           [:> TableBody
            (for [note notes]
              [:> TableRow {:key (:id note)}
               [:> TableCell
                [:> Link {:href (rfe/href :lipas.ui.routes.map/details-view
                                          {:lipas-id (:lipas-id note)})}
                 (or (:site-name note) (:lipas-id note))]]
               [:> TableCell {:sx {:white-space "pre-wrap" :min-width 240}}
                (:body note)]
               [:> TableCell (:author-name note)]
               [:> TableCell {:sx {:white-space "nowrap"}} (when-str note)]
               [:> TableCell [status-actions tr note]]])]]]
         [:> Typography {:color "text.secondary"} (tr :lipas.org/notes-empty)])
       [delete-confirm-dialog tr]])))
