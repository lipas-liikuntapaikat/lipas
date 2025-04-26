(ns lipas.gis-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [lipas.backend.gis :as gis]))

(deftest test-sequence-features
  (testing "Empty feature collection"
    (let [empty-collection {:type "FeatureCollection" :features []}
          result (gis/sequence-features empty-collection)]
      (is (= empty-collection result))))

  (testing "Single LineString feature"
    (let [single-feature-collection {:type "FeatureCollection"
                                     :features [{:type "Feature"
                                                 :id "line1"
                                                 :properties {:name "Single line"}
                                                 :geometry {:type "LineString"
                                                            :coordinates [[0 0] [1 1]]}}]}
          result (gis/sequence-features single-feature-collection)]
      (is (= single-feature-collection result))
      (is (= "line1" (get-in result [:features 0 :id])))))

  (testing "Two connected LineStrings"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line1"
                                  :properties {:name "First line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature"
                                  :id "line2"
                                  :properties {:name "Second line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 2 (count (:features result))))
      (is (= ["line1" "line2"] feature-ids))))

  (testing "Two connected LineStrings in reverse order"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line2"
                                  :properties {:name "Second line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature"
                                  :id "line1"
                                  :properties {:name "First line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 2 (count (:features result))))
      (is (= ["line1" "line2"] feature-ids))))

  (testing "Three LineStrings with complex ordering"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line2"
                                  :properties {:name "Middle line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature"
                                  :id "line3"
                                  :properties {:name "Last line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[2 2] [3 3]]}}
                                 {:type "Feature"
                                  :id "line1"
                                  :properties {:name "First line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 3 (count (:features result))))
      (is (= ["line1" "line2" "line3"] feature-ids))))

  (testing "Disconnected LineStrings"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line1"
                                  :properties {:name "Disconnected line 1"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature"
                                  :id "line2"
                                  :properties {:name "Disconnected line 2"}
                                  :geometry {:type "LineString"
                                             :coordinates [[5 5] [6 6]]}}]}
          result (gis/sequence-features collection)]
      (is (= 2 (count (:features result))))
      ;; The order might be arbitrary for disconnected lines, so we just check that both features exist
      (is (= #{"line1" "line2"} (set (map :id (:features result)))))))

  (testing "Complex network of LineStrings"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line1"
                                  :properties {:name "Line 1"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature"
                                  :id "line2"
                                  :properties {:name "Line 2"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature"
                                  :id "line3"
                                  :properties {:name "Line 3"}
                                  :geometry {:type "LineString"
                                             :coordinates [[2 2] [3 3]]}}
                                 {:type "Feature"
                                  :id "line4"
                                  :properties {:name "Line 4"}
                                  :geometry {:type "LineString"
                                             :coordinates [[3 3] [4 4]]}}
                                 {:type "Feature"
                                  :id "line5"
                                  :properties {:name "Line 5"}
                                  :geometry {:type "LineString"
                                             :coordinates [[4 4] [5 5]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 5 (count (:features result))))
      (is (= ["line1" "line2" "line3" "line4" "line5"] feature-ids))))

  (testing "LineStrings with branches"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "main1"
                                  :properties {:name "Main line 1"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature"
                                  :id "main2"
                                  :properties {:name "Main line 2"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature"
                                  :id "branch"
                                  :properties {:name "Branch line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [1 2]]}}]}
          result (gis/sequence-features collection)
          feature-ids (set (map :id (:features result)))]
      (is (= 3 (count (:features result))))
      (is (= #{"main1" "main2" "branch"} feature-ids))
      ;; Check that main1 comes before main2 and branch
      (let [main1-idx (.indexOf (map :id (:features result)) "main1")
            main2-idx (.indexOf (map :id (:features result)) "main2")
            branch-idx (.indexOf (map :id (:features result)) "branch")]
        (is (< main1-idx main2-idx))
        (is (< main1-idx branch-idx)))))

  (testing "Preservation of properties"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line2"
                                  :properties {:name "Second line" :color "red"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature"
                                  :id "line1"
                                  :properties {:name "First line" :color "blue"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}]}
          result (gis/sequence-features collection)
          first-feature (first (:features result))
          second-feature (second (:features result))]
      (is (= "line1" (:id first-feature)))
      (is (= "line2" (:id second-feature)))
      (is (= {:name "First line" :color "blue"} (:properties first-feature)))
      (is (= {:name "Second line" :color "red"} (:properties second-feature))))))


(deftest test-sequence-features-error-handling
  (testing "Non-LineString geometries"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "point1"
                                  :properties {:name "Point feature"}
                                  :geometry {:type "Point"
                                             :coordinates [0 0]}}]}]
      ;; Original features are returned when failures occur
      (is (= collection (gis/sequence-features collection))))))


(deftest test-3d-coordinates
  (testing "LineStrings with 3D coordinates"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line1"
                                  :properties {:name "First 3D line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0 10] [1 1 15]]}}
                                 {:type "Feature"
                                  :id "line2"
                                  :properties {:name "Second 3D line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1 15] [2 2 20]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 2 (count (:features result))))
      (is (= ["line1" "line2"] feature-ids)))))

(deftest test-high-precision-coordinates
  (testing "LineStrings with high-precision coordinates"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "line1"
                                  :properties {:name "First high-precision line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8129042842923 62.7692698622464]
                                                           [22.8116620019017 62.7689001882033]]}}
                                 {:type "Feature"
                                  :id "line2"
                                  :properties {:name "Second high-precision line"}
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8116620019017 62.7689001882033]
                                                           [22.8111965179819 62.7687244865827]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 2 (count (:features result))))
      (is (= ["line1" "line2"] feature-ids)))))

