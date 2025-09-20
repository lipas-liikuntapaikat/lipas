# LIPAS Project Summary

## Overview

LIPAS (Liikuntapaikat.fi) is a nationwide, public Geographic Information System (GIS) service for Finnish sports facilities, developed and maintained by the University of Jyväskylä. It serves as Finland's comprehensive database for sports infrastructure, providing mapping, analysis, and management tools for sports facilities across the country.

The system provides both an internal API for the LIPAS web application and public APIs (V1 legacy, V2 recommended) for external integrations, enabling widespread access to Finnish sports facility data.

## Core Purpose

LIPAS manages two primary entity types:
- **Sports Sites**: Physical structures intended for specific sports and exercise (stadiums, ski tracks, swimming pools, etc.)
- **Locations of Interest (LOI)**: Complementary non-structural points enriching outdoor recreation data (campfire sites, info boards, historical buildings)

## Technology Stack

### Frontend
- **Framework**: ClojureScript SPA with re-frame for state management
- **UI Components**: Material-UI (MUI) - migrating from Reagent to UIX (React wrapper)
- **Mapping**: OpenLayers for interactive geospatial visualization
- **Build**: Shadow-cljs for compilation and optimization

### Backend
- **Core**: Clojure HTTP service with Ring/Reitit for routing
- **Database**: PostgreSQL with PostGIS extensions for spatial data
- **Search**: Elasticsearch for full-text search and analytics
- **Authentication**: JWT tokens with HTTP-basic authentication
- **System Management**: Integrant for component lifecycle management

### Infrastructure
- **Reverse Proxy**: Nginx for SSL termination and request routing
- **Map Services**: MapProxy for basemap caching, GeoServer for spatial data publishing
- **Routing**: OSRM for multi-modal travel time calculations
- **Storage**: AWS S3 for file storage
- **Deployment**: Docker Compose for orchestration

## Project Structure

```
/Users/tipo/lipas/lipas/webapp/
├── src/
│   ├── clj/             # Backend Clojure code
│   │   └── lipas/backend/
│   │       ├── api/     # API endpoints (v1, v2)
│   │       ├── db/      # Database access layer
│   │       ├── search/  # Elasticsearch integration
│   │       ├── ptv/     # PTV integration (Finnish service registry)
│   │       ├── analysis/# Data analysis features
│   │       └── jobs/    # Background job processing
│   ├── cljs/            # Frontend ClojureScript code
│   │   └── lipas/ui/
│   │       ├── map/     # Map components and visualization
│   │       ├── sports_sites/ # Sports site management UI
│   │       ├── search/  # Search interface
│   │       └── routes/  # Route planning features
│   └── cljc/            # Shared code between frontend and backend
├── resources/
│   ├── migrations/      # Database migrations (SQL and EDN)
│   ├── public/          # Static assets
│   └── email_templates/ # Email notification templates
├── test/
│   └── clj/            # Backend tests
├── dev/                # Development utilities
│   └── user.clj        # REPL development namespace
└── docs/               # Documentation

Key Configuration Files:
- deps.edn              # Clojure dependencies and aliases
- shadow-cljs.edn       # ClojureScript build configuration
- bb.edn                # Babashka tasks for automation
- ../.env.sh            # Environment variables (from .env.sample.sh)
```

## Key Dependencies and Their Roles

### Core Libraries
- **buddy** (2.0.0): Authentication and authorization
- **reitit** (0.7.1): HTTP routing for APIs
- **re-frame** (1.4.3): Frontend state management
- **reagent** (2.0.0-alpha2): React wrapper (legacy, migrating to UIX)
- **uix.core** (1.1.1): Modern React wrapper for new code
- **integrant** (0.10.0): System component management

### Database & Data
- **next.jdbc** (1.3.939): Modern JDBC wrapper
- **honeysql** (2.6.1270): SQL query building
- **hugsql** (0.5.3): SQL template system
- **migratus** (1.0.6): Database migrations
- **hikari-cp** (2.7.0): Connection pooling

### External Integrations
- **spandex** (0.8.2): Elasticsearch client
- **clj-http** (3.13.0): HTTP client
- **postal** (2.0.2): Email sending
- **awssdk/s3** (2.20.135): AWS S3 integration

### Utilities
- **malli** (0.17.0): Data validation and schemas
- **timbre** (6.7.1): Logging
- **tongue** (0.4.4): Internationalization
- **docjure** (1.12.0): Excel file handling
- **geo** (3.0.1): Geospatial calculations

## API Architecture

LIPAS has a clear separation between internal APIs used by the frontend and public APIs for external integrations:

### Internal API (`/api`)
Used exclusively by the LIPAS frontend application for authenticated operations and internal functionality.

#### Key Endpoints
- **Sports Sites Management**
  - `POST /api/sports-sites` - Create/update sports sites (requires auth)
  - `GET /api/sports-sites/:lipas-id` - Get sports site details
  - `GET /api/sports-sites/history/:lipas-id` - Get version history
  - `GET /api/sports-sites/type/:type-code` - Get sites by type

