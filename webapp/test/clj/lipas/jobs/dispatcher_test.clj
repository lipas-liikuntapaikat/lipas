(ns lipas.jobs.dispatcher-test
  "Unit tests for job dispatcher handlers."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures] :as t]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.dispatcher :as dispatcher]
   [lipas.test-utils :as test-utils]
   [next.jdbc :as jdbc]
   [taoensso.timbre :as log]))

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

;; Test utilities
(defn test-db [] (:lipas/db @test-system))

;; Mock emailer that records sent emails
(defrecord TestEmailer [sent-emails]
  email/Emailer
  (send! [_ msg]
    (swap! sent-emails conj msg)
    {:status :sent :message-id (str "test-" (System/currentTimeMillis))}))

(defn create-test-emailer []
  (->TestEmailer (atom [])))

;; Mock search client
(defn create-mock-search []
  {:client {:host "mock"} :indices {}})

;; =============================================================================
;; Job Dispatcher Handler Tests
;; =============================================================================

(deftest email-job-handler-test
  (testing "Email job handler processes different email types"
    (let [test-emailer (create-test-emailer)
          system {:db (test-db) :emailer test-emailer :search (create-mock-search)}]

      ;; Test basic email
      (testing "Basic email sending"
        (let [job {:id 1 :type "email"
                   :payload {:to "user@example.com"
                             :subject "Test Subject"
                             :body "Hello World"}}]
          (dispatcher/dispatch-job system job)
          (is (= 1 (count @(:sent-emails test-emailer))))
          (let [sent-email (first @(:sent-emails test-emailer))]
            (is (= "user@example.com" (:to sent-email)))
            (is (= "Test Subject" (:subject sent-email))))))

      ;; Test reminder email
      ;; Test reminder email - mock the reminder email function to avoid template issues
      (testing "Reminder email type"
        (with-redefs [lipas.backend.email/send-reminder-email!
                      (fn [emailer email link body]
                        (email/send! emailer {:to email :subject "Reminder" :body body}))]
          (reset! (:sent-emails test-emailer) []) ; Clear previous emails
          (let [job {:id 2 :type "email"
                     :payload {:type "reminder"
                               :email "user@example.com"
                               :link "http://example.com/reminder"
                               :body "Don't forget!"}}]
            (dispatcher/dispatch-job system job)
            (is (= 1 (count @(:sent-emails test-emailer))))))))))

(deftest analysis-job-handler-test
  (testing "Analysis job handler processes sports sites"
    ;; Mock the entire analysis chain to avoid database and geometry dependencies
    (with-redefs [lipas.backend.core/get-sports-site
                  (fn [db lipas-id]
                    {:lipas-id lipas-id
                     :location {:geometries {:type "FeatureCollection"
                                             :features []}}})
                  lipas.backend.gis/simplify
                  (fn [geom] geom)
                  lipas.backend.analysis.diversity/recalc-grid!
                  (fn [search geom]
                    (log/info "Mock diversity analysis called")
                    {:status :calculated})]
      (let [system {:db (test-db) :search (create-mock-search)}
            _ (test-utils/prune-db! (test-db))
            sports-site-id 12345
            job {:id 1 :type "analysis" :payload {:lipas-id sports-site-id}}]

        ;; Should return success status with proper mocking
        (let [result (dispatcher/dispatch-job system job)]
          (is (= :success (:status result))))))))

;; Integration between lipas and legacy-lipas db can be removed
;; soon. Let's not waste time making it work with the new Jobs system.

#_(deftest integration-job-handler-test
    (testing "Integration job handler processes sports site sync"
      (let [system {:db (test-db) :search (create-mock-search)}
            job {:id 1 :type "integration" :payload {:lipas-id 12345}}]

      ;; Should not throw - handler should complete
      ;; Should return success status
        (let [result (dispatcher/dispatch-job system job)]
          (is (= :success (:status result)))))))

(deftest webhook-job-handler-test
  (testing "Webhook job handler processes lipas and loi IDs"
    (let [system {:db (test-db) :search (create-mock-search)}
          job {:id 1 :type "webhook"
               :payload {:lipas-ids [1 2]
                         :operation-type "test-update"}}]

      ;; Mock the webhook process function to avoid external dependencies
      (with-redefs [lipas.integration.utp.webhook/process-v2!
                    (fn [db config payload]
                      (log/info "Mock webhook process called" payload)
                      {:status :success})]
        (let [result (dispatcher/dispatch-job system job)]
          (is (= :success (:status result))))))))

(deftest cleanup-jobs-handler-test
  (testing "Cleanup jobs handler removes old completed jobs"
    (let [db (test-db)
          system {:db db :search (create-mock-search)}]

      ;; Add some old completed jobs to clean up
      (let [old-result (jobs/enqueue-job! db "email" {:to "old@example.com" :subject "Old Email" :body "Old test email"})
            old-job-id (:id old-result)]
        ;; Mark as completed and backdate it
        (jobs/mark-completed! db old-job-id)
        (jdbc/execute! db
                       ["UPDATE jobs SET completed_at = completed_at - interval '10 days' WHERE id = ?"
                        old-job-id])

        ;; Add a recent job that shouldn't be cleaned
        (let [recent-result (jobs/enqueue-job! db "email" {:to "recent@example.com" :subject "Recent Email" :body "Recent test email"})
              recent-job-id (:id recent-result)]
          (jobs/mark-completed! db recent-job-id)

          ;; Run cleanup job
          (let [cleanup-job {:id 1 :type "cleanup-jobs" :payload {:days-old 7}}]
            (dispatcher/dispatch-job system cleanup-job)

            ;; Old job should be gone, recent job should remain
            (let [remaining-jobs (jdbc/execute! db ["SELECT * FROM jobs WHERE status = 'completed'"])]
              (is (= 1 (count remaining-jobs))
                  "Only recent completed job should remain"))))))))

