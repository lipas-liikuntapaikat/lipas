# Database Schema

LIPAS uses PostgreSQL with extensive JSONB document storage. The schema follows an append-only event sourcing pattern for core entities.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      PostgreSQL                             │
├─────────────────────────────────────────────────────────────┤
│  Event Log Tables          │  Current State Views          │
│  ─────────────────         │  ────────────────────         │
│  sports_site (append-only) │  sports_site_current          │
│  loi (append-only)         │  loi_current                  │
│                            │  sports_site_by_year          │
├─────────────────────────────────────────────────────────────┤
│  Standard Tables           │  Job Queue System             │
│  ───────────────           │  ─────────────────            │
│  account                   │  jobs                         │
│  org                       │  dead_letter_jobs             │
│  city                      │  circuit_breakers             │
│  subsidy                   │  job_metrics                  │
│  reminder                  │  webhook_queue                │
│  email_out_queue           │                               │
│  versioned_data            │                               │
└─────────────────────────────────────────────────────────────┘
```

## Core Tables

### `sports_site` - Facility Event Log

The central table implementing **append-only event sourcing**. Every edit creates a new revision sharing the same `lipas_id`.

```sql
CREATE TABLE sports_site (
  id         uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
  event_date timestamptz NOT NULL,  -- When this revision became valid
  lipas_id   integer NOT NULL,      -- Permanent facility identifier
  status     text NOT NULL,         -- 'published', 'draft'
  document   jsonb NOT NULL,        -- Full facility data
  author_id  uuid REFERENCES account(id),
  type_code  integer NOT NULL,      -- Facility type (indexed)
  city_code  text NOT NULL          -- City code (indexed)
);
```

**Key characteristics:**
- `lipas_id` is the permanent identifier for a facility
- `id` (UUID) uniquely identifies each revision
- `document` contains the complete facility data as JSONB
- Revisions are never updated or deleted (append-only)

### `account` - User Accounts

```sql
CREATE TABLE account (
  id                uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at        timestamptz DEFAULT CURRENT_TIMESTAMP,
  email             text UNIQUE NOT NULL,
  username          text UNIQUE NOT NULL,
  password          text NOT NULL,       -- bcrypt hash
  permissions       jsonb,               -- Role/permission structure
  user_data         jsonb,               -- Profile and preferences
  history           jsonb,               -- Activity history
  status            text,                -- 'active', 'archived'
  reset_token       text,
  reset_valid_until timestamp
);
```

### `org` - Organizations

```sql
CREATE TABLE org (
  id       uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  name     text UNIQUE NOT NULL,
  data     jsonb,        -- Organization metadata
  ptv_data jsonb         -- Finnish public service registry data
);
```

### `loi` - Locations of Interest

Follows the same append-only pattern as `sports_site` for non-sports-site locations.

```sql
CREATE TABLE loi (
  id         uuid NOT NULL,
  event_date timestamptz NOT NULL,
  created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
  author_id  uuid REFERENCES account(id),
  status     text DEFAULT 'active',
  loi_type   text NOT NULL,
  document   jsonb NOT NULL,
  PRIMARY KEY (id, event_date)
);
```

### `city` - City Statistics

```sql
CREATE TABLE city (
  city_code integer PRIMARY KEY,
  stats     jsonb           -- Population, area, etc.
);
```

### `subsidy` - Government Subsidies

```sql
CREATE TABLE subsidy (
  id   serial PRIMARY KEY,
  year integer NOT NULL,
  data jsonb              -- Subsidy details
);
```

### `reminder` - User Reminders

```sql
CREATE TABLE reminder (
  id         uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
  event_date timestamptz NOT NULL,  -- When to send
  account_id uuid REFERENCES account(id),
  body       jsonb NOT NULL,        -- Reminder content
  status     text NOT NULL          -- 'pending', 'sent'
);
```

### `versioned_data` - Generic Versioned Storage

For storing versioned configuration or reference data.

```sql
CREATE TABLE versioned_data (
  id         uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  event_date timestamptz DEFAULT NOW(),
  status     text NOT NULL,         -- 'published', 'draft'
  type       text NOT NULL,         -- Data category
  body       jsonb NOT NULL
);

CREATE INDEX idx_versioned_data_type_event_date
  ON versioned_data(type, event_date DESC);
