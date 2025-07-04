(ns lipas.jobs.dead-letter-core-test
  "Core tests for dead letter queue functionality."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.db :as jobs-db]
   [lipas.test-utils :as test-utils]
   [next.jdbc :as jdbc]
   [cheshire.core :as json]))

;; Test system setup
(defonce test-system (atom nil))

(defn setup-test-system! []
  (test-utils/ensure-test-database!)
  (reset! test-system
          (ig/init (select-keys (config/->system-config test-utils/config)
                                [:lipas/db]))))

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

;; Test helpers
(defn create-dead-letter-job!
  [db & [{:keys [job-type error-message acknowledged-by]
          :or {job-type "email"
               error-message "SMTP connection failed"}}]]
  (let [correlation-id (java.util.UUID/randomUUID)
        original-job {:id 999
                      :type job-type
                      :payload {:to "test@example.com" :template "welcome"}
                      :status "failed"
                      :priority 100
                      :attempts 3
                      :max_attempts 3
                      :scheduled_at (java.sql.Timestamp/from (java.time.Instant/now))
                      :run_at (java.sql.Timestamp/from (java.time.Instant/now))
                      :created_at (java.sql.Timestamp/from (java.time.Instant/now))
                      :updated_at (java.sql.Timestamp/from (java.time.Instant/now))
                      :correlation_id correlation-id}
        dlj (jobs-db/insert-dead-letter!
             db
             {:original_job (json/generate-string original-job)
              :error_message error-message
              :error_details nil
              :correlation_id correlation-id})]
    (if acknowledged-by
      (do
        (jobs-db/acknowledge-dead-letter!
         db
         {:id (:id dlj)
          :acknowledged_by acknowledged-by})
        ;; Return the updated job
        (jobs-db/get-dead-letter-by-id db {:id (:id dlj)}))
      dlj)))

(deftest get-dead-letter-jobs-test
  (testing "Get unacknowledged dead letter jobs"
    (let [db (:lipas/db @test-system)]
      ;; Create some dead letter jobs
      (create-dead-letter-job! db)
      (create-dead-letter-job! db {:job-type "analysis"})

      ;; Get all unacknowledged
      (let [jobs (jobs/get-dead-letter-jobs db {:acknowledged false})]
        (is (= 2 (count jobs)))
        (is (every? #(false? (:acknowledged %)) jobs))
        (is (every? #(contains? % :original-job) jobs))
        ;; Check that original job was parsed
        (is (map? (:original-job (first jobs))))))))

(deftest dead-letter-job-filter-test
  (testing "Filter by acknowledgment status"
    (let [db (:lipas/db @test-system)]
      ;; Create one unacknowledged and one acknowledged
      (let [unack-job (create-dead-letter-job! db)
            ack-job (create-dead-letter-job! db {:acknowledged-by "admin@test.com"})]

        ;; Check filters work
        (let [unack-jobs (jobs/get-dead-letter-jobs db {:acknowledged false})
              ack-jobs (jobs/get-dead-letter-jobs db {:acknowledged true})
              all-jobs (jobs/get-dead-letter-jobs db {})]

          (is (= 1 (count unack-jobs)))
          (is (= 1 (count ack-jobs)))
          (is (= 2 (count all-jobs))))))))

(deftest reprocess-dead-letter-job-test
  (testing "Successfully reprocess a dead letter job"
    (let [db (:lipas/db @test-system)
          dlj (create-dead-letter-job! db)
          new-job (jobs/reprocess-dead-letter-job! db (:id dlj) "admin@test.com")]

      ;; Check new job was created
      (is (some? new-job))
      (is (= "email" (:type new-job)))
      (is (map? (:payload new-job)))
      (is (= "pending" (:status new-job)))
      (is (= 3 (:max-attempts new-job)))

      ;; Check original was acknowledged
      (let [acknowledged (jobs-db/get-dead-letter-by-id db {:id (:id dlj)})]
        (is (true? (:acknowledged acknowledged)))
        (is (= "admin@test.com" (:acknowledged_by acknowledged))))))

  (testing "Error when dead letter job not found"
    (let [db (:lipas/db @test-system)]
      (is (thrown-with-msg? Exception #"Dead letter job not found"
                            (jobs/reprocess-dead-letter-job! db 999999 "admin@test.com"))))))

(deftest reprocess-dead-letter-jobs-bulk-test
  (testing "Bulk reprocess multiple jobs"
    (let [db (:lipas/db @test-system)
          dlj1 (create-dead-letter-job! db)
          dlj2 (create-dead-letter-job! db)

          result (jobs/reprocess-dead-letter-jobs!
                  db
                  [(:id dlj1) (:id dlj2) 999999] ; 999999 doesn't exist
                  "admin@test.com")]

      ;; Check results
      (is (= 2 (count (:succeeded result))))
      (is (= 1 (count (:failed result))))

      ;; Check succeeded jobs have new IDs
      (is (every? #(contains? % :new-job-id) (:succeeded result)))
      (is (every? #(pos? (:new-job-id %)) (:succeeded result)))

      ;; Check failed job has error
      (is (= 999999 (-> result :failed first :dead-letter-id)))
      (is (string? (-> result :failed first :error)))))

  (testing "Empty list handling"
    (let [db (:lipas/db @test-system)
          result (jobs/reprocess-dead-letter-jobs! db [] "admin@test.com")]
      (is (= 0 (count (:succeeded result))))
      (is (= 0 (count (:failed result)))))))