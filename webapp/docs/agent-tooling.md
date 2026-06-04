# Agent Tooling

How we work with AI coding agents (Claude Code et al.) on LIPAS — what we're building, why, and how to extend it.

This is meta-documentation: it explains the *approach*, not the codebase. For agent-shaped reference docs about LIPAS itself, see the rest of `webapp/docs/`.

## The problem

AI agents start every session cold. Without persistent, agent-shaped context they:

- **Rediscover the same things every session.** Routes, file paths, the data model, REPL incantations. Burns tokens; introduces fabrication risk.
- **Mis-read prose docs.** Tutorials and architecture overviews are written for humans-learning. Agents are doing-not-learning. The signal-to-noise ratio is wrong.
- **Trust stale facts.** A doc that confidently asserts an outdated fact (wrong version number, removed library, renamed function) is *worse* than no doc — the agent acts on it.
- **Lack curation.** Given a 200-line event trace and asked "what went wrong?", an agent burns context scanning. Given a 5-line summary that already names the failure, it acts.

We're investing in a persistent layer that addresses these directly.

## What we're aiming for

Agents that, on day-one of a fresh session, can:

1. Orient in seconds, not minutes — without flooding context.
2. Run real e2e scenarios (create/edit a sports site, sync a PTV org, etc.) without rediscovering the flow.
3. Verify their work cheaply across DB, Elasticsearch, app-db, and DOM — not just by scraping the UI.
4. Diagnose failures with curated cross-layer snapshots, not raw dumps.
5. Add what they learned back into the system, so the next agent starts further ahead.

The tooling should make agents faster *and* more honest.

## Principles

The shape of useful agent tooling, in priority order:

### 1. Curation > observation

Observation tools (logs, traces, event streams) help when you don't know what to look at. Verification tools (snapshots, coherence checks, diffs) help when you know what should be true. **An agent doing real work is in the second case ~80% of the time.** Invest in verification first; add observation surgically when a specific bug class demands it.

### 2. Structured > prose

Tables, lists, EDN maps, file:line refs. Not narrative paragraphs. Agents read structured data well and prose poorly. A 5-row table beats 5 paragraphs every time.

### 3. Push curation into the tools

`(coherent? lipas-id)` returning `{:ok? false :drift [{:layer :es :missing [:name/se]}]}` is 10× more useful than dumping the full ES doc. The summary IS the assertion. Save the agent from sifting; tell it what's true.

### 4. Lazy loading > exhaustive context

Always-loaded text costs every conversation. On-demand text costs only when used. This dissolves the depth/breadth tradeoff: you can write thorough docs without bloating context, *as long as the index is good*.

### 5. Verifiable facts only

If a fact might be stale, link to the source instead of duplicating it. Stale "facts" actively mislead — they're worse than missing facts. Examples we hit: `frontend.md` claiming UIx was "limited use recommended" when it had been fully removed; version numbers off by a minor.

### 6. Skip meta-instruction

Don't write docs that tell agents how to think ("Trust nothing, verify everything", "Start small"). Modern agents already operate this way. Generic LLM-coaching docs are dead weight; they crowd out the LIPAS-specific facts that *only* live in our codebase.

### 7. Skills compound, files don't

A scenario file added today helps every agent that runs the scenario tomorrow. A debugging session that doesn't get codified into the skill is one-shot value. After each non-trivial task, ask: *did I rediscover something? did a doc steer me wrong?* If yes, fix the skill before moving on.

## Architecture

Three loading tiers, ordered by cost:

```
┌────────────────────────────────────────────────────────────────────┐
│  CLAUDE.md                                                         │
│  Always loaded every session. Must stay tiny.                      │
│  Vocabulary, invariants, REPL access, must-knows.                  │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ when triggered by task keywords
┌────────────────────────────────────────────────────────────────────┐
│  SKILL.md  (one per skill, e.g. .claude/skills/lipas-e2e/)         │
│  Loaded when the skill triggers. Should be a thin router/index.    │
│  Names what's available; links into the payload.                   │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ on demand from SKILL.md links
┌────────────────────────────────────────────────────────────────────┐
│  Skill payload                                                     │
│  scenarios/, catalog.md, recipes — read only what the task needs.  │
│  Detail can be rich because it costs nothing until loaded.         │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ referenced by anchor
┌────────────────────────────────────────────────────────────────────┐
│  webapp/docs/                                                      │
│  Domain reference (data-model.md, auth.md, map-gis.md...).         │
│  Skills POINT at specific anchors here rather than re-documenting. │
└────────────────────────────────────────────────────────────────────┘
```

