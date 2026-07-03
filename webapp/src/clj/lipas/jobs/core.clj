(ns lipas.jobs.core
  "Unified job queue for LIPAS background processing.

  Single Postgres-backed jobs table with SELECT FOR UPDATE SKIP LOCKED
  fetching, retries with exponential backoff and a dead letter queue.

  State model: pending -> processing -> completed. A retry is a pending
  job with a future run_at. Permanently failed jobs are moved to the
  dead_letter_jobs table and removed from jobs."
  (:require
   [lipas.backend.db.utils :refer [->kebab-case-keywords]]
   [lipas.jobs.db :as jobs-db]
   [lipas.jobs.patterns :as patterns]
   [lipas.jobs.registry :as registry]
   [lipas.jobs.triage :as triage]
   [taoensso.timbre :as log]))

(def job-type-schema registry/job-type-schema)
(def job-status-schema registry/job-status-schema)

(def stuck-job-timeout-minutes
  "Processing jobs older than this are considered crashed and get recovered.
  Derived from the longest registered job timeout plus a safety margin, so
  a legitimately long-running job is never recovered while still running."
  (+ 30 (apply max (map :timeout-min (vals registry/job-types)))))

(defn enqueue-job!
  "Enqueue a job for processing with payload validation.

  Registry defaults (priority, max-attempts, dedup key, debounce delay)
  are applied automatically and can be overridden via opts:
    :priority     - job priority, higher runs first
    :max-attempts - max attempts before dead-lettering
    :run-at       - when to run (overrides registry debounce)
    :created-by   - who created this job
    :dedup-key    - deduplication key (overrides registry :dedup-key-fn)

  Returns {:id <job-id>} or nil when an equal pending job already exists
  (deduplicated). Throws ex-info on unknown type or invalid payload."
  [db job-type payload & [{:keys [priority max-attempts run-at created-by dedup-key]}]]
  (let [job-def (registry/get-def job-type)
        validation (registry/validate-payload job-type payload)]
    (when-not (:valid? validation)
      (log/error "Invalid job payload"
                 {:job-type job-type
                  :payload payload
                  :errors (:errors validation)})
      (throw (ex-info "Invalid job payload"
                      {:job-type job-type
                       :payload payload
                       :errors (:errors validation)})))
    (let [dedup-key (or dedup-key
                        (when-let [f (:dedup-key-fn job-def)]
                          (f payload)))
          run-at (or run-at
                     (java.sql.Timestamp/from
                      (.plusSeconds (java.time.Instant/now)
                                    (long (:debounce-sec job-def 0)))))
          row (jobs-db/enqueue-job!
               db
               {:type job-type
                :payload payload
                :priority (or priority (:priority job-def))
                :max_attempts (or max-attempts (:max-attempts job-def))
                :run_at run-at
                :created_by created-by
                :dedup_key dedup-key})]
      (if row
        (do (log/debug "Enqueued job" {:id (:id row) :type job-type})
            {:id (:id row)})
        (do (log/debug "Job deduplicated" {:type job-type :dedup-key dedup-key})
            nil)))))

(defn fetch-next-jobs
  "Fetch the next batch of runnable jobs with atomic locking.

  Uses PostgreSQL SELECT FOR UPDATE SKIP LOCKED for safe concurrent access.

  opts: :limit (default 5), :job-types (vector of allowed types)"
  [db {:keys [limit job-types] :or {limit 5}}]
  (let [jobs (jobs-db/fetch-next-jobs db {:limit limit
                                          :job_types (when job-types
                                                       (into-array String job-types))})]
    (when (seq jobs)
      (log/debug "Fetched jobs" {:count (count jobs) :types (map :type jobs)}))
    jobs))

(defn mark-completed!
  "Mark a job as successfully completed.

  When the claimed attempts value is passed, the update is fenced to that
  execution: a stale executor (e.g. a zombie thread whose job was already
  finalized by the watchdog or recovered) updates zero rows. Returns the
  number of rows updated."
  [db job-id & [attempts]]
  (let [n (jobs-db/mark-job-completed! db {:id job-id :attempts attempts})]
    (when (and attempts (zero? n))
      (log/warn "Stale execution tried to complete a job; ignored"
                {:id job-id :attempts attempts}))
    n))

