# Development Guide for Claude

## 🚀 Quick Start - You Already Have REPL Access!

**IMPORTANT**: When working with this project through clojure-nrepl-eval command in port 7888, you have **direct access to a running Clojure REPL** with all development utilities pre-loaded in the `user` namespace.

## 🔧 Using clj-nrepl-eval (Port 7888)

The LIPAS REPL runs on **port 7888**. Use `clj-nrepl-eval` to evaluate Clojure code.

### Basic Usage

```bash
# Simple expression
clj-nrepl-eval -p 7888 "(+ 1 2 3)"

# Multiple expressions on one line
clj-nrepl-eval -p 7888 "(def x 10) (+ x 20)"
```

### Multiline Code with Heredoc

Use heredoc for complex expressions - avoids shell escaping issues:

```bash
clj-nrepl-eval -p 7888 <<'EOF'
(require '[lipas.backend.core :as core] :reload)
(core/get-sports-site db 123456)
EOF
```

### Common LIPAS Patterns

```bash
# Reload system after code changes
clj-nrepl-eval -p 7888 "(user/reset)"

# Verify a namespace compiles (returns nil if OK)
clj-nrepl-eval -p 7888 "(require 'lipas.backend.ptv.core :reload)"

# Run tests after changes
clj-nrepl-eval -p 7888 <<'EOF'
(require '[lipas.backend.ptv.core-test :as test] :reload)
(clojure.test/run-tests 'lipas.backend.ptv.core-test)
EOF

# Access system components
clj-nrepl-eval -p 7888 "(user/db)"
clj-nrepl-eval -p 7888 "(user/search)"
```

### Key Options

- `-p 7888` - LIPAS nREPL port (required)
- `-t 300000` - Custom timeout for long operations (default: 2 min)
- `--reset-session` - Clear session state if corrupted

### Important Notes

- **Always use `:reload`** when requiring namespaces to pick up changes
- **Session persists** between evaluations - defined vars remain available
- **Heredoc (`<<'EOF'`)** simplifies multiline code and string escaping

## Large workflow communication

When you're working with large tasks, notify me by using `say` command via bash tool and say "I'm done". If you get stuck, call for help by saying "Please help".

## Immediate Development Setup

**No setup needed!** Simply run:

```clojure
(user/reset)
```

This single command will:
- Reload code in the REPL
- Start the Jetty server
- Connect to PostgreSQL and Elasticsearch
- Initialize all system components (db, search, email, PTV, etc.)

### System Access After Startup

Once `(user/reset)` has run, access system components via:

```clojure
integrant.repl.state/system  ; Full system map with all components
```

Available components:
- `:lipas/server` - Jetty web server
- `:lipas/db` - PostgreSQL database connection
- `:lipas/search` - Elasticsearch client
- `:lipas/emailer` - Email configuration
- `:lipas/ptv` - Finnish public service integration
- `:lipas/mailchimp` - Newsletter integration
- ...etc

## ClojureScript Development

1. From the Clojure REPL, run
   ```clj
   (user/browser-repl)
   ```
   to switch into a live ClojureScript REPL.

2. In the ClojureScript REPL, evaluate
   ```clj
   :cljs/quit
   ```
   to return to the Clojure REPL.

3. In the Clojure REPL, run
   ```clj
   (user/compile-cljs)
   ```
   to compile ClojureScript and view any build warnings or errors.

## Testing Guidelines

Important! Be pedantic and scientific about the outcomes. If any tests are failing, it means that either the tests or the implementation is WRONG.

If you are unable to fix the tests immediately, take a step back and ask yourself coaching questions to identify the nature of the problem.

Think what's really valuable to test.

Integration tests are the most important ones - unit tests come second.

Generative tests are more valuable than example based tests.

Property based tests are more valuable than example value assertions.

Avoid mocking. Mocks don't provide value. Use mocks only as the last resort when there's no feasible way to setup a proper testing-system.

### Templates for testing

- Use lipas.backend.org-test namespace as an example how to setup handler tests with test-system, fixtures etc.
- Put common functionality to lipas.test-utils namespace

## 🧪 Running Tests

Important! Always RELOAD changed code before running the tests:

- `(user/reset)` reloads all changed namespaces and restarts the dev system.

After the system is loaded with `(user/reset)`:

```clojure
;; Example running tests in the REPL

(require '[clojure.test])

;; Require the namespace to test
(require 'lipas.jobs.handler-test :reload)

;; Run single test
(clojure.test/run-test lipas.jobs.handler-test/authorization-test)

;; Run all tests in a namespace
(clojure.test/run-tests 'lipas.jobs.handler-test)
```

### Final checking with bb

Once tests are working in the REPL, a final clean state check should be done with bb:

```bash
cd webapp

# Run single test
bb test-var lipas.jobs.handler-test/authorization-test

# Run all tests in a namespace
bb test-ns lipas.jobs.handler-test
```

Run clean tests for all changed namespaces.

### Development Examples
```clojure
;; Common development tasks
(user/reindex-search!)  ; After changing search mappings
(user/reset-admin-password! "dev123")  ; For local development
(user/run-db-migrations!)  ; After adding new migrations

;; Easy access to commonly used system components
(def db-conn (user/db))
(def search (user/search))

;; Get seeded user with admin permissions
(def user (user/get-robot-user))

;; The `user` namespace also includes extensive rich comment blocks
;; with real development examples and maintenance operations
```

## 🎭 Browser Testing with Playwright-MCP

