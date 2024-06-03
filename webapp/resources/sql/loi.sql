-- :name insert-loi-rev!
-- :command :insert
-- :result :raw
-- :doc Inserts a loi revision into loi table
INSERT INTO public.loi (
  event_date,
  id,
  status,
  document,
  author_id,
  loi_type
)
VALUES (
  :event_date ::timestamptz,
  :id ::uuid,
  :status,
  :document,
  :author_id ::uuid,
  :loi_type
);

-- :name get-latest-by-loi-type
-- :command :query
-- :result :many
-- :doc Returns latest revisions of lois by loi_type
SELECT *
FROM loi_current
WHERE loi_type = :loi_type

-- :name get-latest-by-status
-- :command :query
-- :result :many
-- :doc Returns latest revisions of lois by status
SELECT *
FROM loi_current
WHERE status = :status

-- :name get-latest
-- :command :query
-- :result :many
-- :doc Returns latest revisions of all lois
SELECT *
FROM loi_current

-- :name get-latest-by-ids
-- :command :query
-- :result :many
-- :doc Lists latest revisions of lois by ids
SELECT *
FROM loi_current
WHERE id IN (:v*:ids)
