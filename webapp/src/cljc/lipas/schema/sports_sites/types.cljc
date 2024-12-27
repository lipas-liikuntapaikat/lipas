(ns lipas.schema.sports-sites.types
  (:require [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.schema.common :as common]))

(def tags [:sequential [:string {:min 2 :max 100}]])

(def type-code
  (into [:enum {:description "Sports facility type according to LIPAS classification https://www.jyu.fi/fi/file/lipas-tyyppikoodit-2024"}]
        (keys types/active)))

(def type-codes
  [:set {:description (-> type-code second :description)}
   #'type-code])

(def main-category (into [:enum] (keys types/main-categories)))
(def sub-category (into [:enum] (keys types/sub-categories)))
(def prop-type-key (into [:enum] (keys prop-types/all)))

(def type
  [:map {:description "Metadata definition for a specific sports facility type in LIPAS"}
   [:name #'common/localized-string]
   [:description #'common/localized-string]
   [:tags {:optional true}
    [:map
     [:fi {:optional true} #'tags]
     [:se {:optional true} #'tags]
     [:en {:optional true} #'tags]]]
   [:type-code #'type-code]
   [:main-category
    [:map
     [:type-code #'main-category]
     [:name #'common/localized-string]]]
   [:sub-category
    [:map
     [:type-code #'sub-category]
     [:name #'common/localized-string]]]
   [:geometry-type [:enum "Point" "LineString" "Polygon"]]
   [:props
    [:vector
     [:map
      [:priority [:int]]
      [:key #'prop-type-key]
      [:name #'common/localized-string]
      [:description #'common/localized-string]
      [:data-type [:enum "numeric" "boolean" "enum" "enum-coll" "string"]]
      [:opts {:optional true}
       [:map-of [:string {:min 2 :max 200}] #'common/localized-string]]]]]])

(comment
  (require '[malli.core :as m])
  (m/schema type)
  )
