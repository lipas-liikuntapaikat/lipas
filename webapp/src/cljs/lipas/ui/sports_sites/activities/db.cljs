(ns lipas.ui.sports-sites.activities.db
  (:require [lipas.data.activities :as data]))

(def default-sort-order
  [:route-name
   :type
   :paddling-route-type
   :birdwatching-type
   :activities
   :paddling-activities
   :cycling-activities
   :description-short
   :description-long
   :independent-entity
   :highlights
   :arrival
   :rules
   :rules-structured
   :permits-rules-guidelines
   :good-to-know
   :food-and-water
   :accommodation
   :duration
   :route-length-km
   :route-notes
   :travel-direction
   :route-marking
   :safety
   :habitat
   :birdwatching-habitat
   :birdwatching-character
   :birdwatching-season
   :birdwatching-species
   :unpaved-percentage
   :trail-percentage
   :cyclable-percentage
   :paddling-properties
   :paddling-difficulty
   :fish-population
   :fishing-methods
   :fishing-species
   :fishing-waters
   :fishing-permit
   :fishing-permit-additional-info
   :images
   :videos
   :accessibility-classification
   :accessibility
   :parking
   :contacts
   :role
   :organization
   :email
   :www
   :phone-number])

(defn make-field-sorter
  [ks]
  (let [lookup (->> ks (reverse) (map-indexed (fn [idx k] [k idx])) (into {}))]
    (fn [[k _]]
      (get lookup k -1))))

(def default-field-sorter (make-field-sorter default-sort-order))

(def default-db
  {:mode          :default
   :data          data/activities
   :by-type-code  data/by-type-code
   :field-sorters (into
                    {:default default-field-sorter}
                    (for [{:keys [sort-order value]} (vals data/activities)
                          :when                      sort-order]
                      [(keyword value) (make-field-sorter sort-order)]))})
