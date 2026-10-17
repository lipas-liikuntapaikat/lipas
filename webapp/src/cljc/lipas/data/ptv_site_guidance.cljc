(ns lipas.data.ptv-site-guidance
  "Per-group authoring guidance for PTV Service Location (liikuntapaikka)
  summaries and descriptions.

  DVV groups sports-facility types into ten groups of their own that cut
  across LIPAS sub-categories — eight sub-categories are split between two
  groups, because the grouping tracks the CHARACTER of a place (area vs.
  built facility, route vs. rink, sport-specific hall vs. general hall)
  rather than the LIPAS hierarchy. Guidance is therefore keyed by
  `type-code`, not by sub-category, which is how the sibling
  `lipas.data.ptv-service-guidance` keys the Service-level guidance.

  Each group carries two kinds of material:

  UI-facing (shown to the author in the PTV site view, localized fi/se/en)
    :summary     - what the tiivistelmä should answer
    :description - what the kuvaus should answer
    :avoid       - what does not belong in a kuvaus

  Assistant-facing (Finnish only; background for Lipastaja, not rendered)
    :definition   - what the group covers
    :usable-info  - topics and vocabulary a description may draw on

  The texts are written as questions and instructions to a human author
  (\"Kerro, onko...\", \"Mainitse vain...\") and describe what a description
  should COVER — they are not facts about any individual site.

  Source: Liikuntapaikkojen_kuvausohje_ryhmitelty.xlsx (DVV, 09/2026).
  Finnish is the authoritative source text; se/en are LIPAS translations.
  PTV text generation does not read this namespace yet.")

(def groups
  "Group key -> guidance. See ns docstring for the field split."
  {:ulkoilu-ja-virkistysalueet
   {:name {:fi "Ulkoilu- ja virkistysalueet"
           :se "Frilufts- och rekreationsområden"
           :en "Outdoor and recreation areas"}
    :definition "Alueet, joissa voi ulkoilla, liikkua, retkeillä, virkistäytyä tai viettää aikaa luonnossa tai rakennetussa lähiympäristössä."
    :summary {:fi "1. Mikä paikka on?
Kerro, onko kyseessä esimerkiksi virkistysalue, ulkoilualue, liikuntapuisto tai luonnossa sijaitseva retkeilyalue.

2. Mitä paikassa voi tehdä?
Mainitse tärkeimmät tekemiset, kuten ulkoilu, retkeily, liikkuminen, kalastus, oleskelu tai eri liikuntalajit.

3. Mikä tekee paikasta käyttäjälle tunnistettavan?
Mainitse yksi tai kaksi olennaista piirrettä, jos ne auttavat hahmottamaan paikkaa."
              :se "1. Vad är platsen?
Berätta om det till exempel är ett rekreationsområde, ett friluftsområde, en idrottspark eller ett vandringsområde i naturen.

2. Vad kan man göra på platsen?
Nämn de viktigaste aktiviteterna, såsom friluftsliv, vandring, motion, fiske, vistelse eller olika idrottsgrenar.

3. Vad gör platsen igenkännbar för användaren?
Nämn en eller två väsentliga egenskaper om de hjälper användaren att uppfatta platsen."
              :en "1. What is this place?
State whether it is, for example, a recreation area, an outdoor area, a sports park or a hiking area in a natural setting.

2. What can you do there?
Mention the main activities, such as outdoor recreation, hiking, exercise, fishing, spending time or various sports.

3. What makes the place recognisable to the user?
Mention one or two essential features if they help the reader picture the place."}
    :description {:fi "1. Millainen alue on?
Kuvaa alueen luonne lyhyesti: onko se esimerkiksi puistomainen, metsäinen, luonnonsuojelualue, liikuntapuisto tai monikäyttöinen lähialue.

2. Mitä alueella voi tehdä?
Kerro keskeiset liikunta-, ulkoilu- ja virkistysmahdollisuudet.

3. Mitä rakenteita tai kohteita alueella on?
Mainitse vain käyttäjän kannalta tärkeimmät, kuten reitit, kentät, taukopaikat, uimapaikka tai opasteet.

4. Kenelle alue sopii?
Kerro tarvittaessa, jos alue sopii erityisesti esimerkiksi perheille, retkeilijöille, kuntoilijoille tai omatoimiseen liikkumiseen."
                  :se "1. Hurdant är området?
Beskriv områdets karaktär kort: är det till exempel parkliknande, skogbevuxet, ett naturskyddsområde, en idrottspark eller ett mångsidigt närområde.

2. Vad kan man göra i området?
Berätta om de centrala motions-, frilufts- och rekreationsmöjligheterna.

3. Vilka konstruktioner eller objekt finns i området?
Nämn endast de viktigaste för användaren, såsom leder, planer, rastplatser, badplats eller skyltar.

4. För vem passar området?
Berätta vid behov om området särskilt lämpar sig för till exempel familjer, vandrare, motionärer eller självständig motion."
                  :en "1. What kind of area is it?
Describe the character of the area briefly: is it, for example, park-like, forested, a nature conservation area, a sports park or a multi-purpose local area.

2. What can you do in the area?
Describe the main sports, outdoor and recreation opportunities.

3. What structures or features does the area have?
Mention only those that matter most to the user, such as routes, fields, rest areas, a swimming spot or signposting.

4. Who is the area suited to?
If relevant, say whether the area particularly suits, for example, families, hikers, people exercising or independent use."}
    :usable-info "Alueen luonne, tärkeimmät liikunta- ja virkistysmahdollisuudet, keskeiset rakenteet, reitit, taukopaikat, uimapaikka, esteettömyys tai muu käyttäjän valintaa helpottava tieto."
    :avoid {:fi "Markkinointipuhe, pitkä historiakuvaus, kunnan ylläpitotyön kuvailu, osoitteet, linkit, hinnat, yksityiskohtaiset aukioloajat ja kaikkien alueen yksittäisten kohteiden pitkä luettelo."
            :se "Marknadsföringsspråk, långa historiebeskrivningar, beskrivningar av kommunens underhållsarbete, adresser, länkar, priser, detaljerade öppettider och en lång förteckning över alla enskilda objekt i området."
            :en "Marketing language, lengthy history, descriptions of the municipality's maintenance work, addresses, links, prices, detailed opening hours and a long list of every individual feature in the area."}
    :type-codes #{101 103 106 107 109 110 111 112 113 1110}}

   :retkeilyn-palvelupaikat
   {:name {:fi "Retkeilyn palvelupaikat"
           :se "Serviceplatser för vandring"
           :en "Hiking service points"}
    :definition "Yksittäiset kohteet ja rakenteet, jotka tukevat retkeilyä, ulkoilua, levähtämistä, ruoanlaittoa, yöpymistä, opastusta tai luonnon tarkkailua."
    :summary {:fi "1. Mikä paikka on?
Kerro, onko kyseessä esimerkiksi laavu, kota, tupa, tulentekopaikka, luontotorni, opastuspiste tai veneilyn palvelupaikka.

2. Mihin paikkaa käytetään?
Kerro, palveleeko paikka levähtämistä, retkeilyä, ruoanlaittoa, yöpymistä, opastusta, luonnon tarkkailua tai vesillä liikkumista.

3. Mikä varustelu on tärkein?
Mainitse vain olennaisin varustelu."
              :se "1. Vad är platsen?
Berätta om det till exempel är ett vindskydd, en kåta, en stuga, en eldplats, ett naturtorn, en informationspunkt eller en serviceplats för båtliv.

2. Vad används platsen till?
Berätta om platsen tjänar rast, vandring, matlagning, övernattning, vägledning, naturobservation eller färd på vatten.

3. Vilken utrustning är viktigast?
Nämn endast den mest väsentliga utrustningen."
              :en "1. What is this place?
State whether it is, for example, a lean-to shelter, a hut, a cabin, a campfire site, a nature observation tower, an information point or a boating service point.

2. What is the place used for?
Say whether it serves resting, hiking, cooking, overnight stays, guidance, nature observation or getting out on the water.

3. Which equipment matters most?
Mention only the most essential equipment."}
    :description {:fi "1. Mitä käyttäjä löytää paikalta?
Kuvaa keskeiset rakenteet tai varustelu.

2. Miten paikka tukee retkeilyä tai ulkoilua?
Kerro lyhyesti, mihin tilanteeseen paikka sopii.

3. Missä yhteydessä paikkaa käytetään?
Voit mainita, jos kohde liittyy esimerkiksi reittiin, virkistysalueeseen, rantaan tai kalastuskohteeseen.

4. Tarvitseeko käyttäjä jotain omaa?
Mainitse vain, jos tieto on paikan käytön kannalta olennainen, esimerkiksi omat polttopuut."
                  :se "1. Vad hittar användaren på platsen?
Beskriv de centrala konstruktionerna eller utrustningen.

2. Hur stöder platsen vandring eller friluftsliv?
Berätta kort vilken situation platsen passar för.

3. I vilket sammanhang används platsen?
Du kan nämna om objektet anknyter till exempel till en led, ett rekreationsområde, en strand eller ett fiskeobjekt.

4. Behöver användaren ta med något eget?
Nämn detta endast om uppgiften är väsentlig för användningen av platsen, till exempel egen ved."
                  :en "1. What will the user find at the site?
Describe the key structures or equipment.

2. How does the place support hiking or outdoor recreation?
Say briefly what situation the place suits.

3. In what context is the place used?
You may mention if the site is connected to, for example, a route, a recreation area, a shore or a fishing site.

4. Does the user need to bring anything?
Mention this only if it is essential to using the place, for example bringing your own firewood."}
    :usable-info "Tulentekomahdollisuus, katos tai suoja, wc, laituri, opasteet, näköalapaikka, kalastusmahdollisuus, yöpymiseen tai levähtämiseen liittyvä varustelu."
    :avoid {:fi "Kahvilan tai yrittäjän pitkät palvelukuvaukset, pysäköintimaksut, yksityiskohtaiset varausohjeet, Facebook- tai verkkolinkit, osoitteet ja pitkät järjestyssäännöt."
            :se "Långa servicebeskrivningar för ett kafé eller en företagare, parkeringsavgifter, detaljerade bokningsanvisningar, Facebook- eller webblänkar, adresser och långa ordningsregler."
            :en "Long service descriptions for a café or an entrepreneur, parking fees, detailed booking instructions, Facebook or web links, addresses and lengthy rules of order."}
    :type-codes #{201 202 203 204 206 207 301 302 304 7000}}

   :reitit
   {:name {:fi "Reitit"
           :se "Leder"
           :en "Routes"}
    :definition "Liikkumiseen, ulkoiluun, harrastamiseen tai retkeilyyn tarkoitetut reitit eri vuodenaikoina ja eri kulkutavoilla."
    :summary {:fi "1. Mikä reitti on?
Kerro reitin tyyppi, esimerkiksi kuntorata, luontopolku, hiihtolatu, pyöräilyreitti tai melontareitti.

2. Mihin liikkumiseen reitti sopii?
Mainitse kulkutapa tai laji.

3. Mikä on käyttäjälle tärkein tieto?
Mainitse esimerkiksi pituus, vaativuus tai ympäristö, jos tieto on saatavilla."
              :se "1. Vilken led är det?
Berätta ledens typ, till exempel motionsspår, naturstig, skidspår, cykelled eller paddlingsled.

2. För vilken typ av färd lämpar sig leden?
Nämn färdsätt eller gren.

3. Vilken är den viktigaste uppgiften för användaren?
Nämn till exempel längd, svårighetsgrad eller omgivning, om uppgiften finns tillgänglig."
              :en "1. What kind of route is it?
State the type of route, for example a fitness track, a nature trail, a ski track, a cycling route or a canoeing route.

2. What kind of movement does the route suit?
Mention the mode of travel or the sport.

3. What is the most important information for the user?
Mention, for example, length, difficulty or surroundings, if the information is available."}
    :description {:fi "1. Mihin reittiä käytetään?
Kuvaa käyttötarkoitus selkeästi.

2. Millaisessa ympäristössä reitti kulkee?
Mainitse esimerkiksi metsä, puisto, taajama, vesistö tai maasto, jos se auttaa käyttäjää.

3. Kuinka pitkä tai vaativa reitti on?
Kerro pituus ja vaativuus, jos tiedot ovat saatavilla ja hyödyllisiä.

4. Onko käyttö vuodenaika- tai olosuhderiippuvaista?
Mainitse tämä yleisellä tasolla, jos se on reitin kannalta olennaista."
                  :se "1. Vad används leden till?
Beskriv användningsändamålet tydligt.

2. I hurdan miljö går leden?
Nämn till exempel skog, park, tätort, vattendrag eller terräng, om det hjälper användaren.

3. Hur lång eller krävande är leden?
Ange längd och svårighetsgrad om uppgifterna finns tillgängliga och är till nytta.

4. Är användningen beroende av årstid eller förhållanden?
Nämn detta på en allmän nivå om det är väsentligt för leden."
                  :en "1. What is the route used for?
Describe the purpose clearly.

2. What kind of environment does the route pass through?
Mention, for example, forest, park, built-up area, water or terrain, if it helps the user.

3. How long or how demanding is the route?
Give length and difficulty if the information is available and useful.

4. Does use depend on the season or conditions?
Mention this in general terms if it is essential for the route."}
    :usable-info "Pituus, kulkutapa, maasto, vaativuus, valaistus, talvikäyttö, reitin luonne, yhteys muihin reitteihin tai taukopaikkoihin."
    :avoid {:fi "Ajantasaiset kunnossapitotiedot, pitkät käyttökiellot, varoitukset, osoitteet, linkit, yksityiskohtaiset kellonajat ja sääntöluettelot."
            :se "Aktuell underhållsinformation, långa användningsförbud, varningar, adresser, länkar, detaljerade klockslag och regelförteckningar."
            :en "Real-time maintenance information, extended closure notices, warnings, addresses, links, precise times of day and lists of rules."}
    :type-codes #{1550 4401 4402 4403 4404 4405 4406 4407 4411 4412 4421 4422 4430 4440 4441 4451 4452}}

   :ulkoliikunta-ja-lahiliikuntapaikat
   {:name {:fi "Ulkoliikunta- ja lähiliikuntapaikat"
           :se "Utomhus- och närmotionsplatser"
           :en "Outdoor and local sports facilities"}
    :definition "Rakennetut ulkoliikuntapaikat, joissa voi harrastaa omaehtoista lähiliikuntaa, kuntoilua tai tiettyä ulkona tapahtuvaa lajia."
    :summary {:fi "1. Mikä paikka on?
Kerro, onko kyseessä esimerkiksi lähiliikuntapaikka, ulkokuntoilupaikka, parkour-alue, skeittipaikka tai frisbeegolfrata.

2. Mitä paikassa voi tehdä?
Mainitse tärkeimmät liikuntamahdollisuudet.

3. Kenelle paikka sopii?
Kerro tarvittaessa, sopiiko paikka esimerkiksi kaikenikäisille, aloittelijoille, harrastajille tai omatoimiseen liikkumiseen."
              :se "1. Vad är platsen?
Berätta om det till exempel är en närmotionsplats, ett utegym, ett parkourområde, en skateboardplats eller en discgolfbana.

2. Vad kan man göra på platsen?
Nämn de viktigaste motionsmöjligheterna.

3. För vem passar platsen?
Berätta vid behov om platsen lämpar sig för till exempel alla åldrar, nybörjare, motionärer eller självständig motion."
              :en "1. What is this place?
State whether it is, for example, a local sports facility, an outdoor gym, a parkour area, a skateboarding spot or a disc golf course.

2. What can you do there?
Mention the main opportunities for exercise.

3. Who does the place suit?
If relevant, say whether the place suits, for example, all ages, beginners, regular participants or independent exercise."}
    :description {:fi "1. Mitä välineitä tai rakenteita paikassa on?
Kuvaa vain tärkeimmät välineet, telineet, radat tai harjoittelumahdollisuudet.

2. Millainen paikka on käyttää?
Kerro tarvittaessa, onko paikka esimerkiksi helppokäyttöinen, omatoiminen, aloittelijoille sopiva tai tiettyyn lajiin tarkoitettu.

3. Onko määrätieto käyttäjälle hyödyllinen?
Mainitse esimerkiksi frisbeegolfradan väylien määrä tai ratagolfradan luonne, jos tieto auttaa käyttäjää."
                  :se "1. Vilken utrustning eller vilka konstruktioner finns på platsen?
Beskriv endast de viktigaste redskapen, ställningarna, banorna eller träningsmöjligheterna.

2. Hurdan är platsen att använda?
Berätta vid behov om platsen till exempel är lättanvänd, självbetjänad, lämplig för nybörjare eller avsedd för en viss gren.

3. Är en sifferuppgift till nytta för användaren?
Nämn till exempel antalet banor på en discgolfbana eller bangolfbanans karaktär, om uppgiften hjälper användaren."
                  :en "1. What equipment or structures does the place have?
Describe only the most important equipment, frames, courses or training opportunities.

2. What is the place like to use?
If relevant, say whether the place is, for example, easy to use, self-service, suitable for beginners or intended for a particular sport.

3. Is a number useful to the user?
Mention, for example, the number of holes on a disc golf course or the character of a mini golf course, if it helps the user."}
    :usable-info "Keskeiset välineet, radan tai alueen luonne, väylien määrä, soveltuvuus aloittelijoille tai harrastajille, omatoiminen käyttö yleisellä tasolla."
    :avoid {:fi "Laitevalmistajien pitkät luettelot, tekniset yksityiskohdat, markkinointipuhe, koulun tai muun toimijan toiminnan kuvailu, tarkat käyttöajat ja hinnat."
            :se "Långa förteckningar från utrustningstillverkare, tekniska detaljer, marknadsföringsspråk, beskrivningar av en skolas eller annan aktörs verksamhet, exakta användningstider och priser."
            :en "Long lists from equipment manufacturers, technical details, marketing language, descriptions of a school's or another operator's activities, precise hours of use and prices."}
    :type-codes #{1120 1130 1140 1150 1160 1170 1180 1190 1395 1640}}

   :kentat-ja-suorituspaikat
   {:name {:fi "Kentät ja suorituspaikat"
           :se "Planer och tävlingsplatser"
           :en "Pitches and event areas"}
    :definition "Ulkona sijaitsevat kentät ja suorituspaikat, jotka on tarkoitettu palloiluun, yleisurheiluun tai muihin lajikohtaisiin suorituksiin."
    :summary {:fi "1. Mikä kenttä tai suorituspaikka on?
Kerro laji tai käyttötarkoitus.

2. Mitä paikassa voi tehdä?
Mainitse esimerkiksi pelaaminen, harjoittelu, yleisurheilu tai kilpailutoiminta.

3. Mikä ominaisuus on käyttäjälle tärkein?
Mainitse esimerkiksi pinta, valaistus, katsomo tai varustelu, jos tieto auttaa käyttäjää."
              :se "1. Vilken plan eller tävlingsplats är det?
Berätta gren eller användningsändamål.

2. Vad kan man göra på platsen?
Nämn till exempel spel, träning, friidrott eller tävlingsverksamhet.

3. Vilken egenskap är viktigast för användaren?
Nämn till exempel underlag, belysning, läktare eller utrustning, om uppgiften hjälper användaren."
              :en "1. What kind of pitch or event area is it?
State the sport or the intended use.

2. What can you do there?
Mention, for example, playing, training, athletics or competitive activity.

3. Which feature matters most to the user?
Mention, for example, the surface, lighting, stands or equipment, if the information helps the user."}
    :description {:fi "1. Mille lajille paikka on tarkoitettu?
Kerro laji tai käyttötarkoitus selkeästi.

2. Millainen kenttä tai suorituspaikka on?
Kuvaa käyttäjän kannalta tärkeimmät ominaisuudet, kuten pinta, valaistus, katsomo tai pukutilat.

3. Miten paikkaa käytetään?
Voit mainita yleisellä tasolla, jos paikka on vapaassa käytössä silloin, kun se ei ole varattu, tai jos käyttö perustuu varattuihin vuoroihin.

4. Tarvitaanko mittoja?
Mainitse mitat vain, jos ne ovat lajin tai käyttäjän kannalta olennaisia."
                  :se "1. För vilken gren är platsen avsedd?
Berätta gren eller användningsändamål tydligt.

2. Hurdan är planen eller tävlingsplatsen?
Beskriv de egenskaper som är viktigast för användaren, såsom underlag, belysning, läktare eller omklädningsrum.

3. Hur används platsen?
Du kan nämna på en allmän nivå om platsen är fritt tillgänglig när den inte är bokad, eller om användningen bygger på bokade turer.

4. Behövs mått?
Ange mått endast om de är väsentliga för grenen eller användaren."
                  :en "1. Which sport is the place intended for?
State the sport or the intended use clearly.

2. What kind of pitch or event area is it?
Describe the features that matter most to the user, such as the surface, lighting, stands or changing rooms.

3. How is the place used?
You may mention in general terms whether the place is freely available when it is not booked, or whether use is based on booked slots.

4. Are dimensions needed?
Give dimensions only if they are essential for the sport or the user."}
    :usable-info "Laji, kentän pinta, valaistus, suorituspaikat, katsomo, pukutilat, yleinen tieto vapaasta käytöstä tai varattavuudesta."
    :avoid {:fi "Hinnat, yksityiskohtaiset vuoronhakuprosessit, lautakuntapäätökset, pitkät varausohjeet, kaikki tekniset mitat ja kunnossapidon kuvailu."
            :se "Priser, detaljerade processer för att ansöka om turer, nämndbeslut, långa bokningsanvisningar, alla tekniska mått och beskrivningar av underhållet."
            :en "Prices, detailed processes for applying for slots, committee decisions, lengthy booking instructions, all technical dimensions and descriptions of maintenance."}
    :type-codes #{1210 1220 1310 1320 1330 1340 1350 1360 1370 1380 1390 2310}}

   :talviliikuntapaikat
   {:name {:fi "Talviliikuntapaikat"
           :se "Vinteridrottsplatser"
           :en "Winter sports facilities"}
    :definition "Talvilajeihin tarkoitetut paikat ja alueet, joiden käyttö voi riippua säästä, lumesta, jäästä tai kaudesta."
    :summary {:fi "1. Mikä talviliikuntapaikka on?
Kerro, onko kyseessä esimerkiksi luistelukenttä, kaukalo, latu, hiihtokeskus, laskettelupaikka tai hyppyrimäki.

2. Mitä paikassa voi tehdä?
Mainitse keskeinen talvilaji tai harjoittelumahdollisuus.

3. Riippuuko käyttö olosuhteista?
Mainitse tämä lyhyesti, jos tieto on paikan kannalta olennainen."
              :se "1. Vilken vinteridrottsplats är det?
Berätta om det till exempel är en isbana, en sarg, ett skidspår, ett skidcentrum, en slalombacke eller en hoppbacke.

2. Vad kan man göra på platsen?
Nämn den centrala vintergrenen eller träningsmöjligheten.

3. Beror användningen på förhållandena?
Nämn detta kort om uppgiften är väsentlig för platsen."
              :en "1. What kind of winter sports facility is it?
State whether it is, for example, an ice rink, a rink with boards, a ski track, a ski resort, a downhill skiing spot or a ski jumping hill.

2. What can you do there?
Mention the main winter sport or training opportunity.

3. Does use depend on conditions?
Mention this briefly if it is essential for the place."}
    :description {:fi "1. Mihin talvilajiin paikka on tarkoitettu?
Kuvaa laji ja käyttötarkoitus.

2. Millainen suoritusympäristö on?
Mainitse esimerkiksi kaukalo, jääalue, latuverkosto, rinne, ampumapaikat tai hyppyrimäki.

3. Onko käyttö omatoimista, harjoittelu- tai kilpailukäyttöä?
Kerro tämä yleisellä tasolla, jos tieto on käyttäjälle olennainen.

4. Miten olosuhteet vaikuttavat?
Mainitse sää-, lumi- tai jääolosuhteiden vaikutus yleisellä tasolla."
                  :se "1. För vilken vintergren är platsen avsedd?
Beskriv gren och användningsändamål.

2. Hurdan är utövningsmiljön?
Nämn till exempel sarg, isområde, spårnätverk, slalombacke, skjutplatser eller hoppbacke.

3. Är användningen självständig, tränings- eller tävlingsbruk?
Berätta detta på en allmän nivå om uppgiften är väsentlig för användaren.

4. Hur påverkar förhållandena?
Nämn väder-, snö- eller isförhållandenas inverkan på en allmän nivå."
                  :en "1. Which winter sport is the place intended for?
Describe the sport and the intended use.

2. What is the setting like?
Mention, for example, a rink with boards, an ice area, a network of ski tracks, a slope, shooting places or a ski jumping hill.

3. Is use independent, for training or for competition?
State this in general terms if the information matters to the user.

4. How do conditions affect use?
Mention the effect of weather, snow or ice conditions in general terms."}
    :usable-info "Laji, suoritusympäristö, valaistus, radan tai alueen pääominaisuus, olosuhderiippuvuus, yleisöluistelu yleisellä tasolla."
    :avoid {:fi "Ajantasainen jää- tai latutilanne, tarkat vuorolistat, hinnat, linkit, pitkät turvallisuusohjeet ja yksityiskohtaiset varausprosessit."
            :se "Aktuellt is- eller spårläge, exakta turlistor, priser, länkar, långa säkerhetsanvisningar och detaljerade bokningsprocesser."
            :en "Real-time ice or ski track conditions, precise timetables of slots, prices, links, lengthy safety instructions and detailed booking processes."}
    :type-codes #{1510 1520 1530 1540 1560 4110 4220 4230 4240 4320 4610 4620 4630 4640}}

   :sisaliikuntatilat
   {:name {:fi "Sisäliikuntatilat"
           :se "Idrottslokaler inomhus"
           :en "Indoor sports facilities"}
    :definition "Sisällä sijaitsevat yleis- ja lajikohtaiset liikuntatilat, joissa voi harjoitella, harrastaa tai osallistua ohjattuun tai omatoimiseen liikuntaan."
    :summary {:fi "1. Mikä tila on?
Kerro, onko kyseessä esimerkiksi liikuntasali, liikuntahalli, kuntosali, tanssitila, sisäkiipeilyseinä tai kamppailusali.

2. Mitä tilassa voi tehdä?
Mainitse keskeiset liikuntamuodot.

3. Mikä varustelu on käyttäjälle tärkein?
Mainitse vain olennaiset välineet, tilat tai harjoittelumahdollisuudet."
              :se "1. Vilken lokal är det?
Berätta om det till exempel är en gymnastiksal, en idrottshall, ett gym, en danslokal, en inomhusklättervägg eller en kampsportssal.

2. Vad kan man göra i lokalen?
Nämn de centrala motionsformerna.

3. Vilken utrustning är viktigast för användaren?
Nämn endast de väsentliga redskapen, utrymmena eller träningsmöjligheterna."
              :en "1. What kind of space is it?
State whether it is, for example, a sports hall, a gymnasium, a gym, a dance space, an indoor climbing wall or a martial arts room.

2. What can you do in the space?
Mention the main forms of exercise.

3. Which equipment matters most to the user?
Mention only the essential equipment, spaces or training opportunities."}
    :description {:fi "1. Mihin liikuntaan tila soveltuu?
Kuvaa käyttötarkoitus selkeästi.

2. Mitä tilassa on?
Mainitse keskeinen varustelu, kuten laitteet, välineet, salipinta, pukutilat tai muut käyttäjälle tärkeät tilat.

3. Miten tilaa käytetään?
Voit kertoa yleisellä tasolla, jos tila soveltuu omatoimiseen harjoitteluun, ryhmäliikuntaan, seuratoimintaan tai varattaviin vuoroihin.

4. Onko esteettömyys tai kulkuoikeus olennainen tieto?
Mainitse vain tarvittaessa ja yleisellä tasolla."
                  :se "1. För vilken motion lämpar sig lokalen?
Beskriv användningsändamålet tydligt.

2. Vad finns i lokalen?
Nämn den centrala utrustningen, såsom maskiner, redskap, salens underlag, omklädningsrum eller andra utrymmen som är viktiga för användaren.

3. Hur används lokalen?
Du kan berätta på en allmän nivå om lokalen lämpar sig för självständig träning, gruppmotion, föreningsverksamhet eller bokade turer.

4. Är tillgänglighet eller tillträdesrätt väsentlig information?
Nämn detta endast vid behov och på en allmän nivå."
                  :en "1. What kind of exercise is the space suited to?
Describe the intended use clearly.

2. What is in the space?
Mention the key equipment, such as machines, gear, the floor surface, changing rooms or other spaces that matter to the user.

3. How is the space used?
You may say in general terms whether the space suits independent training, group exercise, club activity or booked slots.

4. Is accessibility or right of entry essential information?
Mention this only where needed and in general terms."}
    :usable-info "Käyttötarkoitus, tärkeimmät laitteet tai välineet, pukuhuoneet, suihkut, esteettömyys, omatoiminen tai ohjattu käyttö yleisellä tasolla."
    :avoid {:fi "Kaikkien laitteiden yksityiskohtainen luettelo, kulkuoikeuden hankinnan vaiheet, hinnat, tarkat aukioloajat, osoitteet ja ylläpitäjän toiminnan kuvailu."
            :se "En detaljerad förteckning över alla maskiner, stegen för att skaffa tillträdesrätt, priser, exakta öppettider, adresser och beskrivningar av underhållarens verksamhet."
            :en "A detailed list of every machine, the steps for obtaining right of entry, prices, precise opening hours, addresses and descriptions of the operator's activities."}
    :type-codes #{2110 2120 2130 2140 2150 2210 2220 2225 2320 2330 2340 2350 2360 2370 2380 2620}}

   :lajihallit-ja-jaaurheilun-sisatilat
   {:name {:fi "Lajihallit ja jääurheilun sisätilat"
           :se "Grenspecifika hallar och isidrottslokaler"
           :en "Sport-specific halls and indoor ice facilities"}
    :definition "Tiettyä lajia tai lajiryhmää varten rakennetut hallit, joissa voi harjoitella, pelata, kilpailla tai osallistua yleisövuoroille."
    :summary {:fi "1. Mikä halli on?
Kerro laji tai hallin käyttötarkoitus.

2. Mitä hallissa voi tehdä?
Mainitse esimerkiksi harjoittelu, pelaaminen, keilailu, luistelu tai ottelut.

3. Mikä tiloissa on käyttäjälle tärkeää?
Mainitse esimerkiksi ratojen, kenttien tai kaukaloiden määrä, jos tieto auttaa käyttäjää."
              :se "1. Vilken hall är det?
Berätta gren eller hallens användningsändamål.

2. Vad kan man göra i hallen?
Nämn till exempel träning, spel, bowling, skridskoåkning eller matcher.

3. Vad är viktigt för användaren i lokalerna?
Nämn till exempel antalet banor, planer eller rinkar, om uppgiften hjälper användaren."
              :en "1. What kind of hall is it?
State the sport or the intended use of the hall.

2. What can you do in the hall?
Mention, for example, training, playing, bowling, skating or matches.

3. What matters to the user about the facilities?
Mention, for example, the number of lanes, courts or rinks, if the information helps the user."}
    :description {:fi "1. Mille lajille halli on tarkoitettu?
Kuvaa laji ja käyttötarkoitus.

2. Mitä tiloja tai ratoja hallissa on?
Mainitse käyttäjän kannalta tärkeimmät rakenteet, kuten kentät, radat, kaukalot, katsomo tai pukutilat.

3. Millaiseen käyttöön halli soveltuu?
Kerro yleisellä tasolla, soveltuuko halli harrastus-, harjoitus-, kilpailu-, tapahtuma- tai yleisövuorokäyttöön.

4. Tarvitaanko teknisiä tietoja?
Käytä teknisiä mittoja vain, jos ne ovat käyttäjälle aidosti hyödyllisiä."
                  :se "1. För vilken gren är hallen avsedd?
Beskriv gren och användningsändamål.

2. Vilka utrymmen eller banor finns i hallen?
Nämn de konstruktioner som är viktigast för användaren, såsom planer, banor, rinkar, läktare eller omklädningsrum.

3. För hurdan användning lämpar sig hallen?
Berätta på en allmän nivå om hallen lämpar sig för hobby-, tränings-, tävlings-, evenemangs- eller allmänhetsturer.

4. Behövs tekniska uppgifter?
Använd tekniska mått endast om de är till verklig nytta för användaren."
                  :en "1. Which sport is the hall intended for?
Describe the sport and the intended use.

2. What facilities or lanes does the hall have?
Mention the structures that matter most to the user, such as courts, lanes, rinks, stands or changing rooms.

3. What kind of use does the hall suit?
State in general terms whether the hall suits recreational, training, competition, event or public-session use.

4. Is technical information needed?
Use technical dimensions only if they are genuinely useful to the user."}
    :usable-info "Laji, ratojen tai kenttien määrä, kaukalo, katsomo, pukutilat, yleisövuorot yleisellä tasolla, varusteiden vuokraus yleisellä tasolla."
    :avoid {:fi "Rakennustekniset yksityiskohdat, energiatekniset ratkaisut, markkinointipuhe, kattavat hinnastot, osoitteet ja varausohjeet."
            :se "Byggnadstekniska detaljer, energitekniska lösningar, marknadsföringsspråk, omfattande prislistor, adresser och bokningsanvisningar."
            :en "Construction details, energy solutions, marketing language, comprehensive price lists, addresses and booking instructions."}
    :type-codes #{1630 2230 2240 2250 2260 2270 2280 2290 2295 2510 2520 2530 2610 4210}}

   :vesiliikunta-ja-vesiurheilupaikat
   {:name {:fi "Vesiliikunta- ja vesiurheilupaikat"
           :se "Vattenmotions- och vattensportplatser"
           :en "Swimming and water sports facilities"}
    :definition "Uimiseen, vesiliikuntaan, vesillä liikkumiseen tai vesiurheiluun tarkoitetut sisä- ja ulkopaikat."
    :summary {:fi "1. Mikä paikka on?
Kerro, onko kyseessä esimerkiksi uimahalli, uimaranta, talviuintipaikka, melontakeskus, soutustadion tai purjehdusalue.

2. Mitä paikassa voi tehdä?
Mainitse uiminen, vesiliikunta, melonta, soutu, purjehdus, vesihiihto tai muu keskeinen käyttö.

3. Mikä rakenteissa tai olosuhteissa on käyttäjälle tärkeää?
Mainitse esimerkiksi altaat, ranta, laituri, radat tai talviuintimahdollisuus."
              :se "1. Vad är platsen?
Berätta om det till exempel är en simhall, en badstrand, en vinterbadplats, ett paddlingscentrum, en roddstadion eller ett seglingsområde.

2. Vad kan man göra på platsen?
Nämn simning, vattenmotion, paddling, rodd, segling, vattenskidåkning eller annan central användning.

3. Vad är viktigt för användaren i konstruktionerna eller förhållandena?
Nämn till exempel bassänger, strand, brygga, banor eller möjlighet till vinterbad."
              :en "1. What is this place?
State whether it is, for example, a swimming pool, a beach, a winter swimming place, a canoeing centre, a rowing stadium or a sailing area.

2. What can you do there?
Mention swimming, water exercise, canoeing, rowing, sailing, water skiing or other main use.

3. What matters to the user about the structures or conditions?
Mention, for example, the pools, the shore, a jetty, lanes or the option of winter swimming."}
    :description {:fi "1. Mihin vesiliikuntaan tai vesiurheiluun paikka soveltuu?
Kuvaa käyttötarkoitus.

2. Mitä rakenteita tai tiloja paikassa on?
Mainitse tärkeimmät altaat, rannat, laiturit, radat, pukutilat tai muut käyttäjän kannalta olennaiset kohteet.

3. Onko käyttö olosuhderiippuvaista?
Mainitse tämä yleisellä tasolla esimerkiksi uimarannoilla, melontareiteillä ja talviuintipaikoissa.

4. Mitkä mitat auttavat käyttäjää?
Mainitse esimerkiksi altaan pituus tai ratojen määrä, jos tieto on hyödyllinen."
                  :se "1. För vilken vattenmotion eller vattensport lämpar sig platsen?
Beskriv användningsändamålet.

2. Vilka konstruktioner eller utrymmen finns på platsen?
Nämn de viktigaste bassängerna, stränderna, bryggorna, banorna, omklädningsrummen eller andra objekt som är väsentliga för användaren.

3. Är användningen beroende av förhållandena?
Nämn detta på en allmän nivå till exempel vid badstränder, paddlingsleder och vinterbadplatser.

4. Vilka mått hjälper användaren?
Nämn till exempel bassängens längd eller antalet banor, om uppgiften är till nytta."
                  :en "1. What water exercise or water sport is the place suited to?
Describe the intended use.

2. What structures or facilities does the place have?
Mention the main pools, shores, jetties, lanes, changing rooms or other features essential to the user.

3. Does use depend on conditions?
Mention this in general terms, for example at beaches, on canoeing routes and at winter swimming places.

4. Which dimensions help the user?
Mention, for example, the length of the pool or the number of lanes, if the information is useful."}
    :usable-info "Altaat, radat, ranta, laituri, sauna yleisellä tasolla, pukutilat, talviuintimahdollisuus, olosuhderiippuvuus, soveltuvuus lapsille tai omatoimiseen liikuntaan."
    :avoid {:fi "Hinnat, pantit, peseytymisohjeiden pitkät kuvaukset, uimaveden näytetulosten yksityiskohdat, osoitteet, linkit ja aukioloaikataulukot."
            :se "Priser, panter, långa tvättanvisningar, detaljer om badvattenprovresultat, adresser, länkar och tabeller över öppettider."
            :en "Prices, deposits, lengthy washing instructions, details of bathing water sample results, addresses, links and tables of opening hours."}
    :type-codes #{3110 3120 3130 3210 3220 3230 3240 3250 5110 5120 5130 5140 5150 5160}}

   :erityisliikuntapaikat
   {:name {:fi "Erityisliikuntapaikat"
           :se "Specialidrottsplatser"
           :en "Special sports facilities"}
    :definition "Laji- tai harrastuskohtaiset liikuntapaikat, joiden käyttöön voi liittyä erityisiä välineitä, osaamista, turvallisuutta, lupia tai seurojen toimintaa."
    :summary {:fi "1. Mikä erityisliikuntapaikka on?
Kerro laji tai käyttötarkoitus.

2. Kenelle paikka on tarkoitettu?
Kerro tarvittaessa, soveltuuko paikka harrastajille, seuroille, omatoimiseen harjoitteluun tai ohjattuun toimintaan.

3. Liittyykö käyttöön erityisosaamista tai turvallisuutta?
Mainitse tämä lyhyesti ja yleisellä tasolla, jos se on olennaista."
              :se "1. Vilken specialidrottsplats är det?
Berätta gren eller användningsändamål.

2. För vem är platsen avsedd?
Berätta vid behov om platsen lämpar sig för utövare, föreningar, självständig träning eller ledd verksamhet.

3. Kräver användningen särskild kompetens eller säkerhet?
Nämn detta kort och på en allmän nivå om det är väsentligt."
              :en "1. What kind of special sports facility is it?
State the sport or the intended use.

2. Who is the place intended for?
If relevant, say whether the place suits enthusiasts, clubs, independent training or supervised activity.

3. Does use involve special skills or safety considerations?
Mention this briefly and in general terms if it is essential."}
    :description {:fi "1. Mihin lajiin tai harrastukseen paikka on tarkoitettu?
Kuvaa käyttötarkoitus selkeästi.

2. Millainen paikka on?
Mainitse keskeiset radat, kentät, alueet tai rakenteet.

3. Miten paikkaa käytetään?
Voit kertoa yleisellä tasolla, jos käyttö tapahtuu seuran, luvan, varauksen, ohjauksen tai omatoimisen harjoittelun kautta.

4. Mitä turvallisuudesta pitää sanoa?
Mainitse vain yleisesti, että käyttö edellyttää lajin turvallisuusohjeiden noudattamista, jos tieto on tarpeen."
                  :se "1. För vilken gren eller hobby är platsen avsedd?
Beskriv användningsändamålet tydligt.

2. Hurdan är platsen?
Nämn de centrala banorna, planerna, områdena eller konstruktionerna.

3. Hur används platsen?
Du kan berätta på en allmän nivå om användningen sker via en förening, ett tillstånd, en bokning, ledd verksamhet eller självständig träning.

4. Vad ska sägas om säkerheten?
Nämn endast allmänt att användningen förutsätter att grenens säkerhetsanvisningar följs, om uppgiften behövs."
                  :en "1. Which sport or pastime is the place intended for?
Describe the intended use clearly.

2. What kind of place is it?
Mention the key tracks, pitches, areas or structures.

3. How is the place used?
You may say in general terms whether use goes through a club, a permit, a booking, supervision or independent training.

4. What should be said about safety?
Mention only in general terms that use requires following the safety instructions for the sport, if the information is needed."}
    :usable-info "Laji, keskeiset radat tai suorituspaikat, käyttäjäryhmä, turvallisuusluonne yleisesti, omatoiminen tai ohjattu käyttö yleisellä tasolla, lajitaitojen tarve."
    :avoid {:fi "Yksityiskohtaiset lupaehdot, seurojen sopimusvastuut, pitkät turvallisuusmääräykset, kattavat tekniset luettelot, hinnat, linkit ja osoitteet."
            :se "Detaljerade tillståndsvillkor, föreningarnas avtalsansvar, långa säkerhetsföreskrifter, omfattande tekniska förteckningar, priser, länkar och adresser."
            :en "Detailed permit conditions, clubs' contractual responsibilities, lengthy safety regulations, comprehensive technical lists, prices, links and addresses."}
    :type-codes #{1610 1650 4510 4710 4720 4810 4820 4830 4840 5210 5310 5320 5330 5340 5350 5360 5370 6110 6120 6130 6140 6150 6210 6220}}})

(def lipas-assigned-type-codes
  "Type codes the DVV sheet does not list. Grouped here by LIPAS on the
   nearest fit; confirm with DVV before treating them as source-approved."
  #{2530 4230 4240 5130})

(def group-by-type-code
  "type-code -> group key."
  (into {} (for [[k {:keys [type-codes]}] groups
                 type-code type-codes]
             [type-code k])))

(defn for-type-code
  "Guidance map for a sports-site type code, or nil when the type has none."
  [type-code]
  (get groups (group-by-type-code type-code)))

(defn text
  "Localized guidance text for `field` (:summary, :description or :avoid),
   falling back to Finnish when the locale has no translation."
  [type-code field locale]
  (when-let [m (get (for-type-code type-code) field)]
    (or (get m (keyword locale)) (:fi m))))

(def general-principles
  "Cross-cutting principles that apply to every group (sheet \"Yleiset\n   periaatteet\"). Assistant-facing background; Finnish only."
  [{:topic "Tiivistelmä"
    :question "Ymmärtääkö käyttäjä 1–2 virkkeestä, mikä paikka on ja mitä siellä voi tehdä?"
    :guidance "Kirjoita lyhyt ja konkreettinen tiivistelmä. Nimeä liikuntapaikan tyyppi ja tärkein käyttötarkoitus."
    :avoid "Aukioloajat, hinnat, varausohjeet, yhteystiedot ja markkinointipuhe."}
   {:topic "Kuvaus"
    :question "Saako käyttäjä kuvauksesta riittävän kuvan paikasta?"
    :guidance "Kerro mitä paikassa voi tehdä, millainen paikka on ja mitkä ominaisuudet ovat käyttäjälle olennaisia."
    :avoid "Palvelun, organisaation, ylläpidon tai kunnan toiminnan yleinen kuvailu."}
   {:topic "Mitat ja numerot"
    :question "Auttaako numero käyttäjää valitsemaan tai ymmärtämään paikan?"
    :guidance "Mainitse esimerkiksi reitin pituus, frisbeegolfradan väylien määrä, altaan pituus tai kentän olennainen ominaisuus."
    :avoid "Tekniset mittaluettelot ja numerot, joilla ei ole käyttäjän kannalta selvää merkitystä."}
   {:topic "Maksullisuus"
    :question "Kuuluuko tieto omaan maksullisuuskenttään?"
    :guidance "Älä kirjoita hintoja kuvaukseen. Mainitse maksullisuus kuvauksessa vain, jos se on välttämätöntä paikan käyttötavan ymmärtämiseksi."
    :avoid "Hinnastot, pantit, korttityypit ja maksutavat."}
   {:topic "Varaukset ja käyttöohjeet"
    :question "Tarvitseeko käyttäjä tätä tietoa ymmärtääkseen paikan käyttötavan?"
    :guidance "Voit mainita yleisesti, jos käyttö edellyttää varausta, vuoroa, lupaa tai seuran toimintaa. Yksityiskohtaiset toimintaohjeet kuuluvat muualle."
    :avoid "Varausjärjestelmän käyttöohjeet, hakuaikojen kuvaukset ja pitkät sääntöluettelot."}
   {:topic "Sävy"
    :question "Onko teksti selkeää yleiskieltä?"
    :guidance "Kirjoita neutraalisti, napakasti ja käyttäjän näkökulmasta."
    :avoid "Upea, ainutlaatuinen, huippumoderni, maailman paras ja muu mainosmainen ilmaisu."}])
