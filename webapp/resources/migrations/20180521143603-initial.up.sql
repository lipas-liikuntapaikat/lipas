CREATE TABLE IF NOT EXISTS account (
id              UUID PRIMARY KEY DEFAULT UUID_GENERATE_V4()
, created_on    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
, email         TEXT               NOT NULL UNIQUE
, username      TEXT               NOT NULL UNIQUE
, password      TEXT               NOT NULL
, permissions   TEXT
, user_data     TEXT
, refresh_token TEXT
);
