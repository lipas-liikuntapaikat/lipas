# Legacy API Migration Status

**Date**: 2025-12-25
**Branch**: `feat/legacy-api-v1`

## Tests

| Test Suite                                                        | Tests  | Assertions | Description                                 |
|-------------------------------------------------------------------|--------|------------|---------------------------------------------|
| Handler tests (`lipas.backend.api.v1.handler-test`)               | 15     | 879        | Endpoint tests with test data               |
| Golden file tests (`lipas.backend.api.v1.golden-files-test`)      | 11     | 5,892      | Validates against 1000 production responses |
| Transform tests (`lipas.backend.api.v1.transform-test`)           | 14     | 499        | Unit tests for transformation logic         |
| Integration tests (`lipas.backend.api.v1.integration-test`)       | 13     | 125        | End-to-end: DB → ES → API pipeline          |
| HTTP tests (`lipas.backend.api.v1.http-test`)                     | 4      | 227        | Pagination and base path extraction         |
| Legacy index sync tests (`lipas.backend.api.v1.legacy-index-sync-test`) | 4  | 19         | Real-time sync to legacy ES index           |
| **Total**                                                         | **61** | **7,641**  |                                             |

## Schema Validation Status

| Endpoint                              | Schema Validation | Notes                         |
|---------------------------------------|-------------------|-------------------------------|
| `GET /v1/sports-places`               | ✅ Validates      | Main list endpoint            |
| `GET /v1/sports-places/:id`           | ✅ Validates      | Single item endpoint          |
| `GET /v1/categories`                  | ✅ Validates      | Category hierarchy            |
| `GET /v1/sports-place-types`          | ✅ Validates      | Type list                     |
| `GET /v1/sports-place-types/:code`    | ✅ Validates      | Type details with properties  |
| `GET /v1/deleted-sports-places`       | ✅ Validates      | Deleted items since timestamp |

**Note:** All three production entry points route to the same backend:
- `api.lipas.fi/v1/*` → backend `/v1/*`
- `lipas.fi/rest/api/*` → backend `/v1/*`
- `lipas.cc.jyu.fi/api/*` → backend `/v1/*`

## Implementation Details

### `/sports-place-types/:code` Endpoint

The endpoint uses `prop-mappings-reverse` to convert property keys from new kebab-case format to legacy camelCase:

- `:heating?` → `:heating`
- `:ligthing?` → `:ligthing` (preserving legacy typo)
- `:surface-material` → `:surfaceMaterial`

Property dataTypes are simplified to match legacy API:
- `"boolean"`, `"numeric"`, `"string"` only
- Enum types (`enum`, `enum-coll`) become `"string"` without `opts`

Common property `infoFi` is added to all types.

## Confidence Assessment

### Strengths

1. **Golden file validation**: Tests validate against 1000 real production API responses covering:
   - 17 unique type codes
   - 156 unique cities
   - Various property combinations

2. **Schema coercion active**: Response coercion middleware catches schema violations at runtime

3. **Transform pipeline verified**:
   - New LIPAS format → Legacy format conversion works
   - Localization (name, city name, type name) works correctly
   - Date format conversion (ISO8601 → `yyyy-MM-dd HH:mm:ss.SSS`) works
   - Admin/owner enum-to-string conversion works
   - Property key mapping via `prop-mappings-reverse` works

4. **Pagination verified**:
   - 200 OK for complete results
   - 206 Partial Content for paginated results
   - Link headers and X-total-count headers work

### Known Limitations

1. **Field selection**: Tests for field selection (`?fields=name,type`) are commented out because sparse responses don't match the full schema

2. **Additional properties**: New properties not in legacy API may be included - this is acceptable as it's not a breaking change

## Real-Time Legacy Index Sync

The legacy ES index is now kept in sync with the main index in real-time. When `save-sports-site!` is called:

1. **Active sites** (`status: "active"` or `"out-of-service-temporarily"`) are indexed to the legacy index
2. **Inactive sites** (`status: "out-of-service-permanently"` or `"incorrect-data"`) are **removed** from the legacy index
3. **Draft saves** do NOT sync to the legacy index

This ensures:
- External API consumers see changes immediately after save
- Deleted/inactive sites are removed from the legacy API
- No stale data in the legacy index between full re-indexes

**Implementation**: `lipas.backend.core/save-sports-site!` (lines 580-584)

```clojure
;; Sync to legacy API index - keep it in sync with the main index
(if (should-be-in-legacy-index? resp)
  (index-legacy-sports-place! search resp :sync)
  ;; Delete from legacy index if status changed to inactive
  (delete-from-legacy-index! search (:lipas-id resp)))
```

## Key V1 API Files

```
src/clj/lipas/backend/api/v1/
├── routes.clj       # Reitit routes, request/response handling
├── core.clj         # Business logic, ES queries orchestration
├── search.clj       # Elasticsearch query builders
├── transform.clj    # New LIPAS → Legacy format transformation
├── sports_place.clj # Property mappings (prop-mappings, prop-mappings-reverse)
├── handlers.clj     # Categories and types endpoint logic
├── http.clj         # Pagination, partial content responses
├── locations.clj    # Location formatting
└── util.clj         # Helper functions

src/cljc/lipas/schema/api/
└── v1.cljc          # Malli schemas for V1 API responses
```

## Test Commands

```bash
# Run all V1 API tests
bb test-ns lipas.backend.api.v1.handler-test \
           lipas.backend.api.v1.golden-files-test \
           lipas.backend.api.v1.transform-test \
           lipas.backend.api.v1.integration-test \
           lipas.backend.api.v1.http-test

# Run individual test suites
bb test-ns lipas.backend.api.v1.handler-test        # Endpoint tests
bb test-ns lipas.backend.api.v1.golden-files-test   # Golden file validation
bb test-ns lipas.backend.api.v1.transform-test      # Transform logic
bb test-ns lipas.backend.api.v1.integration-test    # End-to-end pipeline
bb test-ns lipas.backend.api.v1.http-test           # Pagination/base path
```

## Integration Test Coverage

The integration tests (`lipas.backend.api.v1.integration-test`) verify the complete data flow:

```
PostgreSQL (new LIPAS format)
    │
    ▼
Transform (lipas.backend.api.v1.transform)
    │
    ▼
Elasticsearch (legacy_sports_sites_current)
    │
    ▼
V1 API Response (camelCase, legacy format)
```

**What is tested:**
- Sports site saved to DB is queryable via V1 API
- Field transformations: `lipas-id` → `sportsPlaceId`, `type-code` → `typeCode`
- Date format: ISO8601 → `yyyy-MM-dd HH:mm:ss.SSS`
- City code: string → integer
- Filtering by typeCodes and cityCodes works
- Pagination returns 206 with Link headers
- Response schema compliance for all endpoints
