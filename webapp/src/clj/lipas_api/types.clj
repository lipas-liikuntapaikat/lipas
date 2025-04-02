(ns lipas-api.types
  (:require [lipas-api.util :refer [locale-key]]
            [lipas-api.property-types :refer [format-prop-type]]))

(defn find-types-props
  [type props]
  (filter #(= (:type-code type) (:type-code %))) props)

(defn join-props
  [types props]
  (map #(assoc % :props (find-types-props % props)) types))

(defn format-type-list-item
  [type locale]
  (array-map
   :typeCode (:type-code type)
   :name ((locale-key :name locale) type)
   :description ((locale-key :description locale) type)
   :geometryType (:geometry-type type)
   :subCategory (:subcategory-id type)))

(defn format-type
  [type props locale]
  (merge (format-type-list-item type locale)
         (hash-map
          :properties (reduce merge (map #(format-prop-type % locale) props)))))


(defn validate-types-props
  "Validates that provided :props belong to provided :types-props.
  Assocs {:error \"message\"} to input if invalid props exist, otherwise input as is."
  [{:keys [type-code types-props props] :as input}]
  (let [allowed     (map (comp keyword :handle-en) types-props)
        not-allowed (filter #((complement some) #{%} allowed) (keys props))]
    (if (not-empty not-allowed)
      (assoc input :error (str "Following properties are not allowed for type "
                               type-code
                               ": " (pr-str not-allowed) ". Allowed properties are: "
                               (pr-str allowed)))
      input)))
