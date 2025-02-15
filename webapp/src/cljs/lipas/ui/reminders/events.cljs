(ns lipas.ui.reminders.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [lipas.ui.db :as db]
            [re-frame.core :as rf]))

(rf/reg-event-db ::toggle-dialog
  (fn [db _]
    (update-in db [:reminders :dialog-open?] not)))

(rf/reg-event-db ::clear-form
  (fn [db _]
    (assoc-in db [:reminders :form] (-> db/default-db :reminders :form))))

(rf/reg-event-db ::set-message
  (fn [db [_ message]]
    (assoc-in db [:reminders :form :body :message] message)))

(rf/reg-event-fx ::set-date
  (fn [{:keys [db]} [_ date]]
    {:db       (assoc-in db [:reminders :form :date] date)
     :dispatch [::set-event-date]}))

(rf/reg-event-fx ::set-time
  (fn [{:keys [db]} [_ time]]
    {:db       (assoc-in db [:reminders :form :time] time)
     :dispatch [::set-event-date]}))

(rf/reg-event-db ::set-event-date
  (fn [db _]
    (let [date (-> db :reminders :form :date)
          time (-> db :reminders :form :time)]
      (assoc-in db [:reminders :form :event-date] (str date "T" time)))))

(rf/reg-event-fx ::select-option
  (fn [_ [_ event-date]]
    (let [date (-> event-date (str/split "T") first)]
      {:dispatch [::set-date date]})))

(rf/reg-event-fx ::add
  (fn [_ [_ message]]
    {:dispatch-n
     [[::toggle-dialog]
      [::set-message message]
      [::set-date nil]]}))

(rf/reg-event-db ::fetch-upcoming-success
  (fn [db [_  data]]
    (assoc-in db [:reminders :data] data)))

(rf/reg-event-fx ::fetch-upcoming-failure
  (fn [_ [_ _resp]]
    {:tracker/event! ["error" "fetch-upcoming-reminders-failure"]}))

(rf/reg-event-fx ::fetch-upcoming-reminders
  (fn [{:keys [db]} [_ reminder]]
    {:http-xhrio
     {:method          :post
      :params          reminder
      :uri             (str (:backend-url db) "/actions/get-upcoming-reminders")
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::fetch-upcoming-success]
      :on-failure      [::fetch-upcoming-failure]}}))

(rf/reg-event-fx ::save
  (fn [{:keys [db]} [_ reminder]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method          :post
        :params          reminder
        :uri             (str (:backend-url db) "/actions/add-reminder")
        :headers         {:Authorization (str "Token " token)}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::save-success]
        :on-failure      [::save-failure]}})))

(rf/reg-event-fx ::save-success
  (fn [{:keys [db]} [_  data]]
    (let [tr (:translator db)]
      {:db (assoc-in db [:reminders :data] data)
       :dispatch-n
       [[::toggle-dialog]
        [::clear-form]
        [:lipas.ui.events/set-active-notification
         {:message  (tr :notifications/save-success)
          :success? true}]]})))

(rf/reg-event-fx ::save-failure
  (fn [{:keys [db]} [_ _resp]]
    (let [tr (:translator db)]
      {:dispatch       [:lipas.ui.events/set-active-notification
                        {:message  (tr :notifications/save-failed)
                         :success? false}]
       :tracker/event! ["error" "save-reminder-failure"]})))
