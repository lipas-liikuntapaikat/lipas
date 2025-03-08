(ns lipas.data.prop-types
  "Type codes went through a major overhaul in the summer of 2024. This
  namespace represents the changes made."
  (:require
   [lipas.data.materials :as materials]))

(def all
  {:height-m
   {:name
    {:fi "Tilan korkeus m", :se "Utrymmets höjd", :en "Venue's height"},
    :data-type "numeric",
    :description
    {:fi "Sisäliikuntatilan korkeus metreinä (matalin kohta)",
     :se "Motionssalens höjd i meter (från lägsta punkten)",
     :en "Height of the indoor sports facility in meters (lowest point)"}},
   :heating?
   {:name      {:fi "Lämmitys", :se "Uppvärmning", :en "Heating"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikassa lämmitys",
     :se "Är idrottsplatsen utrustad med uppvärmning",
     :en "Is there heating in the sports facility"}},
   :field-2-area-m2
   {:name
    {:fi "2. kentän ala m2",
     :se "Andra planens areal m2",
     :en "2. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän pinta-ala neliömetreinä",
     :se "Andra planens areal i kvadratmeter",
     :en "2. field's area in square meters"}},
   :surface-material
   {:name
    {:fi "Pintamateriaali", :se "Ytmaterial", :en "Surface material"},
    :data-type "enum-coll",
    :opts (update-vals materials/surface-materials (fn [m] {:label m}))
    :description
    {:fi
     "Liikunta-alueiden pääasiallinen pintamateriaali - tarkempi kuvaus liikuntapaikan eri tilojen pintamateriaalista voidaan antaa pintamateriaalin lisätietokentässä",
     :se
     "Huvudsakligt ytskikt för idrottsområden - en mer detaljerad beskrivning av ytskiktet i olika delar av idrottsanläggningen kan ges i tilläggsinformationsfältet för ytskikt.",
     :en "Primary surface material of sports areas - a more detailed description of the surface material in different parts of the sports facility can be provided in the additional information field for surface material."}},
   :basketball-fields-count
   {:name
    {:fi "Koripallokentät lkm",
     :se "Antalet korgbollsplaner",
     :en "Basketball fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Koripallokenttien lukumäärä",
     :se "Antalet korgbollsplaner i salen",
     :en "The number of basketball courts"}},
   :surface-material-info
   {:name
    {:fi "Pintamateriaalin lisätieto",
     :se "Ytterligare information om ytmaterialen",
     :en "Surface material information"},
    :data-type "string",
    :description
    {:fi
     "Syötä pintamateriaalin tarkempi kuvaus, kuten tekonurmen yleisnimitys, tuotenimi ja tieto täytemateriaalin laadusta."
     #_"Syötä pintamateriaalin tarkempi kuvaus - esim. tekonurmen yleisnimitys ”esim. Kumirouhetekonurmi”, tuotenimi ja tieto täytemateriaalin laadusta (esim. biohajoava/perinteinen kumirouhe).",
     :se "Ange en mer detaljerad beskrivning av ytskiktet - till exempel den allmänna beteckningen för konstgräs t.ex. gummigranulatkonstgräs, produktnamn och information om fyllnadsmaterialets kvalitet (t.ex. biologiskt nedbrytbart/traditionellt gummigranulat).",
     :en "Enter a more detailed description of the surface material - for example, the general name for artificial turf “e.g., rubber granulate artificial turf,” product name, and information about the quality of the infill material (e.g., biodegradable/traditional rubber granulate)."}},
   :height-of-basket-or-net-adjustable?
   {:name
    {:fi "Korin tai verkon korkeus säädettävissä",
     :se "Korgens eller nätets höjd är justerbar",
     :en "Height of the basket or net is adjustable"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :holes-count
   {:name
    {:fi "Reikien/väylien lkm",
     :se "Antal hål/fairways",
     :en "Number of holes/fairways"},
    :data-type "numeric",
    :description
    {:fi "Väylien lukumäärä", :se "Antalet ranger", :en ""}},
   :skijump-hill-type
   {:name
    {:fi "Hyppyrimäen tyyppi",
     :se "Hoppbackens typ",
     :en "Type of ski jump hill"},
    :data-type "string",
    :description
    {:fi "Hyppyrimäen tyyppi (harjoitus, pienmäki, normaali, suurmäki)",
     :se
     "Typ av hoppbacke (övningsbacke, liten, normalbacke, storbacke)",
     :en "Type of ski jump (training hill, small hill, normal hill, large hill)"}},
   :lifts-count
   {:name      {:fi "Hissit lkm", :se "Antalet skidliftar", :en "Lifts"},
    :data-type "numeric",
    :description
    {:fi "Hiihtohissien lukumäärä",
     :se "Antal skidliftar i skidcentrumet",
     :en "Number of ski lifts"}},
   :field-3-length-m
   {:name
    {:fi "3. kentän pituus m",
     :se "Tredje planens längd m",
     :en "3. field's length m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän pituus metreinä",
     :se "Tredje planens längd i meter",
     :en "Length of the third field in meters"}},
   :pool-tracks-count
   {:name
    {:fi "1. altaan radat lkm",
     :se "Första bassängens antal banor",
     :en "Lanes in 1. pool"},
    :data-type "numeric",
    :description
    {:fi "1. altaan ratojen lukumäärä",
     :se "Antal banor i första bassängen",
     :en "Number of lanes in the first pool"}},
   :field-2-length-m
   {:name
    {:fi "2. kentän pituus m",
     :se "Andra planens längd m",
     :en "2. field's length m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän pituus metreinä",
     :se "Andra planens längd i meter",
     :en "Length of the second field in meters"}},
   :plastic-outrun?
   {:name
    {:fi "Muovitettu alastulo",
     :se "Plast-belagd landning",
     :en "Plastic outrun"},
    :data-type "boolean",
    :description
    {:fi "Muovitettu hyppyrimäen alastulopaikka",
     :se "Hoppbacken har plast-belagd landningsplats",
     :en "Plastic-coated landing area of the ski jump"}},
   :automated-timing?
   {:name
    {:fi "Automaattinen ajanotto",
     :se "Automatisk tidtagning",
     :en "Automatic timing"},
    :data-type "boolean",
    :description
    {:fi "Varustus automaattiseen ajanottoon",
     :se "Utrustning för automatisk tidtagning",
     :en "Equipment for automatic timing"}},
   :freestyle-slope?
   {:name      {:fi "Kumparerinne", :se "Puckelpist", :en "Freestyle slope"},
    :data-type "boolean",
    :description
    {:fi "Hiihtokeskuksessa on kumparerinne",
     :se "Skidcentret har en puckelpist",
     :en "The ski resort has a mogul slope."}},
   :kiosk?
   {:name      {:fi "Kioski", :se "Kiosk", :en "Kiosk"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla kioski tai vastaava",
     :se "Har idrottsplatsen en kiosk eller något liknande",
     :en "Is there a kiosk or similar facility at the sports venue"}},
   :summer-usage?
   {:name      {:fi "Kesäkäyttö", :se "I sommarbruk", :en "Summer usage"},
    :data-type "boolean",
    :description
    {:fi "Käytössä myös kesäisin",
     :se "Tillgänglig även på sommaren",
     :en "Available also in the summer"}},
   :stand-capacity-person
   {:name
    {:fi "Katsomon kapasiteetti hlö",
     :se "Läktarens person kapasitet",
     :en "Stand size"},
    :data-type "numeric",
    :description
    {:fi "Katsomon koko kapasiteetti, henkilölukumäärä",
     :se "Läktarens hela person kapasitet",
     :en "Total capacity of the stands, number of people"}},
   :free-use?
   {:name
    {:fi "Kohde on vapaasti käytettävissä",
     :se "Fri användning",
     :en "Free access"},
    :data-type "boolean",
    :description
    {:fi
     "Liikuntapaikka on vapaasti käytettävissä ilman vuorovarausta tai pääsymaksua",
     :se
     "Motionsplatsen är fri att användas utan tidsbokning eller entréavgift",
     :en ""}},
   :pier?
   {:name        {:fi "Laituri", :se "Brygga", :en "Pier"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :sport-specification
   {:name
    {:fi "Lajitarkenne",
     :se "Sportspecifikation",
     :en "Sport specification"},
    :data-type "enum",
    :opts
    {"floor-disciplines"
     {:label
      {:fi "Lattialajit",
       :en "Floor disciplines",
       :se "Golvdiscipliner"},
      :description
      {:fi "Voimistelulajit, jotka suoritetaan pääasiassa lattialla.",
       :en
       "Gymnastics disciplines that are primarily performed on the floor.",
       :se "Gymnastikdiscipliner som huvudsakligen utförs på golvet."}},
     "apparatus-disciplines"
     {:label
      {:fi "Telinelajit",
       :en "Apparatus disciplines",
       :se "Redskapsgrenar"},
      :description
      {:fi "Voimistelulajit, jotka suoritetaan erilaisilla telineillä.",
       :en
       "Gymnastics disciplines that are performed on various apparatus.",
       :se "Gymnastikdiscipliner som utförs på olika redskap."}},
     "floor-and-apparatus"
     {:label
      {:fi "Lattia- ja telinelajit mahdollisia",
       :en "Both floor and apparatus disciplines possible",
       :se "Både golv- och redskapsgrenar möjliga"},
      :description
      {:fi "Tilassa voidaan harjoitella sekä lattia- että telinelajeja.",
       :en
       "The space allows for practicing both floor and apparatus disciplines.",
       :se
       "Utrymmet möjliggör träning av både golv- och redskapsgrenar."}},
     "cheerleading-circus"
     {:label
      {:fi "Pääasiassa cheerleading- tai sirkusharjoittelukäyttöön",
       :en "Mainly for cheerleading or circus training",
       :se "Huvudsakligen för cheerleading- eller cirkusträning"},
      :description
      {:fi
       "Tila on ensisijaisesti tarkoitettu cheerleading- tai sirkusharjoitteluun.",
       :en
       "The space is primarily intended for cheerleading or circus training.",
       :se
       "Utrymmet är främst avsett för cheerleading- eller cirkusträning."}},
     "no-information"
     {:label
      {:fi "Ei tietoa", :en "No information", :se "Ingen information"}}},
    :description
    {:fi "Valitse voimistelulaji, johon tila on pääasiassa tarkoitettu.",
     :se "",
     :en ""}},
   :may-be-shown-in-excursion-map-fi?
   {:name
    {:fi "Saa julkaista Retkikartta.fi-palvelussa",
     :se "Får publiceras i Utflyktskarta.fi",
     :en "May be shown in Excursionmap.fi"},
    :data-type "boolean",
    :description
    {:fi "Kohteen tiedot saa julkaista Retkikartta.fi-palvelussa",
     :se "Information om motionsstället får publiceras i Retkikartta.fi",
     :en ""}},
   :sprint-lanes-count
   {:name
    {:fi "Etusuorien lkm",
     :se "Antalet raksträckor (framför läktaren)",
     :en "Number of sprint lanes"},
    :data-type "numeric",
    :description
    {:fi "Etusuorien lukumäärä",
     :se "Antalet raksträckor (framför läkataren)",
     :en ""}},
   :javelin-throw-places-count
   {:name
    {:fi "Keihäänheittopaikat lkm",
     :se "Spjutkastningsplatser st.",
     :en "Number of javelin throw places"},
    :data-type "numeric",
    :description
    {:fi "Keihäänheittopaikkojen lukumäärä",
     :se "Antalet spjutkastningsplatser",
     :en ""}},
   :active-space-width-m
   {:name
    {:fi "Liikuntakäytössä olevan tilan leveys m",
     :se "Bredd på aktivt utrymme m",
     :en "Width of active space m"},
    :data-type "numeric",
    :description
    {:fi "Liikuntakäytössä olevan tilan leveys (m)", :se "", :en ""}},
   :tennis-courts-count
   {:name
    {:fi "Tenniskentät lkm",
     :se "Antalet tennisplaner",
     :en "Tennis courts pcs"},
    :data-type "numeric",
    :description
    {:fi "Tenniskenttien lukumäärä",
     :se "Antalet tennisplaner",
     :en "Number of tennis courts"}},
   :ski-service?
   {:name      {:fi "Suksihuolto", :se "Skidservice", :en "Ski service"},
    :data-type "boolean",
    :description
    {:fi "Suksihuoltopiste löytyy",
     :se "Det finns en skidservicepunkt",
     :en ""}},
   :field-1-length-m
   {:name
    {:fi "1. kentän pituus m",
     :se "Första planens längd m",
     :en "1. field's length m"},
    :data-type "numeric",
    :description
    {:fi "1. kentän pituus metreinä",
     :se "Första planens längd i meter",
     :en "Length of the first field in meters"}},
   :mirror-wall?
   {:name      {:fi "Peiliseinä", :se "Spegelvägg", :en "Mirror wall"},
    :data-type "boolean",
    :description
    {:fi "Liikuntatilassa vähintään yhdellä seinällä on kiinteät peilit",
     :se "I idrottsutrymmet finns fasta speglar på minst en vägg.",
     :en "The sports facility has fixed mirrors on at least one wall."}},
   :finish-line-camera?
   {:name      {:fi "Maalikamera", :se "Målkamera", :en "Finish line camera"},
    :data-type "boolean",
    :description
    {:fi "Liikuntapaikalla on maalikamera",
     :se "Idrottsplatsen har en målkamera",
     :en "The sports facility has a finish line camera."}},
   :travel-mode-info
   {:name
    {:fi "Kulkutavat, lisätieto",
     :se "Resesätt, ytterligare information",
     :en "Travel Modes, Additional Information"},
    :data-type "string",
    :description
    {:fi "Täsmennä soveltuvia kulkutapoja tarvittaessa",
     :se "Specificera lämpliga resesätt vid behov",
     :en "Specify suitable travel modes if necessary"}},
   :parking-place?
   {:name
    {:fi "Parkkipaikka", :se "Parkeringsplats", :en "Parking place"},
    :data-type "boolean",
    :description
    {:fi "Parkkipaikka käytettävissä",
     :se "Tillgänglig parkeringsplats",
     :en "Parking available"}},
   :canoeing-club?
   {:name      {:fi "Melontaseura", :se "", :en "Canoeing club"},
    :data-type "boolean",
    :description
    {:fi "Onko kyseessä melontaseuran tila", :se "", :en ""}},
   :total-billiard-tables-count
   {:name
    {:fi "Biljardipöydät yhteensä lkm",
     :se "Totalt antal biljardbord",
     :en "Total number of billiard tables"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :sledding-hill?
   {:name      {:fi "Pulkkamäki", :se "Pulkabacke", :en "Sledding hill"},
    :data-type "boolean",
    :description
    {:fi "Kohteessa on pulkkamäki",
     :se "Det finns en pulkabacke på platsen.",
     :en "There is a sledding hill at the location."}},
   :climbing-routes-count
   {:name
    {:fi "Kiipeilyreittien lkm",
     :se "Antalet klättringsrutter",
     :en "Climbing routes pcs"},
    :data-type "numeric",
    :description
    {:fi "Kiipeilyreittien lukumäärä",
     :se "Antalet klättringsrutter",
     :en "Number of climbing routes"}},
   :outdoor-exercise-machines?
   {:name
    {:fi "Kuntoilutelineitä",
     :se "Gym apparater utomhus",
     :en "Exercise machines outdoors"},
    :data-type "boolean",
    :description
    {:fi "Onko reitin varrella kuntoilulaitteita",
     :se "Finns det gym apparater längs rutten",
     :en "Are there fitness equipment along the route"}},
   :automated-scoring?
   {:name
    {:fi "Kirjanpitoautomaatti",
     :se "Bokföringsautomat",
     :en "Automatic scoring"},
    :data-type "boolean",
    :description
    {:fi "Keilaradalla on sähköinen pistelasku",
     :se "Bowlingbanan har elektroniskt poängräknings system",
     :en "The bowling alley has electronic scoring."}},
   :mobile-orienteering?
   {:name
    {:fi "Mobiilisuunnistusmahdollisuus",
     :se "Mobilorientering möjlig",
     :en "Mobile Orienteering Available"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :track-width-m
   {:name
    {:fi "Radan leveys m", :se "Banans bredd m", :en "Width of track m"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan, pyöräilyradan tms. leveys metreinä",
     :se "Löpbanan, rundbanan el.dyl. bredd i meter",
     :en "Width of the running track, cycling track, etc., in meters"}},
   :ice-climbing?
   {:name      {:fi "Jääkiipeily", :se "Isklättring", :en "Ice climbing"},
    :data-type "boolean",
    :description
    {:fi "Onko jääkiipeily mahdollista kiipeilypaikalla",
     :se "Finns det möjlighet för isklättring vid klättringsplatsen",
     :en "Is ice climbing possible at the climbing site"}},
   :field-length-m
   {:name
    {:fi "Kentän pituus m",
     :se "Planens längd m",
     :en "Length of field"},
    :data-type "numeric",
    :description
    {:fi "Kentän/kenttien pituus mahdollisine turva-alueineen metreinä",
     :se "Planens/planernas längd i meter",
     :en "Length of the field(s) including any safety areas in meters"}},
   :skijump-hill-material
   {:name
    {:fi "Vauhtimäen rakennemateriaali",
     :se "Överbackens konstruktionsmaterial",
     :en "Ski jump hill material"},
    :data-type "string",
    :description
    {:fi "Vauhtimäen rakennemateriaali",
     :se "Överbackens konstruktionsmaterial (backhoppning)",
     :en "Construction material of the inrun"}},
   :carom-tables-count
   {:name
    {:fi "Karapöydät lkm",
     :se "Antal karombord",
     :en "Number of carom tables"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :longest-slope-m
   {:name
    {:fi "Pisin rinne m",
     :se "Längsta slalombacken m",
     :en "Longest slope m"},
    :data-type "numeric",
    :description
    {:fi "Pisimmän rinteen pituus metreinä",
     :se "Längsta slalombackens längd i meter",
     :en "Length of the longest slope in meters"}},
   :circular-lanes-count
   {:name
    {:fi "Kiertävät radat lkm",
     :se "Antalet cirkulerande löpbanor",
     :en "Number of circular lanes"},
    :data-type "numeric",
    :description
    {:fi "Kiertävien juoksuratojen lukumäärä",
     :se "Antalet cirkulerande löpbanor",
     :en "Number of circular running tracks"}},
   :boat-launching-spot?
   {:name
    {:fi "Veneen vesillelaskupaikka",
     :se "Sjösättningsplats för båtar",
     :en "Place for launching a boat"},
    :data-type "boolean",
    :description
    {:fi "Mahdollisuus veneen vesillelaskuun",
     :se "Sjösättningsplats för båtar",
     :en "Possibility for boat launching"}},
   :parkour-hall-equipment-and-structures
   {:name
    {:fi "Parkour-salin varustelu ja rakenteet",
     :se "Utrustning och strukturer i parkourhallen",
     :en "Parkour hall equipment and structures"},
    :data-type "enum-coll",
    :opts
    {"fixed-obstacles"
     {:label
      {:fi "Kiinteät esteet/rakennelmat",
       :en "Fixed obstacles/structures",
       :se "Fasta hinder/strukturer"},
      :description
      {:fi
       "Pysyvästi asennetut esteet tai rakennelmat harjoittelua varten.",
       :en
       "Permanently installed obstacles or structures for training purposes.",
       :se
       "Permanent installerade hinder eller strukturer för träningsändamål."}},
     "movable-obstacles"
     {:label
      {:fi "Liikkuvat esteet/rakennelmat",
       :en "Movable obstacles/structures",
       :se "Flyttbara hinder/strukturer"},
      :description
      {:fi
       "Siirrettävät tai muunneltavat esteet ja rakennelmat harjoittelua varten.",
       :en
       "Movable or adjustable obstacles and structures for training purposes.",
       :se
       "Flyttbara eller justerbara hinder och strukturer för träningsändamål."}},
     "floor-acrobatics-area"
     {:label
      {:fi "Permanto/akrobatiatila",
       :en "Floor/acrobatics area",
       :se "Golv/akrobatikområde"},
      :description
      {:fi
       "Avoin tila lattiaharjoittelua ja akrobaattisia liikkeitä varten.",
       :en "Open space for floor exercises and acrobatic movements.",
       :se "Öppet utrymme för golvövningar och akrobatiska rörelser."}},
     "gym-strength-area"
     {:label
      {:fi "Kuntosali-/voimailutila",
       :en "Gym/strength training area",
       :se "Gym/styrketräningsområde"},
      :description
      {:fi
       "Alue, joka on varustettu kuntosalilaitteilla ja välineillä voimaharjoittelua varten.",
       :en
       "Area equipped with gym machines and equipment for strength training.",
       :se
       "Område utrustat med gymmaskiner och utrustning för styrketräning."}}},
    :description
    {:fi "Valitse parkour-salissa olevat rakenteet tai varusteet",
     :se "Välj de strukturer eller utrustningar som finns i parkourhallen",
     :en "Select the structures or equipment available in the parkour gym"}},
   :ski-track-traditional?
   {:name
    {:fi "Perinteinen latu",
     :se "Skidspår för klassisk stil",
     :en "Traditional ski track"},
    :data-type "boolean",
    :description
    {:fi "Perinteisen tyylin hiihtomahdollisuus/latu-ura",
     :se "Möjlighet att skida klassisk stil",
     :en "Classic style skiing track available"}},
   :altitude-difference
   {:name
    {:fi "Korkeusero m",
     :se "Höjdskillnad m",
     :en "Altitude difference"},
    :data-type "numeric",
    :description
    {:fi "Reitin korkeusero metreinä",
     :se "Ruttens höjdskillnad i meter",
     :en "Elevation difference of the route in meters"}},
   :climbing-wall-height-m
   {:name
    {:fi "Kiipeilyseinän korkeus m",
     :se "Klätterväggens höjd m",
     :en "Climbing wall height"},
    :data-type "numeric",
    :description
    {:fi "Kiipeilyseinän korkeus metreinä (max)",
     :se "Klätterväggens höjd i meter (max)",
     :en "Height of the climbing wall in meters (max)"}},
   :route-width-m
   {:name
    {:fi "Reitin leveys m",
     :se "Ruttens bredd m",
     :en "Route's width m"},
    :data-type "numeric",
    :description
    {:fi "Reitin leveys metreinä", :se "Banans bredd i meter", :en ""}},
   :rapid-canoeing-centre?
   {:name
    {:fi "Koskimelontakeskus",
     :se "Centrum för paddling",
     :en "Rapid canoeing centre"},
    :data-type "boolean",
    :description
    {:fi "Kilpailujen järjestäminen mahdollista.",
     :se "Möjligt att arrangera tävlingar.",
     :en "Competitions possible."}},
   :beach-length-m
   {:name
    {:fi "Rannan pituus m",
     :se "Strandens längd m",
     :en "Length of beach m"},
    :data-type "numeric",
    :description
    {:fi "Hoidetun rannan pituus metreinä",
     :se "Skötta strandens längd i meter",
     :en "Length of the maintained beach in meters"}},
   :match-clock?
   {:name      {:fi "Ottelukello", :se "Matchklocka", :en "Match clock"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla ottelukello",
     :se "Finns det en matchklocka vid idrottsplatsen",
     :en "Is there a match clock at the sports facility"}},
   :sprint-track-length-m
   {:name
    {:fi "Etusuoran pituus m",
     :se "Raksträckans längd (framför läktaren)",
     :en "Length of sprint track"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan etusuoran pituus",
     :se "Längden på löpbanans raksträcka",
     :en "Length of the home straight of the running track"}},
   :inner-lane-length-m
   {:name
    {:fi "Sisäradan pituus m",
     :se "Innerbanans längd m",
     :en "Length of inner lane m"},
    :data-type "numeric",
    :description
    {:fi "Sisäradan pituus kiertävissä radoissa",
     :se "Längden på innerbanan i de cirkulerande banorna",
     :en "Length of the inside lane in circular tracks"}},
   :discus-throw-places
   {:name
    {:fi "Kiekonheittopaikat lkm",
     :se "Diskusplatser st.",
     :en "Number of discus throw places"},
    :data-type "numeric",
    :description
    {:fi "Kiekonheittopaikkojen lukumäärä",
     :se "Antalet diskuskastningsplatser",
     :en "Number of discus throw areas"}},
   :fields-count
   {:name
    {:fi "Kenttien lkm", :se "Antalet planer", :en "Number of fields"},
    :data-type "numeric",
    :description
    {:fi "Montako saman tyypin kenttää liikuntapaikassa on",
     :se "Hur många planer av samma typ har motionsplatsen",
     :en "How many of the same type of fields are there at the sports facility"}},
   :field-1-width-m
   {:name
    {:fi "1. kentän leveys m",
     :se "Första planens bredd m",
     :en "1. field's width m"},
    :data-type "numeric",
    :description
    {:fi "1. kentän leveys metreinä",
     :se "Första planens bredd i meter",
     :en "Width of the first field in meters"}},
   :field-3-width-m
   {:name
    {:fi "3. kentän leveys m",
     :se "Tredje planens bredd m",
     :en "3. field's width m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän leveys metreinä",
     :se "Tredje planens bredd i meter",
     :en "Width of the third field in meters"}},
   :field-2-width-m
   {:name
    {:fi "2. kentän leveys m",
     :se "Andra planens bredd m",
     :en "2. field's width m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän leveys metreinä",
     :se "Andra planens bredd i meter",
     :en "Width of the second field in meters"}},
   :badminton-courts-count
   {:name
    {:fi "Sulkapallokentät lkm",
     :se "Antalet badmintonsplaner",
     :en "Badminton courts pcs"},
    :data-type "numeric",
    :description
    {:fi "Sulkapallokenttien lukumäärä salissa",
     :se "Antalet badmintonsplaner i salen",
     :en "Number of badminton courts in the hall"}},
   :fitness-stairs-length-m
   {:name
    {:fi "Kuntoportaiden pituus m",
     :se "Längden på tränings trapporna m",
     :en "Length of the fitness stairs m"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :free-customer-use?
   {:name
    {:fi "Vapaa asiakaskäyttö",
     :se "Fri kundanvändning",
     :en "Free customer use"},
    :data-type "boolean",
    :description
    {:fi
     "Liikuntapaikka on asiakkaiden käytettävissä esim. kulkukortilla ilman henkilökunnan läsnäoloa. Vapaa asiakaskäyttö voi olla rajattu tiettyihin kellonaikoihin.",
     :se "Idrottsanläggningen är tillgänglig för kunder, t.ex. med ett passerkort, utan personal närvarande. Fri kundanvändning kan vara begränsad till vissa tider.",
     :en "The sports facility is available for customer use, e.g., with an access card, without staff presence. Free customer access may be limited to certain hours."}},
   :hammer-throw-places-count
   {:name
    {:fi "Moukarinheittopaikat lkm",
     :se "Antalet släggkastningsplatser",
     :en "Hammer throw"},
    :data-type "numeric",
    :description
    {:fi "Moukarinheittopaikkojen lukumäärä",
     :se "Antal platser för att släggkastning",
     :en "Number of hammer throw areas"}},
   :may-be-shown-in-harrastuspassi-fi?
   {:name
    {:fi "Saa julkaista Harrastuspassi.fi-sovelluksessa",
     :se "Får publiceras i Harrastuspassi.fi",
     :en "May be shown in Harrastuspassi.fi"},
    :data-type "boolean",
    :description
    {:fi "Kohteen tiedot saa julkaista Harrastuspassi.fi-sovelluksessa",
     :se
     "När du kryssat för rutan ”Kan visas på Harrastuspassi.fi” flyttas uppgifterna om idrottsanläggningen automatisk till Harrastuspassi.fi –applikationen.",
     :en
     "When the option ”May be shown in Harrastuspassi.fi” is ticked, the information regarding the sport facility will be transferred automatically to the Harrastuspassi.fi application."}},
   :pool-width-m
   {:name
    {:fi "1. altaan leveys m",
     :se "Första bassängens bredd m",
     :en "1. pool's width"},
    :data-type "numeric",
    :description
    {:fi "1. altaan/pääaltaan leveys metreinä",
     :se "Första/huvudbassängens bredd i meter",
     :en ""}},
   :pool-min-depth-m
   {:name
    {:fi "1. altaan syvyys min m",
     :se "1a bassängens djup min m",
     :en "1. pool's depth min m"},
    :data-type "numeric",
    :description
    {:fi "1. altaan syvyys matalimmasta päästä metreinä",
     :se "Första bassängens grundaste punkt i meter.",
     :en "The depth of the shallowest end of the pool in meters."}},
   :padel-courts-count
   {:name
    {:fi "Padelkentät lkm",
     :se "Antalet padelbanor",
     :en "Number of padel courts"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :hs-point
   {:name      {:fi "HS-piste", :se "HS-punkt", :en "HS Point"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrimäen HS-piste metreinä",
     :se "HS-punkten i backhoppning i meter",
     :en "Ski jumping hill HS point in "}},
   :ice-rinks-count
   {:name      {:fi "Kaukalot lkm", :se "Antalet rinkar", :en "Ice rinks"},
    :data-type "numeric",
    :description
    {:fi "Kaukaloiden lukumäärä",
     :se "Antalet rinkar (hockey) det finns vid idrottsplatsen",
     :en "The number of rinks."}},
   :field-1-area-m2
   {:name
    {:fi "1. kentän ala m2",
     :se "Första planens areal m2",
     :en "1. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "1. kentän pinta-ala neliömetreinä",
     :se "Första planens areal i kvadratmeter",
     :en "The area of the field in square meters."}},
   :k-point
   {:name      {:fi "K-piste", :se "K-punkt", :en "K point"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrimäen k-piste metreinä",
     :se "Hoppbackens k-punkt i meter",
     :en "The K-point of the ski jumping hill in meters"}},
   :polevault-places-count
   {:name
    {:fi "Seiväshyppypaikat lkm",
     :se "Antalet stavhoppsplatser",
     :en "Pole vault"},
    :data-type "numeric",
    :description
    {:fi "Seiväshyppypaikkojen lukumäärä",
     :se "Antalet stavhoppningsplatser",
     :en "The number of pole vault areas."}},
   :group-exercise-rooms-count
   {:name
    {:fi "Ryhmäliikuntatilat lkm",
     :se "Antalet gruppmotions utrymmen",
     :en "Room for exercise groups"},
    :data-type "numeric",
    :description
    {:fi "Liikuntasalien ja ryhmäliikuntatilojen lukumäärä",
     :se "Antalet gymnastiksalar och gruppmotions utrymmen",
     :en "The number of sports halls and group exercise spaces"}},
   :snowpark-or-street?
   {:name
    {:fi "Parkki", :se "Trick/street pist", :en "Snow park/street"},
    :data-type "boolean",
    :description
    {:fi
     "Onko rinnehiihtokeskuksessa ns. temppurinne, snowpark tai vastaava",
     :se "Har skidcentrumet en trickbana, snowpark eller något liknande",
     :en "Is there a so-called adventure slope, snowpark, or similar area in the ski resort"}},
   :field-2-flexible-rink?
   {:name
    {:fi "2. kenttä: onko joustokaukalo?",
     :se "Fält 2: finns det flexibel rink?",
     :en "Field 2: is there a flexible rink?"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :space-divisible
   {:name
    {:fi "Tila jaettavissa osiin",
     :se "Utrymmet kan delas upp",
     :en "Space can be divided"},
    :helper-text
    {:fi "Moneenko osaan tila on jaettavissa (lkm)",
     :se "Ange antalet delar som utrymmet kan delas in i",
     :en
     "Enter the number of sections into which the space can be divided"},
    :data-type "numeric",
    :description
    {:fi
     "Onko tila jaettavissa osiin esim. jakoseinien tai -verhojen avulla",
     :se
     "Är utrymmet delbart i sektioner, till exempel med skiljeväggar eller gardiner",
     :en
     "Is the space divisible into sections, for example, with partition walls or curtains"}},
   :max-vertical-difference
   {:name
    {:fi "Korkeusero max m",
     :se "Höjdskillnad max m",
     :en "Max vertical difference"},
    :data-type "numeric",
    :description
    {:fi "Suurin korkeusero rinteissä",
     :se "Största höjdskillnaden i slalombackorna",
     :en "The greatest elevation difference in the slopes"}},
   :bowling-lanes-count
   {:name
    {:fi "Keilaradat lkm",
     :se "Antalet bowlingbanor",
     :en "Bowling lanes"},
    :data-type "numeric",
    :description
    {:fi "Keilaratojen lukumäärä", :se "Antalet bowlingbanor", :en ""}},
   :air-gun-shooting?
   {:name
    {:fi "Ilma-aseammunta",
     :se "Luftgevärsskytte",
     :en "Air gun shooting"},
    :data-type "boolean",
    :description
    {:fi "Ilma-aseammuntamahdollisuus",
     :se "Möjlighet för luftgevärsskytte",
     :en "Availability of air gun shooting"}},
   :gymnastic-routines-count
   {:name
    {:fi "Telinevoimistelusarjat lkm",
     :se "Antalet redskapsgymnastikserier",
     :en "Gymnastic routines"},
    :data-type "numeric",
    :description
    {:fi "Telinevoimistelun telinesarjojen lukumäärä",
     :se "Antalet redskap för redskapsgymnastik",
     :en "The number of apparatus for gymnastics routines"}},
   :toilet?
   {:name      {:fi "Yleisö-wc", :se "Allmän toalett", :en "Toilet"},
    :data-type "boolean",
    :description
    {:fi "Onko kohteessa yleiseen käyttöön tarkoitettuja wc-tiloja",
     :se "Är allmänna toaletten i användning",
     :en "Are there public restroom facilities available at the location"}},
   :gymnastics-space?
   {:name
    {:fi "Telinevoimistelutila",
     :se "Utrymme för redskapsgymnastik",
     :en "Space for gymnastics"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntasalissa myös telinevoimistelutila",
     :se "Har motionssalen också område/utrymme för redskapsgymnastik",
     :en "Is there also a gymnastics area in the sports hall"}},
   :snooker-tables-count
   {:name
    {:fi "Snookerpöydät lkm",
     :se "Antal snookerbord",
     :en "Number of snooker tables"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :show-jumping?
   {:name      {:fi "Esteratsastus", :se "Banhoppning", :en "Show jumping"},
    :data-type "boolean",
    :description
    {:fi
     "Onko ratsastuskentällä/maneesissa esteratsastukseen soveltuva varustus",
     :se "Har ridplanen/ridhuset utrustning för banhoppning",
     :en "Is there equipment suitable for show jumping in the riding arena/indoor arena"}},
   :shower?
   {:name      {:fi "Suihku", :se "Dusch", :en "Shower"},
    :data-type "boolean",
    :description
    {:fi "Onko suihku käytettävissä",
     :se "Är duschen i användning",
     :en "Is there a shower available"}},
   :rest-places-count
   {:name
    {:fi "Taukopaikat lkm",
     :se "Antalet viloplatser",
     :en "Rest places"},
    :data-type "numeric",
    :description
    {:fi "Montako taukopaikkaa reitin varrella on",
     :se "Hur många viloplatser finns det längs med rutten",
     :en "How many rest areas are there along the route"}},
   :changing-rooms?
   {:name      {:fi "Pukukopit", :se "Omklädningsrum", :en "Changing rooms"},
    :data-type "boolean",
    :description
    {:fi "Onko pukukoppeja", :se "Finns det omklädningsrum", :en ""}},
   :pistol-shooting?
   {:name
    {:fi "Pistooliammunta", :se "Pistolskytte", :en "Pistol shooting"},
    :data-type "boolean",
    :description
    {:fi "Pistooliammuntamahdollisuus",
     :se "Möjlighet för pistolskytte",
     :en "Availability of pistol shooting"}},
   :halfpipe-count
   {:name
    {:fi "Halfpipe lkm", :se "Antal halfpipe", :en "Halfpipe count"},
    :data-type "numeric",
    :description
    {:fi "Halfpipe, superpipe lukumäärät",
     :se "Antal halfpipe",
     :en "The number of halfpipes and superpipes"}},
   :shooting-positions-count
   {:name
    {:fi "Ampumapaikat lkm",
     :se "Antalet skytteplatser",
     :en "Shooting positions"},
    :data-type "numeric",
    :description
    {:fi "Montako ampumapaikkaa liikuntareitin varrella on",
     :se "Hur många skytteplatser finns det längs motionsrutten",
     :en "How many shooting spots are there along the exercise route"}},
   :running-track-surface-material
   {:name
    {:fi "Juoksuradan pintamateriaali",
     :se "Löpbanans ytmaterial",
     :en "Surface material for running track"},
    :data-type "string",
    :description
    {:fi "Juoksuradan pintamateriaali/päällyste",
     :se "Löpbanans ytmaterial/pålägg",
     :en "The surface material/overlay of the running track"}},
   :boating-service-class
   {:name
    {:fi "Venesatama- tai laituriluokka",
     :se "Båthamn eller bryggklass",
     :en "Boat harbor or pier class"},
    :description
    {:fi
     "https://ava.vaylapilvi.fi/ava/Julkaisut/MKL/mkl_2008-1_venesatamien_luokitus.pdf",
     :se
     "https://ava.vaylapilvi.fi/ava/Julkaisut/MKL/mkl_2008-1_venesatamien_luokitus.pdf",
     :en
     "https://ava.vaylapilvi.fi/ava/Julkaisut/MKL/mkl_2008-1_venesatamien_luokitus.pdf"},
    :data-type "enum",
    :opts
    {"home-harbor"
     {:label {:fi "Kotisatama", :en "Home harbor", :se "Hemmahamn"},
      :description
      {:fi
       "Satama, jossa veneet pääasiallisesti säilytetään veneilykauden aikana ja jossa veneen omistaja joko omistaa tai hallitsee venepaikan. Satamat ovat yleensä kunnallisia, kaupallisia tai veneseurojen ylläpitämiä satamia.",
       :en
       "A harbor where boats are mainly stored during the boating season and where the boat owner either owns or controls the berth. Harbors are usually municipal, commercial, or maintained by boating clubs.",
       :se
       "En hamn där båtar huvudsakligen förvaras under båtsäsongen och där båtägaren antingen äger eller kontrollerar båtplatsen. Hamnarna är vanligtvis kommunala, kommersiella eller underhålls av båtklubbar."}},
     "visiting-harbor"
     {:label
      {:fi "Vierassatama (Palvelusatama, Vieraslaituri, Retkisatama)",
       :en "Visiting harbor",
       :se "Besökshamn"},
      :description
      {:fi
       "Satama, jossa veneretken tai matkapurjehduksen aikana voi käydä kaupassa, asioimassa, lepäämässä, yöpymässä tai veneen huollossa.",
       :en
       "A harbor where during a boating trip or sailing voyage you can go shopping, run errands, rest, stay overnight, or service the boat.",
       :se
       "En hamn där du under en båttur eller seglingsresa kan handla, göra ärenden, vila, övernatta eller serva båten."}},
     "safety-harbor"
     {:label
      {:fi "Turvasatama (Suojasatama, Hätäsatama)",
       :en "Safety harbor",
       :se "Säkerhetshamn"},
      :description
      {:fi
       "Satama, josta voidaan hakea suojaa tai saada ensiapua tai korjausapua.",
       :en
       "A harbor where you can seek shelter or get first aid or repair assistance.",
       :se
       "En hamn där man kan söka skydd eller få första hjälp eller reparationshjälp."}},
     "canoe-pier"
     {:label {:fi "Melontalaituri", :en "Canoe pier", :se "Kanotbrygga"},
      :description
      {:fi "Melontaan tarkoitettu laituri.",
       :en "Pier intended for canoeing.",
       :se "Brygga avsedd för kanotpaddling."}},
     "no-class"
     {:label
      {:fi
       "Kohde ei täytä minkään venesatama- tai laituriluokan vaatimuksia (esim. rantautumispaikka)",
       :en
       "The target does not meet the requirements of any marina or dock class (e.g., landing place)",
       :se
       "Objektet uppfyller inte kraven för någon småbåtshamn- eller bryggklass (t.ex. landningsplats)"},
      :description
      {:fi
       "Kohde ei täytä minkään venesatama- tai laituriluokan vaatimuksia (esim. rantautumispaikka).",
       :en
       "The site does not meet the requirements of any boat harbor or pier class (e.g., landing place).",
       :se
       "Platsen uppfyller inte kraven för någon båthamn eller bryggklass (t.ex. landningsplats)."}},
     "no-information"
     {:label
      {:fi "Ei tietoa",
       :se "Ingen information",
       :en "No information"}}}},
   :tatamis-count
   {:name
    {:fi "Tatamit ja mattoalueet lkm",
     :se "Antal tatami- och mattområden",
     :en "Tatamis and mat areas"},
    :data-type "numeric",
    :description
    {:fi "Tatamien ja mattoalueiden lukumäärä",
     :se "Antal tatami- och mattområden",
     :en "Number of tatami and mat areas"}},
   :lit-route-length-km
   {:name
    {:fi "Valaistua reittiä km",
     :se "Belyst rutt km",
     :en "Lit route's length km"},
    :data-type "numeric",
    :description
    {:fi "Montako kilometriä reitistä on valaistua",
     :se "Hur många km av rutten är uppbelyst",
     :en "How many kilometers of the route are illuminated"}},
   :area-m2
   {:name
    {:fi "Liikuntapinta-ala m2",
     :se "Areal m2",
     :en "Area in square meters"},
    :data-type "numeric",
    :description
    {:fi "Liikuntapaikan liikuntapinta-ala, neliömetreinä",
     :se "Idrottsplatsens areal i kvadratmeter",
     :en "The sports area of the facility in square meters"}},
   :field-width-m
   {:name
    {:fi "Kentän leveys m", :se "Planens bredd m", :en "Width of field"},
    :data-type "numeric",
    :description
    {:fi "Kentän/kenttien leveys mahdollisine turva-alueineen metreinä",
     :se "Planens/planernas bredd i meter",
     :en "The width of the field(s), including any safety zones, in meters"}},
   :cosmic-bowling?
   {:name      {:fi "Hohtokeilaus", :se "Discobowling", :en "Cosmic bowling"},
    :data-type "boolean",
    :description
    {:fi "Onko keilaradalla hohtokeilausmahdollisuus",
     :se "Har bowlingsbanan möjlighet för discobowling",
     :en "Is there an option for glow bowling at the bowling alley"}},
   :travel-modes
   {:name      {:fi "Kulkutavat", :se "Resesätt", :en "Travel Modes"},
    :data-type "enum-coll",
    :opts
    {"by-foot"
     {:label {:fi "Jalan", :en "On Foot", :se "Till fots"},
      :description
      {:fi "Liikkuminen jalkaisin",
       :en "Traveling on foot",
       :se "Att resa till fots"}},
     "snow-shoes"
     {:label
      {:fi "Lumikengillä", :en "With Snowshoes", :se "Med snöskor"},
      :description
      {:fi "Liikkuminen lumikengillä",
       :en "Traveling with snowshoes",
       :se "Att resa med snöskor"}},
     "fat-bike"
     {:label
      {:fi "Läskipyörällä", :en "With Fat Bike", :se "Med fatbike"},
      :description
      {:fi "Liikkuminen läskipyörällä",
       :en "Traveling with a fat bike",
       :se "Att resa med fatbike"}}},
    :description
    {:fi "Lisää reitille soveltuvat kulkutavat",
     :se "Lägg till lämpliga resesätt för rutten",
     :en "Add suitable travel modes for the route"}},
   :wrestling-mats-count
   {:name
    {:fi "Painimatot lkm",
     :se "Brottarmattor st.",
     :en "Wrestling mats pcs"},
    :data-type "numeric",
    :description
    {:fi "Painimattojen lukumäärä", :se "Antal brottarmattor", :en ""}},
   :lighting-info
   {:name
    {:fi "Valaistuksen lisätieto",
     :se "Ytterligare information om belysningen",
     :en "Additional information about the lighting"},
    :data-type "string",
    :description
    {:fi "Esim. lux-määrä tai muu tarkentava tieto",
     :se "T.ex. lux-mängd eller annan förtydligande information",
     :en "E.g. lux amount or other specifying information"}},
   :eu-beach?
   {:name      {:fi "EU-uimaranta", :se "EU-badstrand", :en "EU beach"},
    :data-type "boolean",
    :description
    {:fi
     "Uimaranta, joka täyttää EU-kriteerit uimaveden laadusta ja valvonnasta",
     :se
     "Badstrand, som fyller EU-kriterierna med kvaliteten och övervakningen av badvattnet",
     :en "A swimming beach that meets EU criteria for bathing water quality and monitoring"}},
   :rifle-shooting?
   {:name
    {:fi "Kivääriammunta", :se "Gevärbana", :en "Rifle shooting places"},
    :data-type "boolean",
    :description
    {:fi "Kivääriammuntamahdollisuus",
     :se "Möjlighet för gevärskytte",
     :en "Availability of rifle shooting"}},
   :swimming-pool-count
   {:name
    {:fi "Altaiden lukumäärä",
     :se "Antalet simbassänger",
     :en "Number of swimming pools"},
    :data-type "numeric",
    :description
    {:fi
     "Altaiden lukumäärä yhteensä. Syötä tieto tai laske automaattisesti.",
     :se "Antalet simbassänger, också terapi bassänger",
     :en "Total number of pools. Enter the information or calculate automatically."}},
   :pool-water-area-m2
   {:name
    {:fi "Vesipinta-ala m2",
     :se "Bassängernas vatten areal",
     :en "Pool water area in sq. m"},
    :data-type "numeric",
    :description
    {:fi "Asiakaskäytössä oleva vesipinta-ala yhteensä.",
     :se "Den totala vattenytan tillgänglig för kunder.",
     :en "The total water surface area available for customers."}},
   :highest-obstacle-m
   {:name
    {:fi "Korkeimman esteen korkeus m",
     :se "Höjden på den högsta hindret m",
     :en "The height of the highest obstacle m"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :year-round-use?
   {:name
    {:fi "Ympärivuotinen käyttö",
     :se "Året runt användning",
     :en "Year-round Use"},
    :data-type "boolean",
    :description
    {:fi "Kohde on ympärivuotisessa käytössä",
     :se "Platsen är i användning året runt",
     :en "The location is in use year-round"}},
   :curling-lanes-count
   {:name
    {:fi "Curling-ratojen lukumäärä",
     :se "Antal curlingbanor",
     :en "Count of curling lanes"},
    :data-type "numeric",
    :description
    {:fi "Curling-ratojen lukumäärä",
     :se "Antal curlingbanor",
     :en "Number of curling lanes"}},
   :climbing-wall-width-m
   {:name
    {:fi "Kiipeilyseinän leveys m",
     :se "Klätterväggens bredd m",
     :en "Climbing wall width"},
    :data-type "numeric",
    :description
    {:fi "Kiipeilyseinän leveys metreinä sivusuunnassa",
     :se "Bredden på klätterväggen i meter i sidled",
     :en "Width of the climbing wall in meters laterally"}},
   :area-km2
   {:name
    {:fi "Pinta-ala km2",
     :se "Areal km2",
     :en "Area in square kilometres"},
    :data-type "numeric",
    :description
    {:fi "Alueen pinta-ala neliökilometreinä",
     :se "Områdets areal i kvadratkilometer",
     :en "Area of the region in square kilometers"}},
   :scoreboard?
   {:name      {:fi "Tulostaulu", :se "Resultattavla", :en "Score board"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla tulostaulu/sähköinen tulostaulu",
     :se "Finns det en resultattavla/elektronisk resultattavla på idrottsplatsen",
     :en "Is there a scoreboard/electronic scoreboard at the sports facility"}},
   :futsal-fields-count
   {:name
    {:fi "Futsal-kentät lkm",
     :se "Antalet Futsal-planer",
     :en "Number of futsal fields"},
    :data-type "numeric",
    :description
    {:fi "Futsal-kenttien lukumäärä",
     :se "Antal futsalplaner",
     :en "Number of futsal courts"}},
   :ski-orienteering?
   {:name
    {:fi "Hiihtosuunnistus mahdollista",
     :se "Skidorientering möjlig",
     :en "Ski Orienteering Possible"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :training-wall?
   {:name
    {:fi "Lyöntiseinä",
     :se "Vägg att träna på tennis",
     :en "Training wall for tennis"},
    :data-type "boolean",
    :description
    {:fi "Onko tenniskentällä lyöntiseinä",
     :se "Finns det en träningsvägg vid tennisplanen",
     :en "Is there a hitting wall on the tennis court"}},
   :shotput-count
   {:name
    {:fi "Kuulantyöntöpaikat lkm",
     :se "Antalet kulstötningsplatser",
     :en "Shot put"},
    :data-type "numeric",
    :description
    {:fi "Kuulantyöntöpaikkojen lukumäärä",
     :se "Antal kulstötningsplatser",
     :en "Number of shot put areas"}},
   :active-space-length-m
   {:name
    {:fi "Liikuntakäytössä olevan tilan pituus m",
     :se "Längd på aktivt utrymme m",
     :en "Length of active space m"},
    :data-type "numeric",
    :description
    {:fi "Liikuntakäytössä olevan tilan pituus (m)", :se "", :en ""}},
   :longjump-places-count
   {:name
    {:fi "Pituus- ja kolmiloikkapaikat lkm",
     :se "Antalet längd- och trestegshopp platser",
     :en "Long jump"},
    :data-type "numeric",
    :description
    {:fi "Pituus- ja kolmiloikkapaikkojen lukumäärä",
     :se "Antal längd- och trestegshoppsplatser",
     :en "Number of long jump and triple jump areas"}},
   :football-fields-count
   {:name
    {:fi "Jalkapallokentät lkm",
     :se "Antalet fotbollsplaner",
     :en "Football fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako jalkapallokenttää mahtuu saliin/halliin",
     :se "Hur många fotbollsplaner ryms i salen/hallen",
     :en "How many football (soccer) fields can fit in the hall"}},
   :floorball-fields-count
   {:name
    {:fi "Salibandykentät lkm",
     :se "Antalet innebandyplaner",
     :en "Floor ball field"},
    :data-type "numeric",
    :description
    {:fi "Salibandykenttien lukumäärä",
     :se "Antalet innebandyplaner",
     :en "Number of floorball courts"}},
   :auxiliary-training-area?
   {:name
    {:fi "Oheisharjoittelutila",
     :se "Kompletterande träningsområde",
     :en "Auxiliary training area"},
    :data-type "boolean",
    :description
    {:fi
     "Onko kohteessa oheisharjoitteluun soveltuva tila? Oheisharjoittelutila on liikuntapaikan käyttäjille tarkoitettu erillinen pienliikuntatila, jota voidaan käyttää esim. lämmittelyyn tai oheisharjoitteluun. Tilan koko, varustelu ja pintamateriaali ovat oheisharjoitteluun soveltuvia.",
     :se "Finns det en lämplig plats för kompletterande träning på platsen? En kompletterande träningsyta är en separat mindre träningsyta avsedd för användare av idrottsanläggningen, som kan användas till exempel för uppvärmning eller kompletterande träning. Utrymmets storlek, utrustning och ytskikt är lämpliga för kompletterande träning.",
     :en "Is there a suitable area for supplementary training at the facility? A supplementary training area is a separate small exercise space intended for users of the sports facility, which can be used, for example, for warm-ups or supplementary training. The size, equipment, and surface material of the area are suitable for supplementary training."}},
   :equipment-rental?
   {:name
    {:fi "Välinevuokraus",
     :se
     "Uthyrning av idrottsutrustning t.ex. slalom,skidning, terräncyklar osv.",
     :en "Equipment rental"},
    :data-type "boolean",
    :description
    {:fi "Välinevuokraus mahdollista",
     :se "Möjlighet att hyra utrustning",
     :en "Equipment rental available"}},
   :slopes-count
   {:name
    {:fi "Rinteiden lkm",
     :se "Antalet slalombackar",
     :en "Number of slopes"},
    :data-type "numeric",
    :description
    {:fi "Rinteiden määrä yhteensä",
     :se "Totala antalet slalombackar",
     :en "Total number of slopes"}},
   :pool-length-m
   {:name
    {:fi "1. altaan pituus m",
     :se "Första bassängens längd",
     :en "1. pool's length"},
    :data-type "numeric",
    :description
    {:fi "1. altaan/pääaltaan pituus metreinä",
     :se "Första/huvudbassängens längd i meter",
     :en "The length of the pool/main pool in meters."}},
   :other-pools-count
   {:name
    {:fi "Muut altaat lkm",
     :se "Antalet andra bassänger",
     :en "Number of other pools"},
    :data-type "numeric",
    :description
    {:fi "Porealtaiden, kylmäaltaiden yms lukumäärä yhteensä",
     :se "Antalet övriga bassänger såsom bubbelpool, kallbassäng o.dyl.",
     :en "Total number of hot tubs, cold pools, etc."}},
   :shortest-slope-m
   {:name
    {:fi "Lyhin rinne m",
     :se "Kortaste slalombacken m",
     :en "Shortest slope m"},
    :data-type "numeric",
    :description
    {:fi "Lyhimmän rinteen pituus metreinä",
     :se "Kortaste skidbacken i meter",
     :en "Length of the shortest slope in meters"}},
   :pool-tables-count
   {:name
    {:fi "Poolpöydät lkm",
     :se "Antal poolbord",
     :en "Number of pool tables"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :squash-courts-count
   {:name
    {:fi "Squash-kentät lkm",
     :se "Antalet squashplaner",
     :en "Squash courts"},
    :data-type "numeric",
    :description
    {:fi "Squash-kenttien lukumäärä",
     :se "Antalet squash-planer",
     :en "The number of squash courts"}},
   :changing-rooms-m2
   {:name
    {:fi "Pukukoppien kokonaispinta-ala m²",
     :se "Omklädningsrummens totala yta m²",
     :en "Total area of the changing rooms in m²"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :ringette-boundary-markings?
   {:name
    {:fi "Ringeten rajamerkinnät",
     :se "Gränsmarkeringar för ringette",
     :en "Ringette boundary markings"},
    :data-type "boolean",
    :description
    {:fi "Onko kaukaloissa ringeten rajamerkinnät?", :se "", :en ""}},
   :boxing-rings-count
   {:name
    {:fi "Nyrkkeilykehät lkm",
     :se "Antalet boxningsringar",
     :en "Boxing rings pcs"},
    :data-type "numeric",
    :description
    {:fi "Nyrkkeilykehien lukumäärä",
     :se "Antalet boxningsringar",
     :en "The number of boxing rings"}},
   :ice-reduction?
   {:name      {:fi "Jäätymisenesto", :se "Frostskydd", :en "Ice reduction"},
    :data-type "boolean",
    :description
    {:fi "Jäätymisenestojärjestelmä talviuintipaikassa",
     :se "Har vinterbadplatsen mekanism för frostskydd",
     :en "Anti-freeze system at the winter swimming location"}},
   :activity-service-company?
   {:name
    {:fi "Ohjelmapalveluyritys", :se "", :en "Activity service company"},
    :data-type "boolean",
    :description
    {:fi "Toimiiko kohteessa ohjelmapalveluyritys.", :se "", :en ""}},
   :field-1-flexible-rink?
   {:name
    {:fi "1. kenttä: onko joustokaukalo?",
     :se "Fält 1: finns det flexibel rink?",
     :en "Field 1: is there a flexible rink?"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :fencing-bases-count
   {:name
    {:fi "Miekkailualustat lkm",
     :se "Antalet fäktnings underlag",
     :en "Fencing bases"},
    :data-type "numeric",
    :description
    {:fi "Miekkailualustojen lukumäärä",
     :se "Antal underlägg avsedda för fäktning",
     :en "The number of fencing mats"}},
   :weight-lifting-spots-count
   {:name
    {:fi "Painonnostopaikat/nostolavat lkm",
     :se "Antal tyngdlyftningsplatser/lyftplattformar",
     :en "Number of weightlifting areas/platforms"},
    :data-type "numeric",
    :description
    {:fi
     "Painnostopaikkojen lukumäärä. Huom. nostolava on painonnostopaikka, joka kestää painojen pudottamisen",
     :se
     "Antal tyngdlyftningsplatser. Obs: En lyftplattform är en tyngdlyftningsplats som tål att vikter tappas.",
     :en
     "Number of weightlifting areas. Note: A lifting platform is a weightlifting area that can withstand the dropping of weights."}},
   :landing-places-count
   {:name
    {:fi "Alastulomontut lkm",
     :se "Antalet landningsgropar",
     :en "Landing places"},
    :data-type "numeric",
    :description
    {:fi "Alastulomonttujen lukumäärä",
     :se "Antalet landnigsgropar",
     :en "The number of landing pits"}},
   :bike-orienteering?
   {:name
    {:fi "Pyöräsuunnistus mahdollista",
     :se "Cykelorientering möjlig",
     :en "Bike Orienteering Possible"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :toboggan-run?
   {:name      {:fi "Ohjaskelkkamäki", :se "Rodelbana", :en "Toboggan run"},
    :data-type "boolean",
    :description
    {:fi "Onko rinnehiihtokeskuksessa ohjaskelkkamäki",
     :se "Har skidcentrumet en rodelbana",
     :en "Is there a guided sledding slope at the ski resort?"}},
   :sauna?
   {:name      {:fi "Sauna", :se "Bastu", :en "Sauna"},
    :data-type "boolean",
    :description
    {:fi "Onko sauna käytettävissä",
     :se "Är bastun i användning",
     :en "Is there a sauna available"}},
   :jumps-count
   {:name
    {:fi "Hyppyrien lkm",
     :se "Antalet hoppbackar",
     :en "Number of jumps"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrien lukumäärä", :se "Antalet hoppbackar", :en ""}},
   :table-tennis-count
   {:name
    {:fi "Pöytätennispöytien lkm",
     :se "Antalet bord för bordtennis",
     :en "Table tennis table count"},
    :data-type "numeric",
    :description
    {:fi "Pingis-/pöytätennispöytien lukumäärä",
     :se "Antal bordtennisbord (pingisbord)",
     :en "The number of table tennis tables"}},
   :pool-max-depth-m
   {:name
    {:fi "1. altaan syvyys max m",
     :se "Första bassängens max djup m",
     :en "1. pool's depth max m"},
    :data-type "numeric",
    :description
    {:fi "1. altaan syvyys syvimmästä päästä metreinä",
     :se "Första bassängens djupaste punkt i meter",
     :en "The depth of the deepest end of the pool in meter"}},
   :loudspeakers?
   {:name      {:fi "Äänentoisto", :se "Ljudåtergivning", :en "Loudspeakers"},
    :data-type "boolean",
    :description
    {:fi
     "Onko liikuntapaikalla välineistö ja valmius kenttäkuulutuksiin",
     :se
     "Har motionsplatsen utrustning och färdighet till att göra utrop",
     :en "Does the sports facility have equipment and readiness for making announcements"}},
   :customer-service-point?
   {:name
    {:fi "Myynti- tai asiakaspalvelupiste",
     :en "Sales or customer service point",
     :se "Försäljnings- eller kundservicepunkt"},
    :description
    {:fi "Liikuntapaikalla on pysyvä asiakaspalvelupiste tai myyntipiste, josta on saatavilla asiakaspalvelua. Myynti- tai asiakaspalvelupiste voi olla rajoitetusti auki liikuntapaikan käyttöaikojen puitteissa."
     #_"Liikuntapaikalla on pysyvä myynti- tai asiakaspalvelupiste, josta on saatavissa asiakaspalvelua. Myynti- tai asiakaspalvelupiste voi olla rajoitetusti auki liikuntapaikan käyttöaikojen puitteissa.",
     :se
     "Det finns en permanent försäljnings- eller kundservicestation på idrottsanläggningen där kundservice är tillgänglig. Försäljnings- eller kundservicestationen kan ha begränsade öppettider inom idrottsanläggningens användningstider.",
     :en
     "There is a permanent sales or customer service point at the sports facility, where customer service is available. The sales or customer service point may have limited opening hours within the usage hours of the sports facility."},
    :data-type "boolean"},
   :shotgun-shooting?
   {:name
    {:fi "Haulikkoammunta",
     :se "Hagelgevärsskytte",
     :en "Shotgun shooting"},
    :data-type "boolean",
    :description
    {:fi "Haulikkoammuntamahdollisuus",
     :se "Möjlighet för hagelskytte",
     :en "Availability of shotgun shooting"}},
   :water-point
   {:name        {:fi "Vesipiste", :en "Water point", :se "Vattenpunkt"},
    :description {:fi "", :se "", :en ""},
    :data-type   "enum",
    :opts
    {"year-round"
     {:label {:fi "Ympärivuotinen", :se "Året runt", :en "Year-round"}},
     "seasonal"
     {:label
      {:fi "Kausittaisesti käytössä",
       :se "Säsongsvis i bruk",
       :en "Seasonally in use"}}}},
   :lit-slopes-count
   {:name
    {:fi "Valaistut rinteet lkm",
     :se "Antalet belysta slalombackar",
     :en "Number of lit slopes"},
    :data-type "numeric",
    :description
    {:fi "Montako rinnettä on valaistu",
     :se "Hur många belysta slalombackar finns det",
     :en "How many slopes are illuminated"}},
   :green?
   {:name      {:fi "Puttausviheriö", :se "Puttnings green", :en "Green"},
    :data-type "boolean",
    :description
    {:fi "Onko golfkentällä puttausviheriö",
     :se "Finns det en puttnings green vid golfbanan",
     :en "Is there a putting green on the golf course"}},
   :free-rifle-shooting?
   {:name
    {:fi "Pienoiskivääriammunta",
     :se "Miniatyrgevärsskytte",
     :en "Free rifle shooting"},
    :data-type "boolean",
    :description
    {:fi "Pienoiskivääriammuntamahdollisuus",
     :se "Möjlighet förminiatyrgevärskytte",
     :en "Availability of small bore rifle shooting"}},
   :winter-usage?
   {:name      {:fi "Talvikäyttö", :se "Vinterbruk", :en "Winter usage"},
    :data-type "boolean",
    :description
    {:fi "Liikuntapaikka on käytössä myös talvisin",
     :se "Motionsplatsen är i bruk under vintern",
     :en ""}},
   :ligthing?
   {:name      {:fi "Valaistus", :se "Belysning", :en "Lighting"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikka valaistu",
     :se "Är idrottsplatsen uppbelyst",
     :en "Is the sports facility illuminated"}},
   :field-3-area-m2
   {:name
    {:fi "3. kentän ala m2",
     :se "Tredje planens areal m2",
     :en "3. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän pinta-ala neliömetreinä",
     :se "Tredje planens areal i kvadratmeter",
     :en "The area of the field in square meters"}},
   :accessibility-info
   {:name
    {:fi "Linkki esteettömyystietoon",
     :se "Länk till tillgänglighetsinformation",
     :en "Link to accessibility information"},
    :data-type "string",
    :description
    {:fi
     "Syötä linkki verkkosivulle, jossa on kuvattu kohteen esteettömyyteen liittyvät tiedot",
     :se
     "Ange länken till webbplatsen där information om objektets tillgänglighet beskrivs.",
     :en
     "Enter the link to the website where the accessibility information of the location is described."}},
   :covered-stand-person-count
   {:name
    {:fi "Katettua katsomoa hlö",
     :se "Takbeläggda läktarens person mängd",
     :en "Stand with roof"},
    :data-type "numeric",
    :description
    {:fi "Katetun katsomon henkilömäärä",
     :se "Hur mycket av läktaren är täckt med tak, antalet personer",
     :en "The seating capacity of the covered grandstand"}},
   :playground?
   {:name      {:fi "Leikkipuisto", :se "Lekpark", :en "Playground"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikan yhteydessä leikkipuisto",
     :se "Finns det en lekpark i samband med idrottsplatsen",
     :en "Is there a playground associated with the sports facility"}},
   :handball-fields-count
   {:name
    {:fi "Käsipallokentät lkm",
     :se "Antalet handbollsplaner",
     :en "Handball fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Käsipallokenttien lukumäärä salissa",
     :se "Antalet handbollsplaner som ryms i salen/hallen",
     :en "The number of handball courts in the hall"}},
   :p-point
   {:name      {:fi "P-piste", :se "P-punkt", :en "P point"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrimäen P-piste metreinä",
     :se "Hoppbackens P-punkt i meter",
     :en "The P-point of the ski jumping hill in meters"}},
   :inruns-material
   {:name
    {:fi "Vauhtimäen latumateriaali",
     :se "Överbackens spårmaterial",
     :en "Inrun's material"},
    :data-type "string",
    :description
    {:fi "Hyppyrimäen vauhtimäen materiaali",
     :se "Hoppbackens spårmaterial vid överbacken",
     :en "The material of the take-off ramp of the ski jumping hill"}},
   :pyramid-tables-count
   {:name
    {:fi "Pyramidipöydät lkm",
     :se "Antal pyramidbord",
     :en "Number of pyramid tables"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :basketball-field-type
   {:name
    {:fi "Koripallokentän tyyppi",
     :se "Korgbollsplanens typ",
     :en "Type of basketball field"},
    :data-type "string",
    :description
    {:fi
     "Onko liikuntapaikka normaali koripallokenttä, minikoripallokenttä vai yhden korin koripallokenttä",
     :se
     "Har idrottsplaten en normal korgbollsplan, mini-korgbollsplan eller bara en korg",
     :en "Is the sports facility a standard basketball court, a mini basketball court, or a one-basket basketball court"}},
   :kaisa-tables-count
   {:name
    {:fi "Kaisapöydät lkm",
     :se "Antal kaisabord",
     :en "Number of kaisa tables"},
    :data-type   "numeric",
    :description {:fi "", :se "", :en ""}},
   :volleyball-fields-count
   {:name
    {:fi "Lentopallokentät lkm",
     :se "Antalet volleybollplaner",
     :en "Volleyball field"},
    :data-type "numeric",
    :description
    {:fi "Lentopallokenttien lukumäärä",
     :se "Antalet volleybollplaner",
     :en "The number of volleyball courts"}},
   :boat-places-count
   {:name
    {:fi "Venepaikat lkm", :se "Antalet båtplats", :en "Boat places"},
    :data-type "numeric",
    :description
    {:fi "Venepaikkojen lukumäärä", :se "Antalet båtplatser", :en ""}},
   :pool-temperature-c
   {:name
    {:fi "1. altaan lämpö c",
     :se "Första bassängens temperatur c",
     :en "1. pool's temperature c"},
    :data-type "numeric",
    :description
    {:fi "1. altaan veden lämpötila celsiusasteina",
     :se "Första bassängens vatten temperatur i celcius",
     :en "The water temperature of the pool in degrees Celsius"}},
   :climbing-wall?
   {:name      {:fi "Kiipeilyseinä", :se "Klättervägg", :en "Climbing wall"},
    :data-type "boolean",
    :description
    {:fi "Onko kohteessa kiipeilyseinä",
     :se "Finns det en klättervägg på platsen",
     :en "Is there a climbing wall at the location"}},
   :ski-track-freestyle?
   {:name
    {:fi "Luistelu-ura", :se "Fristils spår", :en "Freestyle ski track"},
    :data-type "boolean",
    :description
    {:fi "Vapaan tyylin latu-ura/luistelu-ura",
     :se "Skidspår för fristil",
     :en "Free style track/skating track"}},
   :spinning-hall?
   {:name      {:fi "Spinning-sali", :se "Spinning sal", :en "Spinning hall"},
    :data-type "boolean",
    :description
    {:fi "Salissa spinning-varustus",
     :se "Salen har spinning utrustning",
     :en "Spinning equipment in the hall"}},
   :other-platforms?
   {:name
    {:fi "Muut hyppytelineet",
     :se "Andra hoppställningar",
     :en "Other platforms"},
    :data-type "boolean",
    :description
    {:fi "Uimahyppytelineet rannalla",
     :se "Hopptornen vid stranden",
     :en "Diving boards at the beach"}},
   :school-use?
   {:name
    {:fi "Koululiikuntapaikka",
     :se "Skolidrottsplats",
     :en "Sport facility in school use"},
    :data-type "boolean",
    :description
    {:fi "Liikuntapaikkaa käytetään koulujen liikuntatunneilla",
     :se "Idrottsplatsen används under skolornas gymnastiktimmar",
     :en "The sports facility is used for school physical education classes"}},
   :highjump-places-count
   {:name
    {:fi "Korkeushyppypaikat lkm",
     :se "Antalet höjdhopps platser",
     :en "High jump"},
    :data-type "numeric",
    :description
    {:fi "Korkeushppypaikkojen lukumäärä",
     :se "Antalet höjdhoppsplatser",
     :en "The number of high jump areas"}},
   :light-roof?
   {:name
    {:fi "Kevytkate", :se "Lättvikts takläggning", :en "Light roof"},
    :data-type "boolean",
    :description
    {:fi "Kentälle voidaan asentaa kevytkate tai muu tilapäinen katos",
     :se
     "På planen kan installeras en lättvikts takläggning eller något annat tillfälligt tak",
     :en "A lightweight cover or another temporary canopy can be installed on the field"}},
   :route-length-km
   {:name
    {:fi "Reitin pituus km",
     :se "Ruttens längd km",
     :en "Route's length km"},
    :data-type "numeric",
    :description
    {:fi "Reitin pituus kilometreinä",
     :se "Ruttens längd i kilometer",
     :en "The length of the route in kilometers"}},
   :field-3-flexible-rink?
   {:name
    {:fi "3. kenttä: onko joustokaukalo?",
     :se "Fält 3: finns det flexibel rink?",
     :en "Field 3: is there a flexible rink?"},
    :data-type   "boolean",
    :description {:fi "", :se "", :en ""}},
   :exercise-machines-count
   {:name
    {:fi "Kuntoilulaitteet lkm",
     :se "Antalet gym apparater",
     :en "Number of exercise machines"},
    :data-type "numeric",
    :description
    {:fi "Kuntoilulaitteiden lukumäärä",
     :se "Antalet gym apparater",
     :en "The number of exercise machines"}},
   :track-type
   {:name        {:fi "Ratatyyppi", :se "Typ av bana", :en "Type of track"},
    :data-type   "string",
    :description {:fi "Radan tyyppi", :se "Banans typ", :en "The type of the track"}},
   :training-spot-surface-material
   {:name
    {:fi "Suorituspaikan pintamateriaali",
     :se "Prestationsplatsens ytmaterial",
     :en "Surface material for training spot"},
    :data-type "string",
    :description
    {:fi "Esim. keihäänheittopaikan pintamateriaali/päällys",
     :se "T.ex. spjutkastningsplatsens ytmaterial/överläggning",
     :en "E.g. the surface material/overlay of the javelin throw area."}},
   :range?
   {:name
    {:fi "Harjoitusalue/range", :se "Övningszon/Range", :en "Range"},
    :data-type "boolean",
    :description
    {:fi "Onko golfin harjoitusalue/range",
     :se "Finns det övningsområde/range för golf",
     :en "Is there a golf practice area/range"}},
   :track-length-m
   {:name
    {:fi "Radan pituus m",
     :se "Banans längd i m",
     :en "Length of track m"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan, pyöräilyradan tms. pituus metreinä",
     :se "Löpbanans, rundbanans el.dyl. längd i meter",
     :en "The length of the running track, cycling track, etc., in meters"}}})

(def schemas
  (into {} (for [[k m] all]
             [k (case (:data-type m)
                  "string"    [:string]
                  "numeric"   number?
                  "boolean"   [:boolean]
                  "enum"      (into [:enum] (keys (:opts m)))
                  "enum-coll" [:sequential (into [:enum] (keys (:opts m)))])])))