- **Organization & User Management**
  - `GET /api/current-user-orgs` - Get user's organizations
  - `GET /api/orgs` - List all organizations (admin only)
  - `POST /api/orgs` - Create organization (admin only)
  - `GET /api/org/:org-id/users` - List organization users
  - `GET /api/users` - List all users (requires users/manage privilege)

- **Analysis & Reports**
  - `POST /api/actions/calculate-stats` - Generate statistics
  - `POST /api/actions/create-analysis-report` - Create analysis reports
  - `GET /api/actions/query-subsidies` - Query subsidy data
  - `GET /api/actions/get-accessibility-statements` - Accessibility info

- **Locations of Interest**
  - `GET /api/lois` - Search LOIs
  - `GET /api/lois/:loi-id` - Get specific LOI
  - `GET /api/lois/type/:loi-type` - Get LOIs by type

#### Authentication
- **Initial Login**: HTTP Basic Authentication to obtain JWT token
- **Subsequent Requests**: JWT token in Authorization header
- **Middleware**: `mw/token-auth` and `mw/auth` for protected endpoints
- **Privilege System**: Granular role-based access control

### Public API V2 (Recommended)
Designed for external integrations with comprehensive OpenAPI documentation.

- **Base URL**: `/v2/`
- **Documentation**: `/v2/swagger-ui/index.html`
- **OpenAPI Spec**: `/v2/openapi.json`
- **Authentication**: Generally not required for read operations
- **CORS**: Enabled for cross-origin requests

#### Main Endpoints
- `/v2/sports-sites` - Paginated list with filtering options
- `/v2/sports-sites/{lipas-id}` - Individual sports site details
- `/v2/sports-site-categories` - Complete type hierarchy
- `/v2/sports-site-categories/{type-code}` - Specific category details
- `/v2/lois` - Paginated LOI list with filtering
- `/v2/lois/{loi-id}` - Individual LOI details

### Public API V1 (Legacy)
- **Base URL**: `/v1/`
- **Documentation**: `/v1/swagger.json`
- **Status**: Maintained for backward compatibility only
- **Note**: No new features added, use V2 for new integrations

### API Routing Architecture
Nginx acts as a reverse proxy, routing requests:
- `/api/*` → Internal backend handlers
- `/v2/*` → Public API V2 handlers
- `/v1/*` → Legacy API handlers (separate service)

## Unified Job Queue System

LIPAS uses a sophisticated background job processing system with smart concurrency control:

### Job Categories
**Fast Jobs** (< 30 seconds):
- `email` - Email notifications
- `produce-reminders` - Generate facility update reminders
- `cleanup-jobs` - System maintenance
- `integration` - External system sync
- `webhook` - Webhook dispatching

**Slow Jobs** (minutes to hours):
- `analysis` - Diversity and accessibility analysis
- `elevation` - Elevation data enrichment
- `ptv-sync` - PTV service registry synchronization

### Reliability Features
- Exponential backoff for retries
- Circuit breakers for external services
- Dead letter queue for failed jobs
- Correlation IDs for tracing
- Deduplication keys to prevent duplicates

### Job Management
```clojure
;; Enqueue a job
(lipas.jobs.core/enqueue-job!
  {:type :email
   :payload {:to "user@example.com" :template :reminder}
   :priority 5
   :run-at #inst "2025-01-31T12:00:00"
   :correlation-id "req-123"})
```

## Development Workflow

### Local Development Setup

1. **Environment Configuration**
   ```bash
   cp .env.sample.sh .env.sh
   # Edit .env.sh with required secrets
   source .env.sh
   ```

2. **Start REPL-Driven Development**
   ```bash
   # Launches watcher for the frontend and nrepl server in port 7888.

   npx shadow-cljs watch app

   # NOTE: usually we have this running in the background and our
   # REPL clients connect to port 7888 for REPL-driven development.
   ```
   In REPL (user namespace):
   ```clojure
   (reset)  ; Start or reload the entire system
   (browser-repl) ; promote clojure REPL to a CLJS repl
   ```

3. **Access System Components**
   All helper functions are in the `user` namespace (defined in `dev/user.clj`):
   ```clojure
   (user/db)       ; Database connection
   (user/search)   ; Elasticsearch client
   (user/ptv)      ; PTV integration client
   integrant.repl.state/system  ; Full system map
   ```

4. **Additional REPL Utilities**
   ```clojure
   (user/reindex-search!)        ; Reindex Elasticsearch
   (user/reindex-analytics!)     ; Reindex analytics
   (user/reset-password! email password)  ; Reset user password
   (user/reset-admin-password! password)  ; Reset admin password
   (user/run-db-migrations!)    ; Run database migrations
   (user/browser-repl)          ; Connect to browser REPL
   (user/compile-cljs)          ; Compile ClojureScript
   ```

### Testing

**Babashka Tasks**:
```bash
bb test              # Run unit tests
bb test-integration  # Run integration tests
bb test-all         # Run all tests
bb test-ns namespace # Test specific namespace
bb docker-test      # Dockerized test suite
```

