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
  lipas_id,
  status,
  document,
  author_id,
  type_code,
  city_code
)
VALUES (
  :lipas_id,
  :status,
  :document,
  :author_id,
  :type_code,
  :city_code
);
