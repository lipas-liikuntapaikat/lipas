(ns lipas.backend.address.resolve-test
  "Building number -> postal code matching. The segments below are the real
  BAF segments of Mannerheimintie in Helsinki, in the shape the
  `postal_street_segment` rows carry them."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.address.resolve :as resolve]))

(defn- bound
  ([number] (bound number nil nil))
  ([number letter] (bound number letter nil))
  ([number letter number2]
   {:number number :letter letter :number2 number2 :letter2 nil}))

(def mannerheimintie
  [{:postal-code "00100" :side :odd :min-bound (bound 1) :max-bound (bound 13 "e")}
   {:postal-code "00100" :side :even :min-bound (bound 2 "a") :max-bound (bound 40)}
   {:postal-code "00250" :side :odd :min-bound (bound 17) :max-bound (bound 69)}
   {:postal-code "00260" :side :even :min-bound (bound 42) :max-bound (bound 56)}])

(def odd-segment (first mannerheimintie))

;;; segment-contains? ;;;

(deftest segment-contains-test
  (testing "respects odd/even side"
    (is (resolve/segment-contains? odd-segment 5))
    (is (not (resolve/segment-contains? odd-segment 6))))

  (testing "respects lettered bounds"
    (is (resolve/segment-contains? odd-segment 13))
    (is (resolve/segment-contains? odd-segment 13 "e"))
    (is (not (resolve/segment-contains? odd-segment 13 "f")))
    (is (not (resolve/segment-contains? odd-segment 15))))

  (testing "a lettered lower bound excludes earlier letters"
    (let [even-segment (second mannerheimintie)]     ; 2a - 40
      (is (resolve/segment-contains? even-segment 2 "a"))
      (is (resolve/segment-contains? even-segment 2))
      (is (not (resolve/segment-contains? even-segment 2 "A")))))

  (testing "uses dual numbers as upper bounds"
    (let [dual {:postal-code "x" :side :even :min-bound (bound 2) :max-bound (bound 20 nil 22)}]
      (is (resolve/segment-contains? dual 22))
      (is (not (resolve/segment-contains? dual 24)))))

  (testing "a boundless segment (BAF side code '0') contains nothing"
    (let [no-address {:postal-code "00002" :side nil :min-bound nil :max-bound nil}]
      (is (not (resolve/segment-contains? no-address 1)))
      (is (not (resolve/segment-contains? no-address 2 "a"))))))

;;; resolve-postal-codes ;;;

(deftest resolve-postal-codes-test
  (testing "a building number resolves to its own postal code"
    (is (= [{:postal-code "00260" :exact? true}]
           (resolve/resolve-postal-codes mannerheimintie 56))))

  (testing "all codes on the street, exact, when no number is given"
    (is (= [{:postal-code "00100" :exact? true}
            {:postal-code "00250" :exact? true}
            {:postal-code "00260" :exact? true}]
           (resolve/resolve-postal-codes mannerheimintie nil))))

  (testing "numbers in a data gap fall back to all codes, non-exact"
    (let [matches (resolve/resolve-postal-codes mannerheimintie 15)]
      (is (= 3 (count matches)))
      (is (every? (complement :exact?) matches))))

  (testing "a code matched by several segments is returned once"
    (let [segments [{:postal-code "00100" :side :odd :min-bound (bound 1) :max-bound (bound 13)}
                    {:postal-code "00100" :side nil :min-bound (bound 1) :max-bound (bound 99)}]]
      (is (= [{:postal-code "00100" :exact? true}]
             (resolve/resolve-postal-codes segments 3)))))

  (testing "letters narrow the match"
    (is (= [{:postal-code "00100" :exact? true}]
           (resolve/resolve-postal-codes mannerheimintie 13 "e")))
    (is (every? (complement :exact?)
                (resolve/resolve-postal-codes mannerheimintie 13 "f"))))

  (testing "a street with no segments resolves to nothing"
    (is (= [] (resolve/resolve-postal-codes [] 5)))))

;;; parse-building-number ;;;

(deftest parse-building-number-test
  (testing "number with and without a letter"
    (is (= {:number 16 :letter nil} (resolve/parse-building-number "16")))
    (is (= {:number 6 :letter "b"} (resolve/parse-building-number "6b")))
    (is (= {:number 6 :letter "b"} (resolve/parse-building-number "6 B")))
    (is (= {:number 13 :letter "e"} (resolve/parse-building-number " 13E"))))

  (testing "trailing noise is ignored, leading noise is not a number"
    (is (= {:number 6 :letter nil} (resolve/parse-building-number "6-8")))
    (is (nil? (resolve/parse-building-number "as oy 5")))
    (is (nil? (resolve/parse-building-number "")))
    (is (nil? (resolve/parse-building-number nil)))))

;;; Generative ;;;

(def ^:private segment-gen
  "Segments the way BAF has them: the bounds share the parity of the side."
  (gen/let [side (gen/elements [:odd :even nil])
            postal-code (gen/elements ["00100" "00250" "00260"])
            low (gen/choose 1 9998)
            span (gen/choose 0 200)]
    (let [low (if (or (nil? side) (= (= :odd side) (odd? low))) low (inc low))
          span (if side (* 2 (quot span 2)) span)]
      {:postal-code postal-code
       :side side
       :min-bound (bound low)
       :max-bound (bound (+ low span))})))

(defspec segment-contains-its-own-bounds 200
  (prop/for-all [segment segment-gen]
                (every? #(resolve/segment-contains? segment (get-in segment [% :number]))
                        [:min-bound :max-bound])))

(defspec matched-numbers-respect-side-and-bounds 300
  (prop/for-all [segment segment-gen
                 number (gen/choose 1 10200)]
                (let [{:keys [side min-bound max-bound]} segment]
                  (if (resolve/segment-contains? segment number)
                    (and (<= (:number min-bound) number (:number max-bound))
                         (or (nil? side) (= (= :odd side) (odd? number))))
                    (or (not (<= (:number min-bound) number (:number max-bound)))
                        (and side (not= (= :odd side) (odd? number))))))))

(defspec resolve-postal-codes-invariants 200
  (prop/for-all [segments (gen/vector segment-gen 1 6)
                 number (gen/choose 1 10200)]
                (let [matches (resolve/resolve-postal-codes segments number)
                      codes (map :postal-code matches)
                      street-codes (set (map :postal-code segments))]
                  (and ;; sorted, deduped and never invented
                    (= codes (sort (distinct codes)))
                    (every? street-codes codes)
                    (seq codes)
                    (if (every? :exact? matches)
                      ;; every exact code is backed by a segment that really
                      ;; contains the number
                      (every? (fn [code]
                                (some #(and (= code (:postal-code %))
                                            (resolve/segment-contains? % number))
                                      segments))
                              codes)
                      ;; the inexact fallback is the whole street
                      (= street-codes (set codes)))))))
