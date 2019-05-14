(ns lipas.data.types)

(def main-categories
  {0
   {:type-code 0,
    :name
    {:fi "Virkistyskohteet ja palvelut",
     :se "Rekreationsanläggningar och tjänster",
     :en "Recreational destinations and services"}},
   1000
   {:type-code 1000,
    :name
    {:fi "Ulkokentät ja liikuntapuistot",
     :se "Utomhusplaner och idrottsparker",
     :en "Outdoor fields and sports parks"}},
   2000
   {:type-code 2000,
    :name
    {:fi "Sisäliikuntatilat",
     :se "Anläggningar för inomhusidrott",
     :en "Indoor sports facilities"}},
   3000
   {:type-code 3000,
    :name
    {:fi "Vesiliikuntapaikat",
     :se "Anläggningar för vattenidrott",
     :en "Water sports facilities"}},
   4000
   {:type-code 4000,
    :name
    {:fi "Maastoliikuntapaikat",
     :se "Anläggningar för terrängidrott",
     :en "Cross-country sports facilities"}},
   5000
   {:type-code 5000,
    :name
    {:fi "Veneily, ilmailu ja moottoriurheilu",
     :se "Anläggningar för båtsport, flygsport och motorsport",
     :en "Boating, aviation and motor sports"}},
   6000
   {:type-code 6000,
    :name
    {:fi "Eläinurheilualueet",
     :se "Anläggningar för djursport",
     :en "Animal sports areas"}},
   7000
   {:type-code 7000,
    :name
    {:fi "Huoltorakennukset",
     :se "Servicebyggnader",
     :en "Maintenance/service buildings"}}})

(def sub-categories
  {2100
   {:type-code 2100,
    :name
    {:fi "Kuntoilukeskukset ja liikuntasalit",
     :se "Konditionsidrottscentra och idrottssalar",
     :en "Fitness centres and sports halls"},
    :main-category "2000"},
   5200
   {:type-code 5200,
    :name
    {:fi "Urheiluilmailualueet",
     :se "Områden för flygsport",
     :en "Sport aviation areas"},
    :main-category "5000"},
   4200
   {:type-code 4200,
    :name
    {:fi "Katetut talviurheilupaikat",
     :se "Vintersportplatser under tak",
     :en "Covered winter sports facilities"},
    :main-category "4000"},
   2200
   {:type-code 2200,
    :name
    {:fi "Liikuntahallit", :se "Idrottshallar", :en "Sports halls"},
    :main-category "2000"},
   1
   {:type-code 1,
    :name
    {:fi "Virkistys- ja retkeilyalueet",
     :se "Rekreations- och friluftsområden",
     :en "Recreational and outdoor areas "},
    :main-category "0"},
   7000
   {:type-code 7000,
    :name
    {:fi "Huoltotilat",
     :se "Servicebyggnader",
     :en "Maintenance/service buildings"},
    :main-category "7000"},
   4400
   {:type-code 4400,
    :name
    {:fi "Liikunta- ja ulkoilureitit",
     :se "Idrotts- och friluftsleder",
     :en "Sports and outdoor recreation routes "},
    :main-category "4000"},
   1300
   {:type-code 1300,
    :name
    {:fi "Pallokentät", :se "Bollplaner", :en "Ball games courts"},
    :main-category "1000"},
   6100
   {:type-code 6100,
    :name
    {:fi "Hevosurheilu", :se "Hästsport", :en "Equestrian sports"},
    :main-category "6000"},
   4700
   {:type-code 4700,
    :name
    {:fi "Kiipeilypaikat",
     :se "Klättringsplatser",
     :en "Climbing venues"},
    :main-category "4000"},
   3100
   {:type-code 3100,
    :name
    {:fi "Uima-altaat, hallit ja kylpylät",
     :se "Simbassänger, hallar och badinrättningar",
     :en "Indoor swimming pools, halls and spas"},
    :main-category "3000"},
   2500
   {:type-code 2500,
    :name {:fi "Jäähallit", :se "Ishallar", :en "Ice-skating arenas"},
    :main-category "2000"},
   1200
   {:type-code 1200,
    :name
    {:fi "Yleisurheilukentät ja -paikat",
     :se "Planer och platser för friidrott",
     :en "Athletics fields and venues"},
    :main-category "1000"},
   5300
   {:type-code 5300,
    :name
    {:fi "Moottoriurheilualueet",
     :se "Områden för motorsport",
     :en "Motor sports areas"},
    :main-category "5000"},
   1600
   {:type-code 1600,
    :name {:fi "Golfkentät", :se "Golfbanor", :en "Golf courses"},
    :main-category "1000"},
   1500
   {:type-code 1500,
    :name
    {:fi "Jääurheilualueet ja luonnonjäät",
     :se "Isidrottsområden och naturisar",
     :en "Ice sports areas and sites with natural ice"},
    :main-category "1000"},
   6200
   {:type-code 6200,
    :name {:fi "Koiraurheilu", :se "Hundsport", :en "Dog sports"},
    :main-category "6000"},
   3200
   {:type-code 3200,
    :name
    {:fi "Maauimalat ja uimarannat",
     :se "Utebassänger och badstränder",
     :en "Open air pools and beaches"},
    :main-category "3000"},
   2
   {:type-code 2,
    :name
    {:fi "Retkeilyn palvelut",
     :se "Utflyktstjänster",
     :en "Hiking facilities"},
    :main-category "0"},
   1100
   {:type-code 1100,
    :name
    {:fi "Lähiliikunta ja liikuntapuistot",
     :se "Närmotion och idrottsparker",
     :en "Neighbourhood sports facilities and parks "},
    :main-category "1000"},
   4100
   {:type-code 4100,
    :name
    {:fi "Laskettelurinteet ja rinnehiihtokeskukset",
     :se "Slalombackar och alpina skidcentra",
     :en "Ski slopes and downhill ski resorts"},
    :main-category "4000"},
   5100
   {:type-code 5100,
    :name
    {:fi "Veneurheilupaikat",
     :se "Platser för båtsport",
     :en "Boating sports facilities"},
    :main-category "5000"},
   2600
   {:type-code 2600,
    :name
    {:fi "Keilahallit", :se "Bowlinghallar", :en "Bowling alleys"},
    :main-category "2000"},
   2300
   {:type-code 2300,
    :name
    {:fi "Yksittäiset lajikohtaiset sisäliikuntapaikat",
     :se "Enstaka grenspecifika anläggningar för inomhusidrott",
     :en "Indoor venues for various sports "},
    :main-category "2000"},
   4300
   {:type-code 4300,
    :name
    {:fi "Hyppyrimäet", :se "Hoppbackar", :en "Ski jumping hills"},
    :main-category "4000"},
   4500
   {:type-code 4500,
    :name
    {:fi "Suunnistusalueet",
     :se "Orienteringsområden",
     :en "Orienteering areas"},
    :main-category "4000"},
   4600
   {:type-code 4600,
    :name
    {:fi "Maastohiihtokeskukset",
     :se "Längdåkningscentra",
     :en "Cross-country ski resorts"},
    :main-category "4000"},
   4800
   {:type-code 4800,
    :name
    {:fi "Ampumaurheilupaikat",
     :se "Sportskytteplatser",
     :en "Shooting sports facilities"},
    :main-category "4000"}})

