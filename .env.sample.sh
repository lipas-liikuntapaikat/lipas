#!/bin/bash

# Environment variables

# Backend
export AUTH_KEY=***FILL_THIS***
export ADMIN_PASSWORD=***FILL_THIS***

# Postgres
export DB_NAME=lipas
export DB_HOST=postgres
export DB_PORT=5432
export DB_SUPERUSER=postgres
export DB_SUPERUSER_PASSWORD=***FILL_THIS***
export DB_USER=lipas
export DB_PASSWORD=***FILL_THIS***

# Mapproxy
MML_USERNAME=***FILL_THIS***
MML_PASSWORD=***FILL_THIS***
export MML_AUTH=`echo -n $MML_USERNAME:$MML_PASSWORD | base64`
