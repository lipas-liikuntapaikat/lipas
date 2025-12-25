# Elasticsearch & Search

This document covers LIPAS's Elasticsearch infrastructure, indexing strategy, and search APIs.

## Architecture Overview

```
┌─────────────────────┐     ┌──────────────────────┐
│   PostgreSQL DB     │     │    Elasticsearch     │
│                     │     │                      │
│  sports_site table  │────▶│  sports_sites_current│ (alias)
│  (append-only log)  │     │  legacy_sports_sites │ (V1 API)
│                     │     │  analytics           │
│  sports_site_current│     │  lois                │
│  (view: latest)     │     │  schools, population │
└─────────────────────┘     └──────────────────────┘
         │                            │
         │    Enrichment Process      │
         └────────────────────────────┘
```

The database is the **source of truth**. Elasticsearch serves as a **read-optimized cache** with enriched, denormalized data for fast queries.

## Index Structure

All index names are configured in `src/clj/lipas/backend/config.clj`:

| Index Alias                   | Purpose                               | Mapping Key           |
|-------------------------------|---------------------------------------|-----------------------|
| `sports_sites_current`        | Main sports site search               | `:sports-sites`       |
| `legacy_sports_sites_current` | V1 API compatibility format           | `:legacy-sports-site` |
| `analytics`                   | All revisions for analytics           | `:sports-sites`       |
| `lois`                        | Locations of Interest                 | `:lois`               |
| `schools`                     | School data for reachability analysis | —                     |
| `vaestoruutu_1km`             | Population grid (1km resolution)      | `:population-grid`    |
| `vaestoruutu_250m`            | Population grid (250m resolution)     | `:population-grid`    |
| `diversity`                   | Pre-calculated diversity indices      | custom                |
| `subsidies`                   | Subsidy reporting data                | —                     |
| `city_stats`                  | City-level statistics                 | —                     |

### Aliasing Strategy

Indexes use aliases for zero-downtime reindexing:
1. Create new index with timestamp suffix (e.g., `search-2024-01-15t10-30-00`)
2. Index all documents to new index
3. Atomically swap alias to new index
4. Delete old index

## Mappings

Mappings are defined in `src/clj/lipas/backend/search.clj`:

### Sports Sites Mapping

```clojure
{:settings
 {:max_result_window 60000
  :index {:mapping {:total_fields {:limit 2000}}}}
 :mappings
 {:properties
  {:search-meta.location.wgs84-point  {:type "geo_point"}
   :search-meta.location.wgs84-center {:type "geo_point"}
   :search-meta.location.wgs84-end    {:type "geo_point"}
   :search-meta.location.geometries   {:type "geo_shape"}}}}
```

Key fields are explicitly mapped; others use dynamic mapping (keyword/text).

### Legacy Sports Sites Mapping

```clojure
{:mappings
 {:properties
  {:location.coordinates.wgs84 {:type "geo_point"}
   :location.geom-coll         {:type "geo_shape"}
   :lastModified               {:type "date"
                                :format "yyyy-MM-dd HH:mm:ss.SSS"}}}}
```

### LOIs Mapping

```clojure
{:settings {:max_result_window 50000}
 :mappings
 {:properties
  {:search-meta.location.wgs84-point {:type "geo_point"}
   :search-meta.location.geometries  {:type "geo_shape"}}}}
```

## Enrichment Process

Documents are enriched before indexing to add computed fields for search and filtering.

### Core Enrichment (`src/clj/lipas/backend/core.clj`)

The `enrich*` function adds a `:search-meta` key with:

```clojure
{:search-meta
 {:name          "sortable-name"        ; Normalized for sorting
  :admin         {:name "Kunta"}        ; Resolved from code
  :owner         {:name "Yksityinen"}   ; Resolved from code
  :audits        {:latest-audit-date}   ; Most recent audit
  :location
  {:wgs84-point   [lon lat]             ; Start/first coordinate
   :wgs84-center  [lon lat]             ; Geometry centroid
   :wgs84-end     [lon lat]             ; End coordinate (routes)
   :geometries    {...}                 ; GeoJSON for geo_shape
   :city          {:name "Helsinki"}    ; Resolved city name
   :province      {:name "Uusimaa"}     ; Resolved province
   :avi-area      {:name "..."}}        ; Resolved AVI area
  :type
  {:name          "Jäähalli"            ; Type name
   :tags          ["jää" "sisä"]        ; Type tags
   :main-category {:name "Jääurheilupaikat"}
   :sub-category  {:name "..."}}
  :fields
  {:field-types ["jääkiekkokaukalo"]}}} ; Distinct field types
```

### Type-Specific Enrichment

Multimethod dispatches on type-code for custom enrichment:

```clojure
(defmulti enrich (comp :type-code :type))
(defmethod enrich :default [sports-site] (enrich* sports-site))
(defmethod enrich 4412 [sports-site] (enrich-cycling-route sports-site))
```

### Data Flow

```
1. Save sports-site!
   ↓
2. Insert to sports_site table (new revision)
   ↓
3. core/enrich - Add :search-meta with resolved lookups
   ↓
4. search/index! - Send to sports_sites_current (sync or async)
   ↓
5. If active status: Also index to legacy_sports_sites_current
```

## Query Patterns

### Basic Search

