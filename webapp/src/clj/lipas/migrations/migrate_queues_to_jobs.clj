(ns lipas.migrations.migrate-queues-to-jobs
  (:require
   [lipas.jobs.core :as jobs]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]
   [taoensso.timbre :as log]))

(defn- get-elevation-queue-entries
  "Fetch all pending elevation queue entries"
  [db]
  (jdbc/execute! db
                 ["SELECT lipas_id, added_at, status FROM elevation_queue WHERE status = 'pending'"]
                 {:builder-fn jdbc-rs/as-unqualified-lower-maps}))

(defn- get-analysis-queue-entries [db]
  "Fetch pending analysis queue entries in batches"
  (jdbc/execute! db
                 ["SELECT lipas_id, added_at, status FROM analysis_queue WHERE status = 'pending'"]
                 {:builder-fn jdbc-rs/as-unqualified-lower-maps}))

(defn- get-email-queue-entries
  "Fetch all email queue entries"
  [db]
  (jdbc/execute! db
                 ["SELECT id, message, created_at FROM email_out_queue"]
                 {:builder-fn jdbc-rs/as-unqualified-lower-maps}))

(defn- migrate-elevation-entries
  "Migrate elevation queue entries to jobs system"
  [db entries]
  (log/info "Migrating elevation queue entries" {:count (count entries)})
  (doseq [{:keys [lipas_id added_at]} entries]
    (try
      (jobs/enqueue-job! db "elevation"
                         {:lipas-id lipas_id}
                         {:run-at added_at
                          :created-by "migration-elevation-queue"
                          :dedup-key (str "elevation-migration-" lipas_id)})
      (log/debug "Migrated elevation job" {:lipas-id lipas_id})
      (catch Exception e
        (log/error "Failed to migrate elevation job"
                   {:lipas-id lipas_id :error (.getMessage e)})
        (throw e)))))

(defn- migrate-analysis-entries
  "Migrate analysis queue entries to jobs system"
  [db entries]
  (log/info "Migrating analysis queue entries" {:count (count entries)})
  (doseq [{:keys [lipas_id added_at]} entries]
    (try
      (jobs/enqueue-job! db "analysis"
                         {:lipas-id lipas_id}
                         {:run-at added_at
                          :created-by "migration-analysis-queue"
                          :dedup-key (str "analysis-migration-" lipas_id)})
      (log/debug "Migrated analysis job" {:lipas-id lipas_id})
      (catch Exception e
        (log/error "Failed to migrate analysis job"
                   {:lipas-id lipas_id :error (.getMessage e)})
        (throw e)))))

(defn- extract-email-data
  "Extract email data from jsonb message field"
  [message]
  (cond
    ;; Handle reminder format
    (and (map? message)
         (= (:type message) "reminder")
         (contains? message :email))
    {:type "reminder"
     :email (:email message)
     :link (:link message)
     :body (:body message)}

    ;; Handle general email format
    (and (map? message) (contains? message :to))
    {:to (:to message)
     :subject (or (:subject message) "Migrated Email")
     :body (str (:body message))}

    ;; Handle simple string messages
    (string? message)
    {:to "admin@lipas.fi"
     :subject "Migrated Email"
     :body message}

    ;; Default case - convert to general format
    :else
    {:to "admin@lipas.fi"
     :subject "Migrated Email"
     :body (str message)}))

(defn- migrate-email-entries
  "Migrate email queue entries to jobs system"
  [db entries]
  (log/info "Migrating email queue entries" {:count (count entries)})
  (doseq [{:keys [id message created_at]} entries]
    (try
      (let [email-data (extract-email-data message)]
        (jobs/enqueue-job! db "email"
                           email-data
                           {:run-at created_at
                            :created-by "migration-email-queue"
                            :dedup-key (str "email-migration-" id)}))
      (log/debug "Migrated email job" {:id id})
      (catch Exception e
        (log/error "Failed to migrate email job"
                   {:id id :error (.getMessage e)})
        (throw e)))))

