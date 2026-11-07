# PTV sync review v2: browser scenarios

These scenarios are run by hand in the browser, the way a municipal user works.
Each one checks what the user sees and then what really happened: the saved
site in Lipas and the location in PTV. Plan:
[`ptv-sync-review-v2-plan.md`](ptv-sync-review-v2-plan.md).

## Ground rules

- **Where:** local Lipas (https://localhost) against the PTV test env,
  **Utajärvi only**. Liminka and Raahe hold training data and must not be
  touched.
- **Verify after every write:**
  - `(user.ptv-drift-scenarios/state lipas-id)` shows the saved site, the live
    PTV location and the review rows left.
  - PTV's own UI is the second check where a scenario says so.
- **Reset:**
  - `(user.ptv-drift-scenarios/run-all!)` recreates scenarios s1–s16;
    `(run-all! [#'user.ptv-drift-scenarios/s10-phone!])` just one.
  - `(user.ptv-drift-scenarios/report)` lists each scenario site's review
    rows, as the PTV tool computes them.
- **PTV speed:** writes take a few seconds again (they took ~140 s from
  2026-10-22 until the MML fix of 2026-10-26). The test env has short
  outages (502/503); `run-all!` reports them per scenario.
- **Known blocker:** test sites 615340 (s1) and 89913 (s2) have unpublished
  drafts in PTV, left by failed writes during the slow period. Their rows
  read "Estetty" once opened (F2). Publish or discard the drafts in the PTV
  test UI before using s1 or s2.
- **Users:**
  - **PTV manager**: admin, or a user with the `ptv-manager` role for
    Utajärvi (889).
  - **Editor**: may edit Utajärvi's sites but has no PTV rights.
- **Phase:** each scenario names the phase that built it. Phases 1–6 of v2
  are built (2026-10-26).

## Demo sites

| Scenario | Site | What differs |
|---|---|---|
| s1 | 615340 Utajärven pallokenttä | nothing |
| s2 | 89913 Utajärven jäähalli | fi summary and description changed in PTV |
| s3 | 506495 Utajärven koulun piha-alue | fi description changed in Lipas |
| s4 | 509023 Nuoriso- ja liikuntatalon kuntosali | fi summary changed on both sides |
| s5 | 507128 Utajärven urheilukenttä | connections changed in PTV |
| s6 | 523482 Kirkonkylän valaistu latu | name and marketing name changed in PTV |
| s7 | 600093 Kirkonkylän uimaranta | English added in Lipas |
| s8 | 506497 Hyvinvointitalon lähiliikuntapaikka | not linked; links to a PTV-made location |
| s9 | 79800 Nuoriso- ja liikuntatalon liikuntasali | all of the above at once |
| s10 | 506493 Utajärven frisbeegolfrata | phone changed in PTV |
| s11 | 514575 Valkeisjärven uimapaikka | a second phone and an extra web page added in PTV |
| s12 | 600121 Hyvinvointitalon ulkokuntoilupaikka | visiting address changed in PTV |
| s13 | 605450 Utajärven urheilukentän ulkokuntoilupaikka | map point moved in PTV, about 200 m north |
| s14 | 606904 Utajärven kuntoportaat | Swedish version added in PTV (the org has fi+en) |
| s15 | 527789 Utajärven skeittipuisto | not linked; a PTV location of the same name has fi+en texts |
| s16 | 526137 Keisarintien päiväkodin leikkikenttä | postal address (PL 1, 91601) added in PTV; fi summary changed in Lipas |

s12–s14 and s16 export the site to a new location first if it has none.
620077 "Lipas linkitystesti sali" is a removed site still published in PTV:
it waits under "Odottaa arkistointia" (E3).

---

## A. Finding the sites that need attention (PTV tool, Liikuntapaikat tab)

**A1. Which sites have changes in PTV** (phase 4)
1. As a PTV manager, open the map, then PTV, choose "Utajärven kunta", then the
   Liikuntapaikat tab.
2. Click the "Ristiriita" filter chip.

Expect:
- Exactly the sites with PTV-side changes to decide are listed: s4, s5, s6,
  s9, s10, s11, s12 (and s2 once its draft is cleared).
- Each row shows the amber "Ristiriita" chip.

**A2. Banner over the table** (phase 4)

With the sites of A1 present:
- Expect "{n} liikuntapaikassa on PTV:ssä tehtyjä muutoksia, jotka odottavat
  valintaa. [Näytä ne]".
- "Näytä ne" applies the same filter as A1.

**A3. Status chips tell the states apart** (phase 4)

| Site | Chip |
|---|---|
| s1 (draft cleared) | "Ajan tasalla" (green) |
| s3, s7, s13, s14, s16 | "Valmis synkronoitavaksi" (blue): nothing to decide; a point (D4) or a language removal (D6) is information |
| s4, s5, s6, s9, s10, s11, s12 | "Ristiriita" (amber) |
| s1, s2 while their drafts are stuck, once opened; a site whose last sync failed | "Estetty" (red) |
| s8, s15 | not linked, integration off: the faded "PTV" chip |

Also: after every choice in s4 is made, its chip turns blue without a sync.
"Synkronoi kaikki valmiit (n)" counts the blue sites whose texts are
complete and whose sync is on.

**A4. Opening a row is fresh** (phase 4)
1. With the dialog open, change s3's description in PTV's own UI.
2. Open s3's row.

Expect: the review shows the new PTV-side change without reopening the
dialog.

---

## B. Reviewing one site (PTV tool, expanded row)

**B1. Nothing to review** (built)

Open s1. Expect:
- No review panel.
- "Synkronoi nyt" disabled, with the hint "Ajan tasalla".

**B2. Changed in PTV: read and decide** (built; bar wording phase 4)
1. Open s2. The bar is collapsed.
2. Read the bar. Expect:
   - built: "Lipas ja PTV eroavat 2 kohdassa";
   - phase 4: "PTV:ssä on muutettu 2 tietoa – valitse, mitkä jäävät
     voimaan." plus the second line with dates.
3. Expand ("Näytä erot"). Expect:
   - rows Tiivistelmä (FI) and Kuvaus (FI), each "Muutettu PTV:ssä";
   - Lipas and PTV cards, with the words only on one side highlighted;
   - "Valitse ennen synkronointia" on both rows.
4. Click "Ota PTV:n versio" on the summary. Expect:
   - the PTV card is marked "Lomakkeessa";
   - the summary field below shows PTV's text;
   - the row no longer asks for a choice;
   - the outcome line "Kopioidaan Lipakseen: Tiivistelmä (FI)".
5. Click "Pidä Lipaksen versio" on the description. Expect: the outcome line
   "Viedään PTV:hen: Kuvaus (FI)".
6. Click "Synkronoi nyt". Expect: no question asked, since everything is
   decided.

Verify:
- Lipas has PTV's summary and Lipas's own description.
- PTV has the same.
- Reopening the row shows no review.

**B3. Changed only in Lipas** (built)

Open s3. Expect:
- built: a row "Muutettu Lipaksessa" with no "Valitse ennen synkronointia";
- phase 4: listed under "Päivittyy PTV:hen Lipaksesta" instead, as one line.

Click "Synkronoi nyt". Expect: no question. Verify: PTV got Lipas's
description.

**B4. Conflict** (built)

Open s4. Expect:
- the row "Muutettu molemmissa", tinted;
- the hint "Voit myös yhdistää molempien muutokset muokkaamalla tekstiä
  lomakkeessa".

Then:
1. Edit the summary field below by hand so it merges both texts.
2. Expect the row to read "Lomakkeessa on muokattu teksti", showing the text,
   and to count as decided.
3. Sync. Verify: both Lipas and PTV have the merged text.

**B5. Connections** (built)

Open s5. Expect:
- one "Liitetyt palvelut" block listing both sides' services;
- the differing ones marked, with "liitetty PTV:ssä" or "liitos poistettu
  PTV:ssä" per line.

Then:
1. Choose "Pidä liitos" for the service PTV added, and "Poista liitos" for
   the one PTV removed.
2. Sync.

Verify: PTV's and Lipas's connections are exactly the chosen ones.

Superseded by M1–M5 (2026-11-07): the connections are now one choice,
Lipas's set or PTV's, like every other row.

**B6. Taking PTV's name renames the site** (built)

Open s6. On the name card, read "Vaihtaa myös liikuntapaikan nimen
Lipaksessa".
1. Take PTV's name and marketing name.
2. Expect "Nimi vaihtuu" next to the site in the table, with the old name in
   its tooltip.
3. Sync.

Verify: the site is renamed in Lipas (database and search) and PTV is
unchanged.

**B7. Language added in Lipas** (built)

Open s7. Expect:
- one line "Lipas lisää kieliversion EN: nimi, tiivistelmä, kuvaus viedään
  PTV:hen";
- no choice.

Sync. Verify: PTV has English.

**B8. Everything at once, bulk and undo** (built; progress and collapse phase 4)
1. Open s9.
2. Click "Ota kaikki PTV:n muutokset". Expect every PTV-side row taken, the
   name included.
3. Click "Kumoa valinnat". Expect what the choices wrote taken back (here
   everything as saved) and the choices open again.
4. Click "Pidä Lipaksen versio kaikissa". Expect no open choices left.
5. Phase 4 adds:
   - the counter "k / n valittu" moves with each choice;
   - decided rows collapse to "✓ … [Muuta]", and "Muuta" reopens them.

**B9. A choice expires when PTV changes again** (built)
1. In s2 choose "Pidä Lipaksen versio" for the summary.
2. In PTV's own UI change the summary again, then click the tool's refresh
   button (A4, phase 4, makes the refresh automatic).

Expect:
- the row asks for a choice again; the old choice no longer counts;
- "Kumoa valinnat" is gone when that was the only choice.

**B10. Reload loses open choices** (built, by design)

Make choices in s9 without syncing, then reload the page. Expect:
- the choices are gone, so no stale ones remain;
- the review is as before.

Write down whether this surprises users.

---

## C. Syncing with open choices (gate)

**C1. "Synkronoi nyt" with open choices, collapsed bar** (built; dialog wording phase 4)
1. Open s2 and leave the bar collapsed.
2. Click "Synkronoi nyt".

Expect a dialog listing what would be overwritten, with these buttons:

| | Buttons |
|---|---|
| built | "Käy erot läpi" / "Synkronoi ja korvaa" |
| phase 4 | primary "Käy muutokset läpi", secondary "Korvaa PTV:n muutokset", "Peruuta". Short values show both sides, e.g. "Puhelinnumero: +358 40 … → 08 …" |

**C2. "Käy muutokset läpi"** (built)

From C1, choose to review. Expect:
- the panel opens and scrolls into view;
- phase 4: keyboard focus is on the first open row.

**C3. "Korvaa PTV:n muutokset"** (built)

From C1, choose to overwrite. Verify: PTV has Lipas's texts again, and the
review is empty afterwards.

**C4. Cancel** (phase 4)

From C1, "Peruuta". Expect: nothing is sent and the row is as it was.

---

## D. Site form (PTV manager)

Run these on s9: B2 syncs s2, and B leaves s9 unsynced.

**D1. Badge and review on the PTV tab** (built)
1. Open s9 on the map and click edit.

Expect:
- an amber dot on the "PTV" tab label;
- on the tab, the bar collapsed, stacked cards when expanded, and the footer
  "Valinnat tulevat voimaan, kun tallennat liikuntapaikan…".

**D2. Save with open choices** (built)

Edit something unrelated (e.g. construction year) and save. Expect
"Tallennetaanko ja korvataanko PTV:n muutokset?" listing the open rows
(name, description, connection). "Käy erot läpi" goes to the PTV tab with
the panel open.

**D3. Decide in the form, then save** (built)

Take PTV's summary on the PTV tab. Expect:
- the summary field shows PTV's text at once;
- saving asks nothing.

Verify: both Lipas and PTV have the texts as chosen.

**D4. Taking PTV's contacts updates the basic data** (phase 3)

Open s10 for editing. On the PTV tab:
1. Expect the row "Puhelinnumero" with the caption "Muuttaa myös Lipaksen
   puhelinnumeron (Perustiedot)".
2. Take PTV's number.
3. Switch to "Perustiedot". Expect the phone field holds PTV's number.

Save. Verify: Lipas and PTV agree.

**D5. Undo choices in the form** (built)

After D3's choice, "Kumoa valinnat". Expect: the edit is back to what it
held before the choice, and the form fields follow.

**D5b. Undo keeps what the user wrote**
1. In s10's form, type your own phone number in Perustiedot, then take
   PTV's number on the PTV tab, then "Kumoa valinnat". Expect your number
   back, the row open.
2. Take PTV's number again, then type over it in Perustiedot, then "Kumoa
   valinnat". Expect your typing kept, only the choice forgotten.

**D6. Cancel editing** (built)

Make choices, then cancel the edit. Expect:
- the edit is discarded;
- reopening the edit shows the review as before, with no choices.

**D7. Status alerts** (phase 4)
- A site archived in PTV must not say "Uusi palvelupaikka luodaan".
- A site whose location has a draft in PTV shows the Modified notice (see F2).

**D8. A save shows that it waits for PTV** (built)

Save a synced site. Expect:
- the form closes at once;
- a blue notice "Tallennetaan ja synkronoidaan PTV:hen…" with a spinner
  stays until the save's own result ("Tallennus onnistui", or the PTV
  error) replaces it.

