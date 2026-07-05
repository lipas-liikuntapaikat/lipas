(ns lipas.backend.analysis.diversity-simple-test
  "Simple tests for diversity analysis that work without full system setup"
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.gis :as gis]
            [lipas.backend.search :as search]
            [lipas.schema.diversity :as diversity-schema]
            [malli.core :as m]
            [malli.error :as me]
            [muuntaja.core :as muuntaja]))

;;; Pure function tests ;;;

(deftest min-finite-test
  (testing "Minimum over non-nil values"
    (is (= 3 (diversity/min-finite [5 nil 3])))
    (is (= 900.0 (diversity/min-finite [1900.0 1100.0 900.0])))
    (is (= 1234.5 (diversity/min-finite [1234.5]))))
  (testing "Nil when no finite values (unroutable destinations)"
    (is (nil? (diversity/min-finite [nil nil])))
    (is (nil? (diversity/min-finite [])))))

(deftest site-osrm-mins-test
  (testing "Per-profile minimums over the site's matrix columns"
    (let [tables {:car {:distances [[2000.0 1234.5 3000.0]]
                        :durations [[300.0 180.2 400.0]]}
                  :bicycle {:distances [[2500.0 1500.0 3500.0]]
                            :durations [[800.0 450.0 1000.0]]}
                  :foot {:distances [[1900.0 1100.0 900.0]]
                         :durations [[1368.0 792.0 648.0]]}}
          result (diversity/site-osrm-mins tables 0 [0 1 2])]
      (is (= 1234.5 (-> result :car :distance-m)))
      (is (= 180.2 (-> result :car :duration-s)))
      (is (= 1500.0 (-> result :bicycle :distance-m)))
      (is (= 450.0 (-> result :bicycle :duration-s)))
      (is (= 900.0 (-> result :foot :distance-m)))
      (is (= 648.0 (-> result :foot :duration-s)))))

  (testing "Only the site's own columns are considered"
    (let [tables {:car {:distances [[100.0 900.0 50.0]]
                        :durations [[10.0 90.0 5.0]]}}
          result (diversity/site-osrm-mins tables 0 [0 1])]
      (is (= 100.0 (-> result :car :distance-m)))
      (is (= 10.0 (-> result :car :duration-s)))))

  (testing "Row selected by cell index"
    (let [tables {:car {:distances [[100.0] [200.0]]
                        :durations [[10.0] [20.0]]}}]
      (is (= 200.0 (-> (diversity/site-osrm-mins tables 1 [0]) :car :distance-m)))))

  (testing "Min distance and min duration are independent (old apply-mins semantics)"
    (let [tables {:car {:distances [[2000.0 1000.0]]
                        :durations [[100.0 500.0]]}}
          result (diversity/site-osrm-mins tables 0 [0 1])]
      (is (= 1000.0 (-> result :car :distance-m)))
      (is (= 100.0 (-> result :car :duration-s)))))

  (testing "Unroutable destinations (nils) don't crash and yield nil mins"
    (let [tables {:car {:distances [[nil nil]]
                        :durations [[nil nil]]}}
          result (diversity/site-osrm-mins tables 0 [0 1])]
      (is (= {:distance-m nil :duration-s nil} (:car result)))))

  (testing "No profile data yields nil"
    (is (nil? (diversity/site-osrm-mins {} 0 [0])))))

