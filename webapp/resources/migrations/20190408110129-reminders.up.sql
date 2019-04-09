-- Table: public.reminder

CREATE TABLE public.reminder (
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  event_date timestamp with time zone NOT NULL,
  id         uuid NOT NULL DEFAULT uuid_generate_v4(),
  account_id uuid NOT NULL,
  body       jsonb NOT NULL,
  status     text COLLATE pg_catalog."default" NOT NULL,
  CONSTRAINT reminder_pkey PRIMARY KEY (id),
  CONSTRAINT account_id_fk FOREIGN KEY (account_id)
  REFERENCES public.account (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
) WITH (
  OIDS = FALSE
)

TABLESPACE pg_default;

ALTER TABLE public.reminder
OWNER to lipas;
