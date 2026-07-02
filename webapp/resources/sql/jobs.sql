-- Queries for the LIPAS job queue.
--
-- State model: pending -> processing -> completed
-- Retries are pending jobs with run_at in the future.
-- Permanently failed jobs live only in dead_letter_jobs.

-- :name enqueue-job! :<! :1
-- :doc Add a job to the queue. With a dedup_key, inserting is a no-op when an equal pending job exists (returns no row).
INSERT INTO jobs (type, payload, priority, run_at, max_attempts, created_by, dedup_key)
VALUES (:type, :payload::jsonb, :priority, :run_at, :max_attempts, :created_by, :dedup_key)
--~ (when (:dedup_key params) "ON CONFLICT (type, dedup_key) WHERE dedup_key IS NOT NULL AND status = 'pending' DO NOTHING")
RETURNING id;

-- :name fetch-next-jobs :? :*
-- :doc Fetch the next batch of runnable jobs, locking them atomically
UPDATE jobs
SET status = 'processing',
    started_at = now(),
    attempts = attempts + 1
WHERE id IN (
    SELECT id FROM jobs
    WHERE status = 'pending'
      AND run_at <= now()
      AND (:job_types::text[] IS NULL OR type = ANY(:job_types::text[]))
    ORDER BY priority DESC, run_at ASC
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
)
RETURNING id, type, payload, priority, attempts, max_attempts, created_at;

-- :name mark-job-completed! :! :n
-- :doc Mark a job as completed
UPDATE jobs
SET status = 'completed',
    completed_at = now(),
    error_message = NULL
WHERE id = :id;

-- :name update-job-retry! :! :n
-- :doc Schedule a failed job for retry
UPDATE jobs
SET status = 'pending',
    run_at = :run_at,
    error_message = :error_message,
    last_error = :error_message,
    last_error_at = now()
WHERE id = :id;

-- :name move-job-to-dead-letter! :! :n
-- :doc Move a permanently failed job to the dead letter queue
WITH moved AS (
  DELETE FROM jobs
  WHERE id = :id
  RETURNING *
)
INSERT INTO dead_letter_jobs (original_job, error_message)
SELECT row_to_json(moved), :error_message
FROM moved;

-- :name recover-stuck-jobs! :! :n
-- :doc Return crashed processing jobs with attempts left back to pending
UPDATE jobs
SET status = 'pending',
    last_error = 'Job stuck in processing state - recovered',
    last_error_at = now()
WHERE status = 'processing'
  AND started_at < (now() - (:timeout_minutes || ' minutes')::interval)
  AND attempts < max_attempts;

-- :name dead-letter-stuck-jobs! :! :n
-- :doc Move crashed processing jobs with no attempts left to the dead letter queue
WITH moved AS (
  DELETE FROM jobs
  WHERE status = 'processing'
    AND started_at < (now() - (:timeout_minutes || ' minutes')::interval)
    AND attempts >= max_attempts
  RETURNING *
)
INSERT INTO dead_letter_jobs (original_job, error_message)
SELECT row_to_json(moved), 'Job stuck in processing state - max attempts exhausted'
FROM moved;

-- :name cleanup-completed-jobs! :! :n
-- :doc Remove completed jobs older than the given number of days
DELETE FROM jobs
WHERE status = 'completed'
  AND completed_at < (now() - (:days || ' days')::interval);

-- :name cleanup-dead-letter-jobs! :! :n
-- :doc Remove acknowledged dead letter jobs older than the given number of days
DELETE FROM dead_letter_jobs
WHERE acknowledged = true
  AND acknowledged_at < (now() - (:days || ' days')::interval);

-- Monitoring queries

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

-- :name get-queue-health :? :1
-- :doc Get current queue health metrics
SELECT
    count(*) FILTER (WHERE status = 'pending') as pending_count,
    count(*) FILTER (WHERE status = 'processing') as processing_count,
    count(*) FILTER (WHERE status = 'pending' AND attempts > 0) as retrying_count,
    (SELECT count(*) FROM dead_letter_jobs WHERE acknowledged = false) as dead_count,
    extract(epoch from (now() - min(created_at) FILTER (WHERE status = 'pending'))) / 60 as oldest_pending_minutes,
    extract(epoch from (now() - min(started_at) FILTER (WHERE status = 'processing'))) / 60 as longest_processing_minutes
FROM jobs;

-- :name get-performance-metrics :? :*
-- :doc Get performance metrics by job type within timeframe
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

-- Dead letter queue queries

-- :name get-dead-letter-jobs :? :*
-- :doc Get dead letter jobs with optional filter for acknowledgment status
SELECT
  id,
  original_job,
  error_message,
  died_at,
  acknowledged,
  acknowledged_by,
  acknowledged_at
FROM dead_letter_jobs
WHERE (:acknowledged::boolean IS NULL OR acknowledged = :acknowledged)
ORDER BY died_at DESC;

-- :name get-dead-letter-by-id :? :1
-- :doc Get a single dead letter job by ID
SELECT
  id,
  original_job,
  error_message,
  died_at,
  acknowledged,
  acknowledged_by,
  acknowledged_at
FROM dead_letter_jobs
WHERE id = :id;

-- :name acknowledge-dead-letter! :! :n
-- :doc Acknowledge a dead letter job
UPDATE dead_letter_jobs
SET acknowledged = true,
    acknowledged_by = :acknowledged_by,
    acknowledged_at = now()
WHERE id = :id;

-- :name requeue-dead-letter-job! :<! :1
-- :doc Requeue a dead letter job back to the main queue and mark it acknowledged
WITH dlj AS (
  SELECT * FROM dead_letter_jobs WHERE id = :id
),
new_job AS (
  INSERT INTO jobs (type, payload, priority, max_attempts, created_by)
  SELECT
    original_job->>'type',
    original_job->'payload',
    COALESCE((original_job->>'priority')::int, 100),
    :max_attempts,
    :reprocessed_by
  FROM dlj
  RETURNING *
),
updated AS (
  UPDATE dead_letter_jobs
  SET acknowledged = true,
      acknowledged_by = :reprocessed_by,
      acknowledged_at = now()
  WHERE id = :id
)
SELECT * FROM new_job;
