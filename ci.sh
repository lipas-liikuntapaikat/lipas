#!/bin/bash

set -e

# Backend
docker-compose run backend-migrate
docker-compose run backend-test

# Frontend
# TODO
