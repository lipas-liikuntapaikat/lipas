# PTV Palvelut table: browser scenarios

These scenarios check the Palvelut tab of the PTV tool, rebuilt as one table
(branch `feat/ptv-services-table`, on top of `feat/ptv-drift-review`). They are
run in the browser the way a PTV manager works, and every write is checked
where it landed: the service in PTV, Lipas's mirror of it (`ptv_service`) and,
for links, the sites. Companion to
[`ptv-sync-review-v2-browser-scenarios.md`](ptv-sync-review-v2-browser-scenarios.md),
whose section K covered the old Palvelut accordions.

What changed and is under test:
- One table: Integraatio, Katselmointi, Nimi, Luotu, Palvelupaikat,
  Liikuntapaikat; the old service panel opens in the row.
- Filter bar shared with Liikuntapaikat: search and one row of status chips,
  "Ei palvelupaikkoja" among them.
- Services that don't concern Lipas are listed below the table on request;
  a search says when it matches them.
- Counts of linked sites read what is saved (`:ptv-saved`), not the default
  links the tool makes in memory on load.
- The Liikuntapaikat count leads to the Liikuntapaikat tab, filtered to that
  service's sites (phase 2).
- The row status, review and audit verdict come from
  `lipas.data.ptv-services/service-state`, computed once for the row and the
  open panel.

## Ground rules

- **Where:** local Lipas (https://localhost) against the PTV test env,
  **Utajärvi only** (PTV org `3d1759a2-e47a-4947-9a31-cab1c1e2512b`, city
  889). Liminka and Raahe hold training data: don't select them in the tool.
- **User:** admin, a PTV manager with audit rights. I3 also needs a user
  who may edit Utajärvi's sites but has no PTV rights.
- **Verify after every write** with the helper below: `(ps/state id)` shows
  the live PTV service, Lipas's mirror and the review rows left. PTV's own
  test UI (https://palvelutietovaranto.trn.suomi.fi) is the second check
  where a scenario says so.
- **Writes that can't be taken back:**
  - The first save of a service made in PTV gives it Lipas's sourceId for
    good (C3). Use the throwaway services named here, never "Liikuntaneuvonta"
    or "Soveltava liikunta", which the site scenarios use.
  - A created service (E1, the p8 fixture) can only be archived. `ps/cleanup!`
    archives them.
- **Interplay with the site scenarios:** `user.ptv-drift-scenarios/run-all!`
  re-syncs sites s1–s17 and their connections. That changes the Palvelupaikat
  counts below and undoes F1. Run it before this plan, not during it, then
  take the baseline again with `(ps/report)`.
- **Hidden tab:** a Chrome window that isn't in front pauses Reagent's
  renders. Before clicking in such a tab, run the MessageChannel patch from
  the site plan's tooling note (`reagent.impl.batching.next_tick` and
  `requestAnimationFrame`), and again after every page reload. The PTV
  dialog is a lazy module: open it with the PTV button before dispatching
  any `lipas.ui.ptv.*` event from the console.
- **Translations** need a full page reload to show changes.

## Step 0: the helper (to build first)

`dev/user/ptv_services_scenarios.clj`, in the style of
`dev/user/ptv_drift_scenarios.clj` (same `org`, `svc`, `admin`, `dbc`,
`ptv` helpers). Every setup function is re-runnable: it first writes the
service back to a known state, then applies its change.

```clojure
(load-file "dev/user/ptv_services_scenarios.clj")
(require '[user.ptv-services-scenarios :as ps])
(ps/run-all!)     ; p1–p8 set up; returns [[name :ok|:failed ...] ...]
(ps/report)       ; each scenario service as the table computes it
(ps/state id)     ; one service: PTV, mirror, review rows, audit, links
(ps/cleanup!)     ; archives p8 and p10, undoes F1's link
```

What the functions do:

| Function | Does |
|---|---|
| `lipas-write!` `[service-id texts]` | Writes texts as the tool's "Synkronoi nyt" does: `ptv-core/upsert-ptv-service!` with the service's mode ("lipas" by source-id, "native" by id). PTV and the mirror then agree: the baseline. |
| `ptv-edit!` `[service-id f]` | What an edit in PTV's own UI does. GETs the live service, applies `f` to its `{field {locale text}}` texts, builds the PUT with `ptv-data/native-patch-body`, then **puts the live `:sourceId` back** (the body stamps Lipas's adopted sourceId, which would turn a Lipas-created service into an adopted one), and PUTs it with `ptvi/update-service-by-id`. The mirror is not touched. |
| `create-other-system!` | POSTs a minimal fi service with sourceId `e2e-other-system-1` (no `lipas-` prefix, so `:other-system`), connects s1's location (615340) to it with `ptvi/update-service-connections`. Returns its id. |
| `archive!` `[service-id]` | PUT of the service's own texts (`native-patch-body`) with publishingStatus "Deleted". A status-only PUT, as `archive-service-location` does for locations, is refused for services: PTV wants the target groups, ontology terms and service classes with it. |
| `state` `[service-id]` | `{:ptv {:status :source-id :languages :modified :name :summary :description :user-instruction :locations n :lipas-locations n :other-channels n} :mirror {texts :last-sync} :review [[key origin] ...] :audit {field status} :sites #{lipas-id ...}}`. `:review` is `ptv-review/service-review` with no edits, `:sites` the sites whose saved `:ptv :service-ids` hold it. |
| `report` | `state` of every scenario service, short form. |

Setup functions, each one `lipas-write!` back to known texts first:
`p1-baseline!`, `p2-ptv-summary!`, `p3-ptv-summary!`, `p4-ptv-name!`,
`p5-baseline!`, `p8-other-system!`, `p11-baseline!`. p6, p7, p9 and p10 have
none: C3, D1's draft, F1 and E1 make their state in the browser or PTV's UI.

## Demo services

Utajärvi, as of 2026-10-30. Ids are in the helper's `svc` map.

