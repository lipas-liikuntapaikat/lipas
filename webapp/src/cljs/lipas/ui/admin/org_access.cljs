(ns lipas.ui.admin.org-access
  "Admin page tab: org-management rollout. Every org with its access level
  (PTV only / org management), switchable per row after a confirmation that
  states how many members are affected. Reads and writes the shared org list in
  app-db ([:user :orgs]), so a switch shows in every other org view at once."
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.schema.org :as org-schema]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.org.events :as org-events]
            [lipas.ui.org.subs :as org-subs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(def ^:private level-labels
  {"ptv-only"       :lipas.org/access-level-ptv-only
   "org-management" :lipas.org/access-level-org-management})

(defn- level-of [org]
  (or (:access-level org) org-schema/default-access-level))

(defn- confirm-dialog [tr]
  (let [{:keys [open? org access-level saving?]} @(rf/subscribe [::org-subs/access-dialog])]
    [:> Dialog {:open (boolean open?)
                :on-close #(rf/dispatch [::org-events/close-access-dialog])}
     [:> DialogContent
      [:> Typography
       (tr (if (= "org-management" access-level)
             :lipas.org/access-level-enable-confirm
             :lipas.org/access-level-disable-confirm)
           (:name org)
           (count (:members org)))]]
     [:> DialogActions
      [:> Button {:on-click #(rf/dispatch [::org-events/close-access-dialog])}
       (tr :actions/cancel)]
      [:> Button {:color "secondary"
                  :variant "contained"
                  :disabled (boolean saving?)
                  :on-click #(rf/dispatch [::org-events/set-access-level
                                           (str (:id org)) access-level])}
       (tr :lipas.org/access-level-confirm)]]]))

(defn- level-select [tr org]
  [:> Select {:size "small"
              :value (level-of org)
              :on-change (fn [e]
                           (let [v (.. e -target -value)]
                             (when (not= v (level-of org))
                               (rf/dispatch [::org-events/open-access-dialog org v]))))}
   (for [level org-schema/access-levels]
     [:> MenuItem {:key level :value level} (tr (level-labels level))])])

(defn- org-row [tr org]
  [:> TableRow
   [:> TableCell
    [:> Link {:href (rfe/href :lipas.ui.routes/org {:org-id (str (:id org))})}
     (:name org)]]
   [:> TableCell (some->> (:type org) (str "type-") (keyword "lipas.org") tr)]
   [:> TableCell {:align "right"} (count (:members org))]
   [:> TableCell {:align "right"} (:site-count org 0)]
   [:> TableCell [level-select tr org]]])

(defn org-access-tab []
  (r/with-let [_      (rf/dispatch [::org-events/get-user-orgs])
               search (r/atom "")
               level  (r/atom "all")]
    (let [tr   @(rf/subscribe [:lipas.ui.subs/translator])
          orgs @(rf/subscribe [::org-subs/user-orgs])
          q    (str/lower-case (str/trim @search))
          rows (->> orgs
                    (filter #(or (= "all" @level) (= @level (level-of %))))
                    (filter #(or (str/blank? q)
                                 (str/includes? (str/lower-case (str (:name %))) q)))
                    (sort-by (comp str/lower-case str :name)))]
      [:> Card {:square true}
       [:> CardContent
        [:> Typography {:variant "h5" :sx {:mb 2}} (tr :lipas.org/access-level-tab)]
        [:> Stack {:direction {:xs "column" :sm "row"} :spacing 2 :sx {:mb 2}}
         [text-fields/text-field-controlled
          {:label (tr :lipas.org/access-level-search)
           :value @search
           :on-change #(reset! search (or % ""))}]
         [:> Select {:size "small"
                     :value @level
                     :on-change #(reset! level (.. % -target -value))}
          [:> MenuItem {:value "all"} (tr :lipas.org/access-level-all)]
          (for [l org-schema/access-levels]
            [:> MenuItem {:key l :value l} (tr (level-labels l))])]]
        [:> Table {:size "small"}
         [:> TableHead
          [:> TableRow
           [:> TableCell (tr :lipas.org/name)]
           [:> TableCell (tr :lipas.org/org-type)]
           [:> TableCell {:align "right"} (tr :lipas.org/members-assigned)]
           [:> TableCell {:align "right"} (tr :lipas.org/sites-owned-col)]
           [:> TableCell (tr :lipas.org/access-level)]]]
         [:> TableBody
          (for [org rows]
            ^{:key (str (:id org))} [org-row tr org])]]
        [confirm-dialog tr]]])))
