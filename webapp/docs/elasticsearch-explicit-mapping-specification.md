# LIPAS Elasticsearch Explicit Mapping Specification

**Document ID:** LIPAS-ES-001
**Version:** 1.1.0
**Status:** Draft for Approval
**Date:** 2025-12-30
**Author:** System Architecture Team
**Target Release:** TBD

---

## 1. Document Control

### 1.1 Change History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.1.0 | 2025-12-30 | Architecture Team | Changed to programmatic mapping generation from prop-types; removed verbose code examples |
| 1.0.0 | 2025-01-29 | Architecture Team | Initial specification |

### 1.2 Related Documents

- Current Implementation: `src/clj/lipas/backend/search.clj`
- Property Types Schema: `src/cljc/lipas/data/prop_types.cljc` (source of truth for property mappings)
- Malli Schemas: `src/cljc/lipas/schema/sports_sites.cljc`
- Search Indexer: `src/clj/lipas/search_indexer.clj`
- Proposal Document: `docs/elasticsearch-explicit-mapping-proposal.md`

---

## 2. Executive Summary

### 2.1 Objective

Replace dynamic Elasticsearch field mapping with explicit mapping for the `sports_sites_current` index to achieve:
- Prevention of index bloat from nested activity data structures
- Type-safe property field handling
- ES 8.x compatibility
- Improved performance and maintainability

### 2.2 Scope

**In Scope:**
- Explicit mapping definition for 260+ fields
- Activities field restructuring to prevent bloat
- All 181 property field mappings
- Migration strategy and testing plan
- Query pattern updates for affected endpoints

**Out of Scope:**
- Changes to the `sports_sites_legacy` index
- UI changes (except where query patterns change)
- Database schema changes
- Performance optimization beyond mapping improvements

### 2.3 Success Criteria

1. All existing queries work without modification (except activities filtering)
2. Index size reduced by 30-40%
3. All 181 properties correctly typed and queryable
4. Zero production errors during migration
5. All tests pass with strict mapping enabled

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-1 | All query patterns must continue to work | Critical | All integration tests pass |
| FR-2 | All 181 properties must be filterable | Critical | Property filters work for numeric, boolean, string, enum types |
| FR-3 | Activity filtering must work | High | V2 API activity filter returns correct results |
| FR-4 | Geographic queries must work | Critical | Heatmap, diversity, reachability analyses produce correct results |
| FR-5 | Full-text search must work | Critical | Name, address, description searches return expected results |
| FR-6 | Export functionality must work | High | Excel, CSV, GeoJSON exports complete successfully |

### 3.2 Non-Functional Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| NFR-1 | Index size must be reduced | High | 30-40% reduction in index size |
| NFR-2 | Zero downtime migration | Critical | Alias swap with no service interruption |
| NFR-3 | Query performance must not degrade | High | Query response times within 10% of current |
| NFR-4 | Type safety must be enforced | Medium | Unmapped field queries fail with clear errors |
| NFR-5 | Maintainability must improve | Medium | Mapping serves as documentation of searchable fields |

### 3.3 Constraints

1. **Breaking Change:** Activity filtering query pattern will change
2. **Reindex Required:** Full reindex necessary to apply new mapping
3. **ES Version:** Must support Elasticsearch 8.x
4. **Backward Compatibility:** V1 API must continue to work unchanged

---

## 4. Technical Design

### 4.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Application Layer                       │
│  (V1 API, V2 API, Reports, Heatmap, Analysis)               │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              Elasticsearch Index Layer                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  sports_sites_current (alias)                         │  │
│  │  → sports_sites_2025_01_29_strict (actual index)     │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Mapping Configuration:                                      │
│  ├─ Dynamic: "strict"                                       │
│  ├─ Total Fields Limit: 300                                │
│  └─ Explicit Mappings: 260+ fields                         │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                       │
│              (Source of Truth - sports_site)                 │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Field Categories

#### 4.2.1 Geographic Fields (4 fields)

