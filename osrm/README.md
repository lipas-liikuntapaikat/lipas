# OSRM

## Update data

Run following commands in the directory of this README.

```shell
# Download latest dataset
wget http://download.geofabrik.de/europe/finland-latest.osm.pbf

# Car

docker run -t -v "${PWD}/car:/data" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/finland-latest.osm.pbf
docker run -t -v "${PWD}/car:/data" osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run -t -v "${PWD}/car:/data" osrm/osrm-backend osrm-customize /data/finland-latest.osrm

# Foot

docker run -t -v "${PWD}/foot:/data" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf" osrm/osrm-backend osrm-extract -p /opt/foot.lua /data/finland-latest.osm.pbf
docker run -t -v "${PWD}/foot:/data" osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run -t -v "${PWD}/foot:/data" osrm/osrm-backend osrm-customize /data/finland-latest.osrm

# Bicycle

docker run -t -v "${PWD}/bicycle:/data" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf" osrm/osrm-backend osrm-extract -p /opt/bicycle.lua /data/finland-latest.osm.pbf
docker run -t -v "${PWD}/bicycle:/data" osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run -t -v "${PWD}/bicycle:/data" osrm/osrm-backend osrm-customize /data/finland-latest.osrm
```
