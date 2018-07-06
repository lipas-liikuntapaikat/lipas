(ns lipas.ui.ice-stadiums.events
  (:require [lipas.ui.db :refer [default-db]]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(defn make-editable [ice-stadium]
  (-> ice-stadium
      (update-in [:rinks] utils/->indexed-map)))

(defn make-saveable [ice-stadium]
  (-> ice-stadium
      (update-in [:rinks] (comp utils/remove-ids vals))))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:ice-stadiums :active-tab] active-tab)))

(re-frame/reg-event-db
 ::set-edit-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:ice-stadiums :editing :site] lipas-id)))

(re-frame/reg-event-db
 ::select-energy-consumption-year
 (fn [db [_ year]]
   (let [lipas-id (-> db :ice-stadiums :editing :site)
         site     (get-in db [:sports-sites lipas-id])
         rev      (or (utils/find-revision site year)
                      (utils/make-revision site (str year)))]
     (-> db
         (assoc-in [:ice-stadiums :editing :year] year)
         (assoc-in [:ice-stadiums :editing :rev] rev)))))

(re-frame/reg-event-fx
 ::commit-energy-consumption
 (fn [{:keys [db]} [_ rev]]
   (let [tr (:translator db)]
     {:db       (utils/commit-energy-consumption db rev)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

(re-frame/reg-event-db
 ::edit-site
 (fn [db [_ {:keys [lipas-id]}]]
   (let [site      (get-in db [:sports-sites lipas-id])
         timestamp (utils/timestamp)
         rev       (utils/make-revision site timestamp)]
     (assoc-in db [:ice-stadiums :editing :rev] (make-editable rev)))))

(re-frame/reg-event-db
 ::save-edits
 (fn [db _]
   (let [rev (-> db :ice-stadiums :editing :rev make-saveable)]
     (utils/save-edits db rev))))

(re-frame/reg-event-db
 ::discard-edits
 (fn [db _]
   (let [lipas-id (-> db :ice-stadiums :editing :rev :lipas-id)]
     (utils/discard-edits db lipas-id))))

;; TODO do ajax request to backend and move this to success handler
(re-frame/reg-event-fx
 ::commit-edits
 (fn [{:keys [db]} _]
   (let [lipas-id (-> db :ice-stadiums :editing :rev :lipas-id)
         rev      (utils/latest-edit (get-in db [:sports-sites lipas-id :edits]))
         tr       (:translator db)]
     {:db       (utils/commit-edits db rev)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

;; TODO do ajax request to backend and move this to success handler
(re-frame/reg-event-fx
 ::commit-draft
 (fn [{:keys [db]} _]
   (let [lipas-id (-> db :ice-stadiums :editing :rev :lipas-id)
         rev      (utils/latest-edit (get-in db [:sports-sites lipas-id :edits]))
         draft    (assoc rev :status :draft)
         tr       (:translator db)]
     {:db       (utils/commit-edits db draft)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (assoc-in db (into [:ice-stadiums] path) value)))

(defn- calculate-totals [data]
  {:electricity-mwh (reduce + (map :electricity-mwh (vals data)))
   :heat-mwh        (reduce + (map :heat-mwh (vals data)))
   :cold-mwh        (reduce + (map :cold-mwh (vals data)))
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
 ::save-rink
 (fn [db [_ value]]
   (let [path [:ice-stadiums :editing :rev :rinks]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-rink
 (fn [db [_ {:keys [id]}]]
   (update-in db [:ice-stadiums :editing :rev :rinks] dissoc id)))

(re-frame/reg-event-db
 ::display-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:ice-stadiums :display-site] lipas-id)))
