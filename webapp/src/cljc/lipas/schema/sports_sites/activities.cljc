(ns lipas.schema.sports-sites.activities
  (:require [clojure.string :as str]
            [lipas.data.activities :as activities-data]
            [lipas.data.types :as types]
            [malli.util :as mu]))

(def activity (into [:enum] (keys activities-data/activities)))
(def activities
  [:set {:description "Enriched activity related content for Luontoon.fi service. Certain sports facility types may contain data about activities that can be practiced at the facility."}
   activity])

(defn -append-description
  [schema {:keys [type-codes label]}]
  (let [s (str (:en label)
               " is an activity associated with facility types "
               (str/join ", " (for [[k m] (select-keys types/all type-codes)]
                                (str k " " (get-in m [:name :en]))))
               ". Enriched activity information is collected for Luontoon.fi service.")]
    (mu/update-properties schema assoc :description s)))

(def fishing (-append-description
              activities-data/fishing-schema
              activities-data/fishing))

(def outdoor-recreation-areas (-append-description
                               activities-data/outdoor-recreation-areas-schema
                               activities-data/outdoor-recreation-areas))

(def outdoor-recreation-routes (-append-description
                                activities-data/outdoor-recreation-routes-schema
                                activities-data/outdoor-recreation-routes))

(def outdoor-recreation-facilities (-append-description
                                    activities-data/outdoor-recreation-facilities-schema
                                    activities-data/outdoor-recreation-facilities))

(def cycling (-append-description
              activities-data/cycling-schema
              activities-data/cycling))

(def paddling (-append-description
               activities-data/paddling-schema
               activities-data/paddling))

(def birdwatching (-append-description
                   activities-data/birdwatching-schema
                   activities-data/birdwatching))
