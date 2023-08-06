(ns lipas.ui.sports-sites.activities.events
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::add-route
 (fn [{:keys [db]} [_ lipas-id]]
   {:db (assoc-in db [:sports-sites :activities :mode] :add-route)
    :fx [[:dispatch
          [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"]]]}))

(re-frame/reg-event-fx
 ::finish-route
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sports-sites :activities :mode] :route-details)
    :fx [#_[:dispatch [:lipas.ui.map.events/continue-editing]]]}))

(re-frame/reg-event-fx
 ::clear
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:sports-sites :activities :mode] :default)
            )}))

(re-frame/reg-event-fx
 ::finish-route-details
 (fn [{:keys [db]} [_ {:keys [fids route lipas-id]}]]
   (let [current-routes (get-in db [:sports-sites lipas-id :editing :activities :routes])]
     {:db (assoc-in db [:sports-sites :activities :mode] :default)
      :fx [[:dispatch [:lipas.ui.map.events/continue-editing]]
           [:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities :routes]
                       (conj (or current-routes [])
                             (assoc route :fids fids))]]]})))

(re-frame/reg-event-fx
 ::select-route
 (fn [{:keys [db]} [_ {:keys [fids] :as route}]]
   {:db db
    :fx [[:dispatch [:lipas.ui.map.events/highlight-features fids]]]}))
