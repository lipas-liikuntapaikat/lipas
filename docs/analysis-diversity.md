# Diversity Analysis Documentation

## Overview

The diversity analysis system calculates accessibility and diversity indices for geographic grid areas by analyzing the distribution and accessibility of sports facilities. This document describes the system's architecture, data flow, and performance considerations.

## System Architecture

### Core Components

1. **Grid System**: Uses MML (Maanmittauslaitos/National Land Survey) 250m population grid data
2. **Sports Facilities**: Indexed in Elasticsearch with location and type information
3. **OSRM Integration**: Calculates real-world travel distances via car, bicycle, and foot
4. **Diversity Index Calculation**: Measures the variety and accessibility of sports facilities

### Data Flow

```
MML Grid Items → Fetch Sports Sites → Calculate Distances → Compute Indices → Bulk Index Results
     ↓              (one ES query)         (via OSRM         (diversity)      (Elasticsearch,
  (250m grid)                            table batches)                         per tile)
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
3. Delegates to `process-cells!`

### `process-cells!`
Shared batch engine used by both `recalc-grid!` and CSV seeding
(`seed-new-grid-from-csv!`):

1. **One ES query** fetches every candidate sports site over the whole
   area (cells buffered by `cell-radius-m`, 2km), instead of one geo
   query per grid cell.
2. **Assignment**: each cell gets the sites with at least one
   destination vertex within `cell-radius-m` (+`assignment-margin-m`)
   by haversine distance. Route distance is never shorter than
   euclidean distance, so the prefilter keeps every site whose OSRM
   minimum could matter.
3. **Tiling**: cells are grouped into 2km TM35FIN tiles
   (`tile-size-m`). Each tile issues **one multi-source OSRM table
   request per profile** — all tile cells as sources, the deduplicated
   union of the tile's site vertices as destinations. Destination
   lists larger than `max-table-locations` are chunked and the matrix
   columns merged back (`merge-table-chunks`).
4. **Assembly**: each cell/site pair takes per-profile minimums over
   the site's matrix columns (`site-osrm-mins`); min distance and min
   duration are computed independently, like the interactive analysis
   does.
5. Each tile's docs are bulk-indexed as soon as the tile completes,
   keeping memory bounded.

The old implementation issued 3 OSRM requests (car/bicycle/foot) *per
site per cell* plus one ES geo query per cell — on the order of 100k
HTTP calls for an urban point site. The tiled table requests reduce
that to a handful of requests per tile (typically 3-12 per job) and
1-2 ES queries, without changing the stored document shape.

### `site-osrm-mins`
Extracts per-profile minimums from the tile's table matrices for one
cell (matrix row) and one site (its destination columns).

**Behavior**: Takes the minimum value across the site's destinations
for EACH mode independently. Unroutable destinations (`null` in the
OSRM response) are skipped; if nothing is routable the minimums are
`nil`.

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
- **Sports sites per grid item**: 150-450 (in urban areas)
- **OSRM table requests per analysis**: 3 per ~2km tile of cells
  (typically 3-15 per job; more when destination chunking kicks in)

Measured on a Helsinki-center point-site job (49 cells, 827 candidate
sites, real local OSRM): the old per-site implementation made 50,193
HTTP requests in ~324s; the tiled implementation makes 15 requests
(~3,300x fewer) in ~160s on the same 4-core host. Wall time is bound
by foot-profile table computation (~20ms per destination + ~1ms per
source-destination pair), which is why tile size matters more than
request count - see the `tile-size-m` docstring.

Note on OSRM snapping: table values for coordinates that sit off the
routable network (e.g. facilities inside parks) can differ slightly
depending on the other coordinates in the same request. In an
old-vs-new comparison of ~100k cell/site/profile values, exactly one
pair differed for this reason (3.9m vs 3054m by car for a park
facility - the larger value routes via the real road network).

## Configuration Parameters

Read-time (interactive API):

- `analysis-radius-km`: Search radius for sports sites (default: 5km)
- `max-distance-m`: Maximum distance for diversity calculations (default: 800m)
- `distance-mode`: `:euclid` or `:route` (route uses OSRM)
- `categories`: Sport type codes to include in analysis

Precompute-time (constants in `lipas.backend.analysis.diversity`):

- `cell-radius-m`: Site search radius around each cell (2000)
- `assignment-margin-m`: Slack in the euclidean cell↔site assignment (250)
- `tile-size-m`: Tile edge for grouped OSRM table requests (2000)
- `max-table-locations`: Sources + destinations budget per table
  request (1500; deployment runs osrm-routed with `--max-table-size 3000`)

## Testing Strategy

### Unit Tests
- Pure functions: `site-osrm-mins`, `min-finite`, `calc-aggs`, `bool->num`
- Batching logic: `prepare-site-entry`, `assign-sites`, `index-dests`,
  `merge-table-chunks`
- No external dependencies required

### Integration Tests
- Full workflow with a matrix-shaped OSRM mock whose distances are
  haversine-derived, so stored minimums are asserted against
  independently computed expected values
- Chunking invariance: tiny `max-table-locations` must not change results
- Profile failure / total failure / timeout degradation
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
