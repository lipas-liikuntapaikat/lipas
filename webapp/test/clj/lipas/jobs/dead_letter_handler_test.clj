(ns lipas.jobs.dead-letter-handler-test
  "Handler tests for dead letter queue endpoints."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [lipas.backend.jwt :as jwt]
    [lipas.jobs.core :as jobs]
    [lipas.jobs.db :as jobs-db]
    [lipas.test-utils :as test-utils]
    [next.jdbc :as jdbc]
    [ring.mock.request :as mock]))

;; Test system setup using shared fixture
(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- admin!
  "An admin account persisted in the test database.

   These tests used to authenticate as a hand-written map with `:id 1`. That no
   longer works: `mw/auth` now runs the per-user token-revocation check on every
   token request, and it looks the caller's `:id` up in `account` — a non-uuid
   id makes the lookup throw, and the check fails closed (see
   `lipas.backend.token-revocation`). Every caller here has to be a real row."
  [db]
  (test-utils/gen-admin-user :db-component db))

(defn- powerless!
  "A persisted account with no roles at all, for the 403 cases."
  [db]
  (test-utils/gen-user {:db? true :db-component db :permissions {:roles []}}))

(defn- token
  [user]
  (jwt/create-token user {:valid-seconds 300}))

;; Tests
(deftest get-dead-letter-jobs-endpoint-test
  (testing "GET /api/actions/get-dead-letter-jobs"
    (let [db (:lipas/db @test-system)
          app (:lipas/app @test-system)
          admin-token (token (admin! db))]

      ;; Create test dead letter jobs
      (test-utils/create-test-dead-letter-job! db {:acknowledged false})
      (test-utils/create-test-dead-letter-job! db {:acknowledged false})
      (test-utils/create-test-dead-letter-job! db {:acknowledged true})

      (testing "returns unacknowledged jobs when acknowledged=false"
        (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs?acknowledged=false")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 2 (count body)))
          (is (every? #(false? (:acknowledged %)) body))))

      (testing "returns acknowledged jobs when acknowledged=true"
        (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs?acknowledged=true")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 1 (count body)))
          (is (every? #(true? (:acknowledged %)) body))))

      (testing "returns all jobs when acknowledged not specified"
        (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 3 (count body))))))))

(deftest dead-letter-enrichment-test
  (testing "GET /api/actions/get-dead-letter-jobs enriches entries with :error-class and :superseded-by"
    (let [db (:lipas/db @test-system)
          app (:lipas/app @test-system)
          admin-token (token (admin! db))

          ;; Superseded case: analysis job for a site dies, then a newer
          ;; analysis job for the same site completes.
          {dead-id :id} (jobs/enqueue-job! db "analysis" {:lipas-id 111111})
          _ (jobs/move-to-dead-letter! db dead-id "Job execution timed out after 60 minutes")
          {newer-id :id} (jobs/enqueue-job! db "analysis" {:lipas-id 111111})]
      (jdbc/execute! db ["UPDATE jobs SET status = 'processing', started_at = now() WHERE id = ?" newer-id])
      (jobs/mark-completed! db newer-id)

      ;; Not superseded: elevation job dies, no later completed job for the site.
      (let [{lone-id :id} (jobs/enqueue-job! db "elevation" {:lipas-id 222222})]
        (jobs/move-to-dead-letter! db lone-id "java.net.SocketException: Connection reset"))

      (let [resp (app (-> (mock/request :get "/api/actions/get-dead-letter-jobs")
                          (mock/content-type "application/transit+json")
                          (mock/header "Accept" "application/transit+json")
                          (test-utils/token-header admin-token)))
            body (test-utils/<-transit (:body resp))
            by-site (fn [lipas-id]
                      (first (filter #(= lipas-id (get-in % [:original-job :payload :lipas-id]))
                                     body)))
            superseded (by-site 111111)
            lone (by-site 222222)]
        (is (= 200 (:status resp)))

        (testing "superseded entry"
          (is (some? superseded))
          (is (= :timeout (:error-class superseded)))
          (is (= newer-id (get-in superseded [:superseded-by :job-id])))
          (is (some? (get-in superseded [:superseded-by :completed-at]))))

        (testing "entry without a later completed job"
          (is (some? lone))
          (is (= :mml-api (:error-class lone)))
          (is (nil? (:superseded-by lone))))))))

(deftest reprocess-dead-letter-jobs-endpoint-test
  (testing "POST /api/actions/reprocess-dead-letter-jobs"
    (let [db (:lipas/db @test-system)
          app (:lipas/app @test-system)
          admin (admin! db)
          admin-token (token admin)

          ;; Create test dead letter jobs
          dlj1 (test-utils/create-test-dead-letter-job! db)
          dlj2 (test-utils/create-test-dead-letter-job! db)
          dlj3 (test-utils/create-test-dead-letter-job! db)]

      (testing "successfully reprocesses single job"
        (let [params {:dead-letter-ids [(:id dlj1)]}
              resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
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
            (is (= (:email admin) (:acknowledged_by dlj))))))

      (testing "successfully reprocesses multiple jobs"
        (let [params {:dead-letter-ids [(:id dlj2) (:id dlj3)]}
              resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 2 (count (:succeeded body))))
          (is (= 0 (count (:failed body))))))

      (testing "handles invalid dead letter ID gracefully"
        (let [params {:dead-letter-ids [99999]}
              resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 0 (count (:succeeded body))))
          (is (= 1 (count (:failed body))))
          (is (= 99999 (:dead-letter-id (first (:failed body)))))))

      (testing "supports custom max-attempts"
        (let [dlj4 (test-utils/create-test-dead-letter-job! db)
              params {:dead-letter-ids [(:id dlj4)]
                      :max-attempts 5}
              resp (app (-> (mock/request :post "/api/actions/reprocess-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 1 (count (:succeeded body))))

          ;; Verify the requeued job has correct max_attempts
          (let [new-job-id (:new-job-id (first (:succeeded body)))
                job (first (jdbc/execute! db ["SELECT * FROM jobs WHERE id = ?" new-job-id]))]
            (is (= 5 (:jobs/max_attempts job)))))))))

(deftest acknowledge-dead-letter-jobs-endpoint-test
  (testing "POST /api/actions/acknowledge-dead-letter-jobs"
    (let [db (:lipas/db @test-system)
          app (:lipas/app @test-system)
          admin (admin! db)
          admin-token (token admin)

          ;; Create test dead letter jobs
          dlj1 (test-utils/create-test-dead-letter-job! db {:acknowledged false})
          dlj2 (test-utils/create-test-dead-letter-job! db {:acknowledged false})
          dlj3 (test-utils/create-test-dead-letter-job! db {:acknowledged true})]

      (testing "successfully acknowledges single job"
        (let [params {:dead-letter-ids [(:id dlj1)]}
              resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 1 (:acknowledged body)))

          ;; Verify the job was acknowledged
          (let [dlj (jobs-db/get-dead-letter-by-id db {:id (:id dlj1)})]
            (is (:acknowledged dlj))
            (is (= (:email admin) (:acknowledged_by dlj)))
            (is (some? (:acknowledged_at dlj))))))

      (testing "successfully acknowledges multiple jobs"
        (let [dlj4 (test-utils/create-test-dead-letter-job! db {:acknowledged false})
              dlj5 (test-utils/create-test-dead-letter-job! db {:acknowledged false})
              params {:dead-letter-ids [(:id dlj4) (:id dlj5)]}
              resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
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
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          ;; Already acknowledged jobs are skipped
          (is (= 0 (:acknowledged body)))))

      (testing "handles invalid dead letter ID gracefully"
        (let [params {:dead-letter-ids [99999]}
              resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 0 (:acknowledged body)))))

      (testing "handles mixed valid and invalid IDs"
        (let [dlj6 (test-utils/create-test-dead-letter-job! db {:acknowledged false})
              params {:dead-letter-ids [(:id dlj6) 99999 88888]}
              resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          ;; Only the valid job should be acknowledged
          (is (= 1 (:acknowledged body)))))

      (testing "handles empty dead-letter-ids"
        (let [params {:dead-letter-ids []}
              resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Accept" "application/transit+json")
                            (test-utils/token-header admin-token)
                            (mock/body (test-utils/->transit params))))
              body (test-utils/<-transit (:body resp))]
          (is (= 200 (:status resp)))
          (is (= 0 (:acknowledged body)))))

      (testing "requires authorization"
        (let [no-auth-token (token (powerless! db))
              params {:dead-letter-ids [(:id dlj2)]}
              resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Authorization" (str "Token " no-auth-token))
                            (mock/body (test-utils/->transit params))))]
          (is (= 403 (:status resp))))))))

(deftest authorization-test
  (testing "Dead letter endpoints require jobs/manage permission"
    (let [app (:lipas/app @test-system)
          no-auth-token (token (powerless! (:lipas/db @test-system)))]

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
                            (mock/body (test-utils/->transit {:dead-letter-ids [1]}))))]
          (is (= 403 (:status resp)))))

      (testing "POST acknowledge endpoint returns 403 without permission"
        (let [resp (app (-> (mock/request :post "/api/actions/acknowledge-dead-letter-jobs")
                            (mock/content-type "application/transit+json")
                            (mock/header "Authorization" (str "Token " no-auth-token))
                            (mock/body (test-utils/->transit {:dead-letter-ids [1]}))))]
          (is (= 403 (:status resp))))))))

(comment
  (clojure.test/run-test-var #'authorization-test))
