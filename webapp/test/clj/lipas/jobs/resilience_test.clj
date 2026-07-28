(ns lipas.jobs.resilience-test
  "Resilience tests: full retry ladders, queue consistency after partial
  failures and dead letter queue management."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [lipas.jobs.core :as jobs]
    [lipas.jobs.db :as jobs-db]
    [lipas.test-utils :as test-utils]
    [next.jdbc :as jdbc]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(deftest full-retry-ladder-test
  (testing "A job retries up to max attempts and then dead-letters"
    (let [db (test-db)
          {job-id :id} (jobs/enqueue-job! db "email"
                                          {:to "retry@example.com" :subject "R" :body "B"}
                                          {:max-attempts 3})]

      ;; Attempts 1 and 2: fail and retry
      (doseq [[attempt msg] [[1 "First failure"] [2 "Second failure"]]]
        (jdbc/execute! db ["UPDATE jobs SET run_at = now() WHERE id = ?" job-id])
        (let [[job] (jobs/fetch-next-jobs db {:limit 1})]
          (is (= attempt (:attempts job)))
          (jobs/fail-job! db job-id msg
                          {:current-attempt (:attempts job)
                           :max-attempts (:max_attempts job)}))
        (let [after (test-utils/get-job-by-id db job-id)]
          (is (= "pending" (:jobs/status after)))
          (is (= attempt (:jobs/attempts after)))
          (is (= msg (:jobs/error_message after)))
          (is (.after (:jobs/run_at after)
                      (java.sql.Timestamp/from (java.time.Instant/now)))
              "Backoff pushes run_at into the future")))

      ;; Attempt 3: dead letter
      (jdbc/execute! db ["UPDATE jobs SET run_at = now() WHERE id = ?" job-id])
      (let [[job] (jobs/fetch-next-jobs db {:limit 1})]
        (jobs/fail-job! db job-id "Final failure"
                        {:current-attempt (:attempts job)
                         :max-attempts (:max_attempts job)}))

      (is (nil? (test-utils/get-job-by-id db job-id))
          "Dead job is removed from the jobs table")
      (let [[dlj] (jobs/get-dead-letter-jobs db {:acknowledged false})]
        (is (= "Final failure" (:error-message dlj)))
        (is (= 3 (get-in dlj [:original-job :attempts])))))))

(deftest queue-consistency-after-partial-failures-test
  (testing "Queue stays consistent when some jobs complete and others die"
    (let [db (test-db)
          _ (dotimes [i 5]
              (jobs/enqueue-job! db "email"
                                 {:to (str "t" i "@example.com") :subject "T" :body "B"}))
          fetched (jobs/fetch-next-jobs db {:limit 5})]
      (is (= 5 (count fetched)))

      ;; Complete two
      (jobs/mark-completed! db (:id (nth fetched 0)))
      (jobs/mark-completed! db (:id (nth fetched 1)))

      ;; Dead-letter three
      (doseq [job (drop 2 fetched)]
        (jobs/fail-job! db (:id job) "Simulated failure"
                        {:current-attempt (:max_attempts job)
                         :max-attempts (:max_attempts job)}))

      (let [all-jobs (test-utils/get-all-jobs db)]
        (is (= 2 (count all-jobs)) "Only completed jobs remain in the table")
        (is (every? #(= "completed" (:jobs/status %)) all-jobs)))
      (is (= 3 (count (jobs/get-dead-letter-jobs db {:acknowledged false}))))

      ;; Dead letters don't block new work
      (let [{new-id :id} (jobs/enqueue-job! db "email"
                                            {:to "new@example.com" :subject "N" :body "B"})
            [next-job] (jobs/fetch-next-jobs db {:limit 5})]
        (is (= new-id (:id next-job)))))))

(deftest malformed-input-test
  (testing "Invalid job type is rejected"
    (is (thrown-with-msg? Exception #"Unknown job type"
                          (jobs/enqueue-job! (test-db) "invalid-type" {:data "x"}))))

  (testing "Invalid payload is rejected before touching the queue"
    (is (thrown-with-msg? Exception #"Invalid job payload"
                          (jobs/enqueue-job! (test-db) "email" {:no-to-field true})))
    (is (empty? (test-utils/get-all-jobs (test-db))))))

;; =============================================================================
;; Dead letter queue management
;; =============================================================================

(deftest get-dead-letter-jobs-test
  (testing "Get unacknowledged dead letter jobs"
    (let [db (test-db)]
      (test-utils/create-test-dead-letter-job! db)
      (test-utils/create-test-dead-letter-job! db {:job-type "analysis"})

      (let [jobs (jobs/get-dead-letter-jobs db {:acknowledged false})]
        (is (= 2 (count jobs)))
        (is (every? #(false? (:acknowledged %)) jobs))
        (is (every? #(map? (:original-job %)) jobs))))))

(deftest dead-letter-job-filter-test
  (testing "Filter by acknowledgment status"
    (let [db (test-db)]
      (test-utils/create-test-dead-letter-job! db)
      (test-utils/create-test-dead-letter-job! db {:acknowledged-by "admin@test.com"})

      (is (= 1 (count (jobs/get-dead-letter-jobs db {:acknowledged false}))))
      (is (= 1 (count (jobs/get-dead-letter-jobs db {:acknowledged true}))))
      (is (= 2 (count (jobs/get-dead-letter-jobs db {})))))))

(deftest reprocess-dead-letter-job-test
  (testing "Successfully reprocess a dead letter job"
    (let [db (test-db)
          dlj (test-utils/create-test-dead-letter-job! db)
          new-job (jobs/reprocess-dead-letter-job! db (:id dlj) "admin@test.com")]

      (is (some? new-job))
      (is (= "email" (:type new-job)))
      (is (map? (:payload new-job)))
      (is (= "pending" (:status new-job)))
      (is (= 3 (:max-attempts new-job)))
      (is (zero? (:attempts new-job)) "Requeued job starts with fresh attempts")

      (let [acknowledged (jobs-db/get-dead-letter-by-id db {:id (:id dlj)})]
        (is (true? (:acknowledged acknowledged)))
        (is (= "admin@test.com" (:acknowledged_by acknowledged))))))

  (testing "Error when dead letter job not found"
    (is (thrown-with-msg? Exception #"Dead letter job not found"
                          (jobs/reprocess-dead-letter-job! (test-db) 999999 "admin@test.com"))))

  (testing "Error when the original job type is no longer registered"
    (let [db (test-db)
          dlj (test-utils/create-test-dead-letter-job! db {:job-type "produce-reminders"
                                                           :payload {}})]
      (is (thrown-with-msg? Exception #"unknown job type"
                            (jobs/reprocess-dead-letter-job! db (:id dlj) "admin@test.com"))))))

(deftest reprocess-dead-letter-jobs-bulk-test
  (testing "Bulk reprocess multiple jobs"
    (let [db (test-db)
          dlj1 (test-utils/create-test-dead-letter-job! db)
          dlj2 (test-utils/create-test-dead-letter-job! db)
          result (jobs/reprocess-dead-letter-jobs!
                   db
                   [(:id dlj1) (:id dlj2) 999999] ; 999999 doesn't exist
                   "admin@test.com")]

      (is (= 2 (count (:succeeded result))))
      (is (= 1 (count (:failed result))))
      (is (every? #(pos? (:new-job-id %)) (:succeeded result)))
      (is (= 999999 (-> result :failed first :dead-letter-id)))
      (is (string? (-> result :failed first :error)))))

  (testing "Empty list handling"
    (let [result (jobs/reprocess-dead-letter-jobs! (test-db) [] "admin@test.com")]
      (is (= 0 (count (:succeeded result))))
      (is (= 0 (count (:failed result)))))))
