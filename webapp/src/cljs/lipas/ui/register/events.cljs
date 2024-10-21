(ns lipas.ui.register.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [lipas.ui.db :refer [default-db]]
            [re-frame.core :as rf]))

(rf/reg-event-db
  ::clear-errors
  (fn [db [_ _]]
    (update-in db [:user] dissoc :registration-error)))

(rf/reg-event-fx
  ::set-registration-form-field
  (fn [{:keys [db]} [_ path value]]
    (let [path (into [:user :registration-form] path)]
      {:db       (assoc-in db path value)
       :dispatch [::clear-errors]})))

(rf/reg-event-db
  ::set-registration-form-email
  (fn [db [_ email]]
    (let [username (-> email
                       (str/split #"@")
                       first)]
      (-> db
          (assoc-in [:user :registration-form :email] email)
          (assoc-in [:user :registration-form :username] username)))))

(rf/reg-event-fx
  ::registration-success
  (fn [{:keys [db]} [_ result]]
    (let [empty-form (-> default-db :user :registration-form)]
      {:db             (-> db
                           (assoc-in [:user :registration] result)
                           (assoc-in [:user :registration-form] empty-form))
       :tracker/event! ["user" "registered"]})))

(rf/reg-event-db
  ::registration-failure
  (fn [db [_ result]]
    (assoc-in db [:user :registration-error] result)))

(rf/reg-event-fx
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

(rf/reg-event-db
  ::reset-form
  (fn [db _]
    (update-in db [:user] dissoc :registration :registration-error)))
