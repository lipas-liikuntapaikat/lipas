(ns lipas.ui.events
  (:require [lipas.i18n.core :as i18n]
            [lipas.ui.db :as db]
            [lipas.ui.routes :as routes]
            [lipas.ui.utils :refer [==>]]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx :lipas.ui.local-storage/get :login-data)]
 (fn  [{:keys [local-storage]}]
   (if-let [login-data (:login-data local-storage)]
     (let [admin? (-> login-data :permissions :admin?)]
       {:db       (-> db/default-db
                      (assoc-in [:user :login] login-data)
                      (assoc :logged-in? true))
        :dispatch [:lipas.ui.login.events/refresh-login]
        :ga/set   [{:dimension1 (if admin? "admin" "user")}]})
     {:db     db/default-db
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
 (fn [db [_ locale]]
   (assoc db :translator (i18n/->tr-fn locale))))

(re-frame/reg-event-db
 ::set-active-notification
 (fn [db [_ notification]]
   (assoc db :active-notification notification)))

(re-frame/reg-event-db
 ::set-active-disclaimer
 (fn [db [_ disclaimer]]
   (assoc db :active-disclaimer disclaimer)))

(re-frame/reg-event-db
 ::confirmed
 (fn [db _]
   (assoc db :active-confirmation nil)))

(re-frame/reg-event-db
 ::confirm
 (fn [db [_ message on-confirm on-decline]]
   (let [tr           (:translator db)
         close        #(==> [:lipas.ui.events/confirmed])
         confirmation {:title         (tr :confirm/headline)
                       :message       message
                       :cancel-label  (tr :actions/cancel)
                       :decline-label (tr :confirm/no)
                       :confirm-label (tr :confirm/yes)
                       :on-confirm    (comp on-confirm close)
                       :on-decline    (when on-decline
                                        (comp on-decline close))
                       :on-cancel     close}]
     (assoc db :active-confirmation confirmation))))

(re-frame/reg-event-fx
 ::navigate
 (fn [_ [_ path & opts]]
   (apply routes/navigate! (into [path] opts))
   {}))

(re-frame/reg-event-fx
 ::display
 (fn [{:keys [db]} [_ lipas-id]]
   (let [latest    (get-in db [:sports-sites lipas-id :latest])
         site      (get-in db [:sports-sites lipas-id :history latest])
         type-code (-> site :type :type-code)
         path      (case type-code
                     (2510 2520) "jaahalliportaali/hallit"
                     (3110 3130) "uimahalliportaali/hallit"
                     "liikuntapaikat")]
     {:dispatch [::navigate (str "/#/" path "/" lipas-id)]})))

(re-frame/reg-event-fx
 ::report-energy-consumption
 (fn [{:keys [db]} [_ lipas-id]]
   (let [latest    (get-in db [:sports-sites lipas-id :latest])
         site      (get-in db [:sports-sites lipas-id :history latest])
         type-code (-> site :type :type-code)
         path      (case type-code
                     (2510 2520) "jaahalliportaali/ilmoita-tiedot"
                     (3110 3130) "uimahalliportaali/ilmoita-tiedot")]
     {:dispatch-n
      [[::navigate (str "/#/" path)]
       [:lipas.ui.energy.events/select-energy-consumption-site lipas-id]]})))
