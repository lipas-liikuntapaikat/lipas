-- Revert to original view that only shows 'active' status LOIs

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
  WHERE status = 'active'
  GROUP BY loi.id) b
ON a.id = b.id AND a.event_date = b.max_date;

COMMENT ON VIEW public.loi_current
IS 'Latest revisions of all Locations of Interest (loi)';
