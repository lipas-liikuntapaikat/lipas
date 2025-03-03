(ns lipas.schema.core-legacy
  (:require
   [clojure.spec.alpha :as s]
   [lipas.schema.common :as common-schema]
   [lipas.schema.core]
   [lipas.schema.sports-sites.location :as location-schema]
   [lipas.schema.sports-sites.types :as types-schema]
   [malli.core :as m]
   [malli.util :as mu]))


(def lang [:enum "fi" "en" "se"])

;; From legacy codebase
(def legacy-fields ["name" "marketingName" "lastModified" "constructionYear" "renovationYears" "freeUse" "schoolUse" "type.typeCode"
                    "type.name" "www" "email" "phoneNumber" "reservationsLink" "owner" "admin" "location.geometries"
                    "location.coordinates.wgs84" "location.coordinates.tm35fin" "location.address" "location.postalCode"
                    "location.postalOffice" "location.neighborhood" "location.city.name" "location.city.cityCode" "location.locationId"
                    "location.sportsPlaces" "properties"])

;; From legacy codebase
#_(s/defschema PropertyTypes
    {(apply s/enum prop-type-keys)
     {:name        (describe s/Str "Human friendly property name" :example "Length (m)")
      :description (describe (s/maybe s/Str) "Description of the property"
                             :example "A nice prop")
      :dataType    (s/enum "boolean" "string" "integer" "numeric")}})

(def legacy-sport-place-property
  [:map-of types-schema/prop-type-key
   [:map
    [:name :string]
    [:description {:optional true} :string]
    [:dataType [:enum "boolean" "string" "integer" "numeric"]]]])

;; From legacy codebase
#_(s/defschema SportsplaceType
    {:typeCode     (describe s/Int "Unique type code" :example 1170)
     :name         (describe s/Str "Type name" :example "Velodrome")
     :description  (describe (s/maybe s/Str) "Type description" :example "A nice type")
     :geometryType (s/enum "Point" "LineString" "Polygon")
     :subCategory  (describe s/Int "Sub-category type code" :example 1100)
     :properties   (describe PropertyTypes "Additional properties of the sports place"
                             :example {:trackLengthM {:name        "Length of track m"
                                                      :description (str "Track length in"
                                                                        " meters")
                                                      :dataType    "numeric"}
                                       :ligthing     {:name        "Lighting"
                                                      :description "Place has lights?"
                                                      :dataType    "boolean"}})})
(def legacy-sports-place
  [:map
   [:typeCode :int]
   [:name :string]
   [:description {:optional true} :string]
   [:geometryType [:enum  "Point" "LineString" "Polygon"]]
   [:subCategory :int]
   [:properties legacy-sport-place-property]])

#_(s/defschema qp [{pageSize :- (s/constrained Long pos?) (:default-page-size config)}
                   {page :- (s/constrained Long pos?) 1}
                   {fields :- [schemas/Field] []}
                   {typeCodes :- [(:type-codes enums)] []}
                   {cityCodes :- [(:city-codes enums)] []}
                   {closeToDistanceKm :- (s/constrained Double pos?) nil}
                   {closeToLon :- geo-schemas/wgs84-lon nil}
                   {closeToLat :- geo-schemas/wgs84-lat nil}
                   {closeToMatch :- (s/enum :start-point :any-point) :start-point}
                   {modifiedAfter :- schemas/ModifiedAfter nil}
                   {searchString :- s/Str nil}
                   {retkikartta :- schemas/ExcursionMap? false}
                   {harrastuspassi :- schemas/Harrastuspassi? false}])

(def search-params
  (let [schema [:map
                [:pageSize [pos-int?]]
                [:page [pos-int?]]
                [:fields [:or
                          [:vector (into [:enum] legacy-fields)]
                          (into [:enum] legacy-fields)]]
                [:typeCodes [:or 
                             #'types-schema/active-type-code
                             #'types-schema/active-type-codes]]
                [:closeToLon [:int {:min -180 :max 180}]]
                [:closeToLat [:int {:min -90 :max 90}]]
                [:lang #'lang]
                [:modifiedAfter [:re #"\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01]) (?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d\.\d{3}"]]
                [:retkikartta :boolean]
                [:closeToMatch [:enum "start-point" "any-point"]]
                [:closeToDistanceKm [common-schema/number]]
                [:harrastuspassi :boolean]
                [:cityCodes [:or
                             [:int]
                             #'location-schema/city-codes]]
                [:searchString [:string {:min 1 :max 2056}]]]]
    (mu/optional-keys schema (mu/keys schema))))

(comment
  (m/explain search-params {:fields ["name"]
                            :typeCodes 1530})
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
