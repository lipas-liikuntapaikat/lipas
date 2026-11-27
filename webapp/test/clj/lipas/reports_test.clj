(ns lipas.reports-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.data.cities :as cities]
            [lipas.reports :as reports]))

(def gen-pop-data
  "City code -> population for a random subset of active cities."
  (gen/let [city-codes (gen/set (gen/elements (keys cities/by-city-code))
                                {:min-elements 1 :max-elements 40})
            pops (gen/vector (gen/choose 0 700000) (count city-codes))]
    (zipmap city-codes pops)))

(defn- ->bucket
  "ES returns numeric terms keys as floats."
  [k doc-count m2-sum]
  {:key (double k)
   :doc_count doc-count
   :area_m2_stats {:count doc-count :sum m2-sum :min 1.0 :max m2-sum :avg (/ m2-sum doc-count)}
   :length_km_stats {:count 0 :sum 0.0 :min nil :max nil :avg nil}})

(def gen-case
  (gen/let [region-k (gen/elements [:province-id :avi-id])
            pop-data gen-pop-data
            ;; Selection may be empty (= all cities) and include cities
            ;; without population data
            selected (gen/one-of
                       [(gen/return [])
                        (gen/vector (gen/elements (concat (keys pop-data)
                                                          (keys cities/by-city-code)))
                                    1 20)])
            buckets (gen/vector (gen/tuple (gen/elements (keys (if (= :avi-id region-k)
                                                                 cities/avi-areas
                                                                 cities/provinces)))
                                           (gen/choose 1 500)
                                           (gen/double* {:min 1.0 :max 1e6 :NaN? false}))
                                0 10)]
    {:region-k region-k
     :pop-data pop-data
     :selected selected
     :aggs-data (->> buckets
                     (map (fn [[k n m2]] [k (->bucket k n m2)]))
                     (into {})
                     vals)}))

(defn- expected-population
  "Population of selected cities in the region, whether or not they have
  sites. Nil when none of them has population data."
  [{:keys [region-k pop-data selected]} region]
  (let [city-codes (if (empty? selected) (keys pop-data) (distinct selected))
        populations (->> city-codes
                         (filter #(= region (get-in cities/by-city-code [% region-k])))
                         (keep pop-data))]
    (when (seq populations)
      (reduce + populations))))

(defspec region-population-counts-selected-cities-without-sites 200
  (prop/for-all [{:keys [region-k pop-data selected aggs-data] :as c} gen-case]
                (let [result (reports/calculate-stats-by-region aggs-data pop-data selected region-k)]
                  (and (= (set (map (comp int :key) aggs-data)) (set (keys result)))
                       (every? (fn [[region {:keys [population]}]]
                                 (= (expected-population c region) population))
                               result)))))

(defspec region-per-capita-follows-population 200
  (prop/for-all [{:keys [region-k pop-data selected aggs-data]} gen-case]
                (let [result (reports/calculate-stats-by-region aggs-data pop-data selected region-k)]
                  (every? (fn [{:keys [key doc_count area_m2_stats]}]
                            (let [{:keys [population area-m2-pc sites-count-p1000c area-m2-sum]}
                                  (result (int key))]
                              (and (= (:sum area_m2_stats) area-m2-sum)
                                   (if (and population (pos? population))
                                     (and (== (/ (:sum area_m2_stats) population) area-m2-pc)
                                          (== (/ doc_count (/ population 1000)) sites-count-p1000c))
                                     (and (nil? area-m2-pc) (nil? sites-count-p1000c))))))
                          aggs-data))))

(deftest type-grouping-population-is-selected-cities-total
  (let [pop-data {91 600000 837 250000 49 300000}
        aggs-data [(->bucket 1110 3 30.0) (->bucket 2240 1 5.0)]]
    (is (= {1110 850000 2240 850000}
           (update-vals (reports/calculate-stats-by-type aggs-data pop-data [91 837]) :population)))
    (is (= {1110 1150000 2240 1150000}
           (update-vals (reports/calculate-stats-by-type aggs-data pop-data []) :population)))))

(deftest region-without-population-has-no-per-capita
  ;; A zero population must not divide by zero
  (let [result (reports/calculate-stats-by-region [(->bucket 1 2 10.0)] {91 0} [] :province-id)]
    (is (= 0 (get-in result [1 :population])))
    (is (nil? (get-in result [1 :area-m2-pc])))
    (is (nil? (get-in result [1 :sites-count-p1000c])))))
