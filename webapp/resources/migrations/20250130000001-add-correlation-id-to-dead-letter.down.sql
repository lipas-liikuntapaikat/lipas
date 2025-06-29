-- Remove correlation_id from dead letter queue
DROP INDEX IF EXISTS idx_dead_letter_correlation;

ALTER TABLE public.dead_letter_jobs
  DROP COLUMN IF EXISTS correlation_id;
