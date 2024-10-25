#!/bin/bash

set -e

cd /var/lipas
source .env.sh
cat /var/lipas/scripts/refresh-mat-views.sql | /usr/bin/docker compose exec -T postgres psql -U postgres -d "lipas-legacy"
