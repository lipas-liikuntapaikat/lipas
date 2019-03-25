#!/bin/bash

set -e

source .env.sh

### Cert ###

printf "\n *** Generating self-signed SSL certificate *** \n\n"
openssl req -new -newkey rsa:2048 -days 365 -nodes -x509 \
        -subj "/C=US/ST=Denial/L=Springfield/O=Dis/CN=www.example.com" \
        -keyout certs/server.key -out certs/server.crt

### Backend ###

printf "\n *** Running backend migrations *** \n\n"
docker-compose run backend-migrate

printf "\n *** Packaging backend *** \n\n"
docker-compose run backend-build

printf "\n *** Creating htpasswd file for Kibana *** \n\n"
docker-compose build htpasswd
docker-compose run htpasswd admin $ADMIN_PASSWORD nginx/htpasswd

### Frontend ###

printf "\n *** Fetching npm dependencies *** \n\n"
docker-compose run frontend-npm-deps

printf "\n *** Bundling npm dependencies *** \n\n"
docker-compose run frontend-npm-bundle

### Start services ###

printf "\n *** Starting backend services *** \n\n"
docker-compose up -d proxy-dev

printf "\n *** Starting Figwheel *** \n\n"
lein figwheel
