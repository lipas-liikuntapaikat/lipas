(ns lipas.schema-test
  (:require [clojure.spec.alpha :refer [valid?]]
            [clojure.test :refer [deftest is testing]]
            [lipas.schema.core]
            [lipas.schema.common :as common]
            [malli.core :as m]
            [malli.generator :as mg]))

(deftest email-validity-test
  (testing "valid emails"
    (is (valid? :lipas/email "a@b.co"))
    (is (valid? :lipas/email "ääkkö@set.com")))
  (testing "invalid emails"
    (is (not (valid? :lipas/email "a..b@.com")))
    (is (not (valid? :lipas/email "ab@..com")))
    (is (not (valid? :lipas/email "ab@...com")))
    (is (not (valid? :lipas/email "ab@...........................com")))
    (is (not (valid? :lipas/email "@.com")))
    (is (not (valid? :lipas/email "a@")))
    (is (not (valid? :lipas/email "a@b")))
    (is (not (valid? :lipas/email "@b")))
    (is (not (valid? :lipas/email "@")))
    (is (not (valid? :lipas/email "a.b.com")))))

(deftest polygon-schema-test
  (testing "valid Polygon geometries"
    ;; Simple square polygon (exterior ring only)
    (is (m/validate common/polygon-geometry
                    {:type "Polygon"
                     :coordinates [[[25.0 65.0]
                                    [26.0 65.0]
                                    [26.0 66.0]
                                    [25.0 66.0]
                                    [25.0 65.0]]]}))

    ;; Polygon with altitude
    (is (m/validate common/polygon-geometry
                    {:type "Polygon"
                     :coordinates [[[25.0 65.0 100.0]
                                    [26.0 65.0 100.0]
                                    [26.0 66.0 100.0]
                                    [25.0 66.0 100.0]
                                    [25.0 65.0 100.0]]]}))

    ;; Polygon with hole (2 rings: exterior + interior)
    (is (m/validate common/polygon-geometry
                    {:type "Polygon"
                     :coordinates [;; Exterior ring
                                   [[25.0 65.0]
                                    [27.0 65.0]
                                    [27.0 67.0]
                                    [25.0 67.0]
                                    [25.0 65.0]]
                                   ;; Interior ring (hole)
                                   [[25.5 65.5]
                                    [26.5 65.5]
                                    [26.5 66.5]
                                    [25.5 66.5]
                                    [25.5 65.5]]]})))

  (testing "invalid Polygon geometries - RFC 7946 linear ring requirements"
    ;; Not a linear ring - only 3 positions (need 4+)
    (is (not (m/validate common/polygon-geometry
                         {:type "Polygon"
                          :coordinates [[[25.0 65.0]
                                         [26.0 65.0]
                                         [25.0 66.0]]]})))

    ;; Not closed - first != last
    (is (not (m/validate common/polygon-geometry
                         {:type "Polygon"
                          :coordinates [[[25.0 65.0]
                                         [26.0 65.0]
                                         [26.0 66.0]
                                         [25.0 66.0]]]})))

    ;; Empty coordinates
    (is (not (m/validate common/polygon-geometry
                         {:type "Polygon"
                          :coordinates []})))

    ;; Empty ring
    (is (not (m/validate common/polygon-geometry
                         {:type "Polygon"
                          :coordinates [[]]})))

    ;; Wrong type
    (is (not (m/validate common/polygon-geometry
                         {:type "LineString"
                          :coordinates [[[25.0 65.0]
                                         [26.0 65.0]
                                         [26.0 66.0]
                                         [25.0 66.0]
                                         [25.0 65.0]]]})))

    ;; Missing coordinates
    (is (not (m/validate common/polygon-geometry
                         {:type "Polygon"})))

    ;; Point out of bounds
    (is (not (m/validate common/polygon-geometry
                         {:type "Polygon"
                          :coordinates [[[0.0 65.0] ; out of bounds!
                                         [26.0 65.0]
                                         [26.0 66.0]
                                         [25.0 66.0]
                                         [0.0 65.0]]]})))

    ;; Second ring not closed
    (is (not (m/validate common/polygon-geometry
                         {:type "Polygon"
                          :coordinates [;; Exterior ring (valid)
                                        [[25.0 65.0]
                                         [27.0 65.0]
                                         [27.0 67.0]
                                         [25.0 67.0]
                                         [25.0 65.0]]
                                        ;; Interior ring (invalid - not closed)
                                        [[25.5 65.5]
                                         [26.5 65.5]
                                         [26.5 66.5]
                                         [25.5 66.5]]]}))))

  (testing "Polygon geometry generator produces valid data"
    (let [generated (mg/generate common/polygon-geometry {:size 10 :seed 42})]
      (is (m/validate common/polygon-geometry generated))
      (is (= "Polygon" (:type generated)))
      (is (vector? (:coordinates generated)))
      (is (>= (count (:coordinates generated)) 1))
      (is (every? vector? (:coordinates generated)))
      ;; Check each ring is a linear ring (4+ positions, first = last)
      (doseq [ring (:coordinates generated)]
        (is (>= (count ring) 4))
        (is (= (first ring) (last ring)))))))

