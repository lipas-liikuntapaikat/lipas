# Map & GIS

LIPAS uses **OpenLayers** for map visualization and **JTS** (Java Topology Suite) for backend geometry operations. This document covers the map system architecture, coordinate handling, and geometry editing.

---

## Table of Contents

1. [Overview](#overview)
2. [Coordinate Systems](#coordinate-systems)
3. [OpenLayers Integration](#openlayers-integration)
4. [Layer Management](#layer-management)
5. [Geometry Types](#geometry-types)
6. [Geometry Editing](#geometry-editing)
7. [Feature Styling](#feature-styling)
8. [Backend GIS Operations](#backend-gis-operations)
9. [Map Interactions](#map-interactions)
10. [Key Files Reference](#key-files-reference)

---

## Overview

The map system consists of:

| Component | Technology | Purpose |
|-----------|------------|---------|
| Frontend Map | OpenLayers 7+ | Interactive map visualization |
| Geometry Editing | OpenLayers interactions | Draw, modify, delete features |
| Backend GIS | JTS + Geo library | Geometry operations, transformations |
| Base Maps | MML (Maanmittauslaitos) WMTS | Finnish national map tiles |
| Projections | proj4js | Coordinate system transformations |

---

## Coordinate Systems

### Primary Projections

| EPSG Code | Name | Usage |
|-----------|------|-------|
| **EPSG:3067** | ETRS-TM35FIN | Display, Finnish national grid |
| **EPSG:4326** | WGS84 | Storage, GeoJSON, APIs |

### Frontend Projection Setup

The projection is registered at application startup:

```clojure
;; src/cljs/lipas/ui/map/projection.cljs

(def epsg3067-extent #js [-548576.0 6291456.0 1548576.0 8388608.0])

(def epsg3067-defs
  (str "+proj=utm"
       "+zone=35"
       "+ellps=GRS80"
       "+towgs84=0,0,0,0,0,0,0"
       "+units=m"
       "+no_defs"))

(defn init! []
  (proj4/defs "EPSG:3067" epsg3067-defs)
  (register proj4)
  ;; ...
  )
```

### Backend Transformations

```clojure
;; src/clj/lipas/backend/gis.clj

(def srid 4326)        ; WGS84 - storage format
(def tm35fin-srid 3067) ; Finnish national grid - display/calculations

(defn wgs84->tm35fin [[lon lat]]
  (let [transformed (jts/transform-geom (jts/point lat lon) srid tm35fin-srid)]
    {:easting (.getX transformed) :northing (.getY transformed)}))

(defn transform-crs
  ([geom] (transform-crs geom srid tm35fin-srid))
  ([geom from-crs to-crs]
   (jts/transform-geom geom from-crs to-crs)))
```

---

## OpenLayers Integration

### Map Initialization

The map is created with Finnish national settings:

```clojure
;; src/cljs/lipas/ui/map/map.cljs

(def mml-resolutions
  #js [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])

(defn init-view [center zoom]
  (ol/View. #js {:center #js [(:lon center) (:lat center)]
                 :extent proj/epsg3067-extent
                 :zoom zoom
                 :projection "EPSG:3067"
                 :resolutions mml-resolutions
                 :units "m"
                 :enableRotation false}))
```

### Map Context

The map system maintains a context object with references to all components:

```clojure
{:lmap    <OpenLayers Map instance>
 :layers  {:basemaps {...}
           :overlays {...}}
 :overlays {:popup <Overlay>}
 :interactions {:draw <Draw>
                :modify <Modify>
                :snap <Snap>
                ...}
 :mode {...}}
```

---

## Layer Management

### Base Maps (WMTS)

Finnish National Land Survey (MML) provides base maps via WMTS:

| Layer | Description |
|-------|-------------|
| `taustakartta` | Background map (default) |
| `maastokartta` | Terrain/topographic map |
| `ortokuva` | Aerial imagery |

```clojure
(def urls
  {:taustakartta (->wmts-url "mml_taustakartta")
   :maastokartta (->wmts-url "mml_maastokartta")
   :ortokuva     (->wmts-url "mml_ortokuva")})
```

### Overlay Layers

Vector layers for different purposes:

| Layer | Purpose | Type |
|-------|---------|------|
| `vectors` | Sports sites display | VectorImageLayer |
| `lois` | Locations of Interest | VectorImageLayer |
| `edits` | Current editing session | VectorLayer |
| `highlights` | Selected features | VectorLayer |
| `markers` | Point markers | VectorLayer |
| `analysis` | Analysis results | VectorLayer |
| `population` | Population grid | VectorImageLayer |
| `schools` | School locations | VectorImageLayer |
| `heatmap` | Heatmap visualization | Special layer |

### WMS Overlays

Additional data from external sources:

| Layer | Source | Data |
|-------|--------|------|
| `light-traffic` | Väylävirasto | Light traffic network |
| `retkikartta-snowmobile-tracks` | Metsähallitus | Snowmobile tracks |
| `mml-kiinteisto` | MML | Property boundaries |
| `mml-kuntarajat` | MML | Municipality boundaries |

---

## Geometry Types

### Supported Types

| Type | Usage | Editing |
|------|-------|---------|
| **Point** | Single location facilities | Click to place |
| **LineString** | Routes, trails, tracks | Draw path |
| **Polygon** | Area facilities, recreation areas | Draw outline |

### GeoJSON Structure

All geometries are stored as GeoJSON FeatureCollections:

```clojure
{:type "FeatureCollection"
 :features [{:type "Feature"
             :geometry {:type "Point"
                        :coordinates [25.0 62.0]}
             :properties {:name "..."}}]}
```

### LineString Properties

Route segments can have per-feature properties:

```clojure
{:type "Feature"
 :geometry {:type "LineString"
            :coordinates [[25.0 62.0] [25.1 62.1]]}
 :properties {:name "Segment 1"
              :type-code 4402
              :route-part-difficulty "demanding"
              :travel-direction "one-way"}}
```

---

## Geometry Editing

### Editing Modes

The editing system supports multiple sub-modes:

| Sub-mode | Description |
|----------|-------------|
| `:drawing` | Add new geometry |
| `:editing` | Modify existing geometry |
| `:deleting` | Remove segments/features |
| `:splitting` | Split LineString at point |
| `:drawing-hole` | Cut holes in polygons |
| `:simplifying` | Reduce vertex count |
| `:travel-direction` | Set route direction |
| `:route-part-difficulty` | Set segment difficulty |

### Drawing New Geometry

```clojure
;; src/cljs/lipas/ui/map/editing.cljs

(defn start-drawing!
  [{:keys [lmap layers] :as map-ctx} geom-type on-draw-end]
  (let [layer  (-> layers :overlays :edits)
        source (.getSource layer)
        draw   (Draw. #js {:snapTolerance 0
                           :source source
                           :type geom-type})]

    (.addInteraction lmap draw)
    (.on draw "drawend"
         (fn [e]
           (let [f (.-feature e)]
             (.setId f (str (random-uuid)))
             ;; ... fix features and callback
             (on-draw-end (map-utils/->geoJSON-clj ...)))))

    (-> map-ctx
        (assoc-in [:interactions :draw] draw)
        enable-snapping!)))
```

### Modifying Geometry

```clojure
(defn start-editing!
  [{:keys [lmap layers] :as map-ctx} geoJSON-feature on-modifyend]
  (let [layer    (-> layers :overlays :edits)
        source   (.getSource layer)
        features (-> geoJSON-feature clj->js map-utils/->ol-features)
        modify   (Modify. #js {:source source})]

    (.clear source)
    (.addFeatures source features)
    (.addInteraction lmap modify)

    (.on modify "modifyend"
         (fn [_]
           (-> source .getFeatures map-utils/->geoJSON-clj on-modifyend)))

    (-> map-ctx
        (assoc-in [:interactions :modify] modify)
        enable-snapping!)))
```

### Snapping

Snapping ensures vertices align with existing geometry:

```clojure
(defn enable-snapping!
  [{:keys [lmap layers] :as map-ctx}]
  (let [layer  (-> layers :overlays :edits)
        source (.getSource layer)
        snap   (Snap. #js {:source source
                           :pixelTolerance 5})]
    (.addInteraction lmap snap)
    (assoc-in map-ctx [:interactions :snap] snap)))
```

### Splitting LineStrings

Split a route at a clicked point:

```clojure
(defn enable-splitting!
  [{:keys [lmap layers] :as map-ctx} geoms on-modify]
  (let [layer  (-> layers :overlays :edits)
        source (.getSource layer)
        split  (Select. #js {:layers #js [layer]
                             :style  styles/hover-style})]

    (.on split "select"
         (fn [e]
           (let [selected (.-selected e)
                 wgs      (proj/toLonLat (.. e -mapBrowserEvent -coordinate) "EPSG:3067")]
             (when (not-empty selected)
               (doseq [f selected]
                 (when-let [splitted (map-utils/split-at-coords f wgs)]
                   ;; Replace original with two new features
                   ...))))))
    ...))
```

### Polygon Holes

Use ol-ext's DrawHole for cutting holes in polygons:

```clojure
(defn start-drawing-hole!
  [{:keys [lmap layers] :as map-ctx} on-modifyend]
  (let [layer     (-> layers :overlays :edits)
        draw-hole (DrawHole. #js {:layers #js [layer]})]
    (.addInteraction lmap draw-hole)
    (.on draw-hole "drawend" ...)
    ...))
```

---

## Feature Styling

### Style Architecture

Styles are defined as OpenLayers Style objects with fills, strokes, and images:

```clojure
;; src/cljs/lipas/ui/map/styles.cljs

(def edit-style
  (Style. #js {:stroke (Stroke. #js {:width 3 :color "blue"})
               :fill (Fill. #js {:color "rgba(255,255,0,0.4)"})
               :image (Circle. #js {:radius 7
                                    :fill (Fill. #js {:color "rgba(255,255,0,0.85)"})
                                    :stroke default-stroke})}))
```

### Vertex Display

LineStrings and Polygons show vertices during editing:

```clojure
(def vertices-style
  (Style. #js {:image (Circle. #js {:radius 5
                                    :stroke (Stroke. #js {:color mui/primary})
                                    :fill (Fill. #js {:color mui/secondary2})})
               :geometry (fn [f]
                           (let [coords (case (-> f .getGeometry .getType)
                                          "Polygon" (-> f .getGeometry .getCoordinates ...)
                                          "LineString" (-> f .getGeometry .getCoordinates)
                                          nil)]
                             (when coords (MultiPoint. coords))))}))
```

### Marker Styles

Point features use SVG markers:

```clojure
(defn ->marker-style [opts]
  (Style. #js {:image (Icon. #js {:src (str "data:image/svg+xml;charset=utf-8,"
                                            (-> opts svg/->marker-str js/encodeURIComponent))
                                  :anchor #js [0.5 0.85]})}))
```

### Dynamic Styling

Style functions allow per-feature styling based on properties:

```clojure
(defn feature-style [feature resolution]
  (let [type-code (.get feature "type-code")
        status    (.get feature "status")
        style-def (get styles/all type-code)]
    (->symbol-style style-def
                    :planned? (= status "planned")
                    :planning? (= status "planning"))))
```

---

## Backend GIS Operations

### JTS Integration

The backend uses JTS for geometry operations:

```clojure
;; src/clj/lipas/backend/gis.clj

;; Convert GeoJSON to JTS
(defn ->jts-geom [f]
  (-> f
      (update :features #(map dummy-props %))
      json/encode
      (gio/read-geojson srid)
      (->> (map :geometry))
      (GeometryCombiner.)
      (.combine)))

;; Calculate centroid
(defn centroid [m]
  (-> m
      (update :features #(map dummy-props %))
      json/encode
      (gio/read-geojson srid)
      (->> (map :geometry))
      jts/geometry-collection
      jts/centroid
      (jts/set-srid srid)
      gio/to-geojson
      (json/decode keyword)))
```

### Geometry Simplification

Reduce vertex count using Douglas-Peucker algorithm:

```clojure
(def default-simplify-tolerance 0.001) ; ~111m

(defn simplify [m tolerance]
  (-> m
      (update :features #(map dummy-props %))
      json/encode
      (gio/read-geojson srid)
      (->> (map #(simplify-geom % tolerance)))
      gio/to-geojson-feature-collection
      (json/decode keyword)))
```

### Buffer Operations

Create buffer zones around geometries:

```clojure
(defn calc-buffer [fcoll distance-m]
  (-> fcoll
      (->jts-geom)
      (jts/transform-geom srid tm35fin-srid)  ; Transform to meters
      (BufferOp.)
      (.getResultGeometry distance-m)
      (jts/transform-geom tm35fin-srid srid)  ; Back to WGS84
      gio/to-geojson
      (json/decode keyword)))
```

### Distance Calculations

```clojure
(defn shortest-distance [g1 g2]
  (DistanceOp/distance g1 g2))

(defn nearest-points [g1 g2]
  (DistanceOp/nearestPoints g1 g2))
```

### Convex/Concave Hull

```clojure
(defn concave-hull [fcoll]
  (let [points (-> fcoll ->flat-coords ->jts-multi-point)
        convex (-> points ConvexHull. .getConvexHull)]
    (.concaveHull hull-tool convex (into [] (.getCoordinates points)))))
```

### Envelope Operations

```clojure
(defn ->tm35fin-envelope [fcoll buff-m]
  (let [envelope (-> fcoll
                     ->jts-geom
                     .getEnvelope
                     (jts/transform-geom srid tm35fin-srid)
                     jts/get-envelope-internal
                     (doto (.expandBy buff-m)))]
    {:max-x (.getMaxX envelope)
     :max-y (.getMaxY envelope)
     :min-x (.getMinX envelope)
     :min-y (.getMinY envelope)}))
```

---

## Map Interactions

### Default Interactions

The map initializes with standard navigation interactions:

```clojure
:interactions #js [(MouseWheelZoom.)
                   (KeyboardZoom.)
                   (KeyboardPan.)
                   (PinchZoom.)
                   (DragPan.)
                   (DoubleClickZoom.)]
```

### Hover Effects

Select interaction for hover highlighting:

```clojure
(def vector-hover
  (SelectInteraction.
    #js {:layers #js [vectors-layer]
         :style styles/feature-style-hover
         :multi true
         :condition events-condition/pointerMove}))
```

### Click Selection

```clojure
(def vector-select
  (SelectInteraction.
    #js {:layers #js [vectors-layer]
         :style styles/feature-style-selected}))

(.on vector-select "select"
     (fn [e]
       (let [selected (.-selected e)]
         (when (seq selected)
           (dispatch-feature-selected (first selected))))))
```

---

## Key Files Reference

### Frontend

| File | Purpose |
|------|---------|
| `src/cljs/lipas/ui/map/map.cljs` | Map initialization, layers |
| `src/cljs/lipas/ui/map/projection.cljs` | EPSG:3067 setup |
| `src/cljs/lipas/ui/map/editing.cljs` | Geometry editing interactions |
| `src/cljs/lipas/ui/map/styles.cljs` | Feature styling |
| `src/cljs/lipas/ui/map/utils.cljs` | Helper functions |
| `src/cljs/lipas/ui/map/events.cljs` | Re-frame events |
| `src/cljs/lipas/ui/map/subs.cljs` | Re-frame subscriptions |
| `src/cljs/lipas/ui/map/db.cljs` | Default state |
| `src/cljs/lipas/ui/map/views.cljs` | React components |
| `src/cljs/lipas/ui/map/import.cljs` | Geometry import (GPX, etc.) |
| `src/cljs/lipas/ui/map/routes.cljs` | Route-specific handling |

### Backend

| File | Purpose |
|------|---------|
| `src/clj/lipas/backend/gis.clj` | JTS geometry operations |
| `src/clj/lipas/backend/geom_utils.clj` | GeoJSON utilities |

### External Dependencies

| Library | Purpose |
|---------|---------|
| `ol` (OpenLayers) | Map visualization |
| `ol-ext` | Extended interactions (DrawHole) |
| `proj4` | Coordinate transformations |
| `geo` (Clojure) | JTS wrapper |
| `JTS` | Java geometry library |

---

## Summary

The LIPAS map system provides:

1. **Finnish-optimized mapping** with MML base layers and EPSG:3067
2. **Full geometry editing** including draw, modify, delete, split
3. **Multiple layer types** for different data visualization needs
4. **Backend GIS operations** via JTS for spatial analysis
5. **Flexible styling** with per-feature dynamic styles
6. **Route-specific features** like travel direction and difficulty
