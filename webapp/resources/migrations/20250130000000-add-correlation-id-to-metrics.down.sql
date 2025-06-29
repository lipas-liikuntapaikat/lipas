-- Remove correlation_id from job_metrics table
DROP INDEX IF EXISTS idx_job_metrics_correlation_time;
DROP INDEX IF EXISTS idx_job_metrics_correlation;

ALTER TABLE public.job_metrics
  DROP COLUMN IF EXISTS correlation_id;