(defn move-to-dead-letter!
  "Move a permanently failed job to the dead letter queue.
  The job row is removed from the jobs table. With :attempts the delete is
  fenced to the claimed execution."
  [db job-id error-message & [attempts]]
  (log/warn "Moving job to dead letter queue" {:id job-id :error error-message})
  (jobs-db/move-job-to-dead-letter! db {:id job-id
                                        :attempts attempts
                                        :error_message error-message}))

(defn fail-job!
  "Handle a failed job: re-enqueue a retry with exponential backoff, or move
  the job to the dead letter queue when attempts are exhausted.

  The retry goes through the same dedup gate as a fresh enqueue: when a
  newer pending job with the same (type, dedup_key) exists, the retry is
  dropped as superseded - the successor does the same work on newer data.

  opts: :current-attempt (the attempt that just failed), :max-attempts,
        :backoff-opts for exponential backoff config,
        :attempts - claimed attempts fingerprint; when present, finalization
                    is fenced so a stale executor is a no-op"
  [db job-id error-message & [{:keys [backoff-opts current-attempt max-attempts attempts]
                               :or {backoff-opts {}}}]]
  (log/info "Job failed" {:id job-id
                          :error error-message
                          :attempt current-attempt
                          :max-attempts max-attempts})
  (if (and current-attempt max-attempts (>= current-attempt max-attempts))
    (move-to-dead-letter! db job-id error-message attempts)
    (let [delay-ms (patterns/exponential-backoff-ms (or current-attempt 1) backoff-opts)
          run-at (java.sql.Timestamp. (+ (System/currentTimeMillis) delay-ms))
          n (jobs-db/retry-job! db {:id job-id
                                    :attempts attempts
                                    :error_message error-message
                                    :run_at run-at})]
      (if (pos? n)
        (log/debug "Scheduled job retry" {:id job-id
                                          :attempt current-attempt
                                          :delay-ms delay-ms})
        (log/info "Retry dropped (superseded by a newer pending job, or stale execution)"
                  {:id job-id :attempt current-attempt}))
      n)))

(defn recover-stuck-jobs!
  "Recover jobs stuck in processing state (e.g. after a worker crash).
  Exhausted jobs move to the dead letter queue; jobs with attempts left are
  re-enqueued through the dedup gate, so a stuck job superseded by a newer
  pending job is dropped instead of blocking the batch.
  Returns {:recovered n :dead-lettered n}."
  [db timeout-minutes]
  (let [dead (jobs-db/dead-letter-stuck-jobs! db {:timeout_minutes timeout-minutes})
        recovered (jobs-db/recover-stuck-jobs! db {:timeout_minutes timeout-minutes})]
    (when (or (pos? recovered) (pos? dead))
      (log/warn "Recovered stuck jobs" {:recovered recovered
                                        :dead-lettered dead
                                        :timeout-minutes timeout-minutes}))
    {:recovered recovered :dead-lettered dead}))

(defn cleanup-jobs!
  "Apply retention: delete completed jobs and acknowledged dead letter
  entries older than the configured number of days."
  [db & [{:keys [completed-days dead-letter-days]
          :or {completed-days 30 dead-letter-days 90}}]]
  (let [completed (jobs-db/cleanup-completed-jobs! db {:days completed-days})
        dead (jobs-db/cleanup-dead-letter-jobs! db {:days dead-letter-days})]
    (log/info "Jobs retention cleanup" {:completed-deleted completed
                                        :dead-letter-deleted dead})
    {:completed-deleted completed :dead-letter-deleted dead}))

;; Monitoring

(defn get-queue-stats
  "Get current queue statistics for monitoring."
  [db]
  (->> (jobs-db/get-job-stats db)
       (group-by :status)
       (map (fn [[status entries]]
              [(keyword status) (first entries)]))
       (into {})))

