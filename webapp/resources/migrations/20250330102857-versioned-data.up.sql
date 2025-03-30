CREATE TABLE IF NOT EXISTS public.versioned_data
(
    id uuid NOT NULL DEFAULT uuid_generate_v4(),
    event_date timestamp with time zone NOT NULL DEFAULT NOW(),
    status text COLLATE pg_catalog."default" NOT NULL,
    type text COLLATE pg_catalog."default" NOT NULL,
    body jsonb NOT NULL,
    CONSTRAINT versioned_data_pkey PRIMARY KEY (id)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

--;;

ALTER TABLE IF EXISTS public.versioned_data
    OWNER to lipas;

--;;

CREATE INDEX idx_versioned_data_type_event_date
ON versioned_data(type, event_date DESC);
