(ns lipas-ui.swimming-pools.events
  (:require [re-frame.core :as re-frame]
            [lipas-ui.db :refer [default-db]]))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:swimming-pools :active-tab] active-tab)))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog]]
   (update-in db [:swimming-pools :dialogs dialog :open?] not)))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (assoc-in db (into [:swimming-pools] path) value)))

(re-frame/reg-event-db
 ::set-base-form-field
 (fn [db [_ path value]]
   (assoc-in db (into [:swimming-pools :edit] path) value)))

(re-frame/reg-event-db
 ::set-dialog-form-field
 (fn [db [_ path value]]
   (assoc-in db (into [:swimming-pools :dialogs] path) value)))

(def maxc (partial apply max))

(defn add-entity [db path entity]
  (let [next-id (-> db (get-in path) keys maxc inc)]
    (assoc-in db (conj path next-id) (assoc entity :id next-id))))

(re-frame/reg-event-db
 ::add-renovation
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :renovations]]
     (add-entity db path value))))

(re-frame/reg-event-db
 ::remove-renovation
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :renovations] dissoc id)))

(re-frame/reg-event-db
 ::add-pool
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :pools]]
     (add-entity db path value))))

(re-frame/reg-event-db
 ::remove-pool
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :pools] dissoc id)))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (let [empty-data (-> default-db :swimming-pools :dialogs dialog)]
     (assoc-in db [:swimming-pools :dialogs dialog] empty-data))))
