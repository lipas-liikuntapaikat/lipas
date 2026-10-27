# Help articles for the PTV sync review (v2)

To publish through the help CMS (Ohjeet → muokkaa), section
**PTV-integraatio** (`ptv-integraatio`). The review panel links to the new
page by its slug, `ptv-muutosten-lapikaynti`: until the page exists the link
opens the help centre's front page.

Two existing pages describe the old behaviour and need the edits below.

---

## New page: PTV-muutosten läpikäynti

Slug: `ptv-muutosten-lapikaynti`

Synkronointi vie Lipaksen tiedot PTV:hen ja korvaa palvelupaikan tiedot
PTV:ssä. Jos palvelupaikkaa on muokattu PTV:ssä edellisen synkronoinnin
jälkeen, Lipas näyttää muutokset ennen synkronointia, ja voit valita, mitkä
tiedot jäävät voimaan. Muutokset näkyvät PTV-työkalun Liikuntapaikat-
välilehdellä, Käyttöönotto-ohjatussa toiminnossa ja liikuntapaikan
PTV-välilehdellä.

**Mitä näkymä kertoo**

- Ylimpänä on yhteenveto: montako tietoa on muutettu PTV:ssä ja milloin
  synkronoitu viimeksi. Avaa muutokset painikkeesta *Näytä muutokset*.
- *Palvelupaikan tila* kertoo, jos jokin estää synkronoinnin, esimerkiksi
  PTV:ssä julkaisematon luonnos, tai jos palvelupaikka on arkistoitu PTV:ssä.
- *Valitse, mikä jää voimaan* listaa PTV:ssä tehdyt muutokset aiheittain:
  nimi ja kuvaukset, yhteystiedot, osoite ja sijainti, kieliversiot sekä
  liitetyt palvelut. Kunkin kohdan vieressä on Lipaksen ja PTV:n versio.
- *Päivittyy PTV:hen Lipaksesta* listaa Lipaksessa tehdyt muutokset, jotka
  synkronointi vie PTV:hen. Niistä ei tarvitse valita mitään.

**Valinnat**

- *Pidä Lipaksen versio*: Lipaksen tieto viedään PTV:hen, ja PTV:n muutos
  korvautuu.
- *Ota PTV:n versio*: PTV:n tieto kopioidaan Lipakseen, ja se säilyy myös
  PTV:ssä. Nimen, yhteystietojen tai osoitteen ottaminen muuttaa myös
  liikuntapaikan tiedot Lipaksessa, mikä vaatii oikeuden muokata
  liikuntapaikkaa.
- Lipaksessa voi olla vain yksi puhelinnumero, sähköpostiosoite,
  verkkosivu, varauslinkki ja käyntiosoite. Jos PTV:ssä on useampia, valitse
  niistä yksi; muut poistuvat PTV:stä.
- *Pidä liitos* / *Poista liitos* liitetyille palveluille.
- Tekstiä tai arvoa voi myös muokata lomakkeessa. Kun sitä on muutettu myös
  PTV:ssä, vahvista oma versiosi painikkeella *Vie muokattu versio PTV:hen*.
  Jos PTV:n tieto muuttuu vielä tämän jälkeen, Lipas kysyy uudelleen.

Valinnat tulevat voimaan, kun synkronoit (tai liikuntapaikan lomakkeella
tallennat). Jos yrität synkronoida ennen kuin olet valinnut, Lipas kysyy
ensin. *Käy muutokset läpi* vie valintoihin; *Korvaa PTV:n muutokset*
synkronoi Lipaksen tiedoilla.

**Tiedot, joista ei valita**

- *Kieliversio, jota Lipas ei julkaise*: synkronointi poistaa sen PTV:stä.
  Jos haluat säilyttää kieliversion, pyydä Lipaksen tukea ottamaan kieli
  käyttöön organisaatiollenne (lipasinfo@jyu.fi). Kun kieli on käytössä,
  voit ottaa PTV:n tekstit Lipakseen painikkeella *Ota PTV:n tekstit*.
  Tekstit voi myös kopioida talteen.
