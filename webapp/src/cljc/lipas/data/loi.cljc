(ns lipas.data.loi
  (:require [lipas.data.status :as status]))

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
     :description {:fi "Esim. \"Haltia pihan opastustaulu\""
                   :se "T.ex. \"Haltia gårds informationsstavla\""
                   :en "E.g. \"Information board on Haltia yard\""}
     :label       {:fi "Kohteen nimi"
                   :se "Objektets namn"
                   :en "Name of the object"}}}

   :description
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Rakenteen esittämiseen liittyvää tietoa."
                   :se "Information om presentationen av strukturen"
                   :en "Information about the presentation of the structure"}
     :label       {:fi "Yleiskuvaus"
                   :se "Allmän beskrivning"
                   :en "Overview"}}}

   :images
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} localized-string-schema]
              [:alt-text {:optional true} localized-string-schema]
              [:copyright {:optional true} localized-string-schema]]]
    :field
    {:type        "images"
     :description {:fi "Lisää kohteen maisemia, luontoa tai harrastamisen olosuhteita esitteleviä valokuvia. Voit lisätä vain kuvatiedostoja, et URL-kuvalinkkejä. Kelvollisia tiedostomuotoja ovat .jpg, .jpeg ja .png. Varmista, että sinulla on oikeus lisätä kuva."
                   :se "Lägg till foton som visar landskapet, naturen eller rekreationsförhållandena på platsen. Du kan bara lägga till bildfiler, inte URL-bildlänkar. Giltiga filformat är .jpg, .jpeg och .png. Se till att du har behörighet att lägga till bilden."
                   :en "Add photographs depicting the landscapes, nature, or conditions of activity of the place. You can add only image files, not URL links. Allowed file extensions are .jpg, .jpeg and .png. Make sure that you have a permission to add the photographs."}
     :label       {:fi "Valokuvat"
                   :se "Fotografier"
                   :en "Photographs"}
     :props
     {:url
      {:field
       {:type   "text-field"
        :hidden true}}
      :description
      {:field
       {:type        "textarea"
        :description {:fi "Kuvan yhteydessä kaikille näytettävä teksti kuvassa esitettävistä asioista."
                      :se "Text som visas tillsammans med bilden om vad som visas i den."
                      :en "Text to be displayed with the image about what is shown in it."}
        :label       {:fi "Kuvateksti"
                      :se "Bildtext"
                      :en "Image description"}}}
      :alt-text
      {:field
       {:type        "textarea"
        :description {:fi "Ruudunlukijan näkövammaisille kertoma teksti kuvassa esitettävistä asioista. Lue lisää: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"
                      :se "Text som skärmläsaren berättar för synskadade om vad som visas i bilden. Läs mer: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"
                      :en "Text that the screen reader tells visually impaired users about what is shown in the image. Read more: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"}
        :label       {:fi "Alt-teksti"
                      :se "Alt-text"
                      :en "Alt text"}}}

      :copyright
      {:field
       {:type        "textarea"
        :description {:fi "Syötä kuvan ottaja, kuvan lähde, mahdollinen lisenssi sekä päivämäärä, jos tiedossa."
                      :se "Ange fotografens namn, bildkälla, eventuell licens och datum om det är känt."
                      :en "Enter the photographer, image source, possible license, and date if known."}
        :label       {:fi "Tekijänoikeustiedot"
                      :se "Upphovsrättsinformation"
                      :en "Copyright information"}}}}}}})

(def accessibility-props
  {:accessible?
   {:schema [:boolean]
    :field
    {:type        "checkbox"
     :description {:fi "Onko kohde esteetön"
                   :se "Är destinationen tillgänglig?"
                   :en "Is the destination accessible?"}
     :label       {:fi "Esteetön"
                   :se "Tillgänglig"
                   :en "Accessible"}}}

   :accessibility
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Yleistä tietoa kohteen esteettömyydestä"
                   :se "Allmän information om platsens tillgänglighet"
                   :en "General information about the accessibility of the place"}
     :label       {:fi "Esteettömyys"
                   :se "Tillgänglighet"
                   :en "Accessibility"}}}})

