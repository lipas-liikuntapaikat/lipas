(ns lipas.data.help)

(def sections
  {:general
   {:title {:fi "Yleiset" :se "Allmänt" :en "General"}
    :pages
    {:what-is-lipas
     {:title {:fi "Mikä Lipas on?"
              :en "What is Lipas?"
              :se "Vad är Lipas?"}
      :text {:fi "Lipas on suomalainen liikunta- ja ulkoilupaikkojen tietokanta, joka tarjoaa tietoa urheilukentistä, uimahalleista ja muista liikuntapaikoista."
             :en "Lipas is a Finnish database for sports and outdoor facilities, providing information on sports fields, swimming pools, and other recreational facilities."
             :se "Lipas är en finsk databas för sport- och friluftsanläggningar som tillhandahåller information om idrottsplaner, simhallar och andra fritidsanläggningar."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}
     :data
     {:title {:fi "Tietosisältö"
              :en "Data Content"
              :se "Datainnehåll"}
      :text {:fi "Lipas sisältää tietoja liikuntapaikkojen sijainnista, laadusta, varustelusta ja käyttöönotosta."
             :en "Lipas contains information on the location, quality, equipment, and usage of sports facilities."
             :se "Lipas innehåller information om plats, kvalitet, utrustning och användning av idrottsanläggningar."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}

     :map-view
     {:title {:fi "Karttanäkymä"
              :en "Map View"
              :se "Kartvy"}
      :text {:fi "Lipasin karttanäkymässä voit tarkastella liikuntapaikkoja kartalla ja suodattaa niitä eri kriteerien mukaan."
             :en "In Lipas's map view, you can explore sports facilities on a map and filter them by various criteria."
             :se "I Lipas kartvy kan du utforska idrottsanläggningar på en karta och filtrera dem efter olika kriterier."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}
     :registration
     {:title {:fi "Rekisteröityminen"
              :en "Registration"
              :se "Registrering"}
      :text {:fi "Rekisteröitymällä Lipas-palveluun voit päivittää liikuntapaikkojen tietoja ja käyttää lisäominaisuuksia."
             :en "By registering for the Lipas service, you can update sports facility information and use additional features."
             :se "Genom att registrera dig för Lipas-tjänsten kan du uppdatera information om idrottsanläggningar och använda ytterligare funktioner."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}
     :login
     {:title {:fi "Kirjautuminen"
              :en "Login"
              :se "Logga in"}
      :text {:fi "Kirjaudu Lipas-palveluun päästäksesi käsiksi henkilökohtaisiin asetuksiin ja työkaluihin."
             :en "Log in to the Lipas service to access personal settings and tools."
             :se "Logga in på Lipas-tjänsten för att få tillgång till personliga inställningar och verktyg."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}
     :reports
     {:title {:fi "Raportit"
              :en "Reports"
              :se "Rapporter"}
      :text {:fi "Lipas tarjoaa raportointityökaluja liikuntapaikkojen tilastojen ja kehityksen seuraamiseen."
             :en "Lipas provides reporting tools for monitoring statistics and development of sports facilities."
             :se "Lipas erbjuder rapporteringsverktyg för att övervaka statistik och utveckling av idrottsanläggningar."}
      :videos ["https://www.youtube.com/embed/y0sF5xhGreA?si=Pzdp1BU7HInvFVxZ"]}}}

   :analysis-tools
   {:title {:fi "Analyysityökalut"
            :en "Analysis Tools"
            :se "Analysverktyg"}
    :pages
    {:panduli
     {:title {:fi "Saavutettavuustyökalu"
              :en "Accessibility Tool"
              :se "Tillgänglighetsverktyg"}
      :text {:fi "Panduli-työkalu auttaa arvioimaan liikuntapaikkojen saavutettavuutta eri käyttäjäryhmille."
             :en "The Panduli tool helps assess the accessibility of sports facilities for different user groups."
             :se "Panduli-verktyget hjälper till att bedöma tillgängligheten för idrottsanläggningar för olika användargrupper."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}
     :kissa
     {:title {:fi "Monipuolisuustyökalu"
              :en "Versatility Tool"
              :se "Mångsidighetsverktyg"}
      :text {:fi "Kissa-työkalu mahdollistaa liikuntapaikkojen monipuolisuuden analysoinnin ja vertailun."
             :en "The Kissa tool enables the analysis and comparison of the versatility of sports facilities."
             :se "Kissa-verktyget möjliggör analys och jämförelse av mångsidigheten hos idrottsanläggningar."}
      :videos ["https://www.youtube.com/embed/y0sF5xhGreA?si=Pzdp1BU7HInvFVxZ"]}}}

   :open-data
   {:title {:fi "Avoin data" :en "Open data" :se "Öppen data"}
    :pages
    {:excel-exports
     {:title {:fi "Excel-raportit" :en "Excel reports" :se "Excel-rapporter"}
      :text {:fi "Lataa Lipas-tiedot Excel-muodossa käyttöösi raportointia ja analysointia varten."
             :en "Download Lipas data in Excel format for reporting and analysis purposes."
             :se "Ladda ner Lipas-data i Excel-format för rapportering och analys."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}
     :geojson-reports
     {:title {:fi "GeoJSON-raportit" :en "GeoJSON reports" :se "GeoJSON-rapporter"}
      :text {:fi "GeoJSON-muodossa saatavilla olevat Lipas-tiedot soveltuvat erityisesti karttojen ja paikkatietojen käyttöön."
             :en "Lipas data available in GeoJSON format is particularly suitable for use in maps and geospatial applications."
             :se "Lipas-data tillgänglig i GeoJSON-format är särskilt lämplig för användning i kartor och geospatiala applikationer."}
      :videos ["https://www.youtube.com/embed/y0sF5xhGreA?si=Pzdp1BU7HInvFVxZ"]}
     :geoserver
     {:title {:fi "Geoserver" :en "Geoserver" :se "Geoserver"}
      :text {:fi "Lipas-tiedot ovat saatavilla Geoserverin kautta, mikä mahdollistaa niiden integroinnin muihin järjestelmiin."
             :en "Lipas data is available through Geoserver, enabling integration with other systems."
             :se "Lipas-data är tillgänglig via Geoserver, vilket möjliggör integration med andra system."}
      :videos ["https://www.youtube.com/embed/y0sF5xhGreA?si=Pzdp1BU7HInvFVxZ"]}
     :rest-api
     {:title {:fi "REST-Api" :en "REST-Api" :se "REST-Api"}
      :text {:fi "Lipas tarjoaa REST-rajapinnan, jonka avulla voit hakea ja päivittää tietoja ohjelmallisesti."
             :en "Lipas provides a REST API that allows you to retrieve and update data programmatically."
             :se "Lipas tillhandahåller ett REST-API som gör det möjligt att hämta och uppdatera data programmatiskt."}
      :videos ["https://www.youtube.com/embed/y0sF5xhGreA?si=Pzdp1BU7HInvFVxZ"]}}}

   :animals
   {:title {:fi "Eläimet" :en "Animals" :se "Djur"}
    :pages
    {:panduli
     {:title {:fi "Panduli" :en "Panda" :se "Panda"}
      :text {:fi "Panduli on sympaattinen ja rauhallinen eläin, joka viihtyy metsissä ja syö bambua."
             :en "Panduli is a sympathetic and calm animal that thrives in forests and eats bamboo."
             :se "Panduli är ett sympatiskt och lugnt djur som trivs i skogar och äter bambu."}
      :videos ["https://www.youtube.com/embed/dqT-UlYlg1s?si=ojdgysvOqbA8avlz"]}
     :kissa
     {:title {:fi "Kissa" :en "Cat" :se "Katt"}
      :text {:fi "Kissa on lemmikkieläin, joka on tunnettu itsenäisyydestään ja uteliaisuudestaan."
             :en "The cat is a pet known for its independence and curiosity."
             :se "Katten är ett husdjur som är känt för sin självständighet och nyfikenhet."}
      :videos ["https://www.youtube.com/embed/y0sF5xhGreA?si=Pzdp1BU7HInvFVxZ"]}
     :koira
     {:title {:fi "Koira" :en "Dog" :se "Hund"}
      :text {:fi "Koira on uskollinen ja iloinen lemmikkieläin, joka rakastaa lenkkeilyä ja leikkejä."
             :en "The dog is a loyal and cheerful pet that loves walks and games."
             :se "Hunden är ett lojalt och glatt husdjur som älskar promenader och lekar."}
      :videos ["https://www.youtube.com/embed/ApU0exIw7yA?si=5blu20IXAJIfxzZn"]}
     :kana
     {:title {:fi "Kana" :en "Chicken" :se "Höna"}
      :text {:fi "Kana on maatilaeläin, joka tuottaa munia ja on suosittu kotieläin maaseudulla."
             :en "The chicken is a farm animal that produces eggs and is a popular pet in rural areas."
             :se "Hönan är ett lantbruksdjur som producerar ägg och är ett populärt husdjur på landsbygden."}
      :videos ["https://www.youtube.com/embed/nLwML2PagbY?si=z0DxoY8-yPAb8w6n"]}}}}
)
