# Elasticsearch Explicit Mapping Proposal for sports_sites_current

**Date:** 2025-01-29
**Updated:** 2025-01-29 (Critical amendments based on deep analysis)
**Status:** Proposal (Revised)
**Purpose:** Define explicit field mappings to replace dynamic mapping and improve index performance/clarity

---

## ⚠️ CRITICAL AMENDMENTS

After deeper investigation, THREE major issues were identified that change the approach:

1. **Scandinavian Sorting (ÄÖÅ)** - Need ICU collation for proper Finnish/Swedish alphabetical order
2. **Activities Bloat** - Cannot use dynamic mapping; activities contain huge nested structures with geo data
3. **Properties Complete Mapping** - ALL 181 properties must be explicitly mapped (derived from `prop_types.cljc`)

---

## Executive Summary

After comprehensive analysis of all Elasticsearch queries across:
- V2 API
- V1 API (queries sports_sites_current, not legacy index)
- Reports functionality
- Analysis tools (heatmap, diversity, reachability)
- Property type definitions (`lipas.data.prop-types`)

We identified **~260 fields that must be explicitly indexed**:
- 71 core/search-meta fields
- 181 properties fields (from prop_types.cljc)
- 7 activity-related fields
- Plus multilingual variants

**Key Benefits:**
- Correct Scandinavian letter sorting (ÄÖÅ after Z)
- Zero index bloat from activities nested content
- Type-safe property filtering (all 181 properties mapped correctly)
- Smaller index size (only index what's queried)
- Faster indexing (skip analysis of unused fields)
- Clearer documentation (mapping shows exactly what's searchable)
- Better type control (prevent dynamic mapping conflicts)
- ES 8.x compatibility (avoid dense_vector inference issues)

---

## 1. Current vs. Proposed Mapping

### Current State
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

**Problems:**
- Only 4 fields explicitly mapped
- 1996 fields allowed via dynamic mapping
- No control over `.keyword` suffix creation
- Potential type inference conflicts
- All text fields get analyzed (performance cost)

### Proposed State
```clojure
{:settings
 {:max_result_window 60000
  :index {:mapping {:total_fields {:limit 300}}}}  ; Reduced from 2000

 :mappings
 {:dynamic "strict"  ; Reject unmapped fields in queries
  :properties {
    ; All ~260 fields explicitly defined below
    ; - 71 core/search-meta fields
    ; - 181 properties.* fields (from prop_types.cljc)
    ; - Activity fields (search-meta.activities array)
    ; - Disabled nested: activities.*, *.geometries
  }}}
```

**Benefits of Explicit Over Dynamic:**
- ✅ ICU collation for `search-meta.name` (proper ÄÖÅ sorting)
- ✅ Activities bloat prevented (`enabled: false` on nested content)
- ✅ All 181 properties correctly typed (97 numeric, 67 boolean, 11 string, 6 enum)
- ✅ No dense_vector inference on geometry coordinates
- ✅ Field limit reduced from 2000 to ~300

---

## 2. Critical Issue #1: Scandinavian Letter Sorting

### The Problem

**Current implementation** (`utils.cljc:13-18`):
```clojure
(defn ->sortable-name [s]
  (-> s (str/lower-case) (str/replace #"(\"|\(|\))" "")))
```

**Issue:** No ICU collation. Scandinavian letters Ä/Ö/Å sort as accented A/O instead of after Z.

**Example incorrect sorting:**
- Current: Ähtäri, Örebro, Ankara, Örnsköldsvik (Ä/Ö treated as A/O)
- Correct Finnish/Swedish: Ankara, Ähtäri, Örebro, Örnsköldsvik (Ä/Ö after Z)

### Solution: ICU Collation Plugin

**Requires:** `elasticsearch-plugin install analysis-icu`

**Mapping:**
```clojure
:search-meta {:properties
              {:name {:type "icu_collation_keyword"
                      :language "fi"
                      :country "FI"
                      :variant ""
                      :strength "tertiary"}}}
```

**Benefits:**
- Correct Finnish/Swedish alphabetical order
- ÄÖÅ sort after Z as linguistically expected
- Case-insensitive sorting
- Locale-aware character normalization

**Alternative (if ICU plugin not available):**
Use standard `keyword` field - ES default Unicode ordering is acceptable but not linguistically correct.

---

## 3. Critical Issue #2: Activities Bloat Prevention

### The Problem

**V2 API existence check** (`api/v2.clj:88-89`):
```clojure
activities (into (for [a activities]
                   {:exists {:field (str "activities." a)}}))
```

**Activity schema structure** (from `activities.cljc`):
- 7 activity types: `cycling`, `fishing`, `paddling`, `birdwatching`, `outdoor-recreation-*`
- Each contains **20+ nested fields**:
  - `routes.geometries` (geo_shape - MASSIVE!)
  - `description-short`, `description-long`
  - `rules`, `arrival`, `accessibility`, `contacts`
  - `images`, `videos`, `additional-info-link`
  - Complex nested objects with multilingual content

**If we allow dynamic mapping:**
- 7 types × 20+ fields = 140+ indexed fields
- Includes geometry coordinates (ES 8.x dense_vector inference!)
- Index bloat defeats entire purpose

### Solution: Disable Nested Indexing + Search-Meta Array

**Step 1:** Enrich activities into searchable array (`core.clj`):
```clojure
(defn enrich* [sports-site]
  (let [activity-keys (when-let [acts (:activities sports-site)]
                        (vec (keys acts)))
        search-meta {;; ... existing fields
                     :activities activity-keys}]  ; NEW
    (assoc sports-site :search-meta search-meta)))
```

**Step 2:** Disable activities.* nested indexing:
```clojure
:activities {:type "object"
             :enabled false}  ; Don't index ANY nested content
```

**Step 3:** Map search-meta.activities:
```clojure
:search-meta {:properties
              {:activities {:type "keyword"}}}  ; Array of activity keys
```

**Step 4:** Update V2 API query:
```clojure
;; OLD (won't work):
;; {:exists {:field "activities.cycling"}}

;; NEW:
{:terms {:search-meta.activities ["cycling" "fishing"]}}
```

**Benefits:**
- ✅ Existence checks still work (different query)
- ✅ Full activity content in `_source` for display
- ✅ Zero index bloat from nested fields
- ✅ Geometry coordinates not indexed

---

## 4. Critical Issue #3: Complete Properties Mapping

### The Problem

**Dynamic property filtering** (`ui/search/events.cljs:256-286`):
```clojure
(reduce-kv
  (fn [acc prop-key prop-filter]
    (let [prop-path (keyword (str "properties." (name prop-key)))]
      (case (:type prop-filter)
        :range (add-filter {:range {prop-path {:gte min :lte max}}})
        :boolean (add-filter {:term {prop-path true}})
        :string (add-filter {:wildcard {(str prop-path ".keyword") text}})))))
```

**Users can filter on ANY property!** Cannot use "stored-only" approach.

### Solution: Explicit Mapping from prop_types.cljc

**All 181 properties derived from** `lipas.data.prop-types/all`:

| Data Type | Count | ES Mapping |
|-----------|-------|------------|
| `numeric` | 97 | `{:type "double"}` |
| `boolean` | 67 | `{:type "boolean"}` |
| `string` | 11 | `{:type "text" :fields {:keyword {:type "keyword"}}}` |
| `enum` | 3 | `{:type "keyword"}` |
| `enum-coll` | 3 | `{:type "keyword"}` |
| **TOTAL** | **181** | |

**Mapping structure** (see Section 5 for complete list):
```clojure
:properties {:properties
             {;; Numeric (97 fields)
              :area-m2 {:type "double"}
              :pool-length-m {:type "double"}
              ;; ... 95 more

              ;; Boolean (67 fields)
              :year-round-use? {:type "boolean"}
              :lighting? {:type "boolean"}
              ;; ... 65 more

              ;; String (11 fields)
              :surface-material-info {:type "text" :fields {:keyword {:type "keyword"}}}
              ;; ... 10 more

              ;; Enum/Enum-coll (6 fields)
              :surface-material {:type "keyword"}
              ;; ... 5 more
              }}
```

**Benefits:**
- ✅ All properties queryable (range, boolean, wildcard)
- ✅ Type-safe (correct ES types for each property)
- ✅ No dynamic mapping needed
- ✅ Schema alignment with Malli definitions

---

## 5. Complete Field Inventory

### 2.1 Geographic Fields (CRITICAL - geo_point & geo_shape)

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `search-meta.location.wgs84-point` | geo_point | Heatmap geohash agg, bbox filters, distance scoring | **Most critical geo field** |
| `search-meta.location.wgs84-center` | geo_point | Alternative centroid for routes | Used in some analysis |
| `search-meta.location.wgs84-end` | geo_point | End point for route geometries | Route-specific |
| `search-meta.location.geometries` | geo_shape | Buffer-based geo_shape queries (reachability, diversity) | Used with `intersects` relation |

**ES 8.x Note:** Must disable indexing on raw GeoJSON coordinates to prevent `dense_vector` inference:
```clojure
:location.geometries {:enabled false}
:search-meta.location.simple-geoms {:enabled false}
:activities.*.routes.geometries {:enabled false}
```

---

### 2.2 Core Identification & Status Fields

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `lipas-id` | integer | Document ID, filtering | Primary key |
| `status` | keyword | Filter published/draft status | **Used in every query** |
| `event-date` | date | Sort deleted sites, temporal queries | Format: ISO8601 |

**Mapping:**
```clojure
:lipas-id {:type "integer"}
:status {:type "keyword"}
:event-date {:type "date"}
```

---

### 2.3 Type Classification Fields

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `type.type-code` | integer | Filter by facility type | **Used in almost all queries** |
| `search-meta.type.name.fi` | text + keyword | Text search + exact match | Multi-field |
| `search-meta.type.name.se` | text + keyword | Swedish search + exact | Multi-field |
| `search-meta.type.name.en` | text + keyword | English search + exact | Multi-field |
| `search-meta.type.tags.fi` | keyword | Tag-based filtering | Array of tags |
| `search-meta.type.tags.se` | keyword | Swedish tags | Array |
| `search-meta.type.tags.en` | keyword | English tags | Array |
| `search-meta.type.main-category.name.fi` | text + keyword | Main category filter | Multi-field |
| `search-meta.type.main-category.name.se` | text + keyword | Swedish category | Multi-field |
| `search-meta.type.main-category.name.en` | text + keyword | English category | Multi-field |
| `search-meta.type.sub-category.name.fi` | text + keyword | Sub-category filter | Multi-field |
| `search-meta.type.sub-category.name.se` | text + keyword | Swedish sub-category | Multi-field |
| `search-meta.type.sub-category.name.en` | text + keyword | English sub-category | Multi-field |

**Mapping Pattern (example):**
```clojure
:search-meta.type.name.fi
{:type "text"
 :fields {:keyword {:type "keyword"}}}
```

---

### 2.4 Location Fields

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `location.city.city-code` | integer | Filter by municipality | **Heavily used** |
| `search-meta.location.city.name.fi` | text + keyword | City name search | Multi-field |
| `search-meta.location.city.name.se` | text + keyword | Swedish city name | Multi-field |
| `search-meta.location.city.name.en` | text + keyword | English city name | Multi-field |
| `search-meta.location.province.name.fi` | text + keyword | Province filter | Multi-field |
| `search-meta.location.province.name.se` | text + keyword | Swedish province | Multi-field |
| `search-meta.location.province.name.en` | text + keyword | English province | Multi-field |
| `search-meta.location.avi-area.name.fi` | text + keyword | AVI area filter | Multi-field |
| `search-meta.location.avi-area.name.se` | text + keyword | Swedish AVI | Multi-field |
| `search-meta.location.avi-area.name.en` | text + keyword | English AVI | Multi-field |

---

### 2.5 Name & Description Fields

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `name` | text + keyword | Primary search field (10x boost) | **Most important text field** |
| `marketing-name` | text + keyword | Marketing name search (3x boost) | Optional field |
| `name-localized.se` | text + keyword | Swedish name (3x boost) | Optional |
| `name-localized.en` | text + keyword | English name (3x boost) | Optional |
| `search-meta.name` | keyword | Sortable name | Normalized for sorting |

---

### 2.6 Ownership & Administration

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `admin` | keyword | Filter by administrator | **Heavily used in filters** |
| `owner` | keyword | Filter by owner | **Heavily used in filters** |
| `search-meta.admin.name` | keyword | Resolved admin name | For display |
| `search-meta.owner.name` | keyword | Resolved owner name | For display |

---

### 2.7 Temporal Fields

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `construction-year` | integer | Range queries, aggregations | Year between 1800-2035 |
| `search-meta.audits.latest-audit-date` | date | Sort/filter by audit date | Optional |

---

### 2.8 Properties Fields (All 181 Properties)

**All properties from `lipas.data.prop-types/all` are explicitly mapped based on their `:data-type`.**

This ensures the UI feature for filtering on arbitrary properties (see `src/cljs/lipas/ui/search/events.cljs:256-286`) works correctly without dynamic mapping.

#### Numeric Properties (97 fields)

Mapped as `{:type "double"}` in Elasticsearch.

| Field Path | ES Type |
|-----------|---------|
| `properties.active-space-length-m` | double |
| `properties.active-space-width-m` | double |
| `properties.altitude-difference` | double |
| `properties.area-km2` | double |
| `properties.area-m2` | double |
| `properties.badminton-courts-count` | double |
| `properties.basketball-fields-count` | double |
| `properties.beach-length-m` | double |
| `properties.boat-places-count` | double |
| `properties.bowling-lanes-count` | double |
| `properties.boxing-rings-count` | double |
| `properties.carom-tables-count` | double |
| `properties.changing-rooms-m2` | double |
| `properties.circular-lanes-count` | double |
| `properties.climbing-routes-count` | double |
| `properties.climbing-wall-height-m` | double |
| `properties.climbing-wall-width-m` | double |
| `properties.covered-stand-person-count` | double |
| `properties.curling-lanes-count` | double |
| `properties.discus-throw-places` | double |
| `properties.exercise-machines-count` | double |
| `properties.fencing-bases-count` | double |
| `properties.field-1-area-m2` | double |
| `properties.field-1-length-m` | double |
| `properties.field-1-width-m` | double |
| `properties.field-2-area-m2` | double |
| `properties.field-2-length-m` | double |
| `properties.field-2-width-m` | double |
| `properties.field-3-area-m2` | double |
| `properties.field-3-length-m` | double |
| `properties.field-3-width-m` | double |
| `properties.field-length-m` | double |
| `properties.field-width-m` | double |
| `properties.fields-count` | double |
| `properties.fitness-stairs-length-m` | double |
| `properties.floorball-fields-count` | double |
| `properties.football-fields-count` | double |
| `properties.futsal-fields-count` | double |
| `properties.group-exercise-rooms-count` | double |
| `properties.gymnastic-routines-count` | double |
| `properties.halfpipe-count` | double |
| `properties.hammer-throw-places-count` | double |
| `properties.handball-fields-count` | double |
| `properties.height-m` | double |
| `properties.highest-obstacle-m` | double |
| `properties.highjump-places-count` | double |
| `properties.holes-count` | double |
| `properties.hs-point` | double |
| `properties.ice-rinks-count` | double |
| `properties.inner-lane-length-m` | double |
| `properties.javelin-throw-places-count` | double |
| `properties.jumps-count` | double |
| `properties.k-point` | double |
| `properties.kaisa-tables-count` | double |
| `properties.landing-places-count` | double |
| `properties.lifts-count` | double |
| `properties.lit-route-length-km` | double |
| `properties.lit-slopes-count` | double |
| `properties.longest-slope-m` | double |
| `properties.longjump-places-count` | double |
| `properties.max-vertical-difference` | double |
| `properties.other-pools-count` | double |
| `properties.p-point` | double |
| `properties.padel-courts-count` | double |
| `properties.polevault-places-count` | double |
| `properties.pool-length-m` | double |
| `properties.pool-max-depth-m` | double |
| `properties.pool-min-depth-m` | double |
| `properties.pool-tables-count` | double |
| `properties.pool-temperature-c` | double |
| `properties.pool-tracks-count` | double |
| `properties.pool-water-area-m2` | double |
| `properties.pool-width-m` | double |
| `properties.pyramid-tables-count` | double |
| `properties.rest-places-count` | double |
| `properties.route-length-km` | double |
| `properties.route-width-m` | double |
| `properties.shooting-positions-count` | double |
| `properties.shortest-slope-m` | double |
| `properties.shotput-count` | double |
| `properties.slopes-count` | double |
| `properties.snooker-tables-count` | double |
| `properties.space-divisible` | double |
| `properties.sprint-lanes-count` | double |
| `properties.sprint-track-length-m` | double |
| `properties.squash-courts-count` | double |
| `properties.stand-capacity-person` | double |
| `properties.swimming-pool-count` | double |
| `properties.table-tennis-count` | double |
| `properties.tatamis-count` | double |
| `properties.tennis-courts-count` | double |
| `properties.total-billiard-tables-count` | double |
| `properties.track-length-m` | double |
| `properties.track-width-m` | double |
| `properties.volleyball-fields-count` | double |
| `properties.weight-lifting-spots-count` | double |
| `properties.wrestling-mats-count` | double |

#### Boolean Properties (67 fields)

Mapped as `{:type "boolean"}` in Elasticsearch.

| Field Path | ES Type |
|-----------|---------|
| `properties.activity-service-company?` | boolean |
| `properties.air-gun-shooting?` | boolean |
| `properties.automated-scoring?` | boolean |
| `properties.automated-timing?` | boolean |
| `properties.auxiliary-training-area?` | boolean |
| `properties.bike-orienteering?` | boolean |
| `properties.boat-launching-spot?` | boolean |
| `properties.canoeing-club?` | boolean |
| `properties.changing-rooms?` | boolean |
| `properties.climbing-wall?` | boolean |
| `properties.cosmic-bowling?` | boolean |
| `properties.customer-service-point?` | boolean |
| `properties.equipment-rental?` | boolean |
| `properties.eu-beach?` | boolean |
| `properties.field-1-flexible-rink?` | boolean |
| `properties.field-2-flexible-rink?` | boolean |
| `properties.field-3-flexible-rink?` | boolean |
| `properties.finish-line-camera?` | boolean |
| `properties.free-customer-use?` | boolean |
| `properties.free-rifle-shooting?` | boolean |
| `properties.free-use?` | boolean |
| `properties.freestyle-slope?` | boolean |
| `properties.green?` | boolean |
| `properties.gymnastics-space?` | boolean |
| `properties.heating?` | boolean |
| `properties.height-of-basket-or-net-adjustable?` | boolean |
| `properties.ice-climbing?` | boolean |
| `properties.ice-reduction?` | boolean |
| `properties.kiosk?` | boolean |
| `properties.light-roof?` | boolean |
| `properties.ligthing?` | boolean |
| `properties.loudspeakers?` | boolean |
| `properties.match-clock?` | boolean |
| `properties.may-be-shown-in-excursion-map-fi?` | boolean |
| `properties.may-be-shown-in-harrastuspassi-fi?` | boolean |
| `properties.mirror-wall?` | boolean |
| `properties.mobile-orienteering?` | boolean |
| `properties.other-platforms?` | boolean |
| `properties.outdoor-exercise-machines?` | boolean |
| `properties.parking-place?` | boolean |
| `properties.pier?` | boolean |
| `properties.pistol-shooting?` | boolean |
| `properties.plastic-outrun?` | boolean |
| `properties.playground?` | boolean |
| `properties.range?` | boolean |
| `properties.rapid-canoeing-centre?` | boolean |
| `properties.rifle-shooting?` | boolean |
| `properties.ringette-boundary-markings?` | boolean |
| `properties.sauna?` | boolean |
| `properties.school-use?` | boolean |
| `properties.scoreboard?` | boolean |
| `properties.shotgun-shooting?` | boolean |
| `properties.show-jumping?` | boolean |
| `properties.shower?` | boolean |
| `properties.ski-orienteering?` | boolean |
| `properties.ski-service?` | boolean |
| `properties.ski-track-freestyle?` | boolean |
| `properties.ski-track-traditional?` | boolean |
| `properties.sledding-hill?` | boolean |
| `properties.snowpark-or-street?` | boolean |
| `properties.spinning-hall?` | boolean |
| `properties.summer-usage?` | boolean |
| `properties.toboggan-run?` | boolean |
| `properties.toilet?` | boolean |
| `properties.training-wall?` | boolean |
| `properties.winter-usage?` | boolean |
| `properties.year-round-use?` | boolean |

#### String Properties (11 fields)

Mapped as `{:type "text" :fields {:keyword {:type "keyword"}}}` for both full-text search and exact filtering.

| Field Path | ES Type |
|-----------|---------|
| `properties.accessibility-info` | text with keyword subfield |
| `properties.basketball-field-type` | text with keyword subfield |
| `properties.inruns-material` | text with keyword subfield |
| `properties.lighting-info` | text with keyword subfield |
| `properties.running-track-surface-material` | text with keyword subfield |
| `properties.skijump-hill-material` | text with keyword subfield |
| `properties.skijump-hill-type` | text with keyword subfield |
| `properties.surface-material-info` | text with keyword subfield |
| `properties.track-type` | text with keyword subfield |
| `properties.training-spot-surface-material` | text with keyword subfield |
| `properties.travel-mode-info` | text with keyword subfield |

#### Enum Properties (3 fields)

Mapped as `{:type "keyword"}` for exact filtering.

| Field Path | ES Type |
|-----------|---------|
| `properties.boating-service-class` | keyword |
| `properties.sport-specification` | keyword |
| `properties.water-point` | keyword |

#### Enum Collection Properties (3 fields)

Mapped as `{:type "keyword"}` (arrays of enums).

| Field Path | ES Type |
|-----------|---------|
| `properties.parkour-hall-equipment-and-structures` | keyword |
| `properties.surface-material` | keyword |
| `properties.travel-modes` | keyword |

---

### 2.9 Activities Fields

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `activities` | object | Activity data storage | **Indexing disabled** (`{:enabled false}`) |
| `search-meta.activities` | keyword | Activity existence checks | Array of activity keys for filtering |

**Critical Prevention of Index Bloat:**

The `activities` field contains complex nested structures with full geometries. If indexed dynamically, this would massively bloat the index with hundreds of unused fields.

**Solution:**
1. Disable indexing on `activities`: `{:activities {:enabled false}}`
2. Add `search-meta.activities` array during enrichment containing just the activity keys
3. Update V2 API to use `{:terms {:search-meta.activities [...]}}` instead of `{:exists {:field "activities.X"}}`

This prevents index bloat while maintaining the activity filtering feature.

---

### 2.10 Fields Used in Text Search (Multi-Match)

These fields need text analysis for search but also `.keyword` for exact matching:

```clojure
:comment {:type "text" :fields {:keyword {:type "keyword"}}}
:email {:type "text" :fields {:keyword {:type "keyword"}}}
:phone-number {:type "text" :fields {:keyword {:type "keyword"}}}
:location.address {:type "text" :fields {:keyword {:type "keyword"}}}
:location.postal-office {:type "text" :fields {:keyword {:type "keyword"}}}
:location.postal-code {:type "text" :fields {:keyword {:type "keyword"}}}
:location.neighborhood {:type "text" :fields {:keyword {:type "keyword"}}}
```

---

### 2.11 Search-Meta Additional Fields

| Field Path | Type | Usage | Notes |
|-----------|------|-------|-------|
| `search-meta.fields.field-types` | keyword | Field type filtering | Array of field type keys |
| `search-meta.tags` | keyword | General tag search | Array |

---

## 3. Fields That Are STORED ONLY (Not Indexed)

These fields are returned in `_source` for display/export but don't need indexing:

```
# Basic Fields (stored but not indexed):
- www
- reservations-link
- renovation-years (array of years)
- circumstances (complex object)
- fields (array of field objects)
- locker-rooms (complex object)
- audits (array of audit objects)

# Location Fields (coordinates, not geo queries):
- location.geometries (raw GeoJSON - indexing disabled)
- search-meta.location.simple-geoms (simplified geoms - indexing disabled)

# Activities (complex nested objects with geometries):
- activities (indexing disabled to prevent bloat - see section 2.9)
```

**Why disable indexing for geometries?**
- ES 8.x tries to infer `dense_vector` type for coordinate arrays
- We already index geo fields as `geo_point` and `geo_shape`
- Saves index size and prevents type conflicts

---

## 4. Complete Proposed Mapping

The complete explicit mapping has been extracted to a data file for easier maintenance and programmatic use:

**File:** `resources/elasticsearch/sports-sites-explicit-mapping.edn`

This mapping is generated programmatically from `lipas.data.prop-types/all` to ensure consistency with the schema definitions.

**Key Features:**

1. **ICU Collation Analyzers** - Finnish and Swedish alphabetical sorting (Ä, Ö, Å after Z)
2. **Dynamic "strict"** - Rejects queries on unmapped fields
3. **All 181 Properties** - Explicitly mapped based on `:data-type` from prop-types
4. **Activities Bloat Prevention** - Indexing disabled on `activities` field
5. **search-meta.activities** - Array of activity keys for filtering
6. **Sortable Fields** - Use `.sort` subfield with ICU collation normalizers

**Total Fields:** ~260 explicitly mapped fields

**Property Mappings by Type:**
- 97 numeric properties → `{:type "double"}`
- 67 boolean properties → `{:type "boolean"}`
- 11 string properties → `{:type "text" :fields {:keyword {:type "keyword"}}}`
- 3 enum properties → `{:type "keyword"}`
- 3 enum-coll properties → `{:type "keyword"}`

**Usage Example:**

```clojure
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io])

(def sports-sites-mapping
  (edn/read-string
    (slurp (io/resource "elasticsearch/sports-sites-explicit-mapping.edn"))))

;; Use in search.clj when creating the index
(search/create-index! client "sports_sites_current" sports-sites-mapping)
```

---

## 5. Migration Strategy

**Direct approach: Go immediately to strict mode and fix missing mappings through tests.**

### Step 1: Create Strict-Mode Index
```clojure
;; Load the mapping from EDN
(def mapping (edn/read-string
               (slurp (io/resource "elasticsearch/sports-sites-explicit-mapping.edn"))))

;; Create new index with strict mode enabled
(search/create-index! client "sports_sites_strict_test" mapping)
```

### Step 2: Reindex All Documents
```clojure
;; Reindex from current index to strict-mode index
(indexer/reindex-all! db search "sports_sites_strict_test")
```

**Expected outcome:** Any unmapped fields will cause indexing errors immediately, revealing gaps in the mapping.

### Step 3: Run Full Test Suite
```bash
# Run all integration tests
bb test-ns lipas.backend.api.v2-test
bb test-ns lipas.backend.analysis.heatmap-test
bb test-ns lipas.backend.core-test
# ... etc
```

**Expected outcome:** Tests will fail if queries use unmapped fields. Error messages will clearly show:
```
"Elasticsearch error: no such field [field-name]"
```

### Step 4: Fix Missing Mappings (Iterative)
When tests reveal missing fields:

1. Add the field to `sports-sites-explicit-mapping.edn`
2. Regenerate the EDN file if needed (if field should come from prop-types)
3. Delete and recreate the test index
4. Reindex
5. Re-run tests
6. Repeat until all tests pass

### Step 5: Deploy to Production
Once all tests pass with strict mode:

1. Create the production index with strict mapping
2. Reindex production data
3. Atomically swap alias to new index
4. Monitor for any errors
5. Delete old index after verification

### Benefits of Direct Approach
- **No intermediate state** - cleaner migration path
- **Tests as validation** - comprehensive test suite catches all missing fields
- **Fail fast** - issues surface immediately during reindexing
- **Confidence** - if tests pass, production will work

---

## 6. Testing Strategy

**Approach: Extend existing integration tests to cover all query patterns with strict-mode index.**

### Philosophy

The strict-mode mapping will be validated through **comprehensive integration tests** that exercise all real query patterns. Missing field mappings will surface as Elasticsearch errors during test execution, making them easy to identify and fix.

### Test Pattern (Following `lipas.backend.org-test`)

Use test-system with strict-mode index:

```clojure
(ns lipas.backend.search-strict-test
  (:require [clojure.test :refer :all]
            [lipas.test-utils :as test-utils]))

(use-fixtures :once test-utils/test-system-fixture)

(deftest strict-mapping-loads-correctly
  (testing "Strict mapping can be loaded and applied"
    (let [mapping (test-utils/load-strict-mapping)]
      (is (= "strict" (get-in mapping [:mappings :dynamic])))
      (is (= 181 (count (get-in mapping [:mappings :properties :properties :properties])))))))

(deftest all-queries-work-with-strict-mapping
  (testing "V2 API queries work with strict mapping"
    ;; These tests will fail with clear error messages if fields are missing
    (test-v2-sports-sites-query)
    (test-v2-with-all-filters)
    (test-v2-sorting))

  (testing "Heatmap queries work with strict mapping"
    (test-heatmap-aggregations)
    (test-heatmap-filters))

  (testing "Analysis queries work with strict mapping"
    (test-diversity-analysis)
    (test-reachability-analysis))

  (testing "Reports work with strict mapping"
    (test-excel-export)
    (test-csv-export)
    (test-geojson-export)))
```

### Extend Existing Test Namespaces

**Pattern:** Add strict-mode variants to existing integration tests

1. **`test/clj/lipas/backend/api/v2_test.clj`**
   - Add tests for all filter combinations
   - Test activity filtering (using new `search-meta.activities`)
   - Test property filtering (all 181 properties)

2. **`test/clj/lipas/backend/analysis/heatmap_test.clj`**
   - Add tests for all dimensions
   - Test all boolean property filters
   - Test geo queries on all geo fields

3. **`test/clj/lipas/backend/core_test.clj`**
   - Test enrichment adds `search-meta.activities`
   - Test indexing with strict mapping succeeds

4. **Create `test/clj/lipas/backend/reports_test.clj`**
   - Test scroll API with field selection
   - Test all export formats

### Critical Query Patterns to Test

Based on the audit, ensure these patterns are tested:

```clojure
;; V2 API - Activity filtering (NEW query pattern with search-meta.activities)
{:query {:bool {:filter [{:terms {:search-meta.activities ["swimming" "skating"]}}]}}}

;; V2 API - Property range filtering
{:query {:bool {:filter [{:range {:properties.area-m2 {:gte 100 :lte 500}}}]}}}

;; V2 API - Property boolean filtering
{:query {:bool {:filter [{:term {:properties.school-use? true}}]}}}

;; Heatmap - All dimensions
{:aggs {:grid {:geohash_grid {:field "search-meta.location.wgs84-point" :precision 5}}}}

;; Reports - Field selection with scroll
{:_source {:includes ["lipas-id" "name" "properties.area-m2"]}}

;; Analysis - Geo shape queries
{:query {:bool {:filter [{:geo_shape {:search-meta.location.geometries
                                      {:shape {...} :relation "intersects"}}}]}}}
```

### Manual Testing Checklist (Post-Integration)

- [ ] V2 API: Sports sites list with all filters
- [ ] V2 API: LOIs list with filters
- [ ] V1 API: Legacy sports places query
- [ ] V1 API: Deleted places query
- [ ] Reports: Excel export with >100 fields
- [ ] Reports: CSV export with >10,000 results
- [ ] Reports: GeoJSON export
- [ ] Heatmap: All dimensions (density, area, type-distribution, year-round, lighting, activities)
- [ ] Heatmap: All filters (status, type, city, year, admin, owner, surface-material, boolean props)
- [ ] Diversity: Grid fetch with both euclidean and route distances
- [ ] Diversity: Pre-calculated grid query
- [ ] Reachability: Sports sites fetch with type filter
- [ ] Reachability: Population grid fetch (1km and 250m)
- [ ] Reachability: Schools fetch

---

## 7. Expected Benefits

### Index Size Reduction
- **Before:** ~2000 potential fields, all text fields get `.keyword` multi-field
- **After:** 71 explicitly indexed fields, rest stored-only
- **Estimated reduction:** 30-40% index size

### Performance Improvements
- Faster indexing (less field analysis)
- Smaller field data structures
- Clearer query plans (optimizer knows exact types)

### Maintainability
- Self-documenting (mapping shows exactly what's searchable)
- Prevents accidental queries on non-indexed fields
- Easier to identify missing indexes

### Type Safety
- No more dynamic type inference conflicts
- Explicit control over keyword vs. text
- Prevents ES 8.x `dense_vector` issues

---

## 8. Risks & Mitigation

### Risk 1: Unmapped Field Queries
**Problem:** Queries on fields not in mapping will fail with `strict` mode

**Mitigation:**
- Phase 1 keeps `dynamic: true` during validation
- Comprehensive test suite covers all query patterns
- Monitor logs for unmapped field errors before switching to strict

### Risk 2: New Features Requiring New Fields
**Problem:** Adding searchable fields requires mapping update + reindex

**Mitigation:**
- Document process for adding new indexed fields
- Maintain list of indexed vs. stored-only fields
- Use `dynamic: false` instead of `strict` if needed (silently ignores unmapped)

### Risk 3: Reindexing Downtime
**Problem:** Full reindex required to apply new mapping

**Mitigation:**
- Use aliasing strategy (already in place)
- Index to new timestamped index
- Atomically swap alias when complete
- Zero downtime

---

## 9. Comparison with Malli Schemas

The field types in this mapping should align with Malli schemas defined in:
- `src/cljc/lipas/schema/sports_sites.cljc`
- `src/cljc/lipas/schema/sports_sites/*.cljc`

**Type Mapping:**
| Malli Type | ES Type | Notes |
|------------|---------|-------|
| `:int` | `integer` | lipas-id, type-code, city-code, construction-year |
| `:string` | `text` + `keyword` | name, marketing-name, comment, email, etc. |
| `:boolean` | `boolean` | properties.year-round-use?, etc. |
| `:double` / `:float` | `double` | properties.area-m2, route-length-km |
| `inst?` | `date` | event-date, audit-date |
| GeoJSON | `geo_point` / `geo_shape` | location coordinates |

**Action:** Create automated test that validates ES mapping types match Malli schema types for all indexed fields.

---

## 10. Next Steps

1. **Review:** Get feedback on this proposal from team
2. **Prototype:** Create test index with explicit mapping
3. **Validate:** Run full test suite against test index
4. **Benchmark:** Compare performance vs. current dynamic mapping
5. **Implement:** Deploy to production using aliasing strategy
6. **Monitor:** Watch for unmapped field errors in logs
7. **Document:** Update search.md with final mapping

---

## References

- Elasticsearch 8.x Mapping Documentation
- Current mapping: `src/clj/lipas/backend/search.clj`
- Query patterns analyzed:
  - V2 API: `src/clj/lipas/backend/api/v2.clj`
  - V1 API: `src/clj/lipas/backend/api/v1/search.clj`
  - Reports: `src/clj/lipas/backend/core.clj` (sports-sites-report-*)
  - Heatmap: `src/clj/lipas/backend/analysis/heatmap.clj`
  - Diversity: `src/clj/lipas/backend/analysis/diversity.clj`
  - Reachability: `src/clj/lipas/backend/analysis/reachability.clj`
- Malli schemas: `src/cljc/lipas/schema/sports_sites.cljc`

---

**Document Version:** 1.0
**Last Updated:** 2025-01-29
