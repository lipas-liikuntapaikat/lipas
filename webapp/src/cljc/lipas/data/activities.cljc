(ns lipas.data.activities
  (:require
   #?(:clj [cheshire.core :as json])
   #?(:clj [clojure.data.csv :as csv])
   [clojure.string :as str]
   [clojure.walk :as walk]
   [lipas.data.materials :as materials]
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

(def percentage-schema
  (let [props {:min 0 :max 100}]
    [:or [:int props] [:double props]]))

(def duration-schema
  [:map
   [:min {:optional true} number-schema]
   [:max {:optional true} number-schema]
   [:unit {:optional true} [:enum "days" "hours" "minutes"]]])

(def surface-material-schema
  [:sequential (into [:enum] (keys materials/surface-materials))])

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
     "Pysäköintialueella on jäteastia. Tuo roskapussi ja vie roskat jäteastiaan.",
     :en
     "There are waste bins in the parking area. Bring a garbage bag and dispose of the garbage in the waste bin.",
     :sv
     "Det finns soptunnor på parkeringsområdet. Ta med en soppåse och lägg i soporna i soptunnan."},
    :value "bring-garbage-bag-bins-available"},

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

   "overnight-stay-guest-harbour-allowed"
   {:label
    {:fi "Yöpyminen retkisatamassa on sallittu",
     :en "Overnight stay in the guest harbour is allowed",
     :sv "Övernattning i gästhamnen är tillåten"},
    :description
    {:fi "Retkisatamassa saa pitää venettä ja yöpyä enintään 2 vuorokautta.",
     :en "You are allowed to keep a boat and stay for a maximum of 2 days.",
     :sv "Du får behålla en båt och övernatta i gästhamnen i högst 2 dagar."},
    :value "overnight-stay-guest-harbour-allowed"},

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
    :description
    {:fi "Tilapäinen leiriytyminen jokaisenoikeuksilla on sallittua lukuunottamatta pysäköintipaikkoja. Matkailuautoissa saa yöpyä pysäköintialueella.",
     :en "Temporary camping under everyman's rights is allowed, excluding parking areas. It is permitted to stay overnight in a motorhome in the parking area.",
     :sv "Tillfälligt camping med allemansrätt är tillåtet, med undantag av parkeringsplatser. Det är tillåtet att övernatta i en husbil på parkeringsplatsen."},
    :value "temporary-camping-allowed"},

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
    {:fi "Maastopalovaroituksen aikaan tulenteko on kielletty."
     :en "During a wildfire warning, making a fire is prohibited."
     :sv "Under en skogsbrandsvarning är det förbjudet att göra upp eld."
     },
    :value "only-fire-in-designated-places"},

   "only-fire-in-designated-places-during-wildfire-warning"
   {:label
    {:fi "Tee tulet vain sallituilla paikoilla"
     :en "Only make fire in designated places",
     :sv "Gör endast upp eld på angivna platser"}
    :description
    {:fi "Tarkista aina voimassa oleva maastopalovaroitus ennen tulentekoa. Maastopalovaroituksen aikana tulen saa tehdä vain hormillisissa keittokatoksissa."
     :en "Always check the current wildfire warning before making a fire. During a wildfire warning, fires can only be made in cooking shelters with chimneys."
     :sv "Kontrollera alltid den gällande skogsbrandsvarningen innan du gör upp eld. Under en skogsbrandsvarning får eld endast göras i matlagningskåpor med skorsten."}
    :value "only-fire-in-designated-places-during-wildfire-warning"}

   "camping-forbidden"
   {:label
    {:fi "Leiriytyminen alueella on kielletty",
     :en "Camping in the area is forbidden",
     :sv "Camping i området är förbjuden"},
    :description {:fi "", :en "", :sv ""},
    :value       "camping-forbidden"}

   "use-delivered-firewood-or-bring-own"
   {:label
    {:fi "Tulentekopaikalla saa käyttää ainoastaan paikalle toimitettuja tai itse tuotuja polttopuita."
     :en "Only firewood provided on site or brought by yourself can be used."
     :sv "På eldstaden får endast ved som tillhandahålls på plats eller som du själv tar med dig användas."}
    :description
    {:fi "Muista puiden kohtuukäyttö."
     :en "Remember the moderate use of firewood."
     :sv "Kom ihåg det måttliga användandet av ved."}
    :value "use-delivered-firewood-or-bring-own"}

   "bring-own-firewood"
   {:label       {:fi "Tuo omat polttopuut" :en "Bring your own firewood" :sv "Ta med egen ved"}
    :description {:fi "Alueella ei ole polttopuuhuoltoa." :en "There is no firewood service in the area." :sv "Det finns ingen vedservice i området."}
    :value       "bring-own-firewood"}

   "disposable-grills-forbidden"
   {:label
    {:fi "Risukeittimien ja kertakäyttögrillien käyttö on kielletty."
     :en "The use of twig stoves and disposable grills is prohibited."
     :sv "Användning av kvistkockare och engångsgrillar är förbjuden."}
    :description {:fi "" :en "" :sv ""}
    :value       "disposable-grills-forbidden"}

   "keep-pets-leashed-poop-ok"
   {:label {:fi "Pidä lemmikit aina kytkettynä" :en "Always keep your pets leashed" :sv "Håll alltid dina husdjur kopplade"}
    :description
    {:fi "Koirien jätökset tulee siivota poluista sivummalle."
     :en "Dog waste should be cleaned up and moved off the trails."
     :sv "Hundavfall ska städas upp och flyttas bort från stigarna."}
    :value "keep-pets-leashed-poop-ok"}

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

