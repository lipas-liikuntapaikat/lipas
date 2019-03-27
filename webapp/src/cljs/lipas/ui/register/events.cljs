(ns lipas.ui.register.events
  (:require
   [re-frame.core :as re-frame]
   [clojure.string :as string]
   [lipas.ui.db :refer [default-db]]
   [ajax.core :as ajax]))

(re-frame/reg-event-db
 ::clear-errors
 (fn [db [_ _]]
   (update-in db [:user] dissoc :registration-error)))

(re-frame/reg-event-fx
 ::set-registration-form-field
 (fn [{:keys [db]} [_ path value]]
   (let [path (into [:user :registration-form] path)]
     {:db       (assoc-in db path value)
      :dispatch [::clear-errors]})))

(re-frame/reg-event-db
 ::set-registration-form-email
 (fn [db [_ email]]
   (let [username (-> email
                      (string/split #"@")
                      first)]
     (-> db
         (assoc-in [:user :registration-form :email] email)
         (assoc-in [:user :registration-form :username] username)))))

(re-frame/reg-event-fx
 ::registration-success
 (fn [{:keys [db]} [_ result]]
   (let [empty-form (-> default-db :user :registration-form)]
     {:db (-> db
           (assoc-in [:user :registration] result)
           (assoc-in [:user :registration-form] empty-form))
      :ga/event ["user" "registered"]})))

(re-frame/reg-event-db
 ::registration-failure
 (fn [db [_ result]]
   (assoc-in db [:user :registration-error] result)))

(re-frame/reg-event-fx
 ::submit-registration-form
 (fn [{:keys [db]} [_ form-data]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/register")
     :params          form-data
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::registration-success]
     :on-failure      [::registration-failure]}}))

(re-frame/reg-event-db
 ::reset-form
 (fn [db _]
   (update-in db [:user] dissoc :registration :registration-error)))