| Field | Type | Purpose |
|-------|------|---------|
| `search-meta.location.wgs84-point` | geo_point | Primary location for distance/bbox queries |
| `search-meta.location.wgs84-center` | geo_point | Route center point |
| `search-meta.location.wgs84-end` | geo_point | Route end point |
| `search-meta.location.geometries` | geo_shape | Polygon/line queries with intersects relation |

#### 4.2.2 Core Fields (12 fields)

- `lipas-id` (integer)
- `status` (keyword)
- `event-date` (date)
- `type.type-code` (integer)
- `location.city.city-code` (integer)
- `construction-year` (integer)
- `admin` (keyword)
- `owner` (keyword)
- `name` (text + keyword)
- `marketing-name` (text + keyword)
- `search-meta.name` (keyword for sorting)
- `comment` (text + keyword)

#### 4.2.3 Search-Meta Enrichment Fields (47 fields)

Multilingual fields (fi/se/en) for:
- Type names, tags, categories
- Location names (city, province, AVI area)
- Admin/owner names
- Field type arrays
- Activities array (NEW)

#### 4.2.4 Property Fields (181 fields)

All fields from `lipas.data.prop-types/all`:
- 97 numeric → `{:type "double"}`
- 67 boolean → `{:type "boolean"}`
- 11 string → `{:type "text" :fields {:keyword {:type "keyword"}}}`
- 3 enum → `{:type "keyword"}`
- 3 enum-coll → `{:type "keyword"}`

#### 4.2.5 Disabled Fields (Index Bloat Prevention)

- `activities.*` → `{:enabled false}` (all nested content)
- `location.geometries` → `{:enabled false}` (raw GeoJSON)
- `search-meta.location.simple-geoms` → `{:enabled false}` (simplified geoms)

### 4.3 Activities Field Restructuring

**Problem:** Dynamic indexing of `activities.*` creates 140+ fields including large geometries.

**Solution:** Two-tier approach

1. **Source Storage:** The `activities` field is disabled for indexing (`:enabled false`) but remains in `_source` for retrieval
2. **Search Array:** A new `search-meta.activities` keyword array contains activity keys for filtering

**Enrichment:** Extract activity keys and add to `search-meta.activities` during indexing

**Query Change:** V2 API must switch from `{:exists {:field "activities.cycling"}}` to `{:terms {:search-meta.activities ["cycling"]}}`

### 4.4 Mapping Generation Strategy

**Approach:** Generate mappings programmatically from `lipas.data.prop-types/all` to avoid maintaining two separate sources of truth.

**Generation Function Location:** `lipas.backend.search` namespace

**Type Mapping Rules:**
- `:data-type "numeric"` → `{:type "double"}`
- `:data-type "boolean"` → `{:type "boolean"}`
- `:data-type "string"` → `{:type "text" :fields {:keyword {:type "keyword"}}}`
- `:data-type "enum"` → `{:type "keyword"}`
- `:data-type "enum-coll"` → `{:type "keyword"}`

**Static Fields:** Geographic, core, and search-meta fields are defined directly in code (not derived from prop-types)

**Settings:**
- `dynamic: "strict"` - reject unmapped fields
- `total_fields.limit: 300` - prevent excessive field count
- `max_result_window: 60000` - support large result sets

---

## 5. Implementation Plan

### 5.1 Prerequisites

| Task | Owner | Est. Time | Dependencies |
|------|-------|-----------|--------------|
| Review and approve specification | Tech Lead | 2 hours | This document |
| Implement mapping generation function | Backend Dev | 4 hours | prop-types schema |

### 5.2 Implementation Phases

#### Phase 1: Mapping Generation (Week 1)

**Tasks:**
1. Create `generate-explicit-mapping` function in `lipas.backend.search`
2. Iterate over `prop-types/all` and apply type mapping rules
3. Add static fields (geographic, core, search-meta)
4. Add activities field restructuring
5. Validate generated mapping structure

**Deliverables:**
- Mapping generation function in codebase
- Unit tests for mapping generation logic

