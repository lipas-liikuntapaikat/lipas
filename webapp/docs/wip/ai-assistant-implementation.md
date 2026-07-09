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
| `lipas.backend.help` | Help content v2 storage: per-locale versioned_data docs (`help-v2-fi/-se/-en`), get/save/draft/versions per locale, `migrate-v1->v2!`. Schema in `lipas.schema.help` (cljc). |
| `lipas.backend.assistant` | Agent: Gemini function-calling loop, **first turn forced to call a tool** (toolConfig ANY). Tools: `search_kb`, `get_kb_document`, `lookup_type_code`, `explain_field`, `list_stale_sites`, `list_sites_missing_data`, `get_site_summary`, `check_site_permission` (**authoritative verdict from `roles/check-privilege` + user's roles with official names — never LLM-reasoned**), `escalate_to_support` (draft-only), plus **UI-action tools** `apply_search`, `show_site_on_map`, `pan_map_to_location`, `navigate_to_view` (propose-only: validated via `lipas.schema.assistant`, returned as `:actions` in the response, rendered as buttons the user clicks — nothing auto-fires). `editable-scope` from roles = *relevance default* for listing tools, not authorization (site data is public). Per-user in-memory rate limits (30 chat/h, 5 escalations/day). `chat!`/`escalate!` entrypoints, exchange logging to `assistant_logs` (incl. proposed action types). `context-schema` (closed Malli map) validates the widget's app-db snapshot. |
| `lipas.schema.assistant` (cljc) | **Closed action vocabulary**: Malli multi-schema for the 4 action types + `views` registry (id → description, feeds the `navigate_to_view` tool enum and the frontend route mapping). Reuses canonical `lipas-id`/`city-code`/`active-type-code` schemas. The model never produces re-frame event vectors — it calls an action tool, the backend validates against this schema, the frontend translates. |
| `lipas.backend.handler` | Routes `/actions/assistant-chat`, `/actions/assistant-escalate` (both `:require-privilege :ai-assistant/use`); help CMS routes `save-help-draft`, `get-help-versions`, `get-help-version`. |
| `lipas.backend.search` | `kb-mapping` (strict; finnish/swedish/english analyzer subfields, dense_vector 768 cosine), `assistant-logs-mapping`. Registered in `mappings` ⇒ auto-created at startup. |
| `lipas.backend.config` | `:indices` gained `:kb {:kb "lipas_kb_v1"}` and `:assistant {:logs "assistant_logs"}`. |
| `lipas.jobs.*` | Job type `help-kb-sync` (dedup+debounce) in registry, handler in dispatcher, enqueued on every help publish (`core/save-help-data`) + daily scheduler tick. |
| `lipas.roles` | New privilege `:ai-assistant/use`. **Admin-only now** (admin holds all privileges); GA = add it to `roles/basic`. |

Frontend (cljs):

| File | What |
|---|---|
| `lipas.ui.assistant.{events,subs,views}` | Widget: Fab launcher + panel at app root (`lipas.ui.views/main-panel`), privilege-gated. Launcher placement: on the map route it's embedded in the bottom-right map-control container (`MapLauncher` in `map/views.cljs` — the corner is crowded there); elsewhere a fixed Fab bottom-right; when the fullscreen help dialog is open the fixed Fab always shows (zIndex 1350 > modal 1300, < snackbar 1400). Markdown answers (`?ohje=` links open help dialog in place, external links new tab), source chips, escalation confirmation card (editable summary, shows what's sent where), new-chat button. **Action buttons**: `::run-action` is the whitelist translator (action map → dispatches; unknown types inert), gated on map edit-mode (notification instead of navigating away from unsaved edits); executed buttons flip to disabled+✓. `apply-search` → navigate to map + `search/replace-filters`; `show-site` → `map/show-sports-site`; `pan-to-location` → digitransit geocode then `set-center`/`set-zoom` (12 for places, 14 for addresses); `navigate-to-view` → `view->route` map. `db->context` builds the per-message app-db snapshot (route, readable view, locale, selected site's lipas-id, edit-mode). Stateless server: full history sent per request. |
| `lipas.ui.search.events/replace-filters` | Replace whole search spec (string + filters) from clean defaults in one event → single ES query with `:fit-view` (map pans to results). Used by assistant action buttons. |
| `lipas.ui.help.*` | **v2 (see "Help center v2" below)**: docs-site read view (tree sidebar + content column), per-locale trees with fi fallback banner, nav search, mobile drawer. Deep links: `?ohje=section/page` synced via replaceState; `::navigate-to` resolves slugs *and aliases* (pending-slugs if data not loaded yet); dialog at app root; content loads at app init (`::help/init`, dispatch-sync in `core.cljs` *before* router start). Markdown text blocks (react-markdown). Editor: per-locale tabs, Save draft / Publish / History per locale, slug generate-from-title (+auto-alias). |

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
- **Names from data only**: the model decorates bare codes with world
  knowledge and gets them wrong (city 320 → "Muurame"; it's Kemijärvi). Every
  code in the prompt or a tool result carries its resolved name
  (`city-label`/`type-label`/`describe-roles`; role names read from the UI
  i18n file). Prompt forbids inferring names from codes.
- **Answer links are allow-listed server-side** (`sanitize-answer-links`):
  `?ohje=` links must match a deep-link retrieved this conversation; only
  http(s)/mailto pass otherwise; fabricated slugs and tool-call-scheme links
  (`navigate_to_view:profile`) degrade to plain text. The model fabricated
  all three kinds in testing.
- **Permissions**: user's roles (official names + scopes) in USER CONTEXT;
  per-site verdicts via `check_site_permission` (real permission engine);
  role semantics as code-derived KB docs (privilege `:doc` strings). The
  *process* content (how to request rights, org flows) is Lipasinfo-authored
  KB material — still missing.
- **UI actions are propose-only, closed-vocabulary**: the model calls an
  action tool → backend validates against `lipas.schema.assistant` (canonical
  city/type/lipas-id enums; city *names* resolved server-side; site existence
  checked) → widget renders a button → the user clicks. The frontend
  translator is the only place an action becomes a dispatch; edit-mode blocks
  execution. Max 3 actions per answer reach the client.
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

- KB: 1079 docs (7 seeded help-cms + 984 code-data incl. 2 nav-structure
  docs fi/en and 13 role docs + 87 ingested + 1 log). Help CMS content is **my dev seed**
  (2 sections / 4 pages, incl. "Miten kopioin liikuntapaikan?") — real content
  should be authored by Lipasinfo via the editor (draft → publish).
- Multi-intent lesson (seasonal luistelukenttä/pallokenttä case): the
  two-separate-sites rule is answered perfectly out of the box (ingested jyu
  doc has the literal example). But a *latent* second intent (copy feature
  eases creating the second site) is only surfaced if content carries it: the
  agent's searches follow the user's phrasing, and a WORK-SAVING FEATURES
  prompt rule proved unreliable at thinkingLevel minimal (with feature names
  in the rule the model tips **without retrieving** = provenance risk; without
  names it skips the extra search). Robust fix is content-level: the canonical
  seasonal CMS article should itself contain the copy tip / cross-link.
  Direct "miten kopioin kohteen?" questions answer correctly from the seeded
  article (cited as `?ohje=` deep link).
- Eval: hybrid 40/41 hit@3. The 1 miss ("paikka missä voi heittää koreja" →
  1310) is **pre-existing**, verified unrelated to the nav docs by
  delete-and-requery; kNN alone ranks it #2, the RRF merge drops it to #5.
  Prompt-iteration backlog, not a regression.
- Assistant verified in-browser: grounded how-tos with citations (incl. `&t=`
  video timestamps), context adaptation, type-code disambiguation,
  out-of-scope refusal, stale-sites listing (Utajärvi), two-turn escalation,
  chat logging. **UI actions verified end-to-end** (Playwright, fresh
  session): "Mitä liikuntapaikkoja on Äänekoskella?" → button → 225 results +
  map fit; "Missä näen kuntien taloustilastot?" → button → `/tilastot/talous`;
  edit-mode gate refuses with notification. Unit tests:
  `lipas.backend.assistant-test` (action schema + tool handlers).

## Help center v2 (2026-07-09)

The built-in help was revamped in the same branch — the CMS is the KB's
canonical source, so the two ship together.

**Data model** (`lipas.schema.help`, storage `lipas.backend.help`): each
locale (fi/se/en) has its own independent tree of sections → pages →
blocks with **plain-string leaves** — replaces the v1 shared structure
with `{:fi :se :en}` maps whose untranslated slots accumulated
placeholder junk ("New Page", "Ny sida") that leaked into the UI and the
KB. One versioned_data document per locale (`help-v2-fi/-se/-en`) so
languages draft/publish/roll back independently. Nodes carry stable
`:id`, human `:slug` (generated from title via `lipas.utils/->slug`),
`:aliases` (old slugs keep resolving — deep-link/citation stability),
optional `:summary` (landing lists + KB body) and `:translation-of`
(reserved for future per-page fallback).

**Migration**: `(lipas.backend.help/migrate-v1->v2! db)` — one-shot; fi
gets everything with regenerated readable slugs (old timestamp slugs →
aliases), se/en start empty (prod se/en content was placeholder junk or
stale-seed lies like "Excel reports" over accessibility-tool content).
v1 `"help"` rows stay untouched as history. Run once per environment.

**Read view** (`lipas.ui.help.views` rewrite, docs-site pattern): tree
sidebar (collapsible sections, sentence-case wrapping labels; the theme
uppercases all heading variants so content titles set
`textTransform "none"`), max-width content column, responsive 16:9
video embeds, PDF document cards (title bar + open-in-new + 70vh
preview), section landing lists (theme-colored — the v1 white-gradient
cards were unreadable in dark mode), client-side search over the nav,
mobile drawer, locale fallback to fi with an info banner
(`:help/only-in-finnish`). `?ohje=` resolves slugs *and aliases* and
rewrites the URL to canonical form.

**Editor** (`lipas.ui.help.manage` rewrite): locale tabs (fi/se/en);
Save draft / Publish / History act on the active locale only; plain
TextFields (no per-field language tabs); slug field with
generate-from-title button that auto-adds the old slug to aliases;
summary fields.

**KB**: `kb/help-cms->docs` walks the per-locale trees — the lang tag is
trustworthy by construction and junk-language docs are impossible. Also
fixed `block->text` emitting bare video/pdf URLs for languages with
blank titles (with prod data this had produced 34 junk "New Page"
docs). Page `:summary` is prepended to the doc body. Assistant context
gained `:help {:section :page :title}` (open help page → "kysy tästä
aiheesta" works through the universal Fab).

Tests: `lipas.backend.help-test` (migration transform, slug util,
help-cms->docs skip rules).

## Remaining / next

1. **PR + code review** (branch has ~2.5k added lines; `/code-review` worth it).
2. Deploy: `GEMINI_API_KEY` in prod env; **run help-v2 migration against
   prod DB** (`(lipas.backend.help/migrate-v1->v2! db)`, once, before the
   first v2 publish); first `help-kb-sync` runs via scheduler/publish;
   **run ingestion against prod DB** (87 entries live only in local
   versioned_data); real CMS content authoring (summaries + hand-tuned
   slugs where wanted, se/en translations when they exist); then flip GA
   (`:ai-assistant/use` → `roles/basic`). If `assistant_logs` already exists
   in an env, PUT `_mapping` with `actions {:type "keyword"}` (strict mapping;
   fresh indices get it automatically).
3. Kibana views over `assistant_logs` (escalation rate = failure metric,
   top questions = content backlog).
4. Prompt iteration from `assistant_logs` (watch for hedged invention, e.g.
   icon locations not in sources).
5. Parked: lipasinfo email-history harvesting (PM/DPO); Drive-hosted melonta
   guide; se/en translations of help content; v1.5 ideas (public-data tools,
   anchor-seeded openers, related-articles via kNN).
