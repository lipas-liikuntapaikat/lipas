# Legacy API Production Comparison Findings

Date: 2025-12-14

## Overview

Sanity check comparing our local Legacy API implementation against production (`api.lipas.fi/v1`).

## Status

All known issues have been fixed. The local implementation matches production API behavior.

## Fixed Issues

### Default fields behavior (commit 4d7c21ad)

**Issue:** `/rest/api/sports-places` returned all fields by default, but production returns only `sportsPlaceId`.

**Fix:** When no `fields` param is specified, the list endpoint now returns only `{sportsPlaceId: N}` for each item, matching production behavior.

```bash
# Both now return only sportsPlaceId by default
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=2" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=2" | jq '.'
# Output: [{"sportsPlaceId": 12345}, {"sportsPlaceId": 12346}]
```

## Acceptable Differences

### Coordinate precision

TM35FIN coordinates have slightly different precision due to floating point calculations. No functional impact.

### Legacy-only IDs (hardcoded to 0)

Fields like `location.locationId`, `properties.pointId`, `properties.routeId` etc. are hardcoded to `0` since the new LIPAS database doesn't have these legacy identifiers.

## Test Commands

```bash
# Compare list endpoint (default fields)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3" | jq '.'

# Compare list endpoint with fields
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=2&fields=name&fields=type.typeCode" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=2&fields=name&fields=type.typeCode" | jq '.'

# Compare deleted-sports-places
curl -s "https://api.lipas.fi/v1/deleted-sports-places" | jq '.[0:2]'
curl -s "http://localhost:8091/rest/api/deleted-sports-places" | jq '.[0:2]'

# Compare single sports place
curl -s "https://api.lipas.fi/v1/sports-places/86695" | jq -S . > /tmp/prod.json
curl -s "http://localhost:8091/rest/api/sports-places/86695" | jq -S . > /tmp/local.json
diff /tmp/prod.json /tmp/local.json
```
