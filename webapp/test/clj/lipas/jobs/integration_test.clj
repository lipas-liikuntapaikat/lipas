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
   [lipas.jobs.worker :as worker]
   [lipas.jobs.scheduler :as scheduler]
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
          (ig/init (select-keys config/system-config [:lipas/db]))))

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
      (let [email-job-id (jobs/enqueue-job! db "email"
                                            {:to "test@example.com" :subject "Test"}
                                            {:priority 95})
            cleanup-job-id (jobs/enqueue-job! db "cleanup-jobs"
                                              {:days-old 30}
                                              {:priority 50})]

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

(deftest ^:integration legacy-compatibility-test
  (testing "Legacy queue functions still work with unified system"
    (let [db (:lipas/db @test-system)
          test-site {:lipas-id 12345 :name "Test Site"}]

      ;; Test all legacy functions
      (jobs/add-to-analysis-queue! db test-site)
      (jobs/add-to-elevation-queue! db test-site)
      (jobs/add-to-integration-out-queue! db test-site)
      (jobs/add-to-email-out-queue! db {:to "legacy@test.com" :subject "Legacy"})

      ;; Validate jobs were created correctly
      (let [all-jobs (get-all-jobs db)]
        (is (m/validate [:sequential job-schema] all-jobs))
        (is (= 4 (count all-jobs)))

        ;; Validate job types and priorities match legacy expectations
        (let [job-types (map :jobs/type all-jobs)
              priorities (map :jobs/priority all-jobs)]
          (is (= #{"analysis" "elevation" "integration" "email"} (set job-types)))
          (is (every? #(>= % 70) priorities)) ; All have reasonable priorities
          (is (some #(= 95 %) priorities))))))) ; Email has high priority

(comment
  (clojure.test/run-tests *ns*))
