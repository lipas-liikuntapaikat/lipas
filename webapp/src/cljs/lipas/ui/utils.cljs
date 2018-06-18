(ns lipas.ui.utils
  (:require [re-frame.core :as re-frame]
            [clojure.reader :refer [read-string]]))

(def <== (comp deref re-frame/subscribe))
(def ==> re-frame/dispatch)

(def maxc (partial apply max))

(defn next-id [db path]
  (-> db (get-in path) keys maxc inc))

(defn save-entity [db path entity]
  (let [id (or (:id entity) (next-id db path))]
    (assoc-in db (conj path id) (assoc entity :id id))))

(defn ->indexed-map [coll]
  (into {} (map-indexed (fn [idx item]
                          [idx
                           (if (map? item)
                             (assoc item :id idx)
                             item)])
                        coll)))

(defn index-by [idx-fn coll]
  (into {} (map (juxt idx-fn identity)) coll))

(defn localize-field [tr k prefix m]
  (if (k m)
    (update m k #(tr (keyword prefix %)))
    m))

(defn ->localized-select-entry [tr prefix k]
  (->> {:value k :label k}
       (localize-field tr :label prefix)))

(defn ->select-entries [tr prefix enum-map]
  (map (partial ->localized-select-entry tr prefix) (keys enum-map)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO consider using proper time-manipulation lib ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment (resolve-year "2014-12-02"))
(comment (resolve-year 2014))
(defn resolve-year [timestamp]
  (read-string (reduce str (take 4 (str timestamp)))))

(def this-year (.getFullYear (js/Date.)))

(defn begin-of-year
  ([]
   (begin-of-year this-year))
  ([y]
   (str y "-01-01")))

(defn end-of-year
  ([]
   (end-of-year this-year))
  ([y]
   (str y "-12-31")))

(defn ->timestamp [year]
  (str year))

(comment (prev-or-first [1 3 5] 2))
(comment (prev-or-first ["2005" "2012" "2001"] "2006"))
(defn prev-or-first [coll x]
  (let [ordered   (-> (conj coll x) sort reverse)
        head-at-x (drop-while #(not= x %) ordered)]
    (or (fnext head-at-x) (last (butlast ordered)))))

(comment (resolve-prev-rev [{:timestamp "2018-01-01"}
                            {:timestamp "2012-01-01"}
                            {:timestamp "2000-01-01"}]
                           "2013"))
(defn resolve-prev-rev [history rev-ts]
  (let [by-ts   (index-by :timestamp history)
        closest (prev-or-first (keys by-ts) rev-ts)]
    (get by-ts closest)))

(defn make-revision [site timestamp]
  (let [prev-rev (resolve-prev-rev (:history site) timestamp)]
    (-> prev-rev
        (assoc :timestamp timestamp)
        (dissoc :energy-consumption))))
