(ns lipas.backend.legacy.api
  (:require
   [lipas.data.prop-types :as prop-types]
   [lipas.data.types :as types]
   [lipas.utils :as utils]))

(defn fill-properties [m]
  (reduce (fn [acc k] (assoc-in acc [:props k] (prop-types/all k)))
          m
          (-> m :props keys)))


(defn localize-props [m locale prop-keys]
  (reduce (fn [acc k]
            (-> acc
                (update-in [:props k :description] locale)
                (update-in [:props k :name] locale)))
          m
          prop-keys))

(defn- localize [m lang]
  (let [prop-keys (-> m :props keys)]
   (-> m
       (update :name lang)
       (update :description lang)
       (localize-props lang prop-keys))))

(defn- ->legacy-api [m lang]
  (-> m
      (fill-properties)
      (localize lang)
      (update :name lang)
      (update :description lang)
      utils/->camel-case-keywords))



(prop-types/all :track-length-m)
(defn sports-place-types [lang]
  (->> (vals types/all)
       (map #(->legacy-api % lang))))

(defn sports-place-by-type-code [lang type-code]
  (->legacy-api (types/all type-code) lang))
