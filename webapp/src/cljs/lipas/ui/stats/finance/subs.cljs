(ns lipas.ui.stats.finance.subs
  (:require [lipas.reports :as reports]
            [lipas.ui.utils :as utils]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

(rf/reg-sub
  ::selected-view
  (fn [db _]
    (-> db :stats :finance :selected-view)))

(rf/reg-sub
  ::selected-cities
  (fn [db _]
    (-> db :stats :finance :selected-cities)))

(rf/reg-sub
  ::selected-year
  (fn [db _]
    (-> db :stats :finance :selected-year)))

(rf/reg-sub
  ::selected-unit
  (fn [db _]
    (-> db :stats :finance :selected-unit)))

(rf/reg-sub
  ::selected-city-service
  (fn [db _]
    (-> db :stats :finance :selected-city-service)))

(rf/reg-sub
  ::selected-metrics
  (fn [db _]
    (-> db :stats :finance :selected-metrics)))

(rf/reg-sub
  ::selected-grouping
  (fn [db _]
    (-> db :stats :finance :selected-grouping)))

(rf/reg-sub
  ::selected-ranking-metric
  (fn [db _]
    (-> db :stats :finance :selected-ranking-metric)))

(rf/reg-sub
  ::units
  (fn [db _]
    (-> db :stats :finance :units)))

(rf/reg-sub
  ::city-services
  (fn [db _]
    (-> db :stats :finance :city-services)))

(rf/reg-sub
  ::metrics
  (fn [db _]
    (-> db :stats :finance :metrics)))

(rf/reg-sub
  ::groupings
  (fn [db _]
    (-> db :stats :finance :groupings)))

(rf/reg-sub
  ::chart-type
  (fn [db _]
    (-> db :stats :finance :chart-type)))

(rf/reg-sub
  ::data*
  (fn [db _]
    (-> db :stats :finance :data)))

(defn ->int [x]
  (try
    (js/parseInt x)
    (catch js/Error _ nil)))

(rf/reg-sub
  ::data
  :<- [:lipas.ui.subs/translator]
  :<- [::data*]
  :<- [::selected-grouping]
  :<- [::selected-unit]
  :<- [:lipas.ui.sports-sites.subs/avi-areas]
  :<- [:lipas.ui.sports-sites.subs/provinces]
  :<- [:lipas.ui.stats.subs/cities]
  (fn [[tr data grouping unit avis provinces cities] _]
    (let [locale       (tr)
          op           (if (= unit "euros-per-capita")
                         (comp ->int utils/round-safe :avg)
                         :sum)
          get-avi      (fn [city-code]
                         (-> city-code cities :avi-id avis :name locale))
          get-province (fn [city-code]
                         (-> city-code cities :province-id provinces :name locale))]
      (->> data
           :aggregations
           :by_grouping
           :buckets
           (reduce
             (fn [res {:keys [key by_year]}]
               (let [k (if (= grouping "avi") :avi-id :province-id)]
                 (into res
                       (for [b    (:buckets by_year)
                             :let [region (condp = grouping
                                            "avi"      avis
                                            "province" provinces
                                            "city"     cities)]]
                         (merge
                           {k                     key
                            :year                 (:key b)
                            :region               (get-in region [key :name locale])
                            :count                (-> b :doc_count)
                            :operating-expenses   (-> b :operating-expenses op)
                            :operating-incomes    (-> b :operating-incomes op)
                            :net-costs            (-> b :net-costs op)
                            :subsidies            (-> b :subsidies op)
                            :investments          (-> b :investments op)
                            :population           (-> b :population :sum)
                            :operational-expenses (-> b :operational-expenses op)
                            :operational-income   (-> b :operational-income op)
                            :surplus              (-> b :surplus op)
                            :deficit              (-> b :deficit op abs)}
                           (when (= "city" grouping)
                             {:avi-name      (get-avi key)
                              :province-name (get-province key)}))))))
             [])
           (sort-by (if (= grouping "avi") :avi-id :province-id))))))

(rf/reg-sub
  ::ranking-data
  :<- [::data]
  :<- [::selected-ranking-metric]
  (fn [[data metric] _]
    (let [kw (keyword metric)]
      (sort-by #(-> % kw cutils/->number) utils/reverse-cmp data))))

(rf/reg-sub
  ::headers
  :<- [:lipas.ui.subs/translator]
  :<- [::selected-grouping]
  (fn [[tr grouping] _]
    (->>
      [[:region (tr :stats/region)]
       (when (= "city" grouping)
         [:province-name (tr :lipas.location/province)])
       (when (= "city" grouping)
         [:avi-name (tr :lipas.location/avi-area)])
       [:year (tr :time/year)]
     ;;[:city-code (tr :lipas.location/city-code)]
       [:population (tr :stats/population)]
       [:investments (tr :stats-metrics/investments)]
       [:net-costs (tr :stats-metrics/net-costs)]
       [:operating-expenses (tr :stats-metrics/operating-expenses)]
       [:operating-incomes (tr :stats-metrics/operating-incomes)]
       [:subsidies (tr :stats-metrics/subsidies)]
       [:operational-expenses (tr :stats-metrics/operational-expenses)]
       [:operational-income (tr :stats-metrics/operational-income)]
       [:surplus (tr :stats-metrics/surplus)]
       [:deficit (tr :stats-metrics/deficit)]]
      (remove nil?))))

(rf/reg-sub
  ::labels
  (fn [db _]
    (let [tr     (-> db :translator)
          locale (tr)]
      (reduce
        (fn [res [k v]]
          (let [k2 (str k "-avg")
                v2 (str (locale v) " " (tr :stats/country-avg))]
            (assoc res (keyword k) (locale v) (keyword k2) v2)))
        {:population (tr :stats/population)}
        reports/stats-metrics))))
