(ns lipas.ui.reports.subs
  (:require
   [lipas.reports :as reports]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::dialog-open?
 (fn [db _]
   (-> db :reports :dialog-open?)))

(re-frame/reg-sub
 ::downloading?
 (fn [db _]
   (-> db :reports :downloading?)))

(re-frame/reg-sub
 ::fields
 (fn [db _]
   (-> db :reports :fields)))

(re-frame/reg-sub
 ::selected-fields
 (fn [db _]
   (-> db :reports :selected-fields)))

(re-frame/reg-sub
 ::city-services
 (fn [db _]
   (-> db :reports :city-services)))

(re-frame/reg-sub
 ::selected-city-service
 (fn [db _]
   (-> db :reports :selected-city-service)))

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :reports :selected-cities)))

(re-frame/reg-sub
 ::stats-metrics
 (fn [db _]
   (-> db :reports :stats-metrics)))

(re-frame/reg-sub
 ::selected-metrics
 (fn [db _]
   (-> db :reports :selected-metrics)))

(re-frame/reg-sub
 ::stats-units
 (fn [db _]
   (-> db :reports :stats-units)))

(re-frame/reg-sub
 ::selected-unit
 (fn [db _]
   (-> db :reports :selected-unit)))

(re-frame/reg-sub
 ::selected-years
 (fn [db _]
   (-> db :reports :selected-years)))

(re-frame/reg-sub
 ::stats-data
 (fn [db _]
   (-> db :reports :stats)))

(re-frame/reg-sub
 ::stats-labels
 (fn [db _]
   (let [tr     (-> db :translator)
         locale (tr)]
     (reduce
      (fn [res [k v]]
        (let [k2 (str k "-avg")
              v2 (str (locale v) " " (tr :reports/country-avg))]
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
 ::cities-stats
 :<- [::stats-data]
 :<- [::selected-cities]
 :<- [::selected-city-service]
 :<- [::selected-unit]
 :<- [::selected-years]
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
