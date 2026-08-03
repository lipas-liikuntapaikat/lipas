(ns lipas.ui.sports-sites.floorball.events
  (:require [lipas.ui.sports-sites.floorball.db :as floorball]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

;; NOTE: props derivation from :fields used to happen here automatically on
;; every edit (calc-derived-fields-for-type 2240). It stamped nil/""/0 over
;; the props whenever :fields was empty, which both blocked saving (schema
;; rejects nil props) and wiped legacy hand-entered values. Derivation is
;; now an explicit one-off action in the properties form, backed by
;; lipas.data.floorball/derive-props.

(rf/reg-event-db ::set-dialog-field
  (fn [db [_ dialog field value]]
    (let [path [:sports-sites :floorball :dialogs dialog :data field]]
      (utils/set-field db path value))))

(rf/reg-event-db ::reset-dialog
  (fn [db [_ dialog]]
    (assoc-in db
              [:sports-sites :floorball :dialogs dialog]
              (-> floorball/default-db
                  :dialogs dialog))))

(rf/reg-event-db ::toggle-dialog
  (fn [db [_ dialog data]]
    (let [data (or data (-> db :sports-sites :floorball :dialogs dialog :data)
                   (-> floorball/default-db :dialogs dialog :data))]
      (-> db
          (update-in [:sports-sites :floorball :dialogs dialog :open?] not)
          (assoc-in [:sports-sites :floorball :dialogs dialog :data] data)))))

(rf/reg-event-fx ::save-dialog
  (fn [{:keys [db]} [_ entities-k lipas-id value]]
    (let [path     (if lipas-id
                     [:sports-sites lipas-id :editing entities-k]
                     [:new-sports-site :data entities-k])
          new-db   (utils/save-entity db path value)
          entities (get-in new-db path)]
      {:db         new-db
       :dispatch-n [(when lipas-id
                      [:lipas.ui.sports-sites.events/edit-field lipas-id [entities-k] entities])
                    (when-not lipas-id
                      [:lipas.ui.sports-sites.events/edit-new-site-field [entities-k] entities])]})))

(rf/reg-event-db ::remove-field
  (fn [db [_ lipas-id {:keys [id]}]]
    (let [path (if lipas-id
                 [:sports-sites lipas-id :editing :fields]
                 [:new-sports-site :data :fields])]
      (update-in db path dissoc id))))

(rf/reg-event-db ::remove-locker-room
  (fn [db [_ lipas-id {:keys [id]}]]
    (let [path (if lipas-id
                 [:sports-sites lipas-id :editing :locker-rooms]
                 [:new-sports-site :data :locker-rooms])]
      (update-in db path dissoc id))))

(rf/reg-event-db ::remove-audit
  (fn [db [_ lipas-id {:keys [id]}]]
    (let [path (if lipas-id
                 [:sports-sites lipas-id :editing :audits]
                 [:new-sports-site :data :audits])]
      (update-in db path dissoc id))))
