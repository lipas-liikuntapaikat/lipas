(ns lipas.ui.events
  (:require [lipas.ui.db :as db]
            [lipas.ui.routes :as routes]
            [lipas.ui.utils :refer [==>]]
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
 ::show-account-menu
 (fn [db [_ anchor]]
   (assoc db :account-menu-anchor anchor)))

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

(re-frame/reg-event-db
 ::confirmed
 (fn [db _]
   (assoc db :active-confirmation nil)))

(re-frame/reg-event-db
 ::confirm
 (fn [db [_ message on-confirm]]
   (let [tr           (:translator db)
         close        #(==> [:lipas.ui.events/confirmed])
         confirmation {:title         (tr :confirm/headline)
                       :message       message
                       :cancel-label  (tr :confirm/no)
                       :confirm-label (tr :confirm/yes)
                       :on-confirm    (comp on-confirm close)
                       :on-cancel     close}]
     (assoc db :active-confirmation confirmation))))

(re-frame/reg-event-fx
 ::navigate
 (fn [_ [_ path & opts]]
   (apply routes/navigate! (into [path] opts))
   {}))
