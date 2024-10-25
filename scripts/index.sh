#!/bin/bash

set -e

cd /var/lipas
source .env.sh
docker compose run backend-index-search
docker compose run backend-index-search --analytics

echo TADA!
