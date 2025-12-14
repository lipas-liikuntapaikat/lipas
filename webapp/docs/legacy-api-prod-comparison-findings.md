# Legacy API Production Comparison Findings

Date: 2025-12-14

## Overview

Sanity check comparing our local Legacy API implementation against production (`api.lipas.fi/v1`).

## Status

All known issues have been fixed. The local implementation matches production API behavior.

## Acceptable Differences

### Coordinate precision

TM35FIN coordinates have slightly different precision due to floating point calculations. No functional impact.

### Legacy-only IDs (hardcoded to 0)

Fields like `location.locationId`, `properties.pointId`, `properties.routeId` etc. are hardcoded to `0` since the new LIPAS database doesn't have these legacy identifiers.

## Test Commands

```bash
# Compare deleted-sports-places
curl -s "https://api.lipas.fi/v1/deleted-sports-places" | jq '.[0:2]'
curl -s "http://localhost:8091/rest/api/deleted-sports-places" | jq '.[0:2]'

# Compare single sports place
curl -s "https://api.lipas.fi/v1/sports-places/86695" | jq -S . > /tmp/prod.json
curl -s "http://localhost:8091/rest/api/sports-places/86695" | jq -S . > /tmp/local.json
diff /tmp/prod.json /tmp/local.json
```
