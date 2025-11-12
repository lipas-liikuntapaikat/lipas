(ns lipas.schema.common-test
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.schema.common :as common]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]))

(deftest coordinates-validation-test
  (testing "Valid coordinates"
    (is (m/validate common/coordinates [25.0 60.2]))
    (is (m/validate common/coordinates [25 60]))
    (is (m/validate common/coordinates [25.0 60.2 100.0]))
    (is (m/validate common/coordinates [25 60 100])))

  (testing "Finland bounds"
    (is (m/validate common/coordinates [18.0 59.0]))
    (is (m/validate common/coordinates [33.0 71.0]))
    (is (m/validate common/coordinates [26.5 68.0])))

  (testing "Out of bounds rejected"
    (is (not (m/validate common/coordinates [50.0 60.0])))
    (is (not (m/validate common/coordinates [25.0 50.0])))
    (is (not (m/validate common/coordinates [25.0 75.0]))))

  (testing "Infinite and NaN rejected"
    (is (not (m/validate common/coordinates [Double/POSITIVE_INFINITY 60.0])))
    (is (not (m/validate common/coordinates [25.0 Double/NEGATIVE_INFINITY])))
    (is (not (m/validate common/coordinates [Double/NaN 60.0])))
    (is (not (m/validate common/coordinates [25.0 Double/NaN]))))

  (testing "Altitude bounds"
    (is (m/validate common/coordinates [25.0 60.0 -1500.0]))
    (is (m/validate common/coordinates [25.0 60.0 2000.0]))
    (is (m/validate common/coordinates [25.0 60.0 -10000.0])) ; actual min bound
    (is (not (m/validate common/coordinates [25.0 60.0 -10001.0]))) ; below min
    (is (not (m/validate common/coordinates [25.0 60.0 3000.0])))))

