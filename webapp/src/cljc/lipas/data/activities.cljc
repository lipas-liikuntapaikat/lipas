(ns lipas.data.activities
  (:require
   #?(:clj [cheshire.core :as json])
   #?(:clj [clojure.data.csv :as csv])
   #?(:clj [clojure.string :as str])
   [lipas.data.materials :as materials]
   [lipas.schema.common :as common-schema]
   [lipas.utils :as utils]
   [malli.core :as m]
   [malli.json-schema :as json-schema]
   [malli.util :as mu]))

(defn collect-schema
  [m]
  (into [:map] (map (juxt first (constantly {:optional true}) (comp :schema second)) m)))
(def duration-schema
  [:map
   [:min {:optional true} common-schema/number]
   [:max {:optional true} common-schema/number]
   [:unit {:optional true} [:enum "days" "hours" "minutes"]]])

(def surface-material-schema
  [:sequential (into [:enum] (keys materials/surface-materials))])

(def contact-roles
  {"admin"            {:fi "Ylläpitäjä"
                       :se "Administratör"
                       :en "Administrator"}
   "content-creator"  {:fi "Sisällöntuottaja"
                       :se "Innehållsproducent"
                       :en "Content producer"}
   "customer-service" {:fi "Asiakaspalvelu"
                       :se "Kundtjänst"
                       :en "Customer service"}})

(def common-rules
  {"everymans-rights-valid"
   {:label
    {:fi "Jokaisenoikeudet ovat voimassa tällä alueella",
     :en "Everyone's rights apply in this area",
     :se "Allemansrätten gäller på detta område"},
    :description {:fi "", :en "", :se ""},
    :value       "everymans-rights-valid"},

   "bring-garbage-bag-bins-available"
   {:label
    {:fi "Muista tuoda roskapussi",
     :en "Remember to bring a garbage bag",
     :se "Se till att ta med en soppåse"},
    :description
    {:fi "Pysäköintialueella on jäteastia. Tuo roskapussi ja vie roskat jäteastiaan.",
     :en "There is a waste bin in the parking area. Bring a garbage bag and take the garbage to the waste bin.",
     :se "Det finns en soptunna på parkeringsplatsen. Ta med en soppåse och ta med soporna till soptunnan."},
    :value "bring-garbage-bag-bins-available"},

   "bring-garbage-bag-no-bins"
   {:label
    {:fi "Muista tuoda roskapussi",
     :en "Remember to bring a garbage bag",
     :se "Se till att ta med en soppåse"},
    :description
    {:fi "Alueella ei ole jäteastioita. Tuo roskapussi ja vie roskat mennessäsi.",
     :en "There are no waste bins in the area. Bring a garbage bag and take the trash with you.",
     :se "Det finns inga sopkärl på området. Ta med en soppåse och ta med dig soporna."},
    :value "bring-garbage-bag-no-bins"},

   "overnight-stay-guest-harbour-allowed"
   {:label
    {:fi "Yöpyminen retkisatamassa on sallittu",
     :en "Overnight accommodation in the excursion harbour is allowed",
     :se "Övernattning i utflyktshamnen är tillåten"},
    :description
    {:fi "Retkisatamassa saa pitää venettä ja yöpyä enintään 2 vuorokautta.",
     :en "It is allowed to keep a boat and stay overnight in the excursion harbour for a maximum of 2 days.",
     :se "Du får behålla en båt och övernatta i utflyktshamnen i max 2 dygn."},
    :value "overnight-stay-guest-harbour-allowed"},

   "fire-only-at-marked-fireplaces"
   {:label
    {:fi "Tulenteko on sallittu ainoastaan merkityillä tulentekopaikoilla",
     :en "Campfires are allowed only at marked campfire sites",
     :se "Det är tillåtet att göra upp eld endast på markerade eldplatser"},
    :description
    {:fi "Maastopalovaroituksen aikaan tulenteko on kokonaan kielletty. Tulentekopaikalla saa käyttää vain alueella tarjolla olevia tai itse tuotuja polttopuita.",
     :en "During a wildfire warning, making fire is completely forbidden. Only firewood delivered to the site or brought in by yourself may be used at the campfire site.",
     :se "Under en varning för skogsbrand är det helt förbjudet att göra upp eld. Endast ved som levererats till platsen eller som du själv tar med dig får användas på eldplatsen."},
    :value "fire-only-at-marked-fireplaces"},

   "temporary-camping-allowed"
   {:label
    {:fi "Tilapäinen leiriytyminen alueella on sallittu",
     :en "Temporary camping in the area is allowed",
     :se "Tillfällig tältning på området är tillåten"},
    :description
    {:fi "Tilapäinen leiriytyminen jokaisenoikeuksilla on sallittua lukuunottamatta pysäköintipaikkoja. Matkailuautoissa saa yöpyä pysäköintialueella.",
     :en "Temporary camping under everyman's rights is permitted, except for parking spaces. It is allowed to stay overnight in motorhomes in the parking lot.",
     :se "Tillfällig camping med allemansrätter är tillåten, med undantag för parkeringsplatser. Det är tillåtet att övernatta i husbilar på parkeringen."},
    :value "temporary-camping-allowed"},

   "overnight-stay-not-allowed"
   {:label
    {:fi "Yöpyminen alueella on kielletty",
     :en "Overnight accommodation in the area is prohibited",
     :se "Det är förbjudet att övernatta i området."},
    :description {:fi "", :en "", :se ""},
    :value       "overnight-stay-not-allowed"},

   "only-fire-in-designated-places"
   {:label
    {:fi "Tee tulet vain sallituilla paikoilla",
     :en "Make fire only in permitted places",
     :se "Göra upp eld endast på tillåtna platser"},
    :description
    {:fi "Maastopalovaroituksen aikaan tulenteko on kielletty.",
     :en "During a wildfire warning, making a fire is forbidden.",
     :se "Under en varning för skogsbrand är det förbjudet att göra upp eld."},
    :value "only-fire-in-designated-places"},

   "only-fire-in-designated-places-during-wildfire-warning"
   {:label
    {:fi "Tee tulet vain sallituilla paikoilla",
     :en "Make fire only in permitted places",
     :se "Göra upp eld endast på tillåtna platser"}
    :description
    {:fi "Tarkista aina voimassa oleva maastopalovaroitus ennen tulentekoa. Maastopalovaroituksen aikana tulen saa tehdä vain hormillisissa keittokatoksissa.",
     :en "Always check the current wildfire warning before making a fire. During a wildfire warning, fires can only be made in cooking shelters with chimneys.",
     :se "Kontrollera alltid den gällande varningen för skogsbrand innan du gör upp eld. Under brandvarning får eldning endast göras upp i kokskjul med rökkanaler."},
    :value "only-fire-in-designated-places-during-wildfire-warning"},

   "camping-forbidden"
   {:label
    {:fi "Leiriytyminen alueella on kielletty",
     :en "Camping in the area is forbidden",
     :se "Det är förbjudet att tälta på området"},
    :description {:fi "", :en "", :se ""},
    :value       "camping-forbidden"},

   "use-delivered-firewood-or-bring-own"
   {:label
    {:fi "Tulentekopaikalla saa käyttää ainoastaan paikalle toimitettuja tai itse tuotuja polttopuita.",
     :en "Only firewood delivered to the site or brought in by yourself may be used at the campfire site.",
     :se "Endast ved som levererats till platsen eller tagits in av dig själv får användas på eldplatsen."}
    :description
    {:fi "Muista puiden kohtuukäyttö.",
     :en "Remember the moderate use of firewood.",
     :se "Kom ihåg den måttliga användningen av ved."},
    :value "use-delivered-firewood-or-bring-own"},

   "bring-own-firewood"
   {:label       {:fi "Tuo omat polttopuut", :en "Bring your own firewood", :se "Ta med egen ved"},
    :description {:fi "Alueella ei ole polttopuuhuoltoa.", :en "There is no firewood supply in the area.", :se "Det finns ingen vedförsörjning i området."},
    :value       "bring-own-firewood"},

   "disposable-grills-forbidden"
   {:label
    {:fi "Risukeittimien ja kertakäyttögrillien käyttö on kielletty.",
     :en "The use of twig stoves and disposable barbecues is prohibited.",
     :se "Det är förbjudet att använda vildmarkskök och engångsgrillar."},
    :description {:fi "", :en "", :se ""},
    :value       "disposable-grills-forbidden"},

   "keep-pets-leashed-poop-ok"
   {:label       {:fi "Pidä lemmikit aina kytkettynä", :en "Always keep pets on a leash", :se "Håll husdjur kopplade hela tiden"},
    :description {:fi "Koirien jätökset tulee siivota poluista sivummalle.", :en "Remove dog droppings away from the paths.", :se "Hundspillning bör rensas bort från stigarna."},
    :value       "keep-pets-leashed-poop-ok"},

   "keep-pets-leashed-poop-not-ok"
   {:label       {:fi "Pidä lemmikit aina kytkettynä", :en "Always keep pets on a leash", :se "Håll husdjur kopplade hela tiden"},
    :description {:fi "Muista myös koirankakkapussi ja korjaa jätökset pois.", :en "Also remember the dog poop bag and remove the droppings.", :se "Glöm inte att packa hundbajs och ta bort spillning."},
    :value       "keep-pets-leashed-poop-not-ok"}})


