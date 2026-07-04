(ns lipas.jobs.patterns
  "Reliability patterns for the job queue: exponential backoff, per-item
  timeouts and a simple in-memory circuit breaker."
  (:require
   [taoensso.timbre :as log])
  (:import
   [java.util Random]
   [java.util.concurrent TimeoutException]))

(def ^:private random (Random.))

(defn exponential-backoff-ms
  "Calculate exponential backoff with jitter.

  attempt: 0-based attempt number
  base-ms: base delay in milliseconds (default 1000)
  max-ms: maximum delay in milliseconds (default 300000 = 5 minutes)
  jitter: jitter factor 0.0-1.0 (default 0.1 = 10%)

  Returns delay in milliseconds with formula:
  min(base * 2^attempt, max) * (1 ± jitter)"
  [attempt & {:keys [base-ms max-ms jitter]
              :or {base-ms 1000
                   max-ms 300000
                   jitter 0.1}}]
  (let [exponential-delay (min (* base-ms (Math/pow 2 attempt)) max-ms)
        jitter-range (* exponential-delay jitter)
        jitter-offset (- (* 2 (.nextDouble random) jitter-range) jitter-range)]
    (long (max 0 (+ exponential-delay jitter-offset)))))

(defn deref-with-timeout
  "Deref a future with timeout. Returns result or throws TimeoutException.

  f: the future to deref
  timeout-ms: timeout in milliseconds
  error-context: descriptive string for error message (e.g., 'elevation chunk 5')"
  [f timeout-ms error-context]
  (let [result (deref f timeout-ms ::timeout)]
    (if (= result ::timeout)
      (do
        (future-cancel f)
        (throw (TimeoutException.
                (str "Operation timed out after " timeout-ms "ms: " error-context))))
      result)))

(defn pmap-with-timeout
  "Parallel map with per-item timeout. Launches all futures, then collects
  with timeout.

  timeout-ms: timeout per item in milliseconds
  f: function to apply to each item
  coll: collection of items"
  [timeout-ms f coll]
  (let [futures (mapv #(future (f %)) coll)]
    (mapv #(deref-with-timeout %1 timeout-ms (str "item " %2))
          futures (range))))

;; In-memory circuit breaker
;;
;; Protects an external service from being hammered while it is down:
;; after :failure-threshold consecutive failures the breaker opens and
;; calls fail fast for :open-duration-ms, after which a single trial call
;; is allowed through (half-open). Success closes the breaker.
;;
;; State is per-JVM which is sufficient: LIPAS runs a single worker
;; process, and a fresh (closed) breaker after restart is harmless.

(defonce ^:private breakers (atom {}))

(defn reset-breakers!
  "Reset all circuit breaker state. Intended for tests."
  []
  (reset! breakers {}))

(defn breaker-status
  "Current state of all breakers, for monitoring/logging."
  []
  @breakers)

(defn- now-ms [] (System/currentTimeMillis))

(defn circuit-open-ex [service-name breaker]
  (ex-info "Circuit breaker is open"
           {:type ::circuit-breaker-open
            :service service-name
            :breaker breaker}))

(defn circuit-breaker-open?
  "True when ex is the fail-fast exception thrown by an open breaker."
  [ex]
  (= ::circuit-breaker-open (:type (ex-data ex))))

(defn- on-success [service-name]
  (swap! breakers assoc service-name {:state :closed :failure-count 0}))

(defn- on-failure [service-name failure-threshold]
  (let [breaker (-> (swap! breakers update service-name
                           (fn [{:keys [failure-count] :as b}]
                             (let [failures (inc (or failure-count 0))]
                               (if (>= failures failure-threshold)
                                 {:state :open
                                  :failure-count failures
                                  :opened-at (now-ms)}
                                 (assoc b
                                        :state :closed
                                        :failure-count failures)))))
                    (get service-name))]
    (when (and (= :open (:state breaker))
               (= (:failure-count breaker) failure-threshold))
      (log/error "Circuit breaker opened"
                 {:service service-name
                  :failure-count (:failure-count breaker)}))))

(defn- interrupt?
  "True when ex is (or was caused by) a thread interrupt, or the current
  thread's interrupt flag is set. An interrupt comes from the worker's
  watchdog timeout, not from the protected service, so it must not count
  as a service failure."
  [^Throwable ex]
  (or (.isInterrupted (Thread/currentThread))
      (loop [^Throwable e ex]
        (cond
          (nil? e) false
          (instance? InterruptedException e) true
          (instance? java.io.InterruptedIOException e) true
          :else (recur (.getCause e))))))

(defn with-circuit-breaker*
  "Execute f with circuit breaker protection.

  Options:
  :failure-threshold - consecutive failures before opening (default 5)
  :open-duration-ms  - how long to fail fast before a trial call (default 60000)

  Thread interrupts (e.g. the job watchdog firing at the timeout) are
  rethrown without being counted: they say nothing about the health of the
  protected service.

  Throws the fail-fast exception (see circuit-breaker-open?) while open."
  [service-name {:keys [failure-threshold open-duration-ms]
                 :or {failure-threshold 5
                      open-duration-ms 60000}}
   f]
  (let [{:keys [state opened-at] :as breaker} (get @breakers service-name)]
    (if (and (= :open state)
             (< (- (now-ms) opened-at) open-duration-ms))
      (throw (circuit-open-ex service-name breaker))
      ;; Closed, or open long enough for a (half-open) trial call
      (try
        (let [result (f)]
          (on-success service-name)
          result)
        (catch Exception e
          (cond
            (interrupt? e)
            (throw e)

            (= :open state)
            ;; Failed trial call - reopen immediately
            (do (swap! breakers assoc service-name
                       {:state :open
                        :failure-count (:failure-count breaker)
                        :opened-at (now-ms)})
                (throw e))

            :else
            (do (on-failure service-name failure-threshold)
                (throw e))))))))

(defmacro with-circuit-breaker
  "Macro version of with-circuit-breaker*.

  Example:
  (with-circuit-breaker \"mml-elevation-service\" {:failure-threshold 5}
    (fetch-elevations!))"
  [service-name opts & body]
  `(with-circuit-breaker* ~service-name ~opts (fn [] ~@body)))
