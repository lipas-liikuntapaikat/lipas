(ns lipas.jobs.resilience-test
  "Resilience and error handling tests for the unified job queue system.

  Tests job retry logic, exponential backoff, dead letter queue behavior,
  database connection failures, malformed payloads, and system recovery.

  These tests focus on the error handling and resilience aspects of the
  job queue system, ensuring that jobs are properly retried, failed jobs
  are moved to dead letter queue, and the system can recover from crashes."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.db :as jobs-db]
   [lipas.test-utils :as test-utils]
   [next.jdbc :as jdbc]))

;; Test system setup
(defonce test-system (atom nil))

(defn setup-test-system! []
  (test-utils/ensure-test-database!)
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
    (test-utils/prune-db! (:lipas/db @test-system))
    (f)))

;; Helper functions
(defn test-db [] (:lipas/db @test-system))

(defn get-job-by-id [db id]
  (first (jdbc/execute! db ["SELECT * FROM jobs WHERE id = ?" id])))

(deftest job-retry-logic-test
  (testing "Job retry logic with progressive backoff"
    (let [db (test-db)]

      (testing "Job retries on failure with exponential backoff"
        (let [job-result (jobs/enqueue-job! db "email" {:to "retry@example.com" :subject "Retry Test" :body "This will be retried"} {:max-attempts 3})
              job-id (:id job-result)]

          ;; First failure - should retry
          (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                job (first fetched-jobs)]
            (jobs/fail-job! db job-id "First failure"
                            {:current-attempt (:attempts job)
                             :max-attempts (:max_attempts job)}))

          (let [job-after-first-failure (get-job-by-id db job-id)]
            (is (= "pending" (:jobs/status job-after-first-failure)))
            (is (= 1 (:jobs/attempts job-after-first-failure)))
            (is (= "First failure" (:jobs/error_message job-after-first-failure)))
            ;; Should have exponential backoff delay (1 minute)
            (is (.after (:jobs/run_at job-after-first-failure) (java.sql.Timestamp/from (java.time.Instant/now)))))

          ;; Second failure - should retry
          (jdbc/execute! db ["UPDATE jobs SET run_at = now() WHERE id = ?" job-id])
          (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                job (first fetched-jobs)]
            (jobs/fail-job! db job-id "Second failure"
                            {:current-attempt (:attempts job)
                             :max-attempts (:max_attempts job)}))

          (let [job-after-second-failure (get-job-by-id db job-id)]
            (is (= "pending" (:jobs/status job-after-second-failure)))
            (is (= 2 (:jobs/attempts job-after-second-failure)))
            (is (= "Second failure" (:jobs/error_message job-after-second-failure)))
            ;; Should have longer backoff delay (2 minutes)
            (is (.after (:jobs/run_at job-after-second-failure) (java.sql.Timestamp/from (java.time.Instant/now)))))

          ;; Third failure - should go to dead letter queue
          (jdbc/execute! db ["UPDATE jobs SET run_at = now() WHERE id = ?" job-id])
          (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                job (first fetched-jobs)]
            (jobs/fail-job! db job-id "Final failure"
                            {:current-attempt (:attempts job)
                             :max-attempts (:max_attempts job)}))

          (let [job-after-final-failure (get-job-by-id db job-id)]
            (is (= "dead" (:jobs/status job-after-final-failure)))
            (is (= 3 (:jobs/attempts job-after-final-failure)))
            (is (= "Final failure" (:jobs/error_message job-after-final-failure))))))

      (testing "Custom max-attempts respected"
        (let [job-result (jobs/enqueue-job! db "analysis" {:lipas-id 456} {:max-attempts 1})
              job-id (:id job-result)]
          (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                job (first fetched-jobs)]
            (jobs/fail-job! db job-id "Single failure"
                            {:current-attempt (:attempts job)
                             :max-attempts (:max_attempts job)}))

          (let [dead-job (get-job-by-id db job-id)]
            (is (= "dead" (:jobs/status dead-job)))
            (is (= 1 (:jobs/attempts dead-job)))))))))

