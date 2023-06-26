-- :name add-to-queue!
-- :command :insert
-- :result :raw
-- :doc Inserts lipas_id to elevation_queue table
INSERT INTO public.elevation_queue (lipas_id)
VALUES (:lipas_id)
ON CONFLICT (lipas_id) DO UPDATE SET added_at = CURRENT_TIMESTAMP;

-- :name get-queue
-- :command :query
-- :result :many
-- :doc Returns all entries in elevation_queue table that are not in-progress
SELECT * FROM public.elevation_queue
WHERE status = 'pending'

-- :name get-all
-- :command :query
-- :result :many
-- :doc Returns all entries in elevation_queue table
SELECT * FROM public.elevation_queue

-- :name delete-from-queue!
-- :command :execute
-- :result :affected
-- :doc Deletes entry from elevation_queue table by lipas_id
DELETE FROM public.elevation_queue
WHERE lipas_id = :lipas_id

-- :name update-status!
-- :command :execute
-- :result :affected
-- :doc Sets entry status in elevation_queue table by lipas_id
UPDATE public.elevation_queue
SET status = :status
WHERE lipas_id = :lipas_id
