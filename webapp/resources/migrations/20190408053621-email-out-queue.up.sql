-- Table: public.email_out_queue

CREATE TABLE public.email_out_queue (
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  message    jsonb NOT NULL,
  id         uuid NOT NULL DEFAULT uuid_generate_v4(),
  CONSTRAINT email_out_queue_pkey PRIMARY KEY (id)
) WITH (
  OIDS = FALSE
)

TABLESPACE pg_default;

ALTER TABLE public.email_out_queue
OWNER to lipas;
