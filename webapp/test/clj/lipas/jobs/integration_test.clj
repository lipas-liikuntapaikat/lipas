(ns lipas.jobs.integration-test
  "End-to-end integration tests: scheduler + worker processing real jobs
  against a real database."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [lipas.backend.core :as core]
    [lipas.backend.db.db :as db]
    [lipas.jobs.core :as jobs]
    [lipas.jobs.scheduler :as scheduler]
    [lipas.jobs.worker :as worker]
    [lipas.test-utils :as test-utils]
    [next.jdbc :as jdbc]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(deftest ^:integration complete-job-lifecycle-test
  (testing "Enqueue -> worker fetch -> process -> complete"
    (let [db (:lipas/db @test-system)
          test-emailer (test-utils/create-test-emailer)
          {email-id :id} (jobs/enqueue-job! db "email"
                                            {:to "test@example.com"
                                             :subject "Test"
                                             :body "Test message"})]
      (try
        (worker/start-mixed-duration-worker!
          {:db db :emailer test-emailer :search nil}
          {:fast-threads 1 :general-threads 1 :batch-size 5 :poll-interval-ms 300})

        (is (test-utils/wait-for-condition
              (fn []
                (= "completed" (:jobs/status (test-utils/get-job-by-id db email-id))))
              10000)
            "Email job should complete within timeout")

        (is (= 1 (count @(:sent-emails test-emailer))))
        (is (= "test@example.com" (:to (first @(:sent-emails test-emailer)))))

        (finally
          (worker/stop-mixed-duration-worker!))))))

(deftest ^:integration save-sports-site-job-triggers-test
  (testing "Save enqueues expensive jobs only when their inputs changed"
    (let [db (:lipas/db @test-system)
          search (:lipas/search @test-system)
          admin (test-utils/gen-user {:db? true :admin? true :db-component db})
          ;; 4402 = hiking route (LineString) so both analysis and elevation apply
          site (-> (test-utils/gen-sports-site-with-type 4402)
                   (dissoc :lipas-id :ptv)
                   (assoc :status "active"))
          job-types (fn [] (frequencies (map :jobs/type (test-utils/get-all-jobs db))))
          bump (fn [s] (assoc s :event-date (str (java.time.Instant/now))))
          saved (core/save-sports-site! db search nil admin site)]

      (testing "New site enqueues analysis and elevation"
        (is (= {"analysis" 1 "elevation" 1} (job-types))))

      (testing "Enqueue happens after the transaction commits"
        (let [[job] (test-utils/get-all-jobs db)]
          (is (some? (:jobs/created_at job)))))

      (testing "An irrelevant edit (name change) enqueues nothing"
        (jdbc/execute! db ["DELETE FROM jobs"])
        (core/save-sports-site! db search nil admin (bump (assoc saved :name "Renamed site")))
        (is (= {} (job-types))))

      (testing "A geometry edit enqueues both jobs again"
        (jdbc/execute! db ["DELETE FROM jobs"])
        (let [moved (update-in (bump saved) [:location :geometries :features 0 :geometry :coordinates]
                               (fn [coords]
                                 (mapv (fn [[x y & _]] [(+ x 0.001) y]) coords)))]
          (core/save-sports-site! db search nil admin moved)
          (is (= {"analysis" 1 "elevation" 1} (job-types)))))

      (testing "A status change enqueues analysis only"
        (jdbc/execute! db ["DELETE FROM jobs"])
        (let [current (core/get-sports-site db (:lipas-id saved))]
          (core/save-sports-site! db search nil admin
                                  (bump (assoc current :status "out-of-service-temporarily")))
          (is (= {"analysis" 1} (job-types))))))))

(deftest ^:integration save-survives-enqueue-failure-test
  (testing "A failing job enqueue neither rolls back nor fails the save"
    (let [db (:lipas/db @test-system)
          search (:lipas/search @test-system)
          admin (test-utils/gen-user {:db? true :admin? true :db-component db})
          site (-> (test-utils/gen-sports-site-with-type 4402)
                   (dissoc :lipas-id :ptv)
                   (assoc :status "active"))]
      (with-redefs [jobs/enqueue-job! (fn [& _] (throw (ex-info "queue down" {})))]
        (let [saved (core/save-sports-site! db search nil admin site)]
          (is (pos-int? (:lipas-id saved)))
          (is (seq (core/get-sports-site db (:lipas-id saved)))
              "The site revision is committed despite the enqueue failure")))
      (is (empty? (test-utils/get-all-jobs db))))))

(deftest ^:integration reminders-end-to-end-test
  (testing "Overdue reminder -> scheduler produces email job -> worker sends it"
    (let [db (:lipas/db @test-system)
          test-emailer (test-utils/create-test-emailer)]

      ;; Create a user with an overdue reminder
      (jdbc/execute! db
                     ["INSERT INTO account (email, username, password, status, user_data, permissions)
                       VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)"
                      "e2e-reminder@example.com" "e2euser" "test123" "active" "{}" "{}"])
      (let [user (first (db/get-users db))]
        (core/add-reminder! db user
                            {:body {:message "E2E reminder" :title "E2E"}
                             :event-date (java.sql.Timestamp/valueOf "2024-01-01 10:00:00")}))

      (try
        (worker/start-mixed-duration-worker!
          {:db db :emailer test-emailer :search nil}
          {:fast-threads 1 :general-threads 1 :batch-size 5 :poll-interval-ms 300})

        ;; Scheduler tick (called directly for determinism)
        (is (= 1 (scheduler/produce-reminder-emails! db)))

        (is (test-utils/wait-for-condition
              (fn [] (= 1 (count @(:sent-emails test-emailer))))
              10000)
            "Reminder email should be sent")

        (is (= "e2e-reminder@example.com"
               (:to (first @(:sent-emails test-emailer)))))

        (finally
          (worker/stop-mixed-duration-worker!))))))
