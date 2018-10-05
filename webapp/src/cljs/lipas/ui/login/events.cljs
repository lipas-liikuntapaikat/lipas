(ns lipas.ui.login.events
  (:require [ajax.core :as ajax]
            [lipas.ui.db :as db]
            [lipas.ui.utils :as utils]
            [lipas.ui.local-storage :as local-storage]
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
 (fn [{:keys [db]} [_ login-type body]]
   (let [admin?             (-> body :permissions :admin?)
         refresh-interval-s 900] ; 15 minutes
     (merge
      {:db (-> db
               (assoc-in [:logged-in?] true)
               (assoc-in [:user :login] body))

       ::local-storage/set! [:login-data body]

       :dispatch-later
       [{:ms       (* 1000 refresh-interval-s)
         :dispatch [::refresh-login]}]
       :dispatch-n
       [(when (= :magic-link login-type)
          [:lipas.ui.events/navigate "/#/profiili"])]}
      (when (not= :refresh login-type)
        {:ga/set   [{:dimension1 (if admin? "admin" "user")}]
         :ga/event ["user" "login-success"]})))))

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
     :on-success      [::login-success :login]
     :on-failure      [::login-failure]}
    :dispatch    [::clear-errors]}))

(re-frame/reg-event-fx
 ::login-refresh-failure
 (fn [_ [_ {:keys [status] :as resp}]]
   (if (#{401 403} status)
     {:dispatch [::logout]}
     {})))

(re-frame/reg-event-fx
 ::refresh-login
 [(re-frame/inject-cofx ::local-storage/get :login-data)]
 (fn [{local-storage :local-storage
       db            :db} _]
   (let [login-data (:login-data local-storage)]
     (if (or (empty? login-data) (not (:logged-in? db)))
       {}
       (let [token (-> login-data :token)]
         {:http-xhrio
          {:method          :get
           :uri             (str (:backend-url db) "/actions/refresh-login")
           :headers         {:Authorization (str "Token " token)}
           :format          (ajax/json-request-format)
           :response-format (ajax/json-response-format {:keywords? true})
           :on-success      [::login-success :refresh]
           :on-failure      [::login-refresh-failure]}})))))

(re-frame/reg-event-fx
 ::login-with-magic-link
 (fn [{db :db} [_ token]]
   {:http-xhrio
    {:method          :get
     :uri             (str (:backend-url db) "/actions/refresh-login")
     :headers         {:Authorization (str "Token " token)}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::login-success :magic-link]
     :on-failure      [::logout]}
    :ga/event ["user" "magic-link-opened"]}))

(re-frame/reg-event-fx
 ::logout
 (fn [{:keys [db]}  _]
   {:db (-> db/default-db
            (assoc :active-panel :login-panel)
            (assoc :backend-url (:backend-url db)))

    ::local-storage/remove! :login-data

    :dispatch [:lipas.ui.events/navigate "/#/kirjaudu"]
    :ga/set   [{:dimension1 "logged-out"}]}))
