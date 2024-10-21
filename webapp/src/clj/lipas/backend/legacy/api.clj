(ns lipas.backend.legacy.api
  (:require [lipas.data.types-old :as types-old]
            [lipas.utils :as utils]))

(defn- ->legacy-api [m lang]
  (-> m
      utils/->camel-case-keywords
      (select-keys [:typeCode :name :description :geometryType :subCategory])
      (update :name lang)
      (update :description lang)))

(defn sports-place-types [lang]
  (->> (vals types-old/all)
       (map #(->legacy-api % lang))))

(defn sports-place-by-type-code [lang type-code]
  (->legacy-api (types-old/all type-code) lang))
