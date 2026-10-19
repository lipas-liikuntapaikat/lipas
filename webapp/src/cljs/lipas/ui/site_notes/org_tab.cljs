(ns lipas.ui.site-notes.org-tab
  "The org page's notes tab: all of the org's site notes. Required only by
  lipas.ui.org.views, so it stays in the lazy :org module."
  (:require ;; clj-kondo false positive: the bare symbol `Box` resolves
            ;; against a builtin during analysis, so the alias is reported
            ;; unused even though `[:> Box ...]` is used below. See
            ;; lipas.ui.assistant.views for the same pattern.
    #_{:clj-kondo/ignore [:unused-namespace]}
    ["@mui/material/Box$default" :as Box]
    ["@mui/material/Chip$default" :as Chip]
    ["@mui/material/Link$default" :as Link]
    ["@mui/material/Stack$default" :as Stack]
    ["@mui/material/Table$default" :as Table]
    ["@mui/material/TableBody$default" :as TableBody]
    ["@mui/material/TableCell$default" :as TableCell]
    ["@mui/material/TableHead$default" :as TableHead]
    ["@mui/material/TableRow$default" :as TableRow]
    ["@mui/material/Typography$default" :as Typography]
    [lipas.ui.site-notes.components :as components]
    [lipas.ui.site-notes.events :as events]
    [lipas.ui.site-notes.subs :as subs]
    [re-frame.core :as rf]
    [reagent.core :as r]
    [reitit.frontend.easy :as rfe]))

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
               [:> TableCell {:sx {:white-space "nowrap"}} (components/when-str note)]
               [:> TableCell [components/status-actions tr note]]])]]]
         [:> Typography {:color "text.secondary"} (tr :lipas.org/notes-empty)])])))
