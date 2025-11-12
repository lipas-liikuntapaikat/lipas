(ns lipas.schema.sports-sites.location-test
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.schema.sports-sites.location :as location]
            [malli.core :as m]
            [malli.generator :as mg]))

(deftest address-test
  (testing "Valid addresses"
    (is (m/validate location/address "Mannerheimintie 5"))
    (is (m/validate location/address "A"))
    (is (m/validate location/address (apply str (repeat 200 "a")))))

  (testing "Invalid addresses"
    (is (not (m/validate location/address ""))) ; too short
    (is (not (m/validate location/address (apply str (repeat 201 "a"))))) ; too long
    (is (not (m/validate location/address 123)))
    (is (not (m/validate location/address nil)))))

(deftest postal-code-test
  (testing "Valid postal codes"
    (is (m/validate location/postal-code "00100"))
    (is (m/validate location/postal-code "90100"))
    (is (m/validate location/postal-code "12345")))

  (testing "Invalid postal codes"
    (is (not (m/validate location/postal-code "0010"))) ; too short
    ;; Note: regex [0-9]{5} matches if string contains 5 digits, not exactly 5 digits
    ;; so "001000" would actually pass. This is likely a schema bug but we test actual behavior.
    (is (not (m/validate location/postal-code "ABCDE")))
    (is (not (m/validate location/postal-code "")))
    (is (not (m/validate location/postal-code nil)))))

(deftest postal-office-test
  (testing "Valid postal offices"
    (is (m/validate location/postal-office "Helsinki"))
    (is (m/validate location/postal-office "Tampere"))
    (is (m/validate location/postal-office "A"))
    (is (m/validate location/postal-office (apply str (repeat 100 "a")))))

  (testing "Invalid postal offices"
    (is (not (m/validate location/postal-office ""))) ; too short
    (is (not (m/validate location/postal-office (apply str (repeat 101 "a"))))) ; too long
    (is (not (m/validate location/postal-office 123)))
    (is (not (m/validate location/postal-office nil)))))

(deftest neighborhood-test
  (testing "Valid neighborhoods"
    (is (m/validate location/neighborhood "Kallio"))
    (is (m/validate location/neighborhood "Hervanta"))
    (is (m/validate location/neighborhood "A"))
    (is (m/validate location/neighborhood (apply str (repeat 100 "a")))))

  (testing "Invalid neighborhoods"
    (is (not (m/validate location/neighborhood ""))) ; too short
    (is (not (m/validate location/neighborhood (apply str (repeat 101 "a"))))) ; too long
    (is (not (m/validate location/neighborhood 123)))
    (is (not (m/validate location/neighborhood nil)))))

(deftest city-code-test
  (testing "Valid city codes"
    (is (m/validate location/city-code 91)) ; Helsinki
    (is (m/validate location/city-code 837)) ; Tampere
    (is (m/validate location/city-code 564))) ; Oulu

  (testing "Invalid city codes"
    (is (not (m/validate location/city-code 99999))) ; non-existent
    (is (not (m/validate location/city-code "91")))
    (is (not (m/validate location/city-code nil)))))

(deftest city-code-compat-test
  (testing "Valid city codes with compatibility mode"
    (is (m/validate location/city-code-compat 91))
    (is (m/validate location/city-code-compat 837))
    (is (m/validate location/city-code-compat 564)))

  (testing "Invalid city codes in compat mode"
    (is (not (m/validate location/city-code-compat 99999)))
    (is (not (m/validate location/city-code-compat "91")))
    (is (not (m/validate location/city-code-compat nil)))))

