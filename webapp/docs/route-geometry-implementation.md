# Route Geometry: Implementation Guide

Technical reference for the route segment ordering system. For concepts and data model, see [route-geometry-concepts.md](route-geometry-concepts.md).

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Schema Definition](#schema-definition)
3. [Backend: Geometry Resolution](#backend-geometry-resolution)
4. [Frontend: Save Pipeline](#frontend-save-pipeline)
5. [Frontend: Re-frame Events](#frontend-re-frame-events)
6. [Frontend: Subscriptions](#frontend-subscriptions)
7. [Frontend: Segment Builder UI](#frontend-segment-builder-ui)
8. [Map Integration: Direction Arrows](#map-integration-direction-arrows)
9. [Map Integration: Segment Highlighting](#map-integration-segment-highlighting)
10. [Elevation Profile](#elevation-profile)
11. [Key Files Reference](#key-files-reference)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ Schema (cljc)                                                   │
│   activities.cljc — segment-schema, segments-schema             │
├─────────────────────────────────────────────────────────────────┤
│ Backend (clj)                                                   │
│   core.clj/deref-fids — resolves segments → ordered geometries  │
├─────────────────────────────────────────────────────────────────┤
│ Frontend (cljs)                                                 │
│   ┌─────────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│   │ events.cljs  │→ │ app-db       │→ │ subs.cljs             │ │
│   │ reorder      │  │ :segments    │  │ resolve + connectivity│ │
│   │ toggle dir   │  │ stored as-is │  │ compute lengths       │ │
│   │ remove       │  │              │  │ auto-migrate legacy   │ │
│   └─────────────┘  └──────────────┘  └───────────┬───────────┘ │
│                                                   │             │
│   ┌───────────────────────┐  ┌────────────────────┤             │
│   │ views.cljs            │  │ map/editing.cljs   │             │
│   │ segment-builder UI    │  │ direction arrows   │             │
│   │ hover → highlight     │  │ highlight styles   │             │
│   └───────────────────────┘  └────────────────────┘             │
│                                                                 │
│   utils.cljs/cleanup-geoms — derives :fids from :segments       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Schema Definition

**File**: `src/cljc/lipas/data/activities.cljc`

```clojure
(def segment-schema
  [:map
   [:fid :string]
   [:reversed? {:optional true :default false} :boolean]])

(def segments-schema
  [:sequential segment-schema])
```

The `:segments` field is added to three route schemas:

| Activity | Schema location | Line |
|----------|----------------|------|
| Outdoor recreation | `outdoor-recreation-routes` route schema | ~627 |
| Cycling | `cycling` route schema | ~967 |
| Paddling | `paddling` route schema | ~1325 |

Each as: `[:segments {:optional true} segments-schema]`

---

## Backend: Geometry Resolution

**File**: `src/clj/lipas/backend/core.clj` — `deref-fids`

When routes are served via the API, `deref-fids` resolves segment/fid references into actual GeoJSON geometries. It handles two paths:

```clojure
(defn- deref-fids [sports-site route]
  (let [all-features (get-in sports-site [:location :geometries :features])
        features-by-id (into {} (map (juxt :id identity)) all-features)]
    (if-let [segments (seq (:segments route))]
      ;; New path: ordered + directional
      ;; 1. Iterate segments in order
      ;; 2. Look up each feature by :fid
      ;; 3. If :reversed?, reverse the coordinates vector
      ;; 4. Return ordered FeatureCollection
      ...
      ;; Legacy path: unordered fids set
      ;; Filter features by membership in :fids set
      ...)))
```

**New path** (when `:segments` exists):
- Iterates segments in their defined order
- Resolves each `:fid` to a feature via the lookup map
- Reverses coordinates when `:reversed?` is true
- Returns a FeatureCollection with features in segment order

**Legacy path** (`:fids` only):
- Filters features by set membership (unordered)

Called by `enrich-activities` during API response enrichment.

---

## Frontend: Save Pipeline

**File**: `src/cljs/lipas/ui/utils.cljs` — `cleanup-geoms`

Before saving, `cleanup-geoms` ensures backward compatibility:

```clojure
(defn- cleanup-geoms [activity]
  (cond-> activity
    (seq (:routes activity))
    (update :routes
      (fn [routes]
        (map (fn [route]
               (let [route (dissoc route :geometries
                                   :route-length
                                   :elevation-stats
                                   :segment-details)]
                 (if (seq (:segments route))
                   ;; Derive :fids from :segments for backward compat
                   (assoc route :fids (vec (distinct (map :fid (:segments route)))))
                   ;; Legacy: clean up nil fids
                   (update route :fids #(vec (keep identity %))))))
             routes)))))
```

Key behaviors:
- **Strips computed fields** (`:geometries`, `:route-length`, `:elevation-stats`, `:segment-details`) — these are recomputed on read
- **Derives `:fids` from `:segments`** — ensures the legacy set stays in sync
- Both `:segments` and `:fids` are persisted to the database

---

## Frontend: Re-frame Events

**File**: `src/cljs/lipas/ui/sports_sites/activities/events.cljs`

### Helper Functions

```clojure
(defn- segments->direction-map [segments])
;; Returns {fid -> "start-to-end"|"end-to-start"} for map arrow rendering

(defn- ensure-route-segments [route all-features])
;; Auto-migrates legacy routes: generates :segments from :fids if missing

(defn- get-routes-with-segments [db lipas-id activity-k])
;; Reads routes from db, applying ensure-route-segments to each
```

### Events

| Event | Purpose |
|-------|---------|
| `::init-routes` | On UTP tab open: creates initial route if none exist, sets travel directions on map |
| `::reorder-segments` | Drag/arrow reorder: splices segment at `source-idx` to `target-idx`, updates map arrows |
| `::toggle-segment-direction` | Toggles `:reversed?` on a segment, updates map arrows |
| `::remove-segment` | Removes a segment from the route, updates `:fids` accordingly |
| `::finish-route-details` | Saves route: merges new fids into existing segments, clears map arrows |
| `::select-route` | Opens route for editing: highlights features on map, sets travel directions |
| `::delete-route` | Removes route, clears map highlight and arrows |
| `::cancel-route-details` | Cancels editing, clears map state |

All segment-mutating events follow the pattern:
1. Read current routes via `get-routes-with-segments` (ensures migration)
2. Apply the mutation to the segments vector
3. Dispatch `edit-field` to update the route in app-db
4. Dispatch `::set-segment-directions` to update map arrows

---

## Frontend: Subscriptions

**File**: `src/cljs/lipas/ui/sports_sites/activities/subs.cljs`

### `::routes` Subscription

The main subscription that enriches raw route data with computed fields:

```clojure
(rf/reg-sub ::routes
  (fn [[_ lipas-id _]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])])
  (fn [[edit-data] [_ _lipas-id activity-k]]
    ;; 1. Auto-migrate legacy routes (fids → segments)
    ;; 2. Resolve segments to features (with reversed? applied)
    ;; 3. Compute per-segment details (length, connectivity)
    ;; 4. Compute route totals (total length, elevation)
    ...))
```

### Key computation functions

**`ensure-segments`** — Auto-migration:
```clojure
;; If route has :fids but no :segments, generate segments
;; from features in their array order with :reversed? false
```

**`resolve-segments`** — Feature resolution:
```clojure
;; Look up each segment's feature by :fid
;; Apply coordinate reversal if :reversed? is true
;; Return ordered feature vector
```

**`coords-close?`** — Connectivity check:
```clojure
;; Compare two [x y] coordinates
;; Threshold: 0.00001 degrees (~1 meter at Finnish latitudes)
;; Returns true if both axes are within threshold
```

**`compute-segment-details`** — Per-segment metadata:
```clojure
;; For each segment, computes:
;;   :fid, :reversed?, :length-km, :first-coord, :last-coord
;; Then adds :connected-to-next? by comparing adjacent endpoints
```

The subscription returns routes with these additional computed fields:

| Field | Type | Description |
|-------|------|-------------|
| `:segment-details` | vector of maps | Per-segment: length, connectivity, coordinates |
| `:route-length` | number | Total route length in km |

---

## Frontend: Segment Builder UI

**File**: `src/cljs/lipas/ui/sports_sites/activities/views.cljs` — `segment-builder`

The segment builder renders when a route has more than one segment. Each row shows:

| Element | Description |
|---------|-------------|
| Arrow buttons (up/down) | Reorder via `::reorder-segments` |
| Label | "Osuus N (X.X km)" — auto-numbered with individual length |
| Connectivity dot | Green (connected) or orange (gap) between adjacent segments |
| Reverse button | `swap_horiz` icon, highlighted blue when `:reversed?` is true |
| Remove button | `close` icon, dispatches `::remove-segment` |

The entire row has:
- **Hover highlight**: CSS `&:hover` background via MUI `:sx`
- **Map highlight**: `on-mouse-enter`/`on-mouse-leave` dispatch `::highlight-features` with the segment's feature ID

The component is used in both `single-route` (one-route sites) and `multiple-routes` (multi-route sites with route selection).

---

## Map Integration: Direction Arrows

Direction arrows are rendered on the OpenLayers map to show travel direction along each segment.

### Data Flow

```
::init-routes / ::select-route / ::reorder-segments / ::toggle-segment-direction
  → dispatch ::set-segment-directions {fid → "start-to-end"|"end-to-start"}
    → stored in [:map :mode :segment-directions]
      → map-inner detects mode change
        → editing/update-editing-mode!
          → apply-travel-direction-styles!
            → sets OL feature styles with arrow icons
```

### Direction Storage

**File**: `src/cljs/lipas/ui/map/events.cljs`

```clojure
(rf/reg-event-db ::set-segment-directions
  (fn [db [_ direction-by-fid]]
    (assoc-in db [:map :mode :segment-directions] direction-by-fid)))

(rf/reg-event-db ::clear-segment-directions
  (fn [db _]
    (update-in db [:map :mode] dissoc :segment-directions)))
```

Direction data is stored separately from the geometries — it is display-only and never persisted. The `:segment-directions` map lives within `[:map :mode]` so that changes trigger `update-editing-mode!` in the map component.

### Arrow Rendering

**File**: `src/cljs/lipas/ui/map/styles.cljs`

The `direction-arrows` function iterates over each line segment of a feature's geometry, placing arrow icons:

- Arrow position: at the endpoint of each line segment (for `start-to-end`) or start point (for `end-to-start`)
- Arrow rotation: calculated via `atan2` from the segment's direction vector
- Arrow scale: inversely proportional to map resolution (arrows shrink when zoomed out)
- Icon: SVG arrow loaded as a data URI

Two style functions use `direction-arrows`:
- `line-direction-style-fn` — normal: blue edit stroke + arrows
- `line-direction-highlight-style-fn` — highlighted: red highlight stroke + blue edit stroke + arrows

### Style Application

**File**: `src/cljs/lipas/ui/map/editing.cljs` — `apply-travel-direction-styles!`

Called at the end of both `set-editing-mode!` and `update-editing-mode!`. For each feature on the edits layer:

| Has direction? | Highlighted? | Applied style |
|---------------|-------------|---------------|
| Yes | Yes | `line-direction-highlight-style-fn` |
| Yes | No | `line-direction-style-fn` |
| No | Yes | `[highlight-style, edit-style]` |
| No | No | `nil` (layer default) |

This approach handles highlighting on the edits layer directly, because the edits layer (`zIndex: 10`) renders above the highlights layer (`zIndex: 0`), which would otherwise obscure the highlight.

---

## Elevation Profile

**File**: `src/cljs/lipas/ui/sports_sites/views.cljs`

The elevation profile concatenates elevation data across all segments in route order. When the backend resolves geometries via `deref-fids` respecting segment order, the elevation data returned by the API is automatically in the correct sequence.

The profile displays:
- Combined elevation curve across all segments
- Total ascent and descent
- Distance axis showing cumulative distance from route start

---

## Key Files Reference

| File | Responsibility |
|------|---------------|
| `src/cljc/lipas/data/activities.cljc` | Schema: `segment-schema`, `segments-schema`; route schemas |
| `src/clj/lipas/backend/core.clj` | `deref-fids`: resolves segments to ordered geometries |
| `src/cljs/lipas/ui/utils.cljs` | `cleanup-geoms`: derives `:fids` from `:segments` on save |
| `src/cljs/lipas/ui/sports_sites/activities/events.cljs` | Re-frame events for segment manipulation |
| `src/cljs/lipas/ui/sports_sites/activities/subs.cljs` | `::routes` sub: auto-migration, connectivity, lengths |
| `src/cljs/lipas/ui/sports_sites/activities/views.cljs` | `segment-builder` UI component |
| `src/cljs/lipas/ui/map/events.cljs` | `::set-segment-directions`, `::clear-segment-directions` |
| `src/cljs/lipas/ui/map/editing.cljs` | `apply-travel-direction-styles!`: OL feature styling |
| `src/cljs/lipas/ui/map/styles.cljs` | `direction-arrows`, `line-direction-style-fn`, `line-direction-highlight-style-fn` |
| `src/cljs/lipas/ui/sports_sites/views.cljs` | Concatenated elevation profile |
| `src/cljc/lipas/i18n/{fi,en,se}/utp.edn` | Translations: `:utp/segment`, `:utp/segments`, etc. |
