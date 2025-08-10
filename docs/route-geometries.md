# Route Architecture: Separating Physical Infrastructure from Logical Traversal

## Executive Summary

This document describes a comprehensive architecture for managing sports facility routes in LIPAS, establishing a clear separation between physical infrastructure (geometry segments) and logical traversal patterns (routes). The design enables complex route definitions including ordered traversal, repeated segments, and directional overrides while maintaining backwards compatibility.

## Data Model Hierarchy

```
sports_site (document root)
│
├── location
│   └── geometries (FeatureCollection) ← PHYSICAL WORLD
│       └── features[]
│           ├── id: "segment-123"
│           ├── geometry: {type: "LineString", coordinates: [...]}
│           └── properties: {
│               ├── name: "Forest Trail Section A"
│               ├── travel-direction: "start-to-end"     ← Physical signage/design
│               ├── route-part-difficulty: "3-moderate"  ← Terrain difficulty
│               ├── surface-type: "gravel"              ← Physical material
│               └── lighting: "unlit"                   ← Infrastructure
│           }
│
└── activities
    └── [activity-type] (e.g., "cycling", "hiking")
        └── routes[] ← LOGICAL/CONSUMER VIEW
            ├── route-name: {fi: "Beginners Loop"}
            ├── segments: [                           ← NEW: Ordered traversal
            │   {fid: "segment-123", direction: "forward"},
            │   {fid: "segment-456", direction: "reverse"},
            │   {fid: "segment-123", direction: "reverse"}  ← Same segment, different direction
            ├── ]
            ├── fids: #{"segment-123", "segment-456"}      ← Legacy: Unordered set
            ├── route-length-km: 15.2                      ← Calculated from traversal
            ├── route-duration: "2-3h"                     ← User experience metric
            └── ordering-method: "manual"                  ← How order was determined
```

## Information Storage Principles

### Physical World (location.geometries)
**What**: Infrastructure that exists in the real world
**Where**: `document.location.geometries.features[].properties`
**Examples**:
- Inherent difficulty of terrain
- Physical surface materials
- Installed infrastructure (lighting, signage)
- Preferred/designed travel direction

### Logical Routing (activities.\<type\>.routes)
**What**: How humans traverse the infrastructure
**Where**: `document.activities.<activity>.routes[]`
**Examples**:
- Order of segment traversal
- Direction for each traversal
- Named route combinations
- Activity-specific usage patterns

### Key Principle
> "Segments describe what exists, routes describe how to use it"

## Problem Statement

LIPAS currently stores geometries in a "GIS way" - representing physical structures in the world. However, users need to model "advertised routes" that direct humans how to traverse these geometries. Key challenges:

1. **Sequential Ordering**: Routes need to traverse segments in a specific order
2. **Repeated Segments**: Same segment may be traveled multiple times  
3. **Directional Travel**: Each segment traversal may be in different directions
4. **User Experience**: Data entry personnel need simple tools to define complex routes

## Current Implementation

### Geometry Storage
- Stored as GeoJSON FeatureCollection in PostgreSQL JSONB
- Each LineString feature has unique ID for referencing
- Located at `document.location.geometries` in sports_site table

### Segment-Level Properties (Ad-hoc Implementation)
- **Travel Direction**: Individual segments can have `travel-direction` property
  - Values: `"start-to-end"`, `"end-to-start"`, or `nil`
  - Stored on the segment's geometry properties
  - UI: Click segments in `:travel-direction` mode to toggle
- **Route Difficulty**: Individual segments can have `route-part-difficulty` property
  - Values: Difficulty levels (e.g., "1a-easy" through "5-extremely-challenging")
  - Stored on the segment's geometry properties
  - UI: Click segments in `:route-part-difficulty` mode, select from popup

### Algorithmic Ordering (Cycling Routes)
- **Current**: Type 4412 (cycling routes) use JTS LineSequencer for automatic ordering
- **Implementation**: `gis/sequence-features` in backend, called by `enrich-cycling-route`
- **Limitation**: Works well for simple cases but cannot handle complex routing scenarios

