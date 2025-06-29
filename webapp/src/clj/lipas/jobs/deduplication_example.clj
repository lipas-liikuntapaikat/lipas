(ns lipas.jobs.deduplication-example
  "Example demonstrating job deduplication functionality"
  (:require
   [lipas.jobs.core :as jobs]
   [clojure.java.jdbc :as jdbc]))

(defn demonstrate-deduplication
  "Shows how deduplication prevents duplicate jobs"
  [db]
  (println "\n=== Job Deduplication Example ===\n")

  ;; Example 1: Basic deduplication
  (println "1. Basic deduplication:")
  (let [dedup-key "daily-report-2025-06-29"
        job1 (jobs/enqueue-job! db "email"
                                {:to "admin@lipas.fi"
                                 :subject "Daily Report"
                                 :body "Report for 2025-06-29"}
                                {:dedup-key dedup-key
                                 :priority 50})

        ;; Try to enqueue the same job again
        job2 (jobs/enqueue-job! db "email"
                                {:to "admin@lipas.fi"
                                 :subject "Daily Report (duplicate)"
                                 :body "This should be ignored"}
                                {:dedup-key dedup-key
                                 :priority 50})]

    (println "  First job ID:" (:id job1))
    (println "  Second job result:" (if (:id job2)
                                      (str "ID " (:id job2) " (ERROR: Should be deduped!)")
                                      "nil (correctly deduplicated)"))
    (println))

  ;; Example 2: Different job types can use same dedup key
  (println "2. Different job types with same dedup-key:")
  (let [dedup-key "site-update-12345"
        email-job (jobs/enqueue-job! db "email"
                                     {:to "user@example.com"
                                      :subject "Site updated"}
                                     {:dedup-key dedup-key})

        webhook-job (jobs/enqueue-job! db "webhook"
                                       {:lipas-ids [12345]
                                        :operation-type "update"}
                                       {:dedup-key dedup-key})]

    (println "  Email job ID:" (:id email-job))
    (println "  Webhook job ID:" (:id webhook-job))
    (println "  Both succeeded (different job types)")
    (println))

  ;; Example 3: Completed jobs don't block new ones
  (println "3. Re-running after completion:")
  (let [dedup-key "weekly-cleanup"
        ;; First run
        job1 (jobs/enqueue-job! db "cleanup-jobs"
                                {:days-old 7}
                                {:dedup-key dedup-key})

        ;; Simulate completion
        _ (jobs/mark-completed! db (:id job1))

        ;; Can run again after completion
        job2 (jobs/enqueue-job! db "cleanup-jobs"
                                {:days-old 7}
                                {:dedup-key dedup-key})]

    (println "  First job ID:" (:id job1) "(completed)")
    (println "  Second job ID:" (:id job2) "(new job allowed)")
    (println))

  ;; Example 4: Show current state
  (println "4. Current deduplication state in database:")
  (let [dedup-jobs (jdbc/query db
                               ["SELECT type, dedup_key, status, created_at 
                                 FROM jobs 
                                 WHERE dedup_key IS NOT NULL 
                                 ORDER BY created_at DESC 
                                 LIMIT 10"])]
    (doseq [job dedup-jobs]
      (println (format "  %s | %s | %s | %s"
                       (:type job)
                       (:dedup_key job)
                       (:status job)
                       (:created_at job))))))

(defn use-cases-for-deduplication
  "Examples of when to use deduplication in LIPAS"
  []
  (println "\n=== Deduplication Use Cases ===\n")

  (println "1. Daily/Weekly Reports:")
  (println "   dedup-key: \"daily-stats-report-2025-06-29\"")
  (println "   Prevents multiple identical reports for the same day")
  (println)

  (println "2. Webhook Notifications:")
  (println "   dedup-key: \"webhook-site-12345-update-v2\"")
  (println "   Prevents duplicate webhooks during retries")
  (println)

  (println "3. Batch Processing:")
  (println "   dedup-key: \"monthly-elevation-enrichment-2025-06\"")
  (println "   Ensures batch jobs run only once per period")
  (println)

  (println "4. User-triggered Actions:")
  (println "   dedup-key: \"user-123-export-request-abc\"")
  (println "   Prevents duplicate processing if user clicks button multiple times")
  (println)

  (println "5. Integration Syncs:")
  (println "   dedup-key: \"ptv-sync-site-45678-v3\"")
  (println "   Ensures external system syncs happen only once"))

(comment
  ;; Usage:
  (require '[lipas.backend.config :as config])
  (def db (:db config/default-config))

  ;; Run the demonstration
  (demonstrate-deduplication db)

  ;; Show use cases
  (use-cases-for-deduplication))
