(ns lipas.gis-test
  "Improved test suite for sequence-features that validates actual sequencing properties.

  This suite tests the BEHAVIOR of sequencing:
  - Validates that adjacent features actually connect (share endpoints)
  - Measures sequencing quality (number of connections)
  - Tests that sequencing improves over shuffled input
  - Validates preservation of features and properties"
  (:require [clojure.test :as t :refer [deftest is testing]]
            [lipas.backend.gis :as gis]))

;; ============================================================================
;; Helper Functions - Validate Core Sequencing Properties
;; ============================================================================

(defn get-endpoints
  "Extract start and end coordinates from a LineString feature"
  [feature]
  (let [coords (get-in feature [:geometry :coordinates])]
    {:start (first coords)
     :end (last coords)}))

(defn coords-equal?
  "Check if two coordinates are equal, ignoring Z values.
  Handles both 2D [x y] and 3D [x y z] coordinates."
  [c1 c2]
  (and (== (first c1) (first c2))
       (== (second c1) (second c2))))

(defn features-connect?
  "Check if two LineString features share an endpoint (in any direction).
  Returns true if they can be connected, considering reversal."
  [f1 f2]
  (let [{s1 :start e1 :end} (get-endpoints f1)
        {s2 :start e2 :end} (get-endpoints f2)]
    (or (coords-equal? e1 s2) ; f1 -> f2 (forward-forward)
        (coords-equal? e1 e2) ; f1 -> reversed f2 (forward-backward)
        (coords-equal? s1 s2) ; reversed f1 -> f2 (backward-forward)
        (coords-equal? s1 e2) ; both reversed (backward-backward)
        )))

(defn count-connections
  "Count how many adjacent pairs in the feature list actually connect.
  This is the PRIMARY metric for sequencing quality."
  [features]
  (if (< (count features) 2)
    0
    (count (filter true?
                   (map features-connect?
                        features
                        (rest features))))))

(defn max-possible-connections
  "Maximum number of connections in a linear sequence of n features is n-1"
  [features]
  (max 0 (dec (count features))))

(defn is-perfect-sequence?
  "Check if features form a perfect linear sequence where all adjacent pairs connect"
  [features]
  (if (< (count features) 2)
    true
    (= (count-connections features)
       (max-possible-connections features))))

(defn sequencing-quality
  "Return a quality metric [connections max-possible] for reporting"
  [features]
  {:connections (count-connections features)
   :max-possible (max-possible-connections features)
   :perfect? (is-perfect-sequence? features)})

;; ============================================================================
;; Core Property Tests - These validate the ESSENTIAL behavior
;; ============================================================================

(deftest test-perfect-linear-sequence-from-shuffled
  (testing "Sequencing creates perfect linear path from shuffled connected features"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "c"
                                  :geometry {:type "LineString"
                                             :coordinates [[2 2] [3 3]]}}
                                 {:type "Feature" :id "a"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature" :id "b"
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}]}
          result (gis/sequence-features collection)
          features (:features result)
          input-quality (sequencing-quality (:features collection))
          output-quality (sequencing-quality features)]

      ;; CORE ASSERTION: Result should form a perfect sequence
      (is (is-perfect-sequence? features)
          (str "All adjacent features should connect. Quality: " output-quality))

      ;; Should improve from shuffled input
      (is (> (:connections output-quality)
             (:connections input-quality))
          "Sequencing should improve connection count from shuffled input"))))

(deftest test-long-chain-worst-case-shuffle
  (testing "Long chain of features in worst-case random order"
    (let [n 10
          ;; Create connected features
          ordered-features (for [i (range n)]
                             {:type "Feature"
                              :id (str "seg" i)
                              :geometry {:type "LineString"
                                         :coordinates [[i i] [(inc i) (inc i)]]}})
          ;; Shuffle them thoroughly
          shuffled (shuffle ordered-features)
          collection {:type "FeatureCollection" :features shuffled}
          result (gis/sequence-features collection)
          result-features (:features result)
          output-quality (sequencing-quality result-features)]

      (is (= n (count result-features)) "All features preserved")

      ;; Should form a perfect sequence
      (is (:perfect? output-quality)
          (str "Should form perfect sequence with all " (dec n) " connections. Got: " output-quality)))))