(def rules-structured-schema
  [:map
   [:common-rules [:sequential (into [:enum] (keys common-rules))]]
   [:custom-rules {:optional true}
    [:sequential
     [:map
      [:label {:optional true} common-schema/localized-string]
      [:description {:optional true} common-schema/localized-string]
      [:value {:optional true} [:string {:min 2}]]]]]])

(def status-opts
  {"draft" {:fi "Luonnos"
            :se "Utkast"
            :en "Draft"}
   "active" {:fi "Aktiivinen"
             :se "Aktiv"
             :en "Active"}})

(def common-props
  {:status
   {:schema [:enum "draft" "active"]
    :field
    {:type "select"
     ;; NOTE: select default value has to be be manually applied into the data during save or somewhere
     ;; for this field, it is done on lipas.ui.utils/make-saveable
     :default "active"
     :label   {:fi "UTP-tietojen tila"
               :se "Status för UTP-information"
               :en "Status of UTP data"}
     :description {:fi "Aktiivisia tietoja voidaan siirtää Lipas-järjestelmästä eteenpäin. Luonnos-tilaiset tiedot eivät siirry eteenpäin."
                   :se "Aktiv data kan överföras vidare från Lipas-systemet. Data med status utkast överförs inte vidare."
                   :en "Active data can be transferred onward from the Lipas system. Draft status data will not be transferred onward."}
     :opts status-opts}}

   :description-short
   {:schema common-schema/localized-string
    :field
    {:type        "textarea"
     :description {:fi "1-3 lauseen esittely kohteesta ja sen erityispiirteistä."
                   :se "Presentation av 1–3 meningar om platsen och dess detaljer."
                   :en "Overview in 1 to 3 sentences describing the place and its characteristics."}
     :label       {:fi "Yleiskuvaus"
                   :se "Allmän beskrivning"
                   :en "Overview"}}}

   :description-long
   {:schema common-schema/localized-string
    :field
    {:type        "textarea"
     :description {:fi "Yleiskuvausta jatkava, laajempi kuvaus kohteesta ja sen ominaisuuksista"
                   :se "En mer detaljerad beskrivning av platsens olika egenskaper."
                   :en "Continuing the overview, a more detailed, extended description of the place."}
     :label       {:fi "Kohdekuvaus"
                   :se "Beskrivning av platsen"
                   :en "Place description"}}}

   :contacts
   {:schema [:sequential
             [:map
              [:organization {:optional true} common-schema/localized-string]
              [:role {:optional true} [:sequential (into [:enum] (keys contact-roles))]]
              [:email {:optional true} common-schema/localized-string]
              [:www {:optional true} common-schema/localized-string]
              [:phone-number {:optional true} common-schema/localized-string]]]
    :field
    {:type        "contacts"
     :description {:fi "Syötä kohteesta vastaavien tahojen yhteystiedot"
                   :se "Ange kontaktuppgifter till de parter som ansvarar för platsen"
                   :en "Enter contact information of the parties responsible for the place."}
     :label       {:fi "Yhteystiedot"
                   :se "Kontaktuppgifter"
                   :en "Contact information"}
     :props
     {:organization
      {:field
       {:type        "text-field"
        :label       {:fi "Organisaatio"
                      :se "Organisationen"
                      :en "Organization"}
        :description {:fi "Organisaation nimi"
                      :se "Organisationens namn"
                      :en "Name of the organization"}}}
      :role
      {:field
       {:type        "multi-select"
        :label       {:fi "Rooli"
                      :se "Roll"
                      :en "Role"}
        :description {:fi [:<>
                           "Asiakaspalvelu: Kohteen asiakaspalvelusta vastaava organisaatio"
                           [:br]
                           "Sisällöntuottaja: Kohdetta kuvailevista LIPAS-tietosisällöistä vastaava organisaatio"
                           [:br]
                           "Ylläpitäjä: Kohteen rakenteiden ja olosuhteiden ylläpidosta vastaava organisaatio"]
                      :se [:<>
                           "Kundtjänst: Organisation som ansvarar för kundservice "
                           [:br]
                           "Innehållsproducent: Organisation som ansvarar för LIPAS-datainnehåll som beskriver platsen"
                           [:br]
                           "Administratör: Organisation som ansvarar för att upprätthålla platsens strukturer och förhållanden"]
                      :en [:<>
                           "Customer service: The organisation responsible for the customer service of the place."
                           [:br]
                           "Content producer: The organisation responsible for the LIPAS content."
                           [:br]
                           "Administrator: The organisation responsible for the administration of the structures and facilities concerning the place."]}
        :opts        contact-roles}}
      :email
      {:field
       {:type        "text-field"
        :label       {:fi "Sähköposti"
                      :se "E-post"
                      :en "Email"}
        :description {:fi "Organisaation sähköpostiosoite (syötä vain yksi sähköpostiosoite)"
                      :se "Organisationens e-postadress (ange endast en e-postadress)"
                      :en "Organization's email address (enter only one email address)"}}}

      :www
      {:field
       {:type        "text-field"
        :label       {:fi "Web-osoite"
                      :se "Webbadress"
                      :en "Website"}
        :description {:fi "Organisaation verkkosivu (syötä vain yksi verkko-osoite)"
                      :se "Organisationens webbplats (ange endast en webbadress)"
                      :en "Organization's website (enter only one web address)"}}}

      :phone-number
      {:field
       {:type        "text-field"
        :label       {:fi "Puhelinnumero"
                      :se "Telefonnummer"
                      :en "Phone number"}
        :description {:fi "Organisaation puhelinnumero"
                      :se "Organisationens telefonnummer"
                      :en "Organization's phone number"}}}}}}

   :videos
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} common-schema/localized-string]]]
    :field
    {:type        "videos"
     :description {:fi "Lisää URL-linkki web-palvelussa olevaan kohteen maisemia, luontoa tai harrastamisen olosuhteita esittelevään videoon. Varmista, että sinulla on oikeus lisätä video."
                   :se "Lägg till en URL-länk till en video i webbtjänsten som presenterar landskapet, naturen eller förhållandena på platsen. Se till att du har behörighet att lägga till videon."
                   :en "Add a URL link to a video depicting the landscapes, nature, or conditions of activity of the place. Make sure you have a permission to add the video."}
     :label       {:fi "Videot"
                   :se "Videoinspelningar"
                   :en "Videos"}}}

   :images
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} common-schema/localized-string]
              [:alt-text {:optional true} common-schema/localized-string]
              [:copyright {:optional true} common-schema/localized-string]]]
    :field
    {:type        "images"
     :description {:fi "Lisää kohteen maisemia, luontoa tai harrastamisen olosuhteita esitteleviä valokuvia. Voit lisätä vain kuvatiedostoja, et URL-kuvalinkkejä. Kelvollisia tiedostomuotoja ovat .jpg, .jpeg ja .png. Varmista, että sinulla on oikeus lisätä kuva."
                   :se "Lägg till foton som visar landskapet, naturen eller rekreationsförhållandena på platsen. Du kan bara lägga till bildfiler, inte URL-bildlänkar. Giltiga filformat är .jpg, .jpeg och .png. Se till att du har behörighet att lägga till bilden."
                   :en "Add photographs depicting the landscapes, nature, or conditions of activity of the place. You can add only image files, not URL links. Allowed file extensions are .jpg, .jpeg and .png. Make sure that you have a permission to add the photographs."}
     :label       {:fi "Valokuvat"
                   :se "Foton"
                   :en "Photographs"}
     :props
     {:url
      {:field
       {:type   "text-field"
        :hidden true}}
      :description
      {:field
       {:type        "textarea"
        :description {:fi "Kuvan yhteydessä kaikille näytettävä teksti kuvassa esitettävistä asioista. Maksimissaan 255 merkkiä."
                      :se "Text som visas för alla i anslutning till bilden om vad som visas i bilden. Maximalt 255 tecken."
                      :en "Text to be displayed for everyone in connection with the image about what is shown in the image. A maximum of 255 characters."}
        :label       {:fi "Kuvateksti"
                      :se "Bildtext"
                      :en "Image caption"}}}
      :alt-text
      {:field
       {:type        "textarea"
        :description {:fi "Ruudunlukijan näkövammaisille kertoma teksti kuvassa esitettävistä asioista. Lue lisää: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"
                      :se "Text som skärmläsaren berättar för synskadade om vad som visas i bilden. Läs mer: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"
                      :en "Text that the screen reader tells visually impaired people about what is shown in the image. Read more: https://www.saavutettavasti.fi/kuva-ja-aani/kuvat/"}
        :label       {:fi "Alt-teksti"
                      :se "Alt-text"
                      :en "Alt-text"}}}
      :copyright
      {:field
       {:type        "textarea"
        :description {:fi "Syötä kuvan ottaja, kuvan lähde, mahdollinen lisenssi sekä päivämäärä, jos tiedossa."
                      :se "Ange fotografens namn, bildkälla, eventuell licens och datum om det är känt."
                      :en "Enter the photographer, image source, possible license, and date if known."}
        :label       {:fi "Tekijänoikeustiedot"
                      :se "Upphovsrättsinformation"
                      :en "Copyright information"}}}}}}

   :additional-info-link
   {:schema common-schema/localized-string
    :field
    {:type        "text-field"
     :description {:fi "Linkki ulkoisella sivustolla sijaitsevaan laajempaan kohde-esittelyyn"
                   :se "Webblänk till en större beskrivning av platsen på en extern webbplats"
                   :en "Web link to a broader presentation of the place on an external website."}
     :label       {:fi "Lisätietoa kohteesta saatavilla"
                   :se "Mer information tillgänglig"
                   :en "More information"}}}

   :rules
   {:schema common-schema/localized-string
    :field
    {:type        "textarea"
     :description {:fi "Liikkumis- tai toimintaohjeet, joiden avulla ohjataan toimintaa. Tässä voidaan kertoa myös mahdollisista liikkumis- tai toimintarajoituksista."
                   :se "Rörelse- eller bruksanvisning för att styra driften. Eventuella begränsningar av rörelsefriheten eller aktivitetsfriheten kan också beskrivas här."
                   :en "Instructions for guiding the activity in the region. This field can also contain information about possible restrictions regarding the activity."}
     :label       {:fi "Luvat, säännöt, ohjeet"
                   :se "Tillstånd, regler, anvisningar"
                   :en "Permits, regulations, instructions"}}}

   :arrival
   {:schema common-schema/localized-string
    :field
    {:type        "textarea"
     :description {:fi "Eri kulkumuodoilla kohteeseen pääsyyn liittyvää tietoa. Esim. pysäköintialueet ja joukkoliikenneyhteydet."
                   :se "Information om olika transportsätt att ta sig till destinationen. T.ex. parkeringsplatser och kollektivtrafikförbindelser."
                   :en "Information about how to get to the destination with different means of transport, e.g. parking areas and public transport."}
     :label       {:fi "Saapuminen"
                   :se "Ankomst"
                   :en "Arrival to destination"}}}

   :accessibility
   {:schema common-schema/localized-string
    :field
    {:type        "textarea"
     :description {:fi "Yleistä tietoa kohteen esteettömyydestä tai kuljettavuudesta"
                   :se "Allmän information om platsens tillgänglighet eller farbarhet"
                   :en "General information about the accessibility or passability of the place."}
     :label       {:fi "Esteettömyys"
                   :se "Tillgänglighet"
                   :en "Accessibility"}}}

   :highlights
   {:schema [:sequential common-schema/localized-string]
    :field
    {:type        "textlist"
     :description {:fi "Syötä 2-6 konkreettista kohteen erityispiirrettä, jotka täydentävät yleiskuvausta. Syötä yksi kohokohta kerrallaan. Käytä isoa Alkukirjainta."
                   :se "2–6 konkreta detaljer (höjdpunkter) hos platsen, som kompletterar översikten. Ange en detalj i taget. Använd versaler Initial."
                   :en "2 to 6 highlights depicting the distinctive features of the place and complement the overview. Enter 1 highlight at a time. Use a Capital initial."}
     :label       {:fi "Kohokohdat"
                   :se "Höjdpunkter"
                   :en "Highlights"}}}})