| Id | Service | Kind | Set up as |
|---|---|---|---|
| p1 | Jäähallit (`8ee111d8…`) | created in Lipas | baseline |
| p2 | Kuntoilukeskukset ja liikuntasalit (`5f74a478…`) | created in Lipas | fi summary changed in PTV |
| p3 | Pallokentät (`0ebaa2ae…`) | created in Lipas | fi summary changed in PTV; the user edits it in Lipas (conflict) |
| p4 | Maauimalat ja uimarannat (`ea2e945a…`) | created in Lipas | fi name changed in PTV |
| p5 | Lipas capture -testi C | made in PTV, written by Lipas (fi+se) | baseline |
| p6 | PTV Test3 | made in PTV, never written by Lipas (fi+se), not linked | untouched until C3 |
| p7 | Lipas capture -testi D | made in PTV, written by Lipas | unpublished draft in PTV (made in PTV's UI) |
| p8 | e2e other system | another system's sourceId | created by `create-other-system!`, s1's location connected |
| p9 | Puistojumppa (`0d7771c4…`) | made in PTV, 1 service location, not linked | untouched until F1 |
| p10 | Ampumaurheilupaikat | created in Lipas during E1 | – |
| p11 | Retkeilyn palvelut | created in Lipas | baseline; audited in H |

p7's draft: a PUT with publishingStatus "Modified" makes one through the API
("Draft" is refused for a published service). Nothing but another "Modified"
PUT is accepted while it exists, so only PTV's own UI can publish or discard
it.

### Baseline table

Before any scenario: "16/16", with 68 services behind "Näytä ne".

| Service | Integraatio | Luotu | Palvelupaikat | Caption | Liikuntapaikat |
|---|---|---|---|---|---|
| Jäähallit | Ajan tasalla | Lipas | 1 | 1 Lipaksesta | 1 |
| Jääurheilualueet ja luonnonjäät | Ajan tasalla | Lipas | 2 | – | 0 |
| Kuntoilukeskukset ja liikuntasalit | Ajan tasalla | Lipas | 4 | 3 Lipaksesta | 3 |
| Liikunta- ja ulkoilureitit | Ajan tasalla | Lipas | 19 | 1 Lipaksesta | 1 |
| Lähiliikunta ja liikuntapuistot | Ajan tasalla | Lipas | 8 | 6 Lipaksesta | 6 |
| Maauimalat ja uimarannat | Ajan tasalla | Lipas | 2 | 2 Lipaksesta | 2 |
| Pallokentät | Ajan tasalla | Lipas | 4 | 1 Lipaksesta | 2 |
| Retkeilyn palvelut | Ajan tasalla | Lipas | 4 | – | 0 |
| Yleisurheilukentät ja -paikat | Ajan tasalla | Lipas | 1 | 1 Lipaksesta | 1 |
| Liikuntaneuvonta | – | PTV | 2 | 1 Lipaksesta · +1 muu kanava | 0 |
| Lipas capture -testi A edit, C, D, E; Lipas native -testi F | Ajan tasalla | PTV | Ei yhtään (amber) | – | 0 |
| Soveltava liikunta | – | PTV | 1 | 1 Lipaksesta · +1 muu kanava | 0 |

Retkeilyn palvelut reads 0 sites although the wizard's default links give it
19 in memory: those aren't saved (see F3).

---

## A. Finding services (read-only)

**A1. The default view**

Open the map, then PTV, choose "Utajärven kunta", then Palvelut. Expect:
- "Luo uusi palvelu" above the filter bar; the bar open with "16/16", the
  search and the chips "Kaikki (16)", "Ristiriita", "Valmis
  synkronoitavaksi", "Estetty", "Ajan tasalla", "Ei palvelupaikkoja";
- the rows of the baseline table, Lipas-created first, then by name;
- under the table: "Organisaatiolla on PTV:ssä lisäksi 68 palvelua, jotka
  eivät liity Lipakseen." and "Näytä ne". Hovering the sentence explains
  what counts as related to Lipas.

**A2. Column tooltips**

Hover each underlined header (Luotu, Palvelupaikat, Liikuntapaikat), each
origin chip ("Lipas", "PTV") and the "–" status. Expect the explanations;
"–" says Lipas hasn't updated the service.

**A3. Palvelupaikat tooltip**

Hover Liikuntaneuvonta's "2". Expect "Palvelupaikat PTV:ssä (2)", then
"Lipaksen liikuntapaikat (1)" (Utajärven urheilukenttä), "PTV:ssä lisätyt
(1)" and "Muut asiointikanavat (1)" with the type ("verkkoasiointi").
Hover Liikunta- ja ulkoilureitit's "19": at most 7 names per group, then
"ja n muuta". Click inside the tooltip: the row must not open.

**A4. Chips**

- "Ei palvelupaikkoja": the five capture/native test services; the count
  shows on the chip.
- "Ajan tasalla": 14 (all but Liikuntaneuvonta and Soveltava liikunta).
- "Ristiriita", "Valmis synkronoitavaksi", "Estetty": none at baseline.
- Clicking the selected chip again goes back to "Kaikki".
- Collapse the bar with the count button: the selected chip shows as a chip
  with ×, which clears it.

**A5. Search, and the services below the table**
1. Search "kirjasto". Expect "0/16", "Yksikään Lipakseen liittyvä palvelu ei
   vastaa hakua.", "Tyhjennä suodattimet", and under the table "2 niistä
   vastaa hakua." with a search icon.
2. "Näytä ne". Expect "2/84", a heading row "Organisaation muut palvelut (2)
   · Eivät liity Lipakseen" with Kirjastopalvelut ("Ei yhtään" in grey,
   "+1 muu kanava") and Kolarin kirjasto (1, "+1 muu kanava"), and
   "Piilota organisaation muut palvelut".
3. Clear the search. Expect the 16 rows, the heading, then the 68 others by
   name; a service without a Finnish name shows in its Swedish or English
   name, or "(nimetön)".
4. "Piilota…". Expect the 16 again.

**A6. A row opens in place**

Open Jäähallit (the arrow, or anywhere on the row). Expect:
- no review panel;
- left: "Palvelun nimi", Palveluluokat, Asiasanat, "Palvelupaikat PTV:ssä
  (1)" with "Lipaksen liikuntapaikat (1)" linking to the PTV location,
  "Liitetyt liikuntapaikat Lipaksessa (1)" with Utajärven jäähalli and
  "Näytä Liikuntapaikat-välilehdellä", the info box, the AI and translate
  buttons, "Synkronoi nyt" disabled;
- right: FI/EN tabs and the three texts;
- the table doesn't scroll sideways.

Open Lipas capture -testi C: "Palvelupaikat PTV:ssä (0)" with the amber
"Palveluun ei ole liitetty yhtään palvelupaikkaa…", FI/SE/EN tabs (org
languages plus its versions), the AI button disabled with its tooltip.

---

## B. A service created in Lipas (writes)

