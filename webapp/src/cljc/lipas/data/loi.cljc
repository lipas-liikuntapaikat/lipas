(ns lipas.data.loi
  (:require
   #?(:clj [cheshire.core :as json])
   [lipas.data.status :as status]
   [malli.core :as m]
   [malli.json-schema :as json-schema]
   [malli.util :as mu]))

(def statuses status/statuses)

(def localized-string-schema
  [:map
   [:fi {:optional true} [:string]]
   [:se {:optional true} [:string]]
   [:en {:optional true} [:string]]])

(def common-props
  {:name
   {:schema localized-string-schema
    :field
    {:type        "textfield"
     :description {:fi "Esim. \"Haltia pihan opastustaulu\""}
     :label       {:fi "Kohteen nimi"}}}

   :description
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Rakenteen esittämiseen liittyvää tietoa."}
     :label       {:fi "Yleiskuvaus"}}}

   :images
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} localized-string-schema]
              [:alt-text {:optional true} localized-string-schema]]]
    :field
    {:type        "images"
     :description {:fi "Lisää kohteen maisemia, luontoa tai harrastamisen olosuhteita esitteleviä valokuvia. Voit lisätä vain kuvatiedostoja, et URL-kuvalinkkejä. Kelvollisia tiedostomuotoja ovat .jpg, .jpeg ja .png. Varmista, että sinulla on oikeus lisätä kuva."}
     :label       {:fi "Valokuvat"}
     :props
     {:url
      {:field
       {:type   "text-field"
        :hidden true}}
      :description
      {:field
       {:type        "textarea"
        :description {:fi "Kuvan yhteydessä kaikille näytettävä teksti kuvassa esitettävistä asioista."}
        :label       {:fi "Kuvateksti"}}}
      :alt-text
      {:field
       {:type        "textarea"
        :description {:fi "Ruudunlukijan näkövammaisille kertoma teksti kuvassa esitettävistä asioista. Lue lisää: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"}
        :label       {:fi "Alt-teksti"}}}}}}})

(def accessibility-props
  {:accessible?
   {:schema [:boolean]
    :field
    {:type        "checkbox"
     :description {:fi "Onko kohde esteetön"}
     :label       {:fi "Esteetön"}}}

   :accessibility
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Yleistä tietoa kohteen esteettömyydestä"}
     :label       {:fi "Esteettömyys"}}}})

(def fire-props
  {:use-structure-during-fire-warning
   {:schema [:boolean]
    :field
    {:type        "checkbox"
     :label       {:fi "Rakenteen käyttö maastopalovaroituksen aikana"
                   :en ""
                   :se ""}
     :description {:fi "Valitse kenttä, jos rakennetta on sallittua käyttää tulentekoon maastopalovaroituksen aikana"
                   :se ""
                   :en ""}}}})

(def water-conditions-hazards
  {"rapid"      {:fi "Koski"}
   "open-water" {:fi "Avoin selkä"}})

