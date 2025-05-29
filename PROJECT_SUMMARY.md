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
│   │   │   └── lipas/backend/ # Core backend logic
│   │   ├── cljc/              # Shared Clojure(Script) code
│   │   │   └── lipas/         # Data models, schemas, i18n
│   │   └── cljs/              # Frontend ClojureScript source
│   │       └── lipas/ui/      # UI components and logic
│   ├── resources/             # Static resources, SQL migrations
│   └── test/                  # Test suites
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

## Development Workflow

### Prerequisites
- Java 21+ (Temurin recommended)
- Clojure CLI 1.12.0+
- Node.js and NPM (for frontend dependencies)
- Docker and Docker Compose
- Babashka (for task automation)

### Quick Start
```bash
# Clone and setup environment
cp .env.sample.sh .env.sh
# Edit .env.sh with required secrets

# Option 1: Full Docker setup
./setup-dev.sh

# Option 2: Local development
bb up                                    # Start Docker services
cd webapp
clojure -M:nrepl                        # Start backend REPL (port 7888)
# In REPL: (reset)
npm install && npm run watch             # Start frontend build
```

### Available Babashka Tasks

#### Development
- `bb test` - Run fast tests
- `bb test-integration` - Run integration tests  
- `bb test-all` - Run all tests
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
- **Analysis queues** - Background job processing
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
- **Infrastructure**: Docker Compose orchestration

## Extension Points

### Adding New Sports Site Types
1. Update `lipas.data.types` with new type definitions
2. Add validation schemas in `lipas.schema.sports-sites`
3. Implement specialized UI components in `lipas.ui.sports-sites`
4. Add database migrations if needed

### New Analysis Features
1. Extend `lipas.backend.analysis.*` namespaces
2. Add Elasticsearch mappings and queries
3. Create UI components for visualization
4. Integrate with existing queue-based processing

### External Service Integration
1. Add configuration in `lipas.backend.config`
2. Implement service clients in `lipas.backend.*`
3. Add background job processing if needed
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
- End-to-end tests using Etaoin for UI testing
- Database tests using transaction rollback

### State Management (Frontend)
- All application state in re-frame app-db
- Events for state changes (`lipas.ui.events`)
- Subscriptions for derived state (`lipas.ui.subs`)
- Effects for side effects (`lipas.ui.effects`)

### Backend Architecture
- Integrant for system lifecycle management
- Ring/Reitit for HTTP handling
- Component-based architecture with dependency injection
- Queue-based background processing

This summary provides a comprehensive foundation for understanding and contributing to the LIPAS codebase. The project exemplifies modern Clojure(Script) web development with sophisticated geospatial capabilities and integration requirements.
