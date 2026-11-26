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
  read "Estetty" once opened (F2), and only PTV's own UI can publish or
  discard the drafts. Since 2026-11-16 the stand-ins s1b and s2b set the
  same up on other sites, and `run-all!` uses them; read s1b and s2b
  wherever a scenario says s1 or s2.
- **Users:**
  - **PTV manager**: admin, or a user with the `ptv-manager` role for
    Utajärvi (889).
  - **Editor**: may edit Utajärvi's sites but has no PTV rights.
- **Phase:** each scenario names the phase that built it. Phases 1–6 of v2
  are built (2026-10-26).

## Demo sites

| Scenario | Site | What differs |
|---|---|---|
| s1 | 615340 Utajärven pallokenttä | nothing (stuck draft in PTV: use s1b) |
| s1b | 79801 Sangin vanhan koulun liikuntasali | nothing; fi and en published |
| s2 | 89913 Utajärven jäähalli | fi summary and description changed in PTV (stuck draft in PTV: use s2b) |
| s2b | 523483 Utajärven kirkonkylän valaistu kuntorata | fi summary and description changed in PTV |
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
| s18 | 600099 Utajärven koirapuisto | phone, a web page under PTV's own name and English added in PTV; the site has none of them (Q1) |
| s19 | 523489 Rokuan hiihtostadionin valaistu latu | fi summary and email changed in PTV; two phones in PTV, none in Lipas (Q2) |

s12–s14, s16, s18 and s19 export the site to a new location first if it has none.
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
- Expect "{n} liikuntapaikassa on ristiriitoja PTV:n kanssa – ratkaise ne
  ennen synkronointia. [Näytä ne]" (the wording since "Ristiriita").
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

