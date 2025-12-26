# Reports & Statistics - Technical Documentation

This document describes the technical implementation of reporting features in LIPAS.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                 │
├─────────────────────────────────────────────────────────────────┤
│  lipas.ui.stats.*         - Statistics UI components            │
│  lipas.ui.reports.*       - Custom report dialog                │
│  lipas.ui.analysis.*      - Analysis tool reports               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Backend                                  │
├─────────────────────────────────────────────────────────────────┤
│  lipas.backend.handler    - HTTP endpoints                       │
│  lipas.backend.core       - Report generation logic              │
│  lipas.reports            - Shared report definitions            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Data Sources                               │
├─────────────────────────────────────────────────────────────────┤
│  Elasticsearch            - Sports sites index                   │
│  PostgreSQL               - Subsidies, city stats                │
└─────────────────────────────────────────────────────────────────┘
```

## Frontend Components

### Statistics Module

**Location:** `src/cljs/lipas/ui/stats/`

The statistics module is organized by report type:

```
stats/
├── views.cljs           # Main tabbed container
├── routes.cljs          # URL routing (/tilastot/*)
├── events.cljs          # Tab navigation events
├── subs.cljs            # Common subscriptions
├── db.cljs              # Default state
├── common.cljs          # Shared UI components
│
├── sport/               # Sports facility stats
│   ├── views.cljs
│   ├── events.cljs
│   ├── subs.cljs
│   └── db.cljs
│
├── age_structure/       # Construction year stats
│   ├── views.cljs
│   ├── events.cljs
│   ├── subs.cljs
│   └── db.cljs
│
├── city/                # City-level stats
│   ├── views.cljs
│   ├── events.cljs
│   ├── subs.cljs
│   └── db.cljs
│
├── finance/             # Financial stats
│   ├── views.cljs
│   ├── events.cljs
│   ├── subs.cljs
│   └── db.cljs
│
└── subsidies/           # Subsidies stats
    ├── views.cljs
    ├── events.cljs
    ├── subs.cljs
    └── db.cljs
```

### Report Dialog

**Location:** `src/cljs/lipas/ui/reports/`

The custom report dialog allows users to configure and download Excel/CSV/GeoJSON exports:

```clojure
;; Key components in reports/views.cljs
(defn dialog [{:keys [tr btn-variant]}]
  ;; Main report configuration dialog
  ;; - Field selector with autocomplete
  ;; - Format selector (xlsx/csv/geojson)
  ;; - Quick-select presets
  ;; - Saved templates (for logged-in users)
  )

(defn fields-selector [{:keys [tr value on-change]}]
  ;; Multi-select autocomplete for 100+ available fields
  )
```

### URL Routes

Statistics pages use the following routes (defined in `stats/routes.cljs`):

| Route | Tab | Event |
|-------|-----|-------|
| `/tilastot` | sports | Default |
| `/tilastot/liikuntapaikat` | sports | Sports stats |
| `/tilastot/rakennusvuodet` | age-structure | Age structure |
| `/tilastot/kunta` | city | City stats |
| `/tilastot/talous` | finance | Finance stats |
| `/tilastot/avustukset` | subsidies | Subsidies |

## Backend Endpoints

### Report Generation Endpoints

**Location:** `src/clj/lipas/backend/handler.clj`

#### Sports Sites Report

```
POST /actions/create-sports-sites-report
```

**Request body:**
```clojure
{:search-query {...}   ; Elasticsearch query
 :format "xlsx"        ; "xlsx" | "csv" | "geojson"
 :fields ["lipas-id" "name" ...]  ; Fields to include
 :locale :fi}          ; :fi | :en | :se
```

**Implementation:** `src/clj/lipas/backend/core.clj`

```clojure
(defn sports-sites-report-excel [search query fields locale out]
  ;; Uses Elasticsearch scroll API for large result sets
  ;; Streams results to avoid memory issues
  )

(defn sports-sites-report-csv [search query fields locale out]
  ;; Similar to Excel but CSV format
  )

(defn sports-sites-report-geojson [search query fields locale out]
  ;; Includes geometries as GeoJSON features
  )
```

#### Data Model Report

```
POST /actions/create-data-model-report
```

Returns Excel workbook documenting the complete data model.

**Implementation:** `src/clj/lipas/data_model_export.clj`

Sheets included:
- Liikuntapaikka (sports site fields)
- Liikuntapaikkatyypit (facility types)
- Ominaisuudet (properties)
- Luokitellut ominaisuudet (enum values)
- Tyypit+Ominaisuudet (type-property mappings)
- Omistajaluokat (owner classes)
- Ylläpitäjäluokat (admin classes)
- Aktiviteetit (activities)
- Muut kohteet (LOI)
- Salibandyn olosuhdetiedot (floorball conditions)
- WFS-tasot (WFS layers)
- Tekniset mäppäykset (API field mappings)

#### Energy Report

```
POST /actions/create-energy-report
```

**Request body:**
```clojure
{:type-code 2510  ; Ice stadium or swimming pool type
 :year 2023}
```

Returns aggregate energy consumption statistics.

### Statistics Data Endpoints

Statistics data is fetched via these patterns:

#### Finance Statistics

Data source: `city_stats` Elasticsearch index

```clojure
(defn query-finance-report [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:report :city-stats])]
    (:body (search/search client idx-name params))))
```

#### Subsidies Statistics

Data source: `subsidies` Elasticsearch index

```clojure
(defn query-subsidies [{:keys [indices client]} params]
  (let [idx-name (get-in indices [:report :subsidies])]
    (:body (search/search client idx-name params))))
