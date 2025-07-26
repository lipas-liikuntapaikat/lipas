(ns lipas.backend.routes.repl-testing-examples
  "REPL-driven testing examples for route ordering feature.
   These examples are designed to be evaluated interactively in the REPL
   for rapid testing and debugging during development."
  (:require [lipas.schema.sports-sites.activities :as activities]
            [lipas.data.activities :as data-activities]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]))

(comment
  ;; =====================================================
  ;; Schema Validation Testing
  ;; =====================================================

  ;; Test basic segment reference validation
  (m/validate data-activities/segment-reference-schema
              {:fid "seg-123" :direction "forward"})
  ;; => true

  ;; Test invalid direction
  (m/validate data-activities/segment-reference-schema
              {:fid "seg-123" :direction "backwards"})
  ;; => false

  ;; Get detailed error explanation
  (-> (m/explain data-activities/segment-reference-schema
                 {:fid "seg-123" :direction "backwards"})
      (me/humanize))
  ;; => {:direction ["should be either forward or reverse"]}

  ;; Generate valid test data
  (mg/generate data-activities/segment-reference-schema)
  ;; => {:fid "xY7k9", :direction "reverse"}

  ;; =====================================================
  ;; Route Schema Testing
  ;; =====================================================

  ;; Get the route schema for outdoor recreation
  (def route-schema
    (-> data-activities/outdoor-recreation-routes
        :props
        :routes
        :schema))

  ;; Test a valid route with ordering
  (def test-route-ordered
    {:id "test-route-1"
     :route-name {:fi "Testireitti" :se "Testled" :en "Test Route"}
     :fids ["seg-a" "seg-b" "seg-c"]
     :segments [{:fid "seg-a" :direction "forward"}
                {:fid "seg-b" :direction "reverse"}
                {:fid "seg-c" :direction "forward"}]
     :ordering-method "manual"
     :route-length-km 5.2})

  (m/validate route-schema [test-route-ordered])
  ;; => true

  ;; Test backwards compatibility - route without ordering
  (def test-route-legacy
    {:id "legacy-route"
     :route-name {:fi "Vanha reitti"}
     :fids ["seg-1" "seg-2" "seg-3"]
     :route-length-km 4.5})

  (m/validate route-schema [test-route-legacy])
  ;; => true

  ;; Test invalid ordering method
  (def test-route-invalid
    (assoc test-route-ordered :ordering-method "random"))

  (m/validate route-schema [test-route-invalid])
  ;; => false

  (-> (m/explain route-schema [test-route-invalid])
      (me/humanize))
  ;; Shows the validation error

  ;; =====================================================
  ;; Ordering Algorithm Testing
  ;; =====================================================

  ;; Mock ordering algorithm for REPL testing
  (defn calculate-segment-ordering
    "Calculate optimal ordering for segments based on geometry"
    [segments]
    ;; Simple algorithm: order by first coordinate
    (let [get-start-x (fn [seg]
                        (-> seg :geometry :coordinates first first))
          sorted (sort-by get-start-x segments)]
      (mapv (fn [seg] {:fid (:id seg) :direction "forward"}) sorted)))

  ;; Test with simple linear segments
  (def test-segments
    [{:id "C" :geometry {:type "LineString" :coordinates [[2 0] [3 0]]}}
     {:id "A" :geometry {:type "LineString" :coordinates [[0 0] [1 0]]}}
     {:id "B" :geometry {:type "LineString" :coordinates [[1 0] [2 0]]}}])

  (calculate-segment-ordering test-segments)
  ;; => [{:fid "A", :direction "forward"}
  ;;     {:fid "B", :direction "forward"}
  ;;     {:fid "C", :direction "forward"}]

  ;; =====================================================
  ;; Validation Logic Testing
  ;; =====================================================

  (defn segments-match-fids?
    "Check if all segment fids exist in the route's fid list"
    [route]
    (let [fid-set (set (:fids route))
          segment-fids (set (map :fid (:segments route)))]
      (= fid-set segment-fids)))

  ;; Test matching
  (segments-match-fids? test-route-ordered)
  ;; => true

  ;; Test mismatch
  (segments-match-fids?
   {:fids ["a" "b" "c"]
    :segments [{:fid "a" :direction "forward"}
               {:fid "d" :direction "forward"}]}) ; 'd' not in fids
  ;; => false

  ;; =====================================================
  ;; Data Generation for Testing
  ;; =====================================================

  ;; Generate multiple valid routes for testing
  (defn generate-test-routes [n]
    (repeatedly n
                (fn []
                  {:id (str "gen-route-" (rand-int 1000))
                   :route-name {:fi (str "Reitti " (rand-int 100))}
                   :fids (vec (repeatedly (+ 2 (rand-int 5))
                                          (fn [] (str "seg-" (rand-int 1000)))))
                   :route-length-km (* 0.5 (rand-int 20))})))

  (def generated-routes (generate-test-routes 5))

  ;; Validate all generated routes
  (every? #(m/validate route-schema [%]) generated-routes)
  ;; => true

  ;; Add ordering to generated routes
  (defn add-ordering-to-route [route]
    (assoc route
           :segments (mapv (fn [fid]
                             {:fid fid
                              :direction (rand-nth ["forward" "reverse"])})
                           (:fids route))
           :ordering-method (rand-nth ["manual" "algorithmic"])))

  (def ordered-routes (map add-ordering-to-route generated-routes))

  ;; Validate ordered routes
  (every? #(m/validate route-schema [%]) ordered-routes)
  ;; => true

  ;; =====================================================
  ;; Performance Testing
  ;; =====================================================

  ;; Create a large route for performance testing
  (defn create-large-route [segment-count]
    {:id "perf-test-route"
     :route-name {:fi "Suorituskykytesti"}
     :fids (vec (map #(str "seg-" %) (range segment-count)))
     :segments (vec (map #(hash-map :fid (str "seg-" %)
                                    :direction "forward")
                         (range segment-count)))
     :ordering-method "algorithmic"
     :route-length-km (* segment-count 0.1)})

  ;; Test validation performance
  (time (m/validate route-schema [(create-large-route 100)]))
  ;; "Elapsed time: X msecs"
  ;; => true

  ;; Test with 1000 segments
  (time (m/validate route-schema [(create-large-route 1000)]))

  ;; =====================================================
  ;; Integration Testing Helpers
  ;; =====================================================

  ;; Simulate API request/response
  (defn simulate-ordering-request [segments]
    {:status 200
     :body {:segments (calculate-segment-ordering segments)}})

  (simulate-ordering-request test-segments)
  ;; => {:status 200, :body {:segments [{:fid "A", :direction "forward"} ...]}}

  ;; Test error cases
  (defn simulate-ordering-request-with-validation [segments]
    (cond
      (empty? segments)
      {:status 400 :body {:error "No segments provided"}}

      (some #(nil? (:geometry %)) segments)
      {:status 400 :body {:error "Invalid geometry in segments"}}

      :else
      {:status 200 :body {:segments (calculate-segment-ordering segments)}}))

  (simulate-ordering-request-with-validation [])
  ;; => {:status 400, :body {:error "No segments provided"}}

  (simulate-ordering-request-with-validation
   [{:id "bad" :geometry nil}])
  ;; => {:status 400, :body {:error "Invalid geometry in segments"}}

  ;; =====================================================
  ;; End of REPL examples
  ;; =====================================================
  )