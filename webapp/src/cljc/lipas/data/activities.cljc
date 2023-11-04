(ns lipas.data.activities
  (:require
   [lipas.utils :as utils]
   [clojure.walk :as walk]))

(def common-props
  {:description-short
   {:field
    {:type        "textarea"
     :description {:fi "Tekstimuotoinen tiivistys kohteesta. Näytetään esim. kohde-esittelyn ingressinä tai useamman kohteen listauksessa."}
     :label       {:fi "Yleiskuvaus"}}}

   :description-long
   {:field
    {:type        "textarea"
     :description {:fi "Tarkempi tekstimuotoinen kuvaus kohteesta"}
     :label       {:fi "Kohdekuvaus"}}}

   :contacts
   {:field
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
        :description {:fi "Yhteystietoon liittyvän organisaation rooli"}
        :opts        {"admin"            {:fi "Ylläpitäjä"}
                      "content-creator"  {:fi "Sisällöntuottaja"}
                      "customer-service" {:fi "Asiakaspalvelu"}}}}
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
   {:field
    {:type        "videos"
     :description {:fi "Videot ovat linkkejä esim. kolmannen osapuolen palvelussa olevaan sisältöön"}
     :label       {:fi "Videot"}}}

   :images
   {:field
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
   {:field
    {:type        "textarea"
     :description {:fi "Liikkumisohje, jonka avulla voidaan ohjata harrastusta ja esimerkiksi varoittaa poistumasta polulta herkällä kohteella. Tätä kautta voidaan informoida myös mahdollisista lakisääteisistä liikkumisrajoituksista."}
     :label       {:fi "Luvat, säännöt, ohjeet"}}}

   :arrival
   {:field
    {:type        "textarea"
     :description {:fi "Autolla ja joukkoliikenteellä saapumiseen liittyvää tietoa"}
     :label       {:fi "Saapuminen"}}}

   :accessibility
   {:field
    {:type        "textarea"
     :description {:fi "Yleistä tietoa kohteen esteettömyydestä"}
     :label       {:fi "Esteettömyys"}}}

   :parking
   {:field
    {:type        "point-feature"
     :description {:fi "Karttamerkintä liikuntapaikkakohtainen lisätieto. Mahdollisesti useampi parkkipaikka/liikuntapaikka."}
     :label       {:fi "Pysäköinti"}}}})

(def outdoor-recreation-areas
  {:label      {:fi "Retkeily ja ulkoilualueet"}
   :value      "outdoor-recreation-areas"
   :type-codes #{102 103 104 106 107 108 109 110 111 112}
   :props
   (merge
    common-props
    {:everymans-rights
     {:field
      {:type        "checkbox"
       :description {:fi "Onko jokaisen oikeudet voimassa. Kyllä/Ei"}
       :label       {:fi "Jokamiehenoikeudet"}}}})})

