#!/bin/bash

set -e

### Backend ###

printf "\n *** Running backend migrations *** \n\n"
docker-compose run backend-migrate

printf "\n *** Running backend tests *** \n\n"
docker-compose run backend-test

### Frontend ###

printf "\n *** Compiling frontend *** \n\n"
docker-compose run frontend-build
