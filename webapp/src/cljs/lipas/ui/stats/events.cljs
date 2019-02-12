(ns lipas.ui.stats.events
  (:require
   [ajax.core :as ajax]
   [ajax.protocols :as ajaxp]
   [lipas.ui.utils :as utils]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

;;; General ;;;

(re-frame/reg-event-fx
 ::navigate
 (fn [_ [_ v]]
   (let [route (keyword :lipas.ui.routes.stats v)]
     {:dispatch [:lipas.ui.events/navigate route]})))

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

;;; Finance ;;;

(re-frame/reg-event-fx
 ::select-finance-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n (when (not-empty (get-in new-db path))
                    [[::create-finance-report]])})))

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
 ::select-finance-view
 (fn [db [_ v]]
   (assoc-in db [:stats :finance :selected-view] v)))

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
         (assoc-in [:stats :finance :data :country] (:country-averages data))))))

(re-frame/reg-event-fx
 ::download-finance-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/city-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :ga/event                         ["stats" "download-excel" "finance"]})))

;;; Age structure ;;;

(re-frame/reg-event-fx
 ::select-age-structure-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :age-structure :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-age-structure-report]]})))

(re-frame/reg-event-fx
 ::select-age-structure-types
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :age-structure :selected-types]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-age-structure-report]]})))

(re-frame/reg-event-fx
 ::select-age-structure-grouping
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :age-structure :selected-grouping] v)
    :dispatch [::create-age-structure-report]}))

(re-frame/reg-event-fx
 ::select-age-structure-interval
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :age-structure :selected-interval] v)
    :dispatch [::create-age-structure-report]}))

(re-frame/reg-event-fx
 ::clear-age-structure-filters
 (fn [_]
   {:dispatch-n
    [[::select-age-structure-cities []]
     [::select-age-structure-types []]
     [::create-age-structure-report]]}))

(defn ->age-structure-query [city-codes type-codes grouping interval]
  {:size 0,
   :query
   {:bool
    {:filter
     (remove nil?
             [{:terms {:status.keyword ["active"]}}
              (when (not-empty city-codes)
                {:terms {:location.city.city-code city-codes}})
              (when (not-empty type-codes)
                {:terms {:type.type-code type-codes}})])}}
   :aggs
   {:years
    {:composite
     {:size 1000,
      :sources
      [{:construction-year
        {:histogram {:field :construction-year, :interval interval}}}
       {:owner
        {:terms
         {:field (condp = grouping
                   "owner" :owner.keyword
                   "admin" :admin.keyword)}}}]}}}})

(re-frame/reg-event-fx
 ::create-age-structure-report
 (fn [{:keys [db]} _]
   (let [city-codes (-> db :stats :age-structure :selected-cities)
         type-codes (-> db :stats :age-structure :selected-types)
         grouping   (-> db :stats :age-structure :selected-grouping)
         interval   (-> db :stats :age-structure :selected-interval)]
     {:dispatch
      [::create-age-structure-report* city-codes type-codes grouping interval]})))

(re-frame/reg-event-fx
 ::create-age-structure-report*
 (fn [{:keys [db]} [_ city-codes type-codes grouping interval]]
   (let [query (->age-structure-query city-codes type-codes grouping interval)]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/search")
       :params          query
       ;;:format          (ajax/transit-request-format)
       ;;:response-format (ajax/transit-response-format)
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::age-structure-report-success]
       :on-failure      [::report-failure]}})))

(re-frame/reg-event-db
 ::age-structure-report-success
 (fn [db [_ resp]]
   (let [stats (-> resp :aggregations)]
     (assoc-in db [:stats :age-structure :data] stats))))

(re-frame/reg-event-fx
 ::download-age-structure-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/age-structure-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :ga/event                         ["stats" "download-excel" "age-structure"]})))

(re-frame/reg-event-db
 ::select-age-structure-view
 (fn [db [_ v]]
   (assoc-in db [:stats :age-structure :selected-view] v)))

;;; Sports stats ;;;

(re-frame/reg-event-fx
 ::select-sports-stats-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [ path  [:stats :sports-stats :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-sports-stats-report]]})))

(re-frame/reg-event-fx
 ::select-sports-stats-types
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :sports-stats :selected-types]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-sports-stats-report]]})))

(re-frame/reg-event-fx
 ::select-sports-stats-metric
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :sports-stats :selected-metric] v)
    ;;:dispatch [::create-sports-stats-report]
    }))

(re-frame/reg-event-fx
 ::clear-sports-stats-filters
 (fn [_]
   {:dispatch-n
    [[::select-sports-stats-cities []]
     [::select-sports-stats-types []]
     [::create-sports-stats-report]]}))

(re-frame/reg-event-fx
 ::create-sports-stats-report
 (fn [{:keys [db]} _]
   (let [city-codes (-> db :stats :sports-stats :selected-cities)
         type-codes (-> db :stats :sports-stats :selected-types)]
     {:dispatch [::create-sports-stats-report* city-codes type-codes]})))

(defn ->sports-stats-query [city-codes type-codes]
  (cond-> {}
    city-codes (assoc :city-codes city-codes)
    type-codes (assoc :type-codes type-codes)))

(re-frame/reg-event-fx
 ::create-sports-stats-report*
 (fn [{:keys [db]} [_ city-codes type-codes]]
   (let [query (->sports-stats-query city-codes type-codes)
         url   (str (:backend-url db) "/actions/create-m2-per-capita-report")]
     {:http-xhrio
      {:method          :post
       :uri             url
       :params          query
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::sports-stats-report-success]
       :on-failure      [::report-failure]}})))

(re-frame/reg-event-db
 ::sports-stats-report-success
 (fn [db [_ resp]]
   (assoc-in db [:stats :sports-stats :data] resp)))

(re-frame/reg-event-fx
 ::download-sports-stats-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/sports-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :ga/event                         ["stats" "download-excel" "sports-stats"]})))

(re-frame/reg-event-db
 ::select-sports-stats-view
 (fn [db [_ v]]
   (assoc-in db [:stats :sports-stats :selected-view] v)))