- *Kunta ja sijainti*: Lipas vie PTV:hen liikuntapaikan sijainnin ja kunnan.
  Jos PTV:n sijainti on oikea, siirrä liikuntapaikkaa kartalla.
- *Aukioloajat ja postiosoite* säilyvät PTV:ssä, koska Lipaksessa ei ole
  niille kenttiä. Postiosoite, jota PTV ei hyväksy rajapinnan kautta,
  poistuu, ja näkymä kertoo siitä; korjaa se PTV:ssä.
- *Käyntiosoitteen lisätieto ja muunkieliset kadunnimet* sekä
  *puhelinnumeron lisätieto ja maksullisuus* säilyvät PTV:ssä, kun osoite ja
  numero ovat samat kuin Lipaksessa. Jos osoite tai numero vaihtuu
  Lipaksessa, ne poistuvat vanhan mukana.

**Palvelut**

PTV-työkalun Palvelut-välilehdellä sama näkymä koskee Lipaksessa luotuja
palveluja. Synkronointi vie PTV:hen vain Lipaksessa muokatut nimet ja
tekstit; PTV:ssä muutetut säilyvät. Jos samaa tekstiä on muutettu sekä
Lipaksessa että PTV:ssä edellisen synkronoinnin jälkeen, valitse, kumpi jää
voimaan. Palveluluokat ja asiasanat tulevat aina Lipaksesta.

**Kuka synkronoi**

Vain PTV-vastaava (PTV-oikeudet kunnalle) synkronoi PTV:hen. Kun muu
käyttäjä tallentaa liikuntapaikan, muutos jää Lipakseen, ja liikuntapaikka
näkyy PTV-työkalussa tilassa *Valmis synkronoitavaksi*. Kun PTV-vastaava
tallentaa liikuntapaikan muualla kuin PTV-välilehdellä (esimerkiksi
hakutulosten pikamuokkauksella), tallennus synkronoidaan vain, jos PTV:ssä ei
ole läpikäymättömiä muutoksia; muuten se jää Lipakseen, ja Lipas kertoo
siitä. Poistettu
liikuntapaikka näkyy tilassa *Odottaa arkistointia*, kunnes PTV-vastaava
arkistoi sen PTV:ssä.

---

## Edit: PTV-työkalun Liikuntapaikat-välilehden suodattimet ja symbolit

Replace the description of the status symbols and filters with:

Liikuntapaikan tila PTV:ssä:

- **Ajan tasalla** (vihreä): Lipas ja PTV ovat samat.
- **Valmis synkronoitavaksi** (sininen): Lipaksessa on muutoksia, jotka
  synkronointi vie PTV:hen; mitään ei tarvitse valita.
- **Odottaa valintaa** (oranssi): PTV:ssä on tehty muutoksia, joista pitää
  valita ennen synkronointia (ks. *PTV-muutosten läpikäynti*).
- **Estetty** (punainen): synkronointi ei mene läpi. PTV:ssä on
  julkaisematon luonnos, tai edellinen synkronointi epäonnistui.
- **Ei viety**: liikuntapaikkaa ei ole vielä viety PTV:hen.

Suodattimilla voi näyttää vain tietyn tilan liikuntapaikat. Taulukon
yläpuolella oleva ilmoitus kertoo, montako liikuntapaikkaa odottaa valintaa,
ja *Näytä ne* suodattaa ne esiin. *Synkronoi kaikki valmiit* synkronoi
kerralla kaikki valmiit liikuntapaikat; ne, joissa on valittavaa, jätetään
väliin ja luetellaan.

---

## Edit: Tietojen ylläpito ja synkronointi PTV:hen

- Step 2: replace "esim. 'Muuttunut'" with "esim. 'Valmis synkronoitavaksi'
  tai 'Odottaa valintaa'".
- Add after step 4: "Jos PTV:ssä on tehty muutoksia, Lipas näyttää ne ennen
  synkronointia (ks. *PTV-muutosten läpikäynti*)."
- Step 5: replace "muista arkistoida kohde myös suoraan PTV:n
  käyttöliittymässä" with "PTV-vastaava arkistoi palvelupaikan PTV:ssä
  PTV-työkalun kautta (tila *Odottaa arkistointia*)."
