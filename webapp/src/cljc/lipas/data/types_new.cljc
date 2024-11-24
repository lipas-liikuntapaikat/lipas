(ns lipas.data.types-new
  "Type codes went through a major overhaul in the summer of 2024. This
  namespace represents the outcome of the changes."
  (:require
   [lipas.utils :as utils]))

(def main-categories
  {0
   {:type-code 0,
    :name
    {:fi "Virkistyskohteet ja palvelut",
     :se "Rekreationsanläggningar och tjänster",
     :en "Recreational destinations and services"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p10416"
      "http://www.yso.fi/onto/koko/p37350"],
     :service-classes
     ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2"]}},
   1000
   {:type-code 1000,
    :name
    {:fi "Ulkokentät ja liikuntapuistot",
     :se "Utomhusplaner och idrottsparker",
     :en "Outdoor fields and sports parks"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p10416"
      "http://www.yso.fi/onto/koko/p69291"
      "http://www.yso.fi/onto/koko/p67276"],
     :service-classes
     ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2"]}},
   2000
   {:type-code 2000,
    :name
    {:fi "Sisäliikuntatilat",
     :se "Anläggningar för inomhusidrott",
     :en "Indoor sports facilities"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p10416"
      "http://www.yso.fi/onto/koko/p69660"],
     :service-classes
     ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.1"]}},
   3000
   {:type-code 3000,
    :name
    {:fi "Vesiliikuntapaikat",
     :se "Anläggningar för vattenidrott",
     :en "Water sports facilities"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p10416"
      "http://www.yso.fi/onto/koko/p18621"],
     :service-classes
     ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.1"
      "http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2"]}},
   4000
   {:type-code 4000,
    :name
    {:fi "Maastoliikuntapaikat",
     :se "Anläggningar för terrängidrott",
     :en "Cross-country sports facilities"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p10416"
      "http://www.yso.fi/onto/koko/p12424"],
     :service-classes
     ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2"]}},
   5000
   {:type-code 5000,
    :name
    {:fi "Veneily, ilmailu ja moottoriurheilu",
     :se "Anläggningar för båtsport, flygsport och motorsport",
     :en "Boating, aviation and motor sports"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p34055"
      "http://www.yso.fi/onto/koko/p36083"
      "http://www.yso.fi/onto/koko/p18298"
      "http://www.yso.fi/onto/koko/p75772"
      "http://www.yso.fi/onto/koko/p31773"],
     :service-classes
     ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.2"]}},
   6000
   {:type-code 6000,
    :name
    {:fi "Eläinurheilualueet",
     :se "Anläggningar för djursport",
     :en "Animal sports areas"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p10416"
      "http://www.yso.fi/onto/koko/p8973"],
     :service-classes
     ["http://uri.suomi.fi/codelist/ptv/ptvserclass2/code/P27.1"]}},
   7000
   {:type-code 7000,
    :name
    {:fi "Huoltorakennukset",
     :se "Servicebyggnader",
     :en "Maintenance/service buildings"},
    :ptv {:ontology-urls [], :service-classes []}}})

(def sub-categories
  {2100
   {:type-code 2100,
    :name
    {:fi "Kuntoilukeskukset ja liikuntasalit",
     :se "Konditionsidrottscentra och idrottssalar",
     :en "Fitness centres and sports halls"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p30560"
      "http://www.yso.fi/onto/koko/p85878"]},
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
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p8460"]},
    :main-category "4000"},
   2200
   {:type-code 2200,
    :name
    {:fi "Liikuntahallit", :se "Idrottshallar", :en "Sports halls"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p33522"]},
    :main-category "2000"},
   1
   {:type-code 1,
    :name
    {:fi "Virkistys- ja retkeilyalueet",
     :se "Rekreations- och friluftsområden",
     :en "Recreational and outdoor areas "},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p37350"
      "http://www.yso.fi/onto/koko/p33303"]},
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
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p32315"]},
    :main-category "4000"},
   1300
   {:type-code 1300,
    :name {:fi "Pallokentät", :se "Bollplaner", :en "Ball games courts"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p75504"]},
    :main-category "1000"},
   6100
   {:type-code 6100,
    :name {:fi "Hevosurheilu", :se "Hästsport", :en "Equestrian sports"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p35523"]},
    :main-category "6000"},
   4700
   {:type-code 4700,
    :name
    {:fi "Kiipeilypaikat",
     :se "Klättringsplatser",
     :en "Climbing venues"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p4261"]},
    :main-category "4000"},
   3100
   {:type-code 3100,
    :name
    {:fi "Uima-altaat, hallit ja kylpylät",
     :se "Simbassänger, hallar och badinrättningar",
     :en "Indoor swimming pools, halls and spas"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p1459"
      "http://www.yso.fi/onto/koko/p11070"
      "http://www.yso.fi/onto/koko/p35112"]},
    :main-category "3000"},
   2500
   {:type-code 2500,
    :name {:fi "Jäähallit", :se "Ishallar", :en "Ice-skating arenas"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p11376"]},
    :main-category "2000"},
   1200
   {:type-code 1200,
    :name
    {:fi "Yleisurheilukentät ja -paikat",
     :se "Planer och platser för friidrott",
     :en "Athletics fields and venues"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p85607"]},
    :main-category "1000"},
   5300
   {:type-code 5300,
    :name
    {:fi "Moottoriurheilualueet",
     :se "Områden för motorsport",
     :en "Motor sports areas"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p31773"]},
    :main-category "5000"},
   1600
   {:type-code 1600,
    :name {:fi "Golfkentät", :se "Golfbanor", :en "Golf courses"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p32648"]},
    :main-category "1000"},
   1500
   {:type-code 1500,
    :name
    {:fi "Jääurheilualueet ja luonnonjäät",
     :se "Isidrottsområden och naturisar",
     :en "Ice sports areas and sites with natural ice"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p75572"]},
    :main-category "1000"},
   6200
   {:type-code 6200,
    :name {:fi "Koiraurheilu", :se "Hundsport", :en "Dog sports"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p72589"]},
    :main-category "6000"},
   3200
   {:type-code 3200,
    :name
    {:fi "Maauimalat ja uimarannat",
     :se "Utebassänger och badstränder",
     :en "Open air pools and beaches"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p32279"
      "http://www.yso.fi/onto/koko/p76123"
      "http://www.yso.fi/onto/koko/p13459"]},
    :main-category "3000"},
   2
   {:type-code 2,
    :name
    {:fi "Retkeilyn palvelut",
     :se "Utflyktstjänster",
     :en "Hiking facilities"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p36881"]},
    :main-category "0"},
   1100
   {:type-code 1100,
    :name
    {:fi "Lähiliikunta ja liikuntapuistot",
     :se "Närmotion och idrottsparker",
     :en "Neighbourhood sports facilities and parks "},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p84486"
      "http://www.yso.fi/onto/koko/p69291"]},
    :main-category "1000"},
   4100
   {:type-code 4100,
    :name
    {:fi "Laskettelurinteet ja rinnehiihtokeskukset",
     :se "Slalombackar och alpina skidcentra",
     :en "Ski slopes and downhill ski resorts"},
    :ptv
    {:ontology-urls
     ["http://www.yso.fi/onto/koko/p10549"
      "http://www.yso.fi/onto/koko/p84378"
      "http://www.yso.fi/onto/koko/p4432"]},
    :main-category "4000"},
   5100
   {:type-code 5100,
    :name
    {:fi "Veneurheilupaikat",
     :se "Platser för båtsport",
     :en "Boating sports facilities"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p75772"]},
    :main-category "5000"},
   2600
   {:type-code 2600,
    :name
    {:fi "Keilahallit ja biljardisalit",
     :se "Bowlinghallar och biljardsalonger",
     :en "Bowling alleys and billiard halls"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p9812"]},
    :main-category "2000"},
   2300
   {:type-code 2300,
    :name
    {:fi "Yksittäiset lajikohtaiset sisäliikuntapaikat",
     :se "Enstaka grenspecifika anläggningar för inomhusidrott",
     :en "Indoor venues for various sports "},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p66287"]},
    :main-category "2000"},
   4300
   {:type-code 4300,
    :name {:fi "Hyppyrimäet", :se "Hoppbackar", :en "Ski jumping hills"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p72457"]},
    :main-category "4000"},
   4500
   {:type-code 4500,
    :name
    {:fi "Suunnistusalueet",
     :se "Orienteringsområden",
     :en "Orienteering areas"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p5355"]},
    :main-category "4000"},
   :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p18298"]},
   4600
   {:type-code 4600,
    :name
    {:fi "Maastohiihtokeskukset",
     :se "Längdåkningscentra",
     :en "Cross-country ski resorts"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p75831"]},
    :main-category "4000"},
   4800
   {:type-code 4800,
    :name
    {:fi "Ampumaurheilupaikat",
     :se "Sportskytteplatser",
     :en "Shooting sports facilities"},
    :ptv {:ontology-urls ["http://www.yso.fi/onto/koko/p25336"]},
    :main-category "4000"}})

(def all
  {1530
 {:description
  {:fi
   "Luisteluun, jääkiekkoon, kaukalopalloon, curlingiin tai muuhun jääurheiluun tarkoitettu kaukalo. Käytössä talvikaudella.",
   :se
   "Rink avsedd för skridskoåkning, ishockey, rinkbandy osv. Används under vintersäsongen.",
   :en
   "Rink intended for ice-skating, ice hockey, rink bandy, etc."},
  :tags {:fi ["jääkiekkokaukalo"]},
  :name {:fi "Kaukalo", :se "Rink", :en "Rink"},
  :type-code 1530,
  :main-category 1000,
  :status "active",
  :sub-category 1500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :ice-rinks-count {:priority 80},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :changing-rooms-m2 {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :light-roof? {:priority 70}}},
 1520
 {:description
  {:fi
   "Luisteluun tarkoitettu luonnonmukainen kenttä. Jäädytetään käyttökuntoon talvikaudelle.",
   :se
   "Plan avsedd för skridskoåkning. Används under vintersäsongen.",
   :en "Field intended for ice-skating."},
  :tags {:fi []},
  :name
  {:fi "Luistelukenttä",
   :se "Skridskobana",
   :en "Ice-skating field"},
  :type-code 1520,
  :main-category 1000,
  :status "active",
  :sub-category 1500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :fields-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :changing-rooms-m2 {:priority 70},
   :customer-service-point? {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :light-roof? {:priority 70}}},
 2320
 {:description
  {:fi
   "Pysyvästi voimisteluun varustettu tila. Voimistelutilassa on erilaisia kiinteitä voimistelutelineitä ja -rakenteita (esim. volttimonttu, rekki tai trampoliini). Myös cheerleadingin ja sirkusharjoittelun olosuhteet luetaan voimistelutiloiksi. Tarkempi olosuhdetieto kerrotaan lisätiedoissa.",
   :se "Permanent utrustning för att träna redskapsgymnastik.",
   :en "Space permanently equipped for artistic gymnastics."},
  :tags {:fi ["monttu" "rekki" "nojapuut"]},
  :name
  {:fi "Voimistelutila",
   :se "Utrymme för redskapsgymnastik",
   :en "Artistic gymnastics facility"},
  :type-code 2320,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :sport-specification {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :space-divisible {:priority 70},
   :gymnastic-routines-count {:priority 80},
   :area-m2 {:priority 90},
   :landing-places-count {:priority 80},
   :school-use? {:priority 40}}},
 6130
 {:description
  {:fi
   "Pysyvästi esteratsastukseen varusteltu kenttä tai alue ulkona.",
   :se "Bana med permanent utrustning för banhoppning",
   :en "Field permanently equipped for show jumping. Outdoors."},
  :tags {:fi ["ratsastuskenttä"]},
  :name
  {:fi "Esteratsastuskenttä/-alue",
   :se "Bana för banhoppning",
   :en "Show jumping field"},
  :type-code 6130,
  :main-category 6000,
  :status "active",
  :sub-category 6100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :ligthing? {:priority 60},
   :school-use? {:priority 40}}},
 1395
 {:description
  {:fi
   "Yksi tai useampi pöytätennispöytä ulkona. Pöytätennispöydän tulee olla sijoitettu niin, että pelaamiseen on riittävä tila pöydän ympärillä. Pöydän tulee olla pelikäyttöön soveltuva.",
   :se
   "Ett eller flera bordtennisbord utomhus. Bordet är lämpligt för spel och bör vara placerat så att det finns tillräckligt utrymme för spel.",
   :en
   "One or more outdoor table tennis tables in the same area. The table must be positioned so that there is enough space for playing. The table must be suitable for the sport."},
  :tags {:fi ["pöytätennis" "pingis" "ping pong"]},
  :name
  {:fi "Pöytätennisalue",
   :se "Område med bordtennisbord",
   :en "Table tennis area"},
  :type-code 1395,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :table-tennis-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :water-point {:priority 70},
   :lighting-info {:priority 50}}},
 6210
 {:description
  {:fi
   "Koiran koulutukseen, agilityyn tai muuhun harjoittamiseen varattu ulkoalue.",
   :se
   "Område reserverat för hundträning, agility eller annan hundhobby.",
   :en
   "Area reserved for dog training, agility or other dog sports."},
  :tags {:fi ["agility" "koirakenttä"]},
  :name
  {:fi "Koiraurheilualue",
   :se "Område för hundsport",
   :en "Dog sports area"},
  :type-code 6210,
  :main-category 6000,
  :status "active",
  :sub-category 6200,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :ligthing? {:priority 60},
   :track-length-m {:priority 90}}},
 1370
 {:description
  {:fi
   "Tennikseen tarkoitettu kenttä. Mahdollinen lyöntiseinä ja kentän pintamateriaali merkitään lisätietoihin.",
   :se
   "En eller flera tennisbanor på samma område. Antalet banor, ytmaterial mm i karakteristika. Även uppgift om slagväggen.",
   :en
   "One or more tennis courts in the same area. Number of courts, surface material, etc. specified in properties, including information about a potential hit wall."},
  :tags {:fi ["tenniskenttä"]},
  :name
  {:fi "Tenniskenttä",
   :se "Område med tennisbanor",
   :en "Tennis court area"},
  :type-code 1370,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :training-wall? {:priority 80},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :light-roof? {:priority 70}}},
 1360
 {:description
  {:fi
   "Pesäpalloon tarkoitettu kenttä. Jos kentän yhteydessä on katsomoita, lisätään kentän nimeen stadion-sana. Vähintään kansallisen tason pelipaikka. Pintamateriaali on esim. hiekka, hiekkatekonurmi tai muu synteettinen päällyste. Kentän koko on vähintään 50 x 100 m.",
   :se
   "En bobollsplan, kan ha flera läktare. Minimikrav: spelplats på nationell nivå. Sand, konstgräs med sand / annan syntetisk beläggning. >50 x 100 m.",
   :en
   "Finnish baseball field, may include stands. Can host at least national-level games. Sand, artificial turf / other synthetic surface, > 50 x 100 m. "},
  :tags {:fi ["pesäpallostadion"]},
  :name
  {:fi "Pesäpallokenttä", :se "Bobollsplan", :en "Baseball field"},
  :type-code 1360,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:heating? {:priority 70},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :customer-service-point? {:priority 70},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :school-use? {:priority 40}}},
 110
 {:description
  {:fi
   "Erämaalailla perustetut alueet pohjoisimmassa Lapissa. Metsähallitus tietolähteenä.",
   :se
   "Grundade enligt ödemarkslagen, i nordligaste Lappland. Källa: Forststyrelsen.",
   :en
   "Areas located in northernmost Lapland, established based on the Wildeness Act (1991/62). Source of information  Metsähallitus."},
  :tags {:fi []},
  :name
  {:fi "Erämaa-alue", :se "Vildmarksområden", :en "Wilderness area"},
  :type-code 110,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 2360
 {:description
  {:fi "Pysyvästi käytössä oleva ampumarata sisätiloissa.",
   :se "Permanent skjutbana inomhus.",
   :en "Permanent indoor shooting range."},
  :tags {:fi ["ilmakivääri" "ilma-ase" "ammunta"]},
  :name
  {:fi "Sisäampumarata",
   :se "Inomhusskjutbana",
   :en "Indoor shooting range"},
  :type-code 2360,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 79},
   :surface-material-info {:priority 78},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :air-gun-shooting? {:priority 80},
   :pistol-shooting? {:priority 80},
   :shooting-positions-count {:priority 80},
   :area-m2 {:priority 90},
   :rifle-shooting? {:priority 80},
   :free-rifle-shooting? {:priority 80},
   :school-use? {:priority 40},
   :track-length-m {:priority 80}}},
 5310
 {:description
  {:fi
   "Useiden eri moottoriurheilun lajien suorituspaikkoja, huoltotilat olemassa.",
   :se
   "Platser för flera olika motorsportgrenar, serviceutrymmen finns.",
   :en
   "Venues for various motor sports; service premises available."},
  :tags {:fi []},
  :name
  {:fi "Moottoriurheilukeskus",
   :se "Centrum för motorsport",
   :en "Motor sports centre"},
  :type-code 5310,
  :main-category 5000,
  :status "active",
  :sub-category 5300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :track-length-m {:priority 90}}},
 1560
 {:description
  {:fi
   "Alamäkiluistelua varten vuosittain rakennettava rata. Käytössä talvikaudella.",
   :se "Permanent bana byggd för utförsåkning.",
   :en "Permanent track built for downhill skating. "},
  :tags {:fi ["luistelu" "alamäkiluistelu"]},
  :name
  {:fi "Alamäkiluistelurata",
   :se "Skridskobana för utförsåkning",
   :en "Downhill skating track"},
  :type-code 1560,
  :main-category 1000,
  :status "active",
  :sub-category 1500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :lifts-count {:priority 70},
   :free-use? {:priority 40},
   :track-width-m {:priority 90},
   :altitude-difference {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :lighting-info {:priority 50},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :track-length-m {:priority 90}}},
 205
 {:description
  {:fi
   "Rantautumiseen osoitettu paikka, ei järjestettyjä palveluita.",
   :se
   "Plats som anvisats för ilandstigning, inga ordnade tjänster.",
   :en "Place intended for landing by boat, no services provided."},
  :tags {:fi ["laituri" "taukopaikka"]},
  :name
  {:fi "Rantautumispaikka",
   :se "Ilandstigningsplats",
   :en "Boat dock"},
  :type-code 205,
  :main-category 0,
  :status "deprecated",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:toilet? {:priority 80},
   :boat-launching-spot? {:priority 90},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :pier? {:priority 80},
   :school-use? {:priority 70},
   :free-use? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 2150
 {:description
  {:fi
   "Muun rakennuksen yhteydessä oleva avoin liikuntatila, joka sopii monipuolisesti erilaisten liikuntamuotojen harrastamiseen. Salin liikuntapinta-ala vaihtelee tyypillisesti alle 300 neliöstä noin 750 neliöön. Esim. koulurakennuksessa sijaitseva liikuntasali.",
   :se
   "En idrottssal som är ansluten till en annan byggnad. Storlek och höjd anges i karakteristika.",
   :en
   "A gymnastics hall connected to another building. Size and height specified in properties."},
  :tags {:fi ["jumppasali" "voimistelusali"]},
  :name
  {:fi "Liikuntasali", :se "Idrottssal", :en "Gymnastics hall"},
  :type-code 2150,
  :main-category 2000,
  :status "active",
  :sub-category 2100,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 89},
   :basketball-fields-count {:priority 80},
   :surface-material-info {:priority 88},
   :free-use? {:priority 40},
   :tennis-courts-count {:priority 80},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :badminton-courts-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :space-divisible {:priority 70},
   :gymnastics-space? {:priority 80},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :futsal-fields-count {:priority 80},
   :football-fields-count {:priority 80},
   :floorball-fields-count {:priority 80},
   :handball-fields-count {:priority 80},
   :volleyball-fields-count {:priority 80},
   :spinning-hall? {:priority 80},
   :school-use? {:priority 40}}},
 2210
 {:description
  {:fi
   "Liikuntahalli on itsenäinen rakennus, jossa voi olla useita liikuntatiloja tai osiin jaettavissa oleva pääsali.",
   :se
   "Idrottshall med utrymmen för flera idrottsgrenar eller med ett i mindre sektioner indelbart huvudidrottsutrymme. Storleken varierar mellan ca 750 och 4999 m2. Inkluderar inomhusaktivitetsparker med faciliteter för flera fysiska aktiviteter.",
   :en
   "Building containing facilities for various sports or the main sports area can be split into smaller sections. Hall size varies between app. 750 - 4999 square meters. Includes indoor activity parks with facilities for multiple physical activities."},
  :tags {:fi ["urheilutalo" "urheiluhalli"]},
  :name {:fi "Liikuntahalli", :se "Idrottshall", :en "Sports hall "},
  :type-code 2210,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 89},
   :basketball-fields-count {:priority 80},
   :surface-material-info {:priority 88},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :sprint-lanes-count {:priority 80},
   :javelin-throw-places-count {:priority 80},
   :tennis-courts-count {:priority 80},
   :field-length-m {:priority 90},
   :circular-lanes-count {:priority 80},
   :match-clock? {:priority 70},
   :inner-lane-length-m {:priority 80},
   :discus-throw-places {:priority 80},
   :badminton-courts-count {:priority 80},
   :hammer-throw-places-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :padel-courts-count {:priority 80},
   :polevault-places-count {:priority 80},
   :group-exercise-rooms-count {:priority 80},
   :space-divisible {:priority 70},
   :toilet? {:priority 70},
   :gymnastics-space? {:priority 80},
   :running-track-surface-material {:priority 80},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :scoreboard? {:priority 70},
   :futsal-fields-count {:priority 80},
   :shotput-count {:priority 80},
   :longjump-places-count {:priority 80},
   :football-fields-count {:priority 80},
   :floorball-fields-count {:priority 80},
   :auxiliary-training-area? {:priority 80},
   :squash-courts-count {:priority 80},
   :customer-service-point? {:priority 70},
   :accessibility-info {:priority 40},
   :handball-fields-count {:priority 80},
   :volleyball-fields-count {:priority 80},
   :climbing-wall? {:priority 80},
   :school-use? {:priority 40},
   :highjump-places-count {:priority 80}}},
 101
 {:description
  {:fi
   "Sijaitsevat taajamissa, max 1 km asutuksesta. Toimivat kävely-, leikki-, oleskelu-, lenkkeily- ja pyöräilypaikkoina. Kaavamerkintä V tai VL. Esimerkkejä lähi- tai ulkoilupuistoista: leikkipuistot, liikennepuistot, perhepuistot, oleskelupuistot, keskuspuistot ja kirkkopuistot.",
   :se
   "I tätorter, i omedelbar närhet till bebyggelse. Avsedd för daglig användning. Plats för lek, vistelse och promenader. Planbeteckning VL. Till exempel en lekpark.",
   :en
   "In population centres, in or near residential areas. Intended for daily use. Used for play, recreation and walks. Plan symbol VL. E.g. a playground."},
  :tags {:fi ["puisto" "lähiliikuntapaikka"]},
  :name
  {:fi "Lähi-/ulkoilupuisto",
   :se "Närpark",
   :en "Neighbourhood park"},
  :type-code 101,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:playground? {:priority 90},
   :area-km2 {:priority 100},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :water-point {:priority 80},
   :toilet? {:priority 80}}},
 102
 {:description
  {:fi
   "Päivittäin käytettäviä alueita, max 1 km asunnoista. Toimivat kävely-, leikki-, oleskelu-, lenkkeily- ja pyöräilypaikkoina. Kevyt liikenne voi mennä ulkoilupuiston läpi. Voi sisältää puistoa, metsää, peltoa, niittyä, vesialuetta. Kaavamerkintä V tai VL.",
   :se
   "Områden avsedda för daglig använding, max på 1 kilometers avstånd från bebyggelse. Fungerar som ett område för promenader, lekar, vistelse, joggning och cykling. Lätt trafikled kan fara igenom friluftsparken. Området kan bestå av park, skog, åker, äng och vattenled. Planbeteckning V eller VL.",
   :en
   "Used daily, max. 1 km from residential areas. Intended for walks, play, recreation, jogging and cycling. There may be bicycle and pedestrian traffic across the park. May consist of park, forest, fields, meadows, bodies of water. Symbol V or VL."},
  :tags {:fi ["puisto"]},
  :name
  {:fi "Ulkoilupuisto", :se "Friluftspark", :en "Leisure park"},
  :type-code 102,
  :main-category 0,
  :status "deprecated",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:playground? {:priority 90},
   :area-km2 {:priority 100},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :school-use? {:priority 70},
   :free-use? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 7000
 {:description
  {:fi
   "Liikuntapaikan tai -paikkojen yhteydessä oleva, liikuntapaikan ylläpitoa tai käyttöä palveleva rakennus. Voi sisältää varastoja, pukuhuoneita, suihkutiloja yms.",
   :se "Servicebyggnader i anslutning till idrottsanläggningar.",
   :en
   "Maintenance buildings in connection with sports facilities."},
  :tags {:fi ["konesuoja" "huoltotila"]},
  :name
  {:fi "Huoltorakennukset",
   :se "Servicebyggnader",
   :en "Maintenance/service buildings"},
  :type-code 7000,
  :main-category 7000,
  :status "active",
  :sub-category 7000,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :ski-service? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shower? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :equipment-rental? {:priority 70},
   :sauna? {:priority 70},
   :school-use? {:priority 40}}},
 1110
 {:description
  {:fi
   "Liikuntapuisto on useita liikuntapaikkoja käsittävä liikunta-alue. Liikuntapuistossa voi olla esim. erilaisia kenttiä, uimaranta, kuntorata, monitoimihalli, leikkipuisto jne. koottuna samalle alueelle. Lipakseen tallennetaan sekä tieto liikuntapuistosta että yksittäiset liikuntapaikat, joita puisto sisältää. Liikuntapaikat lasketaan omiksi paikoikseen.",
   :se
   "En idrottspark är ett idrottsområde med flera idrottsplatser. Där kan finnas olika planer, badstrand, konditionsbana, allaktivitetshall, lekpark osv samlade på samma område. I Lipas lagras uppgifter om såväl idrottsparken som enstaka faciliteter som finns i parken. Varje motionsplats räknas som en plats.",
   :en
   "A sports park is an area including several sports facilities, e.g., different fields, beach, a jogging track, a multi-purpose hall, a playground. 'Lipas' contains information both on the sports park and the individual sports facilities found there. The sports facilities are listed as individual items in the classification."},
  :tags {:fi ["puisto" "lähiliikunta" "lähiliikuntapaikka"]},
  :name {:fi "Liikuntapuisto", :se "Idrottspark", :en "Sports park"},
  :type-code 1110,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Polygon",
  :props
  {:free-use? {:priority 50},
   :fields-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 60},
   :ligthing? {:priority 70},
   :accessibility-info {:priority 50},
   :playground? {:priority 80},
   :school-use? {:priority 50}}},
 3250
 {:description
  {:fi
   "Vesiurheilukeskuksessa on vesistössä sijaitsevia liikuntapalveluita tai palvelukokonaisuus, joka voi muodostua erilaisista veden päällä tai vedessä olevista suorituspaikoista tai -radoista."},
  :tags {:fi []},
  :name {:fi "Vesiurheilukeskus"},
  :type-code 3250,
  :main-category 3000,
  :status "active",
  :sub-category 3200,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :pier? {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shower? {:priority 70},
   :changing-rooms? {:priority 70},
   :pool-water-area-m2 {:priority 90},
   :sauna? {:priority 70},
   :customer-service-point? {:priority 70}}},
 6220
 {:description
  {:fi
   "Erityisesti koiraharrastusta, agilityä, koulutusta tms. varten varustettu halli.",
   :se
   "Hall som utrustats särskilt för hundhobby, agility, träning osv.",
   :en
   "Hall specifically equipped for dog sports, agility, training, etc."},
  :tags {:fi ["agility" "koirahalli"]},
  :name
  {:fi "Koiraurheiluhalli",
   :se "Hundsporthall",
   :en "Dog sports hall"},
  :type-code 6220,
  :main-category 6000,
  :status "active",
  :sub-category 6200,
  :geometry-type "Point",
  :props
  {:heating? {:priority 70},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :ligthing? {:priority 60}}},
 4530
 {:description
  {:fi
   "Pyöräillen tapahtuvaan suunnistamiseen, alueesta on pyöräsuunnistukseen soveltuva kartta.",
   :se "Karta över område som lämpar sig för cykelorientering.",
   :en "A map for mountain bike orienteering available."},
  :tags {:fi []},
  :name
  {:fi "Pyöräsuunnistusalue",
   :se "Cykelorienteringsområde",
   :en " Mountain bike orienteering area"},
  :type-code 4530,
  :main-category 4000,
  :status "deprecated",
  :sub-category 4500,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :school-use? {:priority 40},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 4720
 {:description
  {:fi
   "Merkitty luonnon kallio, jota voi käyttää kiipeilyyn. Jääkiipeily lisätietoihin. Myös boulderointikalliot.",
   :se
   "Märkt berg i naturen. Isklättring i tilläggsinformation. Även berg för bouldering.",
   :en
   "Marked natural cliff. Ice climbing specified in additional information. Also includes bouldering cliffs."},
  :tags {:fi []},
  :name
  {:fi "Kiipeilykallio", :se "Klätterberg", :en "Climbing rock"},
  :type-code 4720,
  :main-category 4000,
  :status "active",
  :sub-category 4700,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :climbing-routes-count {:priority 80},
   :ice-climbing? {:priority 80},
   :climbing-wall-height-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :climbing-wall-width-m {:priority 90},
   :ligthing? {:priority 60}}},
 1330
 {:description
  {:fi
   "Rantalentopallokenttä, pehmeä alusta. Kohde voi sijaita muuallakin kuin rannalla.",
   :se
   "Beachvolleybollplan, mjuk grund. Kan också ha annat läge än stranden.",
   :en
   "Beach volleyball court, soft basement. May also be located far from a beach."},
  :tags {:fi ["rantalentopallo" "rantalentopallokenttä"]},
  :name
  {:fi "Beachvolley-/rantalentopallokenttä",
   :se "Beachvolleyplan",
   :en "Beach volleyball court"},
  :type-code 1330,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :height-of-basket-or-net-adjustable? {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40}}},
 206
 {:description
  {:fi
   "Rakennettu tulentekopaikka tai keittokatos. Kohde voi olla esimerkiksi maasta eristetty katoksellinen tulisija tai tulisija avotulelle. Tulentekopaikan tarkempi kuvaus ja tieto mahdollisista rajoituksista lisätietoihin.",
   :se
   "En byggd eldningsplats eller ett kokskjul. Objektet kan till exempel vara en eldstad med tak som är isolerad från marken eller en eldstad för öppen eld. En mer detaljerad beskrivning av eldningsplatsen och information om eventuella begränsningar finns i ytterligare information.",
   :en
   "A constructed fireplace or cooking shelter. The site can be, for example, a covered fireplace isolated from the ground or a fireplace for open fires. A more detailed description of the fireplace and information about any possible restrictions can be found in the additional information."},
  :tags
  {:fi
   ["nuotiopaikka"
    "keittokatos"
    "grillauspaikka"
    "ruoka"
    "taukopaikka"]},
  :name
  {:fi "Ruoanlaitto- / tulentekopaikka",
   :se "Matlagningsplats",
   :en "Cooking facilities"},
  :type-code 206,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :toilet? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :free-use? {:priority 60}}},
 4830
 {:description
  {:fi
   "Ulkona tai sisällä sijaitseva jousiammuntarata. Radan käyttö edellyttää erillistä lupaa, seuran jäsenyyttä tai harjoitusvuoroa.  Radan varustus ja soveltuvat lajit kuvataan lisätiedoissa.",
   :se "Ute eller inne. Utrustning och grenar i karakteristika.",
   :en
   "Outdoors or indoors. Equipment and the various sports detailed in properties."},
  :tags {:fi ["jousiampumarata"]},
  :name
  {:fi "Jousiammuntarata", :se "Bågskyttebana", :en "Archery range"},
  :type-code 4830,
  :main-category 4000,
  :status "active",
  :sub-category 4800,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :track-width-m {:priority 90},
   :free-customer-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shooting-positions-count {:priority 90},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :ligthing? {:priority 60},
   :track-length-m {:priority 90}}},
 1180
 {:description
  {:fi "Frisbeegolfin pelaamiseen rakennettu rata.",
   :se "En bana byggt för frisbeegolf.",
   :en "Track built for disc golf. "},
  :tags {:fi []},
  :name
  {:fi "Frisbeegolfrata",
   :se "Frisbeegolfbana",
   :en "Disc golf course"},
  :type-code 1180,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :holes-count {:priority 90},
   :free-use? {:priority 50},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :altitude-difference {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :lighting-info {:priority 60},
   :ligthing? {:priority 70},
   :accessibility-info {:priority 50},
   :school-use? {:priority 50},
   :track-type {:priority 80},
   :range? {:priority 80},
   :track-length-m {:priority 90}}},
 4422
 {:description
  {:fi
   "Moottorikelkkailuun tarkoitettu reitti, jolla ei ole tehty virallista reittitoimitusta. Reitillä on kuitenkin ylläpitäjä ja maanomistajien lupa.",
   :se "Ingen ruttexpedition.",
   :en "No official approval."},
  :tags {:fi []},
  :name
  {:fi "Moottorikelkkaura",
   :se "Snöskoterspår",
   :en "Unofficial snowmobile route"},
  :type-code 4422,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :route-width-m {:priority 98},
   :route-length-km {:priority 99},
   :rest-places-count {:priority 70},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 4430
 {:description
  {:fi
   "Ratsastukseen ja/tai kärryillä ajoon tarkoitettu reitti. Sallitut käyttötavat kerrotaan reitin tarkemmissa tiedoissa.",
   :se
   "Led avsedd för ridning och/eller häst med kärra. Användningsanvisningar i karakteristika.",
   :en
   "Route intended for horseback riding and/or carriage riding. Different uses specified in additional information."},
  :tags {:fi ["ratsastusreitti"]},
  :name {:fi "Hevosreitti", :se "Hästled", :en "Horse track"},
  :type-code 4430,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :route-width-m {:priority 98},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 97},
   :school-use? {:priority 40},
   :route-length-km {:priority 99}}},
 204
 {:description
  {:fi
   "Luonnon tarkkailuun tarkoitettu rakennelma, lintutorni tai vastaava.",
   :se
   "Anordning avsedd för observationer i  naturen, t ex fågeltorn.",
   :en
   "Structure built for nature observation. E.g. bird observation tower."},
  :tags {:fi ["lintutorni" "näkötorni" "torni"]},
  :name
  {:fi "Luontotorni",
   :se "Naturtorn",
   :en "Nature observation tower"},
  :type-code 204,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:toilet? {:priority 80},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :free-use? {:priority 70}}},
 106
 {:description
  {:fi
   "Monikäyttöalueiksi voidaan nimittää jokaisenoikeuksin ulkoiluun käytettäviä maa- ja metsätalousalueita. Monikäyttöalueita ovat erityisesti rakentamattomat rannat ja taajamien läheiset maa- ja metsätalousalueet. Kaavamerkintä MU. Virkistysmetsien metsätaloudessa on huomioitu mm. maisemalliset arvot, ja ne on perustettu Metsähallituksen päätöksellä. Virkistysmetsien osalta Lipas-aineisto perustuu Metsähallituksen tietoihin.",
   :se
   "Områden för mångsidig användning kan kallas jord- och skogsbruksområden som används för rekreation med allemansrätten. Områden för mångsidig användning är särskilt obebyggda stränder och jord- och skogsbruksområden nära tätorter. Planbeteckning MU. Inom skogsbruket i rekreationsskogar har man beaktat bl.a. landskapsvärden, och de har inrättats genom beslut av Forststyrelsen. När det gäller rekreationsskogar baseras Lipas-materialet på uppgifter från Forststyrelsen.",
   :en
   "Multi-use areas can be designated as agricultural and forestry areas used for outdoor activities under everyman's rights. Multi-use areas are particularly undeveloped shores and agricultural and forestry areas near urban areas. Plan designation MU. In the forestry of recreational forests, landscape values, among other things, have been considered, and they have been established by a decision of Metsähallitus. For recreational forests, the Lipas data is based on information from Metsähallitus."},
  :tags {:fi ["ulkoilualue" "virkistysalue"]},
  :name
  {:fi
   "Monikäyttöalue tai virkistysmetsä, jossa on virkistyspalveluita",
   :se
   "Mångbruksområde eller rekreationsskog med rekreationstjänster",
   :en
   "Multi-use area or recreational forest with recreational services"},
  :type-code 106,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 2620
 {:description
  {:fi
   "Biljardisali on biljardin pelaamiseen tarkoitettu tila. Biljardipöytien määrä ja tyyppi kuvataan lisätiedoissa."},
  :tags {:fi []},
  :name {:fi "Biljardisali"},
  :type-code 2620,
  :main-category 2000,
  :status "active",
  :sub-category 2600,
  :geometry-type "Point",
  :props
  {:total-billiard-tables-count {:priority 90},
   :carom-tables-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :snooker-tables-count {:priority 80},
   :pool-tables-count {:priority 80},
   :customer-service-point? {:priority 70},
   :pyramid-tables-count {:priority 80},
   :kaisa-tables-count {:priority 80},
   :school-use? {:priority 40}}},
 4610
 {:description
  {:fi
   "Ampumahiihdon harjoitteluun tarkoitettu alue, jossa on ainakin latu ja ampumapaikka/-paikkoja. ",
   :se
   "Annat träningsområde för skidskytte. Spår och skjutplats finns.",
   :en
   "Other training area for biathlon. Ski track and shooting range."},
  :tags {:fi ["ampumapaikka"]},
  :name
  {:fi "Ampumahiihdon harjoittelualue",
   :se "Träningsområde för skidskytte",
   :en "Training area for biathlon"},
  :type-code 4610,
  :main-category 4000,
  :status "active",
  :sub-category 4600,
  :geometry-type "Point",
  :props
  {:stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :finish-line-camera? {:priority 70},
   :ski-track-traditional? {:priority 80},
   :route-width-m {:priority 98},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :changing-rooms? {:priority 70},
   :shooting-positions-count {:priority 80},
   :lit-route-length-km {:priority 97},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :changing-rooms-m2 {:priority 70},
   :loudspeakers? {:priority 70},
   :ski-track-freestyle? {:priority 80},
   :route-length-km {:priority 99}}},
 2610
 {:description
  {:fi
   "Keilailuun varustettu halli. Ratojen määrä ja palveluvarustus kirjataan lisätietoihin.",
   :se "Antalet banor och serviceutrustning i karakteristika.",
   :en "Number of alleys and service facilities in properties."},
  :tags {:fi []},
  :name {:fi "Keilahalli", :se "Bowlinghall", :en "Bowling alley"},
  :type-code 2610,
  :main-category 2000,
  :status "active",
  :sub-category 2600,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :bowling-lanes-count {:priority 90},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :cosmic-bowling? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :school-use? {:priority 40}}},
 2110
 {:description
  {:fi
   "Erilasia liikuntapalveluita ja -tiloja tarjoava kuntokeskus. Kohteessa voi olla esimerkiksi kuntosali- ja ryhmäliikuntatiloja.",
   :se
   "Olika motionstjänster och -utrymmen, t ex gym och gruppidrottsutrymmen.",
   :en
   "Different sports services and premises, e.g., gym, group exercise premises. "},
  :tags {:fi ["kuntosali" "kuntoilu"]},
  :name
  {:fi "Kuntokeskus",
   :se "Konditionsidrottscentrum",
   :en "Fitness centre"},
  :type-code 2110,
  :main-category 2000,
  :status "active",
  :sub-category 2100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-customer-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :group-exercise-rooms-count {:priority 80},
   :area-m2 {:priority 90},
   :weight-lifting-spots-count {:priority 80},
   :customer-service-point? {:priority 70},
   :spinning-hall? {:priority 80},
   :school-use? {:priority 40},
   :exercise-machines-count {:priority 80}}},
 3120
 {:description
  {:fi
   "Yksittäinen tai useampi pieni uima-allas muun kuin uimahallin tai kylpylän yhteydessä. Uima-allastilat voivat olla pääasiassa esim. kuntoutus- tai terapiakäytössä. Altaiden määrä ja vesipinta-ala kerrotaan ominaisuustiedoissa.",
   :se
   "Enstaka simbassäng , ofta i anslutning till en annan byggnad.",
   :en
   "Individual swimming pool, often in connection with other buildings."},
  :tags {:fi []},
  :name
  {:fi "Uima-allastila", :se "Simbassäng", :en "Swimming pool"},
  :type-code 3120,
  :main-category 3000,
  :status "active",
  :sub-category 3100,
  :geometry-type "Point",
  :props
  {:pool-tracks-count {:priority 87},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :pool-width-m {:priority 89},
   :pool-min-depth-m {:priority 80},
   :swimming-pool-count {:priority 90},
   :pool-water-area-m2 {:priority 90},
   :pool-length-m {:priority 88},
   :pool-max-depth-m {:priority 80},
   :accessibility-info {:priority 40},
   :pool-temperature-c {:priority 80},
   :school-use? {:priority 40}}},
 104
 {:description
  {:fi
   "Sijaitsevat kauempana taajamasta, automatkan päässä. Monipuolinen polku- ja reittiverkosto. Käyttö painottuu viikonloppuihin ja loma-aikoihin. Palvelevat usein useaa kuntaa. Kaavamerkintä VR.",
   :se
   "Ett område på bil avstånd från tätorten, Området har en stor variation av stig- och ruttnätverk. Användningen av området fokuserar sig mest till helgerna och semester tiderna. Området betjänar oftast mer än en kommun. Planbeteckning VR.",
   :en
   "Located further away from population centres, accessible by car. Complex network of paths and routes. Use concentrated during weekends and holidays. Often serves several municipalities. Symbol VR."},
  :tags {:fi ["virkistysalue"]},
  :name
  {:fi "Retkeilyalue", :se "Utflyktsområde", :en "Hiking area"},
  :type-code 104,
  :main-category 0,
  :status "deprecated",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :area-km2 {:priority 90},
   :school-use? {:priority 70},
   :free-use? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 2330
 {:description
  {:fi "Pysyvästi pöytätennikseen varustettu tila.",
   :se "Permanent utrustning för att träna bordtennis.",
   :en "Space permanently equipped for table tennis."},
  :tags {:fi ["pingis" "pingispöytä"]},
  :name
  {:fi "Pöytätennistila",
   :se "Utrymme för bordtennis",
   :en "Table tennis venue"},
  :type-code 2330,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :active-space-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :active-space-length-m {:priority 90},
   :table-tennis-count {:priority 80},
   :school-use? {:priority 40}}},
 2280
 {:description
  {:fi
   "Tenniksen pelaamiseen varusteltu halli. Kenttien lukumäärä ja pintamateriaali kerrotaan kohteen lisätiedoissa.",
   :se "Antalet banor i karakteristika.",
   :en "Number of courts specified in properties."},
  :tags {:fi []},
  :name {:fi "Tennishalli", :se "Tennishall", :en "Tennis hall"},
  :type-code 2280,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :tennis-courts-count {:priority 80},
   :field-length-m {:priority 90},
   :badminton-courts-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :padel-courts-count {:priority 80},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :scoreboard? {:priority 70},
   :floorball-fields-count {:priority 80},
   :squash-courts-count {:priority 80},
   :customer-service-point? {:priority 70},
   :volleyball-fields-count {:priority 80},
   :school-use? {:priority 40}}},
 6140
 {:description
  {:fi "Raviurheilun harjoitus- tai kilparata.",
   :se "Övnings- eller tävlingsbana för travsport.",
   :en "Training or competition track for horse racing."},
  :tags {:fi []},
  :name {:fi "Ravirata", :se "Travbana", :en "Horse racing track"},
  :type-code 6140,
  :main-category 6000,
  :status "active",
  :sub-category 6100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :lighting-info {:priority 50},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :track-length-m {:priority 90}}},
 2140
 {:description
  {:fi
   "Sali, jossa voi harrastaa kamppailulajeja kuten painia, nyrkkeilyä tai budolajeja. Tilan koko ja varustus kerrotaan kohteen lisätiedoissa.",
   :se
   "Sal där man kan utöva självförsvarsgrenar, t ex brottning, boxning. Storleken anges i karakteristika.",
   :en
   "Hall for self-defence sports, e.g., wrestling, boxing. Size specified in properties. "},
  :tags {:fi ["paini" "judo" "tatami"]},
  :name
  {:fi "Kamppailulajien sali",
   :se "Sal för kampsport",
   :en "Martial arts hall"},
  :type-code 2140,
  :main-category 2000,
  :status "active",
  :sub-category 2100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :group-exercise-rooms-count {:priority 80},
   :space-divisible {:priority 70},
   :tatamis-count {:priority 80},
   :area-m2 {:priority 90},
   :wrestling-mats-count {:priority 80},
   :boxing-rings-count {:priority 80},
   :weight-lifting-spots-count {:priority 80},
   :customer-service-point? {:priority 70},
   :school-use? {:priority 40},
   :exercise-machines-count {:priority 80}}},
 4220
 {:description
  {:fi
   "Hiihtoon tarkoitettu katettu tila (esim. tunneli, putki, halli).",
   :se
   "Utrymme avsett för skidåkning under tak (tunnel, rör, hall el dyl).",
   :en
   "Covered space (tunnel, tube, hall, etc.) intended for skiing."},
  :tags {:fi []},
  :name {:fi "Hiihtotunneli", :se "Skidtunnel", :en "Ski tunnel"},
  :type-code 4220,
  :main-category 4000,
  :status "active",
  :sub-category 4200,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :altitude-difference {:priority 90},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :equipment-rental? {:priority 70},
   :accessibility-info {:priority 40},
   :route-length-km {:priority 99}}},
 2230
 {:description
  {:fi
   "Ensisijaisesti jalkapalloiluun tarkoitettu halli. Halli voi olla ympärivuotisessa käytössä tai erikseen talviajalle pystytettävä kevythalli. Kentän pintamateriaalina on yleensä tekonurmi.",
   :se
   "Hall avsedd för fotboll. Ytmaterial, antalet planer och storlek i karakteristika.",
   :en
   "Hall intended for football. Surface material, number and size of courts specified in properties."},
  :tags {:fi []},
  :name
  {:fi "Jalkapallohalli", :se "Fotbollshall", :en "Football hall"},
  :type-code 2230,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :heating? {:priority 70},
   :surface-material {:priority 89},
   :basketball-fields-count {:priority 80},
   :surface-material-info {:priority 88},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :tennis-courts-count {:priority 80},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :sprint-track-length-m {:priority 80},
   :badminton-courts-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :scoreboard? {:priority 70},
   :football-fields-count {:priority 80},
   :auxiliary-training-area? {:priority 80},
   :loudspeakers? {:priority 70},
   :customer-service-point? {:priority 70},
   :volleyball-fields-count {:priority 80},
   :school-use? {:priority 40}}},
 1350
 {:description
  {:fi
   "Suuri jalkapallokenttä, katsomoita. Vähintään kansallisen tason pelipaikka.",
   :se
   "Stor fotbollsplan, flera läktare. Minimikrav: spelplats på nationell nivå.",
   :en
   "Large football field, stands. Can host at least national-level games."},
  :tags {:fi ["jalkapallokenttä"]},
  :name
  {:fi "Jalkapallostadion",
   :se "Fotbollsstadion",
   :en "Football stadium"},
  :type-code 1350,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:heating? {:priority 70},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :customer-service-point? {:priority 70},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :school-use? {:priority 40},
   :light-roof? {:priority 70}}},
 4840
 {:description
  {:fi
   "Maastoon rakennettu jousiammuntarata. Radan käyttö edellyttää erillistä lupaa, seuran jäsenyyttä tai harjoitusvuoroa.  ",
   :se "Bågskyttebana byggd i terrängen.",
   :en "Archery course built in rough terrain."},
  :tags {:fi ["jousiampumarata"]},
  :name
  {:fi "Jousiammuntamaastorata",
   :se "Terrängbana för bågskytte",
   :en "Field archery course"},
  :type-code 4840,
  :main-category 4000,
  :status "active",
  :sub-category 4800,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :free-customer-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shooting-positions-count {:priority 80},
   :lighting-info {:priority 50},
   :ligthing? {:priority 60},
   :track-length-m {:priority 90}}},
 113
 {:description
  {:fi
   "Vapaa-ajankalastukseen sopiva alue. Kohteessa voi olla palvelurakenteita.",
   :se
   "Område eller en plats i ett naturligt vattendrag som ställts i ordning för fritidsfiske.",
   :en
   "Natural aquatic destination equipped and maintained for recreational fishing."},
  :tags {:fi ["kalastusalue" "kalastuspaikka"]},
  :name
  {:fi "Kalastuskohde (alue)",
   :se "Område eller plats för fiske",
   :en "Fishing area/spot "},
  :type-code 113,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:free-use? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :pier? {:priority 80},
   :customer-service-point? {:priority 80},
   :equipment-rental? {:priority 80}}},
 1510
 {:description
  {:fi
   "Koneellisesti tai keinotekoisesti jäähdytetty ulkokenttä. Kentän koko ja varustustiedot löytyvät lisätiedoista. Käytössä talvikaudella.",
   :se
   "En utomhusbana som är mekaniskt eller artificiellt kyld. Information om banans storlek och utrustning finns i ytterligare information. Används under vintersäsongen.",
   :en
   "An outdoor rink that is mechanically or artificially cooled. Details about the size and equipment of the rink can be found in the additional information. Used during the winter season."},
  :tags {:fi ["luistelukenttä" "luistelu"]},
  :name
  {:fi "Tekojääkenttä/tekojäärata",
   :se "Konstis",
   :en "Mechanically frozen open-air ice rink"},
  :type-code 1510,
  :main-category 1000,
  :status "active",
  :sub-category 1500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :fields-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :ice-rinks-count {:priority 80},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :changing-rooms-m2 {:priority 70},
   :customer-service-point? {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :light-roof? {:priority 70}}},
 5350
 {:description
  {:fi
   "Pääasiallisesti kiihdytysautoiluun tai kiihdytysmoottoripyöräilyyn käytetty rata.",
   :se "Huvudsakligen för accelerationskörning.",
   :en "Mainly for drag racing."},
  :tags {:fi []},
  :name
  {:fi "Kiihdytysrata", :se "Accelerationsbana", :en "Dragstrip"},
  :type-code 5350,
  :main-category 5000,
  :status "active",
  :sub-category 5300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :track-length-m {:priority 90}}},
 2225
 {:description
  {:fi
   "Sisäleikkipuistot ovat yleensä pienille lapsille tarkoitettuja liikunnallisia leikkipaikkoja. Sisäaktiviteettipuistot ovat tyypillisesti lapsille ja nuorille tarkoitettuja liikuntakeskuksia, jotka sisältävät erilaisia liikunnallisia kohteita."},
  :tags {:fi []},
  :name {:fi "Sisäleikki-/sisäaktiviteettipuisto"},
  :type-code 2225,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:customer-service-point? {:priority 70},
   :height-m {:priority 90},
   :area-m2 {:priority 90}}},
 4440
 {:description
  {:fi
   "Hiihtolatu, jossa on aina tai tiettyinä aikoina koirahiihto sallittua. Perinteinen tyyli tai vapaa tyyli.",
   :se
   "Ett skidspår där det alltid eller vissa tider är tillåtet att åka med hundspann. Klassisk stil eller fristil.",
   :en
   "Ski track on which dog skijoring is allowed either always or at given times. Traditional or free style."},
  :tags {:fi ["koiralatu"]},
  :name
  {:fi "Koirahiihtolatu",
   :se "Spår för hundspann",
   :en "Dog skijoring track"},
  :type-code 4440,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :ski-track-traditional? {:priority 80},
   :route-width-m {:priority 98},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 97},
   :ski-track-freestyle? {:priority 80},
   :school-use? {:priority 40},
   :route-length-km {:priority 99}}},
 2520
 {:description
  {:fi
   "Kilpajäähalli on jääurheilun kilpailu- ja ottelutapahtumiin soveltuva jäähalli. Katsomon koko, kenttien lukumäärä ja muut tarkemmat tiedot kuvataan lisätiedoissa.",
   :se
   "Läktare finns, storleken på läktaren anges i karakteristika, likaså antalet planer.",
   :en
   "Includes bleachers, whose size is specified in properties. Number of fields, heating, changing rooms, etc., specified in properties."},
  :tags {:fi ["jäähalli"]},
  :additional-type
  {:small
   {:fi "Pieni kilpahalli > 500 hlö",
    :en "Small competition hall > 500 people",
    :se "Liten tävlingshall > 500 personer"},
   :competition
   {:fi "Kilpahalli < 3000 hlö",
    :en "Competition hall < 3000 people",
    :se "Tävlingshall < 3000 personer"},
   :large
   {:fi "Suurhalli > 3000 hlö",
    :en "Large hall > 3000 people",
    :se "Större hall > 3000 personer"}},
  :name
  {:fi "Kilpajäähalli",
   :se "Tävlingsishall",
   :en "Competition ice arena"},
  :type-code 2520,
  :keywords {:fi ["Jäähalli"], :en [], :se []},
  :main-category 2000,
  :status "active",
  :sub-category 2500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 79},
   :surface-material-info {:priority 78},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :ice-rinks-count {:priority 80},
   :field-2-flexible-rink? {:priority 80},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :curling-lanes-count {:priority 80},
   :scoreboard? {:priority 70},
   :auxiliary-training-area? {:priority 80},
   :ringette-boundary-markings? {:priority 80},
   :field-1-flexible-rink? {:priority 80},
   :loudspeakers? {:priority 70},
   :school-use? {:priority 40},
   :field-3-flexible-rink? {:priority 80}}},
 4710
 {:description
  {:fi
   "Rakennettu kiipeilyseinä ulkona, köysikiipeilyrata tai vastaava kiipeilyä varten rakennettu paikka. Myös rakennetut boulderointipaikat. Paikan tarkempi kuvaus ominaisuustietoihin.",
   :se
   "Byggd klättervägg utomhus, klätterbana eller annan plats byggd för klättring. Även platser för bouldering. Precisering i karakteristika.",
   :en
   "Built outdoor climbing wall, rope climbing path or other place built for climbing. Also bouldering venues. Clarification in 'properties'."},
  :tags {:fi ["kiipeilyseinä" "köysikiipeily"]},
  :name
  {:fi "Ulkokiipeilypaikka",
   :se "Utomhusklätterplats",
   :en "Open-air climbing venue"},
  :type-code 4710,
  :main-category 4000,
  :status "active",
  :sub-category 4700,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :climbing-routes-count {:priority 80},
   :ice-climbing? {:priority 80},
   :climbing-wall-height-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 60},
   :climbing-wall-width-m {:priority 90},
   :ligthing? {:priority 70},
   :school-use? {:priority 40}}},
 304
 {:description
  {:fi
   "Tavallisen arkiliikunnan taukopaikka, päiväkäyttöön. Lisätietoihin merkitään kohteessa olevat palvelut, esim. kahvio, vuokrauspiste tai opastuspiste.",
   :se "Rastplats för bruk under dagen, vardagsmotion.",
   :en "Rest area for regular daily sports, for daytime use."},
  :tags {:fi ["tupa" "taukopaikka" "ulkoilumaja" "hiihtomaja"]},
  :name
  {:fi "Ulkoilumaja/hiihtomaja",
   :se "Friluftsstuga/skidstuga",
   :en "Outdoor/ski lodge "},
  :type-code 304,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:toilet? {:priority 70},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :customer-service-point? {:priority 70},
   :equipment-rental? {:priority 70},
   :free-use? {:priority 60}}},
 4412
 {:description
  {:fi
   "Pyöräilyreitti, joka kulkee enimmäkseen päällystetyillä teillä tai sorateillä. Reitti voi olla merkitty maastoon tai se on digitaalisesti opastettu.",
   :se "Cykelled, ej för mountainbikar.",
   :en "Biking route, not intended for cross-country biking."},
  :tags {:fi []},
  :name {:fi "Pyöräilyreitti", :se "Cykelled", :en "Biking route"},
  :type-code 4412,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 98},
   :route-length-km {:priority 99}}},
 4820
 {:description
  {:fi
   "Ampumarata jossa myös palveluita. SM-kisojen järjestäminen mahdollista.",
   :se "Skjutbana med tjänster. Möjligt att arrangera FM-tävlingar.",
   :en
   "Shooting range with services. National competitions possible."},
  :tags {:fi ["ampumapaikka" "ammunta"]},
  :name
  {:fi "Ampumaurheilukeskus",
   :se "Sportskyttecentrum",
   :en "Shooting sports centre"},
  :type-code 4820,
  :main-category 4000,
  :status "active",
  :sub-category 4800,
  :geometry-type "Point",
  :props
  {:stand-capacity-person {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :air-gun-shooting? {:priority 80},
   :toilet? {:priority 70},
   :pistol-shooting? {:priority 80},
   :shooting-positions-count {:priority 90},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :rifle-shooting? {:priority 80},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :shotgun-shooting? {:priority 80},
   :free-rifle-shooting? {:priority 80},
   :ligthing? {:priority 60},
   :accessibility-info {:priority 40},
   :track-length-m {:priority 90}}},
 1170
 {:description
  {:fi "Ratapyöräilyä varten rakennettu paikka, ulkona (velodromi).",
   :se "Utomhus, velodrom.",
   :en "For track racing outdoors (velodrome)."},
  :tags {:fi ["velodromi"]},
  :name
  {:fi "Pyöräilyrata/velodromi", :se "Velodrom", :en "Velodrome"},
  :type-code 1170,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 50},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 60},
   :ligthing? {:priority 70},
   :school-use? {:priority 50},
   :track-length-m {:priority 90}}},
 6150
 {:description
  {:fi
   "Islanninhevosten askellajiratsastukseen varattu rata ulkona.",
   :se
   "En bana utomhus avsedd för gångartstävlingar med islandshästar.",
   :en
   "An outdoor track designated for gaited riding competitions with Icelandic horses."},
  :tags
  {:fi
   ["islanninhevosrata"
    "islanninhevosratsastus"
    "ovaalibaana"
    "askellajiratsastus"
    "askellajirata"],
   :se
   ["islandshästbana"
    "islandshästridning"
    "ovalbana"
    "gångartstävling"
    "gångartsbana"],
   :en
   ["Icelandic horse track"
    "Icelandic horse riding"
    "oval track"
    "gaited riding"
    "gaited track"]},
  :name {:fi "Ovaalirata", :se "Ovalbana", :en "Oval Track"},
  :type-code 6150,
  :main-category 6000,
  :status "active",
  :sub-category 6100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :customer-service-point? {:priority 70},
   :ligthing? {:priority 60},
   :track-length-m {:priority 90}}},
 4404
 {:description
  {:fi
   "Erityisesti luontoharrastusta varten rakennettu ulkoilureitti. Reitin varrella opasteita tai infotauluja alueen luonnosta.",
   :se
   "I synnerhet för naturintresse, info- och orienteringstavlor längs leden.",
   :en
   "Intended particularly for nature activities; signposts or info boards along the route."},
  :tags {:fi ["retkeily"]},
  :name {:fi "Luontopolku", :se "Naturstig", :en "Nature trail"},
  :type-code 4404,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 98},
   :accessibility-info {:priority 40},
   :school-use? {:priority 40},
   :route-length-km {:priority 99}}},
 108
 {:description
  {:fi
   "Metsähallituksen päätöksellä perustettu virkistysmetsä. Metsätaloudessa huomioidaan mm. maisemalliset arvot. Metsähallitus tietolähde.",
   :se
   "Grundad enligt Forststyrelsens beslut. I skogsbruket tas hänsyn till bl a landskapsvärden. Källa: Forststyrelsen.",
   :en
   "Recreational forest designated by Metsähallitus. E.g. scenic value is considered in forestry. Source of information  Metsähallitus."},
  :tags {:fi []},
  :name
  {:fi "Virkistysmetsä",
   :se "Friluftsskog",
   :en "Recreational forest"},
  :type-code 108,
  :main-category 0,
  :status "deprecated",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :school-use? {:priority 70},
   :free-use? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 4401
 {:description
  {:fi
   "Kuntoiluun tarkoitettu hoidettu liikuntareitti asutuksen läheisyydessä. Usein ainakin osittain valaistu.",
   :se "Led avsedd för konditionssport i närheten av bebyggelse.",
   :en "Route intended for jogging in or near a residential area."},
  :tags {:fi ["pururata"]},
  :name {:fi "Kuntorata", :se "Konditionsbana", :en "Jogging track"},
  :type-code 4401,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :outdoor-exercise-machines? {:priority 80},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :shooting-positions-count {:priority 80},
   :lit-route-length-km {:priority 98},
   :accessibility-info {:priority 40},
   :school-use? {:priority 40},
   :route-length-km {:priority 99}}},
 2350
 {:description
  {:fi
   "Pysyvästi tanssi-, ilmaisu- tai ryhmäliikuntaan varustettu itsenäinen tila, joka ei ole osa esim. kuntokeskusta. Myös boutique-, fitness- ja mikrostudiot ovat tanssi- tai ryhmäliikuntatiloja.",
   :se "Permanent utrustning för dans och kreativ motion.",
   :en
   "Space permanently equipped for dance or expressive movement exercise."},
  :tags {:fi ["peilisali" "baletti" "tanssisali"]},
  :name
  {:fi "Tanssi-/ryhmäliikuntatila",
   :se "Utrymme för dans",
   :en "Dance studio"},
  :type-code 2350,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :active-space-width-m {:priority 90},
   :mirror-wall? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :group-exercise-rooms-count {:priority 80},
   :area-m2 {:priority 90},
   :active-space-length-m {:priority 90},
   :school-use? {:priority 40}}},
 2340
 {:description
  {:fi "Pysyvästi miekkailuun varustettu tila.",
   :se "Permanent utrustning för fäktning.",
   :en "Space permanently equipped for fencing."},
  :tags {:fi []},
  :name
  {:fi "Miekkailutila",
   :se "Utrymme för fäktning",
   :en "Fencing venue"},
  :type-code 2340,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :fencing-bases-count {:priority 80},
   :school-use? {:priority 40}}},
 2120
 {:description
  {:fi
   "Liikuntatila, jossa on useita kuntosalilaitteita pysyvästi sijoitettuna.",
   :se "Gymredskap osv. Storleken anges i karakteristika.",
   :en "Gym equipment, etc. Size specified in properties."},
  :tags {:fi ["kuntoilu" "voimailu"]},
  :name {:fi "Kuntosali", :se "Gym", :en "Gym"},
  :type-code 2120,
  :main-category 2000,
  :status "active",
  :sub-category 2100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-customer-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :group-exercise-rooms-count {:priority 80},
   :area-m2 {:priority 90},
   :weight-lifting-spots-count {:priority 80},
   :customer-service-point? {:priority 70},
   :spinning-hall? {:priority 80},
   :school-use? {:priority 40},
   :exercise-machines-count {:priority 80}}},
 109
 {:description
  {:fi
   "Ulkoilulailla perustetut, retkeilyä ja luonnon virkistyskäyttöä varten. Metsähallitus tietolähteenä.",
   :se
   "Grundat enligt lagen om friluftsliv för att användas för friluftsliv och rekreation i naturen. Källa: Forststyrelsen.",
   :en
   "Established based on the Outdoor Recreation Act for hiking and recreational use of nature. Source of information  Metsähallitus."},
  :tags {:fi []},
  :name
  {:fi "Valtion retkeilyalue",
   :se "Statens friluftsområde",
   :en "National hiking area"},
  :type-code 109,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 1650
 {:description
  {:fi
   "Ensisijaisesti golfin pelaamiseen tarkoitettu alue kesäkaudella. Reikien määrä merkitään lisätietoihin.",
   :se "Officiell golfbana. Antalet hål anges i karakteristika.",
   :en
   "Official golf course. Number of holes included in properties."},
  :tags {:fi ["greeni" "puttialue" "range"]},
  :name {:fi "Golfkenttä (alue)", :se "Golfbana", :en "Golf course"},
  :type-code 1650,
  :main-category 1000,
  :status "active",
  :sub-category 1600,
  :geometry-type "Polygon",
  :props
  {:holes-count {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :lighting-info {:priority 50},
   :customer-service-point? {:priority 70},
   :green? {:priority 80},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :range? {:priority 80}}},
 4441
 {:description
  {:fi "Koiravaljakoille ylläpidetty reitti.",
   :se "En rutt underhållen för hundspann.",
   :en "A route maintained for dog sledding."},
  :tags {:fi ["valjakkoajo" "valjakkoreitti"], :se [], :en []},
  :name
  {:fi "Koiravaljakkoreitti",
   :se "Hundspannsrutt",
   :en "Dog Sledding Route"},
  :type-code 4441,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :route-length-km {:priority 99},
   :lit-route-length-km {:priority 97},
   :route-width-m {:priority 98},
   :free-use? {:priority 40},
   :year-round-use? {:priority 80}}},
 5160
 {:description
  {:fi
   "Soudun ja melonnan sisäharjoittelutila on erityisesti näihin lajeihin pysyvästi tarkoitettu liikuntapaikka.",
   :se "Separat, ej normal bassäng.",
   :en "Separate training facility, not a regular swimming pool."},
  :tags {:fi ["kajakki" "kanootti" "melonta"]},
  :name
  {:fi "Soudun ja melonnan sisäharjoittelutila",
   :se "Inomhusträningsutrymme för rodd och paddling",
   :en "Indoor training facility for rowing and canoeing"},
  :type-code 5160,
  :main-category 5000,
  :status "active",
  :sub-category 5100,
  :geometry-type "Point",
  :props
  {:area-m2 {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 1550
 {:description
  {:fi
   "Luisteluun tarkoitettu luonnonjäälle tai maalle rakennettava huollettu luistelureitti. Rakennetaan talvisin samalle alueelle. ",
   :se
   "Byggs varje vinter på samma område t ex i en idrottspark eller på havsis.",
   :en
   "Built yearly in the same area, e.g., in a sports park or on frozen lake/sea."},
  :tags {:fi ["luistelu" "retkiluistelu" "retkiluistelurata"]},
  :name
  {:fi "Luistelureitti", :se "Skridskoled", :en "Ice-skating route"},
  :type-code 1550,
  :main-category 1000,
  :status "active",
  :sub-category 1500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :lighting-info {:priority 50},
   :equipment-rental? {:priority 70},
   :customer-service-point? {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :track-length-m {:priority 90}}},
 3230
 {:description
  {:fi
   "Pieni yleinen uimaranta tai uimapaikka, jossa pelastusväline ja ilmoitustaulu. Veden laadun seuranta ja alueen hoito järjestetty.",
   :se
   "Liten allmän badstrand eller badplats. Räddningsutrustning och en anslagstavla finns. Kvaliteten på vattnet följs upp och området underhålls.",
   :en
   "Small public beach or swimming site. Rescue equipment and a notice board are available. The quality of the water is monitored and the area is maintained."},
  :tags {:fi ["uimaranta"]},
  :name {:fi "Uimapaikka", :se "Badplats", :en "Swimming site"},
  :type-code 3230,
  :main-category 3000,
  :status "active",
  :sub-category 3200,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :pier? {:priority 80},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :beach-length-m {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shower? {:priority 70},
   :changing-rooms? {:priority 70},
   :sauna? {:priority 70},
   :other-platforms? {:priority 80},
   :school-use? {:priority 40}}},
 5130
 {:description
  {:fi "Pysyvä moottorivenekilpailujen rata-alue.",
   :se "Permanent banområde för hastighetstävlingar.",
   :en "Permanent track area for speed competitions."},
  :tags {:fi []},
  :name
  {:fi "Moottoriveneurheilualue",
   :se "Område för motorbåtsport",
   :en "Motor boat sports area"},
  :type-code 5130,
  :main-category 5000,
  :status "active",
  :sub-category 5100,
  :geometry-type "Point",
  :props
  {:pier? {:priority 80},
   :area-km2 {:priority 90},
   :boat-places-count {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 5110
 {:description
  {:fi
   "Soutustadion sisältää pysyvästi soutuun käytettäviä rakenteita. Soutustadionissa on katsomo ja valmius ratamerkintöihin.",
   :se
   "Byggt för rodd, permanent. Läktare och förberett för banmärkning.",
   :en
   "Permanent construction for rowing. Bleachers, track markings possible."},
  :tags {:fi []},
  :name
  {:fi "Soutustadion", :se "Roddstadion", :en "Rowing stadium"},
  :type-code 5110,
  :main-category 5000,
  :status "active",
  :sub-category 5100,
  :geometry-type "Point",
  :props
  {:automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :pier? {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :scoreboard? {:priority 70},
   :track-length-m {:priority 90}}},
 3240
 {:description
  {:fi
   "Talviuintipaikka voi sijaita avannossa, avovedessä tai maauimalassa talvikaudella. Talviuintipaikka merkitään omaksi liikuntapaikakseen.",
   :se
   "Vinterbadplats kan vara belägen i en vak, öppet vatten eller utomhuspool. Vinterbadplats är markerad som egen idrottsanläggning",
   :en
   "Winter swimming area may be located in an ice hole, open water or open air pool. Winter swimming area is marked as its own sports facility."},
  :tags {:fi ["avanto" "avantouinti" "talviuinti"]},
  :name
  {:fi "Talviuintipaikka",
   :se "Vinterbadplats",
   :en "Winter swimming area"},
  :type-code 3240,
  :main-category 3000,
  :status "active",
  :sub-category 3200,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :pier? {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shower? {:priority 70},
   :changing-rooms? {:priority 70},
   :ice-reduction? {:priority 70},
   :sauna? {:priority 70},
   :school-use? {:priority 40}}},
 4510
 {:description
  {:fi
   "Suunnistukseen käytetty alue. Lisätietoihin merkitään, jos aluetta käytetään mobo-, pyörä- tai hiihtosuunnistukseen. Suunnistusalueesta on saatavilla kartta ja maankäyttöön on maanomistajan suostumus.",
   :se
   "Anmält till orienteringsförbundet. Karta över området tillgänglig.",
   :en
   "The Finnish Orienteering Federation has been informed. A map of the area available."},
  :tags {:fi []},
  :name
  {:fi "Suunnistusalue",
   :se "Orienteringsområde",
   :en "Orienteering area"},
  :type-code 4510,
  :main-category 4000,
  :status "active",
  :sub-category 4500,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :school-use? {:priority 40},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :mobile-orienteering? {:priority 80},
   :bike-orienteering? {:priority 80},
   :ski-orienteering? {:priority 80}}},
 4240
 {:description
  {:fi "Katettu laskettelurinne.",
   :se
   "Slalombacke med tak. Höjdskillnad och längd i karakteristika.",
   :en
   "Covered ski slope. Height and length specified in attributes."},
  :tags {:fi []},
  :name
  {:fi "Lasketteluhalli",
   :se "Slalomhall",
   :en "Downhill skiing hall"},
  :type-code 4240,
  :main-category 4000,
  :status "active",
  :sub-category 4200,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :altitude-difference {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :equipment-rental? {:priority 70},
   :route-length-km {:priority 99}}},
 2270
 {:description
  {:fi
   "Ensisijaisesti squashin pelaamiseen tarkoitettu halli. Yksittäisen kentän mitat 9,75 m x 6,4 m. Vapaa korkeus ja pintamateriaali ilmoitetaan lisätiedoissa.",
   :se
   "En eller flera squashplaner. Antalet planer i karakteristika.",
   :en
   "One or more squash courts. Number of courts specified in properties."},
  :tags
  {:fi ["squash" "squash-kenttä" "squashkenttä" "squashhalli"]},
  :name {:fi "Squash-halli", :se "Squashhall", :en "Squash hall"},
  :type-code 2270,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :stand-capacity-person {:priority 80},
   :free-use? {:priority 40},
   :tennis-courts-count {:priority 80},
   :field-length-m {:priority 90},
   :badminton-courts-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :padel-courts-count {:priority 80},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :scoreboard? {:priority 70},
   :floorball-fields-count {:priority 80},
   :squash-courts-count {:priority 80},
   :customer-service-point? {:priority 70},
   :volleyball-fields-count {:priority 80},
   :school-use? {:priority 40}}},
 4210
 {:description
  {:fi
   "Pysyvästi lajiin varustettu tila, esim. curlingrata tai curlinghalli.",
   :se "Curlingbana med tak och permanent utrustning för grenen.",
   :en "Covered track permanently equipped for curling."},
  :tags {:fi ["curlinghalli" "curling-halli" "curling-rata"]},
  :name
  {:fi "Curlingrata/-halli", :se "Curlingbana", :en "Curling sheet"},
  :type-code 4210,
  :main-category 4000,
  :status "active",
  :sub-category 4200,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :curling-lanes-count {:priority 90},
   :changing-rooms-m2 {:priority 70}}},
 301
 {:description
  {:fi
   "Päiväsaikainen levähdyspaikka retkeilijöille. Esimerkiksi kodalla tarkoitetaan kotamallista sääsuojaa tai levähdyspaikkaa ja laavu on kaltevakattoinen sääsuoja, joka sisältää tulipaikan. Lisätietoihin merkitään tieto tulentekopaikasta.",
   :se "Viloplats för vandrare under dagtid.",
   :en "Daytime rest stop for hikers."},
  :tags {:fi ["taukopaikka"]},
  :name
  {:fi "Laavu, kota tai kammi",
   :se "Vindskydd eller kåta",
   :en "Lean-to, goahti (Lapp tent shelter) or 'kammi' earth lodge"},
  :type-code 301,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :toilet? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :water-point {:priority 70},
   :free-use? {:priority 60}}},
 111
 {:description
  {:fi
   "Kansallispuistot ovat luonnonsuojelualueita, joiden perustamisesta ja tarkoituksesta on säädetty lailla. Kansallispuistoissa on merkittyjä reittejä, luontopolkuja ja tulentekopaikkoja. Kansallispuistoissa voi myös yöpyä, sillä niissä on telttailualueita tai yöpymiseen tarkoitettuja rakennuksia. LIPAS-aineisto perustuu Metsähallituksen tietoihin.",
   :se
   "Naturskyddsområden med lagstadgad status och uppgift. Areal minst 1000 ha. Källa: Forststyrelsen.",
   :en
   "Nature conservation areas whose establishment and purpose are based on legislation. Min. area 1,000 ha. Source of information  Metsähallitus."},
  :tags {:fi []},
  :name
  {:fi "Kansallispuisto", :se "Nationalpark", :en "National park"},
  :type-code 111,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 4630
 {:description
  {:fi
   "Hiihtokilpailujen järjestämiseen soveltuva kisakeskus, jossa on esimerkiksi lähtö- ja maalialue, huoltotilat ja riittävä latuverkosto.",
   :se "Start- och målområden, serviceutrymmen, spårsystem.",
   :en "Start and finish area, service premises. Tracks."},
  :tags {:fi ["hiihtostadion"]},
  :name
  {:fi "Kilpahiihtokeskus",
   :se "Maastohiihtokeskus",
   :en "Ski competition centre"},
  :type-code 4630,
  :main-category 4000,
  :status "active",
  :sub-category 4600,
  :geometry-type "Point",
  :props
  {:stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :finish-line-camera? {:priority 70},
   :ski-track-traditional? {:priority 80},
   :route-width-m {:priority 98},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shower? {:priority 70},
   :rest-places-count {:priority 70},
   :changing-rooms? {:priority 70},
   :lit-route-length-km {:priority 97},
   :scoreboard? {:priority 70},
   :sauna? {:priority 70},
   :loudspeakers? {:priority 70},
   :accessibility-info {:priority 40},
   :ski-track-freestyle? {:priority 80},
   :route-length-km {:priority 99}}},
 4810
 {:description
  {:fi "Ulkoampumarata yhdelle tai useammalle lajille.",
   :se "Utomhusskjutbana för en eller flera grenar.",
   :en "Outdoor shooting range for one or more sports. "},
  :tags {:fi ["ampumapaikka" "ammunta"]},
  :name
  {:fi "Ampumarata", :se "Skjutbana", :en "Open-air shooting range"},
  :type-code 4810,
  :main-category 4000,
  :status "active",
  :sub-category 4800,
  :geometry-type "Point",
  :props
  {:stand-capacity-person {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :air-gun-shooting? {:priority 80},
   :toilet? {:priority 70},
   :pistol-shooting? {:priority 80},
   :shooting-positions-count {:priority 90},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :rifle-shooting? {:priority 80},
   :year-round-use? {:priority 70},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :shotgun-shooting? {:priority 80},
   :free-rifle-shooting? {:priority 80},
   :ligthing? {:priority 60},
   :accessibility-info {:priority 40},
   :track-length-m {:priority 90}}},
 1540
 {:description
  {:fi
   "Pikaluisteluun varusteltu luistelurata. Radan koko ja pituus lisätään ominaisuustietoihin. Käytössä talvikaudella.",
   :se "Bana för hastighetsåkning. Används under vintersäsongen.",
   :en "Track size and length specified in properties."},
  :tags {:fi ["luistelurata"]},
  :name
  {:fi "Pikaluistelurata",
   :se "Bana för hastighetsåkning på skridsko",
   :en "Speed-skating track"},
  :type-code 1540,
  :main-category 1000,
  :status "active",
  :sub-category 1500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :changing-rooms-m2 {:priority 70},
   :customer-service-point? {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :track-length-m {:priority 90}}},
 5320
 {:description
  {:fi
   "Pääasiassa moottoripyöräilyä varten rakennettu, luonnonmukainen ei-asfalttipintainen alue (esim. enduroreitit ja trial-harjoittelualueet maastoliikennealueilla).",
   :se "Huvudsakligen för motorcykelsport.",
   :en "Mainly for motorcycling."},
  :tags {:fi ["motocross"]},
  :name
  {:fi "Moottoripyöräilyalue",
   :se "Område för motorcykelsport",
   :en "Motorcycling area"},
  :type-code 5320,
  :main-category 5000,
  :status "active",
  :sub-category 5300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :track-length-m {:priority 90}}},
 3210
 {:description
  {:fi
   "Maauimala tai vesipuisto on ulkona sijaitseva, vedenpuhdistusjärjestelmällä varustettu uintiin tarkoitettu ja hoidettu vesistö tai uima-altaita/allas. Lisäksi kohteessa voi olla vesiliukumäkiä.",
   :se "Vattenreningssystem.",
   :en "Water treatment system."},
  :tags {:fi ["uima-allas" "ulkoallas" "ulkouima-allas"]},
  :name
  {:fi "Maauimala/vesipuisto",
   :se "Utebassäng",
   :en "Open-air pool "},
  :type-code 3210,
  :main-category 3000,
  :status "active",
  :sub-category 3200,
  :geometry-type "Point",
  :props
  {:pool-tracks-count {:priority 87},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :pool-width-m {:priority 89},
   :pool-min-depth-m {:priority 80},
   :toilet? {:priority 70},
   :swimming-pool-count {:priority 90},
   :pool-water-area-m2 {:priority 90},
   :pool-length-m {:priority 88},
   :pool-max-depth-m {:priority 80},
   :loudspeakers? {:priority 70},
   :pool-temperature-c {:priority 80},
   :school-use? {:priority 40}}},
 4640
 {:description
  {:fi
   "Hiihdon opetteluun ja harjoitteluun tarkoitettu paikka erityisesti lapsille. Erilaisia harjoittelupaikkoja, kuten latuja, mäkiä ym.",
   :se "Träningsplats för skidåkning, teknikhaster mm.",
   :en
   "Ski training venue, an area of parallel short ski tracks for ski instruction, etc."},
  :tags {:fi []},
  :name
  {:fi "Hiihtomaa", :se "Skidland", :en "Cross-country ski park"},
  :type-code 4640,
  :main-category 4000,
  :status "active",
  :sub-category 4600,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :ski-service? {:priority 70},
   :ski-track-traditional? {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 98},
   :scoreboard? {:priority 70},
   :equipment-rental? {:priority 70},
   :loudspeakers? {:priority 70},
   :accessibility-info {:priority 0},
   :ski-track-freestyle? {:priority 80},
   :school-use? {:priority 40},
   :route-length-km {:priority 99}}},
 1150
 {:description
  {:fi
   "Rullaluistelua, skeittausta, potkulautailua varten varustettu paikka. Ominaisuustiedoissa tarkemmat tiedot kohteesta.",
   :se
   "Plats utrustad för rullskridskoåkning, skejtning och sparkcykelåkning.",
   :en
   "An area equipped  for roller-blading, skateboarding, kick scooting."},
  :tags
  {:fi ["ramppi" "skeittipaikka" "skeittipuisto" "skeittiparkki"]},
  :name
  {:fi "Skeitti-/rullaluistelupaikka",
   :se "Plats för skejtning/rullskridskoåkning",
   :en "Skateboarding/roller-blading rink "},
  :type-code 1150,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:area-m2 {:priority 90},
   :surface-material-info {:priority 80},
   :surface-material {:priority 80},
   :ligthing? {:priority 70},
   :school-use? {:priority 50},
   :free-use? {:priority 50},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :lighting-info {:priority 60}}},
 2310
 {:description
  {:fi
   "Yksittäinen yleisurheilun olosuhde sisätiloissa esim. liikunta- tai monitoimihallin yhteydessä. Suorituspaikat kuvataan lisätiedoissa.",
   :se
   "Fristående, ej i anslutning till en friidrottshall. I karakteristika anges övningsplatserna.",
   :en
   "Stand-alone, not in an athletics hall. Venues specified under properties."},
  :tags {:fi ["yleisurheilu" "juoksurata"]},
  :name
  {:fi "Yksittäinen yleisurheilun suorituspaikka",
   :se "Enstaka övningsplats för friidrott",
   :en "Stand-alone athletics venue"},
  :type-code 2310,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :free-use? {:priority 40},
   :javelin-throw-places-count {:priority 80},
   :sprint-track-length-m {:priority 80},
   :inner-lane-length-m {:priority 80},
   :discus-throw-places {:priority 80},
   :hammer-throw-places-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :polevault-places-count {:priority 80},
   :area-m2 {:priority 90},
   :shotput-count {:priority 80},
   :longjump-places-count {:priority 80},
   :school-use? {:priority 40},
   :highjump-places-count {:priority 80}}},
 5210
 {:description
  {:fi
   "Harraste- tai urheiluilmailuun tarkoitettu alue, esim. lentopaikka.",
   :se "Flygsportarena, t ex en flygplats.",
   :en "Area for air sports, e.g. an airfield."},
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
  :type-code 5210,
  :main-category 5000,
  :status "active",
  :sub-category 5200,
  :geometry-type "Point",
  :props
  {:track-length-m {:priority 90},
   :area-m2 {:priority 90},
   :surface-material-info {:priority 80},
   :surface-material {:priority 80},
   :track-width-m {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 2380
 {:description
  {:fi "Parkouria varten varustettu sisätila.",
   :se "Inomhusutrymme utrustat för parkour.",
   :en "Indoor space equipped for parkour. "},
  :tags {:fi ["parkour"]},
  :name {:fi "Parkour-sali", :se "Parkoursal", :en "Parkour hall"},
  :type-code 2380,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :parkour-hall-equipment-and-structures {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :highest-obstacle-m {:priority 80},
   :auxiliary-training-area? {:priority 70},
   :school-use? {:priority 40}}},
 103
 {:description
  {:fi
   "Voivat sijaita taajaman reunoilla, vyöhykkeittäin taajaman sisällä tai taajaman ulkopuolella. Kohteissa voi olla myös taajamasta lähteviä tai taajamaan palaavia reittejä tai polku- ja reittiverkosto. Kohteet sisältävät vaihtelevaa maastoa ja luonnonmukaisia tai puistomaisia alueita. Kohteet voivat myös sijaita vesialuiden lähellä kuten rannoilla tai saarissa. Kohteiden pääasiallinen käyttö on retkeilyä ja luonnossa virkistäytymistä, mutta niitä voidaan käyttää monipuolisesti erilaisen liikunnan kuten hiihdon, lenkkeilyn tai uinnin harrastamiseen.  Kaavamerkintä esim. VR. HUOM! Uusien liikunta- ja ulkoilupaikkojen lisäksi Ulkoilu-/virkistysalueluokka sisältää ennen vuotta 2024 Ulkoilualueet ja Retkeilyalueet tyyppiluokkiin lisätyt olosuhteet",
   :se
   "Området befinner sig i utkanten av tätorter eller i zoner inom tätorten. På 1-10 kilometers avstånd från bebyggelse. Friluftsområdet används för t.ex. promenader, skidning, joggning, simning. Serverar oftast friluftsaktiviteter för en kommun. Området erbjuder en stor variation av motions möjligheter. Området kan bestå av skog, kärr, åkrar, naturenliga områden och parkliknande delar. Planbeteckning VR.",
   :en
   "On the edge of population centres or zoned within population centres. 1-10 km from residential areas. Used for e.g. walks, skiing, jogging, swimming. Serves usually recreational needs within one municipality, offers versatile sports facilities. May include forest, swamp, fields, natural areas and park areas. Symbol VR."},
  :tags {:fi ["puisto" "virkistysalue"]},
  :name
  {:fi "Ulkoilu-/virkistysalue",
   :se "Friluftsområde",
   :en "Outdoor area"},
  :type-code 103,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :area-km2 {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :water-point {:priority 80}}},
 201
 {:description
  {:fi
   "Vapaa-ajankalastukseen sopiva kohde. Kohteessa voi olla palvelurakenteita.",
   :se
   "Område eller en plats i ett naturligt vattendrag som ställts i ordning för fritidsfiske.",
   :en
   "Natural aquatic destination equipped and maintained for recreational fishing."},
  :tags {:fi ["kalastusalue" "kalastuspaikka"]},
  :name
  {:fi "Kalastuskohde (piste)",
   :se "Område eller plats för fiske",
   :en "Fishing area/spot "},
  :type-code 201,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:toilet? {:priority 80},
   :free-use? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :pier? {:priority 80},
   :customer-service-point? {:priority 80},
   :equipment-rental? {:priority 80}}},
 1220
 {:description
  {:fi
   "Hyvin varusteltu yleisurheilukenttä. Yleissurheilukentällä on ratoja ja yleisurheilun suorituspaikkoja. Myös kisakäyttö on mahdollista. Lyhytrataiset (juoksurata alle 400 m) yleisurheilukentät tallennettaan yleisurheilun harjoitusalueeksi. Yleisurheilukentällä sijaitseva jalkapallon tai muun lajin keskeinen suorituspaikka merkitään omaksi liikuntapaikakseen (esim. jalkapallostadion tai pallokenttä). ",
   :se
   "En plan, banor och träningsplatser för friidrott. Centrum, banor, ytbeläggningar samt träningsplatser med beskrivningar.",
   :en
   "Field, track and athletic venues/facilities. Centre, tracks, surfaces, venues specified in properties. "},
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
  :type-code 1220,
  :main-category 1000,
  :status "active",
  :sub-category 1200,
  :geometry-type "Point",
  :props
  {:heating? {:priority 70},
   :surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :sprint-lanes-count {:priority 80},
   :javelin-throw-places-count {:priority 80},
   :finish-line-camera? {:priority 70},
   :field-length-m {:priority 90},
   :circular-lanes-count {:priority 80},
   :match-clock? {:priority 70},
   :sprint-track-length-m {:priority 80},
   :inner-lane-length-m {:priority 80},
   :discus-throw-places {:priority 80},
   :hammer-throw-places-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :polevault-places-count {:priority 80},
   :toilet? {:priority 70},
   :running-track-surface-material {:priority 80},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :scoreboard? {:priority 70},
   :shotput-count {:priority 80},
   :longjump-places-count {:priority 80},
   :loudspeakers? {:priority 70},
   :customer-service-point? {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :school-use? {:priority 40},
   :highjump-places-count {:priority 80},
   :training-spot-surface-material {:priority 80}}},
 4411
 {:description
  {:fi
   "Maastopyöräilyyn tarkoitettu reitti, joka kulkee vaihtelevassa maastossa ja on merkitty maastoon. Reitti voi hyödyntää muita olemassa olevia ulkoilureittipohjia.",
   :se "Led avsedd framför allt för mountainbikar, märkt.",
   :en "Marked route intended especially for cross-country biking."},
  :tags {:fi []},
  :name
  {:fi "Maastopyöräilyreitti",
   :se "Mountainbikeled",
   :en "Cross-country biking route"},
  :type-code 4411,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 98},
   :school-use? {:priority 40},
   :route-length-km {:priority 99}}},
 1140
 {:description
  {:fi "Parkouria varten varustettu alue.",
   :se "Område utrustat för parkour.",
   :en "An area equipped for parkour."},
  :tags {:fi []},
  :name
  {:fi "Parkour-alue", :se "Parkourområde", :en "Parkour area"},
  :type-code 1140,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 50},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 60},
   :highest-obstacle-m {:priority 80},
   :ligthing? {:priority 70},
   :climbing-wall? {:priority 80},
   :school-use? {:priority 50}}},
 4520
 {:description
  {:fi
   "Hiihtosuunnistukseen soveltuva alue, alueesta hiihtosuunnistuskartta saatavilla.",
   :se
   "Skidorienteringskarta över området, ej för sommarorientering.",
   :en
   "A ski orienteering map of the area available; no summer orienteering."},
  :tags {:fi []},
  :name
  {:fi "Hiihtosuunnistusalue",
   :se "Skidorienteringsområde",
   :en "Ski orienteering area"},
  :type-code 4520,
  :main-category 4000,
  :status "deprecated",
  :sub-category 4500,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :school-use? {:priority 40},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 107
 {:description
  {:fi
   "Matkailupalvelujen alueet ovat matkailua palveleville toiminnoille varattuja alueita, jotka sisältävät myös sisäiset liikenneväylät ja -alueet, alueen toimintoja varten tarpeelliset palvelut ja virkistysalueet sekä yhdyskuntateknisen huollon alueet. Kohteet voivat toimia myös retkeilyauto- ja pyörämatkailijoiden tauko- ja yöpymispaikkoina. Kaavamerkintä RM.",
   :se
   "Områden med turisttjänster har reserverats för turist- och semestercentra, semesterbyar, semesterhotell och motsvarande aktörer. De har egna trafikleder och -områden samt områden för egna serviceenheter och egen infrastruktur för aktiviteter.  Planbeteckning RM.",
   :en
   "Area reserved for tourism and holiday centres, holiday villages, hotels, etc., also including internal traffic routes and areas; services and recreational areas needed for operations, as well as technical maintenance areas. Symbol RM."},
  :tags {:fi ["ulkoilualue" "virkistysalue" "leirintäalue"]},
  :name
  {:fi "Matkailupalveluiden alue",
   :se "Område med turisttjänster",
   :en "Tourist services area"},
  :type-code 107,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:area-km2 {:priority 90},
   :free-use? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 80}}},
 6110
 {:description
  {:fi "Ratsastukseen varustettu kenttä.",
   :se "Bana avsedd för ridning. Storlek i karakteristika.",
   :en
   "Field reserved for horseback riding. Size specified in properties."},
  :tags {:fi []},
  :name
  {:fi "Ratsastuskenttä", :se "Ridbana", :en "Equestrian field"},
  :type-code 6110,
  :main-category 6000,
  :status "active",
  :sub-category 6100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :show-jumping? {:priority 80},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :ligthing? {:priority 60},
   :school-use? {:priority 40}}},
 1120
 {:description
  {:fi
   "Lähiliikuntapaikka on tarkoitettu päivittäiseen ulkoiluun ja liikuntaan. Se sijaitsee asutuksen läheisyydessä, on pienimuotoinen ja alueelle on vapaa pääsy. Yleensä tarjolla on erilaisia suorituspaikkoja. Suorituspaikat tulee tallentaa omiksi liikuntapaikoikseen (esim. pallokenttä, ulkokuntosali tai parkour-alue). Lähiliikuntapaikka voi olla myös koulun tai päiväkodin piha, jos liikuntapaikan käyttö on mahdollista kouluajan ulkopuolella.",
   :se
   "Ett näridrottsområde är avsett för daglig utomhusaktivitet och motion. Det ligger nära bostadsområden, är småskaligt och har fri tillgång. Vanligtvis erbjuds olika aktivitetsplatser. Aktivitetsplatserna bör registreras som egna idrottsplatser (t.ex. bollplan, utomhusgym eller parkourområde). Ett näridrottsområde kan också vara en skolgård eller en daghemsgård om idrottsplatsen kan användas utanför skoltid.",
   :en
   "A local sports facility is intended for daily outdoor activities and exercise. It is located near residential areas, is small-scale, and has free access. Usually, various activity sites are available. Activity sites should be recorded as individual sports facilities (e.g., ball field, outdoor gym, or parkour area). A local sports facility can also be a school or daycare yard if the sports facility can be used outside school hours."},
  :tags
  {:fi
   ["ässäkenttä" "monitoimikenttä" "monitoimikaukalo" "lähipuisto"]},
  :name
  {:fi "Lähiliikuntapaikka",
   :se "Näridrottsplats",
   :en "Neighbourhood sports area"},
  :type-code 1120,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 90},
   :surface-material-info {:priority 90},
   :fields-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :ice-rinks-count {:priority 80},
   :area-m2 {:priority 90},
   :lighting-info {:priority 60},
   :ligthing? {:priority 70},
   :accessibility-info {:priority 50},
   :playground? {:priority 80},
   :school-use? {:priority 50},
   :exercise-machines-count {:priority 80}}},
 1390
 {:description
  {:fi
   "Padelin pelaamiseen tarkoitettu kenttä ulkona. Pintamateriaali hiekkatekonurmi. Lajivaatimusten mukaiset seinät. Voi olla myös katettu.",
   :se
   "En utomhusbana avsedd för padelspel. Underlagsmaterialet är sandkonstgräs. Väggar enligt sportens krav. Kan även vara täckt.",
   :en
   "An outdoor court intended for playing padel. The surface material is sand artificial grass. Walls meet the sport's requirements. It can also be covered."},
  :tags {:fi ["padel" "padel-kenttä"]},
  :name
  {:fi "Padelkenttä",
   :se "Område med padelbanor",
   :en "Padel court area"},
  :type-code 1390,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :light-roof? {:priority 70}}},
 5340
 {:description
  {:fi "Pääasiallisesti kartingajoon tai supermotoon käytetty rata.",
   :se "Huvudsakligen för karting.",
   :en "Mainly for karting."},
  :tags {:fi []},
  :name {:fi "Karting-rata", :se "Kartingbana", :en "Kart circuit"},
  :type-code 5340,
  :main-category 5000,
  :status "active",
  :sub-category 5300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :free-use? {:priority 40},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :ligthing? {:priority 60},
   :track-length-m {:priority 90}}},
 302
 {:description
  {:fi
   "Autiotupa, varaustupa, taukotupa, päivätupa. Yöpymis- ja levähdyspaikka retkeilijöille. Autiotupa avoin, varaustupa lukittu ja maksullinen. Päivätupa päiväkäyttöön.",
   :se
   "Övernattningsstuga, reserveringsstuga, raststuga, dagstuga. Övernattnings- och rastplats för vandrare. Övernattningsstugan öppen, reserveringsstugan låst och avgiftsbelagd. Dagstuga för bruk under dagen.",
   :en
   "Open hut, reservable hut, rest hut, day hut. Overnight resting place for hikers. An open hut is freely available; a reservable hut locked and subject to a charge. A day hut is for daytime use."},
  :tags {:fi ["taukopaikka"]},
  :name {:fi "Tupa", :se "Stuga", :en "Hut"},
  :type-code 302,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :toilet? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :water-point {:priority 70},
   :free-use? {:priority 60}}},
 4405
 {:description
  {:fi
   "Maastossa oleva retkeilyreitti, yleensä kauempana asutuksesta. Reitin varrella retkeilyn palveluita, esim. laavuja.",
   :se
   "Utflyktsled i terrängen, oftast längre borta från bebyggelse. Längs rutten friluftstjänster, t ex vindskydd.",
   :en
   "Natural hiking route, usually further away from residential areas. Provides hiking facilities, e.g. lean-to structures."},
  :tags {:fi ["retkeily" "vaellus" "vaelluspolku"]},
  :name
  {:fi "Retkeilyreitti", :se "Utflyktsled", :en "Hiking route"},
  :type-code 4405,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 98},
   :accessibility-info {:priority 40},
   :route-length-km {:priority 99}}},
 6120
 {:description
  {:fi "Kylmä tai lämmin katettu tila ratsastukseen.",
   :se "Kallt eller varmt takförsett utrymme för ridning.",
   :en "Cold or warm, covered space for horseback riding."},
  :tags {:fi ["ratsastushalli" "maneesi"]},
  :name
  {:fi "Ratsastusmaneesi", :se "Ridmanege", :en "Riding manège"},
  :type-code 6120,
  :main-category 6000,
  :status "active",
  :sub-category 6100,
  :geometry-type "Point",
  :props
  {:heating? {:priority 70},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :show-jumping? {:priority 80},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :school-use? {:priority 40}}},
 4407
 {:description
  {:fi
   "Asfaltoitu rullahiihtoon lumettomana aikana tarkoitettu reitti. Reitti kulkee maastossa, ja sen käyttöä muilla kulkutavoilla on rajoitettu.",
   :se
   "En asfalterad bana avsedd för rullskidåkning under snöfria perioder. Banan går genom terrängen och användningen av andra färdsätt är begränsad.",
   :en
   "An asphalt track intended for roller skiing during snow-free periods. The track runs through terrain, and the use of other modes of travel is restricted."},
  :tags
  {:fi ["rullahiihto"],
   :se ["rullskidåkning"],
   :en ["roller skiing"]},
  :name
  {:fi "Rullahiihtorata",
   :se "Rullskidbana",
   :en "Roller Ski Track"},
  :type-code 4407,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :free-use? {:priority 40},
   :outdoor-exercise-machines? {:priority 80},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 98},
   :route-length-km {:priority 99}}},
 1310
 {:description
  {:fi
   "Koripalloon varustettu kenttä, kiinteät tai siirrettävät koritelineet.  Minikenttä ja yhden korin kenttä lisätiedoissa.",
   :se
   "Plan utrustad för basket med fasta eller flyttbara ställningar. Miniplan och enkorgsplan i tilläggsupgifter.",
   :en
   "A field equipped for basketball, with fixed or movable apparatus. 'Mini-court' and 'one-basket court'  included in additional information. "},
  :tags {:fi []},
  :name
  {:fi "Koripallokenttä", :se "Basketplan", :en "Basketball court"},
  :type-code 1310,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :height-of-basket-or-net-adjustable? {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 40},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :scoreboard? {:priority 70},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :basketball-field-type {:priority 80},
   :school-use? {:priority 40}}},
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
  :type-code 202,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:toilet? {:priority 70},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :water-point {:priority 70},
   :free-use? {:priority 70}}},
 1190
 {:description
  {:fi
   "Yleinen mäenlaskuun esimerkiksi pulkalla tai liukurilla tarkoitettu mäki. Kohde on ylläpidetty ja hoidettu, ja se voi muodostua luonnon mäestä tai rakennetuista kumpareista.",
   :se
   "En allmän backe avsedd för åkning med till exempel pulka eller stjärtlapp. Backen är underhållen och skött och kan bestå av en naturlig backe eller konstruerade högar.",
   :en
   "A common hill intended for sledding with, for example, a sled or a slider. The hill is maintained and taken care of, and it can consist of a natural hill or constructed mounds."},
  :tags {:fi ["pulkkailu" "pulkka" "mäenlasku"]},
  :name {:fi "Pulkkamäki", :se "Pulkabacke", :en "Sledding hill"},
  :type-code 1190,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:ligthing? {:priority 70},
   :school-use? {:priority 50},
   :may-be-shown-in-harrastuspassi-fi? {:priority 50},
   :toilet? {:priority 70},
   :lighting-info {:priority 60}}},
 1620
 {:description
  {:fi
   "Ensisijaisesti golfin pelaamiseen tarkoitettu alue kesäkaudella. Reikien määrä merkitään lisätietoihin.",
   :se "Officiell golfbana. Antalet hål anges i karakteristika.",
   :en
   "Official golf course. Number of holes included in properties."},
  :tags {:fi ["greeni" "puttialue" "range"]},
  :name
  {:fi "Golfkenttä (piste)", :se "Golfbana", :en "Golf course"},
  :type-code 1620,
  :main-category 1000,
  :status "active",
  :sub-category 1600,
  :geometry-type "Point",
  :props
  {:holes-count {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 70},
   :toilet? {:priority 70},
   :lighting-info {:priority 50},
   :customer-service-point? {:priority 70},
   :green? {:priority 80},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :range? {:priority 80}}},
 2250
 {:description
  {:fi
   "Ensisijaisesti skeittausta varten varustettu halli. Hallia voidaan käyttää bmx-pyöräilyn tai muiden soveltuvien lajien harrastamiseen.",
   :se
   "Hall utrustad för skejtning, rullskridskoåkning, bmx-åkning osv.",
   :en
   "An area for skateboarding, roller-blading, BMX biking, etc."},
  :tags {:fi ["ramppi"]},
  :name
  {:fi "Skeittihalli", :se "Skateboardhall", :en "Indoor skatepark"},
  :type-code 2250,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :scoreboard? {:priority 70},
   :customer-service-point? {:priority 70},
   :school-use? {:priority 40}}},
 2530
 {:description
  {:fi "Pikaluisteluun tarkoitettu halli.",
   :se
   "Hall avsedd för hastighetsåkning på skridsko. Storlek > 333 1/3 m.",
   :en "Hall intended for speed-skating. Size > 333.3 m."},
  :tags {:fi ["jäähalli"]},
  :name
  {:fi "Pikaluisteluhalli",
   :se "Skridskohall",
   :en "Speed-skating hall"},
  :type-code 2530,
  :main-category 2000,
  :status "active",
  :sub-category 2500,
  :geometry-type "Point",
  :props
  {:field-2-area-m2 {:priority 80},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :field-3-length-m {:priority 80},
   :field-2-length-m {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-1-length-m {:priority 80},
   :finish-line-camera? {:priority 70},
   :match-clock? {:priority 70},
   :field-1-width-m {:priority 80},
   :field-3-width-m {:priority 80},
   :field-2-width-m {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :ice-rinks-count {:priority 80},
   :field-1-area-m2 {:priority 80},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :scoreboard? {:priority 70},
   :auxiliary-training-area? {:priority 80},
   :loudspeakers? {:priority 70},
   :field-3-area-m2 {:priority 80},
   :school-use? {:priority 40}}},
 112
 {:description
  {:fi
   "Muut luonnonsuojelualueet kuin kansallispuistot. Tietoja kerätään vain sellaisilta luonnonsuojelualueilta ja luonnonpuistoilta, joiden virkistyskäyttö on mahdollista. Esim. kunta- tai yksityisomisteisille maille perustetut suojelualueet. Kaavamerkintä S, SL.",
   :se
   "Andra naturskyddsområden än nationalparker och naturparker. Endast de naturskyddsområden där friluftsanvändning är möjlig. T ex skyddsområden som grundats på kommunal eller privat mark. Planbeteckning S, SL.",
   :en
   "Nature conservation areas other than national parks and natural parks. Only nature conservation areas with opportunities for recreation. E.g. protection areas established on municipal and private land. Symbol S, SL."},
  :tags {:fi ["virkistysalue"]},
  :name
  {:fi "Muu luonnonsuojelualue, jolla on virkistyspalveluita",
   :se "Annat naturskyddsområde med rekreationstjänster",
   :en "Other nature conservation area with recreational services"},
  :type-code 112,
  :main-category 0,
  :status "active",
  :sub-category 1,
  :geometry-type "Polygon",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :area-km2 {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 2130
 {:description
  {:fi
   "Painonnostoon tai toiminnalliseen voimaharjoitteluun varustettu kuntoilutila tai voimailusali. Esimerkiksi crossfit- ja painonnostosalit.",
   :se
   "Utrustad för tyngdlyftning och boxning. Storleken anges i karakteristika.",
   :en
   "Equipped for weightlifting and boxing. Size specified in properties."},
  :tags {:fi ["kuntosali" "kuntoilu" "painonnosto" "voimanosto"]},
  :name
  {:fi "Voimailusali",
   :se "Styrketräningssal",
   :en "Weight training hall "},
  :type-code 2130,
  :main-category 2000,
  :status "active",
  :sub-category 2100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-customer-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :group-exercise-rooms-count {:priority 80},
   :tatamis-count {:priority 80},
   :area-m2 {:priority 90},
   :wrestling-mats-count {:priority 80},
   :boxing-rings-count {:priority 80},
   :weight-lifting-spots-count {:priority 80},
   :customer-service-point? {:priority 70},
   :school-use? {:priority 40},
   :exercise-machines-count {:priority 80}}},
 4406
 {:description
  {:fi
   "Talvisin tai ympärivuotisesti käytössä oleva maastoreitti, joka soveltuu usealle kulkutavalle (esim. jalan, lumikengille, läskipyörälle). Lisätietoihin merkitään, jos reitti on ympärivuotisessa käytössä ja mahdolliset kulkutavat.",
   :se
   "En terrängled som används på vintern eller året runt och som är lämplig för flera färdsätt (t.ex. till fots, med snöskor, fatbike). Ytterligare information anger om leden är i bruk året runt och möjliga färdsätt.",
   :en
   "A terrain trail used in winter or year-round, suitable for multiple modes of travel (e.g., on foot, with snowshoes, fat bike). Additional information indicates if the trail is in year-round use and possible modes of travel."},
  :tags
  {:fi ["yhteiskäyttöreitti" "talvipolku"],
   :se ["gemensam led" "vinterstig"],
   :en ["shared trail" "winter path"]},
  :name
  {:fi "Monikäyttöreitti",
   :se "Multianvändningsled",
   :en "Multi-use Trail"},
  :type-code 4406,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :travel-mode-info {:priority 80},
   :route-width-m {:priority 97},
   :toilet? {:priority 70},
   :rest-places-count {:priority 80},
   :lit-route-length-km {:priority 98},
   :travel-modes {:priority 80},
   :year-round-use? {:priority 80},
   :route-length-km {:priority 99}}},
 3220
 {:description
  {:fi
   "Yleinen uimaranta, EU-uimaranta. Pelastusväline ja ilmoitustaulu, jäteastia ja käymälä. Veden laadun seuranta ja alueen hoito järjestetty.",
   :se
   "Allmän badstrand, EU badstrand. Räddningsutrustning, en anslagstavla samt ett sopkärl och en toalett finns. Kvaliteten på vattnet följs upp och området underhålls.",
   :en
   "Public beach, EU bathing beach. Rescue equipment, a notice board, a waste bin, and a toilet are available. The quality of the water is monitored and the area is maintained."},
  :tags {:fi ["uimapaikka"]},
  :name {:fi "Uimaranta", :se "Badstrand", :en "Public beach"},
  :type-code 3220,
  :main-category 3000,
  :status "active",
  :sub-category 3200,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :pier? {:priority 80},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :beach-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shower? {:priority 70},
   :changing-rooms? {:priority 70},
   :sauna? {:priority 70},
   :other-platforms? {:priority 80},
   :school-use? {:priority 40}}},
 5330
 {:description
  {:fi
   "Suuri rata-autoiluun tai moottoripyöräilyyn tarkoitettu asfaltoitu moottoriurheilupaikka.",
   :se "Stor motorsportplats avsedd för bankörning.",
   :en "Large motor sports venue for formula racing."},
  :tags {:fi ["autourheilu"]},
  :name
  {:fi "Moottorirata", :se "Motorbana", :en "Formula race track"},
  :type-code 5330,
  :main-category 5000,
  :status "active",
  :sub-category 5300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :ligthing? {:priority 60},
   :track-length-m {:priority 90}}},
 4230
 {:description
  {:fi "Lumilautailua varten rakennettu halli.",
   :se
   "Tunnel avsedd för snowboardåkning. Olika användningsmöjligheter i tilläggsinformation.",
   :en
   "Tunnel intended for snowboarding. Different uses specified in additional information."},
  :tags {:fi ["laskettelu"]},
  :name
  {:fi "Lumilautatunneli",
   :se "Snowboardtunnel",
   :en "Snowboarding tunnel"},
  :type-code 4230,
  :main-category 4000,
  :status "active",
  :sub-category 4200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :altitude-difference {:priority 90},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :equipment-rental? {:priority 70},
   :route-length-km {:priority 99}}},
 4320
 {:description
  {:fi
   "Mäkihyppyyn soveltuva mäki, vauhtimäessä on jää-, keramiikka- tai muovilatu. Mäen koko, materiaalit ja mahdollinen kesäkäyttö kuvataan lisätiedoissa.",
   :se
   "Is-, keramik- eller plastspår. K-punkt samt sommar- och vinteranvändning i karakteristika. Minimikrav: en liten backe med k-punkt på 75 m eller mera.",
   :en
   "Ice, ceramic or plastic track. Summer and winter use specified in attributes, along with K point, etc. Minimum normal hill, K point minimum 75 m."},
  :tags {:fi ["mäkihyppy" "hyppyri"]},
  :name {:fi "Hyppyrimäki", :se "Hoppbacke", :en "Ski jumping hill"},
  :type-code 4320,
  :main-category 4000,
  :status "active",
  :sub-category 4300,
  :geometry-type "Point",
  :props
  {:skijump-hill-type {:priority 80},
   :lifts-count {:priority 70},
   :plastic-outrun? {:priority 80},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :skijump-hill-material {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :hs-point {:priority 90},
   :k-point {:priority 90},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :year-round-use? {:priority 80},
   :changing-rooms-m2 {:priority 70},
   :jumps-count {:priority 80},
   :inruns-material {:priority 80}}},
 3130
 {:description
  {:fi
   "Monipuolinen uimala kuntoutus-, virkistys- tai hyvinvointipalveluilla. Vesipinta-ala ja allasmäärät/tyypit ominaisuuksiin.",
   :se
   "Mångsidig badinrättning med rehabiliterings- och rekreationstjänster. Vattenareal samt antal och typ av bassänger i karakteristika.",
   :en
   "Versatile spa with rehabilitation or wellness services. Water volume and number/types of pools specified in properties."},
  :tags {:fi []},
  :name {:fi "Kylpylä", :se "Badinrättning", :en "Spa"},
  :type-code 3130,
  :main-category 3000,
  :status "active",
  :sub-category 3100,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :swimming-pool-count {:priority 90},
   :pool-water-area-m2 {:priority 90},
   :accessibility-info {:priority 40},
   :school-use? {:priority 40}}},
 3110
 {:description
  {:fi
   "Halli, jossa on yksi tai useampia uima-altaita. Altaiden määrä ja vesipinta-ala kysytään ominaisuustiedoissa.",
   :se
   "Hall med en eller flera simbassänger. Antalet bassänger och vattenareal anges i karakteristika.",
   :en
   "Hall with one or several swimming pools. Number of pools and water surface area is requested in properties."},
  :tags {:fi []},
  :name
  {:fi "Uimahalli",
   :se "Simhall",
   :en "Public indoor swimming pool"},
  :type-code 3110,
  :main-category 3000,
  :status "active",
  :sub-category 3100,
  :geometry-type "Point",
  :props
  {:automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :swimming-pool-count {:priority 90},
   :pool-water-area-m2 {:priority 90},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :accessibility-info {:priority 40},
   :school-use? {:priority 40}}},
 203
 {:description
  {:fi
   "Kohteessa on veneilyyn liittyviä palveluita kuten säilytysmahdollisuus, vesillelaskupaikka tai veneen kiinnitysmahdollisuus. Kohteelle määritetään venesatamaluokka, jonka palveluvarustus kuvataan lisätiedoissa. Jos kyse on melontalaiturista, se kirjataan kyseisen laituriluokan alle. Kohde tulee merkitä tärkeimmän laiturin läheisyyteen, jos sellainen kohteessa on.",
   :se "Tjänster för båtfarare. Precisering i karakteristika.",
   :en "Facilities related to boating. Specififed in 'attributes'."},
  :tags {:fi ["satama" "laituri"]},
  :name
  {:fi "Veneilyn palvelupaikka",
   :se "Serviceplats för båtfarare",
   :en "Boating services"},
  :type-code 203,
  :main-category 0,
  :status "active",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 70},
   :pier? {:priority 80},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :boat-launching-spot? {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :boating-service-class {:priority 90},
   :customer-service-point? {:priority 80},
   :water-point {:priority 70},
   :accessibility-info {:priority 70}}},
 4620
 {:description
  {:fi
   "Vähintään kansallisen tason kilpailujen järjestämiseen soveltuva ampumahiihtokeskus. Ampumahiihtokeskuksessa useita ampumapaikkoja ja latuverkosto.",
   :se "Tillräckligt stort för åtminstone nationella tävlingar.",
   :en "For minimum national level competitions."},
  :tags {:fi ["ampumapaikka"]},
  :name
  {:fi "Ampumahiihtokeskus",
   :se "Skidskyttecentrum",
   :en "Biathlon centre"},
  :type-code 4620,
  :main-category 4000,
  :status "active",
  :sub-category 4600,
  :geometry-type "Point",
  :props
  {:stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :finish-line-camera? {:priority 70},
   :ski-track-traditional? {:priority 80},
   :route-width-m {:priority 98},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :shower? {:priority 70},
   :rest-places-count {:priority 70},
   :changing-rooms? {:priority 70},
   :shooting-positions-count {:priority 80},
   :lit-route-length-km {:priority 97},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :sauna? {:priority 70},
   :loudspeakers? {:priority 70},
   :accessibility-info {:priority 40},
   :ski-track-freestyle? {:priority 80},
   :route-length-km {:priority 99}}},
 5360
 {:description
  {:fi
   "Pääasiallisesti jokamiesajoa, rallicrossia tai moottoripyöräilyä varten.",
   :se "Huvudsakligen för allemanskörning och/eller rallycross.",
   :en "Mainly for everyman racing and/or rallycross."},
  :tags {:fi []},
  :name
  {:fi "Jokamies- ja rallicross-rata",
   :se "Allemans- och rallycrossbana",
   :en "Everyman racing and rallycross track "},
  :type-code 5360,
  :main-category 5000,
  :status "active",
  :sub-category 5300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :ligthing? {:priority 60},
   :covered-stand-person-count {:priority 70},
   :track-length-m {:priority 90}}},
 2290
 {:description
  {:fi "Petanque-peliin tarkoitettu halli.",
   :se
   "Hall avsedd för petanque. Storlek, antalet planer och utrustning i karakteristika.",
   :en "Hall intended for petanque."},
  :tags {:fi ["petankki"]},
  :name
  {:fi "Petanque-halli", :se "Petanquehall", :en "Petanque hall"},
  :type-code 2290,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :fields-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :scoreboard? {:priority 70},
   :customer-service-point? {:priority 70},
   :school-use? {:priority 40}}},
 2260
 {:description
  {:fi
   "Ensisijaisesti sulkapallon pelaamiseen tarkoitettu halli. Vapaa korkeus ilmoitetaan lisätiedoissa.",
   :se "Hall i första hand  avsedd för badminton.",
   :en "Hall intended primarily for badminton."},
  :tags {:fi []},
  :name
  {:fi "Sulkapallohalli", :se "Badmintonhall", :en "Badminton hall"},
  :type-code 2260,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :tennis-courts-count {:priority 80},
   :field-length-m {:priority 90},
   :badminton-courts-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :padel-courts-count {:priority 80},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :scoreboard? {:priority 70},
   :floorball-fields-count {:priority 80},
   :squash-courts-count {:priority 80},
   :customer-service-point? {:priority 70},
   :volleyball-fields-count {:priority 80},
   :school-use? {:priority 40}}},
 1160
 {:description
  {:fi
   "Pyöräilyä ja temppuilua varten varattu alue, esim. bmx-, pump track- tai dirt-pyöräilyalue.",
   :se
   "Ett område avsett för cykling och trick, till exempel BMX-, pump track- eller dirtcyklingsområde.",
   :en
   "An area designated for cycling and stunts, such as a BMX, pump track, or dirt biking area."},
  :tags {:fi ["pumptrack" "bmx" "pump" "track"]},
  :name
  {:fi "Pyöräilyalue", :se "Cykelåkningsområde", :en "Cycling area"},
  :type-code 1160,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 50},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :lighting-info {:priority 60},
   :customer-service-point? {:priority 70},
   :ligthing? {:priority 70},
   :accessibility-info {:priority 50},
   :covered-stand-person-count {:priority 70},
   :school-use? {:priority 50}}},
 1210
 {:description
  {:fi
   "Yleisurheilun harjoitusalueeksi merkitään kohde, jossa on yleisurheilun harjoitteluun soveltuvia suorituspaikkoja, esim. kenttä, ratoja tai eri lajien suorituspaikkoja, mutta ei virallisen yleisurheilukentän kaikkia suorituspaikkoja. Lyhytrataiset (juoksurata alle 400 m) yleisurheilukentät tallennetaan yleisurheilun harjoitusalueeksi.",
   :se
   "Ett område för friidrottsträning markeras där det finns anläggningar som är lämpliga för friidrottsträning, till exempel en plan, banor eller platser för olika grenar, men inte alla anläggningar för en officiell friidrottsarena. Kortbaniga friidrottsarenor (löparbana under 400 m) registreras som friidrottsträningsområden.",
   :en
   "An athletics training area is designated for locations with facilities suitable for athletics training, such as a field, tracks, or various event areas, but not all facilities of an official athletics stadium. Short track athletics fields (running track under 400 m) are recorded as athletics training areas."},
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
  :type-code 1210,
  :main-category 1000,
  :status "active",
  :sub-category 1200,
  :geometry-type "Point",
  :props
  {:heating? {:priority 70},
   :surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :free-use? {:priority 50},
   :sprint-lanes-count {:priority 80},
   :javelin-throw-places-count {:priority 80},
   :field-length-m {:priority 90},
   :circular-lanes-count {:priority 80},
   :sprint-track-length-m {:priority 80},
   :inner-lane-length-m {:priority 80},
   :discus-throw-places {:priority 80},
   :hammer-throw-places-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :polevault-places-count {:priority 80},
   :toilet? {:priority 70},
   :running-track-surface-material {:priority 80},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 60},
   :shotput-count {:priority 80},
   :longjump-places-count {:priority 80},
   :ligthing? {:priority 70},
   :school-use? {:priority 50},
   :highjump-places-count {:priority 80},
   :training-spot-surface-material {:priority 80}}},
 5140
 {:description
  {:fi
   "Rakennettu pysyvästi vesihiihdolle. Vesihiihtoalueella on laituri.",
   :se "Byggt för vattenskidåkning, permanent. Minimikrav: brygga.",
   :en
   "Permanent construction for water skiing. Minimum equipment  pier."},
  :tags {:fi []},
  :name
  {:fi "Vesihiihtoalue",
   :se "Område för vattenskidåkning",
   :en "Water ski area"},
  :type-code 5140,
  :main-category 5000,
  :status "active",
  :sub-category 5100,
  :geometry-type "Point",
  :props
  {:pier? {:priority 80},
   :area-km2 {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 4310
 {:description
  {:fi
   "Mäkihypyn harjoitteluun rakennettu mäki. K-piste ominaisuustietoihin, materiaalit, kesä- ja talvikäyttö ominaisuuksiin. ",
   :se
   "K-punkt, material samt sommar- och vinteranvändning i karakteristika.",
   :en
   "K point in properties; materials, summer and winter use specified in attributes. "},
  :tags {:fi ["mäkihyppy" "hyppyri" "hyppyrimäki"]},
  :name
  {:fi "Harjoitushyppyrimäki",
   :se "Träningshoppbacke",
   :en "Ski jumping hill for training"},
  :type-code 4310,
  :main-category 4000,
  :status "deprecated",
  :sub-category 4300,
  :geometry-type "Point",
  :props
  {:skijump-hill-type {:priority 80},
   :lifts-count {:priority 70},
   :plastic-outrun? {:priority 80},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :skijump-hill-material {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :k-point {:priority 90},
   :toilet? {:priority 70},
   :year-round-use? {:priority 80},
   :p-point {:priority 90},
   :inruns-material {:priority 80},
   :school-use? {:priority 40}}},
 207
 {:description
  {:fi
   "Opastuspiste on ulkoilureitin, virkistysalueen tai muun liikuntapaikan yhteydessä oleva lisätieto. Paikassa voi olla esimerkiksi opastustaulu ja kartta tai laajempi palvelupiste. Opastuspiste-merkintää voidaan käyttää myös ilmoittamaan reitin lähtöpisteen.",
   :se
   "En informationspunkt är en extra information vid en friluftsled, ett rekreationsområde eller en annan idrottsplats. På platsen kan det till exempel finnas en informationstavla och karta eller en mer omfattande servicestation. Informationspunkt-markeringen kan också användas för att ange startpunkten för en rutt.",
   :en
   "An information point is additional information associated with an outdoor trail, recreational area, or other sports facility. The location may include, for example, an information board and map or a more extensive service point. The information point marking can also be used to indicate the starting point of a route."},
  :tags {:fi ["info" "opastaulu" "infopiste"]},
  :name {:fi "Opastuspiste", :se "Informationspunkt", :en "Info"},
  :type-code 207,
  :main-category 0,
  :status "deprecated",
  :sub-category 2,
  :geometry-type "Point",
  :props
  {:parking-place? {:priority 70},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :toilet? {:priority 70},
   :school-use? {:priority 60},
   :free-use? {:priority 60},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 1130
 {:description
  {:fi
   "Ulkokuntoilupaikka on esimerkiksi kuntoilulaitteita, voimailulaitteita tai kuntoportaat sisältävä liikuntapaikka. Kohde voi olla osa liikuntapuistoa, liikuntareitin varrella oleva kuntoilupaikka tai ns. ulkokuntosali.",
   :se
   "En utomhusträningsplats är en idrottsplats som innehåller till exempel träningsutrustning, styrketräningsutrustning eller träningsstegar. Platsen kan vara en del av en idrottspark, en träningsplats längs en motionsslinga eller en så kallad utomhusgym.",
   :en
   "An outdoor fitness area is a sports facility that includes, for example, exercise equipment, strength training equipment, or fitness stairs. The location can be part of a sports park, a fitness spot along a trail, or a so-called outdoor gym"},
  :tags
  {:fi
   ["kuntoilulaite"
    "ulkokuntoilupiste"
    "kuntoilupiste"
    "kuntoilupaikka"
    "kuntoportaat"]},
  :name
  {:fi "Ulkokuntoilupaikka",
   :se "Konditionspark för utomhusaktiviteter",
   :en "Street workout park"},
  :type-code 1130,
  :main-category 1000,
  :status "active",
  :sub-category 1100,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 0},
   :fitness-stairs-length-m {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :lighting-info {:priority 60},
   :ligthing? {:priority 70},
   :playground? {:priority 80},
   :school-use? {:priority 0},
   :exercise-machines-count {:priority 80}}},
 5120
 {:description
  {:fi "Pysyvästi purjehdusta varten varustettu alue.",
   :se "Byggt för segling, permanent.",
   :en "Permanent construction for sailing."},
  :tags {:fi []},
  :name
  {:fi "Purjehdusalue", :se "Seglingsområde", :en "Sailing area"},
  :type-code 5120,
  :main-category 5000,
  :status "active",
  :sub-category 5100,
  :geometry-type "Point",
  :props
  {:area-km2 {:priority 90},
   :pier? {:priority 80},
   :boat-places-count {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 4110
 {:description
  {:fi
   "Laskettelun suorituspaikka on lasketteluun tai lumilautailuun tarkoitettu liikuntapaikka, esim. laskettelukeskus. Laskettelun suorituspaikassa voi olla laskettelurinteitä, parkkeja tai muita rinnerakenteita ja hiihtohissejä.",
   :se
   "Slalombacke, rodelbana, pipe, puckelpist, freestyle ramp, trickbana.",
   :en "Ski slopes, half pipes and other slope structures."},
  :tags {:fi ["rinne" "laskettelu" "laskettelurinne"]},
  :name
  {:fi "Laskettelun suorituspaikka",
   :se "Slalombackar och alpina skidcentra",
   :en "Ski slopes and downhill ski resorts"},
  :type-code 4110,
  :main-category 4000,
  :status "active",
  :sub-category 4100,
  :geometry-type "Point",
  :props
  {:lifts-count {:priority 80},
   :freestyle-slope? {:priority 80},
   :free-use? {:priority 40},
   :ski-service? {:priority 70},
   :sledding-hill? {:priority 80},
   :longest-slope-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :snowpark-or-street? {:priority 80},
   :max-vertical-difference {:priority 90},
   :toilet? {:priority 70},
   :halfpipe-count {:priority 80},
   :equipment-rental? {:priority 70},
   :slopes-count {:priority 90},
   :shortest-slope-m {:priority 90},
   :jumps-count {:priority 80},
   :customer-service-point? {:priority 70},
   :lit-slopes-count {:priority 80},
   :accessibility-info {:priority 40}}},
 4452
 {:description
  {:fi
   "Merkitty vesireitti, ei kuitenkaan veneväylä. Vesireitti on reittiehdotus-tyyppinen suositeltu vesiretkeilyyn soveltuva reitti. Esim. kirkkovenesoutureitti.",
   :se
   "Märkt vattenled, endast ruttförslag t ex för kyrkbåtsrodd, inte som farled för småbåtar.",
   :en
   "Marked water route, not a navigation channel. Route suggestions included. E.g., route for \"church rowing boats\"."},
  :tags {:fi ["kanootti" "kajakki" "melonta"]},
  :name
  {:fi "Vesiretkeilyreitti",
   :se "Utflyktsled i vattendrag",
   :en "Water route"},
  :type-code 4452,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:may-be-shown-in-excursion-map-fi? {:priority 0},
   :route-length-km {:priority 90},
   :rest-places-count {:priority 70},
   :school-use? {:priority 40},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :jumps-count {:priority 80}}},
 5370
 {:description
  {:fi "Pääasiallisesti jääspeedwayta varten.",
   :se "Huvudsakligen för isracing.",
   :en "Speedway mainly for ice racing."},
  :tags {:fi ["speedway"]},
  :name
  {:fi "Speedway-/jääspeedwayrata",
   :se "Isracingbana",
   :en "Ice speedway track"},
  :type-code 5370,
  :main-category 5000,
  :status "active",
  :sub-category 5300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :track-width-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :scoreboard? {:priority 70},
   :loudspeakers? {:priority 70},
   :ligthing? {:priority 60},
   :track-length-m {:priority 90}}},
 2240
 {:description
  {:fi
   "Ensisijaisesti salibandyyn tarkoitettu halli. Kenttien määrä ja pintamateriaali kirjataan lisätietoihin.",
   :se
   "Hall i första hand avsedd för innebandy. Antalet planer samt ytmaterial i karakteristika.",
   :en
   "Hall primarily intended for floorball. Number of courts and surface material specified in properties."},
  :tags {:fi ["sähly"]},
  :name
  {:fi "Salibandyhalli", :se "Innebandyhall", :en "Floorball hall"},
  :type-code 2240,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90, :derived? true},
   :surface-material {:priority 89, :derived? true},
   :surface-material-info {:priority 88, :derived? true},
   :stand-capacity-person {:priority 70, :derived? true},
   :free-use? {:priority 40},
   :field-length-m {:priority 90, :derived? true},
   :badminton-courts-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90, :derived? true},
   :field-width-m {:priority 90, :derived? true},
   :scoreboard? {:priority 70},
   :floorball-fields-count {:priority 80, :derived? true},
   :auxiliary-training-area? {:priority 80},
   :customer-service-point? {:priority 70},
   :school-use? {:priority 40}}},
 2510
 {:description
  {:fi
   "Harjoitusjäähalli on pääasiassa jääurheilun harjoitteluun ja jääliikuntaan käytettävä jäähalli.",
   :se
   "Antalet planer och omklädningshytter, uppvärmning osv anges i karakteristika.",
   :en
   "Number of fields, heating, changing rooms, etc., specified in properties."},
  :tags {:fi ["jäähalli"]},
  :name
  {:fi "Harjoitusjäähalli",
   :se "Övningsishall",
   :en "Training ice arena"},
  :type-code 2510,
  :keywords {:fi ["Jäähalli"], :en [], :se []},
  :main-category 2000,
  :status "active",
  :sub-category 2500,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :finish-line-camera? {:priority 70},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :ice-rinks-count {:priority 80},
   :field-2-flexible-rink? {:priority 80},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :curling-lanes-count {:priority 80},
   :scoreboard? {:priority 70},
   :auxiliary-training-area? {:priority 80},
   :ringette-boundary-markings? {:priority 80},
   :field-1-flexible-rink? {:priority 80},
   :loudspeakers? {:priority 70},
   :school-use? {:priority 40},
   :field-3-flexible-rink? {:priority 80}}},
 1640
 {:description
  {:fi "Ratagolfliiton hyväksymät ratagolf-/minigolfradat.",
   :se
   "Bangolf / minigolf, enligt Finlands Bangolfförbundets regler.",
   :en
   "A course built for miniature golf, accepted by the Ratagolf Union."},
  :tags {:fi ["minigolf"]},
  :name {:fi "Ratagolf", :se "Bangolf", :en "Minigolf course"},
  :type-code 1640,
  :main-category 1000,
  :status "active",
  :sub-category 1600,
  :geometry-type "Point",
  :props
  {:holes-count {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :lighting-info {:priority 50},
   :customer-service-point? {:priority 70},
   :green? {:priority 80},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :range? {:priority 80}}},
 1380
 {:description
  {:fi "Rullakiekon pelaamiseen varustettu kenttä.",
   :se "Plan utrustad för inlinehockey.",
   :en "Field equipped for roller hockey."},
  :tags {:fi ["rullakiekko"]},
  :name
  {:fi "Rullakiekkokenttä",
   :se "Inlinehockeyplan",
   :en "Roller hockey field"},
  :type-code 1380,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :match-clock? {:priority 70},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40}}},
 4451
 {:description
  {:fi
   "Erityisesti melontaan tarkoitettu vesistöreitti. Reitistä on laadittu reittikuvaus ja maastossa löytyy opasteita (esim. rantautumispaikoista). Reittiehdotus-tyyppinen, ei navigointiin.",
   :se
   "Särskilt för paddling, märkt med ruttförslag, ej för navigering.",
   :en
   "Marked route particularly for canoeing. Route suggestions are not intended for navigation."},
  :tags {:fi ["kanootti" "kajakki"]},
  :name {:fi "Melontareitti", :se "Paddlingsled", :en "Canoe route"},
  :type-code 4451,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:route-length-km {:priority 90},
   :rest-places-count {:priority 70},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 4403
 {:description
  {:fi
   "Jalkaisin tapahtuvaan ulkoiluun tarkoitettu reitti. Suhteellisen leveä ja helppokulkuinen reitti, yleensä valaistu ja pinnoitettu.",
   :se
   "Promenadled. Relativt bred och lättilgänglig, eventuellt belyst och asfalterad.",
   :en
   "Route intended for outdoor pedestrian activities. Relatively broad and passable. Potentially lit and surfaced."},
  :tags {:fi ["ulkoilu" "kävely" "ulkoilureitti" "kävelyreitti"]},
  :name
  {:fi "Kävelyreitti/ulkoilureitti",
   :se "Promenadled/friluftsled",
   :en "Walking route/outdoor route"},
  :type-code 4403,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 89},
   :surface-material-info {:priority 88},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :outdoor-exercise-machines? {:priority 80},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :lit-route-length-km {:priority 98},
   :accessibility-info {:priority 40},
   :route-length-km {:priority 99}}},
 5150
 {:description
  {:fi
   "Melontaan tarkoitettu palvelupaikka, jossa voi olla esim. vuokrauspalveluita. Melontakeskuksesta voi lähteä melontareitti tai sen yhteydessä voi olla melontaratoja.",
   :se
   "En paddlingsanläggning med uthyrningstjänster. Från paddlingscentret kan det finnas paddelleder eller paddelbanor i närheten.",
   :en
   "A canoeing facility with rental services. From the canoeing center, there may be canoeing routes or paddling tracks nearby."},
  :tags {:fi ["melonta" "kajakki" "kanootti" "melontakeskus"]},
  :name
  {:fi "Melontakeskus",
   :se "Centrum för paddling",
   :en "Canoeing centre"},
  :type-code 5150,
  :main-category 5000,
  :status "active",
  :sub-category 5100,
  :geometry-type "Point",
  :props
  {:free-use? {:priority 40},
   :pier? {:priority 80},
   :canoeing-club? {:priority 80},
   :altitude-difference {:priority 90},
   :rapid-canoeing-centre? {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :equipment-rental? {:priority 70},
   :activity-service-company? {:priority 80}}},
 1630
 {:description
  {:fi
   "Golfia varten varustettu sisäharjoittelutila. Voi olla useita erilaisia suorituspaikkoja.",
   :se "Övningsutrymme byggt för golf. Storlek i karakteristika.",
   :en
   "Training space built for golf. Size specified in properties."},
  :tags {:fi ["greeni" "puttialue"]},
  :name
  {:fi "Golfin harjoitushalli",
   :se "Övningshall för golf",
   :en "Golf training hall"},
  :type-code 1630,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :holes-count {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :customer-service-point? {:priority 70},
   :green? {:priority 80},
   :school-use? {:priority 40},
   :range? {:priority 80}}},
 2295
 {:description
  {:fi
   "Yksi tai useampi padelkenttä sisällä. Pintamateriaali tekonurmi (hiekkatekonurmi). Lajivaatimusten mukaiset seinät. Vapaa korkeus ilmoitetaan lisätiedoissa.",
   :se
   "En eller flera padelbanor inomhus. Ytmaterial konstgräs (med sand), 20 x 10 m. Väggar måste uppfylla spelets krav. Höjd anges i karakteristika.",
   :en
   "One or more indoor padel courts. The court has an artificial grass surface and its measurements are 20 x 10 metres. The walls must meet the requirements for the sport. Height given in 'properties'."},
  :tags {:fi ["padel" "padel-halli"]},
  :name {:fi "Padelhalli", :se "Padelhall", :en "Padel hall"},
  :type-code 2295,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 90},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :padel-courts-count {:priority 80},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :scoreboard? {:priority 70},
   :school-use? {:priority 40}}},
 2370
 {:description
  {:fi "Kiipeilyyn varustettu sisätila. Myös boulderointipaikat.",
   :se
   "Inomhusutrymme utrustat för klättring, även platser för bouldering.",
   :en
   "Indoor space equipped for climbing. Also bouldering venues."},
  :tags {:fi ["kiipeilyseinä"]},
  :name
  {:fi "Sisäkiipeilyseinä",
   :se "Klättervägg inomhus",
   :en "Indoor climbing wall"},
  :type-code 2370,
  :main-category 2000,
  :status "active",
  :sub-category 2300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :climbing-routes-count {:priority 80},
   :climbing-wall-height-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :area-m2 {:priority 90},
   :climbing-wall-width-m {:priority 90},
   :school-use? {:priority 40}}},
 1340
 {:description
  {:fi
   "Palloiluun tarkoitettu kenttä, jonka pintamateriaali on esim. hiekka, nurmi tai hiekkatekonurmi. Kentällä on mahdollista pelata yhtä tai useampaa palloilulajia. Kentän koko merkitään lisätietoihin.  Kevyt poistettava kate on mahdollinen.",
   :se
   "Plan avsedd för bollspel. Sand, konstgräs med sand el dyl, storleken varierar. En eller flera bollspelsgrenar möjliga.",
   :en
   "A field intended for ball games. Sand, grass, artificial turf, etc., size varies. One or more types of ball games possible."},
  :tags {:fi []},
  :name {:fi "Pallokenttä", :se "Bollplan", :en "Ball field"},
  :type-code 1340,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:heating? {:priority 70},
   :surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :changing-rooms? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :year-round-use? {:priority 80},
   :customer-service-point? {:priority 70},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :light-roof? {:priority 70}}},
 1610
 {:description
  {:fi
   "Golfin harjoittelua varten varustettu alue. Harjoitusalue voi sisältää useampia suorituspaikkoja, kuten rangen ja puttausviheriön. Harjoitusalue sijaitsee ulkona.",
   :se
   "Ett område utrustat för golfträning. Träningsområdet kan innehålla flera träningsplatser, såsom en range och en puttningsgreen. Träningsområdet ligger utomhus.",
   :en
   "An area equipped for golf practice. The practice area may include multiple facilities, such as a driving range and a putting green. The practice area is located outdoors."},
  :tags {:fi ["greeni" "puttialue" "range"]},
  :name
  {:fi "Golfin harjoitusalue",
   :se "Träningsområde för golf",
   :en "Golf training area"},
  :type-code 1610,
  :main-category 1000,
  :status "active",
  :sub-category 1600,
  :geometry-type "Point",
  :props
  {:holes-count {:priority 90},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :lighting-info {:priority 50},
   :customer-service-point? {:priority 70},
   :green? {:priority 80},
   :ligthing? {:priority 60},
   :school-use? {:priority 40},
   :range? {:priority 80}}},
 4421
 {:description
  {:fi
   "Reittitoimituksella hyväksytty, virallinen moottorikelkkailureitti.",
   :se "En officiell rutt som godkänts genom en ruttexpedition.",
   :en
   "Officially approved route (in compliance with Act 670/1991)."},
  :tags {:fi []},
  :name
  {:fi "Moottorikelkkareitti",
   :se "Snöskoterled",
   :en "Official snowmobile route"},
  :type-code 4421,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:rest-places-count {:priority 70},
   :route-width-m {:priority 98},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :route-length-km {:priority 99},
   :free-use? {:priority 40},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0}}},
 2220
 {:description
  {:fi
   "Monitoimihalli on suuri liikuntatila, joka on merkittävä monien lajien kilpailu- ja tapahtumapaikka. Liikuntapinta-ala on suurempi kuin 5 000 m2.",
   :se "Större tävlingsplats för ett flertal grenar, >= 5 000 m2.",
   :en
   "Significant competition venue for various sports, >=5000 m2."},
  :tags {:fi ["liikuntahalli" "urheilutalo" "urheiluhalli"]},
  :name
  {:fi "Monitoimihalli/areena",
   :se "Allaktivitetshall/multiarena",
   :en "Multipurpose hall/arena"},
  :type-code 2220,
  :main-category 2000,
  :status "active",
  :sub-category 2200,
  :geometry-type "Point",
  :props
  {:height-m {:priority 80},
   :surface-material {:priority 89},
   :basketball-fields-count {:priority 80},
   :surface-material-info {:priority 88},
   :automated-timing? {:priority 70},
   :stand-capacity-person {:priority 70},
   :free-use? {:priority 40},
   :sprint-lanes-count {:priority 80},
   :javelin-throw-places-count {:priority 80},
   :tennis-courts-count {:priority 80},
   :field-length-m {:priority 90},
   :circular-lanes-count {:priority 80},
   :match-clock? {:priority 70},
   :sprint-track-length-m {:priority 80},
   :inner-lane-length-m {:priority 80},
   :discus-throw-places {:priority 80},
   :badminton-courts-count {:priority 80},
   :hammer-throw-places-count {:priority 80},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :padel-courts-count {:priority 80},
   :polevault-places-count {:priority 80},
   :space-divisible {:priority 70},
   :toilet? {:priority 70},
   :gymnastics-space? {:priority 80},
   :running-track-surface-material {:priority 80},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :scoreboard? {:priority 70},
   :shotput-count {:priority 80},
   :longjump-places-count {:priority 80},
   :football-fields-count {:priority 80},
   :floorball-fields-count {:priority 80},
   :auxiliary-training-area? {:priority 80},
   :squash-courts-count {:priority 80},
   :loudspeakers? {:priority 70},
   :customer-service-point? {:priority 70},
   :accessibility-info {:priority 40},
   :handball-fields-count {:priority 80},
   :volleyball-fields-count {:priority 80},
   :climbing-wall? {:priority 80},
   :school-use? {:priority 40},
   :highjump-places-count {:priority 80}}},
 1320
 {:description
  {:fi
   "Lentopalloon varustettu kenttä, jossa on kiinteät lentopallotolpat.",
   :se "Plan utrustad för volleyboll. Fasta volleybollställningar.",
   :en
   "A field equipped for volleyball. Fixed volleyball apparatus."},
  :tags {:fi []},
  :name
  {:fi "Lentopallokenttä",
   :se "Volleybollplan",
   :en "Volleyball court"},
  :type-code 1320,
  :main-category 1000,
  :status "active",
  :sub-category 1300,
  :geometry-type "Point",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :height-of-basket-or-net-adjustable? {:priority 80},
   :free-use? {:priority 40},
   :field-length-m {:priority 90},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :area-m2 {:priority 90},
   :field-width-m {:priority 90},
   :lighting-info {:priority 50},
   :water-point {:priority 70},
   :ligthing? {:priority 60},
   :school-use? {:priority 40}}},
 4402
 {:description
  {:fi
   "Talvikaudella hiihtoa varten ylläpidetty reitti. Hiihtotyylit kerrotaan ominaisuustiedoissa.",
   :se
   "Led avsedd för skidåkning. Ej sommaranvändning och -underhåll. Åkstilar anges i karakteristika.",
   :en
   "Route intended for skiing. Not in use and unmaintained in summer. Ski styles provided in properties."},
  :tags {:fi ["hiihto" "hiihtolatu"]},
  :name {:fi "Hiihtolatu", :se "Skidspår", :en "Ski track"},
  :type-code 4402,
  :main-category 4000,
  :status "active",
  :sub-category 4400,
  :geometry-type "LineString",
  :props
  {:surface-material {:priority 80},
   :surface-material-info {:priority 80},
   :free-use? {:priority 40},
   :may-be-shown-in-excursion-map-fi? {:priority 0},
   :outdoor-exercise-machines? {:priority 80},
   :ski-track-traditional? {:priority 80},
   :route-width-m {:priority 97},
   :may-be-shown-in-harrastuspassi-fi? {:priority 0},
   :toilet? {:priority 70},
   :rest-places-count {:priority 70},
   :shooting-positions-count {:priority 80},
   :lit-route-length-km {:priority 98},
   :ski-track-freestyle? {:priority 80},
   :school-use? {:priority 40},
   :route-length-km {:priority 99}}}})

(def active
  (reduce-kv (fn [m k v] (if (not= "active" (:status v)) (dissoc m k) m)) all all))

(def by-main-category (group-by :main-category (vals active)))
(def by-sub-category (group-by :sub-category (vals active)))

(def main-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals main-categories)))

(def sub-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals sub-categories)))

(comment
  (require '[clojure.pprint :as pprint])
  #?(:clj (spit "/tmp/types.edn" (with-out-str (pprint/pprint all))))
  #?(:clj (spit "/tmp/sub-cats.edn" (with-out-str (pprint/pprint sub-categories))))
  #?(:clj (spit "/tmp/main-cats.edn" (with-out-str (pprint/pprint main-categories))))

  )