(deftest prepare-site-entry-test
  (testing "Point site yields dest strings and numeric coords"
    (let [site {:_id "site-1"
                :_source {:status "active"
                          :type {:type-code 1520}
                          :search-meta
                          {:location
                           {:simple-geoms
                            {:type "FeatureCollection"
                             :features
                             [{:type "Feature"
                               :geometry {:type "Point"
                                          :coordinates [24.9 60.17]}}]}}}}}
          entry (diversity/prepare-site-entry site prn)]
      (is (= "site-1" (:id entry)))
      (is (= 1520 (:type-code entry)))
      (is (= "active" (:status entry)))
      (is (= ["24.9,60.17"] (:dests entry)))
      (is (= [[24.9 60.17]] (:coords entry)))))

  (testing "Duplicate vertices are deduped"
    (let [site {:_id "site-2"
                :_source {:status "active"
                          :type {:type-code 4401}
                          :search-meta
                          {:location
                           {:simple-geoms
                            {:type "FeatureCollection"
                             :features
                             [{:type "Feature"
                               :geometry {:type "LineString"
                                          :coordinates [[24.9 60.17]
                                                        [24.91 60.18]
                                                        [24.9 60.17]]}}]}}}}}
          entry (diversity/prepare-site-entry site prn)]
      (is (= ["24.9,60.17" "24.91,60.18"] (:dests entry)))))

  (testing "Site without usable geometry yields nil"
    (let [errors (atom [])]
      (is (nil? (diversity/prepare-site-entry {:_id "broken" :_source {}}
                                              #(swap! errors conj %)))))))

(deftest assign-sites-test
  (let [cell [24.94 60.16]
        near-site {:coords [[24.945 60.161]]} ; ~300 m
        far-site {:coords [[25.05 60.16]]} ; ~6 km
        multi-vertex-site {:coords [[25.05 60.16] ; far vertex
                                    [24.95 60.162]]}] ; near vertex

    (testing "Site within radius is assigned"
      (is (= [[0]] (diversity/assign-sites [cell] [near-site] 2250))))

    (testing "Site outside radius is not assigned"
      (is (= [[]] (diversity/assign-sites [cell] [far-site] 2250))))

    (testing "One near vertex suffices"
      (is (= [[0]] (diversity/assign-sites [cell] [multi-vertex-site] 2250))))

    (testing "Assignments align with cell order"
      (let [far-cell [26.0 61.0]]
        (is (= [[0] []]
               (diversity/assign-sites [cell far-cell] [near-site] 2250)))))

    (testing "Radius boundary respects haversine distance"
      (let [d (gis/haversine [24.94 60.16] [24.945 60.161])]
        (is (= [[0]] (diversity/assign-sites [cell] [near-site] (+ d 1))))
        (is (= [[]] (diversity/assign-sites [cell] [near-site] (- d 1))))))))

(deftest index-dests-test
  (let [entries [{:dests ["1,1" "2,2"]}
                 {:dests ["2,2" "3,3"]}
                 {:dests ["4,4"]}]]

    (testing "Shared destinations get a single column"
      (let [[dests site->cols] (diversity/index-dests entries [0 1 2])]
        (is (= ["1,1" "2,2" "3,3" "4,4"] dests))
        (is (= {0 [0 1] 1 [1 2] 2 [3]} site->cols))))

    (testing "Only requested sites are indexed"
      (let [[dests site->cols] (diversity/index-dests entries [2])]
        (is (= ["4,4"] dests))
        (is (= {2 [0]} site->cols))))

    (testing "Empty input"
      (is (= [[] {}] (diversity/index-dests entries []))))))

(deftest merge-table-chunks-test
  (testing "Columns of consecutive chunks are concatenated per row"
    (let [merged (diversity/merge-table-chunks
                  [{:car {:distances [[1 2] [3 4]] :durations [[10 20] [30 40]]}}
                   {:car {:distances [[5] [6]] :durations [[50] [60]]}}])]
      (is (= [[1 2 5] [3 4 6]] (-> merged :car :distances)))
      (is (= [[10 20 50] [30 40 60]] (-> merged :car :durations)))))

  (testing "Single chunk passes through"
    (let [merged (diversity/merge-table-chunks
                  [{:foot {:distances [[1.0]] :durations [[2.0]]}}])]
      (is (= [[1.0]] (-> merged :foot :distances)))
      (is (= [[2.0]] (-> merged :foot :durations)))))

  (testing "Profile missing from any chunk is dropped entirely"
    (let [merged (diversity/merge-table-chunks
                  [{:car {:distances [[1]] :durations [[10]]}
                    :foot {:distances [[1]] :durations [[10]]}}
                   {:car {:distances [[2]] :durations [[20]]}}])]
      (is (= #{:car} (set (keys merged))))))

  (testing "All profiles missing yields empty map"
    (is (= {} (diversity/merge-table-chunks [{}])))))

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

;;; Schema validation tests ;;;

(def valid-analysis-area-fcoll
  {:type "FeatureCollection"
   :features [{:type "Feature"
               :geometry {:type "Point"
                          :coordinates [24.9 60.1]}}]})

(defn make-req [categories & {:as overrides}]
  (merge {:categories categories
          :analysis-area-fcoll valid-analysis-area-fcoll}
         overrides))

(deftest schema-accepts-various-collection-types-test
  (testing "Schema accepts vectors for categories and type-codes"
    (is (m/validate diversity-schema/diversity-indices-req
                    (make-req [{:name "Cat" :type-codes [1520 1530] :factor 1}]))))

  (testing "Schema accepts lists for type-codes (transit deserialization)"
    (is (m/validate diversity-schema/diversity-indices-req
                    (make-req [{:name "Cat" :type-codes '(1520 1530) :factor 1}]))))

  (testing "Schema accepts lazy seqs for type-codes"
    (is (m/validate diversity-schema/diversity-indices-req
                    (make-req [{:name "Cat" :type-codes (map identity [1520 1530]) :factor 1}]))))

  (testing "Schema accepts list of categories"
    (is (m/validate diversity-schema/diversity-indices-req
                    (make-req (list {:name "Cat" :type-codes [1520] :factor 1})))))

  (testing "Schema rejects empty type-codes"
    (is (not (m/validate diversity-schema/diversity-indices-req
                         (make-req [{:name "Cat" :type-codes [] :factor 1}])))))

  (testing "Schema rejects empty categories"
    (is (not (m/validate diversity-schema/diversity-indices-req
                         (make-req [])))))

  (testing "Schema accepts optional settings"
    (is (m/validate diversity-schema/diversity-indices-req
                    (make-req [{:name "Cat" :type-codes [1520] :factor 1}]
                              :max-distance-m 1000
                              :analysis-radius-km 3
                              :distance-mode "euclid")))))

(deftest error-response-serializable-test
  (testing "Validation error response is transit-serializable"
    (let [invalid-data (make-req [{:name "Cat" :type-codes [] :factor 1}])
          error-body {:error (-> (m/explain diversity-schema/diversity-indices-req invalid-data)
                                 me/humanize)}
          m-instance (muuntaja/create)]
      (is (some? (muuntaja/encode m-instance "application/transit+json" error-body))))))

;;; Custom categories in calc-indices ;;;

(deftest calc-indices-with-custom-categories-test
  (let [pop-data [{:_source {:vaesto "200" :ika_0_14 "30" :ika_15_64 "140" :ika_65_ "30"}
                   :sports-sites [{:type-code 1520 :distance-m 300 :status true}
                                  {:type-code 1530 :distance-m 500 :status true}
                                  {:type-code 2210 :distance-m 700 :status true}]}]
        opts {:max-distance-m 800
              :statuses #{true}}]

    (testing "Single custom category with one matching type"
      (let [categories (diversity/prepare-categories
                         [{:name "Ice rinks" :type-codes [1520] :factor 1}])
            result (first (#'diversity/calc-indices pop-data categories opts))]
        (is (= 1 (get-in result [:categories "Ice rinks"])))
        (is (= 1 (:diversity-index result)))))

    (testing "Multiple custom categories"
      (let [categories (diversity/prepare-categories
                         [{:name "Ice" :type-codes [1520 1530] :factor 1}
                          {:name "Swim" :type-codes [2210] :factor 1}])
            result (first (#'diversity/calc-indices pop-data categories opts))]
        (is (= 1 (get-in result [:categories "Ice"])))
        (is (= 1 (get-in result [:categories "Swim"])))
        (is (= 2 (:diversity-index result)))))

    (testing "Category with no matching sites scores 0"
      (let [categories (diversity/prepare-categories
                         [{:name "Missing" :type-codes [4530] :factor 1}])
            result (first (#'diversity/calc-indices pop-data categories opts))]
        (is (= 0 (get-in result [:categories "Missing"])))
        (is (= 0 (:diversity-index result)))))

    (testing "Custom factor multiplies the score"
      (let [categories (diversity/prepare-categories
                         [{:name "Weighted" :type-codes [1520] :factor 3}])
            result (first (#'diversity/calc-indices pop-data categories opts))]
        (is (= 3 (get-in result [:categories "Weighted"])))
        (is (= 3 (:diversity-index result)))))

    (testing "Sites beyond max-distance-m are excluded"
      (let [categories (diversity/prepare-categories
                         [{:name "Near" :type-codes [1520] :factor 1}
                          {:name "Far" :type-codes [2210] :factor 1}])
            result (first (#'diversity/calc-indices pop-data categories
                                                    (assoc opts :max-distance-m 400)))]
        (is (= 1 (get-in result [:categories "Near"])))
        (is (= 0 (get-in result [:categories "Far"])))
        (is (= 1 (:diversity-index result)))))

    (testing "prepare-categories works with lists and lazy seqs"
      (let [cats-from-list (diversity/prepare-categories
                             (list {:name "Cat" :type-codes '(1520 1530) :factor 1}))
            cats-from-seq (diversity/prepare-categories
                            [{:name "Cat" :type-codes (map identity [1520 1530]) :factor 1}])]
        (is (set? (:type-codes (first cats-from-list))))
        (is (set? (:type-codes (first cats-from-seq))))
        (is (= #{1520 1530} (:type-codes (first cats-from-list))))
        (is (= #{1520 1530} (:type-codes (first cats-from-seq))))))))