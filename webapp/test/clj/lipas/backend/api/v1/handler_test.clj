(ns lipas.backend.api.v1.handler-test
  "Integration tests for the Legacy API (V1).

   These tests verify that:
   1. Each endpoint returns correct HTTP status codes
   2. The legacy indexer correctly transforms new LIPAS data to legacy format
   3. Response structure matches the legacy API contract
   4. Query parameters work as expected (filtering, pagination, etc.)"
  (:require
   [clojure.string :as str]
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

(defn create-sports-site!
  "Creates a sports site in the database and indexes it to both regular and legacy ES indices."
  [site]
  (let [admin (create-admin-user)]
    (core/upsert-sports-site!* (test-db) admin site)
    (core/index! (test-search) site :sync)
    (core/index-legacy-sports-place! (test-search) site :sync)
    ;; Give ES a moment to index
    (Thread/sleep 100)
    site))

;;; Response parsing helpers ;;;

(defn parse-json-body [response]
  (let [body (test-utils/<-json (:body response))]
    ;; Ensure collections are vectors for consistent testing
    (cond
      (sequential? body) (vec body)
      :else body)))

;;; Tests for GET /v1/sports-places/:id ;;;

(deftest get-single-sports-place-test
  (testing "GET /v1/sports-places/:id"

    (testing "returns 200 for existing sports place"
      (let [site (test-utils/make-point-site 12345 :name "Test Lähiliikuntapaikka")
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v1/sports-places/12345"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 12345 (:sportsPlaceId body)))
        (is (string? (:name body)))
        (is (map? (:type body)))
        (is (= 1120 (-> body :type :typeCode)))
        (is (map? (:location body)))))

    (testing "returns 404 for non-existing sports place"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places/999999"))]
        (is (= 404 (:status resp)))))

    (testing "returns correct legacy field format"
      (let [site (-> (test-utils/make-point-site 12346
                                                 :name "Test Football Field"
                                                 :type-code 1310)
                     (assoc :construction-year 2010
                            :event-date "2024-06-15T10:30:00.000Z"))
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v1/sports-places/12346"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (integer? (:sportsPlaceId body)))
        (is (= 12346 (:sportsPlaceId body)))
        (is (string? (:name body)))
        (is (integer? (-> body :type :typeCode)))
        (is (string? (-> body :type :name)))
        ;; lastModified should be in legacy format "yyyy-MM-dd HH:mm:ss.SSS"
        (when-let [lm (:lastModified body)]
          (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}" lm)
              "lastModified should be in legacy format"))
        ;; Location should have cityCode as integer
        (when-let [city (-> body :location :city)]
          (is (integer? (:cityCode city))))))))

(deftest get-sports-place-with-lang-test
  (testing "GET /v1/sports-places/:id with lang parameter"
    (let [site (test-utils/make-point-site 12347 :name "Suomenkielinen Nimi")
          _ (create-sports-site! site)]

      (testing "fi language (default)"
        (let [resp ((test-app) (mock/request :get "/v1/sports-places/12347?lang=fi"))
              body (parse-json-body resp)]
          (is (= 200 (:status resp)))
          (is (string? (:name body)))))

      (testing "en language"
        (let [resp ((test-app) (mock/request :get "/v1/sports-places/12347?lang=en"))
              body (parse-json-body resp)]
          (is (= 200 (:status resp)))
          (is (some? body))))

      (testing "se language"
        (let [resp ((test-app) (mock/request :get "/v1/sports-places/12347?lang=se"))
              body (parse-json-body resp)]
          (is (= 200 (:status resp)))
          (is (some? body)))))))

;;; Tests for GET /v1/sports-places (list) ;;;

(deftest list-sports-places-empty-test
  (testing "GET /v1/sports-places returns 200 with empty result"
    (let [resp ((test-app) (-> (mock/request :get "/v1/sports-places")
                               (mock/content-type "application/json")))]
      (is (= 200 (:status resp)))
      (let [body (parse-json-body resp)]
        (is (vector? body))
        (is (empty? body))))))

(deftest list-sports-places-with-results-test
  (testing "GET /v1/sports-places returns results when data exists"
    (doseq [i (range 1 4)]
      (create-sports-site! (test-utils/make-point-site (+ 20000 i) :name (str "Test Site " i))))

    (testing "default response contains only sportsPlaceId"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (vector? body))
        (is (= 3 (count body)))
        (is (every? :sportsPlaceId body))
        (is (every? #(= #{:sportsPlaceId} (set (keys %))) body)
            "Default response should only contain sportsPlaceId")))

    (testing "response with requested fields includes name and type"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?fields=name&fields=type.typeCode"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (every? :sportsPlaceId body))
        (is (every? :name body))
        (is (every? :type body))))))

