(ns lipas.backend.gis-test
  "Characterization tests for lipas.backend.gis, written as a safety net
  ahead of the dependency rewrite (factual/geo + geowave GeometryHullTool
  -> direct JTS 1.20 interop + proj4j, with JTS built-in ConcaveHull).

  Design notes:

  - Concave hull tests assert algorithm-agnostic invariants only, because
    the hull algorithm itself will change. NOTE: the current geowave-based
    hull does NOT necessarily contain all input points (interior points can
    be 'carved past'), so containment of all inputs is deliberately NOT an
    invariant here. The invariants used hold for both geowave's hull and
    JTS ConcaveHull: valid simple polygon, closed exterior ring, hull
    vertices are a subset of input points, hull is covered by the convex
    hull, hull area <= convex hull area, hull envelope equals input
    envelope, convex-hull vertices lie on the hull.

  - CRS transform assertions are tolerance-based (1 m projected, 1e-6 deg
    geographic) with reference values evaluated from the current
    implementation and sanity-checked against known TM35FIN values.

  - sequence-features is covered by lipas.backend.gis-sequencing-test
    and intentionally not duplicated here."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [lipas.backend.gis :as gis])
  (:import
    [org.locationtech.jts.algorithm ConvexHull]
    [org.locationtech.jts.geom Geometry]
    [org.locationtech.jts.operation.distance DistanceOp]))

;;; Tolerances ;;;

(def m-tol "Tolerance for projected (EPSG:3067) coordinates, meters" 1.0)
(def deg-tol "Tolerance for geographic (WGS84) coordinates, degrees" 1e-6)
(def eps 1e-9)

(defn- approx=
  ([expected actual] (approx= expected actual eps))
  ([expected actual tol]
   (< (Math/abs (- (double expected) (double actual))) (double tol))))

;;; Fixtures (from the rich comment block in lipas.backend.gis and
;;; real Finnish geometries) ;;;

(def helsinki-wgs84 [24.9384 60.1699])
;; Reference values from the current implementation (proj4j),
;; sanity-checked: Helsinki centre is ~E 385 600, N 6 672 100 in TM35FIN.
(def helsinki-tm35fin [385611.31668490137 6672118.3802054245])

(def jyvaskyla-wgs84 [25.7473 62.2426])
(def jyvaskyla-tm35fin [434912.2258726123 6901836.196624962])

(def tampere-wgs84 [23.8259457479965 61.4952794263427])
(def tampere-tm35fin [331055.0808543216 6822069.698338025])

(def test-point
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :geometry {:type "Point"
                :coordinates [25.720539797408946 62.62057217751676]}}]})

(def test-route
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :geometry {:type "LineString"
                :coordinates [[26.2436753445903 63.9531598143881]
                              [26.4505514903968 63.9127506671744]]}}
    {:type "Feature"
     :geometry {:type "LineString"
                :coordinates [[26.2436550567509 63.9531552213109]
                              [25.7583312263512 63.9746827436437]]}}]})

(def test-polygon
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :properties {}
     :geometry {:type "Polygon"
                :coordinates [[[26.2436753445903 63.9531598143881]
                               [26.4505514903968 63.9127506671744]
                               [26.4505514903968 63.9531598143881]
                               [26.2436753445903 63.9531598143881]]]}}
    {:type "Feature"
     :properties {}
     :geometry {:type "Polygon"
                :coordinates [[[26.2436550567509 63.9531552213109]
                               [25.7583312263512 63.9746827436437]
                               [25.7583312263512 63.9531552213109]
                               [26.2436550567509 63.9531552213109]]]}}]})

(def test-polygon-empty
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :properties {}
     :geometry {:type "Polygon" :coordinates [[[] [] []]]}}]})

;; Non-convex 12-point set in central Finland (>= 10 points so that
;; ->coord-pair-strs takes the concave-hull branch).
(def hull-pts
  [[25.0 62.0] [25.1 62.05] [25.2 62.0] [25.3 62.1] [25.25 62.2]
   [25.1 62.15] [25.0 62.2] [24.9 62.15] [24.85 62.05] [24.95 62.02]
   [25.05 62.08] [25.15 62.12]])

(defn- linestring-fcoll [coords]
  {:type "FeatureCollection"
   :features [{:type "Feature"
               :geometry {:type "LineString" :coordinates (vec coords)}}]})

(def hull-fcoll (linestring-fcoll hull-pts))

(defn- bbox [coords]
  {:min-x (apply min (map first coords))
   :max-x (apply max (map first coords))
   :min-y (apply min (map second coords))
   :max-y (apply max (map second coords))})

(defn- in-bbox? [{:keys [min-x max-x min-y max-y]} [x y] tol]
  (and (<= (- min-x tol) x (+ max-x tol))
       (<= (- min-y tol) y (+ max-y tol))))

;;; point->dummy-area & point? ;;;

