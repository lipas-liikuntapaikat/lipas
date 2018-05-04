(ns lipas-ui.local-storage
  (:require [cljs.reader :as reader]
            [re-frame.core :as re-frame]))

(defn ls-set
  [k v]
  (when (and k v)
    (.setItem js/localStorage k (str v))))

(defn ls-remove
  [k]
  (.removeItem js/localStorage k))

(defn ls-get
  [k]
  (some->> (.getItem js/localStorage k)
           (reader/read-string)))

(re-frame/reg-cofx
 :get-local-storage-value
 (fn [cofx k]
   (assoc cofx :local-storage-value (ls-get k))))

(re-frame/reg-cofx
 :remove-local-storage-value
 (fn [cofx k]
   (ls-remove k)
   cofx))