**A5. Sorting by a column** (no write; #258, merged 2026-11-26)

Click "Nimi": A→Z, the arrow on the header; again: Z→A. "Integraatio":
Estetty, Ristiriita, Valmis synkronoitavaksi, Ajan tasalla, not synced,
sync off. "Katselmointi": changes requested, approved with a remark,
approved, not audited. Ties go by name A→Z in either direction. Until a
header is clicked: audit priority, then type.

Sorted by Integraatio, open an "Ajan tasalla" site (s1b) and edit its
summary: the chip turns "Valmis synkronoitavaksi" and the row stays
where it is until closed. "Peru tallentamattomat muutokset".

Result 2026-11-26: pass (Nimi both ways with aria-sort, Integraatio in that
order; s1b stayed at row 16 when edited).

---

## B. Reviewing one site (PTV tool, expanded row)

**B1. Nothing to review** (built)

Open s1. Expect:
- No review panel.
- "Synkronoi nyt" disabled, with the hint "Ajan tasalla".
- No "Näytä muutokset" under "Synkronoi nyt" (since 2026-11-16, P2).

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
- phase 4: listed under "Päivittyy PTV:hen Lipaksesta" instead, as one line;
- since 2026-11-16: the line reads "Kuvaus (FI)" with "Näytä muutos", which
  opens the word diff (struck through: goes from PTV; underlined: comes in)
  and the legend "Yliviivattu poistuu PTV:stä, alleviivattu tulee tilalle."

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

Open s6. On the name card, read "Muuttaa myös liikuntapaikan nimen
Lipaksessa".
1. Take PTV's name and marketing name.
2. Expect "Nimi vaihtuu" next to the site in the table, with the old name in
   its tooltip.
3. Sync.

Verify: the site is renamed in Lipas (database and search) and PTV is
unchanged.

**B7. Language added in Lipas** (built)

Open s7. Expect:
- one line "Lipas lisää englanninkielisen kieliversion: nimi, tiivistelmä,
  kuvaus.";
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
- a site with open choices (e.g. s2) is skipped and listed;
- since 2026-11-16: the confirmation names what each ready site's sync
  removes from PTV, under "Synkronointi poistaa PTV:stä nämä tiedot", e.g.
  "Utajärven kuntoportaat: Ruotsinkielinen kieliversio" (s14), and a
  disconnected service as "Liitetyt palvelut (X)". No list when nothing
  goes.

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

Since 2026-11-16, with both kept as Lipas's, "Synkronoi nyt" asks first:
"Synkronointi poistaa tietoja PTV:stä" naming "Puhelinnumero:" with PTV's
second number struck through (not Lipas's own) and "Muut verkkosivut:"
with the URL struck through.

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

"Kopioi" puts the text on the clipboard. Sync: since 2026-11-16 "Synkronoi
nyt" asks first, "Synkronointi poistaa tietoja PTV:stä" with "Ruotsinkielinen
kieliversio (nimi, tiivistelmä ja kuvaus)" and "Lipas julkaisee vain kielet,
jotka ovat organisaatiolla käytössä ja joille on sekä tiivistelmä että
kuvaus."; confirm with the dialog's "Synkronoi nyt". Verify: PTV has fi only (s14's
location has no English version, and the site no English texts).

**G10. Language enabled for the org, but no texts in Lipas**

Enable sv for the test org and run s14 again. Expect "Ota PTV:n tekstit",
and since 2026-11-16 the reason in the row: "Synkronointi poistaa PTV:stä
ruotsinkielisen kieliversion: nimen, tiivistelmän ja kuvauksen. Siltä
puuttuu Lipaksessa tiivistelmä ja kuvaus, ja kieliversio julkaistaan vain,
kun sillä on sekä tiivistelmä että kuvaus." (the enable hint only while the
org hasn't enabled the language). Take them. Verify: Lipas has the sv texts
and PTV keeps sv.

**G11. Postal address is kept** (s16)

s16 added a PO box (PL 1, 91601) in PTV, and Lipas changed the summary.
Expect:
- no postal-address row; the summary under "Päivittyy PTV:hen Lipaksesta"
  (since 2026-11-16 with "Näytä muutos", B3);
- the panel line "Säilyvät PTV:ssä: aukioloajat, postiosoite." (or just
  "postiosoite" without opening hours).

Sync. Verify in PTV's UI: the postal address is unchanged.

**G12. Postal address PTV's API would reject**

A postal street address with no number and no coordinates. Expect the
information line "Postiosoite poistuu PTV:stä, koska PTV ei hyväksy sitä
rajapinnan kautta – korjaa se PTV:ssä." Since 2026-11-16 "Synkronoi nyt"
asks first, naming "Postiosoite:" with the address struck through and "PTV
ei hyväksy osoitetta rajapinnan kautta. Jos haluat säilyttää sen, korjaa se
PTV:ssä." The sync succeeds.

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
  version unless English texts are written or taken (G10); since
  2026-11-16 the row says why: "Siltä puuttuu Lipaksessa tiivistelmä ja
  kuvaus…";
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
with the summary under "Päivittyy PTV:hen Lipaksesta" (since 2026-11-16 with
"Näytä muutos") and the footer "Valinnat tulevat voimaan, kun synkronoit
palvelun (Synkronoi nyt)." The Palvelut tab's own plan
([`ptv-services-table-browser-scenarios.md`](ptv-services-table-browser-scenarios.md))
supersedes this section.

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

## P. What the user changes, and what a sync removes (2026-11-16)

Final feedback: a user who erased a text wasn't told that the sync removes
it from PTV. Now (2026-11-16):
- the review panel also shows the edit's own changes, before they are
  saved: a field where the saved site agrees with PTV but the form doesn't
  is a "Muutettu Lipaksessa" row (Lipas's change, no choice), in the PTV
  tool and in the site form;
- a text's line under "Päivittyy PTV:hen Lipaksesta" opens a word diff
  ("Näytä muutos");
- "Näytä muutokset" under "Synkronoi nyt" opens the panel and scrolls to it;
- "Synkronoi nyt" asks first when the sync removes something from PTV: a
  language version, a value or text PTV has that the edit leaves empty, a
  value only PTV has, a service the location is disconnected from. With
  open choices, the gate names them ("Lisäksi synkronointi poistaa PTV:stä
  nämä tiedot:"), one dialog either way;
- a language row says why the version goes: a text Lipas lacks, or the
  language not enabled for the org.

The same on the Palvelut tab: its plan's section L.

**P1. An edit shows in the panel at once** (no write)

Open a site that reads "Ajan tasalla" (e.g. s1b), and change its fi
description.

Expect:
- the panel appears: "Synkronointi päivittää PTV:hen 1 tietoa."; the chip
  "Valmis synkronoitavaksi";
- expanded: no "Valitse ennen synkronointia"; "Päivittyy PTV:hen
  Lipaksesta (1)" with "Kuvaus (FI) · Näytä muutos", whose diff strikes
  through what goes and underlines what comes, with the legend; "Ajan
  tasalla:" no longer lists "kuvaus (fi)"; ⑤ "Viedään PTV:hen: Kuvaus (FI)";
- "Synkronoi nyt" asks nothing (nothing is removed).

Undo the typing: the panel goes and the chip is "Ajan tasalla" again.

**P2. "Näytä muutokset"** (no write)

From P1, with the panel collapsed and out of sight: "Näytä muutokset" under
"Synkronoi nyt". Expect the panel open and scrolled into view. A site
without anything to show has no such button (B1).

**P3. Erasing one English text removes the English version** (no write
unless confirmed)

On a site whose English version Lipas has synced (s1b, or s7 after B7),
erase the English summary. (A site whose English is only PTV's, such as Kylätalo
Katajiston liikuntasali, shows the same row without an edit, its reason
"…puuttuu Lipaksessa tiivistelmä ja kuvaus…".)

Expect:
- the bar "Synkronointi poistaa PTV:stä englanninkielisen kieliversion.";
- the row "Englanti (EN) · Poistuu PTV:stä": "Synkronointi poistaa PTV:stä
  englanninkielisen kieliversion: nimen, tiivistelmän ja kuvauksen. Siltä
  puuttuu Lipaksessa tiivistelmä, ja kieliversio julkaistaan vain, kun
  sillä on sekä tiivistelmä että kuvaus.", "Näytä poistuvat tekstit", "Ota
  PTV:n tekstit";
- "Ajan tasalla:" lists no English text.

1. "Synkronoi nyt". Expect the dialog "Synkronointi poistaa tietoja
   PTV:stä", "Jos synkronoit, nämä tiedot poistuvat PTV:stä:",
   "Englanninkielinen kieliversio (nimi, tiivistelmä ja kuvaus)" with "Lipas
   julkaisee vain kielet, jotka ovat organisaatiolla käytössä ja joille on
   sekä tiivistelmä että kuvaus.", and the buttons "Peruuta", "Näytä
   muutokset", "Synkronoi nyt".
2. "Näytä muutokset": the dialog closes, the panel opens and scrolls into
   view (at its top; the row may be further down), nothing is sent.
3. "Ota PTV:n tekstit": PTV's English texts are back in the form, the row
   goes (and the panel with it when nothing else differs).

Optional write: erase again and confirm. Verify: PTV has fi only. Reset the
site's scenario with `run-all!`.

**P4. A value only PTV has** (s6; no write unless confirmed)

s6's marketing name was added in PTV. Choose "Pidä Lipaksen versio" on it,
then "Synkronoi nyt". Expect the removal dialog naming "Markkinointinimi:"
with PTV's name struck through. "Peruuta".

**P5. Disconnecting a service** (no write unless confirmed)

On a site that reads "Ajan tasalla" with one service (e.g. s1b):
1. "Muuta palvelua", add another service: the panel's line "Liitetyt
   palvelut PTV:ssä: lisätään X". "Synkronoi nyt" would ask nothing:
   nothing is removed.
2. "Muuta palvelua", remove the original one: "…lisätään X; poistetaan
   Y".
3. "Synkronoi nyt". Expect the removal dialog with "Liitetyt palvelut:" and
   Y struck through. "Peruuta".

A first link (M4) disconnects nothing: the first sync keeps PTV's
connections, and no removal is named.

PTV's other values of a one-slot field are named too, when PTV has more
of them than the one Lipas sends (G2's second phone number). One value
swapped for Lipas's is a change, not a removal, as an edited text is.

**P6. Removals with open choices: one dialog** (no write)

s10 (phone changed in PTV, undecided): disconnect a service as in P5, then
"Synkronoi nyt". Expect one dialog, "PTV:ssä on muutoksia, joita et ole
käynyt läpi", the phone line, then "Lisäksi synkronointi poistaa PTV:stä
nämä tiedot:" with "Liitetyt palvelut:" and the service struck through, and
the buttons "Käy muutokset läpi", "Synkronoi ja korvaa", "Peruuta". A
language version the gate already lists isn't repeated. "Peruuta".

**P7. Many sites at once** (no write)

As E4: the confirmation of "Synkronoi kaikki valmiit" lists what each ready
site's sync removes. "Peruuta".

**P8. The site form shows the form's edits** (no write)

Open a site that reads "Ajan tasalla" on the map, "Muokkaa", change its
phone number in Perustiedot, then the PTV tab.

Expect:
- the panel "Synkronointi päivittää PTV:hen 1 tietoa.", with
  "Puhelinnumero: vanha → uusi" under "Päivittyy PTV:hen Lipaksesta";
- no amber dot on the tab (nothing to decide);
- in view mode (no edit) no such row.

Saving the form doesn't ask about removals yet (open): the panel is where
the site form says what goes.

**P9. Discarding the unsaved changes** (no write)

s1b, PTV tool row: no "Peru tallentamattomat muutokset" while nothing is
edited. Edit the summary: the button appears under "Näytä muutokset". It
asks "Perutaanko tallentamattomat muutokset?"; "Peruuta" keeps the edit,
"Peru muutokset" puts the saved text back and the button goes.

s2b: "Ota PTV:n versio" on one row ("1 / 2 valittu") shows the button too;
discarding forgets the choice ("0 / 2 valittu"). The same discards a
changed service, link, sync switch or a value taken from PTV. The site
form has its own discard and gets no button.

## Q. Taking all of PTV's changes (2026-11-26)

Tester feedback: "Ota kaikki PTV:n muutokset" left a web page PTV keeps
under its own name unchosen, though the site had none. Now
(ptv-review/bulk-take-rows):
- the obvious choices are made: PTV's only web page goes in as the web page
  while PTV names none the web page and the edit has none;
- a language version PTV has that the sync would remove is kept (its texts
  copied into Lipas) when the org has the language enabled, and goes when
  it hasn't, as the org's languages say; not one the user marked to go;
- what has several options (PTV has two phones; a web page while PTV names
  another the web page) is left to the user: a line above the buttons says
  so before the click ("Kaikkea ei voi ottaa kerralla, koska PTV:ssä on
  useampi vaihtoehto. Valitse itse: puhelinnumero."), and after it the
  focus goes to the first row still open;
- "Pidä Lipaksen versio kaikissa" also lets go of a language version taken.

The same panel serves the PTV tool's Liikuntapaikat and Palvelut tabs and
the site form's PTV tab; the Käyttöönotto wizard's "Ota kaikki PTV:n
muutokset" (export blocked) takes the same rows.

**Q1. Nothing left to pick** (s18, no write)

PTV tool, Liikuntapaikat, "Utajärven koirapuisto" → "Näytä muutokset".

Expect: rows Puhelinnumero, Muut verkkosivut ("Ota verkkosivuksi", "Ota
varauslinkiksi") and Englanti (EN) ("Ota PTV:n tekstit"); no "Kaikkea ei
voi…" line. "Ota kaikki PTV:n muutokset": "2 / 2 valittu"; the edit holds
the phone "+358 401234567", the web page https://www.utajarvi.fi/koirapuisto
and the English name and texts; ⑤ "Kopioidaan Lipakseen: Puhelinnumero,
Muut verkkosivut" and "… Englanti (EN)". Discard.

**Q2. A pick left** (s19, no write)

"Rokuan hiihtostadionin valaistu latu" → "Näytä muutokset".

Expect: "0 / 3 valittu" and "Kaikkea ei voi ottaa kerralla, koska PTV:ssä
on useampi vaihtoehto. Valitse itse: puhelinnumero." "Ota kaikki PTV:n
muutokset": the summary and email are taken ("2 / 3 valittu"), Puhelinnumero
stays "Valitse ennen synkronointia", and the focus is on its first button.
Discard.

**Q3. Palvelut** (no write)

Palvelut, Pallokentät (p3: its fi summary changed in PTV), tab EN: empty
the Tiivistelmä. Expect rows Tiivistelmä (FI) and Englanti (EN) (it lacks
a summary); "Ota kaikki PTV:n muutokset" takes PTV's fi summary and puts
the English summary back, and the English row goes. Discard.

**Q4. Site form** (no write)

Map, s18 → "Muokkaa" → PTV. As Q1, the values going into the form's edit.
"Peruuta" → "Kyllä": nothing saved.

**Q5. A language the org hasn't enabled** (s14, no write)

s14's Swedish version (Utajärvi publishes fi+en): taking all leaves it,
and the sync removes it, as before.

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

## Results: run of 2026-11-16 (during the build)

Local Lipas against the PTV test env, Utajärvi only, as admin, in
`playwright-cli` (headless Chrome with `.playwright/cli.config.json` and the
MessageChannel render patch). Nothing was synced: every dialog was
cancelled, every edit discarded with the session.

| Scenario | Site | Result |
|---|---|---|
| P1 | 605450 | pass: the panel appeared on typing ("Synkronointi päivittää PTV:hen 1 tietoa."), the chip turned "Valmis synkronoitavaksi", "Kuvaus (FI) · Näytä muutos" opened the diff ("ja tasapainoa", "kesäisin" underlined, "ympäri vuoden" struck through, the legend), "Ajan tasalla:" without "kuvaus (fi)" |
| P2 | 605450 | pass: the button opened the panel and scrolled to it; absent while the site read "Ajan tasalla" |
| P3 (dialog) | Kylätalo Katajiston liikuntasali | pass: with no English texts in Lipas, the row read "…Siltä puuttuu Lipaksessa tiivistelmä ja kuvaus…"; "Synkronoi nyt" opened the removal dialog with the English version and its reason; the dialog's "Näytä muutokset" opened the panel at the row, nothing sent |
| P5 | 605450 | pass: swapping "Lähiliikunta ja liikuntapuistot" for "Jäähallit": "Synkronointi päivittää PTV:hen 1 tietoa.", then the dialog "Liitetyt palvelut: ~~Lähiliikunta ja liikuntapuistot~~"; "Peruuta" |
| P7 | – | pass: "Synkronoi kaikki valmiit (6)" listed "Kylätalo Katajiston liikuntasali: Englanninkielinen kieliversio" and "Utajärven kuntoportaat: Ruotsinkielinen kieliversio"; "Peruuta" |

Not run in the browser: P3's erasing and "Ota PTV:n tekstit" on a site (run
on a service instead, Palvelut L1), P4, P6, P8.

Added after the regression run:

| Scenario | Site | Result |
|---|---|---|
| P9 | s1b (79801), s2b (523483) | pass: no button before the edit; the dialog's "Peruuta" kept the edited summary, "Peru muutokset" put the saved one back and hid the button; on s2b the button appeared after "Ota PTV:n versio" ("1 / 2 valittu") and discarding went back to "0 / 2 valittu" |

## Results: regression run of 2026-11-16 (sites)

Liikuntapaikat tab and site form after the day's changes (unsaved edits in
the panel, "Näytä muutos", "Näytä muutokset", the removal question, the
language row's reason). Local Lipas against the PTV test env, Utajärvi
only, as admin, in `playwright-cli` (headless Chrome, session `sites`,
`.playwright/cli.config.json`, the MessageChannel render patch). s1 and s2
were replaced by s1b (79801) and s2b (523483), their drafts in PTV being
stuck. 605450 was left alone (the Palvelut run used it). Where a scenario
says "no write", "Synkronoi nyt" and Save were clicked with the browser set
offline (Playwright `setOffline`), so a missing question would have shown as
a failed request, not a write; every such case was checked with `state`.
PTV writes took 2–6 s. Screenshots: `/private/tmp/claude-501/-Users-tipo-lipas-lipas/2c75db1c-416b-448b-8131-504617e00596/scratchpad/pw-sites/`.

| Scenario | Site | Result |
|---|---|---|
| A1 | all | pass: "Ristiriita" listed exactly s2b, s4, s5, s6, s9, s10, s11, s12 (8), each with the amber chip |
| A3 | all | pass: s1b "Ajan tasalla"; s3, s7, s13, s14, s16 "Valmis synkronoitavaksi"; s2b, s4, s5, s6, s9, s10, s11, s12 "Ristiriita"; s1 "Estetty"; s8, s15 faded "PTV"; s4 turned blue once its row was decided, without a sync. s2 (89913) read "Valmis synkronoitavaksi" unopened |
| C1 | s2b | pass: bar collapsed, "Synkronoi nyt": "PTV:SSÄ ON MUUTOKSIA, JOITA ET OLE KÄYNYT LÄPI", "Tiivistelmä (FI)", "Kuvaus (FI)", buttons Peruuta / Synkronoi ja korvaa / Käy muutokset läpi (primary) |
| C4 | s2b | pass: "Peruuta" closed it, the row unchanged, nothing sent |
| C2 | s2b | pass: "Käy muutokset läpi" opened the panel scrolled into view, focus on "Pidä Lipaksen versio: Tiivistelmä (FI)" |
| C3 | s2b | pass: "Synkronoi ja korvaa", "Tallennus onnistui", the row "Ajan tasalla" with no "Näytä muutokset". `(state 523483)`: PTV has Lipas's summary and description, `:review []`. Reset with `s2b-ptv-texts!` |
| B2 | s2b | pass: bar "PTV:ssä on muutettu 2 tietoa – valitse, mitkä jäävät voimaan." and the dates line; rows Tiivistelmä (FI), Kuvaus (FI) "Muutettu PTV:ssä", "Valitse ennen synkronointia", marks only on PTV's words; "Ota PTV:n versio" on the summary: field holds PTV's text, the row collapsed, [Muuta] shows "Lomakkeessa" on the PTV card, "Kopioidaan Lipakseen: Tiivistelmä (FI)"; "Pidä Lipaksen versio" on the description: "Viedään PTV:hen: Kuvaus (FI)"; no "Muutettu Lipaksessa" row for the taken text; "Synkronoi nyt" asked nothing. `state`: both sides PTV's summary + Lipas's description, `:review []`; the row "Ajan tasalla" |
| B1 | s1b | pass: no panel, "Synkronoi nyt" disabled with "Ajan tasalla", no "Näytä muutokset" |
| B3 | s3 | pass: "Päivittyy PTV:hen Lipaksesta (1)", "Kuvaus (FI) · Näytä muutos", diff with the added words underlined and the legend "Yliviivattu poistuu PTV:stä, alleviivattu tulee tilalle."; "Synkronoi nyt" asked nothing; `state`: PTV has Lipas's description |
| B4 | s4 | pass: "Muutettu molemmissa", the merge hint; typing a merged summary gave "Lomakkeessa on muokattu teksti" with the text and "Vie muokattu versio PTV:hen" (no separate "Muutettu Lipaksessa" row); confirmed, 1/1, chip blue, "Viedään PTV:hen muokattuna: Tiivistelmä (FI)"; sync asked nothing; `state`: merged text on both sides |
| P4 | s6 | pass: "Pidä Lipaksen versio" on Markkinointinimi with the name still open: the gate, the name line, then "Lisäksi synkronointi poistaa PTV:stä nämä tiedot:" "Markkinointinimi: ~~Utajärven kuntolatu~~" (one dialog). With the name kept too: "Synkronointi poistaa tietoja PTV:stä", "Markkinointinimi: ~~Utajärven kuntolatu~~", Peruuta / Näytä muutokset / Synkronoi nyt. Peruuta. Run on the fresh s6 before B6 |
| B6 | s6 | pass: caption "Muuttaa myös liikuntapaikan nimen Lipaksessa"; both taken: "Nimi vaihtuu" in the table, tooltip "Synkronointi vaihtaa liikuntapaikan nimen Lipaksessa. Nykyinen nimi: Utajärven kirkonkylän valaistu latu"; sync asked nothing; DB and search "Kirkonkylän valaistu latu" / "Utajärven kuntolatu", PTV unchanged. Reset with `s6-names!` |
| B7 | s7 | pass: "Lipas lisää englanninkielisen kieliversion: nimi, tiivistelmä, kuvaus.", no choice; sync asked nothing; PTV languages en + fi |
| B8 | s9 | pass: "Ota kaikki PTV:n muutokset" 3/3 (name, description, connections) and left the summary (Lipas's change) under "Päivittyy PTV:hen Lipaksesta"; "Kumoa valinnat" 0/3; "Pidä Lipaksen versio kaikissa" 3/3; rows collapsed "✓ … [Muuta]", [Muuta] reopened with "Lomakkeessa". Not synced |
| D1 | s9 | pass: dot on "PTV" in view and edit mode; bar collapsed; stacked cards; footer "Valinnat tulevat voimaan, kun tallennat liikuntapaikan. Tallennus synkronoi lopputuloksen PTV:hen." |
| D2 | s9 | pass: Perustamisvuosi 1988 → 1989, Save: "PTV:SSÄ ON MUUTOKSIA…", "Nimi (FI): PTV:n ”Utajärven liikuntasali” korvautuu…", "Kuvaus (FI)", "Liitetyt palvelut: PTV:n versio korvautuu Lipaksen versiolla (poistetaan Soveltava liikunta)", Peruuta / Tallenna ja korvaa / Käy muutokset läpi; "Käy muutokset läpi" opened the PTV tab, panel open, focus on "Pidä Lipaksen versio: Nimi (FI)" |
| D3 | s9 | pass (s9 has no PTV summary to take, so: PTV's description, Lipas's name, PTV's connections): the description field held PTV's text at once; year put back to 1988; Save asked nothing, "Tallennetaan ja synkronoidaan PTV:hen…", "Tallennus onnistui". `state`: texts and services as chosen on both sides, `:review []`, year 1988. Reset with `s9-everything!` |
| G2 | s11 | pass: "Puhelinnumero" needs a choice, PTV card lists both numbers with "Ota tämä", Lipas card "PTV:n muut arvot poistuvat.", "Ota tämä" on Lipas's number pressed "Pidä Lipaksen versio" too; "Muut verkkosivut" with "Ota verkkosivuksi" / "Ota varauslinkiksi". Both kept: "Synkronoi nyt" asked "Synkronointi poistaa tietoja PTV:stä", "Muut verkkosivut: ~~https://www.utajarvi.fi/liikunta~~", the second phone not named (as P5 notes); confirmed. `state`: one phone, one web page. Reset with `s11-second-phone-and-page!` |
| G9 | s14 | pass: row "Ruotsi (SV) · Poistuu PTV:stä", "Näytä poistuvat tekstit" with name, summary, description, each with a copy button; the hint "Ruotsi ei ole käytössä organisaatiollanne Lipaksessa… (lipasinfo@jyu.fi)…"; bar "Synkronointi poistaa PTV:stä ruotsinkielisen kieliversion."; dialog "Ruotsinkielinen kieliversio (nimi, tiivistelmä ja kuvaus)" + "Lipas julkaisee vain kielet…"; confirmed. PTV has fi only: see finding 1. Reset with `s14-sv-added!` |
| G11 | s16 | pass: no postal-address row, "Tiivistelmä (FI) · Näytä muutos" (" Utajärven keskustassa" underlined), "Säilyvät PTV:ssä: postiosoite."; sync asked nothing; PTV still has "PL 1, 91601". Reset with `s16-postal-address!` |
| H3 | s15 | pass, no sync: "Liitä tähän palvelupaikkaan" gave "Palvelupaikan tiedot PTV:ssä eroavat Lipaksesta 2 kohdassa… Lisäksi synkronointi poistaa englanninkielisen kieliversion."; fi texts "Eroaa" with no origin; "Englanti (EN) · Poistuu PTV:stä" with "Siltä puuttuu Lipaksessa tiivistelmä ja kuvaus, ja kieliversio julkaistaan vain, kun sillä on sekä tiivistelmä että kuvaus.", "Ota PTV:n tekstit"; no "Säilyvät PTV:ssä" postal-address line (not checked whether the location has one). DB unchanged |
| M1 | s5 | pass: as listed in M1 (one row, Pallokentät and Liikuntaneuvonta marked, the caption, no "Pidä liitos"/"Poista liitos"); "Ota PTV:n versio" collapsed it, chip blue, "Kopioidaan Lipakseen: Liitetyt palvelut", field Liikuntaneuvonta + Yleisurheilu; "Synkronoi nyt" asked nothing (no removal named for Pallokentät). `state`: yleisurheilu + liikuntaneuvonta on both sides. Reset with `s5-connections!` |
| M3 | s5 | pass: own set "Lomakkeessa on muokattu palveluvalinta" (Yleisurheilukentät ja -paikat), "Vie muokattu versio PTV:hen"; Save: "Liitetyt palvelut: PTV:n versio korvautuu muokatulla versiolla (poistetaan Liikuntaneuvonta)", Peruuta / Tallenna ja korvaa / Käy muutokset läpi. Peruuta, edit discarded; `state` unchanged |
| M4 | s8 | pass: link put PTV's two services in the field; "…eroavat Lipaksesta 2 kohdassa…"; no services row in ②; "PTV:ssä liitetyt palvelut säilyvät ja kopioidaan Lipakseen: Lähiliikunta ja liikuntapuistot, Puistojumppa" |
| M6 | s8 | pass: own summary unconfirmed + PTV's description, integration switched on (the export button is disabled until then), "Vie palvelutietovarantoon": "LIPAS JA PTV EROAVAT", "Näitä Lipaksen ja PTV:n eroja…", "Tiivistelmä (FI)", Peruuta / Synkronoi ja korvaa / Käy erot läpi; no removal section (a first link disconnects nothing). Peruuta; DB unchanged |
| M7 | s12, s10 | pass: site form (s12) "Käyntiosoite: PTV:n ”Kirkkotie 21, 91600 UTAJÄRVI” korvautuu Lipaksen tiedolla ”Kirkkotie 19, 91600 Utajärvi”", "Tallenna ja korvaa"; tool (s10) "Puhelinnumero: PTV:n ”+358 401234567” korvautuu Lipaksen tiedolla ”+358 858755700”", "Synkronoi ja korvaa". Both Peruuta, nothing saved |
| P1 | s1b | pass: typing in the fi description: "Synkronointi päivittää PTV:hen 1 tietoa.", chip blue, "Päivittyy PTV:hen Lipaksesta (1)", "Kuvaus (FI) · Näytä muutos" (", salibandya" underlined, ". Vuorot varataan kyläyhdistykseltä" struck through, the legend), "Ajan tasalla:" without "kuvaus (fi)", "Viedään PTV:hen: Kuvaus (FI)", no "Valitse ennen synkronointia"; "Synkronoi nyt" asked nothing (offline: the request went out and failed). Typing back: panel gone, "Ajan tasalla" |
| P2 | s1b | pass: with the bar under the sticky header, "Näytä muutokset" under "Synkronoi nyt" opened the panel and scrolled it into view; absent while "Ajan tasalla" |
| P5 | s1b | pass: "Jäähallit" added: "Liitetyt palvelut PTV:ssä: lisätään Jäähallit", "Synkronoi nyt" asked nothing; original removed: "…lisätään Jäähallit; poistetaan Kuntoilukeskukset ja liikuntasalit"; dialog "Liitetyt palvelut: ~~Kuntoilukeskukset ja liikuntasalit~~", Peruuta |
| P3 | s1b | pass: EN summary erased: bar "Synkronointi poistaa PTV:stä englanninkielisen kieliversion.", "Englanti (EN) · Poistuu PTV:stä" with "…Siltä puuttuu Lipaksessa tiivistelmä, ja kieliversio julkaistaan vain, kun sillä on sekä tiivistelmä että kuvaus.", "Näytä poistuvat tekstit", "Ota PTV:n tekstit"; "Ajan tasalla:" no English text. Dialog as P3 step 1 with all three buttons; its "Näytä muutokset" closed it and showed the panel, nothing sent (finding 2); "Ota PTV:n tekstit" put the text back, panel gone. Optional write: erased and confirmed, "Tallennus onnistui"; `state`: PTV languages fi only. Reset with `s1b-in-sync!` |
| P6 | s10 | pass: phone open, service swapped (Jäähallit in, Lähiliikunta ja liikuntapuistot out): one dialog, "PTV:ssä on muutoksia, joita et ole käynyt läpi", the phone line, "Lisäksi synkronointi poistaa PTV:stä nämä tiedot:", "Liitetyt palvelut: ~~Lähiliikunta ja liikuntapuistot~~", Peruuta / Synkronoi ja korvaa / Käy muutokset läpi. Peruuta |
| P7 | – | pass: after a reload, "Synkronoi kaikki valmiit (4)": "Synkronoidaanko 4 liikuntapaikkaa?", "Synkronointi poistaa PTV:stä nämä tiedot": "Kylätalo Katajiston liikuntasali: Englanninkielinen kieliversio", "Utajärven kuntoportaat: Ruotsinkielinen kieliversio", then the skipped sites with conflicts. Peruuta |
| P8 | s1b | pass: view mode no row; edit, Perustiedot phone "08 5875 1234": PTV tab "Synkronointi päivittää PTV:hen 1 tietoa.", "Puhelinnumero: (ei arvoa) → +358 858751234" under "Päivittyy PTV:hen Lipaksesta", no dot on the tab; edit discarded |
| L5 | – | pass for the review: no errors from the review, the panel or the dialogs. See finding 3 |

Findings:
1. **G9's "Verify: PTV has fi+en only" can't hold for s14.** s14 has no
   English texts in Lipas, so the sync left PTV with fi only (`(state
   606904)` before the reset: `:languages ["fi"]`). Correct behaviour;
   the doc's expectation reads "the org's languages" as the site's. Not
   related to today's changes. Screenshot: `/private/tmp/claude-501/-Users-tipo-lipas-lipas/2c75db1c-416b-448b-8131-504617e00596/scratchpad/pw-sites/g9-removal-dialog.png`.
2. **P3's "Näytä muutokset" in the removal dialog** opened the panel and
   scrolled to it, but the language row sat at the bottom edge of the
   view (y 644–672 of 720) under the bar, and the focus went back to
   "Synkronoi nyt" (the dialog's own focus restore). The gate's "Käy
   muutokset läpi" puts the focus on the first open row (C2); a language
   row has no choice button, so there's nothing to focus there. Expected
   "the panel opens at the row"; seen: the panel's top. Minor, today's
   change. Screenshot: `/private/tmp/claude-501/-Users-tipo-lipas-lipas/2c75db1c-416b-448b-8131-504617e00596/scratchpad/pw-sites/p3-shown.png`.
3. **Console: MUI Autocomplete "getOptionLabel … returned object (null)"**,
   12 times, whenever the services selector ("Muuta palvelua") listed its
   options: three services of the Utajärvi test org have no name
   (026ee5c3-fe81-401c-bf9b-3196868ac005,
   11cc13d3-1a0e-4aeb-afe0-f12ffb4e5aec,
   400bd064-7d3d-4430-871e-075c58b61249) and show as blank options at the
   top of the list. Not in the local `ptv_service` mirror. Older than
   today's changes. The other console lines: the two failed requests of the
   offline checks (tooling), the MUI GridLegacy deprecation and
   ":focus-visible" warnings. Log: `/private/tmp/claude-501/-Users-tipo-lipas-lipas/2c75db1c-416b-448b-8131-504617e00596/scratchpad/pw-sites/console-errors.log`.
4. **"Synkronoi kaikki valmiit (n)" counts a site whose only change is an
   unsaved edit in the tool**: during P1 it went from 6 to 7 while s1b's
   description was being typed. Consistent with the chip turning blue;
   the bulk sync would send that edit too. Not checked further.
5. **Wording newer than the doc** (not bugs): A2's banner reads "8
   liikuntapaikassa on ristiriitoja PTV:n kanssa – ratkaise ne ennen
   synkronointia."; B6's caption "Muuttaa myös liikuntapaikan nimen
   Lipaksessa"; B7's line "Lipas lisää englanninkielisen kieliversion:
   nimi, tiivistelmä, kuvaus."; P5's line "lisätään X; poistetaan Y"; B4's
   own text counts as decided only after "Vie muokattu versio PTV:hen" (as
   in M3).

Not run: the site form's save without a removal warning (known, open).

Environment left behind:
- Synced and reset: s2b (C3, B2) with `s2b-ptv-texts!`; s3 (B3)
  `s3-lipas-texts!`; s4 (B4) `s4-conflict!`; s6 (B6) `s6-names!`; s7 (B7)
  `s7-new-language!`; s11 (G2) `s11-second-phone-and-page!`; s14 (G9)
  `s14-sv-added!`; s16 (G11) `s16-postal-address!`; s5 (M1)
  `s5-connections!`; s9 (D3) `s9-everything!`; s1b (P3) `s1b-in-sync!`.
  `(user.ptv-drift-scenarios/report)` afterwards matched the start for
  every site above; each reset added PTV and Lipas revisions as usual.
- Not written: s8, s10, s12, s15 (links and edits stayed in the browser);
  `state` unchanged. 605450 untouched (it read `[]` at the end, from the
  Palvelut run).
- The playwright session `sites` was closed.

## Results: take all, run of 2026-11-26

Local, admin, Utajärvi, playwright (headless, MessageChannel render patch).
Q1–Q4 pass as written:
- Q1: before, no picks line; after, "2 / 2 valittu", the edit holding the
  phone, the web page and the English texts; no focus move (nothing open).
- Q2: the picks line before and after; the summary and email taken, the
  phone open, the focus on its "Pidä Lipaksen versio" (the row's first
  button).
- Q3: before, Tiivistelmä (FI) and Englanti (EN); after, "1 / 1 valittu",
  the English summary back in the form.
- Q4: the same values in the form's edit; cancelled, nothing saved.
Q5 is covered by the unit tests (ptv-review-fields-test), not run.

Environment left behind:
- s18 (600099) and s19 (523489) set up by their scenario functions; each
  run's edits discarded. 523489's PTV location existed before (sourceId
  without a timestamp) and the setup overwrote its texts and contacts.
- 523480 Niskan latu (removed in Lipas) had its PTV location archived by a
  setup run that picked it by mistake; it had been waiting to be archived.
- 600983 Pälli Frisbeegolf (not a PTV type) got a location by mistake; it
  was archived and the site unlinked again.
- s8 (506497) was linked by mistake and reset with `s8-first-link!`
  (phone and email put back first).
