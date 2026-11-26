# PTV-integraatio: help articles (fi)

Source for the help centre's **PTV-integraatio** section (`ptv-integraatio`).
Supersedes `ptv-sync-review-help-articles.md`. Each page below starts with a
`<!-- page ... -->` marker carrying its id, slug and old slugs (aliases), then
the title (`##`), the summary (`>`) and the body. The UI links to
`ptv-integraatio/ptv-muutosten-lapikaynti`, so keep that slug.

Loaded into the help CMS as a draft with `dev`-time REPL code (see the PR
notes); the CMS publishes it. Pages refer to each other by name, not by link:
a `?ohje=` link inside an article reloads the whole app.

<!-- page id=new slug=mika-ptv-integraatio-on -->
## Mikä PTV-integraatio on?
> Lipas voi viedä kunnan liikuntapaikat Suomi.fi-palvelutietovarantoon (PTV), jolloin samoja tietoja ei tarvitse ylläpitää kahdessa paikassa.

Suomi.fi-palvelutietovaranto (PTV) on julkisten palveluiden yhteinen tietovaranto. PTV:hen kuvatut palvelut näkyvät Suomi.fi-verkkopalvelussa ja muissa palveluissa, jotka hakevat tietoja PTV:stä.

PTV-integraation avulla kunta vie liikuntapaikkojensa tiedot Lipaksesta PTV:hen. Tiedot ylläpidetään Lipaksessa, ja muutokset viedään sieltä PTV:hen. Samoja tietoja ei tarvitse kirjoittaa kahteen paikkaan.

**Mitä PTV:hen viedään**

- **Palvelu** kertoo, mitä kunta tarjoaa, esimerkiksi *Uimahallit* tai *Lähiliikuntapaikat*. Kullekin liikuntapaikkojen tyyppiluokalle tehdään yksi palvelu.
- **Palvelupaikka** on yksittäinen paikka, jossa palvelua saa, esimerkiksi tietty uimahalli. Jokaisesta PTV:hen viedystä liikuntapaikasta tulee oma palvelupaikka, joka liitetään palveluun.

Palvelupaikan nimi, osoite, sijainti ja yhteystiedot tulevat liikuntapaikan tiedoista Lipaksesta. Lisäksi palvelupaikalle kirjoitetaan tiivistelmä ja kuvaus. Tekoäly voi laatia niistä luonnokset.

PTV:hen voidaan viedä kunnan ja kuntaenemmistöisten yritysten omistamat liikuntapaikat, jotka ovat toiminnassa tai poissa käytöstä väliaikaisesti. Huoltorakennuksia ja opastuspisteitä ei viedä.

**Kuka tekee mitä**

- **PTV-käyttäjä** on kunnan työntekijä, jolla on Lipaksessa PTV-oikeudet kunnan liikuntapaikkoihin. Hän ottaa integraation käyttöön, tarkistaa kuvaukset ja vie muutokset PTV:hen.
- **Muut Lipaksen käyttäjät** muokkaavat liikuntapaikkoja tavalliseen tapaan. Heidän muutoksensa tallentuvat Lipakseen, ja PTV-käyttäjä vie ne PTV:hen.
- **DVV:n katselmoija** voi tarkistaa PTV:hen viedyt tekstit ja pyytää niihin korjauksia.

**Mistä aloittaa**

Lue ensin ohjeet *PTV-integraation käyttöönoton valmistelu ja vastuut*, *PTV:n IN-rajapinnan käyttöluvan hakeminen* ja *PTV-käyttöoikeudet ja PTV-työkalun avaaminen*. Itse käyttöönotto tehdään ohjeen *Käyttöönotto: palvelut ja palvelupaikat PTV:hen* mukaan.

<!-- page id=d527155f-0cb2-4af7-b80e-e383fb94650a slug=ptv-integraation-kayttoonoton-valmistelu-ja-vastuut -->
## PTV-integraation käyttöönoton valmistelu ja vastuut
> Ennen käyttöönottoa sovitaan, kuka integraatiota hoitaa, mitkä liikuntapaikat viedään PTV:hen ja millä kielillä.

Liikuntapaikkatietojen vieminen Palvelutietovarantoon (PTV) vaatii valmistelua organisaatiossa.

1. **Vastuut:** Nimeä henkilöt, jotka hoitavat integraatiota (PTV-käyttäjät). He tarkistavat kuvaukset ja vievät muutokset PTV:hen. Sovi myös, kuka huolehtii liikuntapaikkatietojen ajantasaisuudesta Lipaksessa.
2. **Liikuntapaikkatiedot:** Tarkista, että liikuntapaikkojen nimet, osoitteet, sijainnit, yhteystiedot ja omistajat ovat Lipaksessa ajan tasalla. Nämä tiedot siirtyvät PTV:hen sellaisinaan.
3. **Vietävät liikuntapaikat:** Päätä, viedäänkö kaikki liikuntapaikat vai vain tietyt tyyppiluokat, esimerkiksi uimahallit ja kuntosalit. PTV:hen voidaan viedä kunnan ja kuntaenemmistöisten yritysten omistamat liikuntapaikat.
4. **Kielet:** Päätä, julkaistaanko tiedot suomen lisäksi ruotsiksi tai englanniksi. Lipaksen tuki ottaa kielet käyttöön organisaatiollenne (lipasinfo@jyu.fi). Katso ohje *Kieliversiot: ruotsi ja englanti*.
5. **PTV:ssä jo olevat tiedot:** Selvitä, onko kunnalla PTV:ssä jo liikuntapalveluja tai -paikkoja. Ne liitetään Lipakseen sen sijaan, että tehtäisiin uudet (ks. *Liittäminen PTV:ssä jo oleviin palveluihin ja palvelupaikkoihin*).
6. **Käytännöt:** Kirjaa vastuut työnkuviin, jotta integraation ylläpito ei ole yksittäisten henkilöiden varassa.

<!-- page id=a4050c21-33c8-4a2a-b47b-d11d03baac72 slug=ptvn-in-rajapinnan-kayttoluvan-hakeminen -->
## PTV:n IN-rajapinnan käyttöluvan hakeminen
> Kunta hakee Suomi.fi-palveluhallinnasta luvan, jotta Lipas voi viedä tietoja PTV:hen kunnan puolesta.

Jotta LIPAS-tietoja voidaan siirtää PTV:hen, kunnan on haettava IN-rajapinnan käyttölupa Suomi.fi-palveluhallinnassa. 

1. Tunnistaudu vahvasti Suomi.fi-palveluhallintaan ja klikkaa Palvelutietovaranto-kohdassa "Hae käyttölupaa". 
2. Täytä hakemukseen organisaation tiedot. 
3. Ilmoita tekniseksi yhteyshenkilöksi Jyväskylän yliopiston tiedot: tapani.j.laakso@jyu.fi ja lipasinfo@jyu.fi. 
4. Ilmoita alihankkijaksi Jyväskylän yliopisto (Y-tunnus 0245894-7) ja mainitse, että käytätte yliopiston API-avainta API@jyu.fi. 
5. Kun lupa on myönnetty (käsittelyaika 1-2 viikkoa), PTV-tuki kytkee organisaation API-avaimeen ja toimittaa organisaatiotunnisteen (ID) Jyväskylän yliopistolle.

