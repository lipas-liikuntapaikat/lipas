(ns lipas.jobs.integration-test
  "End-to-end integration tests for the unified job queue system.

  Focuses on high-value testing with Malli validation instead of
  manual assertions for better maintainability."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.scheduler :as scheduler]
   [lipas.jobs.worker :as worker]
   [lipas.test-utils :as test-utils]
   [malli.core :as m]))

;; Test system setup
(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;; Malli schemas for test validation

(def job-schema
  [:map
   [:jobs/id pos-int?]
   [:jobs/type jobs/job-type-schema]
   [:jobs/status jobs/job-status-schema]
   [:jobs/payload map?]
   [:jobs/priority [:>= 0]]
   [:jobs/attempts [:>= 0]]
   [:jobs/max_attempts pos-int?]
   [:jobs/created_at inst?]])

(def worker-stats-schema
  [:map
   [:running? boolean?]
   [:active-futures nat-int?]
   [:fast-pool-size {:optional true} nat-int?]
   [:fast-active {:optional true} nat-int?]
   [:general-pool-size {:optional true} nat-int?]
   [:general-active {:optional true} nat-int?]])

;; HIGH-VALUE END-TO-END TESTS

(deftest ^:integration complete-job-lifecycle-test
  (testing "Complete job lifecycle: enqueue → fetch → process → complete"
    (let [db (:lipas/db @test-system)
          test-emailer (test-utils/create-test-emailer)
          worker-system {:db db :emailer test-emailer :search nil}]

      ;; 1. Enqueue jobs of different types and priorities
      (let [email-job-result (jobs/enqueue-job! db "email"
                                                {:to "test@example.com" :subject "Test" :body "Test message"}
                                                {:priority 95})
            email-job-id (:id email-job-result)
            cleanup-job-result (jobs/enqueue-job! db "cleanup-jobs"
                                                  {:days-old 30}
                                                  {:priority 50})
            cleanup-job-id (:id cleanup-job-result)]

        ;; Validate job creation with Malli
        (is (m/validate pos-int? email-job-id))
        (is (m/validate pos-int? cleanup-job-id))

        ;; 2. Start worker and let it process jobs
        (worker/start-mixed-duration-worker!
         worker-system
         {:fast-threads 1 :general-threads 1 :batch-size 5 :poll-interval-ms 500})

        ;; 3. Wait for jobs to be processed
        (is (test-utils/wait-for-condition
             (fn []
               (let [jobs (test-utils/get-all-jobs db)
                     completed-count (count (filter #(= "completed" (:jobs/status %)) jobs))]
                 (>= completed-count 2)))
             10000) ; 10 second timeout
            "Jobs should be processed within timeout")

        ;; 4. Validate final state with Malli
        (let [final-jobs (test-utils/get-all-jobs db)]
          (is (m/validate [:sequential job-schema] final-jobs))

          ;; Check that email was "sent"
          (is (= 1 (count @(:sent-emails test-emailer))))
          (is (m/validate [:map [:to string?] [:subject string?]]
                          (first @(:sent-emails test-emailer)))))

        ;; 5. Stop worker
        (worker/stop-mixed-duration-worker!)))))

(deftest ^:integration scheduler-integration-test
  (testing "Scheduler automatically produces jobs at scheduled intervals"
    (let [db (:lipas/db @test-system)]

      ;; Start scheduler
      (scheduler/start-scheduler! db)

      ;; Wait for initial jobs to be produced
      (is (test-utils/wait-for-condition
           (fn [] (>= (count (test-utils/get-all-jobs db)) 2))
           5000))

      ;; Validate scheduled jobs
      (let [scheduled-jobs (test-utils/get-all-jobs db)]
        (is (m/validate [:sequential job-schema] scheduled-jobs))

        ;; Should have both reminder and cleanup jobs
        (let [job-types (set (map :jobs/type scheduled-jobs))]
          (is (contains? job-types "produce-reminders"))
          (is (contains? job-types "cleanup-jobs"))))

      ;; Stop scheduler
      (scheduler/stop-scheduler!))))

;; Note: The following tests have been removed as they are covered more thoroughly elsewhere:
;; - job-retry-and-dead-letter-test -> resilience_test.clj (job-retry-logic-test, dead-letter-queue-test)
;; - correlation-id-tracking-test -> correlation_test.clj (comprehensive correlation tests)

(comment
  (clojure.test/run-tests *ns*))
