(ns user
  "Top-level development utilities with dynamic dependency loading.

  This namespace provides a unified REPL experience across both
  infrastructure (top-level) and webapp contexts by dynamically
  loading webapp development dependencies on demand."
  (:require
   [clojure.repl.deps :as deps]
   [clojure.java.io :as io]
   [clojure.edn :as edn]))

(defn read-webapp-deps
  "Read webapp dependencies from webapp/deps.edn"
  []
  (let [deps-file (io/file "webapp/deps.edn")]
    (when (.exists deps-file)
      (edn/read-string (slurp deps-file)))))

(defn extract-dev-deps
  "Extract development dependencies from webapp deps.edn structure"
  [webapp-deps]
  (let [dev-alias (get-in webapp-deps [:aliases :dev :extra-deps])
        test-alias (get-in webapp-deps [:aliases :test :extra-deps])]
    (merge dev-alias test-alias)))

(defn load-webapp-dev-deps!
  "Dynamically load webapp :dev and :test dependencies only.

  Production dependencies are already available via :local/root inclusion.
  Reads :dev and :test aliases from webapp/deps.edn."
  []
  (print "Loading webapp :dev and :test dependencies... ")

  (if-let [webapp-deps (read-webapp-deps)]
    (do
      (let [dev-deps (extract-dev-deps webapp-deps)]
        (when (seq dev-deps)
          (deps/add-libs dev-deps)
          (println (count dev-deps) "dev deps loaded.")))

      ;; Load webapp's dev utils
      (try
        (require '[repl :as webapp-repl])
        (println "webapp utilities loaded ✓")
        (catch Exception e
          (println "webapp utilities failed ⚠")))

      (println "Development environment ready. Use (go) to start system."))

    (println "✗ Could not read webapp/deps.edn")))

(defn dev-webapp!
  "Ultra-fast startup - just load deps and start system.
  Uses webapp utilities with minimal setup validation."
  []
  (println "⚡ LIPAS Dev setup")
  (load-webapp-dev-deps!)
  (require '[lipas.backend.system])
  ((requiring-resolve 'repl/reset))
  (println "⚡ Dev System ready!"))

;;; NOTE: webapp-repl contains several handy development utilities! ***
