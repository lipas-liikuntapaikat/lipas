(ns lipas.schema.common
  (:require [lipas.data.status :as status]))

(def status (into [:enum] (keys status/statuses)))
(def statuses [:set status])
;; https://github.com/metosin/malli/issues/670
(def number number?)
(def percentage [number? {:min 0 :max 100}])

(def localized-string
  [:map
   [:fi {:optional true} [:string]]
   [:se {:optional true} [:string]]
   [:en {:optional true} [:string]]])

(def coordinates
  [:vector {:min 2
            :max 3
            :title "Coordinates"
            :description "WGS84 Lon, Lat and optional altitude in meters"}
   number?])

(def point-geometry
  [:map
   {:title "Point"}
   [:type [:enum "Point"]]
   [:coordinates #'coordinates]])

(def line-string-geometry
  [:map
   {:title "LineString"}
   [:type [:enum "LineString"]]
   [:coordinates [:vector #'coordinates]]])

(def polygon-geometry
  [:map
   {:title "Polygon"}
   [:type [:enum "Polygon"]]
   [:coordinates [:vector [:vector #'coordinates]]]])

(def point-feature
  [:map {:title "PointFeature"}
   [:type [:enum "Feature"]]
   [:geometry #'point-geometry]
   [:properties {:optional true} [:map]]])

(def line-string-feature
  [:map {:title "LineStringFeature"}
   [:type [:enum "Feature"]]
   [:geometry #'line-string-geometry]
   [:properties {:optional true} [:map]]])

(def polygon-feature
  [:map {:title "PolygonFeature"}
   [:type [:enum "Feature"]]
   [:geometry #'polygon-geometry]
   [:properties {:optional true} [:map]]])

(def point-fcoll
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential point-feature]]])

(def line-string-fcoll
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential
     #'line-string-feature]]])

(def polygon-fcoll
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential
     #'polygon-feature]]])

(comment
  (require '[malli.core :as m])
  (m/schema point-geometry)
  (m/schema line-string-geometry))
