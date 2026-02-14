# Route Geometry: Concepts and Data Model

This document describes how LIPAS models route geometries — the relationship between physical map segments and the virtual routes built from them.

---

## Table of Contents

1. [Overview](#overview)
2. [Physical Segments](#physical-segments)
3. [Virtual Routes](#virtual-routes)
4. [Segment Direction](#segment-direction)
5. [Shared Segment Networks](#shared-segment-networks)
6. [Connectivity](#connectivity)
7. [Data Model](#data-model)
8. [Backward Compatibility](#backward-compatibility)

---

## Overview

LIPAS route-type sports sites (ski trails, hiking routes, cycling routes, paddling routes) store their geometry as a GeoJSON FeatureCollection containing one or more LineString features. Each feature represents a physical segment of infrastructure — a stretch of trail, road, or waterway.

A **route** is a logical traversal of these physical segments in a specific order and direction. The same physical segments can be shared across multiple routes, each defining its own sequence.

```
Physical segments (FeatureCollection)     Virtual routes
┌─────────────────────────────────┐       ┌───────────────────┐
│  Feature A ─────────            │       │ 3 km route:       │
│  Feature B      ─────────      │       │   A → B → C       │
│  Feature C           ─────     │       │                   │
│  Feature D ───────             │       │ 5 km route:       │
│                                │       │   D → A → B → C   │
└─────────────────────────────────┘       └───────────────────┘
```

---

## Physical Segments

Physical segments live in the sports site's location geometry:

```
sports-site → :location → :geometries → :features
```

Each feature is a GeoJSON LineString with a unique `:id` (UUID string). These represent the actual physical infrastructure on the ground — a piece of trail between two junctions, a road section, a waterway stretch.

Physical segments are:

- **Drawn on the map** by the user using OpenLayers editing tools
- **Shared** across all routes belonging to the same sports site
- **Unordered** in the FeatureCollection — their array position has no semantic meaning
- **Bidirectional** — a segment can be traversed in either direction

---

## Virtual Routes

A virtual route defines a specific traversal through a subset of the physical segments. Each route specifies:

1. **Which segments** to include (by feature ID)
2. **What order** to traverse them
3. **Which direction** to travel along each segment

This is stored in the `:segments` field — an ordered vector of segment references:

```clojure
{:id       "route-uuid"
 :route-name {:fi "Luontopolku" :en "Nature trail" :se "Naturstig"}
 :segments [{:fid "feature-a-uuid" :reversed? false}
            {:fid "feature-b-uuid" :reversed? true}
            {:fid "feature-c-uuid" :reversed? false}]
 :fids     #{"feature-a-uuid" "feature-b-uuid" "feature-c-uuid"}}
```

The vector order is the route order. This determines:

- **Elevation profiles** — elevations are concatenated in segment order
- **Route descriptions** — "first through forest, then along the lake"
- **Map visualization** — directional arrows show the travel direction

---

## Segment Direction

Each physical segment has a natural coordinate order (the order in which the LineString's coordinates were originally drawn). The `:reversed?` flag controls traversal direction:

| `:reversed?` | Direction | Meaning |
|--------------|-----------|---------|
| `false` | start-to-end | Traverse coordinates in their natural order |
| `true` | end-to-start | Traverse coordinates in reverse order |

Reversing a segment flips:
- The direction of travel arrows shown on the map
- The coordinate order used for elevation profile computation
- The endpoint used for connectivity checking

The physical geometry in the database is never modified — `:reversed?` only affects how the segment is interpreted at read time.

---

## Shared Segment Networks

Multiple routes within the same sports site can reference the same physical segments in different combinations and orders. This models real-world trail networks where routes overlap:

```
Physical segments:
  A: Parking lot → Forest junction
  B: Forest junction → Lake viewpoint
  C: Lake viewpoint → Summit
  D: Forest junction → River bridge

Short route (3 km):  A → B          (parking to lake)
Long route (8 km):   A → B → C      (parking to summit)
River route (5 km):  A → D          (parking to river)
Loop route (6 km):   A → D → D̃ → A̅  (out and back via river)
```

Each route is independent — reordering segments in one route does not affect other routes.

---

## Connectivity

The system computes **connectivity** between adjacent segments in a route by comparing their endpoints:

- **Connected**: The last coordinate of segment N is within ~1 meter of the first coordinate of segment N+1
- **Gap**: The endpoints are more than ~1 meter apart

Connectivity is displayed in the segment builder UI:
- Green dot between connected segments
- Orange dot and orange border between segments with a gap

Gaps indicate that segments may need reordering or reversing to form a continuous path. However, gaps are not errors — some routes intentionally have discontinuities (e.g., a route that involves a ferry crossing or a drive between trailheads).

---

## Data Model

### Route Schema

Routes appear within activity data at:

```
sports-site → :activities → <activity-key> → :routes → [route, ...]
```

Where `<activity-key>` is `:outdoor-recreation-routes`, `:cycling`, or `:paddling`.

### Route Fields

| Field | Type | Description |
|-------|------|-------------|
| `:id` | UUID string | Unique route identifier |
| `:route-name` | `{:fi :se :en}` | Localized route name |
| `:segments` | `[{:fid :reversed?}, ...]` | Ordered segment references (primary) |
| `:fids` | set of strings | Unordered feature ID set (derived, for backward compat) |
| `:geometries` | FeatureCollection | Resolved geometry (computed at read time, not stored) |

### Segment Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `:fid` | string | required | Feature ID referencing a LineString in the FeatureCollection |
| `:reversed?` | boolean | `false` | Whether to reverse the coordinate order |

### Malli Schema

```clojure
(def segment-schema
  [:map
   [:fid :string]
   [:reversed? {:optional true :default false} :boolean]])

(def segments-schema
  [:sequential segment-schema])
```

The `:segments` field is added as `[:segments {:optional true} segments-schema]` to route schemas for outdoor recreation, cycling, and paddling activities.

---

## Backward Compatibility

The `:segments` field is additive — it coexists with the legacy `:fids` set:

- **On save**: `:fids` is automatically derived from `:segments` (the set of unique feature IDs). This ensures API consumers that read `:fids` continue to work.
- **On load**: If a route has `:fids` but no `:segments`, the system auto-migrates by generating segments from the feature array in their default order with `:reversed? false`.
- **API output**: The `deref-fids` function on the backend respects `:segments` order and direction when building the route's resolved `:geometries`.

This means existing routes created before the segment ordering feature continue to work — they just lack explicit ordering until a user opens the route builder and saves.
