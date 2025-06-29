## REPL-Driven Testing Instructions for LLM's

When working with Clojure code, **ALWAYS use the REPL** for development and debugging. Follow these principles:

### 1. Start Small, Verify Everything
- Test individual functions in the REPL before writing full implementations
- Verify each assumption with actual code execution
- Build solutions incrementally, testing each piece

### 2. When Debugging Tests or Code Issues
Instead of guessing or making blind changes:
- Run failing tests individually first: `(test/run-test-var #'namespace/specific-test)`
- Use the REPL to reproduce the exact failure scenario
- Test your fix in the REPL before modifying files
- Verify the fix works by re-running the specific test

### 3. REPL Health Check
**STOP immediately if the REPL shows signs of not working:**
- No output after evaluation
- Missing expected return values
- Silent failures (no error but no result)
- Output shows only `nil` when you expect data

If this happens, notify me with: "The REPL appears to be unresponsive. Please check the REPL connection before continuing."

### 4. Effective REPL Workflow
```clojure
;; 1. Always reload namespaces after changes
(require '[namespace.name :as alias] :reload)
(require '[test-namespace.name :as test-alias] :reload)

;; 2. Test specific functionality before running full test suites
(let [result (function-to-test args)]
  (println "Result:" result)
  (println "Type:" (type result)))

;; 3. Catch and examine exceptions
(try
  (potentially-failing-code)
  (catch Exception e
    (println "Error:" (.getMessage e))
    (.printStackTrace e)))
```

### 5. Development Process
1. **Explore** - Use REPL to understand existing code behavior
2. **Experiment** - Test solutions interactively
3. **Implement** - Write code based on verified REPL experiments
4. **Verify** - Confirm changes work in REPL before considering done

### 6. Red Flags - Don't Proceed Without REPL Feedback
- Never make changes based solely on reading code
- Don't guess what an error might be
- Don't assume how a function behaves - test it
- If you can't get REPL output, don't continue

Remember: The REPL is your primary tool for understanding and verifying code behavior. Use it constantly, not just when things break.