(def fire-props
  {:use-structure-during-fire-warning
   {:schema [:boolean]
    :field
    {:type        "checkbox"
     :label       {:fi "Rakenteen käyttö maastopalovaroituksen aikana"
                   :se "Användning av strukturen under en varning för terrängbrand"
                   :en "Use of structure under a wildfire warning"}
     :description {:fi "Valitse kenttä, jos rakennetta on sallittua käyttää tulentekoon maastopalovaroituksen aikana"
                   :se "Välj fältet om strukturen får användas för att göra upp eld under en varning för terrängsbrand."
                   :en "Select the field if the structure can to be used for making a campfire during a wildfire warning."}}}})

(def water-conditions-hazards
  {"rapid"      {:fi "Koski"
                 :se "Fors"
                 :en "Rapid"}
   "open-water" {:fi "Avoin selkä"
                 :se "Öppen sjö"
                 :en "Open part of the lake"}})

;; NOTE: The current select input used in loi.views always sorts the options
(def protected-area-opts
  {"forbidden" {:fi "Alueella liikkuminen on kielletty kokonaan"
                :se "Området är helt förbjudet att vistas på"
                :en "Movement in the area is completely forbidden"}
   "restricted" {:fi "Alueella liikkumista tai sen käyttöä on rajoitettu"
                 :se "Vistelse eller användning av området är begränsad"
                 :en "Movement or use of the area is restricted"}
   "allowed" {:fi "Alueella liikkuminen on sallittu"
              :se "Vistelse i området är tillåten"
              :en "Movement in the area is allowed"}})

(def protected-area-fields
  {:protected-area-specification
   {:schema (into [:enum] (keys protected-area-opts))
    :field {:type "select"
            :label {:fi "Tarkenne"
                    :se "Specifikation"
                    :en "Specification"}
            :opts protected-area-opts}}})

