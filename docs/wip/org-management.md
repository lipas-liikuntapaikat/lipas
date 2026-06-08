# Organization Management — Design, UX & Implementation

Status: **Implemented** on branch `feat/org-management` (backend P1–P3, UI P1–P3,
dashboard read endpoints, site-form ownership, instructions, legacy-editor
lookup). This document consolidates and supersedes the three earlier working
docs (`org-management-design-spec.md`, `org-management-ux-plan.md`,
`org-management-ux-followup.md`).

Scope: data model + permission engine + the org-management UI. It records the
decisions already made, the model as built, where reality diverged from the
original plan, and the open items.

> **In-progress design change (role-model unification).** The codebase currently
> stores a member's authority as a **split** — `:org-role` (`:admin`/`:member`,
> management capability) **plus** `:templates` (data-edit grants from the catalog).
> That split is being collapsed into **one assigned-role list** per member (§3.2,
> §4.1), because downstream both already reduce to the same flat set of
> `{:role :org-id}` maps matched by one rule (the split only ever existed at the
> assignment/storage layer). The sections below describe the **unified target
> model**; §13 is the concrete refactor plan and lists what still reflects the old
> split in code. Safe to do now — still pre-production.

---

## 1. Background & goals

LIPAS has organizations and a user-level permission system, but historically they
were disconnected:

- Sites carried **no org reference** — no answer to "which facilities does org X own".
- Org membership granted only `:org/member` / `:org/manage` — never editing.
- The `org` table was a **single mutable row per org** — no history, no audit.

Goals (all addressed):

1. **Q1** — answer "which facilities are owned by org X" efficiently (ES). ✅
2. **Q2** — answer "who can edit site Z" (orgs *and* legacy users). ✅
3. **Immutable org history** — every change (config *and* membership) is an
   append-only, auditable revision, mirroring the `sports_site` event log. ✅
4. **Org self-service** — org admins manage members and grant edit on their sites
   to other orgs, without a LIPAS admin in the loop. ✅
5. **Opt-in / additive** — the existing user-level permission system is unchanged;
   a user with no org membership behaves exactly as before. ✅
6. **Elegant simplicity** — one frozen matching rule; authority is data. ✅

### Non-goals (v1)

- Org hierarchies (ylä-/ala-organisaatiot) — deferred. The one feature that would
  justify a rules engine; explicitly not built.
- Per-member permission overrides — a member receives each assigned role's grants
  uniformly (no per-member tweaking of what a role confers).
- Approval workflows on edits — none (bottlenecks reduce data freshness). Take-over
  *ownership* has a light approval flow; per-edit approval does not exist.
- Migrating away from direct user roles — they remain first-class indefinitely.

---

## 2. Design principles (the decisions already made)

### 2.1 Authority is **data**, matched by **one frozen rule**

The matching engine (`lipas.roles/select-role` + `check-privilege`) gained **no new
concepts**. Authority is role data with context sets
(`:city-code :type-code :lipas-id :activity :org-id`); a single intersection rule
decides access. The one engine change was making `:org-id` **multi-valued
(set-intersection), exactly like `:activity`** — because a *resource* (site) can
have several editor orgs (owner + grants). Per-org variation is expressed as data
(the role-template catalog), never as bespoke rules.

> Litmus test: anything answerable in both directions (forward "what can X edit" /
> reverse "who can edit Z") and shown in the FE is **data matched by the rule**,
> compiled to an ES query for the reverse direction — never a rule that computes.

### 2.2 Derived permissions are **computed at login, never persisted**

The only org fact stored is **membership** (in the org document, §4). Everything a
membership confers — editor / activity / ptv roles from the template — is
**projected at token-creation time and lives only in the JWT** (≤6h life).

Consequence: there is **no stored derived state to drift**. A template edit or
ownership-rule change propagates to everyone at their next token refresh, with zero
recompute machinery. The per-request `token-backend` path is untouched; only login
does the extra read.

### 2.3 Immutability via append-only revisions (mirror `sports_site`)

- **Org state** (config + membership) → append-only `org` revisions + `org_current` view.
- **Site ownership & edit-grants** → fields on the **site document**, riding the
  existing `sports_site` event log.

Two history surfaces, both copies of a production pattern. No bespoke audit tables.

### 2.4 Opt-in is **structural**, not conditional

