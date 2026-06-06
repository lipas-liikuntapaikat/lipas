# Org Management — UX Implementation Follow-up

Status: backend P1–P3 landed on branch `feat/org-management` (commit `36abe855`).
This doc is the hand-off for implementing the **UI** (the UX plan) on top of it,
plus the small backend deltas the UI needs and how to verify.

Read first: `docs/wip/org-management-ux-plan.md` (the design) and
`docs/wip/org-management-design-spec.md` (the model). This file maps that plan
to the *actual* backend that now exists and flags where reality diverges from
the plan.

---

## 1. What the backend already gives you

### Data shapes
- **Org** (from `GET /orgs`, `GET /current-user-orgs`, and internally
  `org/get-org`): `{:id :name :data {:primary-contact {…}} :ptv-data :type
  :role-templates :ownership :members}`. `:id` is the stable org id.
- **Member** (inside `:members` and returned by `GET /orgs/:id/users`):
  `{:user-id :org-role "admin"|"member" :templates ["editor" "ptv" …]}`.
  `GET /orgs/:id/users` returns the **account** maps (`:id :email :username
  :permissions`) *augmented* with `:org-role` and `:templates`.
- **Role-template catalog** (`:role-templates`): a map
  `{:editor {:label "Muokkaaja" :roles [{:role "org-editor"}]}
    :ptv    {:label "PTV" :roles [{:role "ptv-manager" :city-code [91]}]}
    :melonta{:label "Melonta" :roles [{:role "activities-manager"
                                       :activity ["melonta"]}]}}`.
- **Ownership rule** (`:ownership`): `{:city-codes [..] :owners [..]}`.
- **Site ownership fields** (on the sports-site document): `:owner-org-id`
  (uuid string | nil), `:edit-grants` (vector of org-id strings). Indexed in ES
  as `search-meta.owner-org-id` and `search-meta.editor-org-ids`.

### Endpoints (all under `/api`)
| Method · path | Privilege | Body / notes |
|---|---|---|
| `GET /current-user-orgs` | any | orgs the user belongs to (admins/ptv-auditors see all) |
| `GET /orgs` | `:org/admin` (lipas-admin) | all orgs |
| `POST /orgs` | `:org/admin` | create org (`new-org` schema) |
| `PUT /orgs/:id` | `:org/manage` | update details; **members + catalog are preserved**, never wiped |
| `GET /orgs/:id/users` | `:org/member` | member accounts + `:org-role`/`:templates` |
| `PUT /orgs/:id/role-templates` | `:users/manage` (lipas-admin) | `{:role-templates {…}}` — the catalog (ceiling) |
| `POST /orgs/:id/invite` | `:org/manage` | `{:email :org-role? :templates? :login-url}` — **separate org-admin invite**, catalog-validated, creates account + sends custom email for unknown emails |
| `PUT /orgs/:id/members/:user-id/role` | `:org/manage` | `{:org-role "admin"|"member"}` (promote/demote) |
| `PUT /orgs/:id/members/:user-id/templates` | `:org/manage` | `{:templates [..]}` (⊆ catalog) |
| `DELETE /orgs/:id/members/:user-id` | `:org/manage` | remove member |
| `POST /orgs/:id/site-edit-grants` | `:org/manage` (owner org) | `{:lipas-id :grantee-org-id}` cross-org grant |
| `DELETE /orgs/:id/site-edit-grants` | `:org/manage` | `{:lipas-id :grantee-org-id}` revoke |
| `POST /orgs/:id/takeover-request` | `:org/manage` | request to claim matching sites |
| `GET /actions/org-takeover-requests?status=` | `:org/admin` | list requests (lipas-admin queue) |
| `POST /actions/org-takeover-requests/:request-id/approve` | `:org/admin` | apply ownership |
| `POST /actions/org-takeover-requests/:request-id/deny` | `:org/admin` | deny |
| `PUT /orgs/:id/ptv-config` | `:users/manage` | unchanged |
| `POST /orgs/:id/add-user-by-email`, `POST /orgs/:id/users` | `:org/manage` | legacy add/remove (kept; prefer `/invite` + member ops) |

`:login-url` must start with a known LIPAS domain (e.g. `https://localhost`,
`https://lipas-dev.cc.jyu.fi`) — see `lipas.schema.handler/magic-link-login-url`.

### Permissions in the FE
`lipas.roles` is shared. `select-role`'s `:org-id` is now **set-intersection**,
so any `:org-id` role-context passed to `check-privilege` MUST be a set — e.g.
`{:org-id #{org-id}}` (already fixed in `lipas.ui.org.subs`
`::is-org-admin?`/`::is-org-member?`). Don't pass a scalar — it throws.