(defn- backup-old-queues
  "Create backup tables before migration"
  [db]
  (log/info "Creating backup tables")
  ;; Drop existing backup tables if they exist
  (jdbc/execute! db ["DROP TABLE IF EXISTS elevation_queue_backup"])
  (jdbc/execute! db ["DROP TABLE IF EXISTS analysis_queue_backup"])
  (jdbc/execute! db ["DROP TABLE IF EXISTS email_out_queue_backup"])
  ;; Create new backup tables
  (jdbc/execute! db ["CREATE TABLE elevation_queue_backup AS SELECT * FROM elevation_queue"])
  (jdbc/execute! db ["CREATE TABLE analysis_queue_backup AS SELECT * FROM analysis_queue"])
  (jdbc/execute! db ["CREATE TABLE email_out_queue_backup AS SELECT * FROM email_out_queue"]))

(defn- cleanup-old-queues
  "Remove old queue entries after successful migration"
  [db]
  (log/info "Cleaning up old queue tables")
  (jdbc/execute! db ["DELETE FROM elevation_queue WHERE status = 'pending'"])
  (jdbc/execute! db ["DELETE FROM analysis_queue WHERE status = 'pending'"])
  (jdbc/execute! db ["DELETE FROM email_out_queue"]))

(defn migrate-up
  "Migrate old queue systems to unified jobs system"
  [config]
  (let [db (:db config)]
    (log/info "Starting queue migration to jobs system")

    ;; Create backups first
    (backup-old-queues db)

    (let [elevation-entries (get-elevation-queue-entries db)
          analysis-entries (get-analysis-queue-entries db)
          email-entries (get-email-queue-entries db)]

      (log/info "Starting to migrate entries..."
                {:elevation-count (count elevation-entries)
                 :analysis-count (count analysis-entries)
                 :email-count (count email-entries)})

      ;; Migrate each queue type
      (migrate-elevation-entries db elevation-entries)
      (migrate-analysis-entries db analysis-entries)
      (migrate-email-entries db email-entries)

      ;; Remove successfully migrated entries
      (when (> (count elevation-entries) 0)
        (let [ids (map :lipas_id elevation-entries)]
          (jdbc/execute! db [(str "DELETE FROM elevation_queue WHERE lipas_id IN ("
                                  (clojure.string/join "," ids) ")")])))

      (when (> (count analysis-entries) 0)
        (let [ids (map :lipas_id analysis-entries)]
          (jdbc/execute! db [(str "DELETE FROM analysis_queue WHERE lipas_id IN ("
                                  (clojure.string/join "," ids) ")")])))

      (when (> (count email-entries) 0)
        (let [ids (map #(str "'" % "'") (map :id email-entries))]
          (jdbc/execute! db [(str "DELETE FROM email_out_queue WHERE id IN ("
                                  (clojure.string/join "," ids) ")")])))

      ;; No more entries to process
      (log/info "Queue migration completed successfully"
                {:elevation-count (count elevation-entries)
                 :analysis-count (count analysis-entries)
                 :email-count (count email-entries)}))))

(defn migrate-down
  "Rollback migration by restoring from backup tables"
  [config]
  (let [db (:db config)]
    (log/info "Rolling back queue migration")

    ;; Restore from backups
    (jdbc/execute! db ["DELETE FROM elevation_queue"])
    (jdbc/execute! db ["INSERT INTO elevation_queue SELECT * FROM elevation_queue_backup"])

    (jdbc/execute! db ["DELETE FROM analysis_queue"])
    (jdbc/execute! db ["INSERT INTO analysis_queue SELECT * FROM analysis_queue_backup"])

    (jdbc/execute! db ["DELETE FROM email_out_queue"])
    (jdbc/execute! db ["INSERT INTO email_out_queue SELECT * FROM email_out_queue_backup"])

    ;; Remove jobs that were created by migration
    (jdbc/execute! db ["DELETE FROM jobs WHERE created_by LIKE 'migration-%'"])

    ;; Drop backup tables
    (jdbc/execute! db ["DROP TABLE IF EXISTS elevation_queue_backup"])
    (jdbc/execute! db ["DROP TABLE IF EXISTS analysis_queue_backup"])
    (jdbc/execute! db ["DROP TABLE IF EXISTS email_out_queue_backup"])

    (log/info "Queue migration rollback completed")))

(comment
  (repl/run-db-migrations!)
  )