(deftest produce-reminders-handler-test
  (testing "Produce reminders handler creates email jobs from overdue reminders"
    (let [system {:db (test-db)}
          ;; Create a test user
          test-user-data {:email "reminder-test@example.com"
                          :username "reminderuser"
                          :password "test123"
                          :status "active"
                          :user_data "{\"firstname\": \"Reminder\", \"lastname\": \"User\"}"
                          :permissions "{}"}
          _ (jdbc/execute! (test-db)
                           ["INSERT INTO account (email, username, password, status, user_data, permissions) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)"
                            (:email test-user-data) (:username test-user-data) (:password test-user-data)
                            (:status test-user-data) (:user_data test-user-data) (:permissions test-user-data)])
          test-user (first (db/get-users (test-db)))]

      ;; Clear any existing reminders and jobs for clean test
      (jdbc/execute! (test-db) ["DELETE FROM reminder WHERE account_id = ?" (:id test-user)])
      (jdbc/execute! (test-db) ["DELETE FROM jobs WHERE type IN ('email', 'produce-reminders')"])

      ;; Create test reminders that are overdue
      (let [reminder1 {:body {:message "This is your first reminder" :title "First Reminder"}
                       :event-date (java.sql.Timestamp/valueOf "2024-01-01 10:00:00")}
            reminder2 {:body {:message "This is your second reminder" :title "Second Reminder"}
                       :event-date (java.sql.Timestamp/valueOf "2024-01-02 10:00:00")}]

        ;; Add reminders using core function
        (core/add-reminder! (test-db) test-user reminder1)
        (core/add-reminder! (test-db) test-user reminder2)

        ;; Verify reminders were added and are overdue
        (let [overdue-before (db/get-overdue-reminders (test-db))]
          (is (= 2 (count overdue-before)) "Should have 2 overdue reminders"))

        ;; Get initial queue stats
        (let [queue-stats-before (jobs/get-queue-stats (test-db))
              pending-before (get-in queue-stats-before [:pending :count] 0)]

          ;; Run the produce-reminders handler
          (let [job {:id 1 :type "produce-reminders" :payload {} :correlation-id "test-123"}]
            (dispatcher/dispatch-job system job))

          ;; Verify email jobs were created
          (let [email-jobs (jdbc/execute! (test-db) ["SELECT * FROM jobs WHERE type = 'email'"])]
            (is (= 2 (count email-jobs)) "Should have created 2 email jobs")

            ;; Verify email job payloads contain reminder data
            (doseq [job email-jobs]
              (let [payload (:jobs/payload job)]
                (is (= "reminder" (:type payload)) "Email should be of type reminder")
                (is (= (:email test-user) (:email payload)) "Email should be sent to test user")
                (is (some? (:reminder-id payload)) "Email should have reminder ID")
                (is (some? (:body payload)) "Email should have body content"))))

          ;; Verify reminders were marked as processed (no longer overdue)
          (let [overdue-after (db/get-overdue-reminders (test-db))]
            (is (= 0 (count overdue-after)) "Should have no overdue reminders after processing")))))))

(deftest job-handler-error-handling-test
  (testing "Job handlers handle errors gracefully"
    (let [system {:db (test-db) :emailer nil :search nil}] ; Broken system

      ;; Email job should throw exception (dispatcher no longer catches)
      (testing "Email job with broken emailer"
        (let [job {:id 1 :type "email"
                   :payload {:to "test@example.com" :subject "Test"}}]
          (is (thrown? Exception
                       (dispatcher/dispatch-job system job)))))

      ;; Analysis job should throw exception
      (testing "Analysis job with broken search"
        (let [job {:id 2 :type "analysis" :payload {:lipas-id 12345}}]
          (is (thrown? Exception
                       (dispatcher/dispatch-job system job))))))))

(deftest unknown-job-type-handler-test
  (testing "Unknown job types are handled by default handler"
    (let [system {:db (test-db) :search (create-mock-search)}
          job {:id 1 :type "unknown-job-type" :payload {:data "test"}}]

      ;; Should throw for unknown job type
      (is (thrown-with-msg? Exception
                            #"Unknown job type"
                            (dispatcher/dispatch-job system job))))))

(deftest malformed-payload-handling-test
  (testing "Handlers validate job payloads appropriately"
    (let [system {:db (test-db) :emailer (create-test-emailer) :search (create-mock-search)}]

      ;; Email job with missing required fields
      (testing "Email job with missing 'to' field"
        (let [job {:id 1 :type "email" :payload {:subject "Test"}} ; Missing :to
              result (dispatcher/dispatch-job system job)]
          ;; Should complete successfully (email handler is resilient)
          (is (= :success (:status result)))))

      ;; Analysis job with missing lipas-id
      (testing "Analysis job with missing lipas-id"
        (let [job {:id 2 :type "analysis" :payload {}}] ; Missing :lipas-id
          ;; Should throw when trying to process
          (is (thrown? Exception
                       (dispatcher/dispatch-job system job))))))))

(comment
  (clojure.test/run-tests *ns*))
