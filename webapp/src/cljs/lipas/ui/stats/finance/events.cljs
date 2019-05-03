(ns lipas.ui.stats.finance.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [city-codes (or (-> db :stats :finance :selected-cities not-empty)
                        (-> db :cities keys))]
     {:dispatch [::create-report* city-codes]})))

(re-frame/reg-event-fx
 ::create-report*
 (fn [{:keys [db]} [_ city-codes]]
   (let [url (str (:backend-url db) "/actions/create-finance-report")]
     {:http-xhrio
      {:method          :post
       :uri             url
       :params          {:city-codes city-codes}
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::report-success]
       :on-failure      [:lipas.ui.stats.events/report-failure]}})))

(re-frame/reg-event-db
 ::report-success
 (fn [db [_ data]]
   (let [cities (-> data :data-points vals (->> (cutils/index-by :city-code)))]
     (-> db
         (update-in [:stats :finance :data :cities] merge cities)
         (assoc-in [:stats :finance :data :country] (:country-averages data))))))

(re-frame/reg-event-fx
 ::download-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/city-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :ga/event                         ["stats" "download-excel" "finance"]})))
