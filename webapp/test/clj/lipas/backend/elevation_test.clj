(ns lipas.backend.elevation-test
  "Tests for elevation enrichment.

  Integration tests mock the MML fetch (`get-elevation-coverage`) with
  synthetic ESRI ascii grids built from a deterministic, cell-unique
  elevation function, so exact z-values can be asserted without network
  access. The old (pre vertex-driven optimization) implementation is
  copied at the bottom of this namespace and used to assert exact
  old-vs-new equivalence of the produced values."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [lipas.backend.elevation :as elevation]
            [lipas.backend.gis :as gis]
            [lipas.jobs.patterns :as patterns])
  (:import [clojure.lang ExceptionInfo]))

;;; Synthetic elevation model ;;;

(defn synthetic-z-str
  "Deterministic, cell-unique elevation for the 2m cell with lower left
  corner [cx cy] (TM35FIN), as it would appear in the ascii grid body.
  cx + cy/1e7 is injective over the whole coverage envelope, so reading
  the wrong cell always produces a different value."
  [cx cy]
  (format "%.7f" (+ cx (/ cy 1e7))))

(defn expected-z
  "The z value the synthetic coverage yields for a WGS84 coordinate:
  computed through the same format+parse round trip as the mock grids."
  [coords]
  (let [[x y] (gis/wgs84->tm35fin-no-wrap coords)
        cx    (* 2 (long (Math/floor (/ x 2))))
        cy    (* 2 (long (Math/floor (/ y 2))))]
    (parse-double (synthetic-z-str cx cy))))

(defn synthetic-grid-body
  "ESRI ascii grid text covering the envelope with synthetic-z values.
  Works for any 2m-aligned envelope (both the new fixed 250m chunks and
  the old geometry-envelope-aligned chunks)."
  [{:keys [min-x max-x min-y max-y]}]
  (let [ncols (long (/ (- max-x min-x) 2))
        nrows (long (/ (- max-y min-y) 2))]
    (str "NCOLS " ncols "\r\n"
         "NROWS " nrows "\r\n"
         "XLLCORNER " min-x "\r\n"
         "YLLCORNER " min-y "\r\n"
         "CELLSIZE 2.0\r\n"
         "NODATA_VALUE -9999\r\n"
         (str/join "\n"
                   (for [row (range nrows)] ;; rows top-down (north first)
                     (let [cy (+ min-y (* 2 (- nrows 1 row)))]
                       (str/join " "
                                 (for [col (range ncols)]
                                   (synthetic-z-str (+ min-x (* 2 col)) cy)))))))))

(defn synthetic-coverage
  "Drop-in mock for `elevation/get-elevation-coverage`: parses a
  synthetic grid body with the real parser."
  [envelope]
  (elevation/parse-ascii-grid (synthetic-grid-body envelope)))

(defn counting
  "Wrap f so calls are counted in the counter atom."
  [counter f]
  (fn [& args]
    (swap! counter inc)
    (apply f args)))

;;; Pure function tests ;;;

(deftest fit-to-coverage-test
  (testing "Rounds to even coordinates (2m cell alignment)"
    (is (= {:min-x 400000 :min-y 6900000 :max-x 400010 :max-y 6900012}
           (elevation/fit-to-coverage
            {:min-x 400001.4 :min-y 6900000.6 :max-x 400010.5 :max-y 6900011.9}
            elevation/coverage-info))))

  (testing "Even values pass through"
    (is (= {:min-x 400002 :min-y 6900004 :max-x 400006 :max-y 6900008}
           (elevation/fit-to-coverage
            {:min-x 400002.0 :min-y 6900004.0 :max-x 400006.0 :max-y 6900008.0}
            elevation/coverage-info))))

  (testing "Clamps to the coverage envelope"
    (let [{:keys [envelope]} elevation/coverage-info
          fitted (elevation/fit-to-coverage
                  {:min-x 0.0 :min-y 0.0 :max-x 99999999.0 :max-y 99999999.0}
                  elevation/coverage-info)]
      (is (= {:min-x (:min-x envelope) :min-y (:min-y envelope)
              :max-x (:max-x envelope) :max-y (:max-y envelope)}
             fitted)))))

