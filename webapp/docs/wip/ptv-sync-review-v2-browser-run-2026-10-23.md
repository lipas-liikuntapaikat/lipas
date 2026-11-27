# PTV sync review v2: browser run 2026-10-23

Run of [`ptv-sync-review-v2-browser-scenarios.md`](ptv-sync-review-v2-browser-scenarios.md)
with Claude in Chrome against local Lipas and the PTV test env (Utajärvi),
as admin. Every write was checked with
`(user.ptv-drift-scenarios/state lipas-id)`.

PTV writes still took ~2.5 min each, ~5 min when connections changed too.

## Result

All 29 built scenarios were run: 27 pass and 2 fail (L3, L5). The 34
scenarios for phases 3–6 can't be run yet.

| Scenario | Result | Notes |
|---|---|---|
| A1 | pass | "Muutettu PTV:ssä (5)": s2, s4, s5, s6, s9 |
| A2–A4 | phase 4 | A4 today: a PTV change shows only after the refresh button (seen in B9) |
| B1 | pass | |
| B2 | pass | took summary, kept description; both sides agree |
| B3 | pass | |
| B4 | pass | merged text → "Lomakkeessa on muokattu teksti", "Viedään PTV:hen muokattuna" |
| B5 | pass | wording bug, see finding 3 |
| B6 | pass | renamed in DB and search; PTV unchanged |
| B7 | pass | bar still counts "3 kohdassa" (phase 4) |
| B8 | pass | |
| B9 | pass | the choice expired after the 2nd PTV edit; see finding 8 |
| B10 | pass | choices gone after reload, review unchanged |
| C1 | pass | the dialog already has Peruuta / Käy erot läpi / Synkronoi ja korvaa |
| C2 | pass | panel scrolls into view; focus stays on "Synkronoi nyt" (phase 4) |
| C3 | pass | |
| C4 | pass | Peruuta sends nothing |
| D1, D2 | pass | run on s9 (s2 was already synced by then) |
| D3 | pass | s9 renamed, both sides agree; see finding 6 |
| D4 | phase 3 | |
| D5, D6 | pass | |
| D7 | phase 4 | |
| E1–E4 | phase 5 | |
| F1–F6 | phase 4 | F2 situation now exists on s2 (see Environment) |
| G1–G13 | phase 3 | |
| H1 | pass | entered through the name-conflict notice "Liitä tähän palvelupaikkaan" |
| H2 | pass | gate works; the export stopped on a PTV 502 at s2, so s8 was exported alone from the Liikuntapaikat tab: one location, connections kept, PTV's texts |
| H3 | phase 3 | |
| H4 | pass | the wizard has no "Lataa tekstit PTV:stä" |
| I1–I3 | phase 5 | |
| J1 | phase 4 | |
| K1 | phase 6 | |
| L1 | pass | site form at 400 px (same-origin iframe): cards stack, no sideways scroll |
| L2 | pass | Tab/Enter/Space work; the focus mark is only MUI's ripple pulse |
| L3 | **fail** | finding 2 |
| L4 | pass | sv and en complete; see finding 7 |
| L5 | **fail** | finding 1 |
| L6 | pass | typing in one site recomputed 1 of 52 cache entries |

Two syncs running at once (five in a row, overlapping) all came out right.

## Findings

1. **Console error from the review highlights.**
   - `review.cljs:48` and `:173` pass `(clj->js (get diff-styles side))` as the style.
   - React then sees `border-radius`, logs "Unsupported style property border-radius. Did you mean borderRadius?", and drops the radius.
   - Fix: pass the Clojure map (Reagent converts the keys) or use `:borderRadius`.
2. **Screen reader.**
   - The choice buttons read only "Pidä Lipaksen versio" / "Ota PTV:n versio" (and "Pidä liitos" / "Poista liitos"), with no field and no `aria-pressed`.
   - The bar's toggles have no `aria-expanded`, and there are two identical "Näytä erot" buttons.
   - The tool's refresh icon button has no name; the tooltip label sits on its wrapper span.
3. **Wrong outcome wording for a removed connection.**
   - Taking PTV's removal of Pallokentät is listed as "Kopioidaan Lipakseen: Liitetty palvelu Pallokentät", which reads as if it were added.
   - The gate dialog gets this right ("Liitos palveluun … poistetaan").
4. **The wizard export stops silently.**
   - A failure, here PTV's 502 wrapped as our 409, halts the batch at "1/9".
   - The user sees only a toast that fades in seconds.
   - Nothing says which site failed, that the rest were skipped, or what to do.
5. **Noisy word diff.**
   - `diff.cljc:30` splits only on whitespace, so "jääkiekkoa" vs "jääkiekkoa." counts as a changed word.
   - A short edit can end up highlighting most of the sentence.
