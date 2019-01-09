(ns lipas.ui.ice-stadiums.events
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:ice-stadiums :active-tab] active-tab)))

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
 (fn [db [_ lipas-id value]]
   (let [path [:sports-sites lipas-id :editing :rinks]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-rink
 (fn [db [_ lipas-id {:keys [id]}]]
   (update-in db [:sports-sites lipas-id :editing :rinks] dissoc id)))

(re-frame/reg-event-fx
 ::display-site
 (fn [{:keys [db]} [_ {:keys [lipas-id]}]]
   {:db       (assoc-in db [:ice-stadiums :display-site] lipas-id)
    :dispatch-n
    [(when lipas-id
       [:lipas.ui.sports-sites.events/get-history lipas-id])
     (when lipas-id
       [:lipas.ui.events/navigate (str "/#/jaahalliportaali/hallit/" lipas-id)])
     (when-not lipas-id
       [:lipas.ui.events/navigate "/#/jaahalliportaali/hallit"])]
    :lipas.ui.effects/reset-scroll! nil}))

(re-frame/reg-event-fx
 ::display-stats
 (fn [{:keys [db]} [_ year]]
   {:db (assoc-in db [:ice-stadiums :stats-year] year)
    :dispatch-n
    [[:lipas.ui.energy.events/fetch-energy-report year 2510]
     [:lipas.ui.energy.events/fetch-energy-report year 2520]]}))
