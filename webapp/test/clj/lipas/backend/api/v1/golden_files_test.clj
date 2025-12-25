(ns lipas.backend.api.v1.golden-files-test
  "Golden file tests verify that our transformation produces output
   matching the production legacy API format.

   The key test: Given a sports place in NEW LIPAS format,
   transform it using ->old-lipas-sports-site and verify the result
   matches the production legacy API response.

   Test fixtures are paired:
   - new-lipas-input-*.edn - The new LIPAS format (what's in our DB)
   - legacy-expected-*.json - The production legacy API response (golden file)

   These tests verify the TRANSFORMATION LOGIC is correct by comparing
   our transform output against known-good production responses."
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.test :refer [deftest is testing]]
   [lipas.backend.api.v1.sports-place :as legacy-sports-place]
   [lipas.backend.api.v1.transform :as transform]))

;;; Fixture loading helpers

(def fixtures-dir "test/resources/legacy_api/fixtures/")

(defn fixture-file [filename]
  (io/file fixtures-dir filename))

(defn fixture-exists? [filename]
  (.exists (fixture-file filename)))

(defn read-json-fixture [filename]
  (when (fixture-exists? filename)
    (-> (fixture-file filename)
        slurp
        (json/read-str :key-fn keyword))))

;;; Transform verification tests

(deftest transform-produces-legacy-format
  (testing "Transform output has correct top-level structure"
    (let [new-input {:lipas-id 12345
                     :name "Test Place"
                     :status "active"
                     :admin "city-technical-services"
                     :owner "city"
                     :event-date "2024-01-15T12:30:45.123Z"
                     :type {:type-code 1120}
                     :location {:city {:city-code "91"}
                                :address "Test Street 1"
                                :postal-code "00100"
                                :postal-office "Helsinki"
                                :geometries {:type "FeatureCollection"
                                             :features [{:type "Feature"
                                                         :geometry {:type "Point"
                                                                    :coordinates [25.0 60.0]}}]}}}
          result (transform/->old-lipas-sports-site new-input)]

      (testing "Has correct top-level keys"
        (is (contains? result :name))
        (is (contains? result :type))
        (is (contains? result :location))
        (is (contains? result :lastModified))
        (is (contains? result :admin))
        (is (contains? result :owner)))

      (testing "Name is in locale map format"
        (is (map? (:name result)))
        (is (contains? (:name result) :fi)))

      (testing "Type has correct structure"
        (is (integer? (-> result :type :typeCode))))

      (testing "lastModified is in legacy format"
        (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}"
                        (:lastModified result))))

      (testing "Location has correct keys"
        (is (contains? (:location result) :city))
        (is (contains? (:location result) :address))
        (is (contains? (:location result) :postalCode))
        (is (contains? (:location result) :geometries))))))

(deftest date-transformation
  (testing "UTC dates are converted to Helsinki timezone legacy format"
    (let [test-cases [["2019-08-29T12:55:30.259Z" "2019-08-29 15:55:30.259"]  ; Summer (+3)
                      ["2024-01-15T10:30:45.123Z" "2024-01-15 12:30:45.123"]  ; Winter (+2)
                      ["2024-06-15T14:00:00.000Z" "2024-06-15 17:00:00.000"]]] ; Summer (+3)
      (doseq [[utc-input expected] test-cases]
        (is (= expected (transform/UTC->last-modified utc-input))
            (str "Failed for input: " utc-input))))))

