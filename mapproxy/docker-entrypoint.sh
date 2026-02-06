#!/bin/bash
set -e

# Create mapproxy user
groupadd -f mapproxy
useradd --home-dir /mapproxy -s /bin/bash -g mapproxy mapproxy 2>/dev/null || true
chown -R mapproxy:mapproxy /mapproxy/config/cache_data

# Substitute environment variables in template using Python
python3 -c "
import os
with open('/mapproxy/config/mapproxy_template.yaml') as f:
    content = f.read()
content = content.replace('\${MML_AUTH}', os.environ.get('MML_AUTH', ''))
with open('/mapproxy/config/mapproxy.yaml', 'w') as f:
    f.write(content)
"

# Run uwsgi with socket on port 8080 (uwsgi protocol)
cd /mapproxy
exec su mapproxy -c "/usr/local/bin/uwsgi \
  --master \
  --socket 0.0.0.0:8080 \
  --wsgi-file /mapproxy/app.py \
  --processes 4 \
  --threads 10"
