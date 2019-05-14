(ns lipas.ui.stats.subsidies.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
   [cljs.pprint :as pprint]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::select-view
 (fn [db [_ view]]
   (assoc-in db [:stats :subsidies :selected-view] view)))

(re-frame/reg-event-fx
 ::select-cities
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :subsidies :selected-cities]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(re-frame/reg-event-fx
 ::select-types
 (fn [{:keys [db]} [_ v append?]]
   (let [path   [:stats :subsidies :selected-types]
         new-db (if append?
                  (update-in db path (comp set into) v)
                  (assoc-in db path v))]
     {:db         new-db
      :dispatch-n [[::create-report]]})))

(re-frame/reg-event-fx
 ::select-issuers
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :subsidies :selected-issuers] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::select-years
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :subsidies :selected-years] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::select-owners
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :subsidies :selected-owners] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::select-grouping
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:stats :subsidies :selected-grouping] v)
    :dispatch [::create-report]}))

(re-frame/reg-event-fx
 ::select-metrics
 (fn [{:keys [db]} [_ v]]
   {:db (assoc-in db [:stats :subsidies :selected-metrics] v)}))

(re-frame/reg-event-db
 ::toggle-chart-type
 (fn [db _]
   (let [oldv (-> db :stats :subsidies :chart-type)
         newv (if (= oldv "ranking") "comparison" "ranking")]
     (assoc-in db [:stats :subsidies :chart-type] newv))))

(re-frame/reg-event-fx
 ::clear-filters
 (fn [_ _]
   {:dispatch-n
    [[::select-cities []]
     [::select-types []]
     [::create-report]]}))

(re-frame/reg-event-fx
 ::create-report
 (fn [{:keys [db]} _]
   (let [params {:city-codes (-> db :stats :subsidies :selected-cities)
                 :type-codes (-> db :stats :subsidies :selected-types)
                 :years      (-> db :stats :subsidies :selected-years)
                 :grouping   (-> db :stats :subsidies :selected-grouping)
                 :issuers    (-> db :stats :subsidies :selected-issuers)}]
     {:dispatch [::create-report* params]})))

(defn- ->query
  [{:keys [city-codes type-codes years grouping issuers]}]
  (let [group-key (condp = grouping
                    "avi"      :avi-id
                    "province" :province-id
                    "city"     :city-code
                    "type"     :type-codes)]
    {:size 0
     :query
     {:bool
      {:filter
       (into [] (remove nil?)
             [(when (not-empty years))
              {:terms {:year years}}
              (when (not-empty city-codes)
                {:terms {:city-code city-codes}})
              (when (not-empty type-codes)
                {:terms {:type-codes type-codes}})
              (when (not-empty issuers)
                {:terms {:issuer.keyword issuers}})])}}
     :aggs
     {:by_grouping
      {:terms {:field group-key :size 400}
       :aggs
       {:by_year
        {:terms {:field :year :size 20}
         :aggs
         {:amount {:stats {:field :amount}}}}}}}}))

(re-frame/reg-event-fx
 ::create-report*
 (fn [{:keys [db]} [_ params]]
   (let [body (->query params)
         url  (str (:backend-url db) "/actions/query-subsidies")]
     ;;p(pprint/pprint body)
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
       (assoc-in [:stats :subsidies :data] data))))

(re-frame/reg-event-fx
 ::download-excel
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :stats/city-stats)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config
      :ga/event                         ["stats" "download-excel" "subsid"]})))

(re-frame/reg-event-fx
 ::select-filters
 (fn [{:keys [db]} [_ {:keys [type-code city-code avi-id province-id]} grouping]]
   (let [types-path    [:stats :subsidies :selected-types]
         cities-path   [:stats :subsidies :selected-cities]
         grouping-path [:stats :subsidies :selected-grouping]]
     {:db (cond-> db
            (= "type" grouping)     (->
                                     (assoc-in types-path [type-code])
                                     (assoc-in cities-path []))
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
                                     (assoc-in grouping-path "city"))
            (= "city" grouping)     (->
                                     (assoc-in cities-path [city-code])
                                     (assoc-in grouping-path "type")
                                     (assoc-in types-path [])))
      :dispatch-n
      [[::create-report]]})))
