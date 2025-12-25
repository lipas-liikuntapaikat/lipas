# Test Infrastructure: Removing Global Vars from test-utils

## Problem Statement

The `lipas.test-utils` namespace currently defines global vars (`db`, `app`, `search`) that are initialized at namespace load time. This causes:

1. **StackOverflow on REPL reload** - When namespaces are reloaded, malli schema `#'var` references become stale, causing infinite recursion when routes are compiled
2. **Implicit shared state** - Tests silently depend on globals, making isolation and reasoning harder
3. **Reload ceremony** - The current staged fix uses `delay`, requiring `@` deref at every call site

## Proposed Solution

Adopt the **fixture-based pattern** already used in `org_test.clj` and `bulk_operations_test.clj`:
- Each test namespace owns its own `test-system` atom
- Use `full-system-fixture` to manage lifecycle
- Define local accessor functions (`test-db`, `test-app`, etc.)
- Remove global vars entirely from `test-utils`

## Current State Analysis

### Test Namespaces Using Global Vars

| Namespace | Uses | Pattern | Effort |
|-----------|------|---------|--------|
| `lipas.wfs.core-test` | `test-utils/db` (17 refs) | No fixtures, direct db access | Medium |
| `lipas.jobs.handler-test` | `:refer [app db]` (many refs) | `init-db!` fixture, no system | Medium |
| `lipas.backend.ptv-test` | `:refer [app db]` (6 refs) | `init-db!` fixture, no system | Medium |
| `lipas.backend.analysis.diversity-test` | `prune-es!` (0-arity) | Custom partial system | Low |

### Test Namespaces Already Using Proper Pattern

| Namespace | Status |
|-----------|--------|
| `lipas.backend.org-test` | Full-system-fixture pattern |
| `lipas.backend.bulk-operations-test` | Full-system-fixture pattern |
| `legacy-api.handler-test` | Full-system-fixture pattern (just refactored) |

### Comment Blocks (REPL usage, not actual tests)

- `lipas.backend.bulk-operations-test` line 334: `(test-utils/prune-es!)` in comment
- `lipas.test-utils` line 361: `(prune-es!)` in comment

## Migration Plan

### Phase 1: Update test-utils infrastructure

**File: `test/clj/lipas/test_utils.clj`**

1. Remove global `db`, `app`, `search` defs (currently wrapped in `delay`)
2. Remove `system-atom` and related functions (`ensure-test-system!`, `reset-test-system!`, `stop-test-system!`)
3. Update `prune-es!` to require explicit search component (remove 0-arity)
4. Update `gen-user` to require explicit `:db-component` when `db?` is true (remove fallback to global)
5. Keep `db-fixture` and `db-and-search-fixture` for simpler tests that don't need full system
6. Ensure `full-system-fixture` remains the recommended pattern

### Phase 2: Migrate test namespaces

#### 2.1 `lipas.wfs.core-test` (17 references)

Current:
```clojure
(lipas.backend.db.db/upsert-sports-site! test-utils/db test-user site)
```

After:
```clojure
(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

;; In tests:
(lipas.backend.db.db/upsert-sports-site! (test-db) test-user site)
```

Changes needed:
- Add `test-system` atom and fixture setup
- Add `test-db` accessor
- Replace all 17 occurrences of `test-utils/db` with `(test-db)`

#### 2.2 `lipas.jobs.handler-test` (many references)

Current:
```clojure
[lipas.test-utils :refer [->json <-json app db] :as tu]
;; uses `app` and `db` directly
```

After:
```clojure
[lipas.test-utils :refer [->json <-json] :as tu]

(defonce test-system (atom nil))
(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-app [] (:lipas/app @test-system))
(defn test-db [] (:lipas/db @test-system))

;; In tests:
(test-app) instead of app
(test-db) instead of db
```

Changes needed:
- Remove `app db` from `:refer`
- Add system atom and fixture
- Add accessor functions
- Replace all `app` calls with `(test-app)`
- Replace all `db` calls with `(test-db)`

#### 2.3 `lipas.backend.ptv-test` (6 references)

Current:
```clojure
[lipas.test-utils :refer [<-json app db] :as tu]
(backend-org/create-org db org)
(app (-> (mock/request ...)))
```

After: Same pattern as jobs.handler-test

Changes needed:
- Remove `app db` from `:refer`
- Add system atom and fixture
- Add accessor functions
- Update 6 references

#### 2.4 `lipas.backend.analysis.diversity-test`

Current:
```clojure
;; Custom partial system (only :lipas/search)
(use-fixtures :each
  (fn [f]
    (test-utils/prune-es!)  ;; Uses 0-arity which relies on global
    (f)))
```

After:
```clojure
(use-fixtures :each
  (fn [f]
    (test-utils/prune-es! (test-search))
    (f)))
```

Changes needed:
- Pass `(test-search)` to `prune-es!`

### Phase 3: Remove deprecated code from test-utils

Once all namespaces are migrated:

1. Remove the `delay`-wrapped globals
2. Remove `system-atom` and helper functions
3. Remove 0-arity `prune-es!`
4. Update `gen-user` to fail if `db?` is true but no `:db-component` provided
5. Update `db-and-search-fixture` to not use globals

## Benefits After Migration

1. **No reload issues** - No top-level code runs on namespace load
2. **Explicit dependencies** - Each test namespace declares what it needs
3. **Better isolation** - Tests can't accidentally share state
4. **No deref ceremony** - `(test-db)` instead of `@test-utils/db`
5. **Consistent pattern** - All test namespaces follow the same structure
6. **Easier parallel execution** - Systems are isolated per namespace

## Template for New Test Namespaces

```clojure
(ns my.new-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [lipas.test-utils :as test-utils]
   [ring.mock.request :as mock]))

;;; Test system setup ;;;
(defonce test-system (atom nil))

;;; Fixtures ;;;
(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Accessors ;;;
(defn test-app [] (:lipas/app @test-system))
(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))

;;; Tests ;;;
(deftest my-test
  (testing "something"
    (let [resp ((test-app) (mock/request :get "/api/foo"))]
      (is (= 200 (:status resp))))))
```

## Execution Order

1. Migrate `lipas.wfs.core-test` (largest, most references)
2. Migrate `lipas.jobs.handler-test`
3. Migrate `lipas.backend.ptv-test`
4. Update `lipas.backend.analysis.diversity-test`
5. Remove deprecated code from `test-utils`
6. Run full test suite with `bb test`
7. Verify REPL reload works cleanly
