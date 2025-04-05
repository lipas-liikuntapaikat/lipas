(ns lipas.schema.sports-sites.types
  (:require [lipas.data.prop-types :as prop-types]
            [lipas.data.types :as types]
            [lipas.schema.common :as common]
            [malli.util :as mu]))

(def tags [:sequential [:string {:min 2 :max 100}]])

(def active-type-code
  (into [:enum {:description "Sports facility type according to LIPAS classification https://www.jyu.fi/fi/file/lipas-tyyppikoodit-2024"}]
        (keys types/active)))

(def active-type-codes
  [:set {:description (-> active-type-code second :description)}
   #'active-type-code])

(def type-code-with-legacy
  (into (subvec active-type-code 0 2) (keys types/all)))

(def type-codes-with-legacy
  [:set {:description (-> active-type-code second :description)}
   #'type-code-with-legacy])

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
   [:type-code #'active-type-code]
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
       [:map-of
        [:string {:min 2 :max 200}]
        [:map
         [:label #'common/localized-string]
         [:description {:optional true} #'common/localized-string]]]]]]]])

(comment
  (require '[malli.core :as m])
  (m/schema type)
  )
