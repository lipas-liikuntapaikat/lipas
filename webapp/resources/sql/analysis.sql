-- :name add-to-queue!
-- :command :insert
-- :result :raw
-- :doc Inserts lipas_id to analysis_queue table
INSERT INTO public.analysis_queue (lipas_id)
VALUES (:lipas_id)
ON CONFLICT (lipas_id) DO UPDATE SET added_at = CURRENT_TIMESTAMP;

-- :name get-queue
-- :command :query
-- :result :many
-- :doc Returns 10 entries in analysis_queue table that are pending
SELECT * FROM public.analysis_queue
WHERE status = 'pending'
LIMIT 10

-- :name get-all
-- :command :query
-- :result :many
-- :doc Returns all entries in analysis_queue table
SELECT * FROM public.analysis_queue

-- :name delete-from-queue!
-- :command :execute
-- :result :affected
-- :doc Deletes entry from analysis_queue table by lipas_id
DELETE FROM public.analysis_queue
WHERE lipas_id = :lipas_id

-- :name update-status!
-- :command :execute
-- :result :affected
-- :doc Sets entry status in analysis_queue table by lipas_id
UPDATE public.analysis_queue
SET status = :status
WHERE lipas_id = :lipas_id