**B1. Edit and sync** (p1)
1. Open Jäähallit, filter "Ajan tasalla".
2. Add a sentence to the fi summary.

Expect:
- the chip turns "Valmis synkronoitavaksi"; the row stays, marked "Ei enää
  vastaa suodatinta", and keeps its place;
- "Ajan tasalla (13)";
- the review panel "Synkronointi päivittää PTV:hen 1 tietoa.";
- "Synkronoi nyt" enabled, "Peru tallentamattomat muutokset" shown.

3. "Synkronoi nyt".

Expect: the spinner, then the chip "Ajan tasalla"; no panel; the row still
open in the same place. Close it: it leaves the filtered list unless it
matches.

Verify `(ps/state p1)`:
- PTV's fi summary is the new text, its other texts unchanged;
- the mirror has the same and a new `:last-sync`;
- `:review` is empty.

**B2. A change made only in PTV is kept** (p2) — *superseded by K1/K2
(2026-11-07): a change made only in PTV is now a choice in the review.*

p2's fi summary was changed in PTV. Expect:
- the chip "Ajan tasalla", no panel when opened (PTV's text shows in the
  field);
- edit the description and sync.

Verify: PTV has the new description and still PTV's summary; the mirror
holds both.

**B3. Conflict, decided in Lipas's favour** (p3)
1. Open Pallokentät and edit the fi summary.

Expect:
- the chip "Ristiriita"; above the filter bar "1 palvelussa on ristiriita
  PTV:n kanssa – ratkaise se ennen synkronointia." with "Näytä se";
- the sort: Pallokentät stays where it was while open (its place is pinned);
  close it and it moves to the top.

2. Type something into the search, then "Näytä se". Expect the search
   cleared, "Ristiriita (1)", Pallokentät listed.
3. Open it. Expect "PTV:ssä on muutettu 1 tietoa – valitse, mitkä jäävät
   voimaan.", the row "Tiivistelmä (FI) · Muutettu molemmissa".
4. "Synkronoi nyt" first: expect the question with "Käy muutokset läpi".
   Choose it: the panel scrolls into view.
5. "Pidä Lipaksen versio". Expect "Valinnat tehty. Synkronointi päivittää
   PTV:hen 1 tietoa.", the chip "Valmis synkronoitavaksi", the banner gone.
6. Sync.

Verify: PTV and the mirror have Lipas's summary; `:review` empty.

**B4. Conflict, decided in PTV's favour** (p3 again) — *superseded by K1:
taking PTV's version now leaves "Valmis synkronoitavaksi", and the sync
records PTV's version as Lipas's without a write.*

`(ps/run-all! [#'ps/p3-ptv-summary!])`, reload, edit the summary, then "Ota
PTV:n versio". Expect: the field shows PTV's text, the chip "Ajan tasalla",
nothing to sync. Verify: no write happened (PTV `:modified` unchanged).

**B5. A name changed in PTV is kept** (p4)

Edit p4's description and sync. Verify: PTV keeps the name it was given in
PTV; only the description changed.

**B6. A name edited in Lipas** (p1)

With the pencil, rename Jäähallit to "Jäähallit e2e", sync. Verify: PTV's fi
name is the new one, the row shows it after the sync. Rename it back and sync.

**B7. PTV changed after the tab loaded** (p1)
1. With the tab open, `(ps/ptv-edit! p1 #(assoc-in % [:summary :fi] "…"))`.
2. In the browser, edit p1's description and sync.

Expect: the refusal "Palvelua on muokattu PTV:ssä sen jälkeen, kun tiedot
haettiin…", the services reloaded, your description edit kept, the summary
now PTV's. Sync again. Verify: PTV kept its summary and has your description.
*Since 2026-11-07 (K7): after the reload the summary is a change made in PTV
to decide ("Ristiriita"), the form showing Lipas's summary until PTV's is
taken.*

**B8. Texts PTV would reject**

Shorten p1's fi summary to 3 characters. Expect "Korjaa ennen
synkronointia" listing the summary, and "Synkronoi nyt" disabled. "Peru
tallentamattomat muutokset" restores it. Nothing is written.

---

## C. Services made in PTV (writes)

**C1. Edit a service Lipas has written** (p5)

Edit Lipas capture -testi C's fi description and sync. Verify: PTV has it,
its classification and other texts unchanged; the mirror has a new revision.

**C2. Remove a language version** (p5)

p5's language versions are fi and en (its `languages` field says sv, which is
not a version). On the EN tab, "Poista kieliversio EN PTV:stä". Expect the
notice "Kieliversio EN poistetaan PTV:stä seuraavassa synkronoinnissa…", the
texts read-only, the chip "Valmis synkronoitavaksi". Sync. Verify: PTV has
the fi version only. The EN tab stays, empty, as English is one of the org's
languages. Reset: `(ps/p5-baseline!)` writes the English version back.

**C3. First save of a service Lipas hasn't written** (p6, irreversible)
1. "Näytä ne", search "PTV Test3", open it. Expect "–", origin "PTV".
2. Edit its fi summary. Expect "Valmis synkronoitavaksi".
3. "Synkronoi nyt". Expect the dialog "Päivitetäänkö PTV:ssä luotu palvelu
   Lipaksesta?" with "…asiointikanavissa (0 kpl). Lipaksen liikuntapaikoista
   siihen on liitetty 0." (the saved links).
4. "Päivitä PTV:hen".

Expect:
- the chip "Ajan tasalla"; the row stays open and in its place below the
  heading, without remounting (the language tab you were on stays);
- close it: it moves above the heading, among the Lipas-related services;
  the footer says 67.

Verify: PTV's sourceId is `lipas-3d1759a2-…-ptv-<id>`; the mirror has a
first revision.

**C4. The AI button**

On p5 and p6, "Laadi luonnokset tekoälyllä" is disabled with its tooltip;
on p1 it is enabled.

---

## D. Blocked and read-only services

**D1. An unpublished draft in PTV** (p7)

Services aren't refetched when a row opens, so the draft shows only after a
sync hits it.
1. Open Lipas capture -testi D, edit a text, sync.

Expect: the chip "Estetty", the review's Modified notice with "Avaa PTV:ssä",
"Synkronoi nyt" disabled.

Verify: PTV's draft untouched, the mirror unchanged. Publish or discard the
draft in PTV's UI, reload: the chip is back to "Ajan tasalla".

**D2. Another system's service** (p8)

