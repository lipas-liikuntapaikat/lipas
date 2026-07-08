# LIPAS AI Assistant & Knowledge Base — Implementation Plan

Status: agreed design; **implemented** — see
[ai-assistant-implementation.md](./ai-assistant-implementation.md) for the
as-built map, REPL recipes and remaining work. This doc stays as the design
rationale.

## Goal

A single knowledge base (KB) consolidating all LIPAS user-assistance material,
served to registered users through a context-aware AI assistant available in
every view of lipas.fi. The existing help CMS remains the canonical authoring
surface; jyu.fi and YouTube content is ingested one-off; the assistant grounds
every answer in the KB and cites sources with deep links.

## Decisions (agreed)

- KB lives in Elasticsearch (8.19.9-icu — no upgrade, no plugin needed;
  `dense_vector` + kNN are core ES).
- Hybrid retrieval: BM25 (finnish + ICU analyzers) + kNN, merged client-side
  with RRF (avoids license-gated `rrf` retriever).
- Embeddings: `gemini-embedding-001` via the existing Google API key.
  `output_dimensionality: 768` (MRL; re-normalize after truncation),
  task types `RETRIEVAL_DOCUMENT` (ingest, with title) / `RETRIEVAL_QUERY`.
- Registered users only, gated by a new `:ai-assistant/use` privilege
  (rollout: `:admin` first, then basic privilege set at GA).
- lipasinfo email-history harvesting is **scoped out** pending PM/DPO decision.
  The pipeline is source-agnostic so it can slot in later.
- Aggregate-statistics tools ("how many X in Y") are out — v1 tools target
  data-maintenance workflows (stale sites, missing data).
- Escalation to human support is a first-class tool (two-phase confirm,
  sends email to lipasinfo@jyu.fi under the hood).

## Indices

Both behind aliases (`lipas-kb` → `lipas-kb-v1`); schema change = new index +
full sync + alias flip. Explicit mappings only — never dynamic (see the legacy
dense_vector auto-mapping incident).

### `lipas-kb`

One doc = one task/question-sized entry ("task-shaped", see below).

| Field | Type | Notes |
|---|---|---|
| `title`, `body` | text | finnish analyzer + ICU folding subfields; body stores markdown |
| `lang` | keyword | fi / se / en; single index, kNN filters on lang |
| `embedding` | dense_vector | dims 768, cosine, index: true |
| `source-type` | keyword | help-cms / jyu / youtube / code-data / docs |
| `source-ref` | keyword | page slug, video id, var name, URL |
| `deep-link` | keyword | where citations land |
| `type-codes` | integer | when entry is type-specific |
| `updated-at` | date | |
| `content-hash` | keyword | sync idempotency |
| `embedding-model` | keyword | enables batch re-embed on model change |
| `review-status` | keyword | machine / reviewed |

### `assistant-logs`

One doc per exchange: hashed user id, question, answer, tool calls, cited doc
ids, latency, token counts, feedback (thumbs), `escalated?` flag. Summary
events also emitted to the existing analytics event-log for Kibana/Ohry.
Escalation rate = the assistant's primary failure metric; most-escalated
topics = KB content backlog.

## Content sources

1. **help-CMS (canonical, continuous sync)** — published content only.
2. **Code-derived (`source-type: code-data`, regenerated every sync)**:
   `lipas.data.types` (one doc per type code), `lipas.data.prop-types`
   (field definitions), `lipas.data.activities`, status/owner/admin enums.
   Deterministic templates, no LLM. Long instructional prose from i18n EDN
   (analysis help, PTV wizard, map hints) — long entries only, not tooltips.
3. **jyu.fi PDFs (one-off)** — route/point/area guides, analysis tool guides,
   Luontoon.fi content guides, data-model docs.
4. **YouTube (one-off)** — Lipasinfo channel (@lipasinfo7382) + Luontoon.fi
   playlist; transcripts via Gemini video input; citations carry timestamps.
5. ~~lipasinfo email history~~ — parked (PM/DPO). Escalation emails (below)
   are designed to be the clean future corpus if green-lit.

One-off ingestion is *migration*, not mirroring: after ingest, canonical
ownership moves to the help-CMS; jyu.fi shrinks to a brochure. Nightly job
flags jyu/youtube docs older than N months for review.

## Task-shaped entries

One KB doc = one user intent, self-contained (no unresolved references),
100–400 words of markdown: task-phrased title, short answer, prerequisites
(role needed), numbered steps, pitfalls, related links + metadata. Rationale:
one intent = one sharp embedding (retrieval precision); retrieved unit =
answer unit (less hallucinated glue); task-phrased titles close the
colloquial-vs-official vocabulary gap; citations land somewhere honest;
maintenance/dedup/review operate at intent granularity.

## KB integrity (multi-pass)

Authored/ingested entries pass through:

1. **Extract** (LLM): source → task-shaped entries.
2. **Grounding** (separate LLM call): every claim supported by source, else flag.
3. **Dedup**: embedding cosine > ~0.9 → LLM judge merges.
4. **Consistency** (mechanical): referenced type codes exist, deep links
   resolve, lang present, embedding-model current.
5. **Review status**: enters as `machine`; Lipasinfo flips to `reviewed` in a
   lightweight admin list. Agent prefers reviewed docs and cites vintage.
6. **Golden eval set**: ~40 hand-authored questions (emails are out of scope)
   stored in repo; retrieval eval (top-3 hit rate: bm25 / knn / hybrid) +
   end-to-end answer eval before corpus or prompt changes ship.

Code-derived docs skip 1–3 (deterministic).

