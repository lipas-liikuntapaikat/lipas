# Reachability Analysis

LIPAS provides **reachability analysis** tools to evaluate how accessible sports facilities are to the population. This feature calculates travel times and distances from population centers to facilities using different transport modes.

---

## Table of Contents

1. [Overview](#overview)
2. [OSRM Integration](#osrm-integration)
3. [Travel Profiles](#travel-profiles)
4. [Population Data](#population-data)
5. [Analysis Workflow](#analysis-workflow)
6. [Zones and Metrics](#zones-and-metrics)
7. [Report Generation](#report-generation)
8. [API Endpoints](#api-endpoints)
9. [Frontend UI](#frontend-ui)
10. [Configuration](#configuration)
11. [Key Files Reference](#key-files-reference)

---

## Overview

Reachability analysis answers questions like:
- How many people can reach a sports facility within 15 minutes by car?
- What schools are within 2km of a swimming pool?
- How does a new facility location improve population coverage?

The system combines:
- **OSRM** (Open Source Routing Machine) for travel time calculations
- **Population grid data** from Statistics Finland
- **School location data** for accessibility analysis
- **Sports site locations** from LIPAS

---

## OSRM Integration

OSRM provides routing calculations for three transport profiles. Each profile uses a separate OSRM server instance with Finnish road network data.

### Configuration

```clojure
;; Environment variables for OSRM server URLs
:osrm-car-url     "http://osrm-car:5000/table/v1/driving/"
:osrm-bicycle-url "http://osrm-bicycle:5000/table/v1/cycling/"
:osrm-foot-url    "http://osrm-foot:5000/table/v1/walking/"
```

### API Usage

The OSRM integration uses the `/table/v1` endpoint for matrix calculations:

```clojure
;; src/clj/lipas/backend/osrm.clj

(defn get-distances-and-travel-times
  [{:keys [profiles] :or {profiles [:car :bicycle :foot]} :as m}]
  ;; Parallel requests to all profile servers
  (->> profiles
       (mapv (fn [p] (vector p (future (get-data (assoc m :profile p))))))
       (reduce (fn [res [p f]] (assoc res p (deref f))) {})))
```

### Response Format

OSRM returns matrices of:
- **durations** - Travel times in seconds
- **distances** - Distances in meters

### Caching

OSRM responses are cached for 5 minutes to reduce load:

```clojure
(defonce osrm-cache
  (atom (cache/ttl-cache-factory {} :ttl (* 5 60 1000))))
```

---

## Travel Profiles

### Transport Modes

| Profile | Description | Speed Characteristics |
|---------|-------------|----------------------|
| `:car` | Driving by car | Follows road network, speed limits |
| `:bicycle` | Cycling | Cycle paths preferred, ~15-20 km/h |
| `:foot` | Walking | Pedestrian paths, ~5 km/h |
| `:direct` | Straight-line distance | No routing, pure geometry |

### Direct Distance Calculation

The `:direct` profile bypasses OSRM and calculates straight-line (Euclidean) distance using JTS:

```clojure
(defn calc-distances [source-fcoll populations]
  (let [geom-jts (-> source-fcoll gis/->jts-geom gis/transform-crs)]
    (->> populations
         (reduce-kv
          (fn [res id {:keys [coords] :as m}]
            (let [g (gis/wgs84wkt->tm35fin-geom coords)]
              (assoc res id (assoc m :distance-m (gis/shortest-distance geom-jts g)))))
          {}))))
```

---

## Population Data

### Data Source

Population data comes from **Statistics Finland's population grid** (väestöruutu):
- 1km grid for distances > 10km
- 250m grid for distances ≤ 10km (high-definition)

### Elasticsearch Indices

```clojure
;; Index aliases
:population          "population-1km"      ; 1km resolution
:population-high-def "population-250m"     ; 250m resolution
```

### Grid Resolution Selection

```clojure
(defn get-population-data [{:keys [indices client]} fcoll distance-km]
  (let [idx (if (> distance-km population-high-def-threshold-km)
              (get-in indices [:analysis :population])
              (get-in indices [:analysis :population-high-def]))]
    (get-es-data* idx client fcoll distance-km)))
```

### Demographics Fields

Each grid cell contains:

| Field | Description |
|-------|-------------|
| `vaesto` | Total population |
| `ika_0_14` | Age 0-14 years |
| `ika_15_64` | Age 15-64 years |
| `ika_65_` | Age 65+ years |
| `coords` | WKT point geometry |
| `kunta` | Municipality code |

### Privacy/Anonymization

Population values below 10 are anonymized to protect individual privacy:

```clojure
(def anonymity-threshold 10)

(defn anonymize [n]
  (when (and (some? n) (>= n anonymity-threshold)) n))
```

---

## Analysis Workflow

### Input Parameters

```clojure
{:search-fcoll  {...}           ; GeoJSON FeatureCollection (analysis origin)
 :buffer-fcoll  {...}           ; Buffered geometry for data queries
 :distance-km   10              ; Search radius in kilometers
 :profiles      [:car :foot :bicycle]
 :type-codes    [1520 2240]     ; Filter to specific facility types
 :zones         {...}}          ; Zone definitions for grouping results
```

### Processing Pipeline

```
1. Query Data (parallel)
   ├── Population grid cells within buffer
   ├── Schools within buffer
   └── Sports sites within buffer

2. Calculate Distances (parallel)
   ├── Direct distances (JTS geometry)
   └── Travel times (OSRM matrix)

3. Resolve Zones
   └── Assign each point to appropriate zone based on distance/time

4. Aggregate Statistics
   └── Sum demographics by zone, profile, metric

5. Return Combined Results
   ├── population - Grid cells with zone assignments
   ├── population-stats - Aggregated demographics by zone
   ├── schools - School locations with distances
   └── sports-sites - Facility locations with distances
```

### Parallel Processing

The implementation uses Clojure futures for concurrent execution:

```clojure
(defn calc-distances-and-travel-times [search params]
  (let [pop-data (future (common/get-population-data ...))
        school-data (future (common/get-school-data ...))
        sports-site-data (future (get-sports-site-data ...))

        pop-distances (future (calc-distances ...))
        pop-travel-times (future (calc-travel-times ...))
        ;; ...more futures...
        ]

    ;; Wait for all futures and combine
    (combine @sports-site-data @sports-site-distances ...)))
```

---

## Zones and Metrics

### Zone Definitions

Zones group results into distance/time bands for easier interpretation:

```clojure
;; Default distance zones (km)
{:distance
 [{:id :zone1 :min 0 :max 2}
  {:id :zone2 :min 2 :max 5}
  {:id :zone3 :min 5 :max 10}]}

;; Default travel time zones (minutes)
{:travel-time
 [{:id :zone1 :min 0 :max 10}
  {:id :zone2 :min 10 :max 20}
  {:id :zone3 :min 20 :max 30}
  {:id :zone4 :min 30 :max 40}]}
```

### Zone Resolution

Each population point is assigned to a zone based on its distance/travel-time:

```clojure
(defn resolve-zone [zones m profile metric]
  (if (= :travel-time metric)
    (when-let [time-s (-> m :route profile :duration-s)]
      (let [time-min (/ time-s 60)]
        (->> zones :travel-time
             (some (fn [zone]
                     (when (>= (:max zone) time-min (:min zone))
                       (:id zone)))))))
    ;; distance metric
    (let [distance-km (-> m :route profile :distance-m (/ 1000))]
      (->> zones :distance
           (some (fn [zone]
                   (when (>= (:max zone) distance-km (:min zone))
                     (:id zone))))))))
```

### Metrics

| Metric | Unit | Description |
|--------|------|-------------|
| `:travel-time` | minutes | Time to reach facility |
| `:distance` | kilometers | Distance to facility |

---

## Report Generation

The system generates Excel reports with multiple sheets:

### Sheets

1. **Väestö (Population)** - Demographics by zone
2. **Koulut (Schools)** - School counts by zone and type
3. **Liikuntapaikat (Sports Sites)** - Facility counts by zone and type

### Report Structure

```clojure
;; Headers for population sheet
["Suure" "Kulkutapa" "Etäisyys" "Väestöryhmä" <site-names...>]

;; Example row
["Matka-aika" "Autolla" "0-10min" "Ikä 0-14" 1234 567 890]
```

### School Types

```clojure
{:vaka        "Varhaiskasvatusyksikkö"    ; Early childhood education
 :lukiot      "Lukiot"                     ; Upper secondary schools
 :peruskoulut "Peruskoulut"                ; Comprehensive schools
 :perus+lukio "Perus- ja lukioasteen koulut"
 :erityis     "Peruskouluasteen erityiskoulut"}  ; Special needs schools
```

---

## API Endpoints

### Calculate Distances and Travel Times

```
POST /api/actions/calc-distances-and-travel-times
```

**Request Body:**
```json
{
  "search-fcoll": {
    "type": "FeatureCollection",
    "features": [{"type": "Feature", "geometry": {...}}]
  },
  "buffer-fcoll": {...},
  "distance-km": 10,
  "profiles": ["car", "foot", "bicycle"],
  "type-codes": [1520, 2240],
  "zones": {
    "distance": [...],
    "travel-time": [...]
  }
}
```

**Response:**
```json
{
  "population": [...],
  "population-stats": {
    "travel-time": {
      "car": {"zone1": {"vaesto": 12345, "ika_0_14": 1234, ...}}
    }
  },
  "schools": [...],
  "sports-sites": [...]
}
```

### Generate Report

```
POST /api/actions/create-analysis-report
```

Returns an Excel file (`.xlsx`) with analysis results.

---

## Frontend UI

### Components

The reachability UI is built with Re-frame and Material-UI:

```
analysis/reachability/
├── db.cljs       ; Default state and zone definitions
├── events.cljs   ; Re-frame event handlers
├── subs.cljs     ; Re-frame subscriptions
└── views.cljs    ; Reagent components
```

### Main Views

1. **Sports Sites Tab** - Bar chart of facilities by zone
2. **Population Tab** - Area chart of population by zone
3. **Schools Tab** - Bar chart of schools by zone
4. **Settings Tab** - Zone configuration

### Travel Profile Selector

Users can switch between transport modes:
- Car icon → `:car` profile
- Bicycle icon → `:bicycle` profile
- Walking icon → `:foot` profile
- Distance icon → `:direct` (straight-line)

### Zone Configuration

Users can customize zone boundaries using range sliders:

```clojure
;; Available distance options (km)
[0 0.5 1 1.5 2 3 4 5 7.5 10 15 20 30 40 50]

;; Available time options (minutes)
[0 10 20 30 40 50 60]
```

### Visualization

Results are displayed using:
- **Area charts** for cumulative population coverage
- **Bar charts** for facility/school counts by zone
- **Map visualization** with color-coded zones

---

## Configuration

### Environment Variables

```bash
# OSRM server URLs (required for travel time calculations)
OSRM_CAR_URL=http://osrm-car:5000/table/v1/driving/
OSRM_BICYCLE_URL=http://osrm-bicycle:5000/table/v1/cycling/
OSRM_FOOT_URL=http://osrm-foot:5000/table/v1/walking/
```

### Elasticsearch Indices

```clojure
;; Analysis indices configuration
{:analysis
 {:population          "population-1km"
  :population-high-def "population-250m"
  :schools             "schools"}}
```

### Data Loading

Population data is loaded from CSV files:

```clojure
;; Load 1km population grid
(common/seed-population-1km-grid-from-csv! search "/path/to/population-1km.csv")

;; Load 250m population grid
(common/seed-population-250m-grid-from-csv! search "/path/to/population-250m.csv")
```

---

## Key Files Reference

### Backend

| File | Purpose |
|------|---------|
| `src/clj/lipas/backend/osrm.clj` | OSRM client with caching |
| `src/clj/lipas/backend/analysis/reachability.clj` | Main analysis logic |
| `src/clj/lipas/backend/analysis/common.clj` | Shared utilities, population data |
| `src/clj/lipas/backend/core.clj` | API entry points |
| `src/clj/lipas/backend/gis.clj` | Geometry operations (JTS) |

### Frontend

| File | Purpose |
|------|---------|
| `src/cljs/lipas/ui/analysis/reachability/db.cljs` | Default state, zone definitions |
| `src/cljs/lipas/ui/analysis/reachability/events.cljs` | Re-frame events |
| `src/cljs/lipas/ui/analysis/reachability/subs.cljs` | Re-frame subscriptions |
| `src/cljs/lipas/ui/analysis/reachability/views.cljs` | UI components |
| `src/cljs/lipas/ui/charts.cljs` | Chart components |

### Related Analysis Features

| Feature | Files |
|---------|-------|
| Diversity Analysis | `src/clj/lipas/backend/analysis/diversity.clj` |
| Heatmap | `src/clj/lipas/backend/analysis/heatmap.clj` |

---

## Summary

The reachability analysis feature provides:

1. **Multi-modal travel calculations** via OSRM integration
2. **Population coverage analysis** with Statistics Finland grid data
3. **School accessibility** for educational planning
4. **Configurable zones** for customized analysis
5. **Excel report generation** for offline analysis
6. **Interactive UI** with charts and map visualization

The system efficiently handles large datasets through:
- Parallel processing with Clojure futures
- Response caching for OSRM requests
- Automatic resolution selection for population grids
- Privacy-preserving anonymization
