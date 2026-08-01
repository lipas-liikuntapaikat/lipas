#!/bin/bash

set -e

source .env.sh

### Git hooks ###

printf "\n *** Enabling repository git hooks *** \n\n"
# .githooks/pre-commit rejects unformatted or broken Clojure. Hooks are not
# shared by git itself, so every clone has to opt in via core.hooksPath.
git config core.hooksPath .githooks
# Keeps the bulk cljfmt reformat out of `git blame`.
git config blame.ignoreRevsFile .git-blame-ignore-revs
echo "core.hooksPath -> .githooks (bypass a single commit with --no-verify)"

### Cert ###

printf "\n *** Generating self-signed SSL certificate *** \n\n"
openssl req -new -newkey rsa:2048 -days 365 -nodes -x509 \
        -subj "/C=US/ST=Denial/L=Springfield/O=Dis/CN=www.example.com" \
        -keyout certs/server.key -out certs/server.crt

### Backend ###

printf "\n *** Running backend migrations *** \n\n"
# The -dev variant runs from source — the plain backend-migrate service runs
# from the installed uberjar, which does not exist yet on a fresh clone.
docker compose run backend-migrate-dev

printf "\n *** Packaging backend *** \n\n"
docker compose run backend-build

printf "\n *** Creating htpasswd file for Kibana *** \n\n"
docker compose build htpasswd
docker compose run htpasswd admin $ADMIN_PASSWORD > nginx/htpasswd

### Frontend ###

printf "\n *** Fetching npm dependencies *** \n\n"
npm install

### Start services ###

printf "\n *** Starting backend services *** \n\n"
docker compose up -d proxy-local

printf "\n *** Setup complete! *** \n\n"
echo "To start the frontend dev server, run:"
echo "  npx shadow-cljs watch app"
