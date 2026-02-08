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
                   (let [data (or data (-> db :sports-sites :hall-equipment :dialogs dialog :data))]
                     (-> db
                         (update-in [:sports-sites :hall-equipment :dialogs dialog :open?] not)
                         (assoc-in [:sports-sites :hall-equipment :dialogs dialog :data] data)))))

(rf/reg-event-db ::set-dialog-field
                 (fn [db [_ dialog field value]]
                   (let [path [:sports-sites :hall-equipment :dialogs dialog :data field]]
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
                   (assoc-in db [:sports-sites :hall-equipment :dialogs dialog] {})))

;; Subs — Layer 1

(rf/reg-sub ::hall-equipment
            (fn [db _]
              (-> db :sports-sites :hall-equipment)))

;; Subs — Layer 2

(rf/reg-sub ::dialogs
            :<- [::hall-equipment]
            (fn [hall-equipment _]
              (:dialogs hall-equipment)))

(rf/reg-sub ::pool-form
            :<- [::hall-equipment]
            (fn [hall-equipment _]
              (-> hall-equipment :dialogs :pool :data)))

(rf/reg-sub ::slide-form
            :<- [::hall-equipment]
            (fn [hall-equipment _]
              (-> hall-equipment :dialogs :slide :data)))

(rf/reg-sub ::pool-types
            :<- [::hall-equipment]
            (fn [hall-equipment _]
              (:pool-types hall-equipment)))

(rf/reg-sub ::accessibility
            :<- [::hall-equipment]
            (fn [hall-equipment _]
              (:accessibility hall-equipment)))

(rf/reg-sub ::size-categories
            :<- [::hall-equipment]
            (fn [hall-equipment _]
              (:size-categories hall-equipment)))
