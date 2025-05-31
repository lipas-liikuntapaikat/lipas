# Development Guide for Claude with Clojure-MCP

## 🚀 Quick Start - You Already Have REPL Access!

**IMPORTANT**: When working with this project through clojure-mcp, you have **direct access to a running Clojure REPL** with all development utilities pre-loaded in the `user` namespace.

### Immediate Development Setup

**No setup needed!** Simply run:

```clojure
(dev-webapp!)
```

This single command will:
- Load all webapp dependencies
- Start the Jetty server  
- Connect to PostgreSQL and Elasticsearch
- Initialize all system components (AWS, email, PTV, etc.)

### Available User Namespace Functions

The `user` namespace provides these utilities immediately:

- `(dev-webapp!)` - **One-command startup**: loads dependencies and starts the complete system
- `(load-webapp-dev-deps!)` - Loads only :dev and :test dependencies from webapp/deps.edn  
- `(read-webapp-deps)` - Inspects webapp dependency structure
- `(extract-dev-deps webapp-deps)` - Extracts development dependencies

### System Access After Startup

Once `(dev-webapp!)` has run, access system components via:

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

After the system is loaded with `(dev-webapp!)`:

```clojure
(require '[cognitect.test-runner.api :as tr])
(tr/test {:dirs ["webapp/test/clj"]})
```

## 🛠️ Webapp REPL Utilities

Once `(dev-webapp!)` has loaded the system, you have access to rich webapp-specific utilities via the `repl` namespace:

### System Access Functions
```clojure
;; Access system components directly
(repl/db)           ; Database connection
(repl/search)       ; Elasticsearch client  
(repl/ptv)          ; PTV integration
(repl/current-system) ; Full system map
(repl/current-config) ; System configuration
```

### Database & Search Operations
```clojure
;; Rebuild search indices
(repl/reindex-search!)     ; Rebuild main search index
(repl/reindex-analytics!)  ; Rebuild analytics index

;; Database migrations
(repl/run-db-migrations!)  ; Run pending database migrations
```

### User Management
```clojure
;; Password management
(repl/reset-admin-password! "new-password")           ; Reset admin password
(repl/reset-password! "user@example.com" "password") ; Reset any user's password
(repl/get-robot-user)                                ; Get the robot admin user
```

### System Lifecycle
```clojure
;; System control (integrant.repl functions)
(repl/reset)  ; Reset system with code reload
(go)          ; Start system
(halt)        ; Stop system  
(reset-all)   ; Full reset clearing all state
```

### Development Examples
```clojure
;; Common development tasks
(repl/reindex-search!)  ; After changing search mappings
(repl/reset-admin-password! "dev123")  ; For local development
(repl/run-db-migrations!)  ; After adding new migrations

;; Access data for exploration
(def db-conn (repl/db))
(def search-client (repl/search))

;; The repl namespace also includes extensive rich comment blocks
;; with real development examples and maintenance operations
```

### Rich Comment Block Examples

The `webapp/dev/repl.clj` file includes extensive comment blocks with real-world examples of:
- Database migrations and schema updates
- Search index management  
- Data type management and merging
- User management operations
- System maintenance tasks

These comment blocks serve as documentation and executable examples for common development and maintenance operations.

## 📁 Project Structure

```
├── deps.edn              # Root dependencies + development tooling
├── dev/user.clj          # Development utilities (you have access to this!)
├── CLAUDE.md            # This file
├── DEV-README.md        # Detailed development documentation
└── webapp/
    ├── deps.edn          # Clean production dependencies
    ├── src/clj/          # Backend Clojure code
    ├── src/cljs/         # Frontend ClojureScript code  
    ├── src/cljc/         # Shared Clojure/ClojureScript code
    └── test/             # Tests
```

## 💡 Development Workflow for Claude

### Starting Work
1. **Just run** `(dev-webapp!)` - everything will be ready
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
2. **Single command startup** - just `(dev-webapp!)`
3. **Rich exploration tools** - use `clj-mcp.repl-tools/*` functions
4. **Real-time feedback** - integrated linting and syntax checking
5. **Full system access** - all components available via `integrant.repl.state/system`

**Remember**: Don't overthink the setup - you already have everything you need to start developing immediately!
