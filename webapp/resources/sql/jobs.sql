-- Queries for the LIPAS job queue.
--
-- State model: pending -> processing -> completed
-- Retries are pending jobs with run_at in the future.
-- Permanently failed jobs live only in dead_letter_jobs.
--
-- Invariants enforced here rather than by convention:
--
-- * One door into 'pending': every transition into pending (enqueue, retry,
--   stuck recovery) goes through INSERT ... ON CONFLICT DO NOTHING against
--   the pending-only dedup index. A conflict means the work is superseded
--   by a newer pending job for the same entity and is dropped on purpose.
-- * Fenced finalization: statements that finalize an execution take the
--   claimed attempts value and match it together with status='processing',
--   so a stale executor (zombie thread, double finalization) updates zero
--   rows instead of clobbering a newer state. Passing the attempts param
--   activates the fence; test fixtures may omit it.
-- * Per-entity serial execution: fetch-next-jobs never claims a pending job
--   while a processing job with the same (type, dedup_key) exists, so a
--   successor always runs strictly after its predecessor finishes.

-- :name enqueue-job! :<! :1
-- :doc Add a job to the queue. With a dedup_key, inserting is a no-op when an equal pending job exists (returns no row).
INSERT INTO jobs (type, payload, priority, run_at, max_attempts, created_by, dedup_key)
VALUES (:type, :payload::jsonb, :priority, :run_at, :max_attempts, :created_by, :dedup_key)
--~ (when (:dedup_key params) "ON CONFLICT (type, dedup_key) WHERE dedup_key IS NOT NULL AND status = 'pending' DO NOTHING")
RETURNING id;

-- :name fetch-next-jobs :? :*
-- :doc Fetch the next batch of runnable jobs, locking them atomically. Skips jobs whose predecessor (same type + dedup_key) is still processing so per-entity execution is sequential.
UPDATE jobs
SET status = 'processing',
    started_at = now(),
    attempts = attempts + 1
WHERE id IN (
    SELECT j.id FROM jobs j
    WHERE j.status = 'pending'
      AND j.run_at <= now()
      AND (:job_types::text[] IS NULL OR j.type = ANY(:job_types::text[]))
      AND NOT EXISTS (
        SELECT 1 FROM jobs p
        WHERE p.status = 'processing'
          AND p.type = j.type
          AND p.dedup_key IS NOT NULL
          AND p.dedup_key = j.dedup_key)
    ORDER BY j.priority DESC, j.run_at ASC
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
)
RETURNING id, type, payload, priority, attempts, max_attempts, created_at;

-- :name mark-job-completed! :! :n
-- :doc Mark a job as completed. With :attempts, fenced to the claimed execution (no-op for stale executors).
UPDATE jobs
SET status = 'completed',
    completed_at = now(),
    error_message = NULL
WHERE id = :id
--~ (when (:attempts params) "AND status = 'processing' AND attempts = :attempts")
;

-- :name retry-job! :! :n
-- :doc Re-enqueue a failed job for retry via the dedup gate: when a newer pending job with the same (type, dedup_key) exists, the retry is dropped as superseded. With :attempts, fenced to the claimed execution. Returns 0 when dropped or fenced, 1 when the retry is scheduled.
WITH failed AS (
  DELETE FROM jobs
  WHERE id = :id
--~ (when (:attempts params) "AND status = 'processing' AND attempts = :attempts")
  RETURNING *
)
INSERT INTO jobs (id, type, payload, status, priority, run_at, attempts, max_attempts,
                  created_at, created_by, dedup_key, error_message, last_error, last_error_at)
SELECT id, type, payload, 'pending', priority, :run_at, attempts, max_attempts,
       created_at, created_by, dedup_key, :error_message, :error_message, now()
FROM failed
ON CONFLICT (type, dedup_key) WHERE dedup_key IS NOT NULL AND status = 'pending' DO NOTHING;

-- :name move-job-to-dead-letter! :! :n
-- :doc Move a permanently failed job to the dead letter queue. With :attempts, fenced to the claimed execution.
WITH moved AS (
  DELETE FROM jobs
  WHERE id = :id
--~ (when (:attempts params) "AND status = 'processing' AND attempts = :attempts")
  RETURNING *
)
INSERT INTO dead_letter_jobs (original_job, error_message)
SELECT row_to_json(moved), :error_message
FROM moved;