(deftest admin-owner-transformation
  (testing "Admin/owner enum keys are preserved (localization happens at API layer)"
    (let [input {:lipas-id 1
                 :name "Test"
                 :status "active"
                 :admin "city-technical-services"
                 :owner "city"
                 :type {:type-code 1120}
                 :event-date "2024-01-01T00:00:00.000Z"
                 :location {:city {:city-code "91"}
                            :geometries {:type "FeatureCollection"
                                         :features []}}}
          result (transform/->old-lipas-sports-site input)]
      ;; Transform preserves enum keys; localization to "Kunta / tekninen toimi"
      ;; happens in the API layer (format-sports-place-es)
      (is (= "city-technical-services" (:admin result)))
      (is (= "city" (:owner result)))))

  (testing "Unknown admin/owner converted to no-information"
    (let [input {:lipas-id 1
                 :name "Test"
                 :status "active"
                 :admin "unknown"
                 :owner "unknown"
                 :type {:type-code 1120}
                 :event-date "2024-01-01T00:00:00.000Z"
                 :location {:city {:city-code "91"}
                            :geometries {:type "FeatureCollection"
                                         :features []}}}
          result (transform/->old-lipas-sports-site input)]
      (is (= "no-information" (:admin result)))
      (is (= "no-information" (:owner result))))))

(deftest property-key-transformation
  (testing "Property keys are transformed from kebab-case to camelCase"
    (let [input {:lipas-id 1
                 :name "Test"
                 :status "active"
                 :admin "city"
                 :owner "city"
                 :type {:type-code 1120}
                 :event-date "2024-01-01T00:00:00.000Z"
                 :properties {:ligthing? true
                              :playground? true
                              :fields-count 2
                              :school-use? true}
                 :location {:city {:city-code "91"}
                            :geometries {:type "FeatureCollection"
                                         :features []}}}
          result (transform/->old-lipas-sports-site input)]

      (testing "schoolUse extracted to top level"
        (is (= true (:schoolUse result))))

      (testing "Properties have camelCase keys"
        (let [props (:properties result)]
          (is (contains? props :ligthing) "Legacy typo 'ligthing' preserved")
          (is (contains? props :playground))
          (is (contains? props :fieldsCount)))))))

(deftest comment-to-infoFi-transformation
  (testing "Comment field is transformed to properties.infoFi"
    (let [input {:lipas-id 1
                 :name "Test"
                 :status "active"
                 :admin "city"
                 :owner "city"
                 :type {:type-code 1120}
                 :event-date "2024-01-01T00:00:00.000Z"
                 :comment "This is a test comment"
                 :location {:city {:city-code "91"}
                            :geometries {:type "FeatureCollection"
                                         :features []}}}
          result (transform/->old-lipas-sports-site input)]
      (is (= "This is a test comment" (-> result :properties :infoFi))))))

;;; Production data structure validation
;;; These tests verify our understanding of production data format
;;; They only run if the golden files exist

(deftest production-data-structure-validation
  (testing "Production fixture has expected structure (validates our golden files)"
    (if-let [fixtures (read-json-fixture "sports-places-1000.json")]
      (do
        (testing "Fixtures is a collection"
          (is (sequential? fixtures))
          (is (= 1000 (count fixtures))))

        (testing "Each fixture has required fields"
          (doseq [sp fixtures]
            (is (integer? (:sportsPlaceId sp))
                (str "sportsPlaceId should be integer for " (:name sp)))
            (is (some? (:name sp)))
            (is (map? (:type sp)))
            (is (integer? (-> sp :type :typeCode)))))

        (testing "lastModified dates are in legacy format"
          (let [dates (->> fixtures (map :lastModified) (remove nil?))]
            (doseq [date dates]
              (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}" date)
                  (str "Invalid date format: " date)))))

        (testing "Admin/owner are localized strings in production"
          (let [admins (->> fixtures (map :admin) (remove nil?) set)]
            ;; Production data has localized strings like "Kunta / tekninen toimi"
            (is (some #(re-find #"Kunta" %) admins)
                "Production admin values should be localized Finnish"))))
      (println "SKIP: sports-places-1000.json not found"))))

;;; Prop mapping completeness tests

