CREATE TABLE public.integration_out_queue (
  added_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  lipas_id integer NOT NULL,

  CONSTRAINT integration_out_queue_pkey PRIMARY KEY (lipas_id)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.integration_out_queue
OWNER to lipas;
