(ns lipas.schema.sports-sites.location
  (:require [lipas.data.cities :as cities]
            [lipas.schema.common :as common]
            [malli.util :as mu]))

(def address
  [:string {:description "Street address of the sports facility."
            :min 1
            :max 200}])

(def postal-code
  [:re {:description "Postal code of the address of the sports facility."}
   common/postal-code-regex])

(def postal-office
  [:string {:description "Postal office of the address of the sports facility."
            :min 1
            :max 100}])

(def neighborhood
  [:string {:description "Neighborhood or common name for the area of the location."
            :min 1
            :max 100}])

(def city-code
  (into [:enum {:description "Official municipality identifier https://stat.fi/fi/luokitukset/kunta/kunta_1_20240101"}]
        (sort (keys cities/by-city-code))))

(def city-code-compat
  (into [:enum {:encode/json identity
                :description "Official municipality identifier https://stat.fi/fi/luokitukset/kunta/kunta_1_20240101"}]
        (sort (keys cities/by-city-code))))

(def city-codes
  [:set {:description (-> city-code second :description)}
   city-code])

(defn make-location-schema
  ([feature-schema geom-type]
   (make-location-schema feature-schema geom-type false))
  ([feature-schema geom-type compat]
   [:map {:description (str "Location of the sports facility with required "
                            geom-type
                            " feature.")}
    [:city
     [:map
      [:city-code (if compat #'city-code-compat #'city-code)]
      [:neighborhood {:optional true} #'neighborhood]]]
    [:address #'address]
    [:postal-code #'postal-code]
    [:postal-office {:optional true} #'postal-office]
    [:geometries
     [:map
      [:type [:enum "FeatureCollection"]]
      [:features
       [:vector {:min 1} feature-schema]]]]]))

(def line-string-feature-props
  [:map
   [:name {:optional true} :string]
   #_[:lipas-id {:optional true} #'lipas-id]
   [:type-code {:optional true} :int]
   [:route-part-difficulty {:optional true} :string]
   [:travel-direction {:optional true} :string]])

(def line-string-feature
  (mu/assoc common/line-string-feature :properties line-string-feature-props))

(def point-location
  (make-location-schema common/point-feature "Point"))
(def point-location-compat
  (make-location-schema common/point-feature "Point" :compat))
(def line-string-location
  (make-location-schema line-string-feature "LineString"))
(def line-string-location-compat (make-location-schema line-string-feature "LineString" :compat))
(def polygon-location
  (make-location-schema common/polygon-feature "Polygon"))
(def polygon-location-compat
  (make-location-schema common/polygon-feature "Polygon" :compat))
