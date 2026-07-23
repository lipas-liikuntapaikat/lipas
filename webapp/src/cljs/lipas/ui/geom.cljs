(ns lipas.ui.geom
  "Light GeoJSON geometry helpers usable from the base module.

   Extracted from lipas.ui.map.utils so that always-loaded code (the AI
   assistant, sports-site forms) doesn't drag the whole map/OpenLayers/
   turf closure into the base bundle — these only need the small
   @turf/length, @turf/area and @turf/helpers packages."
  (:require ["@turf/area$default" :as turf-area]
            ["@turf/helpers" :refer [convertArea]]
            ["@turf/length$default" :as turf-length]
            [clojure.reader :refer [read-string]]
            [lipas.ui.utils :as utils]))

(defn calculate-length-km
  [fcoll]
  (when (seq (:features fcoll))
    (-> fcoll
        clj->js
        turf-length ;; returns kilometers
        (utils/round-safe 2)
        read-string)))

(defn calculate-area-km2
  [fcoll]
  (-> fcoll
      clj->js
      turf-area ;; returns square meters
      (convertArea "meters" "kilometers")
      (utils/round-safe 2)
      read-string))

(defn calculate-area-m2
  [fcoll]
  (when (seq (:features fcoll))
    (-> fcoll
        clj->js
        turf-area ;; returns square meters
        (utils/round-safe 2)
        read-string)))

(defn calculate-elevation-stats
  [fcoll]
  (->> fcoll
       :features
       (map (comp :coordinates :geometry))
       (mapcat (fn [coll] (map (fn [coords] (get coords 2)) coll)))
       (partition 2 1)
       (reduce (fn [res [prev curr]]
                 (let [d (- curr prev)]
                   (cond
                     (zero? d) res
                     (pos? d) (update res :ascend-m + d)
                     (neg? d) (update res :descend-m + (Math/abs d)))))
               {:ascend-m 0 :descend-m 0})))
