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

(def common-props
  {:description-short
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Tekstimuotoinen tiivistys kohteesta. Näytetään esim. kohde-esittelyn ingressinä tai useamman kohteen listauksessa."}
     :label       {:fi "Yleiskuvaus"}}}

   :description-long
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Tarkempi tekstimuotoinen kuvaus kohteesta"}
     :label       {:fi "Kohdekuvaus"}}}

   :contacts
   {:schema [:map
             [:organization {:optional true} localized-string-schema]
             [:role {:optional true} (into [:enum] (keys contact-roles))]
             [:email {:optional true} [:string]]
             [:phone-number {:optional true} [:string]]]
    :field
    {:type        "contacts"
     :description {:fi "Tähän joku seliteteksti"}
     :label       {:fi "Yhteystiedot"}
     :props
     {:organization
      {:field
       {:type        "text-field"
        :label       {:fi "Organisaatio"}
        :description {:fi "Yhteystietoon liittyvän organisaation nimi"}}}
      :role
      {:field
       {:type        "multi-select"
        :label       {:fi "Rooli"}
        :description {:fi [:<>
                           "Asiakaspalvelu: Kohteen asiakaspalvelusta vastaava organisaatio"
                           [:br]
                           "Sisällöntuottaja: Kohteesta sähköisessä palvelussa kerrottavista tiedoista vastaava organisaatio"
                           [:br]
                           "Ylläpitäjä: Kohteen olosuhteiden ylläpidosta vastaava organisaatio"]}
        :opts        contact-roles}}
      :email
      {:field
       {:type        "text-field"
        :label       {:fi "Sähköposti"}
        :description {:fi "Yhteystietoon liittyvän organisaation sähköpostiosoite"}}}
      :phone-number
      {:field
       {:type        "text-field"
        :label       {:fi "Puhelinnumero"}
        :description {:fi "Yhteystietoon liittyvän organisaation puhelinnumero"}}}}}}

   :videos
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} localized-string-schema]]]
    :field
    {:type        "videos"
     :description {:fi "Videot ovat linkkejä esim. kolmannen osapuolen palvelussa olevaan sisältöön"}
     :label       {:fi "Videot"}}}

   :images
   {:schema [:sequential
             [:map
              [:url [:string]]
              [:description {:optional true} localized-string-schema]
              [:alt-text {:optional true} localized-string-schema]]]
    :field
    {:type        "images"
     :description {:fi ""}
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

   :rules
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Liikkumisohje, jonka avulla voidaan ohjata harrastusta ja esimerkiksi varoittaa poistumasta polulta herkällä kohteella. Tätä kautta voidaan informoida myös mahdollisista lakisääteisistä liikkumisrajoituksista."}
     :label       {:fi "Luvat, säännöt, ohjeet"}}}

   :arrival
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Autolla ja joukkoliikenteellä saapumiseen liittyvää tietoa"}
     :label       {:fi "Saapuminen"}}}

   :accessibility
   {:schema localized-string-schema
    :field
    {:type        "textarea"
     :description {:fi "Yleistä tietoa kohteen esteettömyydestä"}
     :label       {:fi "Esteettömyys"}}}

   :highlights
   {:schema [:sequential localized-string-schema]
    :field
    {:type        "textlist"
     :description {:fi "Tekstiä"}
     :label       {:fi "Kohokohdat"}}}})

(def common-props-schema
  (collect-schema common-props))

(comment
  (m/schema common-props-schema))

(def outdoor-recreation-areas
  {:label      {:fi "Retkeily ja ulkoilualueet"}
   :value      "outdoor-recreation-areas"
   :type-codes #{102 103 104 106 107 108 109 110 111 112}
   :props
   (merge
    common-props
    {:everymans-rights
     {:schema [:boolean {:optional true}]
      :field
      {:type        "checkbox"
       :description {:fi "Onko jokaisen oikeudet voimassa. Kyllä/Ei"}
       :label       {:fi "Jokamiehenoikeudet"}}}})})

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
   "inaccessible"        {:fi "Esteellinen"}})

