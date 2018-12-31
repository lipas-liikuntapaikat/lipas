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

# Mapproxy
MML_USERNAME=***FILL_THIS***
MML_PASSWORD=***FILL_THIS***
export MML_AUTH=`echo -n $MML_USERNAME:$MML_PASSWORD | base64`

# Integrations
export OLD_LIPAS_URL=http://lipas-kehitys.cc.jyu.fi
export OLD_LIPAS_USER=***FILL_THIS***
export OLD_LIPAS_PASS=***FILL_THIS***
