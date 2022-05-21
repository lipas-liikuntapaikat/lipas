(ns lipas.data.prop-types
  (:require
   [lipas.data.types :as types]))

(def all
  {:height-m
   {:name
    {:fi "Tilan korkeus m",
     :se "Utrymmets höjd",
     :en "Venue's height"},
    :data-type "numeric",
    :description
    {:fi "Sisäliikuntatilan korkeus metreinä (matalin kohta)",
     :se "Motionssalens höjd i meter (från lägsta punkten)",
     :en ""}},
   :heating?
   {:name      {:fi "Lämmitys", :se "Uppvärmning", :en "Heating"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikassa lämmitys",
     :se "Är idrottsplatsen utrustad med uppvärmning",
     :en ""}},
   :field-2-area-m2
   {:name
    {:fi "2. kentän ala m2",
     :se "Andra planens areal m2",
     :en "2. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän pinta-ala neliömetreinä",
     :se "Andra planens areal i kvadratmeter",
     :en ""}},
   :surface-material
   {:name
    {:fi "Pintamateriaali", :se "Ytmaterial", :en "Surface material"},
    :data-type "enum-coll",
    :description
    {:fi
     "Liikunta-alueen pintamateriaali. Yleisurheilukentässä keskikentän päällyste.",
     :se
     "Idrotts områdets ytmaterial. Friidrottsplanens mittplans pålägg.",
     :en ""}},
   :basketball-fields-count
   {:name
    {:fi "Koripallokentät lkm",
     :se "Antalet korgbollsplaner",
     :en "Basketball fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Koripallokenttien lukumäärä",
     :se "Antalet korgbollsplaner i salen",
     :en ""}},
   :surface-material-info
   {:name
    {:fi "Pintamateriaali lisätieto",
     :se "Ytterligare information om ytmaterialen",
     :en "Surface material information"},
    :data-type "string",
    :description
    {:fi "Pintamateriaalin lisätieto",
     :se "Ytterilgare information om ytmaterialen",
     :en ""}},
   :holes-count
   {:name
    {:fi "Väylien lkm", :se "Antalet ranger", :en "Number of holes"},
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
    {:fi
     "Hyppyrimäen tyyppi (harjoitus, pienmäki, normaali, suurmäki)",
     :se
     "Typ av hoppbacke (övningsbacke, liten, normalbacke, storbacke)",
     :en ""}},
   :lifts-count
   {:name      {:fi "Hissit lkm", :se "Antalet skidliftar", :en "Lifts"},
    :data-type "numeric",
    :description
    {:fi "Hiihtohissien lukumäärä",
     :se "Antal skidliftar i skidcentrumet",
     :en ""}},
   :field-3-length-m
   {:name
    {:fi "3. kentän pituus m",
     :se "Tredje planens längd m",
     :en "3. field's length m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän pituus metreinä",
     :se "Tredje planens längd i meter",
     :en ""}},
   :pool-tracks-count
   {:name
    {:fi "1. altaan radat lkm",
     :se "Första bassängens antal banor",
     :en "Courses in 1. pool"},
    :data-type "numeric",
    :description
    {:fi "1. altaan ratojen lukumäärä",
     :se "Antal banor i första bassängen",
     :en ""}},
   :field-2-length-m
   {:name
    {:fi "2. kentän pituus m",
     :se "Andra planens längd m",
     :en "2. field's length m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän pituus metreinä",
     :se "Andra planens längd i meter",
     :en ""}},
   :plastic-outrun?
   {:name
    {:fi "Muovitettu alastulo",
     :se "Plast-belagd landning",
     :en "Plastic outrun"},
    :data-type "boolean",
    :description
    {:fi "Muovitettu hyppyrimäen alastulopaikka",
     :se "Hoppbacken har plast-belagd landningsplats",
     :en ""}},
   :automated-timing?
   {:name
    {:fi "Automaattinen ajanotto",
     :se "Automatisk tidtagning",
     :en "Automatic timing"},
    :data-type "boolean",
    :description
    {:fi "Varustus automaattiseen ajanottoon",
     :se "Utrustning för automatisk tidtagning",
     :en ""}},
   :freestyle-slope?
   {:name
    {:fi "Kumparerinne", :se "Puckelpist", :en "Freestyle slope"},
    :data-type "boolean",
    :description
    {:fi "Hiihtokeskuksessa on kumparerinne",
     :se "Skidcentret har en puckelpist",
     :en ""}},
   :kiosk?
   {:name      {:fi "Kioski", :se "Kiosk", :en "Kiosk"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla kioski tai vastaava",
     :se "Har idrottsplatsen en kiosk eller något liknande",
     :en ""}},
   :summer-usage?
   {:name      {:fi "Kesäkäyttö", :se "I sommarbruk", :en "Summer usage"},
    :data-type "boolean",
    :description
    {:fi "Käytössä myös kesäisin",
     :se "Används också under sommaren",
     :en ""}},
   :stand-capacity-person
   {:name
    {:fi "Katsomon kapasiteetti hlö",
     :se "Läktarens person kapasitet",
     :en "Stand size"},
    :data-type "numeric",
    :description
    {:fi "Katsomon koko kapasiteetti, henkilölukumäärä",
     :se "Läktarens hela person kapasitet",
     :en ""}},
   :free-use?
   {:name
    {:fi "Vapaa käyttö", :se "Fri användning", :en "Free access"},
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
   :may-be-shown-in-excursion-map-fi?
   {:name
    {:fi "Saa julkaista Retkikartta.fi-palvelussa",
     :se "Får publiceras i Utflyktskarta.fi",
     :en "May be shown in Excursionmap.fi"},
    :data-type "boolean",
    :description
    {:fi "Kohteen tiedot saa julkaista Retkikartta.fi-palvelussa",
     :se
     "Information om motionsstället får publiceras i Retkikartta.fi",
     :en ""}},
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
     :en "When the option ”May be shown in Harrastuspassi.fi” is ticked, the information regarding the sport facility will be transferred automatically to the Harrastuspassi.fi application."}}
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
   :tennis-courts-count
   {:name
    {:fi "Tenniskentät lkm",
     :se "Antalet tennisplaner",
     :en "Tennis courts pcs"},
    :data-type "numeric",
    :description
    {:fi "Tenniskenttien lukumäärä",
     :se "Antalet tennisplaner",
     :en ""}},
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
     :en ""}},
   :finish-line-camera?
   {:name
    {:fi "Maalikamera", :se "Målkamera", :en "Finish line camera"},
    :data-type "boolean",
    :description
    {:fi "Liikuntapaikalla on maalikamera",
     :se "Idrottsplatsen har en målkamera",
     :en ""}},
   :parking-place?
   {:name
    {:fi "Parkkipaikka", :se "Parkeringsplats", :en "Parking place"},
    :data-type "boolean",
    :description
    {:fi "Parkkipaikka käytettävissä",
     :se "Tillgänglig parkeringsplats",
     :en ""}},
   :climbing-routes-count
   {:name
    {:fi "Kiipeilyreittien lkm",
     :se "Antalet klättringsrutter",
     :en "Climbing routes pcs"},
    :data-type "numeric",
    :description
    {:fi "Kiipeilyreittien lukumäärä",
     :se "Antalet klättringsrutter",
     :en ""}},
   :outdoor-exercise-machines?
   {:name
    {:fi "Kuntoilutelineitä",
     :se "Gym apparater utomhus",
     :en "Exercise machines outdoors"},
    :data-type "boolean",
    :description
    {:fi "Onko reitin varrella kuntoilulaitteita",
     :se "Finns det gym apparater längs rutten",
     :en ""}},
   :automated-scoring?
   {:name
    {:fi "Kirjanpitoautomaatti",
     :se "Bokföringsautomat",
     :en "Automatic scoring"},
    :data-type "boolean",
    :description
    {:fi "Keilaradalla on sähköinen pistelasku",
     :se "Bowlingbanan har elektroniskt poängräknings system",
     :en ""}},
   :track-width-m
   {:name
    {:fi "Radan leveys m",
     :se "Banans bredd m",
     :en "Width of track m"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan, pyöräilyradan tms. leveys metreinä",
     :se "Löpbanan, rundbanan el.dyl. bredd i meter",
     :en ""}},
   :ice-climbing?
   {:name      {:fi "Jääkiipeily", :se "Isklättring", :en "Ice climbing"},
    :data-type "boolean",
    :description
    {:fi "Onko jääkiipeily mahdollista kiipeilypaikalla",
     :se "Finns det möjlighet för isklättring vid klättringsplatsen",
     :en ""}},
   :field-length-m
   {:name
    {:fi "Kentän pituus m",
     :se "Planens längd m",
     :en "Length of field"},
    :data-type "numeric",
    :description
    {:fi "Kentän/kenttien pituus metreinä",
     :se "Planens/planernas längd i meter",
     :en ""}},
   :skijump-hill-material
   {:name
    {:fi "Vauhtimäen rakennemateriaali",
     :se "Överbackens konstruktionsmaterial",
     :en "Ski jump hill material"},
    :data-type "string",
    :description
    {:fi "Vauhtimäen rakennemateriaali",
     :se "Överbackens konstruktionsmaterial (backhoppning)",
     :en ""}},
   :longest-slope-m
   {:name
    {:fi "Pisin rinne m",
     :se "Längsta slalombacken m",
     :en "Longest slope m"},
    :data-type "numeric",
    :description
    {:fi "Pisimmän rinteen pituus metreinä",
     :se "Längsta slalombackens längd i meter",
     :en ""}},
   :circular-lanes-count
   {:name
    {:fi "Kiertävät radat lkm",
     :se "Antalet cirkulerande löpbanor",
     :en "Number of circular lanes"},
    :data-type "numeric",
    :description
    {:fi "Kiertävien juoksuratojen lukumäärä",
     :se "Antalet cirkulerande löpbanor",
     :en ""}},
   :boat-launching-spot?
   {:name
    {:fi "Veneen vesillelaskupaikka",
     :se "Sjösättningsplats för båtar",
     :en "Place for launching a boat"},
    :data-type "boolean",
    :description
    {:fi "Mahdollisuus veneen vesillelaskuun",
     :se "Sjösättningsplats för båtar",
     :en ""}},
   :ski-track-traditional?
   {:name
    {:fi "Perinteinen latu",
     :se "Skidspår för klassisk stil",
     :en "Traditional ski track"},
    :data-type "boolean",
    :description
    {:fi "Perinteisen tyylin hiihtomahdollisuus / latu-ura",
     :se "Möjlighet att skida klassisk stil",
     :en ""}},
   :altitude-difference
   {:name
    {:fi "Korkeusero m",
     :se "Höjdskillnad m",
     :en "Altitude difference"},
    :data-type "numeric",
    :description
    {:fi "Reitin korkeusero metreinä",
     :se "Ruttens höjdskillnad i meter",
     :en ""}},
   :climbing-wall-height-m
   {:name
    {:fi "Kiipeilyseinän korkeus m",
     :se "Klätterväggens höjd m",
     :en "Climbing wall height"},
    :data-type "numeric",
    :description
    {:fi "Kiipeilyseinän korkeus metreinä (max)",
     :se "Klätterväggens höjd i meter (max)",
     :en ""}},
   :route-width-m
   {:name
    {:fi "Reitin leveys m",
     :se "Ruttens bredd m",
     :en "Route's width m"},
    :data-type "numeric",
    :description
    {:fi "Reitin leveys metreinä",
     :se "Banans bredd i meter",
     :en ""}},
   :beach-length-m
   {:name
    {:fi "Rannan pituus m",
     :se "Strandens längd m",
     :en "Length of beach m"},
    :data-type "numeric",
    :description
    {:fi "Hoidetun rannan pituus metreinä",
     :se "Skötta strandens längd i meter",
     :en ""}},
   :match-clock?
   {:name      {:fi "Ottelukello", :se "Matchklocka", :en "Match clock"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla ottelukello",
     :se "Finns det en matchklocka vid idrottsplatsen",
     :en ""}},
   :sprint-track-length-m
   {:name
    {:fi "Etusuoran pituus m",
     :se "Raksträckans längd (framför läktaren)",
     :en "Length of sprint track"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan etusuoran pituus",
     :se "Längden på löpbanans raksträcka",
     :en ""}},
   :inner-lane-length-m
   {:name
    {:fi "Sisäradan pituus m",
     :se "Innerbanans längd m",
     :en "Length of inner lane m"},
    :data-type "numeric",
    :description
    {:fi "Sisäradan pituus kiertävissä radoissa",
     :se "Längden på innerbanan i de cirkulerande banorna",
     :en ""}},
   :discus-throw-places
   {:name
    {:fi "Kiekonheittopaikat lkm",
     :se "Diskusplatser st.",
     :en "Number of discus throw places"},
    :data-type "numeric",
    :description
    {:fi "Kiekonheittopaikkojen lukumäärä",
     :se "Antalet diskuskastningsplatser",
     :en ""}},
   :fields-count
   {:name
    {:fi "Kenttien lkm", :se "Antalet planer", :en "Number of fields"},
    :data-type "numeric",
    :description
    {:fi "Montako saman tyypin kenttää liikuntapaikassa on",
     :se "Hur många planer av samma typ har motionsplatsen",
     :en ""}},
   :field-1-width-m
   {:name
    {:fi "1. kentän leveys m",
     :se "Första planens bredd m",
     :en "1. field's width m"},
    :data-type "numeric",
    :description
    {:fi "1. kentän leveys metreinä",
     :se "Första planens bredd i meter",
     :en ""}},
   :field-3-width-m
   {:name
    {:fi "3. kentän leveys m",
     :se "Tredje planens bredd m",
     :en "3. field's width m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän leveys metreinä",
     :se "Tredje planens bredd i meter",
     :en ""}},
   :field-2-width-m
   {:name
    {:fi "2. kentän leveys m",
     :se "Andra planens bredd m",
     :en "2. field's width m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän leveys metreinä",
     :se "Andra planens bredd i meter",
     :en ""}},
   :badminton-courts-count
   {:name
    {:fi "Sulkapallokentät lkm",
     :se "Antalet badmintonsplaner",
     :en "Badminton courts pcs"},
    :data-type "numeric",
    :description
    {:fi "Sulkapallokenttien lukumäärä salissa",
     :se "Antalet badmintonsplaner i salen",
     :en ""}},
   :hammer-throw-places-count
   {:name
    {:fi "Moukarinheittopaikat lkm",
     :se "Antalet släggkastningsplatser",
     :en "Hammer throw"},
    :data-type "numeric",
    :description
    {:fi "Moukarinheittopaikkojen lukumäärä",
     :se "Antal platser för att släggkastning",
     :en ""}},
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
     :en ""}},
   :ice-rinks-count
   {:name      {:fi "Kaukalot lkm", :se "Antalet rinkar", :en "Ice rinks"},
    :data-type "numeric",
    :description
    {:fi "Kaukaloiden lukumäärä",
     :se "Antalet rinkar (hockey) det finns vid idrottsplatsen",
     :en ""}},
   :field-1-area-m2
   {:name
    {:fi "1. kentän ala m2",
     :se "Första planens areal m2",
     :en "1. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "1. kentän pinta-ala neliömetreinä",
     :se "Första planens areal i kvadratmeter",
     :en ""}},
   :k-point
   {:name      {:fi "K-piste", :se "K-punkt", :en "K point"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrimäen k-piste metreinä",
     :se "Hoppbackens k-punkt i meter",
     :en ""}},
   :polevault-places-count
   {:name
    {:fi "Seiväshyppypaikat lkm",
     :se "Antalet stavhoppsplatser",
     :en "Pole vault"},
    :data-type "numeric",
    :description
    {:fi "Seiväshyppypaikkojen lukumäärä",
     :se "Antalet stavhoppningsplatser",
     :en ""}},
   :group-exercise-rooms-count
   {:name
    {:fi "Ryhmäliikuntatilat lkm",
     :se "Antalet gruppmotions utrymmen",
     :en "Room for exercise groups"},
    :data-type "numeric",
    :description
    {:fi "Liikuntasalien ja ryhmäliikuntatilojen lukumäärä",
     :se "Antalet gymnastiksalar och gruppmotions utrymmen",
     :en ""}},
   :snowpark-or-street?
   {:name
    {:fi "Temppurinne",
     :se "Trick / street pist",
     :en "Snow park/street"},
    :data-type "boolean",
    :description
    {:fi
     "Onko rinnehiihtokeskuksessa ns. temppurinne, snowpark tai vastaava",
     :se
     "Har skidcentrumet en trickbana, snowpark eller något liknande",
     :en ""}},
   :max-vertical-difference
   {:name
    {:fi "Korkeusero max m",
     :se "Höjdskillnad max m",
     :en "Max vertical difference"},
    :data-type "numeric",
    :description
    {:fi "Suurin korkeusero rinteissä",
     :se "Största höjdskillnaden i slalombackorna",
     :en ""}},
   :bowling-lanes-count
   {:name
    {:fi "Keilaradat lkm",
     :se "Antalet bowlingbanor",
     :en "Bowling lanes"},
    :data-type "numeric",
    :description
    {:fi "Keilaratojen lukumäärä",
     :se "Antalet bowlingbanor",
     :en ""}},
   :air-gun-shooting?
   {:name
    {:fi "Ilma-aseammunta",
     :se "Luftgevärsskytte",
     :en "Air gun shooting"},
    :data-type "boolean",
    :description
    {:fi "Ilma-aseammuntamahdollisuus",
     :se "Möjlighet för luftgevärsskytte",
     :en ""}},
   :gymnastic-routines-count
   {:name
    {:fi "Telinevoimistelusarjat lkm",
     :se "Antalet redskapsgymnastikserier",
     :en "Gymnastic routines"},
    :data-type "numeric",
    :description
    {:fi "Telinevoimistelun telinesarjojen lukumäärä",
     :se "Antalet redskap för redskapsgymnastik",
     :en ""}},
   :toilet?
   {:name      {:fi "Yleisö-wc", :se "Allmän toalett", :en "Toilet"},
    :data-type "boolean",
    :description
    {:fi "Onko yleisö-wc:tä käytettävissä",
     :se "Är allmänna toaletten i användning",
     :en ""}},
   :gymnastics-space?
   {:name
    {:fi "Telinevoimistelutila",
     :se "Utrymme för redskapsgymnastik",
     :en "Space for gymnastics"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntasalissa myös telinevoimistelutila",
     :se
     "Har motionssalen också område / utrymme för redskapsgymnastik",
     :en ""}},
   :show-jumping?
   {:name      {:fi "Esteratsastus", :se "Banhoppning", :en "Show jumping"},
    :data-type "boolean",
    :description
    {:fi
     "Onko ratsastuskentällä / maneesissa esteratsastukseen soveltuva varustus",
     :se "Har ridplanen / ridhuset utrustning för banhoppning",
     :en ""}},
   :shower?
   {:name      {:fi "Suihku", :se "Dusch", :en "Shower"},
    :data-type "boolean",
    :description
    {:fi "Onko suihku käytettävissä",
     :se "Är duschen i användning",
     :en ""}},
   :rest-places-count
   {:name
    {:fi "Taukopaikat lkm",
     :se "Antalet viloplatser",
     :en "Rest places"},
    :data-type "numeric",
    :description
    {:fi "Montako taukopaikkaa reitin varrella on",
     :se "Hur många viloplatser finns det längs med rutten",
     :en ""}},
   :changing-rooms?
   {:name
    {:fi "Pukukopit", :se "Omklädningsrum", :en "Changing rooms"},
    :data-type "boolean",
    :description
    {:fi "Onko pukukoppeja",
     :se "Finns det omklädningsrum",
     :en ""}},
   :pistol-shooting?
   {:name
    {:fi "Pistooliammunta", :se "Pistolskytte", :en "Pistol shooting"},
    :data-type "boolean",
    :description
    {:fi "Pistooliammuntamahdollisuus",
     :se "Möjlighet för pistolskytte",
     :en ""}},
   :halfpipe-count
   {:name      {:fi "Lumikouru kpl", :se "Halfpipe st.", :en "Pipe"},
    :data-type "numeric",
    :description
    {:fi "Halfpipe, superpipe lukumäärät",
     :se "Antal halfpipe",
     :en ""}},
   :shooting-positions-count
   {:name
    {:fi "Ampumapaikat lkm",
     :se "Antalet skytteplatser",
     :en "Shooting positions"},
    :data-type "numeric",
    :description
    {:fi "Montako ampumapaikkaa liikuntareitin varrella on",
     :se "Hur många skytteplatser finns det längs motionsrutten",
     :en ""}},
   :running-track-surface-material
   {:name
    {:fi "Juoksuradan pintamateriaali",
     :se "Löpbanans ytmaterial",
     :en "Surface material for running track"},
    :data-type "string",
    :description
    {:fi "Juoksuradan pintamateriaali/päällyste",
     :se "Löpbanans ytmaterial / pålägg",
     :en ""}},
   :tatamis-count
   {:name      {:fi "Tatamit lkm", :se "Tatamimattor", :en "Tatamis pcs"},
    :data-type "numeric",
    :description
    {:fi "Tatamien lukumäärä", :se "Antalet tatamin", :en ""}},
   :lit-route-length-km
   {:name
    {:fi "Valaistua reittiä km",
     :se "Belyst rutt km",
     :en "Lit route's length km"},
    :data-type "numeric",
    :description
    {:fi "Montako kilometriä reitistä on valaistua",
     :se "Hur många km av rutten är uppbelyst",
     :en ""}},
   :area-m2
   {:name
    {:fi "Liikuntapinta-ala m2",
     :se "Areal m2",
     :en "Area in square meters"},
    :data-type "numeric",
    :description
    {:fi "Liikuntapaikan liikuntapinta-ala, neliömetreinä",
     :se "Idrottsplatsens areal i kvadratmeter",
     :en ""}},
   :field-width-m
   {:name
    {:fi "Kentän leveys m",
     :se "Planens bredd m",
     :en "Width of field"},
    :data-type "numeric",
    :description
    {:fi "Kentän/kenttien leveys metreinä",
     :se "Planens/planernas bredd i meter",
     :en ""}},
   :cosmic-bowling?
   {:name
    {:fi "Hohtokeilaus", :se "Discobowling", :en "Cosmic bowling"},
    :data-type "boolean",
    :description
    {:fi "Onko keilaradalla hohtokeilausmahdollisuus",
     :se "Har bowlingsbanan möjlighet för discobowling",
     :en ""}},
   :wrestling-mats-count
   {:name
    {:fi "Painimatot lkm",
     :se "Brottarmattor st.",
     :en "Wrestling mats pcs"},
    :data-type "numeric",
    :description
    {:fi "Painimattojen lukumäärä",
     :se "Antal brottarmattor",
     :en ""}},
   :eu-beach?
   {:name      {:fi "EU-uimaranta", :se "EU-badstrand", :en "EU beach"},
    :data-type "boolean",
    :description
    {:fi
     "Uimaranta, joka täyttää EU-kriteerit uimaveden laadusta ja valvonnasta",
     :se
     "Badstrand, som fyller EU-kriterierna med kvaliteten och övervakningen av badvattnet",
     :en ""}},
   :rifle-shooting?
   {:name
    {:fi "Kivääriammunta",
     :se "Gevärbana",
     :en "Rifle shooting places"},
    :data-type "boolean",
    :description
    {:fi "Kivääriammuntamahdollisuus",
     :se "Möjlighet för gevärskytte",
     :en ""}},
   :swimming-pool-count
   {:name
    {:fi "Uima-altaiden lkm",
     :se "Antalet simbassänger",
     :en "Number of swimming pools"},
    :data-type "numeric",
    :description
    {:fi "Uima-altaiden lukumäärä, myös terapia-altaat",
     :se "Antalet simbassänger, också terapi bassänger",
     :en ""}},
   :pool-water-area-m2
   {:name
    {:fi "Vesipinta-ala m2",
     :se "Bassängernas vatten areal",
     :en "Pool water area in sq. m"},
    :data-type "numeric",
    :description
    {:fi "Uimahallin, kylpylän tms. altaiden vesipinta-ala yhteensä",
     :se
     "Simhallens, badhusets el.dyl. bassängernas totala vatten areal",
     :en ""}},
   :curling-lanes-count
   {:name
    {:fi "Curling-ratojen lkm",
     :se "Antal curlingbanor",
     :en "How many curling lanes"},
    :data-type "numeric",
    :description
    {:fi "Curling-ratojen lukumäärä",
     :se "Antalet curlingbanor",
     :en ""}},
   :climbing-wall-width-m
   {:name
    {:fi "Kiipeilyseinän leveys m",
     :se "Klätterväggens bredd m",
     :en "Climbing wall width"},
    :data-type "numeric",
    :description
    {:fi "Kiipeilyseinän leveys metreinä sivusuunnassa",
     :se "Klätterväggens bredd i meter (vågrätt mätt)",
     :en ""}},
   :area-km2
   {:name
    {:fi "Pinta-ala km2",
     :se "Areal km2",
     :en "Area in square kilometres"},
    :data-type "numeric",
    :description
    {:fi "Alueen pinta-ala neliökilometreinä",
     :se "Områdets areal i kvadratkilometer",
     :en ""}},
   :scoreboard?
   {:name      {:fi "Tulostaulu", :se "Resultattavla", :en "Score board"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla tulostaulu/sähköinen tulostaulu",
     :se "Har idrottsplatsen resultattavla/elektronisk resultattavla",
     :en ""}},
   :futsal-fields-count
   {:name
    {:fi "Futsal-kentät lkm",
     :se "Antalet Futsal-planer",
     :en "Number of futsal fields"},
    :data-type "numeric",
    :description
    {:fi "Futsal-kenttien lukumäärä",
     :se "Antalet futsal planer",
     :en ""}},
   :training-wall?
   {:name
    {:fi "Lyöntiseinä",
     :se "Vägg att träna på tennis",
     :en "Training wall for tennis"},
    :data-type "boolean",
    :description
    {:fi "Onko tenniskentällä lyöntiseinä",
     :se "Finns det en träningsvägg vid tennisplanen",
     :en ""}},
   :shotput-count
   {:name
    {:fi "Kuulantyöntöpaikat lkm",
     :se "Antalet kulstötningsplatser",
     :en "Shot put"},
    :data-type "numeric",
    :description
    {:fi "Kuulantyöntöpaikkojen lukumäärä",
     :se "Antalet kulstötningsplatser",
     :en ""}},
   :longjump-places-count
   {:name
    {:fi "Pituus- ja kolmiloikkapaikat lkm",
     :se "Antalet längd- och trestegshopp platser",
     :en "Long jump"},
    :data-type "numeric",
    :description
    {:fi "Pituus- ja kolmiloikkapaikkojen lukumäärä",
     :se "Antalet längd- och trestegsplatser",
     :en ""}},
   :football-fields-count
   {:name
    {:fi "Jalkapallokentät lkm",
     :se "Antalet fotbollsplaner",
     :en "Football fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako jalkapallokenttää mahtuu saliin/halliin",
     :se "Hur många fotbollsplaner ryms i salen/hallen",
     :en ""}},
   :floorball-fields-count
   {:name
    {:fi "Salibandykentät lkm",
     :se "Antalet innebandyplaner",
     :en "Floor ball field"},
    :data-type "numeric",
    :description
    {:fi "Salibandykenttien lukumäärä",
     :se "Antalet innebandyplaner",
     :en ""}},
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
     :en ""}},
   :slopes-count
   {:name
    {:fi "Rinteiden lkm",
     :se "Antalet slalombackar",
     :en "Number of slopes"},
    :data-type "numeric",
    :description
    {:fi "Rinteiden määrä yhteensä",
     :se "Totala antalet slalombackar",
     :en ""}},
   :pool-length-m
   {:name
    {:fi "1. altaan pituus m",
     :se "Första bassängens längd",
     :en "1. pool's length"},
    :data-type "numeric",
    :description
    {:fi "1. altaan/pääaltaan pituus metreinä",
     :se "Första/huvudbassängens längd i meter",
     :en ""}},
   :other-pools-count
   {:name
    {:fi "Muut altaat lkm",
     :se "Antalet andra bassänger",
     :en "Number of other pools"},
    :data-type "numeric",
    :description
    {:fi "Porealtaiden, kylmäaltaiden yms lukumäärä yhteensä",
     :se
     "Antalet övriga bassänger såsom bubbelpool, kallbassäng o.dyl.",
     :en ""}},
   :shortest-slope-m
   {:name
    {:fi "Lyhin rinne m",
     :se "Kortaste slalombacken m",
     :en "Shortest slope m"},
    :data-type "numeric",
    :description
    {:fi "Lyhimmän rinteen pituus metreinä",
     :se "Kortaste skidbacken i meter",
     :en ""}},
   :squash-courts-count
   {:name
    {:fi "Squash-kentät lkm",
     :se "Antalet squashplaner",
     :en "Squash courts"},
    :data-type "numeric",
    :description
    {:fi "Squash-kenttien lukumäärä",
     :se "Antalet squash-planer",
     :en ""}},
   :boxing-rings-count
   {:name
    {:fi "Nyrkkeilykehät lkm",
     :se "Antalet boxningsringar",
     :en "Boxing rings pcs"},
    :data-type "numeric",
    :description
    {:fi "Nyrkkeilykehien lukumäärä",
     :se "Antalet boxningsringar",
     :en ""}},
   :ice-reduction?
   {:name
    {:fi "Jäätymisenesto", :se "Frostskydd", :en "Ice reduction"},
    :data-type "boolean",
    :description
    {:fi "Jäätymisenestojärjestelmä talviuintipaikassa",
     :se "Har vinterbadplatsen mekanism för frostskydd",
     :en ""}},
   :fencing-bases-count
   {:name
    {:fi "Miekkailualustat lkm",
     :se "Antalet fäktnings underlag",
     :en "Fencing bases"},
    :data-type "numeric",
    :description
    {:fi "Miekkailualustojen lukumäärä",
     :se "Antal underlägg avsedda för fäktning",
     :en ""}},
   :weight-lifting-spots-count
   {:name
    {:fi "Painonnostopaikat lkm",
     :se "Antalet tyngdlyftningsplatser",
     :en "Weight lifting spot"},
    :data-type "numeric",
    :description
    {:fi "Painonnostopaikkojen lukumäärä",
     :se "Antalet tyngdlyftningsplatser",
     :en ""}},
   :landing-places-count
   {:name
    {:fi "Alastulomontut lkm",
     :se "Antalet landningsgropar",
     :en "Landing places"},
    :data-type "numeric",
    :description
    {:fi "Alastulomonttujen lukumäärä",
     :se "Antalet landnigsgropar",
     :en ""}},
   :toboggan-run?
   {:name      {:fi "Ohjaskelkkamäki", :se "Rodelbana", :en "Toboggan run"},
    :data-type "boolean",
    :description
    {:fi "Onko rinnehiihtokeskuksessa ohjaskelkkamäki",
     :se "Har skidcentrumet en rodelbana",
     :en ""}},
   :sauna?
   {:name      {:fi "Sauna", :se "Bastu", :en "Sauna"},
    :data-type "boolean",
    :description
    {:fi "Onko sauna käytettävissä",
     :se "Är bastun i användning",
     :en ""}},
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
    {:fi "Pöytätennispöydät lkm",
     :se "Antalet bord för bordtennis",
     :en "Table tennis table count"},
    :data-type "numeric",
    :description
    {:fi "Pingis-/pöytätennispöytien lukumäärä",
     :se "Antal bordtennisbord (pingisbord)",
     :en ""}},
   :pool-max-depth-m
   {:name
    {:fi "1. altaan syvyys max m",
     :se "Första bassängens max djup m",
     :en "1. pool's depth max m"},
    :data-type "numeric",
    :description
    {:fi "1. altaan syvyys syvimmästä päästä metreinä",
     :se "Första bassängens djupaste punkt i meter",
     :en ""}},
   :loudspeakers?
   {:name
    {:fi "Äänentoisto", :se "Ljudåtergivning", :en "Loudspeakers"},
    :data-type "boolean",
    :description
    {:fi
     "Onko liikuntapaikalla välineistö ja valmius kenttäkuulutuksiin",
     :se
     "Har motionsplatsen utrustning och färdighet till att göra utrop",
     :en ""}},
   :shotgun-shooting?
   {:name
    {:fi "Haulikkoammunta",
     :se "Hagelgevärsskytte",
     :en "Shotgun shooting"},
    :data-type "boolean",
    :description
    {:fi "Haulikkoammuntamahdollisuus",
     :se "Möjlighet för hagelskytte",
     :en ""}},
   :lit-slopes-count
   {:name
    {:fi "Valaistut rinteet lkm",
     :se "Antalet belysta slalombackar",
     :en "Number of lit slopes"},
    :data-type "numeric",
    :description
    {:fi "Montako rinnettä on valaistu",
     :se "Hur många belysta slalombackar finns det",
     :en ""}},
   :green?
   {:name      {:fi "Puttausviheriö", :se "Puttnings green", :en "Green"},
    :data-type "boolean",
    :description
    {:fi "Onko golfkentällä puttausviheriö",
     :se "Finns det en puttnings green vid golfbanan",
     :en ""}},
   :free-rifle-shooting?
   {:name
    {:fi "Pienoiskivääriammunta",
     :se "Miniatyrgevärsskytte",
     :en "Free rifle shooting"},
    :data-type "boolean",
    :description
    {:fi "Pienoiskivääriammuntamahdollisuus",
     :se "Möjlighet förminiatyrgevärskytte",
     :en ""}},
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
     :en ""}},
   :field-3-area-m2
   {:name
    {:fi "3. kentän ala m2",
     :se "Tredje planens areal m2",
     :en "3. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän pinta-ala neliömetreinä",
     :se "Tredje planens areal i kvadratmeter",
     :en ""}},
   :accessibility-info
   {:name
    {:fi "Kohteesta on esteettömyystietoa",
     :se "Motionsstället har information om tillgänglighet",
     :en "Information about barrier free environment"},
    :data-type "string",
    :description
    {:fi "Kohteesta on saatavissa esteettömyystietoa",
     :se "Motionsstället har information om tillgänglighet",
     :en ""}},
   :covered-stand-person-count
   {:name
    {:fi "Katettua katsomoa hlö",
     :se "Takbeläggda läktarens person mängd",
     :en "Stand with roof"},
    :data-type "numeric",
    :description
    {:fi "Katetun katsomon henkilömäärä",
     :se "Hur mycket av läktaren är täckt med tak, antalet personer",
     :en ""}},
   :playground?
   {:name      {:fi "Leikkipuisto", :se "Lekpark", :en "Playground"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikan yhteydessä leikkipuisto",
     :se "Finns det en lekpark i samband med idrottsplatsen",
     :en ""}},
   :handball-fields-count
   {:name
    {:fi "Käsipallokentät lkm",
     :se "Antalet handbollsplaner",
     :en "Handball fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Käsipallokenttien lukumäärä salissa",
     :se "Antalet handbollsplaner som ryms i salen/hallen",
     :en ""}},
   :p-point
   {:name      {:fi "P-piste", :se "P-punkt", :en "P point"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrimäen P-piste metreinä",
     :se "Hoppbackens P-punkt i meter",
     :en ""}},
   :inruns-material
   {:name
    {:fi "Vauhtimäen latumateriaali",
     :se "Överbackens spårmaterial",
     :en "Inrun's material"},
    :data-type "string",
    :description
    {:fi "Hyppyrimäen vauhtimäen materiaali",
     :se "Hoppbackens spårmaterial vid överbacken",
     :en ""}},
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
     :en ""}},
   :volleyball-fields-count
   {:name
    {:fi "Lentopallokentät lkm",
     :se "Antalet volleybollplaner",
     :en "Volleyball field"},
    :data-type "numeric",
    :description
    {:fi "Lentopallokenttien lukumäärä",
     :se "Antalet volleybollplaner",
     :en ""}},
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
     :en ""}},
   :climbing-wall?
   {:name
    {:fi "Kiipeilyseinä", :se "Klättervägg", :en "Climbing wall"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntasalissa kiipeilyseinä",
     :se "Har motionssalen en klättervägg",
     :en ""}},
   :ski-track-freestyle?
   {:name
    {:fi "Luistelu-ura",
     :se "Fristils spår",
     :en "Freestyle ski track"},
    :data-type "boolean",
    :description
    {:fi "Vapaan tyylin latu-ura/luistelu-ura",
     :se "Skidspår för fristil",
     :en ""}},
   :spinning-hall?
   {:name
    {:fi "Spinning-sali", :se "Spinning sal", :en "Spinning hall"},
    :data-type "boolean",
    :description
    {:fi "Salissa spinning-varustus",
     :se "Salen har spinning utrustning",
     :en ""}},
   :other-platforms?
   {:name
    {:fi "Muut hyppytelineet",
     :se "Andra hoppställningar",
     :en "Other platforms"},
    :data-type "boolean",
    :description
    {:fi "Uimahyppytelineet rannalla",
     :se "Hopptornen vid stranden",
     :en ""}},
   :school-use?
   {:name
    {:fi "Koululiikuntapaikka",
     :se "Skolidrottsplats",
     :en "Sport facility in school use"},
    :data-type "boolean",
    :description
    {:fi "Liikuntapaikkaa käytetään koulujen liikuntatunneilla",
     :se "Idrottsplatsen används under skolornas gymnastiktimmar",
     :en ""}},
   :highjump-places-count
   {:name
    {:fi "Korkeushyppypaikat lkm",
     :se "Antalet höjdhopps platser",
     :en "High jump"},
    :data-type "numeric",
    :description
    {:fi "Korkeushppypaikkojen lukumäärä",
     :se "Antalet höjdhoppsplatser",
     :en ""}},
   :light-roof?
   {:name
    {:fi "Kevytkate", :se "Lättvikts takläggning", :en "Light roof"},
    :data-type "boolean",
    :description
    {:fi "Kentälle voidaan asentaa kevytkate tai muu tilapäinen katos",
     :se
     "På planen kan installeras en lättvikts takläggning eller något annat tillfälligt tak",
     :en ""}},
   :route-length-km
   {:name
    {:fi "Reitin pituus km",
     :se "Ruttens längd km",
     :en "Route's length km"},
    :data-type "numeric",
    :description
    {:fi "Reitin pituus kilometreinä",
     :se "Ruttens längd i kilometer",
     :en ""}},
   :exercise-machines-count
   {:name
    {:fi "Kuntoilulaitteet lkm",
     :se "Antalet gym apparater",
     :en "Number of exercise machines"},
    :data-type "numeric",
    :description
    {:fi "Kuntoilulaitteiden lukumäärä",
     :se "Antalet gym apparater",
     :en ""}},
   :track-type
   {:name        {:fi "Ratatyyppi", :se "Typ av bana", :en "Type of track"},
    :data-type   "string",
    :description {:fi "Radan tyyppi", :se "Banans typ", :en ""}},
   :training-spot-surface-material
   {:name
    {:fi "Suorituspaikan pintamateriaali",
     :se "Prestationsplatsens ytmaterial",
     :en "Surface material for training spot"},
    :data-type "string",
    :description
    {:fi "Esim. keihäänheittopaikan pintamateriaali / päällys",
     :se "T.ex. spjutkastningsplatsens ytmaterial/överläggning",
     :en ""}},
   :range?
   {:name
    {:fi "Harjoitusalue/range", :se "Övningszon/Range", :en "Range"},
    :data-type "boolean",
    :description
    {:fi "Onko golfin harjoitusalue/range",
     :se "Finns det övningsområde / range för golf",
     :en ""}},
   :track-length-m
   {:name
    {:fi "Radan pituus m",
     :se "Banans längd i m",
     :en "Length of track m"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan, pyöräilyradan tms. pituus metreinä",
     :se "Löpbanans, rundbanans el.dyl. längd i meter",
     :en ""}}
   :padel-courts-count
   {:name
    {:fi "Padelkentät lkm"
     :se "Antalet padelbanor"
     :en "Number of padel courts"}
    :data-type "numeric"
    :description
    {:fi "",
     :se "",
     :en ""}}})

(def used
  (let [used (set (mapcat (comp keys :props second) types/all))]
    (select-keys all used)))

(comment
  (require '[clojure.set :as cset])
  (cset/difference (keys all) (keys used))
  (cset/difference (keys used) (keys all))

  
  ;; Unused?
  #{:ski-track? :winter-swimming? :archery?
    :throwing-sports-spots-count :radio-and-tv-capabilities?
    :horse-carriage-allowed? :hall-length-m :old-lipas-typecode
    :adp-readiness? :classified-route :hall-width-m :info-fi
    :pool-length-mm})
