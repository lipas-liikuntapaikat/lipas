# Development Guide for Agents

## Project Structure
- CLAUDE.md file is in the root directory
- /webapp directory contains the web application

## Available Tools

### Clojure-MCP Tools
This environment provides enhanced Clojure/ClojureScript editing capabilities via clojure-mcp:
- **Integrated Linting**: Built-in clj-kondo analysis with real-time feedback
- **Syntax Checking**: Automatic validation of Clojure/ClojureScript syntax
- **Code Analysis**: Advanced static analysis and code quality checks
- **REPL Integration**: Enhanced REPL capabilities for interactive development

Use these tools when working with Clojure/ClojureScript files for immediate feedback and validation.

## Build Commands
- **Start Dev Environment**: `./setup-dev.sh` or `bb up`
- **Backend Build**: `bb uberjar` or `docker compose run backend-build`
- **Frontend Build**: `clojure -M -m shadow.cljs.devtools.cli release app`
- **REPL**: `clojure -M:nrepl` followed by `user=> (reset)`
- **ClojureScript Watch**: `npm run watch` or `bb cljs-watch`

## Webapp Commands (run from /webapp directory)

### Testing
- **Fast Tests (via nrepl)**: `bb nrepl:test -p .shadow-cljs/nrepl.port -n <comma-separated-test-namespaces>`
- **All Tests**: `bb test`
- **Integration Tests**: `bb test :integration`
- **Single Namespace**: `bb test :only namespace-name`
- **From REPL**: `(t/run-tests *ns*)`

### Code Quality
- **Lint**: `bb lint` (clj-kondo) or use clojure-mcp tools for immediate feedback
- **Format**: `bb cljfmt [files]`
- **Clean Namespaces**: `bb clean-ns [files]`

## Webapp Code Style Guidelines
- **Namespace Aliases**: Use `str` for `clojure.string`, `rf` for `re-frame.core`, `r` for `reagent.core`
- **Namespace Imports**: Keep required namespaces sorted case-sensitively. Don't use :refer :all - use namespace aliases instead.
- **File Organization**: Backend in `src/clj`, frontend in `src/cljs`, shared in `src/cljc`
- **Testing**: Use `clojure.test` with `deftest`, `testing`, and `is` assertions
- **JS Interop**: Prefer direct JS interop over `goog.object`

## Test-Driven Development Workflow

### Development Process
- **Always write tests** for new functions and functionality
- **Use fast feedback loops** by running tests iteratively during development
- **Add tests to existing test namespaces** when extending functionality, or create new test namespaces for new modules
- **Run tests frequently** using the fast test command: `bb nrepl:test -p .shadow-cljs/nrepl.port -n <namespace>`
- **Use clojure-mcp tools** for immediate linting feedback during development

### Test Development Steps
1. **Examine the target function** and understand its purpose and expected behavior
2. **Check existing test namespace** (e.g., `lipas.utils-test` for `lipas.utils`)
3. **Add concise but comprehensive test cases** covering:
   - Basic functionality
   - Edge cases (empty inputs, nil values)
   - Different input types
   - Error conditions
4. **Run tests immediately** to verify implementation
5. **Iterate** until all tests pass and code quality checks are satisfied

### Test Structure
- Use `clojure.test` with `deftest`, `testing`, and `is` assertions
- Group related assertions under descriptive `testing` blocks
- Test file naming: `*_test.cljc` for shared code, `*_test.clj` for backend, `*_test.cljs` for frontend
- Keep tests focused and readable

### Example
```bash
# After adding tests for lipas.utils/->prefix-map
cd /webapp && bb nrepl:test -p .shadow-cljs/nrepl.port -n lipas.utils-test
```

## Webapp frontend

### Status and Code Style

We're gradually migrating from reagent to uix.

- Write new frontend code using uix
- Use uix reagent interop when there's a complex existing reagent component in the codebase. Rewrite simple components using uix and MUI V5
- Prefer explicit MUI requires. Don't use the legacy lipas.ui.mui namespace.

### UIX Syntax

UIX is a thin React Wrapper.

- Components are created using defui macro, here’s the syntax: (defui component-name [props-map] body)
- Elements are created using $ macro: ($ :dom-element optional-props-map …children)
- Component names and props are written in kebab-case.
- Dom element keywords support hyper script syntax to define classes and id: :div#id.class
- JS names should be translated into idiomatic Clojure names.

Following React example:

```javascript
function Item({ name, isPacked }) {
  return <li className="item">{name}</li>;
}

export default function PackingList() {
  return (
    <section>
      <h1>Sally Ride's Packing List</h1>
      <ul>
        <Item isPacked={true} name="Space suit" />
        <Item isPacked={true} name="Helmet with a golden leaf" />
        <Item isPacked={false} name="Photo of Tam" />
      </ul>
    </section>
  );
}
```

Translates to UIX like this:

```clojurescript
(ns packing-list.core
  (:require [uix.core :refer [$ defui]]))

(defui item [{:keys [name packed?]}]
  ($ :li.item name))

(defui packing-list []
  ($ :section
     ($ :h1 "Sally Ride's Packing List")
     ($ :ul
        ($ item {:packed? true :name "Space suit"})
        ($ item {:packed? true :name "Helmet with a golden leaf"})
        ($ item {:packed? false :name "Photo of Tam"}))))
```

### UIX Hooks and State Management Guide

#### Hook Rules ####

1. **Always call hooks at the top level** of your component body, never inside conditions, loops, or nested functions
2. **List all dependencies** in hooks that require them (like `use-effect` and `use-callback`)
3. **Clean up side effects** by returning a cleanup function from effect hooks

#### State Management ####

- **Local state**: `(let [[value set-value!] (use-state initial-value)] ...)`
- **Update state**: `(set-value! new-value)` or `(set-value! (fn [prev] (transform prev)))`
- **Refs (non-reactive)**: `(let [ref (use-ref initial-value)] ...)` - access with `@ref`, update with `(reset! ref new-value)`

#### Common Patterns ####

```clojure
;; Effect that runs on mount and cleanup
(use-effect
  (fn []
    ;; Setup code
    (fn [] ;; Cleanup function
      ;; Cleanup code
    ))
  [])

;; Effect that runs when dependencies change
(use-effect
  (fn []
    ;; Code that uses value1 and value2
  )
  [value1 value2])

;; Form input with controlled state
(let [[value set-value!] (use-state "")]
  ($ :input {:value value
             :on-change #(set-value! (.. % -target -value))}))
```

#### Common Mistakes ####

- ❌ Calling hooks conditionally: `(when condition (use-state 0))`
- ❌ Missing dependencies: `(use-effect (fn [] (use-value)) [])` when `use-value` should be a dependency
- ❌ Infinite update loops: updating state in an effect without proper dependencies

##### Custom Hooks #####

- Define with `(defhook use-custom-hook [params] ...hook logic...)`
- Must start with `use-` prefix
- Follow the same rules as built-in hooks

## Development Workflow with Clojure-MCP

### Best Practices
- **Use clojure-mcp tools first** for immediate syntax checking and linting feedback
- **Run manual lint commands** (`bb lint`) only when needed for comprehensive project-wide checks
- **Leverage integrated analysis** to catch issues early in the development process
- **Test frequently** using the fast nrepl test commands for rapid iteration

### Code Quality Workflow
1. **Edit code** using clojure-mcp tools for real-time feedback
2. **Run targeted tests** with `bb nrepl:test` for affected namespaces
3. **Use manual linting** (`bb lint`) for final project-wide validation before commits
4. **Format code** with `bb cljfmt` as needed

This approach maximizes development speed while maintaining code quality through continuous feedback.
