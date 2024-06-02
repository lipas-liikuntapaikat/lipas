(ns lipas.data.ptv
  (:require
   [lipas.data.types :as types]
   [lipas.utils :as utils]))

;; Utajärven jäähalli
;; https://api.palvelutietovaranto.suomi.fi/api/v11/ServiceChannel/8604a900-be6b-4f9d-8024-a272e07afba3?showHeader=false
;;

;; PTV:n käyttämä ontologiakoostehelvetti
;; https://finto.fi/koko/fi/page/p11070
;; https://finto.fi/koko/fi/search?clang=fi&q=uimahalli

;; json-patch https://github.com/borgeby/clj-json-pointer

(def locale->language
  {:fi "fi" :se "sv" :en "en"})

(def placeholder "TODO: Value missing!")

(defn ->ptv-lang-item
  [m]
  (->> (select-keys m [:fi :se :en])
       (map (fn [[locale s]] {:value s :language (locale->language locale)}))))

(defn ->ptv-service
  [org type-code]
  (let [type     (get types/all type-code)
        sub-cat  (get types/sub-categories (:sub-category type))
        main-cat (get types/main-categories (:main-category type))]
    {:keywords (->> type :tags (map ->ptv-lang-item))

     ;; List of ontology term urls (see http://finto.fi/koko/fi/)
     :ontologyTerms []

     ;; https://stat.fi/fi/luokitukset/toimiala/
     ;;:industrialClasses []

     ;; List of valid identifiers can be retrieved from the endpoint
     ;; /api/GeneralDescription
     ;; :generalDescriptionId "..."

     ;;:validFrom "date-time when published"
     ;;:validTo "date-time when archived"

     ;; Undocumented??
     ;; :subType nil

     :type "Service" ; Service | PermitOrObligation | ProfessionalQualification

     ;; General description overrides this
     :serviceChargeType "Chargeable" ; Chargeable | FreeOfCharge

     :fundingType "PubliclyFunded" ;; PubliclyFunded | MarketFunded

     :serviceNames (for [[locale lang] locale->language]
                     {:type      "Name" ; Name | AlternativeName
                      :languaage lang
                      :value     (get-in sub-cat [:name locale])})

     ;; List of target group urls (see
     ;; http://finto.fi/ptvl/fi/page/?uri=http://urn.fi/URN:NBN:fi:au:ptvl:KR)
     ;; General description overrides this
     :targetGroups []

     ;; Nationwide | NationwideExceptAlandIslands | LimitedType
     :areaType "LimitedType"

     ;; TODO is this the actual language in which the service is
     ;; provided? Maybe default to just "fi"?
     :languages (vals locale->language)

     :serviceDescriptions (for [[locale lang] locale->language]
                            {:type      "Summary" ; Description |
                                        ; Summary |
                                        ; UserInstruction |
                                        ; ValidityTime |
                                        ; ProcessingTime |
                                        ; DeadLine |
                                        ; ChargeTypeAdditionalInfo
                                        ; | ServiceType
                             :languaage lang
                             :value     (get-in sub-cat [:description locale] placeholder)})

     ;; TODO can this be inferred from owner / admin info reliably or do we
     :serviceProducers [{;; SelfProducedServices | ProcuredServices | Other
                         :provisionType "SelfProducedServices"}]

     :publishingStatus "Published" ; Draft | Published

     ;; Attach with ServiceChannelId
     ;;:serviceChannels []

     :mainResponsibleOrganization (:id org)
     }))

(defn ->ptv-service-location-channel
  [org {:keys [ptv lipas-id location] :as sports-site}]
  (let [type     (get types/all (get-in sports-site [:type :type-code]))
        sub-cat  (get types/sub-categories (:sub-category type))
        main-cat (get types/main-categories (:main-category type))]
    {:organizationId      (:id org)
     :sourceId            (str "lipas-" lipas-id)
     :serviceChannelNames (keep identity
                                [(when-let [s (:name sports-site)]
                                   {:type     "Name"
                                    :value    s
                                    :language "fi"})

                                 (when-let [s (get-in sports-site [:name-localized :se])]
                                   {:type     "Name"
                                    :value    s
                                    :language "sv"})

                                 (when-let [s (get-in sports-site [:name-localized :en])]
                                   {:type     "Name"
                                    :value    s
                                    :language "en"})

                                 (when-let [s (:marketing-name sports-site)]
                                   {:type     "AlternativeName"
                                    :value    s
                                    :language "fi"})])

     :displayNameType [{:type "Name" :language "fi"}
                       {:type "Name" :language "sv"}
                       {:type "Name" :language "en"}]

     :serviceChannelDescriptions (keep identity
                                       (for [[type-k type-v] {:summary "Summary" :description "Description"}
                                             [locale lang]   locale->language]
                                         (when-let [v (get-in ptv [type-k locale])]
                                           {:type     type-v
                                            :value    v
                                            :language lang})))

     ;; TODO should this be controlled in org or sports-site level?
     :languages {:supported-languages org}

     :addresses [{:type    "Location" ; Location | Postal
                  :subType "Street" ; | Single | Street | PostOfficeBox | Abroad | Other.
                  :country "FI"
                  :streetAddress
                  (let [[lon lat] (-> location :geometries :features first
                                      :geometry :coordinates)]
                    {:municipality (-> location
                                       :city
                                       :city-code
                                       (utils/zero-left-pad 3))
                     :street       [{:value (:address location) :language "fi"}]
                     :latitude     lat
                     :longitude    lon})}]

     :publishigStatus "Published" ; Draft | Published

     ;; Link services by serviceId
     :services []
     }))

(comment
  (->ptv-service {:id "lol org id"} 1530)

  (def uta-jh
    {:properties {:area-m2 1539, :surface-material []},
      :email "palaute@utajarvi.fi",
      :envelope
      {:insulated-ceiling? true,
       :insulated-exterior? false,
       :low-emissivity-coating? false},
      :phone-number "+358858755700",
      :building
      {:total-volume-m3 17700,
       :seating-capacity 250,
       :total-ice-area-m2 1539,
       :total-surface-area-m2 2457,
       :total-ice-surface-area-m2 1539},
      :ventilation
      {:dryer-type "munters",
       :heat-pump-type "none",
       :dryer-duty-type "automatic",
       :heat-recovery-type "thermal-wheel",
       :heat-recovery-efficiency 75},
      :admin "city-technical-services",
      :www "https://www.utajarvi.fi",
      :name "Utajärven jäähalli",
      :construction-year 1997,
      :type {:type-code 2520, :size-category "small"},
      :lipas-id 89913,
      :renovation-years [2014],
      :conditions
      {:open-months 6,
       :stand-temperature-c 7,
       :ice-average-thickness-mm 40,
       :air-humidity-min 60,
       :air-humidity-max 90,
       :maintenance-water-temperature-c 45,
       :ice-surface-temperature-c -4,
       :weekly-maintenances 12,
       :skating-area-temperature-c 7,
       :daily-open-hours 11,
       :average-water-consumption-l 700},
      :status "active",
      :event-date "2019-04-05T13:54:19.910Z",
      :refrigeration
      {:original? true,
       :refrigerant "R404A",
       :refrigerant-solution "freezium"},
      :location
      {:city {:city-code 889},
       :address "Laitilantie 5",
       :geometries
       {:type "FeatureCollection",
        :features
        [{:type "Feature",
          :geometry
          {:type "Point",
           :coordinates [26.4131256689191 64.7631112249574]}}]},
       :postal-code "91600",
       :postal-office "Utajärvi"},
      :owner "city",
     :hall-id "91600UT1"})

  (-> uta-jh
      :location
      :city
      :city-code
      (utils/zero-left-pad 3))

  (->ptv-service-location-channel {:id "lol org id" :supported-languages ["fi"]} uta-jh)
  )
