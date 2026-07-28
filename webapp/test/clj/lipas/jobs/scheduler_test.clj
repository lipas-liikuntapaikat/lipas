(ns lipas.jobs.scheduler-test
  "Tests for the scheduler's direct maintenance tasks: reminder email
  production, retention cleanup and scheduler lifecycle."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [lipas.backend.core :as core]
    [lipas.backend.db.db :as db]
    [lipas.jobs.scheduler :as scheduler]
    [lipas.test-utils :as test-utils]
    [next.jdbc :as jdbc]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(defn create-test-user! [db]
  (jdbc/execute! db
                 ["INSERT INTO account (email, username, password, status, user_data, permissions)
                   VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)"
                  "reminder-test@example.com" "reminderuser" "test123" "active"
                  "{\"firstname\": \"Reminder\", \"lastname\": \"User\"}" "{}"])
  (first (db/get-users db)))

(deftest produce-reminder-emails-test
  (testing "Overdue reminders become email jobs and are marked processed"
    (let [db (test-db)
          user (create-test-user! db)]

      (core/add-reminder! db user
                          {:body {:message "First reminder" :title "First"}
                           :event-date (java.sql.Timestamp/valueOf "2024-01-01 10:00:00")})
      (core/add-reminder! db user
                          {:body {:message "Second reminder" :title "Second"}
                           :event-date (java.sql.Timestamp/valueOf "2024-01-02 10:00:00")})

      (is (= 2 (count (db/get-overdue-reminders db))))

      (let [produced (scheduler/produce-reminder-emails! db)]
        (is (= 2 produced)))

      ;; Email jobs enqueued with reminder payloads
      (let [email-jobs (filter #(= "email" (:jobs/type %)) (test-utils/get-all-jobs db))]
        (is (= 2 (count email-jobs)))
        (doseq [job email-jobs]
          (let [payload (:jobs/payload job)]
            (is (= "reminder" (:type payload)))
            (is (= (:email user) (:email payload)))
            (is (some? (:body payload))))))

      ;; Reminders marked processed
      (is (empty? (db/get-overdue-reminders db)))

      ;; Second run is a no-op
      (is (= 0 (scheduler/produce-reminder-emails! db)))
      (is (= 2 (count (test-utils/get-all-jobs db))))))

  (testing "No overdue reminders produces no jobs"
    (test-utils/prune-db! (test-db))
    (is (= 0 (scheduler/produce-reminder-emails! (test-db))))
    (is (empty? (test-utils/get-all-jobs (test-db))))))

(deftest scheduler-lifecycle-test
  (testing "Scheduler starts, runs its tasks and stops"
    (let [db (test-db)]
      (try
        (let [result (scheduler/start-scheduler! db)]
          (is (= :running (:status result)))
          (is (= (count scheduler/schedule-configs) (:scheduled-count result))))

        (let [stats (scheduler/scheduler-stats)]
          (is (:running? stats))
          (is (= (count scheduler/schedule-configs) (:scheduled-tasks-count stats))))

        (testing "Double start is a no-op"
          (is (nil? (scheduler/start-scheduler! db)))
          (is (= (count scheduler/schedule-configs)
                 (:scheduled-tasks-count (scheduler/scheduler-stats)))
              "Second start must not schedule duplicate tasks"))

        (testing "Scheduler tasks do not enqueue maintenance jobs"
          ;; The old design routed reminder checks and cleanup through the
          ;; queue as job rows; the new design calls them directly. Real
          ;; work still becomes jobs: the nightly KB sync enqueues exactly
          ;; one (deduplicated) help-kb-sync job on its startup tick.
          (Thread/sleep 500)
          (let [jobs (test-utils/get-all-jobs db)]
            (is (= ["help-kb-sync"] (mapv :jobs/type jobs))
                "No queue churn from scheduler ticks beyond the KB sync")))

        (finally
          (scheduler/stop-scheduler!)))

      (is (false? (:running? (scheduler/scheduler-stats)))))))