(defn parse-link-header
  "Parse a Link header string into a map of rel->url"
  [link-header]
  (when link-header
    (->> (str/split link-header #",\s*")
         (map (fn [link]
                (let [[url rel] (str/split link #";\s*")]
                  (when (and url rel)
                    (let [url (-> url (str/replace #"^<" "") (str/replace #">$" ""))
                          rel (-> rel (str/replace #"rel=\"" "") (str/replace #"\"$" ""))]
                      [rel url])))))
         (remove nil?)
         (into {}))))

(defn extract-page-param
  "Extract the page parameter from a URL"
  [url]
  (when url
    (when-let [match (re-find #"[?&]page=(\d+)" url)]
      (Integer/parseInt (second match)))))

(deftest list-sports-places-pagination-test
  (testing "GET /v1/sports-places pagination"
    (doseq [i (range 1 16)]
      (create-sports-site! (test-utils/make-point-site (+ 30000 i) :name (str "Pagination Site " i))))

    (testing "default page size returns up to 10"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (<= (count body) 10))))

    (testing "custom pageSize"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?pageSize=5"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (= 5 (count body)))))

    (testing "page parameter"
      (let [resp1 ((test-app) (mock/request :get "/v1/sports-places?pageSize=5&page=1"))
            resp2 ((test-app) (mock/request :get "/v1/sports-places?pageSize=5&page=2"))
            body1 (parse-json-body resp1)
            body2 (parse-json-body resp2)]
        (is (#{200 206} (:status resp1)))
        (is (#{200 206} (:status resp2)))
        (let [ids1 (set (map :sportsPlaceId body1))
              ids2 (set (map :sportsPlaceId body2))]
          (is (empty? (clojure.set/intersection ids1 ids2))
              "Page 1 and 2 should have different items"))))

    (testing "206 Partial Content when more results available"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?pageSize=5"))]
        (is (= 206 (:status resp)) "Should return 206 when pagination is needed")
        (is (some? (get-in resp [:headers "Link"])))))

    (testing "Link header has correct page numbers"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?pageSize=5&page=1"))
            link-header (get-in resp [:headers "Link"])
            links (parse-link-header link-header)]
        (is (some? link-header) "Link header should be present")
        (is (= 1 (extract-page-param (get links "first"))) "first should be page=1")
        (is (= 2 (extract-page-param (get links "next"))) "next should be page=2 (not page=1)")
        (is (= 1 (extract-page-param (get links "prev"))) "prev should be page=1 (can't go below 1)")
        (is (= 3 (extract-page-param (get links "last"))) "last should be page=3 (15 items / 5 per page)")))))

(deftest list-sports-places-filter-by-type-codes-test
  (testing "GET /v1/sports-places filter by typeCodes"
    (create-sports-site! (test-utils/make-point-site 40001 :name "Type 1120 Site" :type-code 1120))
    (create-sports-site! (test-utils/make-point-site 40002 :name "Type 1310 Site" :type-code 1310))

    (testing "single typeCode filter"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?typeCodes=1120&fields=type.typeCode"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? #(= 1120 (-> % :type :typeCode)) body))))

    (testing "multiple typeCodes filter"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?typeCodes=1120&typeCodes=1310&fields=type.typeCode"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (= 2 (count body)))
        (is (= #{1120 1310} (set (map #(-> % :type :typeCode) body))))))))

(deftest list-sports-places-filter-by-city-codes-test
  (testing "GET /v1/sports-places filter by cityCodes"
    (create-sports-site! (test-utils/make-point-site 50001 :name "Helsinki Site" :city-code "91"))
    (create-sports-site! (test-utils/make-point-site 50002 :name "Espoo Site" :city-code "49"))

    (testing "single cityCode filter"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?cityCodes=91&fields=location.city.cityCode"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? #(= 91 (-> % :location :city :cityCode)) body))))

    (testing "multiple cityCodes filter"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?cityCodes=91&cityCodes=49"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (= 2 (count body)))))))

(deftest list-sports-places-search-string-test
  (testing "GET /v1/sports-places with searchString"
    (create-sports-site! (test-utils/make-point-site 60001 :name "Unique Searchable Name XYZ123"))

    (let [resp ((test-app) (mock/request :get "/v1/sports-places?searchString=XYZ123"))
          body (parse-json-body resp)]
      (is (#{200 206} (:status resp)))
      (is (= 1 (count body)))
      (is (= 60001 (-> body first :sportsPlaceId))))))

(deftest list-sports-places-search-string-relevance-test
  (testing "GET /v1/sports-places searchString prioritizes name matches (production behavior)"
    ;; Create a site with "uimahalli" in the name - should rank highest
    (create-sports-site! (test-utils/make-point-site 61001 :name "Kaupungin uimahalli"))

    ;; Create a site with "uimahalli" NOT in the name but in marketing-name
    ;; This should rank lower than name matches
    (let [site-with-marketing (-> (test-utils/make-point-site 61002 :name "Liikuntakeskus")
                                  (assoc :marketing-name "Uimahallin vieressä"))]
      (create-sports-site! site-with-marketing))

    ;; Create a site with "uimahalli" in www field (should rank even lower or not match)
    (let [site-with-www (-> (test-utils/make-point-site 61003 :name "Kuntosali")
                            (assoc :www "http://uimahalli.example.com"))]
      (create-sports-site! site-with-www))

    ;; Create a site with completely unrelated name
    (create-sports-site! (test-utils/make-point-site 61004 :name "Jalkapallokenttä"))

    (let [resp ((test-app) (mock/request :get "/v1/sports-places?searchString=uimahalli&fields=name"))
          body (parse-json-body resp)]
      (is (#{200 206} (:status resp)))

      ;; The site with "uimahalli" in the name should be first
      (testing "Site with search term in name appears first"
        (is (= 61001 (-> body first :sportsPlaceId))
            (str "First result should be site with 'uimahalli' in name. Got IDs: "
                 (mapv :sportsPlaceId body))))

      ;; Unrelated site should not appear at all
      (testing "Unrelated sites do not appear in results"
        (let [ids (set (map :sportsPlaceId body))]
          (is (not (contains? ids 61004))
              "Site without 'uimahalli' anywhere should not appear in results"))))))

(deftest list-sports-places-field-selection-test
  (testing "GET /v1/sports-places with fields parameter"
    (create-sports-site! (test-utils/make-point-site 70001 :name "Field Selection Test"))

    (testing "default response returns only sportsPlaceId"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (every? :sportsPlaceId body))
        (is (every? #(= #{:sportsPlaceId} (set (keys %))) body)
            "Default response should only contain sportsPlaceId")))

    (testing "selecting specific fields with repeated params (production format)"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?fields=name&fields=type.typeCode"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (every? :sportsPlaceId body) "sportsPlaceId is always included")
        (is (every? :name body) "requested field 'name' should be present")
        (is (every? #(-> % :type :typeCode) body) "requested field 'type.typeCode' should be present")))

    (testing "selecting specific fields with comma-separated format (also supported)"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?fields=name,type.typeCode"))
            body (parse-json-body resp)]
        (is (#{200 206} (:status resp)))
        (is (every? :sportsPlaceId body) "sportsPlaceId is always included")
        (is (every? :name body) "requested field 'name' should be present")
        (is (every? #(-> % :type :typeCode) body) "requested field 'type.typeCode' should be present")))))

;;; Tests for GET /v1/deleted-sports-places ;;;

(defn create-deleted-sports-site!
  "Creates a sports site with 'out-of-service-permanently' status (considered deleted in legacy API)."
  [site]
  (let [admin (create-admin-user)
        deleted-site (assoc site :status "out-of-service-permanently")]
    (core/upsert-sports-site!* (test-db) admin deleted-site)
    (core/index! (test-search) deleted-site :sync)
    ;; Give ES a moment to index
    (Thread/sleep 100)
    deleted-site))

(deftest deleted-sports-places-test
  (testing "GET /v1/deleted-sports-places"

    (testing "returns 200 with empty result when no deleted places"
      ;; Create a regular (published) site first to establish the index mappings
      ;; This ensures the status.keyword field exists for the query
      (let [site (test-utils/make-point-site 94999 :name "Published Site")]
        (create-sports-site! site))
      (let [resp ((test-app) (mock/request :get "/v1/deleted-sports-places?since=2020-01-01%2000:00:00.000"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? body))
        (is (empty? body))))

    (testing "returns deleted sports places with correct format"
      (let [;; Create a deleted sports site with a recent event date
            site (-> (test-utils/make-point-site 95001 :name "Deleted Site 1")
                     (assoc :event-date "2024-06-15T10:30:00.000Z"))
            _ (create-deleted-sports-site! site)
            resp ((test-app) (mock/request :get "/v1/deleted-sports-places?since=2024-01-01%2000:00:00.000"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? body))
        (is (= 1 (count body)))

        ;; Verify structure matches schema
        (let [deleted-item (first body)]
          (is (integer? (:sportsPlaceId deleted-item))
              "sportsPlaceId should be an integer")
          (is (= 95001 (:sportsPlaceId deleted-item))
              "sportsPlaceId should match the lipas-id")
          (is (string? (:deletedAt deleted-item))
              "deletedAt should be a string")
          ;; Verify date format is legacy format: "2024-06-15 13:30:00.000" (Europe/Helsinki)
          (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}" (:deletedAt deleted-item))
              "deletedAt should be in legacy format 'yyyy-MM-dd HH:mm:ss.SSS'"))))

    (testing "filters by since parameter"
      ;; Create two deleted sites with different dates
      (let [old-site (-> (test-utils/make-point-site 95002 :name "Old Deleted Site")
                         (assoc :event-date "2023-01-15T10:00:00.000Z"))
            new-site (-> (test-utils/make-point-site 95003 :name "New Deleted Site")
                         (assoc :event-date "2024-08-20T15:45:00.000Z"))
            _ (create-deleted-sports-site! old-site)
            _ (create-deleted-sports-site! new-site)
            ;; Query with since=2024-01-01 should only return the new site
            resp ((test-app) (mock/request :get "/v1/deleted-sports-places?since=2024-01-01%2000:00:00.000"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        ;; Should have the new site (95003) and the one from previous test (95001)
        ;; but NOT the old site (95002) from 2023
        (let [ids (set (map :sportsPlaceId body))]
          (is (contains? ids 95003) "Should include new deleted site")
          (is (not (contains? ids 95002)) "Should NOT include old deleted site (before since)"))))))

;;; Tests for GET /v1/categories ;;;

(deftest categories-endpoint-test
  (testing "GET /v1/categories"

    (testing "returns 200 with categories"
      (let [resp ((test-app) (mock/request :get "/v1/categories"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? body))
        (is (pos? (count body)))
        (doseq [cat body]
          (is (string? (:name cat)))
          (is (integer? (:typeCode cat)))
          (is (vector? (:subCategories cat))))))

    (testing "respects lang parameter"
      (doseq [lang ["fi" "en" "se"]]
        (let [resp ((test-app) (mock/request :get (str "/v1/categories?lang=" lang)))
              body (parse-json-body resp)]
          (is (= 200 (:status resp)))
          (is (vector? body)))))

    (testing "subCategories are populated (not empty)"
      (let [resp ((test-app) (mock/request :get "/v1/categories"))
            body (parse-json-body resp)
            ;; Find a category that should have subcategories (e.g., typeCode 0 or 1000)
            category-with-subcats (some #(when (seq (:subCategories %)) %) body)]
        (is (some? category-with-subcats) "At least one category should have subCategories")
        (when category-with-subcats
          (let [subcats (:subCategories category-with-subcats)]
            (is (pos? (count subcats)) "subCategories should not be empty")
            (doseq [subcat subcats]
              (is (integer? (:typeCode subcat)) "subCategory should have integer typeCode")
              (is (string? (:name subcat)) "subCategory should have string name")
              (is (vector? (:sportsPlaceTypes subcat)) "subCategory should have sportsPlaceTypes vector")
              (is (every? integer? (:sportsPlaceTypes subcat)) "sportsPlaceTypes should be integers"))))))))

;;; Tests for GET /v1/sports-place-types ;;;

(deftest sports-place-types-list-test
  (testing "GET /v1/sports-place-types"

    (testing "returns 200 with all types"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? body))
        (is (> (count body) 100) "Should have many types")
        (doseq [t body]
          (is (integer? (:typeCode t)))
          (is (string? (:name t)))
          (is (string? (:description t)))
          (is (#{"Point" "LineString" "Polygon"} (:geometryType t)))
          (is (integer? (:subCategory t))))))

    (testing "respects lang parameter"
      (doseq [lang ["fi" "en" "se"]]
        (let [resp ((test-app) (mock/request :get (str "/v1/sports-place-types?lang=" lang)))
              body (parse-json-body resp)]
          (is (= 200 (:status resp)))
          (is (vector? body)))))))

;;; Tests for GET /v1/sports-place-types/:code ;;;

(deftest sports-place-type-detail-test
  (testing "GET /v1/sports-place-types/:code"

    (testing "returns 200 for valid type code 1120 (Lähiliikuntapaikka)"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/1120"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1120 (:typeCode body)))
        (is (string? (:name body)))
        (is (string? (:description body)))
        (is (= "Point" (:geometryType body)))
        (is (integer? (:subCategory body)))
        (is (map? (:properties body)) "Should have properties map (key: 'properties' not 'props')")))

    (testing "type 1120 includes infoFi property"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/1120"))
            body (parse-json-body resp)
            info-fi (get-in body [:properties :infoFi])]
        (is (some? info-fi) "Type 1120 should include infoFi property")
        (when info-fi
          (is (string? (:name info-fi)) "infoFi should have name")
          (is (string? (:description info-fi)) "infoFi should have description")
          (is (= "string" (:dataType info-fi)) "infoFi dataType should be string"))))

    (testing "returns 200 for type code 1310 (Football field)"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/1310"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1310 (:typeCode body)))
        ;; Note: Type 1310 is Point in the current LIPAS system (not Polygon as in legacy)
        (is (#{"Point" "LineString" "Polygon"} (:geometryType body)))))

    (testing "returns 200 for type code 4401 (Jogging track - LineString)"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/4401"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 4401 (:typeCode body)))
        (is (= "LineString" (:geometryType body)))))

    (testing "returns 200 for type code 103 (Outdoor area - Polygon)"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/103"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 103 (:typeCode body)))
        (is (= "Polygon" (:geometryType body)))))

    (testing "property definitions have required fields"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/1120"))
            body (parse-json-body resp)]
        (is (map? (:properties body)) "Should have properties map")
        (is (nil? (:props body)) "Should NOT have props key (legacy uses 'properties')")
        (when-let [props (:properties body)]
          (is (pos? (count props)) "properties should not be empty")
          (doseq [[k prop] props]
            (is (not (re-find #"\?$" (name k)))
                (str "Property key should not end with '?': " k))
            (is (string? (:name prop)))
            ;; Legacy API only supports: boolean, string, numeric
            ;; enum/enum-coll are coerced to string
            (is (#{"boolean" "string" "numeric"} (:dataType prop))
                (str "dataType should be boolean/string/numeric, got: " (:dataType prop)))
            (is (nil? (:opts prop)) "enum opts should be removed in legacy API")))))

    (testing "enum properties are coerced to string dataType"
      ;; Type 1120 has surfaceMaterial which is enum-coll in the source data
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/1120"))
            body (parse-json-body resp)
            surface-material (get-in body [:properties :surfaceMaterial])]
        (is (some? surface-material) "Type 1120 should have surfaceMaterial property")
        (when surface-material
          (is (= "string" (:dataType surface-material))
              "surfaceMaterial dataType should be coerced from enum-coll to string")
          (is (nil? (:opts surface-material))
              "surfaceMaterial should not have opts (enum options removed)"))))

    (testing "properties key uses legacy format without question marks"
      (let [resp ((test-app) (mock/request :get "/v1/sports-place-types/6210"))
            body (parse-json-body resp)
            prop-keys (when (:properties body) (keys (:properties body)))]
        (is (some? prop-keys) "Type 6210 should have properties")
        ;; Production uses "ligthing" not "ligthing?"
        (when (seq prop-keys)
          (is (some #(= "ligthing" (name %)) prop-keys)
              "Should have 'ligthing' property (not 'ligthing?')")
          (is (not-any? #(str/ends-with? (name %) "?") prop-keys)
              "No property keys should end with '?'"))))

    (testing "respects lang parameter"
      (doseq [lang ["fi" "en" "se"]]
        (let [resp ((test-app) (mock/request :get (str "/v1/sports-place-types/1120?lang=" lang)))
              body (parse-json-body resp)]
          (is (= 200 (:status resp)))
          (is (= 1120 (:typeCode body))))))))

;;; Tests for different geometry types ;;;

(deftest geometry-types-test
  (testing "Different geometry types are correctly handled"

    (testing "Point geometry site"
      (let [site (test-utils/make-point-site 80001 :name "Point Site" :type-code 1120)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v1/sports-places/80001"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "Point" (-> body :location :geometries :features first :geometry :type)))))

    (testing "LineString geometry site (route)"
      (let [site (test-utils/make-route-site 80002 :name "Route Site" :type-code 4405)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v1/sports-places/80002"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "LineString" (-> body :location :geometries :features first :geometry :type)))))

    (testing "Polygon geometry site (area)"
      (let [site (test-utils/make-area-site 80003 :name "Area Site" :type-code 103)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v1/sports-places/80003"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "Polygon" (-> body :location :geometries :features first :geometry :type)))))))

;;; Tests for response format verification ;;;

(deftest legacy-format-verification-test
  (testing "Verify response format matches legacy API contract"
    (let [site (-> (test-utils/make-point-site 90001 :name "Format Verification Site")
                   (assoc :construction-year 2015
                          :www "http://example.com"
                          :email "test@example.com"
                          :phone-number "+358401234567"
                          :event-date "2024-01-15T12:30:45.123Z")
                   (assoc-in [:properties :ligthing?] true))
          _ (create-sports-site! site)
          resp ((test-app) (mock/request :get "/v1/sports-places/90001"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))

      (testing "sportsPlaceId is integer"
        (is (integer? (:sportsPlaceId body))))

      (testing "name is string"
        (is (string? (:name body))))

      (testing "type has correct structure"
        (is (map? (:type body)))
        (is (integer? (-> body :type :typeCode)))
        (is (string? (-> body :type :name))))

      (testing "location has correct structure"
        (let [loc (:location body)]
          (is (map? loc))
          (is (map? (:city loc)))
          (is (integer? (-> loc :city :cityCode)))
          (is (string? (-> loc :city :name)))))

      (testing "lastModified is in legacy format"
        (when-let [lm (:lastModified body)]
          (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}" lm))))

      (testing "admin is localized string (not enum key)"
        (when-let [admin (:admin body)]
          (is (not (re-matches #"^[a-z]+-[a-z]+(-[a-z]+)*$" admin))
              "admin should be localized, not kebab-case")))

      (testing "properties use camelCase keys"
        (when-let [props (:properties body)]
          (doseq [k (keys props)]
            (is (not (re-find #"-" (name k)))
                (str "Property key should be camelCase: " k)))))

      (testing "geometries is a FeatureCollection"
        (when-let [geoms (-> body :location :geometries)]
          (is (= "FeatureCollection" (:type geoms))))))))

;;; Tests for error handling ;;;

(deftest invalid-page-size-test
  (testing "Invalid pageSize returns 400"
    (let [resp ((test-app) (mock/request :get "/v1/sports-places?pageSize=0"))]
      (is (= 400 (:status resp))))

    (let [resp ((test-app) (mock/request :get "/v1/sports-places?pageSize=101"))]
      (is (= 400 (:status resp))))))

;;; Tests for production behavior compliance ;;;

(deftest sort-by-sports-place-id-test
  (testing "List results are sorted by sportsPlaceId ascending (production behavior)"
    ;; Create sites with specific IDs in non-sorted order
    (doseq [id [10500 10200 10700 10300 10100]]
      (create-sports-site! (test-utils/make-point-site id :name (str "Sort Test " id))))

    (let [resp ((test-app) (mock/request :get "/v1/sports-places?pageSize=100"))
          body (parse-json-body resp)
          ids (mapv :sportsPlaceId body)]
      (is (#{200 206} (:status resp)))
      ;; Verify IDs are sorted ascending
      (is (= ids (sort ids))
          (str "Results should be sorted by sportsPlaceId ascending. Got: " ids)))))

(deftest error-response-format-test
  (testing "Error response format matches production API"

    (testing "404 Not Found response format"
      (let [resp ((test-app) (mock/request :get "/v1/sports-places/999999999"))
            body (parse-json-body resp)]
        (is (= 404 (:status resp)))
        ;; Production format: {"errors":{"sportsPlaceId":"Didn't find such sports place. :("}}
        (is (map? (:errors body)) "Should have 'errors' key (not 'error')")
        (is (= "Didn't find such sports place. :("
               (-> body :errors :sportsPlaceId))
            "Should have exact production error message")))

    (testing "400 Bad Request - invalid typeCodes format"
      ;; Production returns: {"errors":{"typeCodes":["(not (#{ ...valid codes... } :invalid))"]}}
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?typeCodes=invalid"))
            body (parse-json-body resp)]
        (is (= 400 (:status resp)))
        (is (map? (:errors body)) "Should have 'errors' key")))

    (testing "400 Bad Request - validation error messages are human-readable"
      ;; The issue: local was showing raw Malli schema objects like
      ;; "malli.core$_simple_schema$reify$reify__39032@6f9688bd"
      ;; instead of human-readable messages
      (let [resp ((test-app) (mock/request :get "/v1/sports-places?pageSize=abc"))
            body (parse-json-body resp)]
        (is (= 400 (:status resp)))
        (is (map? (:errors body)) "Should have 'errors' key")
        (let [page-size-errors (get-in body [:errors :pageSize])]
          (is (some? page-size-errors) "Should have error for pageSize")
          ;; Verify error messages are human-readable, not raw schema objects
          (when (sequential? page-size-errors)
            (doseq [error-msg page-size-errors]
              (is (not (re-find #"malli\.core\$" (str error-msg)))
                  (str "Error message should NOT contain raw Malli schema object reference. Got: " error-msg))
              (is (not (re-find #"@[0-9a-f]+" (str error-msg)))
                  (str "Error message should NOT contain Java object reference (@hexid). Got: " error-msg)))))))))

;;; Tests for HEAD method support (production behavior) ;;;

(deftest head-method-support-test
  (testing "HEAD method is supported on list endpoint (production behavior)"
    ;; Create some sites so pagination kicks in
    (doseq [i (range 1 6)]
      (create-sports-site! (test-utils/make-point-site (+ 65000 i) :name (str "HEAD Test Site " i))))

    (testing "HEAD on /sports-places returns 206 with headers but no body"
      (let [resp ((test-app) (mock/request :head "/v1/sports-places?pageSize=3"))]
        (is (= 206 (:status resp))
            "HEAD should return 206 Partial Content like GET")
        (is (some? (get-in resp [:headers "Link"]))
            "HEAD should return Link header")
        (is (some? (get-in resp [:headers "X-total-count"]))
            "HEAD should return X-total-count header")
        ;; HEAD responses should have empty or nil body
        (is (or (nil? (:body resp))
                (empty? (:body resp))
                (= "" (:body resp)))
            "HEAD should return empty body")))

    (testing "HEAD on /sports-places returns 200 when no pagination needed"
      ;; Query for a specific type that has only a few results
      (let [resp ((test-app) (mock/request :head "/v1/sports-places?pageSize=100"))]
        (is (#{200 206} (:status resp))
            "HEAD should return valid status")))

    (testing "HEAD on /sports-places/:id returns 200"
      (let [resp ((test-app) (mock/request :head "/v1/sports-places/65001"))]
        (is (= 200 (:status resp))
            "HEAD on single item should return 200")))

    (testing "HEAD on /sports-places/:id returns 404 for missing"
      (let [resp ((test-app) (mock/request :head "/v1/sports-places/999999999"))]
        (is (= 404 (:status resp))
            "HEAD on missing item should return 404")))))

(comment
  (clojure.test/run-tests 'lipas.backend.api.v1.handler-test)

  (clojure.test/run-test-var #'get-single-sports-place-test)
  (clojure.test/run-test-var #'list-sports-places-empty-test)
  (clojure.test/run-test-var #'list-sports-places-with-results-test)
  (clojure.test/run-test-var #'categories-endpoint-test)
  (clojure.test/run-test-var #'sports-place-types-list-test)
  (clojure.test/run-test-var #'sports-place-type-detail-test)
  (clojure.test/run-test-var #'geometry-types-test)
  (clojure.test/run-test-var #'list-sports-places-search-string-relevance-test)
  (clojure.test/run-test-var #'head-method-support-test))
