(ns lipas.ui.swimming-pools.events
  (:require [lipas.ui.utils :as utils]
            [lipas.ui.swimming-pools.utils :as swim-utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:swimming-pools :active-tab] active-tab)))

(re-frame/reg-event-db
 ::select-energy-consumption-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:swimming-pools :editing :lipas-id] lipas-id)))

(re-frame/reg-event-db
 ::select-energy-consumption-year
 (fn [db [_ year]]
   (let [lipas-id (-> db :swimming-pools :editing :lipas-id)
         site     (get-in db [:sports-sites lipas-id])
         rev      (or (utils/find-revision site year)
                      (utils/make-revision site (utils/->timestamp year)))
         rev      (swim-utils/make-editable rev)]
     (-> db
         (assoc-in [:swimming-pools :editing :year] year)
         (assoc-in [:sports-sites lipas-id :editing] rev)))))

(re-frame/reg-event-fx
 ::commit-energy-consumption
 (fn [_ [_ rev]]
   (let [rev (swim-utils/make-saveable rev)]
     {:dispatch [:lipas.ui.sports-sites.events/commit-rev rev]})))

(re-frame/reg-event-db
 ::edit-site
 (fn [db [_ {:keys [lipas-id]}]]
   (let [site (get-in db [:sports-sites lipas-id])
         rev  (utils/make-revision site (utils/timestamp))]
     (-> db
         (assoc-in [:sports-sites lipas-id :editing] (swim-utils/make-editable rev))
         (assoc-in [:swimming-pools :editing?] true)
         (assoc-in [:swimming-pools :editing :lipas-id] lipas-id)))))

(re-frame/reg-event-db
 ::save-edits
 (fn [db _]
   (let [lipas-id (-> db :swimming-pools :editing :lipas-id)
         rev      (get-in db [:sports-sites lipas-id :editing])]
     (-> db
         (assoc-in [:swimming-pools :editing?] false)
         (utils/save-edits (swim-utils/make-saveable rev))))))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :swimming-pools :dialogs dialog :data))]
     (-> db
         (update-in [:swimming-pools :dialogs dialog :open?] not)
         (assoc-in [:swimming-pools :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::set-dialog-field
 (fn [db [_ dialog field value]]
   (let [path [:swimming-pools :dialogs dialog :data field]]
     (utils/set-field db path value))))

(re-frame/reg-event-db
 ::save-sauna
 (fn [db [_ value]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)
         path [:sports-sites lipas-id :editing :saunas]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::save-pool
 (fn [db [_ value]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)
         path [:sports-sites lipas-id :editing :pools]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::save-slide
 (fn [db [_ value]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)
         path [:sports-sites lipas-id :editing :slides]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-sauna
 (fn [db [_ {:keys [id]}]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)]
     (update-in db [:sports-sites lipas-id :editing :saunas] dissoc id))))

(re-frame/reg-event-db
 ::remove-pool
 (fn [db [_ {:keys [id]}]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)]
     (update-in db [:sports-sites lipas-id :editing :pools] dissoc id))))

(re-frame/reg-event-db
 ::remove-slide
 (fn [db [_ {:keys [id]}]]
   (let [lipas-id (-> db :ice-stadiums :editing :lipas-id)]
     (update-in db [:sports-sites lipas-id :editing :slides] dissoc id))))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (assoc-in db [:swimming-pools :dialogs dialog] {})))

(re-frame/reg-event-fx
 ::display-site
 (fn [{:keys [db]} [_ {:keys [lipas-id]}]]
   {:db         (assoc-in db [:swimming-pools :display-site] lipas-id)
    :dispatch-n [(when lipas-id
                   [:lipas.ui.sports-sites.events/get-history lipas-id])]}))
