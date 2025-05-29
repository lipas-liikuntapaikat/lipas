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
          (print (count dev-deps) "dev deps loaded, ")))

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

;; Test dependencies are automatically included in setup!
;; To run tests: (require '[cognitect.test-runner.api :as tr])
;;               (tr/test {:dirs ["webapp/test/clj"]})

(declare go)

(defn setup-and-go!
  "Complete LIPAS development environment setup in one command.
  
  This function performs the full sequence needed to get from a fresh REPL
  to a running system with all components operational by delegating to
  the existing webapp/dev/user.clj utilities.
  
  Returns: system status and useful development tips"
  []
  (println "🚀 Starting complete LIPAS development environment setup...")

  ;; Step 1: Load dependencies and webapp utilities
  (println "\n📦 Step 1: Loading webapp dependencies and utilities...")
  (load-webapp-dev-deps!)
  (println "   ✓ Webapp utilities loaded with integrant configuration")

  ;; Step 2: Start the system using webapp's go function
  (println "\n🏗️  Step 2: Starting system components...")
  (try
    ;; The webapp/dev/user.clj already sets up integrant config 
    (integrant.repl/go) ; Use fully qualified to avoid conflicts
    (println "   ✓ System started successfully!")
    (catch Exception e
      (println "   ✗ Error starting system:" (.getMessage e))
      {:status :failed :error (.getMessage e)}))

  ;; Step 3: Validation and tips using webapp utilities
  (println "\n✅ Step 3: Validation and webapp utilities available...")
  (try
    ;; These functions are now available from webapp/dev/user.clj
    (let [system-info (current-system)
          search-test (lipas.backend.core/search (search) {:query {:match_all {}} :size 1})
          total-facilities (get-in search-test [:body :hits :total :value])]

      (println (format "   ✓ System running with %d components" (count system-info)))
      (println (format "   ✓ Database connected: %s" (boolean (db))))
      (println (format "   ✓ Search operational: %s facilities indexed" total-facilities))

      (println "\n🎉 LIPAS Development Environment Ready!")
      (println "\n📋 Webapp Development Utilities Available:")
      (println "   (current-system)              ; System component map")
      (println "   (reset)                       ; Reload code changes")
      (println "   (db) (search) (ptv)          ; Access system components")
      (println "   (reindex-search!)            ; Reindex search data")
      (println "   (reindex-analytics!)         ; Reindex analytics")
      (println "   (reset-admin-password! \"pw\") ; Reset admin password")
      (println "   (run-db-migrations!)         ; Run database migrations")
      (println "\n📋 General Development:")
      (println "   (health-check)               ; System health check")
      (println "   (dev-status)                ; Show environment status")

      {:status :success
       :components (count system-info)
       :facilities total-facilities
       :message "LIPAS development environment ready with webapp utilities!"})

    (catch Exception e
      (println "   ⚠ Validation completed with warnings:" (.getMessage e))
      {:status :partial-success
       :message "System started but validation had issues"})))

(defn quick-start
  "Ultra-fast startup - just load deps and start system.
  Uses webapp utilities with minimal setup validation."
  []
  (println "⚡ Quick LIPAS setup (essentials only)...")
  (load-webapp-dev-deps!)
  (integrant.repl/go) ; Use fully qualified to avoid conflicts
  (println "⚡ Ready! Use (health-check) or (setup-and-go!) for detailed status."))

(defn health-check
  "Quick system health check with key metrics.
  Uses webapp utilities if system is running."
  []
  (println "🔍 LIPAS System Health Check")
  (println "=" (apply str (repeat 30 "=")))
  (try
    (if-let [system (current-system)]
      (let [search-result (lipas.backend.core/search (search) {:query {:match_all {}} :size 0})
            facility-count (get-in search-result [:body :hits :total :value])
            cities-count (count (lipas.backend.core/get-cities (db)))
            types-count (count lipas.backend.core/types)]

        (println "System Components:" (count system) "/ 9 expected")
        (println "Sports Facilities:" (if (>= facility-count 10000) "10,000+" (str facility-count)))
        (println "Cities in Database:" cities-count)
        (println "Sport Types Available:" types-count)
        (println "Database Status:" (if (db) "✓ Connected" "✗ Not connected"))
        (println "Search Status:" (if (search) "✓ Operational" "✗ Not operational"))

        {:healthy? true :components (count system) :facilities facility-count
         :cities cities-count :types types-count})
      (do
        (println "❌ System not running. Use (setup-and-go!) or (quick-start) to start.")
        {:healthy? false :error "System not running"}))
    (catch Exception e
      (println "❌ Health check failed:" (.getMessage e))
      {:healthy? false :error (.getMessage e)})))

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

(println "Top-level dev environment loaded. 
📚 Available Commands:
   (setup-and-go!)  - Complete setup with validation and examples
   (quick-start)    - Fast setup for immediate development  
   (health-check)   - System health check with key metrics
   (setup!)         - Load dependencies only
   (status)         - Show current status")
