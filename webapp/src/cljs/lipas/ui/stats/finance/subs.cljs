(ns lipas.ui.stats.finance.subs
  (:require
   [lipas.reports :as reports]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :finance :selected-cities)))

(re-frame/reg-sub
 ::selected-years
 (fn [db _]
   (-> db :stats :finance :selected-years)))

(re-frame/reg-sub
 ::selected-unit
 (fn [db _]
   (-> db :stats :finance :selected-unit)))

(re-frame/reg-sub
 ::selected-city-service
 (fn [db _]
   (-> db :stats :finance :selected-city-service)))

(re-frame/reg-sub
 ::selected-grouping
 (fn [db _]
   (-> db :stats :finance :selected-grouping)))

(re-frame/reg-sub
 ::units
 (fn [db _]
   (-> db :stats :finance :units)))

(re-frame/reg-sub
 ::city-services
 (fn [db _]
   (-> db :stats :finance :city-services)))

(re-frame/reg-sub
 ::groupings
 (fn [db _]
   (-> db :stats :finance :groupings)))

(re-frame/reg-sub
 ::data*
 (fn [db _]
   (-> db :stats :finance :data)))

(defn round-vals [m]
  (reduce
   (fn [m [k v]]
     (assoc m k (if (#{:year :population :city-code} k)
                  v
                  (utils/round-safe v))))
   {}
   m))

(re-frame/reg-sub
 ::data
 :<- [:lipas.ui.subs/translator]
 :<- [::data*]
 :<- [::selected-grouping]
 :<- [:lipas.ui.sports-sites.subs/avi-areas]
 :<- [:lipas.ui.sports-sites.subs/provinces]
 (fn [[tr data grouping avis provinces] _]
   (let [locale (tr)]
     (->> data
          :aggregations
          :by_grouping
          :buckets
          (reduce
           (fn [res {:keys [key by_year]}]
             (let [k (if (= grouping "avi") :avi-id :province-id)]
               (into res
                     (for [b (:buckets by_year)]
                       {k                   key
                        :year               (:key b)
                        :region             (if (= grouping "avi")
                                              (get-in avis [key :name locale])
                                              (get-in provinces [key :name locale]))
                        :count              (-> b :doc_count)
                        :operating-expenses (-> b :operating-expenses :sum)
                        :operating-incomes  (-> b :operating-incomes :sum)
                        :net-costs          (-> b :net-costs :sum)
                        :subsidies          (-> b :subsidies :sum)
                        :investments        (-> b :investments :sum)
                        :population         (-> b :population :sum)}))))
           [])))))

(re-frame/reg-sub
 ::headers
 :<- [:lipas.ui.subs/translator]
 (fn [tr _]
   [[:region (tr :stats/region)]
    [:year (tr :time/year)]
    ;;[:city-code (tr :lipas.location/city-code)]
    [:population (tr :stats/population)]
    [:investments (tr :stats-metrics/investments)]
    [:net-costs (tr :stats-metrics/net-costs)]
    [:operating-expenses (tr :stats-metrics/operating-expenses)]
    [:operating-incomes (tr :stats-metrics/operating-incomes)]
    [:subsidies (tr :stats-metrics/subsidies)]]))

(re-frame/reg-sub
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
