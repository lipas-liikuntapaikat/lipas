(ns lipas.ui.stats.finance.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::select-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :finance :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(re-frame/reg-event-fx
 ::select-unit
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-unit] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::select-years
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-years] v)
    :dispatch [::create-report]}))


(re-frame/reg-event-fx
 ::select-city-service
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-city-service] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::select-grouping
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-grouping] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::clear-filters
 (fn [_ _]
   {:dispatch-n
    [[::select-cities []]
     [::create-repeort]]}))

(re-frame/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [params {:city-codes   (or (-> db :stats :finance :selected-cities not-empty)
                                   (-> db :cities keys))
                 :years        (-> db :stats :finance :selected-years)
                 :city-service (-> db :stats :finance :selected-city-service)
                 :unit         (-> db :stats :finance :selected-unit)
                 :grouping     (-> db :stats :finance :selected-grouping)}]
     {:dispatch [::create-report* params]})))

(defn- aggs-fields [unit city-service]
  (let [per-capita? (= "euros-per-capita" unit)]
    (merge
     {:population {:stats {:field :population}}}
     (->>
      [:net-costs :operating-expenses :operating-incomes :investments :subsidies]
      (reduce
       (fn [m k]
         (let [f (keyword (str city-service (when per-capita? "-pc") "-" (name k)))]
           (assoc m k {:stats {:field f}})))
       {})))))

(defn- ->query
  [{:keys [city-codes years unit city-service grouping]}]
  {:size 0
   :query
   {:bool
    {:filter
     (into [] (remove nil?)
           [(when (not-empty years)
              {:terms {:year years}})
            (when (not-empty city-codes)
              {:terms {:city-code city-codes}})])}}
   :aggs
   {:by_grouping
    {:terms {:field (if (= "avi" grouping) :avi-id :province-id) :size 20}
     :aggs
     {:by_year
      {:terms {:field :year :size 20}
       :aggs
       (aggs-fields unit city-service)}}}}})

(re-frame/reg-event-fx
 ::create-report*
 (fn [{:keys [db]} [_ params]]
   (let [body (->query params)
         url  (str (:backend-url db) "/actions/query-finance-report")]
     {:http-xhrio
      {:method          :post
       :uri             url
       :params          body
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::report-success]
       :on-failure      [:lipas.ui.stats.events/report-failure]}})))

(re-frame/reg-event-db
 ::report-success
 (fn [db [_ data]]
   (-> db
       (assoc-in [:stats :finance :data] data))))

(re-frame/reg-event-fx
 ::download-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/city-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :ga/event                         ["stats" "download-excel" "finance"]})))
