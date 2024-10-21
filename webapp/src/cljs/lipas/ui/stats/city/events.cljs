(ns lipas.ui.stats.city.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

;;; General ;;;

(rf/reg-event-fx
  ::select-cities
  (fn [{:keys [db]} [_ v append?]]
    (let [path   [:stats :city :selected-cities]
          new-db (if append?
                   (update-in db path (comp set into) v)
                   (assoc-in db path v))]
      {:db         new-db
       :dispatch-n (when (not-empty (get-in new-db path))
                     [[::create-finance-report]])})))

;;; Finance ;;;

(rf/reg-event-db
  ::select-finance-metrics
  (fn [db [_ v append?]]
    (if append?
      (update-in db [:stats :city :finance :selected-metrics] (comp set into) v)
      (assoc-in db [:stats :city :finance :selected-metrics] v))))

(rf/reg-event-db
  ::select-finance-city-service
  (fn [db [_ v]]
    (assoc-in db [:stats :city :finance :selected-city-service] v)))

(rf/reg-event-db
  ::select-finance-unit
  (fn [db [_ v]]
    (assoc-in db [:stats :city :finance :selected-unit] v)))

(rf/reg-event-db
  ::select-finance-years
  (fn [db [_ v]]
    (assoc-in db [:stats :city :finance :selected-years] v)))

(rf/reg-event-db
  ::select-finance-view
  (fn [db [_ v]]
    (assoc-in db [:stats :city :finance :selected-view] v)))

(rf/reg-event-fx
  ::create-report
  (fn [_ _]
    {:dispatch-n [[::create-finance-report]]}))

(rf/reg-event-fx
  ::create-finance-report
  (fn [{:keys [db]} _]
    (let [city-codes (-> db :stats :city :selected-cities)]
      {:dispatch [::create-finance-report* city-codes]})))

(rf/reg-event-fx
  ::create-finance-report*
  (fn [{:keys [db]} [_ city-codes]]
    (let [url (str (:backend-url db) "/actions/create-finance-report")]
      {:http-xhrio
       {:method          :post
        :uri             url
        :params          {:city-codes city-codes}
        :format          (ajax/transit-request-format)
        :response-format (ajax/transit-response-format)
        :on-success      [::finance-report-success]
        :on-failure      [:lipas.ui.stats.events/report-failure]}})))

(rf/reg-event-db
  ::finance-report-success
  (fn [db [_ data]]
    (let [cities (-> data :data-points vals (->> (cutils/index-by :city-code)))]
      (-> db
          (update-in [:stats :city :finance :data :cities] merge cities)
          (assoc-in [:stats :city :finance :data :country] (:country-averages data))))))

(rf/reg-event-fx
  ::download-finance-excel
  (fn [{:keys [db]} [_ data headers]]
    (let [tr     (:translator db)
          config {:filename (tr :stats/city-stats)
                  :sheet
                  {:data (utils/->excel-data headers data)}}]
      {:lipas.ui.effects/download-excel! config
       :tracker/event!                   ["stats" "download-excel" "city-finance"]})))
