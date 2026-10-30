(ns lipas.ui.ptv.diff-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.ui.ptv.diff :as sut]))

(defn- a-of
  "Reconstruct the `a` string from a diff result (concat :equal + :removed)."
  [ops]
  (apply str (for [[op v] ops :when (#{:equal :removed} op)] v)))

(defn- b-of
  "Reconstruct the `b` string from a diff result (concat :equal + :added)."
  [ops]
  (apply str (for [[op v] ops :when (#{:equal :added} op)] v)))

(deftest tokenize-test
  (is (= [] (sut/tokenize nil)))
  (is (= [] (sut/tokenize "")))
  (is (= [] (sut/tokenize "   ")))
  (is (= ["hello"] (sut/tokenize "hello")))
  (is (= ["hello" " " "world"] (sut/tokenize "hello world")))
  (is (= ["a" "  " "b" "\n" "c"] (sut/tokenize "a  b\nc")))
  (testing "punctuation marks are tokens of their own"
    (is (= ["jääkiekkoa" "."] (sut/tokenize "jääkiekkoa.")))
    (is (= ["Linnukka" "-" "halli" "," " " "\"" "uusi" "\"" "…"]
           (sut/tokenize "Linnukka-halli, \"uusi\"…")))))

(deftest diff-punctuation
  (testing "an added full stop marks only the stop"
    (is (= [[:equal "Pelataan jääkiekkoa"] [:added "."]]
           (sut/coalesce (sut/diff "Pelataan jääkiekkoa" "Pelataan jääkiekkoa.")))))
  (testing "a changed comma leaves the words around it equal"
    (is (= [[:equal "Kenttä"] [:removed ","] [:added ";"] [:equal " jossa"]]
           (sut/coalesce (sut/diff "Kenttä, jossa" "Kenttä; jossa"))))))

(def ^:private text-gen
  ;; words, spaces, line breaks and punctuation, in any order
  (gen/fmap str/join
            (gen/vector (gen/elements ["a" "ä" "kenttä" " " "  " "\n" "." "," "-" "–" "\"" "(" ")" "…" "/"])
                        0 30)))

(defspec diff-reassembles-both-texts 200
  (prop/for-all [a text-gen
                 b text-gen]
                (let [ops (sut/diff a b)
                      blank->empty #(if (str/blank? %) "" %)]
                  (and (= (blank->empty a) (a-of ops))
                       (= (blank->empty b) (b-of ops))
                       (= (apply str (sut/tokenize a)) (blank->empty a))))))

(deftest diff-identical-inputs
  (testing "all-equal output for identical strings"
    (is (= [[:equal "hello"]] (sut/diff "hello" "hello")))
    (is (= [[:equal "hello"] [:equal " "] [:equal "world"]]
           (sut/diff "hello world" "hello world"))))
  (testing "empty inputs"
    (is (= [] (sut/diff "" "")))
    (is (= [] (sut/diff nil nil)))
    (is (= [] (sut/diff "   " "")))))

(deftest diff-pure-add-and-remove
  (testing "adding to empty → all :added"
    (is (= [[:added "hello"] [:added " "] [:added "world"]]
           (sut/diff "" "hello world"))))
  (testing "removing everything → all :removed"
    (is (= [[:removed "hello"] [:removed " "] [:removed "world"]]
           (sut/diff "hello world" "")))))

(deftest diff-edits-in-the-middle
  (testing "single-word change in the middle preserves surrounding equal tokens"
    (let [ops (sut/diff "the quick fox" "the lazy fox")]
      (is (= [[:equal "the"] [:equal " "]
              [:removed "quick"] [:added "lazy"]
              [:equal " "] [:equal "fox"]]
             ops))))
  (testing "insertion at the end keeps the leading equal run"
    (is (= [[:equal "hello"] [:equal " "] [:added "world"]]
           (sut/diff "hello " "hello world"))))
  (testing "deletion at the end"
    (is (= [[:equal "hello"] [:equal " "] [:removed "world"]]
           (sut/diff "hello world" "hello ")))))

(deftest diff-completely-disjoint
  (testing "no shared tokens → only :added and :removed"
    (let [ops (sut/diff "abc" "xyz")]
      (is (every? (fn [[op _]] (#{:added :removed} op)) ops))
      (is (some #(= [:removed "abc"] %) ops))
      (is (some #(= [:added "xyz"] %) ops)))))

(deftest diff-roundtrip-property
  (testing "for any pair, :equal+:removed reconstructs a; :equal+:added reconstructs b"
    (doseq [[a b] [["the quick brown fox" "the slow brown fox"]
                   ["alpha beta gamma" "alpha gamma"]
                   ["one two three" "one two three four"]
                   ["short" "completely different sentence"]
                   ["" "non-empty"]
                   ["leading space" "  leading space"]
                   ["multi\nline\ntext" "multi\nline\nrewritten"]
                   ["palvelutilan kuvaus" "palvelutilan tarkka kuvaus"]]]
      (let [ops (sut/diff a b)]
        (is (= a (a-of ops)) (str "a-side roundtrip failed for " (pr-str a) " -> " (pr-str b)))
        (is (= b (b-of ops)) (str "b-side roundtrip failed for " (pr-str a) " -> " (pr-str b)))))))

(deftest coalesce-test
  (testing "merges runs of equal ops"
    (is (= [[:equal "abc"]]
           (sut/coalesce [[:equal "a"] [:equal "b"] [:equal "c"]]))))
  (testing "preserves boundaries between different ops"
    (is (= [[:equal "the "] [:removed "quick"] [:added "lazy"] [:equal " fox"]]
           (sut/coalesce (sut/diff "the quick fox" "the lazy fox")))))
  (testing "empty input"
    (is (= [] (sut/coalesce [])))))
