# LIPAS Heatmap Feature Documentation

## Overview

The LIPAS Heatmap feature is a density map visualization tool that allows users to illustrate the regional and local quantity and density of sports facilities across Finland. The heatmap provides an intuitive visual representation of where sports facilities are concentrated, helping users identify areas with high or low facility density.

## Purpose

The heatmap visualization helps users:
- **Identify facility clusters**: Quickly see where sports facilities are concentrated
- **Discover underserved areas**: Find regions with low facility density that may need more resources
- **Analyze regional distribution**: Understand how facilities are distributed across different geographical areas
- **Support planning decisions**: Use visual data to inform infrastructure planning and resource allocation

## Key Features

### Automatic Map Refresh
The heatmap automatically updates whenever the user:
- **Zooms** in or out
- **Pans** the map to a different location
- Changes any filter setting

This provides real-time visual feedback as users explore different regions and zoom levels.

### Intelligent Precision
The heatmap uses automatic geohash precision based on the current zoom level:
- **High zoom levels** (close view): Higher precision for detailed local analysis
- **Low zoom levels** (overview): Lower precision for regional analysis
- **Automatic adjustment**: Ensures optimal performance and visual clarity at any zoom level

### Current Map View Focus
The heatmap always analyzes data within the current map viewport, ensuring:
- Fast performance by processing only visible data
- Relevant results for the area being examined
- Immediate feedback when exploring different regions

## User Interface

### Info Panel
A blue information box at the top of the controls explains:
- The purpose of the heatmap tool
- How to customize the visualization
- Special considerations for route-based facilities

### Filter Options
Users can filter sports facilities by:
- **Type/Category**: Select specific types of sports facilities
- **Status**: Active, temporarily out of service, etc.
- **Owner**: Filter by facility ownership
- **Admin**: Filter by administrative entity
- **Construction Year**: Range slider for year filtering

### Visual Settings
Users can customize the heatmap appearance:

#### Radius (5-100px)
Controls the size of each heat point. Larger values create more spread-out, smoother visualizations.

#### Blur (0-50px)
Adjusts the smoothness of the heat gradient. Higher values create softer, more blended transitions.

#### Opacity (0.1-1.0)
Controls the transparency of the heatmap layer. Lower values allow the base map to show through more clearly.

#### Color Gradient
Three gradient options available:
- **Cool** (Purple to White): Default, provides good contrast
- **Warm** (Yellow to Red): Traditional heat map appearance
- **Grayscale**: Neutral color scheme for accessibility or printing

## Technical Implementation

### Architecture

```
┌─────────────────────────────────────────┐
│         User Interface Layer            │
│  (views.cljs, events.cljs, subs.cljs)  │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│       Frontend State Management          │
│         (db.cljs, re-frame)             │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│          Backend API Layer               │
│     (heatmap.clj, HTTP endpoints)       │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│         Elasticsearch Index              │
│    (sports_sites_current, queries)      │
└─────────────────────────────────────────┘
```

### Frontend Components

#### State Management (`db.cljs`)
Default state configuration:
```clojure
{:dimension :density           ; Locked to facility density
 :weight-by :count            ; Locked to count-based weighting
 :precision nil               ; Auto-precision based on zoom
 :use-bbox-filter? true       ; Always use current map view
 :filters {...}               ; User-configurable filters
 :visual {                    ; User-configurable visual settings
   :radius 20
   :blur 15
   :opacity 0.8
   :gradient :cool
   :weight-fn :linear}}       ; Locked to linear weighting
```

#### Event Handlers (`events.cljs`)

**Key Events:**
- `::create-heatmap`: Fetches heatmap data from backend
- `::map-view-changed`: Triggers refresh when map view changes
- `::update-filter-and-refresh`: Updates filters and refreshes data
- `::update-visual-param`: Updates visual settings (client-side only)

**Auto-Refresh Implementation:**
```clojure
(rf/reg-event-fx ::map-view-changed
  (fn [{:keys [db]} _]
    (when (= (get-in db [:map :mode :sub-mode]) :heatmap)
      {:dispatch-n [[::get-facets]
                    [::create-heatmap]]})))
```

The `::map-view-changed` event is dispatched by the main map `::set-view` event handler whenever the map extent changes.

#### UI Components (`views.cljs`)

**Main Component:** `heatmap-controls`
- Displays info text (internationalized)
- Shows loading indicator during data fetch
- Displays error messages if data fetch fails
- Renders filter panel (collapsed by default)
- Renders visual settings panel (collapsed)

**Hidden Controls:**
The following settings are locked and no longer displayed in the UI:
- Dimension selector (always "Facility Density")
- Weight selector (always "Count")
- Geohash precision selector (always automatic)
- Bbox filter toggle (always enabled)
- Weight function selector (always "Linear")

### Backend Processing

#### API Endpoint (`/actions/create-heatmap`)

**Request Parameters:**
```clojure
{:dimension :density          ; Currently only :density supported
 :weight-by :count           ; Weighting method
 :precision nil              ; nil = auto, or specific geohash precision
 :zoom 10                    ; Current zoom level
 :bbox {:min-x 19.5          ; Bounding box for analysis
        :max-x 31.5
        :min-y 59.5
        :max-y 70.5}
 :filters {:type-codes [...]  ; Optional filters
           :status-codes [...]
           :owners [...]}}
```

