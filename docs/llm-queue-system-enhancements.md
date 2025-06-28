# LIPAS Queue System Enhancement Plan
*Adding enterprise-grade reliability patterns to the PostgreSQL-based unified job queue*

## Overview

This document outlines enhancements to the existing unified job queue system, adding battle-tested reliability patterns while maintaining operational simplicity. All features are implemented using PostgreSQL without introducing new dependencies.

## Current State

We have implemented:
- ✅ Unified jobs table replacing 5 separate queues
- ✅ Fast lane/general lane worker architecture
- ✅ Basic job processing with Integrant integration
- ✅ Database-based aggregation support (Phase 2 approach)

## Enhancement Goals

Add production-grade reliability features:
- Exponential backoff with jitter
- Circuit breakers for external services
- Dead letter queue for failed jobs
- Correlation IDs for request tracing
- Comprehensive monitoring and alerting
- Graceful shutdown and timeouts

## Database Schema Enhancements

### 1. Extend Jobs Table

```sql
-- File: webapp/resources/migrations/20250129000000-jobs-reliability-enhancements.up.sql

-- Add retry and tracing columns
ALTER TABLE public.jobs
  ADD COLUMN IF NOT EXISTS last_error text,
  ADD COLUMN IF NOT EXISTS last_error_at timestamp with time zone,
  ADD COLUMN IF NOT EXISTS correlation_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  ADD COLUMN IF NOT EXISTS parent_job_id bigint REFERENCES jobs(id),
  ADD COLUMN IF NOT EXISTS created_by text,
  ADD COLUMN IF NOT EXISTS dedup_key text;

-- Add missing status value
ALTER TABLE public.jobs
  DROP CONSTRAINT IF EXISTS jobs_status_check;

ALTER TABLE public.jobs
  ADD CONSTRAINT jobs_status_check
  CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'dead'));

-- Indexes for new functionality
CREATE INDEX IF NOT EXISTS idx_jobs_correlation ON public.jobs (correlation_id);
CREATE INDEX IF NOT EXISTS idx_jobs_dedup ON public.jobs (type, dedup_key)
  WHERE dedup_key IS NOT NULL AND status IN ('pending', 'processing');
```

### 2. Add Reliability Tables

```sql
-- Dead letter queue
CREATE TABLE IF NOT EXISTS public.dead_letter_jobs (
  id                bigserial PRIMARY KEY,
  original_job      jsonb NOT NULL,
  error_message     text NOT NULL,
  error_details     jsonb,
  died_at           timestamp with time zone NOT NULL DEFAULT now(),
  acknowledged      boolean DEFAULT false,
  acknowledged_by   text,
  acknowledged_at   timestamp with time zone
);

CREATE INDEX idx_dead_letter_unack ON public.dead_letter_jobs (died_at)
  WHERE acknowledged = false;

-- Circuit breaker state
CREATE TABLE IF NOT EXISTS public.circuit_breakers (
  service_name      text PRIMARY KEY,
  state             text NOT NULL DEFAULT 'closed',
  failure_count     integer NOT NULL DEFAULT 0,
  success_count     integer NOT NULL DEFAULT 0,
  last_failure_at   timestamp with time zone,
  opened_at         timestamp with time zone,
  half_opened_at    timestamp with time zone,
  updated_at        timestamp with time zone NOT NULL DEFAULT now(),

  CONSTRAINT circuit_state_check CHECK (state IN ('closed', 'open', 'half_open'))
);

-- Job metrics for monitoring
CREATE TABLE IF NOT EXISTS public.job_metrics (
  id                bigserial PRIMARY KEY,
  job_type          text NOT NULL,
  status            text NOT NULL,
  duration_ms       bigint,
  queue_time_ms     bigint,
  recorded_at       timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_metrics_type_time ON public.job_metrics (job_type, recorded_at);

-- Update trigger for circuit breakers
CREATE TRIGGER update_circuit_breakers_updated_at
  BEFORE UPDATE ON public.circuit_breakers
  FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Monitoring view
CREATE OR REPLACE VIEW job_queue_health AS
SELECT
  type,
  status,
  COUNT(*) as count,
  MIN(created_at) as oldest,
  MAX(attempts) as max_attempts,
  AVG(EXTRACT(EPOCH FROM (now() - created_at))) as avg_age_seconds
FROM jobs
WHERE status IN ('pending', 'processing', 'failed')
GROUP BY type, status;
```

## Implementation Plan

### Phase 1: Core Reliability Patterns

#### 1.1 Create Patterns Library

**New file: `src/clj/lipas/jobs/patterns.clj`**

This namespace implements:
- Exponential backoff with jitter
- Timeout wrapper
- Retry logic
- Circuit breaker pattern

Key functions:
- `exponential-backoff-ms` - Calculate retry delays
- `with-timeout` - Execute with timeout protection
- `with-retry` - Automatic retry with backoff
- `with-circuit-breaker` - Circuit breaker for external services

#### 1.2 Enhance Core API

**Update: `src/clj/lipas/jobs/core.clj`**

Enhancements:
- Add correlation ID support to `enqueue-job!`
- Implement `fail-job!` with retry scheduling
- Add `move-to-dead-letter!` for permanent failures
- Support deduplication keys

#### 1.3 Update Database Functions

**Update: `src/clj/lipas/jobs/db.clj`**

