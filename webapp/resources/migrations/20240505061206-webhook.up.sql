CREATE TABLE IF NOT EXISTS public.webhook_queue (
  id         uuid NOT NULL DEFAULT uuid_generate_v4(),
  added_at   timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status     text NOT NULL DEFAULT 'pending',
  batch_data jsonb NOT NULL DEFAULT '{}',

  CONSTRAINT webhook_queue_pkey PRIMARY KEY (id)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.webhook_queue
OWNER to lipas;