(def common-route-props
  (-> common-props
      (assoc-in [:description-long :field :description :fi]
                "Tarkempi reitin eri vaiheiden kuvaus. Esim. kuljettavuus, nähtävyydet, taukopaikat ja palvelut. Erota vaiheet omiksi kappaleiksi.")
      (assoc-in [:description-short :field :description :fi]
                "1-3 lauseen esittely reitistä ja sen erityispiirteistä.")))

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
                [:route-length-km {:optional true} number-schema]
                [:surface-material {:optional true} surface-material-schema]
                [:accessibility-classification
                 (into [:enum] (keys accessibility-classification))]
                [:independent-entity {:optional true} [:boolean]]])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
      :label       {:fi "Reittiosan tyyppi"}
      :props
      (merge
       (-> common-route-props
           (dissoc :rules :accessibility)
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

(def cycling-difficulty
  {"1-easy"                   {:fi "1 - Helppo"}
   "2-somewhat-challenging"   {:fi "2 - Osittain vaativa"}
   "3-moderately-challenging" {:fi "3 - Keskivaativa"}
   "4-challenging"            {:fi "4 - Vaativa"}
   "5-extremely-challenging"  {:fi "5 - Erittäin vaativa"}})

(def cycling
  {:label       {:fi "Pyöräily"}
   :value       "cycling"
   :description {:fi ""}
   :type-codes  #{4411 4412}
   :sort-order  [:route-name
                :description-short
                :description-long
                :route-notes
                :highlights
                :cycling-activities
                :route-length-km
                :duration
                :cycling-difficulty
                :surface-material
                :unpaved-percentage
                :trail-percentage
                :cyclable-percentage
                :arrival
                :accommodation
                :food-and-water
                :good-to-know
                :accessibility
                :contacts
                :additional-info-link
                :images
                :videos]
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
                [:cycling-difficulty {:optional true}
                 (into [:enum] (keys cycling-difficulty))]
                [:duration {:optional true} duration-schema]
                [:food-and-water {:optional true} localized-string-schema]
                [:accommodation {:optional true} localized-string-schema]
                [:good-to-know {:optional true} localized-string-schema]
                [:route-notes {:optional true} localized-string-schema]
                [:route-length-km {:optional true} number-schema]
                [:surface-material {:optional true} surface-material-schema]
                [:unpaved-percentage {:optional true} percentage-schema]
                [:trail-percentage {:optional true} percentage-schema]
                [:cyclable-percentage {:optional true} percentage-schema]])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
      :label       {:fi "Reittityyppi"}
      :props
      (merge
       (dissoc common-route-props :rules)
       {:route-name
        {:field
         {:type        "text-field"
          :description {:fi "Anna reitille kuvaava nimi, esim. sen maantieteellisen sijainnin tai reitin päätepisteiden mukaan."}
          :label       {:fi "Reitin nimi"}}}
        :cycling-activities
        {:field
         {:type        "multi-select"
          :description {:fi "Valitse reitille soveltuvat pyöräilylajit"}
          :label       {:fi "Alalaji"}
          :opts        (dissoc cycling-activities "road-cycling")}}

        :cycling-difficulty
        {:field
         {:type        "select"
          :description {:fi "Haastavuus"}
          :label       {:fi "Reitin arvioitu haastavuus"}
          :opts        cycling-difficulty}}

        :duration
        {:field
         {:type        "duration"
          :description {:fi "Kulkuaika"}
          :label       {:fi "Reitin arvioitu kulkuaika"}}}

        :food-and-water
        {:field
         {:type        "textarea"
          :description {:fi "Tietoa reitin varrella olevista ruokailu- ja juomapaikoista ja/tai ohjeet omasta ruoka- ja juomahuollosta."}
          :label       {:fi "Ruoka & juoma"}}}

        :accommodation
        {:field
         {:type        "textarea"
          :description {:fi "Tietoa reitin varrella olevista majoitusmahdollisuuksista ja -palveluista."}
          :label       {:fi "Majoitus"}}}

        :good-to-know
        {:field
         {:type        "textarea"
          :description {:fi "Tietoa reittiin tai reitillä liikkumiseen liittyvistä säännöistä ja ohjeista."}
          :label       {:fi "Hyvä tietää"}}}

        :route-notes
        {:field
         {:type        "textarea"
          :description {:fi "Reitin tarkempi kuvaus reittiosuuksittain sekä huomautukset erityisen vaativista osuuksista tai vaaranpaikoista. Erottele eri vaiheet omiksi kappaleikseen."}
          :label       {:fi "Reittimuistiinpanot"}}}

        :unpaved-percentage
        {:field
         {:type        "percentage"
          :description {:fi "Kuinka suuri osuus reitistä on päällystämätöntä?"}
          :label       {:fi "Päällystämätöntä"}}}

        :trail-percentage
        {:field
         {:type        "percentage"
          :description {:fi "Kuinka suuri osuus reitistä on polkua?"}
          :label       {:fi "Polkua"}}}

        :cyclable-percentage
        {:field
         {:type        "percentage"
          :description {:fi "Kuinka suuri osuus reitistä on pyöräiltävissä?"}
          :label       {:fi "Pyöräiltävissä"}}}

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
          :description    {:fi "Valitse kaikki pintamateriaalit, joita reitillä kuljetaan"}}}})}}}})

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
  "Paddling is a bastard child and it contains

  - routes (4451 4452)
  - points (5150)

  ... and different content needs to be collected for paddling
  centers. This is resolved by mapping relevant props to each type
  in :type->props"
  {:label       {:fi "Melonta ja SUP"}
   :value       "paddling"
   :description {:fi ""}
   :type-codes  #{4451 4452 5150}
   :type->props {4451 #{:routes}
                 4452 #{:routes}
                 5150 (into #{:equipment-rental?
                              :rapid-canoeing-centre?
                              :canoeing-club?
                              :activity-service-company?} (keys common-props))}
   :sort-order  [:route-name
                 :description-short
                 :description-long
                 :highlights
                 :paddling-route-type
                 :paddling-activities
                 :route-length-km
                 :duration
                 :travel-direction
                 :paddling-difficulty
                 :paddling-properties
                 :equipment-rental?
                 :rapid-canoeing-centre?
                 :canoeing-club?
                 :activity-service-company?
                 :arrival
                 :good-to-know
                 :rules
                 :safety
                 :accessibility
                 :contacts
                 :additional-info-link
                 :images
                 :videos]
   :props
   (merge #_common-props {}
          {:equipment-rental?
           {:schema [:boolean]
            :field
            {:type           "lipas-property"
             :lipas-property :equipment-rental?
             :label          {:fi "Välinevuokraus"
                              :se ""
                              :en ""}
             :description    {:fi "Välinevuokraus mahdollista."
                              :se ""
                              :en ""}}}

           :rapid-canoeing-centre?
           {:schema [:boolean]
            :field
            {:type           "lipas-property"
             :lipas-property :rapid-canoeing-centre?
             :label          {:fi "Koskimelontakeskus"
                              :se "Centrum för paddling"
                              :en "Rapid canoeing centre"}
             :description    {:fi "Kilpailujen järjestäminen mahdollista."
                              :se "Möjligt att arrangera tävlingar."
                              :en "Competitions possible."}}}

           :canoeing-club?
           {:schema [:boolean]
            :field
            {:type           "lipas-property"
             :lipas-property :canoeing-club?
             :label          {:fi "Melontaseura"
                              :se ""
                              :en "Canoeing club"}
             :description    {:fi "Onko kyseessä melontaseuran tila."
                              :se ""
                              :en ""}}}

           :activity-service-company?
           {:schema [:boolean]
            :field
            {:type           "lipas-property"
             :lipas-property :activity-service-company?
             :label          {:fi "Ohjelmapalveluyritys"}
             :description    {:fi "Toimiiko kohteessa ohjelmapalveluyritys."}}}

           :routes
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
                       [:travel-direction {:optional true} [:enum "clockwise" "counter-clockwise"]]
                       [:safety {:optional true} localized-string-schema]
                       [:good-to-know {:optional true} localized-string-schema]
                       [:duration {:optional true} duration-schema]])]
            :field
            {:type        "routes"
             :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
             :label       {:fi "Reittityyppi"}
             :props
             (merge
              common-route-props
              {:route-name
               {:field
                {:type        "text-field"
                 :description {:fi "Anna reitille kuvaava nimi, esim. sen maantieteellisen sijainnin tai reitin päätepisteiden mukaan."}
                 :label       {:fi "Reitin nimi"}}}

               :paddling-activities
               {:field
                {:type        "multi-select"
                 :description {:fi "Valitse soveltuvat melontatavat"}
                 :label       {:fi "Aktiviteetti"}
                 :opts        paddling-activities}}

               :paddling-route-type
               {:field
                {:type        "multi-select"
                 :description {:fi "Valitse, minkä tyyppinen melontakohde (-reitti) on kyseessä."}
                 :label       {:fi "Melontakohteen tyyppi"}
                 :opts        paddling-route-types}}

               :paddling-properties
               {:field
                {:type        "multi-select"
                 :description {:fi "Valitse kohdat, jotka kuvaavat reitin ominaisuuksia."}
                 :label       {:fi "Ominaisuudet"}
                 :opts        paddling-properties}}

               :paddling-difficulty
               {:field
                {:type        "select"
                 :description {:fi "Haastavuus"}
                 :label       {:fi "Reitin arvioitu haastavuus."}
                 :opts        paddling-difficulty}}

               :safety
               {:field
                {:type        "textarea"
                 :description {:fi "Lisää reitin turvallisuuteen liittyvää tietoa esim. kuvaile vaativuutta, suositeltavaa osaamistasoa tai kalustoa."}
                 :label       {:fi "Turvallisuus"}}}

               :good-to-know
               {:field
                {:type        "textarea"
                 :description {:fi "Syötä tähän asioita, joista vesilläliikkujan on hyvä tietää (esim. matkapuhelimen kuuluvuuden katvealueet)."}
                 :label       {:fi "Hyvä tietää"}}}

               :duration
               {:field
                {:type        "duration"
                 :description {:fi "Reitin arvioitu kulkuaika"}
                 :label       {:fi "Kulkuaika"}}}

               :route-length-km
               {:field
                {:type           "lipas-property"
                 :lipas-property :route-length-km
                 :label          {:fi "Reitin pituus (km)"}
                 :description    {:fi "Reitin pituus kilometreinä (voit syöttää tiedon käsin tai laskea sen automaattisesti)"}}}

               :travel-direction
               {:field
                {:type        "select"
                 :opts        {"clockwise"         {:fi "Myötäpäivään"}
                               "counter-clockwise" {:fi "Vastapäivään"}}
                 :description {:fi "Valitse reitin kulkusuunta, myötäpäivään/vastapäivään, jos reitillä on suositeltu kulkusuunta."}
                 :label       {:fi "Kulkusuunta"}}}})}}})})

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
   :type-codes  #{201 113}
   :sort-order [:description-short
                :description-long
                :highlights
                :fishing-type
                :fishing-waters
                :fishing-species
                :fish-population
                :fishing-activities
                :fishing-methods
                :fishing-permit
                :fishing-permit-additional-info
                :rules
                :arrival
                :accessibility-classification
                :accessibility-categorized
                :accessibility
                :contacts
                :additional-info-link
                :images
                :videos]
   :props
   (merge common-props
    {:fishing-type
     {:schema [:sequential (into [:enum] (keys fishing-types))]
      :field
      {:type        "multi-select"
       :description {:fi "Valitse, mistä kohteessa voi kalastaa"}
       :label       {:fi "Kohdetyyppi"}
       :opts        fishing-types}}

     :fishing-activities
     {:schema [:sequential (into [:enum] (keys fishing-activities))]
      :field
      {:type        "multi-select"
       :description {:fi "Valitse soveltuvat kalastusmuodot"}
       :label       {:fi "Hyvin soveltuvat kalastusmuodot"}
       :opts        fishing-activities}}

     :fishing-waters
     {:schema (into [:enum] (keys fishing-waters))
      :field
      {:type        "select"
       :description {:fi "Valitse vesistön tyyppi"}
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
       :description {:fi "Kirjoita tähän kuvaus kohteen vesistössä esiintyvästä kalastosta."}
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
       :description {:fi "Valitse kohteen kalastuslupatarve yhdellä vavalla kalastettaessa. Huom. useammalla vavalla kalastaminen vaatii aina paikallisen luvan."}
       :opts        fishing-permit-opts}}

     :fishing-permit-additional-info
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Syötä tähän tarvittaessa lisätietoa kalastuslupia koskevista muista asioista"}
       :label       {:fi "Kalastuslupatarpeen lisätiedot"}}}

     :accessibility-classification
     {:schema (into [:enum] (keys accessibility-classification))
      :field
      {:type        "select"
       :label       {:fi "Esteettömyysluokittelu"}
       :description {:fi "Valitse onko kohde esteellinen tai esteetön."}
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
       :description {:fi "Yleistä tietoa kohteen esteettömyydestä"}
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
                               cycling
                               paddling
                               #_birdwatching
                               fishing]))

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
