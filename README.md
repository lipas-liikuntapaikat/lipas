# LIPAS

[prod](https://uimahallit.lipas.fi/) |
 [dev](https://lipas-dev.cc.jyu.fi/) |
 [ci](https://travis-ci.com/lipas-liikuntapaikat/lipas) |
 [issues](https://github.com/lipas-liikuntapaikat/lipas/issues) |
 [dev-tasks](https://trello.com/b/q7dgXf28/lipas-20-dev) |
 [use-cases](https://trello.com/b/S8i6NexB/k%C3%A4ytt%C3%B6tapaukset) (finnish) |
 [ideas](https://trello.com/b/IEwJ5Nrq/lipas) (finnish)

LIPAS is a nationwide, public GIS service for Finnish sport
sites. More information
[here](https://www.jyu.fi/sport/en/cooperation/lipas).

LIPAS consists of three main services:

* Hub for data and information of sports facility conditions (under construction)
* Monitoring energy efficiency of ice stadiums (published)
* Monitoring energy efficiency of indoor swimming pools (published)

## Project status

Application in this repository will replace [current
LIPAS](http://lipas.fi/) once necessary features are done and data is
migrated. Until that old and new systems run side by side.

Existing interfaces will continue to work until further notice.

## Dev-env setup

``` shell
# Create env-file from template
cp .env.sample.sh .env.sh

# Fill in secrets
$EDITOR .env.sh

# Load environment variables
source .env.sh

# Initialize
docker-compose run backend-migrate
docker-compose run backend-seed

# Run backend services in background
docker-compose up -d proxy

# Run figwheel
lein figwheel
```

Add following to your `hosts` file to use same hostnames in both
docker and host while developing.

``` shell
# Lipas dev
127.0.0.1       postgres
127.0.0.1       backend-dev
127.0.0.1       mapproxy
127.0.0.1       proxy
```

## Production build

### Backend

`docker-compose run backend-build`

See [certs/README.md](certs/README.md).

### Frontend

`docker-compose run frontend-build`
