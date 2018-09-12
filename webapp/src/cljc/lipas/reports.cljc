(ns lipas.reports
  (:require [lipas.utils :as utils]))

(defn- all-energy-data-exists? [{:keys [energy-consumption]}]
  (let [{:keys [electricity-mwh heat-mwh water-m3]} energy-consumption]
    (and
     (some? electricity-mwh)
     (some? heat-mwh)
     (some? water-m3))))

(defn- get-values [sites field-kw]
  (->> sites
       (map (comp field-kw :energy-consumption))
       (remove nil?)))

(defn- ->data-point [{:keys [lipas-id name energy-consumption]}]
  (-> energy-consumption
      (assoc :energy-mwh (+ (:heat-mwh energy-consumption 0)
                            (:electricity-mwh energy-consumption 0)))
      (assoc :name name)
      (assoc :lipas-id lipas-id)))

(defn energy-report [sites]
  {:total-count     (count sites)
   :electricity-mwh (utils/simple-stats (get-values sites :electricity-mwh))
   :heat-mwh        (utils/simple-stats (get-values sites :heat-mwh))
   :water-m3        (utils/simple-stats (get-values sites :water-m3))
   :data-points     (->> sites
                         (filter all-energy-data-exists?)
                         (map ->data-point))
   :hall-of-fame    (->> sites
                         (filter all-energy-data-exists?)
                         (map #(select-keys % [:lipas-id :name])))})