(def common-route-props
  (-> common-props
      (dissoc :status)
      (assoc-in [:description-long :field :description :fi]
                "Tarkempi reitin eri vaiheiden kuvaus. Esim. kuljettavuus, nähtävyydet, taukopaikat ja palvelut. Erota vaiheet omiksi kappaleiksi.")
      (assoc-in [:description-long :field :description :se]
                "En mer detaljerad beskrivning av ruttens olika etapper. T.ex. transportmöjligheter, attraktioner, rastplatser och tjänster. Dela upp stegen i egna stycken.")
      (assoc-in [:description-long :field :description :en]
                "A more detailed description of the different stages of the route. E.g. passability, attractions, rest areas and services. Split the stages into individual paragraphs.")
      (assoc-in [:description-short :field :description :fi]
                "1-3 lauseen esittely reitistä ja sen erityispiirteistä.")
      (assoc-in [:description-short :field :description :se]
                "Presentation av 1–3 meningar om rutten och dess detaljer.")
      (assoc-in [:description-short :field :description :en]
                "Presentation of 1-3 sentences about the route and its specific properties.")))

(def common-props-schema
  (collect-schema common-props))

(def common-route-props-schema
  (collect-schema common-route-props))

(comment
  (m/schema common-props-schema))

;; This field is added to multiple activities, but under some of them, it
;; is only displayed for certain type-codes.
;; :show callback is used to control which type-codes should display this field.
;; Currently only the route-form component in the UI side uses this :show option.
;;
;; outdoor-recreation-routes paitsi 4402 (hiihtolatu)
;; cycling (kaikki)
;; paddling paitsi 4452 (vesiretkeilyreitti) ja 5150 (ei routes dataa)
(def pilgrimage-field
  {:schema [:boolean {:optional true}]
   :field
   {:type "checkbox"
    :description {:fi "Jos kohde on pyhiinvaellusreitti, aktivoi liukukytkin. HUOM! Pyhiinvaellusreitti on ulkoilureitti, joka tarjoaa mahdollisuuden liikkumiseen, hiljentymiseen ja hengellisyyteen/henkisyyteen.  Reitin varrelle on rakennettu mobiilisti tai maastoon opasteita ja sisältöjä, jotka ohjaavat vaeltajaa."
                  :se ""
                  :en ""}
    :label {:fi "Pyhiinvaelluskohde"
            :se ""
            :en "Pilgrimage destination"}}})

(def pilgrimage-key-schema
  [:pilgrimage {:optional true} :boolean])

(def outdoor-recreation-areas
  {:label      {:fi "Retkeily ja ulkoilualueet"
                :se "Utomhusrekreation och naturområden"
                :en "Outdoor Recreation Areas"}
   :value      "outdoor-recreation-areas"
   :type-codes #{102 103 104 106 107 #_#_#_#_108 109 110 111 112}
   :sort-order [:status
                :description-short
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
       :description {:fi "Onko jokaisen oikeudet voimassa. Kyllä/Ei"
                     :se "Är allemansrätten i kraft. Ja/Nej"
                     :en "Are everyman's rights in force. Yes/No"}
       :label       {:fi "Jokamiehenoikeudet"
                     :se "Allemansrätt"
                     :en "Everyman's rights"}}}
     :geo-park
     {:schema [:boolean {:optional true}]
      :field
      {:type        "checkbox"
       :description {:fi "Jos kohde on geopark, niin aktivoi liukukytkin (aktivoitu kytkin muuttuu punaiseksi). HUOM! Geopark on yhtenäinen maantieteellinen alue, jolla on kansainvälisesti merkittävää geologista arvoa."
                     :se "Om platsen är en Geopark, aktivera skjutreglaget (det aktiverade reglaget blir rött). OBS! En Geopark är ett sammanhängande geografiskt område med internationellt betydande geologiskt värde."
                     :en "If the place is a Geopark, activate the slider (activated slider turns red). N.B! A Geopark is a single, unified geographical area of international geological significance."}
       :label       {:fi "Geopark"
                     :se "Geopark"
                     :en "Geopark"}}}
     :rules-structured
     {:schema rules-structured-schema
      :field
      {:type        "rules"
       :description {:fi "Liikkumis- tai toimintaohjeet, joiden avulla ohjataan toimintaa ja esim. varoitetaan poistumasta polulta herkällä kohteella. Tässä voidaan kertoa myös mahdollisista liikkumis- tai toimintarajoituksista."
                     :se "Rörelse- eller bruksanvisning för att styra driften och till exempel varna för att lämna stigen på ett känsligt område. Eventuella begränsningar av rörelsefriheten eller aktivitetsfriheten kan också beskrivas här."
                     :en "Instructions for guiding the activity in the region and, for example, warn against leaving the path at a sensitive place. This can also be used to inform about possible restrictions regarding movement."}
       :label       {:fi "Luvat, säännöt, ohjeet"
                     :se "Tillstånd, regler, anvisningar"
                     :en "Permits, regulations, instructions"}
       :opts        common-rules}}})})

(def outdoor-recreation-areas-schema
  (collect-schema (:props outdoor-recreation-areas)))

(def outdoor-recreation-routes-activities
  {"camping"            {:fi "Retkeily"
                         :se "Camping"
                         :en "Camping"}
   "hiking"             {:fi "Vaellus"
                         :se "Vandring"
                         :en "Hiking"}
   "outdoor-recreation" {:fi "Ulkoilu"
                         :se "Utomhusaktiviteter"
                         :en "Outdoor recreation"}
   "mountain-biking"    {:fi "Maastopyöräily"
                         :se "Mountainbiking"
                         :en "Mountain biking"}
   "paddling"           {:fi "Melonta"
                         :se "Paddling"
                         :en "Paddling"}
   "skiing"             {:fi "Hiihto"
                         :se "Skidåkning"
                         :en "Skiing"}})


(def accessibility-classification
  {"accessible"          {:fi "Esteetön"
                          :se "Tillgänglig"
                          :en "Accessible"}
   "advanced-accessible" {:fi "Vaativa esteetön"
                          :se "Krävande tillgänglig"
                          :en "Demanding accessible"}
   "inaccessible"        {:fi "Esteellinen"
                          :se "Inte tillgänglig"
                          :en "Inaccessible"}
   "unknown"             {:fi "Ei tietoa"
                          :se "Okänd"
                          :en "Unknown"}})