(deftest test-real-world-data-sample
  (testing "Real-world data sample with 3D high-precision coordinates"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "segment1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8129042842923 62.7692698622464 83.185]
                                                           [22.8116620019017 62.7689001882033 83.165]
                                                           [22.8111965179819 62.7687244865827 83.581]]}}
                                 {:type "Feature"
                                  :id "segment2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8111965179819 62.7687244865827 83.581]
                                                           [22.8102974508471 62.7685628114078 84.026]
                                                           [22.8078886752191 62.7682571661726 84.079]]}}
                                 {:type "Feature"
                                  :id "segment3"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8078886752191 62.7682571661726 84.079]
                                                           [22.8064159058541 62.767853590877 83.291]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 3 (count (:features result))))
      (is (= ["segment1" "segment2" "segment3"] feature-ids)))))

(deftest test-disconnected-segments
  (testing "Mixture of connected and disconnected segments"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "segment1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.81 62.76 83.1]
                                                           [22.80 62.75 83.2]]}}
                                 {:type "Feature"
                                  :id "segment2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.80 62.75 83.2]
                                                           [22.79 62.74 83.3]]}}
                                 {:type "Feature"
                                  :id "disconnected1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.70 62.70 85.0]
                                                           [22.71 62.71 85.1]]}}
                                 {:type "Feature"
                                  :id "disconnected2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.71 62.71 85.1]
                                                           [22.72 62.72 85.2]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 4 (count (:features result))))
      ;; Connected segments should be in sequence
      (is (= #{"segment1" "segment2" "disconnected1" "disconnected2"}
             (set feature-ids)))
      ;; Check that segment1 comes before segment2
      (let [segment1-idx (.indexOf feature-ids "segment1")
            segment2-idx (.indexOf feature-ids "segment2")]
        (is (< segment1-idx segment2-idx)))
      ;; Check that disconnected1 comes before disconnected2
      (let [disconnected1-idx (.indexOf feature-ids "disconnected1")
            disconnected2-idx (.indexOf feature-ids "disconnected2")]
        (is (< disconnected1-idx disconnected2-idx))))))

(deftest test-complex-trail-network
  (testing "Complex trail network with multiple connections"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "main1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.80 62.75 83.0]
                                                           [22.81 62.76 83.1]]}}
                                 {:type "Feature"
                                  :id "main2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.81 62.76 83.1]
                                                           [22.82 62.77 83.2]]}}
                                 {:type "Feature"
                                  :id "branch1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.81 62.76 83.1]
                                                           [22.81 62.77 83.3]]}}
                                 {:type "Feature"
                                  :id "branch2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.81 62.77 83.3]
                                                           [22.82 62.78 83.4]]}}
                                 {:type "Feature"
                                  :id "loop1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.82 62.77 83.2]
                                                           [22.83 62.78 83.5]]}}
                                 {:type "Feature"
                                  :id "loop2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.83 62.78 83.5]
                                                           [22.82 62.78 83.4]]}}]}
          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]
      (is (= 6 (count (:features result))))
      (is (= #{"main1" "main2" "branch1" "branch2" "loop1" "loop2"}
             (set feature-ids)))
      ;; Check that main1 comes before main2
      (let [main1-idx (.indexOf feature-ids "main1")
            main2-idx (.indexOf feature-ids "main2")]
        (is (< main1-idx main2-idx)))
      ;; Check that branch1 comes before branch2
      (let [branch1-idx (.indexOf feature-ids "branch1")
            branch2-idx (.indexOf feature-ids "branch2")]
        (is (< branch1-idx branch2-idx))))))

