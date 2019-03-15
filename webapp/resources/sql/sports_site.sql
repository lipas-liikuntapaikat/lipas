-- :name next-lipas-id
-- :command :query
-- :result :one
-- :doc Returns next value in lipas_id_seq sequence
SELECT nextval('lipas_id_seq');

-- :name insert-sports-site-rev!
-- :command :insert
-- :result :raw
-- :doc Inserts a sports-site revision into sports_site table
INSERT INTO public.sports_site (
  event_date,
  lipas_id,
  status,
  document,
  author_id,
  type_code,
  city_code
)
VALUES (
  :event_date ::timestamptz,
  :lipas_id,
  :status,
  :document,
  :author_id ::uuid,
  :type_code,
  :city_code
);

-- :name get
-- :command :query
-- :result :one
-- :doc Returns current revision of sports-site with given lipas-id
SELECT *
FROM sports_site_current
WHERE lipas_id = :lipas_id

-- :name get-by-type-code
-- :command :query
-- :result :many
-- :doc Returns all revisions of sports sites by type_code
SELECT *
FROM sports_site
WHERE type_code = :type_code

-- :name get-latest-by-type-code
-- :command :query
-- :result :many
-- :doc Returns latests revisions of all sports sites by type_code
SELECT *
FROM sports_site_current
WHERE type_code = :type_code

-- :name get-history
-- :command :query
-- :result :many
-- :doc Returns history for a single sports-site (lipas_id)
SELECT *
FROM sports_site_by_year
WHERE lipas_id = :lipas_id

-- :name get-yearly-by-type-code
-- :command :query
-- :result :many
-- :doc Lists latest revision for each sports-site for each year
SELECT *
FROM sports_site_by_year
WHERE type_code = :type_code

-- :name get-by-type-code-and-year
-- :command :query
-- :result :many
-- :doc Lists latest revision for each sports-site for given year
SELECT *
FROM sports_site_by_year
WHERE type_code = :type_code AND date_part('year', event_date) = :year

-- :name get-by-author-and-status
-- :command :query
-- :result :many
-- :doc Lists sports-sites by given author_id (user) and status
SELECT *
FROM sports_site
WHERE status = :status AND author_id = :author_id ::uuid

-- :name get-last-modified
-- :command :query
-- :result :many
-- :doc Lists latest timestamps for given lipas_ids
SELECT lipas_id, created_at, event_date
FROM sports_site_current
WHERE lipas_id IN (:v*:lipas_ids)

-- :name get-modified-since
-- :command :query
-- :result :many
-- :doc Lists latest revisions of sports-sites modified after given timestamp
SELECT *
FROM sports_site_current
WHERE event_date > :timestamp ::timestamptz

-- :name get-latest-by-ids
-- :command :query
-- :result :many
-- :doc Lists latest revisions of sports-sites by lipas-ids
SELECT *
FROM sports_site_current
WHERE lipas_id IN (:v*:lipas_ids)

-- :name invalidate-since!
-- :command :execute
-- :result :affected
-- :doc Changes status to 'incorrect-data' for all revs of :lipas-id after :event-date
UPDATE public.sports_site
SET document = jsonb_set(document, '{status}', '"incorrect-data"')
WHERE lipas_id = :lipas_id AND event_date > :event_date ::timestamptz