---

## 2. Backend deltas the UI will need (small, not yet built)

These are intentionally deferred from the backend phase; implement alongside the
matching UX phase.

1. **Owned/editable sites query (Q1, for "Our sites" UX-P2).** ES filtering on
   `search-meta.owner-org-id` / `search-meta.editor-org-ids` works
   (`wrap-es-query-site-has-privilege` already compiles `:org-editor` roles), but
   there is **no dedicated endpoint/param** to list "sites owned by org X". Add a
   thin endpoint, e.g. `GET /orgs/:id/sites?filter=owned|editable`, that runs an
   ES `terms` query on `search-meta.owner-org-id` (owned) or `editor-org-ids`
   (editable). Cheap; mirrors existing search handlers. Use it for the card
   site-count badge too (just a `count`/`_count`).
2. **"Who can edit site Z" (Q2, for the per-site drawer in UX-P2/P3).** No
   endpoint yet. Compose server-side: owner org + `:edit-grants` (off the site
   doc) ∪ activity-editor orgs (enumerate org catalogs whose template grants an
   activity the site has) ∪ best-effort legacy direct users. Add
   `GET /sites/:lipas-id/editors` (or fold into the site response). Spec §6
   describes the algorithm; it's sub-millisecond at real scale (§14.2 S3).
3. **Org history timeline (UX-P3, §4.6).** The `org` revision table *is* the
   audit log (diff consecutive revisions). Add `GET /orgs/:id/history` returning
   revisions (`event_date`, `author_id`, and a computed diff of `document`).
   Site-ownership/grant history comes from `sports_site` revisions
   (incl. `acting_org_id`, which is on the table but **not** in
   `sports_site_current` — query the table for history).
4. **"Pending invitations" — reframe (UX-P3 §4.2).** There is **no invitation
   table** (by design). An invited-but-not-yet-logged-in user is just an account
   member who hasn't logged in. Either drop the "Pending invitations"
   sub-section, or surface it as "members who haven't logged in yet" using the
   account's login history/status. Update the UX plan text accordingly.

---

## 3. UI work by phase (track the UX plan's phasing)

Base files: `src/cljs/lipas/ui/org/{views,events,subs}.cljs`. Existing subs:
`::user-orgs ::org-users ::is-lipas-admin ::is-org-admin? ::is-org-member?
::current-tab ::editing-org`. Events use `:http-xhrio` with `(:backend-url db)`.
Use Reagent 2.0 `r/defc` + `reagent.hooks`, explicit MUI requires (see CLAUDE.md
+ memory notes about `[:>`/`r/as-element`).

### UX-P1 — Members + Roles & templates (with backend P2/P3; the headline)
- **Capability sub:** add `::subs/can? capability org-id` resolving the matrix in
  UX plan §2 from the existing `is-lipas-admin`/`is-org-admin?`/`is-org-member?`.
