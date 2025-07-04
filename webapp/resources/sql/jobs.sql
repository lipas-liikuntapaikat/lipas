-- :name enqueue-job! :<! :1
-- :doc Add a new job to the queue
INSERT INTO jobs (type, payload, priority, run_at, max_attempts)
VALUES (:type, :payload::jsonb, :priority, :run_at, :max_attempts)
RETURNING id;

-- :name fetch-next-jobs :? :*
-- :doc Fetch the next batch of jobs to process, locking them atomically
UPDATE jobs
SET status = 'processing',
    started_at = now(),
    attempts = attempts + 1
WHERE id IN (
    SELECT id FROM jobs
    WHERE status IN ('pending', 'failed')
      AND run_at <= now()
      AND (:job_types::text[] IS NULL OR type = ANY(:job_types::text[]))
    ORDER BY priority DESC, run_at ASC
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
)
RETURNING id, type, payload, priority, attempts, max_attempts, created_at, correlation_id;

-- :name mark-job-completed! :! :n
-- :doc Mark a job as completed
UPDATE jobs
SET status = 'completed',
    completed_at = now(),
    error_message = NULL
WHERE id = :id;

-- :name mark-job-failed! :! :n
-- :doc Mark a job as failed (for retry)
UPDATE jobs
SET status = 'failed',
    error_message = :error_message,
    last_error = :error_message,
    last_error_at = now()
WHERE id = :id;

-- :name get-job-stats :? :*
-- :doc Get queue statistics
SELECT
    status,
    count(*) as count,
    min(created_at) as oldest_created_at,
    extract(epoch from (now() - min(created_at)))/60 as oldest_minutes
FROM jobs
GROUP BY status
UNION ALL
SELECT
    'total' as status,
    count(*) as count,
    min(created_at) as oldest_created_at,
    extract(epoch from (now() - min(created_at)))/60 as oldest_minutes
FROM jobs;

-- :name cleanup-old-jobs! :! :n
-- :doc Remove completed and dead jobs older than specified days
DELETE FROM jobs
WHERE status IN ('completed', 'dead')
  AND completed_at < (now() - (:days || ' days')::interval);

-- :name get-jobs-by-type :? :*
-- :doc Get jobs filtered by type and status
SELECT id, type, payload, status, priority, attempts, max_attempts,
       created_at, started_at, completed_at, error_message
FROM jobs
WHERE (:type IS NULL OR type = :type)
  AND (:status IS NULL OR status = :status)
ORDER BY created_at DESC
LIMIT :limit
OFFSET :offset;

-- :name reset-stuck-jobs! :! :n
-- :doc Reset jobs that have been processing for too long
UPDATE jobs
SET status = 'failed',
    error_message = 'Job stuck in processing state - reset by cleanup'
WHERE status = 'processing'
  AND started_at < (now() - (:timeout_minutes || ' minutes')::interval);

-- :name get-performance-metrics :? :*
-- :doc Get detailed performance metrics by job type within timeframe
SELECT 
    type,
    status,
    count(*) as job_count,
    round(avg(extract(epoch from (coalesce(completed_at, now()) - started_at)))) as avg_duration_seconds,
    round(percentile_cont(0.5) within group (order by extract(epoch from (coalesce(completed_at, now()) - started_at)))) as p50_duration_seconds,
    round(percentile_cont(0.95) within group (order by extract(epoch from (coalesce(completed_at, now()) - started_at)))) as p95_duration_seconds,
    round(avg(attempts)) as avg_attempts,
    min(created_at) as earliest_job,
    max(created_at) as latest_job
FROM jobs 
WHERE created_at >= :from_timestamp
  AND created_at <= :to_timestamp
  AND started_at IS NOT NULL
GROUP BY type, status
ORDER BY type, status;

-- :name get-hourly-throughput :? :*
-- :doc Get job throughput by hour within timeframe
SELECT 
    date_trunc('hour', created_at) as hour,
    type,
    status,
    count(*) as job_count
FROM jobs
WHERE created_at >= :from_timestamp
  AND created_at <= :to_timestamp
GROUP BY date_trunc('hour', created_at), type, status
ORDER BY hour DESC, type;

-- :name get-queue-health :? :1
-- :doc Get current queue health metrics
SELECT 
    count(*) FILTER (WHERE status = 'pending') as pending_count,
    count(*) FILTER (WHERE status = 'processing') as processing_count,
    count(*) FILTER (WHERE status = 'failed') as failed_count,
    count(*) FILTER (WHERE status = 'dead') as dead_count,
    extract(epoch from (now() - min(created_at))) / 60 as oldest_pending_minutes,
    extract(epoch from (now() - min(started_at))) / 60 as longest_processing_minutes
