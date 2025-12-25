# LIPAS Documentation (System-Level)

This folder contains **system-level documentation** for operators, API consumers, and stakeholders.

## Naming Convention

| Pattern | Purpose |
|---------|---------|
| `{topic}.md` | Core system docs |
| `api-{version}.md` | Public API documentation |
| `integration-{name}.md` | External system integrations |

## Contents

### Operations
- **ops.md** - Deployment, infrastructure, maintenance procedures

### Public API Documentation
- **api-v1.md** - V1 REST API (legacy-compatible)
- **api-v2.md** - V2 REST API (modern)

### Feature Overviews
- **async-jobs.md** - Background job system
- **organizations.md** - Organization management
- **analysis-diversity.md** - Diversity analysis feature

### Integrations
- **integration-ptv.md** - PTV (Palvelutietovaranto) overview

## Audience

- System operators
- API consumers (external developers)
- Stakeholders needing high-level understanding

## See Also

- [`webapp/docs/`](../webapp/docs/) - Developer technical documentation
- [`webapp/CLAUDE.md`](../webapp/CLAUDE.md) - LLM development context
