(ns lipas.utils
  (:require [clojure.walk :as walk]
            #?(:cljs [cljs.reader :refer [read-string]])
            #?(:cljs [goog.string :as gstring])
            #?(:cljs [goog.string.format])))

(defn index-by
  ([idx-fn coll]
   (index-by idx-fn identity coll))
  ([idx-fn value-fn coll]
   (into {} (map (juxt idx-fn value-fn)) coll)))

(def this-year #?(:cljs (.getFullYear (js/Date.))
                  :clj  (.getYear (java.time.LocalDate/now))))

(defn timestamp
  "Returns current timestamp in \"2018-07-11T09:38:06.370Z\" format.
  Always UTC."
  []
  #?(:cljs (.toISOString (js/Date.))
     :clj  (.toString (java.time.Instant/now))))

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
    (catch Exception ex)))

(defn ->int [s]
  (when-let [num (->number s)]
    (int num)))
