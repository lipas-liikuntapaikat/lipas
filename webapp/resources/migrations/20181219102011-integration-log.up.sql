CREATE TABLE public.integration_log (
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  name       text COLLATE pg_catalog."default" NOT NULL,
  status     text COLLATE pg_catalog."default" NOT NULL,
  document   jsonb,

  CONSTRAINT integration_log_pkey PRIMARY KEY (created_at, name)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.integration_log
OWNER to lipas;
