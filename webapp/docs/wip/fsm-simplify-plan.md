# FSM Simplification: Smart Entry-Only Actions

## Context & Problem Statement

The current FSM architecture has a dual action system that creates unnecessary complexity:

1. **`cleanup-actions`** - Run when LEAVING a state (during transitions)
2. **`actions`** - Run when ENTERING a state
3. **`conditional-actions`** - Run when entering a state, but with conditions

This creates several problems:
- **Inheritance confusion**: Base configs with cleanup-actions get merged in unclear ways
- **Split responsibility**: Exit vs entry actions for what should be atomic state setup
- **Context gaps**: Cleanup actions can't be conditional based on transition context
- **Maintenance burden**: Need to understand multiple action systems

### Specific Bug That Motivated This

When adding multiple LineString segments in adding mode:
1. User draws first segment → `adding-drawing` → `adding-editing` (works)
2. User clicks "Add new linestring" → `adding-editing` → `adding-drawing`
3. **Problem**: The transition clears existing geometry because cleanup-actions include `:clear-edits!`

The bug reveals that we need **context-aware state setup**, not separate exit/entry phases.

## Solution: Smart Entry-Only Pattern

**Philosophy**: Each state is responsible for setting itself up correctly based on context, similar to how React components render based on props.

**Key Insight**: States don't need to "clean up after themselves" - they need to "set themselves up correctly" based on where they came from and what they need.

### Current Complex Pattern
```clojure
{:cleanup-actions [:clear-edits! :clear-interactions!]
 :actions [:enable-marker-hover! :start-drawing!]
 :conditional-actions [{:action :fit-to-extent! :condition some-fn}]}
```

### Target Simple Pattern
```clojure
{:actions [
  ;; Smart cleanup - conditional on context
  {:action :clear-edits!
   :condition (fn [mode] (not (adding-mode? (:previous-mode mode))))
   :description "Clear edits when coming from non-adding modes"}

  ;; Standard setup - always needed
  :clear-interactions!
  :enable-marker-hover!
  :start-drawing!

  ;; Context-specific setup
  {:action :fit-to-extent! :condition first-time-entry?}]}
```

## Core Architectural Principles

### 1. Entry State Owns Everything
Each state declares **all actions needed** to set itself up correctly:
- Cleanup previous state (conditionally)
- Clear interactions, markers, etc.
- Set up its own layers, interactions, and behaviors

### 2. Context-Aware Setup
States use `mode` context to make smart decisions:
- `(:previous-mode mode)` - What state we came from
- `(:geoms mode)` - Current geometry data
- `(:first-time mode)` - Whether this is initial entry
- Other mode-specific data

### 3. Declarative Actions
Actions declare **intent**, not implementation details:
- `:clear-edits!` means "ensure no edit geometry"
- Conditional logic determines **when** to run it
- Implementation remains in action registry

## Implementation Plan

### Phase 1: Design Action Patterns

**IMPORTANT: Prefer inlining over abstraction** - Each state's conditions are likely to be specific to that state's context. Don't create "helper" functions unless there's clear evidence of identical reuse across multiple states.

**Example patterns to inline directly**:

```clojure
;; 1. Conditional cleanup based on previous state - INLINE the condition
{:action :clear-edits!
 :condition (fn [mode] (not (#{:adding-drawing :adding-editing}
                            (get-in mode [:previous-mode :name]))))
 :description "Clear edits when not staying within adding modes"}

;; 2. First-time setup - INLINE the condition
{:action :fit-to-extent!
 :condition (fn [mode] (nil? (:previous-mode mode)))
 :description "Fit to extent on initial entry"}

;; 3. Context-specific behavior - INLINE the condition
{:action :select-sports-site!
 :condition (fn [mode] (and (:lipas-id mode) (not (:highlight-source mode))))
 :description "Select site when not highlighting routes"}
```

**Core Principle: Immediate Readability**
> "By looking at the state definition, the user can easily see exactly what will happen without having to jump to many places. This is the power of data-driven architectures - it's easily readable by both humans and machines."

**Why inline instead of abstract:**
- **No indirection** - the complete logic is visible right where it's used
- **Self-contained states** - each state definition tells the complete story
- **Easy debugging** - no need to trace through multiple files to understand behavior
- **Data-driven clarity** - the state configuration IS the documentation
- **Machine-friendly** - tools can analyze the complete logic without resolving dependencies
- **Context-specific logic** - conditions are usually specific to that state's needs

**Only abstract if**: You find the **exact same condition logic** used in 3+ states after completing the conversion.

### Phase 2: Convert State Configurations

**Start with the problematic case** - `adding.cljc`:

1. **Remove all cleanup-actions** from both `adding-drawing-config` and `adding-editing-config`
2. **Convert to smart entry actions**:

