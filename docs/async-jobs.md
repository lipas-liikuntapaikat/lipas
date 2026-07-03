# LIPAS Async Jobs System

A single PostgreSQL-backed job queue for all background processing:
diversity analysis, elevation enrichment and email delivery.

Design goals: simplicity, robustness, maintainability. One jobs table,
one registry of job types, one worker process.

## Architecture

```
save-sports-site! ──► jobs table ──► worker (polls) ──► handler
                      (Postgres)         │
scheduler ───────────► email jobs        ├── fast lane  (email, webhook)
(reminders, direct)                      └── general lane (analysis, elevation)
```

- **Producers** call `lipas.jobs.core/enqueue-job!`. The main producer is
  `save-sports-site!`, which enqueues `analysis` and `elevation` (routes
  only) **after its transaction commits**, and only when the save changed
  the inputs the job consumes (registry `:trigger-fn`, see below). The
  elevation handler writes its enriched revision back through the
  low-level upsert on purpose — a robot save must not enqueue new jobs,
  or every completed enrichment would trigger another cycle.
- **The worker** (`lipas.jobs.worker`) polls the `jobs` table with
  `SELECT FOR UPDATE SKIP LOCKED` and runs handlers on two thread pools:
  a fast lane reserved for quick jobs and a general lane for everything.
  This prevents slow analysis jobs from blocking email delivery.
- **The scheduler** (`lipas.jobs.scheduler`) runs periodic maintenance
  directly (not through the queue): producing reminder emails, recovering
  stuck jobs, retention cleanup and health checks. Only real work (an
  actual email to send) becomes a job.

## State model

```
pending ──► processing ──► completed
   ▲             │
   └── retry ◄───┤ failure, attempts < max  (pending with future run_at)
                 │
                 └── failure, attempts = max ──► dead_letter_jobs
                                                 (removed from jobs)
```

There are exactly three statuses in the `jobs` table: `pending`,
`processing` and `completed`. A retry is a pending job whose `run_at` is
in the future (exponential backoff with jitter). Permanently failed jobs
live only in `dead_letter_jobs`, where admins can inspect, reprocess or
acknowledge them via the admin UI.

Three invariants are enforced by the SQL statements themselves
(`resources/sql/jobs.sql`), not by convention:

1. **One door into `pending`.** Every transition into `pending` — fresh
   enqueue, retry, stuck recovery — is an `INSERT … ON CONFLICT DO
   NOTHING` against the pending-only dedup index. A conflict is not an
   error: it means a newer pending job for the same entity already covers
   the work, and the retry/recovery is dropped as *superseded*.
2. **Fenced finalization.** Claiming a job increments `attempts`, and
   every finalizer (`completed`, retry, dead-letter) matches
   `(id, attempts, status = 'processing')`. A stale executor — a zombie
   thread, a double finalization — updates zero rows instead of
   clobbering newer state. The database is the arbiter of ownership.
3. **Per-entity sequential execution.** `fetch-next-jobs` never claims a
   pending job while a processing job with the same `(type, dedup_key)`
   exists, so a successor always runs strictly after its predecessor and
   its fresher results can never be overwritten by a slower stale run.

## Job type registry

`lipas.jobs.registry` is the single source of truth. Each entry defines:

| Key | Purpose |
|---|---|
| `:payload-schema` | Malli schema, validated at enqueue time |
| `:lane` | `:fast` or `:slow` — worker thread lane |
| `:timeout-min` | hard execution timeout (watchdog) |
| `:priority` | default priority, higher runs first |
| `:max-attempts` | retries before dead-lettering |
| `:dedup-key-fn` | optional — deduplicates pending jobs |
| `:debounce-sec` | optional — delay before the job is runnable |
| `:trigger-fn` | optional — `(fn [old-site new-site])`; save enqueues the job only when its inputs changed |

Handlers are `defmethod`s on `lipas.jobs.dispatcher/handle-job`; the
worker asserts at startup that every registered type has one.

Current job types:

| Type | Lane | Timeout | Dedup | Notes |
|---|---|---|---|---|
| `analysis` | slow | 60 min | per lipas-id, 30 s debounce | diversity grid recalc |
| `elevation` | slow | 30 min | per lipas-id, 30 s debounce | MML elevation, circuit breaker |
| `email` | fast | 1 min | – | reminders + general mail |
| `webhook` | fast | 2 min | – | **disabled**: producers commented out until UTP needs it again |

To add a job type: add a registry entry, add a `handle-job` defmethod,
enqueue it somewhere. That's it.

## Change triggers, dedup and debounce

Three layers keep the expensive jobs from running needlessly:

1. **Change triggers.** A save enqueues `elevation` only when the 2D
   geometries changed (or previously enriched z data was lost), and
   `analysis` only when the type code, status or 2D geometries changed.
   A phone-number edit — or a bulk property update across hundreds of
   sites — enqueues nothing. Comparisons are conservative: no previous
   revision or unrecognized shape means enqueue. The z coordinates that
   elevation writes back are invisible to the diff, which also breaks
   any enrichment feedback loop.