**Acceptance Criteria:**
- [ ] All 181 properties mapped correctly by type
- [ ] Type mapping rules match prop-types data model exactly
- [ ] `activities` field disabled, `search-meta.activities` added
- [ ] Geographic fields correctly typed
- [ ] Total field count ≤ 300
- [ ] Mapping generation is deterministic and testable

#### Phase 2: Code Updates (Week 1-2)

**Tasks:**
1. Update `lipas.backend.core/enrich` to add `search-meta.activities`
2. Update `lipas.backend.api.v2` activity filtering to use new query pattern
3. Update `lipas.search-indexer/main` to call mapping generation function
4. Add unit tests for enrichment and mapping generation

**Files to Modify:**
- `src/clj/lipas/backend/core.clj` - enrichment logic
- `src/clj/lipas/backend/api/v2.clj` - activity filtering
- `src/clj/lipas/backend/search.clj` - mapping generation
- `src/clj/lipas/search_indexer.clj` - use generated mapping

**Acceptance Criteria:**
- [ ] Enrichment adds activity keys array
- [ ] V2 API uses `{:terms {:search-meta.activities [...]}}` query
- [ ] Index creation uses programmatically generated mapping
- [ ] Unit tests verify mapping generation correctness
- [ ] Code review approved

#### Phase 3: Testing (Week 2)

**Tasks:**
1. Create strict-mode test index
2. Reindex test data
3. Run full integration test suite
4. Fix any missing mappings iteratively
5. Performance benchmark against current index

**Test Coverage:**
- V2 API (all filters, sorting, pagination)
- V1 API (backward compatibility)
- Reports (Excel, CSV, GeoJSON)
- Heatmap (all dimensions, all filters)
- Diversity analysis
- Reachability analysis
- Full-text search

**Acceptance Criteria:**
- [ ] All integration tests pass
- [ ] No unmapped field errors
- [ ] Query performance within 10% of baseline
- [ ] Index size reduced by 30-40%

#### Phase 4: Staging Deployment (Week 3)

**Tasks:**
1. Create new index with strict mapping
2. Reindex staging data
3. Deploy updated backend code
4. Swap alias to new index
5. Monitor for errors
6. Manual QA testing

**Acceptance Criteria:**
- [ ] Zero errors during reindexing
- [ ] All staging tests pass
- [ ] Manual QA sign-off
- [ ] Performance metrics acceptable

#### Phase 5: Production Deployment (Week 4)

**Tasks:**
1. Create new index with strict mapping
2. Reindex production data (off-hours)
3. Deploy backend code update
4. Atomically swap alias
5. Monitor metrics and logs
6. Rollback plan ready

**Rollback Procedure:**
1. Swap alias back to old index
2. Revert backend code deployment
3. Investigate and fix issues

**Acceptance Criteria:**
- [ ] Zero downtime during migration
- [ ] No errors in production logs
- [ ] Query performance meets SLA
- [ ] Index size reduction achieved
- [ ] Monitoring dashboards green

### 5.3 Timeline

```
Week 1: Phases 1-2 (Mapping + Code)
Week 2: Phase 3 (Testing)
Week 3: Phase 4 (Staging)
Week 4: Phase 5 (Production)
```

---

## 6. Testing Specifications

### 6.1 Unit Tests

**New:** `test/clj/lipas/backend/search_mapping_test.clj`

Test coverage:
- Mapping generation produces correct structure (`dynamic: "strict"`, field limit 300)
- All 181 properties from prop-types are mapped
- Property ES types match their `:data-type` (numeric→double, boolean→boolean, etc.)
- Activities field is disabled, search-meta.activities is keyword type
- Geographic fields have correct geo types

**New:** Enrichment tests in `test/clj/lipas/backend/core_test.clj`

Test coverage:
- Enrichment extracts activity keys to `search-meta.activities`
- Empty activities map produces empty array
- Nil activities produces nil or empty array

### 6.2 Integration Tests

**Extend:** `test/clj/lipas/backend/api/v2_test.clj`

Test coverage:
- Activity filtering with new `search-meta.activities` query pattern
- Property filtering (numeric range, boolean, string, enum)
- All existing V2 API endpoints continue to work

