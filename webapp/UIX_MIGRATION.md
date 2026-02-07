# UIX → Reagent 2.0 Migration Strategy

## Context

The codebase has two ClojureScript React wrappers: **Reagent** (primary, used everywhere) and **UIX** (used in ~18 files, 93 `defui` components, 776 `$` element calls). The goal is to consolidate on Reagent 2.0, which now provides native hooks support via `r/defc` and `reagent.hooks` — the exact features that originally motivated UIX adoption. Zero regressions required; no frontend tests exist.

## Key Insight: Reagent 2.0 Closes the Gap

Reagent 2.0 (already in `deps.edn`) provides:
- **`r/defc`** — creates functional components (like UIX's `defui`)
- **`reagent.hooks`** — `use-state`, `use-memo`, `use-callback`, `use-effect`, `use-ref`, `use-sync-external-store`, etc.
- **`r/defc` components support both hooks AND ratoms** — meaning `@(rf/subscribe [...])` just works, no custom `use-subscribe` hook needed

## Translation Rules

| UIX | Reagent 2.0 | Notes |
|-----|------------|-------|
| `(defui name [props] ...)` | `(r/defc name [props] ...)` | Direct replacement |
| `($ MuiComponent {:k v} child)` | `[:> MuiComponent {:k v} child]` | `:>` for React components |
| `($ :div {:k v} child)` | `[:div {:k v} child]` | Regular hiccup for HTML |
| `($ :<> child1 child2)` | `[:<> child1 child2]` | Fragment |
| `($ reagent-comp props)` | `[reagent-comp props]` | Regular hiccup for Reagent |
| `(uix/use-state val)` | `(hooks/use-state val)` | `reagent.hooks/use-state` |
| `(uix/use-memo f deps)` | `(hooks/use-memo f deps)` | `reagent.hooks/use-memo` |
| `(uix/use-callback f deps)` | `(hooks/use-callback f deps)` | `reagent.hooks/use-callback` |
| `(use-subscribe [:query])` | `@(rf/subscribe [:query])` | No custom hook needed — ratoms work in `r/defc` |
| `(uix/fn [props] body)` | `(fn [props] body)` | Regular fn |
| `(spread-props m1 m2)` | `(merge-props m1 m2)` | New utility (see below) |

## MUI Component Handling

Use `:>` with direct imports everywhere. Each file imports MUI components directly (as they already do for UIX) and uses `[:> Component ...]`. No `mui.cljs` wrapper layer needed for migrated code.

## `spread-props` Replacement

A small utility to replace the UIX macro, needed only in `autocompletes.cljs` and similar JS-interop-heavy places:

```clojure
(defn merge-props
  "Merges Clojure maps and JS objects into a single JS object."
  [& props]
  (apply js/Object.assign #js {}
    (map #(if (map? %) (clj->js %) %) props)))
```

## Tiered Migration with Before/After Verification

Progress is tracked in `UIX_MIGRATION.md` at the repo root. Each tier follows a strict before/after testing protocol.

### App Access & Login

- **URL**: `https://localhost` (default HTTPS port, self-signed certificate — accept browser warning)
- **Username**: `admin`
- **Password**: read from environment variable `ADMIN_PASSWORD` (use `echo $ADMIN_PASSWORD` or source `../.env.sh`)
- **Login flow**: Navigate to login page, enter credentials, submit
- **Important**: Log in before testing so all authenticated views (admin, PTV, user profile, help management) are accessible

### Before Starting

1. **Create feature branch**: `git checkout -b refactor/remove-uix`
2. **Create `UIX_MIGRATION.md`** at the repo root (`/Users/tipo/lipas/lipas/webapp/UIX_MIGRATION.md`)
   - Contains the full plan (translation rules, tier structure, file list)
   - Has per-tier progress sections with checkboxes
   - Serves as persistent context across conversation windows
   - Will be updated after each tier with verification results
3. **Commit the migration plan** as the first commit on the branch

### Per-Tier Workflow

For **each** tier, the sequence is:

#### Step 1: BEFORE — Capture current behavior via Playwright MCP
- Navigate to each affected page/route
- Take **baseline screenshots** (saved to `screenshots/tier-N/before/`)
- Perform **functional tests** that exercise the UIX components:
  - Click interactive elements (buttons, tabs, accordions)
  - Type into text fields and autocompletes
  - Verify dropdowns open and options render
  - Check that state changes are reflected (e.g., form values update, selections stick)
  - Take accessibility snapshots to capture DOM structure
- Check **console messages** for any pre-existing errors (document them)
- Record findings in `UIX_MIGRATION.md` under the tier section

#### Step 2: MIGRATE — Apply translation rules to all files in the tier
- Rewrite `defui` → `r/defc`, `$` → hiccup, hooks → `reagent.hooks`, etc.
- Update `ns` requires (remove `uix.core`, add `reagent.core`/`reagent.hooks`)

#### Step 3: COMPILE — Verify Shadow-CLJS builds cleanly
- `(user/compile-cljs)` — must be zero warnings/errors
- Fix any compilation issues before proceeding

#### Step 4: AFTER — Re-test identical scenarios via Playwright MCP
- Navigate to the **same pages** as Step 1
- Take **comparison screenshots** (saved to `screenshots/tier-N/after/`)
- Repeat the **same functional tests** from Step 1
- Check **console messages** — no new errors or React warnings
- **Compare before vs after**:
  - Visual: screenshots should match (layout, content, styling)
  - Functional: all interactions should behave identically
  - Console: no new errors

#### Step 5: COMMIT
- If verification passes, commit all migrated files with descriptive message
- Update progress in `UIX_MIGRATION.md`

#### Step 6: STOP if regression detected
- If any functional difference is found, fix before proceeding
- Document the issue and resolution in `UIX_MIGRATION.md`

---

### Tier 1: Leaf components (simplest, no UIX dependents)

**Files:**
- `src/cljs/lipas/ui/user/views.cljs` — 4 defui, 18 `$`
- `src/cljs/lipas/ui/register/views.cljs` — 5 defui, 18 `$`
- `src/cljs/lipas/ui/login/views.cljs` — 7 defui, 39 `$`

**Playwright test plan:**
- Login page: screenshot, type email/password, verify form controls work
- Registration page: screenshot, fill form fields, verify validation
- User profile: screenshot, verify role display, org links

### Tier 2: Shared components (used by many other files)

**Files:**
- `src/cljs/lipas/ui/components/autocompletes.cljs` — 5 defui, 5 `$`. `spread-props` replacement here.
- `src/cljs/lipas/ui/components/lists.cljs` — 3 defui, 10 `$`. External hooks (`useMeasure`, `react-window`).

**Playwright test plan:**
- Find a page with autocomplete selector: type to filter, select an option, verify selection sticks
- Find a page with virtualized list: verify items render, scroll through list
- These are shared components — also spot-check downstream pages

### Tier 3: Feature views

**Files:**
- `src/cljs/lipas/ui/help/views.cljs` — 13 defui, 151 `$`
- `src/cljs/lipas/ui/help/manage.cljs` — 19 defui, 223 `$` (largest file)
- `src/cljs/lipas/ui/admin/views.cljs` — 11 defui, 44 `$`

**Playwright test plan:**
- Help page: screenshot, expand/collapse accordions, search help content, verify video embeds render
- Help management (if accessible): screenshot, test content editing UI
- Admin panel: screenshot, test selectors (type, city, org, activity), verify permission cards

### Tier 4: PTV module (most complex)

**Files:**
- `src/cljs/lipas/ui/ptv/controls.cljs` — 5 defui, 22 `$`
- `src/cljs/lipas/ui/ptv/audit.cljs` — 6 defui, 53 `$`
- `src/cljs/lipas/ui/ptv/components.cljs` — 2 defui, 41 `$`
- `src/cljs/lipas/ui/ptv/site_view.cljs` — 5 defui, 51 `$`
- `src/cljs/lipas/ui/ptv/views.cljs` — 6 defui, 85 `$`

**Required context — read before migrating:**
- `webapp/docs/context-ptv.md` — LLM context document with code organization, UI patterns, key concepts
- `webapp/docs/integration-ptv.md` — Technical architecture, sequence diagrams, data transformations
- `webapp/docs/integration-ptv-audit.md` — Audit feature: UI components, re-frame events, validation, testing checklist

**Playwright test plan:**
- PTV integration page: screenshot, test language tabs, service selectors
- Audit panels: screenshot, test radio buttons, text fields
- Service forms: screenshot, test multi-language input, tab switching

### Tier 5: Call-site updates (files that call UIX components via `$` but have no `defui`)

**Files:**
- `src/cljs/lipas/ui/map/views.cljs` — 2 defui, 8 `$`
- `src/cljs/lipas/ui/org/views.cljs` — 0 defui, 2 `$`
- `src/cljs/lipas/ui/search/views.cljs` — 0 defui, 2 `$`
- `src/cljs/lipas/ui/navbar.cljs` — 0 defui, 1 `$`
- `src/cljs/lipas/ui/bulk_operations/views.cljs` — 0 defui, 3 `$`

**Playwright test plan:**
- Map view: screenshot, verify popup behavior
- Search: screenshot, verify autocomplete in search context
- Navbar: screenshot, verify help drawer opens
- Bulk operations: screenshot, verify type/admin/owner selectors

### Tier 6: Cleanup

**Actions:**
- Delete `src/cljs/lipas/ui/uix/hooks.cljs`
- Delete `src/cljs/lipas/ui/uix/utils.clj` and `utils.cljs`
- Remove `com.pitch/uix.core` from `deps.edn`
- Remove `use-sync-external-store` from `package.json` (verify not used elsewhere)

**Playwright test plan:**
- Full app smoke test: navigate through all major pages
- Verify compilation succeeds with UIX fully removed
- Final commit

## Resuming Work Across Sessions

**At the start of each new session**, read `UIX_MIGRATION_PLAN.md` and check the Progress Tracking section below. Find the first tier that is NOT marked `[x] DONE` and continue from there, following the Per-Tier Workflow.

If a tier is marked as `IN PROGRESS`, read the notes and check commits to understand what was completed and what remains.

---

## Progress Tracking

Update this section as each tier is completed. Copy this to `UIX_MIGRATION_PLAN.md` when executing.

### Tier 1: Leaf components
- [x] BEFORE: Playwright baseline captured
- [x] MIGRATE: Files rewritten
- [x] COMPILE: Shadow-CLJS clean build
- [x] AFTER: Playwright verification passed
- [x] COMMIT: Changes committed
- **Status**: DONE
- **Notes**: Zero regressions. All pages (login, register, profile) render identically. No new console errors. Tab switching, form inputs, login/logout all work correctly. `r/as-element` wrappers removed from Reagent component children (no longer needed in hiccup context), kept only for prop values (e.g. CardHeader `:action`).

### Tier 2: Shared components
- [x] BEFORE: Playwright baseline captured
- [x] MIGRATE: Files rewritten
- [x] COMPILE: Shadow-CLJS clean build
- [x] AFTER: Playwright verification passed
- [x] COMMIT: Changes committed
- **Status**: DONE
- **Notes**: Zero regressions. `spread-props` macro replaced with `merge-props` utility using `Object.assign`. `autocomplete2` uses `hooks/use-memo` for memoization. `virtualized-list` external hooks (`useMeasure`, `react-window`) work correctly in `r/defc`. Call sites in 7 downstream files updated to use `(r/as-element [component ...])` wrapping for UIX→Reagent boundary.

### Tier 3: Feature views
- [x] BEFORE: Playwright baseline captured
- [x] MIGRATE: Files rewritten
- [x] COMPILE: Shadow-CLJS clean build
- [x] AFTER: Playwright verification passed
- [x] COMMIT: Changes committed
- **Status**: DONE
- **Notes**: Zero regressions in all three areas (admin panel, help dialog, help management). Found and fixed critical bug in `autocomplete2` (Tier 2 file): Reagent's `[:>` interop doesn't handle raw JS objects as props — it checks `(map? props)` and treats non-maps as children. Changed `[:> Autocomplete (merge-props ...)]` to `(r/create-element Autocomplete (merge-props ...))` to bypass hiccup processing. Also fixed metadata keys `^{:key idx}` on `[:> MenuItem ...]` in manage.cljs — moved to prop-based keys `{:key idx}` for consistency with `:>` interop. All `defui` → `r/defc`, `$` → hiccup, hooks → `reagent.hooks`. `r/as-element` wrapping applied to MUI prop values (`:title`, `:action`, `:startIcon`, `:expandIcon`). Removed `r/as-element` from admin select components (no longer needed since caller and callee are both Reagent).

### Tier 4: PTV module
- [ ] BEFORE: Playwright baseline captured
- [ ] MIGRATE: Files rewritten
- [ ] COMPILE: Shadow-CLJS clean build
- [ ] AFTER: Playwright verification passed
- [ ] COMMIT: Changes committed
- **Status**: NOT STARTED
- **Notes**: _(verification results, issues found, etc.)_

### Tier 5: Call-site updates
- [ ] BEFORE: Playwright baseline captured
- [ ] MIGRATE: Files rewritten
- [ ] COMPILE: Shadow-CLJS clean build
- [ ] AFTER: Playwright verification passed
- [ ] COMMIT: Changes committed
- **Status**: NOT STARTED
- **Notes**: _(verification results, issues found, etc.)_

### Tier 6: Cleanup
- [ ] UIX files deleted
- [ ] Dependencies removed from deps.edn and package.json
- [ ] COMPILE: Shadow-CLJS clean build
- [ ] AFTER: Full app smoke test passed
- [ ] COMMIT: Final cleanup committed
- **Status**: NOT STARTED
- **Notes**: _(verification results, issues found, etc.)_

---

## Risk Assessment

| Component | Risk | Reason |
|-----------|------|--------|
| `autocomplete2` | **High** | Most-used shared component, `spread-props` replacement, JS interop |
| `virtualized-list` | **High** | External hooks (`useMeasure`), `react-window` integration |
| `help/manage.cljs` | **Medium** | Largest file (223 `$` calls), complex state management |
| PTV module | **Medium** | Complex forms with mixed UIX/Reagent patterns |
| Login/Register | **Low** | Simple forms, well-bounded |
| User views | **Low** | Simple display components |
