(ns lipas.data.prop-types
  "Type codes went through a major overhaul in the summer of 2024. This
  namespace represents the changes made."
  (:require
   [lipas.data.types :as types]
   [lipas.data.prop-types-old :as old]))

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

      ;; Add new :ligthing-info prop
      (assoc :ligthing-info
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

      ;; Add new "korkein pudotus (m) prop"
      (assoc :highest-drop-m
             {:name      {:fi "Korkein pudotus m"
                          :se "Den högsta droppen"
                          :en "Highest drop"}
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

      ))

(def used
  (let [used (set (mapcat (comp keys :props second) types/all))]
    (select-keys all used)))
