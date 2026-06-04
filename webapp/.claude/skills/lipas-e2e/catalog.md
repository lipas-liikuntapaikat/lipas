# LIPAS E2E Catalog

Reference data, REPL recipes, and the e2e tooling API. Load when composing a scenario that isn't covered, or when you need a fixture/route quickly.

## Fixtures

### Always-present users

Defined in `src/clj/lipas/seed.clj`, seeded by `seed-default-users!` (admin, robot, import) and `seed-demo-users!` (the rest). To reseed an individual user into a running DB: `(core/add-user! (user/db) lipas.seed/<user>)`.

| Email | Username | Password | Role | Use |
|---|---|---|---|---|
| `admin@lipas.fi` | admin | `$ADMIN_PASSWORD` from `../.env.sh` | admin (all) | Human-style admin. |
| `robot@lipas.fi` | robot | random | admin | Programmatic ops. `(user/get-robot-user)`. |
| `liminka@lipas.fi` | limindemo | `liminka` | city-manager (city 425) | **Primary fixture for create/update scenarios.** Municipality user with all-types access in one city. |
| `sb@lipas.fi` | sbdemo | `atk-on-ihanaa` | type-manager (type 2240) | Type-scoped role; useful for cross-city type permission tests. |
| `uh@lipas.fi` | uhdemo | `uimahalli` | site-manager (lipas-id 506032) | Single-site role; legacy demo. |
| `jh@lipas.fi` | jhdemo | `jaahalli` | site-manager (lipas-id 89839) | Legacy demo, kept for back-compat. Don't use as primary fixture. |

Get a user from the DB:
```clojure
(require '[lipas.backend.core :as core])
(core/get-user (user/db) "liminka@lipas.fi")
```

### PTV test orgs (use for any PTV scenario)

| City | city-code | PTV org # |
|---|---|---|
| Utajärvi | 889 | 6 |
| Raahe | 678 | 8 |
| Liminka | 425 | 9 |

PTV UUIDs and full details: see `memory/reference_ptv_test_orgs.md` in user memory.

### Type codes (commonly used)

| Code | Name (fi) | Geometry |
|---|---|---|
| 1110 | Tekojääkenttä (outdoor ice rink) | Point |
| 1530 | Kaukalo (rink) | Point |
| 2240 | Liikuntahalli (sports hall) | Point |
| 3110 | Uimahalli (swimming pool) | Point |
| 3210 | Maauimala (outdoor pool) | Point |
| 4401 | Retkeilyreitti (hiking trail) | LineString |
| 4402 | Luontopolku (nature path) | LineString |
| 1100 | Ulkokenttäalue (outdoor field area) | Polygon |
| 101  | Lähiliikuntapaikka (local sports area) | Polygon |

Full table: `lipas.data.types/all` (cljc).

### City codes (commonly useful)

| Code | City | Notes |
|---|---|---|
| 91   | Helsinki | Largest, many sites |
| 179  | Jyväskylä | LIPAS host city |
| 425  | Liminka | PTV test org |
| 678  | Raahe | PTV test org |
| 889  | Utajärvi | PTV test org |

Full table: `lipas.data.cities/by-city-code`.

## Routes (frontend)

Reitit-based, no fragment routing (`:use-fragment false`). Defined in `src/cljs/lipas/ui/routes.cljs` + per-feature `*/routes.cljs`.

| Path | Feature | Route name |
|---|---|---|
| `/kirjaudu` | Login | `:lipas.ui.login.routes/login` |
| `/liikuntapaikat` | Map / sports sites | `:lipas.ui.map.routes/map` |
| `/liikuntapaikat/:lipas-id` | Site detail | `:lipas.ui.map.routes/sports-site` |
| `/admin/*` | Admin panel | `:lipas.ui.admin.routes/*` |
| `/stats` | Statistics | `:lipas.ui.stats.routes/stats` |
| `/user/*` | User profile/settings | `:lipas.ui.user.routes/*` |

Programmatic navigation (browser REPL):
```clojure
(lipas.ui.routes/navigate! :lipas.ui.routes.map/map)
(lipas.ui.routes/navigate! "/liikuntapaikat/12345")
```

## REST endpoints (e2e-relevant)

Defined in `src/clj/lipas/backend/handler.clj`.

| Method+Path | Purpose | Auth |
|---|---|---|
| `POST /actions/login` | Get JWT (basic auth body) | Basic |
| `POST /actions/refresh-login` | Refresh JWT | Token |
| `POST /actions/order-magic-link` | Email magic link | None |
| `POST /sports-sites` | Upsert site (create or update) | Token + `:site/create-edit` |
| `GET /sports-sites/:id` | Get site (current) | Public |
| `GET /sports-sites/:id/history` | Revision history | Public |
| `GET /sports-sites/type/:type-code` | Sites by type | Public |

