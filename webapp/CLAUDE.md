# LIPAS Development Guide

## REPL Access

nREPL runs on port 7888. Use `clj-nrepl-eval` to evaluate Clojure code:

```bash
# Simple expression
clj-nrepl-eval -p 7888 "(+ 1 2 3)"

# Multiline with heredoc (avoids shell escaping)
clj-nrepl-eval -p 7888 <<'EOF'
(require '[lipas.backend.core :as core] :reload)
(core/get-sports-site db 123456)
EOF
```

Key options: `-t 300000` (custom timeout), `--reset-session` (clear corrupted state)

**Always use `:reload`** when requiring namespaces to pick up changes.

## Common Commands

```clojure
(user/reset)                    ; Reload code and restart system
(user/db)                       ; Get database connection
(user/search)                   ; Get Elasticsearch client
(user/reindex-search!)          ; Reindex after mapping changes
(user/run-db-migrations!)       ; Run pending migrations
(user/browser-repl)             ; Switch to ClojureScript REPL
(user/compile-cljs)             ; Compile ClojureScript
```

System components available via `integrant.repl.state/system` after reset.

## Testing

Reload changed code before running tests:

```clojure
(require 'lipas.jobs.handler-test :reload)
(clojure.test/run-tests 'lipas.jobs.handler-test)
```

Final clean-state verification with babashka:

```bash
bb test-var lipas.jobs.handler-test/authorization-test
bb test-ns lipas.jobs.handler-test
```

### Testing Philosophy

- Integration tests are most valuable, unit tests come second
- Prefer generative/property-based tests over example-based assertions
- Avoid mocking - use proper test fixtures instead
- Use `lipas.backend.org-test` as template for handler tests
- Put common functionality in `lipas.test-utils`

## Browser Testing

Delegate browser testing to `browser-tester` sub-agent to preserve context.

**Before delegating**: Run `(user/compile-cljs)` and fix any build errors.

## Code Style

### Backend (Clojure)

- Use `str` alias for `clojure.string`
- Use `clojure.test` with `deftest`, `testing`, `is`

### Frontend (ClojureScript)

- Prefer Reagent + Re-Frame for new code
- Use UIX only when React hooks are needed by 3rd party libraries
- Use explicit MUI requires, avoid legacy `lipas.ui.mui`
- UIX hooks: call at top level only, list all dependencies in effects

## Project Structure

```
src/
├── clj/lipas/backend/     # Backend: api/, db/, search/, ptv/, jobs/
├── cljs/lipas/ui/         # Frontend: map/, sports_sites/, search/, routes/
└── cljc/                  # Shared code and data model definitions
resources/
├── migrations/            # Database migrations (SQL and EDN)
└── sql/                   # SQL query files
test/clj/                  # Backend tests
dev/user.clj               # REPL development namespace
```

Key config: `deps.edn`, `shadow-cljs.edn`, `bb.edn`, `../.env.sh`

## Sports Sites Data Model

### Database: Append-Only Event Log

The `sports_site` table uses append-only revisions. Each revision shares `lipas_id` but gets unique `id` and `event_date`.

Key columns: `lipas_id`, `id` (UUID), `event_date`, `status`, `document` (JSONB), `author_id`, `type_code`, `city_code`

`sports_site_current` view shows latest revision per `lipas_id`.

SQL queries: `resources/sql/sports_site.sql`

### Elasticsearch Index

`sports_sites_current` contains enriched, denormalized documents for search. The `enrich` function adds:
- Resolved references (type names, city names, owner names)
- Geospatial data formatted for ES queries
- Search metadata for filtering/faceting

Core functions in `lipas.backend.core`: `index!`, `enrich`, `search`, `save-sports-site!`

### Data Flow

```
User Edit → sports_site table (new revision)
         → sports_site_current view
         → Elasticsearch index (enriched)
         → API reads & search
```

Database is source of truth; Elasticsearch is read-optimized cache.

### Schema Changes

- Never make breaking changes - LIPAS has long history
- Don't rename or remove enum values (e.g., `lipas.data.owners`, `lipas.data.admins`)
- Additive changes are fine
- Query database to check if a value is in use before removal

## Workflow Communication

For large tasks, use `say` command: "I'm done" when finished, "Please help" if stuck.
