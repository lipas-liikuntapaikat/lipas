# Lipas

Lipas is a nationwide, public GIS service for Finnish sport sites.

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

## Production build

### Backend

TODO

### Frontend

`docker-compose run frontend-build`
