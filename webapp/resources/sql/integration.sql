-- :name insert-entry!
-- :command :insert
-- :result :raw
-- :doc Inserts entry to integration_log table
INSERT INTO public.integration_log (
  event_date,
  status,
  "name",
  document
)
VALUES (
  :event_date ::timestamptz,
  :status,
  :name,
  :document
);

-- :name get-last-timestamp-by-name-and-status
-- :command :query
-- :result :one
-- :doc Returns timestamp of last event by name and status
SELECT MAX(event_date) AS result
FROM public.integration_log
WHERE "name" = :name AND status = :status

-- :name add-to-out-queue!
-- :command :insert
-- :result :raw
-- :doc Inserts lipas_id to integration_out_queue table
INSERT INTO public.integration_out_queue (lipas_id)
VALUES (:lipas_id)
ON CONFLICT (lipas_id) DO UPDATE SET added_at = CURRENT_TIMESTAMP;

-- :name get-out-queue
-- :command :query
-- :result :many
-- :doc Returns all entries in integration_out_queue table
SELECT * FROM public.integration_out_queue

-- :name delete-from-out-queue!
-- :command :execute
-- :result :affected
-- :doc Deletes entry from integration_out_queue table by lipas_id
DELETE FROM public.integration_out_queue
WHERE lipas_id = :lipas_id
