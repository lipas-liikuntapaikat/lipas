(ns lipas.schema.sports-sites.location
  (:require [lipas.schema.core :as specs]
            [lipas.data.cities :as cities]
            [lipas.schema.common :as common]
            [malli.util :as mu]))

(def address
  [:string {:description "Street address of the sports facility."
            :min 1
            :max 200}])

(def postal-code
  [:re {:description "Postal code of the address of the sports facility."}
   specs/postal-code-regex])

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

(def city-codes
  [:set {:description (-> city-code second :description)}
   city-code])

(defn make-location-schema [feature-schema geom-type]
  [:map {:description (str "Location of the sports facility with required "
                           geom-type
                           " feature.")}
   [:city
    [:map
     [:city-code #'city-code]
     [:neighborhood {:optional true} #'neighborhood]]]
   [:address #'address]
   [:postal-code #'postal-code]
   [:postal-office {:optional true} #'postal-office]
   [:geometries
    [:map
     [:type [:enum "FeatureCollection"]]
     [:features
      [:vector feature-schema]]]]])

(def line-string-feature-props
  [:map
   [:name {:optional true} :string]
   #_[:lipas-id {:optional true} #'lipas-id]
   [:type-code {:optional true} :int]
   [:route-part-difficulty {:optional true} :string]
   [:travel-direction {:optional true} :string]])

(def line-string-feature
  (mu/assoc common/line-string-feature :properties line-string-feature-props))

(def point-location (make-location-schema common/point-feature "Point"))
(def line-string-location (make-location-schema line-string-feature "LineString"))
(def polygon-location (make-location-schema common/polygon-feature "Polygon"))
