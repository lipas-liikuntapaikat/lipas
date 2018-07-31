#!/bin/bash

set -e

### Backend ###

printf "\n *** Running backend migrations *** \n\n"
docker-compose run backend-migrate

printf "\n *** Running backend tests *** \n\n"
docker-compose run backend-tests

printf "\n *** Packaging backend *** \n\n"
docker-compose run backend-build

### Frontend ###

printf "\n *** Compiling frontend *** \n\n"
docker-compose run frontend-build

### Integration tests ###

printf "\n *** Seeding test data *** \n\n"
docker-compose run backend-seed

printf "\n *** Running integration tests *** \n\n"
docker-compose run integration-tests
