(ns lipas.data.activities
  (:require
   #?(:clj [cheshire.core :as json])
   #?(:clj [clojure.data.csv :as csv])
   [clojure.string :as str]
   [clojure.walk :as walk]
   [lipas.utils :as utils]
   [malli.core :as m]
   [malli.json-schema :as json-schema]
   [malli.util :as mu]))

(defn collect-schema
  [m]
  (into [:map] (map (juxt first (constantly {:optional true}) (comp :schema second)) m)))

(def localized-string-schema
  [:map
   [:fi {:optional true} [:string]]
   [:se {:optional true} [:string]]
   [:en {:optional true} [:string]]])

(def number-schema
  [:or [:int] [:double]])

(def duration-schema
  [:map
   [:min {:optional true} number-schema]
   [:max {:optional true} number-schema]
   [:unit {:optional true} [:enum "days" "hours" "minutes"]]])

(def route-fcoll-schema
  [:map
   [:type [:enum "FeatureCollection"]]
   [:features
    [:sequential
     [:map
      [:id {:optional true} [:string]]
      [:type [:enum "Feature"]]
      [:properties {:optional true} [:map]]
      [:geometry
       [:map
        [:type [:enum "LineString"]]
        [:coordinates
         [:sequential
          [:or
           [:tuple :double :double]
           [:tuple :double :double :double]]]]]]]]]])

(def contact-roles
  {"admin"            {:fi "Ylläpitäjä"}
   "content-creator"  {:fi "Sisällöntuottaja"}
   "customer-service" {:fi "Asiakaspalvelu"}})

(def common-rules
  {"everymans-rights-valid"
   {:label
    {:fi "Jokaisenoikeudet ovat voimassa tällä alueella",
     :en "Everyman's rights are valid in this area",
     :sv "Allemansrätten gäller i detta område"},
    :description {:fi "", :en "", :sv ""},
    :value       "everymans-rights-valid"},

   "bring-garbage-bag-bins-available"
   {:label
    {:fi "Muista tuoda roskapussi",
     :en "Remember to bring a garbage bag",
     :sv "Kom ihåg att ta med en soppsäck"},
    :description
    {:fi
     "Pysäköintialueella on jäteastiat. Tuo roskapussi ja vie roskat jäteastiaan.",
     :en
     "There are waste bins in the parking area. Bring a garbage bag and dispose of the garbage in the waste bin.",
     :sv
     "Det finns soptunnor på parkeringsområdet. Ta med en soppåse och lägg i soporna i soptunnan."},
    :value "bring-garbage-bag-bins-available"},

   "overnight-stay-guest-harbour-allowed"
   {:label
    {:fi "Yöpyminen retkisatamassa on sallittu",
     :en "Overnight stay in the guest harbour is allowed",
     :sv "Övernattning i gästhamnen är tillåten"},
    :description
    {:fi "Retkisatamassa saa pitää venettä enintään 2 vuorokautta.",
     :en "In the guest harbour, you can keep a boat for up to 2 days.",
     :sv "Du får behålla en båt i gästhamnen i högst 2 dagar."},
    :value "overnight-stay-guest-harbour-allowed"},

   "bring-garbage-bag-no-bins"
   {:label
    {:fi "Muista tuoda roskapussi",
     :en "Remember to bring a garbage bag",
     :sv "Kom ihåg att ta med en soppsäck"},
    :description
    {:fi
     "Alueella ei ole jäteastioita. Tuo roskapussi ja vie roskat mennessäsi.",
     :en
     "There are no waste bins in the area. Bring a garbage bag and take the garbage with you when you leave.",
     :sv
     "Det finns inga soptunnor i området. Ta med en soppåse och ta soporna med dig när du går."},
    :value "bring-garbage-bag-no-bins"},

   "fire-only-at-marked-fireplaces"
   {:label
    {:fi
     "Tulenteko on sallittu ainoastaan merkityillä tulentekopaikoilla",
     :en "Making fire is only allowed at marked fireplaces",
     :sv
     "Det är endast tillåtet att göra upp eld på märkta eldställen"},
    :description
    {:fi
     "Maastopalovaroituksen aikaan tulenteko on kokonaan kielletty. Tulentekopaikalla saa käyttää vain alueella tarjolla olevia tai itse tuotuja polttopuita.",
     :en
     "During a wildfire warning, making fire is completely forbidden. At the fireplace, only use firewood that is available in the area or brought by yourself.",
     :sv
     "Under en skogsbrandsvarning är eldstad helt förbjudet. Vid eldstaden får du endast använda ved som finns i området eller som du har med dig själv."},
    :value "fire-only-at-marked-fireplaces"},

   "temporary-camping-allowed"
   {:label
    {:fi "Tilapäinen leiriytyminen alueella on sallittu",
     :en "Temporary camping in the area is allowed",
     :sv "Tillfällig camping i området är tillåten"},
    :description {:fi "", :en "", :sv ""},
    :value       "temporary-camping-allowed"},

   "overnight-stay-not-allowed"
   {:label
    {:fi "Yöpyminen alueella on kielletty",
     :en "Overnight stay in the area is not allowed",
     :sv "Övernattning i området är förbjuden"},
    :description {:fi "", :en "", :sv ""},
    :value       "overnight-stay-not-allowed"},

   "only-fire-in-designated-places"
   {:label
    {:fi "Tee tulet vain sallituilla paikoilla",
     :en "Only make fire in designated places",
     :sv "Gör endast upp eld på angivna platser"},
    :description
    {:fi
     "Tarkista aina ennen tulentekoa voimassa oleva maastopalovaroitus. Tulentekopaikalla saa käyttää vain alueella tarjolla olevia tai itse tuotuja polttopuita.",
     :en
     "Always check the current forest fire warning before making a fire. At the campfire site, only use firewood that is available in the area or brought by yourself.",
     :sv
     "Kontrollera alltid den aktuella skogsbrandsvarningen innan du eldar. Du får bara använda eldved som finns i området eller som du själv har med dig vid eldstaden."},
    :value "only-fire-in-designated-places"},

   "camping-forbidden"
   {:label
    {:fi "Leiriytyminen alueella on kielletty",
     :en "Camping in the area is forbidden",
     :sv "Camping i området är förbjuden"},
    :description {:fi "", :en "", :sv ""},
    :value       "camping-forbidden"}

   "bring-own-firewood"
   {:label       {:fi "Tuo omat polttopuut" :en "Bring your own firewood" :sv "Ta med egen ved"}
    :description {:fi "Alueella ei ole polttopuuhuoltoa." :en "There is no firewood service in the area." :sv "Det finns ingen vedservice i området."}
    :value       "bring-own-firewood"}

   "keep-pets-leashed-poop-ok"
   {:label       {:fi "Pidä lemmikit aina kytkettynä" :en "Always keep your pets leashed" :sv "Håll alltid dina husdjur kopplade"}
    :description {:fi "Koirat saavat tehdä jätöksensä luontoon, hieman poluista sivummalla." :en "Dogs can defecate in nature, a bit off the trails." :sv "Hundar kan defekera i naturen, lite av vägen."}
    :value       "keep-pets-leashed-poop-ok"}

   "keep-pets-leashed-poop-not-ok"
   {:label       {:fi "Pidä lemmikit aina kytkettynä" :en "Always keep your pets leashed" :sv "Håll alltid dina husdjur kopplade"}
    :description {:fi "Muista myös koirankakkapussi ja korjaa jätökset pois." :en "Also remember the dog poop bag and remove the stools." :sv "Kom också ihåg hundens avföringspåse och ta bort avföringen."}
    :value       "keep-pets-leashed-poop-not-ok"}})

