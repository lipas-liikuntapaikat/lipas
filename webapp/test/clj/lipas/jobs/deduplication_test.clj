(ns lipas.jobs.deduplication-test
  "Tests for job deduplication functionality"
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.core :as jobs]
   [lipas.test-utils :as test-utils]))

(defonce test-system (atom nil))

(defn setup-test-system! []
  ;; Ensure database is properly initialized
  (test-utils/ensure-test-database!)
  ;; Initialize test system using test config (with _test database suffix)
  (reset! test-system
          (ig/init (select-keys (config/->system-config test-utils/config) [:lipas/db])))
  (let [db (:lipas/db @test-system)]
    (test-utils/prune-db! db)))

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
    (let [db (:lipas/db @test-system)]
      (test-utils/prune-db! db)
      (f))))

(defn test-db []
  (:lipas/db @test-system))

(deftest deduplication-basic-test
  (testing "Jobs with same dedup-key are deduplicated"
    (let [db (test-db)
          dedup-key "test-dedup-123"

          ;; First job should succeed
          job1 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Test 1"
                                   :body "Test email body"}
                                  {:dedup-key dedup-key})

          ;; Second job with same dedup-key should be ignored
          job2 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Test 2"
                                   :body "Different body"}
                                  {:dedup-key dedup-key})]

      ;; First job should have an ID
      (is (some? (:id job1)))

      ;; Second job should return nil (deduped)
      (is (nil? job2))

      ;; Only one job should exist
      (let [jobs (jdbc/query db ["SELECT * FROM jobs WHERE dedup_key = ?" dedup-key])]
        (is (= 1 (count jobs)))
        (is (= "Test 1" (get-in (first jobs) [:payload :subject])))))))

(deftest deduplication-different-types-test
  (testing "Different job types with same dedup-key are not deduplicated"
    (let [db (test-db)
          dedup-key "test-dedup-456"

          job1 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Test email"
                                   :body "Email body"}
                                  {:dedup-key dedup-key})

          job2 (jobs/enqueue-job! db "webhook"
                                  {:lipas-ids [123]}
                                  {:dedup-key dedup-key})]

      (is (some? (:id job1)))
      (is (some? (:id job2)))
      (is (not= (:id job1) (:id job2)))

      ;; Both jobs should exist
      (let [jobs (jdbc/query db ["SELECT * FROM jobs WHERE dedup_key = ?" dedup-key])]
        (is (= 2 (count jobs)))))))

(deftest deduplication-completed-jobs-test
  (testing "Completed jobs don't prevent new jobs with same dedup-key"
    (let [db (test-db)
          dedup-key "test-dedup-789"

          ;; Create and complete first job
          job1 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "First"
                                   :body "First email body"}
                                  {:dedup-key dedup-key})
          _ (jobs/mark-completed! db (:id job1))

          ;; Should be able to enqueue new job with same dedup-key
          job2 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Second"
                                   :body "Second email body"}
                                  {:dedup-key dedup-key})]

      (is (some? (:id job1)))
      (is (some? (:id job2)))
      (is (not= (:id job1) (:id job2)))

      ;; Both jobs should exist
      (let [jobs (jdbc/query db ["SELECT * FROM jobs WHERE dedup_key = ? ORDER BY id" dedup-key])]
        (is (= 2 (count jobs)))
        (is (= "completed" (:status (first jobs))))
        (is (= "pending" (:status (second jobs))))))))

(deftest deduplication-null-key-test
  (testing "Jobs without dedup-key are not deduplicated"
    (let [db (test-db)

          job1 (jobs/enqueue-job! db "email"
                                  {:to "test1@example.com"
                                   :subject "Test 1"
                                   :body "Email body 1"})

          job2 (jobs/enqueue-job! db "email"
                                  {:to "test2@example.com"
                                   :subject "Test 2"
                                   :body "Email body 2"})]

      (is (some? (:id job1)))
      (is (some? (:id job2)))
      (is (not= (:id job1) (:id job2))))))

(deftest deduplication-processing-status-test
  (testing "Processing jobs prevent duplicates"
    (let [db (test-db)
          dedup-key "test-dedup-processing"

          ;; Create job
          job1 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Test email"
                                   :body "Email body"}
                                  {:dedup-key dedup-key})

          ;; Mark as processing
          _ (jdbc/update! db :jobs
                          {:status "processing"
                           :started_at (java.sql.Timestamp. (System/currentTimeMillis))}
                          ["id = ?" (:id job1)])

          ;; Try to enqueue duplicate
          job2 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Test email"
                                   :body "Email body"}
                                  {:dedup-key dedup-key})]

      (is (some? (:id job1)))
      (is (nil? job2)) ; Should be deduped

      ;; Only one job should exist
      (let [jobs (jdbc/query db ["SELECT * FROM jobs WHERE dedup_key = ?" dedup-key])]
        (is (= 1 (count jobs)))))))

(deftest deduplication-failed-status-test
  (testing "Failed jobs don't prevent new jobs"
    (let [db (test-db)
          dedup-key "test-dedup-failed"

          ;; Create first job
          job1 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Test email"
                                   :body "Email body"}
                                  {:dedup-key dedup-key
                                   :max-attempts 1}) ; Set to 1 so it goes straight to dead

          ;; Fetch and fail the job (simulating it going to dead letter after max attempts)
          _ (let [fetched-jobs (jobs/fetch-next-jobs db {:limit 1})
                  job (first fetched-jobs)]
              ;; Fail with max attempts to move to dead letter
              (jobs/fail-job! db (:id job) "Test failure"
                              {:current-attempt 1 ; First attempt = max attempts
                               :max-attempts 1}))

          ;; Should be able to enqueue new job since the first one is dead
          job2 (jobs/enqueue-job! db "email"
                                  {:to "test@example.com"
                                   :subject "Test email"
                                   :body "Email body"}
                                  {:dedup-key dedup-key})]

      (is (some? (:id job1)))
      (is (some? (:id job2)))
      (is (not= (:id job1) (:id job2)))

      ;; Both jobs should exist
      (let [jobs (jdbc/query db ["SELECT * FROM jobs WHERE dedup_key = ? ORDER BY id" dedup-key])]
        (is (= 2 (count jobs)))
        (is (= "dead" (:status (first jobs)))) ; Changed from "failed" to "dead"
        (is (= "pending" (:status (second jobs))))))))

(deftest deduplication-concurrent-test
  (testing "Deduplication works with concurrent inserts"
    (let [db (test-db)
          dedup-key "test-concurrent-dedup"
          results (atom [])

          ;; Try to insert 5 jobs concurrently
          futures (doall
                   (for [i (range 5)]
                     (future
                       (try
                         (let [job (jobs/enqueue-job! db "email"
                                                      {:to (str "test" i "@example.com")
                                                       :subject (str "Test " i)
                                                       :body (str "Email body " i)}
                                                      {:dedup-key dedup-key})]
                           (swap! results conj job))
                         (catch Exception e
                           (swap! results conj {:error (.getMessage e)}))))))]

      ;; Wait for all futures
      (doseq [f futures] @f)

      ;; Only one should have succeeded (returned a non-nil result with an ID)
      (let [successful (filter #(and % (:id %)) @results)]
        (is (= 1 (count successful))))

      ;; Database should have exactly one job
      (let [jobs (jdbc/query db ["SELECT * FROM jobs WHERE dedup_key = ?" dedup-key])]
        (is (= 1 (count jobs)))))))
