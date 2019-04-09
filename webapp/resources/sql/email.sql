-- :name add-to-out-queue!
-- :command :insert
-- :result :raw
-- :doc Inserts lipas_id to integration_out_queue table
INSERT INTO public.email_out_queue (message)
VALUES (:message)

-- :name delete-from-out-queue!
-- :command :execute
-- :result :affected
-- :doc Deletes entry from email_out_queue table by id
DELETE FROM public.email_out_queue
WHERE id = :id

-- :name get-out-queue
-- :command :query
-- :result :many
-- :doc Returns all entries in email_out_queue table
SELECT * FROM public.email_out_queue
