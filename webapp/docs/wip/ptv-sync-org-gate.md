# PTV sync gate + unified org resolution

Branch: `fix/ptv-sync-org-gate` · Status: **built** (2026-10-09). §§4–9 implemented as planned; §9 add = plain membership (org-admin assigns roles). Tests: `lipas.backend.ptv.sync-gate-test`, `lipas.data.ptv-test`. Browser smoke done locally.

## 1. What happened

Found via the admin "PTV-integraation käyttöönotto" tab. 11 Pyhäjärvi sites
(76011, 81900, 89827, 97453, 99683, 99882, 505946, 605978, 608850, 612402,
615515) were saved from the site form PTV tab in Jul–Aug 2026 with
`:ptv {:sync-enabled true}` but **no `:org-id`, no service, no texts**. Nothing
ever reached PTV and nobody was told:

1. The editor holds the legacy, city-scoped `ptv-manager` role (city 626) but
   isn't a member of the Pyhäjärvi org. The site form lists PTV orgs from org
   **membership** (`/actions/get-current-user-orgs` → `[:user :orgs]`), so
   `resolve-org-id` returned nil.
2. `::toggle-site-sync-enabled` still set `sync-enabled true`.
3. The tab hides services, texts and AI generation whenever `org-id` is nil
   (`site_view.cljs:367`). The user saw a switch, an empty org picker and a
   blue *info* alert.
4. Nothing validates `:ptv` on a normal site save: the `sports-site` schema is
   open and has no `:ptv` entry. Save succeeded.
5. `sync-ptv!` skipped the sync because the data wasn't ready
   (`ptv/core.clj:644`). It logged a line and stored no error on the site.

## 2. Prod numbers (read-only sweep, 2026-10-09)

**Sites** (1234 carry `:ptv`; 1163 have sync on):

| State | Count | Notes |
|---|---|---|
| Sync on, org + texts present | 1152 | healthy (incl. archived-and-deleted) |
| Sync on, **no org**, no texts | **11** | the Pyhäjärvi case; the only sites with sync on that lack org or texts |
| Sync on, stored `:error` | 3 | 516089 Liperi (PTV 500), 511191 Rauma (missing service), 613080 Ylöjärvi (502, never retried) |
| Published, no `:service-ids` | 2 | 507136 Lieto, 621150 Pyhäjärvi (owner "unknown") |
| Sync off, no org | 4 | 1× 626, 2× 214 Kankaanpää, 1× 436 Lumijoki — likely the same flow, then switched off |
| Sync off, never sent | 50 | mostly Ii (37), looks like deliberate staging |

Every stored `:org-id` matches some org's `[:ptv-data :org-id]`, and every
site's city is covered by its org. So there is no org-id corruption beyond the
missing ones.

**Users** with legacy `ptv-manager` roles (80 active accounts):

- 64: already a member of the org covering their city (org-management backfill)
- **11: a PTV org covers their city but they aren't a member.** Same position as the Pyhäjärvi editor: both the site tab and the wizard are empty for them.
- 5: no PTV org configured for their city

## 3. The model, clarified

**Authority is already unified.** `roles/check-privilege user {:city-code c} :ptv/manage`
sees direct account roles and org-catalog roles alike. Catalog roles are
projected into the JWT with their `:city-code`. Org → city goes through
`org [:ptv-data :city-codes]`. Every backend PTV gate uses exactly this
(`ptv/handler.clj:20-87`), and the design comment there explicitly rejects
membership.

**Only listing is split.** The frontend asks "which orgs am I a member of?"
when it should ask "which PTV orgs cover cities I manage?".

Therefore: **no role migration.** Fix listing and resolution so they use the
same rule as authority.

Principles:

1. **The site determines the org.** A site's PTV org is the org whose PTV config
   covers the site's city (and owner). It doesn't depend on who is editing.
   A persisted `:ptv :org-id` stays authoritative once set.
