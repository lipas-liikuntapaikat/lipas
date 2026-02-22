(ns lipas.schema.common
  (:refer-clojure :exclude [uuid])
  (:require [lipas.data.status :as status]
            [malli.core :as m]
            #?(:clj [clojure.test.check.generators :as gen])))

;;; Regexes (moved from schema/core.cljc) ;;;
(def email-regex #"^[a-zA-Z0-9åÅäÄöÖ._%+-]+@[a-zA-Z0-9åÅäÄöÖ.-]+\.[a-zA-Z]{2,63}$")
(def postal-code-regex #"[0-9]{5}")

(def -iso8601-pattern #"^(?:[1-9]\d{3}-(?:(?:0[1-9]|1[0-2])-(?:0[1-9]|1\d|2[0-8])|(?:0[13-9]|1[0-2])-(?:29|30)|(?:0[13578]|1[02])-31)|(?:[1-9]\d(?:0[48]|[2468][048]|[13579][26])|(?:[2468][048]|[13579][26])00)-02-29)T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d+)?Z$")

(def iso8601-timestamp
  (m/schema
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
    [:re -iso8601-pattern]]))

(def status (m/schema (into [:enum] (keys status/statuses))))
(def statuses (m/schema [:set status]))
;; https://github.com/metosin/malli/issues/670
(def number
  "Number schema that excludes Infinity and NaN.
   Generator produces doubles for consistent Elasticsearch dynamic mapping."
  (m/schema
   [:and
    {:gen/gen #?(:clj (gen/double* {:infinite? false :NaN? false})
                 :cljs nil)}
    number?
    [:fn {:error/message "Value cannot be Infinity"}
     #?(:clj #(not (Double/isInfinite %))
        :cljs #(js/isFinite %))]
    [:fn {:error/message "Value cannot be NaN (Not a Number)"}
     #?(:clj #(not (Double/isNaN %))
        :cljs #(not (js/isNaN %)))]]))

(def pos-int
  "Positive integer schema for count fields.
   Ensures consistent Elasticsearch long mapping."
  (m/schema
   [:int {:min 0
          :gen/gen #?(:clj (gen/large-integer* {:min 0 :max 10000})
                      :cljs nil)}]))

(def percentage
  (m/schema
   [:and
    number?
    [:fn {:error/message "Percentage must be between 0 and 100"}
     #(<= 0 % 100)]]))
(def -uuid-pattern #"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
(def uuid (m/schema [:re {:description "UUID v4 string"} -uuid-pattern]))

(def localized-string
  (m/schema
   [:map
    [:fi {:optional true :description "Finnish translation"} [:string]]
    [:se {:optional true :description "Swedish translation"} [:string]]
    [:en {:optional true :description "English translation"} [:string]]]))

(def lon
  "WGS84 longitude for Finland (18.0-33.0°E).
   Accepts both integers and doubles. Rejects Infinity and NaN."
  (m/schema
   [:and
    {:description "Longitude in degrees (18.0-33.0°E)"
     :gen/gen #?(:clj (gen/double* {:min 18.0 :max 33.0 :infinite? false :NaN? false})
                 :cljs nil)}
    number
    [:fn {:error/message "Longitude must be between 18.0 and 33.0 degrees (Finland bounds)"}
     #(<= 18.0 % 33.0)]
    [:fn {:error/message "Longitude cannot be Infinity"}
     #?(:clj #(not (Double/isInfinite %))
        :cljs #(js/isFinite %))]
    [:fn {:error/message "Longitude cannot be NaN (Not a Number)"}
     #?(:clj #(not (Double/isNaN %))
        :cljs #(not (js/isNaN %)))]]))

(def lat
  "WGS84 latitude for Finland (59.0-71.0°N).
   Accepts both integers and doubles. Rejects Infinity and NaN."
  (m/schema
   [:and
    {:description "Latitude in degrees (59.0-71.0°N)"
     :gen/gen #?(:clj (gen/double* {:min 59.0 :max 71.0 :infinite? false :NaN? false})
                 :cljs nil)}
    number
    [:fn {:error/message "Latitude must be between 59.0 and 71.0 degrees (Finland bounds)"}
     #(<= 59.0 % 71.0)]
    [:fn {:error/message "Latitude cannot be Infinity"}
     #?(:clj #(not (Double/isInfinite %))
        :cljs #(js/isFinite %))]
    [:fn {:error/message "Latitude cannot be NaN (Not a Number)"}
     #?(:clj #(not (Double/isNaN %))
        :cljs #(not (js/isNaN %)))]]))

(def altitude
  "Altitude/elevation in meters.
   Range: -10,000 to 2,000 meters.

   Note: The minimum is set to -10,000 to accommodate fallback values (-9999)
   returned by external APIs when elevation data is unavailable.

   Realistic Finnish elevations range from approximately -1,444m (Pyhäsalmi mine)
   to 1,324m (Halti peak), so -1,500 to 2,000 would cover all real locations.

   Accepts both integers and doubles. Rejects Infinity and NaN."
  (m/schema
   [:and
    {:description "Altitude in meters (-10,000 to 2,000)"
     :gen/gen #?(:clj (gen/double* {:min -10000.0 :max 2000.0 :infinite? false :NaN? false})
                 :cljs nil)}
    number
    [:fn {:error/message "Altitude must be between -10,000 and 2,000 meters"}
     #(<= -10000.0 % 2000.0)]
    [:fn {:error/message "Altitude cannot be Infinity"}
     #?(:clj #(not (Double/isInfinite %))
        :cljs #(js/isFinite %))]
    [:fn {:error/message "Altitude cannot be NaN (Not a Number)"}
     #?(:clj #(not (Double/isNaN %))
        :cljs #(not (js/isNaN %)))]]))

(def lon-euref
  "TM35FIN (ETRS-TM35FIN) Easting coordinate for Finland.
   Range: 40,000 to 770,000 meters (EPSG:3067 bounds with tolerance).
   Official EPSG bounds: 43,547.79 to 764,796.72 meters.
   Integer values only (meters from false origin)."
  (m/schema
   [:int {:min 40000
          :max 770000
          :description "TM35FIN Easting (E) coordinate in meters (40,000 to 770,000)"
          :error/message "Easting must be between 40,000 and 770,000 meters (TM35FIN bounds for Finland)"
          :gen/gen #?(:clj (gen/large-integer* {:min 40000 :max 770000})
                      :cljs nil)}]))

(def lat-euref
  "TM35FIN (ETRS-TM35FIN) Northing coordinate for Finland.
   Range: 6,500,000 to 7,800,000 meters (EPSG:3067 bounds with tolerance).
   Official EPSG bounds: 6,522,236.87 to 7,795,461.19 meters.
   Integer values only (meters from false origin)."
  (m/schema
   [:int {:min 6500000
          :max 7800000
          :description "TM35FIN Northing (N) coordinate in meters (6,500,000 to 7,800,000)"
          :error/message "Northing must be between 6,500,000 and 7,800,000 meters (TM35FIN bounds for Finland)"
          :gen/gen #?(:clj (gen/large-integer* {:min 6500000 :max 7800000})
                      :cljs nil)}]))

(def coordinates
  "WGS84 coordinates [longitude, latitude, altitude?] for Finland.
   Uses the individual lon, lat, and altitude schemas for validation.
   - Longitude: 18.0-33.0°E (Finland bounds with tolerance)
   - Latitude: 59.0-71.0°N (Finland bounds with tolerance)
   - Altitude: -10,000 to +2,000 meters (optional, see altitude schema for details)
   Accepts both integers and doubles. Rejects Infinity and NaN."
  (m/schema
   [:cat {:gen/fmap vec} ;; Convert sequence to vector for generator
    ;; Longitude - use the lon schema
    #'lon
    ;; Latitude - use the lat schema
    #'lat
    ;; Altitude - use the altitude schema (optional)
    [:? #'altitude]]))

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
  (m/schema
   [:map {:description "GeoJSON Point geometry"}
    [:type [:enum "Point"]]
    ;; Use the coordinates schema's built-in generator for randomness
    [:coordinates #'coordinates]]))

(def line-string-geometry
  (m/schema
   [:map {:description "GeoJSON LineString geometry. Per RFC 7946, requires 2+ positions."}
    [:type [:enum "LineString"]]
    [:coordinates
     ;; Use the vector generator with min 2, max 10 coordinates for variety
     [:vector {:min 2} #'coordinates]]]))

(def polygon-geometry
  (m/schema
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
       valid-polygon-coordinates?]]]]))

(def point-feature
  (m/schema
   [:map {:description "GeoJSON Feature with required Point geometry."}
    [:type [:enum "Feature"]]
    [:id {:optional true} :string]
    [:geometry #'point-geometry]
    [:properties {:optional true} [:map]]]))

(def line-string-feature
  (m/schema
   [:map {:description "GeoJSON Feature with required LineString geometry."}
    [:type [:enum "Feature"]]
    [:id {:optional true} :string]
    [:geometry #'line-string-geometry]
    [:properties {:optional true} [:map]]]))

(def polygon-feature
  (m/schema
   [:map {:description "GeoJSON Feature with required Polygon geometry."}
    [:type [:enum "Feature"]]
    [:id {:optional true} :string]
    [:geometry #'polygon-geometry]
    [:properties {:optional true} [:map]]]))

(def point-feature-collection
  (m/schema
   [:map {:description "GeoJSON FeatureCollection with required Point geometries."}
    [:type [:enum "FeatureCollection"]]
    [:features
     [:sequential #'point-feature]]]))

(def line-string-feature-collection
  (m/schema
   [:map {:description "GeoJSON FeatureCollection with required LineString geometries."}
    [:type [:enum "FeatureCollection"]]
    [:features
     [:sequential #'line-string-feature]]]))

(def polygon-feature-collection
  (m/schema
   [:map {:description "GeoJSON FeatureCollection with required Polygon geometries."}
    [:type [:enum "FeatureCollection"]]
    [:features
     [:sequential #'polygon-feature]]]))

;; Map view bounds (wider than Finland for zoomed-out views)
(def map-wgs84-bounds-lat
  (m/schema [:and number [:fn #(<= 32.88 % 84.73)]]))

(def map-wgs84-bounds-lon
  (m/schema [:and number [:fn #(<= 16.1 % 40.18)]]))

;; Finland coordinate bounds (tighter, for data validation)
(def finland-bounds-lat
  (m/schema [:and number [:fn #(<= 59.846373196 % 70.1641930203)]]))

(def finland-bounds-lon
  (m/schema [:and number [:fn #(<= 20.6455928891 % 31.5160921567)]]))

(comment
  (require '[malli.core :as m])
  (m/schema point-geometry)
  (m/schema line-string-geometry))
