(ns lipas-api.properties
  (:require [clojure.string :as str]
            [lipas-api.util :refer [locale-key not-blank]]))

(def prop-translators {"boolean" #(Boolean/valueOf %)
                       "integer" #(when-not (str/blank? %) (Integer/valueOf %))
                       "numeric" #(when-not (str/blank? %) (Double/valueOf %))
                       "string" #(not-blank %)})

(def checkers {"boolean" boolean?
               "integer" integer?
               "numeric" number?
               "string"  string?})

(defn prop-format-reducer-es
  [res k v]
  (if-let [value (apply (prop-translators (:data-type v)) [(:value v)])]
    (assoc res k value)
    (dissoc res k)))

(defn format-props-es
  [props locale]
  (reduce-kv prop-format-reducer-es props props))

(defn prop-format-reducer-db
  "Wraps prop-format-reducer call to support
  'old style prop coll coming from the db."
  [res prop]
  (apply prop-format-reducer-es [res (keyword (:handle-en prop)) prop]))

(defn format-props-db
  [props locale]
  (reduce prop-format-reducer-db {} props))

(defn validate-props
  [{:keys [types-props props] :as input}]
  (let [tp (reduce (fn [res e] (assoc res (keyword (:handle-en e)) e))
                   {} types-props)]
    (reduce-kv (fn [res propk propv]
                 (let [dtype (-> tp propk :data-type)
                       checker (checkers dtype)
                       ok? (apply checker [propv])]
                   (if ok?
                     res
                     (update res :error str "Value for property " propk
                             " must be " dtype "!"))))
               input props)))
