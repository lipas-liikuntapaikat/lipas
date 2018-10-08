(ns lipas.i18n.fi)

;; Use "Zero-width space" to define where to split long words for
;; mobile displays. This is mainly needed in headlines with such words
;; as "Uimahalliportaali" => (str "Uimahalli" ZWSP "portaali").
(def ZWSP \u200B)

(def translations
  {:menu
   {:headline      "LIPAS"
    :headline-test "LIPAS-TESTAUS"
    :main-menu     "Päävalikko"
    :jyu           "Jyväskylän yliopisto"
    :frontpage     "Etusivu"}

   :restricted
   {:login-or-register "Kirjaudu sisään tai rekisteröidy"}

   :home-page
   {:headline "Etusivu"}

   :sport
   {:headline          "Liikuntapaikat"
    :description       "LIPAS-järjestelmä tarjoaa ajantasaisen tiedon Suomen
    julkisista liikuntapaikoista avoimessa tietokannassa."
    :legacy-disclaimer "Liikuntapaikat sijaitsevat toistaiseksi
    nykyisessä LIPAS-järjestelmässä. Pääset sinne alla olevasta
    linkistä."

    :up-to-date-information "Ajantasainen tieto Suomen liikuntapaikoista"
    :updating-tools         "Päivitystyökalut tiedontuottajille"
    :open-interfaces        "Avoimet rajapinnat"}

   :lipas.sports-site
   {:headline          "Liikuntapaikka"
    :id                "LIPAS-ID"
    :name-short        "Nimi"
    :name              "Virallinen nimi"
    :marketing-name    "Markkinointinimi"
    :owner             "Omistaja"
    :admin             "Ylläpitäjä"
    :type              "Tyyppi"
    :construction-year (str "Rakennus" ZWSP "vuosi")
    :renovation-years  (str "Perus" ZWSP "korjaus" ZWSP "vuodet")
    :phone-number      "Puhelinnumero"
    :www               "Web-sivu"
    :email-public      "Sähköposti (julkinen)"}

   :type
   {:type-code "Tyyppikoodi"
    :name      "Liikuntapaikkatyyppi"}

   :lipas.location
   {:headline      "Sijainti"
    :address       "Katuosoite"
    :postal-code   "Postinumero"
    :postal-office "Postitoimipaikka"
    :city          "Kunta"
    :city-code     "Kuntanumero"
    :neighborhood  "Kuntaosa"}

   :reports
   {:headline (str "Yhteys" ZWSP "tiedot")
    :contacts "Yhteystiedot"}

   :ice
   {:headline    (str "Jäähalli" ZWSP "portaali")
    :description "Jäähalliportaali sisältää
              hallien perus- ja energiankulutustietoja sekä ohjeita
              energiatehokkuuden parantamiseen."

    :basic-data-of-halls  "Jäähallien perustiedot"
    :entering-energy-data "Energiankulutustietojen syöttäminen"
    :updating-basic-data  "Perustietojen päivitys"
    :size-category        "Kokoluokitus"
    :comparison           "Hallien vertailu"
    :small                "Pieni kilpahalli > 500 hlö"
    :competition          "Kilpahalli < 3000 hlö"
    :large                "Suurhalli > 3000 hlö"}


   :ice-rinks
   {:headline "Hallien tiedot"}

   :ice-energy
   {:headline       (str "Energia" ZWSP "info")
    :description    "Ajantasaisen tietopaketin löydät Jääkiekkoliiton sivuilta."
    :finhockey-link "Siirry Jääkiekkoliiton sivuille"}

   :ice-comparison
   {:headline "Hallien vertailu"}

   :lipas.energy-consumption
   {:headline                  (str "Energian" ZWSP "kulutus")
    :headline-year             "Lukemat vuonna {1}"
    :electricity               "Sähköenergia MWh"
    :heat                      "Lämpöenergia (ostettu) MWh"
    :cold                      "Kylmäenergia (ostettu) MWh"
    :water                     "Vesi m³"
    :monthly?                  "Haluan ilmoittaa energiankulutuksen kuukausitasolla"
    :reported-for-year         "Vuoden {1} energiankulutus ilmoitettu"
    :report                    "Ilmoita lukemat"
    :contains-other-buildings? "Energialukemat sisältävät myös muita
    rakennuksia tai tiloja"
    :comment                   "Kommentti"
    :operating-hours           "Käyttötunnit"
    :yearly                    "Vuositasolla*"
    :monthly                   "Kuukausitasolla"
    :monthly-readings-in-year  "Kuukausikulutukset vuonna {1}"
    :not-reported-monthly      "Ei kuukausikulutustietoja"}

   :lipas.energy-stats
   {:headline            "Hallien energiankulutus vuonna {1}"
    :disclaimer          "*Perustuu ilmoitettuihin kulutuksiin vuonna {1}"
    :energy-mwh          "Sähkö + lämpö MWh"
    :electricity-mwh     "Sähkö MWh"
    :heat-mwh            "Lämpö MWh"
    :water-m3            "Vesi m³"
    :hall-missing?       "Puuttuvatko hallisi tiedot kuvasta?"
    :report              "Ilmoita tiedot"
    :energy-reported-for "Sähkön-, lämmön- ja vedenkulutus ilmoitettu vuodelta {1}"}

   :lipas.visitors
   {:headline                 "Kävijämäärät"
    :total-count              "Käyttäjämäärä"
    :spectators-count         "Katsojamäärä"
    :monthly-visitors-in-year "Kuukausittaiset kävijämäärät vuonna {1}"
    :not-reported-monthly     "Ei kuukausitason tietoja"}

   :lipas.swimming-pool.conditions
   {:headline          "Aukiolo"
    :daily-open-hours  "Aukiolotunnit päivässä"
    :open-days-in-year "Aukiolopäivät vuodessa"
    :open-hours-mon    "Maanantaisin"
    :open-hours-tue    "Tiistaisin"
    :open-hours-wed    "Keskiviikkoisin"
    :open-hours-thu    "Torstaisin"
    :open-hours-fri    "Perjantaisin"
    :open-hours-sat    "Lauantaisin"
    :open-hours-sun    "Sunnuntaisin"}

   :lipas.swimming-pool.energy-saving
   {:headline                    (str "Energian" ZWSP "säästö" ZWSP "toimet")
    :shower-water-heat-recovery? "Suihkuveden lämmöntalteenotto"
    :filter-rinse-water-heat-recovery?
    "Suodattimien huuhteluveden lämmöntalteenotto"}

   :swim
   {:headline       (str "Uimahalli" ZWSP "portaali")
    :list           "Hallien tiedot"
    :visualizations "Hallien vertailu"
    :description    "Uimahalliportaali sisältää hallien perus- ja
          energiankulutustietoja sekä ohjeita energiatehokkuuden
          parantamiseen."

    :basic-data-of-halls  "Uimahallien perustiedot"
    :entering-energy-data "Energiankulutustietojen syöttäminen"
    :updating-basic-data  "Perustietojen päivitys"

    :latest-updates "Viimeksi päivitetyt tiedot"}

   :swim-energy
   {:headline       (str "Energia" ZWSP "info")
    :headline-short "Info"
    :description    "Ajantasaisen tietopaketin löydät UKTY:n sivuilta."
    :ukty-link      "Siirry UKTY:n sivuille"}

   :open-data
   {:headline            "Avoin data"
    :description         "Linkit ja ohjeet rajapintojen käyttöön."
    :rest                "REST"
    :wms-wfs             "WMS & WFS"
    :wms-wfs-description "<Tähän linkki Geoserveriin>"}

   :partners
   {:headline (str "Kehittä" ZWSP "misessä mukana")}

   :help
   {:headline "Ohjeet"}

   :user
   {:headline            "Oma sivu"
    :greeting            "Hei {1} {2}!"
    :front-page-link     "Siirry etusivulle"
    :admin-page-link     "Siirry admin-sivulle"
    :ice-stadiums-link   "Jäähalliportaali"
    :swimming-pools-link "Uimahalliportaali"}

   :lipas.admin
   {:headline           "Admin"
    :users              "Käyttäjät"
    :magic-link         "Taikalinkki"
    :confirm-magic-link "Haluatko varmasti lähettää taikalinkin
    käyttäjälle {1}?"
    :access-all-sites   "Sinulla on pääkäyttäjän oikeudet. Voit muokata
    kaikkia liikuntapaikkoja."}

   :lipas.user
   {:contact-info               "Yhteystiedot"
    :email                      "Sähköposti"
    :email-example              "email@example.com"
    :username                   "Käyttäjätunnus"
    :username-example           "tane12"
    :firstname                  "Etunimi"
    :lastname                   "Sukunimi"
    :password                   "Salasana"
    :permissions                "Käyttöoikeudet"
    :permissions-example        "Oikeus päivittää Jyväskylän jäähallien tietoja."
    :permissions-help           "Kerro, mitä tietoja haluat päivittää Lipaksessa"
    :requested-permissions      "Pyydetyt oikeudet"
    :sports-sites               "Omat kohteet"
    :no-permissions             "Sinulle ei ole myönnetty käyttöoikeuksia
    julkaista muutoksia yhteenkään kohteeseen. "
    :draft-encouragement        "Voit kuitenkin tallentaa muutosehdotuksia,
    jotka lähetetään ylläpidon hyväksyttäväksi."
    :view-basic-info            "Tarkista perustiedot"
    :report-energy-consumption  "Ilmoita energiankulutus"
    :report-energy-and-visitors "Ilmoita energia- ja kävijämäärätiedot"}

   :lipas.user.permissions
   {:admin?       "Admin"
    :draft?       "Ehdota muutoksia"
    :all-cities?  "Oikeus kaikkiin kuntiin"
    :all-types?   "Oikeus kaikkiin tyyppeihin"
    :sports-sites "Liikuntapaikat"
    :types        "Tyypit"
    :cities       "Kunnat"}

   :register
   {:headline "Rekisteröidy"}

   :login
   {:headline         "Kirjaudu"
    :username         "Sähköposti / käyttäjätunnus"
    :username-example "paavo.paivittaja@kunta.fi"
    :password         "Salasana"
    :login            "Kirjaudu"
    :logout           "Kirjaudu ulos"
    :bad-credentials  "Käyttäjätunnus tai salasana ei kelpaa"
    :forgot-password? "Unohtuiko salasana?"}

   :reset-password
   {:headline             "Unohtuiko salasana?"
    :change-password      "Vaihda salasana"
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
    :low-emissivity-coating? "Yläpohjassa matalaemissiiviteettipinnoite"}

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
    :skating-area-temperature-c      "Luistelualueen lämpötila 1 m korkeudella"
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

   :lipas.swimming-pool.pool
   {:outdoor-pool? "Ulkoallas"}

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
   {:headline    "Saunat"
    :add-sauna   "Lisää sauna"
    :edit-sauna  "Muokkaa saunaa"
    :women?      "Naiset"
    :men?        "Miehet"
    :accessible? "Esteetön"}

   :lipas.swimming-pool.facilities
   {:headline                       "Muut palvelut"
    :platforms-1m-count             "1 m hyppypaikkojen lkm"
    :platforms-3m-count             "3 m hyppypaikkojen lkm"
    :platforms-5m-count             "5 m hyppypaikkojen lkm"
    :platforms-7.5m-count           "7.5 m hyppypaikkojen lkm"
    :platforms-10m-count            "10 m hyppypaikkojen lkm"
    :hydro-massage-spots-count      "Muiden hierontapisteiden lkm"
    :hydro-neck-massage-spots-count "Niskahierontapisteiden lkm"
    :kiosk?                         "Kioski / kahvio"
    :gym?                           "Kuntosali"
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
   {:year               "Vuosi"
    :hour               "Tunti"
    :month              "Kuukausi"
    :start              "Alkoi"
    :end                "Päättyi"
    :date               "Päivämäärä"
    :just-a-moment-ago  "Hetki sitten"
    :less-than-hour-ago "Alle tunti sitten"
    :today              "Tänään"
    :yesterday          "Eilen"
    :this-week          "Tällä viikolla"
    :this-month         "Tässä kuussa"
    :this-year          "Tänä vuonna"
    :last-year          "Viime vuonna"
    :two-years-ago      "2 vuotta sitten"
    :three-years-ago    "3 vuotta sitten"
    :long-time-ago      "Kauan sitten"}

   :duration
   {:hour  "tuntia"
    :month "kuukautta"}

   :actions
   {:add               "Lisää"
    :edit              "Muokkaa"
    :save              "Tallenna"
    :save-draft        "Tallenna ehdotus"
    :delete            "Poista"
    :discard           "Kumoa"
    :cancel            "Peruuta"
    :close             "Sulje"
    :select-hall       "Valitse halli"
    :select-year       "Valitse vuosi"
    :show-all-years    "Näytä kaikki vuodet"
    :show-account-menu "Avaa käyttäjävalikko"
    :open-main-menu    "Avaa päävalikko"
    :submit            "Lähetä"
    :download          "Lataa"
    :browse-to-portal  "Siirry portaaliin"
    :choose-energy     "Valitse energia"}

   :confirm
   {:headline              "Varmistus"
    :no                    "Ei"
    :yes                   "Kyllä"
    :discard-changes?      "Tahdotko kumota tekemäsi muutokset?"
    :press-again-to-delete "Varmista painamalla uudestaan"
    :save-basic-data?      "Haluatko tallentaa perustiedot?"}

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
    :structure    "Rakenne"
    :hall         "Halli"
    :updated      "Päivitetty"
    :reported     "Ilmoitettu"}

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
    :reset-token-expired "Salasanan vaihto peäonnistui. Linkki on vanhentunut."
    :invalid-form        "Korjaa punaisella merkityt kohdat"}})