```clojure
(search/search client "sports_sites_current"
  {:size 100
   :query {:bool {:filter [{:term {:status.keyword "active"}}]}}})
```

### Full-Text with Field Boosting

```clojure
{:query
 {:multi_match
  {:query    "Uimahalli Helsinki"
   :fields   ["name^10"
              "type.name^5"
              "marketingName^3"
              "location.address^2"
              "location.city.name^2"
              "*"]
   :type     "best_fields"
   :operator "and"}}}
```

### Geo Queries

**Geo Distance (proximity search):**
```clojure
{:query
 {:bool
  {:filter
   {:geo_distance
    {:distance "10km"
     :search-meta.location.wgs84-point [25.0 60.2]}}}}}
```

**Geo Shape (polygon search):**
```clojure
{:query
 {:bool
  {:filter
   {:geo_shape
    {:search-meta.location.geometries
     {:shape    {:type "polygon" :coordinates [...]}
      :relation "intersects"}}}}}}
```

### Pagination

```clojure
{:from (* (dec page) page-size)
 :size page-size
 :track_total_hits 60000
 :_source {:includes [:lipas-id :name :type :location]}}
```

### Aggregations (Heatmap)

```clojure
{:size 0
 :query {:bool {:filter [...]}}
 :aggs
 {:grid
  {:geohash_grid {:field "search-meta.location.wgs84-point"
                  :precision 5
                  :size 50000}
   :aggs {:centroid {:geo_centroid
                     {:field "search-meta.location.wgs84-point"}}}}}}
```

## Reindexing

### Full Reindex (`src/clj/lipas/search_indexer.clj`)

```bash
# From REPL
(require '[lipas.search-indexer :as indexer])
(indexer/main system db search "search")   ; Main index
(indexer/main system db search "legacy")   ; Legacy V1 index
(indexer/main system db search "analytics") ; All revisions
```

### Process

1. **Create timestamped index**: `search-2024-01-15t10-30-00`
2. **Bulk index by type-code**: Iterates all ~250 type codes
3. **Swap alias**: Atomically point alias to new index
4. **Delete old indexes**: Clean up previous indexes

### Bulk Indexing

```clojure
(->> sports-sites
     (map core/enrich)
     (search/->bulk "sports_sites_current" :lipas-id)
     (search/bulk-index! client))
```

Configuration:
- `flush-threshold`: 100 documents per batch
- `flush-interval`: 5000ms
- `max-concurrent-requests`: 3

### Single Document Index

```clojure
;; Async (default)
(core/index! search sports-site)

;; Sync (waits for refresh)
(core/index! search sports-site true)
```

## Search APIs

### V1 API (`/v1/*`)

Legacy format for backwards compatibility.

| Endpoint | Description |
|----------|-------------|
| `GET /v1/sports-places` | Paginated list with filters |
| `GET /v1/sports-places/:id` | Single place by ID |
| `GET /v1/deleted-sports-places` | Deleted since timestamp |
| `GET /v1/sports-place-types` | All facility types |
| `GET /v1/categories` | Type categories |

Query params: `search-string`, `cityCode`, `typeCode`, `closeTo`, `pageSize`, `page`

### V2 API (`/v2/*`)

Modern format with full data model.

| Endpoint | Description |
|----------|-------------|
| `GET /v2/sports-sites` | Paginated sports sites |
| `GET /v2/sports-sites/:lipas-id` | Single site by ID |
| `GET /v2/lois` | Paginated LOIs |
| `GET /v2/lois/:loi-id` | Single LOI by ID |
| `GET /v2/sports-site-categories` | All categories/types |

Query params: `page`, `pageSize`, `statuses`, `typeCodes`, `cityCodes`, `admins`, `owners`, `activities`

### Internal Search Actions

| Endpoint | Purpose |
|----------|---------|
| `POST /actions/search-schools` | School reachability analysis |
| `POST /actions/search-population` | Population grid queries |
| `POST /actions/calc-diversity-indices` | Diversity calculations |
| `POST /actions/search-lois` | LOI search |

## Key Files

| File | Purpose |
|------|---------|
| `src/clj/lipas/backend/search.clj` | ES client, mappings, bulk ops |
| `src/clj/lipas/search_indexer.clj` | Full reindexing logic |
| `src/clj/lipas/backend/core.clj` | Enrichment functions |
| `src/clj/lipas/backend/api/v1/search.clj` | V1 API query builders |
| `src/clj/lipas/backend/api/v2.clj` | V2 API handlers |
| `src/clj/lipas/backend/analysis/heatmap.clj` | Heatmap aggregations |
| `src/clj/lipas/backend/analysis/common.clj` | Geo analysis queries |

## Development Notes

### Verify Index Exists

```clojure
(search/index-exists? client "sports_sites_current")
```

### Check Current Alias Targets

```clojure
(search/current-idxs client {:alias "sports_sites_current"})
;; => ["search-2024-01-15t10-30-00"]
```

### Debug Query

```clojure
(search/search client "sports_sites_current"
  {:size 0
   :track_total_hits true
   :query {:match_all {}}})
;; Check :hits :total :value for document count
```

### Scroll Large Result Sets

```clojure
(let [ch (search/scroll client "sports_sites_current"
           {:size 1000
            :query {:match_all {}}})]
  (loop []
    (when-let [batch (async/<!! ch)]
      (process-batch batch)
      (recur))))
```
