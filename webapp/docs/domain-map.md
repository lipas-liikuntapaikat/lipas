# LIPAS Domain Map

Use this document to find the source of truth for a change. It is intentionally
compact: follow the links for detail, then verify current behavior in source,
tests, and the REPL.

## System in one page

LIPAS is Finland's national registry of sports facilities. Its central record is
a data-driven sports-site document identified permanently by `lipas-id` and
stored as an ordered series of revisions.

```text
Browser SPA (Re-frame/Reagent, OpenLayers)
    │ HTTP actions and queries
    ▼
Ring/Reitit application
    │ authentication, coercion, authorization, orchestration
    ├── PostgreSQL/PostGIS ── authoritative records and revision history
    ├── Elasticsearch ─────── derived search/read projections
    ├── PostgreSQL jobs ───── post-commit enrichment and integration work
    └── External systems ──── PTV, UTP/Luontoon.fi, OSRM, GeoServer, MML
```

Integrant owns stateful component construction and lifecycle. Shared `.cljc`
data and Malli schemas carry domain vocabulary and validation between backend
and frontend.

## Sources of truth by concern

| Concern | Authoritative starting points | Deep reference / representative tests |
|---|---|---|
| Runtime components | `lipas.backend.config`, `lipas.backend.system`, `lipas.jobs.system` | [architecture.md](architecture.md), [backend.md](backend.md) |
| HTTP application | `lipas.backend.handler`, `lipas.backend.api.v1.routes`, `lipas.backend.api.v2` | `lipas.backend.api.v1.*-test`, `lipas.backend.api.v2.handler-test` |
| Sports-site shape | `lipas.schema.sports-sites` and its nested schema namespaces | [data-model.md](data-model.md), `lipas.schema.sports-sites-test` |
| Type/property vocabulary | `lipas.data.types`, `lipas.data.prop-types`, `lipas.data.owners`, `lipas.data.admins`, `lipas.data.status` | schema tests under `test/clj/lipas/schema/` |
| Sports-site persistence | `lipas.backend.db.db`, `lipas.backend.db.sports-site`, `resources/sql/sports_site.sql` | [database.md](database.md), handler/integration tests |
| Save orchestration | `lipas.backend.core/save-sports-site!` | `lipas.backend.org-test`, API handler/integration tests, `dev/lipas/e2e/tools.clj` |
| Search projection | `lipas.backend.core/enrich`, `index!`, `lipas.backend.search`, `lipas.search-indexer` | [search.md](search.md), search and legacy-index tests |
| Authorization | `lipas.roles`, then route middleware and business-level checks | [auth.md](auth.md), `lipas.roles-test`, `lipas.backend.org-test` |
| Organizations | `lipas.backend.org`, `lipas.schema.org`, organization routes/UI | `lipas.backend.org-test` |
| Background jobs | `lipas.jobs.registry`, `lipas.jobs.core`, `lipas.jobs.worker`, `lipas.jobs.handler` | job namespace tests and admin jobs UI |
| PTV | `lipas.backend.ptv.*`, `lipas.data.ptv`, organization PTV configuration | [context-ptv.md](context-ptv.md), [integration-ptv-architecture.md](integration-ptv-architecture.md) |
| Help and assistant | `lipas.backend.help`, `lipas.backend.kb`, `lipas.backend.assistant`, `lipas.backend.llm`, shared schemas, matching UI features | current tests plus `docs/wip/ai-assistant-implementation.md` while it remains WIP |
| Frontend state/navigation | `lipas.ui.core`, `lipas.ui.routes`, feature `events`/`subs`/`views` namespaces | [frontend.md](frontend.md), [guide-frontend-patterns.md](guide-frontend-patterns.md) |
| GIS and analysis | `lipas.backend.gis`, `lipas.backend.analysis.*`, map feature namespaces | [map-gis.md](map-gis.md), [heatmap.md](heatmap.md) |
| I18n | `lipas.i18n.*`, translation maps and `:tr` subscriptions | [i18n.md](i18n.md) |

When a document and code disagree, migrations and executable source/tests win.
Correct the document as part of the task when the difference is durable.

## Architectural boundaries

### Domain data and schemas

The project is intentionally data-driven:

- `lipas.data.*` maps define facility types, properties, owner/admin values,
  statuses, activities, cities, and other controlled vocabulary.
- `lipas.schema.*` derives or composes Malli contracts from that vocabulary.
- `.cljc` makes genuinely shared domain rules available to Clojure and
  ClojureScript.
- Roles and privileges are data in `lipas.roles`; `check-privilege` interprets
  that data against a role context.
- Reitit routes, Integrant systems, and the job registry are declarative maps
  interpreted at system boundaries.

Prefer extending these established data/dispatch points over adding parallel
conditional logic. Normalize external representations at entry points and keep
one canonical internal shape.

### Backend effects

Keep pure decisions and transformations separate from effects:

```text
transport/coercion → domain decision/transformation → DB transaction
                                            └──────→ post-commit effects
```

HTTP handlers should primarily adapt transport data and invoke application
functions. PostgreSQL queries belong in the DB layer. Search enrichment should
be deterministic from an authoritative sports-site document wherever possible.
External calls and background work need explicit failure and consistency rules.

### Frontend flow

The SPA follows Re-frame's unidirectional model:

```text
DOM input → event → coeffect/effect → app-db → subscription → view
```

Keep views declarative, subscriptions as derived reads, and events/effects as
state transitions and side-effect orchestration. Put reusable domain
transformations in pure/shared functions rather than render bodies.

## Sports-site lifecycle

### Two different statuses

