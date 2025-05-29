# Lipas Development Environment

This project uses a **two-level development setup**:

## Architecture Overview

**Top Level** (`/deps.edn`): Infrastructure orchestration, deployment, and tooling integration
- Minimal dependencies (just webapp as local dependency)
- **All webapp production dependencies automatically available via :local/root**
- Development paths include both levels
- Ideal for tools like **clojure-mcp** (used by Claude for REPL access)
- Provides unified access to entire project scope

**Webapp Level** (`/webapp/deps.edn`): Application-specific dependencies and tooling
- Full application dependencies (Clojure, ClojureScript, databases, etc.)
- Development aliases (`:dev`, `:test`) with rich tooling
- Domain-specific development utilities

The top-level setup **dynamically loads only the :dev and :test dependencies** from webapp/deps.edn on demand. All production dependencies are already available through the :local/root inclusion.

---

## Quick Start

1. **Set up environment variables**:
   ```bash
   cp .env.sample.sh .env.sh  # Copy and customize
   source .env.sh             # Load variables
   ```

2. **Start supporting services**:
   ```bash
   docker compose up -d proxy-local
   ```

3. **Start the REPL** from the project root:
   ```bash
   clj -M:nrepl
   ```

4. **Load environment and start system**:
   ```clojure
   (setup-and-go!)  ; Loads :dev and :test deps, starts system
   ```

**Alternative step-by-step approach**:
```clojure
(setup!)  ; Load :dev and :test dependencies only
(go)      ; Start system when ready
```

## Available Functions

### System Lifecycle
- `(go)` - Start the system
- `(halt)` - Stop the system
- `(reset)` - Restart system with code reload
- `(reset-all)` - Full reset clearing all state

### Code Reloading
- `(refresh)` - Reload changed namespaces
- `(refresh-all)` - Reload all namespaces

### System Access (when system is running)
- `(current-system)` - Get system map
- `(current-config)` - Get config map
- `(db)` - Get database connection
- `(search)` - Get search client
- `(ptv)` - Get PTV integration

### Webapp Utilities
- `(reindex-search!)` - Rebuild search index
- `(reindex-analytics!)` - Rebuild analytics index
- `(reset-admin-password! "password")` - Reset admin password
- `(run-db-migrations!)` - Run database migrations

### Development Utilities
- `(status)` - Check development environment status
- `(setup!)` - Load :dev and :test dependencies from webapp/deps.edn
- `(setup-and-go!)` - Load :dev and :test dependencies and start system

## Running Tests

After loading the development environment:

```clojure
(require '[cognitect.test-runner.api :as tr])
(tr/test {:dirs ["webapp/test/clj"]})
```

## Dev Setup Architecture

### File Structure

```
├── deps.edn              # Top-level project dependencies
├── dev/
│   └── user.clj          # Development utilities with dynamic loading
└── webapp/
    ├── deps.edn          # Webapp-specific dependencies
    └── dev/
        └── user.clj      # Webapp-specific development helpers
```

### User.clj Files

**Top-level `/dev/user.clj`**:
- Provides `(setup!)` function to dynamically load :dev and :test dependencies
- Reads webapp/deps.edn and loads only development-specific dependencies
- Sets up integrant.repl workflow functions (go, halt, reset)
- Loads webapp's user.clj to access domain-specific utilities
- Minimal, focused on bootstrapping development tooling (production deps already available)

**Webapp `/webapp/dev/user.clj`**:
- Domain-specific development helpers for the Lipas application
- System access functions: `(db)`, `(search)`, `(ptv)`
- Application utilities: `(reindex-search!)`, `(reset-admin-password!)`
- Database management: `(run-db-migrations!)`
- Rich comment blocks with development workflows and examples

The top-level user.clj acts as a "launcher" that dynamically loads development tooling, while the webapp user.clj contains the actual domain knowledge and operational utilities. All webapp production dependencies are available immediately via the :local/root inclusion.

## Environment Variables

The system requires various environment variables for full functionality.

**Setup**: Use the shell script approach:
```bash
# Copy the sample and customize
cp .env.sample.sh .env.sh
# Edit .env.sh with your values
# Source before starting REPL
source .env.sh
clj -M:nrepl
```

See `.env.sample.sh` for documentation and `webapp/src/clj/lipas/backend/config.clj` to see how they're loaded.

## Troubleshooting

**Problem**: Functions not available after REPL start
**Solution**: Run `(setup!)`

**Problem**: System won't start with `(go)`
**Solution**: Ensure all required environment variables are set

**Problem**: Can't access webapp namespaces
**Solution**: Check that webapp paths are on classpath with `(status)`

**Problem**: Tests not found
**Solution**: Ensure `webapp/test/clj` is on classpath (should be automatic)
