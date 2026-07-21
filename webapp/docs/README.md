# LIPAS Webapp Documentation (Developer)

Technical documentation for developers working on the webapp.

## Naming Convention

| Pattern | Purpose | Examples |
|---------|---------|----------|
| `{topic}.md` | Core reference | `architecture.md`, `frontend.md` |
| `guide-{topic}.md` | How-to guides | `guide-debugging.md` |
| `integration-{name}.md` | External systems | `integration-ptv.md` |
| `context-{topic}.md` | LLM context | `context-babashka.md` |
| `wip/*.md` | Work-in-progress | Temporary planning docs |

## Contents

### Start Here

- **domain-map.md** - Compact system map, sources of truth, invariants, and
  verification routes
- **glossary.md** - LIPAS vocabulary and easily confused concepts
- **architecture.md** - Long-form system architecture and design decisions
- **data-model.md** - Sports-site and shared domain data model

### Architecture & Core

- **backend.md** - Backend components, routing, middleware, and save flow
- **frontend.md** - Re-frame/Reagent frontend architecture
- **database.md** - PostgreSQL tables, views, revision model, and migrations
- **search.md** - Elasticsearch mappings, enrichment, indexing, and queries
- **auth.md** - Authentication and contextual privilege model
- **i18n.md** - Internationalization system
- **map-gis.md** - Map and geospatial architecture
- **reports.md** - Report generation
- **agent-tooling.md** - Agent context, runtime helpers, and maintenance approach

### Guides

- **guide-debugging.md** - Debugging heuristics
- **guide-testing.md** - REPL-driven testing
- **guide-frontend-patterns.md** - Frontend code patterns & snippets

### Subsystems

- **heatmap.md** - Heatmap analysis feature
- **geoserver.md** - GeoServer integration
- **itrs.md** - ITRS classification support
- **mui.md** - Material-UI usage
- **ai-workbench.md** - AI workbench behavior

### Integrations (Technical Details)

- **integration-ptv.md** - PTV implementation details
- **integration-ptv-architecture.md** - PTV component and data flow
- **integration-ptv-audit.md** - PTV data audit
- **ptv-ai-integration.md** - PTV AI-assisted workflows
- **integration-yti.md** - YTI terminology integration

### API (Internal)
- **api-v1-internal.md** - V1 API implementation details

### LLM Context

Specialized context for AI assistants:
- **context-babashka.md** - Babashka scripting
- **context-ptv.md** - PTV integration context
- **context-repl.md** - REPL interaction patterns

### Work-in-Progress (`wip/`)
Temporary planning and tracking docs. Delete when completed.

## Primary LLM Context

- Codex: [`../AGENTS.md`](../AGENTS.md)
- Claude Code: [`../CLAUDE.md`](../CLAUDE.md)

Both entry points route to this directory for deeper domain knowledge. Prefer
links to executable source and tests over copying facts that can drift.

## See Also

- [`../../docs/`](../../docs/) - System-level documentation (ops, public API)