**New:** `test/clj/lipas/backend/search_strict_test.clj`

Test coverage:
- Queries on unmapped fields are rejected with clear error messages
- All mapped fields are queryable

### 6.3 Performance Tests

**New:** `test/clj/lipas/backend/search_performance_test.clj`

Test coverage:
- Query latency comparison (strict vs dynamic) - target: within 10%
- Index size comparison - target: 30-40% reduction
- Bulk indexing performance

### 6.4 Manual QA Checklist

**V2 API:**
- [ ] Sports sites list loads
- [ ] All filter types work (type, city, admin, owner, status)
- [ ] Activity filtering works with new query
- [ ] Property filtering works (numeric range, boolean, string)
- [ ] Sorting works (name, date, construction-year)
- [ ] Pagination works
- [ ] LOIs endpoint works

**V1 API (Backward Compatibility):**
- [ ] `/v1/sports-places` returns results
- [ ] `/v1/deleted-sports-places` works
- [ ] Sorting works
- [ ] Pagination works

**Reports:**
- [ ] Excel export completes
- [ ] CSV export completes
- [ ] GeoJSON export completes
- [ ] Field selection works
- [ ] Exported data is complete and correct

**Analysis:**
- [ ] Heatmap loads for all dimensions
- [ ] Heatmap filters work
- [ ] Diversity analysis completes
- [ ] Reachability analysis completes

**Search:**
- [ ] Full-text search returns relevant results
- [ ] Name search works
- [ ] Address search works

---

## 7. Risk Management

### 7.1 Risk Register

| ID | Risk | Impact | Probability | Mitigation | Owner |
|----|------|--------|-------------|------------|-------|
| R1 | Missing field mapping causes production errors | High | Medium | Comprehensive testing, staged rollout | Backend Lead |
| R2 | Query performance degrades | Medium | Low | Benchmark before deployment, rollback plan | Backend Dev |
| R3 | Reindex fails on large dataset | Medium | Low | Test on staging copy of prod data | Backend Dev |
| R4 | Activities query update breaks V2 API | High | Medium | Integration tests, API versioning | Backend Dev |
| R5 | Alias swap causes momentary downtime | Medium | Low | Practice on staging, off-hours deployment | DevOps |

### 7.2 Rollback Plan

**Triggers for Rollback:**
- Query errors in production logs
- Performance degradation > 20%
- Data integrity issues
- Critical functionality broken

**Rollback Steps:**
1. **Immediate:** Swap alias back to old index using `search/update-alias!` (< 1 minute)
2. **Code:** Revert backend deployment via git revert and redeploy
3. **Investigate:** Root cause analysis in staging
4. **Fix:** Address issues before re-attempting
5. **Cleanup:** Delete failed strict-mode index

**Rollback Testing:**
- [ ] Practice rollback procedure on staging
- [ ] Document exact commands
- [ ] Assign rollback authority
- [ ] Set up monitoring alerts

---

## 8. Monitoring and Success Metrics

### 8.1 Key Metrics

**Index Health:**
- Index size (bytes) - Target: 30-40% reduction
- Field count - Target: ≤ 300
- Document count - Should match pre-migration
- Indexing rate (docs/sec) - Should improve
- Refresh time - Should improve

**Query Performance:**
- Average query latency - Target: ≤ 10% increase
- P95 query latency - Target: ≤ 10% increase
- P99 query latency - Target: ≤ 20% increase
- Query rate (qps) - Should remain stable
- Error rate - Target: 0%

**Application Health:**
- API response times - Target: ≤ 10% increase
- Error rates - Target: 0% increase
- Search result accuracy - Target: 100% match

### 8.2 Monitoring Dashboards

**Create Grafana Dashboards:**
1. **ES Index Metrics**
   - Index size trend
   - Document count
   - Field count
   - Indexing rate

2. **Query Performance**
   - Latency percentiles (P50, P95, P99)
   - Query rate
   - Error rate
   - Cache hit rate

3. **Application Metrics**
   - API endpoint response times
   - Error rates by endpoint
   - Search result counts

