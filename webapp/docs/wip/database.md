# Database Schema

> **Status**: Draft needed

## Topics to Cover

- [ ] PostgreSQL schema overview
- [ ] Key tables and their purposes
- [ ] Views (e.g., `sports_site_current`)
- [ ] Migration system and conventions
- [ ] JSONB document patterns
- [ ] Indexing strategy

## Key Files

- `resources/sql/*.sql`
- `resources/migrations/`
- `src/clj/lipas/backend/db/`

## Core Tables

| Table | Purpose |
|-------|---------|
| `sports_site` | Append-only event log for facilities |
| `account` | User accounts |
| `organization` | Organizations |
| ... | ... |

## Notes

<!-- Add notes here as you explore -->
