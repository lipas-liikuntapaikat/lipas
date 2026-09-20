-- DVV's katselmointi verdicts on a sports site's PTV texts used to live
-- inside the site document ([:ptv :audit]). Every audit save appended a
-- sports_site revision, which (a) moved the site's event_date although no
-- content changed — read by the PTV views as "out of sync with PTV" — and
-- (b) made the audit a client-round-tripped part of the document. Audits
-- are information *about* the document, so they get their own append-only
-- table, like ptv_service holds the Service audits. lipas_id is the lineage
-- key (no FK: sports_site is a revision table). The code migration
-- 20260918100100 copies the existing audits over and repairs the event
-- dates of the audit-only site revisions.
CREATE TABLE IF NOT EXISTS public.ptv_site_audit (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  lipas_id integer NOT NULL,
  -- the sports_site revision the verdicts were given on (provenance; the
  -- whose-move states compare the audited text itself, see
  -- lipas.data.ptv/audit-field-state). NULL only for legacy audits whose
  -- revision could not be resolved.
  site_revision_id uuid NULL,
  status text NOT NULL DEFAULT 'active',
  auditor_id uuid NULL,
  document jsonb NOT NULL,
  CONSTRAINT ptv_site_audit_pkey PRIMARY KEY (id),
  CONSTRAINT ptv_site_audit_site_revision_fk FOREIGN KEY (site_revision_id)
    REFERENCES public.sports_site (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION,
  CONSTRAINT ptv_site_audit_auditor_fk FOREIGN KEY (auditor_id)
    REFERENCES public.account (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

--;;

COMMENT ON TABLE public.ptv_site_audit
IS 'Append-only DVV katselmointi verdicts on a sports site''s PTV texts (summary/description). document = the audit map (per-field status/feedback/audited-content + timestamp/auditor-id).';

--;;

ALTER TABLE public.ptv_site_audit
OWNER to lipas;

--;;

CREATE INDEX IF NOT EXISTS ptv_site_audit_lipas_id_event_idx
ON public.ptv_site_audit (lipas_id, event_date DESC);

--;;

CREATE OR REPLACE VIEW public.ptv_site_audit_current AS
SELECT
  a.id,
  a.created_at,
  a.event_date,
  a.lipas_id,
  a.site_revision_id,
  a.status,
  a.auditor_id,
  a.document
FROM ptv_site_audit a
JOIN (
  SELECT
    id,
    row_number() OVER (PARTITION BY lipas_id ORDER BY event_date DESC, created_at DESC) AS rn
  FROM ptv_site_audit) b
ON a.id = b.id AND b.rn = 1;

--;;

ALTER TABLE public.ptv_site_audit_current
OWNER TO lipas;

--;;

COMMENT ON VIEW public.ptv_site_audit_current
IS 'Latest katselmointi per sports site (lipas_id), regardless of status';
