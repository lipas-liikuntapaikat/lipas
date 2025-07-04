# LIPAS Async Jobs System Documentation

The LIPAS async jobs system provides a unified, reliable background job processing capability that replaced five separate queue systems with a single PostgreSQL-based solution featuring smart concurrency control and enterprise-grade reliability patterns.

## System Overview

### Architecture

The jobs system implements a **Fast Lane + General Lane** architecture that prevents head-of-line blocking by reserving threads for quick operations while allowing slow jobs to run without impacting system responsiveness.

```
┌─────────────────────────────────────────────────────────────┐
│                    UNIFIED JOBS TABLE                       │
│  ┌─────────────┬─────────────┬──────────────┬─────────────┐ │
│  │ id          │ type        │ payload      │ status      │ │
│  │ priority    │ run_at      │ attempts     │ created_at  │ │
│  └─────────────┴─────────────┴──────────────┴─────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    SMART DISPATCHER                         │
│              Routes jobs by type to handlers                │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────┐                    ┌─────────────────┐
│   FAST LANE     │                    │  GENERAL LANE   │
│   (3 threads)   │                    │   (5 threads)   │
│                 │                    │                 │
│ • emails        │                    │ • any job type  │
│ • reminders     │                    │ • analysis      │
│ • cleanup       │                    │ • elevation     │
│ • webhooks      │                    │                 │
│ • monitoring    │                    │                 │
└─────────────────┘                    └─────────────────┘
```

### Key Features

- **Smart Concurrency**: Fast jobs (< 30 seconds) get dedicated threads, preventing blocking by slow operations
- **Reliability Patterns**: Exponential backoff, circuit breakers, dead letter queue, timeouts
- **Multi-Process Safe**: Uses PostgreSQL `SELECT FOR UPDATE SKIP LOCKED` for coordination
- **Request Tracing**: Correlation IDs for debugging and monitoring
- **Zero Downtime**: Graceful shutdown and rolling deployments
- **Deduplication**: Prevents duplicate jobs using unique keys
- **Job Chaining**: Create related jobs with shared correlation IDs

## Database Schema

### Jobs Table

The core `jobs` table stores all background tasks:

```sql
CREATE TABLE public.jobs (
  id             bigserial PRIMARY KEY,
  type           text NOT NULL,
  payload        jsonb NOT NULL DEFAULT '{}',
  status         text NOT NULL DEFAULT 'pending',
  priority       integer NOT NULL DEFAULT 100,
  attempts       integer NOT NULL DEFAULT 0,
  max_attempts   integer NOT NULL DEFAULT 3,
  scheduled_at   timestamp with time zone NOT NULL DEFAULT now(),
  run_at         timestamp with time zone NOT NULL DEFAULT now(),
  started_at     timestamp with time zone,
  completed_at   timestamp with time zone,
  error_message  text,
  last_error     text,
  last_error_at  timestamp with time zone,
  correlation_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  parent_job_id  bigint REFERENCES jobs(id),
  created_by     text,
  dedup_key      text,
  created_at     timestamp with time zone NOT NULL DEFAULT now(),
  updated_at     timestamp with time zone NOT NULL DEFAULT now(),

  CONSTRAINT jobs_status_check CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'dead')),
  CONSTRAINT jobs_priority_check CHECK (priority >= 0)
);

-- Deduplication index
CREATE UNIQUE INDEX idx_jobs_dedup_unique ON public.jobs (type, dedup_key)
  WHERE dedup_key IS NOT NULL AND status IN ('pending', 'processing');
```

**Status Values:**
- `pending` - Job is queued and ready to process
- `processing` - Job is currently being executed
- `completed` - Job finished successfully
- `failed` - Job failed but will be retried
- `dead` - Job failed permanently and moved to dead letter queue

### Supporting Tables

```sql
-- Dead letter queue for permanently failed jobs
CREATE TABLE public.dead_letter_jobs (
  id                bigserial PRIMARY KEY,
  original_job      jsonb NOT NULL,
  error_message     text NOT NULL,
  error_details     jsonb,
  correlation_id    uuid,
  died_at           timestamp with time zone NOT NULL DEFAULT now(),
  acknowledged      boolean DEFAULT false,
  acknowledged_by   text,
  acknowledged_at   timestamp with time zone
);

-- Circuit breaker state for external services
CREATE TABLE public.circuit_breakers (
  service_name      text PRIMARY KEY,
  state             text NOT NULL DEFAULT 'closed',
  failure_count     integer NOT NULL DEFAULT 0,
  success_count     integer NOT NULL DEFAULT 0,
  last_failure_at   timestamp with time zone,
  opened_at         timestamp with time zone,
  half_opened_at    timestamp with time zone,
  updated_at        timestamp with time zone NOT NULL DEFAULT now()
);

-- Job execution metrics
CREATE TABLE public.job_metrics (
  id                bigserial PRIMARY KEY,
  job_type          text NOT NULL,
  status            text NOT NULL,
  duration_ms       bigint,
  queue_time_ms     bigint,
  correlation_id    uuid,
  recorded_at       timestamp with time zone NOT NULL DEFAULT now()
);
```