(def categories
  {"water-conditions"
   {:label {:fi "Vesiolosuhteet"}
    :types
    {:hazard
     {:label {:fi "Vaaranpaikka"}
      :value "hazard"
      :props common-props
      #_     (merge
              (select-keys common-props [:name])
              {:hazard-type
               {:schema (into [:enum] (keys water-conditions-hazards))
                :field
                {:type        "select"
                 :label       {:fi "Tyyppi"}
                 :description {:fi "Vaaranpaikan tyyppi"}
                 :opts        water-conditions-hazards}}
               :description
               {:schema localized-string-schema
                :field
                {:type        "textarea"
                 :label       {:fi "Kuvaus"}
                 :description {:fi "Tekstimuotoinen kuvaus vaaranpaikasta"}}}})}

     :landing-spot
     {:label {:fi "Rantautumispaikka"}
      :value "landing-spot"
      :props common-props}

     :rapid
     {:label {:fi "Koski"}
      :value "rapid"
      :props common-props}

     :open-water
     {:label {:fi "Avoin selkä"}
      :value "open-water"
      :props common-props}

     :boat-lane
     {:label {:fi "Veneväylä"}
      :value "boat-lane"
      :props common-props}

     :whitewater-canoeing
     {:label {:fi "Koskimelontakohde"}
      :value "whitewater-canoeing"
      :props common-props}}}

   "outdoor-recreation-facilities"
   {:label {:fi "Retkeily ja ulkoilurakenteet"}
    :types
    {:information-board
     {:label {:fi "Infotaulu"}
      :value "information-board"
      :props (merge
              common-props
              accessibility-props)}

     :parking-spot
     {:label {:fi "Pysäköintipaikka"}
      :value "parking-spot"
      :props (merge common-props accessibility-props)}

     :canopy
     {:label {:fi "Katos"}
      :value "canopy"
      :props (merge common-props accessibility-props)}

     :cooking-shelter
     {:label {:fi "Keittokatos"}
      :value "cooking-shelter"
      :props
      (merge common-props accessibility-props fire-props)}

     :fire-pit
     {:label {:fi "Tulentekopaikka"}
      :value "fire-pit"
      :props
      (merge common-props accessibility-props fire-props)}

     :rest-area
     {:label {:fi "Taukopaikka"}
      :value "rest-area"
      :props (merge common-props accessibility-props)}

     :woodshed
     {:label {:fi "Puuvaja"}
      :value "woodshed"
      :props (merge common-props accessibility-props)}

     :dry-toilet
     {:label {:fi "Kuivakäymälä"}
      :value "dry-toilet"
      :props (merge common-props accessibility-props)}

     :wc
     {:label {:fi "WC"}
      :value "wc"
      :props (merge common-props accessibility-props)}

     :tent-site
     {:label {:fi "Telttapaikka"}
      :value "tent-site"
      :props (merge common-props accessibility-props)}

     :sauna
     {:label {:fi "Sauna" :en "Sauna" :se "Sauna"}
      :value "sauna"
      :props (merge common-props)}

     :well
     {:label {:fi "Kaivo" :en "Well"}
      :value "well"
      :props (merge common-props)}

     :water-source
     {:label {:fi "Vesipiste" :en "Water source"}
      :value "water-source"
      :props (merge common-props)}

     :viewpoint
     {:label {:fi "Näköalapaikka" :en "Viewpoint"}
      :value "viewpoint"
      :props (merge common-props)}

     :viewing-platform
     {:label {:fi "Näköalatasanne" :en "Viewing platform"}
      :value "viewing-platform"
      :props (merge common-props accessibility-props)}

     :refueling-point
     {:label {:fi "Tankkauspiste" :en "Refueling point"}
      :value "refueling-point"
      :props (merge common-props)}

     :rowboat-rental
     {:label {:fi "Soutuvenevuokraus" :en "Rowboat rental" :se "Roddbåtuthyrning"}
      :value "rowboat-rental"
      :props (merge common-props)}

     :sauna-rental
     {:label {:fi "Vuokrasauna" :en "Sauna rental" :se "Bastuuthyrning"}
      :value "sauna-rental"
      :props (merge common-props)}

     :accommodation-rental
     {:label {:fi "Vuokramajoitus" :en "Accommodation rental" :se "Boendeuthyrning"}
      :value "accommodation-rental"
      :props (merge common-props)}

     :space-rental
     {:label {:fi "Vuokratila" :en "Space rental" :se "Lokaluthyrning"}
      :value "space-rental"
      :props (merge common-props)}

     :reservation-campsite
     {:label {:fi "Varaustulipaikka" :en "Reservation campsite" :se "Bokningscampingplats"}
      :value "reservation-campsite"
      :props
      (merge common-props fire-props)}

     :dog-swimming-area
     {:label {:fi "Koirien uintipaikka" :en "Dog swimming area" :se "Hundsimningsområde"}
      :value "dog-swimming-area"
      :props (merge common-props)}

     :changing-room
     {:label {:fi "Pukukoppi" :en "Changing room" :se "Omklädningsrum"}
      :value "changing-room"
      :props (merge common-props)}

     :swimming-pier
     {:label {:fi "Uimalaituri" :en "Swimming pier" :se "Badbrygga"}
      :value "swimming-pier"
      :props (merge common-props)}

     :boat-dock
     {:label {:fi "Venelaituri" :en "Boat dock" :se "Båtbrygga"}
      :value "boat-dock"
      :props (merge common-props)}

     :guest-boat-dock
     {:label {:fi "Vierasvenelaituri" :en "Guest boat dock" :se "Gästbåtbrygga"}
      :value "guest-boat-dock"
      :props (merge common-props)}

     :canoe-dock
     {:label {:fi "Melontalaituri" :en "Canoe dock" :se "Kanotbrygga"}
      :value "canoe-dock"
      :props (merge common-props)}

     :mooring-ring
     {:label {:fi "Kiinnitysrengas" :en "Mooring ring" :se "Mooringsring"}
      :value "mooring-ring"
      :props (merge common-props)}

     :mooring-buoy
     {:label {:fi "Kiinnityspoiju" :en "Mooring buoy" :se "Förtöjningsboj"}
      :value "mooring-buoy"
      :props (merge common-props)}

     :boat-ramp
     {:label {:fi "Veneluiska" :en "Boat ramp" :se "Båtramp"}
      :value "boat-ramp"
      :props (merge common-props)}

     :passenger-ferry
     {:label {:fi "Yhteysalus" :en "Passenger ferry" :se "Passagerarfärja"}
      :value "passenger-ferry"
      :props (merge common-props)}

     :ferry
     {:label {:fi "Lossi" :en "Ferry" :se "Färja"}
      :value "ferry"
      :props (merge common-props)}

     :chain-ferry
     {:label {:fi "Kapulalossi" :en "Chain ferry" :se "Kedjefärja"}
      :value "chain-ferry"
      :props (merge common-props)}

     :stairs
     {:label {:fi "Portaat" :en "Stairs" :se "Trappor"}
      :value "stairs"
      :props (merge common-props)}

     :waste-disposal-point
     {:label {:fi "Jätepiste" :en "Waste disposal point" :se "Avfallspunkt"}
      :value "waste-disposal-point"
      :props (merge common-props)}

     :recycling-point
     {:label {:fi "Kierrätyspiste" :en "Recycling point" :se "Återvinningspunkt"}
      :value "recycling-point"
      :props (merge common-props)}

     :building
     {:label {:fi "Rakennus" :en "Building" :se "Byggnad"}
      :value "building"
      :props (merge common-props)}

     :historical-building
     {:label {:fi "Historiallinen rakennus" :en "Historical building" :se "Historisk byggnad"}
      :value "historical-building"
      :props (merge common-props)}

     :historical-structure
     {:label {:fi "Historiallinen rakennelma" :en "Historical structure" :se "Historisk struktur"}
      :value "historical-structure"
      :props (merge common-props)}

     :old-defense-building
     {:label {:fi "Vanha puolustusrakennus" :en "Old defense building" :se "Gammalt försvarsbyggnad"}
      :value "old-defense-building"
      :props (merge common-props)}

     :monument
     {:label {:fi "Muistomerkki" :en "Monument" :se "Minnesmärke"}
      :value "monument"
      :props (merge common-props)}

     :septic-tank-emptying
     {:label {:fi "Septitankin tyhjennys" :en "Septic tank emptying" :se "Tömning av slamavskiljare"}
      :value "septic-tank-emptying"
      :props (merge common-props)}

     :fishing-pier
     {:label {:fi "Kalastuslaituri" :en "Fishing pier" :se "Fiskedäck"}
      :value "fishing-pier"
      :props accessibility-props}

     :bridge
     {:label {:fi "Silta" :en "Bridge" :se "Bro"}
      :value "bridge"
      :props (merge common-props)}

     }}

   "natural-attractions-and-geo-objects"
   {:label {:fi "Luonnonnähtävyydet / geokohteet"}
    :types

    {:geo-object
     {:label {:fi "Geokohde" :en "Geographic object" :se ""}
      :value "geo-object"
      :props (merge common-props)}

     :natural-attraction
     {:label {:fi "Luonnonnähtävyys" :en "Natural attraction" :se "Natursevärdhet"}
      :value "natural-attraction"
      :props (merge common-props)}}}

   "protected-areas"
   {:label {:fi "Käyttörajoitusalueet"}
    :types

    {:nature-reserve
     {:label     {:fi "Luonnonsuojelualue" :en "Nature reserve" :se "Naturreservat"}
      :value     "nature-reserve"
      :geom-type "Polygon"
      :props     (merge common-props)}

     :other-area-with-movement-restrictions
     {:label     {:fi "Muu alue, jolla on liikkumisrajoituksia"
                  :en "Other area with movement restrictions"
                  :se "Annat område med rörelserestriktioner"}
      :value     "other-area-with-movement-restrictions"
      :geom-type "Polygon"
      :props     (merge common-props)}}}})

