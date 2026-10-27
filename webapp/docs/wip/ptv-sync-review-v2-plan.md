# PTV sync review v2: one review for every overwrite

Status: **built 2026-10-26** (phases 1–6), branch `feat/ptv-drift-review`
(PR #272), which also carries #266. Designed 2026-10-23 on top of phases 1–4
of [`ptv-drift-review-plan.md`](ptv-drift-review-plan.md). Each phase below
ends with what was built and where it differs from the plan; the
integration as it now works is described in
[`../integration-ptv-architecture.md`](../integration-ptv-architecture.md).
Browser scenarios and their results:
[`ptv-sync-review-v2-browser-scenarios.md`](ptv-sync-review-v2-browser-scenarios.md).
Still open: the release-build check, publishing the help article, the
session with a municipal user (phase 4), and the rollout steps.

## Goal

Before Lipas writes over a PTV service location, the user sees everything the
write would change in PTV and decides about every change someone made in PTV.
This holds wherever the write starts:

| Entrance | Code today |
|---|---|
| "Synkronoi nyt" in the Liikuntapaikat tab | `ptv/events.cljs ::sync-with-review` |
| Käyttöönotto wizard: linking an existing location (first link), then export | `views.cljs service-location-details`, `::create-all-ptv-service-locations` |
| Site form save with PTV sync on | `map/events.cljs ::save-edits` → `core/save-sports-site!` → `sync-ptv!` |

There are no API writers (D3), so these three are all the content writes.

One component, one set of rules and one vocabulary replace the ad-hoc warnings
that grew around each entrance. The UI is written for municipal staff who
don't know how the integration works.

## What is wrong today (on the branch)

### 1. The review covers only part of what a sync overwrites

`->ptv-service-location` sends 13 kinds of data. The review compares 5 of them.

| PTV data | Lipas source | Reviewed? |
|---|---|---|
| Name fi/sv/en | `:name`, `:name-localized` | yes |
| Marketing name (AlternativeName fi) | `:marketing-name` | yes |
| Summary, description per language | `[:ptv :summary/:description]` | yes |
| Service connections | `[:ptv :service-ids]` | yes |
| Language versions added | effective languages | yes (info line) |
| **Language versions removed** | effective languages | **no**: a separate alert, no choice |
| **Phone** | `:phone-number` (first number, parsed) | **no** |
| **Email** | `:email` | **no** |
| **Web pages** | `:www`, `:reservations-link` (named per language) | **no** |
| **Visiting address** | `[:location :address]`, `:postal-code` | **no** |
| **Municipality** | `[:location :city :city-code]` | **no** |
| **Map point** | first point of the geometry, TM35FIN | **no** |
| Publishing status | `:status` | link state, not a value |

`docs/integration-ptv-architecture.md` ("What is *not* reviewed") accepts the
gap: municipalities rarely edit these fields in PTV. Today a phone number fixed
in PTV is overwritten without a word. Contacts and addresses are edited only
in the site form, so the PTV tool can't take PTV's value either. Names had the
same problem before `:site-names`.

### 2. Warnings about the same thing are spread over many places

From the inventory of the branch (file:line in the appendix):

- **Language removal.** `unpublished-languages-alert` is the only place that
  says a sync deletes PTV content, and it offers no action. It is not a review
  row.
- **Link state.** Each entrance shows it differently, or not at all:
  - archived location: a label in the tab; "Uusi palvelupaikka luodaan" in the
    site form, which is wrong because the backend re-publishes the same
    location;
  - unpublished changes in PTV ("Modified"): a blocker in the tab, dead code
    in the site form;
  - double link: an alert in the tab and the site form, silent in the wizard;
  - name conflict: tab and wizard;
  - name differs wholesale: nowhere.
- **Status chips.**
  - `:content-drift` reads "Muutettu PTV:ssä" also when nobody can tell who
    changed what (`:unknown`, `:first-link`).
  - The chip stays red after every row is decided.
  - `:out-of-date` ("Lipaksessa on uudempia muutoksia") also covers an archived
    location, a removed link and PTV's extra languages.
- **"Lataa tekstit PTV:stä"** (Liikuntapaikat tab) is a coarse duplicate of
  "Ota PTV:n versio". It replaces all summary and description maps, wipes
  Lipas-only languages, and leaves the review comparing against a stale
  channel.
- **Type change.** It re-binds the site's sub-category service at sync time
  (`core.clj` ~757). The review can't show this, because it compares the saved
  `:service-ids`, not what the sync will send.
- **Org settings.** Removing a language from the org deletes that language
  version from every location on their next syncs. Nothing says so.

### 3. Gaps in the flows

- **Editors without PTV rights.**
  - The site form fetches the review and the save gate asks them too, but the
    PTV tab is visible only with `:ptv/manage`. "Käy erot läpi" sends them to
    tab 6, which falls back to tab 0, so their only real choice is "Tallenna
    ja korvaa".
  - They also can't take PTV's texts: `[:ptv :summary]` is a PTV setting, so
    `check-ptv-save!` returns 403.
- **Freshness.** The PTV tool compares against the channels as they were when
  the dialog opened. Only admins see the refresh button.
- **No server-side check.** Open question 3 of the previous plan is still
  open. A change made in PTV after the review is overwritten.
- **Base derived again from the old revision.** The base is re-derived with
  today's `->ptv-service-location`. Extending the review to contacts makes
  this a false-alarm risk: when the derivation has changed since a site's last
  sync, a value PTV never touched reads as "Muutettu PTV:ssä" (see "Three
  ways"). PTV also rewrites what it stores (the street copied into 6
  languages, the postOffice in upper case, `serviceChargeType` on phones,
  full-precision coordinates), so values can't be compared raw.

## Design principles

1. **One review, every entrance.** The same panel, rules and words in the
   Liikuntapaikat tab, the wizard and the site form. The Palvelut tab follows
   once #266 lands.
2. **The review covers exactly what the sync writes, by construction.** The
   Lipas side is the payload the sync would send, run through the same
   projection as PTV's record. A test fails when the payload gains a key the
   review doesn't know.
3. **Ask about PTV's changes, report Lipas's, and stay quiet about noise.**
   - A difference needs a choice only when PTV changed it, or when nobody can
     tell who did.
   - Lipas's own changes are an informational list.
   - A difference that is only PTV's normalisation is not a difference.
4. **A choice is an edit (as built).** Taking PTV's version writes it into the
   site. Keeping Lipas's is recorded against the PTV value it was made against.
   The write carries the result.
5. **Nothing changed in PTV is overwritten unknowingly. The backend enforces
   this, not only the UI.**
6. **Only PTV managers write to PTV (D2).** A save by an editor without
   `:ptv/manage` for the site's municipality is a Lipas-only save. It never
   syncs, so it is never asked about PTV. The site then reads "Valmis
   synkronoitavaksi" in the PTV tool until a PTV manager syncs it.
7. **Plain language, consequences first.** Say what will happen ("poistuu
   PTV:stä", "muuttaa myös Lipaksen tiedon") before how it works.

Unchanged from the architecture doc: Lipas is the master of what it sends,
one-way write, and nothing is kept silently (principle 3 there). A PTV value
survives a sync only because a user chose it, and it then lives in Lipas.

## Model

### Projection

New cljc namespace `lipas.data.ptv-review`.

- **Move** `sync-review` and its helpers out of the 1951-line `ptv.cljc`.
- **Git:** `src/cljc/lipas/data/` matches a `.gitignore` rule, so the new file
  must be force-added.

`location-values` takes any ServiceLocation-shaped map: the payload or PTV's
GET. It returns comparable values keyed by review key:

```clojure
{[:name "fi"] "Utajärven pallokenttä"
 [:marketing-name "fi"] nil
 [:summary "fi"] "…" [:description "fi"] "…"
 [:phone] #{{:prefix "+358" :number "858755700" :service? false}}
 [:email] #{"tilavaraukset@utajarvi.fi"}
 [:web-page :www] #{"https://www.utajarvi.fi"}
 [:web-page :reservations-link] #{"https://utajarvi.asio.fi"}
 [:web-page :other] #{}                     ; PTV pages Lipas didn't name
 [:address] {:street "Ahmatie 4" :postal-code "91600"}
 [:municipality] "889"
 [:point] [7182203 472169]                  ; TM35FIN, whole metres
 [:languages] #{"fi" "en"}
 [:services] #{"0ebaa2ae-…"}}
```

It normalises the GET/PUT asymmetries that were measured:

- municipality is `{:code}` in the GET and a string in the payload;
- the street is read from the `fi` entry, plus `streetNumber` when PTV split it
  off (seen on non-Lipas locations, e.g. Vestia);
- coordinates are parsed to numbers;
- phones are compared as prefix + number;
- web pages are compared by URL. Their names are Lipas's fixed labels,
  unnamed before #269, and never make a row. A page is matched to a Lipas slot
  by name when it has one, otherwise by URL; anything else is `:other`;
- streets are compared ignoring case (PTV capitalises them);
- services are ids from the GET's `{:service {:id}}` entries.

Values hold every language's entry, so a Swedish phone number PTV has is
visible: it would be overwritten.

- **Lipas side:** `(location-values (->ptv-service-location … site))`.
  - The browser already builds this payload in the PTV tool
    (`ptv/subs.cljs ::service-location-preview`, using
    `projection/wgs84->epsg3067`), so no new dependency is needed.
  - The service ids are the ones the sync will send: after `link-service-ids`
    on a first link, and after the type-change re-bind. That re-bind moves
    from `core.clj` (~757) to cljc so the review shows it.
- **Coverage test:** every key of `->ptv-service-location`'s output is either
  read by `location-values` or listed in `not-reviewed-payload-keys` with a
  reason. Today those are `:organizationId`, `:sourceId`, `:displayNameType`,
  `:publishingStatus` and the `:deleteAll*` flags.

### Field registry

One cljc vector drives rows, labels, grouping, write-back, rights and tests.
It follows the pattern of the bulk-edit registry.

| Key | Group | Kind | Take PTV writes | Who may take |
|---|---|---|---|---|
| `[:name lang]` | Nimi ja kuvaukset | short | `:name` / `[:name-localized loc]` | site editor |
| `[:marketing-name "fi"]` | Nimi ja kuvaukset | short | `:marketing-name` | site editor |
| `[:summary lang]`, `[:description lang]` | Nimi ja kuvaukset | text (word diff) | `[:ptv :summary loc]` … | PTV manager |
| `[:phone]` | Yhteystiedot | one slot | `:phone-number`, formatted `+358 858755700` | site editor |
| `[:email]` | Yhteystiedot | one slot | `:email` | site editor |
| `[:web-page :www]` | Yhteystiedot | one slot | `:www` | site editor |
| `[:web-page :reservations-link]` | Yhteystiedot | one slot | `:reservations-link` | site editor |
| `[:web-page :other]` | Yhteystiedot | extra entries | into the `:www` or `:reservations-link` slot | site editor |
| `[:address]` | Osoite ja sijainti | composite | `[:location :address]`, `:postal-code`, `:postal-office` (PTV's fi postOffice, title-cased) | site editor |
| `[:municipality]` | Osoite ja sijainti | info | — (moving a site to another municipality is not a PTV decision) | — |
| `[:point]` | Osoite ja sijainti | distance | — (geometry is edited on the map) | — |
| `[:languages]` | Kieliversiot | languages | PTV's texts in that language → Lipas | PTV manager |
| `[:services]` | Liitetyt palvelut | set | `[:ptv :service-ids]` (as built) | PTV manager |

**One-slot kind.** Lipas holds one value; PTV may hold several (another
language, a second number added in PTV).

- The PTV card lists each PTV value with its own "Ota tämä" button.
- The result is always one value.
- Keeping Lipas's removes the others from PTV, and the card says so.

**One-slot note (D5).** Every one-slot card says so: "Lipaksessa voi olla vain
yksi osoite" (and likewise for phone, email and each web page). The address is
one value in Lipas and is sent for every language, so a Swedish street name
PTV has is one of the choices on the PTV card, not a separate row.

**Point and municipality (D4): information, never a choice.** PTV holds only a
point, and Lipas can't build a route or an area from it.

- The point row appears only beyond 50 m. On prod, no point differs by
  1–50 m; three differ by more.
- For routes and areas, Lipas sends the first coordinate of the geometry
  (`search-meta` `wgs84-point`), so PTV's point can legitimately sit
  elsewhere.
- Text: "PTV:n koordinaatit ({n} m päässä) korvautuvat Lipaksen sijainnilla."
  PTV mostly geocodes its point from the address, so Lipas's geometry is the
  better one.
- These rows don't count as open choices and never block a sync.

### Three ways, with evidence of a PTV change

Per key there are three values:

- `L` — Lipas now (projected payload);
- `P` — PTV now (projected GET);
- `L₀` — what the last sync sent (projected base, below).

PTV's own rewriting (the street in six languages, the postOffice, the phone
charge type, full-precision coordinates) never reaches the comparison: the
projection reads only the parts Lipas controls.

| L ≠ P? | PTV changed (P ≠ L₀, with evidence) | Lipas changed (L ≠ L₀) | Row |
|---|---|---|---|
| no | – | – | none |
| yes | no | any | Päivittyy PTV:hen: report, no choice |
| yes | yes | no | Muutettu PTV:ssä: choice |
| yes | yes | yes | Muutettu molemmissa: choice |

**Where `L₀`/`P₀` come from (D1): the sync's own revision, as built.** No new
storage. The base is the revision the last sync wrote, run through today's
derivation: `L₀ ≈ P₀ ≈ derive(base revision)`. The point needs `enrich`, so
the backend derives the base server-side in `get-ptv-sync-bases`.

The derivation has changed since many sites were last synced:

| Date | Change |
|---|---|
| 2026-05-07 | Phone parser rewritten (50e8271ab) |
| 2026-05-14 | Parsers extended for 282 sites with unusual data (c33057a65) |
| 2026-10-14 | Web page names; emails rejected as web pages (ed22cc28d) |

For a site last synced before one of these, `derive(base)` differs from what
PTV actually got, even though nobody touched PTV. So a row is `:ptv` only with
evidence that PTV changed:

- **Values:** PTV's `:modified` differs from the one PTV had right after
  Lipas's last sync. The sync stores that value from the post-sync GET it
  already does, as a new server-owned key `[:ptv :ptv-modified]` (added to
  `server-owned-ptv-keys`).
  - `:ptv` is `dynamic false` in the analytics mapping, so the key needs no
    reindex.
  - **Sites synced before the key exists** have no `:ptv-modified`. Every
    reader treats the key as optional:
    - The schema gets `[:ptv-modified {:optional true} :string]`. `ptv-meta`
      is a closed map, so without this a round-tripped site fails the save
      schema.
    - These sites compare `:modified` with `:last-sync` + a margin. A PTV
      write normally takes 1–5 s, so 2 min is plenty.
    - No migration is needed: the key appears on each site's next sync. A fixed margin is fragile, which is why the key is worth adding:
    in the test env our own sync left `:modified` 2 min 15 s after
    `:last-sync`, because `:last-sync` is taken before the PUT. The other way
    round, a margin misses PTV edits made soon after a sync: all of the local
    demo scenarios (PTV edited seconds after the sync) read as "no evidence".
- **Connections:** always the base. Connections are raw ids and their
  derivation never changed. Connecting a service from the service side does
  **not** change the location's `:modified` (verified in the test env,
  2026-10-23).

Without evidence, a difference is Lipas's: it is listed under "Päivittyy
PTV:hen", and the sync sends it.

A residual case: on a site that *was* edited in PTV, a derivation-only
difference in another field also reads as PTV's. Phase 0 counts these. A table
storing PTV's post-sync GET would remove them; it is worth adding only if
phase 0 finds enough of them.

**First link** (no `L₀`/`P₀`): as built. Every difference is "Eroaa".

- Values need a choice.
- Connections are kept (`link-service-ids`) and shown.
- Language versions PTV has and Lipas doesn't are listed for information, as on synced sites (D6).

### Choices and writes

- **As built.** A choice writes into the edit. Keep-Lipas is recorded as
  `{:seen <PTV value>}`. A choice no longer counts once PTV's value changes.
- **`:site-names` becomes `:site-patch`.**
  - Paths: `site-patch-paths` in cljc, the registry's site-editor paths.
  - Backend: rejects any other path (400), validates values with the site
    schema, and requires `:site/create-edit` (403, as for names).
  - The PTV tool and the wizard send it. The site form edits the fields
    directly.
  - The rename is safe: `:site-names` isn't in prod.
- **Language row (D6): information, not a choice.**
  - It never blocks a sync.
  - When the org has the language enabled, it also offers "Ota PTV:n
    tekstit". That writes the language's name, summary and description into
    the site, so the language stays.
- **Wizard and PTV tool.** Taking a site field needs the right to edit that
  site. This generalises `names-takeable?` to a per-row `:takeable?` from the
  registry plus the user's rights.

### Stale-review check (phase 5)

There are no API writers (D3) and editors without PTV rights don't sync (D2),
so every write comes from a PTV manager who saw the review. What remains is a
review that went stale: the PTV tool compares against the channels as they
were when the dialog opened, and someone edits the location in PTV
meanwhile. Every content write to an existing location goes through
`upsert-ptv-service-location!*`, which already GETs the location first, so
the check costs no extra PTV call.

- **What the client sends:** `:reviewed {:modified <PTV's :modified> :service-ids
  [...]}` of the channel its review was made against. The service ids are
  included because a connection made from the service side doesn't change
  `:modified` (verified).
- **What the server does:** if the fresh GET has a newer `:modified` or other
  connections, it returns 409 `{:type :ptv-changed-since-review}` and writes
  nothing.
- **PTV tool and wizard:** the UI refetches the channel, opens the review and
  says "PTV:ssä on muutettu tietoja sen jälkeen, kun avasit näkymän. Tarkista
  muutokset ja synkronoi uudelleen."
- **Site-form save:** the Lipas revision is saved, the sync is skipped, and
  `:ptv :error {:type "ptv-changed-since-review"}` is stored. That is the
  existing way a failed sync is recorded. The existing "saved but sync
  failed" toast gets a body for it.
- **No `:reviewed`** (an old browser tab, the archive path) means no check:
  today's behaviour. The status-only archive writes no content anyway.

The full per-row check considered earlier (the server recomputing the review
and requiring an acknowledgement per row) protected writers without the UI.
With none, it isn't worth its complexity; revisit if API writers appear.

## UX

### Words

- One noun for the whole thing: **"Muutokset PTV:ssä"**. The site form tab
  badge, the chip, the bar and the dialog all lead to it.
- Fixed verbs everywhere:

| Situation | Verbs |
|---|---|
| Value rows | Pidä Lipaksen versio / Ota PTV:n versio / Ota tämä |
| Language rows | Kopioi teksti · Ota PTV:n tekstit (when the org has the language) |
| Connections | Pidä liitos / Poista liitos |
| Bulk | Ota kaikki PTV:n muutokset / Pidä Lipaksen versio kaikissa / Kumoa valinnat |

### Panel anatomy

From top to bottom. The panel is collapsed by default (as built).

```
┌ ⚠ PTV:ssä on muutettu 3 tietoa – valitse, mitkä jäävät voimaan.  [Näytä ▾] ┐
│   Synkronoitu viimeksi 12.10.2026 · muokattu PTV:ssä 20.10.2026            │
└────────────────────────────────────────────────────────────────────────────┘
  ① Palvelupaikan tila (only when something is wrong; see below)

  ② Valitse, mikä jää voimaan                                   1 / 3 valittu
  ── Nimi ja kuvaukset ────────────────────────────────────────────────────
  Tiivistelmä (FI)                                          Muutettu PTV:ssä
  ┌ Lipas ─────────────────────────┐ ┌ PTV ───────────────────────────────┐
  │ …nurmikenttä keskustassa.      │ │ …nurmi- ja tekonurmikenttä         │
  │                                │ │ keskustassa.                       │
  │ [Pidä Lipaksen versio]         │ │ [Ota PTV:n versio]                 │
  └────────────────────────────────┘ └────────────────────────────────────┘
  ✓ Kuvaus (FI): PTV:n versio otetaan Lipakseen                    [Muuta]
  ── Yhteystiedot ─────────────────────────────────────────────────────────
  Puhelinnumero                                             Muutettu PTV:ssä
  ┌ Lipas ─────────────────────────┐ ┌ PTV ───────────────────────────────┐
  │ 08 5875 5700                   │ │ +358 40 123 4567       [Ota tämä]  │
  │ [Pidä Lipaksen versio]         │ │ +358 8 5875 5700 (SV)  [Ota tämä]  │
  │ PTV:n muut numerot poistuvat.  │ │ Muuttaa myös Lipaksen puhelinnumeron│
  └────────────────────────────────┘ └────────────────────────────────────┘
  ── Kieliversiot ─────────────────────────────────────────────────────────
  Ruotsi (SV)                                          Poistuu PTV:stä
    Synkronointi poistaa PTV:stä ruotsinkielisen kieliversion: nimen,
    tiivistelmän ja kuvauksen. Ruotsi ei ole käytössä organisaatiollanne
    Lipaksessa.
    ▸ Näytä poistuvat tekstit               (each with [Kopioi])
    Jos haluat säilyttää kieliversion, pyydä Lipaksen tukea ottamaan ruotsi
    käyttöön (lipasinfo@jyu.fi) ennen synkronointia. Voit myös kopioida
    tekstit talteen tästä.

  ③ Päivittyy PTV:hen Lipaksesta (2) ▸        (collapsed while ② has open rows)
      Verkkosivu: www.utajarvi.fi → www.utajarvi.fi/liikunta
      Lipas lisää kieliversion EN

  ④ Ajan tasalla: nimi, sähköposti, käyntiosoite, liitetyt palvelut.

  [Ota kaikki PTV:n muutokset] [Pidä Lipaksen versio kaikissa] [Kumoa valinnat]

  ⑤ ┌ Viedään PTV:hen: Tiivistelmä (FI), Verkkosivu, kieliversio EN       ┐
     │ Kopioidaan Lipakseen: Kuvaus (FI)                                  │
     │ Valitse vielä: Puhelinnumero, Ruotsi (SV)                          │
     │ Valinnat tulevat voimaan, kun synkronoit (Synkronoi nyt).          │
     └────────────────────────────────────────────────────────────────────┘
```

**Bar.** One sentence that says what to do, and one count.

| State | Severity | Text |
|---|---|---|
| Choices open | warning | "PTV:ssä on muutettu {n} tietoa – valitse, mitkä jäävät voimaan." |
| First link | warning | "Palvelupaikan tiedot PTV:ssä eroavat Lipaksesta {n} kohdassa – valitse, mitkä jäävät voimaan." |
| No base | warning | "Lipas ja PTV eroavat {n} kohdassa – valitse, mitkä jäävät voimaan." |
| All decided | success | "Valinnat tehty. Synkronointi päivittää PTV:hen {n} tietoa." |
| Lipas changes only | info | "Synkronointi päivittää PTV:hen {n} tietoa." |

When the sync would remove a language version, every bar text ends with it,
e.g. "… ja poistaa ruotsinkielisen kieliversion." A removal must be visible
without opening the panel (D6), and the gate dialog and the outcome footer
list it too.

- The second line gives dates, which tell the user who changed what and when.
  PTV's `:modified` applies to the whole location.
- The red conflict colour moves off the bar. A conflict is still marked on its
  row, as "Muutettu molemmissa" with a tint. A municipal user can't act any
  differently on red than on amber.

**① Palvelupaikan tila.** The link-state notices that are scattered today,
with the same texts and actions everywhere.

| State | Text (short) | Actions |
|---|---|---|
| Archived/deleted in PTV | "Palvelupaikka on arkistoitu PTV:ssä." | Julkaise uudelleen (= sync) · Irrota liitos · Pidä arkistoituna (sync off) |
| Unpublished changes in PTV (`Modified`) | "PTV:ssä on julkaisemattomia muutoksia. Julkaise tai peru ne PTV:ssä ennen synkronointia." | Avaa PTV:ssä (blocks the sync) |

A `Modified` draft also arises when PTV itself fails mid-write. On 2026-10-23
a PTV 502 left a draft on prod site 100643, and on test site 615340.
Afterwards the API refuses every update ("You cannot update entity with status
Modified"), also one sent with `currentPublishingStatus: Modified`. Only PTV's
UI can publish or discard the draft. So:

- the sync-failure toast for a 5xx says to check the location in PTV for a
  leftover draft;
- a 400 with that message maps to this notice, not to the generic "sync
  failed".
| Double link | "Palvelupaikka on liitetty myös liikuntapaikkaan X." | Avaa X (blocks) |
| Name conflict (not linked) | "PTV:ssä on jo palvelupaikka nimellä X." | Liitä tähän palvelupaikkaan (→ first-link review) |
| Names differ wholesale | "Nimet eroavat kokonaan. Onko liitos oikea?" | Vaihda palvelupaikka |
| PTV not reachable | "PTV:n tietoja ei saatu haettua." | Yritä uudelleen (the stale-review check still protects the write) |
| Removed in Lipas, still published in PTV (D2) | "Liikuntapaikka on merkitty Lipaksessa poistetuksi ({status}, {pvm}). Palvelupaikka näkyy yhä PTV:ssä." | Arkistoi PTV:ssä (status-only archive) · Avaa liikuntapaikka |

**② Decisions.**

- Rows are grouped (Nimi ja kuvaukset, Yhteystiedot, Osoite ja sijainti,
  Kieliversiot, Liitetyt palvelut), and only the groups that have rows are
  shown.
- The counter "k / n valittu" shows progress. Rows that are information
  only (language removal, point, municipality) sit in their group for
  context but don't count.
- A decided row collapses to one line ("✓ Kuvaus (FI): PTV:n versio otetaan
  Lipakseen [Muuta]"), so the remaining work stays in sight. The "Lomakkeessa"
  marker stays on the expanded card.
- Long texts get the word diff (as built). Short values (names, contacts,
  address) are shown side by side without a diff.
- A card states the consequence when there is one:
  - "Muuttaa myös Lipaksen puhelinnumeron" (generalises the name caption);
  - "PTV:n muut numerot poistuvat";
  - "Synkronointi siirtää PTV:n pisteen takaisin Lipaksen sijaintiin".
- A row that can't be taken says why and where to fix it: "Sijaintia ei voi
  kopioida PTV:stä. Jos PTV:n sijainti on oikea, siirrä liikuntapaikkaa
  kartalla."

**③ Lipas's changes.** These were mixed in with the decisions until now.

- One line each, with the old value → new value for short values.
- "Kumoa" is in the line's menu, for the rare case.
- Language additions go here.

**④, the bulk buttons and ⑤** are as built. The bulk buttons sit after the
rows, and "Kumoa valinnat" is among them.

### Gate dialog (all three entrances)

```
PTV:ssä on muutoksia, joita et ole käynyt läpi
Jos jatkat, nämä PTV:ssä tehdyt muutokset korvautuvat Lipaksen tiedoilla:
  • Puhelinnumero: +358 40 123 4567 → 08 5875 5700
  • Tiivistelmä (FI)
  • Ruotsinkielinen kieliversio poistuu PTV:stä
                     [Peruuta]  [Korvaa PTV:n muutokset]  [Käy muutokset läpi]
```

- The safe action is now the primary one. Today "…ja korvaa" is the confirm
  button.
- Short values show both sides. Texts show only the field name.
- "Käy muutokset läpi" opens the panel, scrolls to it and moves focus to the
  first open row.

### Entrances

- **Liikuntapaikat tab.**
  - Status chip per row:

    | Chip | When |
    |---|---|
    | Odottaa valintaa | amber; choices open |
    | Valmis synkronoitavaksi | blue; differences, all decided or Lipas-only |
    | Ajan tasalla | green |
    | Ei viety | grey |
    | Estetty | red; a ① blocker or a sync error |

    These replace `:content-drift` / `:out-of-date` (FE-only values, free to
    rename).
  - A banner over the table: "{n} liikuntapaikassa on PTV:ssä tehtyjä
    muutoksia, jotka odottavat valintaa. [Näytä ne]". The button applies the
    existing filter.
  - Remove "Lataa tekstit PTV:stä" and `::load-ptv-texts`.
  - Opening a row refetches that location (one GET), so the review isn't as
    old as the dialog.
- **Wizard step 3.** As built: the panel per site, the export gate and bulk
  actions over all sites. Remove the separate unpublished-languages alert,
  which becomes language rows. Render the double-link block, which is silent
  there today.
- **Site form, PTV manager.**
  - As built: the review on the PTV tab, the badge, and the save gate (with
    the new dialog).
  - Contact and address rows write the basic-data fields. The card caption
    names the tab: "Muuttaa myös Lipaksen puhelinnumeron (Perustiedot)".
  - Fix the status alerts: "Uusi palvelupaikka luodaan" must not show for an
    archived location, and the "Modified" alert reads the fetched channel.
- **Site form, editor without PTV rights (D2).** A Lipas-only save: no
  review, no dialog, no sync. The backend decides this, not the UI:
  `save-sports-site!` syncs only when the user has `:ptv/manage` for the
  site's municipality. This also fixes the dead-end described under "Gaps in
  the flows".
  - The site then waits in the PTV tool as "Valmis synkronoitavaksi". PTV
    managers need a way to clear the backlog without opening each row: a
    "Synkronoi kaikki valmiit ({n})" action in the Liikuntapaikat tab. It
    runs through the same gate: sites with open choices are skipped and
    listed.
  - **Archiving included (D2):** nothing an editor without PTV rights does
    reaches PTV. A removal (incorrect-data, permanently out of service,
    delete) is stored in Lipas only.
    - The site shows in the PTV tool as "Odottaa arkistointia", with the ①
      notice above. A PTV manager acknowledges it with "Arkistoi PTV:ssä".
    - The tool lists today only `active` / `out-of-service-temporarily`
      sites (`get-eligible-sites`), and `:ptv` isn't indexed in the search
      index (`:enabled false`). So these sites come from a DB query:
      `[:ptv :publishing-status] = "Published"` and no longer a candidate.
      That is the same rule as the adoption view's
      `:ptv/published-not-candidate`, which also covers owner and type
      changes.
  - Texts that say a save or delete touches PTV must follow the user's
    rights. The delete dialog says "Palvelupaikka arkistoidaan PTV:ssä, kun
    PTV-vastaava hyväksyy sen" instead of "Poistaminen arkistoi myös
    PTV-palvelupaikan".
- **Org settings.** Removing a language warns: "Seuraava synkronointi poistaa
  {kieli}-kieliversion {n} palvelupaikasta."

### Accessibility and layout

- Diff highlights have a non-colour cue (`<mark>` + dotted underline) and a
  legend line: "Korostettu: sanat, jotka ovat vain tässä versiossa".
- Choice buttons carry `aria-pressed` and an accessible name that includes the
  field, e.g. "Ota PTV:n versio: Tiivistelmä (FI)".
- Focus moves on "Käy muutokset läpi".
- Cards stack below the `md` breakpoint and in the site form (as built,
  `:stacked?`).
- A help article "PTV-muutosten läpikäynti" in the KB, linked from the
  panel's intro.

## Consolidation: what goes away or moves

| Today | v2 |
|---|---|
| `unpublished-languages-alert` (`components.cljs:78`, tab, wizard, site form) | language rows in ② |
| "Lataa tekstit PTV:stä" (`views.cljs:499`, `events.cljs:1852`) | removed (rows + "Ota kaikki PTV:n muutokset") |
| Archived label, `new-service-location-will-be-created` for archived sites, `modified-in-ptv` (tab, site form dead code `site_view.cljs:503`) | ① |
| Double-link alerts (tab, site form; wizard silent) | ① |
| Name-conflict alert (tab, wizard) | ① |
| Type-change re-bind alerts (`site_view.cljs:442`) | connection rows (re-bind in cljc) |
| Status chips `:content-drift`/`:out-of-date`, filter "Muuttunut" | new chip set + "Odottaa valintaa" filter |
| `:ptv-texts`, `service-channel-compare-fields`, stale "drift" comments, `choices-path` docstring (`:merge`), unused i18n keys (appendix) | deleted |
| `sync-status-languages-test` asserting `:content-drift` without a base | rewritten for the new statuses |

The following stay as they are, because they are not about Lipas and PTV
being out of step: save blockers (`sync-blockers`), archive and sync-off
confirmations, the delete dialog's archive note, and the sync-failure toasts.
The toasts gain bodies for 403 (`:no-permission`), 409 double link and 409
review required.

## Implementation plan

Each phase is one or more commits on `feat/ptv-drift-review` (#272 isn't
merged). Phases 1–2 are refactors under green tests, so the PR stays
reviewable. Sizes: S ≤ 1 day, M 2–3 days, L ~1 week.

### Phase 0: ground truth (done 2026-10-23)

Script: `webapp/dev/user/ptv_review_measure.clj` (`run-all!`, `summary`,
`d2-impact`, `d2-drilldown`). It is read-only: a scratch namespace, SELECTs,
and PTV list GETs. It was dry-run locally on Utajärvi, where it gave the
expected rows for every demo scenario.

**Prod (tunnel 7889, read-only), 1268 synced sites in 47 PTV orgs:**

| What | Result |
|---|---|
| Base revision found | 1268 / 1268. Open question 2 of the previous plan is settled on prod |
| Sites differing at all | 301 with the refined comparison (1015 before, see the noise rows below) |
| Real PTV-side changes (evidence, or connections) | **83 sites (6.5 %)** |
| — connections | 50 sites (Keuruu 16, Juuka 12, Pyhäjärvi 9, …) |
| — phone / email | 20 / 21 sites. Liperi removed the generic kirjaamo phone and email from 19 locations the day after the sync |
| — web pages | 34 sites (Liperi 17, Pudasjärvi 8, …): pages replaced with other URLs and their own names, e.g. "Skeittiparkit", "Saimaa by Cycle -verkkosivu" |
| — summary / description | 8 (Pudasjärvi 4, Liperi 3, Raahe 1) |
| — name / marketing name | 2 / 1 |
| — street / point | 1 / 2 |
| Lipas-only differences | 218 sites, e.g. Uusikaupunki EN (42) and texts edited since the last sync (85 rows) |
| Language versions only in PTV | 0 |
| Several phones / emails / addresses in PTV; per-language streets | 0 / 0 / 0; 0 |
| Point distances over 1 m | 3 sites: 2 at 50–200 m, 1 at 200 m–1 km. **None between 1 and 50 m**, so a 50 m threshold is safe |
| `:modified` later than last-sync + 10 min | 55 sites; 12 of them with no difference in the fields we compare |
| D2 impact: site-form saves of synced sites in the last 365 days | 495 by users with `:ptv/manage` for the site's city, **9 by others** (7 sites, 4 users; 8 of the 9 changed the location, 1 the status) |

**Normalisation the projection must do:**

- **Web pages: compare URLs only.**
  - Page names are Lipas's fixed labels. Before #269 the sync sent no names
    and no booking-link page.
  - Comparing slots by name made **930 sites** differ on web pages, none of
    them edited in PTV.
  - Even by URL, 188 sites differ without evidence. Causes: the booking link
    added by #269, and `http://` → `https://` from `parse-www`.
  - Names never make a row. A slot is matched by name when the page has one,
    otherwise by URL.
- **Streets: ignore case.** PTV capitalises them on save ("praavantie 345" →
  "Praavantie 345", "ruNKOTIE 96" → "Runkotie 96"), which made 19 false rows.

**What this shows:**

- D2 changes almost nothing in practice: about 9 saves a year would wait for
  a PTV manager instead of syncing. When checking rights against stored rows,
  note two traps: org roles are derived at login (`org/enrich-org-roles`), and
  `sports_site.city_code` is text while role contexts hold numbers.

- The evidence rule is essential. Without it, the derivation changes would
  show about 220 sites as "Muutettu PTV:ssä" even when URLs are compared, and
  1000 when they aren't.
- Contacts are the second-largest real case after connections, so extending
  the review to them is justified.
- Every multi-value case is hypothetical on today's data. The one-slot
  pattern stays (it costs little), but needs no polish beyond that.

**Test env (Utajärvi):**

- Connecting a service from the service side does not change the location's
  `:modified`.
- Lipas removes a language version the org hasn't enabled. A Swedish name,
  summary and description were PUT into 615340 as a PTV user would add them;
  after an ordinary Lipas sync PTV had fi/en only.
- `:modified` is UTC: within 1–5 s of our UTC `:last-sync` on a normal sync.

**Fields Lipas doesn't send (test env, 615340, 2026-10-23).** Opening hours
and a postal address were added as a PTV user would, then an ordinary Lipas
sync ran:

| PTV data | After the Lipas sync |
|---|---|
| Opening hours (`serviceHours`) | **Kept**. Lists with a `deleteAll*` flag in the PUT schema are kept when left out |
| Postal address (second entry in `addresses`) | **Deleted**. `addresses` is replaced as a whole by Lipas's single visiting address |

The test location was cleaned up afterwards. A fax number didn't show in the
GET at all, so it couldn't be checked.

**How PTV's own UI stores addresses (prod, 120 random locations, public
read API):**

- The street name and number are separate fields: `street` "Kotkantie" +
  `streetNumber` "41" in 134 of 137 addresses. Lipas sends "Kotkantie 41" as
  the street with no number.
- The street is translated per language. In 54 of 137 the Swedish street name
  differs from the Finnish one; Lipas sends the Finnish one for every
  language.
- Coordinates are geocoded by PTV (`coordinateState` "Ok", 85) or entered by
  the user ("EnteredByUser", 51).
- There is per-language extra text, e.g. "1. krs.".
- **39 of 120 locations also have a postal address** (PO box or street).
- PTV rejects a street address that has neither a street number nor
  coordinates.

**Consequences:**

- The projection compares the `fi` street + `streetNumber` with Lipas's
  address (whitespace and case normalised).
- Taking PTV's address writes "street number" into `[:location :address]`.
- The address row says what a sync replaces: a Swedish street name and the
  extra text.
- A postal address in PTV is deleted by every Lipas sync today. That
  matters most on a first link from the wizard, since about a quarter of
  PTV-made locations have one (106 of 400 sampled). On synced sites there are
  none left. Decided in D8: the sync keeps them.

**Keeping postal addresses (D8), checked 2026-10-23:**

- Prod sample: 71 postal street addresses and 35 PO boxes in 400 locations.
  - 68 of the 71 street addresses carry coordinates.
  - 2 of the 71 have neither a street number nor coordinates ("Liikekuja 3"
    with an empty `streetNumber`). PTV's API rejects such an address.
  - Nearly all carry entries in languages the location doesn't publish. PTV
    copies the street into six languages and adds "PB" / "P.O.Box" next to
    "PL".
- Test env (615340, languages fi+en):
  - Sending an address entry in a language the location doesn't have fails
    the whole write: PTV wants a full new language version.
  - Sending the postal addresses back from the pre-sync GET, kept to the
    payload's languages, gives **identical** postal addresses afterwards. PTV
    regenerates its own copies in the other languages.

### Phase 1: projection and registry (built 2026-10-23, uncommitted)

- **New cljc namespace `lipas.data.ptv-review`.** The review code moved there
  from `lipas.data.ptv` (1951 → 1456 lines). So did `sports-site->ptv-input`,
  whose sync status follows the review; leaving it behind would make the two
  namespaces depend on each other. Callers were updated. In the map module
  the alias is `ptv-sync`, because `ptv-review` already names
  `lipas.ui.ptv.review-data` there.
- **Comparing payloads.** `site->location` builds the payload a sync of the
  site sends (`->ptv-service-location`, no coordinates yet).
  `location-values` turns a payload or PTV's GET into values by review key.
  `sync-review` compares those for the saved site, the base and PTV. Rows
  keep their shape and gain `:group`, `:kind` and `:takeable-by` from the
  registry. The review also returns `:langs`, which `annotate-review` uses to
  project the edit once.
- **Text rows follow the payload's published languages.** A language PTV has
  but the sync won't publish gets no text-by-text rows; the language rows of
  phase 3 cover that. The old code compared a Finnish fallback that is never
  sent, and two tests asserted that; they were corrected.
- **The base carries more.** `->review-base` keeps every site path a sync
  derives from (names, contacts, address, municipality; no geometry), so
  phase 3 has a base for its rows. `review-saved-site` overlays the same
  paths.
- **Registry.** `review-fields` (`:field :group :kind :languages
  :takeable-by :reads`) and `not-reviewed-payload-keys` with reasons
  (contacts and address "not yet reviewed").
- **Removed:** the `println`/`prn` of every synced site's `:ptv` in
  `->ptv-service-location` (it reached the prod logs), and the unused
  `service-channel-compare-fields` and `:ptv-texts`.
- **Performance.** Instead of per-site subscriptions, `::sports-sites` keeps
  a per-site cache: a site's row is reused while its inputs are the same
  values (site, audit, base, channels, services; settings by `=`).
  - The edit's review runs only when the edit differs from the saved site.
  - Measured in the browser (dev build): **42 µs per payload projection**,
    so about 40 ms on first load for an org with 300 synced sites. Only
    sites with a linked location are reviewed.
- **Tests:**
  - `payload-coverage-test`: every payload key is reviewed or explained,
    and nothing is both.
  - `location-values-test`: payload and GET shapes give the same values;
    the placeholder is no text.
  - `ptv-review-projection-test` (clj): fixtures recorded from the test
    env, `test/resources/ptv_review/synced_*.edn`. What the sync sent and
    what PTV returns project equal; no rows; a PTV edit is `:ptv`.
  - Two properties over every text field: who changed it; taking PTV's
    version leaves no difference.
  - `sync-review-test`: base keys now "what the payload derives from, no
    geometry".
  - Results: cljc review + payload tests (53/544 in the REPL), backend PTV
    namespaces (80 tests), and `bb check` are green.
- **Browser (local, Utajärvi):**
  - The site form review for 89913 shows "2 kohdassa"; taking PTV's
    summary marks it "Lomakkeessa".
  - In the PTV tool, the "Muutettu PTV:ssä" filter lists exactly the five
    PTV-changed scenarios. 79800 shows its 4 rows. Taking a name updates the
    table chip and the outcome lines, and "Kumoa valinnat" reverts it.
  - No console errors.
- **Not run locally:** the release build and `bb bundle-size-check`, because
  `npm run build` would overwrite the running dev watch's output. CI checks
  them.
- **Moved to phase 3:** the type-change service re-bind. It needs the org's
  PTV services, which the site form doesn't load, so it isn't a refactor.

### Phase 2: base for every field, and evidence (built 2026-10-26)

- The base already carries names, contacts and address (phase 1). What's
  missing is the point: `get-ptv-sync-bases` adds it, derived server-side
  with `enrich`. The PTV tool's cached sites have it in `search-meta`.
- `[:ptv :ptv-modified]` stored by the sync (optional in `ptv-meta`, added to
  `server-owned-ptv-keys`); `:modified` evidence with the `:last-sync` +
  2 min fallback; connections always on the base.
- Rule for any new `:ptv` key: optional in the schema, and every reader
  tolerates it missing. Otherwise a migration patches existing revisions.
  Test: the review and save paths with a `:ptv` that lacks the key.
- Deploy: no migration, no reindex.

**As built** (commit 02e5bb09f, with phase 3's data and backend): as
planned. The base's point comes from `enrich`; every sync stores
`[:ptv :ptv-modified]`, PTV's `:modified` right after the write. A value
counts as changed in PTV only when PTV's `:modified` differs from it (sites
synced before: `:last-sync` + 2 min), so a change in how Lipas derives the
payload (http→https, #269's booking link) reads as Lipas's. Connections need
no evidence: connecting from the service side doesn't move `:modified`.

### Phase 3: new rows and write-back (built 2026-10-26)

**Type-change service re-bind (moved here from phase 1):** move the re-bind
in `sync-ptv!` (`core.clj` ~757) to cljc, so the review shows the
connections a type change adds and removes. It needs the org's PTV services
(source-id → service): the PTV tool has them; the site form's review fetch
must return them.

**Postal addresses (D8):**

- `upsert-ptv-service-location!*` already GETs the location before writing.
  Its non-visiting addresses are converted to the PUT input shape by a cljc
  function next to `->ptv-service-location`, and appended to `:addresses`.
- The conversion:
  - municipality object → code;
  - drop blank entries and an empty `streetNumber`;
  - keep only the payload's languages;
  - PO box: `postOfficeBox`, `postalCode`, `municipality`,
    `additionalInformation`;
  - street: `street`, `streetNumber`, `postalCode`, `municipality`,
    coordinates, `additionalInformation`.
- An address PTV's API would reject can't be sent back and drops out:
  - a street address with neither a number nor coordinates;
  - a PO box without a box or a postal code;
  - any other sub-type (Abroad, Other; none seen among prod postal
    addresses).

  The check runs before the write, so a sync never fails on PTV's own data.
  The review shows such an address as information: "Postiosoite poistuu
  PTV:stä, koska PTV ei hyväksy sitä rajapinnan kautta – korjaa se PTV:ssä."
- Postal addresses are not review rows: Lipas doesn't manage them. The panel
  line becomes "Säilyvät PTV:ssä: aukioloajat, postiosoite."
- Tests:
  - cljc conversion tests from recorded GET fixtures: a street address, a PO
    box, an empty number, extra languages, a rejected address;
  - the sync payload carries them;
  - test-env scenario s16: a postal address added in PTV survives a sync.

- One-slot rows (phone, email, both web pages, other pages), address,
  municipality, point, and language-removal rows.
- `:site-patch` (schema, whitelist, 403/400) replaces `:site-names`.
- Remove `unpublished-languages-alert`.
- i18n fi/se/en.
- New demo scenarios in `dev/user/ptv_drift_scenarios.clj`:
  - s10 phone changed in PTV;
  - s11 second phone and an extra web page;
  - s12 address changed (and re-geocoded);
  - s13 point moved;
  - s14 SV version added in PTV;
  - s15 first link to a bilingual location.

**As built** (02e5bb09f data and backend, cd81d2b21 UI):

- All 13 kinds of payload data are reviewed. Phone, email and both web
  pages are one-slot rows; PTV's other web pages can be taken into a slot.
  Pages match by Lipas's names in any language, else by URL; emails and
  street names compare case-insensitively, with PTV's split street number
  joined.
- Municipality and point are information (D4). The point only shows beyond
  50 m, computed with a new cljc TM35FIN projection (`lipas.tm35fin`, within
  1 mm of proj4 over Finland).
- The type-change swap moved from `sync-ptv!` to cljc
  (`ptv-data/rebound-service-ids`) and runs in the shared upsert, so the PTV
  tool's sync swaps too and the review shows it. A missing service for the
  new sub-category is the `:ptv/missing-service` blocker (409). The site
  form's review fetch returns the org's LIPAS services from the
  `ptv_service` mirror.
- `:site-patch` takes the paths in `site-patch-paths`, validated with the
  site's own field schemas; anything else is a 400, and required fields are
  never removed.
- Postal addresses as planned (D8). Scenarios s10–s16 are in
  `dev/user/ptv_drift_scenarios.clj` (s16 is the postal address).
- **Fixed after the build:** a one-slot row where PTV kept Lipas's value and
  added another counted as decided, because the edit held one of PTV's
  values. A sync would have dropped PTV's addition without asking. The saved
  value now leaves the row undecided; taking Lipas's own value from PTV's
  list records "Pidä Lipaksen versio" (`ptv-review/lipas-value?`).

### Phase 4: panel UX and consolidation (built 2026-10-26)

- Bar texts, ① state notices, grouped ② with progress and collapsed decided
  rows, ③ Lipas changes, the new gate dialog, the status chips, the tab banner
  and refetch on open.
- Remove "Lataa tekstit PTV:stä".
- Site-form fixes (archived, Modified).
- The org-settings language warning.
- Accessibility items and the help article.
- Delete the leftovers listed above.
- Before merging, a session with one real municipal user (lipas-dev,
  Utajärvi) watching them use it.

**As built** (cd81d2b21), as planned, with these differences:

- Moving the focus or scrolling to the panel from the gate dialog runs a
  frame plus 50 ms later (`::focus!`, `::scroll-into-view!`). The closing
  MUI dialog gives the focus back to the button that opened it in React's
  effect cleanup, which undid both.
- A sync of PTV's unpublished draft fails ("You cannot update entity with
  status Modified"). PTV's listing and a GET show only the published
  version. **Fixed after the build:** opening a row refetches its location
  and asks PTV's `/active/` endpoint too, as does the site form's review
  fetch (`ptv-core/with-draft-status`). So the row reads "Estetty" with
  "Avaa PTV:ssä" before a sync is tried. Until the row is opened, the table
  still shows the site's other status.
- The help article is written in `ptv-sync-review-help-articles.md`, to be
  published through the help CMS. The panel links to its slug.
- The municipal-user session hasn't happened yet.

### Phase 5: role split and stale-review check (built 2026-10-26)

- `save-sports-site!` syncs or archives only for users with `:ptv/manage`
  for the site's municipality (D2).
- "Odottaa arkistointia" in the PTV tool: a DB query for sites published but
  no longer candidates, the ① notice, and a status-only archive with
  acknowledgement.
- "Synkronoi kaikki valmiit" in the Liikuntapaikat tab.
- `:reviewed` sent by the tool, the wizard and the site form. On the save path
  it travels in the `POST /sports-sites` body and is stripped before storing,
  like `:acting-org-id`.
- The 409 / stored-error handling in the UI.
- Tests:
  - an editor's save, removal or delete doesn't call PTV;
  - a removed site appears as "Odottaa arkistointia" until a PTV manager
    archives it;
  - a PTV manager's save does;
  - a PTV change after the review gives 409 (tool) or saves Lipas and skips
    the sync (form);
  - a missing `:reviewed` keeps today's behaviour.

**As built** (56b20205b), as planned. The site form asks before
overwriting, and says "Tallennetaan ja synkronoidaan…", only for a PTV
manager's save. The delete dialog tells an editor that the location is
archived once the PTV manager approves. Sync-failure toasts gained texts
for the stale review, 403, a 409 double link and a PTV 5xx.

### Phase 6: Palvelut tab (built 2026-10-26)

- The same panel in a service mode. The base is the latest `ptv_service`
  revision (as planned). The registry gets service fields: names, summary,
  description, user instruction.
- The tab's "out-of-date" chip currently means "unsent local edits". Align it
  with the new chip set.

**As built** (uncommitted at the time of writing):

- `ptv-review/service-review` compares the edit, PTV now and the base (the
  latest `ptv_service` revision, `get-ptv-service-bases`,
  `/actions/fetch-ptv-service-bases`). A sync sends the edit, which holds
  PTV's own text in every field the user hasn't edited. So only an edited
  field can overwrite PTV: a row is the user's edit (`:lipas`), or `:both`
  when PTV changed that field too since Lipas's last write, which is a
  choice. Fields PTV changed that the user didn't edit are kept
  (`:ptv-changes`).
- **Names follow the texts (a new decision):** a Lipas-created service's
  sync used to send the sub-category's names, overwriting a rename made in
  PTV without the review showing it. Now `->ptv-service` sends the name
  edited in Lipas, else PTV's own, else the sub-category's.
- Chips are the sites' set: "Estetty" (draft in PTV), "Odottaa valintaa",
  "Valmis synkronoitavaksi", "Ajan tasalla". The old PTV chips (red,
  amber "Tallentamattomia muutoksia", green) and the separate draft alert
  went; the draft is the panel's ① notice. Their i18n keys and the other
  keys left unused by phases 4–6 were removed (`ptv_name-conflict.edn`
  entirely).
- The panel's footer is "Valinnat tulevat voimaan, kun synkronoit palvelun
  (Synkronoi nyt)."; "Synkronoi nyt" goes through the same gate dialog.
- The service info texts say that Lipas maintains service classes and
  keywords, that names and texts can also be edited in PTV, and that only
  Lipas's edits are sent.
- Tests: `service-review-test` (cljc), `service-bases-test` (backend),
  `->ptv-service-names` (names rule).

### Independent review (2026-10-26)

A review of the five commits, by an agent that hadn't seen the build, found
five gaps where a change made in PTV could still be overwritten unseen. All
fixed:

1. **A decision outlived PTV's next change.** Only "keep Lipas's" recorded
   the PTV value it was made against; a taken PTV value or an own edit
   counted as decided whatever PTV did next, also after a 409 refetch. Now
   every choice holds only against PTV's current value and while the edit
   holds what was chosen (`review-choice`), and a taken PTV value that PTV
   has since changed reads as undecided.
2. **Saves that sync without a review.** The search table's quick edit, the
   delete dialog's status change, resurrect, a site-form save whose review
   fetch failed and older tabs sent no `:reviewed`, and the backend skipped
   the check. The server now runs the review itself for such a sync and
   refuses it while a row needs a decision (`check-nothing-unreviewed!`,
   `:ptv-not-reviewed`); otherwise it syncs as before. The refusal names its
   revision (`:at`), so the UI warns after this save only. Verified on the
   test env (s10: Lipas saved, PTV's phone kept).
3. **Text written into the edit by something else counted as decided** (AI
   generation in the wizard or the form). An own value now counts only once
   confirmed: "Vie muokattu versio PTV:hen" (`edited-choice`, for the exact
   value and PTV's value of the time). One more click where the user writes
   their own text over a change made in PTV.
4. **The address's other-language streets and additional information, and
   the phone's information and charge** were deleted by every sync, unseen.
   They are now kept, as postal addresses are (D8), while they belong to
   what Lipas sends (`with-ptv-extras`). Verified on the test env: the old
   payload deleted them, the new one keeps them.
5. **Lipas-created services had no stale check.** They now send
   `:expected-modified` like native ones (`:changed-in-ptv`).

Also: taking one of PTV's other web pages into a web-page slot now reads as
PTV's version of that slot, and the gate dialog names the value a sync sends
for an edited row.

Found in the browser afterwards (cdbc68868): the site form checked PTV
rights against the app-db's `:user`, not its `:login`, so no one counted as
a PTV manager there. The review was never fetched, the PTV tab showed no
panel, and the save asked nothing and sent no `:reviewed`; the server's
check then refused the sync (s10). The same check kept the map view's
fetch for a viewed site (`ptv-review-fetcher`) from running, so the
read-only panel was missing too. Fixed.

### Second review: code only the browser runs (2026-10-26)

The rights bug above went unseen because no test runs ClojureScript and the
browser pass hadn't reached the site form. Two more reviews, one of the
front end's contracts with the backend and one of its own wiring, plus
event-level runs in the browser (re-frame events, app-db and HTTP, checked
against the DB and PTV), found and fixed:

- **A cancelled edit kept its PTV choices** (4ce2c4f3c): the next edit
  read a "keep Lipas" row as decided and the save overwrote PTV unasked.
- **An archived location's refetch was a 500** (d41b3ce9d): the PTV tool
  said "PTV couldn't be reached" with a retry that could never succeed.
- **`:reviewed :service-ids []` was dropped** by the save's `clean`
  (926d1d905), so a connection made in PTV to a location without any was
  not compared.
- **An archive could write review choices into the site** (b9ae9dc1d): a
  halted batch left its patches, and the archive reused the sync payload.
- **Language rows** (c36549a27): "Ota PTV:n tekstit" never showed (no
  `:takeable-by`), and the edit was annotated in the saved site's
  languages, so taken texts still read as a removal in the bar and dialog.
  `annotate-review` now takes the languages the edit publishes.
- **"Synkronoi kaikki valmiit"** (b232f110c) took sites the row's own
  button refuses (no service, texts PTV rejects); one refusal stopped the
  run.
- **The PTV tab hid** when an edit took a synced site out of PTV's scope
  (8dc3c3c2f), though the save still syncs and its dialog leads there.
- **A kept service text** (206160f05) held through later edits or AI
  generation; it now holds for the text kept, as a site row's own value.
- **Scroll and focus to the panel** (5746e30e1) now wait for the lazy
  module to render it.
- **A site open in the map** (1ce13e21b) is reloaded after the PTV tool
  syncs or archives it, so an edit doesn't start from, and revert to, the
  revision before.

Checked and not a bug: PTV's PUT response for a service carries the same
`modified` as the next GET (two saves in a row on Utajärvi went through).
Changed with the user (2026-10-27): "Kumoa valinnat" did put every
reviewed field back to the saved version, also text the user typed. It now
does what it says: the choices are forgotten, and what they wrote into the
edit is taken back to what it held before the first choice
(`record-review-writes`, `undo-review-writes`, at `[:ptv :review :undo id]`,
spent with the choices). A field the user typed into after the choice keeps
their text. The same for services in the Palvelut tab, which only forgot
the choices before.

### Visual pass (2026-10-27)

Clicking and typing in a visible tab (results in the scenarios doc) found
four more, fixed in 2c125cc8c: the focus dropped to the page when a row
collapsed or opened or "Kumoa valinnat" went; a language version kept with
PTV's texts still read as removed, and the bar counted it as a change;
"Kumoa valinnat" showed for PTV's connections a first link keeps, which it
can't undo (it now follows `[:ptv :review :undo id]`, sub
`::review-written?`); and a deref inside a lazy seq in the panel.

Renamed with the user afterwards: the `:awaiting-choice` chip and filter
"Odottaa valintaa" became **"Ristiriita"** (sv "Konflikt", en "Conflict"),
the banner "{1} liikuntapaikassa on ristiriitoja PTV:n kanssa – ratkaise
ne ennen synkronointia.", the wizard's count "Ratkaisematta: {1}". The
design text above keeps the old name.

### Rollout

1. Deploy: app only. No migration, no reindex. `[:ptv :ptv-modified]` is
   optional and appears on each site's next sync.
2. Before deploying: a read-only dry run over all orgs,
   `(user.ptv-review-measure/review-dry-run!)` in
   `dev/user/ptv_review_measure.clj`. It runs the review that ships
   (`sports-site->ptv-input`) and returns, per org, sites by status
   (awaiting choice first) and the open rows by field. Municipalities with
   many sites waiting for a choice get a heads-up through the usual tester
   channel. Checked locally on Utajärvi: it matches the PTV tool's chips.
3. Tell the municipalities that editors' saves no longer reach PTV by
   themselves (D2).
4. ~~Update `docs/integration-ptv-architecture.md`~~ (done 2026-10-26).
5. Publish the help article and the two edits in
   `ptv-sync-review-help-articles.md` through the help CMS.

## Tests (beyond each phase's list)

**Property tests over the registry, per kind:**

- no change anywhere gives no rows;
- a PTV-only change is never `:lipas`, and vice versa;
- PTV's normalisation alone gives no row (projection fixtures from real GETs);
- a derivation change without `:modified` evidence is never `:ptv`;
- taking PTV's value leaves no row;
- a keep choice stops counting when PTV's value changes;
- a language added or removed is never a text row;
- one-slot fields always end with exactly one value.

**Backend:**

- base lookup and projection, with and without `:modified` evidence;
- `:site-patch` rights and whitelist;
- the role split and the stale-review check (phase 5).

**End to end (Claude in Chrome against the PTV test env, Utajärvi):**
scenarios 1–15 through all three entrances, checked with
`(user.ptv-drift-scenarios/state lipas-id)`. Then a pass as an editor without
PTV rights, which no earlier pass covered.

## Decisions

| # | Decision | Status (decided 2026-10-23, built 2026-10-26) |
|---|---|---|
| D1 | Separate snapshot table for the last sync | **No table.** The sync's revision is the base. PTV's `:modified`, compared with one new server-owned key `[:ptv :ptv-modified]`, is the evidence that PTV changed. Revisit only if phase 0 finds false "Muutettu PTV:ssä" rows on sites edited in PTV |
| D2 | Editors without PTV rights | **Nothing they do reaches PTV**, archiving included. A removed site waits in the PTV tool for a PTV manager to archive it |
| D3 | API writers | **None exist.** Only the stale-review check for the UI; revisit if API writers appear |
| D4 | Point and municipality | **Information only.** No choice, never blocks a sync, PTV's point can't be taken |
| D5 | Addresses and contacts | **One slot in Lipas, pick one of PTV's**, with the note "Lipaksessa voi olla vain yksi …" |
| D6 | Language versions PTV has and Lipas won't publish | **Verified: Lipas removes them.** Information, not a choice: what is removed (texts shown, copyable), that the language is enabled through Lipas support, and that otherwise it will be gone. Named in the bar |
| D7 | Bar wording and severities | **As proposed** (state-specific sentence, one count, no red) |
| D8 | PTV's postal address, which Lipas has no field for and every sync deletes today | **Keep it: the integration doesn't touch what Lipas doesn't have.** The sync re-sends PTV's postal addresses from the pre-sync GET (see "Postal addresses" in phase 3) |
| D9 | A Lipas-created service's names (phase 6) | **Kept as PTV has them unless edited in Lipas**, as its texts are. Before, every sync sent the sub-category's names |

## Appendix: code anchors (branch, 2026-10-23, before the build)

Line numbers have moved since; the review code now lives in
`cljc/lipas/data/ptv_review.cljc`.

| What | Where (under `webapp/src`) |
|---|---|
| Payload | `cljc/lipas/data/ptv.cljc` `->ptv-service-location` 652, `->ptv-web-pages` 569, parsers 462–650 |
| Review | same file 1321–1642 (`sync-review` 1364, `review-writes` 1593, `review-site-names` 1615), status 1763 |
| Panel | `cljs/lipas/ui/ptv/review.cljs`, `review_data.cljs` |
| Tool sync, choices | `cljs/lipas/ui/ptv/events.cljs` `::review-take` 1603, `::sync-with-review` 1659, `service-location-payload` 1483 |
| Browser payload preview | `cljs/lipas/ui/ptv/subs.cljs` `::service-location-preview` 321 |
| Site form | `cljs/lipas/ui/map/events.cljs` `::save-edits` 456, `::fetch-ptv-site-review` 489; `map/views.cljs` `view-ptv?` 982; `ptv/site_view.cljs` 295–506 |
| Backend sync | `clj/lipas/backend/ptv/core.clj` `upsert-ptv-service-location!*` 487 (pre-GET 507, post-GET 568), `upsert-ptv-service-location!` 637, `sync-ptv!` 737 (type re-bind ~757) |
| Base | `get-ptv-sync-bases` (`ptv/core.clj` 47, `resources/sql/sports_site.sql` 142) |
| Save path | `clj/lipas/backend/core.clj` `check-ptv-save!` ~1658, `save-sports-site!` 1731 |
| Ad-hoc notices to fold in | `ptv/components.cljs` 78 (languages); `ptv/views.cljs` 242 (archived), 276 (name conflict), 354 (double link), 396 (Modified), 499 (load texts), 1437/1510 (wizard); `ptv/site_view.cljs` 349–356, 442–452, 476–506 |
| Unused i18n keys | `:ptv/data-incomplete`, `:ptv/descriptions`, `:ptv/no-ptv-permission`, `:ptv.actions/select-service-channel`, `:ptv.wizard/regenerate-descriptions`, `:ptv.wizard/mapped-services`, `:ptv.service/use-wizard-to-create`, `add-service`, `link-sub-category`, `link-sub-category-helper`, `link-confirm` |
