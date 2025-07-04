(ns lipas.jobs.core
  "Unified job queue system for LIPAS background processing.

  Replaces the existing separate queue tables (analysis_queue, elevation_queue,
  email_out_queue, integration_out_queue, webhook_queue) with a single
  unified jobs table and smart concurrency control."
  (:require
   [lipas.jobs.db :as jobs-db]
   [lipas.jobs.patterns :as patterns]
   [lipas.jobs.payload-schema :as payload-schema]
   [malli.core :as m]
   [taoensso.timbre :as log]))

;; Job type specifications

(def job-type-schema
  [:enum "analysis" "elevation" "email" "integration" "webhook"
   "produce-reminders" "cleanup-jobs" "monitor-queue-health"])

(def job-status-schema
  [:enum "pending" "processing" "completed" "failed" "dead"])

;; Job duration classifications for smart concurrency
(def job-duration-types
  "Classification of job types by expected duration.
  Fast jobs get priority thread allocation to prevent blocking."
  {:fast #{"email" "produce-reminders" "cleanup-jobs" "integration" "webhook" "monitor-queue-health"}
   :slow #{"analysis" "elevation"}})

(defn fast-job?
  "Check if a job type is classified as fast."
  [job-type]
  (contains? (:fast job-duration-types) job-type))

(defn enqueue-job!
  "Enqueue a job for processing with payload validation.

  Parameters:
  - db: Database connection
  - job-type: String job type (see job-type-schema)
  - payload: Map containing job-specific data (will be validated)
  - opts: Optional map with:
    :priority - Job priority (default 100)
    :max-attempts - Max retry attempts (default 3)
    :run-at - When to run (default now)
    :correlation-id - UUID for tracking related jobs
    :parent-job-id - ID of parent job
    :created-by - Who created this job
    :dedup-key - Deduplication key (prevents duplicate jobs)

  Returns: Map with :id and :correlation-id
  Throws: ex-info if validation fails"
  [db job-type payload & [{:keys [priority max-attempts run-at correlation-id
                                  parent-job-id created-by dedup-key]
                           :or {priority 100
                                max-attempts 3
                                run-at (java.sql.Timestamp/from (java.time.Instant/now))
                                correlation-id (java.util.UUID/randomUUID)}}]]
  {:pre [(m/validate job-type-schema job-type)
         (map? payload)]}

  ;; Validate payload
  (let [validation (payload-schema/validate-payload-for-type job-type payload)]
    (when-not (:valid? validation)
      (log/error "Invalid job payload"
                 {:job-type job-type
                  :payload payload
                  :errors (:errors validation)})
      (throw (ex-info "Invalid job payload"
                      {:job-type job-type
                       :payload payload
                       :errors (:errors validation)}))))

  ;; Transform payload (applies defaults)
  (let [transformed-payload (payload-schema/transform-payload job-type payload)]
    (log/debug "Enqueuing job with correlation"
               {:type job-type
                :correlation-id correlation-id
                :dedup-key dedup-key})

    (jobs-db/enqueue-job-with-correlation!
     db
     {:type job-type
      :payload transformed-payload
      :priority priority
      :max_attempts max-attempts
      :run_at run-at
      :correlation_id correlation-id
      :parent_job_id parent-job-id
      :created_by created-by
      :dedup_key dedup-key})))

(defn fetch-next-jobs
  "Fetch the next batch of jobs to process, with atomic locking.

  Uses PostgreSQL SELECT FOR UPDATE SKIP LOCKED for safe concurrent access.

  Parameters:
  - db: Database connection
  - opts: Map with :limit, :job-types (vector of allowed types)

  Returns: Vector of job maps"
  [db {:keys [limit job-types] :or {limit 5}}]

  (let [jobs (jobs-db/fetch-next-jobs db {:limit limit
                                          :job_types (when job-types (into-array String job-types))})]
    (when (seq jobs)
      (log/debug "Fetched jobs" {:count (count jobs) :types (map :type jobs)}))
    jobs))

(defn mark-completed!
  "Mark a job as successfully completed."
  [db job-id]
  (log/debug "Marking job completed" {:id job-id})
  (jobs-db/mark-job-completed! db {:id job-id}))

(defn mark-failed!
  "Mark a job as failed. Will retry if attempts < max-attempts, otherwise mark as dead."
  [db job-id error-message]
  (log/info "Marking job failed" {:id job-id :error error-message})
  (jobs-db/mark-job-failed! db {:id job-id :error_message (str error-message)}))

(defn move-to-dead-letter!
  "Move a permanently failed job to the dead letter queue.

  Parameters:
  - db: Database connection
  - job-id: Job ID
  - error-message: Final error description
  - error-details: Optional map with additional error context"
  [db job-id error-message & [error-details]]
  (log/warn "Moving job to dead letter queue"
            {:id job-id
             :error error-message
             :details error-details})
  (jobs-db/move-job-to-dead-letter! db {:id job-id
                                        :error_message error-message})
  ;; Optionally record additional details
  (when error-details
    (jobs-db/insert-dead-letter!
     db
     {:original_job {:job_id job-id}
      :error_message error-message
      :error_details error-details})))

(defn fail-job!
  "Mark a job as failed with exponential backoff retry scheduling.

  Uses the patterns library to calculate next retry time.
  Moves to dead letter queue if max attempts exceeded.

  Parameters:
  - db: Database connection
  - job-id: Job ID
  - error-message: Error description
  - opts: Map with :backoff-opts for exponential backoff config,
          :correlation-id for tracing"
  [db job-id error-message & [{:keys [backoff-opts current-attempt max-attempts correlation-id]
                               :or {backoff-opts {}}}]]
  (log/info "Job failed" {:id job-id
                          :error error-message
                          :attempt current-attempt
                          :correlation-id correlation-id})

  (if (and current-attempt max-attempts (>= current-attempt max-attempts))
    ;; Max attempts reached - move to dead letter
    (do
      (log/warn "Moving job to dead letter queue"
                {:id job-id
                 :attempts current-attempt
                 :correlation-id correlation-id})
      (move-to-dead-letter! db job-id error-message))
    ;; Schedule retry with exponential backoff
    (let [delay-ms (patterns/exponential-backoff-ms (or current-attempt 0) backoff-opts)
          run-at (java.sql.Timestamp. (+ (System/currentTimeMillis) delay-ms))]
      (log/debug "Scheduling job retry"
                 {:id job-id
                  :delay-ms delay-ms
                  :run-at run-at
                  :correlation-id correlation-id})
      (jobs-db/update-job-retry! db {:id job-id
                                     :error_message error-message
                                     :run_at run-at}))))

(defn get-queue-stats
  "Get current queue statistics for monitoring."
  [db]
  (->> (jobs-db/get-job-stats db)
       (group-by :status) ; Keep status as string from database
       (map (fn [[status entries]]
              [(keyword status) (first entries)]))
       (into {})))

(defn get-job-stats-by-type
  "Get job statistics grouped by type."
  [db]
  (jobs-db/get-job-stats-by-type db))

(defn get-performance-metrics
  "Get detailed performance metrics by job type within timeframe."
  [db {:keys [from-hours-ago to-hours-ago]
       :or {from-hours-ago 24 to-hours-ago 0}}]
  (let [now (java.time.Instant/now)
        from-timestamp (java.sql.Timestamp/from (.minus now from-hours-ago java.time.temporal.ChronoUnit/HOURS))
        to-timestamp (java.sql.Timestamp/from (.minus now to-hours-ago java.time.temporal.ChronoUnit/HOURS))]
    (->> (jobs-db/get-performance-metrics db {:from_timestamp from-timestamp
                                              :to_timestamp to-timestamp})
         (map #(-> %
                   (update :earliest_job str)
                   (update :latest_job str))))))

(defn get-hourly-throughput
  "Get job throughput by hour within timeframe."
  [db {:keys [from-hours-ago to-hours-ago]
       :or {from-hours-ago 24 to-hours-ago 0}}]
  (let [now (java.time.Instant/now)
        from-timestamp (java.sql.Timestamp/from (.minus now from-hours-ago java.time.temporal.ChronoUnit/HOURS))
        to-timestamp (java.sql.Timestamp/from (.minus now to-hours-ago java.time.temporal.ChronoUnit/HOURS))]
    (->> (jobs-db/get-hourly-throughput db {:from_timestamp from-timestamp
                                            :to_timestamp to-timestamp})
         (map #(-> %
                   (update :hour str))))))