Why this works: the cheap layer is the index, the expensive layer is the detail, and you only pay for detail you actually use. You can grow the detail freely (more scenarios, deeper recipes) without taxing every conversation.

## The runtime tooling layer

Skills tell agents *what to do*; runtime helpers let them actually do it. For LIPAS we've added `lipas.e2e.tools` (under `dev/`) — a small Clojure namespace with helpers that:

- **Match real code paths.** `seed-site!` calls `core/save-sports-site!` (the same fn the HTTP handler uses), not a synthetic shortcut. Tests that pass against the real path mean something.
- **Return small, structured results.** `(coherent? lid)` returns `{:ok? bool :drift [...]}`, not a JSON dump. Curation lives in the helper.
- **Document drift causes inline.** When the obvious fn is wrong (`core/get-sports-site-history` collapses by year), the helper that replaces it (`revision-count`) says so in its docstring. Future-agent reads the docstring, doesn't repeat the mistake.
- **Trust internal code; throw at boundaries.** No defensive validation around things the framework guarantees. Try/catch only where a missing layer (ES doc not yet indexed) is genuinely an expected `nil`.

The same namespace also exposes UI-driver helpers (`ui-login!`, `ui-create-site!`, `ui-update-site!`, etc.) that drive the running SPA via re-frame dispatches. These call `shadow.cljs.devtools.api/cljs-eval` to fire events in the browser and poll cljs state from the clj side using `Thread/sleep`. The browser-side counterpart is `dev/lipas/e2e/scripts.cljs` — a sync-only namespace of dispatchers and readers, no Promises. The clj wrappers do all the waiting. Result: agents get UI flows that feel synchronous (`(def lid (e2e/ui-create-site! ...))`) without ever leaving the clj REPL session.

## The first skill: lipas-e2e

`/.claude/skills/lipas-e2e/` is our first agent-first skill. Its purpose: drive end-to-end flows against a live dev system, verifying glass-box across DB / ES / app-db / DOM.

- **`SKILL.md`** — router. Trip-wires (append-only model, save vs. upsert, sync vs. async surface). Links into scenarios and `webapp/docs/` anchors. Lists what NOT to load (low-signal docs).
- **`catalog.md`** — fixtures (always-present users including `limindemo` city-manager), routes, REST endpoints, REPL recipes, the `lipas.e2e.tools` API.
- **`scenarios/create-site.md`** — the most important LIPAS use case. REPL path + UI path + verification + gotchas + cross-references.
- **`scenarios/update-site.md`** — second-most-important. Same shape; permissions stricter; revision semantics covered.

Two real bugs got caught the first time we ran it:

- We were calling `core/upsert-sports-site!` (DB only). Right fn is `core/save-sports-site!` (DB + sync ES + jobs).
- We were counting "revisions" via `core/get-sports-site-history` which queries the by-year view and collapses same-year edits.

Both findings are now in `SKILL.md` trip-wires. Next agent doesn't repeat them.

## Extending the system

### Adding a scenario

When you do an e2e task not yet covered:

1. Drop a file in `scenarios/<task>.md` using the existing ones as templates.
2. Keep it recipe-shaped: setup → trigger (REPL + UI) → verify → gotchas → related links.
3. Use runnable Clojure in code blocks. Avoid prose explanations of what the code does.
4. Link to `webapp/docs/` anchors for domain detail; don't redocument.
5. List in `SKILL.md`'s scenario index.

### Adding a tool helper

When you reach for something that should already exist:

1. Add a stub in `lipas.e2e.tools` with a clear docstring describing intent.
2. Implement against real code paths, not shortcuts.
3. Return structured EDN, not strings.
4. Add to `catalog.md`'s API table.
5. If the helper has a non-obvious gotcha (like `revision-count` vs. `get-history`), document it in the docstring AND in `SKILL.md` trip-wires.