<!-- page id=85910b9e-f3fe-4813-9679-826c86d45127 slug=ptv-kayttooikeudet-ja-ptv-tyokalun-avaaminen aliases=lipas-jarjestelman-ptv-kayttooikeuksien-hankkiminen -->
## PTV-käyttöoikeudet ja PTV-työkalun avaaminen
> PTV-integraatiota käytetään PTV-käyttäjän oikeuksilla. PTV-työkalu avataan liikuntapaikkahaun PTV-painikkeesta.

PTV-integraatiota voivat käyttää Lipaksessa käyttäjät, joilla on **PTV-käyttäjä**-oikeudet kunnan liikuntapaikkoihin.

**Oikeuksien hankkiminen**

1. Jos sinulla ei ole vielä Lipas-tunnusta, rekisteröidy osoitteessa lipas.fi/rekisteroidy ja mainitse lomakkeella, että tarvitset PTV-oikeudet.
2. Jos sinulla on jo tunnus, pyydä PTV-oikeuksia sähköpostitse osoitteesta lipasinfo@jyu.fi. Kerro, minkä kunnan liikuntapaikkoja oikeudet koskevat.
3. Kun oikeudet on myönnetty, kirjaudu ulos ja takaisin sisään, jotta ne tulevat voimaan.

**PTV-työkalun avaaminen**

1. Avaa Lipaksen liikuntapaikkahaku (karttanäkymä).
2. Paina hakupaneelin alareunan painikeriviltä **PTV**-painiketta. Painike näkyy, kun hakutulokset ovat listanäkymässä eikä mitään liikuntapaikkaa ole avattuna.
3. Jos sinulla on oikeudet vain yhden organisaation tietoihin, organisaatio valitaan automaattisesti. Muuten valitse organisaatio oikean yläkulman valikosta **Valitse organisaatio**.

Jos organisaatiovalikko on tyhjä, kunnallesi ei ole vielä määritetty PTV-organisaatiota. Ota yhteyttä Lipaksen tukeen.

**PTV-työkalun välilehdet**

- **Käyttöönotto**: palveluiden ja palvelupaikkojen ensimmäinen vienti PTV:hen.
- **Palvelut**: kunnan PTV-palvelut ja niiden tekstit.
- **Liikuntapaikat**: kunnan liikuntapaikat, niiden tila PTV:ssä ja niiden kuvaukset.

Lisäksi liikuntapaikan tiedoissa on **PTV**-välilehti, jolla yksittäisen liikuntapaikan voi viedä PTV:hen (ks. *Uuden liikuntapaikan vieminen PTV:hen*).

<!-- page id=867f68f3-3ca7-4539-adfc-52127e8d74d6 slug=kayttoonotto-palvelut-ja-palvelupaikat-ptvhen aliases=uusien-palveluiden-ja-palvelupaikkojen-luominen-ptv-integraatiolla -->
## Käyttöönotto: palvelut ja palvelupaikat PTV:hen
> Käyttöönotto-välilehdellä kunnan liikuntapaikat viedään PTV:hen kolmessa vaiheessa: valitaan liikuntapaikat, luodaan palvelut ja luodaan palvelupaikat.

PTV-työkalun Käyttöönotto-välilehdellä viedään kunnan liikuntapaikat PTV:hen ensimmäistä kertaa. Käyttöönotossa on kolme vaihetta. Vaiheiden otsikoita voi klikata, joten edelliseen vaiheeseen voi palata.

Kirjoitetut ja tekoälyn laatimat tekstit säilyvät vain selaimessa, kunnes ne viedään PTV:hen. Jos suljet sivun tai lataat sen uudelleen ennen vientiä, tekstit katoavat.

**Vaihe 1: Valitse liikuntapaikat**

1. Valitse **Vie kaikki liikuntapaikat** tai **Valitse tyyppiluokat**, jos haluat viedä vain osan liikuntapaikoista.
2. Paina **Seuraava**.

**Vaihe 2: Luo PTV-palvelut**

Jokaiselle valitulle tyyppiluokalle tarvitaan PTV:hen palvelu. Ne näkyvät oikealla otsikon **Perustettavat palvelut** alla.

1. Jos kunnalla on PTV:ssä jo sopiva palvelu, valitse se kohdasta **Löytyykö palvelu jo PTV:stä?**. Silloin uutta palvelua ei luoda (ks. *Liittäminen PTV:ssä jo oleviin palveluihin ja palvelupaikkoihin*).
2. Kirjoita muille palveluille **Tiivistelmä**, **Kuvaus** ja **Toimintaohje**. Voit laatia luonnokset kaikille palveluille kerralla painikkeella **Laadi luonnokset kaikille** tai yhdelle kerrallaan painikkeella **Laadi luonnokset tekoälyllä**.
3. Tarkista ja muokkaa tekstit. Kenttien alta saat vinkkejä, esimerkiksi *Mitä toimintaohjeeseen kannattaa kirjoittaa?*.
4. Jos organisaatiollanne on käytössä useampi kieli, käännä tekstit painikkeella **Käännä muille kielille**.
5. Paina **Vie palvelutietovarantoon**. Painike on käytettävissä, kun jokaisella palvelulla on suomenkielinen tiivistelmä, kuvaus ja toimintaohje.
6. Kun palvelut on luotu, paina **Seuraava**.

Lipas täydentää palveluille palveluluokat, asiasanat ja muut PTV:n vaatimat tiedot automaattisesti.

**Vaihe 3: Luo liikuntapaikoista palvelupaikat**

1. Avaa liikuntapaikka oikean reunan listasta.
2. Jos liikuntapaikka on jo PTV:ssä palvelupaikkana, valitse se kohdasta **Löytyykö palvelupaikka jo PTV:stä?**. Muuten PTV:hen luodaan uusi palvelupaikka.
3. Kirjoita **Tiivistelmä** ja **Kuvaus** tai laadi ne tekoälyllä. Liikuntapaikka liitetään automaattisesti tyyppiluokkansa palveluun.
4. Tarkista tekstit ja kytke päälle liukukytkin **Olen lukenut kuvaukset ja haluan, että tämä kohde viedään PTV:hen.** Kytkimen saa päälle, kun suomenkielinen tiivistelmä ja kuvaus on kirjoitettu.
5. Toista muille liikuntapaikoille. Liikuntapaikkoja, joiden kytkin on pois päältä, ei viedä PTV:hen.
6. Paina **Vie palvelutietovarantoon**.

Painike **Laadi luonnokset kaikille** laatii luonnokset kaikille listan liikuntapaikoille ja korvaa kentissä jo olevat tekstit.

