#!/bin/bash

set -e

# Meant to be run from cron. Rebuilds the legacy /rest/api Elasticsearch
# index (the legacy API is served by the main backend, but from its own
# index). Referenced by root's crontab on lipas-prod (00:30 nightly).

cd /var/lipas
source .env.sh
docker compose run --rm backend-index-search --legacy