## Agent

Server-side Gemini function-calling loop (generic LLM plumbing extracted from
`lipas.backend.ptv.ai` into `lipas.backend.llm`; provider registry, retry,
OpenAI fallback transfer as-is). Endpoint `/actions/assistant-chat` behind
`:ai-assistant/use` middleware. Structured output (`response-schema`, already
supported by the client): `{answer-md, citations [{doc-id, title, deep-link}],
confidence}`. `search_kb` is force-run on every user turn before the model may
answer; empty retrieval → honest "en löytänyt ohjetta" + escalation offer.

### Tools (v1, read-only except escalation)

| Tool | Purpose |
|---|---|
| `search_kb` | hybrid retrieval → snippets + deep links |
| `get_kb_document` | full entry by id |
| `lookup_type_code` | free text or code → full type spec |
| `explain_field` | prop/field → definition, requirements |
| `list_stale_sites` | sites not updated since date, city/type filtered |
| `list_sites_missing_data` | sites lacking given fields (`must_not exists`) |
| `get_site_summary` | site doc summary by lipas-id (no author PII) |
| `escalate_to_support` | two-phase support handoff (below) |

The three site tools run through the same role scoping as the UI (allowed
city-codes/type-codes derived from JWT roles, server-side). No write tools;
"fix it" answers end in a deep link to the edit view.

### Escalation to support

Agent may *propose* escalation, never send unilaterally:

1. Agent calls `escalate_to_support` with a drafted problem summary.
2. Widget renders a confirmation card: editable summary + notice
   ("Lähetän kysymyksesi LIPAS-tukeen, saat vastauksen sähköpostiisi …") +
   Send/Cancel.
3. On confirm, a separate endpoint emails lipasinfo@jyu.fi via the existing
   emailer: subject `[LIPAS-avustaja] <summary>` + conversation id, edited
   summary, trimmed transcript (last N turns), context snapshot (view,
   selected site as lipas.fi link, org, role scope), `Reply-To:` user's
   registered email.

Tighter rate cap than chat (few/user/day). Logged with `escalated?` flag.

## Access, limits, privacy

- `:ai-assistant/use` in `lipas.roles/privileges`; frontend launcher behind
  `check-privilege` (same pattern as `HelpManageButton`); 6h JWT bake accepted.
- Per-user sliding-window message cap + daily token budget in the endpoint.
- Context contains only the user's own scope + currently-viewed public site;
  logs store trimmed context; user ids hashed in `assistant-logs`.
- Gemini key lives server-side only.

## Grounding & deep links

- Help pages become addressable via URL-synced query param (`?ohje=osio/sivu`)
  so links compose with any view (dialog opens over the map, no navigation).
  Requires: dialog-state ↔ URL sync + help data loaded at app init (today it
  loads only via the map route controller).
- Citation chips ("Lähteet") under each answer open the help dialog at the
  page, the YouTube video at timestamp, or the type-code explorer entry.

## KB freshness

1. **CMS hook**: `core/save-help-data` enqueues `sync-help-kb` on the jobs
   queue after successful publish → deterministic transform, content-hash
   diff, embed changed only, delete-by-query orphans.
2. **Code-derived regeneration**: same job, at deploy/startup + nightly.
3. **Nightly integrity job**: deep links resolve, re-embed stale
   embedding-model, flag aged one-off docs.
4. **Migration**: alias flip (`lipas-kb-v2` + full sync + move alias).

Prerequisite: CMS draft/publish split (only `active` syncs) — also version
history UI + rollback (storage is already append-only).

## app-db context injection

**Always**: current route + params, locale, user's editing scope (role names,
allowed city-codes/type-codes/orgs). **Per-view**: map (selected site
{lipas-id, name, type-code, status, last event-date}, edit mode, active tool,
new-site type), search (filters + result count), analysis (tool + params),
PTV (org + sync summary), help dialog (open section/page).

Mechanism: each feature ns exposes an `::assistant-context` re-frame sub
returning a compact map; a route → subs registry; root `::assistant/context`
composes static + active-view parts. Widget snapshots the sub **per message**
(context tracks navigation mid-chat), sends as `context` field, server
validates against a closed Malli schema, size-caps (~2 KB), injects into the
system prompt as a structured block.

## CMS prerequisites (phase 1)

- Markdown rendering for `:text` blocks; language fallback (se/en → fi)
  instead of blank.
- Draft/publish via `versioned_data.status`; history list + rollback.
- Help deep links + app-init data load.
- Known bugfixes: `::save-success` resets `[:ptv :save-in-progress]`
  (manage.cljs:329); `::get-help-data` sets `[:help :save-in-progress]` and
  never clears it.

## Build order

1. Prereqs: help deep links + init load; CMS markdown + draft/publish;
   `lipas.backend.llm` extraction + `embed` fn.
2. KB: index mappings, code-data generator, CMS sync job, hybrid `search_kb`
   — retrieval eval against golden set **before any agent exists**.
3. Ingestion: jyu PDFs + YouTube through the multi-pass pipeline, human review.
4. Agent + widget: tool loop, gating, citations, context sub, logs/limits.
   Admin dogfood → all registered users.

## Open items (need humans)

- YouTube corpus boundary confirmation + the Google Drive-hosted
  *Melonnan sisällönsyötön ohje*.
- Who at Lipasinfo owns `review-status` triage.
- PM/DPO decision on email-history harvesting (design accommodates it).
- Later (v1.5+): public-data search tools over site descriptions,
  anchor-seeded chat openers, related-articles via kNN.
