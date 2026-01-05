(ns lipas.backend.core-enrichment-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [lipas.backend.core :as core]))

(def sample-sports-site-with-activities
  {:lipas-id 123456
   :name "Test Sports Site"
   :type {:type-code 101}
   :location {:city {:city-code 91}
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :id "feature-1"
                                       :geometry {:type "Point"
                                                  :coordinates [25.0 60.0]}}]}}
   :activities {:cycling {:name "Cycling routes"}
                :swimming {:name "Swimming"}
                :outdoor-recreation-routes {:name "Hiking trails"}}})

(def sample-sports-site-empty-activities
  {:lipas-id 123457
   :name "Test Sports Site 2"
   :type {:type-code 102}
   :location {:city {:city-code 91}
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :id "feature-1"
                                       :geometry {:type "Point"
                                                  :coordinates [25.0 60.0]}}]}}
   :activities {}})

(def sample-sports-site-no-activities
  {:lipas-id 123458
   :name "Test Sports Site 3"
   :type {:type-code 103}
   :location {:city {:city-code 91}
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :id "feature-1"
                                       :geometry {:type "Point"
                                                  :coordinates [25.0 60.0]}}]}}})

(deftest enrich-adds-activities-array-test
  (testing "Enrichment extracts activity keys to search-meta.activities"
    (let [enriched (core/enrich sample-sports-site-with-activities)]

      (testing "search-meta.activities exists"
        (is (contains? (:search-meta enriched) :activities)))

      (testing "search-meta.activities contains all activity keys"
        (let [activities (get-in enriched [:search-meta :activities])
              expected #{:cycling :swimming :outdoor-recreation-routes}]
          (is (set? (set activities)))
          (is (= expected (set activities)))))

      (testing "search-meta.activities is a vector for ES keyword array"
        (let [activities (get-in enriched [:search-meta :activities])]
          (is (vector? activities))))))

  (testing "Empty activities map produces empty array"
    (let [enriched (core/enrich sample-sports-site-empty-activities)
          activities (get-in enriched [:search-meta :activities])]

      (is (or (nil? activities)
              (empty? activities))
          "Empty activities map should produce nil or empty array")))

  (testing "Nil activities produces nil or empty array"
    (let [enriched (core/enrich sample-sports-site-no-activities)
          activities (get-in enriched [:search-meta :activities])]

      (is (or (nil? activities)
              (empty? activities))
          "Nil activities should produce nil or empty array")))

  (testing "Activities field remains in enriched document"
    (let [enriched (core/enrich sample-sports-site-with-activities)]
      (is (= (:activities sample-sports-site-with-activities)
             (:activities enriched))
          "Original activities field should be preserved in _source"))))
