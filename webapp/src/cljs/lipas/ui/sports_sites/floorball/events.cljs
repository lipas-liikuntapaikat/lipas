(ns lipas.ui.sports-sites.floorball.events
  (:require
   [re-frame.core :as re-frame]
   [lipas.ui.utils :as utils :refer [==>]]))

(re-frame/reg-event-db
 ::set-dialog-field
 (fn [db [_ dialog field value]]
   (let [path [:sports-sites :floorball :dialogs dialog :data field]]
     (utils/set-field db path value))))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (assoc-in db [:sports-sites :floorball :dialogs dialog] {})))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :sports-sites :floorball :dialogs dialog :data))]
     (-> db
         (update-in [:sports-sites :floorball :dialogs dialog :open?] not)
         (assoc-in [:sports-sites :floorball :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::save-dialog
 (fn [db [_ entities-k lipas-id value]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing entities-k]
                [:new-sports-site :data entities-k])]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-field
 (fn [db [_ lipas-id {:keys [id]}]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing :fields]
                [:new-sports-site :data :fields])]
     (update-in db path dissoc id))))

(re-frame/reg-event-db
 ::remove-locker-room
 (fn [db [_ lipas-id {:keys [id]}]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing :fields]
                [:new-sports-site :data :locker-rooms])]
     (update-in db path dissoc id))))
