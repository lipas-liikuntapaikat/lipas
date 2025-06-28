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
│ • integration   │                    │ • elevation     │
│ • cleanup       │                    │ • webhooks      │
│ • webhooks      │                    │                 │
└─────────────────┘                    └─────────────────┘
```

### Key Features

- **Smart Concurrency**: Fast jobs (< 30 seconds) get dedicated threads, preventing blocking by slow operations
- **Reliability Patterns**: Exponential backoff, circuit breakers, dead letter queue, timeouts
- **Multi-Process Safe**: Uses PostgreSQL `SELECT FOR UPDATE SKIP LOCKED` for coordination
- **Request Tracing**: Correlation IDs for debugging and monitoring
- **Zero Downtime**: Graceful shutdown and rolling deployments

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
  recorded_at       timestamp with time zone NOT NULL DEFAULT now()
);
```

## Job Types

The system processes several types of background jobs:

### Fast Jobs (< 30 seconds)
- **`email`** - Send email notifications and alerts
- **`produce-reminders`** - Generate reminder tasks for overdue items
- **`cleanup-jobs`** - Remove old completed jobs and maintain system hygiene
- **`integration`** - Sync data with external systems
- **`webhook`** - Process UTP webhook notifications

### Slow Jobs (minutes to hours)
- **`analysis`** - Calculate diversity analysis for sports sites
- **`elevation`** - Enrich location data with elevation information

### Job Type Payload Schemas

See [webapp/src/clj/lipas/jobs/payload_schema.clj](webapp/src/clj/lipas/jobs/payload_schema.clj)

## Core API

### Enqueueing Jobs

```clojure
(require '[lipas.jobs.core :as jobs])

;; Basic job
(jobs/enqueue-job! db "email" {:to "user@example.com" :template "welcome"})

;; With options
(jobs/enqueue-job! db "analysis"
                   {:lipas-id 12345}
                   {:priority 50
                    :run-at (time/plus (time/now) (time/minutes 5))
                    :correlation-id "user-action-123"
                    :dedup-key "analysis-12345"})
```

**Options:**
- `:priority` - Lower numbers = higher priority (default: 100)
- `:run-at` - Schedule job for future execution
- `:correlation-id` - For request tracing (auto-generated if not provided)
- `:dedup-key` - Prevent duplicate jobs (combined with job type)
- `:max-attempts` - Override default retry limit

### Job Management

```clojure
;; Get queue statistics
(jobs/get-queue-stats db)
;; => {:pending 42 :processing 3 :failed 1 :oldest-pending-minutes 5}

;; Find jobs by correlation ID
(jobs/get-jobs-by-correlation db "user-action-123")

;; Manually fail a job
(jobs/fail-job! db job-id "External service unavailable" {:will-retry? true})

;; Move to dead letter
(jobs/move-to-dead-letter! db job-id "Permanent validation error")
```

## Job Handlers

### Creating Handlers

Job handlers are implemented as multimethods in `lipas.jobs.dispatcher`:

```clojure
(defmethod handle-job "email"
  [{:keys [db emailer]} {:keys [id payload correlation-id]}]
  (let [{:keys [to template data]} payload]
    (log/info "Sending email" {:correlation-id correlation-id :template template})
    (patterns/with-circuit-breaker db "email-service" {}
      (email/send-templated-email! emailer to template data))))

(defmethod handle-job "analysis"
  [{:keys [db search]} {:keys [id payload correlation-id]}]
  (let [{:keys [lipas-id]} payload
        sports-site (core/get-sports-site db lipas-id)
        fcoll (-> sports-site :location :geometries core/simplify)]
    (log/info "Starting diversity analysis" {:correlation-id correlation-id :lipas-id lipas-id})
    (patterns/with-timeout 600000  ; 10 minutes
      (diversity/recalc-grid! search fcoll))))
```

### Handler Context

Job handlers receive:
- `:db` - Database connection
- `:search` - Elasticsearch client
- `:emailer` - Email service
- Job data: `:id`, `:payload`, `:correlation-id`, `:attempts`

### Error Handling

Handlers should throw exceptions for failures. The worker automatically:
- Retries with exponential backoff
- Records error details
- Moves to dead letter after max attempts
- Triggers circuit breakers for external service failures

## Reliability Patterns

### Exponential Backoff

Failed jobs are retried with increasing delays:
- Attempt 1: 1 second
- Attempt 2: ~2 seconds (with jitter)
- Attempt 3: ~4 seconds (with jitter)
- Max delay: 5 minutes

```clojure
(patterns/exponential-backoff-ms 1000 3 0.1)  ; Base 1s, attempt 3, 10% jitter
;; => ~4100ms
```

