(ns lipas.data.loi
  (:require [lipas.data.status :as status]))

(def statuses status/statuses)

(def common-props
  {:accessibility {:field
                   {:type        "text-field"
                    :description {:fi "Tähän joku järkevä ohje"}
                    :label       {:fi "Esteettömyys"}}}})

(def categories
  {"outdoor-recreation-facilities"
   {:label {:fi "Retkeily ja ulkoilurakenteet"}
    :types
    {:information-board
     {:label {:fi "Infotaulu"}
      :value "information-board"
      :props common-props}

     :parking-spot
     {:label {:fi "Pysäköintipaikka"}
      :value "parking-spot"
      :props common-props}

     :canopy
     {:label {:fi "Katos"}
      :value "canopy"
      :props common-props}

     :cooking-shelter
     {:label {:fi "Keittokatos"}
      :value "cooking-shelter"
      :props common-props}

     :fire-pit
     {:label {:fi "Tulentekopaikka"}
      :value "fire-pit"
      :props common-props}

     :rest-area
     {:label {:fi "Taukopaikka"}
      :value "rest-area"
      :props common-props}

     :woodshed
     {:label {:fi "Puuvaja"}
      :value "woodshed"
      :props common-props}

     :dry-toilet
     {:label {:fi "Kuivakäymälä"}
      :value "dry-toilet"
      :props common-props}

     :tent-site
     {:label {:fi "Telttapaikka"}
      :value "tent-site"
      :props common-props}

     :sauna
     {:label {:fi "Sauna" :en "Sauna" :se "Sauna"}
      :value "sauna"
      :props {}}

     :well
     {:label {:fi "Kaivo" :en "Well"}
      :value "well"
      :props {}}

     :water-source
     {:label {:fi "Vesipiste" :en "Water source"}
      :value "water-source"
      :props {}}

     :viewpoint
     {:label {:fi "Näköalapaikka" :en "Viewpoint"}
      :value "viewpoint"
      :props {}}

     :viewing-platform
     {:label {:fi "Näköalatasanne" :en "Viewing platform"}
      :value "viewing-platform"
      :props common-props}

     :refueling-point
     {:label {:fi "Tankkauspiste" :en "Refueling point"}
      :value "refueling-point"
      :props {}}

     :rowboat-rental
     {:label {:fi "Soutuvenevuokraus" :en "Rowboat rental" :se "Roddhyra uthyrning"}
      :value "rowboat-rental"
      :props {}}

     :sauna-rental
     {:label {:fi "Vuokrasauna" :en "Sauna rental" :se "Bastuuthyrning"}
      :value "sauna-rental"
      :props {}}

     :accommodation-rental
     {:label {:fi "Vuokramajoitus" :en "Accommodation rental" :se "Boendeuthyrning"}
      :value "accommodation-rental"
      :props {}}

     :space-rental
     {:label {:fi "Vuokratila" :en "Space rental" :se "Lokaluthyrning"}
      :value "space-rental"
      :props {}}

     :reservation-campsite
     {:label {:fi "Varaustulipaikka" :en "Reservation campsite" :se "Bokningscampingplats"}
      :value "reservation-campsite"
      :props {}}

     :dog-swimming-area
     {:label {:fi "Koirien uintipaikka" :en "Dog swimming area" :se "Hundsimningsområde"}
      :value "dog-swimming-area"
      :props {}}

     :changing-room
     {:label {:fi "Pukukoppi" :en "Changing room" :se "Omklädningsrum"}
      :value "changing-room"
      :props {}}

     :swimming-pier
     {:label {:fi "Uimalaituri" :en "Swimming pier" :se "Badbrygga"}
      :value "swimming-pier"
      :props {}}

     :boat-dock
     {:label {:fi "Venelaituri" :en "Boat dock" :se "Båtbrygga"}
      :value "boat-dock"
      :props {}}

     :guest-boat-dock
     {:label {:fi "Vierasvenelaituri" :en "Guest boat dock" :se "Gästbåtbrygga"}
      :value "guest-boat-dock"
      :props {}}

     :canoe-dock
     {:label {:fi "Melontalaituri" :en "Canoe dock" :se "Kanotbrygga"}
      :value "canoe-dock"
      :props {}}

     :mooring-ring
     {:label {:fi "Kiinnitysrengas" :en "Mooring ring" :se "Mooringsring"}
      :value "mooring-ring"
      :props {}}

     :mooring-buoy
     {:label {:fi "Kiinnityspoiju" :en "Mooring buoy" :se "Förtöjningsboj"}
      :value "mooring-buoy"
      :props {}}

     :boat-ramp
     {:label {:fi "Veneluiska" :en "Boat ramp" :se "Båtramp"}
      :value "boat-ramp"
      :props {}}

     :passenger-ferry
     {:label {:fi "Yhteysalus" :en "Passenger ferry" :se "Passagerarfärja"}
      :value "passenger-ferry"
      :props {}}

     :ferry
     {:label {:fi "Lossi" :en "Ferry" :se "Färja"}
      :value "ferry"
      :props {}}

     :chain-ferry
     {:label {:fi "Kapulalossi" :en "Chain ferry" :se "Kedjefärja"}
      :value "chain-ferry"
      :props {}}

     :stairs
     {:label {:fi "Portaat" :en "Stairs" :se "Trappor"}
      :value "stairs"
      :props {}}

     :waste-disposal-point
     {:label {:fi "Jätepiste" :en "Waste disposal point" :se "Avfallspunkt"}
      :value "waste-disposal-point"
      :props {}}

     :recycling-point
     {:label {:fi "Kierrätyspiste" :en "Recycling point" :se "Återvinningspunkt"}
      :value "recycling-point"
      :props {}}

     :building
     {:label {:fi "Rakennus" :en "Building" :se "Byggnad"}
      :value "building"
      :props {}}

     :historical-building
     {:label {:fi "Historiallinen rakennus" :en "Historical building" :se "Historisk byggnad"}
      :value "historical-building"
      :props {}}

     :historical-structure
     {:label {:fi "Historiallinen rakennelma" :en "Historical structure" :se "Historisk struktur"}
      :value "historical-structure"
      :props {}}

     :old-defense-building
     {:label {:fi "Vanha puolustusrakennus" :en "Old defense building" :se "Gammalt försvarsbyggnad"}
      :value "old-defense-building"
      :props {}}

     :monument
     {:label {:fi "Muistomerkki" :en "Monument" :se "Minnesmärke"}
      :value "monument"
      :props {}}

     :septic-tank-emptying
     {:label {:fi "Septitankin tyhjennys" :en "Septic tank emptying" :se "Tömning av slamavskiljare"}
      :value "septic-tank-emptying"
      :props {}}

     :fishing-pier
     {:label {:fi "Kalastuslaituri" :en "Fishing pier" :se "Fiskedäck"}
      :value "fishing-pier"
      :props common-props}

     :bridge
     {:label {:fi "Silta" :en "Bridge" :se "Bro"}
      :value "bridge"
      :props {}}

     }}})

(def types (->> categories
                vals
                (mapcat :types)
                (into {})))
