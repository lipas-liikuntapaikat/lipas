# LIPAS Queue System Refactoring Plan
*A comprehensive implementation guide for migrating from ad-hoc queue tables to a unified job queue system*

## Executive Summary

This plan migrates LIPAS from 5 separate queue tables and processing systems to a unified job queue with smart concurrency control. The solution prevents head-of-line blocking by reserving threads for fast jobs while allowing slow jobs (analysis/elevation) to run without blocking quick operations (emails/reminders).

**Key Innovation**: Fast Lane + General Lane architecture ensures emails never wait for elevation jobs.

## Current State Analysis

### Existing Queue Systems
1. **analysis_queue** - Diversity analysis calculations (slow: minutes)
2. **elevation_queue** - Elevation data enrichment (very slow: several minutes)
3. **email_out_queue** - Email notifications (fast: seconds)
4. **integration_out_queue** - Legacy system sync (fast: seconds)
5. **webhook_queue** - UTP webhook processing (fast: seconds)

### Current Problems
- **Code duplication**: Each queue has its own processing logic
- **Resource contention**: All queues can run simultaneously without coordination
- **No visibility**: Can't see overall queue health or job status
- **Maintenance burden**: 5 different systems to monitor and debug
- **Scaling issues**: Hard to add new job types or adjust processing rates

## Target Architecture

### Unified Job Queue Design
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
│   (2 threads)   │                    │   (4 threads)   │
│                 │                    │                 │
│ • emails        │                    │ • any job type  │
│ • producers     │                    │ • analysis      │
│ • integration   │                    │ • elevation     │
│ • cleanup       │                    │ • webhooks      │
│ • webhooks      │                    │                 │
└─────────────────┘                    └─────────────────┘
```

### Concurrency Model
- **Fast Lane**: 2 threads reserved for jobs < 30 seconds
- **General Lane**: 4 threads for any job type (including slow ones)
- **Database Coordination**: PostgreSQL `SELECT FOR UPDATE SKIP LOCKED`
- **Multi-Process Safe**: Multiple workers can run on different servers

## Implementation Plan

### Phase 1: Foundation
**Goal**: Create new infrastructure alongside existing system. This is important! Try to leave the existing system as untouched as possible.

#### 1.1 Database Schema
Create unified jobs table:

```sql
-- File: webapp/resources/migrations/20250107000000-unified-jobs-table.up.sql
CREATE TABLE IF NOT EXISTS public.jobs (
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
  created_at     timestamp with time zone NOT NULL DEFAULT now(),
  updated_at     timestamp with time zone NOT NULL DEFAULT now(),

  CONSTRAINT jobs_status_check CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'dead')),
  CONSTRAINT jobs_priority_check CHECK (priority >= 0)
);

-- Critical indexes for performance
CREATE INDEX idx_jobs_processing ON public.jobs (status, run_at, priority)
  WHERE status IN ('pending', 'failed');

CREATE INDEX idx_jobs_type ON public.jobs (type);

-- Update trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_jobs_updated_at
  BEFORE UPDATE ON public.jobs
  FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

ALTER TABLE public.jobs OWNER TO lipas;
```

#### 1.2 Core Namespaces
Create the job system namespaces:

**File: `src/clj/lipas/jobs/core.clj`**
- Public API: `enqueue-job!`, `fetch-next-jobs`, `mark-completed!`, etc.
- Job configurations and validation
- Legacy compatibility wrappers

**File: `src/clj/lipas/jobs/db.clj`**
- Database access functions
- Atomic job fetching with `SELECT FOR UPDATE SKIP LOCKED`
- Job lifecycle management

**File: `src/clj/lipas/jobs/dispatcher.clj`**
- Multimethod dispatch based on job type
- Job handlers for each type (analysis, elevation, email, etc.)
- Validation and error handling

#### 1.3 Testing Setup
```clojure
;; File: test/clj/lipas/jobs/core_test.clj
(deftest enqueue-and-process-job-test
  (testing "Basic job lifecycle"
    (let [job-id (jobs/enqueue-job! db "email" {:to "test@example.com"})]
      (is (number? job-id))

      (let [jobs (jobs/fetch-next-jobs db {:limit 1})]
        (is (= 1 (count jobs)))
        (is (= "email" (:type (first jobs))))))))