(def outdoor-recreation-routes
  {:label       {:fi "Retkeily ja ulkoilureitit"}
   :value       "outdoor-recreation-routes"
   :description {:fi ""}
   :type-codes  #{4401 4402 4403 4404 4405}
   :props
   {:routes
    {:field
     {:type        "routes"
      :description {:fi "Reittikokonaisuus, päiväetappi, vaativuusosuus"}
      :label       {:fi "Reittityyppi"}
      :props
      (merge
       common-props
       {:accessibility
        {:field
         {:type        "accessibility"
          :label       {:fi "Saavutettavuus"}
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
          :opts        {"camping"            {:fi "Retkeily"}
                        "hiking"             {:fi "Vaellus"}
                        "outdoor-recreation" {:fi "Ulkoilu"}
                        "mountain-biking"    {:fi "Maastopyöräily"}
                        "paddling"           {:fi "Melonta"}
                        "skiing"             {:fi "Hiihto"}}}}

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
          :label       {:fi "Reittimerkintä"}}}})

      :derived-props
      {:length-m
       {:field
        {:type        "number"
         :description "Esim. 347 km"
         :label       "Pituus"}}

       :ascend-m
       {:field
        {:type        "number"
         :description "Esim. 5165 m"
         :label       "Nousumetrit"}}

       :descend-m
       {:field
        {:type        "number"
         :description "Esim. x m"
         :label       "Laskumetrit"}}

       :duration
       {:field
        {:type        "duration"
         :description "Esim. 4-5 h, 4-8 päivää"
         :label       "Ajoaika"}}

       :accessibility-classification
       {:field
        {:type        "select"
         :opts        {"accessible"          {:fi "Esteetön"}
                       "advanced-accessible" {:fi "Vaativa esteetön"}
                       "inaccessible"        {:fi "Ei"}}
         :description "Tähän joku kuvaus"
         :label       {:fi "Esteettömyysluokitus"}}}}}}}})

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
    {:field
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
          :opts        {"gravel-and-bikepacking" {:fi "Gravel & pyörävaellus"}
                        "road-cycling"           {:fi "Maantie"}
                        "mountain-biking"        {:fi "Maastopyöräily"}}}}

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

        :highlights
        {:field
         {:type        "textlist"
          :description {:fi "Tekstiä"}
          :label       {:fi "Kohokohdat"}}}

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
          :label       {:fi "Pyöräiltävissä"}}}})

      :derived-props
      {:length-m
       {:field
        {:type        "number"
         :description "Esim. 347 km"
         :label       "Pituus"}}

       :ascend-m
       {:field
        {:type        "number"
         :description "Esim. 5165 m"
         :label       "Nousumetrit"}}

       :descend-m
       {:field
        {:type        "number"
         :description "Esim. x m"
         :label       "Laskumetrit"}}

       }}}}})

(def paddling
  {:label       {:fi "Melonta ja SUP"}
   :value       "paddling"
   :description {:fi ""}
   :type-codes  #{4451 4452}
   :props
   {:routes
    {:field
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
          :opts        {"trip-padding"        {:fi "Retkimelonta"}
                        "whitewater-paddling" {:fi "Koskimelonta"}}}}

        :route-type
        {:field
         {:type        "multi-select"
          :description {:fi "Avovesi, Suojaisa, Joki, Koski"}
          :label       {:fi "Melontakohteen tyyppi"}
          :opts        {"open-water" {:fi "Avovesi"}
                        "sheltered"  {:fi "Suojaisa"}
                        "river"      {:fi "Joki"}
                        "rapids"     {:fi "Koski"}}}}

        :properties
        {:field
         {:type        "multi-select"
          :description {:fi "Seliteteksti?"}
          :label       {:fi "Ominaisuudet"}
          :opts        {"includes-portage-sections" {:fi "Sisältää kanto-osuuksia"}
                        "includes-canals-or-locks"  {:fi "Sisältää sulkuja / kanavia"}
                        "ideal-for-fishing"         {:fi "Hyvä kalapaikka"}
                        "scenic-vistas"             {:fi "Hyvät maisemat"}}}}

        :difficulty
        {:field
         {:type        "multi-select"
          :description {:fi "Vaativuus"}
          :label       {:fi "Vaativuus"}
          :opts        {"easy"   {:fi "Helppo"}
                        "medium" {:fi "Keskivaikea"}
                        "hard"   {:fi "Vaativa"}}}}

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
          :label       {:fi "Kulkuaika"}}}})

      :derived-props
      {:length-m
       {:field
        {:type        "number"
         :description "Esim. 347 km"
         :label       "Pituus"}}

       :ascend-m
       {:field
        {:type        "number"
         :description "Esim. 5165 m"
         :label       "Nousumetrit"}}

       :descend-m
       {:field
        {:type        "number"
         :description "Esim. x m"
         :label       "Laskumetrit"}}

       :duration
       {:field
        {:field
         {:type        "text-field"
          :description "Esim. 4-5 h, 4-8 päivää"
          :label       "Ajoaika"}}}}}}}})

