(ns lipas.backend.osrm
  (:require
   [cemerick.url :as url]
   [clj-http.client :as client]
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

;; Cache statistics
(defonce cache-stats
  (atom {:hits 0 :misses 0}))

(def http-options
  {:as :json ; Parse JSON directly, skipping string intermediate
   :throw-exceptions false ; Return error responses instead of throwing
   :connection-timeout 2000 ; 2 seconds to establish connection
   :socket-timeout 30000 ; 30 seconds for response
   ;; Disable connection pooling - create fresh connection each time
   :connection-manager false
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
  [{:keys [sources destinations] :as m}]
  (when (and (seq sources) (seq destinations))
    (let [url (make-url m)
          cached-value (cache/lookup @osrm-cache url)]

      (if (cache/has? @osrm-cache url)
        ;; Cache hit
        (do
          (swap! cache-stats update :hits inc)
          (log/debug "OSRM cache hit for URL:" url)
          cached-value)

        ;; Cache miss - fetch and cache
        (do
          (swap! cache-stats update :misses inc)
          (log/debug "OSRM cache miss for URL:" url)
          (let [response (client/get url http-options)]
            (cond
              ;; Success - cache and return
              (= 200 (:status response))
              (let [result (:body response)]
                (swap! osrm-cache cache/miss url result)
                result)

              ;; Error response
              (>= (:status response) 400)
              (do
                (log/error "OSRM error:" (:status response) (:body response))
                nil)

              ;; Connection error or timeout
              (:error response)
              (do
                (log/error "OSRM connection error:" (:error response))
                nil)

              :else nil)))))))

(defn get-distances-and-travel-times
  "Fetch distances and travel times for multiple profiles in parallel.
  Returns partial results if some profiles timeout (logs warning instead of throwing)."
  [{:keys [profiles]
    :or {profiles [:car :bicycle :foot]}
    :as m}]
  (let [futures (->> profiles
                     (mapv (fn [p] [p (future (get-data (assoc m :profile p)))])))]
    (reduce (fn [res [profile f]]
              (let [result (deref f osrm-parallel-timeout-ms ::timeout)]
                (cond
                  (= result ::timeout)
                  (do
                    (future-cancel f)
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
