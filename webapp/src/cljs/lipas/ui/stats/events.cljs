(ns lipas.ui.stats.events
  (:require
   [ajax.core :as ajax]
   [ajax.protocols :as ajaxp]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::select-tab
 (fn [db [_ v]]
   (assoc-in db [:stats :selected-tab] v)))

(re-frame/reg-event-fx
 ::report-failure
 (fn [{:keys [db]} [_ error]]
   ;; TODO display error msg
   (let [fatal? false]
     {:ga/exception [(:message error) fatal?]})))

(re-frame/reg-event-fx
 ::select-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n (when (not-empty (get-in new-db path))
                    [[::create-finance-report]
                     [::create-age-structure-report]])})))

(re-frame/reg-event-db
 ::select-finance-metrics
 (fn [db [_ v append?]]
   (if append?
     (update-in db [:stats :finance :selected-metrics] (comp set into) v)
     (assoc-in db [:stats :finance :selected-metrics] v))))

(re-frame/reg-event-db
 ::select-finance-city-service
 (fn [db [_ v]]
   (assoc-in db [:stats :finance :selected-city-service] v)))

(re-frame/reg-event-db
 ::select-finance-unit
 (fn [db [_ v]]
   (assoc-in db [:stats :finance :selected-unit] v)))

(re-frame/reg-event-db
 ::select-finance-years
 (fn [db [_ v]]
   (assoc-in db [:stats :finance :selected-years] v)))

(re-frame/reg-event-db
 ::select-finance-view-type
 (fn [db [_ v]]
   (assoc-in db [:stats :finance :view-type] v)))

(re-frame/reg-event-fx
 ::create-finance-report
 (fn [{:keys [db]} _]
   (let [city-codes (-> db :stats :selected-cities)]
     {:dispatch [::create-finance-report* city-codes]})))

(re-frame/reg-event-fx
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
       :on-failure      [::report-failure]}})))

(re-frame/reg-event-db
 ::finance-report-success
 (fn [db [_ data]]
   (let [cities (-> data :data-points vals (->> (cutils/index-by :city-code)))]
     (-> db
         (update-in [:stats :finance :data :cities] merge cities)
         (assoc-in [:stats  ::finance :data :country] (:country-averages data))))))

(re-frame/reg-event-fx
 ::download-finance-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/city-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config})))


(re-frame/reg-event-fx
 ::select-age-structure-grouping
 (fn [{:keys [db]} [_ v]]
   (let [city-codes (-> db :stats :selected-cities)]
     {:db       (assoc-in db [:stats :age-structure :selected-grouping] v)
      :dispatch [::create-age-structure-report city-codes v]})))

(defn ->age-structure-query [city-codes grouping]
  {:size 0,
   :query
   {:bool
    {:filter
     [{:terms {:status.keyword ["active"]}}
      {:terms {:location.city.city-code city-codes}}]}}
   :aggs
   {:years
    {:composite
     {:size 100,
      :sources
      [{:construction-year
        {:histogram {:field :construction-year, :interval 10}}}
       {:owner
        {:terms
         {:field (condp = grouping
                   "owner" :owner.keyword
                   "admin" :admin.keyword)}}}]}}}})

(re-frame/reg-event-fx
 ::create-age-structure-report
 (fn [{:keys [db]} _]
   (let [city-codes (-> db :stats :selected-cities)
         grouping   (-> db :stats :age-structure :selected-grouping)]
     {:dispatch [::create-age-structure-report* city-codes grouping]})))

(re-frame/reg-event-fx
 ::create-age-structure-report*
 (fn [{:keys [db]} [_ city-codes grouping]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/search")
     :params          (->age-structure-query city-codes grouping)
     ;;:format          (ajax/transit-request-format)
     ;;:response-format (ajax/transit-response-format)
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::age-structure-report-success]
     :on-failure      [::report-failure]}}))

(re-frame/reg-event-db
 ::age-structure-report-success
 (fn [db [_ resp]]
   (let [stats (-> resp :aggregations)]
     (assoc-in db [:stats :age-structure :data] stats))))