### Spawning a new skill

A new skill (vs. a new scenario in lipas-e2e) when the task domain is genuinely separate. E.g. `lipas-ptv` if PTV scenarios grow enough to deserve their own SKILL.md and trip-wires. Don't fragment prematurely — a skill earns its split by having ≥3 scenarios and distinct trip-wires.

### Updating CLAUDE.md

CLAUDE.md is **always loaded**, so every line costs every conversation. Add to it only when:

- The fact is touched by ≥3 different task types (otherwise it goes in a skill).
- It's a vocabulary item or invariant agents need to even *parse* the codebase.
- Skipping it would cause the agent to fabricate.

When in doubt, put it in a skill, not CLAUDE.md.

## What we deliberately don't build

Investments we've considered and decided against (or deferred until we have evidence):

- **Massive event-tracing infrastructure** (cross-stack `tap>` bus). Elegant, but verification helpers cover ~80% of cases without the bookkeeping. Defer until a concrete bug class demands it.
- **Self-extending playbook libraries.** Sounds great; in practice rots into contradictions without a human curator. We update one canonical scenario file when we learn something durable.
- **Reactive expectation DSLs.** Premature ergonomics. Plain `let` with before/after snapshots is flexible and honest.
- **Generic LLM-coaching docs** ("how to use a REPL", "how to debug"). Modern agents already know. The docs that bear that stamp in `webapp/docs/` (`guide-testing.md`, `guide-debugging.md`, `context-repl.md`) should be revisited.
- **Browser-test recording layers.** Browser tests are the slowest, flakiest path. We use them for genuine UX assertions only; everything else goes through the REPL/back-end.

## Test data & dev DB hygiene

Two principles in tension, both intentional:

- **Dev DB is typically a prod snapshot** for realism. Don't assume an empty DB.
- **Tests always seed their own data** for stability. Don't reuse existing entities.

Reconciliation: scenarios always call `seed-site!` for the site under test, and use the seeded `limindemo` user (which is added by `seed-demo-users!`) — not entities pulled from snapshot data. After a scenario, `cleanup!` does a soft-delete (status flip — append-only model means no real delete). The dev DB accumulates revisions but stays usable.

`e2e/find-site` exists for exploratory work and failure investigation, **not** for picking test inputs.

## Maintenance loop

After every non-trivial task:

1. **Did I rediscover something?** → add to the relevant scenario, or to `catalog.md` if cross-cutting.
2. **Did a scenario steer me wrong?** → fix it. Stale > missing.
3. **Did I do a flow not yet covered?** → drop a new scenario.
4. **Did I reach for a helper that didn't exist?** → stub it in `lipas.e2e.tools` with docstring.
5. **Did a fact in CLAUDE.md or webapp/docs/ turn out wrong?** → fix it now or open a PR.

The skill earns its place by repeatedly saving rediscovery cost. Entries that never get used should be deleted.

## What we're still figuring out

Honest open questions:

- **Should we cull `webapp/docs/`?** Several files are LLM-generated marketing prose, generic LLM coaching, or leaked system prompts. They actively cost context. We've deferred the rewrite until the skill has carried real weight, so the cull is evidence-driven.
- **Should `.claude/skills/clj-surgeon/` and `.claude/skills/playwright-cli/` be checked in?** They're currently per-developer installs. Same answer applies: prove the value via use, then standardize.
- **When does a tap>-based event bus pay off?** Probably only when we hit a class of bugs whose root cause is invisible to snapshots (e.g. event-ordering bugs). Until then, defer.
- **How does this scale to non-LIPAS projects?** The architecture is generic; the skill content isn't. The interesting question is whether the *patterns* (skill router, catalog, scenarios, runtime tools ns) become a template.

The space is moving fast. What works in 2026 may not be what works in 2027. We're optimizing for what helps the current generation of agents while keeping the structure light enough to evolve.

## See also

- `webapp/.claude/skills/lipas-e2e/SKILL.md` — the first skill instance
- `webapp/dev/lipas/e2e/tools.clj` — the runtime tooling layer
- `webapp/CLAUDE.md` — the always-on tier
