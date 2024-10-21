(ns lipas.schema.core-legacy
  (:require [clojure.spec.alpha :as s]
            [lipas.schema.core]))

(s/def :lipas-legacy.api.parameter/lang :lipas.api/lang)

(s/def :lipas-legacy.api.response/sports-place-types
  (s/keys :req-un [:lipas-legacy.sports-site/typeCode
                :lipas.sports-site/name
                :lipas.sports-site/description
                :lipas-legacy.sports-site/geometryType
                :lipas-legacy.sports-site/subCategory]))

(s/def :lipas-legacy.sports-site/geometryType #{"Point"  "LineString"})
(s/def :lipas-legacy.sports-site/typeCode (s/int-in 1 5000))
(s/def :lipas-legacy.sports-site/subCategory (s/int-in 1 5000))