(deftest linestring-schema-test
  (testing "valid LineString geometries"
    (is (m/validate common/line-string-geometry
                    {:type "LineString"
                     :coordinates [[25.0 65.0] [26.0 66.0]]}))
    (is (m/validate common/line-string-geometry
                    {:type "LineString"
                     :coordinates [[25.0 65.0] [26.0 66.0] [27.0 67.0]]})) ; 3+ points
    (is (m/validate common/line-string-geometry
                    {:type "LineString"
                     :coordinates [[25.0 65.0 100.0] [26.0 66.0 200.0]]}))) ; with altitude

  (testing "invalid LineString geometries - RFC 7946 requires 2+ positions"
    (is (not (m/validate common/line-string-geometry
                         {:type "LineString"
                          :coordinates [[25.0 65.0]]}))) ; only 1 position
    (is (not (m/validate common/line-string-geometry
                         {:type "LineString"
                          :coordinates []}))) ; empty
    (is (not (m/validate common/line-string-geometry
                         {:type "Point"
                          :coordinates [[25.0 65.0] [26.0 66.0]]}))) ; wrong type
    (is (not (m/validate common/line-string-geometry
                         {:type "LineString"}))) ; missing coordinates
    (is (not (m/validate common/line-string-geometry
                         {:type "LineString"
                          :coordinates [[0.0 65.0] [26.0 66.0]]}))) ; point out of bounds
    (is (not (m/validate common/line-string-geometry
                         {:type "LineString"
                          :coordinates [[25.0 65.0] [26.0 0.0]]})))) ; point out of bounds

  (testing "LineString geometry generator produces valid data"
    (let [generated (mg/generate common/line-string-geometry {:size 10 :seed 42})]
      (is (m/validate common/line-string-geometry generated))
      (is (= "LineString" (:type generated)))
      (is (vector? (:coordinates generated)))
      (is (>= (count (:coordinates generated)) 2))
      (is (every? vector? (:coordinates generated))))))

(deftest point-schema-test
  (testing "valid Point geometries"
    (is (m/validate common/point-geometry
                    {:type "Point"
                     :coordinates [25.0 65.0]}))
    (is (m/validate common/point-geometry
                    {:type "Point"
                     :coordinates [25.0 65.0 100.0]})) ; with altitude
    (is (m/validate common/point-geometry
                    {:type "Point"
                     :coordinates [18.0 59.0]}))) ; min bounds

  (testing "invalid Point geometries"
    (is (not (m/validate common/point-geometry
                         {:type "Point"
                          :coordinates [0.0 65.0]}))) ; longitude out of bounds
    (is (not (m/validate common/point-geometry
                         {:type "Point"
                          :coordinates [25.0 0.0]}))) ; latitude out of bounds
    (is (not (m/validate common/point-geometry
                         {:type "LineString"
                          :coordinates [25.0 65.0]}))) ; wrong type
    (is (not (m/validate common/point-geometry
                         {:type "Point"
                          :coordinates []}))) ; empty coordinates
    (is (not (m/validate common/point-geometry
                         {:type "Point"})))) ; missing coordinates

  (testing "Point geometry generator produces valid data"
    (let [generated (mg/generate common/point-geometry {:size 10 :seed 42})]
      (is (m/validate common/point-geometry generated))
      (is (= "Point" (:type generated)))
      (is (vector? (:coordinates generated)))
      (is (>= (count (:coordinates generated)) 2)))))
