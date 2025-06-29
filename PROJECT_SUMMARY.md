# LIPAS - Project Summary

## Overview

LIPAS is a nationwide, public GIS (Geographic Information System) service for Finnish sports facilities developed by the University of Jyväskylä. It provides comprehensive mapping, analysis, and management tools for sports infrastructure across Finland, integrating multiple geospatial services and data sources.

**Live URLs:**
- Production: https://www.lipas.fi/
- Development: https://lipas-dev.cc.jyu.fi/
- CI: https://github.com/lipas-liikuntapaikat/lipas/actions

## Architecture Overview

LIPAS is a full-stack geospatial web application built around a microservices architecture with the following key components:

- **Frontend**: ClojureScript Single Page Application (SPA) using re-frame and Material-UI
- **Backend**: Clojure HTTP service with JWT authentication and extensive geospatial APIs
- **Database**: PostGIS (PostgreSQL with spatial extensions) for master data storage
- **Search**: Elasticsearch with Logstash and Kibana (ELK stack) for search functionality
- **Mapping**: Nginx reverse proxy, MapProxy for basemap caching, GeoServer for legacy support
- **Routing**: OSRM (Open Source Routing Machine) for multi-modal route calculation
- **Job Queue System**: Unified background job processing with smart concurrency control
- **Legacy Integration**: Maintains compatibility with existing LIPAS services

## Technology Stack

### Core Languages & Frameworks
- **Clojure 1.12.0** - Backend services
- **ClojureScript 1.11.132** - Frontend application
- **re-frame 1.4.3** - Frontend state management
- **Reagent 1.2.0** - React wrapper for ClojureScript
- **Reitit 0.7.1** - Routing (both backend and frontend)

### Frontend Dependencies
- **Material-UI 5.15.19** (@mui/material) - UI component library
- **OpenLayers 7.5.2** - Web mapping library
- **Turf.js** - Geospatial analysis functions
- **React 18.3.1** - UI framework
- **Recharts 2.2.0** - Data visualization

### Backend Dependencies
- **next.jdbc 1.3.939** - Database access
- **HugSQL 0.4.8** - SQL query management
- **HoneySQL 2.6.1270** - SQL generation
- **Buddy 2.0.0** - Authentication and security
- **Cheshire 5.13.0** - JSON handling
- **Integrant 0.10.0** - System management
- **Migratus 1.0.6** - Database migrations
- **Malli 0.17.0** - Data validation and schemas

### Infrastructure
- **PostgreSQL/PostGIS** - Spatial database
- **Elasticsearch + Logstash + Kibana** - Search and analytics
- **Nginx** - Reverse proxy and SSL termination
- **MapProxy** - Basemap proxy and caching
- **GeoServer** - Legacy spatial data publishing
- **OSRM** - Multi-modal routing (car, bicycle, foot)
- **Docker Compose** - Container orchestration

## Project Structure

```
/Users/tipo/lipas/lipas/
├── deps.edn                    # Root project configuration
├── webapp/                     # Main application directory
│   ├── deps.edn               # Webapp dependencies and aliases
│   ├── bb.edn                 # Babashka task definitions
│   ├── shadow-cljs.edn        # ClojureScript build configuration
│   ├── package.json           # NPM dependencies
│   ├── src/
│   │   ├── clj/               # Backend Clojure source
│   │   │   ├── lipas/backend/ # Core backend logic
│   │   │   └── lipas/jobs/    # Unified job queue system
│   │   ├── cljc/              # Shared Clojure(Script) code
│   │   │   └── lipas/         # Data models, schemas, i18n
│   │   └── cljs/              # Frontend ClojureScript source
│   │       └── lipas/ui/      # UI components and logic
│   ├── resources/             # Static resources, SQL migrations
│   │   ├── migrations/        # Database schema migrations
│   │   └── sql/               # HugSQL query definitions
│   └── test/                  # Test suites
│       └── clj/lipas/jobs/    # Job system integration tests
├── docker-compose.yml         # Container definitions
├── scripts/                   # Deployment and utility scripts
├── nginx/                     # Nginx configuration
├── mapproxy/                  # MapProxy configuration
├── geoserver/                 # GeoServer data and configuration
├── osrm/                      # OSRM routing data (car, bicycle, foot)
└── docs/                      # Project documentation
```

## Key Namespaces and Components

### Backend Core (`lipas.backend.*`)
- **`lipas.backend.core`** - Main business logic and API functions
- **`lipas.backend.system`** - Integrant system configuration
- **`lipas.backend.handler`** - HTTP request routing and middleware
- **`lipas.backend.db.db`** - Database access layer
- **`lipas.backend.search`** - Elasticsearch integration
- **`lipas.backend.auth`** - Authentication and authorization
- **`lipas.backend.gis`** - Geospatial operations and utilities

