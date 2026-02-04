(ns lipas.backend.analysis.diversity-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [integrant.core :as ig]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.config :as config]
            [lipas.backend.gis :as gis]
            [lipas.backend.search :as search]
            [lipas.backend.osrm :as osrm]
            [lipas.test-utils :as test-utils]
            [taoensso.timbre :as log]))

;;; Test system setup ;;;
;; Note: This test only needs :lipas/search component, not the full system
(defonce test-system (atom nil))

;;; Accessors ;;;
(defn test-search []
  (:lipas/search @test-system))

;;; Fixtures ;;;

(use-fixtures :once
  (fn [f]
    ;; Ensure database is properly initialized
    (test-utils/ensure-test-database!)
    ;; Initialize test system using test config (only search component)
    (reset! test-system
            (ig/init (select-keys (config/->system-config test-utils/config) [:lipas/search])))
    (try (f)
         (finally
           (when @test-system
             (ig/halt! @test-system)
             (reset! test-system nil))))))

(use-fixtures :each
  (fn [f]
    ;; Clean elasticsearch indices before each test - pass search explicitly
    (test-utils/prune-es! (test-search))
    (f)))

;;; Test data based on real production data ;;;

(def test-grid-items
  "Sample MML grid items from Helsinki area"
  [{:grd_id "250mN667175E38600"
    :WKT "POINT (24.9477872860078 60.1678597021898)"
    :vaesto "1"
    :ika_0_14 "-1"
    :ika_15_64 "-1"
    :ika_65_ "-1"
    :xkoord 24.9477872860078
    :ykoord 60.1678597021898
    :kunta "Helsinki"
    :vuosi 2023
    :id_nro "250mN667175E38600"}
   {:grd_id "250mN667150E38600"
    :WKT "POINT (24.9432145 60.1680234)"
    :vaesto "150"
    :ika_0_14 "25"
    :ika_15_64 "100"
    :ika_65_ "25"
    :xkoord 24.9432145
    :ykoord 60.1680234
    :kunta "Helsinki"
    :vuosi 2023
    :id_nro "250mN667150E38600"}])

(def test-sports-sites
  "Sample sports sites with realistic data - positioned close to test grid items"
  [{:_id "site-1"
    :_source {:name "Väinämöisenkenttä / Kaukalo"
              :status "active"
              :type {:type-code 1530}
              :search-meta
              {:location
               {:simple-geoms
                {:features
                 [{:geometry {:coordinates [24.948 60.168] ; Very close to first grid item
                              :type "Point"}
                   :type "Feature"
                   :properties {}}]
                 :type "FeatureCollection"}
                :geometries ; Also need this for geo_shape query
                {:type "Point"
                 :coordinates [24.948 60.168]}}}}}
   {:_id "site-2"
    :_source {:name "Väinämöisenkenttä / Luistelukenttä"
              :status "active"
              :type {:type-code 1520}
              :search-meta
              {:location
               {:simple-geoms
                {:features
                 [{:geometry {:coordinates [24.945 60.167] ; ~200m from first grid item
                              :type "Point"}
                   :type "Feature"
                   :properties {}}]
                 :type "FeatureCollection"}
                :geometries ; Also need this for geo_shape query
                {:type "Point"
                 :coordinates [24.945 60.167]}}}}}
   {:_id "site-3"
    :_source {:name "Hesperian Esplanadi / Luistelukenttä"
              :status "active"
              :type {:type-code 1520}
              :search-meta
              {:location
               {:simple-geoms
                {:features
                 [{:geometry {:coordinates [24.950 60.169] ; ~300m from first grid item
                              :type "Point"}
                   :type "Feature"
                   :properties {}}]
                 :type "FeatureCollection"}
                :geometries ; Also need this for geo_shape query
                {:type "Point"
                 :coordinates [24.950 60.169]}}}}}])

