(ns lipas.data.ptv-service-guidance
  "Per-sub-category authoring guidance for PTV Service descriptions and
  user instructions (toimintaohje).

  The texts are written as instructions to a human author (\"Kerro,
  että...\", \"Ohjeista asiakasta...\") and describe what TOPICS a PTV
  Service description/toimintaohje for each LIPAS sub-category should
  cover. They deliberately authorize generic, type-level statements;
  municipality-specific claims must still come from LIPAS data.

  Used by:
  - the AI service-description generation prompt (backend)
  - editor guidance in the PTV admin UI (frontend)
  - the description-quality eval rubric (lipas.backend.ptv.eval)

  Source: LIPAS_tyyppikohtaiset_kuvaukset_ja_toimintaohjeet.xlsx
  (DVV/PTV content guidance, 06/2026). Keys are LIPAS sub-category codes;
  covers all PTV-relevant sub-categories (7000 Huoltotilat is not part
  of the PTV integration and has no entry).")

(def guidance
  "Map of sub-category code -> {:description ... :user-instruction ...}.
  :description      = what the Service kuvaus should contain
  :user-instruction = what the Service toimintaohje should contain"
  {
   ;; Virkistys- ja retkeilyalueet
   1
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa virkistys- ja retkeilyalueita ulkoilua ja luonnossa liikkumista varten. Voit mainita esimerkkeinä lähipuistot, ulkoilupuistot, ulkoilualueet, retkeilyalueet ja muut luonnonalueet.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa luonnossa liikkumisen, ulkoilun ja virkistäytymisen. Voit mainita, että alueet soveltuvat
- kävelyyn ja patikointiin
- retkeilyyn
- luonnon tarkkailuun ja oleskeluun.
Voit todeta, että osa alueista on lähellä asutusta ja osa kauempana, retkeilyä varten.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille luonnossa liikkumisesta ja virkistäytymisestä kiinnostuneille. Voit todeta, että alueet soveltuvat eri-ikäisille käyttäjille.

4. Palvelun luonne
Mainitse, että alueet ovat omatoimiseen käyttöön tarkoitettuja. Voit todeta, että osa alueista on suojeltuja tai erityiskohteita, mikä voi vaikuttaa niiden käyttöön."
    :user-instruction
    "1. Vapaa käyttö
Kerro, että virkistys- ja retkeilyalueet ovat vapaasti käytettävissä jokamiehenoikeuksien ja alueen omien sääntöjen mukaisesti.

2. Alueiden ja reittien tarkistaminen
Ohjeista asiakasta tutustumaan alueisiin, reitteihin ja niiden varustukseen (esim. taukopaikat, opastaulut) asiointikanavan tai karttapalvelun kautta ennen retkelle lähtöä.

3. Suojelualueiden erityisohjeet
Mainitse, että jos alue on luonnonsuojelualue, kansallispuisto tai muu erityiskohde, käyttäjän tulee noudattaa alueen omia järjestyssääntöjä ja mahdollisia liikkumisrajoituksia. Ohjaa tarkistamaan ne asiointikanavasta tai aluekohtaisesta tiedosta.

4. Toimiminen alueella
Kerro, että asiakas liikkuu alueella omatoimisesti, noudattaa jokamiehenoikeuksia ja huolehtii roskien viemisestä pois alueelta. Mainitse, että tulenteko on sallittua vain merkityillä paikoilla, jos sellaisia on."}

   ;; Retkeilyn palvelut
   2
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa retkeilyä tukevia rakenteita ja palvelupisteitä luonnossa. Voit mainita esimerkiksi opastuspisteet, luontotornit, taukopaikat, tulentekopaikat ja telttailualueet.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa retkeilyn ja ulkoilun aikaisen levähtämisen, ruoanlaiton ja yöpymisen. Voit mainita yleisellä tasolla, että rakenteet tukevat retkeilyä, kalastusta ja vesillä liikkumista.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille retkeilystä ja luonnossa liikkumisesta kiinnostuneille. Voit todeta, että se soveltuu eri-ikäisille käyttäjille.

4. Palvelun luonne
Mainitse, että rakenteet ja pisteet ovat osa laajempaa retkeily- ja virkistysaluekokonaisuutta ja tarkoitettu omatoimiseen käyttöön."
    :user-instruction
    "1. Vapaa käyttö
Kerro, että retkeilyn palvelurakenteet (esim. taukopaikat, laavut, tulipaikat) ovat pääosin vapaasti käytettävissä kaikille.

2. Varauksella käytettävät kohteet
Mainitse, jos jokin kohde (esim. varauslaavu tai telttailualue) edellyttää varausta. Ohjaa asiakas tarkistamaan varausmahdollisuus ja ehdot asiointikanavasta.

3. Sijainnin ja varustuksen tarkistaminen
Ohjeista asiakasta tutustumaan retkeilypisteiden sijaintiin ja varustukseen karttapalvelun tai asiointikanavan kautta ennen retkelle lähtöä.

4. Toimiminen paikan päällä
Kerro, että asiakas käyttää rakenteita omatoimisesti ja vastuullisesti, noudattaa tulenteko- ja jätehuolto-ohjeita, ja jättää paikan siistiksi seuraavaa käyttäjää varten."}

   ;; Lähiliikunta ja liikuntapuistot
   1100
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa lähiliikuntaan tarkoitettuja alueita ja puistoja. Voit mainita esimerkiksi liikuntapuistot, lähiliikuntapaikat ja ulkokuntoilupaikat.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa monipuolisen omaehtoisen liikkumisen, kuten
- kuntoilun ulkokuntoiluvälineillä
- pyöräilyn ja rullaluistelun
- frisbeegolfin
- muun matalan kynnyksen liikkumisen.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikenikäisille liikkujille. Voit todeta, että alueet soveltuvat erityisesti arkiliikuntaan ja lähiympäristössä tapahtuvaan harrastamiseen.

4. Palvelun luonne
Mainitse, että alueet ovat vapaasti ja omatoimisesti käytettäviä lähiliikuntapaikkoja."
    :user-instruction
    "1. Vapaa käyttö
Kerro, että lähiliikuntapaikat ja -puistot ovat vapaasti ja maksutta kaikkien käytössä.

2. Käyttöajat
Mainitse, jos alueilla on yleisiä käyttöaikoja tai valaistusaikoja, ja ohjaa tarkistamaan ne asiointikanavasta.

3. Omatoiminen käyttö ja varusteet
Kerro, että asiakas käyttää alueen välineitä ja rakenteita omatoimisesti niiden käyttötarkoituksen mukaisesti. Mainitse, että esimerkiksi frisbeegolfissa tai pyöräilyssä käyttäjä tuo omat välineensä.

4. Turvallisuus ja yhteiskäyttö
Ohjeista huomioimaan muut käyttäjät ja noudattamaan alueen mahdollisia käyttöohjeita ja -sääntöjä."}

   ;; Yleisurheilukentät ja -paikat
   1200
   {:description
    "1. Mikä palvelu on?
Kerro, että kunta tarjoaa yleisurheiluun tarkoitettuja kenttiä ja harjoitusalueita.

2. Mitä palvelu sisältää?
Kuvaa, että kentillä voi harrastaa erilaisia yleisurheilulajeja, kuten juoksua, hyppyjä ja heittoja. Voit mainita, että paikkoja käytetään harjoitteluun, harrastamiseen ja liikuntaan.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille liikkujille ja yleisurheilusta kiinnostuneille. Voit todeta, että kentät soveltuvat eri-ikäisille ja eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että kenttiä käytetään sekä omaehtoiseen liikkumiseen että ohjattuun harjoitteluun ja seuratoimintaan."
    :user-instruction
    "1. Vapaa käyttö ja varaukset
Kerro, että osa yleisurheilupaikoista on vapaasti käytettävissä, kun ne eivät ole varattuna. Mainitse, että osa tiloista edellyttää varausta.

2. Varaustilanteen tarkistaminen
Ohjeista asiakas tarkistamaan varaustilanteen varauskalenterista tai muusta käytettävästä järjestelmästä.

3. Vuoron varaaminen
Kerro, että asiakas voi varata yleisurheilukentän tai -tilan tilavarausjärjestelmän kautta.

4. Paikan päällä toimiminen
Kerro, että asiakas käyttää kenttää omatoimisesti ja noudattaa yleisurheilupaikan käyttöohjeita ja huomioi kentän kunnon ja olosuhteet."}

   ;; Pallokentät
   1300
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa ulkona sijaitsevia kenttiä erilaisten pallopelien pelaamiseen. Voit mainita esimerkiksi jalkapallo-, pesäpallo-, tennis- ja koripallokentät.

2. Mitä palvelu sisältää?
Kuvaa, että kentät mahdollistavat erilaisten pallopelilajien harrastamisen ja pelaamisen. Voit mainita, että kenttiä käytetään sekä harrastamiseen että harjoitteluun ja otteluihin.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille liikkujille, harrastajille ja yhteisöille. Voit todeta, että kentät soveltuvat eri-ikäisille käyttäjille.

4. Palvelun luonne
Mainitse, että kentät ovat monikäyttöisiä ja niitä käytetään sekä omaehtoiseen liikkumiseen että ohjattuun toimintaan ja seuratoimintaan."
    :user-instruction
    "1. Kenttien vapaa käyttö
Kerro, jos kenttiä voi käyttää vapaasti, kun niissä ei ole varattua toimintaa. Kerro myös, mitkä kentät ovat maksullisia ja mitkä maksuttomia.

2. Varaustilanteen tarkistaminen
Ohjeista asiakas tarkistamaan kenttien varaustilanne käytössä olevasta varauskalenterista tai asiointikanavasta.

3. Vuorojen varaaminen
Kerro, että asiakas voi varata kenttävuoron asiointikanavan kautta, jos varaus on mahdollista. Mainitse lyhyesti, että yhdistykset ja seurat voivat varata vuoroja suoraan järjestelmästä, jos tämä kuuluu palveluun.

4. Miten asiakas toimii kentällä?
Mainitse, että asiakas noudattaa kentän käyttöohjeita ja tuo omat pelivälineet mukaan, jos se on tarpeen."}

   ;; Jääurheilualueet ja luonnonjäät
   1500
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa mahdollisuuksia luisteluun ja jääurheiluun ulkona sijaitsevilla jääalueilla. Voit mainita esimerkiksi luistelukentät, kaukalot ja luistelureitit.

2. Mitä palvelu sisältää?
Kerro, että palvelu mahdollistaa luistelun, jääurheilun harrastamisen ja vapaa-ajan liikkumisen talvella. Voit mainita, että tarjolla on sekä yleisöluistelua että harrastus- ja harjoittelumahdollisuuksia.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille luistelusta ja jääliikunnasta kiinnostuneille. Voit todeta, että se soveltuu eri-ikäisille ja eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että alueet ovat pääosin omatoimiseen käyttöön ja että talviolosuhteet vaikuttavat niiden käytettävyyteen."
    :user-instruction
    "1. Käyttöajat ja vapaa käyttö
Kerro, että ulkoluistelupaikkoja voi käyttää vapaasti, kun ne ovat avoinna ja kunnossa.

2. Jäätilanteen tarkistaminen
Ohjaa asiakas tarkistamaan jäiden ja luistelureittien ajantasainen kunto ja avoinnaolo asiointikanavasta ennen käyntiä, sillä käyttö on olosuhderiippuvaista.

3. Mahdolliset varaukset
Mainitse, jos osa kaukaloista tai kentistä on varattavissa ohjattua toimintaa varten, ja ohjaa tarkistamaan tilanne asiointikanavasta.

4. Mitä asiakas huomioi paikan päällä?
Mainitse, että asiakkaan tulee noudattaa luistelupaikan sääntöjä ja käyttää omia varusteita."}

   ;; Golfkentät
   1600
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa golfin pelaamiseen ja harjoitteluun tarkoitettuja alueita. Voit mainita esimerkiksi golfkentät, harjoitusalueet ja ratagolfkentät.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa golfin pelaamisen ja harjoittelun eri tasoisille pelaajille. Voit mainita, että tarjolla voi olla myös ratagolfia, joka soveltuu myös aloittelijoille ja perheille.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu golfista ja ratagolfista kiinnostuneille. Voit todeta, että se soveltuu eri-ikäisille ja eritasoisille pelaajille.

4. Palvelun luonne
Mainitse, että käyttö voi perustua esimerkiksi pelioikeuksiin tai vapaaseen käyttöön kentästä riippuen."
    :user-instruction
    "1. Käyttöoikeus ja maksut
Kerro, jos golfkentän käyttö edellyttää pelioikeutta, jäsenyyttä tai maksua. Ohjaa asiakas tarkistamaan käytännöt ja hinnat asiointikanavasta.

2. Ratagolfin vapaa käyttö
Mainitse, jos ratagolfrata on vapaasti ja maksutta kaikkien käytössä.

3. Varaaminen
Kerro, että peliajan tai vuoron voi tarvittaessa varata asiointikanavan tai varausjärjestelmän kautta.

4. Käytännöt paikan päällä
Kerro, että asiakas noudattaa kentän pelisääntöjä ja etikettiä, ja tuo omat pelivälineet mukaan."}

   ;; Kuntoilukeskukset ja liikuntasalit
   2100
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa kuntosaleja ja kuntoiluun tarkoitettuja sisätiloja. Voit mainita, että mukana voi olla esimerkiksi kuntosaleja, voimailusaleja ja muita harjoittelutiloja.

2. Mitä palvelu sisältää?
Kerro esimerkiksi, että tilat mahdollistavat monipuolisen lihaskunto- ja kuntosaliharjoittelun sekä esimerkiksi voimailu- ja kamppailulajien harjoittelun. Voit mainita, että käytettävissä on erilaisia kuntoilulaitteita ja harjoittelumahdollisuuksia.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kuntalaisille ja muille liikkujille. Voit mainita esimerkiksi, että salit soveltuvat eri-ikäisille ja eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että tiloja käytetään omatoimiseen harjoitteluun. Voit myös mainita, jos osa tiloista on suunnattu tietyille käyttäjäryhmille tai lajeille."
    :user-instruction
    "1. Kuntosalin käyttöajat ja vuorot
Ohjeista asiakasta tarkistamaan kuntosalin varaukset ja käyttöajat käytössä olevasta varauskalenterista tai asiointikanavasta. Kerro, jos ohjatut ryhmät tai varatut vuorot rajoittavat omatoimikäyttöä.

2. Miten käyttöoikeus tai pääsy hankitaan?
Jos palvelu on maksullinen, kerro, että asiakas voi ostaa kertalipun, sarjakortin tai käyttöoikeuden asiointikanavan kautta.

3. Miten asiakas toimii paikan päällä?
Mainitse lyhyesti, että asiakas käyttää laitteita omatoimisesti ja noudattaa tilan käyttöohjeita. Voit tarvittaessa mainita, että omat välineet tuodaan mukana, jos se kuuluu kunnan käytäntöihin.

4. Muut huomiot
Mainitse, jos esimerkiksi voimailu- tai kamppailulajien salien käyttö edellyttää erillistä lupaa, vuoroa tai seuran kautta tapahtuvaa varaamista."}

   ;; Liikuntahallit
   2200
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa sisäliikuntaan tarkoitettuja halleja. Voit mainita tilatyyppejä, kuten liikuntahallit, monitoimihallit ja lajikohtaiset hallit (esim. sulkapallo, squash, jalkapallo, salibandy).

2. Mitä palvelu sisältää?
Kerro, että hallit mahdollistavat monipuolisen liikkumisen ja harrastamisen, kuten
- palloilun ja mailapelit
- ohjatun liikunnan
- muun liikunta- ja harrastustoiminnan.
Voit mainita, että tiloja käytetään myös seura- ja kilpailutoiminnassa.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kuntalaisille, harrastajille ja yhteisöille. Voit mainita yleisesti, että hallit soveltuvat eri-ikäisille käyttäjille.

4. Palvelun luonne
Mainitse yleisellä tasolla, että halleja voi käyttää omatoimisesti tai ohjatussa toiminnassa, ja että käyttö ja saatavuus vaihtelevat halleittain."
    :user-instruction
    "1. Miten asiakas voi käyttää halleja?
Kerro, voiko halleihin mennä vapaasti silloin, kun ne eivät ole varattuna.

2. Miten käyttövuorot tarkistetaan?
Ohjeista asiakas tarkistamaan hallien vapaat ajat käytössä olevasta varauskalenterista tai muusta asiointikanavasta.

3. Miten varaus tehdään, jos tarvitaan?
Kerro, että vuoron voi varata varauskalenterin kautta tai asioimalla liikuntapalveluissa. Kerro myös, miten mahdollisia vakiovuoroja haetaan.

4. Mitä asiakkaan tulee huomioida?
Mainitse, että käyttäjän tulee noudattaa hallien käyttöohjeita ja huolehtia omista varusteista."}

   ;; Yksittäiset lajikohtaiset sisäliikuntapaikat
   2300
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa lajikohtaisiin tarpeisiin suunniteltuja sisäliikuntatiloja. Voit mainita esimerkkeinä tanssitilat, kiipeilyseinät, telinevoimistelutilat ja muut erikoistilat, ilman yksityiskohtaisia paikkakuvauksia.

2. Mitä palvelu sisältää?
Kerro, että tilat mahdollistavat tietyn lajin harjoittelun ja harrastamisen erikoistuneessa ympäristössä. Voit mainita, että tiloja käytetään harrastetoiminnassa, harjoittelussa ja seuratoiminnassa.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kyseisen lajin harrastajille ja siitä kiinnostuneille. Voit mainita yleisesti, että tilat soveltuvat eri-ikäisille käyttäjille.

4. Palvelun luonne
Mainitse yleisellä tasolla, että tiloja voi käyttää omatoimisesti tai ohjatussa toiminnassa, ja että käyttö ja saatavuus vaihtelevat tiloittain."
    :user-instruction
    "1. Miten asiakas voi käyttää tiloja?
Kerro, voiko tiloihin mennä vapaasti silloin, kun ne eivät ole varattuna.

2. Miten käyttövuorot tarkistetaan?
Ohjeista asiakas tarkistamaan tilojen vapaat ajat käytössä olevasta varauskalenterista tai muusta asiointikanavasta.

3. Miten varaus tehdään, jos tarvitaan?
Kerro, että vuoron voi varata varauskalenterin kautta tai asioimalla liikuntapalveluissa.

4. Mitä asiakkaan tulee huomioida?
Mainitse, että käyttäjän tulee noudattaa tilojen käyttöohjeita ja lajikohtaisia turvallisuusohjeita sekä huolehtia omista varusteista."}

   ;; Jäähallit
   2500
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa jäähalleja luisteluun ja jääurheiluun. Voit mainita, että halleja käytetään esimerkiksi harjoitteluun, otteluihin ja yleisöluisteluun.

2. Mitä palvelu sisältää?
Kerro, että palvelu mahdollistaa luistelun, jääurheilun harrastamisen ja vapaa-ajan liikkumisen sisätiloissa. Voit mainita, että tarjolla on sekä yleisöluistelua että harjoittelu- ja harrastusmahdollisuuksia.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille luistelusta ja jääurheilusta kiinnostuneille. Voit todeta, että se soveltuu eri-ikäisille ja eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että halleja käytetään sekä omatoimiseen luisteluun että ohjattuun ja seuratoimintaan."
    :user-instruction
    "1. Yleisöluisteluvuorot
Ohjeista asiakas tarkistamaan jäähallien yleisöluisteluvuorot asiointikanavasta.

2. Vuorojen tarkistaminen ja varaukset
Ohjaa asiakas tarkistamaan jäähallien varaukset ja vapaat vuorot käytössä olevasta varauskalenterista. Kerro, että osa vuoroista voi olla maksullisia tai maksuttomia.

3. Miten vuoro varataan tai palvelua käytetään?
Kerro, että asiakas voi varata oman vuoron asiointikanavan kautta, jos varausmahdollisuus on käytössä. Kerro, että yleisöluisteluvuoroille voi osallistua omatoimisesti ilmoitettujen ohjeiden mukaisesti.

4. Mitä asiakas huomioi paikan päällä?
Mainitse, että asiakkaan tulee noudattaa jäähallin sääntöjä ja käyttää omia varusteita. Mainitse myös, jos paikassa on mahdollisuus vuokrata luistimia."}

   ;; Keilahallit
   2600
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa keilailuun tarkoitettuja sisätiloja. Voit mainita, että kyseessä on keilahalli.

2. Mitä palvelu sisältää?
Kerro, että palvelu mahdollistaa keilailun harrastamisena ja vapaa-ajan viettämisenä. Voit mainita, että halli soveltuu sekä yksittäisille käyttäjille että ryhmille.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille keilailusta kiinnostuneille. Voit todeta, että se soveltuu eri-ikäisille käyttäjille ja sopii myös aloittelijoille.

4. Palvelun luonne
Mainitse, että toiminta on omatoimista, ja että ratoja voidaan varata myös ryhmäkäyttöön."
    :user-instruction
    "1. Vuoron varaaminen
Ohjeista asiakas varaamaan keilarata asiointikanavan tai varausjärjestelmän kautta, jos varaus on mahdollista. Mainitse, että radalle voi tulla myös ilman varausta, jos vapaita ratoja on.

2. Aukioloajat
Ohjaa asiakas tarkistamaan hallin aukioloajat asiointikanavasta.

3. Maksut
Kerro, että keilailu on yleensä maksullista, ja ohjaa asiakas tarkistamaan hinnat ja maksutavat asiointikanavasta.

4. Toimiminen paikan päällä
Kerro, että asiakas noudattaa hallin käyttöohjeita ja varusteiden (esim. keilakenkien) käyttöä koskevia ohjeita."}

   ;; Uima-altaat, hallit ja kylpylät
   3100
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa uimahalleja ja kylpylöitä vesiliikuntaan ja virkistäytymiseen. Voit mainita, että kyseessä on sisätiloissa sijaitseva uintipaikka.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa uimisen ja muun vesiliikunnan, kuten vesijumpan. Voit mainita myös, jos tarjolla on ohjattua toimintaa, opetusta ja kylpyläpalveluita, kuten saunoja.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille vesiliikunnasta ja virkistäytymisestä kiinnostuneille. Voit todeta, että se soveltuu eri-ikäisille ja eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että palvelu tarjoaa mahdollisuuksia sekä omatoimiseen liikkumiseen että ohjattuun toimintaan."
    :user-instruction
    "1. Miten palvelua käytetään?
Kerro, että uimahalliin tai kylpylään tullaan aukioloaikojen puitteissa ja että sisäänpääsy on pääsääntöisesti maksullinen.

2. Lipun ostaminen ja kulkeminen
Ohjaa asiakas ostamaan kertalipun, sarjakortin tai kausikortin asiointikanavassa tai palvelupisteessä.

3. Peseytyminen ja käytännöt
Kerro, että asiakkaan tulee peseytyä ennen allastiloihin siirtymistä ja käyttää uima-asua. Voit mainita yleisen turvallisuusperiaatteen, esimerkiksi että uimataidottomalla tai pienellä lapsella tulee olla aikuinen valvoja mukana.

4. Aukioloajat ja käytön rajoitukset
Ohjeista tarkistamaan aukioloajat asiointikanavasta. Kerro, että osa palveluista (esim. saunat, lastenaltaat) voi olla ajoittain pois käytöstä.

5. Toimiminen paikan päällä
Mainitse, että asiakkaan tulee seurata henkilökunnan ohjeita ja noudattaa hallin sääntöjä."}

   ;; Maauimalat ja uimarannat
   3200
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa maauimaloita, uimarantoja ja talviuintipaikkoja vesiliikuntaan ja virkistäytymiseen. Voit mainita, että kohteita sijaitsee eri puolilla kuntaa.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa uimisen ja oleskelun vesistöjen äärellä. Voit mainita yleisesti, että kohteissa voi olla uimiseen ja virkistäytymiseen liittyviä varustuksia (esim. laiturit, taukopaikat, altaat).

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille vesillä liikkumisesta ja ulkoilusta kiinnostuneille. Voit todeta, että se soveltuu eri-ikäisille käyttäjille.

4. Palvelun luonne
Mainitse, että kohteita käytetään omatoimiseen virkistäytymiseen ja liikkumiseen. Voit todeta, että kunta huolehtii yleisesti kohteiden kunnossapidosta ja uimaveden seurannasta."
    :user-instruction
    "1. Vapaa käyttö
Kerro, että maauimalat, uimarannat ja useimmat uimapaikat ovat vapaasti käytettävissä, kun ne ovat turvallisessa kunnossa. Mainitse, että talviuintipaikkojen käytettävyys voi vaihdella vuodenaikojen mukaan, ja että paikka voi olla maksullinen tai vaatia uimaseuran jäsenyyttä.

2. Uiminen omalla vastuulla
Kerro lyhyesti, että uinti tapahtuu omalla vastuulla ja että huoltajat vastaavat lasten ja uimataidottomien valvonnasta.

3. Rannan ohjeiden noudattaminen
Ohjeista noudattamaan rannalla olevia ohjeita, kieltoja ja varoituksia.

4. Uimaveden laatu ja olosuhteet
Ohjaa asiakas tarkistamaan uimaveden laatu- ja olosuhdetiedot asiointikanavasta, koska tiedot päivittyvät säännöllisesti.

5. Rannan käyttäminen ja siisteys
Kerro, että rannan käyttäjän tulee huolehtia siisteydestä ja käyttää jäteastioita. Mainitse, että talviuintipaikoissa voi olla rajoituksia, kuten avannon sulkeminen tietyiksi ajoiksi."}

   ;; Laskettelurinteet ja rinnehiihtokeskukset
   4100
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa laskettelua ja muuta rinnetoimintaa varten suunniteltuja alueita. Voit mainita esimerkiksi laskettelurinteet ja niihin liittyvät rakenteet (esim. hissit).

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa laskettelun ja muun rinneliikunnan, kuten laskettelun, lumilautailun ja muun talvilajien harrastamisen rinteessä.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikenikäisille laskettelusta ja rinnetoiminnasta kiinnostuneille. Voit todeta, että se soveltuu eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että käyttö on usein lipulla tai kausikortilla tapahtuvaa ja olosuhderiippuvaista (lumi- ja sääolosuhteet vaikuttavat aukioloon)."
    :user-instruction
    "1. Aukioloajat ja kausi
Ohjeista asiakas tarkistamaan rinteen ja hissien aukioloajat ja talvikauden tilanne asiointikanavasta, sillä käyttö on olosuhderiippuvaista.

2. Lipun hankkiminen
Kerro, että rinteen käyttö edellyttää usein päivä-, sarja- tai kausilipun hankkimista, ja ohjaa asiakas hankkimaan lipun asiointikanavan tai paikan päällä olevan myyntipisteen kautta.

3. Turvallisuus ja säännöt
Mainitse, että rinteessä tulee noudattaa rinneturvallisuussääntöjä ja opasteita.

4. Varusteet
Kerro, että asiakas käyttää omia välineitä, ellei alueella ole erillistä vuokrauspalvelua."}

   ;; Katetut talviurheilupaikat
   4200
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa katettuja, sisätiloissa sijaitsevia talviurheilupaikkoja. Voit mainita esimerkiksi hiihtotunnelit, lasketteluhallit ja curling-radat.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa talvilajien, kuten hiihdon, laskettelun, lumilautailun tai curlingin, harrastamisen ympäri vuoden sään ja kauden vaikuttamatta.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikenikäisille talvilajeista kiinnostuneille. Voit todeta, että se soveltuu eritasoisille käyttäjille, myös aloittelijoille.

4. Palvelun luonne
Mainitse, että käyttö on usein maksullista ja perustuu vuoroihin tai lippuihin."
    :user-instruction
    "1. Vuorot ja aukioloajat
Ohjeista asiakas tarkistamaan käyttövuorot ja aukioloajat asiointikanavasta.

2. Lipun tai vuoron hankkiminen
Kerro, että käyttö edellyttää lipun ostamista tai vuoron varaamista asiointikanavan kautta.

3. Turvallisuusohjeet
Mainitse, että tiloissa tulee noudattaa turvallisuus- ja käyttöohjeita.

4. Varusteet
Kerro, että asiakas käyttää omia varusteitaan, ellei tilassa ole erillistä vuokrauspalvelua."}

   ;; Hyppyrimäet
   4300
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa mäkihyppyyn ja sen harjoitteluun tarkoitettuja hyppyrimäkiä.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa mäkihypyn harjoittelun ja harrastamisen erikokoisilla hyppyrimäillä.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu mäkihypystä kiinnostuneille harrastajille ja seuroille. Voit todeta, että käyttö voi edellyttää lajitaitoja.

4. Palvelun luonne
Mainitse, että toiminta tapahtuu ohjatusti tai seuratoiminnan kautta ja perustuu turvallisuusperiaatteisiin."
    :user-instruction
    "1. Käytön edellytykset
Kerro, että hyppyrimäkien käyttö edellyttää lajitaitoja ja turvallisuusohjeiden noudattamista, ja että käyttö tapahtuu usein seuran kautta.

2. Käyttövuorot
Ohjeista asiakas tarkistamaan harjoitusvuorot ja käyttömahdollisuudet asiointikanavasta tai seuralta.

3. Turvallisuus
Mainitse, että mäessä tulee noudattaa valvojan tai seuran antamia turvallisuusohjeita.

4. Olosuhteet
Kerro, että käyttö voi olla olosuhderiippuvaista (esim. lumitilanne, keliolosuhteet) ja ohjaa tarkistamaan ajantasaisen tilanteen."}

   ;; Liikunta- ja ulkoilureitit
   4400
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa erilaisia liikuntaan ja ulkoiluun tarkoitettuja reittejä. Voit mainita esimerkiksi kuntoradat, hiihtoladut, kävely- ja pyöräilyreitit ja luontopolut.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa liikkumisen ja retkeilyn reiteillä eri vuodenaikoina. Voit mainita, jos reitit soveltuvat esimerkiksi
- kävelyyn ja juoksuun
- hiihtoon
- pyöräilyyn
- muuhun retkeilyyn ja ulkoiluun.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kaikille ulkoilusta ja liikkumisesta kiinnostuneille. Voit todeta, että reitit soveltuvat eritasoisille liikkujille.

4. Palvelun luonne
Mainitse, että reitit ovat omatoimiseen liikkumiseen tarkoitettuja. Voit todeta, että olosuhteet ja vuodenaika vaikuttavat reittien käytettävyyteen (esim. talvikäyttö)."
    :user-instruction
    "1. Vapaa käyttö
Kerro, että reitit ovat vapaasti käytettävissä, kun ne ovat avoimia ja turvallisessa kunnossa.

2. Reittien kunnon ja olosuhteiden tarkistaminen
Ohjeista asiakas tarkistamaan reittien ajantasainen kunto (esim. latujen ja talvireittien tilanne) asiointikanavasta tai karttapalvelusta ennen lähtöä.

3. Reittikuvaukset ja kartat
Ohjaa asiakas tutustumaan reittikuvauksiin, pituuksiin ja karttoihin asiointikanavan tai karttapalvelun kautta.

4. Toimiminen reitillä
Mainitse lyhyesti, että asiakas liikkuu reitillä omatoimisesti ja noudattaa yleisiä ulkoiluohjeita sekä lajikohtaisia erityispiirteitä (esim. moottorikelkkareiteillä ajoneuvon vaatimukset, hevosreiteillä yhteiskäytön pelisäännöt). Kerro, että käyttäjä tuo omat välineensä."}

   ;; Suunnistusalueet
   4500
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa suunnistukseen tarkoitettuja maasto- ja ulkoilualueita. Voit mainita, että alueet soveltuvat suunnistukseen, hiihtosuunnistukseen ja pyöräsuunnistukseen.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa suunnistuksen harrastamisen ja harjoittelun maastossa. Voit mainita, että alueilla voi olla rastiverkostoja tai karttoja tukemassa toimintaa.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu suunnistuksesta kiinnostuneille harrastajille, kuntoilijoille ja seuroille. Voit todeta, että se soveltuu eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että alueet ovat omatoimiseen ja seuratoiminnan käyttöön tarkoitettuja, ja että käyttö voi olla olosuhderiippuvaista (esim. talvilajit)."
    :user-instruction
    "1. Vapaa käyttö
Kerro, että suunnistusalueet ovat pääosin vapaasti käytettävissä jokamiehenoikeuksien mukaisesti.

2. Karttojen ja reittien tarkistaminen
Ohjaa asiakas tutustumaan suunnistuskarttoihin ja rastiverkostoihin asiointikanavan tai seuran kautta, jos sellaisia on tarjolla.

3. Tapahtumat ja varatut käyttöajat
Mainitse, jos alueella järjestetään ajoittain suunnistustapahtumia, jotka voivat vaikuttaa muuhun käyttöön, ja ohjaa tarkistamaan tilanne asiointikanavasta.

4. Toimiminen alueella
Kerro, että asiakas liikkuu alueella omatoimisesti, noudattaa jokamiehenoikeuksia ja huomioi muut käyttäjät, kuten metsästäjät ja maanomistajat."}

   ;; Maastohiihtokeskukset
   4600
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa maastohiihtoon ja ampumahiihtoon tarkoitettuja keskuksia ja alueita. Voit mainita esimerkiksi hiihtomaat ja ampumahiihtokeskukset.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa maastohiihdon ja ampumahiihdon harjoittelun ja harrastamisen. Voit mainita, että keskuksissa voi olla erilaisia latuverkostoja ja ampumahiihtoon liittyviä alueita.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu hiihdosta ja ampumahiihdosta kiinnostuneille harrastajille ja kilpailijoille. Voit todeta, että se soveltuu eritasoisille käyttäjille.

4. Palvelun luonne
Mainitse, että toiminta on pääosin omatoimista, mutta osa alueista voi olla varattu seuratoimintaan ja kilpailuihin. Käyttö on olosuhderiippuvaista."
    :user-instruction
    "1. Latujen ja alueiden kunto
Ohjeista asiakas tarkistamaan latujen ja alueiden ajantasainen kunto ja avoinnaolo asiointikanavasta ennen käyntiä.

2. Vapaa käyttö
Kerro, että hiihtomaat ja ladut ovat yleensä vapaasti käytettävissä, kun ne ovat avoinna.

3. Ampumahiihtoalueiden käyttö
Mainitse, että ampumahiihtoon liittyvien alueiden (esim. ampumaradat) käyttö voi edellyttää erillistä lupaa tai seuran kautta tapahtuvaa varaamista, ja ohjaa tarkistamaan käytännöt asiointikanavasta.

4. Toimiminen alueella
Kerro, että asiakas liikkuu alueella omatoimisesti ja noudattaa alueen sääntöjä ja turvallisuusohjeita."}

   ;; Kiipeilypaikat
   4700
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa ulkona sijaitsevia kiipeilyyn tarkoitettuja paikkoja. Voit mainita esimerkiksi kiipeilykalliot ja muut ulkokiipeilypaikat.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa kiipeilyn harrastamisen luonnonympäristössä. Voit mainita, että paikat voivat soveltua eritasoisille kiipeilijöille.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu kiipeilystä kiinnostuneille harrastajille. Voit todeta, että käyttö edellyttää lajitaitoja ja asianmukaisia varusteita.

4. Palvelun luonne
Mainitse, että toiminta on omatoimista ja perustuu käyttäjän omaan vastuuseen ja turvallisuusosaamiseen."
    :user-instruction
    "1. Vapaa käyttö
Kerro, että kiipeilypaikat ovat pääosin vapaasti käytettävissä.

2. Turvallisuus ja omavastuu
Mainitse, että kiipeily tapahtuu omalla vastuulla ja edellyttää asianmukaista osaamista, varusteita ja turvallisuuskäytäntöjen noudattamista.

3. Alueen kunto ja mahdolliset rajoitukset
Ohjeista asiakas tarkistamaan alueen kunto ja mahdolliset käyttörajoitukset (esim. luonnonsuojeluun liittyvät) asiointikanavasta.

4. Varusteet
Kerro, että asiakas tuo omat kiipeilyvälineensä, ellei alueella ole erillistä varustevuokrausta."}

   ;; Ampumaurheilupaikat
   4800
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa ampumaurheiluun tarkoitettuja ulkoalueita. Voit mainita esimerkkeinä ampumaradat, ampumaurheilukeskukset ja jousiammuntaradat.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa ampumaurheilun ja jousiammunnan harjoittelun ja harrastamisen. Voit mainita yleisesti, että paikat soveltuvat erilaisiin ampumalajeihin.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu ampumaurheilusta ja jousiammunnasta kiinnostuneille harrastajille. Voit pitää kohderyhmän yleisenä.

4. Palvelun luonne
Mainitse, että toiminta tapahtuu rakennetuilla ja valvotuilla alueilla, ja että käyttö perustuu turvallisiin toimintatapoihin ja ohjeisiin."
    :user-instruction
    "1. Käytön edellytykset
Kerro, että ampumaurheilupaikkojen käyttö edellyttää turvallisuusohjeiden noudattamista, ja että tilojen käyttäminen voi edellyttää seuran jäsenyyttä tai erillistä sopimusta, jos kunta toimii näin.

2. Miten käyttövuoroja saa
Ohjeista asiakas tarkistamaan mahdolliset vuorot, harjoitusajat tai varattavat ajat asiointikanavasta.

3. Turvallisuus
Mainitse, että ampumaurheilupaikoilla on tiukat turvallisuusohjeet, jotka käyttäjän on aina luettava ja noudatettava.

4. Omatoiminen harjoittelu
Kerro, että omatoiminen harjoittelu on mahdollista vain niille käyttäjille, joilla on lupa tai sovittujen käytäntöjen mukainen oikeus.

5. Lisätiedot ja yhteydenotot
Ohjaa asiakas ottamaan tarvittaessa yhteyttä palvelun asiointikanavaan tai seuraan, jos hän haluaa käyttää tiloja eikä ole varma toimintatavasta."}

   ;; Veneurheilupaikat
   5100
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa vesiurheiluun ja veneilyharrastuksiin tarkoitettuja paikkoja. Voit mainita esimerkiksi soutu-, melonta-, purjehdus- ja vesihiihtoalueet.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa erilaisten vesiurheilulajien harrastamisen, kuten
- melonnan ja soudun
- purjehduksen
- vesihiihdon
- muun vesillä tapahtuvan urheilun.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu vesiurheilusta kiinnostuneille harrastajille ja seuroille. Voit todeta, että se soveltuu eri-ikäisille käyttäjille.

4. Palvelun luonne
Mainitse, että toiminta tapahtuu pääosin omatoimisesti ja luonnonolosuhteissa, ja että sää- ja vesistöolosuhteet vaikuttavat käyttöön."
    :user-instruction
    "1. Käytettävyys ja vapaa käyttö
Kerro, voiko vesiurheilupaikkoja käyttää omatoimisesti, kun ne ovat avoinna ja olosuhteet ovat turvalliset. Mainitse, että alueilla tulee noudattaa vesiliikenteen sääntöjä ja paikan käyttöohjeita.

2. Varauskäytännöt (jos niitä on)
Jos paikan käyttö edellyttää varausta tietyille ajoille, ohjeista asiakas tarkistamaan varaustilanteen asiointikanavasta.

3. Varusteet ja omatoiminen toiminta
Mainitse, että asiakas käyttää palvelua omilla varusteilla (esim. vene, kanootti, kajakki, SUP-lauta), ellei kunnalla ole erillistä lainaus- tai vuokrausjärjestelmää. Ohjeista huomioimaan turvallisuus ja mahdolliset pelastusliivisuositukset yleisellä tasolla.

4. Reittien ja alueiden tarkistaminen
Ohjaa asiakas tutustumaan reitteihin ja alueisiin asiointikanavassa, jos kunta tarjoaa reittikarttoja tai aluekohtaisia tietoja."}

   ;; Urheiluilmailualueet
   5200
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa urheiluilmailuun tarkoitettuja alueita ja tiloja. Voit mainita ilmailukeskukset, lentopaikat ja muut harrastukseen soveltuvat alueet.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa erilaisten urheiluilmailulajien harrastamisen, kuten purjelento, moottorilento tai muu harrasteilmailu. Pidä mahdolliset esimerkit lyhyinä.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu urheiluilmailusta kiinnostuneille ja harrastajille. Voit mainita, että se soveltuu myös lajin aktiivisille toimijoille.

4. Palvelun luonne
Mainitse, että toiminta tapahtuu lajiin suunnitelluilla alueilla. Voit todeta, että käyttö on olosuhderiippuvaista (sää). Vältä lupiin ja turvallisuusohjeisiin viittaamista yksityiskohtaisesti."
    :user-instruction
    "1. Käytön edellytykset
Kerro, että urheiluilmailualueen käyttö edellyttää voimassa olevia lupia ja toimimista lajia koskevien määräysten ja turvallisuusohjeiden mukaisesti. Mainitse, että pääsy voi edellyttää käyttäjä- tai kerhokohtaista lupaa, jos tällainen käytäntö on tyypillinen.

2. Alueen aukiolo ja olosuhderiippuvuus
Kerro, että aluetta voi käyttää ympäri vuoden, jos sää- ja kenttäolosuhteet sen sallivat. Ohjeista tarkistamaan ajantasainen käytettävyys asiointikanavasta.

3. Varaus- ja toimintakäytännöt
Ohjaa tarvittaessa tarkistamaan, onko alueella varattavia lentovuoroja, tapahtumia tai harjoitusjaksoja. Jos toiminta tapahtuu seurojen tai kerhojen kautta, mainitse, että käyttäjän tulee toimia niiden ohjeiden mukaan.

4. Turvallisuus ja kenttäohjeet
Korosta, että urheiluilmailualueella on tiukat turvallisuusohjeet, joita tulee noudattaa kaikessa toiminnassa. Mainitse, että käyttäjän tulee perehtyä kentän toimintaohjeisiin.

5. Omatoiminen toiminta ja varusteet
Kerro, että ilmailutoiminta tapahtuu omilla ja tarkoituksenmukaisilla välineillä sekä että käyttäjä huolehtii omasta ja muiden turvallisuudesta. Mainitse yleisesti, että toiminta edellyttää lajin vaatimaa koulutusta ja pätevyyttä."}

   ;; Moottoriurheilualueet
   5300
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa moottoriurheiluun tarkoitettuja alueita ja ratoja. Voit mainita esimerkiksi moottoriradat, kartingradat ja muut moottoriurheilualueet.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa erilaisten moottoriurheilulajien harrastamisen ja harjoittelun. Voit mainita yleisesti, että alueet on suunniteltu lajille sopiviksi ja turvallisiksi harjoitteluympäristöiksi.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu moottoriurheilusta kiinnostuneille ja harrastajille. Ei rajata tarkemmin (esim. seurat, lisenssit kuuluvat toimintaohjeeseen).

4. Palvelun luonne
Mainitse, että toiminta tapahtuu erikseen rakennetuilla ja lajeihin varatuilla alueilla. Voit todeta, että käyttö perustuu paikkakohtaisiin käytäntöihin ja turvallisuusperiaatteisiin."
    :user-instruction
    "1. Käytön edellytykset
Kerro, että moottoriurheilupaikkojen käyttö edellyttää turvallisuusohjeiden ja radan sääntöjen noudattamista. Mainitse, että osa moottoriradoista on käytettävissä vain organisoidun toiminnan, seurojen, kerhojen tai tapahtumien aikana.

2. Varaus- ja käyttövuorot
Ohjeista asiakas tarkistamaan radan varaus- ja harjoitusvuorot asiointikanavasta.

3. Turvallisuus ja varusteet
Mainitse, että käyttäjällä tulee olla asianmukaiset suojavarusteet ja soveltuva ajokalusto. Korosta, että radan henkilökunnan tai järjestäjän ohjeita on noudatettava.

4. Alueen olosuhteet ja mahdolliset rajoitukset
Ohjeista tarkistamaan, onko radalla tilapäisiä sulkuja, tapahtumia, kunnossapitoa tai olosuhderajoituksia.

5. Omatoiminen toiminta ja muut käyttäjät
Kerro, että omatoiminen ajo tapahtuu radan sääntöjen ja sovittujen vuorojen puitteissa. Ohjeista huomioimaan muut radankäyttäjät ja noudattamaan radan liikenne- ja ajolinjasääntöjä."}

   ;; Hevosurheilu
   6100
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa hevosurheiluun ja ratsastukseen tarkoitettuja paikkoja. Voit mainita esimerkkeinä ratsastuskentät, maneesit ja raviradat.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa ratsastuksen ja muun hevosurheilun harrastamisen. Voit mainita, että paikkoja käytetään harjoitteluun ja harrastustoimintaan.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu hevosharrastajille ja ratsastuksesta kiinnostuneille. Vältä rajaamasta vain aktiiviharrastajiin.

4. Palvelun luonne
Mainitse, jos toimintaa voidaan harjoittaa sekä omatoimisesti että ohjatusti. Voit todeta, että käyttö perustuu paikkakohtaisiin käytäntöihin."
    :user-instruction
    "1. Käyttö ja saavutettavuus
Kerro, kenen käytössä ratsastuskenttä, -maneesi tai rata on ja milloin sitä saa käyttää. Kerro myös, onko käyttö maksullista.

2. Varaustilanteen tarkistaminen
Ohjeista asiakas tarkistamaan varaustilanteen asiointikanavasta, jos kunnassa käytetään varausjärjestelmää.

3. Lajikohtaiset turvallisuus- ja käyttöohjeet
Mainitse, että hevosurheilupaikoilla tulee noudattaa turvallisuusohjeita, kuten hevosten ja ratsastajien etäisyyksien huomioimista ja kentän kiertosääntöjä. Jos ravirataa käytetään, kerro että sen käyttö noudattaa rata-alueen omia sääntöjä.

4. Varusteet ja omatoiminen toiminta
Kerro, että asiakas huolehtii omista varusteistaan, kuten hevosesta ja turvavarusteista."}

   ;; Koiraurheilu
   6200
   {:description
    "1. Mikä palvelu on?
Kerro, että palvelu tarjoaa koiraurheiluun ja -harrastukseen tarkoitettuja alueita ja tiloja. Voit mainita esimerkiksi koiraurheilualueet ja -hallit, kuten agilityradat.

2. Mitä palvelu sisältää?
Kuvaa, että palvelu mahdollistaa koiran kanssa harrastamisen ja harjoittelun. Voit mainita esimerkkejä lajeista, kuten agility, toko tai muu koiraharrastus, ilman pitkää lajilistaa.

3. Kenelle palvelu on?
Kerro, että palvelu on tarkoitettu koiranomistajille ja koiraharrastajille. Voit todeta, että alueet ja tilat soveltuvat erilaisiin käyttötarkoituksiin.

4. Palvelun luonne
Mainitse, että alueet ja tilat ovat koirien harrastuskäyttöön suunniteltuja ja rajattuja tai tarkoitukseen varattuja. Voit todeta, että käyttö on pääosin omatoimista, mutta osa tiloista voi olla varattavissa."
    :user-instruction
    "1. Käyttöajat ja vapaa käyttö
Kerro, että ulkoalueet ovat vapaassa käytössä, kun ne ovat avoinna. Mainitse, että joillakin alueilla voi olla yleiset käyttöajat, jotka on hyvä tarkistaa asiointikanavasta.

2. Hallien varaaminen
Mainitse, että koiraurheiluhallien käyttö voi edellyttää vuoron varaamista asiointikanavan kautta.

3. Alueen säännöt ja vastuut
Ohjeista noudattamaan alueella olevia opastetauluja ja järjestyssääntöjä. Mainitse, että koiranomistaja vastaa koiransa käyttäytymisestä, turvallisuudesta ja valvonnasta.

4. Varusteet ja omatoiminen toiminta
Kerro, että koiraharrastusalueet ovat yleensä omatoimisessa käytössä ja että käyttäjän tulee tuoda omat välineet ja varusteet, jos niitä tarvitaan.

5. Siisteys ja turvallisuus
Ohjeista asiakkaita huolehtimaan siisteydestä ja käyttämään alueen jäteastioita. Mainitse, että koiran tulee olla hallinnassa ja että käyttäjä huomioi muut koirat ja ihmiset."}
   })
