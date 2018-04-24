CREATE TABLE IF NOT EXISTS permission (
    id           SERIAL PRIMARY KEY
    , permission TEXT UNIQUE NOT NULL
);

--;;
CREATE TABLE IF NOT EXISTS registered_user (
   id              UUID PRIMARY KEY DEFAULT UUID_GENERATE_V4()
   , created_on    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   , email         CITEXT             NOT NULL UNIQUE
   , username      CITEXT             NOT NULL UNIQUE
   , password      TEXT               NOT NULL
   , refresh_token TEXT
);
--;;
CREATE TABLE IF NOT EXISTS user_permission (
    id           SERIAL  PRIMARY KEY
    , user_id    UUID    REFERENCES registered_user (id)    ON DELETE CASCADE
    , permission TEXT    REFERENCES permission (permission) ON DELETE CASCADE
);
--;;
CREATE TABLE IF NOT EXISTS password_reset_key (
  id              SERIAL    PRIMARY KEY NOT NULL
  , reset_key     TEXT                  NOT NULL UNIQUE
  , already_used  BOOLEAN               NOT NULL DEFAULT FALSE
  , user_id       UUID      REFERENCES registered_user (id) ON DELETE CASCADE
  , valid_until   TIMESTAMP WITH TIME ZONE DEFAULT CLOCK_TIMESTAMP() + INTERVAL '24 hours'
);
