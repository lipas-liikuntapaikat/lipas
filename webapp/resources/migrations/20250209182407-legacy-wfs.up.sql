-- SCHEMA: wfs
CREATE SCHEMA IF NOT EXISTS wfs
    AUTHORIZATION lipas;

--;;

-- Table: wfs.master
CREATE TABLE IF NOT EXISTS wfs.master
(
    id uuid NOT NULL DEFAULT uuid_generate_v4(),
    lipas_id integer NOT NULL,
    type_code integer NOT NULL,
    geom_type text COLLATE pg_catalog."default" NOT NULL,
    doc jsonb NOT NULL,
    the_geom geometry NOT NULL,
    status text COLLATE pg_catalog."default",
    CONSTRAINT master_pkey PRIMARY KEY (id)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE IF EXISTS wfs.master
    OWNER to lipas;

--;;
CREATE INDEX IF NOT EXISTS idx_master_type_code ON wfs.master(type_code);

--;;
CREATE INDEX IF NOT EXISTS idx_master_status ON wfs.master(status);

--;;
CREATE INDEX IF NOT EXISTS idx_master_type_code_status ON wfs.master(type_code, status);

--;;
CREATE INDEX IF NOT EXISTS idx_master_doc ON wfs.master USING GIN(doc);
