#!/bin/sh
# Elasticsearch initialization script
# Waits for ES to be ready, then applies ILM policies

set -e

ES_HOST="${ES_HOST:-elasticsearch:9200}"
MAX_RETRIES="${MAX_RETRIES:-30}"
RETRY_INTERVAL="${RETRY_INTERVAL:-5}"

echo "Waiting for Elasticsearch at $ES_HOST..."

# Wait for Elasticsearch to be ready
retry_count=0
until curl -s "http://$ES_HOST/_cluster/health" | grep -q '"status":"green"\|"status":"yellow"'; do
    retry_count=$((retry_count + 1))
    if [ $retry_count -ge $MAX_RETRIES ]; then
        echo "ERROR: Elasticsearch did not become ready in time"
        exit 1
    fi
    echo "Waiting for Elasticsearch... (attempt $retry_count/$MAX_RETRIES)"
    sleep $RETRY_INTERVAL
done

echo "Elasticsearch is ready!"

# Apply logstash ILM policy with 60-day retention
echo "Applying ILM policy: logstash-policy"

response=$(curl -s -w "\n%{http_code}" -X PUT "http://$ES_HOST/_ilm/policy/logstash-policy" \
    -H 'Content-Type: application/json' \
    -d '{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_age": "30d",
            "max_size": "50gb"
          }
        }
      },
      "delete": {
        "min_age": "60d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}')

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    echo "  OK: logstash-policy applied successfully"
else
    echo "  WARNING: Failed to apply logstash-policy (HTTP $http_code)"
    echo "  Response: $body"
fi

echo "Elasticsearch initialization complete!"
