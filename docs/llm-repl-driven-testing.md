## REPL-Driven Testing Instructions for LLM's

### 1. **Start with Individual Test Runs**
Instead of running all tests at once (which can timeout), I found it most effective to:
```clojure
;; Run specific failing tests first
(test/run-test-var #'lipas.jobs.resilience-test/system-recovery-stuck-jobs-test)
```

### 2. **Always Reload Changed Code Before Testing**
This was crucial - I needed to reload both the source and test namespaces:
```clojure
(require '[lipas.jobs.core :as jobs] :reload)
(require '[lipas.jobs.resilience-test :as resilience-test] :reload)
(require '[lipas.test-utils :as test-utils] :reload)
```

### 3. **Handle Namespace Conflicts**
When I hit conflicts like "attempt to replace interned var", I needed to unmap first:
```clojure
(ns-unmap 'lipas.jobs.handler-test 'db)
(require '[lipas.jobs.handler-test :as handler-test] :reload)
```

### 4. **Use Simple Patterns for Running Related Tests**
For running multiple related tests:

```clojure
(doseq [ns ['lipas.jobs.core-test
            'lipas.jobs.patterns-test
            'lipas.jobs.resilience-test]]
  (require ns :reload)
  (test/run-tests ns))
```

### 5. **Run Tests in Logical Groups**
I found it effective to test in this order:
1. **Unit tests first** (core-database-test) - These are simpler and help verify basic functionality
2. **Integration tests next** (resilience-test, integration-test) - These test more complex interactions
3. **Handler/dispatcher tests last** - These often depend on the core functionality working

### 6. **Pay Attention to Test Output**
The error messages were very informative:
- `ERROR: operator does not exist: bigint = jsonb` immediately told me a map was being passed where an ID was expected
- Stack traces showed exactly which SQL queries were failing

### 7. **Effective Test Development Workflow**
My most effective workflow was:
```clojure
(do
  ;; 1. Set up test system
  (def test-system (setup-test-system!))
  (def db (:lipas/db test-system))

  ;; 2. Clean database
  (test-utils/prune-db! db)

  ;; 3. Test the specific functionality in REPL first
  (let [result (jobs/enqueue-job! db "email" {:to "test@example.com"})]
    (println "Result:" result)
    (println "ID:" (:id result)))

  ;; 4. Then run the actual test
  (test/run-test-var #'specific-test))
```

### 8. **Use REPL for Quick Debugging**
Before fixing tests, I used the REPL to understand the actual behavior:
```clojure
;; This helped me see that enqueue-job! returns a map
(let [result (jobs/enqueue-job! db "email" {:to "test@example.com"})]
  (println "Result type:" (type result))
  (println "Result:" result))
```

### Key Takeaway
The REPL-driven approach was incredibly powerful for fixing these tests. Rather than making blind changes and running full test suites, I could:
1. Identify the problem through targeted test runs
2. Experiment in the REPL to understand the actual behavior
3. Fix the code
4. Verify the fix with individual test runs
5. Finally run broader test suites to ensure no regressions

This iterative, focused approach saved a lot of time compared to running full test suites repeatedly.
