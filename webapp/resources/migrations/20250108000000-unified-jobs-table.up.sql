CREATE TABLE IF NOT EXISTS public.jobs (
  id             bigserial PRIMARY KEY,
  type           text NOT NULL,
  payload        jsonb NOT NULL DEFAULT '{}',
  status         text NOT NULL DEFAULT 'pending',
  priority       integer NOT NULL DEFAULT 100,
  attempts       integer NOT NULL DEFAULT 0,
  max_attempts   integer NOT NULL DEFAULT 3,
  scheduled_at   timestamp with time zone NOT NULL DEFAULT now(),
  run_at         timestamp with time zone NOT NULL DEFAULT now(),
  started_at     timestamp with time zone,
  completed_at   timestamp with time zone,
  error_message  text,
  created_at     timestamp with time zone NOT NULL DEFAULT now(),
  updated_at     timestamp with time zone NOT NULL DEFAULT now(),

  CONSTRAINT jobs_status_check CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'dead')),
  CONSTRAINT jobs_priority_check CHECK (priority >= 0)
);

-- Critical indexes for performance
CREATE INDEX idx_jobs_processing ON public.jobs (status, run_at, priority)
  WHERE status IN ('pending', 'failed');

CREATE INDEX idx_jobs_type ON public.jobs (type);

-- Update trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_jobs_updated_at
  BEFORE UPDATE ON public.jobs
  FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

ALTER TABLE public.jobs OWNER TO lipas;