(def point-fcoll-schema
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential
     [:map
      [:id {:optional true} [:string]]
      [:type [:enum "Feature"]]
      [:properties {:optional true} [:map]]
      [:geometry
       [:map
        [:type [:enum "Point"]]
        [:coordinates
         [:or
          [:tuple :double :double]
          [:tuple :double :double :double]]]]]]]]])

(def schema
  (into [:or]
        (for [[cat-k cat-v] categories
              [_type-k type-v] (:types cat-v)]
          (into
           [:map {:description (str cat-k " > " (:value type-v))
                  :title (-> type-v :label :fi)}
            [:id [:string]]
            [:event-date [:string]]
            [:created-at [:string]]
            [:geometries point-fcoll-schema]
            [:status (into [:enum] (keys statuses))]
            [:loi-category [:enum cat-k]]
            [:loi-type [:enum (:value type-v)]]]
           (for [[prop-k prop-v] (:props type-v)]
             [prop-k {:optional true} (:schema prop-v)])))))

(defn gen-json-schema
  []
  (-> schema
      json-schema/transform
      #?(:clj(json/encode {:pretty true})
         :cljs clj->js)
      println))

(comment
  (gen-json-schema)

  (json-schema/transform [:tuple :double :double])
  ;; => {:type "array",
  ;;     :items [{:type "number"} {:type "number"}],
  ;;     :additionalItems false}

  )

(def types (->> categories
                vals
                (mapcat :types)
                (into {})))



;; Esteettömyys: boolean + infoteksti
;; Reiteissä: esteetön, vaativa esteetön
