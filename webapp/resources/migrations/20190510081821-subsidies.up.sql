-- SEQUENCE: public.subsidy_id_seq

CREATE SEQUENCE public.subsidy_id_seq;

ALTER SEQUENCE public.subsidy_id_seq
OWNER TO lipas;

-- Table: public.subsidy

CREATE TABLE public.subsidy (
  data jsonb,
  id   integer NOT NULL DEFAULT nextval('subsidy_id_seq'::regclass),
  year integer NOT NULL,
CONSTRAINT subsidy_pkey PRIMARY KEY (id)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.subsidy
OWNER to lipas;