(def rules-structured-schema
  [:map
   [:common-rules [:sequential (into [:enum] (keys common-rules))]]
   [:custom-rules {:optional true}
    [:sequential
     [:map
      [:label {:optional true} localized-string-schema]
      [:description {:optional true} localized-string-schema]
      [:value {:optional true} [:string {:min 2}]]]]]])

(def common-props
  {:description-short
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "1-3 lauseen esittely kohteesta ja sen erityispiirteistä."}
     :label       {:fi "Yleiskuvaus"}}}

   :description-long
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Yleiskuvausta jatkava, laajempi kuvaus kohteesta ja sen ominaisuuksista"}
     :label       {:fi "Kohdekuvaus"}}}

   :contacts
   {:schema [:map
             [:organization {:optional true} localized-string-schema]
             [:role {:optional true} (into [:enum] (keys contact-roles))]
             [:email {:optional true} [:string]]
             [:www {:optional true} [:string]]
             [:phone-number {:optional true} [:string]]]
    :field
    {:type        "contacts"
     :description {:fi "Syötä kohteesta vastaavien tahojen yhteystiedot"}
     :label       {:fi "Yhteystiedot"}
     :props
     {:organization
      {:field
       {:type        "text-field"
        :label       {:fi "Organisaatio"}
        :description {:fi "Organisaation nimi"}}}
      :role
      {:field
       {:type        "multi-select"
        :label       {:fi "Rooli"}
        :description {:fi [:<>
                           "Asiakaspalvelu: Kohteen asiakaspalvelusta vastaava organisaatio"
                           [:br]
                           "Sisällöntuottaja: Kohdetta kuvailevista LIPAS-tietosisällöistä vastaava organisaatio"
                           [:br]
                           "Ylläpitäjä: Kohteen rakenteiden ja olosuhteiden ylläpidosta vastaava organisaatio"]}
        :opts        contact-roles}}
      :email
      {:field
       {:type        "text-field"
        :label       {:fi "Sähköposti"}
        :description {:fi "Organisaation sähköpostiosoite (syötä vain yksi sähköpostiosoite)"}}}

      :www
      {:field
       {:type        "text-field"
        :label       {:fi "Web-osoite"}
        :description {:fi "Organisaation verkkosivu (syötä vain yksi verkko-osoite)"}}}

      :phone-number
      {:field
       {:type        "text-field"
        :label       {:fi "Puhelinnumero"}
        :description {:fi "Organisaation puhelinnumero"}}}}}}

   :videos
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} localized-string-schema]]]
    :field
    {:type        "videos"
     :description {:fi "Lisää URL-linkki web-palvelussa olevaan kohteen maisemia, luontoa tai harrastamisen olosuhteita esittelevään videoon. Varmista, että sinulla on oikeus lisätä video."}
     :label       {:fi "Videot"}}}

   :images
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} localized-string-schema]
              [:alt-text {:optional true} localized-string-schema]]]
    :field
    {:type        "images"
     :description {:fi "Lisää kohteen maisemia, luontoa tai harrastamisen olosuhteita esitteleviä valokuvia. Voit lisätä vain kuvatiedostoja, et URL-kuvalinkkejä. Kelvollisia tiedostomuotoja ovat .jpg, .jpeg ja .png. Varmista, että sinulla on oikeus lisätä kuva."}
     :label       {:fi "Valokuvat"}
     :props
     {:url
      {:field
       {:type   "text-field"
        :hidden true}}
      :description
      {:field
       {:type        "textarea"
        :description {:fi "Kuvan yhteydessä kaikille näytettävä teksti kuvassa esitettävistä asioista."}
        :label       {:fi "Kuvateksti"}}}
      :alt-text
      {:field
       {:type        "textarea"
        :description {:fi "Ruudunlukijan näkövammaisille kertoma teksti kuvassa esitettävistä asioista. Lue lisää: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"}
        :label       {:fi "Alt-teksti"}}}}}}

   :additional-info-link
   {:schema [:string]
    :field
    {:type        "text-field"
     :description {:fi "Linkki ulkoisella sivustolla sijaitsevaan laajempaan kohde-esittelyyn"}
     :label       {:fi "Lisätietoa kohteesta saatavilla"}}}

   :rules
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Liikkumis- tai toimintaohjeet, joiden avulla ohjataan toimintaa. Tässä voidaan kertoa myös mahdollisista liikkumis- tai toimintarajoituksista."}
     :label       {:fi "Luvat, säännöt, ohjeet"}}}

   :arrival
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Eri kulkumuodoilla kohteeseen pääsyyn liittyvää tietoa. Esim. pysäköintialueet ja joukkoliikenneyhteydet."}
     :label       {:fi "Saapuminen"}}}

   :accessibility
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Yleistä tietoa kohteen esteettömyydestä tai kuljettavuudesta"}
     :label       {:fi "Esteettömyys"}}}

   :highlights
   {:schema [:sequential localized-string-schema]
    :field
    {:type        "textlist"
     :description {:fi "Syötä 2-6 konkreettista kohteen erityispiirrettä, jotka täydentävät yleiskuvausta. Syötä yksi kohokohta kerrallaan. Käytä isoa Alkukirjainta."}
     :label       {:fi "Kohokohdat"}}}})