The fixture is connected to s1's location, so it is listed by default.
Expect:
- the row: a lock icon in Integraatio, origin "Muu järjestelmä", Palvelupaikat
  "1 · 1 Lipaksesta";
- opened: no review panel, the name as a link to PTV, the info "Palvelua
  ylläpidetään toisen järjestelmän kautta…" with "Avaa PTV:ssä", the texts
  read-only, no sync button;
- no error in the console. Before the `parse-service-source-id` fix, its
  sourceId threw here.

---

## E. Creating a service

**E1. "Luo uusi palvelu"** (p10, archived by `cleanup!`)
1. Click "Luo uusi palvelu". Expect the creation form above the table; the
   button's icon turns into ×.
2. Choose "Ampumaurheilupaikat". Write (or generate) fi and en texts.
3. Export.

Expect: the form closes; Ampumaurheilupaikat appears with "Ajan tasalla",
origin "Lipas", "Ei yhtään" in amber, 0 sites.

Verify: PTV has the service with sourceId `lipas-3d1759a2-…-<sub-category>`
and the texts; the mirror has it.

---

## F. Links and counts (writes through the Liikuntapaikat tab)

**F1. Linking a site moves a service into view** (p9; undone by `cleanup!`)
1. Note that Puistojumppa is below the table (1 service location, 0 sites).
2. In Liikuntapaikat, open 605450 Utajärven urheilukentän ulkokuntoilupaikka,
   add Puistojumppa to its services, save and sync.
3. Back in Palvelut.

Expect: Puistojumppa among the Lipas-related services; Palvelupaikat "2 · 1
Lipaksesta", Liikuntapaikat 1; the footer one less.

Verify `(ps/state p9)`: `:sites #{605450}`, PTV connects the site's location.

**F2. Unlinking** — remove it again, sync. Expect Puistojumppa back below the
table. Verify the PTV connection is gone.

**F3. An unsaved link doesn't count**

In Liikuntapaikat, add a service to a site's row without syncing. Palvelut's
Liikuntapaikat count for that service is unchanged. Undo.

---

## G. From the count to the sites (phase 2)

**G1. The filter**

Click Kuntoilukeskukset ja liikuntasalit's "3". Expect the Liikuntapaikat
tab with the chip "Palvelu: Kuntoilukeskukset ja liikuntasalit", "3/3", and
the three sites (509023, 79800, Kylätalo Katajiston liikuntasali). The search
and status chips narrow within them.

The same from the open row's "Näytä Liikuntapaikat-välilehdellä".

**G2. Clearing** — × on the chip: all sites, "52/52".

**G3. The conflict banner** — with the filter set, the banner "… liikuntapaikassa
on ristiriitoja …" counts every site; its "Näytä ne" clears the service filter
and shows them all.

**G4. "Synkronoi kaikki valmiit" with the filter set** (writes)

Make one site of the three and one outside them "Valmis synkronoitavaksi"
(e.g. edit their descriptions), set the filter, and note the button's
count. It counts every ready site, not only the filtered ones. Decide
whether that's right before running it. If kept: run it, verify both
sites synced.

**G5. Reload and tab switch** — the filter survives switching tabs; a page
reload clears it. (Changing the org also clears it; don't test that with
Liminka or Raahe.)

---

## H. Audit (writes to `ptv_service_audit`)

**H1. A change request** (p11)

In Katselmointi, request changes to Retkeilyn palvelut's description, with
feedback, and save. In Palvelut, expect:
- the amber warning in Katselmointi, the row sorted to the top (after any
  conflict or blocked row);
- opened: "Katselmoijan palaute - vaatii muutoksia" under the description.

**H2. Fixing it**

Edit the description and sync. Expect the verdict per
`audit-display-status`: a text fixed in response to a change request counts
as approved, so the green check. Verify `(ps/state p11)` `:audit`.

**H3. Approved with a comment**

Approve with a comment. Expect the check and the grey comment marker; its
tooltip shows the comment.

---

## I. Every scenario, also

**I1. Reload loses unsaved edits** — by design; write down whether it
surprises anyone.

**I2. Two tabs** — edit p1 in one, sync from the other first: the second
sync is refused as in B7.

**I3. Editor without PTV rights** — no Palvelut tab at all.

**I4. Console** — no errors or warnings, React key warnings included, in any
scenario.

**I5. Narrow window** — at about 400 px the table scrolls inside its box,
the page doesn't; the open row stacks its columns.

**I6. Keyboard** — row arrows, chips, the count links and "Näytä ne" reach
and work by keyboard; focus stays visible.

**I7. Languages** — sv and en: every new text translated, no key names.

**I8. Responsiveness** — with "Näytä ne" on (84 rows), typing in an open
service's text stays instant (`service-state-cache`).

---

## J. Cleanup

`(ps/cleanup!)`: archives p8 and p10, removes F1's link, writes p5's sv
texts back. p6 keeps its Lipas sourceId (C3). Check `(ps/report)` against
the baseline table, apart from p6 now being Lipas-related.

## K. Changes made in PTV, and the tester's findings (2026-11-07)

From Terhi's notes on lipas-dev (`local-scrap/2026-11-ptv-Havaintoja.pdf`,
`…-Muutosehdotukset_tekstit.xlsx`), decided with the user:

- **A1** A name or text changed in PTV since Lipas's last write of the
  service opens the review, as on the Liikuntapaikat tab. Lipas's side is
  its last write (the mirror, `ptv_service`) plus the user's unsaved edits;
  the form shows it until PTV's version is taken. Who changed what is told
  by PTV's `modified`: the mirror now stores the one PTV reported for the
  write (`:ptv-modified`); an older revision falls back to "over 2 minutes
  after `:last-sync`". A language version added in PTV since the write is
  PTV's (Lipas has no version of it). A service Lipas hasn't written keeps
  PTV's texts in the form, as before.
- **A2** Every difference taken as PTV's and nothing to send: "Synkronoi
  nyt" records PTV's current service as Lipas's version
  (`/actions/take-ptv-service-version`, a fresh GET, revision marked
  `:read-from-ptv`), without a PTV write.
- **A3** The Integraatio heading and "–" explain that Lipas tracks a
  service from its first save from Lipas; linking a site doesn't save it.
- **A4** "Muokattu viimeksi … (Lipaksessa)" / "(PTV:ssä)".
- **A5** New info texts (Lipas-created; PTV-made, written or not) and a
  tracking bullet in the first-save dialog.