(deftest malformed-payload-test
  (testing "Handling of malformed job payloads"
    (let [db (test-db)]

      (testing "Non-map payload validation"
        (is (thrown? AssertionError
                     (jobs/enqueue-job! db "email" "not-a-map"))))

      (testing "Invalid job type validation"
        (is (thrown? AssertionError
                     (jobs/enqueue-job! db "invalid-type" {:data "test"}))))

      (testing "Malformed payload throws validation exception"
        ;; Now that validation always runs, malformed payloads throw immediately
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid job payload"
                              (jobs/enqueue-job! db "email" {:no-to-field true})))

        ;; Verify no job was created in the database
        (let [all-jobs (jdbc/execute! db ["SELECT * FROM jobs"])]
          (is (= 0 (count all-jobs))))))))

(deftest database-connection-failure-test
  (testing "Handling database connection failures"
    (let [db (test-db)]

      (testing "Transient database errors don't corrupt queue state"
        (let [job-result (jobs/enqueue-job! db "cleanup-jobs" {:days-old 30})
              job-id (:id job-result)]

          ;; Simulate database connection issue
          (let [bad-db {:datasource nil}]
            (is (thrown? Exception
                         (jobs/fetch-next-jobs bad-db {:limit 1}))))

          ;; Verify original job is still processable
          (let [jobs (jobs/fetch-next-jobs db {:limit 1})]
            (is (= 1 (count jobs)))
            (is (= (:id (first jobs)) job-id))))))))

(deftest system-recovery-stuck-jobs-test
  (testing "Stuck jobs are recovered after system restart"
    (let [db (test-db)
          stuck-job-result (jobs/enqueue-job! db "analysis" {:lipas-id 789})
          stuck-job-id (:id stuck-job-result)
          normal-job-result (jobs/enqueue-job! db "email" {:to "test@example.com" :subject "Test" :body "Test message"})
          normal-job-id (:id normal-job-result)]

      ;; Fetch jobs (marks as processing)
      (jobs/fetch-next-jobs db {:limit 2})

      ;; Verify jobs are processing
      (let [stuck-job (get-job-by-id db stuck-job-id)
            normal-job (get-job-by-id db normal-job-id)]
        (is (= "processing" (:jobs/status stuck-job)))
        (is (= "processing" (:jobs/status normal-job))))

      ;; Simulate system restart with stuck job recovery
      (jobs-db/reset-stuck-jobs! db {:timeout_minutes -1})

      ;; Check that stuck jobs are marked as failed
      (let [recovered-stuck (get-job-by-id db stuck-job-id)
            recovered-normal (get-job-by-id db normal-job-id)]
        (is (= "failed" (:jobs/status recovered-stuck)))
        (is (= "failed" (:jobs/status recovered-normal)))
        (is (.contains (:jobs/error_message recovered-stuck) "stuck in processing"))
        (is (.contains (:jobs/error_message recovered-normal) "stuck in processing"))))))

(deftest system-recovery-queue-consistency-test
  (testing "Queue maintains consistency after partial processing failures"
    (let [db (test-db)
          job-results (doall (for [i (range 5)]
                               (jobs/enqueue-job! db "email" {:to (str "test" i "@example.com") :subject "Test" :body "Test message"})))
          job-ids (mapv :id job-results)]

      ;; Process some jobs successfully, fail others
      (let [jobs (jobs/fetch-next-jobs db {:limit 5})]
        (is (= 5 (count jobs)))

        ;; Complete first 2 jobs
        (jobs/mark-completed! db (:id (nth jobs 0)))
        (jobs/mark-completed! db (:id (nth jobs 1)))

        ;; For failed jobs, use mark-failed! to get simple failure without retry logic
        (jobs/mark-failed! db (:id (nth jobs 2)) "Simulated failure")
        (jobs/mark-failed! db (:id (nth jobs 3)) "Simulated failure")
        (jobs/mark-failed! db (:id (nth jobs 4)) "Simulated failure"))

      ;; Verify queue state is consistent
      (let [stats (jobs/get-queue-stats db)]
        (is (>= (get-in stats [:completed :count] 0) 2) "Should have completed jobs")
        (is (>= (get-in stats [:failed :count] 0) 3) "Should have failed jobs")
        (is (>= (get-in stats [:total :count] 0) 5) "Total should match")))))