### Circuit Breakers

External services are protected by circuit breakers that prevent cascade failures:

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

Long-running jobs can be protected by timeouts:

```clojure
(patterns/with-timeout 300000  ; 5 minutes
  (elevation/enrich-sports-site! elevation-service payload))
```

### Dead Letter Queue

Permanently failed jobs are moved to the dead letter queue for manual review:

```sql
SELECT * FROM dead_letter_jobs
WHERE acknowledged = false
ORDER BY died_at DESC;
```

## Configuration

See [webapp/src/clj/lipas/jobs/system.clj](webapp/src/clj/lipas/jobs/system.clj)

### Development

```clojure
{:lipas.jobs/worker
 {:enabled-job-types #{"email" "analysis" "elevation"}
  :fast-threads 1
  :general-threads 2
  :batch-size 5
  :poll-interval-ms 2000}

 :lipas.jobs/retry
 {:base-ms 1000
  :max-ms 60000
  :jitter 0.1}

 :lipas.jobs/circuit-breaker
 {:failure-threshold 3
  :open-duration-ms 30000}}
```

### Production

```clojure
{:lipas.jobs/worker
 {:enabled-job-types #{"produce-reminders" "cleanup-jobs" "analysis"
                       "elevation" "email" "integration" "webhook"}
  :fast-threads 3              ; Dedicated for fast jobs
  :general-threads 5           ; All job types including slow ones
  :batch-size 20               ; Fetch more jobs per database round-trip
  :poll-interval-ms 1000       ; Poll every second
  :fast-timeout-minutes 2      ; Fast job timeout
  :slow-timeout-minutes 20}    ; Slow job timeout

 :lipas.jobs/retry
 {:base-ms 1000
  :max-ms 300000               ; Max 5 minute delay
  :jitter 0.1}

 :lipas.jobs/circuit-breaker
 {:failure-threshold 5
  :open-duration-ms 60000}}    ; 1 minute open state
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
    # ... web app configuration

  lipas-worker:
    image: lipas:latest
    command: ["java", "-jar", "backend.jar", "worker"]
    environment:
      - WORKER_FAST_THREADS=3
      - WORKER_GENERAL_THREADS=5
```

### Integrant Systems

**Worker System (`src/clj/lipas/jobs/system.clj`):**
```clojure
(def worker-system-config
  {:lipas/db {...}
   :lipas/search {...}
   :lipas/emailer {...}

   :lipas.jobs/scheduler     ; Produces periodic jobs
   {:db (ig/ref :lipas/db)}

   :lipas.jobs/worker        ; Processes all job types
   {:db (ig/ref :lipas/db)
    :search (ig/ref :lipas/search)
    :emailer (ig/ref :lipas/emailer)
    :config {:fast-threads 3 :general-threads 5}}})
```

## Monitoring and Operations

### Health Monitoring

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
```

### Circuit Breaker Status

```sql
-- Check external service health
SELECT service_name, state, failure_count, last_failure_at
FROM circuit_breakers
WHERE state != 'closed';
```

### Key Metrics

Monitor these indicators:
- **Queue depth** - Jobs pending by type
- **Processing time** - P50, P95, P99 by job type
- **Error rate** - Failed jobs percentage by type
- **Thread utilization** - Fast vs general lane usage
- **Circuit breaker trips** - External service failures

### Alerts

Set up monitoring for:
- Pending jobs > 500 for > 5 minutes
- Any job type error rate > 10%
- Fast lane utilization > 90% for > 2 minutes
- Oldest pending job > 1 hour
- Circuit breaker in open state

## Operational Procedures

### Handling Failed Jobs

#### View Dead Letter Queue
```sql
SELECT id, original_job->>'type' as job_type, error_message, died_at
FROM dead_letter_jobs
WHERE acknowledged = false
ORDER BY died_at DESC;
```

#### Acknowledge Dead Letter
```clojure
(db/acknowledge-dead-letter! db
  {:id 123
   :acknowledged_by "admin@lipas.fi"})
```

#### Requeue Dead Job
```clojure
(let [dead-letter (db/get-dead-letter db 123)
      original-job (:original_job dead-letter)]
  (jobs/enqueue-job! db
                     (:type original-job)
                     (:payload original-job)
                     {:correlation-id (:correlation_id original-job)}))
```

### Circuit Breaker Management

#### Reset Circuit Breaker
```clojure
(db/reset-circuit-breaker! db "email-service")
```

#### Force Circuit Open
```clojure
(db/update-circuit-breaker! db
  {:service_name "external-api"
   :state "open"
   :opened_at (time/now)})
