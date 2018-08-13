(ns lipas.ui.i18n
  (:require [tongue.core :as tongue]
            [clojure.string :as s]
            [lipas.data.swimming-pools :as pools]
            [lipas.data.materials :as materials]
            [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]))

(defn ->translation-map [locale m]
  (reduce-kv (fn [res k v]
               (assoc res k (-> v locale))) {} m))

(def fi
  {:menu
   {:headline      "LIPAS"
    :headline-test "LIPAS-TESTIYMPÄRISTÖ"
    :main-menu     "Päävalikko"
    :jyu           "Jyväskylän yliopisto"
    :frontpage     "Etusivu"}

   :restricted
   {:login-or-register "Kirjaudu sisään tai rekisteröidy"}

   :home-page
   {:headline    "Etusivu"
    :description "LIPAS-järjestelmä tarjoaa ajantasaisen tiedon Suomen
    julkisista liikuntapaikoista avoimessa tietokannassa."}

   :sport
   {:headline          "Liikuntapaikat"
    :description       "LIPAS on suomalaisten liikuntapaikkojen tietokanta."
    :legacy-disclaimer "Liikuntapaikat sijaitsevat toistaiseksi
    nykyisessä LIPAS-järjestelmässä. Pääset sinne alla olevasta
    linkistä."}

   :lipas.sports-site
   {:headline          "Liikuntapaikka"
    :id                "LIPAS-ID"
    :name              "Virallinen nimi"
    :marketing-name    "Markkinointinimi"
    :owner             "Omistaja"
    :admin             "Ylläpitäjä"
    :type              "Tyyppi"
    :construction-year "Rakennusvuosi"
    :renovation-years  "Peruskorjausvuodet"
    :phone-number      "Puhelinnumero"
    :www               "Web-sivu"
    :email-public      "Sähköposti (julkinen)"}

   :type
   {:type-code "Tyyppikoodi"
    :name      "Liikuntapaikkatyyppi"}

   :admin (->translation-map :fi admins/all)
   :owner (->translation-map :fi owners/all)

   :lipas.location
   {:headline      "Sijainti"
    :address       "Katuosoite"
    :postal-code   "Postinumero"
    :postal-office "Postitoimipaikka"
    :city          "Kunta"
    :city-code     "Kuntanumero"
    :neighborhood  "Kuntaosa"}

   :reports
   {:headline "Raportit"
    :contacts "Yhteystiedot"}

   :ice
   {:headline      "Jäähalliportaali"
    :description   "Jäähalliportaali sisältää
              hallien perus- ja energiankulutustietoja sekä ohjeita
              energiatehokkuuden parantamiseen."
    :size-category "Kokoluokitus"
    :comparison    "Hallien vertailu"
    :small         "Pieni kilpahalli > 500 hlö"
    :competition   "Kilpahalli < 3000 hlö"
    :large         "Suurhalli > 3000 hlö"}


   :ice-rinks
   {:headline "Hallien tiedot"}

   :ice-energy
   {:headline       "Energia-info"
    :description    "Ajantasaisen tietopaketin löydät Jääkiekkiliiton sivuilta."
    :finhockey-link "Siirry Jääkiekkoliiton sivuille"}

   :ice-comparison
   {:headline "Hallien vertailu"}

   :ice-form
   {:headline       "Ilmoita tiedot"
    :headline-short "Ilmoita kulutus"
    :select-rink    "Valitse halli"}

   :lipas.energy-consumption
   {:headline      "Energiankulutus"
    :headline-year "Energiankulutus vuonna {1}"
    :electricity   "Sähköenergia MWh"
    :heat          "Lämpöenergia (ostettu) MWh"
    :cold          "Kylmäenergia (ostettu) Mwh"
    :water         "Vesi m³"
    :yearly        "Energiankulutus vuositasolla"
    :monthly?      "Haluan ilmoittaa energiankulutuksen kuukausitasolla"}

   :lipas.swimming-pool.visitors
   {:headline      "Kävijämäärä"
    :headline-year "Kävijämäärä vuonna {1}"
    :total-count   "Kokonaismäärä"}

   :lipas.swimming-pool.conditions
   {:headline          "Aukiolo"
    :daily-open-hours  "Aukiolotunnit päivässä"
    :open-days-in-year "Aukiolopäivät vuodessa"}

   :swim
   {:headline       "Uimahalliportaali"
    :list           "Hallien tiedot"
    :visualizations "Hallien vertailu"
    :edit           "Ilmoita tiedot"
    :description    "Uimahalliportaali sisältää hallien perus- ja
          energiankulutustietoja, sekä ohjeita energiatehokkuuden
          parantamiseen."}

   :swim-energy
   {:headline    "Energia-info"
    :description "Ajantasaisen tietopaketin löydät UKTY:n sivuilta."
    :ukty-link   "Siirry UKTYn sivuille"}


   :open-data
   {:headline            "Avoin data"
    :description         "Linkit ja ohjeet rajapintojen käyttöön."
    :rest                "REST"
    :wms-wfs             "WMS & WFS"
    :wms-wfs-description "<Tähän linkki Geoserveriin>"}

   :data-users
   {:headline    "Käyttäjät"
    :description "Olisiko tähän hyvä laittaa lista Lipaksen datan
    hyödyntäjistä ja muista käyttäjistä?"}

   :partners
   {:headline    "Kumppanit"
    :description "Yhteistyökumppaneille voisi olla myös oma osio. Tai
    ainakin listaus logoineen."}

   :team
   {:headline    "Tiimi"
    :description "Tekijöille voisi olla myös esittely?"}

   :pool-types            (->translation-map :fi pools/pool-types)
   :sauna-types           (->translation-map :fi pools/sauna-types)
   :heat-sources          (->translation-map :fi pools/heat-sources)
   :filtering-methods     (->translation-map :fi pools/filtering-methods)
   :pool-structures       (->translation-map :fi materials/pool-structures)
   :slide-structures      (->translation-map :fi materials/slide-structures)
   :building-materials    (->translation-map :fi materials/building-materials)
   :supporting-structures (->translation-map :fi materials/supporting-structures)
   :ceiling-structures    (->translation-map :fi materials/ceiling-structures)

   :help
   {:headline    "Ohjeet"
    :description "Lipaksen käyttöohjeet, videot yms."}

   :user
   {:headline        "Oma sivu"
    :greeting        "Hei {1} {2}!"
    :front-page-link "Siirry etusivulle"}

   :lipas.user
   {:email                 "Sähköposti"
    :email-example         "email@example.com"
    :username              "Käyttäjätunnus"
    :username-example      "tane12"
    :firstname             "Etunimi"
    :lastname              "Sukunimi"
    :password              "Salasana"
    :permissions           "Käyttöoikeudet"
    :permissions-example   "Oikeus päivittää Jyväskylän jäähallien tietoja."
    :permissions-help      "Kerro, mitä tietoja haluat päivittää Lipaksessa"
    :requested-permissions "Pyydetyt oikeudet"}

   :lipas.user.permissions
   {:admin?       "Pääkäyttäjän oikeudet"
    :draft?       "Ehdota muutoksia"
    :sports-sites "Oikeus liikuntapaikkoihin"
    :types        "Oikeus liikuntapaikkatyypin liikuntapaikkoihin"
    :cities       "Oikeus kunnan liikuntapaikkoihin"}

   :register
   {:headline "Rekisteröidy"}

   :login
   {:headline         "Kirjaudu"
    :username         "Käyttäjätunnus"
    :username-example "tane12"
    :password         "Salasana"
    :login            "Kirjaudu"
    :logout           "Kirjaudu ulos"
    :bad-credentials  "Käyttäjätunnus tai salasana ei kelpaa"
    :forgot-password? "Unohtuiko salasana?"}

   :reset-password
   {:headline             "Unohtuiko salasana?"
    :helper-text          "Lähetämme salasanan vaihtolinkin sinulle sähköpostitse."
    :reset-link-sent      "Linkki lähetetty! Tarkista sähköpostisi."
    :enter-new-password   "Syötä uusi salasana"
    :password-helper-text "Salasanassa on oltava vähintään 6 merkkiä"
    :reset-success        "Salasana vaihdettu! Kirjaudu sisään uudella salasanalla."
    :get-new-link         "Tilaa uusi vaihtolinkki"}

   :lipas.building
   {:headline                    "Rakennus"
    :main-designers              "Pääsuunnittelijat"
    :total-surface-area-m2       "Bruttopinta-ala m²"
    :total-volume-m3             "Bruttotilavuus m³"
    :total-pool-room-area-m2     "Allashuoneen pinta-ala m²"
    :total-water-area-m2         "Vesipinta-ala m²"
    :total-ice-area-m2           "Jääpinta-ala m²"
    :water-slides-total-length-m "Vesiliukumäet yht. (m)"
    :heat-sections?              "Allastila on jaettu lämpötilaosioihin"
    :piled?                      "Paalutettu"
    :heat-source                 "Lämmönlähde"
    :main-construction-materials "Päärakennusmateriaalit"
    :supporting-structures       "Kantavat rakenteet"
    :ceiling-structures          "Yläpohjan rakenteet"
    :staff-count                 "Henkilökunnan lukumäärä"
    :seating-capacity            "Katsomokapasiteetti"}

   :lipas.ice-stadium.envelope
   {:headline                "Vaipan rakenne"
    :base-floor-structure    "Alapohjan laatan rakenne"
    :insulated-exterior?     "Ulkoseinä lämpöeristetty"
    :insulated-ceiling?      "Yläpohja lämpöeristetty"
    :low-emissivity-coating? "Yläpohjassa matalaemissiiviteettipinnote"}

   :lipas.ice-stadium.rinks
   {:headline  "Radat"
    :edit-rink "Muokkaa rataa"
    :add-rink  "Lisää rata"}

   ::lipas.ice-stadium.refrigeration
   {:headline                       "Kylmätekniikka"
    :original?                      "Alkuperäinen"
    :individual-metering?           "Alamittaroitu"
    :power-kw                       "Kylmäkoneen teho (kW)"
    :condensate-energy-recycling?   "Lauhde-energia hyötykäytetty"
    :condensate-energy-main-targets "Lauhdelämmön pääkäyttökohde"
    :refrigerant                    "Kylmäaine"
    :refrigerant-amount-kg          "Kylmäaineen määrä (kg)"
    :refrigerant-solution           "Kylmäliuos"
    :refrigerant-solution-amount-l  "Kylmäliuoksen määrä (l)"}

   :lipas.ice-stadium.conditions
   {:headline                        "Käyttöolosuhteet"
    :daily-open-hours                "Aukiolotunnit päivässä"
    :open-months                     "Aukiolokuukaudet vuodessa"
    :air-humidity-min                "Ilman suhteellinen kosteus % min"
    :air-humidity-max                "Ilman suhteellinen kosteus % max"
    :ice-surface-temperature-c       "Jään pinnan lämpötila"
    :skating-area-temperature-c      "Luistelualueen lämpötila 1m korkeudella"
    :stand-temperature-c             "Katsomon tavoiteltu keskilämpötila
          ottelun aikana"
    :daily-maintenances-week-days    "Jäähoitokerrat arkipäivinä"
    :daily-maintenances-weekends     "Jäähoitokerrat viikonlppuina"
    :weekly-maintenances             "Jäänhoitokerrat viikossa"
    :average-water-consumption-l     "Keskimääräinen jäänhoitoon
          käytetty veden määrä (per ajo)"
    :maintenance-water-temperature-c "Jäähoitoveden
          lämpötila (tavoite +40)"
    :ice-resurfacer-fuel             "Jäänhoitokoneen polttoaine"
    :ice-average-thickness-mm        "Jään keskipaksuus mm"}

   :lipas.ice-stadium.ventilation
   {:headline                 "Hallin ilmanvaihto"
    :heat-recovery-type       "Lämmöntalteenoton tyyppi"
    :heat-recovery-efficiency "Lämmöntalteenoton hyötysuhde %"
    :dryer-type               "Ilmankuivaustapa"
    :dryer-duty-type          "Ilmankuivauksen käyttötapa"
    :heat-pump-type           "Lämpöpumpputyyppi"}

   :lipas.swimming-pool.water-treatment
   {:headline          "Vedenkäsittely"
    :ozonation?        "Otsonointi"
    :uv-treatment?     "UV-käsittely"
    :activated-carbon? "Aktiivihiili"
    :filtering-methods "Suodatustapa"}

   :lipas.swimming-pool.pools
   {:headline  "Altaat"
    :add-pool  "Lisää allas"
    :edit-pool "Muokkaa allasta"
    :structure "Rakenne"}

   :lipas.swimming-pool.slides
   {:headline   "Liukumäet"
    :add-slide  "Lisää liukumäki"
    :edit-slide "Muokkaa liukumäkeä"}

   :lipas.swimming-pool.saunas
   {:headline   "Saunat"
    :add-sauna  "Lisää sauna"
    :edit-sauna "Muokkaa saunaa"
    :women?     "Naiset"
    :men?       "Miehet"}

   :lipas.swimming-pool.facilities
   {:headline                       "Muut palvelut"
    :platforms-1m-count             "1m hyppypaikkojen lkm"
    :platforms-3m-count             "3m hyppypaikkojen lkm"
    :platforms-5m-count             "5m hyppypaikkojen lkm"
    :platforms-7.5m-count           "7.5m hyppypaikkojen lkm"
    :platforms-10m-count            "10m hyppypaikkojen lkm"
    :hydro-massage-spots-count      "Hierontapisteiden lkm"
    :hydro-neck-massage-spots-count "Niskahierontapisteiden lkm"
    :kiosk?                         "Kioski / kahvio"
    :showers-men-count              "Miesten suihkut lkm"
    :showers-women-count            "Naisten suihkut lkm"
    :lockers-men-count              "Miesten pukukaapit lkm"
    :lockers-women-count            "Naisten pukukaapit lkm"}

   :dimensions
   {:volume-m3       "Tilavuus m³"
    :area-m2         "Pinta-ala m²"
    :surface-area-m2 "Pinta-ala m²"
    :length-m        "Pituus m"
    :width-m         "Leveys m"
    :depth-min-m     "Syvyys min m"
    :depth-max-m     "Syvyys max m"}

   :units
   {:times-per-day  "kertaa päivässä"
    :times-per-week "kertaa viikossa"
    :hours-per-day  "tuntia päivässä"
    :days-in-year   "päivää vuodessa"
    :person         "hlö"
    :pcs            "kpl"
    :percent        "%"}

   :physical-units
   {:temperature-c "Lämpötila °C"
    :mwh           "MWh"
    :m             "m"
    :mm            "mm"
    :l             "l"
    :m2            "m²"
    :m3            "m³"
    :celsius       "°C"
    :hour          "h"}

   :month
   {:jan "Tammikuu"
    :feb "Helmikuu"
    :mar "Maaliskuu"
    :apr "Huhtikuu"
    :may "Toukokuu"
    :jun "Kesäkuu"
    :jul "Heinäkuu"
    :aug "Elokuu"
    :sep "Syyskuu"
    :oct "Lokakuu"
    :nov "Marraskuu"
    :dec "Joulukuu"}

   :time
   {:year  "Vuosi"
    :hour  "Tunti"
    :month "Kuukausi"
    :start "Alkoi"
    :end   "Päättyi"}

   :duration
   {:hour  "tuntia"
    :month "kuukautta"}

   :actions
   {:add               "Lisää"
    :edit              "Muokkaa"
    :save              "Tallenna"
    :save-draft        "Tallenna ehdotus"
    :publish           "Julkaise muutokset"
    :delete            "Poista"
    :discard           "Kumoa"
    :cancel            "Peruuta"
    :close             "Sulje"
    :select-hall       "Valitse halli"
    :select-year       "Valitse vuosi"
    :show-all-years    "Näytä kaikki vuodet"
    :open-account-menu "Avaa käyttäjävalikko"
    :open-main-menu    "Avaa päävalikko"
    :submit            "Lähetä"
    :download          "Lataa"}

   :search
   {:headline "Haku"}

   :statuses
   {:edited "{1} (muokattu)"}

   :general
   {:name         "Nimi"
    :type         "Tyyppi"
    :description  "Kuvaus"
    :general-info "Yleiset tiedot"
    :comment      "Kommentti"
    :structure    "Rakenne"}

   :notifications
   {:save-success "Tallennus onnistui"
    :save-failed  "Tallennus epäonnistui"
    :get-failed   "Tietojen hakeminen epäonnistui."}

   :disclaimer
   {:headline     "HUOMIO!"
    :test-version "Tämä on LIPAS-sovelluksen testiversio ja
    tarkoitettu koekäyttöön. Muutokset eivät tallennu oikeaan
    Lipakseen."}

   :error
   {:unknown             "Tuntematon virhe tapahtui. :/"
    :user-not-found      "Käyttäjää ei löydy."
    :email-not-found     "Sähköpostiosoitetta ei ole rekisteröity."
    :reset-token-expired "Salasanan vaihto peäonnistui. Linkki on vanhentunut."}})

(def se {:menu
         {:jyu "Jyväskylä universitet"
          :login "Logga in"}
         :sport
         {:headline "Sport platsen"
          :description "LIPAS är jättebra."}
         :ice
         {:headline "Ishall portal"
          :description "Den här portal är jättebra"}
         :ice-energy
         {:description "Jaajaa"}
         :swim
         {:headline "Simhall portal"
          :description "Den här portal är också jättebra"}
         :open-data
         {:headline "Öppen databorg"
          :description "Alla data är jätteöppen."}
         :help
         {:headline "Hjälpa"
          :description "Här har du hjälpa."}
         :register
         {:headline "Registera"}
         :login
         {:headline "Logga in"}
         :time
         {:year "År"}})


(def en {:menu
         {:jyu "University of Jyväskylä"
          :login "Log in"
          :register "Register"}
         :sport
         {:headline "Sports sites"
          :description "LIPAS is cool."}
         :ice
         {:headline "Skating rink portal"
          :description "Description comes here"}
         :swim
         {:headline "Swimming pool portal"
          :description "Description comes here"}
         :open-data
         {:headline "Open data"
          :description "All data is free for use"}
         :help
         {:headline "Help"
          :description "Help help help"}
         :register
         {:headline "Register"}
         :login
         {:headline "Login"}})

(def dicts {:fi fi
            :se se
            :en en
            :tongue/fallback :fi})

(comment (translate :fi :front-page/lipas-headline))
(comment (translate :fi :menu/sports-panel))
(comment (translate :fi :menu/sports-panel :lower))
(def translate (tongue/build-translate dicts))

(def formatters
  {:lower-case s/lower-case
   :upper-case s/upper-case
   :capitalize s/capitalize})

(defn fmt
  "Supported formatter options:

  :lower-case
  :upper-case
  :capitalize"
  [s args]
  (case (first args)
    :lower-case (s/lower-case s)
    :upper-case (s/upper-case s)
    :capitalize (s/capitalize s)
    s))

(comment ((->tr-fn :fi) :menu/sports-panel))
(comment ((->tr-fn :fi) :menu/sports-panel :lower))
(defn ->tr-fn
  "Creates translator fn with support for optional formatter. See
  `lipas.ui.i18n/fmt`

  Translator fn Returns current locale (:fi :sv :en) when called with
  no args.

  Function usage: ((->tr-fn :fi) :menu/sports-panel :lower)
  => \"liikuntapaikat\""
  [locale]
  (fn
    ([]
     locale)
    ([kw & args]
     (-> (apply translate (into [locale kw] (filter (complement keyword?) args)))
         (fmt args)))))
