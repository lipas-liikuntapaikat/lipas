# PTV Integration Architecture

This document describes the architecture, principles, and design decisions of the
LIPAS↔PTV integration. It is the canonical entry-point for understanding *why*
the integration is shaped the way it is. Implementation details (sequence
diagrams, sample payloads, endpoint reference) live in
[`integration-ptv.md`](integration-ptv.md). Audit workflow details live in
[`integration-ptv-audit.md`](integration-ptv-audit.md). AI-driven description
generation lives in [`ptv-ai-integration.md`](ptv-ai-integration.md).

## What this is

PTV (Palvelutietovaranto) is Finland's centralized service information
repository — citizens discover public services through suomi.fi, which is fed
from PTV. LIPAS holds Finland's authoritative catalogue of public sports
facilities. The integration publishes LIPAS-managed sports facility data to PTV
so the same facilities appear on suomi.fi without anyone authoring the same
information twice.

The integration is operational for pilot municipalities (Utajärvi, Raahe,
Liminka, Oulu) and expanding. Each municipality's PTV organization is
configured separately in LIPAS.

## Goals

1. **Publish LIPAS facility data to PTV** so each LIPAS sports site appears as
   a PTV ServiceLocation linked to a PTV Service for its sub-category.
2. **Single source of truth.** Municipalities author facility data in LIPAS;
   the integration takes care of the PTV side.
3. **Per-site control.** Each sports site has a sync-enabled toggle; not every
   facility in LIPAS needs to be in PTV.
4. **Multi-language support.** LIPAS publishes Finnish, Swedish, and English
   versions where the LIPAS user has authored them.
5. **Minimal maintenance burden.** Kuntien shouldn't need to keep two systems
   in agreement by hand. Edits in LIPAS propagate; edits in PTV are temporary.

## Non-goals