Permission checks happen inside `core/upsert-sports-site!`, not in middleware. Errors come back as `{:type :no-permission}` ex-data on a 403.

## REPL recipes

### System component access

```clojure
(user/db)              ;; PostgreSQL connection (next.jdbc spec)
(user/search)          ;; Elasticsearch component {:client ... :indices ...}
(user/ptv)             ;; PTV client
(user/get-robot-user)  ;; admin user map for programmatic ops
(integrant.repl.state/system)  ;; full system map
```

### Query a site

```clojure
(require '[lipas.backend.core :as core])

(core/get-sports-site (user/db) 12345)         ;; from DB (current revision)
(core/get-sports-site2 (user/search) 12345)    ;; from ES (enriched, denormalized)
(core/get-sports-site-history (user/db) 12345) ;; all revisions, oldest first
```

### Inspect job queue

```clojure
(require '[next.jdbc :as jdbc])

;; Pending jobs
(jdbc/execute! (user/db)
  ["select id, type, status, attempts, last_error, created_at
    from jobs
    where status = 'pending'
    order by created_at desc
    limit 20"])

;; Jobs for a specific site
(jdbc/execute! (user/db)
  ["select id, type, status, last_error
    from jobs
    where (payload->>'lipas-id')::int = ?
    order by created_at desc"
   12345])
```

### Frontend introspection

```clojure
(user/browser-repl)  ;; switch to ClojureScript REPL (shadow :app)

;; in cljs:
@re-frame.db/app-db
@(rf/subscribe [:lipas.ui.subs/active-panel])
@(rf/subscribe [:lipas.ui.user.subs/check-privilege {:city-code 425} :site/create-edit])
(rf/dispatch [:lipas.ui.events/navigate "/liikuntapaikat"])

:cljs/quit  ;; back to clj REPL
```

### Reindex after schema/data fixes

```clojure
(user/reindex-search!)    ;; re-enrich and re-index sports sites
(user/reindex-lois!)      ;; re-index LOIs
(user/reindex-analytics!) ;; re-index analytics view
```

### Test users via test-utils

```clojure
(require '[lipas.test-utils :as tu])

(tu/gen-admin-user        :db? true :db-component (user/db))
(tu/gen-regular-user      :db? true :db-component (user/db))
(tu/gen-city-manager-user 425 :db? true :db-component (user/db))
(tu/gen-type-manager-user 3110 :db? true :db-component (user/db))
(tu/gen-site-manager-user 12345 :db? true :db-component (user/db))
```

These persist into the running DB. Call `(tu/prune-db! (user/db))` to nuke (test DB only — never prod).

## E2E tools API

Defined in `dev/lipas/e2e/tools.clj`. Load with `(require '[lipas.e2e.tools :as e2e] :reload)`.

| Fn | Returns / purpose |
|---|---|
| `(e2e/seed-site! opts)` | `lipas-id`. Calls `core/save-sports-site!` — same path the HTTP handler uses (sync ES indexing included). Point geometry only. |
| `(e2e/find-site name-fragment)` | first matching `lipas-id` (ILIKE on current DB view) or `nil`. |
| `(e2e/snapshot lipas-id)` | `{:db ... :es ... :revs N :jobs [...]}` — paste into failure reports. |
| `(e2e/coherent? lipas-id)` | `{:ok? bool :drift [{:field ... :db ... :es ...}]}` — DB↔ES field comparison on `:name`, `:status`, `:type-code`, `:city-code`. Extend `coherence-fields` for more. |
| `(e2e/revision-count lipas-id)` | int — raw revisions in `sports_site` table. **Use this**, not `core/get-sports-site-history`, which queries `sports_site_by_year` and collapses same-year revisions. |
| `(e2e/jobs-for lipas-id :type "analysis")` | job rows for a site, newest first. Optional `:type` filter. |
| `(e2e/wait-for-job lipas-id type :timeout-ms 30000)` | first terminal (`completed`/`failed`/`dead`) job row, or `nil` on timeout. For asserting on async work (analysis, elevation, PTV sync). |
| `(e2e/cleanup! lipas-id)` | `nil`. Soft delete via status flip — append-only model means no real delete. Creates a new revision. |

**No `wait-for-indexed`** — `core/save-sports-site!` calls `index! search resp :sync`, so create/update are synchronous through ES. Async surface is the jobs table; use `wait-for-job` for that.

System accessors: `(e2e/db)`, `(e2e/search)`, `(e2e/ptv)`, `(e2e/admin-user)` — mirror the `user/` ones.
