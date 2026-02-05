#!/bin/bash
mapproxy-seed --cleanup=mml_all \
  -f /mapproxy/config/mapproxy.yaml \
  -s /mapproxy/config/mml_cleanup.yaml