1. **Automatic two-way sync.** PTV-side edits to LIPAS-managed fields never
   flow into LIPAS on their own. The sync review shows them, and a user can
   take one into LIPAS explicitly (principle #3).
2. **Real-time sync.** Sync is event-driven (on save) or manual (button). There
   is no background poller, no webhook listener.
3. **Automatic conflict resolution.** When LIPAS and PTV both changed a field
   since the last sync, the user decides which version stays; nothing is
   merged automatically.
4. **Mirroring PTV's full data model.** Only fields LIPAS understands and can
   author are integrated. PTV-only metadata (e.g. opening hours in PTV's full
   form) is left untouched.
5. **Bringing PTV state into LIPAS for display elsewhere.** PTV state is read
   for the sync review only; it is not surfaced as "live PTV content" anywhere
   else in LIPAS.

## Core principles

### 1. LIPAS is master

For every PTV field LIPAS integrates, LIPAS is the source of truth. PTV is a
publishing target. This is the foundational rule everything else follows from.
The original integration design called for one-way LIPAS→PTV sync; this is
preserved.

### 2. One-way write

Sync is exclusively LIPAS → PTV. The system never writes PTV state into the
LIPAS DB by itself. PTV is treated as a downstream replica with respect to
LIPAS-managed fields. A PTV value enters LIPAS only when a user takes it in the
sync review, and then it is an ordinary LIPAS edit (a new site revision), which
the sync then pushes.

### 3. Review before overwrite

When PTV state diverges from what LIPAS will push (typically because a kunta
edited the PTV record directly), the integration shows the divergence in the
LIPAS UI, says who changed what since the last sync, and lets the user choose
per field: keep LIPAS's version, take PTV's (it is copied into LIPAS), or write
their own. A sync that would overwrite a change made in PTV without a decision
asks first (the wizard export waits until each has one).

This rules out the mid-way "preserve PTV-side edits" patterns the system
briefly experimented with — those quietly hide PTV-side state from the LIPAS
user, which is a worse failure mode than overwriting it visibly. Nothing is
preserved silently: a PTV-side value that survives a sync does so because a
user chose it (or, on a first link, it is one of the location's connections,
listed in the review), and it then lives in LIPAS.

(Before October 2026 this principle was "drift visibility, not drift
resolution": the divergence was shown, and the user had to retype PTV's value
in LIPAS to keep it.)

### 4. Per-language Finnish fallback

Finnish is the canonical language. For each declared language version (Swedish,
English), LIPAS uses the localized value when the user has entered one,
otherwise falls back to the Finnish value. Whitespace-only counts as
not-entered. This ensures every language slot PTV expects always has content.

### 5. Idempotent full-replace updates

Every sync sends a fully-formed payload covering every declared language. PTV's
PUT endpoints replace the relevant lists wholesale. There is no diff
computation, no client-side bookkeeping of what changed. Re-running a sync is
safe and produces the same PTV state as the first run.

### 6. Cohesion at the data boundary

The data transformation (LIPAS site → PTV payload) is pure and lives in
`lipas.data.ptv` (cljc). This keeps the orchestration layer (HTTP, DB, jobs)
free of data-shape concerns and makes the transformation testable in
isolation. Drift detection lives next to the transformation for the same
reason.

## Conceptual model

### Mapping

```
LIPAS                                            PTV
─────                                            ───
Organization (kunta)                          ↔  Organization
Sub-category (e.g. Pallokentät)               →  Service (Palvelu)
Sports site (e.g. Rantakylän koulun pall.)    →  ServiceLocation (Palvelupaikka)
```

- One PTV **Service** per (sub-category × organization).
- One PTV **ServiceLocation** per (sports site × organization).
- A ServiceLocation links to one or more Services.

The Service represents the *kind* of facility ("playing fields"); the
ServiceLocation is the specific facility at a specific address.

### Identity

LIPAS generates deterministic source-ids embedded in PTV resources:

- Services: `lipas-{org-id}-{sub-category-id}`
- ServiceLocations: `lipas-{org-id}-{lipas-id}-{timestamp}`

Source-ids act as upsert keys: LIPAS can find a previously-created PTV resource
even if the PTV UUID has changed (PTV may issue new UUIDs across versioned
updates). Both source-id and current PTV UUID are persisted in the
sports-site's `:ptv` metadata.

### Language codes

LIPAS uses `:se` for Swedish internally. PTV uses `"sv"`. Translation happens
*only* at API boundaries — never inside LIPAS data structures. The maps are in
`lipas.data.ptv`:

```clojure
(def lang->locale         {"fi" :fi, "sv" :se, "en" :en})         ;; PTV → LIPAS
(def lipas-lang->ptv-lang {"fi" "fi", "se" "sv", "en" "en"})      ;; LIPAS → PTV
```

This was a historical bug source — Swedish content was silently dropped when a
conversion was missing. Centralizing the maps prevents recurrence.

## Data flow architecture

### Outbound: sync (LIPAS → PTV)

1. **Trigger.** Auto on sports-site save (when `:sync-enabled true`), manual
   from the LIIKUNTAPAIKAT tab, or batch from the wizard.
2. **Build payload.** `->ptv-service-location` produces a fully-formed PTV PUT
   body: every declared language has Name, displayNameType entry, Summary, and
   Description; addresses, services, and contact info are derived from LIPAS
   site data.
3. **PUT or POST.** PUT if `:service-channel-ids` exists; POST otherwise.
4. **Connection diff.** For updates, the upsert reads the channel's current
   service link set and patches added/removed services via the Connection
   endpoint.
5. **Persist.** Store the returned PTV state (new IDs if any, sourceId,
   publishing-status, last-sync timestamp) back into the sports-site's `:ptv`
   metadata. The sports site is rewritten as a new event-log revision.
6. **Index.** Re-index the site in Elasticsearch with the updated PTV meta.

No fetch-merge-write step. The payload is built from LIPAS state alone.

### Inbound: the sync review (PTV → cache)

1. On UI load the backend fetches the org's PTV ServiceChannels and Services
   via PTV list endpoints and returns them to the frontend.
2. The frontend caches the responses in re-frame app-db per organization,
   keyed by PTV UUID. (There is no server-side cache.) It also fetches each
   synced site's *base*, what its last sync sent
   (`/actions/fetch-ptv-sync-bases`); the site form fetches the base and the
   live channel for its one site (`/actions/fetch-ptv-site-review`).
3. `sync-review` (cljc) compares each site three ways, the saved site, the
   channel and the base, and returns one row per difference with who changed
   it (see "Sync review model").

PTV-side edits are read for the review. **They reach the LIPAS DB only when a
user takes them**, as an edit of the site. This is what differentiates the
review from "two-way sync."

### Shadow store for Services (`ptv_service`)

PTV Services now have a durable LIPAS-side record: the append-only
`ptv_service` table (revisions keyed by `(org_id, source_id)`, latest exposed
via the `ptv_service_current` view). Every successful Service write shadows
the **LIPAS-managed subset** of the PTV response (names, descriptions,
languages, publishing status — `lipas.data.ptv/->service-document`) into a new
revision, and Service audits are stored on the same document (`:audit`, same
schema as site audits). This does not violate the one-way principle: what is
persisted is LIPAS's own pushed state (plus a one-time lazy snapshot at
audit time), never kunta-side PTV edits. PTV remains authoritative for all
non-LIPAS-managed Service fields (ontology terms, service classes, target
groups, ...). See `integration-ptv-audit.md` → "Service Audits".

