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
FROM sports_site
WHERE lipas_id = :lipas_id
