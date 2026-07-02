-- Jobs system simplification.
--
-- New state model: pending -> processing -> completed.
-- Retries are pending jobs with a future run_at. Permanently failed jobs
-- live only in dead_letter_jobs. Reminder production and queue cleanup are
-- invoked directly by the scheduler and are no longer job types.

-- Scheduler-internal job types no longer go through the queue
DELETE FROM jobs WHERE type IN ('produce-reminders', 'cleanup-jobs');

--;;

-- Dead jobs live only in dead_letter_jobs from now on. Existing dead rows
-- were already copied to dead_letter_jobs when they died.
DELETE FROM jobs WHERE status = 'dead';

--;;

-- Retryable failures are represented as pending + run_at
UPDATE jobs SET status = 'pending' WHERE status = 'failed';

--;;

ALTER TABLE public.jobs DROP CONSTRAINT IF EXISTS jobs_status_check;

--;;

ALTER TABLE public.jobs
  ADD CONSTRAINT jobs_status_check
  CHECK (status IN ('pending', 'processing', 'completed'));

--;;

-- Never-used columns
ALTER TABLE public.jobs
  DROP COLUMN IF EXISTS parent_job_id,
  DROP COLUMN IF EXISTS correlation_id,
  DROP COLUMN IF EXISTS scheduled_at;

--;;

ALTER TABLE public.dead_letter_jobs
  DROP COLUMN IF EXISTS correlation_id,
  DROP COLUMN IF EXISTS error_details;

--;;

-- Deduplication applies to pending jobs only: a job already being processed
-- must not suppress a successor triggered by a newer edit
DROP INDEX IF EXISTS idx_jobs_dedup_unique;

--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_dedup_pending ON public.jobs (type, dedup_key)
  WHERE dedup_key IS NOT NULL AND status = 'pending';

--;;

DROP INDEX IF EXISTS idx_jobs_processing;

--;;

CREATE INDEX IF NOT EXISTS idx_jobs_pending ON public.jobs (status, run_at, priority)
  WHERE status = 'pending';

--;;

-- Unused monitoring view (only pending/processing/failed; superseded by
-- get-queue-health query)
DROP VIEW IF EXISTS job_queue_health;

--;;

-- Write-only metrics table: written on every job execution, read by nothing
DROP TABLE IF EXISTS public.job_metrics;

--;;

-- DB-backed circuit breaker replaced by an in-memory breaker
DROP TABLE IF EXISTS public.circuit_breakers;
