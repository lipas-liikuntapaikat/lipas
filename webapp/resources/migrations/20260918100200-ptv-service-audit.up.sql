-- DVV's katselmointi verdicts on a PTV Service used to live inside the
-- ptv_service document ([:audit]): every audit save appended a ptv_service
-- revision and every content sync had to carry the audit forward. Audits
-- are information *about* the service, so they get their own append-only
-- table, mirroring ptv_site_audit for sports sites. Lineage is
-- (org_id, source_id) like ptv_service itself (no FK to org, see
-- 20260710090000). The code migration 20260918100300 copies the existing
-- audits over.
CREATE TABLE IF NOT EXISTS public.ptv_service_audit (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  org_id uuid NOT NULL,
  source_id text NOT NULL,
  service_id uuid NULL,
  -- the ptv_service revision the verdicts were given on (provenance; the
  -- whose-move states compare the audited text itself)
  service_revision_id uuid NULL,
  status text NOT NULL DEFAULT 'active',
  auditor_id uuid NULL,
  document jsonb NOT NULL,
  CONSTRAINT ptv_service_audit_pkey PRIMARY KEY (id),
  CONSTRAINT ptv_service_audit_service_revision_fk FOREIGN KEY (service_revision_id)
    REFERENCES public.ptv_service (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION,
  CONSTRAINT ptv_service_audit_auditor_fk FOREIGN KEY (auditor_id)
    REFERENCES public.account (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

--;;

COMMENT ON TABLE public.ptv_service_audit
IS 'Append-only DVV katselmointi verdicts on a LIPAS-managed PTV Service''s texts (summary/description/user-instruction). document = the audit map (per-field status/feedback/audited-content + timestamp/auditor-id).';

--;;

ALTER TABLE public.ptv_service_audit
OWNER to lipas;

--;;

CREATE INDEX IF NOT EXISTS ptv_service_audit_org_source_event_idx
ON public.ptv_service_audit (org_id, source_id, event_date DESC);

--;;

CREATE INDEX IF NOT EXISTS ptv_service_audit_service_id_idx
ON public.ptv_service_audit (service_id);

--;;

CREATE OR REPLACE VIEW public.ptv_service_audit_current AS
SELECT
  a.id,
  a.created_at,
  a.event_date,
  a.org_id,
  a.source_id,
  a.service_id,
  a.service_revision_id,
  a.status,
  a.auditor_id,
  a.document
FROM ptv_service_audit a
JOIN (
  SELECT
    id,
    row_number() OVER (PARTITION BY org_id, source_id ORDER BY event_date DESC, created_at DESC) AS rn
  FROM ptv_service_audit) b
ON a.id = b.id AND b.rn = 1;

--;;

ALTER TABLE public.ptv_service_audit_current
OWNER TO lipas;

--;;

COMMENT ON VIEW public.ptv_service_audit_current
IS 'Latest katselmointi per PTV Service lineage (org_id, source_id), regardless of status';
