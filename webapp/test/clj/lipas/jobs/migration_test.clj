(ns lipas.jobs.migration-test
  "Tests for migrating orphaned dead jobs"
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.core :as jobs]
   [lipas.test-utils :as test-utils]
   [next.jdbc :as jdbc]))

(defonce test-system (atom nil))

(defn setup-test-system! []
  (test-utils/ensure-test-database!)
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

(deftest migrate-orphaned-dead-jobs-test
  (testing "Orphaned dead jobs are migrated to dead_letter_jobs"
    (let [db (test-db)]

      ;; Create some jobs and manually mark them as dead (simulating legacy behavior)
      (let [job1 (jobs/enqueue-job! db "email" {:to "test1@example.com" :subject "Test 1" :body "Body 1"})
            job2 (jobs/enqueue-job! db "webhook" {:lipas-ids [123]} {:correlation-id #uuid "11111111-1111-1111-1111-111111111111"})
            job3 (jobs/enqueue-job! db "analysis" {:lipas-id 456})]

        ;; Manually update jobs to dead status without going through proper flow
        (jdbc/execute! db ["UPDATE jobs SET status = 'dead', error_message = ? WHERE id = ?"
                           "Legacy error 1" (:id job1)])
        (jdbc/execute! db ["UPDATE jobs SET status = 'dead', last_error = ? WHERE id = ?"
                           "Legacy error 2" (:id job2)])
        (jdbc/execute! db ["UPDATE jobs SET status = 'dead' WHERE id = ?"
                           (:id job3)])

        ;; Verify they're dead but not in dead_letter_jobs
        (let [dead-jobs (jdbc/execute! db ["SELECT * FROM jobs WHERE status = 'dead'"])
              dead-letters (jdbc/execute! db ["SELECT * FROM dead_letter_jobs"])]
          (is (= 3 (count dead-jobs)))
          (is (= 0 (count dead-letters))))

        ;; Run migration
        (let [migrated-count (jobs/migrate-orphaned-dead-jobs! db)]
          (is (= 3 migrated-count)))

        ;; Verify migration results
        (let [dead-letters (jdbc/execute! db ["SELECT * FROM dead_letter_jobs"])
              dl-by-job-id (into {} (map (fn [dl]
                                           [(get-in dl [:dead_letter_jobs/original_job :id])
                                            dl])
                                         dead-letters))]
          (is (= 3 (count dead-letters)))

          ;; Check first job - has error_message
          (let [dl1 (get dl-by-job-id (:id job1))]
            (is (= "Legacy error 1" (:dead_letter_jobs/error_message dl1))))

          ;; Check second job - has last_error and correlation_id
          (let [dl2 (get dl-by-job-id (:id job2))]
            (is (= "Legacy error 2" (:dead_letter_jobs/error_message dl2)))
            (is (= "11111111-1111-1111-1111-111111111111"
                   (str (:dead_letter_jobs/correlation_id dl2)))))

          ;; Check third job - no error message
          (let [dl3 (get dl-by-job-id (:id job3))]
            (is (= "Legacy dead job - no error message recorded"
                   (:dead_letter_jobs/error_message dl3)))))

        ;; Run migration again - should be idempotent
        (let [migrated-count (jobs/migrate-orphaned-dead-jobs! db)]
          (is (= 0 migrated-count)))

        ;; Verify no duplicates
        (let [dead-letters (jdbc/execute! db ["SELECT * FROM dead_letter_jobs"])]
          (is (= 3 (count dead-letters))))))))

(deftest migrate-mixed-dead-jobs-test
  (testing "Migration handles mix of orphaned and properly handled dead jobs"
    (let [db (test-db)]

      ;; Create a properly dead-lettered job
      (let [job1 (jobs/enqueue-job! db "email" {:to "proper@example.com" :subject "Proper" :body "Body"}
                                    {:max-attempts 1})]
        ;; Fetch and fail it properly
        (let [fetched (jobs/fetch-next-jobs db {:limit 1})
              job (first fetched)]
          (jobs/fail-job! db (:id job) "Proper failure"
                          {:current-attempt 1
                           :max-attempts 1})))

      ;; Create an orphaned dead job
      (let [job2 (jobs/enqueue-job! db "webhook" {:lipas-ids [789]})]
        (jdbc/execute! db ["UPDATE jobs SET status = 'dead', error_message = ? WHERE id = ?"
                           "Orphaned error" (:id job2)]))

      ;; Check initial state
      (let [dead-jobs (jdbc/execute! db ["SELECT * FROM jobs WHERE status = 'dead'"])
            dead-letters-before (jdbc/execute! db ["SELECT * FROM dead_letter_jobs"])]
        (is (= 2 (count dead-jobs)))
        (is (= 1 (count dead-letters-before)))) ; Only the proper one

      ;; Run migration
      (let [migrated-count (jobs/migrate-orphaned-dead-jobs! db)]
        (is (= 1 migrated-count))) ; Only migrates the orphaned one

      ;; Verify final state
      (let [dead-letters-after (jdbc/execute! db ["SELECT * FROM dead_letter_jobs"])]
        (is (= 2 (count dead-letters-after))))))) ; Now both are there
