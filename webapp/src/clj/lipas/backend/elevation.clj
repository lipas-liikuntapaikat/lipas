(ns lipas.backend.elevation
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [clojure.string :as str]
            [lipas.backend.config :as config]
            [lipas.backend.gis :as gis]
            [lipas.jobs.patterns :as patterns]
            [taoensso.timbre :as log]))

;; MML elevation coverage model consists of 2x2m squares that contain
;; the elevation information as meters above/below sea level. Elevation
;; is resolved per geometry vertex: we derive the set of fixed-grid
;; 250x250m chunks that contain at least one vertex and plan the MML
;; requests adaptively (`plan-fetches`):
;;
;;   - geometries spanning at most one chunk (points, most single
;;     sports sites) fetch one small envelope around the vertices,
;;     like the pre-optimization implementation did
;;   - dense clusters of chunks are merged into 1000x1000m tile
;;     requests (measured: a 16-chunk tile costs ~3x one chunk fetch)
;;   - remaining chunks are fetched individually
;;
;; Each request returns an ESRI Ascii Grid text file. Requests run
;; with bounded concurrency and each vertex resolves its elevation
;; from an O(1) chunk-key index over the fetched grids.
;;
;; All requested envelopes have even (2m-aligned) corners, so the
;; value resolved for a vertex does not depend on which envelope
;; framing served its cell.

;; https://www.maanmittauslaitos.fi/ortokuvien-ja-korkeusmallien-kyselypalvelu/tekninen-kuvaus
;; https://en.wikipedia.org/wiki/Esri_grid

(def mml-api-key (get-in config/default-config [:app :mml-api :api-key]))
(def mml-coverage-url (get-in config/default-config [:app :mml-api :coverage-url]))

(def default-query-params
  {"service"     "WCS",
   "version"     "2.0.1",
   "request"     "GetCoverage",
   "format"      "text/plain"
   "CoverageID"  "korkeusmalli_2m"
   "SCALEFACTOR" "1",
   "SUBSET"      ["N(7181000,7181200)" "E(496000,496200)"]})

(def coverage-info
  "Acquired from `describe-coverage` output."
  {:cell-size 2
   :envelope
   {:min-x 44000
    :max-x 740000
    :min-y 6594000
    :max-y 7782000}})

(def query-envelope-size-m
  "250x250m was selected as the grid size because of good perf/coverage
  ratio with MML api. Must be even so chunk corners align with the 2m
  coverage cells (the coverage envelope bounds are multiples of 250)."
  250)

(def merge-tile-size-m
  "Dense chunk clusters are merged into requests of this size. Must be
  a multiple of `query-envelope-size-m`; the coverage envelope bounds
  are also multiples of it. Sizes beyond 1000m get disproportionately
  slow on the MML side (2500m ~2.9s, 5000m ~16s)."
  1000)

(def merge-tile-min-chunks
  "Merge a tile into one request when it contains at least this many
  vertex chunks. Measured MML timings: one 250m chunk ~255ms, one
  1000m tile (16 chunk areas) ~740ms, so from 3 chunks up a tile
  request costs MML no more server time and is always fewer requests.
  On the 300-route sweep this threshold cut total requests 3.8x
  (threshold 4 gave 3.0x; 2 gave 4.5x but doubles MML's per-area
  server time on the merged pairs)."
  3)

(def vertex-buffer-m
  "Tolerance added around the vertices in single-envelope requests so
  that `fit-to-coverage` rounding can never leave an extreme vertex
  outside the fetched grid. 8 meters was found via experimentation."
  8)

(def max-concurrent-fetches
  "Upper bound on simultaneous MML requests. MML is an external,
  rate-limited API guarded by a circuit breaker in the job dispatcher;
  fetches proceed in bounded waves instead of launching every chunk
  fetch at once (the old implementation fired one concurrent request
  per chunk, 165 for one benchmark route). Deliberate tradeoff: under
  severe MML degradation a many-chunk route may now exceed its job
  timeout and retry later via the queue, instead of 'winning' by
  hammering the degraded API with hundreds of concurrent slow
  requests."
  8)

