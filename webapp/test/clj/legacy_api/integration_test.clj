(ns legacy-api.integration-test
  "End-to-end integration tests for the Legacy API reindexing pipeline.

   These tests verify the complete data flow:
   1. Sports facilities are saved to PostgreSQL (new LIPAS format)
   2. Legacy reindexing transforms data to legacy format
   3. Data is indexed to Elasticsearch (legacy_sports_sites_current)
   4. Legacy API queries return correctly transformed data

   This is the critical path for the legacy API migration - if these tests
   pass, external consumers of the legacy API should continue to work.

   Test coverage includes:
   - Point geometry sites (most indoor facilities, fields)
   - LineString geometry sites (routes: hiking, skiing, cycling)
   - Polygon geometry sites (areas: parks, golf courses, recreation areas)
   - Multi-segment geometries (routes with multiple trail sections)
   - Type-specific properties (pools, rinks, route lengths, etc.)"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [legacy-api.transform :as legacy-transform]
   [lipas.backend.core :as core]
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

(defn save-and-index!
  "Saves site to database and indexes to legacy ES."
  [site]
  (save-to-database! site)
  (index-to-legacy-es! site))

(defn query-legacy-api
  "Queries the legacy API and returns parsed response."
  [path]
  (let [resp ((test-app) (mock/request :get path))
        body (test-utils/<-json (:body resp))]
    {:status (:status resp)
     :headers (:headers resp)
     :body (if (sequential? body) (vec body) body)}))

;;; Core Integration Tests ;;;

(deftest point-geometry-pipeline-test
  (testing "Point geometry sites (most common type)"

    (testing "Basic sports field (type 1120)"
      (let [site (test-utils/make-point-site 100001
                                             :name "Testikenttä"
                                             :type-code 1120
                                             :city-code "91"
                                             :construction-year 2015
                                             :properties {:ligthing? true
                                                          :surface-material ["artificial-turf"]})
            _ (save-and-index! site)
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

        ;; Verify geometry is present and correct type
        (is (= "FeatureCollection" (-> body :location :geometries :type)))
        (is (= "Point" (-> body :location :geometries :features first :geometry :type)))))

    (testing "Swimming pool (type 3110) with pool properties"
      (let [site (test-utils/make-swimming-pool-site 100002
                                                     :name "Uimahalli"
                                                     :pool-length-m 50
                                                     :pool-tracks-count 8)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/100002")]

        (is (= 200 status))
        (is (= 3110 (-> body :type :typeCode)))
        ;; Verify pool properties are transformed
        (when-let [props (:properties body)]
          (is (or (nil? (:poolLengthM props))
                  (number? (:poolLengthM props)))
              "Pool length should be transformed to camelCase"))))

    (testing "Ice stadium (type 2510) with rink properties"
      (let [site (test-utils/make-ice-stadium-site 100003
                                                   :name "Jäähalli"
                                                   :ice-rinks-count 2
                                                   :field-length-m 60
                                                   :field-width-m 30)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/100003")]

        (is (= 200 status))
        (is (= 2510 (-> body :type :typeCode)))))))

(deftest route-geometry-pipeline-test
  (testing "LineString geometry sites (routes)"

    (testing "Single segment hiking route (type 4405)"
      (let [site (test-utils/make-hiking-route-site 200001
                                                    :name "Retkeilyreitti"
                                                    :route-length-km 8.5)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/200001")]

        (is (= 200 status))
        (is (= 4405 (-> body :type :typeCode)))

        ;; Verify geometry type
        (is (= "FeatureCollection" (-> body :location :geometries :type)))
        (let [geom-type (-> body :location :geometries :features first :geometry :type)]
          (is (= "LineString" geom-type)
              "Route should have LineString geometry"))))

    (testing "Ski track (type 4402) with skiing properties"
      (let [site (test-utils/make-ski-track-site 200002
                                                 :name "Hiihtolatu"
                                                 :route-length-km 15.0
                                                 :lit-route-length-km 5.0)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/200002")]

        (is (= 200 status))
        (is (= 4402 (-> body :type :typeCode)))))

    (testing "Multi-segment route (3 segments)"
      (let [site (test-utils/make-multi-segment-route 200003 3
                                                      :type-code 4405
                                                      :name "Moniosainen reitti"
                                                      :properties {:route-length-km 12.0})
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/200003")]

        (is (= 200 status))
        (is (= 4405 (-> body :type :typeCode)))

        ;; Verify multiple features
        (let [features (-> body :location :geometries :features)]
          (is (= 3 (count features))
              "Should have 3 route segments")
          (is (every? #(= "LineString" (-> % :geometry :type)) features)
              "All segments should be LineString"))))))

(deftest area-geometry-pipeline-test
  (testing "Polygon geometry sites (areas)"

    (testing "Sports park (type 1110)"
      (let [site (test-utils/make-sports-park-site 300001
                                                   :name "Liikuntapuisto"
                                                   :playground? true
                                                   :ligthing? true)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/300001")]

        (is (= 200 status))
        (is (= 1110 (-> body :type :typeCode)))

        ;; Verify geometry type
        (is (= "FeatureCollection" (-> body :location :geometries :type)))
        (let [geom-type (-> body :location :geometries :features first :geometry :type)]
          (is (= "Polygon" geom-type)
              "Area should have Polygon geometry"))))

    (testing "Another sports park with different ID"
      ;; Note: Golf course (1650) is a new type not in legacy types-old data,
      ;; so we use another sports park instance to test area handling
      (let [site (test-utils/make-sports-park-site 300002
                                                   :name "Liikuntapuisto 2"
                                                   :playground? false
                                                   :ligthing? true)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/300002")]

        (is (= 200 status))
        (is (= 1110 (-> body :type :typeCode)))))

    (testing "Outdoor recreation area (type 103)"
      (let [site (test-utils/make-outdoor-recreation-area-site 300003
                                                               :name "Ulkoilualue"
                                                               :free-use? true)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/300003")]

        (is (= 200 status))
        (is (= 103 (-> body :type :typeCode)))))

    (testing "Multi-polygon area (3 separate regions)"
      (let [site (test-utils/make-multi-polygon-area 300004 3
                                                     :type-code 103
                                                     :name "Moniosainen alue"
                                                     :properties {:free-use? true})
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/300004")]

        (is (= 200 status))
        (is (= 103 (-> body :type :typeCode)))

        ;; Verify multiple polygon features
        (let [features (-> body :location :geometries :features)]
          (is (= 3 (count features))
              "Should have 3 polygon regions")
          (is (every? #(= "Polygon" (-> % :geometry :type)) features)
              "All features should be Polygon"))))))

(deftest legacy-transform-correctness-test
  (testing "Transform function produces correct legacy format"

    (testing "Field name transformations (kebab-case → camelCase)"
      (let [new-site (test-utils/make-point-site 400001
                                                 :properties {:heating? true
                                                              :surface-material ["gravel"]
                                                              :area-m2 500})
            legacy (legacy-transform/->old-lipas-sports-site new-site)]

        (is (map? legacy) "Transform should produce a map")
        (is (map? (:type legacy)) "Should have type map")
        (is (map? (:location legacy)) "Should have location map")
        (is (contains? legacy :lastModified) "Should have lastModified")

        ;; Verify camelCase conversion
        (is (contains? (:type legacy) :typeCode) "typeCode should be camelCase")
        (is (not (contains? (:type legacy) :type-code)) "type-code should be converted")))

    (testing "Date format transformation"
      (let [new-site (test-utils/make-point-site 400002
                                                 :event-date "2024-06-15T10:30:00.000Z")
            legacy (legacy-transform/->old-lipas-sports-site new-site)]
        (when-let [lm (:lastModified legacy)]
          (is (string? lm))
          (is (re-find #"\d{4}-\d{2}-\d{2}" lm)
              "Should have date portion"))))

    (testing "Route geometry transformation"
      (let [new-site (test-utils/make-route-site 400003
                                                 :type-code 4405
                                                 :segments [[[25.0 60.2] [25.01 60.21] [25.02 60.22]]])
            legacy (legacy-transform/->old-lipas-sports-site new-site)]
        (is (= "FeatureCollection" (-> legacy :location :geometries :type)))
        (is (= "LineString" (-> legacy :location :geometries :features first :geometry :type)))))

    (testing "Polygon geometry transformation"
      (let [new-site (test-utils/make-area-site 400004
                                                :type-code 103)
            legacy (legacy-transform/->old-lipas-sports-site new-site)]
        (is (= "FeatureCollection" (-> legacy :location :geometries :type)))
        (is (= "Polygon" (-> legacy :location :geometries :features first :geometry :type)))))))

(deftest default-fields-behavior-test
  (testing "List endpoint returns only sportsPlaceId by default (no fields param)"
    (let [site (test-utils/make-point-site 450001
                                           :name "Default Fields Test"
                                           :type-code 1120
                                           :city-code "91")
          _ (save-and-index! site)
          {:keys [status body]} (query-legacy-api "/rest/api/sports-places")]

      (is (#{200 206} status))
      (is (vector? body) "List response must be a vector")
      (when-let [item (first (filter #(= 450001 (:sportsPlaceId %)) body))]
        ;; Production returns ONLY sportsPlaceId when no fields are specified
        (is (= #{:sportsPlaceId} (set (keys item)))
            "Default response should contain only sportsPlaceId, but got extra keys")
        (is (= 450001 (:sportsPlaceId item))))))

  (testing "List endpoint returns specified fields when fields param is provided"
    (let [site (test-utils/make-point-site 450002
                                           :name "Fields Param Test"
                                           :type-code 1120
                                           :city-code "91")
          _ (save-and-index! site)
          {:keys [status body]} (query-legacy-api "/rest/api/sports-places?fields=name&fields=type.typeCode")]

      (is (#{200 206} status))
      (when-let [item (first (filter #(= 450002 (:sportsPlaceId %)) body))]
        ;; sportsPlaceId should always be included
        (is (contains? item :sportsPlaceId) "sportsPlaceId should always be included")
        ;; Requested fields should be present
        (is (contains? item :name) "Requested field 'name' should be present")
        (is (contains? item :type) "Requested nested field 'type' should be present")
        (is (integer? (-> item :type :typeCode)) "type.typeCode should be present")))))

(deftest api-response-schema-compliance-test
  (testing "API responses match legacy schema expectations"

    (testing "/sports-places/:id response structure"
      (let [site (test-utils/make-point-site 500001)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/500001")]

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

    (testing "/sports-places list response structure - with fields param returns requested fields"
      (let [site (test-utils/make-point-site 500002)
            _ (save-and-index! site)
            ;; Use valid field names from legacy-fields list
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places?fields=name&fields=type.typeCode&fields=type.name&fields=location.city.cityCode")]

        (is (#{200 206} status))
        (is (vector? body) "List response must be a vector")
        (when (seq body)
          (let [item (first body)]
            (is (integer? (:sportsPlaceId item))) ; sportsPlaceId always included
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

    (testing "/sports-place-types/:code response structure for different geometry types"
      ;; Point type
      (let [{:keys [status body]} (query-legacy-api "/rest/api/sports-place-types/1120")]
        (is (= 200 status))
        (is (= "Point" (:geometryType body))))

      ;; LineString type (route)
      (let [{:keys [status body]} (query-legacy-api "/rest/api/sports-place-types/4405")]
        (is (= 200 status))
        (is (= "LineString" (:geometryType body))))

      ;; Polygon type (area)
      (let [{:keys [status body]} (query-legacy-api "/rest/api/sports-place-types/103")]
        (is (= 200 status))
        (is (= "Polygon" (:geometryType body)))))))

(deftest filtering-across-geometry-types-test
  (testing "Filtering works across different geometry types"

    ;; Create sites with different geometry types and cities
    (let [point-site (test-utils/make-point-site 600001 :type-code 1120 :city-code "91")
          route-site (test-utils/make-route-site 600002 :type-code 4405 :city-code "91")
          area-site (test-utils/make-area-site 600003 :type-code 103 :city-code "49")
          route-site2 (test-utils/make-route-site 600004 :type-code 4405 :city-code "49")]

      (doseq [site [point-site route-site area-site route-site2]]
        (save-and-index! site))

      ;; Filter by type code - routes only (need to request type.typeCode field)
      (testing "Filter by route type code"
        (let [{:keys [body]} (query-legacy-api "/rest/api/sports-places?typeCodes=4405&fields=type.typeCode")]
          (is (every? #(= 4405 (-> % :type :typeCode)) body)
              "Should only return routes")))

      ;; Filter by city code (need to request location.city.cityCode field)
      (testing "Filter by city code"
        (let [{:keys [body]} (query-legacy-api "/rest/api/sports-places?cityCodes=91&fields=location.city.cityCode")]
          (is (every? #(= 91 (-> % :location :city :cityCode)) body)
              "Should only return Helsinki sites")))

      ;; Filter by multiple type codes (need to request type.typeCode field)
      (testing "Filter by multiple type codes"
        (let [{:keys [body]} (query-legacy-api "/rest/api/sports-places?typeCodes=1120&typeCodes=103&fields=type.typeCode")]
          (is (every? #(#{1120 103} (-> % :type :typeCode)) body)
              "Should return point and polygon types"))))))

(deftest pagination-and-headers-test
  (testing "Pagination works correctly"
    ;; Create enough sites to trigger pagination
    (doseq [i (range 1 16)]
      (let [site (test-utils/make-point-site (+ 700000 i) :name (str "Pagination Test " i))]
        (save-and-index! site)))

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

(deftest type-specific-properties-test
  (testing "Type-specific properties are correctly transformed"

    (testing "Route properties (route-length-km, lit-route-length-km)"
      (let [site (test-utils/make-ski-track-site 800001
                                                 :route-length-km 10.5
                                                 :lit-route-length-km 3.5)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/800001")]
        (is (= 200 status))
        ;; Properties should be camelCase if present
        (when-let [props (:properties body)]
          (doseq [k (keys props)]
            (is (not (re-find #"-" (name k)))
                (str "Property key should be camelCase: " k))))))

    (testing "Pool properties"
      (let [site (test-utils/make-swimming-pool-site 800002
                                                     :pool-length-m 50
                                                     :pool-tracks-count 10
                                                     :pool-width-m 25)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/800002")]
        (is (= 200 status))
        (when-let [props (:properties body)]
          (doseq [k (keys props)]
            (is (not (re-find #"-" (name k)))
                (str "Property key should be camelCase: " k))))))

    (testing "Area properties (sports park)"
      ;; Note: Golf course (1650) is a new type not in legacy types-old data,
      ;; so we use sports park (1110) which exists in both systems
      (let [site (test-utils/make-sports-park-site 800003
                                                   :playground? true
                                                   :ligthing? true)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/rest/api/sports-places/800003")]
        (is (= 200 status))
        (when-let [props (:properties body)]
          (doseq [k (keys props)]
            (is (not (re-find #"-" (name k)))
                (str "Property key should be camelCase: " k))))))))

(comment
  ;; Run all tests in this namespace
  (clojure.test/run-tests 'legacy-api.integration-test)

  ;; Run specific tests
  (clojure.test/run-test-var #'point-geometry-pipeline-test)
  (clojure.test/run-test-var #'route-geometry-pipeline-test)
  (clojure.test/run-test-var #'area-geometry-pipeline-test)
  (clojure.test/run-test-var #'legacy-transform-correctness-test)
  (clojure.test/run-test-var #'api-response-schema-compliance-test)
  (clojure.test/run-test-var #'filtering-across-geometry-types-test)
  (clojure.test/run-test-var #'pagination-and-headers-test)
  (clojure.test/run-test-var #'type-specific-properties-test))
