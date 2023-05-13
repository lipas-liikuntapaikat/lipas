(ns lipas.ui.login.events
  (:require
   [ajax.core :as ajax]
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
 ::select-login-mode
 (fn [db [_ login-mode]]
   (assoc-in db [:user :login-mode] login-mode)))

(re-frame/reg-event-db
 ::set-comeback-path
 (fn [db [_ path]]
   (if (= "/kirjaudu" path)
     (assoc db :comeback-path "/profiili")
     (assoc db :comeback-path path))))

(re-frame/reg-event-fx
 ::login-success
 (fn [{:keys [db]} [_ login-type body]]
   (let [admin?             (-> body :permissions :admin?)
         refresh-interval-s 900] ; 15 minutes
     (merge
      {:db (-> db
               (assoc-in [:logged-in?] true)
               (assoc-in [:user :login] body)
               (assoc-in [:analysis :diversity :user-category-presets]
                         (utils/index-by :name
                                         (get-in body [:user-data
                                                       :saved-diversity-settings
                                                       :category-presets]))))

       ::local-storage/set! [:login-data body]

       :dispatch-later
       [{:ms (* 1000 refresh-interval-s) :dispatch [::refresh-login]}]

       :dispatch-n
       [(when (= :magic-link login-type) [:lipas.ui.events/navigate "/profiili"])
        (when (not= :refresh login-type)
          [:lipas.ui.search.events/set-logged-in-filters])]}

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
   (update-in db [:user] dissoc :login-error :magic-link-ordered?)))

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
 (fn [{local-storage :local-storage db :db} _]
   (let [login-data (:login-data local-storage)]
     (if (or (empty? login-data) (not (:logged-in? db)))
       {}
       (let [token (-> login-data :token)]
         (if (utils/jwt-expired? token)
           {:dispatch [::logout]}
           {:http-xhrio
            {:method          :get
             :uri             (str (:backend-url db) "/actions/refresh-login")
             :headers         {:Authorization (str "Token " token)}
             :format          (ajax/json-request-format)
             :response-format (ajax/json-response-format {:keywords? true})
             :on-success      [::login-success :refresh]
             :on-failure      [::login-refresh-failure]}}))))))

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
   {:db (assoc db/default-db :backend-url (:backend-url db))

    ::local-storage/remove! :login-data

    :dispatch [:lipas.ui.events/navigate "/kirjaudu"]
    :ga/set   [{:dimension1 "logged-out"}]}))

(re-frame/reg-event-fx
 ::order-magic-link
 (fn [{db :db} [_ {:keys [email]}]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/order-magic-link")
     :params          {:email     email
                       :variant   :lipas
                       :login-url (str (utils/base-url) "/kirjaudu")}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::order-magic-link-success]
     :on-failure      [::order-magic-link-failed]}}))

(re-frame/reg-event-fx
 ::order-magic-link-success
 (fn [{:keys [db]} [_ result]]
   {:db       (assoc-in db [:user :magic-link-ordered?] true)
    :ga/event ["user" "magic-link-order-success"]}))

(re-frame/reg-event-fx
 ::order-magic-link-failed
 (fn [{:keys [db]} [_ result]]
   {:db       (assoc-in db [:user :login-error] result)
    :ga/event ["user" "magic-link-order-failed"]}))