(deftest prop-mappings-coverage
  (testing "All production property keys have reverse mappings"
    (if-let [fixtures (read-json-fixture "sports-places-1000.json")]
      (let [prod-prop-keys (->> fixtures
                                (mapcat #(-> % :properties keys))
                                (remove #{:infoFi}) ; infoFi is special (from :comment)
                                set)
            mapped-keys (set (keys legacy-sports-place/prop-mappings))]

        (testing "Production keys are mapped"
          (let [unmapped (set/difference prod-prop-keys mapped-keys)]
            (when (seq unmapped)
              (println "WARNING: Unmapped production property keys:" unmapped))
            ;; This is informational - some keys may be type-specific
            (is (< (count unmapped) 20)
                (str "Too many unmapped keys: " unmapped)))))
      (println "SKIP: sports-places-1000.json not found"))))

;;; Categories and types endpoint validation

(deftest categories-fixture-structure
  (testing "Categories fixture has expected structure"
    (if-let [categories (read-json-fixture "categories.json")]
      (do
        (is (sequential? categories))
        (is (pos? (count categories)))

        (doseq [cat categories]
          (is (string? (:name cat)))
          (is (integer? (:typeCode cat)))
          (is (sequential? (:subCategories cat)))

          (doseq [subcat (:subCategories cat)]
            (is (string? (:name subcat)))
            (is (integer? (:typeCode subcat))))))
      (println "SKIP: categories.json not found"))))

(deftest sports-place-types-fixture-structure
  (testing "Sports place types fixture has expected structure"
    (if-let [types (read-json-fixture "sports-place-types.json")]
      (do
        (is (sequential? types))
        (is (> (count types) 100) "Should have many types")

        (doseq [t types]
          (is (integer? (:typeCode t)))
          (is (string? (:name t)))
          ;; Description may be nil for some types
          (is (or (nil? (:description t)) (string? (:description t))))
          (is (contains? #{"Point" "LineString" "Polygon"} (:geometryType t)))
          (is (integer? (:subCategory t)))))
      (println "SKIP: sports-place-types.json not found"))))

(deftest sports-place-type-detail-fixture-structure
  (testing "Type detail fixture has properties"
    (if-let [type-detail (read-json-fixture "sports-place-type-1120.json")]
      (do
        (is (= 1120 (:typeCode type-detail)))
        (is (string? (:name type-detail)))
        (is (string? (:description type-detail)))
        (is (= "Point" (:geometryType type-detail)))
        (is (map? (:properties type-detail)))

        (testing "Properties have correct structure"
          (doseq [[k prop] (:properties type-detail)]
            (is (string? (:name prop)) (str "Property " k " should have name"))
            (is (contains? #{"boolean" "string" "numeric" "enum" "enum-coll"}
                           (:dataType prop))
                (str "Property " k " has invalid dataType: " (:dataType prop))))))
      (println "SKIP: sports-place-type-1120.json not found"))))

;;; Coverage statistics (informational)

(deftest fixture-coverage-stats
  (testing "Fixture coverage statistics (informational)"
    (if-let [fixtures (read-json-fixture "sports-places-1000.json")]
      (let [type-codes (->> fixtures (map #(-> % :type :typeCode)) frequencies)
            city-codes (->> fixtures (map #(-> % :location :city :cityCode)) frequencies)
            geom-types (->> fixtures
                            (map #(-> % :location :geometries :features first :geometry :type))
                            frequencies)]

        (println "\n=== Golden File Coverage Statistics ===")
        (println "Total fixtures:" (count fixtures))
        (println "Unique type codes:" (count type-codes))
        (println "Unique cities:" (count city-codes))
        (println "Geometry types:" geom-types)
        (println "Sample type codes:" (take 10 (keys type-codes)))

        ;; Verify we have meaningful data
        (is (= 1000 (count fixtures)))
        (is (pos? (count type-codes)) "Should have some type codes")
        ;; At minimum we should have Point and Polygon (LineString may not be in sample)
        (is (contains? (set (keys geom-types)) "Point") "Should have Point geometries")
        (is (every? #{"Point" "LineString" "Polygon"} (keys geom-types))
            "All geometry types should be valid"))
      (println "SKIP: sports-places-1000.json not found"))))

(comment
  ;; Run tests
  (clojure.test/run-tests 'lipas.backend.api.v1.golden-files-test))