(def common-props-schema
  (collect-schema common-props))


(comment
  (m/schema common-props-schema))

(def outdoor-recreation-areas
  {:label      {:fi "Retkeily ja ulkoilualueet"}
   :value      "outdoor-recreation-areas"
   :type-codes #{102 103 104 106 107 #_#_#_#_108 109 110 111 112}
   :sort-order [:description-short
                :description-long
                :highlights
                :rules-structured
                :arrival
                :accessibility
                :geo-park
                :contacts
                :additional-info-link
                :images
                :videos]
   :props
   (merge
    (-> common-props
        (dissoc :rules))
    {#_#_:everymans-rights
     {:schema [:boolean {:optional true}]
      :field
      {:type        "checkbox"
       :description {:fi "Onko jokaisen oikeudet voimassa. Kyllä/Ei"}
       :label       {:fi "Jokamiehenoikeudet"}}}
     :geo-park
     {:schema [:boolean {:optional true}]
      :field
      {:type        "checkbox"
       :description {:fi "Jos kohde on geopark, niin aktivoi liukukytkin (aktivoitu kytkin muuttuu punaiseksi). HUOM! Geopark on yhtenäinen maantieteellinen alue, jolla on kansainvälisesti merkittävää geologista arvoa."}
       :label       {:fi "Geopark"}}}
     :rules-structured
     {:schema rules-structured-schema
      :field
      {:type        "rules"
       :description {:fi "Liikkumis- tai toimintaohjeet, joiden avulla ohjataan toimintaa ja esim. varoitetaan poistumasta polulta herkällä kohteella. Tässä voidaan kertoa myös mahdollisista liikkumis- tai toimintarajoituksista."}
       :label       {:fi "Luvat, säännöt, ohjeet"}
       :opts        common-rules}}})})

(def outdoor-recreation-areas-schema
  (collect-schema (:props outdoor-recreation-areas)))

(def outdoor-recreation-routes-activities
  {"camping"            {:fi "Retkeily"}
   "hiking"             {:fi "Vaellus"}
   "outdoor-recreation" {:fi "Ulkoilu"}
   "mountain-biking"    {:fi "Maastopyöräily"}
   "paddling"           {:fi "Melonta"}
   "skiing"             {:fi "Hiihto"}})

(def accessibility-classification
  {"accessible"          {:fi "Esteetön"}
   "advanced-accessible" {:fi "Vaativa esteetön"}
   "inaccessible"        {:fi "Esteellinen"}
   "unknown"             {:fi "Ei tietoa"}})

