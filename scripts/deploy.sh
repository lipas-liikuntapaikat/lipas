#!/bin/bash

set -e

cd /var/lipas
source .env.sh

docker compose run --rm backend-build
docker compose run --rm backend-migrate
docker compose stop backend
docker compose up -d backend
docker compose run --rm frontend-npm-deps
docker compose run --rm frontend-npm-bundle
docker compose run --rm frontend-build
docker compose restart proxy