(def all
  {1530
   {:description
    {:fi
     "Luisteluun, jääkiekkoon, kaukalopalloon jne. tarkoitettu kaukalo. Käytössä talvikaudella.",
     :se
     "Rink avsedd för skridskoåkning, ishockey, rinkbandy osv. Används under vintersäsongen.",
     :en
     "Rink intended for ice-skating, ice hockey, rink bandy, etc. Potential summer use specified in 'attribute data'."},
    :tags          {:fi ["jääkiekkokaukalo"]},
    :name          {:fi "Kaukalo", :se "Rink", :en "Rink"},
    :type-code     1530,
    :main-category 1000,
    :status        "active",
    :sub-category  1500,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :match-clock?          {:priority 0},
     :ice-rinks-count       {:priority 0},
     :toilet?               {:priority 0},
     :changing-rooms?       {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :light-roof?           {:priority 0}}},
   1520
   {:description
    {:fi
     "Luisteluun tarkoitettu kenttä. Jäädytetään talvisin. Käytössä talvikaudella.",
     :se
     "Plan avsedd för skridskoåkning. Används under vintersäsongen",
     :en
     "Field intended for ice-skating. Summer use specified in 'attribute data'."},
    :tags          {:fi []},
    :name
    {:fi "Luistelukenttä",
     :se "Skridskobana",
     :en "Ice-skating field"},
    :type-code     1520,
    :main-category 1000,
    :status        "active",
    :sub-category  1500,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :match-clock?          {:priority 0},
     :fields-count          {:priority 0},
     :toilet?               {:priority 0},
     :changing-rooms?       {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :light-roof?           {:priority 0}}},
   2320
   {:description
    {:fi "Pysyvästi telinevoimisteluun varustettu tila.",
     :se "Permanent utrustning för att träna redskapsgymnastik.",
     :en "Space permanently equipped for artistic gymnastics."},
    :tags          {:fi ["monttu" "rekki" "nojapuut"]},
    :name
    {:fi "Telinevoimistelutila",
     :se "Utrymme för redskapsgymnastik",
     :en "Artistic gymnastics facility"},
    :type-code     2320,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:landing-places-count     {:priority 0},
     :surface-material-info    {:priority 0},
     :surface-material         {:priority 1},
     :area-m2                  {:priority 1},
     :height-m                 {:priority 0},
     :gymnastic-routines-count {:priority 0},
     :school-use?              {:priority 0},
     :free-use?                {:priority 0}}},
   6130
   {:description
    {:fi "Pysyvästi esteratsastukseen varusteltu kenttä. Ulkona.",
     :se "Bana med permanent utrustning för banhoppning",
     :en "Field permanently equipped for show jumping. Outdoors."},
    :tags          {:fi ["ratsastuskenttä"]},
    :name
    {:fi "Esteratsastuskenttä",
     :se "Bana för banhoppning",
     :en "Show jumping field"},
    :type-code     6130,
    :main-category 6000,
    :status        "active",
    :sub-category  6100,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0}}},
   6210
   {:description
    {:fi
     "Koiran koulutukseen, agilityyn tai muuhun harjoittamiseen varattu ulkoalue.",
     :se
     "Område reserverat för hunddressyr, agility eller annan träning.",
     :en
     "Area reserved for dog training, agility or other dog sports."},
    :tags          {:fi ["agility" "koirakenttä"]},
    :name
    {:fi "Koiraurheilualue",
     :se "Område för hundsport",
     :en "Dog sports area"},
    :type-code     6210,
    :main-category 6000,
    :status        "active",
    :sub-category  6200,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 0},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :track-length-m        {:priority 0}}},
   1370
   {:description
    {:fi
     "Tenniskenttä tai useampi kenttä samalla alueella. Montako kenttää, pintamateriaali yms. ominaisuustietoihin. Myös tieto lyöntiseinästä.",
     :se
     "En eller flera tennisbanor på samma område. Antalet banor, ytmaterial mm i karakteristika. Även uppgift om slagväggen.",
     :en
     "One or more tennis courts in the same area. Number of courts, surface material, etc. specified in 'attribute data', including information about a potential hit wall."},
    :tags          {:fi ["tenniskenttä"]},
    :name
    {:fi "Tenniskenttäalue",
     :se "Område med tennisbanor",
     :en "Tennis court area"},
    :type-code     1370,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:heating?              {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :fields-count          {:priority 1},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :training-wall?        {:priority 0},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :light-roof?           {:priority 0}}},
   1360
   {:description
    {:fi
     "Pesäpallokenttä, mikäli katsomoita tarjolla, lisätään kentän nimeen stadion-sana. Vähintään kansallisen tason pelipaikka. Hiekka, hiekkatekonurmi / muu synteettinen päällyste, > 50 x 100 m. ",
     :se
     "En bobollsplan, kan ha flera läktare. Minimikrav: spelplats på nationell nivå.Sand, konstgräs på sand / annan syntetisk beläggning. >50 x 100 m.",
     :en
     "Finnish baseball field, may include stands. Can host at least national-level games. Sand, artificial turf / other synthetic surface, > 50 x 100 m. "},
    :tags          {:fi ["pesäpallostadion"]},
    :name
    {:fi "Pesäpallokenttä", :se "Bobollsplan", :en "Baseball field"},
    :type-code     1360,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:heating?                   {:priority 0},
     :surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :kiosk?                     {:priority 0},
     :stand-capacity-person      {:priority 0},
     :free-use?                  {:priority 0},
     :field-length-m             {:priority 1},
     :match-clock?               {:priority 0},
     :fields-count               {:priority 0},
     :toilet?                    {:priority 0},
     :changing-rooms?            {:priority 0},
     :area-m2                    {:priority 1},
     :field-width-m              {:priority 1},
     :scoreboard?                {:priority 0},
     :loudspeakers?              {:priority 0},
     :ligthing?                  {:priority 1},
     :covered-stand-person-count {:priority 0},
     :school-use?                {:priority 0}}},
   110
   {:description
    {:fi
     "Erämaalailla perustetut alueet pohjoisimmassa Lapissa. Metsähallitus tietolähteenä.",
     :se
     "Grundade enligt ödemarkslagen, i nordligaste Lappland. Källa: Forststyrelsen.",
     :en
     "Areas located in northernmost Lapland, established based on the Wildeness Act (1991/62). Source of information  Metsähallitus."},
    :tags          {:fi []},
    :name
    {:fi "Erämaa-alue", :se "Vildmarksområden", :en "Wilderness area"},
    :type-code     110,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   2360
   {:description
    {:fi "Pysyvästi käytössä oleva ampumarata sisätiloissa.",
     :se
     "Permanent skjutbana inomhus. Obs. Länk till sportskytteplatser.",
     :en
     "Permanent indoor shooting range. Note  Link to 'shooting sports facilities'."},
    :tags          {:fi ["ilmakivääri" "ilma-ase" "ammunta"]},
    :name
    {:fi "Sisäampumarata",
     :se "Inomhusskjutbana",
     :en "Indoor shooting range"},
    :type-code     2360,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:surface-material         {:priority 0},
     :surface-material-info    {:priority 0},
     :free-use?                {:priority 0},
     :air-gun-shooting?        {:priority 0},
     :pistol-shooting?         {:priority 0},
     :shooting-positions-count {:priority 1},
     :area-m2                  {:priority 1},
     :rifle-shooting?          {:priority 0},
     :free-rifle-shooting?     {:priority 0},
     :school-use?              {:priority 0},
     :track-length-m           {:priority 1}}},
   5310
   {:description
    {:fi
     "Useiden eri moottoriurheilun lajien suorituspaikkoja, huoltotilat olemassa.",
     :se
     "Platser för flera olika motorsportgrenar, serviceutrymmen finns.",
     :en
     "Venues for various motor sports; service premises available."},
    :tags          {:fi []},
    :name
    {:fi "Moottoriurheilukeskus",
     :se "Centrum för motorsport",
     :en "Motor sports centre"},
    :type-code     5310,
    :main-category 5000,
    :status        "active",
    :sub-category  5300,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :automated-timing?          {:priority 0},
     :kiosk?                     {:priority 0},
     :stand-capacity-person      {:priority 0},
     :free-use?                  {:priority 0},
     :finish-line-camera?        {:priority 0},
     :track-width-m              {:priority 0},
     :toilet?                    {:priority 0},
     :area-m2                    {:priority 0},
     :scoreboard?                {:priority 0},
     :loudspeakers?              {:priority 0},
     :ligthing?                  {:priority 0},
     :covered-stand-person-count {:priority 0},
     :school-use?                {:priority 0},
     :track-length-m             {:priority 1}}},
   1560
   {:description
    {:fi
     "Alamäkiluistelua varten vuosittain rakennettava rata. Käytössä talvikaudella.",
     :se "Permanent bana byggd för utförsåkning.",
     :en "Permanent track built for downhill skating. "},
    :tags          {:fi ["luistelu" "alamäkiluistelu"]},
    :name
    {:fi "Alamäkiluistelurata",
     :se "Skridskobana för utförsåkning",
     :en "Downhill skating track"},
    :type-code     1560,
    :main-category 1000,
    :status        "active",
    :sub-category  1500,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :lifts-count           {:priority nil},
     :free-use?             {:priority 0},
     :track-width-m         {:priority 0},
     :altitude-difference   {:priority 1},
     :toilet?               {:priority 0},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :track-length-m        {:priority 1}}},
   205
   {:description
    {:fi
     "Rantautumiseen osoitettu paikka, ei järjestettyjä palveluita.",
     :se
     "Plats som anvisats för ilandstigning, inga ordnade tjänster.",
     :en "Place intended for landing by boat, no services provided."},
    :tags          {:fi ["laituri" "taukopaikka"]},
    :name
    {:fi "Rantautumispaikka",
     :se "Ilandstigningsplats",
     :en "Boat dock"},
    :type-code     205,
    :main-category 0,
    :status        "active",
    :sub-category  2,
    :geometry-type "Point",
    :props
    {:toilet?                           {:priority 0},
     :boat-launching-spot?              {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :pier?                             {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   2150
   {:description
    {:fi
     "Muun rakennuksen yhteydessä oleva liikuntatila. Salin koko vaihtelee alle 300 neliöstä noin 750 neliöön.",
     :se "",
     :en
     "A gymnastics hall connected to another building. Size and height specified in 'additional information'. Also specified in 'additional information' if within a school."},
    :tags          {:fi ["jumppasali" "voimistelusali"]},
    :name
    {:fi "Liikuntasali", :se "Idrottssal", :en "Gymnastics hall"},
    :type-code     2150,
    :main-category 2000,
    :status        "active",
    :sub-category  2100,
    :geometry-type "Point",
    :props
    {:height-m                {:priority 1},
     :surface-material        {:priority 1},
     :basketball-fields-count {:priority 0},
     :surface-material-info   {:priority 0},
     :free-use?               {:priority 0},
     :tennis-courts-count     {:priority 0},
     :field-length-m          {:priority 1},
     :match-clock?            {:priority 0},
     :badminton-courts-count  {:priority 0},
     :gymnastics-space?       {:priority 0},
     :area-m2                 {:priority 1},
     :field-width-m           {:priority 1},
     :futsal-fields-count     {:priority 0},
     :football-fields-count   {:priority 0},
     :floorball-fields-count  {:priority 0},
     :handball-fields-count   {:priority 0},
     :volleyball-fields-count {:priority 0},
     :spinning-hall?          {:priority 0},
     :school-use?             {:priority 0}}},
   2210
   {:description
    {:fi
     "Liikuntahalli on rakennus, jossa on useita liikuntatiloja. Koko vaihtelee noin 750-4999 neliön välillä.",
     :se
     "Byggnad med utrymmen för flera idrottsgrenar. Storlek och övningsfaciliteter i karakteristika.",
     :en
     "Building containing facilities for various sports. Size and facilities specified in 'attribute data'."},
    :tags          {:fi ["urheilutalo" "urheiluhalli"]},
    :name          {:fi "Liikuntahalli", :se "Idrottshall", :en "Sports hall "},
    :type-code     2210,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m                       {:priority 1},
     :surface-material               {:priority 1},
     :basketball-fields-count        {:priority 0},
     :surface-material-info          {:priority 0},
     :kiosk?                         {:priority 0},
     :stand-capacity-person          {:priority 0},
     :free-use?                      {:priority 0},
     :sprint-lanes-count             {:priority 0},
     :javelin-throw-places-count     {:priority 0},
     :tennis-courts-count            {:priority 0},
     :field-length-m                 {:priority 1},
     :circular-lanes-count           {:priority 0},
     :match-clock?                   {:priority 0},
     :inner-lane-length-m            {:priority 0},
     :discus-throw-places            {:priority 0},
     :badminton-courts-count         {:priority 0},
     :hammer-throw-places-count      {:priority 0},
     :polevault-places-count         {:priority 0},
     :group-exercise-rooms-count     {:priority 0},
     :toilet?                        {:priority 0},
     :gymnastics-space?              {:priority 0},
     :running-track-surface-material {:priority 0},
     :area-m2                        {:priority 1},
     :field-width-m                  {:priority 1},
     :scoreboard?                    {:priority nil},
     :futsal-fields-count            {:priority 0},
     :shotput-count                  {:priority 0},
     :longjump-places-count          {:priority 0},
     :football-fields-count          {:priority 0},
     :floorball-fields-count         {:priority 0},
     :squash-courts-count            {:priority 0},
     :accessibility-info             {:priority 0},
     :handball-fields-count          {:priority 0},
     :volleyball-fields-count        {:priority 0},
     :climbing-wall?                 {:priority 0},
     :school-use?                    {:priority 0},
     :highjump-places-count          {:priority 0}}},
   101
   {:description
    {:fi
     "Lähipuistot sijaitsevat taajamissa asutuksen välittömässä läheisyydessä. Lähipuistot on tarkoitettu jokapäiväiseen käyttöön. Leikki-, oleskelu- ja kävelypaikka. Kaavamerkintä VL.",
     :se
     "Närparkerna befinner sig i tätorter, i omedelbar närhet av bebyggelse. Närparkerna är avsedda till daglig användning. En plats för lek, vistelse och promenad.                   Planbeteckning VL.",
     :en
     "In population centres, in or near residential areas. Intended for daily use. Used for play, recreation and walks. Symbol VL."},
    :tags          {:fi ["puisto"]},
    :name          {:fi "Lähipuisto", :se "Närpark", :en "Neighbourhood park"},
    :type-code     101,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:playground? {:priority 0},
     :area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   102
   {:description
    {:fi
     "Päivittäin käytettäviä alueita, max 1 km asunnoista. Toimivat kävely-, leikki-, oleskelu-, lenkkeily- ja pyöräilypaikkoina. Kevyt liikenne voi mennä ulkoilupuiston läpi. Voi sisältää puistoa, metsää, peltoa, niittyä, vesialuetta. Kaavamerkintä V tai VL.",
     :se
     "Områden avsedda för daglig använding, max på 1 kilometers avstånd från bebyggelse. Fungerar som ett område för promenader, lekar, vistelse, joggning och cykling. Lätt trafikled kan fara igenom friluftsparken. Området kan bestä av park, skog, äker, äng och vattenled. Planbeteckning V eller VL.",
     :en
     "Used daily, max. 1 km from residential areas. Intended for walks, play, recreation, jogging and cycling. There may be bicycle and pedestrian traffic across the park. May consist of park, forest, fields, meadows, bodies of water. Symbol V or VL."},
    :tags          {:fi ["puisto"]},
    :name
    {:fi "Ulkoilupuisto", :se "Friluftspark", :en "Leisure park"},
    :type-code     102,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:playground?                       {:priority 0},
     :area-km2                          {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   7000
   {:description
    {:fi
     "Liikuntapaikan tai -paikkojen yhteydessä oleva, liikuntapaikan ylläpitoa tai käyttöä palveleva rakennus. Voi sisältää varastoja, pukuhuoneita, suihkutiloja yms.",
     :se "Servicebyggnader i anslutning till idrottsanläggningar.",
     :en
     "Maintenance buildings in connection with sports facilities."},
    :tags          {:fi ["konesuoja" "huoltotila"]},
    :name
    {:fi "Huoltorakennukset",
     :se "Servicebyggnader",
     :en "Maintenance/service buildings"},
    :type-code     7000,
    :main-category 7000,
    :status        "active",
    :sub-category  7000,
    :geometry-type "Point",
    :props
    {:kiosk?            {:priority 0},
     :free-use?         {:priority 0},
     :ski-service?      {:priority 0},
     :toilet?           {:priority 0},
     :shower?           {:priority 0},
     :changing-rooms?   {:priority 0},
     :area-m2           {:priority 0},
     :equipment-rental? {:priority 0},
     :sauna?            {:priority 0},
     :school-use?       {:priority 0}}},
   1110
   {:description
    {:fi
     "Liikuntapuisto on useita liikuntapaikkoja käsittävä liikunta-alue. Liikuntapuistossa voi olla esim. erilaisia kenttiä, uimaranta, kuntorata, monitoimihalli, leikkipuisto jne. koottuna samalle alueelle. Lipakseen tallennetaan sekä tieto liikuntapuistosta että yksittäiset liikuntapaikat, joita puisto sisältää. Liikuntapaikat lasketaan omiksi paikoikseen.",
     :se
     "En idrottspark är ett idrottsområde med flera idrottsplatser. Där kan finnas olika planer, badstrand, konditionsbana, allaktivitetshall, lekpark osv samlade på samma område. I Lipas lagras uppgifter om såväl idrottsparken som enstaka faciliteter som finns i parken. Varje motionsplats räknas som en plats. Sökning kan ske både via parken eller enstaka faciliteter.",
     :en
     "A sports park is an area including several sports facilities, e.g., different fields, beach, a jogging track, a multi-purpose hall, a playground. 'Lipas' contains information both on the sports park and the individual sports facilities found there. The sports facilities are listed as individual items in the classification, enabling search by the park or by the individual facility."},
    :tags          {:fi ["puisto" "lähiliikunta" "lähiliikuntapaikka"]},
    :name          {:fi "Liikuntapuisto", :se "Idrottspark", :en "Sports park"},
    :type-code     1110,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Polygon",
    :props
    {:area-m2            {:priority 1},
     :playground?        {:priority 0},
     :accessibility-info {:priority nil},
     :ligthing?          {:priority 1},
     :fields-count       {:priority 0},
     :school-use?        {:priority 0},
     :free-use?          {:priority 0}}},
   6220
   {:description
    {:fi
     "Erityisesti koiraharrastusta, agilityä, koulutusta tms. varten varustettu halli.",
     :se
     "Hall som utrustats särskilt för hundhobby, agility, dressyr osv.",
     :en
     "Hall specifically equipped for dog sports, agility, training, etc."},
    :tags          {:fi ["agility" "koirahalli"]},
    :name
    {:fi "Koiraurheiluhalli",
     :se "Hundsporthall",
     :en "Dog sports hall"},
    :type-code     6220,
    :main-category 6000,
    :status        "active",
    :sub-category  6200,
    :geometry-type "Point",
    :props
    {:heating?              {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 0},
     :ligthing?             {:priority 0},
     :school-use?           {:priority 0}}},
   4530
   {:description
    {:fi
     "Pyöräillen tapahtuvaan suunnistamiseen, alueesta on pyöräsuunnistukseen soveltuva kartta.",
     :se "Karta över område som lämpar sig för cykelorientering.",
     :en "A map for mountain bike orienteering available."},
    :tags          {:fi []},
    :name
    {:fi "Pyöräsuunnistusalue",
     :se "Cykelorienteringsområde",
     :en " Mountain bike orienteering area"},
    :type-code     4530,
    :main-category 4000,
    :status        "active",
    :sub-category  4500,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   4720
   {:description
    {:fi
     "Merkitty luonnon kallio, jota voi käyttää kiipeilyyn. Jääkiipeily lisätietoihin. Myös boulderointikalliot.",
     :se
     "Märkt berg i naturen. Isklättring i tilläggsinformation. Även berg för bouldering.",
     :en
     "Marked natural cliff. Ice climbing specified in additional information. Also includes bouldering cliffs."},
    :tags          {:fi []},
    :name
    {:fi "Kiipeilykallio", :se "Klätterberg", :en "Climbing rock"},
    :type-code     4720,
    :main-category 4000,
    :status        "active",
    :sub-category  4700,
    :geometry-type "Point",
    :props
    {:surface-material                  {:priority 0},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :climbing-routes-count             {:priority 0},
     :ice-climbing?                     {:priority 0},
     :climbing-wall-height-m            {:priority 1},
     :area-m2                           {:priority 1},
     :climbing-wall-width-m             {:priority 1},
     :ligthing?                         {:priority 0},
     :school-use?                       {:priority 0}}},
   1330
   {:description
    {:fi
     "Rantalentopallokenttä, pehmeä alusta. Voi sijaita muuallakin kuin rannalla.",
     :se
     "Beachvolleybollplan, mjuk grund. Kan också ha annat läge än stranden.",
     :en
     "Beach volleyball court, soft basement. May also be located far from a beach."},
    :tags          {:fi ["rantalentopallo" "rantalentopallokenttä"]},
    :name
    {:fi "Beachvolleykenttä",
     :se "Beachvolleyplan",
     :en "Beach volleyball court"},
    :type-code     1330,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0}}},
   206
   {:description
    {:fi "Rakennettu tulentekopaikka tai keittokatos.",
     :se "Kokskjul eller ordnad plats för att göra upp eld.",
     :en "Campfire or cooking shelter."},
    :tags
    {:fi
     ["nuotiopaikka"
      "keittokatos"
      "grillauspaikka"
      "ruoka"
      "taukopaikka"]},
    :name
    {:fi "Ruoanlaittopaikka",
     :se "Matlagningsplats",
     :en "Cooking facilities"},
    :type-code     206,
    :main-category 0,
    :status        "active",
    :sub-category  2,
    :geometry-type "Point",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :toilet?                           {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   4830
   {:description
    {:fi "Ulkona tai sisällä. Varustus ja lajit ominaisuustietoihin.",
     :se "Ute eller inne.Utrustning och grenar i karakteristika.",
     :en
     "Outdoors or indoors. Equipment and the various sports detailed in 'attribute data'."},
    :tags          {:fi []},
    :name
    {:fi "Jousiammuntarata", :se "Bågskyttebana", :en "Archery range"},
    :type-code     4830,
    :main-category 4000,
    :status        "active",
    :sub-category  4800,
    :geometry-type "Point",
    :props
    {:kiosk?                   {:priority 0},
     :free-use?                {:priority 0},
     :track-width-m            {:priority 0},
     :toilet?                  {:priority nil},
     :shooting-positions-count {:priority 1},
     :area-m2                  {:priority 0},
     :ligthing?                {:priority 1},
     :school-use?              {:priority 0},
     :track-length-m           {:priority 1}}},
   1180
   {:description
    {:fi "Frisbeegolfin pelaamiseen rakennettu rata.",
     :se "En bana byggt för frisbeegolf.",
     :en "Track built for disc golf. "},
    :tags          {:fi []},
    :name
    {:fi "Frisbeegolfrata",
     :se "Frisbeegolfbana",
     :en "Disc golf course"},
    :type-code     1180,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Point",
    :props
    {:surface-material                  {:priority 0},
     :surface-material-info             {:priority 0},
     :holes-count                       {:priority 1},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :altitude-difference               {:priority 0},
     :ligthing?                         {:priority 0},
     :accessibility-info                {:priority 0},
     :school-use?                       {:priority 0},
     :track-type                        {:priority 0},
     :range?                            {:priority 0},
     :track-length-m                    {:priority 0}}},
   4422
   {:description
    {:fi
     "Moottorikelkkailuun tarkoitettu reitti, jolla ei ole tehty virallista reittitoimitusta. Reitillä on kuitenkin ylläpitäjä ja maanomistajien lupa.",
     :se "Ingen ruttexpedition.",
     :en "No official approval."},
    :tags          {:fi []},
    :name
    {:fi "Moottorikelkkaura",
     :se "Snöskoterspår",
     :en "Unofficial snowmobile route"},
    :type-code     4422,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :route-width-m                     {:priority 0},
     :route-length-km                   {:priority 1},
     :rest-places-count                 {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   4430
   {:description
    {:fi
     "Ratsastukseen ja/tai kärryillä ajoon tarkoitettu reitti. Sallitut käyttötavat kerrotaan reitin tarkemmissa tiedoissa.",
     :se
     "Led avsedd för ridning och/eller häst med kärra. Användningsanvisningar i karakteristika.",
     :en
     "Route intended for horseback riding and/or carriage riding. Different uses specified in 'attribute data'."},
    :tags          {:fi ["ratsastusreitti"]},
    :name          {:fi "Hevosreitti", :se "Hästled", :en "Horse track"},
    :type-code     4430,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material      {:priority 1},
     :rest-places-count     {:priority 0},
     :lit-route-length-km   {:priority 1},
     :surface-material-info {:priority 0},
     :route-width-m         {:priority 1},
     :route-length-km       {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   204
   {:description
    {:fi
     "Luonnon tarkkailuun tarkoitettu rakennelma, lintutorni tai vastaava.",
     :se
     "Anordning avsedd för observationer i  naturen, t ex fågeltorn.",
     :en
     "Structure built for nature observation. E.g. bird observation tower."},
    :tags          {:fi ["lintutorni" "näkötorni" "torni"]},
    :name
    {:fi "Luontotorni",
     :se "Naturtorn",
     :en "Nature observation tower"},
    :type-code     204,
    :main-category 0,
    :status        "active",
    :sub-category  2,
    :geometry-type "Point",
    :props
    {:toilet?                           {:priority nil},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   106
   {:description
    {:fi
     "Maa- ja metsätalousalueita, joita käytetään jokamiehenoikeuteen perustuen myös ulkoiluun, voidaan nimittää monikäyttöalueiksi. Monikäyttöalueita ovat erityisesti rakentamattomat rannat ja taajamien läheiset maa- ja metsätalousalueet. Kaavamerkintä MU.",
     :se
     "Lant- och skogsbruksområden som genom allemansrätten också får användas till friluftsliv, dessa områden kan kallas allaktivitetsområden. Sådana här områden är i synnerhet obebyggda stränder och lant- och skogsbruksområden i närheten av tätorter. Planbeteckning MU.",
     :en
     "Agricultural and forestry areas that (based on the right of public access) are also used for recreation can be called multipurpose areas. In particular, these include unbuilt shores and agricultural and forestry areas close to population centres. Symbol MU."},
    :tags          {:fi ["ulkoilualue" "virkistysalue"]},
    :name
    {:fi "Monikäyttöalue, jolla on virkistyspalveluita",
     :se "Allaktivitetsområde med rekreationstjänster",
     :en "Multipurpose area with recreational services"},
    :type-code     106,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:area-km2                          {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :playground?                       {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   4610
   {:description
    {:fi
     "Ampumahiihdon harjoitteluun tarkoitettu alue, jossa on ainakin latu ja ampumapaikka/-paikkoja. ",
     :se
     "Annat träningsområde för skidskytte. Spår och skjutplats finns.",
     :en
     "Other training area for biathlon. Ski track and shooting range."},
    :tags          {:fi ["ampumapaikka"]},
    :name
    {:fi "Ampumahiihdon harjoittelualue",
     :se "Träningsområde för skidskytte",
     :en "Training area for biathlon"},
    :type-code     4610,
    :main-category 4000,
    :status        "active",
    :sub-category  4600,
    :geometry-type "Point",
    :props
    {:automated-timing?        {:priority 0},
     :kiosk?                   {:priority 0},
     :summer-usage?            {:priority 0},
     :stand-capacity-person    {:priority 0},
     :free-use?                {:priority 0},
     :ski-service?             {:priority 0},
     :finish-line-camera?      {:priority 0},
     :ski-track-traditional?   {:priority 0},
     :route-width-m            {:priority 0},
     :toilet?                  {:priority 0},
     :rest-places-count        {:priority 0},
     :shooting-positions-count {:priority 1},
     :lit-route-length-km      {:priority 0},
     :scoreboard?              {:priority 0},
     :loudspeakers?            {:priority 0},
     :ski-track-freestyle?     {:priority 0},
     :school-use?              {:priority 0},
     :route-length-km          {:priority 0}}},
   2610
   {:description
    {:fi
     "Keilailuun varustettu halli. Ratojen määrä ja palveluvarustus vaihtelee.",
     :se "Antalet banor och serviceutrustning i karakteristika.",
     :en
     "Number of alleys and service facilities in 'attribute data'."},
    :tags          {:fi []},
    :name          {:fi "Keilahalli", :se "Bowlinghall", :en "Bowling alley"},
    :type-code     2610,
    :main-category 2000,
    :status        "active",
    :sub-category  2600,
    :geometry-type "Point",
    :props
    {:kiosk?              {:priority 0},
     :free-use?           {:priority 0},
     :automated-scoring?  {:priority 0},
     :bowling-lanes-count {:priority 1},
     :toilet?             {:priority 0},
     :area-m2             {:priority 0},
     :cosmic-bowling?     {:priority 0},
     :scoreboard?         {:priority 0},
     :loudspeakers?       {:priority 0},
     :school-use?         {:priority 0}}},
   2110
   {:description
    {:fi
     "Erilaisia liikuntapalveluita ja tiloja sisältävä kuntoilukeskus, esim. kuntosali, ryhmäliikuntatiloja jne.",
     :se
     "Olika motionstjänster och -utrymmen, t ex gym och gruppidrottsutrymmen.",
     :en
     "Different sports services and premises, e.g., gym, group exercise premises. "},
    :tags          {:fi ["kuntosali" "kuntoilu"]},
    :name
    {:fi "Kuntokeskus",
     :se "Konditionsidrottscentrum",
     :en "Fitness centre"},
    :type-code     2110,
    :main-category 2000,
    :status        "active",
    :sub-category  2100,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :kiosk?                     {:priority 0},
     :free-use?                  {:priority 0},
     :group-exercise-rooms-count {:priority 0},
     :area-m2                    {:priority 1},
     :boxing-rings-count         {:priority 0},
     :weight-lifting-spots-count {:priority 0},
     :spinning-hall?             {:priority 0},
     :school-use?                {:priority 0},
     :exercise-machines-count    {:priority 0}}},
   3120
   {:description
    {:fi
     "Yksittäinen uima-allas muun kuin uimahallin yhteydessä. Altaan pituus ja tyyppi kerrotaan ominaisuustiedoissa.",
     :se
     "Enstaka simbassäng , ofta i anslutning till en annan byggnad.",
     :en
     "Individual swimming pool, often in connection with other buildings. <17 m long."},
    :tags          {:fi []},
    :name          {:fi "Uima-allas", :se "Simbassäng", :en "Swimming pool"},
    :type-code     3120,
    :main-category 3000,
    :status        "active",
    :sub-category  3100,
    :geometry-type "Point",
    :props
    {:pool-tracks-count   {:priority 0},
     :free-use?           {:priority 0},
     :pool-width-m        {:priority 0},
     :pool-min-depth-m    {:priority 0},
     :swimming-pool-count {:priority 0},
     :pool-water-area-m2  {:priority 1},
     :pool-length-m       {:priority 1},
     :pool-max-depth-m    {:priority 0},
     :accessibility-info  {:priority 0},
     :pool-temperature-c  {:priority 0},
     :school-use?         {:priority 0}}},
   104
   {:description
    {:fi
     "Sijaitsevat kauempana taajamasta, automatkan päässä. Monipuolinen polku- ja reittiverkosto. Käyttö painottuu viikonloppuihin ja loma-aikoihin. Palvelevat usein useaa kuntaa. Kaavamerkintä VR.",
     :se
     "Ett område på bil avstånd från tätorten, Området har en stor variation av stig- och ruttnätverk. Användningen av området fokuserar sig mest till helgerna och semester tiderna. Området betjänar oftast mer än en kommun. Planbeteckning VR.",
     :en
     "Located further away from population centres, accessible by car. Complex network of paths and routes. Use concentrated during weekends and holidays. Often serves several municipalities. Symbol VR."},
    :tags          {:fi ["virkistysalue"]},
    :name
    {:fi "Retkeilyalue", :se "Utflyktsområde", :en "Hiking area"},
    :type-code     104,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :area-km2                          {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   2330
   {:description
    {:fi "Pysyvästi pöytätennikseen varustettu tila.",
     :se "Permanent utrustning för att träna bordtennis.",
     :en "Space permanently equipped for table tennis."},
    :tags          {:fi ["pingis" "pingispöytä"]},
    :name
    {:fi "Pöytätennistila",
     :se "Utrymme för bordtennis",
     :en "Table tennis venue"},
    :type-code     2330,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :height-m              {:priority 0},
     :kiosk?                {:priority 0},
     :area-m2               {:priority 1},
     :table-tennis-count    {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   2280
   {:description
    {:fi
     "Tenniksen pelaamiseen varusteltu halli. Pääkäyttömuoto tenniksen peluu.",
     :se "Antalet banor i karakteristika.",
     :en "Number of courts specified in 'attribute data'."},
    :tags          {:fi []},
    :name          {:fi "Tennishalli", :se "Tennishall", :en "Tennis hall"},
    :type-code     2280,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m                {:priority 1},
     :surface-material        {:priority 1},
     :surface-material-info   {:priority 0},
     :kiosk?                  {:priority 0},
     :stand-capacity-person   {:priority 0},
     :free-use?               {:priority 0},
     :tennis-courts-count     {:priority 1},
     :field-length-m          {:priority 0},
     :badminton-courts-count  {:priority 0},
     :toilet?                 {:priority 0},
     :area-m2                 {:priority 1},
     :field-width-m           {:priority 0},
     :scoreboard?             {:priority 0},
     :floorball-fields-count  {:priority 0},
     :squash-courts-count     {:priority 0},
     :volleyball-fields-count {:priority 0},
     :school-use?             {:priority 0}}},
   6140
   {:description
    {:fi "Raviurheilun harjoitus- tai kilparata.",
     :se "Övnings- eller tävlingsbana för travsport.",
     :en "Training or competition track for horse racing."},
    :tags          {:fi []},
    :name          {:fi "Ravirata", :se "Travbana", :en "Horse racing track"},
    :type-code     6140,
    :main-category 6000,
    :status        "active",
    :sub-category  6100,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :automated-timing?          {:priority 0},
     :kiosk?                     {:priority 0},
     :stand-capacity-person      {:priority 0},
     :free-use?                  {:priority 0},
     :finish-line-camera?        {:priority 0},
     :track-width-m              {:priority 1},
     :toilet?                    {:priority 0},
     :scoreboard?                {:priority 0},
     :loudspeakers?              {:priority 0},
     :ligthing?                  {:priority 1},
     :covered-stand-person-count {:priority 0},
     :school-use?                {:priority 0},
     :track-length-m             {:priority 1}}},
   2140
   {:description
    {:fi "",
     :se "",
     :en
     "Hall for self-defence sports, e.g., wrestling, boxing. Size specified in 'attribute data'. "},
    :tags          {:fi ["paini" "judo" "tatami"]},
    :name
    {:fi "Kamppailulajien sali", :se "", :en "Martial arts hall"},
    :type-code     2140,
    :main-category 2000,
    :status        "active",
    :sub-category  2100,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :kiosk?                     {:priority 0},
     :free-use?                  {:priority 0},
     :group-exercise-rooms-count {:priority 0},
     :tatamis-count              {:priority 0},
     :area-m2                    {:priority 1},
     :wrestling-mats-count       {:priority 0},
     :boxing-rings-count         {:priority 0},
     :weight-lifting-spots-count {:priority 0},
     :school-use?                {:priority 0},
     :exercise-machines-count    {:priority 0}}},
   4220
   {:description
    {:fi
     "Hiihtoon tarkoitettu katettu tila (esim. tunneli, putki, halli).",
     :se
     "Utrymme avsett för skidåkning under tak (tunnel, rör, hall el dyl)",
     :en
     "Covered space (tunnel, tube, hall, etc.) intended for skiing."},
    :tags          {:fi []},
    :name          {:fi "Hiihtotunneli", :se "Skidtunnel", :en "Ski tunnel"},
    :type-code     4220,
    :main-category 4000,
    :status        "active",
    :sub-category  4200,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :ski-service?          {:priority 0},
     :altitude-difference   {:priority 1},
     :route-width-m         {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 0},
     :equipment-rental?     {:priority 0},
     :accessibility-info    {:priority 0},
     :school-use?           {:priority 0},
     :route-length-km       {:priority 1}}},
   2230
   {:description
    {:fi
     "Jalkapalloiluun tarkoitettu halli. Yleensä tekonurmi tai luonnonnurmi.",
     :se
     "Hall avsedd för fotboll. Ytmaterial, antalet planer, storlek och byggnadens konstruktion i karakteristika.",
     :en
     "Hall intended for football. Surface material, number and size of courts specified in 'attribute data', as well as hall structure."},
    :tags          {:fi []},
    :name
    {:fi "Jalkapallohalli", :se "Fotbollshall", :en "Football hall"},
    :type-code     2230,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m                {:priority 1},
     :heating?                {:priority 0},
     :surface-material        {:priority 1},
     :basketball-fields-count {:priority 0},
     :surface-material-info   {:priority 0},
     :kiosk?                  {:priority 0},
     :stand-capacity-person   {:priority 0},
     :free-use?               {:priority 0},
     :tennis-courts-count     {:priority 0},
     :field-length-m          {:priority 1},
     :match-clock?            {:priority 0},
     :sprint-track-length-m   {:priority 0},
     :badminton-courts-count  {:priority 0},
     :toilet?                 {:priority 0},
     :area-m2                 {:priority 1},
     :field-width-m           {:priority 1},
     :scoreboard?             {:priority 0},
     :football-fields-count   {:priority 0},
     :loudspeakers?           {:priority 0},
     :volleyball-fields-count {:priority 0},
     :school-use?             {:priority 0}}},
   1350
   {:description
    {:fi
     "Suuri jalkapallokenttä, katsomoita. Vähintään kansallisen tason pelipaikka.",
     :se
     "Stor fotbollsplan, flera läktare. Minimikrav: spelplats på nationell nivå.",
     :en
     "Large football field, stands. Can host at least national-level games."},
    :tags          {:fi ["jalkapallokenttä"]},
    :name
    {:fi "Jalkapallostadion",
     :se "Fotbollsstadion",
     :en "Football stadium"},
    :type-code     1350,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:heating?                   {:priority 0},
     :surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :kiosk?                     {:priority 0},
     :stand-capacity-person      {:priority 0},
     :free-use?                  {:priority 0},
     :finish-line-camera?        {:priority 0},
     :field-length-m             {:priority 1},
     :match-clock?               {:priority 0},
     :fields-count               {:priority 0},
     :toilet?                    {:priority 0},
     :area-m2                    {:priority 1},
     :field-width-m              {:priority 1},
     :scoreboard?                {:priority 0},
     :loudspeakers?              {:priority 0},
     :ligthing?                  {:priority 1},
     :covered-stand-person-count {:priority 0},
     :school-use?                {:priority 0},
     :light-roof?                {:priority 0}}},
   4840
   {:description
    {:fi "Maastoon rakennettu jousiammuntarata.",
     :se "Bågskyttebana byggd i terrängen.",
     :en "Archery course built in rough terrain."},
    :tags          {:fi []},
    :name
    {:fi "Jousiammuntamaastorata",
     :se "Terrängbana för bågskytte.",
     :en "Field archery course"},
    :type-code     4840,
    :main-category 4000,
    :status        "active",
    :sub-category  4800,
    :geometry-type "Point",
    :props
    {:shooting-positions-count {:priority 1},
     :toilet?                  {:priority nil},
     :track-length-m           {:priority 1},
     :ligthing?                {:priority 1},
     :school-use?              {:priority 0},
     :free-use?                {:priority 0}}},
   1510
   {:description
    {:fi
     "Koneellisesti / keinotekoisesti jäähdytetty ulkokenttä. Kentän koko ja varustustiedot löytyvät ominaisuustiedoista. Käytössä talvikaudella.",
     :se
     "Konstfrusen utomhusplan. Storlek, utrustning, rink, belysning osv i karakteristika. Likaså uppgiften om planen är kombinerad. Används under vintersäsongen",
     :en
     "Mechanically/artificially frozen open-air field. Field size, equipment, rink, lighting, etc. specified in 'attribute data', as well as information about multi-use fields."},
    :tags          {:fi ["luistelukenttä" "luistelu"]},
    :name
    {:fi "Tekojääkenttä",
     :se "Konstis",
     :en "Mechanically frozen open-air ice rink"},
    :type-code     1510,
    :main-category 1000,
    :status        "active",
    :sub-category  1500,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :match-clock?          {:priority 0},
     :fields-count          {:priority 0},
     :ice-rinks-count       {:priority 0},
     :toilet?               {:priority 0},
     :changing-rooms?       {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :light-roof?           {:priority 0}}},
   5350
   {:description
    {:fi "Pääasiallisesti kiihdytysautoilua varten.",
     :se "Huvudsakligen för accelerationskörning.",
     :en "Mainly for drag racing."},
    :tags          {:fi []},
    :name
    {:fi "Kiihdytysrata", :se "Accelerationsbana", :en "Dragstrip"},
    :type-code     5350,
    :main-category 5000,
    :status        "active",
    :sub-category  5300,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :automated-timing?          {:priority 0},
     :kiosk?                     {:priority 0},
     :stand-capacity-person      {:priority 0},
     :free-use?                  {:priority 0},
     :track-width-m              {:priority 0},
     :area-m2                    {:priority 0},
     :scoreboard?                {:priority 0},
     :loudspeakers?              {:priority 0},
     :ligthing?                  {:priority 0},
     :covered-stand-person-count {:priority 0},
     :school-use?                {:priority 0},
     :track-length-m             {:priority 1}}},
   4440
   {:description
    {:fi
     "Hiihtolatu, jossa on aina tai tiettyinä aikoina koirahiihto sallittua. Perinteinen tyyli tai vapaa tyyli.",
     :se
     "Ett skidspår där det alltid eller vissa tider är tillåtet att åka med hundspann. Klassisk stil eller fristil.",
     :en
     "Ski track on which dog skijoring is allowed either always or at given times. Traditional or free style."},
    :tags          {:fi ["koiralatu"]},
    :name
    {:fi "Koirahiihtolatu",
     :se "Spår för hundspann",
     :en "Dog skijoring track"},
    :type-code     4440,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material       {:priority 1},
     :surface-material-info  {:priority 0},
     :free-use?              {:priority 0},
     :ski-track-traditional? {:priority 0},
     :route-width-m          {:priority 0},
     :toilet?                {:priority 0},
     :rest-places-count      {:priority 0},
     :lit-route-length-km    {:priority 1},
     :ski-track-freestyle?   {:priority 0},
     :school-use?            {:priority 0},
     :route-length-km        {:priority 1}}},
   2520
   {:description
    {:fi
     "Kilpajäähalli, jossa on katsomo. Katsomon koko määritellään ominaisuustiedoissa. Kenttien määrät yms. ominaisuuksiin.",
     :se
     "Läktare finns, storleken på läktaren anges i karakteristika, likaså antalet planer.",
     :en
     "Includes bleachers, whose size is specified in 'attribute data'. Number of fields, etc., specified in 'attribute data'."},
    :tags          {:fi ["jäähalli"]},
    :additional-type
    {:small       {:fi "Pieni kilpahalli > 500 hlö", :en nil, :se nil},
     :competition {:fi "Kilpahalli < 3000 hlö", :en nil, :se nil},
     :large       {:fi "Suurhalli > 3000 hlö", :en nil, :se nil}},
    :name
    {:fi "Kilpajäähalli",
     :se "Tävlingsishall",
     :en "Competition ice arena"},
    :type-code     2520,
    :keywords      {:fi ["Jäähalli"], :en [], :se []},
    :main-category 2000,
    :status        "active",
    :sub-category  2500,
    :geometry-type "Point",
    :props
    {:heating?              {:priority 0},
     :field-2-area-m2       {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :field-3-length-m      {:priority 0},
     :field-2-length-m      {:priority 0},
     :automated-timing?     {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-1-length-m      {:priority 0},
     :finish-line-camera?   {:priority 0},
     :match-clock?          {:priority 0},
     :field-1-width-m       {:priority 0},
     :field-3-width-m       {:priority 0},
     :field-2-width-m       {:priority 0},
     :ice-rinks-count       {:priority 1},
     :field-1-area-m2       {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :curling-lanes-count   {:priority 0},
     :scoreboard?           {:priority 0},
     :loudspeakers?         {:priority 0},
     :field-3-area-m2       {:priority 0},
     :school-use?           {:priority 0}}},
   4710
   {:description
    {:fi
     "Rakennettu kiipeilyseinä ulkona. Korkeus ominaisuustietoihin. Myös boulderointipaikat.",
     :se
     "Byggd klättervägg utomhus. Höjden anges i karakteristika. Även platser för bouldering.",
     :en
     "Built outdoor climbing wall. Height given in 'attribute data'. Also includes bouldering venues."},
    :tags          {:fi ["kiipeilyseinä"]},
    :name
    {:fi "Ulkokiipeilyseinä",
     :se "Utomhusklättervägg",
     :en "Open-air climbing wall"},
    :type-code     4710,
    :main-category 4000,
    :status        "active",
    :sub-category  4700,
    :geometry-type "Point",
    :props
    {:surface-material       {:priority 0},
     :surface-material-info  {:priority 0},
     :free-use?              {:priority 0},
     :climbing-routes-count  {:priority 0},
     :ice-climbing?          {:priority 0},
     :climbing-wall-height-m {:priority 1},
     :area-m2                {:priority 1},
     :climbing-wall-width-m  {:priority 1},
     :ligthing?              {:priority 0},
     :school-use?            {:priority 0}}},
   304
   {:description
    {:fi "Tavallisen arkiliikunnan taukopaikka, päiväkäyttöön.",
     :se "Rastplats för bruk under dagen, vardagsmotion.",
     :en "Rest area for regular daily sports, for daytime use."},
    :tags          {:fi ["tupa" "taukopaikka" "ulkoilumaja" "hiihtomaja"]},
    :name
    {:fi "Ulkoilumaja/hiihtomaja",
     :se "Friluftsstuga/skidstuga",
     :en "Outdoor/ski lodge "},
    :type-code     304,
    :main-category 0,
    :status        "active",
    :sub-category  3,
    :geometry-type "Point",
    :props
    {:toilet?                           {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   4412
   {:description
    {:fi
     "Pyöräilyreitti, joka kulkee enimmäkseen päällystetyillä tai sorateillä. Reitin varrella opasteita.",
     :se "Cykelled, ej för mountainbikar.",
     :en "Biking route, not intended for cross-country biking."},
    :tags          {:fi []},
    :name          {:fi "Pyöräilyreitti", :se "Cykelled", :en "Biking route"},
    :type-code     4412,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material                  {:priority 1},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :route-width-m                     {:priority 0},
     :toilet?                           {:priority 0},
     :rest-places-count                 {:priority 0},
     :lit-route-length-km               {:priority 1},
     :school-use?                       {:priority 0},
     :route-length-km                   {:priority 1}}},
   4820
   {:description
    {:fi
     "Ampumarata jossa myös palveluita. SM-kisojen järjestäminen mahdollista.",
     :se "Skjutbana med tjänster. Möjligt att arrangera FM-tävlingar.",
     :en
     "Shooting range with services. National competitions possible."},
    :tags          {:fi ["ampumapaikka" "ammunta"]},
    :name
    {:fi "Ampumaurheilukeskus",
     :se "Sportskyttecentrum",
     :en "Shooting sports centre"},
    :type-code     4820,
    :main-category 4000,
    :status        "active",
    :sub-category  4800,
    :geometry-type "Point",
    :props
    {:kiosk?                   {:priority 0},
     :stand-capacity-person    {:priority 0},
     :free-use?                {:priority 0},
     :track-width-m            {:priority 0},
     :air-gun-shooting?        {:priority 0},
     :toilet?                  {:priority 0},
     :pistol-shooting?         {:priority 0},
     :shooting-positions-count {:priority 1},
     :area-m2                  {:priority 0},
     :rifle-shooting?          {:priority 0},
     :scoreboard?              {:priority 0},
     :loudspeakers?            {:priority 0},
     :shotgun-shooting?        {:priority 0},
     :free-rifle-shooting?     {:priority 0},
     :winter-usage?            {:priority 0},
     :ligthing?                {:priority 1},
     :accessibility-info       {:priority 0},
     :school-use?              {:priority 0},
     :track-length-m           {:priority 1}}},
   1170
   {:description
    {:fi "Ratapyöräilyä varten rakennettu paikka, ulkona (velodromi).",
     :se "Utomhus, velodrom.",
     :en "For track racing outdoors (velodrome)."},
    :tags          {:fi ["velodromi"]},
    :name
    {:fi "Pyöräilyrata", :se "Cykelåkningsbana", :en "Velodrome"},
    :type-code     1170,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Point",
    :props
    {:track-length-m        {:priority 1},
     :track-width-m         {:priority 1},
     :area-m2               {:priority 0},
     :surface-material-info {:priority 0},
     :ligthing?             {:priority 1},
     :surface-material      {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   4404
   {:description
    {:fi
     "Erityisesti luontoharrastusta varten rakennettu ulkoilureitti. Reitin varrella opasteita tai infotauluja alueen luonnosta.",
     :se
     "I synnerhet för naturintresse, info- och orienteringstavlor längs leden.",
     :en
     "Intended particularly for nature activities; signposts or info boards along the route."},
    :tags          {:fi ["retkeily"]},
    :name          {:fi "Luontopolku", :se "Naturstig", :en "Nature trail"},
    :type-code     4404,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material                  {:priority 0},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :route-width-m                     {:priority 0},
     :rest-places-count                 {:priority 0},
     :lit-route-length-km               {:priority 0},
     :accessibility-info                {:priority 0},
     :school-use?                       {:priority 0},
     :route-length-km                   {:priority 1}}},
   108
   {:description
    {:fi
     "Metsähallituksen päätöksellä perustettu virkistysmetsä. Metsätaloudessa huomioidaan mm. maisemalliset arvot. Metsähallitus tietolähde.",
     :se
     "Grundad enligt Forststyrelsens beslut. I skogsbruket tas hänsyn till bl a landskapsvärden. Källa: Forststyrelsen.",
     :en
     "Recreational forest designated by Metsähallitus. E.g. scenic value is considered in forestry. Source of information  Metsähallitus."},
    :tags          {:fi []},
    :name
    {:fi "Virkistysmetsä",
     :se "Friluftsskog",
     :en "Recreational forest"},
    :type-code     108,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   4401
   {:description
    {:fi
     "Kuntoiluun tarkoitettu hoidettu liikuntareitti asutuksen läheisyydessä. Usein ainakin osittain valaistu.",
     :se
     "Led avsedd för konditionssport i närheten av bebyggelse.Vinter- och sommaranvändning beskrivs i karakteristika.",
     :en
     "Winter and summer uses specified in 'attribute data'. Route intended for jogging in or near a residential area."},
    :tags          {:fi ["pururata"]},
    :name          {:fi "Kuntorata", :se "Konditionsbana", :en "Jogging track"},
    :type-code     4401,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material                  {:priority 1},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :outdoor-exercise-machines?        {:priority 0},
     :route-width-m                     {:priority 0},
     :toilet?                           {:priority 0},
     :rest-places-count                 {:priority 0},
     :shooting-positions-count          {:priority 0},
     :lit-route-length-km               {:priority 1},
     :accessibility-info                {:priority 0},
     :school-use?                       {:priority 0},
     :route-length-km                   {:priority 1}}},
   2350
   {:description
    {:fi "Pysyvästi tanssi- tai ilmaisuliikuntaan varustettu tila.",
     :se "Permanent utrustning för dans och kreativ motion.",
     :en
     "Space permanently equipped for dance or expressive movement exercise (tanssi- tai ilmaisuliikunta)."},
    :tags          {:fi ["peilisali" "baletti" "tanssisali"]},
    :name
    {:fi "Tanssitila", :se "Utrymme för dans", :en "Dance studio"},
    :type-code     2350,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:group-exercise-rooms-count {:priority 0},
     :surface-material           {:priority 1},
     :area-m2                    {:priority 1},
     :surface-material-info      {:priority 0},
     :kiosk?                     {:priority 0},
     :height-m                   {:priority 0},
     :school-use?                {:priority 0},
     :free-use?                  {:priority 0}}},
   2340
   {:description
    {:fi "Pysyvästi miekkailuun varustettu tila.",
     :se "Permanent utrustning för fäktning.",
     :en "Space permanently equipped for fencing."},
    :tags          {:fi []},
    :name
    {:fi "Miekkailutila",
     :se "Utrymme för fäktning",
     :en "Fencing venue"},
    :type-code     2340,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:area-m2               {:priority 1},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :height-m              {:priority 0},
     :kiosk?                {:priority 0},
     :fencing-bases-count   {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   2120
   {:description
    {:fi
     "Liikuntatila, jossa on useita kuntosalilaitteita pysyvästi sijoitettuna.",
     :se "Gymredskap osv. Storleken anges i karakteristika.",
     :en "Gym equipment, etc. Size specified in 'attribute data'."},
    :tags          {:fi ["kuntoilu" "voimailu"]},
    :name          {:fi "Kuntosali", :se "Gym", :en "Gym"},
    :type-code     2120,
    :main-category 2000,
    :status        "active",
    :sub-category  2100,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :kiosk?                     {:priority 0},
     :free-use?                  {:priority 0},
     :group-exercise-rooms-count {:priority 0},
     :area-m2                    {:priority 1},
     :weight-lifting-spots-count {:priority 0},
     :spinning-hall?             {:priority 0},
     :school-use?                {:priority 0},
     :exercise-machines-count    {:priority 0}}},
   109
   {:description
    {:fi
     "Ulkoilulailla perustetut, retkeilyä ja luonnon virkistyskäyttöä varten. Metsähallitus tietolähteenä.",
     :se
     "Grundat enligt lagen om friluftsliv för att användas för friluftsliv och rekreation i naturen. Källa: Forststyrelsen.",
     :en
     "Established based on the Outdoor Recreation Act for hiking and recreational use of nature. Source of information  Metsähallitus."},
    :tags          {:fi []},
    :name
    {:fi "Valtion retkeilyalue",
     :se "Statens friluftsområde",
     :en "National hiking area"},
    :type-code     109,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   5160
   {:description
    {:fi "Erillinen liikuntapaikka, ei normaali uima-allas.",
     :se "Separat, ej normal bassäng.",
     :en "Separate training facility, not a regular swimming pool."},
    :tags          {:fi ["kajakki" "kanootti" "melonta"]},
    :name
    {:fi "Soudun ja melonnan sisäharjoittelutila",
     :se "Inomhusträningsutrymme för rodd och paddling",
     :en "Indoor training facility for rowing and canoeing"},
    :type-code     5160,
    :main-category 5000,
    :status        "active",
    :sub-category  5100,
    :geometry-type "Point",
    :props
    {:area-m2     {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   1550
   {:description
    {:fi
     "Luisteluun tarkoitettu, luonnonjäälle tai maalle rakennettava huollettu luistelureitti. Rakennetaan talvisin samalle alueelle, esim. liikuntapuiston alueelle tai meren jäälle.",
     :se
     "Byggs varje vinter på samma område t ex i en idrottspark eller på havsis. Samma som skridskoleden i Anläggningar för terrängidrott.",
     :en
     "Built yearly in the same area, e.g., in a sports park or on frozen lake/sea. Same as 'ice-skating route' in 'outdoor recreation routes'."},
    :tags          {:fi ["luistelu" "retkiluistelu" "retkiluistelurata"]},
    :name
    {:fi "Luistelureitti", :se "Skridskoled", :en "Ice-skating route"},
    :type-code     1550,
    :main-category 1000,
    :status        "active",
    :sub-category  1500,
    :geometry-type "Point",
    :props
    {:surface-material                  {:priority 1},
     :surface-material-info             {:priority 0},
     :kiosk?                            {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :track-width-m                     {:priority 1},
     :toilet?                           {:priority 0},
     :equipment-rental?                 {:priority 0},
     :ligthing?                         {:priority 1},
     :school-use?                       {:priority 0},
     :track-length-m                    {:priority 1}}},
   3230
   {:description
    {:fi
     "Uintiin tarkoitettu paikka, jossa on pukukopit ja veden laadun seuranta. Ei valvontaa.",
     :se
     "Badplats med t ex omklädningshytter men ej övervakning. Vinterbad i karakteristika.",
     :en "Beach with e.g. changing rooms. No lifeguards. "},
    :tags          {:fi ["uimaranta"]},
    :name
    {:fi "Uimapaikka", :se "Badplats", :en "Unsupervised beach "},
    :type-code     3230,
    :main-category 3000,
    :status        "active",
    :sub-category  3200,
    :geometry-type "Point",
    :props
    {:kiosk?                            {:priority 0},
     :free-use?                         {:priority 0},
     :pier?                             {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :beach-length-m                    {:priority 1},
     :toilet?                           {:priority 0},
     :shower?                           {:priority 0},
     :changing-rooms?                   {:priority 0},
     :sauna?                            {:priority 0},
     :other-platforms?                  {:priority 0},
     :school-use?                       {:priority 0}}},
   5130
   {:description
    {:fi "Pysyvä nopeuskilpailujen rata-alue.",
     :se "Permanent banområde för hastighetstävlingar.",
     :en "Permanent track area for speed competitions."},
    :tags          {:fi []},
    :name
    {:fi "Moottoriveneurheilualue",
     :se "Område för motorbåtsport",
     :en "Motor boat sports area"},
    :type-code     5130,
    :main-category 5000,
    :status        "active",
    :sub-category  5100,
    :geometry-type "Point",
    :props
    {:pier?             {:priority 0},
     :area-km2          {:priority 0},
     :boat-places-count {:priority 0},
     :school-use?       {:priority 0},
     :free-use?         {:priority 0}}},
   5110
   {:description
    {:fi
     "Rakennettu pysyvästi soudulle. Katsomo ja valmius ratamerkintöihin.",
     :se
     "Byggt för rodd, permanent. Läktare och förberett för banmärkning.",
     :en
     "Permanent construction for rowing. Bleachers, track markings possible."},
    :tags          {:fi []},
    :name
    {:fi "Soutustadion", :se "Roddstadion", :en "Rowing stadium"},
    :type-code     5110,
    :main-category 5000,
    :status        "active",
    :sub-category  5100,
    :geometry-type "Point",
    :props
    {:automated-timing?     {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :pier?                 {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 0},
     :scoreboard?           {:priority 0},
     :school-use?           {:priority 0},
     :track-length-m        {:priority 1}}},
   3240
   {:description
    {:fi
     "Avanto tai avovesi, joka on talvisin käytettävissä talviuintiin.",
     :se "Endast vinterbadplats, eventuellt vak.",
     :en "Winter swimming area only, possibly a hole in the ice. "},
    :tags          {:fi ["avanto" "avantouinti"]},
    :name
    {:fi "Talviuintipaikka",
     :se "Vinterbadplats",
     :en "Winter swimming area"},
    :type-code     3240,
    :main-category 3000,
    :status        "active",
    :sub-category  3200,
    :geometry-type "Point",
    :props
    {:kiosk?          {:priority 0},
     :free-use?       {:priority 0},
     :pier?           {:priority 0},
     :toilet?         {:priority 0},
     :shower?         {:priority 0},
     :changing-rooms? {:priority 0},
     :ice-reduction?  {:priority 0},
     :sauna?          {:priority 0},
     :school-use?     {:priority 0}}},
   4510
   {:description
    {:fi
     "Suunnistusalue, joka on ilmoitettu suunnistusliitolle. Alueesta kartta saatavilla ja maanomistajien suostumus.",
     :se
     "Anmält till orienteringsförbundet. Karta över området tillgänglig.",
     :en
     "The Finnish Orienteering Federation has been informed. A map of the area available."},
    :tags          {:fi []},
    :name
    {:fi "Suunnistusalue",
     :se "Orienteringsområde",
     :en "Orienteering area"},
    :type-code     4510,
    :main-category 4000,
    :status        "active",
    :sub-category  4500,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   4240
   {:description
    {:fi "Katettu laskettelurinne.",
     :se
     "Slalombacke med tak. Höjdskillnad och längd i karakteristika.",
     :en
     "Covered ski slope. Height and length specified in attributes."},
    :tags          {:fi []},
    :name
    {:fi "Lasketteluhalli",
     :se "Slalomhall",
     :en "Downhill skiing hall"},
    :type-code     4240,
    :main-category 4000,
    :status        "active",
    :sub-category  4200,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :ski-service?          {:priority 0},
     :altitude-difference   {:priority 1},
     :toilet?               {:priority 0},
     :area-m2               {:priority 0},
     :equipment-rental?     {:priority 0},
     :school-use?           {:priority 0},
     :route-length-km       {:priority 1}}},
   2270
   {:description
    {:fi "Yksi tai useampi squash-kenttä.",
     :se
     "En eller flera squashplaner. Antalet planer i karakteristika.",
     :en
     "One or more squash courts. Number of courts specified in 'attribute data'."},
    :tags
    {:fi ["squash" "squash-kenttä" "squashkenttä" "squashhalli"]},
    :name          {:fi "Squash-halli", :se "Squashhall", :en "Squash hall"},
    :type-code     2270,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m                {:priority 1},
     :surface-material        {:priority 1},
     :surface-material-info   {:priority 0},
     :kiosk?                  {:priority 0},
     :stand-capacity-person   {:priority 0},
     :free-use?               {:priority 0},
     :tennis-courts-count     {:priority 0},
     :field-length-m          {:priority 0},
     :badminton-courts-count  {:priority 0},
     :toilet?                 {:priority 0},
     :area-m2                 {:priority 1},
     :field-width-m           {:priority 0},
     :scoreboard?             {:priority 0},
     :floorball-fields-count  {:priority 0},
     :squash-courts-count     {:priority 1},
     :volleyball-fields-count {:priority 0},
     :school-use?             {:priority 0}}},
   4210
   {:description
    {:fi "Pysyvästi lajiin varustettu, katettu curlingrata.",
     :se "Curlingbana med tak och permanent utrustning för grenen.",
     :en "Covered track permanently equipped for curling."},
    :tags          {:fi ["curlinghalli" "curling-halli" "curling-rata"]},
    :name          {:fi "Curlingrata", :se "Curlingbana", :en "Curling sheet"},
    :type-code     4210,
    :main-category 4000,
    :status        "active",
    :sub-category  4200,
    :geometry-type "Point",
    :props
    {:kiosk?                {:priority 0},
     :surface-material      {:priority 1},
     :toilet?               {:priority 0},
     :surface-material-info {:priority 0},
     :area-m2               {:priority 1},
     :fields-count          {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   301
   {:description
    {:fi "Päiväsaikainen levähdyspaikka retkeilijöille.",
     :se "Viloplats för vandrare under dagtid.",
     :en "Daytime rest stop for hikers."},
    :tags          {:fi ["taukopaikka"]},
    :name
    {:fi "Laavu, kota tai kammi",
     :se "Vindskydd eller kåta",
     :en "Lean-to, goahti (Lapp tent shelter) or 'kammi' earth lodge"},
    :type-code     301,
    :main-category 0,
    :status        "active",
    :sub-category  3,
    :geometry-type "Point",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :toilet?                           {:priority nil},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   111
   {:description
    {:fi
     "Luonnonsuojelualueita, joiden perustamisesta ja tarkoituksesta on säädetty lailla. Pinta-ala väh. 1000 ha. Metsähallitus tietolähde.",
     :se
     "Naturskyddsområden med lagstadgad status och uppgift. Areal minst 1000 ha. Källa: Forststyrelsen",
     :en
     "Nature conservation areas whose establishment and purpose are based on legislation. Min. area 1,000 ha. Source of information  Metsähallitus."},
    :tags          {:fi []},
    :name
    {:fi "Kansallispuisto", :se "Nationalpark", :en "National park"},
    :type-code     111,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   4630
   {:description
    {:fi
     "Hiihtokilpailujen järjestämiseen soveltuva kisakeskus, jossa on esimerkiksi lähtö- ja maalialue, huoltotilat ja riittävä latuverkosto.",
     :se "Start- och målområden, serviceutrymmen, spårsystem.",
     :en "Start and finish area, service premises. Tracks."},
    :tags          {:fi ["hiihtostadion"]},
    :name
    {:fi "Kilpahiihtokeskus",
     :se "Skidtävlingscentrum",
     :en "Ski competition centre"},
    :type-code     4630,
    :main-category 4000,
    :status        "active",
    :sub-category  4600,
    :geometry-type "Point",
    :props
    {:automated-timing?      {:priority 0},
     :kiosk?                 {:priority 0},
     :stand-capacity-person  {:priority 0},
     :free-use?              {:priority 0},
     :ski-service?           {:priority 0},
     :finish-line-camera?    {:priority 0},
     :ski-track-traditional? {:priority 0},
     :route-width-m          {:priority 0},
     :toilet?                {:priority 0},
     :shower?                {:priority 0},
     :rest-places-count      {:priority 0},
     :changing-rooms?        {:priority 0},
     :lit-route-length-km    {:priority 0},
     :scoreboard?            {:priority 0},
     :sauna?                 {:priority 0},
     :loudspeakers?          {:priority 0},
     :accessibility-info     {:priority 0},
     :ski-track-freestyle?   {:priority 0},
     :school-use?            {:priority 0},
     :route-length-km        {:priority 0}}},
   4810
   {:description
    {:fi "Ulkoampumarata yhdelle tai useammalle lajille.",
     :se "Utomhusskjutbana för en eller flera grenar.",
     :en "Outdoor shooting range for one or more sports. "},
    :tags          {:fi ["ampumapaikka" "ammunta"]},
    :name
    {:fi "Ampumarata", :se "Skjutbana", :en "Open-air shooting range"},
    :type-code     4810,
    :main-category 4000,
    :status        "active",
    :sub-category  4800,
    :geometry-type "Point",
    :props
    {:kiosk?                   {:priority 0},
     :stand-capacity-person    {:priority 0},
     :free-use?                {:priority 0},
     :track-width-m            {:priority 0},
     :air-gun-shooting?        {:priority 0},
     :toilet?                  {:priority 0},
     :pistol-shooting?         {:priority 0},
     :shooting-positions-count {:priority 1},
     :area-m2                  {:priority 0},
     :rifle-shooting?          {:priority 0},
     :scoreboard?              {:priority 0},
     :loudspeakers?            {:priority 0},
     :shotgun-shooting?        {:priority 0},
     :free-rifle-shooting?     {:priority 0},
     :winter-usage?            {:priority 0},
     :ligthing?                {:priority 1},
     :accessibility-info       {:priority 0},
     :school-use?              {:priority 0},
     :track-length-m           {:priority 1}}},
   1540
   {:description
    {:fi
     "Pikaluisteluun varusteltu luistelurata. Käytössä talvikaudella.",
     :se "Bana för hastighetsåkning. Används under vintersäsongen.",
     :en "Track size and length specified in 'attribute data'."},
    :tags          {:fi ["luistelurata"]},
    :name
    {:fi "Pikaluistelurata",
     :se "Bana hastighetsåkning på skridsko",
     :en "Speed-skating track"},
    :type-code     1540,
    :main-category 1000,
    :status        "active",
    :sub-category  1500,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :track-width-m         {:priority 1},
     :toilet?               {:priority 0},
     :area-m2               {:priority 0},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :track-length-m        {:priority 1}}},
   5320
   {:description
    {:fi
     "Pääasiallisesti moottoripyöräilyä varten. Lajityypit mainitaan ominaisuustiedoissa.",
     :se
     "Huvudsakligen för motorcykelsport. Möjliga grenar anges i karakteristika.",
     :en
     "Mainly for motorcycling. Sports types detailed in 'attribute data'."},
    :tags          {:fi ["motocross"]},
    :name
    {:fi "Moottoripyöräilyalue",
     :se "Område för motorcykelsport",
     :en "Motorcycling area"},
    :type-code     5320,
    :main-category 5000,
    :status        "active",
    :sub-category  5300,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :automated-timing?          {:priority 0},
     :kiosk?                     {:priority 0},
     :stand-capacity-person      {:priority 0},
     :free-use?                  {:priority 0},
     :track-width-m              {:priority 0},
     :area-m2                    {:priority 0},
     :scoreboard?                {:priority 0},
     :loudspeakers?              {:priority 0},
     :ligthing?                  {:priority 0},
     :covered-stand-person-count {:priority 0},
     :school-use?                {:priority 0},
     :track-length-m             {:priority 1}}},
   3210
   {:description
    {:fi
     "Ulkona sijaitseva, uintiin tarkoitettu ja hoidettu vesistö tai uima-altaita/allas. Vedenpuhdistusjärjestelmä.",
     :se "Vattenreningssystem.",
     :en "Water treatment system."},
    :tags          {:fi ["uima-allas" "ulkoallas" "ulkouima-allas"]},
    :name          {:fi "Maauimala", :se "Utebassäng", :en "Open-air pool "},
    :type-code     3210,
    :main-category 3000,
    :status        "active",
    :sub-category  3200,
    :geometry-type "Point",
    :props
    {:pool-tracks-count     {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :pool-width-m          {:priority 0},
     :pool-min-depth-m      {:priority 0},
     :toilet?               {:priority 0},
     :swimming-pool-count   {:priority 1},
     :pool-water-area-m2    {:priority 1},
     :pool-length-m         {:priority 0},
     :other-pools-count     {:priority 0},
     :pool-max-depth-m      {:priority 0},
     :loudspeakers?         {:priority 0},
     :pool-temperature-c    {:priority 0},
     :school-use?           {:priority 0}}},
   4640
   {:description
    {:fi
     "Hiihdon opetteluun ja harjoitteluun tarkoitettu paikka erityisesti lapsille. Erilaisia harjoittelupaikkoja, kuten latuja, mäkiä ym.",
     :se "Träningsplats för skidåkning, teknikhaster mm.",
     :en
     "Ski training venue, an area of parallel short ski tracks for ski instruction, etc."},
    :tags          {:fi []},
    :name
    {:fi "Hiihtomaa", :se "Skidland", :en "Cross-country ski park"},
    :type-code     4640,
    :main-category 4000,
    :status        "active",
    :sub-category  4600,
    :geometry-type "Point",
    :props
    {:kiosk?                 {:priority 0},
     :free-use?              {:priority 0},
     :ski-service?           {:priority 0},
     :ski-track-traditional? {:priority 0},
     :toilet?                {:priority 0},
     :rest-places-count      {:priority 0},
     :lit-route-length-km    {:priority 0},
     :scoreboard?            {:priority 0},
     :equipment-rental?      {:priority 0},
     :loudspeakers?          {:priority 0},
     :accessibility-info     {:priority 0},
     :ski-track-freestyle?   {:priority 0},
     :school-use?            {:priority 0},
     :route-length-km        {:priority 0}}},
   1150
   {:description
    {:fi
     "Rullaluistelua, skeittausta, potkulautailua varten varustettu paikka. Ominaisuustiedoissa tarkemmat tiedot.",
     :se
     "Plats utrustad för rullskridskoåkning, skejtning och sparkcykelåkning. I preciseringsuppgifterna anges vad som är tillåtet på området.",
     :en
     "An area equipped  for roller-blading, skateboarding, kick scooting. Hobby  specified in 'attribute data'."},
    :tags
    {:fi ["ramppi" "skeittipaikka" "skeittipuisto" "skeittiparkki"]},
    :name
    {:fi "Skeitti-/rullaluistelupaikka",
     :se "Plats för skejtning/rullskridskoåkning",
     :en "Skateboarding/roller-blading rink "},
    :type-code     1150,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Point",
    :props
    {:area-m2               {:priority 1},
     :surface-material-info {:priority 0},
     :surface-material      {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   2310
   {:description
    {:fi
     "Yksittäinen yleisurheilun olosuhde, ei yu-hallin yhteydessä. Ominaisuustiedoissa kerrotaan suorituspaikat.",
     :se
     "Fristående, ej i anslutning till en friidrottshall. I karakteristika anges övningsplatserna (exemplifieras nedan)",
     :en
     "Stand-alone, not in an athletics hall. Venues specified under 'attribute data' (examples below)."},
    :tags          {:fi ["yleisurheilu" "juoksurata"]},
    :name
    {:fi "Yksittäinen yleisurheilun suorituspaikka",
     :se "Enstaka övningsplats för friidrott",
     :en "Stand-alone athletics venue"},
    :type-code     2310,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:height-m                   {:priority 0},
     :surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :free-use?                  {:priority 0},
     :javelin-throw-places-count {:priority 0},
     :sprint-track-length-m      {:priority 0},
     :inner-lane-length-m        {:priority 0},
     :discus-throw-places        {:priority 0},
     :hammer-throw-places-count  {:priority 0},
     :polevault-places-count     {:priority 0},
     :area-m2                    {:priority 0},
     :shotput-count              {:priority 0},
     :longjump-places-count      {:priority 0},
     :school-use?                {:priority 0},
     :highjump-places-count      {:priority 0}}},
   5210
   {:description   {:fi "Ilmailuun tarkoitettu alue.", :se "", :en nil},
    :tags
    {:fi
     ["lentäminen"
      "lento"
      "lentokone"
      "ilmailu"
      "ilmailualue"
      "lentokenttä"]},
    :name
    {:fi "Urheiluilmailualue",
     :se "Område för flygsport",
     :en "Sport aviation area"},
    :type-code     5210,
    :main-category 5000,
    :status        "active",
    :sub-category  5200,
    :geometry-type "Point",
    :props
    {:track-length-m        {:priority 0},
     :area-m2               {:priority 0},
     :surface-material-info {:priority nil},
     :surface-material      {:priority nil},
     :track-width-m         {:priority 0},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   2380
   {:description
    {:fi "Parkouria varten varustettu sisätila.",
     :se "Inomhusutrymme utrustat för parkour.",
     :en "Indoor space equipped for parkour. "},
    :tags          {:fi ["parkour"]},
    :name          {:fi "Parkour-sali", :se "Parkoursal", :en "Parkour hall"},
    :type-code     2380,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:height-m                 {:priority 0},
     :surface-material         {:priority 0},
     :surface-material-info    {:priority 0},
     :kiosk?                   {:priority 0},
     :free-use?                {:priority 0},
     :climbing-routes-count    {:priority 0},
     :climbing-wall-height-m   {:priority 0},
     :gymnastic-routines-count {:priority 0},
     :area-m2                  {:priority 1},
     :landing-places-count     {:priority 0},
     :school-use?              {:priority 0}}},
   103
   {:description
    {:fi
     "Taajamien reunoilla tai vyöhykkeittäin taajaman sisällä. 1-10 km päässä asuinalueista. Käytetään esim. kävelyyn, hiihtoon, lenkkeilyyn, uintiin jne. Yleensä yhden kunnan virkistysaluetarpeita palveleva, tarjoaa monipuolisia liikuntamahdollisuuksia. Voi olla metsää, suota, peltoja, luonnonmukaisia alueita ja puistomaisia osia. Kaavamerkintä VR.",
     :se
     "Området befinner sig i utkanten av tätorter eller i zoner inom tätorten. På 1-10 kilometers avstånd från bebyggelse. Friluftsområdet används för t.ex. promenader, skidning, joggning, simning. Serverar oftast friluftsaktiviteter för en kommun. Området erbjuder en stor variation av motions möjligheter. Området kan bestå av skog, kärr, åkrar, naturenliga områden och parkliknande delar. Planbeteckning VR.",
     :en
     "On the edge of population centres or zoned within population centres. 1-10 km from residential areas. Used for e.g. walks, skiing, jogging, swimming. Serves usually recreational needs within one municipality, offers versatile sports facilities. May include forest, swamp, fields, natural areas and park areas. Symbol VR."},
    :tags          {:fi ["puisto" "virkistysalue"]},
    :name
    {:fi "Ulkoilualue", :se "Friluftsområde", :en "Outdoor area"},
    :type-code     103,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :playground?                       {:priority 0},
     :area-km2                          {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   201
   {:description
    {:fi
     "Luonnonvesistössä sijaitseva virkistyskalastusta varten varustettu ja hoidettu kohde.",
     :se
     "Område eller en plats i ett naturligt vattendrag som ställts i ordning för fritidsfiske.",
     :en
     "Natural aquatic destination equipped and maintained for recreational fishing."},
    :tags          {:fi ["kalastusalue" "kalastuspaikka"]},
    :name
    {:fi "Kalastusalue/-paikka",
     :se "Område eller plats för fiske",
     :en "Fishing area/spot "},
    :type-code     201,
    :main-category 0,
    :status        "active",
    :sub-category  2,
    :geometry-type "Point",
    :props
    {:toilet?     {:priority nil},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   1220
   {:description
    {:fi
     "Hyvin varustelu yleisurheilukenttä, kiertävät radat (400 m). Kenttä, ratoja ja yleisurheilun suorituspaikkoja. Myös kisakäyttö mahdollinen.",
     :se
     "En plan, banor och träningsplatser för friidrott. Centrum, banor, ytbeläggningar samt träningsplatser med beskrivningar.",
     :en
     "Field, track and athletic venues/facilities. Centre, tracks, surfaces, venues specified in 'attribute data'. "},
    :tags
    {:fi
     ["keihäs"
      "keihäänheitto"
      "moukari"
      "pituushyppy"
      "juoksurata"
      "kolmiloikka"
      "seiväs"
      "kuula"
      "urheilukenttä"]},
    :name
    {:fi "Yleisurheilukenttä",
     :se "Friidrottsplan",
     :en "Athletics field"},
    :type-code     1220,
    :main-category 1000,
    :status        "active",
    :sub-category  1200,
    :geometry-type "Point",
    :props
    {:heating?                       {:priority 0},
     :surface-material               {:priority 1},
     :surface-material-info          {:priority 0},
     :automated-timing?              {:priority 0},
     :kiosk?                         {:priority 0},
     :stand-capacity-person          {:priority 0},
     :free-use?                      {:priority 0},
     :sprint-lanes-count             {:priority 0},
     :javelin-throw-places-count     {:priority 0},
     :finish-line-camera?            {:priority 0},
     :field-length-m                 {:priority 1},
     :circular-lanes-count           {:priority 0},
     :match-clock?                   {:priority 0},
     :sprint-track-length-m          {:priority 0},
     :inner-lane-length-m            {:priority 0},
     :discus-throw-places            {:priority 0},
     :hammer-throw-places-count      {:priority 0},
     :polevault-places-count         {:priority 0},
     :toilet?                        {:priority 0},
     :running-track-surface-material {:priority 1},
     :area-m2                        {:priority 1},
     :field-width-m                  {:priority 1},
     :scoreboard?                    {:priority 0},
     :shotput-count                  {:priority 0},
     :longjump-places-count          {:priority 0},
     :loudspeakers?                  {:priority 0},
     :ligthing?                      {:priority 1},
     :covered-stand-person-count     {:priority 0},
     :school-use?                    {:priority 0},
     :highjump-places-count          {:priority 0},
     :training-spot-surface-material {:priority 0}}},
   4411
   {:description
    {:fi
     "Maastopyöräilyyn tarkoitettu reitti, joka kulkee vaihtelevassa maastossa ja on merkitty maastoon. Reitti voi hyödyntää muita olemassa olevia ulkoilureittipohjia.",
     :se "Led avsedd framför allt för mountainbikar, märkt.",
     :en "Marked route intended especially for cross-country biking."},
    :tags          {:fi []},
    :name
    {:fi "Maastopyöräilyreitti",
     :se "Mountainbikeled",
     :en "Cross-country biking route"},
    :type-code     4411,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material                  {:priority 1},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :route-width-m                     {:priority 0},
     :toilet?                           {:priority 0},
     :rest-places-count                 {:priority 0},
     :lit-route-length-km               {:priority 1},
     :school-use?                       {:priority 0},
     :route-length-km                   {:priority 1}}},
   1140
   {:description
    {:fi "Parkouria varten varustettu alue.",
     :se "Område utrustat för parkour.",
     :en "An area equipped for parkour."},
    :tags          {:fi []},
    :name
    {:fi "Parkour-alue", :se "Parkourområde", :en "Parkour area"},
    :type-code     1140,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Point",
    :props
    {:area-m2               {:priority 1},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   4520
   {:description
    {:fi
     "Hiihtosuunnistukseen soveltuva alue, alueesta hiihtosuunnistuskartta saatavilla.",
     :se
     "Skidorienteringskarta över området, ej för sommarorientering.",
     :en
     "A ski orienteering map of the area available; no summer orienteering."},
    :tags          {:fi []},
    :name
    {:fi "Hiihtosuunnistusalue",
     :se "Skidorienteringsområde",
     :en "Ski orienteering area"},
    :type-code     4520,
    :main-category 4000,
    :status        "active",
    :sub-category  4500,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   107
   {:description
    {:fi
     "Matkailupalvelujen alueet ovat matkailu- ja lomakeskuksille, lomakylille, lomahotelleille ja muille vastaaville matkailua palveleville toiminnoille varattuja alueita, jotka sisältävät myös sisäiset liikenneväylät ja -alueet, alueen toimintoja varten tarpeelliset palvelut ja virkistysalueet sekä yhdyskuntateknisen huollon alueet. Kaavamerkintä RM.",
     :se
     "Områden med turisttjänster har reserverats för turist- och semestercentra, semesterbyar, semesterhotell och motsvarande aktörer. De har egna trafikleder och -områden samt områden för egna serviceenheter och egen infrastruktur för aktiviteter.  Planbeteckning RM.",
     :en
     "Area reserved for tourism and holiday centres, holiday villages, hotels, etc., also including internal traffic routes and areas; services and recreational areas needed for operations, as well as technical maintenance areas. Symbol RM."},
    :tags          {:fi ["ulkoilualue" "virkistysalue" "leirintäalue"]},
    :name
    {:fi "Matkailupalveluiden alue",
     :se "Område med turisttjänster",
     :en "Tourist services area"},
    :type-code     107,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   6110
   {:description
    {:fi "Ratsastukseen varustettu kenttä.",
     :se "Bana avsedd för ridning. Storlek i karakteristika.",
     :en
     "Field reserved for horseback riding. Size specified in 'attribute data'."},
    :tags          {:fi []},
    :name
    {:fi "Ratsastuskenttä", :se "Ridbana", :en "Equestrian field"},
    :type-code     6110,
    :main-category 6000,
    :status        "active",
    :sub-category  6100,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :toilet?               {:priority 0},
     :show-jumping?         {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0}}},
   1120
   {:description
    {:fi
     "Lähiliikuntapaikka on tarkoitettu päivittäiseen ulkoiluun ja liikuntaan. Se sijaitsee asutuksen välittömässä läheisyydessä, on pienimuotoinen ja alueelle on vapaa pääsy. Yleensä tarjolla on erilaisia suorituspaikkoja. Voi olla myös koulun tai päiväkodin piha, jos paikan käyttö on mahdollista myös muille kouluajan jälkeen.",
     :se
     "Näridrottsplats avsedd för daglig motion och dagligt friluftsliv. Den ligger i omedelbar närhet till bebyggelse, är relativt liten och har fritt tillträde. I regel erbjuds olika faciliteter för enstaka aktiviteter. I tilläggsinformation anges om det är en skolgård eller daghemsgård samt vilka enskilda faciliteter som finns.",
     :en
     "A neighbourhood sports area is intended for daily outdoor activities and exercise. It is a small area located in or near a residential area, with free public access. It usually provides different exercise/play facilities. It is specified in 'additional information' whether it is a school/nursery school yard, what facilities/equipment are offered, etc."},
    :tags          {:fi ["ässäkenttä" "monitoimikenttä" "monitoimikaukalo"]},
    :name
    {:fi "Lähiliikuntapaikka",
     :se "Näridrottsplats",
     :en "Neighbourhood sports area"},
    :type-code     1120,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Point",
    :props
    {:surface-material        {:priority 1},
     :surface-material-info   {:priority 0},
     :free-use?               {:priority 0},
     :fields-count            {:priority 0},
     :ice-rinks-count         {:priority 0},
     :area-m2                 {:priority 1},
     :ligthing?               {:priority 1},
     :accessibility-info      {:priority 0},
     :playground?             {:priority 0},
     :school-use?             {:priority 0},
     :exercise-machines-count {:priority 0}}},
   5340
   {:description
    {:fi "Pääasiallisesti karting-ajoa varten",
     :se "Huvudsakligen för karting.",
     :en "Mainly for karting."},
    :tags          {:fi []},
    :name          {:fi "Karting-rata", :se "Kartingbana", :en "Kart circuit"},
    :type-code     5340,
    :main-category 5000,
    :status        "active",
    :sub-category  5300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :automated-timing?     {:priority 0},
     :free-use?             {:priority 0},
     :track-width-m         {:priority 0},
     :area-m2               {:priority 0},
     :ligthing?             {:priority 0},
     :school-use?           {:priority 0},
     :track-length-m        {:priority 1}}},
   302
   {:description
    {:fi
     "Autiotupa, varaustupa, taukotupa, päivätupa. Yöpymis- ja levähdyspaikka retkeilijöille. Autiotupa avoin, varaustupa lukittu ja maksullinen. Päivätupa päiväkäyttöön.",
     :se
     "Övernattningsstuga, reserveringsstuga, raststuga, dagstuga. Övernattnings- och rastplats för vandrare. Övernattningsstugan öppen, reserveringsstugan låst och avgiftsbelagd. Dagstuga för bruk under dagen.",
     :en
     "Open hut, reservable hut, rest hut, day hut. Overnight resting place for hikers. An open hut is freely available; a reservable hut locked and subject to a charge. A day hut is for daytime use."},
    :tags          {:fi ["taukopaikka"]},
    :name          {:fi "Tupa", :se "Stuga", :en "Hut"},
    :type-code     302,
    :main-category 0,
    :status        "active",
    :sub-category  3,
    :geometry-type "Point",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :toilet?                           {:priority nil},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   4405
   {:description
    {:fi
     "Maastossa oleva retkeilyreitti, yleensä kauempana asutuksesta. Reitin varrella retkeilyn palveluita, esim. laavuja.",
     :se
     "Utflyktsled i terrängen, oftast längre borta från bebyggelse. Längs rutten friluftstjänster, t ex vindskydd. Sommar- och vinteranvändning i karakteristika.",
     :en
     "Natural hiking route, usually further away from residential areas. Provides hiking facilities, e.g. lean-to structures. Summer and winter use specified in 'attribute data'."},
    :tags          {:fi ["retkeily" "vaellus" "vaelluspolku"]},
    :name
    {:fi "Retkeilyreitti", :se "Utflyktsled", :en "Hiking route"},
    :type-code     4405,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material                  {:priority 0},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :route-width-m                     {:priority 0},
     :toilet?                           {:priority 0},
     :rest-places-count                 {:priority 0},
     :lit-route-length-km               {:priority 1},
     :accessibility-info                {:priority 0},
     :school-use?                       {:priority 0},
     :route-length-km                   {:priority 1}}},
   6120
   {:description
    {:fi "Kylmä tai lämmin katettu tila ratsastukseen.",
     :se "Kallt eller varmt takförsett utrymme för ridning.",
     :en "Cold or warm, covered space for horseback riding."},
    :tags          {:fi ["ratsastushalli" "maneesi"]},
    :name
    {:fi "Ratsastusmaneesi", :se "Ridmanege", :en "Riding manège"},
    :type-code     6120,
    :main-category 6000,
    :status        "active",
    :sub-category  6100,
    :geometry-type "Point",
    :props
    {:heating?              {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :toilet?               {:priority 0},
     :show-jumping?         {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0}}},
   1310
   {:description
    {:fi
     "Koripalloon varustettu kenttä, kiinteät tai siirrettävät koritelineet.  Minikenttä ja yhden korin kenttä lisätiedoissa.",
     :se
     "Plan utrustad för basket med fasta eller flyttbara ställningar. Miniplan och enkorgsplan i tilläggsupgifter.",
     :en
     "A field equipped for basketball, with fixed or movable apparatus. 'Mini-court' and 'one-basket court'  included in additional information. "},
    :tags          {:fi []},
    :name
    {:fi "Koripallokenttä", :se "Basketplan", :en "Basketball court"},
    :type-code     1310,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :match-clock?          {:priority 0},
     :fields-count          {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :scoreboard?           {:priority 0},
     :ligthing?             {:priority 1},
     :basketball-field-type {:priority 0},
     :school-use?           {:priority 0}}},
   202
   {:description
    {:fi "Telttailualue tai muu leiriytymiseen osoitettu paikka.",
     :se "Tältplats eller annat område ordnat för tältning.",
     :en "Camping site for tents or other encampment. "},
    :tags
    {:fi ["yöpyminen" "taukopaikka" "telttapaikka" "leirintäalue"]},
    :name
    {:fi "Telttailu ja leiriytyminen",
     :se "Tältning och läger",
     :en "Camping"},
    :type-code     202,
    :main-category 0,
    :status        "active",
    :sub-category  2,
    :geometry-type "Point",
    :props
    {:toilet?                           {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   1620
   {:description
    {:fi
     "Virallinen golfkenttä. Väylien määrä kysytään ominaisuustiedoissa.",
     :se "Officiell golfbana. Antalet hål anges i karakteristika.",
     :en
     "Official golf course. Number of holes included in 'attribute data'."},
    :tags          {:fi ["greeni" "puttialue" "range"]},
    :name          {:fi "Golfkenttä", :se "Golfbana", :en "Golf course"},
    :type-code     1620,
    :main-category 1000,
    :status        "active",
    :sub-category  1600,
    :geometry-type "Point",
    :props
    {:range?      {:priority 0},
     :holes-count {:priority 1},
     :ligthing?   {:priority 1},
     :toilet?     {:priority 0},
     :green?      {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   2250
   {:description
    {:fi
     "Skeittausta, rullaluistelua, bmx-pyöräilyä yms. varten varustettu halli.",
     :se
     "Hall utrustad för skejtning, rullskridskoåkning, bmx-åkning osv.",
     :en
     "An area for skateboarding, roller-blading, BMX biking, etc,. found in sports halls."},
    :tags          {:fi ["ramppi"]},
    :name
    {:fi "Skeittihalli", :se "Skateboardhall", :en "Indoor skatepark"},
    :type-code     2250,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m              {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :scoreboard?           {:priority 0},
     :school-use?           {:priority 0}}},
   2530
   {:description
    {:fi "Pikaluisteluun tarkoitettu halli. Koko >333 1 / 3 m",
     :se
     "Hall avsedd för hastighetsåkning på skridsko. Storlek > 333 1/3 m",
     :en "Hall intended for speed-skating. Size > 333.3 m."},
    :tags          {:fi ["jäähalli"]},
    :name
    {:fi "Pikaluisteluhalli",
     :se "Skridskohall",
     :en "Speed-skating hall"},
    :type-code     2530,
    :main-category 2000,
    :status        "active",
    :sub-category  2500,
    :geometry-type "Point",
    :props
    {:heating?              {:priority 0},
     :field-2-area-m2       {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :field-3-length-m      {:priority 0},
     :field-2-length-m      {:priority 0},
     :automated-timing?     {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-1-length-m      {:priority 0},
     :finish-line-camera?   {:priority 0},
     :match-clock?          {:priority 0},
     :field-1-width-m       {:priority 0},
     :field-3-width-m       {:priority 0},
     :field-2-width-m       {:priority 0},
     :ice-rinks-count       {:priority 1},
     :field-1-area-m2       {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :scoreboard?           {:priority 0},
     :loudspeakers?         {:priority 0},
     :field-3-area-m2       {:priority 0},
     :school-use?           {:priority 0}}},
   112
   {:description
    {:fi
     "Muut luonnonsuojelualueet kuin kansallispuistot ja luonnonpuistot. Vain luonnonsuojelualueet, joilla virkistyskäyttö on mahdollista. Esim. kuntien ja yksityisten maille perustetut suojelualueet. Kaavamerkintä S, SL.",
     :se
     "Andra naturskyddsområden än nationalparker och naturparker. Endast de naturskyddsområden där friluftsanvändning är möjlig. T ex skyddsområden som grundats på kommunal eller privat mark. Planbeteckning S, SL.",
     :en
     "Nature conservation areas other than national parks and natural parks. Only nature conservation areas with opportunities for recreation. E.g. protection areas established on municipal and private land. Symbol S, SL."},
    :tags          {:fi ["virkistysalue"]},
    :name
    {:fi "Muu luonnonsuojelualue, jolla on virkistyspalveluita",
     :se "Annat naturskyddsområde med rekreationstjänster",
     :en "Other nature conservation area with recreational services"},
    :type-code     112,
    :main-category 0,
    :status        "active",
    :sub-category  1,
    :geometry-type "Polygon",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :area-km2                          {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   2130
   {:description
    {:fi
     "Painonnostoon ja nyrkkeilyyn varustettu kuntoilutila. Nyrkkeilykehä.",
     :se
     "Utrustad för tyngdlyftning och boxning. Storleken anges i karakteristika.",
     :en
     "Equipped for weightlifting and boxing. Size specified in 'attribute data'."},
    :tags          {:fi ["kuntosali" "kuntoilu" "painonnosto" "voimanosto"]},
    :name
    {:fi "Voimailusali",
     :se "Styrketräningssal",
     :en "Weight training hall "},
    :type-code     2130,
    :main-category 2000,
    :status        "active",
    :sub-category  2100,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :kiosk?                     {:priority 0},
     :free-use?                  {:priority 0},
     :group-exercise-rooms-count {:priority 0},
     :tatamis-count              {:priority 0},
     :area-m2                    {:priority 1},
     :wrestling-mats-count       {:priority 0},
     :boxing-rings-count         {:priority 0},
     :weight-lifting-spots-count {:priority 0},
     :school-use?                {:priority 0},
     :exercise-machines-count    {:priority 0}}},
   3220
   {:description
    {:fi
     "Uintiin varusteltu hoidettu ranta. Aukioloaikoina valvottu ja pukukopit löytyvät.",
     :se
     "Bevakad under öppethållandetider, omklädningshytter o dyl utrustning. Vinterbad i karakteristika.",
     :en
     "Supervised during opening hours; includes changing rooms and other facilities. "},
    :tags          {:fi ["uimapaikka"]},
    :name          {:fi "Uimaranta", :se "Badstrand", :en "Supervised beach"},
    :type-code     3220,
    :main-category 3000,
    :status        "active",
    :sub-category  3200,
    :geometry-type "Point",
    :props
    {:kiosk?                            {:priority 0},
     :free-use?                         {:priority 0},
     :pier?                             {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :beach-length-m                    {:priority 1},
     :toilet?                           {:priority 0},
     :shower?                           {:priority 0},
     :changing-rooms?                   {:priority 0},
     :eu-beach?                         {:priority 0},
     :sauna?                            {:priority 0},
     :other-platforms?                  {:priority 0},
     :school-use?                       {:priority 0}}},
   5330
   {:description
    {:fi "Suuri rata-autoiluun tarkoitettu moottoriurheilupaikka.",
     :se "Stor motorsportplats avsedd för bankörning.",
     :en "Large motor sports venue for formula racing."},
    :tags          {:fi ["autourheilu"]},
    :name
    {:fi "Moottorirata", :se "Motorbana", :en "Formula race track"},
    :type-code     5330,
    :main-category 5000,
    :status        "active",
    :sub-category  5300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :automated-timing?     {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :finish-line-camera?   {:priority 0},
     :track-width-m         {:priority 0},
     :scoreboard?           {:priority 0},
     :loudspeakers?         {:priority 0},
     :ligthing?             {:priority 0},
     :school-use?           {:priority 0},
     :track-length-m        {:priority 1}}},
   4230
   {:description
    {:fi "Lumilautailua varten rakennettu halli.",
     :se
     "Tunnel avsedd för snowboardåkning. Olika användningsmöjligheter i tilläggsinformation.",
     :en
     "Tunnel intended for snowboarding. Different uses specified in additional information."},
    :tags          {:fi ["laskettelu"]},
    :name
    {:fi "Lumilautatunneli",
     :se "Snowboardtunnel",
     :en "Snowboarding tunnel"},
    :type-code     4230,
    :main-category 4000,
    :status        "active",
    :sub-category  4200,
    :geometry-type "Point",
    :props
    {:height-m              {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :ski-service?          {:priority 0},
     :altitude-difference   {:priority 1},
     :route-width-m         {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 0},
     :equipment-rental?     {:priority 0},
     :school-use?           {:priority 0},
     :route-length-km       {:priority 1}}},
   4320
   {:description
    {:fi
     "Mäkihyppyyn ja kilpailukäyttöön soveltuva mäki. Jää-, keramiikka- tai muovilatu. Vähintään pienmäki, K-piste min 75 m.",
     :se
     "Is-, keramik- eller plastspår. K-punkt samt sommar- och vinteranvändning i karakteristika. Minimikrav: en liten backe med k-punkt på 75 m eller mera.",
     :en
     "Ice, ceramic or plastic track. Summer and winter use specified in attributes, along with K point, etc. Minimum normal hill, K point minimum 75 m."},
    :tags          {:fi ["mäkihyppy" "hyppyri"]},
    :name          {:fi "Hyppyrimäki", :se "Hoppbacke", :en "Ski jumping hill"},
    :type-code     4320,
    :main-category 4000,
    :status        "active",
    :sub-category  4300,
    :geometry-type "Point",
    :props
    {:skijump-hill-type     {:priority 1},
     :lifts-count           {:priority 0},
     :plastic-outrun?       {:priority 0},
     :summer-usage?         {:priority 0},
     :free-use?             {:priority 0},
     :ski-service?          {:priority 0},
     :skijump-hill-material {:priority 0},
     :k-point               {:priority 1},
     :toilet?               {:priority 0},
     :p-point               {:priority 0},
     :inruns-material       {:priority 0},
     :school-use?           {:priority 0}}},
   3130
   {:description
    {:fi
     "Monipuolinen uimala kuntoutus-, virkistys- tai hyvinvointipalveluilla. Vesipinta-ala ja allasmäärät/tyypit ominaisuuksiin.",
     :se
     "Mångsidig badinrättning med rehabiliterings- och rekreationstjänster. Vattenareal samt antal och typ av bassänger i karakteristika.",
     :en
     "Versatile spa with rehabilitation or wellness services. Water volume and number/types of pools specified in 'attribute data'."},
    :tags          {:fi []},
    :name          {:fi "Kylpylä", :se "Badinrättning", :en "Spa"},
    :type-code     3130,
    :main-category 3000,
    :status        "active",
    :sub-category  3100,
    :geometry-type "Point",
    :props
    {:pool-tracks-count   {:priority 0},
     :kiosk?              {:priority 0},
     :free-use?           {:priority 0},
     :pool-width-m        {:priority 0},
     :pool-min-depth-m    {:priority 0},
     :toilet?             {:priority 0},
     :swimming-pool-count {:priority 1},
     :pool-water-area-m2  {:priority 1},
     :pool-length-m       {:priority 1},
     :other-pools-count   {:priority 0},
     :pool-max-depth-m    {:priority 0},
     :accessibility-info  {:priority 0},
     :pool-temperature-c  {:priority 0},
     :school-use?         {:priority 0}}},
   3110
   {:description
    {:fi
     "Halli, jossa on yksi tai useampia uima-altaita. Altaiden määrä ja vesipinta-ala kysytään ominaisuustiedoissa.",
     :se
     "Hall med en eller flera simbassänger. Antalet bassänger och vattenareal anges i karakteristika.",
     :en
     "Hall with one or several swimming pools. Number of pools and water surface area is requested in 'attribute data'."},
    :tags          {:fi []},
    :name
    {:fi "Uimahalli",
     :se "Simhall",
     :en "Public indoor swimming pool"},
    :type-code     3110,
    :main-category 3000,
    :status        "active",
    :sub-category  3100,
    :geometry-type "Point",
    :props
    {:pool-tracks-count     {:priority 0},
     :automated-timing?     {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :finish-line-camera?   {:priority 0},
     :match-clock?          {:priority 0},
     :pool-width-m          {:priority 0},
     :pool-min-depth-m      {:priority 0},
     :toilet?               {:priority 0},
     :swimming-pool-count   {:priority 1},
     :pool-water-area-m2    {:priority 1},
     :scoreboard?           {:priority 0},
     :pool-length-m         {:priority 1},
     :other-pools-count     {:priority 0},
     :pool-max-depth-m      {:priority 0},
     :loudspeakers?         {:priority 0},
     :accessibility-info    {:priority 0},
     :pool-temperature-c    {:priority 0},
     :school-use?           {:priority 0}}},
   203
   {:description
    {:fi
     "Kohteessa on veneilyyn liittyviä palveluita. Palveluvarustus kuvattava.",
     :se "Tjänster för båtfarare.Precisering i karakteristika.",
     :en "Facilities related to boating. Specififed in 'attributes'."},
    :tags          {:fi ["satama" "laituri"]},
    :name
    {:fi "Veneilyn palvelupaikka",
     :se "Serviceplats för båtfarare",
     :en "Boating services"},
    :type-code     203,
    :main-category 0,
    :status        "active",
    :sub-category  2,
    :geometry-type "Point",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :boat-launching-spot?              {:priority 0},
     :toilet?                           {:priority 0},
     :pier?                             {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   4620
   {:description
    {:fi
     "Vähintään kansallisen tason kilpailujen järjestämiseen soveltuva ampumahiihtokeskus. Ampumahiihtokeskuksessa useita ampumapaikkoja ja latuverkosto.",
     :se "Tillräckligt stort för åtminstone nationella tävlingar.",
     :en "For minimum national level competitions."},
    :tags          {:fi ["ampumapaikka"]},
    :name
    {:fi "Ampumahiihtokeskus",
     :se "Skidskyttecentrum",
     :en "Biathlon centre"},
    :type-code     4620,
    :main-category 4000,
    :status        "active",
    :sub-category  4600,
    :geometry-type "Point",
    :props
    {:automated-timing?        {:priority 0},
     :kiosk?                   {:priority 0},
     :stand-capacity-person    {:priority 0},
     :free-use?                {:priority 0},
     :ski-service?             {:priority 0},
     :finish-line-camera?      {:priority 0},
     :ski-track-traditional?   {:priority 0},
     :route-width-m            {:priority 0},
     :toilet?                  {:priority nil},
     :shower?                  {:priority 0},
     :rest-places-count        {:priority 0},
     :changing-rooms?          {:priority 0},
     :shooting-positions-count {:priority 1},
     :lit-route-length-km      {:priority 0},
     :scoreboard?              {:priority 0},
     :sauna?                   {:priority 0},
     :loudspeakers?            {:priority 0},
     :accessibility-info       {:priority 0},
     :ski-track-freestyle?     {:priority 0},
     :school-use?              {:priority 0},
     :route-length-km          {:priority 0}}},
   5360
   {:description
    {:fi "Pääasiallisesti jokamiesajoa ja/tai rallicrossia varten.",
     :se "Huvudsakligen för allemanskörning och/eller rallycross.",
     :en "Mainly for everyman racing and/or rallycross."},
    :tags          {:fi []},
    :name
    {:fi "Jokamies- ja rallicross-rata",
     :se "Allemans- och rallycrossbana",
     :en "Everyman racing and rallycross track "},
    :type-code     5360,
    :main-category 5000,
    :status        "active",
    :sub-category  5300,
    :geometry-type "Point",
    :props
    {:surface-material           {:priority 1},
     :surface-material-info      {:priority 0},
     :automated-timing?          {:priority 0},
     :kiosk?                     {:priority 0},
     :stand-capacity-person      {:priority 0},
     :free-use?                  {:priority 0},
     :finish-line-camera?        {:priority 0},
     :track-width-m              {:priority 0},
     :toilet?                    {:priority 0},
     :area-m2                    {:priority 0},
     :scoreboard?                {:priority 0},
     :loudspeakers?              {:priority 0},
     :ligthing?                  {:priority 0},
     :covered-stand-person-count {:priority 0},
     :school-use?                {:priority 0},
     :track-length-m             {:priority 1}}},
   2290
   {:description
    {:fi "Petanque-peliin tarkoitettu halli.",
     :se
     "Hall avsedd för petanque. Storlek, antalet planer och utrustning i karakteristika.",
     :en "Hall intended for petanque"},
    :tags          {:fi ["petankki"]},
    :name
    {:fi "Petanque-halli", :se "Petanquehall", :en "Petanque Hall"},
    :type-code     2290,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m              {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :free-use?             {:priority 0},
     :fields-count          {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :scoreboard?           {:priority 0},
     :school-use?           {:priority 0}}},
   2260
   {:description
    {:fi
     "Ensisijaisesti sulkapalloon tarkoitettu halli jossa sulkapalloilu on pääkäyttömuoto.",
     :se "Hall i första hand  avsedd för badminton.",
     :en "Hall intended primarily for badminton."},
    :tags          {:fi []},
    :name
    {:fi "Sulkapallohalli", :se "Badmintonhall", :en "Badminton hall"},
    :type-code     2260,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m                {:priority 1},
     :surface-material        {:priority 1},
     :surface-material-info   {:priority 0},
     :kiosk?                  {:priority 0},
     :stand-capacity-person   {:priority 0},
     :free-use?               {:priority 0},
     :tennis-courts-count     {:priority 0},
     :field-length-m          {:priority 0},
     :badminton-courts-count  {:priority 1},
     :toilet?                 {:priority 0},
     :area-m2                 {:priority 1},
     :field-width-m           {:priority 0},
     :scoreboard?             {:priority 0},
     :floorball-fields-count  {:priority 0},
     :squash-courts-count     {:priority 0},
     :volleyball-fields-count {:priority 0},
     :school-use?             {:priority 0}}},
   1160
   {:description
    {:fi
     "Pyöräilyä ja temppuilua varten varattu alue, esim. bmx-, pump track- tai dirt- pyöräilyalue.",
     :se
     "Avsett för cykelåkning och trick, t ex bmx- eller dirtåkning.",
     :en "For cycling and stunting, e.g. BMX or dirt-biking."},
    :tags          {:fi ["pumptrack" "bmx" "pump" "track"]},
    :name
    {:fi "Pyöräilyalue", :se "Cykelåkningsområde", :en "Cycling area"},
    :type-code     1160,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Point",
    :props
    {:ligthing?             {:priority 1},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :area-m2               {:priority 1},
     :school-use?           {:priority 0},
     :free-use?             {:priority 0}}},
   1210
   {:description
    {:fi
     "Yleisurheilun harjoitusalueeksi merkitään kohde, jossa on yleisurheilun harjoitteluun soveltuvia suorituspaikkoja, esim. kenttä, ratoja tai eri lajien suorituspaikkoja, mutta ei virallisen yleisurheilukentän kaikkia suorituspaikkoja.",
     :se
     "Platserna för olika aktiviteter anges i preciseringsuppgifter. Även enstaka aktiviteter.",
     :en
     "The various sports venues are specified in 'attribute data', also individual venues."},
    :tags
    {:fi
     ["keihäs"
      "keihäänheitto"
      "moukari"
      "pituushyppy"
      "juoksurata"
      "kolmiloikka"
      "seiväs"
      "kuula"
      "urheilukenttä"
      "yleisurheilukenttä"]},
    :name
    {:fi "Yleisurheilun harjoitusalue",
     :se "Träningsområde för friidrott",
     :en "Athletics training area"},
    :type-code     1210,
    :main-category 1000,
    :status        "active",
    :sub-category  1200,
    :geometry-type "Point",
    :props
    {:heating?                       {:priority 0},
     :surface-material               {:priority 1},
     :surface-material-info          {:priority 0},
     :free-use?                      {:priority 0},
     :sprint-lanes-count             {:priority 0},
     :javelin-throw-places-count     {:priority 0},
     :field-length-m                 {:priority 0},
     :circular-lanes-count           {:priority 0},
     :sprint-track-length-m          {:priority 0},
     :inner-lane-length-m            {:priority 0},
     :discus-throw-places            {:priority 0},
     :hammer-throw-places-count      {:priority 0},
     :polevault-places-count         {:priority 0},
     :toilet?                        {:priority 0},
     :running-track-surface-material {:priority 0},
     :area-m2                        {:priority 1},
     :field-width-m                  {:priority 0},
     :shotput-count                  {:priority 0},
     :longjump-places-count          {:priority 0},
     :ligthing?                      {:priority 1},
     :school-use?                    {:priority 0},
     :highjump-places-count          {:priority 0},
     :training-spot-surface-material {:priority 0}}},
   5140
   {:description
    {:fi "Rakennettu pysyvästi vesihiihdolle. Vähintään laituri.",
     :se "Byggt för vattenskidåkning, permanent. Minimikrav: brygga.",
     :en
     "Permanent construction for water skiing. Minimum equipment  pier."},
    :tags          {:fi []},
    :name
    {:fi "Vesihiihtoalue",
     :se "Område för vattenskidåkning",
     :en "Water ski area"},
    :type-code     5140,
    :main-category 5000,
    :status        "active",
    :sub-category  5100,
    :geometry-type "Point",
    :props
    {:pier?       {:priority 0},
     :area-km2    {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   4310
   {:description
    {:fi
     "Mäkihypyn harjoitteluun rakennettu mäki. K-piste ominaisuustietoihin, materiaalit, kesä- ja talvikäyttö ominaisuuksiin. ",
     :se
     "K-punkt, material samt sommar- och vinteranvändning i karakteristika.",
     :en
     "K point in 'attribute data'; materials, summer and winter use specified in attributes. "},
    :tags          {:fi ["mäkihyppy" "hyppyri" "hyppyrimäki"]},
    :name
    {:fi "Harjoitushyppyrimäki",
     :se "Träningshoppbacke",
     :en "Ski jumping hill for training"},
    :type-code     4310,
    :main-category 4000,
    :status        "active",
    :sub-category  4300,
    :geometry-type "Point",
    :props
    {:skijump-hill-type     {:priority 1},
     :lifts-count           {:priority 0},
     :plastic-outrun?       {:priority 0},
     :summer-usage?         {:priority 0},
     :free-use?             {:priority 0},
     :ski-service?          {:priority 0},
     :skijump-hill-material {:priority 0},
     :k-point               {:priority 1},
     :toilet?               {:priority 0},
     :p-point               {:priority 0},
     :inruns-material       {:priority 0},
     :school-use?           {:priority 0}}},
   207
   {:description
    {:fi
     "Opastuspiste on ulkoilureitin, virkistysalueen tai muun liikuntapaikan yhteydessä oleva lisätieto. Paikassa voi olla esimerkiksi opastustaulu ja kartta tai laajempi palvelupiste. Opastuspiste-merkintää voi käyttää myös ilmoittamaan reitin lähtöpisteen.",
     :se
     "I närheten av friluftsbanor, friluftsområden och andra motionsplatser, finns det oftast en informationspunkt som innehåller extra information om området. Det kan t.ex. finnas en karta och informationstavla eller en större servicepunkt. Ruttens startpunkt kan också betäcknas med hjälp av informationspunkten",
     :en
     "Information point or starting point of a route, recreation area etc. Map of the route or area included, possibly also parking area."},
    :tags          {:fi ["info" "opastaulu" "infopiste"]},
    :name          {:fi "Opastuspiste", :se "Informationspunkt", :en "Info"},
    :type-code     207,
    :main-category 0,
    :status        "active",
    :sub-category  2,
    :geometry-type "Point",
    :props
    {:parking-place?                    {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :toilet?                           {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   1130
   {:description
    {:fi
     "Kuntoilulaitteita, voimailulaitteita, ns. \"ulkokuntosali\". Kohde voi olla osa liikuntapuistoa tai liikuntareitin varrella oleva kuntoilupaikka.",
     :se
     "Konditions- och styrketräningsanordningar osv, en \"utomhusträningssal\". Kan vara en del av en idrottspark.",
     :en
     "Contains fitness and gym equipment, etc., \"outdoor gym\". May be part of a sports park."},
    :tags
    {:fi
     ["kuntoilulaite"
      "ulkokuntoilupiste"
      "kuntoilupiste"
      "kuntoilupaikka"]},
    :name
    {:fi "Ulkokuntoilupaikka",
     :se "Konditionspark för utomhusaktiviteter",
     :en "Fitness training place"},
    :type-code     1130,
    :main-category 1000,
    :status        "active",
    :sub-category  1100,
    :geometry-type "Point",
    :props
    {:area-m2                 {:priority 1},
     :exercise-machines-count {:priority 1},
     :ligthing?               {:priority 1},
     :surface-material-info   {:priority 0},
     :surface-material        {:priority 1},
     :playground?             {:priority 0},
     :school-use?             {:priority 0},
     :free-use?               {:priority 0}}},
   5120
   {:description
    {:fi "Rakennettu pysyvästi purjehdukselle.",
     :se "Byggt för segling, permanent.",
     :en "Permanent construction for sailing."},
    :tags          {:fi []},
    :name
    {:fi "Purjehdusalue", :se "Seglingsområde", :en "Sailing area"},
    :type-code     5120,
    :main-category 5000,
    :status        "active",
    :sub-category  5100,
    :geometry-type "Point",
    :props
    {:area-km2          {:priority 0},
     :pier?             {:priority 0},
     :boat-places-count {:priority 0},
     :school-use?       {:priority 0},
     :free-use?         {:priority 0}}},
   4110
   {:description   {:fi "", :se "", :en "-"},
    :tags          {:fi ["rinne" "laskettelu" "laskettelurinne"]},
    :name
    {:fi "Laskettelun suorituspaikka",
     :se "",
     :en "Ski slopes and downhill ski resorts"},
    :type-code     4110,
    :main-category 4000,
    :status        "active",
    :sub-category  4100,
    :geometry-type "Point",
    :props
    {:lifts-count             {:priority 1},
     :freestyle-slope?        {:priority 0},
     :kiosk?                  {:priority 0},
     :free-use?               {:priority 0},
     :ski-service?            {:priority 0},
     :longest-slope-m         {:priority 0},
     :snowpark-or-street?     {:priority 0},
     :max-vertical-difference {:priority 1},
     :toilet?                 {:priority 0},
     :halfpipe-count          {:priority 0},
     :equipment-rental?       {:priority 0},
     :slopes-count            {:priority 1},
     :shortest-slope-m        {:priority 0},
     :toboggan-run?           {:priority 0},
     :jumps-count             {:priority 0},
     :lit-slopes-count        {:priority 1},
     :accessibility-info      {:priority 0},
     :school-use?             {:priority 0}}},
   4452
   {:description
    {:fi
     "Merkitty vesireitti, ei kuitenkaan veneväylä. Vesireitti on reittiehdotus-tyyppinen suositeltu vesiretkeilyyn soveltuva reitti. Esim. kirkkovenesoutureitti.",
     :se
     "Märkt vattenled, endast ruttförslag t ex för kyrkbåtsrodd, inte som farled för småbåtar.",
     :en
     "Marked water route, not a navigation channel. Route suggestions included. E.g., route for \"church rowing boats\"."},
    :tags          {:fi ["kanootti" "kajakki" "melonta"]},
    :name
    {:fi "Vesiretkeilyreitti",
     :se "Utflyktsled i vattendrag",
     :en "Water route"},
    :type-code     4452,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:may-be-shown-in-excursion-map-fi? {:priority 0},
     :route-length-km                   {:priority 1},
     :rest-places-count                 {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   5370
   {:description
    {:fi "Pääasiallisesti jääspeedwayta varten.",
     :se "Huvudsakligen för isracing.",
     :en "Speedway mainly for ice racing."},
    :tags          {:fi ["speedway"]},
    :name
    {:fi "Jääspeedway-rata",
     :se "Isracingbana",
     :en "Ice speedway track"},
    :type-code     5370,
    :main-category 5000,
    :status        "active",
    :sub-category  5300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :automated-timing?     {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :finish-line-camera?   {:priority 0},
     :track-width-m         {:priority 0},
     :scoreboard?           {:priority 0},
     :loudspeakers?         {:priority 0},
     :ligthing?             {:priority 0},
     :school-use?           {:priority 0},
     :track-length-m        {:priority 1}}},
   2240
   {:description
    {:fi
     "Ensisijaisesti salibandyyn tarkoitettu halli, jossa salibandy on pääkäyttömuoto.",
     :se
     "Hall i första hand avsedd för innebandy. Antalet planer samt ytmaterial i karakteristika.",
     :en
     "Hall primarily intended for floorball. Number of courts and surface material specified in 'attribute data'."},
    :tags          {:fi ["sähly"]},
    :name
    {:fi "Salibandyhalli", :se "Innebandyhall", :en "Floorball hall"},
    :type-code     2240,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m               {:priority 0},
     :surface-material       {:priority 1},
     :surface-material-info  {:priority 0},
     :kiosk?                 {:priority 0},
     :stand-capacity-person  {:priority 0},
     :free-use?              {:priority 0},
     :field-length-m         {:priority 1},
     :badminton-courts-count {:priority 0},
     :toilet?                {:priority 0},
     :area-m2                {:priority 1},
     :field-width-m          {:priority 1},
     :scoreboard?            {:priority 0},
     :floorball-fields-count {:priority 1},
     :school-use?            {:priority 0}}},
   2510
   {:description
    {:fi
     "Harjoitusjäähalli, kenttien määrä ominaisuustietoihin. Lämmitys, pukukopit yms. ominaisuustietoihin.",
     :se
     "Antalet planer och omklädningshytter, uppvärmning osv anges i karakteristika.",
     :en
     "Number of fields, heating, changing rooms, etc., specified in 'attribute data'. "},
    :tags          {:fi ["jäähalli"]},
    :name
    {:fi "Harjoitusjäähalli",
     :se "Övningsishall",
     :en "Training ice arena"},
    :type-code     2510,
    :keywords      {:fi ["Jäähalli"], :en [], :se []},
    :main-category 2000,
    :status        "active",
    :sub-category  2500,
    :geometry-type "Point",
    :props
    {:heating?              {:priority 0},
     :field-2-area-m2       {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :field-3-length-m      {:priority 0},
     :field-2-length-m      {:priority 0},
     :automated-timing?     {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-1-length-m      {:priority 0},
     :finish-line-camera?   {:priority 0},
     :match-clock?          {:priority 0},
     :field-1-width-m       {:priority 0},
     :field-3-width-m       {:priority 0},
     :field-2-width-m       {:priority 0},
     :ice-rinks-count       {:priority 1},
     :field-1-area-m2       {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :curling-lanes-count   {:priority 0},
     :scoreboard?           {:priority 0},
     :loudspeakers?         {:priority 0},
     :field-3-area-m2       {:priority 0},
     :school-use?           {:priority 0}}},
   1640
   {:description
    {:fi "Ratagolfliiton hyväksymät ratagolf-/minigolfradat.",
     :se
     "Bangolf / minigolf, enligt Finlands Bangolfförbundets regler.",
     :en
     "A course built for miniature golf, accepted by the Ratagolf Union"},
    :tags          {:fi ["minigolf"]},
    :name          {:fi "Ratagolf", :se "Bangolf", :en "Minigolf course"},
    :type-code     1640,
    :main-category 1000,
    :status        "active",
    :sub-category  1600,
    :geometry-type "Point",
    :props
    {:range?      {:priority 0},
     :holes-count {:priority 1},
     :ligthing?   {:priority 1},
     :green?      {:priority 0},
     :toilet?     {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   1380
   {:description
    {:fi "Rullakiekon pelaamiseen varustettu kenttä.",
     :se "Plan utrustad för inlinehockey.",
     :en "Field equipped for roller hockey."},
    :tags          {:fi ["rullakiekko"]},
    :name
    {:fi "Rullakiekkokenttä",
     :se "Inlinehockeyplan",
     :en "Roller hockey field"},
    :type-code     1380,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :match-clock?          {:priority 0},
     :fields-count          {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0}}},
   4451
   {:description
    {:fi
     "Erityisesti melontaan tarkoitettu vesistöreitti. Reitistä on laadittu reittikuvaus ja maastossa löytyy opasteita (esim. rantautumispaikoista). Reittiehdotus-tyyppinen, ei navigointiin.",
     :se
     "Särskilt för paddling, märkt med ruttförslag, ej för navigering.",
     :en
     "Marked route particularly for canoeing. Route suggestions are not intended for navigation."},
    :tags          {:fi ["kanootti" "kajakki"]},
    :name          {:fi "Melontareitti", :se "Paddlingsled", :en "Canoe route"},
    :type-code     4451,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:route-length-km                   {:priority 1},
     :rest-places-count                 {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   4403
   {:description
    {:fi
     "Jalkaisin tapahtuvaan ulkoiluun tarkoitettu reitti. Suhteellisen leveä ja helppokulkuinen reitti, yleensä valaistu ja pinnoitettu.",
     :se
     "Promenadled. Relativt bred och lättilgänglig, eventuellt belyst och asfalterad.",
     :en
     "Route intended for outdoor pedestrian activities. Relatively broad and passable. Potentially lit and surfaced."},
    :tags          {:fi ["ulkoilu" "kävely" "ulkoilureitti" "kävelyreitti"]},
    :name
    {:fi "Kävelyreitti/ulkoilureitti",
     :se "Promenadled/friluftsled",
     :en "Walking route/outdoor route"},
    :type-code     4403,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material                  {:priority 1},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :outdoor-exercise-machines?        {:priority 0},
     :route-width-m                     {:priority 0},
     :toilet?                           {:priority 0},
     :rest-places-count                 {:priority 0},
     :lit-route-length-km               {:priority 1},
     :accessibility-info                {:priority 0},
     :school-use?                       {:priority 0},
     :route-length-km                   {:priority 1}}},
   5150
   {:description
    {:fi "Kilpailujen järjestäminen mahdollista.",
     :se "Möjligt att arrangera tävlingar.",
     :en "Competitions possible."},
    :tags          {:fi ["melonta" "kajakki" "kanootti" "melontakeskus"]},
    :name
    {:fi "Koskimelontakeskus",
     :se "Centrum för paddling",
     :en "Rapid canoeing centre"},
    :type-code     5150,
    :main-category 5000,
    :status        "active",
    :sub-category  5100,
    :geometry-type "Point",
    :props
    {:equipment-rental?   {:priority 0},
     :pier?               {:priority 0},
     :altitude-difference {:priority 0},
     :school-use?         {:priority 0},
     :free-use?           {:priority 0}}},
   1630
   {:description
    {:fi
     "Golfia varten varustettu sisäharjoittelutila. Voi olla useita erilaisia suorituspaikkoja.",
     :se
     "Övningsutrymme byggt för golf. Storlek i karakteristika. Länk till Anläggningar  för inomhusidrott.",
     :en
     "Training space built for golf. Size specified in 'attribute data'. Link to 'indoor sport facilities'"},
    :tags          {:fi ["greeni" "puttialue"]},
    :name
    {:fi "Golfin harjoitushalli",
     :se "Övningshall för golf",
     :en "Golf training hall"},
    :type-code     1630,
    :main-category 1000,
    :status        "active",
    :sub-category  1600,
    :geometry-type "Point",
    :props
    {:height-m    {:priority 1},
     :holes-count {:priority 0},
     :kiosk?      {:priority 0},
     :free-use?   {:priority 0},
     :toilet?     {:priority 0},
     :area-m2     {:priority 1},
     :green?      {:priority 0},
     :school-use? {:priority 0},
     :range?      {:priority 0}}},
   2370
   {:description
    {:fi "Kiipeilyyn varustettu sisätila. Myös boulderointipaikat.",
     :se
     "Inomhusutrymme utrustat för klättring, även platser för bouldering.",
     :en
     "Indoor space equipped for climbing. Also bouldering venues."},
    :tags          {:fi ["kiipeilyseinä"]},
    :name
    {:fi "Sisäkiipeilyseinä",
     :se "Klättervägg inomhus",
     :en "Indoor climbing wall"},
    :type-code     2370,
    :main-category 2000,
    :status        "active",
    :sub-category  2300,
    :geometry-type "Point",
    :props
    {:surface-material       {:priority 0},
     :surface-material-info  {:priority 0},
     :kiosk?                 {:priority 0},
     :free-use?              {:priority 0},
     :climbing-routes-count  {:priority 0},
     :climbing-wall-height-m {:priority 1},
     :area-m2                {:priority 1},
     :climbing-wall-width-m  {:priority 1},
     :school-use?            {:priority 0}}},
   1340
   {:description
    {:fi
     "Palloiluun tarkoitettu kenttä. Hiekka, nurmi, hiekkatekonurmi tms., koko vaihtelee. Yksi tai useampi palloilulaji mahdollista. Kevyt poistettava kate mahdollinen.",
     :se
     "Plan avsedd för bollspel.Sand, konstgräs på sand el dyl, storleken varierar. En eller flera bollspelsgrenar möjliga.",
     :en
     "A field intended for ball games. Sand, grass, artificial turf, etc., size varies. One or more types of ball games possible."},
    :tags          {:fi []},
    :name          {:fi "Pallokenttä", :se "Bollplan", :en "Ball field"},
    :type-code     1340,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:heating?              {:priority 0},
     :surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :kiosk?                {:priority 0},
     :stand-capacity-person {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :fields-count          {:priority 0},
     :toilet?               {:priority 0},
     :changing-rooms?       {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0},
     :light-roof?           {:priority 0}}},
   1610
   {:description
    {:fi
     "Golfin harjoittelua varten varustettu alue. Yksi tai useampia suorituspaikkoja. Ulkona.",
     :se
     "Träningsplats för golf, en eller flera övningsplatser. Utomhus.",
     :en "One or more areas for golf training. Outdoors."},
    :tags          {:fi ["greeni" "puttialue" "range"]},
    :name
    {:fi "Golfin harjoitusalue",
     :se "Träningsområde för golf",
     :en "Golf training area"},
    :type-code     1610,
    :main-category 1000,
    :status        "active",
    :sub-category  1600,
    :geometry-type "Point",
    :props
    {:holes-count {:priority 1},
     :ligthing?   {:priority 1},
     :toilet?     {:priority 0},
     :range?      {:priority 0},
     :green?      {:priority 0},
     :school-use? {:priority 0},
     :free-use?   {:priority 0}}},
   4421
   {:description
    {:fi
     "Reittitoimituksella hyväksytty, virallinen moottorikelkkailureitti.",
     :se "En officiell rutt som godkänts genom en ruttexpedition.",
     :en
     "Officially approved route (in compliance with Act 670/1991)."},
    :tags          {:fi []},
    :name
    {:fi "Moottorikelkkareitti",
     :se "Snöskoterled",
     :en "Official snowmobile route"},
    :type-code     4421,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:rest-places-count                 {:priority 0},
     :route-width-m                     {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :route-length-km                   {:priority 1},
     :school-use?                       {:priority 0},
     :free-use?                         {:priority 0}}},
   2220
   {:description
    {:fi
     "Monitoimihalli on suuri liikuntatila, joka on merkittävä monien lajien kilpailupaikka ja kooltaan suurempi kuin 5000 m2.",
     :se "Större tävlingsplats för ett flertal grenar, >= 5 000 m2.",
     :en
     "Significant competition venue for various sports, >=5000 m2."},
    :tags          {:fi ["liikuntahalli" "urheilutalo" "urheiluhalli"]},
    :name
    {:fi "Monitoimihalli/areena",
     :se "Allaktivitetshall/multiarena",
     :en "Multipurpose hall/arena"},
    :type-code     2220,
    :main-category 2000,
    :status        "active",
    :sub-category  2200,
    :geometry-type "Point",
    :props
    {:height-m                       {:priority 1},
     :surface-material               {:priority 1},
     :basketball-fields-count        {:priority 0},
     :surface-material-info          {:priority 0},
     :automated-timing?              {:priority 0},
     :kiosk?                         {:priority 0},
     :stand-capacity-person          {:priority 0},
     :free-use?                      {:priority 0},
     :sprint-lanes-count             {:priority 0},
     :javelin-throw-places-count     {:priority 0},
     :tennis-courts-count            {:priority 0},
     :field-length-m                 {:priority 1},
     :circular-lanes-count           {:priority 0},
     :match-clock?                   {:priority 0},
     :sprint-track-length-m          {:priority 0},
     :inner-lane-length-m            {:priority 0},
     :discus-throw-places            {:priority 0},
     :badminton-courts-count         {:priority 0},
     :hammer-throw-places-count      {:priority 0},
     :polevault-places-count         {:priority 0},
     :toilet?                        {:priority 0},
     :gymnastics-space?              {:priority 0},
     :running-track-surface-material {:priority 0},
     :area-m2                        {:priority 1},
     :field-width-m                  {:priority 1},
     :scoreboard?                    {:priority 0},
     :shotput-count                  {:priority 0},
     :longjump-places-count          {:priority 0},
     :football-fields-count          {:priority 0},
     :floorball-fields-count         {:priority 0},
     :squash-courts-count            {:priority 0},
     :loudspeakers?                  {:priority 0},
     :accessibility-info             {:priority 0},
     :handball-fields-count          {:priority 0},
     :volleyball-fields-count        {:priority 0},
     :climbing-wall?                 {:priority 0},
     :school-use?                    {:priority 0},
     :highjump-places-count          {:priority 0}}},
   1320
   {:description
    {:fi
     "Lentopalloon varustettu kenttä. Kiinteät lentopallotelineet.",
     :se "Plan utrustad för volleyboll. Fasta volleybollställningar.",
     :en
     "A field equipped for volleyball. Fixed volleyball apparatus."},
    :tags          {:fi []},
    :name
    {:fi "Lentopallokenttä",
     :se "Volleybollplan",
     :en "Volleyball court"},
    :type-code     1320,
    :main-category 1000,
    :status        "active",
    :sub-category  1300,
    :geometry-type "Point",
    :props
    {:surface-material      {:priority 1},
     :surface-material-info {:priority 0},
     :free-use?             {:priority 0},
     :field-length-m        {:priority 1},
     :fields-count          {:priority 0},
     :toilet?               {:priority 0},
     :area-m2               {:priority 1},
     :field-width-m         {:priority 1},
     :ligthing?             {:priority 1},
     :school-use?           {:priority 0}}},
   4402
   {:description
    {:fi
     "Talvikaudella hiihtoa varten ylläpidetty reitti. Hiihtotyylit kerrotaan ominaisuustiedoissa. Sama reitti voi olla myös kuntorata kesäisin.",
     :se
     "Led avsedd för skidåkning. Ej sommaranvändning och -underhåll. Åkstilar anges i karakteristika.",
     :en
     "Route intended for skiing. Not in use and unmaintained in summer. Ski styles provided in 'attribute data'."},
    :tags          {:fi ["hiihto" "hiihtolatu"]},
    :name          {:fi "Latu", :se "Skidspår", :en "Ski track"},
    :type-code     4402,
    :main-category 4000,
    :status        "active",
    :sub-category  4400,
    :geometry-type "LineString",
    :props
    {:surface-material                  {:priority 1},
     :surface-material-info             {:priority 0},
     :free-use?                         {:priority 0},
     :may-be-shown-in-excursion-map-fi? {:priority 0},
     :outdoor-exercise-machines?        {:priority 0},
     :ski-track-traditional?            {:priority 0},
     :route-width-m                     {:priority 0},
     :toilet?                           {:priority 0},
     :rest-places-count                 {:priority 0},
     :shooting-positions-count          {:priority 0},
     :lit-route-length-km               {:priority 1},
     :ski-track-freestyle?              {:priority 0},
     :school-use?                       {:priority 0},
     :route-length-km                   {:priority 1}}}})

(def unknown
  {:name          {:fi "Ei tietoa" :se "Unknown" :en "Unknown"}
   :type-code     -1
   :main-category -1
   :sub-category  -1})

(def by-main-category (group-by :main-category (vals all)))
(def by-sub-category (group-by :sub-category (vals all)))
