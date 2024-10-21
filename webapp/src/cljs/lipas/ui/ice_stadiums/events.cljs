(ns lipas.ui.ice-stadiums.events
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-fx
 ::init
 (fn [_ _]
   {:dispatch-n
    [[:lipas.ui.sports-sites.events/get-by-type-code 2510]
     [:lipas.ui.sports-sites.events/get-by-type-code 2520]
     [::display-stats (dec utils/this-year)]]}))

(rf/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:ice-stadiums :active-tab] active-tab)))

(rf/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :ice-stadiums :dialogs dialog :data))]
     (-> db
         (update-in [:ice-stadiums :dialogs dialog :open?] not)
         (assoc-in [:ice-stadiums :dialogs dialog :data] data)))))

(rf/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (assoc-in db [:ice-stadiums :dialogs dialog] {})))

(rf/reg-event-db
 ::set-dialog-field
 (fn [db [_ dialog field value]]
   (let [path [:ice-stadiums :dialogs dialog :data field]]
     (utils/set-field db path value))))

(rf/reg-event-db
 ::save-rink
 (fn [db [_ lipas-id value]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing :rinks]
                [:new-sports-site :data :rinks])]
     (utils/save-entity db path value))))

(rf/reg-event-db
 ::remove-rink
 (fn [db [_ lipas-id {:keys [id]}]]
   (let [path (if lipas-id
                [:sports-sites lipas-id :editing :rinks]
                [:new-sports-site :data :inks])]
     (update-in db path dissoc id))))

(rf/reg-event-fx
 ::display-site
 (fn [{:keys [db]} [_ {:keys [lipas-id]}]]
   {:db       (assoc-in db [:ice-stadiums :display-site] lipas-id)
    :dispatch-n
    [(when lipas-id
       [:lipas.ui.sports-sites.events/get-history lipas-id])
     (when lipas-id
       [:lipas.ui.events/navigate
        :lipas.ui.routes.ice-stadiums/details-view {:lipas-id lipas-id}])
     (when-not lipas-id
       [:lipas.ui.events/navigate :lipas.ui.routes.ice-stadiums/list-view])]
    :lipas.ui.effects/reset-scroll! nil}))

(rf/reg-event-fx
 ::display-stats
 (fn [{:keys [db]} [_ year]]
   {:db (assoc-in db [:ice-stadiums :stats-year] year)
    :dispatch-n
    [[:lipas.ui.energy.events/fetch-energy-report year 2510]
     [:lipas.ui.energy.events/fetch-energy-report year 2520]]}))

(rf/reg-event-db
 ::filter-sites
 (fn [db [_ s]]
   (assoc-in db [:ice-stadiums :sites-filter] s)))
