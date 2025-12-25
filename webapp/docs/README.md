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

### Architecture & Core
- **architecture.md** - System design, patterns, decisions
- **frontend.md** - Frontend architecture overview
- **i18n.md** - Internationalization system

### Guides
- **guide-debugging.md** - Debugging heuristics
- **guide-testing.md** - REPL-driven testing
- **guide-frontend-patterns.md** - Frontend code patterns & snippets

### Subsystems
- **heatmap.md** - Heatmap analysis feature
- **geoserver.md** - GeoServer integration
- **wfs.md** - WFS layer configuration
- **mui.md** - Material-UI usage

### Integrations (Technical Details)
- **integration-ptv.md** - PTV implementation details
- **integration-ptv-audit.md** - PTV data audit
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

The main LLM context is in [`../CLAUDE.md`](../CLAUDE.md).

## See Also

- [`../../docs/`](../../docs/) - System-level documentation (ops, public API)
