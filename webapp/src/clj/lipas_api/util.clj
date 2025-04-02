(ns lipas-api.util
  (:require [camel-snake-kebab.extras :refer [transform-keys]]
            [camel-snake-kebab.core :refer [->camelCaseKeyword
                                            ->snake_case
                                            ->kebab-case]]
            [clojure.core.memoize :refer [fifo]]
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

(defn only-non-nil
  [a-map]
  (into {} (filter (comp some? val) a-map)))

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

(defn parse-int-default
  [input default-val]
  (try (Integer/valueOf input)
       (catch Exception e default-val)))

(def memo-camel
  (fifo ->camelCaseKeyword :fifo/threshold 512))

(def memo-snake
  (fifo ->snake_case :fifo/threshold 512))

(def memo-kebab
  (fifo ->kebab-case :fifo/threshold 512))

(defn convert-keys->db
  "Converts keywords in a map to snake_case

  {:kissa-koira 2} => {:kissa_koira 2}"
  [a-map]
  (transform-keys memo-snake a-map))

(defn convert-keys<-db
  [a-map]
  (transform-keys memo-kebab a-map))

(defn locale-key
  [a-key locale]
  (if (= :all locale)
    (fn [sp] {:fi ((locale-key a-key :fi) sp)
              :se ((locale-key a-key :se) sp)
              :en ((locale-key a-key :en) sp)})
    (keyword (str (name a-key) "-" (name locale)))))

;; localizations are now under their own keys
(defn localize
  [sp]
  {:fi (:name sp)
   :se (:name-se sp)
   :en (:name-en sp)})

(defn read-string-safe
  [s]
  (when-not (empty? s)
    (read-string s)))

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
