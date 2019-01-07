CREATE TABLE IF NOT EXISTS public.account (
  id                uuid NOT NULL DEFAULT uuid_generate_v4(),
  created_at        timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  email             text COLLATE pg_catalog."default" NOT NULL,
  username          text COLLATE pg_catalog."default" NOT NULL,
  password          text COLLATE pg_catalog."default" NOT NULL,
  permissions       jsonb,
  user_data         jsonb,
  history           jsonb,
  status            text COLLATE pg_catalog."default",
  reset_token       text COLLATE pg_catalog."default",
  reset_valid_until timestamp without time zone,

  CONSTRAINT account_pkey PRIMARY KEY (id),
  CONSTRAINT account_email_key UNIQUE (email),
  CONSTRAINT account_username_key UNIQUE (username)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.account
OWNER to lipas;

CREATE TABLE IF NOT EXISTS public.sports_site (
  id         uuid NOT NULL DEFAULT uuid_generate_v4(),
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  lipas_id   integer NOT NULL,
  status     text COLLATE pg_catalog."default" NOT NULL,
  document   jsonb NOT NULL,
  author_id  uuid NOT NULL,
  type_code  integer NOT NULL,
  city_code  text COLLATE pg_catalog."default" NOT NULL,

  CONSTRAINT "sports-site_pkey" PRIMARY KEY (id),
  CONSTRAINT author_id_account_fk FOREIGN KEY (author_id)
  REFERENCES public.account (id) MATCH SIMPLE
  ON UPDATE NO ACTION
  ON DELETE NO ACTION
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.sports_site
OWNER to lipas;

CREATE SEQUENCE public.lipas_id_seq;

ALTER SEQUENCE public.lipas_id_seq
OWNER TO lipas;

CREATE OR REPLACE VIEW public.sports_site_current AS
SELECT
  a.created_at,
  a.event_date,
  a.lipas_id,
  a.status,
  a.document,
  a.author_id,
  a.type_code,
  a.city_code
FROM sports_site a
JOIN (
  SELECT
    sports_site.lipas_id,
    max(sports_site.event_date) AS max_date
  FROM sports_site
  WHERE status = 'active'
  GROUP BY sports_site.lipas_id) b
ON a.lipas_id = b.lipas_id AND a.event_date = b.max_date;

ALTER TABLE public.sports_site_current
OWNER TO lipas;
COMMENT ON VIEW public.sports_site_current
IS 'Latest revisions of all sports sites';

CREATE OR REPLACE VIEW public.sports_site_by_year AS
SELECT
  a.id,
  a.created_at,
  a.event_date,
  a.lipas_id,
  a.status,
  a.document,
  a.author_id,
  a.type_code,
  a.city_code
FROM
  sports_site a,
  (SELECT
    sports_site.id,
    row_number() OVER
      (PARTITION BY sports_site.lipas_id,
        (date_trunc('year'::text, sports_site.event_date))
        ORDER BY sports_site.event_date DESC) AS row_number
   FROM sports_site
   WHERE sports_site.status != 'draft'::text) b
WHERE a.id = b.id AND b.row_number = 1;

ALTER TABLE public.sports_site_by_year
OWNER TO lipas;
COMMENT ON VIEW public.sports_site_by_year
IS 'Lists latest revision for each lipas_id for each year.';
