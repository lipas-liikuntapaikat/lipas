# PTV Integration - Technical Documentation

## Overview

LIPAS integrates with Finland's Palvelutietovaranto (PTV, Service Information Repository) to publish municipal sports facilities as PTV ServiceLocations and their groupings as PTV Services. The integration supports AI-generated descriptions, multi-language content (fi/sv/en), and bidirectional drift detection.

## Architecture

### Conceptual Mapping

```
LIPAS                          PTV
----                           ---
Sub-category (e.g. Pallokentät) → Service (Palvelu)
Sports site (e.g. Rantakylän   → ServiceLocation (Palvelupaikka)
  beach volley -kenttä 1)
```

- One PTV **Service** per LIPAS sub-category per organization
- One PTV **ServiceLocation** per LIPAS sports site
- A ServiceLocation links to one or more Services

### File Structure

**Backend** (`src/clj/lipas/backend/ptv/`)
- `integration.clj` — PTV API client: HTTP, auth tokens, all endpoint wrappers
- `core.clj` — Sync logic, DB operations, upsert flows
- `handler.clj` — HTTP route handlers (15 endpoints)
- `ai.clj` — AI model registry, prompt templates (v5), response schemas

**Frontend** (`src/cljs/lipas/ui/ptv/`)
- `events.cljs` — Re-frame events: wizard flow, API calls, sync
- `subs.cljs` — Subscriptions: org config, candidates, sync status
- `views.cljs` — UI components: wizard, PALVELUT tab, LIIKUNTAPAIKAT tab
- `controls.cljs` — Reusable form controls (services-selector, lang-selector)
- `components.cljs` — Service location preview
- `site_view.cljs` — PTV tab in the sports-site editor (outside PTV dialog)
- `audit.cljs` — Audit UI for content review

**Shared** (`src/cljc/`)
- `lipas/data/ptv.cljc` — Data model, PTV payload builders, language mapping, drift detection
- `lipas/schema/sports_sites/ptv.cljc` — Malli schemas for PTV metadata

## PTV API

### Version & Auth

- API version: **v11** (`/api/v11/`)
- Auth: Bearer token from PTV token endpoint
- Test env: `api.palvelutietovaranto.trn.suomi.fi` (per-org credentials hardcoded in `get-test-credentials`)
- Prod env: `api.palvelutietovaranto.suomi.fi` (credentials from config)
- Token quirk: test returns `:ptvToken`, prod returns `:serviceToken`
- Prod auth requires `apiUserOrganisation` with a "persistent org-id" (different from the version org-id used elsewhere)

### Key Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/v11/Service` | Create service |
| PUT | `/v11/Service/SourceId/{id}` | Update service by LIPAS source-id |
| PUT | `/v11/Service/{id}` | Update service by UUID (adopt existing) |
| POST | `/v11/ServiceChannel/ServiceLocation` | Create service location |
| PUT | `/v11/ServiceChannel/ServiceLocation/{id}` | Update service location |
| PUT | `/v11/Connection/serviceId/{id}` | Update service↔channel links |
| GET | Various list endpoints | Fetch org's services, channels, collections |

## Data Model

### Source IDs

LIPAS generates deterministic source-ids to track ownership:

- **Service**: `lipas-{org-id}-{sub-category-id}` (e.g., `lipas-7fdd...-1300`)
- **ServiceLocation**: `lipas-{org-id}-{lipas-id}-{timestamp}` (e.g., `lipas-7fdd...-516095-2026-03-21T19-37-59Z`)

The source-id is used for upsert: try PUT by source-id first, create if 404.

### Language Handling

LIPAS uses `"se"` / `:se` for Swedish internally. PTV uses `"sv"`. Translation happens at API boundaries only:

- **Outbound** (LIPAS → PTV): `resolve-lang-pairs` in `ptv.cljc` maps `"se"` → `"sv"`
- **Inbound** (PTV → LIPAS): `lang->locale` maps `"sv"` → `:se`

This was a historical bug source — Swedish descriptions were silently dropped when the conversion was missing. The key maps:

```clojure
(def lang->locale   {"fi" :fi, "sv" :se, "en" :en})     ;; PTV → LIPAS
(def lipas-lang->ptv-lang {"fi" "fi", "se" "sv", "en" "en"})  ;; LIPAS → PTV
```

### PTV Metadata on Sports Sites

Stored in `:ptv` field of each sports-site document:

```clojure
{:org-id           "uuid"           ;; PTV organization
 :sync-enabled     true/false       ;; Auto-sync on save
 :languages        ["fi" "se" "en"] ;; Supported languages
 :summary          {:fi "..." :se "..." :en "..."}
 :description      {:fi "..." :se "..." :en "..."}
 :user-instruction {:fi "..." :se "..." :en "..."} ;; Services only
 :service-ids      ["uuid"]         ;; Linked PTV Services
 :service-channel-ids ["uuid"]      ;; PTV ServiceLocation ID
 :source-id        "lipas-..."      ;; For future updates
 :last-sync        "2026-03-22T..."  ;; Last successful sync timestamp
 :publishing-status "Published"     ;; PTV state
 :previous-type-code 1330           ;; Detect type changes
 :audit            {:summary {:status "approved" :feedback "..."} ...}}
```

### PTV Description Types

