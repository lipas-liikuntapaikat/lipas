# Scenario: Municipality user creates a sports facility

The most important LIPAS use case. Municipalities produce ~90% of the data; if this flow breaks, LIPAS is broken.

## When to use this

Any task verifying that a user with appropriate privilege can: log in → open map → pick a site type → draw geometry → fill the form → save → see the site indexed and searchable.

## Test data

- **City**: pick from [catalog](../catalog.md#city-codes-commonly-useful) (e.g. Liminka 425)
- **Site type**: pick by geometry type — `3110` swimming pool (Point), `4401` hiking trail (LineString), `1100` outdoor field (Polygon)
- **User**: see Setup below

## Setup

```clojure
(require '[lipas.e2e.tools :as e2e]
         '[lipas.backend.core :as core])

;; Primary fixture: the seeded city-manager for Liminka (limindemo)
(def liminka-user (core/get-user (e2e/db) "liminka@lipas.fi"))

;; If for some reason limindemo isn't seeded:
;; (require '[lipas.seed :as seed])
;; (core/add-user! (e2e/db) seed/liminka-demo)

;; Or impersonate admin/robot when permission isn't what you're testing:
;; (def admin-user (e2e/admin-user))
```

## Trigger — REPL path (fastest, prefer this)

Skip the UI; drive the same `core/save-sports-site!` the HTTP handler calls. Captures all the same validation and side-effects (sync ES index, async jobs).

```clojure
;; Easiest: use the seed helper
(def lid (e2e/seed-site! {:type-code 3110
                          :city-code 425
                          :name "E2E Test Pool"
                          :user liminka-user}))

;; Or build the doc manually for finer control / non-Point geometry
(def site
  {:type {:type-code 3110}
   :name "E2E Test Pool"
   :owner "city"
   :admin "city-sports"
   :status "active"
   :event-date (str (java.time.Instant/now))
   :location {:city {:city-code 425}
              :address "Testikatu 1"
              :postal-code "91900"
              :postal-office "Liminka"
              :geometries
              {:type "FeatureCollection"
               :features
               [{:type "Feature"
                 :properties {}
                 :geometry {:type "Point"
                            :coordinates [25.42 64.81]}}]}}
   :properties {}})
(def saved (core/save-sports-site! (e2e/db) (e2e/search) (e2e/ptv) liminka-user site))
(def lid (:lipas-id saved))
```

**Why `save-sports-site!`, not `upsert-sports-site!`?** `upsert-sports-site!` is DB-only. `save-sports-site!` wraps it in a transaction, calls `index! search resp :sync` after the tx commits, and enqueues the async jobs (analysis, elevation, PTV sync). Always use `save-sports-site!` for e2e.

## Trigger — UI path (when UX matters)

Use only when verifying user-visible behavior the REPL path can't see (form layout, button enablement, geometry-drawing UX).

```
1. POST /actions/login (basic auth: city-user creds)
2. Navigate /liikuntapaikat
3. Click the "Add" FAB (top-right of map) — dispatches ::map.events/start-adding-new-site
4. Step 1: pick site type 3110
5. Step 2: draw point geometry on map (drawend → ::map.events/new-geom-drawn)
6. Step 3: fill form fields (only :name is required)
7. Click Save — dispatches ::map.events/save-new-site → ::sports-sites.events/commit-rev
```

If delegating to the browser-tester subagent: hand it the exact creds, city-code, and type-code. Don't make it discover the flow.

## Verify

`save-sports-site!` indexes ES synchronously, so no waiting needed for create/update assertions. Downstream jobs are async — see "Async jobs" below if you need them.

```clojure
;; 1. DB has the site
(core/get-sports-site (e2e/db) lid)
;; → site doc with :event-date, :status "active"

;; 2. ES doc exists and is enriched
(core/get-sports-site2 (e2e/search) lid)
;; → enriched doc with :search-meta populated (:type :name, :location :city :name, etc.)

;; 3. Cross-layer coherence (DB ↔ ES on key fields)
(e2e/coherent? lid)
;; → {:ok? true :drift []} on a happy path

;; 4. Exactly one raw revision exists
(e2e/revision-count lid)  ;; → 1
;; (Don't use core/get-sports-site-history — it queries the by-year view
;;  and collapses same-year revisions.)

;; 5. Snapshot for failure debugging
(e2e/snapshot lid)
;; → {:db ... :es ... :revs 1 :jobs [...]} — paste into a failure report
```

## Async jobs (only if scenario depends on them)

Save enqueues an `"analysis"` job for every site, plus `"elevation"` for routes/areas. PTV-published sites also get a PTV sync job. These run in the worker process, not in `save-sports-site!`.

```clojure
(e2e/jobs-for lid)                           ;; all jobs for this site
(e2e/jobs-for lid :type "analysis")          ;; filtered

(e2e/wait-for-job lid "analysis" :timeout-ms 30000)
;; → terminal job row, or nil on timeout (no worker / job stuck)
```

Returns nil if no worker is running — that's an environment issue, not a code bug.

## UI verification (if UI path used)

After save, the app navigates to `/liikuntapaikat/<lid>`. Checks:

- Site detail panel shows the entered name
- Search box (sidebar) — typing the name returns this site (after ES catches up — give it ~1s)
- The "Edit" button is visible (current user has the auto-granted `:site-manager` role for this lid)

## Cleanup

```clojure
;; Sports sites are append-only — no delete API. Soft-delete by flipping status:
(e2e/cleanup! lid)

;; For a true clean slate (test DB only):
;; (tu/prune-db! (user/db))   ;; nuclear, NEVER on prod-shaped DBs
```

## Gotchas

- **Auto-permission grant on create.** Even if `city-user` lacked the city role, on first save they'd get `:site-manager` for that lipas-id automatically (`core/ensure-permission!`). So "permission denied" on a create is rare — it only fires if `:analysis-tool/use` is also missing. To test denial, target the *update* scenario instead.
- **Coordinate order**: GeoJSON is `[lon lat]`, not `[lat lon]`. Mixing them up may pass shape validation but fail Finland-bounds validation (lon 18-33°E, lat 59-71°N).
- **Type-specific properties**: passing properties not declared in the type's `:props` map fails Malli validation. Empty `:properties {}` is always safe.
- **Elevation jobs for routes/areas**: LineString and Polygon sites enqueue an elevation enrichment job after save. Coords gain a third element (altitude) once it runs. If you assert on coordinate shape immediately, expect `[lon lat]`; after elevation, expect `[lon lat alt]`.
- **PTV side-effects**: if the city has PTV enabled, save enqueues a PTV sync job. Watch the `jobs` table or use a non-PTV city to keep the scenario clean.
- **Status validation**: only certain statuses are valid initial values. `"active"` is the safe choice; `"planning"` works only if the user has `:analysis-tool/use`.

## Related

- Update flow → [update-site.md](update-site.md)
- Permissions detail → `webapp/docs/auth.md` §Role System
- Document shape → `webapp/docs/data-model.md` §Sports Sites
- Geometry & coordinate systems → `webapp/docs/map-gis.md`
- Backend handler → `src/clj/lipas/backend/handler.clj` `POST /sports-sites` (~line 148)
- Save fn → `src/clj/lipas/backend/core.clj` `upsert-sports-site!`
- Frontend save event → `src/cljs/lipas/ui/map/events.cljs` `::save-new-site` (~line 488)
- New-site form state → `:new-sports-site` in app-db
