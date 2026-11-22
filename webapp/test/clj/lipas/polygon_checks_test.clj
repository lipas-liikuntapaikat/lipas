(ns lipas.polygon-checks-test
  "The editor's polygon checks (lipas.polygon-checks), with the shapes
  accidental clicks saved in production (2026-09..11)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.gis :as gis]
            [lipas.polygon-checks :as sut]))

(defn- polygon [rings & {:keys [id]}]
  (cond-> {:type "Feature" :geometry {:type "Polygon" :coordinates rings}}
    id (assoc :id id)))

(defn- fcoll [& features] {:type "FeatureCollection" :features (vec features)})

(def ^:private point-repeated
  "621743's first polygon (2026-11-17): two double-clicks on one spot."
  [[27.71245077973908 60.57303669809983] [27.71245077973908 60.57303669809983]
   [27.71245077973908 60.57303669809983] [27.71245077973908 60.57303669809983]])

(def ^:private two-points
  "620978's only polygon (2026-09-01)."
  [[22.904854042170857 61.33789397958819] [22.904854042170857 61.33789397958819]
   [22.904893357323303 61.337895166083754] [22.904854042170857 61.33789397958819]])

(def ^:private collinear [[25.0 60.0] [25.001 60.0] [25.002 60.0] [25.0 60.0]])

(def ^:private square [[25.0 60.0] [25.001 60.0] [25.001 60.001] [25.0 60.001] [25.0 60.0]])

(deftest problem-test
  (testing "the shapes accidental clicks saved"
    (is (= :too-few-points (sut/problem [point-repeated])))
    (is (= :too-few-points (sut/problem [two-points]))))
  (testing "points on one line have no area"
    (is (= :no-area (sut/problem [collinear]))))
  (testing "a real polygon is fine, duplicates or not"
    (is (nil? (sut/problem [square])))
    (is (nil? (sut/problem [(vec (mapcat #(repeat 2 %) square))]))))
  (testing "a tiny but real polygon is fine: about 1 m²"
    (is (nil? (sut/problem [[[25.0 60.0] [25.00002 60.0] [25.00002 60.00001] [25.0 60.0]]])))))

(deftest clean-test
  (testing "consecutive duplicates go, the closing position stays"
    (is (= [[[25.0 60.0] [25.001 60.0] [25.001 60.001] [25.0 60.0]]]
           (-> (fcoll (polygon [[[25.0 60.0] [25.0 60.0] [25.001 60.0] [25.001 60.0]
                                 [25.001 60.001] [25.0 60.0] [25.0 60.0]]]))
               sut/clean
               (get-in [:features 0 :geometry :coordinates])))))
  (testing "a hole without area goes, a real hole stays"
    (let [real-hole [[25.0002 60.0002] [25.0004 60.0002] [25.0004 60.0004] [25.0002 60.0002]]]
      (is (= [square real-hole]
             (-> (fcoll (polygon [square two-points real-hole]))
                 sut/clean
                 (get-in [:features 0 :geometry :coordinates]))))))
  (testing "a broken exterior stays, for `problems` to report"
    (is (= [[(first point-repeated)]]
           (get-in (sut/clean (fcoll (polygon [point-repeated]))) [:features 0 :geometry :coordinates]))))
  (testing "other features and empty collections are left alone"
    (let [line {:type "Feature" :geometry {:type "LineString" :coordinates [[25.0 60.0] [25.0 60.0]]}}]
      (is (= (fcoll line) (sut/clean (fcoll line)))))
    (is (nil? (sut/clean nil)))
    (is (= {:type "FeatureCollection" :features []} (sut/clean (fcoll))))))

(deftest problems-test
  (is (= [{:index 1 :id "b" :problem :too-few-points}
          {:index 2 :id nil :problem :no-area}]
         (sut/problems (fcoll (polygon [square] :id "a")
                              (polygon [point-repeated] :id "b")
                              (polygon [collinear]))))))

(deftest new-problems-test
  (let [saved (fcoll (polygon [square] :id "a")
                     (polygon [two-points] :id "old-broken")
                     (polygon [point-repeated]))]
    (testing "polygons saved broken don't block, with or without an id"
      ;; the editor gives id-less features an id, and cleans every polygon,
      ;; when the geometry changes
      (is (= [] (sut/new-problems saved
                                  (sut/clean (assoc-in saved [:features 2 :id] "given-by-editor"))))))
    (testing "a polygon broken while editing does"
      (is (= [{:index 0 :id "a" :problem :no-area}]
             (sut/new-problems saved (assoc-in saved [:features 0 :geometry :coordinates] [collinear])))))
    (testing "a new site has nothing saved"
      (is (= [:too-few-points] (map :problem (sut/new-problems nil (fcoll (polygon [two-points])))))))))

;;; Generative

(def ^:private gen-ring
  ;; Points on a coarse grid (about 50 m apart), so that rings repeat points
  ;; and run along lines; closed, as saved.
  (gen/fmap (fn [ps] (conj ps (first ps)))
            (gen/vector (gen/fmap (fn [[x y]] [(+ 25.0 (* x 0.001)) (+ 60.0 (* y 0.0005))])
                                  (gen/tuple (gen/choose 0 3) (gen/choose 0 3)))
                        1 7)))

(def ^:private gen-with-duplicates
  ;; `gen-ring` with random positions repeated in place, as (double-)clicks do
  (gen/let [ring gen-ring
            times (gen/vector (gen/frequency [[3 (gen/return 1)] [1 (gen/choose 2 4)]])
                              (count ring))]
    (vec (mapcat repeat times ring))))

(defspec dropping-duplicates-never-changes-the-verdict 300
  (prop/for-all [ring gen-with-duplicates]
                (let [cleaned (get-in (sut/clean (fcoll (polygon [ring]))) [:features 0 :geometry :coordinates 0])]
                  (and (= (sut/problem [ring]) (sut/problem [cleaned]))
                       (not-any? (fn [[a b]] (= a b)) (partition 2 1 cleaned))
                       (= (first ring) (first cleaned))
                       (= (last ring) (last cleaned))
                       (= cleaned (get-in (sut/clean (fcoll (polygon [cleaned]))) [:features 0 :geometry :coordinates 0]))))))

(def ^:private gen-star
  ;; A simple (star-shaped) polygon: 3-8 points around a centre in Finland,
  ;; at increasing angles, 1 m - 2 km from it.
  (gen/let [lon (gen/double* {:min 21.0 :max 30.0 :NaN? false :infinite? false})
            lat (gen/double* {:min 60.0 :max 69.0 :NaN? false :infinite? false})
            n (gen/choose 3 8)
            radii (gen/vector (gen/double* {:min 1.0 :max 2000.0 :NaN? false :infinite? false}) n)]
    (let [ring (mapv (fn [i r]
                       (let [a (* 2 Math/PI (/ i n))]
                         [(+ lon (/ (* r (Math/cos a)) (* 111320 (Math/cos (Math/toRadians lat)))))
                          (+ lat (/ (* r (Math/sin a)) 110574))]))
                     (range n) radii)]
      (conj ring (first ring)))))

(defspec area-agrees-with-jts 200
  (prop/for-all [ring gen-star]
                (let [jts-m2 (.getArea (gis/fcoll->tm35fin-geom (fcoll (polygon [ring]))))]
                  (< (Math/abs (- (sut/ring-area-m2 ring) jts-m2)) (* 0.01 jts-m2)))))