(deftest parse-ascii-grid-test
  (testing "Headers with CRLF line endings (MML response format)"
    (let [dada    (str "NCOLS 1\r\nNROWS 1\r\nXLLCORNER 434354.531249997672\r\n"
                       "YLLCORNER 6943965.504886634648\r\nCELLSIZE 1.000000000000\r\n"
                       "NODATA_VALUE -9999\r\n")
          headers (elevation/parse-ascii-grid-headers (str/split-lines dada))]
      (is (= 1 (:ncols headers)))
      (is (= 1 (:nrows headers)))
      (is (= 434354.531249997672 (:xllcorner headers)))
      (is (= 6943965.504886634648 (:yllcorner headers)))
      (is (= 1.0 (:cellsize headers)))
      (is (= -9999 (:nodata_value headers)))))

  (testing "Data rows incl. negative and NODATA values"
    (let [s      (str "NCOLS 3\r\nNROWS 2\r\nXLLCORNER 434352\r\nYLLCORNER 6943964\r\n"
                      "CELLSIZE 2.0\r\nNODATA_VALUE -9999\r\n"
                      "152.55 -0.34 -9999\n"
                      "152.39 152.3 7.0\n")
          parsed (elevation/parse-ascii-grid s)]
      (is (= [[152.55 -0.34 -9999.0]
              [152.39 152.3 7.0]]
             (:data parsed)))))

  (testing "Data is fully realized vectors for O(1) lookups"
    (let [parsed (elevation/parse-ascii-grid
                  "NCOLS 2\r\nNROWS 1\r\nXLLCORNER 0\r\nYLLCORNER 0\r\nCELLSIZE 2.0\r\n1.0 2.0\n")]
      (is (vector? (:data parsed)))
      (is (every? vector? (:data parsed)))))

  (testing "Trailing empty lines are skipped"
    (let [parsed (elevation/parse-ascii-grid
                  "NCOLS 1\r\nNROWS 1\r\nXLLCORNER 0\r\nYLLCORNER 0\r\nCELLSIZE 2.0\r\n1.0\n\n")]
      (is (= [[1.0]] (:data parsed))))))

(deftest chunk-key-test
  (testing "Chunk keys are quotients of the fixed 250m grid"
    (is (= [1600 27600] (elevation/chunk-key [400000.0 6900000.0])))
    (is (= [1600 27600] (elevation/chunk-key [400249.99 6900249.99])))
    (is (= [1601 27601] (elevation/chunk-key [400250.0 6900250.0])))
    (is (= [1599 27599] (elevation/chunk-key [399999.99 6899999.99]))))

  (testing "Chunk envelope corners are 2m-aligned multiples of 250"
    (let [env (elevation/chunk-key->envelope [1600 27600])]
      (is (= {:min-x 400000 :max-x 400250 :min-y 6900000 :max-y 6900250} env))
      (is (every? even? (vals env)))))

  (testing "Every TM35FIN point is inside its own chunk envelope"
    (doseq [p [[400000.0 6900000.0] [400249.99 6900249.99]
               [434355.5312 6943966.5048] [44000.01 6594000.01]]]
      (let [[x y] p
            {:keys [min-x max-x min-y max-y]} (elevation/chunk-key->envelope
                                               (elevation/chunk-key p))]
        (is (and (>= x min-x) (< x max-x) (>= y min-y) (< y max-y))))))

  (testing "Chunk envelopes are clamped to the coverage envelope"
    (let [{:keys [envelope]} elevation/coverage-info]
      ;; First chunk column inside coverage
      (is (= (:min-x envelope)
             (:min-x (elevation/chunk-key->envelope [(/ (:min-x envelope) 250) 27600]))))
      ;; Chunk fully outside coverage collapses to a degenerate
      ;; envelope (min > max) whose MML request fails the job
      (let [env (elevation/chunk-key->envelope [0 27600])]
        (is (> (:min-x env) (:max-x env)))))))