(def outdoor-recreation-routes
  {:label       {:fi "Retkeily ja ulkoilureitit"}
   :value       "outdoor-recreation-routes"
   :description {:fi ""}
   :type-codes  #{4401 4402 4403 4404 4405}
   :sort-order  [:route-name
                 :description-short
                 :description-long
                 :independent-entity
                 :highlights
                 :rules-structured
                 :arrival
                 :route-length-km
                 :duration
                 :travel-direction
                 :route-marking
                 :route-marking
                 :surface-material
                 :outdoor-recreation-activities
                 :accessibility-classification
                 :accessibility
                 :accessibility-categorized
                 :contacts
                 :additional-info-link
                 :images
                 :videos]
   :props
   {:routes
    {:schema [:sequential
              (mu/merge
               (-> common-props-schema
                   (mu/dissoc :accessibility)
                   (mu/dissoc :latest-updates)
                   (mu/dissoc :rules))
               [:map
                [:id [:string]]
                [:geometries route-fcoll-schema]
                [:accessibility-categorized {:optional true}
                 [:map
                  [:mobility-impaired {:optional true} localized-string-schema]
                  [:hearing-impaired {:optional true} localized-string-schema]
                  [:visually-impaired {:optional true} localized-string-schema]
                  [:developmentally-disabled {:optional true} localized-string-schema]]]
                [:route-name {:optional true} localized-string-schema]
                [:outdoor-recreation-activities {:optional true}
                 [:sequential [:enum (keys outdoor-recreation-routes-activities)]]]
                [:duration {:optional true} duration-schema]
                [:travel-direction {:optional true} [:enum "clockwise" "counter-clockwise"]]
                [:route-marking {:optional true} localized-string-schema]
                [:rules-structured {:optional true} rules-structured-schema]
                [:accessibility-classification
                 (into [:enum] (keys accessibility-classification))]
                [:independent-entity {:optional true} [:boolean]]])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
      :label       {:fi "Reittiosan tyyppi"}
      :props
      (merge
       (-> common-props
           (dissoc :rules :accessibility)
           (assoc-in [:description-long :field :description :fi]
                     "Tarkempi reitin eri vaiheiden kuvaus. Esim. kuljettavuus, nähtävyydet, taukopaikat ja palvelut. Erota vaiheet omiksi kappaleiksi.")
           (assoc-in [:description-short :field :description :fi]
                     "3-7 lauseen mittainen kuvaus kohteesta. Näytetään esim. kohde-esittelyn ingressinä tai useamman kohteen listauksessa."))
       {:accessibility-classification
        {:field
         {:type        "select"
          :label       {:fi "Esteettömyysluokittelu"}
          :description {:fi "Valitse onko reitti esteellinen, esteetön vai vaativa esteetön (vaativalla esteettömällä reitillä saatetaan tarvita avustaja ja/tai apuväline, kuten maastopyörätuoli)"}
          :opts        (dissoc accessibility-classification "unknown")}}

        :rules-structured
        {:field
         {:type        "rules"
          :description {:fi "Liikkumisohje, jonka avulla voidaan ohjata harrastusta ja esimerkiksi varoittaa poistumasta polulta herkällä kohteella. Tätä kautta voidaan informoida myös mahdollisista lakisääteisistä liikkumisrajoituksista."}
          :label       {:fi "Luvat, säännöt, ohjeet"}
          :opts        common-rules}}

        :accessibility-categorized
        {:field
         {:type        "accessibility"
          :label       {:fi "Esteettömyys vammaryhmittäin"}
          :description {:fi "Syötä esteettömyyskuvailu vammaryhmille"}
          :props
          {:mobility-impaired
           {:value "mobility-impaired"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"}
             :label       {:fi "Liikuntavammaiset"}}}
           :hearing-impaired
           {:value "hearing-impaired"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"}
             :label       {:fi "Kuurot ja kuulovammaiset"}}}
           :visually-impaired
           {:value "visually-impaired"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"}
             :label       {:fi "Näkövammaiset"}}}
           :developmentally-disabled
           {:value "developmentally-disabled"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"}
             :label       {:fi "Kehitysvammaiset"}}}}}}

        :route-name
        {:field
         {:type        "text-field"
          :description {:fi "Anna reitille kuvaava nimi, esim. sen maantieteellisen sijainnin tai reitin päätepisteiden mukaan."}
          :label       {:fi "Reitin nimi"}}}

        :outdoor-recreation-activities
        {:field
         {:type        "multi-select"
          :description {:fi "Valitse reitille soveltuvat kulkutavat"}
          :label       {:fi "Kulkutavat"}
          :opts        outdoor-recreation-routes-activities}}

        :duration
        {:field
         {:type        "duration"
          :description {:fi "Reitin arvioitu kulkuaika"}
          :label       {:fi "Kulkuaika"}}}

        :travel-direction
        {:field
         {:type        "select"
          :opts        {"clockwise"         {:fi "Myötäpäivään"}
                        "counter-clockwise" {:fi "Vastapäivään"}}
          :description {:fi "Valitse reitin kulkusuunta, myötäpäivään/vastapäivään, jos reitillä on suositeltu kulkusuunta."}
          :label       {:fi "Kulkusuunta"}}}

        :route-marking
        {:field
         {:type        "text-field"
          :description {:fi "Kuvaile tapa, jolla reitti on merkitty maastoon. Esim. syötä reittimerkkien symboli ja väri."}
          :label       {:fi "Reittimerkintä"}}}

        :route-length-km
        {:field
         {:type           "lipas-property"
          :lipas-property :route-length-km
          :label          {:fi "Reitin pituus (km)"}
          :description    {:fi "Reitin pituus kilometreinä (voit syöttää tiedon käsin tai laskea sen automaattisesti)"}}}

        :surface-material
        {:field
         {:type           "lipas-property"
          :lipas-property :surface-material
          :label          {:fi "Pintamateriaali"}
          :description    {:fi "Valitse kaikki pintamateriaalit, joita reitillä kuljetaan"}}}

        #_#_:latest-updates
        {:schema localized-string-schema
         :field
         {:type        "textarea"
          :description {:fi "Tähän joku seliteteksti"}
          :label       {:fi "Ajankohtaista"}}}

        :independent-entity
        {:schema [:boolean {:optional true}]
         :field
         {:type        "checkbox"
          :description {:fi "Aktivoi liukukytkin, jos reitti ei kuulu mihinkään alueeseen tai laajempaan kokonaisuuteen (esim. ulkoilualueeseen tai kansallispuistoon).  Aktivoitu kytkin muuttuu punaiseksi."}
          :label       {:fi "Itsenäinen kohde"}}}})}}}})

