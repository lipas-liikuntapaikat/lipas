#!/bin/bash

set -e

# Stupid hack to pass secrets from env vars to mapproxy.yaml
# https://github.com/mapproxy/mapproxy/issues/278

cat /mapproxy/mapproxy_template.yaml | sed "s/{MML_AUTH}/$MML_AUTH/g" > /mapproxy/mapproxy.yaml

rm -f /mapproxy/app.py
mapproxy-util create -t wsgi-app -f mapproxy.yaml /mapproxy/app.py

if [ $1 == "dev" ]
then
    mapproxy-util serve-develop -b 0.0.0.0:8080 /mapproxy/mapproxy.yaml
else
    ln -s /mapproxy/mapproxy.yaml /mapproxy.yaml
    uwsgi --ini /uwsgi.conf
fi