2. **The user's right is checked separately.** `:ptv/manage` for the site's
   city covers both planes.
3. **The backend re-derives and never trusts the client.** The org-id in the
   request must equal the persisted or derived org.
4. **Sync on is a promise.** If sync is on, the site must have an org, and a
   candidate site must have texts. Otherwise the save fails with a stated
   reason, in the UI and in the backend.

## 4. Shared rules — `lipas.data.ptv` (cljc)

- `covering-orgs [site ptv-orgs]`: orgs whose `:city-codes` contains the site's
  city-code and whose `:owners` (when configured) contains the site's owner.
- `resolve-org-id [site ptv-orgs]`:
  1. the persisted `[:ptv :org-id]`;
  2. otherwise the single covering org;
  3. otherwise nil.

  This **drops** the current "user has exactly one org" rule, which picks a
  wrong org whenever that org doesn't cover the site's city. The input becomes
  *PTV orgs*, not *user orgs*.
- `sync-blockers [site {:keys [org-id]}]` returns a set, empty when the site
  may sync. It only applies when `:sync-enabled` is on and the site isn't being
  archived:
  - `:ptv/no-org`: no org-id
  - `:ptv/missing-texts`: candidate and `(not (ptv-ready? site))`

  "Missing service" can only be known against live PTV data, so it stays a
  runtime check with a loud stored error.

## 5. Backend

### 5a. Listing PTV orgs

New endpoint `POST /actions/get-ptv-orgs`. It returns the orgs with
`:ptv-data` that the caller may act for:

- admins and `:ptv/audit` holders: all of them;
- everyone else: orgs where `manages-org-ptv?` holds.

It returns a **projection**: org id, name, and `ptv-data` without
`:test-credentials` or other secrets.
`get-current-user-orgs` stays membership-only, because it feeds org
management.

### 5b. Gate in `save-sports-site!`

A new `check-ptv-save!` runs before the transaction, against `prev`, which is
read inside the transaction as now.

1. **No `:ptv` change → pass through.** "Change" means a diff in
   user-controlled keys: `:sync-enabled :org-id :delete-existing :service-ids
   :summary :description :user-instruction`. This keeps ordinary edits by
   non-PTV users working; they still trigger the sync of an integrated site,
   as today.
2. **`:ptv` changed → require `:ptv/manage` for the site's city.** Otherwise
   403. This closes the hole where any site editor could set an arbitrary
   `:org-id` plus `sync-enabled` and make LIPAS write to that org with its own
   PTV credentials (`core.clj:1681` TODO).
3. **Org-id integrity.** The incoming `:org-id` must equal `prev`'s persisted
   org-id or `resolve-org-id` over all PTV orgs. Otherwise 400
   `:ptv/org-mismatch`. If sync is on and the org-id is missing, the backend
   fills it from `resolve-org-id` when that is unambiguous.
4. **Sync blockers.** If `sync-blockers` is non-empty, return 400 with the
   blocker keys. The frontend maps them to the same messages it shows inline.
5. Error types are registered in the handler's exception mapping (400/403 with
   `:type`), following `:roles-outside-catalog`.

Not in the shared `sports-site` schema: it is also used for **response**
coercion, so existing bad rows would turn reads into 500s.

### 5c. No silent skips

`sync-ptv!`'s not-ready branch becomes unreachable for the gated cases. It stays
as a defence, but writes `:ptv :error {:message ... :type :not-ready}` instead
of only logging, so the tab and the adoption view show it.

### 5d. Write gates check the site too

`ptv-org-write-access?` (save-ptv-service-location) and `ptv-meta-write-access?`
check the body's org against the user's cities, but not that the **site**
belongs to that org. Add: the site's city must be among the org's
`:city-codes`. Small change, same family of bug.

## 6. Frontend — site form PTV tab

**Org source.** Load `/actions/get-ptv-orgs` into `[:ptv :orgs]`. `::all-orgs`
(PTV) reads from there instead of `[:user :orgs]`. This also **fixes the wizard**
for the 11 legacy-only users: its org selector and auto-select use
`::all-orgs`.

