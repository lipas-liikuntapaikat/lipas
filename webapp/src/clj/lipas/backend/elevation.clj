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
;; 250x250m chunks that contain at least one vertex, request each chunk
;; as an ESRI Ascii Grid text file from MML api (bounded concurrency)
;; and resolve the elevation value for each vertex from an O(1) index
;; over the fetched grids.
;;
;; Chunk corners are multiples of 250 and therefore aligned with the
;; 2m coverage cell grid, so the value resolved for a vertex does not
;; depend on which chunk framing served its cell.

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

(def max-concurrent-fetches
  "Upper bound on simultaneous MML requests. MML is an external,
  rate-limited API guarded by a circuit breaker in the job dispatcher;
  fetches proceed in bounded waves instead of launching every chunk
  fetch at once."
  8)

(defonce ^{:doc "Reusable connection pool shared by all MML requests.
  Keep-alive connections avoid a fresh TCP+TLS handshake per request,
  which matters when a route needs tens of chunk fetches."}
  connection-manager
  (doto (conn-mgr/make-reusable-conn-manager
         {:timeout 30 ; Idle connection TTL (seconds)
          :threads max-concurrent-fetches
          :default-per-route max-concurrent-fetches})
    ;; Health-check connections that sat idle >1s before reuse, so a
    ;; keep-alive connection the server closed doesn't surface as an
    ;; IOException mid-request
    (.setValidateAfterInactivity 1000)))

(def mml-http-options
  "HTTP options for MML API requests."
  {:connection-timeout 5000     ;; 5 seconds to establish connection
   :socket-timeout 120000       ;; 2 minutes for response (large grids take time)
   :connection-request-timeout 10000 ;; 10 seconds to lease from the pool
   :connection-manager connection-manager
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

(defn fit-to-coverage
  "Returns an envelope that contains given envelope and 'matches' MML
  2x2m coverage grid. Ensures that the returned envelope is within the
  bounds of the coverage's envelope."
  [{:keys [min-x min-y max-x max-y]} {:keys [envelope]}]
  {:min-x (max (:min-x envelope) (let [n (Math/round (double min-x))] (if (odd? n) (dec n) n)))
   :min-y (max (:min-y envelope) (let [n (Math/round (double min-y))] (if (odd? n) (dec n) n)))
   :max-x (min (:max-x envelope) (let [n (Math/round (double max-x))] (if (odd? n) (dec n) n)))
   :max-y (min (:max-y envelope) (let [n (Math/round (double max-y))] (if (odd? n) (dec n) n)))})

(defn chunk-key
  "Key [kx ky] of the fixed-grid chunk that contains the given TM35FIN
  point. Chunk origins are multiples of `query-envelope-size-m`."
  [[x y]]
  [(long (Math/floor (/ x query-envelope-size-m)))
   (long (Math/floor (/ y query-envelope-size-m)))])

(defn chunk-key->envelope
  "Envelope of fixed-grid chunk [kx ky], clamped to the coverage
  envelope. Chunk corners are multiples of `query-envelope-size-m` and
  hence already aligned with the 2m coverage cells; a chunk fully
  outside the coverage yields a degenerate envelope whose MML request
  fails, which fails the whole job (same outcome as the old
  implementation for out-of-coverage geometries)."
  [[kx ky]]
  (let [{:keys [envelope]} coverage-info
        size query-envelope-size-m]
    {:min-x (max (:min-x envelope) (* kx size))
     :max-x (min (:max-x envelope) (* (inc kx) size))
     :min-y (max (:min-y envelope) (* ky size))
     :max-y (min (:max-y envelope) (* (inc ky) size))}))

(defn geometry-vertices
  "All [lon lat] vertices of fcoll that `append-elevations` will resolve
  an elevation for. Walks the geometry types exactly like
  `append-elevations` so the fetched chunk set always covers the
  resolved vertices."
  [fcoll]
  (into []
        (mapcat
         (fn [f]
           (let [coords (-> f :geometry :coordinates)]
             (condp = (-> f :geometry :type)
               "Point"      [coords]
               "LineString" coords
               "Polygon"    (mapcat identity coords)
               (throw (ex-info "Encountered unexpected geometry type" f))))))
        (:features fcoll)))

(defn resolve-elevation
  [grid-index coords]
  (let [[lon lat :as tm35] (gis/wgs84->tm35fin-no-wrap coords)
        k (chunk-key tm35)

        {:keys [headers rows]}
        (or (get grid-index k)
            (throw (ex-info "No elevation grid covers coordinate"
                            {:coords coords :tm35fin tm35 :chunk-key k})))

        ;; Resolve col and row for the coords relative to the lower
        ;; left corner of the grid. Cap to max-bounds.
        col (min (long (Math/floor
                        (/ (- lon (:xllcorner headers))
                           (:cellsize headers))))
                 (dec (:ncols headers)))
        row (min (long (Math/floor
                        (/ (- lat (:yllcorner headers))
                           (:cellsize headers))))
                 (dec (:nrows headers)))]

    (-> rows (nth row) (nth col))))

(defn append-elevations
  [fcoll grid-index]
  (update fcoll :features
          (fn [fs]
            (map
             (fn [f]
               (update-in f [:geometry :coordinates]
                          (fn [coords]
                            (condp = (-> f :geometry :type)
                              "Point"      (let [[x y] coords]
                                             [x y (resolve-elevation grid-index coords)])
                              "LineString" (mapv
                                            (fn [coords]
                                              (let [[x y] coords]
                                                [x y (resolve-elevation grid-index coords)]))
                                            coords)
                              "Polygon"    (mapv
                                            (fn [coords]
                                              (mapv
                                               (fn [coords]
                                                 (let [[x y] coords]
                                                   [x y (resolve-elevation grid-index coords)]))
                                               coords))
                                            coords)
                              (throw (ex-info "Encountered unexpected geometry type" f))))))
             fs))))

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
  [lines]
  (into []
        (for [s     lines
              :when (not-empty s)]
          (into [] (map parse-double) (re-seq #"-?\d+\.?\d*" s)))))

(defn parse-ascii-grid
  [s]
  (let [lines        (str/split-lines s)
        header-lines (take-while #(re-matches #"^[A-Za-z].*" %) lines)
        data-lines   (drop (count header-lines) lines)]
    {:headers (parse-ascii-grid-headers header-lines)
     :data    (parse-ascii-grid-data data-lines)}))

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
                    {:query-params (make-query-params envelope)
                     :basic-auth   (str mml-api-key ":")})]
    (log/info "Getting coverage with envelope" envelope)
    (let [response (client/get mml-coverage-url opts)]
      (cond
        (= 200 (:status response))
        (parse-ascii-grid (:body response))

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
  "Fetch and index the elevation grids for chunk-keys with bounded
  concurrency (waves of `max-concurrent-fetches`). Returns a map of
  chunk-key -> indexed grid. Any chunk failure propagates and fails the
  whole enrichment: the job queue retries and the circuit breaker in
  the dispatcher sees the failure."
  [chunk-keys]
  (into {}
        (mapcat
         (fn [wave]
           (mapv (fn [k grid] [k (index-grid grid)])
                 wave
                 (patterns/pmap-with-timeout
                  elevation-chunk-timeout-ms
                  (comp get-elevation-coverage chunk-key->envelope)
                  wave))))
        (partition-all max-concurrent-fetches chunk-keys)))

(defn enrich-elevation
  [fcoll]
  (let [chunk-keys (->> (geometry-vertices fcoll)
                        (map (comp chunk-key gis/wgs84->tm35fin-no-wrap))
                        distinct
                        sort)]
    (log/info "Fetching elevation data for" (count chunk-keys) "grid chunks")
    (-> chunk-keys
        fetch-grids
        (->> (append-elevations fcoll)))))

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
