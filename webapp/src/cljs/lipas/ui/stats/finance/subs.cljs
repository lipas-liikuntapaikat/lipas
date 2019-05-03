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
 ::data*
 (fn [db _]
   (-> db :stats :sport :data)))

(defn get-averages [avgs service fields]
  (reduce
   (fn [m k]
     (let [v (get-in avgs [:services (keyword service) k :mean])
           k (keyword (str (name k) "-avg"))]
       (assoc m k v)))
   {} fields))

(defn ->entries [avgs years res m]
  (let [fields (-> reports/stats-metrics keys (->> (map keyword)))]
    (into res
          (for [year years
                :let [youth (get-in m [:stats year :services :youth-services])
                      sport (get-in m [:stats year :services :sport-services])
                      avgs     (get avgs year)]]
            (merge
             (select-keys youth fields)
             (select-keys sport fields)
             (get-averages avgs youth fields)
             (get-averages avgs sport fields)
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
 ::data
 :<- [::data*]
 :<- [::selected-cities]
 :<- [::selected-years]
 (fn [[data city-codes years] _]
   (let [cities  (-> data :cities (select-keys city-codes))
         avgs    (-> data :country)]
     (->> cities
          vals
          (reduce (partial ->entries avgs years) [])
          (map round-vals)
          (sort-by :year)))))

(re-frame/reg-sub
 ::headers
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
