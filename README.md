# LIPAS

[prod](https://www.lipas.fi/) |
 [dev](https://lipas-dev.cc.jyu.fi/) |
 [ci](https://travis-ci.com/lipas-liikuntapaikat/lipas) |
 [issues](https://github.com/lipas-liikuntapaikat/lipas/issues) |
 [dev-tasks](https://trello.com/b/q7dgXf28/lipas-20-dev) |
 [use-cases](https://trello.com/b/S8i6NexB/k%C3%A4ytt%C3%B6tapaukset) (finnish) |
 [ideas](https://trello.com/b/IEwJ5Nrq/lipas) (finnish)

LIPAS is a nationwide, public GIS service of Finnish sport
facilities. More information
[here](https://www.jyu.fi/fi/lipas-liikunnan-paikkatietojarjestelma).

## Tech stack

[Architecture diagram](https://drive.google.com/file/d/18JercdBIV_QO8HOXz4uBjAMPRvhy2CUW/view?usp=sharing)

### Webapp

Webapp is written in Clojure(script).

#### Backend

Webapp backend is a simple HTTP(s) service with endpoints for
different tasks. Some endpoints require authentication. Authentication
is implemented using HTTP-basic authentication and signed JWT-tokens.

#### Frontend

* [re-frame](https://github.com/Day8/re-frame) Single Page Application
* [material-ui](https://material-ui.com/) components

#### Postgis database

Spatial [PostGIS](https://postgis.net/) for master data storage.

### Nginx

Web facing [Nginx](https://www.nginx.com/) reverse proxy, ssl-termination.

### Mapproxy

[Mapproxy](https://mapproxy.org/) basemap proxy and cache.

### ELK-stack

ELK (ElasticSearch, Logstash, Kibana) is used to provide webapp search
funtionalities as well as data and monitoring information.

### Old Lipas

All data flows also to old Lipas which exposes the data through
Geoserver and legacy REST-Api.

#### REST-API

See Api-documentation [here](https://github.com/lipas-liikuntapaikat/lipas-api).

#### GeoServer

[Geoserver](http://geoserver.org/) publishing spatial data. See
available layers [here](http://lipas.cc.jyu.fi/geoserver).

## Dev-env setup

``` shell
# Create env-file from template
cp .env.sample.sh .env.sh

# Fill in secrets
$EDITOR .env.sh

# Run setup script
./setup-dev.sh
```

### Extra

Add following to your `hosts` file to use same hostnames in both
docker and host while developing.

``` shell
# Lipas dev
127.0.0.1       postgres
127.0.0.1       backend-dev
127.0.0.1       mapproxy
127.0.0.1       elasticsearch
127.0.0.1       kibana
127.0.0.1       logstash
127.0.0.1       proxy
```

## Production build

### Backend

`docker-compose run backend-build`

See [certs/README.md](certs/README.md).

### Frontend

```bash
docker-compose run frontend-npm-deps
docker-compose run frontend-build
```

### Apple Silicon considerations

Open Source Routing Machine currently has not an arm64 build. It is much faster than using the x86 image so consider building it yourself:

```
git clone https://github.com/Project-OSRM/osrm-backend.git
cd osrm-backend
docker build -t osrm-local --platform arm64 -f docker/Dockerfile . 
```

Then you need to replace every usage of osrm/osrm-backend image with osrm-local, for example:

```
sed -i '.backup' s|osrm/osrm-backend|osrm-local|g docker-compose.yml
sed -i '.backup' s|osrm/osrm-backend|osrm-local|g osrm/README.md    
```

Then see [osrm/README.md](osrm/README.md) to build the osrm files. 

See https://github.com/Project-OSRM/osrm-backend/issues/6133 
