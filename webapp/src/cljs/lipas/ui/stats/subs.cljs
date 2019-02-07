(ns lipas.ui.stats.subs
  (:require
   [lipas.reports :as reports]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::selected-tab
 (fn [db _]
   (-> db :stats :selected-tab)))

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :selected-cities)))

(re-frame/reg-sub
 ::finance-city-services
 (fn [db _]
   (-> db :stats :finance :city-services)))

(re-frame/reg-sub
 ::selected-finance-city-service
 (fn [db _]
   (-> db :stats :finance :selected-city-service)))

(re-frame/reg-sub
 ::finance-metrics
 (fn [db _]
   (-> db :stats :finance :metrics)))

(re-frame/reg-sub
 ::selected-finance-metrics
 (fn [db _]
   (-> db :stats :finance :selected-metrics)))

(re-frame/reg-sub
 ::finance-units
 (fn [db _]
   (-> db :stats :finance :units)))

(re-frame/reg-sub
 ::selected-finance-unit
 (fn [db _]
   (-> db :stats :finance :selected-unit)))

(re-frame/reg-sub
 ::selected-finance-years
 (fn [db _]
   (-> db :stats :finance :selected-years)))

(re-frame/reg-sub
 ::finance-data*
 (fn [db _]
   (-> db :stats :finance :data)))

(re-frame/reg-sub
 ::finance-view-type
 (fn [db _]
   (-> db :stats :finance :view-type)))

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
      {}
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
             {:city-code (-> m :city-code)
              :year      year})))))

(defn round-vals [m]
  (reduce
   (fn [m [k v]]
     (assoc m k (if (and (number? v) (not= :year k) (not= :city-code k))
                  (.toFixed v 2)
                  v)))
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
 ::selected-age-structure-cities
 (fn [db _]
   (-> db :stats :age-structure :selected-cities)))

(re-frame/reg-sub
 ::selected-age-structure-types
 (fn [db _]
   (-> db :stats :age-structure :selected-types)))

(re-frame/reg-sub
 ::age-structure-groupings
 (fn [db _]
   (-> db :stats :age-structure :groupings)))

(re-frame/reg-sub
 ::selected-age-structure-grouping
 (fn [db _]
   (-> db :stats :age-structure :selected-grouping)))

(re-frame/reg-sub
 ::selected-age-structure-interval
 (fn [db _]
   (-> db :stats :age-structure :selected-interval)))

(re-frame/reg-sub
 ::age-structure-data*
 (fn [db _]
   (-> db :stats :age-structure :data)))

(re-frame/reg-sub
 ::age-structure-data
 :<- [::age-structure-data*]
 (fn [data _]
   (let [data (-> data :years :buckets)]
     (->> data
          (reduce
           (fn [m v]
             (let [year  (-> v :key :construction-year)
                   owner (-> v :key :owner)
                   count (-> v :doc_count)]
               (-> m
                   (assoc-in [year owner] count)
                   (assoc-in [year :construction-year] year))))
           {})
          vals
          (sort-by :construction-year)))))

(re-frame/reg-sub
 ::age-structure-labels
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 (fn [[tr admins owners] _]
   (let [locale (tr)]
     (merge
      (into {} (map (juxt first (comp locale second)) admins))
      (into {} (map (juxt first (comp locale second)) owners))
      {:y-axis (tr :stats/sports-sites-count)}))))

(re-frame/reg-sub
 ::selected-sports-stats-cities
 (fn [db _]
   (-> db :stats :sports-stats :selected-cities)))

(re-frame/reg-sub
 ::selected-sports-stats-types
 (fn [db _]
   (-> db :stats :sports-stats :selected-types)))

(re-frame/reg-sub
 ::sports-stats-data*
 (fn [db _]
   (-> db :stats :sports-stats :data)))

(re-frame/reg-sub
 ::sports-stats-data
 :<- [::sports-stats-data*]
 (fn [data _]
   ;;TODO
   data))

(re-frame/reg-sub
 ::sports-stats-labels
 (fn [db _]
   {}))