(def outdoor-recreation-routes
  {:label       {:fi "Retkeily ja ulkoilureitit"
                 :se "Utomhusrekreationsrutter"
                 :en "Outdoor Recreation Routes"}
   :value       "outdoor-recreation-routes"
   :description {:fi ""}
   :type-codes  #{4401 4402 4403 4404 4405}
   :sort-order  [:status
                 :route-name
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
                 :pilgrimage
                 :contacts
                 :additional-info-link
                 :images
                 :videos]
   :props
   {:status (:status common-props)

    :routes
    {:schema [:sequential
              (mu/merge
               (-> common-route-props-schema
                   (mu/dissoc :accessibility)
                   (mu/dissoc :latest-updates)
                   (mu/dissoc :rules))
               [:map
                [:id [:string]]
                [:geometries common-schema/line-string-fcoll]
                [:accessibility-categorized {:optional true}
                 [:map
                  [:mobility-impaired {:optional true} common-schema/localized-string]
                  [:hearing-impaired {:optional true} common-schema/localized-string]
                  [:visually-impaired {:optional true} common-schema/localized-string]
                  [:developmentally-disabled {:optional true} common-schema/localized-string]]]
                [:route-name {:optional true} common-schema/localized-string]
                [:outdoor-recreation-activities {:optional true}
                 [:sequential (into [:enum] (keys outdoor-recreation-routes-activities))]]
                [:duration {:optional true} duration-schema]
                [:travel-direction {:optional true} [:enum "clockwise" "counter-clockwise"]]
                [:route-marking {:optional true} common-schema/localized-string]
                [:rules-structured {:optional true} rules-structured-schema]
                [:route-length-km {:optional true} common-schema/number]
                [:surface-material {:optional true} surface-material-schema]
                [:accessibility-classification {:optional true}
                 (into [:enum] (keys accessibility-classification))]
                [:independent-entity {:optional true} [:boolean]]
                pilgrimage-key-schema])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"
                    :se "Ruttens helhet, dagsträcka, svårighetsgraden på sträckan"
                    :en "Route entity, daily leg, degree of difficulty of the leg"}
      :label       {:fi "Reittiosan tyyppi"
                    :se "Rutttyp"
                    :en "Route type"}
      :props
      (merge
       (-> common-route-props
           (dissoc :rules :accessibility)
           (assoc-in [:description-short :field :description :se] "Beskrivning av platsen i 3–7 meningar. Visas till exempel som en introduktion till en plats eller i en lista med flera platser.")
           (assoc-in [:description-short :field :description :en] "A description of the place in 3-7 sentences. Showing e.g. as an intro to the presentation of the place or in the listing of several places.")
           (assoc-in [:description-short :field :description :fi] "3-7 lauseen mittainen kuvaus kohteesta. Näytetään esim. kohde-esittelyn ingressinä tai useamman kohteen listauksessa."))
       {:accessibility-classification
        {:field
         {:type        "select"
          :label       {:fi "Esteettömyysluokittelu"
                        :se "Klassificering av tillgänglighet"
                        :en "Accessibility classification"}
          :description {:fi "Valitse onko reitti esteellinen, esteetön vai vaativa esteetön (vaativalla esteettömällä reitillä saatetaan tarvita avustaja ja/tai apuväline, kuten maastopyörätuoli)"
                        :se "Välj om rutten är inte tillgänglig, tillgänglig eller krävande tillgänglig (på en krävande tillgänglig rutt kan en assistent och/eller hjälpmedel som en terrängrullstol behövas)"
                        :en "Select if the route is not accessible, is accessible or is demanding accessible (a personal assistant or an assistive device, such as an all-terrain wheelchair might be necessary)"}
          :opts        (dissoc accessibility-classification "unknown")}}

        :rules-structured
        {:field
         {:type        "rules"
          :description {:fi "Liikkumisohje, jonka avulla voidaan ohjata harrastusta ja esimerkiksi varoittaa poistumasta polulta herkällä kohteella. Tätä kautta voidaan informoida myös mahdollisista lakisääteisistä liikkumisrajoituksista."
                        :se "Rörelsesanvisningar som kan användas för att styra en hobby och till exempel varna för att lämna stigen på ett känsligt område. Detta kan också användas för att informera om eventuella lagstadgade begränsningar av rörelsefriheten."
                        :en "Instructions that can be used to guide a hobby and, for example, warn against leaving the path at a sensitive place. This can also be used to inform about possible statutory restrictions regarding movement."}
          :label       {:fi "Luvat, säännöt, ohjeet"
                        :se "Tillstånd, regler, anvisningar"
                        :en "Permits, regulations, instructions"}
          :opts        common-rules}}

        :accessibility-categorized
        {:field
         {:type        "accessibility"
          :label       {:fi "Esteettömyys vammaryhmittäin"
                        :se "Tillgänglighet per funktionshindergrupp"
                        :en "Accessibility per handicap group"}
          :description {:fi "Syötä esteettömyyskuvailu vammaryhmille"
                        :se "Ange tillgänglighetsbeskrivningar för funktionshindergrupper."
                        :en "Enter information about accessibility for the appropriate handicap group(s)"}
          :props
          {:mobility-impaired
           {:value "mobility-impaired"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"
                           :se "Ämnesspecifik textbeskrivning"
                           :en "Subject-specific text description"}
             :label       {:fi "Liikuntavammaiset"
                           :se "Rörelsehindrade"
                           :en "Mobility impaired"}}}
           :hearing-impaired
           {:value "hearing-impaired"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"
                           :se "Ämnesspecifik textbeskrivning"
                           :en "Subject-specific text description"}
             :label       {:fi "Kuurot ja kuulovammaiset"
                           :se "Döva och hörselskadade"
                           :en "Hearing impaired"}}}
           :visually-impaired
           {:value "visually-impaired"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"
                           :se "Ämnesspecifik textbeskrivning"
                           :en "Subject-specific text description"}
             :label       {:fi "Näkövammaiset"
                           :se "Synskadade"
                           :en "Visually impaired"}}}
           :developmentally-disabled
           {:value "developmentally-disabled"
            :field
            {:type        "textarea"
             :description {:fi "Aihekohtainen tekstikuvaus"
                           :se "Ämnesspecifik textbeskrivning"
                           :en "Subject-specific text description"}
             :label       {:fi "Kehitysvammaiset"
                           :se "Personer med utvecklingsstörning"
                           :en "Developmentally disabled"}}}}}}

        :route-name
        {:field
         {:type        "text-field"
          :description {:fi "Anna reitille kuvaava nimi, esim. sen maantieteellisen sijainnin tai reitin päätepisteiden mukaan."
                        :se "Ange ett beskrivande namn för rutten, t.ex. beroende på dess geografiska läge eller ruttens ändpunkter."
                        :en "Enter a descriptive name for the route based on its geographical location or its endpoints."}
          :label       {:fi "Reitin nimi"
                        :se "Ruttens namn"
                        :en "Route name"}}}

        :outdoor-recreation-activities
        {:field
         {:type        "multi-select"
          :description {:fi "Valitse reitille soveltuvat kulkutavat"
                        :se "Välj lämpliga färdsätt för rutten."
                        :en "Select the appropriate means of travel on the route."}
          :label       {:fi "Kulkutavat"
                        :se "Färdsätt"
                        :en "Means of travel"}
          :opts        outdoor-recreation-routes-activities}}

        :duration
        {:field
         {:type        "duration"
          :description {:fi "Reitin arvioitu kulkuaika"
                        :se "Beräknad restid på rutten"
                        :en "The estimated time of travel on the route"}
          :label       {:fi "Kulkuaika"
                        :se "Tid för passage"
                        :en "Travel time"}}}

        :travel-direction
        {:field
         {:type        "select"
          :opts        {"clockwise"         {:fi "Myötäpäivään"
                                             :se "Medurs"
                                             :en "Clockwise"}
                        "counter-clockwise" {:fi "Vastapäivään"
                                             :se "Moturs"
                                             :en "Counter-clockwise"}}
          :description {:fi "Valitse reitin kulkusuunta, myötäpäivään/vastapäivään, jos reitillä on suositeltu kulkusuunta."
                        :se "Välj ruttens gångriktning, medurs/moturs om rutten har en rekommenderad gångriktning."
                        :en "If the route has a recommended travelling direction (clockwise, counterclockwise), choose it here."}
          :label       {:fi "Kulkusuunta"
                        :se "Färdriktning"
                        :en "Travel direction"}}}

        :route-marking
        {:field
         {:type        "text-field"
          :description {:fi "Kuvaile tapa, jolla reitti on merkitty maastoon. Esim. syötä reittimerkkien symboli ja väri."
                        :se "Beskriv hur rutten är markerad i terrängen. T.ex. ange symbol och färg på markeringarna."
                        :en "Describe the way the route is marked in the terrain, e.g. enter the symbol and colour of the route markings."}
          :label       {:fi "Reittimerkintä"
                        :se "Vägmarkering"
                        :en "Route marking"}}}

        :route-length-km
        {:field
         {:type           "lipas-property"
          :lipas-property :route-length-km
          :label          {:fi "Reitin pituus (km)"
                           :se "Ruttens längd (km)"
                           :en "Route length (km)"}
          :description    {:fi "Reitin pituus kilometreinä (voit syöttää tiedon käsin tai laskea sen automaattisesti)"
                           :se "Ruttlängd i kilometer (du kan ange informationen manuellt eller beräkna den automatiskt)."
                           :en "The length of the route in kilometres (you can enter it manually of have it calculated automatically)"}}}

        :surface-material
        {:field
         {:type           "lipas-property"
          :lipas-property :surface-material
          :label          {:fi "Pintamateriaali"
                           :se "Underlag"
                           :en "Surface material"}
          :description    {:fi "Valitse kaikki pintamateriaalit, joita reitillä kuljetaan"
                           :se "Välj alla underlag som finns på rutten."
                           :en "Select all the surface materials on the route"}}}

        #_#_:latest-updates
        {:schema common-schema/localized-string
         :field
         {:type        "textarea"
          :description {:fi "Tähän joku seliteteksti"
                        :se "Här någon förklarande text"
                        :en "Some explanatory text here"}
          :label       {:fi "Ajankohtaista"
                        :se "Aktuellt"
                        :en "Latest updates"}}}

        :independent-entity
        {:schema [:boolean {:optional true}]
         :field
         {:type        "checkbox"
          :description {:fi "Aktivoi liukukytkin, jos reitti ei kuulu mihinkään alueeseen tai laajempaan kokonaisuuteen (esim. ulkoilualueeseen tai kansallispuistoon).  Aktivoitu kytkin muuttuu punaiseksi."
                        :se "Aktivera skjutreglaget om rutten inte hör till ett område eller en större helhet (t.ex. ett rekreationsområde eller en nationalpark). När det är aktiverat blir reglaget rött."
                        :en "Activate the slider if the route is not a part of any region or a broader entity (e.g. recreational area or national park). The activated slider turns red."}
          :label       {:fi "Itsenäinen kohde"
                        :se "Fristående plats"
                        :en "Standalone place"}}}

        :pilgrimage (assoc pilgrimage-field :show (fn [{:keys [type-code]}]
                                                    (not (#{4402} type-code))))})}}}})

(def outdoor-recreation-routes-schema
  (collect-schema (:props outdoor-recreation-routes)))

(def cycling-activities
  {"gravel-and-bikepacking" {:fi "Gravel & pyörävaellus"
                             :se "Grus och bikepacking"
                             :en "Gravel & bikepacking"}
   "road-cycling"           {:fi "Maantie"
                             :se "Landsväg"
                             :en "Road cycling"}
   "bike-touring"           {:fi "Retkipyöräily"
                             :se "Cykelsemester"
                             :en "Bike touring"}
   "mountain-biking"        {:fi "Maastopyöräily"
                             :se "Mountainbiking"
                             :en "Mountain biking"}
   "winter-cycling"         {:fi "Talvipyöräily"
                             :se "Vintercykling"
                             :en "Winter cycling"}})

(def cycling-difficulty
  {"1-easy"                   {:fi "1 - Helppo"
                               :se "1 - Lätt"
                               :en "1 - Easy"}
   "2-somewhat-challenging"   {:fi "2 - Osittain vaativa"
                               :se "2 - Något utmanande"
                               :en "2 - Somewhat challenging"}
   "3-moderately-challenging" {:fi "3 - Keskivaativa"
                               :se "3 - Medelsvår"
                               :en "3 - Moderately challenging"}
   "4-challenging"            {:fi "4 - Vaativa"
                               :se "4 - Utmanande"
                               :en "4 - Challenging"}
   "5-extremely-challenging"  {:fi "5 - Erittäin vaativa"
                               :se "5 - Mycket utmanande"
                               :en "5 - Extremely challenging"}})

;; https://www.bikeland.fi/vaativuusluokitukset
(def cycling-route-part-difficulty
  {"1a-easy"                  {:label {:fi "1a - Erittäin helppo (päällystetie)"
                                       :se "1a - Mycket lätt (asfalterad väg)"
                                       :en "1a - Very easy (paved road)"}}
   "1b-easy"                  {:label {:fi "1b - Erittäin helppo (sora- tai metsätie)"
                                       :se "1b - Mycket lätt (grus- eller skogsväg)"
                                       :en "1b - Very easy (gravel or forest road)"}}
   "2-easy"                   {:label {:fi "2 - Helppo"
                                       :se "2 - Lätt"
                                       :en "2 - Easy"}
                               :description {:fi "Maastopyöräilyreitti, joka on yleensä leveä polku tai möykkyisämpi metsätie"
                                             :se "Mountainbikerutt, som vanligtvis är en bred stig eller en gropigare skogsväg"
                                             :en "Mountain biking trail, which is usually a wide path or a bumpier forest road"}}
   "3-moderately-challenging" {:label {:fi "3 - Keskivaativa"
                                       :se "3 - Medelsvår"
                                       :en "3 - Moderately challenging"}
                               :description {:fi "Maastopyöräilyreitti, joka on usein polku tai muu kapeahko maastossa oleva ura"
                                             :se "Mountainbikerutt, som ofta är en stig eller ett annat smalt spår i terrängen"
                                             :en "Mountain biking trail, which is often a path or another narrow track in the terrain"}}
   "4-challenging"            {:label {:fi "4 - Vaativa"
                                       :se "4 - Utmanande"
                                       :en "4 - Challenging"}
                               :description {:fi "Maastopyöräilyreitti, joka on kapea polku tai reitti ja siinä on useita vaikeakulkuisia kohtia"
                                             :se "Mountainbikerutt, som är en smal stig eller led och har flera svårframkomliga partier"
                                             :en "Mountain biking trail, which is a narrow path or route with several difficult sections"}}
   "5-extremely-challenging"  {:label {:fi "5 - Erittäin vaativa"
                                       :se "5 - Mycket utmanande"
                                       :en "5 - Extremely challenging"}
                               :description {:fi "Maastopyöräilyreitti, joka on usein kapeaa ja erittäin haastavaa polkua ja siinä on jatkuvasti haastavia osuuksia"
                                             :se "Mountainbikerutt, som ofta är smal och mycket utmanande, med ständigt svåra avsnitt"
                                             :en "Mountain biking trail, which is often narrow and very challenging, with constantly difficult sections"}}})

(def cycling-route-part-difficulty-label {:fi "Reittiosan vaativuus"
                                          :se "Avsnittets svårighetsgrad"
                                          :en "Section difficulty"})

(def cycling
  {:label       {:fi "Pyöräily"
                 :se "Cykling"
                 :en "Cycling"}
   :value       "cycling"
   :description {:fi ""}
   :type-codes  #{4411 4412}
   :sort-order  [:status
                :route-name
                :description-short
                :route-notes
                :description-long
                :highlights
                :cycling-activities
                :route-length-km
                :duration
                :cycling-difficulty
                :cycling-route-difficulty
                :surface-material
                :unpaved-percentage
                :trail-percentage
                :cyclable-percentage
                :arrival
                :accommodation
                :food-and-water
                :good-to-know
                :accessibility
                :pilgrimage
                :contacts
                :additional-info-link
                :images
                :videos]
   :props
   {:status (:status common-props)

    ;; Päiväetapit pitää pystyä esittelemään erikseen kartalla ja
    ;; kuvailemaan omana kohteenaan. Reittikokonaisuus olisi päätason
    ;; liikuntapaikka (alatasona päiväetapit, jotka ovat ehdotusmaisia
    ;; etappeja).
    :routes
    {:schema [:sequential
              (mu/merge
               common-route-props-schema
               [:map
                [:id [:string]]
                [:geometries common-schema/line-string-fcoll]
                [:route-name {:optional true} common-schema/localized-string]
                [:cycling-activities {:optional true}
                 [:sequential (into [:enum] (keys cycling-activities))]]
                [:cycling-difficulty {:optional true}
                 (into [:enum] (keys cycling-difficulty))]
                [:cycling-route-difficulty {:optional true} common-schema/localized-string]
                [:duration {:optional true} duration-schema]
                [:food-and-water {:optional true} common-schema/localized-string]
                [:accommodation {:optional true} common-schema/localized-string]
                [:good-to-know {:optional true} common-schema/localized-string]
                [:route-notes {:optional true} common-schema/localized-string]
                [:route-length-km {:optional true} common-schema/number]
                [:surface-material {:optional true} surface-material-schema]
                [:unpaved-percentage {:optional true} common-schema/percentage]
                [:trail-percentage {:optional true} common-schema/percentage]
                [:cyclable-percentage {:optional true} common-schema/percentage]
                pilgrimage-key-schema])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"
                    :se "Ruttens helhet, dagsträcka, svårighetsgraden på sträckan"
                    :en "Route entity, daily leg, degree of difficulty of the leg"}
      :label       {:fi "Reittityyppi"
                    :se "Ruttyp"
                    :en "Route type"}
      :props
      (merge
       (-> (dissoc common-route-props :rules)
           (update-in [:description-long :field] assoc
                      :label {:fi "Reittikuvaus"
                              :se ""
                              :en ""}
                      :description {:fi "Reitin tarkempi kuvaus reittiosuuksittain sekä huomautukset erityisen vaativista osuuksista tai vaaranpaikoista. Erota vaiheet omiksi kappaleiksi."
                                    :se ""
                                    :en ""}))
       {:route-name
        {:field
         {:type        "text-field"
          :description {:fi "Anna reitille kuvaava nimi, esim. sen maantieteellisen sijainnin tai reitin päätepisteiden mukaan."
                        :se "Ange ett beskrivande namn för rutten, t.ex. beroende på dess geografiska läge eller ruttens ändpunkter."
                        :en "Enter a descriptive name for the route based on its geographical location or its endpoints."}
          :label       {:fi "Reitin nimi"
                        :se "Ruttens namn"
                        :en "Route name"}}}
        :cycling-activities
        {:field
         {:type        "multi-select"
          :description {:fi "Valitse reitille soveltuvat pyöräilylajit"
                        :se "Välj de cykelsporter som passar på rutten."
                        :en "Select the cycling types suitable for the route."}
          :label       {:fi "Alalaji"
                        :se "Undertyp"
                        :en "Subtypes"}
          :opts        (dissoc cycling-activities "road-cycling")}}

        :cycling-difficulty
        {:field
         {:type        "select"
          :description {:fi "Haastavuus"
                        :se "Utmaning"
                        :en "Difficulty"}
          :label       {:fi "Reitin arvioitu haastavuus"
                        :se "Uppskattad utmaning för rutten"
                        :en "Estimated difficulty of the route"}
          :opts        cycling-difficulty}}

        :cycling-route-difficulty
        {:field
         {:type        "textarea"
          :description {:fi "Kuvaile reitin kokonaishaastavuutta. Huomioi kuvauksessa esim. reitin pinnoite ja ajettavuus, suositeltava varustus, reitin liikennemäärät ja mäkisyys."
                        :se ""
                        :en ""}
          :label       {:fi "Haastavuus"
                        :se "Utmaning"
                        :en "Difficulty"}}}

        :duration
        {:field
         {:type        "duration"
          :description {:fi "Kulkuaika"
                        :se "Tid för passage"
                        :en "Travel time"}
          :label       {:fi "Reitin arvioitu kulkuaika"
                        :se "Beräknad restid av rutten"
                        :en "Estimated time of travel on the route"}}}

        :food-and-water
        {:field
         {:type        "textarea"
          :description {:fi "Tietoa reitin varrella olevista ruokailu- ja juomapaikoista ja/tai ohjeet omasta ruoka- ja juomahuollosta."
                        :se "Information om mat- och dryckesställen längs rutten och/eller anvisningar om ditt eget mat- och dryckesförråd."
                        :en "Information about eating and drinking places along the route and/or instructions for your own food and water supply."}
          :label       {:fi "Ruoka & juoma"
                        :se "Mat & Dryck"
                        :en "Food and drink"}}}

        :accommodation
        {:field
         {:type        "textarea"
          :description {:fi "Tietoa reitin varrella olevista majoitusmahdollisuuksista ja -palveluista."
                        :se "Information om inkvartering och service längs rutten."
                        :en "Information about accommodation and services along the route."}
          :label       {:fi "Majoitus"
                        :se "Inkvartering"
                        :en "Accommodation"}}}

        :good-to-know
        {:field
         {:type        "textarea"
          :description {:fi "Tietoa reittiin tai reitillä liikkumiseen liittyvistä säännöistä ja ohjeista."
                        :se "Information om regler och anvisningar för rutten eller navigering av rutten."
                        :en "Information about the rules and instructions related to the route or navigating the route."}
          :label       {:fi "Hyvä tietää"
                        :se "Bra att veta"
                        :en "Good to know"}}}

        :route-notes
        {:field
         {:type        "textarea"
          :description {:fi "Yleiskuvausta jatkava, laajempi kuvaus kohteesta ja sen ominaisuuksista."
                        :se ""
                        :en ""}
          :label       {:fi "Laajennettu yleiskuvaus"
                        :se ""
                        :en ""}}}

        :unpaved-percentage
        {:field
         {:type        "percentage"
          :description {:fi "Kuinka suuri osuus reitistä on päällystämätöntä?"
                        :se "Hur många procent av rutten är oasfalterad?"
                        :en "How many per cent of the route is not paved?"}
          :label       {:fi "Päällystämätöntä"
                        :se "Oasfalterad"
                        :en "Not paved"}}}

        :trail-percentage
        {:field
         {:type        "percentage"
          :description {:fi "Kuinka suuri osuus reitistä on polkua?"
                        :se "Hur många procent av rutten är en led?"
                        :en "How many per cent of the route is a trail?"}
          :label       {:fi "Polkua"
                        :se "Led"
                        :en "Trail"}}}

        :cyclable-percentage
        {:field
         {:type        "percentage"
          :description {:fi "Kuinka suuri osuus reitistä on pyöräiltävissä?"
                        :se "Hur många procent av rutten kan man cykla?"
                        :en "How many per cent of the route is ridable?"}
          :label       {:fi "Pyöräiltävissä"
                        :se "Kan cyclas"
                        :en "Is ridable"}}}

        :route-length-km
        {:field
         {:type           "lipas-property"
          :lipas-property :route-length-km
          :label          {:fi "Reitin pituus (km)"
                           :se "Ruttens längd (km)"
                           :en "Route length (km)"}
          :description    {:fi "Reitin pituus kilometreinä (voit syöttää tiedon käsin tai laskea sen automaattisesti)"
                           :se "Ruttlängd i kilometer (du kan ange informationen manuellt eller beräkna den automatiskt)."
                           :en "The length of the route in kilometres (you can enter it manually of have it calculated automatically)"}}}

        :surface-material
        {:field
         {:type           "lipas-property"
          :lipas-property :surface-material
          :label          {:fi "Pintamateriaali"
                           :se "Underlag"
                           :en "Surface material"}
          :description    {:fi "Valitse kaikki pintamateriaalit, joita reitillä kuljetaan"
                           :se "Välj alla underlag som finns på rutten."
                           :en "Select all the surface materials on the route"}}}

        :pilgrimage pilgrimage-field})}}}})

(def cycling-schema
  (collect-schema (:props cycling)))

(def paddling-activities
  {"trip-padding"        {:fi "Retkimelonta"
                          :se "Tur-paddling"
                          :en "Trip padding"}
   "whitewater-paddling" {:fi "Koskimelonta"
                          :se "Forspadling"
                          :en "Whitewater paddling"}})

(def paddling-route-types
  {"open-water" {:fi "Avovesi"
                 :se "Öppet vatten"
                 :en "Open water"}
   "sheltered"  {:fi "Suojaisa"
                 :se "Skyddad"
                 :en "Sheltered"}
   "river"      {:fi "Joki"
                 :se "Flod"
                 :en "River"}
   "rapids"     {:fi "Koski"
                 :se "Forsar"
                 :en "Rapids"}})

(def paddling-properties
  {"includes-portage-sections" {:fi "Sisältää kanto-osuuksia"
                                :se "Innehåller bärpartier"
                                :en "Includes portage sections"}
   "includes-canals-or-locks"  {:fi "Sisältää sulkuja / kanavia"
                                :se "Innehåller slussar/kanaler"
                                :en "Includes canals or locks"}
   "ideal-for-fishing"         {:fi "Hyvä kalapaikka"
                                :se "Idealisk för fiske"
                                :en "Ideal for fishing"}
   "scenic-vistas"             {:fi "Hyvät maisemat"
                                :se "Vackra vyer"
                                :en "Scenic vistas"}})

(def paddling-difficulty
  {"easy"   {:fi "Helppo"
             :se "Lätt"
             :en "Easy"}
   "medium" {:fi "Keskivaikea"
             :se "Medel"
             :en "Medium"}
   "hard"   {:fi "Vaativa"
             :se "Svår"
             :en "Hard"}})

(def paddling
  "Paddling is a bastard child and it contains

  - routes (4451 4452)
  - points (5150)

  ... and different content needs to be collected for paddling
  centers. This is resolved by mapping relevant props to each type
  in :type->props"
  {:label       {:fi "Melonta ja SUP"
                 :se "Paddling och SUP"
                 :en "Paddling and SUP"}
   :value       "paddling"
   :description {:fi ""}
   :type-codes  #{4451 4452 5150}
   :type->props {4451 #{:status :routes}
                 4452 #{:status :routes}
                 5150 (into #{:equipment-rental?
                              :rapid-canoeing-centre?
                              :canoeing-club?
                              :activity-service-company?} (keys common-props))}
   :sort-order  [:status
                 :route-name
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
                 :pilgrimage
                 :contacts
                 :additional-info-link
                 :images
                 :videos]
   :props
   (merge #_common-props {}
          {:status (:status common-props)

           :equipment-rental?
           {:schema [:boolean]
            :field
            {:type           "lipas-property"
             :lipas-property :equipment-rental?
             :label          {:fi "Välinevuokraus"
                              :se "Uthyrning av utrustning"
                              :en "Equipment rental"}
             :description    {:fi "Välinevuokraus mahdollista."
                              :se "Uthyrning av utrustning möjlig."
                              :en "Equipment rental available."}}}

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
                              :se "Paddlingsklubb"
                              :en "Canoeing club"}
             :description    {:fi "Onko kyseessä melontaseuran tila."
                              :se "Platsen hör till en paddlingsklubb."
                              :en "The place belongs to a paddling club"}}}

           :activity-service-company?
           {:schema [:boolean]
            :field
            {:type           "lipas-property"
             :lipas-property :activity-service-company?
             :label          {:fi "Ohjelmapalveluyritys"
                              :se "Aktivitetsföretag"
                              :en "Activity agency"}
             :description    {:fi "Toimiiko kohteessa ohjelmapalveluyritys."
                              :se "Aktivitetsföretagens tjänster tillgängliga."
                              :en "Activity agency services available."}}}

           :routes
           {:schema [:sequential
                     (mu/merge
                      common-route-props-schema
                      [:map
                       [:id [:string]]
                       [:geometries common-schema/line-string-fcoll]
                       [:route-name {:optional true} common-schema/localized-string]
                       [:paddling-activities {:optional true}
                        [:sequential (into [:enum] (keys paddling-activities))]]
                       [:paddling-route-type {:optional true}
                        [:sequential (into [:enum] (keys paddling-route-types))]]
                       [:paddling-properties {:optional true}
                        [:sequential (into [:enum] (keys paddling-properties))]]
                       [:paddling-difficulty (into [:enum] (keys paddling-difficulty))]
                       [:travel-direction {:optional true} [:enum "clockwise" "counter-clockwise"]]
                       [:safety {:optional true} common-schema/localized-string]
                       [:good-to-know {:optional true} common-schema/localized-string]
                       [:duration {:optional true} duration-schema]
                       pilgrimage-key-schema])]
            :field
            {:type        "routes"
             :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"
                           :se "Ruttens helhet, dagsträcka, svårighetsgraden på sträckan"
                           :en "Route entity, daily leg, degree of difficulty of the leg"}
             :label       {:fi "Reittityyppi"
                           :se "Ruttyp"
                           :en "Route type"}
             :props
             (merge
              common-route-props
              {:route-name
               {:field
                {:type        "text-field"
                 :description {:fi "Anna reitille kuvaava nimi, esim. sen maantieteellisen sijainnin tai reitin päätepisteiden mukaan."
                               :se "Ange ett beskrivande namn för rutten, t.ex. beroende på dess geografiska läge eller ruttens ändpunkter."
                               :en "Enter a descriptive name for the route based on its geographical location or its endpoints."}
                 :label       {:fi "Reitin nimi"
                               :se "Ruttens namn"
                               :en "Route name"}}}

               :paddling-activities
               {:field
                {:type        "multi-select"
                 :description {:fi "Valitse soveltuvat melontatavat"
                               :se "Välj lämpliga paddlingsmetoder."
                               :en "Choose suitable paddling methods."}
                 :label       {:fi "Aktiviteetti"
                               :se "Aktivitet"
                               :en "Activity"}
                 :opts        paddling-activities}}

               :paddling-route-type
               {:field
                {:type        "multi-select"
                 :description {:fi "Valitse, minkä tyyppinen melontakohde (-reitti) on kyseessä."
                               :se "Välj vilken typ av paddlingsplats (-rutt) det gäller."
                               :en "Select the type of paddling destination (-route) in question."}
                 :label       {:fi "Melontakohteen tyyppi"
                               :se "Typ av paddlingsplats/-rut"
                               :en "Paddling route type"}
                 :opts        paddling-route-types}}

               :paddling-properties
               {:field
                {:type        "multi-select"
                 :description {:fi "Valitse kohdat, jotka kuvaavat reitin ominaisuuksia."
                               :se "Välj de alternativ som beskriver ruttens egenskaper."
                               :en "Select the choices that describe the characteristics of the route."}
                 :label       {:fi "Ominaisuudet"
                               :se "Egenskaper"
                               :en "Characteristics"}
                 :opts        paddling-properties}}

               :paddling-difficulty
               {:field
                {:type        "select"
                 :description {:fi "Haastavuus"
                               :se ""
                               :en "Difficulty"}
                 :label       {:fi "Reitin arvioitu haastavuus."
                               :se ""
                               :en "Estimated difficulty of the route"}
                 :opts        paddling-difficulty}}

               :safety
               {:field
                {:type        "textarea"
                 :description {:fi "Lisää reitin turvallisuuteen liittyvää tietoa esim. kuvaile vaativuutta, suositeltavaa osaamistasoa tai kalustoa."
                               :se "Mer information relaterad till ruttens säkerhet, t.ex. Beskriv komplexiteten, den rekommenderade kompetensnivån eller utrustningen."
                               :en "More information related to route safety, e.g. Describe the complexity, recommended skill level or equipment."}
                 :label       {:fi "Turvallisuus"
                               :se "Säkerhet"
                               :en "Safety"}}}

               :good-to-know
               {:field
                {:type        "textarea"
                 :description {:fi "Syötä tähän asioita, joista vesilläliikkujan on hyvä tietää (esim. matkapuhelimen kuuluvuuden katvealueet)."
                               :se "Här skriver du in saker som paddlare bör känna till (t.ex. döda vinklar i mobiltäckningen)."
                               :en "Enter here things that the paddler should know about (e.g. blind spots in mobile phone coverage)."}
                 :label       {:fi "Hyvä tietää"
                               :se "Bra att veta"
                               :en "Good to know"}}}

               :duration
               {:field
                {:type        "duration"
                 :description {:fi "Reitin arvioitu kulkuaika"
                               :se "Beräknad restid på rutten"
                               :en "The estimated time of travel on the route"}
                 :label       {:fi "Kulkuaika"
                               :se "Tid för passage"
                               :en "Travel time"}}}

               :route-length-km
               {:field
                {:type           "lipas-property"
                 :lipas-property :route-length-km
                 :label          {:fi "Reitin pituus (km)"
                                  :se "Ruttens längd (km)"
                                  :en "Route length (km)"}
                 :description    {:fi "Reitin pituus kilometreinä (voit syöttää tiedon käsin tai laskea sen automaattisesti)"
                                  :se "Ruttlängd i kilometer (du kan ange informationen manuellt eller beräkna den automatiskt)."
                                  :en "The length of the route in kilometres (you can enter it manually of have it calculated automatically)"}}}

               :travel-direction
               {:field
                {:type        "select"
                 :opts        {"clockwise"         {:fi "Myötäpäivään"
                                                    :se "Medurs"
                                                    :en "Clockwise"}
                               "counter-clockwise" {:fi "Vastapäivään"
                                                    :se "Moturs"
                                                    :en "Counter-clockwise"}}
                 :description {:fi "Valitse reitin kulkusuunta, myötäpäivään/vastapäivään, jos reitillä on suositeltu kulkusuunta."
                               :se "Välj ruttens gångriktning, medurs/moturs om rutten har en rekommenderad gångriktning."
                               :en "If the route has a recommended travelling direction (clockwise, counterclockwise), choose it here."}
                 :label       {:fi "Kulkusuunta"
                               :se "Färdriktning"
                               :en "Travel direction"}}}

               :pilgrimage (assoc pilgrimage-field :show (fn [{:keys [type-code]}]
                                                           (= 4451 type-code)))})}}})})

