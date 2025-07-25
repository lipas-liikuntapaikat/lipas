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

(def excel-headers
  [[:main-category "Pääluokka"]
   [:sub-category "Alaluokka"]
   [:type-code "Tyyppikoodi"]
   [:geometry-type "Geometria"]
   [:main-category-name-fi "Pääluokka nimi fi"]
   [:main-category-name-se "Pääluokka nimi se"]
   [:main-category-name-en "Pääluokka nimi en"]
   [:sub-category-name-fi "Alaluokka nimi fi"]
   [:sub-category-name-se "Alaluokka nimi se"]
   [:sub-category-name-en "Alaluokka nimi en"]
   [:type-name-fi "Tyyppi nimi fi"]
   [:type-name-se "Tyyppi nimi se"]
   [:type-name-en "Tyyppi nimi en"]
   [:description-fi "Tyyppi kuvaus fi"]
   [:description-se "Tyyppi kuvaus se"]
   [:description-en "Tyyppi kuvaus en"]])

(defn ->excel-row
  [m]
  (into {}
        [[:main-category (get-in m [:main-category :type-code])]
         [:sub-category (get-in m [:sub-category :type-code])]
         [:type-code (get m :type-code)]
         [:geometry-type (get m :geometry-type)]
         [:main-category-name-fi (get-in m [:main-category :name :fi])]
         [:main-category-name-se (get-in m [:main-category :name :se])]
         [:main-category-name-en (get-in m [:main-category :name :en])]
         [:sub-category-name-fi (get-in m [:sub-category :name :fi])]
         [:sub-category-name-se (get-in m [:sub-category :name :se])]
         [:sub-category-name-en (get-in m [:sub-category :name :en])]
         [:type-name-fi (get-in m [:name :fi])]
         [:type-name-se (get-in m [:name :se])]
         [:type-name-en (get-in m [:name :en])]
         [:description-fi (get-in m [:description :fi])]
         [:description-se (get-in m [:description :se])]
         [:description-en (get-in m [:description :en])]]))

(def excel-headers-with-props
  (into [] cat [excel-headers [[:property "Ominaisuus"]
                               [:property-data-type "Ominaisuus tietotyyppi"]
                               [:property-name-fi "Ominaisuus nimi fi"]
                               [:property-name-se "Ominaisuus nimi se"]
                               [:property-name-en "Ominaisuus nimi en"]
                               [:property-description-fi "Ominaisuus kuvaus fi"]
                               [:property-description-fi "Ominaisuus kuvaus se"]
                               [:property-description-fi "Ominaisuus kuvaus en"]]]))

(def csv-data
  (into [(mapv second excel-headers-with-props)]
        (sort-by (juxt #(nth % 0) ; main category
                       #(nth % 1) ; sub category
                       #(nth % 2) ; type-code
                       )
                 (for [[type-code {:keys [main-category sub-category] :as type}] active]
                   [main-category
                    sub-category
                    type-code
                    (:geometry-type type)
                    (get-in main-categories [main-category :name :fi])
                    (get-in main-categories [main-category :name :se])
                    (get-in main-categories [main-category :name :en])
                    (get-in sub-categories [sub-category :name :fi])
                    (get-in sub-categories [sub-category :name :se])
                    (get-in sub-categories [sub-category :name :en])
                    (get-in type [:name :fi])
                    (get-in type [:name :se])
                    (get-in type [:name :en])
                    (get-in type [:description :fi])
                    (get-in type [:description :se])
                    (get-in type [:description :en])]))))

(def csv-data-with-props
  (into [(mapv second excel-headers-with-props)]
        (sort-by (juxt #(nth % 0) ; main category
                       #(nth % 1) ; sub category
                       #(nth % 2) ; type-code
                       #(nth % 16) ; prop-name
                       )
         (for [[type-code {:keys [main-category sub-category] :as type}] active
               [prop-k _] (:props type)]
           [main-category
            sub-category
            type-code
            (:geometry-type type)
            (get-in main-categories [main-category :name :fi])
            (get-in main-categories [main-category :name :se])
            (get-in main-categories [main-category :name :en])
            (get-in sub-categories [sub-category :name :fi])
            (get-in sub-categories [sub-category :name :se])
            (get-in sub-categories [sub-category :name :en])
            (get-in type [:name :fi])
            (get-in type [:name :se])
            (get-in type [:name :en])
            (get-in type [:description :fi])
            (get-in type [:description :se])
            (get-in type [:description :en])
            (name prop-k)
            (get-in prop-types/all [prop-k :data-type])
            (get-in prop-types/all [prop-k :name :fi])
            (get-in prop-types/all [prop-k :name :se])
            (get-in prop-types/all [prop-k :name :en])
            (get-in prop-types/all [prop-k :description :fi])
            (get-in prop-types/all [prop-k :description :se])
            (get-in prop-types/all [prop-k :description :en])]))))

(def used-prop-types
  (let [used (set (mapcat (comp keys :props second) all))]
    (select-keys prop-types/all used)))

(comment
  (->type (get all 1180))

  )