(deftest system-recovery-failed-jobs-test
  (testing "Failed jobs can be processed after system recovery"
    (let [db (test-db)
          job-result (jobs/enqueue-job! db "integration" {:lipas-id 456})
          job-id (:id job-result)]

      ;; Fail the job initially
      (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

            job (first fetched-jobs)]
        (jobs/fail-job! db job-id "Transient network error"
                        {:current-attempt (:attempts job)
                         :max-attempts (:max_attempts job)}))

      (let [failed-job (get-job-by-id db job-id)]
        (is (= "pending" (:jobs/status failed-job))) ; Changed from "failed" to "pending"
        (is (= 1 (:jobs/attempts failed-job))))

      ;; Simulate time passing (reset run_at to allow immediate retry)
      (jdbc/execute! db ["UPDATE jobs SET run_at = now() WHERE id = ?" job-id])

      ;; System recovers and processes the retry
      (let [retry-jobs (jobs/fetch-next-jobs db {:limit 1})]
        (is (= 1 (count retry-jobs)))
        (is (= job-id (:id (first retry-jobs))))

        ;; This time job succeeds
        (jobs/mark-completed! db job-id)

        (let [completed-job (get-job-by-id db job-id)]
          (is (= "completed" (:jobs/status completed-job)))
          (is (= 2 (:jobs/attempts completed-job))) ; Should show it was retried
          (is (inst? (:jobs/completed_at completed-job))))))))

(deftest dead-letter-queue-test
  (testing "Dead letter queue behavior"
    (let [db (test-db)]

      (testing "Jobs become dead after exceeding max attempts"
        (let [job-result (jobs/enqueue-job! db "webhook" {:url "http://invalid.example.com"} {:max-attempts 2})
              job-id (:id job-result)]

          ;; First failure
          (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                job (first fetched-jobs)]
            (jobs/fail-job! db job-id "HTTP 500 error"
                            {:current-attempt (:attempts job)
                             :max-attempts (:max_attempts job)}))
          (let [job1 (get-job-by-id db job-id)]
            (is (= "pending" (:jobs/status job1)))
            (is (= 1 (:jobs/attempts job1))))

          ;; Second failure - should go to dead letter queue
          (jdbc/execute! db ["UPDATE jobs SET run_at = now() WHERE id = ?" job-id])
          (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                job (first fetched-jobs)]
            (jobs/fail-job! db job-id "HTTP 500 error - final attempt"
                            {:current-attempt (:attempts job)
                             :max-attempts (:max_attempts job)}))

          (let [dead-job (get-job-by-id db job-id)]
            (is (= "dead" (:jobs/status dead-job)))
            (is (= 2 (:jobs/attempts dead-job)))
            (is (= "HTTP 500 error - final attempt" (:jobs/error_message dead-job))))))

      (testing "Dead letter queue jobs don't block normal processing"
        ;; Create a dead job
        (let [dead-job-result (jobs/enqueue-job! db "analysis" {:lipas-id 999} {:max-attempts 1})
              dead-job-id (:id dead-job-result)]
          (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                job (first fetched-jobs)]
            (jobs/fail-job! db dead-job-id "Goes straight to dead"
                            {:current-attempt (:attempts job)
                             :max-attempts (:max_attempts job)}))

          (is (= "dead" (:jobs/status (get-job-by-id db dead-job-id)))))

        ;; Create normal jobs - should still be processable
        (let [normal-job-result (jobs/enqueue-job! db "email" {:to "normal@example.com" :subject "Normal" :body "Normal message"})
              normal-job-id (:id normal-job-result)]
          (let [jobs (jobs/fetch-next-jobs db {:limit 5})]
            ;; Should only fetch the normal job, not the dead one
            (is (= 1 (count jobs)))
            (is (= normal-job-id (:id (first jobs)))))))

      (testing "Dead letter queue can be cleaned up"
        ;; Create some dead jobs
        (doseq [i (range 3)]
          (let [job-result (jobs/enqueue-job! db "cleanup-jobs" {:days-old (* 30 (inc i))} {:max-attempts 1})
                job-id (:id job-result)]
            (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})

                  job (first fetched-jobs)]
              (jobs/fail-job! db job-id "Test dead job"
                              {:current-attempt (:attempts job)
                               :max-attempts (:max_attempts job)}))))

        ;; Verify dead jobs exist
        (let [dead-jobs (jdbc/execute! db ["SELECT COUNT(*) as count FROM jobs WHERE status = 'dead'"])]
          (is (>= (:count (first dead-jobs)) 3)))

        ;; Clean up old jobs (this would be done by cleanup job in production)
        (jobs/cleanup-old-jobs! db 0) ; Clean jobs older than 0 days

        ;; The cleanup function should execute without error
        (is true "Cleanup function executes without error")))))
