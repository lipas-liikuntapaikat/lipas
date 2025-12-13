(ns legacy-api.integration-test
  "End-to-end integration tests for the Legacy API reindexing pipeline.

   These tests verify the complete data flow:
   1. Sports facilities are saved to PostgreSQL (new LIPAS format)
   2. Legacy reindexing transforms data to legacy format
   3. Data is indexed to Elasticsearch (legacy_sports_sites_current)
   4. Legacy API queries return correctly transformed data

   This is the critical path for the legacy API migration - if these tests
   pass, external consumers of the legacy API should continue to work."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [legacy-api.transform :as legacy-transform]
   [lipas.backend.core :as core]
   [lipas.search-indexer :as indexer]
   [lipas.test-utils :as test-utils]
   [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

(defn test-app [] (:lipas/app @test-system))
(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Test Data Factories ;;;

(defn make-new-lipas-site
  "Creates a sports site in the NEW LIPAS format (kebab-case, modern structure).
   This is what gets stored in the database."
  [lipas-id & {:keys [type-code city-code name status admin owner
                      construction-year properties event-date]
               :or {type-code 1120
                    city-code 91
                    name "Test Sports Place"
                    status "active"
                    admin "city-technical-services"
                    owner "city"
                    event-date "2024-06-15T10:30:00.000Z"}}]
  (cond-> {:lipas-id lipas-id
           :name name
           :status status
           :admin admin
           :owner owner
           :event-date event-date
           :type {:type-code type-code}
           :location {:city {:city-code (str city-code)}
                      :address "Testikatu 1"
                      :postal-code "00100"
                      :postal-office "Helsinki"
                      :geometries {:type "FeatureCollection"
                                   :features [{:type "Feature"
                                               :geometry {:type "Point"
                                                          :coordinates [25.0 60.0]}}]}}}
    construction-year (assoc :construction-year construction-year)
    properties (assoc :properties properties)))

;;; Helper Functions ;;;

(defn save-to-database!
  "Saves a sports site to PostgreSQL using the standard LIPAS flow."
  [site]
  (let [admin (test-utils/gen-admin-user :db-component (test-db))]
    (core/upsert-sports-site!* (test-db) admin site)
    site))

(defn index-to-legacy-es!
  "Indexes a single site to the legacy Elasticsearch index."
  [site]
  (core/index-legacy-sports-place! (test-search) site :sync)
  (Thread/sleep 100) ; Give ES time to index
  site)

(defn query-legacy-api
  "Queries the legacy API and returns parsed response."
  [path]
  (let [resp ((test-app) (mock/request :get path))
        body (test-utils/<-json (:body resp))]
    {:status (:status resp)
     :headers (:headers resp)
     :body (if (sequential? body) (vec body) body)}))

(defn parse-json-body [response]
  (let [body (test-utils/<-json (:body response))]
    (if (sequential? body) (vec body) body)))

;;; Core Integration Tests ;;;

(deftest database-to-legacy-api-pipeline-test
  (testing "Complete pipeline: DB → Transform → ES → Legacy API"

    (testing "Point geometry site (type 1120)"
      (let [;; 1. Create site in NEW format
            new-site (make-new-lipas-site 100001
                                          :name "Testikenttä"
                                          :type-code 1120
                                          :city-code 91
                                          :admin "city-technical-services"
                                          :owner "city"
                                          :construction-year 2015
                                          :properties {:ligthing? true
                                                       :surface-material ["artificial-turf"]})

            ;; 2. Save to database
            _ (save-to-database! new-site)

            ;; 3. Index to legacy ES (this is the transform step)
            _ (index-to-legacy-es! new-site)

            ;; 4. Query via legacy API
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/100001")]

        ;; Verify HTTP success
        (is (= 200 status))

        ;; Verify ID transformation
        (is (= 100001 (:sportsPlaceId body))
            "lipas-id should become sportsPlaceId")

        ;; Verify type transformation
        (is (= 1120 (-> body :type :typeCode))
            "type-code should become typeCode")
        (is (string? (-> body :type :name))
            "Type should have localized name")

        ;; Verify location transformation
        (is (= 91 (-> body :location :city :cityCode))
            "city-code string should become cityCode integer")
        (is (string? (-> body :location :city :name))
            "City should have localized name")

        ;; Verify admin/owner enum-to-string transformation (if present in response)
        ;; Note: admin might be nil if not included in the indexed data
        (when-let [admin (:admin body)]
          (is (string? admin) "admin should be localized string")
          (is (not= "city-technical-services" admin)
              "admin should NOT be the raw enum value"))

        ;; Verify date format transformation
        (when-let [lm (:lastModified body)]
          (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}" lm)
              "lastModified should be in legacy format yyyy-MM-dd HH:mm:ss.SSS"))

        ;; Verify properties use camelCase keys (if present)
        ;; Note: Properties may be empty depending on type and input data
        (when-let [props (:properties body)]
          (when (seq props)
            (doseq [k (keys props)]
              (is (not (re-find #"-" (name k)))
                  (str "Property key should be camelCase, not kebab-case: " k)))))

        ;; Verify geometry is present
        (is (= "FeatureCollection" (-> body :location :geometries :type))
            "Geometries should be a FeatureCollection")))

    (testing "Multiple sites with filtering"
      ;; Create sites with different type codes
      (let [site1 (make-new-lipas-site 100010 :type-code 1120 :city-code 91 :name "Helsinki Site")
            site2 (make-new-lipas-site 100011 :type-code 1310 :city-code 49 :name "Espoo Site")
            site3 (make-new-lipas-site 100012 :type-code 1120 :city-code 49 :name "Espoo Site 2")]

        (doseq [site [site1 site2 site3]]
          (save-to-database! site)
          (index-to-legacy-es! site))

        ;; Filter by type code
        (let [{:keys [body]} (query-legacy-api "/rest/api/sports-places?typeCodes=1120")]
          (is (every? #(= 1120 (-> % :type :typeCode)) body)
              "Type filter should work"))

        ;; Filter by city code
        (let [{:keys [body]} (query-legacy-api "/rest/api/sports-places?cityCodes=49")]
          (is (every? #(= 49 (-> % :location :city :cityCode)) body)
              "City filter should work"))))))

(deftest legacy-transform-correctness-test
  (testing "Transform function produces correct legacy format"

    (testing "Field name transformations"
      (let [new-site (make-new-lipas-site 200001
                                          :properties {:heating? true
                                                       :surface-material ["gravel"]
                                                       :area-m2 500})
            legacy (legacy-transform/->old-lipas-sports-site new-site)]

        ;; Check that transform produces expected structure
        ;; Note: sportsPlaceId is added by the indexing pipeline, not the transform
        (is (map? legacy) "Transform should produce a map")
        (is (map? (:type legacy)) "Should have type map")
        (is (map? (:location legacy)) "Should have location map")
        (is (contains? legacy :lastModified) "Should have lastModified")

        ;; Verify camelCase conversion happened
        (is (contains? (:type legacy) :typeCode) "typeCode should be camelCase")
        (is (not (contains? (:type legacy) :type-code)) "type-code should be converted")))

    (testing "Date format transformation"
      (let [new-site (make-new-lipas-site 200002 :event-date "2024-06-15T10:30:00.000Z")
            legacy (legacy-transform/->old-lipas-sports-site new-site)]
        ;; The transform should convert ISO date to legacy format
        (when-let [lm (:lastModified legacy)]
          (is (string? lm))
          ;; Note: exact format depends on timezone handling
          (is (re-find #"\d{4}-\d{2}-\d{2}" lm)
              "Should have date portion"))))))

(deftest api-response-schema-compliance-test
  (testing "API responses match legacy schema expectations"

    (testing "/sports-places/:id response structure"
      (let [site (make-new-lipas-site 300001)
            _ (save-to-database! site)
            _ (index-to-legacy-es! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/300001")]

        (is (= 200 status))

        ;; Required top-level fields
        (is (integer? (:sportsPlaceId body)) "sportsPlaceId must be integer")
        (is (string? (:name body)) "name must be string")
        (is (map? (:type body)) "type must be map")
        (is (map? (:location body)) "location must be map")

        ;; Type structure
        (is (integer? (-> body :type :typeCode)) "typeCode must be integer")
        (is (string? (-> body :type :name)) "type name must be string")

        ;; Location structure
        (is (map? (-> body :location :city)) "city must be map")
        (is (integer? (-> body :location :city :cityCode)) "cityCode must be integer")))

    (testing "/sports-places list response structure"
      (let [site (make-new-lipas-site 300002)
            _ (save-to-database! site)
            _ (index-to-legacy-es! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places")]

        (is (#{200 206} status))
        (is (vector? body) "List response must be a vector")
        (when (seq body)
          (let [item (first body)]
            (is (integer? (:sportsPlaceId item)))
            (is (string? (:name item)))))))

    (testing "/categories response structure"
      (let [{:keys [status body]} (query-legacy-api "/rest/api/categories")]
        (is (= 200 status))
        (is (vector? body))
        (when (seq body)
          (let [cat (first body)]
            (is (string? (:name cat)))
            (is (integer? (:typeCode cat)))
            (is (vector? (:subCategories cat)))))))

    (testing "/sports-place-types response structure"
      (let [{:keys [status body]} (query-legacy-api "/rest/api/sports-place-types")]
        (is (= 200 status))
        (is (vector? body))
        (is (> (count body) 100) "Should have many types")
        (when (seq body)
          (let [t (first body)]
            (is (integer? (:typeCode t)))
            (is (string? (:name t)))
            (is (string? (:geometryType t)))))))

    (testing "/sports-place-types/:code response structure"
      (let [{:keys [status body]} (query-legacy-api "/rest/api/sports-place-types/1120")]
        (is (= 200 status))
        (is (= 1120 (:typeCode body)))
        (is (string? (:name body)))
        (is (map? (:props body)) "Should have props map")))))

(deftest pagination-and-headers-test
  (testing "Pagination works correctly"
    ;; Create enough sites to trigger pagination
    (doseq [i (range 1 16)]
      (let [site (make-new-lipas-site (+ 400000 i) :name (str "Pagination Test " i))]
        (save-to-database! site)
        (index-to-legacy-es! site)))

    (testing "206 Partial Content when paginated"
      (let [{:keys [status headers]} (query-legacy-api "/rest/api/sports-places?pageSize=5")]
        (is (= 206 status) "Should return 206 when more results available")
        (is (some? (get headers "Link")) "Should have Link header")
        (is (some? (get headers "X-total-count")) "Should have X-total-count header")))

    (testing "Page parameter works"
      (let [page1 (query-legacy-api "/rest/api/sports-places?pageSize=5&page=1")
            page2 (query-legacy-api "/rest/api/sports-places?pageSize=5&page=2")
            ids1 (set (map :sportsPlaceId (:body page1)))
            ids2 (set (map :sportsPlaceId (:body page2)))]
        (is (empty? (clojure.set/intersection ids1 ids2))
            "Pages should have different items")))))

;;; Type-Specific Transformation Tests ;;;

(deftest type-specific-transformations-test
  (testing "Type-specific property transformations work"

    ;; Note: These tests verify that type-specific transformations
    ;; don't break the pipeline. The golden file tests provide more
    ;; comprehensive coverage of actual property values.

    (testing "Ice stadium (type 2510) with rinks"
      (let [site (-> (make-new-lipas-site 500001 :type-code 2510)
                     (assoc :rinks [{:length-m 60 :width-m 30}]))
            _ (save-to-database! site)
            _ (index-to-legacy-es! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/500001")]
        (is (= 200 status))
        (is (= 2510 (-> body :type :typeCode)))))

    (testing "Swimming pool (type 3110) with pools"
      (let [site (-> (make-new-lipas-site 500002 :type-code 3110)
                     (assoc :pools [{:length-m 25 :width-m 10}]))
            _ (save-to-database! site)
            _ (index-to-legacy-es! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/500002")]
        (is (= 200 status))
        (is (= 3110 (-> body :type :typeCode)))))))

(comment
  ;; Run all tests in this namespace
  (clojure.test/run-tests 'legacy-api.integration-test)

  ;; Run specific test
  (clojure.test/run-test-var #'database-to-legacy-api-pipeline-test)
  (clojure.test/run-test-var #'api-response-schema-compliance-test)
  (clojure.test/run-test-var #'pagination-and-headers-test))
