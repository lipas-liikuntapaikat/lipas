-- :name insert!
-- :command :returning-execute
-- :result :one
-- :doc Inserts entry into table reminder
INSERT INTO reminder (
  account_id,
  event_date,
  status,
  body
)
VALUES (
  :account_id ::uuid,
  :event_date ::timestamptz,
  :status,
  :body
) RETURNING *

-- :name update-status!
-- :command :execute
-- :result :affected
-- :doc Update reminder status
UPDATE reminder
SET    status = :status
WHERE  id = :id ::uuid;

-- :name get-overdue
-- :command :query
-- :result :many
-- :doc Returns all reminders that should be sent
SELECT *
FROM public.reminder
WHERE status = 'pending' AND event_date < NOW();

-- :name get-by-user-and-status
-- :command :query
-- :result :many
-- :doc Returns all reminders for user with given status
SELECT *
FROM public.reminder
WHERE status = :status AND account_id = :account_id ::uuid;
