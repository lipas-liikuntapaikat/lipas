(ns lipas.utils
  (:require
   [clojure.walk :as walk]
   [clojure.string :as string]
   [clojure.spec.alpha :as s]
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as csk-extras]
   #?(:cljs [cljs.reader :refer [read-string]])
   #?(:cljs [goog.string :as gstring])
   #?(:cljs [goog.string.format]))
  (:import [java.util.UUID]))

(defn index-by
  ([idx-fn coll]
   (index-by idx-fn identity coll))
  ([idx-fn value-fn coll]
   (into {} (map (juxt idx-fn value-fn)) coll)))

(def this-year #?(:cljs (.getFullYear (js/Date.))
                  :clj  (.getYear (java.time.LocalDate/now))))

(defn this-year? [x]
  (= (str x) (str this-year)))

(defn timestamp
  "Returns current timestamp in \"2018-07-11T09:38:06.370Z\" format.
  Always UTC."
  []
  #?(:cljs (.toISOString (js/Date.))
     :clj  (.toString (java.time.Instant/now))))

(defn ->ISO-timestamp
  "Converts timestamps from old LIPAS to ISO string format.
  Example: '2018-12-01 00:00:00.000' => '2018-12-01T00:00:00.000Z'
  "
  [s]
  (when (not-empty s)
    (-> (take 23 s) string/join (string/replace " " "T") (str "Z"))))

(defn ->old-lipas-timestamp
  "Converts timestamps from ISO string format to old LIPAS format.
  Example: '2018-12-01T00:00:00.000Z' => '2018-12-01 00:00:00.000'"
  [s]
  (when (not-empty s)
    (-> s (subs 0 23) (string/replace "T" " "))))

(defn zero-left-pad
  [s len]
  (let [format-fn #?(:clj format
                     :cljs gstring/format)]
    (format-fn (str "%0" len "d") s)))

(defn remove-nils [m]
  (into {} (remove (comp nil? second) m)))

(defn clean
  "Removes nil values and empty {} entries recursively from maps."
  [m]
  (walk/postwalk
   (fn [x]
     (cond
       (map? x) (not-empty (into {} (filter (comp some? val)) x))
       :else x))
   m))

(defn ->number [s]
  (try
    (let [x (read-string s)]
      (when (number? x) x))
    #?(:cljs (catch :default ex)
       :clj  (catch Exception ex))))

(defn ->int [s]
  (when-let [num (->number s)]
    (int num)))

(defn ->uuid [s]
  #?(:clj (java.util.UUID/fromString s)
     :cljs (uuid s)))

(defn ->uuid-safe [x]
  (cond
    (uuid? x)   x
    (string? x) (try (->uuid x) #?(:cljs (catch :default ex)
                                   :clj  (catch Exception e)))
    :else nil))

(defn ->bool [x]
  #?(:clj (java.lang.Boolean/parseBoolean (str x))
     :cljs (= "true" (-> x str string/lower-case))))

(defn gen-uuid []
  #?(:clj (java.util.UUID/randomUUID)
     :cljs (random-uuid)))

;;; Simple statistics ;;;

;; https://github.com/clojure-cookbook/clojure-cookbook

(defn mean [coll]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (/ sum count)
      0)))

(defn median [coll]
  (when-not (empty? coll)
    (let [sorted (sort coll)
          cnt (count sorted)
          halfway (quot cnt 2)]
      (if (odd? cnt)
        (nth sorted halfway) ; (1)
        (let [bottom (dec halfway)
              bottom-val (nth sorted bottom)
              top-val (nth sorted halfway)]
          (mean [bottom-val top-val]))))))

(defn mode [coll]
  (when-not (empty? coll)
    (let [freqs (frequencies coll)
          occurrences (group-by val freqs)
          modes (last (sort occurrences))
          modes (->> modes
                     val
                     (map key))]
      modes)))

(defn simple-stats [coll]
  {:count  (count coll)
   :sum    (reduce + coll)
   :mean   (mean coll)
   :median (median coll)
   :mode   (mode coll)})

(defn validate-noisy [spec data]
  (s/explain spec data)
  (s/valid? spec data))

(defn all-valid? [spec data]
  (every? true? (map (partial validate-noisy spec) data)))

;; From Stackoverflow https://bit.ly/2wslBqY
(defn deep-merge [a b]
  (merge-with
   (fn [x y]
     (cond (map? y)    (deep-merge x y)
           (vector? y) (concat x y)
           :else       y))
   a b))

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            repeat)
       (rest csv-data)))

(defn +safe [& args]
  (if-let [valid-args (not-empty (filter number? args))]
    (apply + valid-args)))

(defn get-in-path [m s]
  (let [ks (map keyword (string/split s #"\."))]
    (get-in m ks)))

(defn join [coll]
  (string/join "," coll))

(def content-type
  {:xlsx "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})

(defn ->kebab-case-keywords [m]
  (csk-extras/transform-keys csk/->kebab-case m))

(defn ->camel-case-keywords [m]
  (csk-extras/transform-keys csk/->camelCase m))

(defn ->snake-case-keywords [m]
  (csk-extras/transform-keys csk/->snake_case m))

(def trim (fnil string/trim ""))
(def sreplace (fnil string/replace ""))

 ;; (prn {:ts1     ts1 :ts2 ts2
 ;;       :update? (> (compare ts1 ts2) 0)})
(defn filter-newer [m1 ts-fn1 m2 ts-fn2]
  (select-keys m1 (filter (fn [k]
                            (let [ts1 (-> k m1 ts-fn1)
                                  ts2 (-> k m2 ts-fn2)]
                              (> (compare ts1 ts2) 0)))
                          (keys m1))))

(defn mapv-indexed [& args]
  (into [] (apply map-indexed args)))

(defn reverse-cmp [a b]
  (compare b a))
