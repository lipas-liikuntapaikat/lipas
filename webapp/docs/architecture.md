# LIPAS Architecture

> **LIPAS** (Liikuntapaikat.fi) is a national sports facility registry for Finland, providing comprehensive data about sports facilities, their locations, and associated services.

This document describes the key architectural decisions, design patterns, and heuristics that shape the LIPAS system.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Core Design Principles](#core-design-principles)
3. [Technology Stack](#technology-stack)
4. [Data Architecture](#data-architecture)
5. [Backend Architecture](#backend-architecture)
6. [Frontend Architecture](#frontend-architecture)
7. [Infrastructure](#infrastructure)
8. [Integration Architecture](#integration-architecture)
9. [Analysis Capabilities](#analysis-capabilities)
10. [Development Philosophy](#development-philosophy)

---

## System Overview

LIPAS is a **distributed GIS application** consisting of:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         LIPAS Architecture                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│  │   Web UI     │    │   REST API   │    │   Worker     │          │
│  │  (Browser)   │◄──►│   (Jetty)    │    │  (Background)│          │
│  │              │    │              │    │              │          │
│  │  Re-frame    │    │   Reitit     │    │  Job Queue   │          │
│  │  OpenLayers  │    │   Ring       │    │  Processing  │          │
│  └──────────────┘    └──────┬───────┘    └──────┬───────┘          │
│                             │                   │                   │
│                             ▼                   ▼                   │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                    Core Business Logic                    │      │
│  │                   (lipas.backend.core)                    │      │
│  └──────────────────────────┬───────────────────────────────┘      │
│                             │                                       │
│         ┌───────────────────┼───────────────────┐                  │
│         ▼                   ▼                   ▼                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│  │  PostgreSQL  │    │Elasticsearch │    │  External    │          │
│  │   (PostGIS)  │    │   (Search)   │    │  Services    │          │
│  │              │    │              │    │              │          │
│  │Source of     │    │Read-optimized│    │ PTV, OSRM,   │          │
│  │Truth         │    │Index         │    │ GeoServer... │          │
│  └──────────────┘    └──────────────┘    └──────────────┘          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**
- **Single-page application** with ClojureScript frontend
- **RESTful API** serving JSON data
- **Background job processing** for async operations
- **Event-sourced data model** with full audit trail
- **GIS-first design** with rich geospatial capabilities

---

## Core Design Principles

### 1. Immutability Over Mutability

**Opinion:** Data should be append-only. Every change creates a new revision rather than mutating existing records.

**Implementation:** The `sports_site` table stores every revision. A database view (`sports_site_current`) exposes only the latest revision per facility. This provides:
- Complete audit trail
- Point-in-time queries
- Safe concurrent modifications
- Easy rollback capabilities

### 2. Separation of Read and Write Paths

**Opinion:** Read and write operations have different performance characteristics and should be optimized independently.

**Implementation:**
- **PostgreSQL** is the source of truth for writes
- **Elasticsearch** serves all read/search operations
- Write path: API → Validate → DB → Enrich → Index
- Read path: API → Elasticsearch (bypasses DB entirely)

### 3. Data Enrichment at Index Time

**Opinion:** Denormalize data at write time to optimize reads. N+1 queries at read time are unacceptable.

**Implementation:** The `enrich*` function transforms raw database documents before indexing:
- Resolves reference codes to human-readable names
- Computes derived fields (categories, centroids)
- Formats geometries for Elasticsearch geo queries
- Adds search metadata for faceted filtering

### 4. Component-Based Architecture

**Opinion:** Systems should be composed of isolated, lifecycle-managed components with explicit dependencies.

**Implementation:** Integrant manages all stateful components:
```clojure
:lipas/db      → Database connection pool
:lipas/search  → Elasticsearch client
:lipas/server  → Jetty web server
:lipas/emailer → SMTP email service
:lipas/ptv     → PTV integration client
```

Benefits:
- Clear dependency graph
- Easy testing (swap components)
- Graceful startup/shutdown
- REPL-friendly development

### 5. Schema-First Validation

**Opinion:** Data validation should be declarative, composable, and shared between frontend and backend.

**Implementation:** Malli schemas in `src/cljc/` define data structures used by both Clojure and ClojureScript:
- Input validation at API boundaries
- Form field validation in UI
- Documentation generation
- Test data generation

---

## Technology Stack

### Backend (Clojure)

| Component    | Technology           | Purpose                          |
|--------------|----------------------|----------------------------------|
| Language     | Clojure 1.12         | JVM-based functional programming |
| HTTP Server  | Jetty + Ring         | Web server and middleware        |
| Routing      | Reitit               | Data-driven routing with OpenAPI |
| Validation   | Malli                | Schema definition and validation |
| Database     | PostgreSQL + PostGIS | Relational storage with spatial  |
| Search       | Elasticsearch 7.x    | Full-text search and analytics   |
| DI/Lifecycle | Integrant            | Component management             |
| SQL          | HugSQL               | SQL-first database access        |
| Migrations   | Migratus             | Database schema migrations       |

### Frontend (ClojureScript)

| Component  | Technology     | Purpose                  |
|------------|----------------|--------------------------|
| Language   | ClojureScript  | Clojure on JavaScript    |
| Build      | Shadow-cljs    | Modern CLJS compilation  |
| State      | Re-frame       | Unidirectional data flow |
| UI         | Reagent        | React wrapper            |
| Components | Material-UI v7 | Design system            |
| Maps       | OpenLayers     | GIS visualization        |
| Charts     | Recharts       | Data visualization       |

### Infrastructure

| Component      | Technology        | Purpose                           |
|----------------|-------------------|-----------------------------------|
| Containers     | Docker Compose    | Service orchestration             |
| Reverse Proxy  | Nginx             | SSL termination, routing          |
| Map Tiles      | MapProxy          | Tile caching and serving          |
| Routing        | OSRM              | Distance/travel time calculations |
| Map Publishing | GeoServer         | WFS/WMS services                  |
| Logging        | Logstash + Kibana | Centralized logging               |

---

## Data Architecture

### The Sports Site Model

A sports site is the core entity, representing any sports or recreational facility:

```
sports_site (Database Table)
├── lipas_id        → Permanent facility identifier
├── id              → Revision UUID (primary key)
├── event_date      → When this revision became valid
├── status          → 'published' or 'draft'
├── document        → Full facility data (JSONB)
├── author_id       → User who created revision
├── type_code       → Facility type (indexed)
└── city_code       → Municipality (indexed)
```

### Document Structure

```clojure
{:lipas-id      123456
 :event-date    "2024-01-15T10:30:00Z"
 :status        "active"
 :name          "Olympiastadion"
 :marketing-name "Helsinki Olympic Stadium"
 :type          {:type-code 1520}
 :owner         "city"
 :admin         "city-sports"
 :location      {:city {:city-code 91}
                 :address "Paavo Nurmen tie 1"
                 :geometries {...}}  ; GeoJSON FeatureCollection
 :properties    {...}                ; Type-specific attributes
 :contact       {:phone "..." :email "..." :www "..."}}
```

### Type Hierarchy

Sports facilities are classified in a three-level hierarchy:

```
Main Category (7 categories)
├── 1000: Outdoor fields and sports parks
│   ├── Sub-Category: 1500 Ball sports fields
│   │   ├── Type 1520: Football stadium
│   │   ├── Type 1530: Football field
│   │   └── Type 1540: Tennis court
│   └── ...
├── 2000: Indoor sports facilities
├── 3000: Water sports facilities
├── 4000: Cross-country/terrain sports
├── 5000: Boating, aviation, motor sports
├── 6000: Animal sports areas
└── 7000: Service buildings
```

Each type defines:
- Allowed geometry types (Point/LineString/Polygon)
- Type-specific properties
- PTV ontology mappings
- Multilingual names and descriptions

### Elasticsearch Index Structure

The search index contains enriched documents with computed fields:

```clojure
{:lipas-id 123456
 :name "Olympiastadion"
 ;; ... base fields ...

 :search-meta
 {:name "olympiastadion"  ; Normalized for sorting
  :location
  {:city {:name {:fi "Helsinki" :se "Helsingfors"}}
   :province {:name {:fi "Uusimaa"}}
   :wgs84-point [24.9384 60.1879]
   :geometries [...]}  ; ES geo_shape format
  :type
  {:name {:fi "Jalkapallostadion"}
   :main-category {:name {:fi "Ulkokentät"}}
   :sub-category {:name {:fi "Pallokentät"}}}
  :owner {:name {:fi "Kunta"}}
  :admin {:name {:fi "Liikuntatoimi"}}}}
```

---

## Backend Architecture

### Request Flow

```
HTTP Request
     │
     ▼
┌─────────────┐
│   Nginx     │  SSL termination, static files
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Jetty     │  HTTP server
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Ring      │  Middleware stack
│ Middleware  │  - CORS
│             │  - Authentication (JWT/Basic)
│             │  - Content negotiation
│             │  - Exception handling
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Reitit    │  Route matching + coercion
│   Router    │  - Path parameters
│             │  - Query parameters
│             │  - Body validation (Malli)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Handler   │  Business logic
│  Functions  │  - Permission checks
│             │  - Data operations
│             │  - Side effects
└─────────────┘
```

### API Organization

```
/api/
├── /health                    Health check
├── /swagger.json              OpenAPI spec
│
├── /sports-sites              Sports facility
│   ├── POST   /               Upsert facility
│   ├── GET    /:id            Get facility
│   └── GET    /:id/history    Revision history
│
├── /actions                   CQRS endpoints
│   ├── POST   /login          Authenticate
│   ├── POST   /register       Create account
│   └── POST   /magic-link     Passwordless login
│
├── /admin                     Admin operations
│   ├── /users                 User management
│   └── /permissions           Permission grants
│
├── /analysis                  Spatial analysis
│   ├── /reachability          Travel time analysis
│   ├── /diversity             Coverage analysis
│   └── /heatmap               Density visualization
│
└── /integrations              External system sync
    └── /ptv                   PTV service registry
```

### Authentication & Authorization

**Authentication Methods:**
- JWT tokens (primary) - stateless, expiring tokens
- Basic auth - for initial login only
- Magic links - passwordless email authentication

**Authorization Model:**
```clojure
;; Permission structure
{:roles [{:role "admin"}
         {:role "site-manager" :lipas-ids [123 456]}
         {:role "org-admin" :org-id "uuid"}
         {:role "type-manager" :type-codes [1520 1530]}]}
```

**Permission Checks:**
- Route-level: Middleware validates required privileges
- Resource-level: Business logic checks ownership/access
- Automatic grants: Creators get management rights

### Background Job System

The job system handles async operations using PostgreSQL-backed queues:

```
┌────────────────────────────────────────────────────────────────┐
│                    Job Processing Architecture                  │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│   ┌─────────────┐                    ┌─────────────────────┐   │
│   │  API Server │                    │   Worker Process    │   │
│   │             │    enqueue!        │                     │   │
│   │  (creates   │ ──────────────►    │   ┌─────────────┐   │   │
│   │   jobs)     │                    │   │  Fast Pool  │   │   │
│   └─────────────┘                    │   │  (2 threads)│   │   │
│                                      │   │  - Email    │   │   │
│   ┌─────────────┐                    │   │  - Webhooks │   │   │
│   │  PostgreSQL │                    │   │  - Reminders│   │   │
│   │             │                    │   └─────────────┘   │   │
│   │  jobs table │ ◄─────────────►    │                     │   │
│   │  (SKIP      │   poll & claim     │   ┌─────────────┐   │   │
│   │   LOCKED)   │                    │   │General Pool │   │   │
│   └─────────────┘                    │   │  (2 threads)│   │   │
│                                      │   │  - Analysis │   │   │
│                                      │   │  - Elevation│   │   │
│                                      │   └─────────────┘   │   │
│                                      └─────────────────────┘   │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**Job Types:**
- `email` - Notification delivery
- `webhook` - External system notifications
- `analysis` - Reachability/diversity calculations
- `elevation` - Route elevation enrichment
- `produce-reminders` - Generate facility update reminders

**Reliability Patterns:**

1. **Atomic Claiming:** `SELECT FOR UPDATE SKIP LOCKED` prevents double-processing
2. **Exponential Backoff:** Failed jobs retry with increasing delays
3. **Circuit Breaker:** Prevents cascading failures to external services
4. **Timeout Protection:** Per-job-type timeouts prevent hanging
5. **Dead Letter Queue:** Jobs exceeding max attempts move to failed state

---

## Frontend Architecture

### State Management (Re-frame)

```
┌─────────────────────────────────────────────────────────────┐
│                    Re-frame Architecture                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌───────────┐    ┌───────────┐    ┌───────────────────┐  │
│   │   View    │───►│  Event    │───►│    Event Handler  │  │
│   │ Component │    │ Dispatch  │    │   (Pure Function) │  │
│   └───────────┘    └───────────┘    └─────────┬─────────┘  │
│         ▲                                     │            │
│         │                                     ▼            │
│   ┌───────────┐                        ┌───────────┐       │
│   │Subscription│◄──────────────────────│  App DB   │       │
│   │ (Derived) │                        │  (Atom)   │       │
│   └───────────┘                        └───────────┘       │
│                                              │             │
│                                              ▼             │
│                                        ┌───────────┐       │
│                                        │  Effects  │       │
│                                        │ (HTTP, etc)│      │
│                                        └───────────┘       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**State Structure:**
```clojure
{:user          {...}        ; Authentication state
 :sports-sites  {...}        ; Facility data & editing
 :map           {...}        ; Map controls, layers, mode
 :search        {...}        ; Search filters & results
 :analysis      {...}        ; Reachability, diversity, heatmap
 :admin         {...}        ; Admin panel state
 :translator    fn           ; i18n function
 ...}
```

### Component Organization

```
src/cljs/lipas/ui/
├── core.cljs              Application entry point
├── db.cljs                Initial state definition
├── events.cljs            Global event handlers
├── subs.cljs              Global subscriptions
├── views.cljs             Main layout component
├── routes.cljs            Reitit frontend routing
│
├── components/            Reusable UI components
│   ├── buttons.cljs       Button variants
│   ├── text_fields.cljs   Input components
│   ├── selects.cljs       Dropdown/autocomplete
│   ├── tables.cljs        Data tables
│   └── dialogs.cljs       Modal dialogs
│
├── map/                   Map feature (largest module)
│   ├── map.cljs           OpenLayers setup
│   ├── views.cljs         Map UI (84KB)
│   ├── events.cljs        Map state mutations
│   ├── subs.cljs          Map subscriptions
│   ├── styles.cljs        Feature styling
│   └── editing.cljs       Draw/edit interactions
│
└── [feature]/             Feature modules
    ├── db.cljs            Local state schema
    ├── events.cljs        Feature events
    ├── subs.cljs          Feature subscriptions
    ├── views.cljs         Feature UI
    └── routes.cljs        Feature routes
```

### Map Integration (OpenLayers)

The map is the central UI element, supporting:

**Basemaps:**
- Taustakartta (Background map)
- Maastokartta (Terrain map)
- Ortokuva (Orthophoto)
- Property boundaries

**Vector Layers:**
- Sports facilities (clustered)
- Locations of Interest
- Edit geometries
- Analysis overlays (heatmap, reachability)

**Editing Capabilities:**
- Point placement
- LineString drawing (routes)
- Polygon drawing (areas)
- Geometry modification
- Snapping to existing features

**Coordinate System:**
- Display: EPSG:3067 (Finnish national grid)
- Storage: WGS84 (EPSG:4326)
- Automatic transformation

### UIX/React Hooks Integration

For cases requiring React hooks (third-party libraries), UIX components can use Re-frame subscriptions:

```clojure
(defn use-subscribe [query]
  "Bridge Re-frame subscriptions to React hooks"
  (let [sub (rf/subscribe query)]
    (use-reaction sub)))

(defui my-component []
  (let [data (use-subscribe [::subs/my-data])]
    ($ mui/Box data)))
```

---

## Infrastructure

### Docker Compose Services

```yaml
services:
  # Application Layer
  backend:        # Clojure web server (port 8091)
  worker:         # Background job processor

  # Data Layer
  postgres:       # PostGIS database (port 5432)
  elasticsearch:  # Search engine (port 9200)

  # Web Layer
  proxy:          # Nginx reverse proxy (ports 80/443)
  mapproxy:       # Tile caching service

  # Routing Services
  osrm-car:       # Car routing
  osrm-bicycle:   # Bicycle routing
  osrm-foot:      # Pedestrian routing

  # Publishing
  geoserver:      # WFS/WMS services (port 8888)

  # Monitoring
  logstash:       # Log aggregation
  kibana:         # Log visualization
```

### Deployment Model

**Current:** SSH-based deployment to remote hosts

```bash
# Deployment flow
1. Build uberjar locally (bb uberjar)
2. Compile ClojureScript (shadow-cljs release)
3. SCP artifacts to target host
4. SSH: copy to deployment directory
5. SSH: docker compose restart
6. HTTP health check verification
```

**Environment Configuration:**
- Secrets in `.env.sh` files (not in git)
- Sourced at container startup
- Separate configs for dev/prod

**Target Environments:**
- `lipas-dev.cc.jyu.fi` - Development/staging
- `lipas.fi` - Production

---

## Integration Architecture

### PTV (Palvelutietovaranto)

Finnish public service registry integration:

```
LIPAS                           PTV API
┌─────────────────┐            ┌─────────────────┐
│                 │            │                 │
│ Sports Site     │───────────►│ Service Channel │
│ (facility)      │            │ (location)      │
│                 │            │                 │
│ Type Category   │───────────►│ Service         │
│ (sub-category)  │            │ (service type)  │
│                 │            │                 │
│ Organization    │───────────►│ Organization    │
│ (city/entity)   │            │ (responsible)   │
│                 │            │                 │
└─────────────────┘            └─────────────────┘
```

**Sync Triggers:**
- Sports site creation/update
- Type change (may require new service)
- Explicit admin action

**AI-Assisted Descriptions:**
OpenAI generates Finnish service descriptions from facility data, with human review.

### OSRM (Open Source Routing Machine)

Travel time and distance calculations:

```clojure
(osrm/get-distances-and-travel-times
 {:profiles     [:car :bicycle :foot]
  :sources      ["24.9,60.1" "25.0,60.2"]  ; Population grid centers
  :destinations ["24.95,60.15"]})          ; Facility location
```

Used for:
- Reachability analysis
- Diversity analysis

### GeoServer

WFS/WMS publishing for external consumers:

- **WFS (Web Feature Service):** Vector data access
- **WMS (Web Map Service):** Rendered map tiles
- Enables third-party GIS tools to access LIPAS data

### UTP (Luontoon.fi service)

Webhook-based integration for data synchronization. LIPAS acts as an CMS for Luontoon.fi.

---

## Analysis Capabilities

### Reachability Analysis

Calculates how many people can reach a facility within time/distance thresholds:

```
Input:
- Facility geometry (point/polygon)
- Analysis radius (km)
- Travel modes (car, bicycle, foot)

Process:
1. Query population grid cells within radius
2. Calculate travel times via OSRM
3. Aggregate population by time zones

Output:
- Population counts per zone (0-5min, 5-10min, etc.)
- Demographics breakdown (age groups)
- Coverage percentage
```

### Diversity Analysis

Evaluates facility type coverage across population centers:

```
Input:
- Geographic area (city/region)
- Facility types of interest

Process:
1. Query population grid
2. For each grid cell, find nearest facilities
3. Calculate diversity index

Output:
- Coverage maps
- Underserved area identification
- Facility distribution metrics
```

### Heatmap Generation

Density visualization of facility distribution:

```
Input:
- Facility type filter
- Geographic bounds

Process:
1. Aggregate facility locations
2. Generate density grid
3. Apply color gradient

Output:
- Tile-based heatmap overlay
- Density statistics
```

---

## Development Philosophy

### REPL-Driven Development

**Opinion:** The REPL is the primary development interface. Code should be immediately testable.

**Implementation:**
- `user` namespace with system utilities
- Hot-reload via `(user/reset)`
- Live ClojureScript REPL via Shadow-cljs
- Rich comment blocks for exploration

### Testing Strategy

**Opinion:** Integration tests provide more value than unit tests. Avoid mocking.

**Implementation:**
- Real database connections in tests
- Test fixtures create isolated environments
- Property-based testing where applicable
- Browser automation for end-to-end tests

### Code Organization

**Opinion:** Feature-based organization scales better than layer-based.

**Implementation:**
```
Good:
  sports_sites/
    ├── db.cljs
    ├── events.cljs
    ├── subs.cljs
    └── views.cljs

Avoid:
  db/
    └── sports_sites.cljs
  events/
    └── sports_sites.cljs
```

### Error Handling

**Opinion:** Errors should be explicit and data-driven.

**Implementation:**
- Exceptions for exceptional conditions only
- Return error maps for expected failures
- Structured logging with correlation IDs
- User-facing error codes for i18n

---

## Key Architectural Decisions Log

| Decision                    | Rationale                                          | Trade-offs                                        |
|-----------------------------|----------------------------------------------------|---------------------------------------------------|
| **Append-only data**        | Full audit trail, safe concurrent access           | Storage growth, complex queries for current state |
| **Elasticsearch for reads** | Fast search, geo queries, faceted filtering        | Eventual consistency, index maintenance overhead  |
| **Integrant for lifecycle** | Explicit dependencies, testable components         | Learning curve, boilerplate                       |
| **Re-frame for frontend**   | Predictable state, time-travel debugging           | Verbose for simple cases                          |
| **PostgreSQL job queue**    | Transactional consistency, no extra infrastructure | Limited throughput vs dedicated queue             |
| **SSH deployment**          | Simple, direct control                             | Manual process, no rollback automation            |
| **Shared cljc schemas**     | Single source of truth, frontend/backend alignment | Version synchronization                           |

---

## Summary

LIPAS demonstrates how a mature Clojure/ClojureScript application can handle complex GIS requirements while maintaining developer productivity. The key success factors are:

1. **Immutable data model** - Audit trail and safe operations
2. **Clear read/write separation** - Optimized for each path
3. **Component architecture** - Testable, maintainable
4. **Schema-first approach** - Validation everywhere
5. **REPL-driven development** - Fast feedback loops
6. **Feature-based organization** - Scalable codebase

The system successfully balances pragmatism (SSH deployment, PostgreSQL queues) with sophistication (event sourcing, spatial analysis, AI integration).