(def outdoor-recreation-routes-schema
  (collect-schema (:props outdoor-recreation-routes)))

(def cycling-activities
  {"gravel-and-bikepacking" {:fi "Gravel & pyörävaellus"}
   "road-cycling"           {:fi "Maantie"}
   "bike-touring"           {:fi "Retkipyöräily"}
   "mountain-biking"        {:fi "Maastopyöräily"}
   "winter-cycling"         {:fi "Talvipyöräily"}})

(def cycling
  {:label       {:fi "Pyöräily"}
   :value       "cycling"
   :description {:fi ""}
   :type-codes  #{4411 4412}
   :props
   {
    ;; Päiväetapit pitää pystyä esittelemään erikseen kartalla ja
    ;; kuvailemaan omana kohteenaan. Reittikokonaisuus olisi päätason
    ;; liikuntapaikka (alatasona päiväetapit, jotka ovat ehdotusmaisia
    ;; etappeja).
    :routes
    {:schema [:sequential
              (mu/merge
               common-props-schema
               [:map
                [:id [:string]]
                [:geometries route-fcoll-schema]
                [:route-name {:optional true} localized-string-schema]
                [:cycling-activities {:optional true}
                 [:sequential (into [:enum] (keys cycling-activities))]]
                [:duration {:optional true} duration-schema]
                [:food-and-water {:optional true} localized-string-schema]
                [:accommodation {:optional true} localized-string-schema]
                [:good-to-know {:optional true} localized-string-schema]
                [:route-notes {:optional true} localized-string-schema]
                [:unpaved-percentage {:optional true} number-schema]
                [:trail-percentage {:optional true} number-schema]
                [:cyclable-percentage {:optional true} number-schema]])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
      :label       {:fi "Reittityyppi"}
      :props
      (merge
       (dissoc common-props :rules)
       {:route-name
        {:field
         {:type        "text-field"
          :description {:fi "Tähän joku järkevä ohje"}
          :label       {:fi "Reitin nimi"}}}
        :cycling-activities
        {:field
         {:type        "multi-select"
          :description {:fi "Gravel & pyörävaellus, Maantie, Maastopyöräily"}
          :label       {:fi "Alalaji"}
          :opts        (dissoc cycling-activities "road-cycling")}}

        :duration
        {:field
         {:type        "duration"
          :description {:fi "Ajoaika"}
          :label       {:fi "Ajoaika"}}}

        :food-and-water
        {:field
         {:type        "textarea"
          :description {:fi "Tekstiä"}
          :label       {:fi "Ruoka & vesi"}}}

        :accommodation
        {:field
         {:type        "textarea"
          :description {:fi "Tekstiä"}
          :label       {:fi "Majoitus"}}}

        :good-to-know
        {:field
         {:type        "textarea"
          :description {:fi "Tekstiä"}
          :label       {:fi "Hyvä tietää"}}}

        :route-notes
        {:field
         {:type        "textarea"
          :description {:fi "Tekstiä"}
          :label       {:fi "Reittimuistiinpanot"}}}

        :unpaved-percentage
        {:field
         {:type        "number"
          :adornment   "%"
          :description {:fi "Esim. 28%"}
          :label       {:fi "Päällystämätöntä"}}}

        :trail-percentage
        {:field
         {:type        "number"
          :adornment   "%"
          :description {:fi "Esim. 0%"}
          :label       {:fi "Polkua"}}}

        :cyclable-percentage
        {:field
         {:type        "number"
          :adornment   "%"
          :description {:fi "Esim. 100%"}
          :label       {:fi "Pyöräiltävissä"}}}})}}}})

(def cycling-schema
  (collect-schema (:props cycling)))

(def paddling-activities
  {"trip-padding"        {:fi "Retkimelonta"}
   "whitewater-paddling" {:fi "Koskimelonta"}})

(def paddling-route-types
  {"open-water" {:fi "Avovesi"}
   "sheltered"  {:fi "Suojaisa"}
   "river"      {:fi "Joki"}
   "rapids"     {:fi "Koski"}})

(def paddling-properties
  {"includes-portage-sections" {:fi "Sisältää kanto-osuuksia"}
   "includes-canals-or-locks"  {:fi "Sisältää sulkuja / kanavia"}
   "ideal-for-fishing"         {:fi "Hyvä kalapaikka"}
   "scenic-vistas"             {:fi "Hyvät maisemat"}})

