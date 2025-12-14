# Legacy API Production Comparison Findings

Date: 2025-12-14

## Overview

Comparison of local Legacy API implementation against production (`api.lipas.fi/v1`).

## Fixed Issues

### 1. searchString Results Different (HIGH PRIORITY)

**Status:** FIXED

**Problem:** Production returns highly relevant matches; local returned unrelated results.

**Root Cause:** Two issues:
1. Search query used `query_string` which searched all fields equally
2. Results were sorted by sportsPlaceId, overriding relevance scoring

**Solution:** (in `legacy-api/search.clj`)
1. Changed to `multi_match` query with field boosting (name^10, type.name^5, marketingName^3)
2. When searchString is provided, use relevance scoring; only sort by sportsPlaceId when no search

```clojure
;; New multi_match query with field boosting
{:multi_match {:query qs
               :fields ["name^10" "type.name^5" "marketingName^3"
                        "location.address^2" "location.city.name^2" "*"]
               :type "best_fields"
               :operator "and"}}
```

### 2. HEAD Method Not Supported

**Status:** FIXED

**Problem:** Production supports HEAD requests; local returned 405 Method Not Allowed.

**Solution:** Added HEAD handlers to `/sports-places` and `/sports-places/:id` routes in `legacy-api/routes.clj`. HEAD handlers reuse GET handler logic but strip the response body.

## Accepted Differences

### 3. Field Validation Stricter in Production

**Status:** ACCEPTED (local is more lenient)

- Production rejects `location`, `type` as fields; requires specific paths like `location.address`
- Local accepts both high-level and specific field names
- Comma-separated `fields=a,b` works in local but not production

### 4. Error Message Format

**Status:** ACCEPTED

- Production: `{"error": "message"}`
- Local: `{"errors": {"field": ["message"]}}`

Different format but both provide useful error info.

### 5. Coordinate Precision

**Status:** ACCEPTED

TM35FIN coordinates have minor floating-point precision differences:
- Production: `380848.639900476`
- Local: `380848.63990043715`

No functional impact.

### 6. Legacy-only IDs (hardcoded to 0)

**Status:** ACCEPTED (per migration plan)

Fields hardcoded to `0` since the new database doesn't have these legacy identifiers:
- `location.locationId`
- `properties.pointId`
- `properties.routeId`, `routeCollectionId`, `areaId`, etc.

### 7. Link Header Path Prefix

**Status:** ACCEPTED

- Production: `/api/sports-places/`
- Local: `/rest/api/sports-places/`

Expected - production uses nginx URL rewrite rules.

## Working Correctly

| Feature | Status |
|---------|--------|
| Basic list (no params) | OK - same IDs, same order, only sportsPlaceId returned |
| `typeCodes` filter | OK - identical results |
| `cityCodes` filter | OK - identical results |
| Pagination (page, pageSize) | OK - same IDs, 206 status, Link headers, X-total-count |
| Field selection (proper format) | OK - `fields=name&fields=location.address` works |
| Combined filters | OK - typeCodes + cityCodes + fields work |
| Single item GET | OK - structure matches (with accepted differences above) |
| Language parameter (fi/en/se) | OK - admin/owner/type translations match |
| Sort by sportsPlaceId | OK - ascending order |
| `deleted-sports-places` endpoint | OK - structure matches |
| `categories` endpoint | OK - structure matches |
| `sports-place-types` endpoint | OK - structure matches |

## Test Commands

```bash
# Basic list comparison
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3" | jq '.'

# Single item comparison
curl -s "https://api.lipas.fi/v1/sports-places/72269" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places/72269" | jq '.'

# Language test
curl -s "https://api.lipas.fi/v1/sports-places/72269?lang=en" | jq '{admin, owner, type}'
curl -s "http://localhost:8091/rest/api/sports-places/72269?lang=en" | jq '{admin, owner, type}'

# searchString test (currently broken)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=3&searchString=uimahalli&fields=name" | jq '.'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=3&searchString=uimahalli&fields=name" | jq '.'
```
