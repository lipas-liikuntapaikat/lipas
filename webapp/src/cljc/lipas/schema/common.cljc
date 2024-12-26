(ns lipas.schema.common
  (:require [lipas.data.status :as status]))

(def status (into [:enum] (keys status/statuses)))
(def statuses [:set status])
;; https://github.com/metosin/malli/issues/670
(def number number?)
(def percentage [number? {:min 0 :max 100}])
(def -uuid-pattern #"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
(def uuid [:re {:description "UUID v4 string"} -uuid-pattern])

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

(def point-feature-collection
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential point-feature]]])

(def line-string-feature-collection
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential
     #'line-string-feature]]])

(def polygon-feature-collection
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential
     #'polygon-feature]]])

(comment
  (require '[malli.core :as m])
  (m/schema point-geometry)
  (m/schema line-string-geometry))
