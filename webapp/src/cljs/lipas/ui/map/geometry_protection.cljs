(ns lipas.ui.map.geometry-protection
  "Utilities for protecting geometry segments that are used in routes"
  (:require [clojure.set :as set]))

(defn check-segment-usage
  "Check if a segment is used in any routes.
   Returns a vector of maps with route information for each route using the segment."
  [sports-site segment-id]
  (let [activities (:activities sports-site)
        all-routes (mapcat (fn [[activity-k activity-data]]
                             (map #(assoc % :activity-k activity-k)
                                  (:routes activity-data)))
                           activities)]
    (filter #(contains? (:fids %) segment-id) all-routes)))

(defn get-affected-routes-details
  "Get detailed information about routes affected by segment deletion"
  [sports-site segment-id locale]
  (let [affected-routes (check-segment-usage sports-site segment-id)]
    (map (fn [route]
           {:route-name (get-in route [:route-name locale] "Nimetön reitti")
            :activity (name (:activity-k route))
            :segment-count (count (:fids route))})
         affected-routes)))

(defn check-multiple-segments-usage
  "Check if multiple segments are used in any routes.
   Returns a map with segment-id as key and affected routes as value."
  [sports-site segment-ids]
  (let [segment-set (set segment-ids)]
    (reduce (fn [acc segment-id]
              (let [routes (check-segment-usage sports-site segment-id)]
                (if (seq routes)
                  (assoc acc segment-id routes)
                  acc)))
            {}
            segment-set)))

(defn can-delete-segment?
  "Check if a segment can be safely deleted without affecting routes"
  [sports-site segment-id]
  (empty? (check-segment-usage sports-site segment-id)))

(defn segments-in-ordered-routes
  "Get segments that are part of routes with explicit ordering"
  [sports-site]
  (let [activities (:activities sports-site)
        all-routes (mapcat :routes (vals activities))
        ordered-routes (filter :segments all-routes)]
    (set (mapcat (fn [route]
                   (map :fid (:segments route)))
                 ordered-routes))))