## Module layout

| File | Layer | Responsibility |
|------|-------|---------------|
| `src/cljc/lipas/data/ptv.cljc` | Pure data | Payload builders, language mapping, sync review (`sync-review`, `annotate-review`, `review-writes`), organization config, `->service-document`. CLJ + CLJS so the same code runs in tests and in the browser preview. |
| `src/cljc/lipas/schema/sports_sites/ptv.cljc` | Schema | Malli schemas for the `:ptv` metadata block. `{:closed true}` — strict; new fields must be added explicitly. |
| `src/cljc/lipas/schema/ptv.cljc` | Schema | Malli schemas for Service audits and the `ptv_service` document (reuses the audit schemas above). |
| `src/clj/lipas/backend/db/ptv_service.clj` | DB accessors | Append-only `ptv_service` table (LIPAS-managed Service content + audits); `ptv_service_current` view queries. |
| `src/clj/lipas/backend/ptv/integration.clj` | API client | HTTP, OAuth tokens, all PTV endpoint wrappers. Knows nothing about LIPAS data shape. |
| `src/clj/lipas/backend/ptv/core.clj` | Orchestration | Upsert flows, DB writes via the sports-site event log, service↔channel link diffing. The "what to do when a sync runs" lives here. |
| `src/clj/lipas/backend/ptv/handler.clj` | HTTP routes | REST endpoints under `/actions/*`. Thin — delegates to core. |
| `src/clj/lipas/backend/ptv/ai.clj` | AI client | Description generation against Gemini. See `ptv-ai-integration.md`. |
| `src/clj/lipas/backend/ptv/eval.clj` | Eval harness | Offline evaluation of AI prompt versions. |
| `src/cljs/lipas/ui/ptv/views.cljs` | UI views | Wizard, PALVELUT and LIIKUNTAPAIKAT tabs, audit dashboard. |
| `src/cljs/lipas/ui/ptv/site_view.cljs` | UI views | PTV tab inside the sports-site editor (separate from the PTV dialog). |
| `src/cljs/lipas/ui/ptv/components.cljs` | UI primitives | Reusable components — service-location preview. |
| `src/cljs/lipas/ui/ptv/review.cljs` | UI component | The sync review panel, used by the Liikuntapaikat tab, the wizard and the site form. |
| `src/cljs/lipas/ui/ptv/review_data.cljs` | UI state | Review state paths, the site form's review, row labels. Free of UI so both the :ptv and :map modules can load it. |
| `src/cljs/lipas/ui/ptv/events.cljs` / `subs.cljs` | Re-frame | Wizard state, sync triggers, app-db subscriptions. |
| `src/cljc/lipas/i18n/{fi,se,en}/ptv*.edn` | Translations | UI text. Each `:ptv.*` namespace has its own EDN file. |

