(ns lipas.backend.address.resolve
  "Street + building number -> postal code, against the BAF segments of one
  street in one municipality (a `postal_street_segment` row set, i.e. what
  `lipas.backend.address.posti/parse-baf-line` produces).

  A building matches a segment when the side of the street (odd/even) agrees
  with the parity of the number and the number falls inside the segment's
  min..max bounds. Buildings that land between segments — gaps in Posti's data,
  and new houses — fall back to every postal code on the street, flagged
  `:exact? false`."
  (:require [clojure.string :as str]))

(defn- bound-compare
  "Compares (`number`, `letter`) against a segment `bound`, returning a
  negative number, zero or a positive number the way `compare` does.

  `upper?` picks the tail of a dual building number ('6a/8b') when there is
  one, since that is what the upper bound of the segment really is. Bounds
  without a letter are inclusive of every letter, and so is a query without
  one: 13 and 13e both sit inside a segment ending at 13e."
  [number letter bound upper?]
  (let [bound-number (if upper? (or (:number2 bound) (:number bound)) (:number bound))]
    (if (not= number bound-number)
      (- number bound-number)
      (let [bound-letter (if upper? (or (:letter2 bound) (:letter bound)) (:letter bound))]
        (if (or (nil? bound-letter) (nil? letter))
          0
          (compare letter bound-letter))))))

(defn- side-matches?
  [side number]
  (or (nil? side)
      (= (= :odd side) (odd? number))))

(defn segment-contains?
  "True when building `number` (+ optional `letter`, lowercase) is inside
  `segment`. Segments without bounds — BAF's side code '0' rows, which carry
  no street address at all — contain nothing."
  ([segment number] (segment-contains? segment number nil))
  ([{:keys [side min-bound max-bound]} number letter]
   (boolean
     (and min-bound
          max-bound
          (side-matches? side number)
          (>= (bound-compare number letter min-bound false) 0)
          (<= (bound-compare number letter max-bound true) 0)))))

(defn parse-building-number
  "Splits a building number as it appears in an address string ('16', '16 a',
  '6b') into `{:number 6 :letter \"b\"}`. Returns nil when there is no leading
  number. The letter is lowercased to match BAF, which stores lowercase
  letters."
  [s]
  (when-let [[_ number letter] (some->> s (re-find #"^\s*(\d{1,5})\s*([A-Za-z])?"))]
    {:number (parse-long number)
     :letter (some-> letter str/lower-case)}))

(defn resolve-postal-codes
  "Postal codes for building `number` (+ optional `letter`) on the street whose
  BAF `segments` are given, as

      [{:postal-code \"00260\" :exact? true} ...]

  sorted by postal code. Segments matching the number exactly win; when none
  match, every postal code on the street is returned flagged `:exact? false`,
  which is the honest answer for a number in a data gap. A nil `number` asks
  'which codes does this street have', so all of them are exact."
  ([segments number] (resolve-postal-codes segments number nil))
  ([segments number letter]
   (let [codes-of    (fn [segs] (sort (distinct (map :postal-code segs))))
         exact-codes (when number
                       (codes-of (filter #(segment-contains? % number letter) segments)))]
     (if (seq exact-codes)
       (mapv (fn [code] {:postal-code code :exact? true}) exact-codes)
       (mapv (fn [code] {:postal-code code :exact? (nil? number)}) (codes-of segments))))))
