-- Fails if a LOI already has two revisions at the same event_date.

CREATE OR REPLACE VIEW public.loi_current AS
SELECT
  a.created_at,
  a.event_date,
  a.id,
  a.status,
  a.document,
  a.author_id,
  a.loi_type
FROM loi a
JOIN (
  SELECT
    loi.id,
    max(loi.event_date) AS max_date
  FROM loi
  GROUP BY loi.id) b
ON a.id = b.id AND a.event_date = b.max_date;

--;;

COMMENT ON VIEW public.loi_current
IS 'Latest revisions of all Locations of Interest (loi)';

--;;

ALTER TABLE public.loi DROP CONSTRAINT loi_pkey;

--;;

ALTER TABLE public.loi ADD CONSTRAINT loi_pkey PRIMARY KEY (id, event_date);