(deftest test-already-ordered-maintains-sequence
  (testing "Already-ordered features maintain valid sequence"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "a"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature" :id "b"
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature" :id "c"
                                  :geometry {:type "LineString"
                                             :coordinates [[2 2] [3 3]]}}]}
          result (gis/sequence-features collection)
          features (:features result)]

      (is (is-perfect-sequence? features)
          "Pre-ordered sequence should remain valid")

      ;; Order should be preserved or produce equivalent valid sequence
      (is (= 2 (count-connections features))
          "Should maintain all connections"))))

(deftest test-disconnected-components-grouped
  (testing "Multiple disconnected components are properly sequenced within each group"
    (let [collection {:type "FeatureCollection"
                      :features [;; Component 1 (shuffled)
                                 {:type "Feature" :id "a2"
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature" :id "a1"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 ;; Component 2 (shuffled, far away)
                                 {:type "Feature" :id "b2"
                                  :geometry {:type "LineString"
                                             :coordinates [[11 11] [12 12]]}}
                                 {:type "Feature" :id "b1"
                                  :geometry {:type "LineString"
                                             :coordinates [[10 10] [11 11]]}}]}
          result (gis/sequence-features collection)
          features (:features result)
          quality (sequencing-quality features)]

      (is (= 4 (count features)) "All features preserved")

      ;; KEY: Should have 2 connections (one per component)
      ;; Input had 0 connections due to interleaving
      (is (= 2 (:connections quality))
          (str "Should have one connection per component. Got: " quality))

      ;; Verify each component is contiguous
      (let [a-indices (keep-indexed #(when (#{"a1" "a2"} (:id %2)) %1) features)
            b-indices (keep-indexed #(when (#{"b1" "b2"} (:id %2)) %1) features)]
        (is (= 1 (- (second a-indices) (first a-indices)))
            "Component A features should be adjacent")
        (is (= 1 (- (second b-indices) (first b-indices)))
            "Component B features should be adjacent")))))

(deftest test-three-disconnected-components
  (testing "Three separate trails properly sequenced"
    (let [collection {:type "FeatureCollection"
                      :features [;; Trail C
                                 {:type "Feature" :id "c2"
                                  :geometry {:type "LineString"
                                             :coordinates [[21 21] [22 22]]}}
                                 ;; Trail A
                                 {:type "Feature" :id "a1"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature" :id "a2"
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 ;; Trail B
                                 {:type "Feature" :id "b1"
                                  :geometry {:type "LineString"
                                             :coordinates [[10 10] [11 11]]}}
                                 ;; Trail C continued
                                 {:type "Feature" :id "c1"
                                  :geometry {:type "LineString"
                                             :coordinates [[20 20] [21 21]]}}
                                 ;; Trail B continued
                                 {:type "Feature" :id "b2"
                                  :geometry {:type "LineString"
                                             :coordinates [[11 11] [12 12]]}}]}
          result (gis/sequence-features collection)
          features (:features result)
          quality (sequencing-quality features)]

      (is (= 6 (count features)))

      ;; Should have 3 connections total (one per trail)
      (is (= 3 (:connections quality))
          (str "Should have 3 connections (one per trail). Got: " quality)))))

;; ============================================================================
;; Coordinate Precision Tests
;; ============================================================================

(deftest test-3d-coordinates-handled
  (testing "3D coordinates handled correctly (Z values preserved, ignored for connectivity)"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "b"
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1 10] [2 2 20]]}}
                                 {:type "Feature" :id "a"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0 5] [1 1 10]]}}]}
          result (gis/sequence-features collection)
          features (:features result)]

      (is (is-perfect-sequence? features)
          "3D coordinates should be handled (Z ignored for connectivity check)")

      ;; Z values should be preserved in output
      (is (= [0 0 5] (get-in (first features) [:geometry :coordinates 0]))
          "Z coordinate preserved"))))

