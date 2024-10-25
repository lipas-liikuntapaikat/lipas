#!/bin/bash

set -e

# Meant to be run from cron

cd /var/lipas
source .env.sh
docker compose exec mapproxy /lipas/purge.sh