FROM jobs
WHERE status IN ('pending', 'processing', 'failed');

-- Circuit Breaker Queries

-- :name get-circuit-breaker :? :1
-- :doc Get circuit breaker state for a service
SELECT service_name, state, failure_count, success_count, 
       last_failure_at, opened_at, half_opened_at, updated_at
FROM circuit_breakers
WHERE service_name = :service_name;

-- :name get-circuit-breakers :? :*
-- :doc Get all circuit breaker states
SELECT service_name, state, failure_count, success_count, 
       last_failure_at, opened_at, half_opened_at, updated_at
FROM circuit_breakers
ORDER BY service_name;

-- :name update-circuit-breaker! :! :n
-- :doc Update or insert circuit breaker state  
-- For new breakers, use ensure-circuit-breaker! first
-- This preserves existing values when updating specific fields
UPDATE circuit_breakers
SET state = COALESCE(:state, state),
    failure_count = COALESCE(:failure_count, failure_count),
    success_count = COALESCE(:success_count, success_count),
    last_failure_at = COALESCE(:last_failure_at, last_failure_at),
    opened_at = COALESCE(:opened_at, opened_at),
    half_opened_at = COALESCE(:half_opened_at, half_opened_at),
    updated_at = now()
WHERE service_name = :service_name;

-- :name ensure-circuit-breaker! :! :n
-- :doc Ensure circuit breaker exists with default values
INSERT INTO circuit_breakers (service_name, state, failure_count, success_count)
VALUES (:service_name, 'closed', 0, 0)
ON CONFLICT (service_name) DO NOTHING;

-- :name increment-circuit-breaker-failure! :! :n
-- :doc Atomically increment failure count and potentially open circuit
-- Returns number of rows updated
UPDATE circuit_breakers
SET failure_count = failure_count + 1,
    last_failure_at = :last_failure_at,
    state = CASE 
      WHEN failure_count + 1 >= :failure_threshold THEN 'open'
      ELSE state
    END,
    opened_at = CASE
      WHEN failure_count + 1 >= :failure_threshold AND state != 'open' THEN :opened_at
      ELSE opened_at
    END,
    updated_at = now()
WHERE service_name = :service_name
  AND state = 'closed';

-- :name reset-circuit-breaker! :! :n
-- :doc Reset circuit breaker to closed state
UPDATE circuit_breakers
SET state = 'closed',
    failure_count = 0,
    success_count = 0,
    last_failure_at = NULL,
    opened_at = NULL,
    half_opened_at = NULL,
    updated_at = now()
WHERE service_name = :service_name;

-- Dead Letter Queue Queries

-- :name insert-dead-letter! :! :n
-- :doc Insert a job into the dead letter queue
INSERT INTO dead_letter_jobs (original_job, error_message, error_details, correlation_id)
VALUES (:original_job::jsonb, :error_message, :error_details::jsonb, :correlation_id);

-- :name get-dead-letters :? :*
-- :doc Get unacknowledged dead letter jobs
SELECT id, original_job, error_message, error_details, died_at
FROM dead_letter_jobs
WHERE acknowledged = false
ORDER BY died_at DESC
LIMIT :limit
OFFSET :offset;

-- :name acknowledge-dead-letter! :! :n
-- :doc Acknowledge a dead letter job
UPDATE dead_letter_jobs
SET acknowledged = true,
    acknowledged_by = :acknowledged_by,
    acknowledged_at = now()
WHERE id = :id;

-- Job Metrics Queries

-- :name record-job-metric! :! :n
-- :doc Record a job execution metric
INSERT INTO job_metrics (job_type, status, duration_ms, queue_time_ms, correlation_id)
VALUES (:job_type, :status, :duration_ms, :queue_time_ms, :correlation_id);

-- Enhanced Job Queries

-- :name update-job-retry! :! :n
-- :doc Update job for retry with exponential backoff
UPDATE jobs
SET status = 'pending',
    run_at = :run_at,
    error_message = :error_message,
    last_error = :error_message,
    last_error_at = now()
WHERE id = :id;

-- :name move-job-to-dead-letter! :! :n
-- :doc Move a permanently failed job to dead letter queue
WITH moved AS (
  UPDATE jobs
  SET status = 'dead',
      error_message = :error_message
  WHERE id = :id
  RETURNING *
)
INSERT INTO dead_letter_jobs (original_job, error_message)
SELECT row_to_json(moved), :error_message
FROM moved;

