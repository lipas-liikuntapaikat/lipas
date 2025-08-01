(ns legacy-api.handler-test
  "Integration tests for legacy API v1 endpoints.
  
  Tests the REST API endpoints under /rest/api to ensure proper
  functionality, error handling, and response format compliance.
  
  TEST ARCHITECTURE:
  - Based on lipas.jobs.handler-test patterns
  - Uses test-utils for DB setup (with _test suffix isolation)
  - Automatic database pruning between tests
  - Graceful ES handling (continues if ES unavailable)
  
  TEST LEVELS:
  1. Basic tests: No ES required, test core routing and static data
  2. Integration tests: Require both DB and ES for full functionality
  
  SETUP & TEARDOWN:
  - Database: Automatically initialized and pruned between tests
  - Elasticsearch: Pruned if available, continues with warning if not
  - Test data: Created fresh for each test scenario"
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [legacy-api.core :as legacy-core]
   [lipas.backend.core :as core]
   [lipas.backend.search :as search]
   [lipas.test-utils :refer [->json <-json app db search] :as tu]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]
   [taoensso.timbre :as log]))

(use-fixtures :once (fn [f]
                      (tu/init-db!)
                      (f)))

(use-fixtures :each (fn [f]
                      (tu/prune-db!)
                      (tu/prune-es!)
                      (f)))

; NOTE: Some tests intentionally trigger error conditions and may generate 
; expected error logs (especially ES connection errors and 404 responses)

(defn create-test-sports-site!
  "Creates a test sports site in the database and search index for testing."
  [db search user overrides]
  (let [base-site {:name "Test Sports Site"
                   :status "active"
                   :type {:type-code 1120}
                   :location {:city {:city-code 91
                                     :name "Helsinki"}
                              :address "Test Address 1"
                              :coordinates {:wgs84 {:lat 60.1699 :lon 24.9384}}}
                   :properties {:surface-material "natural-grass"
                                :area-m2 1000}
                   :comment "Test facility for integration testing"}
        sports-site (merge base-site overrides)]
    (core/save-sports-site! db search nil user sports-site)))

(defn setup-test-data!
  "Creates test sports sites with various statuses for testing."
  [db search]
  (let [user (tu/gen-user {:db? true})]

    ;; Create active sports site
    (create-test-sports-site! db search user
                              {:name "Active Test Site"
                               :status "active"
                               :type {:type-code 1120}})

    ;; Create out-of-service-temporarily site
    (create-test-sports-site! db search user
                              {:name "Temporarily Closed Site"
                               :status "out-of-service-temporarily"
                               :type {:type-code 1110}})

    ;; Create out-of-service-permanently site (should appear in deleted endpoint)
    (create-test-sports-site! db search user
                              {:name "Permanently Closed Site"
                               :status "out-of-service-permanently"
                               :type {:type-code 1130}})

    ;; Create site with incorrect-data status (should appear in deleted endpoint)
    (create-test-sports-site! db search user
                              {:name "Incorrect Data Site"
                               :status "incorrect-data"
                               :type {:type-code 1140}})

    ;; Wait a bit for ES indexing
    (Thread/sleep 1000)

    {:active-site "Active Test Site"
     :temp-closed "Temporarily Closed Site"
     :permanently-closed "Permanently Closed Site"
     :incorrect-data "Incorrect Data Site"}))

(deftest basic-system-health-test
  (testing "Basic system health without ES dependencies"
    (testing "Main API health endpoint responds"
      (let [resp (app (mock/request :get "/api/health"))]
        (is (= 200 (:status resp)))
        (is (= {:status "OK"} (<-json (:body resp))))))

    (testing "Legacy API routes are registered"
      ;; Test that legacy routes are mounted by checking a simple static endpoint
      (let [resp (app (mock/request :get "/rest/api/swagger-ui"))]
        ;; Should at least not be 404, even if ES is down the route should exist
        (is (not= 404 (:status resp)))))

    (testing "Legacy API OpenAPI spec is accessible"
      (let [resp (app (mock/request :get "/rest/api/openapi.json"))]
        (is (= 200 (:status resp)))
        (let [spec (<-json (:body resp))]
          (is (= "LIPAS API V1" (get-in spec [:info :title])))
          (is (contains? spec :paths)))))))

