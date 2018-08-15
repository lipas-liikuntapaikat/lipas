(ns lipas.ui.events
  (:require [lipas.ui.db :as db]
            [lipas.ui.routes :as routes]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx :get-local-storage-value :login-data)]
 (fn  [{login-data :local-storage-value} _]
   (if login-data
     (let [admin? (-> login-data :permissions :admin?)]
       {:db (-> db/default-db
                (assoc-in [:user :login] login-data)
                (assoc :logged-in? true))
        :dispatch [:lipas.ui.login.events/refresh-login]
        :ga/set [{:dimension1 (if admin? "admin" "user")}]})
     {:db db/default-db
      :ga/set [{:dimension1 "guest"}]})))

(re-frame/reg-event-db
 ::set-backend-url
 (fn [db [_ url]]
   (assoc db :backend-url url)))

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

(re-frame/reg-event-db
 ::set-active-notification
 (fn [db [_ notification]]
   (assoc db :active-notification notification)))

(re-frame/reg-event-fx
 ::navigate
 (fn [_ [_ path]]
   (routes/navigate! path)
   {}))
