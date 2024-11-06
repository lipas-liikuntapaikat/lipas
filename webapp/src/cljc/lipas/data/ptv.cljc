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

;; org 10
#_(def uta-org-id-test "52e0f6dc-ec1f-48d5-a0a2-7a4d8b657d53")

;; Testiorganisaatio 6 (Kunta)
(def uta-org-id-test "3d1759a2-e47a-4947-9a31-cab1c1e2512b")

;; org 9
(def liminka-org-id-test "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5")

;; org 8
#_(def uta-org-id-test "92374b0f-7d3c-4017-858e-666ee3ca2761")
#_(def uta-org-id-prod "7b83257d-06ad-4e3b-985d-16a5c9d3fced")

;; TODO: Tulossa 5 kuntaa, muut:
;; (Lumijoki. Pyhäjärvi, Ii, Liminka ja Oulu sekä tietenkin bonuksena Utajärvi).

(def lang->locale
  {"fi" :fi, "sv" :se, "en" :en})

(def lipas-lang->ptv-lang
  {"fi" "fi", "se" "sv", "en" "en"})

(def placeholder "TODO: Value missing!")

(def default-langs ["fi"])

(defn ->ptv-service
  [{:keys [org-id city-codes source-id sub-category-id languages _description _summary]
    :or   {languages default-langs} :as m}]
  (let [languages (set languages)
        #_#_type  (get types/all type-code)
        sub-cat   (get types/sub-categories sub-category-id)
        main-cat  (get types/main-categories (parse-long (:main-category sub-cat)))]

    {:sourceId source-id

     #_#_:keywords (let [tags (:tags type)]
                     (for [locale [:fi :se :en]
                           :let   [kws (get tags locale)]
                           kw     kws
                           :when  (some? kw)]
                       {:language (locale->language locale) :value kw}))

     ;; List of ontology term urls (see http://finto.fi/koko/fi/)
     :ontologyTerms (into []
                          (comp cat (distinct))
                          [(-> main-cat :ptv :ontology-urls)
                           (-> sub-cat :ptv :ontology-urls)])

     :serviceClasses (into []
                           (comp (remove nil?) cat (distinct))
                           [(-> sub-cat :ptv :service-classes)
                            (-> main-cat :ptv :service-classes)])

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
     #_#_:serviceChargeType "Chargeable" ; Chargeable | FreeOfCharge

     :fundingType "PubliclyFunded" ;; PubliclyFunded | MarketFunded

     :serviceNames (for [[lang locale] (select-keys lang->locale languages)]
                     {:type     "Name" ; Name | AlternativeName
                      :language lang
                      :value    (get-in sub-cat [:name locale])})

     ;; List of target group urls
     ;; https://koodistot.suomi.fi/codescheme;registryCode=ptv;schemeCode=ptvkohderyhmat
     ;; General description overrides this
     :targetGroups ["http://uri.suomi.fi/codelist/ptv/ptvkohderyhmat/code/KR1"] ;; Kansalaiset

     ;; Nationwide | NationwideExceptAlandIslands | LimitedType
     :areaType "LimitedType"


     :areas (for [city-code city-codes]
              ;; Type of the area. Possible values are: Municipality,
              ;; Region, BusinessSubRegion, HospitalDistrict or
              ;; WellbeingServiceCounties.
              {:type      "Municipality"
               ;; List of area codes related to type. For example if
               ;; type = Municipality, areaCodes-list need to include
               ;; municipality codes like 491 or 091.
               :areaCodes [city-code]})

     ;; TODO is this the actual language in which the service is
     ;; provided? Maybe default to just "fi"?
     :languages languages

     :serviceDescriptions (for [[k v]         {:summary "Summary" :description "Description"}
                                [lang locale] (select-keys lang->locale languages)]
                            {:type     v ; Description |
                                        ; Summary |
                                        ; UserInstruction |
                                        ; ValidityTime |
                                        ; ProcessingTime |
                                        ; DeadLine |
                                        ; ChargeTypeAdditionalInfo
                                        ; | ServiceType
                             :language lang
                             :value    (get-in m [k locale] placeholder)})

     ;; TODO can this be inferred from owner / admin info reliably or do we
     :serviceProducers [{;; SelfProducedServices | ProcuredServices | Other
                         :provisionType "SelfProducedServices"
                         :organizations [org-id]}]

     :publishingStatus "Published" ; Draft | Published

     ;; Attach with ServiceChannelId
     ;;:serviceChannels []

     :mainResponsibleOrganization org-id
     }))

