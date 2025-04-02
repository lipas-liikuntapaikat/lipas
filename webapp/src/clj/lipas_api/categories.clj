(ns lipas-api.categories
  (:require [lipas-api.util :refer [locale-key]]
            [clojure.string :as str]))

(defn parse-int-list
  [text sep-regexp]
  (map read-string (str/split text sep-regexp)))

(defn format-sub-category
  [sub-cat locale]
  (array-map
   :typeCode (:type-code sub-cat)
   :name ((locale-key :name locale) sub-cat)
   :sportsPlaceTypes (parse-int-list (:sports-place-type-codes sub-cat) #",")))

(defn find-sub-categories
  [main-cat sub-cats]
  (filter
   #(= (:main-category-type-code %) (:type-code main-cat)) sub-cats))

(defn format-category
  [main-cat sub-cats locale]
  (array-map
   :typeCode (:type-code main-cat)
   :name ((locale-key :name locale) main-cat)
   :subCategories (map #(format-sub-category % locale)
                       (find-sub-categories main-cat sub-cats))))
