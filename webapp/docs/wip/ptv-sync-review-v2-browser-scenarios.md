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
  - `(user.ptv-drift-scenarios/run-all!)` recreates scenarios s1–s9.
  - s10 onwards are added in phase 3.
- **Slow PTV:** writes take ~140 s on PTV's side since 2026-10-22. Wait for
  the sync to finish before checking.
- **Known blocker:** test site 615340 (s1) has an unpublished draft in PTV.
  Publish or discard it in the PTV test UI before using s1.
- **Users:**
  - **PTV manager**: admin, or a user with the `ptv-manager` role for
    Utajärvi (889).
  - **Editor**: may edit Utajärvi's sites but has no PTV rights.
- **Phase:** each scenario names the phase it needs. Phases 1–4 of #272 and
  phase 1 of v2 are built. Run the rest as their phases land.

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
| s10 | (phase 3) | phone changed in PTV |
| s11 | (phase 3) | a second phone and an extra web page added in PTV |
| s12 | (phase 3) | visiting address changed in PTV, re-geocoded |
| s13 | (phase 3) | map point moved in PTV over 50 m |
| s14 | (phase 3) | Swedish version added in PTV (the org has fi+en) |
| s15 | (phase 3) | first link to a bilingual PTV-made location |
| s16 | (phase 3) | postal address added in PTV |

---

## A. Finding the sites that need attention (PTV tool, Liikuntapaikat tab)

**A1. Which sites have changes in PTV** (built)
1. As a PTV manager, open the map, then PTV, choose "Utajärven kunta", then the
   Liikuntapaikat tab.
2. Click the "Muutettu PTV:ssä" filter chip.

Expect:
- Exactly the sites with PTV-side changes are listed: s2, s4, s5, s6, s9.
- Each row shows the red PTV chip.
- The count on the chip matches the list.

**A2. Banner over the table** (phase 4)

With the sites of A1 present:
- Expect "{n} liikuntapaikassa on PTV:ssä tehtyjä muutoksia, jotka odottavat
  valintaa. [Näytä ne]".
- "Näytä ne" applies the same filter as A1.

**A3. Status chips tell the states apart** (phase 4)

| Site | Chip |
|---|---|
| s1 | "Ajan tasalla" (green) |
| s3, s7 | "Valmis synkronoitavaksi" (blue) |
| s2, s4, s5, s6, s9 | "Odottaa valintaa" (amber) |
| a site with a sync error or a link blocker | "Estetty" (red) |

Also: after every choice in s2 is made, its chip turns blue without a sync.

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
3. Click "Kumoa valinnat". Expect everything back as saved and the choices
   open again.
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

After D3's choice, "Kumoa valinnat". Expect: the edit is back to the saved
texts and the form fields follow.

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

Edit s1 in PTV's UI and save without publishing (or use 615340 while it's
stuck). Expect:
- "PTV:ssä on julkaisemattomia muutoksia. Julkaise tai peru ne PTV:ssä
  ennen synkronointia." with "Avaa PTV:ssä";
- the sync is blocked.

The same is shown in the site form. A sync attempt that hits "You cannot
update entity with status Modified" shows this notice, not "Synkronointi
epäonnistui".

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

Expect:
- the PTV card lists both numbers, each with "Ota tämä";
- the Lipas card says "PTV:n muut numerot poistuvat".

Keep Lipas's. Verify: PTV has one number. Repeat for the extra web page.

**G3. Web pages without names are not a difference**

On a site last synced before #269 (unnamed pages in PTV, same URLs), expect
no web-page row.

**G4. Address changed in PTV** (s12)

Expect the row "Käyntiosoite":
- PTV's street and number joined ("Kotkantie 41"), with postal code and
  office.

Take it. Verify: Lipas has the address, postal code and postal office.

**G5. Swedish street name and extra text** (s15)

The address row says that the Swedish street name and the extra text ("1.
krs.") are replaced by the sync.

**G6. Map point moved** (s13)

Expect:
- the information line "PTV:n koordinaatit ({n} m päässä) korvautuvat
  Lipaksen sijainnilla";
- no choice and no take button.

Sync. Verify: PTV's point is back at Lipas's location.

**G7. Point within 50 m**

Move the point 20 m in PTV. Expect no row.

**G8. Municipality differs**

Expect information only, with a hint to check the location; it can't be
taken.

**G9. Language PTV has, the org hasn't enabled** (s14)

Expect:
- the row "Ruotsi (SV) — Poistuu PTV:stä", listing what goes;
- "Näytä poistuvat tekstit", each text with "Kopioi";
- "pyydä Lipaksen tukea ottamaan ruotsi käyttöön (lipasinfo@jyu.fi)";
- the bar ends with "… ja poistaa ruotsinkielisen kieliversion";
- no choice needed.

"Kopioi" puts the text on the clipboard. Sync. Verify: PTV has fi+en only.

**G10. Language enabled for the org, but no texts in Lipas**

Enable sv for the test org and run s14 again. Expect "Ota PTV:n tekstit".
Take them. Verify: Lipas has the sv texts and PTV keeps sv.

**G11. Postal address is kept** (s16)

Add a postal address in PTV's UI. Expect:
- no row;
- the panel line "Säilyvät PTV:ssä: aukioloajat, postiosoite".

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

On the first link, expect:
- the address row (G5);
- the language row for sv if the org lacks sv (G9);
- the postal address kept (G11).

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

**K1. A Lipas-created service changed in PTV**

Edit a Lipas-created service's description in PTV's UI. Expect the same panel
on the service, with "Muutettu PTV:ssä" and Keep/Take. Sync. Verify: PTV and
Lipas agree.

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
