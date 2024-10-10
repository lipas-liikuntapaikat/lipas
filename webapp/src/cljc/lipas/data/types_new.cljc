(ns lipas.data.types-new
  "Type codes went through a major overhaul in the summer of 2024. This
  namespace represents the changes made."
  (:require
   [lipas.data.types-old :as old]
   [lipas.utils :as utils]))

(def main-categories
  old/main-categories)

(def sub-categories
  old/sub-categories)

(def all1
  (-> old/all
      ;;; Virkistys ;;;

      ;; merge 102 -> 101
      (assoc-in [102 :status] "deprecated")
      (assoc-in [101 :name :fi] "Lähi-/ulkoilupuisto")
      (assoc-in [101 :description :fi] "Sijaitsevat taajamissa, max 1 km asutuksesta. Toimivat kävely-, leikki-, oleskelu-, lenkkeily- ja pyöräilypaikkoina. Kaavamerkintä V tai VL. Esimerkkejä lähi- tai ulkoilupuistoista: leikkipuistot, liikennepuistot, perhepuistot, oleskelupuistot, keskuspuistot ja kirkkopuistot.")

      ;; 101 prop updates
      (assoc-in [101 :props :water-point] {:priority 0})
      (assoc-in [101 :props :toilet?] {:priority 0})
      (update-in [101 :props] dissoc :school-use? :free-use?)

      ;; Merge 104 -> 103
      (assoc-in [104 :status] "deprecated")
      (assoc-in [103 :name :fi] "Ulkoilu-/virkistysalue")
      (assoc-in [103 :description :fi] "Voivat sijaita taajaman reunoilla, vyöhykkeittäin taajaman sisällä tai taajaman ulkopuolella. Kohteissa voi olla myös taajamasta lähteviä tai taajamaan palaavia reittejä tai polku- ja reittiverkosto. Kohteet sisältävät vaihtelevaa maastoa ja luonnonmukaisia tai puistomaisia alueita. Kohteet voivat myös sijaita vesialuiden lähellä kuten rannoilla tai saarissa. Kohteiden pääasiallinen käyttö on retkeilyä ja luonnossa virkistäytymistä, mutta niitä voidaan käyttää monipuolisesti erilaisen liikunnan kuten hiihdon, lenkkeilyn tai uinnin harrastamiseen.  Kaavamerkintä esim. VR. HUOM! Uusien liikunta- ja ulkoilupaikkojen lisäksi Ulkoilu-/virkistysalueluokka sisältää ennen vuotta 2024 Ulkoilualueet ja Retkeilyalueet tyyppiluokkiin lisätyt olosuhteet")
      (update-in [103 :props] dissoc :playground?)

      ;; 103 prop updates
      (assoc-in [103 :props :water-point] {:priority 0})
      (update-in [103 :props] dissoc :school-use? :free-use?)

      ;; merge 108 -> 106
      (assoc-in [108 :status] "deprecated")
      (assoc-in [106 :name :fi] "Monikäyttöalueet ja virkistysmetsät, joissa on virkistyspalveluita")
      (assoc-in [106 :description :fi] "Monikäyttöalueiksi voidaan nimittää jokaisenoikeuksin ulkoiluun käytettäviä maa- ja metsätalousalueita. Monikäyttöalueita ovat erityisesti rakentamattomat rannat ja taajamien läheiset maa- ja metsätalousalueet. Kaavamerkintä MU. Virkistysmetsien metsätaloudessa on huomioitu mm. maisemalliset arvot ja ne on perustettu metsähallituksen päätöksellä. Virkistysmetsien osalta Lipas-aineisto perustuu Metsähallituksen tietoihin.")

      ;; 106 prop changes
      (update-in [106 :props] dissoc :school-use? :free-use? :playground?)

      ;; 107 prop changes
      (update-in [107 :props] dissoc :school-use?)
      (assoc-in [107 :props :toilet?] {:priority 0})
      (assoc-in [107 :description :fi] "Matkailupalvelujen alueet ovat matkailua palveleville toiminnoille varattuja alueita, jotka sisältävät myös sisäiset liikenneväylät ja -alueet, alueen toimintoja varten tarpeelliset palvelut ja virkistysalueet sekä yhdyskuntateknisen huollon alueet. Kohteet voivat toimia myös retkeilyauto- ja pyörämatkailijoiden tauko- ja yöpymispaikkoina. Kaavamerkintä RM.")

      ;; 109 prop changes
      (update-in [109 :props] dissoc :school-use? :free-use?)

      ;; 110 prop changes
      (update-in [110 :props] dissoc :school-use? :free-use?)

      ;; 111 prop changes
      (update-in [111 :props] dissoc :school-use? :free-use?)
      (assoc-in [111 :description :fi] "Kansallispuistot ovat luonnonsuojelualueita, joiden perustamisesta ja tarkoituksesta on säädetty lailla. Kansallispuistoissa on merkittyjä reittejä, luontopolkuja ja tulentekopaikkoja. Kansallispuistoissa voi myös yöpyä, sillä niissä on telttailualueita tai yöpymiseen tarkoitettuja rakennuksia. LIPAS-aineisto perustuu Metsähallituksen tietoihin.")

      ;; 112 prop changes
      (update-in [112 :props] dissoc :school-use? :free-use?)
      (assoc-in [112 :description :fi] "Muut luonnonsuojelualueet kuin kansallispuistot. Tietoja kerätään vain sellaisilta luonnonsuojelualueilta ja luonnonpuistoilta, joiden virkistyskäyttö on mahdollista. Esim. kunta- tai yksityisomisteisille maille perustetut suojelualueet. Kaavamerkintä S, SL.")

      ;; Merge 205 -> 203
      (assoc-in [205 :status] "deprecated")

      ;; 203 Veneilyn palvelupaikka
      (assoc-in [203 :props :boating-service-class] {:priority 1})
      (assoc-in [203 :description :fi] "Kohteessa on veneilyyn liittyviä palveluita kuten säilytysmahdollisuus, vesillelaskupaikka tai veneen kiinnitysmahdollisuus. Kohteelle määritetään venesatamaluokka, jonka palveluvarustus kuvataan lisätiedoissa. Jos kyse on melontalaiturista, se kirjataan kyseisen laituriluokan alle. Kohde tulee merkitä tärkeimmän laiturin läheisyyteen, jos sellainen kohteessa on.")

      ;; 207 -> lois
      ;; TODO
      (assoc-in [207 :status] "deprecated")

      ;; 201 kalastuspiste prop changes
      (assoc-in [201 :props :pier?] {:priority 0})
      (assoc-in [201 :props :customer-service-point?] {:priority 0})
      (assoc-in [201 :props :equipment-rental?] {:priority 0})
      (update-in [201 :props] dissoc :school-use?)

      ;; 113 kalastusalue prop changes
      (assoc-in [113 :props :pier?] {:priority 0})
      (assoc-in [113 :props :customer-service-point?] {:priority 0})
      (assoc-in [113 :props :equipment-rental?] {:priority 0})
      (update-in [113 :props] dissoc :school-use?)

      ;; 202 telttailu
      (update-in [202 :props] dissoc :school-use? :free-use?)
      (assoc-in [202 :props :water-point] {:priority 0})

      ;; 203 veneilyn palvelupaikka
      (update-in [203 :props] dissoc :school-use? :free-use?)
      (assoc-in [203 :props :water-point] {:priority 0})
      (assoc-in [203 :props :customer-service-point?] {:priority 0})
      (assoc-in [203 :props :accessibility-info] {:priority 0})

      ;; 204 Luontotorni
      (update-in [204 :props] dissoc :school-use? :free-use?)

      ;; 206 ruoanlaittopaikka
      (assoc-in [206 :name :fi] "Ruoanlaitto- / tulentekopaikka")
      (assoc-in [206 :description :fi] "Rakennettu tulentekopaikka tai keittokatos. Kohde voi olla esimerkiksi maasta eristetty tulisija, jossa on katos tai tulisija avotulelle. Tulentekopaikan tarkempi kuvaus ja tieto mahdollisista rajoituksista lisätietoihin.")
      (update-in [206 :props] dissoc :school-use? :free-use?)

      ;; 301 Laavu, kota kammi
      (assoc-in [301 :description :fi] "Päiväsaikainen levähdyspaikka retkeilijöille. Esimerkiksi kodalla tarkoitetaan kotamallista sääsuojaa tai levähdyspaikkaa ja laavu on kaltevakattoinen sääsuoja, joka sisältää tulipaikan. Lisätietoihin merkitään tieto tulentekopaikasta.")
      (update-in [301 :props] dissoc :school-use? :free-use?)
      (assoc-in [301 :props :water-point] {:priority 0})

      ;; 302 Tupa
      (update-in [302 :props] dissoc :school-use? :free-use?)
      (assoc-in [302 :props :water-point] {:priority 0})

      ;; 304 Ulkoilu/hiihtomaja
      (assoc-in [304 :description :fi] "Tavallisen arkiliikunnan taukopaikka, päiväkäyttöön. Lisätietoihin merkitään kohteessa olevat palvelut, esim. kahvio, vuokrauspiste tai opastuspiste.")
      (update-in [304 :props] dissoc :school-use? :free-use?)
      (assoc-in [304 :props :customer-service-point?] {:priority 0})
      (assoc-in [304 :props :equipment-rental?] {:priority 0})))