**IMPORTANT: Always delegate browser testing to the `browser-tester` sub-agent** to preserve context and avoid filling the main conversation with Playwright snapshots.

### Browser Testing Workflow

**Before delegating to browser-tester, always:**

1. **Compile ClojureScript** - Run `(user/compile-cljs)` in the REPL
2. **Check for build warnings/errors** - Review the compilation output
3. **Fix any issues** - Address warnings or errors before testing
4. **Then delegate** - Once the build is clean, delegate to browser-tester

This ensures the browser-tester agent tests the latest code without wasting time on build issues.

### Delegating to browser-tester Agent

Use the Task tool with `subagent_type='browser-tester'` for all browser testing:

```
Task: Test the new search filter feature
- Login as admin
- Navigate to /liikuntapaikat
- Test the type filter dropdown
- Verify results update correctly
- Check for console errors
```

The browser-tester agent has full knowledge of:
- LIPAS URLs, login flow, and credentials
- How to handle the dev tools overlay (#rdt) that blocks clicks
- Expected vs. unexpected console errors
- Finnish UI terminology

### When to Delegate

Delegate to browser-tester when:
- Verifying a feature works end-to-end after implementation
- Testing UI interactions and user flows
- Checking for console errors after changes
- Validating that frontend changes render correctly

## 📁 Project Structure

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
│   └── cljc/            # Shared code and data model definitions between frontend and backend
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

## Sports Sites Data Model

### Database: Append-Only Event Log

The `sports_site` table implements an **append-only, immutable event log** pattern where every edit creates a new revision. Each revision shares the same `lipas_id` (permanent facility identifier) but gets an `event_date` and a unique `id`.

#### Table Structure

- `lipas_id`: Permanent sports site identifier
- `id`: UUID for this revision (primary key)
- `event_date`: When this revision became valid
- `status`: Publication status of this revision ("published", "draft")
- `document`: Full sports site data as JSONB
- `author_id`: User who created this revision
- `type_code`: Facility type (for indexing)
- `city_code`: City code (for indexing)
- `created_at`: Timestamp when row was inserted

**Key SQL queries:** `webapp/resources/sql/sports_site.sql`

#### Current State View

`sports_site_current` is a database view that shows only the latest revision for each `lipas_id`. This view is used throughout the codebase for fetching current data and powers most read operations.

### Elasticsearch: Enriched Search Index

The Elasticsearch index `sports_sites_current` contains **enriched, denormalized sports site documents** optimized for search and read operations. The indexing process transforms raw database documents through an `enrich` function that adds computed fields.

#### Enrichment Process

The enrichment adds:
- **Search metadata**: Computed fields for filtering and faceting
- **Resolved references**: Type names, city names, owner names (not just codes)
- **Geospatial data**: Properly formatted for Elasticsearch geo queries
- **Denormalized lookups**: Pre-joined data to avoid runtime lookups

**Core functions:** `webapp/src/clj/lipas/backend/core.clj`
- `index!` - Indexes sports sites to Elasticsearch
- `enrich` - Transforms raw data for search
- `search` - Executes search queries
- `save-sports-site!` - Orchestrates save to DB and index

#### Search Operations

The search index powers most API read operations. For example, searching for facilities with specific field types uses the enriched `search-meta.fields.field-types` data.

### Data Flow

```
User Edit → sports_site table (new revision)
         ↓
sports_site_current view (latest per lipas-id)
         ↓
Elasticsearch index (enriched + denormalized)
         ↓
API reads & search queries
```

The database is the **source of truth** with full history, while Elasticsearch serves as a **read-optimized cache** of current revisions with enriched data for fast queries.

### Implementation Notes

- Data consistency is maintained by indexing to Elasticsearch synchronously or asynchronously after database writes
- The `sync?` parameter controls whether indexing blocks the request or happens in the background
- Bulk operations use batch indexing for efficiency

### Schema Changes

- LIPAS has long history - **never make breaking changes to schemas**
- Don't rename or remove enum values (e.g., `lipas.data.owners`, `lipas.data.admins`)
- Additive changes (new values) are fine
- When in doubt, query the database to check if a value is in use

### 💡 Development Workflow for Claude

#### Starting Work

1. **Just run** `(user/reset)` - everything will be ready
2. **Make changes** using editing tools
3. **Test changes** immediately in the REPL

#### No Need For:

- ❌ Complex setup scripts
- ❌ Build commands
- ❌ Manual dependency management
- ❌ Figuring out how to start the system

#### Code Quality Tools Available

### 📝 Code Style Guidelines

#### Backend (Clojure)

- **Namespace Aliases**: Use `str` for `clojure.string`, standard aliases
- **Testing**: Use `clojure.test` with `deftest`, `testing`, and `is`

#### Frontend (ClojureScript)

- **Framework**: Mix of Reagent and UIX (React wrapper)
- **New Code**: PREFER Reagent + Re-Frame, Use UIX only when React hooks are needed by 3rd party libraries
- **Styling**: Use explicit MUI requires, avoid legacy `lipas.ui.mui`

#### UIX Hooks Rules

1. **Always call hooks at component top level** - never in conditions/loops
2. **List all dependencies** in effect hooks: `[value1 value2]`
3. **Clean up effects** by returning cleanup functions

### 🎯 Key Points for Efficient Development

1. **You have immediate REPL access** - no setup required
2. **Single command startup** - just `(user/reset)`
3. **Real-time feedback** - integrated linting and syntax checking
4. **Full system access** - all components available via `integrant.repl.state/system`

**Remember**: Don't overthink the setup - you already have everything you need to start developing immediately!
