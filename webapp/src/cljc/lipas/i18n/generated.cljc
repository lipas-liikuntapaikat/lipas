(ns lipas.i18n.generated)

;; From csv ;;

(def dicts
  {:fi
   {:loi
    {:category                     "Kategoria"
     :status                       "Tila"
     :type                         "Kohteen tyyppi"
     :type-and-category-disclaimer "Kategoria ja tyyppi tulee olla valittuna ennen kartalle lisäämistä"}
    :ptv
    {:tooltip          "Vie Palvelutietovarantoon"
     :service          "Palvelu"
     :services         "Palvelut"
     :service-channel  "Palvelupaikka"
     :service-channels "Palvelupaikat"
     :description      "Kuvaus"
     :descriptions     "Kuvaukset"
     :summary          "Tiivistelmä"
     :loading-from-ptv "Haetaan tietoja PTV:stä..."
     :sports-sites     "Liikuntapaikat"
     :tools            "Massatyökalut"
     :settings         "Asetukset"
     :wizard           "Käyttöönotto"
     :keywords         "Avainsanat"}
    :ptv.service
    {:classes                 "Palveluluokat"
     :show-only-lipas-managed "Näytä vain Lipakseen liitetyt palvelut"}
    :ptv.name-conflict
    {:opt1            "Liitä liikuntapaikka olemassa olevaan palvelupaikkaan painamalla \"Liitä tähän palvelupaikkaan\" -painiketta (suositus)"
     :opt2            "Valitse haluamasi palvelupaikka alla olevasta valitsimesta"
     :opt3            "Laita liukukytkin pois päältä, jolloin tätä kohdetta ei integroida PTV:oon."
     :opt4            "Arkistoi kyseinen palvelupaikka PTV:stä."
     :do-one-of-these "Tee yksi näistä"}
    :ptv.wizard
    {:generate-descriptions         "Laadi kuvaukset"
     :generate-descriptions-helper1 "Palvelut voidaan perustaa PTV:hen, kun niissä on vähintään suomenkielinen kuvaus ja tiivistelmä. LIPAS täydentää palveluluokituksen, avainsanat ja muut metatiedot automaattisesti."

     :generate-descriptions-helper2         "Paina \"Laadi kuvaukset\" -painiketta luodaksesi kuvaukset automaattisesti tekoälyn avulla. Kuvaukset laaditaan Lipakseen tallennettujen liikuntapaikkojen pohjalta."
     :services-to-add                       "Perustettavat palvelut"
     :export-services-to-ptv                "Vie palvelutietovarantoon"
     :export-service-locations-to-ptv       "Vie palvelutietovarantoon"
     :all-services-exist                    "Kaikki tarvittavat palvelut on PTV:ssä. Hieno juttu!"
     :integrate-service-locations           "Luo liikuntapaikoista palvelupaikat"
     :assign-services-to-sports-sites       "Liitä liikuntapaikat palveluihin"
     :generate-descriptions-helper3         "Palvelupaikat voidaan perustaa PTV:hen kun niissä on vähintään suomenkielinen kuvaus ja tiivistelmä. LIPAS täydentää palveluluokituksen, avainsanat ja muut metatiedot automaattisesti."
     :unselect-helper                       "Jos et halua viedä tiettyä kohdetta, voit avata
     kohteen oikealta ja laittaa liukukytkimen pois päältä."
     :service-channel-name-conflict         "PTV:ssä on jo olemassa palvelupaikka nimellä {1}."
     :attach-to-conflicting-service-channel "Liitä tähän palvelupaikkaan" }
    :ptv.tools.generate-services
    {:headline "Luo PTV-palvelut"}
    :ptv.tools.ai
    {:headline     "Laadi palvelupaikkojen kuvaukset tekoälyn avulla"
     :start        "Aloita"
     :start-helper "Voit muokata tekoälyn ehdotuksia sitä mukaa kuin ne valmistuvat."
     :canceling    "Peruutetaan... Odota hetki"}
    :ptv.tools.ai.sports-sites-filter
    {:label                                "Valitse liikuntapaikat"
     :all                                  "Kaikki"
     :sync-enabled                         "Vietäväksi valitut"
     :no-existing-description              "Kaikki joista kuvaus puuttuu"
     :sync-enabled-no-existing-description "Vietäväksi valitut joista kuvaus puuttuu"}
    :ptv.actions
    {:export                 "Vie"
     :export-disclaimer      "Olen lukenut kuvaukset ja haluan, että tämä kohde viedään PTV:hen."
     :select-org             "Valitse organisaatio"
     :select-integration     "Valitse liittämistapa"
     :generate-with-ai       "Generoi kuvaukset tekoälyllä"
     :select-service         "Valitse palvelu"
     :select-service-channel "Valitse palvelupaikka"
     :select-languages       "Valitse kielet"}
    :ptv.integration
    {:manual "Liitä manuaalisesti"}
    :ptv.integration.service
    {:lipas-managed        "LIPAS määrittää palvelut (suositeltu)"
     :lipas-managed-helper "LIPAS perustaa tarvittavat palvelut PTV:hen DVV:n suositusten mukaisesti ja liittää palvelupaikat (liikuntapaikat) niihin automaattisesti."
     :manual-helper        "Liikuntapaikat liitetään olemassa oleviin Palvelutietovarannon palveluihin manuaalisesti tässä työkalussa. Liitos täytyy tehdä jokaiselle liikuntapaikalle kerran."}
    :ptv.integration.service-channel
    {:lipas-managed        "LIPAS määrittää palvelupaikat (suositeltu)"
     :lipas-managed-helper "LIPAS perustaa jokaiselle liikuntapaikalle palvelupaikan PTV:hen ja liittää liikuntapaikkojen tiedot niihin automaattisesti."
     :manual-helper        "Liikuntapaikka liitetään olemassa olevaan palvelupaikkaan manuaalisesti tässä työkalussa. Liitos täytyy tehdä jokaiselle liikuntapaikalle kerran"}
    :ptv.integration.default-settings
    {:headline "Oletusasetukset"
     :helper   "Oletusasetukset voi ylikirjoittaa määrittämällä Liikuntapaikat-välilehdellä kohdekohtaiset asetukset."}
    :ptv.integration.interval
    {:headline  "Vienti Palvelutietovarantoon"
     :label     "Milloin muutokset viedään Palvelutietovarantoon"
     :immediate "Automaattisesti kun muutoksia tallennetaan Lipaksessa (suositeltu)"
     :daily     "Automaattisesti kerran päivässä"
     :manual    "Vain manuaalisesti"}
    :ptv.integration.description
    {:lipas-managed-comment-field        "Käytä liikuntapaikan lisätietokenttää"
     :lipas-managed-comment-field-helper "Palvelupaikan kuvaus ylläpidetään liikuntapaikan lisätietokentässä. Tiivistelmä on lisätietokentän ensimmäinen enter-painikkeella erotettu kappale."
     :lipas-managed-ptv-fields           "Käytä erillistä kuvausta"
     :lipas-managed-ptv-fields-helper    "Kuvaus ja tiivistelmä ylläpidetään liikuntapaikasta erillään tässä työkalussa. Kuvausehdotukset voidaan laatia automaattisesti tekoälyn avulla."
     :ptv-managed                        "Älä päivitä kuvauksia"
     :ptv-managed-helper                 "Kuvaus ja tiivistelmä ylläpidetään PTV:ssä. Lipas ei muuta tai päivitä kuvauksia."}
    :utp
    {:headline                   "Ulkoilutietopalvelu"
     :read-only-disclaimer       "Aktiviteeteille on toistaiseksi olemassa vain editointinäkymä. Kirjaudu sisään ja siirry kynäsymbolista muokkaustilaan."
     :add-contact                "Lisää yhteystieto"
     :unit                       "yksikkö"
     :highlight                  "Kohokohta"
     :add-highlight              "Lisää kohokohta"
     :photo                      "Valokuva"
     :add-photo                  "Lisää valokuva"
     :video                      "Video"
     :add-video                  "Lisää video"
     :link                       "Linkki"
     :length-km                  "Pituus km"
     :add-subroute               "Lisää osareitti"
     :delete-route-prompt        "Haluatko varmasti poistaa tämän reitin?"
     :custom-rule                "Oma lupa, sääntö, ohje"
     :custom-rules               "Omat säännöt"
     :add-subroute-ok            "OK"
     :route-is-made-of-subroutes "Reitti koostuu monesta erillisestä osuudesta"
     :select-route-parts-on-map  "Valitse reitin osat kartalta"
     :finish-route-details       "Reitti valmis"}
    :newsletter
    {:subscribe-short      "Tilaa"
     :subscribe            "Tilaa uutiskirje"
     :subscription-failed  "Uutiskirjeen tilaus epäonnistui. Yritä uudelleen."
     :subscription-success "Uutiskirje tilattu!"}
    :analysis
    {:headline                   "Analyysityökalu (beta)"
     :description                "Analyysityökalulla voi arvioida liikuntaolosuhteiden tarjontaa ja saavutettavuutta vertailemalla liikuntapaikan etäisyyttä ja matkustusaikoja suhteessa muihin liikuntapaikkoihin, väestöön sekä oppilaitoksiin."
     :results                    "Tulokset"
     :close                      "Sulje analyysityökalut"
     :mean                       "Keskiarvo"
     :population-weighted-mean   "Väestöpainotettu monipuolisuusindeksi"
     :median                     "Mediaani"
     :mode                       "Moodi"
     :reachability               "Saavutettavuus"
     :categories                 "Kategoriat"
     :diversity                  "Monipuolisuus"
     :diversity-grid             "Tulosruudukko"
     :diversity-help1            "Monipuolisuustyökalulla voi arvioida ja vertailla liikuntaolosuhteiden monipuolisuutta asukkaiden lähiympäristössä  ruutu- ja aluetasolla. Monipuolisuustyökalun laskema monipuolisuusindeksi kuvaa, kuinka monipuolisesti erilaisia liikunnan harrastusmahdollisuuksia asukas voi saavuttaa valitun etäisyyden sisällä tie- ja polkuverkostoja pitkin (oletuksena 800 m). Mitä korkeampi indeksin antama arvo on, sitä enemmän erilaisia liikuntapaikkoja asukkaiden lähiympäristössä on."
     :diversity-help2            "Oletuksena työkalussa käytetään postinumeroihin perustuvaa aluejakoa. Jako voidaan tehdä myös muun olemassa olevan aluejaon perusteella tuomalla haluttu geometriatiedosto (Shapefile, GeoJSON tai KML)."
     :diversity-help3            "Laskenta tehdään Tilastokeskuksen 250 x 250 m väestöruuduittain. Aluetason tulokset kertovat keskimääräisestä liikunnan olosuhteiden monipuolisuudesta alueen asukkaiden lähiympäristöissä (monipuolisuusindeksin väestöpainotettu keskiarvo). Monipuolisuustyökalun laskentaetäisyys perustuu OpenStreetMapin tieverkostoaineistoon ja OSRM-työkaluun."
     :analysis-areas             "Analyysialueet"
     :categories-help            "Samaan kategoriaan kuuluvat liikuntapaikkatyypit vaikuttavat monipuolisuusindeksiin vain yhden kerran."
     :description2               "Väestöaineistona käytetään Tilastokeskuksen 250x250m ja 1x1km ruutuaineistoja, joista selviää kussakin ruudussa olevan väestön jakauma kolmessa ikäryhmässä (0-14, 15-65, 65-)."
     :description3               "Matka-aikojen laskeminen eri kulkutavoilla (kävellen, polkupyörällä, autolla) perustuu avoimeen OpenStreetMap-aineistoon ja OSRM-työkaluun."
     :description4               "Oppilaitosten nimi- ja sijaintitiedot perustuvat Tilastokeskuksen avoimeen aineistoon. Varhaiskasvatusyksiköiden nimi- ja sijaintitiedoissa käytetään LIKES:n keräämää ja luovuttamaa aineistoa."
     :add-new                    "Lisää uusi"
     :distance                   "Etäisyys"
     :travel-time                "Matka-aika"
     :zones                      "Korit"
     :zone                       "Kori"
     :schools                    "Koulut"
     :daycare                    "Varhaiskasvatusyksikkö"
     :elementary-school          "Peruskoulu"
     :high-school                "Lukio"
     :elementary-and-high-school "Perus- ja lukioasteen koulu"
     :special-school             "Erityiskoulu"
     :population                 "Väestö"
     :direct                     "Linnuntietä"
     :by-foot                    "Kävellen"
     :by-car                     "Autolla"
     :by-bicycle                 "Polkupyörällä"
     :analysis-buffer            "Analyysialue"
     :filter-types               "Suodata tyypin mukaan"
     :settings                   "Asetukset"
     :settings-map               "Kartalla näkyvät kohteet"
     :settings-zones             "Etäisyydet ja matka-ajat"
     :settings-help              "Analyysialue määräytyy suurimman etäisyyskorin mukaan. Myöskään matka-aikoja ei lasketa tämän alueen ulkopuolelta."}
    :sport
    {#_#_:description
     "LIPAS tarjoaa ajantasaisen tiedon Suomen julkisista liikunta- ja ulkoilupaikoista avoimessa tietokannassa."
     :description     "LIPAS on valtakunnallinen liikunnan paikkatietojärjestelmä."
     :headline        "Liikunta- ja ulkoilupaikat",
     :open-interfaces "Avoimet rajapinnat",
     :up-to-date-information
     "Ajantasainen tieto Suomen liikunta- ja ulkoilupaikoista",
     :updating-tools  "Päivitystyökalut tiedontuottajille"
     :analysis-tools  "Liikuntaolosuhteiden analysointityökalut"},
    :confirm
    {:discard-changes? "Tahdotko kumota tekemäsi muutokset?",
     :headline         "Varmistus",
     :no               "Ei",
     :delete-confirm   "Haluatko varmasti poistaa rivin?",
     :resurrect?       "Tahdotko palauttaa liikuntapaikan aktiiviseksi?",
     :save-basic-data? "Haluatko tallentaa perustiedot?",
     :yes              "Kyllä"},
    :lipas.swimming-pool.saunas
    {:accessible? "Esteetön",
     :add-sauna   "Lisää sauna",
     :edit-sauna  "Muokkaa saunaa",
     :headline    "Saunat",
     :men?        "Miehet",
     :women?      "Naiset"},
    :slide-structures
    {:concrete         "Betoni",
     :hardened-plastic "Lujitemuovi",
     :steel            "Teräs"},
    :lipas.swimming-pool.conditions
    {:open-days-in-year "Aukiolopäivät vuodessa",
     :open-hours-mon    "Maanantaisin",
     :headline          "Aukiolo",
     :open-hours-wed    "Keskiviikkoisin",
     :open-hours-thu    "Torstaisin",
     :open-hours-fri    "Perjantaisin",
     :open-hours-tue    "Tiistaisin",
     :open-hours-sun    "Sunnuntaisin",
     :daily-open-hours  "Aukiolotunnit päivässä",
     :open-hours-sat    "Lauantaisin"},
    :lipas.ice-stadium.ventilation
    {:dryer-duty-type          "Ilmankuivauksen käyttötapa",
     :dryer-type               "Ilmankuivaustapa",
     :headline                 "Hallin ilmanvaihto",
     :heat-pump-type           "Lämpöpumpputyyppi",
     :heat-recovery-efficiency "Lämmöntalteenoton hyötysuhde %",
     :heat-recovery-type       "Lämmöntalteenoton tyyppi"},
    :swim
    {:description
     "Uimahalliportaali sisältää hallien perus- ja  energiankulutustietoja sekä ohjeita energiatehokkuuden  parantamiseen.",
     :latest-updates       "Viimeksi päivitetyt tiedot",
     :headline             "Uimahalli​portaali",
     :basic-data-of-halls  "Uimahallien perustiedot",
     :updating-basic-data  "Perustietojen päivitys",
     :edit                 "Uimahalli​portaali",
     :entering-energy-data "Energiankulutustietojen syöttäminen",
     :list                 "Hallien tiedot",
     :visualizations       "Hallien vertailu"},
    :home-page          {:description "Etusivu", :headline "Etusivu"},
    :ice-energy
    {:description
     "Ajantasaisen tietopaketin löydät Jääkiekkoliiton sivuilta.",
     :energy-calculator "Jäähallin energialaskuri",
     :finhockey-link    "Siirry Jääkiekkoliiton sivuille",
     :headline          "Energia​info"},
    :filtering-methods
    {:activated-carbon      "Aktiivihiili",
     :coal                  "Hiili",
     :membrane-filtration   "Kalvosuodatus",
     :multi-layer-filtering "Monikerrossuodatus",
     :open-sand             "Avohiekka",
     :other                 "Muu",
     :precipitation         "Saostus",
     :pressure-sand         "Painehiekka"},
    :open-data
    {:description "Linkit ja ohjeet rajapintojen käyttöön.",
     :headline    "Avoin data",
     :rest        "REST",
     :wms-wfs     "WMS & WFS"},
    :lipas.floorball
    {:headline "Olosuhteet"}
    :lipas.swimming-pool.pool
    {:accessibility "Saavutettavuus", :outdoor-pool? "Ulkoallas"},
    :pool-types
    {:multipurpose-pool "Monitoimiallas",
     :whirlpool-bath    "Poreallas",
     :main-pool         "Pääallas",
     :therapy-pool      "Terapia-allas",
     :fitness-pool      "Kuntouintiallas",
     :diving-pool       "Hyppyallas",
     :childrens-pool    "Lastenallas",
     :paddling-pool     "Kahluuallas",
     :cold-pool         "Kylmäallas",
     :other-pool        "Muu allas",
     :teaching-pool     "Opetusallas"},
    :lipas.ice-stadium.envelope
    {:base-floor-structure "Alapohjan laatan rakenne",
     :headline             "Vaipan rakenne",
     :insulated-ceiling?   "Yläpohja lämpöeristetty",
     :insulated-exterior?  "Ulkoseinä lämpöeristetty",
     :low-emissivity-coating?
     "Yläpohjassa  matalaemissiiviteettipinnoite (heijastus/säteily)"},
    :disclaimer
    {:headline       "HUOMIO!",
     :test-version
     "Tämä on LIPAS-sovelluksen testiversio ja  tarkoitettu koekäyttöön. Muutokset eivät tallennu oikeaan  Lipakseen."
     :data-ownership "LIPAS-paikkatietojärjestelmän tietovarannon omistaa ja sitä hallinnoi Jyväskylän yliopisto. Jyväskylän yliopisto pidättää oikeudet kaikkeen järjestelmään lisättyyn sekä järjestelmän kehittämisen yhteydessä luotuun sisältöön, tietoon ja aineistoon. Aineistoa järjestelmään lisäävä käyttäjä vastaa siitä, että aineisto on oikeellista eikä se loukkaa kolmannen osapuolen tekijänoikeuksia. Jyväskylän yliopisto ei vastaa aineistoon kohdistuvista kolmansien osapuolten vaateista. Lisäämällä tietoaineistoa LIPAS-paikkatietojärjestelmään käyttäjän katsotaan hyväksyneen lisäämänsä aineiston käytön järjestelmässä osana tietovarantoa. LIPAS-tietokantaan tallennettu tietoaineisto on avointa ja vapaasti käytettävissä CC 4.0 Nimeä -lisenssin mukaisesti."},
    :admin
    {:private-foundation      "Yksityinen / säätiö",
     :city-other              "Kunta / muu",
     :unknown                 "Ei tietoa",
     :municipal-consortium    "Kuntayhtymä",
     :private-company         "Yksityinen / yritys",
     :private-association     "Yksityinen / yhdistys",
     :other                   "Muu",
     :city-technical-services "Kunta / tekninen toimi",
     :city-sports             "Kunta / liikuntatoimi",
     :state                   "Valtio",
     :city-education          "Kunta / opetustoimi"},
    :accessibility
    {:lift            "Allasnostin",
     :low-rise-stairs "Loivat portaat",
     :mobile-lift     "Siirrettävä allasnostin",
     :slope           "Luiska"},
    :general
    {:headline       "Otsikko"
     :description    "Kuvaus",
     :hall           "Halli",
     :women          "Naiset",
     :age-anonymized "Ikä anonymisoitu"
     :total-short    "Yht.",
     :done           "Valmis",
     :updated        "Päivitetty",
     :name           "Nimi",
     :reported       "Ilmoitettu",
     :type           "Tyyppi",
     :last-modified  "Muokattu viimeksi",
     :here           "tästä",
     :event          "Tapahtuma",
     :structure      "Rakenne",
     :general-info   "Yleiset tiedot",
     :comment        "Kommentti",
     :measures       "Mitat",
     :men            "Miehet"
     :more           "Enemmän"
     :less           "Vähemmän"},
    :dryer-duty-types   {:automatic "Automaattinen", :manual "Manuaali"},
    :swim-energy
    {:description
     "Ajantasaisen tietopaketin löydät UKTY:n ja SUH:n sivuilta.",
     :headline       "Energia​info",
     :headline-short "Info",
     :suh-link       "Siirry SUH:n sivuille",
     :ukty-link      "Siirry UKTY:n sivuille"},
    :time
    {:two-years-ago      "2 vuotta sitten",
     :date               "Päivämäärä",
     :hour               "Tunti",
     :this-month         "Tässä kuussa",
     :time               "Aika",
     :less-than-hour-ago "Alle tunti sitten",
     :start              "Alkoi",
     :this-week          "Tällä viikolla",
     :today              "Tänään",
     :month              "Kuukausi",
     :long-time-ago      "Kauan sitten",
     :year               "Vuosi",
     :just-a-moment-ago  "Hetki sitten",
     :yesterday          "Eilen",
     :three-years-ago    "3 vuotta sitten",
     :end                "Päättyi",
     :this-year          "Tänä vuonna",
     :last-year          "Viime vuonna"},
    :ice-resurfacer-fuels
    {:LPG         "Nestekaasu",
     :electicity  "Sähkö",
     :gasoline    "Bensiini",
     :natural-gas "Maakaasu",
     :propane     "Propaani"},
    :ice-rinks          {:headline "Hallien tiedot"},
    :month
    {:sep "Syyskuu",
     :jan "Tammikuu",
     :jun "Kesäkuu",
     :oct "Lokakuu",
     :jul "Heinäkuu",
     :apr "Huhtikuu",
     :feb "Helmikuu",
     :nov "Marraskuu",
     :may "Toukokuu",
     :mar "Maaliskuu",
     :dec "Joulukuu",
     :aug "Elokuu"},
    :type
    {:name          "Liikuntapaikkatyyppi",
     :main-category "Pääryhmä",
     :sub-category  "Alaryhmä",
     :type-code     "Tyyppikoodi"
     :geometry      "Geometria",
     :Point         "Piste",
     :LineString    "Reitti",
     :Polygon       "Alue"},
    :duration
    {:hour        "tuntia",
     :month       "kuukautta",
     :years       "vuotta",
     :years-short "v"},
    :size-categories
    {:competition "Kilpahalli < 3000 hlö",
     :large       "Suurhalli > 3000 hlö",
     :small       "Pieni kilpahalli > 500 hlö"},
    :lipas.admin
    {:access-all-sites
     "Sinulla on pääkäyttäjän oikeudet. Voit muokata kaikkia liikuntapaikkoja.",
     :confirm-magic-link
     "Haluatko varmasti lähettää taikalinkin käyttäjälle {1}?",
     :headline                   "Admin",
     :magic-link                 "Taikalinkki",
     :select-magic-link-template "Valitse saatekirje",
     :send-magic-link            "Taikalinkki käyttäjälle {1}",
     :users                      "Käyttäjät"},
    :lipas.ice-stadium.refrigeration
    {:headline                       "Kylmätekniikka",
     :refrigerant-solution-amount-l  "Rataliuoksen määrä (l)",
     :individual-metering?           "Alamittaroitu",
     :original?                      "Alkuperäinen",
     :refrigerant                    "Kylmäaine",
     :refrigerant-solution           "Rataliuos",
     :condensate-energy-main-targets "Lauhdelämmön pääkäyttökohde",
     :power-kw                       "Kylmäkoneen sähköteho (kW)",
     :condensate-energy-recycling?   "Lauhde-energia hyötykäytetty",
     :refrigerant-amount-kg          "Kylmäaineen määrä (kg)"},
    :lipas.building
    {:headline                    "Rakennus",
     :total-volume-m3             "Bruttotilavuus m³",
     :staff-count                 "Henkilökunnan lukumäärä",
     :piled?                      "Paalutettu",
     :heat-sections?              "Allastila on jaettu lämpötilaosioihin",
     :total-ice-area-m2           "Jääpinta-ala m²",
     :main-construction-materials "Päärakennusmateriaalit",
     :main-designers              "Pääsuunnittelijat",
     :total-pool-room-area-m2     "Allashuoneen pinta-ala m²",
     :heat-source                 "Lämmönlähde",
     :total-surface-area-m2       "Bruttopinta-ala m² (kokonaisala)",
     :total-water-area-m2         "Vesipinta-ala m²",
     :ceiling-structures          "Yläpohjan rakenteet",
     :supporting-structures       "Kantavat rakenteet",
     :seating-capacity            "Katsomokapasiteetti"},
    :heat-pump-types
    {:air-source         "Ilmalämpöpumppu",
     :air-water-source   "Ilma-vesilämpöpumppu",
     :exhaust-air-source "Poistoilmalämpöpumppu",
     :ground-source      "Maalämpöpumppu",
     :none               "Ei lämpöpumppua"},
    :search
    {:table-view            "Näytä hakutulokset taulukossa",
     :headline              "Haku",
     :results-count         "{1} hakutulosta",
     :placeholder           "Etsi...",
     :retkikartta-filter    "Retkikartta.fi-kohteet",
     :harrastuspassi-filter "Harrastuspassi.fi-kohteet",
     :filters               "Rajaa hakua",
     :search-more           "Hae lisää...",
     :page-size             "Näytä kerralla",
     :search                "Hae",
     :permissions-filter    "Näytä vain kohteet joita voin muokata",
     :display-closest-first "Näytä lähimmät kohteet ensin",
     :list-view             "Näytä hakutulokset listana",
     :pagination            "Tulokset {1}-{2}",
     :school-use-filter     "Koulujen liikuntapaikat",
     :clear-filters         "Poista rajaukset"},
    :map.tools
    {:download-backup-tooltip       "Lataa varmuuskopio"
     :restore-backup-tooltip        "Palauta varmuuskopio"
     :drawing-tooltip               "Piirtotyökalu valittu",
     :drawing-hole-tooltip          "Reikäpiirtotyökalu valittu",
     :edit-tool                     "Muokkaustyökalu",
     :importing-tooltip             "Tuontityökalu valittu",
     :deleting-tooltip              "Poistotyökalu valittu",
     :splitting-tooltip             "Katkaisutyökalu valittu"
     :simplifying                   "Yksinkertaistutyökalu valittu"
     :selecting                     "Valintatyökalu valittu"
     :simplify                      "Yksinkertaista"
     :travel-direction-tooltip      "Kulkusuuntatyökalu valittu"
     :route-part-difficulty-tooltip "Reittiosan vaativuus työkalu valittu"},
    :map.tools.simplify
    {:headline "Yksinkertaista geometrioita"}
    :partners           {:headline "Kehittä​misessä mukana"},
    :actions
    {:duplicate                "Kopioi",
     :resurrect                "Palauta",
     :select-year              "Valitse vuosi",
     :select-owners            "Valitse omistajat",
     :select-admins            "Valitse ylläpitäjät",
     :select-tool              "Valitse työkalu",
     :save-draft               "Tallenna luonnos",
     :redo                     "Tee uudelleen",
     :open-main-menu           "Avaa päävalikko",
     :back-to-listing          "Takaisin listaukseen",
     :filter-surface-materials "Rajaa pintamateriaalit",
     :browse                   "siirry",
     :select-type              "Valitse tyyppi",
     :edit                     "Muokkaa",
     :filter-construction-year "Rajaa rakennusvuodet",
     :submit                   "Lähetä",
     :choose-energy            "Valitse energia",
     :delete                   "Poista",
     :browse-to-map            "Siirry kartalle",
     :save                     "Tallenna",
     :close                    "Sulje",
     :filter-area-m2           "Rajaa liikuntapinta-ala m²",
     :show-account-menu        "Avaa käyttäjävalikko",
     :fill-required-fields     "Täytä pakolliset kentät",
     :undo                     "Kumoa",
     :browse-to-portal         "Siirry portaaliin",
     :download-excel           "Lataa",
     :fill-data                "Täytä tiedot",
     :select-statuses          "Liikuntapaikan tila",
     :select-cities            "Valitse kunnat",
     :select-hint              "Valitse...",
     :discard                  "Kumoa",
     :more                     "Lisää...",
     :cancel                   "Peruuta",
     :select-columns           "Valitse tietokentät",
     :add                      "Lisää",
     :show-all-years           "Näytä kaikki vuodet",
     :download                 "Lataa",
     :select-hall              "Valitse halli",
     :clear-selections         "Poista valinnat",
     :select                   "Valitse",
     :select-types             "Valitse tyypit"
     :select-all               "Valitse kaikki"},
    :dimensions
    {:area-m2         "Pinta-ala m²",
     :depth-max-m     "Syvyys max m",
     :depth-min-m     "Syvyys min m",
     :length-m        "Pituus m",
     :surface-area-m2 "Pinta-ala m²",
     :volume-m3       "Tilavuus m³",
     :width-m         "Leveys m"},
    :login
    {:headline              "Kirjaudu",
     :login-here            "täältä",
     :login-with-password   "Kirjaudu salasanalla",
     :password              "Salasana",
     :logout                "Kirjaudu ulos",
     :username              "Sähköposti / käyttäjätunnus",
     :login-help
     "Jos olet jo LIPAS-käyttäjä, voit tilata suoran sisäänkirjautumislinkin sähköpostiisi",
     :login                 "Kirjaudu",
     :magic-link-help
     "Jos olet jo LIPAS-käyttäjä, voit tilata suoran sisäänkirjautumislinkin sähköpostiisi. Linkkiä käyttämällä sinun  ei tarvitse muistaa salasanaasi.",
     :order-magic-link      "Lähetä linkki sähköpostiini",
     :login-with-magic-link "Kirjaudu sähköpostilla",
     :bad-credentials       "Käyttäjätunnus tai salasana ei kelpaa",
     :magic-link-ordered
     "Linkki on lähetetty ja sen pitäisi saapua  sähköpostiisi parin minuutin sisällä. Tarkistathan myös  roskapostin!",
     :username-example      "paavo.paivittaja@kunta.fi",
     :forgot-password?      "Unohtuiko salasana?"},
    :lipas.ice-stadium.conditions
    {:open-months                  "Aukiolokuukaudet vuodessa",
     :headline                     "Käyttöolosuhteet",
     :ice-resurfacer-fuel          "Jäänhoitokoneen polttoaine",
     :stand-temperature-c
     "Katsomon tavoiteltu keskilämpötila  ottelun aikana",
     :ice-average-thickness-mm     "Jään keskipaksuus mm",
     :air-humidity-min             "Ilman suhteellinen kosteus % min",
     :daily-maintenances-weekends  "Jäähoitokerrat viikonlppuina",
     :air-humidity-max             "Ilman suhteellinen kosteus % max",
     :daily-maintenances-week-days "Jäähoitokerrat arkipäivinä",
     :maintenance-water-temperature-c
     "Jäähoitoveden  lämpötila (tavoite +40)",
     :ice-surface-temperature-c    "Jään pinnan lämpötila",
     :weekly-maintenances          "Jäänhoitokerrat viikossa",
     :skating-area-temperature-c
     "Luistelualueen lämpötila 1 m korkeudella",
     :daily-open-hours             "Käyttötunnit päivässä",
     :average-water-consumption-l
     "Keskimääräinen jäänhoitoon  käytetty veden määrä (per ajo)"},
    :map.demographics
    {:headline   "Analyysityökalut",
     :tooltip    "Analyysityökalut",
     :helper-text
     "Valitse liikuntapaikka kartalta tai lisää uusi analysoitava kohde ",
     :copyright1
     "Väestötiedoissa käytetään Tilastokeskuksen vuoden 2022",
     :copyright2 "1x1 km ruutuaineistoa",
     :copyright3 "lisenssillä"
     :copyright4 ", sekä vuoden 2022 250x250m ruutuaineistoa suljetulla lisenssillä."},
    :lipas.swimming-pool.slides
    {:add-slide  "Lisää liukumäki",
     :edit-slide "Muokkaa liukumäkeä",
     :headline   "Liukumäet"},
    :notifications
    {:get-failed             "Tietojen hakeminen epäonnistui.",
     :save-failed            "Tallennus epäonnistui",
     :save-success           "Tallennus onnistui"
     :ie                     (str "Internet Explorer ei ole tuettu selain. "
                                  "Suosittelemme käyttämään toista selainta, "
                                  "esim. Chrome, Firefox tai Edge.")
     :thank-you-for-feedback "Kiitos palautteesta!"},
    :lipas.swimming-pool.energy-saving
    {:filter-rinse-water-heat-recovery?
     "Suodattimien huuhteluveden lämmöntalteenotto",
     :headline                    "Energian​säästö​toimet",
     :shower-water-heat-recovery? "Suihkuveden lämmöntalteenotto"},
    :ice-form
    {:headline       "Nestekaasu",
     :headline-short "Sähkö",
     :select-rink    "Nestekaasu"},
    :restricted
    {:login-or-register "Kirjaudu sisään tai rekisteröidy"},
    :lipas.ice-stadium.rinks
    {:rink1-width   "Kentän 1 leveys m"
     :rink2-width   "Kentän 2 leveys m"
     :rink3-width   "Kentän 3 leveys m"
     :rink1-length  "Kentän 1 pituus m"
     :rink2-length  "Kentän 2 pituus m"
     :rink3-length  "Kentän 3 pituus m"
     :rink1-area-m2 "Kentän 1 pinta-ala m²"
     :rink2-area-m2 "Kentän 2 pinta-ala m²"
     :rink3-area-m2 "Kentän 3 pinta-ala m²"
     :add-rink      "Lisää kenttä",
     :edit-rink     "Muokkaa kenttää",
     :headline      "Kentät"},
    :sports-site.elevation-profile
    {:headline                "Korkeusprofiili"
     :distance-from-start-m   "Etäisyys alusta (m)"
     :distance-from-start-km  "Etäisyys alusta (km)"
     :height-from-sea-level-m "Korkeus merenpinnasta (m)"
     :total-ascend            "Nousua yhteensä"
     :total-descend           "Laskua yhteensä"}
    :lipas.sports-site
    {:accessibility          "Esteettömyys"
     :properties             "Lisätiedot",
     :delete-tooltip         "Poista liikuntapaikka...",
     :headline               "Liikuntapaikka",
     :new-site-of-type       "Uusi {1}",
     :address                "Osoite",
     :new-site               "Uusi liikuntapaikka",
     :phone-number           "Puhelinnumero",
     :admin                  "Ylläpitäjä",
     :admin-helper-text      "Kohteen ylläpidon toteuttava taho"
     :surface-materials      "Pintamateriaalit",
     :www                    "Web-sivu",
     :name                   "Nimi suomeksi",
     :reservations-link      "Tilavaraukset",
     :construction-year      "Rakennus​vuosi",
     :type                   "Tyyppi",
     :delete                 "Poista {1}",
     :renovation-years       "Perus​korjaus​vuodet",
     :name-localized-se      "Nimi ruotsiksi",
     :name-localized-en      "Nimi englanniksi"
     :status                 "Liikuntapaikan tila",
     :id                     "LIPAS-ID",
     :details-in-portal      "Näytä kaikki lisätiedot",
     :comment                "Lisätieto",
     :ownership              "Omistus",
     :name-short             "Nimi",
     :basic-data             "Perustiedot",
     :delete-reason          "Poiston syy",
     :event-date             "Muokattu",
     :email-public           "Sähköposti (julkinen)",
     :add-new                "Lisää liikunta- tai ulkoilupaikka",
     :contact                "Yhteystiedot",
     :contact-helper-text    "Yleisesti kohteesta, palveluista tai rakenteista vastaavan organisaation yhteystiedot"
     :owner                  "Omistaja",
     :owner-helper-text      "Kohteessa olevien rakenteiden tai palveluiden omistaja"
     :marketing-name         "Markkinointinimi"
     :no-permission-tab      "Sinulla ei ole oikeutta muokata tämän välilehden tietoja"
     :add-new-planning       "Lisää liikunta- tai ulkoilupaikka vedos-tilassa"
     :planning-site          "Vedos"
     :creating-planning-site "Olet lisäämässä paikkaa vedos-tilassa analyysityökaluja varten."},
    :status
    {:active                     "Toiminnassa",
     :planned                    "Suunniteltu"
     :incorrect-data             "Väärä tieto",
     :out-of-service-permanently "Poistettu käytöstä pysyvästi",
     :out-of-service-temporarily "Poistettu käytöstä väliaikaisesti"},
    :register
    {:headline "Rekisteröidy",
     :link     "Rekisteröidy"
     :thank-you-for-registering
     "Kiitos rekisteröitymisestä! Saat  sähköpostiisi viestin kun sinulle on myönnetty käyttöoikeudet."},
    :map.address-search {:title "Etsi osoite", :tooltip "Etsi osoite"},
    :map.resolve-address
    {:tooltip        "Selvitä osoite automaattisesti"
     :choose-address "Valitse osoite"
     :helper-text1   "Työkalu hakee lähimmät osoitteet liikuntapaikan geometrian perusteella."
     :helper-text2   "Valitse katuosoite parhaan tietosi mukaan. Voit täydentää osoitenumeron OK-napin painamisen jälkeen."
     :addresses      "Lähimmät osoitteet"}
    :ice-comparison     {:headline "Hallien vertailu"},
    :lipas.visitors
    {:headline             "Kävijämäärät",
     :monthly-visitors-in-year
     "Kuukausittaiset kävijämäärät vuonna {1}",
     :not-reported         "Ei kävijämäärätietoja",
     :not-reported-monthly "Ei kuukausitason tietoja",
     :spectators-count     "Katsojamäärä",
     :total-count          "Käyttäjämäärä"},
    :lipas.energy-stats
    {:headline        "Hallien energiankulutus vuonna {1}",
     :energy-reported-for
     "Sähkön-, lämmön- ja vedenkulutus ilmoitettu vuodelta {1}",
     :report          "Ilmoita lukemat",
     :disclaimer      "*Perustuu ilmoitettuihin kulutuksiin vuonna {1}",
     :reported        "Ilmoitettu {1}",
     :cold-mwh        "Kylmä MWh",
     :hall-missing?   "Puuttuvatko hallisi tiedot kuvasta?",
     :not-reported    "Ei tietoa {1}",
     :water-m3        "Vesi m³",
     :electricity-mwh "Sähkö MWh",
     :heat-mwh        "Lämpö MWh",
     :energy-mwh      "Sähkö + lämpö MWh"},
    :map.basemap
    {:copyright    "© Maanmittauslaitos",
     :maastokartta "Maastokartta",
     :ortokuva     "Ilmakuva",
     :taustakartta "Taustakartta"
     :transparency "Pohjakartan läpinäkyvyys"},
    :map.overlay
    {:tooltip                       "Muut karttatasot"
     :mml-kiinteisto                "Kiinteistörajat"
     :light-traffic                 "Kevyen liikenteen väylät"
     :retkikartta-snowmobile-tracks "Metsähallituksen moottorikelkkaurat"}
    :lipas.swimming-pool.pools
    {:add-pool  "Lisää allas",
     :edit-pool "Muokkaa allasta",
     :headline  "Altaat",
     :structure "Rakenne"},
    :condensate-energy-targets
    {:hall-heating              "Hallin lämmitys",
     :maintenance-water-heating "Jäänhoitoveden lämmitys",
     :other-space-heating       "Muun tilan lämmitys",
     :service-water-heating     "Käyttöveden lämmitys",
     :snow-melting              "Lumensulatus",
     :track-heating             "Ratapohjan lämmitys"},
    :refrigerants
    {:CO2   "CO2 (hiilidioksidi)",
     :R134A "R134A",
     :R22   "R22",
     :R404A "R404A",
     :R407A "R407A",
     :R407C "R407C",
     :R717  "R717"},
    :harrastuspassi
    {:disclaimer
     "Kun ”saa julkaista Harrastuspassissa” – ruutu on rastitettu, liikuntapaikan tiedot siirretään automaattisesti Harrastuspassi-palveluun. Sitoudun päivittämään liikuntapaikan tietojen muutokset viipymättä Lippaaseen. Paikan hallinnoijalla on vastuu tietojen oikeellisuudesta ja paikan turvallisuudesta. Tiedot näkyvät Harrastuspassissa vain, mikäli kunnalla on sopimus Harrastuspassin käytöstä Harrastuspassin palveluntuottajan kanssa."}
    :retkikartta
    {:disclaimer
     "Kun ”Saa julkaista Retkikartassa” -ruutu  on rastitettu, luontoliikuntapaikan tiedot siirretään  automaattisesti Metsähallituksen ylläpitämään Retkikartta.fi  -karttapalveluun kerran vuorokaudessa. Sitoudun päivittämään  luontoliikuntapaikan tietojen muutokset viipymättä  Lippaaseen. Paikan hallinnoijalla on vastuu tietojen  oikeellisuudesta, paikan turvallisuudesta, palautteisiin  vastaamisesta ja mahdollisista yksityisteihin liittyvistä  kustannuksista."},
    :reset-password
    {:change-password      "Vaihda salasana",
     :enter-new-password   "Syötä uusi salasana",
     :get-new-link         "Tilaa uusi vaihtolinkki",
     :headline             "Unohtuiko salasana?",
     :helper-text
     "Lähetämme salasanan vaihtolinkin sinulle sähköpostitse.",
     :password-helper-text "Salasanassa on oltava vähintään 6 merkkiä",
     :reset-link-sent      "Linkki lähetetty! Tarkista sähköpostisi.",
     :reset-success
     "Salasana vaihdettu! Kirjaudu sisään uudella salasanalla."},
    :reports
    {:contacts             "Yhteys​tiedot",
     :download-as-excel    "Luo raportti",
     :select-fields        "Valitse raportin kentät",
     :selected-fields      "Valitut kentät",
     :shortcuts            "Pikavalinnat",
     :tooltip              "Luo Excel-raportti hakutuloksista"
     :file-format          "Tiedostotyyppi"
     :excel-limit-exceeded "Liian suuri Excel-tiedosto (> 10 000 hakutulosta). Rajaa hakua tai valitse eri tiedostomuoto."},
    :heat-sources
    {:district-heating      "Kaukolämpö",
     :private-power-station "Oma voimalaitos"},
    :map.import
    {:headline          "Tuo geometriat",
     :geoJSON
     "Tuo .json tai .geojson päätteinen tiedosto, joka sisältää GeoJSON FeatureCollection -objektin. Lähtöaineiston pitää olla WGS84-koordinaatistossa.",
     :gpx               "Lähtöaineiston pitää olla WGS84-koordinaatistossa.",
     :supported-formats "Tuetut tiedostomuodot ovat {1}",
     :replace-existing? "Korvaa nykyiset geometriat",
     :select-encoding   "Valitse merkistö",
     :tab-header        "Tuo tiedostosta",
     :kml               "Lähtöaineiston pitää olla WGS84 koordinaatistossa.",
     :shapefile
     "Tuo .shp-, .dbf- ja .prj-tiedostot pakattuna .zip-muotoon.",
     :import-selected   "Tuo valitut",
     :tooltip           "Tuo geometriat tiedostosta",
     :unknown-format    "Tuntematon tiedostopääte '{1}'"
     :unknown-error     "Odottamaton virhe tapahtui. Yritä toisella tiedostolla."
     :no-geoms-of-type  "Annettu tiedosto ei sisällä {1} geometrioita."

     :coords-not-in-finland-wgs84-bounds "Annettu tiedosto ei sisällä koordinaatteja WGS84 (EPSG:4326) koordinaatistossa. Tarkista että lähtöaineisto on oikeassa koordinaattijärjestelmässä."

     },
    :error
    {:email-conflict    "Sähköpostiosoite on jo käytössä",
     :email-not-found   "Sähköpostiosoitetta ei ole rekisteröity.",
     :invalid-form      "Korjaa punaisella merkityt kohdat",
     :no-data           "Ei tietoja",
     :reset-token-expired
     "Salasanan vaihto epäonnistui. Linkki on vanhentunut.",
     :unknown           "Tuntematon virhe tapahtui. :/",
     :user-not-found    "Käyttäjää ei löydy.",
     :username-conflict "Käyttäjätunnus on jo käytössä"},
    :reminders
    {:description
     "Viesti lähetetään sähköpostiisi valittuna ajankohtana",
     :after-one-month  "Kuukauden kuluttua",
     :placeholder      "Muista tarkistaa liikuntapaikka \"{1}\" {2}",
     :select-date      "Valitse päivämäärä",
     :tomorrow         "Huomenna",
     :title            "Lisää muistutus",
     :after-six-months "Puolen vuoden kuluttua",
     :in-a-year        "Vuoden kuluttua",
     :message          "Viesti",
     :in-a-week        "Viikon kuluttua"},
    :units
    {:days-in-year   "päivää vuodessa",
     :hours-per-day  "tuntia päivässä",
     :pcs            "kpl",
     :percent        "%",
     :person         "hlö",
     :times-per-day  "kertaa päivässä",
     :times-per-week "kertaa viikossa"},
    :lipas.energy-consumption
    {:contains-other-buildings?
     "Energialukemat sisältävät myös muita  rakennuksia tai tiloja",
     :headline                 "Energian​kulutus",
     :yearly                   "Vuositasolla*",
     :report                   "Ilmoita lukemat",
     :electricity              "Sähköenergia MWh",
     :headline-year            "Lukemat vuonna {1}",
     :monthly?                 "Haluan ilmoittaa energiankulutuksen kuukausitasolla",
     :reported-for-year        "Vuoden {1} energiankulutus ilmoitettu",
     :monthly                  "Kuukausitasolla",
     :operating-hours          "Käyttötunnit",
     :not-reported             "Ei energiankulutustietoja",
     :not-reported-monthly     "Ei kuukausikulutustietoja",
     :heat                     "Lämpöenergia (ostettu) MWh",
     :cold                     "Kylmäenergia (ostettu) MWh",
     :comment                  "Kommentti",
     :water                    "Vesi m³",
     :monthly-readings-in-year "Kuukausikulutukset vuonna {1}"},
    :ice
    {:description
     "Jäähalliportaali sisältää  hallien perus- ja energiankulutustietoja sekä ohjeita  energiatehokkuuden parantamiseen.",
     :large                "Suurhalli > 3000 hlö",
     :competition          "Kilpahalli < 3000 hlö",
     :headline             "Jäähalli​portaali",
     :video                "Video",
     :comparison           "Hallien vertailu",
     :size-category        "Kokoluokitus",
     :basic-data-of-halls  "Jäähallien perustiedot",
     :updating-basic-data  "Perustietojen päivitys",
     :entering-energy-data "Energiankulutustietojen syöttäminen",
     :small                "Pieni kilpahalli > 500 hlö",
     :watch                "Katso",
     :video-description
     "Pihlajalinna Areena on energiatehokas jäähalli"},
    :lipas.location
    {:address       "Katuosoite",
     :no-address    "Kohteelle ei voi määrittää katuosoitetta"
     :city          "Kunta",
     :city-code     "Kuntanumero",
     :headline      "Sijainti",
     :neighborhood  "Kuntaosa",
     :postal-code   "Postinumero",
     :postal-office "Postitoimipaikka"
     :province      "Maakunta"
     :avi-area      "AVI-alue"},
    :lipas.user.permissions
    {:admin?       "Admin",
     :all-cities?  "Oikeus kaikkiin kuntiin",
     :all-types?   "Oikeus kaikkiin tyyppeihin",
     :cities       "Kunnat",
     :sports-sites "Liikunta- ja ulkoilupaikat",
     :types        "Tyypit"
     :activities   "Aktiviteetit"},
    :lipas.user.permissions.roles
    {:roles             "Roolit"
     :role              "Rooli"
     :context-value-all "Kaikki"
     :role-names        {:admin              "Admin"
                         :city-manager       "Kuntakäyttäjä"
                         :type-manager       "Tyyppikäyttäjä"
                         :site-manager       "Paikkakäyttäjä"
                         :activities-manager "UTP-käyttäjä"
                         :floorball-manager  "Salibandy muokkaaja"
                         :analysis-user      "Analyysityökalukäyttäjä"
                         :ptv-manager        "PTV-käyttäjä"}
     :context-keys      {:city-code "Kunta"
                         :type-code "Tyyppi"
                         :activity  "Aktiviteetti"
                         :lipas-id  "Paikka"}
     :edit-role         {:edit-header  "Muokkaa"
                         :new-header   "Lisää rooli"
                         :stop-editing "Lopeta muokkaus"
                         :add          "Lisää"
                         :choose-role  "Valitse rooli ensiksi, jotta voi valita mihin resursseihin rooli vaikuttaa."}
     :permissions-old   "(vanhat, vain luku)"}
    :help
    {:headline                 "Ohjeet",
     :available-pages          "Sivut tässä osiossa"
     :permissions-help
     "Jos haluat lisää käyttöoikeuksia, ota yhteyttä ",
     :permissions-help-body
     "Haluan käyttöoikeudet seuraaviin liikunta- ja ulkoilupaikkoihin:",
     :permissions-help-subject "Haluan lisää käyttöoikeuksia"
     :privacy-policy           "Tietosuojailmoitus"
     :manage-content           "Hallitse ohjesisältöä"},
    :ceiling-structures
    {:concrete         "Betoni",
     :double-t-beam    "TT-laatta",
     :glass            "Lasi",
     :hollow-core-slab "Ontelolaatta",
     :solid-rock       "Kallio",
     :steel            "Teräs",
     :wood             "Puu"},
    :data-users
    {:data-user?    "Käytätkö LIPAS-dataa?",
     :email-body
     "Mukavaa että hyödynnät LIPAS-dataa! Kirjoita tähän kuka olet ja miten hyödynnät Lipasta. Käytätkö mahdollisesti jotain rajapinnoistamme?",
     :email-subject "Mekin käytämme LIPAS-dataa",
     :headline      "Lipasta hyödyntävät",
     :tell-us       "Kerro siitä meille"},
    :lipas.swimming-pool.facilities
    {:showers-men-count              "Miesten suihkut lkm",
     :lockers-men-count              "Miesten pukukaapit lkm",
     :headline                       "Muut palvelut",
     :platforms-5m-count             "5 m hyppypaikkojen lkm",
     :kiosk?                         "Kioski / kahvio",
     :hydro-neck-massage-spots-count "Niskahierontapisteiden lkm",
     :lockers-unisex-count           "Unisex pukukaapit lkm",
     :platforms-10m-count            "10 m hyppypaikkojen lkm",
     :hydro-massage-spots-count      "Muiden hierontapisteiden lkm",
     :lockers-women-count            "Naisten pukukaapit lkm",
     :platforms-7.5m-count           "7.5 m hyppypaikkojen lkm",
     :gym?                           "Kuntosali",
     :showers-unisex-count           "Unisex suihkut lkm",
     :platforms-1m-count             "1 m hyppypaikkojen lkm",
     :showers-women-count            "Naisten suihkut lkm",
     :platforms-3m-count             "3 m hyppypaikkojen lkm"},
    :sauna-types
    {:infrared-sauna "Infrapunasauna",
     :other-sauna    "Muu sauna",
     :sauna          "Sauna",
     :steam-sauna    "Höyrysauna"},
    :stats-metrics
    {:investments          "Investoinnit",
     :net-costs            "Nettokustannukset",
     :operating-expenses   "Käyttökustannukset",
     :operating-incomes    "Käyttötuotot",
     :subsidies            "Kunnan myöntämät avustukset"
     :operational-expenses "Toimintakulut"
     :operational-income   "Toimintatuotot"
     :surplus              "Tilikauden ylijäämä"
     :deficit              "Tilikauden alijäämä"},
    :refrigerant-solutions
    {:CO2             "CO2",
     :H2ONH3          "H2O/NH3",
     :cacl            "CaCl",
     :ethanol-water   "Etanoli-vesi",
     :ethylene-glycol "Etyleeniglykoli",
     :freezium        "Freezium",
     :water-glycol    "Vesi-glykoli"},
    :user
    {:headline            "Oma sivu",
     :admin-page-link     "Siirry admin-sivulle",
     :promo1-link         "Näytä TEAviisari-kohteet jotka voin päivittää",
     :swimming-pools-link "Uimahallit",
     :promo-headline      "Ajankohtaista",
     :front-page-link     "Siirry etusivulle",
     :promo1-text         "Avaa PDF",
     :ice-stadiums-link   "Jäähallit",
     :greeting            "Hei {1} {2}!",
     :promo1-topic        "TIEDOTE LIPAS-TYYPPILUOKKAPÄIVITYKSISTÄ (11.1.2022)"
     :data-ownership      "Käyttöehdot"},
    :building-materials
    {:brick      "Tiili",
     :concrete   "Betoni",
     :solid-rock "Kallio",
     :steel      "Teräs",
     :wood       "Puu"},
    :stats
    {:disclaimer-headline  "Tietolähde"
     :general-disclaimer-1 "Lipas.fi – Suomen liikuntapaikat, ulkoilureitit ja virkistysalueet: Nimeä 4.0 Kansainvälinen (CC BY 4.0)."
     :general-disclaimer-2 "Voit vapaasti käyttää, kopioida, jakaa ja muokata aineistoa. Aineistolähteeseen tulee viitata esimerkiksi näin: Liikuntapaikat: Lipas.fi Jyväskylän yliopisto, otospäivä. Lisätietoja Creative Commons. https://creativecommons.org/licenses/by/4.0/deed.fi"
     :general-disclaimer-3 "Huomaa, että Lipas-tietokannan tiedot perustuvat kuntien ja muiden liikuntapaikkojen omistajien Lipas-tietokantaan syöttämiin tietoihin sekä Jyväskylän yliopiston Lipas-ylläpitäjien tuottamaan aineistoon. Tietojen kattavuutta, virheettömyyttä, ajantasaisuutta ja yhdenmukaisuutta ei voida taata. Lipas.fi –tilastot -osiossa tilastojen laskenta perustuu tietokantaan tallennettuihin tietoihin. Mahdolliset puuttuvat tiedot vaikuttavat laskelmiin."
     :finance-disclaimer   "Aineistolähde: Tilastokeskus avoimet tilastoaineistot ja Valtiokonttori, tutkihallintoa.fi/Kunnan tilinpäätöstiedot palveluluokkakohtaisesti (käyttötalous, investoinnit), 29.12.2022. Huomaa, että kunnat vastaavat itse virallisten taloustietojensa tallentamisesta Tilastokeskuksen tietokantaan Tilastokeskuksen ohjeiden perusteella. Tietojen yhdenmukaisuudesta tai aikasarjojen jatkuvuudesta ei voida antaa takeita."
     :description
     "Kuntien viralliset tilinpäätöstiedot liikunta- ja  nuorisotoimien osalta. Kunta voi seurata omaa menokehitystään ja  vertailla sitä muihin kuntiin.",
     :filter-types         "Rajaa tyypit",
     :length-km-sum        "Liikuntareittien pituus km yhteensä",
     :headline             "Tilastot",
     :select-years         "Valitse vuodet",
     :browse-to            "Siirry tilastoihin",
     :select-issuer        "Valitse myöntäjä",
     :select-unit          "Valitse yksikkö",
     :bullet3              "Avustukset",
     :bullet4              "Liikunta- ja ulkoilupaikkojen rakennusvuodet",
     :finance-stats        "Talous​tiedot",
     :select-city          "Valitse kunta",
     :area-m2-min          "Liikuntapinta-ala m² min",
     :filter-cities        "Rajaa kunnat",
     :select-metrics       "Valitse suureet",
     :area-m2-count        "Liikuntapinta-ala m² ilmoitettu lkm",
     :show-ranking         "Näytä ranking",
     :age-structure-stats  "Rakennus​vuodet",
     :subsidies-count      "Avustusten lkm",
     :area-m2-sum          "Liikuntapinta-ala m² yhteensä",
     :select-metric        "Valitse suure",
     :bullet2              "Liikuntapaikkatilastot",
     :area-m2-max          "Liikuntapinta-ala m² max",
     :select-grouping      "Ryhmittely",
     :select-city-service  "Valitse toimi",
     :region               "Alue",
     :show-comparison      "Näytä vertailu",
     :length-km-avg        "Liikuntareitin pituus km keskiarvo",
     :sports-sites-count   "Liikunta- ja ulkoilupaikkojen lkm",
     :length-km-min        "Liikuntareitin pituus km min",
     :country-avg          "(maan keskiarvo)",
     :length-km-count      "Liikuntareitin pituus ilmoitettu lkm",
     :population           "Asukasluku",
     :sports-stats         "Liikunta​paikat",
     :select-cities        "Valitse kunnat",
     :subsidies            "Avustukset",
     :select-interval      "Valitse aikaväli",
     :bullet1              "Liikunta- ja nuorisotoimen taloustiedot",
     :area-m2-avg          "Liikuntapinta-ala m² keskiarvo",
     :age-structure        "Liikunta​paikkojen rakennus​vuodet",
     :length-km-max        "Liikuntareitin pituus km max",
     :total-amount-1000e   "Yht. (1000 €)",
     :city-stats           "Kunta​tilastot"},
    :pool-structures
    {:concrete         "Betoni",
     :hardened-plastic "Lujitemuovi",
     :steel            "Teräs"},
    :map
    {:retkikartta-checkbox-reminder
     "Muista myös valita \"Saa julkaista Retkikartta.fi -palvelussa\" ruksi seuraavan vaiheen lisätiedoissa.\"",
     :zoom-to-user           "Kohdista sijaintiini",
     :remove                 "Poista",
     :modify-polygon         "Muokkaa aluetta",
     :draw-polygon           "Lisää alue",
     :retkikartta-problems-warning
     "Korjaa kartalle merkityt ongelmat, jos haluat, että kohde siirtyy Retkikartalle.",
     :edit-later-hint        "Voit muokata geometriaa myös myöhemmin",
     :center-map-to-site     "Kohdista kartta kohteeseen",
     :draw-hole              "Lisää reikä",
     :split-linestring       "Katkaise reittiosa",
     :travel-direction       "Määritä kulkusuunta"
     :route-part-difficulty  "Määritä reittiosan vaativuus"
     :delete-vertices-hint
     "Yksittäisiä pisteitä voi poistaa pitämällä alt-näppäintä pohjassa ja klikkaamalla pistettä.",
     :calculate-route-length "Laske reitin pituus automaattisesti",
     :calculate-area         "Laske alueen pinta-ala automaattisesti",
     :calculate-count        "Laske lukumäärä automaattisesti",
     :remove-polygon         "Poista alue",
     :modify-linestring      "Muokkaa reittiä",
     :download-gpx           "Lataa GPX",
     :add-to-map             "Lisää kartalle",
     :bounding-box-filter    "Hae kartan alueelta",
     :remove-linestring      "Poista reittiosa",
     :draw-geoms             "Piirrä",
     :confirm-remove         "Haluatko varmasti poistaa valitun geometrian?",
     :draw                   "Lisää kartalle",
     :draw-linestring        "Lisää reittiosa",
     :modify                 "Voit raahata pistettä kartalla",
     :zoom-to-site           "Kohdista kartta kohteeseen",
     :kink
     "Muuta reitin kulkua niin, että reittiosa ei risteä itsensä kanssa. Voit tarvittaessa katkaista reitin useampaan osaan.",
     :zoom-closer            "Kartta täytyy zoomata lähemmäs"},
    :supporting-structures
    {:brick           "Tiili",
     :concrete        "Betoni",
     :concrete-beam   "Betonipalkki",
     :concrete-pillar "Betonipilari",
     :solid-rock      "Kallio",
     :steel           "Teräs",
     :wood            "Puu"},
    :owner
    {:unknown                "Ei tietoa",
     :municipal-consortium   "Kuntayhtymä",
     :other                  "Muu",
     :company-ltd            "Yritys",
     :city                   "Kunta",
     :state                  "Valtio",
     :registered-association "Rekisteröity yhdistys",
     :foundation             "Säätiö",
     :city-main-owner        "Kuntaenemmistöinen yritys"},
    :menu
    {:frontpage "Etusivu",
     :headline  "LIPAS",
     :jyu       "Jyväskylän yliopisto",
     :main-menu "Päävalikko"},
    :dryer-types
    {:cooling-coil "Jäähdytyspatteri",
     :munters      "Muntters",
     :none         "Ei ilmankuivausta"},
    :physical-units
    {:mm            "mm",
     :hour          "h",
     :m3            "m³",
     :m             "m",
     :temperature-c "Lämpötila °C",
     :l             "l",
     :mwh           "MWh",
     :m2            "m²",
     :celsius       "°C"},
    :lipas.swimming-pool.water-treatment
    {:activated-carbon? "Aktiivihiili",
     :filtering-methods "Suodatustapa",
     :headline          "Vedenkäsittely",
     :ozonation?        "Otsonointi",
     :uv-treatment?     "UV-käsittely"},
    :statuses {:edited "{1} (muokattu)"},
    :lipas.user
    {:email                     "Sähköposti",
     :permissions               "Käyttöoikeudet",
     :permissions-example
     "Oikeus päivittää Jyväskylän jäähallien tietoja.",
     :saved-searches            "Tallennetut haut",
     :report-energy-and-visitors
     "Ilmoita energia- ja kävijämäärätiedot",
     :permission-to-cities
     "Sinulla on käyttöoikeus seuraaviin kuntiin:",
     :password                  "Salasana",
     :lastname                  "Sukunimi",
     :save-report               "Tallenna raporttipohja",
     :sports-sites              "Omat kohteet",
     :permission-to-all-cities
     "Sinulla on käyttöoikeus kaikkiin kuntiin",
     :username                  "Käyttäjätunnus",
     :history                   "Historia",
     :saved-reports             "Tallennetut raporttipohjat",
     :contact-info              "Yhteystiedot",
     :permission-to-all-types
     "Sinulla on käyttöoikeus kaikkiin  liikuntapaikkatyyppeihin",
     :requested-permissions     "Pyydetyt oikeudet",
     :email-example             "kalle.kayttaja@kunta.fi",
     :permission-to-portal-sites
     "Sinulla on käyttöoikeus seuraaviin  yksittäisiin liikuntapaikkoihin:",
     :permissions-help
     "Kerro, mitä tietoja haluat päivittää Lipaksessa",
     :permission-to-activities  "Sinulla on käyttöoikeus seuraaviin aktiviteetteihin"
     :report-energy-consumption "Ilmoita energiankulutus",
     :firstname                 "Etunimi",
     :save-search               "Tallenna haku",
     :view-basic-info           "Tarkista perustiedot",
     :no-permissions            "Sinulle ei ole vielä myönnetty käyttöoikeuksia.",
     :username-example          "tane12",
     :permission-to-types
     "Sinulla on käyttöoikeus seuraaviin  liikuntapaikkatyyppeihin:"},
    :heat-recovery-types
    {:liquid-circulation   "Nestekierto",
     :plate-heat-exchanger "Levysiirrin",
     :thermal-wheel        "Pyörivä"}},
   :se
   {:loi
    {:category                     "Kategori"
     :status                       "Status"
     :type                         "Objekttyp"
     :type-and-category-disclaimer "Kategori och typ måste väljas innan de läggs till på kartan"}
    :utp
    {:read-only-disclaimer       "Det finns för närvarande endast en redigeringsvy för aktiviteter. Logga in och gå till redigeringsläget från pennsymbolen."
     :add-contact                "Lägg till kontaktuppgift"
     :unit                       "enhet"
     :highlight                  "Höjdpunkt"
     :add-highlight              "Lägg till höjdpunkt"
     :photo                      "Foto"
     :add-photo                  "Lägg till foto"
     :video                      "Video"
     :add-video                  "Lägg till video"
     :link                       "Länk"
     :length-km                  "Längd km"
     :add-subroute               "Lägg till delrutt"
     :delete-route-prompt        "Är du säker på att du vill ta bort den här rutten?"
     :custom-rule                "Egna tillstånd, regler, anvisningar"
     :custom-rules               "Egen regler"
     :add-subroute-ok            "OK"
     :route-is-made-of-subroutes "Rutten består av flera separata sektioner"
     :select-route-parts-on-map  "Välj ruttdelar på kartan"
     :finish-route-details       "Rutt färdig"}

    :analysis
    {:headline                   "Analysverktyg (beta)"
     :description                "Med analysverktyget kan du utvärdera utbudet och tillgängligheten av motionsförhållanden genom att jämföra avståndet och restiderna till motionsplatser i förhållande till andra motionsplatser, befolkningen och läroanstalter."
     :results                    "Resultat"
     :mean                       "Medelvärde"
     :population-weighted-mean   "Befolkningsvägd mångfaldighetsindex"
     :median                     "Median"
     :mode                       "Typvärde"
     :reachability               "Tillgänglighet"
     :categories                 "Kategorier"
     :diversity                  "Mångfald"
     :diversity-grid             "Resultatgrid"
     :diversity-help1            "Med mångfaldsverktyget kan du utvärdera och jämföra mångfalden av motionsförhållanden i invånarnas närmiljö på rutnäts- och områdesnivå. Den mångfaldighetsindex som beräknas av mångfaldsverktyget beskriver hur mångfaldigt olika motionsaktivitetsmöjligheter invånaren kan nå inom den valda avståndet längs väg- och stignätet (förutsatt 800 m). Ju högre indexvärde, desto fler olika motionsplatser finns i invånarnas närmiljö."
     :diversity-help2            "Verktyget använder indelning i postnummerområden som standard. Indelningen kan också göras utifrån annan befintlig områdesindelning genom att importera önskad geometrisk fil (Shapefile, GeoJSON eller KML)."
     :diversity-help3            "Beräkningen görs per 250 x 250 m befolkningsrutor från Statistikcentralen. Områdesresultaten visar den genomsnittliga mångfalden av motionsförhållanden i invånarnas närmiljö (befolkningsvägd medeltal av mångfaldighetsindex). Beräkningsavståndet för mångfaldsverktyget baseras på vägnätsdata från OpenStreetMap och OSRM-verktyget."
     :analysis-areas             "Analysområden"
     :categories-help            "Motionsplatskategorier som tillhör samma kategori påverkar mångfaldighetsindexet endast en gång."
     :description2               "Befolkningsdata används från Statistikcentralens 250x250m och 1x1km rutnätsdata, som visar befolkningsfördelningen i tre åldersgrupper (0-14, 15-65, 65-) i varje ruta."
     :description3               "Beräkningen av restider med olika färdsätt (till fots, cykel, bil) baseras på öppna OpenStreetMap-data och OSRM-verktyget."
     :description4               "Uppgifter om läroanstalters namn och plats baseras på öppna data från Statistikcentralen. Uppgifter om förskoleenheter baseras på data som samlats in och tillhandahållits av LIKES."
     :add-new                    "Lägg till ny"
     :distance                   "Avstånd"
     :travel-time                "Restid"
     :zones                      "Zoner"
     :zone                       "Zon"
     :schools                    "Skolor"
     :daycare                    "Förskoleenhet"
     :elementary-school          "Grundskola"
     :high-school                "Gymnasium"
     :elementary-and-high-school "Grund- och gymnasieskola"
     :special-school             "Specialskola"
     :population                 "Befolkning"
     :direct                     "Fågelvägen"
     :by-foot                    "Till fots"
     :by-car                     "Med bil"
     :by-bicycle                 "Med cykel"
     :analysis-buffer            "Analysområde"
     :filter-types               "Filtrera efter typ"
     :settings                   "Inställningar"
     :settings-map               "Objekt som visas på kartan"
     :settings-zones             "Avstånd och restider"
     :settings-help              "Analysområdet bestäms av den största avståndzonen. Restider beräknas heller inte utanför detta område."}

    :sport
    {:description     "LIPAS är en landsomfattande databas för offentliga finländska idrottsplatser.",
     :headline        "Idrottsanläggningar",
     :open-interfaces "Öppna gränssnitt",
     :up-to-date-information
     "Aktuell data om finska idrottsanläggningar",
     :updating-tools  "Uppdateringsverktyg"
     :analysis-tools  "Liikuntaolosuhteiden analysointityökalut"}
    :lipas.ice-stadium.rinks
    {:rink1-width   "1. planens bredd m"
     :rink2-width   "2. planens bredd m"
     :rink3-width   "3. planens bredd m"
     :rink1-length  "1. planens längd m"
     :rink2-length  "2. planens längd m"
     :rink3-length  "3. planens längd m"
     :rink1-area-m2 "1. planens areal m²"
     :rink2-area-m2 "2. planens areal m²"
     :rink3-area-m2 "3. planens areal m²"
     :add-rink      "Lisää kenttä",
     :edit-rink     "Muokkaa kenttää",
     :headline      "Kentät"},,
    :confirm
    {:discard-changes? "Vill du förkasta ändringar?",
     :headline         "Bekräftelse",
     :no               "Nej",
     :delete-confirm   "Vill du verkligen ta bort raden?"
     :resurrect?       "Vill du spara grunddata?",
     :save-basic-data? "Vill du spara grunddata?",
     :yes              "Ja"},
    :lipas.swimming-pool.saunas
    {:accessible? "Tillgängligt",
     :add-sauna   "Lägg till en bastu",
     :edit-sauna  "Redigera bastun",
     :headline    "Bastur",
     :men?        "Män",
     :women?      "Kvinnor"},
    :slide-structures
    {:concrete         "Betong",
     :hardened-plastic "Öppna gränssnitt",
     :steel            "Stål"},
    :lipas.swimming-pool.conditions
    {:open-days-in-year "Fredagar",
     :open-hours-mon    "Måndagar",
     :headline          "Öppettider",
     :open-hours-wed    "Onsdagar",
     :open-hours-thu    "Torsdagar",
     :open-hours-fri    "Fredagar",
     :open-hours-tue    "Tisdagar",
     :open-hours-sun    "Söndagar",
     :daily-open-hours  "Dagliga öppettider",
     :open-hours-sat    "Lördagar"},
    :lipas.ice-stadium.ventilation
    {:dryer-duty-type          "Gatuadress",
     :dryer-type               "Kommun",
     :heat-pump-type           "Gatuadress",
     :heat-recovery-efficiency "Kommun",
     :heat-recovery-type       "Gatuadress"},
    :swim
    {:basic-data-of-halls  "Simhallsportal",
     :edit                 "Simhallsportal",
     :entering-energy-data "Datum",
     :headline             "Simhallsportal",
     :latest-updates       "Timme",
     :list                 "Energi information",
     :updating-basic-data  "Information",
     :visualizations       "Energi information"},
    :home-page
    {:description
     "LIPAS-system innehåller information av idrottsanläggningar, idrottsrutter och friluftsregioner. Data är öppen CC4.0 International.",
     :headline "Startsida"},
    :ice-energy         {:finhockey-link "El", :headline "Bensin"},
    :filtering-methods
    {:activated-carbon    "Kol",
     :coal                "Kol",
     :membrane-filtration "Annat",
     :open-sand           "Annat",
     :other               "Annat",
     :precipitation       "Beskrivning",
     :pressure-sand       "Kommentar"},
    :open-data
    {:description "Länkar och information om gränssnitten",
     :headline    "Öppna data",
     :rest        "REST",
     :wms-wfs     "WMS & WFS"},
    :lipas.swimming-pool.pool
    {:accessibility "Tillgänglighet",
     :outdoor-pool? "Utomhus simbassäng"},
    :pool-types
    {:multipurpose-pool "Allaktivitetsbassäng",
     :whirlpool-bath    "Bubbelbad",
     :main-pool         "Huvudsbassäng",
     :therapy-pool      "Terapibassäng",
     :fitness-pool      "Konditionsbassäng",
     :diving-pool       "Hoppbassäng",
     :childrens-pool    "Barnbassäng",
     :paddling-pool     "Plaskdamm",
     :cold-pool         "Kallbassäng",
     :other-pool        "Annan bassäng",
     :teaching-pool     "Undervisningsbassäng"},
    :disclaimer
    {:headline       "OBS!", :test-version "Automatisk"
     :data-ownership "LIPAS-geografiska informationssystemets datalager ägs och förvaltas av Jyväskylä universitet. Jyväskylä universitet förbehåller sig rätten till allt innehåll, information och material som lagts till i systemet och som skapats under systemets utveckling. Användaren som lägger till material i systemet ansvarar för att materialet är korrekt och inte kränker tredje parts upphovsrätt. Jyväskylä universitet ansvarar inte för krav från tredje part avseende materialet. Genom att lägga till data i LIPAS-geografiska informationssystemet anses användaren ha godkänt användningen av det tillagda materialet i systemet som en del av datalagret. De data som lagras i LIPAS-databasen är öppna och fritt tillgängliga enligt CC 4.0 Erkännande-licensen."},
    :admin
    {:private-foundation      "Privat / stiftelse",
     :city-other              "Kommun / annat",
     :unknown                 "Okänt",
     :municipal-consortium    "Samkommun",
     :private-company         "Privat / företag",
     :private-association     "Privat / förening",
     :other                   "Annat",
     :city-technical-services "Kommun / teknisk väsende",
     :city-sports             "Kommun/ idrottsväsende",
     :state                   "Stat",
     :city-education          "Kommun / utbildingsväsende"},
    :accessibility      {:lift "Lyft i bassängen", :slope "Ramp"},
    :general
    {:headline      "Rubrik"
     :description   "Beskrivning",
     :hall          "Hall",
     :women         "Kvinnor",
     :total-short   "Totalt",
     :done          "Färdig",
     :updated       "Uppdaterad",
     :name          "Namn",
     :reported      "Rapporterad",
     :type          "Typ",
     :last-modified "Senaste redigerad",
     :here          "här",
     :event         "Händelse",
     :structure     "Struktur",
     :general-info  "Allmänna uppgifter",
     :comment       "Kommentar",
     :measures      "Mått",
     :men           "Män"},
    :dryer-duty-types   {:automatic "Automatisk", :manual "Manual"},
    :swim-energy
    {:description    "Information",
     :headline       "Energi information",
     :headline-short "Information",
     :suh-link       "Datum",
     :ukty-link      "Slutade"},
    :time
    {:two-years-ago      "För 2 år sedan",
     :date               "Datum",
     :hour               "Timme",
     :this-month         "Den här månaden",
     :time               "Tid",
     :less-than-hour-ago "För mindre än en timme sedan",
     :start              "Började",
     :this-week          "Den här veckan",
     :today              "I dag",
     :month              "Månad",
     :long-time-ago      "För länge sedan",
     :year               "År",
     :just-a-moment-ago  "För en stund sedan",
     :yesterday          "I går",
     :three-years-ago    "För 3 år sedan",
     :end                "Slutade",
     :this-year          "Det här året",
     :last-year          "Förra året"},
    :ice-resurfacer-fuels
    {:LPG         "El",
     :electicity  "El",
     :gasoline    "Bensin",
     :natural-gas "Naturgas",
     :propane     "Propan"},
    :ice-rinks          {:headline "Du har admin rättigheter."},
    :month
    {:sep "September",
     :jan "Januari",
     :jun "Juni",
     :oct "Oktober",
     :jul "Juli",
     :apr "April",
     :feb "Februari",
     :nov "November",
     :may "Maj",
     :mar "Mars",
     :dec "December",
     :aug "Augusti"},
    :type
    {:name          "Idrottsanläggningstyp",
     :main-category "Huvudkategori",
     :sub-category  "Underkategori",
     :type-code     "Typkod"},
    :duration
    {:hour "timmar", :month "månader", :years "år", :years-short "år"},
    :size-categories    {:large "Betong"},
    :lipas.admin
    {:access-all-sites           "Du har admin rättigheter.",
     :confirm-magic-link
     "Är du säker på att du vill skicka magic link till {1}?",
     :headline                   "Admin",
     :magic-link                 "Magic link",
     :select-magic-link-template "Välj brev",
     :send-magic-link            "Skicka magic link till användare",
     :users                      "Användare"},
    :lipas.building
    {:headline                "Byggnad",
     :total-volume-m3         "Areal av vatten m²",
     :staff-count             "Antalet personal",
     :heat-sections?          "Antalet personal",
     :total-ice-area-m2       "Areal av is m²",
     :main-designers          "Antalet personal",
     :total-pool-room-area-m2 "Areal av vatten m²",
     :total-water-area-m2     "Areal av vatten m²",
     :ceiling-structures      "Byggnad",
     :supporting-structures   "Areal av is m²",
     :seating-capacity        "Antalet personal"},
    :search
    {:table-view            "Visa resultaten i tabellen",
     :headline              "Sökning",
     :results-count         "{1} resultat",
     :placeholder           "Sök...",
     :retkikartta-filter    "Retkikartta.fi-platser",
     :filters               "Filtrera sökning",
     :search-more           "Sök mer...",
     :page-size             "Visa",
     :search                "Sök",
     ;; TODO: "Näytä vain..."
     :permissions-filter    "Visa platser som jag kan redigera",
     :display-closest-first "Visa närmaste platser först",
     :list-view             "Visa resultaten i listan",
     :pagination            "Resultaten {1}-{2}",
     :school-use-filter     "Idrottsanläggningar nära skolor",
     :clear-filters         "Avmarkera filter"},
    :map.tools
    {:drawing-tooltip               "Ritverktyg valt",
     :drawing-hole-tooltip          "Hålteckningsverktyg valt",
     :edit-tool                     "Redigeringsverktyg",
     :importing-tooltip             "Importverktyg valt",
     :deleting-tooltip              "Raderingsverktyg valt",
     :splitting-tooltip             "Delningsverktyg valt",
     :simplifying                   "Förenklingsverktyg valt",
     :selecting                     "Valverktyg valt",
     :simplify                      "Förenkla",
     :travel-direction-tooltip      "Reseriktningverktyg valt"
     :route-part-difficulty-tooltip "Verktyg för val av svårighetsgrad för ruttsektion valt"}
    :partners           {:headline "Tillsammans med"},
    :actions
    {:duplicate                "Kopiera",
     :resurrect                "Öppna huvudmeny",
     :select-year              "Välj år",
     :select-owners            "Välj ägare",
     :select-admins            "Välj administratörer",
     :select-tool              "Välj verktyg",
     :save-draft               "Spara utkast",
     :redo                     "Gör igen",
     :open-main-menu           "Öppna huvudmeny",
     :back-to-listing          "Till listan",
     :filter-surface-materials "Avgränsa enligt ytmaterial",
     :browse                   "flytta dig",
     :select-type              "Välj typ",
     :edit                     "Redigera",
     :filter-construction-year "Avgränsa enligt byggnadsår",
     :submit                   "Skicka",
     :choose-energy            "Välj energi",
     :delete                   "Radera",
     :browse-to-map            "Flytta dig till kartan",
     :save                     "Spara",
     :close                    "Stäng",
     :filter-area-m2           "Avgränsa enligt areal m²",
     :show-account-menu        "Öppna kontomeny",
     :fill-required-fields     "Fyll i obligatoriska fält",
     :undo                     "Ångra",
     :browse-to-portal         "Flytta dig till portalen",
     :download-excel           "Ladda",
     :fill-data                "Fyll i informationen",
     :select-statuses          "Status",
     :select-cities            "Välj kommuner",
     :select-hint              "Välj...",
     :discard                  "Förkasta",
     :more                     "Mer...",
     :cancel                   "Avbryta",
     :select-columns           "Välj datafält",
     :add                      "Tillägg ",
     :show-all-years           "Visa alla år",
     :download                 "Ladda ner",
     :select-hall              "Välj hall",
     :clear-selections         "Ta bort val",
     :select                   "Välj",
     :select-types             "Välj typer"},
    :dimensions
    {:area-m2         "Areal m²",
     :depth-max-m     "Djup max m",
     :depth-min-m     "Djup min m",
     :length-m        "Längd m",
     :surface-area-m2 "Areal m²",
     :volume-m3       "Volym m3",
     :width-m         "Bredd m"},
    :login
    {:headline              "Logga in",
     :login-here            "här",
     :login-with-password   "Logga in med lösenord",
     :password              "Lösenord",
     :logout                "Logga ut",
     :username              "E-post / användarnamn",
     :login-help
     "Om du har ett användarnamn till LIPAS, du kan också logga in med e-post",
     :login                 "Logga in",
     :magic-link-help
     "Om du har ett användarnamn till LIPAS, du kan beställa en logga in-länk till din e-post.",
     :order-magic-link      "Skicka länken till min e-post",
     :login-with-magic-link "Logga in med e-post",
     :bad-credentials       "Lösenord eller användarnamn är felaktigt",
     :magic-link-ordered
     "Länken har skickats och den är snart i din e-post. Kolla också spam!",
     :username-example      "paavo.paivittaja@kunta.fi",
     :forgot-password?      "Glömt ditt lösenord?"},
    :map.demographics
    {:headline   "Befolkning",
     :tooltip    "Visa befolkning",
     :helper-text
     "Välj idrottsanläggning på kartan.",
     :copyright1
     "Befolkningsinformation: Statistikcentralen 2019 årets",
     :copyright2 "1 km * 1 km data",
     :copyright3 "licens"},
    :lipas.swimming-pool.slides
    {:add-slide  "Lägg till en rutschbana",
     :edit-slide "Redigera rutschbanan",
     :headline   "Rutschbanor"},
    :notifications
    {:get-failed   "Datasökning misslyckades.",
     :save-failed  "Sparningen misslyckades",
     :save-success "Sparningen lyckades"
     :ie           (str "Internet Explorer stöds inte. Vi rekommenderar du använder t.ex. Chrome, Firefox eller Edge.")},
    :lipas.swimming-pool.energy-saving
    {:filter-rinse-water-heat-recovery? "Gym",
     :headline                          "Andra tjänster",
     :shower-water-heat-recovery?       "Gym"},
    :ice-form
    {:headline       "Naturgas",
     :headline-short "El",
     :select-rink    "Bensin"},
    :restricted         {:login-or-register "Logga in eller registrera dig"},
    :sports-site.elevation-profile
    {:headline                "Höjdprofil",
     :distance-from-start-m   "Avstånd från start (m)",
     :distance-from-start-km  "Avstånd från start (km)",
     :height-from-sea-level-m "Höjd över havet (m)",
     :total-ascend            "Total stigning",
     :total-descend           "Total nedstigning"}
    :lipas.sports-site
    {:properties             "Ytterligare information",
     :delete-tooltip         "Ta bort idrottsanläggningen...",
     :headline               "Idrottsanläggning",
     :new-site-of-type       "Ny {1}",
     :address                "Adress",
     :new-site               "Ny idrottsplats",
     :phone-number           "Telefonnummer",
     :admin                  "Administratör",
     :surface-materials      "Ytmaterial",
     :www                    "Webbsida",
     :name                   "Namn på finska",
     :reservations-link      "Bokningar",
     :construction-year      "Byggår",
     :type                   "Typ",
     :delete                 "Ta bort {1}",
     :renovation-years       "Renoveringsår",
     :name-localized-se      "Namn på svenska",
     :name-localized-en      "Namn på engelska"
     :status                 "Status",
     :id                     "LIPAS-ID",
     :details-in-portal      "Visa alla ytterligare information",
     :comment                "Ytterligare information",
     :ownership              "Ägare",
     :name-short             "Namn",
     :basic-data             "Grunddata",
     :delete-reason          "Orsak",
     :event-date             "Redigerad",
     :email-public           "E-post (publik)",
     :add-new                "Lägg till en idrottsanläggning",
     :contact                "Kontaktinformation",
     :owner                  "Ägare",
     :marketing-name         "Varumärkesnamn"
     :no-permission-tab      "Du har inte behörighet att redigera informationen på denna flik"
     :add-new-planning       "Lägg till en motions- eller friluftsplats i utkastläge"
     :planning-site          "Utkast"
     :creating-planning-site "Du håller på att lägga till en plats i utkastläge för analysverktyg."},
    :status
    {:active                     "Aktiv",
     :planned                    "Planerad"
     :incorrect-data             "Fel information",
     :out-of-service-permanently "Permanent ur funktion",
     :out-of-service-temporarily "Tillfälligt ur funktion"},
    :register
    {:headline "Registrera",
     :link     "Registrera här"
     :thank-you-for-registering
     "Tack för registrering! Du vill få e-posten snart."},
    :map.address-search {:title "Sök address", :tooltip "Sök address"},
    :ice-comparison     {:headline "Jämförelse av hallar"},
    :lipas.visitors
    {:headline             "Användare",
     :not-reported         "Glömt lösenordet?",
     :not-reported-monthly "Lösenord eller användarnamn är felaktigt",
     :spectators-count     "Glömt ditt lösenord?",
     :total-count          "Lösenord eller användarnamn är felaktigt"},
    :lipas.energy-stats
    {:headline            "Värme MWh",
     :energy-reported-for "Värme MWh",
     :disclaimer          "El MWh",
     :reported            "Vatten m³",
     :cold-mwh            "El + värme MWh",
     :hall-missing?       "Vatten m³",
     :not-reported        "Vatten m³",
     :water-m3            "Vatten m³",
     :electricity-mwh     "El MWh",
     :heat-mwh            "Värme MWh",
     :energy-mwh          "El + värme MWh"},
    :map.basemap
    {:copyright    "© Lantmäteriverket",
     :maastokartta "Terrängkarta",
     :ortokuva     "Flygfoto",
     :taustakartta "Bakgrundskarta"
     :transparency "Bakgrundskartans genomskinlighet"},
    :lipas.swimming-pool.pools
    {:add-pool  "Lägg till en bassäng",
     :edit-pool "Redigera bassängen",
     :headline  "Bassänger",
     :structure "Struktur"},
    :condensate-energy-targets
    {:service-water-heating "Nej",
     :snow-melting          "Vill du förkasta ändringar?",
     :track-heating         "Vill du förkasta ändringar?"},
    :refrigerants
    {:CO2   "CO2 (koldioxid)",
     :R134A "R134A",
     :R22   "R22",
     :R404A "R404A",
     :R407A "R407A",
     :R407C "R407C",
     :R717  "R717"},
    :harrastuspassi     {:disclaimer "När du kryssat för rutan ”Kan visas på Harrastuspassi.fi” flyttas uppgifterna om idrottsanläggningen automatiskt till Harrastuspassi.fi. Jag förbinder mig att uppdatera ändringar i uppgifterna om idrottsanläggningen utan dröjsmål i Lipas. Anläggningens administratör ansvarar för uppgifternas riktighet och anläggningens säkerhet. Uppgifterna visas i Harrastuspassi.fi om kommunen har kontrakt med Harrastuspassi.fi administratör för att använda Harrastuspassi.fi."}
    :retkikartta        {:disclaimer "När du kryssat för rutan ”Kan visas på Utflyktskarta.fi” flyttas uppgifterna om naturutflyktsmålet automatiskt till karttjänsten Utflyktskarta.fi som Forststyrelsen administrerar.
Uppgifter överförs till karttjänsten en gång i dygnet. Jag förbinder mig att uppdatera ändringar i uppgifterna om naturutflyktsmålet utan dröjsmål i Lipas.
Utflyktsmålets administratör ansvarar för uppgifternas riktighet och utflyktsmålets säkerhet samt för svar på respons och för eventuella kostnader i samband med privata vägar."},
    :reset-password
    {:change-password    "Ändra lösenord",
     :enter-new-password "Skriv ett ny lösenord",
     :get-new-link       "Bestella ny återställningslänk",
     :headline           "Glömt lösenordet?",
     :helper-text
     "Vi ska skicka en återställningslänk till din e-post.",
     :password-helper-text
     "Lösenordet måste innehålla minst 6 tecken.",
     :reset-link-sent    "Länken har skickats! Kolla din e-post.",
     :reset-success
     "Lösenordet har ändrats! Logga in med ditt nya lösenord."},
    :reports
    {:contacts          "Kontaktuppgifter",
     :download-as-excel "Skapa Excel",
     :select-fields     "Välj fält för rapport",
     :selected-fields   "Vald fält",
     :shortcuts         "Genvägar",
     :tooltip           "Skapa Excel-rapport från resultaten"},
    :heat-sources
    {:district-heating
     "Om du behöver ytterligare användarrättigheter, kontakt ",
     :private-power-station "Hjälp"},
    :map.import
    {:headline          "Importera geometri",
     :geoJSON
     "Laddar upp .json file som innehåller GeoJSON FeatureCollect object. Koordinater måste vara i WGS84 koordinatsystem.",
     :gpx               "Utgångsmaterial måste vara i WGS84 koordinatsystem.",
     :supported-formats "Filformat som passar är {1}",
     :replace-existing? "Ersätt gammal geometri",
     :select-encoding   "Välj teckenuppsättning",
     :tab-header        "Importera från filen",
     :kml               "Utgångsmaterial måste vara i WGS84 koordinatsystem.",
     :shapefile
     "Importera .shp .dbf och .prj filer i packade .zip filen.",
     :import-selected   "Importera valda",
     :tooltip           "Importera geometrier från filen",
     :unknown-format    "Okänt filformat '{1}'"
     :unknown-error     "Ett oväntat fel inträffade. Försök med en annan fil."
     :no-geoms-of-type  "Den angivna filen innehåller inga {1} geometrier."

     :coords-not-in-finland-wgs84-bounds "Den angivna filen innehåller inte koordinater i WGS84 (EPSG:4326) koordinatsystemet. Kontrollera att ursprungsmaterialet är i rätt koordinatsystem."},
    :error
    {:email-conflict    "E-post är redan registrerad",
     :email-not-found   "E-post är inte registrerad",
     :invalid-form      "Korrigera röda fält",
     :no-data           "Ingen information",
     :reset-token-expired
     "Lösenordet har inte ändrats. Länken har utgått.",
     :unknown           "Ett okänt fel uppstod. :/",
     :user-not-found    "Användare hittas inte.",
     :username-conflict "Användarnamnet redan används"},
    :reminders
    {:description
     "Meddelandet ska skickas till din e-post på vald tid.",
     :after-one-month  "Om en månad",
     :placeholder      "Kom ihåg att kolla idrottsanläggningen \"{1}\" {2}",
     :select-date      "Välj datum",
     :tomorrow         "I morgon",
     :title            "Lägg till en påminnelse",
     :after-six-months "Om halv år",
     :in-a-year        "Om ett år",
     :message          "Meddelande",
     :in-a-week        "Om en vecka"},
    :units
    {:days-in-year   "dagar per år",
     :hours-per-day  "timmar per dag",
     :pcs            "st",
     :percent        "%",
     :person         "pers.",
     :times-per-day  "gånger per dag",
     :times-per-week "gånger per vecka"},
    :lipas.energy-consumption
    {:cold              "Kommentar",
     :comment           "Kommentar",
     :monthly?          "Vatten m³",
     :operating-hours   "Vatten m³",
     :report            "El MWh",
     :reported-for-year "Vatten m³",
     :water             "Vatten m³",
     :yearly            "El MWh"},
    :ice
    {:description          "Stor tävlingshall > 3000",
     :large                "Stor tävlingshall > 3000",
     :competition          "Tävlingsishall < 3000 person",
     :headline             "Ishallsportal",
     :video                "Video",
     :comparison           "Jämförelse av hallar",
     :size-category        "Storlek kategori",
     :basic-data-of-halls  "Grunddata av ishallar",
     :updating-basic-data  "Uppdatering av grunddata",
     :entering-energy-data "Ishallsportal",
     :small                "Liten tävlingshall > 500 person",
     :watch                "Titta",
     :video-description    "Titta"},
    :lipas.location
    {:address       "Gatuadress",
     :city          "Kommun",
     :city-code     "Kommunkod",
     :headline      "Position",
     :neighborhood  "Kommundel",
     :postal-code   "Postnummer",
     :postal-office "Postkontor"},
    :lipas.user.permissions
    {:admin?       "Admin",
     :all-cities?  "Rättighet till alla kommuner",
     :all-types?   "Rättighet till alla typer",
     :cities       "Kommuner",
     :sports-sites "Idrottsanläggning",
     :types        "Typer"
     :activities   "Aktiviteter"},
    :lipas.user.permissions.roles
    {:roles             "Roller"
     :role              "Roll"
     :context-value-all "Alla"
     :role-names        {:admin              "Admin"
                         :city-manager       "Stadsadministratör"
                         :type-manager       "Typadministratör"
                         :site-manager       "Platsadministratör"
                         :activities-manager "UTP-administratör"
                         :floorball-manager  "Innebandyredigerare"
                         :analysis-user      "Analysverktygsanvändare"
                         :ptv-user           "PTV-administratör"}
     :context-keys      {:city-code "Kommun"
                         :type-code "Typ"
                         :activity  "Aktivitet"
                         :lipas-id  "Plats"}
     :edit-role         {:edit-header  "Redigera"
                         :new-header   "Lägg till roll"
                         :stop-editing "Avsluta redigering"
                         :add          "Lägg till"
                         :choose-role  "Välj roll först för att välja vilka resurser rollen påverkar."}
     :permissions-old   "(gamla, endast läs)"}
    :help
    {:headline                 "Hjälp",
     :available-pages          "Sidor i detta avsnitt"
     :permissions-help
     "Om du behöver ytterligare användarrättigheter, kontakt ",
     :permissions-help-body
     "Jag behöver användarrättigheter till följande platser:",
     :permissions-help-subject "Jag behöver mera användarrättigheter"
     :privacy-policy           "Privacy policy"
     :manage-content           "Redigera hjälpinnehåll"},
    :ceiling-structures
    {:concrete         "Betong",
     :double-t-beam    "TT-bricka",
     :glass            "Glas",
     :hollow-core-slab "Häll",
     :solid-rock       "Häll",
     :steel            "Stål",
     :wood             "Trä"},
    :data-users
    {:data-user?    "Använder du LIPAS-data?",
     :email-body    "Vi använder också LIPAS-data",
     :email-subject "Vi använder också LIPAS-data",
     :headline      "Några som använder LIPAS-data",
     :tell-us       "Berättä om det för oss"},
    :lipas.swimming-pool.facilities
    {:showers-men-count              "Antalet duschar för män",
     :lockers-men-count              "Antalet omklädningsskåp för män",
     :headline                       "Andra tjänster",
     :platforms-5m-count             "Antalet 5 m hopplatser",
     :kiosk?                         "Kiosk / café",
     :hydro-neck-massage-spots-count "Antal av nackemassagepunkter",
     :lockers-unisex-count           "Antalet unisex omklädningsskåp",
     :platforms-10m-count            "Antalet 10 m hopplatser",
     :hydro-massage-spots-count      "Antal av andra massagepunkter",
     :lockers-women-count            "Antalet omklädningsskåp för kvinnor",
     :platforms-7.5m-count           "Antalet 7.5 m hopplatser",
     :gym?                           "Gym",
     :showers-unisex-count           "Antalet unisex duschar",
     :platforms-1m-count             "Antalet 1 m hopplatser",
     :showers-women-count            "Antalet duschar för kvinnor",
     :platforms-3m-count             "Antalet 3 m hopplatser"},
    :sauna-types
    {:infrared-sauna "Infraröd bastu",
     :other-sauna    "Annan bastu",
     :sauna          "Bastu",
     :steam-sauna    "Ångbastu"},
    :stats-metrics
    {:investments        "Investeringar",
     :net-costs          "Nettokostnader",
     :operating-expenses "Driftskostnader",
     :operating-incomes  "Driftsintäkter",
     :subsidies          "Understöd och bidrag från kommunen"},
    :refrigerant-solutions
    {:CO2             "CO2",
     :H2ONH3          "H2O/NH3",
     :cacl            "CaCl",
     :ethanol-water   "Freezium",
     :ethylene-glycol "R134A",
     :freezium        "Freezium",
     :water-glycol    "R134A"},
    :user
    {:headline            "Mitt konto",
     :admin-page-link     "Gå till adminsidan",
     :promo1-link         "Visa gymnastiksaler som jag kan uppdatera",
     :swimming-pools-link "Simhallsportal",
     :promo-headline      "Aktuellt",
     :front-page-link     "Gå till framsidan",
     :promo1-text         "Öppna PDF",
     :ice-stadiums-link   "Ishallsportal",
     :greeting            "Hej {1} {2} !",
     :promo1-topic        "OBS! EN UPPDATERING I KLASSIFICERING AV IDROTTS- OCH FRILUFTSPLATSER I LIPAS-SYSTEMET (11 januari 2022)"
     :data-ownership      "Användarvillkor och ansvar"},
    :building-materials
    {:brick      "Tegel",
     :concrete   "Betong",
     :solid-rock "Häll",
     :steel      "Stål",
     :wood       "Trä"},
    :stats
    {:disclaimer-headline  "Datakälla"
     :general-disclaimer-1 "Lipas.fi idrottsanläggningar, friluftsleder och friluftsområden i Finland: Erkännande 4.0 Internationell (CC BY 4.0). https://creativecommons.org/licenses/by/4.0/deed.sv"
     :general-disclaimer-2 "Du har tillstånd att dela, kopiera och vidaredistribuera materialet oavsett medium eller format, bearbeta, remixa, transformera, och bygg vidare på materialet, för alla ändamål, även kommersiellt. Du måste ge ett korrekt erkännande om du använder Lipas-data, till exempel “Idrottsplatser: Lipas.fi, Jyväskylä Universitet, datum/år”."
     :general-disclaimer-3 "Observera, att Lipas.fi data är uppdaterat av kommuner och andra ägare av idrottsplatser samt Lipas.fi administratörer i Jyväskylä Universitet. Omfattning, riktighet eller enhetlighet kan inte garanteras. I Lipas.fi statistik är material baserat på data i Lipas.fi databas. Återstående information kan påverka beräkningar."
     :finance-disclaimer   "Ekonomiska uppgifter av kommuners idrotts- och ungdomsväsende: Statistikcentralen öppen data. Materialet har laddats ner från Statistikcentralens gränssnittstjänst 2001-2019 med licensen CC BY 4.0. Observera, att kommunerna ansvarar själva för att uppdatera sina ekonomiska uppgifter i Statistikcentralens databas. Enhetlighet och jämförbarhet mellan åren eller kommunerna kan inte garanteras."
     :description
     "Ekonomiska uppgifter om idrotts- och ungdomsväsende. Kommunen kan observera hur kostnader utvecklas över tid.",
     :filter-types         "Välj typer",
     :length-km-sum        "Idrottsrutters totalt längd ",
     :headline             "Statistik",
     :select-years         "Välj år",
     :browse-to            "Gå till statistiken",
     :select-issuer        "Välj understödare",
     :select-unit          "Välj måttenhet",
     :bullet3              "Bidrag",
     :bullet4              "Liikuntapaikkojen rakennusvuodet",
     :finance-stats        "Ekonomiska uppgifter",
     :select-city          "Välj kommun",
     :area-m2-min          "Minimum idrottsareal m²",
     :filter-cities        "Välj kommuner",
     :select-metrics       "Välj storheter",
     :area-m2-count        "Antal av platser med idrottsareal information",
     :show-ranking         "Visa rankning",
     :age-structure-stats  "Byggnadsår",
     :subsidies-count      "Antal av bidrag",
     :area-m2-sum          "Totalt idrottsareal m²",
     :select-metric        "Välj storhet",
     :bullet2              "Statistiken om idrottsanläggningar",
     :area-m2-max          "Maximum idrottsareal m²",
     :select-grouping      "Gruppering",
     :select-city-service  "Välj administratör",
     :region               "Region",
     :show-comparison      "Visa jämförelse",
     :length-km-avg        "Medeltal av idrottsruttens längd",
     :sports-sites-count   "Antal av idrottsanläggningar",
     :length-km-min        "Minimum idrottsruttens längd",
     :country-avg          "(medeltal för hela landet)",
     :length-km-count      "Antal av idrottsrutter med längd information",
     :population           "Folkmängd",
     :sports-stats         "Idrottsanläggningar",
     :select-cities        "Välj kommuner",
     :subsidies            "Bidrag",
     :select-interval      "Välj intervall",
     :bullet1              "Ekonomiska uppgifter om idrotts- och ungdomsväsende",
     :area-m2-avg          "Medeltal av idrottsareal m²",
     :age-structure        "Byggnadsår av idrottsanläggningar",
     :length-km-max        "Maximum idrottsruttens längd",
     :total-amount-1000e   "Totalt (1000 €)",
     :city-stats           "Kommunstatistik"},
    :pool-structures
    {:concrete         "Betong",
     :hardened-plastic "Barnbassäng",
     :steel            "Stål"},
    :map
    {:retkikartta-checkbox-reminder
     "Välj också igen \"Kan visas i Retkikartta.fi - service\" i följande steg i ytterligare information.",
     :zoom-to-user           "Zooma in till min position",
     :remove                 "Ta bort",
     :modify-polygon         "Modifera området",
     :draw-polygon           "Lägg till område",
     :retkikartta-problems-warning
     "Korrigera problem som är märkta på kartan för att visa i Retkikartta.fi.",
     :edit-later-hint        "Du kan modifera geometrin också senare",
     :center-map-to-site     "Fokusera kartan på platsen",
     :draw-hole              "Lägg till hål",
     :split-linestring       "Klippa ruttdel",
     :travel-direction       "Bestäm riktningen"
     :route-part-difficulty  "Ange svårighetsgrad för ruttsektion"
     :delete-vertices-hint
     "För att ta bort en punkt, tryck och håll alt-knappen och klicka på punkten",
     :calculate-route-length "Räkna ut längden",
     :calculate-area         "Räkna ytlan"
     :calculate-count        "Beräkna antal automatiskt"
     :remove-polygon         "Ta bort område",
     :modify-linestring      "Modifera rutten",
     :download-gpx           "Ladda ner GPX",
     :add-to-map             "Lägg till på karta",
     :bounding-box-filter    "Sök i området",
     :remove-linestring      "Ta bort ruttdel",
     :draw-geoms             "Rita",
     :confirm-remove         "Är du säker att du vill ta bort geometrin?",
     :draw                   "Lägg till på karta",
     :draw-linestring        "Lägg till ruttdel",
     :modify                 "Du kan dra punkten på kartan",
     :zoom-to-site           "Zooma in till sportplats",
     :kink                   "Korrigera så att ruttdelen inte korsar själv",
     :zoom-closer            "Zooma in"},
    :supporting-structures
    {:brick           "Tegel",
     :concrete        "Betong",
     :concrete-beam   "Häll",
     :concrete-pillar "Stål",
     :solid-rock      "Häll",
     :steel           "Stål",
     :wood            "Trä"},
    :owner
    {:unknown                "Ingen information",
     :municipal-consortium   "Samkommun",
     :other                  "Annat",
     :company-ltd            "Företag",
     :city                   "Kommun",
     :state                  "Stat",
     :registered-association "Registrerad förening",
     :foundation             "Stiftelse",
     :city-main-owner        "Företag med kommun som majoritetsägare"},
    :menu
    {:frontpage "Framsidan",
     :headline  "LIPAS",
     :jyu       "Jyväskylä universitet",
     :main-menu "Huvudmeny"},
    :dryer-types
    {:cooling-coil "timmar", :munters "månader", :none "timmar"},
    :physical-units
    {:mm            "mm",
     :hour          "h",
     :m3            "m³",
     :m             "m",
     :temperature-c "Temperatur °C",
     :l             "l",
     :mwh           "MWh",
     :m2            "m²",
     :celsius       "°C"},
    :lipas.swimming-pool.water-treatment
    {:filtering-methods "E-post",
     :headline          "Kontaktuppgifter",
     :ozonation?        "E-post",
     :uv-treatment?     "Kontaktuppgifter"},
    :statuses            {:edited "{1} (redigerad)"},
    :lipas.user
    {:email                      "E-post",
     :permissions                "Rättigheter",
     :permissions-example        "Rätt att uppdatera Jyväskylä ishallen.",
     :saved-searches             "Sparad sök",
     :report-energy-and-visitors "Spara raportmodellen",
     :permission-to-cities
     "Du har rättighet att uppdatera de här kommunerna:",
     :password                   "Lösenord",
     :lastname                   "Efternamn",
     :save-report                "Spara raportmodellen",
     :sports-sites               "Egna platser",
     :permission-to-all-cities
     "Du har rättighet att uppdatera alla kommuner",
     :username                   "Användarnamn",
     :history                    "Historia",
     :saved-reports              "Sparad raportmodeller",
     :contact-info               "Kontaktuppgifter",
     :permission-to-all-types
     "Du har rättighet att uppdatera alla typer",
     :requested-permissions      "Begärda rättigheter",
     :email-example              "epost@exempel.fi",
     :permission-to-portal-sites
     "Du har rättighet att uppdatera de här platserna:",
     :permissions-help           "Skriv vad du vill uppdatera i Lipas",
     :permission-to-activities   "Du har behörighet till följande aktiviteter"
     :report-energy-consumption  "Begärda rättigheter",
     :firstname                  "Förnamn",
     :save-search                "Spara söket",
     :view-basic-info            "Kolla ut grunddata",
     :no-permissions
     "Användarnamn till Lipas har inte ännu konfirmerats",
     :username-example           "tane12",
     :permission-to-types
     "Du har rättighet att uppdatera de här typerna:"},
    :heat-recovery-types {:thermal-wheel "Hjälp"}},
   :en
   {:loi
    {:category                     "Category"
     :status                       "Status"
     :type                         "Object Type"
     :type-and-category-disclaimer "Category and type must be selected before adding to the map"}
    :utp
    {:read-only-disclaimer       "There is currently only an editing view for activities. Log in and go to the editing mode from the pen symbol."
     :add-contact                "Add contact information"
     :unit                       "unit"
     :highlight                  "Highlight"
     :add-highlight              "Add highlight"
     :photo                      "Photograph"
     :add-photo                  "Add photograph"
     :video                      "Video"
     :add-video                  "Add video"
     :link                       "Link"
     :length-km                  "Length km"
     :add-subroute               "Add sub-route"
     :delete-route-prompt        "Are you sure you want to delete this route?"
     :custom-rule                "Custom permit, regulation, instruction"
     :custom-rules               "Custom instructions"
     :add-subroute-ok            "OK"
     :route-is-made-of-subroutes "The route consists of multiple separate sections"
     :select-route-parts-on-map  "Select route parts on the map"
     :finish-route-details       "Route completed"}
    :analysis
    {:headline                   "Analysis Tool (beta)"
     :description                "The analysis tool can be used to evaluate the supply and accessibility of physical activity conditions by comparing the distance and travel times to physical activity facilities in relation to other facilities, population, and educational institutions."
     :results                    "Results"
     :mean                       "Mean"
     :population-weighted-mean   "Population-weighted diversity index"
     :median                     "Median"
     :mode                       "Mode"
     :reachability               "Reachability"
     :categories                 "Categories"
     :diversity                  "Diversity"
     :diversity-grid             "Result grid"
     :diversity-help1            "The diversity tool can be used to evaluate and compare the diversity of physical activity conditions in the residents' local environment at the grid and area level. The diversity index calculated by the diversity tool describes how diversely different physical activity opportunities the resident can reach within the selected distance along the road and path network (assuming 800 m). The higher the index value, the more diverse physical activity facilities are available in the residents' local environment."
     :diversity-help2            "The tool uses a postal code-based area division by default. The division can also be made based on another existing area division by importing the desired geometry file (Shapefile, GeoJSON or KML)."
     :diversity-help3            "The calculation is done per 250 x 250 m population grid from Statistics Finland. The area-level results indicate the average diversity of physical activity conditions in the residents' local environments (population-weighted average of the diversity index). The calculation distance of the diversity tool is based on the OpenStreetMap road network data and the OSRM tool."
     :analysis-areas             "Analysis areas"
     :categories-help            "Physical activity facility types belonging to the same category affect the diversity index only once."
     :description2               "The population data used is from Statistics Finland's 250x250m and 1x1km grid data, which shows the population distribution in three age groups (0-14, 15-65, 65-) in each grid."
     :description3               "The calculation of travel times by different modes of transport (walking, cycling, car) is based on open OpenStreetMap data and the OSRM tool."
     :description4               "The name and location data of educational institutions is based on open data from Statistics Finland. The name and location data of early childhood education units is based on data collected and provided by LIKES."
     :add-new                    "Add new"
     :distance                   "Distance"
     :travel-time                "Travel time"
     :zones                      "Zones"
     :zone                       "Zone"
     :schools                    "Schools"
     :daycare                    "Early childhood education unit"
     :elementary-school          "Elementary school"
     :high-school                "High school"
     :elementary-and-high-school "Elementary and high school"
     :special-school             "Special school"
     :population                 "Population"
     :direct                     "As the crow flies"
     :by-foot                    "On foot"
     :by-car                     "By car"
     :by-bicycle                 "By bicycle"
     :analysis-buffer            "Analysis area"
     :filter-types               "Filter by type"
     :settings                   "Settings"
     :settings-map               "Objects shown on the map"
     :settings-zones             "Distances and travel times"
     :settings-help              "The analysis area is determined by the largest distance zone. Travel times are also not calculated outside this area."}

    :sport
    {:description
     "LIPAS is the national database of sports facilities and their conditions in Finland.",
     :headline        "Sports Facilities",
     :open-interfaces "Open data and APIs",
     :up-to-date-information
     "Up-to-date information about sports facilities",
     :updating-tools  "Tools for data maintainers"
     :analysis-tools  "Analysis tools"},
    :confirm
    {:discard-changes? "Do you want to discard all changes?",
     :headline         "Confirmation",
     :no               "No",
     :delete-confirm   "Are you sure you want to delete the row?"
     :resurrect?       "Are you sure you want to resurrect this sports facility?",
     :save-basic-data? "Do you want to save general information?",
     :yes              "Yes"},
    :lipas.swimming-pool.saunas
    {:accessible? "Accessible",
     :add-sauna   "Add sauna",
     :edit-sauna  "Edit sauna",
     :headline    "Saunas",
     :men?        "Men",
     :women?      "Women"},
    :slide-structures
    {:concrete         "Concrete",
     :hardened-plastic "Hardened plastic",
     :steel            "Steel"},
    :lipas.swimming-pool.conditions
    {:open-days-in-year "Open days in year",
     :open-hours-mon    "Mondays",
     :headline          "Open hours",
     :open-hours-wed    "Wednesdays",
     :open-hours-thu    "Thursdays",
     :open-hours-fri    "Fridays",
     :open-hours-tue    "Tuesdays",
     :open-hours-sun    "Sundays",
     :daily-open-hours  "Daily open hours",
     :open-hours-sat    "Saturdays"},
    :lipas.ice-stadium.ventilation
    {:dryer-duty-type          "Dryer duty type",
     :dryer-type               "Dryer type",
     :headline                 "Ventilation",
     :heat-pump-type           "Heat pump type",
     :heat-recovery-efficiency "Heat recovery efficiency",
     :heat-recovery-type       "Heat recovery type"},
    :swim
    {:description
     "Swimming pools portal contains data about indoor  swimming pools energy consumption and related factors.",
     :latest-updates       "Latest updates",
     :headline             "Swimming pools",
     :basic-data-of-halls
     "General information about building and facilities",
     :updating-basic-data  "Updating general information",
     :edit                 "Report consumption",
     :entering-energy-data "Reporing energy consumption",
     :list                 "Pools list",
     :visualizations       "Comparison"},
    :home-page
    {:description
     "LIPAS system has information on sport facilities, routes and recreational areas and economy. The content is open  data under the CC4.0 International licence.",
     :headline "Front page"},
    :ice-energy
    {:description
     "Up-to-date information can be found from Finhockey association web-site.",
     :energy-calculator "Ice stadium energy concumption calculator",
     :finhockey-link    "Browse to Finhockey web-site",
     :headline          "Energy Info"},
    :filtering-methods
    {:activated-carbon      "Activated carbon",
     :coal                  "Coal",
     :membrane-filtration   "Membrane filtration",
     :multi-layer-filtering "Multi-layer filtering",
     :open-sand             "Open sand",
     :other                 "Other",
     :precipitation         "Precipitation",
     :pressure-sand         "Pressure sand"},
    :open-data
    {:description "Interface links and instructions",
     :headline    "Open Data",
     :rest        "REST",
     :wms-wfs     "WMS & WFS"},
    :lipas.swimming-pool.pool
    {:accessibility "Accessibility", :outdoor-pool? "Outdoor pool"},
    :pool-types
    {:multipurpose-pool "Multi-purpose pool",
     :whirlpool-bath    "Whirlpool bath",
     :main-pool         "Main pool",
     :therapy-pool      "Therapy pool",
     :fitness-pool      "Fitness pool",
     :diving-pool       "Diving pool",
     :childrens-pool    "Childrens pool",
     :paddling-pool     "Paddling pool",
     :cold-pool         "Cold pool",
     :other-pool        "Other pool",
     :teaching-pool     "Teaching pool"},
    :lipas.ice-stadium.envelope
    {:base-floor-structure    "Base floor structure",
     :headline                "Envelope structure",
     :insulated-ceiling?      "Insulated ceiling",
     :insulated-exterior?     "Insulated exterior",
     :low-emissivity-coating? "Low emissivity coating"},
    :disclaimer
    {:headline       "NOTICE!",
     :test-version
     "This is LIPAS TEST-ENVIRONMENT. Changes made here  don't affect the production system."
     :data-ownership "The LIPAS geographic information system's data repository is owned and managed by the University of Jyväskylä. The University of Jyväskylä reserves the rights to all content, information, and material added to the system and created during the system's development. The user who adds material to the system is responsible for ensuring that the material is accurate and does not infringe on third-party copyrights. The University of Jyväskylä is not liable for claims made by third parties regarding the material. By adding data to the LIPAS geographic information system, the user is considered to have accepted the use of the added material in the system as part of the data repository. The data stored in the LIPAS database is open and freely available under the CC 4.0 Attribution license."},
    :admin
    {:private-foundation      "Private / foundation",
     :city-other              "City / other",
     :unknown                 "Unknown",
     :municipal-consortium    "Municipal consortium",
     :private-company         "Private / company",
     :private-association     "Private / association",
     :other                   "Other",
     :city-technical-services "City / technical services",
     :city-sports             "City / sports",
     :state                   "State",
     :city-education          "City / education"},
    :accessibility
    {:lift            "Pool lift",
     :low-rise-stairs "Low rise stairs",
     :mobile-lift     "Mobile pool lift",
     :slope           "Slope"},
    :general
    {:headline       "Headline"
     :description    "Description",
     :hall           "Hall",
     :women          "Women",
     :age-anonymized "Age anonymized"
     :total-short    "Total",
     :done           "Done",
     :updated        "Updated",
     :name           "Name",
     :reported       "Reported",
     :type           "Type",
     :last-modified  "Last modified",
     :here           "here",
     :event          "Event",
     :structure      "Structure",
     :general-info   "General information",
     :comment        "Comment",
     :measures       "Measures",
     :men            "Men"
     :more           "More"
     :less           "Less"},
    :dryer-duty-types {:automatic "Automatic", :manual "Manual"},
    :swim-energy
    {:description
     "Up-to-date information can be found from UKTY and  SUH web-sites.",
     :headline       "Energy info",
     :headline-short "Info",
     :suh-link       "Browse to SUH web-site",
     :ukty-link      "Browse to UKTY web-site"},
    :time
    {:two-years-ago      "2 years ago",
     :date               "Date",
     :hour               "Hour",
     :this-month         "This month",
     :time               "Time",
     :less-than-hour-ago "Less than an hour ago",
     :start              "Started",
     :this-week          "This week",
     :today              "Today",
     :month              "Month",
     :long-time-ago      "Long time ago",
     :year               "Year",
     :just-a-moment-ago  "Just a moment ago",
     :yesterday          "Yesterday",
     :three-years-ago    "3 years ago",
     :end                "Ended",
     :this-year          "This year",
     :last-year          "Last year"},
    :ice-resurfacer-fuels
    {:LPG         "LPG",
     :electicity  "Electricity",
     :gasoline    "Gasoline",
     :natural-gas "Natural gas",
     :propane     "Propane"},
    :ice-rinks        {:headline "Venue details"},
    :month
    {:sep "September",
     :jan "January",
     :jun "June",
     :oct "October",
     :jul "July",
     :apr "April",
     :feb "February",
     :nov "November",
     :may "May",
     :mar "March",
     :dec "December",
     :aug "August"},
    :type
    {:name          "Type",
     :main-category "Main category",
     :sub-category  "Sub category",
     :type-code     "Type code"},
    :duration
    {:hour "hours", :month "months", :years "years", :years-short "y"},
    :size-categories
    {:competition "Competition < 3000 persons",
     :large       "Large > 3000 persons",
     :small       "Small > 500 persons"},
    :lipas.admin
    {:access-all-sites           "You have admin permissions.",
     :confirm-magic-link
     "Are you sure you want to send magic link to {1}?",
     :headline                   "Admin",
     :magic-link                 "Magic Link",
     :select-magic-link-template "Select letter",
     :send-magic-link            "Send magic link to {1}",
     :users                      "Users"},
    :lipas.ice-stadium.refrigeration
    {:headline                       "Refrigeration",
     :refrigerant-solution-amount-l  "Refrigerant solution amount (l)",
     :individual-metering?           "Individual metering",
     :original?                      "Original",
     :refrigerant                    "Refrigerant",
     :refrigerant-solution           "Refrigerant solution",
     :condensate-energy-main-targets "Condensate energy main target",
     :power-kw                       "Power (kW)",
     :condensate-energy-recycling?   "Condensate energy recycling",
     :refrigerant-amount-kg          "Refrigerant amount (kg)"},
    :lipas.building
    {:headline                    "Building",
     :total-volume-m3             "Volume m³",
     :staff-count                 "Staff count",
     :piled?                      "Piled",
     :heat-sections?              "Pool room is divided to heat sections?",
     :total-ice-area-m2           "Ice surface area m²",
     :main-construction-materials "Main construction materials",
     :main-designers              "Main designers",
     :total-pool-room-area-m2     "Pool room area m²",
     :heat-source                 "Heat source",
     :total-surface-area-m2       "Area m²",
     :total-water-area-m2         "Water surface area m²",
     :ceiling-structures          "Ceiling structures",
     :supporting-structures       "Supporting structures",
     :seating-capacity            "Seating capacity"},
    :heat-pump-types
    {:air-source         "Air source heat pump",
     :air-water-source   "Air-water source heat pump",
     :exhaust-air-source "Exhaust air source heat pump",
     :ground-source      "Ground source heat pump",
     :none               "None"},
    :search
    {:table-view            "Table view",
     :headline              "Search",
     :results-count         "{1} results",
     :placeholder           "Search...",
     :retkikartta-filter    "Retkikartta.fi",
     :filters               "Filters",
     :search-more           "Search more...",
     :page-size             "Page size",
     :search                "Search",
     :permissions-filter    "Show only sites that I can edit",
     :display-closest-first "Display nearest first",
     :list-view             "List view",
     :pagination            "Results {1}-{2}",
     :school-use-filter     "Used by schools",
     :clear-filters         "Clear filters"},
    :map.tools
    {:download-backup-tooltip       "Download backup"
     :drawing-tooltip               "Drawing tool selected",
     :drawing-hole-tooltip          "Hole drawing tool selected",
     :edit-tool                     "Edit tool",
     :importing-tooltip             "Import tool selected",
     :deleting-tooltip              "Delete tool selected",
     :splitting-tooltip             "Split tool selected",
     :simplifying                   "Simplify tool selected",
     :selecting                     "Select tool selected",
     :simplify                      "Simplify",
     :travel-direction-tooltip      "Travel direction tool selected"
     :route-part-difficulty-tooltip "Route section difficulty tool selected"},
    :map.tools.simplify
    {:headline "Simplify geometries"}
    :partners         {:headline "In association with"},
    :actions
    {:duplicate                "Duplicate",
     :resurrect                "Resurrect",
     :select-year              "Select year",
     :select-owners            "Select owners",
     :select-admins            "Select administrators",
     :select-tool              "Select tool",
     :save-draft               "Save draft",
     :redo                     "Redo",
     :open-main-menu           "Open main menu",
     :back-to-listing          "Back to list view",
     :filter-surface-materials "Filter surface materials",
     :browse                   "Browse",
     :select-type              "Select types",
     :edit                     "Edit",
     :filter-construction-year "Filter construction years",
     :submit                   "Submit",
     :choose-energy            "Choose energy",
     :delete                   "Delete",
     :browse-to-map            "Go to map view",
     :save                     "Save",
     :close                    "Close",
     :filter-area-m2           "Filter area m²",
     :show-account-menu        "Open account menu",
     :fill-required-fields     "Please fill all required fields",
     :undo                     "Undo",
     :browse-to-portal         "Enter portal",
     :download-excel           "Download",
     :fill-data                "Fill",
     :select-statuses          "Status",
     :select-cities            "Select cities",
     :select-hint              "Select...",
     :discard                  "Discard",
     :more                     "More...",
     :cancel                   "Cancel",
     :select-columns           "Select fields",
     :add                      "Add",
     :show-all-years           "Show all years",
     :download                 "Download",
     :select-hall              "Select hall",
     :clear-selections         "Clear",
     :select                   "Select",
     :select-types             "Select types"
     :select-all               "Select all"},
    :dimensions
    {:area-m2         "Area m²",
     :depth-max-m     "Depth max m",
     :depth-min-m     "Depth min m",
     :length-m        "Length m",
     :surface-area-m2 "Surface area m²",
     :volume-m3       "Volume m³",
     :width-m         "Width m"},
    :login
    {:headline              "Login",
     :login-here            "here",
     :login-with-password   "Login with password",
     :password              "Password",
     :logout                "Log out",
     :username              "Email / Username",
     :login-help
     "If you are already a LIPAS-user you can login using just your email address.",
     :login                 "Login",
     :magic-link-help       "Order login link",
     :order-magic-link      "Order login link",
     :login-with-magic-link "Login with email",
     :bad-credentials       "Wrong username or password",
     :magic-link-ordered    "Password",
     :username-example      "paavo.paivittaja@kunta.fi",
     :forgot-password?      "Forgot password?"},
    :lipas.ice-stadium.conditions
    {:open-months                     "Open months in year",
     :headline                        "Conditions",
     :ice-resurfacer-fuel             "Ice resurfacer fuel",
     :stand-temperature-c             "Stand temperature (during match)",
     :ice-average-thickness-mm        "Average ice thickness (mm)",
     :air-humidity-min                "Air humidity % min",
     :daily-maintenances-weekends     "Daily maintenances on weekends",
     :air-humidity-max                "Air humidity % max",
     :daily-maintenances-week-days    "Daily maintenances on weekdays",
     :maintenance-water-temperature-c "Maintenance water temperature",
     :ice-surface-temperature-c       "Ice surface temperature",
     :weekly-maintenances             "Weekly maintenances",
     :skating-area-temperature-c
     "Skating area temperature (at 1m height)",
     :daily-open-hours                "Daily open hours",
     :average-water-consumption-l
     "Average water consumption (l) / maintenance"},
    :map.demographics
    {:headline   "Analysis tool",
     :tooltip    "Analysis tool",
     :helper-text
     "Select sports facility on the map.",
     :copyright1 "Statistics of Finland 2019/2020",
     :copyright2 "1x1km and 250x250m population grids",
     :copyright3 "with license"},
    :lipas.swimming-pool.slides
    {:add-slide  "Add Slide",
     :edit-slide "Edit slide",
     :headline   "Slides"},
    :notifications
    {:get-failed             "Couldn't get data.",
     :save-failed            "Saving failed",
     :save-success           "Saving succeeded"
     :ie                     "Internet Explorer is not a supported browser. Please use another web browser, e.g. Chrome, Firefox or Edge."
     :thank-you-for-feedback "Thank you for feedback!"},
    :lipas.swimming-pool.energy-saving
    {:filter-rinse-water-heat-recovery?
     "Filter rinse water heat recovery?",
     :headline                    "Energy saving",
     :shower-water-heat-recovery? "Shower water heat recovery?"},
    :ice-form
    {:headline       "Report readings",
     :headline-short "Report readings",
     :select-rink    "Select stadium"},
    :restricted       {:login-or-register "Please login or register"},
    :lipas.ice-stadium.rinks
    {:add-rink      "Add rink",
     :edit-rink     "Edit rink",
     :headline      "Rinks"
     :rink1-width   "Rink 1 width m"
     :rink2-width   "Rink 2 width m"
     :rink3-width   "Rink 3 width m"
     :rink1-length  "Rink 1 length m"
     :rink2-length  "Rink 2 length m"
     :rink3-length  "Rink 3 length m"
     :rink1-area-m2 "Rink 1 area m²"
     :rink2-area-m2 "Rink 2 area m²"
     :rink3-area-m2 "Rink 3 area m²"},
    :sports-site.elevation-profile
    {:headline                "Elevation Profile",
     :distance-from-start-m   "Distance from Start (m)",
     :distance-from-start-km  "Distance from Start (km)",
     :height-from-sea-level-m "Height from Sea Level (m)",
     :total-ascend            "Total Ascent",
     :total-descend           "Total Descent"}
    :lipas.sports-site
    {:properties             "Properties",
     :delete-tooltip         "Delete sports facility...",
     :headline               "Sports facility",
     :new-site-of-type       "New {1}",
     :address                "Address",
     :new-site               "New Sports Facility",
     :phone-number           "Phone number",
     :admin                  "Admin",
     :surface-materials      "Surface materials",
     :www                    "Web-site",
     :name                   "Finnish name",
     :reservations-link      "Reservations",
     :construction-year      "Construction year",
     :type                   "Type",
     :delete                 "Delete {1}",
     :renovation-years       "Renovation years",
     :name-localized-se      "Swedish name",
     :name-localized-en      "English name"
     :status                 "Status",
     :id                     "LIPAS-ID",
     :details-in-portal      "Click here to see details",
     :comment                "More information",
     :ownership              "Ownership",
     :name-short             "Name",
     :basic-data             "General",
     :delete-reason          "Reason",
     :event-date             "Modified",
     :email-public           "Email (public)",
     :add-new                "Add Sports Facility",
     :contact                "Contact",
     :owner                  "Owner",
     :marketing-name         "Marketing name"
     :no-permission-tab      "You do not have permission to edit the information on this tab"
     :add-new-planning       "Add a sports or outdoor site in draft mode"
     :planning-site          "Draft"
     :creating-planning-site "You are adding a site in draft mode for analysis tools."},
    :status
    {:active                     "Active",
     :planned                    "Planned"
     :incorrect-data             "Incorrect data",
     :out-of-service-permanently "Permanently out of service",
     :out-of-service-temporarily "Temporarily out of service"},
    :register
    {:headline "Register",
     :link     "Sign up here"
     :thank-you-for-registering
     "Thank you for registering! You wil receive an email once we've updated your permissions."},
    :map.address-search
    {:title "Find address", :tooltip "Find address"},
    :ice-comparison   {:headline "Compare"},
    :lipas.visitors
    {:headline                 "Visitors",
     :monthly-visitors-in-year "Monthly visitors in {1}",
     :not-reported             "Visitors not reported",
     :not-reported-monthly     "No monthly data",
     :spectators-count         "Spectators count",
     :total-count              "Visitors count"},
    :lipas.energy-stats
    {:headline        "Energy consumption in {1}",
     :energy-reported-for
     "Electricity, heat and water consumption reported for {1}",
     :report          "Report consumption",
     :disclaimer      "*Based on reported consumption in {1}",
     :reported        "Reported {1}",
     :cold-mwh        "Cold MWh",
     :hall-missing?   "Is your data missing from the diagram?",
     :not-reported    "Not reported {1}",
     :water-m3        "Water m³",
     :electricity-mwh "Electricity MWh",
     :heat-mwh        "Heat MWh",
     :energy-mwh      "Energy MWh"},
    :map.basemap
    {:copyright    "© National Land Survey",
     :maastokartta "Terrain",
     :ortokuva     "Satellite",
     :taustakartta "Default"
     :transparency "Basemap transparency"},
    :map.overlay
    {:tooltip                       "Other layers"
     :mml-kiinteisto                "Property boundaries"
     :light-traffic                 "Light traffic"
     :retkikartta-snowmobile-tracks "Metsähallitus snowmobile tracks"}
    :lipas.swimming-pool.pools
    {:add-pool  "Add pool",
     :edit-pool "Edit pool",
     :headline  "Pools",
     :structure "Structure"},
    :condensate-energy-targets
    {:hall-heating              "Hall heating",
     :maintenance-water-heating "Maintenance water heating",
     :other-space-heating       "Other heating",
     :service-water-heating     "Service water heating",
     :snow-melting              "Snow melting",
     :track-heating             "Track heating"},
    :refrigerants
    {:CO2   "CO2",
     :R134A "R134A",
     :R22   "R22",
     :R404A "R404A",
     :R407A "R407A",
     :R407C "R407C",
     :R717  "R717"},
    :harrastuspassi   {:disclaimer "When the option “May be shown in Harrastuspassi.fi” is ticked, the information regarding the sport facility will be transferred automatically to the Harrastuspassi.fi. I agree to update in the Lipas service any changes to information regarding the sport facility. The site administrator is responsible for the accuracy of information and safety of the location. Facility information is shown in Harrastuspassi.fi only if the municipality has a contract with Harrastuspassi.fi service provider."}
    :retkikartta      {:disclaimer "When the option “May be shown in Excursionmap.fi” is ticked, the information regarding the open air exercise location will be transferred once in every 24 hours automatically to the Excursionmap.fi service, maintained by Metsähallitus.
I agree to update in the Lipas service any changes to information regarding an open air exercise location.
The site administrator is responsible for the accuracy of information, safety of the location, responses to feedback and possible costs related to private roads."},
    :reset-password
    {:change-password    "Change password",
     :enter-new-password "Enter new password",
     :get-new-link       "Get new reset link",
     :headline           "Forgot password?",
     :helper-text        "We will email password reset link to you.",
     :password-helper-text
     "Password must be at least 6 characters long",
     :reset-link-sent    "Reset link sent! Please check your email!",
     :reset-success
     "Password has been reset! Please login with the new password."},
    :reports
    {:contacts          "Contacts",
     :download-as-excel "Download Excel",
     :select-fields     "Select field",
     :selected-fields   "Selected fields",
     :shortcuts         "Shortcuts",
     :file-format       "Format"
     :tooltip           "Create Excel from search results"},
    :heat-sources
    {:district-heating      "District heating",
     :private-power-station "Private power station"},
    :map.import
    {:headline          "Import geometries",
     :geoJSON
     "Upload .json file containing FeatureCollection. Coordinates must be in WGS84 format.",
     :gpx               "Coordinates must be in WGS84 format",
     :supported-formats "Supported formats are {1}",
     :replace-existing? "Replace existing geometries",
     :select-encoding   "Select encoding",
     :tab-header        "Import",
     :kml               "Coordinates must be in WGS84 format",
     :shapefile         "Import zip file containing .shp .dbf and .prj file.",
     :import-selected   "Import selected",
     :tooltip           "Import from file",
     :unknown-format    "Unkonwn format '{1}'"
     :unknown-error     "An unexpected error occurred. Try with another file."
     :no-geoms-of-type  "The provided file does not contain any {1} geometries."

     :coords-not-in-finland-wgs84-bounds "The provided file does not contain coordinates in the WGS84 (EPSG:4326) coordinate system. Check that the source material is in the correct coordinate system."},
    :error
    {:email-conflict      "Email is already in use",
     :email-not-found     "Email address is not registered",
     :invalid-form        "Fix fields marked with red",
     :no-data             "No data",
     :reset-token-expired "Password reset failed. Link has expired.",
     :unknown             "Unknown error occurred. :/",
     :user-not-found      "User not found.",
     :username-conflict   "Username is already in use"},
    :reminders
    {:description
     "We will email the message to you at the selected time",
     :after-one-month  "After one month",
     :placeholder      "Remember to check sports-facility \"{1}\" {2}",
     :select-date      "Select date",
     :tomorrow         "Tomorrow",
     :title            "Add reminder",
     :after-six-months "After six months",
     :in-a-year        "In a year",
     :message          "Message",
     :in-a-week        "In a week"},
    :units
    {:days-in-year   "days a year",
     :hours-per-day  "hours a day",
     :pcs            "pcs",
     :percent        "%",
     :person         "person",
     :times-per-day  "times a day",
     :times-per-week "times a wekk"},
    :lipas.energy-consumption
    {:contains-other-buildings?
     "Readings contain also other buildings or spaces",
     :headline                 "Energy consumption",
     :yearly                   "Yearly",
     :report                   "Report readings",
     :electricity              "Electricity MWh",
     :headline-year            "Energy consumption in {1}",
     :monthly?                 "I want to report monthly energy consumption",
     :reported-for-year        "Energy consumption reported for {1}",
     :monthly                  "Monthly",
     :operating-hours          "Operating hours",
     :not-reported             "Energy consumption not reported",
     :not-reported-monthly     "No monthly data available",
     :heat                     "Heat (acquired) MWh",
     :cold                     "Cold energy (acquired) MWh",
     :comment                  "Comment",
     :water                    "Water m³",
     :monthly-readings-in-year "Monthly energy consumption in {1}"},
    :ice
    {:description
     "Ice stadiums portal contains data about ice stadiums  energy consumption and related factors.",
     :large                "Grand hall > 3000 persons",
     :competition          "Competition hall < 3000 persons",
     :headline             "Ice stadiums",
     :video                "Video",
     :comparison           "Compare venues",
     :size-category        "Size category",
     :basic-data-of-halls
     "General information about building and facilities",
     :updating-basic-data  "Updating general information",
     :entering-energy-data "Reporing energy consumption",
     :small                "Small competition hall > 500 persons",
     :watch                "Watch",
     :video-description
     "Pihjalalinna Areena - An Energy Efficient Ice Stadium"},
    :lipas.location
    {:address       "Address",
     :city          "City",
     :city-code     "City code",
     :headline      "Location",
     :neighborhood  "Neighborhood",
     :postal-code   "Postal code",
     :postal-office "Postal office"},
    :lipas.user.permissions
    {:admin?       "Admin",
     :all-cities?  "Permission to all cities",
     :all-types?   "Permission to all types",
     :cities       "Access to sports faclities in cities",
     :sports-sites "Access to sports faclities",
     :types        "Access to sports faclities of type"
     :activities   "Activities"},
    :lipas.user.permissions.roles
    {:roles             "Roles"
     :role              "Role"
     :context-value-all "All"
     :role-names        {:admin              "Admin"
                         :city-manager       "City Manager"
                         :type-manager       "Type Manager"
                         :site-manager       "Site Manager"
                         :activities-manager "UTP Manager"
                         :floorball-manager  "Floorball Editor"
                         :analysis-user      "Analysis tool user"
                         :ptv                "PTV Manager"}
     :context-keys      {:city-code "Municipality"
                         :type-code "Type"
                         :activity  "Activity"
                         :lipas-id  "Site"}
     :edit-role         {:edit-header  "Edit"
                         :new-header   "Add Role"
                         :stop-editing "Stop Editing"
                         :add          "Add"
                         :choose-role  "Choose a role first to select which resources the role affects."}
     :permissions-old   "(old, read-only)"}
    :help
    {:headline                 "Help",
     :permissions-help
     "Please contact us in case you need more permissions",
     :permissions-help-body
     "I need permissions to following sports facilities:",
     :permissions-help-subject "I need more permissions"
     :privacy-policy           "Privacy policy"
     :manage-content           "Manage help content"},
    :ceiling-structures
    {:concrete         "Concrete",
     :double-t-beam    "Double-T",
     :glass            "Glass",
     :hollow-core-slab "Hollow-core slab",
     :solid-rock       "Solid rock",
     :steel            "Steel",
     :wood             "Wood"},
    :data-users
    {:data-user?    "Do you use LIPAS-data?",
     :email-body    "Tell us",
     :email-subject "We also use LIPAS-data",
     :headline      "Data users",
     :tell-us       "Tell us"},
    :lipas.swimming-pool.facilities
    {:showers-men-count              "Mens showers count",
     :lockers-men-count              "Mens lockers count",
     :headline                       "Other services",
     :platforms-5m-count             "5 m platforms count",
     :kiosk?                         "Kiosk / cafeteria",
     :hydro-neck-massage-spots-count "Neck hydro massage spots count",
     :lockers-unisex-count           "Unisex lockers count",
     :platforms-10m-count            "10 m platforms count",
     :hydro-massage-spots-count      "Other hydro massage spots count",
     :lockers-women-count            "Womens lockers count",
     :platforms-7.5m-count           "7.5 m platforms count",
     :gym?                           "Gym",
     :showers-unisex-count           "Unisex showers count",
     :platforms-1m-count             "1 m platforms count",
     :showers-women-count            "Womens showers count",
     :platforms-3m-count             "3 m platforms count"},
    :sauna-types
    {:infrared-sauna "Infrared sauna",
     :other-sauna    "Other sauna",
     :sauna          "Sauna",
     :steam-sauna    "Steam sauna"},
    :stats-metrics
    {:investments        "Investments",
     :net-costs          "Net costs",
     :operating-expenses "Operating expenses",
     :operating-incomes  "Operating incomes",
     :subsidies          "Granted subsidies"},
    :refrigerant-solutions
    {:CO2             "CO2",
     :H2ONH3          "H2O/NH3",
     :cacl            "CaCl",
     :ethanol-water   "Ethanol-water",
     :ethylene-glycol "Ethylene glycol",
     :freezium        "Freezium",
     :water-glycol    "Water-glycol"},
    :user
    {:admin-page-link     "Admin page",
     :promo1-link         "Show TEAviisari targets I can update"
     :front-page-link     "front page",
     :greeting            "Hello {1} {2}!",
     :headline            "Profile",
     :ice-stadiums-link   "Ice stadiums",
     :promo1-topic        "NOTICE! AN UPDATE IN THE CLASSIFICATION SYSTEM OF LIPAS SPORTS FACILITIES (11 January 2022) ",
     :promo1-text         "View PDF",
     :swimming-pools-link "Swimming pools"
     :promo-headline      "Current News"
     :data-ownership      "Terms of use "},
    :building-materials
    {:brick      "Brick",
     :concrete   "Concrete",
     :solid-rock "Solid rock",
     :steel      "Steel",
     :wood       "Wood"},
    :stats
    {:disclaimer-headline  "Data sources"
     :general-disclaimer-1 "Lipas.fi Finland’s sport venues and places, outdoor routes and recreational areas data is open data under license: Attribution 4.0 International (CC BY 4.0). https://creativecommons.org/licenses/by/4.0/deed.en."
     :general-disclaimer-2 "You are free to use, adapt and share Lipas.fi data in any way, as long as the source is mentioned (Lipas.fi data, University of Jyväskylä, date/year of data upload or relevant information)."
     :general-disclaimer-3 "Note, that Lipas.fi data is updated by municipalities, other owners of sport facilities and Lipas.fi administrators at the University of Jyväskylä, Finland. Data accuracy, uniformity or comparability between municipalities is not guaranteed. In the Lipas.fi statistics, all material is based on the data in Lipas database and possible missing information may affect the results."
     :finance-disclaimer   "Data on finances of sport and youth sector in municipalities: Statistics Finland open data. The material was downloaded from Statistics Finland's interface service in 2001-2019 with the license CC BY 4.0. Notice, that municipalities are responsible for updating financial information to Statistics Finland’s database. Data uniformity and data comparability between years or between municipalities is not guaranteed."
     :description
     "Statistics of sports facilities and related municipality finances",
     :filter-types         "Filter types",
     :length-km-sum        "Total route length km",
     :headline             "Statistics",
     :select-years         "Select years",
     :browse-to            "Go to statistics",
     :select-issuer        "Select issuer",
     :select-unit          "Select unit",
     :bullet3              "Subsidies",
     :bullet4              "Construction years",
     :finance-stats        "Finances",
     :select-city          "Select city",
     :area-m2-min          "Min area m²",
     :filter-cities        "Filter cities",
     :select-metrics       "Select metrics",
     :area-m2-count        "Area m² reported count",
     :show-ranking         "Show ranking",
     :age-structure-stats  "Construction years",
     :subsidies-count      "Subsidies count",
     :area-m2-sum          "Total area m²",
     :select-metric        "Select metric",
     :bullet2              "Sports facility statistics",
     :area-m2-max          "Max area m²",
     :select-grouping      "Grouping",
     :select-city-service  "Select city service",
     :region               "Region",
     :show-comparison      "Show comparison",
     :length-km-avg        "Average route length km",
     :sports-sites-count   "Total count",
     :length-km-min        "Min route length km",
     :country-avg          "(country average)",
     :length-km-count      "Route length reported count",
     :population           "Population",
     :sports-stats         "Sports faclities",
     :select-cities        "Select cities",
     :subsidies            "Subsidies",
     :select-interval      "Select interval",
     :bullet1              "Economic Figures of Sport and Youth sector",
     :area-m2-avg          "Average area m²",
     :age-structure        "Construction years",
     :length-km-max        "Max route length km",
     :total-amount-1000e   "Total amount (€1000)",
     :city-stats           "City statistics"},
    :pool-structures
    {:concrete         "Concrete",
     :hardened-plastic "Hardened plastic",
     :steel            "Steel"},
    :map
    {:retkikartta-checkbox-reminder
     "Remember to tick \"May be shown in ExcursionMap.fi\" later in sports facility properties.",
     :zoom-to-user           "Zoom to my location",
     :remove                 "Remove",
     :modify-polygon         "Modify area",
     :draw-polygon           "Add area",
     :retkikartta-problems-warning
     "Please fix problems displayed on the map in case this route should be visible also in Retkikartta.fi",
     :edit-later-hint        "You can modify geometries later",
     :center-map-to-site     "Center map to sports-facility",
     :draw-hole              "Add hole",
     :split-linestring       "Split",
     :delete-vertices-hint
     "Vertices can be deleted by pressing alt-key and clicking.",
     :travel-direction       "Define travel direction"
     :route-part-difficulty  "Set route section difficulty"
     :calculate-route-length "Calculate route length",
     :calculate-area         "Calculate area",
     :calculate-count        "Calculate count automatically"
     :remove-polygon         "Remove area",
     :modify-linestring      "Modify route",
     :download-gpx           "Download GPX",
     :add-to-map             "Add to map",
     :bounding-box-filter    "Search from map area",
     :remove-linestring      "Remove route",
     :draw-geoms             "Draw",
     :confirm-remove
     "Are you sure you want to delete selected geometry?",
     :draw                   "Draw",
     :draw-linestring        "Add route",
     :modify                 "You can move the point on the map",
     :zoom-to-site           "Zoom map to sports facility's location",
     :kink
     "Self intersection. Please fix either by re-routing or splitting the segment.",
     :zoom-closer            "Please zoom closer"},
    :supporting-structures
    {:brick           "Brick",
     :concrete        "Concrete",
     :concrete-beam   "Concrete beam",
     :concrete-pillar "Concrete pillar",
     :solid-rock      "Solid rock",
     :steel           "Steel",
     :wood            "Wood"},
    :owner
    {:unknown                "Unknown",
     :municipal-consortium   "Municipal consortium",
     :other                  "Other",
     :company-ltd            "Company ltd",
     :city                   "City",
     :state                  "State",
     :registered-association "Registered association",
     :foundation             "Foundation",
     :city-main-owner        "City main owner"},
    :menu
    {:frontpage "Home",
     :headline  "LIPAS",
     :jyu       "University of Jyväskylä",
     :main-menu "Main menu"},
    :dryer-types
    {:cooling-coil "Cooling coil", :munters "Munters", :none "None"},
    :physical-units
    {:mm            "mm",
     :hour          "h",
     :m3            "m³",
     :m             "m",
     :temperature-c "Temperature °C",
     :l             "l",
     :mwh           "MWh",
     :m2            "m²",
     :celsius       "°C"},
    :lipas.swimming-pool.water-treatment
    {:activated-carbon? "Activated carbon",
     :filtering-methods "Filtering methods",
     :headline          "Water treatment",
     :ozonation?        "Ozonation",
     :uv-treatment?     "UV-treatment"},
    :statuses {:edited "{1} (edited)"},
    :lipas.user
    {:email                     "Email",
     :permissions               "Permissions",
     :permissions-example       "Access to update Jyväskylä ice stadiums.",
     :saved-searches            "Saved searches",
     :report-energy-and-visitors
     "Report visitors and energy consumption",
     :permission-to-cities      "You have permission to following cities:",
     :password                  "Password",
     :lastname                  "Last name",
     :save-report               "Save template",
     :sports-sites              "My sites",
     :permission-to-all-cities  "You have permission to all cities",
     :username                  "Username",
     :history                   "History",
     :saved-reports             "Saved templates",
     :contact-info              "Contact info",
     :permission-to-all-types   "You have permission to all types",
     :requested-permissions     "Requested permissions",
     :email-example             "email@example.com",
     :permission-to-portal-sites
     "You have permission to following sports facilities:",
     :permissions-help          "Describe what permissions you wish to have",
     :permission-to-activities  "You have permission to the following activities"
     :report-energy-consumption "Report energy consumption",
     :firstname                 "First name",
     :save-search               "Save search",
     :view-basic-info           "View basic info",
     :no-permissions
     "You don't have permission to publish changes to  any sites.",
     :username-example          "tane12",
     :permission-to-types       "You have permission to following types:"},
    :heat-recovery-types
    {:liquid-circulation   "Liquid circulation",
     :plate-heat-exchanger "Plate heat exchanger",
     :thermal-wheel        "Thermal wheel"}}})
