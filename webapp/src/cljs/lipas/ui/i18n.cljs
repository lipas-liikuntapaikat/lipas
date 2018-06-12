(ns lipas.ui.i18n
  (:require [tongue.core :as tongue]
            [clojure.string :as s]
            [lipas.data.swimming-pools :as pools]
            [lipas.data.materials :as materials]))

(defn ->translation-map [locale m]
  (reduce-kv (fn [res k v]
               (assoc res k (-> v locale))) {} m))

(def fi
  {:menu
   {:jyu "Jyväskylän yliopisto"
    :frontpage "Etusivu"}

   :sport
   {:headline "Liikuntapaikat"
    :description "LIPAS on suomalaisten liikuntapaikkojen tietokanta."}

   :sports-place
   {:headline "Liikuntapaikka"
    :name-fi "Nimi"
    :name-se "Nimi ruotsiksi"
    :name-en "Nimi englanniksi"
    :owner "Omistaja"
    :admin "Ylläpitäjä"
    :phone-number "Puhelinnumero"
    :www "Web-sivu"
    :email-public "Sähköposti (julkinen)"}

   :type
   {:type-code "Liikuntapaikkatyyppi"}

   :admin
   {:city-sports "Kunta / liikuntatoimi"
    :city-education "Kunta / opetustoimi"
    :city-technical-services "Kunta / tekninen toimi"
    :city-other "Kunta / muu"
    :private-association "Yksityinen / yhdistys"
    :private-company "Yksityinen / yritys"
    :private-foundation "Yksityinen / säätiö"
    :state "Valtio"
    :other "Muu"
    :unknown "Ei tietoa"}

   :owner
   {:city "Kunta"
    :registered-association "Rekisteröity yhdistys"
    :company-ltd "Yritys"
    :city-main-owner "Kuntaenemmistöinen yritys"
    :foundation "Säätiö"
    :state "Valtio"
    :other "Muu"
    :unkonwn "Ei tietoa"}

   :location
   {:headline "Sijainti"
    :address "Katuosoite"
    :postal-code "Postinumero"
    :postal-office "Postitoimipaikka"
    :city "Kunta"
    :city-code "Kuntanumero"}

   :ice
   {:headline "Jäähalliportaali"
    :description "Jäähalliportaali sisältää
              hallien perus- ja energiankulutustietoja sekä ohjeita
              energiatehokkuuden parantamiseen"
    :size-category "Kokoluokitus"
    :small "Pieni kilpahalli > 500 hlö"
    :competition "Kilpahalli < 3000 hlö"
    :large "Suurhalli > 3000 hlö"}

   :ice-rinks
   {:headline "Hallien tiedot"}

   :ice-energy
   {:headline "Energiatehokkuus"
    :description "Tänne .pdf dokumentti"}

   :ice-form
   {:headline "Ilmoita kulutustiedot"
    :select-rink "Valitse halli"}

   :ice-basic-data
   {:headline "Ilmoita tiedot"}

   :energy
   {:headline "Energiankulutus"
    :electricity "Sähkö MWh"
    :heat "Lämpö (ostettu) MWh"
    :water "Vesi m³"
    :add-energy-entry "Lisää energiatieto"
    :edit-energy-entry "Muokkaa energiatietoa"
    :remove-energy-entry "Poista energiatieto"}

   :swim
   {:headline "Uimahalliportaali"
    :edit "Päivitä tiedot"
    :description "Uimahalliportaali sisältää hallien perus- ja
          energiankulutustietoja, sekä ohjeita energiatehokkuuden
          parantamiseen."}

   :open-data
   {:headline "Avoin data"
    :description "Kaikki data on avointa."
    :rest "REST"
    :wms-wfs "WMS & WFS"
    :wms-wfs-description "Tämmöisetkin löytyy Geoserveriltä"}

   :pool-types (->translation-map :fi pools/pool-types)
   :pool-structures (->translation-map :fi materials/pool-structures)
   :sauna-types (->translation-map :fi pools/sauna-types)
   :slide-structures (->translation-map :fi materials/slide-structures)
   :filtering-methods (->translation-map :fi pools/filtering-methods)
   :building-materials (->translation-map :fi materials/building-materials)
   :supporting-structures (->translation-map :fi materials/supporting-structures)
   :ceiling-structures (->translation-map :fi materials/ceiling-structures)

   :help
   {:headline "Ohjeet"
    :description "Täältä löytyvät ohjeet"}

   :user
   {:headline "Oma sivu"
    :requested-permissions "Pyydetyt oikeudet"}

   :register
   {:headline "Rekisteröidy"
    :email "Sähköposti"
    :email-example "email@example.com"
    :username "Käyttäjätunnus"
    :username-example "tane12"
    :firstname "Etunimi"
    :lastname "Sukunimi"
    :password "Salasana"
    :permissions "Käyttöoikeudet"
    :permissions-example "Oikeus päivittää Jyväskylän jäähallien tietoja."
    :permissions-help "Kerro, mitä tietoja haluat päivittää Lipaksessa"}

   :login
   {:headline "Kirjaudu"
    :username "Käyttäjätunnus"
    :username-example "tane12"
    :password "Salasana"
    :login "Kirjaudu"
    :logout "Kirjaudu ulos"
    :bad-credentials "Käyttäjätunnus tai salasana ei kelpaa"}

   :building
   {:headline "Rakennus"
    :construction-year "Rakennusvuosi"
    :main-designers "Pääsuunnittelijat"
    :total-surface-area-m2 "Bruttopinta-ala m²"
    :total-volume-m3 "Bruttotilavuus m³"
    :pool-room-total-area-m2 "Allashuoneen pinta-ala m²"
    :total-water-area-m2 "Vesipinta-ala m²"
    :water-slides-total-length-m "Vesiliukumäet yht. (m)"
    :heat-sections? "Allastila on jaettu lämpötilaosioihin"
    :piled? "Paalutettu"
    :main-construction-materials "Päärakennusmateriaalit"
    :supporting-structures "Kantavat rakenteet"
    :ceiling-structures "Yläpohjan rakenteet"
    :staff-count "Henkilökunnan lukumäärä"
    :seating-capacity "Katsomokapasiteetti"}

   :envelope-structure
   {:headline "Vaipan rakenne"
    :base-floor-structure "Alapohjan laatan rakenne"
    :insulated-exterior? "Ulkoseinä lämpöeristetty"
    :insulated-ceiling? "Yläpohja lämpöeristetty"
    :low-emissivity-coating? "Yläpohjassa matalaemissiiviteettipinnote"}

   :renovations
   {:headline "Peruskorjaukset"
    :add-renovation "Lisää peruskorjaus"
    :edit-renovation "Muokkaa peruskorjausta"
    :end-year "Valmistumisvuosi"
    :designers "Suunnittelijat"}

   :rinks
   {:headline "Radat"
    :edit-rink "Muokkaa rataa"
    :add-rink "Lisää rata"}

   :refrigeration
   {:headline "Kylmätekniikka"
    :original? "Alkuperäinen"
    :individual-metering? "Alamittaroitu"
    :power-kw "Kylmäkoneen teho (kW)"
    :condensate-energy-recycling? "Lauhde-energia hyötykäytetty"
    :condensate-energy-main-target "Lauhdelämmön pääkäyttökohde"
    :refrigerant "Kylmäaine"
    :refrigerant-amount-kg "Kylmäaineen määrä (kg)"
    :refrigerant-solution "Kylmäliuos"
    :refrigerant-solution-amount-l "Kylmäliuoksen määrä (l)"}

   :conditions
   {:headline "Olosuhteet"
    :daily-open-hours "Aukiolotunnit päivässä"
    :open-months "Aukiolokuukaudet vuodessa"
    :air-humidity-min "Ilman suhteellinen kosteus % min"
    :air-humidity-max "Ilman suhteellinen kosteus % max"
    :ice-surface-temperature-c "Jään pinnan lämpötila"
    :skating-area-temperature-c "Luistelualueen lämpötila 1m korkeudella"
    :stand-temperature-c "Katsomon tavoiteltu keskilämpötila
          ottelun aikana"}

   :ice-maintenance
   {:headline "Jäänhoito"
    :daily-maintenance-count-week-days "Jäähoitokerrat arkipäivinä"
    :daily-maintenance-count-weekends "Jäähoitokerrat viikonlppuina"
    :average-water-consumption-l "Keskimääräinen jäänhoitoon
          käytetty veden määrä (per ajo)"
    :maintenance-water-temperature-c "Jäähoitoveden
          lämpötila (tavoite +40)"
    :ice-average-thickness-mm "Jään keskipaksuus mm"}

   :ventilation
   {:headline "Hallin ilmanvaihto"
    :heat-recovery-type "Lämmöntalteenoton tyyppi"
    :heat-recovery-thermal-efficiency-percent "Lämmöntalteenoton
          hyötysuhde %"
    :dryer-type "Ilmankuivaustapa"
    :dryer-duty-type "Ilmankuivauksen käyttötapa"
    :heat-pump-type "Lämpöpumpputyyppi"}

   :water-treatment
   {:headline "Vedenkäsittely"
    :ozonation? "Otsonointi"
    :uv-treatment? "UV-käsittely"
    :activated-carbon? "Aktiivihiili"
    :filtering-method "Suodatustapa"}

   :pools
   {:headline "Altaat"
    :add-pool "Lisää allas"
    :edit-pool "Muokkaa allasta"
    :structure "Rakenne"}

   :slides
   {:headline "Liukumäet"
    :add-slide "Lisää liukumäki"
    :edit-slide "Muokkaa liukumäkeä"}

   :saunas
   {:headline "Saunat"
    :add-sauna "Lisää sauna"
    :edit-sauna "Muokkaa saunaa"
    :women "Naiset"
    :men "Miehet"}

   :other-services
   {:headline "Muut palvelut"
    :platforms-1m-count "1m hyppypaikkojen lkm"
    :platforms-3m-count "3m hyppypaikkojen lkm"
    :platforms-5m-count "5m hyppypaikkojen lkm"
    :platforms-7.5m-count "7.5m hyppypaikkojen lkm"
    :platforms-10m-count "10m hyppypaikkojen lkm"
    :hydro-massage-spots-count "Hierontapisteiden lkm"
    :hydro-neck-massage-spots-count "Niskahierontapisteiden lkm"
    :kiosk? "Kioski / kahvio"}

   :facilities
   {:headline "Suihkut ja pukukaapit"
    :showers-men-count "Miesten suihkut lkm"
    :showers-women-count "Naisten suihkut lkm"
    :lockers-men-count "Miesten pukukaapit lkm"
    :lockers-women-count "Naisten pukukaapit lkm"}

   :dimensions
   {:volume-m3 "Tilavuus m²"
    :area-m2 "Pinta-ala m²"
    :surface-area-m2 "Pinta-ala m²"
    :length-m "Pituus m"
    :width-m "Leveys m"
    :depth-min-m "Syvyys min m"
    :depth-max-m "Syvyys max m"}

   :units
   {:person "Hlö"
    :pcs "Kpl"
    :percent "%"}

   :physical-units
   {:temperature-c "Lämpötila C°"
    :mwh "MWh"
    :m2 "m²"
    :m3 "m³"
    :celsius "C°"
    :hour "h"}

   :time
   {:year "Vuosi"
    :month "kuukautta"
    :hour "tuntia"
    :start "Alkoi"
    :end   "Päättyi"}

   :actions
   {:add "Lisää"
    :edit "Muokkaa"
    :save "Tallenna"
    :delete "Poista"
    :cancel "Peruuta"
    :select-hall "Valitse halli"}

   :general
   {:name "Nimi"
    :type "Tyyppi"
    :description "Kuvaus"
    :general-info "Yleiset tiedot"
    :comment "Kommentti"
    :structure "Rakenne"}

   :notification
   {:save-success "Tallennus onnistui"
    :save-failed "Tallennus epäonnistui"}

   :disclaimer
   {:headline "HUOMIO!"
    :test-version "Tämä on LIPAS-sovelluksen testiversio ja
    tarkoitettu koekäyttöön. Muutokset eivät tallennu oikeaan
    Lipakseen."}

   :error
   {:unknown "Tuntematon virhe tapahtui. :/"}})

(def sv {:menu
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
            :sv sv
            :en en
            :tongue/fallback :fi})

(comment (translate :fi :front-page/lipas-headline))
(comment (translate :fi :menu/sports-panel))
(comment (translate :fi :menu/sports-panel :lower))
(def translate (tongue/build-translate dicts))

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
     (-> (translate locale kw)
         (fmt args)))))
