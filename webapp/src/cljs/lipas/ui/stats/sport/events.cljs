(ns lipas.ui.stats.sport.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

;;; Sports stats ;;;

(re-frame/reg-event-fx
 ::select-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [ path  [:stats :sport :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(re-frame/reg-event-fx
 ::select-types
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :sport :selected-types]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(re-frame/reg-event-fx
 ::select-metric
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :sport :selected-metric] v)}))

(re-frame/reg-event-fx
 ::select-grouping
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :sport :selected-grouping] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::clear-filters
 (fn [_]
   {:dispatch-n
    [[::select-cities []]
     [::select-types []]
     [::create-report]]}))

(re-frame/reg-event-fx
 ::select-filters
 (fn [{:keys [db]} [_ {:keys [type-code city-code]} grouping]]
   (prn grouping)
   (let [city-k        "location.city.city-code"
         type-k        "type.type-code"
         types-path    [:stats :sport :selected-types]
         cities-path   [:stats :sport :selected-cities]
         grouping-path [:stats :sport :selected-grouping]]
     {:db (condp = grouping
            type-k (-> db
                       (assoc-in types-path [type-code])
                       (assoc-in grouping-path city-k)
                       (assoc-in cities-path []))
            city-k (-> db
                       (assoc-in cities-path [city-code])
                       (assoc-in grouping-path type-k)
                       (assoc-in types-path [])))
      :dispatch-n
      [[::create-report]]})))

(re-frame/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [city-codes (-> db :stats :sport :selected-cities)
         type-codes (-> db :stats :sport :selected-types)
         grouping   (-> db :stats :sport :selected-grouping)]
     {:dispatch [::create-report* city-codes type-codes grouping]})))

(defn ->query [city-codes type-codes grouping]
  (cond-> {}
    grouping   (assoc :grouping grouping)
    city-codes (assoc :city-codes city-codes)
    type-codes (assoc :type-codes type-codes)))

(re-frame/reg-event-fx
 ::create-report*
 (fn [{:keys [db]} [_ city-codes type-codes grouping]]
   (let [query (->query city-codes type-codes grouping)
         url   (str (:backend-url db) "/actions/calculate-stats")]
     {:http-xhrio
      {:method          :post
       :uri             url
       :params          query
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::report-success]
       :on-failure      [:lipas.ui.stats.events/report-failure]}})))

(re-frame/reg-event-db
 ::report-success
 (fn [db [_ resp]]
   (assoc-in db [:stats :sport :data] resp)))

(re-frame/reg-event-fx
 ::download-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/sports-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :ga/event                         ["stats" "download-excel" "sports-stats"]})))

(re-frame/reg-event-db
 ::select-view
 (fn [db [_ v]]
   (assoc-in db [:stats :sport :selected-view] v)))
