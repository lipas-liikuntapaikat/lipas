# AI Assistant & Knowledge Base — Implementation Status

Status: **built and working end-to-end in dev** on branch `feat/ai-assistant`
(commits `4742860d..732378d3`, 2026-07-08). Design rationale in
[ai-assistant-plan.md](./ai-assistant-plan.md); this doc is the
what-actually-exists map for getting up to speed from scratch.

## What it is

A context-aware AI help assistant for registered LIPAS users: floating chat
widget on every route, answers grounded in an Elasticsearch knowledge base
(help CMS + code-derived type/field docs + ingested jyu.fi PDFs and Lipasinfo
YouTube transcripts), cites sources as in-app `?ohje=` deep links and
timestamped video URLs, runs read-only data-maintenance queries, and escalates
to human support (email to lipasinfo@jyu.fi) only after explicit user
confirmation.

## File map

Backend (clj):

| File | What |
|---|---|
| `lipas.backend.llm` | Generic LLM plumbing extracted from `ptv.ai`: provider registry, `complete`/`gemini-complete` (+retry, OpenAI fallback), `embed`/`embed-one` (gemini-embedding-001, 768 dims, L2-renormalized). `ptv.ai` now wraps it with PTV-specific schema defaults. |
| `lipas.backend.kb` | KB doc builders + sync + retrieval. `help-cms->docs`, `code-data->docs` (types incl. colloquial `:tags`, prop-types), `ingested->docs` (reads versioned_data type=`kb-ingest`), `sync!` (content-hash diff → embed only changed, delete orphans), `search-kb` (hybrid BM25+kNN, client-side RRF, cross-lingual kNN unfiltered, per-language dedup by source-ref), `get-doc` (full entry by id). |
| `lipas.backend.assistant` | Agent: Gemini function-calling loop, **first turn forced to call a tool** (toolConfig ANY). Tools: `search_kb`, `get_kb_document`, `lookup_type_code`, `explain_field`, `list_stale_sites`, `list_sites_missing_data`, `get_site_summary`, `escalate_to_support` (draft-only). `editable-scope` from roles = *relevance default* for listing tools, not authorization (site data is public). Per-user in-memory rate limits (30 chat/h, 5 escalations/day). `chat!`/`escalate!` entrypoints, exchange logging to `assistant_logs`. `context-schema` (closed Malli map) validates the widget's app-db snapshot. |
| `lipas.backend.handler` | Routes `/actions/assistant-chat`, `/actions/assistant-escalate` (both `:require-privilege :ai-assistant/use`); help CMS routes `save-help-draft`, `get-help-versions`, `get-help-version`. |
| `lipas.backend.search` | `kb-mapping` (strict; finnish/swedish/english analyzer subfields, dense_vector 768 cosine), `assistant-logs-mapping`. Registered in `mappings` ⇒ auto-created at startup. |
| `lipas.backend.config` | `:indices` gained `:kb {:kb "lipas_kb_v1"}` and `:assistant {:logs "assistant_logs"}`. |
| `lipas.jobs.*` | Job type `help-kb-sync` (dedup+debounce) in registry, handler in dispatcher, enqueued on every help publish (`core/save-help-data`) + daily scheduler tick. |
| `lipas.roles` | New privilege `:ai-assistant/use`. **Admin-only now** (admin holds all privileges); GA = add it to `roles/basic`. |

Frontend (cljs):

| File | What |
|---|---|
| `lipas.ui.assistant.{events,subs,views}` | Widget: Fab launcher + panel at app root (`lipas.ui.views/main-panel`), privilege-gated. Markdown answers (`?ohje=` links open help dialog in place, external links new tab), source chips, escalation confirmation card (editable summary, shows what's sent where), new-chat button. `db->context` builds the per-message app-db snapshot (route, readable view, locale, selected site's lipas-id, edit-mode). Stateless server: full history sent per request. |
| `lipas.ui.help.*` | Deep links: `?ohje=section/page` synced via replaceState; `::navigate-to` resolves slugs (pending-slugs if data not loaded yet); dialog moved to app root; content loads at app init (`::help/init`, dispatch-sync in `core.cljs` *before* router start). Markdown text blocks (react-markdown) + `lipas.utils/localized` fi→en→se fallback. Editor: Save draft / Publish / History dialog (load any revision into editor; publishing it = rollback). |

Dev tooling:

| File | What |
|---|---|
| `dev/lipas/kb_eval.clj` | 41 golden questions, hit@3 runner, `compare-methods`. Current: bm25 78% / knn 100% / **hybrid 41/41**. Run before corpus/mapping/model changes. |
| `dev/lipas/kb_ingest.clj` | One-off ingestion: extract (LLM → task-shaped entries) → grounding (separate verifier per source; 2/89 rejected) → type-code validation → docs saved to versioned_data `kb-ingest` → `kb/sync!`. VTT parsing with `[s=N]` markers → timestamped YouTube deep links; `gemini-video-transcript` fallback for the 9 caption-less videos. Corpus inventory (9 PDFs + all 21 Lipasinfo videos) is in the ns. |

## Data flow

```
help CMS (canonical, published only) ─┐
lipas.data.types/prop-types (derived) ─┼─> kb/sync! ──> lipas_kb_v1 (1063 docs)
versioned_data "kb-ingest" (one-off)  ─┘   (hash-diff,      │
                                            orphan-delete)  ▼
widget ──/actions/assistant-chat──> assistant loop ──tools──> search-kb / sites index
   ▲          (context snapshot)         │
   └── answer + sources + escalation? ◄──┘        exchanges ──> assistant_logs
```

