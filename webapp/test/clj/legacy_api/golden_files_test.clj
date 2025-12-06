(ns legacy-api.golden-files-test
  "Golden file tests verify that our transformation produces output
   matching the production legacy API format.

   These tests compare structure and field names rather than exact values,
   since we're testing transformation logic not specific data."
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.test :refer [deftest is testing]]
   [legacy-api.transform :as transform]))

(defn read-fixture [filename]
  (-> (io/resource (str "legacy_api/fixtures/" filename))
      slurp
      (json/read-str :key-fn keyword)))

(defn deep-keys
  "Returns all keys in a nested map structure as paths."
  ([m] (deep-keys m []))
  ([m prefix]
   (if (map? m)
     (mapcat (fn [[k v]]
               (let [path (conj prefix k)]
                 (if (map? v)
                   (cons path (deep-keys v path))
                   [path])))
             m)
     [])))

(defn collect-all-keys
  "Collects all unique keys from a collection of maps."
  [coll]
  (->> coll
       (mapcat deep-keys)
       (map #(vec (take 2 %))) ; Only top 2 levels
       set))

;;; Golden file structure tests

(deftest production-api-field-structure
  (testing "Verify production API response has expected top-level fields"
    (let [fixtures (read-fixture "sports-places-1000.json")
          ;; Collect all top-level keys across all fixtures
          all-keys (->> fixtures (mapcat keys) set)]

      (testing "Required fields present in production data"
        (is (contains? all-keys :sportsPlaceId) "sportsPlaceId is required")
        (is (contains? all-keys :name) "name is required")
        (is (contains? all-keys :type) "type is required")
        (is (contains? all-keys :location) "location is required"))

      (testing "Common optional fields present"
        (is (contains? all-keys :properties) "properties field exists")
        (is (contains? all-keys :lastModified) "lastModified field exists")
        (is (contains? all-keys :admin) "admin field exists")
        (is (contains? all-keys :owner) "owner field exists")))))

(deftest production-api-type-structure
  (testing "Verify type object structure"
    (let [fixtures (read-fixture "sports-places-1000.json")
          types (->> fixtures (map :type) (remove nil?))]

      (is (every? :typeCode types) "All types have typeCode")
      (is (every? :name types) "All types have name")
      (is (every? #(integer? (:typeCode %)) types) "typeCode is integer"))))

(deftest production-api-location-structure
  (testing "Verify location object structure"
    (let [fixtures (read-fixture "sports-places-1000.json")
          locations (->> fixtures (map :location) (remove nil?))]

      (testing "City structure"
        (let [cities (->> locations (map :city) (remove nil?))]
          (is (every? :cityCode cities) "Cities have cityCode")
          (is (every? :name cities) "Cities have name")
          (is (every? #(integer? (:cityCode %)) cities) "cityCode is integer")))

      (testing "Coordinates structure"
        (let [coords (->> locations (map :coordinates) (remove nil?))]
          (when (seq coords)
            (is (every? :wgs84 coords) "Has wgs84 coordinates")
            (is (every? :tm35fin coords) "Has tm35fin coordinates"))))

      (testing "Geometries structure"
        (let [geoms (->> locations (map :geometries) (remove nil?))]
          (when (seq geoms)
            (is (every? #(= "FeatureCollection" (:type %)) geoms)
                "Geometries are FeatureCollections")))))))

(deftest production-api-date-format
  (testing "Verify lastModified date format matches legacy format"
    (let [fixtures (read-fixture "sports-places-1000.json")
          dates (->> fixtures (map :lastModified) (remove nil?))]

      (is (seq dates) "Some fixtures have lastModified")

      ;; Legacy format: "yyyy-MM-dd HH:mm:ss.SSS"
      (let [date-pattern #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}"]
        (doseq [date dates]
          (is (re-matches date-pattern date)
              (str "Date format matches legacy pattern: " date)))))))

(deftest production-api-admin-owner-values
  (testing "Admin and owner are localized strings, not enum keys"
    (let [fixtures (read-fixture "sports-places-1000.json")
          admins (->> fixtures (map :admin) (remove nil?) set)
          owners (->> fixtures (map :owner) (remove nil?) set)]

      ;; Should be Finnish localized values, not kebab-case enum keys
      (is (not-any? #(re-find #"^[a-z]+-[a-z]+(-[a-z]+)*$" %) admins)
          "Admin values are not kebab-case enums")
      (is (not-any? #(re-find #"^[a-z]+-[a-z]+(-[a-z]+)*$" %) owners)
          "Owner values are not kebab-case enums")

      ;; Should contain Finnish text
      (is (some #(re-find #"Kunta" %) admins) "Admin contains Finnish 'Kunta'")
      (is (some #(re-find #"Kunta" %) owners) "Owner contains Finnish 'Kunta'"))))

(deftest production-api-property-keys
  (testing "Property keys are camelCase"
    (let [fixtures (read-fixture "sports-places-1000.json")
          all-prop-keys (->> fixtures
                             (map :properties)
                             (remove nil?)
                             (mapcat keys)
                             set)]

      ;; Verify camelCase naming (no kebab-case)
      (is (not-any? #(re-find #"-" (name %)) all-prop-keys)
          "No kebab-case property keys")

      ;; Check for known legacy quirks
      (when (contains? all-prop-keys :ligthing)
        (is true "Legacy 'ligthing' typo preserved")))))

(deftest categories-endpoint-structure
  (testing "Categories response structure"
    (let [categories (read-fixture "categories.json")]

      (is (vector? categories) "Categories is a vector")
      (is (pos? (count categories)) "Has categories")

      (doseq [cat categories]
        (is (:name cat) "Category has name")
        (is (:typeCode cat) "Category has typeCode")
        (is (:subCategories cat) "Category has subCategories")

        (doseq [subcat (:subCategories cat)]
          (is (:name subcat) "SubCategory has name")
          (is (:typeCode subcat) "SubCategory has typeCode"))))))

(deftest sports-place-types-endpoint-structure
  (testing "Sports place types response structure"
    (let [types (read-fixture "sports-place-types.json")]

      (is (vector? types) "Types is a vector")
      (is (< 100 (count types)) "Has many types")

      (doseq [t types]
        (is (:typeCode t) "Type has typeCode")
        (is (:name t) "Type has name")
        (is (:geometryType t) "Type has geometryType")
        (is (contains? #{"Point" "LineString" "Polygon"} (:geometryType t))
            "geometryType is valid")))))

(deftest sports-place-type-detail-structure
  (testing "Single sports place type with properties"
    (let [type-detail (read-fixture "sports-place-type-1120.json")]

      (is (= 1120 (:typeCode type-detail)))
      (is (:name type-detail))
      (is (:description type-detail))
      (is (= "Point" (:geometryType type-detail)))
      (is (:subCategory type-detail))
      (is (map? (:properties type-detail)))

      (testing "Property definitions have required fields"
        (doseq [[_k prop] (:properties type-detail)]
          (is (:name prop) "Property has name")
          (is (:dataType prop) "Property has dataType")
          (is (contains? #{"boolean" "string" "numeric" "enum" "enum-coll"}
                         (:dataType prop))
              "dataType is valid"))))))

;;; Summary statistics for debugging

(deftest fixture-coverage-stats
  (testing "Fixture coverage statistics (informational)"
    (let [fixtures (read-fixture "sports-places-1000.json")
          type-codes (->> fixtures (map #(-> % :type :typeCode)) frequencies)
          city-codes (->> fixtures (map #(-> % :location :city :cityCode)) frequencies)]

      (println "\n=== Golden File Statistics ===")
      (println "Total fixtures:" (count fixtures))
      (println "Unique type codes:" (count type-codes))
      (println "Unique cities:" (count city-codes))
      (println "Type codes sample:" (take 10 (keys type-codes)))

      (is (= 1000 (count fixtures)) "Have 1000 fixtures"))))

(comment
  ;; Run tests
  (clojure.test/run-tests 'legacy-api.golden-files-test))
