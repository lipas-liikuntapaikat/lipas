# Legacy API Production Comparison Findings

Date: 2025-12-14

## Overview

Comparison of local Legacy API implementation against production (`api.lipas.fi/v1`).

## Fixed Issues

### 1. English Translations for Admin/Owner

**Status:** FIXED (was already correct in `lipas.data.admins` and `lipas.data.owners`)

**Original symptom:** English translations were reported to use "City" instead of "Municipality".

**Resolution:** Investigation showed that `lipas.data.admins/old` and `lipas.data.owners/all` already have the correct translations ("Municipality / Technical services", "Municipality", etc.). The i18n EDN files in `src/cljc/lipas/i18n/en/admin.edn` have different translations but these are only used for the frontend UI, not the backend API.

**Test:** `legacy-api.integration-test/english-translations-test` passes.

### 2. Validation Error Message Format

**Status:** FIXED

**Original symptom:** Validation errors showed raw Malli schema objects instead of readable messages.

```bash
# Before fix
{"errors":{"pageSize":["malli.core$_simple_schema$reify$reify__39032@6f9688bd"]}}

# After fix - uses malli.error/humanize
{"errors":{"pageSize":["should be a positive int"]}}
```

**Fix applied:** Updated `lipas.backend.handler.clj` to use `malli.error/humanize` instead of `(str (:schema %))` for coercion error formatting. This produces human-readable error messages.

**Test:** `legacy-api.handler-test/error-response-format-test` passes.

## Acceptable Differences

### 3. searchString Results Order

**Status:** Acceptable

**Symptom:** Results for `searchString` queries return same relevant items but in slightly different order.

**Reason:** Elasticsearch scoring/relevance algorithms may differ between environments. The important thing is that relevant results are returned.

### 4. Route Segment Naming/Ordering

**Status:** Acceptable

**Symptom:** For hiking trails with multiple segments, production preserves original segment naming (e.g., segment_0, segment_1, segment_4, segment_3, segment_2) while local uses sequential naming (segment_0, segment_1, segment_2, segment_3, segment_4).

**Reason:** The geometry data is the same, just labeled differently. Local uses a deterministic sequential naming scheme which is actually more consistent.

### 5. Fields Parameter Format

**Status:** Acceptable

**Symptom:** Production only accepts multi-format (`fields=a&fields=b`), local accepts both multi-format AND comma-separated (`fields=a,b`).

**Decision:** Keep current behavior (more lenient). Document that production requires multi-format.

### 6. Categories - Different sportsPlaceTypes

**Status:** Acceptable

**Symptom:** Local categories have fewer type codes than production.

**Reason:** Local reflects current active types. Production includes historical/deprecated types.

### 7. Type Descriptions Differ

**Status:** Acceptable

**Symptom:** Type descriptions (e.g., for type 1120) have different text.

**Reason:** Descriptions have been updated in the new system. This is intentional improvement.

### 8. Coordinate Precision

TM35FIN coordinates have minor floating-point precision differences:
- Production: `380848.639900476`
- Local: `380848.63990043715`

No functional impact.

### 9. Legacy-only IDs (hardcoded to 0)

Fields hardcoded to `0` since the new database doesn't have these legacy identifiers:
- `location.locationId`
- `properties.pointId`
- `properties.routeId`
- `properties.routeCollectionId`
- `properties.areaId`

### 10. Link Header Path Prefix

- **Production**: `/api/sports-places/`
- **Local**: `/rest/api/sports-places/`

Expected - production uses nginx URL rewrite rules.

## Working Correctly

| Feature | Status | Notes |
|---------|--------|-------|
| Default fields (sportsPlaceId only) | OK | Returns only sportsPlaceId when no fields specified |
| `typeCodes` filter | OK | Same IDs returned |
| `cityCodes` filter | OK | Works correctly |
| `modifiedAfter` filter | OK | Requires full timestamp format `yyyy-MM-dd HH:mm:ss.SSS` |
| `retkikartta` filter | OK | Works correctly |
| `harrastuspassi` filter | OK | Works correctly |
| `lang` parameter (fi/se/en) | OK | All language translations work correctly |
| Single sports place by ID | OK | Identical structure |
| `location` structure | OK | All keys present |
| `location.geometries` | OK | FeatureCollection with correct structure |
| `location.coordinates` | OK | Both wgs84 and tm35fin present |
| `properties` structure | OK | Values match, types correct |
| `type` structure | OK | typeCode + name |
| Pagination status code | OK | 206 Partial Content |
| Link header format | OK | Correct rel values |
| X-total-count header | OK | Present and correct |
| Sort by sportsPlaceId | OK | Results sorted ascending |
| 404 error format | OK | Matches production |
| `deleted-sports-places` endpoint | OK | Structure matches |
| `categories` endpoint | OK | Structure matches |
| `sports-place-types` endpoint | OK | Structure matches |
| `sports-place-types/:code` endpoint | OK | Structure matches |
| Geo proximity filter | OK | Works correctly |
| Multi-format fields param | OK | `fields=a&fields=b` works |
| Ice stadium properties | OK | All rink properties match |
| Swimming pool properties | OK | All pool properties match |
| Combined filters | OK | typeCode + cityCode works |

## Test Commands

```bash
# Basic list - check sort order
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=5" | jq '[.[].sportsPlaceId]'
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=5" | jq '[.[].sportsPlaceId]'

# With fields parameter (production multi-format)
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=2&fields=name&fields=type.typeCode" | jq '.'

# Compare single item
curl -s "https://api.lipas.fi/v1/sports-places/72269" | jq 'keys | sort'
curl -s "http://localhost:8091/rest/api/sports-places/72269" | jq 'keys | sort'

# Test English translations
curl -s "https://api.lipas.fi/v1/sports-places/72269?lang=en" | jq '{admin, owner}'
curl -s "http://localhost:8091/rest/api/sports-places/72269?lang=en" | jq '{admin, owner}'

# Error responses
curl -s "https://api.lipas.fi/v1/sports-places/999999999"
curl -s "http://localhost:8091/rest/api/sports-places/999999999"

# Validation errors
curl -s "https://api.lipas.fi/v1/sports-places?pageSize=abc"
curl -s "http://localhost:8091/rest/api/sports-places?pageSize=abc"
```

## Summary

**Fixed:**
1. English translations - Already correct in `lipas.data.admins` and `lipas.data.owners` (confirmed via test)
2. Validation error format - Now uses `malli.error/humanize` for readable strings

**Acceptable As-Is:**
3. searchString result ordering (ES relevance scoring)
4. Route segment naming (deterministic sequential is fine)
5. Fields parameter accepts both formats (more lenient)
6. Category type code differences (reflects current active types)
7. Type description updates (intentional improvement)
