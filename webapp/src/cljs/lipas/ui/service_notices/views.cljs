(ns lipas.ui.service-notices.views
  "Notices about ongoing problems in services Lipas depends on, shown as
  warning bars at the top of the app.

  To add one, give it an entry in `texts`, keys in
  `i18n/*/service-notices.edn` and a condition for who sees it in
  `subs/::visible`. Full-screen dialogs, such as PTV's, cover the bars, so
  render the notice inside them too. Commit b23a735ba is an example: an MML
  notice on the map and a PTV notice for PTV users, also in the PTV dialog.
  Once the problem is resolved, delete the notice the same way."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Stack$default" :as Stack]
            [lipas.ui.service-notices.events :as events]
            [lipas.ui.service-notices.subs :as subs]
            [lipas.ui.utils :refer [<== ==>]]))

;; notice id -> {:title <tr key> :body <tr key>}
(def texts {})

(defn notice [{:keys [tr id sx]}]
  (let [{:keys [title body]} (texts id)]
    [:> Alert
     {:severity  "warning"
      :closeText (tr :actions/close)
      :onClose   #(==> [::events/dismiss id])
      :sx        sx}
     [:> AlertTitle (tr title)]
     (tr body)]))

(defn banners
  "Full-width bars for the top of the app, one per visible notice."
  [{:keys [tr]}]
  (when-let [ids (seq (<== [::subs/visible]))]
    [:> Stack {:sx #js {:flex "0 0 auto"}}
     (for [id ids]
       ^{:key id}
       [notice {:tr tr :id id :sx #js {:borderRadius 0}}])]))
