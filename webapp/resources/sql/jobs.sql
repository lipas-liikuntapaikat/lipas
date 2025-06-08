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
RETURNING id, type, payload, priority, attempts, max_attempts, created_at;

-- :name mark-job-completed! :! :n
-- :doc Mark a job as completed
UPDATE jobs
SET status = 'completed',
    completed_at = now(),
    error_message = NULL
WHERE id = :id;

-- :name mark-job-failed! :! :n
-- :doc Mark a job as failed
UPDATE jobs
SET status = CASE
    WHEN attempts >= max_attempts THEN 'dead'
    ELSE 'failed'
    END,
    error_message = :error_message,
    run_at = CASE
        WHEN attempts < max_attempts THEN now() + (attempts * interval '1 minute')
        ELSE run_at
    END
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