(def paddling-difficulty
  {"easy"   {:fi "Helppo"}
   "medium" {:fi "Keskivaikea"}
   "hard"   {:fi "Vaativa"}})

(def paddling
  {:label       {:fi "Melonta ja SUP"}
   :value       "paddling"
   :description {:fi ""}
   :type-codes  #{4451 4452}
   :props
   {:routes
    {:schema [:sequential
              (mu/merge
               common-props-schema
               [:map
                [:id [:string]]
                [:geometries route-fcoll-schema]
                [:route-name {:optional true} localized-string-schema]
                [:paddling-activities {:optional true}
                 [:sequential (into [:enum] (keys paddling-activities))]]
                [:paddling-route-type {:optional true}
                 [:sequential (into [:enum] (keys paddling-route-types))]]
                [:paddling-properties {:optional true}
                 [:sequential (into [:enum] (keys paddling-properties))]]
                [:paddling-difficulty (into [:enum] (keys paddling-difficulty))]
                [:safety {:optional true} localized-string-schema]
                [:good-to-know {:optional true} localized-string-schema]
                [:duration {:optional true} duration-schema]])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
      :label       {:fi "Reittityyppi"}
      :props
      (merge
       common-props
       {:route-name
        {:field
         {:type        "text-field"
          :description {:fi "Tähän joku järkevä ohje"}
          :label       {:fi "Reitin nimi"}}}

        :paddling-activities
        {:field
         {:type        "multi-select"
          :description {:fi "Retkimelonta, Koskimelonta"}
          :label       {:fi "Aktiviteetti"}
          :opts        paddling-activities}}

        :paddling-route-type
        {:field
         {:type        "multi-select"
          :description {:fi "Avovesi, Suojaisa, Joki, Koski"}
          :label       {:fi "Melontakohteen tyyppi"}
          :opts        paddling-route-types}}

        :paddling-properties
        {:field
         {:type        "multi-select"
          :description {:fi "Seliteteksti?"}
          :label       {:fi "Ominaisuudet"}
          :opts        paddling-properties}}

        :paddling-difficulty
        {:field
         {:type        "select"
          :description {:fi "Vaativuus"}
          :label       {:fi "Vaativuus"}
          :opts        paddling-difficulty}}

        :safety
        {:field
         {:type        "textarea"
          :description {:fi "Reitin vaativuuden kuvaus, osaamissuositus, kalustosuositus"}
          :label       {:fi "Turvallisuus"}}}

        :good-to-know
        {:field
         {:type        "textarea"
          :description {:fi "Esim. matkapuhelimen kuuluvuuden katvealueet"}
          :label       {:fi "Hyvä tietää"}}}

        :duration
        {:field
         {:type        "duration"
          :description {:fi "Kulkuaika"}
          :label       {:fi "Kulkuaika"}}}})}}}})

(def paddling-schema
  (collect-schema (:props paddling)))

(def birdwatching-types
  {"bird-observation-tower"      {:fi "Lintutorni"}
   "other-bird-observation-spot" {:fi "Muu lintupaikka"}})

(def birdwatching-seasons
  {"spring" {:fi "Kevät"}
   "summer" {:fi "Kesä"}
   "autumn" {:fi "Syksy"}
   "winter" {:fi "Talvi"}})

(def birdwatching
  {:label       {:fi "Lintujen tarkkailu"}
   :value       "birdwatching"
   :description {:fi ""}
   :type-codes  #{204}
   :props
   (merge
    common-props
    {:birdwatching-type
     {:schema [:sequential (into [:enum] (keys birdwatching-types))]
      :field
      {:type        "multi-select"
       :description {:fi "Lintutorni, Muu lintupaikka"}
       :label       {:fi "Tyyppi"}
       :opts        birdwatching-types}}

     :birdwatching-habitat
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Suokohde, …"}
       :label       {:fi "Elinympäristö"}}}

     :birdwatching-character
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Muutonseurantakohde, …"}
       :label       {:fi "Luonne"}}}

     :birdwatching-season
     {:schema [:sequential (into [:enum] (keys birdwatching-seasons))]
      :field
      {:type        "multi-select"
       :description {:fi "Kevät, Kesä, Syksy, Talvi"}
       :label       {:fi "Ajankohta"}
       :opts        birdwatching-seasons}}

     :birdwatching-species
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Kahlaajat, Vesilinnut, Petolinnut, …"}
       :label       {:fi "Lajisto"}}}})})

(def birdwatching-schema (collect-schema (:props birdwatching)))

(def fishing-types
  {"shore"        {:fi "Kalastus rannalta"}
   "on-the-water" {:fi "Kalastus vesiltä / jäältä"}})

(def fishing-activities
  {"angling"         {:fi "Onginta"}
   "ice-fishing"     {:fi "Pilkkiminen"}
   "fly-fishing"     {:fi "Perhokalastus"}
   "lure-fishing"    {:fi "Viehekalastus"}
   "herring-jigging" {:fi "Silakan litkaus"}})

(def fishing-waters
  {"sea"   {:fi "Meri"}
   "river" {:fi "Joki"}
   "lake"  {:fi "Järvi"}})

