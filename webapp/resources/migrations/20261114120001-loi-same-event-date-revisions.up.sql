-- Let a LOI have several revisions at the same event_date, like sports_site.
--
-- A backdated LOI closure appends a closed copy of each later revision at that
-- revision's own event_date (lipas.backend.db.db/upsert-loi!): a correction
-- of what was true at that date, recorded later. The primary key (id,
-- event_date) forbade that, so add created_at to it, and break event_date ties
-- in loi_current by created_at, newest first, as sports_site_current does.
-- Before, loi_current joined on max(event_date) and would have returned every
-- tied row.

ALTER TABLE public.loi DROP CONSTRAINT loi_pkey;

--;;

ALTER TABLE public.loi ADD CONSTRAINT loi_pkey PRIMARY KEY (id, event_date, created_at);

--;;

CREATE OR REPLACE VIEW public.loi_current AS
SELECT
  a.created_at,
  a.event_date,
  a.id,
  a.status,
  a.document,
  a.author_id,
  a.loi_type
FROM (
  SELECT DISTINCT ON (loi.id) loi.*
  FROM loi
  ORDER BY loi.id, loi.event_date DESC, loi.created_at DESC) a;

--;;

COMMENT ON VIEW public.loi_current
IS 'Latest revisions of all Locations of Interest (loi)';