(deftest test-large-trail-with-gaps
  (testing "Large trail with small gaps between segments"
    (let [;; Create a trail with small gaps (0.0001 degree) between segments
          create-trail-segments (fn [start-x start-y segments]
                                  (loop [x start-x
                                         y start-y
                                         i 0
                                         result []]
                                    (if (>= i segments)
                                      result
                                      (let [next-x (+ x 0.001)
                                            next-y (+ y 0.001)
                                            ;; Add a small gap between segments
                                            gap-x (+ next-x 0.0001)
                                            gap-y (+ next-y 0.0001)]
                                        (recur gap-x gap-y (inc i)
                                               (conj result
                                                     {:type "Feature"
                                                      :id (str "segment" i)
                                                      :geometry {:type "LineString"
                                                                 :coordinates [[x y 100.0]
                                                                               [next-x next-y 101.0]]}}))))))

          collection {:type "FeatureCollection"
                      :features (create-trail-segments 22.8 62.7 10)}

          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))
          original-ids (mapv #(get % :id) (:features collection))]

      (is (= (count (:features collection)) (count (:features result))))
      ;; The sequencer might not be able to connect segments with gaps,
      ;; but we should still have all segments
      (is (= (set original-ids) (set feature-ids))))))

(deftest test-full-real-world-data
  (testing "Test with a full real-world data sample"
    (let [collection {:type "FeatureCollection"
                      :features
                      [{:type "Feature"
                        :id "segment1"
                        :geometry {:type "LineString"
                                   :coordinates [[22.8129042842923 62.7692698622464 83.185]
                                                 [22.8116620019017 62.7689001882033 83.165]]}}
                       {:type "Feature"
                        :id "segment2"
                        :geometry {:type "LineString"
                                   :coordinates [[22.8116620019017 62.7689001882033 83.165]
                                                 [22.8111965179819 62.7687244865827 83.581]]}}
                       {:type "Feature"
                        :id "segment3"
                        :geometry {:type "LineString"
                                   :coordinates [[22.8111965179819 62.7687244865827 83.581]
                                                 [22.8102974508471 62.7685628114078 84.026]]}}
                       {:type "Feature"
                        :id "segment4"
                        :geometry {:type "LineString"
                                   :coordinates [[22.8102974508471 62.7685628114078 84.026]
                                                 [22.8078886752191 62.7682571661726 84.079]]}}
                       {:type "Feature"
                        :id "segment5"
                        :geometry {:type "LineString"
                                   :coordinates [[22.8078886752191 62.7682571661726 84.079]
                                                 [22.8064159058541 62.767853590877 83.291]]}}
                       {:type "Feature"
                        :id "disconnected1"
                        :geometry {:type "LineString"
                                   :coordinates [[22.7883526191817 62.7352778858681 83.061]
                                                 [22.7894362264357 62.7348249761127 83.026]]}}
                       {:type "Feature"
                        :id "disconnected2"
                        :geometry {:type "LineString"
                                   :coordinates [[22.7894362264357 62.7348249761127 83.026]
                                                 [22.791026014543 62.7339827131476 83.283]]}}]}

          result (gis/sequence-features collection)
          feature-ids (mapv #(get % :id) (:features result))]

      (is (= 7 (count (:features result))))

      ;; Check that connected segments are in sequence
      (let [segment-indices (map #(.indexOf feature-ids %)
                                 ["segment1" "segment2" "segment3" "segment4" "segment5"])]
        (is (apply < segment-indices)))

      ;; Check that disconnected segments are in sequence
      (let [disconnected1-idx (.indexOf feature-ids "disconnected1")
            disconnected2-idx (.indexOf feature-ids "disconnected2")]
        (is (< disconnected1-idx disconnected2-idx))))))


(comment
  (t/run-tests *ns*)

  )