### Unified Job Queue System (`lipas.jobs.*`) 🆕
- **`lipas.jobs.core`** - Public API for job management and lifecycle
- **`lipas.jobs.db`** - Database access layer with HugSQL queries
- **`lipas.jobs.dispatcher`** - Multimethod job handlers by type
- **`lipas.jobs.worker`** - Mixed-duration worker with fast/general lanes
- **`lipas.jobs.scheduler`** - Periodic job producer scheduling
- **`lipas.jobs.system`** - Integrant system configuration for workers

### Frontend Core (`lipas.ui.*`)
- **`lipas.ui.core`** - Application initialization and routing
- **`lipas.ui.events`** - re-frame event handlers
- **`lipas.ui.subs`** - re-frame subscriptions (derived state)
- **`lipas.ui.views`** - Main view components
- **`lipas.ui.components`** - Reusable UI components
- **`lipas.ui.map.*`** - Map-related components and logic

### Shared Code (`lipas.*`)
- **`lipas.data.*`** - Static data definitions (types, cities, etc.)
- **`lipas.schema.*`** - Data validation schemas using Malli
- **`lipas.i18n.*`** - Internationalization (Finnish, English, Swedish)
- **`lipas.utils`** - Shared utility functions

## Unified Job Queue System 🆕

### Architecture
The system replaces 5 separate queue tables with a unified approach:

```
Scheduler → Jobs Table → Worker (Fast/General Lanes) → Job Handlers
   ↓           ↓              ↓                          ↓
Periodic    Unified       Smart                    Email, Analysis,
Tasks       Queue         Concurrency              Elevation, etc.
```

### Key Features
- **Smart Concurrency**: Fast lane (emails, reminders) + general lane (analysis, elevation)
- **Prevents Head-of-Line Blocking**: Fast jobs never wait for slow jobs
- **Automatic Scheduling**: Replaces tea-time with job producers
- **Error Handling**: Retry logic, failure tracking, dead letter queue
- **Legacy Compatibility**: All existing queue functions continue to work

### Job Types
- **Fast Jobs** (< 30 seconds): email, produce-reminders, cleanup-jobs, integration, webhook
- **Slow Jobs** (minutes): analysis, elevation

### Usage Examples
```clojure
;; Enqueue a job
(jobs/enqueue-job! db "email"
                   {:to "user@example.com" :subject "Welcome"}
                   {:priority 95})

;; Start worker system
(worker/start-mixed-duration-worker!
  {:db db :search search :emailer emailer}
  {:fast-threads 3 :general-threads 5})

;; Legacy compatibility (still works)
(jobs/add-to-analysis-queue! db sports-site)
```

### Database Schema
- **`jobs` table**: Unified queue with status, priority, retry logic
- **HugSQL queries**: `resources/sql/jobs.sql`
- **Migration**: `20250108000000-unified-jobs-table.up.sql`

## Development Workflow

### Prerequisites
- Java 21+ (Temurin recommended)
- Clojure CLI 1.12.0+
- Node.js and NPM (for frontend dependencies)
- Docker and Docker Compose
- Babashka (for task automation)

### Available Babashka Tasks

#### Development
- `bb test` - Run all tests
- `bb test-ns <namespace>` - Run tests for a single namespace
- `bb test-var <var>` - Run a single test var
- `bb db-migrate` - Run database migrations
- `bb db-status` - Check migration status
- `bb uberjar` - Build production JAR

#### Docker Operations
- `bb docker-build` - Build in Docker
- `bb docker-migrate` - Run migrations in Docker
- `bb docker-test` - Run tests in Docker

#### Frontend
- `bb cljs-watch` - Watch and rebuild ClojureScript
- `bb dev-deploy-cljs` - Quick deploy to dev server

### REPL-Driven Development
The project supports REPL-driven development with Integrant for system management:

```clojure
;; In REPL after connecting to port 7888
(reset)         ; Restart system with code changes
(go)            ; Start system
(halt)          ; Stop system

;; Job system testing
(require '[lipas.jobs.core :as jobs])
(jobs/enqueue-job! db "cleanup-jobs" {:days-old 7})
```

## Key APIs and Integration Points

### Sports Sites Management
- CRUD operations for sports facilities
- Geospatial validation and enrichment
- Integration with legacy LIPAS system
- PTV (Palvelutietovaranto) synchronization

### Search and Analysis
- Elasticsearch-powered search with complex geospatial queries
- Diversity and reachability analysis for urban planning
- Statistical reporting and data visualization
- Population and accessibility analysis

### Background Job Processing 🆕
- Unified job queue with smart concurrency control
- Automatic retry logic and error handling
- Periodic task scheduling (reminders, cleanup)
- Legacy queue compatibility layer

### Geospatial Services
- Multi-modal routing (OSRM integration)
- Elevation data integration
- Coordinate system transformations
- GIS format import/export (GeoJSON, Shapefile, etc.)

### External Integrations
- **PTV (Service Information Repository)** - Finnish national service registry
- **UTP CMS** - Content management integration
- **Email services** - User notifications and newsletters
- **AWS S3** - File storage and management

## Database Schema

The application uses PostgreSQL with PostGIS extensions. Key migrations are located in `webapp/resources/migrations/`. The schema includes:

