(ns lipas.backend.legacy.api
  (:require
   [lipas.data.prop-types :as prop-types]
   [lipas.data.types :as types]
   [lipas.utils :as utils]))

(def keys-vec [:type-code :name :description :geometry-type :sub-category])

(defn fill-properties [m]
  (reduce (fn [acc k] (assoc-in acc [:props k] (prop-types/all k)))
          m
          (-> m :props keys)))


(defn localize-properties [m locale]
  (reduce (fn [acc k]
            (-> acc
                (update-in [:props k :description] locale)
                (update-in [:props k :name] locale))) m (-> m :props keys)))

(defn- localize [m lang]
  (-> m
      (update :name lang)
      (update :description lang)))


(defn- ->legacy-api-with-properties [m lang]
  (-> m
      (fill-properties)
      (select-keys (conj keys-vec :props))
      (update :props #(-> % (dissoc :schoolUse? :freeUse?)))
      (localize-properties lang)
      (localize lang)
      utils/->camel-case-keywords))

(defn- ->legacy-api [m lang]
  (-> m
      (select-keys keys-vec)
      (localize lang)
      utils/->camel-case-keywords))

(defn sports-place-types [lang]
  (->>  (vals types/all)
        (map #(->legacy-api % lang))))

(defn sports-place-by-type-code [lang type-code]
  (-> (types/all type-code)
      (->legacy-api-with-properties lang)))
