(ns lipas.data.types)

(def all
  [{:name {:fi "Lähipuisto", :se "Närpark", :en "Neighbourhood park"},
  :description
  {:fi
   "Taajamissa, asutuksen välittömässä läheisyydessä. Tarkoitettu jokapäiväiseen käyttöön. Leikki-, oleskelu- ja kävelypaikka. Kaavamerkintä VL.",
   :se
   "I tätorter, i omedelbar närhet till bebyggelse. Avsedd för daglig användning. Plats för lek, vistelse och promenader. Planbeteckning VL.",
   :en
   "In population centres, in or near residential areas. Intended for daily use. Used for play, recreation and walks. Symbol VL."},
  :type-code 101,
  :status "active",
  :geometry-type "Polygon"}
 {:name {:fi "Ulkoilupuisto", :se "Friluftspark", :en "Leisure park"},
  :description
  {:fi
   "Päivittäin käytettäviä alueita, max 1 km asunnoista. Toimivat kävely-, leikki-, oleskelu-, lenkkeily- ja pyöräilypaikkoina. Kevyt liikenne voi mennä ulkoilupuiston läpi. Voi sisältää puistoa, metsää, peltoa, niittyä, vesialuetta. Kaavamerkintä V tai VL.",
   :se
   "Områden för daglig användning. Avstånd från bebyggelse max 1 km. Fungerar som områden för promenader, lekar, vistelse, joggning och cykling. Lättrafikleder kan gå genom en friluftspark. Kan bestå av park, skog, åker, äng och vattenleder. Planbeteckning V eller VL.",
   :en
   "Used daily, max. 1 km from residential areas. Intended for walks, play, recreation, jogging and cycling. There may be bicycle and pedestrian traffic across the park. May consist of park, forest, fields, meadows, bodies of water. Symbol V or VL."},
  :type-code 102,
  :status "active",
  :geometry-type "Polygon"}
 {:name {:fi "Ulkoilualue", :se "Friluftsområde", :en "Outdoor area"},
  :description
  {:fi
   "Taajamien reunoilla tai vyöhykkeittäin taajaman sisällä. 1-10 km päässä asuinalueista. Käytetään esim. kävelyyn, hiihtoon, lenkkeilyyn, uintiin jne. Yleensä yhden kunnan virkistysaluetarpeita palveleva, tarjoaa monipuolisia liikuntamahdollisuuksia. Voi olla metsää, suota, peltoja, luonnonmukaisia alueita ja puistomaisia osia. Kaavamerkintä VR.",
   :se
   "I utkanten av tätorter eller i zoner inom tätorten. Avstånd från bebyggelse 1-10 km. Används till promenader, skidåkning, joggning, simning osv. I regel friluftsaktiviteter för en kommun. En stor variation av motionsmöjligheter. Kan bestå av skog, kärr, åkrar, naturenliga områden och parkliknande delar. Planbeteckning VR.  ",
   :en
   "On the edge of population centres or zoned within population centres. 1-10 km from residential areas. Used for e.g. walks, skiing, jogging, swimming. Serves usually recreational needs within one municipality, offers versatile sports facilities. May include forest, swamp, fields, natural areas and park areas. Symbol VR."},
  :type-code 103,
  :status "active",
  :geometry-type "Polygon"}
 {:name {:fi "Retkeilyalue", :se "Utflyktsområde", :en "Hiking area"},
  :description
  {:fi
   "Sijaitsevat kauempana taajamasta, automatkan päässä. Monipuolinen polku- ja reittiverkosto. Käyttö painottuu viikonloppuihin ja loma-aikoihin. Palvelevat usein useaa kuntaa. Kaavamerkintä VR.",
   :se
   "Längre borta från tätorter, på bilavstånd. Varierande stig- och vägnätverk. Används mest under helger och semestrar. Betjänar ofta flera kommuner.Planbeteckning VR.",
   :en
   "Located further away from population centres, accessible by car. Complex network of paths and routes. Use concentrated during weekends and holidays. Often serves several municipalities. Symbol VR."},
  :type-code 104,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Monikäyttöalue, jolla on virkistyspalveluita",
   :se "Allaktivitetsområde med rekreationstjänster",
   :en "Multipurpose area with recreational services"},
  :description
  {:fi
   "Maa- ja metsätalousalueita, joita käytetään jokamiehenoikeuteen perustuen myös ulkoiluun, voidaan nimittää monikäyttöalueiksi. Monikäyttöalueita ovat erityisesti rakentamattomat rannat ja taajamien läheiset maa- ja metsätalousalueet. Kaavamerkintä MU.",
   :se
   "Lant- och skogsbruksområden som genom allemansrätten också används för friluftsliv kan kallas allaktivitetsområden. Sådana är i synnerhet obebyggda stränder samt lant- och skogsbruksområden nära tätorter. Planbeteckning MU.",
   :en
   "Agricultural and forestry areas that (based on the right of public access) are also used for recreation can be called multipurpose areas. In particular, these include unbuilt shores and agricultural and forestry areas close to population centres. Symbol MU."},
  :type-code 106,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Matkailupalveluiden alue",
   :se "Område med turisttjänster",
   :en "Tourist services area"},
  :description
  {:fi
   "Matkailupalvelujen alueet ovat matkailu- ja lomakeskuksille, lomakylille, lomahotelleille ja muille vastaaville matkailua palveleville toiminnoille varattuja alueita, jotka sisältävät myös sisäiset liikenneväylät ja -alueet, alueen toimintoja varten tarpeelliset palvelut ja virkistysalueet sekä yhdyskuntateknisen huollon alueet. Kaavamerkintä RM.",
   :se
   "Områden med turisttjänster har reserverats för turist- och semestercentra, semesterbyar, semesterhotell och motsvarande aktörer. De har egna trafikleder och -områden samt områden för egna serviceenheter och egen infrastruktur för aktiviteter.  Planbeteckning RM.",
   :en
   "Area reserved for tourism and holiday centres, holiday villages, hotels, etc., also including internal traffic routes and areas; services and recreational areas needed for operations, as well as technical maintenance areas. Symbol RM."},
  :type-code 107,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Virkistysmetsä",
   :se "Friluftsskog",
   :en "Recreational forest"},
  :description
  {:fi
   "Metsähallituksen päätöksellä perustettu virkistysmetsä. Metsätaloudessa huomioidaan mm. maisemalliset arvot. Metsähallitus tietolähde.",
   :se
   "Grundad enligt Forststyrelsens beslut. I skogsbruket tas hänsyn till bl a landskapsvärden. Källa  Forststyrelsen.",
   :en
   "Recreational forest designated by Metsähallitus. E.g. scenic value is considered in forestry. Source of information  Metsähallitus."},
  :type-code 108,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Valtion retkeilyalue",
   :se "Statens friluftsområde",
   :en "National hiking area"},
  :description
  {:fi
   "Ulkoilulailla perustettu, retkeilyä ja luonnon virkistyskäyttöä varten. Metsähallitus tietolähde.",
   :se
   "Grundat enligt lagen om friluftsliv för att användas för friluftsliv och rekreation i naturen. Källa  Forststyrelsen.",
   :en
   "Established based on the Outdoor Recreation Act for hiking and recreational use of nature. Source of information  Metsähallitus."},
  :type-code 109,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Erämaa-alue", :se "Vildmarksområde", :en "Wilderness area"},
  :description
  {:fi
   "Erämaalailla perustetut alueet pohjoisimmassa Lapissa. Metsähallitus tietolähde.",
   :se
   "Grundade enligt ödemarkslagen, i nordligaste Lappland. Källa  Forststyrelsen. ",
   :en
   "Areas located in northernmost Lapland, established based on the Wildeness Act (1991/62). Source of information  Metsähallitus."},
  :type-code 110,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Kansallispuisto", :se "Nationalpark", :en "National park"},
  :description
  {:fi
   "Luonnonsuojelualueita, joiden perustamisesta ja tarkoituksesta on säädetty lailla. Pinta- ala väh. 1000 ha. Metsähallitus tietolähde.",
   :se
   "Naturskyddsområden med lagstadgad status och uppgift. Areal minst 1000 ha. Källa  Forststyrelsen",
   :en
   "Nature conservation areas whose establishment and purpose are based on legislation. Min. area 1,000 ha. Source of information  Metsähallitus."},
  :type-code 111,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Muu luonnonsuojelualue, jolla on virkistyspalveluita",
   :se "Annat naturskyddsområde med rekreationstjänster",
   :en "Other nature conservation area with recreational services"},
  :description
  {:fi
   "Muut luonnonsuojelualueet kuin kansallispuistot ja luonnonpuistot. Vain luonnonsuojelualueet, joilla virkistyskäyttö on mahdollista. Esim. kuntien ja yksityisten maille perustetut suojelualueet. Kaavamerkintä S, SL.",
   :se
   "Andra naturskyddsområden än nationalparker och naturparker. Endast de naturskyddsområden där friluftsanvändning är möjlig. T ex skyddsområden som grundats på kommunal eller privat mark. Planbeteckning S, SL.",
   :en
   "Nature conservation areas other than national parks and natural parks. Only nature conservation areas with opportunities for recreation. E.g. protection areas established on municipal and private land. Symbol S, SL."},
  :type-code 112,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Kalastusalue/-paikka",
   :se "Område eller plats för fiske",
   :en "Fishing area/spot "},
  :description
  {:fi
   "Luonnonvesistössä sijaitseva virkistyskalastusta varten varustettu ja hoidettu kohde.",
   :se
   "Område eller en plats i ett naturligt vattendrag som ställts i ordning för fritidsfiske.",
   :en
   "Natural aquatic destination equipped and maintained for recreational fishing."},
  :type-code 201,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Telttailu ja leiriytyminen",
   :se "Tältning och läger",
   :en "Camping"},
  :description
  {:fi "Telttailualue tai muu leiriytymiseen osoitettu paikka. ",
   :se "Tältplats eller annat område ordnat för tältning.",
   :en "Camping site for tents or other encampment. "},
  :type-code 202,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Veneilyn palvelupaikka",
   :se "Serviceplats för båtfarare",
   :en "Boating services"},
  :description
  {:fi "Veneilyyn liittyviä palveluita. Tarkennettava palvelut.",
   :se "Tjänster för båtfarare. Precisering i karakteristika.",
   :en "Facilities related to boating. Specififed in 'attributes'."},
  :type-code 203,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Luontotorni", :se "Naturtorn", :en "Nature observation tower"},
  :description
  {:fi "Luonnon tarkkailuun tarkoitettu rakennelma. Esim. lintutorni.",
   :se
   "Anordning avsedd för observationer i  naturen, t ex fågeltorn.",
   :en
   "Structure built for nature observation. E.g. bird observation tower."},
  :type-code 204,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Rantautumispaikka",
   :se "Ilandstigningsplats",
   :en "Boat dock"},
  :description
  {:fi "Rantautumiseen osoitettu paikka, ei järjestettyjä palveluita.",
   :se "Plats som anvisats för ilandstigning, inga ordnade tjänster.",
   :en "Place intended for landing by boat, no services provided."},
  :type-code 205,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Ruoanlaittopaikka",
   :se "Matlagningsplats",
   :en "Cooking facilities"},
  :description
  {:fi "Rakennettu tulentekopaikka tai keittokatos.",
   :se "Kokskjul eller ordnad plats för att göra upp eld.",
   :en "Campfire or cooking shelter."},
  :type-code 206,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Opastuspiste", :se "Informationspunkt", :en "Info"},
  :description
  {:fi
   "Opastuspiste ulkoilureitin, virkistysalueen tai muun liikuntapaikan yhteydessä. Esim. opastustaulu ja kartta. Voi olla laajempikin palvelupiste. Usein reitin lähtöpiste.",
   :se
   "En informationspunkt vid ett friluftsled eller annan idrottsplats. Kan innehålla t.ex. informationstavla och karta. Kan vara en större servicepunkt. Oftast i början av rutten.",
   :en
   "Information point or starting point of a route, recreation area etc. Map of the route or area included, possibly also parking area."},
  :type-code 207,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Laavu, kota tai kammi",
   :se "Vindskydd eller kåta",
   :en "Lean-to, goahti (Lapp tent shelter) or 'kammi' earth lodge"},
  :description
  {:fi "Päiväsaikainen levähdyspaikka retkeilijöille.",
   :se "Viloplats för vandrare under dagtid.",
   :en "Daytime rest stop for hikers."},
  :type-code 301,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Tupa", :se "Stuga", :en "Hut"},
  :description
  {:fi
   "Autiotupa, varaustupa, taukotupa, päivätupa. Yöpymis- ja levähdyspaikka retkeilijöille. Autiotupa avoin, varaustupa lukittu ja maksullinen. Päivätupa päiväkäyttöön.",
   :se
   "Ödestuga, reserveringsstuga, raststuga, dagstuga. Övernattnings- och rastplats för vandrare. Ödestugan öppen, reserveringsstugan låst och avgiftsbelagd. Dagstuga för bruk under dagen.",
   :en
   "Open hut, reservable hut, rest hut, day hut. Overnight resting place for hikers. An open hut is freely available; a reservable hut locked and subject to a charge. A day hut is for daytime use."},
  :type-code 302,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Ulkoilumaja/hiihtomaja",
   :se "Friluftsstuga/skidstuga",
   :en "Outdoor/ski lodge "},
  :description
  {:fi "Tavallisen arkiliikunnan taukopaikka, päiväkäyttöön.",
   :se "Rastplats för bruk under dagen, vardagsmotion.",
   :en "Rest area for regular daily sports, for daytime use."},
  :type-code 304,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Liikuntapuisto", :se "Idrottspark", :en "Sports park"},
  :description
  {:fi
   "Liikuntapuisto on useita liikuntapaikkoja käsittävä liikunta- alue. Liikuntapuistossa voi olla esim. erilaisia kenttiä, uimaranta, kuntorata, monitoimihalli, leikkipuisto jne. koottuna samalle alueelle. Lipakseen tallennetaan sekä tieto liikuntapuistosta että yksittäiset liikuntapaikat joita puisto sisältää. Liikuntapaikat lasketaan omiksi paikoikseen. Haku on mahdollista sekä puiston että yksittäisen paikan kautta. ",
   :se
   "En idrottspark är ett idrottsområde med flera idrottsplatser. Där kan finnas olika planer, badstrand, konditionsbana, allaktivitetshall, lekpark osv samlade på samma område. I Lipas lagras uppgifter om såväl idrottsparken som enstaka faciliteter som finns i parken. Varje motionsplats räknas som en plats. Sökning kan ske både via parken eller enstaka faciliteter.",
   :en
   "A sports park is an area including several sports facilities, e.g., different fields, beach, a jogging track, a multi-purpose hall, a playground. 'Lipas' contains information both on the sports park and the individual sports facilities found there. The sports facilities are listed as individual items in the classification, enabling search by the park or by the individual facility."},
  :type-code 1110,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Lähiliikuntapaikka",
   :se "Näridrottsplats",
   :en "Neighbourhood sports area"},
  :description
  {:fi
   "Lähiliikuntapaikka on tarkoitettu päivittäiseen ulkoiluun ja liikuntaan. Se sijaitsee asutuksen välittömässä läheisyydessä, on pienimuotoinen ja alueelle on vapaa pääsy. Yleensä tarjolla on erilaisia suorituspaikkoja. Lisätietoihin jos on koulun piha, päiväkodin piha,  suorituspaikat jne.",
   :se
   "Näridrottsplats avsedd för daglig motion och dagligt friluftsliv. Den ligger i omedelbar närhet till bebyggelse, är relativt liten och tillträdet är fritt. I regel erbjuds olika faciliteter för enstaka aktiviteter. I tilläggsinformation anges om det är en skolgård eller daghemsgård samt vilka enskilda faciliteter som finns.",
   :en
   "A neighbourhood sports area is intended for daily outdoor activities and exercise. It is a small area located in or near a residential area, with free public access. It usually provides different exercise/play facilities. It is specified in 'additional information' whether it is a school/nursery school yard, what facilities/equipment are offered, etc."},
  :type-code 1120,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Ulkokuntoilupaikka",
   :se "Konditionsplats för utomhusaktiviteter",
   :en "Fitness training place"},
  :description
  {:fi
   "Kuntoilulaitteita, voimailulaitteita yms. ulkokuntosali. Voi olla osa liikuntapuistoa, tai liikuntareitin varrella olevia kuntoilupisteitä.",
   :se
   "Konditions- och styrketräningsanordningar osv, en \"utomhusträningssal\". Kan vara en del av en idrottspark eller konditionsplats vid en motionsbana.",
   :en
   "Contains fitness and gym equipment, etc., \"outdoor gym\". May be part of a sports park."},
  :type-code 1130,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Parkour-alue", :se "Parkourområde", :en "Parkour area"},
  :description
  {:fi "Parkouria varten varustettu alue.",
   :se "Område utrustat för parkour.",
   :en "An area equipped for parkour."},
  :type-code 1140,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Skeitti-/rullaluistelupaikka",
   :se "Plats för skejtning/rullskridskoåkning",
   :en "Skateboarding/roller-blading rink "},
  :description
  {:fi
   "Rullaluistelua, skeittausta, potkulautailua varten varustettu paikka. Ominaisuustietoihin kirjataan, mitä saa harrastaa.",
   :se
   "Plats utrustad för rullskridskoåkning, skejtning och sparkcykelåkning. I preciseringsuppgifterna anges vad som är tillåtet på området.",
   :en
   "An area equipped  for roller-blading, skateboarding, kick scooting. Hobby  specified in 'attribute data'."},
  :type-code 1150,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Pyöräilyalue", :se "Cykelområde", :en "Cycling area"},
  :description
  {:fi
   "Pyöräilyä ja temppuilua varten, esim. bmx- tai dirt- pyöräilyä varten.",
   :se "Avsett för cykelåkning och trick, t ex bmx- eller dirtåkning.",
   :en "For cycling and stunting, e.g. BMX or dirt-biking."},
  :type-code 1160,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Pyöräilyrata", :se "Cykelbana", :en "Velodrome"},
  :description
  {:fi "Ratapyöräilyä varten,ulkona (velodromi).",
   :se "Utomhus, velodrom.",
   :en "For track racing outdoors (velodrome)."},
  :type-code 1170,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Frisbeegolfrata",
   :se "Frisbeegolfbana",
   :en "Disc golf course"},
  :description
  {:fi "Frisbeegolfin pelaamiseen rakennettu rata. ",
   :se "Frisbeegolfbanor finns i idrotts- och friluftsleder.",
   :en "Track built for disc golf. "},
  :type-code 1180,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Yleisurheilun harjoitusalue",
   :se "Träningsområde för friidrott",
   :en "Athletics training area"},
  :description
  {:fi
   "Suorituspaikat ominaisuustietoihin. Myös yksittäiset suorituspaikat.",
   :se
   "Platserna för olika aktiviteter anges i preciseringsuppgifter. Även enstaka aktiviteter.",
   :en
   "The various sports venues are specified in 'attribute data', also individual venues."},
  :type-code 1210,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Yleisurheilukenttä",
   :se "Friidrottsplan",
   :en "Athletics field"},
  :description
  {:fi
   "Kenttä, ratoja ja yleisurheilun suorituspaikkoja. Keskusta, radat, päällysteet, suorituspaikat ominaisuuksiin.",
   :se
   "En plan, banor och träningsplatser för friidrott. Centrum, banor, ytbeläggningar samt träningsplatser med beskrivningar.",
   :en
   "Field, track and athletic venues/facilities. Centre, tracks, surfaces, venues specified in 'attribute data'. "},
  :type-code 1220,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Koripallokenttä", :se "Basketplan", :en "Basketball court"},
  :description
  {:fi
   "Koripalloon varustettu kenttä, kiinteät tai siirrettävät telineet.  Minikenttä ja yhden korin kenttä lisätiedoissa. ",
   :se
   "Plan utrustad för basket med fasta eller flyttbara ställningar. Miniplan och enkorgsplan i tilläggsupgifter.",
   :en
   "A field equipped for basketball, with fixed or movable apparatus. 'Mini-court' and 'one-basket court'  included in additional information. "},
  :type-code 1310,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Lentopallokenttä",
   :se "Volleybollplan",
   :en "Volleyball court"},
  :description
  {:fi "Lentopalloon varustettu kenttä. Kiinteät lentopallotelineet.",
   :se "Plan utrustad för volleyboll. Fasta volleybollställningar.",
   :en "A field equipped for volleyball. Fixed volleyball apparatus."},
  :type-code 1320,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Beachvolleykenttä",
   :se "Beachvolleyplan",
   :en "Beach volleyball court"},
  :description
  {:fi
   "Rantalentopallokenttä, pehmeä alusta. Voi sijaita muuallakin kuin rannalla.",
   :se
   "Beachvolleybollplan, mjukt underlag. Kan också ha annat läge än stranden.",
   :en
   "Beach volleyball court, soft basement. May also be located far from a beach."},
  :type-code 1330,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Pallokenttä", :se "Bollplan", :en "Ball field"},
  :description
  {:fi
   "Palloiluun tarkoitettu kenttä. Hiekka, nurmi, hiekkatekonurmi tms, koko vaihtelee. Yksi tai useampi palloilulaji mahdollista. Kevyt poistettava kate mahdollinen.",
   :se
   "Plan avsedd för bollspel. Sand, gräs, konstgräs med sand el. dyl., storleken varierar. En eller flera bollspelsgrenar möjliga.",
   :en
   "A field intended for ball games. Sand, grass, artificial turf, etc., size varies. One or more types of ball games possible."},
  :type-code 1340,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Jalkapallostadion",
   :se "Fotbollsstadion",
   :en "Football stadium"},
  :description
  {:fi
   "Suuri jalkapallokenttä, katsomoita. Vähintään kansallisen tason pelipaikka.",
   :se
   "Stor fotbollsplan, flera läktare. Minimikrav  spelplats på nationell nivå.",
   :en
   "Large football field, stands. Can host at least national-level games."},
  :type-code 1350,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Pesäpallokenttä", :se "Bobollsplan", :en "Baseball field"},
  :description
  {:fi
   "Pesäpallokenttä, nimeä stadioniksi jos on katsomoita. Vähintään kansallisen tason pelipaikka. Hiekka, hiekkatekonurmi / muu synteettinen päällyste, > 50 x 100 m. ",
   :se
   "En bobollsplan, kan ha flera läktare. Minimikrav  spelplats på nationell nivå. Sand, konstgräs med sand / annan syntetisk beläggning. >50 x 100 m.",
   :en
   "Finnish baseball field, may include stands. Can host at least national-level games. Sand, artificial turf / other synthetic surface, > 50 x 100 m. "},
  :type-code 1360,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Tenniskenttäalue",
   :se "Område med tennisbanor",
   :en "Tennis court area"},
  :description
  {:fi
   "Tenniskenttä tai useampi kenttä samalla alueella. Montako kenttää, pintamateriaali yms. ominaisuustietoihin. Myös tieto lyöntiseinästä.",
   :se
   "En eller flera tennisbanor på samma område. Antalet banor, ytmaterial mm i karakteristika. Även uppgift om slagväggen.",
   :en
   "One or more tennis courts in the same area. Number of courts, surface material, etc. specified in 'attribute data', including information about a potential hit wall."},
  :type-code 1370,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Rullakiekkokenttä",
   :se "Inlinehockeyplan",
   :en "Roller hockey field"},
  :description
  {:fi "Rullakiekon pelaamiseen varustettu kenttä.",
   :se "Plan utrustad för inlinehockey.",
   :en "Field equipped for roller hockey."},
  :type-code 1380,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Tekojääkenttä",
   :se "Konstisplan",
   :en "Mechanically frozen open-air ice rink"},
  :description
  {:fi
   "Koneellisesti / keinotekoisesti jäähdytetty ulkokenttä. Kentän koko, varustus, kaukalo, valaistus yms. Ominaisuustietoihin. Tieto yhdistelmäkentästä mukaan.",
   :se
   "Konstfrusen utomhusplan. Storlek, utrustning, rink, belysning osv i karakteristika. Likaså uppgiften om planen är kombinerad.",
   :en
   "Mechanically/artificially frozen open-air field. Field size, equipment, rink, lighting, etc. specified in 'attribute data', as well as information about multi-use fields."},
  :type-code 1510,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Luistelukenttä", :se "Skridskobana", :en "Ice-skating field"},
  :description
  {:fi
   "Luisteluun tarkoitettu kenttä. Kesäkäyttö ominaisuustietoihin.",
   :se
   "Plan avsedd för skridskoåkning. Sommaranvändning i karakteristika.",
   :en
   "Field intended for ice-skating. Summer use specified in 'attribute data'."},
  :type-code 1520,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Kaukalo", :se "Rink", :en "Rink"},
  :description
  {:fi
   "Luisteluun, jääkiekkoon, kaukalopalloon jne. tarkoitettu kaukalo. Mahdollinen kesäkäyttö ominaisuustietoihin.",
   :se
   "Rink avsedd för skridskoåkning, ishockey, rinkbandy osv. Eventuell sommaranvändning i karakteristika.",
   :en
   "Rink intended for ice-skating, ice hockey, rink bandy, etc. Potential summer use specified in 'attribute data'."},
  :type-code 1530,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Pikaluistelurata",
   :se "Bana för hastighetsåkning på skridskor",
   :en "Speed-skating track"},
  :description
  {:fi "Radan koko ja pituus ominaisuustietoihin.",
   :se "Storlek och längd på banan i karakteristika.",
   :en "Track size and length specified in 'attribute data'."},
  :type-code 1540,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Luistelureitti", :se "Skridskoled", :en "Ice-skating route"},
  :description
  {:fi
   "Rakennetaan talvisin samalle alueelle esim. liikuntapuiston alueelle tai meren jäälle.",
   :se
   "Byggs varje vinter på samma område t ex i en idrottspark eller på havsis.",
   :en
   "Built yearly in the same area, e.g., in a sports park or on frozen lake/sea. Same as 'ice-skating route' in 'outdoor recreation routes'."},
  :type-code 1550,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Alamäkiluistelurata",
   :se "Skridskobana för utförsåkning",
   :en "Downhill skating track"},
  :description
  {:fi "Alamäkiluistelua varten rakennettu pysyvä rata. ",
   :se "Permanent bana byggd för utförsåkning.",
   :en "Permanent track built for downhill skating. "},
  :type-code 1560,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Golfin harjoitusalue",
   :se "Träningsområde för golf",
   :en "Golf training area"},
  :description
  {:fi
   "Golfin harjoittelua varten, yksi tai useampia suorituspaikkoja. Ulkona.",
   :se
   "Träningsplats för golf, en eller flera övningsplatser. Utomhus.",
   :en "One or more areas for golf training. Outdoors."},
  :type-code 1610,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Golfkenttä", :se "Golfbana", :en "Golf course"},
  :description
  {:fi
   "Virallinen golfkenttä. Reikien määrä kysytään ominaisuustiedoissa.",
   :se "Officiell golfbana. Antalet hål anges i karakteristika.",
   :en
   "Official golf course. Number of holes included in 'attribute data'."},
  :type-code 1620,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Golfin harjoitushalli",
   :se "Övningshall för golf",
   :en "Golf training hall"},
  :description
  {:fi
   "Golfia varten rakennettu harjoittelutila. Koko ominaisuustietoihin. Linkki sisäliikuntatiloihin!",
   :se
   "Övningsutrymme byggt för golf. Storlek i karakteristika. Länk till Anläggningar för inomhusidrott.",
   :en
   "Training space built for golf. Size specified in 'attribute data'. Link to 'indoor sport facilities'"},
  :type-code 1630,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Ratagolf", :se "Bangolf", :en "Minigolf course"},
  :description
  {:fi "Ratagolf- liiton hyväksymät ratagolf /minigolf- radat.",
   :se "Bangolf / minigolf, enligt Finlands Bangolfförbundets regler.",
   :en
   "A course built for miniature golf, accepted by the Ratagolf Union"},
  :type-code 1640,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Kuntokeskus", :se "Motionscenter", :en "Fitness centre"},
  :description
  {:fi
   "Erilaisia liikuntapalveluita ja tiloja, esim. kuntosali, ryhmäliikuntatiloja jne. ",
   :se
   "Olika motionstjänster och -utrymmen, t ex gym och gruppidrottsutrymmen.",
   :en
   "Different sports services and premises, e.g., gym, group exercise premises. "},
  :type-code 2110,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Kuntosali", :se "Gym", :en "Gym"},
  :description
  {:fi "Kuntosalilaitteita yms. Koko määritellään ominaisuustiedoissa",
   :se "Gymredskap osv. Storleken anges i karakteristika.",
   :en "Gym equipment, etc. Size specified in 'attribute data'."},
  :type-code 2120,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Voimailusali",
   :se "Kraftsportsal",
   :en "Weight training hall "},
  :description
  {:fi
   "Painonnostoon ja nyrkkeilyyn varustettu. Koko määritellään ominaisuustiedoissa.",
   :se
   "Utrustad för tyngdlyftning och boxning. Storleken anges i karakteristika.",
   :en
   "Equipped for weightlifting and boxing. Size specified in 'attribute data'."},
  :type-code 2130,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Kamppailulajien sali",
   :se "Sal för kampsport",
   :en "Martial arts hall"},
  :description
  {:fi
   "Sali jossa voi harrastaa itsepuolustuslajeja  esim. paini, nyrkkeily ym. Koko ominaisuustietoihin. ",
   :se
   "Sal där man kan utöva självförsvarsgrenar, t ex brottning, boxning. Storleken anges i karakteristika.",
   :en
   "Hall for self-defence sports, e.g., wrestling, boxing. Size specified in 'attribute data'. "},
  :type-code 2140,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Liikuntasali", :se "Idrottssal", :en "Gymnastics hall"},
  :description
  {:fi
   "Muun rakennuksen yhteydessä oleva liikuntatila. Koko ja korkeus lisätiedoissa. Jos on koulun yhteydessä, sekin lisätietoihin.",
   :se
   "En idrottssal som är ansluten till en annan byggnad. Storlek och höjd anges i karakteristika. Precisera till tilläggsuppgifter, ifall ansluten vid en skola.",
   :en
   "A gymnastics hall connected to another building. Size and height specified in 'additional information'. Also specified in 'additional information' if within a school."},
  :type-code 2150,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Liikuntahalli", :se "Idrottshall", :en "Sports hall "},
  :description
  {:fi
   "Useiden lajien liikuntatiloja sisältävä rakennus. Koko ja suorituspaikat ominaisuustietoihin.",
   :se
   "Byggnad med utrymmen för flera idrottsgrenar. Storlek och övningsfaciliteter i karakteristika.",
   :en
   "Building containing facilities for various sports. Size and facilities specified in 'attribute data'."},
  :type-code 2210,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Monitoimihalli/areena",
   :se "Allaktivitetshall/multiarena",
   :en "Multipurpose hall/arena"},
  :description
  {:fi "Merkittävä monien lajien kilpailupaikka, >=5000 m2.",
   :se "Större tävlingsplats för ett flertal grenar, >= 5 000 m2.",
   :en "Significant competition venue for various sports, >=5000 m2."},
  :type-code 2220,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Jalkapallohalli", :se "Fotbollshall", :en "Football hall"},
  :description
  {:fi
   "Jalkapalloiluun tarkoitettu halli. Pintamateriaali, kenttien määrä ja koko ominaisuustietoihin, samoin hallin rakenne.",
   :se
   "Hall avsedd för fotboll. Ytmaterial, antalet planer, storlek och byggnadens konstruktion i karakteristika.",
   :en
   "Hall intended for football. Surface material, number and size of courts specified in 'attribute data', as well as hall structure."},
  :type-code 2230,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Salibandyhalli", :se "Innebandyhall", :en "Floorball hall"},
  :description
  {:fi
   "Ensisijaisesti salibandyyn tarkoitettu halli. Kenttien määrä ja pintamateriaali ominaisuustietoihin.",
   :se
   "Hall i första hand avsedd för innebandy. Antalet planer samt ytmaterial i karakteristika.",
   :en
   "Hall primarily intended for floorball. Number of courts and surface material specified in 'attribute data'."},
  :type-code 2240,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Skeittihalli", :se "Skateboardhall", :en "Indoor skatepark"},
  :description
  {:fi
   "Skeittausta, rullaluistelua, bmx- pyöräilyä yms. varten varustettu halli.",
   :se
   "Hall utrustad för skejtning, rullskridskoåkning, bmx-åkning osv, finns i Idrottshallar.",
   :en
   "An area for skateboarding, roller-blading, BMX biking, etc,. found in sports halls."},
  :type-code 2250,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Sulkapallohalli", :se "Badmintonhall", :en "Badminton hall"},
  :description
  {:fi "Ensisijaisesti sulkapalloon tarkoitettu halli",
   :se "Hall i första hand  avsedd för badminton.",
   :en "Hall intended primarily for badminton."},
  :type-code 2260,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Squash-halli", :se "Squashhall", :en "Squash hall"},
  :description
  {:fi
   "Squash-kenttiä yksi tai useampi. Kenttien määrä ominaisuustietoihin",
   :se "En eller flera squashplaner. Antalet planer i karakteristika.",
   :en
   "One or more squash courts. Number of courts specified in 'attribute data'."},
  :type-code 2270,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Tennishalli", :se "Tennishall", :en "Tennis hall"},
  :description
  {:fi "Kenttien määrä ominaisuuksiin.",
   :se "Antalet banor i karakteristika.",
   :en "Number of courts specified in 'attribute data'."},
  :type-code 2280,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Petanque-halli", :se "Petanquehall ", :en "Petanque Hall"},
  :description
  {:fi
   "Petanque-peliin tarkoitettu halli. Koko, kenttien määrä ja varustus ominaisuustietoihin.",
   :se
   "Hall avsedd för petanque. Storlek, antalet planer och utrustning i karakteristika.",
   :en "Hall intended for petanque"},
  :type-code 2290,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Yksittäinen yleisurheilun suorituspaikka",
   :se "Enstaka övningsplats för friidrott",
   :en "Stand-alone athletics venue"},
  :description
  {:fi
   "Yksittäinen, ei yu-hallin yhteydessä. Ominaisuustiedoissa kerrotaan suorituspaikat (alla esimerkit).",
   :se
   "Fristående, ej i anslutning till en friidrottshall. I karakteristika anges övningsplatserna (exemplifieras nedan).",
   :en
   "Stand-alone, not in an athletics hall. Venues specified under 'attribute data' (examples below)."},
  :type-code 2310,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Telinevoimistelutila",
   :se "Utrymme för redskapsgymnastik",
   :en "Artistic gymnastics facility"},
  :description
  {:fi "Pysyvästi telinevoimisteluun varustettu tila.",
   :se "Permanent utrustning för att träna redskapsgymnastik.",
   :en "Space permanently equipped for artistic gymnastics."},
  :type-code 2320,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Pöytätennistila",
   :se "Utrymme för bordtennis",
   :en "Table tennis venue"},
  :description
  {:fi "Pysyvästi pöytätennikseen varustettu tila.",
   :se "Permanent utrustning för att träna bordtennis.",
   :en "Space permanently equipped for table tennis."},
  :type-code 2330,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Miekkailutila",
   :se "Utrymme för fäktning",
   :en "Fencing venue"},
  :description
  {:fi "Pysyvästi miekkailuun varustettu tila.",
   :se "Permanent utrustning för fäktning.",
   :en "Space permanently equipped for fencing."},
  :type-code 2340,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Tanssitila", :se "Utrymme för dans", :en "Dance studio"},
  :description
  {:fi "Pysyvästi tanssi- tai ilmaisuliikuntaan varustettu tila.",
   :se "Permanent utrustning för dans och kreativ motion.",
   :en
   "Space permanently equipped for dance or expressive movement exercise (tanssi- tai ilmaisuliikunta)."},
  :type-code 2350,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Sisäampumarata",
   :se "Inomhusskjutbana",
   :en "Indoor shooting range"},
  :description
  {:fi
   "Pysyvästi käytössä oleva ampumarata sisätiloissa. Huom. Linkki ampumaurheilupaikkoihin.",
   :se
   "Permanent skjutbana inomhus. Obs. Länk till sportskytteplatser.",
   :en
   "Permanent indoor shooting range. Note  Link to 'shooting sports facilities'."},
  :type-code 2360,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Sisäkiipeilyseinä",
   :se "Klättervägg inomhus",
   :en "Indoor climbing wall"},
  :description
  {:fi "Kiipeilyyn varustettu sisätila. Myös boulderointipaikat. ",
   :se
   "Inomhusutrymme utrustat för klättring, också platser för bouldering.",
   :en "Indoor space equipped for climbing. Also bouldering venues."},
  :type-code 2370,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Parkour-sali", :se "Parkoursal", :en "Parkour hall"},
  :description
  {:fi "Parkouria varten varustettu sisätila. ",
   :se "Inomhusutrymme utrustat för parkour.",
   :en "Indoor space equipped for parkour. "},
  :type-code 2380,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Harjoitusjäähalli",
   :se "Övningsishall",
   :en "Training ice arena"},
  :description
  {:fi
   "Kenttien määrä, lämmitys, pukukopit yms. ominaisuustietoihin. ",
   :se
   "Antalet planer och omklädningsrum, uppvärmning osv anges i karakteristika.",
   :en
   "Number of fields, heating, changing rooms, etc., specified in 'attribute data'. "},
  :type-code 2510,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Kilpajäähalli",
   :se "Tävlingsishall",
   :en "Competition ice arena"},
  :additional-type
  {:small       {:fi "Pieni kilpahalli > 500 hlö"
                 :en nil
                 :se nil}
   :competition {:fi "Kilpahalli < 3000 hlö"
                 :en nil
                 :se nil}
   :large       {:fi "Suurhalli > 3000 hlö"
                 :en nil
                 :se nil}}
  :description
  {:fi
   "Katsomo on, katsomon koko määritellään ominaisuustiedoissa. Kenttien määrät yms. ominaisuuksiin.",
   :se
   "Läktare finns, storleken på läktaren anges i karakteristika, likaså antalet planer.",
   :en
   "Includes bleachers, whose size is specified in 'attribute data'. Number of fields, etc., specified in 'attribute data'."},
  :type-code 2520,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Pikaluisteluhalli",
   :se "Skridskohall",
   :en "Speed-skating hall"},
  :description
  {:fi "Pikaluisteluun tarkoitettu halli. Koko > 333,3 m.",
   :se
   "Hall avsedd för hastighetsåkning på skridsko. Storlek > 333,3 m.",
   :en "Hall intended for speed-skating. Size > 333.3 m."},
  :type-code 2530,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Keilahalli", :se "Bowlinghall", :en "Bowling alley"},
  :description
  {:fi
   "Keilaratoja. Ratojen määrä ja palveluvarustus ominaisuustietoihin.",
   :se "Antalet banor och serviceutrustning i karakteristika.",
   :en "Number of alleys and service facilities in 'attribute data'."},
  :type-code 2610,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Uimahalli", :se "Simhall", :en "Public indoor swimming pool"},
  :description
  {:fi
   "Halli, jossa on yksi tai useampia uima-altaita. Altaiden määrä ja vesipinta-ala kysytään ominaisuustiedoissa.",
   :se
   "Hall med en eller flera simbassänger. Antalet bassänger och vattenareal anges i karakteristika.",
   :en
   "Hall with one or several swimming pools. Number of pools and water surface area is requested in 'attribute data'."},
  :type-code 3110,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Uima-allas", :se "Simbassäng", :en "Swimming pool"},
  :description
  {:fi
   "Yksittäinen uima-allas, usein muun rakennuksen yhteydessä. Ei uimahallissa. <17 m.",
   :se
   "Enstaka simbassäng, ofta i anslutning till en annan byggnad. <17 m.",
   :en
   "Individual swimming pool, often in connection with other buildings. <17 m long."},
  :type-code 3120,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Kylpylä", :se "Badinrättning", :en "Spa"},
  :description
  {:fi
   "Monipuolinen uimala kuntoutus-, virkistys- tai hyvinvointipalveluilla. Vesipinta-ala ja allasmäärät/-tyypit ominaisuuksiin.",
   :se
   "Mångsidig badinrättning med rehabiliterings- och rekreationstjänster. Vattenareal samt antal och typ av bassänger i karakteristika.",
   :en
   "Versatile spa with rehabilitation or wellness services. Water volume and number/types of pools specified in 'attribute data'."},
  :type-code 3130,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Maauimala", :se "Utebassäng", :en "Open-air pool "},
  :description
  {:fi "Vedenpuhdistusjärjestelmä.",
   :se "Vattenreningssystem.",
   :en "Water treatment system."},
  :type-code 3210,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Uimaranta", :se "Badstrand", :en "Supervised beach"},
  :description
  {:fi
   "Aukioloaikoina valvottu, pukukopit yms. varustelua. Talviuinti omana liikuntapaikkanaan.",
   :se
   "Bevakad under öppethållandetider, omklädningshytter o. dyl. utrustning. Vinterbad som egen idrottsplats.",
   :en
   "Supervised during opening hours; includes changing rooms and other facilities. "},
  :type-code 3220,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Uimapaikka", :se "Badplats", :en "Unsupervised beach "},
  :description
  {:fi
   "Uimapaikka, jossa esim. pukukopit. Ei valvontaa. Talviuinti tallennetaan omana liikuntapaikkana.",
   :se
   "Badplats med t ex omklädningshytter men ej övervakning. Vinterbad sparas som egen idrottsplats.",
   :en "Beach with e.g. changing rooms. No lifeguards. "},
  :type-code 3230,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Talviuintipaikka",
   :se "Vinterbadplats",
   :en "Winter swimming area"},
  :description
  {:fi "Vain talviuintipaikka, mahdollisesti avanto.",
   :se "Endast vinterbadplats, eventuellt vak.",
   :en "Winter swimming area only, possibly a hole in the ice. "},
  :type-code 3240,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Laskettelun suorituspaikka",
   :se "Slalombackar och alpina skidcentra",
   :en "Ski slopes and downhill ski resorts"},
  :description
  {:fi "Laskettelurinteet, lumikourut ym. rinnerakenteet.",
   :se
   "Slalombacke, rodelbana, pipe, puckelpist, freestyle ramp, trickbana.",
   :en "-"},
  :type-code 4110,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Curlingrata", :se "Curlingbana", :en "Curling sheet"},
  :description
  {:fi "Pysyvästi lajiin varustettu, katettu curlingrata.",
   :se "Curlingbana med tak och permanent utrustning för grenen.",
   :en "Covered track permanently equipped for curling."},
  :type-code 4210,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Hiihtotunneli", :se "Skidtunnel", :en "Ski tunnel"},
  :description
  {:fi
   "Hiihtoon tarkoitettu katettu tila (tunneli, putki, halli tms.).",
   :se
   "Utrymme avsett för skidåkning under tak (tunnel, rör, hall el dyl).",
   :en
   "Covered space (tunnel, tube, hall, etc.) intended for skiing."},
  :type-code 4220,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Lumilautatunneli",
   :se "Snowboardtunnel",
   :en "Snowboarding tunnel"},
  :description
  {:fi
   "Lautailuun tarkoitettu tunneli. Lisätietoihin eri käyttömuodot.",
   :se
   "Tunnel avsedd för snowboardåkning. Olika användningsmöjligheter i tilläggsinformation.",
   :en
   "Tunnel intended for snowboarding. Different uses specified in additional information."},
  :type-code 4230,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Lasketteluhalli",
   :se "Slalomhall",
   :en "Downhill skiing hall"},
  :description
  {:fi "Katettu laskettelurinne. Korkeusero ja pituus ominaisuuksiin.",
   :se "Slalombacke med tak. Höjdskillnad och längd i karakteristika.",
   :en
   "Covered ski slope. Height and length specified in attributes."},
  :type-code 4240,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Harjoitushyppyrimäki",
   :se "Träningshoppbacke",
   :en "Ski jumping hill for training"},
  :description
  {:fi
   "K-piste, materiaalit, kesä- ja talvikäyttö ominaisuuksiin. Mäkihypyn harjoitteluun tarkoitettu.",
   :se
   "K-punkt, material samt sommar- och vinteranvändning i karakteristika.",
   :en
   "K point in 'attribute data'; materials, summer and winter use specified in attributes. "},
  :type-code 4310,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Hyppyrimäki", :se "Hoppbacke", :en "Ski jumping hill"},
  :description
  {:fi
   "Jää-, keramiikka- tai muovilatu. Kesä- ja talvikäyttö ominaisuuksiin, K-piste ym. Vähintään pienmäki, K-piste min 75 m.",
   :se
   "Is-, keramik- eller plastspår. K-punkt samt sommar- och vinteranvändning i karakteristika. Minimikrav  en liten backe med K-punkt på 75 m eller mera.",
   :en
   "Ice, ceramic or plastic track. Summer and winter use specified in attributes, along with K point, etc. Minimum normal hill, K point minimum 75 m."},
  :type-code 4320,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Kuntorata", :se "Konditionsbana", :en "Jogging track"},
  :description
  {:fi
   "Talvi- ja kesäkäyttötavat kerrotaan ominaisuustiedoissa. Kuntoiluun tarkoitettu reitti asutuksen läheisyydessä.",
   :se
   "Led avsedd för konditionssport i närheten av bebyggelse. Vinter- och sommaranvändning beskrivs i karakteristika. ",
   :en
   "Winter and summer uses specified in 'attribute data'. Route intended for jogging in or near a residential area."},
  :type-code 4401,
  :status "active",
  :geometry-type "LineString"}
 {:name {:fi "Latu", :se "Skidspår", :en "Ski track"},
  :description
  {:fi
   "Hiihtoon tarkoitettu reitti. Ei kesäkäyttöä/hoitoa. Hiihtotyylit kerrotaan ominaisuustiedoissa.",
   :se
   "Led avsedd för skidåkning. Ej sommaranvändning och -underhåll. Åkstilar anges i karakteristika.",
   :en
   "Route intended for skiing. Not in use and unmaintained in summer. Ski styles provided in 'attribute data'."},
  :type-code 4402,
  :status "active",
  :geometry-type "LineString"}
 {:name
  {:fi "Kävelyreitti/ulkoilureitti",
   :se "Promenadled/friluftsled",
   :en "Walking route/outdoor route"},
  :description
  {:fi
   "Jalkaisin tapahtuvaan ulkoiluun tarkoitettu reitti. Suhteellisen leveä ja helppokulkuinen. Mahdollisesti valaistu ja pinnoitettu reitti.",
   :se
   "Promenadled. Relativt bred och lättilgänglig, eventuellt belyst och asfalterad.",
   :en
   "Route intended for outdoor pedestrian activities. Relatively broad and passable. Potentially lit and surfaced."},
  :type-code 4403,
  :status "active",
  :geometry-type "LineString"}
 {:name {:fi "Luontopolku", :se "Naturstig", :en "Nature trail"},
  :description
  {:fi
   "Erityisesti luontoharrastusta varten, reitin varrella opasteita tai infotauluja.",
   :se
   "I synnerhet för naturintresse, info- och orienteringstavlor längs leden.",
   :en
   "Intended particularly for nature activities; signposts or info boards along the route."},
  :type-code 4404,
  :status "active",
  :geometry-type "LineString"}
 {:name {:fi "Retkeilyreitti", :se "Utflyktsled", :en "Hiking route"},
  :description
  {:fi
   "Maastossa oleva retkeilyreitti, yleensä kauempana asutuksesta. Reitin varrella retkeilyn palveluita, esim. laavuja. Kesä- ja talvikäyttö ominaisuustietoihin.",
   :se
   "Utflyktsled i terrängen, oftast längre borta från bebyggelse. Längs rutten friluftstjänster, t ex vindskydd. Sommar- och vinteranvändning i karakteristika.",
   :en
   "Natural hiking route, usually further away from residential areas. Provides hiking facilities, e.g. lean-to structures. Summer and winter use specified in 'attribute data'."},
  :type-code 4405,
  :status "active",
  :geometry-type "LineString"}
 {:name
  {:fi "Maastopyöräilyreitti",
   :se "Cykelled för terrängcykling",
   :en "Cross-country biking route"},
  :description
  {:fi "Erityisesti maastopyöräilyyn tarkoitettu reitti, merkitty.",
   :se "Led avsedd framför allt för terrängcykling, märkt.",
   :en "Marked route intended especially for cross-country biking."},
  :type-code 4411,
  :status "active",
  :geometry-type "LineString"}
 {:name {:fi "Pyöräilyreitti", :se "Cykelled", :en "Biking route"},
  :description
  {:fi "Pyöräilyreitti, ei maastopyöräilyyn.",
   :se "Cykelled, ej för terrängcykling.",
   :en "Biking route, not intended for cross-country biking."},
  :type-code 4412,
  :status "active",
  :geometry-type "LineString"}
 {:name
  {:fi "Moottorikelkkareitti",
   :se "Snöskoterled",
   :en "Official snowmobile route"},
  :description
  {:fi "Reittitoimituksella hyväksytty, virallinen reitti.",
   :se "En officiell rutt som godkänts genom en ruttexpedition.",
   :en "Officially approved route (in compliance with Act 670/1991)."},
  :type-code 4421,
  :status "active",
  :geometry-type "LineString"}
 {:name
  {:fi "Moottorikelkkaura",
   :se "Snöskoterspår",
   :en "Unofficial snowmobile route"},
  :description
  {:fi "Ei reittitoimitusta.",
   :se "Ingen ruttexpedition.",
   :en "No official approval."},
  :type-code 4422,
  :status "active",
  :geometry-type "LineString"}
 {:name {:fi "Hevosreitti", :se "Hästled", :en "Horse track"},
  :description
  {:fi
   "Ratsastukseen ja/tai kärryillä ajoon tarkoitettu reitti. Käyttötavat ominaisuustiedoissa.",
   :se
   "Led avsedd för ridning och/eller häst med kärra. Användningsanvisningar i karakteristika.",
   :en
   "Route intended for horseback riding and/or carriage riding. Different uses specified in 'attribute data'."},
  :type-code 4430,
  :status "active",
  :geometry-type "LineString"}
 {:name
  {:fi "Koirahiihtolatu",
   :se "Spår för skidåkning med hund",
   :en "Dog skijoring track"},
  :description
  {:fi
   "Hiihtolatu, jossa on aina tai tiettyinä aikoina koirahiihto sallittua. Perinteinen tyyli tai vapaa tyyli.",
   :se
   "Ett skidspår där det alltid eller vissa tider är tillåtet att åka skidor med hund. Klassisk stil eller fristil.",
   :en
   "Ski track on which dog skijoring is allowed either always or at given times. Traditional or free style."},
  :type-code 4440,
  :status "active",
  :geometry-type "LineString"}
 {:name {:fi "Melontareitti", :se "Paddlingsled", :en "Canoe route"},
  :description
  {:fi
   "Erityisesti melontaan, merkitty. Reittiehdotus- tyyppinen, ei navigointiin.",
   :se
   "Särskilt för paddling, märkt med ruttförslag, ej för navigering.",
   :en
   "Marked route particularly for canoeing. Route suggestions are not intended for navigation."},
  :type-code 4451,
  :status "active",
  :geometry-type "LineString"}
 {:name
  {:fi "Vesiretkeilyreitti",
   :se "Utflyktsled i vattendrag",
   :en "Water route"},
  :description
  {:fi
   "Merkitty vesireitti, ei veneväylä. Reittiehdotus- tyyppinen. Esim. kirkkovenesoutureitti.",
   :se
   "Märkt vattenled, endast ruttförslag t ex för kyrkbåtsrodd, inte som farled för småbåtar.",
   :en
   "Marked water route, not a navigation channel. Route suggestions included. E.g., route for \"church rowing boats\"."},
  :type-code 4452,
  :status "active",
  :geometry-type "LineString"}
 {:name
  {:fi "Suunnistusalue",
   :se "Orienteringsområde",
   :en "Orienteering area"},
  :description
  {:fi "Ilmoitettu suunnistusliitolle. Alueesta kartta.",
   :se
   "Anmält till orienteringsförbundet. Karta över området tillgänglig.",
   :en
   "The Finnish Orienteering Federation has been informed. A map of the area available."},
  :type-code 4510,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Hiihtosuunnistusalue",
   :se "Skidorienteringsområde",
   :en "Ski orienteering area"},
  :description
  {:fi "Alueesta hiihtosuunnistuskartta, ei kesäsuunnistusalue.",
   :se "Skidorienteringskarta över området, ej för sommarorientering.",
   :en
   "A ski orienteering map of the area available; no summer orienteering."},
  :type-code 4520,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Pyöräsuunnistusalue",
   :se "Cykelorienteringsområde",
   :en " Mountain bike orienteering area"},
  :description
  {:fi "Alueesta pyöräsuunnistukseen soveltuva kartta.",
   :se "Karta över område som lämpar sig för cykelorientering.",
   :en "A map for mountain bike orienteering available."},
  :type-code 4530,
  :status "active",
  :geometry-type "Polygon"}
 {:name
  {:fi "Ampumahiihdon harjoittelualue",
   :se "Träningsområde för skidskytte",
   :en "Training area for biathlon"},
  :description
  {:fi
   "Muu ampumahiihdon harjoittelualue. Latu ja ampumapaikka olemassa.",
   :se
   "Annat träningsområde för skidskytte. Spår och skjutplats finns.",
   :en
   "Other training area for biathlon. Ski track and shooting range."},
  :type-code 4610,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Ampumahiihtokeskus",
   :se "Skidskyttecentrum",
   :en "Biathlon centre"},
  :description
  {:fi "Vähintään kansallisen tason kilpailujen järjestämiseen.",
   :se "Tillräckligt stort för åtminstone nationella tävlingar.",
   :en "For minimum national level competitions."},
  :type-code 4620,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Kilpahiihtokeskus",
   :se "Skidtävlingscentrum",
   :en "Ski competition centre"},
  :description
  {:fi "Lähtö- ja maalialue, huoltotilat. Latuja.",
   :se "Start- och målområden, serviceutrymmen, spårsystem.",
   :en "Start and finish area, service premises. Tracks."},
  :type-code 4630,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Hiihtomaa", :se "Skidland", :en "Cross-country ski park"},
  :description
  {:fi "Hiihdon harjoittelupaikka, tekniikkahalsteri ym.",
   :se "Träningsplats för skidåkning, teknikhalster mm.",
   :en
   "Ski training venue, an area of parallel short ski tracks for ski instruction, etc."},
  :type-code 4640,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Ulkokiipeilyseinä",
   :se "Utomhusklättervägg",
   :en "Open-air climbing wall"},
  :description
  {:fi
   "Rakennettu kiipeilyseinä ulkona. Korkeus ominaisuustietoihin. Myös boulderointipaikat.",
   :se
   "Byggd klättervägg utomhus. Höjden anges i karakteristika. Även platser för bouldering.",
   :en
   "Built outdoor climbing wall. Height given in 'attribute data'. Also includes bouldering venues."},
  :type-code 4710,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Kiipeilykallio", :se "Klätterberg", :en "Climbing rock"},
  :description
  {:fi
   "Merkitty luonnon kallio. Jääkiipeily lisätietoihin. Myös boulderointikalliot.",
   :se
   "Märkt berg i naturen. Isklättring i tilläggsinformation. Även berg för bouldering.",
   :en
   "Marked natural cliff. Ice climbing specified in additional information. Also includes bouldering cliffs."},
  :type-code 4720,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Ampumarata", :se "Skjutbana", :en "Open-air shooting range"},
  :description
  {:fi "Ulkoampumarata yhdelle tai useammalle lajille. ",
   :se "Utomhusskjutbana för en eller flera grenar.",
   :en "Outdoor shooting range for one or more sports. "},
  :type-code 4810,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Ampumaurheilukeskus",
   :se "Sportskyttecentrum",
   :en "Shooting sports centre"},
  :description
  {:fi
   "Ampumarata jossa myös palveluita. Sm-kisojen järjestäminen mahdollista.",
   :se "Skjutbana med tjänster. Möjligt att arrangera FM-tävlingar.",
   :en
   "Shooting range with services. National competitions possible."},
  :type-code 4820,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Jousiammuntarata", :se "Bågskyttebana", :en "Archery range"},
  :description
  {:fi "Ulkona tai sisällä. Varustus ja lajit ominaisuustietoihin.",
   :se "Ute eller inne. Utrustning och grenar i karakteristika.",
   :en
   "Outdoors or indoors. Equipment and the various sports detailed in 'attribute data'."},
  :type-code 4830,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Jousiammuntamaastorata",
   :se "Terrängbana för bågskytte.",
   :en "Field archery course"},
  :description
  {:fi "Maastoon rakennettu jousiammuntarata.",
   :se "Bågskyttebana byggd i terrängen.",
   :en "Archery course built in rough terrain."},
  :type-code 4840,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Soutustadion", :se "Roddstadion", :en "Rowing stadium"},
  :description
  {:fi
   "Rakennettu pysyvästi soudulle. Katsomo ja valmius ratamerkintöihin.",
   :se
   "Byggt för rodd, permanent. Läktare och förberett för banmärkning.",
   :en
   "Permanent construction for rowing. Bleachers, track markings possible."},
  :type-code 5110,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Purjehdusalue", :se "Seglingsområde", :en "Sailing area"},
  :description
  {:fi "Rakennettu pysyvästi purjehdukselle.",
   :se "Byggt för segling, permanent. ",
   :en "Permanent construction for sailing."},
  :type-code 5120,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Moottoriveneurheilualue",
   :se "Område för motorbåtsport",
   :en "Motor boat sports area"},
  :description
  {:fi "Pysyvä nopeuskilpailujen rata-alue.",
   :se "Permanent banområde för hastighetstävlingar.",
   :en "Permanent track area for speed competitions."},
  :type-code 5130,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Vesihiihtoalue",
   :se "Område för vattenskidåkning",
   :en "Water ski area"},
  :description
  {:fi "Rakennettu pysyvästi vesihiihdolle. Vähintään laituri.",
   :se "Byggt för vattenskidåkning, permanent. Minimikrav  brygga.",
   :en
   "Permanent construction for water skiing. Minimum equipment  pier."},
  :type-code 5140,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Koskimelontakeskus",
   :se "Centrum för forspaddling",
   :en "Rapid canoeing centre"},
  :description
  {:fi "Kilpailujen järjestäminen mahdollista.",
   :se "Möjligt att arrangera tävlingar.",
   :en "Competitions possible."},
  :type-code 5150,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Soudun ja melonnan sisäharjoittelutila",
   :se "Inomhusträningsutrymme för rodd och paddling",
   :en "Indoor training facility for rowing and canoeing"},
  :description
  {:fi "Erillinen liikuntapaikka, ei normaali uima-allas.",
   :se "Separat, ej normal simbassäng.",
   :en "Separate training facility, not a regular swimming pool."},
  :type-code 5160,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Urheiluilmailualue",
   :se "Område för flygsport",
   :en "Sport aviation area"},
  :description {:fi nil, :se nil, :en nil},
  :type-code 5210,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Moottoriurheilukeskus",
   :se "Centrum för motorsport",
   :en "Motor sports centre"},
  :description
  {:fi
   "Useiden eri moottoriurheilun lajien suorituspaikkoja, huoltotilat olemassa.",
   :se
   "Platser för flera olika motorsportgrenar, serviceutrymmen finns.",
   :en "Venues for various motor sports; service premises available."},
  :type-code 5310,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Moottoripyöräilyalue",
   :se "Område för motorcykelsport",
   :en "Motorcycling area"},
  :description
  {:fi
   "Pääasiallisesti moottoripyöräilyä varten. Lajityypit mainitaan ominaisuustiedoissa.",
   :se
   "Huvudsakligen för motorcykelsport. Möjliga grenar anges i karakteristika.",
   :en
   "Mainly for motorcycling. Sports types detailed in 'attribute data'."},
  :type-code 5320,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Moottorirata", :se "Motorbana", :en "Formula race track"},
  :description
  {:fi "Suuri rata-autoiluun tarkoitettu moottoriurheilupaikka.",
   :se "Stor motorsportplats avsedd för bankörning.",
   :en "Large motor sports venue for formula racing."},
  :type-code 5330,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Karting-rata", :se "Kartingbana", :en "Kart circuit"},
  :description
  {:fi "Pääasiallisesti karting-ajoa varten",
   :se "Huvudsakligen för karting.",
   :en "Mainly for karting."},
  :type-code 5340,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Kiihdytysrata", :se "Dragracingbana", :en "Dragstrip"},
  :description
  {:fi "Pääasiallisesti kiihdytysautoilua varten.",
   :se "Huvudsakligen avsedd för dragracing.",
   :en "Mainly for drag racing."},
  :type-code 5350,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Jokamies- ja rallicross-rata",
   :se "Allemans- och rallycrossbana",
   :en "Everyman racing and rallycross track "},
  :description
  {:fi "Pääasiallisesti jokamiesajoa ja/tai rallicrossia varten.",
   :se "Huvudsakligen för allemanskörning och/eller rallycross.",
   :en "Mainly for everyman racing and/or rallycross."},
  :type-code 5360,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Jääspeedway-rata",
   :se "Isracingbana",
   :en "Ice speedway track"},
  :description
  {:fi "Pääasiallisesti jääspeedwayta varten.",
   :se "Huvudsakligen för isracing.",
   :en "Speedway mainly for ice racing."},
  :type-code 5370,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Ratsastuskenttä", :se "Ridbana", :en "Equestrian field"},
  :description
  {:fi "Ratsastukseen varattu kenttä. Koko ominasuustietoihin.",
   :se "Bana avsedd för ridning. Storlek i karakteristika.",
   :en
   "Field reserved for horseback riding. Size specified in 'attribute data'."},
  :type-code 6110,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Ratsastusmaneesi", :se "Ridmanege", :en "Riding manège"},
  :description
  {:fi "Kylmä tai lämmin, katettu tila ratsastukseen.",
   :se "Kallt eller varmt takförsett utrymme för ridning.",
   :en "Cold or warm, covered space for horseback riding."},
  :type-code 6120,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Esteratsastuskenttä",
   :se "Bana för banhoppning",
   :en "Show jumping field"},
  :description
  {:fi "Pysyvästi esteratsastukseen varusteltu kenttä. Ulkona.",
   :se "Bana med permanent utrustning för banhoppning.",
   :en "Field permanently equipped for show jumping. Outdoors."},
  :type-code 6130,
  :status "active",
  :geometry-type "Point"}
 {:name {:fi "Ravirata", :se "Travbana", :en "Horse racing track"},
  :description
  {:fi "Raviurheilun harjoitus- tai kilparata.",
   :se "Övnings- eller tävlingsbana för travsport.",
   :en "Training or competition track for horse racing."},
  :type-code 6140,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Koiraurheilualue",
   :se "Område för hundsport",
   :en "Dog sports area"},
  :description
  {:fi
   "Koiran koulutukseen, agilityyn tai muuhun harjoittamiseen varattu alue.",
   :se
   "Område reserverat för hunddressyr, agility eller annan träning.",
   :en "Area reserved for dog training, agility or other dog sports."},
  :type-code 6210,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Koiraurheiluhalli",
   :se "Hundsporthall",
   :en "Dog sports hall"},
  :description
  {:fi
   "Erityisesti koiraharrastusta, agilityä, koulutusta tms. varten varustettu halli.",
   :se
   "Hall som utrustats särskilt för hundhobby, agility, träning osv.",
   :en
   "Hall specifically equipped for dog sports, agility, training, etc."},
  :type-code 6220,
  :status "active",
  :geometry-type "Point"}
 {:name
  {:fi "Huoltorakennukset",
   :se "Servicebyggnader",
   :en "Maintenance/service buildings"},
  :description
  {:fi "Liikuntapaikkoihin liittyvät huoltorakennukset.",
   :se "Servicebyggnader i anslutning till idrottsanläggningar.",
   :en "Maintenance buildings in connection with sports facilities."},
  :type-code 7000,
  :status "active",
  :geometry-type "Point"}])
