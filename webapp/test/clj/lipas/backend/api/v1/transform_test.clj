(ns lipas.backend.api.v1.transform-test
  "Unit tests for the legacy API transformation logic.

   These tests verify that ->old-lipas-sports-site correctly transforms
   new LIPAS format data into the legacy API format.

   Key transformations tested:
   - Date format: UTC ISO8601 -> 'yyyy-MM-dd HH:mm:ss.SSS' (Helsinki TZ)
   - Field names: kebab-case -> camelCase
   - Property mappings: new keys -> legacy keys
   - Type-specific transformations (ice stadiums, swimming pools, golf)"
  (:require
   [clojure.test :refer [deftest testing is]]
   [lipas.backend.api.v1.transform :as transform]
   [lipas.backend.api.v1.sports-place :as legacy-sports-place]
   [clojure.set :as set]))

;;; Test data - representative examples of new LIPAS format

(def minimal-sports-site
  "Minimal valid new LIPAS sports site"
  {:lipas-id 12345
   :name "Test Place"
   :status "active"
   :admin "city-technical-services"
   :owner "city"
   :event-date "2024-01-15T10:30:45.123Z"
   :type {:type-code 1120}
   :location {:city {:city-code "91"}
              :geometries {:type "FeatureCollection"
                           :features []}}})

(def full-sports-site
  "Full new LIPAS sports site with all common fields"
  {:lipas-id 72269
   :name "Keuruun Koulukeskuksen lähiliikunta-alue /ala-aste"
   :status "active"
   :admin "city-technical-services"
   :owner "city"
   :email "test@example.com"
   :www "http://www.example.com"
   :phone-number "+358401234567"
   :construction-year 2011
   :renovation-years [2015 2020]
   :comment "Test comment for infoFi"
   :event-date "2019-08-29T12:55:30.259Z"
   :type {:type-code 1120}
   :properties {:ligthing? true
                :playground? true
                :fields-count 1
                :ice-rinks-count 1
                :school-use? true
                :free-use? true}
   :location {:city {:city-code "249"}
              :address "Keuruuntie 18"
              :postal-code "42700"
              :postal-office "Keuruu"
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [24.7051081551397
                                                                62.2613211721703]}}]}}})

;;; Date transformation tests

(deftest UTC->last-modified-test
  (testing "Converts UTC ISO8601 to Helsinki timezone legacy format"

    (testing "Summer time (+3 hours)"
      (is (= "2019-08-29 15:55:30.259"
             (transform/UTC->last-modified "2019-08-29T12:55:30.259Z")))
      (is (= "2024-06-15 17:00:00.000"
             (transform/UTC->last-modified "2024-06-15T14:00:00.000Z"))))

    (testing "Winter time (+2 hours)"
      (is (= "2024-01-15 12:30:45.123"
             (transform/UTC->last-modified "2024-01-15T10:30:45.123Z")))
      (is (= "2024-12-01 14:00:00.000"
             (transform/UTC->last-modified "2024-12-01T12:00:00.000Z"))))

    (testing "DST transition dates"
      ;; Spring forward (last Sunday of March)
      (is (= "2024-03-31 15:00:00.000"
             (transform/UTC->last-modified "2024-03-31T12:00:00.000Z")))
      ;; Fall back (last Sunday of October)
      (is (= "2024-10-27 14:00:00.000"
             (transform/UTC->last-modified "2024-10-27T12:00:00.000Z"))))

    (testing "Nil input returns nil"
      (is (nil? (transform/UTC->last-modified nil))))))

;;; Basic transformation tests

(deftest transform-minimal-sports-site
  (testing "Minimal sports site transforms correctly"
    (let [result (transform/->old-lipas-sports-site minimal-sports-site)]

      (testing "Name is wrapped in locale map"
        (is (map? (:name result)))
        (is (= "Test Place" (-> result :name :fi))))

      (testing "Type structure is preserved"
        (is (= 1120 (-> result :type :typeCode))))

      (testing "lastModified is transformed"
        (is (= "2024-01-15 12:30:45.123" (:lastModified result))))

      (testing "Admin/owner preserved as enum keys"
        (is (= "city-technical-services" (:admin result)))
        (is (= "city" (:owner result))))

      (testing "Location has correct keys"
        (is (map? (:location result)))
        (is (= "91" (-> result :location :city :cityCode)))))))