Vienti käy liikuntapaikat läpi yksi kerrallaan, ja lopuksi näet, montako liikuntapaikkaa vietiin. Jos jonkin liikuntapaikan vienti epäonnistuu, vienti pysähtyy ja kertoo syyn. Korjaa syy ja paina **Jatka vientiä**.

Käyttöönoton jälkeen tietoja ylläpidetään Palvelut- ja Liikuntapaikat-välilehdillä (ks. *Tietojen ylläpito ja synkronointi PTV:hen*).

<!-- page id=88ea5168-5c5a-40a7-8ec0-fcf9aa854b21 slug=liittaminen-ptvssa-jo-oleviin-palveluihin-ja-palvelupaikkoihin aliases=lipas-kohteiden-yhdistaminen-olemassa-oleviin-ptv-palveluihin -->
## Liittäminen PTV:ssä jo oleviin palveluihin ja palvelupaikkoihin
> Jos kunnalla on PTV:ssä jo liikuntapalveluja tai palvelupaikkoja, Lipaksen tiedot liitetään niihin, jotta PTV:hen ei synny päällekkäisiä tietoja.

Jos kunta on kuvannut liikuntapalveluja PTV:hen jo ennen Lipas-integraatiota, liitä Lipaksen tiedot niihin. Näin PTV:hen ei synny kahta samaa palvelua tai palvelupaikkaa.

**Olemassa oleva palvelu**

1. Avaa Käyttöönotto-välilehden vaiheessa 2 tyyppiluokan palvelu otsikon **Perustettavat palvelut** alta.
2. Valitse oikea palvelu valikosta **Löytyykö palvelu jo PTV:stä?**.
3. Tyyppiluokka siirtyy listaan **Liitetty olemassa oleviin PTV-palveluihin**. Liitoksen voi purkaa painikkeella **Poista linkitys**.

Tyyppiluokan liikuntapaikat liitetään valittuun palveluun, eikä uutta palvelua luoda. Palvelun tekstit eivät muutu PTV:ssä. Voit päivittää niitä myöhemmin Palvelut-välilehdellä (ks. *Palvelut-välilehti: palveluiden muokkaaminen*).

**Olemassa oleva palvelupaikka**

1. Avaa liikuntapaikka Käyttöönotto-välilehden vaiheessa 3 tai Liikuntapaikat-välilehdellä.
2. Valitse palvelupaikka valikosta **Löytyykö palvelupaikka jo PTV:stä?**. Liikuntapaikat-välilehdellä painike on **Liitä olemassa olevaan palvelupaikkaan...**.
3. Jos palvelupaikan tiedot PTV:ssä eroavat Lipaksesta, Lipas näyttää erot. Valitse kunkin kohdalla **Pidä Lipaksen versio** tai **Ota PTV:n versio** (ks. *PTV-muutosten läpikäynti*).
4. Vie liikuntapaikka PTV:hen. Valinnat tulevat voimaan viennissä.

**Huomioi liitettäessä**

- Palvelut, jotka on PTV:ssä liitetty palvelupaikkaan, säilyvät ja kopioidaan Lipakseen.
- Aukioloajat säilyvät PTV:ssä, koska Lipaksessa ei ole niille kenttiä.
- Liikuntapaikan sijainti tulee aina Lipaksesta.
- Yhden palvelupaikan voi liittää vain yhteen liikuntapaikkaan.
- Jos PTV:ssä on jo samanniminen palvelupaikka, Lipas ehdottaa sen liittämistä painikkeella **Liitä tähän palvelupaikkaan**.
- Jos palvelupaikalla on PTV:ssä kieliversio, jota organisaatiollanne ei ole Lipaksessa käytössä, vienti poistaa sen PTV:stä. Pyydä kieli tarvittaessa käyttöön ennen vientiä (ks. *Kieliversiot: ruotsi ja englanti*).

<!-- page id=7a2b3ebb-2125-414c-9c09-66b786979a7c slug=tietojen-yllapito-ja-synkronointi-ptvhen -->
## Tietojen ylläpito ja synkronointi PTV:hen
> Tiedot ylläpidetään Lipaksessa. PTV-käyttäjä vie muutokset PTV:hen tallentamalla liikuntapaikan tai PTV-työkalun synkronointipainikkeilla.

Käyttöönoton jälkeen liikuntapaikkojen tiedot ylläpidetään Lipaksessa. Muutokset viedään PTV:hen synkronoimalla. Synkronointi korvaa PTV:n tiedot Lipaksen tiedoilla, joten tietoja ei kannata muokata PTV:ssä. Jos niitä on kuitenkin muokattu PTV:ssä, Lipas näyttää muutokset ennen synkronointia (ks. *PTV-muutosten läpikäynti*).

**Kun PTV-käyttäjä muokkaa liikuntapaikkaa**

Kun PTV-käyttäjä tallentaa liikuntapaikan, muutokset synkronoidaan samalla PTV:hen. Tallennus voi kestää hetken. Jos synkronointi epäonnistuu, liikuntapaikka tallentuu silti Lipakseen, ja Lipas kertoo syyn.

Hakutulosten pikamuokkauksessa muutos synkronoidaan vain, jos PTV:ssä ei ole läpikäymättömiä muutoksia. Muuten muutos jää Lipakseen, ja liikuntapaikka näkyy PTV-työkalussa tilassa **Estetty**. Käy silloin muutokset läpi ja synkronoi.

**Kun muu käyttäjä muokkaa liikuntapaikkaa**

Kun käyttäjä, jolla ei ole PTV-oikeuksia, tallentaa liikuntapaikan, muutos tallentuu vain Lipakseen. Liikuntapaikka näkyy PTV-työkalun Liikuntapaikat-välilehdellä tilassa **Valmis synkronoitavaksi**, kunnes PTV-käyttäjä synkronoi sen.

**Synkronointi PTV-työkalussa**

1. Avaa PTV-työkalun **Liikuntapaikat**-välilehti.
2. Valitse suodattimista **Valmis synkronoitavaksi**.
3. Avaa liikuntapaikka rivin alussa olevasta nuolesta ja tarkista tarvittaessa tiivistelmä ja kuvaus.
4. Paina **Synkronoi nyt**.

Voit myös synkronoida kaikki valmiit liikuntapaikat kerralla painikkeella **Synkronoi kaikki valmiit**. Se jättää väliin liikuntapaikat, joissa on ristiriitoja tai puuttuvia tietoja.

PTV-työkalussa muokatut tekstit säilyvät vain selaimessa, kunnes synkronoit. Jos suljet PTV-työkalun ennen synkronointia, muutokset katoavat.

**Muista päivittää kuvaukset**

Tiivistelmä ja kuvaus eivät päivity itsestään, kun liikuntapaikan muut tiedot muuttuvat. Jos esimerkiksi liikuntapaikan varustelu muuttuu, päivitä myös kuvaus.

