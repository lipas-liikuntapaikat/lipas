-- Restore the pre-simplification jobs schema shape.
-- Note: deleted rows (dead jobs, produce-reminders/cleanup-jobs history) and
-- dropped data (job_metrics, circuit_breakers, correlation ids) are not
-- recoverable.

ALTER TABLE public.jobs DROP CONSTRAINT IF EXISTS jobs_status_check;

--;;

ALTER TABLE public.jobs
  ADD CONSTRAINT jobs_status_check
  CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'dead'));

--;;

ALTER TABLE public.jobs
  ADD COLUMN IF NOT EXISTS scheduled_at timestamp with time zone NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS correlation_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  ADD COLUMN IF NOT EXISTS parent_job_id bigint REFERENCES jobs(id);

--;;

ALTER TABLE public.dead_letter_jobs
  ADD COLUMN IF NOT EXISTS correlation_id uuid,
  ADD COLUMN IF NOT EXISTS error_details jsonb;

--;;

CREATE INDEX IF NOT EXISTS idx_jobs_correlation ON public.jobs (correlation_id);

--;;

DROP INDEX IF EXISTS idx_jobs_dedup_pending;

--;;

-- The new model allows a pending and a processing job to share
-- (type, dedup_key) - the old unique index below does not. Keep only the
-- newest row's dedup key per group so the index can be created on live
-- data; the other rows stay runnable, just without dedup protection.
UPDATE jobs SET dedup_key = NULL
WHERE id IN (
  SELECT id FROM (
    SELECT id,
           row_number() OVER (PARTITION BY type, dedup_key
                              ORDER BY created_at DESC, id DESC) AS rn
    FROM jobs
    WHERE dedup_key IS NOT NULL
      AND status IN ('pending', 'processing')) ranked
  WHERE rn > 1);

--;;

CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_dedup_unique ON public.jobs (type, dedup_key)
  WHERE dedup_key IS NOT NULL AND status IN ('pending', 'processing');

--;;

DROP INDEX IF EXISTS idx_jobs_pending;

--;;

CREATE INDEX IF NOT EXISTS idx_jobs_processing ON public.jobs (status, run_at, priority)
  WHERE status IN ('pending', 'failed');

--;;

CREATE TABLE IF NOT EXISTS public.job_metrics (
  id                bigserial PRIMARY KEY,
  job_type          text NOT NULL,
  status            text NOT NULL,
  duration_ms       bigint,
  queue_time_ms     bigint,
  correlation_id    uuid,
  recorded_at       timestamp with time zone NOT NULL DEFAULT now()
);

--;;

CREATE INDEX IF NOT EXISTS idx_job_metrics_type_time ON public.job_metrics (job_type, recorded_at);

--;;

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

--;;

CREATE TRIGGER update_circuit_breakers_updated_at
  BEFORE UPDATE ON public.circuit_breakers
  FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

--;;

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

--;;

ALTER TABLE public.job_metrics OWNER TO lipas;

--;;

ALTER TABLE public.circuit_breakers OWNER TO lipas;
