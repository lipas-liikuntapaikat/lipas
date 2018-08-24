(ns lipas.ui.login.events
  (:require [ajax.core :as ajax]
            [lipas.ui.db :as db]
            [lipas.ui.local-storage :refer [ls-set ls-get]]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (let [path (into [:user :login-form] path)]
     (assoc-in db path value))))

(re-frame/reg-event-db
 ::set-comeback-path
 (fn [db [_ path]]
   (assoc db :comeback-path path)))

(re-frame/reg-event-fx
 ::login-success
 [(re-frame/after (fn [db _]
                    (ls-set :login-data (-> db :user :login))))]
 (fn [{:keys [db]} [_ body]]
   (let [admin?             (-> body :permissions :admin?)
         refresh-interval-s 900] ; 15 minutes
     {:db       (-> db
                    (assoc-in [:logged-in?] true)
                    (assoc-in [:user :login] body))
      :dispatch-later
      [{:ms       (* 1000 refresh-interval-s)
        :dispatch [::refresh-login]}]
      :ga/set   [{:dimension1 (if admin? "admin" "user")}]
      :ga/event ["user" "login-success"]})))

(re-frame/reg-event-fx
 ::login-failure
 (fn [{:keys [db]} [_ result]]
   {:db       (assoc-in db [:user :login-error] result)
    :ga/event ["user" "login-failed"]}))

(re-frame/reg-event-db
 ::clear-errors
 (fn [db [_ _]]
   (update-in db [:user] dissoc :login-error)))

(re-frame/reg-event-fx
 ::submit-login-form
 (fn [{:keys [db]} [_ form-data]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/login")
     :headers         {:Authorization (utils/->basic-auth form-data)}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::login-success]
     :on-failure      [::login-failure]}
    :dispatch    [::clear-errors]}))

(re-frame/reg-event-fx
 ::refresh-login
 [(re-frame/inject-cofx :get-local-storage-value :login-data)]
 (fn [{login-data :local-storage-value
       db         :db} _]
   (if (or (empty? login-data) (not (:logged-in? db)))
     {}
     (let [token (-> login-data :token)]
       {:http-xhrio
        {:method          :get
         :uri             (str (:backend-url db) "/actions/refresh-login")
         :headers         {:Authorization (str "Token " token)}
         :format          (ajax/json-request-format)
         :response-format (ajax/json-response-format {:keywords? true})
         :on-success      [::login-success]
         :on-failure      [::logout]}}))))

(re-frame/reg-event-fx
 ::logout
 [(re-frame/inject-cofx :remove-local-storage-value :login-data)]
 (fn [{:keys [db]}  _]
   {:db       (-> db/default-db
                  (assoc :active-panel :login-panel)      ; avoid flickering
                  (assoc :backend-url (:backend-url db))) ; dev-time helper
    :dispatch [:lipas.ui.events/navigate "/#/kirjaudu"]
    :ga/set   [{:dimension1 "logged-out"}]}))