## Job Types

The system processes several types of background jobs:

### Fast Jobs (< 30 seconds)
- **`email`** - Send email notifications and alerts
- **`produce-reminders`** - Generate reminder tasks for overdue items
- **`cleanup-jobs`** - Remove old completed jobs and maintain system hygiene
- **`webhook`** - Process UTP webhook notifications
- **`monitor-queue-health`** - Monitor queue health and send alerts

### Slow Jobs (minutes to hours)
- **`analysis`** - Calculate diversity analysis for sports sites
- **`elevation`** - Enrich location data with elevation information

Note: The `integration` job type for legacy system sync has been deprecated and is no longer implemented.

### Job Type Payload Schemas

All job payloads are validated using Malli schemas. See `webapp/src/clj/lipas/jobs/payload_schema.clj` for detailed schemas. Example payloads:

```clojure
;; Email job
{:to "user@example.com"
 :subject "Welcome!"
 :body "Your account is ready"
 :type "notification"}  ; Optional: "reminder" for special handling

;; Analysis job
{:lipas-id 12345}

;; Elevation job
{:lipas-id 12345}

;; Webhook job
{:lipas-ids [1234, 5678]
 :loi-ids [91011, 121314]
 :operation-type "update"}

;; Cleanup job
{:days-old 7}  ; Optional, defaults to 7

;; Monitor queue health job
{}  ; No payload required
```

## Core API

### Enqueueing Jobs

```clojure
(require '[lipas.jobs.core :as jobs])

;; Basic job
(jobs/enqueue-job! db "email" {:to "user@example.com" :subject "Welcome"})

;; With options
(jobs/enqueue-job! db "analysis"
                   {:lipas-id 12345}
                   {:priority 50
                    :run-at (time/plus (time/now) (time/minutes 5))
                    :correlation-id "user-action-123"
                    :dedup-key "analysis-12345"
                    :max-attempts 5})
```

**Options:**
- `:priority` - Lower numbers = higher priority (default: 100)
- `:run-at` - Schedule job for future execution
- `:correlation-id` - For request tracing (auto-generated if not provided)
- `:dedup-key` - Prevent duplicate jobs (combined with job type)
- `:max-attempts` - Override default retry limit
- `:parent-job-id` - Link to parent job
- `:created-by` - Track who created the job

### Job Management

```clojure
;; Get queue statistics
(jobs/get-queue-stats db)
;; => {:pending {:count 42 :oldest "2024-01-01T10:00:00Z"}
;;     :processing {:count 3}
;;     :failed {:count 1}}

;; Get admin metrics dashboard data
(jobs/get-admin-metrics db {:from-hours-ago 24})
;; => {:current-stats {...}
;;     :health {...}
;;     :performance-metrics [...]
;;     :hourly-throughput [...]}

;; Find jobs by correlation ID
(jobs/get-jobs-by-correlation db "user-action-123")

;; Get complete correlation trace (jobs + metrics)
(jobs/get-correlation-trace db "user-action-123")

;; Manually fail a job (used internally by workers)
(jobs/fail-job! db job-id "External service unavailable" 
                {:current-attempt 2 :max-attempts 3})

;; Create job chain with shared correlation
(jobs/create-job-chain db 
  [{:type "email" :payload {:to "user@example.com"}}
   {:type "analysis" :payload {:lipas-id 123}}])
```

## Job Handlers

### Creating Handlers

Job handlers are implemented as multimethods in `lipas.jobs.dispatcher`:

```clojure
(defmethod handle-job "new-job-type"
  [{:keys [db emailer search]} {:keys [id payload correlation-id]}]
  (log/info "Processing new job" {:correlation-id correlation-id})
  
  ;; Use circuit breaker for external services
  (patterns/with-circuit-breaker db "external-service" {}
    (external-api/call! payload))
  
  ;; Use timeout for long operations
  (patterns/with-timeout 300000  ; 5 minutes
    (process-data! payload)))
```

### Handler Context