Services have three description types:
- **Summary** — max 150 chars, one complete sentence
- **Description** — max 2500 chars, 2-4 paragraphs
- **UserInstruction** (Toimintaohje) — max 2500 chars, how to access the service (mandatory since 3.3.2026)

ServiceLocations have only Summary and Description (no UserInstruction).

## Sync Flows

### Manual Sync (from UI)

1. User clicks "Synkronoi nyt" or "Vie palvelutietovarantoon"
2. Frontend sends site's PTV metadata + org languages to `/actions/save-ptv-service-location`
3. Backend loads full site from DB, merges incoming PTV data
4. `->ptv-service-location` builds PTV API payload (coordinate transform, language mapping)
5. Create or update in PTV API
6. Update service↔channel connections if changed
7. Store response (new IDs, timestamps) back to LIPAS DB + Elasticsearch
8. Return full PTV response to frontend for cache update

### Auto-Sync (on sports-site save)

When a sports site with `:sync-enabled true` is saved through the normal editor:

1. `sync-ptv!` is called after DB write
2. Checks if site is still a PTV candidate and ready
3. Detects type-code changes (may require service reassignment)
4. Calls `upsert-ptv-service-location!*`
5. Errors are caught and stored in `:ptv :error` (non-fatal)

### Service Sync

Services use PTV as the "database" — there's no local LIPAS entity for them. The flow:

1. User edits descriptions in PALVELUT tab
2. Edits stored in `service-candidates` in app-db (ephemeral)
3. "Synkronoi nyt" sends to `/actions/save-ptv-service` which calls `upsert-ptv-service!`
4. Backend tries PUT by source-id, falls back to POST if 404
5. For adopted services (linked to existing PTV service), uses PUT by UUID

## Drift Detection

### ServiceLocations

Compares LIPAS-stored texts against PTV-fetched texts using `texts-match?`:

| Status | Condition |
|--------|-----------|
| `:not-synced` | No `last-sync` timestamp |
| `:ok` | Texts match between LIPAS and PTV |
| `:out-of-date` | `event-date` differs from `last-sync` |
| `:content-drift` | PTV texts differ from LIPAS texts (someone edited in PTV) |

### Services

Compares local edits (in `service-candidates`) against fetched PTV service descriptions. Uses `merge` (not `or`) to combine per-language maps correctly.

## AI Description Generation

### Current Setup

- Model: **Gemini 3 Flash Preview** (switched from OpenAI in commit 8933ffc5)
- Prompt version: **v5** (DVV-aligned rules)
- Response: structured JSON with `{:summary {:fi :se :en} :description {:fi :se :en} :user-instruction {:fi :se :en}}`

### Prompt Guidelines (v5)

- Write from citizen's perspective
- No contact info (addresses, phone numbers, URLs)
- No evaluative adjectives (monipuolinen, kattava, versatile)
- No marketing language or exclamation marks
- Summary: one complete sentence, max 150 chars
- Description: 2-4 paragraphs (what → access → facilities → conditions)
- UserInstruction: 1-3 sentences, concrete steps for the citizen

### Translation

AI translates between languages using `translate-to-other-langs`. The prompt sends source text and target languages. Summaries are truncated to 150 chars in the success handler.

## Wizard Flow

### Step 1: Select Sports Sites

Radio toggle: "Export all" vs "Select type classes". Filters candidates for subsequent steps. The filter only affects the wizard — the LIIKUNTAPAIKAT tab always shows all candidates.

### Step 2: Create/Link PTV Services

For each sub-category with sports sites:
- Generate descriptions (AI batch)
- Or link to an existing PTV service ("Löytyykö palvelu jo PTV:stä?")
- Edit summary, description, user-instruction per language
- Translate to other languages
- Export to PTV

Linking adopts an existing service by updating it with the LIPAS source-id via PUT by UUID.

### Step 3: Create ServiceLocations

Per sports facility:
- Toggle sync on/off
- Select services
- Generate/edit descriptions
- Export to PTV

## Known Issues & Design Decisions

### PTV as Database for Services

Services don't have a corresponding LIPAS entity. PTV itself stores the authoritative service data. Local edits are ephemeral (in re-frame app-db `service-candidates`). This is pragmatic but means:
- Service descriptions are lost if the browser refreshes before sync
- No local history of service changes

### Service Channel ID Versioning

PTV may assign a new `serviceChannelId` on each update. The frontend cleans up stale cache entries on sync success. The backend returns the full PTV response so the frontend can update its cache.

### Schema Strictness

The `ptv-meta` Malli schema uses `{:closed true}`. Any field not in the schema is stripped during validation. This caused a bug where `:languages` was commented out, silently dropping multi-language support. New fields must be added to the schema.

### Candidate Filtering

A sports site is a PTV candidate if:
- Status is NOT "incorrect-data" or "out-of-service-permanently"
- Owner is "city" or "city-main-owner"
- Type-code is NOT 7000 (Huoltorakennus) or 207 (Opastuspiste)

A candidate is "ready" when it has Finnish summary and description with >5 characters each.

### Audit Workflow

Auditors (`:ptv/audit` privilege) review AI-generated descriptions. They can approve or request changes per field (summary, description). Notifications are sent via email to org's PTV managers. Audit feedback is stored in the sports site's `:ptv :audit` field.