```clojure
(def adding-drawing-config
  {:actions [
    ;; Smart cleanup - only clear edits when NOT staying in adding modes
    {:action :clear-edits!
     :condition (fn [mode] (not (#{:adding-drawing :adding-editing}
                                (get-in mode [:previous-mode :name]))))
     :description "Clear edits when entering from non-adding modes"}

    ;; Standard cleanup for this mode
    :clear-interactions!
    :clear-problems!

    ;; Mode setup
    :enable-marker-hover!
    :show-problems!
    :start-drawing!]

   :layers {:show #{:edits :markers}}})

(def adding-editing-config
  {:actions [
    ;; NO :clear-edits! at all - we always want to preserve geometry in editing
    :clear-interactions!
    :clear-problems!

    ;; Mode setup
    :enable-marker-hover!
    :show-problems!
    :start-editing-site!

    ;; Conditional setup - inline the condition logic
    {:action :fit-to-fcoll!
     :condition (fn [mode] (nil? (get-in mode [:previous-mode :sub-mode])))
     :args [:geoms]
     :description "Fit to geometry collection if first time editing"}]

   :layers {:show #{:edits :markers}}})
```

### Phase 3: Remove Cleanup Infrastructure

1. **Update FSM core** (`core.cljc`):
   - Remove `cleanup-actions` handling from `compute-state-diff`
   - Remove cleanup phases from `apply-state-transition`
   - Simplify to single action execution phase

2. **Update schema** (`schema.cljc`):
   - Remove `:cleanup-actions` from state config validation
   - Ensure action lists can contain mixed keywords/maps

3. **Remove base configs** that only existed for cleanup inheritance

### Phase 4: Expand to Other States

**Apply same pattern** to other state files (priority order):
1. `editing.cljc` - Similar patterns to adding
2. `default.cljc` - Simpler case
3. `analysis.cljc` - More complex conditional logic

**For each state**, follow the pattern:
- Remove cleanup-actions completely
- Start actions with conditional cleanup based on context
- Include standard interaction clearing
- Add mode-specific setup actions

## Key Advantages of This Approach

### 1. Simpler Mental Model
- **One action system** instead of three (cleanup/actions/conditional)
- **Declarative** - each state says what it needs
- **Context-aware** - like React components responding to props

### 2. Fixes the Original Bug
```clojure
;; adding-drawing will conditionally clear edits:
{:action :clear-edits!
 :condition (fn [mode] (not (#{:adding-drawing :adding-editing}
                            (get-in mode [:previous-mode :name]))))}

;; adding-editing never clears edits (preserves geometry)
```

### 3. More Maintainable
- **Single source of truth** - each state declares its complete needs
- **No inheritance complexity** - no merging of base configs
- **Easier debugging** - all actions for a state in one place

### 4. More Flexible
- **Rich conditional logic** - can introspect any mode context
- **Atomic state setup** - everything needed happens together
- **Easy to extend** - just add more conditional actions

## Files to Modify

### Priority 1 (Fixes the bug)
- `src/cljc/lipas/ui/map/fsm/states/adding.cljc` ⭐

### Supporting Changes
- `src/cljc/lipas/ui/map/fsm/core.cljc` (remove cleanup-actions logic)
- `src/cljc/lipas/ui/map/fsm/schema.cljc` (update validation)
- `src/cljc/lipas/ui/map/fsm/states/common.cljc` (only if needed for truly shared conditions)

### Phase 2
- `src/cljc/lipas/ui/map/fsm/states/editing.cljc`
- `src/cljc/lipas/ui/map/fsm/states/default.cljc`
- `src/cljc/lipas/ui/map/fsm/states/analysis.cljc`

## Testing Strategy

IMPORTANT: Run tests after each change.
- if tests are failing, analyze whether the problem is in the test or in the code
- fix and run the tests again

Run all fsm tests with:
- `bb test-ns lipas.ui.map.fsm.core-test`

Debug individual tests in REPL with:

```clojure
(require '[clojure.test]')
(require '[lipas.ui.map.fsm.namespace-to-test]' :reload)
(clojure.test/run-test-var #'lipas.ui.map.fsm.namespace-to-test/test-var)
```

IMPORTANT! Failures in tests are not acceptable. If you hit a brick wall, take a few steps back and THINK how to approach scientifically. Ask yourself coaching questions to escape failure loops.

## Success Criteria

✅ **ALL TESTS PASS**: All automated tests pass
✅ **Simplified**: Single action system, no cleanup-actions
✅ **Context-Aware**: States set themselves up based on context
✅ **No Regressions**: Existing transitions continue working
✅ **Maintainable**: Each state's needs are explicit and co-located

## Implementation Notes

### Condition Function Patterns
```clojure
;; Previous state checking
(get-in mode [:previous-mode :name])     ; :adding-drawing, :editing-view-only, etc.
(get-in mode [:previous-mode :category]) ; :adding, :editing, :analysis

;; Mode data checking
(:lipas-id mode)                         ; Sports site ID
(:geoms mode)                           ; Geometry data
(:highlight-source mode)                ; Route highlighting context
```

### Action Execution Order
1. **Conditional cleanup first** - clear what needs clearing based on context
2. **Standard setup second** - interactions, layers, etc.
3. **Mode-specific actions third** - drawing, editing tools, etc.
4. **Conditional setup last** - fit-to-extent, selections, etc.

This order ensures clean state setup without conflicts.

---

This approach eliminates the complexity of dual action systems while providing more power and flexibility through context-aware state setup. Each state becomes self-contained and responsible for its own complete configuration based on the transition context.
