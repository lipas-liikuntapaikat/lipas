(ns lipas.ui.events
  (:require [lipas.i18n.core :as i18n]
            [lipas.roles :as roles]
            [lipas.ui.db :as db]
            [lipas.ui.search.db :as search-db]
            [lipas.data.types :as types-data]
            [lipas.ui.utils :as utils :refer [==>]]
            [re-frame.core :as rf]
            [reitit.frontend.controllers :as rfc]))

(rf/reg-event-fx ::initialize-db
  [(rf/inject-cofx :lipas.ui.local-storage/get :login-data)]
  (fn  [{:keys [local-storage]}]
    (let [login-data (:login-data local-storage)
          ;; data in local-storage is edn and coerced to use keywords and sets after login already
          new-roles? (some? (:roles (:permissions login-data)))]
      (if (and login-data new-roles?)
        (let [admin? (roles/check-role login-data :admin)]
          {:db                     (-> db/default-db
                                       (assoc-in [:user :login] login-data)
                                       (assoc :logged-in? true)
                                       (assoc :search search-db/default-db-logged-in))
           :dispatch               [:lipas.ui.login.events/refresh-login]
           :tracker/set-dimension! [:user-type (if admin? "admin" "user")]})
        {:db                     db/default-db
         :tracker/set-dimension! [:user-type "guest"]}))))

(rf/reg-event-db ::set-backend-url
  (fn [db [_ url]]
    (assoc db :backend-url url)))

(rf/reg-event-db ::show-account-menu
  (fn [db [_ anchor]]
    (assoc db :account-menu-anchor anchor)))

(rf/reg-event-db ::open-drawer
  (fn [db [_ _]]
    (assoc db :drawer-open? true)))

(rf/reg-event-db ::close-drawer
  (fn [db [_ _]]
    (assoc db :drawer-open? false)))

(rf/reg-event-db ::set-translator
  (fn [db [_ locale]]
    (assoc db :translator (i18n/->tr-fn locale))))

(rf/reg-event-db ::set-active-notification
  (fn [db [_ notification]]
    (assoc db :active-notification notification)))

(rf/reg-event-db ::show-test-version-disclaimer
  (fn [db _]
    (let [tr (:translator db)]
      (assoc db :active-disclaimer (tr :disclaimer/test-version)))))

(rf/reg-event-db ::set-active-disclaimer
  (fn [db [_ disclaimer]]
    (assoc db :active-disclaimer disclaimer)))

(rf/reg-event-db ::confirmed
  (fn [db _]
    (assoc db :active-confirmation nil)))

(rf/reg-event-db ::confirm
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

(rf/reg-event-fx ::navigate
  (fn [_ [_ path & opts]]
    {:lipas.ui.effects/navigate! (into [path] opts)}))

(rf/reg-event-fx ::navigated
  (fn [{:keys [db]} [_ {:keys [path] :as new-match}]]
    (if new-match
      (let [old-match (:current-route db)
            ctrls     (rfc/apply-controllers (:controllers old-match) new-match)]
        {:db                 (assoc db :current-route (assoc new-match :controllers ctrls))
         :tracker/page-view! [path]})
      {})))

(rf/reg-event-fx ::display
  (fn [{:keys [db]} [_ lipas-id]]
    (let [latest    (get-in db [:sports-sites lipas-id :latest])
          site      (get-in db [:sports-sites lipas-id :history latest])
          type-code (-> site :type :type-code)
          path      (case type-code
                      (2510 2520) "jaahalliportaali/hallit"
                      (3110 3130) "uimahalliportaali/hallit"
                      "liikuntapaikat")]
      {:dispatch [::navigate (str "/#/" path "/" lipas-id)]})))

(rf/reg-event-fx ::report-energy-consumption
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

(rf/reg-event-db ::set-screen-size
  (fn [db [_ screen-size]]
    (assoc db :screen-size screen-size)))

(rf/reg-event-fx ::download-types-excel
  (fn [{:keys [db]} _]
    (let [types (get-in db [:sports-sites :active-types])
          headers types-data/excel-headers
          data (->> types
                    vals
                    (map types-data/->type)
                    (map types-data/->excel-row)
                    (sort-by (juxt :main-category :sub-category :type-code)))
          config {:filename "lipas_tyyppikoodit"
                  :sheet
                  {:data (utils/->excel-data headers data)}}]
      {:lipas.ui.effects/download-excel! config
       :tracker/event!                   ["stats" "download-excel" "types-excel"]})))