(deftest coordinates-generator-test
  (testing "Generated coordinates are valid"
    (let [samples (mg/sample common/coordinates {:size 100})]
      (is (= 100 (count samples)))
      (is (every? #(m/validate common/coordinates %) samples))))

  (testing "Generated coordinates are within Finland bounds"
    (let [samples (mg/sample common/coordinates {:size 100})]
      (doseq [[lon lat & _] samples]
        (is (<= 18.0 lon 33.0) (str "Longitude " lon " out of bounds"))
        (is (<= 59.0 lat 71.0) (str "Latitude " lat " out of bounds"))
        (is (not (Double/isInfinite lon)))
        (is (not (Double/isInfinite lat)))
        (is (not (Double/isNaN lon)))
        (is (not (Double/isNaN lat))))))

  (testing "Generated coordinates with altitude"
    (let [samples (mg/sample common/coordinates {:size 100})
          with-altitude (filter #(= 3 (count %)) samples)]
      (doseq [[_ _ alt] with-altitude]
        (is (<= -10000.0 alt 2000.0) (str "Altitude " alt " out of bounds"))
        (is (not (Double/isInfinite alt)))
        (is (not (Double/isNaN alt)))))))

(deftest status-test
  (testing "Valid status values"
    (is (m/validate common/status "active"))
    (is (m/validate common/status "out-of-service-temporarily"))
    (is (m/validate common/status "out-of-service-permanently")))

  (testing "Invalid status values"
    (is (not (m/validate common/status "invalid-status")))
    (is (not (m/validate common/status "")))
    (is (not (m/validate common/status :active))) ; keywords not accepted, strings only
    (is (not (m/validate common/status nil)))))

(deftest statuses-test
  (testing "Valid status sets"
    (is (m/validate common/statuses #{"active"}))
    (is (m/validate common/statuses #{"active" "out-of-service-temporarily"}))
    (is (m/validate common/statuses #{}))) ; empty set valid

  (testing "Invalid status sets"
    (is (not (m/validate common/statuses #{"invalid-status"})))
    (is (not (m/validate common/statuses #{"active" "invalid"}))) ; mix of valid/invalid
    (is (not (m/validate common/statuses ["active"]))) ; vector not set
    (is (not (m/validate common/statuses nil)))))

(deftest iso8601-timestamp-test
  (testing "Valid ISO 8601 timestamps"
    (is (m/validate common/iso8601-timestamp "2024-01-15T10:30:00.000Z"))
    (is (m/validate common/iso8601-timestamp "2024-12-31T23:59:59.999Z"))
    (is (m/validate common/iso8601-timestamp "2024-02-29T12:00:00.000Z")) ; leap year
    (is (m/validate common/iso8601-timestamp "1900-01-01T00:00:00.000Z"))
    (is (m/validate common/iso8601-timestamp "2100-12-31T23:59:59.999Z")))

  (testing "Invalid ISO 8601 timestamps"
    (is (not (m/validate common/iso8601-timestamp "2024-01-15T10:30:00"))) ; missing Z
    (is (not (m/validate common/iso8601-timestamp "2024-01-15 10:30:00.000Z"))) ; space instead of T
    (is (not (m/validate common/iso8601-timestamp "2024-02-30T12:00:00.000Z"))) ; invalid date
    (is (not (m/validate common/iso8601-timestamp "2023-02-29T12:00:00.000Z"))) ; not leap year
    (is (not (m/validate common/iso8601-timestamp "2024-13-01T12:00:00.000Z"))) ; invalid month
    (is (not (m/validate common/iso8601-timestamp "2024-01-32T12:00:00.000Z"))) ; invalid day
    (is (not (m/validate common/iso8601-timestamp "2024-01-15T25:00:00.000Z"))) ; invalid hour
    (is (not (m/validate common/iso8601-timestamp "not-a-timestamp"))))

  (testing "Generated ISO 8601 timestamps are valid"
    (let [samples (mg/sample common/iso8601-timestamp {:size 50})]
      (is (= 50 (count samples)))
      (is (every? string? samples))
      (is (every? #(m/validate common/iso8601-timestamp %) samples)))))

(deftest number-test
  (testing "Valid numbers"
    (is (m/validate common/number 0))
    (is (m/validate common/number 42))
    (is (m/validate common/number -42))
    (is (m/validate common/number 3.14))
    (is (m/validate common/number -3.14))
    (is (m/validate common/number 0.0))
    (is (m/validate common/number Double/MAX_VALUE))
    (is (m/validate common/number (- Double/MAX_VALUE))))

  (testing "Invalid numbers - Infinity and NaN"
    (is (not (m/validate common/number Double/POSITIVE_INFINITY)))
    (is (not (m/validate common/number Double/NEGATIVE_INFINITY)))
    (is (not (m/validate common/number Double/NaN)))
    (is (not (m/validate common/number "42")))
    (is (not (m/validate common/number nil))))

  (testing "Generated numbers are valid and finite"
    (let [samples (mg/sample common/number {:size 100})]
      (is (= 100 (count samples)))
      (is (every? number? samples))
      (is (every? #(m/validate common/number %) samples))
      (is (every? #(Double/isFinite %) samples))
      (is (not-any? #(Double/isNaN %) samples)))))

(deftest number-error-messages-test
  (testing "Infinity error messages"
    (let [pos-inf-errors (-> common/number
                             (m/explain Double/POSITIVE_INFINITY)
                             me/humanize)
          neg-inf-errors (-> common/number
                             (m/explain Double/NEGATIVE_INFINITY)
                             me/humanize)]
      (is (some #(= "Value cannot be Infinity" %)
                (flatten pos-inf-errors))
          "Should provide clear Infinity error for positive infinity")
      (is (some #(= "Value cannot be Infinity" %)
                (flatten neg-inf-errors))
          "Should provide clear Infinity error for negative infinity")))

  (testing "NaN error message"
    (let [errors (-> common/number
                     (m/explain Double/NaN)
                     me/humanize)]
      (is (some #(= "Value cannot be NaN (Not a Number)" %)
                (flatten errors))
          "Should provide clear NaN error"))))

(deftest pos-int-test
  (testing "Valid positive integers"
    (is (m/validate common/pos-int 0))
    (is (m/validate common/pos-int 1))
    (is (m/validate common/pos-int 42))
    (is (m/validate common/pos-int 1000))
    (is (m/validate common/pos-int 999999)))

  (testing "Invalid positive integers"
    (is (not (m/validate common/pos-int -1)))
    (is (not (m/validate common/pos-int -42)))
    (is (not (m/validate common/pos-int 3.14)))
    (is (not (m/validate common/pos-int "42")))
    (is (not (m/validate common/pos-int nil))))

  (testing "Generated positive integers are valid"
    (let [samples (mg/sample common/pos-int {:size 100})]
      (is (= 100 (count samples)))
      (is (every? int? samples))
      (is (every? #(>= % 0) samples))
      (is (every? #(m/validate common/pos-int %) samples)))))

(deftest percentage-test
  (testing "Valid percentages"
    (is (m/validate common/percentage 0))
    (is (m/validate common/percentage 0.0))
    (is (m/validate common/percentage 50))
    (is (m/validate common/percentage 50.5))
    (is (m/validate common/percentage 100))
    (is (m/validate common/percentage 100.0)))

  (testing "Invalid percentages - out of range"
    (is (not (m/validate common/percentage -0.1)))
    (is (not (m/validate common/percentage -1)))
    (is (not (m/validate common/percentage 100.1)))
    (is (not (m/validate common/percentage 200)))
    (is (not (m/validate common/percentage Double/POSITIVE_INFINITY)))
    (is (not (m/validate common/percentage Double/NaN)))
    (is (not (m/validate common/percentage "50")))
    (is (not (m/validate common/percentage nil))))

  (testing "Property: all percentages are between 0 and 100"
    (let [samples (repeatedly 100 #(* (rand) 100))]
      (is (every? #(m/validate common/percentage %) samples))
      (is (every? #(<= 0 % 100) samples)))))

(deftest percentage-error-messages-test
  (testing "Out of range error messages"
    (let [too-low-errors (-> common/percentage
                             (m/explain -10.0)
                             me/humanize)
          too-high-errors (-> common/percentage
                              (m/explain 150.0)
                              me/humanize)]
      (is (some #(= "Percentage must be between 0 and 100" %)
                (flatten too-low-errors))
          "Should provide clear range error for negative values")
      (is (some #(= "Percentage must be between 0 and 100" %)
                (flatten too-high-errors))
          "Should provide clear range error for values over 100"))))

(deftest uuid-test
  (testing "Valid UUIDs v4"
    (is (m/validate common/uuid "550e8400-e29b-41d4-a716-446655440000"))
    (is (m/validate common/uuid "123e4567-e89b-42d3-a456-426614174000")) ; v4 UUID
    (is (m/validate common/uuid "00000000-0000-4000-8000-000000000000")))

  (testing "Invalid UUIDs"
    (is (not (m/validate common/uuid "not-a-uuid")))
    (is (not (m/validate common/uuid "550e8400-e29b-41d4-a716"))) ; too short
    (is (not (m/validate common/uuid "550e8400-e29b-31d4-a716-446655440000"))) ; wrong version (3, not 4)
    (is (not (m/validate common/uuid "550e8400-e29b-41d4-c716-446655440000"))) ; wrong variant
    (is (not (m/validate common/uuid "550E8400-E29B-41D4-A716-446655440000"))) ; uppercase
    (is (not (m/validate common/uuid "")))
    (is (not (m/validate common/uuid nil)))))

(deftest localized-string-test
  (testing "Valid localized strings"
    (is (m/validate common/localized-string {:fi "suomi"}))
    (is (m/validate common/localized-string {:se "svenska"}))
    (is (m/validate common/localized-string {:en "english"}))
    (is (m/validate common/localized-string {:fi "suomi" :se "svenska"}))
    (is (m/validate common/localized-string {:fi "suomi" :se "svenska" :en "english"}))
    (is (m/validate common/localized-string {}))) ; all optional

  (testing "Invalid localized strings"
    (is (not (m/validate common/localized-string {:fi 123})))
    (is (not (m/validate common/localized-string {:se nil})))
    ;; Note: extra keys like :de are allowed since map is not closed
    (is (not (m/validate common/localized-string "not a map")))
    (is (not (m/validate common/localized-string nil)))))

(deftest point-feature-test
  (testing "Valid Point features"
    (is (m/validate common/point-feature
                    {:type "Feature"
                     :geometry {:type "Point" :coordinates [25.0 60.0]}}))
    (is (m/validate common/point-feature
                    {:type "Feature"
                     :geometry {:type "Point" :coordinates [25.0 60.0]}
                     :properties {}}))
    (is (m/validate common/point-feature
                    {:type "Feature"
                     :geometry {:type "Point" :coordinates [25.0 60.0 100.0]}
                     :properties {:name "Test" :value 42}})))

  (testing "Invalid Point features"
    (is (not (m/validate common/point-feature
                         {:type "Feature"
                          :geometry {:type "LineString" :coordinates [[25.0 60.0] [26.0 61.0]]}})))
    (is (not (m/validate common/point-feature
                         {:geometry {:type "Point" :coordinates [25.0 60.0]}}))) ; missing type
    (is (not (m/validate common/point-feature
                         {:type "Feature"}))) ; missing geometry
    (is (not (m/validate common/point-feature
                         {:type "Feature"
                          :geometry {:type "Point" :coordinates [0.0 60.0]}})))) ; out of bounds

  (testing "Generated Point features are valid"
    (let [samples (mg/sample common/point-feature {:size 20})]
      (is (= 20 (count samples)))
      (is (every? #(m/validate common/point-feature %) samples))
      (is (every? #(= "Feature" (:type %)) samples))
      (is (every? #(= "Point" (get-in % [:geometry :type])) samples)))))

(deftest line-string-feature-test
  (testing "Valid LineString features"
    (is (m/validate common/line-string-feature
                    {:type "Feature"
                     :geometry {:type "LineString" :coordinates [[25.0 60.0] [26.0 61.0]]}}))
    (is (m/validate common/line-string-feature
                    {:type "Feature"
                     :geometry {:type "LineString" :coordinates [[25.0 60.0] [26.0 61.0] [27.0 62.0]]}
                     :properties {:name "Route" :distance 100}})))

  (testing "Invalid LineString features"
    (is (not (m/validate common/line-string-feature
                         {:type "Feature"
                          :geometry {:type "Point" :coordinates [25.0 60.0]}})))
    (is (not (m/validate common/line-string-feature
                         {:type "Feature"
                          :geometry {:type "LineString" :coordinates [[25.0 60.0]]}}))) ; only 1 point
    (is (not (m/validate common/line-string-feature
                         {:type "Feature"
                          :geometry {:type "LineString" :coordinates []}}))))

  (testing "Generated LineString features are valid"
    (let [samples (mg/sample common/line-string-feature {:size 20})]
      (is (= 20 (count samples)))
      (is (every? #(m/validate common/line-string-feature %) samples))
      (is (every? #(= "Feature" (:type %)) samples))
      (is (every? #(= "LineString" (get-in % [:geometry :type])) samples)))))

(deftest polygon-feature-test
  (testing "Valid Polygon features"
    (is (m/validate common/polygon-feature
                    {:type "Feature"
                     :geometry {:type "Polygon"
                                :coordinates [[[25.0 60.0] [26.0 60.0] [26.0 61.0] [25.0 61.0] [25.0 60.0]]]}}))
    (is (m/validate common/polygon-feature
                    {:type "Feature"
                     :geometry {:type "Polygon"
                                :coordinates [[[25.0 60.0] [26.0 60.0] [26.0 61.0] [25.0 61.0] [25.0 60.0]]]}
                     :properties {:area 100 :name "Park"}})))

  (testing "Invalid Polygon features"
    (is (not (m/validate common/polygon-feature
                         {:type "Feature"
                          :geometry {:type "Point" :coordinates [25.0 60.0]}})))
    (is (not (m/validate common/polygon-feature
                         {:type "Feature"
                          :geometry {:type "Polygon"
                                     :coordinates [[[25.0 60.0] [26.0 60.0] [26.0 61.0]]]}}))) ; not closed
    (is (not (m/validate common/polygon-feature
                         {:type "Feature"
                          :geometry {:type "Polygon" :coordinates []}}))))

  (testing "Generated Polygon features are valid"
    (let [samples (mg/sample common/polygon-feature {:size 20})]
      (is (= 20 (count samples)))
      (is (every? #(m/validate common/polygon-feature %) samples))
      (is (every? #(= "Feature" (:type %)) samples))
      (is (every? #(= "Polygon" (get-in % [:geometry :type])) samples)))))

(deftest feature-collection-test
  (testing "Valid Point FeatureCollections"
    (is (m/validate common/point-feature-collection
                    {:type "FeatureCollection"
                     :features [{:type "Feature"
                                 :geometry {:type "Point" :coordinates [25.0 60.0]}}]}))
    (is (m/validate common/point-feature-collection
                    {:type "FeatureCollection"
                     :features [{:type "Feature"
                                 :geometry {:type "Point" :coordinates [25.0 60.0]}}
                                {:type "Feature"
                                 :geometry {:type "Point" :coordinates [26.0 61.0]}}]}))
    (is (m/validate common/point-feature-collection
                    {:type "FeatureCollection"
                     :features []}))) ; empty is valid

  (testing "Invalid Point FeatureCollections"
    (is (not (m/validate common/point-feature-collection
                         {:type "FeatureCollection"
                          :features [{:type "Feature"
                                      :geometry {:type "LineString" :coordinates [[25.0 60.0] [26.0 61.0]]}}]})))
    (is (not (m/validate common/point-feature-collection
                         {:features [{:type "Feature"
                                      :geometry {:type "Point" :coordinates [25.0 60.0]}}]}))) ; missing type
    (is (not (m/validate common/point-feature-collection
                         {:type "FeatureCollection"}))) ; missing features
    (is (not (m/validate common/point-feature-collection
                         {:type "FeatureCollection"
                          :features "not-an-array"}))))

  (testing "Valid LineString FeatureCollections"
    (is (m/validate common/line-string-feature-collection
                    {:type "FeatureCollection"
                     :features [{:type "Feature"
                                 :geometry {:type "LineString" :coordinates [[25.0 60.0] [26.0 61.0]]}}]})))

  (testing "Valid Polygon FeatureCollections"
    (is (m/validate common/polygon-feature-collection
                    {:type "FeatureCollection"
                     :features [{:type "Feature"
                                 :geometry {:type "Polygon"
                                            :coordinates [[[25.0 60.0] [26.0 60.0] [26.0 61.0] [25.0 61.0] [25.0 60.0]]]}}]}))))

(deftest coordinates-edge-cases-test
  (testing "Altitude with Infinity should be rejected"
    (is (not (m/validate common/coordinates [25.0 60.0 Double/POSITIVE_INFINITY])))
    (is (not (m/validate common/coordinates [25.0 60.0 Double/NEGATIVE_INFINITY])))
    (is (not (m/validate common/coordinates [25.0 60.0 Double/NaN]))))

  (testing "Coordinate arrays must have 2 or 3 elements"
    (is (not (m/validate common/coordinates [25.0]))) ; only 1 element
    (is (not (m/validate common/coordinates [25.0 60.0 100.0 200.0]))) ; 4 elements
    (is (not (m/validate common/coordinates []))))

  (testing "Property: coordinates are always vectors of valid numbers"
    (let [samples (mg/sample common/coordinates {:size 50})]
      (is (every? vector? samples))
      (is (every? #(or (= 2 (count %)) (= 3 (count %))) samples))
      (is (every? (fn [[lon lat alt]]
                    (and (number? lon) (number? lat)
                         (or (nil? alt) (number? alt))))
                  samples)))))

(deftest coordinates-error-messages-test
  (testing "Longitude out of bounds error message"
    (let [errors (-> common/coordinates
                     (m/explain [100.0 60.0])
                     (me/humanize))]
      (is (some #(= "Longitude must be between 18.0 and 33.0 degrees (Finland bounds)" %)
                (flatten errors))
          "Should provide clear longitude bounds error")))

  (testing "Latitude out of bounds error message"
    (let [errors (-> common/coordinates
                     (m/explain [25.0 10.0])
                     (me/humanize))]
      (is (some #(= "Latitude must be between 59.0 and 71.0 degrees (Finland bounds)" %)
                (flatten errors))
          "Should provide clear latitude bounds error")))

  (testing "Altitude out of bounds error message"
    (let [errors (-> common/coordinates
                     (m/explain [25.0 60.0 5000.0])
                     (me/humanize))]
      (is (some #(= "Altitude must be between -10,000 and 2,000 meters" %)
                (flatten errors))
          "Should provide clear altitude bounds error")))

  (testing "Infinity error messages"
    (let [lon-inf-errors (-> common/coordinates
                             (m/explain [Double/POSITIVE_INFINITY 60.0])
                             (me/humanize))
          lat-inf-errors (-> common/coordinates
                             (m/explain [25.0 Double/POSITIVE_INFINITY])
                             (me/humanize))]
      (is (some #(= "Longitude cannot be Infinity" %)
                (flatten lon-inf-errors))
          "Should provide clear Infinity error for longitude")
      (is (some #(= "Latitude cannot be Infinity" %)
                (flatten lat-inf-errors))
          "Should provide clear Infinity error for latitude")))

  (testing "NaN error messages"
    (let [lon-nan-errors (-> common/coordinates
                             (m/explain [Double/NaN 60.0])
                             (me/humanize))
          lat-nan-errors (-> common/coordinates
                             (m/explain [25.0 Double/NaN])
                             (me/humanize))]
      (is (some #(= "Longitude cannot be NaN (Not a Number)" %)
                (flatten lon-nan-errors))
          "Should provide clear NaN error for longitude")
      (is (some #(= "Latitude cannot be NaN (Not a Number)" %)
                (flatten lat-nan-errors))
          "Should provide clear NaN error for latitude"))))

(comment
  (mg/generate common/coordinates)
  (mg/sample common/coordinates)
  (clojure.test/test-var #'coordinates-generator-test)
  (clojure.test/test-var #'coordinates-validation-test))
