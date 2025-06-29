-- Add correlation_id to dead letter queue for better traceability
ALTER TABLE public.dead_letter_jobs
  ADD COLUMN IF NOT EXISTS correlation_id uuid;

-- Add index for efficient correlation-based queries
CREATE INDEX IF NOT EXISTS idx_dead_letter_correlation ON public.dead_letter_jobs (correlation_id)
  WHERE correlation_id IS NOT NULL;

-- Update the original_job JSONB to ensure correlation_id is always included
-- This is handled by the row_to_json(moved) in the move-job-to-dead-letter! query
