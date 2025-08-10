(ns lipas.backend.route-test
  "Comprehensive tests for route ordering functionality"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [lipas.backend.route :as route]
   [lipas.test-utils :as tu]))

;;; Test data helpers ;;;

(defn make-feature
  "Create a LineString feature with given ID and coordinates"
  [id coords]
  {:type "Feature"
   :id (str id)
   :properties {:fid (str id)
                :id id
                :name (str "Segment " id)}
   :geometry {:type "LineString"
              :coordinates coords}})

(defn make-feature-collection [features]
  {:type "FeatureCollection"
   :features features})

;;; Test fixtures ;;;

;; Simple connected line segments
(def connected-features
  [(make-feature 1 [[0 0] [1 1]])
   (make-feature 2 [[1 1] [2 1]])
   (make-feature 3 [[2 1] [3 0]])])

;; Disconnected segments
(def disconnected-features
  [(make-feature 1 [[0 0] [1 1]])
   (make-feature 2 [[5 5] [6 6]]) ; Disconnected
   (make-feature 3 [[1 1] [2 1]])])

;; Segments that need reversal
(def reversed-features
  [(make-feature 1 [[0 0] [1 1]])
   (make-feature 2 [[2 1] [1 1]]) ; Needs reversal to connect
   (make-feature 3 [[2 1] [3 0]])])

;; Loop forming segments
(def loop-features
  [(make-feature 1 [[0 0] [1 0]]) ; North
   (make-feature 2 [[1 0] [1 1]]) ; East
   (make-feature 3 [[1 1] [0 1]]) ; South
   (make-feature 4 [[0 1] [0 0]])]) ; West

;; Complex branching route
(def branching-features
  [(make-feature 1 [[0 0] [1 0]])
   (make-feature 2 [[1 0] [2 0]])
   (make-feature 3 [[1 0] [1 1]]) ; Branch from segment 1's end
   (make-feature 4 [[2 0] [3 0]])])

;;; Tests for suggest-route-order ;;;

