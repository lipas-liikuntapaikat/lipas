(ns lipas.ui.ice-stadiums.events
  (:require [re-frame.core :as re-frame]
            [lipas.ui.db :refer [default-db]]
            [lipas.ui.utils :refer [save-entity ->indexed-map]]))

(defn make-editable [ice-stadium]
  (-> ice-stadium
      (update-in [:renovations] ->indexed-map)
      (update-in [:rinks] ->indexed-map)))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:ice-stadiums :active-tab] active-tab)))

(re-frame/reg-event-db
 ::set-edit-site
 (fn [db [_ site]]
   (-> db
       (assoc-in [:ice-stadiums :editing :site] site)
       (assoc-in [:ice-stadiums :editing :lipas-id] (:lipas-id site)))))

(re-frame/reg-event-db
 ::set-edit-rev
 (fn [db [_ rev]]
   (assoc-in db [:ice-stadiums :editing :rev] (make-editable rev))))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (assoc-in db (into [:ice-stadiums] path) value)))

(defn- calculate-totals [data]
  {:electricity-mwh (reduce + (map :electricity-mwh (vals data)))
   :heat-mwh        (reduce + (map :heat-mwh (vals data)))
   :water-m3        (reduce + (map :water-m3 (vals data)))})

(re-frame/reg-event-db
 ::calculate-total-energy-consumption
 (fn [db _]
   (let [base-path    [:ice-stadiums :editing :rev]
         yearly-path  (conj base-path :energy-consumption)
         monthly-path (conj base-path :energy-consumption-monthly)
         monthly-data (get-in db monthly-path)]
     (if monthly-data
       (update-in db yearly-path #(calculate-totals monthly-data))
       db))))

(re-frame/reg-event-fx
 ::set-monthly-energy-consumption
 (fn [{:keys [db]} [_ args]]
   (let [basepath [:ice-stadiums :editing :rev :energy-consumption-monthly]
         path  (into basepath (butlast args))
         value (last args)]
     {:db (assoc-in db path value)
      :dispatch [::calculate-total-energy-consumption]})))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :ice-stadiums :dialogs dialog :data))]
     (-> db
         (update-in [:ice-stadiums :dialogs dialog :open?] not)
         (assoc-in [:ice-stadiums :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (let [empty-data (-> default-db :ice-stadiums :dialogs dialog)]
     (assoc-in db [:ice-stadiums :dialogs dialog] empty-data))))

(re-frame/reg-event-db
 ::save-renovation
 (fn [db [_ value]]
   (let [path [:ice-stadiums :editing :rev :renovations]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-rink
 (fn [db [_ value]]
   (let [path [:ice-stadiums :editing :rev :rinks]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::remove-renovation
 (fn [db [_ {:keys [id]}]]
   (update-in db [:ice-stadiums :editing :rev :renovations] dissoc id)))

(re-frame/reg-event-db
 ::remove-rink
 (fn [db [_ {:keys [id]}]]
   (update-in db [:ice-stadiums :editing :rev :rinks] dissoc id)))

(re-frame/reg-event-db
 ::display-site
 (fn [db [_ {:keys [lipas-id]}]]
   (let [site (get-in db [:sports-sites lipas-id])]
     (assoc-in db [:ice-stadiums :display-site] site))))
