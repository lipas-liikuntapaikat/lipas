# LIPAS Heatmap Analysis Tool - Implementation Plan

## Overview
Implement an exploratory heatmap analysis tool for sports facilities using Elasticsearch aggregations with server-side query building.

## Key Decisions
- **Data Source**: Elasticsearch only
- **Validation**: Malli for data validation and documentation
- **API Design**: CQRS-style POST endpoints
- **Testing**: Manual browser testing (etaoin as bonus)
- **Performance**: Add caching/debouncing only if needed

## Reuse Existing Search Dimensions

Leverage the existing search functionality filters for consistency:

### Core Filters (from current search implementation)
- **Status**: active, planned, out-of-service
- **Type Codes**: specific facility types
- **City Codes**: geographical filtering
- **Area (m²)**: min/max area range
- **Construction Year**: min/max year range
- **Surface Materials**: specific materials
- **Admins/Owners**: administrative entities
- **Boolean Filters**:
  - `retkikartta?`: sites for retkikartta.fi
  - `harrastuspassi?`: sites for harrastuspassi.fi
  - `school-use?`: school-designated sites

### Heatmap-Specific Aggregations
- **Density**: count of facilities per grid cell
- **Area Coverage**: sum of area-m2 per grid cell
- **Type Distribution**: breakdown by type codes
- **Age Distribution**: average construction year
- **Administrative**: by owner/admin

## Backend Implementation

### 1. API Structure

```clojure
(ns lipas.backend.analysis.heatmap
  (:require [malli.core :as m]
            [lipas.backend.search :as search]))

;; Malli schemas
(def Bbox
  [:map
   [:min-x :double]
   [:max-x :double]
   [:min-y :double]
   [:max-y :double]])

(def HeatmapParams
  [:map
   [:zoom [:int {:min 5 :max 20}]]
   [:bbox Bbox]
   [:dimension [:enum :density :area :capacity :type-distribution
                :year-round :lighting :activities]]
   [:weight-by {:optional true}
    [:enum :count :area-m2 :capacity :route-length-km]]
   [:filters {:optional true}
    [:map
     [:type-codes {:optional true} [:vector :int]]
     [:year-range {:optional true} [:tuple :int :int]]
     [:year-round-only {:optional true} :boolean]]]])

;; CQRS action handler
(defn create-heatmap-handler [req]
  (let [params (:body-params req)]
    (if-let [errors (m/explain HeatmapParams params)]
      (bad-request {:errors (me/humanize errors)})
      (let [es-query (build-es-query params)
            result (search/execute es-query)]
        (ok {:data (transform-to-features result)})))))
```

### 2. Query Building

```clojure
(defn build-es-query [{:keys [zoom bbox dimension weight-by filters]}]
  {:size 0
   :query {:bool {:filter (build-filters bbox filters)}}
   :aggs {:grid
          {:geohash_grid {:field "location"
                         :precision (zoom->precision zoom)}
           :aggs (build-dimension-aggs dimension weight-by)}}})
```

### 3. Routes

```clojure
(require '[reitit.ring :as ring])

(def heatmap-routes
  ["/api/v2/actions"
   ["/create-heatmap" {:post {:handler create-heatmap-handler
                              :parameters {:body HeatmapParams}}}]
   ["/get-heatmap-facets" {:post {:handler get-heatmap-facets-handler
                                   :parameters {:body FacetParams}}}]])
```

## Frontend Implementation

### 1. State Management

```clojure
(ns lipas.ui.analysis.heatmap.db)

(def default-db
  {:dimension :density
   :weight-by :count
   :filters {}
   :visual {:radius 20 :blur 15}
   :loading? false})
```

### 2. Events

```clojure
(ns lipas.ui.analysis.heatmap.events
  (:require [re-frame.core :as rf]))

(rf/reg-event-fx
  ::create-heatmap
  (fn [{:keys [db]} _]
    (let [params (merge
                  (select-keys (:heatmap db) [:dimension :weight-by :filters])
                  (get-map-bounds db))]
      {:http-xhrio {:method :post
                    :uri "/api/v2/actions/create-heatmap"
                    :params params
                    :on-success [::heatmap-created]}})))
```

### 3. View Components

```clojure
(ns lipas.ui.analysis.heatmap.views)

(defn dimension-selector []
  [mui/select
   {:on-change #(rf/dispatch [::events/set-dimension %])}
   [mui/menu-item {:value :density} "Facility Density"]
   [mui/menu-item {:value :area} "Total Area Coverage"]
   [mui/menu-item {:value :capacity} "Capacity Distribution"]
   [mui/menu-item {:value :type-distribution} "Facility Types"]
   [mui/menu-item {:value :year-round} "Year-round Availability"]
   [mui/menu-item {:value :activities} "Activity Distribution"]])

(defn filters-panel []
  [mui/accordion
   [mui/accordion-summary "Filters"]
   [mui/accordion-details
    ;; Type code multi-select
    ;; Year range slider
    ;; Boolean filters (year-round, lighting, etc.)
    ]])
```

### 4. Map Integration

