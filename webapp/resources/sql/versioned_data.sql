-- :name insert!
-- :command :returning-execute
-- :result :one
-- :doc Inserts entry into table versioned_data
INSERT INTO versioned_data (
  event_date,
  status,
  type,
  body
)
VALUES (
  now(),
  :status,
  :type,
  :body
) RETURNING *

-- :name get-latest-by-type-and-status
-- :command :query
-- :result :one
-- :doc Returns the latest entry by given type and status
SELECT *
FROM public.versioned_data
WHERE status = :status AND type = :type
ORDER BY event_date DESC
LIMIT 1