(def categories
  {"water-conditions"
   {:label {:fi "Vesiolosuhteet" :se "Vattenförhållanden" :en "Water conditions"}
    :types
    {:hazard
     {:label {:fi "Vaaranpaikka" :se "Farlig plats" :en "Dangerous place"}
      :value "hazard"
      :props common-props}

     :landing-spot
     {:label {:fi "Rantautumispaikka" :se "Landstigningsplats" :en "Landing spot"}
      :value "landing-spot"
      :props common-props}

     :rapid
     {:label {:fi "Koski" :se "Fors" :en "Rapid"}
      :value "rapid"
      :props common-props}

     :open-water
     {:label {:fi "Avoin selkä" :se "Öppen sjö" :en "Open part of the lake"}
      :value "open-water"
      :props common-props}

     :boat-lane
     {:label {:fi "Veneväylä" :se "Farled" :en "Boat lane"}
      :value "boat-lane"
      :props common-props}

     :whitewater-canoeing
     {:label {:fi "Koskimelontakohde" :se "Forspaddlingsplats" :en "Rapid paddling site"}
      :value "whitewater-canoeing"
      :props common-props}}}

   "outdoor-recreation-facilities"
   {:label {:fi "Retkeily ja ulkoilurakenteet" :se "Frilufts- och vandringsanläggningar" :en "Outdoor recreation facilities"}
    :types
    {:information-board
     {:label {:fi "Infotaulu" :se "Informationsstavla" :en "Information board"}
      :value "information-board"
      :props (merge
              common-props
              accessibility-props)}

     :parking-spot
     {:label {:fi "Pysäköintipaikka" :se "Parkeringsplats" :en "Parking spot"}
      :value "parking-spot"
      :props (merge common-props accessibility-props)}

     :canopy
     {:label {:fi "Katos" :se "Tak" :en "Canopy"}
      :value "canopy"
      :props (merge common-props accessibility-props)}

     :cooking-shelter
     {:label {:fi "Keittokatos" :se "Kokskjul" :en "Cooking shelter"}
      :value "cooking-shelter"
      :props
      (merge common-props accessibility-props fire-props)}

     :fire-pit
     {:label {:fi "Tulentekopaikka" :se "Eldplats" :en "Fire pit"}
      :value "fire-pit"
      :props
      (merge common-props accessibility-props fire-props)}

     :rest-area
     {:label {:fi "Taukopaikka" :se "Rastplats" :en "Rest area"}
      :value "rest-area"
      :props (merge common-props accessibility-props)}

     :woodshed
     {:label {:fi "Puuvaja" :se "Vedlider" :en "Woodshed"}
      :value "woodshed"
      :props (merge common-props accessibility-props)}

     :dry-toilet
     {:label {:fi "Kuivakäymälä" :se "Torrkloset" :en "Dry toilet"}
      :value "dry-toilet"
      :props (merge common-props accessibility-props)}

     :wc
     {:label {:fi "WC" :se "WC" :en "Toilet"}
      :value "wc"
      :props (merge common-props accessibility-props)}

     :tent-site
     {:label {:fi "Telttapaikka" :se "Tältplats" :en "Tent site"}
      :value "tent-site"
      :props (merge common-props accessibility-props)}

     :sauna
     {:label {:fi "Sauna" :en "Sauna" :se "Bastu"}
      :value "sauna"
      :props (merge common-props)}

     :well
     {:label {:fi "Kaivo" :se "Brunn" :en "Well"}
      :value "well"
      :props (merge common-props)}

     :water-source
     {:label {:fi "Vesipiste" :en "Water source" :se "Tappställe"}
      :value "water-source"
      :props (merge common-props)}

     :viewpoint
     {:label {:fi "Näköalapaikka" :en "Viewpoint" :se "Utsiktspunkt"}
      :value "viewpoint"
      :props (merge common-props)}

     :viewing-platform
     {:label {:fi "Näköalatasanne" :en "Viewing platform" :se "Utsiktsterrass"}
      :value "viewing-platform"
      :props (merge common-props accessibility-props)}

     :refueling-point
     {:label {:fi "Tankkauspiste" :en "Refueling point" :se "Tankningsplats"}
      :value "refueling-point"
      :props (merge common-props)}

     :rowboat-rental
     {:label {:fi "Kanootti-/soutuvenevuokraus" :en "Canoe/rowboat rental" :se "Kanot-/roddbåtuthyrning"}
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

     ;; Removed due to these belonging to sports site type 203
     ;;
     ;; :canoe-dock
     ;; {:label {:fi "Melontalaituri" :en "Canoe dock" :se "Kanotbrygga"}
     ;;  :value "canoe-dock"
     ;;  :props (merge common-props)}

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
      :props (merge common-props accessibility-props)}

     :bridge
     {:label {:fi "Silta" :en "Bridge" :se "Bro"}
      :value "bridge"
      :props (merge common-props)}}}

   "natural-attractions-and-geo-objects"
   {:label {:fi "Luonnonnähtävyydet / geokohteet" :se "Natursevärdheter / Geologiska platser" :en "Natural Attractions / Geological sites"}
    :types
    {:geo-object
     {:label {:fi "Geokohde" :en "Geographic object" :se "Geologisk plats"}
      :value "geo-object"
      :props (merge common-props)}

     :natural-attraction
     {:label {:fi "Luonnonnähtävyys" :en "Natural attraction" :se "Natursevärdhet"}
      :value "natural-attraction"
      :props (merge common-props)}}}

   "protected-areas"
   {:label {:fi "Käyttörajoitusalueet" :se "Områden med begränsat tillträde" :en "Restricted areas"}
    :types
    {:nature-reserve
     {:label     {:fi "Luonnonsuojelualue" :se "Naturreservat" :en "Nature reserve"}
      :value     "nature-reserve"
      :geom-type "Polygon"
      :props     (merge common-props protected-area-fields)}

     :other-area-with-movement-restrictions
     {:label     {:fi "Muu alue, jolla on liikkumisrajoituksia"
                  :se "Annat område med rörelserestriktioner"
                  :en "Other area with movement restrictions"}
      :value     "other-area-with-movement-restrictions"
      :geom-type "Polygon"
      :props     (merge common-props protected-area-fields)}}}})

(def types (->> categories
                vals
                (mapcat :types)
                (into {})))
