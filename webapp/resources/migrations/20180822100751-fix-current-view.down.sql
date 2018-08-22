CREATE OR REPLACE VIEW public.sports_site_current AS
SELECT a.created_at,
  a.event_date,
  a.lipas_id,
  a.status,
  a.document,
  a.author_id,
  a.type_code,
  a.city_code
FROM sports_site a
JOIN ( SELECT sports_site.lipas_id,
              max(sports_site.event_date) AS max_date
       FROM sports_site
       WHERE sports_site.status = 'active'::text
       GROUP BY sports_site.lipas_id) b
ON a.lipas_id = b.lipas_id AND a.event_date = b.max_date;

ALTER TABLE public.sports_site_current
OWNER TO lipas;
COMMENT ON VIEW public.sports_site_current
IS 'Latest revisions of all sports sites';
