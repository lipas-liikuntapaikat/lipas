# PTV drift review — plan

Status: phases 1–4 built 2026-10-21 on `feat/ptv-drift-review` (see "As built" at the end).
**Superseded by the second round, [`ptv-sync-review-v2-plan.md`](ptv-sync-review-v2-plan.md)**
(built 2026-10-26): every field a sync writes, evidence of PTV's edits, the
new status chips, PTV managers only, the stale-review check, and the
PALVELUT tab (this plan's phase 5). Kept for its rationale and its test
passes.
Design canvas (private):
https://claude.ai/artifact/EPsuhiCdWbEex1onT2W3Gy — boards "Liikuntapaikat-
välilehti: erojen läpikäynti" (interactive) and "Liikuntapaikan lomake:
ilmoitus ja tallennuksen vahvistus".

## Goal

When a site's PTV service location differs from what Lipas would send, the
user sees what differs and who changed it since the last sync, and chooses
per field which version stays. Nothing changed in PTV is overwritten without
a choice.

## Today (master 11665fa3f)

- `lipas.data.ptv/compute-service-channel-drift` compares what the next sync
  would send with the live location: name per language, marketing name,
  summary and description per language, service connections. It is two-way
  (no base), so it can't say who changed what.
- `sports-site->ptv-input` turns any difference into `:content-drift`: a red
  PTV chip, "PTV:ssä on eri sisältö kuin Lipaksessa". Computed only for sites
  with `:last-sync` whose location is in the org's channel cache.
- `ptv.components/drift-panel` is rendered only in the Liikuntapaikat tab's
  expanded row (`ptv.views/form`), filtered to the `:services` row (#270).
  The text rows have been hidden since 2026-04 (dd849306f), so a red chip can
  come with an empty panel.
- Every sync makes Lipas the master: the location is rewritten from the site,
  and connections missing from the site's `:service-ids` are removed
  (`upsert-ptv-service-location!*`). Only the first link keeps the location's
  existing connections (`first-link?`, `link-service-ids`, #270).
- A site-form save also syncs (`save-sports-site!` → `sync-ptv!`) and shows
  no difference at all.
- Services: no service write touches connections. The Palvelut tab lists
  PTV's connections for a service but compares nothing.

## Prod measurement (2026-10-19, read-only, 1232 synced sites in 25 orgs)

107 sites (8.7 %) differ. No whitespace or quote noise.

| Cause | Count | Edited in PTV? |
|---|---|---|
| Uusikaupunki enabled English after the last sync | 43 sites (EN name, summary, description) | no: a language added on the Lipas side |
| Connections made in PTV | 73 connections on 60 sites in 9 orgs (50 to PTV-made services, 23 to Lipas-created) | yes, the largest real case |
| Texts edited in PTV | fi summary 3, description 5, name 2, marketing name 1 | yes, genuine rewrites (e.g. Puikkari 78843 / 506026) |
| Probably a wrong link | 525387 "Pyhäjärvi DiscGolfPark" ↔ PTV "Pyhäjärven lintutorni" | no |

Only 12 sites were edited since their last sync.

## Design

### Three-way comparison, no new storage

- **Base**: the site revision written by the last sync. `sports_site` is
  append-only and every sync writes a revision whose event-date equals
  `:ptv :last-sync`. Run it through the same derivation as
  `->ptv-service-location`, with the base revision's own `:ptv :languages`,
  so a language added or removed since then becomes a language row instead
  of a text change.
- **Lipas**: the current site, as today.
- **PTV**: the live location, as today.

Per field:

| Base vs Lipas | Base vs PTV | Row |
|---|---|---|
| same | same | none |
| differs | same | Muutettu Lipaksessa |
| same | differs | Muutettu PTV:ssä |
| differs | differs, Lipas ≠ PTV | Muutettu molemmissa (ristiriita) |
| differs | differs, Lipas = PTV | none |

PTV's copy right after the sync isn't stored. That only matters if PTV
rewrites values beyond whitespace (`normalize-ws`), and the prod measurement
found none. Without a base (none found, or a first link) a difference is just
"eroaa", with no origin.

### Rows

- Field scope v1: name per language, marketing name, summary and description
  per language, service connections. Later: addresses, contact details,
  published languages.
- Each row: the field name and origin badge above two cards, Lipas and PTV,
  with a word diff (`lipas.ui.ptv.diff`). The choice button sits on each
  card. Conflict rows add "Yhdistä käsin" under the cards: an editor for a
  text that combines both.
- Connections: one row per differing connection, "Pidä liitos / Poista
  liitos".
- Language rows ("Lipas lisää kieliversion EN") are information, never
  "Muutettu PTV:ssä".
- The linked location itself is not a row: it is a Lipas-side pointer with
  Lipas's sourceId, and PTV has no version of it. Link trouble (e.g. the
  location archived in PTV) is a status notice with actions: "Julkaise
  uudelleen" / "Irrota liitos".
- When the names differ wholesale, hint that the link may be wrong (525387).

### Applying a choice

- Keep Lipas: nothing to copy. The sync sends it.
- Take PTV: copy PTV's value into Lipas, and the sync sends the result.
  Summary and description go to `:ptv :summary` / `:ptv :description`. Names
  go to the site itself (`:name`, `:name-localized`, `:marketing-name`), see
  open question 1.
- Connections: "Pidä liitos" adds the service to `:service-ids`, "Poista
  liitos" leaves it out.

### Where it shows

- **Liikuntapaikat tab.** One bar per site, collapsed by default: amber, red
  with "Ristiriita · n" when any row conflicts, conflict rows tinted when
  expanded. The bar counts Muutettu PTV:ssä / Muutettu Lipaksessa /
  Ristiriita. Bulk "Ota PTV:n muutokset", "Pidä Lipaksen versio kaikissa",
  "Tyhjennä valinnat". "Ajan tasalla: …" lists the fields that match. Footer
  "Viedään PTV:hen: … / Kopioidaan Lipakseen: …" with "Tallenna ja
  synkronoi". The site list gets a "Muutettu PTV:ssä (n)" filter.
- **Site form, PTV tab** (`ptv.site-view`). Notice "PTV:ssä on muutettu n
  tietoa viimeisimmän synkronoinnin jälkeen", naming the fields, with "Käy
  erot läpi". Saving asks "Tallennetaanko ja korvataanko PTV:n muutokset?",
  lists what will be overwritten, and offers Peruuta / Käy erot läpi /
  Tallenna ja korvaa.
- **Käyttöönotto wizard, step 3.** Linking an existing location there is a
  first link: the same component in "eroaa" mode (no base, no origin badges),
  connections shown as information (kept by `link-service-ids`, removable).
  Per-site and all-linked-sites bulk actions; the export waits for the
  choices. Replaces the all-or-nothing "Lataa tekstit PTV:stä"
  (`::load-ptv-texts`) there.
- **Palvelut tab, Lipas-created services.** The same treatment. Base: the
  latest `ptv_service` revision (`persist-ptv-service-revision!`).

### Rules (decided 2026-10-19)

- A change made only in PTV defaults to ask: no preselected choice, and the
  sync waits for one.
- "Synkronoi nyt" (`::create-ptv-service-location`) blocks or asks while
  choices are open, also when the bar is collapsed. Not drawn in the canvas,
  but must be built and tested.
- Site-form save: warn and confirm, since the save also syncs and
  overwrites.

## Code anchors

As they were before the build; what replaced them is under "As built → Where it
lives".

| What | Where (under `webapp/src`) |
|---|---|
| Comparison, status | `cljc/lipas/data/ptv.cljc`: `compute-service-channel-drift`, `sports-site->ptv-input` |
| What a sync sends | same file: `->ptv-service-location`; `effective-lipas-name` / `effective-lipas-text` mirror its fallbacks |
| First link | same file: `first-link?`, `link-service-ids` |
| Sync | `clj/lipas/backend/ptv/core.clj`: `upsert-ptv-service-location!*`, `sync-ptv!` |
| Site-form save sync | `clj/lipas/backend/core.clj`: `save-sports-site!` → `sync-ptv!` |
| Panel, sync button | `cljs/lipas/ui/ptv/components.cljs` `drift-panel`; `views.cljs` `form` and the sync button (`::events/create-ptv-service-location`) |
| Site form PTV tab | `cljs/lipas/ui/ptv/site_view.cljs` |
| Linking, pull | `cljs/lipas/ui/ptv/events.cljs`: `::select-service-channels`, `::load-ptv-texts` |
| Word diff | `cljc/lipas/ui/ptv/diff.cljc` |
| Service revisions | `clj/lipas/backend/ptv/core.clj`: `persist-ptv-service-revision!` |

## Phases

1. **Base and classification.** Base lookup plus a pure cljc classifier
   (base, Lipas, PTV → rows). Settle open question 2 against prod data,
   read-only, first.
2. **Liikuntapaikat tab.** Bar, rows, choices, applying them, the
   "Synkronoi nyt" gate.
3. **Site form.** Notice and save confirmation.
4. **Wizard step 3.** First-link mode and bulk actions.
5. **Palvelut tab.** Lipas-created services.

## Tests

- Property tests for the classifier: no change anywhere gives no rows; a
  PTV-only change is never "Muutettu Lipaksessa" and vice versa; a language
  added or removed is never "Muutettu PTV:ssä"; after taking PTV's value the
  field has no row.
- Applying choices: taking PTV writes exactly the chosen fields; connection
  choices give the expected `:service-ids`.
- The sync gate and the site-form confirmation, as event tests.
- PTV test env (Utajärvi only): edit a text and add a connection in PTV, then
  bar → take PTV → sync → no differences left.

## Open questions

1. Taking PTV's name changes the site's own name everywhere in Lipas, not
   just in PTV. Allow it, or show names but offer only "Pidä Lipaksen"?
2. Base lookup: matching event-date to `:last-sync` missed 11 of the 12
   recently edited sites in the prod check. Find out why (format, precision)
   before relying on it. Fallback: the two-way "eroaa".
3. Should the backend refuse a sync when the location changed in PTV after
   the user reviewed it, like the native service patch's stale-load refusal
   (#266)?
4. Do open choices live only in app-db (lost on reload) or need saving?

## As built (2026-10-21)

Phases 1–4. Browser-verified locally against the PTV test env (Utajärvi):
PTV-side edits to "Utajärven pallokenttä" (615340) made through the API,
reviewed and synced from the Liikuntapaikat tab and from the site form.

### Where it lives

- `lipas.data.ptv` (cljc): `->review-base`, `sync-review` (rows with
  `:origin`), `review-choice-required?`, `review-choice`,
  `open-review-rows`, `apply-review-choices`. `sports-site->ptv-input`
  attaches `:review` and sets `:sync-status` from it: `:content-drift` only
  when a row needs a choice, `:out-of-date` for Lipas-only changes.
  `compute-service-channel-drift` and the old drift panel are gone.
- Backend: SQL `get-ptv-sync-bases`; `ptv.core/get-ptv-sync-bases`,
  `get-ptv-site-review`; routes `/actions/fetch-ptv-sync-bases` (same gate
  as the site audits) and `/actions/fetch-ptv-site-review` (whoever may edit
  the site, plus PTV managers and auditors).
- `lipas.ui.ptv.review-data` (cljs, no UI, shared by the :ptv and :map
  modules): app-db paths, the site form's review, row labels, the
  overwrite confirmation. `lipas.ui.ptv.review` (:ptv module): the panel.
- Site form (:map module): `::fetch-ptv-site-review` on edit start,
  `::save-edits` gate, PTV tab badge.
- Both confirmations use the new `:lipas.ui.events/confirm-with` (global
  confirm with its own wording; its message may be hiccup).

### Open questions, as settled for v1

1. **Names**: PTV's name can be taken everywhere, by whoever may edit the
   site. The site form writes it into the edit; the PTV tool sends it as
   `:site-names` with the sync (`ptv-data/review-site-names`), and the
   backend writes it into the site before syncing (403 without
   `:site/create-edit`). The PTV card says the site is renamed in Lipas too.
2. **Base lookup**: by the stored `[:ptv :last-sync]` value, not by
   event_date: the first revision stored (created_at, then event_date)
   carrying the current last-sync. Local DB: 6/6 found. Why the
   event_date match missed on prod wasn't found out; matching the stored
   value sidesteps it. Not re-measured on prod.
3. **Backend stale guard**: not built.
4. **Choices are the edit** (rebuilt 2026-10-21). The review compares PTV
   with the *saved* site (`:review-saved` snapshot in the PTV tool, the
   latest revision in the site form), so its rows don't change while the
   user works. Choosing a version writes it into the edit
   (`review-writes`), and the form shows it at once; each row reads what
   the edit holds (`annotate-review`: `:current` :lipas / :ptv / :edited).
   A row is decided when the edit holds PTV's version or the user's own
   text, or when "keep Lipas" was recorded (`[:ptv :review :choices
   lipas-id]`, with the PTV value it was made against, `:seen`). Nothing is
   applied at sync/save time any more, and the merge editor is gone: a
   text of one's own is written in the form.

### Behaviour

- The bar says how many places differ, with a "Näytä erot" toggle on both
  sides; who changed what is on the rows. Red with a conflict, amber when
  a choice is needed, blue when only Lipas changed. Collapsed by default.
  Opened from a confirmation ("Käy erot läpi") it scrolls into view.
- Text rows: word diff, own words highlighted on each card (no
  strikethrough: either side may stay). The card the edit holds is marked
  "Lomakkeessa"; a hand-edited field shows as "Lomakkeessa on muokattu
  teksti" with the text. "Kumoa valinnat" puts the saved
  versions back. In the PTV tool a taken PTV name shows as "Nimi vaihtuu"
  in the sites table until the sync.
- Connections: one block listing every service each side connects the
  location to (`:services` in the review), the differing ones marked, and a
  line per difference ("liitetty PTV:ssä" …) with "Pidä liitos" / "Poista
  liitos".
- The bulk buttons ("Ota kaikki PTV:n muutokset", "Pidä Lipaksen versio
  kaikissa") sit after the differences, which are read first. Taking all
  PTV's changes includes conflicts and, where allowed, names.
- A language Lipas adds is one line ("Lipas lisää kieliversion EN"), no
  choice.
- First link (location made elsewhere, `first-link?`): origin
  `:first-link`, texts need a choice, connections don't (they're kept).
- "Synkronoi nyt", the site form's save and the wizard export apply the
  choices to the site before the write; there is no separate sync button
  in the panel. With choices open, the first two ask ("…ja korvaa" / "Käy
  erot läpi" / Peruuta); the wizard export is blocked with a list and two
  bulk buttons over all sites.
- Wizard step 3 shows the panel per site; its "Lataa tekstit PTV:stä" is
  gone (the panel takes PTV's texts per field). The Liikuntapaikat tab
  keeps its button.
- Filter chip "Muutettu PTV:ssä" in the Liikuntapaikat tab.

### Known gaps / next

- Phase 5 (Palvelut tab, Lipas-created services) waits for #266.
- PTV accepts texts under 5 characters in its UI but rejects them in the
  API; taking one into Lipas makes the next sync fail with the generic
  error until #266's length check lands.
- Two LIPAS orgs on one PTV org: the PTV tool uses the selected org's
  languages, the backend the first match (`get-org-by-ptv-org-id`). Found
  locally ("Äänekosken kunta" carried Utajärvi's PTV config; fixed
  2026-10-21) — the sync dropped English and the review then listed it as a
  Lipas addition. Would hit prod too if two orgs ever shared a PTV org with
  different languages.
- Demo scenarios against the PTV test env (Utajärvi):
  `webapp/dev/user/ptv_drift_scenarios.clj` (`run-all!` recreates them,
  `report` prints each site's rows).
- Tests: `lipas.data.ptv-review-test` (examples + two properties: origin
  follows who changed it; taking PTV's values leaves no difference),
  `lipas.backend.ptv.sync-review-test` (base lookup incl. backdated edits,
  endpoint access). No browser automation for the flows.

### End-to-end pass (2026-10-21, Chrome against the PTV test env)

Each scenario done in the UI, then the saved site and the live PTV location
checked with `(user.ptv-drift-scenarios/state lipas-id)`:

| # | Where | Done | Result |
|---|-------|------|--------|
| 1 | PTV tool, site form | nothing to review; site form save | no panel, sync disabled "Ajan tasalla"; save without asking |
| 2 | PTV tool | "Ota kaikki PTV:n muutokset", sync | Lipas took PTV's texts, PTV unchanged |
| 2 | site form | save, "Tallenna ja korvaa" | PTV got Lipas's texts back; next edit has no review |
| 3 | PTV tool | sync (no choice needed, no bulk buttons) | PTV got Lipas's description |
| 4 | PTV tool | sync undecided → "Käy erot läpi", then "Synkronoi ja korvaa" | PTV got Lipas's summary |
| 4 | PTV tool | "Pidä Lipaksen versio", sync | no question asked; PTV got Lipas's summary |
| 5 | PTV tool | keep both differing connections | PTV and Lipas: Yleisurheilu, Pallokentät, Liikuntaneuvonta |
| 6 | PTV tool | take name + marketing name ("Nimi vaihtuu" in the table), sync | site renamed in DB and search index; PTV unchanged |
| 7 | PTV tool | sync | PTV got the EN name, summary and description |
| 8 | wizard | link to the same-named location, take PTV's texts, export blocked by another site's PTV change → wizard "Ota kaikki", export | one location (no duplicate), PTV's texts, Lähiliikunta + Puistojumppa kept |
| 9 | site form | save undecided → gate → "Käy erot läpi" → take all, "Kumoa valinnat", then PTV name + own merged description + keep Soveltava, save | no question on save; DB and PTV hold exactly those |

Fixed during the pass: "Käy erot läpi" opened the review out of sight
(now scrolls it into view: `review-data/panel-dom-id`,
`:lipas.ui.effects/scroll-into-view!`); the marketing-name row said it
renames the site (own caption now). Not covered: a user without the right
to edit the site (names not takeable) — only admin was used.