Palveluiden tekstejä muokataan ja synkronoidaan Palvelut-välilehdellä (ks. *Palvelut-välilehti: palveluiden muokkaaminen*).

<!-- page id=new slug=uuden-liikuntapaikan-vieminen-ptvhen -->
## Uuden liikuntapaikan vieminen PTV:hen
> Käyttöönoton jälkeen lisätyt liikuntapaikat eivät siirry PTV:hen itsestään. PTV-käyttäjä vie ne PTV:hen liikuntapaikan PTV-välilehdeltä.

Käyttöönoton jälkeen lisätyt liikuntapaikat eivät siirry PTV:hen itsestään. PTV-käyttäjä vie ne PTV:hen joko liikuntapaikan PTV-välilehdeltä tai PTV-työkalun Liikuntapaikat-välilehdeltä.

**Liikuntapaikan PTV-välilehdeltä**

1. Avaa liikuntapaikka ja aloita muokkaus.
2. Siirry **PTV**-välilehdelle.
3. Kytke päälle **PTV-integraatio käytössä**. Organisaatio ja liikuntapaikan tyyppiluokkaa vastaava palvelu valitaan automaattisesti.
4. Kirjoita suomenkielinen **Tiivistelmä** ja **Kuvaus** tai laadi ne painikkeella **Laadi luonnokset tekoälyllä**. Muut kielet ovat vapaaehtoisia.
5. Tarkista, että listan **Viimeistele PTV-tiedot** kohdat ovat kunnossa.
6. Tallenna liikuntapaikka. Liikuntapaikka viedään PTV:hen tallennuksen yhteydessä.

Kun integraatio on päällä, liikuntapaikkaa ei voi tallentaa ilman suomenkielistä tiivistelmää ja kuvausta. Täydennä tekstit tai kytke integraatio pois.

Jos liikuntapaikan tyyppiluokalle ei vielä ole palvelua PTV:ssä, Lipas kertoo siitä. Luo palvelu ensin PTV-työkalun Palvelut-välilehdellä painikkeella **Luo uusi palvelu**.

PTV-välilehdeltä viety liikuntapaikka saa aina uuden palvelupaikan. Jos liikuntapaikka on jo PTV:ssä, käytä PTV-työkalua.

**PTV-työkalun Liikuntapaikat-välilehdeltä**

1. Valitse suodattimista **Integraatio pois**.
2. Avaa liikuntapaikka rivin alussa olevasta nuolesta.
3. Jos liikuntapaikka on jo PTV:ssä palvelupaikkana, liitä se painikkeella **Liitä olemassa olevaan palvelupaikkaan...**.
4. Kirjoita tai laadi tiivistelmä ja kuvaus.
5. Kytke päälle liukukytkin **Olen lukenut kuvaukset ja haluan, että tämä kohde viedään PTV:hen.**
6. Paina **Vie palvelutietovarantoon**.

<!-- page id=4f94e362-84ab-4e11-9de2-380d8bbac96e slug=ptv-tyokalun-liikuntapaikat-valilehden-tilat-ja-suodattimet aliases=ptv-tyokalun-liikuntapaikat-valilehden-suodattimet-ja-symbolit -->
## PTV-työkalun Liikuntapaikat-välilehden tilat ja suodattimet
> Liikuntapaikat-välilehti näyttää jokaisen liikuntapaikan tilan PTV:ssä. Suodattimilla löydät liikuntapaikat, jotka vaativat toimia.

PTV-työkalun Liikuntapaikat-välilehdellä näet organisaatiosi liikuntapaikat ja niiden tilan PTV:ssä. Avaa liikuntapaikka rivin alussa olevasta nuolesta.

**Tilat**

Integraatio-sarakkeen merkki kertoo liikuntapaikan tilan:

- **Ajan tasalla** (vihreä): Lipaksen ja PTV:n tiedot ovat samat.
- **Valmis synkronoitavaksi** (sininen): Lipaksessa on muutoksia, jotka synkronointi vie PTV:hen. Mitään ei tarvitse valita.
- **Ristiriita** (oranssi): Lipaksen ja PTV:n tiedot ovat ristiriidassa, yleensä koska tietoja on muutettu PTV:ssä. Valitse ennen synkronointia, mitkä tiedot jäävät voimaan (ks. *PTV-muutosten läpikäynti*).
- **Estetty** (punainen): synkronointi ei mene läpi. PTV:ssä on julkaisematon luonnos, tai edellinen synkronointi epäonnistui. Avaa liikuntapaikka nähdäksesi syyn.
- **Ei vielä viety** (harmaa): liikuntapaikka on valittu vietäväksi, mutta sitä ei ole vielä viety PTV:hen.
- **Haalea PTV-merkki**: integraatio ei ole käytössä, eli liikuntapaikkaa ei viedä PTV:hen.

**Katselmointi-sarake**

- Oranssi varoitusmerkki: katselmoija on pyytänyt muutoksia tiivistelmään tai kuvaukseen.
- Vihreä merkki: tekstit on hyväksytty. Puhekupla merkin vieressä tarkoittaa, että katselmoija jätti kommentin.

Katso ohje *Katselmointi: DVV:n palaute kuvauksista*.

**Suodattimet ja haku**

Avaa suodattimet taulukon yläpuolella olevasta lukumääräpainikkeesta. Valittu suodatin näyttää lukumääränsä. Saat suodattimen pois klikkaamalla sitä uudelleen.

- **Kaikki**: kaikki liikuntapaikat.
- **Integraatio päällä**: liikuntapaikat, jotka viedään PTV:hen.
- **Ei vielä viety**, **Ristiriita**, **Valmis synkronoitavaksi**, **Estetty** ja **Ajan tasalla**: liikuntapaikat tilan mukaan.
- **Kuvaukset puuttuvat**: suomenkielinen tiivistelmä tai kuvaus puuttuu.
- **Integraatio pois**: liikuntapaikat, joita ei viedä PTV:hen.

Yksittäisen liikuntapaikan löydät **Hae nimellä...** -kentällä. Jos avattu liikuntapaikka ei enää vastaa suodatinta, esimerkiksi synkronoinnin jälkeen, se pysyy näkyvissä merkinnällä *Ei enää vastaa suodatinta*.

**Taulukon yläpuolella**

- Ilmoitus kertoo, monessako liikuntapaikassa on ristiriitoja. **Näytä ne** suodattaa ne esiin.
- **Synkronoi kaikki valmiit** synkronoi kerralla kaikki tilassa *Valmis synkronoitavaksi* olevat liikuntapaikat suodattimista riippumatta. Liikuntapaikat, joissa on ristiriitoja tai puuttuvia tietoja, jätetään väliin. Jos jonkin liikuntapaikan synkronointi epäonnistuu, vienti pysähtyy. Jatka sitä painikkeella **Jatka vientiä**.
- **Odottaa arkistointia** listaa liikuntapaikat, jotka on poistettu Lipaksesta tai eivät enää kuulu PTV:hen mutta näkyvät yhä PTV:ssä (ks. *Liikuntapaikan poistaminen PTV:stä (arkistointi)*).