### Virtual Routes (Existing)
- Activities can create routes by selecting segments via `fids` (feature IDs)
- Routes stored under `activities.<activity-type>.routes[]`
- Support for route-level properties (name, length, overall difficulty)
- Route-level `travel-direction` ("clockwise", "counter-clockwise", "no-preference")

### Current Limitations
- `fids` stored as unordered set - no sequence information
- Cannot represent same segment multiple times in a route
- Cannot override segment direction for specific route traversals
- Algorithmic ordering is hardcoded and not overridable
- Segment properties are implemented ad-hoc per activity type

## Proposed Solution

### 1. Unified Segment Property System

Create an extensible framework for segment properties while maintaining backwards compatibility:

```clojure
;; Define standard physical properties schema
(def physical-segment-properties
  [:map
   ;; Existing properties (backwards compatible)
   [:travel-direction {:optional true} 
    [:enum "start-to-end" "end-to-start"]]
   [:route-part-difficulty {:optional true} :string]
   
   ;; Future extensible properties
   [:surface-type {:optional true} :string]
   [:accessibility {:optional true} :string]
   [:seasonal-condition {:optional true} :string]
   [:lighting {:optional true} [:enum "lit" "unlit"]]
   ;; ... more as needed
   ])

;; Activity-specific property requirements
(def activity-segment-properties
  {:cycling [:travel-direction :route-part-difficulty :surface-type]
   :hiking  [:travel-direction :route-part-difficulty :seasonal-condition]
   :skiing  [:travel-direction :lighting]
   ;; ... per activity type
   })
```

**Benefits**:
- Clear schema for all possible segment properties
- Activities declare which properties they use
- UI can dynamically show relevant property editors
- Backwards compatible with existing data

### 2. Manual Route Ordering with Algorithm Assistance

Enhance the route schema to support both manual and algorithmic ordering:

```clojure
;; Enhanced route structure
{:route-name {:fi "Trail name"}
 
 ;; Manual ordering (when provided)
 :segments [{:fid 123 :direction "forward"}
            {:fid 456 :direction "reverse"}
            {:fid 789 :direction "forward"}
            {:fid 456 :direction "forward"}]
 
 ;; Algorithm metadata
 :ordering-method :manual  ; or :algorithmic
 :algorithm-suggestions available? ; flag for UI
 
 ;; Backwards compatibility
 :fids #{123 456 789}}
```

### 3. API Design for Algorithmic Ordering

Expose the JTS LineSequencer algorithm as a reusable service:

```clojure
;; New API endpoint (CQRS style)
POST /api/actions/suggest-route-order
Request: {:lipas-id 12345
          :activity-type :cycling
          :fids [123 456 789]}
Response: {:segments [{:fid 123 :direction "forward"}
                      {:fid 456 :direction "forward"}
                      {:fid 789 :direction "forward"}]
           :confidence :high  ; or :medium, :low
           :warnings ["Multiple valid orderings possible"]}

;; Backend implementation
(defn suggest-route-order-handler
  "CQRS action handler for route ordering suggestions"
  [{:keys [lipas-id activity-type fids]}]
  (let [sports-site (get-sports-site lipas-id)
        feature-collection (get-in sports-site [:location :geometries])
        features (filter #(contains? (set fids) (:id %)) 
                        (:features feature-collection))
        sequenced (gis/sequence-features features)]
    {:segments (map feature->segment-ref sequenced)
     :confidence (assess-confidence sequenced)
     :warnings (identify-routing-issues sequenced)}))
```

### 4. UI Workflow Integration

Combine manual control with algorithmic assistance:

1. **Initial Route Creation**
   - User selects segments on map
   - System offers: "Use automatic ordering?" 
   - If yes: Call suggest-order API, populate segment list
   - If no: Start with selection order

2. **Manual Refinement**
   - Drag to reorder segments
   - Click direction arrows to flip
   - "Reset to suggested order" button available
   - Visual indicator when ordering differs from algorithm

