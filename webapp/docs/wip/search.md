# Elasticsearch & Search

> **Status**: Draft needed

## Topics to Cover

- [ ] Index structure (`sports_sites_current`, `sports_sites_legacy`, etc.)
- [ ] Mappings and field types
- [ ] Query patterns and helpers
- [ ] Enrichment process (DB → ES)
- [ ] Reindexing procedures
- [ ] Search API integration

## Key Files

- `src/clj/lipas/backend/search.clj`
- `src/clj/lipas/search_indexer.clj`
- ES mapping definitions

## Indexes

| Index | Purpose |
|-------|---------|
| `sports_sites_current` | Main search index |
| `sports_sites_legacy` | V1 API compatibility |
| ... | ... |

## Notes

<!-- Add notes here as you explore -->
