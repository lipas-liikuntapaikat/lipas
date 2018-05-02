(ns lipas-ui.i18n
  (:require [tongue.core :as tongue]
            [clojure.string :as s]))

(def fi {:menu
         {:jyu "Jyväskylän yliopisto"}
         :sport
         {:headline "Liikuntapaikat"
          :description "LIPAS on suomalaisten liikuntapaikkojen tietokanta."}
         :ice
         {:headline "Jäähalliportaali"
          :description "Jäähalliportaali sisältää
              hallien perus- ja energiankulutustietoja sekä ohjeita
              energiatehokkuuden parantamiseen"}
         :ice-rinks
         {:headline "Hallien tiedot"}
         :ice-energy
         {:headline "Energiatehokkuus"
          :description "Tänne .pdf dokumentti"}
         :ice-form
         {:headline "Ilmoita kulutustiedot"
          :select-rink "Valitse halli"}
         :energy
         {:consumption-info "Kulutustiedot"
          :electricity "Sähkö"
          :heat "Lämpö (ostettu)"
          :water "Vesi"}
         :swim
         {:headline "Uimahalliportaali"
          :description "Uimahalliportaali sisältää hallien perus- ja
          energiankulutustietoja, sekä ohjeita energiatehokkuuden
          parantamiseen."}
         :open-data
         {:headline "Avoin data"
          :description "Kaikki data on avointa."
          :rest "REST"
          :wms-wfs "WMS & WFS"
          :wms-wfs-description "Tämmöisetkin löytyy Geoserveriltä"}
         :help
         {:headline "Ohjeet"
          :description "Täältä löytyvät ohjeet"}
         :user
         {:headline "Oma sivu"}
         :register
         {:headline "Rekisteröidy"
          :email "Sähköposti"
          :email-example "email@example.com"
          :username "Käyttäjänimi"
          :username-example "tane12"
          :firstname "Etunimi"
          :lastname "Sukunimi"
          :password "Salasana"
          :permissions "Käyttöoikeudet"
          :permissions-example "Oikeus päivittää Jyväskylän jäähallien tietoja."
          :permissions-help "Kerro, mitä tietoja haluat päivittää Lipaksessa"}
         :login
         {:headline "Kirjaudu"
          :username "Käyttäjänimi"
          :username-example "tane12"
          :password "Salasana"
          :login "Kirjaudu"
          :logout "Kirjaudu ulos"}
         :physical-units
         {:mwh "MWh"
          :m3 "m³"}
         :time
         {:year "Vuosi"}
         :actions
         {:save "Tallenna"}})

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
  "Creates translator fn with support for optional formatter.
  See `lipas-ui.i18n/fmt`

  Function usage: ((->tr-fn :fi) :menu/sports-panel :lower)
  => \"liikuntapaikat\""
  [locale]
  (fn [kw & args]
    (-> (translate locale kw)
        (fmt args))))
