CREATE OR REPLACE VIEW public.sports_site_by_year AS
SELECT
  a.id,
  a.created_at,
  a.event_date,
  a.lipas_id,
  a.status,
  a.document,
  a.author_id,
  a.type_code,
  a.city_code
FROM
  sports_site a,
  (SELECT
    sports_site.id,
    row_number() OVER
    (PARTITION BY sports_site.lipas_id,
      (date_trunc('year'::text, sports_site.event_date))
      ORDER BY sports_site.event_date DESC) AS row_number
    FROM sports_site
    WHERE sports_site.status != 'draft'::text) b
WHERE a.id = b.id AND b.row_number = 1;

ALTER TABLE public.sports_site_by_year
OWNER TO lipas;
COMMENT ON VIEW public.sports_site_by_year
IS 'Lists latest revision for each lipas_id for each year.';
