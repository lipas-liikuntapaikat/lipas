#!/bin/bash

set -e

cd /var/lipas
source .env.sh

# Force recreate of mapproxy image (kludge)
docker compose rm -f mapproxy

# Start backend services
docker compose up -d backend mapproxy logstash kibana legacy-api geoserver worker integrations

# Wait until critical services are ready to accept connections
timeout 120 bash -c 'until echo > /dev/tcp/localhost/8091; do sleep 0.5; done'

# Start frontend proxy
docker compose up -d proxy
