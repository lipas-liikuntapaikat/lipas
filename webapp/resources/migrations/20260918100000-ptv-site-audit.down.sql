-- Audits saved after the up-migration exist only in this table; rolling
-- back loses them (the site documents stopped carrying audits).
DROP VIEW IF EXISTS public.ptv_site_audit_current;

--;;

DROP TABLE IF EXISTS public.ptv_site_audit;