Job handlers receive:
- System components: `:db`, `:search`, `:emailer`
- Job data: `:id`, `:type`, `:payload`, `:correlation-id`, `:attempts`

### Error Handling

Handlers should throw exceptions for failures. The worker automatically:
- Retries with exponential backoff (with jitter)
- Records error details
- Moves to dead letter after max attempts
- Triggers circuit breakers for external service failures
- Records metrics for monitoring

## Reliability Patterns

### Exponential Backoff

Failed jobs are retried with increasing delays:
- Attempt 1: 1 second base (with jitter)
- Attempt 2: ~2 seconds (with jitter)
- Attempt 3: ~4 seconds (with jitter)
- Max delay: 5 minutes

```clojure
(patterns/exponential-backoff-ms 1 {:base-ms 1000 :max-ms 300000 :jitter 0.1})
;; => ~2200ms (2s base + 10% jitter)
```

### Circuit Breakers

External services are protected by circuit breakers:

```clojure
(patterns/with-circuit-breaker db "email-service"
  {:failure-threshold 5
   :open-duration-ms 60000}
  (email/send-email! emailer payload))
```

**States:**
- **Closed** - Normal operation, requests flow through
- **Open** - Service failing, requests fail fast
- **Half-Open** - Testing if service recovered

### Timeouts

Long-running jobs are protected by timeouts:

```clojure
(patterns/with-timeout 300000  ; 5 minutes
  (elevation/enrich-sports-site! elevation-service payload))
```

Timeouts throw `TimeoutException` which triggers normal retry logic.

### Dead Letter Queue

Permanently failed jobs are moved to dead letter queue for manual review:

```clojure
;; Get unacknowledged dead letter jobs
(jobs/get-dead-letter-jobs db {:acknowledged false})

;; Reprocess dead letter job
(jobs/reprocess-dead-letter-job! db dead-letter-id "admin@lipas.fi"
                                 {:max-attempts 3})

;; Bulk operations
(jobs/reprocess-dead-letter-jobs! db [123 456 789] "admin@lipas.fi")
(jobs/acknowledge-dead-letter-jobs! db [123 456] "admin@lipas.fi")
```

## Configuration

### Worker Configuration

The worker system uses environment variables for configuration:

```clojure
;; Default configuration (can be overridden via environment)
{:fast-threads 3                    ; WORKER_FAST_THREADS
 :general-threads 5                 ; WORKER_GENERAL_THREADS
 :batch-size 10                     ; WORKER_BATCH_SIZE
 :poll-interval-ms 3000             ; WORKER_POLL_INTERVAL_MS
 :fast-timeout-minutes 2            ; WORKER_FAST_TIMEOUT_MINUTES
 :slow-timeout-minutes 20           ; WORKER_SLOW_TIMEOUT_MINUTES
 :stuck-job-timeout-minutes 60      ; WORKER_STUCK_JOB_TIMEOUT_MINUTES
 
 ;; Memory management
 :memory-check-interval-ms 60000    ; WORKER_MEMORY_CHECK_INTERVAL_MS
 :memory-threshold-percent 85       ; WORKER_MEMORY_THRESHOLD_PERCENT
 
 ;; Per-job-type timeout overrides
 :job-type-timeouts {"analysis" 30  ; WORKER_ANALYSIS_TIMEOUT_MINUTES
                     "elevation" 10 ; WORKER_ELEVATION_TIMEOUT_MINUTES
                     "email" 1}}    ; WORKER_EMAIL_TIMEOUT_MINUTES
```

### Scheduler Configuration

The scheduler produces periodic jobs:

```clojure
{:produce-reminders {:cron "0 0 8 * * ?"      ; Daily at 8 AM
                     :initial-delay-ms 60000}   ; 1 minute startup delay
 :cleanup-jobs {:interval-ms 86400000          ; Daily
                :initial-delay-ms 300000}}     ; 5 minute startup delay
```

## Deployment Architecture

### Process Separation

The system runs as two separate processes:

1. **Web Application** - Handles HTTP requests and enqueues jobs
2. **Worker Process** - Processes background jobs and runs scheduler

### Docker Configuration

```yaml
services:
  backend:
    image: lipas:latest
    command: ["java", "-jar", "backend.jar", "server"]
    environment:
      - DATABASE_URL=postgresql://...
    # ... web app configuration

  lipas-worker:
    image: lipas:latest
    command: ["java", "-jar", "backend.jar", "worker"]
    environment:
      - DATABASE_URL=postgresql://...
      - WORKER_FAST_THREADS=3
      - WORKER_GENERAL_THREADS=5
      - WORKER_BATCH_SIZE=20
      - WORKER_POLL_INTERVAL_MS=1000
```