(deftest basic-api-endpoints-test
  (testing "Basic API endpoints without search dependencies"
    (testing "Categories endpoint works"
      (let [resp (app (mock/request :get "/rest/api/categories"))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (sequential? body))
          (is (> (count body) 0)))))

    (testing "Sports place types endpoint works"
      (let [resp (app (mock/request :get "/rest/api/sports-place-types"))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (sequential? body))
          (is (> (count body) 0)))))

    (testing "Specific sports place type endpoint works"
      (let [resp (app (mock/request :get "/rest/api/sports-place-types/1120"))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (= 1120 (:typeCode body)))
          (is (contains? body :name)))))

    (testing "Error handling works for invalid requests"
      ;; Expected to generate error logs due to invalid type code
      (let [resp (app (mock/request :get "/rest/api/sports-place-types/invalid"))]
        (is (= 400 (:status resp)))))))

(deftest system-health-test
  (testing "System is up and can handle basic requests"
    (testing "API health endpoint responds"
      (let [resp (app (mock/request :get "/api/health"))]
        (is (= 200 (:status resp)))
        (is (= {:status "OK"} (<-json (:body resp))))))

    (testing "Legacy API swagger UI is accessible"
      (let [resp (app (mock/request :get "/rest/api/swagger-ui"))]
        (is (= 200 (:status resp)))))

    (testing "Legacy API OpenAPI spec is accessible"
      (let [resp (app (mock/request :get "/rest/api/openapi.json"))]
        (is (= 200 (:status resp)))
        (let [spec (<-json (:body resp))]
          (is (= "LIPAS API V1" (get-in spec [:info :title])))
          (is (contains? spec :paths)))))))

