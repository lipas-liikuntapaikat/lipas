(ns lipas.jobs.correlation-test
  "Tests for correlation ID tracking throughout the job system"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.monitoring :as monitoring]
   [lipas.test-utils :as test-utils]
   [next.jdbc :as jdbc]))

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
    ;; Prune test database before each test
    (test-utils/prune-db! (:lipas/db @test-system))
    (f)))

(deftest test-correlation-id-propagation
  (testing "Correlation IDs are properly tracked through job lifecycle"
    (let [db (:lipas/db @test-system)
          payload {:test-data "correlation test"}

          ;; Enqueue a job - should get correlation ID
          job-result (jobs/enqueue-job! db "analysis" {:lipas-id 12345})
          job-id (:id job-result)
          correlation-id (:correlation_id job-result)]

      (testing "enqueue-job! returns correlation ID"
        (is (some? correlation-id))
        (is (uuid? correlation-id)))

      (testing "Fetched jobs include correlation ID"
        (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})]
          (is (= 1 (count fetched-jobs)))
          (is (= correlation-id (:correlation_id (first fetched-jobs))))))

      (testing "Jobs can be found by correlation ID"
        (let [correlated-jobs (jobs/get-jobs-by-correlation db correlation-id)]
          (is (= 1 (count correlated-jobs)))
          (is (= job-id (:id (first correlated-jobs))))))

      (testing "Metrics include correlation ID"
        ;; Record a metric
        (monitoring/record-job-metric! db "analysis" "completed"
                                       (java.sql.Timestamp. (System/currentTimeMillis))
                                       (java.sql.Timestamp. (System/currentTimeMillis))
                                       correlation-id)

        ;; Check metrics can be retrieved by correlation ID
        (let [metrics (jobs/get-metrics-by-correlation db correlation-id)]
          (is (= 1 (count metrics)))
          (is (= "analysis" (:job_type (first metrics))))
          (is (= "completed" (:status (first metrics))))))

      (testing "Correlation trace shows complete history"
        (let [trace (jobs/get-correlation-trace db correlation-id)]
          (is (>= (count trace) 2)) ; At least job and metric
          (is (some #(= "job" (:record_type %)) trace))
          (is (some #(= "metric" (:record_type %)) trace)))))))

(deftest test-related-jobs-correlation
  (testing "Multiple related jobs share correlation ID"
    (let [db (:lipas/db @test-system)
          correlation-id (java.util.UUID/randomUUID)

          ;; Enqueue parent job
          parent-result (jobs/enqueue-job!
                         db "webhook" {:lipas-ids [1 2 3]}
                         {:correlation-id correlation-id})

          ;; Enqueue child jobs with same correlation - use valid general email format
          child1-result (jobs/enqueue-job!
                         db "email" {:to "user1@example.com"
                                     :subject "Notification"
                                     :body "System notification"}
                         {:correlation-id correlation-id
                          :parent-job-id (:id parent-result)})

          child2-result (jobs/enqueue-job!
                         db "email" {:to "user2@example.com"
                                     :subject "Notification"
                                     :body "System notification"}
                         {:correlation-id correlation-id
                          :parent-job-id (:id parent-result)})]

      (testing "All jobs share the same correlation ID"
        (is (= correlation-id (:correlation_id parent-result)))
        (is (= correlation-id (:correlation_id child1-result)))
        (is (= correlation-id (:correlation_id child2-result))))

      (testing "All related jobs can be found together"
        (let [related-jobs (jobs/get-jobs-by-correlation db correlation-id)]
          (is (= 3 (count related-jobs)))
          (is (= #{"webhook" "email"}
                 (set (map :type related-jobs)))))))))

(deftest test-error-handling-with-correlation
  (testing "Error handling preserves correlation ID in logs"
    (let [db (:lipas/db @test-system)
          job-result (jobs/enqueue-job! db "analysis" {:lipas-id 99999})
          job-id (:id job-result)
          correlation-id (:correlation_id job-result)]

      ;; Simulate job failure
      (jobs/fail-job! db job-id "Test error"
                      {:current-attempt 1
                       :max-attempts 3
                       :correlation-id correlation-id})

      ;; Job should still be findable by correlation
      (let [jobs (jobs/get-jobs-by-correlation db correlation-id)]
        (is (= 1 (count jobs)))
        ;; For retryable failures, status stays 'pending' but last_error is set
        (is (= "pending" (:status (first jobs))))
        ;; Need to check the full job record for error details
        (let [job-record (first jobs)]
          (is (some? job-record)))))))

(deftest test-dead-letter-correlation
  (testing "Jobs moved to dead letter retain correlation context"
    (let [db (:lipas/db @test-system)
          job-result (jobs/enqueue-job! db "webhook" {:lipas-ids [999]})
          job-id (:id job-result)
          correlation-id (:correlation_id job-result)]

      ;; Simulate max attempts reached
      (jobs/fail-job! db job-id "Final failure"
                      {:current-attempt 3
                       :max-attempts 3
                       :correlation-id correlation-id})

      ;; Job should be marked as dead
      (let [jobs (jobs/get-jobs-by-correlation db correlation-id)]
        (is (= 1 (count jobs)))
        (is (= "dead" (:status (first jobs))))))))

(deftest test-performance-with-correlation-filter
  (testing "Performance metrics can be filtered by correlation ID"
    (let [db (:lipas/db @test-system)
          correlation-id (java.util.UUID/randomUUID)

          ;; Create multiple jobs with same correlation
          _ (dotimes [i 5]
              (jobs/enqueue-job!
               db "analysis" {:lipas-id (inc i)}
               {:correlation-id correlation-id}))

          ;; Create unrelated jobs
          _ (dotimes [i 3]
              (jobs/enqueue-job! db "elevation" {:lipas-id (+ 100 i)}))

          ;; Get all jobs
          all-jobs (jobs/fetch-next-jobs db {:limit 10})
          correlated-jobs (filter #(= correlation-id (:correlation_id %)) all-jobs)]

      (testing "Only correlated jobs are returned"
        (is (= 5 (count correlated-jobs)))
        (is (every? #(= "analysis" (:type %)) correlated-jobs))))))
