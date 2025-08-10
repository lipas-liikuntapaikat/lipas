(ns lipas.ui.map.geometry-protection-test
  "Test namespace for geometry protection functionality"
  (:require [cljs.test :refer-macros [deftest is testing]]
            [lipas.ui.map.geometry-protection :as geom-protection]))

(def sample-sports-site
  {:activities {:cycling {:routes [{:id "route-1"
                                    :route-name {:fi "Pyöräreitti 1"
                                                 :en "Cycling Route 1"}
                                    :fids #{"seg-1" "seg-2" "seg-3"}}
                                   {:id "route-2"
                                    :route-name {:fi "Pyöräreitti 2"
                                                 :en "Cycling Route 2"}
                                    :fids #{"seg-2" "seg-4"}}]}
                :hiking {:routes [{:id "route-3"
                                   :route-name {:fi "Vaellusreitti"
                                                :en "Hiking Trail"}
                                   :fids #{"seg-1" "seg-5"}}]}}})

(deftest check-segment-usage-test
  (testing "Finding routes that use a specific segment"
    (let [routes-using-seg-1 (geom-protection/check-segment-usage sample-sports-site "seg-1")
          routes-using-seg-2 (geom-protection/check-segment-usage sample-sports-site "seg-2")
          routes-using-seg-6 (geom-protection/check-segment-usage sample-sports-site "seg-6")]

      (is (= 2 (count routes-using-seg-1)) "seg-1 should be used in 2 routes")
      (is (= 2 (count routes-using-seg-2)) "seg-2 should be used in 2 routes")
      (is (= 0 (count routes-using-seg-6)) "seg-6 should not be used in any routes")

      ;; Check that the correct routes are found
      (is (some #(= "route-1" (:id %)) routes-using-seg-1))
      (is (some #(= "route-3" (:id %)) routes-using-seg-1))
      (is (some #(= :cycling (:activity-k %)) routes-using-seg-1))
      (is (some #(= :hiking (:activity-k %)) routes-using-seg-1)))))

(deftest can-delete-segment-test
  (testing "Checking if segments can be safely deleted"
    (is (not (geom-protection/can-delete-segment? sample-sports-site "seg-1"))
        "seg-1 should not be deletable as it's used in routes")
    (is (not (geom-protection/can-delete-segment? sample-sports-site "seg-2"))
        "seg-2 should not be deletable as it's used in routes")
    (is (geom-protection/can-delete-segment? sample-sports-site "seg-6")
        "seg-6 should be deletable as it's not used in any routes")))

(deftest check-multiple-segments-usage-test
  (testing "Checking multiple segments at once"
    (let [result (geom-protection/check-multiple-segments-usage
                  sample-sports-site
                  ["seg-1" "seg-2" "seg-6"])]
      (is (= 2 (count result)) "Should find 2 segments in use")
      (is (contains? result "seg-1") "Result should contain seg-1")
      (is (contains? result "seg-2") "Result should contain seg-2")
      (is (not (contains? result "seg-6")) "Result should not contain seg-6"))))

(deftest get-affected-routes-details-test
  (testing "Getting detailed information about affected routes"
    (let [details (geom-protection/get-affected-routes-details
                   sample-sports-site
                   "seg-1"
                   :fi)]
      (is (= 2 (count details)) "Should have details for 2 routes")
      (is (some #(= "Pyöräreitti 1" (:route-name %)) details))
      (is (some #(= "Vaellusreitti" (:route-name %)) details))
      (is (some #(= "cycling" (:activity %)) details))
      (is (some #(= "hiking" (:activity %)) details)))))
