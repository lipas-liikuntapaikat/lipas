-- Caches of two external open datasets used by reverse geocoding:
-- Posti's PCF (postal codes) and BAF (street segments) files, and
-- Tilastokeskus' Paavo postal code area polygons. Nothing here is authored
-- in LIPAS, so the refresh jobs replace whole tables in one transaction and
-- postal_data_source records what run each table came from.
--
-- No CREATE EXTENSION postgis: the postgres image ships PostGIS in template1,
-- which is why 20250209182407-legacy-wfs can declare geometry columns without
-- one. Verified on the dev and test databases (PostGIS 3.5).

CREATE TABLE IF NOT EXISTS public.postal_code (
  code                 text NOT NULL,
  name_fi              text NOT NULL,
  name_sv              text NULL,
  type                 text NOT NULL,
  municipality_code    text NULL,
  municipality_name_fi text NULL,
  municipality_name_sv text NULL,
  region_code          text NULL,
  region_name_fi       text NULL,
  region_name_sv       text NULL,
  valid_from           date NULL,
  CONSTRAINT postal_code_pkey PRIMARY KEY (code)
);

--;;

COMMENT ON TABLE public.postal_code
IS 'Posti PCF: postal code -> postitoimipaikka, municipality and region. Refreshed by the fetch-postal-data job.';

--;;

COMMENT ON COLUMN public.postal_code.name_fi
IS 'Postitoimipaikka, e.g. ''TERVALAMPI''.';

--;;

ALTER TABLE public.postal_code
OWNER to lipas;

--;;

CREATE TABLE IF NOT EXISTS public.postal_street_segment (
  id                bigserial NOT NULL,
  street_key        text NOT NULL,
  street_key_sv     text NULL,
  name_fi           text NULL,
  name_sv           text NULL,
  municipality_code text NOT NULL,
  postal_code       text NOT NULL,
  side              text NULL,
  min_bound         jsonb NULL,
  max_bound         jsonb NULL,
  CONSTRAINT postal_street_segment_pkey PRIMARY KEY (id),
  CONSTRAINT postal_street_segment_side_check CHECK (side IN ('odd', 'even'))
);

--;;

COMMENT ON TABLE public.postal_street_segment
IS 'Posti BAF: one side of one street between two building numbers -> postal code. BAF rows with side code ''0'' carry no street address and are not stored here. Refreshed by the fetch-postal-data job.';

--;;

COMMENT ON COLUMN public.postal_street_segment.street_key
IS 'Normalized Finnish street name (lipas.backend.address.posti/name-key): lowercased, diacritics folded, non-alphanumerics collapsed to single spaces.';

--;;

COMMENT ON COLUMN public.postal_street_segment.min_bound
IS 'Smallest building number of the segment as {number, letter, number2, letter2}; matched in Clojure by lipas.backend.address.resolve.';

--;;

ALTER TABLE public.postal_street_segment
OWNER to lipas;

--;;

CREATE INDEX IF NOT EXISTS postal_street_segment_street_idx
ON public.postal_street_segment (street_key, municipality_code);

--;;

CREATE INDEX IF NOT EXISTS postal_street_segment_street_sv_idx
ON public.postal_street_segment (street_key_sv, municipality_code)
WHERE street_key_sv IS NOT NULL;

--;;

CREATE TABLE IF NOT EXISTS public.paavo_area (
  postal_code       text NOT NULL,
  name_fi           text NULL,
  name_sv           text NULL,
  municipality_code text NULL,
  year              integer NOT NULL,
  geom              geometry(Geometry, 4326) NOT NULL,
  CONSTRAINT paavo_area_pkey PRIMARY KEY (postal_code)
);

--;;

COMMENT ON TABLE public.paavo_area
IS 'Tilastokeskus Paavo postal code areas (layer postialue:pno_meri, i.e. extended over sea). Statistical, generalized boundaries. Refreshed by the fetch-paavo-areas job.';

--;;

ALTER TABLE public.paavo_area
OWNER to lipas;

--;;

CREATE INDEX IF NOT EXISTS paavo_area_geom_idx
ON public.paavo_area USING gist (geom);

--;;

CREATE TABLE IF NOT EXISTS public.postal_data_source (
  kind        text NOT NULL,
  run_date    date NOT NULL,
  imported_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT postal_data_source_pkey PRIMARY KEY (kind)
);

--;;

COMMENT ON TABLE public.postal_data_source
IS 'Which run of each external source (''pcf'', ''baf'', ''paavo'') the corresponding table currently holds. The refresh jobs no-op unless the published data is newer.';

--;;

ALTER TABLE public.postal_data_source
OWNER to lipas;
