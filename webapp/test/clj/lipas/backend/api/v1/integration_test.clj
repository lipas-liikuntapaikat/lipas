(ns lipas.backend.api.v1.integration-test
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
   [lipas.backend.api.v1.transform :as legacy-transform]
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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/100001")]

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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/100002")]

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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/100003")]

        (is (= 200 status))
        (is (= 2510 (-> body :type :typeCode)))))))

(deftest route-geometry-pipeline-test
  (testing "LineString geometry sites (routes)"

    (testing "Single segment hiking route (type 4405)"
      (let [site (test-utils/make-hiking-route-site 200001
                                                    :name "Retkeilyreitti"
                                                    :route-length-km 8.5)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/v1/sports-places/200001")]

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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/200002")]

        (is (= 200 status))
        (is (= 4402 (-> body :type :typeCode)))))

    (testing "Multi-segment route (3 segments)"
      (let [site (test-utils/make-multi-segment-route 200003 3
                                                      :type-code 4405
                                                      :name "Moniosainen reitti"
                                                      :properties {:route-length-km 12.0})
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/v1/sports-places/200003")]

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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/300001")]

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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/300002")]

        (is (= 200 status))
        (is (= 1110 (-> body :type :typeCode)))))

    (testing "Outdoor recreation area (type 103)"
      (let [site (test-utils/make-outdoor-recreation-area-site 300003
                                                               :name "Ulkoilualue"
                                                               :free-use? true)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/v1/sports-places/300003")]

        (is (= 200 status))
        (is (= 103 (-> body :type :typeCode)))))

    (testing "Multi-polygon area (3 separate regions)"
      (let [site (test-utils/make-multi-polygon-area 300004 3
                                                     :type-code 103
                                                     :name "Moniosainen alue"
                                                     :properties {:free-use? true})
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/v1/sports-places/300004")]

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
          {:keys [status body]} (query-legacy-api "/v1/sports-places")]

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
          {:keys [status body]} (query-legacy-api "/v1/sports-places?fields=name&fields=type.typeCode")]

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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/500001")]

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
            {:keys [status body]} (query-legacy-api "/v1/sports-places?fields=name&fields=type.typeCode&fields=type.name&fields=location.city.cityCode")]

        (is (#{200 206} status))
        (is (vector? body) "List response must be a vector")
        (when (seq body)
          (let [item (first body)]
            (is (integer? (:sportsPlaceId item))) ; sportsPlaceId always included
            (is (string? (:name item)))))))

    (testing "/categories response structure"
      (let [{:keys [status body]} (query-legacy-api "/v1/categories")]
        (is (= 200 status))
        (is (vector? body))
        (when (seq body)
          (let [cat (first body)]
            (is (string? (:name cat)))
            (is (integer? (:typeCode cat)))
            (is (vector? (:subCategories cat)))))))

    (testing "/sports-place-types response structure"
      (let [{:keys [status body]} (query-legacy-api "/v1/sports-place-types")]
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
      (let [{:keys [status body]} (query-legacy-api "/v1/sports-place-types/1120")]
        (is (= 200 status))
        (is (= "Point" (:geometryType body))))

      ;; LineString type (route)
      (let [{:keys [status body]} (query-legacy-api "/v1/sports-place-types/4405")]
        (is (= 200 status))
        (is (= "LineString" (:geometryType body))))

      ;; Polygon type (area)
      (let [{:keys [status body]} (query-legacy-api "/v1/sports-place-types/103")]
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
        (let [{:keys [body]} (query-legacy-api "/v1/sports-places?typeCodes=4405&fields=type.typeCode")]
          (is (every? #(= 4405 (-> % :type :typeCode)) body)
              "Should only return routes")))

      ;; Filter by city code (need to request location.city.cityCode field)
      (testing "Filter by city code"
        (let [{:keys [body]} (query-legacy-api "/v1/sports-places?cityCodes=91&fields=location.city.cityCode")]
          (is (every? #(= 91 (-> % :location :city :cityCode)) body)
              "Should only return Helsinki sites")))

      ;; Filter by multiple type codes (need to request type.typeCode field)
      (testing "Filter by multiple type codes"
        (let [{:keys [body]} (query-legacy-api "/v1/sports-places?typeCodes=1120&typeCodes=103&fields=type.typeCode")]
          (is (every? #(#{1120 103} (-> % :type :typeCode)) body)
              "Should return point and polygon types"))))))

(deftest pagination-and-headers-test
  (testing "Pagination works correctly"
    ;; Create enough sites to trigger pagination
    (doseq [i (range 1 16)]
      (let [site (test-utils/make-point-site (+ 700000 i) :name (str "Pagination Test " i))]
        (save-and-index! site)))

    (testing "206 Partial Content when paginated"
      (let [{:keys [status headers]} (query-legacy-api "/v1/sports-places?pageSize=5")]
        (is (= 206 status) "Should return 206 when more results available")
        (is (some? (get headers "Link")) "Should have Link header")
        (is (some? (get headers "X-total-count")) "Should have X-total-count header")))

    (testing "Page parameter works"
      (let [page1 (query-legacy-api "/v1/sports-places?pageSize=5&page=1")
            page2 (query-legacy-api "/v1/sports-places?pageSize=5&page=2")
            ids1 (set (map :sportsPlaceId (:body page1)))
            ids2 (set (map :sportsPlaceId (:body page2)))]
        (is (empty? (clojure.set/intersection ids1 ids2))
            "Pages should have different items")))))

(defn query-legacy-api-with-headers
  "Queries the legacy API with custom headers and returns parsed response."
  [path headers]
  (let [req (-> (mock/request :get path)
                (assoc :headers headers))
        resp ((test-app) req)
        body (test-utils/<-json (:body resp))]
    {:status (:status resp)
     :headers (:headers resp)
     :body (if (sequential? body) (vec body) body)}))

(deftest link-header-base-path-test
  (testing "Link header uses correct base path based on entry point"
    ;; Create enough sites to trigger pagination
    (doseq [i (range 1 16)]
      (let [site (test-utils/make-point-site (+ 750000 i) :name (str "Link Path Test " i))]
        (save-and-index! site)))

    (testing "Default base path is /v1 when no X-Forwarded-Prefix header"
      (let [{:keys [status headers]} (query-legacy-api "/v1/sports-places?pageSize=5")]
        (is (= 206 status))
        (let [link-header (get headers "Link")]
          (is (some? link-header))
          (is (re-find #"/v1/sports-places" link-header)
              "Link header should use /v1 prefix"))))

    (testing "Uses /v1 prefix when X-Forwarded-Prefix is /v1 (api.lipas.fi)"
      (let [{:keys [status headers]} (query-legacy-api-with-headers
                                      "/v1/sports-places?pageSize=5"
                                      {"x-forwarded-prefix" "/v1"})]
        (is (= 206 status))
        (let [link-header (get headers "Link")]
          (is (some? link-header))
          (is (re-find #"/v1/sports-places" link-header)
              "Link header should use /v1 prefix"))))

    (testing "Uses /api prefix when X-Forwarded-Prefix is /api (lipas.cc.jyu.fi)"
      (let [{:keys [status headers]} (query-legacy-api-with-headers
                                      "/v1/sports-places?pageSize=5"
                                      {"x-forwarded-prefix" "/api"})]
        (is (= 206 status))
        (let [link-header (get headers "Link")]
          (is (some? link-header))
          (is (re-find #"/api/sports-places" link-header)
              "Link header should use /api prefix")
          (is (not (re-find #"/v1" link-header))
              "Link header should NOT contain /v1"))))

    (testing "Uses /v1 prefix when X-Forwarded-Prefix is /v1 (lipas.fi)"
      (let [{:keys [status headers]} (query-legacy-api-with-headers
                                      "/v1/sports-places?pageSize=5"
                                      {"x-forwarded-prefix" "/v1"})]
        (is (= 206 status))
        (let [link-header (get headers "Link")]
          (is (some? link-header))
          (is (re-find #"/v1/sports-places" link-header)
              "Link header should use /v1 prefix"))))))

(deftest type-specific-properties-test
  (testing "Type-specific properties are correctly transformed"

    (testing "Route properties (route-length-km, lit-route-length-km)"
      (let [site (test-utils/make-ski-track-site 800001
                                                 :route-length-km 10.5
                                                 :lit-route-length-km 3.5)
            _ (save-and-index! site)
            {:keys [status body]} (query-legacy-api "/v1/sports-places/800001")]
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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/800002")]
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
            {:keys [status body]} (query-legacy-api "/v1/sports-places/800003")]
        (is (= 200 status))
        (when-let [props (:properties body)]
          (doseq [k (keys props)]
            (is (not (re-find #"-" (name k)))
                (str "Property key should be camelCase: " k))))))))

;;; Tests for production comparison findings ;;;

(deftest geo-proximity-filter-test
  (testing "GET /v1/sports-places with geo proximity filter"
    ;; Create sites at known coordinates
    ;; Helsinki center: 60.1699, 24.9384
    ;; Site 1: Very close to center (should be found within 1km)
    (let [site1 (-> (test-utils/make-point-site 900001
                                                :name "Helsinki Center Site"
                                                :type-code 1120)
                    (assoc-in [:location :geometries :features 0 :geometry :coordinates]
                              [24.9385 60.1700]))
          ;; Site 2: Far from center (Espoo - should NOT be found within 1km of Helsinki center)
          site2 (-> (test-utils/make-point-site 900002
                                                :name "Espoo Site"
                                                :type-code 1120)
                    (assoc-in [:location :geometries :features 0 :geometry :coordinates]
                              [24.6559 60.2055]))]
      (doseq [site [site1 site2]]
        (save-and-index! site))

      (testing "filters by closeToLon, closeToLat, closeToDistanceKm"
        (let [{:keys [status body]} (query-legacy-api
                                     "/v1/sports-places?closeToLon=24.9384&closeToLat=60.1699&closeToDistanceKm=1")]
          (is (#{200 206} status) "Should return 200 or 206, not an error")
          (is (vector? body) "Response should be a vector")
          ;; The Helsinki center site should be found (within 1km)
          (when (seq body)
            (let [ids (set (map :sportsPlaceId body))]
              (is (contains? ids 900001) "Should find Helsinki center site within 1km")
              (is (not (contains? ids 900002)) "Should NOT find Espoo site within 1km of Helsinki"))))))))

(deftest english-translations-test
  (testing "English translations for admin/owner match production"
    ;; Create a site with known admin and owner
    (let [site (test-utils/make-point-site 910001
                                           :name "Translation Test Site"
                                           :type-code 1120
                                           :admin "city-technical-services"
                                           :owner "city")
          _ (save-and-index! site)
          {:keys [status body]} (query-legacy-api "/v1/sports-places/910001?lang=en")]

      (is (= 200 status))

      ;; Production uses "Municipality" instead of "City"
      (testing "admin translation uses 'Municipality' not 'City'"
        (is (= "Municipality / Technical services" (:admin body))
            (str "admin should be 'Municipality / Technical services', got: " (:admin body))))

      (testing "owner translation uses 'Municipality' not 'City'"
        (is (= "Municipality" (:owner body))
            (str "owner should be 'Municipality', got: " (:owner body)))))))

(deftest link-header-format-test
  (testing "Link header format matches production"
    ;; Create enough sites for pagination
    (doseq [i (range 1 16)]
      (let [site (test-utils/make-point-site (+ 920000 i) :name (str "Link Format Test " i))]
        (save-and-index! site)))

    (testing "Link header uses camelCase param names and omits empty values"
      (let [{:keys [headers]} (query-legacy-api "/v1/sports-places?pageSize=3&typeCodes=1120")]
        (let [link-header (get headers "Link")]
          (is (some? link-header) "Should have Link header")

          ;; Should use camelCase param names
          (is (re-find #"pageSize=" link-header)
              "Should use 'pageSize' (camelCase)")
          (is (not (re-find #"page-size=" link-header))
              "Should NOT use 'page-size' (kebab-case)")

          ;; Should NOT include empty params
          (is (not (re-find #"city-codes=" link-header))
              "Should NOT include empty city-codes param")
          (is (not (re-find #"modified-after=" link-header))
              "Should NOT include empty modified-after param")
          (is (not (re-find #"search-string=" link-header))
              "Should NOT include empty search-string param"))))))

(comment
  ;; Run all tests in this namespace
  (clojure.test/run-tests 'lipas.backend.api.v1.integration-test)

  ;; Run specific tests
  (clojure.test/run-test-var #'point-geometry-pipeline-test)
  (clojure.test/run-test-var #'route-geometry-pipeline-test)
  (clojure.test/run-test-var #'area-geometry-pipeline-test)
  (clojure.test/run-test-var #'legacy-transform-correctness-test)
  (clojure.test/run-test-var #'api-response-schema-compliance-test)
  (clojure.test/run-test-var #'filtering-across-geometry-types-test)
  (clojure.test/run-test-var #'pagination-and-headers-test)
  (clojure.test/run-test-var #'type-specific-properties-test))