(deftest test-high-precision-real-world-coordinates
  (testing "High-precision real-world coordinates maintain connectivity"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "seg2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8116620019017 62.7689001882033]
                                                           [22.8111965179819 62.7687244865827]]}}
                                 {:type "Feature" :id "seg1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8129042842923 62.7692698622464]
                                                           [22.8116620019017 62.7689001882033]]}}]}
          result (gis/sequence-features collection)
          features (:features result)]

      (is (is-perfect-sequence? features)
          "High-precision coordinates should maintain connectivity"))))

(deftest test-real-world-trail-with-3d
  (testing "Real-world trail segments with 3D coordinates and multiple points per segment"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "segment3"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8078886752191 62.7682571661726 84.079]
                                                           [22.8064159058541 62.767853590877 83.291]]}}
                                 {:type "Feature" :id "segment1"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8129042842923 62.7692698622464 83.185]
                                                           [22.8116620019017 62.7689001882033 83.165]
                                                           [22.8111965179819 62.7687244865827 83.581]]}}
                                 {:type "Feature" :id "segment2"
                                  :geometry {:type "LineString"
                                             :coordinates [[22.8111965179819 62.7687244865827 83.581]
                                                           [22.8102974508471 62.7685628114078 84.026]
                                                           [22.8078886752191 62.7682571661726 84.079]]}}]}
          result (gis/sequence-features collection)
          features (:features result)]

      (is (= 3 (count features)))
      (is (is-perfect-sequence? features)
          "Real-world trail should form continuous path")

      ;; Verify correct ordering
      (is (= ["segment1" "segment2" "segment3"]
             (mapv :id features))
          "Segments should be in geographic sequence"))))

;; ============================================================================
;; Edge Cases
;; ============================================================================

(deftest test-empty-collection
  (testing "Empty collection handled gracefully"
    (let [collection {:type "FeatureCollection" :features []}
          result (gis/sequence-features collection)]
      (is (= collection result)))))

(deftest test-single-feature
  (testing "Single feature returned unchanged"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "only"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}]}
          result (gis/sequence-features collection)]
      (is (= collection result))
      (is (is-perfect-sequence? (:features result))
          "Single feature is trivially a perfect sequence"))))

(deftest test-two-features
  (testing "Two features minimal case"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "b"
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature" :id "a"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}]}
          result (gis/sequence-features collection)
          features (:features result)]

      (is (= 2 (count features)))
      (is (is-perfect-sequence? features)
          "Two connected features should sequence perfectly"))))

;; ============================================================================
;; Property Preservation
;; ============================================================================

(deftest test-feature-preservation
  (testing "All features preserved with properties and IDs intact"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature"
                                  :id "b"
                                  :properties {:name "Second" :color "red" :distance 100}
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}
                                 {:type "Feature"
                                  :id "a"
                                  :properties {:name "First" :color "blue" :distance 50}
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}]}
          result (gis/sequence-features collection)
          features (:features result)]

      (is (= 2 (count features)) "All features present")

      ;; Should be reordered
      (is (= "a" (:id (first features))))
      (is (= "b" (:id (second features))))

      ;; Properties completely preserved
      (is (= {:name "First" :color "blue" :distance 50}
             (:properties (first features))))
      (is (= {:name "Second" :color "red" :distance 100}
             (:properties (second features))))

      ;; Geometries unchanged
      (is (= [[0 0] [1 1]] (get-in (first features) [:geometry :coordinates])))
      (is (= [[1 1] [2 2]] (get-in (second features) [:geometry :coordinates]))))))

;; ============================================================================
;; Error Handling and Fallback
;; ============================================================================

(deftest test-non-linestring-fallback
  (testing "Non-LineString geometries cause graceful fallback"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "point"
                                  :geometry {:type "Point"
                                             :coordinates [0 0]}}]}
          result (gis/sequence-features collection)]
      (is (= collection result)
          "Non-LineString features should return original collection"))))

