# V1 API Technical Documentation

The V1 API (legacy API) is now served from the main LIPAS backend, replacing the separate legacy codebase. This document covers implementation details.

## Architecture

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

**Key design decisions:**
- **Read-only API**: POST/PUT/DELETE not implemented
- **Separate ES index**: Pre-transformed data in `legacy_sports_sites_current`
- **Transform at index time**: Not at query time, for performance
- **Malli schemas**: Runtime validation of responses

## Namespace Structure

```
src/clj/lipas/backend/api/v1/
├── routes.clj       # Reitit routes, Swagger/OpenAPI specs
├── core.clj         # Business logic, ES query orchestration
├── search.clj       # Elasticsearch query builders
├── transform.clj    # New LIPAS → Legacy format transformation
├── sports_place.clj # Property mappings (prop-mappings, prop-mappings-reverse)
├── handlers.clj     # Categories and types endpoint handlers
├── http.clj         # Pagination, 206 Partial Content, Link headers
├── locations.clj    # Location formatting
└── util.clj         # Helper functions

src/cljc/lipas/schema/api/
└── v1.cljc          # Malli schemas for V1 API responses
```

## Data Transformation

The `transform.clj` module converts new LIPAS format to legacy format.

### Key Transformations

| From (new)        | To (legacy)       | Example                                                  |
|-------------------|-------------------|----------------------------------------------------------|
| `kebab-case` keys | `camelCase` keys  | `lipas-id` → `sportsPlaceId`                             |
| ISO8601 dates     | Legacy timestamp  | `2019-08-29T12:55:30.259Z` → `2019-08-29 15:55:30.259`   |
| Enum keys         | Localized strings | `"city-technical-services"` → `"Kunta / tekninen toimi"` |
| 3D coordinates    | 2D coordinates    | `[lon, lat, elev]` → `[lon, lat]`                        |
| Property keys     | Legacy keys       | 150+ mappings in `prop-mappings`                         |

### Legacy Quirks Preserved

These intentional "bugs" are preserved for backwards compatibility:

- `ligthing` typo (not `lighting`)
- `pool1LengthMM` with double M (pool 1 length)
- Golf area (type 1650) → Golf point (type 1620) conversion
- `location.locationId` hardcoded to `0` (legacy internal ID)
- `location.sportsPlaces` returns `[lipas-id]` (was 1:many, now 1:1)
- Geometry feature IDs (`pointId`, `routeId`, etc.) hardcoded to `0`

### Type-Specific Transformations

Multimethods in `transform.clj` handle special cases:

- **Ice stadiums (2510, 2520)**: Extract rink dimensions from `:rinks` collection
- **Swimming pools (3110, 3130)**: Extract pool dimensions from `:pools` collection
- **Golf (1650)**: Convert area geometry to point (centroid)

## Elasticsearch Index

**Index name**: `legacy_sports_sites_current` (alias)

**Key mappings**:
```clojure
{:location.coordinates.wgs84 {:type "geo_point"}
 :location.geom-coll         {:type "geo_shape"}
 :lastModified               {:type "date"
                              :format "yyyy-MM-dd HH:mm:ss.SSS"}}
```

Documents are stored in legacy format (camelCase, legacy property names).

### Reindexing

Full reindex of all sports places:

```bash
docker compose run backend-index-search --legacy
```

Or from REPL:
```clojure
(user/reindex-legacy-search!)
```

## Real-Time Index Sync

When `save-sports-site!` is called, the legacy index is updated synchronously:

```clojure
;; In lipas.backend.core/save-sports-site!
(if (should-be-in-legacy-index? resp)
  (index-legacy-sports-place! search resp :sync)
  (delete-from-legacy-index! search (:lipas-id resp)))
```

**Indexing rules:**
- `status: "active"` or `"out-of-service-temporarily"` → indexed
- `status: "out-of-service-permanently"` or `"incorrect-data"` → deleted from index
- Draft saves → no sync

## Routing

All three public entry points route to the same backend `/v1/*`:

| Entry Point             | nginx Rewrite | X-Forwarded-Prefix |
|-------------------------|---------------|--------------------|
| `api.lipas.fi/v1/*`     | direct        | `/v1`              |
| `lipas.fi/rest/api/*`   | → `/v1/*`     | `/rest/api`        |
| `lipas.cc.jyu.fi/api/*` | → `/v1/*`     | `/api`             |

The `X-Forwarded-Prefix` header controls Link header generation for pagination.

### Link Header Example

Request to `lipas.fi/rest/api/sports-places?pageSize=5`:
```
Link: </rest/api/sports-places/?pageSize=5&page=2>; rel="next", ...
```

## Pagination

- **Complete results**: `200 OK`
- **Partial results**: `206 Partial Content` with headers:
  - `X-total-count`: Total number of matching items
  - `Link`: RFC 5988 links (`first`, `prev`, `next`, `last`)

## Tests

| Test Suite    | Namespace                                     | Description                           |
|---------------|-----------------------------------------------|---------------------------------------|
| Handler tests | `lipas.backend.api.v1.handler-test`           | Endpoint tests                        |
| Golden files  | `lipas.backend.api.v1.golden-files-test`      | Validates against 1000 prod responses |
| Transform     | `lipas.backend.api.v1.transform-test`         | Unit tests for transformation         |
| Integration   | `lipas.backend.api.v1.integration-test`       | DB → ES → API pipeline                |
| HTTP          | `lipas.backend.api.v1.http-test`              | Pagination, base path                 |
| Index sync    | `lipas.backend.api.v1.legacy-index-sync-test` | Real-time sync                        |

```bash
# Run all V1 API tests
bb test-ns lipas.backend.api.v1.handler-test \
           lipas.backend.api.v1.golden-files-test \
           lipas.backend.api.v1.transform-test \
           lipas.backend.api.v1.integration-test \
           lipas.backend.api.v1.http-test \
           lipas.backend.api.v1.legacy-index-sync-test
```

## Known Limitations

1. **Field selection**: Sparse responses with `?fields=` don't match the full Malli schema (schema validation tests are skipped for these)

2. **Additional properties**: New properties not in the original legacy API may be included. This is not a breaking change.

3. **Coordinate precision**: Minor floating-point differences in TM35FIN coordinates compared to the original implementation.
