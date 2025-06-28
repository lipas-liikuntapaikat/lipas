(ns lipas.jobs.monitoring
  "Database-based monitoring for the job queue system.
  
  Provides health checks, metrics recording, and alert detection
  without external dependencies like Prometheus."
  (:require
   [lipas.jobs.db :as jobs-db]
   [lipas.jobs.core :as jobs]
   [taoensso.timbre :as log]))

(defn record-job-metric!
  "Record execution metrics for a completed job.
  
  Parameters:
  - db: Database connection
  - job-type: Type of job
  - status: Final status (completed/failed/dead)
  - started-at: When job started
  - created-at: When job was created"
  [db job-type status started-at created-at]
  (let [now (System/currentTimeMillis)
        duration-ms (when started-at
                      (- now (.getTime started-at)))
        queue-time-ms (when (and started-at created-at)
                        (- (.getTime started-at) (.getTime created-at)))]
    (jobs-db/record-job-metric!
     db
     {:job_type job-type
      :status status
      :duration_ms duration-ms
      :queue_time_ms queue-time-ms})))

(defn health-check
  "Perform comprehensive health check of the job queue.
  
  Returns a map with :status (:healthy/:warning/:critical) and :issues"
  [db]
  (let [health (jobs/get-queue-health db)
        issues (atom [])]

    ;; Check for stuck jobs
    (when (and (:longest_processing_minutes health)
               (> (:longest_processing_minutes health) 60))
      (swap! issues conj {:type :stuck-jobs
                          :severity :warning
                          :message (str "Jobs processing for over "
                                        (:longest_processing_minutes health)
                                        " minutes")}))

    ;; Check for old pending jobs
    (when (and (:oldest_pending_minutes health)
               (> (:oldest_pending_minutes health) 30))
      (swap! issues conj {:type :old-pending
                          :severity :warning
                          :message (str "Pending jobs older than "
                                        (:oldest_pending_minutes health)
                                        " minutes")}))

    ;; Check dead letter queue
    (when (> (:dead_count health 0) 10)
      (swap! issues conj {:type :dead-letters
                          :severity :critical
                          :message (str (:dead_count health)
                                        " jobs in dead letter queue")}))

    ;; Check for high failure rate
    (let [total (+ (:pending_count health 0)
                   (:processing_count health 0)
                   (:failed_count health 0)
                   (:dead_count health 0))
          failure-rate (if (> total 0)
                         (/ (+ (:failed_count health 0)
                               (:dead_count health 0))
                            total)
                         0)]
      (when (> failure-rate 0.1)
        (swap! issues conj {:type :high-failure-rate
                            :severity :warning
                            :message (str (int (* failure-rate 100))
                                          "% failure rate")})))

    {:status (cond
               (some #(= (:severity %) :critical) @issues) :critical
               (seq @issues) :warning
               :else :healthy)
     :health health
     :issues @issues
     :checked-at (java.time.Instant/now)}))

(defn check-circuit-breakers
  "Check status of all circuit breakers.
  
  Returns a map of service-name to breaker status"
  [db]
  (let [breakers (jobs-db/get-circuit-breakers db)]
    (->> breakers
         (map (fn [breaker]
                [(:service_name breaker)
                 {:state (:state breaker)
                  :failure-count (:failure_count breaker)
                  :last-failure (:last_failure_at breaker)
                  :opened-at (:opened_at breaker)}]))
         (into {}))))

(defn monitor-and-alert!
  "Run monitoring checks and trigger alerts if needed.
  
  This function is designed to be called by a scheduled job."
  [db {:keys [alert-fn] :or {alert-fn (fn [alert] (log/warn "ALERT:" alert))}}]
  (let [health-result (health-check db)
        circuit-breakers (check-circuit-breakers db)]

    ;; Log health status
    (case (:status health-result)
      :critical (log/error "Queue health CRITICAL" health-result)
      :warning (log/warn "Queue health WARNING" health-result)
      :healthy (log/debug "Queue health OK" health-result))

    ;; Alert on critical issues
    (doseq [issue (:issues health-result)]
      (when (= (:severity issue) :critical)
        (alert-fn {:type :queue-health
                   :issue issue
                   :timestamp (java.time.Instant/now)})))

    ;; Alert on open circuit breakers
    (doseq [[service breaker] circuit-breakers]
      (when (= (:state breaker) "open")
        (alert-fn {:type :circuit-breaker-open
                   :service service
                   :breaker breaker
                   :timestamp (java.time.Instant/now)})))

    {:health health-result
     :circuit-breakers circuit-breakers}))