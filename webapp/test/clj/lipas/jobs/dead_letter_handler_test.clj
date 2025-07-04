(ns lipas.jobs.dead-letter-handler-test
  "Handler tests for dead letter queue endpoints."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [cognitect.transit :as transit]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.backend.jwt :as jwt]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.db :as jobs-db]
   [lipas.test-utils :as test-utils]
   [muuntaja.core :as m]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

;; Test system setup
(defonce test-system (atom nil))

(defn setup-test-system! []
  (test-utils/ensure-test-database!)
  (reset! test-system
          (ig/init (config/->system-config test-utils/config))))

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

;; Helper functions
(defn ->transit [x]
  (let [out (java.io.ByteArrayOutputStream. 4096)
        writer (cognitect.transit/writer out :json)]
    (cognitect.transit/write writer x)
    (.toString out)))

(defn <-transit [in]
  (when in
    (let [reader (transit/reader in :json)]
      (transit/read reader))))

(defn create-test-dead-letter-job!
  "Create a dead letter job for testing"
  [db & [{:keys [job-type payload error-message acknowledged]
          :or {job-type "email"
               payload {:to "test@example.com" :template "welcome"}
               error-message "SMTP connection failed"
               acknowledged false}}]]
  (let [original-job {:id 1
                      :type job-type
                      :payload payload
                      :status "failed"
                      :priority 100
                      :attempts 3
                      :max_attempts 3
                      :correlation_id (java.util.UUID/randomUUID)}
        dlj (jobs-db/insert-dead-letter!
             db
             {:original_job original-job
              :error_message error-message
              :error_details nil
              :correlation_id (:correlation_id original-job)})]
    (when acknowledged
      (jobs-db/acknowledge-dead-letter! db {:id (:id dlj)
                                            :acknowledged_by "system"}))
    (jobs-db/get-dead-letter-by-id db {:id (:id dlj)})))

(defn create-admin-token []
  (let [admin-user {:id 1
                    :email "admin@lipas.fi"
                    :username "admin"
                    :permissions {:roles [{:role :admin}]}}]
    (jwt/create-token admin-user {:valid-seconds 300})))

;; Tests
(deftest get-dead-letter-jobs-endpoint-test
  (testing "GET /api/actions/get-dead-letter-jobs"
    (let [db (:lipas/db @test-system)
          app (:lipas/app @test-system)]

      ;; Create test dead letter jobs
      (create-test-dead-letter-job! db {:acknowledged false})
      (create-test-dead-letter-job! db {:acknowledged false})
      (create-test-dead-letter-job! db {:acknowledged true})

      (testing "returns unacknowledged jobs when acknowledged=false"
        (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs?acknowledged=false")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (mock/header "Authorization" (str "Token " (create-admin-token)))))
              body (<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 2 (count body)))
          (is (every? #(false? (:acknowledged %)) body))))

      (testing "returns acknowledged jobs when acknowledged=true"
        (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs?acknowledged=true")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (mock/header "Authorization" (str "Token " (create-admin-token)))))
              body (<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 1 (count body)))
          (is (every? #(true? (:acknowledged %)) body))))

      (testing "returns all jobs when acknowledged not specified"
        (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (mock/header "Authorization" (str "Token " (create-admin-token)))))
              body (<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 3 (count body))))))))

(deftest reprocess-dead-letter-jobs-endpoint-test
  (testing "POST /api/actions/reprocess-dead-letter-jobs"
    (let [db (:lipas/db @test-system)
          app (:lipas/app @test-system)]

      ;; Create test dead letter jobs
      (let [dlj1 (create-test-dead-letter-job! db)
            dlj2 (create-test-dead-letter-job! db)
            dlj3 (create-test-dead-letter-job! db)]

        (testing "successfully reprocesses single job"
          (let [params {:dead-letter-ids [(:id dlj1)]}
                resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 1 (count (:succeeded body))))
            (is (= 0 (count (:failed body))))

            ;; Verify the job was requeued
            (let [succeeded (first (:succeeded body))]
              (is (= (:id dlj1) (:dead-letter-id succeeded)))
              (is (pos? (:new-job-id succeeded))))

            ;; Verify the dead letter job was acknowledged
            (let [dlj (jobs-db/get-dead-letter-by-id db {:id (:id dlj1)})]
              (is (:acknowledged dlj))
              (is (= "admin@lipas.fi" (:acknowledged_by dlj))))))

        (testing "successfully reprocesses multiple jobs"
          (let [params {:dead-letter-ids [(:id dlj2) (:id dlj3)]}
                resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 2 (count (:succeeded body))))
            (is (= 0 (count (:failed body))))))

        (testing "handles invalid dead letter ID gracefully"
          (let [params {:dead-letter-ids [99999]}
                resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 0 (count (:succeeded body))))
            (is (= 1 (count (:failed body))))
            (is (= 99999 (:dead-letter-id (first (:failed body)))))))

        (testing "supports custom max-attempts"
          (let [dlj4 (create-test-dead-letter-job! db)
                params {:dead-letter-ids [(:id dlj4)]
                        :max-attempts 5}
                resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 1 (count (:succeeded body))))

            ;; Verify the requeued job has correct max_attempts
            (let [new-job-id (:new-job-id (first (:succeeded body)))
                  job (first (jdbc/execute! db ["SELECT * FROM jobs WHERE id = ?" new-job-id]))]
              (is (= 5 (:jobs/max_attempts job))))))))))