(def all2
  (-> all1
      ;;; Ulkokentät ;;;

      ;; 1120 lähiliikuntapaikka
      (assoc-in [1120 :description :fi] "Lähiliikuntapaikka on tarkoitettu päivittäiseen ulkoiluun ja liikuntaan. Se sijaitsee asutuksen läheisyydessä, on pienimuotoinen ja alueelle on vapaa pääsy. Yleensä tarjolla on erilaisia suorituspaikkoja. Suorituspaikat tulee tallentaa omiksi liikuntapaikoikseen (esim. pallokenttä, ulkokuntosali tai parkouralue). Lähiliikuntapaikka voi olla myös koulun tai päiväkodin piha, jos liikuntapaikan käyttö on mahdollista kouluajan ulkopuolella.")
      (update-in [1120 :props] dissoc :free-use?)

      ;; <new type> 1190 Pulkkamäki
      (assoc 1190
             {:name          {:fi "Pulkkamäki" :se "Pulkabacke" :en "Sledding hill"}
              :description
              {:fi "Yleinen mäenlaskuun esimerkiksi pulkalla tai liukurilla tarkoitettu mäki. Kohde on ylläpidetty ja hoidettu ja se voi muodostua luonnon mäestä tai rakennetuista kumpareista."
               :se "En allmän backe avsedd för åkning med till exempel pulka eller stjärtlapp. Backen är underhållen och skött och kan bestå av en naturlig backe eller konstruerade högar."
               :en "A common hill intended for sledding with, for example, a sled or a slider. The hill is maintained and taken care of, and it can consist of a natural hill or constructed mounds."}
              :geometry-type "Point"
              :tags          {:fi ["pulkkailu" "pulkka" "mäenlasku"]}
              :main-category 1000
              :sub-category  1100
              :status        "active"
              :type-code     1190
              :props
              {:ligthing?                          {:priority 0}
               :school-use?                        {:priority 0}
               :may-be-shown-in-harrastuspassi-fi? {:priority 0}
               :toilet?                            {:priority 0}}})

      ;; 1130 ulkokuntoilupaikka
      (assoc-in [1130 :description :fi] "Ulkokuntoilupaikka on esimerkiksi kuntoilulaitteita, voimailulaitteita tai kuntoportaat sisältävä liikuntapaikka. Kohde voi olla osa liikuntapuistoa, liikuntareitin varrella oleva kuntoilupaikka tai ns. \"ulkokuntosali\".")
      (assoc-in [1130 :props :fitness-stairs-length-m] {:priority 0})

      ;; 1140 Parkour-alue
      (assoc-in [1140 :props :highest-obstacle-m] {:priority 0})
      (assoc-in [1140 :props :climbing-wall?] {:priority 0})
      (assoc-in [1140 :props :lighting-info] {:priority 0})

      ;; 1150 skeitti/rullaluistelupaikka
      (assoc-in [1150 :description :fi] "Rullaluistelua, skeittausta, potkulautailua varten varustettu paikka. Ominaisuustiedoissa tarkemmat tiedot kohteesta.")

      ;; 1160 Pyöräilyalue
      (assoc-in [1160 :props :accessibility-info] {:priority 0})
      (assoc-in [1160 :props :stand-capacity-person] {:priority 0})
      (assoc-in [1160 :props :covered-stand-person-count] {:priority 0})
      (assoc-in [1160 :props :customer-service-point?] {:priority 0})
      (assoc-in [1160 :props :toilet?] {:priority 0})

      ;; 1170 Pyöräilyrata / velodromi
      (assoc-in [1170 :name :fi] "Pyöräilyrata / Velodromi")

      ;; 1210 Yleisurheilun harjoituspaikka
      (assoc-in [1210 :description :fi] "Yleisurheilun harjoitusalueeksi merkitään kohde, jossa on yleisurheilun harjoitteluun soveltuvia suorituspaikkoja, esim. kenttä, ratoja tai eri lajien suorituspaikkoja, mutta ei virallisen yleisurheilukentän kaikkia suorituspaikkoja. Lyhytrataiset (juoksurata alle 400 m) yleisurheilukentät tallennettaan yleisurheilun harjoitusalueeksi.")

      ;; 1220 Yleisurheilukenttä
      (assoc-in [1220 :description :fi] "Hyvin varusteltu yleisurheilukenttä. Yleissurheilukentällä on ratoja ja yleisurheilun suorituspaikkoja. Myös kisakäyttö on mahdollista. Lyhytrataiset (juoksurata alle 400 m) yleisurheilukentät tallennettaan yleisurheilun harjoitusalueeksi. Yleisurheilukentällä sijaitseva jalkapallon tai muun lajin keskeinen suorituspaikka merkitään omaksi liikuntapaikakseen (esim. jalkapallostadion tai pallokenttä). ")

      ;; 1310 Koripallokenttä
      (update-in [1310 :props] dissoc :fields-count)
      (assoc-in [1310 :props :height-of-basket-or-net-adjustable?] {:priority 0})
      (assoc-in [1310 :props :water-point] {:priority 0})

      ;; 1320 Lentopallokenttä
      (assoc-in [1320 :description :fi] "Lentopalloon varustettu kenttä, jossa on kiinteät lentopallotolpat.")
      (update-in [1320 :props] dissoc :fields-count)
      (assoc-in [1320 :props :height-of-basket-or-net-adjustable?] {:priority 0})
      (assoc-in [1320 :props :water-point] {:priority 0})

      ;; 1330 Beachvolleykenttä
      (assoc-in [1330 :description :fi] "Rantalentopallokenttä, pehmeä alusta. Kohde voi sijaita muuallakin kuin rannalla.")
      (assoc-in [1330 :name :fi] "Beachvolley- / rantalentopallokenttä")
      (update-in [1330 :props] dissoc :fields-count)
      (assoc-in [1330 :props :height-of-basket-or-net-adjustable?] {:priority 0})
      (assoc-in [1330 :props :water-point] {:priority 0})

      ;; 1340 Pallokenttä
      (assoc-in [1340 :description :fi] "Palloiluun tarkoitettu kenttä, jonka pintamateriaali on esim. hiekka, nurmi tai hiekkatekonurmi. Kentällä on mahdollista pelata yhtä tai useampaa palloilulajia. Kentän koko merkitään lisätietoihin.  Kevyt poistettava kate on mahdollinen.")
      (update-in [1340 :props] dissoc :fields-count :kiosk?)
      (assoc-in [1340 :props :customer-service-point?] {:priority 0})
      (assoc-in [1340 :props :winter-usage?] {:priority 0})
      (assoc-in [1340 :props :water-point] {:priority 0})

      ;; 1350 Jalkapallostadion
      (update-in [1350 :props] dissoc :fields-count :kiosk? :finish-line-camera?)
      (assoc-in [1350 :props :customer-service-point?] {:priority 0})
      (assoc-in [1350 :props :winter-usage?] {:priority 0})
      (assoc-in [1350 :props :water-point] {:priority 0})

      ;; 1360 Pesäpallokenttä
      (assoc-in [1360 :description :fi] "Pesäpalloon tarkoitettu kenttä. Jos kentän yhteydessä on katsomoita, lisätään kentän nimeen stadion-sana. Vähintään kansallisen tason pelipaikka. Pintamateriaali on esim. hiekka, hiekkatekonurmi tai muu synteettinen päällyste. Kentän koko on vähintään 50 x 100 m.")
      (update-in [1360 :props] dissoc :fields-count :kiosk?)
      (assoc-in [1360 :props :customer-service-point?] {:priority 0})
      (assoc-in [1360 :props :water-point] {:priority 0})

      ;; 1370 Tenniskenttä
      (assoc-in [1370 :description :fi] "Tennikseen tarkoitettu kenttä. Mahdollinen lyöntiseinä ja kentän pintamateriaali merkitään lisätietoihin.")
      (assoc-in [1370 :name :fi] "Tenniskenttä")
      (update-in [1370 :props] dissoc :fields-count :heating?)
      (assoc-in [1370 :props :changing-rooms?] {:priority 0})
      (assoc-in [1370 :props :water-point] {:priority 0})

      ;; 1380 Rullakiekkokenttä
      (update-in [1380 :props] dissoc :fields-count)
      (assoc-in [1380 :props :water-point] {:priority 0})

      ;; 1390 Padelkenttä
      (assoc-in [1390 :description :fi] "Yksi tai useampi padelkenttä ulkona. Pintamateriaali hiekkatekonurmi. Lajivaatimusten mukaiset seinät. Voi olla myös katettu.")
      (assoc-in [1390 :name :fi] "Padelkenttä")
      (update-in [1390 :props] dissoc :fields-count)
      (assoc-in [1390 :props :water-point] {:priority 0})

      ;; 1395 Pöytätennisalue
      (update-in [1395 :props] dissoc :area-m2)
      (assoc-in [1395 :props :water-point] {:priority 0})

      ;; 1510 tekojkääkenttä
      (assoc-in [1510 :description :fi] "Koneellisesti / keinotekoisesti jäähdytetty ulkokenttä. Kentän koko ja varustustiedot löytyvät lisätiedoista. Käytössä talvikaudella.")
      (assoc-in [1510 :name :fi] "Tekojääkenttä / Tekojäärata")

      ;; 1520 Luistelukenttä
      (assoc-in [1520 :description :fi] "Luisteluun tarkoitettu luonnonmukainen kenttä. Jäädytetään käyttökuntoon talvikaudelle.")

      ;; 1540 Pikaluistelurata
      (assoc-in [1540 :description :fi] "Pikaluisteluun varusteltu luistelurata. Radan koko ja pituus lisätään ominaisuustietoihin. Käytössä talvikaudella.")

      ;; 1550 Luistelureitti
      (assoc-in [1550 :description :fi] "Luisteluun tarkoitettu luonnonjäälle tai maalle rakennettava huollettu luistelureitti. Rakennetaan talvisin samalle alueelle. ")

      ;; 1610 Golfin harjoitusalue
      (assoc-in [1610 :description :fi] "Golfin harjoittelua varten varustettu alue. Harjoitusalue voi sisältää useampia suorituspaikkoja kuten rangen ja puttausviheriön. Harjoitusalue sijaitsee ulkona.")

      ;; 1620 Golfkenttä (piste) -> 1650 golfeknttä (alue)
      (assoc-in [1620 :name :fi] "Golfkenttä (piste)")
      (assoc 1650 (-> (get old/all 1620)
                      (assoc-in [:name :fi] "Golfkenttä (alue)")
                      (assoc-in [:description :fi] "Ensisijaisesti golfin pelaamiseen tarkoitettu alue kesäkaudella. Reikien määrä merkitään lisätietoihin.")
                      (assoc-in [:type-code] 1650)
                      (assoc-in [:geometry-type] "Polygon")
                      (assoc-in [:props :customer-service-point?] {:priority 0})))

      ;; 1630 Golfin harjoitushalli
      (assoc-in [1630 :main-category] 2000)
      (assoc-in [1630 :sub-category] 2200)
      (assoc-in [1630 :props :customer-service-point?] {:priority 0})))

