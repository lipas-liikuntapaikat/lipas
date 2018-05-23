-- :name all-users
-- :command :query
-- :result :many
-- :doc Selects all users
SELECT id
       , email
       , username
       , password
       , user_data
       , permissions
       , refresh_token
FROM   account;

-- :name get-user-by-id
-- :command :query
-- :result :one
-- :doc Selects the user matching the id
SELECT id
       , email
       , username
       , password
       , user_data
       , permissions
       , refresh_token
FROM   account
WHERE  id = :id

-- :name get-user-by-username
-- :command :query
-- :result :one
-- :doc Selects the user matching the username
SELECT id
       , email
       , username
       , password
       , user_data
       , permissions
       , refresh_token
FROM   account
WHERE  username = :username

-- :name get-user-by-email
-- :command :query
-- :result :one
-- :doc Selects the user matching the email
SELECT id
       , email
       , username
       , password
       , user_data
       , permissions
       , refresh_token
FROM   account
WHERE  email = :email

-- :name get-user-by-refresh-token
-- :command :query
-- :result :one
-- :doc Selects the user matching the refresh token
SELECT id
       , email
       , username
       , password
       , user_data
       , permissions
       , refresh_token
FROM   account
WHERE  refresh_token = :refresh_token


-- :name insert-user!
-- :command :insert
-- :result :raw
-- :doc Inserts a single user into account table
INSERT INTO account (
       email
        , username
        , password
        , user_data
        , permissions)
VALUES (
       :email
       , :username
       , :password
       , :user_data
       , :permissions);

-- :name update-registered-user!
-- :command :execute
-- :result :affected
-- :doc Update a single user matching provided id
UPDATE account
SET    email = :email
       , username = :username
       , password = :password
       , user_data = :user_data
       , permissions = :permissions
       , refresh_token = :refresh_token
WHERE  id = :id;

-- :name update-user-password!
-- :command :execute
-- :result :affected
-- :doc Update the password for the user matching the given userid
UPDATE account
SET    password = :password
WHERE  id = :id;

-- :name update-user-refresh-token!
-- :command :execute
-- :result :affected
-- :doc Update the refresh token for the user matching the given userid
UPDATE account
SET    refresh_token = :refresh_token
WHERE  id = :id;

-- :name null-refresh-token!
-- :command :execute
-- :result :affected
-- :doc Set refresh token to null for row matching the given refresh token
UPDATE registered_user
SET    refresh_token = NULL
WHERE  refresh_token = :refresh_token;