(defn get-queue-health
  "Get current queue health metrics."
  [db]
  (-> (jobs-db/get-queue-health db)
      (update :oldest_pending_minutes #(when % (Math/round (double %))))
      (update :longest_processing_minutes #(when % (Math/round (double %))))))

(defn get-admin-metrics
  "Get comprehensive admin metrics for monitoring dashboard."
  [db {:keys [from-hours-ago to-hours-ago]
       :or {from-hours-ago 24 to-hours-ago 0}
       :as opts}]
  {:current-stats (get-queue-stats db)
   :health (get-queue-health db)
   :performance-metrics (vec (get-performance-metrics db opts))
   :hourly-throughput (vec (get-hourly-throughput db opts))
   :fast-job-types (vec (get job-duration-types :fast)) ; Convert set to vector
   :slow-job-types (vec (get job-duration-types :slow)) ; Convert set to vector
   :generated-at (str (java.time.Instant/now))})

(defn get-jobs-by-correlation
  "Find all jobs with a given correlation ID.

  Useful for tracing related jobs through the system."
  [db correlation-id]
  (jobs-db/get-job-by-correlation db {:correlation_id correlation-id}))

(defn get-metrics-by-correlation
  "Get all metrics for jobs with a given correlation ID."
  [db correlation-id]
  (jobs-db/get-metrics-by-correlation db {:correlation_id correlation-id}))

(defn get-correlation-trace
  "Get a complete trace of all activity for a correlation ID.

  Returns a unified timeline of jobs and metrics."
  [db correlation-id]
  (jobs-db/get-correlation-trace db {:correlation_id correlation-id}))

(defn create-job-chain
  "Create a chain of related jobs with the same correlation ID.

  This is useful for creating complex workflows where multiple jobs
  need to be tracked together.

  Parameters:
  - db: Database connection
  - jobs: Vector of job specs [{:type \"...\", :payload {...}, :opts {...}}]
  - correlation-id: Optional correlation ID (auto-generated if not provided)

  Returns: Vector of job results with shared correlation ID"
  [db jobs & [correlation-id]]
  (let [correlation-id (or correlation-id (java.util.UUID/randomUUID))]
    (mapv (fn [{:keys [type payload opts]}]
            (enqueue-job!
             db type payload
             (assoc opts :correlation-id correlation-id)))
          jobs)))

(defn gen-correlation-id
  "Generate a new correlation ID for tracking related jobs."
  []
  (java.util.UUID/randomUUID))

(defn with-correlation-context
  "Execute a function with correlation ID in the logging context.

  This ensures all log messages within the function execution
  include the correlation ID for easier debugging."
  [correlation-id f]
  (log/with-context {:correlation-id correlation-id}
    (f)))

(defn cleanup-old-jobs!
  "Remove completed and dead jobs older than specified days."
  [db days]
  (log/info "Cleaning up jobs older than" days "days")
  (jobs-db/cleanup-old-jobs! db {:days days}))

(defn reset-stuck-jobs!
  "Reset jobs that have been stuck in processing state for too long.
   This should be called on worker startup to recover from crashes."
  [db timeout-minutes]
  (let [result (jobs-db/reset-stuck-jobs! db {:timeout_minutes timeout-minutes})]
    (when (pos? result)
      (log/warn "Reset stuck jobs on startup" {:count result :timeout-minutes timeout-minutes}))
    result))

(defn example-job-payloads
  "Get example payloads for all job types for documentation/testing."
  []
  (payload-schema/example-payloads))
