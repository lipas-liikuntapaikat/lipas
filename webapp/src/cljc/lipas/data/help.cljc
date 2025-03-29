(ns lipas.data.help)

(def sections
  {:general
   {:title {:fi "Yleiset" :se "Allmänt" :en "General"}
    :pages
    {:what-is-lipas
     {:title {:fi "Mikä Lipas on?"
              :en "What is Lipas?"
              :se "Vad är Lipas?"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440001"
                :type :text
                :content {:fi "Lipas on suomalainen liikunta- ja ulkoilupaikkojen tietokanta, joka tarjoaa tietoa urheilukentistä, uimahalleista ja muista liikuntapaikoista."
                          :en "Lipas is a Finnish database for sports and outdoor facilities, providing information on sports fields, swimming pools, and other recreational facilities."
                          :se "Lipas är en finsk databas för sport- och friluftsanläggningar som tillhandahåller information om idrottsplaner, simhallar och andra fritidsanläggningar."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440002"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Lipas esittelyvideo"
                        :en "Lipas introduction video"
                        :se "Lipas introduktionsvideo"}}]}
     :data
     {:title {:fi "Tietosisältö"
              :en "Data Content"
              :se "Datainnehåll"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440003"
                :type :text
                :content {:fi "Lipas sisältää tietoja liikuntapaikkojen sijainnista, laadusta, varustelusta ja käyttöönotosta."
                          :en "Lipas contains information on the location, quality, equipment, and usage of sports facilities."
                          :se "Lipas innehåller information om plats, kvalitet, utrustning och användning av idrottsanläggningar."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440004"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Lipas tietosisältö"
                        :en "Lipas data content"
                        :se "Lipas datainnehåll"}}]}

     :map-view
     {:title {:fi "Karttanäkymä"
              :en "Map View"
              :se "Kartvy"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440005"
                :type :text
                :content {:fi "Lipasin karttanäkymässä voit tarkastella liikuntapaikkoja kartalla ja suodattaa niitä eri kriteerien mukaan."
                          :en "In Lipas's map view, you can explore sports facilities on a map and filter them by various criteria."
                          :se "I Lipas kartvy kan du utforska idrottsanläggningar på en karta och filtrera dem efter olika kriterier."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440006"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Karttanäkymän käyttö"
                        :en "Using the map view"
                        :se "Använda kartvyn"}}]}
     :registration
     {:title {:fi "Rekisteröityminen"
              :en "Registration"
              :se "Registrering"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440007"
                :type :text
                :content {:fi "Rekisteröitymällä Lipas-palveluun voit päivittää liikuntapaikkojen tietoja ja käyttää lisäominaisuuksia."
                          :en "By registering for the Lipas service, you can update sports facility information and use additional features."
                          :se "Genom att registrera dig för Lipas-tjänsten kan du uppdatera information om idrottsanläggningar och använda ytterligare funktioner."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440008"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Rekisteröityminen Lipas-palveluun"
                        :en "Registering for the Lipas service"
                        :se "Registrera för Lipas-tjänsten"}}]}
     :login
     {:title {:fi "Kirjautuminen"
              :en "Login"
              :se "Logga in"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440009"
                :type :text
                :content {:fi "Kirjaudu Lipas-palveluun päästäksesi käsiksi henkilökohtaisiin asetuksiin ja työkaluihin."
                          :en "Log in to the Lipas service to access personal settings and tools."
                          :se "Logga in på Lipas-tjänsten för att få tillgång till personliga inställningar och verktyg."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440010"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Kirjautuminen Lipas-palveluun"
                        :en "Logging in to the Lipas service"
                        :se "Logga in på Lipas-tjänsten"}}]}
     :reports
     {:title {:fi "Raportit"
              :en "Reports"
              :se "Rapporter"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440011"
                :type :text
                :content {:fi "Lipas tarjoaa raportointityökaluja liikuntapaikkojen tilastojen ja kehityksen seuraamiseen."
                          :en "Lipas provides reporting tools for monitoring statistics and development of sports facilities."
                          :se "Lipas erbjuder rapporteringsverktyg för att övervaka statistik och utveckling av idrottsanläggningar."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440012"
                :type :video
                :provider :youtube
                :video-id "y0sF5xhGreA"
                :title {:fi "Raporttien käyttö Lipas-palvelussa"
                        :en "Using reports in Lipas service"
                        :se "Använda rapporter i Lipas-tjänsten"}}]}}}

   :analysis-tools
   {:title {:fi "Analyysityökalut"
            :en "Analysis Tools"
            :se "Analysverktyg"}
    :pages
    {:panduli
     {:title {:fi "Saavutettavuustyökalu"
              :en "Accessibility Tool"
              :se "Tillgänglighetsverktyg"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440013"
                :type :text
                :content {:fi "Panduli-työkalu auttaa arvioimaan liikuntapaikkojen saavutettavuutta eri käyttäjäryhmille."
                          :en "The Panduli tool helps assess the accessibility of sports facilities for different user groups."
                          :se "Panduli-verktyget hjälper till att bedöma tillgängligheten för idrottsanläggningar för olika användargrupper."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440014"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Panduli-työkalun käyttö"
                        :en "Using the Panduli tool"
                        :se "Använda Panduli-verktyget"}}]}
     :kissa
     {:title {:fi "Monipuolisuustyökalu"
              :en "Versatility Tool"
              :se "Mångsidighetsverktyg"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440015"
                :type :text
                :content {:fi "Kissa-työkalu mahdollistaa liikuntapaikkojen monipuolisuuden analysoinnin ja vertailun."
                          :en "The Kissa tool enables the analysis and comparison of the versatility of sports facilities."
                          :se "Kissa-verktyget möjliggör analys och jämförelse av mångsidigheten hos idrottsanläggningar."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440016"
                :type :video
                :provider :youtube
                :video-id "y0sF5xhGreA"
                :title {:fi "Kissa-työkalun käyttö"
                        :en "Using the Kissa tool"
                        :se "Använda Kissa-verktyget"}}]}}}

   :open-data
   {:title {:fi "Avoin data" :en "Open data" :se "Öppen data"}
    :pages
    {:excel-exports
     {:title {:fi "Excel-raportit" :en "Excel reports" :se "Excel-rapporter"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440017"
                :type :text
                :content {:fi "Lataa Lipas-tiedot Excel-muodossa käyttöösi raportointia ja analysointia varten."
                          :en "Download Lipas data in Excel format for reporting and analysis purposes."
                          :se "Ladda ner Lipas-data i Excel-format för rapportering och analys."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440018"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Excel-raporttien lataaminen"
                        :en "Downloading Excel reports"
                        :se "Ladda ner Excel-rapporter"}}]}
     :geojson-reports
     {:title {:fi "GeoJSON-raportit" :en "GeoJSON reports" :se "GeoJSON-rapporter"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440019"
                :type :text
                :content {:fi "GeoJSON-muodossa saatavilla olevat Lipas-tiedot soveltuvat erityisesti karttojen ja paikkatietojen käyttöön."
                          :en "Lipas data available in GeoJSON format is particularly suitable for use in maps and geospatial applications."
                          :se "Lipas-data tillgänglig i GeoJSON-format är särskilt lämplig för användning i kartor och geospatiala applikationer."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440020"
                :type :video
                :provider :youtube
                :video-id "y0sF5xhGreA"
                :title {:fi "GeoJSON-raporttien käyttö"
                        :en "Using GeoJSON reports"
                        :se "Använda GeoJSON-rapporter"}}]}
     :geoserver
     {:title {:fi "Geoserver" :en "Geoserver" :se "Geoserver"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440021"
                :type :text
                :content {:fi "Lipas-tiedot ovat saatavilla Geoserverin kautta, mikä mahdollistaa niiden integroinnin muihin järjestelmiin."
                          :en "Lipas data is available through Geoserver, enabling integration with other systems."
                          :se "Lipas-data är tillgänglig via Geoserver, vilket möjliggör integration med andra system."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440022"
                :type :video
                :provider :youtube
                :video-id "y0sF5xhGreA"
                :title {:fi "Geoserverin käyttö"
                        :en "Using Geoserver"
                        :se "Använda Geoserver"}}]}
     :rest-api
     {:title {:fi "REST-Api" :en "REST-Api" :se "REST-Api"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440023"
                :type :text
                :content {:fi "Lipas tarjoaa REST-rajapinnan, jonka avulla voit hakea ja päivittää tietoja ohjelmallisesti."
                          :en "Lipas provides a REST API that allows you to retrieve and update data programmatically."
                          :se "Lipas tillhandahåller ett REST-API som gör det möjligt att hämta och uppdatera data programmatiskt."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440024"
                :type :video
                :provider :youtube
                :video-id "y0sF5xhGreA"
                :title {:fi "REST-APIn käyttö"
                        :en "Using the REST API"
                        :se "Använda REST-API"}}]}}}

   :animals
   {:title {:fi "Eläimet" :en "Animals" :se "Djur"}
    :pages
    {:panduli
     {:title {:fi "Panduli" :en "Panda" :se "Panda"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440025"
                :type :text
                :content {:fi "Panduli on sympaattinen ja rauhallinen eläin, joka viihtyy metsissä ja syö bambua."
                          :en "Panduli is a sympathetic and calm animal that thrives in forests and eats bamboo."
                          :se "Panduli är ett sympatiskt och lugnt djur som trivs i skogar och äter bambu."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440026"
                :type :video
                :provider :youtube
                :video-id "dqT-UlYlg1s"
                :title {:fi "Panduli-video"
                        :en "Panduli video"
                        :se "Panduli-video"}}]}
     :kissa
     {:title {:fi "Kissa" :en "Cat" :se "Katt"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440027"
                :type :text
                :content {:fi "Kissa on lemmikkieläin, joka on tunnettu itsenäisyydestään ja uteliaisuudestaan."
                          :en "The cat is a pet known for its independence and curiosity."
                          :se "Katten är ett husdjur som är känt för sin självständighet och nyfikenhet."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440028"
                :type :video
                :provider :youtube
                :video-id "y0sF5xhGreA"
                :title {:fi "Kissavideo"
                        :en "Cat video"
                        :se "Kattvideo"}}]}
     :koira
     {:title {:fi "Koira" :en "Dog" :se "Hund"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440029"
                :type :text
                :content {:fi "Koira on uskollinen ja iloinen lemmikkieläin, joka rakastaa lenkkeilyä ja leikkejä."
                          :en "The dog is a loyal and cheerful pet that loves walks and games."
                          :se "Hunden är ett lojalt och glatt husdjur som älskar promenader och lekar."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440030"
                :type :video
                :provider :youtube
                :video-id "ApU0exIw7yA"
                :title {:fi "Koiravideo"
                        :en "Dog video"
                        :se "Hundvideo"}}]}
     :kana
     {:title {:fi "Kana" :en "Chicken" :se "Höna"}
      :blocks [{:block-id "550e8400-e29b-41d4-a716-446655440031"
                :type :text
                :content {:fi "Kana on maatilaeläin, joka tuottaa munia ja on suosittu kotieläin maaseudulla."
                          :en "The chicken is a farm animal that produces eggs and is a popular pet in rural areas."
                          :se "Hönan är ett lantbruksdjur som producerar ägg och är ett populärt husdjur på landsbygden."}}
               {:block-id "550e8400-e29b-41d4-a716-446655440032"
                :type :video
                :provider :youtube
                :video-id "nLwML2PagbY"
                :title {:fi "Kanavideo"
                        :en "Chicken video"
                        :se "Hönavideo"}}]}}}
  })
