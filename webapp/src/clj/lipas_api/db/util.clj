(ns lipas-api.db.util
  (:require [clojure.set]
            [lipas-api.util :refer [convert-keys->db convert-keys<-db]]))

;;; Shorthand aliases for keyword conversion funcs
(def ck->db convert-keys->db)
(def ck<-db convert-keys<-db)

;;; Shorthands for converting keywords's in maps
(defn cck->db [coll] (if (coll? coll) (map ck->db coll) coll))
(defn cck<-db [coll] (if (coll? coll) (map ck<-db coll) coll))

(defn q
  "Calls query function with params and db spec
  making keyword translations between clojure
  (kebab-case or camelCase) and db (snake_case)."
  ([query-fn db-spec]
   (q query-fn db-spec nil))
  ([query-fn db-spec params]
   (cck<-db (apply query-fn [(ck->db params) db-spec]))))

(defn parse-user
  "Parses :cities and :types PGobject arrays '[123 321]'
  into Clojure data structures [123 321]."
  [user]
  (reduce (fn [u k] (update u k (comp read-string str))) user [:types :cities]))

(defn geom-type
  [location]
  (-> location :geometries :features first :geometry :type))

(defn first-geom
  "Returns first geometry in a location."
  [location]
  (-> location :geometries :features first :geometry))

(defn new-and-existing
  "Groups given coll of segments into {:new [] :existing []}
  based on whether given 'lookup-prop' is found in segments
  properties. If lookup-prop is found, segment is considered
  existing, otherwise new."
  [lookup-prop segments]
  (let [existing (filter (comp lookup-prop :properties) segments)
        new      (clojure.set/difference (set segments) (set existing))]
    {:new new :existing existing}))

(defn first-non-nil-prop
  ([location prop-keyword]
   (first-non-nil-prop location prop-keyword "unnamed"))
  ([location prop-keyword fallback]
   (let [features (-> location :geometries :features)
         names (map (comp prop-keyword :properties) features)]
     (or (first (filter identity names)) fallback))))

(defn fetch-sports-place-data
  [db {type-codes :type-codes city-codes :city-codes limit :limit offset :offset}]
  (let [params    {:type-codes (if (empty? type-codes)
                                 (.fetch-type-codes db)
                                 type-codes)
                   :city-codes (if (empty? city-codes)
                                 (.fetch-city-codes db)
                                 city-codes)
                   :limit      limit
                   :offset     offset}
        places    (.fetch-sports-places db params)
        locations (.fetch-locations db {:location-ids (map :location-id places)})
        geoms     (.fetch-geoms db {:points (map :point-id locations)
                                    :routes (map :route-id locations)
                                    :areas  (map :area-id locations)})
        props     (.fetch-props db {:ids (map :id places)})]
    {:places places :locations locations :geoms geoms :props props}))

(defn fetch-sports-place-data-single
  [db sports-place-id]
  (let [place    (.fetch-sports-place db sports-place-id)
        location (.fetch-location db (:location-id place))
        geoms    (.fetch-geoms db {:points [(:point-id location)]
                                   :routes [(:route-id location)]
                                   :areas  [(:area-id location)]})
        props    (.fetch-props db {:ids [(:id place)]})]
    {:place place :location location :geoms geoms :props props}))

(defn fetch-validation-data
  [db {type-code :type-code}]
  {:type        (first (.fetch-types db [type-code]))
   :types-props (.fetch-types-props db [type-code])})
