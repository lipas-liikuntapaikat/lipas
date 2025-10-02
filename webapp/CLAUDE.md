# Development Guide for Claude with Clojure-MCP

## 🚀 Quick Start - You Already Have REPL Access!

**IMPORTANT**: When working with this project through clojure-mcp, you have **direct access to a running Clojure REPL** with all development utilities pre-loaded in the `user` namespace.

### Immediate Development Setup

**No setup needed!** Simply run:

```clojure
(user/reset)
```

This single command will:
- Reload code in the REPL
- Start the Jetty server
- Connect to PostgreSQL and Elasticsearch
- Initialize all system components (db, search, email, PTV, etc.)

### Available User Namespace Functions

The `user` namespace provides development time utilities. Explore what's available in the `user` namespace.

- `(clj-mcp.repl-tools/list-vars 'user)`

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

## 🔍 Project Exploration Tools

Use the built-in REPL exploration tools:

```clojure
;; List all available namespaces
(clj-mcp.repl-tools/list-ns)

;; Explore specific namespaces
(clj-mcp.repl-tools/list-vars 'lipas.backend.core)

;; Show documentation for functions
(clj-mcp.repl-tools/doc-symbol 'map)

;; Find symbols by pattern
(clj-mcp.repl-tools/find-symbols "search")
```

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

## 💡 Development Workflow for Claude

### Starting Work
1. **Just run** `(user/reset)` - everything will be ready
2. **Explore** the codebase using `clj-mcp.repl-tools/*` functions
3. **Make changes** using the clojure editing tools
4. **Test changes** immediately in the REPL

### No Need For:
- ❌ Complex setup scripts
- ❌ Build commands
- ❌ Manual dependency management
- ❌ Figuring out how to start the system

### Code Quality Tools Available

The clojure-mcp environment provides:
- **Integrated Linting**: Built-in clj-kondo analysis with real-time feedback
- **Syntax Checking**: Automatic validation during editing
- **Code Analysis**: Static analysis and quality checks
- **REPL Integration**: Enhanced REPL capabilities

## 📝 Code Style Guidelines

### Backend (Clojure)
- **Namespace Aliases**: Use `str` for `clojure.string`, standard aliases
- **Testing**: Use `clojure.test` with `deftest`, `testing`, and `is`

### Frontend (ClojureScript)
- **Framework**: Mix of Reagent and UIX (React wrapper)
- **New Code**: PREFER Reagent + Re-Frame, Use UIX only when React hooks are needed by 3rd party libraries
- **Styling**: Use explicit MUI requires, avoid legacy `lipas.ui.mui`

### UIX Hooks Rules
1. **Always call hooks at component top level** - never in conditions/loops
2. **List all dependencies** in effect hooks: `[value1 value2]`
3. **Clean up effects** by returning cleanup functions

## 🎯 Key Points for Efficient Development

1. **You have immediate REPL access** - no setup required
2. **Single command startup** - just `(user/reset)`
3. **Rich exploration tools** - use `clj-mcp.repl-tools/*` functions
4. **Real-time feedback** - integrated linting and syntax checking
5. **Full system access** - all components available via `integrant.repl.state/system`

**Remember**: Don't overthink the setup - you already have everything you need to start developing immediately!
