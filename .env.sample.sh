#!/bin/bash

# Environment variables

# Postgres
export DB_NAME=lipas
export DB_HOST=postgres
export DB_PORT=5432
export DB_SUPERUSER=postgres
export DB_SUPERUSER_PASSWORD=***FILL_THIS***
export DB_USER=lipas
export DB_PASSWORD=***FILL_THIS***

# Migratus
export DB_URL=postgres://$DB_USER:$DB_PASSWORD@postgres:$DB_PORT/$DB_NAME?stringtype=unspecified
