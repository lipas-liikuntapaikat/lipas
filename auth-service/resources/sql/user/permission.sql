-- :name insert-permission!
-- :command :insert
-- :result :raw
-- :doc Inserts a single permission into the permission table
INSERT INTO permission (permission)
VALUES                 (:permission);

-- :name insert-permission-with-data!
-- :command :insert
-- :result :raw
-- :doc Inserts permission with data
INSERT INTO permission (permission, permission_data)
VALUES                 (:permission, :permission_data);