```

### Phase 2: Smart Worker Implementation
**Goal**: Implement the fast lane + general lane worker

#### 2.1 Mixed Duration Worker
**File: `src/clj/lipas/jobs/worker.clj`**

Key components:
- Job duration classification (fast vs slow)
- Dual thread pool management
- Atomic job fetching with priority handling
- Timeout protection for runaway jobs

```clojure
(def job-duration-types
  {:fast #{"email" "produce-reminders" "cleanup-jobs" "integration" "webhook"}
   :slow #{"analysis" "elevation"}})

(defn create-worker-pools [{:keys [fast-threads general-threads]}]
  {:fast-pool (Executors/newFixedThreadPool fast-threads)
   :general-pool (Executors/newFixedThreadPool general-threads)})
```

#### 2.2 Job Handlers
Implement handlers for each job type in the dispatcher:

```clojure
;; Analysis jobs (slow)
(defmethod handle-job "analysis"
  [{:keys [db search]} {:keys [id payload]}]
  (let [{:keys [lipas-id]} payload
        sports-site (core/get-sports-site db lipas-id)
        fcoll (-> sports-site :location :geometries core/simplify)]
    (diversity/recalc-grid! search fcoll)))

;; Email jobs (fast)
(defmethod handle-job "email"
  [{:keys [emailer]} {:keys [id payload]}]
  (case (:type payload)
    "reminder" (email/send-reminder-email! emailer (:email payload) (:link payload) (:body payload))
    (email/send-email! emailer payload)))
```

#### 2.3 Integrant System Integration
**File: `src/clj/lipas/jobs/system.clj`**

Create worker system as separate Integrant system:
```clojure
(def worker-system-config
  {:lipas/db {...}
   :lipas/search {...}
   :lipas/emailer {...}

   :lipas.jobs/scheduler
   {:db (ig/ref :lipas/db)}

   :lipas.jobs/worker
   {:db (ig/ref :lipas/db)
    :search (ig/ref :lipas/search)
    :emailer (ig/ref :lipas/emailer)
    :config {:fast-threads 3 :general-threads 5}}})
```

### Phase 3: Scheduler Implementation
**Goal**: Replace tea-time periodic tasks with producer jobs

#### 3.1 Lightweight Scheduler
**File: `src/clj/lipas/jobs/scheduler.clj`**

Convert periodic tasks to job producers:
```clojure
(def schedule-configs
  {:check-overdue-reminders
   {:job-type "produce-reminders"
    :payload {}
    :interval-seconds 300}

   :cleanup-old-jobs
   {:job-type "cleanup-jobs"
    :payload {:days-old 7}
    :interval-seconds 86400}})
```

#### 3.2 Producer Job Handlers
**File: `src/clj/lipas/jobs/producers.clj`**

Transform current periodic logic into job handlers:
```clojure
(defmethod handle-job "produce-reminders"
  [{:keys [db]} {:keys [id]}]
  ;; Logic from lipas.reminders/add-overdue-to-queue!
  (let [overdue-reminders (db/get-overdue-reminders db)]
    (doseq [reminder overdue-reminders]
      (jobs/enqueue-job! db "email"
                         (build-reminder-email reminder)
                         {:priority 50}))))
```

### Phase 4: Cut-Over Migration
**Goal**: Switch from old to new system in single deployment

#### 4.1 Pre-Migration Preparation
- Complete testing of new system in staging
- Validate all job types process correctly
- Performance testing under load
- Backup all existing queue tables

#### 4.2 Migration Steps
1. **Deploy new system**: Both old and new systems available
2. **Stop old workers**: Graceful shutdown of tea-time processes
3. **Migrate pending jobs**: Move any pending jobs from old tables to new system
4. **Start new workers**: Launch unified job worker
5. **Validate**: Monitor job processing for correctness

#### 4.3 Job Migration Script
**File: `src/clj/lipas/jobs/migration.clj`**
```clojure
(defn migrate-pending-jobs! [db]
  (log/info "Starting job migration...")

  ;; Migrate analysis queue
  (let [analysis-jobs (db/get-analysis-queue db)]
    (doseq [{:keys [lipas-id]} analysis-jobs]
      (jobs/enqueue-job! db "analysis" {:lipas-id lipas-id}))
    (log/info "Migrated analysis jobs" {:count (count analysis-jobs)}))

  ;; Migrate elevation queue
  (let [elevation-jobs (db/get-elevation-queue db)]
    (doseq [{:keys [lipas-id]} elevation-jobs]
      (jobs/enqueue-job! db "elevation" {:lipas-id lipas-id}))
    (log/info "Migrated elevation jobs" {:count (count elevation-jobs)}))

  ;; Migrate email queue
  (let [email-jobs (db/get-email-out-queue! db)]
    (doseq [{:keys [message]} email-jobs]
      (jobs/enqueue-job! db "email" message {:priority 50}))
    (log/info "Migrated email jobs" {:count (count email-jobs)}))

  ;; Migrate integration queue
  (let [integration-jobs (db/get-integration-out-queue db)]
    (doseq [{:keys [lipas-id]} integration-jobs]
      (jobs/enqueue-job! db "integration" {:lipas-id lipas-id}))
    (log/info "Migrated integration jobs" {:count (count integration-jobs)}))

  ;; Migrate webhook queue
  (let [webhook-jobs (db/get-webhook-queue db)]
    (doseq [{:keys [batch-data]} webhook-jobs]
      (jobs/enqueue-job! db "webhook" {:batch-data batch-data}))
    (log/info "Migrated webhook jobs" {:count (count webhook-jobs)}))

  (log/info "Job migration completed successfully"))
```

#### 4.4 Fallback Strategy
If issues are discovered:
1. **Stop new worker**: Halt unified job processing
2. **Restart old workers**: Re-enable tea-time system
3. **Restore old queues**: Jobs tables remain intact during migration
4. **Investigate**: Analyze logs and fix issues
5. **Retry migration**: Once issues resolved

### Phase 5: Production Deployment and Cleanup
**Goal**: Finalize deployment and remove old system

#### 5.1 Monitoring Setup
**File: `src/clj/lipas/jobs/metrics.clj`**
```clojure
(defn record-job-metrics! [registry job-type duration-ms success?]
  (let [timer (get-timer registry (str "job." job-type ".duration"))
        counter (get-counter registry (str "job." job-type
                                          (if success? ".success" ".failure")))]
    (.update timer duration-ms TimeUnit/MILLISECONDS)
    (.inc counter)))
```

#### 5.2 Health Checks
```clojure
(defn queue-health-check []
  (let [stats (jobs/get-queue-stats db)
        pending-count (:pending stats)
        oldest-pending (:oldest-pending-minutes stats)]
    {:healthy? (and (< pending-count 1000)
                    (< oldest-pending 60))
     :stats stats}))
```

#### 5.3 Old System Cleanup
After validating new system works correctly:
```sql
-- Archive old tables for safety
ALTER TABLE analysis_queue RENAME TO analysis_queue_backup;
ALTER TABLE elevation_queue RENAME TO elevation_queue_backup;
ALTER TABLE email_out_queue RENAME TO email_out_queue_backup;
ALTER TABLE integration_out_queue RENAME TO integration_out_queue_backup;
ALTER TABLE webhook_queue RENAME TO webhook_queue_backup;
```

Remove old processing code:
- Delete tea-time scheduling logic from `worker.clj`
- Remove old queue-specific functions from `lipas.backend.core`
- Update all callers to use new job system

#### 5.4 Deployment Configuration
**File: `docker-compose.yml`**
```yaml
services:
  lipas-worker:
    image: lipas:latest
    command: ["java", "-jar", "backend.jar", "worker"]
    environment:
      - WORKER_FAST_THREADS=3
      - WORKER_GENERAL_THREADS=5
```

## Testing Strategy

### Unit Tests
- **Job lifecycle**: Enqueue → Process → Complete
- **Dispatcher**: Each job type handler
- **Concurrency**: Thread pool management
- **Error handling**: Retries and dead letter

### Integration Tests
- **Database**: Atomic job fetching under concurrency
- **End-to-end**: Full job processing pipeline
- **Migration**: Dual-write validation

### Load Tests
- **Concurrency**: Multiple workers processing same queue
- **Performance**: Job throughput under load
- **Blocking**: Fast jobs with slow jobs running

## Configuration

### Development
```clojure
{:lipas.jobs/worker
 {:enabled-job-types #{"produce-reminders" "email" "analysis" "elevation"}
  :fast-threads 1
  :general-threads 2
  :batch-size 5
  :poll-interval-ms 2000}}
```

### Production
```clojure
;; Production configuration for 4 vCPU / 30GB server
{:lipas.jobs/worker
 {:enabled-job-types #{"produce-reminders" "cleanup-jobs"
                       "analysis" "elevation" "email"
                       "integration" "webhook"}
  :fast-threads 3              ; Emails, reminders, integration, webhooks
  :general-threads 5           ; All job types including slow ones
  :batch-size 12               ; Fetch more jobs per iteration
  :poll-interval-ms 3000       ; Poll every 3 seconds
  :fast-timeout-minutes 2      ; Fast jobs timeout
  :slow-timeout-minutes 20}}   ; Elevation jobs timeout
```

## Monitoring and Operations

### Key Metrics
- **Queue depth**: Jobs pending by type
- **Processing time**: P50, P95, P99 by job type
- **Error rate**: Failed jobs by type
- **Thread utilization**: Fast lane vs general lane usage

### Dashboards
1. **Queue Overview**: Pending/processing/completed counts
2. **Performance**: Job duration trends
3. **Errors**: Failed job details and retry patterns
4. **Concurrency**: Thread pool utilization

### Alerts
- Pending jobs > 500 for > 5 minutes
- Any job type error rate > 10%
- Fast lane utilization > 90% for > 2 minutes
- Oldest pending job > 1 hour

## Process Architecture Recommendation

### Current Setup
- **Main Process**: LIPAS webapp (HTTP server, user interface)
- **Worker Process**: Background job processing (separate Docker container)

### Recommended Scheduler Placement: **Worker Process**

**Why put scheduler in worker process:**
✅ **KISS Principle**: All job-related logic in one place
✅ **Simpler deployment**: No new containers or processes needed
✅ **Better cohesion**: Scheduler and worker share job context
✅ **Easier monitoring**: Single process to monitor for all background work
✅ **Resource efficiency**: Scheduler is lightweight, minimal overhead

### Integrant System Architecture
Two separate Integrant systems for clean separation:

#### Webapp System (unchanged)
```clojure
;; File: src/clj/lipas/backend/system.clj (existing)
(def webapp-system-config
  {:lipas/db {...}
   :lipas/search {...}
   :lipas/emailer {...}
   :lipas/server {...}})
```

#### Worker System (new)
**File: `src/clj/lipas/jobs/system.clj`**
```clojure
(def worker-system-config
  {:lipas/db {...}           ; Database connection
   :lipas/search {...}       ; Elasticsearch client
   :lipas/emailer {...}      ; Email service

   :lipas.jobs/scheduler     ; Periodic job producer
   {:db (ig/ref :lipas/db)}

   :lipas.jobs/worker        ; Mixed-duration job processor
   {:db (ig/ref :lipas/db)
    :search (ig/ref :lipas/search)
    :emailer (ig/ref :lipas/emailer)
    :config {:fast-threads 3 :general-threads 5}}})

;; Integrant component definitions
(defmethod ig/init-key :lipas.jobs/scheduler
  [_ {:keys [db]}]
  (log/info "Starting job scheduler")
  (scheduler/start-scheduler! db)
  {:status :running :db db})

(defmethod ig/halt-key! :lipas.jobs/scheduler
  [_ _]
  (log/info "Stopping job scheduler")
  (scheduler/stop-scheduler!))

(defmethod ig/init-key :lipas.jobs/worker
  [_ {:keys [db search emailer config]}]
  (log/info "Starting mixed-duration worker")
  (worker/start-mixed-duration-worker!
    {:db db :search search :emailer emailer}
    config)
  {:status :running :config config})

(defmethod ig/halt-key! :lipas.jobs/worker
  [_ _]
  (log/info "Stopping mixed-duration worker")
  (worker/stop-mixed-duration-worker!))
```

#### Worker Process Entry Point
**File: `src/clj/lipas/worker.clj`** (updated)
```clojure
(ns lipas.worker
  (:require
   [integrant.core :as ig]
   [lipas.jobs.system :as job-system]
   [taoensso.timbre :as log]))

(defn -main [& args]
  (log/info "Starting LIPAS worker system")

  ;; Start the worker Integrant system
  (let [system (ig/init job-system/worker-system-config)]

    ;; Add shutdown hook
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(ig/halt! system) "shutdown-hook"))

    (log/info "LIPAS worker system started successfully")

    ;; Keep running
    (while true (Thread/sleep 5000))))