<!-- page id=new slug=ptv-muutosten-lapikaynti -->
## PTV-muutosten läpikäynti
> Jos palvelupaikkaa on muokattu PTV:ssä, Lipas näyttää muutokset ennen synkronointia, ja valitset, mitkä tiedot jäävät voimaan.

Synkronointi vie Lipaksen tiedot PTV:hen ja korvaa palvelupaikan tiedot PTV:ssä. Jos palvelupaikkaa on muokattu PTV:ssä edellisen synkronoinnin jälkeen, Lipas näyttää muutokset ennen synkronointia, ja voit valita, mitkä tiedot jäävät voimaan. Tällainen liikuntapaikka on tilassa **Ristiriita**.

Muutokset näkyvät liikuntapaikan PTV-välilehdellä, PTV-työkalun Liikuntapaikat-välilehdellä ja Käyttöönotto-välilehden vaiheessa 3.

**Mitä näkymä kertoo**

- Ylimpänä on yhteenveto, esimerkiksi *PTV:ssä on muutettu 3 tietoa – valitse, mitkä jäävät voimaan.* Avaa muutokset painikkeesta **Näytä muutokset**.
- Yhteenvedon alla voi olla ilmoituksia, jotka estävät synkronoinnin tai vaativat toimia:
  - *PTV:ssä on julkaisemattomia muutoksia*: palvelupaikalla on PTV:ssä luonnos. Julkaise tai peru luonnos PTV:ssä (**Avaa PTV:ssä**) ja paina sitten **Tarkista uudelleen**. Luonnoksen aikana muutoksia ei näytetä.
  - *Palvelupaikka on arkistoitu PTV:ssä*: julkaise palvelupaikka uudelleen (**Julkaise uudelleen**) tai pura liitos (**Irrota liitos**).
  - *Palvelu on arkistoitu PTV:ssä*: liitos arkistoituun palveluun poistuu Lipaksesta synkronoinnissa.
- Kohdassa **Valitse, mikä jää voimaan** ovat PTV:ssä tehdyt muutokset aiheittain: nimi ja kuvaukset, yhteystiedot, osoite ja sijainti, kieliversiot sekä palvelut. Kunkin kohdan vieressä näkyvät Lipaksen ja PTV:n versiot.
- Kohdassa **Päivittyy PTV:hen Lipaksesta** ovat Lipaksessa tehdyt muutokset, jotka synkronointi vie PTV:hen. Niistä ei tarvitse valita mitään.

**Valinnat**

- **Pidä Lipaksen versio**: Lipaksen tieto viedään PTV:hen, ja PTV:n muutos korvautuu.
- **Ota PTV:n versio**: PTV:n tieto kopioidaan Lipakseen ja säilyy PTV:ssä. Nimen, yhteystietojen tai osoitteen ottaminen muuttaa myös liikuntapaikan tiedot Lipaksessa, mikä vaatii oikeuden muokata liikuntapaikkaa.
- **Ota kaikki PTV:n muutokset** ottaa PTV:n version kaikista kohdista, joissa on vain yksi vaihtoehto. Jos liikuntapaikalla ei ole verkkosivua ja PTV:ssä on yksi muu verkkosivu, siitä tulee liikuntapaikan verkkosivu. PTV:n kieliversio säilyy, jos kieli on organisaatiolla käytössä; muuten se poistuu PTV:stä synkronoinnissa. Kun PTV:ssä on useampi vaihtoehto, esimerkiksi kaksi puhelinnumeroa, valitset itse: Lipas kertoo nämä kohdat ennen painamista ja siirtää sen jälkeen kohdistuksen ensimmäiseen.
- **Pidä Lipaksen versio kaikissa** pitää Lipaksen version kaikissa kohdissa. **Kumoa valinnat** peruu tekemäsi valinnat.
- Lipaksessa voi olla vain yksi puhelinnumero, sähköpostiosoite, verkkosivu, varauslinkki ja käyntiosoite. Jos PTV:ssä on useampia, valitse niistä yksi. Muut poistuvat PTV:stä.
- Palvelut valitaan kokonaisuutena: joko Lipaksen tai PTV:n liitetyt palvelut.
- Voit myös muokata tekstiä tai arvoa itse. Jos samaa tietoa on muutettu PTV:ssä, vahvista oma versiosi painikkeella **Vie muokattu versio PTV:hen**. Jos PTV:n tieto muuttuu vielä tämän jälkeen, Lipas kysyy uudelleen.

**Milloin valinnat tulevat voimaan**

Valinnat tulevat voimaan, kun synkronoit tai tallennat liikuntapaikan. Jos yrität synkronoida ennen kuin olet valinnut, Lipas kysyy ensin:

- **Käy muutokset läpi** vie valintoihin.
- **Synkronoi ja korvaa** (liikuntapaikan lomakkeella **Tallenna ja korvaa**) korvaa PTV:n tiedot Lipaksen tiedoilla.

Kun liikuntapaikka liitetään ensimmäistä kertaa PTV:ssä jo olevaan palvelupaikkaan, kysymys on muotoa *Lipas ja PTV eroavat*, ja painike on **Käy erot läpi**.

Kun liikuntapaikkaa vain katsellaan, muutokset näkyvät, mutta valintoja ei voi tehdä. Aloita liikuntapaikan muokkaus, niin voit valita.

**Tiedot, joista ei valita**

- *Kieliversio, jota Lipas ei julkaise*: synkronointi poistaa sen PTV:stä. Jos haluat säilyttää kieliversion, pyydä Lipaksen tukea ottamaan kieli käyttöön organisaatiollenne (lipasinfo@jyu.fi). Kun kieli on käytössä, voit ottaa PTV:n tekstit Lipakseen painikkeella **Ota PTV:n tekstit**.
- *Kunta ja sijainti* tulevat aina Lipaksesta. Jos PTV:n sijainti on oikea, siirrä liikuntapaikkaa Lipaksen kartalla.
- *Aukioloajat ja postiosoite* säilyvät PTV:ssä, koska Lipaksessa ei ole niille kenttiä. Postiosoite, jota PTV ei hyväksy rajapinnan kautta, poistuu, ja näkymä kertoo siitä. Korjaa se PTV:ssä.
- *Käyntiosoitteen lisätieto, muunkieliset kadunnimet sekä puhelinnumeron lisätieto ja maksullisuus* säilyvät PTV:ssä, kun osoite ja puhelinnumero ovat samat kuin Lipaksessa. Jos osoite tai numero vaihtuu Lipaksessa, ne poistuvat vanhan mukana.

<!-- page id=new slug=palvelut-valilehti-palveluiden-muokkaaminen -->
## Palvelut-välilehti: palveluiden muokkaaminen
> Palvelut-välilehdellä näet kunnan PTV-palvelut, muokkaat niiden tekstejä ja luot uusia palveluita.

