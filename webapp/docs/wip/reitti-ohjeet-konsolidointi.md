# Reitti-ohjeet: konsolidoitu luonnos (video + PDF → ohjeet-artikkelit)

Experiment: extracted with Gemini 3.1 Pro native YouTube video understanding
(2026-07-16), consolidated from three Lipasinfo videos and the 23.11.2023
jyu.fi route guide PDF, then grounded against the current codebase.

Sources:
- Video [XcZrIepjYe0](https://www.youtube.com/watch?v=XcZrIepjYe0) — Reittimäisen liikuntapaikan lisääminen (2024-02-22, 7:39)
- Video [DUa_aMbg9k0](https://www.youtube.com/watch?v=DUa_aMbg9k0) — Reittigeometrian tuonti tiedostosta (2024-02-22, 3:28)
- Video [M3tmvCVyoD4](https://www.youtube.com/watch?v=M3tmvCVyoD4) — Reitin katkaisu ja reittijäljen yksinkertaistaminen (2024-03-18, 7:44)
- PDF: Reittimäisen liikuntapaikan lisääminen, muokkaaminen ja poistaminen (JYU, 23.11.2023)

Grounding notes (video/PDF vs. current code):
- "Lisää reittiosa", "Poista reittiosa", "Katkaise reittiosa" — unchanged (`i18n/fi/map.edn`)
- Simplify dialog is now **"Yksinkertaista geometrioita"** (video showed "Yksinkertaista reittiosaa"); slider 0–10, step 0.5 (`map/views.cljs`)
- Self-intersection warning text is now: *"Muuta reitin kulkua niin, että reittiosa ei risteä itsensä kanssa. Voit tarvittaessa katkaista reitin useampaan osaan."* (video/PDF had older wording mentioning Retkikartta)
- Alt+klik vertex deletion — still current (OL `Modify` defaults, `map/editing.cljs:132`)
- **New since videos:** file imports are auto-simplified by default (`map/events.cljs` `process-imports`, turf-simplify level 3)
- Import formats: `.zip` (Shapefile), `.kml`, `.gpx`, `.json`, `.geojson` (`map/import.cljs:20`)
- "Laske reitin pituus automaattisesti" calculator — still current (`sports_sites/views.cljs:404`)
- Delete reasons: Poistettu käytöstä pysyvästi / väliaikaisesti / Väärä tieto (`i18n/fi/status.edn`)

Proposed CMS structure: split the current single page
(`reittimaisen-liikuntapaikan-lisaaminen-ja-muokkaaminen`, today only PDF+video
embeds, zero text → nearly invisible to the assistant KB) into four task-sized
pages. One page = one KB doc, so task-sized pages retrieve better.

---

## Sivu 1: Reittimäisen liikuntapaikan lisääminen (slug: reittimaisen-liikuntapaikan-lisaaminen-ja-muokkaaminen)

**Summary:** Miten uusi reittimäinen liikuntapaikka (esim. kuntorata, latu, ulkoilureitti) lisätään LIPAS-järjestelmään piirtämällä reitti kartalle, ja mitä tietoja reitistä tallennetaan.

### Yleistä reittien tallentamisesta

LIPAS-tietokantaan kerätään tiedot Suomen liikuntaan ja ulkoiluun varustetuista reiteistä. Tallennettavien reittien tulee olla hoidettuja, niiden käyttöön tulee olla mahdolliset maanomistajien luvat tai sopimukset, ja reitillä on oltava omistaja sekä ylläpitäjä. Lisäksi reitin tulee olla opastettu maastomerkeillä tai sen käyttö on muilla tavoin ohjeistettua. Pelkästään jokaisenoikeuksin käytettäviä polkuja ei tallenneta LIPAS-tietokantaan.

Erityinen painopiste on kuntien ja virkistysalueyhdistysten ylläpitämissä liikunta- ja ulkoilureiteissä. Metsähallituksen ylläpitämiä reittejä ei erityisesti kerätä, sillä Metsähallitus huolehtii oman reittiverkostonsa tiedoista omissa järjestelmissään.

Reitit on luokiteltu useisiin liikuntapaikkatyyppeihin (esim. 4401 Kuntorata, 4402 Latu, 4403 Kävelyreitti/ulkoilureitti, 4405 Retkeilyreitti, 4411 Maastopyöräilyreitti). Yksi fyysinen reitti voidaan kuvata useamman tyypin avulla: tyypillisesti sama ura on talvella latu ja kesällä kuntorata. Tyyppiluokittelun kuvaukset löydät Ohjeet-osion Tyyppiluokkaselaimesta.

### Reitin piirtäminen kartalle

**Esitiedot:** Kirjautuminen (lipas.fi/kirjaudu) ja muokkausoikeudet kyseisen kunnan liikuntapaikkoihin. Jos sinulla ei ole tunnusta, rekisteröidy ja määritä mihin tietoihin haluat muokkausoikeudet — ylläpitäjät vahvistavat tunnuksesi.

1. Napsauta karttanäkymän vasemman alakulman **+**-painiketta (Uusi liikuntapaikka).
2. Valitse **Liikuntapaikkatyyppi** — voit hakea tyyppiä kirjoittamalla tai valita listasta. Tyypin lyhyt kuvaus näytetään valinnan yhteydessä. Napsauta **OK**.
3. Kohdista kartta reitin alueelle siirtämällä, zoomaamalla tai osoitehaulla. Jos näet tekstin *"Kartta täytyy zoomata lähemmäs"*, lähennä karttaa kunnes **Lisää kartalle** -painike aktivoituu.
4. Napsauta **Lisää kartalle** ja piirrä reitti: jokainen hiiren napsautus lisää yhden reittipisteen, ja pisteiden välille piirtyy viiva. Päätä reittiosa **kaksoisnapsauttamalla**.
5. Jos reitti koostuu useista erillisistä osista, valitse **Lisää reittiosa** ja piirrä seuraava osa samalla tavalla.
6. Napsauta **Valmis**.
7. Täytä reitin tiedot ja napsauta **Tallenna**. Onnistuneesta tallennuksesta näytetään ilmoitus *"Tallennus onnistui"*.

**Vinkki:** Piirrä reitti myötäilemään maastossa olevaa uraa mahdollisimman tarkasti. Voit hyödyntää taustakartan lisäksi muita karttatasoja (esim. ilmakuvaa).

**Huom:** Reittiviiva ei saisi leikata itseään. Jos reitti risteää itsensä kanssa, ongelmakohta osoitetaan punaisella merkillä: *"Muuta reitin kulkua niin, että reittiosa ei risteä itsensä kanssa. Voit tarvittaessa katkaista reitin useampaan osaan."* Katso ohje reittigeometrian korjaamisesta.

### Reitin tiedot

**Perustiedot:** Liikuntapaikan tilan oletusvalinta on *Toiminnassa*; muut vaihtoehdot ovat *Suunniteltu* ja *Poistettu käytöstä väliaikaisesti*. Pakolliset kentät on merkitty tähdellä, ja ne on täytettävä ennen tallennusta. Katuosoite on pakollinen — jos reitillä ei ole selkeää osoitetta, käytä lähintä mahdollista osoitetta, esimerkiksi lähtöpisteen tai parkkipaikan osoitetta. Reitin nimen lisäksi voit antaa erillisen markkinointinimen; jos erityistä markkinointinimeä ei ole, täytä vain tavallinen nimikenttä. Yhteystiedot-kenttään voit määrittää, kuka vastaa liikuntapaikan asiakaspalvelusta tai palautteista.

**Lisätiedot:** Lisätiedot-välilehden kentät vaihtuvat liikuntapaikkatyypin mukaan. Pintamateriaali ja reitin leveys koskevat oletuksena koko reittiä — jos reittiosilla on eri ominaisuudet, määritä tiedot kullekin reittiosalle erikseen. Reitin pituuden voit syöttää käsin tai antaa järjestelmän laskea sen automaattisesti (laskin-kuvake, *"Laske reitin pituus automaattisesti"*).

---

## Sivu 2: Reittigeometrian tuonti tiedostosta (slug-ehdotus: reittigeometrian-tuonti-tiedostosta)

**Summary:** Reitin geometrian voi piirtämisen sijaan tuoda valmiista tiedostosta (GPX, GeoJSON, KML, Shapefile), esimerkiksi GPS-laitteen tai urheilusovelluksen tallentamasta jäljestä.

**Esitiedot:** Kirjautuminen ja muokkausoikeudet sekä reittitiedosto tuetussa muodossa: **GPX**, **GeoJSON** (.json/.geojson), **KML** tai **Shapefile** (zip-pakattuna). GPX-tiedoston koordinaatiston on oltava WGS84 (GPS-laitteiden ja sovellusten vakiomuoto).

1. Kohdista kartta reitin alueelle esimerkiksi **Hae kartan alueelta** -haulla.
2. Napsauta vasemman alakulman **+**-painiketta, valitse liikuntapaikkatyyppi ja napsauta **OK**.
3. Valitse **Tuo tiedostosta** -välilehti (Piirrä-välilehden vieressä). Jos näet varoituksen *"Kartta täytyy zoomata lähemmäs"*, lähennä karttaa kunnes se poistuu.
4. Napsauta **Tuo geometriat tiedostosta** -painiketta. *"Tuo geometriat"* -ikkuna aukeaa.
5. Valitse tiedosto koneeltasi (Valitse tiedosto / Choose File).
6. Ikkunaan listautuvat tiedostosta tunnistetut reittiosat. Valitse tuotavat osat valintaruuduista ja napsauta **Tuo valitut**. Reitti piirtyy kartalle.
7. Tarkista, että geometria näyttää oikealta, ja napsauta **Valmis**. Täytä sitten reitin tiedot ja tallenna.

**Vinkit:**
- Jos tiedostossa on useita erillisiä reittiosia, ne kaikki listautuvat tuonti-ikkunaan ja voit valita niistä haluamasi.
- Tuonti-ikkunassa voi näkyä ylimääräisiä tai oudosti nimettyjä rivejä tiedoston merkistökoodauksen takia — valitse vain selkeästi nimetyt reittiosat.
- Järjestelmä yksinkertaistaa tuotua reittijälkeä automaattisesti kevyesti, sillä esimerkiksi GPS-jäljet sisältävät usein tarpeettoman tiheästi pisteitä. Jos jälki on silti liian tiheä, käytä Yksinkertaista-työkalua (katso ohje reittigeometrian korjaamisesta).
- Voit tuoda geometrian myös olemassa olevaan reittiin: avaa reitti muokkaustilaan ja valitse työkaluvalikosta *"Tuo geometriat tiedostosta"*.

---

## Sivu 3: Reittigeometrian korjaaminen ja yksinkertaistaminen (slug-ehdotus: reittigeometrian-korjaaminen-ja-yksinkertaistaminen)

**Summary:** Miten reittiviivan virheet (itsensä kanssa risteävä reitti, päällekkäiset pisteet) korjataan ja miten liian tiheä reittijälki yksinkertaistetaan.

**Esitiedot:** Reitti on valittuna ja muokkaustila on päällä (kynäkuvake **Muokkaa**). Muokkaustilassa reittiviivan pisteet tulevat näkyviin.

### Reittipisteiden siirtäminen, lisääminen ja poistaminen

- **Siirrä** reittipistettä tarttumalla siihen hiirellä ja raahaamalla se uuteen kohtaan.
- **Lisää** uusi piste napsauttamalla reittiviivaa kohdasta, johon haluat pisteen.
- **Poista** piste pitämällä **Alt-näppäin** pohjassa ja napsauttamalla pistettä.

### Itsensä kanssa risteävän reitin korjaaminen

Jos reittiviiva leikkaa itsensä, ongelmakohdassa näytetään punainen huomiomerkki: *"Muuta reitin kulkua niin, että reittiosa ei risteä itsensä kanssa. Voit tarvittaessa katkaista reitin useampaan osaan."* LIPAS sallii tallentamisen, mutta risteävä reitti voi estää tietojen hyödyntämisen muissa palveluissa.

1. Valitse työkaluvalikosta **Katkaise reittiosa** (saksikuvake).
2. Napsauta reittiviivaa risteyskohdassa. Katkaisu jakaa reitin kahdeksi reittiosaksi, jolloin virhemerkki poistuu.
3. Varmista katkaisu viemällä hiiri viivan päälle: erillinen reittiosa korostuu omana viivanaan.

Jos ongelmakohdassa on tiheä sumppu päällekkäisiä pisteitä (esim. GPS-jäljen "sykerö"), älä katkaise sokkona: poista ensin turhat päällekkäiset pisteet **Alt + napsautus** -toiminnolla yksi kerrallaan, kunnes reitin kulku selkeytyy, ja katkaise vasta sitten tarvittaessa.

### Reittijäljen yksinkertaistaminen

Tuodussa GPS-jäljessä on usein pisteitä tarpeettoman tiheästi, mikä tekee reitistä raskaan käsitellä.

1. Valitse työkaluvalikosta **Yksinkertaista**. Kartan alareunaan avautuu *"Yksinkertaista geometrioita"* -säädin.
2. Vedä liukusäädintä (asteikko 0–10) oikealle: järjestelmä vähentää reittipisteitä ja näyttää tuloksen kartalla reaaliajassa.
3. Zoomaa lähemmäs ja tarkista, että yksinkertaistettu viiva seuraa yhä maastossa kulkevaa uraa. Jos viiva oikoo liikaa, pienennä säätöä tai korjaa yksittäisiä pisteitä raahaamalla.
4. Hyväksy napsauttamalla **OK**, tai hylkää napsauttamalla **Peruuta**.
5. Voit perua yksinkertaistamisen **Kumoa**-painikkeella (nuolikuvake työkaluvalikossa).

**Varoitus:** Yksinkertaistaminen on kompromissi tarkkuuden ja käsiteltävyyden välillä — tarkista tulos aina kartalta. Jos hyväksyt maksimiyksinkertaistuksen, työkalu ei enää vähennä pisteitä samasta osasta uudelleen; loput pisteet on poistettava käsin (Alt + napsautus).

### Reittiosan poistaminen

Kokonaisen reittiosan voit poistaa valitsemalla työkaluvalikosta **Poista reittiosa** (roskakorikuvake) ja napsauttamalla poistettavaa osaa.

Muista lopuksi tallentaa muutokset (**Tallenna**).

---

## Sivu 4: Reittimäisen liikuntapaikan poistaminen (slug-ehdotus: reittimaisen-liikuntapaikan-poistaminen)

**Summary:** Miten reitti poistetaan LIPAS-järjestelmästä ja mitä eri poistosyyt tarkoittavat.

**Esitiedot:** Poistettava reitti on valittuna kartalta ja sinulla on muokkausoikeudet siihen.

1. Napsauta työkalurivin **roskakorikuvaketta** (Poista liikuntapaikka).
2. Valitse aukeavassa ikkunassa **Poiston syy**:
   - *Poistettu käytöstä pysyvästi* — reitti ei ole enää käytössä. Kohde arkistoituu, mutta se löytyy edelleen liikuntapaikkahaulla.
   - *Poistettu käytöstä väliaikaisesti* — reitti on tilapäisesti pois käytöstä. Kohde arkistoituu vastaavasti.
   - *Väärä tieto* — kohde on virheellinen (esim. kahteen kertaan tallennettu). Reitti poistetaan lopullisesti, eikä se näy enää hauissa eikä kartalla.
3. Valitse tarvittaessa **Vuosi**, jolloin poisto tapahtui.
4. Vahvista napsauttamalla **Poista**. Onnistumisesta näytetään ilmoitus.

**Vinkki:** Jos reitti on pois käytöstä vain määräajan (esim. reittimuutos tai kunnostus), käytä tilaa *Poistettu käytöstä väliaikaisesti* — näin reitin historia ja tiedot säilyvät.