```

### Docker Architecture
```yaml
services:
  backend:
    # Main application - no job processing
    # ... no changes ...

  lipas-worker:
    # Combined scheduler + worker in one process
    image: lipas:latest
    command: ["java", "-jar", "backend.jar", "worker"]
    environment:
      - WORKER_FAST_THREADS=3
      - WORKER_GENERAL_THREADS=5
```

### Alternative: Separate Scheduler Process (Not Recommended)
If you wanted a separate scheduler process:
```yaml
services:
  lipas-scheduler:
    image: lipas:latest
    command: ["java", "-jar", "backend.jar", "scheduler"]
```

**Why not recommended:**
❌ **More complexity**: Additional container to manage
❌ **Resource overhead**: Separate JVM startup costs
❌ **Deployment complexity**: Three containers instead of two
❌ **Against KISS**: Adds unnecessary moving parts

## Rollback Strategy

#### Simple Rollback Process
Since we're doing a cut-over migration rather than gradual:

1. **Stop new worker**: Halt unified job processing immediately
2. **Restart old system**: Re-enable tea-time workers in previous container
3. **Data preservation**: All old queue tables remain intact during migration
4. **Resume processing**: Old system continues from where it left off
5. **Investigation**: Analyze issues in staging environment

#### Emergency Procedures
1. **Immediate rollback**: Script to stop new worker and restart old system
2. **Data safety**: Old queue tables preserved as backup
3. **Monitoring**: Alerts trigger rollback procedures if needed
4. **Communication**: Clear escalation path for issues

## Success Criteria

### Functional Requirements
✅ All job types process correctly
✅ No data loss during migration
✅ Fast jobs never blocked by slow jobs
✅ Error rates remain stable
✅ Processing times improve overall

### Operational Requirements
✅ Single place to monitor all background jobs
✅ Easy to add new job types
✅ Simplified deployment process
✅ Better visibility into queue performance
✅ Consistent retry and error handling

## File Structure Summary

```
src/clj/lipas/jobs/
├── core.clj           # Public API and job management
├── db.clj             # Database access layer
├── dispatcher.clj     # Job type routing and handlers
├── worker.clj         # Mixed duration worker implementation
├── scheduler.clj      # Periodic job producer scheduling
└── system.clj         # Worker Integrant system configuration

