(ns lipas.i18n.fi)

(def translations
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
   {:name            "Liikuntapaikkatyyppi",
    :main-category   "Pääryhmä",
    :main-categories "Pääryhmät"
    :sub-category    "Alaryhmä",
    :sub-cateogires  "Alaryhmät"
    :type-code       "Tyyppikoodi"
    :geometry        "Geometria",
    :count           "Tyyppejä yhteensä"
    :Point           "Piste",
    :LineString      "Reitti",
    :Polygon         "Alue"},
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
    :users                      "Käyttäjät"
    :organizations              "Organisaatiot"},
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
    :results               "Hakutulokset"
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
   {:back                     "Takaisin"
    :duplicate                "Kopioi",
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
    :properties2            "Ominaisuudet",
    :property               "Ominaisuus"
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
   :lipas.properties
   {:allowed-values "Mahdolliset arvot"
    :value          "Arvo"
    :label          "Nimi"
    }
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
                        :ptv-manager        "PTV-käyttäjä"
                        :org-admin          "Organisaatio admin"
                        :org-user           "Organisaatio käyttäjä"}
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
    :thermal-wheel        "Pyörivä"}})
