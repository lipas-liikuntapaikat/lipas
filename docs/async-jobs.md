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
  `save-sports-site!`, which enqueues `analysis` (every save) and
  `elevation` (route geometries).
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

## Dedup and debounce

Repeated saves of the same sports site coalesce: `analysis`/`elevation`
carry a dedup key (`analysis:<lipas-id>`) and a 30-second debounce delay,
so a burst of edits (or a bulk operation) produces one pending job per
site instead of one per save.

Deduplication applies to **pending** jobs only. If a job is already
processing when a new edit arrives, a successor is enqueued — the running
execution may have read pre-edit data, so suppressing the successor would
lose the update. Concurrency is handled with a partial unique index +
`ON CONFLICT DO NOTHING`; a losing `enqueue-job!` returns `nil`.

## Timeouts and stuck jobs

Handlers run directly on worker pool threads. A single watchdog thread
interrupts a pool thread when its job exceeds the registry timeout. The
interrupt flag is cleared (under a lock) before the job's status is
updated, so a timed-out job is failed exactly once and no orphaned
execution races its own retry.

Crash recovery: jobs stuck in `processing` longer than
`lipas.jobs.core/stuck-job-timeout-minutes` (longest registered timeout
+ 30 min margin) are recovered on worker startup and every 10 minutes by
the scheduler — back to `pending` if attempts remain, otherwise to the
dead letter queue.

## Reliability patterns (`lipas.jobs.patterns`)

- **Exponential backoff with jitter** for retries.
- **In-memory circuit breaker** protecting the MML elevation API: after 5
  consecutive failures, elevation jobs fail fast (and retry via the normal
  retry machinery) for 2 minutes instead of hammering a down service.
  State is per-JVM, which is sufficient for the single worker process.
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