## Sync semantics in detail

### Coverage rule

PTV's PUT endpoints replace list fields like `serviceChannelNames` and
`serviceChannelDescriptions` wholesale. PTV cross-validates that every language
declared in the resource's `:languages` set is covered in those required
lists. The LIPAS payload therefore always emits a complete entry per declared
language.

It is technically possible to omit `serviceChannelNames` from a PUT entirely
(PTV will keep the stored list), but this was rejected because it would
quietly preserve kunta-side edits, contradicting principle #3. Keeping a
PTV-side value is an explicit choice in the sync review instead.

### Per-language fallback (in detail)

For Name (sv/en) and for Summary/Description (sv/en):

| LIPAS-side value | What's pushed |
|------------------|---------------|
| Non-blank value entered | The entered value |
| Empty string / nil / whitespace | The Finnish value |

For `AlternativeName fi` (LIPAS `:marketing-name`):

| LIPAS-side value | What's pushed |
|------------------|---------------|
| Non-blank | The marketing name |
| Blank / nil | No `AlternativeName` entry at all |

This rule is implemented once in `->ptv-service-location` and once in
`effective-lipas-name` / `effective-lipas-text` for the sync review. The two
sites must stay aligned — the review's idea of "what LIPAS will push" must
match what the builder actually pushes.

### Create vs update

- **Create** (no `:service-channel-ids` stored): POST. PTV assigns a new UUID;
  we capture it.
- **Update** (channel-ids present): PUT to the stored UUID. PTV may return a
  different UUID (it re-versions resources); we replace our stored copy with
  what came back.
- **Archive** (LIPAS site marked `out-of-service-permanently` or
  `incorrect-data`, or no longer a PTV candidate): PUT with
  `publishingStatus: "Deleted"` and clear the local channel-ids on success.

The Service-side flow is similar but adds an *adoption* path: when linking a
LIPAS sub-category to an existing PTV Service, PUT by UUID (not source-id).
This adoption path currently uses a fetch+merge to preserve PTV-managed
metadata (ontology, classes); see Trade-offs.

### Connection updates

Service↔Channel links are managed by a separate PTV endpoint
(`/v11/Connection/serviceId/{id}`). The upsert flow:

1. Reads the channel's current `:services` from the GET response we already
   have on hand (fetched for connection diffing).
2. Computes added/removed service IDs vs the LIPAS-stored set.
3. Patches the differences via Connection PUTs, one per service.

## Sync review model

Plan, as-built notes and the end-to-end test pass:
[`wip/ptv-drift-review-plan.md`](wip/ptv-drift-review-plan.md).

### What's compared

Per LIPAS sports site, with the cached PTV channel:

| Field | LIPAS side | PTV side |
|-------|------------|----------|
| Name (per language) | site `:name`, `:name-localized {:se :en}` | `serviceChannelNames` Name entries |
| AlternativeName (fi) | site `:marketing-name` | `serviceChannelNames` AlternativeName/fi |
| Summary (per language) | site `:ptv :summary {:fi :se :en}` | `serviceChannelDescriptions` Summary entries |
| Description (per language) | site `:ptv :description {:fi :se :en}` | `serviceChannelDescriptions` Description entries |
| Service connections | site `:ptv :service-ids` | channel's `:services` IDs |