(def outdoor-recreation-routes
  {:label       {:fi "Retkeily ja ulkoilureitit"}
   :value       "outdoor-recreation-routes"
   :description {:fi ""}
   :type-codes  #{4401 4402 4403 4404 4405}
   :props
   {:routes
    {:schema [:sequential
              (mu/merge
               (mu/dissoc common-props-schema :accessibility)
               [:map
                [:id [:string]]
                [:geometries route-fcoll-schema]
                [:accessibility {:optional true}
                 [:map
                  [:mobility-impaired {:optional true} localized-string-schema]
                  [:hearing-impaired {:optional true} localized-string-schema]
                  [:visually-impaired {:optional true} localized-string-schema]
                  [:developmentally-disabled {:optional true} localized-string-schema]]]
                [:route-name {:optional true} localized-string-schema]
                [:activities {:optional true}
                 [:sequential [:enum (keys outdoor-recreation-routes-activities)]]]
                [:duration {:optional true} duration-schema]
                [:travel-direction {:optional true} [:enum "clockwise" "counter-clockwise"]]
                [:route-marking {:optional true} localized-string-schema]
                [:accessibility-classification
                 (into [:enum] (keys accessibility-classification))]
                [:latest-updates {:optional true} localized-string-schema]])]
     :field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
      :label       {:fi "Reittityyppi"}
      :props
      (merge
       common-props
       {:accessibility-classification
        {:field
         {:type        "select"
          :label       {:fi "Esteettömyysluokittelu"}
          :description {:fi "???"}
          :opts        accessibility-classification}}
        :accessibility
        {:field
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
          :description {:fi "Tähän joku järkevä ohje"}
          :label       {:fi "Reitin nimi"}}}

        :activities
        {:field
         {:type        "multi-select"
          :description {:fi "Reittiin liittyvä aktiviteetti. Esim. Retkeily ja ulkoilu, Vaellus, Maastopyöräily, Melonta, Hiihto, … "}
          :label       {:fi "Aktiviteetti"}
          :opts        outdoor-recreation-routes-activities}}

        :duration
        {:field
         {:type        "duration"
          :description {:fi "Reitin ohjeellinen kulkuaika"}
          :label       {:fi "Kulkuaika"}}}

        :travel-direction
        {:field
         {:type        "select"
          :opts        {"clockwise"         {:fi "Myötäpäivään"}
                        "counter-clockwise" {:fi "Vastapäivään"}}
          :description "Suositeltu reitin kulkusuunta; Vastapäivään/Myötäpäivään"
          :label       {:fi "Kulkusuunta"}}}

        :route-marking
        {:field
         {:type        "text-field"
          :description {:fi "Reittimerkkien symboli ja väri"}
          :label       {:fi "Reittimerkintä"}}}

        :latest-updates
        {:schema localized-string-schema
         :field
         {:type        "textarea"
          :description {:fi "Tähän joku seliteteksti"}
          :label       {:fi "Ajankohtaista"}}}})}}}})

(def outdoor-recreation-routes-schema
  (collect-schema (:props outdoor-recreation-routes)))