**D9. Viewing a site shows the differences** (built)

Open s9 without clicking edit. Expect:
- an amber dot on the "PTV" tab while PTV has changes without a choice;
- on the tab, the same bar and rows read-only: no choice or bulk buttons,
  no "Lomakkeessa" chips, no outcome lines;
- the footer "Valitse versiot muokkaamalla liikuntapaikkaa…".

---

## E. Editor without PTV rights (phase 5)

**E1. Save doesn't touch PTV**
1. As an editor, change s3's phone number and save.

Expect:
- no PTV tab, no PTV dialog, no question;
- the save succeeds as usual.

Verify:
- Lipas has the new number.
- PTV is unchanged.
- As a PTV manager, s3 shows "Valmis synkronoitavaksi" in the tool, with
  the phone under "Päivittyy PTV:hen".

**E1b. A PTV manager's quick edit over an undecided PTV change**
1. As a PTV manager, change s10's email in the search table's quick edit and
   save.

Expect: the toast "Liikuntapaikka tallennettiin, mutta sitä ei
synkronoitu, koska PTV:ssä on muutoksia, joita ei ole käyty läpi…".

Verify:
- Lipas has the new email; PTV still has its own phone.
- In the PTV tool, s10 reads "Estetty" and shows the phone row.

The same quick edit on s3 (nothing to decide in PTV) syncs as before.

**E2. Removing a site doesn't archive it in PTV**
1. As an editor, set a synced site's status to "Poistettu pysyvästi
   käytöstä" (or delete it) and save.

Expect:
- the delete dialog says "Palvelupaikka arkistoidaan PTV:ssä, kun
  PTV-vastaava hyväksyy sen";
- not "Poistaminen arkistoi myös…".

Verify: PTV still shows the location as published.

**E3. A PTV manager archives it**

As a PTV manager, open the tool. Expect:
- the site from E2 listed as "Odottaa arkistointia";
- the notice "Liikuntapaikka on merkitty Lipaksessa poistetuksi (…).
  Palvelupaikka näkyy yhä PTV:ssä.";
- the button "Arkistoi PTV:ssä".

Archive. Verify: PTV has the location archived, and the site leaves the list.

**E4. Clearing the backlog**

After E1 and similar edits on two more sites, a PTV manager clicks
"Synkronoi kaikki valmiit ({n})". Expect:
- the sites without open choices sync;
- a site with open choices (e.g. s2) is skipped and listed.

---

## F. Location state (notices at the top of the review)

**F1. Archived in PTV** (phase 4)

Archive s1's location in PTV's UI. Open s1. Expect:
- "Palvelupaikka on arkistoitu PTV:ssä.";
- buttons "Julkaise uudelleen", "Irrota liitos", "Pidä arkistoituna".

"Julkaise uudelleen" then syncs. Verify: the same location is published
again, with no duplicate.

**F2. Unpublished draft in PTV** (phase 4)

Edit s1 in PTV's UI and save without publishing (or use 615340 or 89913
while they're stuck). The table can't tell yet: PTV's listing shows the
published version, and only its `/active/` endpoint shows the draft. Open
the row (the refetch asks `/active/`). Expect:
- the chip turns "Estetty";
- "PTV:ssä on julkaisemattomia muutoksia. Julkaise tai peru ne PTV:ssä
  ennen synkronointia." with "Avaa PTV:ssä" and "Tarkista uudelleen";
- no bar and no rows, but "Lipaksen ja PTV:n erot näytetään, kun muutokset
  on julkaistu tai peruttu PTV:ssä." (the review would be of the published
  version, and no choice could be written);
- the sync is blocked.

"Tarkista uudelleen" fetches the location again: with the draft still
there, "PTV:ssä on yhä julkaisemattomia muutoksia…"; once it's published or
discarded, the notice goes and the review shows.

The same is shown in the site form, whose review fetch asks `/active/` too.
Saving it asks nothing (PTV refuses the write, so nothing is overwritten):
Lipas is saved and the toast says the sync failed on PTV's draft.
A sync attempt that hits "You cannot update entity with status Modified"
(e.g. from "Synkronoi kaikki valmiit" before the row was opened) shows this
notice, not "Synkronointi epäonnistui", and the chip turns "Estetty".

**F3. Double link** (phase 4)

Try to link a second site to s3's location in the tool and in the wizard.
Expect both to refuse, with "Palvelupaikka on liitetty myös liikuntapaikkaan
X" and a link to X. Today the wizard is silent.

**F4. Name conflict, unlinked site** (phase 4)

For an unlinked site whose name equals a PTV location's name, expect "PTV:ssä
on jo palvelupaikka nimellä X." "Liitä tähän palvelupaikkaan" then leads to
the first-link review (H1).

**F5. Names differ wholesale** (phase 4)

Link a site to an unrelated location (test data only). Expect "Nimet eroavat
kokonaan. Onko liitos oikea?" with "Vaihda palvelupaikka".

**F6. PTV unreachable** (phase 4)

Disconnect the network, or make the PTV call fail in dev. Expect:
- "PTV:n tietoja ei saatu haettua." with "Yritä uudelleen";
- a sync attempt still can't overwrite blindly (the stale-review check).

---

## G. Fields added in phase 3

**G1. Phone changed in PTV** (s10)

Expect the row "Puhelinnumero":
- short values side by side, no word diff;
- "Lipaksessa voi olla vain yksi puhelinnumero".

Take PTV's. Verify: Lipas's `:phone-number` holds PTV's number, formatted
"+358 …".

**G2. Second phone and extra web page in PTV** (s11)

