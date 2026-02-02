(ns lipas.jobs.patterns
  "Enterprise reliability patterns for the job queue system.
  
  Provides exponential backoff, circuit breakers, timeouts, and retry logic
  without external dependencies."
  (:require
   [lipas.jobs.db :as jobs-db]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent TimeUnit TimeoutException]
   [java.util Random]))

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

(defmacro with-timeout
  "Execute body with timeout. Returns result or throws TimeoutException.
  
  timeout-ms: timeout in milliseconds
  body: expressions to execute
  
  Example:
  (with-timeout 5000
    (fetch-external-data))"
  [timeout-ms & body]
  `(let [future# (future ~@body)
         result# (deref future# ~timeout-ms ::timeout)]
     (if (= result# ::timeout)
       (do
         (future-cancel future#)
         (throw (TimeoutException. (str "Operation timed out after " ~timeout-ms "ms"))))
       result#)))

(defn get-circuit-breaker
  "Get current state of a circuit breaker from database."
  [db service-name]
  (jobs-db/get-circuit-breaker db {:service_name service-name}))

(defn update-circuit-breaker!
  "Update circuit breaker state in database."
  [db service-name updates]
  ;; Ensure breaker exists before updating
  (jobs-db/ensure-circuit-breaker! db {:service_name service-name})
  ;; Now update specific fields
  (let [params (merge {:service_name service-name
                       :state nil
                       :failure_count nil
                       :success_count nil
                       :last_failure_at nil
                       :opened_at nil
                       :half_opened_at nil}
                      updates)]
    (jobs-db/update-circuit-breaker! db params)))

(defn circuit-breaker-state
  "Determine if circuit breaker should allow request.
  
  Returns :closed, :open, or :half-open based on current state and timeouts."
  [breaker {:keys [open-duration-ms failure-threshold]
            :or {open-duration-ms 60000
                 failure-threshold 5}}]
  (let [{:keys [state failure_count opened_at]} breaker
        now (System/currentTimeMillis)]
    (cond
      ;; Not in database yet - closed
      (nil? breaker) :closed

      ;; No state or closed state - closed
      (or (nil? state) (= state "closed")) :closed

      ;; Open but timeout expired - half-open
      (and (= state "open")
           opened_at
           (> (- now (.getTime opened_at)) open-duration-ms))
      :half-open

      ;; Otherwise use current state
      :else (keyword state))))

(defn with-circuit-breaker*
  "Execute function with circuit breaker protection.
  
  Options:
  :failure-threshold - failures before opening (default 5)
  :open-duration-ms - time to stay open (default 60000)
  :on-open - callback when circuit opens
  
  Throws CircuitBreakerOpenException when circuit is open."
  [db service-name opts f]
  (let [breaker (get-circuit-breaker db service-name)
        state (circuit-breaker-state breaker opts)]
    (case state
      :open
      (throw (ex-info "Circuit breaker is open"
                      {:type ::circuit-breaker-open
                       :service service-name
                       :breaker breaker}))

      :half-open
      ;; Single test request allowed
      (try
        (let [result (f)]
          ;; Success - reset the breaker
          (update-circuit-breaker! db service-name
                                   {:state "closed"
                                    :failure_count 0
                                    :success_count 1})
          result)
        (catch Exception e
          ;; Failed - reopen
          (update-circuit-breaker! db service-name
                                   {:state "open"
                                    :opened_at (java.sql.Timestamp. (System/currentTimeMillis))})
          (throw e)))

      :closed
      ;; Normal operation with failure tracking
      (try
        (let [result (f)]
          ;; Success - increment success count
          (when breaker
            (update-circuit-breaker! db service-name
                                     {:success_count (inc (or (:success_count breaker) 0))}))
          result)
        (catch Exception e
          ;; Ensure breaker exists
          (jobs-db/ensure-circuit-breaker! db {:service_name service-name})
          ;; Atomically increment failure count and check if we should open
          (let [now (java.sql.Timestamp. (System/currentTimeMillis))
                rows-updated (jobs-db/increment-circuit-breaker-failure!
                              db {:service_name service-name
                                  :last_failure_at now
                                  :failure_threshold (:failure-threshold opts 5)
                                  :opened_at now})]
            ;; If no rows updated, circuit was already open or half-open
            (when (> rows-updated 0)
              ;; Check if we just opened the circuit
              (let [updated-breaker (get-circuit-breaker db service-name)]
                (when (and (= "open" (:state updated-breaker))
                           (>= (:failure_count updated-breaker) (:failure-threshold opts 5)))
                  (when-let [on-open (:on-open opts)]
                    (on-open {:service service-name
                              :failure-count (:failure_count updated-breaker)}))))))
          (throw e))))))

(defmacro with-circuit-breaker
  "Macro version of with-circuit-breaker* for inline code.
  
  Example:
  (with-circuit-breaker db \"email-service\" {}
    (send-email! emailer message))"
  [db service-name opts & body]
  `(with-circuit-breaker* ~db ~service-name ~opts (fn [] ~@body)))
