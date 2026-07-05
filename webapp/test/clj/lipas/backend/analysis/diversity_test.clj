(ns lipas.backend.analysis.diversity-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [integrant.core :as ig]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.config :as config]
            [lipas.backend.gis :as gis]
            [lipas.backend.osrm :as osrm]
            [lipas.backend.search :as search]
            [lipas.test-utils :as test-utils]))

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

(def profile-factors
  "Route distance = haversine distance * factor in the mock, so expected
  minimums can be computed independently in assertions."
  {:car 1.0 :bicycle 1.1 :foot 1.25})

(def profile-speeds-m-s
  {:car 10.0 :bicycle 5.0 :foot 1.4})

(defn- parse-coord [s]
  (mapv #(Double/parseDouble %) (str/split s #",")))

(defn mock-osrm-matrix
  "Matrix-shaped mock for osrm/get-data: distances[i][j] is the haversine
  distance from source i to destination j scaled by a per-profile factor."
  [{:keys [profile sources destinations] :as _params}]
  (let [factor (profile-factors profile)
        speed (profile-speeds-m-s profile)]
    {:code "Ok"
     :distances (mapv (fn [src]
                        (mapv (fn [dest]
                                (* factor (gis/haversine (parse-coord src)
                                                         (parse-coord dest))))
                              destinations))
                      sources)
     :durations (mapv (fn [src]
                        (mapv (fn [dest]
                                (/ (* factor (gis/haversine (parse-coord src)
                                                            (parse-coord dest)))
                                   speed))
                              destinations))
                      sources)}))

(def test-point-fcoll
  (gis/->fcoll
   [(gis/->feature {:type "Point"
                    :coordinates [24.9477 60.1678]})]))

(defn- run-recalc-capturing-bulk!
  "Run recalc-grid! with bulk indexing captured instead of written.
  Returns the indexed grid docs keyed by grd_id."
  ([] (run-recalc-capturing-bulk! mock-osrm-matrix))
  ([osrm-mock]
   (let [bulk-calls (atom [])]
     (with-redefs [osrm/get-data osrm-mock
                   search/bulk-index-sync! (fn [_ data]
                                             (swap! bulk-calls conj data)
                                             {:created (count data)})]
       (diversity/recalc-grid! (test-search) test-point-fcoll))
     (->> @bulk-calls
          (mapcat identity)
          (filter :grd_id)
          (reduce (fn [m doc] (assoc m (:grd_id doc) doc)) {})))))

(defn- site-coords [site-id]
  (->> test-sports-sites
       (filter #(= site-id (:_id %)))
       first
       :_source :search-meta :location :geometries :coordinates))

(defn- expected-distance [grid-item site-id profile]
  (let [cell-coords (gis/wkt-point->coords (:WKT grid-item))]
    (* (profile-factors profile)
       (gis/haversine cell-coords (site-coords site-id)))))

(defn- approx= [a b]
  (and (number? a) (number? b) (< (Math/abs (- (double a) (double b))) 0.001)))

;;; Tests ;;;

(deftest fetch-grid-test
  (testing "Fetching grid items within radius"
    (seed-test-data! (test-search))
    (let [result (diversity/fetch-grid (test-search) test-point-fcoll 1)]
      (is (seq result))
      (is (every? #(contains? % :_source) result))
      ;; fetch-grid applies no sort, so ES returns the matching cells in
      ;; arbitrary order - assert the full set, not (first result), or the
      ;; test flakes when the segment layout flips which cell comes first.
      (is (= #{"250mN667175E38600" "250mN667150E38600"}
             (set (map #(-> % :_source :grd_id) result)))))))

(deftest recalc-grid-structure-test
  (testing "Recalculated docs preserve cell fields and carry full site entries"
    (seed-test-data! (test-search))
    (let [docs (run-recalc-capturing-bulk!)]
      (is (= #{"250mN667175E38600" "250mN667150E38600"} (set (keys docs))))

      (doseq [[_ doc] docs]
        ;; Original grid cell fields preserved
        (is (string? (:WKT doc)))
        (is (string? (:vaesto doc)))
        (is (= "Helsinki" (:kunta doc)))

        ;; All 3 test sites are within 2km of both test cells
        (is (= #{"site-1" "site-2" "site-3"}
               (set (map :id (:sports-sites doc)))))

        (doseq [site (:sports-sites doc)]
          (is (contains? site :type-code))
          (is (contains? site :status))
          (is (= #{:car :bicycle :foot} (set (keys (:osrm site)))))
          (doseq [[_ mins] (:osrm site)]
            (is (number? (:distance-m mins)))
            (is (number? (:duration-s mins)))))))))

(deftest recalc-grid-distance-correctness-test
  (testing "Stored minimums match independently computed expected distances"
    (seed-test-data! (test-search))
    (let [docs (run-recalc-capturing-bulk!)]
      (doseq [grid-item test-grid-items
              site-id ["site-1" "site-2" "site-3"]
              profile [:car :bicycle :foot]]
        (let [doc (get docs (:grd_id grid-item))
              site (->> doc :sports-sites (filter #(= site-id (:id %))) first)
              expected (expected-distance grid-item site-id profile)
              actual (get-in site [:osrm profile :distance-m])]
          (is (approx= expected actual)
              (format "cell %s site %s profile %s: expected %.3f, got %s"
                      (:grd_id grid-item) site-id (name profile)
                      (double expected) (pr-str actual)))))

      (testing "durations are scaled by profile speed"
        (let [doc (get docs "250mN667175E38600")
              site (->> doc :sports-sites (filter #(= "site-1" (:id %))) first)]
          (is (approx= (/ (expected-distance (first test-grid-items) "site-1" :foot)
                          (profile-speeds-m-s :foot))
                       (get-in site [:osrm :foot :duration-s]))))))))

(deftest recalc-grid-chunking-invariance-test
  (testing "Tiny max-table-locations forces chunked requests with identical results"
    (seed-test-data! (test-search))
    (let [request-sizes (atom [])
          counting-mock (fn [{:keys [sources destinations] :as params}]
                          (swap! request-sizes conj (+ (count sources)
                                                       (count destinations)))
                          (mock-osrm-matrix params))
          baseline (run-recalc-capturing-bulk!)
          chunked (with-redefs [diversity/max-table-locations 3]
                    (run-recalc-capturing-bulk! counting-mock))]
      ;; Chunking actually happened: more than one request per profile
      (is (> (count @request-sizes) 3))
      ;; Results are unaffected by chunking
      (is (= baseline chunked)))))

(deftest recalc-grid-profile-failure-test
  (testing "A failing profile is omitted; other profiles keep their data"
    (seed-test-data! (test-search))
    (let [failing-mock (fn [{:keys [profile] :as params}]
                         (when-not (= :bicycle profile)
                           (mock-osrm-matrix params)))
          docs (run-recalc-capturing-bulk! failing-mock)]
      (is (seq docs))
      (doseq [[_ doc] docs
              site (:sports-sites doc)]
        (is (= #{:car :foot} (set (keys (:osrm site)))))))))

(deftest recalc-grid-all-profiles-fail-test
  (testing "Total OSRM failure still indexes docs, with nil osrm per site"
    (seed-test-data! (test-search))
    (let [docs (run-recalc-capturing-bulk! (constantly nil))]
      (is (= #{"250mN667175E38600" "250mN667150E38600"} (set (keys docs))))
      (doseq [[_ doc] docs
              site (:sports-sites doc)]
        (is (nil? (:osrm site)))))))

(deftest recalc-grid-timeout-test
  (testing "Slow OSRM requests time out and are treated as failed profiles"
    (seed-test-data! (test-search))
    (let [slow-mock (fn [params]
                      (Thread/sleep 200)
                      (mock-osrm-matrix params))
          docs (with-redefs [diversity/osrm-table-timeout-ms 50]
                 (run-recalc-capturing-bulk! slow-mock))]
      ;; Docs are still indexed, but without OSRM data
      (is (= #{"250mN667175E38600" "250mN667150E38600"} (set (keys docs))))
      (doseq [[_ doc] docs
              site (:sports-sites doc)]
        (is (nil? (:osrm site)))))))

(deftest concurrent-processing-test
  (testing "Concurrent recalculations don't corrupt data"
    (seed-test-data! (test-search))
    (let [test-points [[24.9477 60.1678]
                       [24.9500 60.1700]
                       [24.9400 60.1650]]
          results (atom [])]

      (with-redefs [osrm/get-data mock-osrm-matrix
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

          ;; Every recalc produced at least one bulk call (per tile)
          (is (>= (count @results) (count test-points)))

          ;; Verify no data corruption
          (doseq [result @results]
            (is (seq result))
            (is (every? #(or (contains? % :index) (contains? % :grd_id)) result))
            (doseq [doc (filter :grd_id result)]
              (is (vector? (:sports-sites doc))))))))))

(comment
  (clojure.test/run-test-var #'recalc-grid-distance-correctness-test)
  (seed-test-data! (:lipas/search @test-system)))
