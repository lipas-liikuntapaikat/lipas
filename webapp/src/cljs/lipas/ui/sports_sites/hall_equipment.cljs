(ns lipas.ui.sports-sites.hall-equipment
  (:require [lipas.data.ice-stadiums :as ice-data]
            [lipas.data.swimming-pools :as swim-data]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(def default-db
  {:pool-types      swim-data/pool-types
   :accessibility   swim-data/accessibility
   :size-categories ice-data/size-categories
   :dialogs {:pool  {:open? false}
             :slide {:open? false}}})

;; Events

(rf/reg-event-db ::toggle-dialog
                 (fn [db [_ dialog data]]
                   (let [data (or data (-> db :hall-equipment :dialogs dialog :data))]
                     (-> db
                         (update-in [:hall-equipment :dialogs dialog :open?] not)
                         (assoc-in [:hall-equipment :dialogs dialog :data] data)))))

(rf/reg-event-db ::set-dialog-field
                 (fn [db [_ dialog field value]]
                   (let [path [:hall-equipment :dialogs dialog :data field]]
                     (utils/set-field db path value))))

(rf/reg-event-db ::save-pool
                 (fn [db [_ lipas-id value]]
                   (let [path (if lipas-id
                                [:sports-sites lipas-id :editing :pools]
                                [:new-sports-site :data :pools])]
                     (utils/save-entity db path value))))

(rf/reg-event-db ::save-slide
                 (fn [db [_ lipas-id value]]
                   (let [path (if lipas-id
                                [:sports-sites lipas-id :editing :slides]
                                [:new-sports-site :data :slides])]
                     (utils/save-entity db path value))))

(rf/reg-event-db ::remove-pool
                 (fn [db [_ lipas-id {:keys [id]}]]
                   (let [path (if lipas-id
                                [:sports-sites lipas-id :editing :pools]
                                [:new-sports-site :data :pools])]
                     (update-in db path dissoc id))))

(rf/reg-event-db ::remove-slide
                 (fn [db [_ lipas-id {:keys [id]}]]
                   (let [path (if lipas-id
                                [:sports-sites lipas-id :editing :slides]
                                [:new-sports-site :data :slides])]
                     (update-in db path dissoc id))))

(rf/reg-event-db ::reset-dialog
                 (fn [db [_ dialog]]
                   (assoc-in db [:hall-equipment :dialogs dialog] {})))

;; Subs

(rf/reg-sub ::dialogs
            (fn [db _]
              (-> db :hall-equipment :dialogs)))

(rf/reg-sub ::pool-form
            (fn [db _]
              (-> db :hall-equipment :dialogs :pool :data)))

(rf/reg-sub ::slide-form
            (fn [db _]
              (-> db :hall-equipment :dialogs :slide :data)))

(rf/reg-sub ::pool-types
            (fn [db _]
              (-> db :hall-equipment :pool-types)))

(rf/reg-sub ::accessibility
            (fn [db _]
              (-> db :hall-equipment :accessibility)))

(rf/reg-sub ::size-categories
            (fn [db _ _]
              (-> db :hall-equipment :size-categories)))