**Turning sync on (`::toggle-site-sync-enabled`):**

- Resolve via the new `resolve-org-id`, then write `:sync-enabled` and
  `:org-id` together.
- If nothing resolves, the switch can't be turned on. Show a red alert with the
  reason:
  - **No PTV org covers the city:** "Kunnalle X ei ole määritetty
    PTV-organisaatiota – ota yhteyttä LIPAS-ylläpitoon" (no PTV organisation is
    set up for municipality X; contact LIPAS support).
  - **An org covers the city but the user lacks rights:** "Sinulla ei ole
    PTV-oikeuksia organisaatioon X" (you have no PTV rights for organisation X).
    This shouldn't happen once 5a is in place, but keep it as a defence.

**Sync on with blockers:**

- **Never hide the next step.** Show the texts section as soon as an org
  exists. When something is missing, show a red alert listing the blockers,
  each with its fix: "Kirjoita tiivistelmä ja kuvaus tai luo ne tekoälyllä"
  (write a summary and description, or generate them with AI). Replace today's
  blue `data-incomplete` info alert.
- **Save is blocked** while blockers exist. Add `::ptv-save-blockers` next to
  `::edits-valid?` (`map/views.cljs:1467`). The Save button shows the reason as
  a tooltip or helper text, and the PTV tab label gets an error badge so the
  user can find the problem from other tabs.
- The user can always turn sync back off, which clears the blockers.

**Remove the implicit `sync-enabled true` default.** `calc-derived-fields`
merges `ptv/db :default-settings` (with `:sync-enabled true`) into *any*
`:ptv` map, so a `:ptv` without the key silently becomes sync-on. Sync must
only come from the explicit switch or the wizard. Drop `:sync-enabled` from the
merged defaults; keep the integration-mode keys.

## 7. Existing data

**Decided 2026-10-09: option A.** An admin and Pyhäjärvi fix the sites by hand; no data migration. With the strict gate, a later edit to one of the 11
sites keeps an unchanged-but-invalid `:ptv`. By 5b.1 that edit passes through,
but sync stays impossible until someone fixes it in the tab, which now works
for them (org resolves, texts are visible).

Options:

- **A (recommended).** Leave the data. Tell Pyhäjärvi the sites aren't in PTV
  yet and that the tab now leads them through it. Consider adding the editor to
  the Pyhäjärvi org as well.
- **B.** Append one revision per site setting `:sync-enabled false`, a purely
  revision-based fix, so no sync-on-without-org rows remain.

The 5 other flagged sites are separate from this bug and need individual
look-ups:

- 3 stored sync errors: retry 613080 (502). Investigate 516089 (PTV 500 with a
  trace id) and 511191 (missing service for 2520 in Rauma).
- 2 published sites without `:service-ids`.

## 8. Adoption tab

- Split "Synkronointi päällä" so "sync on, not in PTV" is its own warning count
  (it would have shown 13 today).
- Fix the first tile's caption: "joskus mukana PTV:ssä" (ever included in PTV).
- Add tooltips with the definitions.

## 9. Listing PTV managers who aren't org members

Goal: an admin, or an org-admin, can see which accounts hold PTV rights for an
org's cities but aren't members of that org, and add them in one click. There
are 11 such accounts on prod today.

5a fixes the **functional** problem for them: they can integrate without
membership. This section fixes the **organisational** one. Membership drives
things that the city role doesn't:
- audit notification emails (`get-ptv-managers`, §10);
- the org's member list;
- later, org-scoped features.

### Query

Backend fn `ptv-managers-outside-org [db org]`: active accounts whose direct
`ptv-manager` role covers any of the org's `[:ptv-data :city-codes]`, minus the
org's `:members`.

Also an all-orgs variant for the admin view. It includes the
"no PTV org for your city" group (5 accounts today), so admins can see where an
org's PTV config is missing.

