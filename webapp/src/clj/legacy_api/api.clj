(ns legacy-api.api
  (:require [clojure.string :as str]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.utils :as utils]))

(def keys-vec [:type-code :name :description :geometry-type :sub-category])

;; The infoFi property is a universal property that maps from the 'comment' field
;; in the new data model. It's added to all type property definitions in the legacy API.
(def info-fi-property
  {:name "Lisätieto"
   :description "Liikuntapaikan lisätiedot, vapaa tekstikenttä"
   :dataType "string"})

(defn- strip-question-mark
  "Remove trailing ? from keyword name.
   Legacy API property keys don't have ? suffix (e.g., 'ligthing' not 'ligthing?')."
  [k]
  (let [s (name k)]
    (keyword (if (str/ends-with? s "?")
               (subs s 0 (dec (count s)))
               s))))

(defn- coerce-property-datatype
  "Coerce enum/enum-coll to string and remove opts.
   Legacy API only supports: string, numeric, boolean."
  [prop]
  (let [dtype (:dataType prop)]
    (if (#{"enum" "enum-coll"} dtype)
      (-> prop
          (assoc :dataType "string")
          (dissoc :opts))
      prop)))

(defn- transform-property-keys
  "Transform property keys to legacy format (remove ? suffix)
   and coerce enum types to string."
  [props-map]
  (reduce-kv (fn [m k v]
               (assoc m (strip-question-mark k) (coerce-property-datatype v)))
             {}
             props-map))

(defn fill-properties [m]
  (assoc m :props (select-keys prop-types/all (keys (:props m)))))

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
      ;; Remove schoolUse and freeUse (kebab-case before camelCase transform)
      (update :props #(-> % (dissoc :school-use? :free-use?)))
      (localize-properties lang)
      (localize lang)
      utils/->camel-case-keywords
      ;; Legacy API uses 'properties' key (not 'props') with keys without '?' suffix
      (clojure.set/rename-keys {:props :properties})
      (update :properties transform-property-keys)
      ;; Add infoFi property (maps from 'comment' field in new data model)
      (assoc-in [:properties :infoFi] info-fi-property)))

(defn- ->legacy-api [m lang]
  (-> m
      (select-keys keys-vec)
      (localize lang)
      utils/->camel-case-keywords))

(defn sports-place-types [locale]
  (->> (vals types/all)
       (map #(->legacy-api % locale))))

(defn sports-place-by-type-code [locale type-code]
  (-> (types/all type-code)
      (->legacy-api-with-properties locale)))

(defn collect-sport-place-types [sub-category-type-code]
  (->> (types/by-sub-category sub-category-type-code)
       (map :type-code)))

(defn- collect-subcategories
  "Get sub-categories for a main-category.
   Main-category type-code is an integer (e.g., 0, 1000), but sub-categories
   store :main-category as a string (e.g., \"0\", \"1000\")."
  [type-code locale]
  (let [main-cat-str (str type-code)]
    (->> (vals types/sub-categories)
         (filter #(= main-cat-str (:main-category %)))
         (map (fn [sub-cat]
                {:typeCode (:type-code sub-cat)
                 :name (get-in sub-cat [:name locale])
                 :sportsPlaceTypes (vec (collect-sport-place-types (:type-code sub-cat)))}))
         vec)))

(defn categories [locale]
  (mapv (fn [cat] {:name (-> cat :name locale)
                   :typeCode (cat :type-code)
                   :subCategories (collect-subcategories (cat :type-code) locale)})
        (vals types/main-categories)))
