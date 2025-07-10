(ns lipas.backend.analysis.diversity-simple-test
  "Simple tests for diversity analysis that work without full system setup"
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.search :as search]
            [lipas.backend.osrm :as osrm]))

;;; Pure function tests ;;;

(deftest apply-mins-test
  (testing "Apply mins correctly extracts minimum distances and durations"
    ;; Test with single destination
    (let [single-dest {:car {:distances [[1234.5]] :durations [[180.2]]}
                       :bicycle {:distances [[1500.0]] :durations [[450.0]]}
                       :foot {:distances [[1100.0]] :durations [[792.0]]}}
          result (diversity/apply-mins single-dest)]
      (is (= 1234.5 (-> result :car :distance-m)))
      (is (= 180.2 (-> result :car :duration-s)))
      (is (= 1500.0 (-> result :bicycle :distance-m)))
      (is (= 450.0 (-> result :bicycle :duration-s)))
      (is (= 1100.0 (-> result :foot :distance-m)))
      (is (= 792.0 (-> result :foot :duration-s))))

    ;; Test with multiple destinations - should pick minimum for each mode
    (let [multi-dest {:car {:distances [[2000.0 1234.5 3000.0]]
                            :durations [[300.0 180.2 400.0]]}
                      :bicycle {:distances [[2500.0 1500.0 3500.0]]
                                :durations [[800.0 450.0 1000.0]]}
                      :foot {:distances [[1900.0 1100.0 900.0]]
                             :durations [[1368.0 792.0 648.0]]}}
          result (diversity/apply-mins multi-dest)]
      ;; Each mode takes its minimum value
      (is (= 1234.5 (-> result :car :distance-m)))
      (is (= 180.2 (-> result :car :duration-s)))
      (is (= 1500.0 (-> result :bicycle :distance-m)))
      (is (= 450.0 (-> result :bicycle :duration-s)))
      (is (= 900.0 (-> result :foot :distance-m)))
      (is (= 648.0 (-> result :foot :duration-s))))))

(deftest resolve-dests-test
  (testing "Resolve dests extracts coordinates correctly"
    (let [site {:_source {:search-meta
                          {:location
                           {:simple-geoms
                            {:features
                             [{:geometry {:coordinates [24.9 60.17]
                                          :type "Point"}
                               :type "Feature"}]}}}}}
          on-error (fn [e] (println "Error:" e))]
      (is (= ["24.9,60.17"] (diversity/resolve-dests site on-error))))

    ;; Test error handling
    (let [bad-site {:_source {}}
          errors (atom [])
          on-error (fn [e] (swap! errors conj e))]
      (is (= [] (diversity/resolve-dests bad-site on-error)))
      ;; No error is generated for missing data
      (is (= 0 (count @errors))))))

(deftest bool->num-test
  (testing "Boolean to number conversion"
    (is (= 1 (diversity/bool->num true)))
    (is (= 0 (diversity/bool->num false)))
    (is (nil? (diversity/bool->num nil)))))

(deftest calc-aggs-test
  (testing "Calculate aggregations from population entries"
    (let [pop-entries [{:_source {:vaesto 100 :ika_0_14 20 :ika_15_64 60 :ika_65_ 20}
                        :diversity-index 0.8}
                       {:_source {:vaesto 200 :ika_0_14 40 :ika_15_64 120 :ika_65_ 40}
                        :diversity-index 0.9}
                       {:_source {:vaesto -1 :ika_0_14 -1 :ika_15_64 -1 :ika_65_ -1}
                        :diversity-index 0.7}]
          result (diversity/calc-aggs pop-entries)]
      ;; Should sum valid values and ignore -1
      (is (= 300 (:population result)))
      (is (= 60 (:population-age-0-14 result)))
      (is (= 180 (:population-age-15-64 result)))
      (is (= 60 (:population-age-65- result)))
      (is (number? (:diversity-idx-mean result)))
      (is (number? (:diversity-idx-median result))))))

(deftest process-grid-item-structure-test
  (testing "Process grid item maintains data structure"
    (with-redefs [lipas.backend.analysis.common/get-sports-site-data
                  (fn [_ _ _ _ _]
                    {:hits [{:_id "site-1"
                             :_source {:name "Test Site"
                                       :status "active"
                                       :type {:type-code 1520}
                                       :search-meta
                                       {:location
                                        {:simple-geoms
                                         {:features
                                          [{:geometry {:coordinates [24.95 60.17]
                                                       :type "Point"}
                                            :type "Feature"}]}}}}}]})
                  osrm/get-distances-and-travel-times
                  (fn [_]
                    {:car {:distances [[1000.0]] :durations [[120.0]]}
                     :bicycle {:distances [[1200.0]] :durations [[360.0]]}
                     :foot {:distances [[950.0]] :durations [[684.0]]}})]

      (let [grid-item {:grd_id "test-123"
                       :WKT "POINT (24.9477 60.1678)"
                       :vaesto "100"
                       :extra-field "preserved"}
            mock-search {:client nil :indices {}}
            result (diversity/process-grid-item mock-search 2 grid-item prn)]

        ;; Original fields preserved
        (is (= "test-123" (:grd_id result)))
        (is (= "100" (:vaesto result)))
        (is (= "preserved" (:extra-field result)))

        ;; Sports sites added
        (is (sequential? (:sports-sites result)))
        (is (= 1 (count (:sports-sites result))))

        ;; Check site structure
        (let [site (first (:sports-sites result))]
          (is (= "site-1" (:id site)))
          (is (= 1520 (:type-code site)))
          (is (= "active" (:status site)))
          (is (= 1000.0 (-> site :osrm :car :distance-m)))
          (is (= 120.0 (-> site :osrm :car :duration-s))))))))

(deftest bulk-data-transformation-test
  (testing "Grid items are correctly transformed for bulk indexing"
    (let [grid-items [{:grd_id "grid-1"
                       :sports-sites [{:id "site-1" :type-code 1520}]}
                      {:grd_id "grid-2"
                       :sports-sites [{:id "site-2" :type-code 1530}
                                      {:id "site-3" :type-code 1520}]}]
          bulk-data (search/->bulk "test-index" :grd_id grid-items)]

      ;; Should create 2 entries per grid item (index + data)
      (is (= 4 (count bulk-data)))

      ;; Check first item
      (is (= {:index {:_index "test-index"
                      :_id "grid-1"}}
             (first bulk-data)))
      (is (= (first grid-items)
             (second bulk-data)))

      ;; Check second item
      (is (= {:index {:_index "test-index"
                      :_id "grid-2"}}
             (nth bulk-data 2)))
      (is (= (second grid-items)
             (nth bulk-data 3))))))