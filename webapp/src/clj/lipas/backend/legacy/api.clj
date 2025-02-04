(ns lipas.backend.legacy.api
  (:require [lipas.backend.core :as core]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.schema.core-legacy :refer [legacy-fields]]
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

(defn sports-place-types [locale]
  (->>  (vals types/all)
        (map #(->legacy-api % locale))))

(defn sports-place-by-type-code [locale type-code]
  (-> (types/all type-code)
      (->legacy-api-with-properties locale)))

(defn collect-sport-place-types [sub-category-type-code]
  (->> (vals types/all)
       (filter #(= (% :sub-category) sub-category-type-code))
       (map :type-code)))

(defn- collect-subcategories [type-code locale]
  (->>
   (vals types/sub-categories)
   (filter #(= (% :main-category) (str type-code)))
   (map (fn [x]
          {:typeCode (x :type-code)
           :name (-> x :name locale)
           :sportsPlaceTypes (collect-sport-place-types (x :type-code))}))))

(defn categories [locale]
  (mapv (fn [cat] {:name (-> cat :name locale)
                   :typeCode (cat :type-code)
                   :subCategories (collect-subcategories (cat :type-code) locale)})
        (vals types/main-categories)))


(def query-template {:query
                     {:simple_query_string
                      {:query "*"
                       :analyze_wildcard true,
                       :fields legacy-fields,
                       :default_operator "and"}}})

(defn ->es-query [params]
  ;; TODO: use params
  query-template)

(defn get-sports-places [search params]
  (core/search search (->es-query params)))