Orgs reference users; the **user document is never touched by org code**. There is
no `if org-enabled?` branch — the legacy path is simply the empty-projection case
(no membership → no derived roles → today's behavior exactly).

### 2.5 Bounded delegation — the org-admin ceiling is **data**

Org admins self-serve, capped by a **role catalog** only a **lipas-admin** can edit.
An org admin may *assign* roles to members and invite/remove members — never *define*
what a role grants, nor escalate beyond the catalog. Enforced **structurally at
projection**: `derive-org-roles` only expands roles found in the catalog (plus the
reserved engine roles, §3.5), so an out-of-catalog assignment yields nothing.

Two role kinds an org admin can assign, both via the same per-member `:roles` list:
- **reserved engine roles** — `:admin` (→ `:org-admin`/`:org/manage`). Defined in the
  role registry, *not* in the per-org catalog, so a lipas-admin's wholesale catalog
  edit can never strip an org of its admins (the old `:org-role` field's safety,
  preserved without a separate field).
- **catalog (data-edit) roles** — `:editor`/`:ptv`/`:melonta`/… defined per-org by
  lipas-admin (the ceiling).

Plain membership (presence in `:members`) confers the **`:org/member` baseline**
(view the org) with no role assigned — so a member with `:roles []` can still see
their org.

---

## 3. Core model

### 3.1 Org as append-only revisions

```
org (revision table)
  id          uuid PK     -- per-revision id
  org_id      uuid        -- STABLE org identity (== old org.id), preserved across revisions
  event_date  timestamptz
  created_at  timestamptz
  author_id   uuid        -- who made the change (the audit actor)
  status      text        -- 'active' | 'archived'
  document    jsonb       -- full org state snapshot (see 3.2)

org_current (view) -- latest active revision per org_id (mirror of sports_site_current)
```

Every change — rename, contact edit, PTV config, template change, **member
add/remove**, instructions edit, ownership-rule change, archival — is a **new
revision** authored by the actor. The diff between consecutive revisions *is* the
audit log.

### 3.2 The org document

```clojure
{:name "Oulun kaupunki"
 :type :city                  ; :city | :municipal-consortium | :state | :private | :sports-federation
                              ; drives owner-enum locking (§3.6) and UI defaults

 :primary-contact {:phone .. :email .. :website .. :reservations-link ..}

 :instructions {:fi ".." :se ".." :en ".."}   ; member-facing guidance (org-admin writes; §6.4)

 :ptv-data { ... }            ; unchanged content; now inside the document

 ;; --- the CEILING: named data-edit role catalog, editable only by lipas-admin ---
 :role-templates
 {:editor  {:label "Muokkaaja"     :roles [{:role :org-editor}]}           ; org-id injected at projection
  :ptv     {:label "PTV-hallinta"  :roles [{:role :ptv-manager :city-code [564]}]}
  :melonta {:label "Melonta (UTP)" :roles [{:role :activities-manager :activity ["melonta"]}]}}

 ;; --- which sites this org may claim ownership of (bulk take-over rule, §6.5) ---
 :ownership {:city-codes [564] :type-codes [] :owners ["city" "city-main-owner"]}

 ;; --- membership + per-member ASSIGNED ROLES (one list; set by org-admin) → immutable history ---
 :members
 [{:user-id #uuid "..." :roles [:admin :ptv]}   ; :admin = reserved engine role (org management)
  {:user-id #uuid "..." :roles [:melonta]}        ; plain member + a catalog data role
  {:user-id #uuid "..." :roles []}]}              ; plain member, view-only (baseline)
```

One per-member field, **`:roles`** — a flat list drawn from `#{:admin}` ∪ keys of
`:role-templates`. The **`:role-templates`** catalog (the *ceiling*, lipas-admin only)
defines what each *data* role grants; the reserved **`:admin`** role lives in the role
registry (§3.5), not the catalog. Assigning `:admin` makes a member an org-admin
(`:org/manage`); assigning data roles grants editing; assigning nothing leaves the
member with the `:org/member` view baseline that membership alone confers.

There is deliberately **no separate "org role" field** — management is just one more
assignable role (see §2.5 for why this is safe, and the design discussion behind it).

Membership-in-document makes member + assignment history immutable, with no separate
membership audit log.

### 3.3 Membership — resolved at login only

The account stores **only direct (legacy) roles**. No `:org-id` roles are persisted
on accounts. At login, `org/derive-user-org-roles db user-id` reads `org_current`
rows whose `document.members[].user-id` contains the user and projects that member's
assigned roles (the `:org/member` baseline + reserved/catalog roles) into role data,
baked into the JWT.

### 3.4 Site ownership & edit-grants — fields on the site document

```clojure
{:owner-org-id #uuid "..."         ; nil for un-owned (legacy) sites
 :edit-grants  [#uuid "..." ...]}   ; orgs granted edit by the owner (cross-org collaboration)
```

In the document → ownership history, "on whose behalf" (`acting_org_id`), and ES
indexing come free from the existing `sports_site` revision log.

### 3.5 The org roles in the registry

Three registry roles back org membership; all are projected with `:org-id` context
and matched by the one rule. None is hand-assigned to *accounts* — they are projected
at login from the member's `:roles` list (or, for the baseline, from mere membership).

```clojure
:org-editor                        ; the one genuinely new role (ownership-scoped edit)
{:sort 62
 :assignable false                 ; never on an account; only projected from a catalog role
 :catalog-assignable true
 :privileges lipas.roles/basic     ; :site/save-api :site/create-edit :activity/view :analysis-tool/use
 :required-context-keys [:org-id]
 :optional-context-keys []}

:org-admin                         ; reserved management role — :roles [:admin] projects this
{:privileges #{:org/manage :org/member}
 :required-context-keys [:org-id]} ; org-scoped (org-id injected at projection)

:org-user                          ; the view baseline — projected from mere membership, never assigned
{:privileges #{:org/member}
 :required-context-keys [:org-id]}
```

`:org-editor` is the only thing the existing city/type/lipas-id roles couldn't express
(ownership-scoped edit). UTP reuses `:activities-manager`; PTV reuses `:ptv-manager`.
`:admin` is a **reserved role key** (engine-level, not in any org's catalog) so a
catalog edit can never remove it; `:org-user` is the floor conferred by membership.

### 3.6 Owner-enum locking

When a site is org-owned, its `:owner` enum is **locked** to the value implied by
the owning org's `:type`, via `lipas.data.owners/org-type->owner` (shared by backend
and FE so the form derives the same value):

| Org type | Locked `:owner` |
|---|---|
| `city` | `city` |
| `municipal-consortium` | `municipal-consortium` |
| `state` | `state` |
| `private` | `company-ltd` |
| `sports-federation` | `registered-association` |

Enforced on save by `core/check-owner-lock!` (throws `:owner-locked` on divergence;
no-op for non-org-owned sites).

---

## 4. Permission resolution flow

### 4.1 Projection (the only new logic)

```clojure
(def org-scoped-roles #{:org-editor :org-admin})   ; org-id injected for these

(def reserved-roles                                  ; engine roles, not in any catalog
  {:admin {:role :org-admin}})

(defn derive-org-roles [orgs]                ; orgs the user is a member of (org_current docs)
  (mapcat (fn [{:keys [org-id document member]}]
            (let [catalog  (:role-templates document)
                  baseline {:role :org-user :org-id #{org-id}}   ; membership ⇒ :org/member
                  specs    (->> (:roles member)                  ; the single assigned-role list
                                (mapcat (fn [k]
                                          (or (some-> (reserved-roles k) vector)   ; reserved (:admin)
                                              (:roles (get catalog k))))))]        ; <- CEILING here
              (cons baseline
                    (for [spec specs]
                      (cond-> spec
                        (org-scoped-roles (:role spec)) (assoc :org-id #{org-id}))))))
          orgs))
```

The double miss (neither `reserved-roles` nor `catalog`) is the structural ceiling:
a role key that is neither reserved nor in the catalog expands to nothing. The
`:org-user` baseline comes from membership itself, independent of `:roles`. All
produced fresh, none persisted.

### 4.2 Login wiring

In basic-auth / login / token-refresh, before `jwt/create-token`:

```clojure
(update-in user [:permissions :roles] into (org/derive-user-org-roles db (:id user)))
```

`derive-user-org-roles` = reverse jsonb-containment query over `org_current`
(GIN-indexable), then `derive-org-roles`. Persisted account doc never modified.

### 4.3 Matching

- `site-roles-context` emits `:org-id` as a **set** of the site's editor orgs:
  ```clojure
  :org-id (some-> (concat (some-> site :owner-org-id vector) (:edit-grants site)) set not-empty)
  ```
- `select-role` `:org-id` branch is **set-intersection** (one line, mirrors `:activity`).
- The scalar `:org-id` role-contexts at org-management call sites are wrapped to sets
  (`{:org-id #{(str …)}}`). **Any `:org-id` role-context passed to `check-privilege`
  MUST be a set** — a scalar throws. (Already handled in `lipas.ui.org.subs`.)
- `check-privilege`, `check-permissions!`, and the per-request auth path are untouched.

### 4.4 ES (reverse / bulk direction)

- `enrich*` indexes `search-meta.owner-org-id` and `search-meta.editor-org-ids`.
- `wrap-es-query-site-has-privilege` has an `:org-id` term branch, so an `:org-editor`
  role compiles to `owner-org-id OR edit-grants` term filters.

---

## 5. Answering the two questions

**Q1 — facilities owned by / editable by org X**: ES term query on
`search-meta.owner-org-id` (owned) or `editor-org-ids` (owned ∪ granted). O(query).

**Q2 — who can edit site Z** (`core/site-editors`, returned by `POST /actions/site-editors`):
1. **owner org** — off the site document.
2. **grantee orgs** — off `:edit-grants`.
3. **activity-editor orgs** — enumerate org catalogs whose template grants an activity
   the site has (orgs ≈ hundreds, trivial).
4. **legacy direct users** — **now implemented** (was the deferred unindexed part).
   See §5.1.

The UI shows orgs by name (no member expansion — by product decision) plus the
legacy individual users.

### 5.1 Legacy direct-user lookup (the formerly-unindexed part)

`db/users-with-permissions-matching` pre-filters candidate accounts with jsonb
containment on `account.permissions`, then `core/site-editors` confirms with the
exact `check-privilege`:

- **Candidate pre-filter** — `permissions @> {"roles":[{"city-code":[C]}]}` (and
  `type-code`, `lipas-id`, `activity`), narrowing ~2.3k accounts → a handful.
  Containment params are Clojure maps (auto-converted to jsonb by `ISQLValue`).
  - **Both key spellings** (`city-code` *and* `city_code`, etc.) are queried,
    because the raw JSONB column carries both; the app normalizes (kebab) only on
    read (`db/user.clj` `unmarshall`).
- **Exact check** — candidates are normalized (`->kebab-case-keywords` +
  `conform-roles`) and passed through `roles/check-privilege ctx :site/create-edit`.
  This handles the matcher's "absent context key = matches any" semantics that SQL
  containment can't.
- **Admins excluded** — they can edit everything; listing them under every site is
  noise.
- **Index** — migration `20260607120000-account-permissions-gin` adds
  `account_permissions_gin` (`gin (permissions jsonb_path_ops)`). The planner uses it
  for selective predicates (Bitmap Index Scan); for broad activity predicates it may
  seq-scan, still ms-scale at current size.

**Verified in REPL** (see §11): candidate result == exhaustive naive scan, exactly,
across diverse sites; single-digit-to-low-double-digit ms.

**Caveats:** admins are excluded by design; a hypothetical *context-free* non-admin
edit role (all context keys absent) would be missed by the value pre-filter — but
in practice every edit role carries city/type/lipas-id/activity.

---

## 6. The UI

Base files: `src/cljs/lipas/ui/org/{views,events,subs}.cljs`. Reagent 2.0 `r/defc` +
`reagent.hooks`, explicit MUI requires.

### 6.1 Principles

1. **One interface, capability-gated.** LIPAS admins and org admins use the *same*
   screens; every control is shown/enabled by a capability check.
2. **Make the ceiling visible.** Org admins assign from a catalog shown read-only.
3. **Data-collection first.** Org features never block site editing; a member with no
   org needs nothing here.
4. **Transparency over control.** Owners *see* who can edit their sites, even where
   they don't *manage* it.
5. **History is first-class** (but admin-only — see §6.6).

### 6.2 Capability model (`::subs/can? capability org-id`)

| Capability | lipas-admin | org-admin | member |
|---|---|---|---|
| `:org/create`, `:org/edit-type+ownership`, `:org/edit-catalog` | ✓ | – | – |
| `:org/edit-contact`, `:org/edit-ptv`, `:org/edit-instructions` | ✓ | ✓ | – |
| `:org/manage-members` (invite / remove / assign roles incl. `:admin`) | ✓ | ✓ | – |
| `:org/grant-site-edit` (cross-org) | ✓ | ✓ | – |
| `:org/view-history` | ✓ | ✓ | – |
| `:org/view` (roster, sites, catalog read-only) | ✓ | ✓ | ✓ |
| edit **direct** user-level roles (separate user-mgmt UI) | ✓ | – | – |

Direct user-permission editing stays in the existing admin user-management screen —
the *other* plane, intentionally not part of the org UI.

### 6.3 Tabs (`org-view`)

Order: **Ohjeet · Kohteet · Yleistiedot · Jäsenet · Käyttöoikeudet · PTV · Historia**.

- **Ohjeet** is first (§6.4). **Kohteet** is the **default landing tab** (most-used);
  its data loads on org open via the normal tab path. *(Open item: whether Ohjeet, as
  the first tab, should also be the default landing — see §10.)*
- **Historia** is shown only to admins (`:org/view-history`).
- Terminology: "Kohteet" (was "Kohteemme"), "Käyttöoikeudet" (was "Oikeudet ja
  omistus").

### 6.4 Ohjeet (instructions) — NEW

Org admin / lipas-admin write member-facing instructions, members read-only. Stored
in the org payload under `:instructions {:fi :se :en}` (malli schema + marshall +
`update-org!` whitelist; members/catalog preserved). The three language variants are
**language sub-tabs** (Suomi / Svenska / English) with one editable text area at a
time. Empty + read-only shows a "no instructions yet" message. Audit diff records
"Ohjeet päivitetty".

### 6.5 Yleistiedot (overview)

Name, **type**, primary contact. Contact editable by `:org/edit-contact`; **type +
ownership rule** editable only by lipas-admin (`:org/edit-type+ownership`),
read-only to org-admin (the ownership ceiling). The handler pins `:type`/`:ownership`
to current values for non-lipas-admins, so a non-admin edit is a no-op.

### 6.6 Jäsenet (members)

Unified **invite** control (email + a single **roles** multi-select). The role options
are one list: the reserved **Ylläpitäjä** (`:admin`) plus the org's catalog roles
(`:editor`/`:ptv`/…). Members table: one roles multi-select per member (add/remove,
same option list), remove. No separate "org role" control — admin is just a role in
the list. Members see the roster read-only. **No "pending invitations" table by
design** — there is no invitation table; an invited-but-not-logged-in user is just an
account member (optionally surface via login status later).

**Adding a member (the invite flow, after unification).**
1. Inviter types an email; an existence probe (`POST /actions/check-is-existing-user`)
   decides the account path: an **unknown** address gets an account created on the fly
   and a magic-link email; a **known** one is simply added. *This probe + account
   creation + email path is unchanged by the role unification* — only the role payload
   collapses.
2. The roles multi-select **starts empty**. Empty = a **plain member** (the
   `:org/member` view baseline that membership alone confers). The old "member" default
   on the org-role select **disappears** — "member" is no longer a pickable value, it is
   what you are by being in the org.
3. The inviter may grant any roles up front, **including `Ylläpitäjä` (`:admin`)** — so
   a lipas-admin can create an org's first admin in one step, and an org-admin can
   invite another admin, all through the same control. `:roles []` is a valid
   submission (membership-only viewer).
4. Submit → `POST /actions/invite-org-member` with `{:email :roles}`
   (catalog-validated: `:roles` ⊆ `#{:admin}` ∪ catalog keys).

### 6.7 Käyttöoikeudet (role catalog)

Read for everyone, write for lipas-admin (`:org/edit-catalog`). Lists each catalog
role (label · plain-language description · assigned-member count) — the data-edit
roles org admins assign from, made explicit. The reserved **Ylläpitäjä** (`:admin`)
role is shown here too for transparency but is **not catalog-editable** (engine-level),
so a catalog edit can never remove it.

### 6.8 Kohteet (our sites) + who-can-edit

- One dataset: the org's **editable sites** (owned ∪ granted, each flagged `:owned?`),
  from bulk-operations. Owned vs shared, content filters, and bulk-edit selection are
  facets of this one list. Card site-count badge on the orgs list uses the same data.
- **Per-site "who can edit" drawer** (`site-editors-detail`, on row expand): a single
  scannable list answering "who can edit this site", each row tagged by **reason** with
  a tooltip:
  - **Omistaja** (owner org, primary color) — *"Tähän organisaatioon kuuluvat henkilöt
    saavat muokkausoikeuden organisaation kautta."*
  - **Jaettu** (grantee org; carries the revoke ✕) — *"Omistajaorganisaatio on jakanut
    muokkausoikeuden tälle organisaatiolle."*
  - **Aktiviteetti** (activity-editor org) — *"Organisaatio saa muokata kohteen
    aktiviteettitietoja vastuuaktiviteettiensa kautta."*
  - **Suora oikeus** (legacy direct user, by email) — *"LIPAS-ylläpidon suoraan
    henkilölle myöntämä muokkausoikeus."*
  - Below a divider, a **"Jaa muokkausoikeus toiselle organisaatiolle"** action
    (org selector + grant button) for those who can grant.
  - Fetched lazily, **one request per expand**, cached per lipas-id (accordion: one row
    open at a time). No N+1.
- **Manage edit-grants** on owned sites → `POST /actions/grant-site-edit` /
  `revoke-site-edit`.
- **Edit history** (timestamp + editor email) is shown in the same drawer, lazily
  fetched per expand via `POST /actions/get-site-edit-history` (any authenticated user).
- **Claim ownership** → `request-org-takeover` (org-admin) / `reclaim-org-sites`
  (lipas-admin direct); lipas-admin approval queue via `list-org-takeover-requests` +
  `approve-org-takeover` / `deny-org-takeover`.

### 6.9 Historia (audit) — admin-only

Reverse-chronological timeline over `org` revisions: who changed what when (contact,
catalog, members, template assignments, instructions, ownership rule, …). **UUIDs
are resolved to human-readable identifiers** — both revision authors and member
user-ids referenced in the diff resolve to email/username (one batched account
lookup in `get-history`). Admin-only (`:org/view-history`; route `:org/manage`).

### 6.10 Sports-site form — Omistajuus (ownership)

The site form's ownership section (`lipas.ui.sports-sites.views/ownership-fields`):

| Context | Owner-org autocomplete | Legacy "Omistaja" select |
|---|---|---|
| New site, owner-capable user | shown, **pre-filled when exactly one** ownable org; clearable | hidden when org set; shown when cleared |
| New site, no ownable orgs | – | shown (legacy) |
| Existing site, lipas-admin | **editable** (assign/change/clear ownership) | hidden when org set |
| Existing site, non-admin, org-owned | org name **read-only** | hidden |
| Existing site, non-admin, legacy | – | shown |
| Read-only view, org-owned | org name (read-only) | hidden |

- Selecting an org sets `:owner-org-id` **and** locks `:owner` to the org-type enum
  (§3.6, shared `org-type->owner`). Clearing reverts to the manual select.
- **Pre-fill:** new sites default to the single ownable org (org-owned) — the creator
  clears it to record a legacy/non-org owner (e.g. a private rink a city worker
  maintains on behalf of others). `::init-new-site` sets it; skipped when a template
  fixes `:owner` (analysis/planning sites).
- **Backend authz** (`owner-org-assignment-authorized?` middleware on
  `POST /sports-sites`): a claimed `:owner-org-id` requires `:site/create-edit` on
  that org (org-editor of it, or lipas-admin). Legacy saves (no owner-org-id) pass
  through; site-level edit is still enforced in core.
- **Deferred caveat:** the middleware can't see current site state, so it doesn't
  enforce existing-site ownership immutability against a hand-crafted API call (the FE
  doesn't expose it; not an escalation — they could already edit the site). A ~3-line
  "compare to current revision" core guard could add defense-in-depth later.

### 6.11 Re-frame surface (selected)

Subs: `::can? ::user-orgs ::ownable-orgs ::org-templates ::org-history
::site-editors ::is-lipas-admin ::is-org-admin? ::is-org-member?`. Events:
`::invite-member ::set-member-roles ::remove-member
::edit-template-catalog ::grant-site-edit ::revoke-site-edit ::request-takeover
::approve-takeover ::deny-takeover ::edit-org ::save-org ::get-site-editors
::get-org-history`.

---

## 7. Endpoints (all under `/api`, mostly `POST /actions/*`)

| Method · path | Privilege | Notes |
|---|---|---|
| `POST /actions/get-current-user-orgs` | any | orgs the user belongs to (admins/ptv-auditors see all) |
| `POST /actions/get-all-orgs` | `:org/admin` | all orgs (lipas-admin) |
| `POST /actions/create-org` | `:org/admin` | create org |
| `POST /actions/update-org` | `:org/manage` | details; `:type`/`:ownership` pinned for non-lipas-admins; **members + catalog preserved** |
| `POST /actions/get-org-members` | `:org/member` | member accounts + their `:roles` |
| `POST /actions/update-org-role-templates` | `:users/manage` | the data-role catalog (ceiling) |
| `POST /actions/invite-org-member` | `:org/manage` | body `{:email :roles}`; catalog-validated; creates account + emails unknown addresses |
| `POST /actions/set-org-member-roles`, `DELETE …/:user-id` | `:org/manage` | assign `:roles` (incl. `:admin`), remove |
| `POST /actions/check-is-existing-user` | `:org/manage` | invite-form existence probe (boolean only; org-scoped) |
| `POST /actions/get-org-sites` | `:org/member` | owned / editable site lists (ES) |
| `POST /actions/get-org-sites-for-bulk` | `:site/create-edit` (org) | bulk-edit candidate sites |
| `POST /actions/get-site-editors` | (site-scoped) | Q2: owner + grantees + activity orgs + **legacy users** |
| `POST /actions/get-site-edit-history` | (any authenticated) | per-revision timestamp + editor email (Kohteet drawer) |
| `POST /actions/get-org-history` | `:org/manage` | revisions + computed diff (admin-only) |
| `POST /actions/grant-site-edit`, `revoke-site-edit` | `:org/manage` (owner org) | cross-org grant / revoke |
| `POST /actions/preview-org-takeover` | `:org/manage` | claim impact preview (count + relabel + sample) |
| `POST /actions/request-org-takeover` | `:org/manage` | org-admin requests to claim matching sites |
| `POST /actions/reclaim-org-sites` | `:org/admin` | lipas-admin direct reclaim (create+approve) |
| `POST /actions/list-org-takeover-requests`, `approve-org-takeover`, `deny-org-takeover` | `:org/admin` | lipas-admin queue |
| `POST /sports-sites` | (site authz in core) + `owner-org-assignment-authorized?` | save; gates claimed `:owner-org-id` |
| `POST /actions/update-org-ptv-config` | `:users/manage` | unchanged |

`:login-url` for invites must start with a known LIPAS domain
(`lipas.schema.handler/magic-link-login-url`).

---

## 8. Database changes

1. **`org` revision table + `org_current` view** — `org_id` == old `org.id` to
   preserve references. GIN index on `document` for the login reverse-membership
   query. Seeded one revision per existing org; member backfill from legacy
   `:org-admin`/`:org-user` account roles (→ member `:roles ["admin"]` / `[]`).
   A follow-up **role-unify migration** (self-contained jsonb reshape, *no* `org/*`
   calls — §13) rewrites any old-shape members `{:org-role :templates}` →
   `{:roles}` by appending one revision per org (`:roles` = `templates` ∪
   `["admin"]` when org-role was admin).
2. **Site document fields** — `:owner-org-id`, `:edit-grants` (jsonb; no schema
   migration). Added to the malli site schema. ES mapping gains
   `search-meta.owner-org-id` / `editor-org-ids` (reindex).
3. **`account_permissions_gin`** (`20260607120000`) — GIN `jsonb_path_ops` on
   `account.permissions` for the legacy-editor lookup (§5.1).
4. **`acting_org_id`** on site revisions ("on whose behalf") — on the table; not in
   `sports_site_current` (blocked by dependent views; query the table for history).

No destructive change to `account`. The legacy account `:org-admin`/`:org-user`
roles are removed only in a *follow-up* migration after derive-at-login is verified
in production (until then they coexist and are deduped at login).

---

## 9. Audit & history

| Change | Recorded as | Actor |
|---|---|---|
| Rename / contact / instructions / PTV / **catalog** edit | new `org` revision | `author_id` |
| Member add / remove / role assignment (incl. `:admin`) | new `org` revision (members in document) | `author_id` |
| Org archival | new `org` revision, `status 'archived'` | `author_id` |
| Site ownership take-over / transfer | new **site** revision (`:owner-org-id`) | site `author_id` |
| Cross-org edit grant / revoke | new **site** revision (`:edit-grants`) | site `author_id` |
| "On whose behalf" | optional `acting_org_id` on the site revision | — |

Two append-only logs, both pre-existing patterns. The org-history view diffs
consecutive revisions and resolves all referenced account ids to email/username.

---

## 10. Open questions & decisions

1. **Default landing tab** — Ohjeet is the first tab; Kohteet is currently the
   default selected (most-used). Decide whether the first tab should also be the
   landing tab.
2. **Legacy-user email display (GDPR)** — emails are shown to admins only; **left
   as-is pending PM/DPO feedback**. Recommended mitigation if needed: name/username
   first (email for lipas-admin or behind a reveal) + audience scoping, **not** email
   masking (pseudonymisation ≠ anonymisation; defeats the purpose in a narrow
   audience). Do not change unprompted.
3. **Admins in the who-can-edit list** — currently excluded (they can edit
   everything). Option: include with an `Ylläpitäjä` tag.
4. **Take-over / transfer approval depth** — take-over has a requested→approved
   flow; transfer (owner → new owner) confirmation UX is light. Revisit if needed.
5. **Owner-enum locking** — hard-locked from `org.type` (done) vs default + warn.
6. **Revocation latency** — removing a member / unassigning a template takes effect
   at next token refresh (≤6h). Acceptable, or add an immediate-revoke path?
7. **Catalog editing scope** — lipas-admin-only (current). Keep, or allow a "lead"
   org-admin within a lipas-admin-set boundary? (Recommend keep — it's the ceiling
   guarantee.)
8. **Existing-site ownership immutability** — FE-gated + middleware-authz'd, not
   enforced server-side against raw API (§6.10). Add the core guard if desired.
9. **Per-site ownership-claim requests (correction / cross-org transfer)** — today
   an org-admin has **no reachable claim trigger at all**: both entry points (the
   ownership-rule editor button and setup-checklist step ③) are lipas-admin-only,
   and claim scope is fixed to the org's ownership rule (rule itself is lipas-admin
   only). The `org_takeover_request` state machine *does* support an org-admin
   `request` path (`POST /actions/request-org-takeover`, `:org/manage`) — it just
   has no UI. Two follow-ups, sharing that existing machine:
   - **(a) Surface the org-admin bulk-request trigger** — a "request claim" button
     in Kohteet for `:org/manage` that opens the impact dialog in `request` mode.
     Closes the half-wired request→approve loop with no new backend.
   - **(b) Per-site claim of a site the org doesn't own** — for *wrong owner entered*
     and *private → municipality* transfers. A request row with a hand-picked
     `lipas-id` instead of a rule-expansion (same table, same approval queue, same
     `approve!` apply path; owner-enum lock relabels `:owner` to the org type in the
     same revision — exactly the private→city semantic). New design surface, all
     about what replaces the rule's ceiling:
     - *Contested vs un-owned* — claiming a **legacy/un-owned** site is a low-risk
       correction; claiming one **owned by another org** is a transfer *away* from
       org A → that owner should be notified (or consent), not silently reassigned.
     - *Guardrail* — fully open (admin judgment) vs a soft check that the site's
       city-code is one the requesting org plausibly governs (catches fat-finger
       requests for the wrong municipality).
     - *Entry point* — the site's who-can-edit drawer (`site-editors-detail`) or the
       site-form ownership section: a "Pyydä omistajuutta" action when an org-admin
       does **not** own the site.
     - *Request payload* — tag the row (`kind` / explicit `lipas-ids`) so the queue
       renders "bulk rule claim (N)" vs "single-site request: «name»", and the
       approver sees the current owner for the contested case.

---

## 11. Verification record

### 11.1 Matcher proof (Phase 0)

Standalone against pure `lipas.roles` cljc. Surfaced the one correction (`:org-id`
set-intersection). All assertions green: owner edits owned; non-member denied;
grantee allowed only on cross-granted sites; activity (melonta) editor edits activity
data; org-admin scoped to own org; admin edits anything; legacy roles untouched.
20/20 access-pattern coverage end-to-end (projection → conform-roles →
check-privilege), incl. the **ceiling** (bogus template grants nothing).

### 11.2 Scaling (≈ ≤500 orgs, ~57k sites, users mostly in 1 org)

- **No per-site fan-out** — derived authority is O(orgs × templates), independent of
  owned-site count (org owning 50 000 sites → still 3 derived roles).
- JWT payload stays KB-scale even at pathological membership; `check-privilege` µs-scale.
- Login reverse-membership query GIN-indexed (`jsonb_path_ops`, Bitmap Index Scan), ms-scale.
- ES bulk filter (Q1) sub-millisecond at full site count.

### 11.3 Legacy-editor lookup (§5.1) — measured in REPL

Real dev stack (2 346 accounts, ~2 021 with roles, 57 030 sites):

- **Correctness:** for 6 diverse sites, candidate pre-filter result == exhaustive
  naive scan, exactly (0 missing, 0 extra).
- **Performance:** ~1–14 ms per site (reused connection) vs ~207 ms naive (which is
  dominated by loading *all* accounts into the JVM). The real win is the `WHERE`-clause
  candidate filter (~50–264 rows vs 2 346), not the index per se.
- **Index:** `EXPLAIN ANALYZE` confirms Bitmap Index Scan for selective predicates
  (~1.9 ms); broad activity predicates seq-scan (~5 ms) at current table size.

### 11.4 Test coverage

`bb test-ns lipas.roles-test lipas.backend.org-test` — green. Org-test includes:
owner-org assignment authz (own org ✓ / other 403 / non-member 403); history authz
(member 403); history member-email resolution; org instructions round-trip + route;
site-editors composition; site-editors legacy-users (direct user found, admin
excluded, candidate == naive scan).

---

## 12. Invariants that keep this cheap forever

1. **One frozen matching rule** — future scenarios arrive as data (template entries,
   site fields), never new code branches.
2. **No persisted derived state** — derived roles exist only in tokens; nothing to
   reconcile.
3. **Two append-only logs, both pre-existing patterns** — history/audit is structural.
4. **User document untouched by org code** — opt-in and legacy compatibility by
   construction.

---

## 13. Refactor plan — unify `:org-role` + `:templates` into one `:roles` list

The agreed simplification (§3.2). Goal: a member has **one** `:roles` list drawn from
`#{:admin}` ∪ catalog keys; `:org/member` is the membership baseline; nothing else
changes in the matching engine (it already treats every projected `{:role :org-id}`
uniformly — the split only ever lived at the assignment layer). The roles engine
needs **no** behavioural change beyond adding `:org-admin` to `org-scoped-roles`.

**Why now / why safe.** Pre-production. Catalog edits still can't strip admins
(`:admin` is reserved, engine-level — not in the deletable catalog). Membership still
implies view (baseline). Ceiling still holds (a key in neither the reserved set nor
the catalog expands to nothing). FE capability gating already reads
`check-privilege … :org/manage` / `:org/member`, **not** raw `:org-role`
(`subs.cljs::is-org-admin?`/`::is-org-member?`), so it is unaffected.

### Touch points (by layer)

1. **Role registry** (`lipas.roles`): add `:org-admin` to `org-scoped-roles` (org-id
   injection); `:org-user` stays as the never-assigned baseline. No privilege changes.
2. **Projection** (`lipas.backend.org`):
   - `derive-org-roles` / `member->roles` → the §4.1 shape (baseline + `reserved-roles`
     + catalog expansion over a single `:roles` list). Add `reserved-roles {:admin …}`.
   - `validate-assignment!` → drop the `:org-role` enum branch; validate `:roles` ⊆
     `#{"admin"}` ∪ catalog keys.
   - Collapse `set-member-org-role!` + `set-member-templates!` → one `set-member-roles!`;
     `add-member!` / `upsert-member-full` take `:roles`; `upsert-member` (legacy
     account-role path in `update-org-users!` / `add-org-user-by-email!`) writes
     `:roles ["admin"]` / `[]` instead of `:org-role`.
   - `get-org-users` → augment accounts with `:roles` (drop `:org-role`/`:templates`).
   - `get-history` diff → replace the two member branches (org-role change, templates
     change) with one **"Roolit muuttui: «name» [a] → [b]"** line.
3. **Schema** (`lipas.schema.org`): `members` entry → `[:map [:user-id …]
   [:roles {:optional true} [:vector :string]]]`. `role-templates` / `role-spec`
   unchanged. (The **catalog key stays `:role-templates`** — only the per-member field
   becomes `:roles`. The catalog is still a catalog of role definitions; renaming it
   to `:role-catalog` is optional polish, deliberately out of scope to keep the
   migration to a member-only reshape.)
4. **Endpoints** (`lipas.backend.handler`): `invite-org-member` body `{:email :roles}`;
   merge `set-org-member-role` + `set-org-member-templates` → `set-org-member-roles`
   (`{:org-id :user-id :roles}`). Privileges unchanged (`:org/manage`).
5. **Frontend** (`lipas.ui.org.{views,events,subs}` + the admin add-user-to-org UI in
   `lipas.ui.admin.{views,events,subs}`):
   - members table + invite form → a single **roles** multi-select (options =
     `Ylläpitäjä` (`:admin`) + catalog roles); remove the org-role `Select`.
   - **invite UX** (§6.6): roles select **defaults to empty** (= plain member; the old
     `"member"` default goes away, baseline is implicit); `:roles []` must be a valid
     submission; `:admin` is offered as an option (first-admin creation in one step).
     The existence probe (`check-is-existing-user`) + account creation + magic-link
     email path is **unchanged** — only `::invite-member`'s payload becomes `:roles`.
   - events `::set-member-org-role` + `::set-member-templates` → `::set-member-roles`;
     `::invite-member` payload `:roles`.
   - subs `::is-org-admin?`/`::is-org-member?` unchanged.
6. **i18n** (`lipas_org.edn` ×3): drop/repurpose `:org-role`; add a reserved-admin
   option label (`:role-admin` = "Ylläpitäjä" / "Administratör" / "Administrator");
   members column "Roolit".
7. **Migration**: one **self-contained** forward migration (raw jsonb reshape, no
   `org/*` calls — heed §8's from-scratch lesson) appending a revision per `org_current`
   doc: members `{:org-role :templates}` → `{:roles}` (`:roles` = `templates`, plus
   `"admin"` when `:org-role`="admin"). Runs after `org-event-log` on fresh DBs and
   over existing dev/staging DBs alike.
8. **Tests** (`org-test`, `roles-test`): member fixtures + endpoint bodies → `:roles`;
   projection assertions for baseline + reserved `:admin` + ceiling; add a case proving
   a catalog edit cannot remove `:admin`; invite cases for `:roles []` (plain member,
   gets `:org/member` only) and `:roles ["admin"]` (first-admin creation).

### Sequencing

Engine + projection + schema + migration first (verify in REPL: `derive-org-roles`
yields the same `{:role :org-id}` set as today for equivalent assignments, incl.
baseline and ceiling), then endpoints, then FE, then i18n. Single PR; `bb test-ns
lipas.roles-test lipas.backend.org-test` green before UI work.
