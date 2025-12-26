# LIPAS Data Model

This document describes the data model powering LIPAS, Finland's national sports facility registry. The model is **data-driven** and heavily leverages **Malli schemas** for validation, documentation, and code generation.

---

## Table of Contents

1. [Core Design Principles](#core-design-principles)
2. [Sports Sites (Liikuntapaikat)](#sports-sites-liikuntapaikat)
3. [Type Code Hierarchy](#type-code-hierarchy)
4. [Properties System](#properties-system)
5. [Location and Geometry](#location-and-geometry)
6. [Organizations and Ownership](#organizations-and-ownership)
7. [Cities and Regions](#cities-and-regions)
8. [Locations of Interest (LOI)](#locations-of-interest-loi)
9. [Routes and Trails](#routes-and-trails)
10. [Activities (Luontoon.fi)](#activities-luontofi)
11. [Statuses and Lifecycle](#statuses-and-lifecycle)
12. [Malli Schema Patterns](#malli-schema-patterns)
13. [Key Files Reference](#key-files-reference)

---

## Core Design Principles

### Data-Driven Architecture

LIPAS follows a **data-driven** philosophy where:

1. **Static data definitions** in `src/cljc/lipas/data/` describe available types, properties, owners, etc.
2. **Malli schemas** in `src/cljc/lipas/schema/` provide validation, derived from the static data
3. **Schemas are shared** between frontend (ClojureScript) and backend (Clojure) via `.cljc` files
4. **Type-specific behavior** is determined by looking up data structures, not hardcoded logic

### Append-Only Event Sourcing

The core entity (sports sites) uses an append-only pattern:
- Every edit creates a new revision with shared `lipas-id`
- Revisions are never updated or deleted
- Current state is derived via database views
- Full audit trail is preserved

### Multilingual by Default

All user-facing text supports three languages:
```clojure
{:fi "Finnish text"
 :se "Swedish text"
 :en "English text"}
```

---

## Sports Sites (Liikuntapaikat)

A **sports site** is the core entity representing any sports or recreational facility in Finland.

### Document Structure

```clojure
{:lipas-id      123456                           ; Permanent facility identifier
 :event-date    "2024-01-15T10:30:00.000Z"       ; When this revision became valid
 :status        "active"                          ; Lifecycle status

 ;; Identity
 :name          "Olympiastadion"                  ; Official name (required)
 :marketing-name "Helsinki Olympic Stadium"       ; Marketing/common name (optional)
 :name-localized {:se "..." :en "..."}           ; Translations (optional)

 ;; Classification
 :type          {:type-code 1520}                 ; Facility type (determines properties)

 ;; Ownership
 :owner         "city"                            ; Owner entity
 :admin         "city-sports"                     ; Administrative entity

 ;; Location
 :location      {:city {:city-code 91}
                 :address "Paavo Nurmen tie 1"
                 :postal-code "00250"
                 :postal-office "Helsinki"
                 :geometries {...}}               ; GeoJSON FeatureCollection

 ;; Contact
 :email         "info@olympiastadion.fi"
 :phone-number  "+358 9 123 4567"
 :www           "https://olympiastadion.fi"
 :reservations-link "https://booking.example.com"

 ;; History
 :construction-year 1938
 :renovation-years [1994 2020]

 ;; Type-specific attributes
 :properties    {:stand-capacity-person 36000
                 :surface-material ["grass"]
                 :ligthing? true
                 ...}

 ;; Additional content (optional)
 :comment       "Additional notes..."
 :activities    {...}                             ; Luontoon.fi integration
 :fields        [...]                             ; Floorball-specific
 :locker-rooms  [...]                             ; Floorball-specific
 :circumstances {...}                             ; Floorball-specific
 :audits        [...]}                            ; Facility audits
```

### Schema Implementation

The sports site schema is a **multi-schema** that dispatches on `:type-code`:

```clojure
;; src/cljc/lipas/schema/sports_sites.cljc

(def sports-site
  (into [:multi {:dispatch (fn [x] (-> x :type :type-code))}]
        (for [[type-code type-def] types/all]
          [type-code (make-sports-site-schema
                      {:type-codes #{type-code}
                       :location-schema (case (:geometry-type type-def)
                                          "Point" #'location-schema/point-location
                                          "LineString" #'location-schema/line-string-location
                                          "Polygon" #'location-schema/polygon-location)
                       :extras-schema (-> [:map]
                                          (add-properties-schema props)
                                          (add-activities-schema activity))})])))
```

This approach:
- Validates each facility against type-appropriate rules
- Allows different geometry types per facility type
- Includes only valid properties for each type

---

## Type Code Hierarchy

Facilities are classified in a **three-level hierarchy**:

```
Main Category (8 categories: 0, 1000-7000)
└── Sub-Category (e.g., 1300, 1500, 2100)
    └── Type (specific type-code like 1520, 2240)
```

### Main Categories

| Code | Finnish                             | English                                |
|------|-------------------------------------|----------------------------------------|
| 0    | Virkistyskohteet ja palvelut        | Recreational destinations and services |
| 1000 | Ulkokentät ja liikuntapuistot       | Outdoor fields and sports parks        |
| 2000 | Sisäliikuntatilat                   | Indoor sports facilities               |
| 3000 | Vesiliikuntapaikat                  | Water sports facilities                |
| 4000 | Maastoliikuntapaikat                | Cross-country sports facilities        |
| 5000 | Veneily, ilmailu ja moottoriurheilu | Boating, aviation and motor sports     |
| 6000 | Eläinurheilualueet                  | Animal sports areas                    |
| 7000 | Huoltorakennukset                   | Maintenance/service buildings          |

### Type Definition Structure

Each type is defined as a map:

```clojure
;; src/cljc/lipas/data/types_new.cljc

{1530  ; Type code
 {:name {:fi "Kaukalo" :se "Rink" :en "Rink"}
  :description {:fi "Luisteluun, jääkiekkoon..." :se "..." :en "..."}
  :tags {:fi ["jääkiekkokaukalo"]}
  :type-code 1530
  :main-category 1000
  :sub-category 1500
  :geometry-type "Point"                    ; Point, LineString, or Polygon
  :status "active"                          ; active, deprecated, etc.
  :props                                    ; Allowed properties with priorities
  {:surface-material {:priority 80}
   :stand-capacity-person {:priority 70}
   :field-length-m {:priority 90}
   :ligthing? {:priority 60}
   ...}}}
```

### Key Lookups

```clojure
;; Get all active types
lipas.data.types/active

;; Get types by category
lipas.data.types/by-main-category
lipas.data.types/by-sub-category

;; Resolve type with full details
(lipas.data.types/->type (get types/all 1520))
```

---

## Properties System

Properties are **type-specific attributes** with consistent metadata. The property system is defined in `lipas.data.prop-types`.

### Property Definition

Each property includes:

```clojure
{:height-m
 {:name {:fi "Tilan korkeus m" :se "..." :en "Venue's height"}
  :data-type "numeric"                      ; numeric, boolean, string, enum, enum-coll
  :description {:fi "Sisäliikuntatilan korkeus..." :se "..." :en "..."}}}

{:surface-material
 {:name {:fi "Pintamateriaali" :se "..." :en "Surface material"}
  :data-type "enum-coll"                    ; Multiple selection
  :description {...}
  :opts                                      ; Available options
  {"grass" {:label {:fi "Nurmi" :se "Gräs" :en "Grass"}}
   "asphalt" {:label {:fi "Asfaltti" :se "Asfalt" :en "Asphalt"}}
   "artificial-turf" {:label {:fi "Tekonurmi" :se "Konstgräs" :en "Turf"}}
   ...}}}
```

### Data Types

| Type        | Description                     | Malli Schema                |
|-------------|---------------------------------|-----------------------------|
| `numeric`   | Numeric value (int or double)   | `number?`                   |
| `boolean`   | True/false                      | `boolean?`                  |
| `string`    | Free text                       | `[:string {:max N}]`        |
| `enum`      | Single selection from options   | `[:enum "opt1" "opt2" ...]` |
| `enum-coll` | Multiple selection from options | `[:set [:enum ...]]`        |

### Property-Type Binding

Types declare which properties they support via `:props`:

```clojure
;; In type definition
:props {:stand-capacity-person {:priority 70}
        :surface-material {:priority 80}
        ...}

;; Schema generation selects matching property schemas
(select-keys prop-types/schemas (keys (:props type-def)))
```

The `:priority` controls UI ordering (higher = shown first).

---

## Location and Geometry

### Location Structure

```clojure
{:city {:city-code 91                      ; Official municipality code
        :neighborhood "Töölö"}              ; Optional area name
 :address "Paavo Nurmen tie 1"             ; Street address
 :postal-code "00250"                      ; Finnish postal code
 :postal-office "Helsinki"                 ; Optional postal office name
 :geometries                               ; GeoJSON FeatureCollection
 {:type "FeatureCollection"
  :features [{:type "Feature"
              :geometry {:type "Point"
                         :coordinates [24.9384 60.1879]}
              :properties {}}]}}
```

### Geometry Types

Each facility type specifies allowed geometry:

| Geometry       | Use Case                   | Examples                       |
|----------------|----------------------------|--------------------------------|
| **Point**      | Single location facilities | Fields, halls, pools           |
| **LineString** | Linear facilities          | Trails, ski tracks, routes     |
| **Polygon**    | Area facilities            | Recreation areas, golf courses |

### Coordinate Systems

**Storage**: WGS84 (EPSG:4326) - `[longitude, latitude, altitude?]`

**Display**: ETRS-TM35FIN (EPSG:3067) - Finnish national grid

Coordinates are validated against Finland bounds:
- Longitude: 18.0° - 33.0°E
- Latitude: 59.0° - 71.0°N
- Altitude: -10,000m to +2,000m (accommodates fallback values)

### LineString Features

Routes/trails can have additional properties per segment:

```clojure
{:type "Feature"
 :geometry {:type "LineString"
            :coordinates [[25.0 60.0] [25.1 60.1] ...]}
 :properties {:name "Luontopolku"
              :type-code 4402
              :route-part-difficulty "easy"
              :travel-direction "two-way"}}
```

---

## Organizations and Ownership

### Owner Types

```clojure
;; src/cljc/lipas/data/owners.cljc

{"city"                   {:fi "Kunta" :en "Municipality" :se "Kommun"}
 "registered-association" {:fi "Rekisteröity yhdistys" :en "Registered association" :se "Registrerad förening"}
 "company-ltd"            {:fi "Yritys" :en "Company ltd" :se "Aktiebolag"}
 "city-main-owner"        {:fi "Kuntaenemmistöinen yritys" :en "Municipality major owner" :se "..."}
 "municipal-consortium"   {:fi "Kuntayhtymä" :en "Municipal consortium" :se "Samkommun"}
 "foundation"             {:fi "Säätiö" :en "Foundation" :se "Stiftelse"}
 "state"                  {:fi "Valtio" :en "State" :se "Staten"}
 "other"                  {:fi "Muu" :en "Other" :se "Annan"}
 "no-information"         {:fi "Ei tietoa" :en "No information" :se "..."}}
```

### Administrator Types

```clojure
;; src/cljc/lipas/data/admins.cljc

{"city-sports"             {:fi "Kunta / liikuntatoimi" :en "Municipality / Sports" :se "..."}
 "city-education"          {:fi "Kunta / opetustoimi" :en "Municipality / Education" :se "..."}
 "city-technical-services" {:fi "Kunta / tekninen toimi" :en "Municipality / Technical services" :se "..."}
 "city-other"              {:fi "Kunta / muu" :en "Municipality / Other" :se "..."}
 "municipal-consortium"    {:fi "Kuntayhtymä" :en "Municipal consortium" :se "..."}
 "private-association"     {:fi "Rekisteröity yhdistys" :en "Private / Association" :se "..."}
 "private-company"         {:fi "Yritys" :en "Private / Company" :se "..."}
 "private-foundation"      {:fi "Säätiö" :en "Private / Foundation" :se "..."}
 "state"                   {:fi "Valtio" :en "State" :se "..."}
 "other"                   {:fi "Muu" :en "Other" :se "..."}
 "no-information"          {:fi "Ei tietoa" :en "No information" :se "..."}}
```

---

## Cities and Regions

### Geographic Hierarchy

```
AVI (Regional State Administrative Agency) - 7 regions
└── Province (Maakunta) - 19 provinces
    └── Municipality (Kunta) - ~300 cities
```

### City Data Structure

```clojure
;; src/cljc/lipas/data/cities.cljc

{:name {:fi "Helsinki" :se "Helsingfors" :en "Helsinki"}
 :city-code 91                              ; Official Statistics Finland code
 :province-id 1                             ; Links to province
 :status "valid"                            ; valid or abolished
 :valid-until nil}                          ; Set when municipality merged
```

### Key Lookups

```clojure
lipas.data.cities/by-city-code    ; {91 {:name {...} ...}}
lipas.data.cities/provinces       ; Province definitions
lipas.data.cities/avi-areas       ; AVI region definitions
```

---

## Locations of Interest (LOI)

LOIs are **non-facility entities** that complement sports site data. They're used for outdoor recreation services (Luontoon.fi integration).

### LOI Categories

```
Category → Type → Properties
```

| Category                            | Description                | Example Types                                |
|-------------------------------------|----------------------------|----------------------------------------------|
| water-conditions                    | Water hazards and features | hazard, rapid, boat-lane                     |
| outdoor-recreation-facilities       | Recreation infrastructure  | fire-pit, tent-site, parking-spot, viewpoint |
| natural-attractions-and-geo-objects | Natural features           | geo-object, natural-attraction               |
| protected-areas                     | Access-restricted zones    | nature-reserve                               |

### LOI Structure

```clojure
{:id "uuid-here"
 :event-date "2024-01-15T10:30:00.000Z"
 :status "active"
 :loi-category "outdoor-recreation-facilities"
 :loi-type "fire-pit"
 :geometries {:type "FeatureCollection" :features [...]}

 ;; Type-specific properties
 :name {:fi "Nuotiopaikka" :se "..." :en "Fire pit"}
 :description {:fi "..." :se "..." :en "..."}
 :images [{:url "..." :description {...} :alt-text {...} :copyright {...}}]
 :accessible? true
 :accessibility {:fi "Esteetön kulku..." :en "..."}
 :use-structure-during-fire-warning true}
```

### LOI Schema

The LOI schema is also a multi-schema, dispatching on `:loi-type`:

```clojure
;; src/cljc/lipas/schema/lois.cljc

(def loi
  (into [:multi {:dispatch :loi-type}]
        (for [[cat-k cat-v] loi/categories
              [_type-k type-v] (:types cat-v)]
          [(:value type-v)
           [:map
            [:id loi-id]
            [:event-date ...]
            [:geometries (case (:geom-type type-v) ...)]
            [:loi-category [:enum cat-k]]
            [:loi-type [:enum (:value type-v)]]
            ;; Type-specific props
            ...]])))
```

---

## Routes and Trails

Routes are sports sites with **LineString geometry**. They have special handling:

### Route Properties

Route segments can include per-feature properties:

```clojure
{:name "Karhunkierros"
 :type-code 4401                            ; Hiking trail
 :route-part-difficulty "demanding"
 :travel-direction "one-way"}
```

### Elevation Enrichment

Routes trigger **elevation enrichment** jobs:
1. After save, a job fetches elevation data from external API
2. Altitude values are added to coordinates
3. Route statistics (total ascent/descent) are computed

---

## Activities (Luontoon.fi)

The `:activities` field contains enriched content for [Luontoon.fi](https://luontoon.fi), a Finnish outdoor recreation service.

### Activity Types

| Activity Key                     | Use Case                                 |
|----------------------------------|------------------------------------------|
| `:outdoor-recreation-areas`      | Recreation areas                         |
| `:outdoor-recreation-facilities` | Facilities like lean-tos, campfire sites |
| `:outdoor-recreation-routes`     | Hiking trails, nature paths              |
| `:cycling`                       | Cycling routes                           |
| `:paddling`                      | Paddling routes and waters               |
| `:birdwatching`                  | Birdwatching sites                       |
| `:fishing`                       | Fishing locations                        |

### Activity Structure

Activities add rich content beyond basic facility data:

```clojure
{:activities
 {:outdoor-recreation-routes
  {:route-name {:fi "..." :se "..." :en "..."}
   :description {:fi "..." :se "..." :en "..."}
   :rules ["everymans-rights-valid" "fire-only-at-marked-fireplaces"]
   :highlights [{:type "flora" :description {...}}]
   :images [{:url "..." :description {...} :copyright {...}}]
   :duration {:min 2 :max 4 :unit "hours"}
   :arrival {:public-transport {:fi "..."} :parking {:fi "..."}}
   :contacts [{:role "admin" :organization "..." :phone "..." :email "..."}]
   ...}}}
```

---

## Statuses and Lifecycle

### Facility Status

```clojure
;; src/cljc/lipas/data/status.cljc

{"planning"                   ; Draft/planning phase
 "planned"                    ; Approved but not built
 "active"                     ; In operation
 "out-of-service-temporarily" ; Temporarily closed
 "out-of-service-permanently" ; Permanently closed
 "incorrect-data"}            ; Data quality issue
```

### Document Status

Separate from facility status, documents have publication status:
- `"published"` - Visible to all, included in search
- `"draft"` - Only visible to author, excluded from current views

---

## Malli Schema Patterns

### Multi-Schema Pattern

Type-specific validation via dispatch:

```clojure
[:multi {:dispatch (fn [x] (-> x :type :type-code))}
 [1520 ice-skating-field-schema]
 [1530 rink-schema]
 [2240 sports-hall-schema]
 ...]
```

### Optional Fields Pattern

Most fields are optional with explicit documentation:

```clojure
[:map
 [:required-field :string]
 [:optional-field {:optional true :description "..."} :string]]
```

### Schema Composition

Schemas are composed using `malli.util/merge`:

```clojure
(mu/merge
 base-sports-site-schema
 (when (seq props) [:map [:properties properties-schema]])
 (when activity [:map [:activities activity-schema]]))
```

### JSON Compatibility

For API responses, special schemas handle JSON encoding:

```clojure
;; Prevent integer enums from becoming strings
[:enum {:encode/json identity} 1520 1530 2240 ...]

;; Use sports-site-compat for API response coercion
```

---

## Key Files Reference

### Static Data

| File                                  | Content                                  |
|---------------------------------------|------------------------------------------|
| `src/cljc/lipas/data/types.cljc`      | Type code definitions and lookups        |
| `src/cljc/lipas/data/types_new.cljc`  | Current type definitions (2024 overhaul) |
| `src/cljc/lipas/data/prop_types.cljc` | Property definitions (~200 properties)   |
| `src/cljc/lipas/data/owners.cljc`     | Owner entity types                       |
| `src/cljc/lipas/data/admins.cljc`     | Administrator entity types               |
| `src/cljc/lipas/data/cities.cljc`     | Municipalities, provinces, AVI regions   |
| `src/cljc/lipas/data/status.cljc`     | Lifecycle status definitions             |
| `src/cljc/lipas/data/loi.cljc`        | LOI categories, types, properties        |
| `src/cljc/lipas/data/activities.cljc` | Activity schemas for Luontoon.fi         |
| `src/cljc/lipas/data/materials.cljc`  | Surface material options                 |

### Malli Schemas

| File                                                    | Content                                    |
|---------------------------------------------------------|--------------------------------------------|
| `src/cljc/lipas/schema/sports_sites.cljc`               | Main sports site schema                    |
| `src/cljc/lipas/schema/sports_sites/location.cljc`      | Location and geometry schemas              |
| `src/cljc/lipas/schema/sports_sites/types.cljc`         | Type-specific schema helpers               |
| `src/cljc/lipas/schema/sports_sites/activities.cljc`    | Activity content schemas                   |
| `src/cljc/lipas/schema/sports_sites/fields.cljc`        | Floorball fields schema                    |
| `src/cljc/lipas/schema/sports_sites/circumstances.cljc` | Floorball circumstances schema             |
| `src/cljc/lipas/schema/lois.cljc`                       | LOI schemas                                |
| `src/cljc/lipas/schema/common.cljc`                     | Shared schemas (coordinates, status, etc.) |
| `src/cljc/lipas/schema/core.cljc`                       | Low-level schema primitives                |

### Database Layer

| File                                       | Content                         |
|--------------------------------------------|---------------------------------|
| `resources/sql/sports_site.sql`            | HugSQL queries for sports sites |
| `resources/sql/loi.sql`                    | HugSQL queries for LOIs         |
| `src/clj/lipas/backend/db/sports_site.clj` | Sports site DB operations       |
| `src/clj/lipas/backend/db/loi.clj`         | LOI DB operations               |

---

## Summary

The LIPAS data model demonstrates effective data-driven design:

1. **Single source of truth**: Static data definitions drive both validation and UI
2. **Type-safe flexibility**: Multi-schemas validate type-specific attributes
3. **Multilingual**: All text supports fi/se/en from the ground up
4. **Shared code**: Frontend and backend use identical schemas
5. **Event-sourced**: Full history preserved via append-only storage
6. **GIS-native**: GeoJSON geometry with proper coordinate validation
