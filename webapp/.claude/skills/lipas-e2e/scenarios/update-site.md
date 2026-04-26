# Scenario: Municipality user updates a sports facility

Second-most-important LIPAS use case. Same shape as create, but with stricter permission rules and revision semantics.

## When to use this

Any task verifying edits to existing sites — name change, address change, property change, status flip, geometry edit. Also the right scenario for *permission rejection* tests (create has auto-permission; update does not).

## Test data

Always seed a fresh site per scenario run — don't depend on existing DB state. The dev DB is typically a prod snapshot, so reusing real sites makes tests flaky.

```clojure
(require '[lipas.e2e.tools :as e2e]
         '[lipas.backend.core :as core])

;; Primary user: the seeded city-manager (limindemo, city 425)
(def liminka-user (core/get-user (e2e/db) "liminka@lipas.fi"))

;; Always seed fresh — no find-then-update; gives stable initial state
(def lid (e2e/seed-site! {:type-code 3110
                          :city-code 425
                          :name "Update e2e fixture"
                          :user liminka-user}))
```

(`e2e/find-site` is for exploratory work / failure investigation, not for picking test inputs.)

## Setup variants (when role scope is what you're testing)

```clojure
(require '[lipas.test-utils :as tu])

;; Site scope (only this lipas-id) — also auto-granted to whoever created it
(def site-user (tu/gen-site-manager-user lid :db? true :db-component (e2e/db)))

;; Type scope (all sites of that type-code, any city)
(def type-user (tu/gen-type-manager-user 3110 :db? true :db-component (e2e/db)))

;; For permission-rejection tests: a user with no relevant privilege
(def no-privilege (tu/gen-regular-user :db? true :db-component (e2e/db)))
```

## Trigger — REPL path (fastest)

Fetch current → modify → save. The "modify" step must include a fresh `:event-date` so the revision is well-ordered.

```clojure
(def current (core/get-sports-site (e2e/db) lid))
(def updated (-> current
                 (assoc :name "Updated Name")
                 (assoc :event-date (str (java.time.Instant/now)))))

(def saved (core/save-sports-site! (e2e/db) (e2e/search) (e2e/ptv) liminka-user updated))
;; revision count should now be +1 from before
```

**Use `save-sports-site!`** (sync ES + jobs), not `upsert-sports-site!` (DB-only). See [create-site.md](create-site.md) for the distinction.

## Trigger — UI path via clj REPL (default)

`e2e/ui-update-site!` drives the full edit flow via re-frame dispatches: load → `::edit-site` → `::edit-field` × N → `::save-edits` → wait for save complete.

```clojure
(require '[lipas.e2e.tools :as e2e] :reload)

(e2e/ui-login! "limindemo" "liminka")    ; idempotent

(e2e/ui-update-site! {:lipas-id lid
                      :changes  [[[:name]                    "Updated Name"]
                                 [[:location :address]       "Päivitetty 9"]]})
;; → returns lipas-id once save-edits completes

;; Verify (capture rev count BEFORE the update for an exact-+1 assertion)
(e2e/coherent? lid)              ; → {:ok? true :drift []}
(e2e/revision-count lid)         ; rev-before + 1
(e2e/ui-current-name lid)        ; → "Updated Name"
```

For permission-rejection variants: the dispatch fires regardless; the failure surfaces in `[:sports-sites :errors <ts>]` after save-edits. Read via `(e2e/cljs-eval '(get-in @re-frame.db/app-db [:sports-sites :errors]))`.

### When to fall back to click-driven testing

Only when verifying user-visible interaction the dispatch path can't see — the Edit button enabled-state, form-validation tooltips, geometry handle dragging, etc. See the click-driven gotchas in [create-site.md](create-site.md#when-this-isnt-enough--testing-user-visible-ux).

## Verify

Save is sync through ES — no waiting needed. Capture the rev count before the update so you can assert the exact +1 growth.

```clojure
;; Before the update
(def rev-before (e2e/revision-count lid))

;; ... do the update ...

;; 1. Exactly one new revision
(e2e/revision-count lid)  ;; rev-before + 1

;; 2. DB current = the new value
(:name (core/get-sports-site (e2e/db) lid))  ;; "Updated Name"

;; 3. ES reflects the new value
(:name (core/get-sports-site2 (e2e/search) lid))  ;; "Updated Name"

;; 4. Cross-layer coherence
(e2e/coherent? lid)  ;; → {:ok? true :drift []}

;; 5. Failure debugging snapshot
(e2e/snapshot lid)
```

To inspect raw revision history (older revisions), query `sports_site` directly:
```clojure
(require '[next.jdbc :as jdbc])
(jdbc/execute! (e2e/db)
  ["select event_date, document->>'name' as name
    from sports_site where lipas_id = ?
    order by event_date asc" lid])
```
Don't use `core/get-sports-site-history` — it queries the `sports_site_by_year` view and collapses same-year revisions.

## UI verification

- Detail page shows new value
- Search for new name → finds it
- Search for old name → does not find it (after ES catches up; old revision lives in history but isn't indexed)
- "History" view (if you open it) shows both revisions

## Permission-rejection variant

This is where update differs from create — no auto-grant happens.

```clojure
(try
  (core/upsert-sports-site! (user/db) no-privilege updated)
  (catch clojure.lang.ExceptionInfo e
    (-> e ex-data :type)))
;; → :no-permission
```

For the UI path: a user without privilege should see the "Edit" button hidden (subscription `:lipas.ui.user.subs/check-privilege` returns false), and direct API call returns 403.

## Gotchas

- **Append-only.** Never modify in place. Always fetch → modify → save. Mutating the in-memory map doesn't affect storage; only `upsert-sports-site!` writes.
- **`:event-date` must be fresh.** Reusing the old `:event-date` works but creates ambiguity in revision ordering. Always set to `(str (java.time.Instant/now))`.
- **Status changes are restricted.** Moving a site to `"incorrect-data"` requires `:site/edit-any-status`. Most other transitions are allowed under `:site/create-edit`.
- **Concurrent edits.** Two users saving simultaneously both succeed (both get a revision). Last-write-wins from the "current" perspective. There is no optimistic locking.
- **Geometry edits trigger re-enrichment.** City resolution from coords runs again. Moving a site to a new city changes its `:location :city :city-code` automatically — you don't need to set it.
- **Type changes are flagged but not blocked.** Changing `:type :type-code` is allowed but invalidates type-specific properties. Drop or remap `:properties` first or save will fail Malli validation.
- **PTV-linked sites trigger re-sync.** If the site is PTV-published, an update enqueues a PTV sync job. Inspect the jobs table to confirm.

## Related

- Create flow → [create-site.md](create-site.md)
- Roles & privilege checks → `webapp/docs/auth.md` §Role System
- Append-only model → `webapp/CLAUDE.md` §Sports Sites Data Model
- History query → `src/clj/lipas/backend/core.clj` `get-sports-site-history`
- Permission check site → `src/clj/lipas/backend/core.clj` `check-permissions!`
