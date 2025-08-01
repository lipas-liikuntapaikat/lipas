(ns lipas.ui.sports-sites.activities.itrs-migration
  "Helper component for migrating legacy cycling difficulty to ITRS classification"
  (:require
    ["@mui/material/Alert$default" :as Alert]
    ["@mui/material/Button$default" :as Button]
    ["@mui/material/Dialog$default" :as Dialog]
    ["@mui/material/DialogActions$default" :as DialogActions]
    ["@mui/material/DialogContent$default" :as DialogContent]
    ["@mui/material/DialogContentText$default" :as DialogContentText]
    ["@mui/material/DialogTitle$default" :as DialogTitle]
    ["@mui/material/LinearProgress$default" :as LinearProgress]
    [lipas.data.activities :as activities]
    [lipas.ui.uix.hooks :refer [use-subscribe]]
    [lipas.ui.utils :refer [==>]]
    [re-frame.core :as rf]
    [uix.core :as uix :refer [$ defui]]))

(defui migration-helper
  "Shows a helper message when legacy difficulty data exists and ITRS is enabled"
  [{:keys [route-id lipas-id activity-k]}]
  (let [tr (use-subscribe [:lipas.ui.subs/translator])
        route (use-subscribe [:lipas.ui.sports-sites.activities.subs/route route-id lipas-id activity-k])
        has-legacy? (or (:cycling-difficulty route)
                        (some :route-part-difficulty
                              (-> route :geometries :features)))
        itrs-enabled? (:itrs-classification? route)
        [dialog-open? set-dialog-open] (uix/use-state false)
        [migrating? set-migrating] (uix/use-state false)]

    (when (and has-legacy? itrs-enabled?)
      ($ :div
         ($ Alert
            {:severity "info"
             :action ($ Button
                        {:size "small"
                         :onClick #(set-dialog-open true)}
                        (tr :itrs/migrate-button))}
            (tr :itrs/migration-available))

         ($ Dialog
            {:open dialog-open?
             :onClose #(set-dialog-open false)}
            ($ DialogTitle (tr :itrs/migrate-dialog-title))
            ($ DialogContent
               ($ DialogContentText
                  (tr :itrs/migrate-dialog-description))
               (when migrating?
                 ($ LinearProgress {:sx #js {:marginTop 2}})))
            ($ DialogActions
               ($ Button
                  {:onClick #(set-dialog-open false)
                   :disabled migrating?}
                  (tr :actions/cancel))
               ($ Button
                  {:onClick (fn []
                              (set-migrating true)
                              (rf/dispatch [:lipas.ui.sports-sites.activities.events/migrate-route-to-itrs
                                            lipas-id activity-k route-id
                                            (fn []
                                              (set-migrating false)
                                              (set-dialog-open false))]))
                   :disabled migrating?
                   :variant "contained"}
                  (tr :itrs/migrate-confirm))))))))

(defn migrate-segments-to-itrs
  "Migrates legacy segment difficulty to ITRS technical difficulty"
  [segments]
  (mapv (fn [segment]
          (if-let [legacy (:route-part-difficulty segment)]
            (-> segment
                (assoc :itrs-technical (get activities/legacy->itrs-technical legacy "2"))
                (dissoc :route-part-difficulty))
            segment))
        segments))

(defn migrate-route-to-itrs
  "Migrates a single route's difficulty data to ITRS"
  [route]
  (-> route
      ;; Migrate route-level difficulty (if any mapping needed)
      (dissoc :cycling-difficulty :cycling-route-difficulty)
      ;; Migrate segment difficulties
      (update-in [:geometries :features] migrate-segments-to-itrs)))