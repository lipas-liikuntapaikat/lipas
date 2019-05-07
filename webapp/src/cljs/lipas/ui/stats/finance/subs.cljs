(ns lipas.ui.stats.finance.subs
  (:require
   [lipas.reports :as reports]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::selected-view
 (fn [db _]
   (-> db :stats :finance :selected-view)))

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :finance :selected-cities)))

(re-frame/reg-sub
 ::selected-year
 (fn [db _]
   (-> db :stats :finance :selected-year)))

(re-frame/reg-sub
 ::selected-unit
 (fn [db _]
   (-> db :stats :finance :selected-unit)))

(re-frame/reg-sub
 ::selected-city-service
 (fn [db _]
   (-> db :stats :finance :selected-city-service)))

(re-frame/reg-sub
 ::selected-metrics
 (fn [db _]
   (-> db :stats :finance :selected-metrics)))

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
 ::metrics
 (fn [db _]
   (-> db :stats :finance :metrics)))

(re-frame/reg-sub
 ::groupings
 (fn [db _]
   (-> db :stats :finance :groupings)))

(re-frame/reg-sub
 ::data*
 (fn [db _]
   (-> db :stats :finance :data)))

(re-frame/reg-sub
 ::data
 :<- [:lipas.ui.subs/translator]
 :<- [::data*]
 :<- [::selected-grouping]
 :<- [::selected-unit]
 :<- [:lipas.ui.sports-sites.subs/avi-areas]
 :<- [:lipas.ui.sports-sites.subs/provinces]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 (fn [[tr data grouping unit avis provinces cities] _]
   (let [locale (tr)
         op     (if (= unit "euros-per-capita")
                  (comp utils/round-safe :avg)
                  :sum)]
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
                       {k                   key
                        :year               (:key b)
                        :region             (get-in region [key :name locale])
                        :count              (-> b :doc_count)
                        :operating-expenses (-> b :operating-expenses op)
                        :operating-incomes  (-> b :operating-incomes op)
                        :net-costs          (-> b :net-costs op)
                        :subsidies          (-> b :subsidies op)
                        :investments        (-> b :investments op)
                        :population         (-> b :population :sum)}))))
           [])
          (sort-by (if (= grouping "avi") :avi-id :province-id))))))

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
