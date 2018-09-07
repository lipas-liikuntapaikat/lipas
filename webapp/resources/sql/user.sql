-- :name all-users
-- :command :query
-- :result :many
-- :doc Selects all users
SELECT
  id,
  email,
  username,
  password,
  user_data,
  permissions
FROM
  account;

-- :name get-user-by-id
-- :command :query
-- :result :one
-- :doc Selects the user matching the id
SELECT
  id,
  email,
  username,
  password,
  user_data,
  permissions
FROM
  account
WHERE
  id = :id ::uuid;

-- :name get-user-by-username
-- :command :query
-- :result :one
-- :doc Selects the user matching the username
SELECT
  id,
  email,
  username,
  password,
  user_data,
  permissions
FROM
  account
WHERE  username = :username;

-- :name get-user-by-email
-- :command :query
-- :result :one
-- :doc Selects the user matching the email
SELECT
  id,
  email,
  username,
  password,
  user_data,
  permissions
FROM
  account
WHERE
  email = :email;

-- :name insert-user!
-- :command :insert
-- :result :raw
-- :doc Inserts a single user into account table
INSERT INTO account (
  email,
  username,
  password,
  user_data,
  permissions
)
VALUES (
  :email,
  :username,
  :password,
  :user_data,
  :permissions
);

-- :name update-user-permissions!
-- :command :execute
-- :result :affected
-- :doc Update user permissions and user-data with given id
UPDATE account
SET    permissions = :permissions
WHERE  id = :id ::uuid;

-- :name update-user-password!
-- :command :execute
-- :result :affected
-- :doc Update the password for the user with given id
UPDATE account
SET    password = :password
WHERE  id = :id ::uuid;