-- :name enqueue-job-with-correlation! :<! :1
-- :doc Add a new job with correlation ID and optional deduplication
/*~
(if (:dedup_key params)
  "WITH existing AS (
     SELECT id FROM jobs 
     WHERE type = :type 
       AND dedup_key = :dedup_key 
       AND status IN ('pending', 'processing')
     LIMIT 1
   )
   INSERT INTO jobs (type, payload, priority, run_at, max_attempts, 
                     correlation_id, parent_job_id, created_by, dedup_key)
   SELECT :type, :payload::jsonb, :priority, :run_at, :max_attempts,
          :correlation_id, :parent_job_id, :created_by, :dedup_key
   WHERE NOT EXISTS (SELECT 1 FROM existing)
   RETURNING id, correlation_id"
  
  "INSERT INTO jobs (type, payload, priority, run_at, max_attempts, 
                    correlation_id, parent_job_id, created_by, dedup_key)
   VALUES (:type, :payload::jsonb, :priority, :run_at, :max_attempts,
           :correlation_id, :parent_job_id, :created_by, :dedup_key)
   RETURNING id, correlation_id")
~*/

-- :name get-job-by-correlation :? :*
-- :doc Find jobs by correlation ID
SELECT id, type, payload, status, created_at, completed_at, correlation_id
FROM jobs
WHERE correlation_id = :correlation_id
ORDER BY created_at;

-- :name get-metrics-by-correlation :? :*
-- :doc Get job metrics by correlation ID
SELECT job_type, status, duration_ms, queue_time_ms, recorded_at
FROM job_metrics
WHERE correlation_id = :correlation_id
ORDER BY recorded_at;

-- :name get-correlation-trace :? :*
-- :doc Get complete trace of all jobs and metrics for a correlation ID
WITH job_data AS (
    SELECT 
        'job' as record_type,
        id::text as record_id,
        type as job_type,
        status,
        created_at as timestamp,
        attempts,
        error_message
    FROM jobs
    WHERE correlation_id = :correlation_id
),
metric_data AS (
    SELECT
        'metric' as record_type,
        id::text as record_id,
        job_type,
        status,
        recorded_at as timestamp,
        NULL::int as attempts,
        NULL::text as error_message
    FROM job_metrics
    WHERE correlation_id = :correlation_id
)
SELECT * FROM job_data
UNION ALL
SELECT * FROM metric_data
ORDER BY timestamp;

-- :name get-performance-metrics-by-correlation :? :*
-- :doc Get detailed performance metrics for a specific correlation ID
SELECT 
    type,
    status,
    count(*) as job_count,
    round(avg(extract(epoch from (coalesce(completed_at, now()) - started_at)))) as avg_duration_seconds,
    round(percentile_cont(0.5) within group (order by extract(epoch from (coalesce(completed_at, now()) - started_at)))) as p50_duration_seconds,
    round(percentile_cont(0.95) within group (order by extract(epoch from (coalesce(completed_at, now()) - started_at)))) as p95_duration_seconds,
    round(avg(attempts)) as avg_attempts,
    min(created_at) as earliest_job,
    max(created_at) as latest_job
FROM jobs 
WHERE correlation_id = :correlation_id
  AND started_at IS NOT NULL
GROUP BY type, status
ORDER BY type, status;

-- Monitoring Queries

-- :name get-queue-health-detailed :? :*
-- :doc Get detailed health metrics by job type
SELECT * FROM job_queue_health
ORDER BY type, status;

-- :name find-stuck-jobs :? :*
-- :doc Find jobs that have been processing for too long
SELECT id, type, status, started_at, attempts, correlation_id
FROM jobs
WHERE status = 'processing'
  AND started_at < NOW() - (:minutes * INTERVAL '1 minute')
ORDER BY started_at;

-- :name get-job-stats-by-type :? :*
-- :doc Get job statistics grouped by type
SELECT 
    type,
    COUNT(CASE WHEN status = 'failed' OR status = 'dead' THEN 1 END) as failed,
    COUNT(*) as total
FROM jobs
WHERE created_at > NOW() - INTERVAL '24 hours'
GROUP BY type;

-- :name query-jobs :? :*
-- :doc Query jobs with flexible filters
SELECT * FROM jobs
WHERE 1=1
  /*~ (when (:error_message params) */
  AND error_message = :error_message
  /*~ ) ~*/
  /*~ (when (:created_after params) */
  AND created_at > :created_after::timestamp
  /*~ ) ~*/
ORDER BY created_at DESC
LIMIT 100;
