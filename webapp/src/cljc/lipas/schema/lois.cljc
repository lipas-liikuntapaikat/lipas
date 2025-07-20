(ns lipas.schema.lois
  (:require #?(:clj [cheshire.core :as json])
            #?(:clj [cheshire.generate])
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
  (into [:multi {:description "Location of Interest; a non-facility entity in LIPAS, that complements the sports facility data."
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

#?(:clj
   (cheshire.generate/add-encoder java.util.regex.Pattern
                                  (fn [re jsonGenerator]
                                    (.writeString jsonGenerator (str re)))))

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
     (csv/write-csv *out* loi/csv-data)))

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
