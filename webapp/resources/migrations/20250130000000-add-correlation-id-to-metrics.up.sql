-- Add correlation_id to job_metrics table for better traceability
ALTER TABLE public.job_metrics
  ADD COLUMN IF NOT EXISTS correlation_id uuid;

-- Add index for efficient correlation-based queries
CREATE INDEX IF NOT EXISTS idx_job_metrics_correlation ON public.job_metrics (correlation_id)
  WHERE correlation_id IS NOT NULL;

-- Add composite index for correlation + time queries
CREATE INDEX IF NOT EXISTS idx_job_metrics_correlation_time ON public.job_metrics (correlation_id, recorded_at)
  WHERE correlation_id IS NOT NULL;
