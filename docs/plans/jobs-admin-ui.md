# Plan: Jobs admin UI rework

Status: planned 2026-07-02, on top of branch `refactor/jobs-simplification`
(PR #206). Implement after (or on top of) that branch — the plan assumes
the new state model (pending/processing/completed + dead_letter_jobs),
the registry, and the `retrying_count` health field.

## Goals (from Valtteri)

The view is primarily for one admin. Two jobs-to-be-done:

1. **Trust at a glance** — see that jobs are flowing: what's queued,
   what's running, what completed recently, is the worker alive.
2. **Failure triage** — see dead letters, understand *why* each failed,
   and resolve them with meaningful one-click actions.

Design principles: information density over polish, English-only (admin
tool), every common triage decision should be one click. Key insight from
production data: **96% of dead letters were acknowledged without
reprocessing** — so the highest-value features are the ones that automate
the "this is noise, wave it through" decision (error grouping, superseded
detection, per-group bulk actions).

## Current state (what exists)

- `webapp/src/cljs/lipas/ui/admin/views.cljs` — `jobs-monitoring-tab`
  (~line 840): 4 health tiles, performance-metrics table, current-stats
  cards, fast/slow lane lists. `dead-letter-queue-tab` (~line 952):
  ack filter, bulk select, reprocess/acknowledge. `job-details-dialog`
  (~line 767): raw error + payload JSON.
- Events/subs in `admin/events.cljs` (~line 267–490), `admin/subs.cljs`
  (~line 149–203). App-db under `[:admin :jobs ...]`.
- Backend endpoints (all `:jobs/manage`): create-jobs-metrics-report,
  get-jobs-health-status, get-dead-letter-jobs,
  reprocess-dead-letter-jobs, acknowledge-dead-letter-jobs.
- `hourly-throughput` data is already returned by the metrics report but
  **never rendered** — free win for a chart.
- Recharts 3 via existing `lipas.ui.charts` wrapper.

## Tab 1: Overview ("are jobs working?")

```
┌─ Health ────────────────────────────────────────────────────┐
│ [Pending 3] [Processing 1] [Retrying 0] [Dead letters 2]    │
│ oldest pending: 2 min · longest processing: 4 min           │
│ ⚠ banner when worker looks down (old pending + 0 processing)│
├─ Activity (24h | 7d toggle) ────────────────────────────────┤
│ stacked bar per hour: completed / retried / dead-lettered   │
│ per-type table: type · ok · failed · avg s · p95 s (trimmed)│
├─ Queue now ─────────────────────────────────────────────────┤
│ processing + due-pending jobs:                              │
│ id · type · site (lipas-id link) · attempt · age · started  │
├─ Recent ────────────────────────────────────────────────────┤
│ last 20 finished: id · type · site · duration · attempts    │
└─────────────────────────────────────────────────────────────┘
[auto-refresh: 30s toggle, on while tab visible]
```

Details:
- Keep the four tiles (pending / processing / retrying / unack DLQ) and
  age lines; already correct after the refactor.
- **Worker-down heuristic banner**: `oldest_pending_minutes > 10` AND
  `processing_count = 0` AND `pending_count > 0` → "Worker appears to be
  down or stalled". Derived client-side from existing health data.
- **Throughput chart**: render the already-fetched hourly-throughput as
  a stacked bar (Recharts, reuse `lipas.ui.charts` conventions). 24h
  default, 7d via the existing from-hours-ago param (168 max).
- **Queue now / Recent** need one new backend action (see below). Show
  payload lipas-id as a link to the site view (`/liikuntapaikat/<id>`).
- Drop the "Job Types Configuration" fast/slow lists (static registry
  info, no operational value) — or collapse into a tooltip.
- Auto-refresh: re-frame interval dispatch while the jobs tab is active;
  30 s; manual refresh button stays.

## Tab 2: Dead letters ("what broke, and fix it")

The DLQ is a *problem signal*, not just a cleanup list. Historically dead
letters went unattended because root-causing each one cost too much
energy. The grouping view must answer, per problem class, the two
questions that decide priority with zero digging:

1. **Is it still happening?** (last occurrence, this-week count, weekly
   trend) — an ongoing drip is a live bug; a months-old burst was a
   transient outage.
2. **One bad site or systemic?** (distinct sites affected, top repeat
   offenders) — 50 failures on 1 site is a data problem on that site;
   50 failures on 50 sites is a code/infra problem.

Groups are ordered by priority: active groups (recent occurrences)
first, by unacknowledged count.

```
┌─ Summary ───────────────────────────────────────────────────┐
│ [Unack 12] filter: (Unacknowledged | All | Acknowledged)    │
│ [Acknowledge all superseded (5)]                            │
├─ Grouped by error class × trend ────────────────────────────┤
│ ▼ Timeout · ACTIVE (12 unack / 42 total)                    │
│     analysis ×36, elevation ×6                              │
│     first 2026-03-02 · last today · 3 this week  ▂▁▁▅▂▁▇   │
│     14 distinct sites · top: 123456 🔗 (×9), 78901 🔗 (×4)  │
│     top messages: "timed out after 60 minutes" (38), …      │
│     [Reprocess all] [Acknowledge all]                       │
│     died · type · site · attempts · superseded? · error…    │
│ ▶ MML API error · STALE, last 2026-04-21 (3)  [Ack stale]   │
│ ▶ Site not found (1)                                        │
└─────────────────────────────────────────────────────────────┘
```

Per-group triage stats (all derived **client-side** from the already
fetched DLQ list — no extra endpoints; the DLQ is bounded by retention,
thousands of rows at most):

- unack / total counts, per-type breakdown
- first seen · last seen · count in last 7 days · weekly sparkline
- **ACTIVE** badge (occurrences within 7 days) vs **STALE** (none in
  30 days); stale groups get a suggested one-click "Acknowledge stale"
- distinct affected sites + top repeat offenders (lipas-id × count,
  linked) — repeat offenders are usually the root cause for data-driven
  failures (bad geometry etc.)
- top distinct error-message variants (the class is coarse; the actual
  messages carry the specifics)

Detail dialog (per entry):
- **Timeline**: created → started → died, with queue time and run time
  computed from original_job timestamps + attempts/max.
- **Parsed context**: type, payload (lipas-id linked), priority,
  created_by, dedup key.
- **Full error message** (existing).
- **Superseded notice**: "A newer <type> job for site <id> completed at
  <ts> — this failure is obsolete" with one-click Acknowledge.
- Actions: Reprocess (with optional max-attempts input — API already
  supports it), Acknowledge, "Show other failures for this site"
  (client-side filter), Copy job EDN (clipboard, for REPL debugging).

### Error classification

Small pure function, shared server-side (`lipas.jobs.monitoring` or new
`lipas.jobs.triage`) so future alerting can reuse it:

| Class | Match (error_message) |
|---|---|
| `:timeout` | `timed out` |
| `:mml-api` | `clj-http`, `Connection reset`, `SocketTimeout`, circuit breaker open |
| `:site-not-found` | `not found` |
| `:search` | `spandex`/`Response Exception`/ES |
| `:oom` | `OutOfMemory` |
| `:other` | fallback |

Classify server-side in `get-dead-letter-jobs` response (`:error-class`)
— keeps the UI dumb and the table testable.

### Superseded detection

Server-side enrichment in `get-dead-letter-jobs`: for entries whose
original_job has a lipas-id payload, `:superseded-by {:job-id :completed-at}`
when a *completed* job of the same type + lipas-id exists with
`completed_at > died_at`. SQL: lateral join on
`jobs.type = original_job->>'type' AND jobs.payload->>'lipas-id' = ...
AND status='completed' AND completed_at > died_at`. (Completed jobs are
retained 30 days — good enough; older dead letters simply show unknown.)
Drives the "Acknowledge all superseded" bulk button.

## Backend additions

1. **`POST /api/actions/search-jobs`** `{statuses [..], limit}` →
   rows: id, type, payload, status, attempts, max_attempts, priority,
   created_at, started_at, completed_at, run_at, last_error. One HugSQL
   query, malli schemas, `:jobs/manage`. Serves "Queue now" (statuses
   pending+processing) and "Recent" (completed, order by completed_at
   desc, limit 20).
2. **get-dead-letter-jobs enrichment**: `:error-class` + `:superseded-by`
   (see above).
3. **`classify-error`** pure fn + unit tests.
4. No schema/migration changes needed.

## Frontend structure

- Extract jobs UI out of the 1,100-line `admin/views.cljs` into
  `lipas.ui.admin.jobs.views` / `.events` / `.subs` (app-db stays under
  `[:admin :jobs ...]`; admin views.cljs just mounts the two tabs).
- `r/defc` functional components, explicit MUI requires (no legacy
  `lipas.ui.mui`), Recharts via `lipas.ui.charts` conventions.
  Read the dataviz skill before writing the chart.
- Polling: `:dispatch-later`-based loop guarded by "jobs tab active" flag
  in app-db.

## Testing

- Backend: handler tests for search-jobs (statuses filter, limit, auth);
  superseded enrichment (fresh dead letter + later completed job →
  flagged; no later job → not flagged); classify-error unit tests.
  Template: existing `lipas.jobs.handler-test`.
- Frontend: `(user/compile-cljs)` clean; browser-tester subagent pass —
  seed dev DB via REPL (enqueue + fail jobs to create dead letters incl.
  one superseded case), then verify both tabs render, group expansion,
  bulk ack, detail dialog, reprocess flow.
- Add a REPL seed helper in a comment block (dev convenience).

## Phases (independently landable)

- **A. Backend**: search-jobs endpoint, DLQ enrichment, classifier + tests.
- **B. Overview tab**: extraction to new ns, throughput chart, queue
  now/recent tables, worker-down banner, auto-refresh.
- **C. Dead letters tab**: grouping, superseded actions, detail dialog.

## Non-goals

- No alerting/notification integration (logs pipeline handles that).
- No i18n for the admin tool.
- No historical metrics store (job_metrics stays dead; 30-day jobs
  retention bounds what "Activity" can show, which is fine).
