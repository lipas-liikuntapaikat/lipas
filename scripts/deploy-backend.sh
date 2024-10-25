#!/bin/bash

set -e

cd /var/lipas
source .env.sh

docker compose run --rm backend-build
docker compose run --rm backend-migrate
docker compose stop backend
docker compose up -d backend
# /usr/local/bin/docker-compose restart proxy
