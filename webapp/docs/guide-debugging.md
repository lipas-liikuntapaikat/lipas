# Debugging Heuristics

This document captures debugging heuristics learned from real-world debugging sessions. These principles are domain-agnostic and can guide systematic problem-solving.

## 1. First Reproduce, Then Fix

**Principle**: Before attempting any fixes, reproduce the exact scenario in isolation and examine the actual data at each step.

**Why it matters**: Understanding the problem is more valuable than quickly attempting fixes. Reproduction gives you control over the debugging environment.

**Example**: When a test fails, don't immediately change code. Instead:
```clojure
;; Reproduce the test scenario in REPL
;; Examine actual values at each step
;; Only then proceed with fixes
```

## 2. Trust Nothing, Verify Everything

**Principle**: Don't make assumptions about what error messages mean. Verify your interpretation with actual data.

**Why it matters**: Error messages can be misleading. `actual: nil` might not mean the value is nil - it might mean the assertion returned nil.

**Example**:
```clojure
;; Instead of assuming :confidence is missing when test shows "actual: nil"
;; Verify what the actual value is:
(println "Actual confidence value:" (:confidence body))
;; You might discover it's "high" not :high
```

## 3. Follow the Data, Not the Code

**Principle**: Trace actual data values through the pipeline rather than just reading code structure.

**Why it matters**: Code tells you what should happen; data tells you what actually happens.

**Example checklist**:
- What does the function return? Print it.
- What does the handler return? Print it.
- What does the HTTP response contain? Print it.
- What does the test receive? Print it.

## 4. When Tests Fail at Boundaries, Check Transformations

**Principle**: System boundaries (HTTP, serialization, database) are where data transformations occur. These are prime suspects for test failures.

**Why it matters**: Data types and formats often change at boundaries (keywords → strings, dates → timestamps, etc.).

**Common boundaries to check**:
- JSON serialization/deserialization
- Database storage/retrieval
- HTTP request/response
- External API calls

## 5. Simplify to the Smallest Failing Case

**Principle**: Reduce the problem to its minimal form before debugging.

**Why it matters**: Smaller problems are easier to understand and faster to iterate on.

**Example**:
```clojure
;; Instead of running full integration tests repeatedly
;; Create minimal reproduction:
(= :high (-> {:confidence :high} 
            json/encode 
            json/decode 
            :confidence))  ; => false!
;; This immediately reveals the serialization issue
```

## 6. Read Error Messages Skeptically

**Principle**: Error messages show symptoms, not causes. Interpret them as clues, not facts.

**Why it matters**: 
- `actual: nil` doesn't mean the value is nil - it means the assertion returned nil
- `NullPointerException` doesn't mean something is null - it means something was dereferenced that shouldn't have been
- Stack traces show where errors surface, not where they originate

**Better approach**:
1. Note what the error says
2. Form hypotheses about what it might mean
3. Test each hypothesis with actual data
4. Don't trust the first interpretation

## 7. Use Proper Debugging Tools

**Principle**: Use the right tool for the job. Grep swallows context; proper logging preserves it.

**Examples**:
```bash
# Bad: Loses crucial context
bb test | grep "FAIL"

# Good: Preserves full error information
bb test > test-output.txt 2>&1
# Then examine the complete output
```

## Key Meta-Lesson

**The goal of debugging is not to fix the problem quickly, but to understand it completely.** A well-understood problem often fixes itself, while a poorly-understood problem leads to more bugs.

These heuristics encourage systematic observation before action, which leads to more efficient debugging and better learning from each debugging session.

## 8. When Stuck, Collaborate with a Socratic Coach

**Principle**: When debugging becomes repetitive or frustrating, engage the socratic-coach agent (or a colleague in that role). Don't try to handle both technical debugging and higher-level thinking simultaneously.

**Why it matters**: Technical debugging requires deep focus on implementation details. Simultaneously trying to question your assumptions and see the bigger picture muddies your thinking. Different types of thinking are better handled by different roles.

**Signs you need the socratic-coach**:
- You're making the same types of changes repeatedly
- You're fixing symptoms without understanding causes  
- You feel frustrated or stuck in a loop
- You're "too deep" in the problem to see it clearly

**How to use the socratic-coach effectively**:
1. Pause your technical work
2. Engage the socratic-coach with a clear problem description
3. Let them guide you through questioning, while you focus on answering
4. Don't try to be both the questioner and answerer - that's two different modes of thinking

**Example engagement**:
```
You: "I'm stuck debugging failing tests. The error says 'actual: nil' 
      but I've verified the value exists. I've been changing the same 
      code for 30 minutes."

Coach: "What assumptions are you making about what 'actual: nil' means?"

You: [Focus only on answering, not on questioning yourself]
```

**Key insight**: Collaboration between different roles (implementer + coach) is more effective than one person trying to switch between technical and meta-cognitive thinking. Let the coach handle the "thinking about thinking" while you handle the technical investigation.