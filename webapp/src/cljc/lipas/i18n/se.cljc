(ns lipas.i18n.se)

(def translations
  {:sport
   {:description
    "LIPAS är en landsomfattande databas för offentliga finländska idrottsplatser.",
    :headline "Idrottsanläggningar",
    :open-interfaces "Öppna gränssnitt",
    :up-to-date-information
    "Aktuell data om finska idrottsanläggningar",
    :updating-tools "Uppdateringsverktyg",
    :analysis-tools "Liikuntaolosuhteiden analysointityökalut"},
   :confirm
   {:discard-changes? "Vill du förkasta ändringar?",
    :headline "Bekräftelse",
    :no "Nej",
    :delete-confirm "Vill du verkligen ta bort raden?",
    :resurrect? "Vill du spara grunddata?",
    :save-basic-data? "Vill du spara grunddata?",
    :yes "Ja"},
   :lipas.swimming-pool.saunas
   {:accessible? "Tillgängligt",
    :add-sauna "Lägg till en bastu",
    :edit-sauna "Redigera bastun",
    :headline "Bastur",
    :men? "Män",
    :women? "Kvinnor"},
   :slide-structures
   {:concrete "Betong",
    :hardened-plastic "Öppna gränssnitt",
    :steel "Stål"},
   :lipas.swimming-pool.conditions
   {:open-days-in-year "Fredagar",
    :open-hours-mon "Måndagar",
    :headline "Öppettider",
    :open-hours-wed "Onsdagar",
    :open-hours-thu "Torsdagar",
    :open-hours-fri "Fredagar",
    :open-hours-tue "Tisdagar",
    :open-hours-sun "Söndagar",
    :daily-open-hours "Dagliga öppettider",
    :open-hours-sat "Lördagar"},
   :ptv.integration.description
   {:lipas-managed-ptv-fields "Använd en separat beskrivning",
    :lipas-managed-comment-field
    "Använd ytterligare informationsfältet för idrottsplatsen.",
    :lipas-managed-comment-field-helper
    "Beskrivningen av serviceplatsen underhålls i idrottsanläggningens ytterligare informationsfält. Sammanfattningen är det första stycket som separeras med enter-tangenten i informationsfältet.",
    :lipas-managed-ptv-fields-helper
    "Beskrivningen och sammanfattningen underhålls separat från idrottsanläggningen i det här verktyget. Beskrivningsförslag kan automatiskt genereras med hjälp av artificiell intelligens.",
    :ptv-managed "Uppdatera inte beskrivningarna.",
    :ptv-managed-helper
    "Beskrivningen och sammanfattningen underhålls i PTV. Lipas ändrar eller uppdaterar inte beskrivningarna."},
   :lipas.ice-stadium.ventilation
   {:dryer-duty-type "Gatuadress",
    :dryer-type "Kommun",
    :heat-pump-type "Gatuadress",
    :heat-recovery-efficiency "Kommun",
    :heat-recovery-type "Gatuadress"},
   :swim
   {:description "",
    :latest-updates "Timme",
    :headline "Simhallsportal",
    :basic-data-of-halls "Simhallsportal",
    :updating-basic-data "Information",
    :edit "Simhallsportal",
    :entering-energy-data "Datum",
    :list "Energi information",
    :visualizations "Energi information"},
   :ptv.tools.ai
   {:start-helper
    "Du kan justera de förslag som genereras av AI i takt med att de färdigställs.",
    :canceling "Avbryter... Vänta ett ögonblick.",
    :start "Starta",
    :headline
    "Skapa beskrivningar av servicepunkter med hjälp av artificiell intelligens."},
   :ptv.wizard
   {:export-service-locations-to-ptv "Skicka till PTV",
    :integrate-service-locations
    "Skapa servicepunkter från idrottsanläggningar.",
    :unselect-helper
    "Om du inte vill exportera ett visst objekt kan du öppna det i panelen till höger och inaktivera reglaget.",
    :all-services-exist
    "Alla nödvändiga service är i PTV. Fantastiskt!",
    :generate-descriptions-helper3
    "Servicepunkter kan upprättas i PTV när de har minst en finsk beskrivning och sammanfattning. LIPAS kompletterar serviceklass, nyckelord och andra metadata automatiskt.",
    :service-channel-name-conflict
    "Det finns redan en servicepunkt med namnet {1} i PTV.",
    :assign-services-to-sports-sites
    "Koppla idrottsanläggningar till tjänsterna.",
    :generate-descriptions-helper2
    "Tryck på knappen 'Skapa beskrivningar' för att automatiskt skapa beskrivningar med hjälp av artificiell intelligens. Beskrivningarna skapas baserat på de idrottsanläggningar som är sparade i LIPAS.",
    :services-to-add "Service att upprätta",
    :attach-to-conflicting-service-channel
    "Koppla till denna servicepunkt",
    :export-services-to-ptv "Skicka till PTV",
    :generate-descriptions-helper1
    "Tjänster kan upprättas i PTV när de har minst en finsk beskrivning och sammanfattning. LIPAS kompletterar serviceklass, nyckelord och andra metadata automatiskt.",
    :generate-descriptions "Skapa beskrivningar"},
   :home-page
   {:description
    "LIPAS-system innehåller information av idrottsanläggningar, idrottsrutter och friluftsregioner. Data är öppen CC4.0 International.",
    :headline "Startsida"},
   :ice-energy
   {:finhockey-link "El",
    :headline "Bensin",
    :description "",
    :energy-calculator ""},
   :filtering-methods
   {:activated-carbon "Kol",
    :coal "Kol",
    :membrane-filtration "Annat",
    :open-sand "Annat",
    :other "Annat",
    :precipitation "Beskrivning",
    :pressure-sand "Kommentar",
    :multi-layer-filtering ""},
   :open-data
   {:description "Länkar och information om gränssnitten",
    :headline "Öppna data",
    :rest "REST",
    :wms-wfs "WMS & WFS"},
   :lipas.swimming-pool.pool
   {:accessibility "Tillgänglighet",
    :outdoor-pool? "Utomhus simbassäng"},
   :pool-types
   {:multipurpose-pool "Allaktivitetsbassäng",
    :whirlpool-bath "Bubbelbad",
    :main-pool "Huvudsbassäng",
    :therapy-pool "Terapibassäng",
    :fitness-pool "Konditionsbassäng",
    :diving-pool "Hoppbassäng",
    :childrens-pool "Barnbassäng",
    :paddling-pool "Plaskdamm",
    :cold-pool "Kallbassäng",
    :other-pool "Annan bassäng",
    :teaching-pool "Undervisningsbassäng"},
   :lipas.ice-stadium.envelope
   {:headline "",
    :insulated-ceiling? "",
    :low-emissivity-coating? "",
    :insulated-exterior? "",
    :base-floor-structure ""},
   :disclaimer
   {:headline "OBS!",
    :test-version "Automatisk",
    :data-ownership
    "LIPAS-geografiska informationssystemets datalager ägs och förvaltas av Jyväskylä universitet. Jyväskylä universitet förbehåller sig rätten till allt innehåll, information och material som lagts till i systemet och som skapats under systemets utveckling. Användaren som lägger till material i systemet ansvarar för att materialet är korrekt och inte kränker tredje parts upphovsrätt. Jyväskylä universitet ansvarar inte för krav från tredje part avseende materialet. Genom att lägga till data i LIPAS-geografiska informationssystemet anses användaren ha godkänt användningen av det tillagda materialet i systemet som en del av datalagret. De data som lagras i LIPAS-databasen är öppna och fritt tillgängliga enligt CC 4.0 Erkännande-licensen."},
   :admin
   {:private-foundation "Privat / stiftelse",
    :city-other "Kommun / annat",
    :unknown "Okänt",
    :municipal-consortium "Samkommun",
    :private-company "Privat / företag",
    :private-association "Privat / förening",
    :other "Annat",
    :city-technical-services "Kommun / teknisk väsende",
    :city-sports "Kommun/ idrottsväsende",
    :state "Stat",
    :city-education "Kommun / utbildingsväsende"},
   :accessibility
   {:lift "Lyft i bassängen",
    :slope "Ramp",
    :mobile-lift "",
    :low-rise-stairs ""},
   :general
   {:description "Beskrivning",
    :hall "Hall",
    :women "Kvinnor",
    :headline "Rubrik",
    :total-short "Totalt",
    :done "Färdig",
    :updated "Uppdaterad",
    :name "Namn",
    :reported "Rapporterad",
    :type "Typ",
    :last-modified "Senaste redigerad",
    :here "här",
    :less "Mindre",
    :event "Händelse",
    :structure "Struktur",
    :general-info "Allmänna uppgifter",
    :comment "Kommentar",
    :measures "Mått",
    :age-anonymized "Ålder anonymiserad",
    :more "Mer",
    :men "Män"},
   :dryer-duty-types {:automatic "Automatisk", :manual "Manual"},
   :map.tools.simplify {:headline "Förenkla geometrier"},
   :ptv.tools.generate-services {:headline "Skapa PTV-service"},
   :ptv.integration.default-settings
   {:helper
    "Standardinställningarna kan skrivas över genom att ange objektspecifika inställningar på fliken Idrottsanläggningar.",
    :headline "Standardinställningar"},
   :swim-energy
   {:description "Information",
    :headline "Energi information",
    :headline-short "Information",
    :suh-link "Datum",
    :ukty-link "Slutade"},
   :time
   {:two-years-ago "För 2 år sedan",
    :date "Datum",
    :hour "Timme",
    :this-month "Den här månaden",
    :time "Tid",
    :less-than-hour-ago "För mindre än en timme sedan",
    :start "Började",
    :this-week "Den här veckan",
    :today "I dag",
    :month "Månad",
    :long-time-ago "För länge sedan",
    :year "År",
    :just-a-moment-ago "För en stund sedan",
    :yesterday "I går",
    :three-years-ago "För 3 år sedan",
    :end "Slutade",
    :this-year "Det här året",
    :last-year "Förra året"},
   :ice-resurfacer-fuels
   {:LPG "El",
    :electicity "El",
    :gasoline "Bensin",
    :natural-gas "Naturgas",
    :propane "Propan"},
   :sports-site.elevation-profile
   {:headline "Höjdprofil",
    :distance-from-start-m "Avstånd från start (m)",
    :distance-from-start-km "Avstånd från start (km)",
    :height-from-sea-level-m "Höjd över havet (m)",
    :total-ascend "Total stigning",
    :total-descend "Total nedstigning"},
   :ice-rinks {:headline "Du har admin rättigheter."},
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
   {:main-categories "Huvudkategorier",
    :name "Idrottsanläggningstyp",
    :type-code "Typkod",
    :geometry "Geometri",
    :Point "Punkt",
    :main-category "Huvudkategori",
    :sub-category "Underkategori",
    :count "Totalt antal typer",
    :LineString "Rutt",
    :Polygon "Område",
    :sub-cateogires "Uderkategorier"
    :sports-site-types "Typ av idrottsanläggning"}
   :ptv.service
   {:classes "Serviceklasser",
    :show-only-lipas-managed
    "Visa endast tjänster som är kopplade till LIPAS."},
   :map.overlay
   {:light-traffic "Gång- och cykelvägar",
    :mml-kiinteisto "Fastighetsgränser",
    :tooltip "Andra kartlager",
    :retkikartta-snowmobile-tracks "Forststyrelsens snöskoterleder"
    :mml-property-identifiers "Fastighetsbeteckningar"
    :municipal-boundaries "Kommungränser"}
   :duration
   {:hour "timmar", :month "månader", :years "år", :years-short "år"},
   :size-categories
   {:large "Betong",
    :small "Liten tävlingshall > 500 person",
    :competition "Tävlingshall < 3000 pers."},
   :lipas.admin
   {:access-all-sites "Du har admin rättigheter.",
    :confirm-magic-link
    "Är du säker på att du vill skicka magic link till {1}?",
    :headline "Admin",
    :magic-link "Magic link",
    :select-magic-link-template "Välj brev",
    :send-magic-link "Skicka magic link till användare",
    :users "Användare"},
   :ptv.integration.service-channel
   {:lipas-managed
    "LIPAS definierar servicepunkterna (rekommenderas).",
    :manual-helper
    "Idrottsanläggningen kopplas manuellt till den befintliga servicepunkten i det här verktyget. Kopplingen måste göras för varje idrottsanläggning en gång.",
    :lipas-managed-helper
    "LIPAS skapar en servicepunkt för varje idrottsanläggning i PTV och kopplar automatiskt idrottsanläggningarnas uppgifter till dem."},
   :loi
   {:headline "Annat objekt"
    :category "Kategori",
    :status "Status",
    :type "Objekttyp",
    :type-and-category-disclaimer
    "Kategori och typ måste väljas innan de läggs till på kartan"
    :other-target "Annat objekt"}
   :lipas.ice-stadium.refrigeration
   {:headline "",
    :refrigerant-solution-amount-l "",
    :individual-metering? "",
    :original? "",
    :refrigerant "",
    :refrigerant-solution "",
    :condensate-energy-main-targets "",
    :power-kw "",
    :condensate-energy-recycling? "",
    :refrigerant-amount-kg ""},
   :lipas.building
   {:headline "Byggnad",
    :total-volume-m3 "Areal av vatten m²",
    :staff-count "Antalet personal",
    :piled? "",
    :heat-sections? "Antalet personal",
    :total-ice-area-m2 "Areal av is m²",
    :main-construction-materials "",
    :main-designers "Antalet personal",
    :total-pool-room-area-m2 "Areal av vatten m²",
    :heat-source "",
    :total-surface-area-m2 "",
    :total-water-area-m2 "Areal av vatten m²",
    :ceiling-structures "Byggnad",
    :supporting-structures "Areal av is m²",
    :seating-capacity "Antalet personal"},
   :heat-pump-types
   {:air-source "",
    :ground-source "",
    :air-water-source "",
    :exhaust-air-source "",
    :none ""},
   :search
   {:table-view "Visa resultaten i tabellen",
    :headline "Sökning",
    :results-count "{1} resultat",
    :harrastuspassi-filter "Harrastuspassi.fi-platser",
    :placeholder "Sök...",
    :retkikartta-filter "Retkikartta.fi-platser",
    :filters "Filtrera sökning",
    :search-more "Sök mer...",
    :page-size "Visa",
    :search "Sök",
    :permissions-filter "Visa platser som jag kan redigera",
    :display-closest-first "Visa närmaste platser först",
    :list-view "Visa resultaten i listan",
    :pagination "Resultaten {1}-{2}",
    :school-use-filter "Idrottsanläggningar nära skolor",
    :clear-filters "Avmarkera filter",
    :results "Sökresultat"},
   :map.tools
   {:simplifying "Förenklingsverktyg valt",
    :route-part-difficulty-tooltip
    "Verktyg för val av svårighetsgrad för ruttsektion valt",
    :simplify "Förenkla",
    :drawing-hole-tooltip "Hålteckningsverktyg valt",
    :deleting-tooltip "Raderingsverktyg valt",
    :splitting-tooltip "Delningsverktyg valt",
    :drawing-tooltip "Ritverktyg valt",
    :importing-tooltip "Importverktyg valt",
    :travel-direction-tooltip "Reseriktningverktyg valt",
    :download-backup-tooltip "Ladda ner säkerhetskopia",
    :restore-backup-tooltip "Återställ säkerhetskopia",
    :selecting "Valverktyg valt",
    :edit-tool "Redigeringsverktyg"},
   :partners {:headline "Tillsammans med"},
   :actions
   {:duplicate "Kopiera",
    :resurrect "Öppna huvudmeny",
    :select-year "Välj år",
    :select-owners "Välj ägare",
    :select-admins "Välj administratörer",
    :select-tool "Välj verktyg",
    :save-draft "Spara utkast",
    :redo "Gör igen",
    :open-main-menu "Öppna huvudmeny",
    :back-to-listing "Till listan",
    :filter-surface-materials "Avgränsa enligt ytmaterial",
    :browse "flytta dig",
    :select-type "Välj typ",
    :edit "Redigera",
    :filter-construction-year "Avgränsa enligt byggnadsår",
    :submit "Skicka",
    :choose-energy "Välj energi",
    :delete "Radera",
    :browse-to-map "Flytta dig till kartan",
    :save "Spara",
    :close "Stäng",
    :select-all "Välj allt",
    :filter-area-m2 "Avgränsa enligt areal m²",
    :show-account-menu "Öppna kontomeny",
    :fill-required-fields "Fyll i obligatoriska fält",
    :undo "Ångra",
    :browse-to-portal "Flytta dig till portalen",
    :download-excel "Ladda",
    :fill-data "Fyll i informationen",
    :back "Tillbaka",
    :select-statuses "Status",
    :select-cities "Välj kommuner",
    :select-hint "Välj...",
    :discard "Förkasta",
    :more "Mer...",
    :cancel "Avbryta",
    :select-columns "Välj datafält",
    :add "Tillägg ",
    :show-all-years "Visa alla år",
    :download "Ladda ner",
    :select-hall "Välj hall",
    :clear-selections "Ta bort val",
    :select "Välj",
    :select-types "Välj typer"},
   :newsletter
   {:subscribe "Prenumerera på nyhetsbrevet",
    :subscribe-short "Prenumerera",
    :subscription-failed "Prenumerationen misslyckades. Försök igen.",
    :subscription-success "Nyhetsbrevet beställt!"},
   :dimensions
   {:area-m2 "Areal m²",
    :depth-max-m "Djup max m",
    :depth-min-m "Djup min m",
    :length-m "Längd m",
    :surface-area-m2 "Areal m²",
    :volume-m3 "Volym m3",
    :width-m "Bredd m"},
   :login
   {:headline "Logga in",
    :login-here "här",
    :login-with-password "Logga in med lösenord",
    :password "Lösenord",
    :logout "Logga ut",
    :username "E-post / användarnamn",
    :login-help
    "Om du har ett användarnamn till LIPAS, du kan också logga in med e-post",
    :login "Logga in",
    :magic-link-help
    "Om du har ett användarnamn till LIPAS, du kan beställa en logga in-länk till din e-post.",
    :order-magic-link "Skicka länken till min e-post",
    :login-with-magic-link "Logga in med e-post",
    :bad-credentials "Lösenord eller användarnamn är felaktigt",
    :magic-link-ordered
    "Länken har skickats och den är snart i din e-post. Kolla också spam!",
    :username-example "paavo.paivittaja@kunta.fi",
    :forgot-password? "Glömt ditt lösenord?"},
   :lipas.ice-stadium.conditions
   {:open-months "",
    :headline "",
    :ice-resurfacer-fuel "",
    :stand-temperature-c "",
    :ice-average-thickness-mm "",
    :air-humidity-min "",
    :daily-maintenances-weekends "",
    :air-humidity-max "",
    :daily-maintenances-week-days "",
    :maintenance-water-temperature-c "",
    :ice-surface-temperature-c "",
    :weekly-maintenances "",
    :skating-area-temperature-c "",
    :daily-open-hours "",
    :average-water-consumption-l ""},
   :ptv.tools.ai.sports-sites-filter
   {:no-existing-description "Alla utan beskrivning",
    :all "Allt",
    :sync-enabled "Valda för export",
    :sync-enabled-no-existing-description
    "Valda för export som saknar beskrivning",
    :label "Välj idrottsanläggningar"},
   :map.demographics
   {:headline "Befolkning",
    :tooltip "analysverktyg",
    :helper-text "Välj idrottsanläggning på kartan.",
    :copyright1
    "Befolkningsinformation: Statistikcentralen 2019 årets",
    :copyright2 "1 km * 1 km data",
    :copyright3 "licens",
    :copyright4
    ", samt 250x250m rutdatamaterial från år 2022 med sluten licens."},
   :ptv.integration.service
   {:lipas-managed-helper
    "LIPAS etablerar de nödvändiga tjänsterna i PTV enligt DVV:s rekommendationer och kopplar automatiskt servicepunkterna (idrottsanläggningarna) till dem.",
    :manual-helper
    "Idrottsanläggningarna kopplas manuellt till de befintliga tjänsterna i PTV i det här verktyget. Kopplingen måste göras för varje idrottsanläggning en gång.",
    :lipas-managed "LIPAS definierar tjänsterna (rekommenderas)"},
   :utp
   {:headline "Friluftsdatatjänst",
    :video "Video",
    :length-km "Längd km",
    :unit "enhet",
    :add-subroute-ok "OK",
    :highlight "Höjdpunkt",
    :add-subroute "Lägg till delrutt",
    :select-route-parts-on-map "Välj ruttdelar på kartan",
    :add-video "Lägg till video",
    :add-highlight "Lägg till höjdpunkt",
    :delete-route-prompt
    "Är du säker på att du vill ta bort den här rutten?",
    :finish-route-details "Rutt färdig",
    :route-is-made-of-subroutes
    "Rutten består av flera separata sektioner",
    :photo "Foto",
    :read-only-disclaimer
    "Det finns för närvarande endast en redigeringsvy för aktiviteter. Logga in och gå till redigeringsläget från pennsymbolen.",
    :link "Länk",
    :add-contact "Lägg till kontaktuppgift",
    :custom-rules "Egen regler",
    :custom-rule "Egna tillstånd, regler, anvisningar",
    :add-photo "Lägg till foto"},
   :lipas.swimming-pool.slides
   {:add-slide "Lägg till en rutschbana",
    :edit-slide "Redigera rutschbanan",
    :headline "Rutschbanor"},
   :notifications
   {:get-failed "Datasökning misslyckades.",
    :save-failed "Sparningen misslyckades",
    :save-success "Sparningen lyckades",
    :ie
    "Internet Explorer stöds inte. Vi rekommenderar du använder t.ex. Chrome, Firefox eller Edge.",
    :thank-you-for-feedback "Tack för feedbacken!"},
   :lipas.swimming-pool.energy-saving
   {:filter-rinse-water-heat-recovery? "Gym",
    :headline "Andra tjänster",
    :shower-water-heat-recovery? "Gym"},
   :ice-form
   {:headline "Naturgas", :headline-short "El", :select-rink "Bensin"},
   :restricted {:login-or-register "Logga in eller registrera dig"},
   :lipas.ice-stadium.rinks
   {:headline "Planen",
    :rink2-length "2. planens längd m",
    :rink2-area-m2 "2. planens areal m²",
    :rink1-area-m2 "1. planens areal m²",
    :rink3-width "3. planens bredd m",
    :rink1-length "1. planens längd m",
    :edit-rink "Redigera planen",
    :rink3-area-m2 "3. planens areal m²",
    :rink3-length "3. planens längd m",
    :add-rink "Lägg till en plan",
    :rink2-width "2. planens bredd m",
    :rink1-width "1. planens bredd m"},
   :lipas.sports-site
   {:properties "Ytterligare information",
    :delete-tooltip "Ta bort idrottsanläggningen...",
    :headline "Idrottsanläggning",
    :new-site-of-type "Ny {1}",
    :address "Adress",
    :new-site "Ny idrottsplats",
    :phone-number "Telefonnummer",
    :admin "Administratör",
    :accessibility "Tillgänglighet",
    :surface-materials "Ytmaterial",
    :www "Webbsida",
    :add-new-planning
    "Lägg till en motions- eller friluftsplats i utkastläge",
    :property "Funktion",
    :name "Namn på finska",
    :reservations-link "Bokningar",
    :construction-year "Byggår",
    :type "Typ",
    :delete "Ta bort {1}",
    :planning-site "Utkast",
    :creating-planning-site
    "Du håller på att lägga till en plats i utkastläge för analysverktyg.",
    :admin-helper-text "Enhet ansvarig för underhåll av objektet",
    :renovation-years "Renoveringsår",
    :name-localized-se "Namn på svenska",
    :properties2 "Funktioner",
    :status "Status",
    :id "LIPAS-ID",
    :details-in-portal "Visa alla ytterligare information",
    :no-permission-tab
    "Du har inte behörighet att redigera informationen på denna flik",
    :comment "Ytterligare information",
    :ownership "Ägare",
    :owner-helper-text "Ägare av strukturer eller tjänster i objektet",
    :name-short "Namn",
    :basic-data "Grunddata",
    :delete-reason "Orsak",
    :event-date "Redigerad",
    :name-localized-en "Namn på engelska",
    :email-public "E-post (publik)",
    :add-new "Lägg till en idrottsanläggning",
    :contact "Kontaktinformation",
    :contact-helper-text
    "Allmänt kontaktuppgifter för organisationen som ansvarar för anläggningen, tjänsterna eller strukturerna.",
    :owner "Ägare",
    :marketing-name "Varumärkesnamn"},
   :analysis
   {:elementary-and-high-school "Grund- och gymnasieskola",
    :description
    "Med analysverktyget kan du utvärdera utbudet och tillgängligheten av motionsförhållanden genom att jämföra avståndet och restiderna till motionsplatser i förhållande till andra motionsplatser, befolkningen och läroanstalter.",
    :filter-types "Filtrera efter typ",
    :direct "Fågelvägen",
    :headline "Analysverktyg (beta)",
    :settings-zones "Avstånd och restider",
    :by-bicycle "Med cykel",
    :daycare "Förskoleenhet",
    :by-car "Med bil",
    :mean "Medelvärde",
    :diversity-help2
    "Verktyget använder indelning i postnummerområden som standard. Indelningen kan också göras utifrån annan befintlig områdesindelning genom att importera önskad geometrisk fil (Shapefile, GeoJSON eller KML).",
    :settings-map "Objekt som visas på kartan",
    :zone "Zon",
    :elementary-school "Grundskola",
    :reachability "Tillgänglighet",
    :special-school "Specialskola",
    :diversity-help1
    "Med mångfaldsverktyget kan du utvärdera och jämföra mångfalden av motionsförhållanden i invånarnas närmiljö på rutnäts- och områdesnivå. Den mångfaldighetsindex som beräknas av mångfaldsverktyget beskriver hur mångfaldigt olika motionsaktivitetsmöjligheter invånaren kan nå inom den valda avståndet längs väg- och stignätet (förutsatt 800 m). Ju högre indexvärde, desto fler olika motionsplatser finns i invånarnas närmiljö.",
    :analysis-areas "Analysområden",
    :description2
    "Befolkningsdata används från Statistikcentralens 250x250m och 1x1km rutnätsdata, som visar befolkningsfördelningen i tre åldersgrupper (0-14, 15-65, 65-) i varje ruta.",
    :mode "Typvärde",
    :analysis-buffer "Analysområde",
    :description4
    "Uppgifter om läroanstalters namn och plats baseras på öppna data från Statistikcentralen. Uppgifter om förskoleenheter baseras på data som samlats in och tillhandahållits av LIKES.",
    :settings "Inställningar",
    :schools "Skolor",
    :close "Stäng analysverktygen",
    :diversity "Mångfald"
    :heatmap "Värmekarta"
    :by-foot "Till fots",
    :description3
    "Beräkningen av restider med olika färdsätt (till fots, cykel, bil) baseras på öppna OpenStreetMap-data och OSRM-verktyget.",
    :categories "Kategorier",
    :median "Median",
    :settings-help
    "Analysområdet bestäms av den största avståndzonen. Restider beräknas heller inte utanför detta område.",
    :categories-help
    "Motionsplatskategorier som tillhör samma kategori påverkar mångfaldighetsindexet endast en gång.",
    :population "Befolkning",
    :population-weighted-mean "Befolkningsvägd mångfaldighetsindex",
    :diversity-idx "Mångfaldighetsindex"
    :diversity-help3
    "Mångfalden beräknas för varje befolkningsruta utifrån de motionsplatser som påverkar mångfalden och som är belägna inom det angivna gångavståndet längs vägnätet från befolkningsrutans mittpunkt."
    :zones "Zoner",
    :diversity-grid "Resultatgrid",
    :distance "Avstånd",
    :add-new "Lägg till ny",
    :high-school "Gymnasium",
    :travel-time "Restid",
    :results "Resultat"
    :use-postal-code-areas "Använd postnummerområden"
    :import-own-areas "Importera egna områden"
    :cumulative-results "Kumulativa resultat"
    :ready-classifications "Färdiga klassificeringar"
    :default "Standard"
    :constraints "Begränsningar"
    :in-use-summer "I bruk på sommaren"
    :in-use-winter "I bruk på vintern"
    :in-use-year-round "I bruk året runt"
    :new-category "Ny kategori"
    :restore-default-categories "Återställ standardkategorier"
    :save-categories "Spara kategorier"
    ;; New translations for diversity view
    :calculate-all "Beräkna alla"
    :calculate-diversity "Beräkna mångfald"
    :factor "Faktor"
    :delete-category "Ta bort kategori"
    :confirm-restore-defaults "Vill du verkligen återställa standardkategorierna?"
    :save-categorization "Spara kategorisering"
    :categorization-name "Kategoriseringens namn"
    :format "Format"
    :download-areas "Ladda ner områden"
    :download-grid "Ladda ner rutnät"
    :download-categories "Ladda ner kategorier"
    :download-parameters "Ladda ner parametrar"
    :population-chart "Befolkning"
    :diversity-index-chart "Mångfaldsindex"
    :total-population-chart "Totala befolkningen"
    :population-weighted-diversity-chart "Befolkningsvägd mångfaldsindex"
    :name-chart "Namn"},
   :status
   {:active "Aktiv",
    :planned "Planerad",
    :incorrect-data "Fel information",
    :out-of-service-permanently "Permanent ur funktion",
    :out-of-service-temporarily "Tillfälligt ur funktion"},
   :register
   {:headline "Registrera",
    :link "Registrera här",
    :thank-you-for-registering
    "Tack för registrering! Du vill få e-posten snart."},
   :map.address-search {:title "Sök address", :tooltip "Sök address"},
   :ice-comparison {:headline "Jämförelse av hallar"},
   :lipas.visitors
   {:headline "Användare",
    :not-reported "Glömt lösenordet?",
    :not-reported-monthly "Lösenord eller användarnamn är felaktigt",
    :spectators-count "Glömt ditt lösenord?",
    :total-count "Lösenord eller användarnamn är felaktigt",
    :monthly-visitors-in-year ""},
   :lipas.energy-stats
   {:headline "Värme MWh",
    :energy-reported-for "Värme MWh",
    :report "",
    :disclaimer "El MWh",
    :reported "Vatten m³",
    :cold-mwh "El + värme MWh",
    :hall-missing? "Vatten m³",
    :not-reported "Vatten m³",
    :water-m3 "Vatten m³",
    :electricity-mwh "El MWh",
    :heat-mwh "Värme MWh",
    :energy-mwh "El + värme MWh"},
   :map.resolve-address
   {:choose-address "Välj adress",
    :addresses "Närmaste adresserna",
    :helper-text1
    "Verktyget hämtar de närmaste adresserna baserat på idrottsläggningens geometri.",
    :tooltip "Hitta adress automatiskt",
    :helper-text2
    "Välj gatuadress efter bästa kunskap. Du kan komplettera husnumret efter att ha tryckt på OK-knappen. "},
   :map.basemap
   {:copyright "© Lantmäteriverket",
    :maastokartta "Terrängkarta",
    :ortokuva "Flygfoto",
    :taustakartta "Bakgrundskarta",
    :transparency "Bakgrundskartans genomskinlighet"},
   :lipas.swimming-pool.pools
   {:add-pool "Lägg till en bassäng",
    :edit-pool "Redigera bassängen",
    :headline "Bassänger",
    :structure "Struktur"},
   :ptv.integration.interval
   {:headline "Export till PTV",
    :label "När exporteras ändringarna till PTV",
    :immediate
    "Automatiskt när ändringar sparas i Lipas (rekommenderas).",
    :manual "Endast manuellt",
    :daily "Automatiskt en gång om dagen"},
   :condensate-energy-targets
   {:service-water-heating "Nej",
    :snow-melting "Vill du förkasta ändringar?",
    :track-heating "Vill du förkasta ändringar?",
    :maintenance-water-heating "",
    :hall-heating "",
    :other-space-heating ""},
   :refrigerants
   {:CO2 "CO2 (koldioxid)",
    :R134A "R134A",
    :R22 "R22",
    :R404A "R404A",
    :R407A "R407A",
    :R407C "R407C",
    :R717 "R717"},
   :lipas.properties
   {:label "Namn", :allowed-values "Möjliga värden.", :value "Värde"},
   :retkikartta
   {:disclaimer
    "När du kryssat för rutan ”Kan visas på Utflyktskarta.fi” flyttas uppgifterna om naturutflyktsmålet automatiskt till karttjänsten Utflyktskarta.fi som Forststyrelsen administrerar.\nUppgifter överförs till karttjänsten en gång i dygnet. Jag förbinder mig att uppdatera ändringar i uppgifterna om naturutflyktsmålet utan dröjsmål i Lipas.\nUtflyktsmålets administratör ansvarar för uppgifternas riktighet och utflyktsmålets säkerhet samt för svar på respons och för eventuella kostnader i samband med privata vägar."},
   :harrastuspassi
   {:disclaimer
    "När du kryssat för rutan ”Kan visas på Harrastuspassi.fi” flyttas uppgifterna om idrottsanläggningen automatiskt till Harrastuspassi.fi. Jag förbinder mig att uppdatera ändringar i uppgifterna om idrottsanläggningen utan dröjsmål i Lipas. Anläggningens administratör ansvarar för uppgifternas riktighet och anläggningens säkerhet. Uppgifterna visas i Harrastuspassi.fi om kommunen har kontrakt med Harrastuspassi.fi administratör för att använda Harrastuspassi.fi."},
   :reset-password
   {:change-password "Ändra lösenord",
    :enter-new-password "Skriv ett ny lösenord",
    :get-new-link "Bestella ny återställningslänk",
    :headline "Glömt lösenordet?",
    :helper-text
    "Vi ska skicka en återställningslänk till din e-post.",
    :password-helper-text "Lösenordet måste innehålla minst 6 tecken.",
    :reset-link-sent "Länken har skickats! Kolla din e-post.",
    :reset-success
    "Lösenordet har ändrats! Logga in med ditt nya lösenord."},
   :reports
   {:contacts "Kontaktuppgifter",
    :download-as-excel "Skapa Excel",
    :select-fields "Välj fält för rapport",
    :selected-fields "Vald fält",
    :shortcuts "Genvägar",
    :tooltip "Skapa Excel-rapport från resultaten",
    :file-format "Filformat",
    :excel-limit-exceeded
    "För stor Excel-fil (> 10 000 sökresultat). Begränsa sökningen eller välj ett annat filformat."},
   :heat-sources
   {:district-heating
    "Om du behöver ytterligare användarrättigheter, kontakt ",
    :private-power-station "Hjälp"},
   :map.import
   {:headline "Importera geometri",
    :geoJSON
    "Laddar upp .json file som innehåller GeoJSON FeatureCollect object. Koordinater måste vara i WGS84 koordinatsystem.",
    :gpx "Utgångsmaterial måste vara i WGS84 koordinatsystem.",
    :supported-formats "Filformat som passar är {1}",
    :no-geoms-of-type
    "Den angivna filen innehåller inga {1} geometrier.",
    :replace-existing? "Ersätt gammal geometri",
    :select-encoding "Välj teckenuppsättning",
    :tab-header "Importera från filen",
    :kml "Utgångsmaterial måste vara i WGS84 koordinatsystem.",
    :coords-not-in-finland-wgs84-bounds
    "Den angivna filen innehåller inte koordinater i WGS84 (EPSG:4326) koordinatsystemet. Kontrollera att ursprungsmaterialet är i rätt koordinatsystem.",
    :shapefile
    "Importera .shp .dbf och .prj filer i packade .zip filen.",
    :import-selected "Importera valda",
    :tooltip "Importera geometrier från filen",
    :unknown-format "Okänt filformat '{1}'",
    :unknown-error
    "Ett oväntat fel inträffade. Försök med en annan fil."},
   :error
   {:email-conflict "E-post är redan registrerad",
    :email-not-found "E-post är inte registrerad",
    :invalid-form "Korrigera röda fält",
    :no-data "Ingen information",
    :reset-token-expired
    "Lösenordet har inte ändrats. Länken har utgått.",
    :unknown "Ett okänt fel uppstod. :/",
    :user-not-found "Användare hittas inte.",
    :username-conflict "Användarnamnet redan används"},
   :reminders
   {:description
    "Meddelandet ska skickas till din e-post på vald tid.",
    :after-one-month "Om en månad",
    :placeholder "Kom ihåg att kolla idrottsanläggningen \"{1}\" {2}",
    :select-date "Välj datum",
    :tomorrow "I morgon",
    :title "Lägg till en påminnelse",
    :after-six-months "Om halv år",
    :in-a-year "Om ett år",
    :message "Meddelande",
    :in-a-week "Om en vecka"},
   :units
   {:days-in-year "dagar per år",
    :hours-per-day "timmar per dag",
    :pcs "st",
    :percent "%",
    :person "pers.",
    :times-per-day "gånger per dag",
    :times-per-week "gånger per vecka"},
   :lipas.energy-consumption
   {:contains-other-buildings? "",
    :headline "",
    :yearly "El MWh",
    :report "El MWh",
    :electricity "",
    :headline-year "",
    :monthly? "Vatten m³",
    :reported-for-year "Vatten m³",
    :monthly "",
    :operating-hours "Vatten m³",
    :not-reported "",
    :not-reported-monthly "",
    :heat "",
    :cold "Kommentar",
    :comment "Kommentar",
    :water "Vatten m³",
    :monthly-readings-in-year ""},
   :ice
   {:description "Stor tävlingshall > 3000",
    :large "Stor tävlingshall > 3000",
    :competition "Tävlingsishall < 3000 person",
    :headline "Ishallsportal",
    :video "Video",
    :comparison "Jämförelse av hallar",
    :size-category "Storlek kategori",
    :basic-data-of-halls "Grunddata av ishallar",
    :updating-basic-data "Uppdatering av grunddata",
    :entering-energy-data "Ishallsportal",
    :small "Liten tävlingshall > 500 person",
    :watch "Titta",
    :video-description "Titta"},
   :lipas.location
   {:headline "Position",
    :address "Gatuadress",
    :no-address "Kan inte att ange en gatuadress för målet.",
    :city "Kommun",
    :postal-code "Postnummer",
    :city-code "Kommunkod",
    :avi-area "RFV-område",
    :postal-office "Postkontor",
    :neighborhood "Kommundel",
    :province "Landskap"},
   :lipas.user.permissions
   {:admin? "Admin",
    :all-cities? "Rättighet till alla kommuner",
    :all-types? "Rättighet till alla typer",
    :cities "Kommuner",
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
                        :ptv-manager        "PTV-administratör"
                        :org-admin          "Organisationsadministratör"
                        :org-user           "Organisationsanvändare"}
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
   {:headline "Hjälp",
    :available-pages "Sidor i detta avsnitt",
    :permissions-help
    "Om du behöver ytterligare användarrättigheter, kontakt ",
    :permissions-help-body
    "Jag behöver användarrättigheter till följande platser:",
    :permissions-help-subject "Jag behöver mera användarrättigheter",
    :privacy-policy "Privacy policy",
    :manage-content "Redigera hjälpinnehåll"},
   :ptv.actions
   {:select-integration "Välj anslutningssätt",
    :export-disclaimer
    "Jag har läst beskrivningarna och vill att denna objekt skickas till PTV.",
    :select-service-channel "Välj servicepunkt",
    :select-languages "Välj språk",
    :select-service "Välj service",
    :generate-with-ai
    "Generera beskrivningar med artificiell intelligens",
    :export "Skicka till",
    :select-org "Välj organisation"},
   :ceiling-structures
   {:concrete "Betong",
    :double-t-beam "TT-bricka",
    :glass "Glas",
    :hollow-core-slab "Häll",
    :solid-rock "Häll",
    :steel "Stål",
    :wood "Trä"},
   :data-users
   {:data-user? "Använder du LIPAS-data?",
    :email-body
    "Trevligt att du använder LIPAS-data! Skriv här vem du är och hur du använder LIPAS. Använder du möjligen något av våra gränssnitt?",
    :email-subject "Vi använder också LIPAS-data",
    :headline "Några som använder LIPAS-data",
    :tell-us "Berättä om det för oss"},
   :lipas.floorball {:headline "Förhållanden"},
   :lipas.swimming-pool.facilities
   {:showers-men-count "Antalet duschar för män",
    :lockers-men-count "Antalet omklädningsskåp för män",
    :headline "Andra tjänster",
    :platforms-5m-count "Antalet 5 m hopplatser",
    :kiosk? "Kiosk / café",
    :hydro-neck-massage-spots-count "Antal av nackemassagepunkter",
    :lockers-unisex-count "Antalet unisex omklädningsskåp",
    :platforms-10m-count "Antalet 10 m hopplatser",
    :hydro-massage-spots-count "Antal av andra massagepunkter",
    :lockers-women-count "Antalet omklädningsskåp för kvinnor",
    :platforms-7.5m-count "Antalet 7.5 m hopplatser",
    :gym? "Gym",
    :showers-unisex-count "Antalet unisex duschar",
    :platforms-1m-count "Antalet 1 m hopplatser",
    :showers-women-count "Antalet duschar för kvinnor",
    :platforms-3m-count "Antalet 3 m hopplatser"},
   :ptv.name-conflict
   {:do-one-of-these "Gör en av dessa.",
    :opt4 "Arkivera den aktuella servicepunkten i PTV.",
    :opt3
    "Stäng av reglaget, så att denna objekt inte integreras i PTV.",
    :opt1
    "Koppla idrottsanläggningen till den befintliga servicepunkten genom att trycka på knappen 'Koppla till denna servicepunkt' (rekommenderas).",
    :opt2 "Välj önskad servicepunkt från väljaren nedan."},
   :sauna-types
   {:infrared-sauna "Infraröd bastu",
    :other-sauna "Annan bastu",
    :sauna "Bastu",
    :steam-sauna "Ångbastu"},
   :stats-metrics
   {:operational-expenses "Verksamhetskostnader",
    :deficit "Underskott för räkenskapsåret",
    :surplus "Överskott för räkenskapsåret",
    :investments "Investeringar",
    :subsidies "Understöd och bidrag från kommunen",
    :operational-income "Verksamhetsintäkter",
    :operating-incomes "Driftsintäkter",
    :operating-expenses "Driftskostnader",
    :net-costs "Nettokostnader"},
   :ptv
   {:description "Beskrivning",
    :service "Service",
    :sports-sites "Idrottsanläggningar",
    :tools "Massverktyg",
    :settings "Inställningar",
    :service-channels "Servicepunkt",
    :keywords "Nyckelord",
    :summary "Sammanfattning",
    :wizard "Idrifttagning",
    :loading-from-ptv "Hämtar information från PTV...",
    :descriptions "Beskrivningarna",
    :service-channel "Servicepunkt",
    :tooltip "Skicka till PTV",
    :services "Service"},
   :refrigerant-solutions
   {:CO2 "CO2",
    :H2ONH3 "H2O/NH3",
    :cacl "CaCl",
    :ethanol-water "Freezium",
    :ethylene-glycol "R134A",
    :freezium "Freezium",
    :water-glycol "R134A"},
   :user
   {:headline "Mitt konto",
    :admin-page-link "Gå till adminsidan",
    :promo1-link "Visa gymnastiksaler som jag kan uppdatera",
    :swimming-pools-link "Simhallsportal",
    :promo-headline "Aktuellt",
    :data-ownership "Användarvillkor och ansvar",
    :front-page-link "Gå till framsidan",
    :promo1-text "Öppna PDF",
    :ice-stadiums-link "Ishallsportal",
    :greeting "Hej {1} {2} !",
    :promo1-topic
    "OBS! EN UPPDATERING I KLASSIFICERING AV IDROTTS- OCH FRILUFTSPLATSER I LIPAS-SYSTEMET (11 januari 2022)"},
   :building-materials
   {:brick "Tegel",
    :concrete "Betong",
    :solid-rock "Häll",
    :steel "Stål",
    :wood "Trä"},
   :stats
   {:description
    "Ekonomiska uppgifter om idrotts- och ungdomsväsende. Kommunen kan observera hur kostnader utvecklas över tid.",
    :filter-types "Välj typer",
    :length-km-sum "Idrottsrutters totalt längd ",
    :headline "Statistik",
    :select-years "Välj år",
    :browse-to "Gå till statistiken",
    :select-issuer "Välj understödare",
    :select-unit "Välj måttenhet",
    :general-disclaimer-1
    "Lipas.fi idrottsanläggningar, friluftsleder och friluftsområden i Finland: Erkännande 4.0 Internationell (CC BY 4.0). https://creativecommons.org/licenses/by/4.0/deed.sv",
    :bullet3 "Bidrag",
    :general-disclaimer-2
    "Du har tillstånd att dela, kopiera och vidaredistribuera materialet oavsett medium eller format, bearbeta, remixa, transformera, och bygg vidare på materialet, för alla ändamål, även kommersiellt. Du måste ge ett korrekt erkännande om du använder Lipas-data, till exempel “Idrottsplatser: Lipas.fi, Jyväskylä Universitet, datum/år”.",
    :finance-stats "Ekonomiska uppgifter",
    :select-city "Välj kommun",
    :area-m2-min "Minimum idrottsareal m²",
    :filter-cities "Välj kommuner",
    :select-metrics "Välj storheter",
    :area-m2-count "Antal av platser med idrottsareal information",
    :show-ranking "Visa rankning",
    :age-structure-stats "Byggnadsår",
    :subsidies-count "Antal av bidrag",
    :area-m2-sum "Totalt idrottsareal m²",
    :select-metric "Välj storhet",
    :finance-disclaimer
    "Ekonomiska uppgifter av kommuners idrotts- och ungdomsväsende: Statistikcentralen öppen data. Materialet har laddats ner från Statistikcentralens gränssnittstjänst 2001-2019 med licensen CC BY 4.0. Observera, att kommunerna ansvarar själva för att uppdatera sina ekonomiska uppgifter i Statistikcentralens databas. Enhetlighet och jämförbarhet mellan åren eller kommunerna kan inte garanteras.",
    :general-disclaimer-3
    "Observera, att Lipas.fi data är uppdaterat av kommuner och andra ägare av idrottsplatser samt Lipas.fi administratörer i Jyväskylä Universitet. Omfattning, riktighet eller enhetlighet kan inte garanteras. I Lipas.fi statistik är material baserat på data i Lipas.fi databas. Återstående information kan påverka beräkningar.",
    :bullet2 "Statistiken om idrottsanläggningar",
    :area-m2-max "Maximum idrottsareal m²",
    :select-grouping "Gruppering",
    :select-city-service "Välj administratör",
    :region "Region",
    :show-comparison "Visa jämförelse",
    :length-km-avg "Medeltal av idrottsruttens längd",
    :sports-sites-count "Antal av idrottsanläggningar",
    :length-km-min "Minimum idrottsruttens längd",
    :country-avg "(medeltal för hela landet)",
    :length-km-count "Antal av idrottsrutter med längd information",
    :bullet4 "Liikuntapaikkojen rakennusvuodet",
    :population "Folkmängd",
    :sports-stats "Idrottsanläggningar",
    :disclaimer-headline "Datakälla",
    :select-cities "Välj kommuner",
    :subsidies "Bidrag",
    :select-interval "Välj intervall",
    :bullet1 "Ekonomiska uppgifter om idrotts- och ungdomsväsende",
    :area-m2-avg "Medeltal av idrottsareal m²",
    :age-structure "Byggnadsår av idrottsanläggningar",
    :length-km-max "Maximum idrottsruttens längd",
    :total-amount-1000e "Totalt (1000 €)",
    :city-stats "Kommunstatistik"},
   :ptv.integration {:manual "Anslut manuellt"},
   :pool-structures
   {:concrete "Betong",
    :hardened-plastic "Barnbassäng",
    :steel "Stål"},
   :map
   {:retkikartta-checkbox-reminder
    "Välj också igen \"Kan visas i Retkikartta.fi - service\" i följande steg i ytterligare information.",
    :route-part-difficulty "Ange svårighetsgrad för ruttsektion",
    :zoom-to-user "Zooma in till min position",
    :remove "Ta bort",
    :calculate-area "Räkna ytlan",
    :modify-polygon "Modifera området",
    :draw-polygon "Lägg till område",
    :travel-direction "Bestäm riktningen",
    :retkikartta-problems-warning
    "Korrigera problem som är märkta på kartan för att visa i Retkikartta.fi.",
    :edit-later-hint "Du kan modifera geometrin också senare",
    :center-map-to-site "Fokusera kartan på platsen",
    :draw-hole "Lägg till hål",
    :split-linestring "Klippa ruttdel",
    :delete-vertices-hint
    "För att ta bort en punkt, tryck och håll alt-knappen och klicka på punkten",
    :calculate-route-length "Räkna ut längden",
    :remove-polygon "Ta bort område",
    :modify-linestring "Modifera rutten",
    :download-gpx "Ladda ner GPX",
    :add-to-map "Lägg till på karta",
    :bounding-box-filter "Sök i området",
    :remove-linestring "Ta bort ruttdel",
    :calculate-count "Beräkna antal automatiskt",
    :draw-geoms "Rita",
    :confirm-remove "Är du säker att du vill ta bort geometrin?",
    :draw "Lägg till på karta",
    :draw-linestring "Lägg till ruttdel",
    :modify "Du kan dra punkten på kartan",
    :zoom-to-site "Zooma in till sportplats",
    :kink "Korrigera så att ruttdelen inte korsar själv",
    :zoom-closer "Zooma in"},
   :supporting-structures
   {:brick "Tegel",
    :concrete "Betong",
    :concrete-beam "Häll",
    :concrete-pillar "Stål",
    :solid-rock "Häll",
    :steel "Stål",
    :wood "Trä"},
   :owner
   {:unknown "Ingen information",
    :municipal-consortium "Samkommun",
    :other "Annat",
    :company-ltd "Företag",
    :city "Kommun",
    :state "Stat",
    :registered-association "Registrerad förening",
    :foundation "Stiftelse",
    :city-main-owner "Företag med kommun som majoritetsägare"},
   :menu
   {:frontpage "Framsidan",
    :headline "LIPAS",
    :jyu "Jyväskylä universitet",
    :main-menu "Huvudmeny"},
   :dryer-types
   {:cooling-coil "timmar", :munters "månader", :none "timmar"},
   :physical-units
   {:mm "mm",
    :hour "h",
    :m3 "m³",
    :m "m",
    :temperature-c "Temperatur °C",
    :l "l",
    :mwh "MWh",
    :m2 "m²",
    :celsius "°C"},
   :lipas.swimming-pool.water-treatment
   {:filtering-methods "E-post",
    :headline "Kontaktuppgifter",
    :ozonation? "E-post",
    :uv-treatment? "Kontaktuppgifter",
    :activated-carbon? ""},
   :statuses {:edited "{1} (redigerad)"},
   :lipas.user
   {:email "E-post",
    :permissions "Rättigheter",
    :permissions-example "Rätt att uppdatera Jyväskylä ishallen.",
    :saved-searches "Sparad sök",
    :report-energy-and-visitors "Spara raportmodellen",
    :permission-to-cities
    "Du har rättighet att uppdatera de här kommunerna:",
    :password "Lösenord",
    :lastname "Efternamn",
    :save-report "Spara raportmodellen",
    :sports-sites "Egna platser",
    :permission-to-all-cities
    "Du har rättighet att uppdatera alla kommuner",
    :username "Användarnamn",
    :history "Historia",
    :saved-reports "Sparad raportmodeller",
    :contact-info "Kontaktuppgifter",
    :permission-to-all-types
    "Du har rättighet att uppdatera alla typer",
    :requested-permissions "Begärda rättigheter",
    :email-example "epost@exempel.fi",
    :permission-to-portal-sites
    "Du har rättighet att uppdatera de här platserna:",
    :permissions-help "Skriv vad du vill uppdatera i Lipas",
    :report-energy-consumption "Begärda rättigheter",
    :firstname "Förnamn",
    :save-search "Spara söket",
    :view-basic-info "Kolla ut grunddata",
    :no-permissions
    "Användarnamn till Lipas har inte ännu konfirmerats",
    :permission-to-activities
    "Du har behörighet till följande aktiviteter",
    :username-example "tane12",
    :permission-to-types
    "Du har rättighet att uppdatera de här typerna:"},
   :heat-recovery-types
   {:thermal-wheel "Hjälp",
    :plate-heat-exchanger "",
    :liquid-circulation ""}})