### Integrant Systems

The worker system uses Integrant for component management:

```clojure
;; Worker system components
{:lipas/db {...}              ; Database connection
 :lipas/search {...}          ; Elasticsearch client
 :lipas/emailer {...}         ; Email service
 
 :lipas.jobs/scheduler        ; Produces periodic jobs
 {:db (ig/ref :lipas/db)}
 
 :lipas.jobs/worker           ; Processes all job types
 {:db (ig/ref :lipas/db)
  :search (ig/ref :lipas/search)
  :emailer (ig/ref :lipas/emailer)
  :config (get-worker-config)}
  
 :lipas.jobs/health-monitor   ; Monitors system health
 {:db (ig/ref :lipas/db)
  :config (get-worker-config)}}
```

## Monitoring and Operations

### Health Monitoring

The system includes automatic health monitoring that checks:
- Queue depth and age
- Processing times
- Error rates
- Memory usage
- Circuit breaker states

```sql
-- Check overall queue health
SELECT * FROM job_queue_health ORDER BY type, status;

-- Find stuck jobs
SELECT id, type, status, attempts, created_at, started_at
FROM jobs
WHERE status = 'processing'
  AND started_at < NOW() - INTERVAL '1 hour';

-- Recent failures by type
SELECT type, COUNT(*) as failures, MAX(last_error_at) as latest_failure
FROM jobs
WHERE status = 'failed'
  AND last_error_at > NOW() - INTERVAL '1 hour'
GROUP BY type;

-- Performance metrics
SELECT job_type, 
       AVG(duration_ms) as avg_duration,
       MAX(duration_ms) as max_duration,
       COUNT(*) as total_jobs
FROM job_metrics
WHERE recorded_at > NOW() - INTERVAL '24 hours'
GROUP BY job_type;
```

### Admin API Endpoints

The system provides REST endpoints for monitoring:

```bash
# Get comprehensive metrics
POST /api/actions/create-jobs-metrics-report
{
  "from-hours-ago": 24,
  "to-hours-ago": 0
}

# Get health status
POST /api/actions/get-jobs-health-status
{}

# Manage dead letter queue
GET /api/actions/get-dead-letter-jobs?acknowledged=false

POST /api/actions/reprocess-dead-letter-jobs
{
  "dead-letter-ids": [123, 456],
  "max-attempts": 3
}

POST /api/actions/acknowledge-dead-letter-jobs
{
  "dead-letter-ids": [123, 456]
}
```

### Key Metrics

Monitor these indicators:
- **Queue depth** - Jobs pending by type
- **Processing time** - P50, P95, P99 by job type
- **Error rate** - Failed jobs percentage by type
- **Thread utilization** - Fast vs general lane usage
- **Circuit breaker trips** - External service failures
- **Memory usage** - Worker process heap utilization

### Alerts

Set up monitoring for:
- Pending jobs > 500 for > 5 minutes
- Any job type error rate > 10%
- Fast lane utilization > 90% for > 2 minutes
- Oldest pending job > 1 hour
- Circuit breaker in open state
- Worker memory usage > 85%

## Operational Procedures

### Handling Failed Jobs

#### View Dead Letter Queue
```sql
SELECT id, 
       original_job->>'type' as job_type,
       original_job->>'correlation_id' as correlation_id,
       error_message, 
       died_at
FROM dead_letter_jobs
WHERE acknowledged = false
ORDER BY died_at DESC;
```

#### Investigate Failure Chain
```sql
-- Trace all related jobs
SELECT * FROM jobs
WHERE correlation_id = (
  SELECT original_job->>'correlation_id'::uuid
  FROM dead_letter_jobs
  WHERE id = 123
)
ORDER BY created_at;
```

### Circuit Breaker Management

```sql
-- Check circuit breaker status
SELECT * FROM circuit_breakers;

-- Reset circuit breaker
UPDATE circuit_breakers
SET state = 'closed', failure_count = 0, success_count = 0
WHERE service_name = 'email-service';
```

### Emergency Procedures

#### Stop All Job Processing
```bash
# Graceful shutdown - completes current jobs
docker-compose stop lipas-worker

# Wait for processing to complete
watch "docker-compose exec db psql -c \"SELECT status, COUNT(*) FROM jobs WHERE status='processing' GROUP BY status\""
```

#### Drain Queue Before Maintenance
```sql
-- Pause new job processing (manual intervention required)
-- Workers will naturally drain the queue

-- Monitor progress
SELECT type, status, COUNT(*) 
FROM jobs 
WHERE status IN ('pending', 'processing')
GROUP BY type, status;
```

