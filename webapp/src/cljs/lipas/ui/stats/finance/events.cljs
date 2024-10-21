(ns lipas.ui.stats.finance.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
   [re-frame.core :as rf]))

(rf/reg-event-db
 ::select-view
 (fn [db [_ view]]
   (assoc-in db [:stats :finance :selected-view] view)))

(rf/reg-event-fx
 ::select-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :finance :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(rf/reg-event-fx
 ::select-unit
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-unit] v)
    :dispatch [::create-report]}))

(rf/reg-event-fx
 ::select-year
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-year] v)
    :dispatch [::create-report]}))

(rf/reg-event-fx
 ::select-city-service
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-city-service] v)
    :dispatch [::create-report]}))

(rf/reg-event-fx
 ::select-grouping
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :finance :selected-grouping] v)
    :dispatch [::create-report]}))

(rf/reg-event-fx
 ::select-metrics
 (fn [{:keys [db]} [_ v]]
   {:db (assoc-in db [:stats :finance :selected-metrics] v)}))

(rf/reg-event-fx
 ::select-ranking-metric
 (fn [{:keys [db]} [_ v]]
   {:db (assoc-in db [:stats :finance :selected-ranking-metric] v)}))

(rf/reg-event-db
 ::toggle-chart-type
 (fn [db _]
   (let [oldv (-> db :stats :finance :chart-type)
         newv (if (= oldv "ranking") "comparison" "ranking")]
     (assoc-in db [:stats :finance :chart-type] newv))))

(rf/reg-event-fx
 ::clear-filters
 (fn [_ _]
   {:dispatch-n
    [[::select-cities []]
     [::create-report]]}))

(rf/reg-event-fx
 ::select-filters
 (fn [{:keys [db]} [_ {:keys [city-code avi-id province-id]} grouping]]
   (let [types-path    [:stats :finance :selected-types]
         cities-path   [:stats :finance :selected-cities]
         grouping-path [:stats :finance :selected-grouping]]
     {:db (cond-> db
            (= "avi" grouping)      (->
                                     (assoc-in cities-path
                                               (-> db
                                                   :cities-by-avi-id
                                                   (get avi-id)
                                                   (->> (map :city-code))))
                                     (assoc-in grouping-path "province")
                                     (assoc-in types-path []))
            (= "province" grouping) (->
                                     (assoc-in cities-path
                                               (-> db
                                                   :cities-by-province-id
                                                   (get province-id)
                                                   (->> (map :city-code))))
                                     (assoc-in types-path [])
                                     (assoc-in grouping-path "city")))
      :dispatch-n
      [[::create-report]]})))

(rf/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [params {:city-codes   (or (-> db :stats :finance :selected-cities not-empty)
                                   (-> db :cities keys))
                 :years        (-> db :stats :finance :selected-year vector)
                 :city-service (-> db :stats :finance :selected-city-service)
                 :unit         (-> db :stats :finance :selected-unit)
                 :grouping     (-> db :stats :finance :selected-grouping)}]
     {:dispatch [::create-report* params]})))

(defn- aggs-fields [unit city-service]
  (let [per-capita? (= "euros-per-capita" unit)]
    (merge
     {:population {:stats {:field :population}}}
     (->>
      [:net-costs :operating-expenses :operating-incomes :investments :subsidies
       ;; Added 2021->
       :operational-expenses :operational-income :surplus :deficit]
      (reduce
       (fn [m k]
         (let [f (keyword (str city-service (when per-capita? "-pc") "-" (name k)))]
           (assoc m k {:stats {:field f}})))
       {})))))

(defn- ->query
  [{:keys [city-codes years unit city-service grouping]}]
  (let [group-key (condp = grouping
                    "avi"      :avi-id
                    "province" :province-id
                    "city"     :city-code)]
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
      {:terms {:field group-key :size 400}
       :aggs
       {:by_year
        {:terms {:field :year :size 20}
         :aggs
         (aggs-fields unit city-service)}}}}}))

(rf/reg-event-fx
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

(rf/reg-event-db
 ::report-success
 (fn [db [_ data]]
   (-> db
       (assoc-in [:stats :finance :data] data))))

(rf/reg-event-fx
 ::download-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/city-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :tracker/event!                   ["stats" "download-excel" "finance"]})))
