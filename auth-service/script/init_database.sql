-- allow low-level commands on a remote DB
CREATE EXTENSION IF NOT EXISTS dblink;

-- ensure required user has been created
DO
$body$
BEGIN
   IF NOT EXISTS (
      SELECT *
      FROM   pg_catalog.pg_user
      WHERE  usename = 'auth_service_user') THEN

      CREATE ROLE auth_service_user LOGIN PASSWORD 'password1';
   END IF;
END
$body$;

-- ensure required databases have been created
DO
$doDev$
BEGIN

IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'auth_service') THEN
   RAISE NOTICE 'Database auth_service already exists';
ELSE
   PERFORM dblink_exec('dbname=' || current_database()  -- current db
                     , 'CREATE DATABASE auth_service OWNER auth_service_user');
END IF;

END
$doDev$;


DO
$doTest$
BEGIN

IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'auth_service_test') THEN
   RAISE NOTICE 'Database auth_service_test already exists';
ELSE
   PERFORM dblink_exec('dbname=' || current_database()  -- current db
                     , 'CREATE DATABASE auth_service_test OWNER auth_service_user');
END IF;

END
$doTest$;

GRANT ALL PRIVILEGES ON DATABASE auth_service to auth_service_user;
GRANT ALL PRIVILEGES ON DATABASE auth_service_test to auth_service_user;

-- add case-insensitive option to both databases
\c auth_service;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c auth_service_test;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
