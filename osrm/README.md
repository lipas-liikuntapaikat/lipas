# OSRM

## Update data

Run following commands in the directory of this README.

```shell
# Download latest dataset
wget http://download.geofabrik.de/europe/finland-latest.osm.pbf

# Car

docker run --rm -t -v "${PWD}/car:/data:z" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf:z" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/finland-latest.osm.pbf
docker run --rm -t -v "${PWD}/car:/data:z" osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run --rm -t -v "${PWD}/car:/data:z" osrm/osrm-backend osrm-customize /data/finland-latest.osrm

# Foot

docker run --rm -t -v "${PWD}/foot:/data:z" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf:z" osrm/osrm-backend osrm-extract -p /opt/foot.lua /data/finland-latest.osm.pbf
docker run --rm -t -v "${PWD}/foot:/data:z" osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run --rm -t -v "${PWD}/foot:/data:z" osrm/osrm-backend osrm-customize /data/finland-latest.osrm

# Bicycle

docker run --rm -t -v "${PWD}/bicycle:/data:z" -v "${PWD}/finland-latest.osm.pbf:/data/finland-latest.osm.pbf:z" osrm/osrm-backend osrm-extract -p /opt/bicycle.lua /data/finland-latest.osm.pbf
docker run --rm -t -v "${PWD}/bicycle:/data:z" osrm/osrm-backend osrm-partition /data/finland-latest.osrm
docker run --rm -t -v "${PWD}/bicycle:/data:z" osrm/osrm-backend osrm-customize /data/finland-latest.osrm
```
