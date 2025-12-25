# Diversity Analysis Documentation

## Overview

The diversity analysis system calculates accessibility and diversity indices for geographic grid areas by analyzing the distribution and accessibility of sports facilities. This document describes the system's architecture, data flow, and critical performance considerations discovered during memory leak analysis.

## System Architecture

### Core Components

1. **Grid System**: Uses MML (Maanmittauslaitos/National Land Survey) 250m population grid data
2. **Sports Facilities**: Indexed in Elasticsearch with location and type information
3. **OSRM Integration**: Calculates real-world travel distances via car, bicycle, and foot
4. **Diversity Index Calculation**: Measures the variety and accessibility of sports facilities

### Data Flow

```
MML Grid Items → Fetch Sports Sites → Calculate Distances → Compute Indices → Bulk Index Results
     ↓                    ↓                    ↓                    ↓                  ↓
  (250m grid)      (within radius)        (via OSRM)        (diversity)      (Elasticsearch)
```

## Key Functions

### `recalc-grid!`
Main orchestration function that processes grid items for a given geographic area.

**Input**:
- `search`: Search configuration with ES client and indices
- `fcoll`: Feature collection defining the analysis area

**Process**:
1. Creates a 2km buffer around the analysis area
2. Fetches all grid items intersecting the buffer
3. Processes each grid item to find nearby sports sites
4. Calculates distances using OSRM
5. Bulk indexes results back to Elasticsearch

### `process-grid-item`
Processes a single grid item to calculate sports site accessibility.

**Input**:
- Grid item with WKT point and population data
- Search radius (typically 2km)

**Output**:
- Original grid data enriched with `:sports-sites` vector containing:
  - Site ID, type code, and status
  - OSRM distances and durations for each transport mode

### `apply-mins`
Transforms OSRM response to extract minimum distances for each transport mode.

**Behavior**: Takes the minimum value across ALL destinations for EACH mode independently.

```clojure
;; Input
{:car     {:distances [[2000.0 1234.5 3000.0]]}
 :bicycle {:distances [[2500.0 1500.0 3500.0]]}
 :foot    {:distances [[1900.0 1100.0 900.0]]}}

;; Output
{:car     {:distance-m 1234.5}  ; minimum for car
 :bicycle {:distance-m 1500.0}  ; minimum for bicycle
 :foot    {:distance-m 900.0}}  ; minimum for foot
```

## Data Structures

### Grid Item Structure
```clojure
{:grd_id    "250mN667175E38600"  ; Unique grid identifier
 :WKT       "POINT (24.94 60.16)" ; Location as WKT
 :vaesto    "150"                 ; Total population
 :ika_0_14  "25"                  ; Population age 0-14
 :ika_15_64 "100"                 ; Population age 15-64
 :ika_65_   "25"                  ; Population age 65+
 :kunta     "Helsinki"            ; Municipality
 :vuosi     2023                  ; Year
 :sports-sites [...]              ; Added by processing
}
```

### Sports Site Result Structure
```clojure
{:id        "site-123"
 :type-code 1520          ; Sport type (e.g., ice skating)
 :status    "active"
 :osrm      {:car     {:distance-m 1234.5 :duration-s 180.2}
             :bicycle {:distance-m 1500.0 :duration-s 450.0}
             :foot    {:distance-m 900.0  :duration-s 648.0}}}
```

## Typical High Scale Case

- **Grid items per analysis**: 50-200 (depending on area size)
- **Sports sites per grid item**: 150-300 (in urban areas)
- **OSRM calls per analysis**: 15,000-60,000

## Configuration Parameters

- `analysis-radius-km`: Search radius for sports sites (default: 5km)
- `max-distance-m`: Maximum distance for diversity calculations (default: 800m)
- `distance-mode`: `:euclid` or `:route` (route uses OSRM)
- `categories`: Sport type codes to include in analysis

## Testing Strategy

### Unit Tests
- Pure functions: `apply-mins`, `calc-aggs`, `bool->num`
- Data transformation: `resolve-dests`, `prepare-categories`
- No external dependencies required

### Integration Tests
- Full workflow with mocked OSRM responses
- Elasticsearch operations with test indices
- Memory usage monitoring
- Concurrent processing verification

### Test Data
- Use realistic MML grid structure
- Include edge cases: negative populations (-1), missing data
- Mock OSRM responses with actual API format

## Related Systems

- **Population Analysis**: Uses similar grid-based approach
- **Heatmap Generation**: Visualizes the diversity index results
- **School Analysis**: Similar distance-based accessibility calculations

## Maintenance Notes

- Monitor OSRM API usage and rate limits
- Regular ES index optimization for query performance
- Update grid data annually when new MML data is released
- Check for sports facility data quality (missing coordinates, etc.)
