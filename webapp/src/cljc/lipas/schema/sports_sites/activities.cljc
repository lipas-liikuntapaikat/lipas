(ns lipas.schema.sports-sites.activities
  (:require [lipas.data.activities :as activities-data]))

(def activity (into [:enum] (keys activities-data/activities)))
(def activities
  [:set {:title "Activities"
         :description "Enriched activity related content for Luontoon.fi service. Certain sports facility types may contain data about activities that can be practiced at the facility."}
   activity])

(def fishing activities-data/fishing-schema)
(def outdoor-recreation-areas activities-data/outdoor-recreation-areas-schema)
(def outdoor-recreation-routes activities-data/outdoor-recreation-routes-schema)
(def outdoor-recreation-facilities activities-data/outdoor-recreation-facilities-schema)
(def cycling activities-data/cycling-schema)
(def paddling activities-data/paddling-schema)
(def birdwatching activities-data/birdwatching-schema)