New HugSQL queries:
```sql
-- :name update-job-retry! :! :n
-- :name insert-dead-letter! :! :n
-- :name get-circuit-breaker :? :1
-- :name update-circuit-breaker! :! :n
-- :name record-job-metric! :! :n
```

### Phase 2: Enhanced Worker Implementation

#### 2.1 Update Worker with Patterns

**Update: `src/clj/lipas/jobs/worker.clj`**

Key changes:
- Add timeout protection based on job type
- Implement graceful shutdown
- Add correlation ID to log entries
- Handle failures with retry logic
- Record metrics for monitoring

#### 2.2 Update Job Handlers

**Update: `src/clj/lipas/jobs/dispatcher.clj`**

For each job handler, wrap external calls with circuit breakers:

```clojure
;; Example for email handler
(defmethod handle-job "email"
  [{:keys [db emailer]} {:keys [id payload correlation-id]}]
  (patterns/with-circuit-breaker db "email-service" {}
    (email/send-email! emailer payload)))
```

### Phase 3: Monitoring Without External Dependencies

#### 3.1 Database-Based Monitoring

**New file: `src/clj/lipas/jobs/monitoring.clj`**

Features:
- Record job metrics to database
- Health check endpoint
- Alert checking
- Dashboard data aggregation
- No Prometheus dependency

#### 3.2 Admin Dashboard (Optional)

**New file: `src/clj/lipas/backend/handlers/admin.clj`**

Simple Hiccup-based dashboard showing:
- Queue depths
- Recent failures
- Circuit breaker status
- Dead letter queue

#### 3.3 Automated Alerts

Add scheduled job for monitoring:
```clojure
{:monitor-queue-health
 {:job-type "monitor-queue-health"
  :payload {}
  :interval-seconds 300}}
```

### Phase 4: Testing

#### 4.1 Unit Tests

**New file: `test/clj/lipas/jobs/patterns_test.clj`**

Test coverage for:
- Exponential backoff calculations
- Timeout behavior
- Circuit breaker state transitions
- Retry logic

#### 4.2 Integration Tests

**Update: `test/clj/lipas/jobs/integration_test.clj`**

New test scenarios:
- Job failure and retry
- Dead letter queue
- Circuit breaker integration
- Correlation ID propagation

### Phase 5: Deployment

#### 5.1 Migration Steps

1. **Deploy schema changes** - Run new migrations
2. **Deploy code** - Worker continues processing normally
3. **Monitor** - Watch for any issues
4. **Enable features** - Circuit breakers start in 'closed' state

#### 5.2 Configuration

Update worker configuration:
```clojure
{:lipas.jobs/worker
 {:fast-threads 3
  :general-threads 5
  :batch-size 20
  :poll-interval-ms 1000}

 :lipas.jobs/retry
 {:base-ms 1000
  :max-ms 300000
  :jitter 0.1}

 :lipas.jobs/circuit-breaker
 {:failure-threshold 5
  :open-duration-ms 60000}}
```

## Operational Procedures

### Monitoring Queries

```sql
-- Check queue health
SELECT * FROM job_queue_health ORDER BY type, status;

-- Find stuck jobs
SELECT id, type, status, attempts, created_at
FROM jobs
WHERE status = 'processing'
  AND started_at < NOW() - INTERVAL '1 hour';

-- Review dead letters
SELECT * FROM dead_letter_jobs
WHERE acknowledged = false
ORDER BY died_at DESC;

-- Check circuit breakers
SELECT * FROM circuit_breakers
WHERE state != 'closed';
```

### Common Operations

#### Acknowledge Dead Letter
```clojure
(db/acknowledge-dead-letter! db
  {:id dead-letter-id
   :acknowledged_by "admin@lipas.fi"})
```

#### Reset Circuit Breaker
```clojure
(db/update-circuit-breaker! db
  {:service_name "email-service"
   :state "closed"
   :failure_count 0})
```

#### Requeue Dead Job
```clojure
(let [dead-letter (db/get-dead-letter db dead-letter-id)
      original-job (:original_job dead-letter)]
  (jobs/enqueue-job! db
                     (:type original-job)
                     (:payload original-job)
                     {:correlation-id (:correlation_id original-job)}))
```

## Benefits

1. **Reliability**: Automatic retries, circuit breakers prevent cascade failures
2. **Observability**: Full visibility into job processing and failures
3. **Debuggability**: Correlation IDs trace requests across jobs
4. **Maintainability**: All patterns in one place, well-tested
5. **Simplicity**: No external dependencies, just PostgreSQL

## Future Enhancements

Once these patterns are stable, consider:

1. **Job Chaining**: Explicit workflows (job A → job B → job C)
2. **Rate Limiting**: Prevent overwhelming external services
3. **Priority Lanes**: More than just fast/slow
4. **Job Versioning**: Handle schema evolution
5. **Batch Operations**: Process multiple items in single job

## Success Metrics

After implementation, we should see:
- ❌ → ✅ No data loss from failures
- ❌ → ✅ No manual intervention for transient failures
- ❌ → ✅ Clear visibility into system health
- ❌ → ✅ Predictable retry behavior
- ❌ → ✅ Protected external services

## Conclusion

These enhancements transform the basic queue into a production-grade system suitable for a critical government service. The PostgreSQL-only approach maintains simplicity while providing enterprise-level reliability.

Most importantly for a sole maintainer: **it should just work**, recovering automatically from common failures and clearly reporting when manual intervention is needed.
