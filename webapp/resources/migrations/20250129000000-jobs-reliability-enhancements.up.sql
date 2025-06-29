-- Add retry and tracing columns
ALTER TABLE public.jobs
  ADD COLUMN IF NOT EXISTS last_error text,
  ADD COLUMN IF NOT EXISTS last_error_at timestamp with time zone,
  ADD COLUMN IF NOT EXISTS correlation_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  ADD COLUMN IF NOT EXISTS parent_job_id bigint REFERENCES jobs(id),
  ADD COLUMN IF NOT EXISTS created_by text,
  ADD COLUMN IF NOT EXISTS dedup_key text;

-- Add missing status value
ALTER TABLE public.jobs
  DROP CONSTRAINT IF EXISTS jobs_status_check;

ALTER TABLE public.jobs
  ADD CONSTRAINT jobs_status_check
  CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'dead'));

-- Indexes for new functionality
CREATE INDEX IF NOT EXISTS idx_jobs_correlation ON public.jobs (correlation_id);
-- Unique constraint for deduplication - prevents duplicate jobs
CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_dedup_unique ON public.jobs (type, dedup_key)
  WHERE dedup_key IS NOT NULL AND status IN ('pending', 'processing');

-- Dead letter queue
CREATE TABLE IF NOT EXISTS public.dead_letter_jobs (
  id                bigserial PRIMARY KEY,
  original_job      jsonb NOT NULL,
  error_message     text NOT NULL,
  error_details     jsonb,
  correlation_id    uuid,
  died_at           timestamp with time zone NOT NULL DEFAULT now(),
  acknowledged      boolean DEFAULT false,
  acknowledged_by   text,
  acknowledged_at   timestamp with time zone
);

CREATE INDEX idx_dead_letter_unack ON public.dead_letter_jobs (died_at)
  WHERE acknowledged = false;

-- Circuit breaker state
CREATE TABLE IF NOT EXISTS public.circuit_breakers (
  service_name      text PRIMARY KEY,
  state             text NOT NULL DEFAULT 'closed',
  failure_count     integer NOT NULL DEFAULT 0,
  success_count     integer NOT NULL DEFAULT 0,
  last_failure_at   timestamp with time zone,
  opened_at         timestamp with time zone,
  half_opened_at    timestamp with time zone,
  updated_at        timestamp with time zone NOT NULL DEFAULT now(),

  CONSTRAINT circuit_state_check CHECK (state IN ('closed', 'open', 'half_open'))
);

-- Job metrics for monitoring
CREATE TABLE IF NOT EXISTS public.job_metrics (
  id                bigserial PRIMARY KEY,
  job_type          text NOT NULL,
  status            text NOT NULL,
  duration_ms       bigint,
  queue_time_ms     bigint,
  correlation_id    uuid,
  recorded_at       timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_metrics_type_time ON public.job_metrics (job_type, recorded_at);

-- Update trigger for circuit breakers
CREATE TRIGGER update_circuit_breakers_updated_at
  BEFORE UPDATE ON public.circuit_breakers
  FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Monitoring view
CREATE OR REPLACE VIEW job_queue_health AS
SELECT
  type,
  status,
  COUNT(*) as count,
  MIN(created_at) as oldest,
  MAX(attempts) as max_attempts,
  AVG(EXTRACT(EPOCH FROM (now() - created_at))) as avg_age_seconds
FROM jobs
WHERE status IN ('pending', 'processing', 'failed')
GROUP BY type, status;

-- Set ownership
ALTER TABLE public.dead_letter_jobs OWNER TO lipas;
ALTER TABLE public.circuit_breakers OWNER TO lipas;
ALTER TABLE public.job_metrics OWNER TO lipas;