6. **No progress during a slow save.**
   - The site form closes at once, and the view shows the new name with "Haetaan tietoja PTV:stä…" while the save runs for 2.5–5 min.
   - The PTV tool's row does show its loading state.
7. **Swedish wording.**
   - "Ajan tasalla:" is translated "Uppdaterat:" ("updated"); it should say "the same in both", e.g. "Lika i båda:".
   - The Swedish UI mixes "serviceplats" and "servicepunkt".
8. "Kumoa valinnat" stays visible after the only choice has expired (B9).
9. For an unlinked site the footer says "(Synkronoi nyt)", but the button is "Vie palvelutietovarantoon".
10. **Design question:** the site's view mode shows neither the PTV tab dot nor the review; both appear only when editing.

## Scenario doc corrections

- **H2:** s8 can't be selected while its texts are empty (the switch is disabled), and taking or writing texts decides those rows. So "s8 with open choices selected" doesn't happen; the gate is seen with another site's open rows.
- **D1/D2** name s2, which B2 already syncs. Use a still-unsynced site, or run D before B.
- **B9:** the tool needs its refresh button before the new PTV change shows (A4 is phase 4).

## Environment after the run

- **PTV test env: two locations stuck.** Each has an unpublished "Modified" draft, left by a PTV 502. Every API write to them is refused ("You cannot update entity with status Modified") until the draft is published or discarded in PTV's own UI.
  - 615340 (s1)
  - 89913 (s2), location `e620c202-4878-4f9d-b3fe-0cb618a8eb94`
- **Demo state is used up:**
  - s2–s7 and s9 are in sync, and s8 is linked and synced.
  - s9 is renamed "Utajärven liikuntasali" and s6 "Kirkonkylän valaistu latu".
- **Reset:** `(user.ptv-drift-scenarios/run-all!)`. It does ~3 PTV writes per site, so ~70 min at today's speed, and s1/s2 fail until they're unstuck.

## Fixes (2026-10-23, evening)

All ten findings are fixed (uncommitted, on `feat/ptv-drift-review`).
Rechecked in Chrome on s9 and in the wizard; PTV writes still took 138 s
to 5¾ min.

| # | Fix | Rechecked |
|---|---|---|
| 1 | Highlight styles go to Reagent as a Clojure map | no console messages from load through save; highlights have their 2 px radius |
| 2 | Choice buttons are named "Pidä Lipaksen versio: Tiivistelmä (FI)" and carry `aria-pressed`; one "Näytä erot" toggle with `aria-expanded`/`aria-controls`; the tool's refresh button has an `aria-label` | yes, from the DOM |
| 3 | A connection's outcome says what happens to it: "Liitos viedään PTV:hen / kopioidaan Lipakseen / poistetaan PTV:stä / poistetaan Lipaksesta: {palvelu}" | yes, "Liitos poistetaan Lipaksesta: Pallokentät" |
| 4 | A failed export stops with `in-progress?` off (the spinner used to stay at "1/9") and an alert names the site, PTV's reason and how many weren't tried; "Jatka vientiä" (`::resume-service-locations-creation`) goes on from the failed site | yes: injected state, then a real resume that PTV refused on 89913 |
| 5 | The word diff makes each punctuation mark a token (`diff.cljc`), with a round-trip property test | yes, "lentopalloa, koripalloa ja futsalia." |
| 6 | A site-form save that syncs (`ptv-data/save-syncs?`, now also the backend's rule) shows a sticky blue "Tallennetaan ja synkronoidaan PTV:hen…" with a spinner until the save's own notification replaces it | yes, it showed for the whole 5¾ min |
| 7 | sv: "Lika i båda: …"; the Swedish UI now says "serviceställe" throughout, PTV's own term (it mixed servicepunkt, serviceställe and serviceplats) | yes |
| 8 | "Kumoa valinnat" shows only while a choice still counts | yes, by making the choice's `:seen` stale in app-db |
| 9 | The tool's footer names the button: "(Synkronoi nyt)" or "(Vie palvelutietovarantoon)" | code only: no unlinked site with a review was at hand |
| 10 | Viewing a site fetches its review: the PTV tab has the dot, and the review shows read-only with "Valitse versiot muokkaamalla liikuntapaikkaa…" | yes |

Also: "1 liikuntapaikkaa jäi viemättä" → "yksi liikuntapaikka jäi viemättä".

The scenario doc corrections are applied, with new scenarios D8, D9 and H5
for the fixes.

**s9's state after the recheck.** The save's location PUT went through, but
PTV reset the connection on the following service-connection call ("Connection
reset"). So s9 carries a `:ptv :error`, its `last-sync` didn't move, and
PTV lacks the Pallokentät connection that Lipas has. The location is still
Published (no stuck draft). A sync fixes it.