3. **Validation & Feedback**
   - Show route continuity status
   - Highlight potential issues
   - "Re-analyze route" button to get fresh suggestions

### 5. Migration Strategy with Dual Support

```clojure
(defmulti get-route-segments 
  "Gets ordered segments, with fallback logic"
  (fn [route activity-type feature-collection]
    (cond
      ;; Manual ordering takes precedence
      (:segments route) :manual
      
      ;; Cycling uses algorithm if no manual order
      (= activity-type 4412) :algorithmic
      
      ;; Others use legacy unordered
      :else :legacy)))

(defmethod get-route-segments :manual
  [route _ _]
  (:segments route))

(defmethod get-route-segments :algorithmic
  [route _ feature-collection]
  (let [features (get-features-by-ids feature-collection (:fids route))
        sequenced (gis/sequence-features features)]
    (map feature->segment-ref sequenced)))

(defmethod get-route-segments :legacy
  [route _ _]
  ;; Convert unordered fids to basic segments
  (map #(hash-map :fid % :direction "forward") (:fids route)))
```

### 6. Backwards Compatibility Guarantees

1. **Data Compatibility**
   - Existing routes continue to work unchanged
   - Segment properties remain on geometry features
   - New `segments` field is optional

2. **Behavior Compatibility**
   - Cycling routes still get algorithmic ordering by default
   - Manual ordering overrides algorithm when present
   - Other activities unchanged unless opted in

3. **API Compatibility**
   - Existing endpoints unchanged
   - New endpoints are additive only
   - Gradual migration path

## Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)
- [ ] Add `segments` field to route schema
- [ ] Implement segment ordering UI component
- [ ] Create ordering suggestion API endpoint
- [ ] Add get-route-segments multimethod

### Phase 2: UI Integration (Week 3-4)
- [ ] Integrate ordering UI into route creation flow
- [ ] Add "Use suggested order" option
- [ ] Implement drag-and-drop reordering
- [ ] Add direction toggle per segment

### Phase 3: Enhanced Features (Week 5-6)
- [ ] Route continuity validation
- [ ] Visual ordering indicators on map
- [ ] Batch operations (reverse all, optimize)
- [ ] Activity-specific UI customization

### Phase 4: Property System Unification (Future)
- [ ] Define comprehensive segment property schema
- [ ] Migrate existing properties to unified system
- [ ] Create property editor framework
- [ ] Enable activity-specific property sets

## Technical Details

### Direction Resolution

When rendering a route, the effective direction for each segment traversal is computed from both the segment's physical properties and the route's traversal instruction:

```clojure
(defn get-effective-direction 
  "Determines actual traversal direction"
  [segment-direction route-direction]
  (case [segment-direction route-direction]
    ;; If segment has no preference, use route direction
    [nil "forward"] :forward
    [nil "reverse"] :reverse
    
    ;; If segment prefers start-to-end
    ["start-to-end" "forward"] :forward
    ["start-to-end" "reverse"] :reverse
    
    ;; If segment prefers end-to-start
    ["end-to-start" "forward"] :reverse  ; Note the flip!
    ["end-to-start" "reverse"] :forward
    
    ;; Default
    :forward))
```

### Property Access Patterns

```clojure
;; Accessing physical properties (from geometry)
(get-in sports-site [:location :geometries :features 0 :properties :surface-type])

;; Accessing logical properties (from route)
(get-in sports-site [:activities :hiking :routes 0 :segments])

;; Computing derived properties
(calculate-total-route-length route segments)
```

## Summary

This architecture establishes LIPAS as having two complementary data layers:

1. **Infrastructure Layer** (`location.geometries`): What physically exists
2. **Usage Layer** (`activities.*.routes`): How people use it

By clearly separating these concerns while providing bridges between them (like algorithmic ordering assistance and direction resolution), we create a system that is both powerful and maintainable. The key insight remains: segments describe the physical world, routes describe how to traverse it.