(deftest point->dummy-area-test
  (let [result (gis/point->dummy-area test-point)
        ring (-> result :features first :geometry :coordinates first)
        orig (-> test-point :features first :geometry :coordinates)]
    (testing "returns a FeatureCollection with a single Polygon feature"
      (is (= "FeatureCollection" (:type result)))
      (is (= 1 (count (:features result))))
      (is (= "Polygon" (-> result :features first :geometry :type)))
      (is (false? (gis/point? result))))
    (testing "ring is closed and has 5 coordinates"
      (is (= 5 (count ring)))
      (is (= (first ring) (last ring)))
      (is (= orig (first ring))))
    (testing "ring stays within ~delta (1e-4 deg) of the original point"
      (is (every? #(in-bbox? (bbox [orig]) % 1.1e-4) ring)))
    (testing "produces a valid JTS polygon"
      (is (.isValid ^Geometry (gis/->jts-geom result))))))

(deftest point?-test
  (is (true? (gis/point? test-point)))
  (is (false? (gis/point? test-route)))
  (is (false? (gis/point? test-polygon))))

;;; centroid & ->centroid-point ;;;

(deftest centroid-test
  (testing "centroid of a single point is the point itself"
    (let [c (gis/centroid test-point)]
      (is (= "Point" (:type c)))
      (is (approx= 25.720539797408946 (-> c :coordinates first) deg-tol))
      (is (approx= 62.62057217751676 (-> c :coordinates second) deg-tol))))
  (testing "centroid of multi-feature route (length-weighted, JTS semantics)"
    (let [c (gis/centroid test-route)]
      (is (= "Point" (:type c)))
      ;; Reference value from current implementation
      (is (approx= 26.105728439821604 (-> c :coordinates first) deg-tol))
      (is (approx= 63.95454941661388 (-> c :coordinates second) deg-tol))))
  (testing "centroid of polygons falls within the input bounding box"
    (let [c (gis/centroid test-polygon)
          all-coords (->> test-polygon :features
                          (mapcat #(-> % :geometry :coordinates first)))]
      (is (in-bbox? (bbox all-coords) (:coordinates c) eps)))))

(deftest ->centroid-point-test
  (let [result (gis/->centroid-point test-polygon)]
    (is (= "FeatureCollection" (:type result)))
    (is (= 1 (count (:features result))))
    (is (gis/point? result))
    (is (= {} (-> result :features first :properties)))
    (is (= (:coordinates (gis/centroid test-polygon))
           (-> result :features first :geometry :coordinates)))))

;;; CRS transforms ;;;

(deftest wgs84->tm35fin-test
  (testing "known reference points within 1 m"
    (doseq [[wgs84 [e n] city] [[helsinki-wgs84 helsinki-tm35fin "Helsinki"]
                                [jyvaskyla-wgs84 jyvaskyla-tm35fin "Jyväskylä"]
                                [tampere-wgs84 tampere-tm35fin "Tampere"]]]
      (let [{:keys [easting northing]} (gis/wgs84->tm35fin wgs84)]
        (is (approx= e easting m-tol) (str city " easting"))
        (is (approx= n northing m-tol) (str city " northing"))))))

(deftest wgs84->tm35fin-no-wrap-test
  (let [[e n] (gis/wgs84->tm35fin-no-wrap helsinki-wgs84)]
    (is (approx= (first helsinki-tm35fin) e m-tol))
    (is (approx= (second helsinki-tm35fin) n m-tol))
    (testing "agrees with the wrapped variant"
      (let [{:keys [easting northing]} (gis/wgs84->tm35fin helsinki-wgs84)]
        (is (= [easting northing] [e n]))))))

(deftest epsg3067-point->envelope-test
  (testing "pure arithmetic: [[e-d n+d] [e+d n-d]]"
    (is (= [[90.0 210.0] [110.0 190.0]]
           (gis/epsg3067-point->envelope [100.0 200.0] 10)))
    (is (= [[100.0 200.0] [100.0 200.0]]
           (gis/epsg3067-point->envelope [100.0 200.0] 0)))))

(deftest epsg3067-point->wgs84-envelope-test
  (testing "delta 0 inverts wgs84->tm35fin (round-trip identity)"
    (let [[lon lat] jyvaskyla-wgs84
          [e n] (gis/wgs84->tm35fin-no-wrap [lon lat])
          [[lon1 lat1] [lon2 lat2]] (gis/epsg3067-point->wgs84-envelope [e n] 0)]
      (is (approx= lon lon1 deg-tol))
      (is (approx= lat lat1 deg-tol))
      (is (approx= lon lon2 deg-tol))
      (is (approx= lat lat2 deg-tol))))
  (testing "non-zero delta produces corners ~delta meters from center"
    (let [[e n] (gis/wgs84->tm35fin-no-wrap jyvaskyla-wgs84)
          delta 100
          [[lon1 lat1] [lon2 lat2]] (gis/epsg3067-point->wgs84-envelope [e n] delta)
          c1 (gis/wgs84->tm35fin-no-wrap [lon1 lat1])
          c2 (gis/wgs84->tm35fin-no-wrap [lon2 lat2])]
      ;; first corner is top-left [e-d n+d], second bottom-right [e+d n-d]
      (is (approx= (- e delta) (first c1) m-tol))
      (is (approx= (+ n delta) (second c1) m-tol))
      (is (approx= (+ e delta) (first c2) m-tol))
      (is (approx= (- n delta) (second c2) m-tol))
      (is (< lon1 lon2))
      (is (> lat1 lat2)))))

(deftest wgs84wkt->tm35fin-geom-test
  (let [g (gis/wgs84wkt->tm35fin-geom "POINT (24.9384 60.1699)")]
    (is (approx= (first helsinki-tm35fin) (.getX g) m-tol))
    (is (approx= (second helsinki-tm35fin) (.getY g) m-tol))))

(deftest transform-crs-test
  (testing "1-arity defaults to wgs84 -> tm35fin"
    (let [g (gis/transform-crs (gis/->jts-point 24.9384 60.1699))]
      (is (approx= (first helsinki-tm35fin) (.getX g) m-tol))
      (is (approx= (second helsinki-tm35fin) (.getY g) m-tol))))
  (testing "3-arity with explicit CRS codes"
    (let [g (gis/transform-crs (gis/->jts-point 24.9384 60.1699) 4326 3067)]
      (is (approx= (first helsinki-tm35fin) (.getX g) m-tol))
      (is (approx= (second helsinki-tm35fin) (.getY g) m-tol)))))

;;; Generative CRS round-trip over Finland bounds ;;;

(def finland-lon (gen/double* {:min 19.1 :max 31.5 :infinite? false :NaN? false}))
(def finland-lat (gen/double* {:min 59.5 :max 70.1 :infinite? false :NaN? false}))
(def finland-coord (gen/tuple finland-lon finland-lat))

(defspec wgs84-tm35fin-round-trip-prop 50
  (prop/for-all [[lon lat] finland-coord]
                (let [[e n] (gis/wgs84->tm35fin-no-wrap [lon lat])
                      [[lon' lat'] _] (gis/epsg3067-point->wgs84-envelope [e n] 0)]
                  (and ;; projected coords land in a plausible TM35FIN range
                    (< 0 e 1000000)
                    (< 6400000 n 7900000)
       ;; inverse transform recovers the original coordinate
                    (approx= lon lon' 1e-5)
                    (approx= lat lat' 1e-5)))))

;;; JTS geometry construction & conversions ;;;

(deftest ->jts-geom-test
  (testing "single point feature"
    (let [g ^Geometry (gis/->jts-geom test-point)]
      (is (= "Point" (.getGeometryType g)))
      (is (approx= 25.720539797408946 (.getX g) deg-tol))
      (is (approx= 62.62057217751676 (.getY g) deg-tol))))
  (testing "multi-feature route combines into a MultiLineString"
    (let [g ^Geometry (gis/->jts-geom test-route)]
      (is (= "MultiLineString" (.getGeometryType g)))
      (is (= 2 (.getNumGeometries g)))))
  (testing "polygon features combine and are valid"
    (let [g ^Geometry (gis/->jts-geom test-polygon)]
      (is (= 2 (.getNumGeometries g)))
      (is (.isValid g)))))

(deftest ->jts-point-test
  (let [p (gis/->jts-point 24.9384 60.1699)]
    (is (= "Point" (.getGeometryType p)))
    (testing "x is lon, y is lat"
      (is (= 24.9384 (.getX p)))
      (is (= 60.1699 (.getY p))))
    (is (= 4326 (.getSRID p)))))

(deftest ->jts-multi-point-test
  (let [coords [[25.0 62.0] [25.1 62.1] [25.2 62.0]]
        mp (gis/->jts-multi-point coords)]
    (is (= "MultiPoint" (.getGeometryType mp)))
    (is (= 3 (.getNumGeometries mp)))
    (testing "preserves lon as x, lat as y"
      (is (= coords
             (mapv (fn [c] [(.x c) (.y c)]) (.getCoordinates mp)))))))

(deftest wkt-point->coords-test
  (is (= [29.1946713328528 63.1707254363858]
         (gis/wkt-point->coords "POINT (29.1946713328528 63.1707254363858)"))))

(deftest ->wkt-test
  (let [p (gis/->jts-point 24.9384 60.1699)]
    (is (= "POINT (24.9384 60.1699)" (gis/->wkt p)))
    (testing "round-trips through wkt-point->coords"
      (is (= [24.9384 60.1699] (gis/wkt-point->coords (gis/->wkt p)))))))

;;; Distances ;;;

(deftest shortest-distance-test
  (testing "planar (degree) distance between two JTS points"
    (let [p1 (gis/->jts-point 24.9384 60.1699)
          p2 (gis/->jts-point 25.7473 62.2426)
          expected (Math/sqrt (+ (Math/pow (- 25.7473 24.9384) 2)
                                 (Math/pow (- 62.2426 60.1699) 2)))]
      (is (approx= expected (gis/shortest-distance p1 p2) eps))
      (is (zero? (gis/shortest-distance p1 p1))))))

(deftest ->point-and-distance-point-test
  (testing "->point takes (lat lon), exposes x=lon y=lat"
    (let [p (gis/->point 60.1699 24.9384)]
      (is (approx= 24.9384 (.getX p) eps))
      (is (approx= 60.1699 (.getY p) eps))))
  (testing "distance-point returns meters (Helsinki–Jyväskylä ~235 km)"
    (let [p1 (gis/->point 60.1699 24.9384)
          p2 (gis/->point 62.2426 25.7473)]
      (is (approx= 235019.0 (gis/distance-point p1 p2) 2000.0))
      (is (zero? (gis/distance-point p1 p1))))))

(deftest nearest-points-test
  (let [p1 (gis/->jts-point 24.9384 60.1699)
        p2 (gis/->jts-point 25.7473 62.2426)
        [c1 c2] (gis/nearest-points p1 p2)]
    (is (approx= 24.9384 (.x c1) eps))
    (is (approx= 60.1699 (.y c1) eps))
    (is (approx= 25.7473 (.x c2) eps))
    (is (approx= 62.2426 (.y c2) eps))))

;;; Coordinate plumbing ;;;

(deftest strip-z-test
  (is (= [1.0 2.0] (gis/strip-z [1.0 2.0 3.0])))
  (is (= [1.0 2.0] (gis/strip-z [1.0 2.0])))
  (testing "non-vector seqs are handled lazily via take"
    (is (= [1.0 2.0] (vec (gis/strip-z (list 1.0 2.0 3.0)))))))

(deftest strip-z-fcoll-test
  (testing "Point"
    (is (= [25.0 62.0]
           (-> {:type "FeatureCollection"
                :features [{:type "Feature"
                            :geometry {:type "Point"
                                       :coordinates [25.0 62.0 100.5]}}]}
               gis/strip-z-fcoll :features first :geometry :coordinates))))
  (testing "LineString"
    (is (= [[1.0 2.0] [4.0 5.0]]
           (-> {:type "FeatureCollection"
                :features [{:type "Feature"
                            :geometry {:type "LineString"
                                       :coordinates [[1.0 2.0 3.0] [4.0 5.0 6.0]]}}]}
               gis/strip-z-fcoll :features first :geometry :coordinates))))
  (testing "Polygon"
    (is (= [[[1.0 2.0] [3.0 4.0] [5.0 6.0] [1.0 2.0]]]
           (-> {:type "FeatureCollection"
                :features [{:type "Feature"
                            :geometry {:type "Polygon"
                                       :coordinates [[[1.0 2.0 9.0] [3.0 4.0 9.0]
                                                      [5.0 6.0 9.0] [1.0 2.0 9.0]]]}}]}
               gis/strip-z-fcoll :features first :geometry :coordinates))))
  (testing "2D input passes through unchanged"
    (is (= test-route (gis/strip-z-fcoll test-route)))))

(deftest ->flat-coords-test
  (testing "Point yields a single coordinate pair, z stripped"
    (is (= [[25.720539797408946 62.62057217751676]]
           (gis/->flat-coords test-point)))
    (is (= [[25.0 62.0]]
           (gis/->flat-coords
             {:type "FeatureCollection"
              :features [{:type "Feature"
                          :geometry {:type "Point"
                                     :coordinates [25.0 62.0 100.5]}}]}))))
  (testing "LineString yields all coordinates"
    (is (= [[26.2436753445903 63.9531598143881]
            [26.4505514903968 63.9127506671744]
            [26.2436550567509 63.9531552213109]
            [25.7583312263512 63.9746827436437]]
           (gis/->flat-coords test-route))))
  (testing "Polygon: current behavior takes only the FIRST TWO coordinates
            of each ring (strip-z is applied at ring level)"
    (is (= [[26.2436753445903 63.9531598143881]
            [26.4505514903968 63.9127506671744]
            [26.2436550567509 63.9531552213109]
            [25.7583312263512 63.9746827436437]]
           (gis/->flat-coords test-polygon))))
  (testing "MultiLineString: same first-two-per-linestring behavior"
    (is (= [[25.0 62.0] [25.1 62.1] [26.0 63.0] [26.1 63.1]]
           (gis/->flat-coords
             {:type "FeatureCollection"
              :features [{:type "Feature"
                          :geometry {:type "MultiLineString"
                                     :coordinates [[[25.0 62.0] [25.1 62.1] [25.2 62.2]]
                                                   [[26.0 63.0] [26.1 63.1]]]}}]}))))
  (testing "MultiPolygon: first two coordinates of each ring"
    (is (= [[25.0 62.0] [25.1 62.0]]
           (gis/->flat-coords
             {:type "FeatureCollection"
              :features [{:type "Feature"
                          :geometry {:type "MultiPolygon"
                                     :coordinates [[[[25.0 62.0] [25.1 62.0]
                                                     [25.1 62.1] [25.0 62.0]]]]}}]}))))
  (testing "empty rings are filtered out"
    (is (= [] (gis/->flat-coords test-polygon-empty)))))

(deftest contains-coords?-test
  (is (true? (gis/contains-coords? test-point)))
  (is (true? (gis/contains-coords? test-route)))
  (is (true? (gis/contains-coords? test-polygon)))
  (is (false? (gis/contains-coords? test-polygon-empty)))
  (is (false? (gis/contains-coords? {:type "FeatureCollection" :features []}))))

;;; Simplification ;;;

(def zigzag-line
  "Nearly-straight diagonal with sub-tolerance zigzag perturbations."
  (vec (for [i (range 21)]
         [(+ 25.0 (* i 0.01) (if (odd? i) 0.0001 0.0)) (+ 62.0 (* i 0.01))])))

(deftest simplify-test
  (let [fcoll (linestring-fcoll zigzag-line)
        simplified (gis/simplify fcoll)
        out-coords (-> simplified :features first :geometry :coordinates)]
    (testing "keeps FeatureCollection shape and geometry type"
      (is (= "FeatureCollection" (:type simplified)))
      (is (= 1 (count (:features simplified))))
      (is (= "LineString" (-> simplified :features first :geometry :type))))
    (testing "reduces point count; endpoints preserved"
      (is (<= (count out-coords) (count zigzag-line)))
      (is (= (first zigzag-line) (first out-coords)))
      (is (= (last zigzag-line) (last out-coords))))
    (testing "sub-tolerance zigzag collapses to its two endpoints"
      (is (= 2 (count out-coords))))
    (testing "all output coordinates are a subset of input coordinates
              (Douglas-Peucker keeps original vertices)"
      (is (every? (set zigzag-line) out-coords)))
    (testing "zero-ish tolerance preserves every coordinate"
      (is (= zigzag-line
             (-> (gis/simplify fcoll 0.0)
                 :features first :geometry :coordinates))))
    (testing "current behavior: feature properties are wiped to {}"
      ;; simplify replaces :properties with {} (dummy-props) before
      ;; the GeoJSON round-trip. Characterized on purpose.
      (is (= {}
             (-> (linestring-fcoll zigzag-line)
                 (assoc-in [:features 0 :properties] {:name "foo"})
                 gis/simplify
                 :features first :properties))))
    (testing "multi-feature collections keep all features"
      (is (= 2 (count (:features (gis/simplify test-route))))))))

(deftest simplify-safe-test
  (testing "well-formed input simplifies like simplify"
    (let [fcoll (linestring-fcoll zigzag-line)]
      (is (= (gis/simplify fcoll) (gis/simplify-safe fcoll)))))
  (testing "falls back to original input when simplification throws
            (MultiPoint is unsupported by ->flat-coords)"
    (let [fcoll {:type "FeatureCollection"
                 :features [{:type "Feature"
                             :geometry {:type "MultiPoint"
                                        :coordinates [[25.0 62.0] [25.1 62.1]]}}]}]
      (is (= fcoll (gis/simplify-safe fcoll))))))

;;; Buffering ;;;

(deftest calc-buffer-test
  (testing "point buffered by 100 m"
    (let [buff (gis/calc-buffer test-point 100)
          ring (-> buff :coordinates first)
          center (-> test-point :features first :geometry :coordinates)
          [ce cn] (gis/wgs84->tm35fin-no-wrap center)]
      (is (= "Polygon" (:type buff)))
      (is (= (first ring) (last ring)) "ring is closed")
      (testing "every ring vertex is ~100 m from the center (in TM35FIN)"
        (is (every? (fn [coord]
                      (let [[e n] (gis/wgs84->tm35fin-no-wrap coord)
                            d (Math/sqrt (+ (Math/pow (- e ce) 2)
                                            (Math/pow (- n cn) 2)))]
                        (approx= 100.0 d m-tol)))
                    ring)))
      (testing "buffer covers the original point"
        (is (.covers ^Geometry (gis/->jts-geom (gis/->fcoll [(gis/->feature buff)]))
                     (gis/->jts-point (first center) (second center)))))))
  (testing "linestring buffer covers the original route"
    (let [buff (gis/calc-buffer test-route 50)]
      (is (.covers ^Geometry (gis/->jts-geom (gis/->fcoll [(gis/->feature buff)]))
                   ^Geometry (gis/->jts-geom test-route))))))

;;; Concave hull and friends (algorithm-agnostic invariants) ;;;

(defn- hull-invariants
  "Returns a map of named boolean invariants that must hold for ANY sane
  concave hull implementation (geowave GeometryHullTool, JTS ConcaveHull)."
  [pts]
  (let [fcoll (linestring-fcoll pts)
        hull ^Geometry (gis/concave-hull fcoll)
        mp ^Geometry (gis/->jts-multi-point pts)
        convex ^Geometry (.getConvexHull (ConvexHull. mp))
        ring (.getExteriorRing hull)
        verts (mapv (fn [c] [(.x c) (.y c)]) (.getCoordinates ring))
        b (bbox pts)]
    {:polygon? (= "Polygon" (.getGeometryType hull))
     :valid? (.isValid hull)
     :ring-closed? (= (first verts) (last verts))
     :enough-verts? (>= (count verts) 4)
     :area<=convex? (<= (.getArea hull) (+ (.getArea convex) eps))
     :covered-by-convex? (.covers (.buffer convex eps) hull)
     :verts-subset-of-input?
     (every? (fn [[x y]]
               (some (fn [[px py]]
                       (and (approx= px x) (approx= py y)))
                     pts))
             verts)
     :convex-verts-on-hull?
     (every? (fn [c]
               (<= (DistanceOp/distance hull (gis/->jts-point (.x c) (.y c)))
                   eps))
             (.getCoordinates (.getExteriorRing convex)))
     :verts-in-input-bbox? (every? #(in-bbox? b % eps) verts)
     :envelope-preserved?
     (let [eh (.getEnvelopeInternal hull)]
       (and (approx= (:min-x b) (.getMinX eh))
            (approx= (:max-x b) (.getMaxX eh))
            (approx= (:min-y b) (.getMinY eh))
            (approx= (:max-y b) (.getMaxY eh))))}))

(deftest concave-hull-test
  (testing "12-point non-convex fixture"
    (doseq [[invariant holds?] (hull-invariants hull-pts)]
      (is (true? holds?) (name invariant))))
  (testing "4-point route fixture"
    (doseq [[invariant holds?] (hull-invariants (gis/->flat-coords test-route))]
      (is (true? holds?) (name invariant)))))

(defn- non-degenerate-pts?
  "The concave-hull contract only makes sense for point sets whose convex
  hull is an actual polygon. NOTE: the current geowave implementation
  throws IllegalArgumentException on degenerate (all-identical/collinear)
  inputs; production call sites only invoke it with >= 10 real route
  coordinates, so degenerate inputs are excluded from the contract."
  [pts]
  (and (apply distinct? pts)
       (> (.getArea ^Geometry (.getConvexHull (ConvexHull. (gis/->jts-multi-point pts))))
          1e-9)))

(def hull-pts-gen
  (gen/such-that non-degenerate-pts? (gen/vector finland-coord 10 25) 100))

(defspec concave-hull-invariants-prop 15
  ;; :valid? is excluded here on purpose: the current geowave hull can
  ;; produce self-intersecting (invalid) polygons when the input contains
  ;; many collinear points. JTS ConcaveHull always produces valid output,
  ;; so this property only gets STRICTER after the rewrite. Validity is
  ;; still asserted on the realistic fixtures in concave-hull-test.
  (prop/for-all [pts hull-pts-gen]
                (every? true? (vals (dissoc (hull-invariants pts) :valid?)))))

(deftest ->single-linestring-coords-test
  (let [coords (gis/->single-linestring-coords hull-fcoll)
        b (bbox hull-pts)]
    (testing "forms a closed ring"
      (is (>= (count coords) 4))
      (is (= (first coords) (last coords))))
    (testing "every coordinate is one of the input points"
      (is (every? (fn [[x y]]
                    (some (fn [[px py]]
                            (and (approx= px x) (approx= py y)))
                          hull-pts))
                  coords)))
    (testing "coordinates stay within the input bounding box"
      (is (every? #(in-bbox? b % eps) coords)))))

(defn- parse-coord-pair-str
  "Parses \"lon,lat\" into [lon lat] doubles."
  [s]
  (let [[lon lat & more] (str/split s #",")]
    (when (and lon lat (empty? more))
      [(Double/parseDouble lon) (Double/parseDouble lat)])))

(deftest ->coord-pair-strs-test
  (testing "Point: single \"lon,lat\" string"
    (is (= ["25.720539797408946,62.62057217751676"]
           (gis/->coord-pair-strs test-point))))
  (testing "fewer than 10 points: one string per flat coordinate"
    (is (= ["26.2436753445903,63.9531598143881"
            "26.4505514903968,63.9127506671744"
            "26.2436550567509,63.9531552213109"
            "25.7583312263512,63.9746827436437"]
           (gis/->coord-pair-strs test-route))))
  (testing ">= 10 points: hull-based, algorithm-agnostic invariants"
    (let [strs (gis/->coord-pair-strs hull-fcoll)
          parsed (map parse-coord-pair-str strs)
          b (bbox hull-pts)]
      (is (>= (count strs) 3))
      (is (every? some? parsed) "each string is \"lon,lat\" with double parts")
      (testing "parsed coords stay within the input bbox (and Finland WGS84 bounds)"
        (is (every? #(in-bbox? b % eps) parsed))
        (is (every? (fn [[lon lat]]
                      (and (<= 19.0 lon 32.0) (<= 59.0 lat 71.0)))
                    parsed))))))

;;; repair-self-intersecting-polygon ;;;

(def bowtie-geometry
  "Bowtie polygon that splits into a MultiPolygon when repaired."
  {:type "Polygon"
   :coordinates [[[25.0 62.0]
                  [25.1 62.0]
                  [25.05 62.05]
                  [25.1 62.1]
                  [25.0 62.1]
                  [25.05 62.05]
                  [25.0 62.0]]]})

(deftest repair-self-intersecting-polygon-test
  (testing "repaired features don't introduce :properties key when absent in source"
    (let [bowtie {:type "FeatureCollection"
                  :features [{:type "Feature"
                              :id "bowtie"
                              :geometry bowtie-geometry}]}
          result (gis/repair-self-intersecting-polygon bowtie)]
      (is (= 2 (count (:features result)))
          "Bowtie should split into 2 polygons")
      (doseq [f (:features result)]
        (is (not (contains? f :properties))
            "Repaired feature should not have :properties when source didn't"))))

  (testing "repaired features preserve :properties when present in source"
    (let [bowtie {:type "FeatureCollection"
                  :features [{:type "Feature"
                              :id "bowtie"
                              :properties {:name "Test"}
                              :geometry bowtie-geometry}]}
          result (gis/repair-self-intersecting-polygon bowtie)]
      (is (= 2 (count (:features result))))
      (doseq [f (:features result)]
        (is (= {:name "Test"} (:properties f))
            "Repaired feature should preserve :properties from source"))))

  (testing "valid polygons pass through unchanged"
    (is (= test-polygon (gis/repair-self-intersecting-polygon test-polygon)))))

;;; dedupe-polygon-coords ;;;

(deftest dedupe-polygon-coords-test
  (let [fcoll {:type "FeatureCollection"
               :features [{:type "Feature"
                           :geometry {:type "Polygon"
                                      :coordinates [[[1.0 1.0] [1.0 1.0] [2.0 2.0]
                                                     [3.0 3.0] [3.0 3.0] [1.0 1.0]]]}}]}
        result (gis/dedupe-polygon-coords fcoll)]
    (is (= [[[1.0 1.0] [2.0 2.0] [3.0 3.0] [1.0 1.0]]]
           (->> result :features first :geometry :coordinates
                (mapv vec))))
    (testing "polygon without consecutive duplicates is unchanged"
      (is (= (get-in test-polygon [:features 0 :geometry :coordinates])
             (->> (gis/dedupe-polygon-coords test-polygon)
                  :features first :geometry :coordinates
                  (mapv vec)))))))

;;; ->fcoll & ->feature ;;;

(deftest ->fcoll-test
  (testing "wraps features and defaults nil :properties to {}"
    (let [f {:type "Feature" :geometry {:type "Point" :coordinates [1.0 2.0]}}
          result (gis/->fcoll [f])]
      (is (= "FeatureCollection" (:type result)))
      (is (= {} (-> result :features first :properties)))
      (is (= (:geometry f) (-> result :features first :geometry)))))
  (testing "existing :properties are preserved"
    (let [f {:type "Feature"
             :properties {:a 1}
             :geometry {:type "Point" :coordinates [1.0 2.0]}}]
      (is (= {:a 1} (-> (gis/->fcoll [f]) :features first :properties))))))

(deftest ->feature-test
  (is (= {:type "Feature" :geometry {:type "Point" :coordinates [1.0 2.0]}}
         (gis/->feature {:type "Point" :coordinates [1.0 2.0]}))))

;;; Envelopes ;;;

(deftest ->tm35fin-envelope-test
  (testing "point fcoll: degenerate envelope at the projected point"
    (let [{:keys [min-x max-x min-y max-y]} (gis/->tm35fin-envelope test-point)
          [e n] (gis/wgs84->tm35fin-no-wrap
                  (-> test-point :features first :geometry :coordinates))]
      (is (approx= e min-x m-tol))
      (is (approx= e max-x m-tol))
      (is (approx= n min-y m-tol))
      (is (approx= n max-y m-tol))))
  (testing "buffer-m expands each side by exactly buff-m"
    (let [base (gis/->tm35fin-envelope test-point)
          buffed (gis/->tm35fin-envelope test-point 100)]
      (is (approx= 100.0 (- (:max-x buffed) (:max-x base)) 1e-6))
      (is (approx= 100.0 (- (:min-x base) (:min-x buffed)) 1e-6))
      (is (approx= 100.0 (- (:max-y buffed) (:max-y base)) 1e-6))
      (is (approx= 100.0 (- (:min-y base) (:min-y buffed)) 1e-6))))
  (testing "route envelope: min < max and contains the projected route points"
    (let [{:keys [min-x max-x min-y max-y]} (gis/->tm35fin-envelope test-route)
          projected (map gis/wgs84->tm35fin-no-wrap (gis/->flat-coords test-route))]
      (is (< min-x max-x))
      (is (< min-y max-y))
      (is (every? (fn [[e n]]
                    (and (<= (- min-x 10) e (+ max-x 10))
                         (<= (- min-y 10) n (+ max-y 10))))
                  projected)))))

(deftest get-envelope-test
  (let [g (gis/->jts-geom test-route)
        {:keys [min-x max-x min-y max-y]} (gis/get-envelope g)
        b (bbox (gis/->flat-coords test-route))]
    (testing "matches the WGS84 bounding box of the input"
      (is (approx= (:min-x b) min-x))
      (is (approx= (:max-x b) max-x))
      (is (approx= (:min-y b) min-y))
      (is (approx= (:max-y b) max-y)))
    (testing "buff-m expands each side exactly"
      (let [buffed (gis/get-envelope g 0.5)]
        (is (approx= (- min-x 0.5) (:min-x buffed)))
        (is (approx= (+ max-x 0.5) (:max-x buffed)))
        (is (approx= (- min-y 0.5) (:min-y buffed)))
        (is (approx= (+ max-y 0.5) (:max-y buffed)))))))

(deftest intersects-envelope?-test
  (let [route-tm35 (gis/transform-crs (gis/->jts-geom test-route))]
    (testing "envelope covering all of Finland intersects"
      (is (true? (gis/intersects-envelope?
                   {:min-x 44000 :max-x 740000 :min-y 6594000 :max-y 7782000}
                   route-tm35))))
    (testing "distant envelope does not intersect"
      (is (false? (gis/intersects-envelope?
                    {:min-x 0 :max-x 10 :min-y 0 :max-y 10}
                    route-tm35))))
    (testing "envelope exactly at the route's own bounds intersects"
      (is (true? (gis/intersects-envelope?
                   (gis/->tm35fin-envelope test-route)
                   route-tm35))))))

;;; chunk-envelope ;;;

(deftest chunk-envelope-test
  (testing "exact fit produces a single chunk"
    (is (= [{:min-x 0 :max-x 10 :min-y 0 :max-y 10}]
           (gis/chunk-envelope {:min-x 0 :max-x 10 :min-y 0 :max-y 10} 10))))
  (testing "remainder produces edge chunks clamped to the envelope"
    (is (= [{:min-x 0 :max-x 10 :min-y 0 :max-y 10}
            {:min-x 10 :max-x 11 :min-y 0 :max-y 10}
            {:min-x 0 :max-x 10 :min-y 10 :max-y 11}
            {:min-x 10 :max-x 11 :min-y 10 :max-y 11}]
           (gis/chunk-envelope {:min-x 0 :max-x 11 :min-y 0 :max-y 11} 10))))
  (testing "3x3 grid from 21x21 envelope with max-size 10"
    (is (= 9 (count (gis/chunk-envelope
                      {:min-x 10 :max-x 31 :min-y 10 :max-y 31} 10))))))

(defspec chunk-envelope-prop 50
  (prop/for-all [min-x (gen/choose -1000 1000)
                 min-y (gen/choose -1000 1000)
                 width (gen/choose 1 500)
                 height (gen/choose 1 500)
                 max-size (gen/choose 1 100)]
                (let [env {:min-x min-x :max-x (+ min-x width)
                           :min-y min-y :max-y (+ min-y height)}
                      chunks (gis/chunk-envelope env max-size)]
                  (and (seq chunks)
           ;; every chunk lies within the parent envelope
                       (every? (fn [{:keys [min-x max-x min-y max-y]}]
                                 (and (<= (:min-x env) min-x max-x (:max-x env))
                                      (<= (:min-y env) min-y max-y (:max-y env))))
                               chunks)
           ;; no side exceeds max-size
                       (every? (fn [{:keys [min-x max-x min-y max-y]}]
                                 (and (<= (- max-x min-x) (+ max-size 1e-9))
                                      (<= (- max-y min-y) (+ max-size 1e-9))))
                               chunks)
           ;; chunks tile the parent envelope without overlap
                       (approx= (* width height)
                                (reduce + (map (fn [{:keys [min-x max-x min-y max-y]}]
                                                 (* (- max-x min-x) (- max-y min-y)))
                                               chunks))
                                1e-6)))))
