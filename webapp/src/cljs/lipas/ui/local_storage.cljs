(ns lipas.ui.local-storage
  (:require [cljs.reader :as reader]
            [re-frame.core :as re-frame]))

(defn ls-set! [k v]
  (.setItem js/localStorage (pr-str k) (pr-str v)))

(defn ls-get [k]
  (when-let [s (.getItem js/localStorage (pr-str k))]
    (reader/read-string s)))

(defn ls-remove! [k]
  (.removeItem js/localStorage k))

(re-frame/reg-cofx
 ::get
 (fn [cofx k]
   (assoc-in cofx [:local-storage k] (ls-get k))))

(re-frame/reg-fx
 ::remove!
 (fn  [k]
   (ls-remove! k)))

(re-frame/reg-fx
 ::set!
 (fn  [[k v]]
   (ls-set! k v)))