```clojure
;; OpenLayers Heatmap configuration
(defn create-heatmap-layer [initial-params]
  (HeatmapLayer.
    #js {:source (VectorSource.)
         :name   "sports-heatmap"
         :blur   (:blur initial-params 15)
         :radius (:radius initial-params 20)
         :opacity (:opacity initial-params 0.8)
         :gradient (get-gradient (:gradient initial-params :default))
         :weight (create-weight-fn (:weight-fn initial-params :linear))}))

;; Gradient presets
(def gradients
  {:default #js {"0.0" "blue" "0.25" "cyan" "0.5" "green" "0.75" "yellow" "1.0" "red"}
   :cool #js {"0.0" "purple" "0.33" "blue" "0.66" "cyan" "1.0" "white"}
   :warm #js {"0.0" "yellow" "0.5" "orange" "1.0" "red"}
   :grayscale #js {"0.0" "white" "0.5" "gray" "1.0" "black"}
   :accessibility #js {"0.0" "red" "0.5" "yellow" "1.0" "green"}})

;; Weight transformation functions
(defn create-weight-fn [type]
  (case type
    :linear (fn [^js f] (.get f "weight"))
    :logarithmic (fn [^js f] (js/Math.log (inc (.get f "weight"))))
    :exponential (fn [^js f] (js/Math.pow (.get f "weight") 2))
    :sqrt (fn [^js f] (js/Math.sqrt (.get f "weight")))))

;; Dynamic layer updates
(defn update-heatmap-visual! [layer params]
  (doto layer
    (.setRadius (:radius params))
    (.setBlur (:blur params))
    (.setOpacity (:opacity params))
    (.setGradient (get gradients (:gradient params)))
    ;; Note: weight function requires re-creating features
    ))

## Performance Considerations (implement if needed)

### Potential Optimizations:
- **Debouncing**: 300ms for parameter changes
- **Caching**: Client-side cache with 5-minute TTL
- **Request Cancellation**: Cancel outdated requests
- **Progressive Loading**: Load visible area first

## Testing Strategy

### Backend Tests
- Malli schema validation tests
- Query builder unit tests
- Integration tests with test ES instance

### Frontend Tests
- Manual browser testing for PoC
- Bonus: Automated etaoin tests for UI interactions

## Future Enhancements

1. **Additional Data Sources**
   - Population demographics overlay
   - School locations
   - Public transport accessibility

2. **Advanced Features**
   - Time-based animation
   - Comparison mode
   - Custom weight formulas
   - Export functionality

3. **Performance**
   - PostGIS integration for complex spatial queries
   - Pre-computed aggregations for common views

## Endpoint Explanation

### `/api/v2/actions/create-heatmap`
**Purpose**: Generate heatmap data based on current parameters
- **Input**: zoom level, bounding box, dimension, and filters
- **Output**: GeoJSON features with weights for heatmap visualization
- **When called**: Every time the map moves, zoom changes, or filters update
- **Example response**:
  ```json
  {
    "data": [
      {
        "geometry": {"type": "Point", "coordinates": [24.9384, 60.1699]},
        "weight": 15,
        "properties": {"grid_key": "u6drs", "type_count": 3, "total_area": 5000}
      }
    ]
  }
  ```

### `/api/v2/actions/get-heatmap-facets`
**Purpose**: Get available filter options based on current view
- **Input**: bounding box and current filters
- **Output**: Available values for each filter dimension (for UI dropdowns)
- **When called**: On initial load and when bounding box changes significantly
- **Example response**:
  ```json
  {
    "type_codes": [
      {"value": 1110, "label": "Jalkapallohalli", "count": 45},
      {"value": 2210, "label": "Uimahalli", "count": 23}
    ],
    "owners": [
      {"value": "Helsinki", "count": 123},
      {"value": "Espoo", "count": 89}
    ],
    "year_range": {"min": 1950, "max": 2024}
  }
  ```

The facets endpoint enables dynamic filter options - showing only relevant choices based on what's visible on the map, making the exploration more intuitive.

## OpenLayers Heatmap Visualization Knobs

### Visual Parameters for Exploration:

1. **Radius** (5-100px)
   - Controls the size of each heat point
   - Larger radius = more overlap, smoother appearance
   - Smaller radius = more precise, point-like visualization

2. **Blur** (0-50px)
   - Controls the softness of heat edges
   - 0 = sharp edges (good for precise locations)
   - 50 = very soft, cloud-like appearance

3. **Opacity** (0.1-1.0)
   - Layer transparency
   - Allows base map features to show through

4. **Color Gradient**
   - Different color schemes for different use cases:
   - Default: Blue→Cyan→Green→Yellow→Red (classic heat)
   - Cool: Purple→Blue→Cyan (good for density)
   - Warm: Yellow→Orange→Red (good for intensity)
   - Grayscale: For printing/accessibility
   - Accessibility: Red→Yellow→Green (bad→good)

5. **Weight Function**
   - Linear: Direct mapping of values
   - Logarithmic: Emphasizes differences in lower values
   - Exponential: Emphasizes differences in higher values
   - Square Root: Balanced emphasis

6. **Max Intensity**
   - Controls the weight threshold for maximum color
   - Lower values = more areas reach "hot" colors
   - Higher values = only extreme concentrations are "hot"

These knobs allow users to explore the same data in multiple ways, revealing different patterns and insights depending on the visualization settings.
