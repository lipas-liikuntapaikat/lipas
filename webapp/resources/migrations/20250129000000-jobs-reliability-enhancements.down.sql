-- Drop monitoring view
DROP VIEW IF EXISTS job_queue_health;

-- Drop triggers
DROP TRIGGER IF EXISTS update_circuit_breakers_updated_at ON public.circuit_breakers;

-- Drop indexes
DROP INDEX IF EXISTS idx_job_metrics_type_time;
DROP INDEX IF EXISTS idx_dead_letter_unack;
DROP INDEX IF EXISTS idx_jobs_dedup;
DROP INDEX IF EXISTS idx_jobs_correlation;

-- Drop tables
DROP TABLE IF EXISTS public.job_metrics;
DROP TABLE IF EXISTS public.circuit_breakers;
DROP TABLE IF EXISTS public.dead_letter_jobs;

-- Remove columns from jobs table
ALTER TABLE public.jobs
  DROP COLUMN IF EXISTS dedup_key,
  DROP COLUMN IF EXISTS created_by,
  DROP COLUMN IF EXISTS parent_job_id,
  DROP COLUMN IF EXISTS correlation_id,
  DROP COLUMN IF EXISTS last_error_at,
  DROP COLUMN IF EXISTS last_error;