PTV-työkalun Palvelut-välilehdellä näet organisaatiosi PTV-palvelut. Taulukossa ovat Lipaksessa luodut palvelut, Lipaksesta päivitetyt PTV:n palvelut ja palvelut, joihin Lipaksen liikuntapaikkoja on liitetty. Organisaation muut palvelut saat näkyviin taulukon alta kohdasta **Organisaation muut palvelut** painikkeella **Näytä ne**.

**Sarakkeet**

- **Integraatio**: palvelun tila. Merkit ovat samat kuin Liikuntapaikat-välilehdellä (*Ajan tasalla*, *Valmis synkronoitavaksi*, *Ristiriita*, *Estetty*).
- **Katselmointi**: katselmoijan palaute palvelun teksteistä.
- **Luotu**: missä palvelu on luotu: **Lipas**, **PTV** tai **Muu järjestelmä**.
- **Palvelupaikat**: palveluun liitettyjen palvelupaikkojen määrä PTV:ssä.
- **Liikuntapaikat**: palveluun liitettyjen Lipaksen liikuntapaikkojen määrä. Klikkaamalla lukua näet liikuntapaikat Liikuntapaikat-välilehdellä.

**Palvelun muokkaaminen**

1. Avaa palvelu klikkaamalla sen riviä.
2. Muokkaa **Tiivistelmää**, **Kuvausta** ja **Toimintaohjetta**. Kenttien alta saat vinkkejä, esimerkiksi *Mitä toimintaohjeeseen kannattaa kirjoittaa?*.
3. Paina **Synkronoi nyt**.

Muutokset säilyvät vain selaimessa, kunnes synkronoit. Painike **Peru tallentamattomat muutokset** palauttaa palvelun tiedot ennalleen.

Mitä voi muokata, riippuu siitä, missä palvelu on luotu:

- **Lipas**: tekstit ja suomenkielinen nimi. Palveluluokat ja asiasanat tulevat Lipaksesta. Jos niitä muutetaan PTV:ssä, synkronointi palauttaa ne.
- **PTV**: nimi ja tekstit. Palveluluokat ja muut tiedot säilyvät PTV:ssä. Ensimmäisellä kerralla Lipas kysyy *Päivitetäänkö PTV:ssä luotu palvelu Lipaksesta?*. Vahvista painikkeella **Päivitä PTV:hen**. Tekoälyluonnoksia ei voi laatia PTV:ssä luoduille palveluille.
- **Muu järjestelmä**: palvelua ei voi muokata Lipaksessa, mutta liikuntapaikkoja voi liittää siihen.

**Uusi palvelu**

Kun kuntaan tulee liikuntapaikkoja tyyppiluokasta, jolle ei vielä ole palvelua:

1. Paina **Luo uusi palvelu**.
2. Valitse tyyppiluokka. Valikossa näkyvät vain tyyppiluokat, joille ei vielä ole palvelua.
3. Kirjoita tai laadi tekoälyllä suomenkielinen tiivistelmä, kuvaus ja toimintaohje.
4. Paina **Vie palvelutietovarantoon**.

**Kun palvelua on muutettu PTV:ssä**

Synkronointi vie PTV:hen Lipaksen version palvelun nimestä ja teksteistä. Jos nimeä tai tekstiä on muutettu PTV:ssä Lipaksen edellisen synkronoinnin jälkeen, palvelu on tilassa **Ristiriita**. Valitse silloin ennen synkronointia, kumpi versio jää voimaan: **Pidä Lipaksen versio** tai **Ota PTV:n versio**.

Jos palvelua muokataan PTV:ssä sillä välin, kun se on auki Lipaksessa, Lipas ei synkronoi vaan kertoo siitä. Tallentamattomat muutoksesi säilyvät.

<!-- page id=new slug=liikuntapaikan-poistaminen-ptvsta-arkistointi -->
## Liikuntapaikan poistaminen PTV:stä (arkistointi)
> Kun liikuntapaikka poistuu käytöstä tai sitä ei enää haluta PTV:hen, sen palvelupaikka arkistoidaan PTV:ssä. PTV-käyttäjä tekee sen Lipaksessa.

Palvelupaikkaa ei poisteta PTV:stä, vaan se arkistoidaan, jolloin se ei enää näy julkisesti. Arkistointi tehdään Lipaksessa. PTV:n omaan käyttöliittymään ei tarvitse mennä.

**Kun liikuntapaikka poistetaan Lipaksesta**

Kun liikuntapaikka poistetaan syyllä *Poistettu käytöstä pysyvästi* tai *Väärä tieto*:

- Jos poistaja on PTV-käyttäjä, palvelupaikka arkistoidaan PTV:ssä samalla. Poistoikkuna kertoo tästä.
- Jos poistajalla ei ole PTV-oikeuksia, liikuntapaikka siirtyy PTV-työkalun Liikuntapaikat-välilehdellä listaan **Odottaa arkistointia**, ja PTV-käyttäjä arkistoi sen sieltä.

Väliaikaisesti käytöstä poistettu liikuntapaikka pysyy PTV:ssä. Kerro tarvittaessa kuvauksessa, että paikka on tilapäisesti suljettu.

**Kun liikuntapaikan omistaja tai tyyppi muuttuu**

Jos liikuntapaikan omistaja tai tyyppi muuttuu niin, ettei liikuntapaikka enää kuulu PTV:hen (esimerkiksi omistajaksi tulee yksityinen yritys), palvelupaikkaa ei arkistoida itsestään. Liikuntapaikka näkyy listassa **Odottaa arkistointia**, josta PTV-käyttäjä arkistoi sen.

**Kun liikuntapaikkaa ei haluta enää PTV:hen**

1. Avaa liikuntapaikka PTV-työkalun Liikuntapaikat-välilehdellä, tai muokkaa liikuntapaikkaa ja avaa sen **PTV**-välilehti.
2. Kytke integraatio pois päältä.
3. Lipas kysyy, arkistoidaanko palvelupaikka PTV:ssä:
   - **Kyllä**: palvelupaikka arkistoidaan.
   - **Ei**: palvelupaikka jää PTV:hen, mutta Lipas ei enää päivitä sitä.

PTV-työkalussa valinta tallentuu heti. Liikuntapaikan PTV-välilehdellä se tallentuu, kun tallennat liikuntapaikan.

**Odottaa arkistointia -lista**

Lista näkyy Liikuntapaikat-välilehden yläosassa, kun siinä on liikuntapaikkoja. Kunkin liikuntapaikan kohdalla kerrotaan syy. **Arkistoi PTV:ssä** arkistoi palvelupaikan heti, ja **Avaa liikuntapaikka** avaa liikuntapaikan kartalla.

**Arkistoitu palvelupaikka**