The index is a **deterministic function of Postgres + code**: `(kb/sync! db
search)` rebuilds everything (~20 s full, ~2 s no-change). If the index ever
looks wrong or empty, just resync.

## REPL recipes

```clojure
(require '[lipas.backend.kb :as kb])
(kb/sync! (user/db) (user/search))                     ; rebuild/repair KB
(kb/search-kb (user/search) {:query "..." :lang "fi"}) ; test retrieval

(require '[lipas.kb-eval :as kb-eval])
(kb-eval/compare-methods (user/search))                ; retrieval eval

(require '[lipas.backend.assistant :as assistant])
(assistant/answer! {:db (user/db) :search (user/search)
                    :user (lipas.backend.core/get-user (user/db) "admin@lipas.fi")
                    :message "Miten lisään liikuntapaikan" :history []
                    :context {:route ":lipas.ui.routes.map/map"
                              :view "map view (karttanäkymä)" :locale "fi"}})

;; re-run ingestion (needs <dir> with txt/*.txt + subs/*.vtt, see kb-ingest ns)
(require '[lipas.kb-ingest :as ingest])
(def result (ingest/ingest-all! dir))
(ingest/report result)
(ingest/save-ingested! (user/db) (user/search) (:docs result))

;; browser e2e login (playwright: run from webapp/, config needs
;; {"browser":{"launchOptions":{"args":["--ignore-certificate-errors"]}}})
(require '[lipas.e2e.tools :as e2e])
(e2e/ui-login! "admin" (:admin-password environ.core/env))
```

## Key decisions & invariants

- **One KB doc = one task/question in one language** ("task-shaped"): sharp
  embeddings, retrieved unit = answer unit, honest citations. Titles phrased
  as users ask.
- **Grounding discipline**: forced first tool call; snippets are truncated →
  agent must `get_kb_document` before answering how-tos; prompt forbids
  inventing steps/UI details; no-coverage → offer escalation, not general
  knowledge.
- **Context adaptation = omission only**: user is always logged in (never show
  login steps); steps the context proves done are skipped/acknowledged; never
  ADD anything not in sources.
- **Escalation is two-phase**: agent drafts, UI confirmation card, separate
  endpoint sends via the `email` job queue (user's address in body,
  conversation id in subject).
- **Ingestion is migration, not mirroring**: entries live in versioned_data,
  `review-status "machine"` until a human flips them; grounding verifier
  rejects unsupported claims at ingest time.
- Embedding model changes = bump `llm/embedding-defaults` → sync re-embeds all
  (hash includes `embedding-model`).

## Gotchas (each cost real time)

- **test-utils suffixes ALL configured ES indices via a map walk** — it used
  to be a hardcoded list; my `:kb` entry wasn't on it and `prune-es!` in
  `full-system-fixture` **wiped the real dev KB between tests**. Never go back
  to a list. (Recovery: one `sync!`.)
- REPL: `user/reset` sometimes hits a clj-reload/integrant arity bug → use
  `(integrant.repl/halt)` + `(integrant.repl/go)`. Stale-alias errors on
  `:reload` after refresh → `remove-ns` first, then `:reload`. Config changes
  need `refresh-all` + restart, or components keep stale `:indices`.
- Gemini structured output: no `additionalProperties` (use `mu/open-schema`),
  no `$ref` (inline schemas). Function calling and responseSchema are separate
  modes — the agent loop returns markdown + we collect sources from tool
  results instead.
- YouTube: 9 of 21 videos have **no captions at all** → `gemini-video-transcript`
  (fileData fileUri) fallback. yt-dlp rate-limits; pace with sleeps.
- `lipas-utp-pyorailyohjeistus.pdf` is corrupt at the source (bad xref);
  excluded from ingestion until JYU provides a clean copy.

## Current dev state

- KB: 1063 docs (6 seeded help-cms + 969 code-data + 87 ingested + 1 log).
  Help CMS content is **my dev seed** (2 sections / 3 pages) — real content
  should be authored by Lipasinfo via the editor (draft → publish).
- Eval: hybrid 41/41 hit@3. Assistant verified in-browser: grounded how-tos
  with citations (incl. `&t=` video timestamps), context adaptation, type-code
  disambiguation, out-of-scope refusal, stale-sites listing (Utajärvi),
  two-turn escalation, chat logging.

## Remaining / next

1. **PR + code review** (branch has ~2.5k added lines; `/code-review` worth it).
2. Deploy: `GEMINI_API_KEY` in prod env; first `help-kb-sync` runs via
   scheduler/publish; **run ingestion against prod DB** (87 entries live only
   in local versioned_data); real CMS content; then flip GA
   (`:ai-assistant/use` → `roles/basic`).
3. Kibana views over `assistant_logs` (escalation rate = failure metric,
   top questions = content backlog).
4. Prompt iteration from `assistant_logs` (watch for hedged invention, e.g.
   icon locations not in sources).
5. Parked: lipasinfo email-history harvesting (PM/DPO); Drive-hosted melonta
   guide; se/en translations of help content; v1.5 ideas (public-data tools,
   anchor-seeded openers, related-articles via kNN).
