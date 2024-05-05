-- :name add-to-queue!
-- :command :insert
-- :result :raw
-- :doc Inserts batch_data to webhook_queue table
INSERT INTO public.webhook_queue (batch_data)
VALUES (:batch_data)

-- :name get-queue
-- :command :query
-- :result :many
-- :doc Returns all entries in webhook_queue table that are pending
SELECT * FROM public.webhook_queue
WHERE status = 'pending'

-- :name get-all
-- :command :query
-- :result :many
-- :doc Returns all entries in webhook_queue table
SELECT * FROM public.webhook_queue

-- :name update-status!
-- :command :execute
-- :result :affected
-- :doc Sets entry status in webhook_queue table by lipas_id
UPDATE public.webhook_queue
SET status = :status
WHERE id = :id ::uuid