Jos palvelupaikka on arkistoitu PTV:ssä mutta liikuntapaikka on yhä käytössä, Lipas näyttää ilmoituksen *Palvelupaikka on arkistoitu PTV:ssä*. PTV-työkalussa voit julkaista palvelupaikan uudelleen (**Julkaise uudelleen**) tai purkaa liitoksen (**Irrota liitos**). Liikuntapaikan lomakkeella tallennus julkaisee saman palvelupaikan uudelleen, kun integraatio on päällä.

<!-- page id=new slug=kieliversiot-ruotsi-ja-englanti -->
## Kieliversiot: ruotsi ja englanti
> Tiedot viedään PTV:hen aina suomeksi. Ruotsin ja englannin käyttöönottoa pyydetään Lipaksen tuesta.

PTV:hen viedään aina suomenkieliset tiedot. Ruotsin- ja englanninkieliset tekstit voi viedä, kun kieli on otettu käyttöön organisaatiollenne.

**Kielen käyttöönotto**

Lipaksen ylläpito ottaa kielet käyttöön organisaatiokohtaisesti. Pyydä kieltä osoitteesta lipasinfo@jyu.fi. Kun kieli on käytössä, tekstikenttien yläpuolelle tulee sen välilehti: SE on ruotsi ja EN englanti.

**Kääntäminen**

Kirjoita tekstit ensin suomeksi ja paina **Käännä muille kielille**. Tekoäly kääntää avoinna olevan välilehden tekstit muille käytössä oleville kielille ja korvaa niissä jo olevat tekstit. Tarkista käännökset ennen vientiä.

**Mitä PTV:hen viedään**

- Suomenkieliset tiedot ovat pakollisia.
- Muu kieliversio viedään, kun sillä on sekä tiivistelmä että kuvaus. Suomenkielistä tekstiä ei käytetä puuttuvan käännöksen tilalla.
- Jos palvelupaikalla tai palvelulla on PTV:ssä kieliversio, jota organisaatiollanne ei ole Lipaksessa käytössä, synkronointi poistaa sen PTV:stä. Lipas kertoo tästä ennen synkronointia. Jos haluat säilyttää kieliversion, pyydä kieli käyttöön ennen synkronointia.

<!-- page id=new slug=katselmointi-dvvn-palaute-kuvauksista -->
## Katselmointi: DVV:n palaute kuvauksista
> DVV voi katselmoida PTV:hen viedyt tekstit ja pyytää niihin korjauksia. Näin näet palautteen ja korjaat tekstin.

Digi- ja väestötietovirasto (DVV) voi tarkistaa eli katselmoida Lipaksesta PTV:hen viedyt suomenkieliset tekstit: palvelupaikkojen tiivistelmät ja kuvaukset sekä palveluiden tiivistelmät, kuvaukset ja toimintaohjeet. Katselmoija joko hyväksyy tekstin tai pyytää siihen muutoksia.

**Mistä näet palautteen**

- **Sähköposti**: kun katselmointi on valmis tai siinä on pyydetty korjauksia, organisaation PTV-käyttäjät saavat siitä sähköpostin. Viesti lähetetään käyttäjille, jotka ovat organisaation jäseniä Lipaksessa.
- **Katselmointi-sarake** PTV-työkalun Liikuntapaikat- ja Palvelut-välilehdillä:
  - oranssi varoitusmerkki: muutoksia pyydetty (vie hiiri merkin päälle nähdäksesi päivämäärän ja kentät)
  - vihreä merkki: hyväksytty
  - puhekupla: katselmoija jätti kommentin.

Rivit, joihin on pyydetty muutoksia, ovat taulukon alussa.

**Tekstin korjaaminen**

1. Avaa liikuntapaikka tai palvelu PTV-työkalussa. Liikuntapaikan palautteen näet myös liikuntapaikan PTV-välilehdeltä.
2. Palaute näkyy kentän alla otsikolla **Katselmoijan palaute - vaatii muutoksia**.
3. Korjaa suomenkielinen teksti palautteen mukaan. Päivitä tarvittaessa myös käännökset.
4. Paina **Synkronoi nyt** tai tallenna liikuntapaikka.

Korjauksen jälkeen palautteen otsikko on **Katselmoijan palaute - korjattu**, ja taulukossa näkyy vihreä merkki. Katselmoija voi vielä tarkistaa korjatun tekstin.

<!-- page id=5385bbe6-2839-415e-ba9c-c2884b1449fd slug=kuvausten-laatiminen-tekoalylla aliases=miten-tekoaly-laatii-ptv-kuvaukset,yksittaisen-kohteen-ptv-kuvauksen-luominen,massamuotoinen-kuvausten-luominen-batch,palveluluokan-kuvausten-laatiminen-tekoalylla -->
## Kuvausten laatiminen tekoälyllä
> Tekoäly laatii luonnokset palveluiden ja palvelupaikkojen teksteihin Lipaksen tietojen pohjalta. Luonnokset pitää aina tarkistaa ennen vientiä.

Lipas voi laatia tekoälyn avulla luonnokset palveluiden ja palvelupaikkojen teksteihin. Tekoäly on kirjoitusapu: se ei vie mitään PTV:hen, vaan tekstit tarkistetaan ja viedään aina erikseen. Lipas käyttää tekstien laatimiseen ensisijaisesti Google Gemini -kielimallia.

**Painikkeet**

- **Laadi luonnokset tekoälyllä** laatii luonnoksen yhdelle palvelulle tai liikuntapaikalle.
- **Laadi luonnokset kaikille** laatii Käyttöönotto-välilehdellä luonnokset kaikille listan palveluille tai liikuntapaikoille. Voit muokata valmistuneita luonnoksia, vaikka muut ovat vielä tekeillä, ja keskeyttää laatimisen painikkeella **Peruuta**.
- **Käännä muille kielille** kääntää avoinna olevan kielen tekstit muille käytössä oleville kielille.

**Mihin luonnos perustuu**

- **Palvelupaikka:** liikuntapaikan tiedot Lipaksessa, kuten tyyppi, sijainti, varustelu, esteettömyys ja lisätiedot. PTV-työkalussa käytetään Lipakseen tallennettuja tietoja, joten tallenna liikuntapaikan muutokset ensin. Liikuntapaikan PTV-välilehdellä mukana ovat myös tallentamattomat muutokset.
- **Palvelu:** kunnan nimi ja palveluun kuuluvat liikuntapaikkatyypit. Palvelun kuvaus on yleiskuvaus siitä, mitä palvelu kunnassa sisältää, eikä siinä kerrota yksittäisistä paikoista. Tekoäly laatii palvelulle myös toimintaohjeen.

Tekoäly kirjoittaa neutraalia asiatekstiä eikä keksi tietoja, joita Lipaksessa ei ole. Jos tietoja on vähän, myös kuvaus on lyhyt. Paras tapa parantaa kuvausta on täydentää liikuntapaikan tietoja Lipaksessa.

**Huomioi**