(def fishing-permit-opts
  {"general-fishing-rights"         {:fi "Maksuttomat yleiskalastusoikeudet ovat voimassa (onkiminen, pilkkiminen ja merellä silakan litkaus)"}
   "fee-for-lure-fishing"           {:fi "Kalastonhoitomaksu viehekalastukseen (maksuvelvollisuus koskee 18–69 -vuotiaita)"}
   "local-fishing-permit"           {:fi "Paikallinen kalastuslupa"}
   "special-permit-or-restrictions" {:fi "Kohteella on poikkeuksellisia lupajärjestelyitä tai rajoituksia. Katso kalastusrajoitus.fi"}})

(def fishing-species
  {"perch"        {:fi "Ahven" :se "Abborre" :en "Perch"},
   "pike"          {:fi "Hauki" :se "Gädda" :en "Pike"},
   "zander"        {:fi "Kuha" :se "Gös" :en "Zander"},
   "whitefish"     {:fi "Siika" :se "Sik" :en "Whitefish"},
   "bream"         {:fi "Made" :se "Sutare" :en "Bream"},
   "herring"       {:fi "Silakka" :se "Strömming" :en "Herring"},
   "salmon"        {:fi "Lohi" :se "Lax" :en "Salmon"},
   "trout"         {:fi "Taimen" :se "Harr" :en "Trout"},
   "rainbow-trout" {:fi "Kirjolohi" :se "Regnbåge" :en "Rainbow trout"},
   "arctic-char"   {:fi "Nieriä" :se "Röding" :en "Arctic char"},
   "cyprinids"     {:fi "Särkikalat" :se "Vitfisk" :en "Cyprinids"}})

(def fishing
  {:label       {:fi "Kalastus"}
   :value       "fishing"
   :description {:fi ""}
   :type-codes  #{201 2011}
   :props
   (merge
    (dissoc common-props :accessibility)
    {:fishing-type
     {:schema [:sequential (into [:enum] (keys fishing-types))]
      :field
      {:type        "multi-select"
       :description {:fi "Kalastus rannalta, Kalastus vesiltä/jäältä"}
       :label       {:fi "Kohdetyyppi"}
       :opts        fishing-types}}

     :fishing-activities
     {:schema [:sequential (into [:enum] (keys fishing-activities))]
      :field
      {:type        "multi-select"
       :description {:fi "Onginta, Pilkkiminen, Perhokalastus, Viehekalastus"}
       :label       {:fi "Hyvin soveltuvat kalastusmuodot"}
       :opts        fishing-activities}}

     :fishing-waters
     {:schema (into [:enum] (keys fishing-waters))
      :field
      {:type        "select"
       :description {:fi ""}
       :label       {:fi "Vesistö"}
       :opts        fishing-waters}}

     :fishing-species
     {:schema [:sequential (into [:enum] (keys fishing-species))]
      :field
      {:type        "multi-select"
       :description {:fi "Kohteessa kalastamisen kannalta keskeisimmät kalalajit, esim. ahven, taimen, kirjolohi tms."}
       :label       {:fi "Keskeiset kalalajit"}
       :opts        fishing-species}}

     :fish-population
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Tekstimuotoinen kuvaus kohteen kalastosta"}
       :label       {:fi "Kalasto"}}}


     :fishing-methods
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Tietoa mm. kohteessa kalastukseen vaikuttavista erityispiirteistä, toimivista välinevalinnoista yms."}
       :label       {:fi "Vinkkejä kohteessa kalastamiseen"}}}

     :fishing-permit
     {:schema [:sequential (into [:enum] (keys fishing-permit-opts))]
      :field
      {:type        "checkboxes"
       :label       {:fi "Kalastuslupatarve"}
       :description {:fi "Kohteen kalastuslupatarve yhdellä vavalla kalastettaessa. Huom. useammalla vavalla kalastaminen vaatii aina paikallisen luvan."}
       :opts        fishing-permit-opts}}

     :fishing-permit-additional-info
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Tähän joku selite"}
       :label       {:fi "Kalastuslupatarpeen lisätiedot"}}}

     :accessibility-classification
     {:schema (into [:enum] (keys accessibility-classification))
      :field
      {:type        "select"
       :label       {:fi "Esteettömyysluokittelu"}
       :description {:fi ""}
       :opts        (dissoc accessibility-classification "advanced-accessible")}}

     :accessibility-categorized
     {:schema [:map
               [:mobility-impaired {:optional true} localized-string-schema]
               [:hearing-impaired {:optional true} localized-string-schema]
               [:visually-impaired {:optional true} localized-string-schema]
               [:developmentally-disabled {:optional true} localized-string-schema]]

      :field
      {:type        "accessibility"
       :label       {:fi "Esteettömyys"}
       :description {:fi "Tähän jotain"}
       :props
       {:mobility-impaired
        {:value "mobility-impaired"
         :field
         {:type        "textarea"
          :description {:fi "Aihekohtainen tekstikuvaus"}
          :label       {:fi "Liikuntavammaiset"}}}
        #_#_:hearing-impaired
        {:value "hearing-impaired"
         :field
         {:type        "textarea"
          :description {:fi "Aihekohtainen tekstikuvaus"}
          :label       {:fi "Kuurot ja kuulovammaiset"}}}
        #_#_:visually-impaired
        {:value "visually-impaired"
         :field
         {:type        "textarea"
          :description {:fi "Aihekohtainen tekstikuvaus"}
          :label       {:fi "Näkövammaiset"}}}
        #_#_:developmentally-disabled
        {:value "developmentally-disabled"
         :field
         {:type        "textarea"
          :description {:fi "Aihekohtainen tekstikuvaus"}
          :label       {:fi "Kehitysvammaiset"}}}}}}})})

