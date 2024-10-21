(ns lipas.ui.swimming-pools.events
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::init
  (fn [_ _]
    {:dispatch-n
     [[:lipas.ui.sports-sites.events/get-by-type-code 3110]
      [:lipas.ui.sports-sites.events/get-by-type-code 3130]
      [::display-stats (dec utils/this-year)]]}))

(rf/reg-event-db ::set-active-tab
  (fn [db [_ active-tab]]
    (assoc-in db [:swimming-pools :active-tab] active-tab)))

(rf/reg-event-db ::toggle-dialog
  (fn [db [_ dialog data]]
    (let [data (or data (-> db :swimming-pools :dialogs dialog :data))]
      (-> db
          (update-in [:swimming-pools :dialogs dialog :open?] not)
          (assoc-in [:swimming-pools :dialogs dialog :data] data)))))

(rf/reg-event-db ::set-dialog-field
  (fn [db [_ dialog field value]]
    (let [path [:swimming-pools :dialogs dialog :data field]]
      (utils/set-field db path value))))

(rf/reg-event-db ::save-sauna
  (fn [db [_ lipas-id value]]
    (let [path (if lipas-id
                 [:sports-sites lipas-id :editing :saunas]
                 [:new-sports-site :data :saunas])]
      (utils/save-entity db path value))))

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

(rf/reg-event-db ::remove-sauna
  (fn [db [_ lipas-id {:keys [id]}]]
    (let [path (if lipas-id
                 [:sports-sites lipas-id :editing :saunas]
                 [:new-sports-site :data :saunas])]
      (update-in db path dissoc id))))

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
    (assoc-in db [:swimming-pools :dialogs dialog] {})))

(rf/reg-event-fx ::display-site
  (fn [{:keys [db]} [_ {:keys [lipas-id]}]]
    {:db         (assoc-in db [:swimming-pools :displaying] lipas-id)
     :dispatch-n
     [(when lipas-id
        [:lipas.ui.sports-sites.events/get-history lipas-id])
      (when lipas-id
        [:lipas.ui.events/navigate (str "/#/uimahalliportaali/hallit/" lipas-id)])
      (when-not lipas-id
        [:lipas.ui.events/navigate "/#/uimahalliportaali/hallit"])]
     :lipas.ui.effects/reset-scroll! nil}))

(rf/reg-event-fx ::display-stats
  (fn [{:keys [db]} [_ year]]
    {:db (assoc-in db [:swimming-pools :stats-year] year)
     :dispatch-n
     [[:lipas.ui.energy.events/fetch-energy-report year 3110]
      [:lipas.ui.energy.events/fetch-energy-report year 3130]]}))

(rf/reg-event-db ::filter-sites
  (fn [db [_ s]]
    (assoc-in db [:swimming-pools :sites-filter] s)))