```

## Views

### `sports_site_current`

Shows only the latest **published** revision for each facility.

```sql
CREATE VIEW sports_site_current AS
SELECT a.*
FROM sports_site a
JOIN (
  SELECT id, row_number() OVER (
    PARTITION BY lipas_id
    ORDER BY event_date DESC, created_at DESC
  ) AS row_number
  FROM sports_site
  WHERE status <> 'draft'
) b ON a.id = b.id AND b.row_number = 1;
```

**Usage:** Most read operations use this view via `get-sports-site`.

### `sports_site_by_year`

Shows the latest revision per facility per year (for historical analysis).

```sql
CREATE VIEW sports_site_by_year AS
SELECT a.*
FROM sports_site a
JOIN (
  SELECT id, row_number() OVER (
    PARTITION BY lipas_id, date_trunc('year', event_date)
    ORDER BY event_date DESC
  ) AS row_number
  FROM sports_site
  WHERE status != 'draft'
) b ON a.id = b.id AND b.row_number = 1;
```

### `loi_current`

Latest revision of each Location of Interest.

```sql
CREATE VIEW loi_current AS
SELECT a.*
FROM loi a
JOIN (
  SELECT id, max(event_date) AS max_date
  FROM loi
  GROUP BY id
) b ON a.id = b.id AND a.event_date = b.max_date;
```

## Job Queue System

A unified job queue with reliability features.

### `jobs` - Main Job Queue

```sql
CREATE TABLE jobs (
  id             bigserial PRIMARY KEY,
  type           text NOT NULL,
  payload        jsonb DEFAULT '{}',
  status         text DEFAULT 'pending',  -- pending/processing/completed/failed/dead
  priority       integer DEFAULT 100,      -- Higher = more urgent
  attempts       integer DEFAULT 0,
  max_attempts   integer DEFAULT 3,
  run_at         timestamptz DEFAULT now(),
  started_at     timestamptz,
  completed_at   timestamptz,
  error_message  text,
  correlation_id uuid DEFAULT uuid_generate_v4(),
  parent_job_id  bigint REFERENCES jobs(id),
  dedup_key      text,                     -- Prevents duplicate jobs
  created_at     timestamptz DEFAULT now(),
  updated_at     timestamptz DEFAULT now()
);

-- Processing performance index
CREATE INDEX idx_jobs_processing ON jobs (status, run_at, priority)
  WHERE status IN ('pending', 'failed');

-- Deduplication constraint
CREATE UNIQUE INDEX idx_jobs_dedup_unique ON jobs (type, dedup_key)
  WHERE dedup_key IS NOT NULL AND status IN ('pending', 'processing');
```

### `dead_letter_jobs` - Failed Job Archive

Jobs that exceed retry attempts are moved here for manual review.

```sql
CREATE TABLE dead_letter_jobs (
  id              bigserial PRIMARY KEY,
  original_job    jsonb NOT NULL,
  error_message   text NOT NULL,
  error_details   jsonb,
  correlation_id  uuid,
  died_at         timestamptz DEFAULT now(),
  acknowledged    boolean DEFAULT false,
  acknowledged_by text,
  acknowledged_at timestamptz
);
```

### `circuit_breakers` - External Service Protection

Prevents cascading failures when external services are down.

```sql
CREATE TABLE circuit_breakers (
  service_name    text PRIMARY KEY,
  state           text DEFAULT 'closed',  -- closed/open/half_open
  failure_count   integer DEFAULT 0,
  success_count   integer DEFAULT 0,
  last_failure_at timestamptz,
  opened_at       timestamptz,
  half_opened_at  timestamptz,
  updated_at      timestamptz DEFAULT now()
);
```

### `job_metrics` - Performance Monitoring

```sql
CREATE TABLE job_metrics (
  id             bigserial PRIMARY KEY,
  job_type       text NOT NULL,
  status         text NOT NULL,
  duration_ms    bigint,
  queue_time_ms  bigint,
  correlation_id uuid,
  recorded_at    timestamptz DEFAULT now()
);
```

### `job_queue_health` - Monitoring View

```sql
CREATE VIEW job_queue_health AS
SELECT
  type, status,
  COUNT(*) as count,
  MIN(created_at) as oldest,
  MAX(attempts) as max_attempts,
  AVG(EXTRACT(EPOCH FROM (now() - created_at))) as avg_age_seconds
FROM jobs
WHERE status IN ('pending', 'processing', 'failed')
GROUP BY type, status;
```

## JSONB Document Patterns

### Automatic Clojure ↔ JSONB Conversion

The `lipas.backend.db.utils` namespace provides transparent conversion:

```clojure
;; Clojure maps/vectors automatically become JSONB
(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [m] (->pgobject m)))

