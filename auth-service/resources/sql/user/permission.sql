-- :name insert-permission!
-- :command :insert
-- :result :raw
-- :doc Inserts a single permission into the permission table
INSERT INTO permission (permission)
VALUES                 (:permission);
