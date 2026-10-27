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
user, which is a worse failure mode than overwriting it visibly. A PTV-side
value of a field LIPAS has survives a sync only because a user chose it (or,
on a first link, it is one of the location's connections, listed in the
review), and it then lives in LIPAS. What LIPAS has no field for, it doesn't
touch: opening hours and postal addresses stay as PTV has them (see "What PTV
keeps").

Every field a sync writes is reviewed, as the field registry
(`lipas.data.ptv-review/review-fields`) lists them: names, texts, contacts,
web pages, the visiting address, languages and connections. The location's
point and municipality are shown for information only (D4).

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
isolation. The sync review (`lipas.data.ptv-review`, cljc) projects the same
payload for the same reason: it compares what the sync will send, not the
site.

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

1. **Trigger.** On a sports-site save (when `:sync-enabled true`) by a user
   who manages PTV for the site's municipality (`:ptv/manage`,
   `lipas.backend.core/ptv-sync-by?`); manual from the LIIKUNTAPAIKAT tab
   ("Synkronoi nyt", "Synkronoi kaikki valmiit"); or batch from the wizard.
   Another editor's save stays in LIPAS (see "Who writes to PTV").
2. **Build payload.** `->ptv-service-location` produces a fully-formed PTV PUT
   body: every declared language has Name, displayNameType entry, Summary, and
   Description; addresses, services, and contact info are derived from LIPAS
   site data.
3. **Checks.** The upsert GETs the live channel and refuses with 409
   `:ptv-changed-since-review` when it changed after the review the user made
   (see "Stale review"). A site whose type changed gets its service
   connections re-bound to the new sub-category's services
   (`rebind-on-type-change`, `lipas.data.ptv/rebound-service-ids`).
   PTV's postal addresses from the GET are appended to the payload.
4. **PUT or POST.** PUT if `:service-channel-ids` exists; POST otherwise.
5. **Connection diff.** For updates, the upsert reads the channel's current
   service link set and patches added/removed services via the Connection
   endpoint.
6. **Persist.** Store the returned PTV state (new IDs if any, sourceId,
   publishing-status, last-sync timestamp, and PTV's `:modified` as
   `[:ptv :ptv-modified]`) back into the sports-site's `:ptv` metadata, with
   the review's `:site-patch` (values taken from PTV). The sports site is
   rewritten as a new event-log revision.
7. **Index.** Re-index the site in Elasticsearch with the updated PTV meta.

The payload is built from LIPAS state alone, except for the postal addresses
LIPAS has no field for, which are re-sent as PTV has them.

### Inbound: the sync review (PTV → cache)

1. On UI load the backend fetches the org's PTV ServiceChannels and Services
   via PTV list endpoints and returns them to the frontend.
2. The frontend caches the responses in re-frame app-db per organization,
   keyed by PTV UUID. (There is no server-side cache.) It also fetches each
   synced site's *base*, what its last sync sent
   (`/actions/fetch-ptv-sync-bases`); the site form fetches the base and the
   live channel for its one site (`/actions/fetch-ptv-site-review`). The
   PALVELUT tab fetches each service's base from the `ptv_service` mirror
   (`/actions/fetch-ptv-service-bases`).
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
non-LIPAS-managed Service fields (target groups, area, producers, ...). See
`integration-ptv-audit.md` → "Service Audits". The latest revision is also
the base of the PALVELUT tab's review (`ptv-review/service-review`).

## Module layout

