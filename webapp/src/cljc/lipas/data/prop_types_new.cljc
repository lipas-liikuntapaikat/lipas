(ns lipas.data.prop-types-new
  "Type codes went through a major overhaul in the summer of 2024. This
  namespace represents the changes made."
  (:require
   [lipas.data.prop-types-old :as old]
   [lipas.data.types :as types]))

(def all
  (-> old/all

      ;; Add :boating-service-class prop type
      (assoc :boating-service-class
             {:name
              {:fi "Venesatama- tai laituriluokka"
               :se "Båthamn eller bryggklass"
               :en "Boat harbor or pier class"}
              :description
              {:fi "https://ava.vaylapilvi.fi/ava/Julkaisut/MKL/mkl_2008-1_venesatamien_luokitus.pdf"
               :se "https://ava.vaylapilvi.fi/ava/Julkaisut/MKL/mkl_2008-1_venesatamien_luokitus.pdf"
               :en "https://ava.vaylapilvi.fi/ava/Julkaisut/MKL/mkl_2008-1_venesatamien_luokitus.pdf"}
              :data-type "enum"
              :opts
              {"home-harbor"
               {:label {:fi "Kotisatama" :en "Home harbor" :se "Hemmahamn"}
                :description
                {:fi "Satama, jossa veneet pääasiallisesti säilytetään veneilykauden aikana ja jossa veneen omistaja joko omistaa tai hallitsee venepaikan. Satamat ovat yleensä kunnallisia, kaupallisia tai veneseurojen ylläpitämiä satamia."
                 :en "A harbor where boats are mainly stored during the boating season and where the boat owner either owns or controls the berth. Harbors are usually municipal, commercial, or maintained by boating clubs."
                 :se "En hamn där båtar huvudsakligen förvaras under båtsäsongen och där båtägaren antingen äger eller kontrollerar båtplatsen. Hamnarna är vanligtvis kommunala, kommersiella eller underhålls av båtklubbar."}}

               "visiting-harbor"
               {:label {:fi "Vierassatama (Palvelusatama, Vieraslaituri, Retkisatama)" :en "Visiting harbor" :se "Besökshamn"}
                :description
                {:fi "Satama, jossa veneretken tai matkapurjehduksen aikana voi käydä kaupassa, asioimassa, lepäämässä, yöpymässä tai veneen huollossa."
                 :en "A harbor where during a boating trip or sailing voyage you can go shopping, run errands, rest, stay overnight, or service the boat."
                 :se "En hamn där du under en båttur eller seglingsresa kan handla, göra ärenden, vila, övernatta eller serva båten."}}

               "safety-harbor"
               {:label {:fi "Turvasatama (Suojasatama, Hätäsatama)" :en "Safety harbor" :se "Säkerhetshamn"}
                :description
                {:fi "Satama, josta voidaan hakea suojaa tai saada ensiapua tai korjausapua."
                 :en "A harbor where you can seek shelter or get first aid or repair assistance."
                 :se "En hamn där man kan söka skydd eller få första hjälp eller reparationshjälp."}}

               "canoe-pier"
               {:label {:fi "Melontalaituri" :en "Canoe pier" :se "Kanotbrygga"}
                :description
                {:fi "Melontaan tarkoitettu laituri."
                 :en "Pier intended for canoeing."
                 :se "Brygga avsedd för kanotpaddling."}}

               "no-class"
               {:label
                {:fi "Kohde ei täytä minkään venesatama- tai laituriluokan vaatimuksia (esim. rantautumispaikka)" :en "The target does not meet the requirements of any marina or dock class (e.g., landing place)"
                 :se "Objektet uppfyller inte kraven för någon småbåtshamn- eller bryggklass (t.ex. landningsplats)"}
                :description
                {:fi "Kohde ei täytä minkään venesatama- tai laituriluokan vaatimuksia (esim. rantautumispaikka)."
                 :en "The site does not meet the requirements of any boat harbor or pier class (e.g., landing place)."
                 :se "Platsen uppfyller inte kraven för någon båthamn eller bryggklass (t.ex. landningsplats)."}}

               "no-information"
               {:label {:fi "Ei tietoa" :se "Ingen information" :en "No information"}}}})

      ;; Add "vesipiste" prop type
      (assoc :water-point
             {:name        {:fi "Vesipiste" :en "Water point" :se "Vattenpunkt"}
              :description {:fi "" :se "" :en ""}
              :data-type   "enum"
              :opts        {"year-round" {:label
                                          {:fi "Ympärivuotinen"
                                           :se "Året runt"
                                           :en "Year-round"}}

                            "seasonal" {:label
                                        {:fi "Kausittaisesti käytössä"
                                         :se "Säsongsvis i bruk"
                                         :en "Seasonally in use"}}}})

      ;; Add "Myynti- tai asiakaspalvelupiste"
      (assoc :customer-service-point?
             {:name      {:fi "Myynti- tai asiakaspalvelupiste"
                          :en "Sales or customer service point"
                          :se "Försäljnings- eller kundservicepunkt"}
              :description
              {:fi "Liikuntapaikalla on pysyvä myynti- tai asiakaspalvelupiste, josta on saatavissa asiakaspalvelua. Myynti- tai asiakaspalvelupiste voi olla rajoitetusti auki liikuntapaikan käyttöaikojen puitteissa."
               :se "Det finns en permanent försäljnings- eller kundservicestation på idrottsanläggningen där kundservice är tillgänglig. Försäljnings- eller kundservicestationen kan ha begränsade öppettider inom idrottsanläggningens användningstider."
               :en "There is a permanent sales or customer service point at the sports facility, where customer service is available. The sales or customer service point may have limited opening hours within the usage hours of the sports facility."}
              :data-type "boolean"})

      ;; Update field-length-m and field-width-m descriptions
      (assoc-in [:field-width-m :description :fi] "Kentän/kenttien leveys mahdollisine turva-alueineen metreinä")
      (assoc-in [:field-length-m :description :fi] "Kentän/kenttien pituus mahdollisine turva-alueineen metreinä")

      ;; Update surface material description
      (assoc-in [:surface-material :description :fi] "Liikunta-alueiden pääasiallinen pintamateriaali - tarkempi kuvaus liikuntapaikan eri tilojen pintamateriaalista voidaan antaa pintamateriaalin lisätietokentässä")

      ;; Update surface material info description
      (assoc-in [:surface-material-info :description :fi] "Syötä pintamateriaalin tarkempi kuvaus-  esim. tekonurmen yleisnimitys ”esim. Kumirouhetekonurmi”, tuotenimi ja tieto täytemateriaalin laadusta (esim. biohajoava/perinteinen kumirouhe).")

      ;; Update toilet? description
      (assoc-in [:toilet? :description :fi] "Onko kohteessa yleiseen käyttöön tarkoitettuja wc-tiloja?")

      ;; Add new prop: Korin tai verkon korkeus säädettävissä
      (assoc :height-of-basket-or-net-adjustable?
             {:name
              {:fi "Korin tai verkon korkeus säädettävissä"
               :se "Korgens eller nätets höjd är justerbar"
               :en "Height of the basket or net is adjustable"}
              :data-type   "boolean"
              :description {:fi "" :se "" :en ""}})

      ;; Add new prop for changing room capacity
      (assoc :changing-rooms-m2
             {:name      {:fi "Pukukoppien kokonaispinta-ala m²"
                          :se "Omklädningsrummens totala yta m²"
                          :en "Total area of the changing rooms in m²"}
              :data-type "numeric"
              :description
              {:fi "" :se "" :en ""}})

      ;; Update holes-count name
      (assoc-in [:holes-count :name]
                {:fi "Reikien / väylien lkm"
                 :se "Antal hål/fairways"
                 :en "Number of holes/fairways"})

      ;; Add new :lighting-info prop
      (assoc :lighting-info
             {:name      {:fi "Valaistuksen lisätieto"
                          :se "Ytterligare information om belysningen"
                          :en "Additional information about the lighting"}
              :data-type "string"
              :description
              {:fi "Esim. lux-määrä tai muu tarkentava tieto"
               :se "T.ex. lux-mängd eller annan förtydligande information"
               :en "E.g. lux amount or other specifying information"}})

      ;; Update :weight-lifting-spots-count name and description
      (assoc-in [:weight-lifting-spots-count :name]
                {:fi "Painonnostopaikat/nostolavat lkm"
                 :se "Antal tyngdlyftningsplatser/lyftplattformar"
                 :en "Number of weightlifting areas/platforms"})
      (assoc-in [:weight-lifting-spots-count :description]
                {:fi "Painnostopaikkojen lukumäärä. Huom. nostolava on painonnostopaikka, joka kestää painojen pudottamisen"
                 :se "Antal tyngdlyftningsplatser. Obs: En lyftplattform är en tyngdlyftningsplats som tål att vikter tappas."
                 :en "Number of weightlifting areas. Note: A lifting platform is a weightlifting area that can withstand the dropping of weights."})

      ;; Update :tatamis-count name and description
      (assoc-in [:tatamis-count :name]
                {:fi "Tatamit ja mattoalueet lkm"
                 :se "Antal tatami- och mattområden"
                 :en "Tatamis and mat areas"})
      (assoc-in [:tatamis-count :description]
                {:fi "Tatamien ja mattoalueiden lukumäärä"
                 :se "Antal tatami- och mattområden"
                 :en "Number of tatami and mat areas"})

      ;; Update :table-tennis-count name
      (assoc-in [:table-tennis-count :name :fi] "Pöytätennispöytien lkm")

      ;; Update :accessibility-info name
      (assoc-in [:accessibility-info :name]
                {:fi "Linkki esteettömyystietoon"
                 :se "Länk till tillgänglighetsinformation"
                 :en "Link to accessibility information"})

      ;; Update Halfpipe name
      (assoc-in [:halfpipe-count :name]
                {:fi "Halfpipe lkm"
                 :se "Antal halfpipe"
                 :en "Halfpipe count"})

      ;; Update Temppurinne name
      (assoc-in [:snowpark-or-street? :name :fi] "Parkki")

      ;; Update :free-use? name
      (assoc-in [:free-use? :name :fi] "Kohde on vapaasti käytettävissä")

      ;; Add new "korkeimman esteen korkeus (m) prop"
      (assoc :highest-obstacle-m
             {:name      {:fi "Korkeimman esteen korkeus (m)"
                          :se "Höjden på den högsta hindret (m)"
                          :en "The height of the highest obstacle (m)"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :fitness-stairs-length-m prop
      (assoc :fitness-stairs-length-m
             {:name      {:fi "Kuntoportaiden pituus m"
                          :se "Längden på tränings trapporna m"
                          :en "Length of the fitness stairs m"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; update :climbing-wall? description
      (assoc-in [:climbing-wall? :description :fi] "Onko kohteessa kiipeilyseinä?")

      ;; Add new :free-customer-use? prop
      (assoc :free-customer-use?
             {:name      {:fi "Vapaa asiakaskäyttö"
                          :se "Fri kundanvändning"
                          :en "Free customer use"}
              :data-type "boolean"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :space-divisible prop

      ;; Toteutus esim: Valinta Kyllä/Ei -> Jos kyllä, täydennettävä
      ;; kenttä "Tila voidaan jakaa x osaan" (voidaan syöttää lukuarvo
      ;; esim. 2, joka tarkoittaa että tila voidaan jakaa kahteen
      ;; osaan).
      (assoc :space-divisible
             {:name        {:fi "Tila jaettavissa osiin"
                            :se "Utrymmet kan delas upp"
                            :en "Space can be divided"}
              :helper-text {:fi "Syötä numero moneenko osaan tila on jaettavissa"
                            :se "Ange antalet delar som utrymmet kan delas in i"
                            :en "Enter the number of sections into which the space can be divided"}
              :data-type   "number"
              :description
              {:fi "Onko tila jaettavissa osiin esim. jakoseinien tai -verhojen avulla"
               :se "Är utrymmet delbart i sektioner, till exempel med skiljeväggar eller gardiner?"
               :en "Is the space divisible into sections, for example, with partition walls or curtains?"}})

      ;; Add new :auxiliary-training-area prop
      (assoc :auxiliary-training-area?
             {:name      {:fi "Oheisharjoittelutila"
                          :se "Kompletterande träningsområde"
                          :en "Auxiliary training area"}
              :data-type "boolean"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :sport-specification prop

      ;; Ominaisuuden nimi esim. "Lajitarkenne". Vastaava
      ;; toiminnallisuus kuin veneilyn palvelupaikoissa - eli
      ;; lisätiedoissa voidaan tarkentaa, minkä voimistelulajin
      ;; harrastamiseen kohde on pääasiassa tarkoitettu. Vaihtoehdot:
      ;; a. Lattialajit b. Telinelajit c. Lattia- ja telinelajit
      ;; mahdollisia d. Pääasiassa cheerleading- tai
      ;; sirkusharjoittelukäyttöön e. Ei tietoa
      (assoc :sport-specification
             {:name      {:fi "Lajitarkenne"
                          :se "Sportspecifikation"
                          :en "Sport specification"}
              :data-type "enum"
              :opts {"floor-disciplines"
                     {:label {:fi "Lattialajit" :en "Floor disciplines" :se "Golvdiscipliner"}
                      :description
                      {:fi "Voimistelulajit, jotka suoritetaan pääasiassa lattialla."
                       :en "Gymnastics disciplines that are primarily performed on the floor."
                       :se "Gymnastikdiscipliner som huvudsakligen utförs på golvet."}}

                     "apparatus-disciplines"
                     {:label {:fi "Telinelajit" :en "Apparatus disciplines" :se "Redskapsgrenar"}
                      :description
                      {:fi "Voimistelulajit, jotka suoritetaan erilaisilla telineillä."
                       :en "Gymnastics disciplines that are performed on various apparatus."
                       :se "Gymnastikdiscipliner som utförs på olika redskap."}}

                     "floor-and-apparatus"
                     {:label {:fi "Lattia- ja telinelajit mahdollisia" :en "Both floor and apparatus disciplines possible" :se "Både golv- och redskapsgrenar möjliga"}
                      :description
                      {:fi "Tilassa voidaan harjoitella sekä lattia- että telinelajeja."
                       :en "The space allows for practicing both floor and apparatus disciplines."
                       :se "Utrymmet möjliggör träning av både golv- och redskapsgrenar."}}

                     "cheerleading-circus"
                     {:label {:fi "Pääasiassa cheerleading- tai sirkusharjoittelukäyttöön" :en "Mainly for cheerleading or circus training" :se "Huvudsakligen för cheerleading- eller cirkusträning"}
                      :description
                      {:fi "Tila on ensisijaisesti tarkoitettu cheerleading- tai sirkusharjoitteluun."
                       :en "The space is primarily intended for cheerleading or circus training."
                       :se "Utrymmet är främst avsett för cheerleading- eller cirkusträning."}}

                     "no-information"
                     {:label {:fi "Ei tietoa" :en "No information" :se "Ingen information"}}}

              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :active-space-width-m prop
      (assoc :active-space-width-m
             {:name      {:fi "Liikuntakäytössä olevan tilan leveys m"
                          :se "Bredd på aktivt utrymme m"
                          :en "Width of active space m"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :active-space-length-m prop
      (assoc :active-space-length-m
             {:name      {:fi "Liikuntakäytössä olevan tilan pituus m"
                          :se "Längd på aktivt utrymme m"
                          :en "Length of active space m"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :mirror-wall prop
      (assoc :mirror-wall?
             {:name      {:fi "Peiliseinä"
                          :se "Spegelvägg"
                          :en "Mirror wall"}
              :data-type "boolean"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :parkour-hall-equipment-and-structures prop
      (assoc :parkour-hall-equipment-and-structures
             {:name      {:fi "Parkour-salin varustelu ja rakenteet"
                          :se "Utrustning och strukturer i parkourhallen"
                          :en "Parkour hall equipment and structures"}
              :data-type "enum-coll"
              :opts {"fixed-obstacles"
                     {:label {:fi "Kiinteät esteet / rakennelmat" :en "Fixed obstacles / structures" :se "Fasta hinder / strukturer"}
                      :description
                      {:fi "Pysyvästi asennetut esteet tai rakennelmat harjoittelua varten."
                       :en "Permanently installed obstacles or structures for training purposes."
                       :se "Permanent installerade hinder eller strukturer för träningsändamål."}}

                     "movable-obstacles"
                     {:label {:fi "Liikkuvat esteet / rakennelmat" :en "Movable obstacles / structures" :se "Flyttbara hinder / strukturer"}
                      :description
                      {:fi "Siirrettävät tai muunneltavat esteet ja rakennelmat harjoittelua varten."
                       :en "Movable or adjustable obstacles and structures for training purposes."
                       :se "Flyttbara eller justerbara hinder och strukturer för träningsändamål."}}

                     "floor-acrobatics-area"
                     {:label {:fi "Permanto/akrobatiatila" :en "Floor/acrobatics area" :se "Golv/akrobatikområde"}
                      :description
                      {:fi "Avoin tila lattiaharjoittelua ja akrobaattisia liikkeitä varten."
                       :en "Open space for floor exercises and acrobatic movements."
                       :se "Öppet utrymme för golvövningar och akrobatiska rörelser."}}

                     "gym-strength-area"
                     {:label {:fi "Kuntosali-/voimailutila" :en "Gym/strength training area" :se "Gym/styrketräningsområde"}
                      :description
                      {:fi "Alue, joka on varustettu kuntosalilaitteilla ja välineillä voimaharjoittelua varten."
                       :en "Area equipped with gym machines and equipment for strength training."
                       :se "Område utrustat med gymmaskiner och utrustning för styrketräning."}}}
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :ringette-boundary-markings prop
      (assoc :ringette-boundary-markings?
             {:name      {:fi "Ringeten rajamerkinnät"
                          :se "Gränsmarkeringar för ringette"
                          :en "Ringette boundary markings"}
              :data-type "boolean"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :field-1-flexible-rink prop
      (assoc :field-1-flexible-rink?
             {:name      {:fi "1. kenttä: onko joustokaukalo?"
                          :se "Fält 1: finns det flexibel rink?"
                          :en "Field 1: is there a flexible rink?"}
              :data-type "boolean"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :field-2-flexible-rink prop
      (assoc :field-2-flexible-rink?
             {:name      {:fi "2. kenttä: onko joustokaukalo?"
                          :se "Fält 2: finns det flexibel rink?"
                          :en "Field 2: is there a flexible rink?"}
              :data-type "boolean"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :field-3-flexible-rink prop
      (assoc :field-3-flexible-rink?
             {:name      {:fi "3. kenttä: onko joustokaukalo?"
                          :se "Fält 3: finns det flexibel rink?"
                          :en "Field 3: is there a flexible rink?"}
              :data-type "boolean"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :pool-tables-count prop
      (assoc :pool-tables-count
             {:name      {:fi "Poolpöydät lkm"
                          :se "Antal poolbord"
                          :en "Number of pool tables"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :snooker-tables-count prop
      (assoc :snooker-tables-count
             {:name      {:fi "Snookerpöydät lkm"
                          :se "Antal snookerbord"
                          :en "Number of snooker tables"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :kaisa-tables-count prop
      (assoc :kaisa-tables-count
             {:name      {:fi "Kaisapöydät lkm"
                          :se "Antal kaisabord"
                          :en "Number of kaisa tables"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :pyramid-tables-count prop
      (assoc :pyramid-tables-count
             {:name      {:fi "Pyramidipöydät lkm"
                          :se "Antal pyramidbord"
                          :en "Number of pyramid tables"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :carom-tables-count prop
      (assoc :carom-tables-count
             {:name      {:fi "Karapöydät lkm"
                          :se "Antal karombord"
                          :en "Number of carom tables"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Add new :total-billiard-tables-count prop
      (assoc :total-billiard-tables-count
             {:name      {:fi "Biljardipöydät yhteensä lkm"
                          :se "Totalt antal biljardbord"
                          :en "Total number of billiard tables"}
              :data-type "numeric"
              :description
              {:fi ""
               :se ""
               :en ""}})

      ;; Generated from template

      #_(assoc-in [:kiosk? :name :fi] "Myynti- tai asiakaspalvelupiste")
      ;;  Tuotannossa tämä tietokenttä on vanhalla nimellä "kioski". Devissä ei ole kioskia eikä tätä uutta ominaisuutta.
      #_(assoc-in [:kiosk? :description :fi] "Liikuntapaikalla on pysyvä myynti- tai asiakaspalvelupiste, josta on saatavissa asiakaspalvelua. Myynti- tai asiakaspalvelupiste voi olla rajoitetusti avoinna liikuntapaikan käyttöaikojen puitteissa.")
      (assoc-in [:surface-material :description :fi] "Liikunta-alueiden pääasiallinen pintamateriaali - tarkempi kuvaus liikuntapaikan eri tilojen pintamateriaalista voidaan antaa pintamateriaalin lisätietokentässä")
      (assoc-in [:toilet? :description :fi] "Onko kohteessa yleiseen käyttöön tarkoitettuja wc-tiloja?")
      (assoc-in [:free-customer-use? :description :fi] "Liikuntapaikka on asiakkaiden käytettävissä esim. kulkukortilla ilman henkilökunnan läsnäoloa. Vapaa asiakaskäyttö voi olla rajattu tiettyihin kellonaikoihin.")
      ;;  Toteutus esim: Valinta Kyllä/Ei ->  Jos kyllä, täydennettävä kenttä "Tila voidaan jakaa x osaan" (voidaan syöttää lukuarvo esim. 2, joka tarkoittaa että tila voidaan jakaa kahteen osaan).
      (assoc-in [:space-divisible :description :fi] "Onko tila jaettavissa osiin esim. jakoseinien tai -verhojen avulla")
      (assoc-in [:auxiliary-training-area? :description :fi] "Onko kohteessa oheisharjoitteluun soveltuva tila? Oheisharjoittelutila on liikuntapaikan käyttäjille tarkoitettu erillinen pienliikuntatila, jota voidaan käyttää esim. lämmittelyyn tai oheisharjoitteluun. Tilan koko, varustelu ja pintamateriaali ovat oheisharjoitteluun soveltuvia.")
      ;;  Ominaisuuden nimi esim. "Lajitarkenne". Vastaava toiminnallisuus kuin veneilyn palvelupaikoissa - eli lisätiedoissa voidaan tarkentaa, minkä voimistelulajin harrastamiseen kohde on pääasiassa tarkoitettu. Vaihtoehdot: a. Lattialajit b. Telinelajit c. Lattia- ja telinelajit mahdollisia d. Pääasiassa cheerleading- tai sirkusharjoittelukäyttöön e. Ei tietoa
      (assoc-in [:sport-specification :description :fi] "Valitse voimistelulaji, johon tila on pääasiassa tarkoitettu.")
      (assoc-in [:free-use :name :fi] "Kohde on vapaasti käytettävissä")
      (assoc-in [:active-space-width-m :description :fi] "Liikuntakäytössä olevan tilan leveys (m)")
      (assoc-in [:active-space-length-m :description :fi] "Liikuntakäytössä olevan tilan pituus (m)")
      (assoc-in [:mirror-wall? :description :fi] "Liikuntatilassa vähintään yhdellä seinällä on kiinteät peilit")
      (assoc-in [:highest-obstacle-m :description :fi] "Korkeimman esteen korkeus (m)")
      ;;  Pintamateriaalikentän valittavissa seuraavista ne ominaisuudet, jotka saliin sopivat: a) Kiinteät esteet / rakennelmat b) Liikkuvat esteet / rakennelmat c) Permanto/akrobatiatila d) Kuntosali-/voimailutila
      (assoc-in [:parkour-hall-equipment-and-structures :description :fi] "Valitse parkour-salissa olevat rakenteet tai varusteet")
      (assoc-in [:ringette-boundary-markings? :description :fi] "Onko kaukaloissa ringeten rajamerkinnät?")
      (assoc-in [:swimming-pool-count :name :fi] "Altaiden lukumäärä")
      ;;  Laskenta mahdollista tehdä automaattisesti syötettyjen altaiden perusteella (lasketaan yksinkertaisesti kaikki altaat yhteen tai luvun voi tarvittaessa korjata käsin, samaan tapaan kuin reittien pituuslaskuri toimii)
      (assoc-in [:swimming-pool-count :description :fi] "Altaiden lukumäärä yhteensä. Syötä tieto tai laske automaattisesti.")
      (assoc-in [:pool-water-area-m2 :description :fi] "Asiakaskäytössä oleva vesipinta-ala yhteensä.")

      ))

(def used
  (let [used (set (mapcat (comp keys :props second) types/all))]
    (select-keys all used)))
