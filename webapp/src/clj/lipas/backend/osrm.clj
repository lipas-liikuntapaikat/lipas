(ns lipas.backend.osrm
  (:require
   [cemerick.url :as url]
   [clj-http.client :as client]
   [clj-http.conn-mgr :as conn-mgr]
   [clojure.core.cache :as cache]
   [clojure.string :as str]
   [environ.core :refer [env]]
   [lipas.backend.gis :as gis]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent TimeoutException]))

(def profiles
  {:car {:url (:osrm-car-url env)}
   :bicycle {:url (:osrm-bicycle-url env)}
   :foot {:url (:osrm-foot-url env)}})

;; 5-minute TTL cache for OSRM requests
(defonce osrm-cache
  (atom (cache/ttl-cache-factory {}
                                 :ttl (* 5 60 1000)))) ; 5 minutes in milliseconds

(def osrm-parallel-timeout-ms
  "Timeout for parallel OSRM profile requests (45 seconds).
  This allows buffer for 3 profiles x socket-timeout."
  45000)

;; Cache statistics (cached lookups only)
(defonce cache-stats
  (atom {:hits 0 :misses 0}))

;; Actual HTTP requests issued, cached or not
(defonce request-stats
  (atom {:requests 0}))

(defonce ^{:doc "Reusable connection pool shared by all OSRM requests.
  Keep-alive connections avoid a fresh TCP handshake per request, which
  dominates latency when many table requests are made in sequence.
  Sized so interactive analyses don't queue behind batch precompute
  jobs (which hold at most one connection per profile host at a time)."}
  connection-manager
  (doto (conn-mgr/make-reusable-conn-manager
         {:timeout 30 ; Idle connection TTL (seconds)
          :threads 30 ; Max connections total
          :default-per-route 10}) ; Per profile server (car/bicycle/foot)
    ;; Health-check connections that sat idle >1s before reuse, so a
    ;; keep-alive connection the server closed doesn't surface as an
    ;; IOException mid-request
    (.setValidateAfterInactivity 1000)))

(def http-options
  {:as :json ; Parse JSON directly, skipping string intermediate
   :throw-exceptions false ; Return error responses instead of throwing
   :connection-timeout 2000 ; 2 seconds to establish connection
   :socket-timeout 30000 ; 30 seconds for response
   :connection-request-timeout 10000 ; 10 seconds to lease from the pool
   :connection-manager connection-manager
   ;; OSRM GETs are idempotent - retry once on connection-establishment
   ;; failures (e.g. a stale keep-alive connection that slipped past
   ;; validation). Deliberately NOT on SocketTimeoutException: retrying
   ;; a slow request doubles the load on a server that is still
   ;; computing the abandoned response.
   :retry-handler (fn [ex try-count _ctx]
                    (and (< try-count 2)
                         (not (instance? java.net.SocketTimeoutException ex))))
   ;; Standard options
   :decompress-body true ; OSRM typically uses gzip
   :accept :json
   :cookie-policy :none ; OSRM doesn't use cookies
   :redirect-strategy :none}) ; OSRM doesn't redirect

(defn resolve-sources [fcoll]
  (if (gis/point? fcoll)
    [(-> fcoll :features first :geometry :coordinates (->> (str/join ",")))]
    (-> fcoll
        gis/->single-linestring-coords
        (->> (map #(str/join "," %))))))

(defn make-url
  [{:keys [sources destinations profile annotations]
    :or {annotations "distance,duration"}}]
  (let [base-url (-> profiles profile :url)]
    (str base-url
         (->> (into [] cat [sources destinations])
              (str/join ";"))
         "?"
         (url/map->query
          {:annotations annotations
           :skip_waypoints true
           :generate_hints false
           :sources (str/join ";" (range 0 (count sources)))
           :destinations (str/join ";" (range (count sources)
                                              (+ (count sources)
                                                 (count destinations))))}))))

(defn get-data
  "Fetch a distance/duration table from OSRM. Returns nil on any
  failure (HTTP error status, connection failure, timeout) so callers
  degrade by omitting the profile instead of blowing up mid-batch.

  `:cache?` (default true) controls the URL-keyed TTL cache. Batch
  callers issuing large one-off table requests should pass false so
  multi-kB URLs don't churn the cache.

  `:socket-timeout-ms` overrides the default 30s response timeout -
  large table matrices (especially the foot profile) can legitimately
  take longer to compute."
  [{:keys [sources destinations cache? socket-timeout-ms]
    :or {cache? true} :as m}]
  (when (and (seq sources) (seq destinations))
    (let [url (make-url m)]
      (if (and cache? (cache/has? @osrm-cache url))
        ;; Cache hit
        (do
          (swap! cache-stats update :hits inc)
          (log/debug "OSRM cache hit for URL:" url)
          (cache/lookup @osrm-cache url))

        ;; Cache miss - fetch and cache
        (do
          (when cache?
            (swap! cache-stats update :misses inc))
          (swap! request-stats update :requests inc)
          (let [opts (cond-> http-options
                       socket-timeout-ms (assoc :socket-timeout socket-timeout-ms))
                response (try
                           (client/get url opts)
                           ;; :throw-exceptions false only suppresses
                           ;; status exceptions - connection-level
                           ;; failures still throw
                           (catch java.io.IOException e
                             (log/error "OSRM connection error:" (ex-message e))
                             nil))]
            (cond
              (nil? response) nil

              ;; Success - cache and return
              (= 200 (:status response))
              (let [result (:body response)]
                (when cache?
                  (swap! osrm-cache cache/miss url result))
                result)

              ;; Error response
              (>= (:status response) 400)
              (do
                (log/error "OSRM error:" (:status response) (:body response))
                nil)

              :else nil)))))))

(defn get-distances-and-travel-times
  "Fetch distances and travel times for multiple profiles in parallel.
  Returns partial results if some profiles fail or time out (logs a
  warning instead of throwing)."
  [{:keys [profiles timeout-ms]
    :or {profiles [:car :bicycle :foot]
         timeout-ms osrm-parallel-timeout-ms}
    :as m}]
  (let [futures (->> profiles
                     (mapv (fn [p] [p (future (get-data (assoc m :profile p)))])))]
    (reduce (fn [res [profile f]]
              (let [result (deref f timeout-ms ::timeout)]
                (cond
                  (= result ::timeout)
                  ;; Deliberately no future-cancel: interrupting a
                  ;; request that holds a pooled connection risks
                  ;; leaking it, and the socket timeout bounds the
                  ;; request anyway
                  (do
                    (log/warn "OSRM request timed out for profile" profile)
                    res)

                  (nil? result)
                  res

                  :else
                  (assoc res profile result))))
            {}
            futures)))

(defn cache-info
  "Get cache statistics"
  []
  (let [stats @cache-stats
        total (+ (:hits stats) (:misses stats))
        hit-rate (if (pos? total)
                   (double (/ (:hits stats) total))
                   0.0)]
    {:size (count @osrm-cache)
     :hits (:hits stats)
     :misses (:misses stats)
     :hit-rate hit-rate
     :total-requests total}))

(defn clear-cache!
  "Clear the cache and reset stats"
  []
  (reset! osrm-cache (cache/ttl-cache-factory {} :ttl (* 5 60 1000)))
  (reset! cache-stats {:hits 0 :misses 0})
  (reset! request-stats {:requests 0})
  (log/info "OSRM cache cleared"))

(comment
  (def destinations
    ["25.1048346953729,62.5375762900109"
     "25.1242598119357,62.5378383310659"
     "25.1631111977754,62.5383543185465"])

  (def sources ["27.9601046796022,70.0837473555685"])
  (def params
    {:sources sources
     :destinations destinations
     :profile :bicycle})

  (make-url params)
  (def bicycle (get-data params))
  (def foot (get-data (assoc params :profile :foot)))
  (def car (get-data (assoc params :profile :car)))

  [(:durations foot)
   (:durations bicycle)
   (:durations car)]

  (time
   (get-distances-and-travel-times
    (assoc params :profiles [:car :bicycle :foot]))))