(def test-osrm-responses
  "Realistic OSRM API responses"
  {:car {:code "Ok"
         :distances [[2036.6 2050.3 2500.1]]
         :durations [[338.4 340.2 380.5]]}
   :bicycle {:code "Ok"
             :distances [[2350.0 2360.5 2800.2]]
             :durations [[763.0 765.1 820.3]]}
   :foot {:code "Ok"
          :distances [[1987.2 2000.1 2400.3]]
          :durations [[1432.6 1440.2 1600.1]]}})

;;; Helper functions for seeding test data ;;;

(defn- seed-test-data! [search]
  ;; Seed grid data
  (let [grid-idx (get-in search [:indices :analysis :diversity])]
    (doseq [item test-grid-items]
      (search/index! (:client search) grid-idx :grd_id item :sync)))

  ;; Seed sports site data
  (let [site-idx (get-in search [:indices :sports-site :search])]
    (doseq [site test-sports-sites]
      (search/index!
       (:client search)
       site-idx
       (constantly (:_id site))
       (:_source site)
       :sync))))

;;; Mock OSRM for deterministic tests ;;;

(defn mock-osrm-call
  "Mock for osrm/get-data that returns profile-specific data"
  [{:keys [profile] :as _params}]
  (get test-osrm-responses profile))

;;; Tests ;;;

