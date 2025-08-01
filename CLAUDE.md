# Development Guide for Claude with Clojure-MCP

## 🚀 Quick Start - You Already Have REPL Access!

**IMPORTANT**: When working with this project through clojure-mcp, you have **direct access to a running Clojure REPL** with all development utilities pre-loaded in the `user` namespace.

### Immediate Development Setup

**No setup needed!** Simply run:

```clojure
(reset)
```

This single command will:
- Reload code in the REPL
- Start the Jetty server
- Connect to PostgreSQL and Elasticsearch
- Initialize all system components (AWS, email, PTV, etc.)

### Available User Namespace Functions

The `user` namespace provides development time utilities. Explore what's available in the `user` namespace.

- `(clj-mcp.repl-tools/list-vars 'user)`

### System Access After Startup

Once `(reset)` has run, access system components via:

```clojure
integrant.repl.state/system  ; Full system map with all components
```

Available components:
- `:lipas/server` - Jetty web server
- `:lipas/db` - PostgreSQL database connection
- `:lipas/search` - Elasticsearch client
- `:lipas/emailer` - Email configuration
- `:lipas/aws` - S3 integration
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

## 🧪 Running Tests

Important! Always reload changed code before running the tests:
- changed code namespaces
- changed test namespaces
- explicitly reload lipas.test-utils

After the system is loaded with `(reset)`:

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

;; Access data for exploration
(def db-conn (user/db))
(def search-client (user/search))

;; The user namespace also includes extensive rich comment blocks
;; with real development examples and maintenance operations
```

## 📁 Project Structure

```
├── CLAUDE.md             # This file
├── bb.edn                # Project level tasks
└── webapp/
    ├── deps.edn          # Clean production dependencies
    |-- bb.edn            # Webapp level tasks
    ├── src/clj/          # Backend Clojure code
    ├── src/cljs/         # Frontend ClojureScript code
    ├── src/cljc/         # Shared Clojure/ClojureScript code
    └── test/             # Tests
```

## 💡 Development Workflow for Claude

### Starting Work
1. **Just run** `(reset)` - everything will be ready
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
- **File Organization**: Backend in `src/clj`, tests in `test/clj`
- **Testing**: Use `clojure.test` with `deftest`, `testing`, and `is`

### Frontend (ClojureScript)
- **Framework**: Migrating from Reagent to UIX (React wrapper)
- **New Code**: Write using UIX syntax
- **Components**: `(defui component-name [props-map] body)`
- **Elements**: `($ :dom-element optional-props-map …children)`
- **Styling**: Use explicit MUI requires, avoid legacy `lipas.ui.mui`

### UIX Examples

```clojure
;; Simple component
(defui greeting [{:keys [name]}]
  ($ :div.greeting
     ($ :h1 (str "Hello " name "!"))))

;; With state
(defui counter []
  (let [[count set-count!] (use-state 0)]
    ($ :div
       ($ :p (str "Count: " count))
       ($ :button {:on-click #(set-count! inc)}
          "Increment"))))
```

### UIX Hooks Rules
1. **Always call hooks at component top level** - never in conditions/loops
2. **List all dependencies** in effect hooks: `[value1 value2]`
3. **Clean up effects** by returning cleanup functions

## 🎯 Key Points for Efficient Development

1. **You have immediate REPL access** - no setup required
2. **Single command startup** - just `(reset)`
3. **Rich exploration tools** - use `clj-mcp.repl-tools/*` functions
4. **Real-time feedback** - integrated linting and syntax checking
5. **Full system access** - all components available via `integrant.repl.state/system`

**Remember**: Don't overthink the setup - you already have everything you need to start developing immediately!
