-- :name insert!
-- :command :insert
-- :result :raw
-- :doc Inserts subsidy into subsidy table
INSERT INTO subsidy (data, year)
VALUES (:data, :year)

-- :name get-all
-- :command :query
-- :result :many
-- :doc Returns all subsidies
SELECT *
FROM subsidy

-- :name get-by-years
-- :command :query
-- :result :many
-- :doc Returns cities with given years
SELECT *
FROM subsidy
WHERE year IN (:v*:years)
