(ns lipas.backend.gis
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [geo.io :as gio]
   [geo.jts :as jts]
   [geo.spatial :as geo])
  (:import
   [org.locationtech.geowave.analytic GeometryHullTool]
   [org.locationtech.geowave.analytic.distance CoordinateEuclideanDistanceFn]
   [org.locationtech.jts.algorithm ConvexHull]
   [org.locationtech.jts.geom.util GeometryCombiner]
   [org.locationtech.jts.operation.buffer BufferOp]
   [org.locationtech.jts.operation.distance DistanceOp]
   [org.locationtech.jts.simplify DouglasPeuckerSimplifier]))

(def srid 4326) ;; WGS84
(def tm35fin-srid 3067)
(def default-simplify-tolerance 0.001) ; ~111m

(def hull-tool
  (doto (GeometryHullTool.)
    (.setDistanceFnForCoordinate (CoordinateEuclideanDistanceFn.))))

(defn- simplify-geom [m tolerance]
  (update m :geometry #(-> %
                           (DouglasPeuckerSimplifier/simplify tolerance)
                           (jts/set-srid srid))))

(defn- dummy-props [m]
  (assoc m :properties {}))

(defn point->dummy-area
  [fcoll]
  (let [coords (-> fcoll :features first :geometry :coordinates)
        [y x]  coords
        delta  0.001] ; ~111m in WGS84
    {:type     "FeatureCollection"
     :features [{:type       "Feature"
                 :geometry {:type        "Polygon"
                            :coordinates [[coords
                                           [y (+ x delta)]
                                           [(+ y delta) (+ x delta)]
                                           [(+ y delta) x]
                                           coords]]}}]}))

(defn point? [fcoll]
  (= "Point" (-> fcoll :features first :geometry :type)))

