(ns lipas.reports
  (:require [lipas.utils :as utils]))

(defn- energy-data-exists? [{:keys [energy-consumption]}]
  (let [{:keys [electricity-mwh
                heat-mwh
                water-m3]} energy-consumption]
    (and
     (some? electricity-mwh)
     (some? heat-mwh)
     (some? water-m3))))

(defn- get-values [sites field-kw]
  (->> sites
       (map (comp field-kw
                  :energy-consumption))
       (remove nil?)))

(defn energy-report [sites]
  {:total-count     (count sites)
   :electricity-mwh (utils/simple-stats (get-values sites :electricity-mwh))
   :heat-mwh        (utils/simple-stats (get-values sites :heat-mwh))
   :water-m3        (utils/simple-stats (get-values sites :water-m3))
   :hall-of-fame    (->> sites
                         (filter energy-data-exists?)
                         (map #(select-keys % [:lipas-id :name])))})
