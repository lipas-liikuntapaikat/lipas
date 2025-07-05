# LIPAS

[prod](https://www.lipas.fi/) |
 [dev](https://lipas-dev.cc.jyu.fi/) |
 [ci](https://github.com/lipas-liikuntapaikat/lipas/actions) |
 [issues](https://github.com/lipas-liikuntapaikat/lipas/issues) |
 [dev-tasks](https://trello.com/b/q7dgXf28/lipas-20-dev) |
 [use-cases](https://trello.com/b/S8i6NexB/k%C3%A4ytt%C3%B6tapaukset) (finnish) |
 [ideas](https://trello.com/b/IEwJ5Nrq/lipas) (finnish)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/lipas-liikuntapaikat/lipas)

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

# 1. ALTERNATIVE: Run setup script
# This runs the clj app in a container also
./setup-dev.sh

# 2. ALTERNATIVE:
# Start proxy-local container
bb up
cd webapp
# Start repl
clojure -M:nrepl
# Connect with your editor to port 7888, then:
user=> (reset)
# Start cljs build
npm i
npm run watch
```

## Available Tasks (Babashka)

The project uses [Babashka](https://babashka.org/) for task automation. Run `bb tasks` to see all available tasks.

### Development Tasks
```shell
bb test                    # Run fast tests
bb test-integration        # Run integration tests  
bb test-all               # Run all tests
bb db-migrate             # Run database migrations
bb db-status              # Check migration status
bb uberjar                # Build production JAR
```

### Docker Tasks
```shell
bb docker-build          # Build in Docker
bb docker-migrate         # Run migrations in Docker
bb docker-test            # Run tests in Docker
```

Run `bb test-help`, `bb db-help`, or `bb docker-help` for detailed information.

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

```bash
# Using Babashka tasks
bb uberjar

# Using Docker
docker compose run backend-build

# Direct command
clojure -T:build uber
```

See [certs/README.md](certs/README.md).

### Frontend

```bash
# Using Docker
docker compose run frontend-npm-deps
docker compose run frontend-build

# Direct command
clojure -M -m shadow.cljs.devtools.cli release app
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

### Backups

```
# Dump
docker exec -i lipas-postgres-1 pg_dump -U lipas -Fc > lipas.backup
# Restore
docker exec -i lipas-postgres-1 pg_restore -Fc < lipas.backup
# Rebuild ES index
docker compose run --rm backend-index-search
```

## Migration from Leiningen

This project has been migrated from Leiningen to deps.edn + tools.build + Babashka:

- **Build system**: `lein uberjar` → `bb uberjar` or `clojure -T:build uber`
- **Tests**: `lein test` → `bb test` or `clojure -M:dev:test`
- **REPL**: `lein repl` → `clojure -M:nrepl` (connect to port 7888)
- **Migrations**: `lein migratus migrate` → `bb db-migrate`
- **Docker**: All docker-compose services updated to use deps.edn

All Babashka tasks provide help: `bb <task>-help` (e.g., `bb db-help`, `bb test-help`)
