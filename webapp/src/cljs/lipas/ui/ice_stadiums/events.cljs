(ns lipas.ui.ice-stadiums.events
  (:require [lipas.ui.utils :as utils]
            [lipas.ui.ice-stadiums.utils :as ice-utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:ice-stadiums :active-tab] active-tab)))

(re-frame/reg-event-db
 ::select-energy-consumption-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:ice-stadiums :editing :lipas-id] lipas-id)))

(re-frame/reg-event-db
 ::select-energy-consumption-year
 (fn [db [_ year]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)
         site     (get-in db [:sports-sites lipas-id])
         rev      (or (utils/find-revision site year)
                      (utils/make-revision site (utils/->timestamp year)))
         rev      (ice-utils/make-editable rev)]
     (-> db
         (assoc-in [:ice-stadiums :editing :year] year)
         (assoc-in [:sports-sites lipas-id :editing] rev)))))

(re-frame/reg-event-db
 ::edit-site
 (fn [db [_ {:keys [lipas-id]}]]
   (let [site      (get-in db [:sports-sites lipas-id])
         rev       (utils/make-revision site (utils/timestamp))]
     (-> db
         (assoc-in [:sports-sites lipas-id :editing] (ice-utils/make-editable rev))
         (assoc-in [:ice-stadiums :editing :lipas-id] lipas-id)
         (assoc-in [:ice-stadiums :editing?] true)))))

(re-frame/reg-event-db
 ::save-edits
 (fn [db _]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)
         rev      (get-in db [:sports-sites lipas-id :editing])]
     (-> db
         (assoc-in [:ice-stadiums :editing?] false)
         (utils/save-edits (ice-utils/make-saveable rev))))))

(defn- calculate-totals [data]
  {:electricity-mwh (reduce + (map :electricity-mwh (vals data)))
   :heat-mwh        (reduce + (map :heat-mwh (vals data)))
   :cold-mwh        (reduce + (map :cold-mwh (vals data)))
   :water-m3        (reduce + (map :water-m3 (vals data)))})

(re-frame/reg-event-db
 ::calculate-total-energy-consumption
 (fn [db [_ lipas-id]]
   (let [base-path    [:sports-sites lipas-id :editing]
         yearly-path  (conj base-path :energy-consumption)
         monthly-path (conj base-path :energy-consumption-monthly)
         monthly-data (get-in db monthly-path)]
     (if monthly-data
       (update-in db yearly-path #(calculate-totals monthly-data))
       db))))

(re-frame/reg-event-fx
 ::set-monthly-energy-consumption
 (fn [{:keys [db]} [_ lipas-id month field value]]
   (let [basepath [:sports-sites lipas-id :editing :energy-consumption-monthly]
         path  (into basepath [month field])]
     {:db (assoc-in db path value)
      :dispatch [::calculate-total-energy-consumption lipas-id]})))

(re-frame/reg-event-fx
 ::commit-energy-consumption
 (fn [_ [_ rev]]
   (let [rev (ice-utils/make-saveable rev)]
     {:dispatch [:lipas.ui.sports-sites.events/commit-rev rev]})))

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
   (assoc-in db [:ice-stadiums :dialogs dialog] {})))

(re-frame/reg-event-db
 ::set-dialog-field
 (fn [db [_ dialog field value]]
   (let [path [:ice-stadiums :dialogs dialog :data field]]
     (utils/set-field db path value))))

(re-frame/reg-event-db
 ::save-rink
 (fn [db [_ value]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)
         path [:sports-sites lipas-id :editing :rinks]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-rink
 (fn [db [_ {:keys [id]}]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)]
     (update-in db [:sports-sites lipas-id :editing :rinks] dissoc id))))

(re-frame/reg-event-db
 ::display-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:ice-stadiums :display-site] lipas-id)))
