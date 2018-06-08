(ns lipas.ui.ice-stadiums.events
  (:require [re-frame.core :as re-frame]
            [lipas.ui.utils :refer [save-entity ->indexed-map]]))

(defn make-editable [ice-stadium]
  (-> ice-stadium
      (update-in [:renovations] ->indexed-map)
      (update-in [:rinks] ->indexed-map)
      (update-in [:energy-consumption] ->indexed-map)))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:ice-stadiums :active-tab] active-tab)))

(re-frame/reg-event-db
 ::edit
 (fn [db [_ ice-stadium]]
   (assoc-in db [:ice-stadiums :editing] (make-editable ice-stadium))))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (assoc-in db (into [:ice-stadiums] path) value)))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :ice-stadiums :dialogs dialog :data))]
     (-> db
         (update-in [:ice-stadiums :dialogs dialog :open?] not)
         (assoc-in [:ice-stadiums :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::save-renovation
 (fn [db [_ value]]
   (let [path [:ice-stadiums :editing :renovations]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-rink
 (fn [db [_ value]]
   (let [path [:ice-stadiums :editing :rinks]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-energy
 (fn [db [_ value]]
   (let [path [:ice-stadiums :editing :energy-consumption]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::remove-renovation
 (fn [db [_ {:keys [id]}]]
   (update-in db [:ice-stadiums :editing :renovations] dissoc id)))

(re-frame/reg-event-db
 ::remove-rink
 (fn [db [_ {:keys [id]}]]
   (update-in db [:ice-stadiums :editing :rinks] dissoc id)))

(re-frame/reg-event-db
 ::remove-energy
 (fn [db [_ {:keys [id]}]]
   (update-in db [:ice-stadiums :editing :energy-consumption] dissoc id)))
