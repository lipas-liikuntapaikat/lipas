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

1. **Two-way sync.** PTV-side edits to LIPAS-managed fields are not propagated
   back to LIPAS. Kuntien who want a change to stick make it in LIPAS.
2. **Real-time sync.** Sync is event-driven (on save) or manual (button). There
   is no background poller, no webhook listener.
3. **Conflict resolution.** Concurrent edits in LIPAS and PTV resolve via
   last-write-wins, which under steady state is always LIPAS.
4. **Mirroring PTV's full data model.** Only fields LIPAS understands and can
   author are integrated. PTV-only metadata (e.g. opening hours in PTV's full
   form) is left untouched.
5. **Bringing PTV state into LIPAS for display elsewhere.** PTV state is read
   for drift detection only; it is not surfaced as "live PTV content" anywhere
   else in LIPAS.

## Core principles

### 1. LIPAS is master

For every PTV field LIPAS integrates, LIPAS is the source of truth. PTV is a
publishing target. This is the foundational rule everything else follows from.
The original integration design called for one-way LIPAS→PTV sync; this is
preserved.

### 2. One-way write

Sync is exclusively LIPAS → PTV. The system never writes PTV state back into
LIPAS DB. PTV is treated as a downstream replica with respect to LIPAS-managed
fields.

### 3. Drift visibility, not drift resolution

When PTV state diverges from what LIPAS will push (typically because a kunta
edited the PTV record directly), the integration *shows* the divergence in the
LIPAS UI but does not resolve it by accepting the PTV-side value. The user
resolves drift by editing LIPAS to match what they wanted, knowing the next
sync overwrites PTV.

This is the most consequential principle. It rules out the mid-way "preserve
PTV-side edits" patterns the system briefly experimented with — those quietly
hide PTV-side state from the LIPAS user, which is a worse failure mode than
overwriting it visibly.

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

### Inbound: drift detection only (PTV → cache)

1. Periodically (or on UI load) the backend fetches the org's PTV
   ServiceChannels and Services via PTV list endpoints.
2. Responses are cached server-side per organization, keyed by PTV UUID.
3. The frontend reads from this cache through subscriptions.
4. `compute-service-channel-drift` (cljc) compares each LIPAS site's
   *would-be-pushed* values to the cached PTV-side values, returning a
   structured per-field diff.

PTV state is read for visibility only. **It is never written back into the
LIPAS DB.** This is what differentiates "drift visibility" from "two-way
sync."

## Module layout

| File | Layer | Responsibility |
|------|-------|---------------|
| `src/cljc/lipas/data/ptv.cljc` | Pure data | Payload builders, language mapping, drift detection, organization config. CLJ + CLJS so the same code runs in tests and in the browser preview. |
| `src/cljc/lipas/schema/sports_sites/ptv.cljc` | Schema | Malli schemas for the `:ptv` metadata block. `{:closed true}` — strict; new fields must be added explicitly. |
| `src/clj/lipas/backend/ptv/integration.clj` | API client | HTTP, OAuth tokens, all PTV endpoint wrappers. Knows nothing about LIPAS data shape. |
| `src/clj/lipas/backend/ptv/core.clj` | Orchestration | Upsert flows, DB writes via the sports-site event log, service↔channel link diffing. The "what to do when a sync runs" lives here. |
| `src/clj/lipas/backend/ptv/handler.clj` | HTTP routes | REST endpoints under `/actions/*`. Thin — delegates to core. |
| `src/clj/lipas/backend/ptv/ai.clj` | AI client | Description generation against Gemini. See `ptv-ai-integration.md`. |
| `src/clj/lipas/backend/ptv/eval.clj` | Eval harness | Offline evaluation of AI prompt versions. |
| `src/cljs/lipas/ui/ptv/views.cljs` | UI views | Wizard, PALVELUT and LIIKUNTAPAIKAT tabs, audit dashboard. |
| `src/cljs/lipas/ui/ptv/site_view.cljs` | UI views | PTV tab inside the sports-site editor (separate from the PTV dialog). |
| `src/cljs/lipas/ui/ptv/components.cljs` | UI primitives | Reusable components — service-location preview, drift panel. |
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
quietly preserve kunta-side edits, contradicting principle #3.

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
`effective-lipas-name` / `effective-lipas-text` for drift detection. The two
sites must stay aligned — drift detection's idea of "what LIPAS will push"
must match what the builder actually pushes.

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

## Drift detection model

### What's compared

Per LIPAS sports site, with the cached PTV channel:

| Field | LIPAS side | PTV side |
|-------|------------|----------|
| Name (per language) | site `:name`, `:name-localized {:se :en}` | `serviceChannelNames` Name entries |
| AlternativeName (fi) | site `:marketing-name` | `serviceChannelNames` AlternativeName/fi |
| Summary (per language) | site `:ptv :summary {:fi :se :en}` | `serviceChannelDescriptions` Summary entries |
| Description (per language) | site `:ptv :description {:fi :se :en}` | `serviceChannelDescriptions` Description entries |
| Service link membership | site `:ptv :service-ids` set | channel's `:services` IDs |

The LIPAS side is the *effective* value (with fi-fallback applied), so the
diff reflects what the next sync will actually do.

### Output shape

`compute-service-channel-drift` returns a vector of entries:

```clojure
[{:field :name :type "Name" :language "sv" :locale :se
  :lipas "Halli" :ptv "KUNTA-Hall"}
 {:field :marketing-name :type "AlternativeName" :language "fi" :locale :fi
  :lipas "Hallikauppa" :ptv nil}
 {:field :summary :type "Summary" :language "sv" :locale :se
  :lipas "summary-fi" :ptv "kunta-summary-sv"}
 {:field :services
  :lipas #{"svc-a"} :ptv #{"svc-a" "svc-b"}
  :added #{"svc-b"} :removed #{}}]
```

`sports-site->ptv-input` exposes this on the `:drift-fields` key and sets
`:sync-status :content-drift` when any drift exists.

### How drift is surfaced

- A colored "PTV" chip on the site card shows the sync status
  (`:ok`, `:out-of-date`, `:content-drift`, `:not-synced`).
- The open service-location card includes a `drift-panel` component (see
  `lipas.ui.ptv.components/drift-panel`) listing each drifted field with its
  LIPAS value and PTV value side-by-side, plus a warning ("next sync will
  overwrite these") and instruction ("update LIPAS to match").
- Translation namespace: `:ptv.drift/*` in `i18n/{fi,se,en}/ptv_drift.edn`.

### What is *not* drift-detected

- Addresses, phone numbers, email, web pages, opening hours — derived from
  LIPAS site data; if a kunta edits these in PTV they will be overwritten on
  next sync without warning. This is acceptable today because kuntien rarely
  edit these and the alternative (drift detection on every PTV field) adds
  noise without proportional benefit.
- Service-side drift (per-language `serviceNames`, descriptions on a
  Service). This is a known gap; planned to mirror the channel-side approach.

## Sync states

Each integrated site has a sync-status derived in `sports-site->ptv-input`:

| Status | Meaning |
|--------|---------|
| `:not-synced` | Site is sync-enabled but has never been pushed to PTV. |
| `:ok` | LIPAS and PTV are in agreement and the site hasn't been edited since the last sync. |
| `:out-of-date` | LIPAS has been edited since the last sync (event-date > last-sync). Next save will sync. |
| `:content-drift` | PTV-side state differs from what LIPAS would push. The drift panel itemizes the differences. |

Note: a site can be both out-of-date *and* drifted — the drift panel still
shows the diff regardless.

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

Under strict 1-way, the only way for a kunta to have a custom Swedish name in
PTV is to enter it in LIPAS (`:name-localized :se`). They cannot edit the PTV
field directly — it gets overwritten. This is by design: it removes ambiguity
about where the value lives.

The same applies to descriptions per language and to marketing-name.

### Adoption flow

Today, "adopting" an existing PTV Service into LIPAS preserves PTV-managed
metadata (ontology, classes) via a fetch+merge in `upsert-ptv-service!`.
This is a transitional state. The principled solution — pull selected fields
into LIPAS once at adoption time, then resume strict 1-way — is planned (see
Future Direction).

For ServiceChannels, no adoption flow exists today. If a kunta has a PTV
ServiceLocation they want LIPAS to take over, the channel is recreated rather
than adopted.

### PTV as the authoritative store for Service descriptions

PTV Services don't have a corresponding LIPAS entity. Descriptions are
authored in the PALVELUT tab and exist only ephemerally in re-frame app-db
(`service-candidates`) until synced. A browser refresh before sync loses
unsaved Service edits. Pragmatic but not great; planned to be addressed
alongside Service-side strict-1-way alignment.

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

When a kunta links a LIPAS sub-category to an existing PTV Service (or wants
LIPAS to adopt an existing ServiceChannel), pull a fixed allowlist of fields
once into LIPAS DB:

- For ServiceChannels: localized names (sv, en), descriptions (sv, en).
- For Services: descriptions per language. (Names are sub-category-derived in
  LIPAS, so don't pull.)

After the pull, the LIPAS event log records this as a regular site revision
("imported from PTV at T") and normal 1-way push resumes. This addresses the
kunta workflow of "we already have PTV content; don't make us re-author it."

### Service-side strict-1-way alignment

Apply the same strict-1-way model + drift detection + drift panel to PTV
Services. Today the Service-side `upsert-ptv-service!` retains a fetch+merge
that's load-bearing only for the adoption case; once adoption-time pull
exists, the merge can be removed and Service-side joins the same model as
ServiceChannels.

### Drift visibility in audit and wizard list views

Currently only the open site card surfaces the per-field diff. The audit
dashboard list shows only a status chip; expanding it to the full diff
in-line is a candidate enhancement.

### Telemetry on overwrite events

Log a structured event each time a sync overwrites a non-empty PTV-side
value. This gives us data on how often kuntien actually edit in PTV — useful
for prioritizing the adoption-pull feature and for deciding whether more
fields warrant drift detection.

## See also

- [`integration-ptv.md`](integration-ptv.md) — implementation details, sequence
  diagrams, sample payloads, full endpoint reference.
- [`integration-ptv-audit.md`](integration-ptv-audit.md) — audit workflow.
- [`ptv-ai-integration.md`](ptv-ai-integration.md) — AI description generation.
- [`context-ptv.md`](context-ptv.md) — code-level patterns, intended for LLM
  context.
