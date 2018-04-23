#!/bin/bash

lein migratus migrate
lein run -m auth-service.server 3000
