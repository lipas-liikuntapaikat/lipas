#!/bin/bash

set -e

NORMAL=$(tput sgr0)
LIME_YELLOW=$(tput setaf 190)

### Backend ###

printf "\n ${LIME_YELLOW}*** Running backend migrations ***${NORMAL} \n\n"
docker-compose run backend-migrate

printf "\n ${LIME_YELLOW}*** Running backend tests ***${NORMAL} \n\n"
docker-compose run backend-test

### Frontend ###

printf "\n ${LIME_YELLOW}*** Compiling frontend ***${NORMAL} \n\n"
docker-compose run frontend-build
