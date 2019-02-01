CREATE TABLE public.city  (
  city_code integer NOT NULL,
  stats     jsonb,
CONSTRAINT city_pkey PRIMARY KEY (city_code)
) WITH (
  OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.city
OWNER to lipas;
