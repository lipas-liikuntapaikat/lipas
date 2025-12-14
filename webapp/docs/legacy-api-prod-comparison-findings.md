# Legacy API Production Comparison Findings

Date: 2025-12-14

## Overview

Comparison of local Legacy API implementation against production (`api.lipas.fi/v1`).

## Critical Issues (Must Fix)

### 1. Missing Sort by sportsPlaceId

**Status:** FIXED

**Symptom:** Local returns results in arbitrary ES document order. Production always returns results sorted by `sportsPlaceId` ascending.

**Evidence:**
```bash
# Production first 5 IDs (sorted)
[72269, 72340, 72348, 72359, 72361]

# Local first 5 IDs (unsorted)
[75100, 75104, 75123, 75995, 76121]

# Verified: local DOES have ID 72269, but it's not first
curl -s "http://localhost:8091/rest/api/sports-places/72269" | jq '.sportsPlaceId'
# Returns: 72269
```

**Impact:**
- Pagination is inconsistent - same page returns different results
- Geo queries return different results despite matching the same locations
- API consumers relying on deterministic ordering will break

**Fix Applied:** Added `:sort [{:sportsPlaceId {:order "asc" :unmapped_type "long"}}]` to the ES query in `search.clj`. The `unmapped_type` handles empty indexes gracefully.

```clojure
;; In fetch-sports-places function
{:query query
 :sort [{:sportsPlaceId {:order "asc"
                         :unmapped_type "long"}}]  ;; FIXED
 :track_total_hits true
 :size (:limit params)
 :from (* (:offset params) (:limit params))}
```

**Files changed:**
- `src/clj/legacy_api/search.clj` - `fetch-sports-places` function

### 2. Error Response Format Mismatch

**Status:** FIXED

**Symptom:** Error responses have different structure than production.

| Error Type | Production | Local (Before) | Local (After) |
|------------|------------|----------------|---------------|
| 404 Not Found | `{"errors":{"sportsPlaceId":"Didn't find such sports place. :("}}` | `{"error":"Sports place not found"}` | `{"errors":{"sportsPlaceId":"Didn't find such sports place. :("}}` |
| Invalid param | `{"errors":{"typeCodes":["(not ...)"]}}` | Malli format with `{"value":..., "humanized":...}` | `{"errors":{"typeCodes":["[:enum ...]"]}}` |

**Impact:** API consumers parsing error responses will fail.

**Fix Applied:**
1. Updated 404 response in `routes.clj` to return exact production format
2. Added custom coercion error handler in `handler.clj` that formats errors for `/rest/api/*` routes

## Medium Priority Issues

### 3. Fields Parameter Format

**Status:** Documented (Acceptable)

**Symptom:** Production only accepts multi-format (`fields=a&fields=b`), local accepts both multi-format AND comma-separated (`fields=a,b`).

**Risk:** Code tested locally with comma-separated format will fail in production.

**Decision:** Keep current behavior (more lenient). Document that production requires multi-format.

### 4. Categories - Different sportsPlaceTypes

**Status:** Acceptable

**Symptom:** Local categories have fewer type codes than production.

```bash
# Production subCategory 1 types: [101, 102, 103, 104, 106, 107, 108, 109, 110, 111, 112, 113]
# Local subCategory 1 types:      [101, 103, 106, 107, 109, 110, 111, 112, 113]
# Missing: 102, 104, 108
```

**Reason:** Local reflects current active types. Production includes historical/deprecated types.

### 5. Type Descriptions Differ

**Status:** Acceptable

**Symptom:** Type descriptions (e.g., for type 1120) have different text.

**Reason:** Descriptions have been updated in the new system. This is intentional improvement.

## Working Correctly

| Feature | Status | Notes |
|---------|--------|-------|
| Default fields (sportsPlaceId only) | OK | Returns only sportsPlaceId when no fields specified |
| `typeCodes` filter | OK | Same IDs returned (when sorted) |
| `cityCodes` filter | OK | Works correctly |
| `modifiedAfter` filter | OK | Requires full timestamp format `yyyy-MM-dd HH:mm:ss.SSS` |
| `searchString` filter | OK | Exact same results returned |
| `retkikartta` filter | OK | Works correctly |
| `harrastuspassi` filter | OK | Works correctly |
| `lang` parameter (fi/en/se) | OK | Translations work |
| Single sports place by ID | OK | Identical structure |
| `location` structure | OK | All keys present |
| `location.geometries` | OK | FeatureCollection with correct structure |
| `location.coordinates` | OK | Both wgs84 and tm35fin present |
| `properties` structure | OK | Values match, types correct |
| `type` structure | OK | typeCode + name |
| Pagination status code | OK | 206 Partial Content |
| Link header format | OK | Correct rel values |
| X-total-count header | OK | Present and correct |
| `deleted-sports-places` endpoint | OK | Structure matches |
| `categories` endpoint | OK | Structure matches |
| `sports-place-types` endpoint | OK | Structure matches |
| `sports-place-types/:code` endpoint | OK | Structure matches |
| Geo proximity filter | OK | Works correctly (results differ due to sort) |
| Multi-format fields param | OK | `fields=a&fields=b` works |

## Acceptable Differences

### Coordinate Precision

TM35FIN coordinates have minor floating-point precision differences:
- Production: `380848.639900476`
- Local: `380848.63990043715`

No functional impact.

### Legacy-only IDs (hardcoded to 0)

Fields hardcoded to `0` since the new database doesn't have these legacy identifiers:
- `location.locationId`
- `properties.pointId`
- `properties.routeId`
- `properties.routeCollectionId`
- `properties.areaId`

### Different Data Counts

Local dev environment has different sports places than production:
- Production: ~47,125 sports places
- Local: ~47,756 sports places

### Link Header Path Prefix

- **Production**: `/api/sports-places/`
- **Local**: `/rest/api/sports-places/`

Expected - production uses nginx URL rewrite rules.

### Type/Category Ordering

Order of items in arrays may differ. JSON array ordering is not semantically meaningful for these endpoints.

## Test Commands

```bash
# Basic list - check sort order
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=5" | jq '[.[].sportsPlaceId]'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=5" | jq '[.[].sportsPlaceId]'

# Verify sort: production should be sorted ascending
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=10" | jq '[.[].sportsPlaceId] | sort == .'
# Returns: true

# With fields parameter (production multi-format)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=2&fields=name&fields=type.typeCode" | jq '.'

# Geo filter - results should match when properly sorted
curl -s "https://api.lipas.fi/v1/sports-places?closeToLon=24.94&closeToLat=60.17&closeToDistanceKm=1&pageSize=5" | jq '[.[].sportsPlaceId] | sort == .'

# Compare single item
curl -s "https://api.lipas.fi/v1/sports-places/72269" | jq 'keys | sort'
curl -s "http://localhost:8091/rest/api/sports-places/72269" | jq 'keys | sort'

# Error responses
curl -s "https://api.lipas.fi/v1/sports-places/999999999"
curl -s "http://localhost:8091/rest/api/sports-places/999999999"

# Pagination headers
curl -sI "https://api.lipas.fi/v1/sports-places?pageSize=2&page=2" | grep -iE "(link|x-total)"
curl -sI "http://localhost:8091/rest/api/sports-places?pageSize=2&page=2" | grep -iE "(link|x-total)"
```

## Summary

**Fixed (2025-12-14):**
1. ✅ Sort by `sportsPlaceId` - Added to ES queries with `unmapped_type` for empty index handling
2. ✅ Error response format - Now matches production for 404s and validation errors

**Monitor:**
3. Fields parameter format (document multi-format requirement)