(def cycling-activities
  {"gravel-and-bikepacking" {:fi "Gravel & pyörävaellus"}
   "road-cycling"           {:fi "Maantie"}
   "mountain-biking"        {:fi "Maastopyöräily"}})

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
                [:activities {:optional true}
                 [:sequential (into [:enum] (keys cycling-activities))]]
                [:duration {:optional true} duration-schema]
                [:food-and-water {:optional true} localized-string-schema]
                [:accommodation {:optional true} localized-string-schema]
                [:good-to-know {:optional true} localized-string-schema]
                [:unpaved-percentage {:optional true} number-schema]
                [:trail-percentage {:optional true} number-schema]
                [:cyclable-percentage {:optional true} number-schema]])]
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
        :activities
        {:field
         {:type        "multi-select"
          :description {:fi "Gravel & pyörävaellus, Maantie, Maastopyöräily"}
          :label       {:fi "Alalaji"}
          :opts        cycling-activities}}

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
                [:activities {:optional true}
                 [:sequential (into [:enum] (keys paddling-activities))]]
                [:route-type {:optional true}
                 [:sequential (into [:enum] (keys paddling-route-types))]]
                [:properties {:optional true}
                 [:sequential (into [:enum] (keys paddling-properties))]]
                [:difficulty (into [:enum] (keys paddling-difficulty))]
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

        :activities
        {:field
         {:type        "multi-select"
          :description {:fi "Retkimelonta, Koskimelonta"}
          :label       {:fi "Aktiviteetti"}
          :opts        paddling-activities}}

        :route-type
        {:field
         {:type        "multi-select"
          :description {:fi "Avovesi, Suojaisa, Joki, Koski"}
          :label       {:fi "Melontakohteen tyyppi"}
          :opts        paddling-route-types}}

        :properties
        {:field
         {:type        "multi-select"
          :description {:fi "Seliteteksti?"}
          :label       {:fi "Ominaisuudet"}
          :opts        paddling-properties}}

        :difficulty
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
    {:type
     {:schema [:sequential (into [:enum] (keys birdwatching-types))]
      :field
      {:type        "multi-select"
       :description {:fi "Lintutorni, Muu lintupaikka"}
       :label       {:fi "Tyyppi"}
       :opts        birdwatching-types}}

     :habitat
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Suokohde, …"}
       :label       {:fi "Elinympäristö"}}}

     :character
     {:schema localized-string-schema
      :field
      {:type        "textarea"
       :description {:fi "Muutonseurantakohde, …"}
       :label       {:fi "Luonne"}}}

     :season
     {:schema [:sequential (into [:enum] (keys birdwatching-seasons))]
      :field
      {:type        "multi-select"
       :description {:fi "Kevät, Kesä, Syksy, Talvi"}
       :label       {:fi "Ajankohta"}
       :opts        birdwatching-seasons}}
     :species
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
    {:type
     {:schema [:sequential (into [:enum] (keys fishing-types))]
      :field
      {:type        "multi-select"
       :description {:fi "Kalastus rannalta, Kalastus vesiltä/jäältä"}
       :label       {:fi "Kohdetyyppi"}
       :opts        fishing-types}}

     :activities
     {:schema [:sequential (into [:enum] (keys fishing-types))]
      :field
      {:type        "multi-select"
       :description {:fi "Onginta, Pilkkiminen, Perhokalastus, Viehekalastus"}
       :label       {:fi "Hyvin soveltuvat kalastusmuodot"}
       :opts        fishing-activities}}

     :waters
     {:schema [:sequential (into [:enum] (keys fishing-waters))]
      :field
      {:type        "select"
       :description {:fi "Onginta, Pilkkiminen, Perhokalastus, Viehekalastus"}
       :label       {:fi "Vesistö"}
       :opts        fishing-waters}}

     :species
     {:schema [:sequential (into [:enum] (keys fishing-species))]
      :field
      {:type        "multi-select"
       :description {:fi "Kohteessa kalastamisen kannalta keskeisimmät kalalajit, esim. ahven, taimen, kirjolohi tms."}
       :label       {:fi "Keskeiset kalalajit"}
       :opts fishing-species}}

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

     :accessibility-classification
     {:schema (into [:enum] (keys accessibility-classification))
      :field
      {:type        "select"
       :label       {:fi "Esteettömyysluokittelu"}
       :description {:fi "???"}
       :opts        (dissoc accessibility-classification "advanced-accessible")}}

     :accessibility
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
   :props       common-props})

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
                               birdwatching
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

(defn -main [& args]
  (if (= "csv" (first args))
    (gen-csv)
    (gen-json-schema)))

(comment


  )
