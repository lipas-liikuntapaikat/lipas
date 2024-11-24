(ns lipas.data.types
  "Categorization of sports sites."
  (:require
   [lipas.utils :as utils]
   [lipas.data.types-old :as old]
   #_[lipas.data.types-new :as new]
   ))

(def main-categories
  old/main-categories)

(def sub-categories
  old/sub-categories)

(def all
  old/all)

(def active
  (reduce-kv (fn [m k v] (if (not= "active" (:status v)) (dissoc m k) m)) all all))

(def by-main-category (group-by :main-category (vals active)))
(def by-sub-category (group-by :sub-category (vals active)))

(def main-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals main-categories)))

(def sub-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals sub-categories)))