;; JSONB automatically becomes Clojure data
(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [v _ _]
    (<-pgobject v)))
```

### Marshall/Unmarshall Pattern

Each entity has marshall (Clojure→DB) and unmarshall (DB→Clojure) functions:

```clojure
;; sports_site.clj
(defn marshall [sports-site user status]
  (-> {:event-date (:event-date sports-site)
       :lipas-id   (:lipas-id sports-site)
       :status     status
       :type-code  (-> sports-site :type :type-code)
       :city-code  (-> sports-site :location :city :city-code)
       :author-id  (:id user)}
      utils/->snake-case-keywords
      (assoc :document sports-site)))  ; Full data stored as JSONB

(defn unmarshall [{:keys [document author_id status]}]
  (with-meta document {:author-id author_id :doc-status status}))
```

### Key-Case Conversion

- Database uses `snake_case`
- Clojure uses `kebab-case`
- Conversion happens automatically via `camel-snake-kebab`

## Migration System

Migrations use [Migratus](https://github.com/yogthos/migratus) with timestamp-prefixed files.

### File Naming Convention

```
YYYYMMDDHHMMSS-description.up.sql    # Forward migration
YYYYMMDDHHMMSS-description.down.sql  # Rollback migration
YYYYMMDDHHMMSS-description.edn       # Clojure-based migration
```

### SQL Migrations

```sql
-- 20180521143603-initial.up.sql
CREATE TABLE account (...);
CREATE TABLE sports_site (...);
```

### Clojure Migrations (EDN)

For complex data transformations:

```clojure
;; 20241007092836-roles.edn
{:ns lipas.migrations.roles
 :up-fn migrate-up
 :down-fn migrate-down
 :transaction? true}
```

### Running Migrations

```clojure
;; In REPL
(user/run-db-migrations!)
```

## Indexing Strategy

### Core Table Indexes

| Table            | Index                                | Purpose          |
|------------------|--------------------------------------|------------------|
| `sports_site`    | Primary on `id`                      | Row lookup       |
| `sports_site`    | Implied on `lipas_id`, `event_date`  | View performance |
| `jobs`           | `(status, run_at, priority)` partial | Job fetching     |
| `jobs`           | `(type, dedup_key)` unique partial   | Deduplication    |
| `versioned_data` | `(type, event_date DESC)`            | Latest by type   |
| `job_metrics`    | `(job_type, recorded_at)`            | Metrics queries  |

### JSONB Indexing

JSONB columns are not indexed by default. For frequent queries on JSONB fields, consider GIN indexes:

```sql
-- Example (not currently used)
CREATE INDEX idx_document_status ON sports_site
  USING GIN ((document -> 'status'));
```

## Key Files

| File                                 | Purpose                    |
|--------------------------------------|----------------------------|
| `resources/sql/*.sql`                | HugSQL query definitions   |
| `resources/migrations/`              | Schema migrations          |
| `src/clj/lipas/backend/db/db.clj`    | High-level DB operations   |
| `src/clj/lipas/backend/db/*.clj`     | Entity-specific operations |
| `src/clj/lipas/backend/db/utils.clj` | JSONB conversion utilities |

## Common Patterns

### Fetching Current State

```clojure
;; Uses sports_site_current view
(db/get-sports-site db-spec lipas-id)
```

### Creating New Revision

```clojure
;; Appends new row, doesn't update existing
(db/upsert-sports-site! db-spec user sports-site)
```

### Draft Support

```clojure
;; status = 'draft', excluded from current view
(db/upsert-sports-site! db-spec user sports-site true)

;; Fetch user's drafts
(db/get-users-drafts db-spec user)
```

### Job Enqueueing

```clojure
;; From jobs.sql
INSERT INTO jobs (type, payload, priority, run_at, max_attempts)
VALUES (:type, :payload::jsonb, :priority, :run_at, :max_attempts)
RETURNING id;
```

## Data Flow

```
User Edit
    │
    ▼
sports_site table (new revision appended)
    │
    ▼
sports_site_current view (reflects latest)
    │
    ▼
Elasticsearch index (enriched, denormalized)
    │
    ▼
API responses / Search queries
```

The database is the **source of truth** with full history. Elasticsearch serves as a read-optimized cache of current revisions.