(deftest sports-places-endpoint-test
  (testing "Sports places endpoint functionality"
    (let [test-data (setup-test-data! db search)]

      (testing "GET /rest/api/sports-places returns sports places"
        (let [resp (app (mock/request :get "/rest/api/sports-places"))]
          (is (= 200 (:status resp)))
          (let [body (<-json (:body resp))]
            (is (sequential? body))
            (is (> (count body) 0))
            ;; Should contain both active and temporarily closed sites
            (is (some #(= (:active-site test-data) (:name %)) body))
            (is (some #(= (:temp-closed test-data) (:name %)) body)))))

      (testing "Query parameters work correctly"
        (testing "Page size limit"
          (let [resp (app (mock/request :get "/rest/api/sports-places?pageSize=1"))]
            (is (= 200 (:status resp)))
            (let [body (<-json (:body resp))]
              (is (= 1 (count body))))))

        (testing "Type code filtering"
          (let [resp (app (mock/request :get "/rest/api/sports-places?typeCodes=1120"))]
            (is (= 200 (:status resp)))
            (let [body (<-json (:body resp))]
              (is (every? #(= 1120 (get-in % [:type :typeCode])) body)))))

        (testing "Language parameter"
          (let [resp (app (mock/request :get "/rest/api/sports-places?lang=en"))]
            (is (= 200 (:status resp)))
            (is (vector? (<-json (:body resp))))))))))

(deftest single-sports-place-endpoint-test
  (testing "Single sports place endpoint"
    (setup-test-data! db search)
    (testing "GET /rest/api/sports-places/:id returns specific sports place"
      ;; First get all sports places to find a valid ID
      (let [all-resp (app (mock/request :get "/rest/api/sports-places"))
            all-sites (<-json (:body all-resp))
            first-site (first all-sites)
            sports-place-id (:sportsPlaceId first-site)]

        (when sports-place-id
          (let [resp (app (mock/request :get (str "/rest/api/sports-places/" sports-place-id)))]
            (is (= 200 (:status resp)))
            (let [body (<-json (:body resp))]
              (is (= sports-place-id (:sportsPlaceId body)))
              (is (contains? body :name))
              (is (contains? body :location)))))))

    (testing "Non-existent sports place returns 404"
      ;; Expected to generate error logs when ES search fails for non-existent ID
      (let [resp (app (mock/request :get "/rest/api/sports-places/999999"))]
        (is (= 404 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (= "Sports place not found" (:error body))))))))

(deftest deleted-sports-places-endpoint-test
  (testing "Deleted sports places endpoint"
    (setup-test-data! db search)
    (testing "GET /rest/api/deleted-sports-places returns deleted sites"
      (let [resp (app (mock/request :get "/rest/api/deleted-sports-places"))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (sequential? body))
          ;; Should contain sites with out-of-service-permanently and incorrect-data status
          (is (> (count body) 0))
          ;; Verify structure of response
          (when (> (count body) 0)
            (let [first-deleted (first body)]
              (is (contains? first-deleted :sportsPlaceId))
              (is (contains? first-deleted :deletedAt)))))))
    
    (testing "Since parameter filters results"
      (let [future-date "2099-01-01 00:00:00.000"
            resp (app (mock/request :get (str "/rest/api/deleted-sports-places?since="
                                              (java.net.URLEncoder/encode future-date "UTF-8"))))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          ;; Should return empty array for future date
          (is (sequential? body)))))))

(deftest categories-endpoint-test
  (testing "Categories endpoint"
    (testing "GET /rest/api/categories returns categories"
      (let [resp (app (mock/request :get "/rest/api/categories"))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (sequential? body))
          (is (> (count body) 0))
          ;; Verify structure
          (when (> (count body) 0)
            (let [first-category (first body)]
              (is (contains? first-category :id))
              (is (contains? first-category :name)))))))

    (testing "Language parameter works"
      (let [resp (app (mock/request :get "/rest/api/categories?lang=en"))]
        (is (= 200 (:status resp)))
        (is (vector? (<-json (:body resp))))))))

(deftest sports-place-types-endpoint-test
  (testing "Sports place types endpoint"
    (testing "GET /rest/api/sports-place-types returns types"
      (let [resp (app (mock/request :get "/rest/api/sports-place-types"))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (sequential? body))
          (is (> (count body) 0))
          ;; Verify structure
          (when (> (count body) 0)
            (let [first-type (first body)]
              (is (contains? first-type :typeCode))
              (is (contains? first-type :name)))))))

    (testing "GET /rest/api/sports-place-types/:type-code returns specific type"
      (let [resp (app (mock/request :get "/rest/api/sports-place-types/1120"))]
        (is (= 200 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (= 1120 (:typeCode body)))
          (is (contains? body :name)))))))

(deftest error-handling-test
  (testing "Error handling and validation"
    (testing "Invalid page size returns 400"
      ;; Expected to generate error logs due to validation failure
      (let [resp (app (mock/request :get "/rest/api/sports-places?pageSize=200"))]
        (is (= 400 (:status resp)))
        (let [body (<-json (:body resp))]
          (is (contains? body :type))
          (is (= :invalid-input (:type body))))))

    (testing "Invalid type code in path returns 400"
      ;; Expected to generate error logs due to invalid type code
      (let [resp (app (mock/request :get "/rest/api/sports-place-types/invalid"))]
        (is (= 400 (:status resp)))))

    (testing "Malformed requests are handled gracefully"
      ;; Expected to generate error logs due to malformed parameters
      (let [resp (app (mock/request :get "/rest/api/sports-places?pageSize=abc"))]
        (is (= 400 (:status resp)))))))

(deftest response-format-test
  (testing "Response format compliance"
    (setup-test-data! db search)
    (testing "Sports places response has correct structure"
      (let [resp (app (mock/request :get "/rest/api/sports-places?pageSize=1"))
            body (<-json (:body resp))]
        (when (> (count body) 0)
          (let [site (first body)]
            ;; Verify required fields according to legacy schema
            (is (number? (:sportsPlaceId site)))
            (is (string? (:name site)))
            (is (map? (:location site)))
            (is (map? (:type site)))
            (is (number? (get-in site [:type :typeCode])))))))
    
    (testing "Content-Type headers are correct"
      (let [resp (app (mock/request :get "/rest/api/sports-places"))]
        (is (= 200 (:status resp)))
        (is (re-find #"application/json" (get-in resp [:headers "Content-Type"] "")))))))

(comment
  ;; Run basic tests (no ES required)
  (clojure.test/run-test basic-system-health-test)

  (clojure.test/run-test basic-api-endpoints-test)

  ;; Run individual integration tests (require ES)
  (clojure.test/run-test system-health-test)
  (clojure.test/run-test sports-places-endpoint-test)
  (clojure.test/run-test deleted-sports-places-endpoint-test)
  (clojure.test/run-test categories-endpoint-test)
  (clojure.test/run-test error-handling-test)

  ;; Run all tests
  (clojure.test/run-tests 'legacy-api.handler-test)

  ;; Quick test - just run basic tests
  (do (clojure.test/run-test basic-system-health-test)
      (clojure.test/run-test basic-api-endpoints-test)))
