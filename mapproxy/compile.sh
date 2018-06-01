#!/bin/bash

set -e

# Stupid hack to pass secrets from env vars to mapproxy.yaml
# https://github.com/mapproxy/mapproxy/issues/278

cat /tmp/mapproxy_template.yaml | sed "s/{MML_AUTH}/$MML_AUTH/g" > /mapproxy/mapproxy.yaml
