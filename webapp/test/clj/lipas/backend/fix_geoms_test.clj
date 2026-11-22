(ns lipas.backend.fix-geoms-test
  "`core/fix-geoms` prepares a site's polygons for the search index.

  Two ways it failed in production (2026-11): a polygon that was one point
  repeated (saved by accident from the editor) made it throw, which stopped
  the whole reindex; and repairing a polygon without an id gave it
  `\"id\": null`, which the API's response validation rejects."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.core :as core]
            [lipas.backend.gis :as gis]))

(def ^:private lipas-id 9990100)

(defn- site [features]
  {:lipas-id lipas-id
   :location {:geometries {:type "FeatureCollection" :features features}}})

(defn- polygon [rings & {:keys [id]}]
  (cond-> {:type "Feature" :geometry {:type "Polygon" :coordinates rings}}
    id (assoc :id id)))

(defn- features [site] (get-in site [:location :geometries :features]))

(defn- fix [features] (core/fix-geoms (site features)))

(defn- valid-polygon?
  "JTS can construct `f` and calls it a valid, non-empty Polygon."
  [f]
  (try
    (let [g (gis/->jts-geom {:type "FeatureCollection" :features [f]})]
      (and (= "Polygon" (.getGeometryType g)) (.isValid g) (not (.isEmpty g))))
    (catch Exception _ false)))

(def ^:private point-repeated
  "621743's first polygon (2026-11-17), which stopped the reindex."
  [[[27.71245077973908 60.57303669809983] [27.71245077973908 60.57303669809983]
    [27.71245077973908 60.57303669809983] [27.71245077973908 60.57303669809983]]])

(def ^:private two-points
  "620978's only polygon (2026-09-01)."
  [[[22.904854042170857 61.33789397958819] [22.904854042170857 61.33789397958819]
    [22.904893357323303 61.337895166083754] [22.904854042170857 61.33789397958819]]])

(def ^:private collinear [[[25.0 60.0] [25.01 60.0] [25.02 60.0] [25.0 60.0]]])

(def ^:private square [[[25.0 60.0] [25.01 60.0] [25.01 60.01] [25.0 60.01] [25.0 60.0]]])

(def ^:private spike
  "Self-intersecting; repairs to one polygon."
  [[[25.0 60.0] [25.002 60.0] [25.002 60.002] [25.001 60.002] [25.001 60.003]
    [25.001 60.002] [25.0 60.002] [25.0 60.0]]])

(def ^:private bowtie
  "Self-intersecting; repairs to two polygons."
  [[[25.0 62.0] [25.1 62.0] [25.05 62.05] [25.1 62.1] [25.0 62.1] [25.05 62.05] [25.0 62.0]]])

(def ^:private id-a "2b0e5f3c-6a51-4f0b-9d3e-0c3f6d1e7a10")
(def ^:private id-b "8c2e7f1a-4b3d-4e5f-9a6b-1c2d3e4f5a6b")

(deftest polygons-without-area-test
  (testing "a polygon without area goes, the site's real polygon stays"
    (is (= [id-b] (mapv :id (features (fix [(polygon point-repeated :id id-a)
                                            (polygon square :id id-b)]))))))

  (testing "a site with no polygon that has area can't be indexed"
    (doseq [rings [point-repeated two-points collinear]]
      (let [e (try (fix [(polygon rings :id id-a)]) nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (= {:lipas-id lipas-id} (ex-data e)) (pr-str rings)))))

  (testing "a hole without area goes, its polygon stays"
    (let [hole [[25.002 60.002] [25.003 60.003] [25.002 60.002]]
          [f] (features (fix [(polygon (conj square hole) :id id-a)]))]
      (is (= 1 (count (get-in f [:geometry :coordinates]))))
      (is (valid-polygon? f)))))

(deftest repaired-feature-ids-test
  (testing "a repaired polygon without an id stays without one"
    (let [[f :as fs] (features (fix [(polygon spike)]))]
      (is (= 1 (count fs)))
      (is (not (contains? f :id)))
      (is (valid-polygon? f))))

  (testing "a repaired polygon keeps its id"
    (is (= [id-a] (mapv :id (features (fix [(polygon spike :id id-a)]))))))

  (testing "a split keeps the id on its first part only"
    (is (= [[:id id-a] nil] (mapv #(find % :id) (features (fix [(polygon bowtie :id id-a)]))))))

  (testing "a split of a polygon without an id gives parts without one"
    (is (= [nil nil] (mapv #(find % :id) (features (fix [(polygon bowtie)])))))))

(def ^:private gen-ring
  ;; Points from a 4x4 grid, so rings repeat points, run along a line and
  ;; cross themselves: the shapes accidental clicks make. Closed, as saved.
  (gen/fmap (fn [ps] (conj ps (first ps)))
            (gen/vector (gen/fmap (fn [[x y]] [(+ 25.0 (* x 0.001)) (+ 60.0 (* y 0.001))])
                                  (gen/tuple (gen/choose 0 3) (gen/choose 0 3)))
                        1 6)))

(def ^:private gen-feature
  (gen/let [rings (gen/vector gen-ring 1 2)
            id (gen/one-of [(gen/return nil) (gen/fmap str gen/uuid)])]
    (polygon rings :id id)))

(defspec fix-geoms-gives-valid-polygons-or-refuses 300
  (prop/for-all [fs (gen/vector gen-feature 1 3)]
                (let [result (try (features (core/fix-geoms (site fs)))
                                  (catch clojure.lang.ExceptionInfo e
                                    (when (= {:lipas-id lipas-id} (ex-data e)) ::refused)))
                      valid-in (filter valid-polygon? fs)
                      out-ids (keep :id (when (sequential? result) result))]
                  (if (= ::refused result)
                    (empty? valid-in)
                    (and (seq result)
                         (every? valid-polygon? result)
                         (not-any? #(and (contains? % :id) (nil? (:id %))) result)
                         (every? (set (keep :id fs)) out-ids)
                         (= (count out-ids) (count (distinct out-ids)))
             ;; a polygon that was valid all along is never lost
                         (every? (set out-ids) (keep :id valid-in))
                         (<= (count valid-in) (count result)))))))