(def paddling-schema
  (collect-schema (:props paddling)))

(def birdwatching-types
  {"bird-observation-tower"      {:fi "Lintutorni"
                                  :se "Fågeltorn"
                                  :en "Birdwatching tower"}
   "other-bird-observation-spot" {:fi "Muu lintupaikka"
                                  :se "Annan fågelplats"
                                  :en "Other birdwatching site"}})

(def birdwatching-seasons
  {"spring" {:fi "Kevät"
             :se "Vår"
             :en "Spring"}
   "summer" {:fi "Kesä"
             :se "Sommar"
             :en "Summer"}
   "autumn" {:fi "Syksy"
             :se "Höst"
             :en "Autumn"}
   "winter" {:fi "Talvi"
             :se "Vinter"
             :en "Winter"}})


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
       :description {:fi "Lintutorni, Muu lintupaikka"
                     :se "Fågeltorn, Annan fågelplats"
                     :en "Birdwatching tower, Other birdwatching site"}
       :label       {:fi "Tyyppi"
                     :se "Typ"
                     :en "Type"}
       :opts        birdwatching-types}}

     :birdwatching-habitat
     {:schema common-schema/localized-string
      :field
      {:type        "textarea"
       :description {:fi "Suokohde, …"
                     :se "Myrmark, ..."
                     :en "Wetland, ..."}
       :label       {:fi "Elinympäristö"
                     :se "Livsmiljö"
                     :en "Habitat"}}}

     :birdwatching-character
     {:schema common-schema/localized-string
      :field
      {:type        "textarea"
       :description {:fi "Muutonseurantakohde, …"
                     :se "Plats för flyttfåglar, ..."
                     :en "Migration monitoring site, ..."}
       :label       {:fi "Luonne"
                     :se "Karaktär"
                     :en "Character"}}}

     :birdwatching-season
     {:schema [:sequential (into [:enum] (keys birdwatching-seasons))]
      :field
      {:type        "multi-select"
       :description {:fi "Kevät, Kesä, Syksy, Talvi"
                     :se "Vår, Sommar, Höst, Vinter"
                     :en "Spring, Summer, Autumn, Winter"}
       :label       {:fi "Ajankohta"
                     :se "Säsong"
                     :en "Season"}
       :opts        birdwatching-seasons}}

     :birdwatching-species
     {:schema common-schema/localized-string
      :field
      {:type        "textarea"
       :description {:fi "Kahlaajat, Vesilinnut, Petolinnut, …"
                     :se "Vadare, Sjöfåglar, Rovfåglar, ..."
                     :en "Waders, Waterfowl, Birds of prey, ..."}
       :label       {:fi "Lajisto"
                     :se "Fågelarter"
                     :en "Bird species"}}}})})

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
  {:label       {:fi "Kalastus"
                 :se "Fiske"
                 :en "Fishing"}
   :value       "fishing"
   :description {:fi ""}
   :type-codes  #{201 113}
   :sort-order [:status
                :description-short
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
       :description {:fi "Valitse, mistä kohteessa voi kalastaa"
                     :se "Välj var du vill fiska."
                     :en "Choose where to fish."}
       :label       {:fi "Kohdetyyppi"
                     :se "Typ av platsen"
                     :en "Type of place"}
       :opts        fishing-types}}

     :fishing-activities
     {:schema [:sequential (into [:enum] (keys fishing-activities))]
      :field
      {:type        "multi-select"
       :description {:fi "Valitse soveltuvat kalastusmuodot"
                     :se "Välj lämpliga fiskeformer."
                     :en "Choose suitable forms of fishing."}
       :label       {:fi "Hyvin soveltuvat kalastusmuodot"
                     :se "Lämpliga fiskeformer"
                     :en "Suitable forms of fishing"}
       :opts        fishing-activities}}

     :fishing-waters
     {:schema (into [:enum] (keys fishing-waters))
      :field
      {:type        "select"
       :description {:fi "Valitse vesistön tyyppi"
                     :se "Välj typ av vattendrag."
                     :en "Choose the type of the water system."}
       :label       {:fi "Vesistö"
                     :se "Vattendrag"
                     :en "Water system"}
       :opts        fishing-waters}}

     :fishing-species
     {:schema [:sequential (into [:enum] (keys fishing-species))]
      :field
      {:type        "multi-select"
       :description {:fi "Kohteessa kalastamisen kannalta keskeisimmät kalalajit, esim. ahven, taimen, kirjolohi tms."
                     :se "Välj de viktigaste fiskarterna för fiske på platsen, t.ex. abborre, öring, regnbåge m.m."
                     :en "Choose the main types of fish present in the water system, e.g. perch, trout, rainbow trout, etc."}
       :label       {:fi "Keskeiset kalalajit"
                     :se "Viktiga fiskarter"
                     :en "Main types of fish"}
       :opts        fishing-species}}

     :fish-population
     {:schema common-schema/localized-string
      :field
      {:type        "textarea"
       :description {:fi "Kirjoita tähän kuvaus kohteen vesistössä esiintyvästä kalastosta."
                     :se "Ange här en beskrivning av fiskbeståndet i vattendragen på platsen."
                     :en "Write a description of the fish population in the water system of the place here."}
       :label       {:fi "Kalasto"
                     :se ""
                     :en "Fish population"}}}


     :fishing-methods
     {:schema common-schema/localized-string
      :field
      {:type        "textarea"
       :description {:fi "Tietoa mm. kohteessa kalastukseen vaikuttavista erityispiirteistä, toimivista välinevalinnoista yms."
                     :se "Information om t.ex. särskilda egenskaper som påverkar fisket på platsen, val av funktionell utrustning etc."
                     :en "Information on e.g. special characteristics affecting fishing at the site, functional equipment choices, etc."}
       :label       {:fi "Vinkkejä kohteessa kalastamiseen"
                     :se "Tips för fiske på platsen"
                     :en "Fishing tips"}}}

     :fishing-permit
     {:schema [:sequential (into [:enum] (keys fishing-permit-opts))]
      :field
      {:type        "checkboxes"
       :label       {:fi "Kalastuslupatarve"
                     :se "Fiskelicensbehov"
                     :en "Fishing permit requirement"}
       :description {:fi "Valitse kohteen kalastuslupatarve yhdellä vavalla kalastettaessa. Huom. useammalla vavalla kalastaminen vaatii aina paikallisen luvan."
                     :se "Välj fisketillståndskrav för platsen när du fiskar med ett spö. OBS! Om du fiskar med mer än ett spö behöver du alltid ett lokalt tillstånd."
                     :en "Select the fishing permit requirement for the destination when fishing with one rod. N.B. Fishing with more than one rod always requires a local permit."}
       :opts        fishing-permit-opts}}

     :fishing-permit-additional-info
     {:schema common-schema/localized-string
      :field
      {:type        "textarea"
       :description {:fi "Syötä tähän tarvittaessa lisätietoa kalastuslupia koskevista muista asioista"
                     :se "Ange vid behov mer information om andra frågor som gäller fisketillstånd här."
                     :en "If necessary, enter additional information about other matters concerning fishing permits here."}
       :label       {:fi "Kalastuslupatarpeen lisätiedot"
                     :se ""
                     :en "Additional information about fishing permits"}}}

     :accessibility-classification
     {:schema (into [:enum] (keys accessibility-classification))
      :field
      {:type        "select"
       :label       {:fi "Esteettömyysluokittelu"
                     :se "Klassificering av tillgänglighet"
                     :en "Accessibility classification"}
       :description {:fi "Valitse onko kohde esteellinen tai esteetön."
                     :se "Välj om platsen är inte tillgänglig eller tillgänglig."
                     :en "Select if the place is not accessible or is accessible."}
       :opts        (dissoc accessibility-classification "advanced-accessible")}}

     :accessibility-categorized
     {:schema [:map
               [:mobility-impaired {:optional true} common-schema/localized-string]
               [:hearing-impaired {:optional true} common-schema/localized-string]
               [:visually-impaired {:optional true} common-schema/localized-string]
               [:developmentally-disabled {:optional true} common-schema/localized-string]]

      :field
      {:type        "accessibility"
       :label       {:fi "Esteettömyys"
                     :se "Tillgänglighet"
                     :en "Accessibility"}
       :description {:fi "Yleistä tietoa kohteen esteettömyydestä"
                     :se "Allmän information om platsens tillgänglighet."
                     :en "General information about the accessibility of the place."}
       :props
       {:mobility-impaired
        {:value "mobility-impaired"
         :field
         {:type        "textarea"
          :description {:fi "Aihekohtainen tekstikuvaus"
                        :se "Ämnesspecifik textbeskrivning"
                        :en "Subject-specific text description"}
          :label       {:fi "Liikuntavammaiset"
                        :se "Rörelsehindrade"
                        :en "Mobility impaired"}}}}}}})})