### Admin view

Under Admin → PTV, next to the adoption tab, add a section listing these
accounts with columns:

| Account (name / email) | City | Org that covers the city | Action |
|---|---|---|---|

- **Action "Lisää jäseneksi"** (add as member): adds the account to that org
  with the org's PTV catalog role. If the catalog has no such role, the account
  is added as a plain member.
- **Rows for cities without a PTV org** get a link to the org's PTV config
  instead of an action.

Admins already see emails elsewhere, so showing them here is consistent.

### Org-admin view

In the org page's Members tab, add an "Ehdotetut jäsenet" (suggested members)
box: "N käyttäjällä on PTV-oikeudet kuntaanne mutta he eivät ole
organisaation jäseniä" (N users have PTV rights for your municipality but
aren't members of the organisation).
- Each row has an add button.
- Show **name/username only, no email**, in line with the current GDPR stance
  where emails are admin-only. Org-admins shouldn't get a new way to enumerate
  accounts.

### Adding (decided 2026-10-09)

**Membership only. Roles stay the org-admin's decision.** "Lisää jäseneksi"
adds the account as a plain member. Whoever manages the org then assigns PTV
(or other) roles in the existing Members tab roles editor. No automatic PTV
role.

- **Admin list:** the button uses the existing member-add or invite flow
  (`invite-org-member`). Check whether it adds an existing account directly or
  only sends an invite; if it only invites, add a direct-add variant that is
  admin-only.
- **Org-admin suggestions box:** same action, scoped to the org. Server-side,
  only accounts from the suggestion set are accepted, so org-admins can't add
  arbitrary accounts by id.

### Rollout note

Org management is still **admin-only in the UI**
(`org.subs/can-access-org-management?`). So the org-admin box becomes visible
only when that gate opens. The admin list is useful right away.

## 10. Follow-ups (not in this branch)

- **Decided: follow-up.** `get-ptv-managers` (audit notification recipients) is membership-only, so
  non-member managers never get audit emails. Same root cause; §9 partly covers it by making membership easy. Small, and could
  join this branch if wanted.
- Bulk status change to removed doesn't archive in PTV (bulk ops bypass
  `save-sports-site!`).
- `ptv-candidate?` hardcodes city owners, while the wizard uses the org's
  `:owners`.
- Dead references: `ptv/handler.clj:30` cites `resolve-ptv-org-id`, which
  doesn't exist. `ptv/events.cljs:1735+` uses the removed `uta-org-id-test`.

## 11. Tests

- **cljc unit tests:** `resolve-org-id`, covering both planes and the
  single-org rule regression; `covering-orgs` by owner; `sync-blockers`
  matrix.
- **Backend:** `save-sports-site!`
  - sync on with no org → 400
  - sync on without texts → 400
  - foreign org-id → 400
  - `:ptv` change by a user without `:ptv/manage` → 403
  - unchanged invalid `:ptv` by a city-manager → passes
  - legacy-only ptv-manager enables sync → org filled, sync attempted
- **Backend:** `get-ptv-orgs` for admin, auditor, legacy-only, member and
  unrelated users.
- **Browser smoke** (local, with a legacy-only test user): wizard shows the
  org; the tab resolves the org on toggle; the blocked save shows a reason; an
  unresolvable city shows the red alert.
- **Backend:** `ptv-managers-outside-org` covers direct roles only and
  excludes members. `add-org-member-by-user-id`:
  - rejects a user-id outside the suggestion set
  - rejects a non-admin of the org
  - adds as a plain member (no roles)

## Appendix: org catalogs on prod (2026-10-09)

There are 44 orgs with `:ptv-data`:

| Catalog | Orgs |
|---|---|
| Has a `ptv-manager` template | 18 |
| Has a catalog, but no `ptv-manager` template | 1 |
| Has no catalog at all | 25 |

(§9 settled on plain membership, so this no longer blocks the add action. It
remains useful context for org-admins assigning roles.)