#### Handle Memory Issues
```bash
# Check worker memory usage
docker stats lipas-worker

# Restart if needed
docker-compose restart lipas-worker
```

## Troubleshooting

### Common Issues

#### Jobs Stuck in Processing
**Symptom:** Jobs show `processing` status indefinitely
**Cause:** Worker crashed or network partition
**Solution:** Handled automatically on worker restart via `reset-stuck-jobs!`

#### High Error Rate
**Symptom:** Many jobs failing repeatedly
**Solution:**
1. Check circuit breaker status
2. Review dead letter queue for patterns
3. Check external service connectivity
4. Review recent deployments

#### Queue Backing Up
**Symptom:** Pending job count increasing
**Solution:**
1. Check for slow jobs in general lane
2. Review thread pool metrics
3. Scale worker processes if needed
4. Check for circuit breakers in open state

#### Deduplication Not Working
**Symptom:** Duplicate jobs being created
**Solution:**
1. Ensure `dedup-key` is being set
2. Check that previous job completed/failed
3. Review unique index on (type, dedup_key)

### Debugging with Correlation IDs

```sql
-- Full trace of a user action
WITH job_trace AS (
  SELECT 'job' as source, id, type, status, created_at, 
         completed_at, error_message, attempts
  FROM jobs
  WHERE correlation_id = 'user-action-123'
  
  UNION ALL
  
  SELECT 'metric' as source, id, job_type as type, 
         status, recorded_at as created_at,
         NULL as completed_at, NULL as error_message, 
         NULL as attempts
  FROM job_metrics
  WHERE correlation_id = 'user-action-123'
)
SELECT * FROM job_trace ORDER BY created_at;
```

## Best Practices

### Job Design

1. **Keep Payloads Small** - Store references, not large data
2. **Make Jobs Idempotent** - Safe to retry without side effects
3. **Use Correlation IDs** - Track related operations
4. **Set Appropriate Timeouts** - Prevent indefinite hanging
5. **Use Deduplication** - Prevent duplicate work
6. **Handle Partial Failures** - Design for resumability

### Error Handling

1. **Fail Fast** - Don't retry unrecoverable errors
2. **Log Context** - Include correlation ID and relevant data
3. **Use Circuit Breakers** - Protect external services
4. **Monitor Dead Letters** - Review and address root causes

### Performance

1. **Batch Operations** - Process multiple items per job
2. **Use Priority Wisely** - Reserve low priorities for critical work
3. **Monitor Thread Usage** - Balance fast/slow lane allocation
4. **Clean Up Old Jobs** - Prevent table bloat

## Migration History

The unified jobs system replaced five separate queue tables in January 2025:
- `analysis_queue` → `jobs` type='analysis'
- `elevation_queue` → `jobs` type='elevation'
- `email_out_queue` → `jobs` type='email'
- `integration_out_queue` → deprecated (legacy system being retired)
- `webhook_queue` → `jobs` type='webhook'

Migration benefits:
- **Unified Architecture** - Single system for all background work
- **Better Resource Management** - Smart concurrency control
- **Improved Reliability** - Consistent patterns across all job types
- **Enhanced Observability** - Unified monitoring and tracing
- **Easier Maintenance** - One codebase, one set of patterns

## Future Roadmap

Potential enhancements under consideration:

1. **Job Workflows** - DAG-based job orchestration
2. **Rate Limiting** - Per-service request throttling
3. **Priority Lanes** - More granular than fast/slow
4. **Job Versioning** - Handle payload schema migrations
5. **Event Sourcing** - Full job history retention
6. **Distributed Tracing** - OpenTelemetry integration
7. **Auto-scaling** - Dynamic worker pool sizing

## Getting Started

### For Developers

1. **Read This Documentation** - Understand the architecture
2. **Explore Examples** - See `(jobs/example-job-payloads)`
3. **Run Tests** - `lein test :only lipas.jobs.*`
4. **Try REPL Examples** - See correlation and deduplication examples
5. **Monitor Locally** - Watch logs during development

### Adding New Job Types

1. **Define Payload Schema** in `payload_schema.clj`
2. **Classify Duration** in `core.clj` job-duration-types
3. **Implement Handler** in `dispatcher.clj`
4. **Add Tests** for handler and integration
5. **Update Documentation** with examples
6. **Deploy and Monitor** metrics and errors

The LIPAS async jobs system provides a robust, scalable foundation for all background processing needs, ensuring reliable operation at scale while maintaining operational simplicity.
