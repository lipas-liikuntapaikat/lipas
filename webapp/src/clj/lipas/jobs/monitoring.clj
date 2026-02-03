(ns lipas.jobs.monitoring
  "Database-based monitoring for the job queue system.

  Provides health checks, metrics recording, and alert detection
  without external dependencies like Prometheus.

  Also includes the background health monitor that periodically checks
  memory usage, stuck jobs, and timeout patterns."
  (:require
   [lipas.jobs.db :as jobs-db]
   [lipas.jobs.core :as jobs]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

;; State for the background health monitor
(defonce monitor-state (atom {:scheduler nil}))

(defn record-job-metric!
  "Record execution metrics for a completed job.
  
  Parameters:
  - db: Database connection
  - job-type: Type of job
  - status: Final status (completed/failed/dead)
  - started-at: When job started
  - created-at: When job was created
  - correlation-id: Job correlation ID for tracing"
  [db job-type status started-at created-at correlation-id]
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
      :queue_time_ms queue-time-ms
      :correlation_id correlation-id})))

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
  "Run monitoring checks and log alerts for issues.

  This function is designed to be called by a scheduled job."
  [db]
  (let [health-result (health-check db)
        circuit-breakers (check-circuit-breakers db)]

    ;; Log health status
    (case (:status health-result)
      :critical (log/error "Queue health CRITICAL" health-result)
      :warning (log/warn "Queue health WARNING" health-result)
      :healthy (log/debug "Queue health OK" health-result))

    ;; Log critical issues
    (doseq [issue (:issues health-result)]
      (when (= (:severity issue) :critical)
        (log/error "ALERT: Queue health issue"
                   {:type :queue-health
                    :issue issue})))

    ;; Log open circuit breakers
    (doseq [[service breaker] circuit-breakers]
      (when (= (:state breaker) "open")
        (log/error "ALERT: Circuit breaker open"
                   {:service service
                    :breaker breaker})))

    {:health health-result
     :circuit-breakers circuit-breakers}))

;; =============================================================================
;; Background Health Monitor
;; =============================================================================

(defn check-memory-health
  "Check memory usage and log warnings if thresholds are exceeded."
  [threshold-percent]
  (let [runtime (Runtime/getRuntime)
        max-memory (.maxMemory runtime)
        total-memory (.totalMemory runtime)
        free-memory (.freeMemory runtime)
        used-memory (- total-memory free-memory)
        memory-percent (long (* 100 (/ used-memory max-memory)))]

    (cond
      (> memory-percent threshold-percent)
      (do
        (log/warn "High memory usage detected"
                  {:memory-percent memory-percent
                   :used-mb (/ used-memory 1024 1024)
                   :max-mb (/ max-memory 1024 1024)
                   :free-mb (/ free-memory 1024 1024)})
        ;; Force garbage collection if critical
        (when (> memory-percent 90)
          (log/info "Triggering garbage collection due to high memory usage")
          (System/gc))
        false)

      :else true)))

(defn check-job-health
  "Check for stuck jobs and job failure patterns."
  [db]
  (try
    ;; Check for jobs stuck in processing
    (let [stuck-jobs (jobs-db/find-stuck-jobs db {:minutes 60})]
      (when (seq stuck-jobs)
        (log/warn "Found stuck jobs"
                  {:count (count stuck-jobs)
                   :job-ids (map :id stuck-jobs)})))

    ;; Check failure rates by job type
    (let [stats (jobs/get-job-stats-by-type db)]
      (doseq [{:keys [type failed total]} stats]
        (when (and (> total 10) ; Only check if we have enough samples
                   (> (/ failed total) 0.5)) ; More than 50% failure rate
          (log/error "High failure rate detected"
                     {:job-type type
                      :failed failed
                      :total total
                      :failure-rate (float (/ failed total))}))))

    (catch Exception e
      (log/error e "Error checking job health"))))

(defn check-timeout-patterns
  "Analyze timeout patterns to suggest configuration changes."
  [db]
  (try
    (let [recent-timeouts (jobs-db/query-jobs
                           db
                           {:error_message "Job execution timed out"
                            :created_after (-> (java.time.Instant/now)
                                               (.minus 1 java.time.temporal.ChronoUnit/HOURS)
                                               (.toString))})
          by-type (group-by :type recent-timeouts)]

      (doseq [[job-type jobs] by-type]
        (when (>= (count jobs) 3) ; 3 or more timeouts in an hour
          (log/warn "Multiple timeouts detected for job type"
                    {:job-type job-type
                     :count (count jobs)
                     :suggestion (str "Consider increasing timeout for " job-type " jobs")}))))

    (catch Exception e
      (log/error e "Error checking timeout patterns"))))

(defn start-health-monitor!
  "Start the health monitoring background thread."
  [db config]
  (let [scheduler (Executors/newScheduledThreadPool 1)
        interval-ms (:memory-check-interval-ms config 60000)
        threshold-percent (:memory-threshold-percent config 85)]

    (log/info "Starting health monitor"
              {:interval-ms interval-ms
               :memory-threshold-percent threshold-percent})

    ;; Schedule periodic health checks
    (.scheduleWithFixedDelay scheduler
                             (fn []
                               (try
                                 (check-memory-health threshold-percent)
                                 (check-job-health db)
                                 (check-timeout-patterns db)
                                 (catch Exception e
                                   (log/error e "Error in health monitor"))))
                             0
                             interval-ms
                             TimeUnit/MILLISECONDS)

    (swap! monitor-state assoc :scheduler scheduler)
    scheduler))

(defn stop-health-monitor!
  "Stop the health monitoring."
  []
  (when-let [scheduler (:scheduler @monitor-state)]
    (log/info "Stopping health monitor")
    (.shutdown ^ScheduledExecutorService scheduler)
    (.awaitTermination ^ScheduledExecutorService scheduler 5 TimeUnit/SECONDS)
    (swap! monitor-state assoc :scheduler nil)))