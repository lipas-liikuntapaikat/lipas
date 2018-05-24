#!/bin/bash

set -e

psql -h $DB_HOST -p $DB_PORT -U $DB_SUPERUSER <<EOF
     DROP DATABASE IF EXISTS $DB_NAME;
     DROP USER IF EXISTS $DB_USER;
     CREATE USER $DB_USER WITH
            LOGIN
            REPLICATION
            SUPERUSER
            PASSWORD '$DB_PASSWORD';
     CREATE DATABASE $DB_NAME WITH OWNER=$DB_USER ENCODING='utf-8';
     GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO $DB_USER;
EOF

psql -h $DB_HOST -p $DB_PORT -U $DB_SUPERUSER -d $DB_NAME <<EOF
     CREATE EXTENSION IF NOT EXISTS postgis;
     CREATE EXTENSION IF NOT EXISTS postgis_topology;
     CREATE EXTENSION IF NOT EXISTS citext;
     CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EOF
