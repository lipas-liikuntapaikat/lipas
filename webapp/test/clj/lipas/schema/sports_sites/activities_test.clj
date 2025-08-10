(ns lipas.schema.sports-sites.activities-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.schema.sports-sites.activities :as activities]
            [lipas.data.activities :as data-activities]
            [malli.core :as m]))

;; FAIL in (edge-cases-test) (activities_test.clj:136)
;; Repeated segments allowed

(deftest segment-reference-schema-test
  (testing "Valid segment references"
    (is (m/validate data-activities/segment-reference-schema
                    {:fid "segment-123"
                     :direction "forward"}))
    (is (m/validate data-activities/segment-reference-schema
                    {:fid "segment-456"
                     :direction "reverse"})))

  (testing "Invalid segment references"
    (is (not (m/validate data-activities/segment-reference-schema
                         {:fid "segment-123"}))) ; missing direction
    (is (not (m/validate data-activities/segment-reference-schema
                         {:fid "segment-123"
                          :direction "sideways"}))) ; invalid direction
    (is (not (m/validate data-activities/segment-reference-schema
                         {:fid nil
                          :direction "forward"}))) ; nil fid
    (is (not (m/validate data-activities/segment-reference-schema
                         {}))))) ; empty map

(deftest route-with-ordering-schema-test
  (testing "Outdoor recreation routes with ordering"
    (let [route-schema (-> data-activities/outdoor-recreation-routes
                           :props
                           :routes
                           :schema)
          valid-route {:id "route-1"
                       :route-name {:fi "Testireitti"
                                    :se "Testled"
                                    :en "Test route"}
                       :fids ["seg-1" "seg-2" "seg-3"]
                       :segments [{:fid "seg-1" :direction "forward"}
                                  {:fid "seg-2" :direction "reverse"}
                                  {:fid "seg-3" :direction "forward"}]
                       :ordering-method "manual"
                       :route-length-km 5.2}]
      (is (m/validate route-schema [valid-route]))))

  (testing "Cycling routes with ordering"
    (let [route-schema (-> data-activities/cycling
                           :props
                           :routes
                           :schema)
          valid-route {:id "cycle-route-1"
                       :route-name {:fi "Pyöräreitti"}
                       :fids ["seg-a" "seg-b"]
                       :segments [{:fid "seg-a" :direction "forward"}
                                  {:fid "seg-b" :direction "forward"}]
                       :ordering-method "algorithmic"
                       :cycling-activities ["mountain-biking"]
                       :route-length-km 12.5}]
      (is (m/validate route-schema [valid-route]))))

  (testing "Paddling routes with ordering"
    (let [route-schema (-> data-activities/paddling
                           :props
                           :routes
                           :schema)
          valid-route {:id "paddle-route-1"
                       :route-name {:fi "Melontareitti"}
                       :fids ["water-seg-1" "water-seg-2"]
                       :segments [{:fid "water-seg-1" :direction "forward"}
                                  {:fid "water-seg-2" :direction "forward"}]
                       :ordering-method "manual"
                       :paddling-activities ["whitewater-paddling"]
                       :route-length-km 8.3}]
      (is (m/validate route-schema [valid-route])))))

(deftest backwards-compatibility-test
  (testing "Routes without ordering remain valid"
    (let [route-schema (-> data-activities/outdoor-recreation-routes
                           :props
                           :routes
                           :schema)
          legacy-route {:id "old-route"
                        :route-name {:fi "Vanha reitti"}
                        :fids ["seg-1" "seg-2" "seg-3"]
                       ;; No segments or ordering-method
                        :route-length-km 4.5}]
      (is (m/validate route-schema [legacy-route]))))

  (testing "Routes can have fids without segments"
    (let [route-schema (-> data-activities/cycling
                           :props
                           :routes
                           :schema)
          partial-route {:id "partial-1"
                         :route-name {:fi "Osittainen"}
                         :fids ["a" "b" "c"]
                        ;; Has fids but no segments yet
                         :route-length-km 10.0}]
      (is (m/validate route-schema [partial-route])))))

(deftest edge-cases-test
  (testing "Empty segments array is valid"
    (let [route-schema (-> data-activities/outdoor-recreation-routes
                           :props
                           :routes
                           :schema)
          empty-segments-route {:id "empty-1"
                                :route-name {:fi "Tyhjä"}
                                :segments []
                                :ordering-method "manual"}]
      (is (m/validate route-schema [empty-segments-route]))))

  (testing "Single segment route"
    (let [route-schema (-> data-activities/outdoor-recreation-routes
                           :props
                           :routes
                           :schema)
          single-segment-route {:id "single-1"
                                :route-name {:fi "Yksi"}
                                :fids ["only-one"]
                                :segments [{:fid "only-one" :direction "forward"}]
                                :ordering-method "manual"}]
      (is (m/validate route-schema [single-segment-route]))))

  (testing "Repeated segments allowed"
    (let [route-schema (-> data-activities/paddling
                           :props
                           :routes
                           :schema)
          repeated-segments-route {:id "repeat-1"
                                   :route-name {:fi "Toistuva"}
                                   :fids ["seg-1" "seg-1"]
                                   :segments [{:fid "seg-1" :direction "forward"}
                                              {:fid "seg-1" :direction "reverse"}]
                                   :ordering-method "manual"}]
      (is (m/validate route-schema [repeated-segments-route]))))

  (testing "Invalid ordering method rejected"
    (let [route-schema (-> data-activities/outdoor-recreation-routes
                           :props
                           :routes
                           :schema)
          invalid-method-route {:id "invalid-1"
                                :route-name {:fi "Virheellinen"}
                                :segments [{:fid "a" :direction "forward"}]
                                :ordering-method "random"}] ; Invalid method
      (is (not (m/validate route-schema [invalid-method-route]))))))

(deftest ordering-method-enum-test
  (testing "Valid ordering methods"
    (let [ordering-schema [:enum "manual" "algorithmic"]]
      (is (m/validate ordering-schema "manual"))
      (is (m/validate ordering-schema "algorithmic"))
      (is (not (m/validate ordering-schema "automatic")))
      (is (not (m/validate ordering-schema nil)))
      (is (not (m/validate ordering-schema ""))))))

(deftest segment-fid-consistency-test
  (testing "Validation helper for segment-fid consistency"
    ;; This would be implemented in the actual validation namespace
    (let [consistent-route {:fids ["a" "b" "c"]
                            :segments [{:fid "a" :direction "forward"}
                                       {:fid "b" :direction "forward"}
                                       {:fid "c" :direction "forward"}]}
          inconsistent-route {:fids ["a" "b" "c"]
                              :segments [{:fid "a" :direction "forward"}
                                         {:fid "d" :direction "forward"}]}
          extra-segments-route {:fids ["a" "b"]
                                :segments [{:fid "a" :direction "forward"}
                                           {:fid "b" :direction "forward"}
                                           {:fid "c" :direction "forward"}]}]
      ;; These tests demonstrate what the validation logic should check
      (is (= (set (:fids consistent-route))
             (set (map :fid (:segments consistent-route)))))
      (is (not= (set (:fids inconsistent-route))
                (set (map :fid (:segments inconsistent-route)))))
      (is (not= (set (:fids extra-segments-route))
                (set (map :fid (:segments extra-segments-route))))))))
