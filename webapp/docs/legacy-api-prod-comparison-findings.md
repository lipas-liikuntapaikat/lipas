# Legacy API Production Comparison Findings

Date: 2025-12-14

## Overview

Comparison of local Legacy API implementation against production (`api.lipas.fi/v1`).

## Critical Issues (Must Fix)

### 1. Geo proximity filter broken

**Status:** ✅ FIXED

**Symptom:** Returns "Internal search error" when using `closeToLon`, `closeToLat`, `closeToDistanceKm` parameters.

**Root cause:** In `search.clj`, `validate-geo-params` expected a `:point` key, but `routes.clj` passed the ES field name as the key instead.

**Fix:** Updated `routes.clj` to pass geo params with proper structure `{:distance "1000m" :field :location.coordinates.wgs84 :point {:lon ... :lat ...}}` and updated `search.clj` to build the correct ES geo_distance query.

**Files changed:**
- `src/clj/legacy_api/search.clj` - `validate-geo-params` and `create-geo-filter` functions
- `src/clj/legacy_api/routes.clj` - lines 138-144

### 2. English translation mismatch

**Status:** ✅ FIXED

**Symptom:** English translations for `admin` and `owner` fields differ from production.

```bash
# Production (lang=en):
{"admin": "Municipality / Technical services", "owner": "Municipality"}

# Local (was):
{"admin": "City / technical services", "owner": "City"}
```

**Fix:** Updated English translations in `admins.cljc` and `owners.cljc` to use "Municipality" instead of "City".

**Files changed:**
- `src/cljc/lipas/data/admins.cljc` - English translations for city-* admin types
- `src/cljc/lipas/data/owners.cljc` - English translation for "city" owner type

## Medium Priority Issues

### 3. Link header format differs

**Status:** ✅ FIXED

**Symptom:**
- Links included all params (even empty/nil ones)
- Used internal param names (`city-codes` vs `cityCodes`)

**Fix:** Updated `routes.clj` to build link params from original camelCase query params, filtering out nil/empty values.

**Files changed:**
- `src/clj/legacy_api/routes.clj` - pagination link params construction

## Working Correctly

| Feature | Status | Notes |
|---------|--------|-------|
| Default fields (sportsPlaceId only) | OK | Fixed in commit 4d7c21ad |
| `typeCodes` filter | OK | Same IDs returned |
| `cityCodes` filter | OK | Works correctly |
| `modifiedAfter` filter | OK | Requires full timestamp format |
| `searchString` filter | OK | Results may vary by data |
| `retkikartta` filter | OK | Works correctly |
| `harrastuspassi` filter | OK | Works correctly |
| `lang=fi` | OK | Finnish translations match |
| `lang=se` | OK | Swedish translations match exactly |
| Single sports place structure | OK | Identical top-level keys |
| `location` structure | OK | All keys present |
| `properties` structure | OK | Values match for same ID |
| Pagination headers | OK | 206 status, X-total-count |
| `deleted-sports-places` endpoint | OK | Structure matches |
| `fields` parameter | OK | Field selection works |

## Acceptable Differences

### Coordinate precision

TM35FIN coordinates have minor floating-point precision differences. No functional impact.

### Legacy-only IDs (hardcoded to 0)

Fields like `location.locationId`, `properties.pointId`, `properties.routeId` are hardcoded to `0` since the new database doesn't have these legacy identifiers.

### Different data

Local dev environment may have different sports places than production, so specific IDs and counts may differ.

## Test Commands

```bash
# Basic list (default fields)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3" | jq '.'

# With fields parameter
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=2&fields=name" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=2&fields=name" | jq '.'

# Type filter
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3&typeCodes=1120" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3&typeCodes=1120" | jq '.'

# Geo filter (BROKEN locally)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3&closeToLon=24.9384&closeToLat=60.1699&closeToDistanceKm=1" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3&closeToLon=24.9384&closeToLat=60.1699&closeToDistanceKm=1" | jq '.'

# Language parameter
curl -s "https://api.lipas.fi/v1/sports-places/72269?lang=en" | jq '{admin, owner}'
curl -s "http://localhost:8091/rest/api/sports-places/72269?lang=en" | jq '{admin, owner}'

# Single sports place comparison
curl -s "https://api.lipas.fi/v1/sports-places/72269" | jq -S . > /tmp/prod.json
curl -s "http://localhost:8091/rest/api/sports-places/72269" | jq -S . > /tmp/local.json
diff /tmp/prod.json /tmp/local.json

# Deleted sports places
curl -s "https://api.lipas.fi/v1/deleted-sports-places?since=2024-01-01%2000:00:00.000&pageSize=3" | jq '.'
curl -s "http://localhost:8091/rest/api/deleted-sports-places?since=2024-01-01%2000:00:00.000" | jq '.[0:3]'

# Pagination headers
curl -sD - "https://api.lipas.fi/v1/sports-places?pageSize=3&page=1" | head -15
curl -sD - "http://localhost:8091/rest/api/sports-places?pageSize=3&page=1" | head -15
```