2. **Dedup.** `analysis`/`elevation` carry a dedup key
   (`analysis:<lipas-id>`), so a burst of edits produces one pending job
   per site. Deduplication applies to **pending** jobs only: if a job is
   already processing when a new edit arrives, a successor is enqueued —
   the running execution may have read pre-edit data. Concurrency is
   handled with a partial unique index + `ON CONFLICT DO NOTHING`; a
   losing `enqueue-job!` returns `nil`.
3. **Debounce.** A 30-second `run_at` delay coalesces rapid edit bursts
   before the job becomes claimable.

The running job is never cancelled when superseded: it completes, and
the successor (serialized after it by the claim guard) recomputes with
fresh data. In the other direction, a *failed* job whose successor is
already pending is dropped instead of retried.

## Timeouts and stuck jobs

Handlers run directly on worker pool threads. A single watchdog thread
interrupts a pool thread when its job exceeds the registry timeout. The
interrupt flag is cleared (under a lock) before the job's status is
updated.

If the handler has not unwound one minute after the interrupt
(`worker/watchdog-grace-ms`) — non-interruptible IO, a swallowed
interrupt — the watchdog finalizes the job in the DB itself (retry or
dead-letter) and the handler thread is left behind as a **zombie**: it
keeps occupying its lane thread until its blocking call returns, but
fenced finalization guarantees its late DB updates are no-ops. Zombies
are logged loudly and counted in `worker-stats`; handlers must set
socket timeouts so they cannot hang forever (the SMTP client sets
`mail.smtp.*timeout`, the MML client sets HTTP timeouts).

Crash recovery: jobs stuck in `processing` longer than
`lipas.jobs.core/stuck-job-timeout-minutes` (longest registered timeout
+ 30 min margin) are recovered on worker startup and every 10 minutes by
the scheduler — re-enqueued through the dedup gate if attempts remain
(dropped when a newer pending job supersedes them), otherwise moved to
the dead letter queue. Recovery is per-row: one superseded job never
blocks the rest of the batch.

## Reliability patterns (`lipas.jobs.patterns`)

- **Exponential backoff with jitter** for retries.
- **In-memory circuit breaker** protecting the MML elevation API: after 5
  consecutive failures, elevation jobs fail fast (and retry via the normal
  retry machinery) for 2 minutes instead of hammering a down service.
  State is per-JVM, which is sufficient for the single worker process.
  Thread interrupts (the watchdog firing) are rethrown without counting
  as failures — a slow job says nothing about the service's health.
- **`pmap-with-timeout`** for per-chunk timeouts inside handlers.

## Retention

The scheduler applies retention daily:

- completed jobs: deleted after 30 days
- acknowledged dead letters: deleted after 90 days
- unacknowledged dead letters: kept until an admin acts on them

## Admin API and UI

Admin endpoints (privilege `:jobs/manage`, used by the admin UI's
"Jobs Monitoring" and "Dead Letter Queue" tabs):

- `POST /api/actions/create-jobs-metrics-report` — performance metrics + throughput
- `POST /api/actions/get-jobs-health-status` — pending/processing/retrying/dead counts
- `GET  /api/actions/get-dead-letter-jobs`
- `POST /api/actions/reprocess-dead-letter-jobs`
- `POST /api/actions/acknowledge-dead-letter-jobs`

Health checks also run inside the worker every 15 minutes and log
warnings/errors (stuck jobs, old pending jobs, dead letter growth) to the
central log pipeline.

## Running

Production and local compose both use the same entry point:

```
java -jar backend.jar -m lipas.backend.system worker
```

Worker configuration via env vars (defaults in parentheses):
`WORKER_FAST_THREADS` (2), `WORKER_GENERAL_THREADS` (2),
`WORKER_BATCH_SIZE` (10), `WORKER_POLL_INTERVAL_MS` (3000).
Per-job-type timeouts live in the registry, not in env vars.

## Code map

```
src/clj/lipas/jobs/
├── registry.clj    # job type definitions (single source of truth)
├── core.clj        # enqueue, retry/dead-letter, recovery, retention, stats
├── db.clj          # HugSQL bindings (resources/sql/jobs.sql)
├── dispatcher.clj  # handle-job defmethods per type
├── worker.clj      # polling loop, thread lanes, watchdog timeouts
├── scheduler.clj   # periodic maintenance (direct calls, not jobs)
├── patterns.clj    # backoff, in-memory circuit breaker, pmap-with-timeout
├── monitoring.clj  # health checks
├── schema.clj      # admin endpoint request/response schemas
└── handler.clj     # admin HTTP endpoints
```

Tests: `test/clj/lipas/jobs/` — enqueue/dedup/debounce (including
concurrent race tests), watchdog timeout semantics, retry→dead-letter
ladders, stuck-job recovery, retention, scheduler tasks, admin endpoints.

## History

The system was introduced in 2025 replacing five separate queue tables.
In mid-2026 it was simplified based on 10 months of production data:
correlation tracing, `parent_job_id`, the DB-backed circuit breaker and
the write-only `job_metrics` table were removed; reminders/cleanup no
longer run through the queue; dead jobs moved out of the `jobs` table;
dedup + debounce and correct watchdog timeouts were added for the heavy
`analysis`/`elevation` jobs. The `webhook` job type is kept registered
but has no live producers.
