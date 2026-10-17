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

## Tester round 2026-10-16 (Raahe, lipas-dev)

Two syncs failed with only "Synkronointi PTV:hen epäonnistui". Cause,
reproduced in the PTV test env on a Utajärvi clone of Raahe's
Testihiihtopalvelu (`738e9f70…`): PTV's API rejects texts under five
characters, and PTV's own UI doesn't (that service's English summary is
"Test"). A native patch sends the whole service back, so every sync failed;
the panel had no message for the error.

PTV facts (verified, not in PTV's API spec, which documents only maximums):

- **At least 5 characters, counted after trimming**: every Service
  `serviceDescriptions` entry (Summary, Description, UserInstruction,
  ChargeTypeAdditionalInfo, DeadLine, ProcessingTime, ValidityTime,
  ServiceType), Service `requirements`, ServiceLocation Summary and
  Description. No minimum: names (also AlternativeName), keywords, street
  addresses, emails.
- **A Service PUT replaces** `serviceNames`, `serviceDescriptions` and
  `requirements` whole, requirements even when left out of the body. It
  **keeps** what it isn't sent: keywords, legislation, charge type, life
  events, service vouchers. At most one AlternativeName per language.

Built:

- `ptv-data/min-text-length`, `text-too-short?` (trims .NET's whitespace
  set, the same on the JVM and in JS); `short-service-texts` (a PUT body),
  `service-location-short-texts` (a site, in the languages the sync
  publishes: org languages with both texts). Backend refuses before calling
  PTV (`:texts-too-short` 409 for services, `:ptv/texts-too-short` blocker
  for service locations). Not a site-save blocker (`sync-blockers`): the
  published languages depend on the org's live config. The Palvelut panel,
  the wizard's service candidates and "Luo uusi palvelu" check the exact
  body the sync sends (`lipas-service-text-errors`): a text LIPAS writes is
  the user's to fix, one it passes on as PTV has it is "korjaa PTV:ssä".
  Every PTV text field shows "vähintään 5 merkkiä" while under it.
- A 400 from PTV LIPAS has no message for shows PTV's own validation
  messages (never for other statuses or unparsed bodies).
- LIPAS-created services: the sync reads the live service (by the
  `service-id` the panel sends, or else the one LIPAS's ptv_service mirror
  recorded for the source-id; the write stays by source-id) and carries over
  what LIPAS never writes — AlternativeName, the other description types,
  requirements — in the languages LIPAS publishes only
  (`ptv-data/carry-over-service-entries`). Unlike the 2026-03 adoption merge
  and the reverted 2026-04 channel merge, nothing LIPAS owns is merged: its
  names, texts, classes and languages are still replaced whole. Without a
  service-id from either (the wizard creating one) or when PTV no longer has
  it, the body is LIPAS's alone, as before.
- Palvelut tab: "PTV:ssä luodut palvelut (total)", the default filter said
  in the section info, toggle "Näytä myös organisaation muut PTV:ssä luodut
  palvelut (n)"; LIPAS-created info text lists what LIPAS maintains.

Left as is: `ptv-ready?` still asks the Finnish site texts for more than 5
characters (one stricter than PTV). Test data: Utajärvi PTV test org has the
clone `738e9f70…` ("Lipas native -testi G (hiihtoklooni)"), the LIPAS-created
"Hevosurheilu" `1f9e0e59…` (sub-category 6100) and the service location
"Lipas minimipituustesti" `7a31ed41…`.

## GET/PUT asymmetries (2026-10-17)

A native patch wrote the GET response back with a few fields converted.
That broke on a service with a life event: GET returns code-list objects,
PUT takes URIs, and only three lists were converted. A field-by-field diff
of PTV's spec (V11VmOpenApiService vs V9VmOpenApiServiceInBase) and a
round trip on a Utajärvi clone with every optional field set (life event,
industrial class, KR2 groups, Region area, procured producer, other
responsible org, channel connection, legislation, voucher, requirements,
general description) found more:

| Field | Write-back problem |
|---|---|
| lifeEvents, industrialClasses | objects vs URIs (industrial classes as bare codes: PTV 500) |
| areas (Region, HospitalDistrict, ...) | own `:code`, not their municipalities' |
| classes/ontology/target groups with a general description | GET lists the description's items as the service's own; a PUT counts them ("ServiceClasses: Only 4 items allowed!"), but leaving them out breaks cross-checks ("KR1 is required if life events are attached") |
| target groups | `:uri` and `:newUri` forms can't be mixed |
| serviceCollections etc. | GET-only, were sent back |
| mainResponsibleOrganization | set to the org writing, which may be only another responsible org |

Built instead (core/native-service-put-body): the patch sends only what
LIPAS writes and what PTV needs: names and descriptions (patched),
requirements as PTV has them (a PUT without them clears them),
publishingStatus (required), the general description id when there is one,
otherwise target groups, ontology terms and service classes as URIs (PTV
requires them), and LIPAS's sourceId. Everything else is left out, and
PTV keeps it: the full-structure diff after a patch, with and without a
general description, shows only the patched text. The org writing is
passed to `update-service-by-id` explicitly.

Also: a requirement alone in a language makes a language version
(service-language-versions counts them), so "Poista kieliversio" drops
the language's requirements too (it used to fail with "Required value
with type 'Summary' was not found with language code 'en'!").

LIPAS-created services (full body + carry-over), same test: everything
PTV-side survived a sync except what LIPAS writes, which included its
hardcoded defaults: a procured producer added in PTV was lost, and with a
KR2 general description attached in PTV every sync failed ("Target group
Businesses and non-government organizations one of the sub target groups
is required.", LIPAS sends only KR1).

Decided 2026-10-17 (user): the defaults LIPAS has no UI for are set only
when it creates a service (ptv-data/create-only-service-fields: producers,
area type and areas, funding type). An update leaves them out, so PTV
keeps them, and resends PTV's own target groups (PTV requires target groups
in a PUT without a general description); LIPAS's KR1 only on creation or
when the live service can't be read. LIPAS still maintains the name,
texts, service classes, ontology terms, languages and type. The info texts
and the preview tooltips say so.

Verified in the PTV test env: PTV-side producers (procured), Region area,
market funding and KR1/KR2/KR2.1 kept through a LIPAS sync, also without
a service-id (mirror lookup); the KR2 general description case syncs; a new
service is created with LIPAS's defaults, which PTV-side changes then
survive.