The LIPAS side is the *saved* site's effective value (with fi-fallback
applied), so the diff reflects what the next sync will actually do. The third
side, the *base*, is what the last sync sent: the first stored revision
carrying the site's current `:ptv :last-sync` (`get-ptv-sync-bases`, matched on
the stored string), run through the same derivation. No new storage.

### Rows

`sync-review` returns `{:rows [...] :matching [...] :services {...} :base?
:first-link?}`. Each row is one difference with an `:origin`:

- `:ptv` — PTV changed it since the last sync (LIPAS has the base value)
- `:lipas` — LIPAS changed it; the sync will send it, no choice needed
- `:both` — both changed it (a conflict)
- `:first-link` — the location was made in PTV or elsewhere and is being
  linked for the first time; texts need a choice, connections are kept
  (`link-service-ids`)
- `:unknown` — no base to tell by

A language LIPAS adds (`:language-added?`) needs no choice. `annotate-review`
adds what the edit holds now (`:current` `:lipas` / `:ptv` / `:edited`).
`review-choice-required?` says which rows need a decision; `open-review-rows`
which of those have none yet.

### Choices write into the edit

Taking a version writes it into the edit (`review-writes`,
`apply-review-writes`), so the form shows the result at once; keeping LIPAS's
version is recorded as a choice. Rows stay, compared with the saved site,
until the next sync. Taking PTV's name or marketing name renames the site: the
site form edits the field like any other; the PTV tool and the wizard send the
changed names as `:site-names` with the sync, and `upsert-ptv-service-location!`
writes them into the site in the same revision. That needs `:site/create-edit`
(403 `:no-permission` otherwise).

### How it is surfaced

- A colored "PTV" chip on the site card shows the sync status
  (`:ok`, `:out-of-date`, `:content-drift`, `:not-synced`).
- The review panel (`lipas.ui.ptv.review/review-panel`) in the PTV tool's
  Liikuntapaikat tab (plus a "Muutettu PTV:ssä" filter), in wizard step 3 and
  on the site form's PTV tab: collapsed to a bar, Lipas and PTV cards per row
  with a word diff, a "Liitetyt palvelut" block for connections, bulk buttons.
- Gates: "Synkronoi nyt" and the site form's save ask before overwriting an
  undecided change made in PTV; the wizard export is blocked until each has a
  choice.
- Translation namespace: `:ptv.review/*` in `i18n/{fi,se,en}/ptv_review.edn`.

### What is *not* reviewed

- Addresses, phone numbers, email, web pages, opening hours — derived from
  LIPAS site data; if a kunta edits these in PTV they will be overwritten on
  next sync without warning. This is acceptable today because kuntien rarely
  edit these and the alternative (reviewing every PTV field) adds noise
  without proportional benefit.
- Service-side drift (per-language `serviceNames`, descriptions on a
  Service). Planned to mirror the channel-side review in the Palvelut tab.

## Sync states

Each integrated site has a sync-status derived in `sports-site->ptv-input`:

| Status | Meaning |
|--------|---------|
| `:not-synced` | Site is sync-enabled but has never been pushed to PTV. |
| `:ok` | LIPAS and PTV are in agreement and the site hasn't been edited since the last sync. |
| `:out-of-date` | LIPAS has been edited since the last sync (event-date > last-sync). Next save will sync. |
| `:content-drift` | The sync review has a difference that needs the user's decision: made in PTV, on a first-linked location, or of unknown origin. Stays until the sync, also once decided. |

`:out-of-date` also covers a site whose edit differs from PTV with nothing to
decide (a LIPAS-side change, or a choice already made). The review panel shows
the differences in either case.

## Sports-site → PTV candidate gating

A LIPAS sports site is a PTV candidate when:

- `:status` is not `incorrect-data` or `out-of-service-permanently`
- `:owner` is `city` or `city-main-owner`
- `:type-code` is not 7000 (Huoltorakennus) or 207 (Opastuspiste)