(def all3
  (-> all2
            ;;; Jääurheilu ;;;

      ;; 1510 Tekojääkenttä
      (assoc-in [1510 :props :changing-rooms-m2] {:priority 0})
      (assoc-in [1510 :props :customer-service-point?] {:priority 0})

      ;; 1520 Luistelukenttä
      (assoc-in [1520 :props :changing-rooms-m2] {:priority 0})
      (assoc-in [1520 :props :customer-service-point?] {:priority 0})

      ;; 1530  Kaukalo
      (assoc-in [1530 :description :fi] "Luisteluun, jääkiekkoon, kaukalopalloon, curlingiin tai muuhun jääurheiluun tarkoitettu kaukalo. Käytössä talvikaudella.")
      (assoc-in [1530 :props :changing-rooms-m2] {:priority 0})

      ;; 1540 Pikaluistelurata
      (assoc-in [1540 :props :changing-rooms-m2] {:priority 0})
      (assoc-in [1540 :props :customer-service-point?] {:priority 0})
      (assoc-in [1540 :props :changing-rooms-m2] {:priority 0})

      ;; 1550 Luistelureitti
      (assoc-in [1550 :props :customer-service-point?] {:priority 0})

      ;; 1560 Alamäkiluistelurata

      ;;; Yleisurheilu ;;;

      ;; 1210 yleisurheilun harjoitusalue

      ;; 1220 Yleisurheilukenttä
      (assoc-in [1220 :props :customer-service-point?] {:priority 0})

      ;;; Golfkentät ;;;

      ;; 1610 Golfin harjoitusalue
      (assoc-in [1610 :props :customer-service-point?] {:priority 0})

      ;; 1620 Golfkenttä
      (assoc-in [1620 :description :fi] "Ensisijaisesti golfin pelaamiseen tarkoitettu alue kesäkaudella. Reikien määrä merkitään lisätietoihin.")

      (assoc-in [1620 :props :customer-service-point?] {:priority 0})

      ;; 1630 Golfin harjoitushalli

      ;; 1640 Ratagolf
      (assoc-in [1640 :props :customer-service-point?] {:priority 0})

      ;;; Moottoriirheilu ;;;

      ;; 5310 "Moottoriurheilukeskus"
      (assoc-in [5310 :props :winter-usage?] {:priority 0})

      ;; 5320 "Moottoripyöräilyalue"
      (assoc-in [5320 :props :winter-usage?] {:priority 0})

      ;; 5330 "Moottorirata"
      (assoc-in [5330 :props :winter-usage?] {:priority 0})

      ;; 5340 "Karting-rata"
      (assoc-in [5340 :props :winter-usage?] {:priority 0})

      ;; 5350 "Kiihdytysrata"
      (assoc-in [5350 :props :winter-usage?] {:priority 0})

      ;; 5360 "Jokamies- ja rallicross-rata"
      (assoc-in [5360 :props :winter-usage?] {:priority 0})

      ;; Remove :kiosk? and add :lighthing-info to everywhere where :lighthing? is asked
      (->> (reduce-kv (fn [m k v]
                        (assoc m k
                               (cond-> v
                                 true
                                 (update :props dissoc :kiosk?)

                                 (contains? (:props v) :ligthing?)
                                 (assoc-in [:props :ligthing-info] {:priority 0}))))
                      {}))))

