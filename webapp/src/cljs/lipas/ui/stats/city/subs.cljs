(ns lipas.ui.stats.city.subs
  (:require
   [lipas.reports :as reports]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

;;; General ;;;

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :city :selected-cities)))

;;; City finance ;;;

(re-frame/reg-sub
 ::finance-city-services
 (fn [db _]
   (-> db :stats :city :finance :city-services)))

(re-frame/reg-sub
 ::selected-finance-city-service
 (fn [db _]
   (-> db :stats :city :finance :selected-city-service)))

(re-frame/reg-sub
 ::finance-metrics
 (fn [db _]
   (-> db :stats :city :finance :metrics)))

(re-frame/reg-sub
 ::selected-finance-metrics
 (fn [db _]
   (-> db :stats :city :finance :selected-metrics)))

(re-frame/reg-sub
 ::finance-units
 (fn [db _]
   (-> db :stats :city :finance :units)))

(re-frame/reg-sub
 ::selected-finance-unit
 (fn [db _]
   (-> db :stats :city :finance :selected-unit)))

(re-frame/reg-sub
 ::selected-finance-years
 (fn [db _]
   (-> db :stats :city :finance :selected-years)))

(re-frame/reg-sub
 ::finance-data*
 (fn [db _]
   (-> db :stats :city :finance :data)))

(re-frame/reg-sub
 ::selected-finance-view
 (fn [db _]
   (-> db :stats :city :finance :selected-view)))

(re-frame/reg-sub
 ::finance-labels
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

(defn get-averages [avgs service fields]
  (reduce
   (fn [m k]
     (let [v (get-in avgs [:services (keyword service) k :mean])
           k (keyword (str (name k) "-avg"))]
       (assoc m k v)))
   {} fields))

(defn ->entries [avgs service years res m]
  (let [fields (-> reports/stats-metrics keys (->> (map keyword)))]
    (into res
          (for [year years
                :let [service* (get-in m [:stats year :services (keyword service)])
                      avgs     (get avgs year)]]

            (merge
             (select-keys service* fields)
             (get-averages avgs service fields)
             {:city-code  (-> m :city-code)
              :year       year
              :population (get-in m [:stats year :population])})))))

(defn round-vals [m]
  (reduce
   (fn [m [k v]]
     (assoc m k (if (#{:year :population :city-code} k)
                  v
                  (utils/round-safe v))))
   {}
   m))

(re-frame/reg-sub
 ::finance-data
 :<- [::finance-data*]
 :<- [::selected-cities]
 :<- [::selected-finance-city-service]
 :<- [::selected-finance-unit]
 :<- [::selected-finance-years]
 (fn [[data city-codes service unit years] _]
   (let [cities  (-> data :cities (select-keys city-codes))
         avgs    (-> data :country)
         service (if (= "euros-per-capita" unit)
                   (str service "-pc")
                   service)]
     (->> cities
          vals
          (reduce (partial ->entries avgs service years) [])
          (map round-vals)
          (sort-by :year)))))

(re-frame/reg-sub
 ::finance-headers
 :<- [:lipas.ui.subs/translator]
 (fn [tr _]
   [[:year (tr :time/year)]
    [:city-code (tr :lipas.location/city-code)]
    [:population (tr :stats/population)]
    [:investments (tr :stats-metrics/investments)]
    [:investments-avg (str (tr :stats-metrics/investments) " "
                           (tr :stats/country-avg))]
    [:net-costs (tr :stats-metrics/net-costs)]
    [:net-costs-avg (str (tr :stats-metrics/net-costs) " "
                         (tr :stats/country-avg))]
    [:operating-expenses (tr :stats-metrics/operating-expenses)]
    [:operating-expenses-avg (str (tr :stats-metrics/operating-expenses) " "
                                  (tr :stats/country-avg))]
    [:operating-incomes (tr :stats-metrics/operating-incomes)]
    [:operating-incomes-avg (str (tr :stats-metrics/operating-incomes) " "
                                 (tr :stats/country-avg))]
    [:subsidies (tr :stats-metrics/subsidies)]
    [:subsidies-avg (str (tr :stats-metrics/subsidies) " "
                         (tr :stats/country-avg))]]))
