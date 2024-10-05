(ns lipas.data.types
  "Type codes went through a major overhaul in the summer of 2024. This
  namespace hosts a controlled place to roll-out the changes."
  (:require
   [lipas.data.types-old :as old]
   #_[lipas.data.types-new :as new]
   [lipas.utils :as utils]))

(def main-categories
  old/main-categories)

(def sub-categories
  old/sub-categories)

(def all old/all)

(def active
  (reduce-kv (fn [m k v] (if (not= "active" (:status v)) (dissoc m k) m)) all all))

(def unknown
  old/unknown)

(def by-main-category (group-by :main-category (vals active)))
(def by-sub-category (group-by :sub-category (vals active)))

(def main-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals main-categories)))

(def sub-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals sub-categories)))
