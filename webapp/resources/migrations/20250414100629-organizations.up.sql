CREATE TABLE IF NOT EXISTS public.org (
  id                uuid NOT NULL DEFAULT uuid_generate_v4(),
  name              text COLLATE pg_catalog."default" NOT NULL,
  data              jsonb,
  ptv_data          jsonb,

  CONSTRAINT org_pkey PRIMARY KEY (id),
  CONSTRAINT org_mail_key UNIQUE (name)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;