PTV kept Lipas's number and added another. Expect:
- the row needs a choice, although PTV still has Lipas's number (a sync
  would remove the other one);
- the PTV card lists both numbers, each with "Ota tämä";
- the Lipas card says "PTV:n muut arvot poistuvat.";
- "Ota tämä" on Lipas's own number is the same as "Pidä Lipaksen versio":
  both buttons show as chosen.

Keep Lipas's. Verify: PTV has one number. Repeat for the extra web page
("Muut verkkosivut"), which can also be taken as the web page or the
booking link.

**G2b. What a sync keeps of the address and phone**

In PTV's UI add an extra text ("2. krs.") to s16's visiting address, and
information and "Maksuton" to its phone. Sync from Lipas. Verify in PTV:
both are still there (verified from the REPL on 2026-10-26; the old payload
deleted them).

**G3. Web pages without names are not a difference**

On a site last synced before #269 (unnamed pages in PTV, same URLs), expect
no web-page row.

**G4. Address changed in PTV** (s12)

Expect the row "Käyntiosoite":
- PTV's street and number joined ("Kotkantie 41"), with postal code and
  office.

Take it. Verify: Lipas has the address, postal code and postal office.

**G5. Swedish street name and extra text** (no scenario; set up in PTV's
UI)

The address row says that the Swedish street name and the extra text ("1.
krs.") are replaced by the sync.

**G6. Map point moved** (s13)

Expect:
- the information line "PTV:n koordinaatit (about 200 m päässä)
  korvautuvat Lipaksen sijainnilla.";
- "Sijaintia ei voi kopioida PTV:stä. Jos PTV:n sijainti on oikea, siirrä
  liikuntapaikkaa kartalla.";
- no choice and no take button; the chip stays "Valmis synkronoitavaksi".

Sync. Verify: PTV's point is back at Lipas's location.

**G7. Point within 50 m**

Move the point 20 m in PTV. Expect no row.

**G8. Municipality differs**

Expect information only, with a hint to check the location; it can't be
taken.

**G9. Language PTV has, the org hasn't enabled** (s14)

Expect:
- the row "Synkronointi poistaa PTV:stä ruotsinkielisen kieliversion: nimen,
  tiivistelmän ja kuvauksen.", with the texts that go;
- each text with "Kopioi";
- the hint to ask Lipas support to enable the language (lipasinfo@jyu.fi);
- the bar adds "Lisäksi synkronointi poistaa ruotsinkielisen kieliversion.";
- no choice needed; the chip stays "Valmis synkronoitavaksi".

"Kopioi" puts the text on the clipboard. Sync. Verify: PTV has fi+en only.

**G10. Language enabled for the org, but no texts in Lipas**

Enable sv for the test org and run s14 again. Expect "Ota PTV:n tekstit".
Take them. Verify: Lipas has the sv texts and PTV keeps sv.

**G11. Postal address is kept** (s16)

s16 added a PO box (PL 1, 91601) in PTV, and Lipas changed the summary.
Expect:
- no postal-address row; the summary under "Päivittyy PTV:hen Lipaksesta";
- the panel line "Säilyvät PTV:ssä: aukioloajat, postiosoite." (or just
  "postiosoite" without opening hours).

Sync. Verify in PTV's UI: the postal address is unchanged.

**G12. Postal address PTV's API would reject**

A postal street address with no number and no coordinates. Expect the
information line "Postiosoite poistuu PTV:stä, koska PTV ei hyväksy sitä
rajapinnan kautta – korjaa se PTV:ssä." The sync succeeds.

**G13. Opening hours are kept**

Add opening hours in PTV's UI, then sync from Lipas. Verify: the opening
hours are still in PTV.

---

## H. Käyttöönotto wizard

**H1. First link to a PTV-made location** (built)
1. Wizard step 3: for s8, open "Löytyykö palvelupaikka jo PTV:stä?".
2. Pick "Hyvinvointitalon lähiliikuntapaikka".

Expect:
- rows "Eroaa", with no origin;
- the intro "Palvelupaikka on tehty PTV:ssä…";
- connections listed as kept;
- the services selector below the review.

**H2. Export waits for choices** (built)

s8 can't be selected while its texts are empty, and taking or writing its
texts decides its rows. So link s8 (H1), decide its rows, and select it
together with a site that still has open choices (e.g. s9). Try to export.
Expect:
- the export button is disabled;
- the alert "Käy PTV-erot läpi ennen vientiä" lists each site's open rows;
- bulk "Ota kaikki PTV:n muutokset" / "Pidä Lipaksen versio kaikissa".

Use one bulk button, then export. Verify:
- one location, no duplicate;
- its connections kept;
- texts as chosen.

**H3. Bilingual PTV-made location** (phase 3, s15)

s15's site has no English texts; the PTV location of the same name has
fi+en. In the Liikuntapaikat tab the row offers "Liitä tähän
palvelupaikkaan" (F4); in the wizard, pick the location under "Löytyykö
palvelupaikka jo PTV:stä?". On the first link, expect:
- the fi texts as "Eroaa" rows, with no origin;
- the English texts as a language row: the sync would remove the English
  version unless English texts are written or taken (G10);
- the postal address kept, if the location has one (G11).

**H4. No "Lataa tekstit PTV:stä"** (built in the wizard; phase 4 in the tab)

The wizard has no such button. After phase 4 the Liikuntapaikat tab has
none either.

**H5. An export that fails stops and says where** (built)

Select a site whose location PTV refuses (s2 while its draft is stuck, see
Environment in the run notes) first, and another after it. Export. Expect:
- the progress stops; the alert "Vienti keskeytyi" names the site, says
  why (unpublished changes in PTV) and how many sites weren't tried;
- "Jatka vientiä" goes on from the failed site, and the count keeps
  counting the whole export.

---

## I. Stale review (phase 5)

**I1. PTV changes after the dialog was opened**
1. Open s1 in the tool.
2. In PTV's UI change the fi summary.
3. Back in the tool, without refreshing, click "Synkronoi nyt".

Expect:
- the sync is refused with "PTV:ssä on muutettu tietoja sen jälkeen, kun
  avasit näkymän. Tarkista muutokset ja synkronoi uudelleen.";
- the review reopens with the new row.

Verify: PTV's change was not overwritten.

**I2. Same in the site form**
1. Open s1 for editing.
2. Change PTV's summary in PTV's UI.
3. Save the form.

Expect:
- the Lipas save succeeds;
- the toast says PTV wasn't updated because it changed meanwhile;
- the site shows the stored error until reviewed and synced.

**I2b. A decision against an older PTV value doesn't count**
1. Open s4 in the tool; take PTV's summary (the row collapses as decided).
2. In PTV's UI change the summary again; reopen the row (it refetches).

Expect: the row is open again, with PTV's new text; "Synkronoi nyt" asks
first.

**I2c. An own text needs confirming**
1. In s4's site form, write a text of your own into the summary.

Expect: the row stays open with "Lomakkeessa on muokattu teksti" and "Vie
muokattu versio PTV:hen"; the gate dialog lists "Tiivistelmä (FI)" (a
text shows only its field name; a short value shows both sides). After confirming, the row collapses ("viedään PTV:hen muokattuna").
Typing more opens it again. AI generation over the summary does the same.

**I3. A connection made in PTV after opening**

As I1, but connect a service to the location from the service side in PTV.
Expect the same refusal: `modified` doesn't move for this, so the check
compares service ids.

---

## J. Org settings (phase 4)

**J1. Removing a language warns**

As an admin, remove English from Utajärvi's PTV languages. Expect, before
saving: "Seuraava synkronointi poistaa englanninkielisen kieliversion {n}
palvelupaikasta."

---

## K. Palvelut tab (phase 6, after #266)

The base is Lipas's mirror of the service after its last write
(`ptv_service`). A sync sends the edit, which holds PTV's own text in every
field the user hasn't edited, so only an edited field can overwrite PTV.

**K1. A service with nothing edited**

Open a Lipas-created service (e.g. "Jäähallit"). Expect the chip "Ajan
tasalla" and no panel, even if PTV changed its texts: those are kept.

**K2. An edit**

Edit the summary. Expect the chip "Valmis synkronoitavaksi", and the panel
with the summary under "Päivittyy PTV:hen Lipaksesta" and the footer
"Valinnat tulevat voimaan, kun synkronoit palvelun (Synkronoi nyt)."

**K3. An edit over a change made in PTV**

Change the summary in PTV's UI, then edit it in Lipas. Expect:
- the chip "Ristiriita", the row "Muutettu molemmissa";
- "Synkronoi nyt" asks first; "Käy muutokset läpi" scrolls to the panel and
  focuses the row;
- "Ota PTV:n versio" puts PTV's text back in the edit (chip "Ajan
  tasalla"); "Pidä Lipaksen versio" decides the row ("Valinnat tehty",
  chip "Valmis synkronoitavaksi").

**K4. A name changed in PTV**

Rename a Lipas-created service in PTV's UI, then sync another edit of it.
Verify: PTV keeps the new name; only a name edited in Lipas is sent.

**K4b. A service changed in PTV after the tool loaded it**

