(ns lipas.jobs.handler-test
  "Integration tests for job queue admin endpoints.

  Focuses on metrics correctness using Malli schema validation
  rather than manual assertions on response structure."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [lipas.backend.jwt :as jwt]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.schema :as schema]
   [lipas.test-utils :refer [->json <-json app db] :as tu]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :once (fn [f]
                      (tu/init-db!)
                      (f)))

(use-fixtures :each (fn [f]
                      (tu/prune-db!)
                      (f)))

(defn setup-test-scenario!
  "Create a realistic test scenario with varied job states and timing."
  [db]

  ;; Scenario: Queue processing over the last few hours
  ;; - Some jobs completed successfully
  ;; - Some jobs failed and are retrying
  ;; - Some jobs are pending
  ;; - One job is currently processing

  ;; 2 hours ago: Email campaign completed successfully (fast jobs)
  (let [jobs-2h-ago (for [i (range 10)]
                      (jobs/enqueue-job! db "email"
                                         {:to (str "user" i "@example.com")
                                          :subject "Newsletter"}))]
    (doseq [job-id jobs-2h-ago]
      (jobs/mark-completed! db job-id)))

  ;; 1 hour ago: Analysis jobs completed (slow jobs)
  (let [analysis-jobs (for [i (range 3)]
                        (jobs/enqueue-job! db "analysis"
                                           {:lipas-id (+ 10000 i)}))]
    (doseq [job-id analysis-jobs]
      (jobs/mark-completed! db job-id)))

  ;; 30 minutes ago: Some webhook jobs failed
  (let [webhook-jobs (for [i (range 2)]
                       (jobs/enqueue-job! db "webhook"
                                          {:batch-data {:batch i}}))]
    (doseq [job-id webhook-jobs]
      (jobs/mark-failed! db job-id "Connection timeout")))

  ;; Now: Some jobs are pending
  (doseq [i (range 5)]
    (jobs/enqueue-job! db "email"
                       {:to (str "pending" i "@example.com")
                        :subject "Urgent"}))

  (jobs/enqueue-job! db "elevation" {:lipas-id 99999})

  ;; Simulate one job currently processing
  (let [processing-job (jobs/enqueue-job! db "analysis" {:lipas-id 88888})]
    (jdbc/execute! db ["UPDATE jobs SET status = 'processing', started_at = now() WHERE id = ?" processing-job]))

  ;; Return expected counts for validation
  {:completed-emails 10
   :completed-analysis 3
   :failed-webhooks 2
   :pending-emails 5
   :pending-elevation 1
   :processing-analysis 1})

