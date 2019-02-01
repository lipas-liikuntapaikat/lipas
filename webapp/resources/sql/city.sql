-- :name insert!
-- :command :insert
-- :result :raw
-- :doc Inserts city into table city
INSERT INTO city (
  city_code,
  stats
)
VALUES (
  :city_code,
  :stats
) ON CONFLICT (city_code) DO
  UPDATE SET stats = :stats;

-- :name get-all
-- :command :query
-- :result :many
-- :doc Returns all cities
SELECT *
FROM city

-- :name get-by-city-codes
-- :command :query
-- :result :many
-- :doc Returns cities with given city-codes
SELECT *
FROM city
WHERE city_code IN (:v*:city_codes)