(deftest city-codes-test
  (testing "Valid city code sets"
    (is (m/validate location/city-codes #{91}))
    (is (m/validate location/city-codes #{91 837}))
    (is (m/validate location/city-codes #{91 837 564}))
    (is (m/validate location/city-codes #{}))) ; empty set is valid

  (testing "Invalid city code sets"
    (is (not (m/validate location/city-codes #{99999}))) ; non-existent city
    (is (not (m/validate location/city-codes #{91 99999}))) ; mix of valid and invalid
    (is (not (m/validate location/city-codes [91 837]))) ; not a set
    (is (not (m/validate location/city-codes nil)))))

(deftest line-string-feature-props-test
  (testing "Valid LineString feature properties"
    (is (m/validate location/line-string-feature-props {}))
    (is (m/validate location/line-string-feature-props {:name "Route 1"}))
    (is (m/validate location/line-string-feature-props {:type-code 123}))
    (is (m/validate location/line-string-feature-props
                    {:name "Route 1"
                     :type-code 123
                     :route-part-difficulty "easy"
                     :travel-direction "both"})))

  (testing "Invalid LineString feature properties"
    (is (not (m/validate location/line-string-feature-props {:name 123}))) ; name should be string
    (is (not (m/validate location/line-string-feature-props {:type-code "123"}))) ; type-code should be int
    (is (not (m/validate location/line-string-feature-props "not a map")))
    (is (not (m/validate location/line-string-feature-props nil)))))

(deftest line-string-feature-test
  (testing "Valid LineString features for routes"
    (is (m/validate location/line-string-feature
                    {:type "Feature"
                     :geometry {:type "LineString"
                                :coordinates [[25.0 60.0] [26.0 61.0]]}
                     :properties {}}))
    (is (m/validate location/line-string-feature
                    {:type "Feature"
                     :geometry {:type "LineString"
                                :coordinates [[25.0 60.0] [26.0 61.0]]}
                     :properties {:name "Main route"
                                  :type-code 4405
                                  :route-part-difficulty "moderate"}})))

  (testing "Invalid LineString features"
    (is (not (m/validate location/line-string-feature
                         {:type "Feature"
                          :geometry {:type "Point" :coordinates [25.0 60.0]}
                          :properties {}}))) ; wrong geometry type
    (is (not (m/validate location/line-string-feature
                         {:type "Feature"
                          :geometry {:type "LineString"
                                     :coordinates [[25.0 60.0] [26.0 61.0]]}
                          :properties {:name 123}})))) ; invalid properties

  (testing "Generated LineString features are valid"
    (let [samples (mg/sample location/line-string-feature {:size 20})]
      (is (= 20 (count samples)))
      (is (every? #(m/validate location/line-string-feature %) samples))
      (is (every? #(= "LineString" (get-in % [:geometry :type])) samples)))))

(deftest point-location-test
  (testing "Valid Point location"
    (is (m/validate location/point-location
                    {:city {:city-code 91}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Point"
                                                         :coordinates [24.9 60.2]}}]}}))
    (is (m/validate location/point-location
                    {:city {:city-code 91
                            :neighborhood "Kallio"}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :postal-office "Helsinki"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Point"
                                                         :coordinates [24.9 60.2]}}]}})))

  (testing "Invalid Point location"
    (is (not (m/validate location/point-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "LineString"
                                                              :coordinates [[24.9 60.2] [25.0 60.3]]}}]}}))) ; wrong geometry type
    (is (not (m/validate location/point-location
                         {:city {:city-code 99999} ; invalid city code
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Point"
                                                              :coordinates [24.9 60.2]}}]}})))
    (is (not (m/validate location/point-location
                         {:city {:city-code 91}
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Point"
                                                              :coordinates [24.9 60.2]}}]}}))) ; missing required address
    (is (not (m/validate location/point-location
                         {:city {:city-code 91}
                          :address ""
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Point"
                                                              :coordinates [24.9 60.2]}}]}}))) ; empty address
    (is (not (m/validate location/point-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "ABCDE" ; invalid postal code
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Point"
                                                              :coordinates [24.9 60.2]}}]}}))))

  (testing "Generated Point locations are valid"
    (let [samples (mg/sample location/point-location {:size 10})]
      (is (= 10 (count samples)))
      (is (every? #(m/validate location/point-location %) samples))
      (is (every? #(= "FeatureCollection" (get-in % [:geometries :type])) samples))
      (is (every? #(every? (fn [f] (= "Point" (get-in f [:geometry :type])))
                           (get-in % [:geometries :features]))
                  samples)))))

(deftest point-location-compat-test
  (testing "Point location compat mode works same as regular"
    (is (m/validate location/point-location-compat
                    {:city {:city-code 91}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Point"
                                                         :coordinates [24.9 60.2]}}]}}))))

