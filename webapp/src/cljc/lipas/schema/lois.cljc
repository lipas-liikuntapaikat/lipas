(ns lipas.schema.lois
  (:require #?(:clj [cheshire.core :as json])
            #?(:clj [clojure.data.csv :as csv])
            [lipas.data.loi :as loi]
            [lipas.schema.common :as common]
            [malli.json-schema :as json-schema]))

(def loi-id common/uuid)
(def loi-category (into [:enum] (keys loi/categories)))
(def loi-categories [:set loi-category])

(def loi-type (into [:enum] (for [[_ category] loi/categories
                                  [_ type] (:types category)]
                              (:value type))))

(def loi-types [:set loi-type])

(def loi
  (into [:multi {:title "LocationOfInterest"
                 :dispatch :loi-type}]
        (for [[cat-k cat-v] loi/categories
              [_type-k type-v] (:types cat-v)]
          [(:value type-v)
           (into
            [:map {:description (str cat-k " > " (:value type-v))
                   :title (-> type-v :label :en)}
             [:id loi-id]
             [:event-date {:description "Timestamp when this information became valid (ISO 8601, UTC time zone)"}
              #'common/iso8601-timestamp]
             #_[:created-at [:string]]
             [:geometries (case (:geom-type type-v)
                            ("Polygon") #'common/polygon-feature-collection
                            ("LineString") #'common/line-string-feature-collection
                            #'common/point-feature-collection)]
             [:status common/status]
             [:loi-category {:description "The category of the type of the Location of Interest"}
              [:enum cat-k]]
             [:loi-type {:description "The type of the Location of Interest"}
              [:enum (:value type-v)]]]
            (for [[prop-k prop-v] (:props type-v)]
              [prop-k {:optional true} (:schema prop-v)]))])))

(comment
  (require '[malli.core :as m])
  (m/schema loi)
  )

(defn gen-json-schema
  []
  (-> loi
      json-schema/transform
      #?(:clj(json/encode {:pretty true})
         :cljs clj->js)
      println))

(declare gen-csv)

#?(:clj
   (defn gen-csv
     []
     (->>
      (for [[category-code category] loi/categories
            [_ type] (:types category)
            [prop-k prop] (:props type)]
        [category-code
         (get-in category [:label :fi])
         (get-in category [:label :se])
         (get-in category [:label :en])
         (get-in category [:description :fi])
         (get-in category [:description :se])
         (get-in category [:description :en])
         (:value type)
         (get-in type [:label :fi])
         (get-in type [:label :se])
         (get-in type [:label :en])
         (get-in type [:description :fi])
         (get-in type [:description :se])
         (get-in type [:description :en])
         (name prop-k)
         (get-in prop [:field :label :fi])
         (get-in prop [:field :label :se])
         (get-in prop [:field :label :en])
         (get-in prop [:field :description :fi])
         (get-in prop [:field :description :se])
         (get-in prop [:field :description :en])])
      (into [["kategoria"
              "kategoria nimi fi"
              "kategoria nimi se"
              "kategoria nimi en"
              "kategoria kuvaus fi"
              "kategoria kuvaus se"
              "kategoria kuvaus en"
              "tyyppi"
              "tyyppi nimi fi"
              "tyyppi nimi se"
              "tyyppi nimi en"
              "tyyppi kuvaus fi"
              "tyyppi kuvaus se"
              "tyyppi kuvaus en"
              "ominaisuus"
              "ominaisuus nimi fi"
              "ominaisuus nimi se"
              "ominaisuus nimi en"
              "ominaisuus kuvaus fi"
              "ominaisuus kuvaus se"
              "ominaisuus kuvaus en"]])
      (csv/write-csv *out*))))

(comment
  (gen-json-schema)

  (json-schema/transform [:tuple :double :double])
  ;; => {:type "array",
  ;;     :items [{:type "number"} {:type "number"}],
  ;;     :additionalItems false}


  (gen-csv)
  )

(defn -main [& args]
  (if (= "csv" (first args))
    (gen-csv)
    (gen-json-schema)))
