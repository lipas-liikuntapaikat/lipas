-- Audits saved after the up-migration exist only in this table; rolling
-- back loses them (ptv_service documents stopped carrying audits).
DROP VIEW IF EXISTS public.ptv_service_audit_current;

--;;

DROP TABLE IF EXISTS public.ptv_service_audit;
