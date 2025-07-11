(ns lipas.jobs.core
  "Unified job queue system for LIPAS background processing.

  Replaces the existing separate queue tables (analysis_queue, elevation_queue,
  email_out_queue, integration_out_queue, webhook_queue) with a single
  unified jobs table and smart concurrency control."
  (:require
   [cheshire.core]
   [lipas.backend.db.utils :refer [->kebab-case-keywords]]
   [lipas.jobs.db :as jobs-db]
   [lipas.jobs.patterns :as patterns]
   [lipas.jobs.payload-schema :as payload-schema]
   [malli.core :as m]
   [next.jdbc :as jdbc]
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
  "DEPRECATED - DO NOT USE. Use fail-job! instead.
   This function will be removed in the next version."
  [db job-id error-message]
  (throw (ex-info "mark-failed! is deprecated and has been removed. Use fail-job! instead."
                  {:job-id job-id
                   :error-message error-message
                   :suggestion "Use (fail-job! db job-id error-message {:current-attempt attempts :max-attempts max-attempts})"})))

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
  ;; The SQL query already handles both updating status to 'dead' 
  ;; and inserting into dead_letter_jobs table
  (jobs-db/move-job-to-dead-letter! db {:id job-id
                                        :error_message error-message}))

(defn fail-job!
  "Mark a job as failed with exponential backoff retry scheduling.

  Uses the patterns library to calculate next retry time.
  Moves to dead letter queue if max attempts exceeded.

  Parameters:
  - db: Database connection
  - job-id: Job ID
  - error-message: Error description
  - opts: Map with :backoff-opts for exponential backoff config,
          :correlation-id for tracing,
          :current-attempt (the attempt that just failed),
          :max-attempts"
  [db job-id error-message & [{:keys [backoff-opts current-attempt max-attempts correlation-id]
                               :or {backoff-opts {}}}]]
  (log/info "Job failed" {:id job-id
                          :error error-message
                          :attempt current-attempt
                          :max-attempts max-attempts
                          :correlation-id correlation-id})

  ;; Note: current-attempt is the attempt that just failed (already incremented)
  ;; So if current-attempt >= max-attempts, we've exhausted all retries
  (if (and current-attempt max-attempts (>= current-attempt max-attempts))
    ;; Max attempts reached - move to dead letter
    (do
      (log/warn "Moving job to dead letter queue - max attempts exhausted"
                {:id job-id
                 :attempts current-attempt
                 :max-attempts max-attempts
                 :correlation-id correlation-id})
      (move-to-dead-letter! db job-id error-message
                            {:attempts current-attempt
                             :max-attempts max-attempts
                             :correlation-id correlation-id}))
    ;; Schedule retry with exponential backoff
    (let [delay-ms (patterns/exponential-backoff-ms (or current-attempt 1) backoff-opts)
          run-at (java.sql.Timestamp. (+ (System/currentTimeMillis) delay-ms))]
      (log/debug "Scheduling job retry"
                 {:id job-id
                  :current-attempt current-attempt
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

(defn get-dead-letter-jobs
  "Get dead letter jobs with optional acknowledgment filter.
  
  Parameters:
  - db: Database connection
  - opts: {:acknowledged true/false/nil} - nil returns all"
  [db {:keys [acknowledged] :as _opts}]
  (map ->kebab-case-keywords
       (jobs-db/get-dead-letter-jobs db {:acknowledged acknowledged})))

(defn reprocess-dead-letter-job!
  "Reprocess a dead letter job by requeuing it and marking as acknowledged.
  
  Parameters:
  - db: Database connection  
  - dead-letter-id: ID of the dead letter job
  - user-email: Email of admin performing the action
  - opts: Optional {:max-attempts 3}
  
  Returns the newly created job or throws on error."
  [db dead-letter-id user-email & [{:keys [max-attempts] :or {max-attempts 3}}]]
  ;; First verify the dead letter job exists
  (when-not (jobs-db/get-dead-letter-by-id db {:id dead-letter-id})
    (throw (ex-info "Dead letter job not found" {:id dead-letter-id})))

  ;; Requeue the job
  (let [new-job (jobs-db/requeue-dead-letter-job! db
                                                  {:id dead-letter-id
                                                   :max_attempts max-attempts
                                                   :reprocessed_by user-email})]

    ;; Mark as acknowledged
    (jobs-db/acknowledge-dead-letter! db
                                      {:id dead-letter-id
                                       :acknowledged_by user-email})

    (log/info "Reprocessed dead letter job"
              {:dead-letter-id dead-letter-id
               :new-job-id (:id new-job)
               :user user-email})

    (->kebab-case-keywords new-job)))

(defn reprocess-dead-letter-jobs!
  "Bulk reprocess multiple dead letter jobs.
  
  Parameters:
  - db: Database connection
  - dead-letter-ids: Collection of dead letter job IDs
  - user-email: Email of admin performing the action
  - opts: Optional {:max-attempts 3}
  
  Returns map with :succeeded and :failed job IDs."
  [db dead-letter-ids user-email & [opts]]
  (let [results (reduce (fn [acc id]
                          (try
                            (let [job (reprocess-dead-letter-job! db id user-email opts)]
                              (update acc :succeeded conj {:dead-letter-id id
                                                           :new-job-id (:id job)}))
                            (catch Exception e
                              (log/error e "Failed to reprocess dead letter job" {:id id})
                              (update acc :failed conj {:dead-letter-id id
                                                        :error (.getMessage e)}))))
                        {:succeeded []
                         :failed []}
                        dead-letter-ids)]
    (log/info "Bulk reprocess completed"
              {:total (count dead-letter-ids)
               :succeeded (count (:succeeded results))
               :failed (count (:failed results))
               :user user-email})
    results))

(defn acknowledge-dead-letter-jobs!
  "Acknowledge multiple dead letter jobs without reprocessing them.
  
  Parameters:
  - db: Database connection
  - dead-letter-ids: Collection of dead letter job IDs
  - user-email: Email of admin performing the action
  
  Returns map with :acknowledged count."
  [db dead-letter-ids user-email]
  (let [count (reduce (fn [count id]
                        (try
                          ;; First check if the job exists and is not already acknowledged
                          (let [dlj (jobs-db/get-dead-letter-by-id db {:id id})]
                            (if (and dlj (not (:acknowledged dlj)))
                              (do
                                (jobs-db/acknowledge-dead-letter! db
                                                                  {:id id
                                                                   :acknowledged_by user-email})
                                (inc count))
                              count))
                          (catch Exception e
                            (log/error e "Failed to acknowledge dead letter job" {:id id})
                            count)))
                      0
                      dead-letter-ids)]
    {:acknowledged count}))

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

(defn migrate-orphaned-dead-jobs!
  "Migrate any jobs with status 'dead' that aren't in dead_letter_jobs table.
   This handles legacy dead jobs that were marked dead before the proper
   dead letter queue mechanism was implemented.
   
   Returns the number of jobs migrated."
  [db]
  (try
    (let [orphaned-dead-jobs (jdbc/execute! db
                                            ["SELECT j.* FROM jobs j
                                LEFT JOIN dead_letter_jobs dlj ON dlj.original_job->>'id' = j.id::text
                                WHERE j.status = 'dead' AND dlj.id IS NULL"])
          count (count orphaned-dead-jobs)]

      (when (pos? count)
        (log/warn "Found orphaned dead jobs that need migration to dead_letter_jobs"
                  {:count count})

        (doseq [job orphaned-dead-jobs]
          (let [job-id (:jobs/id job)
                error-msg (or (:jobs/error_message job)
                              (:jobs/last_error job)
                              "Legacy dead job - no error message recorded")
                ;; Convert job data to JSON-safe format
                job-data {:id job-id
                          :type (:jobs/type job)
                          :payload (:jobs/payload job)
                          :status (:jobs/status job)
                          :priority (:jobs/priority job)
                          :attempts (:jobs/attempts job)
                          :max_attempts (:jobs/max_attempts job)
                          :created_at (str (:jobs/created_at job))
                          :started_at (when (:jobs/started_at job) (str (:jobs/started_at job)))
                          :completed_at (when (:jobs/completed_at job) (str (:jobs/completed_at job)))
                          :error_message error-msg
                          :last_error (:jobs/last_error job)
                          :correlation_id (when (:jobs/correlation_id job) (str (:jobs/correlation_id job)))
                          :dedup_key (:jobs/dedup_key job)}]
            (try
              ;; Insert into dead_letter_jobs
              (jdbc/execute! db
                             ["INSERT INTO dead_letter_jobs (original_job, error_message, correlation_id)
                  VALUES (?::jsonb, ?, ?)
                  ON CONFLICT DO NOTHING"
                              (cheshire.core/generate-string job-data)
                              error-msg
                              (:jobs/correlation_id job)])

              (log/info "Migrated orphaned dead job to dead_letter_jobs"
                        {:job-id job-id
                         :job-type (:jobs/type job)
                         :error error-msg})

              (catch Exception e
                (log/error e "Failed to migrate dead job"
                           {:job-id job-id})))))

        (log/info "Completed migration of orphaned dead jobs"
                  {:migrated count}))

      count)

    (catch Exception e
      (log/error e "Error checking for orphaned dead jobs")
      0)))

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
