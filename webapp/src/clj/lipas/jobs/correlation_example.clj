(ns lipas.jobs.correlation-example
  "Example demonstrating how correlation IDs improve traceability in the jobs system."
  (:require
   [lipas.jobs.core :as jobs]
   [clojure.java.jdbc :as jdbc]))

(defn trace-sports-site-save
  "Trace all jobs created by a sports site save operation."
  [db correlation-id]
  (println "\n=== Tracing sports site save operation ===")
  (println "Correlation ID:" correlation-id)
  (println)

  (let [jobs (jobs/get-jobs-by-correlation db correlation-id)]
    (doseq [job jobs]
      (println (format "Job #%d - %s [%s]"
                       (:id job)
                       (:type job)
                       (:status job)))
      (println "  Created:" (:created_at job))
      (when (:started_at job)
        (println "  Started:" (:started_at job)))
      (when (:completed_at job)
        (println "  Completed:" (:completed_at job)))
      (when (:error_message job)
        (println "  Error:" (:error_message job)))
      (println))

    (println "Total jobs:" (count jobs))
    (println "By status:" (frequencies (map :status jobs)))
    (println "By type:" (frequencies (map :type jobs)))))

(defn get-complete-trace
  "Get a unified timeline of all activity for a correlation ID."
  [db correlation-id]
  (println "\n=== Complete trace for correlation ID ===")
  (println correlation-id)
  (println)

  (let [trace (jobs/get-correlation-trace db correlation-id)]
    (doseq [event trace]
      (println (format "[%s] %s - %s (%s)"
                       (:timestamp event)
                       (:source event)
                       (:description event)
                       (:type event))))))

(defn example-query-by-correlation
  "Example SQL queries to find jobs by correlation ID."
  [db correlation-id]
  ;; Find all jobs
  (jdbc/query db
              ["SELECT id, type, status, created_at, completed_at
                FROM jobs 
                WHERE correlation_id = ?
                ORDER BY created_at"
               correlation-id])

  ;; Find failed jobs
  (jdbc/query db
              ["SELECT id, type, error_message, attempts
                FROM jobs 
                WHERE correlation_id = ? 
                AND status IN ('failed', 'dead')"
               correlation-id])

  ;; Calculate total processing time
  (jdbc/query db
              ["SELECT 
                  MIN(created_at) as first_job_created,
                  MAX(completed_at) as last_job_completed,
                  EXTRACT(EPOCH FROM (MAX(completed_at) - MIN(created_at))) as total_seconds
                FROM jobs 
                WHERE correlation_id = ?"
               correlation-id]))

(comment
  ;; Usage example:

  ;; When a sports site is saved, it generates a correlation ID like:
  ;; #uuid "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

  ;; Later, you can trace all related jobs:
  (trace-sports-site-save db #uuid "a1b2c3d4-e5f6-7890-abcd-ef1234567890")

  ;; Output would show something like:
  ;; === Tracing sports site save operation ===
  ;; Correlation ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
  ;; 
  ;; Job #1001 - analysis [completed]
  ;;   Created: 2025-06-29 10:00:00
  ;;   Started: 2025-06-29 10:00:01
  ;;   Completed: 2025-06-29 10:00:15
  ;; 
  ;; Job #1002 - elevation [completed]
  ;;   Created: 2025-06-29 10:00:00
  ;;   Started: 2025-06-29 10:00:01
  ;;   Completed: 2025-06-29 10:00:45
  ;; 
  ;; Job #1003 - webhook [completed]
  ;;   Created: 2025-06-29 10:00:00
  ;;   Started: 2025-06-29 10:00:02
  ;;   Completed: 2025-06-29 10:00:03
  ;; 
  ;; Total jobs: 3
  ;; By status: {"completed" 3}
  ;; By type: {"analysis" 1, "elevation" 1, "webhook" 1}
  )
