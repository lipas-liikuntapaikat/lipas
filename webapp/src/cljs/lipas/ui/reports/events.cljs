(ns lipas.ui.reports.events
  (:require
   [ajax.core :as ajax]
   [ajax.protocols :as ajaxp]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db _]
   (update-in db [:reports :dialog-open?] not)))

(re-frame/reg-event-db
 ::set-selected-fields
 (fn [db [_ v append?]]
   (if append?
     (update-in db [:reports :selected-fields] (comp set into) v)
     (assoc-in db [:reports :selected-fields] v))))

(re-frame/reg-event-fx
 ::create-report
 (fn [{:keys [db]} [_ query fields]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/create-sports-sites-report")
     :params          {:search-query query
                       :fields       fields}
     :format          (ajax/json-request-format)
     :response-format {:type         :blob
                       :content-type (-> cutils/content-type :xlsx)
                       :description  (-> cutils/content-type :xlsx)
                       :read         ajaxp/-body}
     :on-success      [::report-success]
     :on-failure      [::report-failure]}
    :db (assoc-in db [:reports :downloading?] true)}))

(re-frame/reg-event-fx
 ::report-success
 (fn [{:keys [db ]} [_ blob]]
   {:lipas.ui.effects/save-as! {:blob         blob
                                :filename     "lipas.xlsx"
                                :content-type (-> cutils/content-type :xlsx)}
    :db (assoc-in db [:reports :downloading?] false)}))

(re-frame/reg-event-fx
 ::report-failure
 (fn [{:keys [db]} [_ error]]
   ;; TODO display error msg
   (let [fatal? false]
     {:db           (assoc-in db [:reports :downloading?] false)
      :ga/exception [(:message error) fatal?]})))

(re-frame/reg-event-fx
 ::select-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:reports :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [(when-let [city-codes (not-empty (get-in new-db path))]
                     [::create-cities-report city-codes])]})))

(re-frame/reg-event-db
 ::select-metrics
 (fn [db [_ v append?]]
   (if append?
     (update-in db [:reports :selected-metrics] (comp set into) v)
     (assoc-in db [:reports :selected-metrics] v))))

(re-frame/reg-event-db
 ::select-city-service
 (fn [db [_ v]]
   (assoc-in db [:reports :selected-city-service] v)))

(re-frame/reg-event-db
 ::select-unit
 (fn [db [_ v]]
   (assoc-in db [:reports :selected-unit] v)))

(re-frame/reg-event-db
 ::select-years
 (fn [db [_ v]]
   (assoc-in db [:reports :selected-years] v)))

(re-frame/reg-event-fx
 ::create-cities-report
 (fn [{:keys [db]} [_ city-codes]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/create-cities-report")
     :params          {:city-codes city-codes}
     ;;:format          (ajax/json-request-format)
     ;;:response-format (ajax/json-response-format {:keywords? true})
     :format          (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :on-success      [::cities-report-success]
     :on-failure      [::report-failure]}}))

(re-frame/reg-event-db
 ::cities-report-success
 (fn [db [_ data]]
   (let [cities (-> data :data-points vals (->> (cutils/index-by :city-code)))]
     (-> db
         (update-in [:reports :stats :cities] merge cities)
         (assoc-in [:reports :stats :country] (:country-averages data))))))

(re-frame/reg-event-fx
 ::download-stats-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :reports/stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config})))
