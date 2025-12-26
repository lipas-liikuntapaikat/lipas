#!/bin/bash

set -e

psql -p $DB_PORT -U $DB_SUPERUSER <<EOF
     DROP DATABASE IF EXISTS $DB_NAME;
     DROP USER IF EXISTS $DB_USER;
     CREATE USER $DB_USER WITH
            LOGIN
            REPLICATION
            SUPERUSER
            PASSWORD '$DB_PASSWORD';
     CREATE DATABASE $DB_NAME WITH OWNER=$DB_USER ENCODING='UTF8';
     GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO $DB_USER;
EOF

psql -p $DB_PORT -U $DB_SUPERUSER -d $DB_NAME <<EOF
     CREATE EXTENSION IF NOT EXISTS postgis;
     CREATE EXTENSION IF NOT EXISTS postgis_topology;
     CREATE EXTENSION IF NOT EXISTS citext;
     CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

     -- Shims for legacy PostGIS function names (with underscores).
     -- PostGIS 3.x renamed them without underscores (st_force_2d -> st_force2d).
     -- Using plpgsql with explicit search_path to avoid type resolution issues.
     CREATE OR REPLACE FUNCTION public.st_force_2d(geom public.geometry)
     RETURNS public.geometry AS \$\$
     BEGIN
       RETURN public.st_force2d(geom);
     END;
     \$\$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
     SET search_path = public;

     CREATE OR REPLACE FUNCTION public.st_force_3d(geom public.geometry)
     RETURNS public.geometry AS \$\$
     BEGIN
       RETURN public.st_force3d(geom);
     END;
     \$\$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
     SET search_path = public;
EOF
