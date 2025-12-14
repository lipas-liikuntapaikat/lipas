# Legacy API Production Comparison Findings

Date: 2025-12-14 (Updated)

## Overview

Comparison of local Legacy API implementation against production (`api.lipas.fi/v1`).

## Critical Issues (Must Fix)

### 1. Pagination Link Header Bug

**Status:** FIXED ✅

**Symptom:** The "next" page link in the Link header was off by one.

**Root cause:** In `routes.clj`, the code passed `offset` (0-indexed) to `create-page-links`, but the function expected a 1-indexed page number.

**Fix:** Changed to pass the original `page` parameter instead of `offset` to `create-page-links`.

**Files changed:**
- `src/clj/legacy_api/routes.clj` - line 179-180

### 2. Fields Parameter Format Mismatch

**Status:** FIXED ✅ (Already working)

**Symptom:** Production expects separate query params, local accepts comma-separated.

**Finding:** Local implementation already supports BOTH formats:
- Repeated params format: `fields=name&fields=type.typeCode` (production format)
- Comma-separated format: `fields=name,type.typeCode` (additional convenience)

This is MORE lenient than production, which provides backwards compatibility without breaking existing clients.

**No changes needed** - the schema and route handling already support both formats.

## Medium Priority Issues

### 3. Type Properties - Missing `infoFi`

**Status:** FIXED ✅

**Symptom:** The `infoFi` property was present in production's type schema but missing from local.

**Fix:** Added `infoFi` property definition in `legacy-api/api.clj` that is appended to all type property definitions. This maps to the `comment` field in the new data model.

Note: The sports place DATA already correctly maps `comment` to `infoFi` (done in `transform.clj` line 92). This fix adds the property DEFINITION to the type schema endpoint.

**Files changed:**
- `src/clj/legacy_api/api.clj` - added `info-fi-property` definition and included it in type properties output

**Impact:** Type properties endpoint now includes `infoFi` property definition matching production.

### 4. Categories - Different sportsPlaceTypes

**Status:** Acceptable

**Symptom:** Local categories have fewer type codes than production.

```bash
# Production subCategory 1 has types: [101, 103, 104, 106, 107, 108, 112, 111, 110, 109, 102, 113]
# Local subCategory 1 has types:      [110, 101, 106, 113, 109, 111, 103, 107, 112]
# Missing: 104, 108, 102
```

**Reason:** Local reflects current active types in the database. Production includes historical/deprecated types.

## Working Correctly

| Feature | Status | Notes |
|---------|--------|-------|
| Default fields (sportsPlaceId only) | OK | Returns only sportsPlaceId when no fields specified |
| `typeCodes` filter | OK | Same IDs returned |
| `cityCodes` filter | OK | Works correctly |
| `modifiedAfter` filter | OK | Requires full timestamp format `yyyy-MM-dd HH:mm:ss.SSS` |
| `searchString` filter | OK | Same results returned |
| `retkikartta` filter | OK | Works correctly |
| `harrastuspassi` filter | OK | Works correctly |
| `lang=fi` | OK | Finnish translations match |
| `lang=en` | OK | English translations match |
| `lang=se` | OK | Swedish translations match |
| Single sports place structure | OK | Identical top-level keys |
| `location` structure | OK | All keys present |
| `location.geometries` | OK | FeatureCollection with correct structure |
| `location.coordinates` | OK | Both wgs84 and tm35fin present |
| `properties` structure | OK | Values match for same ID |
| `type` structure | OK | typeCode + name |
| Pagination status code | OK | 206 Partial Content |
| X-total-count header | OK | Present and correct |
| `deleted-sports-places` endpoint | OK | Structure matches |
| `categories` endpoint | OK | Structure matches |
| `sports-place-types` endpoint | OK | Structure matches |
| `sports-place-types/:code` endpoint | OK | Structure matches |
| Geo proximity filter | OK | Works correctly |

## Acceptable Differences

### Coordinate precision

TM35FIN coordinates have minor floating-point precision differences. No functional impact.

### Legacy-only IDs (hardcoded to 0)

Fields like `location.locationId`, `properties.pointId`, `properties.routeId` are hardcoded to `0` since the new database doesn't have these legacy identifiers.

### Different data

Local dev environment may have different sports places than production, so specific IDs and counts may differ.

### Link header path prefix

- **Production**: `/api/sports-places/`
- **Local**: `/rest/api/sports-places/`

This is expected - production uses nginx URL rewrite rules.

### Type/category ordering

The order of items in arrays (like sportsPlaceTypes in categories) may differ. This is acceptable as JSON object/array ordering is not semantically meaningful.

## Test Commands

```bash
# Basic list (default fields)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3" | jq '.'

# With fields parameter (production format)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=2&fields=name&fields=type.typeCode" | jq '.'

# With fields parameter (local comma-separated format)
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=2&fields=name,type.typeCode" | jq '.'

# Type filter
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3&typeCodes=1120" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3&typeCodes=1120" | jq '.'

# Geo filter
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3&closeToLon=24.9384&closeToLat=60.1699&closeToDistanceKm=1" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3&closeToLon=24.9384&closeToLat=60.1699&closeToDistanceKm=1" | jq '.'

# Language parameter
curl -s "https://api.lipas.fi/v1/sports-places/72269?lang=en" | jq '{admin, owner}'

# Pagination headers comparison
curl -sI "https://api.lipas.fi/v1/sports-places?pageSize=2&page=1" | grep -E "^(Link|X-total)"
curl -sI "http://localhost:8091/rest/api/sports-places?pageSize=2&page=1" | grep -E "^(Link|X-total)"

# Deleted sports places
curl -s "https://api.lipas.fi/v1/deleted-sports-places?since=2024-01-01%2000:00:00.000" | jq '.[0:3]'
curl -s "http://localhost:8091/rest/api/deleted-sports-places?since=2024-01-01%2000:00:00.000" | jq '.[0:3]'

# Categories
curl -s "https://api.lipas.fi/v1/categories" | jq '.[0]'
curl -s "http://localhost:8091/rest/api/categories" | jq '.[0]'

# Type detail
curl -s "https://api.lipas.fi/v1/sports-place-types/1120" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-place-types/1120" | jq '.'
```
