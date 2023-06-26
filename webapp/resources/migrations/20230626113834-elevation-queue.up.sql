CREATE TABLE IF NOT EXISTS public.elevation_queue (
  added_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status text NOT NULL DEFAULT 'pending',
  lipas_id integer NOT NULL,

  CONSTRAINT elevation_queue_pkey PRIMARY KEY (lipas_id)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.elevation_queue
OWNER to lipas;