(deftest test-mixed-valid-invalid
  (testing "Mix of valid LineStrings and invalid geometries"
    (let [collection {:type "FeatureCollection"
                      :features [{:type "Feature" :id "line1"
                                  :geometry {:type "LineString"
                                             :coordinates [[0 0] [1 1]]}}
                                 {:type "Feature" :id "point"
                                  :geometry {:type "Point"
                                             :coordinates [5 5]}}
                                 {:type "Feature" :id "line2"
                                  :geometry {:type "LineString"
                                             :coordinates [[1 1] [2 2]]}}]}
          result (gis/sequence-features collection)]
      ;; Should fallback to original due to invalid geometry
      (is (= collection result)))))

;; ============================================================================
;; Complex Real-World Scenarios
;; ============================================================================

(deftest test-multiple-trails-with-gaps
  (testing "Multiple trails with small gaps (non-connecting segments)"
    (let [collection {:type "FeatureCollection"
                      :features [;; Trail 1: 3 connected segments
                                 {:type "Feature" :id "t1-seg3"
                                  :geometry {:type "LineString"
                                             :coordinates [[2.0 2.0] [3.0 3.0]]}}
                                 {:type "Feature" :id "t1-seg1"
                                  :geometry {:type "LineString"
                                             :coordinates [[0.0 0.0] [1.0 1.0]]}}
                                 {:type "Feature" :id "t1-seg2"
                                  :geometry {:type "LineString"
                                             :coordinates [[1.0 1.0] [2.0 2.0]]}}

                                 ;; Trail 2: 2 segments with small gap (not connecting)
                                 {:type "Feature" :id "t2-seg1"
                                  :geometry {:type "LineString"
                                             :coordinates [[10.0 10.0] [11.0 11.0]]}}
                                 {:type "Feature" :id "t2-seg2"
                                  :geometry {:type "LineString"
                                             :coordinates [[11.001 11.001] [12.0 12.0]]}}]}
          result (gis/sequence-features collection)
          features (:features result)
          quality (sequencing-quality features)]

      (is (= 5 (count features)))

      ;; Trail 1 has 2 connections, Trail 2 has 0 (gap), so total = 2
      (is (= 2 (:connections quality))
          (str "Should have 2 connections (only from Trail 1). Got: " quality))

      ;; Trail 1 segments should be together
      (let [t1-indices (keep-indexed #(when (clojure.string/starts-with? (:id %2) "t1") %1) features)]
        (is (= 3 (count t1-indices)))
        (is (= (range (first t1-indices) (+ (first t1-indices) 3))
               t1-indices)
            "Trail 1 segments should be contiguous")))))

(deftest test-large-mixed-network
  (testing "Large mixed network with connected and disconnected components"
    (let [;; Create a larger realistic scenario
          trail-a (for [i (range 5)]
                    {:type "Feature"
                     :id (str "a" i)
                     :geometry {:type "LineString"
                                :coordinates [[i i] [(inc i) (inc i)]]}})

          trail-b (for [i (range 3)]
                    {:type "Feature"
                     :id (str "b" i)
                     :geometry {:type "LineString"
                                :coordinates [[(+ 20 i) (+ 20 i)]
                                              [(+ 21 i) (+ 21 i)]]}})

          ;; Shuffle everything together
          shuffled (shuffle (concat trail-a trail-b))

          collection {:type "FeatureCollection" :features shuffled}
          result (gis/sequence-features collection)
          features (:features result)
          quality (sequencing-quality features)]

      (is (= 8 (count features)))

      ;; Should have 4 connections from trail-a and 2 from trail-b = 6 total
      (is (= 6 (:connections quality))
          (str "Should have 6 connections (4+2). Got: " quality)))))

(comment
  (t/run-tests *ns*)

  ;; Quick check of a single test
  (test-perfect-linear-sequence-from-shuffled)
  (test-disconnected-components-grouped)
  (test-long-chain-worst-case-shuffle))
