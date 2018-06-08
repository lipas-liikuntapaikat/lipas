(ns lipas.ui.events
  (:require [re-frame.core :as re-frame]
            [lipas.ui.db :as db]))

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx :get-local-storage-value :login-data)]
 (fn  [{login-data :local-storage-value} _]
   (if login-data
     {:db (-> db/default-db
              (assoc-in [:user :login] login-data)
              (assoc :logged-in? true))}
     {:db db/default-db})))

(re-frame/reg-event-db
 ::set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/reg-event-db
 ::set-menu-anchor
 (fn [db [_ menu-anchor]]
   (assoc db :menu-anchor menu-anchor)))

(re-frame/reg-event-db
 ::toggle-drawer
 (fn [db [_ _]]
   (update db :drawer-open? not)))

(re-frame/reg-event-db
 ::set-translator
 (fn [db [_ translator]]
   (assoc db :translator translator)))

(re-frame/reg-event-fx
 ::logout
 [(re-frame/inject-cofx :remove-local-storage-value :login-data)]
 (fn [_  _]
   {:db db/default-db}))

(re-frame/reg-event-db
 ::set-active-notification
 (fn [db [_ notification]]
   (assoc db :active-notification notification)))