- Luonnos korvaa kentässä olevan tekstin kysymättä. Jos olet muokannut tekstiä itse, kopioi se talteen ennen uuden luonnoksen laatimista.
- Tiivistelmä voi olla enintään 150 merkkiä ja kuvaus 5000 merkkiä. Kentän alla näkyy merkkimäärä.
- PTV:ssä luoduille palveluille ei voi laatia luonnoksia, koska Lipaksessa ei ole niistä taustatietoa.

<!-- page id=e5361584-2020-4c0b-b90f-9b9ef402f483 slug=miten-toimintaohjeet-kentta-taytetaan-ja-mita-se-sisaltaa -->
## Miten Toimintaohjeet-kenttä täytetään ja mitä se sisältää?
> Toimintaohje kertoo asiakkaalle, miten palvelua saa ja missä järjestyksessä asiat hoidetaan. Lipaksessa se kirjoitetaan palveluille.

Toimintaohjeet-kenttä on tarkoitettu asiakkaan ohjaamiseen, jotta hän tietää tarkalleen, miten palvelua saa ja missä järjestyksessä asiat hoidetaan.

Lipaksessa toimintaohje kirjoitetaan palveluille: Käyttöönotto-välilehden vaiheessa 2 ja Palvelut-välilehdellä. Palvelupaikoilla ei ole toimintaohjetta. PTV:n käyttöliittymässä kenttä on Kuvaus-kentän jälkeen.

**Hyvän toimintaohjeen ominaisuudet:**
1. **Ohjaa oikeaan kanavaan:** Kerro, mikä asiointikanava on ensisijainen (esim. verkkoasiointi tai puhelin).
2. **Käytä selkeitä kehotteita:** Esimerkiksi ”Täytä hakulomake verkossa” tai ”Ota yhteyttä puhelimitse”.
3. **Ole konkreettinen:** Voit kertoa myös, mitä asiakkaan *ei* tarvitse tehdä, esimerkiksi: ”Ulkokuntosaleja voi käyttää ilman ajanvarausta”.
4. **Käytä selkeää kieltä:** Vältä virastokieltä ja kirjoita ymmärrettävästi. Hyödynnä väliotsikoita ja luettelomerkkejä.

**Vältä näitä:**
- Älä kirjoita toimintaohjeisiin yhteystietoja (osoitteita, puhelinnumeroita tai URL-linkkejä). Nämä tiedot lisätään asiointikanaviin, jotka liitetään palveluun.
- Älä viittaa muihin verkkosivuihin tyyliin ”Katso lisätiedot alta”, koska tekstiä voidaan lukea eri yhteyksissä.

---

*Lähde: DVV:n PTV-koulutus 31.10.2025. Katso myös [Toimintaohjeet-kentän ohje Suomi.fi-kehittäjäsivustolla](https://kehittajille.suomi.fi/palvelut/palvelutietovaranto/sisallon-tuottaminen/palvelun-kuvaamisen-ohjeet/miten-taytan-palvelun-perustiedot#toimintaohjeet).*

<!-- page id=e99449d4-16ff-43ec-b3cc-8fb4565ae46c slug=toimintaohjeen-pakollisuus aliases=toimintaohjeet-kentan-muuttuminen-pakolliseksi-332026,miten-rajapintakaytto-in-api-vaikuttaa-toimintaohjeiden-pakollisuuteen -->
## Toimintaohjeen pakollisuus
> Toimintaohje on ollut pakollinen PTV:n käyttöliittymässä 3.3.2026 alkaen. Rajapinnan kautta tuotaville palveluille, kuten Lipaksen palveluille, se tulee pakolliseksi rajapintaversiossa 12.

**PTV:n käyttöliittymä**

Toimintaohjeet-kenttä tuli pakolliseksi PTV-palvelukuvauksissa 3.3.2026.

- **Julkaistut palvelut:** Jo julkaistut palvelut pysyvät näkyvissä, vaikka niiltä puuttuisi toimintaohje.
- **Muokkaaminen ja uudelleenjulkaisu:** Palvelua ei voi tallentaa tai julkaista uudelleen PTV:n käyttöliittymässä, jos toimintaohjetta ei ole täytetty kaikilla palvelun kieliversioilla.
- **Pohjakuvaukset:** Jos käytät pohjakuvausta ja se sisältää riittävät toimintaohjeet, omaa täydennystä ei ole pakko tehdä. Jos pohjakuvauksen ohje on puutteellinen tai väärä, ota yhteys PTV-tukeen.

**Rajapinnan kautta tuotavat palvelut (Lipas)**

- 3.3.2026 pakollisuus ei koske rajapinnan kautta tuotavia palveluita, kuten Lipaksesta vietyjä.
- Toimintaohjeista tulee pakollisia rajapintakäyttäjille, kun organisaatio siirtyy käyttämään rajapintaversiota 12 vuoden 2027 aikana.
- Lipas vaatii jo nyt suomenkielisen toimintaohjeen, kun palvelu luodaan Lipaksesta. Tarkista silti vanhempien palveluiden toimintaohjeet Palvelut-välilehdellä ja täydennä puuttuvat hyvissä ajoin.

---

*Lähde: DVV:n PTV-koulutus 31.10.2025. Katso myös [Toimintaohjeet-kentän ohje Suomi.fi-kehittäjäsivustolla](https://kehittajille.suomi.fi/palvelut/palvelutietovaranto/sisallon-tuottaminen/palvelun-kuvaamisen-ohjeet/miten-taytan-palvelun-perustiedot#toimintaohjeet).*

<!-- page id=ed25c92b-4552-4cf5-8730-46d4e11fc49f slug=miten-loydan-palvelut-joilta-puuttuu-toimintaohje -->
## Miten löydän palvelut, joilta puuttuu toimintaohje?
> PTV:n Palvelujen tiedot -raportista näet, miltä organisaation palveluilta puuttuu toimintaohje.

Voit tarkistaa organisaatiosi palveluiden tilanteen PTV-raporttien avulla:

1. Valitse PTV-käyttöliittymän päävalikosta **Raportit**.
2. Valitse raporttilistasta **Suomi.fi-palvelutietovaranto**.
3. Lataa Excel-raportti nimeltä **Palvelujen tiedot**.
4. Suodata Excelissä esiin oma organisaatiosi.
5. Suodata sarakkeet **Toimintaohjeet** ja **Pohjakuvaus: Toimintaohjeet** siten, että näkyviin jäävät vain tyhjät rivit.

Jos organisaatiollasi on vain vähän palveluita, voit tarkistaa ne myös yksitellen: Lipaksessa avaamalla palvelun PTV-työkalun Palvelut-välilehdellä tai PTV:n käyttöliittymässä ottamalla palvelun muokkaustilaan.

---

*Lähde: DVV:n PTV-koulutus 31.10.2025. Katso myös [Toimintaohjeet-kentän ohje Suomi.fi-kehittäjäsivustolla](https://kehittajille.suomi.fi/palvelut/palvelutietovaranto/sisallon-tuottaminen/palvelun-kuvaamisen-ohjeet/miten-taytan-palvelun-perustiedot#toimintaohjeet).*
