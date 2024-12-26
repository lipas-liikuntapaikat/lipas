(ns lipas.schema.sports-sites.location
  (:require [lipas.schema.core :as specs]))

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
