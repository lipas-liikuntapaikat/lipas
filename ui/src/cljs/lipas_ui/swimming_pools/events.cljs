(ns lipas-ui.swimming-pools.events
  (:require [re-frame.core :as re-frame]
            [lipas-ui.db :refer [default-db]]))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:swimming-pools :active-tab] active-tab)))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (assoc-in db (into [:swimming-pools] path) value)))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :swimming-pools :dialogs dialog :data))]
     (-> db
         (update-in [:swimming-pools :dialogs dialog :open?] not)
         (assoc-in [:swimming-pools :dialogs dialog :data] data)))))

(def maxc (partial apply max))

(defn next-id [db path]
  (-> db (get-in path) keys maxc inc))

(defn save-entity [db path entity]
  (let [id (or (:id entity) (next-id db path))]
    (assoc-in db (conj path id) (assoc entity :id id))))

(re-frame/reg-event-db
 ::save-renovation
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :renovations]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-sauna
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :saunas]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-pool
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :pools]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-slide
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :slides]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::remove-renovation
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :renovations] dissoc id)))

(re-frame/reg-event-db
 ::remove-sauna
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :saunas] dissoc id)))

(re-frame/reg-event-db
 ::remove-pool
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :pools] dissoc id)))

(re-frame/reg-event-db
 ::remove-slide
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :slides] dissoc id)))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (let [empty-data (-> default-db :swimming-pools :dialogs dialog)]
     (assoc-in db [:swimming-pools :dialogs dialog] empty-data))))