(deftest plan-fetches-test
  (testing "No vertices -> no requests"
    (is (= [] (elevation/plan-fetches []))))

  (testing "Single vertex -> one small envelope around it"
    (let [[{:keys [envelope chunk-keys]} :as plan]
          (elevation/plan-fetches [[400010.3 6900020.7]])]
      (is (= 1 (count plan)))
      (is (= [[1600 27600]] chunk-keys))
      ;; vertex +/- 8m buffer, fitted to even coordinates
      (is (= {:min-x 400002 :max-x 400018 :min-y 6900012 :max-y 6900028} envelope))))

  (testing "Small geometry straddling a chunk border -> still one request"
    (let [plan (elevation/plan-fetches [[400240.0 6900010.0] [400260.0 6900030.0]])]
      (is (= 1 (count plan)))
      (is (= #{[1600 27600] [1601 27600]} (set (:chunk-keys (first plan)))))))

  (testing "Dense cluster (>= 4 chunks in a 1000m tile) merges into one tile request"
    (let [verts [[400010.0 6900010.0] [400260.0 6900010.0]
                 [400510.0 6900010.0] [400760.0 6900010.0]]
          [{:keys [envelope chunk-keys]} :as plan] (elevation/plan-fetches verts)]
      (is (= 1 (count plan)))
      (is (= {:min-x 400000 :max-x 401000 :min-y 6900000 :max-y 6901000} envelope))
      (is (= 4 (count chunk-keys)))))

  (testing "Sparse chunks (< 4 per tile) are fetched individually"
    (let [verts [[400010.0 6900010.0] [400260.0 6900010.0] [400510.0 6900010.0]]
          plan  (elevation/plan-fetches verts)]
      (is (= 3 (count plan)))
      (is (every? #(= 1 (count (:chunk-keys %))) plan))
      (is (= {:min-x 400000 :max-x 400250 :min-y 6900000 :max-y 6900250}
             (:envelope (first plan))))))

  (testing "Mixed: dense tile merges, distant chunk stays individual"
    (let [verts [[400010.0 6900010.0] [400260.0 6900010.0]
                 [400510.0 6900010.0] [400760.0 6900010.0]
                 [450010.0 6950010.0]]
          plan  (elevation/plan-fetches verts)]
      (is (= 2 (count plan)))))

  (testing "Every distinct chunk key appears in exactly one plan entry"
    (let [verts (for [i (range 40)] [(+ 400010.0 (* i 130.0)) (+ 6900010.0 (* i 45.0))])
          keys* (distinct (map elevation/chunk-key verts))
          plan  (elevation/plan-fetches verts)]
      (is (= (sort keys*) (sort (mapcat :chunk-keys plan))))
      (testing "and each entry's envelope contains its chunk keys' vertices"
        (doseq [{:keys [envelope chunk-keys]} plan
                [x y] verts
                :when (some #{(elevation/chunk-key [x y])} chunk-keys)]
          (is (and (>= x (:min-x envelope)) (<= x (:max-x envelope))
                   (>= y (:min-y envelope)) (<= y (:max-y envelope)))))))))

(deftest geometry-vertices-test
  (testing "Point"
    (is (= [[25.7 62.6]]
           (elevation/geometry-vertices
            {:type "FeatureCollection"
             :features [{:type "Feature"
                         :geometry {:type "Point" :coordinates [25.7 62.6]}}]}))))

  (testing "LineString"
    (is (= [[25.7 62.6] [25.8 62.7]]
           (elevation/geometry-vertices
            {:type "FeatureCollection"
             :features [{:type "Feature"
                         :geometry {:type "LineString"
                                    :coordinates [[25.7 62.6] [25.8 62.7]]}}]}))))

  (testing "Polygon with a hole includes every ring's vertices"
    (is (= [[25.0 62.0] [25.1 62.0] [25.1 62.1] [25.0 62.0]
            [25.04 62.04] [25.06 62.04] [25.06 62.06] [25.04 62.04]]
           (elevation/geometry-vertices
            {:type "FeatureCollection"
             :features [{:type "Feature"
                         :geometry {:type "Polygon"
                                    :coordinates
                                    [[[25.0 62.0] [25.1 62.0] [25.1 62.1] [25.0 62.0]]
                                     [[25.04 62.04] [25.06 62.04] [25.06 62.06] [25.04 62.04]]]}}]}))))

  (testing "Multiple features concatenate in order"
    (is (= [[25.7 62.6] [25.8 62.7] [25.9 62.8]]
           (elevation/geometry-vertices
            {:type "FeatureCollection"
             :features [{:type "Feature"
                         :geometry {:type "LineString"
                                    :coordinates [[25.7 62.6] [25.8 62.7]]}}
                        {:type "Feature"
                         :geometry {:type "Point" :coordinates [25.9 62.8]}}]}))))

  (testing "Unexpected geometry type throws"
    (is (thrown? ExceptionInfo
                 (elevation/geometry-vertices
                  {:type "FeatureCollection"
                   :features [{:type "Feature"
                               :geometry {:type "MultiPoint"
                                          :coordinates [[25.7 62.6]]}}]})))))

(deftest resolve-elevation-test
  ;; Reference case from the original implementation's comment block:
  ;; test-point resolves to 152.34 from this 2x2 grid.
  (let [test-point [25.720539797408946 62.62057217751676] ;; tm35fin ~[434355.53 6943966.50]
        k          (elevation/chunk-key (gis/wgs84->tm35fin-no-wrap test-point))
        grid       {:headers {:ncols 2 :nrows 2 :xllcorner 434352.0
                              :yllcorner 6943964.0 :cellsize 2.0}
                    ;; rows top-down as parsed from the ascii grid
                    :data [[152.55 152.34] [152.39 152.3]]}
        index      {k (elevation/index-grid grid)}]

    (testing "Row/col math relative to the grid's lower left corner"
      (is (= 152.34 (elevation/resolve-elevation index test-point))))

    (testing "Col and row are capped to the grid bounds (edge cells)"
      (let [tiny  {k (elevation/index-grid
                      {:headers {:ncols 1 :nrows 1 :xllcorner 434352.0
                                 :yllcorner 6943964.0 :cellsize 2.0}
                       :data [[152.55]]})}]
        ;; col/row would compute to 1 but are capped to ncols-1 = 0
        (is (= 152.55 (elevation/resolve-elevation tiny test-point)))))

    (testing "NODATA value passes through unchanged"
      (let [nodata {k (elevation/index-grid
                       {:headers {:ncols 2 :nrows 2 :xllcorner 434352.0
                                  :yllcorner 6943964.0 :cellsize 2.0}
                        :data [[-9999.0 -9999.0] [-9999.0 -9999.0]]})}]
        (is (= -9999.0 (elevation/resolve-elevation nodata test-point)))))

    (testing "Vertex without a covering grid throws a descriptive error"
      (is (thrown-with-msg? ExceptionInfo #"No elevation grid covers coordinate"
                            (elevation/resolve-elevation {} test-point))))))

(deftest index-grid-test
  (testing "Rows are reversed so the lower left corner comes first"
    (is (= {:headers {:ncols 2}
            :rows    [[3.0 4.0] [1.0 2.0]]}
           (elevation/index-grid {:headers {:ncols 2}
                                  :data    [[1.0 2.0] [3.0 4.0]]})))))

;;; Integration tests with mocked MML fetch ;;;

(def point-fcoll
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :properties {:name "test point"}
     :geometry {:type "Point"
                :coordinates [25.720539797408946 62.62057217751676]}}]})

(def route-fcoll
  "Two-feature route whose 4 vertices land in 3 distinct chunks. The
  old implementation fetched 165 corridor chunks for this geometry."
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :properties {:name "leg 1"}
     :geometry {:type "LineString"
                :coordinates [[26.2436753445903 63.9531598143881]
                              [26.4505514903968 63.9127506671744]]}}
    {:type "Feature"
     :properties {:name "leg 2"}
     :geometry {:type "LineString"
                :coordinates [[26.2436550567509 63.9531552213109]
                              [25.7583312263512 63.9746827436437]]}}]})

(def polygon-fcoll
  {:type "FeatureCollection"
   :features
   [{:type "Feature"
     :geometry {:type "Polygon"
                :coordinates
                [[[25.72 62.62] [25.7205 62.62] [25.7205 62.6205] [25.72 62.62]]
                 [[25.7201 62.6201] [25.7203 62.6201] [25.7203 62.6202] [25.7201 62.6201]]]}}]})

(deftest enrich-point-test
  (let [requests (atom 0)]
    (with-redefs [elevation/get-elevation-coverage (counting requests synthetic-coverage)]
      (let [enriched (elevation/enrich-elevation point-fcoll)
            [x y z]  (-> enriched :features first :geometry :coordinates)]
        (testing "One vertex needs exactly one MML request"
          (is (= 1 @requests)))
        (testing "x/y preserved, z resolved from the containing 2m cell"
          (is (= [25.720539797408946 62.62057217751676] [x y]))
          (is (= (expected-z [x y]) z)))
        (testing "Feature structure and properties pass through"
          (is (= (assoc-in point-fcoll [:features 0 :geometry :coordinates]
                           [x y z])
                 (update enriched :features vec))))))))

(deftest enrich-multi-chunk-route-test
  (let [requests (atom 0)]
    (with-redefs [elevation/get-elevation-coverage (counting requests synthetic-coverage)]
      (let [enriched (elevation/enrich-elevation route-fcoll)]
        (testing "Chunk count is vertex-driven: 4 vertices / 3 distinct chunks"
          (is (= 3 @requests)))
        (testing "Every vertex gets the exact synthetic z of its own cell"
          (doseq [f    (:features enriched)
                  [x y z] (-> f :geometry :coordinates)]
            (is (= (expected-z [x y]) z))))
        (testing "Feature order and properties are preserved"
          (is (= ["leg 1" "leg 2"]
                 (mapv #(-> % :properties :name) (:features enriched))))
          (is (= (mapv #(mapv (fn [c] (subvec c 0 2)) (-> % :geometry :coordinates))
                       (:features route-fcoll))
                 (mapv #(mapv (fn [c] (subvec c 0 2)) (-> % :geometry :coordinates))
                       (vec (:features enriched))))))))))

(deftest enrich-polygon-test
  (with-redefs [elevation/get-elevation-coverage synthetic-coverage]
    (let [enriched (elevation/enrich-elevation polygon-fcoll)]
      (testing "All ring vertices (incl. hole) get exact z values"
        (doseq [ring    (-> enriched :features first :geometry :coordinates)
                [x y z] ring]
          (is (= (expected-z [x y]) z)))))))

(deftest enrich-vertices-in-same-chunk-dedupe-test
  (let [requests (atom 0)
        ;; ~20 vertices a few meters apart -> all in one 250m chunk
        fcoll    {:type "FeatureCollection"
                  :features
                  [{:type "Feature"
                    :geometry {:type "LineString"
                               :coordinates (vec (for [i (range 20)]
                                                   [(+ 25.7201 (* i 0.00001))
                                                    (+ 62.6201 (* i 0.00001))]))}}]}]
    (with-redefs [elevation/get-elevation-coverage (counting requests synthetic-coverage)]
      (let [enriched (elevation/enrich-elevation fcoll)]
        (testing "All vertices in one chunk -> a single MML request"
          (is (= 1 @requests)))
        (testing "All z values exact"
          (doseq [[x y z] (-> enriched :features first :geometry :coordinates)]
            (is (= (expected-z [x y]) z))))))))

(deftest enrich-fetch-failure-test
  (testing "Any chunk fetch failure fails the whole enrichment"
    (with-redefs [elevation/get-elevation-coverage
                  (fn [envelope]
                    (if (< (:min-x envelope) 462000)
                      (synthetic-coverage envelope)
                      (throw (ex-info "MML API error" {:status 500 :envelope envelope}))))]
      (is (thrown? Exception (elevation/enrich-elevation route-fcoll))))))

(deftest get-elevation-coverage-error-handling-test
  (testing "HTTP 4xx/5xx throws with response context"
    (with-redefs [clj-http.client/get (fn [_url _opts] {:status 503 :body "unavailable"})]
      (is (thrown-with-msg? ExceptionInfo #"MML API error"
                            (elevation/get-elevation-coverage
                             {:min-x 400000 :max-x 400250 :min-y 6900000 :max-y 6900250})))))

  (testing "Unexpected status throws"
    (with-redefs [clj-http.client/get (fn [_url _opts] {:status 302})]
      (is (thrown-with-msg? ExceptionInfo #"Unexpected MML API response"
                            (elevation/get-elevation-coverage
                             {:min-x 400000 :max-x 400250 :min-y 6900000 :max-y 6900250})))))

  (testing "HTTP 200 parses the grid"
    (with-redefs [clj-http.client/get
                  (fn [_url _opts]
                    {:status 200
                     :body   (synthetic-grid-body {:min-x 400000 :max-x 400004
                                                   :min-y 6900000 :max-y 6900004})})]
      (let [grid (elevation/get-elevation-coverage
                  {:min-x 400000 :max-x 400004 :min-y 6900000 :max-y 6900004})]
        (is (= 2 (-> grid :headers :ncols)))
        (is (= 2 (count (:data grid))))))))

(deftest bounded-concurrency-test
  (let [in-flight     (atom 0)
        max-in-flight (atom 0)
        chunk-keys    (for [i (range 30)] [(+ 1600 i) 27600])
        fetch-plan    (for [k chunk-keys]
                        {:envelope (elevation/chunk-key->envelope k) :chunk-keys [k]})
        slow-fetch    (fn [_envelope]
                        (let [n (swap! in-flight inc)]
                          (swap! max-in-flight max n)
                          (Thread/sleep 25)
                          (swap! in-flight dec)
                          {:headers {:ncols 1 :nrows 1 :xllcorner 0.0
                                     :yllcorner 0.0 :cellsize 2.0}
                           :data    [[42.0]]}))]
    (with-redefs [elevation/get-elevation-coverage slow-fetch]
      (let [grids (elevation/fetch-grids fetch-plan)]
        (testing "All chunks fetched and indexed by their key"
          (is (= 30 (count grids)))
          (is (= (set chunk-keys) (set (keys grids)))))
        (testing "Concurrency never exceeds max-concurrent-fetches"
          (is (<= @max-in-flight elevation/max-concurrent-fetches)))
        (testing "Fetches actually run in parallel"
          (is (> @max-in-flight 1)))))))

(deftest enrich-dense-route-tile-merge-test
  ;; ~3 km of vertices every ~25 m: enough chunk density that at
  ;; least one 1000m tile merge kicks in.
  (let [start    [25.7201 62.6201]
        fcoll    {:type "FeatureCollection"
                  :features
                  [{:type "Feature"
                    :geometry {:type "LineString"
                               :coordinates (vec (for [i (range 120)]
                                                   [(+ (first start) (* i 0.0005))
                                                    (second start)]))}}]}
        verts    (mapv gis/wgs84->tm35fin-no-wrap (elevation/geometry-vertices fcoll))
        n-chunks (count (distinct (map elevation/chunk-key verts)))
        plan     (elevation/plan-fetches verts)
        requests (atom 0)]
    (with-redefs [elevation/get-elevation-coverage (counting requests synthetic-coverage)]
      (let [enriched (elevation/enrich-elevation fcoll)]
        (testing "Dense chunks merge into tiles: fewer requests than chunks"
          (is (< (count plan) n-chunks))
          (is (some #(> (count (:chunk-keys %)) 1) plan))
          (is (= (count plan) @requests)))
        (testing "Merged grids resolve the exact same synthetic z values"
          (doseq [[x y z] (-> enriched :features first :geometry :coordinates)]
            (is (= (expected-z [x y]) z))))))))

;;; Old (pre vertex-driven) implementation, kept for equivalence tests ;;;

(defn- old-resolve-elevation
  [coords elevations]
  (let [[lon lat]              (gis/wgs84->tm35fin-no-wrap coords)
        {:keys [headers data]} (->> elevations
                                    (some
                                     (fn [{:keys [headers] :as elevation}]
                                       (let [min-x (:xllcorner headers)
                                             min-y (:yllcorner headers)
                                             max-x (+ min-x (* (:ncols headers)
                                                               (:cellsize headers)))
                                             max-y (+ min-y (* (:nrows headers)
                                                               (:cellsize headers)))]
                                         (and (>= max-x lon min-x)
                                              (>= max-y lat min-y)
                                              elevation)))))
        col (min (long (Math/floor
                        (/ (- lon (:xllcorner headers))
                           (:cellsize headers))))
                 (dec (:ncols headers)))
        row (min (long (Math/floor
                        (/ (- lat (:yllcorner headers))
                           (:cellsize headers))))
                 (dec (:nrows headers)))]
    (-> data rseq (nth row) (nth col))))

(defn- old-append-elevations
  [fcoll elevations]
  (update fcoll :features
          (fn [fs]
            (map
             (fn [f]
               (update-in f [:geometry :coordinates]
                          (fn [coords]
                            (condp = (-> f :geometry :type)
                              "Point"      (let [[x y] coords]
                                             [x y (old-resolve-elevation coords elevations)])
                              "LineString" (mapv
                                            (fn [coords]
                                              (let [[x y] coords]
                                                [x y (old-resolve-elevation coords elevations)]))
                                            coords)
                              "Polygon"    (mapv
                                            (fn [coords]
                                              (mapv
                                               (fn [coords]
                                                 (let [[x y] coords]
                                                   [x y (old-resolve-elevation coords elevations)]))
                                               coords))
                                            coords)
                              (throw (ex-info "Encountered unexpected geometry type" f))))))
             fs))))

(defn old-enrich-elevation
  "The implementation before the vertex-driven optimization: corridor
  chunking over the buffered geometry envelope + linear grid scans."
  [fcoll]
  (let [buff-m    8
        fcoll-jts (-> fcoll gis/->jts-geom gis/transform-crs)
        envelopes (-> (gis/get-envelope fcoll-jts buff-m)
                      (elevation/fit-to-coverage elevation/coverage-info)
                      (gis/chunk-envelope elevation/query-envelope-size-m)
                      (->> (filter #(gis/intersects-envelope? % fcoll-jts))))]
    (->> envelopes
         (patterns/pmap-with-timeout elevation/elevation-chunk-timeout-ms
                                     elevation/get-elevation-coverage)
         (old-append-elevations fcoll))))

(deftest old-vs-new-equivalence-test
  ;; Short geometries keep the old implementation's corridor chunk
  ;; count (and thus test runtime) small.
  (let [short-route {:type "FeatureCollection"
                     :features
                     [{:type "Feature"
                       :properties {:name "short"}
                       :geometry {:type "LineString"
                                  :coordinates [[25.7201 62.6201]
                                                [25.7245 62.6215]
                                                [25.7280 62.6230]]}}]}]
    (with-redefs [elevation/get-elevation-coverage synthetic-coverage]
      (doseq [fcoll [point-fcoll short-route polygon-fcoll]]
        (testing (str "Identical output for " (-> fcoll :features first :geometry :type))
          (let [old (old-enrich-elevation fcoll)
                new (elevation/enrich-elevation fcoll)]
            (is (= (update old :features vec)
                   (update new :features vec)))))))))

(comment
  (clojure.test/run-tests 'lipas.backend.elevation-test))
