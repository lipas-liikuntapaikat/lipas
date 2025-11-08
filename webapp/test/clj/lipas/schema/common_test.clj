(ns lipas.schema.common-test
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.schema.common :as common]
            [malli.core :as m]
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
    (is (not (m/validate common/coordinates [25.0 60.0 -2000.0])))
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
        (is (<= -1500.0 alt 2000.0) (str "Altitude " alt " out of bounds"))
        (is (not (Double/isInfinite alt)))
        (is (not (Double/isNaN alt)))))))

(comment
  (mg/generate common/coordinates)
  (mg/sample common/coordinates)
  (clojure.test/test-var #'coordinates-generator-test)
  (clojure.test/test-var #'coordinates-validation-test))