- **B1** Only service locations: no "Muut asiointikanavat", no "+n muuta
  kanavaa". Another organisation's channel (type unknown) is left out too.
- **B2** The footer line reads like the list heading, "Organisaation muut
  palvelut (n) · Ei liitetty Lipaksen liikuntapaikkoihin", and its button
  ("Näytä ne" / "Piilota ne") stays put.
- **B3** "Palveluun ei ole liitetty yhtään palvelupaikkaa. Liitä palvelu
  liikuntapaikalle Liikuntapaikat-välilehdellä."

Setups (each re-runnable; PTV writes, fast on 2026-11-07):
`ps/p2-ptv-summary!`, `ps/p3-ptv-summary!`, `ps/p4-ptv-name!`,
`ps/p5-ptv-summary!` (new: p5's fi summary changed in PTV). `ps/state`
now also shows each row's `[key origin current]`, `:take-ptv-version?`,
`:last-modified-in` and the mirror's `:ptv-modified`, `:read-from-ptv` and
revision count.

**K1. A text changed only in PTV, PTV's version taken (A1, A2)** (p2)
1. `(ps/p2-ptv-summary!)`, reload. Expect the banner "1 palvelussa on
   ristiriita…", Kuntoilukeskukset ja liikuntasalit "Ristiriita".
2. Open it. Expect the bar "PTV:ssä on muutettu 1 tietoa – valitse, mitkä
   jäävät voimaan.", and expanded the row "Tiivistelmä (FI) · Muutettu
   PTV:ssä" with Lipas's text (no "(e2e PTV)") on the Lipas card; the
   summary field shows Lipas's text.
3. "Ota PTV:n versio". Expect the field to show PTV's text, the bar
   "Valinnat tehty. PTV:n versiot otetaan Lipakseen.", the chip "Valmis
   synkronoitavaksi", "Synkronoi nyt" enabled.
4. "Synkronoi nyt".

Expect "Ajan tasalla", no panel. Verify `(ps/state :p2)`: PTV's
`:modified` unchanged (no write), the mirror one revision more with PTV's
summary, `:read-from-ptv true`, `:review []`. Reload: still "Ajan tasalla".

**K2. A text changed only in PTV, Lipas's version kept (A1)** (p3)

`(ps/p3-ptv-summary!)`, reload, open Pallokentät: "Ristiriita", the same
row. "Pidä Lipaksen versio": the field keeps Lipas's text, "Valinnat tehty.
Synkronointi päivittää PTV:hen 1 tietoa.", "Valmis synkronoitavaksi".
Sync. Verify: PTV's fi summary is Lipas's again (no "(e2e PTV)"), the
mirror has it with PTV's new `:modified`, `:review []`.

**K3. Sync with the change undecided (A1)** (p2 again)

`(ps/p2-ptv-summary!)`, reload, open p2, "Synkronoi nyt" without deciding.
Expect the question "PTV:ssä on muutoksia, joita et ole käynyt läpi" naming
the summary; "Käy muutokset läpi" scrolls to the panel. (Don't overwrite:
K1 continues from here.)

**K4. A name changed in PTV, taken, with an edit of Lipas's own (A1)** (p4)

`(ps/p4-ptv-name!)`, reload, open Maauimalat ja uimarannat. Expect the row
"Nimi (FI) · Muutettu PTV:ssä" and the name field showing Lipas's name.
Take PTV's name, edit the fi description, sync. Expect "Valinnat tehty.
Synkronointi päivittää PTV:hen 1 tietoa." before the sync, "Ajan tasalla"
after. Verify: PTV keeps the "(e2e PTV)" name and has the new description;
the mirror has both, not `:read-from-ptv`.

**K5. A service made in PTV that Lipas has written (A1)** (p5)

`(ps/p5-ptv-summary!)`, open "Lipas capture -testi C" (below the table if
it isn't listed: "Näytä ne"). Expect "Ristiriita", the summary row, the
native info text ("…Jos nimeä tai tekstejä on muutettu PTV:ssä Lipaksen
edellisen synkronoinnin jälkeen…"). Take PTV's version, sync (A2 path).
Verify as K1.

**K6. A service made in PTV that Lipas hasn't written (A1, A3, A5)**
(Puistojumppa, read-only)

"Näytä ne", open Puistojumppa. Expect "–" with the tooltip "Ei seurannassa:
…", no review panel, PTV's texts in the form, the info "Palvelu on luotu
PTV:ssä, eikä sitä ole vielä tallennettu Lipaksesta…". Edit its summary,
"Synkronoi nyt": the dialog "Päivitetäänkö PTV:ssä luotu palvelu
Lipaksesta?" with the new bullet "Tallennuksen jälkeen Lipas seuraa…".
**Peruuta** (a save would give it Lipas's sourceId for good), then "Peru
tallentamattomat muutokset".

**K7. PTV changes again before the take is synced (A2)** (p2, after K3)

Take PTV's version, then `(ps/ptv-edit! :p2 #(update-in % [:summary :fi]
str " (e2e PTV2)"))` before syncing, then "Synkronoi nyt". Expect the
refusal "Palvelua on muokattu PTV:ssä…", the services reloaded and the
row "Ristiriita" again over the new text. Verify: no new mirror revision.
Then take PTV's again and sync (K1's end).

**K8. Integraatio explained (A3)** — hover (or focus) the Integraatio
heading: "Palvelun tila Lipaksen ja PTV:n välillä. Lipas seuraa palvelua
siitä alkaen, kun se on luotu Lipaksessa tai ensimmäisen kerran tallennettu
Lipaksesta. Viiva (–) …".

**K9. Where it was modified (A4)** — open Jäähallit: "Muokattu viimeksi …
(Lipaksessa)". p2 before K1's sync: "(PTV:ssä)"; after K1 (PTV's version
recorded) still "(PTV:ssä)"; p3 after K2: "(Lipaksessa)".

**K10. Info texts (A5)** — Jäähallit: "Palvelu on luotu Lipaksessa. Lipas
ylläpitää palveluluokkia ja asiasanoja, joten niihin PTV:ssä tehdyt
muutokset korvautuvat seuraavassa synkronoinnissa. Nimeä ja kuvauksia voi
muokata molemmissa. Jos niitä on muutettu PTV:ssä, Lipas näyttää
muutokset, ja ennen synkronointia valitaan, kumpi versio jää voimaan. …".
p5 and Puistojumppa as in K5 and K6.

**K11. Only service locations (B1)** — Liikuntaneuvonta (read-only):
Palvelupaikat shows only its service locations ("1 · 1 Lipaksesta", no
"+1 muu kanava"); its tooltip and the open row have no "Muut
asiointikanavat". The Palvelupaikat heading's tooltip says other channels
aren't shown. Kirjastopalvelut (below the table, an e-service only): "Ei
yhtään".

**K12. The footer and the list heading (B2)** — the footer reads
"Organisaation muut palvelut (n) · Ei liitetty Lipaksen
liikuntapaikkoihin" and "Näytä ne". Note the button's position, click it:
the same line, "Piilota ne" at the same place; the table's heading row
reads "Organisaation muut palvelut (n)" with "Ei liitetty Lipaksen
liikuntapaikkoihin". Click again: back.

**K13. A service without service locations (B3)** — open Lipas capture
-testi C (or any "Ei yhtään"): "Palveluun ei ole liitetty yhtään
palvelupaikkaa. Liitä palvelu liikuntapaikalle
Liikuntapaikat-välilehdellä."

**K14. Swedish and English** — the new texts in sv and en, no raw keys.

## Results: run of 2026-10-30

Local Lipas against the PTV test env, Utajärvi, admin. Real writes through
the browser (clicks, typing, and the app's own events where noted), each
verified with `ps/state`. Hidden tab with the MessageChannel patch.

| Scenario | Service | Result |
|---|---|---|
| A1 default view | all | pass: 17/17 (the 16 of the baseline + p8), footer 68 |
| A4 chips | all | pass: "Ei palvelupaikkoja" the five test services, "Ajan tasalla" 14 |
| A5 search, services below the table | Kirjasto… | pass: 0/17 with "2 niistä vastaa hakua.", "Näytä ne" 2/84 with the heading, 85 rows unfiltered |
| B1 edit and sync | p1 | pass: pinned with "Ei enää vastaa suodatinta", 13/17, panel "1 tietoa"; PTV and mirror have the text |
| B2 change made only in PTV | p2 | pass: no panel, PTV's summary in the field; PTV kept it, got the description |
| B3 conflict, keep Lipas | p3 | pass: banner, "Näytä se" cleared the search, gate dialog, focus on "Pidä Lipaksen versio", "Valinnat tehty…"; PTV has Lipas's text |
| B4 conflict, take PTV | p3 | pass: "Ajan tasalla", sync disabled, PTV `:modified` unchanged |
| B5 name changed in PTV | p4 | pass: PTV kept "(e2e PTV)" in the name |
| B6 rename with the pencil | p1 | pass: PTV's fi name changed; the row followed |
| B7 PTV changed after loading | p1 | **failed, fixed**: the summary came back as a conflict (finding 1). Pass after the fix |
| B8 text PTV would reject | p1 | pass: "Tiivistelmä (FI) on liian lyhyt (vähintään 5 merkkiä).", sync disabled, undo restores |
| C1 edit a written native | p5 | pass |
| C2 remove a language version | p5 | pass (expectation corrected above) |
| C3 first save | p6 | pass: dialog "(0 kpl)… 0", sourceId `lipas-…-ptv-b37555b6…`, mirror; the row stayed open on its SE tab below the heading, moved up once closed; 67 behind "Näytä ne" |
| C4 AI button | p5, p6, p1 | pass |
| D1 draft in PTV | p7 | pass: "Estetty", "PTV:ssä on julkaisemattomia muutoksia…", "Avaa PTV:ssä", sync disabled. Also the toast "Synkronointi PTV:hen epäonnistui" with the reason (finding 5) |
| D2 another system's service | p8 | pass: lock, "Muu järjestelmä", "1 · 1 Lipaksesta", read-only, no review, no sync button |
| E1 create | p10 | pass: `31223efe…`, sourceId `lipas-…-4800`, mirror; "Ajan tasalla", "Ei yhtään"; archived by cleanup |
| F1 link a site | p9, 605450 | pass: Liikuntapaikat 1 at once; Palvelupaikat "2 · 1 Lipaksesta" only after a data reload (finding 2) |
| F2 unlink | p9, 605450 | pass: PTV connection gone |
| F3 unsaved link | p9 | pass: count 0; listed among the Lipas-related services by its in-memory link, by design |
| G1 filter from the count | p2 | pass: chip, 3/3, the three sites; also from the open row |
| G2 clear | – | pass |
| G3 conflict banner | – | pass: filter cleared, 7/52 |
| G4 "Synkronoi kaikki valmiit" with the filter | 5 sites | run on 2026-10-30 (second session): with "Palvelu: Kuntoilukeskukset…" set (3/3 shown, 1 of them ready) the button read 5, the dialog "Synkronoidaanko 5 liikuntapaikkaa?" listed the conflicts it skips, and all 5 synced (80247, 506495, 526137, 600093, 606904). Site scenarios s3, s7, s14, s16, s17 re-created afterwards. Kept as is (finding 6) |
| G5 tab switch | – | pass: the filter survives |
| H1 change request | p11 | pass once every required field had a verdict: a partial audit reads as unfinished (`:none`), by design. Warning icon, sorted first |
| H2 fixed and synced | p11 | pass: green check |
| H3 approved with a comment | p11 | pass: check and comment marker |
| I4 console | – | pass for D2–H: no errors or warnings |
| I7 sv, en | – | pass: no raw keys |
| I1 reload | p1 | pass: the unsynced edit is gone after a reload, the row "Ajan tasalla" (by design) |
| I2 two tabs | p1 | pass: tab B synced a description; tab A's sync was refused ("Palvelua on muokattu PTV:ssä…"), A kept its summary edit and showed B's description; A's second sync sent the summary; PTV has both |
| I3 editor without PTV rights | impersonated Utajärvi city manager | pass: no PTV button, no PTV orgs, no tabs even with the dialog opened by its event |
| I5 at 400 px | p1 | pass with notes: in a headless 400×850 viewport the page doesn't scroll sideways, the chips wrap, the table scrolls in its box. Notes: the map has no PTV button at phone width; an open row is as wide as the table (its summary field 761 px), so the editor scrolls sideways inside the table, as the Liikuntapaikat tab's rows do (finding 8) |
| I6 keyboard | – | pass with a note: Tab reaches the search, the six chips, each row's arrow and count link; Enter opens and closes a row, Space toggles a chip; focus visible. The Palvelupaikat, header and origin tooltips can't be reached by keyboard (finding 9) |
| I8 responsiveness | p1 | pass with a note: a keystroke in an open service re-renders in 31 ms (median, 64 ms max) with 18 rows, 76 ms (116 ms max) with all 85; `::service-rows` itself takes 6–9 ms (the cache works). Every row re-renders on each keystroke (finding 10) |
| J cleanup | – | pass: p8 and p10 archived, markers stripped, p5's English back; p7 keeps its draft |

## Fixes and re-run, 2026-10-30 (third session)

Every open finding was fixed (below), and the scenarios they touch run
again: local Lipas, Utajärvi, admin unless noted. The browser was a 1290 px
or 400 px same-origin iframe in the logged-in Chrome, with the render patch.

| Check | Finding | Result |
|---|---|---|
| B8 in the row | 4 | pass: p1's summary "Lyh": the chip "Estetty" with the tooltip "…PTV ei hyväksyisi kaikkia tekstejä…", listed under "Estetty", not under "Valmis synkronoitavaksi"; undo brings back "Ajan tasalla" |
| B7 with the search "jää" on | 3 | pass: the refusal toast, the same table node (no remount), the row open, the search kept, the description edit kept, the summary PTV's. The second sync went through; PTV and the mirror have both |
| D1 | 5 | pass: no toast; "Estetty", the draft notice, the row open. PTV's draft and the mirror untouched |
| A location into a draft (89913, synced before its row was opened) | 5 | pass: PTV refused ("You cannot update entity with status Modified"), no toast, the row "Estetty", no new Lipas revision |
| F1, F2 | 2 | pass without a reload: Puistojumppa into the table with "2 · 1 Lipaksesta", Liikuntapaikat 1, footer 66; after unlinking back below the table, footer 67. The services were refetched in the background: the loading flag never set, the table not remounted |
| A batch of 605450 and 89913 | 2, 5 | pass: no refetch after the first site; PTV refused the second (its draft), no toast, the halt, one refetch. A batch of 605450 alone: one refetch at its end |
| I5 at 400 px | 8 | pass: the open row 320 px wide in the 352 px table box, kept at 16 px while the table scrolls 300 px; the Liikuntapaikat tab the same. At 1290 px nothing changed: no sideways scroll, the row full width. The map's PTV button is in the open search panel |
| I6 | 9 | pass: Tab reaches Luotu (its tooltip explains the three origins), Palvelupaikat, Liikuntapaikat and the footer's text, each opening its tooltip |
| I8 | 10 | pass: one service row renders per keystroke; 35–41 ms with 86 rows, 27–40 ms with 19 |
| Release build | all | pass: `shadow/release*` into a separate directory, loaded with the render patch set before the bundle. The PTV button loaded the module; Utajärvi, the table, an open row, the chips, "Näytä ne" (84), the count → Liikuntapaikat with "Palvelu: Jäähallit", the 400 px row; no errors |
| A PTV manager without admin rights | – | pass: impersonated a Utajärvi city manager with the PTV manager role. Only Utajärvi, selected at once; no Katselmointi tab; a description sync of p1 reached PTV and the mirror; the count link works |

Afterwards: p1 back to its baseline, Puistojumppa unlinked, 605450 synced as
it was. Besides p7, the locations of 615340 and 89913 hold drafts in PTV
(left from the v2 review's runs).

### Findings

1. **Fixed: a synced edit came back.** After a sync, `::create-ptv-service-success`
   kept the edited texts in `:service-candidates`. Once PTV changed the same
   text, the old edit read as the user's (B7: a conflict over a summary
   synced earlier, which "Pidä Lipaksen versio" would have used to revert
   PTV's newer text). Now the texts PTV holds after the write are dropped
   (`ptv-services/drop-written-edits`); what was typed during the write stays.
2. **Fixed: Palvelupaikat stale after a site sync.** A site's sync connects
   its location to services in PTV. The tool now refetches the services
   after it (`events/refetch-services-fx`): after a single sync or an
   archive, and once at the end of a batch, whether done, stopped or halted.
   The refetch is a background one (`::fetch-ptv-services` with
   `:background? true`): it leaves the loading flag alone, which would
   unmount the tabs.
3. **Fixed: the row closed after a refused sync.** The refetch after
   "Palvelua on muokattu PTV:ssä…" is a background one too: the open row,
   the search and the chips stay.
4. **Fixed: text errors didn't show in the row.** An edit PTV would reject
   makes the row "Estetty" (`:invalid-texts?` of
   `ptv-services/service-state`), with a tooltip of its own; it is no longer
   listed as "Valmis synkronoitavaksi".
5. **Fixed: a sync into PTV's draft toasted "Synkronointi PTV:hen
   epäonnistui".** The row says it (the chip "Estetty" and the draft notice),
   so the toast is left out, for services and service locations alike.
   Correction: locations toasted too when the sync reached the draft before
   the row was opened (e.g. "Synkronoi kaikki valmiit"), which the v2 plan
   already expected not to. A batch's halt alert still names the draft.
6. **Decided: G4 stays.** "Synkronoi kaikki valmiit" syncs every ready site
   whatever the filter; its confirmation says how many (decided 2026-10-30).
7. **Left over: p7 has a draft in PTV.** Publish or discard it in PTV's test
   UI; until then a sync of it reads "Estetty".
8. **Fixed: the open row on a phone.** The open row is as wide as the table
   box shows and stays in view while the table scrolls sideways
   (`views/open-row-content`: `100cqw` of the TableContainer, which has
   `containerType: inline-size`), in both tables. Correction: the map does
   have its PTV button at phone width, in the bottom toolbar of the search
   panel, which starts closed on a phone; the I5 run looked with it closed.
9. **Fixed: tooltips without keyboard access.** The headings with a tooltip
   (Luotu, Palvelupaikat, Liikuntapaikat) and the footer's text take
   keyboard focus, which opens the tooltip. The Luotu heading's tooltip now
   explains each origin, as the chips do on hover; the channel names were
   in the open row already.
10. **Fixed: every row re-rendered on a keystroke.** The rows' toggle is made
    once (`r/with-let`), and `::service-rows` hands back the very same map
    for a row that didn't change (`subs/interned-row`). Only the open row
    renders now: 27–41 ms per keystroke whether 19 or 86 rows are listed
    (was 31 and 76 ms). What's left is the open row's own render.

## Results: run of 2026-11-07

Section K, local Lipas against the PTV test env, Utajärvi, admin. PTV writes
took 1–3 s each today (the eight setup writes 14 s in all). The Chrome
extension couldn't be used: a new tab group's tab stopped at Chrome's
certificate interstitial for https://localhost, which the extension can't
act on. The run used a headless `playwright-cli` session instead
(`.playwright/cli.config.json` ignores the self-signed certificate), driven
by clicks and fills in the page, with the render patch. Every write was
verified with `ps/state`.

| Scenario | Service | Result |
|---|---|---|
| K1 PTV-only change, PTV's taken | p2 | pass: banner "4 palvelussa on ristiriitoja…", "Ristiriita"; bar "PTV:ssä on muutettu 1 tietoa…", row "Tiivistelmä (FI) · Muutettu PTV:ssä", Lipas card and summary field without the marker; "Ota PTV:n versio" → field PTV's, "Valinnat tehty. PTV:n versiot otetaan Lipakseen.", "Valmis synkronoitavaksi"; sync → "Ajan tasalla", row stays open. PTV `:modified` unchanged (09:45:30.707885, no write), mirror 4 → 5 revisions, `:read-from-ptv true`, `:ptv-modified` = PTV's, `:review []` |
| K2 PTV-only change, Lipas's kept | p3 | pass: "Valinnat tehty. Synkronointi päivittää PTV:hen 1 tietoa."; after the sync PTV's fi summary is Lipas's, mirror revision 10 with PTV's new `:modified`, "(Lipaksessa)" |
| K3 sync undecided | p2 | pass: "PTV:SSÄ ON MUUTOKSIA, JOITA ET OLE KÄYNYT LÄPI" naming "Tiivistelmä (FI)", buttons Peruuta / Synkronoi ja korvaa / Käy muutokset läpi; the latter focuses "Pidä Lipaksen versio" |
| K4 name changed in PTV, taken, plus an edit | p4 | pass: row "Nimi (FI) · Muutettu PTV:ssä", the name field Lipas's until taken; then "…päivittää PTV:hen 1 tietoa."; PTV keeps "(e2e PTV)" and has the new description, mirror revision 5, not `:read-from-ptv`, "(Lipaksessa)" |
| K5 written native, PTV's taken | p5 | pass: "Ristiriita", the summary row, the new native info text; take + sync recorded without a write (`:modified` unchanged, `:read-from-ptv true`) |
| K6 native never written | Puistojumppa | pass: "–", no panel, PTV's texts, the not-written info; the first-save dialog with "Tallennuksen jälkeen Lipas seuraa…"; cancelled and discarded: PTV sourceId still nil, `:modified` unchanged |
| K7 PTV changes again before the take is synced | p2 | **failed, fixed** (finding 1); pass after the fix: 409, toast "…Käy PTV:n muutokset läpi ja synkronoi uudelleen.", row "Ristiriita", "Muutettu PTV:ssä" with Lipas's version beside PTV's newest text; no mirror revision from the refused take |
| K8 Integraatio heading | – | pass: the tooltip text, by hover |
| K9 where modified | p1, p2, p3, p4, p5 | pass: Jäähallit "(Lipaksessa)"; p2 and p5 after taking PTV's "(PTV:ssä)"; p3 and p4 after a write "(Lipaksessa)"; times now local (finding 3) |
| K10 info texts | p1, p5, Liikuntaneuvonta | pass |
| K11 only service locations | Liikuntaneuvonta, Kirjastopalvelut | pass: cell "2 · 1 Lipaksesta"; tooltip and open row list "Lipaksen liikuntapaikat (1)" and "PTV:ssä lisätyt (1)", no "Muut asiointikanavat"; heading tooltip; Kirjastopalvelut "Ei yhtään" (grey) |
| K12 footer and heading | – | pass: "Organisaation muut palvelut (67) · Ei liitetty Lipaksen liikuntapaikkoihin" + "Näytä ne"; toggled, the same line with "Piilota ne" at the same x (522 px), the table heading row "Organisaation muut palvelut (67) / Ei liitetty Lipaksen liikuntapaikkoihin". The line moves down by the rows opened above it |
| K13 no service locations | p5 | pass: "Palveluun ei ole liitetty yhtään palvelupaikkaa. Liitä palvelu liikuntapaikalle Liikuntapaikat-välilehdellä." |
| K14 sv, en | – | pass: headings, Integraatio tooltip, footer, info texts, "(i PTV)" / "(in PTV)"; no raw keys |
| Console | – | the two 409s of K7 (by design), and a React key warning from the shared filter bar (finding 4) |

### Findings

1. **Fixed: a refused sync kept a taken PTV text as the user's own.** After
   "Ota PTV:n versio" and a change in PTV before the sync, the refusal
   reloaded the services but the edit still held PTV's earlier text, so the
   row read "Muutettu molemmissa" with PTV's old text on the Lipas card. A
   refused sync now drops the edits that repeat PTV's version as loaded
   (`events/::create-ptv-service-failure`, `ptv-services/drop-written-edits`,
   as a successful write already did): PTV's new change is asked about
   against Lipas's version. A text of the user's own stays.
2. **Fixed: "Peru tallentamattomat muutokset" showed without edits.** With
   PTV's change undecided the edit (Lipas's version) differs from PTV, which
   counts as something to send; the button now shows only when the user
   has unsaved edits.
3. **Fixed: "Muokattu viimeksi" was 2 hours off.** PTV's `modified` (UTC
   without a zone) was shown as local time; now converted, as the review's
   dates are (`subs/::services`).
4. **Open, not in this branch's Palvelut code: React key warning.** The
   filter bar's new chip tooltips (`views/filter-bar`) write
   `^{:key value} chip`, which tags the symbol, not the element; chips
   without a tooltip (all of the Palvelut tab's) render without keys.
   `(with-meta chip {:key value})` would fix it.
5. **To decide: the Nimi column shows PTV's name.** While a name changed in
   PTV is undecided, the table shows PTV's name (e.g. "Maauimalat ja
   uimarannat (e2e PTV)") and the open row Lipas's.
6. **Tooling:** the render patch must run each queued callback in a
   try/catch: an OpenLayers frame callback threw in the batch and stalled
   the renders (a row looked closed after a refetch; not reproducible with
   the fixed patch).

Environment left: p2, p4 and p5 written back to their baseline texts
(markers stripped) and p3 holds Lipas's summary from K2, all "Ajan
tasalla"; their mirrors have the run's revisions. Puistojumppa untouched.
No browser session left open.