A candidate is *ready* when its Finnish summary and description both have
content (>5 chars). Ready candidates with `:sync-enabled true` participate in
auto-sync.

## Trade-offs and known limitations

### LIPAS users cannot edit Swedish-version-only PTV content

Under strict 1-way, the way for a kunta to have a custom Swedish name in PTV
is to enter it in LIPAS (`:name-localized :se`). A value edited in PTV
directly is overwritten by the next sync unless a user takes it into LIPAS in
the sync review. This is by design: it removes ambiguity about where the value
lives.

The same applies to descriptions per language and to marketing-name.

### Adoption flow

Today, "adopting" an existing PTV Service into LIPAS preserves PTV-managed
metadata (ontology, classes) via a fetch+merge in `upsert-ptv-service!`.
This is a transitional state. The principled solution — pull selected fields
into LIPAS once at adoption time, then resume strict 1-way — is planned (see
Future Direction).

For ServiceChannels, linking a site to an existing ServiceLocation is a
*first link*: the location keeps its PTV connections (`link-service-ids`), and
the sync review shows its texts against LIPAS's so the user can take them.

### Service descriptions: PTV authoritative, LIPAS shadow

PTV Services now have a thin LIPAS entity: the append-only `ptv_service`
table records the LIPAS-managed subset (names, descriptions, languages,
publishing status) as a revision on every successful Service write, plus
Service audits. However, *drafts* are still ephemeral: descriptions authored
in the PALVELUT tab live only in re-frame app-db (`service-candidates`) until
synced, and a browser refresh before sync loses unsaved Service edits.
Durable drafts and adoption-time pull can extend the same `ptv_service`
document later.

### No PTV-side validation feedback in LIPAS at edit time

The user only learns about a PTV validation failure (e.g. a description
exceeding the 150-char Summary limit) when they click sync. LIPAS does
client-side validation for the per-field length limits documented in
`lipas.data.ptv/max-*-length`, but the full set of PTV constraints is wider.

### Service connection updates are non-atomic

Adding/removing service links happens via individual Connection PUTs. A
failure midway leaves the connections partially updated. The next sync
reconciles, but a transient incomplete state is observable.

## Future direction

### Adoption-time pull

When a kunta links a LIPAS sub-category to an existing PTV Service, pull a
fixed allowlist of fields once into LIPAS DB: descriptions per language.
(Names are sub-category-derived in LIPAS, so don't pull.) For ServiceChannels
the first-link sync review does this per field.

After the pull, the LIPAS event log records this as a regular site revision
("imported from PTV at T") and normal 1-way push resumes. This addresses the
kunta workflow of "we already have PTV content; don't make us re-author it."

For Services, the `ptv_service` table is the natural home for the pulled
content — the adopt path already writes an initial revision there.

### Service-side strict-1-way alignment

Apply the same strict-1-way model + sync review to PTV Services. Today the Service-side `upsert-ptv-service!` retains a fetch+merge
that's load-bearing only for the adoption case; once adoption-time pull
exists, the merge can be removed and Service-side joins the same model as
ServiceChannels.

### Review in the audit view

The Liikuntapaikat tab, the wizard and the site form show the review; the
audit dashboard list shows only a status chip. Showing the review there is a
candidate enhancement.

### Telemetry on overwrite events

Log a structured event each time a sync overwrites a non-empty PTV-side
value. This gives us data on how often kuntien actually edit in PTV — useful
for prioritizing the adoption-pull feature and for deciding whether more
fields warrant review.

## See also

- [`integration-ptv.md`](integration-ptv.md) — implementation details, sequence
  diagrams, sample payloads, full endpoint reference.
- [`integration-ptv-audit.md`](integration-ptv-audit.md) — audit workflow.
- [`ptv-ai-integration.md`](ptv-ai-integration.md) — AI description generation.
- [`context-ptv.md`](context-ptv.md) — code-level patterns, intended for LLM
  context.