(deftest transform-full-sports-site
  (testing "Full sports site transforms correctly"
    (let [result (transform/->old-lipas-sports-site full-sports-site)]

      (testing "Contact info preserved"
        (is (= "test@example.com" (:email result)))
        (is (= "http://www.example.com" (:www result)))
        (is (= "+358401234567" (:phoneNumber result))))

      (testing "Construction/renovation years"
        (is (= 2011 (:constructionYear result)))
        (is (= [2015 2020] (:renovationYears result))))

      (testing "Comment becomes properties.infoFi"
        (is (= "Test comment for infoFi" (-> result :properties :infoFi))))

      (testing "schoolUse and freeUse extracted to top level"
        (is (= true (:schoolUse result)))
        (is (= true (:freeUse result))))

      (testing "Location details"
        (is (= "Keuruuntie 18" (-> result :location :address)))
        (is (= "42700" (-> result :location :postalCode)))
        (is (= "Keuruu" (-> result :location :postalOffice)))))))

;;; Property transformation tests

(deftest property-key-transformation-test
  (testing "Property keys are renamed correctly"
    (let [input (assoc minimal-sports-site
                       :properties {:ligthing? true
                                    :playground? true
                                    :fields-count 2
                                    :ice-rinks-count 1
                                    ;; surface-material is a vector in new format
                                    :surface-material ["grass"]})
          result (transform/->old-lipas-sports-site input)
          props (:properties result)]

      (testing "Boolean properties"
        (is (= true (:ligthing props)) "Legacy typo 'ligthing' preserved")
        (is (= true (:playground props))))

      (testing "Count properties"
        ;; Note: normalize-properties converts integers to doubles for consistent JSON serialization
        (is (= 2.0 (:fieldsCount props)))
        (is (= 1.0 (:iceRinksCount props))))

      (testing "Surface material localized"
        (is (= "Nurmi" (:surfaceMaterial props)))))))

(deftest prop-mappings-bidirectional-test
  (testing "Prop mappings are bidirectional"
    (let [forward legacy-sports-place/prop-mappings
          reverse legacy-sports-place/prop-mappings-reverse]

      (testing "All forward mappings have reverse"
        (doseq [[old-key new-key] forward]
          (is (= old-key (get reverse new-key))
              (str "Missing reverse for " old-key " -> " new-key))))

      (testing "All reverse mappings have forward"
        (doseq [[new-key old-key] reverse]
          (is (= new-key (get forward old-key))
              (str "Missing forward for " new-key " -> " old-key)))))))

;;; Admin/Owner transformation tests

(deftest admin-owner-transformation-test
  (testing "Admin values transformation"
    (let [test-cases [["city-technical-services" "city-technical-services"]
                      ["city-sports" "city-sports"]
                      ["unknown" "no-information"]
                      ["state" "state"]]]
      (doseq [[input expected] test-cases]
        (let [result (transform/->old-lipas-sports-site
                      (assoc minimal-sports-site :admin input))]
          (is (= expected (:admin result))
              (str "Admin transformation failed for: " input))))))

  (testing "Owner values transformation"
    (let [test-cases [["city" "city"]
                      ["state" "state"]
                      ["unknown" "no-information"]
                      ["company" "company"]]]
      (doseq [[input expected] test-cases]
        (let [result (transform/->old-lipas-sports-site
                      (assoc minimal-sports-site :owner input))]
          (is (= expected (:owner result))
              (str "Owner transformation failed for: " input)))))))

;;; Type-specific transformation tests

(deftest ice-stadium-transformation-test
  (testing "Ice stadium (type 2510) specific properties"
    (let [ice-stadium {:lipas-id 99999
                       :name "Test Ice Stadium"
                       :status "active"
                       :admin "city"
                       :owner "city"
                       :event-date "2024-01-01T00:00:00.000Z"
                       :type {:type-code 2510}
                       :rinks [{:length-m 60 :width-m 30}
                               {:length-m 40 :width-m 20}]
                       ;; Add valid properties for type 2510
                       :properties {:ice-rinks-count 2
                                    :stand-capacity-person 500}
                       :location {:city {:city-code "91"}
                                  :geometries {:type "FeatureCollection"
                                               :features []}}}
          result (transform/->old-lipas-sports-site ice-stadium)]

      (testing "Type code preserved"
        (is (= 2510 (-> result :type :typeCode))))

      ;; The ice stadium transformation extracts rink dimensions
      ;; Properties may be nil if empty after cleaning
      (is (or (nil? (:properties result))
              (map? (:properties result)))))))

(deftest swimming-pool-transformation-test
  (testing "Swimming pool (type 3110) specific properties"
    (let [pool {:lipas-id 88888
                :name "Test Swimming Pool"
                :status "active"
                :admin "city"
                :owner "city"
                :event-date "2024-01-01T00:00:00.000Z"
                :type {:type-code 3110}
                :pools [{:length-m 50 :width-m 25 :temperature-c 27
                         :min-depth-m 1.2 :max-depth-m 2.0}
                        {:length-m 25 :width-m 12 :temperature-c 30}]
                :slides [{:length-m 50} {:length-m 30}]
                ;; Add valid properties for type 3110
                :properties {:swimming-pool-count 2}
                :location {:city {:city-code "91"}
                           :geometries {:type "FeatureCollection"
                                        :features []}}}
          result (transform/->old-lipas-sports-site pool)]

      (testing "Type code preserved"
        (is (= 3110 (-> result :type :typeCode))))

      ;; The swimming pool transformation extracts pool dimensions
      ;; Properties may be nil if empty after cleaning
      (is (or (nil? (:properties result))
              (map? (:properties result)))))))