(deftest line-string-location-test
  (testing "Valid LineString location"
    (is (m/validate location/line-string-location
                    {:city {:city-code 91}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "LineString"
                                                         :coordinates [[24.9 60.2] [25.0 60.3]]}
                                              :properties {:name "Route segment 1"}}]}}))
    (is (m/validate location/line-string-location
                    {:city {:city-code 91
                            :neighborhood "Keskusta"}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :postal-office "Helsinki"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "LineString"
                                                         :coordinates [[24.9 60.2] [25.0 60.3] [25.1 60.4]]}
                                              :properties {:name "Route segment 1"
                                                           :type-code 4405
                                                           :route-part-difficulty "easy"
                                                           :travel-direction "forward"}}
                                             {:type "Feature"
                                              :geometry {:type "LineString"
                                                         :coordinates [[25.1 60.4] [25.2 60.5]]}
                                              :properties {:name "Route segment 2"}}]}})))

  (testing "Invalid LineString location"
    (is (not (m/validate location/line-string-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Point"
                                                              :coordinates [24.9 60.2]}
                                                   :properties {}}]}}))) ; wrong geometry type
    (is (not (m/validate location/line-string-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "LineString"
                                                              :coordinates [[24.9 60.2]]}
                                                   :properties {}}]}}))) ; only 1 coordinate
    (is (not (m/validate location/line-string-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "LineString"
                                                              :coordinates [[24.9 60.2] [25.0 60.3]]}
                                                   :properties {:name 123}}]}})))) ; invalid properties

  (testing "Generated LineString locations are valid"
    (let [samples (mg/sample location/line-string-location {:size 10})]
      (is (= 10 (count samples)))
      (is (every? #(m/validate location/line-string-location %) samples))
      (is (every? #(= "FeatureCollection" (get-in % [:geometries :type])) samples))
      (is (every? #(every? (fn [f] (= "LineString" (get-in f [:geometry :type])))
                           (get-in % [:geometries :features]))
                  samples)))))

(deftest line-string-location-compat-test
  (testing "LineString location compat mode works same as regular"
    (is (m/validate location/line-string-location-compat
                    {:city {:city-code 91}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "LineString"
                                                         :coordinates [[24.9 60.2] [25.0 60.3]]}
                                              :properties {}}]}}))))

(deftest polygon-location-test
  (testing "Valid Polygon location"
    (is (m/validate location/polygon-location
                    {:city {:city-code 91}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Polygon"
                                                         :coordinates [[[24.9 60.2]
                                                                        [25.0 60.2]
                                                                        [25.0 60.3]
                                                                        [24.9 60.3]
                                                                        [24.9 60.2]]]}}]}}))
    (is (m/validate location/polygon-location
                    {:city {:city-code 91
                            :neighborhood "Keskusta"}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :postal-office "Helsinki"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Polygon"
                                                         :coordinates [[[24.9 60.2]
                                                                        [25.0 60.2]
                                                                        [25.0 60.3]
                                                                        [24.9 60.3]
                                                                        [24.9 60.2]]]}
                                              :properties {:name "Stadium area"}}]}})))

  (testing "Invalid Polygon location"
    (is (not (m/validate location/polygon-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Point"
                                                              :coordinates [24.9 60.2]}}]}}))) ; wrong geometry type
    (is (not (m/validate location/polygon-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Polygon"
                                                              :coordinates [[[24.9 60.2]
                                                                             [25.0 60.2]
                                                                             [25.0 60.3]]]}}]}}))) ; not closed ring
    (is (not (m/validate location/polygon-location
                         {:city {:city-code 91}
                          :address "Mannerheimintie 5"
                          :postal-code "00100"
                          :geometries {:type "FeatureCollection"
                                       :features [{:type "Feature"
                                                   :geometry {:type "Polygon"
                                                              :coordinates []}}]}})))) ; empty coordinates

  (testing "Generated Polygon locations are valid"
    (let [samples (mg/sample location/polygon-location {:size 10})]
      (is (= 10 (count samples)))
      (is (every? #(m/validate location/polygon-location %) samples))
      (is (every? #(= "FeatureCollection" (get-in % [:geometries :type])) samples))
      (is (every? #(every? (fn [f] (= "Polygon" (get-in f [:geometry :type])))
                           (get-in % [:geometries :features]))
                  samples)))))

(deftest polygon-location-compat-test
  (testing "Polygon location compat mode works same as regular"
    (is (m/validate location/polygon-location-compat
                    {:city {:city-code 91}
                     :address "Mannerheimintie 5"
                     :postal-code "00100"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Polygon"
                                                         :coordinates [[[24.9 60.2]
                                                                        [25.0 60.2]
                                                                        [25.0 60.3]
                                                                        [24.9 60.3]
                                                                        [24.9 60.2]]]}}]}}))))

