(ns lipas.schema.legacy
  (:require [clojure.spec.alpha :as s]
            [lipas.schema.common :as common-schema]
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


;; enum, enum-coll new
(def legacy-sport-place-property
  [:map-of :keyword
   [:map
    [:name :string]
    [:description {:optional true} :string]
    [:dataType [:enum "boolean" "string" "interge" "numeric" "enum" "enum-coll"]]
    [:opts [:map-of :keyword #'common/localized-string]]]])

(def location
  [:map
   [:locationId {:optional true}  :int]
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
                 #'common-schema/polygon-feature-collection
                 #'common-schema/line-string-feature-collection
                 #'common-schema/point-feature-collection]]
   [:coordinates
    {:optional true
     :description "Simple Point coordinates of the location."
     :example {:wgs84   {:lon 25.718659
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


(def sports-places-by-type-response
  [:map
   [:typeCode :int]
   [:name :string]
   [:description :string]
   [:geometryType [:enum "Point" "LineString" "Polygon"]]
   [:subCategory :int]
   [:props legacy-sport-place-property
    #_[:map-of
       :keyword
       [:map
        [:name :string]
        [:dataType [:enum "numeric" "boolean" "enum" "enum-coll" "string"]]
        [:description :string]
        [:opts [:map-of :string :string]]]]]])

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

;; TODO remove any type
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
                [:closeToLon [:float {:min -180 :max 180}]]
                [:closeToLat [:float {:min -90 :max 90}]]
                [:lang #'lang]
                [:modifiedAfter [:re date-re]]
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

  (def response "")

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

  ;; From legacy codebase
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

  #_(s/defschema PropertyTypes
      {(apply s/enum prop-type-keys)
       {:name        (describe s/Str "Human friendly property name" :example "Length (m)")
        :description (describe (s/maybe s/Str) "Description of the property"
                               :example "A nice prop")
        :dataType    (s/enum "boolean" "string" "integer" "numeric")}})

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

  #_(s/defschema SportsPlace
      {:sportsPlaceId                     SportsPlaceId
       (s/optional-key :name)             (describe s/Str "Sports place name"
                                                    :example "Kupittaa Velodrome")
       (s/optional-key :marketingName)    (describe s/Str "Sports place marketing name"
                                                    :example "Microsoft turbo velodrome")
       (s/optional-key :type)             Type
       (s/optional-key :schoolUse)        (describe s/Bool "Schools use this sports place")
       (s/optional-key :freeUse)          (describe s/Bool (str
                                                            "Sports place is accessible "
                                                            "without fees or reservation"))
       (s/optional-key :constructionYear) (describe s/Int "" :example 1987)
       (s/optional-key :renovationYears)  (describe [s/Int] "List of renovation years"
                                                    :example [1990 2015])
       (s/optional-key :lastModified)     (describe s/Str (str
                                                           "Last modification timestamp in "
                                                           "Europe/Helsinki TZ")
                                                    :example "2012-04-23 18:25:43.511")
       (s/optional-key :location)         (describe Location "Location of the sports place")
       (s/optional-key :properties)       Properties
       (s/optional-key :phoneNumber)      (describe (s/maybe s/Str) ""
                                                    :example "(02) 262 3510")
       (s/optional-key :www)              (describe (s/maybe s/Str) ""
                                                    :example "www.example.fi/velodrome")
       (s/optional-key :reservationsLink) (describe (s/maybe s/Str) ""
                                                    :example "www.example.fi/reservations/velodrome")
       (s/optional-key :email)            (describe (s/maybe s/Str) ""
                                                    :example "mail@example.fi")
       (s/optional-key :owner) OwnerName
       (s/optional-key :admin) AdminName})

  #_(s/defschema Location
      {(s/optional-key :locationId)   LocationId
       (s/optional-key :city)         City
       (s/optional-key :address)      (describe s/Str "Street address"
                                                :example "Lemminkäisenkatu 13")
       (s/optional-key :postalCode)   (describe s/Str "Postal code" :example "20520")
       (s/optional-key :postalOffice) (describe s/Str "Postal office" :example "Turku")
       (s/optional-key :neighborhood) (describe s/Str "Neighborhood name"
                                                :example "Metsämäki")
       (s/optional-key :geometries)   (describe LipasFeatureCollection
                                                (str
                                                 "GeoJSON Feature Collection containing "
                                                 "full geometries related to location.")
                                                :example LipasFeatureCollectionExample)
       (s/optional-key :coordinates)  (describe Coordinates (str "Simple Point coordinates "
                                                                 "of the location.")
                                                :example {:wgs84   {:lon 25.718659
                                                                    :lat 62.6023221}
                                                          :tm35fin {:lon 434218
                                                                    :lat 6941935}})
       (s/optional-key :sportsPlaces) (describe [s/Num]
                                                "SportsPlaces in this Location"
                                                :example [508207])}))