(deftest suggest-route-order-test
  (testing "Simple connected segments in correct order"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3] connected-features)]
      (is (:success result))
      (is (= [{:fid "1" :direction "forward"}
              {:fid "2" :direction "forward"}
              {:fid "3" :direction "forward"}]
             (:segments result)))
      (is (= "high" (:confidence result)))
      (is (nil? (:warnings result)))))

  (testing "Segments in wrong order get reordered"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [3 1 2] connected-features)]
      (is (:success result))
      (is (= [{:fid "1" :direction "forward"}
              {:fid "2" :direction "forward"}
              {:fid "3" :direction "forward"}]
             (:segments result)))
      (is (= "high" (:confidence result)))))

  (testing "Disconnected segments generate warnings and lower confidence"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "hiking" [1 2 3] disconnected-features)]
      (is (:success result))
      (is (= 3 (count (:segments result))))
      (is (#{"medium" "low"} (:confidence result)))
      (is (seq (:warnings result)))
      (is (some #(re-find #"disconnected" %) (:warnings result)))))

  (testing "Missing segments are reported in warnings"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3 4 5] connected-features)]
      (is (:success result))
      (is (= 3 (count (:segments result)))) ; Only 3 features exist
      (is (#{"medium" "low"} (:confidence result)))
      (is (some #(re-find #"3 of 5 requested segments were found" %) (:warnings result)))))

  (testing "Empty feature IDs returns empty segments"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [] connected-features)]
      (is (:success result))
      (is (empty? (:segments result)))
      (is (= "high" (:confidence result)))))

  (testing "Single segment returns high confidence"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1] connected-features)]
      (is (:success result))
      (is (= [{:fid "1" :direction "forward"}] (:segments result)))
      (is (= "high" (:confidence result)))))

  (testing "Invalid geometry type causes error"
    (let [sports-site {:lipas-id 12345}
          invalid-features [(assoc-in (first connected-features) [:geometry :type] "Point")]
          result (route/suggest-route-order sports-site "cycling" [1] invalid-features)]
      (is (false? (:success result)))
      (is (re-find #"Invalid geometry type" (:error result)))))

  (testing "Different activity types are handled"
    (doseq [activity ["outdoor-recreation-areas" "outdoor-recreation-routes"
                      "cycling" "paddling" "fishing"]]
      (let [sports-site {:lipas-id 12345}
            result (route/suggest-route-order sports-site activity [1 2] connected-features)]
        (is (:success result))
        (is (= 2 (count (:segments result))))))))

(deftest direction-detection-test
  (testing "Forward direction detection"
    (let [sports-site {:lipas-id 12345}
          ;; Features that maintain their original direction
          features [(make-feature 1 [[0 0] [1 0]])
                    (make-feature 2 [[1 0] [2 0]])]
          result (route/suggest-route-order sports-site "cycling" [1 2] features)]
      (is (:success result))
      (is (every? #(= "forward" (:direction %)) (:segments result)))))

  (testing "Backward direction detection"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3] reversed-features)]
      (is (:success result))
      ;; At least one segment should be marked as backward
      (is (some #(= "backward" (:direction %)) (:segments result)))
      ;; Warning about reversed segments
      (when (> (count (filter #(= "backward" (:direction %)) (:segments result))) 1)
        (is (some #(re-find #"reverse direction" %) (:warnings result)))))))

(deftest confidence-calculation-test
  (testing "High confidence: all segments found, well connected, few reversals"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3] connected-features)]
      (is (= "high" (:confidence result)))))

  (testing "Medium confidence: mostly connected or some missing segments"
    (let [sports-site {:lipas-id 12345}
          ;; Request 5 segments but only 3 exist
          result (route/suggest-route-order sports-site "cycling" [1 2 3 4 5] connected-features)]
      (is (#{"medium" "low"} (:confidence result)))))

  (testing "Low confidence: poor connectivity and missing segments"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3 4 5] disconnected-features)]
      (is (= "low" (:confidence result))))))

(deftest warning-generation-test
  (testing "No warnings for perfect route"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3] connected-features)]
      (is (nil? (:warnings result)))))

  (testing "Warning for missing segments"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3 99] connected-features)]
      (is (some #(re-find #"3 of 4 requested segments were found" %) (:warnings result)))))

  (testing "Warning for disconnected segments"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3] disconnected-features)]
      (is (some #(re-find #"disconnected segments" %) (:warnings result)))))

  (testing "Warning for multiple valid orderings"
    (let [sports-site {:lipas-id 12345}
          ;; Branching route can have multiple valid orderings
          result (route/suggest-route-order sports-site "cycling" [1 2 3 4] branching-features)]
      (when (seq (:warnings result))
        (is (some #(re-find #"Multiple valid route orderings" %) (:warnings result)))))))

(deftest edge-cases-test
  (testing "Handle string and integer FIDs"
    (let [sports-site {:lipas-id 12345}
          mixed-features [(assoc-in (first connected-features) [:properties :fid] "1")
                          (assoc-in (second connected-features) [:properties :fid] 2)]
          result (route/suggest-route-order sports-site "cycling" [1 2] mixed-features)]
      (is (:success result))
      (is (= 2 (count (:segments result))))))

  (testing "Handle feature collection vs features array"
    (let [sports-site {:lipas-id 12345}
          ;; Test with feature collection
          fc-result (route/suggest-route-order sports-site "cycling" [1 2]
                                               (make-feature-collection connected-features))
          ;; Test with raw features array
          arr-result (route/suggest-route-order sports-site "cycling" [1 2] connected-features)]
      (is (:success fc-result))
      (is (:success arr-result))
      (is (= (:segments fc-result) (:segments arr-result)))))

  (testing "Handle various FID extraction patterns"
    (let [sports-site {:lipas-id 12345}
          features [(make-feature 1 [[0 0] [1 0]])
                    ;; Different ID location
                    {:type "Feature"
                     :properties {:id 2} ; No fid property
                     :geometry {:type "LineString" :coordinates [[1 0] [2 0]]}}
                    ;; ID at top level only
                    {:type "Feature"
                     :id 3
                     :properties {}
                     :geometry {:type "LineString" :coordinates [[2 0] [3 0]]}}]
          result (route/suggest-route-order sports-site "cycling" [1 2 3] features)]
      (is (:success result))
      (is (= 3 (count (:segments result))))))

  (testing "Exception handling returns error response"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1] nil)]
      (is (false? (:success result)))
      (is (string? (:error result))))))

(deftest performance-test
  (testing "Large route performance"
    (let [sports-site {:lipas-id 12345}
          ;; Generate 100 connected segments
          large-features (mapv (fn [i]
                                 (make-feature i [[i 0] [(inc i) 0]]))
                               (range 100))
          fids (range 100)
          start-time (System/currentTimeMillis)
          result (route/suggest-route-order sports-site "cycling" fids large-features)
          duration (- (System/currentTimeMillis) start-time)]
      (is (:success result))
      (is (= 100 (count (:segments result))))
      ;; Should complete within 2 seconds even for 100 segments
      (is (< duration 2000)))))

(deftest loop-route-test
  (testing "Closed loop route handling"
    (let [sports-site {:lipas-id 12345}
          result (route/suggest-route-order sports-site "cycling" [1 2 3 4] loop-features)]
      (is (:success result))
      (is (= 4 (count (:segments result))))
      ;; Loop should be detected as well-connected
      (is (= "high" (:confidence result))))))

;; Run tests with:
;; (clojure.test/run-tests 'lipas.backend.route-test)