(ns lipas.jobs.core-database-test
  "Unit tests for core database operations in the unified job queue system."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.core :as jobs]
   [lipas.test-utils :as test-utils]
   [next.jdbc :as jdbc]
   [taoensso.timbre :as log]))

;; Test system setup
(defonce test-system (atom nil))

(defn setup-test-system! []
  ;; Ensure database is properly initialized
  (test-utils/ensure-test-database!)
  ;; Initialize test system using test config (with _test database suffix)
  (reset! test-system
          (ig/init (select-keys (config/->system-config test-utils/config) [:lipas/db]))))

(defn teardown-test-system! []
  (when @test-system
    (ig/halt! @test-system)
    (reset! test-system nil)))

(use-fixtures :once
  (fn [f]
    (setup-test-system!)
    (f)
    (teardown-test-system!)))

(use-fixtures :each
  (fn [f]
    ;; Clean all tables before each test
    (test-utils/prune-db! (:lipas/db @test-system))
    (f)))

;; Helper functions
(defn test-db [] (:lipas/db @test-system))

(defn get-job-by-id [db id]
  (first (jdbc/execute! db ["SELECT * FROM jobs WHERE id = ?" id])))

(defn get-all-jobs [db]
  (jdbc/execute! db ["SELECT * FROM jobs ORDER BY created_at"]))

;; =============================================================================
;; Core Database Operation Tests
;; =============================================================================

(deftest job-enqueueing-test
  (testing "Job enqueueing with various parameters"
    (let [db (test-db)]

      ;; Test basic enqueueing
      (testing "Basic job enqueueing"
        (let [job-result (jobs/enqueue-job! db "email" {:to "test@example.com" :subject "Test Email" :body "This is a test email"})
              job-id (:id job-result)]
          (is (pos-int? job-id))
          (let [job (get-job-by-id db job-id)]
            (is (= "email" (:jobs/type job)))
            (is (= {:to "test@example.com" :subject "Test Email" :body "This is a test email"} (:jobs/payload job)))
            (is (= 100 (:jobs/priority job))) ; default priority
            (is (= "pending" (:jobs/status job))))))

      ;; Test with custom priority
      (testing "Job enqueueing with custom priority"
        (let [job-result (jobs/enqueue-job! db "analysis" {:lipas-id 123} {:priority 50})
              job-id (:id job-result)]
          (is (pos-int? job-id))
          (let [job (get-job-by-id db job-id)]
            (is (= 50 (:jobs/priority job))))))

      ;; Test with custom max-attempts
      (testing "Job enqueueing with custom max-attempts"
        (let [job-result (jobs/enqueue-job! db "webhook" {} {:max-attempts 5})
              job-id (:id job-result)]
          (is (pos-int? job-id))
          (let [job (get-job-by-id db job-id)]
            (is (= 5 (:jobs/max_attempts job))))))

      ;; Test with future run-at
      (testing "Job enqueueing with future run-at time"
        (let [future-time (java.sql.Timestamp/valueOf "2025-12-31 23:59:59")
              job-result (jobs/enqueue-job! db "cleanup-jobs" {} {:run-at future-time})
              job-id (:id job-result)]
          (is (pos-int? job-id))
          (let [job (get-job-by-id db job-id)]
            ;; Should be scheduled for future
            (is (= future-time (:jobs/run_at job)))))))))

