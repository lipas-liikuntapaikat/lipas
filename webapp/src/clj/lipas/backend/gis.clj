(ns lipas.backend.gis
  (:require
   [cheshire.core :as json]
   [geo.io :as gio]
   [geo.jts :as jts])
  (:import [org.locationtech.jts.simplify DouglasPeuckerSimplifier]))

(def srid 4326) ;; WGS84

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
