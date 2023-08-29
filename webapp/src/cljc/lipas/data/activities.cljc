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



   :videos
   {:field
    {:type        "videos"
     :description {:fi "Videot ovat linkkejä esim. kolmannen osapuolen palvelussa olevaan sisältöön"}
     :label       {:fi "Videot"}}}

   :images
   {:field
    {:type        "images"
     :description {:fi ""}
     :label       {:fi "Valokuvat"}}}

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
     :label       {:fi "Saavutettavuus"}}}

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
   :value      "outdoor-recreation-routes"
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
       {:route-name
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
        {:field
         {:type        "duration"
          :description "Esim. 4-5 h, 4-8 päivää"
          :label       "Ajoaika"}}}}}}}})

(def outdoor-recreation-facilities
  {:label       {:fi "Retkeily ja ulkoilurakenteet"}
   :value       "outdoor-recreation-facilities"
   :description {:fi ""}
   :type-codes  #{207 205 206 202 301 302 304 #_204}
   :props       common-props})

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
          :label       {:fi "Hyvä tietää"}}}})

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

       :unpaved-percentage
       {:field
        {:type        "number"
         :description "Esim. 28%"
         :label       "Päällystämätöntä %"}}

       :trail-percentage
       {:field
        {:type        "number"
         :description "Esim. 0%"
         :label       "Polkua %"}}

       :cyclable-percentage
       {:field
        {:type        "number"
         :description "Esim. 100%"
         :label       "Pyöräiltävissä %"}}

       :duration
       {:field
        {:field
         {:type        "textfield"
          :description "Esim. 4-5 h, 4-8 päivää"
          :label       "Ajoaika"}}}}}}}})

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
       {:activities
        {:type        "multi-select"
         :description {:fi "Retkimelonta, Koskimelonta"}
         :label       {:fi "Aktiviteetti"}
         :opts        {"trip-padding"        {:fi "Retkimelonta"}
                       "whitewater-paddling" {:fi "Koskimelonta"}}}

        :route-type
        {:type        "multi-select"
         :description {:fi "Avovesi, Suojaisa, Joki, Koski"}
         :label       {:fi "Aktiviteetti"}
         :opts        {"open-water" {:fi "Avovesi"}
                       "sheltered"  {:fi "Suojaisa"}
                       "river"      {:fi "Joki"}
                       "rapids"     {:fi "Koski"}}}

        :difficulty
        {:type        "multi-select"
         :description {:fi "Vaativuus"}
         :label       {:fi "Vaativuus"}
         :opts        {"easy"   {:fi "Helppo"}
                       "medium" {:fi "Keskivaikea"}
                       "hard"   {:fi "Vaativa"}}}

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
          :label       {:fi "AKulkuaika"}}}})

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
         {:type        "textfield"
          :description "Esim. 4-5 h, 4-8 päivää"
          :label       "Ajoaika"}}}}}}}})

(def birdwatching
  {:label       {:fi "Lintujen tarkkailu"}
   :value       "birdwatching"
   :description {:fi ""}
   :type-codes  #{204}
   :props
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
      :label       {:fi "Lajisto"}}}}})

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

     :rules
     {:field
      {:type        "textarea"
       :description {:fi "Tietoa kohteeseen soveltuvista kalastustavoista"}
       :label       {:fi "Luvat, säännöt, ohjeet"}}}})})

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

;; Suomi, ruotsi, englanti, kaikki saamet

;; Aktiviteetit -> Lajit / lajitiedot ?

;; aktiviteetit lisätietojen alle

;; 21.1. sellainen versio missä työryhmä voi dev-ympäristöön syöttää tietoja

#_(->> [outdoor-recreation-areas
      outdoor-recreation-facilities
      outdoor-recreation-routes
      cycling
      paddling
      birdwatching
      fishing]
     (map (juxt :value #(-> % :props (->> (map (fn [[k {:keys [field]}]]
                                                 [k (if (= "routes" (:type field))
                                                      (map (juxt first (comp :type :field second)) (:props field))
                                                      (:type field))])))))))