src/clj/lipas/
├── worker.clj         # Worker process entry point
└── backend/system.clj # Webapp Integrant system (existing)

test/clj/lipas/jobs/
├── core_test.clj      # Core functionality tests
├── worker_test.clj    # Concurrency and processing tests
└── integration_test.clj # End-to-end tests

resources/migrations/
└── 20250107000000-unified-jobs-table.up.sql

docs/
└── llm-queue-refactoring-plan.md # This file
```

## Getting Started

### For Developers
1. **Read this plan** - Understand the architecture and migration strategy
2. **Run tests** - `bb test` to validate current system
3. **Create branch** - `git checkout -b feature/unified-queue`
4. **Implement Phase 1** - Start with database schema and core namespaces
5. **Test incrementally** - Validate each phase before moving forward

### For LLMs Helping with Implementation
- **Context**: This is a Clojure web application (LIPAS) for Finnish sports facilities
- **Scale**: Small to medium - "only so many sports facilities in Finland"
- **Priority**: Reliability over performance - don't break the existing system
- **Style**: Follow existing LIPAS patterns (HugSQL, Integrant, Timbre logging)
- **Testing**: Write tests for each component before integration

---

*This plan implements a unified job queue system for LIPAS with smart concurrency control and simple cut-over migration strategy.*