(def all4
  (-> all3
      ;;; Sisä- ja vesiliikunta ;;;

      ;; Add new type 2225 Sisäleikki-/sisäaktiviteettipuisto
      (assoc 2225 {:status        "active"
                   :geometry-type "Point"
                   :type-code     2225
                   :main-category 2000
                   :sub-category  2200
                   :tags          {:fi []}})

      ;; Add new type 2620 Biljardisali
      (assoc 2620 {:status        "active"
                   :geometry-type "Point"
                   :type-code     2620
                   :main-category 2000
                   :sub-category  2600
                   :tags          {:fi []}})

      ;; Add new type 3250 Vesiurheilukeskus
      (assoc 3250 {:status        "active"
                   :geometry-type "Point"
                   :type-code     3250
                   :main-category 3000
                   :sub-category  3200
                   :tags          {:fi []}})

      ;; Generated from template
      (assoc-in [2110 :description :fi] "Erilasia liikuntapalveluita ja -tiloja tarjoava kuntokeskus. Kohteessa voi olla esimerkiksi kuntosali- ja ryhmäliikuntatiloja.")
      (update-in [2110 :props] dissoc :boxing-rings-count)
      (update-in [2110 :props] dissoc :free-use?)
      (assoc-in [2110 :props :free-customer-use?] {:priority 0})
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2110 :props :customer-service-point?] {:priority 0})
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2120 :props :customer-service-point?] {:priority 0})
      (assoc-in [2120 :props :free-customer-use?] {:priority 0})
      (update-in [2120 :props] dissoc :free-use?)
      (assoc-in [2130 :description :fi] "Painonnostoon tai toiminnalliseen voimaharjoitteluun varustettu kuntoilutila tai voimailusali. Esimerkiksi crossfit- ja painonnostosalit.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2130 :props :customer-service-point?] {:priority 0})
      (update-in [2130 :props] dissoc :free-use?)
      (assoc-in [2130 :props :free-customer-use?] {:priority 0})
      (assoc-in [2140 :description :fi] "Sali, jossa voi harrastaa kamppailulajeja kuten painia, nyrkkeilyä tai budolajeja. Tilan koko ja varustus kerrotaan kohteen lisätiedoissa.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2140 :props :customer-service-point?] {:priority 0})
      ;;  Toteutus esim: Valinta Kyllä/Ei ->  Jos kyllä, täydennettävä kenttä "Tila voidaan jakaa x osaan" (voidaan syöttää lukuarvo esim. 2, joka tarkoittaa että tila voidaan jakaa kahteen osaan).
      (assoc-in [2140 :props :space-divisible] {:priority 0})
      (update-in [2140 :props] dissoc :free-use?)
      (assoc-in [2150 :description :fi] "Muun rakennuksen yhteydessä oleva avoin liikuntatila, joka sopii monipuolisesti erilaisten liikuntamuotojen harrastamiseen. Salin liikuntapinta-ala vaihtelee tyypillisesti alle 300 neliöstä noin 750 neliöön. Esim. koulurakennuksessa sijaitseva liikuntasali.")
      ;;  Toteutus esim: Valinta Kyllä/Ei ->  Jos kyllä, täydennettävä kenttä "Tila voidaan jakaa x osaan" (voidaan syöttää lukuarvo esim. 2, joka tarkoittaa että tila voidaan jakaa kahteen osaan).
      (assoc-in [2150 :props :space-divisible] {:priority 0})
      (assoc-in [2210 :description :fi] "Liikuntahalli on itsenäinen rakennus, jossa voi olla useita liikuntatiloja tai osiin jaettavissa oleva pääsali.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2210 :props :customer-service-point?] {:priority 0})
      ;;  Toteutus esim: Valinta Kyllä/Ei ->  Jos kyllä, täydennettävä kenttä "Tila voidaan jakaa x osaan" (voidaan syöttää lukuarvo esim. 2, joka tarkoittaa että tila voidaan jakaa kahteen osaan).
      (assoc-in [2210 :props :space-divisible] {:priority 0})
      (assoc-in [2210 :props :auxiliary-training-area?] {:priority 0})
      (assoc-in [2220 :description :fi] "Monitoimihalli on suuri liikuntatila, joka on merkittävä monien lajien kilpailu- ja tapahtumapaikka. Liikuntapinta-ala on suurempi kuin 5 000 m2.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2220 :props :customer-service-point?] {:priority 0})
      ;;  Toteutus esim: Valinta Kyllä/Ei ->  Jos kyllä, täydennettävä kenttä "Tila voidaan jakaa x osaan" (voidaan syöttää lukuarvo esim. 2, joka tarkoittaa että tila voidaan jakaa kahteen osaan).
      (assoc-in [2220 :props :space-divisible] {:priority 0})
      (assoc-in [2220 :props :auxiliary-training-area?] {:priority 0})
      (assoc-in [2230 :description :fi] "Ensisijaisesti jalkapalloiluun tarkoitettu halli. Halli voi olla ympärivuotisessa käytössä tai erikseen talviajalle pystytettävä kevythalli. Kentän pintamateriaalina on yleensä tekonurmi.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2230 :props :customer-service-point?] {:priority 0})
      (assoc-in [2230 :props :auxiliary-training-area?] {:priority 0})
      (assoc-in [2240 :description :fi] "Ensisijaisesti salibandyyn tarkoitettu halli. Kenttien määrä ja pintamateriaali kirjataan lisätietoihin.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2240 :props :customer-service-point?] {:priority 0})
      (assoc-in [2240 :props :auxiliary-training-area?] {:priority 0})
      (assoc-in [2250 :description :fi] "Ensisijaisesti skeittausta varten varustettu halli. Hallia voidaan käyttää bmx-pyöräilyn tai muiden soveltuvien lajien harrastamiseen.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2250 :props :customer-service-point?] {:priority 0})
      (assoc-in [2260 :description :fi] "Ensisijaisesti sulkapallon pelaamiseen tarkoitettu halli. Vapaa korkeus ilmoitetaan lisätiedoissa.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2260 :props :customer-service-point?] {:priority 0})
      (assoc-in [2270 :description :fi] "Ensisijaisesti squashin pelaamiseen tarkoitettu halli. Yksittäisen kentän mitat 9,75 m x 6,4 m. Vapaa korkeus ja pintamateriaali ilmoitetaan lisätiedoissa.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2270 :props :customer-service-point?] {:priority 0})
      (assoc-in [2280 :description :fi] "Tenniksen pelaamiseen varusteltu halli. Kenttien lukumäärä ja pintamateriaali kerrotaan kohteen lisätiedoissa.")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2280 :props :customer-service-point?] {:priority 0})
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      (assoc-in [2290 :props :customer-service-point?] {:priority 0})
      (assoc-in [2295 :description :fi] "Yksi tai useampi padelkenttä sisällä. Pintamateriaali tekonurmi (hiekkatekonurmi). Lajivaatimusten mukaiset seinät. Vapaa korkeus ilmoitetaan lisätiedoissa.")
      ;;  Uusi tyyppiluokka
      (assoc-in [2225 :name :fi] "Sisäleikki-/sisäaktiviteettipuisto")
      ;;  Uusi tyyppiluokka
      (assoc-in [2225 :description :fi] "Sisäleikkipuistot ovat yleensä pienille lapsille tarkoitettuja liikunnallisia leikkipaikkoja. Sisäaktiviteettipuistot ovat tyypillisesti lapsille ja nuorille tarkoitettuja liikuntakeskuksia, jotka sisältävät erilaisia liikunnallisia kohteita.")
      (assoc-in [2225 :props :customer-service-point?] {:priority 0})
      (assoc-in [2225 :props :height-m] {:priority 0})
      (assoc-in [2225 :props :area-m2] {:priority 0})
      (assoc-in [2310 :description :fi] "Yksittäinen yleisurheilun olosuhde sisätiloissa esim. liikunta- tai monitoimihallin yhteydessä. Suorituspaikat kuvataan lisätiedoissa.")
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2310 :props :free-use?] {:priority 0})
      (assoc-in [2320 :name :fi] "Voimistelutila")
      (assoc-in [2320 :description :fi] "Pysyvästi voimisteluun varustettu tila. Voimistelutilassa on erilaisia kiinteitä voimistelutelineitä ja -rakenteita (esim. volttimonttu, rekki tai trampoliini). Myös cheerleadingin ja sirkusharjoittelun olosuhteet luetaan voimistelutiloiksi. Tarkempi olosuhdetieto kerrotaan lisätiedoissa.")
      ;;  Ominaisuuden nimi esim. "Lajitarkenne". Vastaava toiminnallisuus kuin veneilyn palvelupaikoissa - eli lisätiedoissa voidaan tarkentaa, minkä voimistelulajin harrastamiseen kohde on pääasiassa tarkoitettu. Vaihtoehdot: a. Lattialajit b. Telinelajit c. Lattia- ja telinelajit mahdollisia d. Pääasiassa cheerleading- tai sirkusharjoittelukäyttöön e. Ei tietoa
      (assoc-in [2320 :props :sport-specification] {:priority 0})
      ;;  Toteutus esim: Valinta Kyllä/Ei ->  Jos kyllä, täydennettävä kenttä "Tila voidaan jakaa x osaan" (voidaan syöttää lukuarvo esim. 2, joka tarkoittaa että tila voidaan jakaa kahteen osaan).
      (assoc-in [2320 :props :space-divisible] {:priority 0})
      (assoc-in [2330 :props :active-space-width-m] {:priority 0})
      (assoc-in [2330 :props :active-space-length-m] {:priority 0})
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2330 :props :free-use?] {:priority 0})
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2340 :props :free-use?] {:priority 0})
      (assoc-in [2350 :name :fi] "Tanssi-/ryhmäliikuntatila")
      (assoc-in [2350 :description :fi] "Pysyvästi tanssi-, ilmaisu- tai ryhmäliikuntaan varustettu itsenäinen tila, joka ei ole osa esim. kuntokeskusta. Myös boutique-, fitness- ja mikrostudiot ovat tanssi- tai ryhmäliikuntatiloja.")
      (assoc-in [2350 :props :active-space-width-m] {:priority 0})
      (assoc-in [2350 :props :active-space-length-m] {:priority 0})
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2350 :props :free-use?] {:priority 0})
      (assoc-in [2350 :props :mirror-wall?] {:priority 0})
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (update-in [2360 :props] dissoc :free-use?)
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (update-in [2370 :props] dissoc :free-use?)
      (assoc-in [2380 :props :highest-obstacle-m] {:priority 0})
      (update-in [2380 :props] dissoc :height-m)
      (update-in [2380 :props] dissoc :landing-places-count)
      (update-in [2380 :props] dissoc :gymnastic-routines-count)
      ;;  Ei tarvetta kioskille eikä myynti- tai asiakaspalvelupisteelle
      (update-in [2380 :props] dissoc :kiosk?'')
      (update-in [2380 :props] dissoc :climbing-wall-height-m)
      (update-in [2380 :props] dissoc :climbing-routes-count)
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2380 :props :free-use?] {:priority 0})
      ;;  Pintamateriaalikentän valittavissa seuraavista ne ominaisuudet, jotka saliin sopivat: a) Kiinteät esteet / rakennelmat b) Liikkuvat esteet / rakennelmat c) Permanto/akrobatiatila d) Kuntosali-/voimailutila
      (assoc-in [2380 :props :parkour-hall-equipment-and-structures] {:priority 0})
      (assoc-in [2380 :props :auxiliary-training-area?] {:priority 0})
      (assoc-in [2510 :description :fi] "Harjoitusjäähalli on pääasiassa jääurheilun harjoitteluun ja jääliikuntaan käytettävä jäähalli.")
      (assoc-in [2510 :props :ringette-boundary-markings?] {:priority 0})
      ;;  Olisiko kenttien/kaukaloiden tiedonhallintaa mahdollista helpottaa Lipaksessa (mallia esim. uimahallien altaista tai salibandyn ominaisuustiedoista?)
      (assoc-in [2510 :props :field-1-flexible-rink?] {:priority 0})
      ;;  Olisiko kenttien/kaukaloiden tiedonhallintaa mahdollista helpottaa Lipaksessa (mallia esim. uimahallien altaista tai salibandyn ominaisuustiedoista?)
      (assoc-in [2510 :props :field-2-flexible-rink?] {:priority 0})
      ;;  Olisiko kenttien/kaukaloiden tiedonhallintaa mahdollista helpottaa Lipaksessa (mallia esim. uimahallien altaista tai salibandyn ominaisuustiedoista?)
      (assoc-in [2510 :props :field-3-flexible-rink?] {:priority 0})
      (assoc-in [2510 :props :auxiliary-training-area?] {:priority 0})
      (update-in [2510 :props] dissoc :heating?)
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2510 :props :free-use?] {:priority 0})
      (assoc-in [2520 :description :fi] "Kilpajäähalli on jääurheilun kilpailu- ja ottelutapahtumiin soveltuva jäähalli. Katsomon koko, kenttien lukumäärä ja muut tarkemmat tiedot kuvataan lisätiedoissa.")
      (assoc-in [2520 :props :ringette-boundary-markings] {:priority 0})
      ;;  Olisiko kenttien/kaukaloiden tiedonhallintaa mahdollista helpottaa Lipaksessa (mallia esim. uimahallien altaista tai salibandyn ominaisuustiedoista?)
      (assoc-in [2520 :props :field-1-flexible-rink?] {:priority 0})
      ;;  Olisiko kenttien/kaukaloiden tiedonhallintaa mahdollista helpottaa Lipaksessa (mallia esim. uimahallien altaista tai salibandyn ominaisuustiedoista?)
      (assoc-in [2520 :props :field-2-flexible-rink?] {:priority 0})
      ;;  Olisiko kenttien/kaukaloiden tiedonhallintaa mahdollista helpottaa Lipaksessa (mallia esim. uimahallien altaista tai salibandyn ominaisuustiedoista?)
      (assoc-in [2520 :props :field-3-flexible-rink?] {:priority 0})
      (assoc-in [2520 :props :auxiliary-training-area?] {:priority 0})
      (update-in [2520 :props] dissoc :heating?)
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2520 :props :free-use?] {:priority 0})
      (assoc-in [2530 :description :fi] "Pikaluisteluun tarkoitettu halli.")
      (update-in [2530 :props] dissoc :heating?)
      ;;  Devissä tietokentän nimenä on vielä "Vapaa käyttö" -> Pitäisi olla "Kohde on vapaasti käytettävissä"
      (assoc-in [2530 :props :free-use?] {:priority 0})
      (assoc-in [2530 :props :auxiliary-training-area?] {:priority 0})
      (assoc-in [2610 :description :fi] "Keilailuun varustettu halli. Ratojen määrä ja palveluvarustus kirjataan lisätietoihin.")
      (update-in [2610 :props] dissoc :automated-scoring?)
      ;;  Uusi liikuntapaikkatyyppi
      (assoc-in [2620 :name :fi] "Biljardisali")
      ;;  Uusi liikuntapaikkatyyppi
      (assoc-in [2620 :description :fi] "Biljardisali on biljardin pelaamiseen tarkoitettu tila. Biljardipöytien määrä ja tyyppi kuvataan lisätiedoissa.")
      (assoc-in [2620 :props :customer-service-point?] {:priority 0})
      (assoc-in [2620 :props :toilet?] {:priority 0})
      (assoc-in [2620 :props :school-use?] {:priority 0})
      (assoc-in [2620 :props :may-be-shown-in-harrastuspassi-fi?] {:priority 0})
      (assoc-in [2620 :props :pool-tables-count] {:priority 0})
      (assoc-in [2620 :props :snooker-tables-count] {:priority 0})
      (assoc-in [2620 :props :kaisa-tables-count] {:priority 0})
      (assoc-in [2620 :props :pyramid-tables-count] {:priority 0})
      (assoc-in [2620 :props :carom-tables-count] {:priority 0})
      (assoc-in [2620 :props :total-billiard-tables-count] {:priority 0})
      (update-in [3110 :props] dissoc :free-use)
      (update-in [3120 :props] dissoc :free-use)
      (assoc-in [3120 :name :fi] "Uima-allastila")
      (assoc-in [3120 :description :fi] "Yksittäinen tai useampi pieni uima-allas muun kuin uimahallin tai kylpylän yhteydessä. Uima-allastilat voivat olla pääasiassa esim. kuntoutus- tai terapiakäytössä. Altaiden määrä ja vesipinta-ala kerrotaan ominaisuustiedoissa.")
      (update-in [3130 :props] dissoc :free-use)
      (assoc-in [3210 :name :fi] "Maauimala / vesipuisto")
      (assoc-in [3210 :description :fi] "Maauimala tai vesipuisto on ulkona sijaitseva, vedenpuhdistusjärjestelmällä varustettu uintiin tarkoitettu ja hoidettu vesistö tai uima-altaita/allas. Lisäksi kohteessa voi olla vesiliukumäkiä.")
      (update-in [3210 :props] dissoc :other-pools-count)
      ;;  Uusi liikuntapaikkatyyppi
      (assoc-in [3250 :name :fi] "Vesiurheilukeskus")
      ;;  Uusi liikuntapaikkatyyppi
      (assoc-in [3250 :description :fi] "Vesiurheilukeskuksessa on vesistössä sijaitsevia liikuntapalveluita tai palvelukokonaisuus, joka voi muodostua erilaisista veden päällä tai vedessä olevista suorituspaikoista tai -radoista.")
      (assoc-in [3250 :props :customer-service-point?] {:priority 0})
      (assoc-in [3250 :props :pier?] {:priority 0})
      (assoc-in [3250 :props :changing-rooms?] {:priority 0})
      (assoc-in [3250 :props :sauna?] {:priority 0})
      (assoc-in [3250 :props :shower?] {:priority 0})
      (assoc-in [3250 :props :toilet?] {:priority 0})
      (assoc-in [3250 :props :pool-water-area-m2] {:priority 0})
      (assoc-in [3250 :props :may-be-shown-in-harrastuspassi-fi?] {:priority 0})
      (assoc-in [3250 :props :free-use?] {:priority 0})
      (update-in [3220 :props] dissoc :eu-beach?)))

(def all all4)

(def active
  (reduce-kv (fn [m k v] (if (not= "active" (:status v)) (dissoc m k) m)) all all))

(def unknown
  old/unknown)

(def by-main-category (group-by :main-category (vals active)))
(def by-sub-category (group-by :sub-category (vals active)))

(def main-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals main-categories)))

(def sub-category-by-fi-name
  (utils/index-by (comp :fi :name) (vals sub-categories)))

(comment
  (all 2620)
  )
