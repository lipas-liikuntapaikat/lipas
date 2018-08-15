(ns lipas.ui.register.events
  (:require [re-frame.core :as re-frame]
            [lipas.ui.db :refer [default-db]]
            [lipas.ui.login.events :as login-events]
            [ajax.core :as ajax]))

(re-frame/reg-event-db
 ::clear-errors
 (fn [db [_ _]]
   (update-in db [:user] dissoc :registration-error)))

(re-frame/reg-event-db
 ::set-registration-form-field
 (fn [db [_ path value]]
   (let [path (into [:user :registration-form] path)]
     (assoc-in db path value))))

(re-frame/reg-event-fx
 ::registration-success
 (fn [{:keys [db]} [_ result]]
   (let [username   (-> db :user :registration-form :username)
         password   (-> db :user :registration-form :password)
         empty-form (-> default-db :user :registration-form)]
     {:db (-> db
           (assoc-in [:user :registration] result)
           (assoc-in [:user :registration-form] empty-form))
      :dispatch [::login-events/submit-login-form {:username username
                                                   :password password}]
      :ga/event ["user" "registered"]})))

(re-frame/reg-event-db
 ::registration-failure
 (fn [db [_ result]]
   (assoc-in db [:user :registration-error] result)))

(re-frame/reg-event-fx
 ::submit-registration-form
 (fn [{:keys [db]} [_ form-data]]
   {:http-xhrio {:method          :post
                 :uri             (str (:backend-url db) "/actions/register")
                 :params          form-data
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [::registration-success]
                 :on-failure      [::registration-failure]}}))
