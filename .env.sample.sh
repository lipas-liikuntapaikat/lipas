#!/bin/bash

# Environment variables

# Postgres
export DB_NAME=lipas
export DB_HOST=localhost
export DB_PORT=5432
export DB_SUPERUSER=postgres
export DB_SUPERUSER_PASSWORD=***FILL_THIS***
export DB_USER=lipas
export DB_PASSWORD=***FILL_THIS***

# auth-service
export AUTH_DB_NAME=auth_service
export AUTH_DB_USER=auth_service_user
export AUTH_DB_PASSWORD=***FILL_THIS***
export AUTH_DB_URL=postgres://$AUTH_DB_USER:$AUTH_DB_PASSWORD@postgres:5432/$AUTH_DB_NAME?stringtype=unspecified
export AUTH_EMAIL_USER=***FILL_THIS***
export AUTH_EMAIL_PASSWORD=***FILL_THIS***
export AUTH_TOKEN_PRIVATE_KEY=***FILL_THIS***

# auth-service tests
export AUTH_TEST_DB_NAME=auth_service_test
export AUTH_TEST_DB_URL=postgres://$AUTH_DB_USER:$AUTH_DB_PASSWORD@postgres:5432/$AUTH_TEST_DB_NAME?stringtype=unspecified