(def birdwatching
  {:label       {:fi "Lintujen tarkkailu"}
   :value       "birdwatching"
   :description {:fi ""}
   :type-codes  #{204}
   :props
   (merge
    common-props
    {:type
     {:field
      {:type        "multi-select"
       :description {:fi "Lintutorni, Muu lintupaikka"}
       :label       {:fi "Tyyppi"}
       :opts        {"bird-observation-tower"      {:fi "Lintutorni"}
                     "other-bird-observation-spot" {:fi "Muu lintupaikka"}}}}

     :habitat
     {:field
      {:type        "textarea"
       :description {:fi "Suokohde, …"}
       :label       {:fi "Elinympäristö"}}}

     :character
     {:field
      {:type        "textarea"
       :description {:fi "Muutonseurantakohde, …"}
       :label       {:fi "Luonne"}}}

     :season
     {:field
      {:type        "multi-select"
       :description {:fi "Kevät, Kesä, Syksy, Talvi"}
       :label       {:fi "Ajankohta"}
       :opts        {"spring" {:fi "Kevät"}
                     "summer" {:fi "Kesä"}
                     "autumn" {:fi "Syksy"}
                     "winter" {:fi "Talvi"}}}}
     :species
     {:field
      {:type        "textarea"
       :description {:fi "Kahlaajat, Vesilinnut, Petolinnut, …"}
       :label       {:fi "Lajisto"}}}})})

(def fishing
  {:label       {:fi "Kalastus"}
   :value       "fishing"
   :description {:fi ""}
   :type-codes  #{201}
   :props
   (merge
    common-props
    {:type
     {:field
      {:type        "multi-select"
       :description {:fi "Kalastus rannalta, Kalastus vesiltä/jäältä"}
       :label       {:fi "Kohdetyyppi"}
       :opts        {"shore"        {:fi "Kalastus rannalta"}
                     "on-the-water" {:fi "Kalastus vesiltä / jäältä"}}}}

     :activities
     {:field
      {:type        "multi-select"
       :description {:fi "Onginta, Pilkkiminen, Perhokalastus, Viehekalastus"}
       :label       {:fi "Alalaji"}
       :opts        {"angling"      {:fi "Onginta"}
                     "ice-fishing"  {:fi "Pilkkiminen"}
                     "fly-fishing"  {:fi "Perhokalastus"}
                     "lure-fishing" {:fi "Viehekalastus"}}}}

     :waters
     {:field
      {:type        "select"
       :description {:fi "Onginta, Pilkkiminen, Perhokalastus, Viehekalastus"}
       :label       {:fi "Vesistö"}
       :opts        {"sea"   {:fi "Meri"}
                     "river" {:fi "Joki"}
                     "lake"  {:fi "Järvi"}}}}

     :species
     {:field
      {:type        "textarea"
       :description {:fi "ahven, taimen (meritaimen), turpa, …"}
       :label       {:fi "Kalalajit"}}}


     :fish-population
     {:field
      {:type        "textarea"
       :description {:fi "Tekstimuotoinen kuvaus kohteen kalastosta"}
       :label       {:fi "Kalasto"}}}


     :fishing-methods
     {:field
      {:type        "textarea"
       :description {:fi "Tietoa kohteeseen soveltuvista kalastustavoista"}
       :label       {:fi "Kalastustavat"}}}

     :properties
     {:field
      {:type        "multi-select"
       :description {:fi ""}
       :label       {:fi "Ominaisuudet"}
       :opts        {"kalapaikkoja-kaupungeissa" {:fi "Kalapaikkoja kaupungeissa"}
                     "accessible-fishing-spot"   {:fi "Esteetön kalastuspaikka"}
                     "premium-fishing-spot"      {:fi "Laatu-apaja"}}}}

     :rules
     {:field
      {:type        "textarea"
       :description {:fi "Tietoa kohteeseen soveltuvista kalastustavoista"}
       :label       {:fi "Luvat, säännöt, ohjeet"}}}})})

(def outdoor-recreation-facilities
  {:label       {:fi "Retkeily ja ulkoilurakenteet"}
   :value       "outdoor-recreation-facilities"
   :description {:fi ""}
   :type-codes  #{207 205 206 202 301 302 304 #_204}
   :props       common-props})

(def by-types
  (utils/index-by :type-codes [outdoor-recreation-areas
                               outdoor-recreation-facilities
                               outdoor-recreation-routes
                               cycling
                               paddling
                               birdwatching
                               fishing]))

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

(comment


  )