(def fishing-schema
  (mu/merge
   common-props-schema
   (collect-schema (:props fishing))))

(def outdoor-recreation-facilities
  {:label       {:fi "Retkeily ja ulkoilurakenteet"
                 :se "Utomhusrekreationsanläggningar"
                 :en "Outdoor Recreation Facilities"}
   :value       "outdoor-recreation-facilities"
   :description {:fi ""}
   :type-codes  #{207 205 206 202 301 302 304 #_204}
   :sort-order [:status
                :description-short
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
                    (assoc-in [:rules :field :description :se] "Rörelse- eller bruksanvisning för att styra driften. Eventuella begränsningar av rörelsefriheten eller aktivitetsfriheten kan också beskrivas här. OBS! Fyll i fältet endast om det finns några specifika invändningar mot användningen av strukturen.")
                    (assoc-in [:rules :field :description :en] "Instructions for guiding the activity in the region. This field can also contain information about possible restrictions regarding the activity. N.B! Fill in the data only if the use of the structure requires special attention.")
                    (assoc-in [:arrival :field :description :fi] "Eri kulkumuodoilla kohteeseen pääsyyn liittyvää tietoa. Esim. pysäköintialueet ja joukkoliikenneyhteydet. HUOM! Täytä kenttä vain, jos kohteelle saapumiseen liittyy jotakin erityistä huomautettavaa.")
                    (assoc-in [:arrival :field :description :se] "Information om olika transportsätt att ta sig till destinationen. T.ex. parkeringsplatser och kollektivtrafikförbindelser. OBS! Fyll i fältet endast om det finns några särskilda kommentarer om ankomsten till destinationen.")
                    (assoc-in [:arrival :field :description :en] "Information about how to get to the destination with different means of transport, e.g. parking areas and public transport. N.B! Fill in the field only if there are any special comments about arriving at the destination.")
                    (assoc-in [:accessibility :field :description :fi] "Yleistä tietoa kohteen esteettömyydestä  tai kuljettavuudesta")
                    (assoc-in [:accessibility :field :description :se] "Allmän information om platsens tillgänglighet eller farbarhet")
                    (assoc-in [:accessibility :field :description :en] "General information about the accessibility or passability of the place."))
})


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

(def by-type-code
  (->> by-types
       (mapcat (fn [[type-codes v]]
                 (for [type-code type-codes]
                   [type-code v])))
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
