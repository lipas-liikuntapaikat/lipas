(ns lipas.backend.gis
  (:require
   [cheshire.core :as json]
   [geo.io :as gio]
   [geo.jts :as jts])
  (:import [org.locationtech.jts.simplify DouglasPeuckerSimplifier]))

(def srid 4326) ;; WGS84
(def tm35fin-srid 3067)

(defn- simplify-geom [m]
  (update m :geometry #(-> %
                           (DouglasPeuckerSimplifier/simplify 0.001) ;; ~111m
                           (jts/set-srid srid))))

(defn- dummy-props [m]
  (assoc m :properties {}))

(defn simplify
  "Returns simplified version of `m` where `m` is a map representing
  GeoJSON FeatureCollection."
  [m]
  (-> m
      (update :features #(map dummy-props %))
      json/encode
      (gio/read-geojson srid)
      (->> (map simplify-geom))
      gio/to-geojson-feature-collection
      (json/decode keyword)))

(defn centroid
  "Returns centroids of `m` where `m` is a map representing
  GeoJSON FeatureCollection."
  [m]
  (-> m
      (update :features #(map dummy-props %))
      json/encode
      (gio/read-geojson srid)
      (->> (map :geometry))
      jts/geometry-collection
      jts/centroid
      (jts/set-srid srid)
      gio/to-geojson
      (json/decode keyword)))

(defn wgs84->tm35fin [[lon lat]]
  (let [transformed (jts/transform-geom (jts/point lat lon) srid tm35fin-srid)]
    {:easting (.getX transformed) :northing (.getY transformed)}))

(comment

  (wgs84->tm35fin [23.8259457479965 61.4952794263427])

  (def test-point
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [25.720539797408946,
                      62.62057217751676]}}]})

  (centroid test-point)

  (def test-route
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[26.2436753445903, 63.9531598143881],
         [26.4505514903968, 63.9127506671744]]}},
      {:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[26.2436550567509, 63.9531552213109],
         [25.7583312263512, 63.9746827436437]]}}]})

  (centroid test-route))
