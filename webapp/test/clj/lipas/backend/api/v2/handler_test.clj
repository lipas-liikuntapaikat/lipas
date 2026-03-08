(ns lipas.backend.api.v2.handler-test
  "Integration tests for the V2 API.

   These tests verify that:
   1. Each endpoint returns correct HTTP status codes
   2. Response structure matches the V2 API contract
   3. Query parameters work as expected (filtering, pagination, etc.)"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [lipas.backend.core :as core]
   [lipas.test-utils :as test-utils]
   [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

;;; Helper Functions ;;;

(defn test-app []
  (:lipas/app @test-system))

(defn test-db []
  (:lipas/db @test-system))

(defn test-search []
  (:lipas/search @test-system))

;;; Fixtures ;;;

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Test Data Helpers ;;;

(defn create-admin-user []
  (test-utils/gen-admin-user :db-component (test-db)))

(defn- ensure-int-city-code
  "Converts city-code to integer for v2 schema compliance."
  [site]
  (let [cc (get-in site [:location :city :city-code])]
    (cond-> site
      (string? cc) (assoc-in [:location :city :city-code] (Integer/parseInt cc)))))

(defn create-sports-site!
  "Creates a sports site in the database and indexes it to the main ES index.
   Converts city-code to integer for v2 response schema compliance."
  [site]
  (let [admin (create-admin-user)
        site (ensure-int-city-code site)]
    (core/upsert-sports-site!* (test-db) admin site)
    (core/index! (test-search) site :sync)
    (Thread/sleep 100)
    site))

(def polygon-loi-types #{"nature-reserve" "other-area-with-movement-restrictions"})

(defn- fix-loi-geometry
  "Ensures LOI geometry matches the required geometry type for its loi-type."
  [loi]
  (if (polygon-loi-types (:loi-type loi))
    (assoc loi :geometries {:type "FeatureCollection"
                            :features [{:type "Feature"
                                        :geometry {:type "Polygon"
                                                   :coordinates [[[25.0 60.2]
                                                                  [25.01 60.2]
                                                                  [25.01 60.21]
                                                                  [25.0 60.21]
                                                                  [25.0 60.2]]]}}]})
    loi))

(defn create-loi!
  "Creates a LOI and indexes it to ES."
  [loi]
  (let [admin (create-admin-user)
        loi (fix-loi-geometry loi)]
    (core/upsert-loi! (test-db) (test-search) admin loi)
    (Thread/sleep 100)
    loi))

;;; Response parsing helpers ;;;

(defn parse-json-body [response]
  (let [body (test-utils/<-json (:body response))]
    (cond
      (sequential? body) (vec body)
      :else body)))

;;; Tests for GET /v2/sports-site-categories ;;;

(deftest get-all-categories-test
  (testing "GET /v2/sports-site-categories"

    (testing "returns 200 with categories"
      (let [resp ((test-app) (mock/request :get "/v2/sports-site-categories"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? body))
        (is (pos? (count body)))
        (doseq [cat body]
          (is (integer? (:type-code cat)))
          (is (map? (:name cat)))
          (is (string? (:fi (:name cat)))))))))

(deftest get-category-by-type-code-test
  (testing "GET /v2/sports-site-categories/:type-code"

    (testing "returns 200 for valid type code"
      (let [resp ((test-app) (mock/request :get "/v2/sports-site-categories/1120"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1120 (:type-code body)))
        (is (map? (:name body)))))

    (testing "returns error for invalid type code"
      (let [resp ((test-app) (mock/request :get "/v2/sports-site-categories/9999999"))]
        (is (#{400 404} (:status resp)))))))

;;; Tests for GET /v2/sports-sites (list) ;;;

(deftest list-sports-sites-empty-test
  (testing "GET /v2/sports-sites returns 200 with empty items"
    (let [resp ((test-app) (mock/request :get "/v2/sports-sites"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (map? body))
      (is (vector? (:items body)))
      (is (empty? (:items body)))
      (is (map? (:pagination body)))
      (is (= 0 (-> body :pagination :total-items))))))

(deftest list-sports-sites-with-results-test
  (testing "GET /v2/sports-sites returns items with pagination"
    (doseq [i (range 1 4)]
      (create-sports-site! (test-utils/make-point-site (+ 20000 i) :name (str "V2 Test Site " i))))

    (let [resp ((test-app) (mock/request :get "/v2/sports-sites"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 3 (count (:items body))))
      (is (= 3 (-> body :pagination :total-items)))
      (is (= 1 (-> body :pagination :current-page)))

      (testing "items have expected v2 structure"
        (doseq [item (:items body)]
          (is (integer? (:lipas-id item)))
          (is (string? (:name item)))
          (is (string? (:status item)))
          (is (map? (:type item)))
          (is (integer? (-> item :type :type-code)))
          (is (map? (:location item))))))))

(deftest list-sports-sites-pagination-test
  (testing "GET /v2/sports-sites pagination"
    (doseq [i (range 1 16)]
      (create-sports-site! (test-utils/make-point-site (+ 30000 i) :name (str "Pagination Site " i))))

    (testing "default page size returns up to 10"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (<= (count (:items body)) 10))
        (is (= 15 (-> body :pagination :total-items)))
        (is (= 2 (-> body :pagination :total-pages)))))

    (testing "custom page-size"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=5"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 5 (count (:items body))))
        (is (= 3 (-> body :pagination :total-pages)))))

    (testing "page parameter returns different items"
      (let [resp1 ((test-app) (mock/request :get "/v2/sports-sites?page-size=5&page=1"))
            resp2 ((test-app) (mock/request :get "/v2/sports-sites?page-size=5&page=2"))
            body1 (parse-json-body resp1)
            body2 (parse-json-body resp2)]
        (is (= 200 (:status resp1)))
        (is (= 200 (:status resp2)))
        (let [ids1 (set (map :lipas-id (:items body1)))
              ids2 (set (map :lipas-id (:items body2)))]
          (is (empty? (clojure.set/intersection ids1 ids2))
              "Page 1 and 2 should have different items"))))))

(deftest list-sports-sites-filter-by-type-codes-test
  (testing "GET /v2/sports-sites filter by type-codes"
    (create-sports-site! (test-utils/make-point-site 40001 :name "Type 1120 Site" :type-code 1120))
    (create-sports-site! (test-utils/make-point-site 40002 :name "Type 1310 Site" :type-code 1310))

    (testing "single type-code filter"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?type-codes=1120"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? #(= 1120 (-> % :type :type-code)) (:items body)))))

    (testing "multiple type-codes filter (comma-separated)"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?type-codes=1120,1310"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))
        (is (= #{1120 1310} (set (map #(-> % :type :type-code) (:items body)))))))

    (testing "multiple type-codes filter (repeated params)"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?type-codes=1120&type-codes=1310"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))))))

(deftest list-sports-sites-filter-by-city-codes-test
  (testing "GET /v2/sports-sites filter by city-codes"
    (create-sports-site! (test-utils/make-point-site 50001 :name "Helsinki Site" :city-code "91"))
    (create-sports-site! (test-utils/make-point-site 50002 :name "Espoo Site" :city-code "49"))

    (testing "single city-code filter"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?city-codes=91"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1 (count (:items body))))
        (is (= "91" (-> body :items first :location :city :city-code str)))))

    (testing "multiple city-codes filter"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?city-codes=91,49"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))))))

(deftest list-sports-sites-filter-by-statuses-test
  (testing "GET /v2/sports-sites filter by statuses"
    (create-sports-site! (test-utils/make-point-site 51001 :name "Active Site" :status "active"))
    (create-sports-site! (test-utils/make-point-site 51002 :name "Temp OOS Site" :status "out-of-service-temporarily"))

    (testing "filter active only"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?statuses=active"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? #(= "active" (:status %)) (:items body)))))

    (testing "filter multiple statuses"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?statuses=active,out-of-service-temporarily"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))))))

;;; Tests for GET /v2/sports-sites/:lipas-id ;;;

(deftest get-single-sports-site-test
  (testing "GET /v2/sports-sites/:lipas-id"

    (testing "returns 200 for existing sports site"
      (let [site (test-utils/make-point-site 12345 :name "Test Lähiliikuntapaikka")
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/12345"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 12345 (:lipas-id body)))
        (is (string? (:name body)))
        (is (map? (:type body)))
        (is (= 1120 (-> body :type :type-code)))
        (is (map? (:location body)))))

    (testing "returns correct v2 field format"
      (let [site (-> (test-utils/make-point-site 12346
                                                 :name "Test Football Field"
                                                 :type-code 1310)
                     (assoc :construction-year 2010
                            :event-date "2024-06-15T10:30:00.000Z"))
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/12346"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (integer? (:lipas-id body)))
        (is (= 12346 (:lipas-id body)))
        (is (string? (:name body)))
        (is (integer? (-> body :type :type-code)))
        (is (string? (:event-date body)))
        (is (= 2010 (:construction-year body)))
        (is (map? (-> body :location :city)))))))

;;; Tests for different geometry types in v2 ;;;

(deftest geometry-types-test
  (testing "Different geometry types are correctly handled in v2"

    (testing "Point geometry site"
      (let [site (test-utils/make-point-site 80001 :name "Point Site" :type-code 1120)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/80001"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "Point" (-> body :location :geometries :features first :geometry :type)))))

    (testing "LineString geometry site"
      (let [site (test-utils/make-route-site 80002 :name "Route Site" :type-code 4405)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/80002"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "LineString" (-> body :location :geometries :features first :geometry :type)))))

    (testing "Polygon geometry site"
      (let [site (test-utils/make-area-site 80003 :name "Area Site" :type-code 103)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/80003"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "Polygon" (-> body :location :geometries :features first :geometry :type)))))))

;;; Tests for GET /v2/lois (list) ;;;

(deftest list-lois-empty-test
  (testing "GET /v2/lois returns 200 with empty items"
    (let [resp ((test-app) (mock/request :get "/v2/lois"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (map? body))
      (is (vector? (:items body)))
      (is (empty? (:items body)))
      (is (map? (:pagination body))))))

(deftest list-lois-with-results-test
  (testing "GET /v2/lois returns items with pagination"
    (doseq [_ (range 3)]
      (create-loi! (test-utils/gen-loi!)))

    (let [resp ((test-app) (mock/request :get "/v2/lois"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 3 (count (:items body))))
      (is (= 3 (-> body :pagination :total-items)))
      (is (= 1 (-> body :pagination :current-page))))))

(deftest list-lois-pagination-test
  (testing "GET /v2/lois pagination"
    (doseq [_ (range 15)]
      (create-loi! (test-utils/gen-loi!)))

    (testing "default page size returns up to 10"
      (let [resp ((test-app) (mock/request :get "/v2/lois"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (<= (count (:items body)) 10))
        (is (= 15 (-> body :pagination :total-items)))))

    (testing "custom page-size"
      (let [resp ((test-app) (mock/request :get "/v2/lois?page-size=5"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 5 (count (:items body))))))))

;;; Tests for GET /v2/lois/:loi-id ;;;

(deftest get-single-loi-test
  (testing "GET /v2/lois/:loi-id"

    (testing "returns 200 for existing LOI"
      (let [loi (test-utils/gen-loi!)
            _ (create-loi! loi)
            resp ((test-app) (mock/request :get (str "/v2/lois/" (:id loi))))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= (:id loi) (:id body)))
        (is (string? (:status body)))))))

;;; Tests for pagination structure ;;;

(deftest pagination-structure-test
  (testing "Pagination response has correct structure"
    (doseq [i (range 1 26)]
      (create-sports-site! (test-utils/make-point-site (+ 60000 i) :name (str "Pagination Structure " i))))

    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page=2&page-size=10"))
          body (parse-json-body resp)
          pagination (:pagination body)]
      (is (= 200 (:status resp)))
      (is (= 2 (:current-page pagination)))
      (is (= 10 (:page-size pagination)))
      (is (= 25 (:total-items pagination)))
      (is (= 3 (:total-pages pagination))))))

;;; Tests for invalid parameters ;;;

(deftest invalid-parameters-test
  (testing "Invalid page parameter returns 400"
    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page=0"))]
      (is (= 400 (:status resp)))))

  (testing "Invalid page-size parameter returns 400"
    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=0"))]
      (is (= 400 (:status resp))))

    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=101"))]
      (is (= 400 (:status resp))))))

;;; Health check ;;;

(deftest health-check-test
  (testing "GET /v2 returns 200 health status"
    (let [resp ((test-app) (mock/request :get "/v2"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= "healthy" (:status body))))))

(comment
  (clojure.test/run-tests 'lipas.backend.api.v2.handler-test)

  (clojure.test/run-test-var #'get-all-categories-test)
  (clojure.test/run-test-var #'get-category-by-type-code-test)
  (clojure.test/run-test-var #'list-sports-sites-empty-test)
  (clojure.test/run-test-var #'list-sports-sites-with-results-test)
  (clojure.test/run-test-var #'list-sports-sites-pagination-test)
  (clojure.test/run-test-var #'list-sports-sites-filter-by-type-codes-test)
  (clojure.test/run-test-var #'list-sports-sites-filter-by-city-codes-test)
  (clojure.test/run-test-var #'list-sports-sites-filter-by-statuses-test)
  (clojure.test/run-test-var #'get-single-sports-site-test)
  (clojure.test/run-test-var #'geometry-types-test)
  (clojure.test/run-test-var #'list-lois-empty-test)
  (clojure.test/run-test-var #'list-lois-with-results-test)
  (clojure.test/run-test-var #'list-lois-pagination-test)
  (clojure.test/run-test-var #'get-single-loi-test)
  (clojure.test/run-test-var #'pagination-structure-test)
  (clojure.test/run-test-var #'invalid-parameters-test)
  (clojure.test/run-test-var #'health-check-test))