| File | Layer | Responsibility |
|------|-------|---------------|
| `src/cljc/lipas/data/ptv.cljc` | Pure data | Payload builders, language mapping, organization config, `->service-document`, `rebound-service-ids`, `ptv-postal-addresses`. CLJ + CLJS so the same code runs in tests and in the browser preview. |
| `src/cljc/lipas/data/ptv_review.cljc` | Pure data | The sync review: field registry (`review-fields`), payload projection (`location-values`), `sync-review`, `annotate-review`, `review-writes`, `review-site-patch`, sync statuses (`sports-site->ptv-input`), and `service-review` for the PALVELUT tab. |
| `src/cljc/lipas/tm35fin.cljc` | Pure data | WGS84 → TM35FIN in cljc, so the review compares the location's point in the same metres the payload sends. |
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
| `src/cljs/lipas/ui/ptv/review.cljs` | UI component | The sync review panel, used by the Liikuntapaikat tab, the wizard, the site form and the PALVELUT tab. |
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

A language version PTV has and LIPAS doesn't publish (not enabled for the
org, or without LIPAS texts) is removed by the sync. The review lists it as
information (D6); keeping it means enabling the language through LIPAS
support.

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
  `incorrect-data`, or no longer a PTV candidate): a status-only PUT with
  `publishingStatus: "Deleted"`, clearing the local channel-ids on success.
  Only a PTV manager archives: another editor's removal leaves the location
  published, and the PTV tool lists it as "Odottaa arkistointia"
  (`/actions/get-ptv-awaiting-archive`) until a PTV manager archives it
  (`/actions/archive-ptv-site`).

The Service-side flow is similar but adds an *adoption* path: when linking a
LIPAS sub-category to an existing PTV Service, PUT by UUID (not source-id).
This adoption path currently uses a fetch+merge to preserve PTV-managed
metadata (ontology, classes); see Trade-offs.

