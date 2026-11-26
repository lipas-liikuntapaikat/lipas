(ns lipas.data.ptv-table-sort-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.data.ptv-table-sort :as sut]))

(def ^:private opts
  {:columns {:status #(sut/sync-rank (:sync-status %) (:off? %))
             :audit #(sut/audit-rank (:audit-status %))
             :count :count
             :name :name}
   :name-fn :name
   :compare-text compare})

(defn- sorted-names [sort-state rows]
  (mapv :name (sort (sut/row-comparator sort-state opts) rows)))

(deftest status-columns-test
  (testing "Integraatio: the most critical first, a site with sync off last"
    (is (= ["blocked" "conflict" "ready" "ok" "not synced" "unwritten service" "off"]
           (sorted-names {:col :status :dir :asc}
                         [{:name "off" :sync-status :ok :off? true}
                          {:name "ok" :sync-status :ok}
                          {:name "unwritten service" :sync-status nil}
                          {:name "ready" :sync-status :ready}
                          {:name "not synced" :sync-status :not-synced}
                          {:name "conflict" :sync-status :awaiting-choice}
                          {:name "blocked" :sync-status :blocked}]))))

  (testing "Katselmointi: changes requested first, not audited last"
    (is (= ["changes" "remark" "approved" "none"]
           (sorted-names {:col :audit :dir :asc}
                         [{:name "none" :audit-status :none}
                          {:name "approved" :audit-status :approved}
                          {:name "remark" :audit-status :approved-with-feedback}
                          {:name "changes" :audit-status :changes-requested}])))))

(deftest direction-and-ties-test
  (let [rows [{:name "b" :count 2} {:name "a" :count 2} {:name "c" :count 0} {:name "d" :count 5}]]
    (testing "counts smallest first, ties by name"
      (is (= ["c" "a" "b" "d"] (sorted-names {:col :count :dir :asc} rows))))
    (testing "a second click reverses the column, the tie still A→Z"
      (is (= ["d" "a" "b" "c"] (sorted-names {:col :count :dir :desc} rows))))))

(deftest missing-values-test
  (testing "an unnamed row goes last whichever the direction"
    (let [rows [{:name nil} {:name "b"} {:name "a"}]]
      (is (= ["a" "b" nil] (sorted-names {:col :name :dir :asc} rows)))
      (is (= ["b" "a" nil] (sorted-names {:col :name :dir :desc} rows))))))

(deftest next-sort-test
  (is (= {:col :name :dir :asc} (sut/next-sort {:col nil :dir :asc} :name)))
  (is (= {:col :name :dir :desc} (sut/next-sort {:col :name :dir :asc} :name)))
  (is (= {:col :name :dir :asc} (sut/next-sort {:col :name :dir :desc} :name)))
  (is (= {:col :audit :dir :asc} (sut/next-sort {:col :name :dir :desc} :audit))))

(def ^:private gen-row
  (gen/hash-map :count (gen/one-of [(gen/return nil) (gen/choose 0 3)])
                :name (gen/one-of [(gen/return nil) (gen/elements ["a" "b" "c" "d"])])))

(defn- ordered?
  "Whether `rows` are in the order of `dir` by :count, nils last, and by
   :name A→Z (nils last) where the counts tie."
  [rows dir]
  (every? (fn [[x y]]
            (let [cx (:count x) cy (:count y)]
              (cond
                (and (some? cx) (nil? cy)) true
                (and (nil? cx) (some? cy)) false
                (not= cx cy) ((if (= :asc dir) < >) cx cy)
                :else (let [nx (:name x) ny (:name y)]
                        (or (nil? ny) (and (some? nx) (<= (compare nx ny) 0)))))))
          (partition 2 1 rows)))

(defspec sorted-in-either-direction 200
  (prop/for-all [rows (gen/vector gen-row 0 12)
                 dir (gen/elements [:asc :desc])]
                (ordered? (sort (sut/row-comparator {:col :count :dir dir} opts) rows) dir)))