(defonce ^{:doc "Reusable connection pool shared by all MML requests,
  built lazily on first use. Keep-alive connections avoid a fresh
  TCP+TLS handshake per request, which matters when a route needs tens
  of chunk fetches. Sized well above max-concurrent-fetches so that
  concurrently running elevation jobs (worker general lane has
  multiple threads) and abandoned requests still holding leases
  (a timed-out fetch blocks on its socket read until socket-timeout;
  interrupts don't unblock it) can't starve each other of
  connections."}
  connection-manager
  (delay
    (doto (conn-mgr/make-reusable-conn-manager
           {:timeout 30 ; Idle connection TTL (seconds)
            :threads 32
            :default-per-route 32})
      ;; Health-check connections that sat idle >1s before reuse, so a
      ;; keep-alive connection the server closed doesn't surface as an
      ;; IOException mid-request
      (.setValidateAfterInactivity 1000))))

(def mml-http-options
  "HTTP options for MML API requests. The shared `connection-manager`
  is merged in by `get-elevation-coverage`."
  {:connection-timeout 5000     ;; 5 seconds to establish connection
   :socket-timeout 120000       ;; 2 minutes for response (large grids take time)
   ;; Leasing from the pool must tolerate a full socket-timeout wait:
   ;; failing the lease faster than in-flight requests can finish would
   ;; turn MML slowness into job failures (and eventually a tripped
   ;; circuit breaker) instead of slow jobs
   :connection-request-timeout 130000
   :throw-exceptions false      ;; Handle errors explicitly
   :decompress-body true
   ;; GetCoverage GETs are idempotent - retry once on connection-
   ;; establishment failures (e.g. a stale keep-alive connection that
   ;; slipped past validation). Deliberately NOT on
   ;; SocketTimeoutException: the server keeps computing the abandoned
   ;; response, so retrying a slow request doubles its load.
   :retry-handler (fn [ex try-count _ctx]
                    (and (< try-count 2)
                         (not (instance? java.net.SocketTimeoutException ex))))})

(def elevation-chunk-timeout-ms
  "Timeout for fetching a single elevation grid chunk (3 minutes).
  This is higher than socket-timeout to account for retry logic."
  180000)

(defn describe-coverage
  []
  (let [opts {:basic-auth   (str mml-api-key ":")
              :query-params (-> default-query-params
                                (dissoc "SCALEFACTOR" "SUBSET" "format")
                                (assoc "request" "DescribeCoverage"))}]
    (-> (client/get mml-coverage-url opts)
        :body)))

(defn- clamp-to-coverage
  "Clamp an already 2m-aligned envelope to the coverage envelope. An
  envelope fully outside the coverage becomes degenerate (min > max)
  and its MML request fails, which fails the whole job (same outcome
  as the old implementation for out-of-coverage geometries)."
  [{:keys [min-x max-x min-y max-y]}]
  (let [{:keys [envelope]} coverage-info]
    {:min-x (max (:min-x envelope) min-x)
     :max-x (min (:max-x envelope) max-x)
     :min-y (max (:min-y envelope) min-y)
     :max-y (min (:max-y envelope) max-y)}))

(defn fit-to-coverage
  "Returns an envelope that contains given envelope and 'matches' MML
  2x2m coverage grid. Ensures that the returned envelope is within the
  bounds of the coverage's envelope."
  [{:keys [min-x min-y max-x max-y]}]
  (let [down-to-even (fn [v] (let [n (Math/round (double v))] (if (odd? n) (dec n) n)))]
    (clamp-to-coverage {:min-x (down-to-even min-x)
                        :min-y (down-to-even min-y)
                        :max-x (down-to-even max-x)
                        :max-y (down-to-even max-y)})))

(defn chunk-key
  "Key [kx ky] of the fixed-grid chunk that contains the given TM35FIN
  point. Chunk origins are multiples of `query-envelope-size-m`. A
  point exactly on the coverage envelope's max edge belongs to the
  last chunk inside the coverage; `resolve-elevation`'s col/row cap
  then reads its edge cell, like the old implementation did."
  [[x y]]
  (let [{:keys [envelope]} coverage-info
        x (if (= (double x) (double (:max-x envelope))) (dec (double x)) x)
        y (if (= (double y) (double (:max-y envelope))) (dec (double y)) y)]
    [(long (Math/floor (/ x query-envelope-size-m)))
     (long (Math/floor (/ y query-envelope-size-m)))]))

(defn chunk-key->envelope
  "Envelope of fixed-grid chunk [kx ky], clamped to the coverage
  envelope. Chunk corners are multiples of `query-envelope-size-m` and
  hence already aligned with the 2m coverage cells."
  [[kx ky]]
  (let [size query-envelope-size-m]
    (clamp-to-coverage {:min-x (* kx size)
                        :max-x (* (inc kx) size)
                        :min-y (* ky size)
                        :max-y (* (inc ky) size)})))

(defn tile-key->envelope
  "Envelope of the merge tile [tx ty], clamped to the coverage
  envelope."
  [[tx ty]]
  (clamp-to-coverage {:min-x (* tx merge-tile-size-m)
                      :max-x (* (inc tx) merge-tile-size-m)
                      :min-y (* ty merge-tile-size-m)
                      :max-y (* (inc ty) merge-tile-size-m)}))

(defn- vertices-envelope
  "Envelope of the TM35FIN vertices buffered by `vertex-buffer-m`,
  fitted to the coverage grid."
  [tm35-vertices]
  (-> {:min-x (- (reduce min (map first tm35-vertices)) vertex-buffer-m)
       :max-x (+ (reduce max (map first tm35-vertices)) vertex-buffer-m)
       :min-y (- (reduce min (map second tm35-vertices)) vertex-buffer-m)
       :max-y (+ (reduce max (map second tm35-vertices)) vertex-buffer-m)}
      fit-to-coverage))

(defn plan-fetches
  "Plan the MML requests needed to resolve elevations for the given
  TM35FIN vertices. Returns [{:envelope e :chunk-keys [k ...]} ...]
  where every distinct vertex chunk key appears in exactly one entry
  and each entry's envelope covers all of its chunk keys' vertices.

  Small geometries (all vertices within one chunk's span) get a single
  envelope tightly around the vertices; otherwise chunks are fetched
  individually except dense clusters, which are merged into
  `merge-tile-size-m` tiles."
  [tm35-vertices]
  (if (empty? tm35-vertices)
    []
    (let [chunk-keys (distinct (map chunk-key tm35-vertices))
          {:keys [min-x max-x min-y max-y] :as env} (vertices-envelope tm35-vertices)]
      (if (and (<= (- max-x min-x) query-envelope-size-m)
               (<= (- max-y min-y) query-envelope-size-m))
        ;; Small geometry: one request around the vertices (like the
        ;; pre-optimization implementation did for points and most
        ;; single sports sites)
        [{:envelope env :chunk-keys chunk-keys}]
        (->> chunk-keys
             (group-by (fn [[kx ky]]
                         (let [factor (quot merge-tile-size-m query-envelope-size-m)]
                           [(quot kx factor) (quot ky factor)])))
             (mapcat (fn [[tile-key ks]]
                       (if (>= (count ks) merge-tile-min-chunks)
                         [{:envelope (tile-key->envelope tile-key) :chunk-keys ks}]
                         (for [k ks]
                           {:envelope (chunk-key->envelope k) :chunk-keys [k]}))))
             (sort-by (comp (juxt :min-x :min-y) :envelope))
             vec)))))

(defn map-vertices
  "Eagerly map f over every vertex coordinate of fcoll, rebuilding the
  feature collection. Must visit vertices in the same order as
  `reduce-vertices` (the test suite pins the two walks together), so
  the fetched chunk set always covers the resolved vertices."
  [f fcoll]
  (update fcoll :features
          (fn [fs]
            (mapv
             (fn [feat]
               (update-in feat [:geometry :coordinates]
                          (fn [coords]
                            (condp = (-> feat :geometry :type)
                              "Point"      (f coords)
                              "LineString" (mapv f coords)
                              "Polygon"    (mapv #(mapv f %) coords)
                              (throw (ex-info "Encountered unexpected geometry type" feat))))))
             fs))))

(defn reduce-vertices
  "Reduce rf over every vertex coordinate of fcoll without rebuilding
  the collection, in the same order `map-vertices` visits them."
  [rf init fcoll]
  (reduce
   (fn [acc feat]
     (let [coords (-> feat :geometry :coordinates)]
       (condp = (-> feat :geometry :type)
         "Point"      (rf acc coords)
         "LineString" (reduce rf acc coords)
         "Polygon"    (reduce #(reduce rf %1 %2) acc coords)
         (throw (ex-info "Encountered unexpected geometry type" feat)))))
   init
   (:features fcoll)))

(defn geometry-vertices
  "All vertex coordinates of fcoll that `append-elevations` will
  resolve an elevation for, in walk order."
  [fcoll]
  (persistent! (reduce-vertices conj! (transient []) fcoll)))

(defn resolve-elevation
  "Resolve the elevation of a TM35FIN point from the grid index. A
  point exactly on its grid's max edge (a coverage-envelope max-edge
  vertex, see `chunk-key`) caps into the edge cell; anything else
  outside the grid means MML returned a grid that doesn't cover the
  requested envelope, which must fail loudly instead of silently
  resolving a wrong edge cell."
  [grid-index [x y :as tm35]]
  (let [k (chunk-key tm35)

        {:keys [headers rows]}
        (or (get grid-index k)
            (throw (ex-info "No elevation grid covers coordinate"
                            {:tm35fin tm35 :chunk-key k})))

        {:keys [xllcorner yllcorner cellsize ncols nrows]} headers

        ;; Resolve col and row for the coords relative to the lower
        ;; left corner of the grid
        col (long (Math/floor (/ (- x xllcorner) cellsize)))
        row (long (Math/floor (/ (- y yllcorner) cellsize)))]

    (when-not (and (or (< -1 col ncols) (== (double x) (+ xllcorner (* ncols cellsize))))
                   (or (< -1 row nrows) (== (double y) (+ yllcorner (* nrows cellsize)))))
      (throw (ex-info "Vertex outside its elevation grid"
                      {:tm35fin tm35 :chunk-key k :headers headers})))

    (-> rows
        (nth (min row (dec nrows)))
        (nth (min col (dec ncols))))))

(defn append-elevations
  "Append a z coordinate to every vertex, resolved from grid-index.
  tm35-vertices must be the vertices of fcoll in walk order (as
  returned by `geometry-vertices`, CRS-transformed once)."
  [fcoll grid-index tm35-vertices]
  (let [i (volatile! -1)]
    (map-vertices
     (fn [coords]
       (let [[x y] coords]
         [x y (resolve-elevation grid-index (nth tm35-vertices (vswap! i inc)))]))
     fcoll)))

(defn parse-ascii-grid-headers
  [lines]
  (into {}
        (for [s lines]
          (let [[_ k v] (re-find #"(\w+)\s*(-?\d+\.?\d*)" s)
                k       (str/lower-case k)]
            [(keyword k)
             (if (str/starts-with? k "n")
               (parse-long v)
               (parse-double v))]))))

(defn parse-ascii-grid-data
  "Parse data rows into vectors of doubles. Splitting on whitespace is
  ~2x faster than the regex scan this replaced and yields the same
  values; `keep` drops empty tokens from leading whitespace."
  [lines]
  (into []
        (for [s     lines
              :when (not-empty s)]
          (into [] (keep parse-double) (str/split s #"\s+")))))

(defn parse-ascii-grid
  [s]
  (let [lines        (str/split-lines s)
        header-lines (take-while #(re-matches #"^[A-Za-z].*" %) lines)
        data-lines   (drop (count header-lines) lines)]
    {:headers (parse-ascii-grid-headers header-lines)
     :data    (parse-ascii-grid-data data-lines)}))

(defn validate-grid
  "Throw when the parsed grid's dimensions don't match its headers.
  Guards against a truncated or malformed MML response silently
  resolving wrong cells: row indexing counts from the top of the grid,
  so missing rows would shift every subsequent lookup. Throwing fails
  the job, which retries via the queue."
  [{:keys [headers data] :as grid} envelope]
  (let [{:keys [ncols nrows]} headers]
    (when-not (and (= nrows (count data))
                   (every? #(= ncols (count %)) data))
      (throw (ex-info "MML grid dimensions don't match headers"
                      {:envelope     envelope
                       :headers      headers
                       :nrows-parsed (count data)
                       :ncols-parsed (into (sorted-set) (map count) data)})))
    grid))

(defn index-grid
  "Prepare a parsed grid for O(1) vertex lookups: reverse the rows once
  so the lower left corner comes first and row/col math works in
  'natural' order."
  [{:keys [headers data]}]
  {:headers headers
   :rows    (vec (rseq data))})

(defn make-query-params
  [{:keys [min-x max-x min-y max-y]}]
  (assoc default-query-params "SUBSET"
         [(format "N(%s,%s)"  min-y max-y)
          (format "E(%s,%s)"  min-x max-x)]))

(defn get-elevation-coverage
  [envelope]
  (let [opts (merge mml-http-options
                    {:connection-manager @connection-manager
                     :query-params (make-query-params envelope)
                     :basic-auth   (str mml-api-key ":")})]
    (log/info "Getting coverage with envelope" envelope)
    (let [response (try
                     (client/get mml-coverage-url opts)
                     ;; Socket/connect/pool-lease timeouts extend
                     ;; InterruptedIOException, which the circuit
                     ;; breaker's interrupt classifier would rethrow
                     ;; without counting (it can't tell them from a job
                     ;; watchdog interrupt). Rethrow as ex-info WITHOUT
                     ;; the original as cause so MML slowness actually
                     ;; trips the breaker.
                     (catch java.net.SocketTimeoutException e
                       (throw (ex-info (str "MML request timed out: " (ex-message e))
                                       {:envelope envelope :error (str (class e))})))
                     (catch org.apache.http.conn.ConnectTimeoutException e
                       (throw (ex-info (str "MML request timed out: " (ex-message e))
                                       {:envelope envelope :error (str (class e))}))))]
      (cond
        (= 200 (:status response))
        (-> (parse-ascii-grid (:body response))
            (validate-grid envelope))

        (>= (:status response) 400)
        (throw (ex-info "MML API error"
                        {:status (:status response)
                         :body (:body response)
                         :envelope envelope}))

        :else
        (throw (ex-info "Unexpected MML API response"
                        {:status (:status response)
                         :envelope envelope}))))))

(defn fetch-grids
  "Fetch and index the elevation grids for a `plan-fetches` plan with
  bounded concurrency (waves of `max-concurrent-fetches`). Returns a
  map of chunk-key -> indexed grid; an entry's grid is shared by all
  the chunk keys it was fetched for. Any fetch failure propagates and
  fails the whole enrichment: the job queue retries and the circuit
  breaker in the dispatcher sees the failure."
  [fetch-plan]
  (into {}
        (mapcat
         (fn [wave]
           (mapcat (fn [{:keys [chunk-keys]} grid]
                     (let [indexed (index-grid grid)]
                       (map (fn [k] [k indexed]) chunk-keys)))
                   wave
                   (patterns/pmap-with-timeout
                    elevation-chunk-timeout-ms
                    (comp get-elevation-coverage :envelope)
                    wave))))
        (partition-all max-concurrent-fetches fetch-plan)))

(defn enrich-elevation
  [fcoll]
  (let [tm35-vertices (gis/wgs84->tm35fin-coords (geometry-vertices fcoll))
        fetch-plan    (plan-fetches tm35-vertices)]
    (log/info "Fetching elevation data for" (count tm35-vertices) "vertices in"
              (count fetch-plan) "requests")
    (append-elevations fcoll (fetch-grids fetch-plan) tm35-vertices)))

(comment
  (->> (describe-coverage)
       (spit "/tmp/lol.xml"))

  (def test-point
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [25.720539797408946,
                      62.62057217751676]}}]})

  (enrich-elevation test-point)

  (-> test-point
      gis/->flat-coords
      (->> (map gis/wgs84->tm35fin-no-wrap)))
  ;; => ([434355.5312499977 6943966.504886635])

  ;; Timings measured against the MML api (2026-07-05 re-check pending;
  ;; original measurements below):
  ;; :m10    1951 ms  <--- cold start?
  ;; :m25     244 ms
  ;; :m50     257 ms
  ;; :m100    241 ms
  ;; :m250    255 ms  <--- sweet spot?
  ;; :m500    436 ms
  ;; :m1000   740 ms
  ;; :m2500  2919 ms
  ;; :m5000  16045 ms
  ;; :m10000 62406 ms
  (def test-envelopes
    [[:m10    {:min-x 400000 :max-x 400010 :min-y 6900000 :max-y 6900010}]
     [:m25    {:min-x 400000 :max-x 400025 :min-y 6900000 :max-y 6900025}]
     [:m50    {:min-x 400000 :max-x 400050 :min-y 6900000 :max-y 6900050}]
     [:m100   {:min-x 400000 :max-x 400100 :min-y 6900000 :max-y 6900100}]
     [:m250   {:min-x 400000 :max-x 400250 :min-y 6900000 :max-y 6900250}]
     [:m500   {:min-x 400000 :max-x 400500 :min-y 6900000 :max-y 6900500}]
     [:m1000  {:min-x 400000 :max-x 401000 :min-y 6900000 :max-y 6901000}]])

  (doseq [[k v] test-envelopes]
    (println k)
    (let [output (with-out-str (time (get-elevation-coverage v)))]
      (println output)))

  (def dada "NCOLS 1\r\nNROWS 1\r\nXLLCORNER 434354.531249997672\r\nYLLCORNER 6943965.504886634648\r\nCELLSIZE 1.000000000000\r\nNODATA_VALUE -9999\r\n")

  (parse-ascii-grid-headers (str/split-lines dada))

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

  ;; The old implementation selected chunks by line-corridor
  ;; intersection: 165 chunks (~41s of fetches) for this 4-vertex
  ;; route. Vertex-driven selection fetches 4.
  (->> (geometry-vertices test-route)
       (map (comp chunk-key gis/wgs84->tm35fin-no-wrap))
       distinct)

  (enrich-elevation test-route))
