# PTV Service capture — plan

Status: phases 1–2 built on `feat/ptv-service-capture` (2026-10-14),
browser-verified locally against the PTV test env. Follows the read-only fix
for native services (PR #265, c1d6d5e3). See "As built" at the end; it
supersedes the design sections where they differ.

## Goal

An org can take an existing PTV Service that was made directly in PTV
("native") under Lipas management. Afterwards its texts are edited in Lipas,
DVV audits them, and nobody needs the PTV UI for them. It has to be safe: an
explicit act with a preview, no silent merge, and a visible record of who
owns what.

## Today

| Kind | sourceId | Panel | Writes |
|---|---|---|---|
| Lipas-created | `lipas-{org}-{subcat}` | editable | full body built from sub-category |
| Adopted (1 in prod, Eurajoki) | `lipas-{org}-ptv-{uuid}` | editable, but sync is probably broken (panel sends no service-id) | fetch → normalize → merge texts → PUT |
| Native (3853 in prod, 38 linked by 311 sites) | none / foreign | read-only (PR #265) | none |

Adoption entry points are already hidden (`#_` in `components.cljs`
`add-service-panel` and the wizard's "Löytyykö palvelu jo PTV:stä?"). Where
they were reachable, they wrote to PTV the moment a service was picked, with
no preview.

Structural problems to fix along the way:

1. `upsert-ptv-service!` treats "`:service-id` present" as adoption mode, so
   the panel must not send it. That is a heisen-parameter, and it is why
   neither natives nor adopted services can sync from the panel.
2. `merge-ptv-lists` merges forever: Lipas can overwrite a text but never
   remove one, so PTV-side texts survive indefinitely and the published
   content is a mix nobody authored as a whole. DVV would be auditing a blend.
3. Ownership is inferred in the UI from the sourceId prefix
   (`views.cljs` `lipas-managed?`), not from Lipas's own records.

## Comparison with service locations (sites ↔ channels)

How a site takes over an existing PTV service location today:

1. Pick a channel in the selector (wizard or site PTV tab), guarded by
   `assert-no-double-link!`.
2. Optionally pull its texts with "Lataa tekstit PTV:stä" into the form.
3. The drift table shows "Lipas (viedään PTV:hen)" vs "PTV (nykyinen tila)",
   and the unpublished-languages alert shows texts the first sync would drop.
4. Sync sends a full PUT built from the site. Lipas then owns the whole
   channel (name, address, coordinates, contacts, texts, languages, service
   connections), and the sourceId follows PTV's record of the channel
   (`resolve-service-location-source-id`).

For channels, linking means capturing; there is no "linked but PTV-owned"
state. Services should not copy that, for four reasons:

| | Service location | Service |
|---|---|---|
| Cardinality | 1 site ↔ 1 channel, enforced | 1 service ↔ many sites, plus channels Lipas doesn't know (web pages, phone, other locations) |
| Blast radius of a write | that one place | every channel showing the service on suomi.fi, including non-Lipas ones |
| Derivable from Lipas data | almost all of it (Lipas is the master record for the facility) | only the texts; ontology, service classes, target groups, GD, areas, funding are PTV domain knowledge |
| Is linking useful without owning? | no: a synced site always writes its channel | yes: 311 site links to natives work today and need no audit |
| Where Lipas texts live | `sports_site` revisions (site is the lineage) | `ptv_service` revisions, needs a `(org, source_id)` lineage, so capture must assign a sourceId first |
| sourceId | timestamp-generated, so it goes stale on relink (PR #250 pain) | deterministic `lipas-{org}-ptv-{uuid}`, so re-capture is idempotent |

**So the asymmetry is deliberate:**

- Channels have 2 states: not linked / Lipas-owned (whole entity).
- Services have 3 states: native-linked (no writes) / captured (texts only) /
  Lipas-created (whole entity).
- For services, linking and capturing must be separate acts. Linking stays
  cheap and safe; capturing is explicit and gated by a preview.
- Ownership is per field for captured services (texts) and per entity for
  channels.

**Where they should be symmetric:** the UX vocabulary and the safety rails, so
a user who has done one knows the other:

- The same "Lataa tekstit PTV:stä" pull, with the same semantics: into the
  form, saved on the next sync.
- The same drift table component and the same "Lipas → PTV" wording, limited
  to the text fields for services.
- The same unpublished-languages alert when capture or sync would drop a
  language PTV has.
- The sourceId rule "never invent one that collides": services get it by
  construction.
- One gate for sync permission (`check-ptv-save!`/org gate).

One asymmetry I'd *remove* later: channel linking has no explicit
confirmation either; the drift table is the only warning. With the capture
dialog built, a lighter version could be reused for "link existing channel".
That's out of scope here.

## Design

### States and the source of truth

Ownership comes from Lipas's DB, falling back to the sourceId prefix only
for legacy rows:

```
native     : no current ptv_service row for this service-id (or status "released")
captured   : current ptv_service row, source-id matches lipas-{org}-ptv-{uuid}, status "active"
lipas      : sourceId lipas-{org}-{subcat}
```

`::subs/services` gets a `:management` key (`:native | :captured | :lipas`)
computed from the service list joined with `ptv_service_current`. The panel
switches on that instead of `str/starts-with? "lipas-"`.

### Capture flow (backend)

1. **Preview** `POST /actions/preview-ptv-service-capture {org-id service-id}`
   (read-only):
   - live `get-service`;
   - Lipas-owned texts per language (Name, Summary, Description,
     UserInstruction);
   - whether a GD is used, and its texts for display;
   - the other description types present, which are kept;
   - PTV languages vs `languages-with-texts`, i.e. languages that would be
     dropped;
   - connected channels: total, those that are Lipas sites, and others;
   - linked Lipas sites (count);
   - PTV `modified` timestamp;
   - blockers: a foreign sourceId (another integration owns it), archived,
     or not in this org.
2. **Capture** `POST /actions/capture-ptv-service {org-id service-id expected-modified}`:
   - refetch; if `modified` changed, return 409 "changed since preview";
   - refuse a foreign sourceId;
   - PUT by id: the normalized existing body, unchanged, with sourceId set to
     `lipas-{org}-ptv-{uuid}`. If validation fails here, it surfaces before
     the user has written anything, which is what we want;
   - append the first `ptv_service` revision from the response, with
     `:origin "ptv-capture"`, `:captured {:at :by :from-modified}`.
3. **Sync of a captured service**: `upsert-ptv-service!` takes an explicit
   `:mode`:
   - `:lipas-created`: by source-id, create on 404 (current non-adoption path);
   - `:captured`: by service-id, requires an active captured row, then
     fetch → normalize → **replace** the Lipas-owned subset → PUT.

   The panel sends `:mode` and `:service-id`. This fixes Eurajoki too.
4. **Replace semantics** (captured only). **Rule: a captured service never
   loses a PTV language version implicitly.** A plain
   `languages-with-texts(texts, org-langs)` would silently drop:
   (a) languages PTV has but the org hasn't enabled in Lipas;
   (b) languages Lipas doesn't model at all (`lang->locale` is fi/sv/en
   only, e.g. Sámi);
   (c) versions that are valid in PTV but fail Lipas's
   "summary AND description" rule (e.g. name + description only, or main
   text supplied by the GD). So:
   - The captured revision records `:ptv-languages`, all of PTV's languages
     at capture time.
   - Published languages = `:ptv-languages` ∪ languages Lipas has
     complete texts for. Removing a language is an explicit user action
     ("Poista kieliversio"), never a side effect.
   - Lipas replaces only the entries it models: Name and
     Summary/Description/UserInstruction × fi/sv/en. Unmodelled languages
     and every other description type, AlternativeName, ontology, classes,
     target groups, GD and areas pass through untouched.
   - The panel shows unmodelled language versions as read-only
     ("Kieliversio ylläpidetään PTV:ssä").
   - A language enabled in PTV but not in the org's Lipas config is still
     editable on this service; otherwise the user couldn't maintain it.
   - Delete `merge-ptv-lists`/`merge-service-data` once nothing uses them.
   - ⚠ To verify in the PTV test env: whether PUT accepts a service whose
     `:languages` includes a language Lipas sent no entries for; and what PTV
     does with other description types when a language is explicitly
     removed.
5. **Drift**: compare the latest `ptv_service` revision's Lipas-owned subset
   with the live service. Show "Muutettu PTV:ssä", the field diff, and a pull
   button.
6. **Audit**:
   - Captured services enter the service audit list automatically, since the
     lineage exists.
   - Proposal: a service becomes audit-eligible only after its first
     Lipas-authored revision. The capture revision is PTV's own text, and
     DVV said they audit what comes *from Lipas*.

### UI

- **Native panel** (shipped read-only): add "Siirrä Lipaksen hallintaan"
  next to "Avaa PTV:ssä", visible only to users with PTV edit rights on the
  org.
- **Capture dialog**:
  - preview of texts per language (read-only) and the GD notice;
  - "näkyy myös N muussa asiointikanavassa" when non-Lipas channels exist;
  - what changes:
    - texts are edited in Lipas from now on;
    - PTV edits are shown and can be pulled;
    - DVV audits Lipas texts;
    - the classification stays in PTV;
  - a language table (PTV versions, which ones Lipas can edit, which stay
    PTV-maintained). Nothing is dropped by capture or by later syncs;
  - confirm. Follows the "warn before high-impact actions" rule: real
    counts, backed by the preview endpoint.
- **Captured panel**: same as the Lipas panel. The name is editable.
  Translate is allowed. AI generate stays gated by `has-lipas-data?` (no
  sub-category to ground on).
- **List chip**: "Lipas" / "Hallinnassa" / "PTV".
- **Wizard**: replace the hidden "Löytyykö palvelu jo PTV:stä?" adoption
  with **link only**. Picking a native service satisfies the sub-category's
  sites by adding its service-id to them. No service write, no capture. This
  is the common need and it is already supported by
  `resolve-missing-services`.
- **Delete**: `add-service-link-form`, `::link-candidate-to-existing-service`
  text-population, and the hidden `:link` branches.

### Release (phase 2)

`POST /actions/release-ptv-service` appends a `ptv_service` revision with
status `"released"`. No PTV write; the sourceId stays. It is deterministic,
so a re-capture reuses it. The panel goes back to read-only. This lowers the
stakes of capture, but it's not needed for v1.

## Phases

1. **Mode flag + Eurajoki**: explicit `:mode` in `upsert-ptv-service!`; the
   panel sends it; verify Eurajoki's `ptv_service` row on prod (read-only)
   and backfill if missing. Small, and it fixes a live bug.
2. **Capture**: preview + capture endpoints, replace semantics, `:management`
   sub, dialog, chip. Delete the old adoption UI.
3. **Wizard link-only + drift/pull + audit eligibility.**
4. **Release** (if wanted).

## Tests

- `ptv/core_test`:
  - mode dispatch: no service-id ⇒ never adoption;
  - capture refuses a foreign sourceId and a stale `expected-modified`;
  - property test: syncing a captured service never shrinks PTV's language
    set unless an explicit removal is requested (cover org-langs narrower
    than PTV, unmodelled languages, incomplete versions, GD-backed);
  - explicit removal drops that language's Lipas-modelled entries and keeps
    other types;
  - capture is idempotent.
- `ptv/handler_test`: authz on both endpoints (org gate, as in
  `sync_gate_test`).
- PTV test env (Utajärvi only): capture a native with a GD and KR2.x target
  groups (the SubTargetGroupRequired case); drop a language; re-capture.
- Browser: native → dialog → capture → edit → sync → audit list; drift after
  a PTV-side edit.

## Open questions

1. Audit eligibility: from the first Lipas edit (proposed) or from capture?
2. Should a captured service be auto-assigned to *new* sites of a
   sub-category, like Lipas-created ones are? Proposal for v1: no; sites are
   linked by hand. Later, an optional org-level "default service for
   sub-category X".
3. Release in v1 or later?
4. Who may capture: anyone with PTV edit rights for the org, or org PTV
   managers only?

## As built (2026-10-14): capture dropped

**The explicit "capture" step was built, browser-tested, and then removed
the same day.** Everything it protected is covered elsewhere: the sourceId is
set by the first patch, PTV-owned fields are kept by the patch path, the
`expected-modified` check stops overwrites, and audit scope follows "has
LIPAS written it". The one real job left (telling the user the change shows
in every linked channel) moved to a confirmation before the first save. The
user now has two kinds of service, not three:

| | Created in LIPAS | Made in PTV ("native") | Another system's sourceId |
|---|---|---|---|
| LIPAS writes | the whole service (LIPAS is master) | name + Summary/Description/UserInstruction, patched | nothing (read-only) |
| Write path | full body by source-id (unchanged) | GET → `patch-service-texts` → PUT by id | refused (`:source-id-in-use`) |
| Edits made in PTV | overwritten on next sync (said in the panel) | kept; a save on a stale load is refused | - |
| DVV audits | yes | once LIPAS has written it (sourceId set) | no |

Decision (user): no merge logic for LIPAS-created services; LIPAS stays
their master. Patching is for natives only.

### PTV facts found in the test env

- **A language version = any name or description entry in that language.**
  Each version then requires Name + Summary + Description; a leftover
  `ChargeTypeAdditionalInfo` alone makes PTV demand the rest.
- **A service's `:languages` is the languages the service is *provided* in**,
  not its language versions (PTV accepts `["fi" "en"]` with no English
  entries). The native patch never touches it. LIPAS-created services still
  fill it from the text languages (older conflation, left alone).
- **A language version requires the org to be described in it.**
- GET has no `mainResponsibleOrganization`; PUT bodies must set it.
- A zero-change PUT adding only the sourceId changes nothing else.

### What exists

- `lipas.data.ptv`: `service-management` (:lipas / :native / :other-system),
  `service-language-versions`, `service-names`, `patch-service-texts`,
  `language-version-problems`; property tests (a patch never shrinks the
  version set unless removal is explicit, keeps every non-owned entry and
  field, writes LIPAS's texts; explicit removal drops all of a language).
- `upsert-ptv-service!` takes explicit `:mode` "lipas" | "native";
  refusals `:source-id-in-use`, `:changed-in-ptv`,
  `:incomplete-language-versions` come back as 409 `{:type ...}`.
- UI (Palvelut): one info line per kind; native panel = per-language name,
  language tabs = org langs ∪ existing versions, explicit "Poista
  kieliversio" with undo, incomplete-version warning disables sync,
  first-save confirmation with reach; "Peru tallentamattomat muutokset";
  filter "Näytä vain liikuntapaikkoihin liittyvät palvelut" (LIPAS-written or
  linked to a LIPAS site; default on) and a corrected intro text. Unsaved
  native edits are keyed by PTV id (no sourceId before the first save).
- Wizard step 2: "Löytyykö palvelu jo PTV:stä?" links the sub-category's
  sites to an existing service (no write), shown in a "linked" list with
  undo.
- Fixed on the way: `resolve-missing-services` read `(:ptv :service-ids)`
  while both FE callers pass flat sites, so in the wizard a site already
  linked to any service never counted as covered. Now flat everywhere.
- `::reset-wizard` / ai-workbench `::set-model` had docstrings in the
  interceptor slot (worked, logged an error); now comments.

### Not done

- Read-only prod dry run over the 38 linked natives: fetch, build the patch
  body, check it against PTV's PUT schema and `language-version-problems`,
  without sending. Needs the prod nREPL tunnel.
- The `ptv_capture.edn` files (fi/se/en) are unused and should be deleted
  (not registered in `lipas.i18n.utils` any more).
- Release, and "Modified in PTV" diff/pull: dropped. The panel always edits
  on top of PTV's live texts, so there is nothing stale to pull.
- PTV test env: services "Lipas capture -testi A, C, D, E" and "Lipas native
  -testi F" in Utajärvi's test org are test data from this work.
