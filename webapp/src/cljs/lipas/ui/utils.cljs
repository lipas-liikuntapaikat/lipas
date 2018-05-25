(ns lipas.ui.utils
  (:require [re-frame.core :as re-frame]))

(defn ->path&value
  ":k1 :k2 ... :kn js-event

  Event is always the last argument."
  [& args]
  (let [path (into [] (butlast args))
        event (last args)
        value (-> event
                  .-target
                  .-value)]
    (prn "Setting " path value)
    [path value]))

(def <== (comp deref re-frame/subscribe))
(def ==> re-frame/dispatch)

(def maxc (partial apply max))

(defn next-id [db path]
  (-> db (get-in path) keys maxc inc))

(defn save-entity [db path entity]
  (let [id (or (:id entity) (next-id db path))]
    (assoc-in db (conj path id) (assoc entity :id id))))