A Service's sync sends the PALVELUT tab's edit: PTV's own names and texts
with the user's edits over them, so only an edited field overwrites PTV
(`->ptv-service` prefers the edited Finnish name, then PTV's, then the
sub-category's). Service classes and ontology terms of a LIPAS-created
Service come from the sub-category.

### Connection updates

Service↔Channel links are managed by a separate PTV endpoint
(`/v11/Connection/serviceId/{id}`). The upsert flow:

1. Reads the channel's current `:services` from the GET response we already
   have on hand (fetched for connection diffing).
2. Computes added/removed service IDs vs the LIPAS-stored set.
3. Patches the differences via Connection PUTs, one per service.

## Sync review model

Plans and as-built notes: [`wip/ptv-drift-review-plan.md`](wip/ptv-drift-review-plan.md)
(first round) and [`wip/ptv-sync-review-v2-plan.md`](wip/ptv-sync-review-v2-plan.md)
(every field, evidence, roles, stale review, services).

### What's compared

Per LIPAS sports site, three ways: L, what the next sync sends (the saved
site's payload, projected by `location-values`); P, PTV's channel (the cached
list entry, or a fresh GET); B, the *base*, what the last sync sent: the
first stored revision carrying the site's current `:ptv :last-sync`
(`get-ptv-sync-bases`, matched on the stored string), projected the same way.
No new storage.

The fields are the registry `ptv-review/review-fields`, grouped as in the
panel:

| Group | Fields | Kind |
|-------|--------|------|
| Nimi ja kuvaukset | name, marketing name, summary, description (per language) | text |
| Yhteystiedot | phone, email, www, reservations link | one slot (D5) |
| | other web pages | list |
| Osoite ja sijainti | visiting address | one slot |
| | municipality, point (> 50 m in TM35FIN) | information (D4) |
| Kieliversiot | languages PTV has and LIPAS won't publish | information (D6) |
| Liitetyt palvelut | service connections | connection |

The comparison is on the projected values (`comparable`, `same-value?`), so
PTV's own normalisation (whitespace, phone formatting, URL case) is never a
row.

### Evidence

A base that differs from PTV doesn't prove PTV changed: a change in how
LIPAS derives the payload makes it differ too. `ptv-changed-since-sync?`
compares PTV's `:modified` with `[:ptv :ptv-modified]`, the value PTV
reported right after the last sync (stored on every sync). For a site synced
before that was stored, a `:modified` over 2 minutes after the sync counts.
An unreadable or missing time counts as a change: asking is safer than
overwriting. Without evidence a P ≠ B difference reads as LIPAS's.

### Rows

`sync-review` returns `{:rows [...] :matching [...] :services {...} :base?
:first-link? :evidence? :postal-addresses ...}`. Each row is one difference
with an `:origin`:

- `:ptv` — PTV changed it since the last sync (LIPAS has the base value)
- `:lipas` — LIPAS changed it; the sync will send it, no choice needed
- `:both` — both changed it (a conflict)
- `:first-link` — the location was made in PTV or elsewhere and is being
  linked for the first time; texts need a choice, connections are kept
  (`link-service-ids`)
- `:unknown` — no base to tell by

Information rows (`info-kinds`: municipality, point, language removals) never
need a choice. A language LIPAS adds needs none either. `annotate-review`
adds what the edit holds now (`:current` `:lipas` / `:ptv` / `:edited`).
`review-choice-required?` says which rows need a decision; `open-review-rows`
which of those have none yet.

### Choices write into the edit

Taking a version writes it into the edit (`review-writes`,
`apply-review-writes`), so the form shows the result at once; keeping LIPAS's
version is recorded as a choice. A value of the user's own (typed in the form,
or written there by AI generation) counts only once confirmed ("Vie muokattu
versio PTV:hen", `edited-choice`). Every recorded choice holds only while PTV
still has the value it was made against (`:seen`) and the edit still holds
what was chosen; taking PTV's version needs no record, but once PTV changes
that value again, the edit holds neither side and the row asks again. So
nothing decided against an older PTV value is sent over a newer one
(`review-choice`, `review-decided?`). A one-slot field with several PTV values
takes the one picked; the rest leave PTV with the sync. Until something is
picked the row is open, even when one of PTV's values is LIPAS's own: PTV's
others are a change too. Taking LIPAS's own value from PTV's list counts as
keeping LIPAS's version (`lipas-value?`). Rows stay, compared with the saved
site, until the next sync.

Taking PTV's name, contacts or address changes the site itself. The site
form edits the fields like any other; the PTV tool and the wizard send them
as `:site-patch` with the sync (`review-site-patch`; a closed schema of the
paths the registry allows, 400 for any other), and
`upsert-ptv-service-location!` writes them into the site in the same
revision. That needs `:site/create-edit` (403 `:no-permission` otherwise).

### How it is surfaced

- The status chip in the PTV tool's Liikuntapaikat tab (see "Sync states"),
  with status filters and a banner counting the sites that await a choice
  ("Näytä ne").
- The review panel (`lipas.ui.ptv.review/review-panel`) in the Liikuntapaikat
  tab, wizard step 3, the site form's PTV tab and the PALVELUT tab: a bar with
  the state and dates, notices (PTV draft, archived, double link), the rows by
  group with Lipas and PTV cards and a word diff, what LIPAS changes, what is
  up to date, bulk buttons and the outcome.
- Gates: "Synkronoi nyt" and the site form's save ask before overwriting an
  undecided change made in PTV; "Synkronoi kaikki valmiit" skips the sites
  with one and lists them; the wizard export waits until each has a choice.
- Translation namespace: `:ptv.review/*` in `i18n/{fi,se,en}/ptv_review.edn`.

### Who writes to PTV

Only a user with `:ptv/manage` for the site's municipality (D2). Another
editor's save, removal or delete is stored in LIPAS only: the site shows as
"Valmis synkronoitavaksi" in the PTV tool, a removed one as "Odottaa
arkistointia", for a PTV manager to sync or archive. Their save shows no
review and no dialog; the delete dialog's text follows the user's rights.
`save-sports-site!` decides this on the backend (`ptv-sync-by?`), not the
UI.

### Stale review

The client sends the channel its review was made against as `:reviewed
{:modified :service-ids}`. If the live channel has a newer `:modified`, or
other connections, the sync is refused (409 `:ptv-changed-since-review`): the
PTV tool refetches and shows the review again; the site form's save is stored,
its sync skipped and the error stored on the site.

A sync without `:reviewed` (a quick edit in the search table, a status change
from the delete dialog, a resurrect, a site-form save whose review couldn't be
fetched, an older browser tab, a bulk edit) runs the review on the server
(`check-nothing-unreviewed!`): the site as stored before this sync against
its base and the fresh GET, with what the sync sends as the edit. If a row
still needs a decision, the sync is refused (`:ptv-not-reviewed`): 409 from
the PTV tool's endpoint, or on the save path the save is stored, the sync
skipped and the error stored with `:at`, the event-date of its revision, so
the UI tells this save's refusal from an earlier one and says so. While PTV
holds nothing undecided such a save syncs as before.

### What PTV keeps

- Opening hours: LIPAS sends none, and PTV keeps its own.
- Postal addresses: LIPAS has no field for them, and every sync used to
  delete them. The sync re-sends PTV's postal addresses from the pre-sync GET
  (D8); one PTV doesn't accept back through its API is dropped and the review
  says so (`ptv-postal-addresses` → `:dropped`).
- On the visiting address, PTV's street names in other languages (with its
  separate street number) and its additional information; on the phone, its
  additional information and charge. LIPAS has one street and one number
  and none of these, and the sync used to delete them. They are sent back
  while PTV's address is the one LIPAS sends (same street and postal code,
  as the review compares them) and the phone has the number LIPAS sends
  (`with-ptv-extras`). Once LIPAS sends another address or number, they
  belonged to the old one and go. Not kept: an address's entrances.

