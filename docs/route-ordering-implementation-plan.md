# Route Ordering Feature Implementation Plan

## Overview
This document outlines the implementation tasks for the route ordering feature as described in `route-geometries.md`. Each task is designed to be completed by specialized agents under the coordination of the lead developer.

## Phase 1: Core Data Model & Schema (Week 1)

### Task 1.1: Update Route Schema
**Assignee**: Clojure Expert
**Description**: Add the `segments` field to the route schema while maintaining backwards compatibility
**Files to modify**:
- `webapp/src/cljc/lipas/data/activities.cljc`

**Acceptance Criteria**:
- [ ] Add segment-reference-schema with fid and direction fields
- [ ] Update routes-schema to include optional segments field
- [ ] Ensure schema validation passes for both old and new formats
- [ ] Add ordering-method field to track how ordering was determined

### Task 1.2: Implement Data Migration Logic
**Assignee**: Clojure Expert  
**Description**: Create functions to handle dual-format support (segments + fids)
**Files to create/modify**:
- `webapp/src/cljc/lipas/backend/sports_sites/routes.cljc` (new file)

**Acceptance Criteria**:
- [ ] `ensure-segments-format` function converts fids to segments
- [ ] `get-route-segments` multimethod handles manual/algorithmic/legacy cases
- [ ] Dual-write logic maintains both formats on save
- [ ] Unit tests verify all conversion scenarios

## Phase 2: UI Components (Week 2)

### Task 2.1: Design Segment Ordering Component
**Assignee**: LIPAS UX Designer
**Description**: Design the UI/UX for the segment ordering interface
**Deliverables**:
- Mockups for the segment ordering list
- Interaction patterns for drag-and-drop
- Visual design for direction toggles
- Integration with existing route builder

### Task 2.2: Implement Segment Order Builder
**Assignee**: ClojureScript Specialist
**Description**: Build the UIX component for segment ordering
**Files to create/modify**:
- `webapp/src/cljs/lipas/ui/sports_sites/activities/route_ordering.cljs` (new)
- `webapp/src/cljs/lipas/ui/sports_sites/activities/views.cljs`

**Acceptance Criteria**:
- [ ] Sortable list of selected segments
- [ ] Direction toggle for each segment
- [ ] Ability to add same segment multiple times
- [ ] Visual preview of total route length
- [ ] Integration with existing route form

### Task 2.3: Update Map Interaction
**Assignee**: ClojureScript Specialist
**Description**: Add visual indicators for route ordering on the map
**Files to modify**:
- `webapp/src/cljs/lipas/ui/map/styles.cljs`
- `webapp/src/cljs/lipas/ui/map/editing.cljs`

**Acceptance Criteria**:
- [ ] Show order numbers on selected segments
- [ ] Different styling for repeated segments
- [ ] Direction arrows respect route-specific direction

## Phase 3: Backend API (Week 3)

### Task 3.1: Implement Route Order Suggestion Handler
**Assignee**: LIPAS API Design Expert
**Description**: Create CQRS action handler for algorithmic ordering
**Files to create/modify**:
- `webapp/src/clj/lipas/backend/actions/route_ordering.clj` (new)
- `webapp/src/clj/lipas/backend/handler.clj`

**Acceptance Criteria**:
- [ ] POST `/api/actions/suggest-route-order` endpoint
- [ ] Integrates with existing `gis/sequence-features`
- [ ] Returns confidence level and warnings
- [ ] Handles errors gracefully

### Task 3.2: Enhance Route Save Logic
**Assignee**: Clojure Expert
**Description**: Update route saving to handle ordered segments
**Files to modify**:
- `webapp/src/clj/lipas/backend/sports_sites.clj`

**Acceptance Criteria**:
- [ ] Save both segments and fids for backwards compatibility
- [ ] Validate segment references exist
- [ ] Handle activity-specific logic (cycling algorithmic default)
- [ ] Maintain existing API compatibility

## Phase 4: Testing & Validation (Week 4)

### Task 4.1: Write Comprehensive Tests
**Assignee**: LIPAS Test Specialist
**Description**: Create test suite for route ordering functionality
**Test Coverage**:
- Schema validation tests
- Data migration tests  
- UI component tests
- API endpoint tests
- Integration tests
- Backwards compatibility tests

### Task 4.2: Manual Testing Scenarios
**Assignee**: LIPAS Test Specialist
**Description**: Define and execute manual testing scenarios
**Scenarios**:
- Create route with manual ordering
- Edit existing route to add ordering
- Use algorithmic suggestion
- Test with complex routes (loops, repeated segments)
- Verify backwards compatibility

## Phase 5: Polish & Documentation (Week 5)

### Task 5.1: User Documentation
**Assignee**: Lead Developer
**Description**: Create user-facing documentation
**Deliverables**:
- User guide for route ordering feature
- Screenshots/videos of the workflow
- FAQ section

### Task 5.2: Performance Optimization
**Assignee**: Clojure Expert
**Description**: Optimize any performance bottlenecks
**Focus Areas**:
- Route rendering with many segments
- Algorithmic ordering for large routes
- UI responsiveness

## Risk Mitigation

### Identified Risks:
1. **Breaking existing routes**: Mitigated by dual-format support
2. **UI complexity**: Mitigated by progressive disclosure
3. **Performance with large routes**: Mitigated by optimization phase

### Rollback Strategy:
- Feature flag to disable new UI
- Backend continues to support old format
- Database changes are additive only

## Success Metrics
- [ ] All existing routes continue to work
- [ ] New routes can use manual ordering
- [ ] Cycling routes can override algorithmic ordering
- [ ] UI is intuitive for non-technical users
- [ ] Performance remains acceptable

## Coordination Points
- Daily sync between lead and specialists
- Code review before merging
- Testing handoff between phases
- UX review at UI milestones