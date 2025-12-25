(ns lipas.schema.api.v1
  (:require [clojure.spec.alpha :as s]
            [lipas.schema.common :as common]
            [lipas.schema.core]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [malli.core :as m]
            [malli.util :as mu]))

(def date-re #"\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01]) (?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d\.\d{3}")

(def lang [:enum "fi" "en" "se"])

;; From legacy codebase
(def legacy-fields ["name" "marketingName" "lastModified" "constructionYear" "renovationYears" "freeUse" "schoolUse" "type.typeCode"
                    "type.name" "www" "email" "phoneNumber" "reservationsLink" "owner" "admin" "location.geometries"
                    "location.coordinates.wgs84" "location.coordinates.tm35fin" "location.address" "location.postalCode"
                    "location.postalOffice" "location.neighborhood" "location.city.name" "location.city.cityCode" "location.locationId"
                    "location.sportsPlaces" "properties"])

;; Properties are simple key-value pairs in legacy API responses
;; Values can be numbers, booleans, strings, or nil
(def legacy-sport-place-property
  [:map-of :keyword [:or :int :double :boolean :string :nil]])

;; Legacy feature schemas that allow any properties (pointId, routeId, areaId, etc.)
;; These are separate from common schemas to avoid polluting them.
(def legacy-feature-properties
  [:map-of :keyword :any])

(def legacy-point-feature
  (mu/assoc common/point-feature :properties [:maybe legacy-feature-properties]))

(def legacy-line-string-feature
  (mu/assoc common/line-string-feature :properties [:maybe legacy-feature-properties]))

(def legacy-polygon-feature
  (mu/assoc common/polygon-feature :properties [:maybe legacy-feature-properties]))

(def legacy-point-feature-collection
  [:map {:description "GeoJSON FeatureCollection with Point geometries and legacy properties."}
   [:type [:enum "FeatureCollection"]]
   [:features [:sequential legacy-point-feature]]])

(def legacy-line-string-feature-collection
  [:map {:description "GeoJSON FeatureCollection with LineString geometries and legacy properties."}
   [:type [:enum "FeatureCollection"]]
   [:features [:sequential legacy-line-string-feature]]])

(def legacy-polygon-feature-collection
  [:map {:description "GeoJSON FeatureCollection with Polygon geometries and legacy properties."}
   [:type [:enum "FeatureCollection"]]
   [:features [:sequential legacy-polygon-feature]]])

(def location
  [:map
   [:locationId {:optional true} :int]
   [:city [:map
           [:name :string]
           [:cityCode :int]]]
   [:address {:optional true
              :description "Street address"
              :example "Lemminkäisenkatu 13"} :string]
   [:postalCode {:optional true
                 :description "Postal code"
                 :example "20520"} :string]
   [:postalOffice {:optional true
                   :description "Postal office"
                   :example "Turku"} :string]
   [:neighborhood {:optional true
                   :description "Neighborhood name"
                   :example "Metsämäki"} :string]
   [:geometries [:or
                 legacy-polygon-feature-collection
                 legacy-line-string-feature-collection
                 legacy-point-feature-collection]]
   [:coordinates
    {:optional true
     :description "Simple Point coordinates of the location."
     :example {:wgs84 {:lon 25.718659
                       :lat 62.6023221}
               :tm35fin {:lon 434218
                         :lat 6941935}}}
    [:map
     [:wgs84 [:map
              [:lon :float]
              [:lat :float]]]
     [:tm35fin [:map
                [:lon :float]
                [:lat :float]]]]]

   [:sportsPlaces {:optional true
                   :description "SportsPlaces in this Location"
                   :example [508207]}
    [:vector :any]]])

(def subCategory
  [:map
   [:typeCode :int]
   [:name :string]
   [:sportsPlaceTypes :any]]) #_[:vector :int]

(def category
  [:map
   [:name :string]
   [:typeCode :int]
   [:subCategories [:vector subCategory]]])

(def category-response
  [:vector category])

(def sports-place-types-response
  [:vector
   [:map
    [:typeCode :int]
    [:name :string]
    [:description :string]
    [:geometryType :string]
    [:subCategory :int]]])

;; Property type definition for type detail endpoint
;; Each property has name, dataType, and description
;; Legacy API only supports: string, numeric, boolean (enum/enum-coll coerced to string)
(def legacy-property-type-definition
  [:map
   [:name :string]
   [:dataType [:enum "numeric" "boolean" "string"]]
   [:description :string]])

(def sports-places-by-type-response
  [:map
   [:typeCode :int]
   [:name :string]
   [:description :string]
   [:geometryType [:enum "Point" "LineString" "Polygon"]]
   [:subCategory :int]
   ;; Legacy API uses 'properties' key with property definitions
   [:properties [:map-of :keyword legacy-property-type-definition]]])

;; Sports place schema for single item endpoint (/:id) - all fields present
(def legacy-sports-place
  [:map
   [:sportsPlaceId :int]
   [:name {:example "Kupittaa Velodrome"} :string]
   [:marketingName {:optional true
                    :description "Sports place marketing name"
                    :example "Microsoft turbo velodrome"} :string]
   [:type [:map
           [:typeCode :int]
           [:name :string]]]
   [:schoolUse {:optional true
                :description "Schools use this sports place"} :boolean]
   [:freeUse {:optional true :description "Sports place is accessible without fees or reservation"} :boolean]
   [:constructionYear {:optional true
                       :example 1987} :int]
   [:renovationYears {:optional true
                      :description "List of renovation years"
                      :example [1990 2015]} [:vector int?]]
   [:lastModified {:optional true
                   :description "Last modification timestamp in Europe/Helsinki TZ"
                   :example "2012-04-23 18:25:43.511"} :string]
   [:location {:optional true
               :description "Location of the sports place"} #'location]
   [:properties {:optional true} #'legacy-sport-place-property]
   [:phoneNumber {:optional true
                  :example "014 569 4257"} [:maybe :string]]
   [:www {:optional true
          :example "www.example.fi/velodrome"} [:maybe :string]]
   [:reservationsLink {:optional true
                       :example "www.example.fi/reservations/velodrome"} [:maybe :string]]
   [:email {:optional true
            :example "mail@example.fi"} [:maybe :string]]
   [:owner {:optional true} :string]
   [:admin {:optional true} :string]])

;; Flexible location schema for list endpoint - all fields optional since field selection
;; allows requesting only specific nested fields like location.city.cityCode
(def location-list-item
  [:map
   [:locationId {:optional true} :int]
   [:city {:optional true}
    [:map
     [:name {:optional true} :string]
     [:cityCode {:optional true} :int]]]
   [:address {:optional true
              :description "Street address"
              :example "Lemminkäisenkatu 13"} :string]
   [:postalCode {:optional true
                 :description "Postal code"
                 :example "20520"} :string]
   [:postalOffice {:optional true
                   :description "Postal office"
                   :example "Turku"} :string]
   [:neighborhood {:optional true
                   :description "Neighborhood name"
                   :example "Metsämäki"} :string]
   [:geometries {:optional true}
    [:or
     legacy-polygon-feature-collection
     legacy-line-string-feature-collection
     legacy-point-feature-collection]]
   [:coordinates
    {:optional true
     :description "Simple Point coordinates of the location."
     :example {:wgs84 {:lon 25.718659
                       :lat 62.6023221}
               :tm35fin {:lon 434218
                         :lat 6941935}}}
    [:map
     [:wgs84 {:optional true} [:map
                               [:lon :float]
                               [:lat :float]]]
     [:tm35fin {:optional true} [:map
                                 [:lon :float]
                                 [:lat :float]]]]]
   [:sportsPlaces {:optional true
                   :description "SportsPlaces in this Location"
                   :example [508207]}
    [:vector :any]]])

;; Sports place schema for list endpoint - field selection makes most fields optional
;; Only sportsPlaceId is required, all other fields depend on the 'fields' parameter
(def legacy-sports-place-list-item
  [:map
   [:sportsPlaceId :int]
   [:name {:optional true :example "Kupittaa Velodrome"} :string]
   [:marketingName {:optional true
                    :description "Sports place marketing name"
                    :example "Microsoft turbo velodrome"} :string]
   [:type {:optional true}
    [:map
     [:typeCode {:optional true} :int]
     [:name {:optional true} :string]]]
   [:schoolUse {:optional true
                :description "Schools use this sports place"} :boolean]
   [:freeUse {:optional true :description "Sports place is accessible without fees or reservation"} :boolean]
   [:constructionYear {:optional true
                       :example 1987} :int]
   [:renovationYears {:optional true
                      :description "List of renovation years"
                      :example [1990 2015]} [:vector int?]]
   [:lastModified {:optional true
                   :description "Last modification timestamp in Europe/Helsinki TZ"
                   :example "2012-04-23 18:25:43.511"} :string]
   [:location {:optional true
               :description "Location of the sports place"} #'location-list-item]
   [:properties {:optional true} #'legacy-sport-place-property]
   [:phoneNumber {:optional true
                  :example "014 569 4257"} [:maybe :string]]
   [:www {:optional true
          :example "www.example.fi/velodrome"} [:maybe :string]]
   [:reservationsLink {:optional true
                       :example "www.example.fi/reservations/velodrome"} [:maybe :string]]
   [:email {:optional true
            :example "mail@example.fi"} [:maybe :string]]
   [:owner {:optional true} :string]
   [:admin {:optional true} :string]])

;; TODO remove any type
(def search-params
  (let [schema [:map
                [:pageSize pos-int?]
                [:page pos-int?]
                [:fields [:or
                          [:vector (into [:enum] legacy-fields)]
                          (into [:enum] legacy-fields)
                          [:string {:min 1}]]]
                [:typeCodes [:or
                             #'types-schema/active-type-code
                             #'types-schema/active-type-codes]]
                [:closeToLon [:float {:min -180 :max 180}]]
                [:closeToLat [:float {:min -90 :max 90}]]
                [:lang #'lang]
                [:modifiedAfter [:re date-re]]
                [:retkikartta :boolean]
                [:closeToMatch [:enum "start-point" "any-point"]]
                [:closeToDistanceKm common/number]
                [:harrastuspassi :boolean]
                [:cityCodes [:or
                             :int
                             #'location-schema/city-codes]]
                [:searchString [:string {:min 1 :max 2056}]]]]
    (mu/optional-keys schema (mu/keys schema))))

(def deleted-sports-place
  [:map
   [:sportsPlaceId {:description "The ID of the deleted sports place"
                    :example 123456} :int]
   [:deletedAt {:description "Timestamp when the sports place was deleted in Europe/Helsinki TZ"
                :example "2012-04-23 18:25:43.511"} :string]])

(def deleted-sports-places-response
  [:vector deleted-sports-place])