-- :name recover-stuck-jobs! :! :n
-- :doc Re-enqueue crashed processing jobs with attempts left. Goes through the dedup gate per row: a stuck job superseded by a newer pending job is dropped instead of aborting the whole batch.
WITH stuck AS (
  DELETE FROM jobs
  WHERE status = 'processing'
    AND started_at < (now() - (:timeout_minutes || ' minutes')::interval)
    AND attempts < max_attempts
  RETURNING *
)
INSERT INTO jobs (id, type, payload, status, priority, run_at, attempts, max_attempts,
                  created_at, created_by, dedup_key, error_message, last_error, last_error_at)
SELECT id, type, payload, 'pending', priority, now(), attempts, max_attempts,
       created_at, created_by, dedup_key, error_message,
       'Job stuck in processing state - recovered', now()
FROM stuck
ON CONFLICT (type, dedup_key) WHERE dedup_key IS NOT NULL AND status = 'pending' DO NOTHING;

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
FROM jobs
-- Every metric concerns pending/processing rows; completed history is noise
WHERE status IN ('pending', 'processing');

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
-- :doc Get job outcomes by hour: completed (first try), retried (completed after retries), dead_lettered
SELECT
    date_trunc('hour', completed_at) as hour,
    type,
    CASE WHEN attempts > 1 THEN 'retried' ELSE 'completed' END as status,
    count(*) as job_count
FROM jobs
WHERE status = 'completed'
  AND completed_at >= :from_timestamp
  AND completed_at <= :to_timestamp
GROUP BY 1, 2, 3
UNION ALL
SELECT
    date_trunc('hour', died_at) as hour,
    original_job->>'type' as type,
    'dead_lettered' as status,
    count(*) as job_count
FROM dead_letter_jobs
WHERE died_at >= :from_timestamp
  AND died_at <= :to_timestamp
GROUP BY 1, 2
ORDER BY hour DESC, type;

-- :name search-jobs :? :*
-- :doc Search jobs by status. Completed jobs come newest first, queued jobs in run order.
SELECT
  id,
  type,
  payload,
  status,
  attempts,
  max_attempts,
  priority,
  created_at,
  run_at,
  started_at,
  completed_at,
  last_error
FROM jobs
WHERE status = ANY(:statuses::text[])
ORDER BY
  completed_at DESC NULLS LAST,
  started_at DESC NULLS LAST,
  run_at ASC
LIMIT :limit;

-- Dead letter queue queries

-- :name get-dead-letter-jobs :? :*
-- :doc Get dead letter jobs with optional filter for acknowledgment status. Each entry carries the newest completed job of the same type + lipas-id that finished after the failure (superseded detection).
WITH newest_completed AS (
  SELECT DISTINCT ON (type, payload->>'lipas-id')
         type,
         payload->>'lipas-id' AS lipas_id,
         id,
         completed_at
  FROM jobs
  WHERE status = 'completed'
    AND payload->>'lipas-id' IS NOT NULL
  ORDER BY type, payload->>'lipas-id', completed_at DESC
)
SELECT
  dlj.id,
  dlj.original_job,
  dlj.error_message,
  dlj.died_at,
  dlj.acknowledged,
  dlj.acknowledged_by,
  dlj.acknowledged_at,
  newer.id AS superseded_by_job_id,
  newer.completed_at AS superseded_by_completed_at
FROM dead_letter_jobs dlj
LEFT JOIN newest_completed newer
  ON newer.type = dlj.original_job->>'type'
 AND newer.lipas_id = dlj.original_job->'payload'->>'lipas-id'
 AND newer.completed_at > dlj.died_at
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

-- :name acknowledge-dead-letter-jobs! :! :n
-- :doc Bulk acknowledge dead letter jobs in one statement. Skips already-acknowledged rows, so the count reflects newly acknowledged jobs only.
UPDATE dead_letter_jobs
SET acknowledged = true,
    acknowledged_by = :acknowledged_by,
    acknowledged_at = now()
WHERE id = ANY(:ids::bigint[])
  AND acknowledged = false;

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