(deftest fetch-grid-test
  (testing "Fetching grid items within radius"
    (seed-test-data! (test-search))
    (let [test-fcoll (gis/->fcoll
                      [(gis/->feature {:type "Point"
                                       :coordinates [24.9477 60.1678]})])
          result (diversity/fetch-grid (test-search) test-fcoll 1)]
      (is (seq result))
      (is (every? #(contains? % :_source) result))
      (is (= "250mN667175E38600" (-> result first :_source :grd_id))))))

(deftest process-grid-item-test
  (testing "Processing single grid item calculates sports site distances"
    (seed-test-data! (test-search))
    (with-redefs [osrm/get-data mock-osrm-call]
      (let [grid-item (first test-grid-items)
            result (diversity/process-grid-item (test-search) 2 grid-item prn)]

        ;; Check structure
        (is (contains? result :sports-sites))
        (is (coll? (:sports-sites result)))
        (is (pos? (count (:sports-sites result))))

        ;; Check each sports site has required fields
        (doseq [site (:sports-sites result)]
          (is (contains? site :id))
          (is (contains? site :type-code))
          (is (contains? site :status))
          (is (contains? site :osrm)))

        ;; Check OSRM data structure
        (when (seq (:sports-sites result))
          (let [first-site (first (:sports-sites result))]
            (is (= #{:car :bicycle :foot} (set (keys (:osrm first-site)))))
            (is (number? (-> first-site :osrm :car :distance-m)))
            (is (number? (-> first-site :osrm :car :duration-s)))))))))

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

(deftest recalc-grid-test
  (testing "Full grid recalculation workflow"
    (seed-test-data! (test-search))
    (with-redefs [osrm/get-data mock-osrm-call]
      (let [test-fcoll (gis/->fcoll
                        [(gis/->feature {:type "Point"
                                         :coordinates [24.9477 60.1678]})])
            ;; Capture bulk index calls
            bulk-calls (atom [])
            mock-bulk-index (fn [client data]
                              (swap! bulk-calls conj data)
                              {:created (count data)})]

        (with-redefs [search/bulk-index-sync! mock-bulk-index]
          (diversity/recalc-grid! (test-search) test-fcoll)

          ;; Verify bulk indexing was called
          (is (seq @bulk-calls))

          ;; Verify data structure
          (let [indexed-data (first @bulk-calls)]
            (is (every? #(or (contains? % :index) (contains? % :grd_id)) indexed-data))
            (is (= (count indexed-data) (* 2 (count test-grid-items)))) ; 2 entries per item
            (is (every? #(= "diversity_test" (-> % :index :_index))
                        (filter #(contains? % :index) indexed-data)))))))))

(deftest memory-usage-test
  (testing "Processing doesn't accumulate excessive memory"
    (seed-test-data! (test-search))
    (let [test-fcoll (gis/->fcoll
                      [(gis/->feature {:type "Point"
                                       :coordinates [24.9477 60.1678]})])
          runtime (Runtime/getRuntime)
          ;; Force GC and measure baseline
          _ (.gc runtime)
          _ (Thread/sleep 100)
          baseline-memory (.totalMemory runtime)]

      (with-redefs [osrm/get-data mock-osrm-call
                    ;; Limit grid items for memory test
                    diversity/fetch-grid (fn [_ _ _]
                                           (take 5 (map #(hash-map :_source %) test-grid-items)))]

        ;; Run multiple iterations
        (dotimes [_ 3]
          (diversity/recalc-grid! (test-search) test-fcoll))

        ;; Force GC and measure
        (.gc runtime)
        (Thread/sleep 100)
        (let [final-memory (.totalMemory runtime)
              memory-increase (- final-memory baseline-memory)
              ;; Allow up to 100MB increase (reasonable for test data)
              max-allowed-increase (* 100 1024 1024)]

          (is (< memory-increase max-allowed-increase)
              (str "Memory increased by " (/ memory-increase 1024 1024) "MB")))))))

(deftest concurrent-processing-test
  (testing "Concurrent processing doesn't corrupt data"
    (seed-test-data! (test-search))
    (let [test-points [[24.9477 60.1678]
                       [24.9500 60.1700]
                       [24.9400 60.1650]]
          results (atom [])]

      (with-redefs [osrm/get-data mock-osrm-call
                    search/bulk-index-sync! (fn [_ data]
                                              (swap! results conj data)
                                              {:created (count data)})]

        ;; Process multiple points concurrently
        (let [futures (map (fn [coords]
                             (future
                               (let [fcoll (gis/->fcoll
                                            [(gis/->feature {:type "Point"
                                                             :coordinates coords})])]
                                 (diversity/recalc-grid! (test-search) fcoll))))
                           test-points)]

          ;; Wait for all to complete
          (doseq [f futures] @f)

          ;; Verify all results were captured
          (is (= (count test-points) (count @results)))

          ;; Verify no data corruption
          (doseq [result @results]
            (is (seq result))
            (is (every? #(or (contains? % :index) (contains? % :grd_id)) result))))))))

;;; Timeout handling tests ;;;

(deftest osrm-site-timeout-test
  (testing "site timeout constant is defined and positive"
    (is (pos? diversity/osrm-site-timeout-ms))
    ;; Should be at least 30 seconds
    (is (>= diversity/osrm-site-timeout-ms 30000))))

(deftest process-sites-chunk-timeout-handling-test
  (testing "handles OSRM timeout gracefully by returning nil for osrm field"
    (let [;; Mock that simulates a timeout by sleeping longer than the timeout
          slow-mock (fn [_params]
                      (Thread/sleep 200) ; Simulate slow response
                      {:distances [[100]] :durations [[10]]})]

      (with-redefs [osrm/get-data slow-mock
                    ;; Use very short timeout to trigger timeout behavior
                    diversity/osrm-site-timeout-ms 50]
        (let [test-site {:_id "test-site"
                         :_source {:status "active"
                                   :type {:type-code 1520}
                                   :search-meta
                                   {:location
                                    {:simple-geoms
                                     {:features
                                      [{:geometry {:coordinates [24.948 60.168]
                                                   :type "Point"}}]}}}}}
              coords [24.9477 60.1678]
              result (diversity/process-sites-chunk [test-site] coords prn)]
          ;; Should return a result even if OSRM times out
          (is (seq result))
          (is (= "test-site" (:id (first result))))
          ;; OSRM field should be nil due to timeout
          (is (nil? (:osrm (first result)))))))))

(comment
  (setup-test-system!)
  (clojure.test/run-test-var #'process-grid-item-test)
  (clojure.test/run-test-var #'osrm-site-timeout-test)
  (clojure.test/run-test-var #'process-sites-chunk-timeout-handling-test)
  (seed-test-data! (:lipas/search @test-system)))
