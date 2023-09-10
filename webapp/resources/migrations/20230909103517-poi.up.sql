CREATE TABLE IF NOT EXISTS public.loi (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  author_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'active',
  loi_type text NOT NULL,
  document jsonb NOT NULL,
  CONSTRAINT loi_pkey PRIMARY KEY (id, event_date),
  CONSTRAINT loi_author_id_account_fk FOREIGN KEY (author_id)
  REFERENCES public.account (id) MATCH SIMPLE
  ON UPDATE NO ACTION
  ON DELETE NO ACTION
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

COMMENT ON TABLE public.loi
IS 'Location of Interest, other than sports site';

ALTER TABLE public.loi
OWNER to lipas;

CREATE OR REPLACE VIEW public.loi_current AS
SELECT
  a.created_at,
  a.event_date,
  a.id,
  a.status,
  a.document,
  a.author_id,
  a.loi_type
FROM loi a
JOIN (
  SELECT
    loi.id,
    max(loi.event_date) AS max_date
  FROM loi
  WHERE status = 'active'
  GROUP BY loi.id) b
ON a.id = b.id AND a.event_date = b.max_date;

ALTER TABLE public.loi_current
OWNER TO lipas;
COMMENT ON VIEW public.loi_current
IS 'Latest revisions of all Locations of Interest (loi)';
