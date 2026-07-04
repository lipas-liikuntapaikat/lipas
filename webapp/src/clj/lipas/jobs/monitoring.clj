(ns lipas.jobs.monitoring
  "Health checks for the job queue.

  Alerting is log-based: warnings/errors logged here flow to the central
  log pipeline. The same health data is exposed to admins via the
  get-jobs-health-status endpoint."
  (:require
   [lipas.jobs.core :as jobs]
   [lipas.jobs.patterns :as patterns]
   [taoensso.timbre :as log]))

(defn health-check
  "Perform a health check of the job queue.

  Returns a map with :status (:healthy/:warning/:critical), :health
  (raw queue metrics) and :issues."
  [db]
  (let [health (jobs/get-queue-health db)
        issues
        (cond-> []
          ;; Jobs processing longer than the stuck-job threshold indicate
          ;; a crashed worker or a hung handler
          (and (:longest_processing_minutes health)
               (> (:longest_processing_minutes health) jobs/stuck-job-timeout-minutes))
          (conj {:type :stuck-jobs
                 :severity :warning
                 :message (str "Jobs processing for over "
                               (:longest_processing_minutes health)
                               " minutes")})

          ;; Old pending jobs indicate a backlog or a stopped worker
          (and (:oldest_pending_minutes health)
               (> (:oldest_pending_minutes health) 30))
          (conj {:type :old-pending
                 :severity :warning
                 :message (str "Pending jobs older than "
                               (:oldest_pending_minutes health)
                               " minutes")})

          ;; Unacknowledged dead letters need admin attention
          (> (:dead_count health 0) 10)
          (conj {:type :dead-letters
                 :severity :critical
                 :message (str (:dead_count health)
                               " unacknowledged jobs in dead letter queue")}))]
    {:status (cond
               (some #(= (:severity %) :critical) issues) :critical
               (seq issues) :warning
               :else :healthy)
     :health health
     :issues issues
     :checked-at (java.time.Instant/now)}))

(defn log-queue-health!
  "Run a health check and log the outcome. Also logs open circuit breakers."
  [db]
  (let [result (health-check db)]
    (case (:status result)
      :critical (log/error "Queue health CRITICAL" result)
      :warning (log/warn "Queue health WARNING" result)
      :healthy (log/debug "Queue health OK" result))

    (doseq [[service breaker] (patterns/breaker-status)]
      (when (= :open (:state breaker))
        (log/error "Circuit breaker open" {:service service :breaker breaker})))

    result))
