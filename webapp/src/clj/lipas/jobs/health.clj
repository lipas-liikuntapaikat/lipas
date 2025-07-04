(ns lipas.jobs.health
  "Health monitoring for the job worker system."
  (:require
   [lipas.jobs.core :as jobs]
   [lipas.jobs.db :as jobs-db]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(defonce monitor-state (atom {:scheduler nil}))

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