**REPL Testing**:
```clojure
(require '[clojure.test :as t])
(require '[some.test.namepsace] :reload)
(t/run-test-var #'some.test.namespace/some-var)
```

**Test Database**: All tests use a `_test` suffixed database and elastic search indices for isolation.

### Code Quality

```bash
bb lint     # Run clj-kondo static analysis
bb cljfmt   # Format code with clojure-lsp
```

### Deployment

```bash
bb deploy-dev         # Deploy to development
bb deploy-prod        # Deploy to production
bb deploy-backend env # Backend only
bb deploy-frontend env # Frontend only
```

## Coding Conventions

### General Principles
- REPL-driven development is central to the workflow
- Follow standard Clojure style guidelines
- Use `bb cljfmt` for consistent formatting
- Prefix private functions with `-`
- Tests must use test database with `_test` suffix

### Backend (Clojure)
- Namespace organization: `lipas.backend.*`
- Use standard aliases: `str` for `clojure.string`
- Transaction handling: wrap DB operations in `jdbc/with-db-transaction`
- Validation: Use Malli schemas for data validation

### Frontend (ClojureScript)
- **New Code**: Use UIX syntax instead of Reagent
- **Components**: `(defui component-name [props-map] body)`
- **Elements**: `($ :dom-element optional-props-map …children)`
- **State Management**: re-frame events, subscriptions, effects
- **Avoid**: Legacy `lipas.ui.mui` - use explicit MUI requires

### Testing Strategy
- Automated test-suite contains unit-tests and system tests
- System tests use test database and Elasticsearch indices with reset fixtures. Each system test is responsible for setting up the test-data it needs.
- Mocking should be minimal, since mocks don't provide value. Email-service is mocked.
- Integration tests with real external services are marked with `^:integration` metadata. Integration tests are NOT part of automatic test-suite.
- Use `lipas.test-utils` for fixtures and test data generation

## External Integrations

### PTV (Palvelutietovaranto)
Finnish national service registry integration with AI-assisted synchronization.

### Email Services
Templated email notifications for:
- Magic link authentication
- Permission updates
- Facility update reminders

### OSRM Routing
Multi-modal routing support:
- Car navigation
- Bicycle routes
- Walking paths

### MapProxy & GeoServer
- Basemap caching and optimization
- WFS/WMS services for spatial data

## Data Analysis Features

- **Diversity Analysis**: Calculate sports facility diversity by area
- **Reachability Analysis**: Accessibility calculations for urban planning
- **Population Analysis**: Facility coverage relative to population
- **Heatmap Generation**: Visualize facility density
- **Statistical Reporting**: Generate comprehensive facility reports

## Monitoring and Operations

### Health Checks
- `/api/actions/get-jobs-health-status` - Job queue health
- `/api/actions/create-jobs-metrics-report` - Performance metrics

### Admin Operations
- Dead letter job management
- Circuit breaker status monitoring
- Queue statistics and performance metrics

## Extension Points

### Adding New Job Types
1. Define job type in `lipas.jobs.dispatcher`
2. Implement handler function
3. Register in job type mapping
4. Add Malli schema for validation

### Adding New API Endpoints

#### Internal API Endpoints (`/api`)
1. Define route in `lipas.backend.handler`
2. Add authentication middleware if needed (`mw/token-auth`, `mw/auth`)
3. Define privilege requirements with `:require-privilege`
4. Implement business logic in `lipas.backend.core`
5. Add database queries in `lipas.backend.db.*`

#### Public API V2 Endpoints (`/v2`)
1. Define route in `lipas.backend.api.v2`
2. Add OpenAPI documentation annotations
3. Implement handler with proper pagination/filtering
4. Ensure CORS is properly configured
5. Update OpenAPI specification with schemas

### Frontend Components
1. Create UIX component in appropriate namespace
2. Add re-frame events/subscriptions
3. Register route if needed
4. Add Material-UI styling

## Development Tips

1. **Use DeepWiki**: The entire codebase is indexed at `lipas-liikuntapaikat/lipas` for quick reference
2. **REPL is King**: Always develop with a running REPL for instant feedback
3. **Test Isolation**: Ensure all tests use the test database
4. **Code Reloading**: Use `(reset)` for code changes, not full restart
5. **Job Testing**: Use correlation IDs for tracing job execution
6. **Frontend State**: Use re-frame-10x for debugging state changes

## Resources

- **Production**: https://www.liikuntapaikat.fi
- **API Documentation**: https://www.liikuntapaikat.fi/api
- **GitHub**: lipas-liikuntapaikat/lipas
- **DeepWiki Index**: Available for comprehensive codebase queries

---

*Last Updated: January 2025*
- *Clarified API architecture with distinction between internal API (`/api`) used by LIPAS frontend and public APIs (V1/V2) for external integrations*
- *Updated REPL workflow to reflect current development setup: simplified `user` namespace with `(reset)` for system initialization*