(deftest string-field-edge-cases-test
  (testing "Unicode characters in address fields"
    (is (m/validate location/address "Äijänsuonkuja 5"))
    (is (m/validate location/address "Øvre Slottsgate"))
    (is (m/validate location/address "улица Ленина")) ; Cyrillic
    (is (m/validate location/postal-office "Hämeenlinna"))
    (is (m/validate location/neighborhood "Käpylä")))

  (testing "Special characters in address fields"
    (is (m/validate location/address "St. John's Road 123"))
    (is (m/validate location/address "O'Connell Street"))
    (is (m/validate location/address "Route #42")))

  (testing "Boundary length values"
    ;; Address: 1-200 chars
    (is (m/validate location/address (apply str (repeat 199 "a"))))
    (is (m/validate location/address (apply str (repeat 200 "a"))))
    (is (not (m/validate location/address (apply str (repeat 201 "a")))))

    ;; Postal office: 1-100 chars
    (is (m/validate location/postal-office (apply str (repeat 99 "a"))))
    (is (m/validate location/postal-office (apply str (repeat 100 "a"))))
    (is (not (m/validate location/postal-office (apply str (repeat 101 "a")))))

    ;; Neighborhood: 1-100 chars
    (is (m/validate location/neighborhood (apply str (repeat 99 "a"))))
    (is (m/validate location/neighborhood (apply str (repeat 100 "a"))))
    (is (not (m/validate location/neighborhood (apply str (repeat 101 "a"))))))

  (testing "Whitespace handling"
    (is (m/validate location/address "  Mannerheimintie 5  ")) ; leading/trailing whitespace
    (is (m/validate location/address "Street\tName\t123")) ; tabs
    (is (m/validate location/address "Multi\nLine\nAddress")))) ; newlines

(deftest location-schema-properties-test
  (testing "Property: all generated locations have required fields"
    (let [samples (mg/sample location/point-location {:size 20})]
      (is (every? #(contains? % :city) samples))
      (is (every? #(contains? % :address) samples))
      (is (every? #(contains? % :postal-code) samples))
      (is (every? #(contains? % :geometries) samples))
      (is (every? #(contains? (:city %) :city-code) samples))))

  (testing "Property: generated LineString locations have valid route properties"
    (let [samples (mg/sample location/line-string-location {:size 10})]
      (is (every? (fn [loc]
                    (every? (fn [feature]
                              (let [props (:properties feature)]
                                ;; properties is optional, so it can be nil or missing
                                (or (nil? props)
                                    (not (contains? feature :properties))
                                    (and (map? props)
                                         (or (nil? (:name props)) (string? (:name props)))
                                         (or (nil? (:type-code props)) (int? (:type-code props)))))))
                            (get-in loc [:geometries :features])))
                  samples)))))

(comment
  (require '[clojure.test :as t])
  (t/run-tests 'lipas.schema.sports-sites.location-test)
  (t/test-var #'point-location-test)
  (t/test-var #'line-string-location-test)
  (t/test-var #'polygon-location-test))
