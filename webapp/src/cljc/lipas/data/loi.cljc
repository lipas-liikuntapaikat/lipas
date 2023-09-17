(ns lipas.data.loi
  (:require [lipas.data.status :as status]))

(def statuses status/statuses)

(def common-props
  {:name
   {:field
    {:type        "textfield"
     :description {:fi "Esim. \"Haltia pihan opastustaulu\""}
     :label       {:fi "Kohteen nimi"}}}

   :description
   {:field
    {:type        "textarea"
     :description {:fi "Rakenteen esittämiseen liittyvää tietoa."}
     :label       {:fi "Yleiskuvaus"}}}

   :images
   {:field
    {:type        "images"
     :description {:fi ""}
     :label       {:fi "Valokuvat"}}}})

(def accessibility-props
  {:accessible?
   {:field
    {:type        "checkbox"
     :description {:fi "Tähän joku järkevä ohje"}
     :label       {:fi "Esteetön"}}}

   :accessibility
   {:field
    {:type        "textarea"
     :description {:fi "Tähän joku järkevä ohje"}
     :label       {:fi "Esteettömyys"}}}})

(def categories
  {"outdoor-recreation-facilities"
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
      :props (merge common-props accessibility-props)}

     :fire-pit
     {:label {:fi "Tulentekopaikka"}
      :value "fire-pit"
      :props (merge common-props accessibility-props)}

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
     {:label {:fi "Soutuvenevuokraus" :en "Rowboat rental" :se "Roddhyra uthyrning"}
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
      :props (merge common-props)}

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

     }}})

(def types (->> categories
                vals
                (mapcat :types)
                (into {})))



;; Esteettömyys: boolean + infoteksti
;; Reiteissä: esteetön, vaativa esteetön
