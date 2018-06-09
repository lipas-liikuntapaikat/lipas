#!/bin/bash

set -e

### Backend ###

echo "\n *** Running backend migrations *** \n"
docker-compose run backend-migrate

echo "\n *** Running backend tests *** \n"
docker-compose run backend-test

### Frontend ###

echo "\n *** Compiling frontend *** \n"
docker-compose run frontend-build
