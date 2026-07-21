(ns lipas.backend.gis
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [taoensso.timbre :as log])
  (:import
    [org.locationtech.jts.algorithm RobustLineIntersector]
    [org.locationtech.jts.algorithm.hull ConcaveHull]
    [org.locationtech.jts.geom Coordinate CoordinateFilter Envelope Geometry GeometryFactory PrecisionModel]
    [org.locationtech.jts.index.strtree STRtree]
    [org.locationtech.jts.geom.util GeometryCombiner]
    [org.locationtech.jts.io WKTReader]
    [org.locationtech.jts.operation.buffer BufferOp]
    [org.locationtech.jts.operation.distance DistanceOp]
    [org.locationtech.jts.operation.linemerge LineSequencer]
    [org.locationtech.jts.simplify DouglasPeuckerSimplifier]
    [org.locationtech.proj4j BasicCoordinateTransform CoordinateReferenceSystem CRSFactory ProjCoordinate]
    [org.wololo.jts2geojson GeoJSONReader GeoJSONWriter]))

(def srid 4326) ;; WGS84
(def tm35fin-srid 3067)
(def default-simplify-tolerance 0.001) ; ~111m

;;; CRS transforms (proj4j) ;;;

(def ^:private crs-factory (CRSFactory.))

(def ^:private crs-cache (atom {}))

(defn- crs
  "CoordinateReferenceSystem for an EPSG code. CRS objects are immutable
  and expensive to parse, so they're cached. Transforms are NOT cached
  because proj4j BasicCoordinateTransform is not thread-safe."
  ^CoordinateReferenceSystem [epsg-code]
  (or (@crs-cache epsg-code)
      (let [c (.createFromName crs-factory (str "EPSG:" epsg-code))]
        (swap! crs-cache assoc epsg-code c)
        c)))

(defn- transform-geom
  "Returns a copy of `geom` with all coordinates transformed from
  `from-epsg` to `to-epsg` and SRID set to `to-epsg`."
  ^Geometry [^Geometry geom from-epsg to-epsg]
  (let [xform (BasicCoordinateTransform. (crs from-epsg) (crs to-epsg))
        src (ProjCoordinate.)
        dst (ProjCoordinate.)
        out (.copy geom)]
    (.apply out
            (reify CoordinateFilter
              (filter [_ c]
                (set! (.-x src) (.-x c))
                (set! (.-y src) (.-y c))
                (.transform xform src dst)
                (set! (.-x c) (.-x dst))
                (set! (.-y c) (.-y dst)))))
    (.geometryChanged out)
    (.setSRID out to-epsg)
    out))

;;; JTS construction & GeoJSON IO ;;;

(def ^:private geometry-factories (atom {}))

(defn- gf
  ^GeometryFactory [srid*]
  (or (@geometry-factories srid*)
      (let [f (GeometryFactory. (PrecisionModel.) srid*)]
        (swap! geometry-factories assoc srid* f)
        f)))

(defn ->jts-point [lon lat]
  (.createPoint (gf srid) (Coordinate. lon lat)))

(def ^:private geojson-reader (GeoJSONReader.))
(def ^:private geojson-writer (GeoJSONWriter.))

(defn- geom-map->jts
  "GeoJSON geometry map -> JTS Geometry."
  (^Geometry [m] (geom-map->jts m srid))
  (^Geometry [m srid*]
   (.read geojson-reader ^String (json/encode m) (gf srid*))))

(defn- jts->geom-map
  "JTS Geometry -> GeoJSON geometry map (keywordized)."
  [^Geometry g]
  (-> (.write geojson-writer g) str (json/decode keyword)))