```

#### Sports Statistics

Aggregations computed on `sports_sites_current` Elasticsearch index.

```clojure
(defn calculate-stats [db search* {:keys [city-codes type-codes grouping year]}]
  ;; Returns aggregated statistics by city or type
  ;; Includes: site counts, area sums, route lengths, per-capita metrics
  )
```

## Shared Data Definitions

**Location:** `src/cljc/lipas/reports.cljc`

### Available Report Fields

```clojure
(def fields
  (merge
   basic-fields      ; lipas-id, name, type, location, etc.
   meta-fields       ; coordinates, province, categories, audit dates
   prop-fields))     ; All 100+ property fields
```

### Statistics Metrics

```clojure
(def sports-stats-metrics
  {"sites-count"        ; Facility count
   "sites-count-p1000c" ; Per 1000 capita
   "area-m2-sum"        ; Total area
   "area-m2-pc"         ; Area per capita
   "length-km-sum"      ; Route length
   "length-km-pc"})     ; Route length per capita

(def stats-metrics
  {"investments"
   "operating-expenses"
   "operating-incomes"
   "subsidies"
   "net-costs"
   "operational-expenses"
   "operational-income"
   "surplus"
   "deficit"})
```

## Analysis Tool Reports

### Reachability Report

**Location:** `src/clj/lipas/backend/analysis/reachability.clj`

```clojure
(defn create-report [data]
  ;; Returns multi-sheet Excel structure:
  ["Väestö" (create-population-sheet data)
   "Koulut" (create-schools-sheet data)
   "Liikuntapaikat" (create-sports-sites-sheet data)])
```

Data structure required:
```clojure
{:zones {:distance [...] :travel-time [...]}
 :runs {lipas-id {:site-name "..."
                  :population {:stats {...}}
                  :schools {:data [...]}
                  :sports-sites {:data [...]}}}}
```

### Diversity Report Exports

**Location:** `src/cljs/lipas/ui/analysis/diversity/events.cljs`

Export types:
- `::export-aggs` - Area-level diversity indices
- `::export-grid` - 250m grid-level data
- `::export-categories` - Classification definitions
- `::export-settings` - Analysis parameters

Formats: Excel or GeoJSON

## Excel Generation

The backend uses `dk.ative.docjure` for Excel generation:

```clojure
(require '[dk.ative.docjure.spreadsheet :as excel])

(defn sports-sites-report-excel [search query fields locale out]
  (->> (async/<!! data-chan)
       (excel/create-workbook "lipas")
       (excel/save-workbook-into-stream! out)))
```

For streaming large reports, results are processed in pages via Elasticsearch scroll API.

## CSV Generation

Uses `clojure.data.csv`:

```clojure
(require '[clojure.data.csv :as csv])

(defn sports-sites-report-csv [search query fields locale out]
  (with-open [writer (OutputStreamWriter. out)]
    (csv/write-csv writer [headers])
    (loop []
      (when-let [page (async/<!! in-chan)]
        (csv/write-csv writer (map xform ms))
        (recur)))))
```

## GeoJSON Generation

Custom streaming JSON encoder for large geographic exports:

```clojure
(defn sports-sites-report-geojson [search query fields locale out]
  (with-open [writer (OutputStreamWriter. out)]
    (.write writer "{\"type\":\"FeatureCollection\",\"features\":[")
    (loop [page-num 0]
      (when-let [page (async/<!! in-chan)]
        ;; Write features with geometry
        (recur (inc page-num))))
    (.write writer "]}")))
```

## Charts & Visualization

**Location:** `src/cljs/lipas/ui/charts.cljs`

Uses Recharts library for visualizations:

- `sports-stats-chart` - Bar charts for facility statistics
- `age-structure-chart` - Histograms for construction years
- `finance-chart` - Multi-series comparison charts
- `finance-ranking-chart` - Horizontal bar rankings
- `subsidies-chart` - Stacked bar charts
- `population-area-chart` - Stacked area for demographics
- `schools-area-chart` - School distribution by zone

## Localization

Report labels and translations:

**Location:** `src/cljc/lipas/i18n/{fi,en,se}/`

Key translation files:
- `stats.edn` - Statistics section labels
- `reports.edn` - Report dialog labels
- `analysis.edn` - Analysis tool labels
- `stats-metrics.edn` - Metric names

## Performance Considerations

### Large Result Sets

Reports use Elasticsearch scroll API for datasets > 10,000 records:

```clojure
(defn scroll [client idx-name params]
  ;; Returns async channel of result pages
  ;; Each page contains up to 1000 records
  )
```

### Excel Limits

Excel export is blocked when **both** conditions are met:
- More than 10,000 results **AND**
- More than 20 fields selected

```clojure
;; src/cljs/lipas/ui/reports/subs.cljs
(rf/reg-sub ::limits-exceeded?
  :<- [::selected-fields]
  :<- [:lipas.ui.search.subs/search-results-total-count]
  :<- [::selected-format]
  (fn [[fields results-count fmt] _]
    (and (#{"xlsx"} fmt)
         (> results-count 10000)
         (> (count fields) 20))))
```

This is a workaround for observed Excel generation issues with large datasets. The UI warning message simplifies this to "10,000 results" for user clarity, even though exports with fewer fields may still succeed.

For large exports, CSV or GeoJSON formats are recommended as they use streaming and have no practical limits.

### Streaming

All report generation streams directly to the HTTP response output stream, avoiding full materialization of large datasets in memory.

## Testing

Report functionality is covered by:
- `test/clj/lipas/backend/api/v1/handler_test.clj` - API endpoint tests
- Manual testing via browser for UI interactions

## Related Documentation

- [Analysis - Reachability](./analysis-reachability.md) - Detailed reachability analysis docs
- [Elasticsearch & Search](./elasticsearch-search.md) - Search index documentation
- [Data Model](../docs/data-model.md) - Data model overview
