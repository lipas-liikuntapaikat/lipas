(ns lipas.schema.diversity
  (:require [lipas.schema.common :as common]
            [lipas.schema.sports-sites.types :as types-schema]
            [malli.core :as m]))

(def category-name (m/schema [:string {:min 1 :max 128}]))

(def category
  (m/schema
   [:map
    [:name category-name]
    [:factor common/number]
    [:type-codes [:sequential {:min 1} types-schema/type-code-with-legacy]]]))

(def category-preset
  (m/schema
   [:map
    [:name category-name]
    [:categories [:sequential {:min 1} category]]]))

(def category-presets
  (m/schema [:sequential category-preset]))

(def diversity-indices-req
  (m/schema
   [:map
    [:categories [:sequential {:min 1} category]]
    [:analysis-area-fcoll [:map
                           [:type [:enum "FeatureCollection"]]
                           [:features [:sequential [:map
                                                    [:type [:enum "Feature"]]
                                                    [:geometry [:map]]]]]]]
    [:analysis-radius-km {:optional true} common/number]
    [:max-distance-m {:optional true} common/number]
    [:distance-mode {:optional true} [:enum "euclid" "route"]]]))
