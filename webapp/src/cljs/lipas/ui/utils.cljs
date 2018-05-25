(ns lipas.ui.utils
  (:require [re-frame.core :as re-frame]))

(def <== (comp deref re-frame/subscribe))
(def ==> re-frame/dispatch)

(def maxc (partial apply max))

(defn next-id [db path]
  (-> db (get-in path) keys maxc inc))

(defn save-entity [db path entity]
  (let [id (or (:id entity) (next-id db path))]
    (assoc-in db (conj path id) (assoc entity :id id))))
