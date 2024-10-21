(ns lipas.ui.stats.age-structure.events
  (:require [ajax.core :as ajax]
            [lipas.ui.db :as db]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

;;; Age structure ;;;

(rf/reg-event-fx
 ::select-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :age-structure :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(rf/reg-event-fx
 ::select-types
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :age-structure :selected-types]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(rf/reg-event-fx
 ::select-grouping
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :age-structure :selected-grouping] v)
    :dispatch [::create-report]}))

(rf/reg-event-fx
 ::select-interval
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :age-structure :selected-interval] v)
    :dispatch [::create-report]}))

(rf/reg-event-fx
 ::clear-filters
 (fn [_]
   {:dispatch-n
    [[::select-cities []]
     [::select-types []]
     [::create-report]]}))

(defn ->query [city-codes type-codes grouping interval]
  (let [default-statuses (-> db/default-db :search :filters :statuses)]
    {:size 0,
     :query
     {:bool
      {:filter
       (remove nil?
               [{:terms {:status.keyword default-statuses}}
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
                     "admin" :admin.keyword)}}}]}}}}))

(rf/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [city-codes (-> db :stats :age-structure :selected-cities)
         type-codes (-> db :stats :age-structure :selected-types)
         grouping   (-> db :stats :age-structure :selected-grouping)
         interval   (-> db :stats :age-structure :selected-interval)]
     {:dispatch
      [::create-report* city-codes type-codes grouping interval]})))

(rf/reg-event-fx
 ::create-report*
 (fn [{:keys [db]} [_ city-codes type-codes grouping interval]]
   (let [query (->query city-codes type-codes grouping interval)]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/search")
       :params          query
       ;;:format          (ajax/transit-request-format)
       ;;:response-format (ajax/transit-response-format)
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::report-success]
       :on-failure      [:lipas.ui.stats.events/report-failure]}})))

(rf/reg-event-db
 ::report-success
 (fn [db [_ resp]]
   (let [stats (-> resp :aggregations)]
     (assoc-in db [:stats :age-structure :data] stats))))

(rf/reg-event-fx
 ::download-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/age-structure-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :tracker/event!                   ["stats" "download-excel" "age-structure"]})))

(rf/reg-event-db
 ::select-view
 (fn [db [_ v]]
   (assoc-in db [:stats :age-structure :selected-view] v)))
