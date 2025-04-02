(ns lipas-api.geometries
  (:require [cheshire.core :refer [parse-string]]
            [clojure.string :refer [upper-case]]
            [schema.core :as s]))

(defn parse-geojson
  [geom]
  (assoc geom :geometry (parse-string (:geometry geom) true)))

(defn to-feature-coll
  [features]
  {:type "FeatureCollection" :features features})

(defn to-feature
  [geom props]
  {:type "Feature" :geometry geom :properties props})

(defn point-to-geojson
  [point]
  (to-feature (:geometry point) {:pointId (:point-id point)}))

(defn route-to-geojson
  [route]
  (to-feature (:geometry route) {:routeCollectionId (:route-coll-id route)
                                 :routeCollectionName (:route-coll-name-fi route)
                                 :routeId (:route-id route)
                                 :routeName (:route-name-fi route)
                                 :routeSegmentId (:route-segment-id route)
                                 :routeSegmentName (:route-segment-name-fi route)}))

(defn area-to-geojson
  [area]
  (to-feature (:geometry area) {:areaId (:area-id area)
                                :areaName (:area-name-fi area)
                                :areaSegmentId (:area-segment-id area)
                                :areaSegmentName (:area-segment-name-fi area)}))

(defn not-invalid
  "Returns only valid geometries, otherwise nil.

  TODO: maybe add logging for invalid geoms.

  TODO: schema/check was very slow so I added
  stupid manual checking that at least 1 coordinate
  pair exists."
  [geojson]
  (when (not-empty (-> geojson :features first :geometry :coordinates)) geojson))

(defmulti to-geojson map?)

(defmethod to-geojson true
  [{points :points routes :routes areas :areas}]
  (when-let [all-geoms (map parse-geojson (not-empty (concat
                                                      (map point-to-geojson points)
                                                      (map route-to-geojson routes)
                                                      (map area-to-geojson areas))))]
    (when-not (empty? all-geoms) (not-invalid (to-feature-coll all-geoms)))))

(defmethod to-geojson false
  [geojson-str]
  (when-not (empty? geojson-str)
    (not-invalid (parse-string geojson-str true))))

(defn geom-types
  "Returns set of geometry types in given FeatureCollection."
  [fcoll]
  (set (map (comp :type :geometry) fcoll)))

(defn validate-geom-type
  "Returns map with key :error if something's wrong.
  Otherwise returns validated geoms."
  [geoms type]
  (let [geom-types (geom-types (:features geoms))
        geom-type (first geom-types)
        ok-geom-type (-> type :geometry-type)]
    (cond
      (next geom-types) {:error (str "Only one geometry type is  "
                                     "allowed for sports place "
                                     "Found: " geom-types)}
      (not= geom-type ok-geom-type) {:error (str "Geometry type for sports "
                                                 "place type " (:type-code type)
                                                 " must be " ok-geom-type)}
      :else geoms)))

(defn any-val?
  [a-map keys]
  ((complement empty?) (vals (select-keys a-map keys))))

(defn validate-prop-pairs
  "Checks props map against given list of keyword pairs. At least one of the pairs
  must exist in map, otherwise error map is returned."
  [props pairs]
  (let [errors (reduce
                (fn [errs pair]
                  (if-not (any-val? props pair)
                    (assoc errs (first pair)
                           (str (first pair) " or " (second pair) " must be present."))
                    errs))
                {} pairs)]
    (not-empty errors)))

(defn validate-route-props
  "Validates that either id or name is given for routeColl, route and routeSegment.
  Returns {:errors {...}} map if neither id or name is provided in props."
  [props]
  (let [pairs [[:routeCollectionId :routeCollectionName]
               [:routeId :routeName]
               [:routeSegmentId :routeSegmentName]]]
    (validate-prop-pairs props pairs)))

(defn validate-area-props
  [props]
  (let [pairs [[:areaId :areaName]
               [:areaSegmentId :areaSegmentName]]]
    (validate-prop-pairs props pairs)))

(defn validate-geom-props
  [geoms]
  (let [geom-type (first (geom-types (:features geoms)))]
    (condp = geom-type
      "Point"      nil
      "LineString" (map validate-route-props (map :properties (:features geoms)))
      "Polygon"    (map validate-area-props (map :properties (:features geoms))))))

(defn parse-coords
  [geojson-point-str]
  (when-let [point (parse-string geojson-point-str true)]
    (let [[lon lat] (:coordinates point)]
      {:lon lon :lat lat})))
