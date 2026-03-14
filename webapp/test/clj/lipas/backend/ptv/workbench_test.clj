(ns lipas.backend.ptv.workbench-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [lipas.backend.jwt :as jwt]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(defn- post-json [uri body]
  (-> (mock/request :post uri)
      (mock/content-type "application/json")
      (mock/body (tu/->json body))))

(deftest workbench-preview-requires-auth-test
  (testing "Unauthenticated request to preview-data returns 401"
    (let [resp (test-app (post-json "/api/actions/preview-ptv-workbench-data"
                                    {:flow "service-location" :lipas-id 1}))]
      (is (= 401 (:status resp)))))

  (testing "Non-admin user cannot access preview-data"
    (let [user  (tu/gen-regular-user :db-component (test-db))
          token (jwt/create-token user)
          resp  (test-app (-> (post-json "/api/actions/preview-ptv-workbench-data"
                                         {:flow "service-location" :lipas-id 1})
                              (tu/token-header token)))]
      (is (= 403 (:status resp))))))

(deftest workbench-experiment-requires-auth-test
  (testing "Unauthenticated request to experiment returns 401"
    (let [resp (test-app (post-json "/api/actions/run-ptv-workbench-experiment"
                                    {:system-prompt "test"
                                     :user-prompt   "test"
                                     :params        {:model "gpt-4.1-mini"}}))]
      (is (= 401 (:status resp)))))

  (testing "Non-admin user cannot access experiment"
    (let [user  (tu/gen-regular-user :db-component (test-db))
          token (jwt/create-token user)
          resp  (test-app (-> (post-json "/api/actions/run-ptv-workbench-experiment"
                                         {:system-prompt "test"
                                          :user-prompt   "test"
                                          :params        {:model "gpt-4.1-mini"}})
                              (tu/token-header token)))]
      (is (= 403 (:status resp))))))
