Concise instructions for using the Clojure ↔ ClojureScript REPLs:

1. From the Clojure REPL, run
   ```clj
   (user/browser-repl)
   ```
   to switch into a live ClojureScript REPL.

2. In the ClojureScript REPL, evaluate
   ```clj
   :cljs/quit
   ```
   to return to the Clojure REPL.

3. In the Clojure REPL, run
   ```clj
   (user/compile-cljs)
   ```
   to compile ClojureScript and view any build warnings or errors.