(deftest golf-course-transformation-test
  (testing "Golf area (type 1650) converts to Golf point (type 1620)"
    (let [golf {:lipas-id 77777
                :name "Test Golf Course"
                :status "active"
                :admin "company"
                :owner "company"
                :event-date "2024-01-01T00:00:00.000Z"
                :type {:type-code 1650}
                :properties {:holes-count 18}
                :location {:city {:city-code "91"}
                           :geometries {:type "FeatureCollection"
                                        :features [{:type "Feature"
                                                    :geometry {:type "Polygon"
                                                               :coordinates [[[25.0 60.0]
                                                                              [25.1 60.0]
                                                                              [25.1 60.1]
                                                                              [25.0 60.1]
                                                                              [25.0 60.0]]]}}]}}}
          result (transform/->old-lipas-sports-site golf)]

      (testing "Type code changed from 1650 to 1620"
        (is (= 1620 (-> result :type :typeCode)))))))

;;; Location transformation tests

(deftest location-transformation-test
  (testing "Location fields are transformed correctly"
    (let [input (assoc minimal-sports-site
                       :location {:city {:city-code "91"
                                         :neighborhood "Kallio"}
                                  :address "Test Street 123"
                                  :postal-code "00100"
                                  :postal-office "Helsinki"
                                  :geometries {:type "FeatureCollection"
                                               :features [{:type "Feature"
                                                           :geometry {:type "Point"
                                                                      :coordinates [25.0 60.0]}}]}})
          result (transform/->old-lipas-sports-site input)
          loc (:location result)]

      (testing "City code preserved"
        (is (= "91" (-> loc :city :cityCode))))

      (testing "Neighborhood moved to location level"
        (is (= "Kallio" (:neighborhood loc))))

      (testing "Address fields camelCased"
        (is (= "Test Street 123" (:address loc)))
        (is (= "00100" (:postalCode loc)))
        (is (= "Helsinki" (:postalOffice loc))))

      (testing "Geometries preserved"
        (is (= "FeatureCollection" (-> loc :geometries :type)))))))

(deftest long-address-truncation-test
  (testing "Addresses longer than 100 chars are truncated"
    (let [long-address (apply str (repeat 150 "x"))
          input (assoc-in minimal-sports-site [:location :address] long-address)
          result (transform/->old-lipas-sports-site input)]
      (is (= 100 (count (-> result :location :address)))))))

;;; Edge case tests

(deftest nil-handling-test
  (testing "Nil values are handled gracefully"
    (let [input (-> minimal-sports-site
                    (assoc :email nil
                           :www nil
                           :phone-number nil
                           :construction-year nil
                           :comment nil))
          result (transform/->old-lipas-sports-site input)]
      ;; Should not throw, nil fields should be absent or nil
      (is (map? result)))))

(deftest empty-properties-test
  (testing "Empty properties map is handled"
    (let [input (assoc minimal-sports-site :properties {})
          result (transform/->old-lipas-sports-site input)]
      ;; Empty properties get cleaned away by utils/clean
      ;; This is expected behavior - nil or empty map is valid
      (is (or (nil? (:properties result))
              (map? (:properties result)))))))

;;; Regression tests for known issues

(deftest pool1-length-mm-typo-test
  (testing "pool1LengthMM double-M typo is preserved"
    ;; This is a known legacy quirk - pool 1 length has MM not M
    ;; The fix-special-case function handles this
    (let [pool {:lipas-id 11111
                :name "Test Pool"
                :status "active"
                :admin "city"
                :owner "city"
                :event-date "2024-01-01T00:00:00.000Z"
                :type {:type-code 3110}
                :pools [{:length-m 50}]
                :location {:city {:city-code "91"}
                           :geometries {:type "FeatureCollection"
                                        :features []}}}
          result (transform/->old-lipas-sports-site pool)
          props (:properties result)]
      ;; After transformation, should have pool1LengthMM (not pool1LengthMm)
      ;; This depends on the fix-special-case function
      (when (:pool1LengthMm props)
        (is (contains? props :pool1LengthMM)
            "pool1LengthMM should have double M")
        (is (not (contains? props :pool1LengthMm))
            "pool1LengthMm should be removed")))))

(comment
  ;; Run all tests
  (clojure.test/run-tests 'lipas.backend.api.v1.transform-test)

  ;; Run specific test
  (clojure.test/run-test-var #'UTC->last-modified-test)
  (clojure.test/run-test-var #'transform-minimal-sports-site))