- **Members tab (§4.2):** unify `admin-user-management` + `org-admin-user-management`
  into one `invite-member` component (email field + org-role select + templates
  multi-select drawn from the org's `:role-templates` keys). Wire to
  `POST /orgs/:id/invite`. Members table shows `:org-role` (inline Admin/Member
  select → `PUT …/members/:user-id/role`) and template chips (→
  `PUT …/members/:user-id/templates`); delete → `DELETE …/members/:user-id`.
  Chips are drawn from the catalog only (the ceiling); hover shows what each
  grants (translate the role-specs).
- **Roles & templates tab (§4.3, NEW):** read-only for org-admins (show the
  catalog so the assign menu is legible), editable for lipas-admin via
  `PUT /orgs/:id/role-templates`.
- **Events to add:** `::invite-member ::set-member-org-role ::set-member-templates
  ::remove-member ::edit-template-catalog`. **Subs:** `::org-templates org-id`
  (= `:role-templates`), `::member-template-options org-id` (catalog keys).

### UX-P2 — Our sites (needs backend delta #1 and #2)
- New **Our sites** tab (§4.4): Owned (default) / Editable filters → backend #1.
  Columns: name · type · city · last-edited · completeness. Card site-count badge
  on the orgs list (§3) uses the same count.
- Per-site "who can edit" drawer → backend #2.
- Manage edit-grants on owned sites → `POST/DELETE /orgs/:id/site-edit-grants`.
- Claim ownership → `POST /orgs/:id/takeover-request`; lipas-admin approval queue
  (a filter on the orgs list or small admin area) → the
  `/actions/org-takeover-requests…` endpoints.
- **Subs:** `::org-owned-sites ::org-editable-sites ::site-editors`. **Events:**
  `::grant-site-edit ::revoke-site-edit ::request-takeover ::approve-takeover
  ::deny-takeover`.

### UX-P3 — History + site-page panel + approvals polish (needs backend #3)
- **History tab (§4.6):** timeline over `GET /orgs/:id/history` (backend #3).
- **Site-page "Editing rights" panel (§5):** owner + derived editors (backend #2),
  with inline grant control for the owner-org admin.
- Approval queue UX for take-over (badge/states), list-view coverage hints.

### Overview tab (§4.1)
Add `:type` (read-only to org-admin, editable by lipas-admin) and the
`:ownership` rule (lipas-admin only — it's the ownership ceiling). Contact fields
stay editable by `:org/edit-contact`.

---

## 4. Verification

Live dev system: `https://localhost` (self-signed), login `admin` /
`$ADMIN_PASSWORD` from `../.env.sh`. Backend already verified; focus UI
verification on the flows below.

**Before browser testing:** `(user/compile-cljs)` via nREPL (port 7888) and fix
build errors. The dev ES index is already reindexed with the new mapping.

### Seed test data via REPL (clj-nrepl-eval -p 7888)
```clojure
(require '[lipas.backend.org :as org] '[lipas.backend.core :as core])
(def ds (:lipas/db integrant.repl.state/system))
;; pick/seed an org with a catalog + members + an ownership rule
(def oid (:id (first (org/all-orgs ds))))
(org/update-catalog! ds oid
  {:editor {:label "Muokkaaja" :roles [{:role "org-editor"}]}
   :ptv    {:label "PTV"       :roles [{:role "ptv-manager" :city-code [91]}]}} nil)
(org/update-org! ds oid (assoc (org/get-org ds oid)
                               :type "city" :ownership {:city-codes [91] :owners ["city"]}) nil)
;; make the logged-in admin a member to see the org in the UI, etc.
```
Org-derived roles are baked at **login / refresh-login** (≤6h token). After
changing membership/templates, hit `/api/actions/refresh-login` (or re-login) in
the browser before expecting new permissions to take effect.

### Delegate browser testing to `browser-tester-v2`
Give it concrete entry points (per memory: don't say "find an org"). Suggested
scenarios:
1. **Members self-service:** as an org-admin, open the org → Members tab → invite
   an existing email with template "editor" → row shows Member + editor chip →
   change role to Admin → remove. Assert no console errors, network 200s.
2. **Catalog visibility/ceiling:** as org-admin, Roles & templates tab is
   read-only; the invite template multi-select offers only catalog entries.
3. **lipas-admin catalog edit:** add a template → it appears in the assign menu.
4. **Invite new email:** invite an unknown address → success; verify account
   created (REPL `core/get-user`) and the invitation email recorded (dev emailer).
5. **Cross-org grant + Our sites** (after backend #1/#2): grant edit on an owned
   site to another org; the grantee's members can edit it.
6. **Take-over:** org-admin requests; lipas-admin approves; owned sites show the
   org as owner; `:owner` enum is locked to the org type.

### Permission-bound assertions (the security surface — keep tight)
- An org-admin sees write controls only for **their** org; a plain member sees
  the roster read-only.
- The template multi-select never offers anything outside the org catalog.
- A user given only "editor" can edit the org's sites but has no `:users/manage`,
  no PTV tab (unless assigned), and no access to other orgs.

### Regression
Re-run the backend harness if you touch shared cljc (`roles`, schemas):
`bb test-ns lipas.roles-test lipas.backend.org-test lipas.backend.handler-test`.
Note: a pre-existing **legacy-index `dense_vector` ES flakiness** can surface in
combined randomized runs (unrelated to org code) — if it appears, drop the
polluted `legacy_sports_sites_current_test` index and re-run.

---

## 5. Open UX decisions (carry over from UX plan §9)
1. Do non-admin members see History / full roster (spec leans yes, read-only)?
2. Template chips: label only for org-admins, expandable privileges for
   lipas-admins?
3. "Our sites" completeness metric: reuse a data-quality signal or start with
   "last-edited age"?
4. Where does Claim ownership live — inside Our sites, or keep the Bulk-ops tab?

## 6. Backend follow-ups (separate from UX)
- Drop the now-redundant legacy account `:org-admin`/`:org-user` roles (a
  migration) **after** derive-at-login is verified in production — until then
  they coexist with document membership and are deduped at login.
- Consider surfacing `acting_org_id` in `sports_site_current` if "on whose
  behalf" is needed on the current snapshot (blocked today by dependent views;
  needs a CASCADE recreate of `geoms`/`geoms_duplicates`).
