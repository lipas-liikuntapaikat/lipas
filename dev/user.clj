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
  "Dynamically load webapp development and test dependencies.
  
  Reads dependencies from webapp/deps.edn :dev and :test aliases."
  []
  (print "Loading webapp dev dependencies... ")

  (if-let [webapp-deps (read-webapp-deps)]
    (do
      (let [dev-deps (extract-dev-deps webapp-deps)]
        (when (seq dev-deps)
          (deps/add-libs dev-deps)
          (print (count dev-deps) "deps loaded, ")))

      ;; Import key development functions
      (require '[integrant.repl :refer [reset reset-all halt go clear init prep resume suspend set-prep!]])
      (require '[integrant.repl.state])
      (require '[clojure.tools.namespace.repl :refer [refresh refresh-all clear set-refresh-dirs]])
      (print "workflow functions loaded, ")

      ;; Load webapp's user.clj
      (try
        (load-file "webapp/dev/user.clj")
        (println "webapp utilities loaded ✓")
        (catch Exception e
          (println "webapp utilities failed ⚠")))

      (println "Development environment ready. Use (go) to start system."))

    (println "✗ Could not read webapp/deps.edn")))

(defn load-webapp-test-deps!
  "Load additional test-specific dependencies for running tests in REPL."
  []
  (println "Test environment ready. Use: (require '[cognitect.test-runner.api :as tr]) (tr/test {:dirs [\"webapp/test/clj\"]})"))

(defn dev-status
  "Show current development environment status."
  []
  (println "Development Environment Status:")

  (println "\nAvailable namespaces:")
  (doseq [ns-name (->> (all-ns)
                       (map ns-name)
                       (filter #(re-find #"integrant|tools\.namespace|test-runner" (str %)))
                       sort)]
    (println " " ns-name))

  (println "\nSystem status:")
  (try
    (require '[integrant.repl.state])
    (let [system (resolve 'integrant.repl.state/system)]
      (if (and system @system)
        (println "  System running with keys:" (keys @system))
        (println "  System not running - use (go) to start")))
    (catch Exception _
      (println "  System status unknown"))))

;; Convenience aliases
(def setup! load-webapp-dev-deps!)
(def status dev-status)

(println "Top-level dev environment loaded. Use (setup!) to load webapp dependencies.")
