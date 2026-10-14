(ns lipas.ui.site-notes.views
  "Org-private site notes on the sports-site panel: a toolbar button + drawer.
  Writing is for members of the org that owns the site; the backend enforces
  the same. Lives in the lazy :map module — the org page's notes tab is in
  lipas.ui.site-notes.org-tab (lazy :org) and the bits both share in
  lipas.ui.site-notes.components, so neither view is hoisted into :app."
  (:require ["@mui/material/Badge$default" :as Badge]
            ;; clj-kondo false positive: the bare symbol `Box` resolves
            ;; against a builtin during analysis, so the alias is reported
            ;; unused even though `[:> Box ...]` is used below. See
            ;; lipas.ui.assistant.views for the same pattern.
            #_{:clj-kondo/ignore [:unused-namespace]}
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/Drawer$default" :as Drawer]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.site-notes.components :as components]
            [lipas.ui.site-notes.events :as events]
            [lipas.ui.site-notes.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn- note-card [tr note]
  [:> Paper {:variant "outlined"
             :sx {:p 1.5 :opacity (if (= "archived" (:status note)) 0.7 1)}}
   [:> Typography {:variant "body2" :sx {:white-space "pre-wrap" :overflow-wrap "anywhere"}}
    (:body note)]
   [:> Stack {:direction "row" :align-items "center" :sx {:mt 1}}
    [:> Typography {:variant "caption" :color "text.secondary" :sx {:flex 1}}
     (str (:author-name note) " · " (components/when-str note))]
    [components/status-actions tr note]]])

(defn- new-note-form [tr {:keys [lipas-id org-id]}]
  (let [draft   @(rf/subscribe [::subs/draft lipas-id])
        saving? @(rf/subscribe [::subs/saving?])]
    [:> Stack {:spacing 1}
     ;; The patched controlled field keeps keystrokes that a plain
     ;; [:> TextField] bound to a re-frame sub would drop.
     [text-fields/text-field-controlled
      {:multiline true
       :variant "outlined"
       :min-rows 3
       :max-rows 10
       :full-width true
       :value draft
       :placeholder (tr :lipas.org/notes-placeholder)
       :input-props {:max-length 4000}
       :on-change #(rf/dispatch [::events/set-draft lipas-id (or % "")])}]
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
                ^{:key (:id note)} [note-card tr note])])])])]))

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