```

### Emergency Procedures

#### Stop All Job Processing
```bash
# Graceful shutdown - completes current jobs
docker-compose stop lipas-worker

# Force stop if needed
docker-compose kill lipas-worker
```

#### Drain Queue Before Maintenance
```clojure
;; Stop accepting new jobs of certain types
(db/update-jobs! db
  {:status "pending"}
  {:status "paused"})

;; Wait for processing jobs to complete
(loop []
  (let [processing (db/count-processing-jobs db)]
    (if (> processing 0)
      (do (Thread/sleep 5000)
          (recur))
      (log/info "All jobs completed, safe for maintenance"))))
```

## Troubleshooting

### Common Issues

#### Jobs Stuck in Processing
**Symptom:** Jobs show `processing` status but don't complete
**Cause:** Worker crashed or job timed out
**Solution:**
```sql
-- Reset stuck jobs to pending
UPDATE jobs
SET status = 'pending', started_at = null
WHERE status = 'processing'
  AND started_at < NOW() - INTERVAL '1 hour';
```

#### High Error Rate
**Symptom:** Many jobs failing repeatedly
**Cause:** External service down or configuration issue
**Solution:**
1. Check circuit breaker status
2. Verify external service connectivity
3. Review error messages in dead letter queue

#### Queue Backing Up
**Symptom:** Pending job count increasing
**Cause:** Jobs taking longer than expected or insufficient workers
**Solution:**
1. Check for slow jobs blocking fast lane
2. Increase worker threads if needed
3. Optimize slow job handlers

#### Memory Issues
**Symptom:** Worker process consuming excessive memory
**Cause:** Large payloads or memory leaks
**Solution:**
1. Monitor job payload sizes
2. Restart worker process
3. Add heap dump analysis

### Debugging with Correlation IDs

```sql
-- Trace a user action across multiple jobs
SELECT id, type, status, created_at, completed_at, error_message
FROM jobs
WHERE correlation_id = 'user-action-123'
ORDER BY created_at;

-- Find related jobs
SELECT j1.id as parent_id, j1.type as parent_type,
       j2.id as child_id, j2.type as child_type
FROM jobs j1
JOIN jobs j2 ON j1.id = j2.parent_job_id
WHERE j1.correlation_id = 'user-action-123';
```

## Future Enhancements

Potential improvements to consider:

1. **Job Chaining** - Explicit workflows (job A → job B → job C)
2. **Rate Limiting** - Prevent overwhelming external services
3. **Priority Lanes** - More granular than just fast/slow
4. **Job Versioning** - Handle payload schema evolution
5. **Batch Operations** - Process multiple items in single job
6. **Job Scheduling** - Cron-like recurring jobs
7. **Workflow Engine** - Complex multi-step processes

## Migration History

The async jobs system replaced five separate queue tables:
- `analysis_queue` - Diversity analysis calculations
- `elevation_queue` - Elevation data enrichment
- `email_out_queue` - Email notifications
- `integration_out_queue` - Legacy system sync
- `webhook_queue` - UTP webhook processing

Benefits realized:
- **Simplified Architecture** - One system instead of five
- **Better Resource Management** - Smart concurrency prevents blocking
- **Improved Reliability** - Consistent retry and error handling
- **Enhanced Observability** - Unified monitoring and metrics
- **Easier Maintenance** - Single codebase for all background processing

## Getting Started

### For Developers

1. **Understand the Architecture** - Review this documentation and existing code
2. **Run Tests** - Execute `bb test` to validate functionality
3. **Create Feature Branch** - `git checkout -b feature/new-job-type`
4. **Add Job Handler** - Implement in `lipas.jobs.dispatcher`
5. **Test Incrementally** - Add unit and integration tests
6. **Deploy and Monitor** - Watch metrics after deployment

### Adding New Job Types

1. **Define Handler**
```clojure
(defmethod handle-job "new-job-type"
  [{:keys [db]} {:keys [payload correlation-id]}]
  (log/info "Processing new job" {:correlation-id correlation-id})
  ;; Implementation here
  )
```

2. **Configure Duration**
```clojure
;; In lipas.jobs.worker
(def job-duration-types
  {:fast #{"email" "new-fast-job"}
   :slow #{"analysis" "new-slow-job"}})
```

3. **Add to Configuration**
```clojure
{:enabled-job-types #{"existing-jobs" "new-job-type"}}
```

4. **Test and Deploy**

The async jobs system provides a robust foundation for all background processing needs in LIPAS, ensuring reliable operation while maintaining operational simplicity.