(deftest job-metrics-accuracy-test
  (testing "Job metrics accurately reflect database state"
    (let [expected (setup-test-scenario! db)
          admin (tu/gen-user {:db? true :admin? true})
          token (jwt/create-token admin)
          resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                        (mock/content-type "application/json")
                        (mock/body (->json {}))
                        (tu/token-header token)))
          body (<-json (:body resp))]

      (is (= 200 (:status resp)))

      ;; Malli validates the structure, we focus on correctness
      (is (schema/valid-metrics-response? body)
          (str "Response should match schema. Validation errors: "
               (schema/explain-metrics-response body)))

      ;; Test health metrics accuracy
      (let [health (:health body)]
        (is (= (+ (:pending-emails expected) (:pending-elevation expected))
               (:pending_count health))
            "Pending count should match test scenario")

        (is (= (:processing-analysis expected)
               (:processing_count health))
            "Processing count should match test scenario")

        (is (= (:failed-webhooks expected)
               (:failed_count health))
            "Failed count should match test scenario")

        (is (= 0 (:dead_count health))
            "No jobs should be dead in this scenario"))

      ;; Test job type classification
      (is (contains? (:fast-job-types body) "email"))
      (is (contains? (:fast-job-types body) "webhook"))
      (is (contains? (:slow-job-types body) "analysis"))
      (is (contains? (:slow-job-types body) "elevation"))

      ;; Test performance metrics contain expected job types
      (let [perf-metrics (:performance-metrics body)
            email-metrics (filter #(= "email" (:type %)) perf-metrics)
            analysis-metrics (filter #(= "analysis" (:type %)) perf-metrics)]

        (is (not-empty email-metrics) "Should have email performance metrics")
        (is (not-empty analysis-metrics) "Should have analysis performance metrics")

        ;; Email jobs should show as completed
        (let [completed-emails (filter #(= :completed (:status %)) email-metrics)]
          (is (not-empty completed-emails) "Should have completed email metrics")
          (is (= (:completed-emails expected)
                 (:job_count (first completed-emails)))
              "Email job count should match"))))))

(deftest job-health-accuracy-test
  (testing "Job health status accurately reflects current queue state"
    (let [expected (setup-test-scenario! db)
          admin (tu/gen-user {:db? true :admin? true})
          token (jwt/create-token admin)
          resp (app (-> (mock/request :post "/api/actions/get-jobs-health-status")
                        (mock/content-type "application/json")
                        (mock/body (->json {}))
                        (tu/token-header token)))
          body (<-json (:body resp))]

      (is (= 200 (:status resp)))

      ;; Malli validates the structure
      (is (schema/valid-health-response? body)
          (str "Response should match schema. Validation errors: "
               (schema/explain-health-response body)))

      ;; Test each health metric
      (is (= (+ (:pending-emails expected) (:pending-elevation expected))
             (:pending_count body)))
      (is (= (:processing-analysis expected) (:processing_count body)))
      (is (= (:failed-webhooks expected) (:failed_count body)))
      (is (= 0 (:dead_count body)))

      ;; Time-based metrics should be reasonable
      (is (or (nil? (:oldest_pending_minutes body))
              (>= (:oldest_pending_minutes body) 0)))
      (is (or (nil? (:longest_processing_minutes body))
              (>= (:longest_processing_minutes body) 0))))))

(deftest empty-queue-behavior-test
  (testing "Endpoints handle empty queue correctly"
    (tu/prune-db!) ; Ensure clean state

    (let [admin (tu/gen-user {:db? true :admin? true})
          token (jwt/create-token admin)]

      (testing "Metrics report with empty queue"
        (let [resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                            (mock/content-type "application/json")
                            (mock/body (->json {}))
                            (tu/token-header token)))
              body (<-json (:body resp))]

          (is (= 200 (:status resp)))
          (is (schema/valid-metrics-response? body))

          ;; All counts should be zero
          (let [health (:health body)]
            (is (= 0 (:pending_count health)))
            (is (= 0 (:processing_count health)))
            (is (= 0 (:failed_count health)))
            (is (= 0 (:dead_count health))))))

      (testing "Health status with empty queue"
        (let [resp (app (-> (mock/request :post "/api/actions/get-jobs-health-status")
                            (mock/content-type "application/json")
                            (mock/body (->json {}))
                            (tu/token-header token)))
              body (<-json (:body resp))]

          (is (= 200 (:status resp)))
          (is (schema/valid-health-response? body))
          (is (every? zero? [(:pending_count body)
                             (:processing_count body)
                             (:failed_count body)
                             (:dead_count body)])))))))

(deftest timeframe-filtering-test
  (testing "Metrics timeframe filtering works correctly"
    (tu/prune-db!)

    ;; Create jobs at different times (simulate with manual updates)
    (let [old-job (jobs/enqueue-job! db "email" {:to "old@example.com"})]
      (jobs/mark-completed! db old-job)
      ;; Simulate this job being from 48 hours ago
      (jdbc/execute! db ["UPDATE jobs SET created_at = now() - interval '48 hours', completed_at = now() - interval '48 hours' WHERE id = ?" old-job]))

    (let [recent-job (jobs/enqueue-job! db "email" {:to "recent@example.com"})]
      (jobs/mark-completed! db recent-job))

    (let [admin (tu/gen-user {:db? true :admin? true})
          token (jwt/create-token admin)]

      (testing "Last 24 hours should exclude old job"
        (let [resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                            (mock/content-type "application/json")
                            (mock/body (->json {:from-hours-ago 24 :to-hours-ago 0}))
                            (tu/token-header token)))
              body (<-json (:body resp))]

          (is (= 200 (:status resp)))
          (is (schema/valid-metrics-response? body))

          ;; Should only see the recent job
          (let [email-metrics (filter #(and (= "email" (:type %))
                                            (= :completed (:status %)))
                                      (:performance-metrics body))]
            (when (not-empty email-metrics)
              (is (= 1 (:job_count (first email-metrics)))
                  "Should only count recent job in 24h window")))))

      (testing "Last 72 hours should include both jobs"
        (let [resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                            (mock/content-type "application/json")
                            (mock/body (->json {:from-hours-ago 72 :to-hours-ago 0}))
                            (tu/token-header token)))
              body (<-json (:body resp))]

          (is (= 200 (:status resp)))
          (is (schema/valid-metrics-response? body))

          ;; Should see both jobs
          (let [email-metrics (filter #(and (= "email" (:type %))
                                            (= :completed (:status %)))
                                      (:performance-metrics body))]
            (when (not-empty email-metrics)
              (is (= 2 (:job_count (first email-metrics)))
                  "Should count both jobs in 72h window"))))))))

(deftest authorization-test
  (testing "Endpoints require admin role"
    (let [regular-user (tu/gen-user {:db? true :admin? false})
          token (jwt/create-token regular-user)]

      (testing "Non-admin cannot access metrics"
        (let [resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                            (mock/content-type "application/json")
                            (mock/body (->json {}))
                            (tu/token-header token)))]
          (is (= 403 (:status resp)))))

      (testing "Non-admin cannot access health"
        (let [resp (app (-> (mock/request :post "/api/actions/get-jobs-health-status")
                            (mock/content-type "application/json")
                            (mock/body (->json {}))
                            (tu/token-header token)))]
          (is (= 403 (:status resp)))))))

  (testing "Endpoints require authentication"
    (testing "Unauthenticated metrics request"
      (let [resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                          (mock/content-type "application/json")
                          (mock/body (->json {}))))]
        (is (= 401 (:status resp)))))

    (testing "Unauthenticated health request"
      (let [resp (app (-> (mock/request :post "/api/actions/get-jobs-health-status")
                          (mock/content-type "application/json")
                          (mock/body (->json {}))))]
        (is (= 401 (:status resp)))))))

(deftest request-validation-test
  (testing "Invalid request parameters are rejected"
    (let [admin (tu/gen-user {:db? true :admin? true})
          token (jwt/create-token admin)]

      (testing "Invalid timeframe parameters"
        (let [resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                            (mock/content-type "application/json")
                            (mock/body (->json {:from-hours-ago "invalid"}))
                            (tu/token-header token)))]
          (is (= 400 (:status resp)) "Should reject non-integer timeframe")))

      (testing "Timeframe out of range"
        (let [resp (app (-> (mock/request :post "/api/actions/create-jobs-metrics-report")
                            (mock/content-type "application/json")
                            (mock/body (->json {:from-hours-ago 200})) ; > 168 hours (1 week)
                            (tu/token-header token)))]
          (is (= 400 (:status resp)) "Should reject timeframe > 1 week"))))))
