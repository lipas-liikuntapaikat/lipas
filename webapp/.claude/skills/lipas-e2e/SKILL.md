---
name: lipas-e2e
description: Run end-to-end scenarios against a live LIPAS dev system. Use when verifying a user-visible flow works correctly across DB, Elasticsearch, app-db, and DOM. Covers municipality-user flows (create/update sports facility) and grows from there.
---

# LIPAS E2E

Driving real flows through a running dev system, verifying coherently across DB, ES, app-db, and DOM. Not blackbox UI testing — glass-box, REPL-augmented.

## Prerequisites

- nREPL on `localhost:7888` (use `clj-nrepl-eval -p 7888`)
- App reachable at `https://localhost` (self-signed cert)
- System running: `(user/reset)` if it isn't, `(user/refresh-all)` then `(user/reset)` after code changes
- Browser MCP available if a scenario needs UI verification

## Choose a scenario

| Want to verify | Open |
|---|---|
| Municipality user creates a sports facility | [scenarios/create-site.md](scenarios/create-site.md) |
| Municipality user updates an existing sports facility | [scenarios/update-site.md](scenarios/update-site.md) |

If your task isn't listed, compose from the [catalog](catalog.md). Add a new scenario file when the task you just did is something you'll do again.

## E2E tooling

`lipas.e2e.tools` (in `dev/lipas/e2e/tools.clj`) — REPL helpers for seeding, snapshotting, and verifying. Public API summary in [catalog.md](catalog.md#e2e-tools-api). Load it with `(require '[lipas.e2e.tools :as e2e] :reload)`.

## Where domain facts live

Don't redocument what exists. Load these only when the task touches the area:

| Topic | Source | Anchors worth jumping to |
|---|---|---|
| Sports site doc shape | `webapp/docs/data-model.md` | §Sports Sites (lines 60-108) |
| Type codes & geometry per type | `webapp/docs/data-model.md` | §Type Code Hierarchy |
| Roles, privileges, user permissions | `webapp/docs/auth.md` | §Role System (lines 100-160) |
| Coordinate systems & geometry storage | `webapp/docs/map-gis.md` | §Coordinate Systems |
| Append-only revision model | `webapp/CLAUDE.md` | §Sports Sites Data Model |
| Background jobs (PTV sync, elevation) | `webapp/docs/architecture.md` | §Background Job System |

## Cross-cutting trip-wires

Things that bite if forgotten:

- **Append-only.** Every save creates a new revision (same `lipas-id`, new `id`+`event-date`). `sports_site_current` view shows latest. There is no UPDATE — always fetch → modify → save.
- **Use `core/save-sports-site!`, not `core/upsert-sports-site!`.** Upsert is DB-only and skips ES + jobs. Save wraps it in a transaction, then calls `index! search resp :sync` (synchronous ES write) and enqueues async jobs. The HTTP handler uses save; e2e should too.
- **For revision counts, use `e2e/revision-count`, not `core/get-sports-site-history`.** History queries the `sports_site_by_year` view, which collapses same-year revisions. Burns easily when asserting "exactly one new revision."
- **Async surface = jobs table.** Save itself is sync through ES; analysis/elevation/PTV-sync jobs run in the worker. Use `e2e/wait-for-job` only when scenario asserts on those.
- **Coordinates are `[lon lat]`** (GeoJSON order), inside Finland bounds (lon 18-33°E, lat 59-71°N). Mistakes here surface as Malli validation errors.
- **PTV uses `:sv`, LIPAS uses `:se`** for Swedish. Translate at API edges, not internally.
- **Permissions checked in core, not middleware.** The handler trusts `core/upsert-sports-site!` to throw `:no-permission`. Privilege key for sites: `:site/create-edit`.
- **Auto-permission grant on create.** A user creating a new site with no city/type role automatically gets `:site-manager` for that lipas-id (`core/ensure-permission!`). So "permission denied" only fires on edits, not creates.
- **Append-only ⇒ no real delete.** Soft delete = flip status to `out-of-service-permanently`. For a clean slate, `lipas.test-utils/prune-db!` (test DB only).

## Don't load (low signal for e2e)

These docs exist but cost more context than they pay back:

- `docs/frontend.md` — verbose, has stale facts (UIx removed but still mentioned, version numbers off)
- `docs/guide-frontend-patterns.md` — generic Re-frame patterns; grep the codebase instead
- `docs/guide-testing.md` — generic LLM coaching, not LIPAS testing
- `docs/guide-debugging.md` — generic debugging principles
- `docs/context-repl.md` — leaked system prompt, not a reference

If you find yourself reaching for one of these for an e2e task, you probably need to add to the [catalog](catalog.md) or a scenario instead.

## Maintenance

After completing an e2e task:

1. **Did you discover a fact you had to dig for?** Add it to the relevant scenario, or to [catalog.md](catalog.md) if cross-cutting.
2. **Did you do a flow not yet covered?** Drop a new file in `scenarios/`. Use the existing ones as templates.
3. **Did a scenario steer you wrong?** Fix it. Stale > missing.
4. **Did you find a tool you needed but didn't have?** Add a stub to `lipas.e2e.tools` with a docstring describing the intent.
