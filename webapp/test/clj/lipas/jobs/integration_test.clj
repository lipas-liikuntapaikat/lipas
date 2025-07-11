(ns lipas.jobs.integration-test
  "End-to-end integration tests for the unified job queue system.

  Focuses on high-value testing with Malli validation instead of
  manual assertions for better maintainability."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.backend.email :as email]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.db :as jobs-db]
   [lipas.jobs.scheduler :as scheduler]
   [lipas.jobs.worker :as worker]
   [lipas.test-utils :as test-utils]
   [malli.core :as m]
   [next.jdbc :as jdbc]
   [taoensso.timbre :as log]))

;; Test system setup
(defonce test-system (atom nil))

(defn setup-test-system! []
  ;; Ensure database is properly initialized with current migrations
  (test-utils/ensure-test-database!)

  ;; Initialize the test system
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
    ;; Clean all tables before each test (including jobs table)
    (test-utils/prune-db! (:lipas/db @test-system))
    (f)))

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

;; Test emailer that records sent emails
(defrecord TestEmailer [sent-emails]
  email/Emailer
  (send! [_ msg]
    (swap! sent-emails conj msg)
    {:status :sent :message-id (str "test-" (System/currentTimeMillis))}))

(defn create-test-emailer []
  (->TestEmailer (atom [])))

;; Core test utilities

(defn get-all-jobs [db]
  (jdbc/execute! db ["SELECT * FROM jobs ORDER BY created_at"]))

(defn wait-for-condition
  "Wait up to timeout-ms for condition-fn to return truthy value"
  [condition-fn timeout-ms]
  (let [start-time (System/currentTimeMillis)
        end-time (+ start-time timeout-ms)]
    (loop []
      (if (condition-fn)
        true
        (if (< (System/currentTimeMillis) end-time)
          (do (Thread/sleep 100)
              (recur))
          false)))))

;; HIGH-VALUE END-TO-END TESTS

(deftest ^:integration complete-job-lifecycle-test
  (testing "Complete job lifecycle: enqueue → fetch → process → complete"
    (let [db (:lipas/db @test-system)
          test-emailer (create-test-emailer)
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
        (is (wait-for-condition
             (fn []
               (let [jobs (get-all-jobs db)
                     completed-count (count (filter #(= "completed" (:jobs/status %)) jobs))]
                 (>= completed-count 2)))
             10000) ; 10 second timeout
            "Jobs should be processed within timeout")

        ;; 4. Validate final state with Malli
        (let [final-jobs (get-all-jobs db)]
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
      (is (wait-for-condition
           (fn [] (>= (count (get-all-jobs db)) 2))
           5000))

      ;; Validate scheduled jobs
      (let [scheduled-jobs (get-all-jobs db)]
        (is (m/validate [:sequential job-schema] scheduled-jobs))

        ;; Should have both reminder and cleanup jobs
        (let [job-types (set (map :jobs/type scheduled-jobs))]
          (is (contains? job-types "produce-reminders"))
          (is (contains? job-types "cleanup-jobs"))))

      ;; Stop scheduler
      (scheduler/stop-scheduler!))))

(deftest ^:integration job-retry-and-dead-letter-test
  (testing "Jobs retry with exponential backoff and move to dead letter queue"
    (let [db (:lipas/db @test-system)]

      ;; Create a job that will fail
      (let [job-result (jobs/enqueue-job! db "email"
                                          {:to "fail@test.com" :subject "Test" :body "This will fail"}
                                          {:max-attempts 3})
            job-id (:id job-result)]

        ;; Process with mock handler that fails
        (dotimes [attempt 3]
          ;; Simulate job failure
          (jobs/fail-job! db job-id "Simulated failure"
                          {:current-attempt (inc attempt)
                           :max-attempts 3})

          ;; Check job state after each failure
          (let [job (first (jdbc/execute! db ["SELECT * FROM jobs WHERE id = ?" job-id]))]
            (if (< (inc attempt) 3)
              (do
                (is (= "pending" (:jobs/status job)))
                (is (= "Simulated failure" (:jobs/last_error job)))
                (is (inst? (:jobs/run_at job))))
              (is (= "dead" (:jobs/status job))))))

        ;; Verify job moved to dead letter queue
        (let [dead-letters (jdbc/execute! db ["SELECT * FROM dead_letter_jobs"])]
          (is (= 1 (count dead-letters)))
          (is (= "Simulated failure" (:dead_letter_jobs/error_message (first dead-letters)))))))))

(deftest ^:integration correlation-id-tracking-test
  (testing "Correlation IDs track related jobs across the system"
    (let [db (:lipas/db @test-system)
          correlation-id (java.util.UUID/randomUUID)]

      ;; Create parent job
      (let [parent-result (jobs/enqueue-job!
                           db "analysis"
                           {:lipas-id 12345}
                           {:correlation-id correlation-id})
            parent-id (:id parent-result)]

        ;; Create child jobs with same correlation
        (jobs/enqueue-job!
         db "email"
         {:to "notify@test.com" :subject "Analysis started" :body "Analysis has started"}
         {:correlation-id correlation-id
          :parent-job-id parent-id})

        (jobs/enqueue-job!
         db "webhook"
         {:lipas-ids [12345]}
         {:correlation-id correlation-id
          :parent-job-id parent-id})

        ;; Find all jobs by correlation ID
        (let [correlated-jobs (jobs-db/get-job-by-correlation
                               db {:correlation_id correlation-id})]
          (is (= 3 (count correlated-jobs)))
          (is (every? #(= correlation-id (:correlation_id %)) correlated-jobs))
          (is (= #{"analysis" "email" "webhook"}
                 (set (map :type correlated-jobs))))))))) ; Email has high priority

(comment
  (clojure.test/run-tests *ns*)
  (clojure.test/run-test-var #'job-retry-and-dead-letter-test)
  (clojure.test/run-test-var #'correlation-id-tracking-test))
