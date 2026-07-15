-- org_id deliberately has NO foreign key: feat/org-management restructures
-- the org table into an append-only revision table (id becomes a revision id,
-- the stable identity moves to an org_id column exposed via the org_current
-- view) and views can't be FK targets. Org references are stored as plain
-- logical org uuids, following the same convention org-management uses for
-- sports-site owner-org-id.
CREATE TABLE IF NOT EXISTS public.ptv_service (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  org_id uuid NOT NULL,
  source_id text NOT NULL,
  service_id uuid NULL,
  status text NOT NULL DEFAULT 'active',
  author_id uuid NULL,
  document jsonb NOT NULL,
  CONSTRAINT ptv_service_pkey PRIMARY KEY (id),
  CONSTRAINT ptv_service_author_fk FOREIGN KEY (author_id)
    REFERENCES public.account (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

--;;

COMMENT ON TABLE public.ptv_service
IS 'Append-only revisions of LIPAS-managed PTV Service content (names, descriptions, audits). PTV remains authoritative for all non-LIPAS-managed fields.';

--;;

ALTER TABLE public.ptv_service
OWNER to lipas;

--;;

CREATE INDEX IF NOT EXISTS ptv_service_org_source_event_idx
ON public.ptv_service (org_id, source_id, event_date DESC);

--;;

CREATE INDEX IF NOT EXISTS ptv_service_service_id_idx
ON public.ptv_service (service_id);

--;;

CREATE OR REPLACE VIEW public.ptv_service_current AS
SELECT
  a.id,
  a.created_at,
  a.event_date,
  a.org_id,
  a.source_id,
  a.service_id,
  a.status,
  a.author_id,
  a.document
FROM ptv_service a
JOIN (
  SELECT
    ptv_service.org_id,
    ptv_service.source_id,
    max(ptv_service.event_date) AS max_date
  FROM ptv_service
  GROUP BY ptv_service.org_id, ptv_service.source_id) b
ON a.org_id = b.org_id AND a.source_id = b.source_id AND a.event_date = b.max_date;

--;;

ALTER TABLE public.ptv_service_current
OWNER TO lipas;

--;;

COMMENT ON VIEW public.ptv_service_current
IS 'Latest revision per (org_id, source_id) of LIPAS-managed PTV Services, regardless of status';
