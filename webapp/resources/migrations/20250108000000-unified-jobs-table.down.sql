DROP TRIGGER IF EXISTS update_jobs_updated_at ON public.jobs;
DROP FUNCTION IF EXISTS update_updated_at_column();
DROP INDEX IF EXISTS idx_jobs_type;
DROP INDEX IF EXISTS idx_jobs_processing;
DROP TABLE IF EXISTS public.jobs;