With the Palvelut tab open, change a Lipas-created service's summary in PTV's
UI, then edit and sync its description in Lipas. Expect the refusal ("muutettu
PTV:ssä"), the services reloaded, and the summary shown as PTV's. Verify: PTV
kept its summary.

**K5. Native and other-system services**

A PTV-made service the org has taken into Lipas ("Lipas capture -testi C")
shows the same chips; another system's service is read-only, with no panel.
A service with a draft in PTV reads "Estetty" with "Avaa PTV:ssä" once a
sync has hit the draft (services aren't refetched on opening, unlike
locations in F2).

---

## L. Every scenario, also

**L1. Narrow window**

At about 400 px wide: cards stack, nothing scrolls sideways, and the buttons
stay reachable.

**L2. Keyboard only**

Expand the bar, move between choice buttons, choose, and open "Käy muutokset
läpi" without a mouse. Focus is always visible.

**L3. Screen reader**

The choice buttons read with their field, e.g. "Ota PTV:n versio: Tiivistelmä
(FI)", and their pressed state. The highlighted words are marked as
highlighted (phase 4: `<mark>` plus a legend), not by colour alone.

**L4. Languages**

Switch the UI to Swedish and English. Every review text is translated, and no
key names show.

**L5. Console**

No errors or warnings in the browser console during any scenario.

**L6. Responsiveness**

Editing one site's text in a large org stays instant: the other sites aren't
recomputed (phase 1 cache).

---

## M. Tester feedback, November 2026

Fixes from Terhi's lipas-dev test (`local-scrap/2026-11-ptv-Havaintoja.pdf`,
`2026-11-ptv-Muutosehdotukset_tekstit.xlsx`). Utajärvi only. Service names
below are those of the Utajärvi test org.

**M1. Connections are one choice: take PTV's set** (C1)

Setup: s5 (507128 Utajärven urheilukenttä); `(state 507128)` shows
`:review [[[:services] :ptv]]`, else run `(user.ptv-drift-scenarios/s5-connections!)`.
1. PTV tool → Utajärven kunta → Liikuntapaikat, chip "Ristiriita", open s5,
   "Näytä muutokset".

Expect:
- a group "PALVELUT" with one row "Liitetyt palvelut", chip "Muutettu
  PTV:ssä" and "Valitse ennen synkronointia";
- the Lipas card lists "Pallokentät" (highlighted: only Lipas has it) and
  "Yleisurheilukentät ja -paikat"; the PTV card "Liikuntaneuvonta"
  (highlighted) and "Yleisurheilukentät ja -paikat"; the caption
  "Korostettu: palvelut, jotka on liitetty vain tässä versiossa.";
- the buttons "Pidä Lipaksen versio" and "Ota PTV:n versio"; no "Pidä
  liitos" / "Poista liitos" anywhere.

2. Click "Ota PTV:n versio". Expect:
   - the row collapses to "✓ Liitetyt palvelut: PTV:n versio otetaan
     Lipakseen [Muuta]", the chip turns "Valmis synkronoitavaksi";
   - ⑤ "Kopioidaan Lipakseen: Liitetyt palvelut";
   - the row's services field lists PTV's two services.
3. "Synkronoi nyt". Expect no question.

Verify: `(state 507128)`: Lipas's and PTV's services are both
yleisurheilu + liikuntaneuvonta, `:review []`. Reset with `s5-connections!`.

**M2. Keep Lipas's set in the site form** (C1)

Setup: s5 reset as in M1.
1. Open 507128 on the map, edit, PTV tab, "Näytä muutokset".
2. "Pidä Lipaksen versio" on "Liitetyt palvelut". Expect "✓ Liitetyt
   palvelut: Lipaksen versio viedään PTV:hen", ⑤ "Viedään PTV:hen: Liitetyt
   palvelut".
3. Save. Expect no question; "Tallennetaan ja synkronoidaan…", then
   success.

Verify: `(state 507128)`: PTV's services are Lipas's, yleisurheilu +
pallokentät; `:review []`. Reset with `s5-connections!`.

**M3. A set of one's own, and the gate line** (C1, C2; no write)

Setup: s5 reset.
1. Site form, edit, PTV tab: in the services selector keep only
   "Yleisurheilukentät ja -paikat".
2. Expect the row to stay open with "Lomakkeessa on muokattu
   palveluvalinta" listing it, and "Vie muokattu versio PTV:hen".
3. Save. Expect the dialog "PTV:ssä on muutoksia, joita et ole käynyt läpi"
   with the line "Liitetyt palvelut: PTV:n versio korvautuu muokatulla
   versiolla (poistetaan Liikuntaneuvonta)" and the buttons "Käy muutokset
   läpi", "Tallenna ja korvaa", "Peruuta".
4. "Peruuta", then cancel the edit.

Verify: nothing saved, `(state 507128)` as before.

**M4. First link keeps PTV's connections** (C1; no write)

s8 (506497 Hyvinvointitalon lähiliikuntapaikka) isn't synced; its PTV
location has its own texts and the services "Lähiliikunta ja
liikuntapuistot" and "Puistojumppa".
1. PTV tool → Liikuntapaikat, find s8, open it, link it to the existing
   location "Hyvinvointitalon lähiliikuntapaikka".

Expect:
- the bar "Palvelupaikan tiedot PTV:ssä eroavat Lipaksesta n kohdassa…";
- no "Liitetyt palvelut" row in ② (no decision);
- the row's services field holds Lipas's and PTV's services together (the
  link puts PTV's in, as the first sync keeps them);
- in ③ "Päivittyy PTV:hen Lipaksesta": "PTV:ssä liitetyt palvelut säilyvät
  ja kopioidaan Lipakseen: …" naming PTV's services the saved site lacks,
  and, for services only the edit has, "Lipaksessa liitetyt palvelut
  liitetään myös PTV:ssä: …"; ⑤ repeats the line(s).

**M5. Bulk take includes the connections** (C1; no write)

s9 (79800). Open it, "Ota kaikki PTV:n muutokset". Expect the "Liitetyt
palvelut" row decided with PTV's version and the services field holding
PTV's services (the archived test service b0181978 is gone from the edit).
"Kumoa valinnat" puts Lipas's set back. On lipas-dev, don't sync s9 with
Lipas's set: its archived test service b0181978 fails the sync with a 404
(known). The local s9 doesn't have it.

**M6. Gate on a first link says "differences"** (C2; no write)

From M4, with Finnish texts in the edit (e.g. take PTV's summary and
description), click "Synkronoi nyt" while other rows are open.

Expect:
- title "Lipas ja PTV eroavat";
- intro "Näitä Lipaksen ja PTV:n eroja ei ole vielä käyty läpi. Jos jatkat,
  PTV:n tiedot korvautuvat Lipaksen tiedoilla:";
- the lines, then "Jos PTV:n tiedot ovat oikeita, käy erot läpi ja valitse
  ne painamalla Käy erot läpi -painiketta.";
- buttons "Käy erot läpi" (primary), "Synkronoi ja korvaa", "Peruuta".

"Peruuta": nothing is sent.

**M7. Gate wording for changes made in PTV** (C2; no write)

1. s12 (600121, visiting address changed in PTV): site form, edit, save.
   Expect "PTV:ssä on muutoksia, joita et ole käynyt läpi", the line
   "Käyntiosoite: PTV:n ”Kirkkotie 21, …” korvautuu Lipaksen tiedolla
   ”…”", the hint "Jos PTV:n muutokset ovat oikeita, käy ne läpi ja valitse
   ne painamalla Käy muutokset läpi -painiketta." and the buttons "Käy
   muutokset läpi", "Tallenna ja korvaa", "Peruuta". "Peruuta", cancel the
   edit.
2. s10 (506493, phone changed in PTV): PTV tool, "Synkronoi nyt". Expect the
   same title and hint, "Puhelinnumero: PTV:n ”…” korvautuu Lipaksen
   tiedolla ”…”" and the middle button "Synkronoi ja korvaa". "Peruuta".

**M8. What the status chips mean** (C3)

PTV tool → Liikuntapaikat: hover each status chip. Expect one sentence each,
telling apart "Ei vielä viety" (integration on, never exported: the first
sync creates the location) from "Valmis synkronoitavaksi" (already in PTV,
Lipas has changes), "Ristiriita" (choose in the review) from "Estetty" (fix
in PTV: a draft, or the last sync failed), and "Kuvaukset puuttuvat" (no
Finnish summary or description, integration on or off) from "Integraatio
pois" (integration off).

**M9. Moving the point** (C4)

s11 (514575, PTV's point far from Lipas's): open the review. The "Sijainti"
row reads "Sijaintia ei voi kopioida PTV:stä. Jos PTV:n sijainti on oikea,
siirrä liikuntapaikkaa Lipaksen kartalla."

**M10. A type class without a service** (D1; no write)

97262 Rokuan hiihtostadion, a Maastohiihtokeskus, whose type class
Maastohiihtokeskukset has no service in Utajärvi's PTV org.
1. Open it on the map, edit, PTV tab, switch the integration on.

Expect:
- "Viimeistele PTV-tiedot": "Palvelu – Ei vielä valittu. Valitse palvelu
  alta." and "Maastohiihtokeskus kuuluu tyyppiluokkaan
  Maastohiihtokeskukset, jolle ei vielä ole palvelua PTV:ssä. Palvelu
  luodaan PTV-työkalun Palvelut-välilehdellä.";
- no "Luo uusi palvelu" under the services selector, nor the old "Valitse
  olemassa oleva palvelu tai luo uusi" text.
2. Write Finnish texts (over 5 characters each). The finish box goes; the
   sentence about the missing service shows under the services selector.
3. Cancel the edit.

**M11. Optional language versions** (D2; no write)

