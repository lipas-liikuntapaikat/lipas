(ns lipas.schema.common
  (:refer-clojure :exclude [uuid])
  (:require [lipas.data.status :as status]
            #?(:clj [clojure.test.check.generators :as gen])))

(def -iso8601-pattern #"^(?:[1-9]\d{3}-(?:(?:0[1-9]|1[0-2])-(?:0[1-9]|1\d|2[0-8])|(?:0[13-9]|1[0-2])-(?:29|30)|(?:0[13578]|1[02])-31)|(?:[1-9]\d(?:0[48]|[2468][048]|[13579][26])|(?:[2468][048]|[13579][26])00)-02-29)T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d+)?Z$")

(def iso8601-timestamp
  [:and
   {:description "ISO 8601 timestamp in UTC timezone"
    :gen/gen #?(:clj (gen/fmap
                      (fn [millis]
                        (let [instant (java.time.Instant/ofEpochMilli millis)
                              formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                              zdt (.atZone instant (java.time.ZoneId/of "UTC"))]
                          (.format formatter zdt)))
                      (gen/choose
                       (.getTime #inst "1900-01-01")
                       (.getTime #inst "2100-12-31")))
                :cljs nil)}
   :string
   [:re -iso8601-pattern]])

(def status (into [:enum] (keys status/statuses)))
(def statuses [:set status])
;; https://github.com/metosin/malli/issues/670
(def number
  "Number schema that excludes Infinity and NaN.
   Generator produces doubles for consistent Elasticsearch dynamic mapping."
  [:and
   {:gen/gen #?(:clj (gen/double* {:infinite? false :NaN? false})
                :cljs nil)}
   number?
   [:fn #?(:clj #(not (Double/isInfinite %))
           :cljs #(js/isFinite %))]
   [:fn #?(:clj #(not (Double/isNaN %))
           :cljs #(not (js/isNaN %)))]])

(def pos-int
  "Positive integer schema for count fields.
   Ensures consistent Elasticsearch long mapping."
  [:int {:min 0
         :gen/gen #?(:clj (gen/large-integer* {:min 0 :max 10000})
                     :cljs nil)}])

(def percentage
  [:and
   number?
   [:fn #(<= 0 % 100)]])
(def -uuid-pattern #"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
(def uuid [:re {:description "UUID v4 string"} -uuid-pattern])

(def localized-string
  [:map
   [:fi {:optional true :description "Finnish translation"} [:string]]
   [:se {:optional true :description "Swedish translation"} [:string]]
   [:en {:optional true :description "English translation"} [:string]]])

(def coordinates
  "WGS84 coordinates [longitude, latitude, altitude?] for Finland.
   - Longitude: 18.0-33.0°E (Finland bounds with tolerance)
   - Latitude: 59.0-71.0°N (Finland bounds with tolerance)
   - Altitude: -1500 to +2000 meters (optional, lowest mine to highest peaks)
   Accepts both integers and doubles. Rejects Infinity and NaN."
  [:cat {:gen/fmap vec} ;; Convert sequence to vector for generator
   ;; Longitude (18-33°E)
   [:and
    {:description "Longitude in degrees (18.0-33.0°E)"
     :gen/gen #?(:clj (gen/double* {:min 18.0 :max 33.0 :infinite? false :NaN? false})
                 :cljs nil)}
    number
    [:fn #(<= 18.0 % 33.0)]
    [:fn #?(:clj #(not (Double/isInfinite %))
            :cljs #(js/isFinite %))]
    [:fn #?(:clj #(not (Double/isNaN %))
            :cljs #(not (js/isNaN %)))]]
   ;; Latitude (59-71°N)
   [:and
    {:description "Latitude in degrees (59.0-71.0°N)"
     :gen/gen #?(:clj (gen/double* {:min 59.0 :max 71.0 :infinite? false :NaN? false})
                 :cljs nil)}
    number
    [:fn #(<= 59.0 % 71.0)]
    [:fn #?(:clj #(not (Double/isInfinite %))
            :cljs #(js/isFinite %))]
    [:fn #?(:clj #(not (Double/isNaN %))
            :cljs #(not (js/isNaN %)))]]
   ;; Altitude (optional, -1500 to +2000m)
   [:?
    [:and
     {:description "Altitude in meters (-1500 to +2000)"
      :gen/gen #?(:clj (gen/double* {:min -1500.0 :max 2000.0 :infinite? false :NaN? false})
                  :cljs nil)}
     number
     [:fn #(<= -1500.0 % 2000.0)]
     [:fn #?(:clj #(not (Double/isInfinite %))
             :cljs #(js/isFinite %))]
     [:fn #?(:clj #(not (Double/isNaN %))
             :cljs #(not (js/isNaN %)))]]]])

;; GeoJSON validation helpers

(defn- valid-linear-ring?
  "A linear ring is a closed LineString with 4+ positions where first = last.
   Per RFC 7946 Section 3.1.6"
  [ring]
  (and (>= (count ring) 4)
       (= (first ring) (last ring))))

(defn- valid-polygon-coordinates?
  "Validates polygon coordinates: array of linear rings.
   First ring is exterior, others are holes.
   Per RFC 7946 Section 3.1.6"
  [rings]
  (and (>= (count rings) 1)
       (every? valid-linear-ring? rings)))

;; Prettier, but doesn't accept Integers
#_(def coordinates
    "WGS84 coordinates [longitude, latitude, altitude?] for Finland."
    [:cat {:gen/fmap vec}
     [:double {:min 18.0 :max 33.0 :description "Longitude in degrees (18.0-33.0°E)"}]
     [:double {:min 59.0 :max 71.0 :description "Latitude in degrees (59.0-71.0°N)"}]
     [:? [:double {:min -1500.0 :max 2000.0 :description "Altitude in meters (-1500 to +2000)"}]]])

(def point-geometry
  [:map {:description "GeoJSON Point geometry"}
   [:type [:enum "Point"]]
   ;; Use the coordinates schema's built-in generator for randomness
   [:coordinates #'coordinates]])

(def line-string-geometry
  [:map {:description "GeoJSON LineString geometry. Per RFC 7946, requires 2+ positions."}
   [:type [:enum "LineString"]]
   [:coordinates
    ;; Use the vector generator with min 2, max 10 coordinates for variety
    [:vector {:min 2 :max 10} #'coordinates]]])

(def polygon-geometry
  [:map {:description "GeoJSON Polygon geometry. Per RFC 7946, requires linear rings (4+ positions, first = last)."}
   [:type [:enum "Polygon"]]
   [:coordinates
    [:and
     {:gen/gen #?(:clj (gen/fmap
                        (fn [_]
                           ;; Generate a random polygon by creating points around a center
                          (let [;; Random center point within Finland bounds
                                center-lon (+ 20.0 (rand 10.0)) ; 20-30°E
                                center-lat (+ 62.0 (rand 6.0)) ; 62-68°N
                                 ;; Randomly decide whether to include altitude (50% chance)
                                include-altitude? (< (rand) 0.5)
                                altitude (when include-altitude?
                                           (+ -1500.0 (rand 3500.0))) ; -1500 to +2000m
                                 ;; Generate 3-7 random points around center
                                num-points (+ 3 (rand-int 5))
                                 ;; Create points in a circle around center with random radius
                                angles (map #(* 2 Math/PI (/ % num-points)) (range num-points))
                                radius (+ 0.1 (rand 0.4)) ; 0.1-0.5 degrees
                                points (mapv (fn [angle]
                                               (let [lon (+ center-lon (* radius (Math/cos angle)))
                                                     lat (+ center-lat (* radius (Math/sin angle)))]
                                                 (if include-altitude?
                                                   [lon lat altitude]
                                                   [lon lat])))
                                             angles)
                                 ;; Close the ring by appending first point
                                closed-ring (conj points (first points))]
                             ;; Return as polygon coordinates (vector of rings)
                            [closed-ring]))
                        (gen/return nil))
                  :cljs nil)}
     [:vector {:min 1} [:vector {:min 4} #'coordinates]]
     [:fn {:error/message "Polygon coordinates must be valid linear rings (4+ positions, first = last)"}
      valid-polygon-coordinates?]]]])

(def point-feature
  [:map {:description "GeoJSON Feature with required Point geometry."}
   [:type [:enum "Feature"]]
   [:geometry #'point-geometry]
   [:properties {:optional true} [:map]]])

(def line-string-feature
  [:map {:description "GeoJSON Feature with required LineString geometry."}
   [:type [:enum "Feature"]]
   [:geometry #'line-string-geometry]
   [:properties {:optional true} [:map]]])

(def polygon-feature
  [:map {:description "GeoJSON Feature with required Polygon geometry."}
   [:type [:enum "Feature"]]
   [:geometry #'polygon-geometry]
   [:properties {:optional true} [:map]]])

(def point-feature-collection
  [:map {:description "GeoJSON FeatureCollection with required Point geometries."}
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential {:min 1 :max 1} #'point-feature]]])

(def line-string-feature-collection
  [:map {:description "GeoJSON FeatureCollection with required LineString geometries."}
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential {:min 1} #'line-string-feature]]])

(def polygon-feature-collection
  [:map {:description "GeoJSON FeatureCollection with required Polygon geometries."}
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential {:min 1} #'polygon-feature]]])

(comment
  (require '[malli.core :as m])
  (m/schema point-geometry)
  (m/schema line-string-geometry))