(defn simplify
  "Returns simplified version of `m` where `m` is a map representing
  GeoJSON FeatureCollection."
  ([m] (simplify m default-simplify-tolerance))
  ([m tolerance]
   (-> m
       (update :features #(map dummy-props %))
       json/encode
       (gio/read-geojson srid)
       (->> (map #(simplify-geom % tolerance)))
       gio/to-geojson-feature-collection
       (json/decode keyword))))

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

(defn wgs84->tm35fin-no-wrap [[lon lat]]
  (let [transformed (jts/transform-geom (jts/point lat lon) srid tm35fin-srid)]
    [(.getX transformed) (.getY transformed)]))

(defn epsg3067-point->envelope [[e n] delta]
  [[(- e delta) (+ n delta)] [(+ e delta) (- n delta)]])

(defn epsg3067-point->wgs84-envelope [coords delta]
  (let [envelope (epsg3067-point->envelope coords delta)]
    (mapv (fn [[lon lat]]
            (let [transformed (jts/transform-geom (jts/point lat lon) tm35fin-srid srid)]
              [(.getX transformed) (.getY transformed)]))
          envelope)))

(defn ->jts-geom
  ([f]
   (->jts-geom f srid))
  ([f srid]
   (-> f (update :features #(map dummy-props %))
       json/encode
       (gio/read-geojson srid)
       (->> (map :geometry))
       (GeometryCombiner.)
       (.combine))))

(defn wgs84wkt->tm35fin-geom [s]
  (-> s
      gio/read-wkt
      (jts/transform-geom srid tm35fin-srid)))

(defn transform-crs
  ([geom] (transform-crs geom srid tm35fin-srid))
  ([geom from-crs to-crs]
   (jts/transform-geom geom from-crs to-crs)))

(defn shortest-distance [g1 g2]
  (DistanceOp/distance g1 g2))

(def ->point geo/point)

(defn distance-point [p1 p2]
  (geo/distance p1 p2))

(defn nearest-points [g1 g2]
  (DistanceOp/nearestPoints g1 g2))

(defn strip-z
  [coords]
  (if (vector? coords)
    (subvec coords 0 2)
    (take 2 coords)))

(defn ->flat-coords [fcoll]
  (->> fcoll
      :features
      (map :geometry)
      (reduce
       (fn [res g]
         (case (:type g)

           "Point"
           (conj res (-> g :coordinates strip-z))

           "LineString"
           (into res (map strip-z) (:coordinates g))

           ("Polygon" "MultiLineString")
           (into res (->> g :coordinates (filter seq) (mapcat strip-z) (filter seq)))

           ("MultiPolygon")
           (into res (->> g :coordinates  (mapcat identity) (mapcat strip-z) (filter seq)))))
       [])
      (into [] #_(distinct))))

(defn contains-coords? [fcoll]
  (boolean (seq (->flat-coords fcoll))))

(defn ->jts-point [lon lat]
  (geo/jts-point lat lon))

(defn ->jts-multi-point [coords]
  (->> coords
       (map #(geo/jts-point (second %) (first %)))
       (GeometryCombiner.)
       (.combine)))

(defn wkt-point->coords [s]
  (let [coord (-> s
                  gio/read-wkt
                  (.getCoordinate))]
    [(.getX coord) (.getY coord)]))

(defn ->wkt [g]
  (format "POINT (%s %s)" (.getX g) (.getY g)))

(defn calc-buffer [fcoll distance-m]
  (-> fcoll
      (->jts-geom)
      (jts/transform-geom srid tm35fin-srid)
      (BufferOp.)
      (.getResultGeometry distance-m)
      (jts/transform-geom tm35fin-srid srid)
      gio/to-geojson
      (json/decode keyword)))

(defn concave-hull [fcoll]
  (let [points (-> fcoll ->flat-coords ->jts-multi-point)
        convex (-> points ConvexHull. .getConvexHull)]
    (.concaveHull hull-tool convex (into [] (.getCoordinates points)))))

(defn ->single-linestring-coords [fcoll]
  (-> fcoll
      concave-hull
      .getExteriorRing
      .getCoordinates
      (->> (map #(vector (.getX %) (.getY %))))))

(defn ->coord-pair-strs [fcoll]
  (if (point? fcoll)
    [(-> fcoll :features first :geometry :coordinates
         (->> (str/join ",")))]
    (let [points (->flat-coords fcoll)]
      (if (> 10 (count points))
        (map #(str/join "," %) points)
        (-> fcoll
            ->single-linestring-coords
            (->> (map #(str/join "," %))))))))

(defn dedupe-polygon-coords
  [fcoll]
  (update fcoll :features
          (fn [fs]
            (map
             (fn [f]
               (update-in f [:geometry :coordinates] #(map dedupe %)))
             fs))))



(defn ->fcoll [features]
  {:type     "FeatureCollection"
   :features (mapv #(update % :properties (fnil identity {})) features)})

(defn ->feature [geom]
  {:type "Feature" :geometry geom})

(defn ->tm35fin-envelope
  ([fcoll]
   (->tm35fin-envelope fcoll 0))
  ([fcoll buff-m]
   (let [envelope (-> fcoll
                      ->jts-geom
                      .getEnvelope
                      (jts/transform-geom srid tm35fin-srid)
                      jts/get-envelope-internal
                      (doto (.expandBy buff-m)))]
     {:max-x (.getMaxX envelope)
      :max-y (.getMaxY envelope)
      :min-x (.getMinX envelope)
      :min-y (.getMinY envelope)})))

(defn get-envelope
  ([jts-geom]
   (get-envelope jts-geom 0))
  ([jts-geom buff-m]
   (let [envelope (-> jts-geom
                      jts/get-envelope-internal
                      (doto (.expandBy buff-m)))]
     {:max-x (.getMaxX envelope)
      :max-y (.getMaxY envelope)
      :min-x (.getMinX envelope)
      :min-y (.getMinY envelope)})))

(defn intersects-envelope?
  [{:keys [min-x max-x min-y max-y]} jts-geom]
  (let [jts-envelope (-> [(jts/coordinate min-x min-y)
                          (jts/coordinate min-x max-y)
                          (jts/coordinate max-x max-y)
                          (jts/coordinate max-x min-y)
                          (jts/coordinate min-x min-y)]
                         (jts/linear-ring tm35fin-srid)
                         jts/polygon)]
    (.intersects jts-envelope jts-geom)))

(defn chunk-envelope
  "Chunks given envelope to multiple envelopes of `max-size` squares +
  possible reminder rectangles where sides don't exceed `max-size`."
  [{:keys [min-x max-x min-y max-y]} max-size]
  (let [n-max-x (Math/floor (/ (- max-x min-x) max-size))
        n-max-y (Math/floor (/ (- max-y min-y) max-size))
        rem-x   (mod (- max-x min-x) max-size)
        rem-y   (mod (- max-y min-y) max-size)]
    (into []
          (for [row (range (if (zero? rem-y) n-max-y (inc n-max-y)))
                col (range (if (zero? rem-x) n-max-x (inc n-max-x)))]
            (let [cur-min-x (+ min-x (* col max-size))
                  cur-max-x (+ min-x (* (inc col) max-size))
                  cur-min-y (+ min-y (* row max-size))
                  cur-max-y (+ min-y (* (inc row) max-size))]
              {:min-x (if (> (+ cur-min-x max-size) max-x)
                        (if (zero? rem-x) (- max-x max-size) (- max-x rem-x))
                        cur-min-x)
               :max-x (if (> (+ cur-min-x max-size) max-x)
                        max-x
                        cur-max-x)
               :min-y (if (> (+ cur-min-y max-size) max-y)
                        (if (zero? rem-y) (- max-y max-size) (- max-y rem-y))
                        cur-min-y)
               :max-y (if (> (+ cur-min-y max-size) max-y)
                        max-y
                        cur-max-y)})))))

(comment
  (chunk-envelope {:min-x 0 :max-x 10 :min-y 0 :max-y 100} 10)
  (chunk-envelope {:min-x 0 :max-x 10 :min-y 0 :max-y 102} 10)
  (chunk-envelope {:min-x 0 :max-x 10 :min-y 0 :max-y 10} 10)
  (chunk-envelope {:min-x 0 :max-x 11 :min-y 0 :max-y 11} 10)
  (chunk-envelope {:min-x 0 :max-x 21 :min-y 0 :max-y 21} 10)
  (chunk-envelope {:min-x 10 :max-x 20 :min-y 10 :max-y 20} 10)
  (chunk-envelope {:min-x 10 :max-x 21 :min-y 10 :max-y 21} 10)
  (chunk-envelope {:min-x 10 :max-x 31 :min-y 10 :max-y 31} 10))

(comment

  (wgs84->tm35fin [23.8259457479965 61.4952794263427])
  (wkt-point->coords "POINT (29.1946713328528 63.1707254363858)")

  (def test-point
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [25.720539797408946,
                      62.62057217751676]}}]})

  (time (intersects-envelope? {:min-x 0 :max-x 10 :min-y 0 :max-y 100}  test-point))
  (time (intersects-envelope? {:min-x 44000 :max-x 740000 :min-y 6594000 :max-y 7782000}  test-point))

  (-> test-point ->flat-coords)

  (-> test-point
      ->flat-coords
      (->> (map wgs84->tm35fin-no-wrap))
      ->jts-multi-point
      jts/get-envelope-internal
      (doto (.expandBy 1)))
  ;; "Env[434354.5312499977 : 434356.5312499977, 6943965.504886635 : 6943967.504886635]"
  ;; "Env[434355.5312499977 : 434355.5312499977, 6943966.504886635 : 6943966.504886635]"
  ;; "Env[434345.5312499977 : 434365.5312499977, 6943956.504886635 : 6943976.504886635]"
  ;; "Env[434353.5312499977 : 434357.5312499977, 6943964.504886635 : 6943968.504886635]"
  ;; "Env[434353.5312499977 : 434357.5312499977, 6943964.504886635 : 6943968.504886635]"

  ;; => ([434355.5312499977 6943966.504886635])

  (Math/round 434355.5312499977)
  (->tm35fin-envelope test-point 2)

  (def test-point2
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [26.720539797408946,
                      61.62057217751676]}}]})

  (distance-point (-> test-point :features first :geometry)
                  (-> test-point2 :features first :geometry))

  (def buff (calc-buffer test-point 10))

  (json/encode
   {:type "FeatureCollection"
    :features
    [{:type "Feature"
      :geometry buff}]})

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
      {:type "Feature"
       :geometry
       {:type "LineString",
        :coordinates
        [[26.2436550567509, 63.9531552213109],
         [25.7583312263512, 63.9746827436437]]}}]})

  (-> test-route ->flat-coords)

  (->tm35fin-envelope test-route)

  (-> test-route
      ->jts-geom
      )

  (->single-linestring-coords test-route)

  (def mp (->> test-route
        ->flat-coords
        ->jts-multi-point
        ))

  (def coords (.getCoordinates mp))

  (into [] coords)

  (def convex (.getConvexHull (ConvexHull. mp)))

  (.getSRID convex)


  (.setDistanceFnForCoordinate hull-tool )
  (.getDistanceFnForCoordinate hull-tool)

  hull-tool

  (def concave (.concaveHull hull-tool convex (into [] coords)))

  concave

  (->> [{:geometry concave :properties {}}]
       gio/to-geojson-feature-collection
       (spit "/Users/tipo/Desktop/concave.json"))

  (->> [{:geometry convex :properties {}}]
       gio/to-geojson-feature-collection
       (spit "/Users/tipo/Desktop/convex.json"))

  (->> test-route
       json/encode
       (spit "/Users/tipo/Desktop/coriginal.json"))

  (def test-polygon
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[26.2436753445903, 63.9531598143881],
          [26.4505514903968, 63.9127506671744]
          [26.2436753445903, 63.9531598143881]]]}},
      {:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[26.2436550567509, 63.9531552213109],
          [25.7583312263512, 63.9746827436437]
          [26.2436550567509, 63.9531552213109]]]}}]})

  (-> test-polygon ->flat-coords)

  (->tm35fin-envelope test-polygon)

  (require '[lipas.backend.osrm :as osrm])

  (osrm/resolve-sources test-polygon)

  (->> test-polygon
       ->jts-geom)

  (->> test-polygon
       concave-hull
       )

  (centroid test-route)

  (def test-point2
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [19.720539797408946,
                      65.62057217751676]}}]})

  (geo/bounding-box test-point2)

  (calc-buffer test-point2 100)

  (->fcoll [(->feature (calc-buffer test-point2 100))])

  (point? test-point)
  (point? test-polygon)

  (map osrm/resolve-sources [test-point test-route test-polygon])

  (shortest-distance test-point test-route)

  (->flat-coords test-polygon)

  (def p100 [25.742855072021484
             62.240251303552284])

  (def p101 (wgs84->tm35fin p100))

  (def ->tm35fin-envelope
    (epsg3067-point->wgs84-envelope [(:easting p101) (:northing p101)] 125))

  (def test-polygon-empty
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[],
          []
          []]]}},
      {:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[],
          []
          []]]}}]})

  (->flat-coords test-polygon-empty)

  (contains-coords? test-point)
  (contains-coords? test-route)
  (contains-coords? test-polygon)
  (contains-coords? test-polygon-empty)

  )
