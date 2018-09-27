(ns lipas.ui.map.events
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-view
 (fn [db [_ lat lon zoom]]
   (-> db
       (assoc-in [:map :center] {:lat lat :lon lon})
       (assoc-in [:map :zoom] zoom))))

(re-frame/reg-event-db
 ::set-center
 (fn [db [_ lat lon]]
   (assoc-in db [:map :center] {:lat lat :lon lon})))

(re-frame/reg-event-db
 ::set-zoom
 (fn [db [_ zoom]]
   (assoc-in db [:map :zoom] zoom)))

(re-frame/reg-event-db
 ::select-basemap
 (fn [db [_ basemap]]
   (assoc-in db [:map :basemap] basemap)))

(re-frame/reg-event-db
 ::toggle-filter
 (fn [db [_ filter]]
   (update-in db [:map :filters filter] not)))

(re-frame/reg-event-db
 ::show-popup
 (fn [db [_ feature]]
   (assoc-in db [:map :popup] feature)))

(re-frame/reg-event-db
 ::show-sports-site
 (fn [db [_ lipas-id]]
   (assoc-in db [:map :sports-site] lipas-id)))