**Response:**
```clojure
{:data [{:geohash "u5q1"
         :lat 60.1699
         :lon 24.9384
         :weight 5
         :facilities [...]}
        ...]}
```

#### Geohash Aggregation

The backend uses Elasticsearch geohash grid aggregation to cluster facilities:

1. **Determine precision** based on zoom level (4-8)
2. **Query Elasticsearch** with filters and bbox
3. **Aggregate by geohash** to create density clusters
4. **Calculate weights** based on facility count or other metrics
5. **Return cluster data** with coordinates and weights

#### Zoom-to-Precision Mapping

```clojure
zoom ≤ 5  → precision 4 (~39km)
zoom ≤ 7  → precision 5 (~5km)
zoom ≤ 9  → precision 6 (~1.2km)
zoom ≤ 11 → precision 7 (~153m)
zoom ≤ 13 → precision 8 (~38m)
zoom > 13 → precision 8 (~38m)
```

This mapping ensures optimal performance and visual clarity at different zoom levels.

### OpenLayers Integration

The heatmap is rendered using OpenLayers Heatmap layer:

```javascript
// Simplified example
const heatmapLayer = new ol.layer.Heatmap({
  source: vectorSource,
  blur: 15,
  radius: 20,
  gradient: ['rgba(0,0,255,0)', 'rgba(0,0,255,1)', 'rgba(255,255,255,1)']
});
```

Visual parameters are updated reactively when users adjust settings.

## Internationalization

The heatmap feature supports three languages:

### Finnish (fi)
```edn
:heatmap "Lämpökartta"
:analysis.heatmap/info-text "Tällä tiheyskartan visualisointityökalulla..."
```

### English (en)
```edn
:heatmap "Heatmap"
:analysis.heatmap/info-text "This density map visualization tool..."
```

### Swedish (se)
```edn
:heatmap "Värmekarta"
:analysis.heatmap/info-text "Med detta täthetskartevisualiseringsverktyg..."
```

Translation keys are defined in `src/cljc/lipas/i18n/{fi,en,se}/analysis.edn`.

## Performance Considerations

### Optimization Strategies

1. **Bounding Box Filtering**: Only processes facilities within the current map view
2. **Automatic Precision**: Adjusts detail level based on zoom to balance performance and visual quality
3. **Client-Side Visual Updates**: Gradient, blur, and opacity changes don't require server round-trips
4. **Debounced Refresh**: Map movement triggers are managed efficiently to avoid excessive API calls

### Known Limitations

1. **Route Processing**: Line-based facilities (routes, trails) are processed as points, not as linear features. The backend converts line geometries to representative points for heatmap aggregation.

2. **Large Datasets**: At very high zoom levels with dense facility coverage, rendering may slow down. The automatic precision adjustment helps mitigate this.

3. **Precision Bounds**: Maximum geohash precision is capped at 8 to maintain reasonable performance on commodity hardware.

## Development Guide

### Adding New Features

#### New Gradient
1. Define gradient colors in OpenLayers format
2. Add to visual controls in `views.cljs`
3. Update color mapping in map rendering layer

#### New Dimension
1. Add dimension to backend aggregation logic
2. Update schema validation
3. Add UI controls if needed (currently hidden)
4. Update documentation

#### New Filter Type
1. Add filter to `filters-panel` in `views.cljs`
2. Update `::update-filter-and-refresh` event handler
3. Ensure backend query supports the filter
4. Add to facets query if dynamic filtering needed

### Testing

Run backend tests:
```bash
cd webapp
bb test-ns lipas.backend.analysis.heatmap-test
```

Use playwright-mcp to test the frontend manually:
- login as admin
- browse to https://localhost/liikuntapaikat
- click "Analyysityökalut" button
- select "Heatmap" tab

Key test areas:
- Heatmap creation with different parameters
- Filter functionality
- Bbox filtering
- Zoom-precision mapping
- Weight calculation

### Debugging

Enable console logging:
```clojure
;; In events.cljs
(js/console.log "Heatmap params:" params)
```

Check Elasticsearch queries:
```bash
# View actual ES query being executed
curl -X POST localhost:9200/sports_sites_current/_search?pretty -H 'Content-Type: application/json' -d @query.json
```

Inspect map view state:
```javascript
// In browser console
re_frame.db.app_db.deref().map
```

## Future Enhancements

Potential improvements for future releases:

1. **Multiple Dimensions**: Re-enable configurable dimensions with better UI/UX
2. **Time-Based Animation**: Show facility density changes over time
3. **Comparison Mode**: Side-by-side comparison of different regions or time periods
4. **Export Functionality**: Download heatmap as image or data export
5. **Advanced Filters**: Facility amenities, accessibility features, etc.
6. **Custom Gradients**: Allow users to define custom color gradients
7. **Route Density**: Proper line-based density visualization for routes

## References

- [Elasticsearch Geohash Grid Aggregation](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-geohashgrid-aggregation.html)
- [OpenLayers Heatmap Layer](https://openlayers.org/en/latest/apidoc/module-ol_layer_Heatmap-Heatmap.html)
- [Geohash](https://en.wikipedia.org/wiki/Geohash)