### Services (PALVELUT tab)

`ptv-review/service-review` compares a service's edit (PTV's names and texts
with the user's unsaved edits over them) with PTV's current service and the
base, the latest `ptv_service` revision. Only an edited field can overwrite
PTV: a row is the user's edit (`:lipas`), or `:both` when PTV changed the
field too since LIPAS's last write, a choice to make before syncing. Fields
PTV changed and the user didn't edit are kept and listed. The chips are the
sites' (`:blocked` for a PTV draft, `:awaiting-choice`, `:ready`, `:ok`). A
service LIPAS hasn't written since the mirror existed has no base, and every
edit reads as LIPAS's. The services are loaded with the tool, and the edit
holds PTV's texts as they were then: a sync sends `:expected-modified`, and
a service changed in PTV since is refused (`:changed-in-ptv`), after which
the tool reloads the services and the review shows the change.

## Sync states

Each integrated site has a sync status derived in `sports-site->ptv-input`
(`ptv-review/sync-statuses`):

| Status | Chip | Meaning |
|--------|------|---------|
| `:not-synced` | Ei viety | Never exported to PTV. |
| `:blocked` | Estetty | A sync can't go through: PTV holds an unpublished draft of the location, or the last sync failed. PTV's listing shows only the published version, so a draft is known once the row is opened (the refetch, and the site form's review fetch, also ask PTV's `/active/` endpoint: `with-draft-status`) or a sync has hit it. |
| `:awaiting-choice` | Odottaa valintaa | A change made in PTV waits for the user's choice. |
| `:ready` | Valmis synkronoitavaksi | The next sync has something to send, with nothing to decide (a LIPAS change, a choice made, a language added or removed, a link removed, an archived location re-enabled, an unsaved edit in the PTV tool). |
| `:ok` | Ajan tasalla | LIPAS and PTV agree and the site hasn't been edited since the last sync. |

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

### Service-side alignment

The PALVELUT tab has the sync review (see "Services"), but on its own model:
the edit starts from PTV's names and texts, so PTV's changes survive unless
edited, where a site's sync sends LIPAS's values and asks about PTV's
changes. The Service-side `upsert-ptv-service!` also retains a fetch+merge
that's load-bearing only for the adoption case; once adoption-time pull
exists, the merge can be removed, and whether Services should follow the
sites' model can be decided then.

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