(deftest acknowledge-dead-letter-jobs-endpoint-test
  (testing "POST /api/actions/acknowledge-dead-letter-jobs"
    (let [db (:lipas/db @test-system)
          app (:lipas/app @test-system)]

      ;; Create test dead letter jobs
      (let [dlj1 (create-test-dead-letter-job! db {:acknowledged false})
            dlj2 (create-test-dead-letter-job! db {:acknowledged false})
            dlj3 (create-test-dead-letter-job! db {:acknowledged true})]

        (testing "successfully acknowledges single job"
          (let [params {:dead-letter-ids [(:id dlj1)]}
                resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 1 (:acknowledged body)))

            ;; Verify the job was acknowledged
            (let [dlj (jobs-db/get-dead-letter-by-id db {:id (:id dlj1)})]
              (is (:acknowledged dlj))
              (is (= "admin@lipas.fi" (:acknowledged_by dlj)))
              (is (some? (:acknowledged_at dlj))))))

        (testing "successfully acknowledges multiple jobs"
          (let [dlj4 (create-test-dead-letter-job! db {:acknowledged false})
                dlj5 (create-test-dead-letter-job! db {:acknowledged false})
                params {:dead-letter-ids [(:id dlj4) (:id dlj5)]}
                resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 2 (:acknowledged body)))

            ;; Verify both jobs were acknowledged
            (let [dlj4-after (jobs-db/get-dead-letter-by-id db {:id (:id dlj4)})
                  dlj5-after (jobs-db/get-dead-letter-by-id db {:id (:id dlj5)})]
              (is (:acknowledged dlj4-after))
              (is (:acknowledged dlj5-after)))))

        (testing "handles already acknowledged jobs gracefully"
          (let [params {:dead-letter-ids [(:id dlj3)]}
                resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            ;; Already acknowledged jobs are skipped
            (is (= 0 (:acknowledged body)))))

        (testing "handles invalid dead letter ID gracefully"
          (let [params {:dead-letter-ids [99999]}
                resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 0 (:acknowledged body)))))

        (testing "handles mixed valid and invalid IDs"
          (let [dlj6 (create-test-dead-letter-job! db {:acknowledged false})
                params {:dead-letter-ids [(:id dlj6) 99999 88888]}
                resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            ;; Only the valid job should be acknowledged
            (is (= 1 (:acknowledged body)))))

        (testing "handles empty dead-letter-ids"
          (let [params {:dead-letter-ids []}
                resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Accept" "application/transit+json")
                              (mock/header "Authorization" (str "Token " (create-admin-token)))
                              (mock/body (->transit params))))
                body (<-transit (:body resp))]
            (is (= 200 (:status resp)))
            (is (= 0 (:acknowledged body)))))

        (testing "requires authorization"
          (let [no-auth-token (jwt/create-token
                               {:id 1 :email "user@test.com" :permissions {:roles []}}
                               {:valid-seconds 300})
                params {:dead-letter-ids [(:id dlj2)]}
                resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                              (mock/content-type "application/transit+json")
                              (mock/header "Authorization" (str "Token " no-auth-token))
                              (mock/body (->transit params))))]
            (is (= 403 (:status resp)))))))))

(deftest authorization-test
  (testing "Dead letter endpoints require jobs/manage permission"
    (let [app (:lipas/app @test-system)
          no-auth-token (jwt/create-token
                         {:id 1 :email "user@test.com" :permissions {:roles []}}
                         {:valid-seconds 300})]

      (testing "GET endpoint returns 403 without permission"
        (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (mock/header "Authorization" (str "Token " no-auth-token))))]
          (is (= 403 (:status resp)))))

      (testing "POST reprocess endpoint returns 403 without permission"
        (let [resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Authorization" (str "Token " no-auth-token))
                            (mock/body (->transit {:dead-letter-ids [1]}))))]
          (is (= 403 (:status resp)))))

      (testing "POST acknowledge endpoint returns 403 without permission"
        (let [resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Authorization" (str "Token " no-auth-token))
                            (mock/body (->transit {:dead-letter-ids [1]}))))]
          (is (= 403 (:status resp))))))))

(comment
  (clojure.test/run-test-var #'authorization-test))