(defn point->dummy-area
  [fcoll]
  (let [coords (-> fcoll :features first :geometry :coordinates)
        [y x]  coords
        delta  0.0001] ; ~11m in WGS84
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

(defn centroid
  "Returns centroids of `m` where `m` is a map representing
  GeoJSON FeatureCollection."
  [m]
  (let [geoms (map (comp geom-map->jts :geometry) (:features m))]
    (-> (.createGeometryCollection (gf srid) (into-array Geometry geoms))
        (.getCentroid)
        (doto (.setSRID srid))
        jts->geom-map)))

(defn wgs84->tm35fin [[lon lat]]
  (let [transformed (transform-geom (->jts-point lon lat) srid tm35fin-srid)]
    {:easting (.getX transformed) :northing (.getY transformed)}))

(defn wgs84->tm35fin-no-wrap [[lon lat]]
  (let [transformed (transform-geom (->jts-point lon lat) srid tm35fin-srid)]
    [(.getX transformed) (.getY transformed)]))

(defn wgs84->tm35fin-coords
  "Batch version of `wgs84->tm35fin-no-wrap`: transforms every
  [lon lat] with a single transform construction instead of one JTS
  point + transform per coordinate. Applies the same per-coordinate
  math, so results are identical to the single-point fn."
  [coords]
  (let [xform (BasicCoordinateTransform. (crs srid) (crs tm35fin-srid))
        src (ProjCoordinate.)
        dst (ProjCoordinate.)]
    (mapv (fn [[lon lat]]
            (set! (.-x src) (double lon))
            (set! (.-y src) (double lat))
            (.transform xform src dst)
            [(.-x dst) (.-y dst)])
          coords)))

(defn epsg3067-point->envelope [[e n] delta]
  [[(- e delta) (+ n delta)] [(+ e delta) (- n delta)]])

(defn epsg3067-point->wgs84-envelope [coords delta]
  (let [envelope (epsg3067-point->envelope coords delta)]
    (mapv (fn [[e n]]
            (let [transformed (transform-geom (->jts-point e n) tm35fin-srid srid)]
              [(.getX transformed) (.getY transformed)]))
          envelope)))

(defn ->jts-geom
  ([f]
   (->jts-geom f srid))
  ([f srid]
   (let [geoms (map (comp #(geom-map->jts % srid) :geometry) (:features f))]
     (.combine (GeometryCombiner. geoms)))))

(defn wgs84wkt->tm35fin-geom [s]
  (-> (.read (WKTReader. (gf srid)) ^String s)
      (transform-geom srid tm35fin-srid)))

(defn fcoll->tm35fin-geom
  "GeoJSON FeatureCollection (WGS84) -> JTS geometry in TM35FIN
  (EPSG:3067), where distances are in meters."
  ^Geometry [fcoll]
  (transform-geom (->jts-geom fcoll) srid tm35fin-srid))

(defn tm35fin-point
  "JTS point from TM35FIN [easting northing]."
  [[e n]]
  (.createPoint (gf tm35fin-srid) (Coordinate. e n)))

(defn within-distance?
  "True when JTS geometry g2 lies within distance-m of g1. Both
  geometries must be in a metric CRS (e.g. TM35FIN). An envelope
  prefilter avoids the exact distance computation for far pairs."
  [^Geometry g1 ^double distance-m ^Geometry g2]
  (let [e1 (.getEnvelopeInternal g1)
        e2 (.getEnvelopeInternal g2)]
    (and (<= (- (.getMinX e1) distance-m) (.getMaxX e2))
         (<= (.getMinX e2) (+ (.getMaxX e1) distance-m))
         (<= (- (.getMinY e1) distance-m) (.getMaxY e2))
         (<= (.getMinY e2) (+ (.getMaxY e1) distance-m))
         (<= (.distance g1 g2) distance-m))))

(defn transform-crs
  ([geom] (transform-geom geom srid tm35fin-srid))
  ([geom from-crs to-crs]
   (transform-geom geom from-crs to-crs)))

(defn shortest-distance [g1 g2]
  (DistanceOp/distance g1 g2))

(defn ->point
  "JTS point from latitude and longitude (x=lon, y=lat).

  NOTE: takes (lat lon) for backwards compatibility with the removed
  geo.spatial/point. Unlike its spatial4j predecessor, the result is a
  JTS geometry, so it works with `nearest-points` & friends."
  [lat lon]
  (->jts-point lon lat))

(def ^:private earth-mean-radius-m
  "Mean earth radius (meters), same constant spatial4j used."
  6371008.7714)

(defn haversine
  "Geodesic (haversine) distance in meters between [lon lat] pairs."
  [[lon1 lat1] [lon2 lat2]]
  (let [lat1 (Math/toRadians lat1)
        lat2 (Math/toRadians lat2)
        sin-dlat (Math/sin (/ (- lat2 lat1) 2))
        sin-dlon (Math/sin (/ (- (Math/toRadians lon2)
                                 (Math/toRadians lon1))
                              2))
        a (+ (* sin-dlat sin-dlat)
             (* (Math/cos lat1) (Math/cos lat2) sin-dlon sin-dlon))]
    (* 2 earth-mean-radius-m (Math/asin (Math/sqrt (min 1.0 a))))))

(defn distance-point
  "Geodesic (haversine) distance in meters between two points
  (anything with getX/getY where x=lon, y=lat)."
  [p1 p2]
  (haversine [(.getX p1) (.getY p1)] [(.getX p2) (.getY p2)]))

(defn nearest-points [g1 g2]
  (DistanceOp/nearestPoints g1 g2))

(defn strip-z
  [coords]
  (if (vector? coords)
    (subvec coords 0 2)
    (take 2 coords)))

(defn strip-z-fcoll
  [fcoll]
  (update fcoll :features
          (fn [fs]
            (mapv (fn [{:keys [geometry] :as f}]
                    (update-in f [:geometry :coordinates]
                               (fn [coords]
                                 (case (:type geometry)
                                   "Point" (strip-z coords)
                                   "LineString" (mapv strip-z coords)
                                   "Polygon" (mapv (fn [cs] (mapv strip-z cs)) coords)))))
                  fs))))

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

(defn simplify
  "Returns simplified version of `m` where `m` is a map representing
  GeoJSON FeatureCollection. Feature properties are not preserved."
  ([m] (simplify m default-simplify-tolerance))
  ([m tolerance]
   {:type "FeatureCollection"
    :features (mapv (fn [feature]
                      (let [geom (-> feature
                                     :geometry
                                     geom-map->jts
                                     (DouglasPeuckerSimplifier/simplify tolerance)
                                     (doto (.setSRID srid)))]
                        {:type "Feature"
                         :properties {}
                         :geometry (jts->geom-map geom)}))
                    (:features m))}))

(defn simplify-safe
  "Returns simplified version of `m` where `m` is a map representing
  GeoJSON FeatureCollection."
  ([fcoll] (simplify-safe fcoll default-simplify-tolerance))
  ([fcoll tolerance]
   (try
     (let [simplified (simplify fcoll tolerance)]
       (if (contains-coords? simplified)
         simplified
         ;; If simplification removes all coords
         ;; fallback to original geoms
         fcoll))
     (catch Exception ex
       (log/warn ex "Failed to simplify fcoll" fcoll)
       fcoll))))

;;; Route geometry quality analysis ;;;
;;
;; Diagnosis for the AI assistant's check_route_geometry tool — a
;; human-explainable report, not a validator. All input is WGS84
;; GeoJSON; distances via haversine so no projection round-trips.

(defn- round-6 ^double [x] (/ (Math/round (* (double x) 1e6)) 1e6))
(defn- round-2 ^double [x] (/ (Math/round (* (double x) 100.0)) 100.0))

(defn- dist-m
  "Haversine distance in meters between [lon lat] pairs."
  [[lon1 lat1] [lon2 lat2]]
  (let [la1 (Math/toRadians lat1)
        la2 (Math/toRadians lat2)
        sin-dlat (Math/sin (/ (- la2 la1) 2))
        sin-dlon (Math/sin (/ (- (Math/toRadians lon2) (Math/toRadians lon1)) 2))
        a (+ (* sin-dlat sin-dlat)
             (* (Math/cos la1) (Math/cos la2) sin-dlon sin-dlon))]
    (* 2 earth-mean-radius-m (Math/asin (Math/sqrt (min 1.0 a))))))

(defn- line-length-m [coords]
  (reduce + 0.0 (map dist-m coords (rest coords))))

(defn- self-intersection-points
  "Points where a linestring crosses itself. Pairwise robust check of
   non-adjacent segments over an STRtree, so GPS tracks with thousands
   of points stay fast. A shared endpoint between the first and last
   segment (a closed loop) is legitimate and not reported. Returns at
   most `cap` [lon lat] points."
  [coords cap]
  (let [segs (mapv (fn [i c1 c2] [i (Coordinate. (first c1) (second c1))
                                  (Coordinate. (first c2) (second c2))])
                   (range) coords (rest coords))
        tree (STRtree.)
        _ (doseq [[i ^Coordinate c1 ^Coordinate c2] segs]
            (.insert tree (doto (Envelope. c1) (.expandToInclude c2)) i))
        _ (.build tree)
        li (RobustLineIntersector.)
        seg (fn [i] (nth segs i))
        hit (fn [i j]
              (let [[_ ^Coordinate a1 ^Coordinate a2] (seg i)
                    [_ ^Coordinate b1 ^Coordinate b2] (seg j)]
                (.computeIntersection li a1 a2 b1 b2)
                (when (.hasIntersection li)
                  (let [^Coordinate p (.getIntersection li 0)]
                    ;; Adjacent segments always meet at their shared
                    ;; vertex; only a crossing elsewhere is a problem.
                    (when-not (and (= 1 (.getIntersectionNum li))
                                   (or (.equals2D p a1) (.equals2D p a2))
                                   (or (.equals2D p b1) (.equals2D p b2)))
                      [(.-x p) (.-y p)])))))]
    (->> (for [[i ^Coordinate c1 ^Coordinate c2] segs
               j (.query tree (doto (Envelope. c1) (.expandToInclude c2)))
               :let [j (int j)]
               :when (> j (inc i))]
           (hit i j))
         (keep identity)
         (map (fn [[lon lat]] [(round-6 lon) (round-6 lat)]))
         distinct
         (take cap)
         vec)))

(defn- duplicate-vertex-points
  "Consecutive vertices closer than 1 m — leftovers of GPS noise or
   double clicks that make editing tools misbehave."
  [coords cap]
  (->> (map (fn [c1 c2] (when (< (dist-m c1 c2) 1.0) c1)) coords (rest coords))
       (keep identity)
       (map (fn [[lon lat]] [(round-6 lon) (round-6 lat)]))
       distinct
       (take cap)
       vec))

(defn- endpoint-gaps
  "Pairs of segment (feature) endpoints from different linestrings that
   almost touch (0.5–25 m apart) — routes that probably should connect
   but don't quite."
  [lines cap]
  (let [ends (for [[i coords] (map-indexed vector lines)
                   c [(first coords) (last coords)]]
               [i c])]
    (->> (for [[[i c1] & more] (iterate rest ends)
               :while more
               [j c2] more
               :when (not= i j)
               :let [d (dist-m c1 c2)]
               :when (< 0.5 d 25.0)]
           {:features [i j]
            :distance-m (Math/round ^double d)
            :location {:lon (round-6 (first c1)) :lat (round-6 (second c1))}})
         (take cap)
         vec)))

(def ^:private dense-vertices-per-km
  "Above this the track is raw GPS output that begs simplification;
   hand-drawn routes run 10–50 vertices/km."
  120)

(defn route-geometry-report
  "Quality report of a route FeatureCollection (WGS84 GeoJSON with
   LineString features): totals, self-intersections, duplicate
   consecutive vertices, near-miss gaps between features, vertex
   density. Locations are bounded (max ~8 per problem type) and rounded
   so the report stays prompt-sized."
  [fcoll]
  (let [lines (->> (:features fcoll)
                   (filter #(= "LineString" (get-in % [:geometry :type])))
                   (mapv #(mapv strip-z (get-in % [:geometry :coordinates]))))
        cap 8
        per-line (mapv (fn [i coords]
                         {:feature i
                          :vertices (count coords)
                          :length-m (Math/round ^double (line-length-m coords))
                          :self-intersections (self-intersection-points coords cap)
                          :duplicate-vertices (duplicate-vertex-points coords cap)})
                       (range) lines)
        length-km (/ (reduce + 0 (map :length-m per-line)) 1000.0)
        vertices (reduce + 0 (map :vertices per-line))
        kinks (vec (mapcat (fn [{:keys [feature self-intersections]}]
                             (map (fn [[lon lat]]
                                    {:feature feature :lon lon :lat lat})
                                  self-intersections))
                           per-line))
        dupes (vec (mapcat (fn [{:keys [feature duplicate-vertices]}]
                             (map (fn [[lon lat]]
                                    {:feature feature :lon lon :lat lat})
                                  duplicate-vertices))
                           per-line))
        gaps (endpoint-gaps lines cap)
        vpk (when (pos? length-km) (Math/round ^double (/ vertices length-km)))]
    {:feature-count (count lines)
     :vertex-count vertices
     :length-km (round-2 length-km)
     :vertices-per-km vpk
     :needs-simplification? (boolean (and vpk (> vpk dense-vertices-per-km)))
     :self-intersections {:count (count kinks) :locations (vec (take cap kinks))}
     :duplicate-vertices {:count (count dupes) :locations (vec (take cap dupes))}
     :endpoint-gaps {:count (count gaps) :locations gaps}
     :ok? (and (zero? (count kinks))
               (zero? (count dupes))
               (zero? (count gaps)))}))

(defn ->jts-multi-point [coords]
  (.createMultiPointFromCoords
    (gf srid)
    (into-array Coordinate (map (fn [[lon lat]] (Coordinate. lon lat)) coords))))

(defn wkt-point->coords [s]
  (let [coord (-> (.read (WKTReader. (gf srid)) ^String s)
                  (.getCoordinate))]
    [(.getX coord) (.getY coord)]))

(defn ->wkt [g]
  (format "POINT (%s %s)" (.getX g) (.getY g)))

(defn calc-buffer [fcoll distance-m]
  (-> fcoll
      (->jts-geom)
      (transform-geom srid tm35fin-srid)
      (BufferOp.)
      (.getResultGeometry distance-m)
      (transform-geom tm35fin-srid srid)
      jts->geom-map))

(def ^:private concave-hull-length-ratio
  "Tightness of the concave hull: 0 = maximally concave, 1 = convex
  hull. 0.2 produces hulls of similar tightness to the removed geowave
  GeometryHullTool (~0.8 of the convex hull area on reference data;
  geowave was ~0.78)."
  0.2)

(defn concave-hull [fcoll]
  (-> fcoll
      ->flat-coords
      ->jts-multi-point
      (ConcaveHull/concaveHullByLengthRatio concave-hull-length-ratio)))

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

(defn repair-self-intersecting-polygon
  "Repairs a self-intersecting polygon using the buffer(0) technique.
   If the result is a MultiPolygon, decomposes it into multiple Polygon features.
   Returns the input unchanged if the polygon is already valid or not a Polygon."
  [fcoll]
  (let [features (:features fcoll)
        repaired-features
        (mapcat
          (fn [feature]
            (let [geom-type (get-in feature [:geometry :type])]
              (if (= "Polygon" geom-type)
                (let [jts-geom (->jts-geom {:type "FeatureCollection" :features [feature]})]
                  (if (.isValid jts-geom)
                    [feature]  ; Already valid, keep as-is
                    (let [geom-srid (.getSRID jts-geom)
                          fixed-jts (doto (.buffer jts-geom 0.0)
                                      (.setSRID geom-srid))
                          fixed-type (.getGeometryType fixed-jts)]
                      (log/info "Repaired self-intersecting polygon, result type:" fixed-type
                                "lipas-id:" (:lipas-id (:properties feature)))
                      (if (= "MultiPolygon" fixed-type)
                       ;; Decompose MultiPolygon into multiple Polygon features
                        (for [i (range (.getNumGeometries fixed-jts))]
                          (let [poly (.getGeometryN fixed-jts i)
                                _ (.setSRID poly geom-srid)
                                poly-geom (jts->geom-map poly)]
                            (cond-> {:type "Feature"
                                     :id (str (:id feature) "-" i)
                                     :geometry poly-geom}
                              (contains? feature :properties)
                              (assoc :properties (:properties feature)))))
                       ;; Single Polygon result
                        [(cond-> {:type "Feature"
                                  :id (:id feature)
                                  :geometry (jts->geom-map fixed-jts)}
                           (contains? feature :properties)
                           (assoc :properties (:properties feature)))]))))
                [feature])))  ; Not a Polygon, keep as-is
          features)]
    (assoc fcoll :features (vec repaired-features))))

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
                      (transform-geom srid tm35fin-srid)
                      .getEnvelopeInternal
                      (doto (.expandBy buff-m)))]
     {:max-x (.getMaxX envelope)
      :max-y (.getMaxY envelope)
      :min-x (.getMinX envelope)
      :min-y (.getMinY envelope)})))

(defn get-envelope
  ([jts-geom]
   (get-envelope jts-geom 0))
  ([jts-geom buff-m]
   (let [envelope (-> ^Geometry jts-geom
                      .getEnvelopeInternal
                      (doto (.expandBy buff-m)))]
     {:max-x (.getMaxX envelope)
      :max-y (.getMaxY envelope)
      :min-x (.getMinX envelope)
      :min-y (.getMinY envelope)})))

(defn intersects-envelope?
  [{:keys [min-x max-x min-y max-y]} jts-geom]
  (let [factory (gf tm35fin-srid)
        jts-envelope (->> [(Coordinate. min-x min-y)
                           (Coordinate. min-x max-y)
                           (Coordinate. max-x max-y)
                           (Coordinate. max-x min-y)
                           (Coordinate. min-x min-y)]
                          (into-array Coordinate)
                          (.createLinearRing factory)
                          (.createPolygon factory))]
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

(defn ->centroid-point
  [fcoll]
  (-> fcoll centroid ->feature vector ->fcoll))

(defn geojson-coords->jts-coords
  "Convert GeoJSON coordinates to JTS Coordinates, handling both 2D and 3D coordinates"
  [coords]
  (map (fn [coord]
         (if (>= (count coord) 3)
           (Coordinate. (double (first coord))
                        (double (second coord))
                        (double (nth coord 2)))
           (Coordinate. (double (first coord))
                        (double (second coord)))))
       coords))

(defn linestring->jts
  "Convert a GeoJSON LineString to a JTS LineString"
  [coords]
  (let [factory (GeometryFactory.)
        jts-coords (into-array Coordinate (geojson-coords->jts-coords coords))]
    (.createLineString factory jts-coords)))

(defn extract-linestring-from-feature
  "Extract JTS LineString from a GeoJSON Feature"
  [feature]
  (let [geometry (:geometry feature)
        type (:type geometry)]
    (if (= type "LineString")
      (linestring->jts (:coordinates geometry))
      (throw (Exception. (str "Feature geometry is not a LineString, found: " type))))))

(defn create-feature-index-map
  "Create a map of features with their indices and JTS geometries"
  [features]
  (into {} (keep-indexed
             (fn [idx feature]
               [idx {:feature feature
                     :jts-geom (extract-linestring-from-feature feature)}])
             features)))

(defn find-matching-feature-index
  "Find the index of a feature that matches the given geometry"
  [seq-geom feature-map]
  (let [seq-coords (for [i (range (.getNumPoints seq-geom))]
                     (.getCoordinateN seq-geom i))
        start-coord (first seq-coords)
        end-coord (last seq-coords)]

    ;; Find a feature with matching start and end coordinates
    (first
      (for [[idx {:keys [jts-geom]}] feature-map
            :let [feat-coords (for [i (range (.getNumPoints jts-geom))]
                                (.getCoordinateN jts-geom i))
                  feat-start (first feat-coords)
                  feat-end (last feat-coords)]
            :when (or
                  ;; Match in same direction
                    (and (.equals2D start-coord feat-start)
                         (.equals2D end-coord feat-end))
                  ;; Match in reverse direction
                    (and (.equals2D start-coord feat-end)
                         (.equals2D end-coord feat-start)))]
        idx))))

(defn sequence-features
  "Sequence LineString features using JTS LineSequencer"
  [feature-collection]
  (let [features (:features feature-collection)]
    ;; Handle empty collections
    (if (empty? features)
      feature-collection
      (try
        ;; Create mapping of indices to features and geometries
        (let [feature-map (create-feature-index-map features)

              ;; Create LineSequencer
              sequencer (LineSequencer.)

              ;; Add all geometries to the sequencer
              _ (doseq [[_ {:keys [jts-geom]}] feature-map]
                  (.add sequencer jts-geom))

              ;; Check if sequenceable
              sequenceable? (.isSequenceable sequencer)

              ;; Get the ordered features
              ordered-features (if sequenceable?
                                 (let [;; Get the sequenced linestrings
                                       sequenced (.getSequencedLineStrings sequencer)

                                       ;; Find matching features for each geometry in the sequence
                                       ordered-indices (for [i (range (.getNumGeometries sequenced))]
                                                         (let [seq-geom (.getGeometryN sequenced i)]
                                                           (find-matching-feature-index seq-geom feature-map)))]

                                   ;; Get features in the new order, filtering out any nil indices
                                   (mapv #(nth features %) (filter some? ordered-indices)))

                                 ;; If not sequenceable, return original features
                                 features)]

          ;; Return the feature collection with ordered features
          (assoc feature-collection :features ordered-features))

        (catch Exception e
          (println "Error in sequence-features:" (.getMessage e))
          ;; Return original collection on error
          feature-collection)))))

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
      .getEnvelopeInternal
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
      ->jts-geom)

  (->single-linestring-coords test-route)

  (def mp (->> test-route
               ->flat-coords
               ->jts-multi-point))

  (def coords (.getCoordinates mp))

  (into [] coords)

  (def convex (.getConvexHull (org.locationtech.jts.algorithm.ConvexHull. mp)))

  (.getSRID convex)

  (def concave (concave-hull test-route))

  concave

  (->> {:type "FeatureCollection"
        :features [(->feature (jts->geom-map concave))]}
       json/encode
       (spit "/Users/tipo/Desktop/concave.json"))

  (->> {:type "FeatureCollection"
        :features [(->feature (jts->geom-map convex))]}
       json/encode
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
          [26.4505514903968, 63.9127506671744],
          [26.4505514903968, 63.9531598143881],
          [26.2436753445903, 63.9531598143881]]]}},
      {:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[26.2436550567509, 63.9531552213109],
          [25.7583312263512, 63.9746827436437],
          [25.7583312263512, 63.9531552213109],
          [26.2436550567509, 63.9531552213109]]]}}]})

  (centroid test-polygon)

  (->centroid-point test-polygon)

  (-> test-polygon ->flat-coords)

  (->tm35fin-envelope test-polygon)

  (require '[lipas.backend.osrm :as osrm])

  (osrm/resolve-sources test-polygon)

  (->> test-polygon
       ->jts-geom)

  (->> test-polygon
       concave-hull)

  (centroid test-route)

  (def test-point2
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [19.720539797408946,
                      65.62057217751676]}}]})

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
  (contains-coords? test-polygon-empty))