(defn ->ptv-service-location
  [org
   coord-transform-fn
   {:keys [ptv lipas-id location search-meta] :as sports-site}]
  (let [languages (-> ptv
                      (get :languages default-langs)
                      (->> (map lipas-lang->ptv-lang))
                      set)
        type     (get types/all (get-in sports-site [:type :type-code]))
        sub-cat  (get types/sub-categories (:sub-category type))
        main-cat (get types/main-categories (:main-category type))]

    (println "PTV data")
    (prn ptv)
    (println "Langauges resolved" languages)
    (prn location)

    {:organizationId      (:org-id ptv)
     ;; FIXME: PTV doesn't allow a new ServiceLocation with a same sourceId as a Deleted one
     :sourceId            (str "lipas3-" (:org-id ptv) "-" lipas-id)
     :serviceChannelNames (keep identity
                                (let [fallback (get-in sports-site [:name])]
                                  [(when (contains? languages "fi")
                                     {:type     "Name"
                                     :value    fallback
                                     :language "fi"})

                                   (when (contains? languages "sv")
                                     {:type     "Name"
                                      :value    (get-in sports-site [:name-localized :se] fallback)
                                      :language "sv"})

                                   (when (contains? languages "en")
                                       {:type     "Name"
                                        :value    (get-in sports-site [:name-localized :en] fallback)
                                        :language "en"})

                                   (when (contains? languages "fi")
                                     (when-let [s (:marketing-name sports-site)]
                                       {:type     "AlternativeName"
                                        :value    s
                                        :language "fi"}))]))

     :displayNameType (keep identity
                            [(when (contains? languages "fi") {:type "Name" :language "fi"})
                             (when (contains? languages "sv") {:type "Name" :language "sv"})
                             (when (contains? languages "en") {:type "Name" :language "en"})])

     :serviceChannelDescriptions (keep identity
                                       (let [fallback "TODO text missing"]
                                         (for [[type-k type-v] {:summary     "Summary"
                                                                :description "Description"}
                                               [lang locale] (select-keys lang->locale languages)]
                                           {:type     type-v
                                            :value    (get-in ptv [type-k locale] fallback)
                                            :language lang})))

     ;; TODO should this be controlled in org or sports-site level?
     :languages languages

     :addresses [{:type    "Location" ; Location | Postal
                  :subType "Single" ; | Single | Street | PostOfficeBox | Abroad | Other.
                  :country "FI"
                  :streetAddress
                  (let [[lon lat] (-> search-meta :location :wgs84-point coord-transform-fn)]
                    {:municipality (-> location
                                       :city
                                       :city-code
                                       (utils/zero-left-pad 3))
                     :street       (for [lang languages]
                                     {:value (:address location) :language lang})
                     :postalCode   (-> location :postal-code)
                     :latitude     lat
                     :longitude    lon})}]

     :publishingStatus "Published" ; Draft | Published

     ;; Link services by serviceId
     :services (-> sports-site :ptv :service-ids)
     }))

(comment

  (def uta-jh
    {:properties        {:area-m2 1539, :surface-material []},
     :email             "palaute@utajarvi.fi",
     :envelope
     {:insulated-ceiling?      true,
      :insulated-exterior?     false,
      :low-emissivity-coating? false},
     :phone-number      "+358858755700",
     :building
     {:total-volume-m3           17700,
      :seating-capacity          250,
      :total-ice-area-m2         1539,
      :total-surface-area-m2     2457,
      :total-ice-surface-area-m2 1539},
     :ventilation
     {:dryer-type               "munters",
      :heat-pump-type           "none",
      :dryer-duty-type          "automatic",
      :heat-recovery-type       "thermal-wheel",
      :heat-recovery-efficiency 75},
     :admin             "city-technical-services",
     :www               "https://www.utajarvi.fi",
     :name              "Utajärven jäähalli",
     :construction-year 1997,
     :type              {:type-code 2520, :size-category "small"},
     :lipas-id          89913,
     :renovation-years  [2014],
     :conditions
     {:open-months                     6,
      :stand-temperature-c             7,
      :ice-average-thickness-mm        40,
      :air-humidity-min                60,
      :air-humidity-max                90,
      :maintenance-water-temperature-c 45,
      :ice-surface-temperature-c       -4,
      :weekly-maintenances             12,
      :skating-area-temperature-c      7,
      :daily-open-hours                11,
      :average-water-consumption-l     700},
     :status            "active",
     :event-date        "2019-04-05T13:54:19.910Z",
     :refrigeration
     {:original?            true,
      :refrigerant          "R404A",
      :refrigerant-solution "freezium"},
     :location
     {:city          {:city-code 889},
      :address       "Laitilantie 5",
      :geometries
      {:type "FeatureCollection",
       :features
       [{:type "Feature",
         :geometry
         {:type        "Point",
          :coordinates [26.4131256689191 64.7631112249574]}}]},
      :postal-code   "91600",
      :postal-office "Utajärvi"},
     :owner             "city",
     :hall-id           "91600UT1"})

  (-> uta-jh
      :location
      :city
      :city-code
      (utils/zero-left-pad 3))

  (def uta-jh-with-ptv-meta
    (-> uta-jh
        (assoc :ptv {:languages                   ["fi" "en"]
                     :summary                     {:fi "Tiivistelmä suomeksi"
                                                   :se "Jätte tiivistelmä på svenska"
                                                   :en "English Summary text"}
                     :description                 {:fi "Kuvaus suomeksi"
                                                   :se "Jätte deskription på svenska"
                                                   :en "Description in English"}
                     :org-id                      "11111-aaaaa-bbbbb-cccccc-ddddd"
                     :sync-enabled                true
                     :service-integration         "manual"
                     :descriptions-integration    "lipas-managed"
                     :service-channel-integration "lipas-managed"
                     :service-ids                 #{"sid-1"}
                     :service-channel-ids         #{"ssid-1"}})))


  (->ptv-service-location nil (constantly [123 456])  uta-jh-with-ptv-meta)



  )