(def fishing-schema
  (mu/merge
   common-props-schema
   (collect-schema (:props fishing))))

(def outdoor-recreation-facilities
  {:label       {:fi "Retkeily ja ulkoilurakenteet"}
   :value       "outdoor-recreation-facilities"
   :description {:fi ""}
   :type-codes  #{207 205 206 202 301 302 304 #_204}
   :sort-order [:description-short
                :rules
                :arrival
                :accessibility
                :contacts
                :additional-info-link
                :images
                :videos]
   :props       (-> common-props
                    (dissoc :description-long :highlights)
                    (assoc-in [:rules :field :description :fi] "Liikkumis- tai toimintaohjeet, joiden avulla ohjataan toimintaa. Tässä voidaan kertoa myös mahdollisista liikkumis- tai toimintarajoituksista. HUOM! Täytä kenttä vain, jos rakenteen käyttöön liittyy jotakin erityistä huomautettavaa.")
                    (assoc-in [:arrival :field :description :fi] "Eri kulkumuodoilla kohteeseen pääsyyn liittyvää tietoa. Esim. pysäköintialueet ja joukkoliikenneyhteydet. HUOM! Täytä kenttä vain, jos kohteelle saapumiseen liittyy jotakin erityistä huomautettavaa.")
                    (assoc-in [:accessibility :field :description :fi] "Yleistä tietoa kohteen esteettömyydestä  tai kuljettavuudesta")
                    )})


(def outdoor-recreation-facilities-schema
  (collect-schema (:props outdoor-recreation-facilities)))

(def activities-schema
  [:map
   [:outdoor-recreation-areas {:optional true} outdoor-recreation-areas-schema]
   [:outdoor-recreation-facilities {:optional true} outdoor-recreation-facilities-schema]
   [:outdoor-recreation-routes {:optional true} outdoor-recreation-routes-schema]
   [:cycling {:optional true} cycling-schema]
   [:paddling {:optional true} paddling-schema]
   [:birdwatching {:optional true} birdwatching-schema]
   [:fishing {:optional true} fishing-schema]])

(comment
  (m/schema activities-schema)
  (require '[clojure.pprint :as pprint])
  (pprint/pprint activities-schema)

  (require '[malli.json-schema :as json-schema])
  (json-schema/transform activities-schema)

  (require '[cheshire.core :as json])

  (println (-> activities-schema json-schema/transform json/encode))

  )

(defn gen-json-schema
  []
  (-> activities-schema
      json-schema/transform
      #?(:clj(json/encode {:pretty true})
         :cljs clj->js)
      println))

(def by-types
  (utils/index-by :type-codes [outdoor-recreation-areas
                               outdoor-recreation-facilities
                               outdoor-recreation-routes
                               #_cycling
                               #_paddling
                               #_birdwatching
                               #_fishing]))

(def csv-headers
  ["Aktiviteetti nimi fi"
   "Aktiviteetti nimi se"
   "aktiviteetti nimi en"
   "Aktiviteetti kuvaus fi"
   "Aktiviteetti kuvaus se"
   "Aktiviteetti kuvaus en"
   "Aktiviteetti tekninen nimi"
   "LIPAS tyyppikoodit"
   "Ominaisuus tekninen nimi"
   "Ominaisuus tyyppi"
   "Ominaisuus nimi fi"
   "Ominaisuus nimi se"
   "Ominaisuus nimi en"
   "Ominaisuus kuvaus fi"
   "Ominaisuus kuvaus se"
   "Ominaisuus kuvaus en"])

(declare gen-csv)

#?(:clj
   (defn gen-csv
     []
     (->>
      (for [a             (vals by-types)
            :let          [rprops (get-in a [:props :routes :field :props])]
            [prop-k prop] (merge (:props a) rprops)]
        [(get-in a [:label :fi])
         (get-in a [:label :se])
         (get-in a [:label :en])
         (get-in a [:description :fi])
         (get-in a [:description :se])
         (get-in a [:description :en])
         (get-in a [:value])
         (-> a :type-codes (->> (str/join " ")))
         (name prop-k)
         (get-in prop [:field :type])
         (get-in prop [:field :label :fi])
         (get-in prop [:field :label :se])
         (get-in prop [:field :label :en])
         (get-in prop [:field :description :fi])
         (get-in prop [:field :description :se])
         (get-in prop [:field :description :en])])
      (into [csv-headers])
      (csv/write-csv *out*))))

(defn hack-missing-translations
  [m]
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (contains? x :fi))
       (assoc x :se "Inte translation" :en "Missing translation")
       x))
   m))

#_(hack-missing-translations outdoor-recreation-routes)

(def by-type-code
  (->> by-types
       (mapcat (fn [[type-codes v]]
                 (for [type-code type-codes]
                   [type-code (hack-missing-translations v)])))
       (into {})))

(def activities (->> by-types vals (utils/index-by :value)))

(defn -main [& args]
  (if (= "csv" (first args))
    (gen-csv)
    (gen-json-schema)))

(comment

  (json-schema/transform birdwatching-schema)
  (json-schema/transform activities-schema)

  )