(deftest job-fetching-test
  (testing "Job fetching with filters and priorities"
    (let [db (test-db)]

      ;; Enqueue jobs with different priorities
      (let [high-pri-result (jobs/enqueue-job! db "email" {:to "high@example.com" :subject "High Priority" :body "Priority test"} {:priority 95})
            high-pri-id (:id high-pri-result)
            med-pri-result (jobs/enqueue-job! db "analysis" {:lipas-id 123} {:priority 50})
            med-pri-id (:id med-pri-result)
            low-pri-result (jobs/enqueue-job! db "cleanup-jobs" {:days-old 7} {:priority 30})
            low-pri-id (:id low-pri-result)]

        ;; Test priority ordering
        (testing "Jobs fetched in priority order"
          (let [jobs (jobs/fetch-next-jobs db {:limit 2})]
            (is (= 2 (count jobs)))
            (is (>= (:priority (first jobs)) (:priority (second jobs))))
            ;; Highest priority should be first
            (is (= 95 (:priority (first jobs))))))

        ;; Test job-type filtering  
        (testing "Job type filtering"
          ;; Clean slate - re-enqueue for clean test
          (test-utils/prune-db! db)
          (jobs/enqueue-job! db "email" {:to "test1@example.com" :subject "Test 1" :body "Body 1"})
          (jobs/enqueue-job! db "email" {:to "test2@example.com" :subject "Test 2" :body "Body 2"})
          (jobs/enqueue-job! db "analysis" {:lipas-id 456})

          (let [email-jobs (jobs/fetch-next-jobs db {:job-types ["email"] :limit 5})]
            (is (= 2 (count email-jobs)))
            (is (every? #(= "email" (:type %)) email-jobs))))

        ;; Test that fetched jobs are marked as processing
        (testing "Fetched jobs are marked as processing"
          (test-utils/prune-db! db)
          (jobs/enqueue-job! db "email" {:to "test@example.com" :subject "Test Email" :body "This is a test email"})

          (let [jobs (jobs/fetch-next-jobs db {:limit 1})]
            (is (= 1 (count jobs)))
            ;; fetch-next-jobs marks jobs as processing and returns them, but the returned
            ;; jobs don't include status (that's only in the database)
            (is (= 1 (:attempts (first jobs))))
            ;; Verify the job is actually marked as processing in database
            (let [job-in-db (get-job-by-id db (:id (first jobs)))]
              (is (= "processing" (:jobs/status job-in-db))))))))))

(deftest job-status-transitions-test
  (testing "Job status transitions and lifecycle"
    (let [db (test-db)]

      ;; Test completion
      (testing "Job completion"
        (let [job-result (jobs/enqueue-job! db "email" {:to "test@example.com" :subject "Test Email" :body "This is a test email"})
              job-id (:id job-result)]
          ;; Fetch job (marks as processing)
          (let [jobs (jobs/fetch-next-jobs db {:limit 1})
                job (first jobs)]
            ;; Verify job was fetched and status updated in database
            (is (= 1 (count jobs)))
            (let [job-in-db (get-job-by-id db (:id job))]
              (is (= "processing" (:jobs/status job-in-db)))))

          ;; Mark completed
          (jobs/mark-completed! db job-id)
          (let [completed-job (get-job-by-id db job-id)]
            (is (= "completed" (:jobs/status completed-job)))
            (is (inst? (:jobs/completed_at completed-job)))
            (is (nil? (:jobs/error_message completed-job))))))

      ;; Test failure with retry
      (testing "Job failure and retry logic"
        (let [job-result (jobs/enqueue-job! db "analysis" {:lipas-id 789} {:max-attempts 3})
              job-id (:id job-result)]
          ;; Fetch and fail
          (jobs/fetch-next-jobs db {:limit 1})
          (jobs/mark-failed! db job-id "Test error message")

          (let [failed-job (get-job-by-id db job-id)]
            (is (= "failed" (:jobs/status failed-job)))
            (is (= 1 (:jobs/attempts failed-job)))
            (is (= "Test error message" (:jobs/error_message failed-job)))
            ;; Should have retry delay
            (is (inst? (:jobs/run_at failed-job))))))

      ;; Test dead letter after max attempts
      (testing "Dead letter queue after max attempts"
        (let [job-result (jobs/enqueue-job! db "webhook" {:lipas-ids [123]} {:max-attempts 1})
              job-id (:id job-result)]
          ;; Fetch and fail (should go to dead immediately)
          (jobs/fetch-next-jobs db {:limit 1})
          (jobs/mark-failed! db job-id "Final failure")

          (let [dead-job (get-job-by-id db job-id)]
            (is (= "dead" (:jobs/status dead-job)))
            (is (= 1 (:jobs/attempts dead-job)))
            (is (= "Final failure" (:jobs/error_message dead-job)))))))))

(deftest concurrent-job-fetching-test
  (testing "Concurrent job fetching uses database locking correctly"
    (let [db (test-db)]
      ;; Enqueue multiple jobs
      (doseq [i (range 5)]
        (jobs/enqueue-job! db "email" {:to (str "test" i "@example.com") :subject (str "Test " i) :body (str "Body " i)}))

      ;; Fetch jobs concurrently - should get different jobs due to SKIP LOCKED
      (let [batch1 (jobs/fetch-next-jobs db {:limit 2})
            batch2 (jobs/fetch-next-jobs db {:limit 2})]
        (is (= 2 (count batch1)))
        (is (= 2 (count batch2)))

        ;; Should have different job IDs (no overlap)
        (let [batch1-ids (set (map :id batch1))
              batch2-ids (set (map :id batch2))]
          (is (empty? (clojure.set/intersection batch1-ids batch2-ids))))))))

(deftest queue-stats-test
  (testing "Queue statistics reporting"
    (let [db (test-db)]
      ;; Enqueue jobs in different states
      (let [pending-result (jobs/enqueue-job! db "email" {:to "pending@example.com" :subject "Pending" :body "Pending test"})
            pending-id (:id pending-result)
            processing-result (jobs/enqueue-job! db "analysis" {:lipas-id 123})
            processing-id (:id processing-result)
            completed-result (jobs/enqueue-job! db "cleanup-jobs" {:days-old 7})
            completed-id (:id completed-result)]

        ;; Mark one as processing, one as completed  
        (jobs/fetch-next-jobs db {:limit 1}) ; processing-id becomes processing
        (jobs/fetch-next-jobs db {:limit 1}) ; completed-id becomes processing
        (jobs/mark-completed! db completed-id)

        ;; Check stats
        (let [stats (jobs/get-queue-stats db)]
          (is (map? stats))
          ;; Should have various status entries (they're keyed by status name)
          (is (or (contains? stats :pending) (contains? stats :processing) (contains? stats :completed)))
          ;; Check that we have total stats
          (is (contains? stats :total)))))))