- **Users and authentication** - User accounts, permissions, roles
- **Sports sites** - Main entity with extensive geospatial and metadata fields
- **Location data** - Cities, administrative regions, population data
- **Jobs** - Unified background job processing queue 🆕
- **Integration tables** - Legacy system synchronization

## Configuration and Deployment

### Environment Configuration
- Development: `.env.sh` (from `.env.sample.sh`)
- Production: Environment variables via Docker Compose
- Feature flags and regional settings in `lipas.backend.config`

### Build Process
```bash
# Backend
bb uberjar                               # Creates target/backend.jar

# Frontend
npm run build                            # Creates optimized ClojureScript bundle
# OR
clojure -M -m shadow.cljs.devtools.cli release app
```

### Deployment
- **Backend**: Uberjar deployment via Docker containers
- **Frontend**: Static asset serving through Nginx
- **Database**: Automated migrations via Migratus
- **Worker System**: Separate Docker container for background processing 🆕
- **Infrastructure**: Docker Compose orchestration

## Extension Points

### Adding New Sports Site Types
1. Update `lipas.data.types` with new type definitions
2. Add validation schemas in `lipas.schema.sports-sites`
3. Implement specialized UI components in `lipas.ui.sports-sites`
4. Add database migrations if needed

### New Job Types 🆕
1. Add job type to `lipas.jobs.core/job-type-schema`
2. Implement handler in `lipas.jobs.dispatcher`
3. Classify as fast or slow in `job-duration-types`
4. Test with integration tests

### New Analysis Features
1. Extend `lipas.backend.analysis.*` namespaces
2. Add Elasticsearch mappings and queries
3. Create UI components for visualization
4. Add background job handlers for async processing 🆕

### External Service Integration
1. Add configuration in `lipas.backend.config`
2. Implement service clients in `lipas.backend.*`
3. Add background job processing for async operations 🆕
4. Update authentication/authorization as required

## Development Conventions

### Code Style
- Follow standard Clojure style guidelines
- Use `bb cljfmt` for consistent formatting
- Use `bb lint` (clj-kondo) for static analysis
- Prefix private functions with `-`

### Testing Strategy
- Unit tests for pure functions
- Integration tests marked with `^:integration` metadata
- End-to-end job system tests using Malli validation 🆕
- Database tests using transaction rollback
- Focus on test value over assertion details 🆕

### State Management (Frontend)
- All application state in re-frame app-db
- Events for state changes (`lipas.ui.events`)
- Subscriptions for derived state (`lipas.ui.subs`)
- Effects for side effects (`lipas.ui.effects`)

### Backend Architecture
- Integrant for system lifecycle management
- Ring/Reitit for HTTP handling
- Component-based architecture with dependency injection
- Unified queue-based background processing 🆕
- Malli schemas for data validation 🆕

### Job System Patterns 🆕
- Use `jobs/enqueue-job!` for all background work
- Implement job handlers as multimethods in dispatcher
- Use Malli schemas for job payload validation
- Follow fast/slow job classification for optimal performance
- Maintain legacy compatibility wrappers

### Test Database Isolation ⚠️ **CRITICAL**
**All tests MUST use the test database with `_test` suffix to ensure proper isolation.**

#### Configuration Requirements
- ✅ **Correct**: Use `(config/->system-config test-utils/config)` for Integrant system initialization
- ❌ **Wrong**: Never use `config/system-config` directly in tests (connects to production database)

#### Test Database Setup
The `test-utils/config` automatically applies the `_test` suffix to:
- Database name: `lipas` → `lipas_test`
- Search indices: `sports_sites_current` → `sports_sites_current_test`
- All other external services get test-specific configurations

#### Common Pitfalls to Avoid
1. **New test files forgetting test config**: Always use `test-utils/config` in test setup
2. **Direct database connections**: Use the test system's database connection, not direct configs
3. **Shared resources**: Ensure search indices, email services, etc. use test configurations
4. **CI/Local differences**: Test configurations work identically in both environments

#### Example Correct Test Setup
```clojure
(defn setup-test-system! []
  (test-utils/ensure-test-database!)
  (reset! test-system
          (ig/init (select-keys (config/->system-config test-utils/config) [:lipas/db]))))
```

#### Verification
Always verify your tests are using the correct database:
```clojure
;; In test, check database name includes _test suffix
(println "Using database:" (-> (test-db) :dbname))
;; Should print: "lipas_test"
```

This isolation prevents tests from interfering with production data and ensures consistent CI/local behavior.

## Recent Major Changes 🆕

### Unified Job Queue System (2025-06)
- **Replaced**: 5 separate queue tables with unified `jobs` table
- **Added**: Smart concurrency control (fast lane + general lane)
- **Architecture**: Scheduler → Queue → Worker → Handlers pipeline
- **Benefits**: Prevents head-of-line blocking, better monitoring, easier maintenance
- **Migration**: Legacy queue functions continue to work during transition

This summary provides a comprehensive foundation for understanding and contributing to the LIPAS codebase. The project exemplifies modern Clojure(Script) web development with sophisticated geospatial capabilities, robust background processing, and integration requirements.
