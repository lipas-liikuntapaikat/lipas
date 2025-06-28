(ns lipas.jobs.core
  "Unified job queue system for LIPAS background processing.

  Replaces the existing separate queue tables (analysis_queue, elevation_queue,
  email_out_queue, integration_out_queue, webhook_queue) with a single
  unified jobs table and smart concurrency control."
  (:require
   [lipas.jobs.db :as jobs-db]
   [lipas.jobs.patterns :as patterns]
   [malli.core :as m]
   [taoensso.timbre :as log]))

;; Job type specifications

(def job-type-schema
  [:enum "analysis" "elevation" "email" "integration" "webhook" "webhook-batch"
   "produce-reminders" "cleanup-jobs" "monitor-queue-health"])

(def job-status-schema
  [:enum "pending" "processing" "completed" "failed" "dead"])

(def priority-schema
  [:and int? [:>= 0]])

(def payload-schema map?)

(def job-spec-schema
  [:map
   [:job-type job-type-schema]
   [:payload payload-schema]
   [:priority {:optional true} priority-schema]
   [:max-attempts {:optional true} int?]
   [:run-at {:optional true} any?]])

;; Job duration classifications for smart concurrency
(def job-duration-types
  "Classification of job types by expected duration.
  Fast jobs get priority thread allocation to prevent blocking."
  {:fast #{"email" "produce-reminders" "cleanup-jobs" "integration" "webhook" "webhook-batch" "monitor-queue-health"}
   :slow #{"analysis" "elevation"}})

(defn fast-job?
  "Check if a job type is classified as fast."
  [job-type]
  (contains? (:fast job-duration-types) job-type))

(defn enqueue-job!
  "Add a new job to the unified queue.

  Parameters:
  - db: Database connection
  - job-type: String job type (must be in ::job-type spec)
  - payload: Map containing job data
  - opts: Optional map with :priority, :max-attempts, :run-at

  Returns: Job ID"
  [db job-type payload & [{:keys [priority max-attempts run-at]
                           :or {priority 100 max-attempts 3 run-at (java.sql.Timestamp/from (java.time.Instant/now))}}]]

  {:pre [(m/validate job-type-schema job-type)
         (map? payload)]}

  (log/debug "Enqueuing job" {:type job-type :payload payload :priority priority})

  (let [result (jobs-db/enqueue-job! db {:type job-type
                                         :payload payload
                                         :priority priority
                                         :max_attempts max-attempts
                                         :run_at run-at})]
    (:id result)))

(defn enqueue-job-with-correlation!
  "Enhanced job enqueue with correlation ID and deduplication support.
  
  Parameters:
  - db: Database connection
  - job-type: String job type
  - payload: Map containing job data
  - opts: Map with:
    :priority - Job priority (default 100)
    :max-attempts - Max retry attempts (default 3)
    :run-at - When to run (default now)
    :correlation-id - UUID for tracking related jobs
    :parent-job-id - ID of parent job
    :created-by - Who created this job
    :dedup-key - Deduplication key (prevents duplicate jobs)
    
  Returns: Map with :id and :correlation-id, or nil if deduplicated"
  [db job-type payload & [{:keys [priority max-attempts run-at correlation-id
                                  parent-job-id created-by dedup-key]
                           :or {priority 100
                                max-attempts 3
                                run-at (java.sql.Timestamp/from (java.time.Instant/now))
                                correlation-id (java.util.UUID/randomUUID)}}]]
  {:pre [(m/validate job-type-schema job-type)
         (map? payload)]}

  (log/debug "Enqueuing job with correlation"
             {:type job-type
              :correlation-id correlation-id
              :dedup-key dedup-key})

  (jobs-db/enqueue-job-with-correlation!
   db
   {:type job-type
    :payload payload
    :priority priority
    :max_attempts max-attempts
    :run_at run-at
    :correlation_id correlation-id
    :parent_job_id parent-job-id
    :created_by created-by
    :dedup_key dedup-key}))

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

(defn fail-job!
  "Mark a job as failed with exponential backoff retry scheduling.
  
  Uses the patterns library to calculate next retry time.
  Moves to dead letter queue if max attempts exceeded.
  
  Parameters:
  - db: Database connection
  - job-id: Job ID
  - error-message: Error description
  - opts: Map with :backoff-opts for exponential backoff config"
  [db job-id error-message & [{:keys [backoff-opts current-attempt max-attempts]
                               :or {backoff-opts {}}}]]
  (log/info "Job failed" {:id job-id :error error-message :attempt current-attempt})

  (if (and current-attempt max-attempts (>= current-attempt max-attempts))
    ;; Max attempts reached - move to dead letter
    (do
      (log/warn "Moving job to dead letter queue" {:id job-id :attempts current-attempt})
      (jobs-db/move-job-to-dead-letter! db {:id job-id :error_message error-message}))
    ;; Schedule retry with exponential backoff
    (let [delay-ms (patterns/exponential-backoff-ms (or current-attempt 0) backoff-opts)
          run-at (java.sql.Timestamp. (+ (System/currentTimeMillis) delay-ms))]
      (log/debug "Scheduling job retry" {:id job-id :delay-ms delay-ms :run-at run-at})
      (jobs-db/update-job-retry! db {:id job-id
                                     :error_message error-message
                                     :run_at run-at}))))

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

(defn get-queue-stats
  "Get current queue statistics for monitoring."
  [db]
  (->> (jobs-db/get-job-stats db)
       (group-by :status) ; Keep status as string from database
       (map (fn [[status entries]]
              [(keyword status) (first entries)]))
       (into {})))

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

(defn cleanup-old-jobs!
  "Remove completed and dead jobs older than specified days."
  [db days]
  (log/info "Cleaning up jobs older than" days "days")
  (jobs-db/cleanup-old-jobs! db {:days days}))

;; Webhook Operations

(defn enqueue-webhook!
  "Enqueue a single webhook job for immediate delivery.
  
  Parameters:
  - db: Database connection
  - webhook-data: Map with webhook payload
  - opts: Optional priority and other job options
  
  Returns: Job ID"
  [db webhook-data & [opts]]
  (log/debug "Enqueuing individual webhook" {:data webhook-data})
  (enqueue-job! db "webhook" webhook-data (merge {:priority 90} opts)))

(defn enqueue-webhook-batch!
  "Enqueue a batch webhook job for bulk operations.
  
  Use this for operations that affect multiple sports sites at once,
  like bulk email updates or mass data imports.
  
  Parameters:
  - db: Database connection
  - changes: Vector of change maps, each with site data
  - operation-info: Map with:
    :operation-type - Description of the bulk operation
    :initiated-by - Who initiated the operation
    :site-count - Number of sites affected (optional, will be calculated)
  - opts: Optional priority and other job options
  
  Returns: Job ID"
  [db changes operation-info & [opts]]
  {:pre [(vector? changes) (map? operation-info)]}

  (let [site-count (count changes)
        payload (merge operation-info
                       {:type "bulk-update"
                        :changes changes
                        :site-count site-count
                        :created-at (java.time.Instant/now)})]

    (log/info "Enqueuing webhook batch"
              {:operation-type (:operation-type operation-info)
               :site-count site-count
               :initiated-by (:initiated-by operation-info)})

    (enqueue-job! db "webhook-batch" payload (merge {:priority 85} opts))))

(defn should-use-batch-webhook?
  "Determine if a set of changes should use batch webhook or individual webhooks.
  
  Parameters:
  - changes: Vector of change maps
  - operation-context: Map with context about the operation
  
  Returns: Boolean - true if should use batch webhook"
  [changes operation-context]
  (let [change-count (count changes)
        batch-threshold (get operation-context :batch-threshold 5)
        is-bulk-operation? (get operation-context :is-bulk-operation false)]

    (or is-bulk-operation?
        (>= change-count batch-threshold))))

(defn enqueue-webhooks-smart!
  "Smart webhook enqueuing - automatically chooses between individual and batch.
  
  Parameters:
  - db: Database connection
  - changes: Vector of change maps
  - operation-context: Map with:
    :operation-type - Type of operation
    :initiated-by - Who initiated
    :is-bulk-operation - Force batch mode (optional)
    :batch-threshold - Minimum changes for batch (default 5)
  
  Returns: Vector of job IDs"
  [db changes operation-context]
  (if (should-use-batch-webhook? changes operation-context)
    ;; Use batch webhook for bulk operations
    [(enqueue-webhook-batch! db changes operation-context)]

    ;; Use individual webhooks for single/few changes
    (mapv #(enqueue-webhook! db %) changes)))

;; Legacy compatibility functions for existing code

(defn add-to-analysis-queue!
  "Legacy compatibility: Add analysis job to unified queue."
  [db sports-site]
  (enqueue-job! db "analysis" {:lipas-id (:lipas-id sports-site)} {:priority 80}))

(defn add-to-elevation-queue!
  "Legacy compatibility: Add elevation job to unified queue."
  [db sports-site]
  (enqueue-job! db "elevation" {:lipas-id (:lipas-id sports-site)} {:priority 70}))

(defn add-to-integration-out-queue!
  "Legacy compatibility: Add integration job to unified queue."
  [db sports-site]
  (enqueue-job! db "integration" {:lipas-id (:lipas-id sports-site)} {:priority 90}))

(defn add-to-webhook-queue!
  "Legacy compatibility: Add webhook job to unified queue."
  [db batch-data]
  (enqueue-job! db "webhook" {:batch-data batch-data} {:priority 85}))

(defn add-to-email-out-queue!
  "Legacy compatibility: Add email job to unified queue."
  [db message]
  (enqueue-job! db "email" message {:priority 95}))