**Alerts:**
- Query error rate > 0.1%
- Query latency P95 > 500ms
- Index size increase > 10% unexpectedly
- Document count mismatch

### 8.3 Post-Deployment Validation

**Week 1:**
- [ ] Daily review of error logs
- [ ] Daily review of performance metrics
- [ ] User feedback monitoring
- [ ] Query pattern analysis

**Week 2-4:**
- [ ] Weekly metrics review
- [ ] Gradual increase in traffic
- [ ] Long-term performance trending

**Success Declaration Criteria:**
- Zero errors for 7 consecutive days
- Performance metrics stable within targets
- Index size reduction achieved
- All acceptance criteria met

---

## 9. Documentation Updates Required

### 9.1 Technical Documentation

- [ ] Update `docs/elasticsearch.md` with explicit mapping details
- [ ] Update `docs/api/v2.md` with activity filtering change
- [ ] Add `docs/elasticsearch-migration-2025-01.md` for historical reference

### 9.2 Code Documentation

- [ ] Add docstrings to mapping generation functions in `search.clj`
- [ ] Document enrichment changes in `core.clj`
- [ ] Update V2 API handler docstrings for activity filtering
- [ ] Document type mapping rules in code comments

### 9.3 Runbook

- [ ] Create `runbooks/elasticsearch-reindex.md`
- [ ] Create `runbooks/elasticsearch-rollback.md`
- [ ] Update `runbooks/deployment.md` with mapping update procedure

---

## 10. Open Questions and Dependencies

### 10.1 Open Questions

| ID | Question | Owner | Target Resolution |
|----|----------|-------|-------------------|
| Q1 | Should we use `dynamic: "strict"` or `dynamic: false`? | Tech Lead | Before Phase 1 |
| Q2 | Do we need backward compatibility for old activity queries? | Product | Before Phase 2 |
| Q3 | How to handle rollback if mapping generation code changes? | DevOps | Before Phase 1 |
| Q4 | How long to retain old index after migration? | Tech Lead | Before Phase 5 |

**Recommendation for Q1:** Use `"strict"` to catch errors early rather than silently ignoring.

**Recommendation for Q2:** No - V2 API can be updated, V1 API doesn't use activity filtering.

**Recommendation for Q3:** Use git tags for code versions + timestamped index names for rollback to specific mapping versions.

**Recommendation for Q4:** Retain for 30 days, then delete after validation.

### 10.2 External Dependencies

| Dependency | Status | Risk | Mitigation |
|------------|--------|------|------------|
| ES 8.x compatibility | Assumed | Low | Already on 8.19.9 |
| Staging environment | Available | Low | Verify access |
| Production deployment window | TBD | Medium | Schedule with DevOps |

---

## 11. Appendices

### Appendix A: Complete Field List

**Field Count Summary:**
- Geographic: 4 fields
- Core: 12 fields
- Search-Meta: 47 fields
- Properties: 181 fields (programmatically generated from `lipas.data.prop-types/all`)
- Other: 16 fields
- **Total: 260 fields**

See mapping generation function in `lipas.backend.search` for complete implementation.

### Appendix B: Property Type Mapping Table

| Prop Type | Count | ES Type | Notes |
|-----------|-------|---------|-------|
| numeric | 97 | double | Range queries supported |
| boolean | 67 | boolean | Term queries supported |
| string | 11 | text + keyword | Full-text + exact match |
| enum | 3 | keyword | Exact match only |
| enum-coll | 3 | keyword | Array of keywords |

### Appendix C: Query Pattern Updates

**Activity Filtering (Breaking Change):**
- Before: `{:exists {:field "activities.swimming"}}`
- After: `{:terms {:search-meta.activities ["swimming"]}}`

**All Other Queries:** No changes required.

### Appendix D: Glossary

- **Dynamic Mapping:** ES automatically detects field types
- **Strict Mapping:** ES rejects queries on unmapped fields
- **Enrichment:** Adding computed fields during indexing
- **Alias:** Named pointer to an ES index (allows atomic swap)
- **Reindex:** Copying documents to a new index with new mapping

---
