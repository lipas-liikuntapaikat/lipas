(ns lipas.ui.service-notices.views
  "Notices about ongoing problems in services Lipas depends on.

  These are temporary. Once a problem is resolved, delete its entry in
  `texts`, its condition in `subs/::visible` and its keys in
  `i18n/*/service-notices.edn`."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/AlertTitle$default" :as AlertTitle]
            ["@mui/material/Stack$default" :as Stack]
            [lipas.ui.service-notices.events :as events]
            [lipas.ui.service-notices.subs :as subs]
            [lipas.ui.utils :refer [<== ==>]]))

(def texts
  {:mml-maps {:title :service-notices/mml-maps-title
              :body  :service-notices/mml-maps}
   :ptv-sync {:title :service-notices/ptv-sync-title
              :body  :service-notices/ptv-sync}})

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

(defn ptv-notice
  "The PTV notice for the full-screen PTV dialog, which covers the banners."
  [{:keys [tr]}]
  (when (<== [::subs/visible? :ptv-sync])
    [notice {:tr tr :id :ptv-sync}]))
