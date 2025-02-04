(ns lipas.schema.core-legacy
  (:require [clojure.spec.alpha :as s]
            [lipas.schema.common :as common-schema]
            [lipas.schema.core]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [malli.core :as m]
            [malli.util :as mu]))


(def lang [:enum "fi" "en" "se"])

(def legacy-fields ["properties"
                    "schoolUse"
                    "email"
                    "type.name"
                    "reservationsLink"
                    "location.sportsPlaces"
                    "renovationYears"
                    "admin"
                    "location.coordinates.tm35fin"
                    "www"
                    "location.geometries"
                    "name"
                    "type.typeCode"
                    "location.locationId"
                    "constructionYear"
                    "freeUse"
                    "location.city.name"
                    "lastModified"
                    "marketingName"
                    "location.postalCode"
                    "location.postalOffice"
                    "location.city.cityCode"
                    "phoneNumber"
                    "location.neighborhood"
                    "owner"
                    "location.coordinates.wgs84"
                    "location.address"])

(def search-params
  (let [schema [:map
                [:typeCodes #'types-schema/active-type-codes]
                [:closeToLon [:int {:min -180 :max 180}]]
                [:closeToLat [:int {:min -90 :max 90}]]
                [:lang #'lang]
                [:modifiedAfter [:re #"\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01]) (?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d\.\d{3}"]]
                [:fields [:set (into [:enum] legacy-fields)]]
                [:retkikartta :boolean]
                [:closeToMatch [:enum "start-point" "any-point"]]
                [:page [:int {:min 0}]]
                [:closeToDistanceKm [common-schema/number]]
                [:harrastuspassi :boolean]
                [:pageSize [:int {:min 1 :max 100}]]
                [:cityCodes #'location-schema/city-codes]
                [:searchString [:string {:min 1 :max 2056}]]]]
    (mu/optional-keys schema (mu/keys schema))))

(comment
  (m/validate [:re #"\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01]) (?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d\.\d{3}"]
              "2025-02-04 14:48:00.000")
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
  )
