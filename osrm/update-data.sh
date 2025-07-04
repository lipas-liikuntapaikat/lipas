#!/bin/bash

set -e

source ../.env.sh

# Download latest dataset
wget http://download.geofabrik.de/europe/finland-latest.osm.pbf

# Car

docker run --rm -t -v "${PWD}/car:/data:z" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf:z" ghcr.io/project-osrm/osrm-backend osrm-extract -p /opt/car.lua /data/finland-latest.osm.pbf
docker run --rm -t -v "${PWD}/car:/data:z" ghcr.io/project-osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run --rm -t -v "${PWD}/car:/data:z" ghcr.io/project-osrm/osrm-backend osrm-customize /data/finland-latest.osrm

# Foot

docker run --rm -t -v "${PWD}/foot:/data:z" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf:z" ghcr.io/project-osrm/osrm-backend osrm-extract -p /opt/foot.lua /data/finland-latest.osm.pbf
docker run --rm -t -v "${PWD}/foot:/data:z" ghcr.io/project-osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run --rm -t -v "${PWD}/foot:/data:z" ghcr.io/project-osrm/osrm-backend osrm-customize /data/finland-latest.osrm

# Bicycle

docker run --rm -t -v "${PWD}/bicycle:/data:z" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf:z" ghcr.io/project-osrm/osrm-backend osrm-extract -p /opt/bicycle.lua /data/finland-latest.osm.pbf
docker run --rm -t -v "${PWD}/bicycle:/data:z" ghcr.io/project-osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run --rm -t -v "${PWD}/bicycle:/data:z" ghcr.io/project-osrm/osrm-backend osrm-customize /data/finland-latest.osrm
