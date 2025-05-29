(ns lipas.migrate-db
  (:require [migratus.core :as migratus]
            [lipas.backend.config :as config]
            [clojure.string :as str])
  (:gen-class))

(def migration-config
  "Migratus configuration using the existing db config"
  {:store :database
   :migration-dir "migrations/"
   :db (:db config/default-config)})

(defn migrate!
  "Run all pending migrations"
  []
  (try
    (println "Running database migrations...")
    (migratus/migrate migration-config)
    (println "Migrations completed successfully!")
    :success
    (catch Exception e
      (println "Migration failed:" (.getMessage e))
      (.printStackTrace e)
      :failed)))

(defn rollback!
  "Rollback the last migration"
  []
  (try
    (println "Rolling back last migration...")
    (migratus/rollback migration-config)
    (println "Rollback completed successfully!")
    :success
    (catch Exception e
      (println "Rollback failed:" (.getMessage e))
      (.printStackTrace e)
      :failed)))

(defn migration-status
  "Show migration status"
  []
  (try
    (println "Migration status:")
    (let [pending (migratus/pending-list migration-config)]

      (if (seq pending)
        (do
          (println "Pending migrations:")
          (doseq [migration pending]
            (println "  ⏳" (:id migration) "-" (:name migration))))
        (println "No pending migrations"))

      {:pending (count pending)
       :status :success})
    (catch Exception e
      (println "Failed to get migration status:" (.getMessage e))
      (.printStackTrace e)
      :failed)))

(defn reset!
  "Reset database - rollback all migrations then migrate up"
  []
  (try
    (println "Resetting database...")
    (migratus/reset migration-config)
    (println "Database reset completed successfully!")
    :success
    (catch Exception e
      (println "Database reset failed:" (.getMessage e))
      (.printStackTrace e)
      :failed)))

(defn create-migration!
  "Create a new migration with the given name"
  [migration-name]
  (try
    (when (str/blank? migration-name)
      (throw (ex-info "Migration name cannot be blank" {:name migration-name})))
    (println "Creating migration:" migration-name)
    (migratus/create migration-config migration-name)
    (println "Migration created successfully!")
    :success
    (catch Exception e
      (println "Failed to create migration:" (.getMessage e))
      (.printStackTrace e)
      :failed)))

(defn up!
  "Migrate up to a specific migration ID"
  [migration-id]
  (try
    (let [id (if (string? migration-id)
               (parse-long migration-id)
               migration-id)]
      (println "Migrating up to:" id)
      (migratus/up migration-config id)
      (println "Migration up completed successfully!")
      :success)
    (catch Exception e
      (println "Migration up failed:" (.getMessage e))
      (.printStackTrace e)
      :failed)))

(defn down!
  "Migrate down a specific migration ID"
  [migration-id]
  (try
    (let [id (if (string? migration-id)
               (parse-long migration-id)
               migration-id)]
      (println "Migrating down:" id)
      (migratus/down migration-config id)
      (println "Migration down completed successfully!")
      :success)
    (catch Exception e
      (println "Migration down failed:" (.getMessage e))
      (.printStackTrace e)
      :failed)))

(defn -main
  "Main entry point for command line usage"
  [& args]
  (let [command (first args)]
    (case command
      "migrate" (migrate!)
      "rollback" (rollback!)
      "up" (up! (second args))
      "down" (down! (second args))
      "reset" (reset!)
      "create" (create-migration! (second args))
      "status" (migration-status)
      ;; Default to migrate
      (nil "") (migrate!)
      (do
        (println "Unknown command:" command)
        (println "Available commands: migrate, rollback, up <id>, down <id>, reset, create <name>, status")
        :failed))))
