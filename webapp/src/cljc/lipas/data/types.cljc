(ns lipas.data.types
  "Categorization of sports sites."
  (:require
   [lipas.utils :as utils]
   #_[lipas.data.types-old :as old]
   [lipas.data.prop-types :as prop-types]
   [lipas.data.types-new :as new]))

(def main-categories
  new/main-categories)

(def sub-categories
  new/sub-categories)

(def all
  new/all)

(def active
  (reduce-kv (fn [m k v] (if (not= "active" (:status v)) (dissoc m k) m)) all all))

(def unknown
  {:name          {:fi "Ei tietoa" :se "Unknown" :en "Unknown"}
   :type-code     -1
   :main-category -1
   :sub-category  -1})

(def by-main-category (group-by :main-category (vals active)))
(def by-sub-category (group-by :sub-category (vals active)))

(def main-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals main-categories)))

(def sub-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals sub-categories)))

(defn ->type
  [m]
  (-> m
      (update :main-category (fn [id] (-> id
                                          main-categories
                                          (dissoc :ptv))))
      (update :sub-category (fn [id] (-> id
                                         sub-categories
                                         (dissoc :ptv :main-category))))

      (update :props (fn [m]
                       (->> m
                            (reduce-kv (fn [res k v]
                                         (conj res (merge v {:key k} (prop-types/all k))))
                                       [])
                            (sort-by :priority utils/reverse-cmp))))))

(def used-prop-types
  (let [used (set (mapcat (comp keys :props second) all))]
    (select-keys prop-types/all used)))
