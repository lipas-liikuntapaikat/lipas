(ns lipas.data.prop-types)

(def all
  {:height-m
   {:name
    {:fi "Tilan korkeus m",
     :se "Utrymmets höjd",
     :en "Venue's height"},
    :data-type "numeric",
    :description
    {:fi "Sisäliikuntatilan korkeus metreinä",
     :se "Höjd av salen",
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
     :se "2a planens areal m2",
     :en "2. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän pinta-ala neliömetreinä",
     :se "Andra planens areal i kvadratmeter",
     :en ""}},
   :surface-material
   {:name
    {:fi "Pintamateriaali",
     :se "Ytmaterial",
     :en "Surface material"},
    :data-type "enum-coll",
    :description
    {:fi
     "Millainen pintamateriaali liikuntapaikalla on. Yleisurheilukentässä keskikentän päällyste.",
     :se "Ytmaterial",
     :en ""}},
   :ski-track?
   {:name      {:fi "Latu", :se "Skidspår", :en "Ski track"},
    :data-type "boolean",
    :description
    {:fi
     "Onko kuntorata myös latu (jos on, saa myös tyyppikoodin 4402)",
     :se
     "Är konditionsbanan även ett skidspår (om ja, får också typkoden 4402)",
     :en ""}},
   :basketball-fields-count
   {:name
    {:fi "Koripallokentät lkm",
     :se "Korgbollsplaner st.",
     :en "Basketball fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako koripallokenttää salissa on",
     :se "Antalet korgbollsplaner i salen",
     :en ""}},
   :surface-material-info
   {:name
    {:fi "Pintamateriaali lisätieto",
     :se "Ytmaterial information",
     :en "Surface material information"},
    :data-type "string",
    :description
    {:fi "Pintamateriaalin lisätieto",
     :se "Ytmaterial mer information",
     :en ""}},
   :holes-count
   {:name        {:fi "Väylien lkm", :se "Hål st.", :en "Number of holes"},
    :data-type   "numeric",
    :description {:fi "Montako väylää", :se "Hur många hål", :en ""}},
   :skijump-hill-type
   {:name
    {:fi "Hyppyrimäen tyyppi",
     :se "Typ av hoppbacke",
     :en "Type of ski jump hill"},
    :data-type "string",
    :description
    {:fi "Hyppyrimäen tyyppi (harjoitus, pien, normaali, suurmäki)",
     :se
     "Typ av hoppbacke (övningsbacke, liten, normalbacke, storbacke)",
     :en ""}},
   :lifts-count
   {:name      {:fi "Hissit lkm", :se "Skidliftar (antal)", :en "Lifts"},
    :data-type "numeric",
    :description
    {:fi "Montako hissiä hiihtokeskuksessa tms. on",
     :se "Antal skidlidtar i skidcentrumet",
     :en ""}},
   :field-3-length-m
   {:name
    {:fi "3. kentän pituus m",
     :se "3e planens längd m",
     :en "3. field's length m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän pituus metreinä",
     :se "Tredje planens längd i meter",
     :en ""}},
   :pool-tracks-count
   {:name
    {:fi "1. altaan radat lkm",
     :se "1a bassängens banor",
     :en "Courses in 1. pool"},
    :data-type "numeric",
    :description
    {:fi "1.altaan ratojen lukumäärä",
     :se "Antal banor i första bassängen",
     :en ""}},
   :field-2-length-m
   {:name
    {:fi "2. kentän pituus m",
     :se "2a planens längd m",
     :en "2. field's length m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän pituus metreinä",
     :se "Andra planens längd i meter",
     :en ""}},
   :plastic-outrun?
   {:name
    {:fi "Muovitettu alastulo",
     :se "Nederbackens plastbeläggning",
     :en "Plastic outrun"},
    :data-type "boolean",
    :description
    {:fi "Onko hyppyrimäen alastulopaikka muovitettu",
     :se "Har nederbacken plastbeläggning",
     :en ""}},
   :automated-timing?
   {:name
    {:fi "Automaattinen ajanotto",
     :se "Automatisk tidtagning",
     :en "Automatic timing"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla välineistö automaattiseen ajanottoon",
     :se "Har idrottsplatsen utrustning för automatisk tidtagning",
     :en ""}},
   :freestyle-slope?
   {:name
    {:fi "Kumparerinne", :se "Puckelpist", :en "Freestyle slope"},
    :data-type "boolean",
    :description
    {:fi "Onko rinnehiihtokeskuksessa kumparerinne",
     :se "Har skidcentrat puckelpist",
     :en ""}},
   :kiosk?
   {:name      {:fi "Kioski", :se "Kiosk", :en "Kiosk"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla kioski",
     :se "Har idrottsplatsen en kiosk",
     :en ""}},
   :summer-usage?
   {:name      {:fi "Kesäkäyttö", :se "Sommarbruk", :en "Summer usage"},
    :data-type "boolean",
    :description
    {:fi "Käytössä myös kesäisin",
     :se "Kan användas på sommaren",
     :en ""}},
   :stand-capacity-person
   {:name
    {:fi "Katsomon kapasiteetti hlö",
     :se "Läktarens kapasitet pers.",
     :en "Stand size"},
    :data-type "numeric",
    :description
    {:fi "Katsomon koko kapasiteetti, henkilölukumäärä",
     :se "Läktarens totala kapasitet, antalet personer",
     :en ""}},
   :pier?
   {:name      {:fi "Laituri", :se "Brygga", :en "Pier"},
    :data-type "boolean",
    :description
    {:fi "Onko rannalla myös laituri",
     :se "Finns det en brygga på stranden",
     :en ""}},
   :may-be-shown-in-excursion-map-fi?
   {:name
    {:fi "Saa julkaista Retkikartta.fi-palvelussa",
     :se "Kan visas på Utflyktskarta.fi",
     :en "May be shown in Excursionmap.fi"},
    :data-type "boolean",
    :description
    {:fi "Kohteen tiedot saa julkaista Retkikartta.fi- palvelussa",
     :se "Objekts information kan visas på Utflyktskarta.fi",
     :en ""}},
   :sprint-lanes-count
   {:name
    {:fi "Etusuorien lkm",
     :se "Upplopp st. ",
     :en "Number of sprint lanes"},
    :data-type "numeric",
    :description
    {:fi "Montako etusuoraa on", :se "Antalet upploppsrakor", :en ""}},
   :javelin-throw-places-count
   {:name
    {:fi "Keihäänheittopaikat lkm",
     :se "Spjutkastplatser st.",
     :en "Number of javelin throw places"},
    :data-type "numeric",
    :description
    {:fi "Montako keihäänheittopaikkaa on",
     :se "Hur många spjutkastplatser finns det",
     :en ""}},
   :tennis-courts-count
   {:name
    {:fi "Tenniskentät lkm",
     :se "Tennisplaner st.",
     :en "Tennis courts pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako tenniskenttää salissa on", :se "", :en ""}},
   :ski-service?
   {:name
    {:fi "Suksihuolto", :se "Service för skidor", :en "Ski service"},
    :data-type "boolean",
    :description
    {:fi "Onko suksihuoltopiste olemassa",
     :se "Finns det ett utrymme för service för skidor",
     :en ""}},
   :field-1-length-m
   {:name
    {:fi "1. kentän pituus m",
     :se "1a planens längd m",
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
    {:fi "Onko liikuntapaikalla maalikamera",
     :se "Finns det en målkamera vid idrottsplatsen",
     :en ""}},
   :parking-place?
   {:name
    {:fi "Parkkipaikka", :se "Parkeringsplats", :en "Parking place"},
    :data-type "boolean",
    :description
    {:fi "Onko parkkipaikkaa",
     :se "Finns det en parkeringsplats",
     :en ""}},
   :climbing-routes-count
   {:name
    {:fi "Kiipeilyreittien lkm",
     :se "Klättringsrutter st.",
     :en "Climbing routes pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako kiipeilyreittiä on käytettävissä",
     :se "Antalet klättringsrutter",
     :en ""}},
   :outdoor-exercise-machines?
   {:name
    {:fi "Kuntoilutelineitä",
     :se "Gymapparater utomhus",
     :en "Exercise machines outdoors"},
    :data-type "boolean",
    :description
    {:fi "Onko reitin varrella kuntoilulaitteita",
     :se "Finns det gymapparater vid rutten",
     :en ""}},
   :automated-scoring?
   {:name
    {:fi "Kirjanpitoautomaatti",
     :se "Bokföringsautomat",
     :en "Automatic scoring"},
    :data-type "boolean",
    :description
    {:fi "Keilaradan sähköinen pistelasku",
     :se "Bowlingbanans elektroniska poängräkning",
     :en ""}},
   :track-width-m
   {:name
    {:fi "Radan leveys m",
     :se "Bredd av bana m",
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
     :se "Finns det möjlighet för isklättring",
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
     :se "Fartbackens material",
     :en "Ski jump hill material"},
    :data-type "string",
    :description
    {:fi "Vauhtimäen rakennemateriaali",
     :se "Fartbackens material",
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
     :se "Cirkulerande löpbanor st.",
     :en "Number of circular lanes"},
    :data-type "numeric",
    :description
    {:fi "Montaka kiertävää juoksurataa on",
     :se "Antalet cirkulerande löpbanor",
     :en ""}},
   :boat-launching-spot?
   {:name
    {:fi "Veneen vesillelaskupaikka",
     :se "Plats för sjösättningen",
     :en "Place for launching a boat"},
    :data-type "boolean",
    :description
    {:fi "Mahdollisuus veneen vesillelaskuun",
     :se "Plats för båt sjösättning ",
     :en ""}},
   :ski-track-traditional?
   {:name
    {:fi "Perinteinen latu",
     :se "Skidspår klassisk stil",
     :en "Traditional ski track"},
    :data-type "boolean",
    :description
    {:fi "Perinteisen tyylin hiihtomahdollisuus / latu-ura",
     :se "Skidspår för klassisk stil",
     :en ""}},
   :winter-swimming?
   {:name
    {:fi "Talviuintipaikka",
     :se "Vinterbadplats",
     :en "Winter swimming"},
    :data-type "boolean",
    :description
    {:fi "Onko rannalla myös talviuintipaikka",
     :se "Möjlighet för vinterbad vid stranden",
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
     :se "Klätterväggens höjd i meter",
     :en ""}},
   :route-width-m
   {:name
    {:fi "Reitin leveys m",
     :se "Ruttens bredd m",
     :en "Route's width m"},
    :data-type   "numeric",
    :description {:fi "Reitin leveys metreinä", :se "", :en ""}},
   :beach-length-m
   {:name
    {:fi "Rannan pituus m",
     :se "Strandens längd m",
     :en "Length of beach m"},
    :data-type   "numeric",
    :description {:fi "Rannan pituus metreinä", :se "", :en ""}},
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
     :se "Upploppsrakans längd",
     :en "Length of sprint track"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan etusuoran pituus",
     :se "Upploppsrakans längd i meter",
     :en ""}},
   :archery?
   {:name      {:fi "Jousiammunta", :se "Bågskytte", :en "Archery"},
    :data-type "boolean",
    :description
    {:fi "Jousiammuntamahdollisuus",
     :se "Möjlighet för bågskytte",
     :en ""}},
   :throwing-sports-spots-count
   {:name
    {:fi "Heittolajien harjoituspaikat lkm",
     :se "Övningsplatser för kastgrenar",
     :en "Throwing sports places"},
    :data-type "numeric",
    :description
    {:fi "Montako heittolajien (yleisurheilu) harjoituspaikkaa on",
     :se "Antalet övningsplatser för kastgrenar (friidrott)",
     :en ""}},
   :radio-and-tv-capabilities?
   {:name
    {:fi "Radio- ja tv-valmius",
     :se "Radio-/tv-möjlighet",
     :en "Radio/TV possible"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla radio- ja tv-valmiudet",
     :se "Finns det radio- och tv-beredskap på idrottsplatsen",
     :en ""}},
   :inner-lane-length-m
   {:name
    {:fi "Sisäradan pituus m",
     :se "Innerbanans längd m",
     :en "Length of inner lane m"},
    :data-type "numeric",
    :description
    {:fi "Sisäradan pituus kiertävissä juoksuradoissa",
     :se "Innerbanans längd i meter på en rundbana",
     :en ""}},
   :discus-throw-places
   {:name
    {:fi "Kiekonheittopaikat lkm",
     :se "Diskusplatser st.",
     :en "Number of discus throw places"},
    :data-type "numeric",
    :description
    {:fi "Montako kiekonheittopaikkaa on",
     :se "Antalet diskuskastningsplatser",
     :en ""}},
   :fields-count
   {:name
    {:fi "Kenttien lkm", :se "Planer st.", :en "Number of fields"},
    :data-type "numeric",
    :description
    {:fi "Montako saman tyypin kenttää liikuntapaikassa on",
     :se "Antalet likadana planer det finns vid idrottsplatsen",
     :en ""}},
   :field-1-width-m
   {:name
    {:fi "1. kentän leveys m",
     :se "1a planens bredd m",
     :en "1. field's width m"},
    :data-type "numeric",
    :description
    {:fi "1. kentän leveys metreinä",
     :se "Första planens bredd i meter",
     :en ""}},
   :field-3-width-m
   {:name
    {:fi "3. kentän leveys m",
     :se "3e planens bredd m",
     :en "3. field's width m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän leveys metreinä",
     :se "Tredje planens bredd i meter",
     :en ""}},
   :field-2-width-m
   {:name
    {:fi "2. kentän leveys m",
     :se "2a plan bredd m",
     :en "2. field's width m"},
    :data-type "numeric",
    :description
    {:fi "2. kentän leveys metreinä",
     :se "Andra planens bredd i meter",
     :en ""}},
   :badminton-courts-count
   {:name
    {:fi "Sulkapallokentät lkm",
     :se "Badmintonplaner st.",
     :en "Badminton courts pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako sulkapallokenttää salissa on", :se "", :en ""}},
   :hammer-throw-places-count
   {:name
    {:fi "Moukarinheittopaikat lkm",
     :se "Släggkastningsplatser st.",
     :en "Hammer throw"},
    :data-type "numeric",
    :description
    {:fi "Moukarinheittopaikkojen lukumäärä",
     :se "Antal platser för att kasta slägga",
     :en ""}},
   :horse-carriage-allowed?
   {:name
    {:fi "Kärryillä ajo sallittu",
     :se "Sulky tillåtet",
     :en "Horse carriage allowed"},
    :data-type "boolean",
    :description
    {:fi "Saako ratsastusreitillä ajaa myös hevoskärryillä",
     :se "Tillåtet att åka med sulky på ridrutten",
     :en ""}},
   :pool-width-m
   {:name
    {:fi "1. altaan leveys m",
     :se "1a bassängens bredd",
     :en "1. pool's width"},
    :data-type "numeric",
    :description
    {:fi "1.altaan/pääaltaan leveys metreinä",
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
   {:name      {:fi "Kaukalot lkm", :se "Rinkar st.", :en "Ice rinks"},
    :data-type "numeric",
    :description
    {:fi "Montako kaukaloa liikuntapaikalla on",
     :se "Antalet rinkar(hockey) finns det på idrottsplatsen",
     :en ""}},
   :field-1-area-m2
   {:name
    {:fi "1. kentän ala m2",
     :se "1a planens areal m2",
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
     :se "Stavhopp st.",
     :en "Pole vault"},
    :data-type "numeric",
    :description
    {:fi "Seiväshyppypaikkojen lukumäärä", :se "", :en ""}},
   :group-exercise-rooms-count
   {:name
    {:fi "Ryhmäliikuntatilat lkm",
     :se "Utrymme för motionsgrupper",
     :en "Room for exercise groups"},
    :data-type "numeric",
    :description
    {:fi "Montako jumppasalia tai muuta ryhmäliikuntatilaa on",
     :se "Antalet gymnastiksalar för gruppmotion",
     :en ""}},
   :snowpark-or-street?
   {:name      {:fi "Temppurinne", :se "Trickbana", :en "Snow park/street"},
    :data-type "boolean",
    :description
    {:fi
     "Onko rinnehiihtokeskuksessa ns. temppurinne, snowpark tai vastaava",
     :se "Har skidcentrat en trickbana",
     :en ""}},
   :max-vertical-difference
   {:name
    {:fi "Korkeusero max m",
     :se "Höjdskillnad max m",
     :en "Max vertical difference"},
    :data-type "numeric",
    :description
    {:fi "Suurin korkeusero rinteissä",
     :se "Högsta höjdskillnaden i pisten",
     :en ""}},
   :bowling-lanes-count
   {:name
    {:fi "Keilaradat lkm",
     :se "Bowlingsbanor st.",
     :en "Bowling lanes"},
    :data-type "numeric",
    :description
    {:fi "Keilaratojen lukumäärä",
     :se "Hur många bowlingbanor finns det",
     :en ""}},
   :air-gun-shooting?
   {:name
    {:fi "Ilma-aseammunta",
     :se "Luftvapenskytte",
     :en "Air gun shooting"},
    :data-type "boolean",
    :description
    {:fi "Ilma-aseammuntamahdollisuus",
     :se "Möjlighet för luftvapenskytte",
     :en ""}},
   :gymnastic-routines-count
   {:name
    {:fi "Telinevoimistelusarjat lkm",
     :se "Redskapsserier st.",
     :en "Gymnastic routines"},
    :data-type "numeric",
    :description
    {:fi "Telinevoimistelun telinesarjojen lukumäärä",
     :se "Redskapsgymnastiska redkskap, antal",
     :en ""}},
   :toilet?
   {:name      {:fi "Yleisö-wc", :se "Wc-utrymme", :en "Toilet"},
    :data-type "boolean",
    :description
    {:fi "Onko yleisö-wc:tä käytettävissä",
     :se "Finns det wc-utrymme",
     :en ""}},
   :gymnastics-space?
   {:name
    {:fi "Telinevoimistelutila",
     :se "Utrymme för redskapsgymnastik",
     :en "Space for gymnastics"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntasalissa myös telinevoimistelutila",
     :se "Utrymme för redskapssgymnastik i salen",
     :en ""}},
   :show-jumping?
   {:name      {:fi "Esteratsastus", :se "Banhoppning", :en "Show jumping"},
    :data-type "boolean",
    :description
    {:fi
     "Onko ratsastuskentällä / maneesissa esteratsastukseen soveltuva varustus",
     :se "Har ridbanan / manegen utrustning för banhoppning",
     :en ""}},
   :shower?
   {:name        {:fi "Suihku", :se "Dusch", :en "Shower"},
    :data-type   "boolean",
    :description {:fi "Onko suihku käytettävissä", :se "", :en ""}},
   :rest-places-count
   {:name
    {:fi "Taukopaikat lkm", :se "Pausplatser st.", :en "Rest places"},
    :data-type "numeric",
    :description
    {:fi "Montako taukopaikkaa reitin varrella on", :se "", :en ""}},
   :changing-rooms?
   {:name
    {:fi "Pukukopit", :se "Omklädningsrum", :en "Changing rooms"},
    :data-type "boolean",
    :description
    {:fi "Onko uimapaikalla pukukoppeja",
     :se "Finns det omklädningsrum vid badplatsen",
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
    {:fi "Montako halfpipea, superpipea tms. on",
     :se "Antal halfpipe",
     :en ""}},
   :shooting-positions-count
   {:name
    {:fi "Ampumapaikat lkm",
     :se "Skytteplatser st.",
     :en "Shooting positions"},
    :data-type "numeric",
    :description
    {:fi "Montako ampumapaikkaa liikuntareitin varrella on",
     :se "Hur många skytteplatser finns det",
     :en ""}},
   :running-track-surface-material
   {:name
    {:fi "Juoksuradan pintamateriaali",
     :se "Ytmaterial för löpbana",
     :en "Surface material for running track"},
    :data-type "string",
    :description
    {:fi "Juoksuradan pintamateriaali/päällyste",
     :se "Löpbanans ytmaterial",
     :en ""}},
   :tatamis-count
   {:name        {:fi "Tatamit lkm", :se "Tatamimattor", :en "Tatamis pcs"},
    :data-type   "numeric",
    :description {:fi "Montako tatamia on", :se "", :en ""}},
   :lit-route-length-km
   {:name
    {:fi "Valaistua reittiä km",
     :se "Belysta rutten km",
     :en "Lit route's length km"},
    :data-type "numeric",
    :description
    {:fi "Montako kilometriä reitistä on valaistua",
     :se "Belysta rutten km",
     :en ""}},
   :area-m2
   {:name
    {:fi "Pinta-ala m2", :se "Areal m2", :en "Area in square meters"},
    :data-type "numeric",
    :description
    {:fi "Liikuntapaikan pinta-ala, neliömetrejä",
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
    {:fi "Montako painimattoa on käytettävissä",
     :se "Antal brottarmattor",
     :en ""}},
   :eu-beach?
   {:name      {:fi "EU-uimaranta", :se "EU-badstrand", :en "EU beach"},
    :data-type "boolean",
    :description
    {:fi
     "Uimaranta, joka täyttää EU-kriteerit uimaveden laadusta ja valvonnasta",
     :se "Badstrand, som fyller EU-kriterierna",
     :en ""}},
   :hall-length-m
   {:name
    {:fi "Hallin pituus m",
     :se "Längd av hallen m",
     :en "Length of hall m"},
    :data-type "numeric",
    :description
    {:fi "1. maneesin pituus metreinä, liikunta-ala",
     :se "Första manegens längd i meter",
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
     :se "Simbassänger st.",
     :en "Number of swimming pools"},
    :data-type "numeric",
    :description
    {:fi "Uima-altaiden lukumäärä, myös terapia-altaat",
     :se "Hur många simbassänger finns det",
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
     :se "Kätterväggens bredd i meter",
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
     :se "",
     :en ""}},
   :futsal-fields-count
   {:name
    {:fi "Futsal-kentät lkm",
     :se "Futsal planer st.",
     :en "Number of futsal fields"},
    :data-type "numeric",
    :description
    {:fi "Futsal-kenttien lukumäärä",
     :se "Antalet futsal planer",
     :en ""}},
   :training-wall?
   {:name
    {:fi "Lyöntiseinä",
     :se "Vägg att träna tennis",
     :en "Training wall for tennis"},
    :data-type "boolean",
    :description
    {:fi "Onko tenniskentällä lyöntiseinä",
     :se "Finns det en träningsvägg vid tennisplanen",
     :en ""}},
   :shotput-count
   {:name
    {:fi "Kuulantyöntöpaikat lkm",
     :se "Kulstötningsplatser st.",
     :en "Shot put"},
    :data-type "numeric",
    :description
    {:fi "Kuulantyöntöpaikkojen lukumäärä",
     :se "Antalet kulstötningsplatser",
     :en ""}},
   :longjump-places-count
   {:name
    {:fi "Pituus- ja kolmiloikkapaikat lkm",
     :se "Längd- och trestegshopp st.",
     :en "Long jump"},
    :data-type "numeric",
    :description
    {:fi "Pituus- ja kolmiloikkapaikkojen lukumäärä",
     :se "Antal längd- och trestegsplatser",
     :en ""}},
   :football-fields-count
   {:name
    {:fi "Jalkapallokentät lkm",
     :se "Fotbollsplaner st.",
     :en "Football fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako jalkapallokenttää mahtuu saliin/halliin",
     :se "Antalet fotbollsplaner som ryms i salen/hallen",
     :en ""}},
   :floorball-fields-count
   {:name
    {:fi "Salibandykentät lkm",
     :se "Innebandyplaner st.",
     :en "Floor ball field"},
    :data-type   "numeric",
    :description {:fi "Montako salibandykenttää on", :se "", :en ""}},
   :equipment-rental?
   {:name
    {:fi "Välinevuokraus",
     :se "Hyrning av utrustning",
     :en "Equipment rental"},
    :data-type "boolean",
    :description
    {:fi "Onko välinevuokrausta tarjolla",
     :se "Möjlighet att hyra utrustning",
     :en ""}},
   :slopes-count
   {:name
    {:fi "Rinteiden lkm",
     :se "Slalombackar st.",
     :en "Number of slopes"},
    :data-type "numeric",
    :description
    {:fi "Rinteiden määrä yhteensä",
     :se "Totala antal slalombackar",
     :en ""}},
   :old-lipas-typecode
   {:name
    {:fi "Vanha Lipas- tyyppi",
     :se "gamla Lipas-typ",
     :en "old Lipas-type"},
    :data-type "integer",
    :description
    {:fi "tyyppikoodi vanhassa luokituksessa",
     :se "Typkod i gamla klassificeringen",
     :en ""}},
   :other-pools-count
   {:name
    {:fi "Muut altaat lkm",
     :se "Andra bassänger",
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
     :se "Kortaste skidbacke m",
     :en "Shortest slope m"},
    :data-type "numeric",
    :description
    {:fi "Lyhimmän rinteen pituus metreinä",
     :se "Kortaste skidbacken i meter",
     :en ""}},
   :adp-readiness?
   {:name
    {:fi "Atk-valmius", :se "ADB-beredskap", :en "Adp readiness"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikalla atk-valmiudet tulospalveluihin",
     :se "",
     :en ""}},
   :squash-courts-count
   {:name
    {:fi "Squash-kentät lkm",
     :se "Squashplaner st.",
     :en "Squash courts"},
    :data-type   "numeric",
    :description {:fi "Montako squash-kenttää on", :se "", :en ""}},
   :boxing-rings-count
   {:name
    {:fi "Nyrkkeilykehät lkm",
     :se "Boxningsringar st.",
     :en "Boxing rings pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako nyrkkeilykehää on",
     :se "Antalet boxningsringar",
     :en ""}},
   :ice-reduction?
   {:name      {:fi "Jäätymisenesto", :se "Isfritt", :en "Ice reduction"},
    :data-type "boolean",
    :description
    {:fi "Onko talviuintipaikassa jäätymisenestojärjestelmä",
     :se "Har vinterbadplatsen mekanism för isfrihet",
     :en ""}},
   :fencing-bases-count
   {:name
    {:fi "Miekkailualustat lkm",
     :se "Fäktning underlag st.",
     :en "Fencing bases"},
    :data-type "numeric",
    :description
    {:fi "Miekkailualustojen lukumäärä",
     :se "Antal underlägg avsedda för fäktning",
     :en ""}},
   :classified-route
   {:name
    {:fi "Luokiteltu reitti",
     :se "Klassificerad rutt",
     :en "Classified route"},
    :data-type "string",
    :description
    {:fi
     "Suomen ladun reittiluokituksen mukainen luokitus (helppo---vaativa)",
     :se "Klassificerad rutt (enkel---svår) enligt Suomen Latu",
     :en ""}},
   :weight-lifting-spots-count
   {:name
    {:fi "Painonnostopaikat lkm",
     :se "Tyngdlyftningsplatser (st.)",
     :en "Weight lifting spot"},
    :data-type "numeric",
    :description
    {:fi "Montako painonnostopaikkaa on",
     :se "Antalet tyngdlyftningsplatser",
     :en ""}},
   :landing-places-count
   {:name
    {:fi "Alastulomontut lkm",
     :se "Underbacke st.",
     :en "Landing places"},
    :data-type "numeric",
    :description
    {:fi "Montako alastulomonttua on käytettävissä", :se "", :en ""}},
   :toboggan-run?
   {:name      {:fi "Ohjaskelkkamäki", :se "Rodelbana", :en "Toboggan run"},
    :data-type "boolean",
    :description
    {:fi "Onko rinnehiihtokeskuksessa ohjaskelkkamaki",
     :se "Har skidcentrat en rodelbana",
     :en ""}},
   :sauna?
   {:name        {:fi "Sauna", :se "Bastu", :en "Sauna"},
    :data-type   "boolean",
    :description {:fi "Onko sauna käytettävissä", :se "", :en ""}},
   :jumps-count
   {:name
    {:fi "Hyppyrien lkm", :se "Uthopp st.", :en "Number of jumps"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrien lukumäärä", :se "Antalet uthopp", :en ""}},
   :table-tennis-count
   {:name
    {:fi "Pöytätennis lkm", :se "Bordtennis st.", :en "Table tennis"},
    :data-type "numeric",
    :description
    {:fi "Pingis-/pöytätennispöytien lukumäärä",
     :se "Antal bordtennisbord (pingisbord)",
     :en ""}},
   :pool-max-depth-m
   {:name
    {:fi "1. altaan syvyys max m",
     :se "1a bassängens djup max m",
     :en "1. pool's depth max m"},
    :data-type "numeric",
    :description
    {:fi "1.altaan syvyys syvimmästä päästä metreinä",
     :se "Första bassängens djupaste punkt i meter",
     :en ""}},
   :loudspeakers?
   {:name
    {:fi "Äänentoisto", :se "Ljudanläggning", :en "Loudspeakers"},
    :data-type "boolean",
    :description
    {:fi
     "Onko liikuntapaikalla välineistö ja valmius kenttäkuulutuksiin",
     :se "",
     :en ""}},
   :shotgun-shooting?
   {:name
    {:fi "Haulikkoammunta",
     :se "Hagelskyttebana",
     :en "Shotgun shooting"},
    :data-type "boolean",
    :description
    {:fi "Haulikkoammuntamahdollisuus",
     :se "Möjlighet för hagelskytte",
     :en ""}},
   :lit-slopes-count
   {:name
    {:fi "Valaistut rinteet lkm",
     :se "Belysta skidbackar",
     :en "Number of lit slopes"},
    :data-type "numeric",
    :description
    {:fi "Montako rinnettä on valaistu",
     :se "Hur många belysta skidbackar finns det",
     :en ""}},
   :green?
   {:name      {:fi "Puttausviheriö", :se "Puttgreen", :en "Green"},
    :data-type "boolean",
    :description
    {:fi "Onko golfkentällä puttausviheriö",
     :se "Finns det en puttgreen på golfbanan",
     :en ""}},
   :free-rifle-shooting?
   {:name
    {:fi "Pienoiskivääriammunta",
     :se "Salongsgevärskytte",
     :en "Free rifle shooting"},
    :data-type "boolean",
    :description
    {:fi "Pienoiskivääriammuntamahdollisuus",
     :se "Möjlighet för salongsgevärskytte",
     :en ""}},
   :winter-usage?
   {:name
    {:fi "Talvikäyttö",
     :se "Kan användas på vintern",
     :en "Winter usage"},
    :data-type "boolean",
    :description
    {:fi "On käytössä myös talvisin",
     :se "Även i vinterbruk",
     :en ""}},
   :ligthing?
   {:name      {:fi "Valaistus", :se "Belysning", :en "Lighting"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikka valaistu",
     :se "Belysning finns",
     :en ""}},
   :field-3-area-m2
   {:name
    {:fi "3. kentän ala m2",
     :se "3e planens areal m2",
     :en "3. field's area sq. m"},
    :data-type "numeric",
    :description
    {:fi "3. kentän pinta-ala neliömetreinä",
     :se "Tredje planens areal i kvadratmeter",
     :en ""}},
   :accessibility-info
   {:name
    {:fi "Kohteesta on esteettömyystietoa",
     :se "Information om tillgänglighet",
     :en "Information about barrier free environment"},
    :data-type "string",
    :description
    {:fi "Kohteesta on saatavissa esteettömyystietoa",
     :se "Information om tillgänglighet finns",
     :en ""}},
   :covered-stand-person-count
   {:name
    {:fi "Katettua katsomoa hlö",
     :se "Läktare med tak pers.",
     :en "Stand with roof"},
    :data-type "numeric",
    :description
    {:fi "Kuinka paljon on katettua katsomotilaa, henkilömäärä",
     :se "Hur mycket av läktaren är täckt med tak, antalet personer",
     :en ""}},
   :playground?
   {:name      {:fi "Leikkipuisto", :se "Lekpark", :en "Playground"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntapaikassa leikkipuisto",
     :se "Finns det en lekpark i idrottsplatsen",
     :en ""}},
   :handball-fields-count
   {:name
    {:fi "Käsipallokentät lkm",
     :se "Handbollsplaner st.",
     :en "Handball fields pcs"},
    :data-type "numeric",
    :description
    {:fi "Montako käsipallokenttää mahtuu saliin/halliin",
     :se "Antalet handbollsplaner som ryms i salen/hallen",
     :en ""}},
   :p-point
   {:name      {:fi "P-piste", :se "P-punkt", :en "P point"},
    :data-type "numeric",
    :description
    {:fi "Hyppyrimäen p-piste metreinä",
     :se "Hoppbackens p-punkt i meter",
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
     :se "Typ av korgbollsplan",
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
     :se "Volleybollplaner st.",
     :en "Volleyball field"},
    :data-type "numeric",
    :description
    {:fi "Montako lentopallokenttää on",
     :se "Antalet volleybollplaner",
     :en ""}},
   :boat-places-count
   {:name
    {:fi "Venepaikat lkm", :se "Båtplats st.", :en "Boat places"},
    :data-type "numeric",
    :description
    {:fi "Venepaikkojen lukumäärä", :se "Hur många båtplats", :en ""}},
   :pool-temperature-c
   {:name
    {:fi "1. altaan lämpö c",
     :se "1a bassängens temperatur c",
     :en "1. pool's temperature c"},
    :data-type "numeric",
    :description
    {:fi "1. altaan veden lämpötila celsius-asteina",
     :se "Vattnets temperatur i celsius i den första bassängen",
     :en ""}},
   :hall-width-m
   {:name
    {:fi "Hallin leveys m",
     :se "Bredd av hallen m",
     :en "Width of hall m"},
    :data-type "numeric",
    :description
    {:fi "1.maneesin leveys metreinä, ",
     :se "Första manegens bredd i meter",
     :en ""}},
   :climbing-wall?
   {:name
    {:fi "Kiipeilyseinä", :se "Klättervägg", :en "Climbing wall"},
    :data-type "boolean",
    :description
    {:fi "Onko liikuntasalissa kiipeilyseinä",
     :se "Har gymnastiksalen en klättervägg",
     :en ""}},
   :ski-track-freestyle?
   {:name
    {:fi "Luistelu-ura",
     :se "Skidspår fristil",
     :en "Freestyle ski track"},
    :data-type "boolean",
    :description
    {:fi "Vapaan tyylin latu-ura/luistelu-ura.",
     :se "Skidspår för fristil",
     :en ""}},
   :spinning-hall?
   {:name
    {:fi "Spinning-sali", :se "Spinning sal", :en "Spinning hall"},
    :data-type   "boolean",
    :description {:fi "Onko sali spinning-sali", :se "", :en ""}},
   :info-fi
   {:name
    {:fi "Lisätieto", :se "Mer information", :en "More information"},
    :data-type "string",
    :description
    {:fi "Liikuntapaikan lisätiedot, vapaa tekstikenttä",
     :se "Idrottsplatsen öviga information, fri text",
     :en ""}},
   :other-platforms?
   {:name
    {:fi "Muut hyppytelineet",
     :se "Andra hoppställningar",
     :en "Other platforms"},
    :data-type "boolean",
    :description
    {:fi "Onko rannalla hyppyteline",
     :se "Finns det en hoppställning vid stranden",
     :en ""}},
   :highjump-places-count
   {:name
    {:fi "Korkeushyppypaikat lkm",
     :se "Höjdhopp platser st.",
     :en "High jump"},
    :data-type "numeric",
    :description
    {:fi "Montako korkeushyppypaikkaa on",
     :se "Antalet höjdhoppsplater",
     :en ""}},
   :light-roof?
   {:name      {:fi "Kevytkate", :se "Med lätt tak", :en "Light roof"},
    :data-type "boolean",
    :description
    {:fi "Voidaanko kenttään asentaa kevytkate, kupla tms.",
     :se "Kan man installera ett lätt tak på planen",
     :en ""}},
   :pool-length-mm
   {:name
    {:fi "1. altaan pituus m",
     :se "1a bassängens längd",
     :en "1. pool's length"},
    :data-type "numeric",
    :description
    {:fi "1. altaan/pääaltaan pituus metreinä",
     :se "Första/huvudbassängens längd i meter",
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
     :se "Gymapparater st.",
     :en "Number of exercise machines"},
    :data-type "numeric",
    :description
    {:fi "Kuntoilulaitteiden lukumäärä",
     :se "Hur många gymapparater finns det",
     :en ""}},
   :track-type
   {:name        {:fi "Ratatyyppi", :se "Typ av bana", :en "Type of track"},
    :data-type   "string",
    :description {:fi "Radan tyyppi", :se "", :en ""}},
   :training-spot-surface-material
   {:name
    {:fi "Suorituspaikan pintamateriaali",
     :se "Prestationsplatsens ytmaterial",
     :en "Surface material for training spot"},
    :data-type "string",
    :description
    {:fi "Esim. keihäänheittopaikan pintamateriaali / päällys",
     :se "T.ex. ytmaterialet vid spjutkastningsplatsen",
     :en ""}},
   :range?
   {:name      {:fi "Harjoitusalue/range", :se "Range", :en "Range"},
    :data-type "boolean",
    :description
    {:fi "Onko golfin harjoitusalue/range", :se "", :en ""}},
   :track-length-m
   {:name
    {:fi "Radan pituus m",
     :se "Längd av bana m",
     :en "Length of track m"},
    :data-type "numeric",
    :description
    {:fi "Juoksuradan, pyöräilyradan tms. pituus metreinä",
     :se "Löpbanans, rundbanans el.dyl. längd i meter",
     :en ""}}
   :school-use?
   {:name
    {:fi "Koululiikuntapaikka",
     :se "Idrottsanläggning för skola",
     :en "Sport facility in school use"},
    :data-type "boolean",
    :description
    {:fi "Liikuntapaikka on koulun käytettävissä.",
     :se "",
     :en ""}}
   :free-use?
   {:name
    {:fi "Vapaa käyttö",
     :se "Fritt tillträde",
     :en "Free usage"},
    :data-type "boolean",
    :description {:fi "Liikuntapaikka on kenen tahansa vapaasti
    käytettävissä ilman vuoron varausta tai pääsymaksua.",
     :se "",
     :en ""}}})
