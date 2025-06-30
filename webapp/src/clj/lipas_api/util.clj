(ns lipas-api.util
  (:require
   [clojure.string :refer [blank?]]))

(defn parse-year
  [y]
  (when (and (number? y) (< 0 y))
    y))

(defn not-blank
  [map-val]
  (cond (string? map-val) (if (blank? map-val) nil map-val)
        (coll? map-val) (not-empty map-val)
        :else map-val))

(defn only-non-nil-recur
  "Traverses through map recursively and removes all nil values."
  [a-map]
  (reduce-kv #(if (map? %3)
                (let [fixed (only-non-nil-recur %3)]
                  (if ((complement empty?) fixed)
                    (assoc %1 %2 fixed)
                    %1))
                (if (some? (not-blank %3))
                  (assoc %1 %2 %3)
                  %1))
             {} a-map))

(defn locale-key
  [a-key locale]
  (if (= :all locale)
    (fn [sp] {:fi ((locale-key a-key :fi) sp)
              :se ((locale-key a-key :se) sp)
              :en ((locale-key a-key :en) sp)})
    (keyword (str (name a-key) "-" (name locale)))))

(defn select-paths
  "Similar to select-keys, just the 'key' here is a path in the nested map"
  [m & paths]
  (reduce
   (fn [result path]
     (assoc-in result path (get-in m path)))
   {}
   paths))

(defn parse-path
  "Parses keyword path from dot (.) delimited input and returns a vector
   representing the path. It doesn't like nil.

  :kissa.koira.kana => [:kissa :koira :kana]
  :kissa => [:kissa]

  Works with strings or anything that works with `keyword`.

  \"kissa.koira.kana\" => [:kissa :koira :kana]
  'kissa.koira.kana => [:kissa :koira :kana]
  "
  [k]
  {:pre [(keyword k)]}
  (into [] (map keyword (clojure.string/split (name k) #"\."))))