(defn get-queue-health
  "Get current queue health metrics."
  [db]
  (-> (jobs-db/get-queue-health db)
      (update :oldest_pending_minutes #(when % (Math/round (double %))))
      (update :longest_processing_minutes #(when % (Math/round (double %))))))

(defn get-performance-metrics
  "Get performance metrics by job type within timeframe."
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
         ;; ISO instant so the browser parses the hour unambiguously
         (map #(update % :hour (fn [^java.sql.Timestamp ts] (str (.toInstant ts))))))))

(defn get-admin-metrics
  "Get comprehensive admin metrics for the monitoring dashboard."
  [db opts]
  {:current-stats (get-queue-stats db)
   :health (get-queue-health db)
   :performance-metrics (vec (get-performance-metrics db opts))
   :hourly-throughput (vec (get-hourly-throughput db opts))
   :fast-job-types (vec (sort registry/fast-job-types))
   :slow-job-types (vec (sort registry/slow-job-types))
   :generated-at (str (java.time.Instant/now))})

(defn search-jobs
  "Search jobs by status for the admin UI.
  opts: {:statuses [\"pending\" ...] :limit n}"
  [db {:keys [statuses limit] :or {limit 100}}]
  (mapv ->kebab-case-keywords
        (jobs-db/search-jobs db {:statuses (into-array String statuses)
                                 :limit limit})))

;; Dead letter queue management

(defn- enrich-dead-letter-entry
  "Add :error-class and :superseded-by to a kebab-cased dead letter row."
  [{:keys [superseded-by-job-id superseded-by-completed-at] :as entry}]
  (-> entry
      (dissoc :superseded-by-job-id :superseded-by-completed-at)
      (assoc :error-class (triage/classify-error (:error-message entry)))
      (cond-> superseded-by-job-id
        (assoc :superseded-by {:job-id superseded-by-job-id
                               :completed-at superseded-by-completed-at}))))

(defn get-dead-letter-jobs
  "Get dead letter jobs with optional acknowledgment filter, enriched
  with :error-class and :superseded-by for triage.
  opts: {:acknowledged true/false/nil} - nil returns all"
  [db {:keys [acknowledged] :as _opts}]
  (->> (jobs-db/get-dead-letter-jobs db {:acknowledged acknowledged})
       (map ->kebab-case-keywords)
       (mapv enrich-dead-letter-entry)))

(defn reprocess-dead-letter-job!
  "Requeue a dead letter job and mark it acknowledged.
  Returns the newly created job or throws on error."
  [db dead-letter-id user-email & [{:keys [max-attempts] :or {max-attempts 3}}]]
  (let [dlj (jobs-db/get-dead-letter-by-id db {:id dead-letter-id})]
    (when-not dlj
      (throw (ex-info "Dead letter job not found" {:id dead-letter-id})))
    ;; Guard against requeuing job types that are no longer registered
    (let [job-type (get-in dlj [:original_job :type])]
      (when-not (contains? registry/job-types job-type)
        (throw (ex-info "Cannot reprocess: unknown job type"
                        {:id dead-letter-id :job-type job-type}))))
    (let [new-job (jobs-db/requeue-dead-letter-job! db
                                                    {:id dead-letter-id
                                                     :max_attempts max-attempts
                                                     :reprocessed_by user-email})]
      (log/info "Reprocessed dead letter job"
                {:dead-letter-id dead-letter-id
                 :new-job-id (:id new-job)
                 :user user-email})
      (->kebab-case-keywords new-job))))

(defn reprocess-dead-letter-jobs!
  "Bulk reprocess dead letter jobs.
  Returns map with :succeeded and :failed entries."
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
  "Acknowledge dead letter jobs without reprocessing them, in a single
  statement - the admin UI acknowledges whole triage groups at once.
  Already-acknowledged and nonexistent ids are skipped.
  Returns map with :acknowledged count of newly acknowledged jobs."
  [db dead-letter-ids user-email]
  {:acknowledged (if (seq dead-letter-ids)
                   (jobs-db/acknowledge-dead-letter-jobs!
                    db
                    {:ids (long-array dead-letter-ids)
                     :acknowledged_by user-email})
                   0)})