Do not confuse these:

| Status | Location | Meaning |
|---|---|---|
| Facility `:status` | Inside the JSONB `document` | Domain lifecycle: `planning`, `planned`, `active`, temporary/permanent out-of-service, or `incorrect-data` |
| Revision publication status | `sports_site.status` column / metadata `:doc-status` | Storage visibility: `published` or `draft` |

`sports_site_current` selects the newest non-draft revision per `lipas_id`; it
does not select only facilities whose document status is `active`.

### Normal save path

`lipas.backend.core/save-sports-site!` is the complete application save path:

1. Start a PostgreSQL transaction and read the current revision when updating.
2. Check contextual permissions and existence through
   `upsert-sports-site!`; allocate a `lipas-id` for a new site.
3. Insert a full new revision. Published revisions appear through
   `sports_site_current`; drafts are fetched separately by author.
4. If enabled and applicable, PTV synchronization runs inside the transaction
   and can append another sports-site revision. The current PTV implementation
   also indexes that revision from inside the transaction; the returned response
   is aligned with the PTV revision.
5. Commit the transaction.
6. Enqueue applicable background jobs. Registry trigger functions decide which
   jobs the before/after change requires.
7. Synchronously index the current document into `sports_sites_current` and
   either index or remove it from the status-filtered legacy index.

Draft saves skip PTV, jobs, and Elasticsearch indexing.

Use `save-sports-site!` for normal application/E2E behavior. The similarly named
`upsert-sports-site!` handles DB revision insertion and permissions but does not
perform the post-commit search/job integration path. The `*` variant is for
trusted migrations or controlled internal use and skips normal safeguards.

### Consistency model

PostgreSQL is authoritative. Elasticsearch writes happen after commit, so an
indexing failure can leave a committed DB revision missing or stale in search.
Reindexing repairs this direction of drift. Background jobs are enqueued before
indexing so an Elasticsearch failure does not suppress required work.

PTV synchronization is a known exception to the otherwise post-commit indexing
boundary: it can index from inside the DB transaction, after which the outer save
indexes the final revision again following commit. Consequently, failures in the
PTV branch can produce different DB/ES ordering than an ordinary save. Verify
both layers explicitly when changing that path.

The main search index is a derived enriched projection. The legacy index uses a
legacy transformation and contains only `active` and
`out-of-service-temporarily` facilities. The two projections are not equivalent.

### Historical sharp edges

- Normal saves append revisions, but the historical permanent-closure repair
  path mutates later revision documents to `incorrect-data` before inserting the
  new revision. Treat “append-only” as the normal write model, not proof that no
  maintenance SQL ever updates an existing row.
- `core/get-sports-site-history` reads `sports_site_by_year`; it returns yearly
  snapshots and can collapse multiple revisions from the same year. Use the raw
  revision table or the E2E `revision-count` helper when exact revision count is
  the invariant.
- PTV synchronization may create two revisions during one logical save.
- A new site's creator can receive a contextual `:site-manager` role if existing
  permissions do not cover it. Planning-site creation also has an analysis-tool
  permission path.
- Reconstructing old JSON documents can silently discard unknown historical
  fields. Prefer transformations that preserve keys unless removal is explicit.

## Authorization mental model

Authorization is privilege-based and context-sensitive:

```text
stored role assignment
    + role definition's privilege set
    + requested context (city/type/site/activity/org)
    = privilege decision
```

`lipas.roles/check-privilege` is shared between backend and frontend. It selects
role assignments whose constraints match the supplied context, always includes
the unassignable `:default` role, expands active roles to privileges, and checks
the requested privilege. `::roles/any` asks whether a matching permission exists
for any value of a context dimension.

The runtime matcher uses scope keys present on the stored assignment; it does
not enforce the role definition's `:required-context-keys`. Missing assignment
scope matches globally for that dimension, so permission-map schema validation
is security-sensitive. Keep role definitions, user schemas, editor UI, and tests
aligned—especially for PTV and organization roles.

Frontend permission checks control affordances; backend route and business
checks enforce authority. Never treat a hidden button as authorization. Prefer
privilege checks over direct role-name checks; `check-role` is normally for
classification, tracking, or UI purposes rather than granting access.

JWTs contain a permissions snapshot. The UI refreshes from the DB every 15
minutes, but ordinary API authorization does not re-read the user; stale tokens
can retain old permissions until their six-hour expiry. There is no general
server-side revocation mechanism in the current flow.

## Verification by change type

| Change | Minimum focused evidence | Likely broader evidence |
|---|---|---|
| Pure domain transform | REPL examples plus focused unit/property test | callers' namespace tests |
| Shared schema/data | Malli validation in REPL and schema tests | backend and frontend compile; compatibility fixtures |
| Permission rule | `check-privilege` matrix at REPL | `lipas.roles-test`, handler/org tests, UI affordance check |
| Sports-site save | focused handler/core test and before/after DB value | revision count, `e2e/coherent?`, job/index assertions |
| Search enrichment/mapping | enriched document and focused search test | reindex against dev data only when explicitly in scope |
| Re-frame behavior | event/subscription state at CLJS REPL | `(user/compile-cljs)` and browser-visible assertion |
| Migration | migration SQL/data check on disposable/dev DB | affected queries and compatibility tests; never production implicitly |

See [AGENTS.md](../AGENTS.md) for the complete exploration, REPL, clean-test,
and browser feedback loop.

## Maintenance rule

Keep this file as a router and invariant ledger, not a second implementation.
Add a fact only when it changes how multiple tasks should be approached. Put
subsystem detail in its focused document and link to executable source/tests.
