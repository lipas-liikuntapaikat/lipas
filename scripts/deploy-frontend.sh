#!/bin/bash

set -e

/cd /var/lipas
source .env.sh

docker compose run --rm frontend-npm-deps
docker compose run --rm frontend-npm-bundle
docker compose run --rm frontend-build