As M10 step 1. Expect under "Tiivistelmä ja kuvaus suomeksi": "Kirjoita
tiivistelmä ja kuvaus tai tee luonnokset tekoälyllä." and, Utajärvi having
fi + en, "Organisaatiollasi on käytössä myös kieliversio EN. Se on
valinnainen, mutta muista täyttää sekin, jos haluat liikuntapaikan tiedot
PTV:hen myös tällä kielellä." (Raahe, with SE and EN: "…myös kieliversiot SE,
EN. Ne ovat valinnaisia, mutta muista täyttää nekin, … näillä kielillä.")

---

## N. Archived services

When a service is archived in PTV, PTV drops the location's connection to it
at once and answers 404 to every GET of it, but Lipas still lists it in the
site's `[:ptv :service-ids]`. Every sync of such a site used to fail after
writing the location (connecting the service read it), and Lipas showed the
bare id. Now (2026-11-07):
- the review compares the connections without it and names it in a notice
  of ① ("Palvelu X on arkistoitu PTV:ssä 16.5.2026. Liitos poistuu
  Lipaksesta synkronoinnissa."): nothing to choose, the site reads "Valmis
  synkronoitavaksi";
- the services field and the selector's chips name it "X (arkistoitu
  PTV:ssä)";
- a sync reads the services it connects before it writes anything, drops the
  connections to those PTV no longer has from Lipas, and says so ("Liitos
  arkistoituun palveluun X poistettiin.");
- an id PTV doesn't have at all (not in the org's archived list either:
  purged, or another environment's) is treated the same, as "Tuntematon
  palvelu (ei löydy PTV:stä)" with the id in small print.

PTV calls: the PTV tool lists the org's archived services once (and GETs an
id that list doesn't have) only when a site connects a service that neither
the org's published services nor any location's connections have. The site
form's review fetch already read the services only Lipas connects; a 404
there is now reported. The sync's read of a service it connects moved before
the location write.

**Setup** (writes to PTV that can't be undone: each run creates throwaway
services named "(Lipas-testi)" and archives them):

```clojure
(load-file "dev/user/ptv_drift_scenarios.clj")
(user.ptv-drift-scenarios/n1-archived-service!)      ; s3 506495 + "Arkistointikoe N1 (Lipas-testi)", archived
(user.ptv-drift-scenarios/n2-archived-and-unknown!)  ; s7 600093 + "Arkistointikoe N2 (Lipas-testi)", archived,
                                                     ; + 5b3f8d0e-… added in Lipas, an id PTV doesn't have
```

`(state lipas-id)` now reviews with the services the site form's review has
and lists those PTV no longer has under `:gone`. After the setup, `(state
506495)` has `:review []`, `:gone [[<id> :archived "Arkistointikoe N1
(Lipas-testi)"]]`, two services in Lipas and one in PTV.

**N1. The PTV tool before the sync** (no write)

1. PTV tool → Utajärven kunta → Liikuntapaikat.

Expect:
- s3 and s7 read "Valmis synkronoitavaksi", not "Ristiriita";
- open s3: an info notice "Palvelu Arkistointikoe N1 (Lipas-testi) on
  arkistoitu PTV:ssä 7.11.2026. Liitos poistuu Lipaksesta
  synkronoinnissa." (the day of the setup); no bar, no "Liitetyt palvelut"
  row, nothing to choose;
- "Palvelut PTV:ssä" lists "Lähiliikunta ja liikuntapuistot" (a link to PTV)
  and "Arkistointikoe N1 (Lipas-testi) (arkistoitu PTV:ssä)" (no link);
- "Synkronoi nyt" enabled.

2. The pencil of "Palvelut PTV:ssä": the chips read "Lähiliikunta ja
   liikuntapuistot" and "Arkistointikoe N1 (Lipas-testi) (arkistoitu
   PTV:ssä)", no id. "Peruuta".

**N2. An id PTV doesn't have, in the PTV tool** (no write)

Open s7. Expect:
- the notice with two lines, the archived service first: "Palvelu
  Arkistointikoe N2 (Lipas-testi) on arkistoitu PTV:ssä 7.11.2026. Liitos
  poistuu Lipaksesta synkronoinnissa." and "Tuntematon palvelu (ei löydy
  PTV:stä). Liitos poistuu Lipaksesta synkronoinnissa." with
  "5b3f8d0e-2f6a-4c1e-9d7a-1a2b3c4d5e6f" in small print under it;
- "Palvelut PTV:ssä" and the chips name both the same way, the id in small
  print under "Tuntematon palvelu (ei löydy PTV:stä)" in the field.

**N3. The tool's sync drops them and says so**

From N2: "Synkronoi nyt". Expect no question, then the notification
"Tallennus onnistui. Liitos arkistoituun palveluun Arkistointikoe N2
(Lipas-testi) poistettiin. Liitos palveluun 5b3f8d0e-… poistettiin, koska
palvelua ei löydy PTV:stä."; the row reads as synced.

Verify: `(state 600093)`: Lipas's and PTV's services are both uimarannat,
`:review []`, `:gone []`, `:last-sync` moved; no `[:ptv :error]` on the site.

**N4. The site form before the save** (no write)

s3 after the setup. Open 506495 on the map, "Muokkaa", PTV tab. Expect the
same notice, nothing to choose, "Palvelut" naming "Arkistointikoe N1
(Lipas-testi) (arkistoitu PTV:ssä)" and, with its pencil, the chips likewise.
The view mode shows the same once the site's review is fetched.

**N5. The site form's save drops it and says so**

From N4: "Tallenna". Expect no question; "Tallennetaan ja synkronoidaan
PTV:hen…", "Tallennus onnistui", then "Tallennus onnistui. Liitos
arkistoituun palveluun Arkistointikoe N1 (Lipas-testi) poistettiin."

Verify: `(state 506495)`: services lahiliikunta on both sides, `:review []`,
`:gone []`, `:last-sync` moved, no error.

**N6. An id PTV doesn't have, in the site form**

After N5, add the id in Lipas:
`(#'user.ptv-drift-scenarios/set-site! 506495 #(update-in % [:ptv :service-ids] conj user.ptv-drift-scenarios/unknown-service-id))`,
then reload the site. The PTV tab (view mode, then "Muokkaa"): "Tuntematon
palvelu (ei löydy PTV:stä). Liitos poistuu Lipaksesta synkronoinnissa." with
the id; the field and the chip "Tuntematon palvelu (ei löydy PTV:stä)".
"Tallenna": "Tallennus onnistui. Liitos palveluun 5b3f8d0e-… poistettiin,
koska palvelua ei löydy PTV:stä."

**Reset:** `(user.ptv-drift-scenarios/run-all! [#'user.ptv-drift-scenarios/s3-lipas-texts! #'user.ptv-drift-scenarios/s7-new-language!])`.

Covered by tests only (`lipas.backend.ptv.sync-review-test`): a sync without
a review (a quick edit in the search table, a site-form save whose review
didn't arrive) goes through and drops the service; a service archived while
the location was being written (its connection answers 404) is dropped too;
PTV not answering for a service (5xx) refuses the sync before anything is
written. `lipas.data.ptv-review-test`: the review's rows are the same with
and without services PTV no longer has (generative).

---

## O. Save waits for the PTV review

When an edit starts, the site form fetches PTV's side of the site
(`fetch-ptv-site-review`). A PTV manager's save of a synced site needs it to
ask before overwriting changes made in PTV, so until it lands Save is
disabled, with a spinner in place of its icon and the tooltip "Haetaan PTV:n
tietoja… Voit tallentaa, kun ne on haettu." A failed fetch releases Save with
the warning "PTV:n tietoja ei saatu haettua. Voit tallentaa, mutta jos
PTV:ssä on muutoksia, joita ei ole käyty läpi, liikuntapaikkaa ei
synkronoida."; a fetch with no answer in 30 s counts as failed. Only the
site's latest fetch lands: an edit drops the review an earlier fetch
brought, and a late answer to an earlier fetch (the view's, a previous
edit's) is ignored. Nothing in this section writes; s12 (600121) unless
said otherwise.

**Slowing or failing the fetch.** The fetch answers in about 2 s, too fast
to watch. Wrap the backend function for s12 in the REPL, one plan step per
fetch: `{:delay ms}`, `{:fail true}` (a 500), `{:replace {old new}}`
(rewrites a value in the answer). A `(user/reset)` (anyone's) drops the
wrapper, so check it before each scenario. A Playwright `page.route` on the
request can't be used: the headless page crashed soon after one was set,
in view mode too.

```clojure
(def plan (atom []))                ; e.g. (reset! plan [{:delay 25000}])
(alter-var-root
  #'lipas.backend.ptv.core/get-ptv-site-review
  (fn [f]
    (with-meta
      (fn [db ptv lipas-id]
        (let [step (when (= 600121 lipas-id)
                     (ffirst (swap-vals! plan #(vec (rest %)))))]
          (some-> (:delay step) Thread/sleep)
          (when (:fail step) (throw (ex-info "simulated" {})))
          (cond->> (f db ptv lipas-id)
            (:replace step) (clojure.walk/postwalk-replace (:replace step)))))
      {::orig f})))
;; afterwards
(alter-var-root #'lipas.backend.ptv.core/get-ptv-site-review #(::orig (meta %) %))
```

**O1. Save waits after "Muokkaa"**

Setup: s12 open in view mode, the PTV tab with its dot; plan
`[{:delay 25000}]`.
1. "Muokkaa".

Expect:
- "Tallenna" disabled, a spinner in place of its icon; hovering it shows
  "Haetaan PTV:n tietoja… Voit tallentaa, kun ne on haettu.";
- the PTV tab without its dot: the view's review went with the edit's start;
- once the fetch answers, "Tallenna" enabled with its icon, the dot back.

Verify (app-db): while it waits, `[:ptv :review :request 600121]` is
`{:id … :edit? true}` and `[:ptv :review :site 600121]` empty; after, the
request is gone and the review there.

**O2. A quick save can't skip the question**

Setup as O1.
1. "Muokkaa", then "Tallenna" at once, twice.
2. Once "Tallenna" is enabled, "Tallenna".

Expect: step 1 does nothing (no dialog, no save); step 2 asks "PTV:ssä on
muutoksia, joita et ole käynyt läpi" with "Käyntiosoite: PTV:n ”Kirkkotie 21,
91600 UTAJÄRVI” korvautuu Lipaksen tiedolla ”Kirkkotie 19, 91600
Utajärvi”". "Peruuta", cancel the edit.

Verify: s12's revision count unchanged.

**O3. A failed fetch releases Save**

Plan `[{:delay 4000 :fail true}]`.
1. "Muokkaa".

Expect: Save waits about 4 s, then is enabled, and the warning above stays
until closed; no dot on the PTV tab. Cancel the edit. A save from here goes
as it did before this change: Lipas saves, and the backend refuses the sync
while PTV holds undecided changes (`ptv-not-reviewed`, finding 1 of the
2026-11-07 run).

**O4. A fetch that never answers**

Plan `[{:delay 45000}]`.
1. "Muokkaa".

Expect: Save waits 30 s, then as O3. Cancel the edit.

**O5. An earlier edit's late answer doesn't feed the next edit**

Plan `[{:delay 22000 :replace {"Kirkkotie 21" "Vanhakatu 99"}} {} {}]`:
the first edit's fetch answers late, with PTV's visiting address rewritten;
the view's and the second edit's answer at once.
1. "Muokkaa", and at once cancel the edit ("Peruuta", "Kyllä").
2. "Muokkaa" again.
3. Once "Tallenna" is enabled and the first fetch has answered, "Tallenna".

Expect: step 2's Save waits for its own fetch only, though the first is
still out; step 3's dialog names PTV's "Kirkkotie 21", never "Vanhakatu 99".
"Peruuta", cancel the edit.

Verify: app-db's review of 600121 still reads "Kirkkotie 21" after the late
answer.

**O6. Cancel releases**

As O5 step 1: cancelling while Save waits drops the edit's fetch
(`[:ptv :review :request 600121]` is empty, or the view's own
`{:edit? false}` refetch); view mode has no Save; the next edit waits for
nothing but its own fetch.

**O7. The view's fetch never blocks**

Plan `[{:delay 10000}]`, then let the view refetch (open s12, or dispatch
`[:lipas.ui.map.events/fetch-ptv-site-review 600121]`). Expect the request
`{:edit? false}` and `[:lipas.ui.map.subs/ptv-review-pending? 600121]`
false while it's out. "Muokkaa" while it's out: Save waits for the edit's
own fetch, and the view's late answer is ignored.

**O8. A site without PTV sync**

s8 (506497, not linked): "Muokkaa". Expect "Tallenna" enabled at once, no
spinner, no fetch. Cancel the edit.

**O9. A user without PTV rights**

s12 as an editor without PTV rights (or admin with `:ptv/manage` off in the
dev tool's overrides): "Muokkaa". Expect no PTV tab, no fetch, "Tallenna"
enabled at once. Cancel the edit, reset the override.

Covered by tests only (`lipas.ui.ptv.review-fetch-test`): whatever order the
fetches start, answer and are dropped in, only the latest one's answer lands,
once, and Save waits exactly while an edit's fetch is out (generative).

---

## Results: event-level run (2026-10-26)

Run in a Chrome tab the OS had hidden, so nothing painted: each step
dispatched the app's own re-frame events (as the buttons do), and checked
app-db, the HTTP requests, the DB and PTV (Utajärvi, test env). What this
covers is everything but the rendering: card layout, focus, scrolling and
colours are still to be looked at.

| Scenario | Site | Result |
|---|---|---|
| D1 fetch on edit, badge count | s10 | pass (after cdbc68868: the rights check had blocked it) |
| D2 save with an open row | s10 | pass: dialog title, "Käy muutokset läpi" primary, nothing saved; it opens the PTV tab with the panel open |
| D4 take PTV's phone | s10 | pass: Perustiedot phone is PTV's; Lipas and PTV agree after the save |
| D3, D8 decided save | s10 | pass: no question, `:reviewed` sent, "Tallennetaan ja synkronoidaan…" until "Tallennus onnistui" |
| D5 undo | s4 | pass: saved text back, choice dropped |
| D6 cancel | s10 | **failed, fixed** (4ce2c4f3c): a "keep Lipas" choice survived the cancel |
| I2 PTV changed after the form opened | s10 | pass: Lipas saved, sync refused (`ptv-changed-since-review`), toast, PTV kept its number |
| I2c own text | s4 | pass: open until "Vie muokattu versio PTV:hen", more typing reopens, PTV has the text after the save |
| Editor without PTV rights (dev override, FE only) | s10 | pass: no PTV tab, no fetch, a save that wouldn't sync |
| Tool sync while the site is open in the map | s10 | **failed, fixed** (1ce13e21b): the map kept the pre-sync revision |
| D5b undo keeps what the user wrote | s10 | pass, after the change below: typed number back after undo; typing after the choice kept |

Display note: phones show in the comparison form ("+358 858755700") on the
cards and in the dialog, not as typed in Lipas ("08 5875 5700").

## Results: visual run (2026-10-27)

Clicked and typed in a visible tab, mostly at 1312×645 (page zoom 0.75 for
the screenshots), against the same Utajärvi test data. Nothing was synced
or exported: the writes were covered by the event-level run.

| Scenario | Site | Result |
|---|---|---|
| A1 chips, banner, "Synkronoi kaikki valmiit (6)" | all | pass |
| B tool row: cards, captions, bulk, footer | s9 | pass |
| C gate from "Synkronoi nyt", "Käy muutokset läpi" | s9 | pass: scrolls to the panel, focus on the first open row's first button |
| D1 dot on the PTV tab, edit mode | s9 | pass |
| D2 gate from Perustiedot on a fresh load (review code not loaded yet) | s9 | pass: the PTV tab opens with the panel expanded and the focus on "Pidä Lipaksen versio: Nimi (FI)" |
| D3 take PTV's name | s9 | pass: Perustiedot holds PTV's name |
| D4 phone row | s10 | pass: caption "Muuttaa myös Lipaksen puhelinnumeron (Perustiedot)", one-number note, "Valitse vielä" |
| D5 undo in the form | s9 | pass: name back, 0/3 chosen |
| D6 cancel, reopen | s9 | pass: no choices |
| D9 view mode | s9 | pass: dot, read-only rows, no choice/bulk/outcome lines, view-mode footer |
| F2 draft in PTV | s1, s2 | pass: "Estetty" once opened, the notice and "Avaa PTV:ssä", "Synkronoi nyt" disabled. Changed after the run, see below |
| G10 language version PTV has | s17 | **failed, fixed**: see below |
| H1 first link in the wizard | s8 | pass: "Eroaa" rows, "(ei arvoa)" for Lipas, PTV's connections kept below; **"Kumoa valinnat" showed with nothing to undo, fixed** |
| H2 export waits for choices | 15 sites | pass: export disabled, the alert lists each site's open rows, bulk buttons |
| K1, K2, K5 Palvelut | Jäähallit | pass: "Ajan tasalla"; an edit shows "Valmis synkronoitavaksi" and the panel; undoing the typing goes back |
| L1 at ~400 px | s8, s9 | pass in the wizard and the site form. The Liikuntapaikat tab's table scrolls sideways and its header (filter, chips, banner) fills a phone screen: the tool's layout, not the review's |
| L2 keyboard | s9, s8, s17 | **failed, fixed**: see below |
| L4 sv, en | s9, s17 | pass, no missing keys |
| L5 console | s9 | **a warning, fixed**: "Reactive deref not supported in lazy seq" from the review panel |

Fixed after the run:
- **Focus** dropped to the page when a choice collapsed its row, when
  [Muuta] opened it, and when "Kumoa valinnat" went with the choices. It
  now goes to the row's [Muuta], the row's first button, and the first row
  to choose again (or the first choice) respectively.
- **A language version kept with PTV's texts** still read "Poistuu
  PTV:stä" and "Synkronointi poistaa…", and the bar counted the language
  as a change ("päivittää PTV:hen 1 tietoa. Lisäksi … poistaa …", then "0
  tietoa" once taken). Now: "Synkronointi poistaa PTV:stä englanninkielisen
  kieliversion."; taken, the chip "Säilyy", "Englanninkielinen kieliversio
  säilyy PTV:ssä, ja sen tekstit kopioidaan Lipakseen.", the bar "Valinnat
  tehty. PTV:n versiot otetaan Lipakseen." and the outcome line "Kopioidaan
  Lipakseen: Englanti (EN)".
- **"Kumoa valinnat"** showed whenever the edit held one of PTV's versions,
  also the connections a first link keeps, which it can't undo. It shows
  now for a choice or a write a choice made.
- **The review panel** read its expanded rows inside a lazy seq, so a
  [Muuta] could miss a re-render.

Not changed: the clipped tab labels in the site form ("PERUSTIEDO",
"STEETTÖMYY…") at this panel width are older than this work.

Tooling note: Chrome reports a tab of a window that isn't in front as
hidden and pauses its animation frames, which stalls Reagent's renders.
For a run in such a tab, point Reagent's scheduler at a MessageChannel
first (`reagent.impl.batching.next_tick`), or keep the window in front.

### F2 again, after the draft change (2026-10-27)

With the user: while PTV holds a draft, the review is hidden (it compared
with the published version, read as the draft's changes, and no choice
could be written), the notice gets "Tarkista uudelleen", and the site form's
save asks nothing. Run locally:

| Case | Site | Result |
|---|---|---|
| Tool, real draft: notice, both buttons, the line, no bar or rows | s1 | pass |
| Tool, "Tarkista uudelleen" with the draft still there | s1 | pass: spinner, button disabled, then "PTV:ssä on yhä julkaisemattomia muutoksia…" |
| Tool, the draft gone (simulated: the copy marked Modified, PTV's answer real) | s9 | pass: chip "Ristiriita", the review back, no toast |
| Site form, view mode, real draft | s1 | pass: same as the tool |
| Site form, "Tarkista uudelleen", draft still there | s1 | pass |
| Site form, the draft gone (simulated as above) | s9 | pass: both copies refetched, the review back |
| Save with a real draft | s1 | pass: no question; "Liikuntapaikka tallennettu, mutta PTV-synkronointi epäonnistui – PTV:ssä on julkaisemattomia muutoksia…" (s1 got fi texts and construction year 2014 for this) |
| Save with open rows and a draft (simulated; the save itself recorded, not sent) | s9 | pass: no question, `:reviewed` sent; without the draft the question shows |
| A recheck that fails | – | "PTV:n tietoja ei saatu haettua. Yritä hetken päästä uudelleen." |


## Results: run of 2026-11-07

Section M, local Lipas against the PTV test env, Utajärvi only, as admin.
The Claude in Chrome tab couldn't open https://localhost: Chrome showed its
certificate warning (the local certificate is self-signed and expired in
2019), and the extension can't act on Chrome's error pages. The run used
`playwright-cli` instead (headless Chrome with the project's
`.playwright/cli.config.json`, which accepts the local certificate), with
the MessageChannel render patch, clicks and app events, and screenshots
read back. A dev-bundle page load took 3–5 minutes there. PTV writes took
under 30 s.

| Scenario | Site | Result |
|---|---|---|
| M1 take PTV's set, sync | s5 | pass: one row "Liitetyt palvelut", "Muutettu PTV:ssä"; Lipas card Pallokentät (marked) + Yleisurheilukentät ja -paikat, PTV card Liikuntaneuvonta (marked) + the same; the legend; "Ota PTV:n versio" collapsed it to "✓ Liitetyt palvelut: PTV:n versio otetaan Lipakseen", ⑤ "Kopioidaan Lipakseen: Liitetyt palvelut", the chip "Valmis synkronoitavaksi", the edit's ids PTV's two; "Synkronoi nyt" asked nothing. `(state 507128)`: Lipas and PTV both yleisurheilu + liikuntaneuvonta, `:review []` |
| M2 keep Lipas's set in the site form | s5 | pass: stacked cards in the PTV tab; "Pidä Lipaksen versio" put the saved ids back, "✓ Liitetyt palvelut: Lipaksen versio viedään PTV:hen", ⑤ "Viedään PTV:hen: Liitetyt palvelut"; Save asked nothing, "Tallennetaan ja synkronoidaan…" then "Tallennus onnistui". `(state 507128)`: PTV pallokentät + yleisurheilu, `:review []` |
| M3 own set + gate | s5 | pass: "Lomakkeessa on muokattu palveluvalinta" listing Yleisurheilukentät ja -paikat, "Vie muokattu versio PTV:hen"; Save: "PTV:SSÄ ON MUUTOKSIA, JOITA ET OLE KÄYNYT LÄPI", "Liitetyt palvelut: PTV:n versio korvautuu muokatulla versiolla (poistetaan Liikuntaneuvonta)", hint "…painamalla Käy muutokset läpi -painiketta.", buttons Peruuta / Tallenna ja korvaa / Käy muutokset läpi. Peruuta: nothing saved |
| M4 first link | s8 | pass: linking (the selector's event) put PTV's two services in the edit; bar "Palvelupaikan tiedot PTV:ssä eroavat Lipaksesta 2 kohdassa…" (summary, description); no services row in ②; ③ and ⑤ "PTV:ssä liitetyt palvelut säilyvät ja kopioidaan Lipakseen: Lähiliikunta ja liikuntapuistot, Puistojumppa" |
| M5 bulk take, undo | s9 | pass: "Ota kaikki PTV:n muutokset" decided name, description and connections (3/3), edit ids = PTV's (Kuntoilukeskukset ja liikuntasalit + Soveltava liikunta); "Kumoa valinnat" back to Lipas's one, 0/3. Not synced |
| M6 first-link gate | s8 | pass: own summary (unconfirmed) + PTV's description, "Vie palvelutietovarantoon": "LIPAS JA PTV EROAVAT", "Näitä Lipaksen ja PTV:n eroja ei ole vielä käyty läpi. Jos jatkat, PTV:n tiedot korvautuvat Lipaksen tiedoilla:", "Tiivistelmä (FI)", "…käy erot läpi ja valitse ne painamalla Käy erot läpi -painiketta.", buttons Peruuta / Synkronoi ja korvaa / Käy erot läpi. Peruuta: s8 unchanged in the DB |
| M7 changes-made-in-PTV gate | s12, s10 | pass: site form (s12) "Käyntiosoite: PTV:n ”Kirkkotie 21, 91600 UTAJÄRVI” korvautuu Lipaksen tiedolla ”Kirkkotie 19, 91600 Utajärvi”", "Tallenna ja korvaa"; tool (s10) "Puhelinnumero: PTV:n ”+358 401234567” korvautuu Lipaksen tiedolla ”+358 858755700”", "Synkronoi ja korvaa", the summary and description as field names, the language removal. See finding 1 |
| M8 chip tooltips | – | pass: each of the nine chips shows its sentence on hover; clicking a chip still filters ("Ristiriita (7)") |
| M9 point | s11 | pass: "…siirrä liikuntapaikkaa Lipaksen kartalla." |
| M10 missing service | 97262 | pass: "Palvelu – Ei vielä valittu. Valitse palvelu alta. Maastohiihtokeskus kuuluu tyyppiluokkaan Maastohiihtokeskukset, jolle ei vielä ole palvelua PTV:ssä. Palvelu luodaan PTV-työkalun Palvelut-välilehdellä."; no "Luo uusi palvelu"; with texts written the box goes and the sentence shows under "Valitse palvelu". Edit discarded, site unchanged |
| M11 optional languages | 97262 | pass (after a reload, with the singular form added after the first look): "Kirjoita tiivistelmä ja kuvaus tai tee luonnokset tekoälyllä." and "Organisaatiollasi on käytössä myös kieliversio EN. Se on valinnainen, mutta muista täyttää sekin, jos haluat liikuntapaikan tiedot PTV:hen myös tällä kielellä." The first look showed the plural text with one language ("kieliversiot EN. Ne ovat…"), hence the singular key. In Swedish and English the whole box reads right ("Skidcentrum hör till typklassen Längdåkningscentra…", "Cross-country skiing centre belongs to the type class Cross-country ski resorts…"). Edit discarded |

Findings:
1. **Saving before the site form's review has arrived.** The review is
   fetched when the edit opens (`fetch-ptv-site-review`, a PTV round trip of
   a few seconds). A save clicked before it lands asks nothing: on s12 the
   first save, ~6 s after "Muokkaa", saved Lipas and the backend refused the
   sync (`ptv-not-reviewed`): "Liikuntapaikka tallennettu, mutta
   PTV-synkronointi epäonnistui…", and the site stored the error (it reads
   "Estetty" until synced). PTV was not overwritten; the server guard held.
   Also, the fetch of a previous edit stays in app-db until the new one
   lands, so a quick save built its question on it: after s12 was re-synced
   from the REPL, the gate read "Lipas ja PTV eroavat" (the old base no
   longer matched) until the refetch arrived, then "PTV:ssä on muutoksia…".
   Not fixed: worth disabling Save, or waiting, while the review is being
   fetched, and dropping the old fetch when an edit starts.
2. ⑤ says "Viedään PTV:hen muokattuna: Liitetyt palvelut" for a set of the
   user's own before it is confirmed, while the row still asks for a choice.
   Texts behave the same (older behaviour of the outcome lines).
3. On a first export the gate's middle button reads "Synkronoi ja korvaa",
   although the tool's own button is "Vie palvelutietovarantoon".

Environment left behind: s5 was synced twice (M1, M2) and reset with
`s5-connections!` after each, so it's back to `[[:services] :ptv]`. s12
got a Lipas revision with the stored sync error (finding 1) and was reset
with `s12-address!` (`[[:address] :ptv]`, no error). s8, s9, s10, s11 and
97262 unchanged (nothing saved or synced). The playwright session was
closed.

## Results: archived services, run of 2026-11-07

Section N, local Lipas against the PTV test env, Utajärvi only, as admin,
in `playwright-cli` (headless Chrome, session `arch`, the project's
`.playwright/cli.config.json`), with the MessageChannel render patch; clicks
by element, the notifications read from the page as they appeared,
screenshots read back. PTV writes took 1–5 s: the setup of both sites (two
`in-sync!`s, a service created, a sync, an archive, each) took under a
minute.

| Scenario | Site | Result |
|---|---|---|
| N1 tool before the sync | s3 | pass: chip "Valmis synkronoitavaksi" (s7 too), not "Ristiriita"; info notice "Palvelu Arkistointikoe N1 (Lipas-testi) on arkistoitu PTV:ssä 7.11.2026. Liitos poistuu Lipaksesta synkronoinnissa."; no bar, no "Liitetyt palvelut" row; "Palvelut PTV:ssä": the link "Lähiliikunta ja liikuntapuistot" and the text "Arkistointikoe N1 (Lipas-testi) (arkistoitu PTV:ssä)"; the chips the same; "Synkronoi nyt" enabled. The tool asked PTV about the three unlisted ids once (`:unlisted-services` in app-db: two `:archived` with their dates and names, one `:unknown`) |
| N2 unknown id in the tool | s7 | pass: "Tuntematon palvelu (ei löydy PTV:stä). Liitos poistuu Lipaksesta synkronoinnissa." with the id under it, and the archived service's line; field and chips "Tuntematon palvelu (ei löydy PTV:stä)" (id under it in the field) and "Arkistointikoe N2 (Lipas-testi) (arkistoitu PTV:ssä)". The unknown line came first then; see finding 3 |
| N3 tool sync | s7 | pass: no question; "Tallennus onnistui. Liitos palveluun 5b3f8d0e-2f6a-4c1e-9d7a-1a2b3c4d5e6f poistettiin, koska palvelua ei löydy PTV:stä. Liitos arkistoituun palveluun Arkistointikoe N2 (Lipas-testi) poistettiin."; the row "Viety PTV:hen 07.11.2026 13:17:05". `(state 600093)`: uimarannat on both sides, `:review []`, `:gone []`, last-sync 11:17:05Z, no error |
| N4 site form before the save | s3 | pass: PTV tab in edit mode: the notice, "Palvelut": the link and "Arkistointikoe N1 (Lipas-testi) (arkistoitu PTV:ssä)", the chips under "Valitse palvelu" the same; nothing to choose |
| N5 site-form save | s3 | pass: no question; "Tallennetaan ja synkronoidaan PTV:hen… Tämä voi kestää hetken.", "Tallennus onnistui", then (6–8 s later) "Tallennus onnistui. Liitos arkistoituun palveluun Arkistointikoe N1 (Lipas-testi) poistettiin." `(state 506495)`: lahiliikunta on both sides, `:review []`, `:gone []`, last-sync 11:21:25Z, no error |
| N6 unknown id in the site form | s3 | pass: view mode and edit mode both showed "Tuntematon palvelu (ei löydy PTV:stä). Liitos poistuu Lipaksesta synkronoinnissa." with the id, the field likewise, the chip "Tuntematon palvelu (ei löydy PTV:stä)"; save: "Tallennus onnistui. Liitos palveluun 5b3f8d0e-2f6a-4c1e-9d7a-1a2b3c4d5e6f poistettiin, koska palvelua ei löydy PTV:stä." `(state 506495)`: lahiliikunta on both sides, no error |

Console: no errors from the app (one OpenLayers `renderFrame` error from the
render patch's requestAnimationFrame stand-in).

Findings:
1. A site whose only pending change is the drop reads "Valmis
   synkronoitavaksi", whose tooltip says "Lipaksessa on muutoksia, jotka
   synkronointi vie PTV:hen". The sync sends PTV nothing new; it updates
   Lipas. Not changed: the notice says what happens.
2. In the site form the drop's notification replaces "Tallennus onnistui"
   6–8 s after it (the save response carries no list of dropped services;
   the site form's own review knew them, and the notification comes through
   the lazy `:ptv` module). The PTV tool's comes with the sync's own.
3. The notice listed the unknown id before the archived service, and the
   tool's notification likewise. Changed after the run: services archived
   in PTV first, then by name, one nobody can name last
   (`ptv-review/gone-services`, unit-tested); not re-run in the browser.
4. An unknown id without a name is named by its id in the notification
   ("Liitos palveluun 5b3f8d0e-… poistettiin, …"), the only thing known of
   it.

Environment left behind:
- PTV test env: two more archived throwaway services in Utajärvi,
  "Arkistointikoe N1 (Lipas-testi)" 38c98454-9850-4c7d-aeea-91a563210c4d
  and "Arkistointikoe N2 (Lipas-testi)" f39d985f-95a1-45e1-82cc-a478938260d5
  (archived services can't be deleted). Lipas's `ptv_service` mirror (local
  DB) still has both as "active", as it has the October one, b0181978.
- s3 (506495) and s7 (600093) reset with `s3-lipas-texts!` and
  `s7-new-language!`: `(state 506495)` `[[[:description "fi"] :lipas]]`,
  `(state 600093)` the three English rows `:lipas`, `:gone []` on both, as
  before the run.
- The playwright session `arch` was closed.

## Results: save waits for the review, run of 2026-11-07

Section O, local Lipas against the PTV test env, as admin, s12 (600121) and
s8 (506497). Headless Chrome through `playwright-cli` (session `save`), with
clicks, a sampler inside the page recording the Save button's state every
200 ms, and a logging-only re-frame interceptor recording the fetch and save
events with the fetch's id. The fetch was slowed or failed in the backend
REPL (see O); times are UTC. Nothing was saved or synced.

| Scenario | Result |
|---|---|
| O1 Save waits | pass. Fetch held 25 s: click 11:39:43, the edit's fetch `8212e0d1` 11:39:47.6 (`:edit? true`), first paint of the edit 11:39:54.8 with "Tallenna" disabled, spinner, tooltip "Haetaan PTV:n tietoja… Voit tallentaa, kun ne on haettu." (hovered, screenshot); review empty and no dot on the PTV tab meanwhile; answer 11:40:15.3, "Tallenna" enabled with its icon 11:40:17.5, dot back. The view's refetch `ed966983` was still out when the edit started; it failed at 11:39:57 and was ignored (no release, no warning) |
| O2 quick save | pass. Two clicks on the disabled "Tallenna" (11:41:25.8, 11:41:29.8): no `save-edits`, no dialog. Enabled 11:41:50.9; "Tallenna" then asked "PTV:ssä on muutoksia, joita et ole käynyt läpi", "Käyntiosoite: PTV:n ”Kirkkotie 21, 91600 UTAJÄRVI” korvautuu Lipaksen tiedolla ”Kirkkotie 19, 91600 Utajärvi”". Peruuta; s12 still 23 revisions |
| O3 failed fetch | pass. 500 after 4 s: "Tallenna" disabled until 11:42:56, then enabled with the orange warning "PTV:n tietoja ei saatu haettua. Voit tallentaa, mutta jos PTV:ssä on muutoksia, joita ei ole käyty läpi, liikuntapaikkaa ei synkronoida." (screenshot); no review, no dot. Not saved (see O3) |
| O4 no answer | pass. Fetch dispatched 11:43:34.7, failed 11:44:05.7 (the 30 s timeout), "Tallenna" enabled with the warning |
| O5 late answer of an earlier edit | pass. The first run's held answer came back after the 30 s timeout, as a failure: ignored, no warning. Rerun with 22 s: edit 1 `548434c1` cancelled while waiting; edit 2 `1bdf47cd` waited for its own fetch only and was enabled at 11:46:07, while edit 1's was still out; edit 1's answer ("Vanhakatu 99") landed as a success at 11:46:10.6 and was ignored: app-db kept "Kirkkotie 21", and "Tallenna" asked about "Kirkkotie 21". The view's refetch `bdffb9d2`, out when edit 2 started, was ignored the same way |
| O6 cancel | pass (O5): the cancel dropped the edit's request (the view's refetch started with none); view mode had no Save; edit 2 waited for nothing of edit 1's |
| O7 view's fetch | pass. Held 10 s in view mode: request `{:edit? false}`, `ptv-review-pending?` false. Its late answer during an edit: O1, O5 |
| O8 site without sync | pass. s8: "Tallenna" enabled at the first paint of the edit, no spinner, no request |
| O9 without PTV rights | pass. s12 with the `:ptv/manage` override off: no PTV tab, no request, "Tallenna" enabled at the first paint |
| sv, en | pass. With the locale switched: "Hämtar uppgifterna i PTV… Du kan spara när de har hämtats.", "Uppgifterna i PTV kunde inte hämtas. …"; "Fetching PTV's data… You can save once it has been fetched.", "PTV's data couldn't be fetched. …" |

Findings:
1. **Playwright request interception crashes the page here.** Soon after a
   `page.route` on `/actions/fetch-ptv-site-review` the headless tab went to
   about:blank, four times, once in view mode with no edit open. Without a
   route, the same steps ran. Hence the backend wrapper.
2. **Others' resets drop the wrapper.** It was found missing twice during
   the run (another agent's `(user/reset)` reloads
   `lipas.backend.ptv.core`), and those attempts' fetches were neither
   slowed nor failed. Checked before each scenario and in the wrapper's log
   after it.
3. **In the dev bundle the first paint of an edit comes 1–31 s after the
   click,** and the fetch often lands meanwhile, so without a hold Save is
   usually enabled at the first paint. Once, unheld (the wrapper had been
   dropped), Save waited 16 s after the first paint.
4. **A late failure of the view's fetch** (O1, `ed966983`) would, before
   this change, have dropped the edit's review; now it is ignored.

Environment left behind: s12 unchanged, 23 revisions, last saved
2026-11-07T09:37:32Z, no error, `(state 600121)` `[[[:address] :ptv]]`. s8
unchanged, 20 revisions. The backend function is unwrapped, the dev
override reset, the REPL scratch namespace removed, the playwright session
`save` closed.
