#!/bin/bash

# Environment variables

# Backend
export AUTH_KEY=***FILL_THIS***
export ADMIN_PASSWORD=***FILL_THIS***

# Emailer
export SMTP_HOST=***FILL_THIS***
export SMTP_USER=***FILL_THIS***
export SMTP_PASS=***FILL_THIS***
export SMTP_FROM=lipas.dev@gmail.com

# Data migrations
export ICE_STADIUMS_CSV_URL="https://www.dropbox.com/s/1693i83sn5dz5zy/2018-07-23-jaahalli-utf-8.csv?dl=1"
export SWIMMING_POOLS_CSV_URL="https://www.dropbox.com/s/euut7y7tmdk86kq/2018-07-24-uimahalli-utf-8.csv?dl=1"

# Postgres
export DB_NAME=lipas
export DB_HOST=postgres
export DB_PORT=5432
export DB_SUPERUSER=postgres
export DB_SUPERUSER_PASSWORD=***FILL_THIS***
export DB_USER=lipas
export DB_PASSWORD=***FILL_THIS***

# Search
export SEARCH_HOST=http://127.0.0.1:9200
export SEARCH_USER=***FILL_THIS***
export SEARCH_PASS=***FILL_THIS***
export ES_JAVA_OPTS="-Xms1g -Xmx1g"

# Mapproxy
MML_USERNAME=***FILL_THIS***
MML_PASSWORD=***FILL_THIS***
export MML_AUTH=`echo -n $MML_USERNAME:$MML_PASSWORD | base64`

# Integrations
export OLD_LIPAS_URL=http://lipas-kehitys.cc.jyu.fi
export OLD_LIPAS_USER=***FILL_THIS***
export OLD_LIPAS_PASS=***FILL_THIS***

# Accessibility register
export ACCESSIBILITY_REGISTER_BASE_URL=https://asiointi.hel.fi/kapaesteettomyys_testi
export ACCESSIBILITY_REGISTER_SYSTEM_ID=***FILL_THIS***
export ACCESSIBILITY_REGISTER_SECRET_KEY=***FILL_THIS***

# Legacy API
export LEGACY_DB_URL="//postgres:5432/lipas-legacy"
export LEGACY_DB_USER=lipas
export LEGACY_DB_PASS=***FILL_THIS***

# Geoserver
export GEOSERVER_ADMIN_USER=admin
export GEOSERVER_ADMIN_PASSWORD=***FILL_THIS***
export GEOSERVER_INITIAL_MEMORY=1G
export GEOSERVER_MAX_MEMORY=2G

# OSRM
# export OSRM_CAR_URL="http://localhost:5001/table/v1/car/"
# export OSRM_BICYCLE_URL="http://localhost:5002/table/v1/bicycle/"
# export OSRM_FOOT_URL="http://localhost:5003/table/v1/foot/"
export OSRM_CAR_URL="http://osrm-car:5000/table/v1/car/"
export OSRM_BICYCLE_URL="http://osrm-bicycle:5000/table/v1/bicycle/"
export OSRM_FOOT_URL="http://osrm-foot:5000/table/v1/foot/"

# Mailchimp
export MAILCHIMP_CAMPAIGN_FOLDER_ID=58eea4f241 # testit
export MAILCHIMP_CAMPAIGN_FOLDER_ID=11df5ce8ec # uutiskirjeet
export MAILCHIMP_LIST_ID=a70cd3d18a
export MAILCHIMP_NEWSLETTER_INTEREST_ID=93a8beea4a
export MAILCHIMP_API_URL=https://us20.api.mailchimp.com/3.0
export MAILCHIMP_API_KEY=***FILL_THIS***

# Digitransit
export DIGITRANSIT_SUBSCRIPTION_KEY=***FILL_THIS***

# MML API
export MML_COVERAGE_URL="https://avoin-karttakuva.maanmittauslaitos.fi/ortokuvat-ja-korkeusmallit/wcs/v2"
export MML_API_KEY=***FILL_THIS***

# AWS
export AWS_ACCESS_KEY_ID=***FILL_THIS***
export AWS_SECRET_ACCESS_KEY=***FILL_THIS***
export AWS_REGION=eu-north-1
export AWS_S3_BUCKET_PREFIX=example
